package dev.skycraft.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RedstoneDoorsTest {
	private final Set<Long> powered = new HashSet<>();
	private final RedstoneDoors.Power power = (x, y, z) -> this.powered.contains(PlotTally.key(x, y, z));

	@Test
	void findingAPoweredDoorDoesNothingThenEdgesCount() {
		RedstoneDoors d = new RedstoneDoors();
		this.powered.add(PlotTally.key(1, 2, 3));
		d.seen(1, 2, 3, false, 0);
		assertTrue(d.update(0, 120, this.power).isEmpty()); // already powered when found
		this.powered.clear();
		List<RedstoneDoors.Change> off = d.update(1, 120, this.power);
		assertEquals(List.of(new RedstoneDoors.Change(1, 2, 3, false)), off);
		assertTrue(d.update(2, 120, this.power).isEmpty());
		this.powered.add(PlotTally.key(1, 2, 3));
		assertEquals(List.of(new RedstoneDoors.Change(1, 2, 3, true)), d.update(3, 120, this.power));
	}

	@Test
	void anUnloadedDoorKeepsItsState() {
		RedstoneDoors d = new RedstoneDoors();
		boolean[] loaded = { true };
		RedstoneDoors.Power maybe = (x, y, z) -> loaded[0] ? this.powered.contains(PlotTally.key(x, y, z)) : null;
		this.powered.add(PlotTally.key(0, 0, 0));
		d.seen(0, 0, 0, false, 0);
		d.update(0, 120, maybe);
		loaded[0] = false;
		assertTrue(d.update(1, 120, maybe).isEmpty()); // can't tell: no edge
		loaded[0] = true;
		assertTrue(d.update(2, 120, maybe).isEmpty()); // still powered
		assertEquals(1, d.all().get(0)[4]);
	}

	@Test
	void aDoorSkyrimStopsNamingIsForgotten() {
		RedstoneDoors d = new RedstoneDoors();
		d.seen(-5, 64, 9, true, 0);
		d.update(0, 120, this.power);
		assertEquals(1, d.size());
		assertEquals(1, d.all().get(0)[3]); // open in Skyrim
		d.seen(-5, 64, 9, false, 100); // named again: kept
		assertTrue(d.update(220, 120, this.power).isEmpty());
		assertFalse(d.all().isEmpty());
		d.update(221, 120, this.power);
		assertEquals(0, d.size());
	}
}
