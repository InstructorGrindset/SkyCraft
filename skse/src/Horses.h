#pragma once

#include "Link.h"

// Horses carry over (horses_carry_over; Minecraft's side: creatures.SkyHorses). A tamed Minecraft horse and the player's
// Skyrim horse are one animal in two forms. When the player's tamed Minecraft horse comes into the mirror (proto::kEvHorse
// kHorseArrive), the Skyrim horse it was before, or else a new horse the player owns, is put there, enabled, with the Minecraft
// horse's speed (SpeedMult) and health; kHorseArrived tells Minecraft to let its own go. When the player leaves for another
// dimension with their horse near, Minecraft makes it a Minecraft horse there (kHorseDepart) and Skyrim's is disabled until it
// comes back. The player's horse (SkyState::horse) is the one carried over, else the owned horse they last rode. Kept in the
// SKSE co-save (record 'HRSE'). Log: SkyCraft-horse.
namespace skycraft::Horses
{
	// SkyState::horse and horseInfo: the player's horse and proto::HorseInfo (near, away, arrived, carried, SpeedMult, health).
	// 0 and 0 with Minecraft's switch off. Main thread.
	void Describe(RE::PlayerCharacter* a_player, std::uint32_t& a_horse, std::uint32_t& a_info);
	// Main thread: Minecraft's proto::kEvHorse (also while the player is parked in another dimension).
	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event);
	// Main thread, every frame.
	void Update(RE::PlayerCharacter* a_player, float a_delta);
	// A game was loaded or started: its carried horse (from its co-save; none for a new game) from here on. Main thread.
	void Reset();

	// Co-save (CoSave.cpp): record type, writing it, reading one (its body), forgetting what a load read.
	inline constexpr std::uint32_t kSaveRecord = 0x48525345;  // 'HRSE'
	void Save(SKSE::SerializationInterface* a_intfc);
	void Load(SKSE::SerializationInterface* a_intfc, std::uint32_t a_version);
	void Revert();
}
