package dev.skycraft.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import org.junit.jupiter.api.Test;

class CreatureRulesTest {
	private static final int PERSON = 0;
	private static final int BANDIT = Proto.ACTOR_HOSTILE;
	private static final int BANDIT_FIGHTING = Proto.ACTOR_HOSTILE | Proto.ACTOR_IN_COMBAT;
	private static final int DRAUGR = Proto.ACTOR_HOSTILE | Proto.ACTOR_UNDEAD;
	private static final int DEAD = Proto.ACTOR_DEAD;

	@Test
	void monstersGoForTheLivingButLeaveTheirOwnKindBe() {
		assertTrue(CreatureRules.monsterTargets(false, PERSON));
		assertTrue(CreatureRules.monsterTargets(true, PERSON));
		assertTrue(CreatureRules.monsterTargets(false, BANDIT));
		// A spider goes for a draugr; a zombie leaves it be.
		assertTrue(CreatureRules.monsterTargets(false, DRAUGR));
		assertFalse(CreatureRules.monsterTargets(true, DRAUGR));
		assertFalse(CreatureRules.monsterTargets(false, DEAD));
		assertFalse(CreatureRules.monsterTargets(false, DEAD | Proto.ACTOR_HOSTILE));
	}

	@Test
	void wolvesGoOnlyForEnemiesInTheFight() {
		assertTrue(CreatureRules.wolfTargets(BANDIT_FIGHTING));
		assertTrue(CreatureRules.wolfTargets(BANDIT_FIGHTING | Proto.ACTOR_UNDEAD));
		assertFalse(CreatureRules.wolfTargets(BANDIT), "hostile but not fighting: left be, as a Minecraft wolf leaves mobs that haven't fought its owner");
		assertFalse(CreatureRules.wolfTargets(Proto.ACTOR_IN_COMBAT), "fighting someone, but not the player's enemy");
		assertFalse(CreatureRules.wolfTargets(PERSON));
		assertFalse(CreatureRules.wolfTargets(BANDIT_FIGHTING | Proto.ACTOR_DEAD));
	}

	@Test
	void monstersSwitchWhenIdleOrWhenAnActorIsInTheirFace() {
		assertTrue(CreatureRules.monsterSwitches(CreatureRules.Current.NOTHING, 10.0, -1.0));
		assertTrue(CreatureRules.monsterSwitches(CreatureRules.Current.NOTHING, CreatureRules.MONSTER_RANGE, -1.0));
		assertFalse(CreatureRules.monsterSwitches(CreatureRules.Current.NOTHING, CreatureRules.MONSTER_RANGE + 0.1, -1.0));
		// After the player: only for an actor right there, with the player clearly further.
		assertTrue(CreatureRules.monsterSwitches(CreatureRules.Current.PLAYER, 2.0, 20.0));
		assertFalse(CreatureRules.monsterSwitches(CreatureRules.Current.PLAYER, 2.0, 5.0));
		assertFalse(CreatureRules.monsterSwitches(CreatureRules.Current.PLAYER, 8.0, 30.0));
		// Already after an actor or another creature: keeps it.
		assertFalse(CreatureRules.monsterSwitches(CreatureRules.Current.OTHER, 1.0, -1.0));
		assertFalse(CreatureRules.monsterSwitches(CreatureRules.Current.NOTHING, Double.NaN, -1.0));
	}

	@Test
	void wolvesOnlyPickAFightWhenFree() {
		assertTrue(CreatureRules.wolfSwitches(CreatureRules.Current.NOTHING, 5.0));
		assertFalse(CreatureRules.wolfSwitches(CreatureRules.Current.NOTHING, CreatureRules.WOLF_RANGE + 1.0));
		assertFalse(CreatureRules.wolfSwitches(CreatureRules.Current.PLAYER, 5.0));
		assertFalse(CreatureRules.wolfSwitches(CreatureRules.Current.OTHER, 5.0));
	}

	@Test
	void steersOnlyWhenPathFindingGaveUpAndOutOfReach() {
		assertTrue(CreatureRules.steer(5.0, 1.6, false, 12.0, false));
		assertFalse(CreatureRules.steer(1.5, 1.6, false, 12.0, false));
		assertFalse(CreatureRules.steer(5.0, 1.6, false, 12.0, true), "Minecraft's own navigation has a path: left to it");
		// Archers keep their distance.
		assertFalse(CreatureRules.steer(8.0, 1.6, true, 12.0, false));
		assertTrue(CreatureRules.steer(14.0, 1.6, true, 12.0, false));
	}

