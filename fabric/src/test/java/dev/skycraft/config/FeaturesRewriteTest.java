package dev.skycraft.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.config.Features.Feature;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The settings screen's saving: skycraft-features.txt rewritten one switch at a time, and which changes count at once. */
class FeaturesRewriteTest {
	private static Map<Feature, Boolean> parse(String text) {
		List<String> problems = new ArrayList<>();
		Map<Feature, Boolean> on = Features.parse(text, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		return on;
	}

	@Test
	void changesOnlyTheSwitchsLineAndKeepsEverythingElse() {
		String text = "# My notes\n\n# merchants, for trading\nmerchants = on\nkill_xp = on   # I like XP\nthuum = on\n\n# the end\n";
		String out = Features.rewrite(text, Map.of(Feature.MERCHANTS, false));
		assertEquals("# My notes\n\n# merchants, for trading\nmerchants = off\nkill_xp = on   # I like XP\nthuum = on\n\n# the end\n", out);
	}

	@Test
	void keepsTheKeysSpellingSpacingAndTrailingComment() {
		assertEquals("  KILL_XP=off   # I like XP\n", Features.rewrite("  KILL_XP=on   # I like XP\n", Map.of(Feature.KILL_XP, false)));
		assertEquals("Kill_Xp = on\n", Features.rewrite("Kill_Xp = no\n", Map.of(Feature.KILL_XP, true)));
	}

	@Test
	void aLineThatAlreadySaysSoStaysAsWritten() {
		String text = "merchants = Yes\nkill_xp = false # off for now\n";
		assertEquals(text, Features.rewrite(text, Map.of(Feature.MERCHANTS, true, Feature.KILL_XP, false)));
	}

	@Test
	void anEmptyOrBadValueGetsOne() {
		assertEquals("merchants = off\n", Features.rewrite("merchants =\n", Map.of(Feature.MERCHANTS, false)));
		assertEquals("merchants= on # was broken\n", Features.rewrite("merchants=# was broken\n", Map.of(Feature.MERCHANTS, true)));
		assertEquals("merchants = off\n", Features.rewrite("merchants = maybe\n", Map.of(Feature.MERCHANTS, false)));
	}

	@Test
	void everyLineOfARepeatedKeyChanges() {
		String out = Features.rewrite("merchants = on\nmerchants = on\n", Map.of(Feature.MERCHANTS, false));
		assertEquals("merchants = off\nmerchants = off\n", out);
		assertFalse(parse(out).get(Feature.MERCHANTS));
	}

	@Test
	void keepsWindowsLineEndings() {
		String out = Features.rewrite("# notes\r\nmerchants = on\r\nkill_xp = on\r\n", Map.of(Feature.KILL_XP, false));
		assertEquals("# notes\r\nmerchants = on\r\nkill_xp = off\r\n", out);
	}

	@Test
	void aSwitchWithoutALineIsAddedAtTheEndWithItsComment() {
		String out = Features.rewrite("merchants = on", Map.of(Feature.GUIDE_BOOK, false));
		assertEquals("merchants = on\n\n# " + Feature.GUIDE_BOOK.about + "\nguide_book = off\n", out);
		Map<Feature, Boolean> on = parse(out);
		assertFalse(on.get(Feature.GUIDE_BOOK));
		assertTrue(on.get(Feature.MERCHANTS));
		assertEquals("\n# " + Feature.KILL_XP.about + "\nkill_xp = off\n", Features.rewrite("", Map.of(Feature.KILL_XP, false)));
	}

	@Test
	void flippingEachSwitchInTheDefaultsChangesThatOneOnly() {
		for (Feature f : Feature.values()) {
			String out = Features.rewrite(Features.DEFAULT_TEXT, Map.of(f, !f.defaultOn));
			Map<Feature, Boolean> on = parse(out);
			for (Feature g : Feature.values()) {
				assertEquals(g == f ? !g.defaultOn : g.defaultOn, on.get(g), f.key + " flipped, " + g.key);
			}
			assertEquals(Features.DEFAULT_TEXT.split("\n", -1).length, out.split("\n", -1).length, f.key + " added or lost lines");
			// And back again gives the defaults exactly.
			assertEquals(Features.DEFAULT_TEXT, Features.rewrite(out, Map.of(f, f.defaultOn)), f.key);
		}
	}

	@Test
	void aChangeCountsAtOnceUnlessTheSwitchWaitsForARestart() {
		Map<Feature, Boolean> live = new EnumMap<>(Feature.class);
		for (Feature f : Feature.values()) {
			live.put(f, f.defaultOn);
		}
		Map<Feature, Boolean> after = Features.afterSet(live, Feature.MERCHANTS, false);
		assertFalse(after.get(Feature.MERCHANTS));
		assertTrue(live.get(Feature.MERCHANTS), "the old map is left as it was (other threads may hold it)");
		assertTrue(after.get(Feature.KILL_DROPS));
		assertTrue(Features.needsRestart(Feature.MINECRAFT_VIEW));
		assertTrue(Features.needsRestart(Feature.NATURAL_SPAWNS));
		assertFalse(Features.needsRestart(Feature.MERCHANTS));
		assertTrue(Features.afterSet(live, Feature.MINECRAFT_VIEW, false).get(Feature.MINECRAFT_VIEW), "minecraft_view waits for the next start");
	}
}
