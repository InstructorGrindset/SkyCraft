#pragma once

// What kind of weather a Skyrim weather is, and which of a region's weathers comes nearest to one Minecraft asks for
// (Weather sync). Pure, no game: it reads only a weather's classification flags (TESWeather DATA), whether it has a
// thunder sound and its chance in the region's list, so it's unit tested on its own (tests/WeatherKindsTest.cpp).
// Weather.cpp feeds it Skyrim's loaded weathers and never names a weather by its form id.

#include "skycraft_protocol.h"

#include <cstddef>
#include <cstdint>
#include <span>

namespace skycraft::WeatherKinds
{
	// RE::TESWeather::WeatherDataFlag's bits, as TESWeather::Data::flags holds them.
	inline constexpr std::uint8_t kPleasant = 1u << 0;
	inline constexpr std::uint8_t kCloudy = 1u << 1;
	inline constexpr std::uint8_t kRainy = 1u << 2;
	inline constexpr std::uint8_t kSnow = 1u << 3;

	// The proto::WeatherKind of a weather with these classification flags. Rain or snow with a thunder sound is a
	// thunderstorm; snow wins over rain (a few weathers carry both); a weather Skyrim gave no classification (fog, the
	// DLCs' skies) counts as cloudy.
	constexpr std::uint8_t Classify(std::uint8_t a_flags, bool a_thunder)
	{
		if ((a_flags & (kRainy | kSnow)) != 0) {
			if (a_thunder) {
				return proto::kWeatherStorm;
			}
			return (a_flags & kSnow) != 0 ? proto::kWeatherSnow : proto::kWeatherRain;
		}
		if ((a_flags & kPleasant) != 0) {
			return proto::kWeatherClear;
		}
		return proto::kWeatherCloudy;
	}

	// How well a weather of kind a_have stands in for a_want: 0 is the very kind, higher is a worse stand-in, -1 never
	// (a dry weather for rain, a wet one for clear skies). Rain falls back to snow (a cold region's rain is its snow), then
	// a storm; a storm to rain, then snow; clear to cloudy.
	constexpr int Rank(std::uint8_t a_want, std::uint8_t a_have)
	{
		switch (a_want) {
		case proto::kWeatherClear:
			return a_have == proto::kWeatherClear ? 0 : a_have == proto::kWeatherCloudy ? 1 : -1;
		case proto::kWeatherCloudy:
			return a_have == proto::kWeatherCloudy ? 0 : a_have == proto::kWeatherClear ? 1 : -1;
		case proto::kWeatherRain:
			return a_have == proto::kWeatherRain ? 0 : a_have == proto::kWeatherSnow ? 1 : a_have == proto::kWeatherStorm ? 2 : -1;
		case proto::kWeatherSnow:
			return a_have == proto::kWeatherSnow ? 0 : a_have == proto::kWeatherRain ? 1 : a_have == proto::kWeatherStorm ? 2 : -1;
		case proto::kWeatherStorm:
			return a_have == proto::kWeatherStorm ? 0 : a_have == proto::kWeatherRain ? 1 : a_have == proto::kWeatherSnow ? 2 : -1;
		default:
			return -1;
		}
	}

	// One weather a region (or its climate) can have: its kind and its chance in the list.
	struct Candidate
	{
		std::uint8_t  kind;
		std::uint32_t chance;
	};

	// The index of the candidate nearest a_want: the best Rank, then the highest chance, then the first listed. -1 when
	// none will do.
	constexpr int Pick(std::uint8_t a_want, std::span<const Candidate> a_candidates)
	{
		int           best = -1;
		int           bestRank = 0;
		std::uint32_t bestChance = 0;
		for (std::size_t i = 0; i < a_candidates.size(); ++i) {
			const int rank = Rank(a_want, a_candidates[i].kind);
			if (rank < 0) {
				continue;
			}
			if (best < 0 || rank < bestRank || (rank == bestRank && a_candidates[i].chance > bestChance)) {
				best = static_cast<int>(i);
				bestRank = rank;
				bestChance = a_candidates[i].chance;
			}
		}
		return best;
	}

	// A word for the log.
	constexpr const char* Name(std::uint8_t a_kind)
	{
		switch (a_kind) {
		case proto::kWeatherClear:
			return "clear";
		case proto::kWeatherCloudy:
			return "cloudy";
		case proto::kWeatherRain:
			return "rain";
		case proto::kWeatherSnow:
			return "snow";
		case proto::kWeatherStorm:
			return "thunderstorm";
		default:
			return "none";
		}
	}
}
