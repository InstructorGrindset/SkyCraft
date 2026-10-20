#include "Backups.h"

#include "BackupNames.h"
#include "Game.h"

#include <filesystem>

namespace skycraft::Backups
{
	namespace
	{
		namespace fs = std::filesystem;

		// Copies and deletions, one after another on a thread of their own, so a deletion queued after a copy waits for it.
		// Never destroyed: the thread may still be waiting when the game exits.
		class Worker
		{
		public:
			void Post(std::function<void()> a_job)
			{
				{
					std::lock_guard lock(mutex_);
					jobs_.push_back(std::move(a_job));
					if (!started_) {
						started_ = true;
						std::thread([this] { Run(); }).detach();
					}
				}
				wake_.notify_one();
			}

		private:
			void Run()
			{
				::SetThreadPriority(::GetCurrentThread(), THREAD_PRIORITY_BELOW_NORMAL);
				for (;;) {
					std::function<void()> job;
					{
						std::unique_lock lock(mutex_);
						wake_.wait(lock, [this] { return !jobs_.empty(); });
						job = std::move(jobs_.front());
						jobs_.pop_front();
					}
					try {
						job();
					} catch (const std::exception& e) {
						logger::warn("paired backups: {}", e.what());
					}
				}
			}

			std::mutex                        mutex_;
			std::condition_variable           wake_;
			std::deque<std::function<void()>> jobs_;
			bool                              started_ = false;
		};

		Worker& TheWorker()
		{
			static auto* worker = new Worker();
			return *worker;
		}

		// A path for the log (UTF-8, as the log is): a narrow string() can't hold every Documents folder's name.
		std::string Show(const fs::path& a_path)
		{
			const auto text = a_path.u8string();
			return std::string(text.begin(), text.end());
		}

		// Main thread only.
		std::string   loadingSave;  // kPreLoadGame's save, until its load is done
		std::string   pendingSave;  // loaded, still to back up (Minecraft not connected yet)
		bool          pending = false;
		std::uint32_t nextId = 1;
		std::time_t   lastStamp = 0;
		struct Asked
		{
			std::uint32_t id;
			std::string   stamp;
			fs::path      backups;
		};
		std::deque<Asked> asked;  // requests Minecraft hasn't answered yet (the last few)

		// Where Skyrim keeps its saves: <My Games>\Skyrim Special Edition (the folder SKSE's log folder is in, found the
		// way SKSE finds it) and Skyrim.ini's sLocalSavePath (Saves\ unless a mod changes it). Main thread: it reads
		// Skyrim's settings.
		std::optional<fs::path> SavesFolder()
		{
			const auto log = SKSE::log::log_directory();
			if (!log) {
				return std::nullopt;
			}
			std::string local = "Saves\\";
			if (auto* ini = RE::INISettingCollection::GetSingleton()) {
				if (auto* setting = ini->GetSetting("sLocalSavePath:General"); setting && setting->GetType() == RE::Setting::Type::kString) {
					if (const char* value = setting->GetString(); value && *value) {
						local = value;
					}
				}
			}
			fs::path saves = (log->parent_path() / local).lexically_normal();
			if (!saves.has_filename()) {
				saves = saves.parent_path();  // "...\Saves\" names the folder itself
			}
			return saves;
		}

		// Backup thread: the save and its co-save into <backups>\<stamp>, by way of <stamp>.partial so a copy cut short
		// never looks like a backup.
		void Copy(const fs::path& a_saves, const fs::path& a_backups, const std::string& a_save, const std::string& a_stamp)
		{
			std::error_code ec;
			const fs::path  ess = a_saves / (a_save + ".ess");
			const fs::path  coSave = a_saves / (a_save + ".skse");
			if (!fs::exists(ess, ec)) {
				logger::warn("paired backups: {} isn't there, so nothing was copied (sLocalSavePath points elsewhere?)", Show(ess));
				return;
			}
			const fs::path partial = a_backups / (a_stamp + std::string(BackupNames::kPartial));
			const fs::path folder = a_backups / a_stamp;
			fs::remove_all(partial, ec);
			fs::create_directories(partial, ec);
			if (ec) {
				logger::warn("paired backups: couldn't make {}: {}", Show(partial), ec.message());
				return;
			}
			const auto    started = std::chrono::steady_clock::now();
			std::uint64_t bytes = 0;
			int           files = 0;
			for (const auto* file : { &ess, &coSave }) {
				if (!fs::exists(*file, ec)) {
					continue;  // a save made without SKSE has no co-save
				}
				fs::copy_file(*file, partial / file->filename(), fs::copy_options::overwrite_existing, ec);
				if (ec) {
					logger::warn("paired backups: couldn't copy {}: {}", Show(*file), ec.message());
					fs::remove_all(partial, ec);
					return;
				}
				bytes += fs::file_size(*file, ec);
				++files;
			}
			fs::remove_all(folder, ec);
			fs::rename(partial, folder, ec);
			if (ec) {
				logger::warn("paired backups: couldn't rename {} to {}: {}", Show(partial), a_stamp, ec.message());
				return;
			}
			const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - started).count();
			logger::info("paired backups: {} copied to {} ({} files, {:.1f} MB, {} ms)", a_save, Show(folder), files, double(bytes) / 1048576.0, ms);
		}

