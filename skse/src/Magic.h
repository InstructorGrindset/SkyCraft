#pragma once

#include "Link.h"

// Shouts and Spells, Skyrim's side (Minecraft's shouts and spells switches: proto::kFeatShouts, kFeatSpells). Skyrim's own
// controls are cut off from its player while Minecraft drives it (Input.cpp), so Minecraft's Shout and Cast spell keys ask
// for them instead, and Skyrim carries them out for real:
// - The magic table (proto::MagicTable), refreshed about twice a second and at once after each shout or cast: the shouts
//   the player knows (name, words unlocked, each variation's recovery), the one in the voice slot, the voice recovery left
//   and the ShoutRecoveryMult; the player's magicka, and the spells they cast by hand (spell type Spell, fire and forget or
//   concentration: not abilities, powers or diseases), each with its school, its magicka cost for this player
//   (MagicItem::CalculateMagickaCost) and what it does (MagicKinds.h), Skyrim's magic favourites first.
// - kEvShout: a shout the player knows, as many words as asked and unlocked, once the voice has recovered: equipped if
//   another is in the voice slot, the variation's spell cast through the player's voice caster (MagicCaster::
//   CastSpellImmediate, so its visuals, sounds and effects on Skyrim's people happen), and the voice recovery begun as
//   Skyrim's own shout begins it (the variation's recovery times the ShoutRecoveryMult). Answered with kInShoutResult.
// - kEvCastSpell: a spell the player knows, if their magicka covers it: cast from the right hand toward Minecraft's look
//   (at the crosshair's actor for a touch or target spell, at the player for a self one), its magicka taken (per second
//   times the pulse for a concentration spell) and its regeneration held back as Skyrim does. Answered with kInCastResult.
// Log: SkyCraft-magic.
namespace skycraft::Magic
{
	// Main thread, every frame (Game's update): refreshes the magic table when it's due. a_loading: Skyrim is loading or the
	// player has no cell (nothing is written then).
	void Update(RE::PlayerCharacter* a_player, bool a_loading, float a_delta);
	// Main thread: Minecraft's kEvShout and kEvCastSpell. a_puppeting: Minecraft drives the player; otherwise Skyrim has it
	// (a menu, riding, a scene, the player dead) and the answer is "can't now".
	void OnEvent(RE::PlayerCharacter* a_player, const proto::McEvent& a_event, bool a_puppeting);
	// A game was loaded or started: the table is refreshed at once.
	void Reset();
}
