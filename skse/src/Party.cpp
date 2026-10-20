#include "Party.h"

#include "Game.h"

namespace skycraft::Party
{
	namespace
	{
		using Clock = std::chrono::steady_clock;

		// A shout starts its voice recovery, which then only counts down: a jump up of at least this many seconds
		// is a shout just released.
		constexpr float kShoutJump = 0.5f;
		// Actors not looked at for this many frames have left the actor table: forgotten.
		constexpr std::uint32_t kForgetFrames = 600;
		// The camera's furthest swing for a full strength shake while Minecraft drives it (Skyrim units, about a
		// tenth of a block).
		constexpr float kShakeUnits = 7.0f;

		struct Voice
		{
			float         recovery{ 0.0f };
			std::uint32_t frame{ 0 };
		};
		std::unordered_map<RE::FormID, Voice> voices;  // by actor (the player too): voice recovery last time
		std::uint32_t                          frame = 0;

		// Races live as long as the game: whether each is a dragon's is worked out once.
		std::unordered_map<const RE::TESRace*, bool> dragonRaces;

		std::uint16_t shoutSeq = 0;
		std::uint8_t  shoutWords = 0;
		std::uint8_t  shoutFlags = 0;
		RE::FormID    shoutActor = 0;
		int           shoutsLogged = 0;

		// A shake Minecraft asked for, played on the camera Minecraft drives (CameraOffset).
		Clock::time_point shakeStart{};
		Clock::time_point shakeEnd{};
		float             shakeStrength = 0.0f;
		int               shakesLogged = 0;

		bool IsDragon(RE::Actor* a_actor)
		{
			const auto* race = a_actor ? a_actor->GetRace() : nullptr;
			if (!race) {
				return false;
			}
			const auto [it, fresh] = dragonRaces.try_emplace(race, false);
			if (fresh) {
				it->second = race->HasKeywordString("ActorTypeDragon");
			}
			return it->second;
		}

		void Watch(RE::Actor* a_actor, bool a_player)
		{
			const float recovery = a_actor->GetVoiceRecoveryTime();
			const auto [it, fresh] = voices.try_emplace(a_actor->GetFormID(), Voice{ recovery, frame });
			const float before = it->second.recovery;
			it->second = Voice{ recovery, frame };
			// On first sight a shout already recovering is old news.
			if (fresh || !(recovery > before + kShoutJump)) {
				return;
			}
			const std::int32_t level = a_actor->GetCurrentShoutLevel();  // the variation shouted, -1 when it can't say
			const bool         dragon = IsDragon(a_actor);
			++shoutSeq;
			shoutWords = level >= 0 && level <= 2 ? static_cast<std::uint8_t>(level + 1) : std::uint8_t{ 0 };
			shoutFlags = static_cast<std::uint8_t>((a_player ? proto::kShoutByPlayer : 0u) | (dragon ? proto::kShoutByDragon : 0u));
			shoutActor = a_actor->GetFormID();
			if (shoutsLogged < 10) {
				++shoutsLogged;
				const char* name = a_actor->GetDisplayFullName();
				logger::info("SkyCraft-party: {} ({:08X}{}) shouted ({} words, voice recovery {:.1f} s)", name ? name : "?", shoutActor,
					dragon ? ", a dragon" : "", shoutWords, recovery);
			}
		}
	}

	std::uint32_t ActorFlags(RE::Actor* a_actor)
	{
		return IsDragon(a_actor) ? proto::kActorDragon : 0u;
	}

	void WatchActor(RE::Actor* a_actor)
	{
		if (a_actor && Game::FeatureOn(proto::kFeatSharedWorldEvents)) {
			Watch(a_actor, false);
		}
	}

	void Fill(RE::PlayerCharacter* a_player, proto::SkyState& a_sky)
	{
		++frame;
		if (a_player && Game::FeatureOn(proto::kFeatSharedWorldEvents)) {
			Watch(a_player, true);
		}
		if (frame % 300 == 0) {
			std::erase_if(voices, [](const auto& a_entry) { return frame - a_entry.second.frame > kForgetFrames; });
		}
		a_sky.shoutSeq = shoutSeq;
		a_sky.shoutWords = shoutWords;
		a_sky.shoutFlags = shoutFlags;
		a_sky.shoutActor = shoutActor;
	}

	void Shake(RE::PlayerCharacter* a_player, const proto::McEvent& a_event)
	{
		if (!a_player || !Game::FeatureOn(proto::kFeatSharedWorldEvents) || !(a_event.a > 0.0f)) {
			return;
		}
		const float strength = std::min(a_event.a, 1.0f);
		const float seconds = a_event.b >= 0.1f ? std::min(a_event.b, 3.0f) : 0.1f;  // (NaN: the shortest)
		shakeStart = Clock::now();
		shakeEnd = shakeStart + std::chrono::duration_cast<Clock::duration>(std::chrono::duration<float>(seconds));
		shakeStrength = strength;
		// Skyrim's own shake, for when Skyrim drives the camera (riding, a scene); while Minecraft does, the camera is
		// pinned to Minecraft's eye after Skyrim's update, so CameraOffset plays it there.
		RE::ShakeCamera(strength, a_player->GetPosition(), seconds);
		if (shakesLogged < 10) {
			++shakesLogged;
			logger::info("SkyCraft-party: Minecraft shakes the camera ({:.2f} for {:.1f} s): something happened to another player", strength, seconds);
		}
	}

	RE::NiPoint3 CameraOffset()
	{
		const auto now = Clock::now();
		if (shakeStrength <= 0.0f || now >= shakeEnd) {
			return {};
		}
		const float t = std::chrono::duration<float>(now - shakeStart).count();
		const float total = std::chrono::duration<float>(shakeEnd - shakeStart).count();
		const float amp = kShakeUnits * shakeStrength * (total > 0.0f ? 1.0f - t / total : 0.0f);
		// Three unrelated wobbles, fading out: a tremor rather than a sway.
		return { amp * std::sin(t * 47.0f), amp * std::sin(t * 53.0f + 1.3f), amp * 0.6f * std::sin(t * 61.0f + 2.1f) };
	}
}
