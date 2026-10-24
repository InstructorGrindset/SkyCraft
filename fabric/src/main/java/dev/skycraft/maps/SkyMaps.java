package dev.skycraft.maps;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.link.SkySpace;
import dev.skycraft.mixin.MapItemSavedDataInvoker;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyTri;
import dev.skycraft.world.SkyWater;
import dev.skycraft.worlds.Worlds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/**
 * Minecraft maps draw Skyrim (skyrim_maps). Vanilla's map update (MapItem's, on the server, for a map held in a hand)
 * colours each pixel from the highest block in its column; in the mirror there's none where Skyrim's ground is, so a map
 * there was empty apart from placed blocks. {@link #pixel} sits where vanilla stores a pixel's colour and, where no
 * Minecraft block is above Skyrim's ground, gives the pixel Skyrim's: its highest surface there by {@link MapRules},
 * from the triangles {@link SkyCollision} holds (so a map fills in as the player explores, as vanilla's does), with
 * Skyrim's water where it's known (the water grid round the player, remembered as they go, and Skyrim's land samples), and
 * where Minecraft holds no collision yet, Skyrim's coarse land samples ({@link LandSamples}, asked for here). Never in
 * other dimensions. {@link #beforeUpdate} also puts Skyrim's map markers near the player on the map as named banners.
 *
 * <p>Server thread only. Log: SkyCraft-maps.
 */
public final class SkyMaps {
	/** Collision columns' surfaces worked out per server tick at most (the rest wait for vanilla's next pass). */
	private static final int BUILDS_PER_TICK = 24;
	private static final int MAX_SURFACES = 16384;
	private static final int MAX_SEEN_WATER = 1 << 18;
	/** How far under the highest block in a column a block with a map colour is looked for. */
	private static final int SCAN_DEPTH = 64;
	private static final long SAMPLE_TIMEOUT_MS = 3000;
	private static final long SAMPLE_AGAIN_MS = 30_000;
	private static final double SAMPLE_AGAIN_BLOCKS = 48.0;
	private static final long QUIET_MS = 60_000;

	private record Built(int stamp, MapSurface surface) {
	}

	private static final Map<Long, Built> SURFACES = new HashMap<>();
	private static int surfacesGeneration = Integer.MIN_VALUE;
	private static long buildTick = Long.MIN_VALUE;
	private static int builds;
	private static final Map<Long, Float> SEEN_WATER = new HashMap<>();
	private static long waterTick = Long.MIN_VALUE;
	private static final LandSamples SAMPLES = new LandSamples();
	private static int spaceVersion = Integer.MIN_VALUE;

	// The column worked out last (vanilla goes down each column of pixels north to south, so a pixel's north neighbour is
	// the one before it).
	private static int memoX = Integer.MIN_VALUE, memoZ = Integer.MIN_VALUE;
	private static MapRules.@Nullable Ground memoGround;
	private static int memoBlock;

	/** What each map has had from here: the markers on it, and the land sample asked for over it. */
	private static final class MapState {
		final Map<String, double[]> placed = new HashMap<>();
		int markersSeq = -1;
		long askedAt;
		double askedX, askedZ;
	}

	private static final Map<MapItemSavedData, MapState> MAPS = new WeakHashMap<>();
	private static final List<MapMarkers.Marker> LISTED = new ArrayList<>();
	private static final List<MapMarkers.Marker> READING = new ArrayList<>();
	private static SkyMapLink.@Nullable Listing listing;
	private static int listedSeq = -1;

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	private static int pendingRequest;
	private static long pendingSince;
	private static int nextRequest = 1;
	private static int unanswered;
	private static long quietUntil;
	private static int logged;

	private SkyMaps() {
	}

	/** A map of the mirror, updated in the mirror, with skyrim_maps on. */
	private static boolean drawsSkyrim(Level level, MapItemSavedData data) {
		return data.dimension == Worlds.MIRROR && level.dimension() == Worlds.MIRROR && SkyDimension.inMirror(level)
			&& Features.on(Features.Feature.SKYRIM_MAPS);
	}

