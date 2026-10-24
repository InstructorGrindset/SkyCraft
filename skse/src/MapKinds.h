#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

#include "skycraft_protocol.h"

// Skyrim maps, Skyrim's half without the game (skse/tests/MapKindsTest.cpp): which map markers go into the map table
// (proto::MapTable), and how a land sample is written there, the way Java's maps.SkyMapLink and maps.LandSamples read them.
namespace skycraft::MapKinds
{
	// A marker Skyrim's map shows, with how far it is from the player (blocks, along the ground).
	struct Candidate
	{
		float                  distance;
		proto::MapMarkerRecord record;
	};

	// A marker type that's a place (not a door, a quest target or the player's own marker).
	inline bool IsPlace(std::uint16_t a_type)
	{
		return a_type < proto::kMarkerTypeCount;
	}

	// proto::MapMarkerFlags from the marker's MapMarkerData flags.
	inline std::uint8_t Flags(bool a_visible, bool a_canTravelTo)
	{
		return static_cast<std::uint8_t>((a_visible ? proto::kMarkerVisible : 0) | (a_canTravelTo ? proto::kMarkerDiscovered : 0));
	}

	// The nearest a_max candidates closer than a_reach into a_out, nearest first. Returns how far the list reaches: a_reach,
	// or, when some had to be left out, the distance of the nearest one left out, so that every marker closer than what's
	// returned is in the list (Minecraft takes a marker off a map only when it's that close and not listed).
	inline float Choose(std::vector<Candidate>& a_candidates, std::size_t a_max, float a_reach, std::vector<proto::MapMarkerRecord>& a_out)
	{
		a_out.clear();
		std::erase_if(a_candidates, [&](const Candidate& a_c) { return !(a_c.distance < a_reach); });
		std::ranges::stable_sort(a_candidates, {}, &Candidate::distance);
		float reach = a_reach;
		for (std::size_t i = 0; i < a_candidates.size(); ++i) {
			if (a_out.size() >= a_max) {
				reach = a_candidates[i].distance;
				break;
			}
			a_out.push_back(a_candidates[i].record);
		}
		return reach;
	}

	// The same markers, the same way round (markerVersion moves only when they change).
	inline bool Same(const proto::MapMarkerRecord* a_a, std::size_t a_countA, const proto::MapMarkerRecord* a_b, std::size_t a_countB)
	{
		return a_countA == a_countB && (a_countA == 0 || std::memcmp(a_a, a_b, sizeof(proto::MapMarkerRecord) * a_countA) == 0);
	}

	// A height for proto::MapSample: MC y x 4, rounded, kept off kSampleUnknown.
	inline std::int16_t QuarterBlocks(double a_y)
	{
		const double q = std::round(a_y * 4.0);
		return static_cast<std::int16_t>(std::clamp(q, -32767.0, 32767.0));
	}

	// A sample nobody knows (not in a loaded cell).
	inline proto::MapSample Unknown()
	{
		return { proto::kSampleUnknown, proto::kDigNone, 0 };
	}

	// A sample of the land at MC height a_landY, made of a_material, under Skyrim's water at a_waterY when a_hasWater (and
	// that's above the land): the water's surface is the height then, with the blocks of water under it.
	inline proto::MapSample Sample(double a_landY, std::uint8_t a_material, double a_waterY, bool a_hasWater)
	{
		proto::MapSample s{ QuarterBlocks(a_landY), a_material, 0 };
		if (a_hasWater && std::isfinite(a_waterY) && a_waterY > a_landY + 0.125) {
			s.height = QuarterBlocks(a_waterY);
			s.water = static_cast<std::uint8_t>(std::clamp(std::lround(a_waterY - a_landY), 1l, 255l));
		}
		return s;
	}
}
