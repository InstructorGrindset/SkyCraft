package dev.skycraft.skooma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.loot.ItemRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Brewing Skooma: moon sugar has its item definition, model, texture and name, both Skooma potions their names in every
 * bottle, the brewing recipes (26.3's minecraft:brewing format) brew them from an awkward potion and moon sugar and from
 * Skooma and glowstone dust, in every bottle, only while skooma_brewing is on, and item conversion can name the potions.
 * Reads the mod's resources from the test classpath (no Minecraft). The icon comes from tools/gen_skooma_textures.py.
 */
class SkoomaBrewingTest {
	private static final String MOON_SUGAR = "moon_sugar";
	private static final List<String> POTIONS = List.of("skooma", "redwater_skooma");
	private static final List<String> BOTTLES = List.of("potion", "splash_potion", "lingering_potion");

	private static String text(String path) {
		try (InputStream in = SkoomaBrewingTest.class.getResourceAsStream("/" + path)) {
			assertNotNull(in, "missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(path, e);
		}
	}

	private static String compact(String json) {
		return json.replaceAll("\\s+", "");
	}

	@Test
	void moonSugarHasModelTextureAndName() {
		assertTrue(text("assets/skycraft/items/" + MOON_SUGAR + ".json").contains("\"skycraft:item/" + MOON_SUGAR + "\""));
		assertTrue(text("assets/skycraft/models/item/" + MOON_SUGAR + ".json").contains("\"skycraft:item/" + MOON_SUGAR + "\""));
		assertFalse(text("assets/skycraft/textures/item/" + MOON_SUGAR + ".png").isEmpty());
		assertTrue(text("assets/skycraft/lang/en_us.json").contains("\"item.skycraft.moon_sugar\": \"Moon Sugar\""));
	}

	@Test
	void everyBottleOfSkoomaHasAName() {
		String lang = text("assets/skycraft/lang/en_us.json");
		for (String potion : POTIONS) {
			for (String bottle : List.of("potion", "splash_potion", "lingering_potion", "tipped_arrow")) {
				assertTrue(lang.contains("\"item.minecraft." + bottle + ".effect." + potion + "\""), bottle + " of " + potion);
			}
		}
		assertTrue(lang.contains("\"item.minecraft.potion.effect.skooma\": \"Skooma\""));
		assertTrue(lang.contains("\"item.minecraft.potion.effect.redwater_skooma\": \"Redwater Skooma\""));
	}

	@Test
	void anAwkwardPotionAndMoonSugarBrewSkoomaInEveryBottle() {
		for (String bottle : BOTTLES) {
			String r = compact(text("data/skycraft/recipe/brewing/" + bottle + "_awkward_moon_sugar.json"));
			assertTrue(r.contains("\"type\":\"minecraft:brewing\""), r);
			assertTrue(r.contains("\"input\":{\"item\":\"minecraft:" + bottle + "\",\"potion_contents\":{\"potions\":\"minecraft:awkward\"}}"), r);
			assertTrue(r.contains("\"reagent\":{\"item\":\"skycraft:moon_sugar\"}"), r);
			assertTrue(r.contains("\"potion\":\"skycraft:skooma\"}},\"id\":\"minecraft:" + bottle + "\""), r);
			String red = compact(text("data/skycraft/recipe/brewing/" + bottle + "_skooma_glowstone_dust.json"));
			assertTrue(red.contains("\"potions\":\"skycraft:skooma\""), red);
			assertTrue(red.contains("\"reagent\":{\"item\":\"minecraft:glowstone_dust\"}"), red);
			assertTrue(red.contains("\"potion\":\"skycraft:redwater_skooma\"}},\"id\":\"minecraft:" + bottle + "\""), red);
		}
		for (String potion : POTIONS) {
			String splash = compact(text("data/skycraft/recipe/brewing/potion_" + potion + "_gunpowder.json"));
			assertTrue(splash.contains("\"reagent\":{\"item\":\"minecraft:gunpowder\"}") && splash.contains("\"id\":\"minecraft:splash_potion\""), splash);
			String lingering = compact(text("data/skycraft/recipe/brewing/splash_potion_" + potion + "_dragon_breath.json"));
			assertTrue(lingering.contains("\"reagent\":{\"item\":\"minecraft:dragon_breath\"}") && lingering.contains("\"id\":\"minecraft:lingering_potion\""),
				lingering);
		}
	}

	@Test
	void theRecipesLoadOnlyWithSkoomaBrewingOn() {
		List<String> names = new ArrayList<>();
		for (String bottle : BOTTLES) {
			names.add(bottle + "_awkward_moon_sugar");
			names.add(bottle + "_skooma_glowstone_dust");
		}
		for (String potion : POTIONS) {
			names.add("potion_" + potion + "_gunpowder");
			names.add("splash_potion_" + potion + "_dragon_breath");
		}
		for (String name : names) {
			String r = compact(text("data/skycraft/recipe/brewing/" + name + ".json"));
			assertTrue(r.contains("\"fabric:load_conditions\":[{\"condition\":\"skycraft:feature_on\",\"feature\":\"skooma_brewing\"}]"), name);
		}
		assertEquals("skooma_brewing", dev.skycraft.config.Features.Feature.SKOOMA_BREWING.key);
	}

	@Test
	void itemConversionCanNameTheSkoomaPotions() {
		// The rule lines that make converted Skooma the brewed and bought one, and moon sugar SkyCraft's.
		List<String> problems = new ArrayList<>();
		ItemRules rules = ItemRules.parse("""
			name redwater name skooma = minecraft:potion[skycraft:redwater_skooma] 1
			name skooma = minecraft:potion[skycraft:skooma] 1
			tag ingredient name moon name sugar = skycraft:moon_sugar 1
			name moon = minecraft:potion[skycraft:moonshine] 1
			""", problems);
		assertEquals(1, problems.size(), problems.toString()); // the unknown potion
		int potion = 1 << ItemRules.TAGS.indexOf("potion");
		int ingredient = 1 << ItemRules.TAGS.indexOf("ingredient");
		assertEquals("skycraft:skooma", rules.match(new ItemRules.Item(potion, 0, 4, "Skooma")).potion());
		assertEquals("skycraft:redwater_skooma", rules.match(new ItemRules.Item(potion, 0, 4, "Redwater Skooma")).potion());
		assertEquals("skycraft:moon_sugar", rules.match(new ItemRules.Item(ingredient, 0, 1, "Moon Sugar")).item());
		assertEquals(List.of("skycraft:skooma", "skycraft:redwater_skooma"), ItemRules.SKYCRAFT_POTIONS);
	}
}
