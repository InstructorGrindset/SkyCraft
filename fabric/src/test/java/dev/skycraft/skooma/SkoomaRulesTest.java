package dev.skycraft.skooma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.skooma.SkoomaRules.Dose;
import dev.skycraft.skooma.SkoomaRules.Habit;
import dev.skycraft.skooma.SkoomaRules.Settings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkoomaRulesTest {
	private static final Settings S = Settings.DEFAULTS;
	private static final long DAY = SkoomaRules.DAY;
	/** A roll that never hooks, and one that always does. */
	private static final double NEVER_HOOKS = 0.999999, ALWAYS_HOOKS = 0.0;

	private static Habit addictedAt(long t) {
		Dose d = Habit.CLEAN.dose(t, 0, ALWAYS_HOOKS, S);
		assertTrue(d.hooked());
		return d.habit();
	}

	@Test
	void defaultTextParsesToTheDefaults() {
		List<String> problems = new ArrayList<>();
		assertEquals(Settings.DEFAULTS, SkoomaRules.parse(SkoomaRules.DEFAULT_TEXT, problems));
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void badLinesKeepTheDefault() {
		List<String> problems = new ArrayList<>();
		Settings s = SkoomaRules.parse("chance = 2\nwithdrawal_after = 1000 # comment\nwithdrawal_step = 0\nthuum = 3\nnothing\ncomedown = -5\n", problems);
		assertEquals(5, problems.size(), problems.toString());
		assertEquals(S.chance(), s.chance());
		assertEquals(1000, s.withdrawalAfter());
		assertEquals(S.withdrawalStep(), s.withdrawalStep());
		assertEquals(S.comedown(), s.comedown());
	}

	@Test
	void toleranceRisesWithEachDoseAndHalvesOverTime() {
		Habit h = Habit.CLEAN.dose(1000, 0, NEVER_HOOKS, S).habit();
		assertEquals(1.0, h.toleranceAt(1000, S), 1e-9);
		assertEquals(0.5, h.toleranceAt(1000 + S.toleranceHalfLife(), S), 1e-9);
		h = h.dose(1000 + S.toleranceHalfLife(), 0, NEVER_HOOKS, S).habit();
		assertEquals(1.5, h.toleranceAt(1000 + S.toleranceHalfLife(), S), 1e-9);
		assertEquals(2, h.doses());
		assertEquals(0.0, Habit.CLEAN.toleranceAt(5000, S));
	}

	@Test
	void theChanceGrowsWithToleranceAndStrength() {
		assertEquals(0.05, Habit.CLEAN.hookChance(0, 0, S), 1e-9);
		assertEquals(0.20, Habit.CLEAN.hookChance(0, 1, S), 1e-9); // Redwater
		Habit h = Habit.CLEAN.dose(0, 0, NEVER_HOOKS, S).habit();
		assertEquals(0.20, h.hookChance(0, 0, S), 1e-9); // straight after another
		assertEquals(0.35, h.hookChance(0, 1, S), 1e-9);
		Habit sure = new Habit(9, 0, 20.0, false, 0, 0);
		assertEquals(1.0, sure.hookChance(0, 3, S), 1e-9);
	}

	@Test
	void aFewDosesInADayAreRiskyAndAnOccasionalOneIsNot() {
		// Three doses a third of a day apart: about a one in three chance of being hooked by the end.
		Habit h = Habit.CLEAN;
		double clean = 1.0;
		for (int i = 0; i < 3; i++) {
			long t = i * 8000L;
			clean *= 1 - h.hookChance(t, 0, S);
			h = h.dose(t, 0, NEVER_HOOKS, S).habit();
		}
		assertTrue(1 - clean > 0.3, "three in a day: " + (1 - clean));
		// One every two days stays near the base chance.
		h = Habit.CLEAN;
		for (int i = 0; i < 5; i++) {
			long t = i * 2 * DAY;
			assertTrue(h.hookChance(t, 0, S) < 0.07, "dose " + i + ": " + h.hookChance(t, 0, S));
			h = h.dose(t, 0, NEVER_HOOKS, S).habit();
		}
	}

	@Test
	void theRollDecides() {
		Dose missed = Habit.CLEAN.dose(0, 0, 0.06, S);
		assertFalse(missed.hooked());
		assertFalse(missed.habit().addicted());
		Dose hooked = Habit.CLEAN.dose(0, 0, 0.04, S);
		assertTrue(hooked.hooked());
		assertTrue(hooked.habit().addicted());
		assertFalse(hooked.relieved());
		// Already addicted: no new hook, whatever the roll.
		assertFalse(hooked.habit().dose(SkoomaRules.SAME_DOSE, 0, ALWAYS_HOOKS, S).hooked());
	}

	@Test
	void skoomaWithinAMinuteIsTheSameDose() {
		Habit h = Habit.CLEAN.dose(1000, 0, NEVER_HOOKS, S).habit();
		Dose again = h.dose(1000 + SkoomaRules.SAME_DOSE - 1, 1, ALWAYS_HOOKS, S);
		assertFalse(again.counted());
		assertFalse(again.hooked());
		assertEquals(h, again.habit());
		Dose next = h.dose(1000 + SkoomaRules.SAME_DOSE, 0, NEVER_HOOKS, S);
		assertTrue(next.counted());
		assertEquals(2, next.habit().doses());
	}

	@Test
	void withdrawalStartsAfterADayAndWorsensDaily() {
		long t0 = 5000;
		Habit h = addictedAt(t0);
		assertEquals(0, h.withdrawal(t0 + DAY - 1, S));
		assertEquals(1, h.withdrawal(t0 + DAY, S));
		assertEquals(2, h.withdrawal(t0 + 2 * DAY, S));
		assertEquals(3, h.withdrawal(t0 + 3 * DAY, S));
		assertEquals(3, h.withdrawal(t0 + 30 * DAY, S));
		assertEquals(0, Habit.CLEAN.dose(t0, 0, NEVER_HOOKS, S).habit().withdrawal(t0 + 5 * DAY, S), "not addicted, no withdrawal");
	}

	@Test
	void aDoseEndsWithdrawalAndDeepensTheAddiction() {
		Habit h = addictedAt(0).endure(4000);
		Dose relapse = h.dose(2 * DAY, 0, NEVER_HOOKS, S);
		assertTrue(relapse.relieved());
		assertFalse(relapse.hooked());
		Habit after = relapse.habit();
		assertTrue(after.addicted());
		assertEquals(1, after.relapses());
		assertEquals(0, after.endured());
		assertEquals(0, after.withdrawal(2 * DAY + 100, S));
		assertEquals(S.recoveryAfter() + S.recoveryPerRelapse(), after.recoveryTime(S));
		// Between withdrawals a dose isn't a relief, but still a relapse; four at most count.
		Dose maintain = after.dose(2 * DAY + SkoomaRules.SAME_DOSE, 0, NEVER_HOOKS, S);
		assertFalse(maintain.relieved());
		assertEquals(2, maintain.habit().relapses());
		Habit deep = maintain.habit();
		for (int i = 0; i < 10; i++) {
			deep = deep.dose(3 * DAY + i * SkoomaRules.SAME_DOSE, 0, NEVER_HOOKS, S).habit();
		}
		assertEquals(SkoomaRules.MAX_RELAPSES, deep.relapses());
	}

	@Test
	void stayingCleanThroughTheWithdrawalCures() {
		Habit h = addictedAt(0);
		// Played through, a second at a time: cured three days after the dose.
		long now = 0;
		while (!h.recovers(now, S)) {
			now += 20;
			if (h.withdrawal(now, S) > 0) {
				h = h.endure(20);
			}
			assertTrue(now <= 10 * DAY, "never cured");
		}
		assertEquals(S.recoveryAfter(), now);
		Habit cured = h.cured();
		assertFalse(cured.addicted());
		assertEquals(0, cured.withdrawal(now + DAY, S));
		assertEquals(1, cured.doses());
	}

	@Test
	void timeAwayDoesntSkipTheWithdrawal() {
		Habit h = addictedAt(0);
		long back = 5 * DAY; // the world ran on while the player was away
		assertFalse(h.recovers(back, S));
		assertEquals(3, h.withdrawal(back, S));
		assertFalse(h.endure(S.recoveryEndured() - 20).recovers(back, S));
		assertTrue(h.endure(S.recoveryEndured()).recovers(back, S));
	}

	@Test
	void relapsesPutTheCureOff() {
		Habit h = addictedAt(0);
		for (int i = 1; i <= 2; i++) {
			h = h.dose(i * 2000L, 0, NEVER_HOOKS, S).habit();
		}
		long last = 4000;
		Habit endured = h.endure(10 * DAY);
		assertFalse(endured.recovers(last + S.recoveryAfter(), S));
		assertTrue(endured.recovers(last + S.recoveryAfter() + 2 * S.recoveryPerRelapse(), S));
	}

	@Test
	void aCureThatComesBeforeAnyWithdrawalNeedsNone() {
		// Settings where withdrawal would start after the cure: nothing to live through, so nobody is stuck.
		Settings s = new Settings(0.05, 0.15, 0.15, 12000, 5 * DAY, DAY, 3 * DAY, 0, DAY, 3600, 600);
		Habit h = addictedAt(0);
		assertEquals(0, h.enduranceNeeded(s));
		assertTrue(h.recovers(3 * DAY, s));
	}

	@Test
	void stealthScalesWithLevel() {
		assertEquals(1.0, SkoomaRules.stealthScale(0));
		assertEquals(0.6, SkoomaRules.stealthScale(1), 1e-9);
		assertEquals(0.45, SkoomaRules.stealthScale(2), 1e-9);
		assertEquals(0.2, SkoomaRules.stealthScale(50), 1e-9);
	}

	@Test
	void cravingsByLevel() {
		for (List<String> lines : SkoomaRules.CRAVINGS) {
			assertEquals(SkoomaRules.CRAVING_LINES, lines.size());
		}
		assertEquals(SkoomaRules.MAX_WITHDRAWAL, SkoomaRules.CRAVINGS.size());
		assertEquals("You crave Skooma.", SkoomaRules.craving(1, 0));
		assertEquals("Your hands won't stop shaking.", SkoomaRules.craving(2, 0));
		assertEquals(SkoomaRules.craving(3, 1), SkoomaRules.craving(3, 1 + SkoomaRules.CRAVINGS.get(2).size()));
		assertEquals(SkoomaRules.craving(3, 0), SkoomaRules.craving(9, 0));
	}

	@Test
	void describesTheHabit() {
		assertEquals("Never took Skooma.", Habit.CLEAN.describe(0, S));
		String clean = Habit.CLEAN.dose(0, 0, NEVER_HOOKS, S).habit().describe(DAY / 2, S);
		assertTrue(clean.startsWith("1 dose, the last 0.50 days ago"), clean);
		assertTrue(clean.contains("Not addicted"), clean);
		String addicted = addictedAt(0).endure(1000).describe(2 * DAY, S);
		assertTrue(addicted.contains("in withdrawal II"), addicted);
		assertTrue(addicted.contains("cured in 1.00 days"), addicted);
	}
}
