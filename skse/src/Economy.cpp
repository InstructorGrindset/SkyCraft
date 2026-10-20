#include "Economy.h"

#include "FoodValues.h"
#include "Game.h"

namespace skycraft::Economy
{
	namespace
	{
		constexpr RE::FormID   kGold = 0x0000000F;          // Skyrim.esm Gold001, Skyrim's coin
		constexpr float        kReachBlocks = 16.0f;        // Minecraft sells within 8 blocks; further is a stale request
		constexpr std::int32_t kMostGoldOneSale = 100000;   // far above anything a merchant holds
		int                    foodLogs = 0;

		std::string NameOf(RE::TESObjectREFR* a_ref)
		{
			const char* name = a_ref ? a_ref->GetDisplayFullName() : nullptr;
			return name && *name ? std::string(name) : std::format("{:08X}", a_ref ? a_ref->GetFormID() : 0);
		}

		// Where a merchant keeps the gold it pays with: its vendor faction's merchant chest, the one Skyrim's barter menu
		// buys and sells from; its own inventory when it has none (a Skooma dealer only Minecraft makes a merchant).
		RE::TESObjectREFR* Purse(RE::Actor* a_actor)
		{
			// GetVendorFaction returns the faction from before it recalculates it (WorldState::ReadVendorMask).
			auto* faction = a_actor->GetVendorFaction();
			if (!faction) {
				faction = a_actor->GetActorRuntimeData().vendorFaction;
			}
			if (faction && faction->vendorData.merchantContainer) {
				return faction->vendorData.merchantContainer;
			}
			return a_actor;
		}

		std::int32_t GoldIn(RE::TESObjectREFR* a_ref, RE::TESBoundObject* a_gold)
		{
			const auto inventory = a_ref->GetInventory([a_gold](RE::TESBoundObject& a_obj) { return &a_obj == a_gold; });
			const auto it = inventory.find(a_gold);
			return it != inventory.end() ? std::max(0, it->second.first) : 0;
		}

		// The merchant Minecraft asks about, if Skyrim can trade with it now: alive, loaded and near the player.
		RE::Actor* NearMerchant(RE::PlayerCharacter* a_player, RE::FormID a_id)
		{
			auto* actor = RE::TESForm::LookupByID<RE::Actor>(a_id);
			if (!actor || actor == a_player || actor->IsDead() || actor->IsDisabled() || actor->IsDeleted() || !actor->Is3DLoaded()) {
				return nullptr;
			}
			const float away = actor->GetPosition().GetDistance(a_player->GetPosition()) / static_cast<float>(proto::kUnitsPerBlock);
			return away <= kReachBlocks ? actor : nullptr;
		}

		// Main thread: the player ate this food in Skyrim. Minecraft fills its hunger by what FoodValues.h makes of it.
		void Ate(RE::FormID a_food)
		{
			auto* food = RE::TESForm::LookupByID<RE::AlchemyItem>(a_food);
			if (!food || !Game::FeatureOn(proto::kFeatSkyrimFood) || !Link::Get().McAlive()) {
				return;
			}
			const char*   raw = food->GetName();
			const auto*   keywords = food->As<RE::BGSKeywordForm>();
			const bool    rawFood = keywords && keywords->HasKeywordString("VendorItemFoodRaw");
			const auto    value = FoodValues::FromName(raw ? raw : "", rawFood);
			const auto    saturation = static_cast<std::int32_t>(std::lround(value.saturation * 1000.0f));
			Link::Get().PushInput(proto::kInSkyrimFood, static_cast<std::uint16_t>(value.nutrition), saturation, static_cast<std::int32_t>(a_food),
				static_cast<std::int32_t>(value.kind));
			if (foodLogs < 20) {
				++foodLogs;
				logger::info("SkyCraft-economy: ate {} ({:08X}{}): {} for Minecraft, {} food points at saturation {:.1f}", raw ? raw : "?", a_food,
					rawFood ? ", raw" : "", FoodValues::KindName(value.kind), value.nutrition, value.saturation);
			}
		}

