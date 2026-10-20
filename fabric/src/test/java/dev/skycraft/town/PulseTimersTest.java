package dev.skycraft.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PulseTimersTest {
	@Test
	void aPulseEndsAndAnotherLengthensIt() {
		PulseTimers p = new PulseTimers();
		assertTrue(p.start(5L, 30));
		assertFalse(p.start(5L, 20)); // never shorter
		assertTrue(p.due(29).isEmpty());
		assertFalse(p.start(5L, 50));
		assertTrue(p.due(30).isEmpty());
		assertEquals(List.of(5L), p.due(50));
		assertFalse(p.has(5L));
	}

	@Test
	void allEndsEveryPulse() {
		PulseTimers p = new PulseTimers();
		p.start(1L, 10);
		p.start(2L, 99);
		assertEquals(2, p.all().size());
		assertEquals(0, p.size());
	}
}
