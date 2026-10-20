#include "Game.h"
#include "TownKinds.h"
#include "Towns.h"

// Skyrim's switches and Dwemer doors and Minecraft's redstone (dwemer_redstone).
//  - A lever, pull bar, pull chain, button or pressure plate used (Skyrim's own activate event, TESActivateEvent, by the
//    player or anyone else; a pressure plate also counts when it sets off what it's linked to) gives Minecraft a
//    redstone pulse at its block (proto::kInRedstonePulse). Which activators are switches is told by their model path
//    (TownKinds.h).
//  - Every 2 s the Dwemer doors (doors, not load doors, with a Dwemer model) within 24 blocks of the player go to
//    Minecraft (proto::kInDwemerDoor), which watches redstone power at each one's foot; when power comes it sends
//    proto::kEvRedstoneDoor and the door is opened with Skyrim's own activation, when it goes the door is closed again.
//    Locked doors stay shut (redstone doesn't pick locks), and so do doors it would be a crime to open.
namespace skycraft::Redstone
{
	namespace
	{
		constexpr float       kDoorScanSeconds = 2.0f;
		constexpr float       kDoorRange = 24.0f * static_cast<float>(proto::kUnitsPerBlock);
		constexpr std::size_t kMaxDoors = 16;
		constexpr float       kRepeatSeconds = 0.5f;  // one switch pulses at most this often

		struct Used
		{
			RE::ObjectRefHandle ref;
			TownKinds::Switch   kind;
		};
		std::mutex        usedLock;
		std::vector<Used> used;  // from the activate event, for the next frame

		struct Door
		{
			RE::FormID   ref;
			std::int32_t x, y, z;
		};
		std::vector<Door>                     doors;  // main thread: the ones Minecraft was last told of
		std::unordered_map<RE::FormID, float> lastPulse;
		float                                 clock = 0.0f;
		float                                 scanTimer = 0.0f;
		std::size_t                           lastDoorCount = 0;
		int                                   logged = 0;

		bool On() { return Game::FeatureOn(proto::kFeatDwemerRedstone); }

		const char* ModelPath(RE::TESForm* a_base)
		{
			auto*       model = a_base ? a_base->As<RE::TESModel>() : nullptr;
			const char* path = model ? model->GetModel() : nullptr;
			return path ? path : "";
		}

		TownKinds::Switch SwitchOf(RE::TESObjectREFR* a_ref)
		{
			auto* base = a_ref ? a_ref->GetBaseObject() : nullptr;
			if (!base || base->GetFormType() != RE::FormType::Activator) {
				return TownKinds::Switch::kNone;
			}
			return TownKinds::SwitchFromModel(ModelPath(base));
		}

		class ActivateSink final : public RE::BSTEventSink<RE::TESActivateEvent>
		{
		public:
			static ActivateSink* Get()
			{
				static ActivateSink sink;
				return &sink;
			}

			RE::BSEventNotifyControl ProcessEvent(const RE::TESActivateEvent* a_event, RE::BSTEventSource<RE::TESActivateEvent>*) override
			{
				if (!a_event || !On()) {
					return RE::BSEventNotifyControl::kContinue;
				}
				auto* object = a_event->objectActivated.get();
				auto* by = a_event->actionRef.get();
				RE::TESObjectREFR* at = nullptr;
				auto               kind = SwitchOf(object);
				if (kind != TownKinds::Switch::kNone) {
					at = object;  // a switch used
				} else if (SwitchOf(by) == TownKinds::Switch::kPlate) {
					at = by;  // a pressure plate setting off what it's linked to
					kind = TownKinds::Switch::kPlate;
				}
				if (at) {
					std::scoped_lock lock(usedLock);
					if (used.size() < 32) {
						used.push_back({ at->GetHandle(), kind });
					}
				}
				return RE::BSEventNotifyControl::kContinue;
			}
		};

		void ScanDoors(RE::PlayerCharacter* a_player)
		{
			struct Candidate
			{
				float              distSq;
				RE::TESObjectREFR* ref;
			};
			static std::vector<Candidate> found;
			found.clear();
			doors.clear();
			auto* tes = RE::TES::GetSingleton();
			if (!tes) {
				return;
			}
			const auto origin = a_player->GetPosition();
			tes->ForEachReferenceInRange(a_player, kDoorRange, [&](RE::TESObjectREFR* a_ref) {
				auto* base = a_ref ? a_ref->GetBaseObject() : nullptr;
				if (base && base->GetFormType() == RE::FormType::Door && !a_ref->IsDisabled() && !a_ref->IsDeleted() &&
					!a_ref->extraList.HasType<RE::ExtraTeleport>() && TownKinds::IsDwemerDoorModel(ModelPath(base))) {
					found.push_back({ origin.GetSquaredDistance(a_ref->GetPosition()), a_ref });
				}
				return RE::BSContainer::ForEachResult::kContinue;
			});
			std::ranges::sort(found, {}, &Candidate::distSq);
			auto& link = Link::Get();
			for (const auto& c : found) {
				if (doors.size() >= kMaxDoors) {
					break;
				}
				const auto mc = SkyToMc(c.ref->GetPosition());
				Door       d{ c.ref->GetFormID(), std::int32_t(std::floor(mc.x)), std::int32_t(std::floor(mc.y + 0.01)), std::int32_t(std::floor(mc.z)) };
				const auto state = RE::BGSOpenCloseForm::GetOpenState(c.ref);
				const bool open = state == RE::BGSOpenCloseForm::OPEN_STATE::kOpen || state == RE::BGSOpenCloseForm::OPEN_STATE::kOpening;
				link.PushInput(proto::kInDwemerDoor, static_cast<std::uint16_t>(open ? 1 : 0), d.x, d.y, d.z);
				doors.push_back(d);
			}
			if (doors.size() != lastDoorCount) {
				lastDoorCount = doors.size();
				logger::info("SkyCraft-town: {} Dwemer door(s) near the player for redstone", doors.size());
			}
		}
	}

