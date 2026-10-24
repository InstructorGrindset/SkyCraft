#pragma once

#include <algorithm>
#include <cstddef>
#include <cstring>
#include <string_view>
#include <vector>

// Quest markers, Skyrim's half without the game (skse/tests/QuestTargetsTest.cpp): which of the quest targets Skyrim's
// compass has go into the quest table (proto::QuestTargetTable), in what order, and the texts cut to fit their fields.
namespace skycraft::QuestTargets
{
	// One target Quests.cpp found a marker position for.
	struct Candidate
	{
		bool        tracked{ false };   // its quest is active: Skyrim's compass shows it
		double      distanceSq{ 0.0 };  // from the player to the marker, in blocks squared
		std::size_t index{ 0 };         // into the caller's own list
	};

	// The tracked quests' targets first, then the others, each nearest first (ties keep the order they came in), at most
	// a_max of them: the indices of the ones to send, in that order.
	inline std::vector<std::size_t> Order(std::vector<Candidate> a_candidates, std::size_t a_max)
	{
		std::stable_sort(a_candidates.begin(), a_candidates.end(), [](const Candidate& a_l, const Candidate& a_r) {
			if (a_l.tracked != a_r.tracked) {
				return a_l.tracked;
			}
			return a_l.distanceSq < a_r.distanceSq;
		});
		std::vector<std::size_t> out;
		for (const auto& c : a_candidates) {
			if (out.size() >= a_max) {
				break;
			}
			out.push_back(c.index);
		}
		return out;
	}

	// a_text into a_out (a_size bytes), NUL-terminated and cut to fit at the start of a UTF-8 character, so Minecraft never
	// reads half of one. A NUL inside a_text ends it there.
	inline void CopyCut(char* a_out, std::size_t a_size, std::string_view a_text)
	{
		if (!a_out || a_size == 0) {
			return;
		}
		if (const auto nul = a_text.find('\0'); nul != std::string_view::npos) {
			a_text = a_text.substr(0, nul);
		}
		std::size_t n = std::min(a_text.size(), a_size - 1);
		while (n > 0 && n < a_text.size() && (static_cast<unsigned char>(a_text[n]) & 0xC0) == 0x80) {
			--n;  // a_text[n] continues a character: cut before the character it belongs to
		}
		std::memcpy(a_out, a_text.data(), n);
		std::memset(a_out + n, 0, a_size - n);
	}
}
