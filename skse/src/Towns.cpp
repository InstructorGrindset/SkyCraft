#include "Towns.h"

#include "Game.h"
#include "TownKinds.h"

#include <format>

namespace skycraft::Towns
{
	namespace
	{
		constexpr float kBlock = static_cast<float>(proto::kUnitsPerBlock);
		constexpr float kWitnessRange = 30.0f * kBlock;  // people this close to the player may have seen it
		constexpr float kSceneRange = 12.0f * kBlock;    // what's reported as the scene of the crime is this close to it
		constexpr float kStaleRange = 24.0f * kBlock;    // griefing further from the player than this is from before a move
		constexpr float kGuardRange = 16.0f * kBlock;    // a guard this close to the player may remark on their building
		constexpr float kGuardCooldown = 60.0f;          // seconds before the same guard says something again
		constexpr float kAnyGuardCooldown = 20.0f;       // and before any guard does
		constexpr std::uint32_t kCommentChance = 45;     // percent of the chances a guard takes

		float clock = 0.0f;
		std::unordered_map<RE::FormID, float> guardNext;
		float                                 anyGuardNext = 0.0f;
		std::uint32_t                         seed = 0x2545F491u;
		std::string_view                      lastLine;
		int                                   loggedCrimes = 0;
		int                                   loggedComments = 0;

		std::uint32_t Roll()
		{
			seed ^= seed << 13;
			seed ^= seed >> 17;
			seed ^= seed << 5;
			return seed;
		}

		bool Fighting(RE::Actor* a_actor, RE::PlayerCharacter* a_player)
		{
			return a_actor->IsInCombat() || a_actor->IsHostileToActor(a_player);
		}

		// ---- town crime -----------------------------------------------------------------------------

		// Who saw it: the person nearest the player who would report a crime (a crime faction, not the player's follower,
		// not a summon, not someone already fighting them) and who, by Skyrim's own detection, notices the player now.
		// Guards first.
		RE::Actor* FindWitness(RE::PlayerCharacter* a_player, std::int32_t& a_level)
		{
			auto* lists = RE::ProcessLists::GetSingleton();
			if (!lists) {
				return nullptr;
			}
			const auto playerPos = a_player->GetPosition();
			RE::Actor* best = nullptr;
			bool       bestGuard = false;
			float      bestDist = 0.0f;
			for (auto& handle : lists->highActorHandles) {
				auto  actorPtr = handle.get();
				auto* actor = actorPtr.get();
				if (!actor || actor == a_player || actor->IsDead() || !actor->Is3DLoaded() || actor->IsPlayerTeammate() || actor->IsCommandedActor() ||
					Fighting(actor, a_player) || !actor->GetCrimeFaction()) {
					continue;
				}
				const float dist = actor->GetPosition().GetDistance(playerPos);
				if (dist > kWitnessRange) {
					continue;
				}
				const std::int32_t level = actor->RequestDetectionLevel(a_player);
				if (level <= 0) {
					continue;
				}
				const bool guard = actor->IsGuard();
				if (!best || (guard && !bestGuard) || (guard == bestGuard && dist < bestDist)) {
					best = actor;
					bestGuard = guard;
					bestDist = dist;
					a_level = level;
				}
			}
			return best;
		}

		// A real object near the griefing to report as what was stolen from, as a theft from a container reports the
		// container: a container if one is near, else the nearest object of Skyrim's (the building or rock dug into,
		// most often). Skyrim's steal alarm wants a reference; a hole in the ground has none of its own.
		RE::TESObjectREFR* SceneOfCrime(const RE::NiPoint3& a_at)
		{
			auto* tes = RE::TES::GetSingleton();
			if (!tes) {
				return nullptr;
			}
			RE::TESObjectREFR* best = nullptr;
			float              bestScore = 0.0f;
			tes->ForEachCell([&](RE::TESObjectCELL* a_cell) {
				a_cell->ForEachReferenceInRange(a_at, kSceneRange, [&](RE::TESObjectREFR* a_ref) {
					auto* base = a_ref ? a_ref->GetBaseObject() : nullptr;
					if (!base || a_ref->IsDisabled() || a_ref->IsDeleted() || a_ref->As<RE::Actor>() || !a_ref->Get3D()) {
						return RE::BSContainer::ForEachResult::kContinue;
					}
					float penalty = 0.0f;
					switch (base->GetFormType()) {
					case RE::FormType::Container:
						break;
					case RE::FormType::Door:
						if (a_ref->extraList.HasType<RE::ExtraTeleport>()) {
							return RE::BSContainer::ForEachResult::kContinue;
						}
						penalty = 6.0f * kBlock;
						break;
					case RE::FormType::Static:
					case RE::FormType::MovableStatic:
					case RE::FormType::Tree:
					case RE::FormType::Flora:
					case RE::FormType::Furniture:
					case RE::FormType::Activator:
						penalty = 6.0f * kBlock;
						break;
					default:
						return RE::BSContainer::ForEachResult::kContinue;
					}
					const float score = a_ref->GetPosition().GetDistance(a_at) + penalty;
					if (!best || score < bestScore) {
						best = a_ref;
						bestScore = score;
					}
					return RE::BSContainer::ForEachResult::kContinue;
				});
			});
			return best;
		}

