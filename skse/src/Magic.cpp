#include "Magic.h"

#include "Game.h"
#include "MagicKinds.h"

namespace skycraft::Magic
{
	namespace
	{
		// MagicKinds.h's numbers are CommonLib's.
		using Arch = RE::EffectArchetypes::ArchetypeID;
		static_assert(MagicKinds::kArchValueModifier == static_cast<int>(Arch::kValueModifier));
		static_assert(MagicKinds::kArchDualValueModifier == static_cast<int>(Arch::kDualValueModifier));
		static_assert(MagicKinds::kArchCalm == static_cast<int>(Arch::kCalm));
		static_assert(MagicKinds::kArchDemoralize == static_cast<int>(Arch::kDemoralize));
		static_assert(MagicKinds::kArchFrenzy == static_cast<int>(Arch::kFrenzy));
		static_assert(MagicKinds::kArchInvisibility == static_cast<int>(Arch::kInvisibility));
		static_assert(MagicKinds::kArchSummonCreature == static_cast<int>(Arch::kSummonCreature));
		static_assert(MagicKinds::kArchTelekinesis == static_cast<int>(Arch::kTelekinesis));
		static_assert(MagicKinds::kArchReanimate == static_cast<int>(Arch::kReanimate));
		static_assert(MagicKinds::kArchValueAndParts == static_cast<int>(Arch::kValueAndParts));
		static_assert(MagicKinds::kArchStagger == static_cast<int>(Arch::kStagger));
		static_assert(MagicKinds::kArchPeakValueModifier == static_cast<int>(Arch::kPeakValueModifier));
		static_assert(MagicKinds::kArchSlowTime == static_cast<int>(Arch::kSlowTime));
		static_assert(MagicKinds::kArchRally == static_cast<int>(Arch::kRally));
		static_assert(MagicKinds::kArchEtherealize == static_cast<int>(Arch::kEtherealize));
		static_assert(MagicKinds::kAvIllusion == static_cast<int>(RE::ActorValue::kIllusion));
		static_assert(MagicKinds::kAvHealth == static_cast<int>(RE::ActorValue::kHealth));
		static_assert(MagicKinds::kAvResistFire == static_cast<int>(RE::ActorValue::kResistFire));
		static_assert(MagicKinds::kAvResistShock == static_cast<int>(RE::ActorValue::kResistShock));
		static_assert(MagicKinds::kAvResistFrost == static_cast<int>(RE::ActorValue::kResistFrost));
		static_assert(MagicKinds::kAvWardPower == static_cast<int>(RE::ActorValue::kWardPower));
		// MagicSpell::delivery is Skyrim's own MagicSystem::Delivery number (Proto.DELIVERY_*).
		static_assert(static_cast<int>(RE::MagicSystem::Delivery::kSelf) == 0 && static_cast<int>(RE::MagicSystem::Delivery::kTargetLocation) == 4);

		constexpr float kRefreshSeconds = 0.5f;
		// A cast Minecraft asks for comes no sooner than this after the last (and a fire and forget spell no sooner than its
		// own charge time, as Skyrim's hands cast it), so a client gone wrong can't drain the magicka in a burst or flood
		// Skyrim with projectiles.
		constexpr float kCastGapSeconds = 0.1f;
		// A concentration pulse comes no sooner than this share of its own stretch after the last.
		constexpr float kPulseGapShare = 0.8f;
		// The most of a concentration spell one pulse pays for (seconds), and the least.
		constexpr float kPulseLongest = 1.0f;
		constexpr float kPulseShortest = 0.05f;
		// fMagickaRegenDelay's value if the game setting can't be read (Skyrim's default).
		constexpr float kDefaultRegenDelay = 3.0f;

		float         refreshTimer = 0.0f;
		bool          refreshNow = true;
		// Starts somewhere new each Skyrim run, never 0 (0 is "nothing written yet" to Minecraft).
		std::uint32_t refreshSeq = [] {
			LARGE_INTEGER t;
			::QueryPerformanceCounter(&t);
			return static_cast<std::uint32_t>(t.QuadPart) | 1u;
		}();
		float castGap = 0.0f;  // seconds before the next cast is taken
		// What the table said last time, so it's logged when it changes.
		std::uint32_t loggedEquipped = 0xFFFFFFFF;
		std::uint32_t loggedCounts = 0xFFFFFFFF;
		int           tableLogs = 0;
		int           shoutLogs = 0;
		int           castLogs = 0;
		int           refusalLogs = 0;
		std::unordered_set<RE::FormID> wordsLogged;  // shouts whose words read as none unlocked, logged once each

