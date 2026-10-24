#include "Maps.h"

#include "Dig.h"
#include "Game.h"
#include "MapKinds.h"

#include <limits>

namespace skycraft::Maps
{
	namespace
	{
		constexpr float kRefreshSeconds = 2.0f;
		constexpr float kCentreMoveBlocks = 16.0f;  // the table's centre is rewritten once the player has moved this far
		constexpr float kCell = 4096.0f;            // Skyrim units across an exterior cell
		constexpr int   kGatherAgain = 30;          // looks (a minute) before a worldspace's markers are gathered again

		RE::TESWorldSpace*      gatheredWorld = nullptr;  // the worldspace markerRefs are of (forms live as long as the game)
		bool                    gathered = false;
		const void*             lastPlace = nullptr;  // the worldspace the player was in at the last look (null indoors)
		int                     looksSinceGather = 0;
		std::vector<RE::FormID> markerRefs;  // its map marker references
		proto::MapTable         table{};     // what Minecraft sees, written whole
		std::uint32_t           markerVersion = 0;  // never goes back, even over a reset, so Minecraft sees every change
		bool                    dirty = false;
		bool                    written = false;
		float                   refreshTimer = 0.0f;
		int                     logged = 0;

		bool HasMarker(const RE::TESObjectREFR* a_ref)
		{
			return a_ref && a_ref->extraList.HasType<RE::ExtraMapMarker>();
		}

		// Every map marker reference of a worldspace: its persistent references (map markers are persistent, so Skyrim's map
		// shows them wherever the player is) and the markers the player character holds for the map. No reference is kept
		// between frames, only FormIDs, looked up again each time.
		void Gather(RE::PlayerCharacter* a_player, RE::TESWorldSpace* a_world)
		{
			markerRefs.clear();
			std::unordered_set<RE::FormID> seen;
			auto add = [&](const RE::TESObjectREFR* a_ref) {
				if (HasMarker(a_ref) && seen.insert(a_ref->GetFormID()).second) {
					markerRefs.push_back(a_ref->GetFormID());
				}
			};
			std::size_t persistent = 0;
			if (a_world && a_world->persistentCell) {
				a_world->persistentCell->ForEachReference([&](RE::TESObjectREFR* a_ref) {
					add(a_ref);
					return RE::BSContainer::ForEachResult::kContinue;
				});
				persistent = markerRefs.size();
			}
			for (const auto& handle : a_player->GetPlayerRuntimeData().currentMapMarkers) {
				if (auto ref = handle.get()) {
					add(ref.get());
				}
			}
			static std::size_t lastCount = SIZE_MAX;
			if (a_world != gatheredWorld || markerRefs.size() != lastCount) {
				lastCount = markerRefs.size();
				const char* name = a_world ? a_world->GetFormEditorID() : nullptr;
				logger::info("SkyCraft-maps: {} map markers in {} ({} among its persistent references, {} more the player's map holds)", markerRefs.size(),
					name && *name ? name : "this worldspace", persistent, markerRefs.size() - persistent);
			}
		}

