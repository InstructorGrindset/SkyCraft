package dev.skycraft.maps;

import static dev.skycraft.link.Proto.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim's land sampled coarsely (pure, unit tested): what a Minecraft map draws where Minecraft holds none of Skyrim's
 * collision yet, so a new map isn't blank away from where the player has walked. Minecraft asks Skyrim for a square of
 * {@link #SIDE} x {@link #SIDE} samples over a map near the player (EV_MAP_SAMPLE); Skyrim answers for the cells it has
 * loaded (about 150 blocks round its player) with the land's height, what it's made of and its water. Each answer is a
 * {@link Patch}, kept (the newest {@link #MAX_PATCHES}) in the mirror's terms; a point between samples gets the land's
 * height blended from the four round it and the material and water of the nearest.
 */
public final class LandSamples {
	/** Samples along each side of a patch (Proto.MAP_SAMPLE_SIDE). */
	public static final int SIDE = MAP_SAMPLE_SIDE;
	/** Patches kept; the oldest go first. */
	public static final int MAX_PATCHES = 48;
	/** Blocks round Skyrim's player it can answer for (its loaded cells reach about this far). */
	public static final int SKYRIM_REACH = 160;
	/** A patch whose land is further than this (median, blocks) from Skyrim's collision where both are known isn't used. */
	public static final double MAX_DISAGREEMENT = 2.5;
	/** Comparisons a patch needs before it's judged; with fewer it's taken. */
	public static final int MIN_COMPARISONS = 6;

	/**
	 * One answer: samples[z * SIDE + x] is block column {@code x0 + x * spacing, z0 + z * spacing} (mirror coords; Skyrim
	 * samples the column's centre).
	 */
	public record Patch(int x0, int z0, int spacing, short[] height, byte[] material, byte[] water) {
		public boolean covers(double x, double z) {
			double span = (double) (SIDE - 1) * this.spacing;
			return x >= this.x0 && z >= this.z0 && x <= this.x0 + span && z <= this.z0 + span;
		}

		/** The land's height (or the water's surface) at sample (i, j), NaN if unknown. */
		public double heightAt(int i, int j) {
			return decodeHeight(this.height[j * SIDE + i]);
		}

		/** How many of its samples are known. */
		public int known() {
			int n = 0;
			for (short h : this.height) {
				if (h != SAMPLE_UNKNOWN) {
					n++;
				}
			}
			return n;
		}
	}

	/** The land at a point: its height (the water's surface when there's water), DIG_* material, water depth, |normal y|. */
	public record Sample(double height, int material, int water, double ny) {
		public MapRules.Ground ground() {
			if (this.water > 0) {
				return new MapRules.Ground(this.height, MapRules.Tone.WATER, this.water);
			}
			return new MapRules.Ground(this.height, MapRules.tone(this.material, true, true, this.ny), 0);
		}
	}

	private final List<Patch> patches = new ArrayList<>(); // newest first

	/** A sample's height in blocks (MapSample height is MC y x 4), NaN for SAMPLE_UNKNOWN. */
	public static double decodeHeight(short value) {
		return value == SAMPLE_UNKNOWN ? Double.NaN : value / 4.0;
	}

	public void add(Patch patch) {
		this.patches.add(0, patch);
		while (this.patches.size() > MAX_PATCHES) {
			this.patches.remove(this.patches.size() - 1);
		}
	}

	public void clear() {
		this.patches.clear();
	}

	public int size() {
		return this.patches.size();
	}

	/** The land at block column (x, z) from the newest patch that knows it, or null. */
	public @Nullable Sample at(double x, double z) {
		for (Patch p : this.patches) {
			if (!p.covers(x, z)) {
				continue;
			}
			Sample s = sample(p, x, z);
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	/** The land at block column (x, z) inside a patch: null if the nearest sample isn't known. */
	static @Nullable Sample sample(Patch p, double x, double z) {
		double fx = (x - p.x0()) / p.spacing(), fz = (z - p.z0()) / p.spacing();
		int i = Math.min(SIDE - 2, Math.max(0, (int) Math.floor(fx))), j = Math.min(SIDE - 2, Math.max(0, (int) Math.floor(fz)));
		double tx = fx - i, tz = fz - j;
		int ni = tx < 0.5 ? i : i + 1, nj = tz < 0.5 ? j : j + 1;
		int nearest = nj * SIDE + ni;
		if (p.height()[nearest] == SAMPLE_UNKNOWN) {
			return null;
		}
		int water = p.water()[nearest] & 0xFF;
		int material = p.material()[nearest] & 0xFF;
		double h00 = p.heightAt(i, j), h10 = p.heightAt(i + 1, j), h01 = p.heightAt(i, j + 1), h11 = p.heightAt(i + 1, j + 1);
		boolean blend = water == 0 && !Double.isNaN(h00) && !Double.isNaN(h10) && !Double.isNaN(h01) && !Double.isNaN(h11)
			&& (p.water()[j * SIDE + i] | p.water()[j * SIDE + i + 1] | p.water()[(j + 1) * SIDE + i] | p.water()[(j + 1) * SIDE + i + 1]) == 0;
		if (!blend) {
			return new Sample(p.heightAt(ni, nj), material, water, 1.0);
		}
		double h = h00 * (1 - tx) * (1 - tz) + h10 * tx * (1 - tz) + h01 * (1 - tx) * tz + h11 * tx * tz;
		// The slope from the four: blocks up per block across, each way.
		double gx = ((h10 - h00) * (1 - tz) + (h11 - h01) * tz) / p.spacing();
		double gz = ((h01 - h00) * (1 - tx) + (h11 - h10) * tx) / p.spacing();
		return new Sample(h, material, 0, 1.0 / Math.sqrt(1.0 + gx * gx + gz * gz));
	}

	/**
	 * Whether a patch's land agrees with Skyrim's collision: {@code sampled[i]} and {@code exact[i]} are the land's height at
	 * the same points (the patch's, and the collision's own land there). With too few points to judge, it's taken.
	 */
	public static boolean agrees(double[] sampled, double[] exact, int count) {
		if (count < MIN_COMPARISONS) {
			return true;
		}
		double[] diff = new double[count];
		for (int i = 0; i < count; i++) {
			diff[i] = Math.abs(sampled[i] - exact[i]);
		}
		Arrays.sort(diff);
		double median = count % 2 == 1 ? diff[count / 2] : (diff[count / 2 - 1] + diff[count / 2]) / 2.0;
		return median <= MAX_DISAGREEMENT;
	}

	/**
	 * The square to ask Skyrim for over a map near its player: {x0, z0, spacing}, or null when the map is out of Skyrim's
	 * reach. The map covers {@code mapX0, mapZ0} to {@code + mapSize} blocks (at {@code blocksPerPixel}); the part within
	 * {@link #SKYRIM_REACH} of the player ({@code playerX, playerZ}) is asked for, its samples no closer than a pixel apart.
	 */
	public static int @Nullable [] request(int mapX0, int mapZ0, int mapSize, int blocksPerPixel, double playerX, double playerZ) {
		int x0 = Math.max(mapX0, (int) Math.floor(playerX) - SKYRIM_REACH), x1 = Math.min(mapX0 + mapSize, (int) Math.floor(playerX) + SKYRIM_REACH);
		int z0 = Math.max(mapZ0, (int) Math.floor(playerZ) - SKYRIM_REACH), z1 = Math.min(mapZ0 + mapSize, (int) Math.floor(playerZ) + SKYRIM_REACH);
		if (x1 <= x0 || z1 <= z0) {
			return null;
		}
		int side = Math.max(x1 - x0, z1 - z0);
		int spacing = Math.max(blocksPerPixel, (side + SIDE - 2) / (SIDE - 1));
		return new int[] { x0, z0, Math.min(spacing, 256) };
	}
}
