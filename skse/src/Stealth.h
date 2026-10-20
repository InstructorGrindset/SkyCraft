#pragma once

// Light and stealth, and sneak attacks: the rules on their own (pure, no game), unit tested in tests/StealthTest.cpp. The
// game side is Stealth.cpp (walls_hide_you, light_stealth) and Combat.cpp's ApplyHit (sneak_attacks, power_attacks).

#include <algorithm>
#include <cstdint>

namespace skycraft::Stealth
{
	// ---- light and stealth --------------------------------------------------------------------------------------
	// Skyrim's detection asks how lit the player is: the actor's light level (HighProcessData::lightLevel, what the
	// GetLightLevel condition reads), worked out from Skyrim's own sun, sky and lamps. Minecraft's blocks and lights are
	// nothing to it, so a tunnel dug into the ground is as bright as the day above and a torch changes nothing. Minecraft
	// sends its light at the player's eyes (McState eyeBlockLight, eyeSkyLight) and these fold it in.

	// How bright a Minecraft light level (0-15) looks: Minecraft's own curve (LightTexture, with the overworld's ambient
	// light of 0): 1 at 15, about a half at 12, a fifth at 8, nothing at 0.
	constexpr float Brightness(int a_level)
	{
		const float f = static_cast<float>(std::clamp(a_level, 0, 15)) / 15.0f;
		return f / (4.0f - 3.0f * f);
	}

	// Minecraft's brightest light (15) in Skyrim's light level terms: about an exterior in daylight.
	inline constexpr float kFullLight = 100.0f;

	// Minecraft block light (torches, lava, glowstone, ...) as a Skyrim light level: a torch's own block (14) about 78,
	// two blocks off (12) 50, four off (10) 33, eight off (6) 14.
	constexpr float FromBlockLight(int a_block) { return kFullLight * Brightness(a_block); }

	// How much of Skyrim's own light reaches the player through Minecraft's blocks, from Minecraft's sky light there:
	// all of it under open sky (15), and in a Skyrim interior or under Skyrim's own roofs (Minecraft has no blocks there,
	// so its sky light is 15 and Skyrim's light stands as it is); about four fifths a block in under an overhang (14),
	// a half under a deeper roof (12); none sealed in (0), as in a dug tunnel.
	constexpr float SkyShare(int a_sky) { return Brightness(a_sky); }

	// The light level Skyrim's detection uses for the player: Skyrim's own light, shaded by Minecraft's blocks as far as
	// they shut out Minecraft's sky, or Minecraft's block light, whichever is brighter.
	constexpr float PlayerLight(float a_skyrimLight, int a_block, int a_sky)
	{
		return std::max(std::max(a_skyrimLight, 0.0f) * SkyShare(a_sky), FromBlockLight(a_block));
	}

	// ---- walls hide you -----------------------------------------------------------------------------------------
	// An NPC's line of sight to the player is walked block by block through Minecraft's solid blocks (BlockWalk.h
	// SolidBetween), through at most this many blocks (a line about 340 to 1000 blocks long). Longer: Skyrim's own answer
	// stands.
	inline constexpr long long kMaxSightCells = 1024;

	// Where on the player an NPC's line of sight ends: the head (Minecraft's eye), and the chest, this share of the eye
	// height up from the feet (about 1.2 blocks standing, 0.95 crouching).
	inline constexpr float kChestShare = 0.75f;

	// ---- sneak attacks ------------------------------------------------------------------------------------------
	// Skyrim's sneak attack multiplier before perks, by the vanilla weapon the hit lands as: one-handed melee weapons
	// (swords, daggers, war axes, maces) three times, bows and everything else twice. Perks (Backstab, Assassin's Blade,
	// Deadly Aim) then multiply it through Skyrim's Mod Sneak Attack Mult perk entry point.
	constexpr float SneakAttackBase(bool a_ranged, bool a_oneHandedMelee) { return !a_ranged && a_oneHandedMelee ? 3.0f : 2.0f; }

	// A multiplier the perks gave that makes no sense (nothing, or past Assassin's Blade's fifteen twice over) is cut back.
	constexpr float ClampSneakMult(float a_mult) { return std::clamp(a_mult, 1.0f, 30.0f); }

	// Sneak skill uses a sneak attack is worth, as Skyrim counts them (the Sneak skill's own use multiplier makes them
	// experience): a melee sneak attack 30, a ranged one 2.5.
	constexpr float SneakAttackSkillUses(bool a_ranged) { return a_ranged ? 2.5f : 30.0f; }
}
