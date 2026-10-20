package dev.skycraft.skills;

import dev.skycraft.link.Proto;
import java.util.List;
import java.util.Locale;

/**
 * What the host's Skyrim skill levels do in Minecraft, and the numbers in {@code skycraft-skills.txt}. Pure Java, unit
 * tested without Minecraft; SkySkills calls it with the levels Skyrim writes into SkyState.
 *
 * <p>Every bonus grows in a straight line from Skyrim's starting skill, {@link #START} (no bonus), to {@link #TOP} (the
 * full bonus), and grows no further past it. A level of 0 is unknown (a Skyrim plugin from before the skills, or no
 * game loaded yet) and gives nothing.
 *
 * <ul>
 * <li>Sneak: a crouching player is noticed from a shorter distance, {@code sneak_at_100} of it at 100, on top of
 * Skooma's and Stealth's (never under {@link #MIN_STEALTH} in all).
 * <li>Archery: arrows shot from a bow or crossbow hit {@code archery_at_100} times as hard at 100.
 * <li>Smithing: anvil repairs and combining cost a level less for each {@code smithing_per_level} above 15, never
 * under 1.
 * <li>Enchanting: the enchanting table counts a bookshelf more for each {@code enchanting_per_bookshelf} above 15, up
 * to {@link #MAX_BOOKSHELVES} in all.
 * <li>Dragon souls: {@code levels_per_soul} Minecraft XP levels for each soul absorbed ({@link SoulLedger}).
 * <li>Alchemy: potions taken out of a brewing stand last {@code alchemy_per_level} longer for each level above 15.
 * </ul>
 */
public final class SkillRules {
	/** The skills' names, in SkyState's order (ActorValue order from Proto.SKILL_FIRST, One-Handed, to Enchanting). */
	public static final List<String> NAMES = List.of("One-Handed", "Two-Handed", "Archery", "Block", "Smithing", "Heavy Armor", "Light Armor",
		"Pickpocket", "Lockpicking", "Sneak", "Alchemy", "Speech", "Alteration", "Conjuration", "Destruction", "Illusion", "Restoration", "Enchanting");
	/** A new character's skill level: no bonus yet. */
	public static final int START = 15;
	/** Skyrim's skill cap: the full bonus. Levels above it (fortified, or set in the console) count as this. */
	public static final int TOP = 100;
	/** Most bookshelves an enchanting table counts (vanilla's limit too). */
	public static final int MAX_BOOKSHELVES = 15;
	/**
	 * The least share of the distance a crouching player can be noticed from, whatever makes them stealthy: Skooma's floor
	 * (SkoomaRules.stealthScale).
	 */
	public static final double MIN_STEALTH = 0.2;
	/**
	 * The name SkyAlchemy's brewed Stealth potions go by, as a vanilla potion's long and strong kinds share one: their item
	 * names are {@code item.minecraft.potion.effect.stealth} and the like.
	 */
	public static final String STEALTH_POTION = "stealth";
	/** The items a potion's name is looked up for: {@code item.minecraft.<item>.effect.<potion>}. */
	public static final List<String> POTION_ITEMS = List.of("potion", "splash_potion", "lingering_potion", "tipped_arrow");

	/** Settings from {@code config/skycraft-skills.txt}. */
	public record Settings(double sneakAtTop, double archeryAtTop, int smithingPerLevel, int enchantingPerBookshelf, int levelsPerSoul,
		double alchemyPerLevel) {
		public static final Settings DEFAULTS = new Settings(0.6, 1.5, 25, 6, 5, 0.005);
	}

