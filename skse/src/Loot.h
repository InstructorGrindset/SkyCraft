#pragma once

// Looting bridge, step 1 (detect only): logs every item that lands in the player's Skyrim
// inventory (looted corpses, chests, items picked up with activate, crafted, bought) with its
// type, keywords and value, tagged SkyCraft-loot. Nothing is changed in game. The log tells us
// which items players actually loot before conversion rules are written.
namespace skycraft::Loot
{
	void Install();
}
