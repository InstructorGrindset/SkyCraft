package dev.skycraft.weather;

import static dev.skycraft.link.Proto.PLACE_INTERIOR;
import static dev.skycraft.link.Proto.WEATHER_CLEAR;
import static dev.skycraft.link.Proto.WEATHER_RAINING;
import static dev.skycraft.link.Proto.WEATHER_SNOWING;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import dev.skycraft.world.SkyClip;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Weather both ways (switch {@code weather_sync}, rules in {@link WeatherRules}), server side, for the host.
 *
 * <p>Once a second Minecraft's weather follows Skyrim's (SkyState weather): the mirror's sky darkens under Skyrim's clouds
 * and rain, and Tamriel Wilds and Home share the mirror's weather (their levels take the overworld's), so it rains and
 * snows there the Minecraft way while Skyrim's does. The mirror itself never shows Minecraft's own rain or snow: its only
 * biome is minecraft:the_void, which has no precipitation (so no rain particles, no fire put out, no lightning strikes,
 * no snow of its own). Snow settling makes up for it: while snow falls in Skyrim, layers settle on the tops of Minecraft
 * blocks open to the sky near the host, and melt in clear weather.
 *
 * <p>A change of Minecraft's weather SkyCraft didn't make (/weather, a mod) goes to Skyrim as a request in McState
 * ({@link #request}); Skyrim switches to the nearest weather of its region and says so in SkyState weatherAck.
 */
public final class SkyWeather {
	private static final String FILE = "skycraft-weather.txt";
	/** How far above or below the host a block top can be to get snow (blocks). */
	private static final int REACH_UP_DOWN = 24;
	/** How far up Skyrim's geometry is looked for over a block (a roof, a bridge, a cliff over it). */
	private static final double SKY_REACH = 48.0;

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	private static final WeatherRules.Watch WATCH = new WeatherRules.Watch();
	private static final WeatherRules.Asks ASKS = new WeatherRules.Asks((int) System.nanoTime() | 1);
	/** {@link WeatherRules.Asks#packed} for McState, written on the server thread and read on the client's. */
	private static volatile long request = ASKS.packed();
	private static WeatherRules.@Nullable Settings settings;
	private static int logged;
	private static int settled, melted, watered;

	private SkyWeather() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SkyWeather::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	/**
	 * McState's weather request (SkyLink.writeMcState): the request's number in the low 32 bits, the Proto.WEATHER_* kind
	 * asked for above them (WEATHER_NONE: none open). Any thread.
	 */
	public static long request() {
		return request;
	}

	private static void reset() {
		WATCH.reset();
		ASKS.reset();
		request = ASKS.packed();
	}

	private static WeatherRules.Settings settings() {
		WeatherRules.Settings s = settings;
		if (s == null) {
			List<String> problems = new ArrayList<>();
			s = WeatherRules.parse(Config.read(FILE, WeatherRules.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-weather: {} {}", FILE, p));
			SkyCraft.LOG.info("SkyCraft-weather: weather sync {}; snow settles every {} ticks on {} columns within {} blocks (chance {}, up to {} layers, melts {})",
				Features.on(Features.Feature.WEATHER_SYNC) ? "on" : "off", s.settleEvery(), s.columns(), s.radius(), s.settleChance(), s.maxLayers(), s.meltChance());
			settings = s;
		}
		return s;
	}

	private static void tick(MinecraftServer server) {
		if (!Features.on(Features.Feature.WEATHER_SYNC) || !SkyLink.active()) {
			return;
		}
		ServerPlayer host = null;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (SkyNet.isHost(player)) {
				host = player;
			}
		}
		if (host == null) {
			return;
		}
		int tick = server.getTickCount();
		WeatherRules.Settings s = settings();
		if (tick % 20 == 0) {
			sync(server, host, tick);
		}
		if (tick % s.settleEvery() == 0) {
			settle(host, s);
		}
	}

	/** Minecraft's weather now: thunder, rain or clear, as its level shows it (after the fade). */
	private static int observed(ServerLevel level) {
		return level.isThundering() ? WeatherRules.MC_THUNDER : level.isRaining() ? WeatherRules.MC_RAIN : WeatherRules.MC_CLEAR;
	}

	private static void sync(MinecraftServer server, ServerPlayer host, long now) {
		if (!SkyLink.readSkyState(SKY) || !SKY.inGame() || SKY.loading()) {
			return;
		}
		ServerLevel level = server.overworld();
		switch (ASKS.check(SKY.weatherAck, SKY.weatherFlags, now)) {
			case WeatherRules.Asks.ANSWERED -> {
				request = ASKS.packed();
				int got = WeatherRules.minecraftFor(SKY.weather);
				if (got != WeatherRules.UNKNOWN && got != WATCH.expected()) {
					host.sendOverlayMessage(Component.literal("Skyrim has no " + WeatherRules.kindName(ASKS.want()) + " here: " + WeatherRules.kindName(SKY.weather) + " instead"));
				}
				SkyCraft.LOG.info("SkyCraft-weather: Skyrim switched to {} for Minecraft's {}", WeatherRules.kindName(SKY.weather), WeatherRules.kindName(ASKS.want()));
				return; // its sky follows from the next look on
			}
			case WeatherRules.Asks.REFUSED -> {
				request = ASKS.packed();
				host.sendOverlayMessage(Component.literal("Skyrim's sky can't turn to " + WeatherRules.kindName(ASKS.want()) + " here, so Minecraft's follows Skyrim's"));
				SkyCraft.LOG.info("SkyCraft-weather: Skyrim has no {} for Minecraft here (indoors, held by a quest, or none in the region)",
					WeatherRules.kindName(ASKS.want()));
				return;
			}
			case WeatherRules.Asks.TIMED_OUT -> {
				request = ASKS.packed();
				SkyCraft.LOG.info("SkyCraft-weather: Skyrim never answered the request for {} (an older plugin?); following Skyrim's weather again",
					WeatherRules.kindName(ASKS.want()));
			}
			default -> {
			}
		}
		int someone = WATCH.observe(observed(level), now);
		if (someone != WeatherRules.UNKNOWN) {
			int want = WeatherRules.skyrimFor(someone);
			int seq = ASKS.open(want, now);
			request = ASKS.packed();
			SkyCraft.LOG.info("SkyCraft-weather: Minecraft's weather turned to {} (not SkyCraft's doing): asking Skyrim for {} (request {})",
				WeatherRules.command(someone), WeatherRules.kindName(want), Integer.toUnsignedString(seq));
			return;
		}
		if (ASKS.waiting()) {
			return; // Minecraft keeps the player's weather until Skyrim answers
		}
		int target = WeatherRules.minecraftFor(SKY.weather);
		if (target == WeatherRules.UNKNOWN || target == WATCH.expected()) {
			return;
		}
		// "weather" sets the command level's weather: the overworld's is the mirror's, which Tamriel Wilds and Home share.
		var source = server.createCommandSourceStack().withSuppressedOutput().withLevel(level);
		server.getCommands().performPrefixedCommand(source, "weather " + WeatherRules.command(target));
		WATCH.set(target, now);
		if (logged++ < 40) {
			SkyCraft.LOG.info("SkyCraft-weather: Minecraft's weather set to {} for Skyrim's {}", WeatherRules.command(target), WeatherRules.kindName(SKY.weather));
		}
	}

	/**
	 * One pass of snow settling, melting and watering near the host in the mirror: random columns within the radius, each at
	 * most once. Only block tops that are Minecraft's (never Skyrim's own ground) and open to the sky, Minecraft's and
	 * Skyrim's both (no block over them, no Skyrim roof, bridge or cliff), outdoors in Skyrim.
	 */
	private static void settle(ServerPlayer host, WeatherRules.Settings s) {
		if (!SkyDimension.inMirror(host) || !SkyLink.readSkyState(SKY) || !SKY.inGame() || SKY.loading() || (SKY.place & PLACE_INTERIOR) != 0) {
			return;
		}
		boolean snowing = (SKY.weatherFlags & WEATHER_SNOWING) != 0;
		boolean raining = (SKY.weatherFlags & WEATHER_RAINING) != 0;
		boolean clear = SKY.weather == WEATHER_CLEAR && !snowing && !raining;
		boolean waters = raining && s.rainWaters();
		if (!snowing && !waters && !(clear && s.meltChance() > 0.0)) {
			return;
		}
		ServerLevel level = host.level();
		RandomSource random = host.getRandom();
		LongOpenHashSet looked = new LongOpenHashSet();
		int px = host.getBlockX(), py = host.getBlockY(), pz = host.getBlockZ(), r = s.radius();
		for (int i = 0; i < s.columns(); i++) {
			int dx = random.nextInt(2 * r + 1) - r, dz = random.nextInt(2 * r + 1) - r;
			if (dx * dx + dz * dz > r * r || !looked.add(BlockPos.asLong(px + dx, 0, pz + dz))) {
				continue; // one layer a column a pass
			}
			int x = px + dx, z = pz + dz;
			BlockPos cell = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
			if (Math.abs(cell.getY() - py) > REACH_UP_DOWN || !level.isLoaded(cell)) {
				continue; // nothing built in this column near the host
			}
			if (level.getBlockState(cell.below()).is(Blocks.SNOW)) {
				cell = cell.below();
			}
			BlockState here = level.getBlockState(cell);
			BlockState below = level.getBlockState(cell.below());
			if (below.isAir() || below.is(Blocks.SNOW) || underSkyrim(cell)) {
				continue;
			}
			if (waters && below.is(Blocks.FARMLAND) && below.hasProperty(BlockStateProperties.MOISTURE) && below.getValue(BlockStateProperties.MOISTURE) < 7) {
				level.setBlock(cell.below(), below.setValue(BlockStateProperties.MOISTURE, 7), 2);
				watered++;
			}
			int layers = here.is(Blocks.SNOW) ? here.getValue(BlockStateProperties.LAYERS) : here.isAir() ? 0 : -1;
			if (layers < 0) {
				continue; // a flower, a torch, a rail: something of Minecraft's is there
			}
			int next = WeatherRules.nextLayers(layers, snowing, clear, random.nextDouble(), s);
			if (next == layers) {
				continue;
			}
			if (next == 0) {
				level.removeBlock(cell, false);
				melted++;
			} else if (layers == 0) {
				BlockState snow = Blocks.SNOW.defaultBlockState();
				if (snow.canSurvive(level, cell)) {
					level.setBlock(cell, snow, 3);
					settled++;
				}
			} else {
				level.setBlock(cell, here.setValue(BlockStateProperties.LAYERS, next), 3);
				if (next > layers) {
					settled++;
				} else {
					melted++;
				}
			}
		}
		if (settled + melted + watered >= 100) {
			SkyCraft.LOG.info("SkyCraft-weather: {} snow layers settled, {} melted, {} farmland watered by Skyrim's weather", settled, melted, watered);
			settled = melted = watered = 0;
		}
	}

	/** Whether Skyrim's geometry is over this cell (a roof, a bridge, a cliff, the ground over a buried block). */
	private static boolean underSkyrim(BlockPos cell) {
		Vec3 from = new Vec3(cell.getX() + 0.5, cell.getY() + 0.05, cell.getZ() + 0.5);
		return SkyClip.cast(from, from.add(0.0, SKY_REACH, 0.0)) != null;
	}
}
