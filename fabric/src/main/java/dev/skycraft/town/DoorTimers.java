package dev.skycraft.town;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft doors Skyrim's NPCs opened, and when to close them again: once nobody has been in the doorway for a while
 * (and never sooner than that after opening). Keys are the door's lower block, as the caller packs it. At most
 * {@link #MAX} doors are kept; past that the one opened longest ago is closed first. Pure.
 */
public final class DoorTimers {
	public static final int MAX = 64;

	private final Map<Long, long[]> doors = new LinkedHashMap<>(); // key -> {opened, last occupied}

	/** An NPC opened the door at {@code now}. */
	public void opened(long key, long now) {
		this.doors.remove(key); // to the back: opened most recently
		this.doors.put(key, new long[] { now, now });
	}

	/** Someone stands in the doorway at {@code now}: it stays open. */
	public void occupied(long key, long now) {
		long[] d = this.doors.get(key);
		if (d != null) {
			d[1] = Math.max(d[1], now);
		}
	}

	public boolean tracking(long key) {
		return this.doors.containsKey(key);
	}

	/** Stops tracking a door (someone else closed it, or it's gone). */
	public void forget(long key) {
		this.doors.remove(key);
	}

	/** Doors to close now (each forgotten here): empty for {@code closeAfter} ticks, or the oldest past {@link #MAX}. */
	public List<Long> due(long now, int closeAfter) {
		List<Long> out = new ArrayList<>();
		int over = this.doors.size() - MAX;
		for (var it = this.doors.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			if (over-- > 0 || now - e.getValue()[1] >= closeAfter) {
				out.add(e.getKey());
				it.remove();
			}
		}
		return out;
	}

	/** The doors being kept open, oldest first. */
	public List<Long> keys() {
		return new ArrayList<>(this.doors.keySet());
	}

	public int size() {
		return this.doors.size();
	}

	public void clear() {
		this.doors.clear();
	}
}