		MagicKinds::Effect Facts(const RE::Effect* a_effect)
		{
			MagicKinds::Effect out;
			const auto* base = a_effect ? a_effect->baseEffect : nullptr;
			if (!base) {
				return out;
			}
			out.archetype = static_cast<int>(base->GetArchetype());
			out.primaryAV = static_cast<int>(base->data.primaryAV);
			out.resist = static_cast<int>(base->data.resistVariable);
			out.school = static_cast<int>(base->GetMagickSkill());
			out.hostile = base->IsHostile() || base->IsDetrimental();
			return out;
		}

		// A shout's words of power the player has unlocked, in order (a word learned at a wall is unlocked with a dragon soul).
		std::uint8_t UnlockedWords(const RE::TESShout* a_shout)
		{
			std::uint8_t words = 0;
			for (const auto& variation : a_shout->variations) {
				const auto* word = variation.word;
				if (!word || (word->GetFormFlags() & RE::TESForm::RecordFlags::kUnlocked) == 0) {
					break;
				}
				++words;
			}
			return words;
		}

		std::uint8_t ShoutKindOf(const RE::TESShout* a_shout)
		{
			std::array<MagicKinds::Effect, 8> effects{};
			std::size_t                       count = 0;
			if (const auto* spell = a_shout->variations[0].spell) {
				for (const auto* effect : spell->effects) {
					if (count < effects.size()) {
						effects[count++] = Facts(effect);
					}
				}
			}
			return MagicKinds::ShoutKind(a_shout->GetName(), effects.data(), count);
		}

		std::uint8_t SpellKindOf(const RE::SpellItem* a_spell)
		{
			return MagicKinds::SpellKind(Facts(a_spell->effects.empty() ? nullptr : a_spell->effects[0]));
		}

		// A spell the player casts with their hands: spell type Spell, fire and forget or concentration.
		bool HandCast(const RE::SpellItem* a_spell)
		{
			if (!a_spell || a_spell->effects.empty() || a_spell->GetSpellType() != RE::MagicSystem::SpellType::kSpell) {
				return false;
			}
			const auto casting = a_spell->GetCastingType();
			return casting == RE::MagicSystem::CastingType::kFireAndForget || casting == RE::MagicSystem::CastingType::kConcentration;
		}

		// Its school as MagicSpell::school: 18 Alteration to 22 Restoration, 0 none.
		std::uint8_t SchoolOf(const RE::SpellItem* a_spell)
		{
			auto school = a_spell->GetAssociatedSkill();
			if (school == RE::ActorValue::kNone && !a_spell->effects.empty() && a_spell->effects[0] && a_spell->effects[0]->baseEffect) {
				school = a_spell->effects[0]->baseEffect->GetMagickSkill();
			}
			const int av = static_cast<int>(school);
			return av >= static_cast<int>(RE::ActorValue::kAlteration) && av <= static_cast<int>(RE::ActorValue::kRestoration) ? static_cast<std::uint8_t>(av) : 0;
		}

		// Every spell the player casts by hand: their own (the character's spell list), their race's (Flames and Healing for
		// everyone) and those learned since (spell tomes, quests).
		void GatherSpells(RE::PlayerCharacter* a_player, std::vector<RE::SpellItem*>& a_out)
		{
			auto add = [&](RE::SpellItem* a_spell) {
				if (HandCast(a_spell) && std::ranges::find(a_out, a_spell) == a_out.end()) {
					a_out.push_back(a_spell);
				}
			};
			auto addList = [&](const RE::TESSpellList::SpellData* a_list) {
				if (a_list && a_list->spells) {
					for (std::uint32_t i = 0; i < a_list->numSpells; ++i) {
						add(a_list->spells[i]);
					}
				}
			};
			if (auto* npc = a_player->GetActorBase()) {
				addList(npc->GetSpellList());
			}
			if (auto* race = a_player->GetRace()) {
				addList(race->actorEffects);
			}
			for (auto* spell : a_player->GetActorRuntimeData().addedSpells) {
				add(spell);
			}
		}

		// The player knows this spell: Skyrim says so, or it's among those GatherSpells finds (their race's spells too).
		bool Knows(RE::PlayerCharacter* a_player, RE::SpellItem* a_spell)
		{
			if (a_player->HasSpell(a_spell)) {
				return true;
			}
			std::vector<RE::SpellItem*> spells;
			GatherSpells(a_player, spells);
			return std::ranges::find(spells, a_spell) != spells.end();
		}

