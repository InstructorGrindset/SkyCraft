package dev.skycraft.portal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The soul gems have their item definition, model, texture and name, and the mixin that turns vanilla
 * Nether travel into the network is in the mixin config. Reads the mod's resources from the test
 * classpath (no Minecraft). Textures come from tools/gen_portal_textures.py.
 */
class PortalResourcesTest {
	private static String text(String path) {
		try (InputStream in = PortalResourcesTest.class.getResourceAsStream("/" + path)) {
			assertNotNull(in, "missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(path, e);
		}
	}

	@Test
	void everyGemHasModelTextureAndName() {
		String lang = text("assets/skycraft/lang/en_us.json");
		for (String id : PortalStore.ITEM_IDS) {
			assertTrue(text("assets/skycraft/items/" + id + ".json").contains("\"skycraft:item/" + id + "\""), id + " item definition");
			assertTrue(text("assets/skycraft/models/item/" + id + ".json").contains("\"skycraft:item/" + id + "\""), id + " model texture");
			assertFalse(text("assets/skycraft/textures/item/" + id + ".png").isEmpty(), id + " texture");
			assertTrue(lang.contains("\"item.skycraft." + id + "\""), id + " name");
		}
	}

	@Test
	void theNetherPortalMixinIsRegistered() {
		assertTrue(text("skycraft.mixins.json").contains("\"NetherPortalBlockMixin\""));
	}
}
