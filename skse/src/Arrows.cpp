#include "Game.h"

// Skyrim's arrows and bolts stop at Minecraft's blocks. Skyrim's physics knows nothing of them (only
// NPCs are kept out, NpcBlocks.cpp), so before an arrow moves each frame the stretch it's about to fly
// is walked block by block through the solid blocks Minecraft sends (proto::kRenSolids). With a block
// on it, the arrow flies only up to just short of the block's face, so whatever stands in front of the
// block is still hit as usual; the next frame, at the face, Skyrim's arrow is killed and Minecraft
// sticks one of its own in the block there (proto::kInArrowBlocked), which Skyrim then draws like any
// Minecraft arrow (WorldRender, kWeArrow).
namespace skycraft
{
	namespace
	{
		// Skyrim units a second under which an arrow counts as not flying: stuck in something, or
		// carried along by an actor it's stuck in. Arrows fly at thousands.
		constexpr float kFlying = 500.0f;
		// Blocks: an arrow this close to a block's face is stopped there.
		constexpr double kAtFace = 0.08;
		// The most blocks one frame's flight is walked through (a frame's flight is a block or two).
		constexpr int kMaxCells = 64;

		struct Blocked
		{
			McVec at;      // where the arrow reached the block (MC coords)
			McVec toward;  // which way it flew (MC axes, unit length)
		};
		std::mutex           blockedLock;
		std::vector<Blocked> blocked;  // for Minecraft, sent on the main thread (PerFrame)
		int                  logged = 0;

		McVec Along(const McVec& a_from, const McVec& a_to, double a_t)
		{
			return { a_from.x + (a_to.x - a_from.x) * a_t, a_from.y + (a_to.y - a_from.y) * a_t, a_from.z + (a_to.z - a_from.z) * a_t };
		}

		// Skyrim's arrow ends at the block; Minecraft is told where, and which way it flew.
		void Stop(RE::Projectile* a_arrow, const McVec& a_at, const McVec& a_from, const McVec& a_to)
		{
			const double dx = a_to.x - a_from.x, dy = a_to.y - a_from.y, dz = a_to.z - a_from.z;
			const double length = std::sqrt(dx * dx + dy * dy + dz * dz);
			if (length > 1e-6) {
				std::scoped_lock lock(blockedLock);
				if (blocked.size() < 64) {
					blocked.push_back({ a_at, { dx / length, dy / length, dz / length } });
				}
			}
			// Still until it's gone, so this is the only time it's stopped.
			a_arrow->GetProjectileRuntimeData().linearVelocity = RE::NiPoint3{};
			a_arrow->Kill();
			if (logged < 10) {
				++logged;
				logger::info("arrow {:08X}: stopped by a Minecraft block at {:.2f} {:.2f} {:.2f}", a_arrow->GetFormID(), a_at.x, a_at.y, a_at.z);
			}
		}

		struct ArrowUpdateHook
		{
			static void thunk(RE::Projectile* a_this, float a_delta)
			{
				auto&       data = a_this->GetProjectileRuntimeData();
				const auto  velocity = data.linearVelocity;
				const float speed = velocity.Length();
				if (speed < kFlying || a_delta <= 0.0f || !NpcBlocks::Any()) {
					func(a_this, a_delta);
					return;
				}
				const auto  start = a_this->GetPosition();
				const McVec from = SkyToMc(start);
				const McVec to = SkyToMc(start + velocity * a_delta);
				float       share = 1.0f;
				if (const auto t = NpcBlocks::FirstSolid(from, to, kMaxCells)) {
					const double length = double(speed) * a_delta / proto::kUnitsPerBlock;  // blocks
					const double gap = *t * length;
					if (gap <= kAtFace) {
						Stop(a_this, Along(from, to, *t), from, to);
						return;
					}
					share = static_cast<float>((gap - kAtFace * 0.5) / length);  // up to just short of the face
				}
				func(a_this, a_delta * share);
				// Its own flight this frame, in case it went another way than its velocity said
				// (something it bounced off): through a block, it ends where it entered it.
				const auto now = a_this->GetPosition();
				if (now == start || data.linearVelocity.Length() < kFlying) {
					return;
				}
				const McVec went = SkyToMc(now);
				if (const auto t = NpcBlocks::FirstSolid(from, went, kMaxCells)) {
					Stop(a_this, Along(from, went, *t), from, went);
				}
			}
			static inline REL::Relocation<decltype(thunk)> func;
		};
	}

	namespace Arrows
	{
		void Install()
		{
			REL::Relocation<std::uintptr_t> vtbl{ RE::VTABLE_ArrowProjectile[0] };
			ArrowUpdateHook::func = vtbl.write_vfunc(0xAB, ArrowUpdateHook::thunk);
			logger::info("arrow hook installed (arrows stop at Minecraft's blocks)");
		}

		void PerFrame()
		{
			std::vector<Blocked> send;
			{
				std::scoped_lock lock(blockedLock);
				send.swap(blocked);
			}
			constexpr double kTurn = 6.283185307179586;
			for (const auto& b : send) {
				const double yaw = std::atan2(b.toward.x, b.toward.z);                 // from +z toward +x
				const double pitch = std::asin(std::clamp(b.toward.y, -1.0, 1.0));  // up is +
				const auto   yawByte = static_cast<std::uint16_t>(static_cast<int>(std::lround(yaw / kTurn * 256.0)) & 0xFF);
				const auto   pitchByte = static_cast<std::uint16_t>(std::lround((pitch / (kTurn * 0.5) + 0.5) * 255.0));
				Link::Get().PushInput(proto::kInArrowBlocked, static_cast<std::uint16_t>(yawByte << 8 | pitchByte),
					static_cast<std::int32_t>(std::lround(b.at.x * 1024.0)), static_cast<std::int32_t>(std::lround(b.at.y * 1024.0)),
					static_cast<std::int32_t>(std::lround(b.at.z * 1024.0)));
			}
		}
	}
}
