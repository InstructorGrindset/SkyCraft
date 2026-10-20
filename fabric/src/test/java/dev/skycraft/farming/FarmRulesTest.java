package dev.skycraft.farming;

import static dev.skycraft.link.Proto.DIG_DIRT;
import static dev.skycraft.link.Proto.DIG_GRASS;
import static dev.skycraft.link.Proto.DIG_MUD;
import static dev.skycraft.link.Proto.DIG_SAND;
import static dev.skycraft.link.Proto.DIG_SNOW;
import static dev.skycraft.link.Proto.DIG_STONE;
import static dev.skycraft.link.Proto.TRI_DIGGABLE;
import static dev.skycraft.link.Proto.TRI_MATERIAL_SHIFT;
import static dev.skycraft.link.Proto.TRI_STAIR_HELPER;
import static dev.skycraft.link.Proto.TRI_TERRAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.world.SkyGround;
import dev.skycraft.world.SkyTri;
import org.junit.jupiter.api.Test;

class FarmRulesTest {
	/** Flat land at height {@code h} over x, z 0 to 4, facing up. */
	private static SkyTri land(double h, int material, int flags) {
		float y = (float) h;
		return new SkyTri(new float[] { 0, y, 0, 0, y, 4, 4, y, 0 }, 0, flags | material << TRI_MATERIAL_SHIFT);
	}

	@Test
	void soilIsGrassDirtAndMud() {
		assertTrue(FarmRules.soil(DIG_GRASS));
		assertTrue(FarmRules.soil(DIG_DIRT));
		assertTrue(FarmRules.soil(DIG_MUD));
		assertTrue(FarmRules.soil(DIG_DIRT | 5 << SkyGround.DEPTH_SHIFT)); // a depth riding along doesn't matter
		assertFalse(FarmRules.soil(DIG_STONE));
		assertFalse(FarmRules.soil(DIG_SAND));
		assertFalse(FarmRules.soil(DIG_SNOW));
	}

	@Test
	void onlyDiggableSoilFacingUpTills() {
		SkyTri farm = land(10.0, DIG_DIRT, TRI_DIGGABLE | TRI_TERRAIN);
		assertTrue(farm.ny > 0.99);
		assertTrue(FarmRules.tillable(farm, 1.0));
		assertTrue(FarmRules.tillable(land(10.0, DIG_MUD, TRI_DIGGABLE), 0.8)); // a mound of earth, not the land itself
		assertFalse(FarmRules.tillable(farm, 0.5)); // a slope too steep to walk
		assertFalse(FarmRules.tillable(farm, -1.0)); // its underside, from a hole
		assertFalse(FarmRules.tillable(land(10.0, DIG_DIRT, 0), 1.0)); // not diggable: a building's floor
		assertFalse(FarmRules.tillable(land(10.0, DIG_STONE, TRI_DIGGABLE | TRI_TERRAIN), 1.0));
		assertFalse(FarmRules.tillable(land(10.0, DIG_DIRT, TRI_DIGGABLE | TRI_STAIR_HELPER), 1.0));
		assertFalse(FarmRules.tillable(null, 1.0));
	}

	@Test
	void theFarmlandTopComesNearestTheGround() {
		for (int i = 0; i < 200; i++) {
			double h = 10.0 + i * 0.01;
			FarmRules.Till t = FarmRules.till(1.3, h, 2.7, h);
			double top = t.y() + FarmRules.FARMLAND_TOP;
			assertTrue(Math.abs(top - h) <= 0.5 + 1e-9, () -> "ground " + h + ": farmland top " + top);
			// The cell over the farmland is dug exactly when the ground reaches into it, so the crop stands clear.
			assertEquals(h > t.y() + 1, t.digAbove(), () -> "ground " + h);
			assertEquals(1, t.x());
			assertEquals(2, t.z());
		}
	}

	@Test
	void examples() {
		// Ground just over a block line: the farmland sits a hair low in a shallow pit, the cell over it dug.
		assertEquals(new FarmRules.Till(0, 9, 0, true), FarmRules.till(0.5, 10.05, 0.5, 10.05));
		// Halfway up a cell: the farmland stands a little proud of the ground.
		assertEquals(new FarmRules.Till(0, 10, 0, false), FarmRules.till(0.5, 10.5, 0.5, 10.5));
		// Ground height unknown at the column's middle: the hit's height.
		assertEquals(new FarmRules.Till(-1, 10, -3, false), FarmRules.till(-0.5, 10.9, -2.5, Double.NaN));
	}
}
