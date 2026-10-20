package dev.skycraft.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import org.junit.jupiter.api.Test;

class HorseRulesTest {
	@Test
	void anAverageMinecraftHorseIsAnOrdinarySkyrimHorse() {
		assertEquals(100, HorseRules.speedMult(HorseRules.SPEED_AVERAGE));
		assertEquals(150, HorseRules.speedMult(HorseRules.SPEED_MAX));
		assertEquals(50, HorseRules.speedMult(HorseRules.SPEED_MIN));
		assertEquals(150, HorseRules.speedMult(1.0), "clamped");
		assertEquals(100, HorseRules.speedMult(Double.NaN));
		assertEquals(HorseRules.SPEED_AVERAGE, HorseRules.minecraftSpeed(100), 1e-9);
		assertEquals(HorseRules.SPEED_AVERAGE, HorseRules.minecraftSpeed(0), 1e-9);
		assertEquals(HorseRules.SPEED_MAX, HorseRules.minecraftSpeed(250), 1e-9);
		assertEquals(HorseRules.SPEED_MIN, HorseRules.minecraftSpeed(10), 1e-9);
		// There and back again.
		for (int mult = 50; mult <= 150; mult += 5) {
			assertEquals(mult, HorseRules.speedMult(HorseRules.minecraftSpeed(mult)));
		}
	}

	@Test
	void statsPackIntoTheEvent() {
		int packed = HorseRules.packStats(0.2871, 27.0F, 13.4F);
		assertEquals(0.2871, HorseRules.unpackSpeed(packed), 1e-4);
		assertEquals(27, HorseRules.unpackMaxHealth(packed));
		assertEquals(13, HorseRules.unpackHealth(packed));
		int clamped = HorseRules.packStats(5.0, 1000.0F, -3.0F);
		assertEquals(0.4095, HorseRules.unpackSpeed(clamped), 1e-9);
		assertEquals(255, HorseRules.unpackMaxHealth(clamped));
		assertEquals(0, HorseRules.unpackHealth(clamped));
	}

	@Test
	void readsSkyrimsHorseInfo() {
		int info = Proto.HORSE_INFO_NEAR | Proto.HORSE_INFO_CARRIED | 120 << Proto.HORSE_INFO_SPEED_SHIFT | 50 << Proto.HORSE_INFO_HEALTH_SHIFT;
		assertTrue(HorseRules.comesAlong(0x1234, info));
		assertFalse(HorseRules.comesAlong(0, info), "no horse");
		assertFalse(HorseRules.comesAlong(0x1234, info & ~Proto.HORSE_INFO_NEAR), "left behind");
		assertFalse(HorseRules.comesAlong(0x1234, info | Proto.HORSE_INFO_AWAY), "in Minecraft already");
		assertEquals(120, HorseRules.infoSpeedMult(info));
		assertEquals(0.5F, HorseRules.infoHealthShare(info, 0x1234), 1e-6F);
		assertEquals(1.0F, HorseRules.infoHealthShare(info & 0xFFFF, 0x1234), 1e-6F, "not said: whole");
		assertTrue(HorseRules.arrived(Proto.HORSE_INFO_ARRIVED));
		assertFalse(HorseRules.arrived(info));
		assertTrue(HorseRules.away(Proto.HORSE_INFO_AWAY));
	}

	@Test
	void keepsStatsWithinAMinecraftHorsesOwn() {
		assertEquals(HorseRules.JUMP_AVERAGE, HorseRules.jump(0.0), 1e-9);
		assertEquals(0.9, HorseRules.jump(0.9), 1e-9);
		assertEquals(HorseRules.JUMP_MAX, HorseRules.jump(3.0), 1e-9);
		assertEquals(HorseRules.HEALTH_AVERAGE, HorseRules.maxHealth(0.0F), 1e-6F);
		assertEquals(30.0F, HorseRules.maxHealth(53.0F), 1e-6F);
		assertEquals(11.0F, HorseRules.health(22.0F, 0.5F), 1e-6F);
		assertEquals(2.0F, HorseRules.health(22.0F, 0.01F), 1e-6F, "never comes over dying");
		assertEquals(22.0F, HorseRules.health(22.0F, 3.0F), 1e-6F);
	}
}