	void Install()
	{
		if (auto* events = RE::ScriptEventSourceHolder::GetSingleton()) {
			events->AddEventSink<RE::TESActivateEvent>(ActivateSink::Get());
			logger::info("SkyCraft-town: Skyrim's levers, buttons and pressure plates are watched for Minecraft's redstone");
		}
	}

	void Reset()
	{
		{
			std::scoped_lock lock(usedLock);
			used.clear();
		}
		doors.clear();
		lastPulse.clear();
		scanTimer = 0.0f;
	}

	void Update(RE::PlayerCharacter* a_player, float a_delta)
	{
		clock += a_delta;
		std::vector<Used> now;
		{
			std::scoped_lock lock(usedLock);
			now.swap(used);
		}
		if (!On()) {
			doors.clear();
			return;
		}
		for (const auto& u : now) {
			const auto ref = u.ref.get();
			if (!ref) {
				continue;
			}
			auto& last = lastPulse[ref->GetFormID()];
			if (last > 0.0f && clock - last < kRepeatSeconds) {
				continue;
			}
			last = clock;
			const auto mc = SkyToMc(ref->GetPosition());
			const int  x = int(std::floor(mc.x)), y = int(std::floor(mc.y + 0.01)), z = int(std::floor(mc.z));
			Link::Get().PushInput(proto::kInRedstonePulse, static_cast<std::uint16_t>(TownKinds::PulseTicks(u.kind)), x, y, z);
			if (logged < 30) {
				++logged;
				logger::info("SkyCraft-town: {} {} ({:08X}, {}) used: a redstone pulse at {} {} {}", TownKinds::SwitchName(u.kind), ref->GetDisplayFullName(),
					ref->GetFormID(), ModelPath(ref->GetBaseObject()), x, y, z);
			}
		}
		if (lastPulse.size() > 256) {
			std::erase_if(lastPulse, [](const auto& a_entry) { return clock - a_entry.second > 5.0f; });
		}
		scanTimer -= a_delta;
		if (scanTimer <= 0.0f) {
			scanTimer = kDoorScanSeconds;
			ScanDoors(a_player);
		}
	}

	void OnDoorPower(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
	{
		if (!On()) {
			return;
		}
		const std::int32_t x = std::int32_t(std::floor(a_event.a)), y = std::int32_t(std::floor(a_event.b)), z = std::int32_t(std::floor(a_event.c));
		const auto         it = std::ranges::find_if(doors, [&](const Door& a_door) { return a_door.x == x && a_door.y == y && a_door.z == z; });
		if (it == doors.end()) {
			return;  // not one of the doors near the player now
		}
		auto*      door = RE::TESForm::LookupByID<RE::TESObjectREFR>(it->ref);
		const bool powered = a_event.flags != 0;
		if (!door || door->IsDisabled() || !door->Is3DLoaded()) {
			return;
		}
		if (door->IsLocked()) {
			logger::info("SkyCraft-town: redstone {} the Dwemer door {:08X}, but it's locked", powered ? "reached" : "left", it->ref);
			return;
		}
		const auto state = RE::BGSOpenCloseForm::GetOpenState(door);
		const bool open = state == RE::BGSOpenCloseForm::OPEN_STATE::kOpen || state == RE::BGSOpenCloseForm::OPEN_STATE::kOpening;
		if (open == powered) {
			return;  // already as the redstone wants it
		}
		if (door->IsCrimeToActivate()) {
			logger::info("SkyCraft-town: redstone reached the Dwemer door {:08X}, but opening it would be a crime; left alone", it->ref);
			return;
		}
		door->ActivateRef(a_player, 0, nullptr, 0, false);
		logger::info("SkyCraft-town: redstone {} the Dwemer door {} ({:08X}) at {} {} {}", powered ? "opened" : "closed", door->GetDisplayFullName(), it->ref, x, y, z);
	}
}
