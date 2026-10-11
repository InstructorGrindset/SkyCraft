#pragma once

#include "Link.h"

// Monsters in the deep. Minecraft picks a dark spot in a tunnel the player dug (deep enough, out of
// sight) and sends proto::kEvSpawnCreature; this places a real Skyrim creature there (a skeever,
// skeleton or draugr, by the player's level), so Skyrim's own combat, kill XP and drops apply. It
// keeps track of what it placed: at most the cap Minecraft sends are alive at once, and they are
// taken away (disabled and deleted) once the player is far from them or has left for another
// worldspace or interior.
namespace skycraft::Spawn
{
	// Main thread: Minecraft asks for a creature (only while Minecraft drives the player).
	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event);
	// Main thread, every frame: takes away creatures left behind.
	void Update(RE::PlayerCharacter* a_player, float a_delta);
	// One of the creatures placed here (for the actor table's kActorSpawned). Main thread.
	bool IsOurs(const RE::Actor* a_actor);
	// A game was loaded or started: whatever was tracked belongs to the old one.
	void Reset();
}
