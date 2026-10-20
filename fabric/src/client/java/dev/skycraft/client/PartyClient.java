package dev.skycraft.client;

import dev.skycraft.SkyCraft;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.link.SkySpace;
import dev.skycraft.net.SkyNet;
import dev.skycraft.party.GuestParity;
import dev.skycraft.party.PartyCodec;
import dev.skycraft.party.PartyRules;
import dev.skycraft.party.SharedWorldEvents;
import dev.skycraft.party.WorldEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * A guest's client in a friend's world, for the multiplayer features. Their Skyrim is on this PC, the world's server on
 * the host's, so this carries one to the other: with guest_parity on, this Skyrim's actors near the player (ten times a
 * second) and its loot go to the host's server (SkyNet.GuestActors, GuestLoot; party.GuestParity), and the events that
 * server sends for this Skyrim (SkyNet.GuestEvent: hits, skills, items taken, ...) come onto the link; with
 * shared_world_events on, this Skyrim's dragon fights go to the others (SkyNet.GuestDragon) and the camera shakes for
 * theirs (SkyNet.WorldEventShake). On the host's own machine none of this runs: its server reads its Skyrim itself. Each
 * client also marks which Skyrim actor stand-ins are its own Skyrim's (SkyrimActorEntity.setClientOwnership).
 */
final class PartyClient {
	/** How far from the player this Skyrim's actors are worth sending (the host's server keeps to its own guest_range). */
	private static final double SEND_RANGE = 128.0;

	private static final List<SkyLink.Actor> ACTORS = new ArrayList<>();
	/** This Skyrim's dragon fights, told once a cooldown (the host's server applies its own too). */
	private static final WorldEvents DRAGONS = new WorldEvents();
	private static int ticks;
	private static int relayed;

	private PartyClient() {
	}

