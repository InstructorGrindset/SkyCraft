#include "Quests.h"

#include "Game.h"
#include "QuestTargets.h"

// Where the markers come from. Skyrim's compass draws a marker for each target in the player's quest target map
// (PlayerCharacter questTargets: each quest that has targets now, with its TESQuestTarget list, kept under
// questTargetsLock). The journal's active quests (TESQuest IsActive) are the ones the compass shows: kQuestTracked here.
// Each target names one of its quest's aliases, and Skyrim's own TESQuestTarget::GetTrackingRef (GetTargetRef when that
// gives none) says which reference it is on now. For a target in another world Skyrim keeps the way there on the target
// (TESQuestTarget teleportPath: the load doors in turn); the marker goes on the first of those doors that stands in the
// player's world, which is where Skyrim's compass points. A target in another world with no such door (no way there worked
// out yet) is left out rather than guessed at. Nothing here writes to Skyrim's data.
namespace skycraft::Quests
{
	namespace
	{
		constexpr float  kRefreshSeconds = 0.5f;
		constexpr double kOnPlayerBlocks = 1.0;  // a target this close to the player is on the player (an item they carry)

		// The cell a reference is in (its save cell for one Skyrim hasn't attached), null if none (in an inventory).
		RE::TESObjectCELL* CellOf(const RE::TESObjectREFR* a_ref)
		{
			auto* cell = a_ref->GetParentCell();
			return cell ? cell : a_ref->GetSaveParentCell();
		}

		// The world a reference is in, as SkyState::worldId names worlds: its interior cell, else its worldspace. 0 if it is
		// in no cell.
		std::uint32_t WorldOf(const RE::TESObjectREFR* a_ref)
		{
			auto* cell = CellOf(a_ref);
			if (!cell) {
				return 0;
			}
			if (cell->IsInteriorCell()) {
				return cell->GetFormID();
			}
			auto* world = a_ref->GetWorldspace();
			return world ? world->GetFormID() : cell->GetFormID();
		}

		// One target out of the map: its quest, and the doors of its way there (FormIDs, copied while the map is locked).
		struct Seen
		{
			RE::TESQuest*           quest{ nullptr };
			RE::TESQuestTarget*     target{ nullptr };
			std::vector<RE::FormID> doors;
		};

		// The objective of the quest (its own list, loaded with the quest) that this target belongs to, or null.
		const RE::BGSQuestObjective* ObjectiveOf(const RE::TESQuest* a_quest, const RE::TESQuestTarget* a_target)
		{
			for (const auto* objective : a_quest->objectives) {
				if (!objective || !objective->targets) {
					continue;
				}
				for (std::uint32_t i = 0; i < objective->numTargets; ++i) {
					if (objective->targets[i] == a_target) {
						return objective;
					}
				}
			}
			return nullptr;
		}

		// The reference a target is on now, by Skyrim's own reckoning.
		RE::NiPointer<RE::TESObjectREFR> RefOf(RE::TESQuest* a_quest, RE::TESQuestTarget* a_target)
		{
			RE::ObjectRefHandle tracking;
			a_target->GetTrackingRef(tracking, a_quest);
			auto ref = tracking.get();
			if (!ref) {
				RE::ObjectRefHandle direct;
				a_target->GetTargetRef(direct, true, a_quest);
				ref = direct.get();
			}
			return ref;
		}
	}

