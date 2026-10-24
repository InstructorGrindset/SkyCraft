package dev.skycraft.magic;

import dev.skycraft.link.Proto;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The numbers of Shouts and Spells (switches {@code shouts} and {@code spells}), from {@code config/skycraft-magic.txt}, and the
 * pure rules that use them: how long the Shout key is held to how many words, what each shout and spell kind does on
 * Minecraft's side and how hard, and what the HUD says when Skyrim says no. Skyrim shouts and casts for real (its sound,
 * visuals, people, voice recovery and magicka); everything here is only what Minecraft adds to its own mobs and player.
 * Pure, so it's unit tested on its own.
 */
public final class MagicRules {
	private MagicRules() {
	}

	/** What a spell kind does on Minecraft's side ({@link #spellAction}). */
	public static final int ACTION_NONE = 0;
	/** Hurts and sets alight the mob it hits; lights a fire on a Minecraft block it hits. */
	public static final int ACTION_IGNITE = 1;
	/** Hurts and slows the mob it hits. */
	public static final int ACTION_CHILL = 2;
	/** Hurts the mob it hits, more than fire. */
	public static final int ACTION_SHOCK = 3;
	/** Heals the player (a self spell) or whoever it hits (an aimed one). */
	public static final int ACTION_HEAL = 4;
	/** Minecraft's Resistance for a moment. */
	public static final int ACTION_WARD = 5;

	/** Settings from {@code config/skycraft-magic.txt}. Arrays hold the value for 1, 2 and 3 words. */
	public record Settings(double twoWordsAfter, double threeWordsAfter, double coneDegrees, double[] forceReach, double forcePushPerWord,
		double forceDamagePerWord, double forceLift, double[] breathReach, double breathDamagePerWord, double breathSecondsPerWord, double[] sprintBlocks, double sprintSpeed,
		double[] etherealSeconds, double[] slowTimeSeconds, double slowTimeReach, int slowTimeLevel, int clearSkiesSeconds, double spellReach,
		double touchReach, double damagePerMagicka, double maxSpellDamage, double frostDamage, double frostSeconds, double shockDamage,
		double fireSeconds, double fireSplash, boolean fireLightsBlocks, double healPerMagicka, double wardSeconds, int wardLevel,
		boolean concentrationPulses, double pulseSeconds, double hudSeconds) {
		public static final Settings DEFAULTS = new Settings(0.35, 0.8, 35.0, new double[] { 6.0, 8.0, 10.0 }, 0.9, 1.0, 0.3, new double[] { 5.0, 6.0, 7.0 },
			2.0, 3.0, new double[] { 5.0, 9.0, 14.0 }, 1.4, new double[] { 8.0, 13.0, 18.0 }, new double[] { 8.0, 12.0, 16.0 }, 24.0, 4, 300, 32.0, 4.0, 0.12,
			20.0, 0.8, 4.0, 1.3, 4.0, 1.5, true, 0.12, 3.0, 2, true, 0.5, 6.0);
	}

	public static final String FILE = "skycraft-magic.txt";

