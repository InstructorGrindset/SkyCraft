package dev.skycraft.quests;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.SkyLink;
import dev.skycraft.link.WorldSpaces;
import dev.skycraft.net.SkyNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import org.jspecify.annotations.Nullable;

/**
 * Quest markers (switch {@code quest_markers}, rules in {@link QuestRules}), server side: the targets of each player's own
 * Skyrim quests on that player's locator bar, in the Skyrim mirror only. The host's come from the link
 * (SkyLink.readQuestTargets: the table Skyrim's Quests.cpp fills from its compass's quest targets), a guest's from their own
 * client (SkyNet.QuestTargets, read from their own link). Each marker is a waypoint at a fixed block sent to that one player,
 * the way Minecraft's server tells a client about another player on its bar (ClientboundTrackedWaypointPacket, with an id of
 * its own per quest and reference): nobody else's bar shows it, and Minecraft's own waypoints (other players) are left alone.
 * Looking toward a marker names its quest on the action bar. With locator_bar off, or if Minecraft refuses the waypoints, the
 * action bar names the nearest active quest's target every few seconds instead.
 */
public final class SkyQuests {
	/** Ticks between reads of the markers (Skyrim refreshes them twice a second). */
	private static final int REFRESH_TICKS = 10;
	/** A guest's targets count for this long after their client last sent them. */
	private static final int GUEST_FRESH_TICKS = 200;
	/** Without the bar: how often the action bar names the nearest target. */
	private static final int NEAREST_EVERY_TICKS = 100;
	/** Looking from one marker to the next names it no sooner than this after the last. */
	private static final int LOOK_QUIET_TICKS = 20;

	/** What one player's client has been sent, what they see now, and what they last looked toward. */
	private static final class Shown {
		final Map<UUID, QuestRules.Marker> sent = new HashMap<>();
		List<QuestRules.Marker> markers = List.of();
		@Nullable UUID looking;
		int quietUntil;
		int nearestAt = Integer.MIN_VALUE / 2;
		int logged;
		int loggedCount = -1;
	}

	/** A guest's targets as their client last sent them, and when. */
	private record Claimed(List<QuestTable.Target> targets, int tick) {
	}

	private static final Map<UUID, Shown> SHOWN = new HashMap<>();
	private static final Map<UUID, Claimed> GUESTS = new HashMap<>();
	private static final List<QuestTable.Target> HOST = new ArrayList<>();
	private static final List<QuestTable.Target> READ = new ArrayList<>();
	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	private static QuestRules.@Nullable Settings settings;
	private static @Nullable ResourceKey<WaypointStyleAsset> style;
	/** Minecraft refused the waypoints once: from then on the action bar names the nearest target instead. */
	private static boolean barFailed;
	private static int refused;

