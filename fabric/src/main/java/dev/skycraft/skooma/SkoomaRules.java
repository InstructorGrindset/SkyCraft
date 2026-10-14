package dev.skycraft.skooma;

import java.util.List;
import java.util.Locale;

/**
 * Skooma's addiction on the Minecraft side (switch {@code skooma_addiction}): one player's habit and the rules it
 * follows. Pure Java, unit tested without Minecraft; SkySkooma calls it with the world's game time (which keeps
 * running while SkyCraft holds the sun still) and its own random numbers, and keeps each player's {@link Habit} on
 * the player.
 *
 * <ul>
 * <li>A dose is any time a player gets the {@code skycraft:skooma} effect: a potion, a splash, a command. Within a
 * minute of a dose it's the same dose ({@link #SAME_DOSE}).
 * <li>Tolerance: each dose adds one, and it halves every {@code tolerance_half_life}.
 * <li>Each dose may hook a player who isn't addicted yet: {@code chance}, plus {@code chance_per_tolerance} for each
 * point of tolerance before the dose, plus {@code chance_per_strength} for each level above I (Redwater Skooma is II).
 * So doses close together are the risky ones.
 * <li>Withdrawal: an addict {@code withdrawal_after} without a dose goes into withdrawal I, a level worse every
 * {@code withdrawal_step} after that, up to III. A dose ends it at once.
 * <li>Each dose taken while addicted (a relapse) puts the cure off by {@code recovery_per_relapse}, up to four times.
 * <li>Recovery: {@code recovery_after} clean since the last dose, plus the relapses', cures the addiction, once the
 * player has lived through {@code recovery_endured} of withdrawal online. Milk masks the symptoms but not the time, and
 * a player who stays away while the world runs on comes back to the withdrawal they skipped: there's no sleeping it
 * off.
 * </ul>
 */
public final class SkoomaRules {
	/** SkyCraft's effect ids (namespace skycraft; SkyEffects registers them, item conversion's rules name them). */
	public static final String SKOOMA = "skooma", STEALTH = "stealth", WITHDRAWAL = "skooma_withdrawal";
	public static final List<String> EFFECT_IDS = List.of(SKOOMA, STEALTH, WITHDRAWAL);
	/** Withdrawal's worst level. */
	public static final int MAX_WITHDRAWAL = 3;
	/** Relapses that put the cure off; more count as this many. */
	public static final int MAX_RELAPSES = 4;
	/** Ticks in a Minecraft day. */
	public static final long DAY = 24000;
	/**
	 * Skooma again within this many ticks of a counted dose is part of that dose: a lingering cloud gives the effect
	 * every second, and two bottles in a row are one binge.
	 */
	public static final long SAME_DOSE = 1200;

	/** Settings from {@code config/skycraft-skooma.txt}; times in ticks. */
	public record Settings(double chance, double chancePerTolerance, double chancePerStrength, long toleranceHalfLife, long withdrawalAfter,
		long withdrawalStep, long recoveryAfter, long recoveryPerRelapse, long recoveryEndured, long symptomsReturn, int comedown) {
		public static final Settings DEFAULTS = new Settings(0.05, 0.15, 0.15, 12000, 24000, 24000, 72000, 12000, 24000, 3600, 600);
	}

	public static final String DEFAULT_TEXT = """
		# SkyCraft Skooma: an addiction on the Minecraft side. The switch is skooma_addiction in
		# skycraft-features.txt (the Skooma, Stealth and Skooma Withdrawal effects work either way).
		# Restart Minecraft after editing.
		# Times are in ticks: 20 a second, 24000 a Minecraft day (20 minutes). They follow the world's game
		# time, which keeps running while SkyCraft holds the sun still.

		# A dose is any time a player gets the Skooma effect (a potion, a splash, a command). It may make them
		# addicted: chance, plus chance_per_tolerance for each point of tolerance (each dose adds one, and it
		# halves every tolerance_half_life), plus chance_per_strength for each level above I (Redwater Skooma
		# is level II). One now and then is a small risk; a few in a day are a big one.
		chance = 0.05
		chance_per_tolerance = 0.15
		chance_per_strength = 0.15
		tolerance_half_life = 12000

		# An addict this long without a dose goes into withdrawal (level I), and it gets a level worse every
		# withdrawal_step after that, up to III. A dose ends it at once.
		withdrawal_after = 24000
		withdrawal_step = 24000

		# Clean this long since the last dose cures the addiction, plus recovery_per_relapse for each dose
		# taken while addicted (up to four), once the player has lived through recovery_endured of withdrawal
		# while playing.
		recovery_after = 72000
		recovery_per_relapse = 12000
		recovery_endured = 24000

		# Milk clears the symptoms; while the addiction lasts they come back this long after.
		symptoms_return = 3600

		# When a high wears off by itself: Slowness and Hunger for this long.
		comedown = 600
		""";