		float ShoutRecoveryMult(RE::PlayerCharacter* a_player)
		{
			const float mult = a_player->AsActorValueOwner()->GetActorValue(RE::ActorValue::kShoutRecoveryMult);
			return std::isfinite(mult) ? std::clamp(mult, 0.0f, 10.0f) : 1.0f;
		}

		RE::TESShout* EquippedShout(RE::PlayerCharacter* a_player)
		{
			auto* power = a_player->GetActorRuntimeData().selectedPower;
			return power ? power->As<RE::TESShout>() : nullptr;  // a power in the slot isn't a shout
		}

		void FillShouts(RE::PlayerCharacter* a_player, proto::MagicTable& a_table)
		{
			a_table.flags |= proto::kMagicShouts;
			auto* equipped = EquippedShout(a_player);
			a_table.equippedShout = equipped ? equipped->GetFormID() : 0;
			a_table.voiceRecovery = std::max(0.0f, a_player->GetVoiceRecoveryTime());
			a_table.shoutRecoveryMult = ShoutRecoveryMult(a_player);
			std::vector<RE::TESShout*> shouts;
			if (equipped) {
				shouts.push_back(equipped);  // first, so it always has room
			}
			auto* npc = a_player->GetActorBase();
			auto* list = npc ? npc->GetSpellList() : nullptr;
			if (list && list->shouts) {
				for (std::uint32_t i = 0; i < list->numShouts; ++i) {
					if (auto* shout = list->shouts[i]; shout && shout != equipped) {
						shouts.push_back(shout);
					}
				}
			}
			for (auto* shout : shouts) {
				if (a_table.shoutCount >= proto::kMaxMagicShouts) {
					break;
				}
				auto& r = a_table.shouts[a_table.shoutCount++];
				r.formId = shout->GetFormID();
				r.words = UnlockedWords(shout);
				r.kind = ShoutKindOf(shout);
				r.flags = shout == equipped ? proto::kMagicShoutEquipped : 0;
				for (std::size_t i = 0; i < 3; ++i) {
					r.recovery[i] = shout->variations[i].recoveryTime;
				}
				strncpy_s(r.name, shout->GetName(), _TRUNCATE);
				if (r.words == 0 && wordsLogged.insert(r.formId).second && wordsLogged.size() < 40) {
					const auto* word = shout->variations[0].word;
					logger::info("SkyCraft-magic: shout {} ({:08X}) has no word unlocked yet (its first word's flags {:08X})", r.name, r.formId,
						word ? word->GetFormFlags() : 0u);
				}
			}
		}

		void FillSpells(RE::PlayerCharacter* a_player, proto::MagicTable& a_table)
		{
			a_table.flags |= proto::kMagicSpells;
			a_table.magicka = a_player->AsActorValueOwner()->GetActorValue(RE::ActorValue::kMagicka);
			a_table.magickaMax = a_player->GetActorValueMax(RE::ActorValue::kMagicka);
			std::vector<RE::SpellItem*> spells;
			GatherSpells(a_player, spells);
			std::vector<const RE::TESForm*> favourites;
			if (auto* magic = RE::MagicFavorites::GetSingleton()) {
				for (auto* form : magic->spells) {
					if (form) {
						favourites.push_back(form);
					}
				}
			}
			struct Entry
			{
				RE::SpellItem* spell;
				float          cost;
				std::uint8_t   school;
				bool           favourite;
			};
			std::vector<Entry> entries;
			entries.reserve(spells.size());
			for (auto* spell : spells) {
				const float cost = spell->CalculateMagickaCost(a_player);
				entries.push_back({ spell, std::isfinite(cost) ? std::max(cost, 0.0f) : 0.0f, SchoolOf(spell),
					std::ranges::find(favourites, static_cast<const RE::TESForm*>(spell)) != favourites.end() });
			}
			// Favourites first (so they always have room), then by school and cost, as Minecraft cycles them.
			std::ranges::sort(entries, [](const Entry& a_l, const Entry& a_r) {
				if (a_l.favourite != a_r.favourite) {
					return a_l.favourite;
				}
				if (a_l.school != a_r.school) {
					return a_l.school < a_r.school;
				}
				if (a_l.cost != a_r.cost) {
					return a_l.cost < a_r.cost;
				}
				return std::string_view(a_l.spell->GetName()) < std::string_view(a_r.spell->GetName());
			});
			for (const auto& e : entries) {
				if (a_table.spellCount >= proto::kMaxMagicSpells) {
					break;
				}
				auto& r = a_table.spells[a_table.spellCount++];
				r.formId = e.spell->GetFormID();
				r.cost = e.cost;
				r.school = e.school;
				r.kind = SpellKindOf(e.spell);
				r.flags = static_cast<std::uint8_t>((e.spell->GetCastingType() == RE::MagicSystem::CastingType::kConcentration ? proto::kMagicSpellConcentration : 0u) |
				                                    (e.favourite ? proto::kMagicSpellFavourite : 0u) | (e.spell->IsTwoHanded() ? proto::kMagicSpellTwoHanded : 0u) |
				                                    (e.spell->IsHostile() ? proto::kMagicSpellHostile : 0u));
				r.delivery = static_cast<std::uint8_t>(e.spell->GetDelivery());
				strncpy_s(r.name, e.spell->GetName(), _TRUNCATE);
				if (e.favourite) {
					a_table.flags |= proto::kMagicFavourites;
				}
			}
		}

