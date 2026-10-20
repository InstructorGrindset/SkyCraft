package dev.skycraft.party;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * How a guest's own Skyrim reaches the host's server (guest parity): its actor table and its loot, packed into plain
 * lists of ints for SkyNet's packets (the list codec SkyNet already sends the world spaces with), and read back with
 * every count, length and number checked, so a broken or doctored packet is refused rather than believed. What the
 * values mean is still only the guest's word: GuestParity holds them to distances and rates. Pure Java.
 */
public final class PartyCodec {
	/** Changes when the layout does: a packet of another layout is refused, not misread. */
	public static final int VERSION = 1;
	/** As the protocol's ActorRecord, ItemEvent and SkyState have them. */
	public static final int ACTOR_NAME_BYTES = 24, ITEM_NAME_BYTES = 28, TOWN_BYTES = 32;
	/** A packet's most: actors (the nearest), and items (the Skyrim item ring's size). */
	public static final int MAX_ACTORS = 64, MAX_ITEMS = 64;
	/** Most ints a packet of each kind can need (SkyNet sizes its list codecs by these). */
	public static final int MAX_ACTOR_INTS = 6 + TOWN_BYTES / 4 + 1 + MAX_ACTORS * (9 + 1 + ACTOR_NAME_BYTES / 4);
	public static final int MAX_ITEM_INTS = 2 + MAX_ITEMS * (5 + 1 + ITEM_NAME_BYTES / 4);
	/** A Skyrim item count past this is no pickup (Skyrim's own stack counts are far smaller). */
	public static final int MAX_COUNT = 100_000;

	private static final int AWAY = 1;

	private PartyCodec() {
	}

	/**
	 * The guest's Skyrim as its SkyState says: the world (worldspace or cell) its player is in, its place flags, its game
	 * hour, and the town or location's name. {@code away}: the guest is in a dimension other than the mirror (their Skyrim
	 * is parked, so no actors).
	 */
	public record Header(int world, int place, float hour, boolean away, String town) {
	}

	/** One actor of the guest's actor table, its feet already in the mirror's terms (the guest's client moved it into its world's space). */
	public record Actor(int formId, int flags, float x, float y, float z, float yaw, float width, float height, float health, int level, int vendor,
		String name) {
	}

	public record ActorTable(Header header, List<Actor> actors) {
	}

	/** One item event from the guest's Skyrim item ring (see ItemEvent in the protocol header). */
	public record Item(int formId, int count, int tags, int material, int source, boolean chest, boolean quest, int container, String name) {
	}

	// ---- actors --------------------------------------------------------------------------------

	/** The header and up to {@link #MAX_ACTORS} actors (the first ones), packed. */
	public static List<Integer> actors(Header header, List<Actor> actors) {
		List<Integer> out = new ArrayList<>();
		out.add(VERSION);
		out.add(header.world());
		out.add(header.place());
		out.add(Float.floatToRawIntBits(header.hour()));
		out.add(header.away() ? AWAY : 0);
		putString(out, header.town(), TOWN_BYTES);
		int n = Math.min(actors.size(), MAX_ACTORS);
		out.add(n);
		for (int i = 0; i < n; i++) {
			Actor a = actors.get(i);
			out.add(a.formId());
			out.add(a.flags());
			out.add(Float.floatToRawIntBits(a.x()));
			out.add(Float.floatToRawIntBits(a.y()));
			out.add(Float.floatToRawIntBits(a.z()));
			out.add(Float.floatToRawIntBits(a.yaw()));
			out.add(size(a.width()) | size(a.height()) << 16);
			out.add(Float.floatToRawIntBits(a.health()));
			out.add(a.level() & 0xFFFF | (a.vendor() & 0xFFFF) << 16);
			putString(out, a.name(), ACTOR_NAME_BYTES);
		}
		return out;
	}

