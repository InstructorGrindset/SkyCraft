package dev.skycraft.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
		return r.keep() ? "keep" : r.label() + " " + r.perItem();
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
			assertTrue(dev.skycraft.gear.GearTiers.allItemIds().contains(m.group(1)) || dev.skycraft.portal.PortalStore.ITEM_IDS.contains(m.group(1)),
				"unknown item skycraft:" + m.group(1));
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
	void potionResultsNameTheirPotionInBrackets() {
		List<String> problems = new ArrayList<>();
		ItemRules rules = ItemRules.parse("""
			tag potion name healing = minecraft:potion[healing] 1
			tag poison name weak = minecraft:splash_potion[poison]
			tag poison name deadly = minecraft:lingering_potion[ Minecraft:Strong_Harming ] 2
			tag arrow name poisoned = minecraft:tipped_arrow[long_poison] 8
			tag potion name luck = potion[luck] 1
			tag food = minecraft:bread 1
			""", problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(6, rules.size());
		ItemRules.Result healing = rules.match(new ItemRules.Item(tags("potion"), 0, BOUGHT, "Potion of Minor Healing"));
		assertEquals("minecraft:potion", healing.item());
		assertEquals("healing", healing.potion());
		assertEquals(1, healing.perItem());
		assertEquals("minecraft:splash_potion[poison] 1", convert(rules, tags("poison"), 0, CORPSE, "Weak Poison"));
		assertEquals("minecraft:lingering_potion[strong_harming] 2", convert(rules, tags("poison"), 0, CORPSE, "Deadly Poison"));
		assertEquals("minecraft:tipped_arrow[long_poison] 8", convert(rules, tags("arrow"), 0, WORLD, "Poisoned Arrow"));
		assertEquals("potion[luck] 1", convert(rules, tags("potion"), 0, WORLD, "Draught of Luck"));
		// The old syntax still gives plain items.
		ItemRules.Result bread = rules.match(new ItemRules.Item(tags("food"), 0, WORLD, "Bread"));
		assertEquals("minecraft:bread", bread.item());
		assertNull(bread.potion());
		assertEquals("minecraft:bread", bread.label());
	}

	@Test
	void reportsBadPotionsAndEachUnknownPotionOnce() {
		List<String> problems = new ArrayList<>();
		ItemRules rules = ItemRules.parse("""
			tag potion name speed = minecraft:potion[speed] 1
			tag potion name haste = minecraft:potion[speed] 1
			tag potion name quick = minecraft:splash_potion[speed] 1
			tag potion name night = minecraft:potion[nightvision] 1
			tag potion name bread = minecraft:bread[healing] 1
			tag potion name open = minecraft:potion[healing 1
			tag potion name empty = minecraft:potion[] 1
			tag potion name count = minecraft:potion[healing] lots
			tag potion name many = minecraft:potion[healing] 65
			tag potion name keep = keep
			tag potion name fine = minecraft:potion[swiftness] 1
			""", problems);
		assertEquals(2, rules.size());
		// One problem per bad line, but 'speed' (three lines) is reported once, naming every line.
		assertEquals(7, problems.size(), problems.toString());
		assertEquals(1L, problems.stream().filter(p -> p.contains("'speed'")).count(), problems.toString());
		assertTrue(problems.contains("line 1: unknown potion 'speed', rule skipped (also line 2, 3)"), problems.toString());
		assertTrue(problems.contains("line 4: unknown potion 'nightvision', rule skipped"), problems.toString());
		assertTrue(problems.stream().anyMatch(p -> p.startsWith("line 5: only potion")), problems.toString());
		assertTrue(problems.stream().anyMatch(p -> p.startsWith("line 6: no ']'")), problems.toString());
		// Skipped rules don't match: the item falls through to later rules, or stays in Skyrim.
		assertEquals("keep", convert(rules, tags("potion"), 0, WORLD, "Potion of Speed"));
		assertEquals("minecraft:potion[swiftness] 1", convert(rules, tags("potion"), 0, WORLD, "Fine Potion"));
	}

	@Test
	void potionIdsAreThe26Point3OnesAndAllDocumented() {
		assertEquals(46, ItemRules.POTIONS.size());
		assertEquals(46, new java.util.HashSet<>(ItemRules.POTIONS).size());
		for (String id : ItemRules.POTIONS) {
			assertTrue(java.util.regex.Pattern.compile("[ :]" + id + "(\\s|$)", java.util.regex.Pattern.MULTILINE).matcher(ItemRules.DEFAULT_TEXT).find(),
				"potion " + id + " missing from the list in the defaults");
		}
	}

	/** What the Skyrim side sends: the display name cut to 27 bytes (ItemEvent.name is char[28]). */
	private static String sent(String name) {
		return name.length() > 27 ? name.substring(0, 27) : name;
	}

	private static String potion(ItemRules rules, String name) {
		return convert(rules, tags("potion"), 0, CONTAINER, sent(name));
	}

	private static String poison(ItemRules rules, String name) {
		return convert(rules, tags("poison"), 0, CORPSE, sent(name));
	}

	@Test
	void skyrimPotionsBecomeTheMatchingMinecraftPotion() {
		ItemRules rules = defaults();
		// Names from Skyrim.esm, Dragonborn.esm and USSEP; crafted potions are "Potion of " + the effect.
		assertEquals("minecraft:potion[healing] 1", potion(rules, "Potion of Minor Healing"));
		assertEquals("minecraft:potion[healing] 1", potion(rules, "Potion of Healing"));
		assertEquals("minecraft:potion[strong_healing] 1", potion(rules, "Potion of Plentiful Healing"));
		assertEquals("minecraft:potion[strong_healing] 1", potion(rules, "Potion of Ultimate Healing"));
		assertEquals("minecraft:potion[healing] 1", potion(rules, "Potion of Minor Well-being"));
		assertEquals("minecraft:potion[strong_healing] 1", potion(rules, "Potion of Vigorous Well-being"));
		assertEquals("minecraft:potion[healing] 1", potion(rules, "Potion of Restore Health"));
		assertEquals("minecraft:potion[regeneration] 1", potion(rules, "Draught of Regeneration"));
		assertEquals("minecraft:potion[strong_regeneration] 1", potion(rules, "Elixir of Regeneration"));
		assertEquals("minecraft:potion[regeneration] 1", potion(rules, "Potion of Health"));
		assertEquals("minecraft:potion[regeneration] 1", potion(rules, "Potion of Minor Magicka"));
		assertEquals("minecraft:potion[regeneration] 1", potion(rules, "Solution of Lasting Potency"));
		assertEquals("minecraft:potion[regeneration] 1", potion(rules, "Potion of Regenerate Magicka"));
		assertEquals("minecraft:potion[strength] 1", potion(rules, "Potion of the Warrior"));
		assertEquals("minecraft:potion[strength] 1", potion(rules, "Draught of the Berserker"));
		assertEquals("minecraft:potion[strong_strength] 1", potion(rules, "Elixir of the Berserker"));
		assertEquals("minecraft:potion[strong_strength] 1", potion(rules, "Prime Elixir of Conflict"));
		assertEquals("minecraft:potion[strength] 1", potion(rules, "Potion of True Shot"));
		assertEquals("minecraft:potion[strength] 1", potion(rules, "Elixir of Strength"));
		assertEquals("minecraft:potion[strength] 1", potion(rules, "Potion of Fortify Two-handed"));
		assertEquals("minecraft:potion[strength] 1", potion(rules, "Potion of Fortify Carry Weight"));
		assertEquals("minecraft:potion[swiftness] 1", potion(rules, "Potion of Minor Stamina"));
		assertEquals("minecraft:potion[swiftness] 1", potion(rules, "Solution of Enhanced Stamina"));
		assertEquals("minecraft:potion[swiftness] 1", potion(rules, "Potion of Vigor"));
		assertEquals("minecraft:potion[swiftness] 1", potion(rules, "Potion of Regenerate Stamina"));
		assertEquals("minecraft:potion[swiftness] 1", potion(rules, "Skirmisher's Philter"));
		assertEquals("minecraft:potion[turtle_master] 1", potion(rules, "Potion of the Defender"));
		assertEquals("minecraft:potion[turtle_master] 1", potion(rules, "Elixir of the Knight"));
		assertEquals("minecraft:potion[invisibility] 1", potion(rules, "Potion of Brief Invisibility"));
		assertEquals("minecraft:potion[long_invisibility] 1", potion(rules, "Potion of Prolonged Invisibility"));
		assertEquals("minecraft:potion[invisibility] 1", potion(rules, "Potion of Invisibility"));
		assertEquals("minecraft:potion[long_invisibility] 1", potion(rules, "Grand Elixir of Escape"));
		assertEquals("minecraft:potion[invisibility] 1", potion(rules, "Draught of Light Feet"));
		assertEquals("minecraft:potion[invisibility] 1", potion(rules, "Potion of Fortify Sneak"));
		// Philter of the Phantom has no vendor keyword without USSEP.
		assertEquals("minecraft:potion[invisibility] 1", convert(rules, 0, 0, WORLD, "Philter of the Phantom"));
		assertEquals("minecraft:potion[fire_resistance] 1", potion(rules, "Potion of Resist Fire"));
		assertEquals("minecraft:potion[long_fire_resistance] 1", potion(rules, "Elixir of Resist Fire"));
		assertEquals("minecraft:potion[water_breathing] 1", potion(rules, "Potion of Waterbreathing"));
		assertEquals("minecraft:potion[long_water_breathing] 1", potion(rules, "Philter of Waterbreathing"));
		// Bought in a Skyrim shop converts the same way.
		assertEquals("minecraft:potion[healing] 1", convert(rules, tags("potion"), 0, BOUGHT, "Potion of Minor Healing"));
	}

	@Test
	void skyrimPoisonsBecomeSplashPotions() {
		ItemRules rules = defaults();
		assertEquals("minecraft:splash_potion[harming] 1", poison(rules, "Weak Poison"));
		assertEquals("minecraft:splash_potion[harming] 1", poison(rules, "Poison"));
		assertEquals("minecraft:splash_potion[strong_harming] 1", poison(rules, "Deadly Poison"));
		assertEquals("minecraft:splash_potion[harming] 1", poison(rules, "Poison of Damage Health"));
		assertEquals("minecraft:splash_potion[harming] 1", poison(rules, "Poison of Ravage Health"));
		assertEquals("minecraft:splash_potion[poison] 1", poison(rules, "Weak Lingering Poison"));
		assertEquals("minecraft:splash_potion[strong_poison] 1", poison(rules, "Deadly Lingering Poison"));
		assertEquals("minecraft:splash_potion[poison] 1", poison(rules, "Poison of Lingering Damage Health"));
		assertEquals("minecraft:splash_potion[poison] 1", poison(rules, "Frostbite Venom"));
		assertEquals("minecraft:splash_potion[slowness] 1", poison(rules, "Weak Paralysis Poison"));
		assertEquals("minecraft:splash_potion[strong_slowness] 1", poison(rules, "Virulent Paralysis Poison"));
		assertEquals("minecraft:splash_potion[strong_slowness] 1", poison(rules, "Poison of Paralysis"));
		assertEquals("minecraft:splash_potion[slowness] 1", poison(rules, "Poison of Slow"));
		assertEquals("minecraft:splash_potion[weakness] 1", poison(rules, "Potent Fear Poison"));
		assertEquals("minecraft:splash_potion[weakness] 1", poison(rules, "Weak Magicka Poison"));
		assertEquals("minecraft:splash_potion[weakness] 1", poison(rules, "Lingering Stamina Poison"));
		assertEquals("minecraft:splash_potion[weakness] 1", poison(rules, "Malign Vigor Poison"));
		assertEquals("minecraft:splash_potion[weakness] 1", poison(rules, "Poison of Ravage Stamina"));
		assertEquals("minecraft:splash_potion[weakness] 1", poison(rules, "Poison of Damage Magicka Regen"));
	}

	@Test
	void skyrimOnlyPotionsAndPoisonsStay() {
		ItemRules rules = defaults();
		assertEquals("keep", potion(rules, "Blacksmith's Elixir"));
		assertEquals("keep", potion(rules, "Potion of Fortify Smithing"));
		assertEquals("keep", potion(rules, "Draught of the Healer"));
		assertEquals("keep", potion(rules, "Potion of Lockpicking"));
		assertEquals("keep", potion(rules, "Prime Elixir of Larceny"));
		assertEquals("keep", potion(rules, "Potion of Cure Disease"));
		assertEquals("keep", potion(rules, "Elixir of Resist Cold"));
		assertEquals("keep", potion(rules, "Potion of Resist Frost"));
		assertEquals("keep", potion(rules, "Draught of Resist Magic"));
		assertEquals("keep", potion(rules, "Potion of Waterwalking"));
		assertEquals("keep", potion(rules, "Soul Husk Extract"));
		assertEquals("keep", potion(rules, "The White Phial (Full)"));
		assertEquals("keep", convert(rules, 0, 0, WORLD, "Skooma"));
		assertEquals("keep", poison(rules, "Weak Frenzy Poison"));
		assertEquals("keep", poison(rules, "Deadly Aversion to Fire"));
		assertEquals("keep", poison(rules, "Poison of Weakness to Poison"));
		assertEquals("keep", poison(rules, "Lotus Extract"));
		assertEquals("keep", poison(rules, "Nightshade Extract"));
	}

	@Test
	void tagNamesForLogs() {
		assertEquals("food,foodraw", ItemRules.tagNames(tags("food", "foodraw")));
	}

	@Test
	void filledBigSoulGemsBecomePortalKeys() {
		ItemRules rules = defaults();
		// Skyrim's filled base forms share the empty gems' names; the filled tag tells them apart.
		assertEquals("skycraft:filled_soul_gem 1", convert(rules, tags("soulgem", "filled"), 0, CONTAINER, "Grand Soul Gem"));
		assertEquals("skycraft:filled_soul_gem 1", convert(rules, tags("soulgem", "filled"), 0, BOUGHT, "Greater Soul Gem"));
		assertEquals("skycraft:filled_soul_gem 1", convert(rules, tags("soulgem", "filled"), 0, CORPSE, "Black Soul Gem"));
		assertEquals("keep", convert(rules, tags("soulgem"), 0, CONTAINER, "Grand Soul Gem"));
		assertEquals("keep", convert(rules, tags("soulgem"), 0, BOUGHT, "Black Soul Gem"));
		assertEquals("keep", convert(rules, tags("soulgem", "filled"), 0, CONTAINER, "Common Soul Gem"));
		assertEquals("keep", convert(rules, tags("soulgem", "filled"), 0, CONTAINER, "Petty Soul Gem"));
		assertEquals("keep", convert(rules, tags("soulgem", "filled"), 0, WORLD, "Azura's Star"));
		assertEquals("keep", convert(rules, tags("soulgem", "filled"), 0, WORLD, "The Black Star"));
		// Soul gem fragments are misc items, not soul gems.
		assertEquals("keep", convert(rules, tags("clutter"), 0, CONTAINER, "Soul Gem Fragment"));
		assertEquals("soulgem,filled", ItemRules.tagNames(tags("soulgem", "filled")));
	}
}
