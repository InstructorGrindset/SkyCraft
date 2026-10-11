package dev.skycraft.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which Skyrim items become Minecraft items when the player gets them, and what they become.
 *
 * <p>Plain text, one rule per line, first match wins, anything unmatched stays in Skyrim:
 * <pre>
 * tag food name potato = minecraft:potato 1
 * tag weapon material iron from looted = minecraft:iron_ingot 1
 * tag gold = keep
 * </pre>
 * Every condition on the left must hold. {@code name} is a whole word of the Skyrim name (ignoring
 * case; {@code half-eaten} needs both words), {@code tag} one of {@link #TAGS}, {@code material}
 * one of {@link #MATERIALS}, {@code from} one of {@link #SOURCES}. The number on the right is
 * Minecraft items per Skyrim item.
 * Pure Java on purpose, so it's unit tested without Minecraft.
 */
public final class ItemRules {
	/** Tag names, by bit (see ItemTags in the protocol header). */
	public static final List<String> TAGS = List.of("food", "foodraw", "hide", "oreingot", "potion", "poison", "ingredient", "weapon", "armor", "clothing",
		"jewelry", "gold", "key", "arrow", "gem", "clutter", "book");
	/** Material names, by value (see ItemMaterial in the protocol header). */
	public static final List<String> MATERIALS = List.of("none", "wood", "iron", "steel", "imperial", "orcish", "dwarven", "elven", "glass", "ebony", "daedric",
		"dragon", "silver", "hide", "draugr", "falmer");
	/** Source names, by value (see ItemSource); "looted" means corpse or container, "bought" is Skyrim's shop. */
	public static final List<String> SOURCES = List.of("world", "corpse", "container", "actor", "bought");

	public record Item(int tags, int material, int source, String name) {
	}

	/** {@code item} null means keep it in Skyrim. */
	public record Result(String item, int perItem) {
		public boolean keep() {
			return this.item == null;
		}
	}

	private static final Result KEEP = new Result(null, 0);
	/** What separates the words of a name: anything but letters, digits and apostrophes. */
	private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9']+");

	record Rule(int tagMask, int material, int sourceMask, List<String> nameWords, Result result) {
		boolean matches(Item it, List<String> words) {
			if ((it.tags() & this.tagMask) != this.tagMask) {
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

	public static final String DEFAULT_TEXT = """
		# SkyCraft item conversion: which Skyrim items become Minecraft items when you get them.
		# One rule per line, first match wins. Anything that matches no rule stays in Skyrim.
		#   conditions = minecraft:item N     (N Minecraft items per Skyrim item)
		#   conditions = keep                 (stays in Skyrim)
		# Conditions (all must hold): tag <t>, name <word>, material <m>, from <where>
		#   tags: food foodraw hide oreingot potion poison ingredient weapon armor clothing jewelry
		#         gold key arrow gem clutter book
		#   materials: wood iron steel imperial orcish dwarven elven glass ebony daedric dragon
		#              silver hide draugr falmer
		#   from: world corpse container actor bought (Skyrim's shop) looted (corpse or container)
		# Restart Skyrim after editing.

		# Stays in Skyrim: coins for merchants, keys and books for quests, drinks
		tag gold = keep
		tag key = keep
		tag book = keep
		tag food name wine = keep
		tag food name ale = keep
		tag food name mead = keep
		tag food name skooma = keep
		tag food name milk = keep

		# Food
		tag food name pie = minecraft:pumpkin_pie 1
		tag food name salmon name steak = minecraft:cooked_salmon 1
		tag food name salmon = minecraft:salmon 1
		tag food name potato = minecraft:potato 1
		tag food name potatoes = minecraft:baked_potato 1
		tag food name apple = minecraft:apple 1
		tag food name carrot = minecraft:carrot 1
		tag food name cabbage = minecraft:beetroot 1
		tag food name leek = minecraft:beetroot 1
		tag food name tomato = minecraft:sweet_berries 2
		tag food name bread = minecraft:bread 1
		tag food name roll = minecraft:cookie 2
		tag food name cheese = minecraft:bread 1
		tag food name honey = minecraft:honey_bottle 1
		tag food name stew = minecraft:rabbit_stew 1
		tag food name soup = minecraft:mushroom_stew 1
		tag food name steak = minecraft:cooked_beef 1
		tag food name roast = minecraft:cooked_beef 1
		tag food name grilled = minecraft:cooked_chicken 1
		tag foodraw name breast = minecraft:chicken 1
		tag foodraw name leg = minecraft:mutton 1
		tag foodraw name snout = minecraft:beef 2
		tag foodraw name venison = minecraft:beef 1
		tag foodraw name beef = minecraft:beef 1
		tag foodraw name meat = minecraft:beef 1
		tag food = minecraft:bread 1

		# Flowers, berries, mushrooms and feathers picked in Skyrim (Nirnroot and other rare ingredients stay)
		tag ingredient name blue name mountain name flower = minecraft:cornflower 1
		tag ingredient name red name mountain name flower = minecraft:poppy 1
		tag ingredient name purple name mountain name flower = minecraft:allium 1
		tag ingredient name yellow name mountain name flower = minecraft:dandelion 1
		tag ingredient name lavender = minecraft:lilac 1
		tag ingredient name tongue = minecraft:orange_tulip 1
		tag ingredient name snowberries = minecraft:sweet_berries 2
		tag ingredient name juniper name berries = minecraft:sweet_berries 2
		tag ingredient name jazbay = minecraft:sweet_berries 2
		tag ingredient name fly name amanita = minecraft:red_mushroom 1
		tag ingredient name cap = minecraft:brown_mushroom 1
		tag ingredient name stool = minecraft:brown_mushroom 1
		tag ingredient name wheat = minecraft:wheat 1
		tag ingredient name bone name meal = minecraft:bone_meal 2
		tag ingredient name cotton = minecraft:string 2
		name feathers = minecraft:feather 2
		tag foodraw name legs = minecraft:cod 1

		# Leather armor bought in a Skyrim shop becomes Minecraft leather armor
		tag armor material hide name helmet from bought = minecraft:leather_helmet 1
		tag armor material hide name hood from bought = minecraft:leather_helmet 1
		tag armor material hide name armor from bought = minecraft:leather_chestplate 1
		tag armor material hide name bracers from bought = minecraft:leather_leggings 1
		tag armor material hide name gauntlets from bought = minecraft:leather_leggings 1
		tag armor material hide name boots from bought = minecraft:leather_boots 1

		# Hides and leather (strips before hides, in case Skyrim tags them as hide)
		name leather name strips = skycraft:leather_strips 1
		tag hide = minecraft:leather 1
		name leather = minecraft:leather 1
		name firewood = minecraft:oak_log 1

		# Metals and gems
		tag oreingot name iron name ingot = minecraft:iron_ingot 1
		tag oreingot name iron name ore = minecraft:raw_iron 1
		tag oreingot name steel = skycraft:steel_ingot 1
		tag oreingot name gold name ingot = minecraft:gold_ingot 1
		tag oreingot name gold name ore = minecraft:raw_gold 1
		tag oreingot name corundum name ingot = minecraft:copper_ingot 1
		tag oreingot name corundum name ore = minecraft:raw_copper 1
		tag gem name diamond = minecraft:diamond 1
		tag gem name emerald = minecraft:emerald 1
		tag gem name amethyst = minecraft:amethyst_shard 2

		# Skyrim smithing materials for the SkyCraft gear sets (ore and ingot alike)
		tag oreingot name orichalcum = skycraft:orichalcum_ingot 1
		tag oreingot name dwarven = skycraft:dwarven_metal_ingot 1
		tag oreingot name moonstone = skycraft:refined_moonstone 1
		tag oreingot name malachite = skycraft:refined_malachite 1
		tag oreingot name ebony = skycraft:ebony_ingot 1
		name dwemer name scrap = skycraft:dwarven_metal_ingot 1
		name solid name dwemer name metal = skycraft:dwarven_metal_ingot 2
		name dwemer name plate = skycraft:dwarven_metal_ingot 2
		name dwemer name strut = skycraft:dwarven_metal_ingot 3
		name daedra name heart = skycraft:daedra_heart 1

		# Arrows
		tag arrow = minecraft:arrow 1

		# Bows from anywhere become Minecraft bows
		tag weapon name crossbow = minecraft:crossbow 1
		tag weapon name bow = minecraft:bow 1

		# Gear bought in a Skyrim shop becomes the matching SkyCraft gear (looted gear salvages instead).
		# Gauntlets fill the greaves slot, since Minecraft has no glove slot.
		tag armor name shield from bought = minecraft:shield 1
		tag weapon material iron name sword from bought = skycraft:iron_sword 1
		tag weapon material iron name greatsword from bought = skycraft:iron_sword 1
		tag weapon material iron name dagger from bought = skycraft:iron_sword 1
		tag weapon material iron name axe from bought = skycraft:iron_war_axe 1
		tag weapon material iron name battleaxe from bought = skycraft:iron_war_axe 1
		tag weapon material iron name mace from bought = skycraft:iron_war_axe 1
		tag weapon material iron name warhammer from bought = skycraft:iron_war_axe 1
		tag armor material iron name helmet from bought = skycraft:iron_helmet 1
		tag armor material iron name armor from bought = skycraft:iron_cuirass 1
		tag armor material iron name cuirass from bought = skycraft:iron_cuirass 1
		tag armor material iron name gauntlets from bought = skycraft:iron_greaves 1
		tag armor material iron name boots from bought = skycraft:iron_boots 1
		tag weapon material steel name sword from bought = skycraft:steel_sword 1
		tag weapon material steel name greatsword from bought = skycraft:steel_sword 1
		tag weapon material steel name dagger from bought = skycraft:steel_sword 1
		tag weapon material steel name axe from bought = skycraft:steel_war_axe 1
		tag weapon material steel name battleaxe from bought = skycraft:steel_war_axe 1
		tag weapon material steel name mace from bought = skycraft:steel_war_axe 1
		tag weapon material steel name warhammer from bought = skycraft:steel_war_axe 1
		tag armor material steel name helmet from bought = skycraft:steel_helmet 1
		tag armor material steel name armor from bought = skycraft:steel_cuirass 1
		tag armor material steel name cuirass from bought = skycraft:steel_cuirass 1
		tag armor material steel name gauntlets from bought = skycraft:steel_greaves 1
		tag armor material steel name boots from bought = skycraft:steel_boots 1
		tag weapon material orcish name sword from bought = skycraft:orcish_sword 1
		tag weapon material orcish name greatsword from bought = skycraft:orcish_sword 1
		tag weapon material orcish name dagger from bought = skycraft:orcish_sword 1
		tag weapon material orcish name axe from bought = skycraft:orcish_war_axe 1
		tag weapon material orcish name battleaxe from bought = skycraft:orcish_war_axe 1
		tag weapon material orcish name mace from bought = skycraft:orcish_war_axe 1
		tag weapon material orcish name warhammer from bought = skycraft:orcish_war_axe 1
		tag armor material orcish name helmet from bought = skycraft:orcish_helmet 1
		tag armor material orcish name armor from bought = skycraft:orcish_cuirass 1
		tag armor material orcish name cuirass from bought = skycraft:orcish_cuirass 1
		tag armor material orcish name gauntlets from bought = skycraft:orcish_greaves 1
		tag armor material orcish name boots from bought = skycraft:orcish_boots 1
		tag weapon material dwarven name sword from bought = skycraft:dwarven_sword 1
		tag weapon material dwarven name greatsword from bought = skycraft:dwarven_sword 1
		tag weapon material dwarven name dagger from bought = skycraft:dwarven_sword 1
		tag weapon material dwarven name axe from bought = skycraft:dwarven_war_axe 1
		tag weapon material dwarven name battleaxe from bought = skycraft:dwarven_war_axe 1
		tag weapon material dwarven name mace from bought = skycraft:dwarven_war_axe 1
		tag weapon material dwarven name warhammer from bought = skycraft:dwarven_war_axe 1
		tag armor material dwarven name helmet from bought = skycraft:dwarven_helmet 1
		tag armor material dwarven name armor from bought = skycraft:dwarven_cuirass 1
		tag armor material dwarven name cuirass from bought = skycraft:dwarven_cuirass 1
		tag armor material dwarven name gauntlets from bought = skycraft:dwarven_greaves 1
		tag armor material dwarven name boots from bought = skycraft:dwarven_boots 1
		tag weapon material elven name sword from bought = skycraft:elven_sword 1
		tag weapon material elven name greatsword from bought = skycraft:elven_sword 1
		tag weapon material elven name dagger from bought = skycraft:elven_sword 1
		tag weapon material elven name axe from bought = skycraft:elven_war_axe 1
		tag weapon material elven name battleaxe from bought = skycraft:elven_war_axe 1
		tag weapon material elven name mace from bought = skycraft:elven_war_axe 1
		tag weapon material elven name warhammer from bought = skycraft:elven_war_axe 1
		tag armor material elven name helmet from bought = skycraft:elven_helmet 1
		tag armor material elven name armor from bought = skycraft:elven_cuirass 1
		tag armor material elven name cuirass from bought = skycraft:elven_cuirass 1
		tag armor material elven name gauntlets from bought = skycraft:elven_greaves 1
		tag armor material elven name boots from bought = skycraft:elven_boots 1
		tag weapon material glass name sword from bought = skycraft:glass_sword 1
		tag weapon material glass name greatsword from bought = skycraft:glass_sword 1
		tag weapon material glass name dagger from bought = skycraft:glass_sword 1
		tag weapon material glass name axe from bought = skycraft:glass_war_axe 1
		tag weapon material glass name battleaxe from bought = skycraft:glass_war_axe 1
		tag weapon material glass name mace from bought = skycraft:glass_war_axe 1
		tag weapon material glass name warhammer from bought = skycraft:glass_war_axe 1
		tag armor material glass name helmet from bought = skycraft:glass_helmet 1
		tag armor material glass name armor from bought = skycraft:glass_cuirass 1
		tag armor material glass name cuirass from bought = skycraft:glass_cuirass 1
		tag armor material glass name gauntlets from bought = skycraft:glass_greaves 1
		tag armor material glass name boots from bought = skycraft:glass_boots 1
		tag weapon material ebony name sword from bought = skycraft:ebony_sword 1
		tag weapon material ebony name greatsword from bought = skycraft:ebony_sword 1
		tag weapon material ebony name dagger from bought = skycraft:ebony_sword 1
		tag weapon material ebony name axe from bought = skycraft:ebony_war_axe 1
		tag weapon material ebony name battleaxe from bought = skycraft:ebony_war_axe 1
		tag weapon material ebony name mace from bought = skycraft:ebony_war_axe 1
		tag weapon material ebony name warhammer from bought = skycraft:ebony_war_axe 1
		tag armor material ebony name helmet from bought = skycraft:ebony_helmet 1
		tag armor material ebony name armor from bought = skycraft:ebony_cuirass 1
		tag armor material ebony name cuirass from bought = skycraft:ebony_cuirass 1
		tag armor material ebony name gauntlets from bought = skycraft:ebony_greaves 1
		tag armor material ebony name boots from bought = skycraft:ebony_boots 1
		tag weapon material daedric name sword from bought = skycraft:daedric_sword 1
		tag weapon material daedric name greatsword from bought = skycraft:daedric_sword 1
		tag weapon material daedric name dagger from bought = skycraft:daedric_sword 1
		tag weapon material daedric name axe from bought = skycraft:daedric_war_axe 1
		tag weapon material daedric name battleaxe from bought = skycraft:daedric_war_axe 1
		tag weapon material daedric name mace from bought = skycraft:daedric_war_axe 1
		tag weapon material daedric name warhammer from bought = skycraft:daedric_war_axe 1
		tag armor material daedric name helmet from bought = skycraft:daedric_helmet 1
		tag armor material daedric name armor from bought = skycraft:daedric_cuirass 1
		tag armor material daedric name cuirass from bought = skycraft:daedric_cuirass 1
		tag armor material daedric name gauntlets from bought = skycraft:daedric_greaves 1
		tag armor material daedric name boots from bought = skycraft:daedric_boots 1

		# Looted iron and steel gear salvages into ingots
		tag weapon material iron from looted = minecraft:iron_ingot 1
		tag weapon material steel from looted = skycraft:steel_ingot 1
		tag armor material iron from looted = minecraft:iron_ingot 2
		tag armor material steel from looted = skycraft:steel_ingot 2
		tag armor material hide from looted = minecraft:leather 2
		""";

	private final List<Rule> rules;

	private ItemRules(List<Rule> rules) {
		this.rules = rules;
	}

	public int size() {
		return this.rules.size();
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

	/** The first matching rule's result; keep (stays in Skyrim) when nothing matches. */
	public Result match(Item item) {
		List<String> words = words(item.name());
		for (Rule rule : this.rules) {
			if (rule.matches(item, words)) {
				return rule.result();
			}
		}
		return KEEP;
	}

	public static ItemRules parse(String text, List<String> problems) {
		List<Rule> out = new ArrayList<>();
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
			String[] right = line.substring(eq + 1).strip().split("\\s+");
			if (left.length % 2 != 0) {
				problems.add("line " + n + ": conditions come in pairs, like 'tag food'");
				continue;
			}
			int tagMask = 0, material = -1, sourceMask = 0;
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
			if (right.length == 1 && right[0].equalsIgnoreCase("keep")) {
				result = KEEP;
			} else {
				int per = 1;
				if (right.length >= 2) {
					try {
						per = Integer.parseInt(right[1]);
					} catch (NumberFormatException e) {
						problems.add("line " + n + ": bad count '" + right[1] + "'");
						continue;
					}
				}
				if (right[0].isEmpty() || per < 1 || per > 64) {
					problems.add("line " + n + ": needs an item and a count from 1 to 64");
					continue;
				}
				result = new Result(right[0], per);
			}
			out.add(new Rule(tagMask, material, sourceMask, List.copyOf(names), result));
		}
		return new ItemRules(List.copyOf(out));
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
