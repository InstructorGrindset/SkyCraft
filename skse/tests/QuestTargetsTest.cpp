// Tests for QuestTargets.h (pure, no game): build with -DSKYCRAFT_TESTS=ON and run ctest, or directly:
//   g++ -std=c++20 -I skse/src -I protocol skse/tests/QuestTargetsTest.cpp -o quest_targets_test && ./quest_targets_test
#include "QuestTargets.h"

#include "skycraft_protocol.h"

#include <cstdio>
#include <string>

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

	void TrackedFirstThenNearest()
	{
		const std::vector<QuestTargets::Candidate> found{
			{ false, 25.0, 0 },   // an untracked quest's, near
			{ true, 900.0, 1 },   // tracked, far
			{ true, 100.0, 2 },   // tracked, nearer
			{ false, 4.0, 3 },    // untracked, nearest of all
			{ true, 100.0, 4 },   // tracked, as near as 2: after it, as it came after it
		};
		CHECK((QuestTargets::Order(found, 16) == std::vector<std::size_t>{ 2, 4, 1, 3, 0 }));
		CHECK((QuestTargets::Order(found, 2) == std::vector<std::size_t>{ 2, 4 }));
		CHECK(QuestTargets::Order(found, 0).empty());
		CHECK(QuestTargets::Order({}, 16).empty());
	}

	void AtMostTheTablesWorth()
	{
		std::vector<QuestTargets::Candidate> many;
		for (std::size_t i = 0; i < 40; ++i) {
			many.push_back({ i % 2 == 0, double(40 - i), i });
		}
		const auto order = QuestTargets::Order(many, proto::kMaxQuestTargets);
		CHECK(order.size() == proto::kMaxQuestTargets);
		for (const auto i : order) {
			CHECK(i % 2 == 0);  // twenty tracked ones, so no untracked one gets in
		}
		CHECK(order.front() == 38);  // the nearest tracked one
	}

	void CutsToFit()
	{
		char out[8];
		QuestTargets::CopyCut(out, sizeof(out), "Dragon Rising");
		CHECK(std::string(out) == "Dragon ");
		QuestTargets::CopyCut(out, sizeof(out), "Unbound");
		CHECK(std::string(out) == "Unbound");
		CHECK(out[7] == '\0');
		// "Ölfjaä" is 8 bytes of UTF-8 and 7 fit: cut before the "ä" (2 bytes), which would only half fit.
		QuestTargets::CopyCut(out, sizeof(out), "\xC3\x96lfja\xC3\xA4");
		CHECK(std::string(out) == "\xC3\x96lfja");
		QuestTargets::CopyCut(out, sizeof(out), "\xC3\x96lfj\xC3\xA4r");
		CHECK(std::string(out) == "\xC3\x96lfj\xC3\xA4");
		QuestTargets::CopyCut(out, sizeof(out), std::string_view("A\0B", 3));
		CHECK(std::string(out) == "A" && out[1] == '\0' && out[2] == '\0');
		QuestTargets::CopyCut(out, sizeof(out), "");
		CHECK(out[0] == '\0');
		// A three byte character that can't fit is dropped whole.
		char small[4];
		QuestTargets::CopyCut(small, sizeof(small), "a\xE2\x80\xA6");
		CHECK(std::string(small) == "a");
		QuestTargets::CopyCut(small, 0, "never written");
	}

	void FitsTheTable()
	{
		proto::QuestTargetRecord r{};
		const std::string longName(100, 'x');
		QuestTargets::CopyCut(r.name, sizeof(r.name), longName);
		CHECK(std::string(r.name).size() == proto::kQuestNameBytes - 1);
		QuestTargets::CopyCut(r.objectiveText, sizeof(r.objectiveText), "Retrieve the Dragonstone from Bleak Falls Barrow");
		CHECK(std::string(r.objectiveText) == "Retrieve the Dragonstone from Bleak Fal");
	}
}

int main()
{
	TrackedFirstThenNearest();
	AtMostTheTablesWorth();
	CutsToFit();
	FitsTheTable();
	if (failures) {
		std::printf("%d failed\n", failures);
		return 1;
	}
	std::printf("QuestTargets: all passed\n");
	return 0;
}
