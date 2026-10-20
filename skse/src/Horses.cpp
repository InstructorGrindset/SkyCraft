#include "Horses.h"

#include "AnimalKinds.h"
#include "Game.h"

namespace skycraft::Horses
{
	namespace
	{
		constexpr std::uint32_t kSaveVersion = 1;
		constexpr float         kNearBlocks = 16.0f;       // the horse comes along into another dimension from this close
		constexpr float         kArriveReachBlocks = 24.0f;  // an arrival further from the player than this is a stale one
		constexpr float         kArrivedSeconds = 2.0f;    // kHorseArrived is shown this long after an arrival
		constexpr float         kLiftUnits = 10.0f;        // put down just above the ground
		constexpr double        kMinecraftSpeedAverage = 0.225;  // a Minecraft horse's movement speed on average (creatures.HorseRules)
		constexpr RE::FormID    kPlayerFaction = 0x00000DB1;     // Skyrim.esm PlayerFaction

		RE::FormID carried = 0;   // the horse that has crossed between the games (0: none yet)
		bool       away = false;  // it's in Minecraft now: disabled here
		float      arrivedFor = 0.0f;

		struct Saved
		{
			RE::FormID formId{ 0 };
			bool       away{ false };
		};
		std::optional<Saved> loaded;  // from the co-save being loaded; taken on by Reset()

		RE::TESNPC* newHorseBase = nullptr;
		bool        baseLooked = false;

		bool IsHorse(RE::Actor* a_actor)
		{
			const auto* race = a_actor ? a_actor->GetRace() : nullptr;
			const char* id = race ? race->GetFormEditorID() : nullptr;
			return id && AnimalKinds::FromRace(id) == proto::kAnimalHorse;
		}

		bool OwnedByPlayer(RE::Actor* a_actor, RE::PlayerCharacter* a_player)
		{
			const RE::TESForm* owner = a_actor->GetOwner();
			return owner && (owner == a_player->GetActorBase() || owner->GetFormID() == kPlayerFaction);
		}

		std::string_view NameOf(RE::Actor* a_actor)
		{
			const char* name = a_actor->GetDisplayFullName();
			return name && *name ? std::string_view{ name } : std::string_view{ "horse" };
		}

		// The player's horse: the one carried over (even while it's away), else the owned horse they last rode, if it's an
		// ordinary one (not summoned like Arvak, not a quest's essential horse, not disabled by someone).
		RE::Actor* Candidate(RE::PlayerCharacter* a_player)
		{
			if (carried != 0) {
				auto* horse = RE::TESForm::LookupByID<RE::Actor>(carried);
				if (horse && !horse->IsDead() && IsHorse(horse)) {
					return horse;
				}
			}
			const auto mount = a_player->GetInfoRuntimeData().lastRiddenMount.get();
			auto*      horse = mount.get();
			if (horse && !horse->IsDead() && !horse->IsDisabled() && IsHorse(horse) && OwnedByPlayer(horse, a_player) && !horse->IsEssential() &&
				!horse->GetCommandingActor()) {
				return horse;
			}
			return nullptr;
		}

		bool HasRider(RE::Actor* a_horse)
		{
			RE::NiPointer<RE::Actor> rider;
			return a_horse->GetMountedBy(rider) && rider;
		}

		// A plain horse to make when a Minecraft horse comes in that was never Skyrim's: the first horse actor base that isn't
		// unique or essential, Skyrim.esm's one named "Horse" if there is one. Found once, by race, so no FormID is assumed.
		RE::TESNPC* NewHorseBase()
		{
			if (baseLooked) {
				return newHorseBase;
			}
			auto* data = RE::TESDataHandler::GetSingleton();
			if (!data) {
				return nullptr;
			}
			baseLooked = true;
			for (auto* npc : data->GetFormArray<RE::TESNPC>()) {
				const auto* race = npc ? npc->GetRace() : nullptr;
				const char* raceId = race ? race->GetFormEditorID() : nullptr;
				if (!raceId || AnimalKinds::FromRace(raceId) != proto::kAnimalHorse || npc->IsUnique() || npc->IsEssential()) {
					continue;
				}
				const char* name = npc->GetName();
				if ((npc->GetFormID() >> 24) == 0 && name && std::string_view{ name } == "Horse") {
					newHorseBase = npc;
					break;
				}
				if (!newHorseBase) {
					newHorseBase = npc;
				}
			}
			if (newHorseBase) {
				logger::info("SkyCraft-horse: new horses from Minecraft are {} ({:08X})", newHorseBase->GetName(), newHorseBase->GetFormID());
			} else {
				logger::warn("SkyCraft-horse: no plain horse actor in the game's data; a Minecraft horse that was never Skyrim's stays in Minecraft");
			}
			return newHorseBase;
		}