		void OnCrime(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
		{
			if (a_player->IsDead()) {
				return;
			}
			const auto at = McToSky(a_event.a, a_event.b, a_event.c);
			if (at.GetDistance(a_player->GetPosition()) > kStaleRange) {
				return;
			}
			const auto value = static_cast<std::int32_t>(std::clamp(std::lround(a_event.d), 1l, 100000l));
			const auto count = static_cast<std::int32_t>(std::clamp<std::uint32_t>(a_event.flags, 1u, 10000u));
			std::int32_t level = 0;
			auto*        witness = FindWitness(a_player, level);
			if (!witness) {
				if (loggedCrimes++ < 30) {
					logger::info("SkyCraft-town: griefing (kind {}, {} block(s), {} gold): nobody saw it", a_event.formId, count, value);
				}
				return;
			}
			auto* faction = witness->GetCrimeFaction();
			auto* scene = SceneOfCrime(at);
			if (!faction || !scene) {
				if (loggedCrimes++ < 30) {
					logger::info("SkyCraft-town: griefing seen by {}, but there's {} to report it by; no crime", witness->GetDisplayFullName(),
						faction ? "no object near" : "no crime faction");
				}
				return;
			}
			const auto before = faction->GetCrimeGold();
			// Skyrim's own theft alarm: its witnesses, the guards' reaction and the bounty, as for stealing that much from
			// that faction. No "give it back" warning (a_allowWarning): there's nothing to give back.
			a_player->StealAlarm(scene, scene->GetBaseObject(), count, value, faction, false);
			if (loggedCrimes++ < 30) {
				logger::info("SkyCraft-town: griefing (kind {}, {} block(s), {} gold) seen by {} (detection {}): steal alarm for {:08X} at {} ({:08X}); "
							 "bounty {} -> {}",
					a_event.formId, count, value, witness->GetDisplayFullName(), level, faction->GetFormID(), scene->GetDisplayFullName(), scene->GetFormID(),
					before, faction->GetCrimeGold());
			}
		}

		// ---- guards' remarks --------------------------------------------------------------------------

		void OnBuild(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
		{
			const std::uint32_t kind = a_event.flags;
			if (clock < anyGuardNext || TownKinds::GuardLines(kind) == 0 || Roll() % 100 >= kCommentChance) {
				return;
			}
			auto* lists = RE::ProcessLists::GetSingleton();
			if (!lists) {
				return;
			}
			const auto playerPos = a_player->GetPosition();
			RE::Actor* guard = nullptr;
			float      bestDist = 0.0f;
			for (auto& handle : lists->highActorHandles) {
				auto  actorPtr = handle.get();
				auto* actor = actorPtr.get();
				if (!actor || actor == a_player || !actor->IsGuard() || actor->IsDead() || !actor->Is3DLoaded() || Fighting(actor, a_player)) {
					continue;
				}
				const float dist = actor->GetPosition().GetDistance(playerPos);
				if (dist > kGuardRange || (guard && dist >= bestDist)) {
					continue;
				}
				if (const auto it = guardNext.find(actor->GetFormID()); it != guardNext.end() && clock < it->second) {
					continue;  // said something less than a minute ago
				}
				if (actor->RequestDetectionLevel(a_player) <= 0) {
					continue;  // doesn't see the player
				}
				guard = actor;
				bestDist = dist;
			}
			if (!guard) {
				return;
			}
			std::string_view line = TownKinds::GuardLine(kind, Roll());
			for (int i = 0; i < 3 && line == lastLine; ++i) {
				line = TownKinds::GuardLine(kind, Roll());
			}
			lastLine = line;
			const char* name = guard->GetDisplayFullName();
			// No CommonLib function makes an NPC speak a line of ours (Say needs a dialogue topic in a plugin file), so
			// the guard's words go in Skyrim's corner, with their name.
			const std::string text = std::format("{}: {}", name && *name ? name : "Guard", line);
			RE::SendHUDMessage::ShowHUDMessage(text.c_str());
			guardNext[guard->GetFormID()] = clock + kGuardCooldown;
			anyGuardNext = clock + kAnyGuardCooldown;
			if (loggedComments++ < 30) {
				logger::info("SkyCraft-town: {} ({:08X}) saw the player {}: \"{}\"", name ? name : "?", guard->GetFormID(),
					kind == proto::kBuildPlace ? "build" : kind == proto::kBuildBreak ? "break blocks" : "dig", line);
			}
		}

		// ---- Hearthfire plots ---------------------------------------------------------------------------

		// A location of HearthFires.esm's that is a house: Lakeview Manor, Windstad Manor or Heljarchen Hall, whose
		// outdoor cells are the plot. Told by its file and keywords, not by FormIDs or names (which a language changes).
		bool IsHearthfireHouse(const RE::BGSLocation* a_location)
		{
			const auto* file = a_location ? a_location->GetFile(0) : nullptr;
			return file && TownKinds::IsHearthfireFile(file->GetFilename()) &&
			       (a_location->HasKeywordString("LocTypePlayerHouse") || a_location->HasKeywordString("LocTypeHouse"));
		}

		// The Hearthfire house whose land the player stands on outdoors, or null. Cached by location.
		RE::BGSLocation* HearthfirePlot(RE::PlayerCharacter* a_player)
		{
			auto* cell = a_player->GetParentCell();
			if (!cell || cell->IsInteriorCell()) {
				return nullptr;
			}
			static RE::BGSLocation* cachedFor = nullptr;
			static RE::BGSLocation* cachedPlot = nullptr;
			static bool             cached = false;
			auto*                   location = a_player->GetCurrentLocation();
			if (!cached || location != cachedFor) {
				RE::BGSLocation* plot = nullptr;
				for (auto* l = location; l && !plot; l = l->parentLoc) {
					plot = IsHearthfireHouse(l) ? l : nullptr;
				}
				if (plot != cachedPlot) {
					if (plot) {
						logger::info("SkyCraft-town: on the land of the Hearthfire house {} ({:08X}): Minecraft counts the blocks built here", plot->GetName(),
							plot->GetFormID());
					} else if (cachedPlot) {
						logger::info("SkyCraft-town: off the Hearthfire plot");
					}
				}
				cachedFor = location;
				cachedPlot = plot;
				cached = true;
			}
			return cachedPlot;
		}

		void OnStage(const proto::McEvent& a_event)
		{
			auto*       plot = RE::TESForm::LookupByID<RE::BGSLocation>(a_event.formId);
			const char* stage = a_event.flags == proto::kStageSmallHouse ? "small house" : a_event.flags == proto::kStageMainHall ? "main hall" : "wings";
			// Hearthfire builds its own house parts from the drafting table's crafted plans, through its scripts; nothing
			// here drives those (their quest stages and items aren't known without guessing their FormIDs), so this is told
			// and logged, and the Minecraft blocks are the house.
			logger::info("SkyCraft-town: {} ({:08X}): the {} stands, built of {:.0f} Minecraft blocks", plot ? plot->GetName() : "a Hearthfire plot", a_event.formId,
				stage, a_event.a);
		}

		// What Minecraft counts on the plot (McState), logged as it changes.
		void LogHearthfireCount(float a_delta)
		{
			static float         timer = 0.0f;
			static std::uint32_t lastPlot = 0, lastBlocks = 0;
			static int           logged = 0;
			timer -= a_delta;
			if (timer > 0.0f) {
				return;
			}
			timer = 1.0f;
			proto::McState mc{};
			if (!Link::Get().ReadMcState(mc) || (mc.hearthPlot == lastPlot && mc.hearthBlocks == lastBlocks)) {
				return;
			}
			if (mc.hearthPlot != 0 && logged < 40) {
				++logged;
				logger::info("SkyCraft-town: Minecraft counts {} block(s) standing on the Hearthfire plot {:08X}", mc.hearthBlocks, mc.hearthPlot);
			}
			lastPlot = mc.hearthPlot;
			lastBlocks = mc.hearthBlocks;
		}
	}

	void Install()
	{
		Redstone::Install();
		logger::info("SkyCraft-town: towns and building ready (crime, guards, Hearthfire plots, doors and ladders, Dwemer redstone)");
	}

	void Reset()
	{
		guardNext.clear();
		anyGuardNext = 0.0f;
		Redstone::Reset();
	}

	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
	{
		switch (a_event.type) {
		case proto::kEvTownCrime:
			OnCrime(a_player, a_event);
			break;
		case proto::kEvTownBuild:
			OnBuild(a_player, a_event);
			break;
		case proto::kEvHearthfireStage:
			OnStage(a_event);
			break;
		case proto::kEvRedstoneDoor:
			Redstone::OnDoorPower(a_player, a_event);
			break;
		default:
			break;
		}
	}

	void Update(RE::PlayerCharacter* a_player, bool a_puppeting, float a_delta)
	{
		clock += a_delta;
		Redstone::Update(a_player, a_delta);
		if (a_puppeting) {
			Passages::UpdateClimbers(a_player, a_delta);
		}
		if (Game::FeatureOn(proto::kFeatHearthfire)) {
			LogHearthfireCount(a_delta);
		}
		if (guardNext.size() > 128) {
			std::erase_if(guardNext, [](const auto& a_entry) { return clock >= a_entry.second; });
		}
	}

	void FillSkyState(RE::PlayerCharacter* a_player, proto::SkyState& a_sky)
	{
		a_sky.townFlags = proto::kTownPassages;  // this plugin reads kRenPassages
		a_sky.hearthPlot = 0;
		if (Game::FeatureOn(proto::kFeatHearthfire)) {
			if (auto* plot = HearthfirePlot(a_player)) {
				a_sky.townFlags |= proto::kTownHearthfirePlot;
				a_sky.hearthPlot = plot->GetFormID();
			}
		}
	}

	std::uint32_t ActorFlags(RE::Actor* a_actor) { return a_actor->IsGuard() ? proto::kActorGuard : 0u; }
}
