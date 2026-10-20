#pragma once

#include "Link.h"

// Paired backups (switch paired_backups, proto::kFeatPairedBackups): when a save is loaded with Minecraft connected,
// its .ess and .skse co-save are copied into <My Games>\Skyrim Special Edition\SkyCraft Backups\<stamp>, next to the
// Saves folder, and Minecraft is told the save's name and the stamp (proto::kInPairedBackup), so it zips its world under
// the same stamp. Minecraft answers with how many pairs to keep (proto::kEvPairedBackup), and the older copies here go.
// A save loaded before Minecraft is connected is copied once it is. Copies and deletions run on a thread of their own,
// never on the game's. Names and the events' layout are in BackupNames.h. Log: "paired backups".
namespace skycraft::Backups
{
	// SKSE's kPreLoadGame: the save about to be loaded (its name, as SKSE gives it). Main thread.
	void OnPreLoad(const char* a_name, std::uint32_t a_length);
	// SKSE's kPostLoadGame (a_loaded: the load worked) or kNewGame (a_loaded false). Main thread.
	void OnGameLoaded(bool a_loaded);
	// Main thread, every frame: a loaded save still to back up goes once Minecraft is connected with the switch on.
	void Update();
	// Main thread: Minecraft's proto::kEvPairedBackup.
	void OnEvent(const proto::McEvent& a_event);
}
