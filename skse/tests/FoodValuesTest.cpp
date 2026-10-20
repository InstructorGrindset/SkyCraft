// Tests for FoodValues.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src -I protocol skse/tests/FoodValuesTest.cpp -o food_values_test && ./food_values_test
#include "FoodValues.h"

#include <cmath>
#include <cstdio>

namespace
{
	int failures = 0;

	void Check(bool a_ok, const char* a_what, int a_line)
	{
		if (!a_ok) {
			std::printf("FAILED line %d: %s\n", a_line, a_what);
			++failures;
		}
	}
#define CHECK(x) Check((x), #x, __LINE__)

	using namespace skycraft;
	using FoodValues::FromName;

	// The food points, saturation modifier and kind a name gets.
	bool Is(const char* a_name, std::uint32_t a_nutrition, float a_saturation, std::uint32_t a_kind, bool a_raw = false)
	{
		const auto v = FromName(a_name, a_raw);
		const bool ok = v.nutrition == a_nutrition && std::fabs(v.saturation - a_saturation) < 1e-6f && v.kind == a_kind;
		if (!ok) {
			const auto kind = FoodValues::KindName(v.kind);
			std::printf("  %s: got %u, %.2f, %.*s\n", a_name, v.nutrition, static_cast<double>(v.saturation), static_cast<int>(kind.size()), kind.data());
		}
		return ok;
	}

	void Words()
	{
		CHECK(FoodValues::HasWord("Nord Ale", "ale"));
		CHECK(!FoodValues::HasWord("Alto Wine", "ale"));
		CHECK(FoodValues::HasWord("Black-Briar Mead", "briar"));
		CHECK(FoodValues::HasWord("BAKED POTATOES", "potatoes"));
		CHECK(FoodValues::HasWord("Dragon's Breath Mead", "dragon's"));
		CHECK(!FoodValues::HasWord("Steamed Mudcrab Legs", "tea"));
		CHECK(!FoodValues::HasWord("", "ale"));
	}

	// Skyrim.esm's drinks: alcohol fills little, milk a little more.
	void Drinks()
	{
		CHECK(Is("Ale", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Nord Mead", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Honningbrew Mead", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Black-Briar Reserve", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Alto Wine", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Spiced Wine", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Argonian Bloodwine", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Cyrodilic Brandy", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Sujamma", 2, FoodValues::kPoor, proto::kFoodDrink));
		CHECK(Is("Jug of Milk", 3, FoodValues::kLow, proto::kFoodDrink));
	}

	// Stews, soups and dishes, found before what's in them.
	void Dishes()
	{
		CHECK(Is("Beef Stew", 10, FoodValues::kNormal, proto::kFoodStew));
		CHECK(Is("Horker Stew", 10, FoodValues::kNormal, proto::kFoodStew));
		CHECK(Is("Venison Stew", 10, FoodValues::kNormal, proto::kFoodStew));
		CHECK(Is("Horker and Ash Yam Stew", 10, FoodValues::kNormal, proto::kFoodStew));
		CHECK(Is("Apple Cabbage Stew", 8, FoodValues::kNormal, proto::kFoodStew));
		CHECK(Is("Vegetable Soup", 6, FoodValues::kNormal, proto::kFoodSoup));
		CHECK(Is("Tomato Soup", 6, FoodValues::kNormal, proto::kFoodSoup));
		CHECK(Is("Cabbage Potato Soup", 6, FoodValues::kNormal, proto::kFoodSoup));
		CHECK(Is("Clam Chowder", 8, FoodValues::kNormal, proto::kFoodSoup));
		CHECK(Is("Elsweyr Fondue", 8, FoodValues::kNormal, proto::kFoodSoup));
		CHECK(Is("Potage le Magnifique", 8, FoodValues::kNormal, proto::kFoodSoup));
		CHECK(Is("Apple Pie", 8, FoodValues::kLow, proto::kFoodPie));
		CHECK(Is("Snowberry Crostata", 6, FoodValues::kLow, proto::kFoodPie));
		CHECK(Is("Juniper Berry Crostata", 6, FoodValues::kLow, proto::kFoodPie));
		CHECK(Is("Chicken Dumpling", 5, FoodValues::kNormal, proto::kFoodBread));
		CHECK(Is("Apple Dumpling", 5, FoodValues::kNormal, proto::kFoodBread));
	}

	void BreadSweetsCheese()
	{
		CHECK(Is("Bread", 5, FoodValues::kNormal, proto::kFoodBread));
		CHECK(Is("Braided Bread", 5, FoodValues::kNormal, proto::kFoodBread));
		CHECK(Is("Garlic Bread", 5, FoodValues::kNormal, proto::kFoodBread));
		CHECK(Is("Sweet Roll", 3, FoodValues::kPoor, proto::kFoodSweet));
		CHECK(Is("Honey Nut Treat", 3, FoodValues::kPoor, proto::kFoodSweet));
		CHECK(Is("Long Taffy Treat", 3, FoodValues::kPoor, proto::kFoodSweet));
		CHECK(Is("Boiled Creme Treat", 3, FoodValues::kPoor, proto::kFoodSweet));
		CHECK(Is("Eidar Cheese Wheel", 6, FoodValues::kNormal, proto::kFoodCheese));
		CHECK(Is("Goat Cheese Wedge", 3, FoodValues::kNormal, proto::kFoodCheese));
		CHECK(Is("Sliced Eidar Cheese", 2, FoodValues::kNormal, proto::kFoodCheese));
		CHECK(Is("Mammoth Cheese Bowl", 6, FoodValues::kNormal, proto::kFoodCheese));
		CHECK(Is("Butter", 1, FoodValues::kPoor, proto::kFoodDairy));
		CHECK(Is("Honey", 6, FoodValues::kPoor, proto::kFoodDairy));
	}

