#pragma once

#include "Link.h"

// Shared world events (Minecraft's shared_world_events switch, proto::kFeatSharedWorldEvents): things in this
// Skyrim that the other players in the Minecraft world should feel. Skyrim only reports and shakes: which actors
// are dragons (proto::kActorDragon in the actor table), who just shouted (SkyState::shoutSeq and its fields), and a
// small camera shake when Minecraft says something happened to another player (proto::kEvPartyShake). Minecraft
// decides what is worth telling and tells it. Log: SkyCraft-party.
namespace skycraft::Party
{
	// The actor table's party bits for this actor: proto::kActorDragon for a dragon. Main thread.
	std::uint32_t ActorFlags(RE::Actor* a_actor);
	// Main thread, for every actor written to the actor table this frame: notices the frame it starts a shout.
	void WatchActor(RE::Actor* a_actor);
	// Main thread, every frame before SkyState is written: notices the player's own shouts and fills in the shout fields.
	void Fill(RE::PlayerCharacter* a_player, proto::SkyState& a_sky);
	// Main thread: Minecraft's proto::kEvPartyShake (a = strength, b = seconds).
	void Shake(RE::PlayerCharacter* a_player, const proto::McEvent& a_event);
	// Main thread, where the camera is pinned to Minecraft's eye (Game.cpp): how far the shake moves it this frame
	// (Skyrim units; zero with no shake on).
	RE::NiPoint3 CameraOffset();
}
