#pragma once

#include "Link.h"

// The world features' Skyrim side: weather both ways, what the crosshair is on for farming, and shrine blessings.
// Main thread only, every frame from Game's per-frame update, just before SkyState goes out.
namespace skycraft::Weather
{
	// Fills SkyState's weather fields from Skyrim's sky (its weather's kind where the player is, whether rain or snow is
	// falling) and, when Minecraft asks for another weather (a new McState::weatherSeq, switch weather_sync), switches
	// Skyrim to the nearest weather its region has, then acknowledges the request in SkyState::weatherAck. a_mc is null
	// while Minecraft isn't connected.
	void Update(RE::PlayerCharacter* a_player, const proto::McState* a_mc, proto::SkyState& a_sky);
}

namespace skycraft::Farming
{
	// SkyState::aim (proto::AimFlags): kAimHarvest while the crosshair is on a plant Skyrim can harvest.
	std::uint8_t Aim();
}

namespace skycraft::Blessings
{
	// Once a second: a shrine's blessing new on the player (or cast again) is sent to Minecraft (proto::kInBlessing) with
	// the game hours it has left and Skyrim's timescale. a_minecraftThere: Minecraft is connected and in its world; a
	// blessing it missed is sent when it's back, with what's left of it.
	void Update(RE::PlayerCharacter* a_player, bool a_minecraftThere, float a_delta);
}
