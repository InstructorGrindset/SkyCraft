#pragma once

// How filling a Skyrim food is in Minecraft's terms (Skyrim food fills hunger). Pure, no game: it reads only the food's
// name and whether Skyrim marks it raw (the VendorItemFoodRaw keyword), so it's unit tested on its own
// (tests/FoodValuesTest.cpp). Skyrim decides here and sends the result (proto::kInSkyrimFood); Minecraft adds it to its
// player's hunger as it would one of its own foods.
//
// The numbers are Minecraft's own foods': food points (nutrition) and the saturation modifier (saturation gained is
// points x modifier x 2). An apple is 4 at 0.3, bread 5 at 0.6, cooked beef 8 at 0.8, cooked chicken 6 at 0.6, raw beef 3
// at 0.3, raw salmon 2 at 0.1, rabbit stew 10 at 0.6, mushroom stew 6 at 0.6, pumpkin pie 8 at 0.3, a cookie 2 at 0.1, a
// carrot 3 at 0.6, a potato 1 at 0.3, a baked potato 5 at 0.6, honey 6 at 0.1. A Skyrim food gets the values of the
// Minecraft food most like it, by the words of its English name; a name no word matches is a modest 3 at 0.3.

#include "skycraft_protocol.h"

#include <cstddef>
#include <cstdint>
#include <initializer_list>
#include <string_view>

namespace skycraft::FoodValues
{
	struct Value
	{
		std::uint32_t nutrition;   // Minecraft food points (half a drumstick each)
		float         saturation;  // Minecraft's saturation modifier
		std::uint32_t kind;        // proto::FoodKind
	};

	// Minecraft's saturation modifiers (FoodConstants): poor, low, normal, good.
	inline constexpr float kPoor = 0.1f;
	inline constexpr float kLow = 0.3f;
	inline constexpr float kNormal = 0.6f;
	inline constexpr float kGood = 0.8f;

	// ASCII, ignoring case.
	constexpr char Lower(char a_c) { return a_c >= 'A' && a_c <= 'Z' ? static_cast<char>(a_c - 'A' + 'a') : a_c; }

	// Letters, digits and apostrophes make up a word; anything else (spaces, hyphens) splits words.
	constexpr bool WordChar(char a_c)
	{
		const char c = Lower(a_c);
		return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '\'';
	}

	// a_word (lower case) is a whole word of a_name, ignoring case: "ale" is in "Nord Ale", not in "Alto Wine".
	constexpr bool HasWord(std::string_view a_name, std::string_view a_word)
	{
		std::size_t at = 0;
		while (at < a_name.size()) {
			while (at < a_name.size() && !WordChar(a_name[at])) {
				++at;
			}
			std::size_t end = at;
			while (end < a_name.size() && WordChar(a_name[end])) {
				++end;
			}
			if (end - at == a_word.size()) {
				bool same = true;
				for (std::size_t i = 0; i < a_word.size() && same; ++i) {
					same = Lower(a_name[at + i]) == a_word[i];
				}
				if (same) {
					return true;
				}
			}
			at = end;
		}
		return false;
	}

	constexpr bool HasAny(std::string_view a_name, std::initializer_list<std::string_view> a_words)
	{
		for (const auto word : a_words) {
			if (HasWord(a_name, word)) {
				return true;
			}
		}
		return false;
	}

	// Words that make meat or fish cooked: "Grilled Chicken Breast", "Horse Haunch", "Horker Loaf", "Salmon Steak".
	constexpr bool Cooked(std::string_view a_name)
	{
		return !HasWord(a_name, "raw") && HasAny(a_name, { "cooked", "grilled", "roast", "roasted", "steak", "chop", "chops", "haunch", "loaf",
			                                                   "steamed", "baked", "fried", "smoked", "seared", "broiled", "spiced", "jerky",
			                                                   "sausage", "sausages", "ham" });
	}