		// Backup thread: the copies past the newest a_keep pairs, and copies left half done, deleted.
		void Prune(const fs::path& a_backups, int a_keep)
		{
			std::error_code          ec;
			std::vector<std::string> names;
			for (fs::directory_iterator it(a_backups, ec), end; !ec && it != end; it.increment(ec)) {
				const auto name = it->path().filename().u8string();
				names.emplace_back(name.begin(), name.end());
			}
			for (const auto& name : BackupNames::ToDelete(names, a_keep)) {
				fs::remove_all(a_backups / name, ec);  // stamps: plain ASCII
				if (ec) {
					logger::warn("paired backups: couldn't delete the older {}: {}", name, ec.message());
				} else {
					logger::info("paired backups: deleted the older {}", name);
				}
			}
		}

		// Main thread: a stamp for the pair, Minecraft told, the copy started.
		void Start(const std::string& a_save)
		{
			const auto saves = SavesFolder();
			if (!saves) {
				logger::warn("paired backups: Skyrim's save folder isn't known (no Documents folder?); {} wasn't backed up", a_save);
				return;
			}
			std::time_t now = std::time(nullptr);
			if (now <= lastStamp) {
				now = lastStamp + 1;  // two loads within a second still get stamps of their own
			}
			lastStamp = now;
			std::tm local{};
			localtime_s(&local, &now);
			const auto stamp = BackupNames::FormatStamp(local);
			const auto id = nextId++;
			for (const auto& e : BackupNames::Encode(id, stamp, a_save)) {
				Link::Get().PushInput(proto::kInPairedBackup, e.code, e.a, e.b, e.c);
			}
			const fs::path backups = saves->parent_path() / "SkyCraft Backups";
			asked.push_back({ id, stamp, backups });
			while (asked.size() > 8) {
				asked.pop_front();
			}
			logger::info("paired backups: {} was loaded: copying it as {} and asking Minecraft to back up its world with it", a_save, stamp);
			TheWorker().Post([from = *saves, backups, save = a_save, stamp] { Copy(from, backups, save, stamp); });
		}
	}

	void OnPreLoad(const char* a_name, std::uint32_t a_length)
	{
		loadingSave = a_name ? BackupNames::SaveBase(std::string_view(a_name, ::strnlen(a_name, a_length > 0 ? a_length : 1024))) : std::string();
	}

	void OnGameLoaded(bool a_loaded)
	{
		pending = a_loaded && !loadingSave.empty();
		pendingSave = pending ? loadingSave : std::string();
		loadingSave.clear();
	}

	void Update()
	{
		// Game::FeatureOn is only true while Minecraft is connected and has the switch on.
		if (!pending || !Game::FeatureOn(proto::kFeatPairedBackups)) {
			return;
		}
		pending = false;
		Start(pendingSave);
	}

	void OnEvent(const proto::McEvent& a_event)
	{
		std::string stamp = "an earlier request";
		fs::path    backups;
		for (const auto& a : asked) {
			if (a.id == a_event.formId) {
				stamp = a.stamp;
				backups = a.backups;
			}
		}
		if (backups.empty()) {
			const auto saves = SavesFolder();
			if (!saves) {
				return;
			}
			backups = saves->parent_path() / "SkyCraft Backups";
		}
		const int keep = std::clamp(static_cast<int>(a_event.a), 0, 1000);
		logger::info("paired backups: Minecraft {} its world for {} ({:.1f} MB); keeping the newest {} pairs", a_event.flags & 1 ? "backed up" : "couldn't back up",
			stamp, a_event.b, keep == 0 ? std::string("(all)") : std::to_string(keep));
		TheWorker().Post([backups, keep] { Prune(backups, keep); });
	}
}
