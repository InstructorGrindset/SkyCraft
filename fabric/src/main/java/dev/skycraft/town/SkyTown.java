package dev.skycraft.town;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.link.SkySpace;
import dev.skycraft.net.SkyNet;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyDig;
import dev.skycraft.world.SkyTri;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim's towns, Minecraft side: Minecraft doors Skyrim's NPCs open (npc_doors_ladders), griefing a town as a Skyrim
 * crime (town_crime), and building or digging where a guard can see it (guard_comments). Minecraft decides when
 * something happened; Skyrim decides who saw it and does the rest with its own systems (Towns.cpp). The town and its
 * crimes are the host's Skyrim's, so only the host's play is a crime or a guard's business (a guest's blocks are kept
 * as theirs, though). Also the server end of Hearthfire plots (SkyHearthfire) and Dwemer redstone (SkyRedstone).
 *
 * <p>Placing a block is spotted without a mixin: a block item used on a block (Fabric's UseBlockCallback) is
 * remembered with the two cells it could fill, and at the end of the tick a cell that was empty and holds a block now
 * was placed by that player. Server thread.
 */
public final class SkyTown {
	/** A guard this close to the host (blocks) may see them build. */
	private static final double GUARD_REACH = 16.0;
	/** Ticks between two EV_TOWN_BUILD. */
	private static final int BUILD_EVENT_TICKS = 40;

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	private static boolean skyOk;
	private static long skyTick = Long.MIN_VALUE;
	private static @Nullable TownRules rules;
	private static final CrimeTally CRIMES = new CrimeTally();
	private static final DoorTimers DOORS = new DoorTimers();
	private static final List<Use> USES = new ArrayList<>();
	private static final List<SkyLink.Actor> ACTORS = new ArrayList<>();
	private static long lastBuildEvent = Long.MIN_VALUE / 2;
	private static int logged;

	/** A block item used on a block this tick: the cells it could fill, and what they held before. */
	private record Use(ServerPlayer player, ServerLevel level, BlockPos[] cells, BlockState[] before) {
	}

	private SkyTown() {
	}

	public static void init() {
		TownBlocks.init();
		UseBlockCallback.EVENT.register(SkyTown::onUse);
		PlayerBlockBreakEvents.AFTER.register(SkyTown::afterBreak);
		ServerTickEvents.END_SERVER_TICK.register(SkyTown::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SkyRedstone.stop(server);
			SkyHearthfire.save(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CRIMES.clear();
			DOORS.clear();
			USES.clear();
			skyTick = Long.MIN_VALUE;
			SkyHearthfire.stopped();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("skytown").executes(c -> {
			MinecraftServer server = c.getSource().getServer();
			refreshSky(server);
			for (String line : describe(server)) {
				c.getSource().sendSuccess(() -> Component.literal(line), false);
			}
			return 1;
		})));
		TownRules r = rules();
		SkyCraft.LOG.info("SkyCraft-town: doors close {} ticks after their doorway empties; crimes worth {} / {} / {} gold (ground, object, block), "
			+ "batched over {} ticks; Hearthfire stages at {} / {} / {} blocks", r.doorCloseTicks(), r.crimeValue(Proto.CRIME_GROUND),
			r.crimeValue(Proto.CRIME_OBJECT), r.crimeValue(Proto.CRIME_BLOCK), r.crimeBatchTicks(), r.stageBlocks(Proto.STAGE_SMALL_HOUSE),
			r.stageBlocks(Proto.STAGE_MAIN_HALL), r.stageBlocks(Proto.STAGE_WINGS));
	}

	static TownRules rules() {
		TownRules r = rules;
		if (r == null) {
			List<String> problems = new ArrayList<>();
			r = TownRules.parse(Config.read(TownRules.FILE, TownRules.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-town: {} {}", TownRules.FILE, p));
			rules = r;
		}
		return r;
	}

	private static void log(String format, Object... args) {
		if (logged < 60) {
			logged++;
			SkyCraft.LOG.info("SkyCraft-town: " + format, args);
		}
	}

	/** SkyState, read once a tick. */
	private static void refreshSky(MinecraftServer server) {
		long tick = server.getTickCount();
		if (tick != skyTick) {
			skyTick = tick;
			skyOk = SkyLink.readSkyState(SKY) && SKY.inGame() && !SKY.loading();
		}
	}

	/** The host's Skyrim player is in a town (a city, town or settlement, or a building in one). */
	private static boolean inTown() {
		return skyOk && (SKY.place & Proto.PLACE_TOWN) != 0;
	}

	/** The host (the player whose Skyrim this is), or null. */
	static @Nullable ServerPlayer host(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (SkyNet.isHost(p)) {
				return p;
			}
		}
		return null;
	}

	// ---- blocks placed and broken ----------------------------------------------------------------

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp) || player.isSpectator()
			|| !SkyDimension.inMirror(level) || !(player.getItemInHand(hand).getItem() instanceof BlockItem) || USES.size() >= 64) {
			return InteractionResult.PASS;
		}
		BlockPos at = hit.getBlockPos();
		BlockPos[] cells = { at, at.relative(hit.getDirection()) };
		USES.add(new Use(sp, serverLevel, cells, new BlockState[] { level.getBlockState(cells[0]), level.getBlockState(cells[1]) }));
		return InteractionResult.PASS;
	}

	/** End of the tick: which of this tick's block item uses placed a block, and where. */
	private static void spotPlacements() {
		for (Use u : USES) {
			for (int i = 0; i < u.cells().length; i++) {
				BlockPos pos = u.cells()[i];
				BlockState before = u.before()[i];
				BlockState now = u.level().getBlockState(pos);
				if (now == before || now.isAir() || !(before.isAir() || before.canBeReplaced())) {
					continue;
				}
				placed(u.player(), u.level(), pos);
				if (now.getBlock() instanceof DoorBlock && now.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					placed(u.player(), u.level(), pos.above()); // its upper half came with it
				}
			}
		}
		USES.clear();
	}

	private static void placed(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		refreshSky(server);
		if (inTown() && Features.on(Features.Feature.TOWN_CRIME)) {
			TownBlocks.placed(level, pos, player.getUUID().toString());
		}
		SkyHearthfire.placed(player, level, pos, SKY, skyOk, rules());
		if (inTown() && SkyNet.isHost(player)) {
			building(player, pos, Proto.BUILD_PLACE);
		}
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp) || !SkyDimension.inMirror(level)) {
			return;
		}
		MinecraftServer server = serverLevel.getServer();
		refreshSky(server);
		SkyHearthfire.broken(serverLevel, pos, rules());
		String owner = TownBlocks.take(serverLevel, pos);
		if (state.getBlock() instanceof DoorBlock) {
			BlockPos other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
			TownBlocks.take(serverLevel, other); // the other half went with it
			SkyHearthfire.broken(serverLevel, other, rules());
		}
		if (!inTown() || !SkyNet.isHost(sp) || sp.isSpectator()) {
			return;
		}
		building(sp, pos, Proto.BUILD_BREAK);
		if (!Features.on(Features.Feature.TOWN_CRIME)) {
			return;
		}
		int kind = TownRules.brokenBlockCrime(sp.getUUID().toString(), owner, SkyDig.isDug(serverLevel, SKY.worldId, pos));
		if (kind != 0) {
			crime(server, kind, pos, owner != null ? "a block someone else placed" : "the town's ground");
		}
	}

	/**
	 * SkyDig.open: a player dug Skyrim's own geometry out of this cell. In a town that's griefing: its ground, or an object
	 * (a building, wall, rock, tree) if any of what was dug there isn't Skyrim's land.
	 */
	public static void dugSkyrim(ServerPlayer player, BlockPos pos) {
		MinecraftServer server = player.level().getServer();
		refreshSky(server);
		if (!inTown() || !SkyNet.isHost(player)) {
			return;
		}
		building(player, pos, Proto.BUILD_DIG);
		if (!Features.on(Features.Feature.TOWN_CRIME)) {
			return;
		}
		List<SkyTri> tris = new ArrayList<>();
		SkyCollision.originalSurfacesNear(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1), tris);
		boolean object = false;
		for (SkyTri t : tris) {
			boolean inCell = t.maxX >= pos.getX() && t.minX <= pos.getX() + 1 && t.maxY >= pos.getY() && t.minY <= pos.getY() + 1 && t.maxZ >= pos.getZ()
				&& t.minZ <= pos.getZ() + 1;
			if (inCell && t.diggable && !t.terrain) {
				object = true;
				break;
			}
		}
		crime(server, TownRules.digCrime(object), pos, object ? "a Skyrim object" : "Skyrim's ground");
	}

	private static void crime(MinecraftServer server, int kind, BlockPos pos, String what) {
		int value = rules().crimeValue(kind);
		if (value <= 0) {
			return;
		}
		CRIMES.add(kind, value, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, server.getTickCount());
		log("griefing {} in {}: {} at {} {} {} ({} gold)", SKY.town, what, kind, pos.getX(), pos.getY(), pos.getZ(), value);
	}

	/** The host placed, broke or dug blocks in town: a guard who sees it may say something (Skyrim decides). */
	private static void building(ServerPlayer host, BlockPos pos, int kind) {
		MinecraftServer server = host.level().getServer();
		long now = server.getTickCount();
		if (!Features.on(Features.Feature.GUARD_COMMENTS) || now - lastBuildEvent < BUILD_EVENT_TICKS || !guardNear(host)) {
			return;
		}
		lastBuildEvent = now;
		SkyLink.pushEventAt(Proto.EV_TOWN_BUILD, 0, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0F, kind, 0);
	}

	private static boolean guardNear(ServerPlayer host) {
		if (!SkyLink.readActors(ACTORS)) {
			return false;
		}
		for (SkyLink.Actor a : ACTORS) {
			if ((a.flags() & Proto.ACTOR_GUARD) != 0 && !a.dead() && host.distanceToSqr(a.x(), a.y(), a.z()) <= GUARD_REACH * GUARD_REACH) {
				return true;
			}
		}
		return false;
	}

	// ---- doors Skyrim's NPCs open ----------------------------------------------------------------

	/**
	 * IN_OPEN_DOOR: an NPC walked into the closed door at this block (Skyrim world {@code world}'s terms). Any door but
	 * an iron one opens, the way a villager opens it; it closes again once nobody has been in its doorway for a while.
	 */
	public static void npcOpensDoor(MinecraftServer server, UUID host, int world, int x, int y, int z) {
		if (!Features.on(Features.Feature.NPC_DOORS_LADDERS)) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(host);
		if (player == null || !SkyDimension.inMirror(player.level())) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos pos = new BlockPos(x + SkySpace.originX(world), y, z + SkySpace.originZ(world));
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below();
			state = level.getBlockState(pos);
		}
		if (!(state.getBlock() instanceof DoorBlock door) || state.is(Blocks.IRON_DOOR)) {
			return;
		}
		long now = server.getTickCount();
		if (state.getValue(DoorBlock.OPEN)) {
			DOORS.occupied(pos.asLong(), now); // already open (Skyrim heard late): keep it open a while
			return;
		}
		door.setOpen(null, level, state, pos, true);
		DOORS.opened(pos.asLong(), now);
		log("a Skyrim NPC opened the door at {} {} {}", pos.getX(), pos.getY(), pos.getZ());
	}

	/** Doors NPCs opened stay open while anyone (a player, an NPC's stand-in, a mob) is in the doorway, then close. */
	private static void tickDoors(MinecraftServer server, long now) {
		if (DOORS.size() == 0 || now % 5 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (long key : DOORS.keys()) {
			BlockPos pos = BlockPos.of(key);
			BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
			if (state == null || !(state.getBlock() instanceof DoorBlock) || !state.getValue(DoorBlock.OPEN)) {
				DOORS.forget(key); // gone, closed by someone, or out of reach: not ours to close
				continue;
			}
			AABB doorway = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1).inflate(0.15);
			for (Entity e : level.getEntities((Entity) null, doorway)) {
				if (e instanceof LivingEntity && !e.isSpectator()) {
					DOORS.occupied(key, now);
					break;
				}
			}
		}
		for (long key : DOORS.due(now, rules().doorCloseTicks())) {
			BlockPos pos = BlockPos.of(key);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.OPEN)) {
				door.setOpen(null, level, state, pos, false);
				log("closed the door at {} {} {} behind the NPCs", pos.getX(), pos.getY(), pos.getZ());
			}
		}
	}

	// ---- the tick --------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		spotPlacements();
		refreshSky(server);
		for (CrimeTally.Batch b : CRIMES.due(now, rules().crimeBatchTicks())) {
			if (Features.on(Features.Feature.TOWN_CRIME)) {
				SkyLink.pushEventAt(Proto.EV_TOWN_CRIME, b.kind(), b.x(), b.y(), b.z(), b.value(), b.count(), 0);
				log("told Skyrim of griefing {} (kind {}, {} block(s), {} gold); Skyrim decides who saw it", SKY.town, b.kind(), b.count(), b.value());
			}
		}
		tickDoors(server, now);
		SkyRedstone.tick(server, now);
		SkyHearthfire.tick(server, SKY, skyOk, now);
	}

	private static List<String> describe(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		if (!skyOk) {
			out.add("Town: no Skyrim game to ask");
		} else {
			ServerPlayer host = host(server);
			out.add("Town: " + (SKY.town.isBlank() ? "(nowhere named)" : SKY.town) + (inTown() ? ", a town" : ", not a town") + " (place flags " + SKY.place
				+ ", town flags " + SKY.townFlags + "); guard near the host: " + (host != null && guardNear(host) ? "yes" : "no"));
		}
		out.add("Crime: " + (Features.on(Features.Feature.TOWN_CRIME) ? CRIMES.pending() + " block(s) of griefing waiting to be told" : "switched off (town_crime)"));
		out.add("Doors NPCs opened and still open: " + DOORS.size() + ((SKY.townFlags & Proto.TOWN_PASSAGES) != 0 ? "" : " (this Skyrim plugin doesn't read doors and ladders)"));
		out.add(SkyHearthfire.describe(server, SKY, skyOk, rules()));
		out.add(SkyRedstone.describe());
		return out;
	}
}
