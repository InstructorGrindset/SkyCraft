package dev.skycraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyEdgeTest {
	private static final double HALF = 0.3, STEP = 0.6;

	private static void flat(List<SkyTri> out, double x0, double z0, double x1, double z1, double y) {
		quad(out, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1);
	}

	/** Falls away along +X from x0: height = y0 - (x - x0) * tan(angle). */
	private static void downhill(List<SkyTri> out, double x0, double x1, double z0, double z1, double y0, double degrees) {
		double y1 = y0 - (x1 - x0) * Math.tan(Math.toRadians(degrees));
		quad(out, x0, y0, z0, x1, y1, z0, x1, y1, z1, x0, y0, z1);
	}

	private static void quad(List<SkyTri> out, double... p) {
		out.add(tri(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8]));
		out.add(tri(p[0], p[1], p[2], p[6], p[7], p[8], p[9], p[10], p[11]));
	}

	private static SkyTri tri(double... v) {
		float[] f = new float[9];
		for (int i = 0; i < 9; i++) {
			f[i] = (float) v[i];
		}
		return new SkyTri(f, 0, false);
	}

	/** The mixin's test for a player whose feet are centred at (x, y, z): Skyrim surfaces only. */
	private static SkyEdge.Drop drop(List<SkyTri> tris, double x, double y, double z) {
		return (dx, dz) -> !SkyEdge.surfaceUnder(tris, x - HALF + 1.0E-7 + dx, z - HALF + 1.0E-7 + dz, x + HALF - 1.0E-7 + dx, z + HALF - 1.0E-7 + dz,
			y - STEP - 1.0E-7, y + 0.01);
	}

	/** Crouch-walks along +X a tick at a time; returns where the feet end up. */
	private static double walk(List<SkyTri> tris, double x, double y, double z, double speed, int ticks) {
		for (int i = 0; i < ticks; i++) {
			x += SkyEdge.backOff(speed, 0.0, drop(tris, x, y, z))[0];
		}
		return x;
	}

	@Test
	void flatGroundLetsTheMoveThrough() {
		List<SkyTri> t = new ArrayList<>();
		flat(t, -10, -10, 10, 10, 64.0);
		double[] move = SkyEdge.backOff(0.08, -0.03, drop(t, 0.0, 64.0, 0.0));
		assertEquals(0.08, move[0]);
		assertEquals(-0.03, move[1]);
	}

	@Test
	void crouchingStopsAtTheEdgeOfSkyrimGround() {
		List<SkyTri> t = new ArrayList<>();
		flat(t, -10, -10, 0, 10, 64.0); // a ledge ending at x = 0, nothing under it
		double x = walk(t, -2.0, 64.0, 0.0, 0.1, 60);
		// Like Minecraft on a block: the player can lean out until only the back of their box is over the ledge.
		assertTrue(x + HALF > 0.0 && x - HALF <= 0.0, "stopped with its box still over the ledge, at x " + x);
	}

	@Test
	void aBlockHighDropStopsTheCrouchToo() {
		List<SkyTri> t = new ArrayList<>();
		flat(t, -10, -10, 0, 10, 64.0);
		flat(t, 0, -10, 10, 10, 63.0); // a metre below: more than a step
		double x = walk(t, -2.0, 64.0, 0.0, 0.1, 60);
		assertTrue(x - HALF <= 0.0, "stopped at the drop, at x " + x);
	}

	@Test
	void aStepDownIsWalkedOver() {
		List<SkyTri> t = new ArrayList<>();
		flat(t, -10, -10, 0, 10, 64.0);
		flat(t, 0, -10, 10, 10, 63.6); // under the step height
		double x = walk(t, -2.0, 64.0, 0.0, 0.1, 40);
		assertTrue(x > 1.0, "walked on past the small step, at x " + x);
	}

	@Test
	void aGentleSlopeIsWalkedDown() {
		List<SkyTri> t = new ArrayList<>();
		downhill(t, -10, 10, -10, 10, 64.0 + 10.0 * Math.tan(Math.toRadians(20.0)), 20.0); // 64 at x = 0
		double x = -2.0, y = 64.0 + 2.0 * Math.tan(Math.toRadians(20.0));
		for (int i = 0; i < 30; i++) {
			x += SkyEdge.backOff(0.1, 0.0, drop(t, x, y, 0.0))[0];
			y = 64.0 - x * Math.tan(Math.toRadians(20.0)); // the collider keeps the feet on the slope
		}
		assertEquals(1.0, x, 1e-9, "crouch-walked down the slope unhindered");
	}

	@Test
	void sidewaysAlongAnEdgeIsFine() {
		List<SkyTri> t = new ArrayList<>();
		flat(t, -10, -10, 0, 10, 64.0);
		// At the edge (box just over it), walking along it (+Z) isn't stopped; out over it (+X) is.
		double[] along = SkyEdge.backOff(0.0, 0.1, drop(t, 0.25, 64.0, 0.0));
		assertEquals(0.1, along[1]);
		double[] out = SkyEdge.backOff(0.1, 0.0, drop(t, 0.25, 64.0, 0.0));
		assertEquals(0.0, out[0]);
	}
}
