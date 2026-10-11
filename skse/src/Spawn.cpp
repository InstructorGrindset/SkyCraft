#include "Spawn.h"

#include "Dig.h"
#include "Game.h"

#include <random>

namespace skycraft::Spawn
{
	namespace
	{
		// ---- who lives down there ---------------------------------------------------------------
		// Actor bases (NPC_) in Skyrim.esm, so the load index is always 00. Like a leveled list:
		// from each kind, the strongest one the player's level has reached (now and then the one
		// before it). Names and levels as on UESP (Skyrim:Skeever, Skyrim:Skeleton, Skyrim:Draugr).
		struct Creature
		{
			std::uint32_t    kind;      // proto::SpawnKind
			std::uint16_t    minLevel;  // the player's level from which it turns up (its own level)
			RE::FormID       base;
			std::string_view name;
		};
		constexpr std::array kCreatures{
			Creature{ proto::kSpawnSkeever, 1, 0x00023AB7, "Skeever" },             // level 1
			Creature{ proto::kSpawnSkeleton, 1, 0x0002D1DE, "Skeleton" },           // level 1 (Nordic ruins, graveyards)
			Creature{ proto::kSpawnDraugr, 1, 0x0003B547, "Draugr" },               // level 1
			Creature{ proto::kSpawnDraugr, 6, 0x0003B548, "Restless Draugr" },      // level 6
			Creature{ proto::kSpawnDraugr, 13, 0x0003B549, "Draugr Wight" },        // level 13
			Creature{ proto::kSpawnDraugr, 21, 0x0003B54A, "Draugr Scourge" },      // level 21
			Creature{ proto::kSpawnDraugr, 30, 0x0003B54B, "Draugr Deathlord" },    // level 30
		};

		// Never right next to the player, whatever Minecraft says: the closest skycraft-deep.txt allows
		// (4 blocks), less a little for Skyrim's player trailing Minecraft's.
		constexpr float       kMinSpawnBlocks = 3.5f;
		constexpr float       kDefaultDespawnBlocks = 64.0f;
		constexpr std::uint32_t kDefaultCap = 3;
		constexpr std::size_t kMaxTracked = 12;           // corpses beyond this are taken away first
		constexpr float       kLootBlocks = 8.0f;         // ...but not one this close: the player may be looting it
		constexpr float       kCheckSeconds = 0.5f;
		constexpr float       kLift = 10.0f;              // Skyrim units: put down just above the floor

		// SKSE co-save: which of a saved game's actors came out of the dark, so a loaded game still
		// takes them away (and counts them) like any other. Record: count, then per creature its
		// FormID and despawn distance.
		constexpr std::uint32_t kSaveId = 0x53434450;     // 'SCDP'
		constexpr std::uint32_t kSaveRecord = 0x44454550; // 'DEEP'
		constexpr std::uint32_t kSaveVersion = 1;
		constexpr std::uint32_t kMaxSaved = 64;

		// Where someone is, as far as "left for somewhere else" goes: the interior cell, or the
		// worldspace outside (walking from one exterior cell to the next is the distance rule's job).
		struct Place
		{
			RE::TESObjectCELL*  interior{ nullptr };
			RE::TESWorldSpace*  world{ nullptr };

			bool operator==(const Place&) const = default;
		};

		Place PlaceOf(RE::TESObjectREFR* a_ref)
		{
			auto* cell = a_ref->GetParentCell();
			if (cell && cell->IsInteriorCell()) {
				return { cell, nullptr };
			}
			return { nullptr, a_ref->GetWorldspace() };
		}

		struct Spawned
		{
			RE::ObjectRefHandle ref;
			RE::FormID          formId{ 0 };
			float               despawnBlocks{ kDefaultDespawnBlocks };
			std::string_view    name;
		};
		std::vector<Spawned> spawned;
		float                checkTimer = 0.0f;
		// From the co-save of the game being loaded (FormID, despawn distance); taken on by Reset().
		std::vector<std::pair<RE::FormID, float>> loaded;

