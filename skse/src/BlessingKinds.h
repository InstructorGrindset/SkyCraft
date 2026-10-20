#pragma once

// Which shrine's blessing a spell is, by its name (Shrine blessings). Pure, no game: the spell's display name is all it
// reads, so it's unit tested on its own (tests/BlessingKindsTest.cpp). The shrines of the Nine cast spells named
// "Blessing of Akatosh", "Blessing of Arkay", ..., Dawnguard's Auriel one too; any other "Blessing of ..." (the DLCs'
// and mods' shrines) is kBlessingOther. Minecraft's table (blessings.Blessings) decides what each one gives.

#include "skycraft_protocol.h"

#include <cstddef>
#include <string_view>

namespace skycraft::BlessingKinds
{
	// How long a shrine's blessing lasts, in Skyrim's game hours.
	inline constexpr float kBlessingHours = 8.0f;

	constexpr char Lower(char a_c) { return a_c >= 'A' && a_c <= 'Z' ? static_cast<char>(a_c - 'A' + 'a') : a_c; }

	// a_word (lower case) anywhere in a_text, ignoring case.
	constexpr bool Contains(std::string_view a_text, std::string_view a_word)
	{
		if (a_word.size() > a_text.size()) {
			return false;
		}
		for (std::size_t at = 0; at + a_word.size() <= a_text.size(); ++at) {
			std::size_t i = 0;
			while (i < a_word.size() && Lower(a_text[at + i]) == a_word[i]) {
				++i;
			}
			if (i == a_word.size()) {
				return true;
			}
		}
		return false;
	}

	// The proto::BlessingKind of a spell called a_name, kBlessingNone if it isn't a blessing. "Blessing of" must be in
	// the name ("Blessing of Talos"); the god's name picks the kind.
	constexpr std::uint16_t FromSpellName(std::string_view a_name)
	{
		if (!Contains(a_name, "blessing of")) {
			return proto::kBlessingNone;
		}
		struct God
		{
			std::string_view name;
			std::uint16_t    kind;
		};
		constexpr God kGods[] = {
			{ "akatosh", proto::kBlessingAkatosh },
			{ "arkay", proto::kBlessingArkay },
			{ "dibella", proto::kBlessingDibella },
			{ "julianos", proto::kBlessingJulianos },
			{ "kynareth", proto::kBlessingKynareth },
			{ "mara", proto::kBlessingMara },
			{ "stendarr", proto::kBlessingStendarr },
			{ "talos", proto::kBlessingTalos },
			{ "zenithar", proto::kBlessingZenithar },
			{ "auriel", proto::kBlessingAuriel },
		};
		for (const auto& god : kGods) {
			if (Contains(a_name, god.name)) {
				return god.kind;
			}
		}
		return proto::kBlessingOther;
	}
}
