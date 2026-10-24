package dev.skycraft.maps;

import static dev.skycraft.link.Proto.*;

import org.jspecify.annotations.Nullable;

/**
 * How a Minecraft map in the mirror draws Skyrim's world (pure, unit tested; {@link SkyMaps} applies it inside vanilla's
 * map update). The mirror is a void where Skyrim's ground is: where vanilla finds no block in a column, the pixel shows
 * Skyrim's highest surface there instead, coloured by what it's made of and how steep it is, and shaded the way vanilla
 * shades its own ground (each pixel against the one north of it), so hills read as hills. Skyrim's water over its ground
 * is vanilla's water colour, shaded by depth as vanilla's is. A Minecraft block a player placed still wins over Skyrim's
 * ground beneath it; a block buried under Skyrim's ground (in a tunnel) doesn't show, as nothing under the ground does.
 */
public final class MapRules {
	private MapRules() {
	}

	/** Vanilla's map brightness ids (MapColor.Brightness), the low two bits of a map colour byte. */
	public static final int LOW = 0, NORMAL = 1, HIGH = 2;
	/** No Minecraft block with a map colour in the column (or none above Skyrim's ground). */
	public static final int NO_BLOCK = Integer.MIN_VALUE;
	/** At or above this |normal y| (under about 37 degrees) grass stays grass. */
	public static final double FLAT_NY = 0.8;
	/** Below this (steeper than about 60 degrees) the land is a cliff and shows as rock, as Skyrim paints its cliffs. */
	public static final double CLIFF_NY = 0.5;
	/** Snow holds on slopes up to about 70 degrees; steeper is bare rock. */
	public static final double SNOW_CLIFF_NY = 0.35;
	/** Water with no ground known under it is shaded as this many blocks deep. */
	public static final int UNKNOWN_DEPTH = 4;

	/** What a pixel of Skyrim's world shows. SkyMaps colours each with a vanilla map colour. */
	public enum Tone {
		/** Grass on level ground (vanilla's grass). */
		GRASS,
		/** Dirt, and grass on a slope (vanilla's dirt). */
		DIRT,
		/** Mud (vanilla's mud). */
		MUD,
		/** Sand (vanilla's sand). */
		SAND,
		/** Snow (vanilla's snow). */
		SNOW,
		/** Ice (vanilla's ice). */
		ICE,
		/** Rock, cliffs, boulders (vanilla's stone). */
		ROCK,
		/** Skyrim's roads: the land's gravel and cobbled stone (a pale earth tone, terracotta white). */
		ROAD,
		/** Skyrim's water (vanilla's water). */
		WATER,
		/** Buildings and other objects that can't be dug: walls, roofs, bridges, furniture (a stone tone, light gray). */
		BUILDING,
		/** Trees and wood that can be dug (vanilla's wood). */
		WOOD,
		/** Metal (vanilla's metal). */
		METAL,
		/** Other plants that can be dug (moss's green). */
		MOSS,
		/** Cloth (brown wool's brown). */
		CLOTH,
		/** Bone (bone block's colour, sand). */
		BONE,
		/** Ash (Solstheim's; light gray concrete powder's colour). */
		ASH,
	}

	/**
	 * The tone of a Skyrim surface: its DIG_* material, whether it's the land, whether it can be dug, and its |normal y|
	 * (1 level, 0 a wall). Objects that can't be dug (buildings) carry no material and are a stone tone.
	 */
	public static Tone tone(int material, boolean terrain, boolean diggable, double ny) {
		if (!diggable && !terrain) {
			return Tone.BUILDING;
		}
		double flat = Math.abs(ny);
		if (terrain) {
			switch (material) {
				case DIG_GRASS:
					return flat >= FLAT_NY ? Tone.GRASS : flat >= CLIFF_NY ? Tone.DIRT : Tone.ROCK;
				case DIG_SNOW:
					return flat >= SNOW_CLIFF_NY ? Tone.SNOW : Tone.ROCK;
				case DIG_ICE:
					return Tone.ICE;
				case DIG_DIRT:
				case DIG_MUD:
				case DIG_SAND:
				case DIG_GRAVEL:
				case DIG_COBBLE:
					if (flat < CLIFF_NY) {
						return Tone.ROCK;
					}
					return material == DIG_DIRT ? Tone.DIRT : material == DIG_MUD ? Tone.MUD : material == DIG_SAND ? Tone.SAND : Tone.ROAD;
				default:
					break; // the land's other materials (rock, ash, ...) as an object's below
			}
		}
		return switch (material) {
			case DIG_GRASS -> Tone.GRASS;
			case DIG_DIRT -> Tone.DIRT;
			case DIG_MUD -> Tone.MUD;
			case DIG_SAND -> Tone.SAND;
			case DIG_SNOW -> Tone.SNOW;
			case DIG_ICE -> Tone.ICE;
			case DIG_OAK_LOG, DIG_SPRUCE_LOG, DIG_BIRCH_LOG, DIG_PLANKS -> Tone.WOOD;
			case DIG_METAL -> Tone.METAL;
			case DIG_ORGANIC -> Tone.MOSS;
			case DIG_CLOTH -> Tone.CLOTH;
			case DIG_BONE -> Tone.BONE;
			case DIG_ASH -> Tone.ASH;
			case DIG_GLASS -> Tone.BUILDING;
			default -> Tone.ROCK; // stone, cobble, gravel, none known
		};
	}