		// The cell a spot outside is in (the player's when it isn't loaded).
		RE::TESObjectCELL* CellAt(RE::PlayerCharacter* a_player, const RE::NiPoint3& a_pos)
		{
			auto* tes = RE::TES::GetSingleton();
			auto* cell = tes ? tes->GetCell(a_pos) : nullptr;
			return cell && !cell->IsInteriorCell() ? cell : a_player->GetParentCell();
		}

		// Papyrus ObjectReference.MoveTo's own function, with no target reference (Travel.cpp moves the player with it): the
		// horse goes to the spot, from wherever it was (its stable, the other side of Skyrim), changing cells as it must, and is
		// enabled again if it was away. As an SKSE task, like Travel.cpp's move, so it runs outside the frame's actor updates.
		void MoveHorse(RE::Actor* a_horse, RE::TESObjectCELL* a_cell, RE::TESWorldSpace* a_world, const RE::NiPoint3& a_pos, const RE::NiPoint3& a_angle)
		{
			const RE::ActorHandle handle = a_horse->GetHandle();
			auto                  move = [handle, a_cell, a_world, a_pos, a_angle]() {
				using MoveToFn = void(RE::TESObjectREFR*, const RE::ObjectRefHandle&, RE::TESObjectCELL*, RE::TESWorldSpace*, const RE::NiPoint3&, const RE::NiPoint3&);
				static REL::Relocation<MoveToFn> moveTo{ RELOCATION_ID(56227, 56626) };
				const auto                       horse = handle.get();
				if (!horse || horse->IsDead()) {
					return;
				}
				const RE::ObjectRefHandle nobody{};
				moveTo(horse.get(), nobody, a_cell, a_world, a_pos, a_angle);
				if (horse->IsDisabled()) {
					horse->Enable(false);  // back from Minecraft
				}
			};
			if (const auto* tasks = SKSE::GetTaskInterface()) {
				tasks->AddTask(move);
			} else {
				move();
			}
		}

		// The Minecraft horse's speed and health on its Skyrim form (proto::kHorseStat*): SpeedMult, health as a share.
		void ApplyStats(RE::Actor* a_horse, std::uint32_t a_stats)
		{
			auto*        av = a_horse->AsActorValueOwner();
			const double speed = (a_stats & 0xFFF) / 10000.0;
			if (speed > 0.0) {
				const float mult = static_cast<float>(std::clamp(std::round(100.0 * speed / kMinecraftSpeedAverage), 50.0, 150.0));
				av->SetActorValue(RE::ActorValue::kSpeedMult, mult);
			}
			const float maxHealth = static_cast<float>((a_stats >> proto::kHorseStatMaxHealthShift) & 0xFF);
			const float health = static_cast<float>((a_stats >> proto::kHorseStatHealthShift) & 0xFF);
			if (maxHealth > 0.0f && health > 0.0f) {
				const float share = std::clamp(health / maxHealth, 0.05f, 1.0f);
				const float max = a_horse->GetActorValueMax(RE::ActorValue::kHealth);
				const float now = av->GetActorValue(RE::ActorValue::kHealth);
				const float want = share * max;
				if (want > now + 0.5f) {
					av->RestoreActorValue(RE::ActorValue::kHealth, want - now);
				} else if (want < now - 0.5f) {
					av->DamageActorValue(RE::ActorValue::kHealth, now - want);
				}
			}
		}

