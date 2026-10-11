package dev.skycraft.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemRulesTest {
	private static final int WORLD = 0, CORPSE = 1, CONTAINER = 2, ACTOR = 3, BOUGHT = 4;

	private static ItemRules defaults() {
		List<String> problems = new ArrayList<>();
		ItemRules rules = ItemRules.parse(ItemRules.DEFAULT_TEXT, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		return rules;
	}

	private static int tags(String... names) {
		int t = 0;
		for (String n : names) {
			t |= ItemRules.bit(n);
		}
		return t;
	}

	private static String convert(ItemRules rules, int tags, int material, int source, String name) {
		ItemRules.Result r = rules.match(new ItemRules.Item(tags, material, source, name));
		return r.keep() ? "keep" : r.item() + " " + r.perItem();
	}

	@Test
	void itemsFromTheDetectTestConvertAsPlanned() {
		ItemRules rules = defaults();
		// Seen in the build 3 log.
		assertEquals("minecraft:potato 1", convert(rules, tags("food"), 0, CONTAINER, "Potato"));
		assertEquals("minecraft:pumpkin_pie 1", convert(rules, tags("food"), 0, CORPSE, "Apple Pie"));
		assertEquals("keep", convert(rules, tags("food"), 0, CORPSE, "Wine"));
		assertEquals("minecraft:leather 1", convert(rules, tags("hide"), 0, CONTAINER, "Bear Pelt"));
		assertEquals("keep", convert(rules, tags("gold", "clutter"), 0, CONTAINER, "Gold"));
		assertEquals("keep", convert(rules, tags("key"), 0, CORPSE, "Key to Birna's Oddments"));
		assertEquals("keep", convert(rules, tags("ingredient", "foodraw"), 0, CONTAINER, "Salt Pile"));
		assertEquals("keep", convert(rules, tags("clothing"), 0, CORPSE, "Belted Tunic"));
		assertEquals("keep", convert(rules, tags("jewelry"), 0, CORPSE, "Silver Ring"));
		assertEquals("minecraft:iron_ingot 1", convert(rules, tags("weapon"), 2, CORPSE, "Iron Dagger"));
		assertEquals("keep", convert(rules, tags("weapon", "clutter"), 1, CONTAINER, "Wooden Sword"));
	}

	@Test
	void appleIsNotAnApplePieAndCookedSalmonWins() {
		ItemRules rules = defaults();
		assertEquals("minecraft:apple 1", convert(rules, tags("food"), 0, WORLD, "Red Apple"));
		assertEquals("minecraft:cooked_salmon 1", convert(rules, tags("food"), 0, WORLD, "Salmon Steak"));
		assertEquals("minecraft:salmon 1", convert(rules, tags("food", "foodraw"), 0, WORLD, "Salmon Meat"));
		assertEquals("minecraft:beef 1", convert(rules, tags("food", "foodraw"), 0, WORLD, "Horker Meat"));
	}

	@Test
	void gearOnlySalvagesWhenLooted() {
		ItemRules rules = defaults();
		assertEquals("keep", convert(rules, tags("weapon"), 3, WORLD, "Steel Sword"));
		assertEquals("skycraft:steel_ingot 1", convert(rules, tags("weapon"), 3, CONTAINER, "Steel Sword"));
		assertEquals("skycraft:steel_ingot 2", convert(rules, tags("armor"), 3, CORPSE, "Steel Armor"));
		assertEquals("keep", convert(rules, tags("weapon"), 9, CORPSE, "Ebony Sword"));
	}

	@Test
	void boughtGearBecomesTheMatchingGear() {
		ItemRules rules = defaults();
		int steel = ItemRules.MATERIALS.indexOf("steel"), elven = ItemRules.MATERIALS.indexOf("elven"), iron = ItemRules.MATERIALS.indexOf("iron");
		int daedric = ItemRules.MATERIALS.indexOf("daedric"), hide = ItemRules.MATERIALS.indexOf("hide");
		// The user's report: a Hunting Bow bought from Elrindir became a steel ingot.
		assertEquals("minecraft:bow 1", convert(rules, tags("weapon"), steel, BOUGHT, "Hunting Bow"));
		assertEquals("minecraft:bow 1", convert(rules, tags("weapon"), steel, CORPSE, "Hunting Bow"));
		assertEquals("skycraft:steel_sword 1", convert(rules, tags("weapon"), steel, BOUGHT, "Steel Sword"));
		assertEquals("skycraft:steel_war_axe 1", convert(rules, tags("weapon"), steel, BOUGHT, "Steel War Axe"));
		assertEquals("skycraft:daedric_sword 1", convert(rules, tags("weapon"), daedric, BOUGHT, "Daedric Greatsword"));
		assertEquals("skycraft:elven_helmet 1", convert(rules, tags("armor"), elven, BOUGHT, "Elven Helmet"));
		assertEquals("skycraft:iron_greaves 1", convert(rules, tags("armor"), iron, BOUGHT, "Iron Gauntlets"));
		assertEquals("skycraft:steel_boots 1", convert(rules, tags("armor"), steel, BOUGHT, "Steel Shin Boots"));
		assertEquals("minecraft:leather_chestplate 1", convert(rules, tags("armor"), hide, BOUGHT, "Leather Armor"));
		assertEquals("minecraft:shield 1", convert(rules, tags("armor"), iron, BOUGHT, "Iron Shield"));
		// Bought gear never salvages; looted gear still does.
		assertEquals("keep", convert(rules, tags("weapon"), ItemRules.MATERIALS.indexOf("silver"), BOUGHT, "Silver Sword"));
		assertEquals("skycraft:steel_ingot 1", convert(rules, tags("weapon"), steel, CONTAINER, "Steel Sword"));
		// Gold paid by a merchant stays in Skyrim; bought food still converts.
		assertEquals("keep", convert(rules, tags("gold", "clutter"), 0, BOUGHT, "Gold"));
		assertEquals("minecraft:pumpkin_pie 1", convert(rules, tags("food"), 0, ACTOR, "Apple Pie"));
	}

	@Test
	void floraFromTheTestBecomesMinecraftPlants() {
		ItemRules rules = defaults();
		// Seen in the dev8 test log as "stays in Skyrim".
		assertEquals("minecraft:cornflower 1", convert(rules, tags("ingredient"), 0, WORLD, "Blue Mountain Flower"));
		assertEquals("minecraft:allium 1", convert(rules, tags("ingredient"), 0, WORLD, "Purple Mountain Flower"));
		assertEquals("minecraft:cod 1", convert(rules, tags("foodraw"), 0, CORPSE, "Mudcrab Legs"));
		assertEquals("minecraft:orange_tulip 1", convert(rules, tags("ingredient"), 0, WORLD, "Dragon's Tongue"));
		assertEquals("minecraft:feather 2", convert(rules, tags("ingredient"), 0, CORPSE, "Hawk Feathers"));
		assertEquals("keep", convert(rules, tags("ingredient"), 0, CORPSE, "Skeever Tail"));
		assertEquals("keep", convert(rules, tags("ingredient"), 0, WORLD, "Nirnroot"));
	}

	@Test
	void metals() {
		ItemRules rules = defaults();
		assertEquals("minecraft:iron_ingot 1", convert(rules, tags("oreingot"), 0, WORLD, "Iron Ingot"));
		assertEquals("minecraft:raw_iron 1", convert(rules, tags("oreingot"), 0, WORLD, "Iron Ore"));
		assertEquals("minecraft:raw_copper 1", convert(rules, tags("oreingot"), 0, WORLD, "Corundum Ore"));
		assertEquals("skycraft:steel_ingot 1", convert(rules, tags("oreingot"), 0, WORLD, "Steel Ingot"));
	}

	@Test
	void skyrimSmithingMaterialsBecomeGearMaterials() {
		ItemRules rules = defaults();
		assertEquals("skycraft:ebony_ingot 1", convert(rules, tags("oreingot"), 0, WORLD, "Ebony Ingot"));
		assertEquals("skycraft:ebony_ingot 1", convert(rules, tags("oreingot"), 0, CONTAINER, "Ebony Ore"));
		assertEquals("skycraft:orichalcum_ingot 1", convert(rules, tags("oreingot"), 0, WORLD, "Orichalcum Ingot"));
		assertEquals("skycraft:orichalcum_ingot 1", convert(rules, tags("oreingot"), 0, WORLD, "Orichalcum Ore"));
		assertEquals("skycraft:dwarven_metal_ingot 1", convert(rules, tags("oreingot"), 0, WORLD, "Dwarven Metal Ingot"));
		assertEquals("skycraft:refined_moonstone 1", convert(rules, tags("oreingot"), 0, WORLD, "Refined Moonstone"));
		assertEquals("skycraft:refined_moonstone 1", convert(rules, tags("oreingot"), 0, WORLD, "Moonstone Ore"));
		assertEquals("skycraft:refined_malachite 1", convert(rules, tags("oreingot"), 0, WORLD, "Refined Malachite"));
		assertEquals("skycraft:refined_malachite 1", convert(rules, tags("oreingot"), 0, WORLD, "Malachite Ore"));
		assertEquals("skycraft:daedra_heart 1", convert(rules, tags("ingredient"), 0, CORPSE, "Daedra Heart"));
		assertEquals("skycraft:leather_strips 1", convert(rules, 0, 0, CONTAINER, "Leather Strips"));
		assertEquals("skycraft:leather_strips 1", convert(rules, tags("hide"), 0, CONTAINER, "Leather Strips"));
		assertEquals("minecraft:leather 1", convert(rules, 0, 0, CONTAINER, "Leather"));
		assertEquals("skycraft:dwarven_metal_ingot 1", convert(rules, tags("clutter"), 0, WORLD, "Bent Dwemer Scrap Metal"));
		assertEquals("skycraft:dwarven_metal_ingot 2", convert(rules, tags("clutter"), 0, WORLD, "Solid Dwemer Metal"));
		assertEquals("skycraft:dwarven_metal_ingot 3", convert(rules, tags("clutter"), 0, WORLD, "Large Dwemer Strut"));
		// Corundum still feeds copper, which the orichalcum and dwarven ingot recipes use.
		assertEquals("minecraft:copper_ingot 1", convert(rules, tags("oreingot"), 0, WORLD, "Corundum Ingot"));
		// Looted gear of the higher tiers stays in Skyrim (only iron and steel salvage).
		assertEquals("keep", convert(rules, tags("weapon"), 10, CORPSE, "Daedric Sword"));
	}

	@Test
	void everySkycraftItemInTheDefaultsExists() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("skycraft:([a-z0-9_]+)").matcher(ItemRules.DEFAULT_TEXT);
		int found = 0;
		while (m.find()) {
			found++;
			assertTrue(dev.skycraft.gear.GearTiers.allItemIds().contains(m.group(1)), "unknown item skycraft:" + m.group(1));
		}
		assertTrue(found >= 8, "expected the gear material rules in the defaults");
	}

	@Test
	void reportsBadLines() {
		List<String> problems = new ArrayList<>();
		ItemRules rules = ItemRules.parse("""
			tag food = minecraft:bread 1
			tag spicy = minecraft:bread 1
			tag food name = minecraft:bread 1
			material mithril = minecraft:iron_ingot 1
			from moon = minecraft:iron_ingot 1
			tag gem = minecraft:diamond 99
			tag gold = keep
			""", problems);
		assertEquals(2, rules.size());
		assertEquals(5, problems.size(), problems.toString());
	}

	@Test
	void nameValuesWithPunctuationMatchLikeItemNames() {
		List<String> problems = new ArrayList<>();
		ItemRules rules = ItemRules.parse("""
			tag food name half-eaten = minecraft:apple 1
			name dwemer's = minecraft:iron_ingot 1
			name - = minecraft:dirt 1
			""", problems);
		assertEquals(1, problems.size(), problems.toString());
		assertEquals(2, rules.size());
		assertEquals("minecraft:apple 1", convert(rules, tags("food"), 0, WORLD, "Half-Eaten Apple"));
		assertEquals("keep", convert(rules, tags("food"), 0, WORLD, "Eaten Apple"));
		assertEquals("minecraft:iron_ingot 1", convert(rules, 0, 0, WORLD, "Dwemer's Gear"));
	}

	@Test
	void tagNamesForLogs() {
		assertEquals("food,foodraw", ItemRules.tagNames(tags("food", "foodraw")));
	}
}