	void Update(RE::PlayerCharacter* a_player, bool a_minecraftThere, bool a_loading, std::uint32_t a_worldId, float a_delta)
	{
		static float       timer = 0.0f;
		static bool        empty = true;  // the table holds nothing now
		static int         logged = 0;
		static std::size_t lastCount = SIZE_MAX, lastSkipped = SIZE_MAX;
		timer -= a_delta;
		if (timer > 0.0f) {
			return;
		}
		timer = kRefreshSeconds;
		auto& link = Link::Get();
		if (!Game::FeatureOn(proto::kFeatQuestMarkers) || !a_minecraftThere || a_loading || !a_player || a_worldId == 0) {
			if (!empty) {
				link.WriteQuestTargets(nullptr, 0, a_worldId);
				empty = true;
				lastCount = lastSkipped = SIZE_MAX;
			}
			return;
		}

		std::vector<Seen> seen;
		{
			auto&               data = a_player->GetPlayerRuntimeData();
			RE::BSSpinLockGuard lock(data.questTargetsLock);
			for (auto& entry : data.questTargets) {
				auto* quest = entry.first;
				auto* targets = entry.second;
				if (!quest || !targets) {
					continue;
				}
				for (auto* target : *targets) {
					if (!target) {
						continue;
					}
					Seen s{ quest, target, {} };
					for (const auto& step : target->teleportPath.teleportRefs) {
						if (step.ref) {
							s.doors.push_back(step.ref->GetFormID());
						}
					}
					seen.push_back(std::move(s));
				}
			}
		}

		const auto                            here = SkyToMc(a_player->GetPosition());
		std::vector<proto::QuestTargetRecord> records;
		std::vector<QuestTargets::Candidate>  candidates;
		std::size_t                           skipped = 0, tracked = 0;
		for (const auto& s : seen) {
			const auto ref = RefOf(s.quest, s.target);
			if (!ref || ref.get() == a_player) {
				continue;  // nothing there now, or something the player carries
			}
			RE::TESObjectREFR* at = nullptr;
			std::uint8_t       flags = 0;
			if (WorldOf(ref.get()) == a_worldId) {
				at = ref.get();
			} else {
				for (const auto door : s.doors) {
					auto* ref2 = RE::TESForm::LookupByID<RE::TESObjectREFR>(door);
					if (ref2 && WorldOf(ref2) == a_worldId) {
						at = ref2;
						flags |= proto::kQuestViaDoor;
						break;
					}
				}
			}
			if (!at) {
				++skipped;
				continue;
			}
			const auto p = SkyToMc(at->GetPosition());
			const double dx = p.x - here.x, dy = p.y - here.y, dz = p.z - here.z;
			const double distanceSq = dx * dx + dy * dy + dz * dz;
			if (distanceSq < kOnPlayerBlocks * kOnPlayerBlocks) {
				continue;
			}
			bool twice = false;
			for (const auto& r : records) {
				twice = twice || (r.quest == s.quest->GetFormID() && r.ref == at->GetFormID());
			}
			if (twice) {
				continue;  // two of the quest's targets on one reference: one marker
			}
			if (s.quest->IsActive()) {
				flags |= proto::kQuestTracked;
				++tracked;
			}
			if (const auto* cell = CellOf(ref.get()); cell && cell->IsInteriorCell()) {
				flags |= proto::kQuestInside;
			}
			if (s.quest->GetType() == RE::QUEST_DATA::Type::kMainQuest) {
				flags |= proto::kQuestMain;
			}
			if (at->As<RE::Actor>()) {
				flags |= proto::kQuestActor;
			}
			const auto*              objective = ObjectiveOf(s.quest, s.target);
			const char*              name = s.quest->GetName();
			const char*              text = objective ? objective->displayText.c_str() : "";
			proto::QuestTargetRecord r{};
			r.x = static_cast<float>(p.x);
			r.y = static_cast<float>(p.y);
			r.z = static_cast<float>(p.z);
			r.quest = s.quest->GetFormID();
			r.ref = at->GetFormID();
			r.objective = objective ? objective->index : proto::kQuestNoObjective;
			r.flags = flags;
			r.questType = static_cast<std::uint8_t>(s.quest->GetType());
			QuestTargets::CopyCut(r.name, sizeof(r.name), name ? name : "");
			QuestTargets::CopyCut(r.objectiveText, sizeof(r.objectiveText), text ? text : "");
			candidates.push_back({ (flags & proto::kQuestTracked) != 0, distanceSq, records.size() });
			records.push_back(r);
		}

		std::vector<proto::QuestTargetRecord> out;
		for (const auto i : QuestTargets::Order(std::move(candidates), proto::kMaxQuestTargets)) {
			out.push_back(records[i]);
		}
		link.WriteQuestTargets(out.data(), static_cast<std::uint32_t>(out.size()), a_worldId);
		empty = out.empty();
		if ((out.size() != lastCount || skipped != lastSkipped) && logged < 20) {
			++logged;
			logger::info("SkyCraft-quests: {} quest markers for Minecraft's locator bar ({} of {} targets tracked){}{}", out.size(), tracked, records.size(),
				out.empty() ? std::string() : std::format(", the first {}", std::string_view(out.front().name)),
				skipped ? std::format("; {} in another world with no door Skyrim has worked out to them, left out", skipped) : std::string());
		}
		lastCount = out.size();
		lastSkipped = skipped;
	}
}
