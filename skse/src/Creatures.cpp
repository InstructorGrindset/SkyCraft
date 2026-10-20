#include "Creatures.h"

#include "CreatureKinds.h"
#include "Game.h"

#include <random>

namespace skycraft::Creatures
{
	namespace
	{
		constexpr float kRangeUnits = 64.0f * static_cast<float>(proto::kUnitsPerBlock);  // actors this near the player take part
		constexpr float kMobMemorySeconds = 1.5f;   // a Minecraft creature not heard of for this long is gone
		constexpr float kFightTick = 0.2f;          // how often actors look for a creature to swing at
		constexpr float kBlowDelay = 0.3f;          // the blow lands this long into the attack animation
		constexpr float kCooldownMin = 1.4f;        // seconds between one actor's swings
		constexpr float kCooldownMax = 2.4f;
		constexpr double kHeightBlocks = 2.5;       // a creature further above or below an actor's feet is out of reach
		constexpr float kSkyrimToMcDamage = 5.0f;   // as Skyrim's hits on the player (SkyCombat.SKYRIM_TO_MC_DAMAGE)
		constexpr float kFlamesSeconds = 1.5f;      // how often burning flames are cast again on an actor
		constexpr RE::FormID kHazardFireSpell = 0x000153BD;  // Skyrim.esm HazardFireSpell, as Combat.cpp's lava and fire use it
		constexpr std::size_t kMaxMobs = 64;

		struct Mob
		{
			std::int32_t  id{ 0 };     // Minecraft entity id
			McVec         at{};        // feet, Minecraft coords
			float         radius{ 0.3f };
			std::uint32_t traits{ 0 };  // proto::MobTraits
			RE::FormID    goesFor{ 0 };
			float         age{ 0.0f };
		};
		std::vector<Mob> mobs;

		// A swing whose blow hasn't landed yet.
		struct Blow
		{
			RE::FormID    actor{ 0 };
			std::int32_t  mob{ 0 };
			float         damage{ 0.0f };  // Minecraft damage
			std::uint16_t weapon{ proto::kWeaponUnarmed };
			float         delay{ 0.0f };
		};
		std::vector<Blow> blows;

		std::unordered_map<RE::FormID, float>                 cooldowns;  // actor -> seconds until it may swing again
		std::unordered_map<RE::FormID, float>                 flames;     // actor -> seconds until flames are cast again
		std::unordered_map<const RE::TESRace*, std::uint32_t> raceBits;   // races live as long as the game
		float                                                 fightTimer = 0.0f;
		int                                                   logged = 0;

		std::mt19937& Rng()
		{
			static std::mt19937 rng{ std::random_device{}() };
			return rng;
		}

		// The first forty of these lines are logged.
		bool Log()
		{
			if (logged >= 40) {
				return false;
			}
			++logged;
			return true;
		}

		RE::TESObjectWEAP* Weapon(RE::Actor* a_actor)
		{
			auto* form = a_actor->GetEquippedObject(false);
			return form ? form->As<RE::TESObjectWEAP>() : nullptr;
		}

		// What a Skyrim weapon's blow looks like to Minecraft (proto::HitWeapon).
		std::uint16_t WeaponClass(const RE::TESObjectWEAP* a_weapon)
		{
			if (!a_weapon) {
				return proto::kWeaponUnarmed;
			}
			switch (a_weapon->GetWeaponType()) {
			case RE::WEAPON_TYPE::kOneHandSword:
			case RE::WEAPON_TYPE::kTwoHandSword:
				return proto::kWeaponBlade;
			case RE::WEAPON_TYPE::kOneHandAxe:
			case RE::WEAPON_TYPE::kTwoHandAxe:
				return proto::kWeaponAxe;
			case RE::WEAPON_TYPE::kOneHandDagger:
				return proto::kWeaponPierce;
			case RE::WEAPON_TYPE::kHandToHandMelee:
				return proto::kWeaponUnarmed;
			default:
				return proto::kWeaponBlunt;  // maces, and a bow's or staff's bash
			}
		}

