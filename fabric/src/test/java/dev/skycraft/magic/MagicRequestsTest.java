package dev.skycraft.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MagicRequestsTest {
	@Test
	void anAnswerFindsItsRequest() {
		MagicRequests r = new MagicRequests(100);
		int shout = r.open(true, 0x0A000001, "Unrelenting Force", 0.0);
		int spell = r.open(false, 0x0B000001, "Flames", 0.1);
		assertTrue(shout != spell);
		MagicRequests.Pending p = r.answered(spell);
		assertNotNull(p);
		assertEquals("Flames", p.name());
		assertEquals(0x0B000001, p.formId());
		assertNull(r.answered(spell)); // answered once
		assertNull(r.answered(12345)); // not ours
		assertEquals(1, r.waiting());
	}

	@Test
	void unansweredRequestsAreGivenUp() {
		MagicRequests r = new MagicRequests(1);
		r.open(true, 1, "a", 0.0);
		r.open(false, 2, "b", 2.0);
		List<MagicRequests.Pending> gone = r.expired(3.5, 3.0);
		assertEquals(1, gone.size());
		assertEquals("a", gone.get(0).name());
		assertEquals(1, r.waiting());
		assertTrue(r.expired(3.6, 3.0).isEmpty());
		r.clear();
		assertEquals(0, r.waiting());
	}

	@Test
	void numbersNeverZeroAndTheListStaysSmall() {
		MagicRequests r = new MagicRequests(-2);
		assertEquals(-1, r.open(true, 1, "", 0.0));
		assertEquals(1, r.open(true, 1, "", 0.0)); // 0 is skipped: Skyrim answers it as no request at all
		for (int i = 0; i < 200; i++) {
			r.open(false, 2, "", 0.0);
		}
		assertTrue(r.waiting() <= 64);
	}
}
