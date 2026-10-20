package dev.skycraft.farming;

import static dev.skycraft.link.Proto.DIG_DIRT;
import static dev.skycraft.link.Proto.DIG_GRASS;
import static dev.skycraft.link.Proto.DIG_MUD;

import dev.skycraft.world.SkyGround;
import dev.skycraft.world.SkyTri;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim farming's rules (switch {@code skyrim_farming}), pure Java and unit tested. Minecraft's crops need Minecraft
 * farmland, and Skyrim's ground isn't blocks: it's Skyrim's geometry, each triangle knowing what it's made of (SkyTri
 * material, a Proto.DIG_* Skyrim picks from its Havok material: a land texture's material on the land, so a farm's plowed
 * soil is dirt or mud and its edges grass). A hoe on Skyrim's soil tills it as it tills a Minecraft dirt or grass block:
 * the cell turns into a farmland block, dug out of Skyrim's geometry the way digging does (so Skyrim's mesh there is cut
 * and the walls of the little pit are drawn), and Minecraft's crops are planted on it and grow by Minecraft's rules.
 */
public final class FarmRules {
	/** Farmland is this tall. */
	public static final double FARMLAND_TOP = 15.0 / 16.0;
	/** Ground that faces up at least this much can be tilled (Skyrim's walkable slope). */
	public static final double FLAT_ENOUGH = SkyTri.WALKABLE_NY;

	private FarmRules() {
	}

	/** Whether a Skyrim surface of this material is soil a hoe tills: grass, dirt or mud. */
	public static boolean soil(int material) {
		int base = SkyGround.base(material);
		return base == DIG_GRASS || base == DIG_DIRT || base == DIG_MUD;
	}

	/**
	 * Whether the surface the crosshair hit can be tilled: diggable Skyrim soil (the land, or a mound of earth), facing up
	 * ({@code hitNy}: the hit normal's y, toward the player) no steeper than Skyrim lets you walk.
	 */
	public static boolean tillable(@Nullable SkyTri tri, double hitNy) {
		return tri != null && tri.diggable && !tri.stairHelper && soil(tri.material) && hitNy >= FLAT_ENOUGH;
	}

	/** Where the farmland goes: its cell, and whether the cell above must be dug too (the ground reaches into it). */
	public record Till(int x, int y, int z, boolean digAbove) {
	}

	/**
	 * The farmland for a hit at ({@code x, y, z}) on ground whose height at the middle of that block column is {@code ground}
	 * (NaN: not known, the hit's height is used). The farmland block whose top comes nearest the ground: at most half a
	 * block above it or below it. When the ground reaches up into the cell over the farmland, that cell is dug as well, so
	 * the crop isn't buried in Skyrim's ground.
	 */
	public static Till till(double x, double y, double z, double ground) {
		double h = Double.isNaN(ground) ? y : ground;
		int cellY = (int) Math.floor(h - FARMLAND_TOP + 0.5);
		return new Till((int) Math.floor(x), cellY, (int) Math.floor(z), h > cellY + 1);
	}
}
