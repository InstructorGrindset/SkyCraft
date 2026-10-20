package dev.skycraft.villagers;

import java.util.List;
import java.util.Map;

/**
 * Tamriel Wilds' villagers (switch {@code wilds_villagers}): who they are and what Skyrim wares they sell. Pure Java and
 * unit tested; SkyVillagers applies it to the villagers of Tamriel Wilds only, so villagers anywhere else stay vanilla.
 *
 * <ul>
 * <li>A name in Skyrim's way, by profession: "Hrodmar, Nord Blacksmith", "Livia Mallius, Imperial Cartographer". The
 * race and name come from the villager's UUID, so they never change; the title follows its profession (and becomes
 * one when an unemployed villager takes a job, or a child grows up). A name a player gave (a name tag) is left alone.
 * <li>Skyrim trades on top of Minecraft's own: armorers sell SkyCraft armor, weaponsmiths SkyCraft weapons, toolsmiths
 * Skyrim's ingots, leatherworkers leather strips, clerics Skyrim's potions as SkyCraft converts them (items rules). One
 * at the novice level, more as the villager levels up.
 * </ul>
 */
public final class WildsVillagers {
	private WildsVillagers() {
	}

	/** A race of Tamriel: what it's called in a name, how common it is in Tamriel Wilds' villages, and its names. */
	public record Race(String adjective, int weight, List<String> firstNames, List<String> lastNames) {
	}

	/** Mostly Nords, as in Skyrim's villages. Imperials and Bretons have family names, the rest go by one name. */
	public static final List<Race> RACES = List.of(
		new Race("Nord", 50, List.of("Hrodmar", "Asvald", "Eirik", "Gunnhild", "Brunhild", "Thorvald", "Jorund", "Ragnhild", "Sigvard", "Svana", "Yrsa",
			"Bjarni", "Kjeld", "Torgeir", "Vigdis", "Halvar", "Ingrun", "Solveig", "Ulfar", "Hjordis", "Skuli", "Geirlaug", "Orm", "Thrudi"), List.of()),
		new Race("Imperial", 14, List.of("Marcus", "Livia", "Quintus", "Aelia", "Gaius", "Claudia", "Titus", "Octavia", "Lucan", "Severia", "Decimus",
			"Faustina"), List.of("Mallius", "Aurelius", "Varro", "Caro", "Vici", "Maro", "Ancus", "Rufio", "Lento", "Crassius", "Flavus", "Atius")),
		new Race("Breton", 12, List.of("Gaston", "Margot", "Rene", "Celestine", "Jeanne", "Pierrick", "Odile", "Remy", "Ysabel", "Lucien", "Brielle",
			"Arnaud"), List.of("Belmont", "Dufont", "Lirrian", "Montclair", "Vauclair", "Marane", "Sorine", "Adrard", "Corvin", "Gautier")),
		new Race("Redguard", 8, List.of("Hamal", "Rashida", "Tarik", "Zahra", "Jafar", "Nabila", "Kemal", "Samira", "Idris", "Yasmin"), List.of()),
		new Race("Dark Elf", 8, List.of("Dravis", "Ilvesa", "Sedris", "Varona", "Faras", "Mirvyn", "Talsi", "Nelos", "Dreyla", "Galdrus"), List.of()),
		new Race("High Elf", 3, List.of("Ilmiril", "Calindor", "Taurwe", "Merilar", "Aranwe", "Sorondil"), List.of()),
		new Race("Wood Elf", 3, List.of("Fendril", "Gelwen", "Mirwen", "Tharen", "Elidor", "Nimbrin"), List.of()),
		new Race("Khajiit", 1, List.of("Ja'zhari", "Do'raz", "Ma'jhira", "Ra'sava", "J'kara", "Sa'dri"), List.of()),
		new Race("Argonian", 1, List.of("Reeds-in-Wind", "Seeks-the-Sun", "Hulee-Ja", "Teeba-Ei", "Walks-in-Ash", "Deeja-Nur"), List.of()));

	/** Skyrim's word for each Minecraft profession (by its id without the namespace). */
	public static final Map<String, String> TITLES = Map.ofEntries(
		Map.entry("armorer", "Armorer"),
		Map.entry("weaponsmith", "Blacksmith"),
		Map.entry("toolsmith", "Smith"),
		Map.entry("butcher", "Butcher"),
		Map.entry("cartographer", "Cartographer"),
		Map.entry("cleric", "Priest"),
		Map.entry("farmer", "Farmer"),
		Map.entry("fisherman", "Fisherman"),
		Map.entry("fletcher", "Fletcher"),
		Map.entry("leatherworker", "Tanner"),
		Map.entry("librarian", "Scholar"),
		Map.entry("mason", "Stonemason"),
		Map.entry("nitwit", "Beggar"),
		Map.entry("shepherd", "Shepherd"),
		Map.entry("none", "Laborer"));
	/** A profession this table doesn't know (a mod's). */
	public static final String OTHER_TITLE = "Villager";
	/** A baby villager. */
	public static final String CHILD_TITLE = "Child";
	/** Clerics serve one of the Divines. */
	public static final List<String> DIVINES = List.of("Arkay", "Mara", "Kynareth", "Dibella", "Stendarr", "Akatosh", "Julianos", "Zenithar");