	/**
	 * The colour vanilla's update stores for pixel (px, pz) of this map ({@code vanilla}, what it worked out itself): where
	 * Skyrim's ground shows in the pixel's column, Skyrim's colour; vanilla's otherwise.
	 */
	public static byte pixel(Level level, MapItemSavedData data, int px, int pz, byte vanilla) {
		if (!drawsSkyrim(level, data)) {
			return vanilla;
		}
		try {
			newTick(level);
			int bpp = 1 << data.scale;
			int x = MapRules.columnStart(data.centerX, bpp, px) + bpp / 2;
			int z = MapRules.columnStart(data.centerZ, bpp, pz) + bpp / 2;
			// North first: it's the pixel vanilla stored just before this one, still memoised.
			MapRules.Ground north = column(level, x, z - bpp);
			int northBlock = memoBlock;
			MapRules.Ground here = column(level, x, z);
			int block = memoBlock;
			if (here == null) {
				return vanilla;
			}
			int northY = MapRules.shownY(north, northBlock);
			boolean northSkyrim = north != null && MapRules.skyrimShows(northBlock, north);
			boolean vanillaWater = (vanilla & 0xFF) >> 2 == MapColor.WATER.id;
			MapRules.Pixel p = MapRules.pixel(here, block, vanillaWater, northY, northSkyrim, bpp, px, pz);
			if (p == null) {
				return vanilla;
			}
			if (p.tone() == null) {
				return (byte) ((vanilla & 0xFC) | p.brightness());
			}
			return (byte) (colour(p.tone()).id << 2 | p.brightness());
		} catch (RuntimeException e) {
			if (logged++ < 5) {
				SkyCraft.LOG.warn("SkyCraft-maps: couldn't draw Skyrim's ground on a map pixel; vanilla's colour stays", e);
			}
			return vanilla;
		}
	}

	/** Skyrim's ground in a block column and the highest Minecraft block over it ({@link #memoBlock}), memoised for one. */
	private static MapRules.@Nullable Ground column(Level level, int x, int z) {
		if (x == memoX && z == memoZ) {
			return memoGround;
		}
		MapRules.Ground ground = groundAt(x, z);
		memoBlock = topBlock(level, x, z, ground == null ? Integer.MIN_VALUE : ground.shadeY());
		memoX = x;
		memoZ = z;
		memoGround = ground;
		return ground;
	}

	/** Once per server tick: budgets reset, and what no longer holds is dropped. */
	private static void newTick(Level level) {
		long tick = level.getGameTime();
		if (tick == buildTick) {
			return;
		}
		buildTick = tick;
		builds = 0;
		memoX = memoZ = Integer.MIN_VALUE;
		if (SkyCollision.generation() != surfacesGeneration || SURFACES.size() > MAX_SURFACES) {
			surfacesGeneration = SkyCollision.generation();
			SURFACES.clear();
		}
		if (SkySpace.version() != spaceVersion) {
			// Worlds' spaces changed: what was remembered in the mirror's terms may be somewhere else now.
			spaceVersion = SkySpace.version();
			SEEN_WATER.clear();
			SAMPLES.clear();
		}
	}

	/**
	 * Skyrim's ground over a block column: its collision's highest surface there, under Skyrim's water where that's known;
	 * else its land samples; else its water alone; null if none of these.
	 */
	private static MapRules.@Nullable Ground groundAt(int x, int z) {
		MapSurface surface = surface(Math.floorDiv(x, MapSurface.SIZE), Math.floorDiv(z, MapSurface.SIZE));
		double land = surface == null ? Double.NaN : surface.height(x, z);
		if (!Double.isNaN(land)) {
			double water = seenWater(x, z);
			if (Double.isNaN(water)) {
				// A land sample's water over this ground (lakes and rivers beyond the water grid round the player).
				LandSamples.Sample sample = SAMPLES.at(x, z);
				if (sample != null && sample.water() > 0 && sample.height() > land + 0.05) {
					water = sample.height();
				}
			}
			return MapRules.ground(land, surface.tone(x, z), water);
		}
		LandSamples.Sample sample = SAMPLES.at(x, z);
		if (sample != null) {
			return sample.ground();
		}
		return MapRules.ground(Double.NaN, null, seenWater(x, z));
	}

