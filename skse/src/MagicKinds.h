#pragma once

// What a spell or a shout does, as Minecraft mirrors it (Shouts and Spells: proto::SpellKind, proto::ShoutKind). Pure, no
// game: the facts Magic.cpp reads off a spell's effects (each one's archetype, primary and resist actor values, school and
// whether it's hostile) and a shout's name are all it looks at, so it's unit tested on its own (tests/MagicKindsTest.cpp).
// The numbers below are CommonLib's RE::EffectArchetypes::ArchetypeID and RE::ActorValue values; Magic.cpp checks every one
// against CommonLib's own enums.
//
// A spell is its first effect: a ward first (WardPower), then a hostile one resisted by fire, frost or shock resistance
// (Flames, Frostbite, Sparks, the bolts, runes and storms), one restoring health (Healing, Fast Healing, Heal Other), a
// summon or a raise, telekinesis, then illusion (its school, or calm, fear, fury, courage, invisibility). A shout is what
// any of its effects is: Become Ethereal (Etherealize), Slow Time; a hostile one resisted by fire or frost is a breath; then
// by its name (Unrelenting Force, Whirlwind Sprint, Clear Skies; Skyrim's English names), and a first effect that staggers
// is a push like Unrelenting Force's whatever its name.

#include "skycraft_protocol.h"

#include <cstddef>
#include <string_view>

namespace skycraft::MagicKinds
{
	// RE::EffectArchetypes::ArchetypeID
	inline constexpr int kArchValueModifier = 0;
	inline constexpr int kArchDualValueModifier = 5;
	inline constexpr int kArchCalm = 6;
	inline constexpr int kArchDemoralize = 7;
	inline constexpr int kArchFrenzy = 8;
	inline constexpr int kArchInvisibility = 11;
	inline constexpr int kArchSummonCreature = 18;
	inline constexpr int kArchTelekinesis = 20;
	inline constexpr int kArchReanimate = 22;
	inline constexpr int kArchValueAndParts = 31;
	inline constexpr int kArchStagger = 33;
	inline constexpr int kArchPeakValueModifier = 34;
	inline constexpr int kArchSlowTime = 37;
	inline constexpr int kArchRally = 38;
	inline constexpr int kArchEtherealize = 41;

	// RE::ActorValue
	inline constexpr int kAvIllusion = 21;
	inline constexpr int kAvHealth = 24;
	inline constexpr int kAvResistFire = 41;
	inline constexpr int kAvResistShock = 42;
	inline constexpr int kAvResistFrost = 43;
	inline constexpr int kAvWardPower = 63;

	// One effect of a spell, as Magic.cpp reads it from its EffectSetting.
	struct Effect
	{
		int  archetype{ -1 };   // EffectSetting::data.archetype
		int  primaryAV{ -1 };   // data.primaryAV: the actor value it changes
		int  resist{ -1 };      // data.resistVariable: what resists it
		int  school{ -1 };      // data.associatedSkill: its school
		bool hostile{ false };  // hostile or detrimental
	};

	constexpr char Lower(char a_c) { return a_c >= 'A' && a_c <= 'Z' ? static_cast<char>(a_c - 'A' + 'a') : a_c; }

	// a_word (lower case) anywhere in a_text, ignoring case.
	constexpr bool Contains(std::string_view a_text, std::string_view a_word)
	{
		if (a_word.empty() || a_word.size() > a_text.size()) {
			return a_word.empty();
		}
		for (std::size_t at = 0; at + a_word.size() <= a_text.size(); ++at) {
			std::size_t i = 0;
			while (i < a_word.size() && Lower(a_text[at + i]) == a_word[i]) {
				++i;
			}
			if (i == a_word.size()) {
				return true;
			}
		}
		return false;
	}

	// The archetypes that move an actor value up or down (damage, restore, fortify).
	constexpr bool ChangesValue(int a_archetype)
	{
		return a_archetype == kArchValueModifier || a_archetype == kArchDualValueModifier || a_archetype == kArchPeakValueModifier ||
		       a_archetype == kArchValueAndParts;
	}

	// The element a hostile effect is resisted as: kSpellKindFire, Frost or Shock, else kSpellKindOther.
	constexpr std::uint8_t ElementOf(const Effect& a_effect)
	{
		if (!a_effect.hostile) {
			return proto::kSpellKindOther;
		}
		switch (a_effect.resist) {
		case kAvResistFire:
			return proto::kSpellKindFire;
		case kAvResistFrost:
			return proto::kSpellKindFrost;
		case kAvResistShock:
			return proto::kSpellKindShock;
		default:
			return proto::kSpellKindOther;
		}
	}

	// The proto::SpellKind of a spell whose first effect is a_first.
	constexpr std::uint8_t SpellKind(const Effect& a_first)
	{
		if (a_first.primaryAV == kAvWardPower) {
			return proto::kSpellKindWard;
		}
		if (const auto element = ElementOf(a_first); element != proto::kSpellKindOther) {
			return element;
		}
		if (ChangesValue(a_first.archetype) && a_first.primaryAV == kAvHealth && !a_first.hostile) {
			return proto::kSpellKindHeal;
		}
		if (a_first.archetype == kArchSummonCreature || a_first.archetype == kArchReanimate) {
			return proto::kSpellKindSummon;
		}
		if (a_first.archetype == kArchTelekinesis) {
			return proto::kSpellKindTelekinesis;
		}
		switch (a_first.archetype) {
		case kArchCalm:
		case kArchDemoralize:
		case kArchFrenzy:
		case kArchInvisibility:
		case kArchRally:
			return proto::kSpellKindIllusion;
		default:
			break;
		}
		return a_first.school == kAvIllusion ? proto::kSpellKindIllusion : proto::kSpellKindOther;
	}

	// The proto::ShoutKind of a shout called a_name whose (first word's) spell has a_count effects a_effects.
	constexpr std::uint8_t ShoutKind(std::string_view a_name, const Effect* a_effects, std::size_t a_count)
	{
		for (std::size_t i = 0; i < a_count; ++i) {
			if (a_effects[i].archetype == kArchEtherealize) {
				return proto::kShoutKindEthereal;
			}
			if (a_effects[i].archetype == kArchSlowTime) {
				return proto::kShoutKindSlowTime;
			}
		}
		for (std::size_t i = 0; i < a_count; ++i) {
			switch (ElementOf(a_effects[i])) {
			case proto::kSpellKindFire:
				return proto::kShoutKindFireBreath;
			case proto::kSpellKindFrost:
				return proto::kShoutKindFrostBreath;
			default:
				break;
			}
		}
		if (Contains(a_name, "unrelenting force")) {
			return proto::kShoutKindForce;
		}
		if (Contains(a_name, "whirlwind sprint")) {
			return proto::kShoutKindSprint;
		}
		if (Contains(a_name, "clear skies")) {
			return proto::kShoutKindClearSkies;
		}
		if (a_count > 0 && a_effects[0].archetype == kArchStagger && a_effects[0].hostile) {
			return proto::kShoutKindForce;
		}
		return proto::kShoutKindOther;
	}
}
