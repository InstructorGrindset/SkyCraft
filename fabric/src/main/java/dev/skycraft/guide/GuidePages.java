package dev.skycraft.guide;

import java.util.List;

/**
 * The SkyCraft guide book's text (GuideBook gives it): a written book "SkyCraft", a short page for each thing that works
 * differently from both games. A page shows 14 lines of 114 pixels; GuidePagesTest checks they fit.
 */
public final class GuidePages {
	public static final String TITLE = "SkyCraft";
	public static final String AUTHOR = "SkyCraft";
	public static final List<String> PAGES = List.of(
		"SkyCraft\n\nYou play Skyrim as a Minecraft player. These pages tell the few things that work differently.\n\nThis book comes once: keep it.",
		"Sneaking\n\nCrouch (Shift) is Skyrim's sneak. Skyrim's people find it harder to notice you, and it trains your Sneak skill.",
		"Looting\n\nWhat you loot in Skyrim becomes Minecraft items: food, potions, ores, hides, and gear as its smithing metal. "
			+ "Coins, keys, books and quest items stay in Skyrim. The action bar says what became what.",
		"Doors and people\n\nG uses what Skyrim's crosshair is on: doors, people, chests, levers. Right click with nothing to use "
			+ "in your hand does the same.",
		"Shouts and spells\n\nV shouts your Skyrim shout: tap it for one word, hold it for more. B picks another shout. R casts "
			+ "your spell (hold it for Flames), N picks the next. Change the keys in Controls, under SkyCraft.",
		"Merchants\n\nRight click a Skyrim merchant with an empty hand to trade with them, as with a villager. Prices follow the town.",
		"Portals\n\nA Nether portal lit in Skyrim leads nowhere by itself. Use a filled Greater, Grand or Black soul gem on it, "
			+ "then on a second lit portal: the two are bound, and stepping in takes you between them.",
		"Settings\n\nPress O for Minecraft's menu. SkyCraft settings, in its top left corner, switches each feature on or off. "
			+ "Skyrim destruction, beside it, stops mining into Skyrim.",
		"Something wrong?\n\nType /skycraft report in chat. It packs the logs to send into one zip in Minecraft's folder and tells "
			+ "you where it is. Send it with your bug report."
	);

	private GuidePages() {
	}
}
