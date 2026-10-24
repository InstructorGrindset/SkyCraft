package dev.skycraft.quests;

import static dev.skycraft.link.Proto.MAX_QUEST_TARGETS;
import static dev.skycraft.link.Proto.QR_FLAGS;
import static dev.skycraft.link.Proto.QR_NAME;
import static dev.skycraft.link.Proto.QR_NAME_BYTES;
import static dev.skycraft.link.Proto.QR_OBJECTIVE;
import static dev.skycraft.link.Proto.QR_OBJECTIVE_TEXT;
import static dev.skycraft.link.Proto.QR_OBJECTIVE_TEXT_BYTES;
import static dev.skycraft.link.Proto.QR_QUEST;
import static dev.skycraft.link.Proto.QR_QUEST_TYPE;
import static dev.skycraft.link.Proto.QR_REF;
import static dev.skycraft.link.Proto.QR_X;
import static dev.skycraft.link.Proto.QR_Y;
import static dev.skycraft.link.Proto.QR_Z;
import static dev.skycraft.link.Proto.QT_COUNT;
import static dev.skycraft.link.Proto.QT_RECORDS;
import static dev.skycraft.link.Proto.QT_SEQ;
import static dev.skycraft.link.Proto.QT_WORLD_ID;
import static dev.skycraft.link.Proto.QUEST_ACTOR;
import static dev.skycraft.link.Proto.QUEST_INSIDE;
import static dev.skycraft.link.Proto.QUEST_MAIN;
import static dev.skycraft.link.Proto.QUEST_RECORD_BYTES;
import static dev.skycraft.link.Proto.QUEST_TABLE_BYTES;
import static dev.skycraft.link.Proto.QUEST_TRACKED;
import static dev.skycraft.link.Proto.QUEST_VIA_DOOR;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim's quest target table (QuestTargetTable in the protocol header, at Proto.OFF_QUEST_TARGETS) made sense of: the targets
 * Skyrim's compass has for the player's quests, tracked quests first, then nearest first. SkyLink copies the table's bytes out
 * of shared memory under its seqlock; this reads them, checking every count and number, so a table from a plugin that never
 * wrote one (all zeros) or one with a broken record never becomes a marker. Pure Java.
 */
public final class QuestTable {
	private QuestTable() {
	}

	/**
	 * One quest target: where its marker is (the target, or with {@link #viaDoor} the load door Skyrim's compass points to),
	 * in the terms of the Skyrim world the table names until {@link #moved} into its space in the mirror; the quest's FormID,
	 * the reference the marker is on, the objective's index (Proto.QUEST_NO_OBJECTIVE if Skyrim found none), the
	 * Proto.QUEST_* flags, the quest's type (Skyrim's QUEST_DATA::Type), its name, and the objective's text as Skyrim keeps it
	 * (it may hold {@code <Alias=...>} tags).
	 */
	public record Target(int quest, int ref, int objective, int flags, int questType, double x, double y, double z, String name, String objectiveText) {
		/** The quest is active in Skyrim's journal: Skyrim's compass shows it. */
		public boolean tracked() {
			return (this.flags & QUEST_TRACKED) != 0;
		}

		/** The target is in another Skyrim world, and the marker is on the door that leads there. */
		public boolean viaDoor() {
			return (this.flags & QUEST_VIA_DOOR) != 0;
		}

		/** The target itself is inside (an interior cell). */
		public boolean inside() {
			return (this.flags & QUEST_INSIDE) != 0;
		}

		/** The main quest. */
		public boolean main() {
			return (this.flags & QUEST_MAIN) != 0;
		}

		/** The marker is on an actor. */
		public boolean actor() {
			return (this.flags & QUEST_ACTOR) != 0;
		}

		/** The same target moved by {@code dx, dz} blocks (into its world's space in the mirror). */
		public Target moved(double dx, double dz) {
			return new Target(this.quest, this.ref, this.objective, this.flags, this.questType, this.x + dx, this.y, this.z + dz, this.name, this.objectiveText);
		}
	}

	/** A whole table: its sequence number, the Skyrim world (SkyState worldId) its positions are in, and its targets in Skyrim's order. */
	public record Snapshot(int seq, int world, List<Target> targets) {
	}

	/**
	 * The table in {@code bytes} (from its start, at least Proto.QUEST_TABLE_BYTES of them; read little endian whatever the
	 * buffer's order). Null when Skyrim never wrote it (sequence 0: an older plugin, or quest markers never on) or was writing
	 * it (odd). Records with a position that isn't a number are left out; the count is held to the table's size.
	 */
	public static @Nullable Snapshot decode(ByteBuffer bytes) {
		ByteBuffer b = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		int base = b.position();
		if (b.remaining() < QUEST_TABLE_BYTES) {
			return null;
		}
		int seq = b.getInt(base + (int) QT_SEQ);
		if (seq == 0 || (seq & 1) != 0) {
			return null;
		}
		int count = Math.max(0, Math.min(b.getInt(base + (int) QT_COUNT), MAX_QUEST_TARGETS));
		int world = b.getInt(base + (int) QT_WORLD_ID);
		List<Target> targets = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int r = base + (int) QT_RECORDS + i * QUEST_RECORD_BYTES;
			float x = b.getFloat(r + QR_X), y = b.getFloat(r + QR_Y), z = b.getFloat(r + QR_Z);
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
				continue;
			}
			targets.add(new Target(b.getInt(r + QR_QUEST), b.getInt(r + QR_REF), Short.toUnsignedInt(b.getShort(r + QR_OBJECTIVE)),
				Byte.toUnsignedInt(b.get(r + QR_FLAGS)), Byte.toUnsignedInt(b.get(r + QR_QUEST_TYPE)), x, y, z, text(b, r + QR_NAME, QR_NAME_BYTES),
				text(b, r + QR_OBJECTIVE_TEXT, QR_OBJECTIVE_TEXT_BYTES)));
		}
		return new Snapshot(seq, world, List.copyOf(targets));
	}

	/** A NUL-terminated UTF-8 text of at most {@code max} bytes at {@code at}, control characters left out. */
	static String text(ByteBuffer b, int at, int max) {
		int n = 0;
		while (n < max && b.get(at + n) != 0) {
			n++;
		}
		byte[] bytes = new byte[n];
		for (int i = 0; i < n; i++) {
			bytes[i] = b.get(at + i);
		}
		StringBuilder sb = new StringBuilder();
		new String(bytes, StandardCharsets.UTF_8).codePoints().filter(c -> !Character.isISOControl(c)).forEach(sb::appendCodePoint);
		return sb.toString().strip();
	}
}