		// The markers Skyrim's map shows near the player, nearest first, into the table (only when they changed, or the
		// player has moved on).
		void ListMarkers(RE::PlayerCharacter* a_player, RE::TESWorldSpace* a_world)
		{
			using Flag = RE::MapMarkerData::Flag;
			const auto here = SkyToMc(a_player->GetPosition());
			static std::vector<MapKinds::Candidate>   candidates;
			static std::vector<proto::MapMarkerRecord> chosen;
			candidates.clear();
			for (const auto id : markerRefs) {
				auto* ref = RE::TESForm::LookupByID<RE::TESObjectREFR>(id);
				auto* extra = ref ? ref->extraList.GetByType<RE::ExtraMapMarker>() : nullptr;
				auto* data = extra ? extra->mapData : nullptr;
				if (!data || ref->IsDisabled() || ref->IsDeleted()) {
					continue;
				}
				const bool visible = data->flags.any(Flag::kVisible), travel = data->flags.any(Flag::kCanTravelTo);
				const auto type = static_cast<std::uint16_t>(data->type.get());
				if (!(visible || travel) || !MapKinds::IsPlace(type)) {
					continue;
				}
				const auto mc = SkyToMc(ref->GetPosition());
				MapKinds::Candidate c{};
				c.distance = static_cast<float>(std::hypot(mc.x - here.x, mc.z - here.z));
				c.record.x = static_cast<float>(mc.x);
				c.record.z = static_cast<float>(mc.z);
				c.record.formId = id;
				c.record.type = type;
				c.record.flags = MapKinds::Flags(visible, travel);
				const char* name = data->locationName.GetFullName();
				strncpy_s(c.record.name, name ? name : "", _TRUNCATE);
				candidates.push_back(c);
			}
			const float reach = MapKinds::Choose(candidates, proto::kMaxMapMarkers, proto::kMapMarkerReach, chosen);
			const auto  worldId = a_world->GetFormID();
			const bool  changed = !MapKinds::Same(table.markers, table.markerCount, chosen.data(), chosen.size()) || table.worldId != worldId;
			const bool  moved = std::hypot(here.x - table.centerX, here.z - table.centerZ) > kCentreMoveBlocks;
			if (!changed && !moved && reach == table.markerReach) {
				return;
			}
			if (changed) {
				table.markerCount = static_cast<std::uint32_t>(chosen.size());
				std::memset(table.markers, 0, sizeof(table.markers));
				if (!chosen.empty()) {
					std::memcpy(table.markers, chosen.data(), sizeof(proto::MapMarkerRecord) * chosen.size());
				}
				table.markerVersion = ++markerVersion;
				if (logged < 10) {
					++logged;
					std::size_t discovered = 0;
					for (const auto& r : chosen) {
						discovered += (r.flags & proto::kMarkerDiscovered) ? 1u : 0u;
					}
					logger::info("SkyCraft-maps: {} map markers within {:.0f} blocks of the player ({} discovered){}{}", chosen.size(), reach, discovered,
						chosen.empty() ? "" : ", the nearest ", chosen.empty() ? "" : std::string_view(chosen.front().name));
				}
			}
			table.worldId = worldId;
			table.centerX = static_cast<float>(here.x);
			table.centerZ = static_cast<float>(here.z);
			table.markerReach = reach;
			dirty = true;
		}

		// No markers: indoors, or between places.
		void ClearMarkers()
		{
			if (table.worldId == 0 && table.markerCount == 0) {
				return;
			}
			table.worldId = 0;
			table.markerCount = 0;
			std::memset(table.markers, 0, sizeof(table.markers));
			table.markerVersion = ++markerVersion;
			dirty = true;
		}

		// The loaded exterior cell at a point (Skyrim units, x y) with its land loaded, or null: the attached cells round the
		// player, as digging finds a cell's land textures.
		RE::TESObjectCELL* LoadedCellAt(RE::TES* a_tes, float a_x, float a_y)
		{
			auto* grid = a_tes->gridCells;
			if (!grid || a_tes->interiorCell) {
				return nullptr;
			}
			const int cx = static_cast<int>(std::floor(a_x / kCell)), cy = static_cast<int>(std::floor(a_y / kCell));
			for (std::uint32_t i = 0; i < grid->length * grid->length; ++i) {
				auto* cell = grid->cells[i];
				auto* ext = cell ? cell->GetCoordinates() : nullptr;
				if (ext && ext->cellX == cx && ext->cellY == cy) {
					auto* land = cell->GetRuntimeData().cellLand;
					return land && land->loadedData ? cell : nullptr;
				}
			}
			return nullptr;
		}

		void Write()
		{
			if (dirty) {
				dirty = false;
				written = true;
				Link::Get().WriteMapTable(table);
			}
		}
	}