	/** Action bar lines in withdrawal, by level (I to III); each level's first is said when it begins. */
	public static final List<List<String>> CRAVINGS = List.of(
		List.of("You crave Skooma.", "A little Skooma would steady you.", "You can't stop thinking about Skooma."),
		List.of("Your hands won't stop shaking.", "Your head pounds. You need Skooma.", "A cold sweat runs down your back."),
		List.of("Every bone in your body aches for Skooma.", "The shaking won't stop.", "You'd give anything for a bottle of Skooma."));
	/** Lines a level of {@link #CRAVINGS} has. */
	public static final int CRAVING_LINES = 3;
	public static final String HOOKED = "You have become addicted to Skooma.";
	public static final String RELIEF = "The Skooma steadies your hands.";
	public static final String COMEDOWN = "The Skooma wears off.";
	public static final String CURED = "The craving is gone.";

	/** In withdrawal, the chance each second of a craving line on the action bar (about one in two minutes). */
	public static final double CRAVING_CHANCE = 1.0 / 120;
	/** In withdrawal, the chance each second of a spell of nausea, per level (about one in five minutes at I). */
	public static final double NAUSEA_CHANCE = 1.0 / 300;

	/** One player's Skooma habit. {@code tolerance} is what the last dose left (it fades from then). */
	public record Habit(int doses, long lastDose, double tolerance, boolean addicted, int relapses, long endured) {
		public static final long NEVER = Long.MIN_VALUE;
		public static final Habit CLEAN = new Habit(0, NEVER, 0.0, false, 0, 0);

		/** Tolerance at {@code now}: what the last dose left, halving every tolerance_half_life since. */
		public double toleranceAt(long now, Settings s) {
			if (lastDose == NEVER || tolerance <= 0) {
				return 0.0;
			}
			long since = Math.max(0, now - lastDose);
			return tolerance * Math.pow(0.5, (double) since / Math.max(1, s.toleranceHalfLife()));
		}

		/** The chance a dose at {@code now} (amplifier 0 Skooma, 1 Redwater) hooks a player not yet addicted. */
		public double hookChance(long now, int amplifier, Settings s) {
			double c = s.chance() + s.chancePerTolerance() * toleranceAt(now, s) + s.chancePerStrength() * Math.max(0, amplifier);
			return Math.min(1.0, Math.max(0.0, c));
		}

		/** A dose at {@code now}. {@code roll} is a random number from 0 to 1 (below the chance hooks). */
		public Dose dose(long now, int amplifier, double roll, Settings s) {
			if (lastDose != NEVER && now >= lastDose && now - lastDose < SAME_DOSE) {
				return new Dose(this, false, false, false);
			}
			boolean relieved = withdrawal(now, s) > 0;
			boolean hooked = !addicted && roll < hookChance(now, amplifier, s);
			int relapses = addicted ? Math.min(MAX_RELAPSES, this.relapses + 1) : 0;
			Habit after = new Habit(doses + 1, now, toleranceAt(now, s) + 1.0, addicted || hooked, relapses, 0);
			return new Dose(after, true, hooked, relieved);
		}

		/** Withdrawal's level at {@code now}: 0 for none, else I to III. */
		public int withdrawal(long now, Settings s) {
			if (!addicted || lastDose == NEVER) {
				return 0;
			}
			long since = now - lastDose;
			if (since < s.withdrawalAfter()) {
				return 0;
			}
			return (int) Math.min(MAX_WITHDRAWAL, 1 + (since - s.withdrawalAfter()) / Math.max(1, s.withdrawalStep()));
		}

		/** Clean time since the last dose that cures: recovery_after, plus recovery_per_relapse for each relapse. */
		public long recoveryTime(Settings s) {
			return s.recoveryAfter() + relapses * s.recoveryPerRelapse();
		}

		/** Withdrawal to live through before the cure: recovery_endured, or all the withdrawal there is before it if that's less. */
		public long enduranceNeeded(Settings s) {
			return Math.min(s.recoveryEndured(), Math.max(0, recoveryTime(s) - s.withdrawalAfter()));
		}

		/** Whether an addict is cured at {@code now}: clean long enough, the withdrawal lived through. */
		public boolean recovers(long now, Settings s) {
			if (!addicted) {
				return false;
			}
			if (lastDose == NEVER) {
				return true; // no dose to be addicted to
			}
			return now - lastDose >= recoveryTime(s) && endured >= enduranceNeeded(s);
		}

		/** {@code ticks} more of withdrawal lived through. */
		public Habit endure(long ticks) {
			return new Habit(doses, lastDose, tolerance, addicted, relapses, endured + ticks);
		}

		/** Cured: the addiction and its relapses are gone; the doses and the tolerance stay. */
		public Habit cured() {
			return new Habit(doses, lastDose, tolerance, false, 0, 0);
		}

