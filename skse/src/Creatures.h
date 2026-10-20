#pragma once

#include "Link.h"

// Minecraft's creatures and Skyrim's actors, Skyrim's side (Minecraft's: creatures.SkyCreatures). Log: SkyCraft-creatures.
//  - Mobs and NPCs fight (mobs_fight_npcs, wolves_fight_skyrim): Minecraft says where its monsters near the player are, and
//    the player's tamed wolves in a fight (proto::kEvMob kMobNear); a guard, a follower or an enemy of the player next to one,
//    or whoever it goes for, turns to it, plays an attack (its animation graph's attackStart) and the blow lands on Minecraft's
//    side a moment later (proto::kInMobHit). A Minecraft creature's hit on an actor (kMobHit) is real damage credited to no
//    one, with Skyrim's impact and, for a strong push, a stagger; the actor doesn't turn on the player for it.
//  - Creatures and light (creatures_and_light): skeevers and Falmer (CreatureKinds.h) are pushed out of strong Minecraft light
//    (torches, lanterns, glowstone: BlockLights) the way NpcBlocks pushes NPCs out of blocks; undead burning in daylight
//    (kMobBySun, set alight on Minecraft's side) burn here, flames and all.
namespace skycraft::Creatures
{
	// The actor table's creature bits for this actor, from its race (CreatureKinds.h): proto::kActorLightShy, kActorUndead.
	// Main thread.
	std::uint32_t Flags(RE::Actor* a_actor);
	// Main thread: Minecraft's proto::kEvMob (where its monsters are; a creature's or the sun's hit). A tamed wolf's hit is a
	// Minecraft hit by the player and goes through Combat.cpp's hit pipeline instead.
	void OnMobEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event);
	// Main thread, every frame Minecraft drives the player: actors swing at the Minecraft creatures next to them; light-shy
	// creatures are pushed out of strong light.
	void Update(RE::PlayerCharacter* a_player, float a_delta);
	// Forgets Minecraft's creatures and pending swings (Skyrim took the player back, a game was loaded). Main thread.
	void Reset();
}
