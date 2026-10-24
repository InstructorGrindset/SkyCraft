// Tests for MapKinds.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src -I protocol skse/tests/MapKindsTest.cpp -o map_kinds_test && ./map_kinds_test
#include "MapKinds.h"

#include <cstdio>
#include <cstring>

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

	MapKinds::Candidate Marker(std::uint32_t a_formId, float a_distance)
	{
		MapKinds::Candidate c{};
		c.distance = a_distance;
		c.record.formId = a_formId;
		c.record.x = a_distance;
		c.record.type = proto::kMarkerCave;
		c.record.flags = MapKinds::Flags(true, false);
		std::strncpy(c.record.name, "Somewhere", sizeof(c.record.name) - 1);
		return c;
	}

	void Places()
	{
		CHECK(MapKinds::IsPlace(proto::kMarkerCity));
		CHECK(MapKinds::IsPlace(proto::kMarkerCastleKarstaag));
		CHECK(!MapKinds::IsPlace(proto::kMarkerTypeCount));
		CHECK(!MapKinds::IsPlace(61));  // a door
		CHECK(!MapKinds::IsPlace(64));  // the player's own marker
		CHECK(MapKinds::Flags(true, false) == proto::kMarkerVisible);
		CHECK(MapKinds::Flags(true, true) == (proto::kMarkerVisible | proto::kMarkerDiscovered));
		CHECK(MapKinds::Flags(false, false) == 0);
	}

	void Choosing()
	{
		std::vector<MapKinds::Candidate> all{ Marker(3, 300.0f), Marker(1, 10.0f), Marker(4, 2000.0f), Marker(2, 50.0f) };
		std::vector<proto::MapMarkerRecord> out;
		float reach = MapKinds::Choose(all, 48, proto::kMapMarkerReach, out);
		CHECK(out.size() == 3);  // the one 2000 blocks away is past the reach
		CHECK(out[0].formId == 1 && out[1].formId == 2 && out[2].formId == 3);
		CHECK(reach == proto::kMapMarkerReach);

		// More than fit: the nearest, and the reach says where the list stops being complete.
		std::vector<MapKinds::Candidate> many;
		for (std::uint32_t i = 0; i < 60; ++i) {
			many.push_back(Marker(100 + i, 10.0f * float(i)));
		}
		reach = MapKinds::Choose(many, proto::kMaxMapMarkers, proto::kMapMarkerReach, out);
		CHECK(out.size() == proto::kMaxMapMarkers);
		CHECK(out.back().formId == 100 + proto::kMaxMapMarkers - 1);
		CHECK(reach == 10.0f * float(proto::kMaxMapMarkers));  // the nearest one left out
	}

	void Sameness()
	{
		std::vector<MapKinds::Candidate> all{ Marker(1, 10.0f), Marker(2, 20.0f) };
		std::vector<proto::MapMarkerRecord> a, b;
		MapKinds::Choose(all, 48, 1024.0f, a);
		all = { Marker(1, 10.0f), Marker(2, 20.0f) };
		MapKinds::Choose(all, 48, 1024.0f, b);
		CHECK(MapKinds::Same(a.data(), a.size(), b.data(), b.size()));
		b[1].flags = MapKinds::Flags(true, true);  // discovered since
		CHECK(!MapKinds::Same(a.data(), a.size(), b.data(), b.size()));
		CHECK(!MapKinds::Same(a.data(), a.size(), b.data(), 1));
		CHECK(MapKinds::Same(nullptr, 0, nullptr, 0));
	}

	void Samples()
	{
		const auto land = MapKinds::Sample(64.3, proto::kDigGrass, 0.0, false);
		CHECK(land.height == 257);  // 64.3 x 4, rounded
		CHECK(land.material == proto::kDigGrass);
		CHECK(land.water == 0);
		const auto lake = MapKinds::Sample(55.0, proto::kDigMud, 63.9, true);
		CHECK(lake.height == 256);  // the water's surface: 63.9 x 4 = 255.6
		CHECK(lake.water == 9);
		CHECK(lake.material == proto::kDigMud);
		const auto dry = MapKinds::Sample(70.0, proto::kDigDirt, 63.9, true);  // the cell's water is under this land
		CHECK(dry.height == 280 && dry.water == 0);
		const auto puddle = MapKinds::Sample(63.95, proto::kDigDirt, 64.0, true);  // too shallow to be water on a map
		CHECK(puddle.water == 0);
		const auto deep = MapKinds::Sample(-1000.0, proto::kDigSand, 0.0, true);
		CHECK(deep.water == 255);
		CHECK(MapKinds::QuarterBlocks(-1.0e9) == -32767);  // never kSampleUnknown
		CHECK(MapKinds::QuarterBlocks(1.0e9) == 32767);
		CHECK(MapKinds::Unknown().height == proto::kSampleUnknown);
	}
}

int main()
{
	Places();
	Choosing();
	Sameness();
	Samples();
	if (failures) {
		std::printf("%d check(s) failed\n", failures);
		return 1;
	}
	std::printf("MapKinds: all checks passed\n");
	return 0;
}
