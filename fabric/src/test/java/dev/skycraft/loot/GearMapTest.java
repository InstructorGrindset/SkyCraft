package dev.skycraft.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GearMapTest {
	private static int tags(String... names) {
		int t = 0;
		for (String n : names) {
			t |= ItemRules.bit(n);
		}
		return t;
	}

	private static String gear(String tag, String material, String name) {
		GearMap.Gear g = GearMap.of(tags(tag), ItemRules.MATERIALS.indexOf(material), name);
		return g == null ? "nothing" : g.item();
	}

	@Test
	void weaponsBecomeTheirTiersSwordOrWarAxe() {
		assertEquals("skycraft:iron_sword", gear("weapon", "iron", "Iron Sword"));
		assertEquals("skycraft:iron_sword", gear("weapon", "iron", "Iron Dagger"));
		assertEquals("skycraft:steel_sword", gear("weapon", "steel", "Steel Greatsword"));
		assertEquals("skycraft:steel_war_axe", gear("weapon", "steel", "Steel War Axe"));
		assertEquals("skycraft:orcish_war_axe", gear("weapon", "orcish", "Orcish Mace"));
		assertEquals("skycraft:dwarven_war_axe", gear("weapon", "dwarven", "Dwarven Battleaxe"));
		assertEquals("skycraft:elven_war_axe", gear("weapon", "elven", "Elven Warhammer"));
		assertEquals("skycraft:glass_sword", gear("weapon", "glass", "Glass Sword of Burning"));
		assertEquals("skycraft:ebony_war_axe", gear("weapon", "ebony", "Ebony War Axe"));
		assertEquals("skycraft:daedric_sword", gear("weapon", "daedric", "Daedric Dagger"));
	}

	@Test
	void materialsWithoutASetTakeTheNearestTier() {
		assertEquals("skycraft:steel_sword", gear("weapon", "imperial", "Imperial Sword"));
		assertEquals("skycraft:steel_war_axe", gear("weapon", "draugr", "Ancient Nord Battle Axe"));
		assertEquals("skycraft:steel_sword", gear("weapon", "draugr", "Honed Ancient Nord Sword"));
		assertEquals("skycraft:orcish_sword", gear("weapon", "falmer", "Falmer Sword"));
		assertEquals("skycraft:iron_sword", gear("weapon", "silver", "Silver Greatsword"));
		assertEquals("skycraft:daedric_war_axe", gear("weapon", "dragon", "Dragonbone Mace"));
		assertEquals("skycraft:daedric_cuirass", gear("armor", "dragon", "Dragonplate Armor"));
		assertEquals("skycraft:steel_helmet", gear("armor", "imperial", "Imperial Helmet"));
		assertEquals("skycraft:orcish_boots", gear("armor", "falmer", "Falmer Boots"));
		// No known material: weapons with a weapon word are iron, armor is chainmail.
		assertEquals("skycraft:iron_war_axe", gear("weapon", "none", "Forsworn Axe"));
		assertEquals("minecraft:chainmail_chestplate", gear("armor", "none", "Stormcloak Cuirass"));
		assertEquals("minecraft:chainmail_helmet", gear("armor", "none", "Forsworn Headdress"));
		assertEquals("minecraft:wooden_sword", gear("weapon", "wood", "Wooden Sword"));
	}

	@Test
	void armorBySlotGauntletsAreGreaves() {
		assertEquals("skycraft:iron_helmet", gear("armor", "iron", "Iron Helmet"));
		assertEquals("skycraft:steel_cuirass", gear("armor", "steel", "Steel Plate Armor"));
		assertEquals("skycraft:steel_boots", gear("armor", "steel", "Steel Plate Boots"));
		assertEquals("skycraft:steel_boots", gear("armor", "steel", "Steel Shin Boots"));
		assertEquals("skycraft:dwarven_greaves", gear("armor", "dwarven", "Dwarven Gauntlets"));
		assertEquals("skycraft:elven_greaves", gear("armor", "elven", "Elven Gilded Bracers"));
		assertEquals("skycraft:ebony_cuirass", gear("armor", "ebony", "Ebony Mail"));
		assertEquals("minecraft:leather_chestplate", gear("armor", "hide", "Studded Armor"));
		assertEquals("minecraft:leather_helmet", gear("armor", "hide", "Hide Helmet"));
		assertEquals("minecraft:leather_leggings", gear("armor", "hide", "Leather Bracers"));
		assertEquals("minecraft:leather_boots", gear("armor", "hide", "Fur Boots"));
		assertEquals("minecraft:shield", gear("armor", "iron", "Iron Shield"));
		assertEquals("minecraft:shield", gear("armor", "daedric", "Daedric Shield"));
	}

	@Test
	void bowsToolsAndArrowsAreMinecraftsOwn() {
		assertEquals("minecraft:bow", gear("weapon", "steel", "Hunting Bow"));
		assertEquals("minecraft:bow", gear("weapon", "dragon", "Dragonbone Bow"));
		assertEquals("minecraft:crossbow", gear("weapon", "dwarven", "Enhanced Dwarven Crossbow"));
		assertEquals("minecraft:iron_pickaxe", gear("weapon", "iron", "Notched Pickaxe"));
		assertEquals("minecraft:iron_axe", gear("weapon", "iron", "Woodcutter's Axe"));
		assertEquals("minecraft:arrow", gear("arrow", "none", "Iron Arrow"));
		assertEquals("minecraft:arrow", gear("arrow", "none", "Steel Bolt"));
		assertTrue(GearMap.of(tags("arrow"), 0, "Ebony Arrow").ammo());
		assertTrue(!GearMap.of(tags("weapon"), 2, "Iron Sword").ammo());
	}

	@Test
	void staffsClothesAndJewelryDropNothing() {
		assertEquals("nothing", gear("weapon", "none", "Staff of Firebolts"));
		assertEquals("nothing", gear("weapon", "none", "Wabbajack"));
		assertEquals("nothing", gear("clothing", "none", "Fine Clothes"));
		assertEquals("nothing", gear("jewelry", "none", "Gold Ring"));
		assertNull(GearMap.of(tags("armor", "clothing"), 0, "Hooded Monk Robes"));
		assertNull(GearMap.of(tags("food"), 0, "Apple Pie"));
		assertNull(GearMap.of(0, 0, null));
	}

	@Test
	void theirTiersAreRegisteredGear() {
		java.util.Set<String> ids = new java.util.HashSet<>(dev.skycraft.gear.GearTiers.gearItemIds());
		for (String material : ItemRules.MATERIALS) {
			for (String name : List.of("Sword", "Mace", "Helmet", "Armor", "Gauntlets", "Boots")) {
				GearMap.Gear g = GearMap.of(tags(name.equals("Sword") || name.equals("Mace") ? "weapon" : "armor"), ItemRules.MATERIALS.indexOf(material),
					material + " " + name);
				if (g != null && g.item().startsWith("skycraft:")) {
					assertTrue(ids.contains(g.item().substring("skycraft:".length())), g.item());
				}
			}
		}
	}

	@Test
	void chanceIsMinecraftsPlusLooting() {
		assertEquals(0.085F, GearMap.chance(GearMap.VANILLA_CHANCE, GearMap.VANILLA_LOOTING, 0), 1e-6F);
		assertEquals(0.095F, GearMap.chance(GearMap.VANILLA_CHANCE, GearMap.VANILLA_LOOTING, 1), 1e-6F);
		assertEquals(0.115F, GearMap.chance(GearMap.VANILLA_CHANCE, GearMap.VANILLA_LOOTING, 3), 1e-6F);
		assertEquals(1.0F, GearMap.chance(0.9F, 0.5F, 3), 1e-6F);
	}

	@Test
	void arrowsAreASkeletonsNeverMoreThanTheyHad() {
		Random random = new Random(1);
		int[] seen = new int[6];
		for (int i = 0; i < 2000; i++) {
			int n = GearMap.arrows(random, 2, 3, 20);
			assertTrue(n >= 0 && n <= 5, "arrows " + n);
			seen[n]++;
		}
		assertTrue(seen[0] > 0 && seen[5] > 0);
		for (int i = 0; i < 200; i++) {
			assertTrue(GearMap.arrows(random, 2, 0, 20) <= 2);
			assertTrue(GearMap.arrows(random, 2, 3, 1) <= 1);
		}
	}

	@Test
	void damageIsMinecraftsRandomWear() {
		Random random = new Random(2);
		for (int i = 0; i < 2000; i++) {
			int d = GearMap.damage(250, random);
			assertTrue(d >= 4 && d <= 250, "damage " + d);
		}
		assertEquals(1, GearMap.damage(1, random));
	}

	@Test
	void settingsParseAndReportBadLines() {
		List<String> problems = new ArrayList<>();
		GearMap.Settings s = GearMap.parse(GearMap.DEFAULT_TEXT, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(new GearMap.Settings(GearMap.VANILLA_CHANCE, GearMap.VANILLA_LOOTING, GearMap.VANILLA_ARROWS), s);
		s = GearMap.parse("chance = 0.5\nlooting = lots\narrows = 4 # more\nmoons = 2\nchance 1\nchance = 2\n", problems);
		assertEquals(new GearMap.Settings(0.5F, GearMap.VANILLA_LOOTING, 4), s);
		assertEquals(4, problems.size(), problems.toString());
	}
}
