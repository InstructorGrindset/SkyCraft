package dev.skycraft.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrimeTallyTest {
	@Test
	void aDigThroughAWallIsOneCrime() {
		CrimeTally t = new CrimeTally();
		t.add(Proto.CRIME_OBJECT, 20, 1, 2, 3, 100);
		t.add(Proto.CRIME_OBJECT, 20, 2, 2, 3, 120);
		t.add(Proto.CRIME_OBJECT, 20, 3, 2, 3, 150);
		assertEquals(3, t.pending());
		assertTrue(t.due(159, 60).isEmpty());
		List<CrimeTally.Batch> due = t.due(160, 60);
		assertEquals(1, due.size());
		CrimeTally.Batch b = due.get(0);
		assertEquals(Proto.CRIME_OBJECT, b.kind());
		assertEquals(60, b.value());
		assertEquals(3, b.count());
		assertEquals(3.0, b.x(), 1e-9); // the latest place
		assertEquals(0, t.pending());
		assertTrue(t.due(1000, 60).isEmpty());
	}

	@Test
	void kindsAreBatchedApartAndAfresh() {
		CrimeTally t = new CrimeTally();
		t.add(Proto.CRIME_BLOCK, 4, 0, 0, 0, 0);
		t.add(Proto.CRIME_GROUND, 6, 0, 0, 0, 30);
		List<CrimeTally.Batch> first = t.due(60, 60);
		assertEquals(1, first.size());
		assertEquals(Proto.CRIME_BLOCK, first.get(0).kind());
		t.add(Proto.CRIME_BLOCK, 4, 0, 0, 0, 61); // a new batch
		List<CrimeTally.Batch> second = t.due(121, 60);
		assertEquals(2, second.size());
		assertEquals(Proto.CRIME_GROUND, second.get(0).kind()); // kinds in order
		assertEquals(1, second.get(1).count());
	}
}
