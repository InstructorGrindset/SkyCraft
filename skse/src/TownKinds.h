#pragma once

// What the towns and building features look for in Skyrim's data, and what guards say. Pure, no game: it reads
// only model paths (which Skyrim keeps for every object at run time, and which don't change with the game's language)
// and file names, so it's unit tested on its own (tests/TownKindsTest.cpp).

#include "AnimalKinds.h"

#include <cstddef>
#include <cstdint>
#include <string_view>

namespace skycraft::TownKinds
{
	using AnimalKinds::Contains;
	using AnimalKinds::EqualsNoCase;

	// A Skyrim switch whose use powers Minecraft's redstone (Dwemer redstone).
	enum class Switch : std::uint8_t
	{
		kNone,
		kLever,   // levers, pull bars and pull chains
		kButton,  // buttons
		kPlate,   // pressure plates
	};

	// The kind of switch an activator is, from its model path: Skyrim's levers, pull bars, pull chains, buttons and
	// pressure plates are named for what they are (Dungeons\Dwemer\..., Dungeons\Nordic\..., ...), and so are most mods'.
	constexpr Switch SwitchFromModel(std::string_view a_path)
	{
		if (Contains(a_path, "pressureplate") || Contains(a_path, "pressure plate") || Contains(a_path, "pressplate")) {
			return Switch::kPlate;
		}
		if (Contains(a_path, "lever") || Contains(a_path, "pullbar") || Contains(a_path, "pullchain") || Contains(a_path, "pull chain")) {
			return Switch::kLever;
		}
		if (Contains(a_path, "button")) {
			return Switch::kButton;
		}
		return Switch::kNone;
	}

	// How long (Minecraft ticks) a switch powers its block: a lever's pull a little longer than a press.
	constexpr int PulseTicks(Switch a_switch)
	{
		switch (a_switch) {
		case Switch::kLever:
			return 30;
		case Switch::kButton:
		case Switch::kPlate:
			return 20;
		default:
			return 0;
		}
	}

	constexpr std::string_view SwitchName(Switch a_switch)
	{
		switch (a_switch) {
		case Switch::kLever:
			return "lever";
		case Switch::kButton:
			return "button";
		case Switch::kPlate:
			return "pressure plate";
		default:
			return "nothing";
		}
	}

	// A Dwemer door, by its model (Skyrim's Dwemer doors and gates are in Dungeons\Dwemer and named Dwe...).
	constexpr bool IsDwemerDoorModel(std::string_view a_path)
	{
		return Contains(a_path, "dwemer") || Contains(a_path, "dwedoor") || Contains(a_path, "dwegate");
	}

	// The file Hearthfire's houses come from.
	constexpr bool IsHearthfireFile(std::string_view a_fileName) { return EqualsNoCase(a_fileName, "HearthFires.esm"); }

	// What a guard says when they see the player build or dig in town (proto::TownBuildKind): a line picked by a_roll.
	namespace detail
	{
		inline constexpr std::string_view kPlace[] = {
			"Building something? Keep it out of the road.",
			"Never seen stone cut that square.",
			"Does the Jarl know you're building here?",
			"Planning to stay a while, are you?",
			"Odd sort of masonry. Not bad, though.",
		};
		inline constexpr std::string_view kBreak[] = {
			"Careful. Is that yours to break?",
			"Watch it.",
			"Mind you clean that up.",
		};
		inline constexpr std::string_view kDig[] = {
			"Hey! Stop digging up the town.",
			"You'll answer to the Jarl for those holes.",
			"Fill that in before someone breaks a leg.",
			"This isn't a mine, friend.",
		};
	}

	constexpr std::size_t GuardLines(std::uint32_t a_kind)
	{
		switch (a_kind) {
		case proto::kBuildPlace:
			return std::size(detail::kPlace);
		case proto::kBuildBreak:
			return std::size(detail::kBreak);
		case proto::kBuildDig:
			return std::size(detail::kDig);
		default:
			return 0;
		}
	}

	constexpr std::string_view GuardLine(std::uint32_t a_kind, std::uint32_t a_roll)
	{
		const std::size_t n = GuardLines(a_kind);
		if (n == 0) {
			return {};
		}
		const std::size_t i = a_roll % n;
		switch (a_kind) {
		case proto::kBuildPlace:
			return detail::kPlace[i];
		case proto::kBuildBreak:
			return detail::kBreak[i];
		default:
			return detail::kDig[i];
		}
	}
}
