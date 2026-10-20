package dev.skycraft.party;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.combat.SkyCombat;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.companion.CompanionRules;
import dev.skycraft.companion.SkyCompanions;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.link.SkySpace;
import dev.skycraft.loot.DungeonLoot;
import dev.skycraft.loot.SkyBridge;
import dev.skycraft.loot.SkyLoot;
import dev.skycraft.net.SkyNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Guest parity (switch guest_parity): a guest's own Skyrim gets from Minecraft play what the host's gets, through the
 * host's server, which stays in charge of the world. A guest's Skyrim is on their own PC, so what it says reaches the
 * server as SkyNet packets from their client, and what the server tells it goes back the same way:
 *
 * <ul>
 * <li>Their actor table near them (SkyNet.GuestActors, up to ten a second): their actors get stand-ins of their own,
 * owned by them (SkyrimActorEntity.owner), within {@code guest_range} of them. Hits on those go back to their Skyrim
 * (EV_HIT_ACTOR, which also trains their weapon skills), arrows stuck in them too, and a stand-in hit lately and seen dead
 * is a credited kill: kill XP, kill drops and gear drops, as many as {@code guest_kill_*} allow. Their merchants trade at
 * their town's prices, their animals are tamed as the host's are.
 * <li>Their loot (SkyNet.GuestLoot): converted by the host's item rules into their inventory, a dungeon chest rolled once
 * a chest for them, gear their actors wore handed to Gear drops; Skyrim is told to remove what converted. At most
 * {@code guest_loot_*} items: the rest stay in their Skyrim.
 * <li>Skills trained from Minecraft play (defence, Smithing, Speech), a bed's night and Minecraft's explosions near them:
 * {@link #pushSkyrim} to their Skyrim (SkyNet.GuestEvent).
 * </ul>
 *
 * A guest's client sends only with its own guest_parity on and passes on only {@link #RELAYED} event types; this server
 * takes and sends nothing with the host's off. Log: SkyCraft-party. Server thread.
 */
public final class GuestParity {
	private static final String FILE = "skycraft-party.txt";
	/** What a guest's client passes on to its Skyrim (client PartyClient keeps to this list). */
	public static final Set<Integer> RELAYED = Set.of(Proto.EV_HIT_ACTOR, Proto.EV_EXPLOSION, Proto.EV_ARROW_STUCK, Proto.EV_SKILL_USE, Proto.EV_TAKE_ITEM,
		Proto.EV_SLEEP, Proto.EV_PORTAL_TRAVEL, Proto.EV_COMPANION);
	/** Ticks between actor tables taken from one guest (ten a second); more are dropped. */
	private static final int ACTORS_EVERY = 2;
	/** A guest whose client sent no actor table for this long loses their stand-ins (another dimension, no Skyrim, an older client). */
	private static final int STALE_TICKS = 40;
	/** How far from an explosion a guest's Skyrim still feels it. */
	private static final double EXPLOSION_REACH = 64.0;

	private static final Map<UUID, Guest> GUESTS = new HashMap<>();
	private static PartyRules.@Nullable Settings settings;

	/** What the server keeps of one guest's Skyrim. */
	private static final class Guest {
		final UUID id;
		/** Stand-ins of their actors, by form id (another player's Skyrim may have the same form id: those are theirs). */
		final Map<Integer, SkyrimActorEntity> proxies = new HashMap<>();
		/** The latest record of each actor with a stand-in. */
		final Map<Integer, SkyLink.Actor> latest = new HashMap<>();
		final Map<Integer, Boolean> wasDead = new HashMap<>();
		/** Server tick of the last hit on each actor's stand-in, and who landed it: the kill is theirs. */
		final Map<Integer, Integer> lastHit = new HashMap<>();
		final Map<Integer, UUID> hitter = new HashMap<>();
		final Set<Integer> looted = new HashSet<>();
		/** Actors in their table lately (server tick): worn gear is only taken for these. */
		final Map<Integer, Integer> seen = new HashMap<>();
		/** Their companions seen alive, by form id, so a death can be told. */
		final Map<Integer, String> companions = new HashMap<>();
		PartyCodec.Header header = new PartyCodec.Header(0, 0, -1.0F, true, "");
		int lastActors = Integer.MIN_VALUE / 2;
		final PartyRules.Bucket loot, chests, kills;
		/** Skyrim arrows stopped at blocks (not guest parity's: every guest's arrows stick, as the host's do). */
		final PartyRules.Bucket arrows = PartyRules.Bucket.perMinute(20, 600);
		boolean slept;
		int logged;

		Guest(UUID id, PartyRules.Settings s) {
			this.id = id;
			this.loot = PartyRules.Bucket.perMinute(s.lootBurst(), s.lootPerMinute());
			this.chests = PartyRules.Bucket.perHour(s.chestBurst(), s.chestsPerHour());
			this.kills = PartyRules.Bucket.perMinute(s.killBurst(), s.killsPerMinute());
		}

		void discardAll() {
			this.proxies.values().forEach(Entity::discard);
			this.proxies.clear();
			this.latest.clear();
		}
	}

	private GuestParity() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GuestParity::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GUESTS.values().forEach(Guest::discardAll);
			GUESTS.clear();
		});
	}

	/** The guest_parity switch. */
	public static boolean on() {
		return Features.on(Features.Feature.GUEST_PARITY);
	}

	/** The multiplayer numbers (skycraft-party.txt), read once. */
	static PartyRules.Settings settings() {
		PartyRules.Settings s = settings;
		if (s == null) {
			List<String> problems = new ArrayList<>();
			s = PartyRules.parse(Config.read(FILE, PartyRules.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-party: {} {}", FILE, p));
			settings = s;
		}
		return s;
	}

	private static Guest guest(ServerPlayer player) {
		return GUESTS.computeIfAbsent(player.getUUID(), id -> new Guest(id, settings()));
	}

	// ---- telling a player's own Skyrim -----------------------------------------------------------------

	/** Whether this player's own Skyrim can be told things: the host's while it's on the link, a guest's with guest parity on and a client that listens. */
	public static boolean reachesSkyrim(ServerPlayer player) {
		if (SkyNet.isHost(player)) {
			return SkyLink.active();
		}
		return on() && ServerPlayNetworking.canSend(player, SkyNet.GuestEvent.TYPE);
	}

	/**
	 * An event for {@code player}'s own Skyrim (proto::McEvent): the host's goes onto the link, a guest's to their client
	 * (guest parity), nobody else's anywhere. A place in a, b, c (EV_EXPLOSION, EV_ARROW_STUCK, EV_PORTAL_TRAVEL) is
	 * sent to a guest already in their Skyrim world's terms.
	 */
	public static void pushSkyrim(ServerPlayer player, int type, int formId, float a, float b, float c, float d, int flags, int weapon) {
		if (SkyNet.isHost(player)) {
			SkyLink.pushEvent(type, formId, a, b, c, d, flags, weapon);
		} else if (on() && ServerPlayNetworking.canSend(player, SkyNet.GuestEvent.TYPE)) {
			ServerPlayNetworking.send(player, new SkyNet.GuestEvent(type, formId, List.of(a, b, c, d), List.of(flags, weapon)));
		}
	}

	/** {@link #pushSkyrim} with no weapon. */
	public static void pushSkyrim(ServerPlayer player, int type, int formId, float a, float b, float c, float d, int flags) {
		pushSkyrim(player, type, formId, a, b, c, d, flags, 0);
	}

	// ---- what other code asks ----------------------------------------------------------------------

	/** The stand-in of this guest's own Skyrim actor (guest parity on), or null. */
	public static @Nullable SkyrimActorEntity proxy(ServerPlayer guest, int formId) {
		Guest g = on() ? GUESTS.get(guest.getUUID()) : null;
		return g != null ? g.proxies.get(formId) : null;
	}

	/** Whether {@code player}'s own Skyrim has the actor behind {@code proxy} (and can be told about it). */
	public static boolean ownsActor(ServerPlayer player, SkyrimActorEntity proxy) {
		UUID owner = proxy.owner();
		if (owner == null) {
			return SkyNet.isHost(player) && SkyLink.active();
		}
		return owner.equals(player.getUUID()) && reachesSkyrim(player);
	}

	/** The latest actor table record behind a guest's stand-in, or null. */
	public static SkyLink.@Nullable Actor record(SkyrimActorEntity proxy) {
		Guest g = proxy.owner() != null ? GUESTS.get(proxy.owner()) : null;
		return g != null ? g.latest.get(proxy.formId()) : null;
	}

	/** Server tick of the last hit on a guest's stand-in, or null. */
	public static @Nullable Integer lastHit(SkyrimActorEntity proxy) {
		Guest g = proxy.owner() != null ? GUESTS.get(proxy.owner()) : null;
		return g != null ? g.lastHit.get(proxy.formId()) : null;
	}

	/** The town (or location) a guest's Skyrim says they're in, "" if it hasn't said: their merchants' prices. */
	public static String town(UUID guest) {
		Guest g = GUESTS.get(guest);
		return g != null ? g.header.town().strip() : "";
	}

	/** The Skyrim world (SkyState worldId) a guest's Skyrim last said it's in, 0 if it hasn't (guest parity off, an older client). */
	public static int skyrimWorld(ServerPlayer guest) {
		Guest g = on() ? GUESTS.get(guest.getUUID()) : null;
		return g != null ? g.header.world() : 0;
	}

	/** A line about this player for /skyparty. */
	static String describe(ServerPlayer player) {
		if (SkyNet.isHost(player)) {
			return "host, Skyrim " + (SkyLink.active() ? "on the link" : "not linked");
		}
		Guest g = GUESTS.get(player.getUUID());
		boolean listens = ServerPlayNetworking.canSend(player, SkyNet.GuestEvent.TYPE);
		if (!listens) {
			return "guest without this SkyCraft (an older one?)";
		}
		if (g == null || g.header.world() == 0) {
			return "guest, nothing from their Skyrim yet" + (on() ? "" : " (guest_parity is off here)");
		}
		return String.format("guest in Skyrim world %08X%s, %d stand-ins%s", g.header.world(), g.header.town().isEmpty() ? "" : " (" + g.header.town() + ")",
			g.proxies.size(), on() ? "" : " (guest_parity is off here)");
	}

	// ---- from a guest's client -------------------------------------------------------------------------

	/**
	 * A guest's Skyrim arrow or bolt stopped at a Minecraft block (SkyNet.ArrowBlocked): a Minecraft arrow sticks there, as
	 * for the host's. Not guest parity's: it fixes what a guest saw. Held to 20 at once and 10 a second, and to the
	 * guest's surroundings (SkyCombat.stickSkyrimArrow: within 128 blocks, in the mirror).
	 */
	public static void arrowBlocked(ServerPlayer player, float x, float y, float z, int code) {
		if (SkyNet.isHost(player) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
			return; // (the host's come over the link)
		}
		if (!guest(player).arrows.take(player.level().getServer().getTickCount())) {
			return;
		}
		double[] flight = Proto.arrowFlight(code);
		SkyCombat.stickSkyrimArrow(player, new Vec3(x, y, z), new Vec3(flight[0], flight[1], flight[2]));
	}

	/** A guest's Skyrim's actor table and place (SkyNet.GuestActors). */
	public static void onActors(ServerPlayer player, List<Integer> data) {
		if (!on() || SkyNet.isHost(player)) {
			return;
		}
		int tick = player.level().getServer().getTickCount();
		Guest g = guest(player);
		if (tick - g.lastActors < ACTORS_EVERY) {
			return; // more than ten a second: the next one does
		}
		g.lastActors = tick;
		PartyCodec.ActorTable table;
		try {
			table = PartyCodec.readActors(data);
		} catch (IllegalArgumentException e) {
			if (g.logged++ < 5) {
				SkyCraft.LOG.warn("SkyCraft-party: {}'s actor table was refused: {}", player.getName().getString(), e.getMessage());
			}
			return;
		}
		boolean first = g.header.world() == 0;
		g.header = table.header();
		if (first && g.header.world() != 0) {
			SkyCraft.LOG.info("SkyCraft-party: {}'s own Skyrim reaches this world (Skyrim world {}, {})", player.getName().getString(),
				String.format("%08X", g.header.world()), g.header.town().isEmpty() ? "no place name" : g.header.town());
		}
		if (table.header().away() || !SkyDimension.inMirror(player)) {
			g.discardAll(); // their Skyrim is parked while they're in another dimension
			return;
		}
		PartyRules.Settings s = settings();
		double range = s.guestRange();
		List<SkyLink.Actor> actors = new ArrayList<>();
		for (PartyCodec.Actor a : table.actors()) {
			if (actors.size() >= s.guestActors()) {
				break;
			}
			double dx = a.x() - player.getX(), dy = a.y() - player.getY(), dz = a.z() - player.getZ();
			if (dx * dx + dy * dy + dz * dz > range * range) {
				continue; // what their Skyrim says stops at their surroundings
			}
			actors.add(new SkyLink.Actor(a.formId(), a.flags(), a.x(), a.y(), a.z(), a.yaw(), a.width(), a.height(), a.health(), a.level(), a.vendor(),
				a.name()));
		}
		ServerLevel level = player.level();
		kills(level, player, g, actors, tick); // while the dead ones' stand-ins are still there
		sync(level, player, g, actors);
	}

	/** Items that landed in a guest's Skyrim inventory (SkyNet.GuestLoot). */
	public static void onLoot(ServerPlayer player, List<Integer> data) {
		if (!on() || SkyNet.isHost(player)) {
			return;
		}
		Guest g = guest(player);
		List<PartyCodec.Item> items;
		try {
			items = PartyCodec.readItems(data);
		} catch (IllegalArgumentException e) {
			if (g.logged++ < 5) {
				SkyCraft.LOG.warn("SkyCraft-party: {}'s loot was refused: {}", player.getName().getString(), e.getMessage());
			}
			return;
		}
		if (!SkyDimension.inMirror(player)) {
			return; // their Skyrim is parked; whatever this is stays in it
		}
		int tick = player.level().getServer().getTickCount();
		SkyBridge.TakeBack takeBack = (formId, count) -> pushSkyrim(player, Proto.EV_TAKE_ITEM, formId, count, 0.0F, 0.0F, 0.0F, 0);
		int refused = 0;
		for (PartyCodec.Item e : items) {
			SkyLink.Item it = new SkyLink.Item(e.formId(), e.count(), e.tags(), e.material(), e.source(), e.name(), e.container(), e.chest(), e.quest());
			if (it.source() == Proto.SRC_WORN) {
				// Gear an actor of theirs wore when it died: only one this server saw in their table lately (and it drops
				// only for a kill this server credited).
				Integer seen = g.seen.get(it.container());
				if (seen != null && tick - seen <= SkyLoot.HIT_WINDOW_TICKS) {
					SkyBridge.convertForGuest(player, it, g.header.place(), takeBack);
				}
				continue;
			}
			if (it.count() <= 0) {
				continue;
			}
			if (!g.loot.take(tick)) {
				refused++; // never taken from their Skyrim: it stays there
				continue;
			}
			int place = g.header.place();
			if (Features.on(Features.Feature.DUNGEON_CHESTS) && DungeonLoot.guestWouldRoll(g.id, it, place) && !g.chests.take(tick)) {
				place &= ~Proto.PLACE_DUNGEON; // no roll now; a later item from the same chest can still bring it
				SkyCraft.LOG.info("SkyCraft-party: {} has had all the dungeon chest rolls they get for now; chest {} waits", player.getName().getString(),
					String.format("%08X", it.container()));
			}
			SkyBridge.convertForGuest(player, it, place, takeBack);
		}
		if (refused > 0) {
			player.sendOverlayMessage(Component.literal("Too much Skyrim loot at once: " + refused + (refused == 1 ? " item stays" : " items stay")
				+ " in your Skyrim"));
			SkyCraft.LOG.info("SkyCraft-party: {}'s Skyrim handed over {} items too many at once; they stay in Skyrim", player.getName().getString(), refused);
		}
	}

	// ---- for things that happen in the world --------------------------------------------------------

	/** An arrow stuck in a guest's actor's stand-in (mixin.AbstractArrowMixin): their Skyrim pins it to the actor. */
	public static void arrowStuck(SkyrimActorEntity actor, Vec3 at, float yaw, float pitch, int texture) {
		UUID owner = actor.owner();
		if (owner == null || !(actor.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer guest = level.getServer().getPlayerList().getPlayer(owner);
		if (guest != null) {
			pushSkyrim(guest, Proto.EV_ARROW_STUCK, actor.formId(), (float) SkySpace.local(at.x), (float) at.y, (float) SkySpace.local(at.z), yaw,
				Float.floatToRawIntBits(pitch), texture);
		}
	}

	/**
	 * A Minecraft explosion in the mirror (mixin.ServerExplosionMixin): each guest near it, in the same Skyrim world's
	 * space, feels it in their Skyrim as the host does in theirs (loose objects thrown, people knocked away).
	 */
	public static void explosion(ServerLevel level, double x, double y, double z, float radius) {
		if (!on()) {
			return;
		}
		int world = SkySpace.worldAt(x, z);
		for (ServerPlayer p : level.players()) {
			if (SkyNet.isHost(p) || p.distanceToSqr(x, y, z) > EXPLOSION_REACH * EXPLOSION_REACH || SkySpace.worldAt(p.getX(), p.getZ()) != world) {
				continue;
			}
			pushSkyrim(p, Proto.EV_EXPLOSION, 0, (float) SkySpace.local(x), (float) y, (float) SkySpace.local(z), radius, 0);
		}
	}

	// ---- every tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (GUESTS.isEmpty()) {
			return;
		}
		int tick = server.getTickCount();
		for (Iterator<Guest> it = GUESTS.values().iterator(); it.hasNext(); ) {
			Guest g = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(g.id);
			if (player == null) {
				g.discardAll(); // left the world
				it.remove();
				continue;
			}
			if (!g.proxies.isEmpty() && (!on() || tick - g.lastActors > STALE_TICKS || !SkyDimension.inMirror(player))) {
				g.discardAll();
			}
			flushHits(player, g, tick);
			sleep(player, g);
		}
	}

	/** This tick's hits on a guest's stand-ins go to their Skyrim, one per actor, as SkyCombat sends the host's. */
	private static void flushHits(ServerPlayer guest, Guest g, int tick) {
		for (SkyrimActorEntity proxy : g.proxies.values()) {
			float[] hit = proxy.takeHit();
			if (hit == null || !(hit[0] > 0.0F || hit[3] > 0.0F)) {
				continue;
			}
			g.lastHit.put(proxy.formId(), tick);
			UUID by = proxy.lastPlayerHit();
			if (by != null) {
				g.hitter.put(proxy.formId(), by);
			}
			pushSkyrim(guest, Proto.EV_HIT_ACTOR, proxy.formId(), hit[0], hit[1], hit[2], hit[3], Float.floatToRawIntBits(hit[4]),
				Float.floatToRawIntBits(hit[5]));
			if (g.logged++ < 40) {
				SkyCraft.LOG.info("SkyCraft-party: hit {}'s {} for {} (knockback {})", guest.getName().getString(), proxy.skyrimName(), hit[0], hit[3]);
			}
		}
	}

	/** A full night in a bed (bed_sleep) passes the night in the guest's own Skyrim, by its own clock; the world's clock stays the host's. */
	private static void sleep(ServerPlayer guest, Guest g) {
		if (!guest.isSleeping()) {
			g.slept = false;
			return;
		}
		if (g.slept || !guest.isSleepingLongEnough() || !Features.on(Features.Feature.BED_SLEEP) || !reachesSkyrim(guest)) {
			return;
		}
		g.slept = true;
		float hour = g.header.hour() >= 0.0F ? g.header.hour() : 22.0F;
		float hours = dev.skycraft.time.SkyTime.hoursToSleep(hour);
		pushSkyrim(guest, Proto.EV_SLEEP, 0, hours, 0.0F, 0.0F, 0.0F, 0);
		SkyCraft.LOG.info("SkyCraft-party: {} slept in a bed: their Skyrim passes {} hours from {}", guest.getName().getString(), hours,
			String.format("%.2f", hour));
	}

	/** Deaths in a guest's table: a stand-in hit lately and seen dead is a kill, paid as the host's are (SkyLoot.payKill). */
	private static void kills(ServerLevel level, ServerPlayer guest, Guest g, List<SkyLink.Actor> actors, int tick) {
		Set<Integer> present = new HashSet<>();
		boolean companions = Features.on(Features.Feature.COMPANIONS);
		for (SkyLink.Actor a : actors) {
			Integer id = a.formId();
			present.add(id);
			g.seen.put(id, tick);
			if (companions && CompanionRules.companion(a.flags())) {
				if (!a.dead()) {
					g.companions.put(id, a.name());
				} else if (g.companions.remove(id) != null) {
					guest.sendSystemMessage(Component.literal(a.name() + " died")); // as Minecraft tells a pet's owner
				}
			}
			Boolean before = g.wasDead.put(id, a.dead());
			if (!a.dead() || !Boolean.FALSE.equals(before) || g.looted.contains(id)) {
				continue;
			}
			Integer hit = g.lastHit.get(id);
			if (hit == null || tick - hit > SkyLoot.HIT_WINDOW_TICKS) {
				continue; // killed by something else in their Skyrim
			}
			if (!g.kills.take(tick)) {
				SkyCraft.LOG.info("SkyCraft-party: {} killed {}, but more kills than guest_kills_per_minute allows: no drop", guest.getName().getString(),
					a.name());
				continue;
			}
			if ((a.flags() & Proto.ACTOR_SPAWNED) == 0) {
				g.looted.add(id);
			}
			UUID by = g.hitter.get(id);
			ServerPlayer hitter = by != null ? level.getServer().getPlayerList().getPlayer(by) : null;
			ServerPlayer killer = hitter != null && hitter.level() == level ? hitter : guest;
			SkyCraft.LOG.info("SkyCraft-party: {}'s Skyrim: {} killed by {}", guest.getName().getString(), a.name(), killer.getName().getString());
			SkyLoot.payKill(level, a, g.proxies.get(id), killer, g.id, tick);
		}
		g.wasDead.keySet().removeIf(id -> !present.contains(id) && !g.lastHit.containsKey(id));
		if (tick % 20 == 0) {
			g.lastHit.entrySet().removeIf(e -> tick - e.getValue() > SkyLoot.HIT_WINDOW_TICKS * 3 || tick < e.getValue());
			g.hitter.keySet().retainAll(g.lastHit.keySet());
			g.seen.values().removeIf(t -> tick - t > SkyLoot.HIT_WINDOW_TICKS || tick < t);
			if (g.looted.size() > 1024) {
				g.looted.clear(); // form ids are reused across loads; only the latest kills matter
			}
			if (g.companions.size() > 128) {
				g.companions.clear();
			}
		}
	}

	/** Stand-ins for a guest's living actors, where their Skyrim has them (as SkyCombat.sync does the host's). */
	private static void sync(ServerLevel level, ServerPlayer guest, Guest g, List<SkyLink.Actor> actors) {
		Map<Integer, SkyLink.Actor> live = new HashMap<>();
		for (SkyLink.Actor a : actors) {
			if (!a.dead()) {
				live.put(a.formId(), a);
			}
		}
		g.latest.clear();
		g.latest.putAll(live);
		for (Iterator<Map.Entry<Integer, SkyrimActorEntity>> it = g.proxies.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, SkyrimActorEntity> e = it.next();
			SkyrimActorEntity proxy = e.getValue();
			if (!live.containsKey(e.getKey()) || proxy.isRemoved() || proxy.level() != level) {
				proxy.discard();
				it.remove();
			}
		}
		int before = g.proxies.size();
		for (SkyLink.Actor a : live.values()) {
			SkyrimActorEntity proxy = g.proxies.get(a.formId());
			if (proxy == null) {
				proxy = new SkyrimActorEntity(SkyCombat.SKYRIM_ACTOR, level);
				proxy.setOwner(guest);
				proxy.setFormId(a.formId());
				proxy.setVendor(a.vendor());
				proxy.setSkyFlags(a.flags());
				proxy.setSize(a.width(), a.height());
				proxy.snapTo(a.x(), a.y(), a.z(), a.yaw(), 0.0F);
				proxy.setNames(a.name(), SkyCompanions.displayName(a));
				if (level.addFreshEntity(proxy)) {
					g.proxies.put(a.formId(), proxy);
				}
				continue;
			}
			proxy.setVendor(a.vendor());
			proxy.setSkyFlags(a.flags());
			proxy.setNames(a.name(), SkyCompanions.displayName(a));
			proxy.setSize(a.width(), a.height());
			proxy.setPos(a.x(), a.y(), a.z());
			proxy.setYRot(a.yaw());
			proxy.setYHeadRot(a.yaw());
			SkyCombat.stepOnTriggers(level, proxy);
		}
		if (g.proxies.size() != before && (g.proxies.size() % 5 == 0 || g.proxies.size() < 5)) {
			SkyCraft.LOG.info("SkyCraft-party: {} of {}'s Skyrim actors have stand-ins", g.proxies.size(), guest.getName().getString());
		}
	}
}
