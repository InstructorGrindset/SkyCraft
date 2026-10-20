#include "Game.h"
#include "Stealth.h"

// Light and stealth. Skyrim's NPCs see the player and judge how lit they are with Skyrim's world alone, so they saw
// through Minecraft's block walls, a tunnel dug into the ground counted as broad daylight, and torches did nothing.
// - walls_hide_you: Skyrim's line of sight from an actor to a target (Actor::CalculateLOS, which CommonLib wraps) is
//   hooked at every call in the game's code. When it says an NPC sees the player, the line from the NPC's eyes to the
//   player's head and the one to their chest are walked through Minecraft's solid blocks (NpcBlocks); with a block on
//   both, the NPC doesn't see the player. Each NPC's answer is kept for a few frames.
// - light_stealth: the player's light level (HighProcessData::lightLevel, what Skyrim's detection and the GetLightLevel
//   condition read) is Skyrim's own light shaded by Minecraft's blocks, or Minecraft's block light, whichever is brighter
//   (Stealth.h PlayerLight), from the light Minecraft reads at its player's eyes (McState eyeBlockLight, eyeSkyLight).
namespace skycraft
{
	namespace
	{
		// ---- walls hide you -------------------------------------------------------------------------------------
		constexpr std::uint32_t kSightFrames = 6;      // an NPC's view of the player is worked out again after this many frames
		constexpr std::uint32_t kForgetFrames = 600;   // NPCs not asked about for this long are forgotten

		struct Sight
		{
			std::uint32_t frame = 0;  // when it was worked out (frameNumber; 0: never)
			bool          hidden = false;
		};

		// Skyrim asks for lines of sight on its AI threads as well as the main one.
		std::mutex                            sightLock;
		std::unordered_map<RE::FormID, Sight> sights;
		McVec                                 playerHead{}, playerChest{};  // Minecraft coords, this frame

		std::atomic<bool>          wallsActive{ false };
		std::atomic<std::uint32_t> frameNumber{ 1 };
		std::atomic<int>           loggedSights{ 0 };

		using CalculateLosFn = RE::ACTOR_LOS_LOCATION (*)(RE::Actor*, RE::Actor*, float);
		CalculateLosFn calculateLos = nullptr;

		// Where an NPC looks from: Skyrim's own eye point for its line of sight, or 0.9 of its height over its feet if
		// that comes back strange.
		McVec EyeOf(RE::Actor* a_actor)
		{
			const auto  feet = a_actor->GetPosition();
			const float height = std::max(a_actor->GetHeight(), 32.0f);
			auto        eye = a_actor->CalculateLOSLocation(RE::ACTOR_LOS_LOCATION::kEye);
			if (!std::isfinite(eye.x) || !std::isfinite(eye.y) || !std::isfinite(eye.z) || eye.GetDistance(feet) > 2.0f * height + 64.0f) {
				eye = feet + RE::NiPoint3{ 0.0f, 0.0f, 0.9f * height };
			}
			return SkyToMc(eye);
		}

		// Whether Minecraft's blocks hide the player from this NPC: a solid block on the line from its eyes to the
		// player's head, and on the one to their chest. Any thread.
		bool HiddenFrom(RE::Actor* a_npc)
		{
			const auto id = a_npc->GetFormID();
			const auto now = frameNumber.load(std::memory_order_relaxed);
			McVec      head, chest;
			bool       before = false;
			{
				std::scoped_lock lock(sightLock);
				if (const auto it = sights.find(id); it != sights.end()) {
					if (now - it->second.frame < kSightFrames) {
						return it->second.hidden;
					}
					before = it->second.hidden;
				}
				head = playerHead;
				chest = playerChest;
			}
			const McVec eye = EyeOf(a_npc);
			const bool  hidden = NpcBlocks::SolidBetween(eye, head) && NpcBlocks::SolidBetween(eye, chest);
			{
				std::scoped_lock lock(sightLock);
				sights[id] = { now, hidden };
			}
			if (hidden != before && (loggedSights.load(std::memory_order_relaxed) < 12 || DiagnosticsEnabled())) {
				++loggedSights;
				logger::info("walls hide you: {} ({:08X}) {} the player through Minecraft blocks", a_npc->GetDisplayFullName(), id,
					hidden ? "can't see" : "sees");
			}
			return hidden;
		}

		struct LineOfSightHook
		{
			// Actor::CalculateLOS: which of the target's eye, head, torso and feet the actor can see (kNone: none).
			static RE::ACTOR_LOS_LOCATION thunk(RE::Actor* a_this, RE::Actor* a_target, float a_viewCone)
			{
				const auto seen = calculateLos(a_this, a_target, a_viewCone);
				if (seen == RE::ACTOR_LOS_LOCATION::kNone || !a_this || a_this == a_target || !wallsActive.load(std::memory_order_relaxed) ||
					a_target != RE::PlayerCharacter::GetSingleton()) {
					return seen;
				}
				return HiddenFrom(a_this) ? RE::ACTOR_LOS_LOCATION::kNone : seen;
			}
		};

