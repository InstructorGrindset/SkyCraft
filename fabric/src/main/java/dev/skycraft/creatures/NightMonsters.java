package dev.skycraft.creatures;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.deep.DeepRules;
import dev.skycraft.deep.SkyDeep;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyDig;
import dev.skycraft.world.SkyWater;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Night monsters ({@code night_monsters}, off at first): at Skyrim's night, outdoors, away from towns and in Minecraft's dark,
 * Skyrim creatures now and then come out near the player, as Minecraft's monsters do at night (draugr and skeletons near a
 * Nordic barrow's entrance, skeevers and skeletons in the open). Minecraft picks a spot on Skyrim's ground that nobody can see
 * and asks for the creature the way the deep creatures do (EV_SPAWN_CREATURE, Proto.SPAWN_AT_NIGHT): Skyrim places a real
 * actor there, counts it with the deep creatures (one cap for both, never past skycraft-deep.txt's max_alive) and takes it
 * away once the player leaves it behind. Never in interiors. Rules in config/skycraft-night.txt ({@link NightRules}). Host
 * only; server thread only.
 */
public final class NightMonsters {
	private static final String FILE = "skycraft-night.txt";
	/** Columns tried per look. */
	private static final int TRIES = 24;
	/** Blocks over and under the player's feet a column is searched for the ground. */
	private static final int SEARCH_UP = 8, SEARCH_DOWN = 12;
	/** A creature's height in blocks. */
	private static final int BODY = 2;

	private static @Nullable NightRules rules;
	private static final DeepRules.Pacer PACER = new DeepRules.Pacer();
	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	private static final List<SkyLink.Breach> BREACHES = new ArrayList<>();
	private static int world = Integer.MIN_VALUE;
	private static int teleportSeq;
	private static long lastTick = Long.MIN_VALUE;

	private NightMonsters() {
	}

	/** Starts over (the link dropped, or a new server): the first creature waits a cooldown again. */
	public static void reset() {
		PACER.reset();
		world = Integer.MIN_VALUE;
	}

	private static NightRules rules() {
		NightRules r = rules;
		if (r == null) {
			List<String> problems = new ArrayList<>();
			r = NightRules.parse(Config.read(FILE, NightRules.DEFAULT_TEXT), problems);
			for (String p : problems) {
				SkyCraft.LOG.warn("SkyCraft-night: {}: {}", FILE, p);
			}
			SkyCraft.LOG.info("SkyCraft-night: night monsters from {} to {} o'clock, {}% a try, {} alive at most (with the deep creatures', at most {})",
				r.nightFrom(), r.nightTo(), Math.round(r.chance() * 100.0), r.maxAlive(), r.cap(SkyDeep.maxAlive()));
			rules = r;
		}
		return r;
	}

	/** Every server tick while the host is in the mirror. {@code players}: everyone (nobody sees a creature appear). */
	static void tick(ServerPlayer host, List<ServerPlayer> players, long tick) {
		if (!Features.on(Features.Feature.NIGHT_MONSTERS)) {
			return;
		}
		NightRules r = rules();
		if (!r.enabled()) {
			return;
		}
		if (tick < lastTick) {
			reset(); // another server: its ticks start from 0
		}
		lastTick = tick;
		RandomSource random = host.getRandom();
		if (!PACER.started()) {
			PACER.restart(tick, r.cooldownTicks(random.nextDouble()));
			return;
		}
		if (!PACER.shouldLook(tick)) {
			return;
		}
		PACER.looked(tick);
		if (!SkyLink.readSkyState(SKY) || !SKY.inGame() || SKY.loading() || SKY.menuOpen()) {
			return;
		}
		if (SKY.worldId != world || SKY.teleportSeq != teleportSeq) {
			// Another Skyrim world, a load, a door, fast travel: nothing jumps out the moment the player arrives.
			boolean first = world == Integer.MIN_VALUE;
			world = SKY.worldId;
			teleportSeq = SKY.teleportSeq;
			if (!first) {
				PACER.restart(tick, r.cooldownTicks(random.nextDouble()));
				return;
			}
		}
		ServerLevel level = host.level();
		if ((SKY.place & (Proto.PLACE_INTERIOR | Proto.PLACE_TOWN)) != 0 || !r.isNight(SKY.gameHour) || !SkyCollision.active() || !host.isAlive()
			|| host.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		int cap = r.cap(SkyDeep.maxAlive());
		if (SkyDeep.aliveSpawned() >= cap) {
			return;
		}
		if (random.nextDouble() >= r.chance()) {
			PACER.spawned(tick, r.cooldownTicks(random.nextDouble())); // this try brings nothing out
			return;
		}
		BlockPos spot = findSpot(level, host, players, r, random);
		if (spot == null) {
			return; // looked again in a second
		}
		double barrow = nearestBarrow(spot);
		int kind = r.pick(barrow, random.nextInt(1 << 16));
		if (kind == DeepRules.NONE) {
			return;
		}
		SkyLink.pushEventAt(Proto.EV_SPAWN_CREATURE, kind, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, (float) r.despawnDistance(), cap,
			Proto.SPAWN_AT_NIGHT);
		SkyDeep.countSpawn();
		PACER.spawned(tick, r.cooldownTicks(random.nextDouble()));
		SkyCraft.LOG.info("SkyCraft-night: a {} comes out of the night at {} ({} blocks from the player{}, Skyrim hour {})", DeepRules.kindName(kind),
			spot.toShortString(), Math.round(Math.sqrt(host.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5))),
			barrow >= 0.0 && barrow <= r.barrowDistance() ? ", " + Math.round(barrow) + " from a barrow" : "", String.format("%.1f", SKY.gameHour));
	}

	/** A random spot on the ground in range that passes every rule, or null. */
	private static @Nullable BlockPos findSpot(ServerLevel level, ServerPlayer host, List<ServerPlayer> players, NightRules r, RandomSource random) {
		double px = host.getX(), py = host.getY(), pz = host.getZ();
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = r.minDistance() + random.nextDouble() * (r.maxDistance() - r.minDistance());
			int x = (int) Math.floor(px + Math.cos(angle) * distance), z = (int) Math.floor(pz + Math.sin(angle) * distance);
			for (int y = (int) Math.floor(py) + SEARCH_UP; y >= (int) Math.floor(py) - SEARCH_DOWN; y--) {
				BlockPos feet = new BlockPos(x, y, z);
				if (!SkyCollision.isKnown(x, y, z) || !standable(level, feet, r)) {
					continue;
				}
				if (check(level, feet, host, players, r)) {
					return feet;
				}
				break; // the column's ground is no good: another column
			}
		}
		return null;
	}

	/**
	 * Room for a body, in the dark, on something to stand on: Skyrim's own ground (in the feet cell's lower half, or the top of
	 * the cell under it) or a Minecraft block's top, out of Minecraft blocks, fluids and Skyrim's water, and not a cell dug out
	 * of Skyrim's ground (tunnels are the deep creatures').
	 */
	private static boolean standable(ServerLevel level, BlockPos feet, NightRules r) {
		for (int i = 0; i < BODY; i++) {
			BlockPos p = feet.above(i);
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty() || !r.darkEnough(level.getBrightness(LightLayer.BLOCK, p))) {
				return false;
			}
			if (SkyCollision.hasGeometry(p) && (i > 0 || SkyCollision.groundTop(p) > 0.5F)) {
				return false; // Skyrim's rock or a wall in the way
			}
		}
		BlockPos floor = feet.below();
		// Air over Skyrim's ground counts as a sturdy top too (BlockStateBaseMixin), so this is either kind of ground.
		if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
			return false;
		}
		if (SkyWater.anyIn(feet.getX(), feet.getY(), feet.getZ(), feet.getX(), feet.getY() + 1, feet.getZ())) {
			return false;
		}
		return !SkyDig.isDug(level, SKY.worldId, feet);
	}

	/** In range of the host, out of everyone's sight and not right next to anyone. */
	private static boolean check(ServerLevel level, BlockPos feet, ServerPlayer host, List<ServerPlayer> players, NightRules r) {
		for (ServerPlayer player : players) {
			if (player.level() != level || player.isSpectator()) {
				continue;
			}
			double d2 = player.distanceToSqr(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			if (player == host ? !r.inSpawnRange(d2) : d2 < r.minDistance() * r.minDistance()) {
				return false;
			}
			if (SkyDeep.sees(level, player, feet)) {
				return false;
			}
		}
		return true;
	}

	/** Blocks from the spot to the nearest Nordic dungeon entrance Skyrim knows of (barrows, crypts, ruins), or -1. */
	private static double nearestBarrow(BlockPos spot) {
		int breachWorld = SkyLink.readBreaches(BREACHES);
		if (breachWorld != SKY.worldId) {
			return -1.0;
		}
		double best = -1.0;
		for (SkyLink.Breach b : BREACHES) {
			if (b.theme() != Proto.THEME_NORDIC) {
				continue;
			}
			double dx = b.x() - (spot.getX() + 0.5), dy = b.y() - spot.getY(), dz = b.z() - (spot.getZ() + 0.5);
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (best < 0.0 || d < best) {
				best = d;
			}
		}
		return best;
	}
}
