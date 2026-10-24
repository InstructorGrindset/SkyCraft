package dev.skycraft.maps;

import static dev.skycraft.link.Proto.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LandSamplesTest {
	private static final int N = LandSamples.SIDE * LandSamples.SIDE;

	/** A patch whose land rises {@code rise} blocks per sample eastward from {@code base}, all grass. */
	private static LandSamples.Patch slope(int x0, int z0, int spacing, double base, double rise) {
		short[] h = new short[N];
		byte[] m = new byte[N];
		byte[] w = new byte[N];
		for (int j = 0; j < LandSamples.SIDE; j++) {
			for (int i = 0; i < LandSamples.SIDE; i++) {
				h[j * LandSamples.SIDE + i] = (short) Math.round((base + rise * i) * 4);
				m[j * LandSamples.SIDE + i] = (byte) DIG_GRASS;
			}
		}
		return new LandSamples.Patch(x0, z0, spacing, h, m, w);
	}

	@Test
	void heightsAreQuarterBlocks() {
		assertTrue(Double.isNaN(LandSamples.decodeHeight((short) SAMPLE_UNKNOWN)));
		assertEquals(64.0, LandSamples.decodeHeight((short) 256));
		assertEquals(-10.25, LandSamples.decodeHeight((short) -41));
	}

	@Test
	void theLandBetweenSamplesIsBlended() {
		LandSamples samples = new LandSamples();
		samples.add(slope(100, 200, 4, 60.0, 1.0));
		LandSamples.Sample at = samples.at(100, 200);
		assertNotNull(at);
		assertEquals(60.0, at.height(), 1e-9);
		LandSamples.Sample between = samples.at(106, 210);
		assertNotNull(between);
		assertEquals(61.5, between.height(), 1e-9, "half way from the second sample to the third");
		assertEquals(1.0 / Math.sqrt(1.0 + 0.25 * 0.25), between.ny(), 1e-9, "a quarter block up per block across");
		assertEquals(MapRules.Tone.GRASS, between.ground().tone());
		assertNull(samples.at(99, 200), "west of the patch");
		assertNull(samples.at(100 + 19 * 4 + 1, 200), "east of it");
	}

	@Test
	void waterIsTheNearestSamples() {
		LandSamples.Patch p = slope(0, 0, 8, 60.0, 0.0);
		p.height()[LandSamples.SIDE + 1] = (short) (62 * 4); // sample (1, 1): water's surface at 62, 3 deep
		p.water()[LandSamples.SIDE + 1] = 3;
		LandSamples samples = new LandSamples();
		samples.add(p);
		LandSamples.Sample lake = samples.at(9, 7);
		assertNotNull(lake);
		assertEquals(3, lake.water());
		assertEquals(62.0, lake.height(), 1e-9);
		assertEquals(MapRules.Tone.WATER, lake.ground().tone());
		assertEquals(3, lake.ground().waterDepth());
		LandSamples.Sample shore = samples.at(3, 3);
		assertNotNull(shore);
		assertEquals(0, shore.water());
		assertEquals(60.0, shore.height(), 1e-9, "next to water the land isn't blended with the water's surface");
	}

	@Test
	void unknownSamplesAndNewerPatches() {
		LandSamples.Patch old = slope(0, 0, 4, 50.0, 0.0);
		LandSamples.Patch newer = slope(0, 0, 4, 70.0, 0.0);
		Arrays.fill(newer.height(), 0, LandSamples.SIDE * 2, (short) SAMPLE_UNKNOWN); // its two northern rows unknown
		LandSamples samples = new LandSamples();
		samples.add(old);
		samples.add(newer);
		assertEquals(70.0, samples.at(20, 20).height(), 1e-9, "the newest first");
		assertEquals(50.0, samples.at(20, 0).height(), 1e-9, "an older patch knows what the newer doesn't");
		assertEquals(N - 2 * LandSamples.SIDE, newer.known());
		for (int i = 0; i < LandSamples.MAX_PATCHES + 5; i++) {
			samples.add(slope(i * 1000, 0, 4, 50.0, 0.0));
		}
		assertEquals(LandSamples.MAX_PATCHES, samples.size());
		assertNull(samples.at(20, 20), "the oldest went");
	}

	@Test
	void aPatchMustAgreeWithSkyrimsCollision() {
		double[] exact = { 60, 61, 62, 63, 64, 65, 66, 67 };
		assertTrue(LandSamples.agrees(new double[] { 60.5, 61, 61.5, 63, 64.25, 65, 66, 67.5 }, exact, 8));
		assertFalse(LandSamples.agrees(new double[] { 80, 81, 82, 83, 84, 85, 86, 87 }, exact, 8), "heights read wrongly");
		assertTrue(LandSamples.agrees(new double[] { 80, 81, 62, 63, 64, 65, 66, 67 }, exact, 8), "a tree or two doesn't sink it");
		assertTrue(LandSamples.agrees(new double[] { 99, 99 }, exact, 2), "too few to judge");
	}

	@Test
	void theSquareAskedForIsTheMapNearSkyrimsPlayer() {
		// A scale 0 map round the player: all of it, a sample every 7 blocks (19 x 7 = 133 covers 128).
		assertEquals("[-64, -64, 7]", Arrays.toString(LandSamples.request(-64, -64, 128, 1, 10, 10)));
		// A scale 4 map (2048 blocks): only the part Skyrim has loaded, no closer than a pixel.
		int[] wide = LandSamples.request(-1024, -1024, 2048, 16, 0, 0);
		assertEquals("[-160, -160, 17]", Arrays.toString(wide));
		// A map far away: nothing to ask.
		assertNull(LandSamples.request(5000, 5000, 128, 1, 0, 0));
	}
}
