package dev.skycraft.party;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * Shared world events (switch shared_world_events): what happens in the host's Skyrim that everyone in the world should
 * feel. Twice a second the host's server reads its own Skyrim (actor table, place, shouts) and {@link WorldEvents} says
 * what's news: a dragon attacking the host, a dragon slain, the host going into a dungeon, a shout. Every other player
 * in the Skyrim mirror gets it: a title (dragons) or an action bar line, a sound, and for dragons and shouts a small
 * shake of their own Skyrim's camera (kEvPartyShake: their client passes it on). Nothing in their Skyrim changes but the
 * camera. The cheap way back: a guest's client watches its own Skyrim for dragon fights (SkyNet.GuestDragon) and the
 * others are told the same way. {@code /skyparty} shows who's who; {@code /skyparty test <event>} shows one to you.
 * Cooldowns in skycraft-party.txt. Log: SkyCraft-party. Server thread.
 */
public final class SharedWorldEvents {
	/** SkyNet.GuestDragon's kinds. */
	public static final int GUEST_DRAGON_ATTACK = 1, GUEST_DRAGON_SLAIN = 2;
	/** The Skyrim player's FormID: a shout by the player. */
	private static final int PLAYER_FORM = 0x14;
	/** Players within this of one the sound already played at hear that one. */
	private static final double SOUND_SHARED = 16.0;

	private static final WorldEvents HOST = new WorldEvents();
	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	private static final List<SkyLink.Actor> ACTORS = new ArrayList<>();
	/** Each guest's last dragon attack and dragon slain told (milliseconds). */
	private static final Map<UUID, long[]> GUEST_TOLD = new HashMap<>();

	/** A sound for an event, played at a place (each call site names its own sound, so either kind of sound constant fits). */
	private interface Sound {
		void play(ServerLevel level, double x, double y, double z);
	}