	public static final String DEFAULT_TEXT = """
		# SkyCraft magic: Skyrim's shouts and spells from Minecraft's keys. Shout (V), Next shout (B), Cast spell (R) and Next
		# spell (N; sneak for the one before) are in Minecraft's Controls under SkyCraft. The switches are shouts and spells in
		# skycraft-features.txt. Skyrim shouts and casts for real (its sound, visuals, its people, voice recovery and magicka);
		# these numbers are only what Minecraft adds to its own mobs and player. Restart Minecraft after editing. Damage and
		# healing are in Minecraft health points (a heart is 2).

		# The Shout key: a tap shouts one word; held this long (seconds) two words, then three, as Skyrim's shout key does, never
		# more than you have unlocked. Held to the last word you have, it shouts at once.
		two_words_after = 0.35
		three_words_after = 0.8

		# Shouts reach the mobs in front of you: within cone_degrees of where you look (each side), and within the shout's reach.
		cone_degrees = 35

		# Unrelenting Force: its reach (blocks) for 1, 2 and 3 words, its push for each word (Minecraft knockback: a punch is
		# 0.4), its damage for each word, and how far up three words throw them too (blocks a tick).
		force_reach = 6 8 10
		force_push_per_word = 0.9
		force_damage_per_word = 1
		force_lift = 0.3

		# Fire Breath and Frost Breath: reach for 1, 2 and 3 words, damage for each word, and seconds of burning (fire) or
		# slowness (frost) for each word.
		breath_reach = 5 6 7
		breath_damage_per_word = 2
		breath_seconds_per_word = 3

		# Whirlwind Sprint: blocks dashed for 1, 2 and 3 words, at this speed (blocks a tick; 20 ticks a second).
		sprint_blocks = 5 9 14
		sprint_speed = 1.4

		# Become Ethereal: seconds nothing in Minecraft hurts you, for 1, 2 and 3 words (Skyrim's own: 8, 13, 18).
		ethereal_seconds = 8 13 18

		# Slow Time: seconds Minecraft's mobs within slow_time_reach blocks are slowed, for 1, 2 and 3 words (Skyrim's own: 8,
		# 12, 16), and how much (Minecraft's Slowness level, 1 to 6).
		slow_time_seconds = 8 12 16
		slow_time_reach = 24
		slow_time_level = 4

		# Clear Skies: seconds Minecraft's weather stays clear.
		clear_skies_seconds = 300

		# Spells: how far (blocks) an aimed spell reaches in Minecraft (a touch spell: touch_reach), its damage for each point of
		# magicka it cost (a Firebolt, about 40 magicka, hurts about 5) and the most one cast does.
		spell_reach = 32
		touch_reach = 4
		damage_per_magicka = 0.12
		max_spell_damage = 20
		# Frost spells hurt this share as much and slow for frost_seconds; shock spells hurt this much more; fire spells set
		# what they hit alight for fire_seconds.
		frost_damage = 0.8
		frost_seconds = 4
		shock_damage = 1.3
		fire_seconds = 4
		# A fire spell that hits a block sets alight the mobs within fire_splash blocks of where it hit, and lights a fire on
		# a Minecraft block's face (on or off; never on Skyrim's own ground).
		fire_splash = 1.5
		fire_lights_blocks = on

		# Healing spells heal you (or, aimed at someone, whoever they hit) this many health points for each point of magicka.
		heal_per_magicka = 0.12
		# A ward gives Minecraft's Resistance (level 1 to 4) for this many seconds a cast.
		ward_seconds = 3
		ward_level = 2

		# Concentration spells (Flames, Healing, Sparks, ...): while Cast spell is held, Skyrim casts them again every
		# pulse_seconds and takes their magicka per second for it. Off: once each press, like the others.
		concentration_pulses = on
		pulse_seconds = 0.5

		# The HUD (beside the hotbar): the spell's name and the magicka bar, and the shout's name and the voice bar, show for
		# this many seconds after you use their keys (the voice bar also while your voice recovers).
		hud_seconds = 6
		""";

	// ---- the Shout key ----------------------------------------------------------------------------

	/**
	 * Words of power for the Shout key held {@code held} seconds: one for a tap, two from two_words_after, three from
	 * three_words_after, never more than {@code unlocked} (0 when none is unlocked).
	 */
	public static int words(double held, int unlocked, Settings s) {
		int most = Math.min(unlocked, 3);
		if (most <= 0) {
			return 0;
		}
		int w = held >= s.threeWordsAfter() ? 3 : held >= s.twoWordsAfter() ? 2 : 1;
		return Math.min(w, most);
	}

	/** Whether the key has been held to the last word unlocked: the shout goes at once, as in Skyrim. */
	public static boolean fullyCharged(double held, int unlocked, Settings s) {
		int most = Math.min(unlocked, 3);
		return most <= 1 || words(held, unlocked, s) >= most;
	}

	// ---- what Minecraft adds ----------------------------------------------------------------------

	/** The value for {@code words} words (1 to 3) of a per-words list. */
	public static double forWords(double[] perWords, int words) {
		return perWords[Math.max(1, Math.min(words, perWords.length)) - 1];
	}

	/** Whether Minecraft's server does something for a shout of this kind (Whirlwind Sprint is the client's own dash). */
	public static boolean shoutOnServer(int kind) {
		return kind == Proto.SHOUT_KIND_FORCE || kind == Proto.SHOUT_KIND_FIRE_BREATH || kind == Proto.SHOUT_KIND_FROST_BREATH
			|| kind == Proto.SHOUT_KIND_ETHEREAL || kind == Proto.SHOUT_KIND_SLOW_TIME || kind == Proto.SHOUT_KIND_CLEAR_SKIES;
	}

