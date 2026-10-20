#pragma once

#include "Link.h"

// Skyrim's towns and building with Minecraft blocks (Towns.cpp, Passages.cpp, Redstone.cpp). Minecraft decides when
// something happened in its world; Skyrim decides who saw it and answers with its own systems:
//  - town_crime: griefing a town (proto::kEvTownCrime) is a theft in Skyrim if someone sees it (Skyrim's detection),
//    raised with Skyrim's own steal alarm for the witness's crime faction: bounty, witnesses and guards as for a theft;
//  - guard_comments: a guard who sees the player build or dig in town (proto::kEvTownBuild) says something now and then,
//    as a corner notification with the guard's name (no CommonLib function makes an NPC speak a line of our own);
//  - hearthfire_blocks: the Hearthfire house plot the player stands on goes to Minecraft (SkyState::hearthPlot), which
//    counts the blocks built there (proto::kEvHearthfireStage, McState::hearthBlocks);
//  - npc_doors_ladders (Passages.cpp): NPCs open Minecraft doors and are helped up its ladders;
//  - dwemer_redstone (Redstone.cpp): levers, buttons and pressure plates pulse Minecraft's redstone, and redstone works
//    Dwemer doors.
// Log: SkyCraft-town. Main thread unless said otherwise.
namespace skycraft::Towns
{
	// At kDataLoaded: the activate event sink (Dwemer redstone).
	void Install();
	// A game was loaded or started.
	void Reset();
	// Minecraft's kEvTownCrime, kEvTownBuild, kEvHearthfireStage and kEvRedstoneDoor.
	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event);
	// Every frame, after NPCs were pushed out of blocks: switches and Dwemer doors to Minecraft; while Minecraft drives the
	// player (a_puppeting), NPCs on ladders.
	void Update(RE::PlayerCharacter* a_player, bool a_puppeting, float a_delta);
	// SkyState's town fields (townFlags, hearthPlot).
	void FillSkyState(RE::PlayerCharacter* a_player, proto::SkyState& a_sky);
	// The actor table's towns bits for this actor (proto::kActorGuard).
	std::uint32_t ActorFlags(RE::Actor* a_actor);
}

// Minecraft's doors and ladders as Skyrim's NPCs meet them (npc_doors_ladders; proto::kRenPassages).
namespace skycraft::Passages
{
	// Render messages (main thread): a section's doors and ladders; a world change.
	void OnPassages(const std::uint8_t* a_data, std::uint32_t a_bytes);
	void Clear();
	// A closed Minecraft door NPCs may open (not iron) is in this block. Any thread.
	bool ClosedDoorAt(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z);
	// Any door NPCs may open (open or closed) is in this block: paths go through it. Any thread.
	bool NpcDoorAt(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z);
	// An NPC walked into the closed door in this block: Minecraft is asked to open it (at most once a second a door).
	void RequestOpen(std::int32_t a_x, std::int32_t a_y, std::int32_t a_z);
	// Main thread, every frame Minecraft drives the player: NPCs standing in a ladder column whose path goes up are moved
	// up it and onto the block it leans on.
	void UpdateClimbers(RE::PlayerCharacter* a_player, float a_delta);
}

// Skyrim's switches and Dwemer doors and Minecraft's redstone (dwemer_redstone).
namespace skycraft::Redstone
{
	void Install();
	// Every frame: switches used since the last frame pulse Minecraft's redstone; every 2 s the Dwemer doors near the
	// player go to Minecraft.
	void Update(RE::PlayerCharacter* a_player, float a_delta);
	// proto::kEvRedstoneDoor: power came to a Dwemer door's block (open it) or went (close it).
	void OnDoorPower(RE::PlayerCharacter* a_player, const proto::McEvent& a_event);
	void Reset();
}
