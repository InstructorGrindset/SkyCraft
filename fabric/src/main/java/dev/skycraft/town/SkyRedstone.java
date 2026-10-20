package dev.skycraft.town;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.link.SkySpace;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Dwemer redstone (dwemer_redstone), Minecraft side.
 * <ul>
 * <li>A Skyrim lever, button or pressure plate used (IN_REDSTONE_PULSE) powers its block for a moment: vanilla has no
 * invisible power source, so a redstone block stands in that cell while the pulse lasts (only if the cell is empty),
 * then goes. The host's own Skyrim is told not to draw it ({@link #hidden}); a guest's Skyrim shows it for that moment.</li>
 * <li>Skyrim names the Dwemer doors near its player (IN_DWEMER_DOOR: the block at each one's foot). Redstone power at
 * that block or the one above, as a Minecraft iron door would feel it, is watched (RedstoneDoors): when it comes Skyrim
 * opens the door, when it goes Skyrim closes it (EV_REDSTONE_DOOR).</li>
 * </ul>
 * Server thread, except {@link #hidden}.
 */
public final class SkyRedstone {
	/** Ticks a Dwemer door Skyrim stops naming is remembered (Skyrim names each one every 2 s). */
	private static final int DOOR_FORGET_TICKS = 120;
	private static final int MAX_PULSE_TICKS = 100;

	private static final PulseTimers PULSES = new PulseTimers();
	private static final Set<Long> HIDDEN = ConcurrentHashMap.newKeySet();
	private static final RedstoneDoors DOORS = new RedstoneDoors();
	private static int logged;

	private SkyRedstone() {
	}

	private static boolean on() {
		return Features.on(Features.Feature.DWEMER_REDSTONE);
	}

	/**
	 * Whether this block (BlockPos.asLong) is a pulse's stand-in, which the client's exporter leaves out of what Skyrim
	 * draws (the lever is there in Skyrim). Any thread; cheap when no pulse is on.
	 */
	public static boolean hidden(long pos) {
		return !HIDDEN.isEmpty() && HIDDEN.contains(pos);
	}

	private static void log(String format, Object... args) {
		if (logged < 40) {
			logged++;
			SkyCraft.LOG.info("SkyCraft-town: " + format, args);
		}
	}

	/** The mirror level the host plays in, or null (no host, or the host is in another dimension). */
	private static ServerLevel mirror(MinecraftServer server, UUID host) {
		ServerPlayer player = server.getPlayerList().getPlayer(host);
		return player != null && SkyDimension.inMirror(player.level()) ? player.level() : null;
	}

	/** IN_REDSTONE_PULSE: a Skyrim lever, button or plate at this block (Skyrim world {@code world}'s terms) was used. */
	public static void pulse(MinecraftServer server, UUID host, int world, int x, int y, int z, int ticks) {
		ServerLevel level = mirror(server, host);
		if (!on() || level == null) {
			return;
		}
		BlockPos pos = new BlockPos(x + SkySpace.originX(world), y, z + SkySpace.originZ(world));
		if (!level.isLoaded(pos)) {
			return;
		}
		long now = server.getTickCount();
		long key = pos.asLong();
		long end = now + Math.clamp(ticks, 2, MAX_PULSE_TICKS);
		if (PULSES.has(key)) {
			PULSES.start(key, end); // used again while it's on: on for longer
			return;
		}
		if (!level.getBlockState(pos).isAir()) {
			log("a Skyrim switch at {} {} {} was used, but a block is in its place: no pulse", pos.getX(), pos.getY(), pos.getZ());
			return;
		}
		if (!level.getEntities((Entity) null, new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)).isEmpty()) {
			log("a Skyrim switch at {} {} {} was used, but someone stands in its place: no pulse", pos.getX(), pos.getY(), pos.getZ());
			return;
		}
		HIDDEN.add(key); // before the block, so the client never draws it
		level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
		PULSES.start(key, end);
		log("a Skyrim switch powers {} {} {} for {} ticks", pos.getX(), pos.getY(), pos.getZ(), end - now);
	}

	/** IN_DWEMER_DOOR: a Dwemer door's foot is at this block (Skyrim world {@code world}'s terms). */
	public static void door(MinecraftServer server, int world, int x, int y, int z, boolean open) {
		if (!on()) {
			return;
		}
		DOORS.seen(x + SkySpace.originX(world), y, z + SkySpace.originZ(world), open, server.getTickCount());
	}

	/** Every tick: pulses that are over go; every other tick, the power at Dwemer doors is looked at. */
	static void tick(MinecraftServer server, long now) {
		ServerLevel mirror = server.overworld();
		for (long key : PULSES.due(now)) {
			end(mirror, key);
		}
		if (now % 2 != 0 || DOORS.size() == 0) {
			return;
		}
		if (!on()) {
			DOORS.clear();
			return;
		}
		List<RedstoneDoors.Change> changes = DOORS.update(now, DOOR_FORGET_TICKS, (x, y, z) -> {
			BlockPos pos = new BlockPos(x, y, z);
			if (!mirror.isLoaded(pos) || !mirror.isLoaded(pos.above())) {
				return null;
			}
			return mirror.hasNeighborSignal(pos) || mirror.hasNeighborSignal(pos.above());
		});
		for (RedstoneDoors.Change c : changes) {
			SkyLink.pushEventAt(Proto.EV_REDSTONE_DOOR, 0, c.x() + 0.5, c.y() + 0.5, c.z() + 0.5, 0.0F, c.powered() ? 1 : 0, 0);
			log("redstone power {} the Dwemer door at {} {} {}", c.powered() ? "reached" : "left", c.x(), c.y(), c.z());
		}
	}

	private static void end(ServerLevel level, long key) {
		BlockPos pos = BlockPos.of(key);
		if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
		HIDDEN.remove(key);
	}

	/** The server is stopping: no stand-in block may stay behind in the world. */
	static void stop(MinecraftServer server) {
		ServerLevel mirror = server.overworld();
		for (long key : PULSES.all()) {
			end(mirror, key);
		}
		HIDDEN.clear();
		DOORS.clear();
	}

	/** For /skytown. */
	static String describe() {
		if (!on()) {
			return "Dwemer redstone: switched off (dwemer_redstone)";
		}
		StringBuilder sb = new StringBuilder("Dwemer redstone: ").append(PULSES.size()).append(" pulse(s) on; ").append(DOORS.size()).append(" Dwemer door(s) near");
		for (int[] d : DOORS.all()) {
			sb.append(String.format(" [%d %d %d %s, %s]", d[0], d[1], d[2], d[3] == 1 ? "open" : "closed", d[4] == 1 ? "powered" : d[4] == 0 ? "unpowered" : "?"));
		}
		return sb.toString();
	}
}
