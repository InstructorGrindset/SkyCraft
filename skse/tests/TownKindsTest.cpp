// Tests for TownKinds.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src -I protocol skse/tests/TownKindsTest.cpp -o town_kinds_test && ./town_kinds_test
#include "TownKinds.h"

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
	using TownKinds::Switch;
	using TownKinds::SwitchFromModel;

	void Switches()
	{
		CHECK(SwitchFromModel("Dungeons\\Dwemer\\Levers\\DweLever01.nif") == Switch::kLever);
		CHECK(SwitchFromModel("dungeons\\nordic\\levers\\NorPullBar01.nif") == Switch::kLever);
		CHECK(SwitchFromModel("Dungeons\\Shared\\PullChain01.nif") == Switch::kLever);
		CHECK(SwitchFromModel("Dungeons\\Dwemer\\DweButton01.nif") == Switch::kButton);
		CHECK(SwitchFromModel("Dungeons\\Nordic\\Traps\\NorPressurePlate01.nif") == Switch::kPlate);
		CHECK(SwitchFromModel("Traps\\TrapPressurePlate01.nif") == Switch::kPlate);
		// A plate kept in a folder of levers is still a plate.
		CHECK(SwitchFromModel("Dungeons\\Dwemer\\Levers\\DwePressurePlate01.nif") == Switch::kPlate);
		CHECK(SwitchFromModel("Clutter\\Barrel01.nif") == Switch::kNone);
		CHECK(SwitchFromModel("") == Switch::kNone);
		CHECK(TownKinds::PulseTicks(Switch::kLever) > TownKinds::PulseTicks(Switch::kButton));
		CHECK(TownKinds::PulseTicks(Switch::kNone) == 0);
	}

	void DwemerDoors()
	{
		CHECK(TownKinds::IsDwemerDoorModel("Dungeons\\Dwemer\\Doors\\DweDoor01.nif"));
		CHECK(TownKinds::IsDwemerDoorModel("dungeons\\dwemer\\doors\\dwegatesmall01.nif"));
		CHECK(TownKinds::IsDwemerDoorModel("SomeMod\\DweGate.nif"));
		CHECK(!TownKinds::IsDwemerDoorModel("Architecture\\Whiterun\\WRDoor01.nif"));
		CHECK(!TownKinds::IsDwemerDoorModel("Dungeons\\Nordic\\NorDoorSmall01.nif"));
	}

	void Hearthfire()
	{
		CHECK(TownKinds::IsHearthfireFile("HearthFires.esm"));
		CHECK(TownKinds::IsHearthfireFile("hearthfires.ESM"));
		CHECK(!TownKinds::IsHearthfireFile("Skyrim.esm"));
		CHECK(!TownKinds::IsHearthfireFile("HearthFires.esp"));
	}

	void GuardLines()
	{
		for (std::uint32_t kind : { proto::kBuildPlace, proto::kBuildBreak, proto::kBuildDig }) {
			CHECK(TownKinds::GuardLines(kind) >= 3);
			for (std::uint32_t roll = 0; roll < 20; ++roll) {
				const auto line = TownKinds::GuardLine(kind, roll);
				CHECK(!line.empty() && line.size() < 64);  // fits Skyrim's corner
			}
		}
		CHECK(TownKinds::GuardLine(proto::kBuildPlace, 0) != TownKinds::GuardLine(proto::kBuildPlace, 1));
		CHECK(TownKinds::GuardLines(0) == 0 && TownKinds::GuardLine(99, 3).empty());
	}
}

int main()
{
	Switches();
	DwemerDoors();
	Hearthfire();
	GuardLines();
	if (failures == 0) {
		std::printf("TownKinds: all passed\n");
	}
	return failures == 0 ? 0 : 1;
}
