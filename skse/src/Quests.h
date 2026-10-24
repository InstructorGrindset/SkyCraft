#pragma once

#include "Link.h"

// Quest markers (quest_markers, proto::kFeatQuestMarkers; Minecraft's side: quests.SkyQuests). Twice a second the quest
// targets Skyrim's compass has for the player go into proto::QuestTargetTable, for Minecraft's locator bar: each target's
// position when it's in the player's world (worldspace or interior cell), else the door Skyrim's compass points to on the
// way there. Log: SkyCraft-quests.
namespace skycraft::Quests
{
	// Main thread, every frame. a_minecraftThere: Minecraft is connected and in its world; a_loading: a loading screen or no
	// cell yet; a_worldId: the player's world as SkyState::worldId names it. The table is emptied (once) while the switch is
	// off, Minecraft isn't there or Skyrim is loading.
	void Update(RE::PlayerCharacter* a_player, bool a_minecraftThere, bool a_loading, std::uint32_t a_worldId, float a_delta);
}
