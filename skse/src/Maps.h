#pragma once

#include "Link.h"

// Skyrim maps (skyrim_maps, proto::kFeatSkyrimMaps): what Skyrim knows that a Minecraft map in the mirror draws, beside the
// collision Minecraft already holds, in proto::MapTable:
//  - the map markers near the player (within proto::kMapMarkerReach blocks, the nearest proto::kMaxMapMarkers) that Skyrim's
//    own map shows, seen or discovered: where, what kind of place, its name. Minecraft puts them on its maps as banners;
//  - the land under a Minecraft map, sampled when Minecraft asks (proto::kEvMapSample) from the cells Skyrim has loaded: the
//    land's height (TES::GetLandHeight), what it's made of (its texture, as digging reads it) and the cell's water there.
// Log: SkyCraft-maps. Main thread.
namespace skycraft::Maps
{
	// Every frame (Minecraft driving the player or not): about every two seconds, the markers near the player.
	void Update(RE::PlayerCharacter* a_player, float a_delta);
	// proto::kEvMapSample: samples the land Minecraft asked for and answers in the table.
	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event);
	// A game was loaded or started: the markers are gathered again.
	void Reset();
}
