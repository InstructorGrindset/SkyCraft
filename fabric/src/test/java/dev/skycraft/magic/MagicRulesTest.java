package dev.skycraft.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MagicRulesTest {
	private static final MagicRules.Settings S = MagicRules.Settings.DEFAULTS;

	@Test
	void holdTimeGivesWords() {
		assertEquals(1, MagicRules.words(0.0, 3, S)); // a tap
		assertEquals(1, MagicRules.words(0.34, 3, S));
		assertEquals(2, MagicRules.words(0.35, 3, S));
		assertEquals(2, MagicRules.words(0.79, 3, S));
		assertEquals(3, MagicRules.words(0.8, 3, S));
		assertEquals(3, MagicRules.words(5.0, 3, S));
	}

	@Test
	void neverMoreWordsThanUnlocked() {
		assertEquals(1, MagicRules.words(2.0, 1, S));
		assertEquals(2, MagicRules.words(2.0, 2, S));
		assertEquals(0, MagicRules.words(2.0, 0, S)); // no word of it yet: nothing
		assertEquals(3, MagicRules.words(2.0, 7, S)); // a shout has three words at most
	}

	@Test
	void fullyChargedAtTheLastWordUnlocked() {
		assertTrue(MagicRules.fullyCharged(0.0, 1, S)); // one word: it goes at once
		assertFalse(MagicRules.fullyCharged(0.2, 2, S));
		assertTrue(MagicRules.fullyCharged(0.35, 2, S));
		assertFalse(MagicRules.fullyCharged(0.5, 3, S));
		assertTrue(MagicRules.fullyCharged(0.8, 3, S));
	}

	@Test
	void theDefaultFileIsTheDefaults() {
		List<String> problems = new ArrayList<>();
		MagicRules.Settings s = MagicRules.parse(MagicRules.DEFAULT_TEXT, problems);
		assertTrue(problems.isEmpty(), () -> "problems: " + problems);
		assertEquals(S.twoWordsAfter(), s.twoWordsAfter(), 0.0);
		assertEquals(S.threeWordsAfter(), s.threeWordsAfter(), 0.0);
		assertEquals(S.coneDegrees(), s.coneDegrees(), 0.0);
		for (int w = 1; w <= 3; w++) {
			assertEquals(MagicRules.forWords(S.forceReach(), w), MagicRules.forWords(s.forceReach(), w), 0.0);
			assertEquals(MagicRules.forWords(S.breathReach(), w), MagicRules.forWords(s.breathReach(), w), 0.0);
			assertEquals(MagicRules.forWords(S.sprintBlocks(), w), MagicRules.forWords(s.sprintBlocks(), w), 0.0);
			assertEquals(MagicRules.forWords(S.etherealSeconds(), w), MagicRules.forWords(s.etherealSeconds(), w), 0.0);
			assertEquals(MagicRules.forWords(S.slowTimeSeconds(), w), MagicRules.forWords(s.slowTimeSeconds(), w), 0.0);
		}
		assertEquals(S.forcePushPerWord(), s.forcePushPerWord(), 0.0);
		assertEquals(S.forceDamagePerWord(), s.forceDamagePerWord(), 0.0);
		assertEquals(S.forceLift(), s.forceLift(), 0.0);
		assertEquals(S.touchReach(), s.touchReach(), 0.0);
		assertEquals(S.fireSplash(), s.fireSplash(), 0.0);
		assertEquals(S.breathDamagePerWord(), s.breathDamagePerWord(), 0.0);
		assertEquals(S.breathSecondsPerWord(), s.breathSecondsPerWord(), 0.0);
		assertEquals(S.sprintSpeed(), s.sprintSpeed(), 0.0);
		assertEquals(S.slowTimeReach(), s.slowTimeReach(), 0.0);
		assertEquals(S.slowTimeLevel(), s.slowTimeLevel());
		assertEquals(S.clearSkiesSeconds(), s.clearSkiesSeconds());
		assertEquals(S.spellReach(), s.spellReach(), 0.0);
		assertEquals(S.damagePerMagicka(), s.damagePerMagicka(), 0.0);
		assertEquals(S.maxSpellDamage(), s.maxSpellDamage(), 0.0);
		assertEquals(S.frostDamage(), s.frostDamage(), 0.0);
		assertEquals(S.frostSeconds(), s.frostSeconds(), 0.0);
		assertEquals(S.shockDamage(), s.shockDamage(), 0.0);
		assertEquals(S.fireSeconds(), s.fireSeconds(), 0.0);
		assertEquals(S.fireLightsBlocks(), s.fireLightsBlocks());
		assertEquals(S.healPerMagicka(), s.healPerMagicka(), 0.0);
		assertEquals(S.wardSeconds(), s.wardSeconds(), 0.0);
		assertEquals(S.wardLevel(), s.wardLevel());
		assertEquals(S.concentrationPulses(), s.concentrationPulses());
		assertEquals(S.pulseSeconds(), s.pulseSeconds(), 0.0);
		assertEquals(S.hudSeconds(), s.hudSeconds(), 0.0);
	}

	@Test
	void editsAndMistakes() {
		List<String> problems = new ArrayList<>();
		MagicRules.Settings s = MagicRules.parse("""
			two_words_after = 0.5   # slower
			force_reach = 3, 4, 5
			breath_reach = 4 5
			fire_lights_blocks = off
			slow_time_level = 9
			whatever = 1
			no equals here
			""", problems);
		assertEquals(0.5, s.twoWordsAfter(), 0.0);
		assertEquals(4.0, MagicRules.forWords(s.forceReach(), 2), 0.0);
		assertEquals(6.0, MagicRules.forWords(s.breathReach(), 2), 0.0); // two numbers aren't three: the default kept
		assertFalse(s.fireLightsBlocks());
		assertEquals(S.slowTimeLevel(), s.slowTimeLevel()); // past Slowness VI: the default kept
		assertEquals(4, problems.size(), () -> "problems: " + problems);
	}

	@Test
	void threeWordsNeverComeBeforeTwo() {
		List<String> problems = new ArrayList<>();
		MagicRules.Settings s = MagicRules.parse("two_words_after = 1.0\nthree_words_after = 0.5\n", problems);
		assertEquals(1, problems.size());
		assertEquals(1.0, s.threeWordsAfter(), 0.0);
		assertEquals(1, MagicRules.words(0.9, 3, s));
		assertEquals(3, MagicRules.words(1.0, 3, s)); // both words come at the same moment
	}

	@Test
	void shoutsOnMinecraftsSide() {
		assertTrue(MagicRules.shoutOnServer(Proto.SHOUT_KIND_FORCE));
		assertTrue(MagicRules.shoutOnServer(Proto.SHOUT_KIND_CLEAR_SKIES));
		assertFalse(MagicRules.shoutOnServer(Proto.SHOUT_KIND_SPRINT)); // the client's own dash
		assertFalse(MagicRules.shoutOnServer(Proto.SHOUT_KIND_OTHER));
		assertEquals(10.0, MagicRules.shoutReach(Proto.SHOUT_KIND_FORCE, 3, S), 0.0);
		assertEquals(5.0, MagicRules.shoutReach(Proto.SHOUT_KIND_FROST_BREATH, 1, S), 0.0);
		assertEquals(0.0, MagicRules.shoutReach(Proto.SHOUT_KIND_SLOW_TIME, 2, S), 0.0);
		assertEquals(2.7, MagicRules.forcePush(3, S), 1e-9); // more words push harder
		assertTrue(MagicRules.forcePush(1, S) < MagicRules.forcePush(2, S));
		assertEquals(4.0F, MagicRules.shoutDamage(Proto.SHOUT_KIND_FIRE_BREATH, 2, S), 1e-6F);
		assertEquals(0.0F, MagicRules.shoutDamage(Proto.SHOUT_KIND_ETHEREAL, 3, S), 0.0F);
		assertEquals(180, MagicRules.breathTicks(3, S)); // 3 seconds a word
		assertEquals(160, MagicRules.etherealTicks(1, S));
		assertEquals(360, MagicRules.etherealTicks(3, S));
		assertEquals(240, MagicRules.slowTimeTicks(2, S));
		assertEquals(4, MagicRules.sprintTicks(1, S)); // 5 blocks at 1.4 a tick
		assertEquals(10, MagicRules.sprintTicks(3, S));
		assertEquals(10.0, MagicRules.forWords(S.forceReach(), 9), 0.0); // past three words: the third's
	}

	@Test
	void theDashFollowsTheLook() {
		double[] south = MagicRules.sprintVelocity(0.0F, S);
		assertEquals(0.0, south[0], 1e-9);
		assertEquals(S.sprintSpeed(), south[1], 1e-9);
		double[] west = MagicRules.sprintVelocity(90.0F, S);
		assertEquals(-S.sprintSpeed(), west[0], 1e-9);
		assertEquals(0.0, west[1], 1e-9);
	}

	@Test
	void theCone() {
		assertTrue(MagicRules.inCone(0, 0, 5, 0, 0, 1, 35)); // straight ahead
		assertTrue(MagicRules.inCone(2, 0, 5, 0, 0, 1, 35)); // 22 degrees off
		assertFalse(MagicRules.inCone(5, 0, 5, 0, 0, 1, 35)); // 45 degrees off
		assertFalse(MagicRules.inCone(0, 0, -5, 0, 0, 1, 35)); // behind
		assertTrue(MagicRules.inCone(0, 0, 0, 0, 0, 1, 35)); // at the eyes
	}

	@Test
	void spellKindsOnMinecraftsSide() {
		assertEquals(MagicRules.ACTION_IGNITE, MagicRules.spellAction(Proto.SPELL_KIND_FIRE));
		assertEquals(MagicRules.ACTION_CHILL, MagicRules.spellAction(Proto.SPELL_KIND_FROST));
		assertEquals(MagicRules.ACTION_SHOCK, MagicRules.spellAction(Proto.SPELL_KIND_SHOCK));
		assertEquals(MagicRules.ACTION_HEAL, MagicRules.spellAction(Proto.SPELL_KIND_HEAL));
		assertEquals(MagicRules.ACTION_WARD, MagicRules.spellAction(Proto.SPELL_KIND_WARD));
		for (int kind : new int[] { Proto.SPELL_KIND_OTHER, Proto.SPELL_KIND_SUMMON, Proto.SPELL_KIND_ILLUSION, Proto.SPELL_KIND_TELEKINESIS, 99 }) {
			assertEquals(MagicRules.ACTION_NONE, MagicRules.spellAction(kind), "kind " + kind);
		}
	}

	@Test
	void spellDamageGoesByMagicka() {
		assertEquals(4.8F, MagicRules.spellDamage(Proto.SPELL_KIND_FIRE, 40.0F, S), 1e-5F); // a Firebolt
		assertEquals(4.8F * 0.8F, MagicRules.spellDamage(Proto.SPELL_KIND_FROST, 40.0F, S), 1e-5F);
		assertEquals(4.8F * 1.3F, MagicRules.spellDamage(Proto.SPELL_KIND_SHOCK, 40.0F, S), 1e-5F);
		assertEquals(20.0F, MagicRules.spellDamage(Proto.SPELL_KIND_FIRE, 5000.0F, S), 0.0F); // never past max_spell_damage
		assertEquals(0.0F, MagicRules.spellDamage(Proto.SPELL_KIND_HEAL, 40.0F, S), 0.0F);
		assertEquals(0.0F, MagicRules.spellDamage(Proto.SPELL_KIND_FIRE, -3.0F, S), 0.0F);
		assertEquals(6.0F, MagicRules.spellHeal(50.0F, S), 1e-5F);
		assertEquals(20.0F, MagicRules.spellHeal(10000.0F, S), 0.0F); // a full bar at most
	}

	@Test
	void whatTheHudSaysWhenSkyrimSaysNo() {
		assertNull(MagicRules.shoutAnswer(Proto.SHOUT_DONE, "Unrelenting Force", 0));
		assertEquals("Your voice needs 13 more seconds", MagicRules.shoutAnswer(Proto.SHOUT_RECOVERING, "Unrelenting Force", 12.2F));
		assertEquals("Your voice needs 1 more seconds", MagicRules.shoutAnswer(Proto.SHOUT_RECOVERING, "Unrelenting Force", 0.01F));
		String unknown = MagicRules.shoutAnswer(Proto.SHOUT_UNKNOWN, "Fire Breath", 0);
		assertNotNull(unknown);
		assertTrue(unknown.startsWith("Fire Breath"));
		assertNotNull(MagicRules.shoutAnswer(Proto.SHOUT_CANT_NOW, "", 0));
		assertNull(MagicRules.castAnswer(Proto.CAST_DONE, "Flames", 0));
		assertEquals("Not enough magicka for Firebolt (41)", MagicRules.castAnswer(Proto.CAST_NO_MAGICKA, "Firebolt", 40.6F));
		assertEquals("Not enough magicka", MagicRules.castAnswer(Proto.CAST_NO_MAGICKA, "Firebolt", 0.0F));
		assertNotNull(MagicRules.castAnswer(Proto.CAST_UNKNOWN, "", 0));
		assertEquals("Destruction", MagicRules.schoolName(Proto.SCHOOL_DESTRUCTION));
		assertEquals("no school", MagicRules.schoolName(0));
	}

	@Test
	void aGuestsRate() {
		MagicRules.Rate rate = new MagicRules.Rate(2, 1.0);
		assertTrue(rate.take(0.0));
		assertTrue(rate.take(0.1));
		assertFalse(rate.take(0.5)); // a third within the second
		assertTrue(rate.take(1.05)); // the first has left the window
		assertFalse(rate.take(1.06));
		assertTrue(rate.take(2.2));
	}

	@Test
	void ticks() {
		assertEquals(20, MagicRules.ticks(1.0));
		assertEquals(0, MagicRules.ticks(-4.0));
		assertEquals(10, MagicRules.ticks(0.5));
	}
}
