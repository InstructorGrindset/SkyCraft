#pragma once

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <ctime>
#include <set>
#include <string>
#include <string_view>
#include <vector>

// Paired backups, Skyrim's half without the game (skse/tests/BackupNamesTest.cpp): the stamp a pair is named by, the
// backup folder's entries to delete to keep the newest pairs, and the input events (proto::kInPairedBackup) that tell
// Minecraft about one, the way Java's backup.BackupRequest reads them back.
namespace skycraft::BackupNames
{
	inline constexpr std::size_t kStampLength = 19;     // YYYY-MM-DD_hh-mm-ss
	inline constexpr std::size_t kTextMax = 512;        // proto::kPairedBackupTextMax
	inline constexpr std::string_view kPartial = ".partial";  // a copy still being written

	// The stamp for a local time: "YYYY-MM-DD_hh-mm-ss", which sorts by age and is a valid file name.
	inline std::string FormatStamp(const std::tm& a_time)
	{
		char buffer[80];  // room for any int, though a real time takes 19
		std::snprintf(buffer, sizeof(buffer), "%04d-%02d-%02d_%02d-%02d-%02d", a_time.tm_year + 1900, a_time.tm_mon + 1, a_time.tm_mday, a_time.tm_hour,
			a_time.tm_min, a_time.tm_sec);
		return buffer;
	}

	inline bool IsStamp(std::string_view a_text)
	{
		if (a_text.size() != kStampLength) {
			return false;
		}
		for (std::size_t i = 0; i < kStampLength; ++i) {
			const char c = a_text[i];
			const bool ok = (i == 4 || i == 7 || i == 13 || i == 16) ? c == '-' : i == 10 ? c == '_' : (c >= '0' && c <= '9');
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	// A save's name as SKSE hands it over, without the ".ess" a name could carry.
	inline std::string SaveBase(std::string_view a_name)
	{
		if (a_name.size() > 4) {
			const auto ext = a_name.substr(a_name.size() - 4);
			if (ext.size() == 4 && ext[0] == '.' && (ext[1] | 0x20) == 'e' && (ext[2] | 0x20) == 's' && (ext[3] | 0x20) == 's') {
				a_name.remove_suffix(4);
			}
		}
		return std::string(a_name);
	}

	// The entries of the backup folder to delete so that only the newest a_keep pairs stay (0: every pair stays): the
	// stamp folders past those, and "<stamp>.partial" leftovers (a game that closed while copying; never called while a
	// copy runs). Anything else in the folder is never named.
	inline std::vector<std::string> ToDelete(const std::vector<std::string>& a_names, int a_keep)
	{
		std::set<std::string> stamps;
		for (const auto& name : a_names) {
			if (IsStamp(name)) {
				stamps.insert(name);
			}
		}
		std::set<std::string> kept;
		for (auto it = stamps.rbegin(); it != stamps.rend(); ++it) {
			if (a_keep > 0 && kept.size() >= static_cast<std::size_t>(a_keep)) {
				break;
			}
			kept.insert(*it);
		}
		std::vector<std::string> out;
		for (const auto& name : a_names) {
			const bool partial = name.size() == kStampLength + kPartial.size() && IsStamp(std::string_view(name).substr(0, kStampLength)) &&
			                     std::string_view(name).substr(kStampLength) == kPartial;
			if (partial || (IsStamp(name) && !kept.contains(name))) {
				out.push_back(name);
			}
		}
		std::sort(out.begin(), out.end());
		return out;
	}

	// One proto::kInPairedBackup input event.
	struct Event
	{
		std::uint16_t code;
		std::int32_t  a, b, c;
	};

	// The events for a request: code 0 with the id and the text's length, then the text 12 bytes at a time (little
	// endian in a, b, c). The text is "<stamp>|<save name>", cut at kTextMax bytes.
	inline std::vector<Event> Encode(std::uint32_t a_id, std::string_view a_stamp, std::string_view a_saveName)
	{
		std::string text;
		text.reserve(a_stamp.size() + 1 + a_saveName.size());
		text.append(a_stamp).append(1, '|').append(a_saveName);
		if (text.size() > kTextMax) {
			text.resize(kTextMax);
		}
		std::vector<Event> out;
		out.push_back({ 0, static_cast<std::int32_t>(a_id), static_cast<std::int32_t>(text.size()), 0 });
		std::uint16_t part = 1;
		for (std::size_t at = 0; at < text.size(); at += 12, ++part) {
			std::uint32_t words[3]{};
			for (std::size_t i = 0; i < 12 && at + i < text.size(); ++i) {
				words[i / 4] |= std::uint32_t(static_cast<unsigned char>(text[at + i])) << (8 * (i % 4));
			}
			out.push_back({ part, static_cast<std::int32_t>(words[0]), static_cast<std::int32_t>(words[1]), static_cast<std::int32_t>(words[2]) });
		}
		return out;
	}
}
