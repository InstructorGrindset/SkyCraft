#include "Game.h"
#include "Towns.h"

// Minecraft's doors and ladders for Skyrim's NPCs (npc_doors_ladders). Minecraft sends each section's doors and ladders
// (proto::kRenPassages) and, since this plugin says it reads them (proto::kTownPassages), leaves ladders and open doors
// out of the NPC walls (kRenSolids); closed doors stay walls. An NPC pushed back by a closed door (NpcBlocks) asks
// Minecraft to open it (proto::kInOpenDoor) and walks through once it's open, and its path goes through doors rather
// than round them (PathAvoid); Minecraft closes the door again once its doorway has been empty a few seconds. Iron doors
// stay shut: only redstone opens those.
//
// Skyrim's NPCs can't climb: there's no climbing movement or animation for them in the engine. So an NPC standing in a
// ladder column whose path goes up is helped: lifted up the ladder (its position set each frame, as the push out of
// blocks does) and stepped onto the block the ladder leans on. With nowhere to step off, or after a while, it's let
// back down to where it started, never dropped. It rises in whatever pose it was in.
namespace skycraft::Passages
{
	namespace
	{
		struct Entry
		{
			std::uint8_t x, y, z, flags;
		};

		std::unordered_map<std::uint64_t, std::vector<Entry>> sections;
		// Written on the main thread; read there and by Skyrim's AI threads planning NPC paths (PathAvoid).
		std::shared_mutex                                     sectionsLock;
		std::atomic<int>                                      ladders{ 0 };  // ladder blocks known

		constexpr float kClimbSpeed = 2.4f;    // blocks a second (Minecraft's own ladder speed is about 2.35)
		constexpr float kMaxClimb = 10.0f;     // seconds up a ladder before going back down
		constexpr float kRange = 4000.0f;      // Skyrim units from the player
		constexpr double kOffWall = 0.1;       // blocks off the ladder's middle, away from its wall
		constexpr float kAfterClimb = 3.0f;    // seconds before an NPC that got up is helped again
		constexpr float kAfterGiveUp = 10.0f;  // and one that had to come back down

		struct Climb
		{
			int    x, z;    // the ladder column
			int    fx, fz;  // the way the ladder faces (its wall is the other way)
			double startY;
			double y;
			float  time;
			bool   down;
		};
		std::unordered_map<RE::FormID, Climb> climbs;     // main thread
		std::unordered_map<RE::FormID, float> cooldowns;  // main thread: clock until which an NPC isn't helped again
		float                                 clock = 0.0f;
		int                                   logged = 0;

		std::uint64_t SectionKey(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z)
		{
			return (std::uint64_t(std::uint32_t(a_x) & 0x3FFFFF) << 42) | (std::uint64_t(std::uint32_t(a_z) & 0x3FFFFF) << 20) |
			       (std::uint64_t(std::uint32_t(a_y) & 0xFFFFF));
		}

		std::uint8_t FlagsAt(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z)
		{
			std::shared_lock lock(sectionsLock);
			const auto       it = sections.find(SectionKey(a_x >> 4, a_y >> 4, a_z >> 4));
			if (it == sections.end()) {
				return 0;
			}
			for (const auto& e : it->second) {
				if (e.x == (a_x & 15) && e.y == (a_y & 15) && e.z == (a_z & 15)) {
					return e.flags;
				}
			}
			return 0;
		}

		std::uint8_t Kind(std::uint8_t a_flags) { return a_flags & proto::kPassageKindMask; }

		bool LadderAt(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z) { return Kind(FlagsAt(a_x, a_y, a_z)) == proto::kPassageLadder; }

		// Minecraft's 2D facing (0 south +z, 1 west -x, 2 north -z, 3 east +x) as a step in x and z.
		void FacingStep(std::uint8_t a_flags, int& a_fx, int& a_fz)
		{
			static constexpr int kStep[4][2] = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };
			const int            facing = (a_flags & proto::kPassageFacingMask) >> proto::kPassageFacingShift;
			a_fx = kStep[facing][0];
			a_fz = kStep[facing][1];
		}

