#pragma once

#include "Link.h"

// Economy, Skyrim's side. Skyrim food fills hunger: the player eating a food in Skyrim (TESEquipEvent for a food
// AlchemyItem: from the inventory while Skyrim has the player, riding for one, or player.equipitem) sends
// proto::kInSkyrimFood with what FoodValues.h makes of it, while Minecraft has the feature on (kFeatSkyrimFood).
// Selling to merchants: Minecraft asks how much Skyrim gold a merchant can pay (kEvMerchantGold, answered with
// kInMerchantGold) and, once it took the goods, has the gold paid (kEvSellToMerchant): moved out of the merchant's chest,
// the one Skyrim's barter menu trades from, into the player's inventory. Log: SkyCraft-economy.
namespace skycraft::Economy
{
	// Watches the player eat. Once, when the game's data has loaded.
	void Install();
	// Main thread: Minecraft's kEvMerchantGold and kEvSellToMerchant.
	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event);
}
