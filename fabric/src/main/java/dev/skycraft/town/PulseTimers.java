package dev.skycraft.town;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redstone pulses Skyrim's levers, buttons and pressure plates gave: the blocks that stand in for their power, and the
 * tick each one goes again. Another pulse at the same block while one lasts makes it last longer. Pure.
 */
public final class PulseTimers {
	private final Map<Long, Long> until = new LinkedHashMap<>();

	/** A pulse at {@code key} until tick {@code end}. Returns true if it's new (not lengthening one). */
	public boolean start(long key, long end) {
		Long had = this.until.get(key);
		this.until.put(key, had == null ? end : Math.max(had, end));
		return had == null;
	}

	public boolean has(long key) {
		return this.until.containsKey(key);
	}

	/** The pulses that are over at tick {@code now} (each forgotten here). */
	public List<Long> due(long now) {
		List<Long> out = new ArrayList<>();
		for (var it = this.until.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			if (now >= e.getValue()) {
				out.add(e.getKey());
				it.remove();
			}
		}
		return out;
	}

	/** Every pulse still on (each forgotten here): the server is stopping. */
	public List<Long> all() {
		List<Long> out = new ArrayList<>(this.until.keySet());
		this.until.clear();
		return out;
	}

	public Set<Long> keys() {
		return Set.copyOf(this.until.keySet());
	}

	public int size() {
		return this.until.size();
	}
}
