// Tests for BackupNames.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src skse/tests/BackupNamesTest.cpp -o backup_names_test && ./backup_names_test
#include "BackupNames.h"

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

	void Stamps()
	{
		std::tm t{};
		t.tm_year = 2026 - 1900;
		t.tm_mon = 9;
		t.tm_mday = 17;
		t.tm_hour = 15;
		t.tm_min = 45;
		t.tm_sec = 3;
		CHECK(BackupNames::FormatStamp(t) == "2026-10-17_15-45-03");
		CHECK(BackupNames::IsStamp("2026-10-17_15-45-03"));
		CHECK(!BackupNames::IsStamp("2026-10-17 15-45-03"));
		CHECK(!BackupNames::IsStamp("2026-10-17_15-45-03.partial"));
		CHECK(!BackupNames::IsStamp("Saves"));
	}

	void SaveNames()
	{
		CHECK(BackupNames::SaveBase("Save12_ABCD_0_4C79646961_Tamriel_000123_20261017154523_10_1") == "Save12_ABCD_0_4C79646961_Tamriel_000123_20261017154523_10_1");
		CHECK(BackupNames::SaveBase("quicksave.ess") == "quicksave");
		CHECK(BackupNames::SaveBase("Autosave1.ESS") == "Autosave1");
		CHECK(BackupNames::SaveBase(".ess") == ".ess");
	}

	void KeepsTheNewestPairs()
	{
		const std::vector<std::string> names{ "2026-10-19_08-00-00", "2026-10-17_10-00-00", "2026-10-18_12-30-00", "2026-10-16_09-00-00.partial",
			"readme.txt", "2026-10-15_00-00-00 notes" };
		CHECK((BackupNames::ToDelete(names, 2) == std::vector<std::string>{ "2026-10-16_09-00-00.partial", "2026-10-17_10-00-00" }));
		CHECK((BackupNames::ToDelete(names, 3) == std::vector<std::string>{ "2026-10-16_09-00-00.partial" }));
		CHECK((BackupNames::ToDelete(names, 0) == std::vector<std::string>{ "2026-10-16_09-00-00.partial" }));
		CHECK((BackupNames::ToDelete(names, 1) == std::vector<std::string>{ "2026-10-16_09-00-00.partial", "2026-10-17_10-00-00", "2026-10-18_12-30-00" }));
		CHECK(BackupNames::ToDelete({}, 1).empty());
	}

	// The same events Java's BackupRequestTest.theBytesSkyrimSends reads back.
	void EncodesAsMinecraftReadsIt()
	{
		const auto events = BackupNames::Encode(7, "2026-10-17_15-45-23", "Save 3");
		CHECK(events.size() == 4);
		CHECK(events[0].code == 0 && events[0].a == 7 && events[0].b == 26 && events[0].c == 0);
		CHECK(events[1].code == 1 && events[1].a == 0x36323032 && events[1].b == 0x2D30312D && events[1].c == 0x315F3731);
		CHECK(events[2].code == 2 && events[2].a == 0x35342D35 && events[2].b == 0x7C33322D && events[2].c == 0x65766153);
		CHECK(events[3].code == 3 && events[3].a == 0x3320 && events[3].b == 0 && events[3].c == 0);
		// High bytes (UTF-8) stay bytes, and a long name is cut at the limit.
		const auto utf8 = BackupNames::Encode(1, "2026-10-17_15-45-23", "\xC3\x86rin");
		CHECK(utf8[0].b == 25 && (static_cast<std::uint32_t>(utf8[2].c) & 0xFF) == 0xC3);  // byte 20, the first of the name
		const auto longOne = BackupNames::Encode(1, "2026-10-17_15-45-23", std::string(600, 'x'));
		CHECK(longOne[0].b == static_cast<std::int32_t>(BackupNames::kTextMax));
		CHECK(longOne.size() == 1 + (BackupNames::kTextMax + 11) / 12);
	}
}

int main()
{
	Stamps();
	SaveNames();
	KeepsTheNewestPairs();
	EncodesAsMinecraftReadsIt();
	if (failures) {
		std::printf("%d check(s) failed\n", failures);
		return 1;
	}
	std::printf("BackupNames: all checks passed\n");
	return 0;
}