		// A Skyrim swing's damage in Minecraft's terms: the weapon's (a bow or staff only bashes, a fist or a creature's bite is
		// its race's unarmed damage), more for higher levels, divided as Skyrim's hits on the player are.
		float SwingDamage(RE::Actor* a_actor, const RE::TESObjectWEAP* a_weapon, bool a_bash)
		{
			float base = a_weapon && !a_bash ? static_cast<float>(a_weapon->GetAttackDamage()) : 0.0f;
			if (!(base > 0.0f)) {
				const auto* race = a_actor->GetRace();
				base = race ? race->data.unarmedDamage : 0.0f;
			}
			if (!(base > 0.0f)) {
				base = 4.0f;
			}
			const float skyrim = base * (1.0f + static_cast<float>(a_actor->GetLevel()) / 40.0f);
			return std::clamp(skyrim / kSkyrimToMcDamage, 0.5f, 20.0f);
		}

		// A vanilla weapon standing in for a Minecraft creature's blow, for Skyrim's impact effect and sound (as Combat.cpp's).
		RE::TESObjectWEAP* StandInWeapon(std::uint32_t a_weapon)
		{
			RE::FormID id = 0x00013982;  // Iron Mace: fists, claws, anything blunt
			switch (a_weapon) {
			case proto::kWeaponBlade:
				id = 0x00012EB7;  // Iron Sword
				break;
			case proto::kWeaponAxe:
				id = 0x00013790;  // Iron War Axe
				break;
			case proto::kWeaponPierce:
				id = 0x0001397E;  // Iron Dagger
				break;
			case proto::kWeaponArrow:
				id = 0x00013985;  // Hunting Bow
				break;
			default:
				break;
			}
			return RE::TESForm::LookupByID<RE::TESObjectWEAP>(id);
		}

		RE::NiAVObject* HitNode(RE::Actor* a_actor)
		{
			auto* root = a_actor->Get3D();
			if (!root) {
				return nullptr;
			}
			for (const char* name : { "NPC Spine2 [Spn2]", "NPC Spine1 [Spn1]", "NPC Spine [Spn0]" }) {
				if (auto* node = root->GetObjectByName(name)) {
					return node;
				}
			}
			return root;
		}

		void Remember(const proto::McEvent& a_ev)
		{
			if (!std::isfinite(a_ev.a) || !std::isfinite(a_ev.b) || !std::isfinite(a_ev.c)) {
				return;
			}
			const auto id = static_cast<std::int32_t>(a_ev.formId);
			auto       it = std::ranges::find_if(mobs, [id](const Mob& a_m) { return a_m.id == id; });
			if (it == mobs.end()) {
				if (mobs.size() >= kMaxMobs) {
					return;
				}
				mobs.push_back({});
				it = mobs.end() - 1;
			}
			it->id = id;
			it->at = { a_ev.a, a_ev.b, a_ev.c };
			it->radius = std::clamp(std::isfinite(a_ev.d) ? a_ev.d : 0.3f, 0.1f, 2.0f);
			it->traits = (a_ev.flags >> proto::kMobTraitsShift) & 0xFF;
			it->goesFor = a_ev.weapon;
			it->age = 0.0f;
		}

