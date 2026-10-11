#pragma once

#include "Link.h"

// Looting bridge. Every item that lands in the player's Skyrim inventory (looted corpses, chests,
// items picked up with activate, crafted, bought) is logged, tagged SkyCraft-loot, and sent to
// Minecraft as a proto::ItemEvent. Minecraft decides from its rules file what converts; for those
// it gives the Minecraft items and sends kEvTakeItem back, and Take() removes the original here.
namespace skycraft::Loot
{
	void Install();
	// Main thread: remove an item Minecraft converted from the player's Skyrim inventory.
	void Take(RE::PlayerCharacter* a_player, RE::FormID a_item, std::int32_t a_count);
	// What a merchant trades in (proto::VendorBuys), 0 if the actor isn't one.
	std::uint16_t VendorMask(RE::Actor* a_actor);
	// The town/city/settlement the player is in, else their location's name, else "".
	void TownName(RE::PlayerCharacter* a_player, char (&a_out)[32]);
}