		// Eating or drinking is equipping a food or potion as far as Skyrim's events go (Papyrus OnObjectEquipped too).
		class EquipSink final : public RE::BSTEventSink<RE::TESEquipEvent>
		{
		public:
			static EquipSink* Get()
			{
				static EquipSink sink;
				return &sink;
			}

			RE::BSEventNotifyControl ProcessEvent(const RE::TESEquipEvent* a_event, RE::BSTEventSource<RE::TESEquipEvent>*) override
			{
				auto* player = RE::PlayerCharacter::GetSingleton();
				if (!a_event || !a_event->equipped || !player || a_event->actor.get() != player) {
					return RE::BSEventNotifyControl::kContinue;
				}
				const auto* food = RE::TESForm::LookupByID<RE::AlchemyItem>(a_event->baseObject);
				if (!food || !food->IsFood()) {
					return RE::BSEventNotifyControl::kContinue;
				}
				// The input ring has one producer, the main thread: send it from there.
				const RE::FormID id = a_event->baseObject;
				if (const auto* tasks = SKSE::GetTaskInterface()) {
					tasks->AddTask([id]() { Ate(id); });
				} else {
					Ate(id);
				}
				return RE::BSEventNotifyControl::kContinue;
			}
		};
	}

	void Install()
	{
		if (auto* events = RE::ScriptEventSourceHolder::GetSingleton()) {
			events->AddEventSink<RE::TESEquipEvent>(EquipSink::Get());
			logger::info("SkyCraft-economy: watching the player eat (Skyrim food fills Minecraft's hunger)");
		}
	}

	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
	{
		auto* gold = RE::TESForm::LookupByID<RE::TESBoundObject>(kGold);
		if (a_event.type == proto::kEvMerchantGold) {
			auto*              merchant = NearMerchant(a_player, a_event.formId);
			const std::int32_t have = merchant && gold ? GoldIn(Purse(merchant), gold) : -1;
			Link::Get().PushInput(proto::kInMerchantGold, 0, static_cast<std::int32_t>(a_event.formId), have, static_cast<std::int32_t>(a_event.flags));
			if (merchant) {
				logger::info("SkyCraft-economy: {} ({:08X}) has {} gold to buy with", NameOf(merchant), a_event.formId, have);
			} else {
				logger::info("SkyCraft-economy: Minecraft asked about merchant {:08X}, who isn't here to trade (gone, dead or too far)", a_event.formId);
			}
			return;
		}
		if (a_event.type != proto::kEvSellToMerchant || !gold) {
			return;
		}
		const auto asked = static_cast<std::int32_t>(std::clamp(std::lround(a_event.a), 0L, static_cast<long>(kMostGoldOneSale)));
		// Minecraft already took the goods: pay even if the merchant stepped away since it asked, only never more than it has.
		auto* merchant = RE::TESForm::LookupByID<RE::Actor>(a_event.formId);
		if (asked <= 0 || !merchant || merchant == a_player) {
			logger::warn("SkyCraft-economy: Minecraft sold to {:08X} for {} gold, but that's no merchant here; nothing paid", a_event.formId, asked);
			return;
		}
		auto*              purse = Purse(merchant);
		const std::int32_t pay = std::min(asked, GoldIn(purse, gold));
		if (pay > 0) {
			purse->RemoveItem(gold, pay, RE::ITEM_REMOVE_REASON::kRemove, nullptr, nullptr);
			a_player->AddObjectToContainer(gold, nullptr, pay, nullptr);
		}
		logger::info("SkyCraft-economy: {} ({:08X}) paid {} gold for {} Minecraft items{}", NameOf(merchant), a_event.formId, pay,
			static_cast<int>(a_event.b), pay < asked ? std::format(" (Minecraft asked {}, it had no more)", asked) : std::string());
	}
}
