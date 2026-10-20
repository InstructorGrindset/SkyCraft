#include "WorldSync.h"

#include "BlessingKinds.h"

// Shrine blessings (switch shrine_blessings, Minecraft's side only: it ignores kInBlessing with the switch off). A
// shrine's blessing is a spell named "Blessing of ..." that the shrine casts on the player; it shows among the player's
// active effects for 8 game hours. Once a second this looks there (the player's MagicTarget active effect list, as
// CommonLib wraps it) and tells Minecraft about a blessing that is new or was cast again (its elapsed time went back),
// with the game hours it has left and Skyrim's timescale, so Minecraft's effect lasts as long in real time.
namespace skycraft::Blessings
{
	namespace
	{
		struct Seen
		{
			std::uint16_t kind{ 0 };
			float         elapsed{ 0.0f };   // seconds, the effect's own clock
			float         fraction{ 1.0f };  // of its duration still to run
			const char*   name{ "" };
		};
	}

	void Update(RE::PlayerCharacter* a_player, bool a_minecraftThere, float a_delta)
	{
		static float                                 timer = 0.0f;
		static std::unordered_map<RE::FormID, Seen> seen;  // blessing spells on the player at the last look
		static int                                   logged = 0;
		timer -= a_delta;
		if (timer > 0.0f) {
			return;
		}
		timer = 1.0f;
		auto* target = a_player ? a_player->AsMagicTarget() : nullptr;
		auto* effects = target ? target->GetActiveEffectList() : nullptr;
		if (!a_minecraftThere || !effects) {
			seen.clear();  // Minecraft hears of what's still running when it's back
			return;
		}
		std::unordered_map<RE::FormID, Seen> now;
		for (auto* effect : *effects) {
			if (!effect || !effect->spell || effect->flags.any(RE::ActiveEffect::Flag::kDispelled)) {
				continue;
			}
			const char*         name = effect->spell->GetName();
			const std::uint16_t kind = BlessingKinds::FromSpellName(name ? name : "");
			if (kind == proto::kBlessingNone) {
				continue;
			}
			const float fraction = effect->duration > 0.0f ? std::clamp(1.0f - effect->elapsedSeconds / effect->duration, 0.0f, 1.0f) : 1.0f;
			auto [it, added] = now.try_emplace(effect->spell->GetFormID(), Seen{ kind, effect->elapsedSeconds, fraction, name });
			if (!added && effect->elapsedSeconds < it->second.elapsed) {
				it->second.elapsed = effect->elapsedSeconds;  // a spell with several effects: its freshest
				it->second.fraction = std::max(it->second.fraction, fraction);
			}
		}
		auto*       calendar = RE::Calendar::GetSingleton();
		const float timescale = calendar && calendar->timeScale ? calendar->timeScale->value : 20.0f;
		for (const auto& [spell, blessing] : now) {
			const auto before = seen.find(spell);
			if (before != seen.end() && blessing.elapsed >= before->second.elapsed - 0.5f) {
				continue;  // the same blessing running on
			}
			const float hours = BlessingKinds::kBlessingHours * blessing.fraction;
			if (hours <= 0.0f) {
				continue;
			}
			Link::Get().PushInput(proto::kInBlessing, blessing.kind, static_cast<std::int32_t>(hours * 1000.0f), static_cast<std::int32_t>(timescale * 1000.0f));
			if (logged < 20) {
				++logged;
				logger::info("SkyCraft-blessing: {} ({:.1f} game hours left at timescale {:.0f}) told to Minecraft", blessing.name, hours, timescale);
			}
		}
		seen = std::move(now);
	}
}