		// A Minecraft creature's hit, or the sun's: real damage scaled like a Minecraft hit on the actor, credited to no one, so
		// no crime, no kill credit and no fight with the player.
		void TakeHit(RE::PlayerCharacter* a_player, const proto::McEvent& a_ev)
		{
			auto* actor = RE::TESForm::LookupByID<RE::Actor>(a_ev.formId);
			if (!actor || actor == a_player || actor->IsDead() || !actor->Is3DLoaded() || actor->GetPosition().GetDistance(a_player->GetPosition()) > kRangeUnits * 2.0f) {
				return;
			}
			const std::uint32_t hitFlags = (a_ev.flags >> proto::kMobHitFlagsShift) & 0xFF;
			const std::uint32_t attacker = (a_ev.flags >> proto::kMobAttackerShift) & 0xF;
			const float         scale = 5.0f + 0.25f * static_cast<float>(actor->GetLevel());
			const float         damage = std::isfinite(a_ev.a) ? std::clamp(a_ev.a, 0.0f, 100.0f) * scale : 0.0f;
			if (damage > 0.0f) {
				actor->DoDamage(damage, nullptr, true);
			}
			const float push = std::isfinite(a_ev.d) ? a_ev.d : 0.0f;
			if (push > 0.45f && !actor->IsDead()) {
				// A creature's strong shove staggers (Combat.cpp's fallback stagger): Minecraft pushes along (b, c).
				const float pushHeading = std::atan2(a_ev.b, -a_ev.c);
				float       dir = (pushHeading - actor->GetAngleZ()) / (2.0f * 3.14159265f) + 0.5f;
				dir -= std::floor(dir);
				actor->SetGraphVariableFloat("staggerDirection", dir);
				actor->SetGraphVariableFloat("staggerMagnitude", std::clamp((push - 0.4f) * 1.2f, 0.25f, 1.0f));
				actor->NotifyAnimationGraph("staggerStart");
			}
			if ((hitFlags & proto::kHitFire) && !actor->IsDead()) {
				// Burning (the sun on the undead, a blaze's fire): flames on the body, as Minecraft's lava and fire show them.
				auto& wait = flames[actor->GetFormID()];
				static auto* fireSpell = RE::TESForm::LookupByID<RE::SpellItem>(kHazardFireSpell);
				if (wait <= 0.0f && fireSpell) {
					if (auto* caster = actor->GetMagicCaster(RE::MagicSystem::CastingSource::kInstant)) {
						caster->CastSpellImmediate(fireSpell, false, actor, 1.0f, false, 0.0f, nullptr);
					}
					wait = kFlamesSeconds;
				}
			} else if (damage > 0.0f) {
				// Blood and the blow's sound, as a Skyrim weapon connecting.
				auto* weapon = StandInWeapon(a_ev.weapon);
				auto* node = HitNode(actor);
				if (auto* impacts = RE::BGSImpactManager::GetSingleton(); impacts && weapon && weapon->impactDataSet && node) {
					RE::NiPoint3 dir{ a_ev.b, -a_ev.c, 0.0f };
					impacts->PlayImpactEffect(actor, weapon->impactDataSet, node->name.c_str(), dir, 128.0f, false, false);
				}
			}
			if (Log()) {
				logger::info("SkyCraft-creatures: {} hit {} ({:08X}, level {}) for {:.1f} Minecraft -> {:.0f} Skyrim damage{}",
					attacker == proto::kMobBySun ? "daylight" : "a Minecraft creature", actor->GetDisplayFullName(), a_ev.formId, actor->GetLevel(), a_ev.a, damage,
					(hitFlags & proto::kHitFire) ? ", burning" : "");
			}
		}

		// Starts an actor's swing at a Minecraft creature next to it: it turns to it and plays an attack; the blow lands on
		// Minecraft's side kBlowDelay later.
		void Swing(RE::Actor* a_actor, const Mob& a_mob)
		{
			const auto at = a_actor->GetPosition();
			const auto to = McToSky(a_mob.at.x, a_mob.at.y, a_mob.at.z);
			a_actor->SetHeading(std::atan2(to.x - at.x, to.y - at.y));
			auto*       weapon = Weapon(a_actor);
			const bool  bash = weapon && (weapon->IsBow() || weapon->IsCrossbow() || weapon->IsStaff());
			const bool  played = a_actor->NotifyAnimationGraph(bash ? "bashStart" : "attackStart");
			const float damage = SwingDamage(a_actor, weapon, bash);
			blows.push_back({ a_actor->GetFormID(), a_mob.id, damage, bash ? std::uint16_t(proto::kWeaponBlunt) : WeaponClass(weapon), kBlowDelay });
			cooldowns[a_actor->GetFormID()] = std::uniform_real_distribution<float>(kCooldownMin, kCooldownMax)(Rng());
			if (Log()) {
				logger::info("SkyCraft-creatures: {} ({:08X}) swings at a Minecraft {} for {:.1f} Minecraft damage{}", a_actor->GetDisplayFullName(), a_actor->GetFormID(),
					(a_mob.traits & proto::kMobPlayersWolf) ? "wolf (the player's)" : "creature", damage, played ? "" : " (its animation graph didn't take the attack)");
			}
		}