		void LogTable(const proto::MagicTable& a_table)
		{
			const auto counts = static_cast<std::uint32_t>(a_table.shoutCount) << 8 | a_table.spellCount;
			if (tableLogs >= 20 || (a_table.equippedShout == loggedEquipped && counts == loggedCounts)) {
				return;
			}
			++tableLogs;
			loggedEquipped = a_table.equippedShout;
			loggedCounts = counts;
			const char* equipped = "none";
			int         words = 0;
			for (std::uint32_t i = 0; i < a_table.shoutCount; ++i) {
				if (a_table.shouts[i].formId == a_table.equippedShout) {
					equipped = a_table.shouts[i].name;
					words = a_table.shouts[i].words;
				}
			}
			logger::info("SkyCraft-magic: magic table: {} shouts (equipped {}, {} words unlocked; recovery x{:.2f}), {} spells cast by hand{}, magicka {:.0f}/{:.0f}",
				a_table.shoutCount, equipped, words, a_table.shoutRecoveryMult, a_table.spellCount,
				(a_table.flags & proto::kMagicFavourites) ? " (favourites first)" : "", a_table.magicka, a_table.magickaMax);
		}

		void Answer(proto::InputType a_type, std::uint16_t a_result, std::uint8_t a_kind, std::uint32_t a_request, std::int32_t a_b, std::int32_t a_c)
		{
			Link::Get().PushInput(a_type, static_cast<std::uint16_t>(a_result | (a_kind << proto::kMagicKindShift)), static_cast<std::int32_t>(a_request), a_b, a_c);
		}

		void Refused(const char* a_what, const char* a_name, const char* a_why)
		{
			if (refusalLogs < 20) {
				++refusalLogs;
				logger::info("SkyCraft-magic: {} {}: {}", a_what, a_name ? a_name : "?", a_why);
			}
		}

		// The player faces where Minecraft's player looked when it asked (Game.cpp keeps them in step every frame anyway).
		void Aim(RE::PlayerCharacter* a_player, float a_yaw, float a_pitch)
		{
			if (std::isfinite(a_yaw) && std::isfinite(a_pitch)) {
				a_player->data.angle.z = McYawToHeading(a_yaw);
				a_player->data.angle.x = std::clamp(a_pitch, -90.0f, 90.0f) * 0.0174532925f;
			}
		}

		// The voice recovers from now for a_seconds, as after Skyrim's own shout (the player's high process keeps it).
		void BeginRecovery(RE::PlayerCharacter* a_player, float a_seconds)
		{
			auto* process = a_player->GetActorRuntimeData().currentProcess;
			if (process && process->high) {
				process->high->voiceRecoveryTime = a_seconds;
			}
		}

		float RegenDelay()
		{
			auto* settings = RE::GameSettingCollection::GetSingleton();
			auto* setting = settings ? settings->GetSetting("fMagickaRegenDelay") : nullptr;
			return setting && setting->GetType() == RE::Setting::Type::kFloat ? setting->GetFloat() : kDefaultRegenDelay;
		}

