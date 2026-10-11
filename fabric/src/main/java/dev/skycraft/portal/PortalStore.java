package dev.skycraft.portal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The portal network of one Minecraft world (pure; SkyPortals keeps it in the world's save folder as
 * {@code skycraft-portals.txt}). A portal is a lit Nether portal's rectangle of portal blocks plus the
 * Skyrim world it was opened in: Skyrim's worlds (worldspaces and interior cells) all share one set of
 * Minecraft coordinates, so a place is a Skyrim world id and a Minecraft position.
 *
 * <p>Portals are cracks between places that resemble each other: each is bound to exactly one other
 * portal, for good ({@link #bind}), and leads only there ({@link #destination}). Every portal and its
 * {@link Portal#exit} stays listed, so a later free travel mode (Seamstone) can open onto any known
 * portal instead of the partner. One line per portal:
 * {@code <id> <world hex> <flags> <x> <y> <z> <axis X|Z> <width> <height> <side> <partner> <place>},
 * plus {@code next <id>} (ids are never reused, so an attuned gem can't find a newer portal by mistake).
 */
public final class PortalStore {
	/** Portal blocks run along X (the frame faces north and south). */
	public static final int AXIS_X = 0;
	/** Portal blocks run along Z (the frame faces east and west). */
	public static final int AXIS_Z = 1;
	/** Portal flags: opened in a Skyrim interior cell ({@code world} is that cell's FormID). */
	public static final int INTERIOR = 1;
	/** The largest portal Minecraft lights (PortalShape.MAX_WIDTH and MAX_HEIGHT). */
	public static final int MAX_SIZE = 21;

	public static final String HEADER = "# SkyCraft portal network: id world flags x y z axis width height side partner place";

	/** The network's items (PortalItems registers them as skycraft:<id>): the filled and the attuned soul gem. */
	public static final String FILLED_GEM_ID = "filled_soul_gem";
	public static final String ATTUNED_GEM_ID = "attuned_soul_gem";
	public static final List<String> ITEM_IDS = List.of(FILLED_GEM_ID, ATTUNED_GEM_ID);

	/**
	 * One portal. {@code x, y, z} is its lowest portal block toward negative X or Z, {@code width} runs
	 * along its axis, {@code height} up. {@code side} (+1 or -1, along the axis across the portal) is the
	 * side travellers step out on: the side the player stood on when they attuned or bound it.
	 * {@code partner} is the bound portal's id, 0 when unbound. {@code place} is Skyrim's name for where
	 * it is (SkyState town), maybe empty.
	 */
	public record Portal(int id, int world, int flags, int x, int y, int z, int axis, int width, int height, int side, int partner, String place) {
		public boolean interior() {
			return (this.flags & INTERIOR) != 0;
		}

		public boolean bound() {
			return this.partner != 0;
		}

		public int maxX() {
			return this.axis == AXIS_X ? this.x + this.width - 1 : this.x;
		}

		public int maxZ() {
			return this.axis == AXIS_Z ? this.z + this.width - 1 : this.z;
		}

		public int maxY() {
			return this.y + this.height - 1;
		}

		/** Whether the block is one of its portal blocks. */
		public boolean contains(int bx, int by, int bz) {
			return bx >= this.x && bx <= this.maxX() && by >= this.y && by <= this.maxY() && bz >= this.z && bz <= this.maxZ();
		}

		/** Whether the block is one of its portal blocks or its frame (one block round them, in their plane). */
		public boolean frameContains(int bx, int by, int bz) {
			if (by < this.y - 1 || by > this.maxY() + 1) {
				return false;
			}
			if (this.axis == AXIS_X) {
				return bz == this.z && bx >= this.x - 1 && bx <= this.maxX() + 1;
			}
			return bx == this.x && bz >= this.z - 1 && bz <= this.maxZ() + 1;
		}

		/** Whether it is exactly this rectangle of portal blocks. */
		public boolean sameShape(int sx, int sy, int sz, int sAxis, int sWidth, int sHeight) {
			return this.x == sx && this.y == sy && this.z == sz && this.axis == sAxis && this.width == sWidth && this.height == sHeight;
		}

		public Portal withPartner(int p) {
			return new Portal(this.id, this.world, this.flags, this.x, this.y, this.z, this.axis, this.width, this.height, this.side, p, this.place);
		}

		public Portal withSide(int s) {
			return new Portal(this.id, this.world, this.flags, this.x, this.y, this.z, this.axis, this.width, this.height, s < 0 ? -1 : 1, this.partner,
				this.place);
		}

		/**
		 * Where a traveller arrives: feet {x, y, z} in the middle of the block just in front of the portal
		 * on its {@code side}, at the height of its lowest portal blocks, and the yaw facing away from it
		 * (MC degrees: 0 south, 90 west, 180 north, -90 east).
		 */
		public double[] exit() {
			if (this.axis == AXIS_X) {
				return new double[] { this.x + this.width / 2.0, this.y, this.z + 0.5 + this.side, this.side > 0 ? 0.0 : 180.0 };
			}
			return new double[] { this.x + 0.5 + this.side, this.y, this.z + this.width / 2.0, this.side > 0 ? -90.0 : 90.0 };
		}

		/** The middle of its portal blocks. */
		public double[] center() {
			return new double[] { (this.x + this.maxX() + 1) / 2.0, (this.y + this.maxY() + 1) / 2.0, (this.z + this.maxZ() + 1) / 2.0 };
		}
	}

	/** What {@link #bind} did. */
	public enum Bind {
		/** The two are bound to each other now. */
		BOUND,
		/** Both ids are the same portal. */
		SAME,
		/** One of them isn't in the network. */
		MISSING,
		/** One of them is bound already (bonds are for good). */
		ALREADY_BOUND
	}

	private final Map<Integer, Portal> portals = new LinkedHashMap<>();
	private int next = 1;

	/** The side of a portal on this axis that a point is on (+1 or -1), for {@link Portal#side}. */
	public static int sideOf(int axis, int x, int z, double px, double pz) {
		return axis == AXIS_X ? (pz >= z + 0.5 ? 1 : -1) : (px >= x + 0.5 ? 1 : -1);
	}

	public @Nullable Portal get(int id) {
		return this.portals.get(id);
	}

	public Collection<Portal> all() {
		return this.portals.values();
	}

	public int size() {
		return this.portals.size();
	}

	/** The id the next new portal gets. */
	public int nextId() {
		return this.next;
	}

	/** The portal of this Skyrim world one of whose portal blocks this is, or null. */
	public @Nullable Portal at(int world, int bx, int by, int bz) {
		for (Portal p : this.portals.values()) {
			if (p.world() == world && p.contains(bx, by, bz)) {
				return p;
			}
		}
		return null;
	}

	/** Any Skyrim world's portal one of whose portal blocks this is (the blocks are there whichever world it is), or null. */
	public @Nullable Portal anyAt(int bx, int by, int bz) {
		for (Portal p : this.portals.values()) {
			if (p.contains(bx, by, bz)) {
				return p;
			}
		}
		return null;
	}

	/** Every portal whose portal blocks or frame take in this block. */
	public List<Portal> touching(int bx, int by, int bz) {
		List<Portal> out = new ArrayList<>();
		for (Portal p : this.portals.values()) {
			if (p.frameContains(bx, by, bz)) {
				out.add(p);
			}
		}
		return out;
	}

	/** Every portal (any Skyrim world) sharing a portal block with this rectangle. */
	public List<Portal> overlapping(int x, int y, int z, int axis, int width, int height) {
		int maxX = axis == AXIS_X ? x + width - 1 : x;
		int maxZ = axis == AXIS_Z ? z + width - 1 : z;
		int maxY = y + height - 1;
		List<Portal> out = new ArrayList<>();
		for (Portal p : this.portals.values()) {
			if (p.x() <= maxX && p.maxX() >= x && p.y() <= maxY && p.maxY() >= y && p.z() <= maxZ && p.maxZ() >= z) {
				out.add(p);
			}
		}
		return out;
	}

	/** A new, unbound portal with the next id. */
	public Portal add(int world, int flags, int x, int y, int z, int axis, int width, int height, int side, String place) {
		Portal p = new Portal(this.next++, world, flags, x, y, z, axis, width, height, side < 0 ? -1 : 1, 0, clean(place));
		this.portals.put(p.id(), p);
		return p;
	}

	/** Replaces the portal with this id (keeps the network's order). */
	public void put(Portal p) {
		this.portals.put(p.id(), p);
		this.next = Math.max(this.next, p.id() + 1);
	}

	/** Takes a portal out of the network; its partner (if any) is unbound. Returns the removed portal, or null. */
	public @Nullable Portal remove(int id) {
		Portal p = this.portals.remove(id);
		if (p != null && p.bound()) {
			Portal partner = this.portals.get(p.partner());
			if (partner != null && partner.partner() == id) {
				this.portals.put(partner.id(), partner.withPartner(0));
			}
		}
		return p;
	}

	/** Binds two unbound portals to each other, for good. */
	public Bind bind(int a, int b) {
		if (a == b) {
			return Bind.SAME;
		}
		Portal pa = this.portals.get(a), pb = this.portals.get(b);
		if (pa == null || pb == null) {
			return Bind.MISSING;
		}
		if (pa.bound() || pb.bound()) {
			return Bind.ALREADY_BOUND;
		}
		this.portals.put(a, pa.withPartner(b));
		this.portals.put(b, pb.withPartner(a));
		return Bind.BOUND;
	}

	/**
	 * Where stepping into this portal leads: its partner (the lock), or null when unbound. A later free
	 * travel mode picks any other portal of {@link #all} here instead.
	 */
	public @Nullable Portal destination(Portal from) {
		return from.bound() ? this.portals.get(from.partner()) : null;
	}

	private static String clean(String place) {
		return place == null ? "" : place.replace('\n', ' ').replace('\r', ' ').strip();
	}

	public static String format(Portal p) {
		return String.format(Locale.ROOT, "%d %08X %d %d %d %d %s %d %d %d %d %s", p.id(), p.world(), p.flags(), p.x(), p.y(), p.z(), p.axis() == AXIS_X ? "X" : "Z",
			p.width(), p.height(), p.side(), p.partner(), clean(p.place())).stripTrailing();
	}

	public List<String> lines() {
		List<String> out = new ArrayList<>(this.portals.size() + 2);
		out.add(HEADER);
		out.add("next " + this.next);
		for (Portal p : this.portals.values()) {
			out.add(format(p));
		}
		return out;
	}

	/**
	 * Reads lines written by {@link #lines}; bad ones go to {@code problems}. Later lines win. A bond whose
	 * other half is missing or bound elsewhere is dropped (reported), so every bond is mutual.
	 */
	public static PortalStore parse(List<String> lines, List<String> problems) {
		PortalStore store = new PortalStore();
		int next = 1;
		int n = 0;
		for (String raw : lines) {
			n++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("next ")) {
				try {
					next = Math.max(next, Integer.parseInt(line.substring(5).strip()));
				} catch (NumberFormatException e) {
					problems.add("line " + n + ": bad next id");
				}
				continue;
			}
			String[] p = line.split("\\s+", 12);
			if (p.length < 11) {
				problems.add("line " + n + ": expected 'id world flags x y z axis width height side partner place'");
				continue;
			}
			try {
				int id = Integer.parseInt(p[0]);
				int world = Integer.parseUnsignedInt(p[1], 16);
				int flags = Integer.parseInt(p[2]);
				int x = Integer.parseInt(p[3]);
				int y = Integer.parseInt(p[4]);
				int z = Integer.parseInt(p[5]);
				int axis = switch (p[6].toUpperCase(Locale.ROOT)) {
					case "X" -> AXIS_X;
					case "Z" -> AXIS_Z;
					default -> -1;
				};
				int width = Integer.parseInt(p[7]);
				int height = Integer.parseInt(p[8]);
				int side = Integer.parseInt(p[9]);
				int partner = Integer.parseInt(p[10]);
				if (id < 1 || axis < 0 || width < 1 || width > MAX_SIZE || height < 1 || height > MAX_SIZE || (side != 1 && side != -1) || partner < 0
					|| flags < 0) {
					problems.add("line " + n + ": id, axis, size, side, partner or flags out of range");
					continue;
				}
				String place = p.length > 11 ? p[11].strip() : "";
				store.portals.put(id, new Portal(id, world, flags, x, y, z, axis, width, height, side, partner, place));
				next = Math.max(next, id + 1);
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": " + e.getMessage());
			}
		}
		for (Portal p : List.copyOf(store.portals.values())) {
			if (!p.bound()) {
				continue;
			}
			Portal other = store.portals.get(p.partner());
			if (other == null || other.partner() != p.id() || other.id() == p.id()) {
				problems.add("portal " + p.id() + ": its bond to " + p.partner() + " isn't mutual; unbound");
				store.portals.put(p.id(), p.withPartner(0));
			}
		}
		store.next = next;
		return store;
	}
}
