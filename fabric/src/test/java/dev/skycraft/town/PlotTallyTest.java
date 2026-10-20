package dev.skycraft.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotTallyTest {
	private static final int LAKEVIEW = 0x0300_3221;
	private static final int WINDSTAD = 0x0300_3222;
	private final TownRules rules = TownRules.parse("small_house = 3\nmain_hall = 5\nwings = 6\n", new ArrayList<>());

	@Test
	void keysRoundTripEverywhereInTheMirror() {
		int[][] cells = { { 0, 0, 0 }, { -1, -1, -1 }, { 32768 * 7 + 3, 2031, -32768 * 9 }, { -33_000_000, -2048, 33_000_000 }, { 15, 64, -16 } };
		for (int[] c : cells) {
			long k = PlotTally.key(c[0], c[1], c[2]);
			assertEquals(c[0], PlotTally.keyX(k));
			assertEquals(c[1], PlotTally.keyY(k));
			assertEquals(c[2], PlotTally.keyZ(k));
		}
	}

	@Test
	void blocksStandingReachStagesOnce() {
		PlotTally t = new PlotTally();
		assertTrue(t.add(LAKEVIEW, "Lakeview Manor", PlotTally.key(0, 64, 0), this.rules).isEmpty());
		assertTrue(t.add(LAKEVIEW, "", PlotTally.key(0, 64, 0), this.rules).isEmpty()); // the same block again
		t.add(LAKEVIEW, "", PlotTally.key(1, 64, 0), this.rules);
		assertEquals(List.of(Proto.STAGE_SMALL_HOUSE), t.add(LAKEVIEW, "", PlotTally.key(2, 64, 0), this.rules));
		assertEquals("Lakeview Manor", t.name(LAKEVIEW));
		assertEquals(LAKEVIEW, t.remove(PlotTally.key(2, 64, 0)));
		assertEquals(0, t.remove(PlotTally.key(9, 9, 9)));
		assertEquals(2, t.count(LAKEVIEW));
		assertEquals(Proto.STAGE_SMALL_HOUSE, t.stage(LAKEVIEW)); // reached stays reached
		assertTrue(t.add(LAKEVIEW, "", PlotTally.key(2, 64, 0), this.rules).isEmpty()); // not again
		assertTrue(t.add(LAKEVIEW, "", PlotTally.key(3, 64, 0), this.rules).isEmpty());
		assertEquals(List.of(Proto.STAGE_MAIN_HALL), t.add(LAKEVIEW, "", PlotTally.key(4, 64, 0), this.rules));
		assertEquals(List.of(Proto.STAGE_WINGS), t.add(LAKEVIEW, "", PlotTally.key(5, 64, 0), this.rules));
		assertTrue(t.add(LAKEVIEW, "", PlotTally.key(6, 64, 0), this.rules).isEmpty()); // nothing past the wings
	}

	@Test
	void manyStagesAtOnceWhenTheNumbersAreClose() {
		PlotTally t = new PlotTally();
		TownRules tight = TownRules.parse("small_house = 1\nmain_hall = 2\nwings = 3\n", new ArrayList<>());
		assertEquals(List.of(Proto.STAGE_SMALL_HOUSE), t.add(LAKEVIEW, "", PlotTally.key(0, 0, 0), tight));
		assertEquals(List.of(Proto.STAGE_MAIN_HALL), t.add(LAKEVIEW, "", PlotTally.key(1, 0, 0), tight));
		assertEquals(List.of(Proto.STAGE_WINGS), t.add(LAKEVIEW, "", PlotTally.key(2, 0, 0), tight));
		// A tally read with lower numbers than it was built with catches up at the next block.
		PlotTally lagging = PlotTally.parse(List.of("plot 03003221 0 Lakeview Manor", "block 03003221 0 0 0", "block 03003221 1 0 0"), new ArrayList<>());
		assertEquals(List.of(Proto.STAGE_SMALL_HOUSE, Proto.STAGE_MAIN_HALL, Proto.STAGE_WINGS), lagging.add(LAKEVIEW, "", PlotTally.key(2, 0, 0), tight));
	}

	@Test
	void oneBlockCountsForOnePlot() {
		PlotTally t = new PlotTally();
		long b = PlotTally.key(5, 70, 5);
		t.add(LAKEVIEW, "Lakeview Manor", b, this.rules);
		t.add(WINDSTAD, "Windstad Manor", b, this.rules);
		assertEquals(0, t.count(LAKEVIEW));
		assertEquals(1, t.count(WINDSTAD));
	}

	@Test
	void progressSaysWhatsNext() {
		PlotTally t = new PlotTally();
		t.add(LAKEVIEW, "Lakeview Manor", PlotTally.key(0, 0, 0), this.rules);
		assertEquals("1 of 3 blocks for the small house", t.progress(LAKEVIEW, this.rules));
		for (int i = 1; i < 6; i++) {
			t.add(LAKEVIEW, "", PlotTally.key(i, 0, 0), this.rules);
		}
		assertEquals("6 blocks: every stage is built", t.progress(LAKEVIEW, this.rules));
	}

	@Test
	void savesAndReadsBack() {
		PlotTally t = new PlotTally();
		t.add(LAKEVIEW, "Lakeview Manor", PlotTally.key(-3, 2000, 7), this.rules);
		t.add(LAKEVIEW, "", PlotTally.key(1, 2, 3), this.rules);
		t.add(LAKEVIEW, "", PlotTally.key(4, 5, 6), this.rules);
		t.add(WINDSTAD, "Windstad Manor", PlotTally.key(100, -60, -100), this.rules);
		List<String> problems = new ArrayList<>();
		List<String> lines = new ArrayList<>(t.lines());
		lines.add("garbage here");
		lines.add("block zz 1 2 3");
		PlotTally back = PlotTally.parse(lines, problems);
		assertEquals(2, problems.size(), problems.toString());
		assertEquals(3, back.count(LAKEVIEW));
		assertEquals(Proto.STAGE_SMALL_HOUSE, back.stage(LAKEVIEW));
		assertEquals("Lakeview Manor", back.name(LAKEVIEW));
		assertEquals("Windstad Manor", back.name(WINDSTAD));
		assertEquals(1, back.count(WINDSTAD));
		assertEquals(WINDSTAD, back.remove(PlotTally.key(100, -60, -100)));
		assertEquals(List.of(LAKEVIEW, WINDSTAD), back.plots());
	}
}