		void Shout(RE::PlayerCharacter* a_player, const proto::McEvent& a_event, bool a_puppeting)
		{
			auto* shout = RE::TESForm::LookupByID<RE::TESShout>(a_event.formId);
			const std::uint8_t kind = shout ? ShoutKindOf(shout) : proto::kShoutKindOther;
			const char* name = shout ? shout->GetName() : nullptr;
			if (!a_puppeting || !Game::FeatureOn(proto::kFeatShouts) || a_player->IsDead()) {
				Refused("shout", name, "Skyrim has the player now, or the shouts switch is off");
				return Answer(proto::kInShoutResult, proto::kShoutCantNow, kind, a_event.weapon, 0, 0);
			}
			const std::uint8_t unlocked = shout && a_player->HasShout(shout) ? UnlockedWords(shout) : 0;
			if (unlocked == 0) {
				Refused("shout", name, shout ? "no word of it unlocked" : "no such shout");
				return Answer(proto::kInShoutResult, proto::kShoutUnknown, kind, a_event.weapon, 0, 0);
			}
			const float left = a_player->GetVoiceRecoveryTime();
			if (left > 0.05f) {
				return Answer(proto::kInShoutResult, proto::kShoutRecovering, kind, a_event.weapon, 0, static_cast<std::int32_t>(left * 1000.0f));
			}
			const std::uint32_t words = std::clamp<std::uint32_t>(a_event.flags, 1u, unlocked);
			const auto&         variation = shout->variations[words - 1];
			auto*               caster = a_player->GetMagicCaster(RE::MagicSystem::CastingSource::kOther);  // the voice
			if (!variation.spell || !caster) {
				Refused("shout", name, variation.spell ? "the player has no voice caster" : "that variation has no spell");
				return Answer(proto::kInShoutResult, proto::kShoutCantNow, kind, a_event.weapon, 0, 0);
			}
			if (EquippedShout(a_player) != shout) {
				if (auto* equip = RE::ActorEquipManager::GetSingleton()) {
					equip->EquipShout(a_player, shout);  // Minecraft's player picked it: Skyrim's voice slot follows
				}
			}
			Aim(a_player, a_event.a, a_event.b);
			auto* target = variation.spell->GetDelivery() == RE::MagicSystem::Delivery::kSelf ? a_player : nullptr;
			caster->CastSpellImmediate(variation.spell, false, target, 1.0f, false, 0.0f, a_player);
			const float recovery = std::max(0.0f, variation.recoveryTime * ShoutRecoveryMult(a_player));
			BeginRecovery(a_player, recovery);
			refreshNow = true;
			Answer(proto::kInShoutResult, proto::kShoutDone, kind, a_event.weapon, static_cast<std::int32_t>(words), static_cast<std::int32_t>(recovery * 1000.0f));
			if (shoutLogs < 20) {
				++shoutLogs;
				logger::info("SkyCraft-magic: shouted {} ({} of {} words, kind {}): voice recovery {:.1f} s (reads {:.1f} s)", name ? name : "?", words, unlocked, kind,
					recovery, a_player->GetVoiceRecoveryTime());
			}
		}