	/**
	 * What one pixel's column shows of Skyrim from above: the top of its ground (or of its water) and its tone, and how
	 * many blocks of water are over the ground (0: none).
	 */
	public record Ground(double height, Tone tone, int waterDepth) {
		/** The block level vanilla's shading would read for it: the block its top is in. */
		public int shadeY() {
			return (int) Math.floor(this.height - 1.0E-3);
		}

		public boolean water() {
			return this.waterDepth > 0;
		}
	}

	/**
	 * Skyrim's ground in a column: its highest surface ({@code land}, NaN when none is known, with that surface's tone) under
	 * Skyrim's water surface {@code water} (NaN: none known). Null when neither is known.
	 */
	public static @Nullable Ground ground(double land, @Nullable Tone tone, double water) {
		boolean haveLand = !Double.isNaN(land) && tone != null;
		if (!Double.isNaN(water) && (!haveLand || water > land + 0.05)) {
			return new Ground(water, Tone.WATER, haveLand ? waterDepth(water, land) : UNKNOWN_DEPTH);
		}
		return haveLand ? new Ground(land, tone, 0) : null;
	}

	/** Blocks of water from its surface down to the ground, as vanilla counts water blocks in a column (at least 1). */
	public static int waterDepth(double surface, double ground) {
		if (Double.isNaN(ground)) {
			return UNKNOWN_DEPTH;
		}
		return Math.max(1, (int) Math.floor(surface - 1.0E-3) - (int) Math.floor(ground - 1.0E-3));
	}

	/**
	 * Skyrim's ground shows rather than the Minecraft block found in the column (its y, or {@link #NO_BLOCK}): it does when
	 * there's none, or the block is below the ground's top block (buried); a block at or above it wins.
	 */
	public static boolean skyrimShows(int blockY, Ground ground) {
		return blockY == NO_BLOCK || ground.shadeY() > blockY;
	}

	/**
	 * Vanilla's shading of land (MapItem's update): a pixel higher than the one north of it is brighter, lower darker, with
	 * its checkered dither. {@code y} and {@code northY} are block levels, {@code blocksPerPixel} the map's 1 << scale.
	 */
	public static int landShade(int y, int northY, int blocksPerPixel, int px, int pz) {
		double f = (y - northY) * 4.0 / (blocksPerPixel + 4) + (((px + pz) & 1) - 0.5) * 0.4;
		return f > 0.6 ? HIGH : f < -0.6 ? LOW : NORMAL;
	}

	/** Vanilla's shading of water: deeper is darker, with its checkered dither. */
	public static int waterShade(int depth, int px, int pz) {
		double f = depth * 0.1 + ((px + pz) & 1) * 0.2;
		return f < 0.5 ? HIGH : f > 0.9 ? LOW : NORMAL;
	}

	/** The first world column (x or z) of a map pixel, as vanilla's update works it out (its integer division included). */
	public static int columnStart(int center, int blocksPerPixel, int pixel) {
		return (center / blocksPerPixel + pixel - 64) * blocksPerPixel;
	}

	/**
	 * What to draw for one pixel: a tone and brightness (tone null: keep vanilla's colour, at this brightness), or null to
	 * keep vanilla's colour as it is.
	 */
	public record Pixel(@Nullable Tone tone, int brightness) {
	}

	/**
	 * The pixel rule. {@code here}: Skyrim's ground in the pixel's column (null: nothing known). {@code blockY}: the
	 * highest Minecraft block with a map colour in the column, {@link #NO_BLOCK} if none (or none above Skyrim's ground);
	 * {@code vanillaWater}: vanilla coloured the pixel as water. {@code northY}: the block level the column north of it
	 * shows (Skyrim's ground or a block, whichever shows there), {@link #NO_BLOCK} if nothing is known there; {@code
	 * northSkyrim}: it's Skyrim's ground that shows there.
	 *
	 * <p>Skyrim's ground is shaded against whatever shows north of it. A block that wins keeps vanilla's colour, but where
	 * the pixel north of it shows Skyrim's ground (and the map is at 1 block a pixel, where vanilla's height is that one
	 * block's) it's shaded against that ground rather than the void vanilla saw there.
	 */
	public static @Nullable Pixel pixel(@Nullable Ground here, int blockY, boolean vanillaWater, int northY, boolean northSkyrim, int blocksPerPixel,
		int px, int pz) {
		if (here == null) {
			return null;
		}
		if (!skyrimShows(blockY, here)) {
			if (blocksPerPixel == 1 && northSkyrim && northY != NO_BLOCK && !vanillaWater) {
				return new Pixel(null, landShade(blockY, northY, 1, px, pz));
			}
			return null;
		}
		if (here.water()) {
			return new Pixel(Tone.WATER, waterShade(here.waterDepth(), px, pz));
		}
		int y = here.shadeY();
		return new Pixel(here.tone(), landShade(y, northY == NO_BLOCK ? y : northY, blocksPerPixel, px, pz));
	}

	/** The block level a column shows from above for its southern neighbour's shading, or {@link #NO_BLOCK}. */
	public static int shownY(@Nullable Ground ground, int blockY) {
		if (ground != null && skyrimShows(blockY, ground)) {
			return ground.shadeY();
		}
		return blockY;
	}
}