		// Guards, the player's followers and enemies, and whoever a creature goes for, swing at one next to them.
		void FightBack(RE::PlayerCharacter* a_player)
		{
			const bool monsters = Game::FeatureOn(proto::kFeatMobsFightNpcs);
			const bool wolves = Game::FeatureOn(proto::kFeatWolvesFightSkyrim);
			auto*      lists = RE::ProcessLists::GetSingleton();
			if ((!monsters && !wolves) || mobs.empty() || !lists) {
				return;
			}
			const auto playerPos = a_player->GetPosition();
			for (auto& handle : lists->highActorHandles) {
				auto  actorPtr = handle.get();
				auto* actor = actorPtr.get();
				if (!actor || actor == a_player || actor->IsDead() || actor->IsDisabled() || !actor->Is3DLoaded() || actor->GetPosition().GetDistance(playerPos) > kRangeUnits) {
					continue;
				}
				if (cooldowns.contains(actor->GetFormID())) {
					continue;  // swung a moment ago
				}
				if (actor->IsAttacking() || actor->IsStaggering() || actor->IsInRagdollState() || actor->IsInBleedout() || actor->IsOnMount() || actor->IsBeingRidden() ||
					actor->IsInKillMove()) {
					continue;
				}
				const auto   mc = SkyToMc(actor->GetPosition());
				const double radius = std::clamp(double(actor->GetBoundRadius()) / proto::kUnitsPerBlock, 0.2, 1.5);
				double       reach = double(actor->GetReach()) / proto::kUnitsPerBlock;
				reach = std::isfinite(reach) && reach > 0.5 ? std::clamp(reach, 1.0, 3.0) : 2.0;
				const auto*            race = actor->GetRace();
				CreatureKinds::Fighter fighter;
				bool                   known = false;
				const Mob*             best = nullptr;
				double                 bestGap = 1e9;
				for (const auto& m : mobs) {
					if ((m.traits & proto::kMobPlayersWolf) ? !wolves : !monsters) {
						continue;
					}
					if (std::abs(m.at.y - mc.y) > kHeightBlocks) {
						continue;
					}
					const double gap = std::hypot(m.at.x - mc.x, m.at.z - mc.z) - radius - m.radius;
					if (gap > reach || gap >= bestGap) {
						continue;
					}
					if (!known) {
						// What the actor is, worked out only for one with a creature in reach.
						known = true;
						fighter.guard = actor->IsGuard();
						fighter.hostileToPlayer = actor->IsHostileToActor(a_player);
						fighter.teammate = actor->IsPlayerTeammate();
						fighter.undead = (Flags(actor) & proto::kActorUndead) != 0;
						fighter.child = race && race->IsChildRace();
					}
					fighter.goneFor = m.goesFor == actor->GetFormID();
					if (!CreatureKinds::FightsBack(fighter, m.traits)) {
						continue;
					}
					best = &m;
					bestGap = gap;
				}
				if (best) {
					Swing(actor, *best);
				}
			}
		}

