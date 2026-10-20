package dev.skycraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CombatRulesTest {
	@Test
	void aPowerAttackIsAFullyChargedSprintOrCriticalHit() {
		assertTrue(CombatRules.powerAttack(1.0F, true, false));
		assertTrue(CombatRules.powerAttack(1.0F, false, true));
		assertTrue(CombatRules.powerAttack(0.95F, true, true));
		// Fully charged but neither sprinting nor critical: an ordinary hit.
		assertFalse(CombatRules.powerAttack(1.0F, false, false));
		// Sprinting into a half charged swing: not charged enough (Minecraft doesn't give it sprint knockback either).
		assertFalse(CombatRules.powerAttack(0.5F, true, false));
		// Minecraft's own threshold: over 0.9.
		assertFalse(CombatRules.powerAttack(CombatRules.FULL_STRENGTH, true, true));
		assertTrue(CombatRules.powerAttack(Math.nextUp(CombatRules.FULL_STRENGTH), true, false));
	}

	@Test
	void hitFlagsFollowTheSwitches() {
		// A charged sprint swing while crouching, both switches on.
		int both = CombatRules.hitFlags(true, 1.0F, true, false, true, true, true);
		assertEquals(Proto.HIT_POWER_ATTACK | Proto.HIT_CROUCHING, both);
		// Each switch off leaves its bit out.
		assertEquals(Proto.HIT_CROUCHING, CombatRules.hitFlags(true, 1.0F, true, false, true, false, true));
		assertEquals(Proto.HIT_POWER_ATTACK, CombatRules.hitFlags(true, 1.0F, true, false, true, true, false));
		// Not crouching: no crouching bit.
		assertEquals(Proto.HIT_POWER_ATTACK, CombatRules.hitFlags(true, 1.0F, false, true, false, true, true));
	}

	@Test
	void onlyTheirOwnSwingIsAPowerAttack() {
		// An arrow, a thrown trident or a sweep that landed with no swing of the player's on this actor: never a power attack.
		assertEquals(0, CombatRules.hitFlags(false, 1.0F, true, true, false, true, true));
		// A swing seen but its strength unknown (none): no power attack.
		assertEquals(0, CombatRules.hitFlags(true, 0.0F, true, true, false, true, true));
		// A crouched arrow is still crouched: a ranged sneak attack.
		assertEquals(Proto.HIT_CROUCHING, CombatRules.hitFlags(false, 0.0F, false, false, true, true, true));
	}

	@Test
	void lightLevelsStayInMinecraftsRange() {
		assertEquals(0, CombatRules.lightLevel(-4));
		assertEquals(0, CombatRules.lightLevel(0));
		assertEquals(7, CombatRules.lightLevel(7));
		assertEquals(15, CombatRules.lightLevel(15));
		assertEquals(15, CombatRules.lightLevel(99));
	}

	@Test
	void theFourSwitchesHaveTheirSkyrimBits() {
		assertEquals(Proto.FEATURE_WALLS_HIDE_YOU, Features.Feature.WALLS_HIDE_YOU.skyrimBit);
		assertEquals(Proto.FEATURE_SNEAK_ATTACKS, Features.Feature.SNEAK_ATTACKS.skyrimBit);
		assertEquals(Proto.FEATURE_POWER_ATTACKS, Features.Feature.POWER_ATTACKS.skyrimBit);
		assertEquals(Proto.FEATURE_LIGHT_STEALTH, Features.Feature.LIGHT_STEALTH.skyrimBit);
		assertEquals(0xF, Proto.FEATURE_WALLS_HIDE_YOU | Proto.FEATURE_SNEAK_ATTACKS | Proto.FEATURE_POWER_ATTACKS | Proto.FEATURE_LIGHT_STEALTH);
		assertTrue(Features.Feature.WALLS_HIDE_YOU.defaultOn && Features.Feature.LIGHT_STEALTH.defaultOn);
	}

	/** Proto.java and protocol/skycraft_protocol.h agree on everything combat and stealth send. */
	@Test
	void matchesTheProtocolHeader() throws IOException {
		Path header = null;
		for (Path dir = Path.of("").toAbsolutePath(); dir != null && header == null; dir = dir.getParent()) {
			Path candidate = dir.resolve("protocol/skycraft_protocol.h");
			if (Files.exists(candidate)) {
				header = candidate;
			}
		}
		assertNotNull(header, "protocol/skycraft_protocol.h not found above " + Path.of("").toAbsolutePath());
		String text = Files.readString(header);
		String[] expected = {
			"kHitPowerAttack = 1u << " + Integer.numberOfTrailingZeros(Proto.HIT_POWER_ATTACK),
			"kHitCrouching = 1u << " + Integer.numberOfTrailingZeros(Proto.HIT_CROUCHING),
			"kFeatWallsHideYou = 1u << " + Integer.numberOfTrailingZeros(Proto.FEATURE_WALLS_HIDE_YOU),
			"kFeatSneakAttacks = 1u << " + Integer.numberOfTrailingZeros(Proto.FEATURE_SNEAK_ATTACKS),
			"kFeatPowerAttacks = 1u << " + Integer.numberOfTrailingZeros(Proto.FEATURE_POWER_ATTACKS),
			"kFeatLightStealth = 1u << " + Integer.numberOfTrailingZeros(Proto.FEATURE_LIGHT_STEALTH),
			"kEyeLightValid = 1u << " + Integer.numberOfTrailingZeros(Proto.EYE_LIGHT_VALID),
			"eyeBlockLight;  // 0x" + Long.toHexString(Proto.MS_EYE_BLOCK_LIGHT).toUpperCase(),
			"eyeSkyLight;    // 0x" + Long.toHexString(Proto.MS_EYE_SKY_LIGHT).toUpperCase(),
			"eyeLightFlags;  // 0x" + Long.toHexString(Proto.MS_EYE_LIGHT_FLAGS).toUpperCase(),
		};
		for (String line : expected) {
			assertTrue(text.contains(line), "the header should say " + line);
		}
		// The new hit bits follow the four there were.
		assertEquals(Proto.HIT_FIRE << 1, Proto.HIT_POWER_ATTACK);
		assertEquals(Proto.HIT_POWER_ATTACK << 1, Proto.HIT_CROUCHING);
	}
}
