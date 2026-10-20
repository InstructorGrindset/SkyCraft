package dev.skycraft.party;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The multiplayer features' numbers, from {@code config/skycraft-party.txt}: how often shared world events may be told
 * (switch shared_world_events) and how much the host's server takes from a guest's own Skyrim (switch guest_parity).
 * Also the rate limit both use, and which Skyrim actors are dragons. Pure Java.
 */
public final class PartyRules {
	/**
	 * Cooldowns in seconds; ranges in blocks. {@code dragonAttack}: after "a dragon attacks", the next one about the same
	 * player's Skyrim waits this long; {@code dragonSlain}, {@code shout} likewise; {@code dungeon}: the same dungeon is
	 * told again only after this long. A guest's Skyrim: {@code guestRange} how far from the guest their actors may stand;
	 * {@code guestActors} how many of them get stand-ins; {@code lootBurst} items at once and {@code lootPerMinute} after;
	 * {@code chestBurst} dungeon chest rolls at once and {@code chestsPerHour} after; {@code killBurst} credited kills at
	 * once and {@code killsPerMinute} after.
	 */
	public record Settings(int dragonAttack, int dragonSlain, int dungeon, int shout, int guestRange, int guestActors, int lootBurst, int lootPerMinute,
		int chestBurst, int chestsPerHour, int killBurst, int killsPerMinute) {
		public static final Settings DEFAULTS = new Settings(120, 15, 300, 30, 96, 64, 64, 120, 3, 30, 12, 30);
	}

	public static final String DEFAULT_TEXT = """
		# SkyCraft multiplayer: friends who also run SkyCraft in your Minecraft world. Restart Minecraft after
		# editing. Only the host's file counts. Times are in seconds, distances in blocks.

		# Shared world events (shared_world_events in skycraft-features.txt): a dragon attacking a player, a
		# dragon slain, a player entering a dungeon and a shout are told to the others in the Skyrim mirror
		# (a title or the action bar, a sound, a little camera shake). Each kind waits this long before it's
		# told again about the same player; the same dungeon is told once in dungeon_cooldown.
		dragon_attack_cooldown = 120
		dragon_slain_cooldown = 15
		dungeon_cooldown = 300
		shout_cooldown = 30

		# Guest parity (guest_parity): a guest's own Skyrim gets drops, loot, skills and merchants through the
		# host's server, which holds it to these. The guest's Skyrim actors this close to them get stand-ins
		# to hit, the nearest guest_actors of them.
		guest_range = 96
		guest_actors = 64
		# Skyrim items a guest's Skyrim can hand over at once, then a minute's worth: the rest stay in their
		# Skyrim (pick them up again later).
		guest_loot_burst = 64
		guest_loot_per_minute = 120
		# Dungeon chest rolls (each chest once per player) at once, then an hour's worth.
		guest_chest_burst = 3
		guest_chests_per_hour = 30
		# Kills of a guest's own actors that pay drops and XP at once, then a minute's worth.
		guest_kill_burst = 12
		guest_kills_per_minute = 30
		""";

	/**
	 * Dragons by name, for a Skyrim plugin from before the actor table's dragon bit (Proto.ACTOR_DRAGON): the named
	 * dragons of Skyrim and its DLC, as loot.LootRules knows them. Any other name with "dragon" in it is one too, but
	 * not a dragon priest.
	 */
	private static final Set<String> DRAGON_NAMES = Set.of("alduin", "paarthurnax", "odahviing", "durnehviir", "vulthuryol", "viinturuth", "kruziikrel",
		"sahrotaar", "nahagliiv", "vuljotnaak", "voslaarum", "naaslaarum", "mirmulnir", "sahloknir", "krosulhah");

	private PartyRules() {
	}

	/** Whether an actor is a dragon: Skyrim's dragon bit, or (an older plugin) its name. */
	public static boolean dragon(int flags, String name) {
		if ((flags & dev.skycraft.link.Proto.ACTOR_DRAGON) != 0) {
			return true;
		}
		String n = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
		return DRAGON_NAMES.contains(n) || n.contains("dragon") && !n.contains("priest") && !n.contains("dragonborn");
	}