		void Cast(RE::PlayerCharacter* a_player, const proto::McEvent& a_event, bool a_puppeting)
		{
			auto*              spell = RE::TESForm::LookupByID<RE::SpellItem>(a_event.formId);
			const std::uint8_t kind = spell ? SpellKindOf(spell) : proto::kSpellKindOther;
			const char*        name = spell ? spell->GetName() : nullptr;
			const auto         request = a_event.weapon;
			const auto         formId = static_cast<std::int32_t>(a_event.formId);
			if (!a_puppeting || !Game::FeatureOn(proto::kFeatSpells) || a_player->IsDead() || castGap > 0.0f) {
				if (castGap <= 0.0f) {
					Refused("cast", name, "Skyrim has the player now, or the spells switch is off");
				}
				return Answer(proto::kInCastResult, proto::kCastCantNow, kind, request, 0, formId);
			}
			if (!HandCast(spell) || !Knows(a_player, spell)) {
				Refused("cast", name, spell ? "not a spell the player casts by hand" : "no such spell");
				return Answer(proto::kInCastResult, proto::kCastUnknown, kind, request, 0, formId);
			}
			const bool concentration = spell->GetCastingType() == RE::MagicSystem::CastingType::kConcentration;
			const bool pulse = concentration && (a_event.flags & proto::kCastPulse) != 0;
			float      seconds = 0.0f;
			float      cost = spell->CalculateMagickaCost(a_player);  // per second for a concentration spell
			if (pulse) {
				seconds = std::isfinite(a_event.c) ? std::clamp(a_event.c, kPulseShortest, kPulseLongest) : kPulseShortest;
				cost *= seconds;
			}
			cost = std::isfinite(cost) ? std::max(cost, 0.0f) : 0.0f;
			auto* magicka = a_player->AsActorValueOwner();
			if (magicka->GetActorValue(RE::ActorValue::kMagicka) < cost) {
				return Answer(proto::kInCastResult, proto::kCastNoMagicka, kind, request, static_cast<std::int32_t>(cost * 100.0f), formId);
			}
			auto* caster = a_player->GetMagicCaster(RE::MagicSystem::CastingSource::kRightHand);
			if (!caster) {
				Refused("cast", name, "the player has no right hand caster");
				return Answer(proto::kInCastResult, proto::kCastCantNow, kind, request, 0, formId);
			}
			// Who it's cast at: the player for a self spell, the actor under the crosshair for a touch or target actor spell;
			// an aimed or target location spell goes where the player looks.
			RE::NiPointer<RE::TESObjectREFR> picked;
			RE::TESObjectREFR*               target = nullptr;
			const auto                       delivery = spell->GetDelivery();
			if (delivery == RE::MagicSystem::Delivery::kSelf) {
				target = a_player;
			} else if (delivery == RE::MagicSystem::Delivery::kTouch || delivery == RE::MagicSystem::Delivery::kTargetActor) {
				if (auto* pick = RE::CrosshairPickData::GetSingleton()) {
					picked = pick->GetActiveTarget().get();
				}
				auto* actor = picked ? picked->As<RE::Actor>() : nullptr;
				if (!actor || actor == a_player || actor->IsDead()) {
					Refused("cast", name, "nobody under the crosshair to cast it at");
					return Answer(proto::kInCastResult, proto::kCastCantNow, kind, request, 0, formId);
				}
				target = actor;
			}
			Aim(a_player, a_event.a, a_event.b);
			caster->CastSpellImmediate(spell, false, target, 1.0f, false, 0.0f, a_player);
			magicka->DamageActorValue(RE::ActorValue::kMagicka, cost);
			a_player->UpdateRegenDelay(RE::ActorValue::kMagicka, RegenDelay());
			castGap = pulse ? std::max(kCastGapSeconds, kPulseGapShare * seconds) : std::max(kCastGapSeconds, spell->GetChargeTime());
			refreshNow = true;
			Answer(proto::kInCastResult, proto::kCastDone, kind, request, static_cast<std::int32_t>(cost * 100.0f), formId);
			if (castLogs < 20) {
				++castLogs;
				const std::uint8_t school = SchoolOf(spell);
				logger::info("SkyCraft-magic: cast {} ({:.1f} magicka{}, kind {}, delivery {}; {} skill {:.0f})", name ? name : "?", cost,
					pulse ? std::format(" for {:.2f} s", seconds) : std::string(), kind, static_cast<int>(delivery), school,
					school ? magicka->GetBaseActorValue(static_cast<RE::ActorValue>(school)) : 0.0f);
			}
		}
	}

	void Update(RE::PlayerCharacter* a_player, bool a_loading, float a_delta)
	{
		castGap = std::max(0.0f, castGap - std::max(a_delta, 0.0f));
		refreshTimer -= a_delta;
		if (!a_player || a_loading || (!refreshNow && refreshTimer > 0.0f)) {
			return;
		}
		refreshTimer = kRefreshSeconds;
		refreshNow = false;
		proto::MagicTable table{};
		if (++refreshSeq == 0) {
			refreshSeq = 1;
		}
		table.refresh = refreshSeq;
		table.shoutRecoveryMult = 1.0f;
		if (Game::FeatureOn(proto::kFeatShouts)) {
			FillShouts(a_player, table);
		}
		if (Game::FeatureOn(proto::kFeatSpells)) {
			FillSpells(a_player, table);
		}
		Link::Get().WriteMagic(table);
		if (table.flags != 0) {
			LogTable(table);
		}
	}

	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event, bool a_puppeting)
	{
		if (!a_player) {
			return;
		}
		if (a_event.type == proto::kEvShout) {
			Shout(a_player, a_event, a_puppeting);
		} else if (a_event.type == proto::kEvCastSpell) {
			Cast(a_player, a_event, a_puppeting);
		}
	}

	void Reset()
	{
		refreshNow = true;
		castGap = 0.0f;
		wordsLogged.clear();
		loggedEquipped = 0xFFFFFFFF;
		loggedCounts = 0xFFFFFFFF;
	}
}