	// Cooked meat is Minecraft's cooked beef (or chicken for birds and rabbits); meat without a cooking word is raw.
	void Meat()
	{
		CHECK(Is("Cooked Beef", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("Horker Loaf", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("Mammoth Steak", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("Venison Chop", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("Horse Haunch", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("Leg of Goat Roast", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("Cooked Boar Meat", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("Spiced Beef", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("Grilled Chicken Breast", 6, FoodValues::kNormal, proto::kFoodCookedMeat));
		CHECK(Is("Pheasant Roast", 6, FoodValues::kNormal, proto::kFoodCookedMeat));
		CHECK(Is("Rabbit Haunch", 6, FoodValues::kNormal, proto::kFoodCookedMeat));
		CHECK(Is("Raw Beef", 3, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Venison", 3, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Horse Meat", 3, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Horker Meat", 3, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Mammoth Snout", 3, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Leg of Goat", 3, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Dog Meat", 3, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Boar Meat", 3, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Chicken Breast", 2, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Pheasant Breast", 2, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Raw Rabbit Leg", 2, FoodValues::kLow, proto::kFoodRawMeat, true));
		CHECK(Is("Ash Hopper Leg", 2, FoodValues::kLow, proto::kFoodRawMeat, true));
	}

	void Fish()
	{
		CHECK(Is("Salmon Steak", 6, FoodValues::kGood, proto::kFoodCookedFish));
		CHECK(Is("Steamed Mudcrab Legs", 6, FoodValues::kGood, proto::kFoodCookedFish));
		CHECK(Is("Salmon Meat", 2, FoodValues::kPoor, proto::kFoodRawFish, true));
		CHECK(Is("Clam Meat", 2, FoodValues::kPoor, proto::kFoodRawFish, true));
		CHECK(Is("Mudcrab Legs", 2, FoodValues::kPoor, proto::kFoodRawFish, true));
	}

	void FruitAndVegetables()
	{
		CHECK(Is("Red Apple", 4, FoodValues::kLow, proto::kFoodFruit));
		CHECK(Is("Green Apple", 4, FoodValues::kLow, proto::kFoodFruit));
		CHECK(Is("Gourd", 4, FoodValues::kLow, proto::kFoodFruit));
		CHECK(Is("Carrot", 3, FoodValues::kNormal, proto::kFoodVegetable));
		CHECK(Is("Cabbage", 2, FoodValues::kNormal, proto::kFoodVegetable));
		CHECK(Is("Potato", 1, FoodValues::kLow, proto::kFoodVegetable));
		CHECK(Is("Leek", 2, FoodValues::kLow, proto::kFoodVegetable));
		CHECK(Is("Tomato", 2, FoodValues::kLow, proto::kFoodVegetable));
		CHECK(Is("Ash Yam", 2, FoodValues::kLow, proto::kFoodVegetable));
		CHECK(Is("Baked Potatoes", 5, FoodValues::kNormal, proto::kFoodCookedVegetable));
		CHECK(Is("Grilled Leeks", 5, FoodValues::kNormal, proto::kFoodCookedVegetable));
	}

	// A name no word matches: a modest bite, less when Skyrim says it's raw. Case doesn't matter.
	void Unknown()
	{
		CHECK(Is("Velvet LeChance", 3, FoodValues::kLow, proto::kFoodOther));
		CHECK(Is("Pain au fromage", 3, FoodValues::kLow, proto::kFoodOther));
		CHECK(Is("Viande crue", 2, FoodValues::kLow, proto::kFoodOther, true));
		CHECK(Is("", 3, FoodValues::kLow, proto::kFoodOther));
		CHECK(Is("cooked beef", 8, FoodValues::kGood, proto::kFoodCookedMeat));
		CHECK(Is("SWEET ROLL", 3, FoodValues::kPoor, proto::kFoodSweet));
	}

	// Nothing fills more than Minecraft's best food (rabbit stew's 10) or past Minecraft's 20 points.
	void InRange()
	{
		const char* names[] = { "Ale", "Beef Stew", "Apple Pie", "Cooked Beef", "Salmon Steak", "Honey", "Eidar Cheese Wheel", "Potato", "x" };
		for (const char* n : names) {
			for (bool raw : { false, true }) {
				const auto v = FromName(n, raw);
				CHECK(v.nutrition >= 1 && v.nutrition <= 10);
				CHECK(v.saturation > 0.0f && v.saturation <= 1.2f);
			}
		}
		static_assert(FromName("Cooked Beef", false).nutrition == 8, "constexpr");
	}
}

int main()
{
	Words();
	Drinks();
	Dishes();
	BreadSweetsCheese();
	Meat();
	Fish();
	FruitAndVegetables();
	Unknown();
	InRange();
	if (failures) {
		std::printf("%d check(s) failed\n", failures);
		return 1;
	}
	std::printf("FoodValues: all checks passed\n");
	return 0;
}
