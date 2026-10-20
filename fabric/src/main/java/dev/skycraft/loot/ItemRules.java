package dev.skycraft.loot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Which Skyrim items become Minecraft items when the player gets them, and what they become.
 *
 * <p>Plain text, one rule per line, first match wins, anything unmatched stays in Skyrim:
 * <pre>
 * tag food name potato = minecraft:potato 1
 * tag weapon material iron from looted = minecraft:iron_ingot 1
 * tag gold = keep
 * tag potion name healing = minecraft:potion[healing] 1
 * tag potion name vigor = minecraft:potion{speed:0-1:120-300 + saturation:0-4:0 #3a9a3a} 1
 * tag jewelry name diamond = minecraft:gold_nugget 3 + minecraft:diamond 1
 * tag clothing kind boots from looted from bought = minecraft:leather_boots{#5b4632} 1
 * </pre>
 * Every condition on the left must hold. {@code name} is a whole word of the Skyrim name (ignoring
 * case; {@code half-eaten} needs both words), {@code tag} one of {@link #TAGS}, {@code material}
 * one of {@link #MATERIALS}, {@code from} one of {@link #SOURCES} (several {@code from} mean any of
 * them), {@code kind} one of {@link #KINDS}. The number on the right is
 * Minecraft items per Skyrim item; {@code +} joins several items. A bracket after one of the
 * {@link #POTION_ITEMS} names the vanilla potion it holds, one of {@link #POTIONS}; braces list
 * effects of its own instead ({@link Effect}), with an optional {@code #rrggbb} color and
 * {@code "name"}. Effect ranges scale with the Skyrim name's tier ({@link #TIERS}). Braces after any other item list
 * enchantments ({@link #ENCHANTMENTS}, {@code id:level}) and an optional name instead: Skyrim gear that keeps its enchantment;
 * on one of the {@link #DYEABLE} items they may also hold a {@code #rrggbb} dye: Skyrim clothing.
 * Skyrim sends names cut to 27 bytes; {@link #fullName} gives back the known long ones.
 * Pure Java on purpose, so it's unit tested without Minecraft.
 */
public final class ItemRules {
	/** Tag names, by bit (see ItemTags in the protocol header). */
	public static final List<String> TAGS = List.of("food", "foodraw", "hide", "oreingot", "potion", "poison", "ingredient", "weapon", "armor", "clothing",
		"jewelry", "gold", "key", "arrow", "gem", "clutter", "book", "soulgem", "filled");
	/** Material names, by value (see ItemMaterial in the protocol header). */
	public static final List<String> MATERIALS = List.of("none", "wood", "iron", "steel", "imperial", "orcish", "dwarven", "elven", "glass", "ebony", "daedric",
		"dragon", "silver", "hide", "draugr", "falmer");
	/**
	 * Kind names, by value (see ItemKind in the protocol header, Proto.KIND_*): the piece Skyrim says a weapon, armor
	 * piece or piece of clothing is, in bits 24-27 of an item's tags. An older plugin sends none (0).
	 */
	public static final List<String> KINDS = List.of("none", "sword", "dagger", "war_axe", "mace", "greatsword", "battleaxe", "bow", "staff",
		"crossbow", "hand_to_hand", "helmet", "cuirass", "gauntlets", "boots", "shield");
	/** Where an item's kind sits in its tags (Proto.TAG_KIND_SHIFT). */
	static final int KIND_SHIFT = 24;
	/**
	 * Source names, by value (see ItemSource); "looted" means corpse or container, "bought" is Skyrim's shop. Gear
	 * a dying actor wore (5, Proto.SRC_WORN) never reaches the rules: SkyBridge sends it to GearDrops.
	 */
	public static final List<String> SOURCES = List.of("world", "corpse", "container", "actor", "bought");
	/** Vanilla potion ids, as Minecraft 26.3 registers them (net.minecraft.world.item.alchemy.PotionIds). */
	public static final List<String> POTIONS = List.of("water", "mundane", "thick", "awkward", "night_vision", "long_night_vision", "invisibility",
		"long_invisibility", "leaping", "long_leaping", "strong_leaping", "fire_resistance", "long_fire_resistance", "swiftness", "long_swiftness",
		"strong_swiftness", "slowness", "long_slowness", "strong_slowness", "turtle_master", "long_turtle_master", "strong_turtle_master",
		"water_breathing", "long_water_breathing", "healing", "strong_healing", "harming", "strong_harming", "poison", "long_poison", "strong_poison",
		"regeneration", "long_regeneration", "strong_regeneration", "strength", "long_strength", "strong_strength", "weakness", "long_weakness", "luck",
		"slow_falling", "long_slow_falling", "wind_charged", "weaving", "oozing", "infested");
	/** Effect ids, as Minecraft 26.3 registers them (net.minecraft.world.effect.MobEffects), for potions with effects of their own. */
	public static final List<String> EFFECTS = List.of("speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
		"jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility", "blindness", "night_vision", "hunger",
		"weakness", "poison", "wither", "health_boost", "absorption", "saturation", "glowing", "levitation", "luck", "unluck", "slow_falling",
		"conduit_power", "dolphins_grace", "bad_omen", "hero_of_the_village", "darkness", "trial_omen", "raid_omen", "wind_charged", "weaving", "oozing",
		"infested", "breath_of_the_nautilus");
	/**
	 * SkyCraft's own effects (alchemy.SkyEffects), named with their namespace in a rule: {@code skycraft:skooma:0:180} is
	 * Skooma's high, {@code skycraft:stealth:0-1:60-240} Fortify Sneak.
	 */
	public static final List<String> SKYCRAFT_EFFECTS = List.of("skycraft:skooma", "skycraft:stealth", "skycraft:skooma_withdrawal");
	/** Items that hold a potion (the potion_contents component), so a rule can write {@code minecraft:potion[healing]}. */
	public static final List<String> POTION_ITEMS = List.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion",
		"minecraft:tipped_arrow");
	/**
	 * Enchantment ids, as Minecraft 26.3 registers them (net.minecraft.world.item.enchantment.Enchantments), for gear that
	 * keeps a Skyrim enchantment: {@code skycraft:ebony_sword{fire_aspect:2}}. On an enchanted book they're its stored ones.
	 */
	public static final List<String> ENCHANTMENTS = List.of("aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection", "breach",
		"channeling", "density", "depth_strider", "efficiency", "feather_falling", "fire_aspect", "fire_protection", "flame", "fortune", "frost_walker",
		"impaling", "infinity", "knockback", "looting", "loyalty", "luck_of_the_sea", "lunge", "lure", "mending", "multishot", "piercing", "power",
		"projectile_protection", "protection", "punch", "quick_charge", "respiration", "riptide", "sharpness", "silk_touch", "smite", "soul_speed",
		"sweeping_edge", "swift_sneak", "thorns", "unbreaking", "vanishing_curse", "wind_burst");
	/** Items a rule may dye ({@code minecraft:leather_chestplate{#3b5dc9}}): Minecraft's dyeable leather. */
	public static final List<String> DYEABLE = List.of("minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings",
		"minecraft:leather_boots", "minecraft:leather_horse_armor", "minecraft:wolf_armor");
	/** The highest enchantment level a rule may give (vanilla's own top is V). */
	public static final int MAX_ENCHANT_LEVEL = 10;
	/**
	 * How strong a Skyrim potion or poison is, from the words of its name: Minor and Weak are tier 0, a name
	 * without a tier word is tier 1, and Ultimate, Grand and Prime are tier 5. The strongest word counts.
	 */
	public static final Map<String, Integer> TIERS = Map.ofEntries(Map.entry("minor", 0), Map.entry("weak", 0), Map.entry("brief", 0),
		Map.entry("plentiful", 2), Map.entry("draught", 2), Map.entry("potent", 2), Map.entry("extended", 2), Map.entry("enduring", 2),
		Map.entry("lasting", 2), Map.entry("vigorous", 3), Map.entry("solution", 3), Map.entry("philter", 3), Map.entry("virulent", 3),
		Map.entry("malign", 3), Map.entry("persisting", 3), Map.entry("prolonged", 3), Map.entry("extreme", 4), Map.entry("elixir", 4),
		Map.entry("deadly", 4), Map.entry("unceasing", 4), Map.entry("ultimate", 5), Map.entry("grand", 5), Map.entry("prime", 5));
	/** The tier of a name without a tier word, and the highest tier. */
	public static final int DEFAULT_TIER = 1, MAX_TIER = 5;
	/** Bytes of a name an ItemEvent carries (ItemEvent.name is char[28]). */
	public static final int NAME_BYTES = 27;
	/**
	 * Real Skyrim names longer than {@link #NAME_BYTES}: the vanilla potions, the potions and poisons Skyrim's
	 * alchemy names "Potion of " or "Poison of " plus the effect, and a Dwemer part. Three crafted lingering
	 * poisons share their first 27 bytes, so they're left cut.
	 */
	public static final List<String> LONG_NAMES = List.of("Potion of Brief Invisibility", "Potion of Extended Invisibility",
		"Potion of Enduring Invisibility", "Potion of Prolonged Invisibility", "Potion of Plentiful Well-being", "Potion of Vigorous Well-being",
		"Potion of Extreme Well-being", "Potion of Ultimate Well-being", "Solution of Enhanced Stamina", "Potion of Fortify One-handed",
		"Potion of Fortify Two-handed", "Potion of Fortify Heavy Armor", "Potion of Fortify Light Armor", "Potion of Fortify Lockpicking",
		"Potion of Fortify Pickpocket", "Potion of Fortify Persuasion", "Potion of Fortify Enchanting", "Potion of Fortify Alteration",
		"Potion of Fortify Conjuration", "Potion of Fortify Destruction", "Potion of Fortify Restoration", "Potion of Fortify Carry Weight",
		"Potion of Regenerate Magicka", "Potion of Regenerate Stamina", "Poison of Damage Magicka Regen", "Poison of Damage Stamina Regen",
		"Poison of Weakness to Poison", "Poison of Lingering Damage Health", "Poison of Lingering Damage Magicka",
		"Poison of Lingering Damage Stamina", "Large Decorative Dwemer Strut");
	/** Most an effect's amplifier and seconds can be. */
	static final int MAX_AMPLIFIER = 255, MAX_SECONDS = 3600;

	public record Item(int tags, int material, int source, String name) {
	}

	/**
	 * One effect of a potion with effects of its own: a 26.3 effect id, its amplifier (0 is level I) and how many
	 * seconds it lasts (0 for instant effects like instant_health). {@code amplifier} and {@code seconds} are the
	 * values at tier 0 and the {@code Top} ones at tier {@link #MAX_TIER}; tiers between are in proportion, rounded.
	 * A rule writes {@code speed:0-1:60-180}, or {@code speed:1:120} for the same at every tier.
	 */
	public record Effect(String id, int amplifier, int seconds, int amplifierTop, int secondsTop) {
		public Effect(String id, int amplifier, int seconds) {
			this(id, amplifier, seconds, amplifier, seconds);
		}

		/** This effect with a name of tier {@code tier}. */
		public Effect at(int tier) {
			int a = scale(this.amplifier, this.amplifierTop, tier), s = scale(this.seconds, this.secondsTop, tier);
			return new Effect(this.id, a, s);
		}

		/** As a rule writes it. */
		public String text() {
			return this.id + ":" + range(this.amplifier, this.amplifierTop) + ":" + range(this.seconds, this.secondsTop);
		}

		private static String range(int low, int high) {
			return low == high ? String.valueOf(low) : low + "-" + high;
		}
	}

	/** An enchantment an item comes with: a 26.3 enchantment id ({@link #ENCHANTMENTS}) and its level. */
	public record Enchant(String id, int level) {
	}

	/**
	 * {@code item} null means keep it in Skyrim; {@code potion} is the vanilla potion the item holds, or null;
	 * {@code effects} its own effects; {@code color} 0xRRGGBB or -1 (a potion's color, or leather's dye); {@code name} the Minecraft name, or null for
	 * Minecraft's own; {@code also} more items given with it; {@code enchantments} what a non-potion item comes
	 * enchanted with.
	 */
	public record Result(String item, int perItem, String potion, List<Effect> effects, int color, String name, List<Result> also,
		List<Enchant> enchantments) {
		public Result(String item, int perItem) {
			this(item, perItem, null);
		}

		public Result(String item, int perItem, String potion) {
			this(item, perItem, potion, List.of(), -1, null, List.of());
		}

		public Result(String item, int perItem, String potion, List<Effect> effects, int color, String name, List<Result> also) {
			this(item, perItem, potion, effects, color, name, also, List.of());
		}

		public boolean keep() {
			return this.item == null;
		}

		/** A potion with effects of its own (rather than a vanilla potion). */
		public boolean custom() {
			return !this.effects.isEmpty();
		}

		/** The item as a rule writes it, {@code minecraft:potion[healing]} or {@code minecraft:potion{speed:1:120}}, for logs. */
		public String label() {
			if (this.item == null) {
				return "keep";
			}
			StringBuilder sb = new StringBuilder(this.item);
			if (this.potion != null) {
				sb.append('[').append(this.potion).append(']');
			}
			if (this.custom()) {
				sb.append('{').append(this.effects.stream().map(Effect::text).collect(Collectors.joining("+"))).append('}');
			}
			if (!this.enchantments.isEmpty()) {
				sb.append('{').append(this.enchantments.stream().map(e -> e.id() + ":" + e.level()).collect(Collectors.joining("+"))).append('}');
			}
			return sb.toString();
		}

		/** Every item with its count per Skyrim item: {@code minecraft:gold_nugget 3 + minecraft:diamond 1}. */
		public String describe() {
			if (this.item == null) {
				return "keep";
			}
			StringBuilder sb = new StringBuilder(this.label()).append(' ').append(this.perItem);
			for (Result r : this.also) {
				sb.append(" + ").append(r.describe());
			}
			return sb.toString();
		}

		/** This result for a Skyrim item named {@code skyrimName} of tier {@code tier}: effect values picked, custom potions named. */
		Result at(int tier, String skyrimName) {
			List<Effect> fx = this.effects.stream().map(e -> e.at(tier)).toList();
			String n = this.name != null ? this.name : this.custom() && skyrimName != null ? skyrimName.strip() : null;
			List<Result> more = this.also.stream().map(r -> r.at(tier, skyrimName)).toList();
			return new Result(this.item, this.perItem, this.potion, fx, this.color, n, more, this.enchantments);
		}
	}

	private static final Result KEEP = new Result(null, 0);
	/** What separates the words of a name: anything but letters, digits and apostrophes. */
	private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9']+");
	private static final Pattern RANGE = Pattern.compile("(\\d{1,5})(?:-(\\d{1,5}))?");
	private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	/** Cut name to whole name, for the {@link #LONG_NAMES} no other long name shares a cut with. */
	private static final Map<String, String> RESTORE = restoreTable();

	/** {@code kind} is one of {@link #KINDS} by value, or -1 for any. */
	record Rule(int tagMask, int material, int sourceMask, int kind, List<String> nameWords, Result result) {
		boolean matches(Item it, List<String> words) {
			if ((it.tags() & this.tagMask) != this.tagMask) {
				return false;
			}
			if (this.kind >= 0 && (it.tags() >>> KIND_SHIFT & 0xF) != this.kind) {
				return false;
			}
			if (this.material >= 0 && it.material() != this.material) {
				return false;
			}
			if (this.sourceMask != 0 && (this.sourceMask & (1 << it.source())) == 0) {
				return false;
			}
			return words.containsAll(this.nameWords);
		}
	}

	/**
	 * Every default this file shipped with before, by hash (every dev build since b91819f): a config file still
	 * holding one of them untouched gets {@link #DEFAULT_TEXT} instead (config.Config.read). Add the hash of the
	 * current default here ({@code Config.sha256(DEFAULT_TEXT)}) whenever you change it.
	 */
	public static final List<String> OLD_DEFAULTS = List.of(
		"sha256:23db7152d9a9466a0130577639c6f45eaccbd1de8f2cb4b97004c1e1b44bcf75",
		"sha256:72429211b2cc3fdeae1485a4fd7a0ee2f3e71a60c38a25a5bafffe426106f6d3",
		"sha256:a7258817f22d2f5a226d286d1a54e9e6849096bf9533346d9d4f489224b61fd9",
		"sha256:7a18a0ff6b8752a2f0eca4a2db157ce8c86c6237ae31eb8c97d4233db3386f9b",
		"sha256:7d17ee41c5c4cb8b6a480fcc5645b70eb3a4a2a296d5f466101dce40e882eeb6",
		"sha256:4aec26e2a45bc3e7c3bfbce89cdb6c91d938fa7478e17c2458791c7b1cebdbe9",
		"sha256:58ad79f0b836d9dbaa6a73e01f80728b09ef5cedb9699de289d4c39d2eef6625",
		"sha256:04dca244c63b2906470fb972eebc8aef79af2f286530dd3877ef81ec1e0cdfd6",
		"sha256:b5df8342f71997d853834f4d9d20e54bdc3d6f14338f015f71c839ca15cb889b",
		"sha256:27ea3184c36639d40d80657d84f79dc84d90f2ff551a3ffe87dc1b076f4fb985",
		"sha256:4670a25c478cf36f1e51fa323a4a741fb5ff985ba8218fe7d74dc8465f14e5e1",
		"sha256:7d6f9dc73478bc63d73771b1117c841393a17266fbabee4a4725bc2a7d3c6331",
		"sha256:932298a3eece9216b1957ab4c3e9f987348ea7c75bbc027d3b6465e4bc27a754");

	/** Where the default rules live in the jar, beside this class: {@link #DEFAULT_TEXT}. */
	static final String DEFAULT_RESOURCE = "skycraft-items.txt";
	/**
	 * The default rules, written to {@code config/skycraft-items.txt} when it's missing. They're a text file in the jar
	 * ({@link #DEFAULT_RESOURCE}) rather than a string here: thousands of rules are more than a Java string constant holds.
	 */
	public static final String DEFAULT_TEXT = defaultText();

	private final List<Rule> rules;

	private ItemRules(List<Rule> rules) {
		this.rules = rules;
	}

	public int size() {
		return this.rules.size();
	}

	/** The rules in file order, for tests that walk them. */
	List<Rule> rules() {
		return this.rules;
	}

	/** {@link #DEFAULT_RESOURCE} as text; a jar without it is broken, so that fails loudly. */
	private static String defaultText() {
		try (InputStream in = ItemRules.class.getResourceAsStream(DEFAULT_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("the SkyCraft jar has no " + DEFAULT_RESOURCE + " beside " + ItemRules.class.getName());
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("couldn't read the default item rules from the SkyCraft jar", e);
		}
	}

	/** The words of a name, lower case: what {@code name} conditions (and LootRules) match against. */
	public static List<String> words(String name) {
		List<String> out = new ArrayList<>();
		if (name != null) {
			for (String w : NON_WORD.split(name.toLowerCase(Locale.ROOT))) {
				if (!w.isEmpty()) {
					out.add(w);
				}
			}
		}
		return out;
	}

	/** {@code name} as an ItemEvent carries it: its first {@link #NAME_BYTES} bytes of UTF-8. */
	public static String cut(String name) {
		byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
		return bytes.length <= NAME_BYTES ? name : new String(bytes, 0, NAME_BYTES, StandardCharsets.UTF_8);
	}

	/** The whole name of a cut one of the {@link #LONG_NAMES}; any other name as it is. */
	public static String fullName(String name) {
		return name == null ? null : RESTORE.getOrDefault(name, name);
	}

	private static Map<String, String> restoreTable() {
		Map<String, String> out = new HashMap<>();
		Set<String> shared = new HashSet<>();
		for (String name : LONG_NAMES) {
			String cut = cut(name);
			if (!cut.equals(name) && out.putIfAbsent(cut, name) != null && !out.get(cut).equals(name)) {
				shared.add(cut);
			}
		}
		shared.forEach(out::remove);
		return Map.copyOf(out);
	}

	/** The strongest {@link #TIERS} word in {@code words}, not counting the rule's own name words; {@link #DEFAULT_TIER} without one. */
	static int tier(List<String> words, List<String> ruleWords) {
		int best = -1;
		for (String w : words) {
			Integer t = TIERS.get(w);
			if (t != null && !ruleWords.contains(w)) {
				best = Math.max(best, t);
			}
		}
		return best < 0 ? DEFAULT_TIER : best;
	}

	/** {@code low} at tier 0 to {@code high} at tier {@link #MAX_TIER}, rounded. */
	static int scale(int low, int high, int tier) {
		return low + Math.round((high - low) * Math.max(0, Math.min(MAX_TIER, tier)) / (float) MAX_TIER);
	}

	/**
	 * The first matching rule's result, with effect values picked for the name's tier and custom potions named
	 * after the Skyrim item; keep (stays in Skyrim) when nothing matches.
	 */
	public Result match(Item item) {
		String full = fullName(item.name());
		List<String> words = words(full);
		if (full != null && !full.equals(item.name())) {
			// The cut words match too, for rules written against the cut name.
			for (String w : words(item.name())) {
				if (!words.contains(w)) {
					words.add(w);
				}
			}
		}
		for (Rule rule : this.rules) {
			if (rule.matches(item, words)) {
				Result r = rule.result();
				return r.keep() ? r : r.at(tier(words, rule.nameWords()), full);
			}
		}
		return KEEP;
	}

	public static ItemRules parse(String text, List<String> problems) {
		List<Rule> out = new ArrayList<>();
		// Unknown potion and effect ids, with the lines that use them: reported once each, after the rules.
		Map<String, List<Integer>> unknownPotions = new LinkedHashMap<>();
		Map<String, List<Integer>> unknownEffects = new LinkedHashMap<>();
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0) {
				problems.add("line " + n + ": no '='");
				continue;
			}
			String[] left = line.substring(0, eq).strip().toLowerCase(Locale.ROOT).split("\\s+");
			String rhs = line.substring(eq + 1).strip();
			if (left.length % 2 != 0) {
				problems.add("line " + n + ": conditions come in pairs, like 'tag food'");
				continue;
			}
			int tagMask = 0, material = -1, sourceMask = 0, kind = -1;
			List<String> names = new ArrayList<>();
			String bad = null;
			for (int i = 0; i < left.length && bad == null; i += 2) {
				String key = left[i], value = left[i + 1];
				switch (key) {
					case "tag" -> {
						int bit = TAGS.indexOf(value);
						if (bit < 0) {
							bad = "unknown tag '" + value + "'";
						} else {
							tagMask |= 1 << bit;
						}
					}
					case "material" -> {
						material = MATERIALS.indexOf(value);
						if (material < 0) {
							bad = "unknown material '" + value + "'";
						}
					}
					case "from" -> {
						if (value.equals("looted")) {
							sourceMask |= (1 << 1) | (1 << 2);
						} else if (SOURCES.contains(value)) {
							sourceMask |= 1 << SOURCES.indexOf(value);
						} else {
							bad = "unknown source '" + value + "'";
						}
					}
					case "kind" -> {
						kind = KINDS.indexOf(value);
						if (kind < 0) {
							bad = "unknown kind '" + value + "'";
						}
					}
					case "name" -> {
						// Split like item names are, so "name half-eaten" needs both "half" and "eaten".
						List<String> parts = words(value);
						if (parts.isEmpty()) {
							bad = "name '" + value + "' has no letters or digits";
						} else {
							names.addAll(parts);
						}
					}
					default -> bad = "unknown condition '" + key + "'";
				}
			}
			if (bad != null) {
				problems.add("line " + n + ": " + bad);
				continue;
			}
			Result result;
			if (rhs.equalsIgnoreCase("keep")) {
				result = KEEP;
			} else {
				List<String> parts = splitOutputs(rhs);
				List<Result> outputs = new ArrayList<>();
				for (String part : parts) {
					Result r = parseOutput(part, n, problems, unknownPotions, unknownEffects);
					if (r == null) {
						break;
					}
					outputs.add(r);
				}
				if (outputs.size() < parts.size()) {
					continue;
				}
				Result first = outputs.get(0);
				result = new Result(first.item(), first.perItem(), first.potion(), first.effects(), first.color(), first.name(),
					List.copyOf(outputs.subList(1, outputs.size())), first.enchantments());
			}
			out.add(new Rule(tagMask, material, sourceMask, kind, List.copyOf(names), result));
		}
		unknownPotions.forEach((id, lines) -> problems.add("line " + lines.get(0) + ": unknown potion '" + id + "', rule skipped" + alsoLines(lines)));
		unknownEffects.forEach((id, lines) -> problems.add("line " + lines.get(0) + ": unknown effect '" + id + "', rule skipped" + alsoLines(lines)));
		return new ItemRules(List.copyOf(out));
	}

	private static String alsoLines(List<Integer> lines) {
		return lines.size() > 1 ? " (also line " + lines.stream().skip(1).map(String::valueOf).collect(Collectors.joining(", ")) + ")" : "";
	}

	/** The right side split at each {@code +} outside brackets, braces and quotes. */
	static List<String> splitOutputs(String rhs) {
		List<String> out = new ArrayList<>();
		int depth = 0, start = 0;
		boolean quoted = false;
		for (int i = 0; i < rhs.length(); i++) {
			char c = rhs.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted && (c == '[' || c == '{')) {
				depth++;
			} else if (!quoted && (c == ']' || c == '}')) {
				depth = Math.max(0, depth - 1);
			} else if (!quoted && depth == 0 && c == '+') {
				out.add(rhs.substring(start, i).strip());
				start = i + 1;
			}
		}
		out.add(rhs.substring(start).strip());
		return out;
	}

	/**
	 * One item of a right side: {@code item[potion]{effects #color "name"} N}. Null when it's bad (the problem is
	 * added) or names an unknown potion or effect (recorded, reported once after the rules).
	 */
	private static Result parseOutput(String text, int n, List<String> problems, Map<String, List<Integer>> unknownPotions,
		Map<String, List<Integer>> unknownEffects) {
		String rhs = text;
		// {effects}: the potion's own effects; read first, since a quoted name may hold any character.
		String body = null;
		int brace = rhs.indexOf('{');
		if (brace >= 0) {
			int close = closingBrace(rhs, brace);
			if (close < 0) {
				problems.add("line " + n + ": no '}' after '{'");
				return null;
			}
			body = rhs.substring(brace + 1, close);
			rhs = (rhs.substring(0, brace) + " " + rhs.substring(close + 1)).strip();
		}
		// minecraft:potion[healing] 2: the bracket names the potion, the rest reads like any other result.
		String potion = null;
		int open = rhs.indexOf('[');
		if (open >= 0) {
			int close = rhs.indexOf(']', open);
			if (close < 0) {
				problems.add("line " + n + ": no ']' after '['");
				return null;
			}
			potion = rhs.substring(open + 1, close).strip().toLowerCase(Locale.ROOT);
			if (potion.startsWith("minecraft:")) {
				potion = potion.substring("minecraft:".length());
			}
			rhs = (rhs.substring(0, open) + " " + rhs.substring(close + 1)).strip();
		}
		String[] right = rhs.split("\\s+");
		int per = 1;
		if (right.length >= 2) {
			try {
				per = Integer.parseInt(right[1]);
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad count '" + right[1] + "'");
				return null;
			}
		}
		if (right[0].isEmpty() || right[0].equalsIgnoreCase("keep") || per < 1 || per > 64) {
			problems.add("line " + n + ": needs an item and a count from 1 to 64");
			return null;
		}
		String itemId = right[0].toLowerCase(Locale.ROOT);
		boolean holdsPotion = POTION_ITEMS.contains(itemId.indexOf(':') < 0 ? "minecraft:" + itemId : itemId);
		if (potion != null && !holdsPotion) {
			problems.add("line " + n + ": only potion, splash_potion, lingering_potion and tipped_arrow hold a potion, not '" + right[0] + "'");
			return null;
		}
		if (body != null && !holdsPotion) {
			// Gear that keeps its Skyrim enchantment, and maybe its name: skycraft:daedric_sword{sharpness:3 "Mehrunes' Razor"}.
			return parseEnchanted(right[0], per, body, n, problems);
		}
		if (potion != null) {
			if (potion.isEmpty()) {
				problems.add("line " + n + ": '[]' names no potion");
				return null;
			}
			if (!POTIONS.contains(potion)) {
				unknownPotions.computeIfAbsent(potion, k -> new ArrayList<>()).add(n);
				return null;
			}
		}
		List<Effect> effects = List.of();
		int color = -1;
		String name = null;
		if (body != null) {
			List<Effect> fx = new ArrayList<>();
			boolean unknown = false;
			for (String token : effectTokens(body)) {
				if (token.startsWith("\"")) {
					if (token.length() < 2 || !token.endsWith("\"")) {
						problems.add("line " + n + ": no closing '\"' after the name");
						return null;
					}
					name = token.substring(1, token.length() - 1).strip();
					if (name.isEmpty()) {
						problems.add("line " + n + ": '\"\"' names nothing");
						return null;
					}
				} else if (token.startsWith("#")) {
					if (!COLOR.matcher(token).matches()) {
						problems.add("line " + n + ": color '" + token + "' isn't #rrggbb");
						return null;
					}
					color = Integer.parseInt(token.substring(1), 16);
				} else {
					String spec = token.toLowerCase(Locale.ROOT);
					if (spec.startsWith("minecraft:")) {
						spec = spec.substring("minecraft:".length());
					}
					// skycraft:skooma:0:180: a SkyCraft effect keeps its namespace in the id.
					if (spec.startsWith("skycraft:")) {
						int colon = spec.indexOf(':', "skycraft:".length());
						spec = colon < 0 ? spec : spec.substring(0, colon).replace(':', '/') + spec.substring(colon);
					}
					String[] p = spec.split(":", -1);
					if (p.length == 3) {
						p[0] = p[0].replace('/', ':');
					}
					if (p.length != 3 || p[0].isEmpty()) {
						problems.add("line " + n + ": effect '" + token + "' needs effect:amplifier:seconds, like speed:0:60");
						return null;
					}
					int[] amp = range(p[1], MAX_AMPLIFIER), secs = range(p[2], MAX_SECONDS);
					if (amp == null) {
						problems.add("line " + n + ": amplifier '" + p[1] + "' of '" + token + "' isn't 0 to " + MAX_AMPLIFIER + " or a range like 0-2");
						return null;
					}
					if (secs == null) {
						problems.add("line " + n + ": seconds '" + p[2] + "' of '" + token + "' isn't 0 to " + MAX_SECONDS + " or a range like 60-180");
						return null;
					}
					if (!EFFECTS.contains(p[0]) && !SKYCRAFT_EFFECTS.contains(p[0])) {
						unknownEffects.computeIfAbsent(p[0], k -> new ArrayList<>()).add(n);
						unknown = true;
						continue;
					}
					fx.add(new Effect(p[0], amp[0], secs[0], amp[1], secs[1]));
				}
			}
			if (unknown) {
				return null;
			}
			if (fx.isEmpty()) {
				problems.add("line " + n + ": '{}' names no effect");
				return null;
			}
			effects = List.copyOf(fx);
		}
		return new Result(right[0], per, potion, effects, color, name, List.of());
	}

	/**
	 * A non-potion item with braces: {@code item{enchantment:level + ... #rrggbb "name"} N}, the dye only on
	 * {@link #DYEABLE} items. Null (and a problem) when it's bad.
	 */
	private static Result parseEnchanted(String item, int per, String body, int n, List<String> problems) {
		List<Enchant> enchants = new ArrayList<>();
		String name = null;
		int color = -1;
		for (String token : effectTokens(body)) {
			if (token.startsWith("#")) {
				String id = item.toLowerCase(Locale.ROOT);
				if (!DYEABLE.contains(id.indexOf(':') < 0 ? "minecraft:" + id : id)) {
					problems.add("line " + n + ": only leather armor, leather horse armor and wolf armor take a dye like '" + token + "', not '" + item + "'");
					return null;
				}
				if (!COLOR.matcher(token).matches()) {
					problems.add("line " + n + ": color '" + token + "' isn't #rrggbb");
					return null;
				}
				color = Integer.parseInt(token.substring(1), 16);
				continue;
			}
			if (token.startsWith("\"")) {
				if (token.length() < 2 || !token.endsWith("\"")) {
					problems.add("line " + n + ": no closing '\"' after the name");
					return null;
				}
				name = token.substring(1, token.length() - 1).strip();
				if (name.isEmpty()) {
					problems.add("line " + n + ": '\"\"' names nothing");
					return null;
				}
				continue;
			}
			String spec = token.toLowerCase(Locale.ROOT);
			if (spec.startsWith("minecraft:")) {
				spec = spec.substring("minecraft:".length());
			}
			String[] p = spec.split(":", -1);
			if (p.length == 3) {
				problems.add("line " + n + ": only potion, splash_potion, lingering_potion and tipped_arrow hold effects like '" + token + "', not '" + item + "'");
				return null;
			}
			if (p.length != 2 || p[0].isEmpty()) {
				problems.add("line " + n + ": '" + token + "' needs enchantment:level, like sharpness:2");
				return null;
			}
			if (!ENCHANTMENTS.contains(p[0])) {
				problems.add("line " + n + ": unknown enchantment '" + p[0] + "'");
				return null;
			}
			int level;
			try {
				level = Integer.parseInt(p[1]);
			} catch (NumberFormatException e) {
				level = -1;
			}
			if (level < 1 || level > MAX_ENCHANT_LEVEL) {
				problems.add("line " + n + ": level '" + p[1] + "' of '" + token + "' isn't 1 to " + MAX_ENCHANT_LEVEL);
				return null;
			}
			enchants.add(new Enchant(p[0], level));
		}
		if (enchants.isEmpty() && name == null && color < 0) {
			problems.add("line " + n + ": '{}' names no enchantment");
			return null;
		}
		return new Result(item, per, null, List.of(), color, name, List.of(), List.copyOf(enchants));
	}

	/** Where the brace opened at {@code open} closes, skipping quoted text; -1 when it doesn't. */
	private static int closingBrace(String s, int open) {
		boolean quoted = false;
		for (int i = open + 1; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (c == '}' && !quoted) {
				return i;
			}
		}
		return -1;
	}

	/** The parts of an effect list: effects and a color split at spaces and {@code +}, a quoted name whole. */
	private static List<String> effectTokens(String body) {
		List<String> out = new ArrayList<>();
		int i = 0;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (Character.isWhitespace(c) || c == '+') {
				i++;
			} else if (c == '"') {
				int end = body.indexOf('"', i + 1);
				out.add(end < 0 ? body.substring(i) : body.substring(i, end + 1));
				i = end < 0 ? body.length() : end + 1;
			} else {
				int j = i;
				while (j < body.length() && !Character.isWhitespace(body.charAt(j)) && body.charAt(j) != '+' && body.charAt(j) != '"') {
					j++;
				}
				out.add(body.substring(i, j));
				i = j;
			}
		}
		return out;
	}

	/** {@code 2} as {2, 2} and {@code 0-2} as {0, 2}, each at most {@code max}; null when it's neither. */
	private static int[] range(String s, int max) {
		java.util.regex.Matcher m = RANGE.matcher(s);
		if (!m.matches()) {
			return null;
		}
		int low = Integer.parseInt(m.group(1)), high = m.group(2) != null ? Integer.parseInt(m.group(2)) : low;
		return low > max || high > max ? null : new int[] { low, high };
	}

	/** Tag names set in {@code tags}, for logs. */
	public static String tagNames(int tags) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < TAGS.size(); i++) {
			if ((tags & (1 << i)) != 0) {
				if (!sb.isEmpty()) {
					sb.append(',');
				}
				sb.append(TAGS.get(i));
			}
		}
		return sb.toString();
	}

	static int bit(String tag) {
		return 1 << TAGS.indexOf(tag);
	}
}
