package dev.skycraft.loot;

import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Item conversion's action bar line (switch {@code conversion_messages}): what a Skyrim item became, "Steel Sword →
 * Iron Sword", or "Gold ×25 → stays in Skyrim" when the rules keep it. Pickups within a second of the first share
 * its line, which counts the rest: "Steel Sword → Iron Sword, +3 more" (taking everything from a chest is one line).
 * A conversion takes the line from an item that stayed, since it's the news. One per player; SkyBridge adds each
 * pickup and, once a tick, sends the line when it changed. Pure, server thread.
 */
public final class ConversionNotes {
	/** Pickups this soon after a line's first one (ticks) share it. */
	public static final int WINDOW_TICKS = 20;
	private static final String ARROW = " → ";
	private static final String TIMES = " ×";

	/** One kind of Minecraft item a conversion gave: its name, and how many. */
	public record Output(String name, int count) {
	}

	private long windowStart;
	private @Nullable String headline;
	private boolean headlineConverted;
	private int more;
	private boolean changed;

	/** A pickup at server tick {@code tick}: its line ({@link #converted} or {@link #kept}), and whether it converted. */
	public void add(String line, boolean converted, long tick) {
		if (this.headline == null || tick - this.windowStart >= WINDOW_TICKS) {
			this.windowStart = tick;
			this.headline = line;
			this.headlineConverted = converted;
			this.more = 0;
		} else {
			if (converted && !this.headlineConverted) {
				this.headline = line; // the item that stayed is one of the "more" now
				this.headlineConverted = true;
			}
			this.more++;
		}
		this.changed = true;
	}

	/** The line to show, once after each change; null when nothing changed since the last call. */
	public @Nullable String poll() {
		if (!this.changed || this.headline == null) {
			return null;
		}
		this.changed = false;
		return this.more == 0 ? this.headline : this.headline + ", +" + this.more + " more";
	}

	/** "Iron Ore ×3 → Raw Iron ×3", or "Ruby → Gold Nugget ×3 + Diamond" for a rule giving several items. */
	public static String converted(String skyrimName, int count, List<Output> outputs) {
		String to = outputs.stream().map(o -> counted(o.name(), o.count())).collect(Collectors.joining(" + "));
		return counted(skyrimName, count) + ARROW + (to.isEmpty() ? "nothing" : to);
	}

	/** "Gold ×25 → stays in Skyrim"; a quest item says so. */
	public static String kept(String skyrimName, int count, boolean quest) {
		return counted(skyrimName, count) + ARROW + "stays in Skyrim" + (quest ? " (quest item)" : "");
	}

	private static String counted(String name, int count) {
		String n = name == null || name.isBlank() ? "An item" : name.strip();
		return count > 1 ? n + TIMES + count : n;
	}
}