		// Skeevers and Falmer keep out of strong Minecraft light: pushed away from the brightest block lighting them, never into a block.
		void ShyFromLight(RE::PlayerCharacter* a_player, float a_delta)
		{
			auto* lists = RE::ProcessLists::GetSingleton();
			if (!lists || !Game::FeatureOn(proto::kFeatCreaturesAndLight)) {
				return;
			}
			const auto playerPos = a_player->GetPosition();
			for (auto& handle : lists->highActorHandles) {
				auto  actorPtr = handle.get();
				auto* actor = actorPtr.get();
				if (!actor || actor == a_player || actor->IsDead() || !actor->Is3DLoaded() || actor->IsPlayerTeammate() ||
					actor->GetPosition().GetDistance(playerPos) > kRangeUnits || !(Flags(actor) & proto::kActorLightShy)) {
					continue;
				}
				const auto  sky = actor->GetPosition();
				const auto  mc = SkyToMc(sky);
				McVec       from{};
				const int   light = BlockLights::LightAt({ mc.x, mc.y + 0.5, mc.z }, from);
				const float speed = CreatureKinds::ShyPushSpeed(light);
				if (speed <= 0.0f) {
					continue;
				}
				double dx = mc.x - from.x, dz = mc.z - from.z;
				double len = std::hypot(dx, dz);
				if (len < 1e-3) {
					// Right on top of it: back off the way it came (Skyrim heading -> Minecraft x, z).
					const float heading = actor->GetAngleZ();
					dx = -std::sin(heading), dz = std::cos(heading), len = 1.0;
				}
				const double step = double(speed) * a_delta;
				const double nx = mc.x + dx / len * step, nz = mc.z + dz / len * step;
				const double h = std::clamp(double(actor->GetHeight()) / proto::kUnitsPerBlock, 0.5, 4.0);
				bool         blocked = false;
				for (int y = int(std::floor(mc.y + 0.3)); y <= int(std::floor(mc.y + h - 0.1)) && !blocked; ++y) {
					blocked = NpcBlocks::SolidAt(int(std::floor(nx)), y, int(std::floor(nz)));
				}
				if (blocked) {
					continue;
				}
				auto pos = McToSky(nx, mc.y, nz);
				pos.z = sky.z;
				actor->SetPosition(pos, true);
				static int shyLogged = 0;
				if (shyLogged < 10) {
					++shyLogged;
					logger::info("SkyCraft-creatures: {} ({:08X}) keeps away from the light (level {} where it stands)", actor->GetDisplayFullName(), actor->GetFormID(), light);
				}
			}
		}
	}

	std::uint32_t Flags(RE::Actor* a_actor)
	{
		const RE::TESRace* race = a_actor ? a_actor->GetRace() : nullptr;
		if (!race) {
			return 0;
		}
		if (const auto it = raceBits.find(race); it != raceBits.end()) {
			return it->second;
		}
		const char*         id = race->GetFormEditorID();
		const std::uint32_t bits = CreatureKinds::FromRace(id ? id : "");
		raceBits.emplace(race, bits);
		if (bits) {
			logger::info("SkyCraft-creatures: race {} ({:08X}) is{}{}", id ? id : "?", race->GetFormID(), (bits & proto::kActorLightShy) ? " light-shy" : "",
				(bits & proto::kActorUndead) ? " undead" : "");
		}
		return bits;
	}

	void OnMobEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
	{
		switch (a_event.flags & 0xFF) {
		case proto::kMobNear:
			Remember(a_event);
			break;
		case proto::kMobHit:
			TakeHit(a_player, a_event);
			break;
		default:
			break;
		}
	}

	void Update(RE::PlayerCharacter* a_player, float a_delta)
	{
		std::erase_if(mobs, [a_delta](Mob& a_m) { return (a_m.age += a_delta) > kMobMemorySeconds; });
		std::erase_if(cooldowns, [a_delta](auto& a_entry) { return (a_entry.second -= a_delta) <= 0.0f; });
		std::erase_if(flames, [a_delta](auto& a_entry) { return (a_entry.second -= a_delta) <= 0.0f; });
		for (auto it = blows.begin(); it != blows.end();) {
			it->delay -= a_delta;
			if (it->delay > 0.0f) {
				++it;
				continue;
			}
			auto* actor = RE::TESForm::LookupByID<RE::Actor>(it->actor);
			if (actor && !actor->IsDead() && !actor->IsStaggering()) {
				Link::Get().PushInput(proto::kInMobHit, it->weapon, it->mob, static_cast<std::int32_t>(it->damage * 100.0f), static_cast<std::int32_t>(it->actor));
			}
			it = blows.erase(it);
		}
		fightTimer -= a_delta;
		if (fightTimer <= 0.0f) {
			fightTimer = kFightTick;
			FightBack(a_player);
		}
		ShyFromLight(a_player, a_delta);
	}

	void Reset()
	{
		mobs.clear();
		blows.clear();
		cooldowns.clear();
		flames.clear();
	}
}