	/** How far (blocks) a cone shout of this kind reaches with {@code words} words; 0 for the others. */
	public static double shoutReach(int kind, int words, Settings s) {
		return switch (kind) {
			case Proto.SHOUT_KIND_FORCE -> forWords(s.forceReach(), words);
			case Proto.SHOUT_KIND_FIRE_BREATH, Proto.SHOUT_KIND_FROST_BREATH -> forWords(s.breathReach(), words);
			default -> 0.0;
		};
	}

	/** Unrelenting Force's push (Minecraft knockback strength) with {@code words} words. */
	public static double forcePush(int words, Settings s) {
		return s.forcePushPerWord() * clampWords(words);
	}

	/** Damage a cone shout of this kind does with {@code words} words (Minecraft health points). */
	public static float shoutDamage(int kind, int words, Settings s) {
		return switch (kind) {
			case Proto.SHOUT_KIND_FORCE -> (float) (s.forceDamagePerWord() * clampWords(words));
			case Proto.SHOUT_KIND_FIRE_BREATH, Proto.SHOUT_KIND_FROST_BREATH -> (float) (s.breathDamagePerWord() * clampWords(words));
			default -> 0.0F;
		};
	}

	/** Ticks a breath sets alight (fire) or slows (frost) with {@code words} words. */
	public static int breathTicks(int words, Settings s) {
		return ticks(s.breathSecondsPerWord() * clampWords(words));
	}

	/** Ticks of Whirlwind Sprint's dash with {@code words} words, at {@link Settings#sprintSpeed} blocks a tick. */
	public static int sprintTicks(int words, Settings s) {
		return s.sprintSpeed() > 0.0 ? (int) Math.ceil(forWords(s.sprintBlocks(), words) / s.sprintSpeed()) : 0;
	}

	/** The dash's velocity (blocks a tick, x and z) facing {@code yaw} (Minecraft degrees: 0 south +z, 90 west -x). */
	public static double[] sprintVelocity(float yaw, Settings s) {
		double r = Math.toRadians(yaw);
		return new double[] { -Math.sin(r) * s.sprintSpeed(), Math.cos(r) * s.sprintSpeed() };
	}

	/** Ticks Become Ethereal keeps the player unhurt with {@code words} words. */
	public static int etherealTicks(int words, Settings s) {
		return ticks(forWords(s.etherealSeconds(), words));
	}

	/** Ticks Slow Time slows the mobs around with {@code words} words. */
	public static int slowTimeTicks(int words, Settings s) {
		return ticks(forWords(s.slowTimeSeconds(), words));
	}