	/** Reads what {@link #actors} packed. Throws IllegalArgumentException for anything that isn't exactly that. */
	public static ActorTable readActors(List<Integer> data) {
		Reader r = new Reader(data);
		if (r.next() != VERSION) {
			throw new IllegalArgumentException("not a version " + VERSION + " actor table");
		}
		int world = r.next(), place = r.next();
		float hour = r.nextFloat();
		boolean away = (r.next() & AWAY) != 0;
		String town = r.string(TOWN_BYTES);
		int n = r.next();
		if (n < 0 || n > MAX_ACTORS) {
			throw new IllegalArgumentException(n + " actors");
		}
		List<Actor> actors = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int formId = r.next(), flags = r.next();
			float x = r.finite(), y = r.finite(), z = r.finite(), yaw = r.finite();
			int sizes = r.next();
			float health = r.finite();
			int levels = r.next();
			String name = r.string(ACTOR_NAME_BYTES);
			actors.add(new Actor(formId, flags, x, y, z, yaw, unsize(sizes & 0xFFFF), unsize(sizes >>> 16), Math.max(0.0F, Math.min(1.0F, health)),
				levels & 0xFFFF, levels >>> 16, name));
		}
		r.end();
		return new ActorTable(new Header(world, place, Float.isFinite(hour) ? hour : -1.0F, away, town), actors);
	}

	/** A stand-in's width or height (blocks) in hundredths, within what Skyrim sends (0.3 to 12). */
	private static int size(float blocks) {
		return Math.round(Math.max(0.3F, Math.min(12.0F, Float.isFinite(blocks) ? blocks : 0.6F)) * 100.0F);
	}

	private static float unsize(int hundredths) {
		return Math.max(0.3F, Math.min(12.0F, hundredths / 100.0F));
	}

	// ---- items ---------------------------------------------------------------------------------

	/** Up to {@link #MAX_ITEMS} item events (the first ones), packed. */
	public static List<Integer> items(List<Item> items) {
		List<Integer> out = new ArrayList<>();
		out.add(VERSION);
		int n = Math.min(items.size(), MAX_ITEMS);
		out.add(n);
		for (int i = 0; i < n; i++) {
			Item it = items.get(i);
			out.add(it.formId());
			out.add(it.count());
			out.add(it.tags());
			out.add(it.material() & 0xFF | (it.source() & 0xFF) << 8 | (it.chest() ? 1 << 16 : 0) | (it.quest() ? 1 << 24 : 0));
			out.add(it.container());
			putString(out, it.name(), ITEM_NAME_BYTES);
		}
		return out;
	}

	/** Reads what {@link #items} packed. Throws IllegalArgumentException for anything that isn't exactly that. */
	public static List<Item> readItems(List<Integer> data) {
		Reader r = new Reader(data);
		if (r.next() != VERSION) {
			throw new IllegalArgumentException("not a version " + VERSION + " item list");
		}
		int n = r.next();
		if (n < 0 || n > MAX_ITEMS) {
			throw new IllegalArgumentException(n + " items");
		}
		List<Item> items = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int formId = r.next(), count = r.next(), tags = r.next(), bits = r.next(), container = r.next();
			String name = r.string(ITEM_NAME_BYTES);
			if (count < -MAX_COUNT || count > MAX_COUNT) {
				throw new IllegalArgumentException("a count of " + count);
			}
			items.add(new Item(formId, count, tags, bits & 0xFF, bits >>> 8 & 0xFF, (bits >>> 16 & 0xFF) != 0, (bits >>> 24) != 0, container, name));
		}
		r.end();
		return items;
	}

	// ---- names ---------------------------------------------------------------------------------

	/** A name on its own (GuestDragon's), at most {@code maxBytes} of UTF-8. */
	public static List<Integer> name(String name, int maxBytes) {
		List<Integer> out = new ArrayList<>();
		putString(out, name, maxBytes);
		return out;
	}

	/** Reads what {@link #name} packed. Throws IllegalArgumentException for anything that isn't exactly that. */
	public static String readName(List<Integer> data, int maxBytes) {
		Reader r = new Reader(data);
		String name = r.string(maxBytes);
		r.end();
		return name;
	}

	/** {@code s} as at most {@code maxBytes} of UTF-8 (cut at a character's start), its byte count first, four bytes to an int. */
	static void putString(List<Integer> out, String s, int maxBytes) {
		byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
		int n = Math.min(bytes.length, maxBytes);
		while (n > 0 && n < bytes.length && (bytes[n] & 0xC0) == 0x80) {
			n--; // don't split a character
		}
		out.add(n);
		for (int i = 0; i < n; i += 4) {
			int word = 0;
			for (int k = 0; k < 4 && i + k < n; k++) {
				word |= (bytes[i + k] & 0xFF) << (8 * k);
			}
			out.add(word);
		}
	}

	private static final class Reader {
		private final List<Integer> data;
		private int at;

		Reader(List<Integer> data) {
			this.data = data;
		}

		int next() {
			if (this.at >= this.data.size()) {
				throw new IllegalArgumentException("cut short");
			}
			Integer v = this.data.get(this.at++);
			if (v == null) {
				throw new IllegalArgumentException("a hole");
			}
			return v;
		}

		float nextFloat() {
			return Float.intBitsToFloat(this.next());
		}

		float finite() {
			float f = this.nextFloat();
			if (!Float.isFinite(f)) {
				throw new IllegalArgumentException("not a number");
			}
			return f;
		}

		/** A name: at most {@code maxBytes}, with control characters left out. */
		String string(int maxBytes) {
			int n = this.next();
			if (n < 0 || n > maxBytes) {
				throw new IllegalArgumentException("a name of " + n + " bytes");
			}
			byte[] bytes = new byte[n];
			for (int i = 0; i < n; i += 4) {
				int word = this.next();
				for (int k = 0; k < 4 && i + k < n; k++) {
					bytes[i + k] = (byte) (word >>> (8 * k));
				}
			}
			StringBuilder sb = new StringBuilder();
			new String(bytes, StandardCharsets.UTF_8).codePoints().filter(c -> !Character.isISOControl(c)).forEach(sb::appendCodePoint);
			return sb.toString();
		}

		void end() {
			if (this.at != this.data.size()) {
				throw new IllegalArgumentException((this.data.size() - this.at) + " ints too many");
			}
		}
	}
}
