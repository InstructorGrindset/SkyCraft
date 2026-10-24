// Tests for MagicKinds.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++23 -I skse/src -I protocol skse/tests/MagicKindsTest.cpp -o magic_kinds_test && ./magic_kinds_test
#include "MagicKinds.h"

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
	using namespace skycraft::MagicKinds;

	constexpr int kDestruction = 20;
	constexpr int kRestoration = 22;
	constexpr int kConjuration = 19;
	constexpr int kAlteration = 18;
	constexpr int kAvDamageResist = 39;
	constexpr int kAvStamina = 26;
	constexpr int kAvMagicka = 25;
	constexpr int kAvNotHealth = 100;  // stands for what Muffle changes: not health
	constexpr int kArchCloak = 35;
	constexpr int kArchParalysis = 21;
	constexpr int kArchDisarm = 9;
	constexpr int kArchScript = 1;
	constexpr int kArchAccumulateMagnitude = 32;

	Effect Damage(int a_resist) { return { kArchValueModifier, kAvHealth, a_resist, kDestruction, true }; }

	void Destruction()
	{
		CHECK(SpellKind(Damage(kAvResistFire)) == proto::kSpellKindFire);    // Flames, Firebolt, Fire Rune, Incinerate
		CHECK(SpellKind(Damage(kAvResistFrost)) == proto::kSpellKindFrost);  // Frostbite, Ice Spike
		CHECK(SpellKind(Damage(kAvResistShock)) == proto::kSpellKindShock);  // Sparks, Lightning Bolt
		// The element counts whatever the archetype, as long as it's hostile (a Wall of Flames' hazard).
		CHECK(SpellKind({ 40, -1, kAvResistFire, kDestruction, true }) == proto::kSpellKindFire);
		// A hostile health damage nothing resists is no element (a poison, Vampiric Drain).
		CHECK(SpellKind(Damage(-1)) == proto::kSpellKindOther);
		// A cloak (Flame Cloak's first effect) on oneself isn't hostile.
		CHECK(SpellKind({ kArchCloak, -1, kAvResistFire, kDestruction, false }) == proto::kSpellKindOther);
	}

	void Restoration()
	{
		CHECK(SpellKind({ kArchValueModifier, kAvHealth, -1, kRestoration, false }) == proto::kSpellKindHeal);      // Healing
		CHECK(SpellKind({ kArchPeakValueModifier, kAvHealth, -1, kRestoration, false }) == proto::kSpellKindHeal);  // a peak heal
		CHECK(SpellKind({ kArchAccumulateMagnitude, kAvWardPower, -1, kRestoration, false }) == proto::kSpellKindWard);  // Lesser Ward
		CHECK(SpellKind({ kArchValueModifier, kAvMagicka, -1, kRestoration, false }) == proto::kSpellKindOther);    // not health
	}

	void TheOtherSchools()
	{
		CHECK(SpellKind({ kArchSummonCreature, -1, -1, kConjuration, false }) == proto::kSpellKindSummon);  // Conjure Familiar
		CHECK(SpellKind({ kArchReanimate, -1, -1, kConjuration, true }) == proto::kSpellKindSummon);        // Raise Zombie
		CHECK(SpellKind({ kArchTelekinesis, -1, -1, kAlteration, false }) == proto::kSpellKindTelekinesis);
		CHECK(SpellKind({ kArchCalm, -1, -1, 21, true }) == proto::kSpellKindIllusion);           // Calm
		CHECK(SpellKind({ kArchDemoralize, -1, -1, 21, true }) == proto::kSpellKindIllusion);     // Fear
		CHECK(SpellKind({ kArchInvisibility, -1, -1, 21, false }) == proto::kSpellKindIllusion);  // Invisibility
		CHECK(SpellKind({ kArchValueModifier, kAvNotHealth, -1, kAvIllusion, false }) == proto::kSpellKindIllusion);  // Muffle
		CHECK(SpellKind({ kArchValueModifier, kAvDamageResist, -1, kAlteration, false }) == proto::kSpellKindOther);     // Oakflesh
		CHECK(SpellKind({ kArchParalysis, -1, -1, kAlteration, true }) == proto::kSpellKindOther);                        // Paralyze
		CHECK(SpellKind({}) == proto::kSpellKindOther);
	}

	void Shouts()
	{
		const Effect ethereal[] = { { kArchEtherealize, -1, -1, -1, false } };
		CHECK(ShoutKind("Become Ethereal", ethereal, 1) == proto::kShoutKindEthereal);
		CHECK(ShoutKind("Ätherische Gestalt", ethereal, 1) == proto::kShoutKindEthereal);  // any language: by its effect
		const Effect slow[] = { { kArchSlowTime, -1, -1, -1, false } };
		CHECK(ShoutKind("Slow Time", slow, 1) == proto::kShoutKindSlowTime);
		const Effect fire[] = { Damage(kAvResistFire) };
		CHECK(ShoutKind("Fire Breath", fire, 1) == proto::kShoutKindFireBreath);
		const Effect frost[] = { { kArchValueModifier, kAvStamina, -1, -1, true }, Damage(kAvResistFrost) };
		CHECK(ShoutKind("Frost Breath", frost, 2) == proto::kShoutKindFrostBreath);  // any effect, not just the first
		const Effect script[] = { { kArchScript, -1, -1, -1, false } };
		CHECK(ShoutKind("Unrelenting Force", script, 1) == proto::kShoutKindForce);
		CHECK(ShoutKind("WHIRLWIND SPRINT", script, 1) == proto::kShoutKindSprint);
		CHECK(ShoutKind("Clear Skies", script, 1) == proto::kShoutKindClearSkies);
		const Effect push[] = { { kArchStagger, -1, -1, -1, true } };
		CHECK(ShoutKind("Unerbittliche Macht", push, 1) == proto::kShoutKindForce);  // a push by its effect, whatever its name
		const Effect disarm[] = { { kArchDisarm, -1, -1, -1, true } };
		CHECK(ShoutKind("Disarm", disarm, 1) == proto::kShoutKindOther);
		CHECK(ShoutKind("Dragonrend", nullptr, 0) == proto::kShoutKindOther);
		CHECK(ShoutKind("", nullptr, 0) == proto::kShoutKindOther);
	}

	void Names()
	{
		CHECK(Contains("Unrelenting Force", "force"));
		CHECK(!Contains("Fus", "force"));
		CHECK(Contains("anything", ""));
		CHECK(!Contains("", "force"));
	}
}

int main()
{
	Destruction();
	Restoration();
	TheOtherSchools();
	Shouts();
	Names();
	if (failures == 0) {
		std::printf("MagicKindsTest: all passed\n");
	}
	return failures == 0 ? 0 : 1;
}
