// Tests for Stealth.h and BlockWalk.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src -I protocol skse/tests/StealthTest.cpp -o stealth_test && ./stealth_test
#include "BlockWalk.h"
#include "Stealth.h"

#include "skycraft_protocol.h"

#include <array>
#include <cmath>
#include <cstdlib>
#include <cstdio>
#include <iterator>

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

	bool Near(float a_a, float a_b, float a_within = 0.01f) { return std::fabs(a_a - a_b) <= a_within; }

	// Minecraft's own brightness curve: f / (4 - 3f), f = level / 15.
	void BrightnessIsMinecrafts()
	{
		CHECK(Stealth::Brightness(15) == 1.0f);
		CHECK(Stealth::Brightness(0) == 0.0f);
		CHECK(Near(Stealth::Brightness(12), 0.5f));
		CHECK(Near(Stealth::Brightness(8), 0.2222f, 0.001f));
		CHECK(Near(Stealth::Brightness(14), 0.7778f, 0.001f));
		// Out of range levels are the nearest real one.
		CHECK(Stealth::Brightness(20) == 1.0f);
		CHECK(Stealth::Brightness(-3) == 0.0f);
		// Brighter for every level up.
		for (int level = 0; level < 15; ++level) {
			CHECK(Stealth::Brightness(level) < Stealth::Brightness(level + 1));
		}
	}

	// Minecraft's 0-15 on Skyrim's light level scale.
	void BlockLightOnSkyrimsScale()
	{
		CHECK(Stealth::FromBlockLight(15) == Stealth::kFullLight);
		CHECK(Stealth::FromBlockLight(0) == 0.0f);
		CHECK(Near(Stealth::FromBlockLight(14), 77.8f, 0.1f));  // next to a torch
		CHECK(Near(Stealth::FromBlockLight(12), 50.0f, 0.1f));  // two blocks from it
		CHECK(Near(Stealth::FromBlockLight(10), 33.3f, 0.1f));
		CHECK(Near(Stealth::FromBlockLight(6), 14.3f, 0.1f));
	}

	void PlayerLight()
	{
		// Out in the open with no Minecraft light: Skyrim's own light, day or night, inside or out.
		CHECK(Stealth::PlayerLight(100.0f, 0, 15) == 100.0f);
		CHECK(Stealth::PlayerLight(25.0f, 0, 15) == 25.0f);
		// Sealed into a tunnel dug into the ground, no torch: dark, whatever Skyrim's sun says.
		CHECK(Stealth::PlayerLight(120.0f, 0, 0) == 0.0f);
		// A torch in that tunnel lights the player up.
		CHECK(Near(Stealth::PlayerLight(120.0f, 14, 0), 77.8f, 0.1f));
		// Under a roof of blocks: some of Skyrim's daylight still gets in.
		CHECK(Near(Stealth::PlayerLight(100.0f, 0, 12), 50.0f, 0.1f));
		CHECK(Stealth::PlayerLight(100.0f, 0, 14) < 100.0f && Stealth::PlayerLight(100.0f, 0, 14) > 50.0f);
		// A torch never makes a bright place darker, and Skyrim's light is never made brighter by its sky.
		CHECK(Stealth::PlayerLight(150.0f, 14, 15) == 150.0f);
		CHECK(Stealth::PlayerLight(10.0f, 14, 15) > 10.0f);
		// Nonsense from Skyrim (below 0) is no light.
		CHECK(Stealth::PlayerLight(-5.0f, 0, 15) == 0.0f);
		// More cover is never brighter.
		for (int sky = 0; sky < 15; ++sky) {
			CHECK(Stealth::PlayerLight(80.0f, 3, sky) <= Stealth::PlayerLight(80.0f, 3, sky + 1));
		}
	}

	void CellsAlongALine()
	{
		CHECK(BlockWalk::BlockOf(2.7) == 2);
		CHECK(BlockWalk::BlockOf(-0.5) == -1);
		CHECK(BlockWalk::BlockOf(-1.0) == -1);
		CHECK(BlockWalk::BlockOf(-2.3) == -3);
		// The same block: one.
		CHECK(BlockWalk::CellsAlong(0.2, 64.5, 0.2, 0.8, 64.9, 0.7) == 1);
		// Ten blocks east: eleven.
		CHECK(BlockWalk::CellsAlong(0.5, 64.5, 0.5, 10.5, 64.5, 0.5) == 11);
		// Across zero, and diagonally: one for each face crossed.
		CHECK(BlockWalk::CellsAlong(-1.5, 64.5, -1.5, 1.5, 66.5, 1.5) == 1 + 3 + 2 + 3);
		CHECK(BlockWalk::CellsAlong(1.5, 66.5, 1.5, -1.5, 64.5, -1.5) == BlockWalk::CellsAlong(-1.5, 64.5, -1.5, 1.5, 66.5, 1.5));
		// A walk visits exactly that many blocks, each one face away from the last.
		for (const auto& line : { std::array<double, 6>{ 0.5, 64.5, 0.5, 10.5, 64.5, 0.5 }, std::array<double, 6>{ -1.5, 64.5, -1.5, 1.5, 66.5, 1.7 },
				 std::array<double, 6>{ 3.2, 70.9, -7.4, -12.6, 64.1, 5.3 } }) {
			long long visited = 0;
			int       last[3] = { 0, 0, 0 };
			bool      steps = true;
			BlockWalk::Walk(line[0], line[1], line[2], line[3], line[4], line[5], 1 << 20, [&](int a_x, int a_y, int a_z, double) {
				if (visited > 0) {
					steps = steps && std::abs(a_x - last[0]) + std::abs(a_y - last[1]) + std::abs(a_z - last[2]) == 1;
				}
				last[0] = a_x, last[1] = a_y, last[2] = a_z;
				++visited;
				return false;
			});
			CHECK(visited == BlockWalk::CellsAlong(line[0], line[1], line[2], line[3], line[4], line[5]));
			CHECK(steps);
			CHECK(last[0] == BlockWalk::BlockOf(line[3]) && last[1] == BlockWalk::BlockOf(line[4]) && last[2] == BlockWalk::BlockOf(line[5]));
		}
		CHECK(Stealth::kChestShare > 0.5f && Stealth::kChestShare < 1.0f);
	}

	// An NPC at x 0.5 looks east along y 65.6 at a player's head at x 10.5.
	void WallsBetween()
	{
		auto wallAt = [](int a_wallX) { return [a_wallX](int a_x, int a_y, int) { return a_x == a_wallX && a_y == 65; }; };
		auto none = [](int, int, int) { return false; };
		const long long max = Stealth::kMaxSightCells;
		CHECK(!BlockWalk::SolidBetween(0.5, 65.6, 0.5, 10.5, 65.6, 0.5, max, none));
		// A block wall half way: hidden.
		CHECK(BlockWalk::SolidBetween(0.5, 65.6, 0.5, 10.5, 65.6, 0.5, max, wallAt(5)));
		// Blocks the NPC's eyes or the player's head are in don't count (a slab, a carpet, a snow layer).
		CHECK(!BlockWalk::SolidBetween(0.5, 65.6, 0.5, 10.5, 65.6, 0.5, max, wallAt(0)));
		CHECK(!BlockWalk::SolidBetween(0.5, 65.6, 0.5, 10.5, 65.6, 0.5, max, wallAt(10)));
		// The block just in front of either does.
		CHECK(BlockWalk::SolidBetween(0.5, 65.6, 0.5, 10.5, 65.6, 0.5, max, wallAt(1)));
		CHECK(BlockWalk::SolidBetween(0.5, 65.6, 0.5, 10.5, 65.6, 0.5, max, wallAt(9)));
		// A wall past the player, or behind the NPC, hides nothing.
		CHECK(!BlockWalk::SolidBetween(0.5, 65.6, 0.5, 10.5, 65.6, 0.5, max, wallAt(11)));
		CHECK(!BlockWalk::SolidBetween(0.5, 65.6, 0.5, 10.5, 65.6, 0.5, max, wallAt(-1)));
		// Looking the other way gives the same answer.
		CHECK(BlockWalk::SolidBetween(10.5, 65.6, 0.5, 0.5, 65.6, 0.5, max, wallAt(5)));
		// A wall one block high under the line (the NPC looks over it) hides nothing.
		CHECK(!BlockWalk::SolidBetween(0.5, 66.6, 0.5, 10.5, 66.6, 0.5, max, wallAt(5)));
		// A line too long to walk: no wall seen.
		CHECK(!BlockWalk::SolidBetween(0.5, 65.6, 0.5, 5000.5, 65.6, 0.5, max, [](int, int, int) { return true; }));
	}

	void SneakAttacks()
	{
		// Vanilla's bases: one-handed melee three times, bows and the rest twice (Backstab makes the first six, Deadly
		// Aim the bow three, Assassin's Blade a dagger fifteen).
		CHECK(Stealth::SneakAttackBase(false, true) == 3.0f);
		CHECK(Stealth::SneakAttackBase(false, false) == 2.0f);
		CHECK(Stealth::SneakAttackBase(true, false) == 2.0f);
		CHECK(Stealth::SneakAttackBase(true, true) == 2.0f);  // a thrown trident is a ranged attack
		CHECK(Stealth::ClampSneakMult(3.0f * 2.0f * 2.5f) == 15.0f);
		CHECK(Stealth::ClampSneakMult(0.0f) == 1.0f);
		CHECK(Stealth::ClampSneakMult(1000.0f) == 30.0f);
		CHECK(Stealth::SneakAttackSkillUses(false) > Stealth::SneakAttackSkillUses(true));
		CHECK(Stealth::SneakAttackSkillUses(true) > 0.0f);
	}

	// The protocol's new bits are bits of their own.
	void ProtocolBits()
	{
		constexpr std::uint32_t hit[] = { proto::kHitCritical, proto::kHitProjectile, proto::kHitSweep, proto::kHitFire, proto::kHitPowerAttack, proto::kHitCrouching };
		for (std::uint32_t i = 0; i < std::size(hit); ++i) {
			CHECK(hit[i] == 1u << i);
		}
		constexpr std::uint32_t features = proto::kFeatWallsHideYou | proto::kFeatSneakAttacks | proto::kFeatPowerAttacks | proto::kFeatLightStealth;
		CHECK(features == 0xFu);
		CHECK((features & proto::kFeaturesValid) == 0);
		CHECK(proto::kEyeLightValid == 1);
	}

	static_assert(Stealth::PlayerLight(100.0f, 0, 0) == 0.0f, "usable at compile time");
	static_assert(BlockWalk::CellsAlong(0.5, 0.5, 0.5, 3.5, 0.5, 0.5) == 4, "usable at compile time");
}

int main()
{
	BrightnessIsMinecrafts();
	BlockLightOnSkyrimsScale();
	PlayerLight();
	CellsAlongALine();
	WallsBetween();
	SneakAttacks();
	ProtocolBits();
	if (failures) {
		std::printf("%d check(s) failed\n", failures);
		return 1;
	}
	std::printf("Stealth: all checks passed\n");
	return 0;
}