	private SkyQuests() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SkyQuests::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SHOWN.clear();
			GUESTS.clear();
			HOST.clear();
		});
	}

	/** The quest marker settings (skycraft-quests.txt), read once. */
	static QuestRules.Settings settings() {
		QuestRules.Settings s = settings;
		if (s == null) {
			List<String> problems = new ArrayList<>();
			s = QuestRules.parse(Config.read(QuestRules.FILE, QuestRules.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-quests: {} {}", QuestRules.FILE, p));
			SkyCraft.LOG.info("SkyCraft-quests: up to {} quest markers on the locator bar ({}), style {}{}", s.maxMarkers(),
				s.untracked() ? "every quest's" : "active quests'", s.style(), s.locatorBar() ? "" : "; locator_bar off: the action bar names the nearest one");
			settings = s;
		}
		return s;
	}

	/** The waypoint style the settings name (Minecraft's default one for a name it can't read). */
	private static ResourceKey<WaypointStyleAsset> style(QuestRules.Settings s) {
		ResourceKey<WaypointStyleAsset> key = style;
		if (key == null) {
			Identifier id = Identifier.tryParse(s.style());
			key = id != null ? ResourceKey.create(WaypointStyleAssets.ROOT_ID, id) : WaypointStyleAssets.DEFAULT;
			style = key;
		}
		return key;
	}

	/** A guest's client sent its own Skyrim's quest targets (SkyNet.QuestTargets). Server thread. */
	public static void onGuestTargets(ServerPlayer player, List<Integer> data) {
		if (SkyNet.isHost(player) || !Features.on(Features.Feature.QUEST_MARKERS)) {
			return; // the host's come from the link; and the host's switch decides for everyone
		}
		List<QuestTable.Target> targets;
		try {
			targets = QuestCodec.decode(data);
		} catch (IllegalArgumentException e) {
			if (refused++ < 10) {
				SkyCraft.LOG.warn("SkyCraft-quests: {}'s quest targets refused: {}", player.getName().getString(), e.getMessage());
			}
			return;
		}
		// Only markers in the guest's own Skyrim world's space, where the guest is: within half a space of them.
		List<QuestTable.Target> near = new ArrayList<>();
		for (QuestTable.Target t : targets) {
			if (Math.abs(t.x() - player.getX()) <= WorldSpaces.HALF && Math.abs(t.z() - player.getZ()) <= WorldSpaces.HALF && Math.abs(t.y()) < 1.0e5) {
				near.add(t);
			}
		}
		GUESTS.put(player.getUUID(), new Claimed(List.copyOf(near), player.level().getServer().getTickCount()));
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 2 != 0) {
			return;
		}
		if (!Features.on(Features.Feature.QUEST_MARKERS)) {
			if (!SHOWN.isEmpty()) {
				takeAllAway(server); // switched off in game
			}
			return;
		}
		QuestRules.Settings s = settings();
		boolean refresh = tick % REFRESH_TICKS == 0;
		if (refresh) {
			readHost();
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer player : players) {
			Shown shown = SHOWN.computeIfAbsent(player.getUUID(), id -> new Shown());
			if (refresh) {
				shown.markers = QuestRules.select(SkyDimension.inMirror(player) ? targetsOf(player, tick) : List.of(), s);
				if (s.locatorBar() && !barFailed) {
					send(player, shown, s);
				}
			}
			look(player, shown, s, tick);
		}
		if (tick % 100 == 0) {
			Set<UUID> online = new HashSet<>();
			players.forEach(p -> online.add(p.getUUID()));
			SHOWN.keySet().retainAll(online); // a player who left took their bar with them
			GUESTS.keySet().retainAll(online);
		}
	}

	/** The host's Skyrim's targets, in the mirror's terms, while it's in a game in the world the table names. */
	private static void readHost() {
		if (!SkyLink.active()) {
			HOST.clear();
			return;
		}
		if (!SkyLink.readSkyState(SKY)) {
			return; // Skyrim kept writing: the last ones stand
		}
		if (!SKY.inGame() || SKY.loading()) {
			HOST.clear();
			return;
		}
		int world = SkyLink.readQuestTargets(READ);
		if (world < 0) {
			return; // a torn read: the last ones stand
		}
		HOST.clear();
		if (world != 0 && world == SKY.worldId) {
			HOST.addAll(READ); // (just after a world change the table is the last world's for half a second)
		}
	}

	private static List<QuestTable.Target> targetsOf(ServerPlayer player, int tick) {
		if (SkyNet.isHost(player)) {
			return HOST;
		}
		Claimed c = GUESTS.get(player.getUUID());
		return c != null && tick - c.tick() <= GUEST_FRESH_TICKS ? c.targets() : List.of();
	}

	/** Brings the player's bar to {@code shown.markers}: new and moved markers sent, gone ones taken away. */
	private static void send(ServerPlayer player, Shown shown, QuestRules.Settings s) {
		QuestRules.Changes changes = QuestRules.changes(shown.sent, shown.markers);
		if (changes.none()) {
			return;
		}
		try {
			for (UUID id : changes.remove()) {
				player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(id));
				shown.sent.remove(id);
			}
			// Tracking an id the client has already replaces it there (a marker moved or recoloured).
			for (QuestRules.Marker m : changes.show()) {
				player.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(m.id(), icon(m, s), new BlockPos(m.x(), m.y(), m.z())));
				shown.sent.put(m.id(), m);
			}
		} catch (RuntimeException | LinkageError e) {
			barFailed = true;
			SkyCraft.LOG.warn("SkyCraft-quests: Minecraft's locator bar won't take the quest markers ({}); the action bar names the nearest quest target instead",
				e.toString());
			return;
		}
		if (shown.markers.size() != shown.loggedCount && shown.logged < 20) {
			shown.logged++;
			shown.loggedCount = shown.markers.size();
			if (shown.markers.isEmpty()) {
				SkyCraft.LOG.info("SkyCraft-quests: {}'s locator bar: no quest markers", player.getName().getString());
			} else {
				QuestRules.Marker first = shown.markers.getFirst();
				SkyCraft.LOG.info("SkyCraft-quests: {}'s locator bar: {} quest markers (the first {} at {} {} {}{})", player.getName().getString(),
					shown.markers.size(), first.label(), first.x(), first.y(), first.z(), first.target().viaDoor() ? ", its door" : "");
			}
		}
	}

	/** A marker's look: the settings' waypoint style in its quest's colour. */
	private static Waypoint.Icon icon(QuestRules.Marker m, QuestRules.Settings s) {
		Waypoint.Icon icon = new Waypoint.Icon();
		icon.style = style(s);
		icon.color = Optional.of(0xFF000000 | m.color());
		return icon;
	}

	/**
	 * Looking toward a marker names its quest on the action bar, once each time the look comes to it. Without the bar, the
	 * nearest active quest's target is named every few seconds instead.
	 */
	private static void look(ServerPlayer player, Shown shown, QuestRules.Settings s, int tick) {
		if (shown.markers.isEmpty()) {
			shown.looking = null;
			return;
		}
		Vec3 eye = player.getEyePosition();
		float yaw = player.getYRot();
		if (!s.locatorBar() || barFailed) {
			int i = QuestRules.nearestTracked(shown.markers, eye.x, eye.y, eye.z);
			if (i >= 0 && tick - shown.nearestAt >= NEAREST_EVERY_TICKS) {
				player.sendOverlayMessage(Component.literal(QuestRules.line(shown.markers.get(i), eye.x, eye.y, eye.z, yaw)));
				shown.nearestAt = tick;
			}
			return;
		}
		int i = QuestRules.lookedAt(shown.markers, eye.x, eye.z, yaw, s.lookAngle());
		if (i < 0) {
			shown.looking = null;
			return;
		}
		QuestRules.Marker m = shown.markers.get(i);
		if (!m.id().equals(shown.looking) && tick >= shown.quietUntil) {
			player.sendOverlayMessage(Component.literal(QuestRules.line(m, eye.x, eye.y, eye.z, yaw)));
			shown.looking = m.id();
			shown.quietUntil = tick + LOOK_QUIET_TICKS;
		}
	}

	/** The switch went off: every marker sent is taken away again. */
	private static void takeAllAway(MinecraftServer server) {
		int taken = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Shown shown = SHOWN.get(player.getUUID());
			if (shown == null || shown.sent.isEmpty() || barFailed) {
				continue;
			}
			try {
				for (UUID id : shown.sent.keySet()) {
					player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(id));
					taken++;
				}
			} catch (RuntimeException | LinkageError e) {
				barFailed = true;
			}
		}
		SHOWN.clear();
		GUESTS.clear();
		if (taken > 0) {
			SkyCraft.LOG.info("SkyCraft-quests: quest_markers off: {} markers taken off the locator bar", taken);
		}
	}
}
