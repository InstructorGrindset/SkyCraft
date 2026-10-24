package dev.skycraft.maps;

import static dev.skycraft.link.Proto.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.world.SkyTri;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapSurfaceTest {
	private static final int LAND = TRI_TERRAIN | TRI_DIGGABLE;

	private static SkyTri tri(int flags, double... v) {
		float[] f = new float[9];
		for (int i = 0; i < 9; i++) {
			f[i] = (float) v[i];
		}
		return new SkyTri(f, 0, flags);
	}

	/** A level square (two triangles) from x0, z0 to x1, z1 at height y. */
	private static void square(List<SkyTri> out, int flags, double x0, double z0, double x1, double z1, double y) {
		out.add(tri(flags, x0, y, z0, x1, y, z0, x1, y, z1));
		out.add(tri(flags, x0, y, z0, x1, y, z1, x0, y, z1));
	}

	@Test
	void theLandUnderEveryColumn() {
		List<SkyTri> tris = new ArrayList<>();
		square(tris, LAND | DIG_GRASS << TRI_MATERIAL_SHIFT, 16, 24, 24, 32, 10.25);
		MapSurface s = MapSurface.build(16, 24, tris);
		assertEquals(64, s.known());
		for (int x = 16; x < 24; x++) {
			for (int z = 24; z < 32; z++) {
				assertEquals(10.25, s.height(x, z), 1e-6);
				assertEquals(MapRules.Tone.GRASS, s.tone(x, z));
				assertTrue(s.terrain(x, z));
			}
		}
		assertTrue(Double.isNaN(s.height(15, 24)), "outside its square");
		assertNull(s.tone(24, 24));
	}

	@Test
	void theHighestSurfaceWins() {
		List<SkyTri> tris = new ArrayList<>();
		square(tris, LAND | DIG_GRASS << TRI_MATERIAL_SHIFT, 0, 0, 8, 8, 10.0);
		square(tris, 0, 2, 2, 5, 5, 16.0); // a roof: no material, can't be dug
		square(tris, TRI_DIGGABLE | DIG_STONE << TRI_MATERIAL_SHIFT, 6, 6, 8, 8, 7.0); // a rock sunk under the land
		MapSurface s = MapSurface.build(0, 0, tris);
		assertEquals(16.0, s.height(3, 3), 1e-6);
		assertEquals(MapRules.Tone.BUILDING, s.tone(3, 3));
		assertFalse(s.terrain(3, 3));
		assertEquals(10.0, s.height(7, 7), 1e-6, "the buried rock is under the land");
		assertEquals(MapRules.Tone.GRASS, s.tone(7, 7));
		assertEquals(10.0, s.height(0, 0), 1e-6);
	}

	@Test
	void stairRampsAndWallsDontCount() {
		List<SkyTri> tris = new ArrayList<>();
		square(tris, LAND | DIG_DIRT << TRI_MATERIAL_SHIFT, 0, 0, 8, 8, 5.0);
		square(tris, TRI_STAIR_HELPER, 0, 0, 8, 8, 9.0);
		// A vertical wall straight through the middle.
		tris.add(tri(0, 0, 0, 4, 8, 0, 4, 8, 20, 4));
		MapSurface s = MapSurface.build(0, 0, tris);
		assertEquals(5.0, s.height(4, 4), 1e-6);
		assertEquals(MapRules.Tone.DIRT, s.tone(4, 4));
	}

	@Test
	void aSteepSlopeOfGrassIsRock() {
		// y = 2x: about 63 degrees, |ny| 0.45.
		List<SkyTri> tris = new ArrayList<>();
		int grass = LAND | DIG_GRASS << TRI_MATERIAL_SHIFT;
		tris.add(tri(grass, 0, 0, 0, 8, 16, 0, 8, 16, 8));
		tris.add(tri(grass, 0, 0, 0, 8, 16, 8, 0, 0, 8));
		MapSurface s = MapSurface.build(0, 0, tris);
		assertEquals(1.0, s.height(0, 3), 1e-5);
		assertEquals(9.0, s.height(4, 3), 1e-5);
		assertEquals(MapRules.Tone.ROCK, s.tone(4, 3));
	}

	@Test
	void anEmptyColumnHasNoSurface() {
		MapSurface s = MapSurface.build(-8, -8, List.of());
		assertEquals(0, s.known());
		assertTrue(Double.isNaN(s.height(-4, -4)));
		assertFalse(s.terrain(-4, -4));
	}
}
