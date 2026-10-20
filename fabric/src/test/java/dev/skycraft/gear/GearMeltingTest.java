package dev.skycraft.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * SkyCraft's metal gear melts down in a furnace and a blast furnace as vanilla's iron and copper gear does: Skyrim Iron,
 * Steel and Orcish into iron nuggets, Dwarven into copper nuggets; gem and ebony gear (elven, glass, ebony, daedric) is
 * like diamond and netherite gear and doesn't melt. Every piece is listed once and is a real item. Reads the recipes from
 * the test classpath (no Minecraft).
 */
class GearMeltingTest {
	private static final Pattern ITEM = Pattern.compile("\"(skycraft:[a-z_]+)\"");

	private static String text(String path) {
		try (InputStream in = GearMeltingTest.class.getResourceAsStream("/" + path)) {
			assertNotNull(in, "missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(path, e);
		}
	}

	private static List<String> ingredients(String json) {
		List<String> out = new ArrayList<>();
		Matcher m = ITEM.matcher(json);
		while (m.find()) {
			out.add(m.group(1));
		}
		return out;
	}

	private static List<String> pieces(List<String> tiers) {
		List<String> out = new ArrayList<>();
		for (String tier : tiers) {
			for (String piece : GearTiers.PIECES) {
				out.add("skycraft:" + tier + "_" + piece);
			}
		}
		return out;
	}

	@Test
	void metalGearMeltsIntoNuggetsInBothFurnaces() {
		for (String kind : List.of("smelting", "blasting")) {
			String iron = text("data/skycraft/recipe/iron_nugget_from_" + kind + ".json");
			assertTrue(iron.contains("\"type\": \"minecraft:" + kind + "\""), iron);
			assertTrue(iron.contains("\"id\": \"minecraft:iron_nugget\""), iron);
			assertEquals(pieces(List.of("iron", "steel", "orcish")), ingredients(iron));
			String copper = text("data/skycraft/recipe/copper_nugget_from_" + kind + ".json");
			assertTrue(copper.contains("\"type\": \"minecraft:" + kind + "\""), copper);
			assertTrue(copper.contains("\"id\": \"minecraft:copper_nugget\""), copper);
			assertEquals(pieces(List.of("dwarven")), ingredients(copper));
		}
		List<String> gear = new ArrayList<>();
		for (String id : GearTiers.gearItemIds()) {
			gear.add("skycraft:" + id);
		}
		assertTrue(gear.containsAll(pieces(List.of("iron", "steel", "orcish", "dwarven"))));
	}
}
