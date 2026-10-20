package dev.skycraft.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TradeRulesTest {
	private static TradeRules defaults() {
		List<String> problems = new ArrayList<>();
		TradeRules rules = TradeRules.defaults(problems);
		assertTrue(problems.isEmpty(), problems.toString());
		return rules;
	}

	private static Map<String, Integer> basePrices(TradeRules rules, String category) {
		Map<String, Integer> out = new HashMap<>();
		for (TradeRules.Trade t : rules.trades()) {
			if (t.category().equals(category)) {
				out.put((t.sell() ? "sell " : "buy ") + t.count() + " " + t.item(), t.price());
			}
		}
		return out;
	}

	@Test
	void defaultsParseCleanly() {
		TradeRules rules = defaults();
		assertTrue(rules.trades().size() >= 50, "trades " + rules.trades().size());
		assertEquals(18, rules.towns().size());
		Set<String> categories = new HashSet<>();
		for (TradeRules.Trade t : rules.trades()) {
			categories.add(t.category());
		}
		Set<String> all = new HashSet<>(TradeRules.CATEGORIES);
		all.addAll(TradeRules.DEALER_CATEGORIES);
		assertEquals(all, categories);
		Set<String> goldCategories = new HashSet<>();
		for (TradeRules.GoldPrice g : rules.gold()) {
			goldCategories.add(g.category());
		}
		assertEquals(all, goldCategories, "every kind of shop buys something for gold");
	}

	@Test
	void defaultItemsAreVanillaOrSkyCraftIds() {
		for (TradeRules.Trade t : defaults().trades()) {
			assertTrue(t.item().matches("(minecraft|skycraft):[a-z_]+(\\[(minecraft|skycraft):[a-z_]+])?"), t.item());
			assertTrue(t.count() >= 1 && t.count() <= 64, t.toString());
			assertTrue(t.price() >= 1, t.toString());
		}
		for (TradeRules.GoldPrice g : defaults().gold()) {
			assertTrue(g.item().matches("(minecraft|skycraft):[a-z_]+(\\[(minecraft|skycraft):[a-z_]+])?"), g.item());
			assertTrue(g.count() >= 1 && g.count() <= 64 && g.gold() >= 1, g.toString());
		}
	}

	@Test
	void anUneditedEarlierTradesFileGetsTheNewDefaults() {
		// Config.read swaps a trades file that still holds one of these for DEFAULT_TRADES (the gold and Skooma lines).
		assertEquals(2, TradeRules.OLD_TRADES.size());
		for (String old : TradeRules.OLD_TRADES) {
			assertTrue(old.matches("sha256:[0-9a-f]{64}"), old);
		}
		assertFalse(dev.skycraft.config.Config.untouched(TradeRules.DEFAULT_TRADES, TradeRules.OLD_TRADES), "the current default is listed as old");
		assertFalse(dev.skycraft.config.Config.untouched(TradeRules.DEFAULT_TRADES + "\nfood sell 1 minecraft:bread 1\n", TradeRules.OLD_TRADES));
	}

	@Test
	void merchantsBuyForGoldOnlyWhatTheirShopDealsIn() {
		TradeRules rules = defaults();
		assertEquals(3, rules.goldPriceFor(Proto.VENDOR_INGOT, "Ghorza gra-Bagol", "minecraft:iron_ingot").gold());
		assertEquals("ingot", rules.goldPriceFor(Proto.VENDOR_ANY, "Belethor", "minecraft:iron_ingot").category());
		assertEquals(150, rules.goldPriceFor(Proto.VENDOR_JEWELRY, "Madesi", "minecraft:diamond").gold());
		assertEquals(3, rules.goldPriceFor(Proto.VENDOR_HIDE, "", "minecraft:leather").gold());
		assertEquals(3, rules.goldPriceFor(Proto.VENDOR_FOOD, "", "minecraft:cooked_beef").gold());
		// A grocer doesn't buy ingots, a smith doesn't buy bread, nobody buys dirt.
		assertNull(rules.goldPriceFor(Proto.VENDOR_FOOD, "", "minecraft:iron_ingot"));
		assertNull(rules.goldPriceFor(Proto.VENDOR_WEAPON | Proto.VENDOR_ARMOR | Proto.VENDOR_INGOT, "", "minecraft:bread"));
		assertNull(rules.goldPriceFor(Proto.VENDOR_ANY, "", "minecraft:dirt"));
		// General stores don't fence; fences do.
		assertNull(rules.goldPriceFor(Proto.VENDOR_ANY, "Belethor", "minecraft:name_tag"));
		assertEquals("stolen", rules.goldPriceFor(Proto.VENDOR_STOLEN, "Tonilia", "minecraft:name_tag").category());
		assertNull(rules.goldPriceFor(0, "Hulda", "minecraft:iron_ingot"));
		// Moon sugar goes only to the caravans.
		assertNull(rules.goldPriceFor(Proto.VENDOR_ANY, "Belethor", "skycraft:moon_sugar"));
		assertEquals(15, rules.goldPriceFor(Proto.VENDOR_ANY, "Ri'saad", "skycraft:moon_sugar").gold());
	}

	@Test
	void aPotionSellsByItsOwnLineOrThePlainItemsLine() {
		TradeRules rules = TradeRules.parse("""
			potion gold 1 minecraft:potion[healing] 8
			potion gold 1 minecraft:potion 2
			""", "* = copper\n", new ArrayList<>());
		assertEquals(8, rules.goldPriceFor(Proto.VENDOR_POTION, "", "minecraft:potion[minecraft:healing]").gold());
		assertEquals(2, rules.goldPriceFor(Proto.VENDOR_POTION, "", "minecraft:potion[minecraft:swiftness]").gold());
		assertEquals(2, rules.goldPriceFor(Proto.VENDOR_POTION, "", "minecraft:potion").gold());
		assertNull(rules.goldPriceFor(Proto.VENDOR_POTION, "", "minecraft:splash_potion[minecraft:healing]"));
	}

	@Test
	void goldPricesFallWithEveryLotAndStayWithinThePurse() {
		TradeRules rules = defaults();
		TradeRules.GoldPrice gem = rules.goldPriceFor(Proto.VENDOR_JEWELRY, "", "minecraft:diamond");
		// Markarth pays 1.5 times for jewelry; each diamond sold lowers the next one's price 5%.
		assertEquals(List.of(225, 214, 203, 191), rules.goldPrices(gem, "Markarth", new Demand(), 4, 1.0));
		assertEquals(List.of(150, 150), rules.goldPrices(gem, "Helgen", null, 2, 1.0));
		// Half a worn sword's durability left: half the price.
		TradeRules.GoldPrice sword = rules.goldPriceFor(Proto.VENDOR_WEAPON, "", "minecraft:diamond_sword");
		assertEquals(List.of(30), rules.goldPrices(sword, "Helgen", null, 1, 0.5));
		// Never under 1 gold a lot, however low demand falls; and at most a stack of lots.
		TradeRules.GoldPrice paper = rules.goldPriceFor(Proto.VENDOR_BOOK, "", "minecraft:paper");
		Demand glut = new Demand();
		for (int i = 0; i < 30; i++) {
			glut.sold("Helgen", "minecraft:paper");
		}
		assertTrue(rules.goldPrices(paper, "Helgen", glut, 5, 0.01).stream().allMatch(p -> p == 1));
		assertEquals(TradeRules.MAX_GOLD_BATCHES, rules.goldPrices(paper, "Helgen", null, 1000, 1.0).size());
		// A merchant pays for the first lots its gold covers.
		assertEquals(2, TradeRules.affordable(List.of(225, 214, 203), 500));
		assertEquals(3, TradeRules.affordable(List.of(225, 214, 203), 642));
		assertEquals(0, TradeRules.affordable(List.of(225), 224));
		assertEquals(0, TradeRules.affordable(List.of(5), -1));
	}

	@Test
	void khajiitCaravansDealInSkooma() {
		TradeRules rules = defaults();
		for (String khajiit : List.of("Ri'saad", "ahkari", "MA'DRAN", "Kharjo", "Zaynabi", "Atahbah", "Ma'jhad", "Khayla")) {
			assertEquals(Set.of("skooma"), rules.dealerCategories(khajiit), khajiit);
		}
		assertTrue(rules.dealerCategories("Belethor").isEmpty());
		for (long seed = 0; seed < 50; seed++) {
			// A caravan (Skyrim makes Ri'saad a merchant of everything) always has its Skooma and moon sugar on show.
			List<TradeRules.Offer> risaad = rules.offersFor(Proto.VENDOR_ANY, "Ri'saad", "Whiterun", seed, null);
			assertEquals(TradeRules.MAX_OFFERS, risaad.size());
			Set<String> items = new HashSet<>();
			risaad.forEach(o -> items.add(o.item()));
			assertTrue(items.containsAll(Set.of("minecraft:potion[skycraft:skooma]", "minecraft:potion[skycraft:redwater_skooma]", "skycraft:moon_sugar")),
				items.toString());
			// Nobody else does.
			for (TradeRules.Offer o : rules.offersFor(Proto.VENDOR_ANY | Proto.VENDOR_STOLEN, "Belethor", "Whiterun", seed, null)) {
				assertNotEquals("skooma", o.category());
			}
		}
		// A dealer Skyrim doesn't make a merchant: its Skooma alone.
		List<TradeRules.Offer> kharjo = rules.offersFor(TradeRules.DEALER_ONLY, "Kharjo", "Dawnstar", 1, null);
		assertEquals(3, kharjo.size());
		kharjo.forEach(o -> assertEquals("skooma", o.category()));
		assertTrue(TradeRules.categoriesFor(TradeRules.DEALER_ONLY).isEmpty());
		assertTrue(rules.offersFor(TradeRules.DEALER_ONLY, "Belethor", "Whiterun", 1, null).isEmpty());
	}

	@Test
	void itemsAndPotionsAreNamespaced() {
		assertEquals("minecraft:bread", TradeRules.normalizeItem("Bread"));
		assertEquals("minecraft:potion[minecraft:healing]", TradeRules.normalizeItem("potion[healing]"));
		assertEquals("minecraft:potion[skycraft:skooma]", TradeRules.normalizeItem("minecraft:potion[skycraft:skooma]"));
		assertNull(TradeRules.normalizeItem("minecraft:bread[healing]"));
		assertNull(TradeRules.normalizeItem("minecraft:potion[healing"));
		assertNull(TradeRules.normalizeItem("minecraft:potion[]"));
		assertNull(TradeRules.normalizeItem("not an:id"));
		assertEquals("minecraft:potion", TradeRules.baseItem("minecraft:potion[skycraft:skooma]"));
		assertEquals("skycraft:skooma", TradeRules.potionOf("minecraft:potion[skycraft:skooma]"));
		assertNull(TradeRules.potionOf("minecraft:bread"));
	}

	@Test
	void reportsBadGoldAndDealerLines() {
		List<String> problems = new ArrayList<>();
		TradeRules rules = TradeRules.parse("""
			ingot gold 1 minecraft:iron_ingot 3
			ingot gold 1 minecraft:iron_ingot 0
			ingot gold 1 minecraft:bread[healing] 3
			potion buy 1 minecraft:potion[healing] 3
			skooma sell 1 skycraft:moon_sugar 50
			dealer skooma = Ri'saad, ,Ahkari
			dealer smuggling = Ri'saad
			dealer skooma =
			dealer skooma Ri'saad
			""", "* = copper\n", problems);
		assertEquals(6, problems.size(), problems.toString());
		assertEquals(1, rules.gold().size());
		assertEquals(1, rules.trades().size());
		assertEquals(Set.of("skooma"), rules.dealerCategories("ahkari"));
	}

	@Test
	void defaultBuyPricesStayBelowSellPrices() {
		// Per item: what merchants pay is less than what they charge, so a merchant can't be farmed.
		Map<String, Double> sellEach = new HashMap<>();
		for (TradeRules.Trade t : defaults().trades()) {
			if (t.sell()) {
				sellEach.merge(t.item(), (double) t.price() / t.count(), Math::min);
			}
		}
		for (TradeRules.Trade t : defaults().trades()) {
			Double sell = sellEach.get(t.item());
			if (!t.sell() && sell != null) {
				assertTrue((double) t.price() / t.count() < sell, t.toString());
			}
		}
	}

	@Test
	void defaultsNeverBuyCurrencyOrSellItCheap() {
		TradeRules rules = defaults();
		for (TradeRules.Trade t : rules.trades()) {
			if (!t.sell()) {
				assertEquals(0, Currency.value(t.item()), t.toString());
			} else if (Currency.value(t.item()) > 0) {
				// Nobody sells currency to merchants, so its price never drops below the cheapest town's.
				double cheapest = 1.0;
				for (TradeRules.Town town : rules.towns().values()) {
					cheapest = Math.min(cheapest, town.multiplier(t.category()));
				}
				assertTrue(t.price() * cheapest / t.count() > Currency.value(t.item()), t.toString());
			}
		}
	}

	@Test
	void currencyIsOnlyOfferedWhereTheTownCanPayItsFullPrice() {
		// SkyTrade skips a sell offer whose cost can't cover its price. Without that, Rorikstead
		// (copper only) sold a 200-coin diamond for 64 copper ingots, the most two stacks allow.
		TradeRules rules = defaults();
		int offered = 0;
		for (TradeRules.Town town : rules.towns().values()) {
			for (TradeRules.Trade t : rules.trades()) {
				if (!t.sell() || Currency.value(t.item()) == 0) {
					continue;
				}
				int price = Math.max(1, (int) Math.round(t.price() * town.multiplier(t.category())));
				List<Currency.Pay> pay = Currency.cost(price, TradeRules.currenciesFor(town, t.item()));
				if (Currency.total(pay) >= price) {
					offered++;
					assertTrue((double) Currency.total(pay) / t.count() > Currency.value(t.item()), town.name() + " " + t + " " + pay);
				}
			}
		}
		assertTrue(offered > 0);
		TradeRules.Town rorikstead = rules.town("Rorikstead");
		assertTrue(Currency.total(Currency.cost(200, TradeRules.currenciesFor(rorikstead, Currency.DIAMOND))) < 200);
	}

	@Test
	void categoriesFromMask() {
		Set<String> any = TradeRules.categoriesFor(Proto.VENDOR_ANY);
		assertEquals(11, any.size());
		assertFalse(any.contains("stolen"));
		assertEquals(12, TradeRules.categoriesFor(Proto.VENDOR_ANY | Proto.VENDOR_STOLEN).size());
		assertEquals(Set.of("stolen"), TradeRules.categoriesFor(Proto.VENDOR_STOLEN));
		assertEquals(Set.of("weapon", "armor"), TradeRules.categoriesFor(Proto.VENDOR_WEAPON | Proto.VENDOR_ARMOR));
		assertEquals(Set.of("food"), TradeRules.categoriesFor(Proto.VENDOR_FOOD));
		assertTrue(TradeRules.categoriesFor(0).isEmpty());
	}

	@Test
	void offersOnlyFromMaskCategories() {
		TradeRules rules = defaults();
		for (long seed = 0; seed < 50; seed++) {
			for (TradeRules.Offer o : rules.offersFor(Proto.VENDOR_WEAPON | Proto.VENDOR_ARMOR, "Whiterun", seed, null)) {
				assertTrue(o.category().equals("weapon") || o.category().equals("armor"), o.toString());
			}
			for (TradeRules.Offer o : rules.offersFor(Proto.VENDOR_ANY, "Whiterun", seed, null)) {
				assertNotEquals("stolen", o.category());
			}
		}
		assertTrue(rules.offersFor(0, "Whiterun", 1, null).isEmpty());
	}

	@Test
	void smallShopsShowAllTheirTrades() {
		TradeRules rules = defaults();
		int arrows = basePrices(rules, "arrow").size();
		assertEquals(arrows, rules.offersFor(Proto.VENDOR_ARROW, "Whiterun", 7, null).size());
	}

	@Test
	void atMostTwelveOffers() {
		TradeRules rules = defaults();
		for (long seed = 0; seed < 50; seed++) {
			assertEquals(TradeRules.MAX_OFFERS, rules.offersFor(Proto.VENDOR_ANY | Proto.VENDOR_STOLEN, "Riften", seed, null).size());
		}
	}

	@Test
	void sellsBeforeBuys() {
		TradeRules rules = defaults();
		for (long seed = 0; seed < 50; seed++) {
			boolean seenBuy = false;
			for (TradeRules.Offer o : rules.offersFor(Proto.VENDOR_ANY, "Solitude", seed, null)) {
				if (!o.sell()) {
					seenBuy = true;
				} else {
					assertFalse(seenBuy, "a sell after a buy for seed " + seed);
				}
			}
		}
	}

	@Test
	void deterministicBySeed() {
		TradeRules rules = defaults();
		assertEquals(rules.offersFor(Proto.VENDOR_ANY, "Whiterun", 0x0001A66B, null), rules.offersFor(Proto.VENDOR_ANY, "Whiterun", 0x0001A66B, null));
		boolean differs = false;
		List<TradeRules.Offer> first = rules.offersFor(Proto.VENDOR_ANY, "Whiterun", 1, null);
		for (long seed = 2; seed < 20 && !differs; seed++) {
			differs = !first.equals(rules.offersFor(Proto.VENDOR_ANY, "Whiterun", seed, null));
		}
		assertTrue(differs, "every seed picked the same offers");
	}

	@Test
	void townMultipliers() {
		TradeRules rules = defaults();
		// Whiterun: food 0.7, so cheap to buy and pays less
		Map<String, Integer> food = basePrices(rules, "food");
		for (TradeRules.Offer o : rules.offersFor(Proto.VENDOR_FOOD, "Whiterun", 3, null)) {
			int base = food.get((o.sell() ? "sell " : "buy ") + o.count() + " " + o.item());
			assertEquals(Math.max(1, Math.round(base * 0.7)), o.price(), o.toString());
		}
		assertEquals(0.7, rules.town("Whiterun").multiplier("food"), 1e-9);
		assertEquals(1.0, rules.town("Whiterun").multiplier("book"), 1e-9);
		assertEquals(1.5, rules.town("Markarth").multiplier("jewelry"), 1e-9);
		assertEquals(0.6, rules.town("riften").multiplier("stolen"), 1e-9);
		assertEquals(0.6, rules.town("Winterhold").multiplier("book"), 1e-9);
	}

	@Test
	void townCurrencies() {
		TradeRules rules = defaults();
		assertEquals(Set.of(Currency.COPPER), rules.town("Rorikstead").currencies());
		assertEquals(Set.of(Currency.COPPER, Currency.NUGGET, Currency.GOLD), rules.town("Whiterun").currencies());
		assertTrue(rules.town("Solitude").currencies().contains(Currency.DIAMOND));
		assertTrue(rules.town("Markarth").currencies().contains(Currency.EMERALD));
		assertEquals(Set.of(Currency.NUGGET, Currency.GOLD, Currency.EMERALD), TradeRules.currenciesFor(rules.town("Solitude"), Currency.DIAMOND));
	}

	@Test
	void townsMatchIgnoringCaseAndFallBack() {
		TradeRules rules = defaults();
		assertEquals("Shor's Stone", rules.town("SHOR'S STONE").name());
		assertEquals("Dragon Bridge", rules.town("  dragon bridge ").name());
		assertEquals("*", rules.town("Helgen").name());
		assertEquals("*", rules.town("").name());
		assertEquals("*", rules.town(null).name());
		// Unknown towns price at the base price.
		Map<String, Integer> food = basePrices(rules, "food");
		for (TradeRules.Offer o : rules.offersFor(Proto.VENDOR_FOOD, "Helgen", 3, null)) {
			assertEquals((int) food.get((o.sell() ? "sell " : "buy ") + o.count() + " " + o.item()), o.price(), o.toString());
		}
	}

	@Test
	void noStarLineUsesCopperAndGold() {
		List<String> problems = new ArrayList<>();
		TradeRules rules = TradeRules.parse("food sell 6 bread 6\n", "Whiterun = copper\n", problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals("minecraft:bread", rules.trades().getFirst().item());
		assertEquals(Set.of(Currency.COPPER, Currency.NUGGET, Currency.GOLD), rules.town("Riften").currencies());
	}

	@Test
	void demandMovesPrices() {
		TradeRules rules = TradeRules.parse("food sell 6 minecraft:bread 20\nfood buy 20 minecraft:wheat 10\n", "* = copper\n", new ArrayList<>());
		Demand demand = new Demand();
		for (int i = 0; i < 4; i++) {
			demand.bought("Whiterun", "minecraft:bread");
			demand.sold("Whiterun", "minecraft:wheat");
		}
		List<TradeRules.Offer> offers = rules.offersFor(Proto.VENDOR_FOOD, "Whiterun", 1, demand);
		assertEquals(2, offers.size());
		assertEquals(24, offers.get(0).price()); // bread 20 * 1.2
		assertEquals(8, offers.get(1).price()); // wheat 10 * 0.8
		// Another town's prices are untouched.
		assertEquals(20, rules.offersFor(Proto.VENDOR_FOOD, "Riften", 1, demand).getFirst().price());
	}

	@Test
	void priceShrinksWithACappedCount() {
		// 32 ender pearls can't fit one trade (they stack to 16): half the pearls, half the price.
		TradeRules.Offer sellPearls = new TradeRules.Offer(true, "minecraft:ender_pearl", 32, 10, "potion");
		TradeRules.Offer buyPearls = new TradeRules.Offer(false, "minecraft:ender_pearl", 32, 10, "potion");
		assertEquals(5, TradeRules.priceFor(sellPearls, 16));
		assertEquals(5, TradeRules.priceFor(buyPearls, 16));
		assertEquals(10, TradeRules.priceFor(sellPearls, 32));
		// Rounding favors the merchant: up when it sells, down when it buys, never below 1.
		TradeRules.Offer sellSwords = new TradeRules.Offer(true, "minecraft:iron_sword", 3, 10, "weapon");
		TradeRules.Offer buySwords = new TradeRules.Offer(false, "minecraft:iron_sword", 3, 10, "weapon");
		assertEquals(4, TradeRules.priceFor(sellSwords, 1));
		assertEquals(3, TradeRules.priceFor(buySwords, 1));
		assertEquals(1, TradeRules.priceFor(new TradeRules.Offer(false, "minecraft:ender_pearl", 32, 1, "potion"), 16));
	}

	@Test
	void absurdPricesAreRejected() {
		List<String> problems = new ArrayList<>();
		// 2e9 coins times a town multiplier used to overflow into a 1-coin price.
		TradeRules rules = TradeRules.parse("weapon sell 1 minecraft:iron_sword 2000000000\nweapon sell 1 minecraft:bow " + TradeRules.MAX_PRICE + "\n",
			"* = copper ; weapon 1.4\n", problems);
		assertEquals(1, problems.size(), problems.toString());
		assertEquals(1, rules.trades().size());
		assertEquals(140000, rules.offersFor(Proto.VENDOR_WEAPON, "Whiterun", 1, new Demand()).getFirst().price());
	}

	@Test
	void reportsBadLinesOnceAndKeepsGoodOnes() {
		List<String> problems = new ArrayList<>();
		TradeRules rules = TradeRules.parse("""
			# comment
			weapon sell 1 minecraft:iron_sword 40
			weapon sell 1 minecraft:bow
			gems sell 1 minecraft:diamond 100
			food trade 1 minecraft:bread 5
			food sell x minecraft:bread 5
			food sell 65 minecraft:bread 5
			food sell 1 minecraft:bread 0
			food buy 4 minecraft:wheat 2
			""", """
			Whiterun = copper gold ; food 0.7
			Riften copper
			Solitude = septims
			Markarth = gold ; jewels 1.5
			Morthal = copper ; food -1
			Dawnstar = ; food 1.2
			whiterun = copper
			* = copper ; food 1.1, tool 0.9
			""", problems);
		assertEquals(2, rules.trades().size());
		assertEquals(12, problems.size(), problems.toString());
		assertEquals(2, rules.towns().size());
		assertEquals(1.1, rules.town("Riften").multiplier("food"), 1e-9);
		assertEquals(0.7, rules.town("Whiterun").multiplier("food"), 1e-9);
	}

	@Test
	void obsidianForPortalsIsSoldDearAndNeverBought() {
		TradeRules rules = defaults();
		Set<String> sellers = new HashSet<>();
		for (TradeRules.Trade t : rules.trades()) {
			if (!t.item().equals("minecraft:obsidian")) {
				continue;
			}
			assertTrue(t.sell(), "merchants never buy obsidian: " + t);
			sellers.add(t.category());
			// Ten blocks make a frame: a portal costs hundreds of coins even where it's cheapest.
			for (TradeRules.Town town : rules.towns().values()) {
				assertTrue(t.price() * town.multiplier(t.category()) / t.count() >= 20.0, town.name() + " " + t);
			}
		}
		assertEquals(Set.of("ingot", "tool"), sellers);
		// Markarth's smiths (ingots 0.7) sell it for less than a general store elsewhere.
		Map<String, Integer> ingot = basePrices(rules, "ingot");
		double markarth = ingot.get("sell 2 minecraft:obsidian") * rules.town("Markarth").multiplier("ingot") / 2.0;
		assertTrue(markarth < basePrices(rules, "tool").get("sell 1 minecraft:obsidian"), "Markarth " + markarth);
	}
}