	void Update(RE::PlayerCharacter* a_player, float a_delta)
	{
		if (!Game::FeatureOn(proto::kFeatSkyrimMaps)) {
			if (written) {
				// Switched off in Minecraft: nothing listed any more (Minecraft takes the markers off its maps itself).
				table = {};
				table.markerVersion = ++markerVersion;
				dirty = true;
				Write();
				written = false;
				refreshTimer = 0.0f;
				lastPlace = nullptr;
			}
			return;
		}
		refreshTimer -= std::max(a_delta, 0.0f);
		auto* cell = a_player->GetParentCell();
		auto* world = cell && cell->IsExteriorCell() ? a_player->GetWorldspace() : nullptr;
		if (!cell) {
			return;  // between places (loading): the table stands until the player is somewhere
		}
		if (refreshTimer > 0.0f && world == lastPlace) {
			return;  // (somewhere new is looked at at once)
		}
		lastPlace = world;
		refreshTimer = kRefreshSeconds;
		if (!world) {
			ClearMarkers();
		} else {
			// Gathered again now and then: a marker a mod adds, or one not loaded yet at the first look after a load.
			if (world != gatheredWorld || !gathered || ++looksSinceGather >= kGatherAgain) {
				Gather(a_player, world);
				gatheredWorld = world;
				gathered = true;
				looksSinceGather = 0;
			}
			ListMarkers(a_player, world);
		}
		Write();
	}

	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
	{
		if (a_event.type != proto::kEvMapSample || a_event.flags == 0 || !Game::FeatureOn(proto::kFeatSkyrimMaps)) {
			return;
		}
		const int  spacing = std::clamp(static_cast<int>(std::lround(a_event.b)), 1, 256);
		const auto x0 = static_cast<std::int32_t>(std::floor(a_event.a)), z0 = static_cast<std::int32_t>(std::floor(a_event.c));
		auto*      tes = RE::TES::GetSingleton();
		auto*      cell = a_player->GetParentCell();
		auto*      world = cell && cell->IsExteriorCell() ? a_player->GetWorldspace() : nullptr;
		const auto playerZ = a_player->GetPosition().z;
		std::uint32_t known = 0;
		for (std::uint32_t j = 0; j < proto::kMapSampleSide; ++j) {
			for (std::uint32_t i = 0; i < proto::kMapSampleSide; ++i) {
				auto& sample = table.samples[j * proto::kMapSampleSide + i];
				sample = MapKinds::Unknown();
				if (!tes || !world) {
					continue;
				}
				// The middle of the block column the sample stands for.
				auto pos = McToSky(x0 + double(i) * spacing + 0.5, 0.0, z0 + double(j) * spacing + 0.5);
				pos.z = playerZ;
				auto* landCell = LoadedCellAt(tes, pos.x, pos.y);
				float land = 0.0f;
				if (!landCell || !tes->GetLandHeight(pos, land) || !std::isfinite(land)) {
					continue;
				}
				const auto material = Dig::MaterialFor(Dig::LandMaterialAt(pos.x, pos.y), nullptr, false);
				// The cell's water there (its water height, or a lake's or river's), as the water grid round the player reads it.
				float      water = -std::numeric_limits<float>::max();
				RE::NiPoint3 at{ pos.x, pos.y, land + 1.0f };
				landCell->GetWaterHeight(at, water);
				const bool hasWater = std::isfinite(water) && water > -1.0e6f;
				sample = MapKinds::Sample(land / proto::kUnitsPerBlock, material, water / proto::kUnitsPerBlock, hasWater);
				++known;
			}
		}
		table.sampleRequest = a_event.flags;
		table.sampleX = x0;
		table.sampleZ = z0;
		table.sampleSpacing = static_cast<std::uint32_t>(spacing);
		table.sampleWorld = world ? world->GetFormID() : 0;
		table.sampleKnown = known;
		dirty = true;
		Write();
		static int answered = 0;
		if (answered < 10) {
			++answered;
			logger::info("SkyCraft-maps: land under a Minecraft map sampled from {} {} every {} blocks: {} of {} in loaded cells", x0, z0, spacing, known,
				proto::kMapSampleSide * proto::kMapSampleSide);
		}
	}

	void Reset()
	{
		gatheredWorld = nullptr;
		gathered = false;
		lastPlace = nullptr;
		markerRefs.clear();
		refreshTimer = 0.0f;
		const bool had = written;
		table = {};
		table.markerVersion = ++markerVersion;
		if (had) {
			dirty = true;
			Write();
		}
	}
}
