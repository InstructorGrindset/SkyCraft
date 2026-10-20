package dev.skycraft.town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Minecraft blocks standing on Hearthfire house plots, and the stage each plot has reached (hearthfire_blocks). A block
 * counts while it stands: placed while Skyrim said the player was on the plot, and taken off again when it's broken. A
 * stage once reached stays reached. Kept in the world's save folder (SkyHearthfire) as lines of
 * {@code plot <location hex> <stage reached> <name>} and {@code block <location hex> <x> <y> <z>}. Pure.
 */
public final class PlotTally {
	public static final String HEADER = "# SkyCraft Hearthfire plots: plot <location> <stage reached> <name>, then block <location> <x> <y> <z>";

	private static final class Plot {
		int stage;
		String name = "";
		final Set<Long> blocks = new LinkedHashSet<>();
	}

	private final Map<Integer, Plot> plots = new LinkedHashMap<>();
	private final Map<Long, Integer> plotOf = new HashMap<>();

	/** A block position as one long (x and z 26 bits, y 12 bits, two's complement), as the tally keeps it. */
	public static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	public static int keyX(long key) {
		return (int) (key >> 38);
	}

	public static int keyY(long key) {
		return (int) (key << 52 >> 52);
	}

	public static int keyZ(long key) {
		return (int) (key << 26 >> 38);
	}

	/**
	 * A block now stands on {@code plot} (named {@code name}, Skyrim's name for it). Returns the stages it newly reached
	 * (lowest first; none if the block was counted already or no stage was reached).
	 */
	public List<Integer> add(int plot, String name, long block, TownRules rules) {
		Plot p = this.plots.computeIfAbsent(plot, k -> new Plot());
		if (name != null && !name.isBlank()) {
			p.name = name.strip();
		}
		List<Integer> reached = new ArrayList<>();
		Integer had = this.plotOf.get(block);
		if (had != null) {
			if (had == plot) {
				return reached;
			}
			remove(block); // counted for another plot (it can't stand on two)
		}
		p.blocks.add(block);
		this.plotOf.put(block, plot);
		while (p.stage < TownRules.lastStage() && p.blocks.size() >= rules.stageBlocks(p.stage + 1)) {
			p.stage++;
			reached.add(p.stage);
		}
		return reached;
	}

	/** A block was broken. Returns the plot it counted for, or 0 if it didn't count. */
	public int remove(long block) {
		Integer plot = this.plotOf.remove(block);
		if (plot == null) {
			return 0;
		}
		Plot p = this.plots.get(plot);
		if (p != null) {
			p.blocks.remove(block);
		}
		return plot;
	}

	/** Blocks standing on the plot. */
	public int count(int plot) {
		Plot p = this.plots.get(plot);
		return p == null ? 0 : p.blocks.size();
	}

	/** The highest stage the plot reached (0: none yet). */
	public int stage(int plot) {
		Plot p = this.plots.get(plot);
		return p == null ? 0 : p.stage;
	}

	/** Skyrim's name for the plot, as last heard ("" if never). */
	public String name(int plot) {
		Plot p = this.plots.get(plot);
		return p == null ? "" : p.name;
	}

	/** The plots known, in the order they were first built on. */
	public List<Integer> plots() {
		return new ArrayList<>(this.plots.keySet());
	}

	/** Progress to tell the player: "52 of 80 blocks for the small house", or that every stage is done. */
	public String progress(int plot, TownRules rules) {
		int stage = stage(plot), count = count(plot);
		if (stage >= TownRules.lastStage()) {
			return count + " blocks: every stage is built";
		}
		return count + " of " + rules.stageBlocks(stage + 1) + " blocks for the " + TownRules.stageName(stage + 1);
	}

	/** The tally as file lines. */
	public List<String> lines() {
		List<String> out = new ArrayList<>();
		out.add(HEADER);
		for (var e : this.plots.entrySet()) {
			out.add(String.format(Locale.ROOT, "plot %08x %d %s", e.getKey(), e.getValue().stage, e.getValue().name));
		}
		for (var e : this.plots.entrySet()) {
			for (long b : e.getValue().blocks) {
				out.add(String.format(Locale.ROOT, "block %08x %d %d %d", e.getKey(), keyX(b), keyY(b), keyZ(b)));
			}
		}
		return out;
	}

	/** Reads {@link #lines()} back. Bad lines are skipped and reported to {@code problems}. */
	public static PlotTally parse(List<String> lines, List<String> problems) {
		PlotTally t = new PlotTally();
		int n = 0;
		for (String raw : lines) {
			n++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] f = line.split("\\s+", 5);
			try {
				if (f[0].equals("plot") && f.length >= 3) {
					Plot p = t.plots.computeIfAbsent(Integer.parseUnsignedInt(f[1], 16), k -> new Plot());
					p.stage = Math.max(0, Math.min(TownRules.lastStage(), Integer.parseInt(f[2])));
					p.name = f.length > 3 ? line.split("\\s+", 4)[3].strip() : "";
				} else if (f[0].equals("block") && f.length == 5) {
					int plot = Integer.parseUnsignedInt(f[1], 16);
					long b = key(Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]));
					if (!t.plotOf.containsKey(b)) {
						t.plots.computeIfAbsent(plot, k -> new Plot()).blocks.add(b);
						t.plotOf.put(b, plot);
					}
				} else {
					problems.add("line " + n + ": expected 'plot ...' or 'block ...'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": " + e.getMessage());
			}
		}
		return t;
	}
}