		void Arrive(RE::PlayerCharacter* a_player, const proto::McEvent& a_ev)
		{
			auto* cell = a_player->GetParentCell();
			auto* world = a_player->GetWorldspace();
			if (!cell || cell->IsInteriorCell() || !world || a_player->IsDead()) {
				logger::info("SkyCraft-horse: a horse can't come in here (an interior, or between cells); it stays in Minecraft");
				return;
			}
			if (!std::isfinite(a_ev.a) || !std::isfinite(a_ev.b) || !std::isfinite(a_ev.c)) {
				return;
			}
			const auto   playerMc = SkyToMc(a_player->GetPosition());
			const double off = std::sqrt((a_ev.a - playerMc.x) * (a_ev.a - playerMc.x) + (a_ev.b - playerMc.y) * (a_ev.b - playerMc.y) +
										 (a_ev.c - playerMc.z) * (a_ev.c - playerMc.z));
			if (off > kArriveReachBlocks) {
				logger::info("SkyCraft-horse: the horse is {:.0f} blocks from the player; arrival dropped", off);
				return;
			}
			auto               pos = McToSky(a_ev.a, a_ev.b, a_ev.c);
			pos.z += kLiftUnits;
			const RE::NiPoint3 angle{ 0.0f, 0.0f, McYawToHeading(std::isfinite(a_ev.d) ? a_ev.d : 0.0f) };
			auto*              at = CellAt(a_player, pos);

			RE::Actor* horse = nullptr;
			if (a_ev.formId != 0) {
				auto* known = RE::TESForm::LookupByID<RE::Actor>(a_ev.formId);
				if (known && !known->IsDead() && IsHorse(known) && (a_ev.formId == carried || OwnedByPlayer(known, a_player))) {
					horse = known;
				} else {
					logger::info("SkyCraft-horse: Skyrim's horse {:08X} isn't there any more; a new one comes in", a_ev.formId);
				}
			}
			bool made = false;
			if (horse) {
				if (HasRider(horse)) {
					logger::info("SkyCraft-horse: {} ({:08X}) has a rider here; the horse stays in Minecraft", NameOf(horse), horse->GetFormID());
					return;
				}
				MoveHorse(horse, at, world, pos, angle);
			} else {
				auto* base = NewHorseBase();
				auto* data = RE::TESDataHandler::GetSingleton();
				if (!base || !data) {
					return;
				}
				// Born where it comes in, as the deep creatures are (Spawn.cpp's PlaceAt).
				const auto ref = data->CreateReferenceAtLocation(base, pos, angle, at, world, nullptr, nullptr, RE::ObjectRefHandle(), false, true).get();
				horse = ref ? ref->As<RE::Actor>() : nullptr;
				if (!horse) {
					logger::warn("SkyCraft-horse: couldn't place a horse; it stays in Minecraft");
					return;
				}
				made = true;
			}
			horse->SetOwner(a_player->GetActorBase());  // the player's own, as a bought or tamed horse is
			ApplyStats(horse, a_ev.weapon);
			carried = horse->GetFormID();
			away = false;
			arrivedFor = kArrivedSeconds;
			logger::info("SkyCraft-horse: the player's Minecraft horse is {} ({:08X}) in Skyrim now{}, SpeedMult {:.0f}", NameOf(horse), carried,
				made ? " (a new horse)" : "", horse->AsActorValueOwner()->GetActorValue(RE::ActorValue::kSpeedMult));
		}

		void Depart(RE::PlayerCharacter* a_player, const proto::McEvent& a_ev)
		{
			auto* horse = RE::TESForm::LookupByID<RE::Actor>(a_ev.formId);
			if (!horse || horse->IsDead() || !IsHorse(horse)) {
				logger::info("SkyCraft-horse: Minecraft took {:08X} along, but it isn't a living horse here", a_ev.formId);
				return;
			}
			if (a_ev.formId != carried && !OwnedByPlayer(horse, a_player)) {
				logger::warn("SkyCraft-horse: {} ({:08X}) isn't the player's; it stays", NameOf(horse), a_ev.formId);
				return;
			}
			if (HasRider(horse)) {
				logger::info("SkyCraft-horse: {} ({:08X}) has a rider; it stays", NameOf(horse), a_ev.formId);
				return;
			}
			if (!horse->IsDisabled()) {
				horse->Disable();  // in Minecraft now; the same animal isn't in two places
			}
			carried = a_ev.formId;
			away = true;
			logger::info("SkyCraft-horse: {} ({:08X}) went into Minecraft with the player; disabled here until it comes back", NameOf(horse), carried);
		}

		void Lost(const proto::McEvent& a_ev)
		{
			if (a_ev.formId == 0 || a_ev.formId != carried) {
				return;
			}
			logger::info("SkyCraft-horse: the player's horse ({:08X}) died in Minecraft; Skyrim's stays gone", carried);
			carried = 0;
			away = false;
		}
	}