	public static final String DEFAULT_TEXT = """
		# SkyCraft skills: your Skyrim skill levels make Minecraft better (the host's own Skyrim; guests' skills
		# aren't known to the host's world yet). Each has its switch in skycraft-features.txt: sneak_skill,
		# archery_skill, smithing_skill, enchanting_skill, dragon_souls, alchemy_brewing. Restart Minecraft after
		# editing.
		# Every bonus grows in a line from a new character's skill, 15 (none), to 100 (the full bonus), and no
		# further. /skyskills shows your levels and what they give.

		# Sneak: a crouching player is noticed from this share of the distance at Sneak 100 (1 is no change), on
		# top of Skooma and Stealth, never under 0.2 in all. At least 0.2, at most 1.
		sneak_at_100 = 0.6

		# Archery: arrows from a bow or crossbow hit this many times as hard at Archery 100. 1 to 10.
		archery_at_100 = 1.5

		# Smithing: anvil repairs and combining cost one XP level less for every this many Smithing levels above
		# 15, never under 1 level (a rename alone costs what it did). 1 to 100.
		smithing_per_level = 25

		# Enchanting: the enchanting table counts one bookshelf more for every this many Enchanting levels above
		# 15, at most 15 bookshelves in all. 1 to 100.
		enchanting_per_bookshelf = 6

		# Dragon souls: Minecraft XP levels for each dragon soul you absorb in Skyrim. 0 to 100.
		levels_per_soul = 5

		# Alchemy: potions you take out of a brewing stand last this much longer for every Alchemy level above
		# 15 (0.005 is half a percent: 42.5% longer at 100). 0 to 0.1.
		alchemy_per_level = 0.005
		""";

	private SkillRules() {
	}

	/** The name of {@code skill} (a Skyrim ActorValue, One-Handed 6 to Enchanting 23), or "skill N". */
	public static String name(int skill) {
		int i = skill - Proto.SKILL_FIRST;
		return i >= 0 && i < NAMES.size() ? NAMES.get(i) : "skill " + skill;
	}

	/** Levels of {@code level} above {@link #START}, counting no further than {@link #TOP}; 0 for an unknown level (0). */
	public static int above(int level) {
		return level <= 0 ? 0 : Math.max(0, Math.min(level, TOP) - START);
	}

	/** How far {@code level} is on the way from {@link #START} to {@link #TOP}: 0 to 1. */
	public static double progress(int level) {
		return above(level) / (double) (TOP - START);
	}

	/** The share of the distance a crouching player with this Sneak is noticed from, before Skooma: 1 at 15, sneak_at_100 at 100. */
	public static double sneakScale(int sneak, Settings s) {
		return 1.0 - (1.0 - s.sneakAtTop()) * progress(sneak);
	}

	/**
	 * Skooma's or Stealth's scale (1 without either) and Sneak's together: the share of the distance mobs notice a crouching
	 * player from, never under {@link #MIN_STEALTH}. 1 when neither does anything.
	 */
	public static double stealth(double skoomaScale, double sneakScale) {
		double scale = skoomaScale * sneakScale;
		return scale >= 1.0 ? 1.0 : Math.max(MIN_STEALTH, scale);
	}

	/** How many times as hard an arrow shot with this Archery hits: 1 at 15, archery_at_100 at 100. */
	public static double archeryMultiplier(int archery, Settings s) {
		return 1.0 + (s.archeryAtTop() - 1.0) * progress(archery);
	}

	/** An anvil's XP level {@code cost} with this Smithing: a level less for each smithing_per_level above 15, never under 1. */
	public static int anvilCost(int cost, int smithing, Settings s) {
		if (cost <= 1) {
			return cost;
		}
		return Math.max(1, cost - above(smithing) / s.smithingPerLevel());
	}

	/**
	 * The bookshelves an enchanting table with {@code real} of them counts with this Enchanting: one more for each
	 * enchanting_per_bookshelf above 15, at most {@link #MAX_BOOKSHELVES} (never fewer than it has).
	 */
	public static int bookshelves(int real, int enchanting, Settings s) {
		if (real >= MAX_BOOKSHELVES) {
			return real;
		}
		return Math.min(MAX_BOOKSHELVES, Math.max(0, real) + above(enchanting) / s.enchantingPerBookshelf());
	}

	/** How many times as long a potion lasts when taken out of a brewing stand with this Alchemy: 1 at 15, 1.425 at 100 by default. */
	public static double alchemyScale(int alchemy, Settings s) {
		return 1.0 + s.alchemyPerLevel() * above(alchemy);
	}