		std::mt19937& Rng()
		{
			static std::mt19937 rng{ std::random_device{}() };
			return rng;
		}

		const Creature* Pick(std::uint32_t a_kind, std::uint16_t a_level)
		{
			const Creature* best = nullptr;
			const Creature* second = nullptr;
			for (const auto& c : kCreatures) {
				if (c.kind != a_kind || c.minLevel > a_level) {
					continue;
				}
				if (!best || c.minLevel > best->minLevel) {
					second = best;
					best = &c;
				} else if (!second || c.minLevel > second->minLevel) {
					second = &c;
				}
			}
			if (best && second && std::uniform_int_distribution<int>(0, 2)(Rng()) == 0) {
				return second;
			}
			return best;
		}

		// The table's name for a placed creature (by its actor base), for the log.
		std::string_view NameOf(RE::TESObjectREFR* a_ref)
		{
			const auto* base = a_ref->GetBaseObject();
			for (const auto& c : kCreatures) {
				if (base && base->GetFormID() == c.base) {
					return c.name;
				}
			}
			return std::string_view{ "creature" };
		}

		void TakeAway(RE::TESObjectREFR* a_ref, std::string_view a_name, const char* a_why)
		{
			logger::info("SkyCraft-deep: {} {:08X} taken away ({})", a_name, a_ref->GetFormID(), a_why);
			a_ref->Disable();
			a_ref->SetDelete(true);
		}

		bool Alive(RE::TESObjectREFR* a_ref)
		{
			auto* actor = a_ref->As<RE::Actor>();
			return actor && !actor->IsDead();
		}

		// Forgets what no longer exists; returns how many are still alive.
		std::uint32_t Prune()
		{
			std::uint32_t alive = 0;
			for (auto it = spawned.begin(); it != spawned.end();) {
				auto ref = it->ref.get();
				if (!ref || ref->IsDeleted() || ref->IsMarkedForDeletion()) {
					it = spawned.erase(it);
					continue;
				}
				alive += Alive(ref.get()) ? 1u : 0u;
				++it;
			}
			return alive;
		}

		// ---- the co-save (main thread: SKSE saves and loads there) --------------------------------
		void OnSave(SKSE::SerializationInterface* a_intfc)
		{
			if (!a_intfc->OpenRecord(kSaveRecord, kSaveVersion)) {
				logger::warn("SkyCraft-deep: couldn't write the spawned creatures to the co-save");
				return;
			}
			const auto count = static_cast<std::uint32_t>(std::min<std::size_t>(spawned.size(), kMaxSaved));
			a_intfc->WriteRecordData(count);
			for (std::uint32_t i = 0; i < count; ++i) {
				a_intfc->WriteRecordData(spawned[i].formId);
				a_intfc->WriteRecordData(spawned[i].despawnBlocks);
			}
		}

		void OnLoad(SKSE::SerializationInterface* a_intfc)
		{
			loaded.clear();
			std::uint32_t type = 0, version = 0, length = 0;
			while (a_intfc->GetNextRecordInfo(type, version, length)) {
				if (type != kSaveRecord || version != kSaveVersion) {
					continue;
				}
				std::uint32_t count = 0;
				if (a_intfc->ReadRecordData(count) != sizeof(count)) {
					continue;
				}
				for (std::uint32_t i = 0; i < count && i < kMaxSaved; ++i) {
					RE::FormID id = 0;
					float      despawn = kDefaultDespawnBlocks;
					if (a_intfc->ReadRecordData(id) != sizeof(id) || a_intfc->ReadRecordData(despawn) != sizeof(despawn)) {
						break;
					}
					RE::FormID now = 0;
					if (a_intfc->ResolveFormID(id, now) && now != 0) {
						loaded.emplace_back(now, std::isfinite(despawn) && despawn > 0.0f ? despawn : kDefaultDespawnBlocks);
					}
				}
			}
		}

