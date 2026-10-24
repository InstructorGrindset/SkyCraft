package dev.skycraft.quests;

import static dev.skycraft.link.Proto.MAX_QUEST_TARGETS;
import static dev.skycraft.link.Proto.QR_NAME_BYTES;
import static dev.skycraft.link.Proto.QR_OBJECTIVE_TEXT_BYTES;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A guest's own Skyrim's quest targets on the way to the host's server (quest markers in a friend's world): packed into a
 * plain list of ints for SkyNet's QuestTargets packet, positions already in the mirror's terms, and read back with every
 * count, length and number checked, so a broken or doctored packet is refused rather than shown. Pure Java.
 */
public final class QuestCodec {
	/** Changes when the layout does: a packet of another layout is refused, not misread. */
	public static final int VERSION = 1;
	/** Ints a target takes at most: quest, ref, objective|flags|type, x, y, z, and the two texts with their lengths. */
	private static final int TARGET_INTS = 6 + 1 + QR_NAME_BYTES / 4 + 1 + QR_OBJECTIVE_TEXT_BYTES / 4;
	/** The most ints a packet can need (SkyNet sizes its list codec by it). */
	public static final int MAX_INTS = 2 + MAX_QUEST_TARGETS * TARGET_INTS;

	private QuestCodec() {
	}

	/** Up to Proto.MAX_QUEST_TARGETS targets (the first ones), packed. */
	public static List<Integer> encode(List<QuestTable.Target> targets) {
		List<Integer> out = new ArrayList<>();
		out.add(VERSION);
		int n = Math.min(targets.size(), MAX_QUEST_TARGETS);
		out.add(n);
		for (int i = 0; i < n; i++) {
			QuestTable.Target t = targets.get(i);
			out.add(t.quest());
			out.add(t.ref());
			out.add(t.objective() & 0xFFFF | (t.flags() & 0xFF) << 16 | (t.questType() & 0xFF) << 24);
			out.add(Float.floatToRawIntBits((float) t.x()));
			out.add(Float.floatToRawIntBits((float) t.y()));
			out.add(Float.floatToRawIntBits((float) t.z()));
			putString(out, t.name(), QR_NAME_BYTES);
			putString(out, t.objectiveText(), QR_OBJECTIVE_TEXT_BYTES);
		}
		return out;
	}

	/** Reads what {@link #encode} packed. Throws IllegalArgumentException for anything that isn't exactly that. */
	public static List<QuestTable.Target> decode(List<Integer> data) {
		Reader r = new Reader(data);
		if (r.next() != VERSION) {
			throw new IllegalArgumentException("not a version " + VERSION + " quest target list");
		}
		int n = r.next();
		if (n < 0 || n > MAX_QUEST_TARGETS) {
			throw new IllegalArgumentException(n + " quest targets");
		}
		List<QuestTable.Target> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int quest = r.next(), ref = r.next(), bits = r.next();
			float x = r.finite(), y = r.finite(), z = r.finite();
			String name = r.string(QR_NAME_BYTES), objective = r.string(QR_OBJECTIVE_TEXT_BYTES);
			out.add(new QuestTable.Target(quest, ref, bits & 0xFFFF, bits >>> 16 & 0xFF, bits >>> 24, x, y, z, name, objective));
		}
		r.end();
		return out;
	}

	/** {@code s} as at most {@code maxBytes} of UTF-8 (cut at a character's start), its byte count first, four bytes to an int. */
	private static void putString(List<Integer> out, String s, int maxBytes) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		int n = Math.min(bytes.length, maxBytes);
		while (n > 0 && n < bytes.length && (bytes[n] & 0xC0) == 0x80) {
			n--;
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

		float finite() {
			float f = Float.intBitsToFloat(this.next());
			if (!Float.isFinite(f)) {
				throw new IllegalArgumentException("not a number");
			}
			return f;
		}

		/** A text of at most {@code maxBytes}, control characters left out. */
		String string(int maxBytes) {
			int n = this.next();
			if (n < 0 || n > maxBytes) {
				throw new IllegalArgumentException("a text of " + n + " bytes");
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
			return sb.toString().strip();
		}

		void end() {
			if (this.at != this.data.size()) {
				throw new IllegalArgumentException((this.data.size() - this.at) + " ints too many");
			}
		}
	}
}
