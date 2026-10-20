package dev.skycraft.skills;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The brewed Stealth potions have their names for every item a potion can be (a potion, splash and lingering potion, a
 * tipped arrow), and the skills' mixins are in the mixin config. Reads the mod's resources from the test classpath (no
 * Minecraft).
 */
class SkillsResourcesTest {
	private static String text(String path) {
		try (InputStream in = SkillsResourcesTest.class.getResourceAsStream("/" + path)) {
			assertNotNull(in, "missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(path, e);
		}
	}

	@Test
	void stealthPotionsHaveTheirNames() {
		String lang = text("assets/skycraft/lang/en_us.json");
		for (String item : SkillRules.POTION_ITEMS) {
			String key = "\"item.minecraft." + item + ".effect." + SkillRules.STEALTH_POTION + "\"";
			assertTrue(lang.contains(key), key);
		}
	}

	@Test
	void theMixinsAreListed() {
		String mixins = text("skycraft.mixins.json");
		for (String name : new String[] { "LivingEntityStealthMixin", "ArcheryMixin", "AnvilSmithingMixin", "EnchantingTableMixin", "EnchantingCostMixin",
			"EnchantingLapisSlotMixin", "BrewingTakeMixin" }) {
			assertTrue(mixins.contains("\"" + name + "\""), name);
		}
	}
}