		void OnRevert(SKSE::SerializationInterface*)
		{
			loaded.clear();
		}
	}

	void Install()
	{
		const auto* serialization = SKSE::GetSerializationInterface();
		if (!serialization) {
			logger::warn("SkyCraft-deep: no SKSE co-save; creatures from a saved game won't be taken away after it's loaded");
			return;
		}
		serialization->SetUniqueID(kSaveId);
		serialization->SetSaveCallback(OnSave);
		serialization->SetLoadCallback(OnLoad);
		serialization->SetRevertCallback(OnRevert);
	}

	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
	{
		const std::uint32_t kind = a_event.formId;
		if (kind < proto::kSpawnSkeever || kind > proto::kSpawnDraugr) {
			logger::warn("SkyCraft-deep: Minecraft asked for creature kind {}, which isn't one", kind);
			return;
		}
		auto* cell = a_player->GetParentCell();
		if (!cell || cell->IsInteriorCell() || !a_player->GetWorldspace() || a_player->IsDead()) {
			return;  // dug tunnels are under the land outside
		}
		const std::uint32_t cap = a_event.flags > 0 ? std::min<std::uint32_t>(a_event.flags, 16) : kDefaultCap;
		const std::uint32_t alive = Prune();
		if (alive >= cap) {
			logger::info("SkyCraft-deep: {} spawned creatures alive already (at most {}); no more", alive, cap);
			return;
		}
		const float despawn = a_event.d > 0.0f && std::isfinite(a_event.d) ? a_event.d : kDefaultDespawnBlocks;
		if (!std::isfinite(a_event.a) || !std::isfinite(a_event.b) || !std::isfinite(a_event.c)) {
			return;
		}
		const auto  playerMc = SkyToMc(a_player->GetPosition());
		const float dx = static_cast<float>(a_event.a - playerMc.x), dy = static_cast<float>(a_event.b - playerMc.y),
					dz = static_cast<float>(a_event.c - playerMc.z);
		const float away = std::sqrt(dx * dx + dy * dy + dz * dz);
		if (away < kMinSpawnBlocks || away > despawn) {
			logger::warn("SkyCraft-deep: spot {:.1f} blocks from the player is out of range; no spawn", away);
			return;
		}
		// The floor must be a Minecraft block Skyrim knows about here: under the land there's nothing
		// else to stand on, and a creature put down over nothing would fall out of the world.
		const auto bx = static_cast<std::int32_t>(std::floor(a_event.a));
		const auto by = static_cast<std::int32_t>(std::floor(a_event.b + 0.01f));
		const auto bz = static_cast<std::int32_t>(std::floor(a_event.c));
		if (!NpcBlocks::SolidAt(bx, by - 1, bz) || !Dig::IsDug(bx, by, bz)) {
			logger::info("SkyCraft-deep: Skyrim doesn't know the floor of the tunnel at {} {} {} yet; no spawn", bx, by, bz);
			return;
		}
		const auto* creature = Pick(kind, a_player->GetLevel());
		if (!creature) {
			return;
		}
		auto* base = RE::TESForm::LookupByID<RE::TESBoundObject>(creature->base);
		if (!base) {
			logger::error("SkyCraft-deep: {} (actor base {:08X}) isn't in the game; is Skyrim.esm changed?", creature->name, creature->base);
			return;
		}
		auto ref = a_player->PlaceObjectAtMe(base, false);
		if (!ref) {
			logger::warn("SkyCraft-deep: couldn't place a {}", creature->name);
			return;
		}
		auto pos = McToSky(a_event.a, a_event.b, a_event.c);
		pos.z += kLift;
		ref->SetPosition(pos);
		// Facing the player (Skyrim heading: 0 = north, clockwise).
		const auto playerPos = a_player->GetPosition();
		ref->SetAngle({ 0.0f, 0.0f, std::atan2(playerPos.x - pos.x, playerPos.y - pos.y) });

		// Corpses pile up otherwise: past the limit, the oldest dead one goes (not one right by the
		// player, who may be looting it).
		if (spawned.size() >= kMaxTracked) {
			for (auto it = spawned.begin(); it != spawned.end(); ++it) {
				auto old = it->ref.get();
				if (old && !Alive(old.get()) && old->GetPosition().GetDistance(playerPos) > kLootBlocks * static_cast<float>(proto::kUnitsPerBlock)) {
					TakeAway(old.get(), it->name, "too many bodies");
					spawned.erase(it);
					break;
				}
			}
		}
		spawned.push_back({ ref->GetHandle(), ref->GetFormID(), despawn, creature->name });
		logger::info("SkyCraft-deep: a {} ({:08X}, ref {:08X}) comes out of the dark at {:.1f} {:.1f} {:.1f}, {:.0f} blocks from the player; {} of {} alive",
			creature->name, creature->base, ref->GetFormID(), a_event.a, a_event.b, a_event.c, away, alive + 1, cap);
	}

