package dev.skycraft.maps;

import dev.skycraft.world.SkyTri;
import java.util.Arrays;
import java.util.List;

/**
 * Skyrim's highest surface over each block column of one column of collision regions, {@link #SIZE} x {@link #SIZE} block
 * columns, worked out from the triangles Minecraft holds there (pure, unit tested; SkyMaps keeps these per column of
 * regions and builds them again when the triangles change). A column's surface is the highest triangle over the column's
 * centre: the top of the land, a roof, a bridge, a rock. Skyrim's invisible stair ramps and walls (near vertical) don't
 * count.
 */
public final class MapSurface {
	/** Block columns along each side: a collision region's width (SkyCollision.REGION_SIZE). */
	public static final int SIZE = 8;

	/** The north-west block column. */
	public final int originX, originZ;
	private final float[] height = new float[SIZE * SIZE];
	private final byte[] material = new byte[SIZE * SIZE];
	private final byte[] kind = new byte[SIZE * SIZE];
	private final float[] ny = new float[SIZE * SIZE];
	private static final int TERRAIN = 1, DIGGABLE = 2;

	private MapSurface(int originX, int originZ) {
		this.originX = originX;
		this.originZ = originZ;
		Arrays.fill(this.height, Float.NaN);
	}

	/** The highest surface over each of the {@link #SIZE} x {@link #SIZE} columns from {@code originX, originZ}. */
	public static MapSurface build(int originX, int originZ, List<SkyTri> tris) {
		MapSurface out = new MapSurface(originX, originZ);
		for (SkyTri t : tris) {
			if (t.stairHelper || Math.abs(t.ny) < 0.05) {
				continue;
			}
			// The column centres (x + 0.5) inside the triangle's footprint, and inside this square.
			int x0 = Math.max(originX, (int) Math.ceil(t.minX - 0.5)), x1 = Math.min(originX + SIZE - 1, (int) Math.floor(t.maxX - 0.5));
			int z0 = Math.max(originZ, (int) Math.ceil(t.minZ - 0.5)), z1 = Math.min(originZ + SIZE - 1, (int) Math.floor(t.maxZ - 0.5));
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					double h = t.heightAt(x + 0.5, z + 0.5);
					int i = (z - originZ) * SIZE + (x - originX);
					if (!Double.isNaN(h) && !(h <= out.height[i])) {
						out.height[i] = (float) h;
						out.material[i] = (byte) t.material;
						out.kind[i] = (byte) ((t.terrain ? TERRAIN : 0) | (t.diggable ? DIGGABLE : 0));
						out.ny[i] = (float) Math.abs(t.ny);
					}
				}
			}
		}
		return out;
	}

	private int index(int x, int z) {
		int dx = x - this.originX, dz = z - this.originZ;
		return dx < 0 || dz < 0 || dx >= SIZE || dz >= SIZE ? -1 : dz * SIZE + dx;
	}

	/** The height of the highest surface over block column (x, z), or NaN where there's none (or it's not in this square). */
	public double height(int x, int z) {
		int i = index(x, z);
		return i < 0 ? Double.NaN : this.height[i];
	}

	/** Its tone (null where there's no surface). */
	public MapRules.@org.jspecify.annotations.Nullable Tone tone(int x, int z) {
		int i = index(x, z);
		if (i < 0 || Float.isNaN(this.height[i])) {
			return null;
		}
		return MapRules.tone(this.material[i] & 0xFF, (this.kind[i] & TERRAIN) != 0, (this.kind[i] & DIGGABLE) != 0, this.ny[i]);
	}

	/** Whether the surface there is the land itself (not an object on it). */
	public boolean terrain(int x, int z) {
		int i = index(x, z);
		return i >= 0 && !Float.isNaN(this.height[i]) && (this.kind[i] & TERRAIN) != 0;
	}

	/** How many of its columns have a surface. */
	public int known() {
		int n = 0;
		for (float h : this.height) {
			if (!Float.isNaN(h)) {
				n++;
			}
		}
		return n;
	}
}
