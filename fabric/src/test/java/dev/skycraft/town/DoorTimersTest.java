package dev.skycraft.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DoorTimersTest {
	@Test
	void closesAWhileAfterTheDoorwayEmpties() {
		DoorTimers d = new DoorTimers();
		d.opened(7L, 100);
		assertTrue(d.due(159, 60).isEmpty());
		d.occupied(7L, 150); // the NPC walks through
		assertTrue(d.due(200, 60).isEmpty());
		assertEquals(List.of(7L), d.due(210, 60));
		assertFalse(d.tracking(7L));
	}

	@Test
	void openingAgainStartsOverAndForgettingStops() {
		DoorTimers d = new DoorTimers();
		d.opened(1L, 0);
		d.opened(2L, 10);
		d.opened(1L, 50);
		assertEquals(List.of(2L, 1L), d.keys());
		assertEquals(List.of(2L), d.due(70, 60));
		d.forget(1L);
		assertTrue(d.due(1000, 60).isEmpty());
		d.occupied(5L, 3); // not ours: nothing happens
		assertEquals(0, d.size());
	}

	@Test
	void tooManyClosesTheOldest() {
		DoorTimers d = new DoorTimers();
		for (long k = 0; k < DoorTimers.MAX + 2; k++) {
			d.opened(k, 0);
		}
		assertEquals(List.of(0L, 1L), d.due(1, 60));
		assertEquals(DoorTimers.MAX, d.size());
	}
}