	void Update(RE::PlayerCharacter* a_player, float a_delta)
	{
		if (spawned.empty()) {
			return;
		}
		checkTimer -= a_delta;
		if (checkTimer > 0.0f) {
			return;
		}
		checkTimer = kCheckSeconds;
		if (!a_player->GetParentCell()) {
			return;  // between cells (loading)
		}
		const Place here = PlaceOf(a_player);
		const auto  playerPos = a_player->GetPosition();
		for (auto it = spawned.begin(); it != spawned.end();) {
			auto ref = it->ref.get();
			if (!ref || ref->IsDeleted() || ref->IsMarkedForDeletion()) {
				it = spawned.erase(it);
				continue;
			}
			// The player's now (a Dead Thrall, Command Dead): not ours to take away any more.
			if (auto* actor = ref->As<RE::Actor>(); actor && actor->IsPlayerTeammate()) {
				logger::info("SkyCraft-deep: {} {:08X} is the player's now; no longer tracked", it->name, it->formId);
				it = spawned.erase(it);
				continue;
			}
			// Where the creature is now, not where it came out: one that followed the player
			// through a door stays. (No cell: being moved; the distance rule still holds.)
			const char* why = nullptr;
			if (ref->GetParentCell() && !(PlaceOf(ref.get()) == here)) {
				why = "the player left";
			} else if (ref->GetPosition().GetDistance(playerPos) > it->despawnBlocks * static_cast<float>(proto::kUnitsPerBlock)) {
				why = "far from the player";
			}
			if (why) {
				TakeAway(ref.get(), it->name, why);
				it = spawned.erase(it);
				continue;
			}
			++it;
		}
	}

	bool IsOurs(const RE::Actor* a_actor)
	{
		if (!a_actor || spawned.empty()) {
			return false;
		}
		const auto id = a_actor->GetFormID();
		return std::ranges::any_of(spawned, [id](const Spawned& a_s) { return a_s.formId == id; });
	}

	void Reset()
	{
		if (!spawned.empty()) {
			logger::info("SkyCraft-deep: a game was loaded; forgetting {} spawned creatures", spawned.size());
		}
		spawned.clear();
		checkTimer = 0.0f;
		// The loaded game's own creatures (from its co-save; none for a new game).
		for (const auto& [id, despawn] : loaded) {
			auto* actor = RE::TESForm::LookupByID<RE::Actor>(id);
			if (!actor || actor->IsPlayerRef() || actor->IsDeleted()) {
				continue;
			}
			RE::TESObjectREFR* ref = actor;
			spawned.push_back({ ref->GetHandle(), id, despawn, NameOf(ref) });
		}
		if (!spawned.empty()) {
			logger::info("SkyCraft-deep: the loaded game has {} creatures that came out of the dark", spawned.size());
		}
		loaded.clear();
	}
}