		bool On() { return Game::FeatureOn(proto::kFeatNpcDoorsLadders); }
	}

	void OnPassages(const std::uint8_t* a_data, std::uint32_t a_bytes)
	{
		if (a_bytes < sizeof(proto::RenPassages)) {
			return;
		}
		const auto* hdr = reinterpret_cast<const proto::RenPassages*>(a_data);
		const auto  key = SectionKey(hdr->sx, hdr->sy, hdr->sz);
		const auto  count = std::min(hdr->count, static_cast<std::uint32_t>((a_bytes - sizeof(proto::RenPassages)) / sizeof(proto::RenPassage)));
		std::vector<Entry> entries;
		entries.reserve(count);
		int added = 0;
		const auto* records = reinterpret_cast<const proto::RenPassage*>(a_data + sizeof(proto::RenPassages));
		for (std::uint32_t i = 0; i < count; ++i) {
			const auto& r = records[i];
			if (r.x < 16 && r.y < 16 && r.z < 16 && Kind(r.flags) != 0) {
				entries.push_back({ r.x, r.y, r.z, r.flags });
				added += Kind(r.flags) == proto::kPassageLadder ? 1 : 0;
			}
		}
		std::unique_lock lock(sectionsLock);
		int removed = 0;
		if (const auto it = sections.find(key); it != sections.end()) {
			for (const auto& e : it->second) {
				removed += Kind(e.flags) == proto::kPassageLadder ? 1 : 0;
			}
		}
		if (entries.empty()) {
			sections.erase(key);
		} else {
			sections[key] = std::move(entries);
		}
		ladders += added - removed;
	}

	void Clear()
	{
		std::unique_lock lock(sectionsLock);
		sections.clear();
		ladders = 0;
	}

	bool ClosedDoorAt(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z)
	{
		const auto flags = FlagsAt(a_x, a_y, a_z);
		return Kind(flags) == proto::kPassageDoor && !(flags & proto::kPassageOpen) && On();
	}

	bool NpcDoorAt(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z)
	{
		return Kind(FlagsAt(a_x, a_y, a_z)) == proto::kPassageDoor && On();
	}

	void RequestOpen(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z)
	{
		if (!On()) {
			return;
		}
		// Main thread only (NpcBlocks' push), so plain statics.
		using Clock = std::chrono::steady_clock;
		static std::unordered_map<std::uint64_t, Clock::time_point> asked;
		const auto now = Clock::now();
		const auto key = (std::uint64_t(std::uint32_t(a_x) & 0x3FFFFFF) << 38) | (std::uint64_t(std::uint32_t(a_z) & 0x3FFFFFF) << 12) |
		                 (std::uint64_t(std::uint32_t(a_y)) & 0xFFF);
		auto& last = asked[key];
		if (last != Clock::time_point{} && now - last < 1s) {
			return;
		}
		last = now;
		if (asked.size() > 256) {
			std::erase_if(asked, [&](const auto& a_entry) { return now - a_entry.second > 5s; });
		}
		Link::Get().PushInput(proto::kInOpenDoor, 0, a_x, a_y, a_z);
		if (logged < 20) {
			++logged;
			logger::info("SkyCraft-town: an NPC walked into the Minecraft door at {} {} {}; asked Minecraft to open it", a_x, a_y, a_z);
		}
	}

	void UpdateClimbers(RE::PlayerCharacter* a_player, float a_delta)
	{
		clock += a_delta;
		if (!On() || ladders.load() <= 0) {
			climbs.clear();
			return;
		}
		auto* lists = RE::ProcessLists::GetSingleton();
		if (!lists) {
			return;
		}
		std::erase_if(cooldowns, [](const auto& a_entry) { return clock >= a_entry.second; });
		const auto playerPos = a_player->GetPosition();
		for (auto& handle : lists->highActorHandles) {
			auto  actorPtr = handle.get();
			auto* actor = actorPtr.get();
			if (!actor || actor == a_player) {
				continue;
			}
			const auto id = actor->GetFormID();
			auto       it = climbs.find(id);
			const bool able = !actor->IsDead() && actor->Is3DLoaded() && !actor->IsOnMount() && !actor->IsInRagdollState() && !actor->IsInKillMove() &&
			                  !actor->AsActorState()->IsFlying() && actor->GetPosition().GetDistance(playerPos) <= kRange;
			if (!able) {
				if (it != climbs.end()) {
					climbs.erase(it);
				}
				continue;
			}
			const auto mc = SkyToMc(actor->GetPosition());
			if (it == climbs.end()) {
				if (cooldowns.contains(id)) {
					continue;
				}
				const int x = int(std::floor(mc.x)), z = int(std::floor(mc.z)), feet = int(std::floor(mc.y + 0.05));
				std::uint8_t flags = FlagsAt(x, feet, z);
				if (Kind(flags) != proto::kPassageLadder) {
					flags = FlagsAt(x, feet + 1, z);
				}
				if (Kind(flags) != proto::kPassageLadder) {
					continue;
				}
				// Only where its path goes: somewhere well above it.
				RE::NiPoint3 goal;
				if (!PathAvoid::GoalOf(id, goal) || SkyToMc(goal).y < mc.y + 1.5) {
					continue;
				}
				Climb c{};
				c.x = x;
				c.z = z;
				FacingStep(flags, c.fx, c.fz);
				c.startY = mc.y;
				c.y = mc.y;
				it = climbs.emplace(id, c).first;
				if (logged < 20) {
					++logged;
					logger::info("SkyCraft-town: {} climbs the Minecraft ladder at {} {} {}", actor->GetDisplayFullName(), x, feet, z);
				}
			}
			Climb& c = it->second;
			c.time += a_delta;
			if (!c.down) {
				c.y += kClimbSpeed * a_delta;
				const int feet = int(std::floor(c.y + 0.05));
				const bool here = LadderAt(c.x, feet, c.z), below = LadderAt(c.x, feet - 1, c.z), above = LadderAt(c.x, feet + 1, c.z);
				if (!here && below) {
					// Over the ladder's top: onto the block it leans on, if there's room to stand there.
					const int wx = c.x - c.fx, wz = c.z - c.fz;
					if (NpcBlocks::SolidAt(wx, feet - 1, wz) && !NpcBlocks::SolidAt(wx, feet, wz) && !NpcBlocks::SolidAt(wx, feet + 1, wz)) {
						actor->SetPosition(McToSky(wx + 0.5, feet, wz + 0.5), true);
						if (logged < 20) {
							++logged;
							logger::info("SkyCraft-town: {} is up the ladder, at {} {} {}", actor->GetDisplayFullName(), wx, feet, wz);
						}
						cooldowns[id] = clock + kAfterClimb;
						climbs.erase(it);
						continue;
					}
					c.down = true;  // nowhere to step off: back down
				} else if (!here && !below && !above) {
					climbs.erase(it);  // the ladder is gone
					cooldowns[id] = clock + kAfterGiveUp;
					continue;
				} else if (c.time > kMaxClimb) {
					c.down = true;
				}
			}
			if (c.down) {
				c.y -= kClimbSpeed * a_delta;
				if (c.y <= c.startY) {
					actor->SetPosition(McToSky(c.x + 0.5 + c.fx * kOffWall, c.startY, c.z + 0.5 + c.fz * kOffWall), true);
					climbs.erase(it);
					cooldowns[id] = clock + kAfterGiveUp;
					continue;
				}
			}
			actor->SetPosition(McToSky(c.x + 0.5 + c.fx * kOffWall, c.y, c.z + 0.5 + c.fz * kOffWall), true);
		}
		if (climbs.size() > 64) {
			climbs.clear();
		}
	}
}
