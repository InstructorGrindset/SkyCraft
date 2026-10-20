#pragma once

// Walking a straight line through Minecraft's block grid, block face by block face (Amanatides and Woo). Pure, no game:
// NpcBlocks walks Minecraft's solid blocks with it, for Skyrim's arrows (Arrows.cpp) and NPCs' line of sight
// (Stealth.cpp). Unit tested in tests/StealthTest.cpp.

#include <cfloat>
#include <cmath>

namespace skycraft::BlockWalk
{
	// The block a Minecraft coordinate is in (floor, for negatives too).
	constexpr long long BlockOf(double a_v)
	{
		const auto t = static_cast<long long>(a_v);  // toward zero
		return static_cast<double>(t) > a_v ? t - 1 : t;
	}

	// How many blocks a walk from a to b (Minecraft coords) passes through: a's own and one more for every block face
	// crossed, so a walk that long covers the whole line.
	constexpr long long CellsAlong(double a_ax, double a_ay, double a_az, double a_bx, double a_by, double a_bz)
	{
		auto span = [](double a_from, double a_to) {
			const long long d = BlockOf(a_to) - BlockOf(a_from);
			return d < 0 ? -d : d;
		};
		return 1 + span(a_ax, a_bx) + span(a_ay, a_by) + span(a_az, a_bz);
	}

	// Walks the blocks the line from a to b passes through, in order: a_visit(x, y, z, t) for each, t the share of the
	// way at which it's entered (0 for a's own), until it returns true, the walk passes b, or a_maxCells blocks have been
	// visited. Returns whether a_visit stopped it.
	template <class F>
	bool Walk(double a_ax, double a_ay, double a_az, double a_bx, double a_by, double a_bz, long long a_maxCells, F&& a_visit)
	{
		const double p[3] = { a_ax, a_ay, a_az };
		const double d[3] = { a_bx - a_ax, a_by - a_ay, a_bz - a_az };
		int          cell[3], step[3];
		double       next[3], every[3];
		for (int i = 0; i < 3; ++i) {
			cell[i] = static_cast<int>(std::floor(p[i]));
			step[i] = d[i] > 0.0 ? 1 : d[i] < 0.0 ? -1 : 0;
			if (step[i] == 0) {
				next[i] = every[i] = DBL_MAX;
				continue;
			}
			next[i] = ((step[i] > 0 ? cell[i] + 1.0 : double(cell[i])) - p[i]) / d[i];
			every[i] = std::abs(1.0 / d[i]);
		}
		double t = 0.0;
		for (long long n = 0; n < a_maxCells && t <= 1.0; ++n) {
			if (a_visit(cell[0], cell[1], cell[2], t)) {
				return true;
			}
			const int axis = next[0] < next[1] ? (next[0] < next[2] ? 0 : 2) : (next[1] < next[2] ? 1 : 2);
			t = next[axis];
			cell[axis] += step[axis];
			next[axis] += every[axis];
		}
		return false;
	}

	// A solid block (a_solid(x, y, z)) somewhere on the line from a to b, not counting the blocks a and b are in
	// themselves. False when the line passes through more than a_maxCells blocks: too long to say.
	template <class F>
	bool SolidBetween(double a_ax, double a_ay, double a_az, double a_bx, double a_by, double a_bz, long long a_maxCells, F&& a_solid)
	{
		const long long cells = CellsAlong(a_ax, a_ay, a_az, a_bx, a_by, a_bz);
		if (cells > a_maxCells) {
			return false;
		}
		const long long endX = BlockOf(a_bx), endY = BlockOf(a_by), endZ = BlockOf(a_bz);
		bool            first = true;
		return Walk(a_ax, a_ay, a_az, a_bx, a_by, a_bz, cells, [&](int a_x, int a_y, int a_z, double a_t) {
			const bool end = first || a_t >= 1.0 - 1e-9 || (a_x == endX && a_y == endY && a_z == endZ);
			first = false;
			return !end && a_solid(a_x, a_y, a_z);
		});
	}
}