	/** The race of the villager with this seed (from its UUID). */
	public static Race race(long seed) {
		int total = 0;
		for (Race r : RACES) {
			total += r.weight();
		}
		int roll = (int) Long.remainderUnsigned(mix(seed), total);
		for (Race r : RACES) {
			roll -= r.weight();
			if (roll < 0) {
				return r;
			}
		}
		return RACES.getFirst();
	}

	/** The villager's own name (no title): a first name, and a family name for the races that use one. */
	public static String personalName(long seed) {
		Race r = race(seed);
		long h = mix(seed ^ 0x5EED_0F_7A3E1L);
		String first = r.firstNames().get((int) Long.remainderUnsigned(h, r.firstNames().size()));
		if (r.lastNames().isEmpty()) {
			return first;
		}
		return first + " " + r.lastNames().get((int) Long.remainderUnsigned(h >>> 32, r.lastNames().size()));
	}

	/** Skyrim's title for a profession id ("armorer", "minecraft:armorer"), the cleric's with their Divine. */
	public static String title(long seed, String profession, boolean child) {
		if (child) {
			return CHILD_TITLE;
		}
		String id = profession.substring(profession.indexOf(':') + 1);
		String title = TITLES.getOrDefault(id, OTHER_TITLE);
		if (id.equals("cleric")) {
			title += " of " + DIVINES.get((int) Long.remainderUnsigned(mix(seed ^ 0xD1F1E5L), DIVINES.size()));
		}
		return title;
	}

	/** The name shown over the villager: "Hrodmar, Nord Blacksmith". */
	public static String displayName(long seed, String profession, boolean child) {
		return personalName(seed) + ", " + race(seed).adjective() + " " + title(seed, profession, child);
	}

	/** Whether {@code name} is one this class gave the villager with this seed (for any profession), not a player's. */
	public static boolean isOurs(String name, long seed) {
		String prefix = personalName(seed) + ", " + race(seed).adjective() + " ";
		if (!name.startsWith(prefix)) {
			return false;
		}
		String title = name.substring(prefix.length());
		if (title.equals(CHILD_TITLE) || title.equals(OTHER_TITLE) || TITLES.containsValue(title)) {
			return true;
		}
		return title.equals(title(seed, "cleric", false));
	}

	/**
	 * A Skyrim trade: from {@code level} (1 novice to 5 master) a villager of {@code profession} sells {@code count} of
	 * {@code item} for {@code emeralds}, {@code maxUses} times before restocking, for {@code xp} villager experience.
	 * {@code item} is an item id, or {@code potion:<Skyrim potion name>} for what SkyCraft converts that Skyrim potion into.
	 */
	public record Trade(String profession, int level, String item, int count, int emeralds, int maxUses, int xp) {
		public boolean potion() {
			return this.item.startsWith(POTION);
		}

		/** The Skyrim potion's name, for a {@link #potion} trade. */
		public String skyrimName() {
			return this.item.substring(POTION.length());
		}
	}

	/** Marks a trade's item as a Skyrim potion by name. */
	public static final String POTION = "potion:";

	/** Skyrim wares by profession, cheapest first. Prices sit a little above vanilla's for the like (an iron chestplate is 9). */
	public static final List<Trade> TRADES = List.of(
		new Trade("armorer", 1, "skycraft:steel_helmet", 1, 7, 12, 1),
		new Trade("armorer", 3, "skycraft:orcish_cuirass", 1, 18, 12, 10),
		new Trade("armorer", 5, "skycraft:elven_cuirass", 1, 28, 3, 30),
		new Trade("weaponsmith", 1, "skycraft:steel_sword", 1, 7, 12, 1),
		new Trade("weaponsmith", 3, "skycraft:orcish_war_axe", 1, 15, 12, 10),
		new Trade("weaponsmith", 5, "skycraft:dwarven_sword", 1, 24, 3, 30),
		new Trade("toolsmith", 1, "skycraft:steel_ingot", 3, 4, 12, 1),
		new Trade("toolsmith", 3, "skycraft:orichalcum_ingot", 2, 6, 12, 10),
		new Trade("toolsmith", 5, "skycraft:refined_malachite", 2, 12, 12, 30),
		new Trade("leatherworker", 1, "skycraft:leather_strips", 4, 2, 16, 1),
		new Trade("cleric", 1, POTION + "Potion of Minor Healing", 1, 3, 12, 1),
		new Trade("cleric", 3, POTION + "Potion of Resist Fire", 1, 6, 12, 10),
		new Trade("cleric", 5, POTION + "Potion of Ultimate Healing", 1, 12, 12, 30));

	/** The Skyrim trades a villager of this profession (an id, with or without its namespace) and level has. */
	public static List<Trade> tradesFor(String profession, int level) {
		String id = profession.substring(profession.indexOf(':') + 1);
		return TRADES.stream().filter(t -> t.profession().equals(id) && t.level() <= level).toList();
	}

	/** SplitMix64's finalizer: a seed's bits spread evenly. */
	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