	/**
	 * Whether a point {@code (dx, dy, dz)} from the player's eyes lies within {@code coneDegrees} of the look {@code (lx, ly, lz)}
	 * (unit length). The eyes' own spot counts as in front.
	 */
	public static boolean inCone(double dx, double dy, double dz, double lx, double ly, double lz, double coneDegrees) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0E-6) {
			return true;
		}
		return (dx * lx + dy * ly + dz * lz) / len >= Math.cos(Math.toRadians(coneDegrees));
	}

	/** What a spell of this kind does on Minecraft's side (ACTION_*). */
	public static int spellAction(int kind) {
		return switch (kind) {
			case Proto.SPELL_KIND_FIRE -> ACTION_IGNITE;
			case Proto.SPELL_KIND_FROST -> ACTION_CHILL;
			case Proto.SPELL_KIND_SHOCK -> ACTION_SHOCK;
			case Proto.SPELL_KIND_HEAL -> ACTION_HEAL;
			case Proto.SPELL_KIND_WARD -> ACTION_WARD;
			default -> ACTION_NONE;
		};
	}

	/**
	 * Damage (Minecraft health points) a spell of this kind that took {@code magicka} does: damage_per_magicka for each point,
	 * frost_damage and shock_damage times that for frost and shock, never over max_spell_damage. 0 for the kinds that don't hurt.
	 */
	public static float spellDamage(int kind, float magicka, Settings s) {
		double mult = switch (spellAction(kind)) {
			case ACTION_IGNITE -> 1.0;
			case ACTION_CHILL -> s.frostDamage();
			case ACTION_SHOCK -> s.shockDamage();
			default -> 0.0;
		};
		double damage = Math.max(0.0F, magicka) * s.damagePerMagicka() * mult;
		return (float) Math.min(damage, s.maxSpellDamage());
	}

	/** Health points a healing spell that took {@code magicka} restores, at most a full bar (20). */
	public static float spellHeal(float magicka, Settings s) {
		return (float) Math.min(Math.max(0.0F, magicka) * s.healPerMagicka(), 20.0);
	}

	/** Seconds as ticks (20 a second), never negative. */
	public static int ticks(double seconds) {
		return (int) Math.round(Math.max(0.0, seconds) * 20.0);
	}

	private static int clampWords(int words) {
		return Math.max(1, Math.min(words, 3));
	}

	// ---- what the HUD says ------------------------------------------------------------------------

	/** The HUD's line for Skyrim's answer to a shout ({@code result} a Proto.SHOUT_*), or null when it shouted. */
	public static @Nullable String shoutAnswer(int result, String name, float secondsLeft) {
		String what = name.isEmpty() ? "That shout" : name;
		return switch (result) {
			case Proto.SHOUT_DONE -> null;
			case Proto.SHOUT_RECOVERING -> "Your voice needs " + Math.max(1, (int) Math.ceil(secondsLeft)) + " more seconds";
			case Proto.SHOUT_UNKNOWN -> what + ": no word of it is yours yet";
			case Proto.SHOUT_CANT_NOW -> "Skyrim can't shout right now";
			default -> "Skyrim didn't shout (" + result + ")";
		};
	}

	/** The HUD's line for Skyrim's answer to a cast ({@code result} a Proto.CAST_*), or null when it was cast. */
	public static @Nullable String castAnswer(int result, String name, float magickaNeeded) {
		String what = name.isEmpty() ? "That spell" : name;
		return switch (result) {
			case Proto.CAST_DONE -> null;
			case Proto.CAST_NO_MAGICKA -> "Not enough magicka" + (magickaNeeded > 0.0F ? " for " + what + " (" + Math.round(magickaNeeded) + ")" : "");
			case Proto.CAST_UNKNOWN -> what + ": Skyrim doesn't know it";
			case Proto.CAST_CANT_NOW -> "Skyrim can't cast right now";
			default -> "Skyrim didn't cast (" + result + ")";
		};
	}

	/** A school's name, from its ActorValue (Proto.SCHOOL_*). */
	public static String schoolName(int school) {
		return switch (school) {
			case Proto.SCHOOL_ALTERATION -> "Alteration";
			case Proto.SCHOOL_CONJURATION -> "Conjuration";
			case Proto.SCHOOL_DESTRUCTION -> "Destruction";
			case Proto.SCHOOL_ILLUSION -> "Illusion";
			case Proto.SCHOOL_RESTORATION -> "Restoration";
			default -> "no school";
		};
	}

	// ---- a guest's rate ---------------------------------------------------------------------------

	/** At most {@code most} things in any {@code windowSeconds}: what the host's server takes from one guest's Skyrim. */
	public static final class Rate {
		private final int most;
		private final double window;
		private final ArrayDeque<Double> times = new ArrayDeque<>();

		public Rate(int most, double windowSeconds) {
			this.most = most;
			this.window = windowSeconds;
		}

		/** Whether one more fits at {@code now} (seconds); it counts if it does. */
		public boolean take(double now) {
			while (!this.times.isEmpty() && now - this.times.peekFirst() >= this.window) {
				this.times.pollFirst();
			}
			if (this.times.size() >= this.most) {
				return false;
			}
			this.times.addLast(now);
			return true;
		}
	}

	// ---- the file ---------------------------------------------------------------------------------

	/** Parses {@code key = value} lines; bad ones keep the default and go to {@code problems}. */
	public static Settings parse(String text, List<String> problems) {
		Settings d = Settings.DEFAULTS;
		double two = d.twoWordsAfter(), three = d.threeWordsAfter(), cone = d.coneDegrees(), push = d.forcePushPerWord(), forceDamage = d.forceDamagePerWord();
		double lift = d.forceLift(), touch = d.touchReach(), splash = d.fireSplash();
		double[] forceReach = d.forceReach().clone(), breathReach = d.breathReach().clone(), sprintBlocks = d.sprintBlocks().clone();
		double[] ethereal = d.etherealSeconds().clone(), slowTime = d.slowTimeSeconds().clone();
		double breathDamage = d.breathDamagePerWord(), breathSeconds = d.breathSecondsPerWord(), sprintSpeed = d.sprintSpeed(), slowReach = d.slowTimeReach();
		int slowLevel = d.slowTimeLevel(), clearSkies = d.clearSkiesSeconds(), wardLevel = d.wardLevel();
		double spellReach = d.spellReach(), perMagicka = d.damagePerMagicka(), maxDamage = d.maxSpellDamage(), frost = d.frostDamage(), frostSeconds = d.frostSeconds();
		double shock = d.shockDamage(), fireSeconds = d.fireSeconds(), heal = d.healPerMagicka(), wardSeconds = d.wardSeconds(), pulse = d.pulseSeconds();
		double hud = d.hudSeconds();
		boolean lights = d.fireLightsBlocks(), pulses = d.concentrationPulses();
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
					case "two_words_after" -> two = number(value, 0.0, 10.0);
					case "three_words_after" -> three = number(value, 0.0, 10.0);
					case "cone_degrees" -> cone = number(value, 1.0, 180.0);
					case "force_reach" -> forceReach = three(value, 0.0, 64.0);
					case "force_push_per_word" -> push = number(value, 0.0, 10.0);
					case "force_damage_per_word" -> forceDamage = number(value, 0.0, 100.0);
					case "force_lift" -> lift = number(value, 0.0, 4.0);
					case "breath_reach" -> breathReach = three(value, 0.0, 64.0);
					case "breath_damage_per_word" -> breathDamage = number(value, 0.0, 100.0);
					case "breath_seconds_per_word" -> breathSeconds = number(value, 0.0, 600.0);
					case "sprint_blocks" -> sprintBlocks = three(value, 0.0, 64.0);
					case "sprint_speed" -> sprintSpeed = number(value, 0.1, 4.0);
					case "ethereal_seconds" -> ethereal = three(value, 0.0, 600.0);
					case "slow_time_seconds" -> slowTime = three(value, 0.0, 600.0);
					case "slow_time_reach" -> slowReach = number(value, 0.0, 128.0);
					case "slow_time_level" -> slowLevel = (int) number(value, 1.0, 6.0);
					case "clear_skies_seconds" -> clearSkies = (int) number(value, 1.0, 86400.0);
					case "spell_reach" -> spellReach = number(value, 1.0, 128.0);
					case "touch_reach" -> touch = number(value, 1.0, 16.0);
					case "damage_per_magicka" -> perMagicka = number(value, 0.0, 10.0);
					case "max_spell_damage" -> maxDamage = number(value, 0.0, 1000.0);
					case "frost_damage" -> frost = number(value, 0.0, 10.0);
					case "frost_seconds" -> frostSeconds = number(value, 0.0, 600.0);
					case "shock_damage" -> shock = number(value, 0.0, 10.0);
					case "fire_seconds" -> fireSeconds = number(value, 0.0, 600.0);
					case "fire_splash" -> splash = number(value, 0.0, 8.0);
					case "fire_lights_blocks" -> lights = onOff(value);
					case "heal_per_magicka" -> heal = number(value, 0.0, 10.0);
					case "ward_seconds" -> wardSeconds = number(value, 0.0, 600.0);
					case "ward_level" -> wardLevel = (int) number(value, 1.0, 4.0);
					case "concentration_pulses" -> pulses = onOff(value);
					case "pulse_seconds" -> pulse = number(value, 0.1, 5.0);
					case "hud_seconds" -> hud = number(value, 0.0, 600.0);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad value '" + value + "' for " + key);
			}
		}
		if (three < two) {
			problems.add("three_words_after is shorter than two_words_after: both taken as " + two);
			three = two;
		}
		return new Settings(two, three, cone, forceReach, push, forceDamage, lift, breathReach, breathDamage, breathSeconds, sprintBlocks, sprintSpeed,
			ethereal, slowTime, slowReach, slowLevel, clearSkies, spellReach, touch, perMagicka, maxDamage, frost, frostSeconds, shock, fireSeconds, splash,
			lights, heal, wardSeconds, wardLevel, pulses, pulse, hud);
	}

	private static double number(String value, double min, double max) {
		double v = Double.parseDouble(value);
		if (!(v >= min && v <= max)) {
			throw new NumberFormatException(value);
		}
		return v;
	}

	/** Three numbers (for 1, 2 and 3 words), separated by spaces or commas. */
	private static double[] three(String value, double min, double max) {
		String[] parts = value.split("[\\s,]+");
		if (parts.length != 3) {
			throw new NumberFormatException(value);
		}
		double[] out = new double[3];
		for (int i = 0; i < 3; i++) {
			out[i] = number(parts[i], min, max);
		}
		return out;
	}

	private static boolean onOff(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "on", "true", "yes" -> true;
			case "off", "false", "no" -> false;
			default -> throw new NumberFormatException(value);
		};
	}
}
