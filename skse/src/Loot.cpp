#include "Loot.h"

namespace skycraft::Loot
{
	namespace
	{
		std::string_view TypeName(RE::FormType a_type)
		{
			switch (a_type) {
			case RE::FormType::Weapon:
				return "weapon";
			case RE::FormType::Armor:
				return "armor";
			case RE::FormType::Ammo:
				return "ammo";
			case RE::FormType::AlchemyItem:
				return "potion";
			case RE::FormType::Ingredient:
				return "ingredient";
			case RE::FormType::Misc:
				return "misc";
			case RE::FormType::Book:
				return "book";
			case RE::FormType::Scroll:
				return "scroll";
			case RE::FormType::KeyMaster:
				return "key";
			case RE::FormType::SoulGem:
				return "soulgem";
			case RE::FormType::Light:
				return "light";
			default:
				return "other";
			}
		}

		// Where the item came from, in words: the world, a corpse, a container or a living actor.
		std::string Source(RE::FormID a_from)
		{
			if (a_from == 0) {
				return "world";
			}
			auto* ref = RE::TESForm::LookupByID<RE::TESObjectREFR>(a_from);
			if (!ref) {
				return std::format("{:08X}", a_from);
			}
			const char* name = ref->GetDisplayFullName();
			std::string label = name && *name ? name : std::format("{:08X}", a_from);
			if (auto* actor = ref->As<RE::Actor>()) {
				return std::format("{} of {}", actor->IsDead() ? "corpse" : "actor", label);
			}
			return "container " + label;
		}

		std::string Keywords(RE::TESForm* a_item)
		{
			std::string out;
			auto* form = a_item->As<RE::BGSKeywordForm>();
			if (!form) {
				return out;
			}
			for (std::uint32_t i = 0; i < form->numKeywords; ++i) {
				auto* kw = form->keywords[i];
				if (!kw) {
					continue;
				}
				const char* id = kw->GetFormEditorID();
				if (!out.empty()) {
					out += ' ';
				}
				out += id && *id ? id : std::format("{:08X}", kw->GetFormID());
			}
			return out;
		}

		class ContainerSink final : public RE::BSTEventSink<RE::TESContainerChangedEvent>
		{
		public:
			static ContainerSink* Get()
			{
				static ContainerSink sink;
				return &sink;
			}

			RE::BSEventNotifyControl ProcessEvent(const RE::TESContainerChangedEvent* a_event, RE::BSTEventSource<RE::TESContainerChangedEvent>*) override
			{
				auto* player = RE::PlayerCharacter::GetSingleton();
				if (!a_event || !player || a_event->newContainer != player->GetFormID() || a_event->itemCount <= 0) {
					return RE::BSEventNotifyControl::kContinue;
				}
				auto* item = RE::TESForm::LookupByID(a_event->baseObj);
				if (!item) {
					return RE::BSEventNotifyControl::kContinue;
				}
				const char* name = item->GetName();
				std::string_view type = TypeName(item->GetFormType());
				if (auto* alch = item->As<RE::AlchemyItem>(); alch && alch->IsFood()) {
					type = "food";
				}
				std::int32_t value = 0;
				if (auto* valueForm = item->As<RE::TESValueForm>()) {
					value = valueForm->value;
				}
				logger::info("SkyCraft-loot: got {} x {} [{} {:08X}] value {} from {} | {}", a_event->itemCount, name && *name ? name : "?", type,
					item->GetFormID(), value, Source(a_event->oldContainer), Keywords(item));
				return RE::BSEventNotifyControl::kContinue;
			}
		};
	}

	void Install()
	{
		if (auto* events = RE::ScriptEventSourceHolder::GetSingleton()) {
			events->AddEventSink<RE::TESContainerChangedEvent>(ContainerSink::Get());
			logger::info("SkyCraft-loot: watching the player's inventory (detect only)");
		}
	}
}