	constexpr bool MeatWord(std::string_view a_name)
	{
		return HasAny(a_name, { "beef", "venison", "horker", "mammoth", "goat", "horse", "dog", "boar", "mutton", "pork", "bear", "elk", "deer",
			                      "snout", "meat", "steak", "chop", "haunch", "ham", "sausage", "sausages", "jerky", "chicken", "pheasant", "rabbit",
			                      "hare", "hopper", "fowl", "duck", "quail" });
	}

	// Birds, rabbits and ash hoppers: Minecraft's chicken and rabbit, smaller than its beef and pork.
	constexpr bool SmallMeat(std::string_view a_name)
	{
		return HasAny(a_name, { "chicken", "pheasant", "rabbit", "hare", "hopper", "fowl", "duck", "quail" });
	}

	// What a food named a_name (English, as Skyrim names it) is worth to Minecraft's hunger; a_raw: Skyrim's
	// VendorItemFoodRaw keyword is on it. First match wins, so a dish ("Apple Pie", "Chicken Dumpling", "Beef Stew")
	// is found before what's in it.
	constexpr Value FromName(std::string_view a_name, bool a_raw)
	{
		// Drinks: ale, mead and wine fill little (Minecraft has no drink but honey); milk a little more.
		if (HasAny(a_name, { "milk" })) {
			return { 3, kLow, proto::kFoodDrink };
		}
		if (HasAny(a_name, { "ale", "mead", "wine", "brandy", "sujamma", "shein", "flin", "matze", "mazte", "rum", "beer", "cider", "bloodwine",
				"briar", "honningbrew", "tea", "juice" })) {
			return { 2, kPoor, proto::kFoodDrink };
		}
		// Stews and soups: Minecraft's rabbit stew and mushroom stew.
		if (HasAny(a_name, { "stew", "stews" })) {
			return { MeatWord(a_name) ? 10u : 8u, kNormal, proto::kFoodStew };
		}
		if (HasAny(a_name, { "chowder", "fondue", "potage", "broth" })) {
			return { 8, kNormal, proto::kFoodSoup };
		}
		if (HasAny(a_name, { "soup" })) {
			return { 6, kNormal, proto::kFoodSoup };
		}
		// Cheese: a wheel (or the mammoth cheese bowl) is a meal, a wedge a snack, a slice a bite.
		if (HasAny(a_name, { "cheese" })) {
			if (HasAny(a_name, { "wheel", "bowl" })) {
				return { 6, kNormal, proto::kFoodCheese };
			}
			if (HasAny(a_name, { "wedge" })) {
				return { 3, kNormal, proto::kFoodCheese };
			}
			if (HasAny(a_name, { "sliced", "slice", "slices" })) {
				return { 2, kNormal, proto::kFoodCheese };
			}
			return { 4, kNormal, proto::kFoodCheese };
		}
		// Pies (Minecraft's pumpkin pie) and crostatas, a smaller pie.
		if (HasAny(a_name, { "pie", "pies" })) {
			return { 8, kLow, proto::kFoodPie };
		}
		if (HasAny(a_name, { "crostata", "tart", "tarts" })) {
			return { 6, kLow, proto::kFoodPie };
		}
		// Dumplings and bread.
		if (HasAny(a_name, { "dumpling", "dumplings" })) {
			return { 5, kNormal, proto::kFoodBread };
		}
		// Sweets: Minecraft's cookie and cake slice, a little more for a sweet roll.
		if ((HasWord(a_name, "sweet") && HasWord(a_name, "roll")) ||
			HasAny(a_name, { "sweetroll", "treat", "treats", "taffy", "cake", "cakes", "candy", "cookie", "cookies", "pastry" })) {
			return { 3, kPoor, proto::kFoodSweet };
		}
		if (HasAny(a_name, { "bread", "bun", "buns", "biscuit", "biscuits", "muffin" })) {
			return { 5, kNormal, proto::kFoodBread };
		}
		if (HasAny(a_name, { "butter" })) {
			return { 1, kPoor, proto::kFoodDairy };
		}
		if (HasAny(a_name, { "honey" })) {
			return { 6, kPoor, proto::kFoodDairy };
		}
		// Fish and seafood: Minecraft's salmon, cooked or raw.
		if (HasAny(a_name, { "salmon", "clam", "clams", "mudcrab", "crab", "fish", "trout", "perch", "carp", "cod", "char", "slaughterfish", "eel",
				"shrimp", "lobster" })) {
			return Cooked(a_name) ? Value{ 6, kGood, proto::kFoodCookedFish } : Value{ 2, kPoor, proto::kFoodRawFish };
		}
		// Meat: Minecraft's beef and pork, or its chicken and rabbit for the small ones. Meat without a cooking word
		// ("Venison", "Horse Meat", "Leg of Goat") is raw.
		if (MeatWord(a_name)) {
			const bool small = SmallMeat(a_name);
			if (Cooked(a_name)) {
				return small ? Value{ 6, kNormal, proto::kFoodCookedMeat } : Value{ 8, kGood, proto::kFoodCookedMeat };
			}
			return small ? Value{ 2, kLow, proto::kFoodRawMeat } : Value{ 3, kLow, proto::kFoodRawMeat };
		}
		// Fruit: Minecraft's apple, melon slice and sweet berries.
		if (HasAny(a_name, { "apple", "apples", "gourd", "gourds" })) {
			return { 4, kLow, proto::kFoodFruit };
		}
		if (HasAny(a_name, { "pear", "pears", "melon", "plum", "plums" })) {
			return { 3, kLow, proto::kFoodFruit };
		}
		if (HasAny(a_name, { "berry", "berries", "grape", "grapes" })) {
			return { 2, kPoor, proto::kFoodFruit };
		}
		// Vegetables: cooked ones like Minecraft's baked potato, raw ones like its carrot, potato and beetroot.
		if (HasAny(a_name, { "carrot", "carrots", "potato", "potatoes", "cabbage", "leek", "leeks", "tomato", "tomatoes", "yam", "yams", "corn",
				"garlic", "onion", "onions", "beet", "beets", "lettuce", "pumpkin", "vegetable", "vegetables" })) {
			if (HasAny(a_name, { "baked", "grilled", "roasted", "roast", "fried", "boiled", "steamed", "cooked" })) {
				return { 5, kNormal, proto::kFoodCookedVegetable };
			}
			if (HasAny(a_name, { "carrot", "carrots" })) {
				return { 3, kNormal, proto::kFoodVegetable };
			}
			if (HasAny(a_name, { "potato", "potatoes" })) {
				return { 1, kLow, proto::kFoodVegetable };
			}
			if (HasAny(a_name, { "cabbage" })) {
				return { 2, kNormal, proto::kFoodVegetable };
			}
			return { 2, kLow, proto::kFoodVegetable };
		}
		// Anything else (a mod's food, another language's names): a modest bite, less when Skyrim says it's raw.
		return a_raw ? Value{ 2, kLow, proto::kFoodOther } : Value{ 3, kLow, proto::kFoodOther };
	}

	constexpr std::string_view KindName(std::uint32_t a_kind)
	{
		switch (a_kind) {
		case proto::kFoodDrink:
			return "drink";
		case proto::kFoodStew:
			return "stew";
		case proto::kFoodSoup:
			return "soup";
		case proto::kFoodPie:
			return "pie";
		case proto::kFoodSweet:
			return "sweet";
		case proto::kFoodBread:
			return "bread";
		case proto::kFoodCheese:
			return "cheese";
		case proto::kFoodDairy:
			return "dairy";
		case proto::kFoodCookedMeat:
			return "cooked meat";
		case proto::kFoodRawMeat:
			return "raw meat";
		case proto::kFoodCookedFish:
			return "cooked fish";
		case proto::kFoodRawFish:
			return "raw fish";
		case proto::kFoodFruit:
			return "fruit";
		case proto::kFoodVegetable:
			return "vegetable";
		case proto::kFoodCookedVegetable:
			return "cooked vegetable";
		default:
			return "food";
		}
	}
}