	@Test
	void daylightIsSkyrimsDay() {
		assertFalse(CreatureRules.daylight(5.9F));
		assertTrue(CreatureRules.daylight(6.0F));
		assertTrue(CreatureRules.daylight(12.0F));
		assertTrue(CreatureRules.daylight(19.4F));
		assertFalse(CreatureRules.daylight(19.5F));
		assertFalse(CreatureRules.daylight(0.0F));
	}

	@Test
	void undeadBurnInTheOpenSunAsZombiesDo() {
		// A zombie's odds: 4% a tick in full daylight, so a bit over half within a second.
		assertEquals(0.558, CreatureRules.SUN_BURN_PER_SECOND, 0.001);
		assertTrue(CreatureRules.sunBurns(DRAUGR, true, 12.0F, true, false, false, 0.0));
		assertTrue(CreatureRules.sunBurns(DRAUGR, true, 12.0F, true, false, false, 0.55));
		assertFalse(CreatureRules.sunBurns(DRAUGR, true, 12.0F, true, false, false, 0.56));
		assertFalse(CreatureRules.sunBurns(BANDIT, true, 12.0F, true, false, false, 0.0), "the living don't burn");
		assertFalse(CreatureRules.sunBurns(DRAUGR, false, 12.0F, true, false, false, 0.0), "indoors");
		assertFalse(CreatureRules.sunBurns(DRAUGR, true, 23.0F, true, false, false, 0.0), "at night");
		assertFalse(CreatureRules.sunBurns(DRAUGR, true, 12.0F, false, false, false, 0.0), "in shade");
		assertFalse(CreatureRules.sunBurns(DRAUGR, true, 12.0F, true, true, false, 0.0), "wet");
		assertFalse(CreatureRules.sunBurns(DRAUGR, true, 12.0F, true, false, true, 0.0), "burning already");
		assertFalse(CreatureRules.sunBurns(DRAUGR | Proto.ACTOR_ESSENTIAL, true, 12.0F, true, false, false, 0.0), "a quest's vampire, a follower");
		assertFalse(CreatureRules.sunBurns(DRAUGR | Proto.ACTOR_DEAD, true, 12.0F, true, false, false, 0.0));
	}

	@Test
	void packsTheMobEventFlags() {
		int hit = CreatureRules.hitFields(Proto.HIT_FIRE | Proto.HIT_PROJECTILE, Proto.MOB_BY_SUN);
		assertEquals(Proto.MOB_HIT, hit & 0xFF);
		assertEquals(Proto.HIT_FIRE | Proto.HIT_PROJECTILE, hit >>> Proto.MOB_HIT_FLAGS_SHIFT & 0xFF);
		assertEquals(Proto.MOB_BY_SUN, hit >>> Proto.MOB_ATTACKER_SHIFT & 0xF);
		int wolf = CreatureRules.hitFields(0, Proto.MOB_BY_WOLF);
		assertEquals(Proto.MOB_BY_WOLF, wolf >>> Proto.MOB_ATTACKER_SHIFT & 0xF);
		int near = CreatureRules.nearFields(Proto.MOB_UNDEAD | Proto.MOB_PLAYERS_WOLF);
		assertEquals(Proto.MOB_NEAR, near & 0xFF);
		assertEquals(Proto.MOB_UNDEAD | Proto.MOB_PLAYERS_WOLF, near >>> Proto.MOB_TRAITS_SHIFT & 0xFF);
	}

	@Test
	void swingDamageIsSane() {
		assertEquals(2.4F, CreatureRules.swingDamage(240), 1e-6F);
		assertEquals(0.0F, CreatureRules.swingDamage(-500), 1e-6F);
		assertEquals(50.0F, CreatureRules.swingDamage(1_000_000), 1e-6F);
	}

	@Test
	void creatureBitsDontCollideWithTheOthers() {
		int bits = Proto.ACTOR_LIGHT_SHY | Proto.ACTOR_UNDEAD;
		assertEquals(0, bits & (Proto.ACTOR_HOSTILE | Proto.ACTOR_DEAD | Proto.ACTOR_ESSENTIAL | Proto.ACTOR_IN_COMBAT | Proto.ACTOR_SPAWNED
			| Proto.ACTOR_COMPANION | Proto.ACTOR_COMPANION_WAITING | Proto.ACTOR_KIND_MASK));
		assertTrue(CreatureRules.lightShy(Proto.ACTOR_LIGHT_SHY));
		assertFalse(CreatureRules.lightShy(Proto.ACTOR_UNDEAD));
	}
}
