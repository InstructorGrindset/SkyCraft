package dev.skycraft.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyRulesTest {
	@Test
	void defaultTextParsesToTheDefaults() {
		List<String> problems = new ArrayList<>();
		assertEquals(PartyRules.Settings.DEFAULTS, PartyRules.parse(PartyRules.DEFAULT_TEXT, problems));
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void badLinesKeepTheDefault() {
		List<String> problems = new ArrayList<>();
		PartyRules.Settings s = PartyRules.parse("shout_cooldown = 5 # quick\nguest_range = 2\nguest_actors = many\nfus = ro dah\nnothing\n", problems);
		assertEquals(4, problems.size(), problems.toString());
		assertEquals(5, s.shout());
		assertEquals(PartyRules.Settings.DEFAULTS.guestRange(), s.guestRange());
		assertEquals(PartyRules.Settings.DEFAULTS.guestActors(), s.guestActors());
	}

	@Test
	void dragonsByTheirBitOrTheirName() {
		assertTrue(PartyRules.dragon(Proto.ACTOR_DRAGON, "Bandit"));
		assertTrue(PartyRules.dragon(0, "Blood Dragon"));
		assertTrue(PartyRules.dragon(0, "Mirmulnir"));
		assertTrue(PartyRules.dragon(0, " paarthurnax "));
		assertFalse(PartyRules.dragon(0, "Dragon Priest"));
		assertFalse(PartyRules.dragon(0, "Dragonborn Cultist"));
		assertFalse(PartyRules.dragon(0, "Draugr"));
		assertFalse(PartyRules.dragon(0, null));
	}

	@Test
	void aBucketLetsABurstThroughThenItsRate() {
		PartyRules.Bucket b = PartyRules.Bucket.perMinute(3, 60); // a token a second (20 ticks)
		assertTrue(b.take(0));
		assertTrue(b.take(0));
		assertTrue(b.take(0));
		assertFalse(b.take(0));
		assertFalse(b.take(19)); // 0.95 of a token
		assertTrue(b.take(40)); // two seconds on: two tokens
		assertTrue(b.take(40));
		assertFalse(b.take(40));
		assertEquals(3, b.left(100000)); // never more than the burst
	}

	@Test
	void anHourlyBucketRefillsSlowly() {
		PartyRules.Bucket b = PartyRules.Bucket.perHour(1, 30); // one every two minutes
		assertTrue(b.take(0));
		assertFalse(b.take(1200));
		assertTrue(b.take(2400));
	}

	@Test
	void aClockThatGoesBackDoesntRefill() {
		PartyRules.Bucket b = PartyRules.Bucket.perMinute(1, 60);
		assertTrue(b.take(5000));
		assertFalse(b.take(10)); // a restarted server's ticks start again from 0
		assertTrue(b.take(30));
	}

	@Test
	void anEmptyBucketAllowsNothing() {
		PartyRules.Bucket b = PartyRules.Bucket.perMinute(0, 0);
		assertFalse(b.take(0));
		assertFalse(b.take(1_000_000));
	}
}
