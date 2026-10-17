package dev.skycraft.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeaturesTest {
	@Test
	void defaultsTurnEverythingOnButTheOptionalOnes() {
		List<String> problems = new ArrayList<>();
		Map<Features.Feature, Boolean> on = Features.parse(Features.DEFAULT_TEXT, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(Features.Feature.values().length, on.size());
		for (Features.Feature f : Features.Feature.values()) {
			assertEquals(f.defaultOn, on.get(f), f.key);
			assertTrue(Features.DEFAULT_TEXT.contains(f.key + (f.defaultOn ? " = on" : " = off")), f.key);
		}
		assertFalse(Features.Feature.NIGHT_MONSTERS.defaultOn);
		assertTrue(Features.Feature.MERCHANTS.defaultOn);
	}

	@Test
	void aMissingLineIsTheFeaturesDefault() {
		Map<Features.Feature, Boolean> on = Features.parse("merchants = off\n", new ArrayList<>());
		assertFalse(on.get(Features.Feature.NIGHT_MONSTERS));
		assertTrue(on.get(Features.Feature.KILL_DROPS));
	}

	@Test
	void everySkyrimBitIsOneBitOfItsOwn() {
		int seen = 0;
		for (Features.Feature f : Features.Feature.values()) {
			if (f.skyrimBit == 0) {
				continue;
			}
			assertEquals(1, Integer.bitCount(f.skyrimBit), f.key);
			assertTrue((f.skyrimBit & dev.skycraft.link.Proto.FEATURES_VALID) == 0, f.key + " uses the valid bit");
			assertTrue((seen & f.skyrimBit) == 0, f.key + " shares a bit");
			seen |= f.skyrimBit;
		}
	}

	@Test
	void switchesOffAndKeepsTheRestOn() {
		List<String> problems = new ArrayList<>();
		Map<Features.Feature, Boolean> on = Features.parse("merchants = off\nKILL_XP = No  # comment\n", problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertFalse(on.get(Features.Feature.MERCHANTS));
		assertFalse(on.get(Features.Feature.KILL_XP));
		assertTrue(on.get(Features.Feature.DEEP_CREATURES));
	}

	@Test
	void reportsBadLines() {
		List<String> problems = new ArrayList<>();
		Map<Features.Feature, Boolean> on = Features.parse("thuum = on\nmerchants maybe\nmerchants = maybe\n", problems);
		assertEquals(3, problems.size(), problems.toString());
		assertTrue(on.get(Features.Feature.MERCHANTS));
	}
}