	private SharedWorldEvents() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SharedWorldEvents::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("skyparty")
			.executes(c -> status(c.getSource()))
			.then(Commands.literal("test")
				.then(Commands.literal("dragon").executes(c -> test(c.getSource(), WorldEvents.Kind.DRAGON_ATTACK)))
				.then(Commands.literal("slain").executes(c -> test(c.getSource(), WorldEvents.Kind.DRAGON_SLAIN)))
				.then(Commands.literal("dungeon").executes(c -> test(c.getSource(), WorldEvents.Kind.DUNGEON)))
				.then(Commands.literal("shout").executes(c -> test(c.getSource(), WorldEvents.Kind.SHOUT))))));
	}

	/** The shared_world_events switch. */
	public static boolean on() {
		return Features.on(Features.Feature.SHARED_WORLD_EVENTS);
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 10 != 0) {
			return;
		}
		if (t % 1200 == 0) {
			GUEST_TOLD.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
		ServerPlayer host = host(server);
		if (!on() || host == null || !SkyLink.active() || !SkyDimension.inMirror(host) || !SkyLink.readSkyState(SKY) || !SKY.inGame() || SKY.loading()) {
			HOST.reset(); // what's there when it's back isn't news
			return;
		}
		long now = now();
		PartyRules.Settings s = GuestParity.settings();
		WorldEvents.Event dungeon = HOST.place(SKY.place, SKY.town, now, s);
		if (dungeon != null) {
			tell(server, host, dungeon, false);
		}
		if (SkyLink.readActors(ACTORS)) {
			List<WorldEvents.Seen> seen = new ArrayList<>(ACTORS.size());
			for (SkyLink.Actor a : ACTORS) {
				seen.add(new WorldEvents.Seen(a.formId(), a.flags(), a.name()));
			}
			for (WorldEvents.Event e : HOST.actors(seen, now, s)) {
				tell(server, host, e, false);
			}
		}
		WorldEvents.Event shout = HOST.shout(SKY.shoutSeq, SKY.shoutFlags, SKY.shoutWords, shouter(host), now, s);
		if (shout != null) {
			tell(server, host, shout, false);
		}
	}

	/** Who SkyState says shouted last: the host, an actor in the table by name, or "" (a dragon or someone out of the table). */
	private static String shouter(ServerPlayer host) {
		if ((SKY.shoutFlags & Proto.SHOUT_BY_PLAYER) != 0 || SKY.shoutActor == PLAYER_FORM) {
			return host.getName().getString();
		}
		for (SkyLink.Actor a : ACTORS) {
			if (a.formId() == SKY.shoutActor) {
				return a.name();
			}
		}
		return "";
	}

	/** A dragon fight in a guest's own Skyrim (SkyNet.GuestDragon): the others are told, at most once a cooldown per guest. */
	public static void onGuestDragon(ServerPlayer guest, int kind, List<Integer> nameData) {
		if (!on() || SkyNet.isHost(guest) || !SkyDimension.inMirror(guest) || (kind != GUEST_DRAGON_ATTACK && kind != GUEST_DRAGON_SLAIN)) {
			return;
		}
		String name;
		try {
			name = PartyCodec.readName(nameData, PartyCodec.ACTOR_NAME_BYTES);
		} catch (IllegalArgumentException e) {
			return;
		}
		long now = now();
		PartyRules.Settings s = GuestParity.settings();
		long[] last = GUEST_TOLD.computeIfAbsent(guest.getUUID(), id -> new long[] { Long.MIN_VALUE, Long.MIN_VALUE });
		int i = kind - 1;
		int cooldown = kind == GUEST_DRAGON_ATTACK ? s.dragonAttack() : s.dragonSlain();
		if (last[i] != Long.MIN_VALUE && now - last[i] < cooldown * 1000L) {
			return; // their client keeps its own cooldown too; this one is the host's word on it
		}
		last[i] = now;
		WorldEvents.Kind k = kind == GUEST_DRAGON_ATTACK ? WorldEvents.Kind.DRAGON_ATTACK : WorldEvents.Kind.DRAGON_SLAIN;
		tell(guest.level().getServer(), guest, new WorldEvents.Event(k, name, 0, 0), true);
	}

	/**
	 * Tells everyone else in the mirror what happened in {@code about}'s Skyrim ({@code theirOwn}: a guest's own Skyrim,
	 * which nobody else's has).
	 */
	static void tell(MinecraftServer server, ServerPlayer about, WorldEvents.Event e, boolean theirOwn) {
		String who = about.getName().getString();
		List<ServerPlayer> told = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p != about && SkyDimension.inMirror(p)) {
				present(p, e, who, theirOwn);
				told.add(p);
			}
		}
		// One sound for players close together (each would hear the others' too).
		List<ServerPlayer> played = new ArrayList<>();
		for (ServerPlayer p : told) {
			boolean near = false;
			for (ServerPlayer q : played) {
				near |= q.level() == p.level() && q.distanceToSqr(p) < SOUND_SHARED * SOUND_SHARED;
			}
			if (!near) {
				sound(e.kind()).play(p.level(), p.getX(), p.getY(), p.getZ());
				played.add(p);
			}
		}
		SkyCraft.LOG.info("SkyCraft-party: {}'s {}Skyrim: {} '{}'{} told to {} player{}", who, theirOwn ? "own " : "", e.kind().name().toLowerCase(Locale.ROOT),
			e.name(), e.kind() == WorldEvents.Kind.SHOUT && e.words() > 0 ? " (" + e.words() + " words)" : "", told.size(), told.size() == 1 ? "" : "s");
	}

	/** One player sees and feels an event: its words, and for dragons and shouts their own Skyrim's camera shakes. */
	private static void present(ServerPlayer p, WorldEvents.Event e, String who, boolean theirOwn) {
		String in = theirOwn ? " in their Skyrim" : "";
		String dragon = e.name().isBlank() || e.name().strip().equalsIgnoreCase("dragon") ? "A dragon" : e.name().strip();
		switch (e.kind()) {
			case DRAGON_ATTACK -> {
				title(p, Component.literal("Dragon attack!").withStyle(ChatFormatting.DARK_RED), dragon + " attacks " + who + in);
				shake(p, 0.5F, 1.5F);
			}
			case DRAGON_SLAIN -> {
				title(p, Component.literal("Dragon slain").withStyle(ChatFormatting.GOLD), dragon + " falls before " + who + in);
				shake(p, 0.7F, 2.0F);
			}
			case DUNGEON -> p.sendOverlayMessage(Component.literal(who + " enters " + e.name()).withStyle(ChatFormatting.GRAY));
			case SHOUT -> {
				String words = e.words() > 1 ? " (" + e.words() + " words)" : "";
				String line = (e.flags() & Proto.SHOUT_BY_PLAYER) != 0 ? who + " shouts" + words
					: (e.name().isBlank() ? (e.flags() & Proto.SHOUT_BY_DRAGON) != 0 ? "A dragon" : "Someone" : e.name()) + " shouts at " + who + words;
				p.sendOverlayMessage(Component.literal(line).withStyle(ChatFormatting.AQUA));
				shake(p, 0.3F, 0.6F);
			}
		}
	}

	private static void title(ServerPlayer p, Component title, String subtitle) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** Shakes {@code p}'s own Skyrim's camera: the host's over the link, a guest's through their client (which may have the switch off). */
	private static void shake(ServerPlayer p, float strength, float seconds) {
		if (SkyNet.isHost(p)) {
			if (SkyLink.active()) {
				SkyLink.pushEvent(Proto.EV_PARTY_SHAKE, 0, strength, seconds, 0.0F, 0.0F, 0);
			}
		} else if (ServerPlayNetworking.canSend(p, SkyNet.WorldEventShake.TYPE)) {
			ServerPlayNetworking.send(p, new SkyNet.WorldEventShake(strength, seconds));
		}
	}

	private static Sound sound(WorldEvents.Kind kind) {
		return switch (kind) {
			case DRAGON_ATTACK -> (level, x, y, z) -> level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.AMBIENT, 1.0F, 0.9F);
			case DRAGON_SLAIN -> (level, x, y, z) -> level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.AMBIENT, 0.4F, 1.0F);
			case DUNGEON -> (level, x, y, z) -> level.playSound(null, x, y, z, SoundEvents.IRON_DOOR_OPEN, SoundSource.AMBIENT, 0.5F, 0.6F);
			case SHOUT -> (level, x, y, z) -> level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.AMBIENT, 0.6F, 1.5F);
		};
	}

	private static @Nullable ServerPlayer host(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (SkyNet.isHost(p)) {
				return p;
			}
		}
		return null;
	}

	// ---- /skyparty -------------------------------------------------------------------------------------

	private static int status(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		List<String> lines = new ArrayList<>();
		lines.add("SkyCraft multiplayer: guest_parity " + (GuestParity.on() ? "on" : "off") + ", shared_world_events " + (on() ? "on" : "off"));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			lines.add(" " + p.getName().getString() + ": " + GuestParity.describe(p) + (SkyDimension.inMirror(p) ? "" : ", away from the mirror"));
		}
		String text = String.join("\n", lines);
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	/** Shows one made-up event to whoever asked, about themselves, as the others would see it (no cooldown, nobody else told). */
	private static int test(CommandSourceStack source, WorldEvents.Kind kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		WorldEvents.Event e = switch (kind) {
			case DRAGON_ATTACK -> new WorldEvents.Event(kind, "Mirmulnir", 0, 0);
			case DRAGON_SLAIN -> new WorldEvents.Event(kind, "Mirmulnir", 0, 0);
			case DUNGEON -> new WorldEvents.Event(kind, "Bleak Falls Barrow", 0, 0);
			case SHOUT -> new WorldEvents.Event(kind, "Mirmulnir", Proto.SHOUT_BY_DRAGON, 3);
		};
		present(player, e, player.getName().getString(), false);
		sound(kind).play(player.level(), player.getX(), player.getY(), player.getZ());
		source.sendSuccess(() -> Component.literal("Shown to you as the other players would see it" + (on() ? "" : " (shared_world_events is off)")), false);
		return 1;
	}
}
