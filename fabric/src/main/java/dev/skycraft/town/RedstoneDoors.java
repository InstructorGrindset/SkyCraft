package dev.skycraft.town;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dwemer doors near the player, as Skyrim names them (IN_DWEMER_DOOR: the block at each door's foot), and the redstone
 * power there: Minecraft tells Skyrim when power comes to a door or goes (EV_REDSTONE_DOOR). A door seen for the first
 * time takes the power it has then as its state, so finding a door never works it; one Skyrim stops naming is
 * forgotten. Pure: the caller says what's powered.
 */
public final class RedstoneDoors {
	/** Power at a door's block came ({@code powered}) or went. */
	public record Change(int x, int y, int z, boolean powered) {
	}

	/** Whether redstone powers the door at this block (its foot) or the one above; null when that can't be told now (unloaded). */
	public interface Power {
		@org.jspecify.annotations.Nullable Boolean at(int x, int y, int z);
	}

	private static final class Door {
		final int x, y, z;
		long seen;
		boolean open;
		@org.jspecify.annotations.Nullable Boolean powered; // null: not looked at yet

		Door(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private final Map<Long, Door> doors = new LinkedHashMap<>();

	private static long key(int x, int y, int z) {
		return PlotTally.key(x, y, z);
	}

	/** Skyrim named the door at this block at tick {@code now} ({@code open}: it's open in Skyrim now). */
	public void seen(int x, int y, int z, boolean open, long now) {
		Door d = this.doors.computeIfAbsent(key(x, y, z), k -> new Door(x, y, z));
		d.seen = now;
		d.open = open;
	}

	/**
	 * Looks at the power at every door, forgetting those Skyrim hasn't named for {@code forgetTicks}, and returns where it
	 * came or went since the last look.
	 */
	public List<Change> update(long now, int forgetTicks, Power power) {
		List<Change> out = new ArrayList<>();
		for (var it = this.doors.values().iterator(); it.hasNext();) {
			Door d = it.next();
			if (now - d.seen > forgetTicks) {
				it.remove();
				continue;
			}
			Boolean on = power.at(d.x, d.y, d.z);
			if (on == null) {
				continue; // as it was
			}
			if (d.powered != null && !d.powered.equals(on)) {
				out.add(new Change(d.x, d.y, d.z, on));
			}
			d.powered = on;
		}
		return out;
	}

	/** Every door known: {x, y, z, open (1/0), powered (1/0, -1 not looked at yet)}. */
	public List<int[]> all() {
		List<int[]> out = new ArrayList<>();
		for (Door d : this.doors.values()) {
			out.add(new int[] { d.x, d.y, d.z, d.open ? 1 : 0, d.powered == null ? -1 : d.powered ? 1 : 0 });
		}
		return out;
	}

	public int size() {
		return this.doors.size();
	}

	public void clear() {
		this.doors.clear();
	}
}
