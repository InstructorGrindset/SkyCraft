// Tests for WeatherKinds.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src -I protocol skse/tests/WeatherKindsTest.cpp -o weather_kinds_test && ./weather_kinds_test
#include "WeatherKinds.h"

#include <array>
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
	using namespace skycraft::WeatherKinds;

	void Classifies()
	{
		CHECK(Classify(kPleasant, false) == proto::kWeatherClear);
		CHECK(Classify(kCloudy, false) == proto::kWeatherCloudy);
		CHECK(Classify(kRainy, false) == proto::kWeatherRain);
		CHECK(Classify(kSnow, false) == proto::kWeatherSnow);
		CHECK(Classify(kRainy, true) == proto::kWeatherStorm);
		CHECK(Classify(kSnow, true) == proto::kWeatherStorm);
		// Snow wins over rain; a wet flag over a dry one.
		CHECK(Classify(kRainy | kSnow, false) == proto::kWeatherSnow);
		CHECK(Classify(kCloudy | kRainy, false) == proto::kWeatherRain);
		CHECK(Classify(kPleasant | kCloudy, false) == proto::kWeatherClear);
		// Fog and the DLCs' skies have no classification: cloudy. A thunder sound alone doesn't make a storm.
		CHECK(Classify(0, false) == proto::kWeatherCloudy);
		CHECK(Classify(0, true) == proto::kWeatherCloudy);
		CHECK(Classify(kPleasant, true) == proto::kWeatherClear);
		// The aurora bits mean nothing here.
		CHECK(Classify(kPleasant | 0x30, false) == proto::kWeatherClear);
	}

	void Ranks()
	{
		CHECK(Rank(proto::kWeatherClear, proto::kWeatherClear) == 0);
		CHECK(Rank(proto::kWeatherClear, proto::kWeatherCloudy) == 1);
		CHECK(Rank(proto::kWeatherClear, proto::kWeatherRain) < 0);
		CHECK(Rank(proto::kWeatherClear, proto::kWeatherStorm) < 0);
		CHECK(Rank(proto::kWeatherRain, proto::kWeatherRain) == 0);
		CHECK(Rank(proto::kWeatherRain, proto::kWeatherSnow) == 1);
		CHECK(Rank(proto::kWeatherRain, proto::kWeatherStorm) == 2);
		CHECK(Rank(proto::kWeatherRain, proto::kWeatherClear) < 0);
		CHECK(Rank(proto::kWeatherStorm, proto::kWeatherRain) == 1);
		CHECK(Rank(proto::kWeatherStorm, proto::kWeatherCloudy) < 0);
		CHECK(Rank(proto::kWeatherNone, proto::kWeatherClear) < 0);
	}

	void PicksTheNearestOfTheRegions()
	{
		// Whiterun's tundra: clear, cloudy, rain, a storm.
		const std::array<Candidate, 4> tundra{ { { proto::kWeatherClear, 60 }, { proto::kWeatherCloudy, 20 }, { proto::kWeatherRain, 15 }, { proto::kWeatherStorm, 5 } } };
		CHECK(Pick(proto::kWeatherClear, tundra) == 0);
		CHECK(Pick(proto::kWeatherRain, tundra) == 2);
		CHECK(Pick(proto::kWeatherStorm, tundra) == 3);
		// Winterhold: snow only, and clear.
		const std::array<Candidate, 3> winterhold{ { { proto::kWeatherSnow, 40 }, { proto::kWeatherSnow, 50 }, { proto::kWeatherClear, 10 } } };
		CHECK(Pick(proto::kWeatherRain, winterhold) == 1);   // its rain is snow: the likelier one
		CHECK(Pick(proto::kWeatherStorm, winterhold) == 1);  // no storm here: snow
		CHECK(Pick(proto::kWeatherClear, winterhold) == 2);
		// A region that is always overcast: clear skies fall back to cloudy, rain to nothing.
		const std::array<Candidate, 2> overcast{ { { proto::kWeatherCloudy, 10 }, { proto::kWeatherCloudy, 30 } } };
		CHECK(Pick(proto::kWeatherClear, overcast) == 1);
		CHECK(Pick(proto::kWeatherRain, overcast) == -1);
		// Nothing listed: nothing picked.
		CHECK(Pick(proto::kWeatherClear, std::span<const Candidate>{}) == -1);
		// Equal chances: the first listed.
		const std::array<Candidate, 2> twins{ { { proto::kWeatherRain, 10 }, { proto::kWeatherRain, 10 } } };
		CHECK(Pick(proto::kWeatherRain, twins) == 0);
		// A better kind wins over a likelier worse one.
		const std::array<Candidate, 2> mixed{ { { proto::kWeatherSnow, 90 }, { proto::kWeatherRain, 1 } } };
		CHECK(Pick(proto::kWeatherRain, mixed) == 1);
	}
}

int main()
{
	Classifies();
	Ranks();
	PicksTheNearestOfTheRegions();
	if (failures == 0) {
		std::printf("WeatherKindsTest: all passed\n");
	}
	return failures == 0 ? 0 : 1;
}
