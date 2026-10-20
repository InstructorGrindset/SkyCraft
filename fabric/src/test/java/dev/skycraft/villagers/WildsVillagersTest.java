package dev.skycraft.villagers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.gear.GearTiers;
import dev.skycraft.link.Proto;
import dev.skycraft.loot.ItemRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WildsVillagersTest {
	@Test
	void namesReadLikeSkyrims() {
		long seed = 1234567L;
		String name = WildsVillagers.displayName(seed, "minecraft:weaponsmith", false);
		WildsVillagers.Race race = WildsVillagers.race(seed);
		assertEquals(WildsVillagers.personalName(seed) + ", " + race.adjective() + " Blacksmith", name);
		assertTrue(WildsVillagers.displayName(seed, "cartographer", false).endsWith(" " + race.adjective() + " Cartographer"));
		assertTrue(WildsVillagers.displayName(seed, "minecraft:none", false).endsWith(" Laborer"));
		assertTrue(WildsVillagers.displayName(seed, "minecraft:armorer", true).endsWith(" Child"));
		assertTrue(WildsVillagers.displayName(seed, "somemod:beekeeper", false).endsWith(" Villager"));
		assertTrue(WildsVillagers.displayName(seed, "minecraft:cleric", false).contains(" Priest of "));
		// The same villager is always the same person.
		assertEquals(name, WildsVillagers.displayName(seed, "weaponsmith", false));
	}

	@Test
	void everyProfessionHasATitle() {
		for (String p : List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher", "leatherworker", "librarian", "mason",
			"nitwit", "none", "shepherd", "toolsmith", "weaponsmith")) {
			assertTrue(WildsVillagers.TITLES.containsKey(p), p);
		}
	}

	@Test
	void racesComeInSkyrimsProportions() {
		Map<String, Integer> counts = new HashMap<>();
		Random random = new Random(7);
		int n = 20000;
		for (int i = 0; i < n; i++) {
			counts.merge(WildsVillagers.race(random.nextLong()).adjective(), 1, Integer::sum);
		}
		int total = WildsVillagers.RACES.stream().mapToInt(WildsVillagers.Race::weight).sum();
		for (WildsVillagers.Race r : WildsVillagers.RACES) {
			double expected = (double) r.weight() / total;
			double got = counts.getOrDefault(r.adjective(), 0) / (double) n;
			assertEquals(expected, got, 0.015, r.adjective());
		}
		// Imperials and Bretons have family names, Nords go by one.
		for (int i = 0; i < 200; i++) {
			long seed = random.nextLong();
			WildsVillagers.Race r = WildsVillagers.race(seed);
			assertEquals(!r.lastNames().isEmpty(), WildsVillagers.personalName(seed).contains(" "), r.adjective());
		}
	}

	@Test
	void ownNamesAreKnownAndPlayersNamesAreNot() {
		Random random = new Random(11);
		for (int i = 0; i < 500; i++) {
			long seed = random.nextLong();
			for (String p : List.of("armorer", "cleric", "none", "nitwit", "modded:thing")) {
				assertTrue(WildsVillagers.isOurs(WildsVillagers.displayName(seed, p, false), seed), p);
			}
			assertTrue(WildsVillagers.isOurs(WildsVillagers.displayName(seed, "none", true), seed));
			assertFalse(WildsVillagers.isOurs("Bob", seed));
			assertFalse(WildsVillagers.isOurs(WildsVillagers.personalName(seed), seed));
			assertFalse(WildsVillagers.isOurs(WildsVillagers.displayName(seed, "armorer", false) + " the Brave", seed));
		}
	}

	@Test
	void tradesComeWithLevels() {
		assertEquals(1, WildsVillagers.tradesFor("minecraft:armorer", 1).size());
		assertEquals(1, WildsVillagers.tradesFor("armorer", 2).size());
		assertEquals(2, WildsVillagers.tradesFor("armorer", 3).size());
		assertEquals(3, WildsVillagers.tradesFor("armorer", 5).size());
		assertTrue(WildsVillagers.tradesFor("farmer", 5).isEmpty());
		assertTrue(WildsVillagers.tradesFor("none", 1).isEmpty());
		assertTrue(WildsVillagers.tradesFor("nitwit", 5).isEmpty());
	}

	@Test
	void tradesSellRealItemsAtSanePrices() {
		List<String> ids = GearTiers.allItemIds();
		ItemRules rules = ItemRules.parse(ItemRules.DEFAULT_TEXT, new ArrayList<>());
		for (WildsVillagers.Trade t : WildsVillagers.TRADES) {
			assertTrue(t.level() >= 1 && t.level() <= 5, t.item());
			assertTrue(t.emeralds() >= 1 && t.emeralds() <= 64, t.item());
			assertTrue(t.count() >= 1 && t.count() <= 64 && t.maxUses() >= 1, t.item());
			if (t.potion()) {
				ItemRules.Result r = rules.match(new ItemRules.Item(1 << ItemRules.TAGS.indexOf("potion"), 0, Proto.SRC_BOUGHT, t.skyrimName()));
				assertFalse(r.keep(), t.skyrimName() + " converts");
				assertEquals("minecraft:potion", r.item(), t.skyrimName());
			} else {
				assertTrue(t.item().startsWith("skycraft:") && ids.contains(t.item().substring("skycraft:".length())), t.item() + " is a SkyCraft item");
			}
		}
	}
}
