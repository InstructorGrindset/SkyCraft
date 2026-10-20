#pragma once

// Which Skyrim creatures keep away from light and which are undead, by race (creatures_and_light), and who among Skyrim's
// people and creatures swings at a Minecraft creature next to them (mobs_fight_npcs, wolves_fight_skyrim). Pure, no game:
// a race's editor ID is all it reads, like AnimalKinds.h, so it's unit tested on its own (tests/CreatureKindsTest.cpp). The
// actor table carries the race's bits (proto::kActorLightShy, kActorUndead) for Minecraft to read.

#include "AnimalKinds.h"
#include "skycraft_protocol.h"

#include <cstdint>
#include <string_view>

namespace skycraft::CreatureKinds
{
	// The light-shy and undead bits of a race, from its editor ID. Skyrim.esm and the DLCs name their races plainly
	// (SkeeverRace, SkeeverWhiteRace, FalmerRace, DraugrRace, DraugrMagicRace, SkeletonRace, SkeletonNecroRace, NordRaceVampire,
	// DLC1VampireBeastRace, DLC1FalmerFrozenVampRace, ...) and mods mostly follow suit:
	//  - "skeever" and "falmer" keep away from light (Falmer live blind in the dark below; skeevers are vermin of the dark);
	//  - "draugr", "skeleton" and "vamp" (vampires) are undead and burn in daylight (Dawnguard's frozen Falmer vampires,
	//    DLC1FalmerFrozenVampRace, are both).
	// Ghosts, dragon priests and summoned undead are left out: they don't walk about in the sun.
	constexpr std::uint32_t FromRace(std::string_view a_editorId)
	{
		using AnimalKinds::Contains;
		std::uint32_t bits = 0;
		if (Contains(a_editorId, "skeever") || Contains(a_editorId, "falmer")) {
			bits |= proto::kActorLightShy;
		}
		if (Contains(a_editorId, "draugr") || Contains(a_editorId, "skeleton") || Contains(a_editorId, "vamp")) {
			bits |= proto::kActorUndead;
		}
		return bits;
	}

	// What an actor next to a Minecraft creature is, for FightsBack.
	struct Fighter
	{
		bool guard{ false };            // in a guard faction (Actor::IsGuard)
		bool hostileToPlayer{ false };  // the player's enemy
		bool teammate{ false };         // the player's follower
		bool goneFor{ false };          // the creature is going for this actor
		bool undead{ false };           // kActorUndead
		bool child{ false };            // a child's race: never fights
	};

	// Whether this actor swings at a Minecraft creature next to it (a_traits: proto::MobTraits). The player's wolf is only
	// fought by the player's enemies. A monster is fought by whoever it goes for (self defence), and by guards, the player's
	// followers and the player's enemies (bandits, draugr: everyone's enemy), except that Skyrim's undead leave Minecraft's be
	// unless it comes for them, as Minecraft's undead leave Skyrim's be.
	constexpr bool FightsBack(const Fighter& a_actor, std::uint32_t a_traits)
	{
		if (a_actor.child) {
			return false;
		}
		if (a_traits & proto::kMobPlayersWolf) {
			return a_actor.hostileToPlayer;
		}
		if (a_actor.goneFor) {
			return true;
		}
		if (a_actor.undead && (a_traits & proto::kMobUndead)) {
			return false;
		}
		return a_actor.guard || a_actor.hostileToPlayer || a_actor.teammate;
	}

	// Minecraft's light falls off one level a block (in steps along the axes): what a block of a_level light gives a spot
	// a_dx, a_dy, a_dz blocks away (whole blocks between their cells), 0 at the least.
	constexpr int LightFrom(int a_level, int a_dx, int a_dy, int a_dz)
	{
		const int steps = (a_dx < 0 ? -a_dx : a_dx) + (a_dy < 0 ? -a_dy : a_dy) + (a_dz < 0 ? -a_dz : a_dz);
		return a_level > steps ? a_level - steps : 0;
	}

	// Light this strong (a torch's 14 within 6 blocks, a lantern's or glowstone's 15 within 7, a soul torch's 10 within 2) keeps
	// a light-shy creature away.
	inline constexpr int kShyLight = 8;

	// How hard a light-shy creature is pushed out of light this strong: blocks a second, more the brighter it is.
	constexpr float ShyPushSpeed(int a_light)
	{
		if (a_light < kShyLight) {
			return 0.0f;
		}
		const float over = static_cast<float>(a_light - kShyLight + 1);
		const float speed = 1.5f * over;
		return speed < 6.0f ? speed : 6.0f;
	}
}
