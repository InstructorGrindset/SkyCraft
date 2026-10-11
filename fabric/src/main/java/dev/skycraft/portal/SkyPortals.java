package dev.skycraft.portal;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import dev.skycraft.portal.PortalStore.Portal;
import dev.skycraft.world.SkyCollision;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The portal network, Minecraft side. Portals are cracks between places that resemble each other:
 * an obsidian frame lit in the Skyrim mirror world makes real Nether portal blocks (vanilla lights
 * them there, since the mirror is the overworld dimension), but stepping in goes nowhere until the
 * portal is bound to exactly one other portal, for good. A filled soul gem used on a lit portal
 * attunes to it (the gem becomes an attuned soul gem that remembers it); the attuned gem used on a
 * second lit portal binds the two and is spent.
 *
 * <p>Vanilla Nether travel never happens from the mirror world: mixin.NetherPortalBlockMixin hands
 * {@link #reach} the moment Minecraft's usual portal wait is over and returns no destination. For the
 * host, a bound portal then sends EV_PORTAL_TRAVEL and Skyrim moves its player to the partner behind
 * a loading screen, like a load door (Travel.cpp); Minecraft follows Skyrim's world change as it
 * always does (the teleport resync), so the player arrives just in front of the partner on its exit
 * side. Portals a few blocks apart in the same Skyrim world are a plain Minecraft teleport instead.
 *
 * <p>The network is kept in the world's {@code skycraft-portals.txt} (PortalStore), keyed by Skyrim
 * world and Minecraft position, since Skyrim's worldspaces and interiors share one set of Minecraft
 * coordinates. Portals in interiors work too. Breaking a frame (or the portal going out any other
 * way) takes it out of the network and unbinds its partner. Server thread only. Log tag
 * {@code SkyCraft-portal}.
 */
public final class SkyPortals {
	private static final String FILE = "skycraft-portals.txt";
	/** Same Skyrim world and closer than this: Minecraft moves the player itself (no loading screen). */
	static final double LOCAL_HOP = 16.0;
	/** One trip at most this often (a loading screen follows each). */
	private static final long TRIP_GAP_NANOS = 3_000_000_000L;
	/** Skyrim not moving the player within this long means it refused. */
	private static final long PENDING_NANOS = 8_000_000_000L;
	/** Portals this close to a player (blocks, flat) are checked now and then for having gone out. */
	private static final double CHECK_RANGE = 64.0;
	private static final int HINT_REPEAT_TICKS = 60;

	private static final String KEY_ID = "skycraft_portal";
	private static final String KEY_WORLD = "skycraft_portal_world";
	private static final String KEY_X = "skycraft_portal_x";
	private static final String KEY_Y = "skycraft_portal_y";
	private static final String KEY_Z = "skycraft_portal_z";
	static final String KEY_PLACE = "skycraft_portal_place";

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	private static @Nullable MinecraftServer storeServer;
	private static @Nullable PortalStore store;
	private static long lastTrip;
	private static boolean trippedOnce;
	/** The portal rectangle the host stands in (its min corner and axis), 0 when none; and ticks since its hint. */
	private static long hintShape;
	private static int hintAge;

	/** A trip the host asked Skyrim for that hasn't started yet (Skyrim can refuse silently). */
	private record Pending(int from, int to, String place, UUID player, long since, int worldId, int teleportSeq, int epoch) {
	}

	private static @Nullable Pending pending;

	/** A lit portal's rectangle of portal blocks, as found in the world. */
	record Shape(int x, int y, int z, int axis, int width, int height) {
	}

	private SkyPortals() {
	}

	public static void init() {
		PortalItems.init();
		ServerTickEvents.END_SERVER_TICK.register(SkyPortals::tick);
		PlayerBlockBreakEvents.AFTER.register(SkyPortals::afterBreak);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			store = null;
			storeServer = null;
			pending = null;
			hintShape = 0;
		});
	}

	private static boolean on() {
		return Features.on(Features.Feature.PORTALS);
	}

	/** Whether Nether portals in this level belong to the network (the Skyrim mirror, Minecraft's overworld dimension). */
	public static boolean intercepts(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}

	// ---- store --------------------------------------------------------------------------------

	private static PortalStore store(MinecraftServer server) {
		PortalStore s = store;
		if (s == null || storeServer != server) {
			s = load(server);
			store = s;
			storeServer = server;
		}
		return s;
	}

	private static Path path(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE).normalize();
	}

	private static PortalStore load(MinecraftServer server) {
		Path path = path(server);
		if (!Files.exists(path)) {
			return new PortalStore();
		}
		try {
			List<String> problems = new ArrayList<>();
			PortalStore s = PortalStore.parse(Files.readAllLines(path, StandardCharsets.UTF_8), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-portal: {} {}", path, p));
			SkyCraft.LOG.info("SkyCraft-portal: {} portals known in this world", s.size());
			return s;
		} catch (IOException e) {
			SkyCraft.LOG.warn("SkyCraft-portal: couldn't read {}: {}", path, e.toString());
			return new PortalStore();
		}
	}

	private static void save(MinecraftServer server, PortalStore s) {
		Path path = path(server);
		Path tmp = path.resolveSibling(FILE + ".tmp");
		try {
			Files.write(tmp, s.lines(), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			SkyCraft.LOG.warn("SkyCraft-portal: couldn't write {}: {}", path, e.toString());
		}
	}

	// ---- shapes and names ---------------------------------------------------------------------

	/** The lit portal this block belongs to, or null if it isn't a portal block. */
	static @Nullable Shape shapeAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.NETHER_PORTAL)) {
			return null;
		}
		Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
		BlockUtil.FoundRectangle r = BlockUtil.getLargestRectangleAround(pos, axis, PortalStore.MAX_SIZE, Direction.Axis.Y, PortalStore.MAX_SIZE,
			p -> level.getBlockState(p) == state);
		return new Shape(r.minCorner.getX(), r.minCorner.getY(), r.minCorner.getZ(), axis == Direction.Axis.X ? PortalStore.AXIS_X : PortalStore.AXIS_Z,
			r.axis1Size, r.axis2Size);
	}

	/** The network's record of exactly this lit portal in this Skyrim world, or null. */
	private static @Nullable Portal recordOf(PortalStore s, int world, Shape shape) {
		Portal p = s.at(world, shape.x(), shape.y(), shape.z());
		return p != null && p.sameShape(shape.x(), shape.y(), shape.z(), shape.axis(), shape.width(), shape.height()) ? p : null;
	}

	/**
	 * Whether all of a portal's blocks are still lit portal blocks of its axis. Null when not known
	 * without loading its chunks ({@code load} false and they aren't loaded); with {@code load} true its
	 * chunks are loaded to look.
	 */
	private static @Nullable Boolean intact(ServerLevel level, Portal p, boolean load) {
		BlockPos a = new BlockPos(p.x(), p.y(), p.z()), b = new BlockPos(p.maxX(), p.maxY(), p.maxZ());
		if (!load && (!level.isLoaded(a) || !level.isLoaded(b))) {
			return null;
		}
		Direction.Axis axis = p.axis() == PortalStore.AXIS_X ? Direction.Axis.X : Direction.Axis.Z;
		for (BlockPos q : BlockPos.betweenClosed(a, b)) {
			BlockState s = level.getBlockState(q);
			if (!s.is(Blocks.NETHER_PORTAL) || s.getValue(NetherPortalBlock.AXIS) != axis) {
				return false;
			}
		}
		return true;
	}

	static String placeName(Portal p) {
		if (!p.place().isEmpty()) {
			return p.place();
		}
		return p.interior() ? "an unnamed place indoors" : "the wilds";
	}

	private static String where(Portal p) {
		return String.format("portal %d (%s, %s %08X, %d %d %d, %s %dx%d)", p.id(), placeName(p), p.interior() ? "cell" : "world", p.world(), p.x(), p.y(), p.z(),
			p.axis() == PortalStore.AXIS_X ? "X" : "Z", p.width(), p.height());
	}

	/** Skyrim's player is somewhere Minecraft can tie a portal to (in game, not loading). Fills SKY. */
	private static boolean skyrimHere() {
		return SkyLink.readSkyState(SKY) && SKY.inGame() && !SKY.loading() && SKY.worldId != 0;
	}

	/**
	 * The record of this lit portal for the host's Skyrim world, made now if it's new. A record of the
	 * same blocks that no longer matches them (the frame was rebuilt) is dropped first. Null, with the
	 * reason told, when the portal was opened in another Skyrim world.
	 */
	private static @Nullable Portal register(PortalStore s, Shape shape, ServerPlayer player) {
		Portal mine = recordOf(s, SKY.worldId, shape);
		if (mine != null) {
			return mine;
		}
		Portal other = s.anyAt(shape.x(), shape.y(), shape.z());
		if (other != null && other.sameShape(shape.x(), shape.y(), shape.z(), shape.axis(), shape.width(), shape.height())) {
			player.sendOverlayMessage(Component.literal("This portal opened in " + placeName(other) + ". It doesn't answer here."));
			return null;
		}
		// Records whose blocks this portal now fills are gone (the frame was rebuilt another size).
		for (Portal p : s.overlapping(shape.x(), shape.y(), shape.z(), shape.axis(), shape.width(), shape.height())) {
			s.remove(p.id());
			SkyCraft.LOG.info("SkyCraft-portal: {} was rebuilt differently; dropped from the network", where(p));
		}
		int flags = (SKY.place & Proto.PLACE_INTERIOR) != 0 ? PortalStore.INTERIOR : 0;
		int side = PortalStore.sideOf(shape.axis(), shape.x(), shape.z(), player.getX(), player.getZ());
		Portal p = s.add(SKY.worldId, flags, shape.x(), shape.y(), shape.z(), shape.axis(), shape.width(), shape.height(), side, SKY.town);
		SkyCraft.LOG.info("SkyCraft-portal: new {}", where(p));
		return p;
	}

	// ---- soul gems ----------------------------------------------------------------------------

	private static ItemStack attunedGem(Portal p) {
		ItemStack stack = new ItemStack(PortalItems.ATTUNED_SOUL_GEM);
		CompoundTag tag = new CompoundTag();
		tag.putInt(KEY_ID, p.id());
		tag.putInt(KEY_WORLD, p.world());
		tag.putInt(KEY_X, p.x());
		tag.putInt(KEY_Y, p.y());
		tag.putInt(KEY_Z, p.z());
		tag.putString(KEY_PLACE, placeName(p));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** The portal an attuned gem remembers, if it is still that portal (same id, world and place). */
	private static @Nullable Portal attunedTo(PortalStore s, ItemStack gem) {
		CustomData data = gem.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		Portal p = s.get(tag.getIntOr(KEY_ID, 0));
		if (p == null || p.world() != tag.getIntOr(KEY_WORLD, 0) || p.x() != tag.getIntOr(KEY_X, Integer.MIN_VALUE) || p.y() != tag.getIntOr(KEY_Y, Integer.MIN_VALUE)
			|| p.z() != tag.getIntOr(KEY_Z, Integer.MIN_VALUE)) {
			return null;
		}
		return p;
	}

	/** One of the held gems becomes {@code into} (the rest of the stack stays in hand). */
	private static void swapOne(ServerPlayer player, InteractionHand hand, ItemStack held, ItemStack into) {
		if (held.getCount() <= 1) {
			player.setItemInHand(hand, into);
			return;
		}
		held.shrink(1);
		player.getInventory().add(into);
		if (!into.isEmpty()) {
			ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), into);
			dropped.setDefaultPickUpDelay();
			player.level().addFreshEntity(dropped);
		}
	}

	private static void sparkle(ServerLevel level, Portal p, boolean bound) {
		double[] c = p.center();
		level.sendParticles(bound ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, c[0], c[1], c[2], bound ? 80 : 40, 0.4, p.height() / 3.0, 0.4, 0.2);
		level.playSound(null, BlockPos.containing(c[0], c[1], c[2]), bound ? SoundEvents.BEACON_ACTIVATE : SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS,
			1.0F, bound ? 0.8F : 1.2F);
	}

	/**
	 * A soul gem used on a lit portal (SoulGemItem, server side): a filled gem attunes to it, an attuned
	 * gem binds it to the portal the gem remembers. Only the host's Skyrim can feel the cracks.
	 */
	static InteractionResult useGem(UseOnContext context, boolean attunedGem) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.PASS;
		}
		Shape shape = shapeAt(level, context.getClickedPos());
		if (shape == null || !intercepts(level)) {
			return InteractionResult.PASS;
		}
		if (!on()) {
			player.sendOverlayMessage(Component.literal("Portals are switched off (portals in skycraft-features.txt)."));
			return InteractionResult.FAIL;
		}
		if (!SkyNet.isHost(player)) {
			player.sendOverlayMessage(Component.literal("The portal answers only to the host's Skyrim."));
			return InteractionResult.FAIL;
		}
		if (!skyrimHere()) {
			player.sendOverlayMessage(Component.literal("The portal flickers. Try again in a moment."));
			return InteractionResult.FAIL;
		}
		MinecraftServer server = level.getServer();
		PortalStore s = store(server);
		InteractionHand hand = context.getHand();
		ItemStack held = context.getItemInHand();
		int side = PortalStore.sideOf(shape.axis(), shape.x(), shape.z(), player.getX(), player.getZ());
		if (!attunedGem) {
			Portal p = register(s, shape, player);
			if (p == null) {
				save(server, s);
				return InteractionResult.FAIL;
			}
			if (p.bound()) {
				Portal partner = s.get(p.partner());
				player.sendOverlayMessage(Component.literal("This portal is already bound to " + (partner != null ? placeName(partner) : "another") + "."));
				save(server, s);
				return InteractionResult.FAIL;
			}
			p = p.withSide(side);
			s.put(p);
			save(server, s);
			swapOne(player, hand, held, attunedGem(p));
			sparkle(level, p, false);
			player.sendOverlayMessage(Component.literal("The soul gem is attuned to this portal. Use it on a second lit portal to bind the two."));
			SkyCraft.LOG.info("SkyCraft-portal: {} attuned a soul gem to {}", player.getName().getString(), where(p));
			return InteractionResult.SUCCESS;
		}
		Portal a = attunedTo(s, held);
		if (a == null) {
			swapOne(player, hand, held, new ItemStack(PortalItems.FILLED_SOUL_GEM));
			player.sendOverlayMessage(Component.literal("The portal this gem was attuned to is gone. Its soul is still in the gem."));
			SkyCraft.LOG.info("SkyCraft-portal: {} used a soul gem attuned to a portal that's gone; it's a filled gem again", player.getName().getString());
			return InteractionResult.SUCCESS;
		}
		if (a.bound()) {
			swapOne(player, hand, held, new ItemStack(PortalItems.FILLED_SOUL_GEM));
			player.sendOverlayMessage(Component.literal("The portal at " + placeName(a) + " is bound already. The gem's soul is free again."));
			return InteractionResult.SUCCESS;
		}
		Portal q = register(s, shape, player);
		if (q == null) {
			save(server, s);
			return InteractionResult.FAIL;
		}
		if (q.id() == a.id()) {
			player.sendOverlayMessage(Component.literal("The gem is attuned to this very portal. Use it on another one."));
			save(server, s);
			return InteractionResult.FAIL;
		}
		if (q.bound()) {
			Portal partner = s.get(q.partner());
			player.sendOverlayMessage(Component.literal("This portal is already bound to " + (partner != null ? placeName(partner) : "another") + "."));
			save(server, s);
			return InteractionResult.FAIL;
		}
		if (!Boolean.TRUE.equals(intact(level, a, true))) {
			s.remove(a.id());
			save(server, s);
			swapOne(player, hand, held, new ItemStack(PortalItems.FILLED_SOUL_GEM));
			player.sendOverlayMessage(Component.literal("The portal at " + placeName(a) + " has gone out. The gem's soul is free again."));
			SkyCraft.LOG.info("SkyCraft-portal: {} went out before it was bound; dropped", where(a));
			return InteractionResult.SUCCESS;
		}
		q = q.withSide(side);
		s.put(q);
		PortalStore.Bind result = s.bind(a.id(), q.id());
		save(server, s);
		if (result != PortalStore.Bind.BOUND) {
			player.sendOverlayMessage(Component.literal("The portals won't bind."));
			SkyCraft.LOG.warn("SkyCraft-portal: binding {} and {} failed: {}", where(a), where(q), result);
			return InteractionResult.FAIL;
		}
		held.shrink(1);
		sparkle(level, q, true);
		player.sendOverlayMessage(Component.literal("Bound for good: this portal and the one at " + placeName(a) + " open onto each other."));
		SkyCraft.LOG.info("SkyCraft-portal: {} bound {} and {}", player.getName().getString(), where(a), where(q));
		return InteractionResult.SUCCESS;
	}

	// ---- travel -------------------------------------------------------------------------------

	/**
	 * An entity has stood in a portal of the mirror world for Minecraft's usual wait
	 * (NetherPortalBlockMixin; vanilla travel is off there). The host goes to the bound partner;
	 * nothing else goes anywhere.
	 */
	public static void reach(ServerLevel level, Entity entity, BlockPos entry) {
		if (!(entity instanceof ServerPlayer player) || !on() || player.isSpectator()) {
			return;
		}
		if (!SkyNet.isHost(player)) {
			player.sendOverlayMessage(Component.literal("The portal answers only to the host's Skyrim."));
			return;
		}
		long now = System.nanoTime();
		if (pending != null || (trippedOnce && now - lastTrip < TRIP_GAP_NANOS)) {
			return;
		}
		if (!skyrimHere() || SKY.menuOpen()) {
			return;
		}
		Shape shape = shapeAt(level, entry);
		if (shape == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		PortalStore s = store(server);
		Portal from = recordOf(s, SKY.worldId, shape);
		Portal to = from != null ? s.destination(from) : null;
		if (from == null || to == null) {
			if (from != null && from.bound()) {
				s.put(from.withPartner(0)); // its partner's record is missing
				save(server, s);
			}
			player.sendOverlayMessage(Component.literal("This portal is not bound. Use a filled soul gem on it, then on a second portal."));
			return;
		}
		if (!Boolean.TRUE.equals(intact(level, to, true))) {
			s.remove(to.id());
			save(server, s);
			player.sendOverlayMessage(Component.literal("The portal at " + placeName(to) + " has gone out. This one is unbound again."));
			SkyCraft.LOG.info("SkyCraft-portal: {} leads to {}, which has gone out; both unbound", where(from), where(to));
			return;
		}
		double[] exit = to.exit();
		lastTrip = now;
		trippedOnce = true;
		double dx = exit[0] - player.getX(), dy = exit[1] - player.getY(), dz = exit[2] - player.getZ();
		if (to.world() == SKY.worldId && dx * dx + dy * dy + dz * dz < LOCAL_HOP * LOCAL_HOP) {
			// A short hop in the same Skyrim world: Minecraft moves the player, Skyrim's follows as usual.
			player.teleportTo(level, exit[0], exit[1], exit[2], Set.<Relative>of(), (float) exit[3], player.getXRot(), true);
			player.setPortalCooldown();
			level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4F, 1.4F);
			SkyCraft.LOG.info("SkyCraft-portal: {} hopped from {} to {}", player.getName().getString(), where(from), where(to));
			return;
		}
		int flags = to.interior() ? Proto.PORTAL_INTERIOR : 0;
		SkyLink.pushEvent(Proto.EV_PORTAL_TRAVEL, to.world(), (float) exit[0], (float) exit[1], (float) exit[2], (float) exit[3], flags, SKY.worldId);
		pending = new Pending(from.id(), to.id(), placeName(to), player.getUUID(), now, SKY.worldId, SKY.teleportSeq, SkyCollision.epoch());
		player.connection.send(new ClientboundLevelEventPacket(LevelEvent.SOUND_PORTAL_TRAVEL, BlockPos.ZERO, 0, false));
		player.sendOverlayMessage(Component.literal("The portal pulls you toward " + placeName(to) + "..."));
		SkyCraft.LOG.info("SkyCraft-portal: {} steps through {} to {}: Skyrim moves them to {} {} {} facing {}", player.getName().getString(), where(from), where(to),
			String.format("%.2f", exit[0]), String.format("%.2f", exit[1]), String.format("%.2f", exit[2]), (int) exit[3]);
	}

	/**
	 * Did Skyrim take the host? Any sign of a move counts (a loading screen, another world or cell, a
	 * teleport, Skyrim clearing its world for Minecraft); none within PENDING_NANOS means it refused.
	 */
	private static void checkPending(MinecraftServer server) {
		Pending p = pending;
		if (p == null) {
			return;
		}
		boolean moved = SkyCollision.epoch() != p.epoch();
		if (SkyLink.readSkyState(SKY)) {
			moved |= SKY.loading() || SKY.worldId != p.worldId() || SKY.teleportSeq != p.teleportSeq();
		}
		ServerPlayer player = server.getPlayerList().getPlayer(p.player());
		if (moved) {
			pending = null;
			if (player != null) {
				player.setPortalCooldown();
			}
			SkyCraft.LOG.info("SkyCraft-portal: Skyrim is taking the player through portal {} to portal {} ({})", p.from(), p.to(), p.place());
			return;
		}
		if (System.nanoTime() - p.since() < PENDING_NANOS) {
			return;
		}
		pending = null;
		if (player != null) {
			player.sendOverlayMessage(Component.literal("The portal won't take you right now. Step out and try again."));
		}
		SkyCraft.LOG.info("SkyCraft-portal: Skyrim didn't move the player through portal {} to portal {}: it refused the request", p.from(), p.to());
	}

	// ---- every tick ---------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!SkyLink.active()) {
			pending = null;
			hintShape = 0;
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty() || !on()) {
			return;
		}
		int t = server.getTickCount();
		if (pending != null && t % 5 == 0) {
			checkPending(server);
		}
		if (t % 5 == 0) {
			for (ServerPlayer p : players) {
				if (SkyNet.isHost(p) && intercepts(p.level())) {
					hint(p.level(), p);
				}
			}
		}
		if (t % 40 == 0) {
			checkLit(server, players);
		}
	}

	/** The portal block the player is standing in (feet or head), or null. */
	private static @Nullable BlockPos portalBlockIn(ServerLevel level, ServerPlayer player) {
		var box = player.getBoundingBox().deflate(1.0E-3);
		BlockPos from = BlockPos.containing(box.minX, box.minY, box.minZ), to = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** Standing in a portal: where it leads, once, or (unbound) how to bind it, every few seconds. */
	private static void hint(ServerLevel level, ServerPlayer host) {
		BlockPos in = host.isSpectator() ? null : portalBlockIn(level, host);
		Shape shape = in != null ? shapeAt(level, in) : null;
		if (shape == null) {
			hintShape = 0;
			return;
		}
		long key = BlockPos.asLong(shape.x(), shape.y(), shape.z()) ^ ((long) shape.axis() << 63);
		boolean fresh = key != hintShape;
		hintAge = fresh ? 0 : hintAge + 5;
		hintShape = key;
		if (pending != null || !skyrimHere()) {
			return;
		}
		PortalStore s = store(level.getServer());
		Portal from = recordOf(s, SKY.worldId, shape);
		Portal to = from != null ? s.destination(from) : null;
		if (to != null) {
			if (fresh) {
				host.sendOverlayMessage(Component.literal("This portal opens onto " + placeName(to) + "."));
			}
			return;
		}
		if (!fresh && hintAge < HINT_REPEAT_TICKS) {
			return;
		}
		hintAge = 0;
		Portal other = from == null ? s.anyAt(shape.x(), shape.y(), shape.z()) : null;
		if (other != null && other.sameShape(shape.x(), shape.y(), shape.z(), shape.axis(), shape.width(), shape.height())) {
			host.sendOverlayMessage(Component.literal("This portal opened in " + placeName(other) + ". It doesn't answer here."));
		} else {
			host.sendOverlayMessage(Component.literal("This portal is not bound. Use a filled soul gem on it, then on a second portal."));
		}
	}

	/** Portals near anyone that have gone out (an explosion, a piston, a missed break) leave the network. */
	private static void checkLit(MinecraftServer server, List<ServerPlayer> players) {
		PortalStore s = store(server);
		if (s.size() == 0) {
			return;
		}
		ServerLevel level = server.overworld();
		List<Portal> gone = new ArrayList<>();
		for (Portal p : s.all()) {
			double[] c = p.center();
			boolean near = false;
			for (ServerPlayer pl : players) {
				double dx = pl.getX() - c[0], dz = pl.getZ() - c[2];
				near |= pl.level() == level && dx * dx + dz * dz < CHECK_RANGE * CHECK_RANGE;
			}
			if (near && Boolean.FALSE.equals(intact(level, p, false))) {
				gone.add(p);
			}
		}
		for (Portal p : gone) {
			s.remove(p.id());
			SkyCraft.LOG.info("SkyCraft-portal: {} has gone out; dropped from the network{}", where(p), p.bound() ? ", its partner " + p.partner() + " unbound" : "");
		}
		if (!gone.isEmpty()) {
			save(server, s);
		}
	}

	/** A frame block or portal block broken: the portals it held that went out leave the network. */
	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || !intercepts(level) || !(state.is(Blocks.OBSIDIAN) || state.is(Blocks.NETHER_PORTAL))) {
			return;
		}
		PortalStore s = store(serverLevel.getServer());
		boolean changed = false;
		for (Portal p : s.touching(pos.getX(), pos.getY(), pos.getZ())) {
			if (Boolean.TRUE.equals(intact(serverLevel, p, false))) {
				continue;
			}
			s.remove(p.id());
			changed = true;
			player.sendOverlayMessage(Component.literal("The portal at " + placeName(p) + " collapses" + (p.bound() ? "; its bond is broken." : ".")));
			SkyCraft.LOG.info("SkyCraft-portal: {} broke the frame of {}; dropped from the network{}", player.getName().getString(), where(p),
				p.bound() ? ", its partner " + p.partner() + " unbound" : "");
		}
		if (changed) {
			save(serverLevel.getServer(), s);
		}
	}
}
