package dev.skycraft.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Shout key ({@link ShoutCharge}) and the Cast spell key ({@link CastKey}), tick by tick (20 a second). */
class MagicKeysTest {
	private static final MagicRules.Settings S = MagicRules.Settings.DEFAULTS;
	private static final double TICK = 0.05;

	@Test
	void aTapShoutsOneWord() {
		ShoutCharge c = new ShoutCharge();
		assertEquals(0, c.update(true, true, 0.0, 3, S));
		assertTrue(c.holding());
		assertEquals(1, c.update(false, false, TICK, 3, S));
		assertFalse(c.holding());
	}

	@Test
	void aTapBetweenTwoTicks() {
		ShoutCharge c = new ShoutCharge();
		assertEquals(1, c.update(true, false, 0.0, 3, S)); // pressed and up again before the tick saw it held
	}

	@Test
	void heldLongerShoutsMoreWords() {
		ShoutCharge c = new ShoutCharge();
		c.update(true, true, 0.0, 3, S);
		int words = 0;
		double t = 0.0;
		while (t < 0.5) {
			t += TICK;
			words = c.update(false, true, t, 3, S);
			assertEquals(0, words); // still gathering
		}
		assertEquals(2, c.update(false, false, t + TICK, 3, S)); // let go after half a second: two words
	}

	@Test
	void heldToTheLastWordItShoutsAtOnce() {
		ShoutCharge c = new ShoutCharge();
		c.update(true, true, 0.0, 3, S);
		int words = 0;
		double t = 0.0;
		while (words == 0 && t < 2.0) {
			t += TICK;
			words = c.update(false, true, t, 3, S);
		}
		assertEquals(3, words);
		assertTrue(t >= S.threeWordsAfter() && t < S.threeWordsAfter() + 2 * TICK, "at " + t);
		// The key stays down (Minecraft repeats its press): nothing more until it comes up.
		for (int i = 0; i < 40; i++) {
			t += TICK;
			assertEquals(0, c.update(i % 3 == 0, true, t, 3, S));
		}
		assertEquals(0, c.update(false, false, t + TICK, 3, S));
		assertEquals(0, c.update(true, true, t + 2 * TICK, 3, S)); // a fresh press starts again
		assertTrue(c.holding());
	}

	@Test
	void oneWordUnlockedGoesOnThePress() {
		ShoutCharge c = new ShoutCharge();
		assertEquals(1, c.update(true, true, 0.0, 1, S));
		assertEquals(0, c.update(false, true, 0.5, 1, S));
		assertEquals(0, c.update(false, false, 0.6, 1, S));
	}

	@Test
	void twoWordsUnlockedStopAtTwo() {
		ShoutCharge c = new ShoutCharge();
		c.update(true, true, 0.0, 2, S);
		assertEquals(0, c.update(false, true, 0.3, 2, S));
		assertEquals(2, c.update(false, true, 0.4, 2, S));
	}

	@Test
	void noWordUnlockedNoShout() {
		ShoutCharge c = new ShoutCharge();
		assertEquals(0, c.update(true, true, 0.0, 0, S));
		assertEquals(0, c.update(false, false, 0.1, 0, S));
	}

	@Test
	void resetForgetsTheHold() {
		ShoutCharge c = new ShoutCharge();
		c.update(true, true, 0.0, 3, S);
		c.reset();
		assertFalse(c.holding());
		assertEquals(0, c.update(false, false, 0.2, 3, S));
	}

	@Test
	void aFireAndForgetSpellCastsOncePerPress() {
		CastKey k = new CastKey();
		assertEquals(CastKey.CAST, k.update(true, true, 0.0, false, S));
		double t = 0.0;
		for (int i = 0; i < 40; i++) {
			t += TICK;
			assertEquals(CastKey.NONE, k.update(i % 2 == 0, true, t, false, S)); // held, its press repeating
		}
		assertEquals(CastKey.NONE, k.update(false, false, t + TICK, false, S));
		assertEquals(CastKey.CAST, k.update(true, true, t + 2 * TICK, false, S));
	}

	@Test
	void aConcentrationSpellPulsesWhileHeld() {
		CastKey k = new CastKey();
		assertEquals(CastKey.CAST, k.update(true, true, 0.0, true, S));
		assertTrue(k.pulsing());
		int pulses = 0;
		double t = 0.0;
		while (t < 2.0 - 1e-9) {
			t += TICK;
			if (k.update(false, true, t, true, S) == CastKey.PULSE) {
				pulses++;
			}
		}
		assertEquals(4, pulses); // at 0.5, 1.0, 1.5 and 2.0 seconds
		assertEquals(CastKey.NONE, k.update(false, false, t + TICK, true, S));
		assertFalse(k.pulsing());
	}

	@Test
	void pulsesAfterAStallStartOverRatherThanBurst() {
		CastKey k = new CastKey();
		k.update(true, true, 0.0, true, S);
		assertEquals(CastKey.PULSE, k.update(false, true, 5.0, true, S)); // a five second stall
		assertEquals(CastKey.NONE, k.update(false, true, 5.05, true, S));
		assertEquals(CastKey.PULSE, k.update(false, true, 5.5, true, S));
	}

	@Test
	void pulsesOffCastsConcentrationOncePerPress() {
		MagicRules.Settings off = MagicRules.parse("concentration_pulses = off", new java.util.ArrayList<>());
		CastKey k = new CastKey();
		assertEquals(CastKey.CAST, k.update(true, true, 0.0, true, off));
		for (double t = TICK; t < 2.0; t += TICK) {
			assertEquals(CastKey.NONE, k.update(false, true, t, true, off));
		}
	}

	@Test
	void aTappedCastAndReset() {
		CastKey k = new CastKey();
		assertEquals(CastKey.CAST, k.update(true, false, 0.0, true, S));
		assertFalse(k.pulsing());
		assertEquals(CastKey.CAST, k.update(true, true, 0.1, true, S));
		k.reset();
		assertFalse(k.pulsing());
		assertEquals(CastKey.NONE, k.update(false, true, 1.0, true, S));
	}
}
