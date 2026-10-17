package dev.skycraft.world;

import java.util.List;

/**
 * Crouching stops at edges in the mirror, Skyrim's as well as Minecraft's. Minecraft decides where an
 * edge is by looking for collision under the player (Player.maybeBackOffFromEdge), but the player
 * collides with Skyrim's exact triangles, not blocks, so Minecraft alone saw a drop everywhere on Skyrim
 * ground. These are vanilla's steps with Skyrim's surfaces counted as ground too (pure, unit tested).
 */
public final class SkyEdge {
	/** How much vanilla shortens a move at a time until it's back from the edge. */
	static final double STEP = 0.05;
	/** The footprint is sampled this many times along each side (and at both ends). */
	static final int SAMPLES = 4;

	private SkyEdge() {
	}

	/** Whether the player, moved {@code dx, dz}, could fall at least the step height. */
	@FunctionalInterface
	public interface Drop {
		boolean at(double dx, double dz);
	}

	/**
	 * Vanilla's back-off: the horizontal move {@code dx, dz} shortened {@link #STEP} at a time, x, then z,
	 * then both, until the player wouldn't drop off. Returns the move's {dx, dz}.
	 */
	public static double[] backOff(double dx, double dz, Drop drop) {
		double stepX = Math.signum(dx) * STEP, stepZ = Math.signum(dz) * STEP;
		while (dx != 0.0 && drop.at(dx, 0.0)) {
			if (Math.abs(dx) <= STEP) {
				dx = 0.0;
				break;
			}
			dx -= stepX;
		}
		while (dz != 0.0 && drop.at(0.0, dz)) {
			if (Math.abs(dz) <= STEP) {
				dz = 0.0;
				break;
			}
			dz -= stepZ;
		}
		while (dx != 0.0 && dz != 0.0 && drop.at(dx, dz)) {
			dx = Math.abs(dx) <= STEP ? 0.0 : dx - stepX;
			dz = Math.abs(dz) <= STEP ? 0.0 : dz - stepZ;
		}
		return new double[] { dx, dz };
	}

	/**
	 * Whether any of Skyrim's surfaces is under the footprint {@code minX..maxX, minZ..maxZ} between
	 * {@code bottom} and {@code top}: ground to stand on there, as a block in that box would be for vanilla.
	 */
	public static boolean surfaceUnder(List<SkyTri> tris, double minX, double minZ, double maxX, double maxZ, double bottom, double top) {
		for (SkyTri t : tris) {
			if (t.maxY < bottom || t.minY > top || t.maxX < minX || t.minX > maxX || t.maxZ < minZ || t.minZ > maxZ) {
				continue;
			}
			for (int i = 0; i <= SAMPLES; i++) {
				double x = minX + (maxX - minX) * i / SAMPLES;
				for (int k = 0; k <= SAMPLES; k++) {
					double h = t.heightAt(x, minZ + (maxZ - minZ) * k / SAMPLES);
					if (!Double.isNaN(h) && h >= bottom && h <= top) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
