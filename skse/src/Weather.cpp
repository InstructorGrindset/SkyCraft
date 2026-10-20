#include "WorldSync.h"

#include "Game.h"
#include "WeatherKinds.h"

// Weather both ways (switch weather_sync). Skyrim tells Minecraft what its sky is doing where the player is, and
// Minecraft's weather follows it (fabric weather.SkyWeather). When the weather is changed on Minecraft's side (a
// player's /weather, a mod), Minecraft asks for a weather of that kind through McState (a sequence number and the kind),
// and Skyrim switches to the nearest one its current region has, the way Papyrus's Weather.ForceActive does, never by a
// guessed form id. Only Minecraft ever asks and Skyrim acknowledges each request once (SkyState::weatherAck), so the two
// never take turns changing each other's weather.
namespace skycraft::Weather
{
	namespace
	{
		bool HasThunder(RE::TESWeather* a_weather)
		{
			for (auto* sound : a_weather->sounds) {
				if (sound && sound->type == RE::TESWeather::SoundType::kThunder) {
					return true;
				}
			}
			return false;
		}

		std::uint8_t KindOf(RE::TESWeather* a_weather)
		{
			return a_weather ? WeatherKinds::Classify(a_weather->data.flags.underlying(), HasThunder(a_weather)) : proto::kWeatherNone;
		}

		// The weathers the player's region can have (its weather data), else its climate's, each with its kind and chance.
		void Candidates(RE::Sky* a_sky, std::vector<RE::TESWeather*>& a_weathers, std::vector<WeatherKinds::Candidate>& a_out)
		{
			auto add = [&](const RE::BSSimpleList<RE::WeatherType*>& a_list) {
				for (auto* type : a_list) {
					if (type && type->weather) {
						a_weathers.push_back(type->weather);
						a_out.push_back({ KindOf(type->weather), type->chance });
					}
				}
			};
			if (auto* region = a_sky->region; region && region->dataList) {
				for (auto* data : region->dataList->regionDataList) {
					if (data && data->GetType() == RE::TESRegionData::Type::kWeather) {
						add(static_cast<RE::TESRegionDataWeather*>(data)->weatherTypes);
					}
				}
			}
			if (a_out.empty() && a_sky->currentClimate) {
				add(a_sky->currentClimate->weatherList);
			}
		}

		int logged = 0;

		// Minecraft asked for a weather of kind a_want: true once Skyrim has one of that kind (or the nearest its region
		// has), false when it can't change its weather now.
		bool Switch(RE::Sky* a_sky, std::uint8_t a_want, bool a_indoors)
		{
			const bool log = logged < 40;
			logged += log ? 1 : 0;
			if (a_indoors || !a_sky) {
				if (log) {
					logger::info("SkyCraft-weather: Minecraft asked for {}, but the player is indoors: Skyrim's weather stays", WeatherKinds::Name(a_want));
				}
				return false;
			}
			if (a_sky->overrideWeather) {
				if (log) {
					logger::info("SkyCraft-weather: Minecraft asked for {}, but a quest holds Skyrim's weather ({:08X})", WeatherKinds::Name(a_want),
						a_sky->overrideWeather->GetFormID());
				}
				return false;
			}
			if (KindOf(a_sky->currentWeather) == a_want) {
				return true;  // it already is
			}
			std::vector<RE::TESWeather*>          weathers;
			std::vector<WeatherKinds::Candidate> candidates;
			Candidates(a_sky, weathers, candidates);
			const int pick = WeatherKinds::Pick(a_want, candidates);
			if (pick < 0) {
				if (log) {
					logger::info("SkyCraft-weather: Minecraft asked for {}, but none of the {} weathers of this region will do", WeatherKinds::Name(a_want),
						candidates.size());
				}
				return false;
			}
			auto* weather = weathers[static_cast<std::size_t>(pick)];
			if (weather != a_sky->currentWeather) {
				a_sky->ForceWeather(weather, false);  // at once, as /weather is, and the region's own weather takes over again in time
			}
			if (log) {
				logger::info("SkyCraft-weather: Minecraft asked for {}: Skyrim's weather is now {:08X} ({}, {} of {} in the region)", WeatherKinds::Name(a_want),
					weather->GetFormID(), WeatherKinds::Name(candidates[static_cast<std::size_t>(pick)].kind), pick + 1, candidates.size());
			}
			return true;
		}
	}

	void Update(RE::PlayerCharacter* a_player, const proto::McState* a_mc, proto::SkyState& a_sky)
	{
		static std::uint32_t handled = 0;  // the last McState::weatherSeq answered
		static bool          refused = false;
		static std::uint8_t  lastKind = 0xFF;
		auto*                sky = RE::Sky::GetSingleton();
		auto*                cell = a_player ? a_player->GetParentCell() : nullptr;
		const bool           indoors = !cell || cell->IsInteriorCell() || !sky;

		// Minecraft's request first, so this frame already says what Skyrim's sky is now.
		if (a_mc && a_mc->weatherSeq != handled) {
			if (a_mc->weatherWant == proto::kWeatherNone) {
				refused = false;  // a request answered before (Skyrim or Minecraft restarted): nothing to do
			} else if (!Game::FeatureOn(proto::kFeatWeatherSync)) {
				refused = true;
			} else {
				refused = !Switch(sky, a_mc->weatherWant, indoors);
			}
			handled = a_mc->weatherSeq;
		}

		a_sky.weather = indoors ? proto::kWeatherNone : KindOf(sky->currentWeather);
		a_sky.weatherFlags = static_cast<std::uint8_t>((!indoors && sky->IsRaining() ? proto::kWeatherRaining : 0) |
													   (!indoors && sky->IsSnowing() ? proto::kWeatherSnowing : 0) | (refused ? proto::kWeatherRefused : 0));
		a_sky.weatherAck = handled;
		if (a_sky.weather != lastKind) {
			if (logged < 40 && a_sky.weather != proto::kWeatherNone) {
				++logged;
				logger::info("SkyCraft-weather: Skyrim's weather is {} ({:08X})", WeatherKinds::Name(a_sky.weather), sky->currentWeather->GetFormID());
			}
			lastKind = a_sky.weather;
		}
	}
}
