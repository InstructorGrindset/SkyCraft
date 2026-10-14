package dev.skycraft.skooma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Every effect SkyEffects registers has its name and its 18x18 icon (textures/mob_effect, which 26.3 stitches into the
 * gui atlas as mob_effect/<id>). Reads the mod's resources from the test classpath (no Minecraft). Regenerate the icons
 * with tools/gen_effect_icons.py.
 */
class SkoomaResourcesTest {
	private static byte[] bytes(String path) {
		try (InputStream in = SkoomaResourcesTest.class.getResourceAsStream("/" + path)) {
			assertNotNull(in, "missing resource " + path);
			return in.readAllBytes();
		} catch (IOException e) {
			throw new AssertionError(path, e);
		}
	}

	@Test
	void everyEffectHasANameAndAnIcon() {
		String lang = new String(bytes("assets/skycraft/lang/en_us.json"), StandardCharsets.UTF_8);
		for (String id : SkoomaRules.EFFECT_IDS) {
			assertTrue(lang.contains("\"effect.skycraft." + id + "\""), id + " name");
			byte[] png = bytes("assets/skycraft/textures/mob_effect/" + id + ".png");
			ByteBuffer header = ByteBuffer.wrap(png);
			assertEquals(0x89504E47, header.getInt(0), id + " is a PNG");
			assertEquals(18, header.getInt(16), id + " icon width");
			assertEquals(18, header.getInt(20), id + " icon height");
		}
	}
}
