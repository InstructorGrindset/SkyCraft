#pragma once

#include "Link.h"

// The world features' Skyrim side: weather both ways, and what the crosshair is on for farming.
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