		// ---- light and stealth ----------------------------------------------------------------------------------
		struct LightOverride
		{
			bool  on = false;
			float skyrim = 0.0f;   // Skyrim's own light on the player, as it last worked it out
			float written = 0.0f;  // what we put in its place
			float logged = -1000.0f;
			int   logs = 0;
		};
		LightOverride light;

		void UpdateLight(RE::PlayerCharacter* a_player, const proto::McState* a_mc, bool a_puppeting)
		{
			auto*      process = a_player->GetActorRuntimeData().currentProcess;
			auto*      high = process ? process->high : nullptr;
			const bool on = high && a_puppeting && a_mc && Game::FeatureOn(proto::kFeatLightStealth) && (a_mc->eyeLightFlags & proto::kEyeLightValid) != 0;
			if (!on) {
				if (light.on) {
					// Skyrim's own light again, until it works it out anew.
					if (high && high->lightLevel == light.written) {
						high->lightLevel = light.skyrim;
					}
					logger::info("light and stealth: Skyrim's own light on the player again ({:.0f})", light.skyrim);
					light.on = false;
				}
				return;
			}
			float& level = high->lightLevel;
			if (!light.on || level != light.written) {
				light.skyrim = level;  // Skyrim worked out its own light since we last wrote ours
			}
			light.written = Stealth::PlayerLight(light.skyrim, a_mc->eyeBlockLight, a_mc->eyeSkyLight);
			level = light.written;
			if (!light.on) {
				logger::info("light and stealth: Minecraft's light counts for Skyrim's detection");
			}
			light.on = true;
			if (std::fabs(light.written - light.logged) >= 15.0f && (light.logs < 20 || DiagnosticsEnabled())) {
				++light.logs;
				light.logged = light.written;
				logger::info("light and stealth: Skyrim's light {:.0f}, Minecraft's block light {} and sky light {} at the eyes: the player's light is {:.0f}",
					light.skyrim, a_mc->eyeBlockLight, a_mc->eyeSkyLight, light.written);
			}
		}
	}

	namespace Stealth
	{
		void Install()
		{
			// Actor::CalculateLOS is called directly (not through a vtable), so hook its call sites: scan the game's code
			// for `call Actor::CalculateLOS` and redirect each one, as Game.cpp does for PlayerCamera::Update.
			const auto target = REL::Relocation<std::uintptr_t>{ RELOCATION_ID(36752, 37768) }.address();
			calculateLos = reinterpret_cast<CalculateLosFn>(target);
			const auto  text = REL::Module::get().segment(REL::Segment::textx);
			const auto  base = text.address();
			const auto* code = reinterpret_cast<const std::uint8_t*>(base);
			std::vector<std::uintptr_t> sites;
			for (std::size_t i = 0; i + 5 <= text.size(); ++i) {
				if (code[i] != 0xE8) {
					continue;
				}
				std::int32_t rel;
				std::memcpy(&rel, code + i + 1, 4);
				if (base + i + 5 + static_cast<std::intptr_t>(rel) == target) {
					sites.push_back(base + i);
				}
			}
			auto& trampoline = SKSE::GetTrampoline();
			for (const auto site : sites) {
				trampoline.write_call<5>(site, &LineOfSightHook::thunk);
			}
			if (sites.empty()) {
				logger::warn("walls hide you: no call of Actor::CalculateLOS found in this game build; NPCs see through Minecraft blocks");
			} else {
				logger::info("walls hide you: hooked {} call(s) of Actor::CalculateLOS (Skyrim's line of sight)", sites.size());
			}
		}

		void PerFrame(RE::PlayerCharacter* a_player, const proto::McState* a_mc, bool a_puppeting)
		{
			const auto frame = frameNumber.fetch_add(1, std::memory_order_relaxed) + 1;
			const bool walls = a_puppeting && a_mc && calculateLos && Game::FeatureOn(proto::kFeatWallsHideYou) && NpcBlocks::Any();
			if (walls) {
				const double eyeHeight = std::max(a_mc->eyeY - a_mc->y, 0.3);
				std::scoped_lock lock(sightLock);
				playerHead = { a_mc->eyeX, a_mc->eyeY, a_mc->eyeZ };
				playerChest = { a_mc->x, a_mc->y + eyeHeight * Stealth::kChestShare, a_mc->z };
			}
			if (walls != wallsActive.exchange(walls, std::memory_order_relaxed)) {
				static int logged = 0;
				if (logged < 10) {
					++logged;
					logger::info("walls hide you: {}", walls ? "on (Minecraft's blocks cut NPCs' sight of the player)" : "off");
				}
			}
			if (frame % 256 == 0) {
				std::scoped_lock lock(sightLock);
				std::erase_if(sights, [&](const auto& a_entry) { return frame - a_entry.second.frame > kForgetFrames; });
			}
			UpdateLight(a_player, a_mc, a_puppeting);
		}
	}
}
