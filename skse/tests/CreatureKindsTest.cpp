// Tests for CreatureKinds.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src -I protocol skse/tests/CreatureKindsTest.cpp -o creature_kinds_test && ./creature_kinds_test
#include "CreatureKinds.h"

#include <cstdio>

namespace
{
	int failures = 0;

	void Check(bool a_ok, const char* a_what, int a_line)
	{
		if (!a_ok) {
			std::printf("FAILED line %d: %s\n", a_line, a_what);
			++failures;
		}
	}
#define CHECK(x) Check((x), #x, __LINE__)

	using namespace skycraft;
	using CreatureKinds::FromRace;

	void LightShy()
	{
		CHECK(FromRace("SkeeverRace") == proto::kActorLightShy);
		CHECK(FromRace("SkeeverWhiteRace") == proto::kActorLightShy);
		CHECK(FromRace("FalmerRace") == proto::kActorLightShy);
		// Dawnguard's frozen Falmer vampires: both.
		CHECK(FromRace("DLC1FalmerFrozenVampRace") == (proto::kActorLightShy | proto::kActorUndead));
	}

	void Undead()
	{
		CHECK(FromRace("DraugrRace") == proto::kActorUndead);
		CHECK(FromRace("DraugrMagicRace") == proto::kActorUndead);
		CHECK(FromRace("DLC2HulkingDraugrRace") == proto::kActorUndead);
		CHECK(FromRace("SkeletonRace") == proto::kActorUndead);
		CHECK(FromRace("SkeletonNecroRace") == proto::kActorUndead);
		CHECK(FromRace("DLC1SoulCairnSkeletonArmorRace") == proto::kActorUndead);
		CHECK(FromRace("NordRaceVampire") == proto::kActorUndead);
		CHECK(FromRace("DarkElfRaceVampire") == proto::kActorUndead);
		CHECK(FromRace("DLC1VampireBeastRace") == proto::kActorUndead);
	}

	void NeitherKind()
	{
		CHECK(FromRace("NordRace") == 0);
		CHECK(FromRace("KhajiitRace") == 0);
		CHECK(FromRace("WolfRace") == 0);
		CHECK(FromRace("HorseRace") == 0);
		CHECK(FromRace("ChaurusRace") == 0);
		CHECK(FromRace("DragonPriestRace") == 0);
		CHECK(FromRace("WerewolfBeastRace") == 0);
		CHECK(FromRace("") == 0);
		CHECK(FromRace("skeeverrace") == proto::kActorLightShy);  // case doesn't matter
	}

	void WhoFightsBack()
	{
		using CreatureKinds::Fighter;
		using CreatureKinds::FightsBack;
		Fighter guard{ .guard = true };
		Fighter bandit{ .hostileToPlayer = true };
		Fighter follower{ .teammate = true };
		Fighter farmer{};
		Fighter draugr{ .hostileToPlayer = true, .undead = true };
		Fighter child{ .goneFor = true, .child = true };
		const std::uint32_t zombie = proto::kMobUndead, spider = 0, wolf = proto::kMobPlayersWolf;
		CHECK(FightsBack(guard, spider));
		CHECK(FightsBack(bandit, spider));
		CHECK(FightsBack(follower, zombie));
		CHECK(!FightsBack(farmer, spider));
		Fighter attackedFarmer{ .goneFor = true };
		CHECK(FightsBack(attackedFarmer, zombie));  // self defence
		// Undead kin leave each other be, unless one comes for the other.
		CHECK(!FightsBack(draugr, zombie));
		CHECK(FightsBack(draugr, spider));
		Fighter attackedDraugr{ .hostileToPlayer = true, .goneFor = true, .undead = true };
		CHECK(FightsBack(attackedDraugr, zombie));
		// The player's wolf: only the player's enemies.
		CHECK(FightsBack(bandit, wolf));
		CHECK(!FightsBack(guard, wolf));
		CHECK(!FightsBack(follower, wolf));
		Fighter wolfsVictimGuard{ .guard = true, .goneFor = true };
		CHECK(!FightsBack(wolfsVictimGuard, wolf));
		CHECK(!FightsBack(child, spider));
	}

	void Light()
	{
		using CreatureKinds::LightFrom;
		using CreatureKinds::ShyPushSpeed;
		CHECK(LightFrom(14, 0, 0, 0) == 14);
		CHECK(LightFrom(14, 3, -2, 1) == 8);
		CHECK(LightFrom(14, 10, 10, 0) == 0);
		// A torch keeps a skeever 7 blocks off, a soul torch 3, a redstone torch (7) never.
		CHECK(LightFrom(14, 6, 0, 0) >= CreatureKinds::kShyLight);
		CHECK(LightFrom(14, 7, 0, 0) < CreatureKinds::kShyLight);
		CHECK(LightFrom(10, 2, 0, 0) >= CreatureKinds::kShyLight);
		CHECK(LightFrom(7, 0, 0, 0) < CreatureKinds::kShyLight);
		CHECK(ShyPushSpeed(7) == 0.0f);
		CHECK(ShyPushSpeed(8) == 1.5f);
		CHECK(ShyPushSpeed(15) == 6.0f);
	}
}

int main()
{
	LightShy();
	Undead();
	NeitherKind();
	WhoFightsBack();
	Light();
	static_assert(CreatureKinds::FromRace("SkeeverRace") == proto::kActorLightShy);
	if (failures) {
		std::printf("%d failed\n", failures);
		return 1;
	}
	std::printf("CreatureKinds: all passed\n");
	return 0;
}