		/** What {@code /skyskooma} says. */
		public String describe(long now, Settings s) {
			if (lastDose == NEVER) {
				return addicted ? "Addicted to Skooma, with no dose taken." : "Never took Skooma.";
			}
			StringBuilder sb = new StringBuilder();
			long since = now - lastDose;
			sb.append(doses).append(doses == 1 ? " dose" : " doses").append(", the last ").append(days(since)).append(" ago; tolerance ")
				.append(String.format(Locale.ROOT, "%.2f", toleranceAt(now, s))).append(". ");
			if (!addicted) {
				sb.append("Not addicted: the next dose hooks ").append(percent(hookChance(now, 0, s))).append(" of the time (")
					.append(percent(hookChance(now, 1, s))).append(" for Redwater).");
				return sb.toString();
			}
			sb.append("Addicted");
			if (relapses > 0) {
				sb.append(", ").append(relapses).append(relapses == 1 ? " relapse" : " relapses");
			}
			int level = withdrawal(now, s);
			sb.append(level > 0 ? "; in withdrawal " + "I".repeat(level) : "; withdrawal in " + days(s.withdrawalAfter() - since));
			sb.append("; cured ").append(since >= recoveryTime(s) ? "now" : "in " + days(recoveryTime(s) - since)).append(" clean, withdrawal lived through ")
				.append(days(endured)).append(" of ").append(days(enduranceNeeded(s))).append('.');
			return sb.toString();
		}
	}

	/**
	 * What a dose did: the habit after it, whether it counted (not part of the last one), whether it hooked a clean
	 * player, whether it ended a withdrawal.
	 */
	public record Dose(Habit habit, boolean counted, boolean hooked, boolean relieved) {
	}

	private SkoomaRules() {
	}

	/** A craving line for withdrawal at {@code level} (1 to 3); {@code pick} chooses which (0 is the level's first). */
	public static String craving(int level, int pick) {
		List<String> lines = CRAVINGS.get(Math.clamp(level, 1, MAX_WITHDRAWAL) - 1);
		return lines.get(Math.floorMod(pick, lines.size()));
	}

	/**
	 * How much easier a crouching player is to miss under Skooma or Stealth at {@code level} (amplifier + 1): mobs
	 * notice them from this share of the distance they otherwise would (on top of crouching's own 0.8). 0.6 at I,
	 * 0.45 at II, never under 0.2.
	 */
	public static double stealthScale(int level) {
		return level <= 0 ? 1.0 : Math.max(0.2, 0.8 * Math.pow(0.75, level));
	}

	/** Ticks as Minecraft days, for {@link Habit#describe}. */
	static String days(long ticks) {
		return String.format(Locale.ROOT, "%.2f days", Math.max(0, ticks) / (double) DAY);
	}

	private static String percent(double chance) {
		return String.format(Locale.ROOT, "%.0f%%", chance * 100);
	}

	/** Parses {@code key = value} lines; bad ones keep the default and go to {@code problems}. */
	public static Settings parse(String text, List<String> problems) {
		Settings d = Settings.DEFAULTS;
		double chance = d.chance(), perTolerance = d.chancePerTolerance(), perStrength = d.chancePerStrength();
		long halfLife = d.toleranceHalfLife(), after = d.withdrawalAfter(), step = d.withdrawalStep(), recovery = d.recoveryAfter();
		long perRelapse = d.recoveryPerRelapse(), endured = d.recoveryEndured(), symptoms = d.symptomsReturn();
		int comedown = d.comedown();
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
					case "chance" -> chance = fraction(value);
					case "chance_per_tolerance" -> perTolerance = fraction(value);
					case "chance_per_strength" -> perStrength = fraction(value);
					case "tolerance_half_life" -> halfLife = ticks(value, 1);
					case "withdrawal_after" -> after = ticks(value, 0);
					case "withdrawal_step" -> step = ticks(value, 1);
					case "recovery_after" -> recovery = ticks(value, 0);
					case "recovery_per_relapse" -> perRelapse = ticks(value, 0);
					case "recovery_endured" -> endured = ticks(value, 0);
					case "symptoms_return" -> symptoms = ticks(value, 0);
					case "comedown" -> comedown = (int) Math.min(ticks(value, 0), 20 * 60 * 60);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad value '" + value + "' for " + key);
			}
		}
		return new Settings(chance, perTolerance, perStrength, halfLife, after, step, recovery, perRelapse, endured, symptoms, comedown);
	}

	private static double fraction(String value) {
		double f = Double.parseDouble(value);
		if (!(f >= 0.0 && f <= 1.0)) {
			throw new NumberFormatException(value);
		}
		return f;
	}

	/** A time in ticks, at least {@code min} and at most a hundred Minecraft days. */
	private static long ticks(String value, long min) {
		long t = Long.parseLong(value);
		if (t < min || t > 100 * DAY) {
			throw new NumberFormatException(value);
		}
		return t;
	}
}
