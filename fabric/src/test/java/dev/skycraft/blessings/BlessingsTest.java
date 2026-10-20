package dev.skycraft.blessings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import dev.skycraft.loot.ItemRules;
import org.junit.jupiter.api.Test;

class BlessingsTest {
	@Test
	void eachGodGivesItsEffect() {
		assertEquals("resistance", Blessings.gift(Proto.BLESSING_TALOS).effect());
		assertEquals("speed", Blessings.gift(Proto.BLESSING_KYNARETH).effect());
		assertEquals("regeneration", Blessings.gift(Proto.BLESSING_MARA).effect());
		assertEquals("health_boost", Blessings.gift(Proto.BLESSING_ARKAY).effect());
		assertEquals("luck", Blessings.gift(Proto.BLESSING_DIBELLA).effect());
		assertEquals("haste", Blessings.gift(Proto.BLESSING_JULIANOS).effect());
		assertEquals("resistance", Blessings.gift(Proto.BLESSING_STENDARR).effect());
		assertEquals("hero_of_the_village", Blessings.gift(Proto.BLESSING_ZENITHAR).effect());
		assertEquals("haste", Blessings.gift(Proto.BLESSING_AKATOSH).effect());
		assertEquals("night_vision", Blessings.gift(Proto.BLESSING_AURIEL).effect());
		assertEquals("luck", Blessings.gift(Proto.BLESSING_OTHER).effect());
		assertEquals("Talos", Blessings.gift(Proto.BLESSING_TALOS).god());
	}

	@Test
	void everyKindHasAGiftAndNoneHasNone() {
		for (int kind = Proto.BLESSING_AKATOSH; kind <= Proto.BLESSING_OTHER; kind++) {
			Blessings.Gift gift = Blessings.gift(kind);
			assertNotNull(gift, "kind " + kind);
			assertTrue(ItemRules.EFFECTS.contains(gift.effect()), gift.effect() + " is a 26.3 effect");
			assertTrue(gift.amplifier() >= 0 && gift.amplifier() <= 4);
		}
		assertNull(Blessings.gift(Proto.BLESSING_NONE));
		assertNull(Blessings.gift(99)); // a kind from a newer plugin
	}

	@Test
	void eightGameHoursLastByTheTimescale() {
		// The default timescale 20: a game hour is 3 real minutes, 8 of them 24 minutes.
		assertEquals(24 * 60 * 20, Blessings.ticks(Blessings.HOURS, 20.0));
		// Timescale 10 (a popular slower clock): twice as long; 60: a third.
		assertEquals(48 * 60 * 20, Blessings.ticks(8.0, 10.0));
		assertEquals(8 * 60 * 20, Blessings.ticks(8.0, 60.0));
		// What's left of a blessing a while in.
		assertEquals(12 * 60 * 20, Blessings.ticks(4.0, 20.0));
		// As Skyrim sends it (thousandths).
		assertEquals(24 * 60 * 20, Blessings.ticksFromInput(8000, 20000));
		// No timescale: the default. Nothing left: nothing.
		assertEquals(24 * 60 * 20, Blessings.ticks(8.0, 0.0));
		assertEquals(0, Blessings.ticks(0.0, 20.0));
		assertEquals(0, Blessings.ticks(-1.0, 20.0));
	}
}