	/** Parses {@code key = value} lines; unknown keys and bad values go to {@code problems} and keep the default. */
	public static Settings parse(String text, List<String> problems) {
		Settings d = Settings.DEFAULTS;
		int attack = d.dragonAttack(), slain = d.dragonSlain(), dungeon = d.dungeon(), shout = d.shout(), range = d.guestRange(), actors = d.guestActors();
		int lootBurst = d.lootBurst(), lootPerMinute = d.lootPerMinute(), chestBurst = d.chestBurst(), chestsPerHour = d.chestsPerHour();
		int killBurst = d.killBurst(), killsPerMinute = d.killsPerMinute();
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			int hash = line.indexOf('#');
			if (hash >= 0) {
				line = line.substring(0, hash).strip();
			}
			if (line.isEmpty()) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				problems.add("line " + n + ": expected 'key = value'");
				continue;
			}
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).strip();
			try {
				switch (key) {
					case "dragon_attack_cooldown" -> attack = number(value, 0, 86400);
					case "dragon_slain_cooldown" -> slain = number(value, 0, 86400);
					case "dungeon_cooldown" -> dungeon = number(value, 0, 86400);
					case "shout_cooldown" -> shout = number(value, 0, 86400);
					case "guest_range" -> range = number(value, 8, 256);
					case "guest_actors" -> actors = number(value, 0, 64);
					case "guest_loot_burst" -> lootBurst = number(value, 0, 1000);
					case "guest_loot_per_minute" -> lootPerMinute = number(value, 0, 6000);
					case "guest_chest_burst" -> chestBurst = number(value, 0, 100);
					case "guest_chests_per_hour" -> chestsPerHour = number(value, 0, 3600);
					case "guest_kill_burst" -> killBurst = number(value, 0, 1000);
					case "guest_kills_per_minute" -> killsPerMinute = number(value, 0, 6000);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad value '" + value + "' for " + key);
			}
		}
		return new Settings(attack, slain, dungeon, shout, range, actors, lootBurst, lootPerMinute, chestBurst, chestsPerHour, killBurst, killsPerMinute);
	}

	private static int number(String value, int min, int max) {
		int v = Integer.parseInt(value);
		if (v < min || v > max) {
			throw new NumberFormatException(value);
		}
		return v;
	}

	/**
	 * A rate limit: holds up to {@code burst} tokens, refills {@code perTick} a server tick (20 a second), and each thing
	 * allowed takes one. A guest's Skyrim can hand over a chest's worth at once, but not a stream.
	 */
	public static final class Bucket {
		private final double burst;
		private final double perTick;
		private double tokens;
		private long last = Long.MIN_VALUE;

		public Bucket(int burst, double perTick) {
			this.burst = Math.max(0, burst);
			this.perTick = Math.max(0.0, perTick);
			this.tokens = this.burst;
		}

		/** {@code burst} at once, {@code n} a minute after. */
		public static Bucket perMinute(int burst, int n) {
			return new Bucket(burst, n / 1200.0);
		}

		/** {@code burst} at once, {@code n} an hour after. */
		public static Bucket perHour(int burst, int n) {
			return new Bucket(burst, n / 72000.0);
		}

		/** Takes a token at server tick {@code now}, if there is one. */
		public boolean take(long now) {
			this.refill(now);
			if (this.tokens >= 1.0) {
				this.tokens -= 1.0;
				return true;
			}
			return false;
		}

		/** Whole tokens left at {@code now}. */
		public int left(long now) {
			this.refill(now);
			return (int) Math.floor(this.tokens);
		}

		private void refill(long now) {
			if (this.last != Long.MIN_VALUE && now > this.last) {
				this.tokens = Math.min(this.burst, this.tokens + (now - this.last) * this.perTick);
			}
			this.last = now; // (a clock that went back, a restarted server's, refills from there on)
		}
	}
}