	void Describe(RE::PlayerCharacter* a_player, std::uint32_t& a_horse, std::uint32_t& a_info)
	{
		a_horse = 0;
		a_info = 0;
		if (!a_player || !Game::FeatureOn(proto::kFeatHorsesCarryOver)) {
			return;
		}
		std::uint32_t info = arrivedFor > 0.0f ? proto::kHorseArrived : 0u;
		auto*         horse = Candidate(a_player);
		if (!horse) {
			a_info = info;
			return;
		}
		const bool isCarried = horse->GetFormID() == carried;
		if (isCarried) {
			info |= proto::kHorseCarried;
		}
		if (isCarried && away) {
			info |= proto::kHorseAway;
		} else if (horse->Is3DLoaded() && !horse->IsDisabled() && horse->GetWorldspace() == a_player->GetWorldspace() && !HasRider(horse) &&
				   horse->GetPosition().GetDistance(a_player->GetPosition()) <= kNearBlocks * static_cast<float>(proto::kUnitsPerBlock)) {
			info |= proto::kHorseNear;
		}
		auto*       av = horse->AsActorValueOwner();
		const float speed = std::clamp(av->GetActorValue(RE::ActorValue::kSpeedMult), 0.0f, 255.0f);
		const float max = horse->GetActorValueMax(RE::ActorValue::kHealth);
		const float health = max > 0.0f ? std::clamp(av->GetActorValue(RE::ActorValue::kHealth) / max * 100.0f, 1.0f, 100.0f) : 100.0f;
		info |= static_cast<std::uint32_t>(std::lround(speed)) << proto::kHorseSpeedShift;
		info |= static_cast<std::uint32_t>(std::lround(health)) << proto::kHorseHealthShift;
		a_horse = horse->GetFormID();
		a_info = info;
	}

	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
	{
		if (!Game::FeatureOn(proto::kFeatHorsesCarryOver)) {
			return;
		}
		switch (a_event.flags) {
		case proto::kHorseArrive:
			Arrive(a_player, a_event);
			break;
		case proto::kHorseDepart:
			Depart(a_player, a_event);
			break;
		case proto::kHorseLost:
			Lost(a_event);
			break;
		default:
			logger::warn("SkyCraft-horse: unknown horse action {} for {:08X}", a_event.flags, a_event.formId);
			break;
		}
	}

	void Update(RE::PlayerCharacter*, float a_delta)
	{
		if (arrivedFor > 0.0f) {
			arrivedFor -= a_delta;
		}
		static float check = 0.0f;
		check -= a_delta;
		if (check > 0.0f || carried == 0 || away) {
			return;
		}
		check = 5.0f;
		auto* horse = RE::TESForm::LookupByID<RE::Actor>(carried);
		if (horse && horse->IsDead()) {
			logger::info("SkyCraft-horse: the player's horse {} ({:08X}) died", NameOf(horse), carried);
			carried = 0;
		}
	}

	void Reset()
	{
		carried = loaded ? loaded->formId : 0;
		away = loaded && loaded->away;
		loaded.reset();
		arrivedFor = 0.0f;
		if (carried != 0) {
			logger::info("SkyCraft-horse: the loaded game's horse is {:08X}{}", carried, away ? ", away in Minecraft" : "");
		}
	}

	// ---- the co-save (main thread: SKSE saves and loads there; CoSave.cpp calls these) ------------
	// Record 'HRSE' version 1: the carried horse's FormID, then whether it's away in Minecraft (one byte).
	void Save(SKSE::SerializationInterface* a_intfc)
	{
		if (carried == 0) {
			return;
		}
		if (!a_intfc->OpenRecord(kSaveRecord, kSaveVersion)) {
			logger::warn("SkyCraft-horse: couldn't write the player's horse to the co-save");
			return;
		}
		const std::uint8_t isAway = away ? 1 : 0;
		a_intfc->WriteRecordData(carried);
		a_intfc->WriteRecordData(isAway);
	}

	void Load(SKSE::SerializationInterface* a_intfc, std::uint32_t a_version)
	{
		if (a_version != kSaveVersion) {
			logger::warn("SkyCraft-horse: the co-save's horse is version {} (this SkyCraft reads {}); skipped", a_version, kSaveVersion);
			return;
		}
		RE::FormID   id = 0;
		std::uint8_t isAway = 0;
		if (a_intfc->ReadRecordData(id) != sizeof(id) || a_intfc->ReadRecordData(isAway) != sizeof(isAway)) {
			return;
		}
		RE::FormID now = 0;
		if (a_intfc->ResolveFormID(id, now) && now != 0) {
			loaded = Saved{ now, isAway != 0 };
		}
	}

	void Revert()
	{
		loaded.reset();
	}
}