	/** Skyrim's water surface over a column from its water grid (now, or as it was when the player passed), NaN if none. */
	private static double seenWater(int x, int z) {
		double live = SkyWater.surfaceAt(x, z);
		if (!Double.isNaN(live)) {
			return live;
		}
		Float seen = SEEN_WATER.get(columnKey(x, z));
		return seen != null ? seen : Double.NaN;
	}

	/** The surfaces of a column of collision regions, worked out again when its triangles change (within the tick's budget). */
	private static @Nullable MapSurface surface(int rx, int rz) {
		int stamp = SkyCollision.columnStamp(rx, rz);
		if (stamp == 0) {
			return null;
		}
		long key = columnKey(rx, rz);
		Built built = SURFACES.get(key);
		if (built != null && built.stamp() == stamp) {
			return built.surface();
		}
		if (builds >= BUILDS_PER_TICK) {
			return built != null ? built.surface() : null; // the old one until there's time
		}
		builds++;
		List<SkyTri> tris = new ArrayList<>();
		SkyCollision.columnTriangles(rx, rz, tris);
		MapSurface fresh = MapSurface.build(rx * MapSurface.SIZE, rz * MapSurface.SIZE, tris);
		SURFACES.put(key, new Built(stamp, fresh));
		return fresh;
	}

	private static long columnKey(int x, int z) {
		return (long) x << 32 | z & 0xFFFFFFFFL;
	}

