#pragma once

// Where the time goes: QueryPerformanceCounter timers around SkyCraft's per-frame work (and the
// Havok hooks, which run on Skyrim's physics threads), summed and logged as one line every minute
// (every 10 seconds with bDiagnostics), with Skyrim's own frame times for comparison, and how many
// blocks were drawn (Amount).
namespace skycraft::Perf
{
	enum Slot : int
	{
		kFrame,  // Skyrim's frame: from one player update to the next
		kUpdate,
		kCollision,
		kDigMeshes,
		kDigChanged,
		kLights,
		kWater,
		kDraw,
		kInFrame,
		kGrass,
		kReadbacks,
		kHookCharContacts,
		kHookContacts,
		kHookPlanes,
		kHookLand,
		kBlocks,  // WorldRender's passes: Minecraft's blocks, entities and shadows into Skyrim's frame (CPU side)
		kCount
	};

	inline constexpr const char* kNames[kCount] = { "frame", "update", "collision", "dig meshes", "dig changed", "lights", "water", "draw",
		"in-frame draw", "grass", "readbacks", "havok char contacts", "havok contacts", "havok planes", "land height", "block drawing" };

	// Amounts counted once per frame that draws blocks, logged with the timers: the average over those frames and the
	// most in one. Together with "block drawing" they say whether a slow frame is many blocks or something else.
	enum Amount : int
	{
		kSectionsDrawn,   // 16x16x16 block sections drawn into the scene
		kTriangles,       // block triangles sent to the GPU, every pass (sun shadows, scene, water and glass, depth copies)
		kShadowSections,  // sections drawn into the sun's shadow map
		kFarSections,     // far_block_detail: sections left out of the sun's shadow map for being far
		kAmountCount
	};

	inline constexpr const char* kAmountNames[kAmountCount] = { "block sections drawn", "block triangles", "shadow-casting sections",
		"far sections without shadows" };

	struct Stat
	{
		std::atomic<std::int64_t> ticks{ 0 };
		std::atomic<std::int64_t> max{ 0 };
		std::atomic<std::int64_t> calls{ 0 };
	};
	inline Stat stats[kCount];
	inline Stat amounts[kAmountCount];

	inline std::int64_t Now()
	{
		LARGE_INTEGER t;
		::QueryPerformanceCounter(&t);
		return t.QuadPart;
	}

	inline double TicksPerMs()
	{
		static const double f = [] {
			LARGE_INTEGER q;
			::QueryPerformanceFrequency(&q);
			return double(q.QuadPart) / 1000.0;
		}();
		return f;
	}

	inline void Note(Stat& a_stat, std::int64_t a_value)
	{
		a_stat.ticks.fetch_add(a_value, std::memory_order_relaxed);
		a_stat.calls.fetch_add(1, std::memory_order_relaxed);
		auto m = a_stat.max.load(std::memory_order_relaxed);
		while (a_value > m && !a_stat.max.compare_exchange_weak(m, a_value, std::memory_order_relaxed)) {
		}
	}

	inline void Add(Slot a_slot, std::int64_t a_ticks) { Note(stats[a_slot], a_ticks); }

	// This frame's a_amount (once per frame that draws blocks).
	inline void Count(Amount a_amount, std::int64_t a_value) { Note(amounts[a_amount], a_value); }

	struct Scope
	{
		explicit Scope(Slot a_slot) :
			slot(a_slot), start(Now()) {}
		~Scope() { Add(slot, Now() - start); }
		Scope(const Scope&) = delete;
		Scope& operator=(const Scope&) = delete;

		Slot         slot;
		std::int64_t start;
	};

	// Once per frame, on the main thread: now and then, the totals as one log line.
	inline void Report()
	{
		static std::int64_t last = 0, windowStart = 0;
		const auto          now = Now();
		if (last) {
			Add(kFrame, now - last);
		}
		last = now;
		if (!windowStart) {
			windowStart = now;
			return;
		}
		const double windowMs = double(now - windowStart) / TicksPerMs();
		if (windowMs < (DiagnosticsEnabled() ? 10000.0 : 60000.0)) {
			return;
		}
		windowStart = now;
		const auto frames = std::max<std::int64_t>(1, stats[kFrame].calls.exchange(0));
		const auto frameTicks = stats[kFrame].ticks.exchange(0);
		const auto frameMax = stats[kFrame].max.exchange(0);
		std::string line = std::format("perf: {} frames in {:.1f} s ({:.0f} fps), frame avg {:.1f} ms, worst {:.1f} ms", frames, windowMs / 1000.0,
			double(frames) * 1000.0 / windowMs, double(frameTicks) / TicksPerMs() / double(frames), double(frameMax) / TicksPerMs());
		for (int i = kFrame + 1; i < kCount; ++i) {
			const auto calls = stats[i].calls.exchange(0);
			const auto ticks = stats[i].ticks.exchange(0);
			const auto max = stats[i].max.exchange(0);
			if (calls) {
				line += std::format(" | {} {:.2f} ms/frame (worst call {:.1f} ms, {:.1f} calls/frame)", kNames[i], double(ticks) / TicksPerMs() / double(frames),
					double(max) / TicksPerMs(), double(calls) / double(frames));
			}
		}
		for (int i = 0; i < kAmountCount; ++i) {
			const auto calls = amounts[i].calls.exchange(0);
			const auto sum = amounts[i].ticks.exchange(0);
			const auto max = amounts[i].max.exchange(0);
			if (calls) {
				line += std::format(" | {} {:.0f} a frame (most {})", kAmountNames[i], double(sum) / double(calls), max);
			}
		}
		logger::info("{}", line);
	}
}