	static void init() {
		// Which stand-ins are this client's own Skyrim's: the host's (owner 0) on the host's machine, the player's own on a
		// guest's. With guest parity on only those can be picked, so nobody hits or clicks an actor they can't see.
		SkyrimActorEntity.setClientOwnership(id -> {
			Minecraft minecraft = Minecraft.getInstance();
			return id == 0 ? minecraft.getSingleplayerServer() != null : minecraft.player != null && id == minecraft.player.getId();
		}, Features.on(Features.Feature.GUEST_PARITY));
		ClientTickEvents.END_CLIENT_TICK.register(PartyClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(SkyNet.GuestEvent.TYPE, (payload, context) -> relay(payload));
		ClientPlayNetworking.registerGlobalReceiver(SkyNet.WorldEventShake.TYPE, (payload, context) -> {
			// Another player's dragon or shout: this Skyrim's camera shakes a little (nothing else in it changes).
			if (Features.on(Features.Feature.SHARED_WORLD_EVENTS) && SkyLink.active() && !MinecraftView.away()
				&& Float.isFinite(payload.strength()) && Float.isFinite(payload.seconds())) {
				SkyLink.pushEvent(Proto.EV_PARTY_SHAKE, 0, payload.strength(), payload.seconds(), 0.0F, 0.0F, 0);
			}
		});
	}

	/** An event the host's server sends this guest's Skyrim (guest parity): onto the link, if it's one GuestParity sends. */
	private static void relay(SkyNet.GuestEvent e) {
		if (!Features.on(Features.Feature.GUEST_PARITY) || !SkyLink.active() || Minecraft.getInstance().getSingleplayerServer() != null
			|| !GuestParity.RELAYED.contains(e.kind()) || e.values().size() < 4 || e.ints().size() < 2) {
			return;
		}
		for (Float v : e.values()) {
			if (v == null || !Float.isFinite(v)) {
				return;
			}
		}
		List<Float> v = e.values();
		SkyLink.pushEvent(e.kind(), e.formId(), v.get(0), v.get(1), v.get(2), v.get(3), e.ints().get(0), e.ints().get(1));
		if (relayed++ < 10) {
			SkyCraft.LOG.info("SkyCraft-party: the host's server sent this Skyrim event {} ({})", e.kind(), String.format("%08X", e.formId()));
		}
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		if (minecraft.player == null || minecraft.getSingleplayerServer() != null || !SkyClient.linked()) {
			DRAGONS.reset();
			return;
		}
		boolean away = MinecraftView.away() || !dev.skycraft.SkyDimension.inMirror(minecraft.level);
		boolean parity = Features.on(Features.Feature.GUEST_PARITY);
		boolean actorsDue = parity && ticks % 2 == 0 && ClientPlayNetworking.canSend(SkyNet.GuestActors.TYPE);
		boolean dragonsDue = ticks % 10 == 0 && Features.on(Features.Feature.SHARED_WORLD_EVENTS) && ClientPlayNetworking.canSend(SkyNet.GuestDragon.TYPE);
		SkyLink.SkyState sky = SkyClient.sky();
		// (While the host hasn't said where this Skyrim world's blocks are, its actors would land at the wrong place.)
		boolean have = (actorsDue || dragonsDue) && !away && SkySpace.known(sky.worldId) && SkyLink.readActors(ACTORS);
		if (actorsDue) {
			sendActors(minecraft, sky, away, have);
		}
		if (parity && ClientPlayNetworking.canSend(SkyNet.GuestLoot.TYPE)) {
			sendLoot();
		}
		if (dragonsDue) {
			watchDragons(have);
		}
	}

	/** This Skyrim's place and its actors near the player, nearest first (SkyNet.GuestActors). */
	private static void sendActors(Minecraft minecraft, SkyLink.SkyState sky, boolean away, boolean have) {
		PartyCodec.Header header = new PartyCodec.Header(sky.worldId, sky.place, sky.gameHour, away || !sky.inGame(), sky.town);
		List<PartyCodec.Actor> near = new ArrayList<>();
		if (have) {
			Vec3 at = minecraft.player.position();
			List<SkyLink.Actor> sorted = new ArrayList<>(ACTORS);
			sorted.sort(Comparator.comparingDouble(a -> distanceSqr(a, at)));
			for (SkyLink.Actor a : sorted) {
				if (near.size() >= PartyCodec.MAX_ACTORS || distanceSqr(a, at) > SEND_RANGE * SEND_RANGE) {
					break;
				}
				near.add(new PartyCodec.Actor(a.formId(), a.flags(), (float) a.x(), (float) a.y(), (float) a.z(), a.yaw(), a.width(), a.height(),
					a.healthFrac(), a.level(), a.vendor(), a.name()));
			}
		}
		ClientPlayNetworking.send(new SkyNet.GuestActors(PartyCodec.actors(header, near)));
	}

	private static double distanceSqr(SkyLink.Actor a, Vec3 at) {
		double dx = a.x() - at.x, dy = a.y() - at.y, dz = a.z() - at.z;
		return dx * dx + dy * dy + dz * dz;
	}

	/** Whatever landed in this Skyrim's inventory (its item ring), for the host's server to convert (SkyNet.GuestLoot). */
	private static void sendLoot() {
		List<PartyCodec.Item> items = new ArrayList<>();
		SkyLink.drainItems(it -> items.add(new PartyCodec.Item(it.formId(), it.count(), it.tags(), it.material(), it.source(), it.chest(), it.quest(),
			it.container(), it.name())));
		for (int i = 0; i < items.size(); i += PartyCodec.MAX_ITEMS) {
			ClientPlayNetworking.send(new SkyNet.GuestLoot(PartyCodec.items(items.subList(i, Math.min(items.size(), i + PartyCodec.MAX_ITEMS)))));
		}
	}

	/** This Skyrim's dragon fights, for the others (SkyNet.GuestDragon). */
	private static void watchDragons(boolean have) {
		if (!have) {
			DRAGONS.reset();
			return;
		}
		List<WorldEvents.Seen> seen = new ArrayList<>(ACTORS.size());
		for (SkyLink.Actor a : ACTORS) {
			seen.add(new WorldEvents.Seen(a.formId(), a.flags(), a.name()));
		}
		for (WorldEvents.Event e : DRAGONS.actors(seen, System.nanoTime() / 1_000_000L, PartyRules.Settings.DEFAULTS)) {
			int kind = e.kind() == WorldEvents.Kind.DRAGON_ATTACK ? SharedWorldEvents.GUEST_DRAGON_ATTACK : SharedWorldEvents.GUEST_DRAGON_SLAIN;
			ClientPlayNetworking.send(new SkyNet.GuestDragon(kind, PartyCodec.name(e.name(), PartyCodec.ACTOR_NAME_BYTES)));
			SkyCraft.LOG.info("SkyCraft-party: told the world about this Skyrim's dragon {} ({})", e.name(), e.kind());
		}
	}

	/**
	 * A guest's Skyrim arrow stopped at a Minecraft block, at {@code at} in the mirror's terms (InputBridge): the host's
	 * server sticks a Minecraft arrow there (SkyNet.ArrowBlocked).
	 */
	static void arrowBlocked(Vec3 at, int code) {
		if (ClientPlayNetworking.canSend(SkyNet.ArrowBlocked.TYPE)) {
			ClientPlayNetworking.send(new SkyNet.ArrowBlocked((float) at.x, (float) at.y, (float) at.z, code));
		}
	}
}