	/** Minecraft XP levels for {@code souls} dragon souls absorbed. */
	public static int levelsFor(int souls, Settings s) {
		return Math.max(0, souls) * s.levelsPerSoul();
	}

	/**
	 * The Dragon Souls count a player last saw (kept with them, per world): in which Skyrim game ({@code load}, SkyState's
	 * skillsLoad; 0 for none yet) and how many ({@code souls}).
	 */
	public record SoulLedger(int load, int souls) {
		public static final SoulLedger NONE = new SoulLedger(0, 0);

		/**
		 * Skyrim now shows {@code souls} in game {@code load}: the ledger after, and how many souls were absorbed since. Only a
		 * rise within the same game counts: another game (a save loaded, a new character, the first look, a Minecraft that
		 * reconnects to a Skyrim it hasn't seen) only records the count, and souls spent on shouts are only taken off. Load 0
		 * (a plugin from before the skills) changes nothing.
		 */
		public Absorbed see(int load, int souls) {
			if (load == 0) {
				return new Absorbed(this, 0);
			}
			int now = Math.max(0, souls);
			int absorbed = load == this.load && now > this.souls ? now - this.souls : 0;
			return new Absorbed(new SoulLedger(load, now), absorbed);
		}
	}

	/** What {@link SoulLedger#see} found: the ledger to keep and the souls absorbed since the last look. */
	public record Absorbed(SoulLedger ledger, int souls) {
	}

	/** One line on what {@code level} of {@code skill} gives, for /skyskills. */
	public static String describe(int skill, int level, Settings s) {
		String name = name(skill) + " " + (level <= 0 ? "unknown" : String.valueOf(level));
		return switch (skill) {
			case Proto.SKILL_SNEAK -> name + ": noticed crouching from " + format(sneakScale(level, s)) + " of the distance";
			case Proto.SKILL_ARCHERY -> name + ": arrows hit x" + format(archeryMultiplier(level, s));
			case Proto.SKILL_SMITHING -> name + ": anvil repairs cost " + above(level) / s.smithingPerLevel() + " levels less (at least 1)";
			case Proto.SKILL_ENCHANTING -> name + ": " + Math.min(MAX_BOOKSHELVES, above(level) / s.enchantingPerBookshelf()) + " more bookshelves";
			case Proto.SKILL_ALCHEMY -> name + ": brewed potions last x" + format(alchemyScale(level, s));
			default -> name;
		};
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** Parses {@code key = value} lines; bad ones keep the default and go to {@code problems}. */
	public static Settings parse(String text, List<String> problems) {
		Settings d = Settings.DEFAULTS;
		double sneak = d.sneakAtTop(), archery = d.archeryAtTop(), alchemy = d.alchemyPerLevel();
		int smithing = d.smithingPerLevel(), enchanting = d.enchantingPerBookshelf(), souls = d.levelsPerSoul();
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
					case "sneak_at_100" -> sneak = number(value, MIN_STEALTH, 1.0);
					case "archery_at_100" -> archery = number(value, 1.0, 10.0);
					case "smithing_per_level" -> smithing = whole(value, 1, 100);
					case "enchanting_per_bookshelf" -> enchanting = whole(value, 1, 100);
					case "levels_per_soul" -> souls = whole(value, 0, 100);
					case "alchemy_per_level" -> alchemy = number(value, 0.0, 0.1);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad value '" + value + "' for " + key);
			}
		}
		return new Settings(sneak, archery, smithing, enchanting, souls, alchemy);
	}

	private static double number(String value, double min, double max) {
		double v = Double.parseDouble(value);
		if (!(v >= min && v <= max)) {
			throw new NumberFormatException(value);
		}
		return v;
	}

	private static int whole(String value, int min, int max) {
		int v = Integer.parseInt(value);
		if (v < min || v > max) {
			throw new NumberFormatException(value);
		}
		return v;
	}
}
