// Tests for BlessingKinds.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src -I protocol skse/tests/BlessingKindsTest.cpp -o blessing_kinds_test && ./blessing_kinds_test
#include "BlessingKinds.h"

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
	using BlessingKinds::FromSpellName;

	void TheNine()
	{
		CHECK(FromSpellName("Blessing of Akatosh") == proto::kBlessingAkatosh);
		CHECK(FromSpellName("Blessing of Arkay") == proto::kBlessingArkay);
		CHECK(FromSpellName("Blessing of Dibella") == proto::kBlessingDibella);
		CHECK(FromSpellName("Blessing of Julianos") == proto::kBlessingJulianos);
		CHECK(FromSpellName("Blessing of Kynareth") == proto::kBlessingKynareth);
		CHECK(FromSpellName("Blessing of Mara") == proto::kBlessingMara);
		CHECK(FromSpellName("Blessing of Stendarr") == proto::kBlessingStendarr);
		CHECK(FromSpellName("Blessing of Talos") == proto::kBlessingTalos);
		CHECK(FromSpellName("Blessing of Zenithar") == proto::kBlessingZenithar);
	}

	void TheDlcsAndOthers()
	{
		CHECK(FromSpellName("Blessing of Auriel") == proto::kBlessingAuriel);
		CHECK(FromSpellName("Blessing of Azura") == proto::kBlessingOther);
		CHECK(FromSpellName("Blessing of the Ancestors") == proto::kBlessingOther);
		CHECK(FromSpellName("blessing of TALOS") == proto::kBlessingTalos);  // case doesn't matter
	}

	void NotBlessings()
	{
		CHECK(FromSpellName("") == proto::kBlessingNone);
		CHECK(FromSpellName("Healing") == proto::kBlessingNone);
		CHECK(FromSpellName("Amulet of Talos") == proto::kBlessingNone);
		CHECK(FromSpellName("Talos") == proto::kBlessingNone);
		CHECK(FromSpellName("Blessed") == proto::kBlessingNone);
		CHECK(FromSpellName("Well Rested") == proto::kBlessingNone);
	}
}

int main()
{
	TheNine();
	TheDlcsAndOthers();
	NotBlessings();
	if (failures == 0) {
		std::printf("BlessingKindsTest: all passed\n");
	}
	return failures == 0 ? 0 : 1;
}