	/**
	 * The highest Minecraft block with a map colour in a column, at or above {@code floorY} (Skyrim's ground there: a block
	 * buried under it doesn't show), or MapRules.NO_BLOCK. Vanilla's own test (MapColor.NONE is see-through on a map).
	 */
	private static int topBlock(Level level, int x, int z, int floorY) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1; // the highest block that isn't air
		int bottom = Math.max(Math.max(floorY, level.getMinY()), top - SCAN_DEPTH);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = top; y >= bottom; y--) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && state.getMapColor(level, pos) != MapColor.NONE) {
				return y;
			}
		}
		return MapRules.NO_BLOCK;
	}

	/** The vanilla map colour a tone is drawn in. */
	private static MapColor colour(MapRules.Tone tone) {
		return switch (tone) {
			case GRASS -> MapColor.GRASS;
			case DIRT -> MapColor.DIRT;
			case MUD -> MapColor.TERRACOTTA_CYAN; // vanilla's mud
			case SAND -> MapColor.SAND;
			case SNOW -> MapColor.SNOW;
			case ICE -> MapColor.ICE;
			case ROCK -> MapColor.STONE;
			case ROAD -> MapColor.TERRACOTTA_WHITE;
			case WATER -> MapColor.WATER;
			case BUILDING -> MapColor.COLOR_LIGHT_GRAY;
			case WOOD -> MapColor.WOOD;
			case METAL -> MapColor.METAL;
			case MOSS -> MapColor.COLOR_GREEN; // vanilla's moss
			case CLOTH -> MapColor.COLOR_BROWN;
			case BONE -> MapColor.SAND; // vanilla's bone block
			case ASH -> MapColor.COLOR_LIGHT_GRAY;
		};
	}

	// ---- markers and land samples -------------------------------------------------------------------

	/** At the start of vanilla's update of a held map: Skyrim's markers on it, and Skyrim's land under it asked for. */
	public static void beforeUpdate(Level level, Entity viewer, MapItemSavedData data) {
		if (!(viewer instanceof Player player) || level.isClientSide() || data.dimension != Worlds.MIRROR || level.dimension() != Worlds.MIRROR
			|| !SkyDimension.inMirror(level)) {
			return;
		}
		try {
			newTick(level);
			boolean on = Features.on(Features.Feature.SKYRIM_MAPS);
			MapState state = MAPS.get(data);
			if (state == null) {
				if (!on) {
					return;
				}
				state = new MapState();
				MAPS.put(data, state);
			}
			markers(level, data, state, on);
			if (on) {
				rememberWater(level, player);
				samples(data, state);
			}
		} catch (RuntimeException e) {
			if (logged++ < 5) {
				SkyCraft.LOG.warn("SkyCraft-maps: couldn't update Skyrim's markers or samples on a map", e);
			}
		}
	}

	private static void markers(Level level, MapItemSavedData data, MapState state, boolean on) {
		int seq = SkyMapLink.seq();
		if (seq != listedSeq) {
			SkyMapLink.Listing read = SkyMapLink.readMarkers(READING);
			if (read != null || seq == 0) { // a torn read is tried again next tick
				listedSeq = seq;
				listing = read;
				LISTED.clear();
				LISTED.addAll(READING);
			}
		}
		if (on && state.markersSeq == listedSeq) {
			return;
		}
		SkyMapLink.Listing from = listing != null && listing.world() != 0 ? listing : null;
		if (from == null && on) {
			return; // nothing from Skyrim yet, or its player is indoors: what's on the map stays
		}
		state.markersSeq = listedSeq;
		MapMarkers.Plan plan = MapMarkers.plan(LISTED, from == null ? 0 : from.x(), from == null ? 0 : from.z(), from == null ? 0 : from.reach(),
			state.placed, data.centerX, data.centerZ, 1 << data.scale, on);
		MapItemSavedDataInvoker decorations = (MapItemSavedDataInvoker) data;
		for (MapMarkers.Marker m : plan.add()) {
			String id = MapMarkers.id(m.formId());
			decorations.skycraft$addDecoration(banner(m), level, id, m.x(), m.z(), 180.0, Component.literal(MapMarkers.label(m)));
			state.placed.put(id, new double[] { m.x(), m.z() });
		}
		for (String id : plan.remove()) {
			decorations.skycraft$removeDecoration(id);
			state.placed.remove(id);
		}
		if ((!plan.add().isEmpty() || !plan.remove().isEmpty()) && logged++ < 10) {
			SkyCraft.LOG.info("SkyCraft-maps: Skyrim's markers on a map at {} {}: {} shown, {} taken off", data.centerX, data.centerZ, plan.add().size(),
				plan.remove().size());
		}
	}

	/** A marker's banner: the colour of its kind of place, gray while it's only been heard of. */
	private static Holder<MapDecorationType> banner(MapMarkers.Marker m) {
		if (!m.discovered()) {
			return MapDecorationTypes.LIGHT_GRAY_BANNER;
		}
		return switch (MapMarkers.kind(m.type())) {
			case TOWN -> MapDecorationTypes.YELLOW_BANNER;
			case HOMESTEAD -> MapDecorationTypes.LIME_BANNER;
			case DUNGEON -> MapDecorationTypes.RED_BANNER;
			case CAMP -> MapDecorationTypes.ORANGE_BANNER;
			case SACRED -> MapDecorationTypes.PURPLE_BANNER;
			case LANDMARK -> MapDecorationTypes.WHITE_BANNER;
			case TRAVEL -> MapDecorationTypes.CYAN_BANNER;
		};
	}

	/** Once a tick: Skyrim's water round the player (its water grid) is remembered, so the map keeps it after they move on. */
	private static void rememberWater(Level level, Player player) {
		long tick = level.getGameTime();
		if (tick == waterTick || !SkyWater.active()) {
			return;
		}
		waterTick = tick;
		if (SEEN_WATER.size() > MAX_SEEN_WATER) {
			SEEN_WATER.clear();
		}
		int px = player.getBlockX(), pz = player.getBlockZ();
		int half = Proto.WATER_GRID_SIZE / 2;
		for (int x = px - half; x <= px + half; x++) {
			for (int z = pz - half; z <= pz + half; z++) {
				double s = SkyWater.surfaceAt(x, z);
				if (!Double.isNaN(s)) {
					SEEN_WATER.put(columnKey(x, z), (float) s);
				}
			}
		}
	}

	/** Skyrim's answer to the land sample asked for, taken; and a new one asked for over this map when it needs one. */
	private static void samples(MapItemSavedData data, MapState state) {
		long now = System.currentTimeMillis();
		if (pendingRequest != 0) {
			LandSamples.Patch patch = SkyMapLink.readSamples(pendingRequest);
			if (patch != null) {
				pendingRequest = 0;
				unanswered = 0;
				take(patch);
			} else if (now - pendingSince > SAMPLE_TIMEOUT_MS) {
				pendingRequest = 0;
				if (++unanswered >= 3) {
					quietUntil = now + QUIET_MS;
					unanswered = 0;
					if (logged++ < 10) {
						SkyCraft.LOG.info("SkyCraft-maps: Skyrim isn't answering for its land under maps (an older plugin?); asking again in a minute");
					}
				}
			}
			return;
		}
		// Skyrim answers for the cells loaded round its own player (the host's): the part of the map near there is asked for,
		// again once that player has moved on (or after a while: cells finish loading).
		if (now < quietUntil || !SkyLink.active() || !SkyLink.readSkyState(SKY) || !SKY.inGame() || SKY.loading()) {
			return;
		}
		double dx = SKY.x - state.askedX, dz = SKY.z - state.askedZ;
		if (state.askedAt != 0 && now - state.askedAt < SAMPLE_AGAIN_MS && dx * dx + dz * dz < SAMPLE_AGAIN_BLOCKS * SAMPLE_AGAIN_BLOCKS) {
			return;
		}
		int bpp = 1 << data.scale;
		int mapSize = 128 * bpp;
		int[] square = LandSamples.request(MapRules.columnStart(data.centerX, bpp, 0), MapRules.columnStart(data.centerZ, bpp, 0), mapSize, bpp, SKY.x,
			SKY.z);
		state.askedAt = now;
		state.askedX = SKY.x;
		state.askedZ = SKY.z;
		if (square == null || !SkySpace.inWorld(SkyLink.skyrimWorld(), square[0], square[1])) {
			return; // nothing of this map is near Skyrim's player
		}
		int request = nextRequest++;
		if (nextRequest <= 0) {
			nextRequest = 1;
		}
		pendingRequest = request;
		pendingSince = now;
		SkyLink.pushEventAt(Proto.EV_MAP_SAMPLE, 0, square[0], square[2], square[1], 0.0F, request, 0);
	}

	/** A patch of Skyrim's land samples, kept if its land agrees with the collision Minecraft holds there. */
	private static void take(LandSamples.Patch patch) {
		int known = patch.known();
		if (known == 0) {
			return;
		}
		int n = 0;
		double[] sampled = new double[LandSamples.SIDE * LandSamples.SIDE], exact = new double[sampled.length];
		for (int j = 0; j < LandSamples.SIDE; j++) {
			for (int i = 0; i < LandSamples.SIDE; i++) {
				int x = patch.x0() + i * patch.spacing(), z = patch.z0() + j * patch.spacing();
				double h = patch.heightAt(i, j);
				if (Double.isNaN(h) || patch.water()[j * LandSamples.SIDE + i] != 0) {
					continue;
				}
				MapSurface surface = surface(Math.floorDiv(x, MapSurface.SIZE), Math.floorDiv(z, MapSurface.SIZE));
				if (surface != null && surface.terrain(x, z)) {
					sampled[n] = h;
					exact[n] = surface.height(x, z);
					n++;
				}
			}
		}
		boolean agrees = LandSamples.agrees(sampled, exact, n);
		if (agrees) {
			SAMPLES.add(patch);
		}
		if (logged++ < 10) {
			SkyCraft.LOG.info("SkyCraft-maps: Skyrim's land sampled from {} {} every {} blocks: {} of {} known; {} with its collision at {} points{}", patch.x0(),
				patch.z0(), patch.spacing(), known, LandSamples.SIDE * LandSamples.SIDE, agrees ? "agrees" : "DISAGREES", n, agrees ? "" : ", not used");
		}
	}
}
