package dev.skycraft.trade;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What Skyrim merchants trade as Minecraft items, and how each town prices it.
 *
 * <p>Two plain text files, so both can be edited without a rebuild. {@code skycraft-trades.txt}
 * lists every trade a merchant of a category can offer, one per line:
 * <pre>
 * # category sell|buy|gold count item price
 * weapon sell 1 minecraft:iron_sword 40
 * hide buy 4 minecraft:leather 4
 * ingot gold 1 minecraft:iron_ingot 3
 * skooma sell 1 minecraft:potion[skycraft:skooma] 30
 * dealer skooma = Ri'saad, Ahkari, Ma'dran
 * </pre>
 * {@code sell} means the merchant sells to the player and {@code buy} that the merchant buys from the
 * player, both on the villager trade screen for Minecraft coin; {@code gold} that the merchant buys
 * from the player for Skyrim gold, out of its own (sneak and right click it with the goods, {@link SkySell}).
 * An item that holds a potion may name it in brackets on a sell or gold line. A {@code dealer} line
 * names merchants (by their Skyrim name) who also deal in a category no Skyrim shop lists by itself
 * ({@link #DEALER_CATEGORIES}): their trades of it are always offered. {@code skycraft-towns.txt} sets
 * what each town pays with and what's cheap or dear there:
 * <pre>
 * Whiterun = copper gold ; food 0.7, weapon 0.9
 * * = copper gold
 * </pre>
 * A multiplier applies both ways: a town where food is plentiful sells it cheap and pays little for
 * it (in coin and in gold). Towns match ignoring case; unknown towns use the {@code *} line. Prices
 * are in coins (see {@link Currency}), gold lines' in Skyrim gold. Pure Java on purpose, so it's unit
 * tested without Minecraft.
 */
public final class TradeRules {
	/** Trade categories, in Proto.VENDOR_* bit order. */
	public static final List<String> CATEGORIES = List.of("weapon", "armor", "ingot", "potion", "ingredient", "food", "arrow", "jewelry", "book", "hide",
		"tool", "stolen");
	private static final int[] CATEGORY_BITS = { Proto.VENDOR_WEAPON, Proto.VENDOR_ARMOR, Proto.VENDOR_INGOT, Proto.VENDOR_POTION,
		Proto.VENDOR_INGREDIENT, Proto.VENDOR_FOOD, Proto.VENDOR_ARROW, Proto.VENDOR_JEWELRY, Proto.VENDOR_BOOK, Proto.VENDOR_HIDE, Proto.VENDOR_TOOL,
		Proto.VENDOR_STOLEN };

	/**
	 * Categories no Skyrim shop deals in by itself (no VendorBuys bit): only the merchants a {@code dealer} line names
	 * offer their trades (Skooma and moon sugar, from the Khajiit caravans).
	 */
	public static final List<String> DEALER_CATEGORIES = List.of("skooma");
	/**
	 * Not a Skyrim VendorBuys bit (those end at bit 12): Minecraft puts it on the stand-in of an actor a {@code dealer}
	 * line names when Skyrim doesn't make it a merchant, so a click opens its shop. It deals in no category of its own.
	 */
	public static final int DEALER_ONLY = 1 << 15;
	/** Items that hold a potion, which a sell or gold line may name in brackets: {@code minecraft:potion[skycraft:skooma]}. */
	public static final List<String> POTION_ITEMS = dev.skycraft.loot.ItemRules.POTION_ITEMS;

	/** Most offers one merchant shows. */
	public static final int MAX_OFFERS = 12;
	/** Highest price a trades line may set, far above anything two stacks of currency can pay. */
	public static final int MAX_PRICE = 100_000;
	/** Most batches one sale for gold sells: a stack of a one-at-a-time item. */
	public static final int MAX_GOLD_BATCHES = 64;

	/** One sell or buy line of the trades file. */
	public record Trade(String category, boolean sell, int count, String item, int price) {
	}

	/** One gold line of the trades file: a merchant dealing in {@code category} pays {@code gold} Skyrim gold for {@code count} of {@code item}. */
	public record GoldPrice(String category, int count, String item, int gold) {
	}

	/** One line of the towns file. {@code currencies} are item ids. */
	public record Town(String name, Set<String> currencies, Map<String, Double> multipliers) {
		public double multiplier(String category) {
			return this.multipliers.getOrDefault(category, 1.0);
		}
	}

	/** One offer of a merchant, priced for its town; {@code price} is in coins. */
	public record Offer(boolean sell, String item, int count, int price, String category) {
	}

	/** Used when the towns file has no {@code *} line. */
	private static final Town FALLBACK = new Town("*", Set.of(Currency.COPPER, Currency.NUGGET, Currency.GOLD), Map.of());

	public static final String DEFAULT_TRADES = """
		# SkyCraft merchant trades: what Skyrim merchants trade as Minecraft items.
		# One trade per line:  category sell|buy|gold count item price
		#   sell = the merchant sells to you, buy = the merchant buys from you (both on the trade
		#   screen: right click a merchant with an empty hand), price in coins: copper ingot 1,
		#   gold nugget 2, gold ingot 18, emerald 24, diamond 96
		#   gold = the merchant buys from you for Skyrim gold, put in your Skyrim inventory: sneak and
		#   right click it with the goods in your main hand. It pays out of its own gold, for whole
		#   lots of count items, and every lot sold lowers what it pays for that item in town a while.
		#   categories: weapon armor ingot potion ingredient food arrow jewelry book hide tool stolen
		#   An item that holds a potion can name it on a sell or gold line: minecraft:potion[healing]
		# A merchant shows up to 12 of the trades its Skyrim shop deals in; general goods stores
		# deal in everything except stolen goods, fences in stolen goods. What each town pays with
		# and what's cheap there is in skycraft-towns.txt. Restart Minecraft after editing.

		weapon sell 1 minecraft:iron_sword 40
		weapon sell 1 minecraft:bow 30
		weapon sell 1 minecraft:crossbow 45
		weapon sell 1 minecraft:shield 25
		weapon buy 1 minecraft:iron_sword 16
		weapon buy 1 minecraft:bow 12

		armor sell 1 minecraft:iron_helmet 40
		armor sell 1 minecraft:iron_chestplate 64
		armor sell 1 minecraft:iron_leggings 56
		armor sell 1 minecraft:iron_boots 32
		armor sell 1 minecraft:chainmail_chestplate 48
		armor sell 1 minecraft:leather_chestplate 16
		armor buy 1 minecraft:iron_chestplate 26
		armor buy 1 minecraft:leather_chestplate 6

		ingot sell 4 minecraft:iron_ingot 24
		ingot sell 8 minecraft:coal 6
		ingot sell 1 minecraft:diamond 200
		# Obsidian for portal frames (ten make one): dear, and cheapest where ingots are (Markarth, the mining towns)
		ingot sell 2 minecraft:obsidian 72
		ingot buy 4 minecraft:iron_ingot 12
		ingot buy 4 minecraft:raw_iron 10
		ingot buy 2 minecraft:raw_gold 14
		ingot buy 16 minecraft:coal 4

		potion sell 1 minecraft:golden_apple 60
		potion sell 4 minecraft:experience_bottle 30
		potion sell 4 minecraft:glass_bottle 4
		potion buy 4 minecraft:glass_bottle 1
		potion buy 4 minecraft:spider_eye 6

		ingredient sell 8 minecraft:redstone 8
		ingredient sell 8 minecraft:glowstone_dust 12
		ingredient sell 2 minecraft:blaze_powder 24
		ingredient sell 8 minecraft:sugar 4
		ingredient buy 8 minecraft:spider_eye 6
		ingredient buy 16 minecraft:sugar 3

		food sell 6 minecraft:bread 6
		food sell 4 minecraft:cooked_beef 8
		food sell 8 minecraft:apple 5
		food sell 6 minecraft:baked_potato 4
		food sell 1 minecraft:cake 10
		food buy 20 minecraft:wheat 4
		food buy 20 minecraft:potato 3
		food buy 8 minecraft:beef 4
		food buy 8 minecraft:cod 4

		arrow sell 16 minecraft:arrow 8
		arrow sell 8 minecraft:spectral_arrow 12
		arrow buy 32 minecraft:feather 4
		arrow buy 32 minecraft:flint 4

		jewelry sell 1 minecraft:emerald 48
		jewelry sell 4 minecraft:amethyst_shard 12
		jewelry sell 1 minecraft:clock 30
		jewelry buy 2 minecraft:amethyst_shard 3
		jewelry buy 1 minecraft:clock 12

		book sell 1 minecraft:book 6
		book sell 1 minecraft:writable_book 10
		book sell 1 minecraft:map 12
		book sell 1 minecraft:bookshelf 24
		book buy 6 minecraft:paper 2
		book buy 1 minecraft:book 2

		hide sell 4 minecraft:leather 8
		hide sell 4 minecraft:rabbit_hide 4
		hide buy 4 minecraft:leather 4
		hide buy 8 minecraft:bone 2
		hide buy 16 minecraft:rotten_flesh 1

		tool sell 1 minecraft:iron_pickaxe 40
		tool sell 1 minecraft:iron_shovel 14
		tool sell 1 minecraft:shears 12
		tool sell 1 minecraft:fishing_rod 10
		tool sell 16 minecraft:torch 4
		tool sell 1 minecraft:lantern 6
		tool sell 1 minecraft:bucket 20
		tool sell 1 minecraft:obsidian 40
		tool buy 1 minecraft:iron_pickaxe 16
		tool buy 16 minecraft:string 3

		stolen sell 4 minecraft:tnt 40
		stolen sell 1 minecraft:name_tag 30
		stolen sell 1 minecraft:saddle 36
		stolen buy 1 minecraft:name_tag 12
		stolen buy 1 minecraft:saddle 14
		stolen buy 1 minecraft:clock 16

		# Skooma and moon sugar. No Skyrim shop lists them, so a dealer line names who deals in them, on
		# top of what their own shop trades:  dealer category = Name, Name, ...  (Skyrim's names, any case)
		# A dealer Skyrim doesn't make a merchant opens a shop of these alone (talk to them with G).
		skooma sell 1 minecraft:potion[skycraft:skooma] 30
		skooma sell 1 minecraft:potion[skycraft:redwater_skooma] 80
		skooma sell 1 skycraft:moon_sugar 50
		skooma gold 1 skycraft:moon_sugar 15
		skooma gold 1 minecraft:potion[skycraft:skooma] 8
		dealer skooma = Ri'saad, Ahkari, Ma'dran, Kharjo, Zaynabi, Atahbah, Ma'jhad, Khayla, Ra'zhinda, Dro'marash, Ma'randru-jo

		# What merchants pay in Skyrim gold (gold lines): about what Skyrim's own merchants pay for the
		# nearest Skyrim goods, less than they ask for them, so gold can't be farmed round the two games.
		weapon gold 1 minecraft:iron_sword 8
		weapon gold 1 minecraft:diamond_sword 60
		weapon gold 1 minecraft:bow 6
		weapon gold 1 minecraft:crossbow 9
		weapon gold 1 skycraft:steel_sword 12
		weapon gold 1 skycraft:ebony_sword 90

		armor gold 1 minecraft:iron_chestplate 14
		armor gold 1 minecraft:diamond_chestplate 100
		armor gold 1 minecraft:leather_chestplate 4
		armor gold 1 skycraft:steel_cuirass 20

		ingot gold 1 minecraft:iron_ingot 3
		ingot gold 1 minecraft:gold_ingot 25
		ingot gold 4 minecraft:copper_ingot 3
		ingot gold 1 minecraft:netherite_ingot 300
		ingot gold 4 minecraft:raw_iron 2
		ingot gold 1 minecraft:raw_gold 6
		ingot gold 8 minecraft:coal 2
		ingot gold 1 skycraft:steel_ingot 8
		ingot gold 1 skycraft:orichalcum_ingot 14
		ingot gold 1 skycraft:dwarven_metal_ingot 12
		ingot gold 1 skycraft:refined_moonstone 20
		ingot gold 1 skycraft:refined_malachite 30
		ingot gold 1 skycraft:ebony_ingot 60

		potion gold 1 minecraft:potion[healing] 8
		potion gold 1 minecraft:experience_bottle 4
		potion gold 4 minecraft:glass_bottle 1

		ingredient gold 4 minecraft:spider_eye 2
		ingredient gold 4 minecraft:glowstone_dust 3
		ingredient gold 2 minecraft:blaze_powder 5
		ingredient gold 4 minecraft:nether_wart 2
		ingredient gold 8 minecraft:sugar 1

		food gold 4 minecraft:bread 2
		food gold 2 minecraft:cooked_beef 3
		food gold 2 minecraft:cooked_porkchop 3
		food gold 2 minecraft:cooked_mutton 2
		food gold 2 minecraft:cooked_chicken 2
		food gold 2 minecraft:cooked_salmon 3
		food gold 4 minecraft:apple 3
		food gold 4 minecraft:beef 2
		food gold 8 minecraft:wheat 2
		food gold 8 minecraft:potato 2
		food gold 8 minecraft:carrot 2
		food gold 1 minecraft:cake 5
		food gold 1 minecraft:pumpkin_pie 2

		arrow gold 16 minecraft:arrow 3
		arrow gold 16 minecraft:feather 2

		jewelry gold 1 minecraft:diamond 150
		jewelry gold 1 minecraft:emerald 100
		jewelry gold 4 minecraft:amethyst_shard 20
		jewelry gold 8 minecraft:lapis_lazuli 24
		jewelry gold 8 minecraft:redstone 12
		jewelry gold 4 minecraft:quartz 6
		jewelry gold 1 minecraft:clock 15

		book gold 1 minecraft:book 2
		book gold 1 minecraft:enchanted_book 25
		book gold 1 minecraft:map 3
		book gold 8 minecraft:paper 1

		hide gold 1 minecraft:leather 3
		hide gold 4 minecraft:rabbit_hide 3
		hide gold 4 skycraft:leather_strips 1
		hide gold 8 minecraft:bone 2

		tool gold 1 minecraft:iron_pickaxe 6
		tool gold 1 minecraft:diamond_pickaxe 50
		tool gold 1 minecraft:shears 3
		tool gold 1 minecraft:lantern 2
		tool gold 16 minecraft:torch 2

		stolen gold 1 minecraft:name_tag 10
		stolen gold 1 minecraft:saddle 12
		stolen gold 1 minecraft:clock 12
		""";

	/**
	 * Every {@link #DEFAULT_TRADES} shipped before, by hash: the first merchants build's, and the one with obsidian for
	 * portals. A trades file still holding one untouched gets the new defaults (config.Config.read). Add the hash of the
	 * current default here ({@code Config.sha256(DEFAULT_TRADES)}) whenever you change it.
	 */
	public static final List<String> OLD_TRADES = List.of(
		"sha256:5d4f7705376449a610d71b8691c4fe06f9416a9361bba65a18d214a21db81016",
		"sha256:a1bc3d4215993e0b070111f093852d77c8590cf46a36ed8c385201e11ff2a962");

	public static final String DEFAULT_TOWNS = """
		# SkyCraft towns: what Skyrim towns pay with and what's cheap or dear there.
		# One town per line:  Town Name = currencies ; category multiplier, category multiplier, ...
		#   currencies: copper (copper ingots), gold (gold nuggets and ingots), emerald, diamond
		#   a multiplier below 1 means plentiful: cheap to buy, and merchants pay less for it
		#   categories: weapon armor ingot potion ingredient food arrow jewelry book hide tool stolen
		# Town names match Skyrim's, ignoring case. The * line is for everywhere else.
		# Restart Skyrim after editing.

		Whiterun = copper gold ; food 0.7, hide 0.8, weapon 0.9, armor 0.9
		Riverwood = copper gold ; weapon 0.8, armor 0.8, food 0.8, book 1.3
		Rorikstead = copper ; food 0.6, hide 0.8, tool 1.2, book 1.4
		Solitude = gold emerald diamond ; jewelry 1.4, book 1.2, potion 1.2, weapon 1.2, armor 1.2, food 1.2, stolen 1.4
		Dragon Bridge = copper gold ; food 0.9, hide 0.8, tool 1.1
		Morthal = copper ; ingredient 0.7, potion 0.8, food 1.2, weapon 1.3
		Dawnstar = copper gold ; ingot 0.7, tool 0.8, food 1.3, hide 1.1
		Winterhold = copper gold ; book 0.6, potion 0.9, ingredient 0.9, food 1.4, weapon 1.3
		Windhelm = gold emerald ; weapon 0.9, armor 0.9, ingot 0.9, food 1.1, stolen 1.2
		Kynesgrove = copper ; ingot 0.7, tool 0.9, food 1.2
		Riften = copper gold ; stolen 0.6, food 0.8, potion 0.9, jewelry 1.2
		Ivarstead = copper ; food 0.8, hide 0.8, arrow 0.9, book 1.3
		Shor's Stone = copper ; ingot 0.6, tool 0.9, food 1.3
		Falkreath = copper gold ; hide 0.7, arrow 0.8, food 0.9, ingot 1.2
		Markarth = gold emerald diamond ; ingot 0.7, jewelry 1.5, armor 1.2, weapon 1.2, food 1.3, stolen 1.3
		Karthwasten = copper ; ingot 0.6, tool 0.9, food 1.3
		Raven Rock = copper emerald ; ingot 0.8, hide 0.9, ingredient 1.2, food 1.4
		* = copper gold
		""";

	private final List<Trade> trades;
	private final List<GoldPrice> gold;
	/** Lower case Skyrim name to the dealer categories its dealer lines give it. */
	private final Map<String, Set<String>> dealers;
	private final Map<String, Town> towns;

	private TradeRules(List<Trade> trades, List<GoldPrice> gold, Map<String, Set<String>> dealers, Map<String, Town> towns) {
		this.trades = trades;
		this.gold = gold;
		this.dealers = dealers;
		this.towns = towns;
	}

	/** The sell and buy lines: the trade screen's. */
	public List<Trade> trades() {
		return this.trades;
	}

	/** The gold lines: what merchants pay in Skyrim gold. */
	public List<GoldPrice> gold() {
		return this.gold;
	}

	public Map<String, Town> towns() {
		return this.towns;
	}

	/** Shipped defaults; any problem with them is a bug. */
	public static TradeRules defaults(List<String> problems) {
		return parse(DEFAULT_TRADES, DEFAULT_TOWNS, problems);
	}

	/** Parses both files. Bad lines are skipped and reported to {@code problems}, once each. */
	public static TradeRules parse(String tradesText, String townsText, List<String> problems) {
		List<Trade> trades = new ArrayList<>();
		List<GoldPrice> gold = new ArrayList<>();
		Map<String, Set<String>> dealers = new HashMap<>();
		parseTrades(tradesText, trades, gold, dealers, problems);
		Map<String, Set<String>> frozen = new HashMap<>();
		dealers.forEach((name, categories) -> frozen.put(name, Set.copyOf(categories)));
		return new TradeRules(List.copyOf(trades), List.copyOf(gold), Map.copyOf(frozen), Map.copyOf(parseTowns(townsText, problems)));
	}

	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	/** A namespaced id ({@code minecraft:} when it has none), or null when it isn't one. */
	private static String normalizeId(String id) {
		String out = id.strip().toLowerCase(Locale.ROOT);
		out = out.indexOf(':') < 0 ? "minecraft:" + out : out;
		return ID.matcher(out).matches() ? out : null;
	}

	/**
	 * {@code item} as the trades keep it: {@code minecraft:bread}, or with the potion it holds, both namespaced:
	 * {@code minecraft:potion[skycraft:skooma]}. Null when it's malformed, or brackets a potion on an item that can't
	 * hold one.
	 */
	static String normalizeItem(String item) {
		int open = item.indexOf('[');
		if (open < 0) {
			return normalizeId(item);
		}
		if (!item.endsWith("]")) {
			return null;
		}
		String base = normalizeId(item.substring(0, open));
		String potion = normalizeId(item.substring(open + 1, item.length() - 1));
		if (base == null || potion == null || !POTION_ITEMS.contains(base)) {
			return null;
		}
		return base + "[" + potion + "]";
	}

	/** The item of a trades item without its potion: {@code minecraft:potion} for {@code minecraft:potion[skycraft:skooma]}. */
	public static String baseItem(String item) {
		int open = item.indexOf('[');
		return open < 0 ? item : item.substring(0, open);
	}

	/** The potion a trades item names in brackets, or null: {@code skycraft:skooma} for {@code minecraft:potion[skycraft:skooma]}. */
	public static String potionOf(String item) {
		int open = item.indexOf('[');
		return open < 0 ? null : item.substring(open + 1, item.length() - 1);
	}

	private static boolean known(String category) {
		return CATEGORIES.contains(category) || DEALER_CATEGORIES.contains(category);
	}

	private static String townKey(String town) {
		return town == null || town.isBlank() ? "*" : town.strip().toLowerCase(Locale.ROOT);
	}

	private static String nameKey(String name) {
		return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
	}

	private static void parseTrades(String text, List<Trade> trades, List<GoldPrice> gold, Map<String, Set<String>> dealers, List<String> problems) {
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String where = "trades line " + n + ": ";
			if (line.toLowerCase(Locale.ROOT).startsWith("dealer ")) {
				parseDealer(line, where, dealers, problems);
				continue;
			}
			String[] bits = line.split("\\s+");
			if (bits.length != 5) {
				problems.add(where + "expected 'category sell|buy|gold count item price'");
				continue;
			}
			String category = bits[0].toLowerCase(Locale.ROOT);
			if (!known(category)) {
				problems.add(where + "unknown category '" + bits[0] + "'");
				continue;
			}
			String way = bits[1].toLowerCase(Locale.ROOT);
			if (!way.equals("sell") && !way.equals("buy") && !way.equals("gold")) {
				problems.add(where + "expected sell, buy or gold, not '" + bits[1] + "'");
				continue;
			}
			int count, price;
			try {
				count = Integer.parseInt(bits[2]);
				price = Integer.parseInt(bits[4]);
			} catch (NumberFormatException e) {
				problems.add(where + "count and price must be whole numbers");
				continue;
			}
			if (count < 1 || count > 64) {
				problems.add(where + "count must be 1 to 64");
				continue;
			}
			if (price < 1 || price > MAX_PRICE) {
				// Above that, price times multipliers could overflow into a 1-coin price.
				problems.add(where + "price must be 1 to " + MAX_PRICE + (way.equals("gold") ? " gold" : " coins"));
				continue;
			}
			String item = normalizeItem(bits[3]);
			if (item == null) {
				problems.add(where + "'" + bits[3] + "' isn't an item id (a potion in brackets only on potion, splash_potion, lingering_potion or tipped_arrow)");
				continue;
			}
			if (way.equals("buy") && potionOf(item) != null) {
				// The trade screen's payment slot tells items apart, not the potions in them.
				problems.add(where + "a buy line can't name a potion; use a gold line");
				continue;
			}
			if (way.equals("gold")) {
				gold.add(new GoldPrice(category, count, item, price));
			} else {
				trades.add(new Trade(category, way.equals("sell"), count, item, price));
			}
		}
	}

	/** {@code dealer category = Name, Name, ...}. */
	private static void parseDealer(String line, String where, Map<String, Set<String>> dealers, List<String> problems) {
		int eq = line.indexOf('=');
		String category = eq < 0 ? "" : line.substring("dealer".length(), eq).strip().toLowerCase(Locale.ROOT);
		if (eq < 0 || !known(category)) {
			problems.add(where + "expected 'dealer category = Name, Name, ...' with a known category");
			return;
		}
		int added = 0;
		for (String name : line.substring(eq + 1).split(",")) {
			String key = nameKey(name);
			if (!key.isEmpty()) {
				dealers.computeIfAbsent(key, k -> new HashSet<>()).add(category);
				added++;
			}
		}
		if (added == 0) {
			problems.add(where + "a dealer line names nobody");
		}
	}

	private static Map<String, Town> parseTowns(String text, List<String> problems) {
		Map<String, Town> out = new HashMap<>();
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String where = "towns line " + n + ": ";
			int eq = line.indexOf('=');
			if (eq <= 0) {
				problems.add(where + "no '='");
				continue;
			}
			String name = line.substring(0, eq).strip();
			String right = line.substring(eq + 1);
			int semi = right.indexOf(';');
			String currencyText = semi < 0 ? right : right.substring(0, semi);
			String multiplierText = semi < 0 ? "" : right.substring(semi + 1);
			String bad = null;
			Set<String> currencies = new LinkedHashSet<>();
			for (String word : currencyText.strip().split("\\s+")) {
				if (word.isEmpty()) {
					continue;
				}
				List<String> items = Currency.itemsFor(word);
				if (items == null) {
					bad = "unknown currency '" + word + "' (copper, gold, emerald or diamond)";
					break;
				}
				currencies.addAll(items);
			}
			if (bad == null && currencies.isEmpty()) {
				bad = "needs at least one currency";
			}
			Map<String, Double> multipliers = new HashMap<>();
			if (bad == null) {
				for (String part : multiplierText.split(",")) {
					String p = part.strip();
					if (p.isEmpty()) {
						continue;
					}
					String[] bits = p.split("\\s+");
					String category = bits[0].toLowerCase(Locale.ROOT);
					if (bits.length != 2 || !CATEGORIES.contains(category)) {
						bad = "expected 'category multiplier', not '" + p + "'";
						break;
					}
					double m;
					try {
						m = Double.parseDouble(bits[1]);
					} catch (NumberFormatException e) {
						bad = "bad multiplier in '" + p + "'";
						break;
					}
					if (!(m > 0.0 && m <= 10.0)) {
						bad = "multiplier must be above 0 and at most 10 in '" + p + "'";
						break;
					}
					multipliers.put(category, m);
				}
			}
			if (bad == null && name.isEmpty()) {
				bad = "no town name";
			}
			if (bad == null && out.containsKey(townKey(name))) {
				bad = "town '" + name + "' is listed twice";
			}
			if (bad != null) {
				problems.add(where + bad);
				continue;
			}
			out.put(townKey(name), new Town(name, Set.copyOf(currencies), Map.copyOf(multipliers)));
		}
		return out;
	}

	/** The town's line, ignoring case, or the {@code *} line. */
	public Town town(String name) {
		Town t = this.towns.get(townKey(name));
		if (t == null) {
			t = this.towns.get("*");
		}
		return t != null ? t : FALLBACK;
	}

	/** Categories a merchant with this Proto.VENDOR_* mask deals in. */
	public static Set<String> categoriesFor(int vendorMask) {
		Set<String> out = new HashSet<>();
		for (int i = 0; i < CATEGORIES.size(); i++) {
			boolean any = (vendorMask & Proto.VENDOR_ANY) != 0 && CATEGORY_BITS[i] != Proto.VENDOR_STOLEN;
			if (any || (vendorMask & CATEGORY_BITS[i]) != 0) {
				out.add(CATEGORIES.get(i));
			}
		}
		return out;
	}

	/** The dealer categories a {@code dealer} line gives the merchant with this Skyrim name (ignoring case), or none. */
	public Set<String> dealerCategories(String name) {
		return this.dealers.getOrDefault(nameKey(name), Set.of());
	}

	/** Categories a merchant deals in: its Proto.VENDOR_* mask's, and those dealer lines give its name. */
	public Set<String> categoriesFor(int vendorMask, String name) {
		Set<String> out = categoriesFor(vendorMask);
		out.addAll(this.dealerCategories(name));
		return out;
	}

	/** {@link #offersFor(int, String, String, long, Demand)} for a merchant no dealer line names. */
	public List<Offer> offersFor(int vendorMask, String town, long seed, Demand demand) {
		return this.offersFor(vendorMask, "", town, seed, demand);
	}

	/**
	 * Up to {@link #MAX_OFFERS} offers for a merchant: every trade of the dealer categories its {@code name} has (a
	 * Khajiit caravan's Skooma), then the trades of its shop's categories, picked by {@code seed} (the same seed always
	 * picks the same trades); priced for the town and current demand ({@code demand} may be null), sells first.
	 */
	public List<Offer> offersFor(int vendorMask, String name, String town, long seed, Demand demand) {
		Set<String> dealt = this.dealerCategories(name);
		Set<String> categories = categoriesFor(vendorMask);
		List<Trade> picked = new ArrayList<>();
		List<Trade> pool = new ArrayList<>();
		for (Trade t : this.trades) {
			if (dealt.contains(t.category())) {
				picked.add(t);
			} else if (categories.contains(t.category())) {
				pool.add(t);
			}
		}
		Collections.shuffle(pool, new Random(seed));
		picked.addAll(pool);
		Town where = this.town(town);
		List<Offer> sells = new ArrayList<>();
		List<Offer> buys = new ArrayList<>();
		for (Trade t : picked.subList(0, Math.min(MAX_OFFERS, picked.size()))) {
			int price = Math.max(1, (int) Math.round(t.price() * where.multiplier(t.category())));
			if (demand != null) {
				price = demand.apply(price, town, t.item());
			}
			(t.sell() ? sells : buys).add(new Offer(t.sell(), t.item(), t.count(), price, t.category()));
		}
		List<Offer> out = new ArrayList<>(sells);
		out.addAll(buys);
		return out;
	}

	/**
	 * What a merchant pays in Skyrim gold for {@code item} (as {@link #normalizeItem} writes it): the first gold line for it
	 * among the categories the merchant deals in, or null if it doesn't buy it. A potion the merchant has no line for
	 * goes by a line for its plain item ({@code minecraft:potion}), if there is one.
	 */
	public GoldPrice goldPriceFor(int vendorMask, String name, String item) {
		Set<String> categories = this.categoriesFor(vendorMask, name);
		GoldPrice plain = null;
		for (GoldPrice g : this.gold) {
			if (!categories.contains(g.category())) {
				continue;
			}
			if (g.item().equals(item)) {
				return g;
			}
			if (plain == null && g.item().equals(baseItem(item))) {
				plain = g;
			}
		}
		return plain;
	}

	/**
	 * What a merchant in {@code town} pays, lot by lot, for {@code lots} lots of {@code line.count()} items sold one after
	 * another: the line's gold times the town's multiplier for its category, times demand as it falls with each lot sold
	 * ({@code demand} may be null), times {@code share} (what's left of a worn item, 1 for anything whole); at least 1 gold
	 * a lot. At most {@link #MAX_GOLD_BATCHES} lots.
	 */
	public List<Integer> goldPrices(GoldPrice line, String town, Demand demand, int lots, double share) {
		double base = line.gold() * this.town(town).multiplier(line.category()) * Math.max(0.0, Math.min(1.0, share));
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < Math.min(lots, MAX_GOLD_BATCHES); i++) {
			double factor = demand != null ? demand.factorAfter(town, line.item(), i) : 1.0;
			out.add((int) Math.max(1L, Math.round(base * factor)));
		}
		return out;
	}

	/** How many of {@code prices}, the first ones, a merchant holding {@code purse} gold can pay for. */
	public static int affordable(List<Integer> prices, int purse) {
		int left = Math.max(0, purse);
		int n = 0;
		for (int price : prices) {
			if (price > left) {
				break;
			}
			left -= price;
			n++;
		}
		return n;
	}

	/**
	 * {@code o}'s price for {@code count} of its items instead of {@code o.count()} (one trade moves
	 * at most a stack): rounded up when the merchant sells, down when it buys, at least 1 coin.
	 */
	public static int priceFor(Offer o, int count) {
		if (count >= o.count()) {
			return o.price();
		}
		long part = (long) o.price() * Math.max(0, count);
		long price = o.sell() ? (part + o.count() - 1) / o.count() : part / o.count();
		return (int) Math.max(1, price);
	}

	/** Currency items an offer may be paid in: the town's, minus the traded item itself. */
	public static Set<String> currenciesFor(Town town, String item) {
		Set<String> out = new HashSet<>(town.currencies());
		out.remove(item);
		return out;
	}
}
