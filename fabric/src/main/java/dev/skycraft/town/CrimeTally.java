package dev.skycraft.town;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Griefing a town, added up a few seconds at a time, so digging through a wall is one crime and not an alarm a block.
 * Per Proto.CRIME_* kind the first crime starts a batch; once the batch time has passed since then it's due, with its
 * values added up, its blocks counted and the place of its latest crime. Pure.
 */
public final class CrimeTally {
	/** A crime ready for Skyrim (EV_TOWN_CRIME). */
	public record Batch(int kind, int value, int count, double x, double y, double z) {
	}

	private static final class Open {
		long started;
		int value;
		int count;
		double x, y, z;
	}

	private final Map<Integer, Open> open = new TreeMap<>();

	/** A block's worth of griefing of this kind, worth {@code value} gold, at this place, at tick {@code now}. */
	public void add(int kind, int value, double x, double y, double z, long now) {
		Open o = this.open.computeIfAbsent(kind, k -> {
			Open fresh = new Open();
			fresh.started = now;
			return fresh;
		});
		o.value = (int) Math.min(Integer.MAX_VALUE, (long) o.value + Math.max(0, value));
		o.count++;
		o.x = x;
		o.y = y;
		o.z = z;
	}

	/** The batches whose time is up at tick {@code now} (each forgotten here), kinds in order. */
	public List<Batch> due(long now, int batchTicks) {
		List<Batch> out = new ArrayList<>();
		for (Iterator<Map.Entry<Integer, Open>> it = this.open.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Open> e = it.next();
			Open o = e.getValue();
			if (now - o.started >= batchTicks) {
				out.add(new Batch(e.getKey(), o.value, o.count, o.x, o.y, o.z));
				it.remove();
			}
		}
		return out;
	}

	/** Blocks of griefing waiting in open batches. */
	public int pending() {
		int n = 0;
		for (Open o : this.open.values()) {
			n += o.count;
		}
		return n;
	}

	public void clear() {
		this.open.clear();
	}
}
