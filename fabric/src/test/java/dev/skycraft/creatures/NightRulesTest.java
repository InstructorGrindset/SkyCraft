package dev.skycraft.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.deep.DeepRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NightRulesTest {
	private static NightRules defaults() {
		List<String> problems = new ArrayList<>();
		NightRules rules = NightRules.parse(NightRules.DEFAULT_TEXT, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		return rules;
	}

	@Test
	void defaultsParseCleanly() {
		NightRules r = defaults();
		assertTrue(r.enabled());
		assertEquals(20.0F, r.nightFrom(), 0.0F);
		assertEquals(5.0F, r.nightTo(), 0.0F);
		assertEquals(0.4, r.chance(), 1e-9);
		assertEquals(2, r.maxAlive());
		assertEquals(16.0, r.minDistance(), 0.0);
		assertEquals(32.0, r.maxDistance(), 0.0);
		assertEquals(72.0, r.despawnDistance(), 0.0);
		assertEquals(0, r.maxBlockLight());
		assertEquals(64.0, r.barrowDistance(), 0.0);
		assertEquals(2, r.creatures().size());
		assertEquals(2, r.barrowCreatures().size());
	}

	@Test
	void nightWrapsPastMidnight() {
		NightRules r = defaults();
		assertTrue(r.isNight(20.0F));
		assertTrue(r.isNight(23.9F));
		assertTrue(r.isNight(0.0F));
		assertTrue(r.isNight(4.99F));
		assertFalse(r.isNight(5.0F));
		assertFalse(r.isNight(12.0F));
		assertFalse(r.isNight(19.9F));
		NightRules early = NightRules.parse("night = 1-4\n", new ArrayList<>());
		assertTrue(early.isNight(2.0F));
		assertFalse(early.isNight(22.0F));
		assertFalse(NightRules.parse("night = 3-3\n", new ArrayList<>()).isNight(3.0F));
	}

	@Test
	void sharesTheDeepCreaturesCap() {
		NightRules r = defaults();
		assertEquals(2, r.cap(3));
		assertEquals(1, r.cap(1));
		assertEquals(0, r.cap(0));
	}

	@Test
	void picksByWeightAndNearBarrows() {
		NightRules r = defaults();
		Map<Integer, Integer> open = new HashMap<>();
		Map<Integer, Integer> barrow = new HashMap<>();
		for (int pick = 0; pick < 500; pick++) {
			open.merge(r.pick(-1.0, pick), 1, Integer::sum);
			barrow.merge(r.pick(30.0, pick), 1, Integer::sum);
		}
		// skeever 3, skeleton 2 in the open; draugr 3, skeleton 1 by a barrow.
		assertEquals(300, (int) open.getOrDefault(DeepRules.SKEEVER, 0));
		assertEquals(200, (int) open.getOrDefault(DeepRules.SKELETON, 0));
		assertEquals(0, (int) open.getOrDefault(DeepRules.DRAUGR, 0));
		assertEquals(375, (int) barrow.getOrDefault(DeepRules.DRAUGR, 0));
		assertEquals(125, (int) barrow.getOrDefault(DeepRules.SKELETON, 0));
		// Too far from the barrow: the open country's.
		assertEquals(DeepRules.SKEEVER, r.pick(65.0, 0));
	}

	@Test
	void rangeDarkAndCooldown() {
		NightRules r = defaults();
		assertFalse(r.inSpawnRange(15.9 * 15.9));
		assertTrue(r.inSpawnRange(20.0 * 20.0));
		assertFalse(r.inSpawnRange(32.1 * 32.1));
		assertTrue(r.darkEnough(0));
		assertFalse(r.darkEnough(1));
		assertEquals(1200L, r.cooldownTicks(0.0));
		assertEquals(2400L, r.cooldownTicks(1.0));
	}

	@Test
	void reportsBadLinesAndKeepsDefaults() {
		List<String> problems = new ArrayList<>();
		NightRules r = NightRules.parse("""
			night = 20
			chance = 2
			creatures = skeever 2, dragon
			distance = 2-100
			max_alive = lots
			colour = red
			despawn_distance = 20
			""", problems);
		assertEquals(7, problems.size(), problems.toString());
		assertEquals(0.4, r.chance(), 1e-9);
		assertEquals(2, r.creatures().size());
		assertEquals(16.0, r.minDistance(), 0.0);
		assertEquals(40.0, r.despawnDistance(), 0.0); // raised to clear the spawn distance
	}

	@Test
	void anEmptyListOrNoChanceTurnsItOff() {
		assertFalse(NightRules.parse("chance = 0\n", new ArrayList<>()).enabled());
		assertFalse(NightRules.parse("max_alive = 0\n", new ArrayList<>()).enabled());
		NightRules none = NightRules.parse("creatures =\n", new ArrayList<>());
		assertFalse(none.enabled());
		assertEquals(DeepRules.NONE, none.pick(-1.0, 3));
	}
}
