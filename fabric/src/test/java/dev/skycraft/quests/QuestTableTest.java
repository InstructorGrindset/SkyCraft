package dev.skycraft.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QuestTableTest {
	/** A table laid out the way Skyrim's Link::WriteQuestTargets writes proto::QuestTargetTable. */
	private static ByteBuffer table(int seq, int count, int world) {
		ByteBuffer b = ByteBuffer.allocate(Proto.QUEST_TABLE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(0, seq);
		b.putInt(4, count);
		b.putInt(8, world);
		return b;
	}

	private static void record(ByteBuffer b, int i, float x, float y, float z, int quest, int ref, int objective, int flags, int type, byte[] name,
		byte[] text) {
		int r = 16 + i * 104;
		b.putFloat(r, x);
		b.putFloat(r + 4, y);
		b.putFloat(r + 8, z);
		b.putInt(r + 12, quest);
		b.putInt(r + 16, ref);
		b.putShort(r + 20, (short) objective);
		b.put(r + 22, (byte) flags);
		b.put(r + 23, (byte) type);
		for (int k = 0; k < name.length; k++) {
			b.put(r + 24 + k, name[k]);
		}
		for (int k = 0; k < text.length; k++) {
			b.put(r + 64 + k, text[k]);
		}
	}

	private static byte[] utf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void theLayoutIsTheProtocolHeaders() {
		assertEquals(16 + 16 * 104, Proto.QUEST_TABLE_BYTES);
		assertEquals(0x690, Proto.QUEST_TABLE_BYTES);
		assertTrue(Proto.OFF_QUEST_TARGETS >= Proto.OFF_INPUT_RING + Proto.IR_DATA + 16L * Proto.INPUT_RING_ENTRIES, "after the input ring");
		assertTrue(Proto.OFF_QUEST_TARGETS + Proto.QUEST_TABLE_BYTES <= 0x11800, "within the quests' 0x700 bytes");
	}

	private static String bit(int value) {
		return "1u << " + Integer.numberOfTrailingZeros(value);
	}

	/** Proto.java and protocol/skycraft_protocol.h agree on the quest table. */
	@Test
	void matchesTheProtocolHeader() throws java.io.IOException {
		java.nio.file.Path header = null;
		for (java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath(); dir != null && header == null; dir = dir.getParent()) {
			java.nio.file.Path candidate = dir.resolve("protocol/skycraft_protocol.h");
			if (java.nio.file.Files.exists(candidate)) {
				header = candidate;
			}
		}
		assertNotNull(header, "protocol/skycraft_protocol.h not found above " + java.nio.file.Path.of("").toAbsolutePath());
		String text = java.nio.file.Files.readString(header);
		String[] expected = {
			"kOffQuestTargets = 0x" + Long.toHexString(Proto.OFF_QUEST_TARGETS),
			"kMaxQuestTargets = " + Proto.MAX_QUEST_TARGETS,
			"kQuestNameBytes = " + Proto.QR_NAME_BYTES,
			"kQuestObjectiveBytes = " + Proto.QR_OBJECTIVE_TEXT_BYTES,
			"kQuestNoObjective = 0x" + Integer.toHexString(Proto.QUEST_NO_OBJECTIVE).toUpperCase(),
			"kQuestTracked = " + bit(Proto.QUEST_TRACKED),
			"kQuestViaDoor = " + bit(Proto.QUEST_VIA_DOOR),
			"kQuestInside = " + bit(Proto.QUEST_INSIDE),
			"kQuestMain = " + bit(Proto.QUEST_MAIN),
			"kQuestActor = " + bit(Proto.QUEST_ACTOR),
			"kFeatQuestMarkers = " + bit(Proto.FEATURE_QUEST_MARKERS),
			"sizeof(QuestTargetRecord) == " + Proto.QUEST_RECORD_BYTES,
			"offsetof(QuestTargetRecord, quest) == " + Proto.QR_QUEST,
			"offsetof(QuestTargetRecord, ref) == " + Proto.QR_REF,
			"offsetof(QuestTargetRecord, objective) == " + Proto.QR_OBJECTIVE,
			"offsetof(QuestTargetRecord, flags) == " + Proto.QR_FLAGS,
			"offsetof(QuestTargetRecord, questType) == " + Proto.QR_QUEST_TYPE,
			"offsetof(QuestTargetRecord, name) == " + Proto.QR_NAME,
			"offsetof(QuestTargetRecord, objectiveText) == " + Proto.QR_OBJECTIVE_TEXT,
			"sizeof(QuestTargetTable) == " + Proto.QT_RECORDS + " + 104 * kMaxQuestTargets",
		};
		for (String e : expected) {
			assertTrue(text.contains(e), "the header should say: " + e);
		}
		// Skyrim's QUEST_DATA::Type values the table passes on.
		assertEquals(1, Proto.QUEST_TYPE_MAIN);
		assertEquals(6, Proto.QUEST_TYPE_MISC);
		// The quests' Skyrim bit is the one handed to them, clear of every other feature's.
		assertEquals(1 << 4, Proto.FEATURE_QUEST_MARKERS);
	}

	@Test
	void readsWhatSkyrimWrote() {
		ByteBuffer b = table(42, 2, 0x3C);
		record(b, 0, 120.5F, 64.0F, -300.25F, 0x0002610C, 0x00012345, 10, Proto.QUEST_TRACKED | Proto.QUEST_VIA_DOOR | Proto.QUEST_INSIDE | Proto.QUEST_MAIN,
			Proto.QUEST_TYPE_MAIN, utf8("The Golden Claw"), utf8("Retrieve the Golden Claw"));
		record(b, 1, -5.0F, 70.0F, 8.0F, 0x00030001, 0x00054321, Proto.QUEST_NO_OBJECTIVE, Proto.QUEST_ACTOR, Proto.QUEST_TYPE_MISC, new byte[0],
			utf8("Speak to Lucan"));
		QuestTable.Snapshot t = QuestTable.decode(b);
		assertNotNull(t);
		assertEquals(42, t.seq());
		assertEquals(0x3C, t.world());
		assertEquals(2, t.targets().size());
		QuestTable.Target claw = t.targets().getFirst();
		assertEquals(0x0002610C, claw.quest());
		assertEquals(0x00012345, claw.ref());
		assertEquals(10, claw.objective());
		assertTrue(claw.tracked() && claw.viaDoor() && claw.inside() && claw.main());
		assertFalse(claw.actor());
		assertEquals(120.5, claw.x(), 0.0);
		assertEquals(-300.25, claw.z(), 0.0);
		assertEquals("The Golden Claw", claw.name());
		assertEquals("Retrieve the Golden Claw", claw.objectiveText());
		QuestTable.Target lucan = t.targets().get(1);
		assertEquals(Proto.QUEST_NO_OBJECTIVE, lucan.objective());
		assertTrue(lucan.actor());
		assertFalse(lucan.tracked());
		assertEquals("", lucan.name());
		assertEquals(Proto.QUEST_TYPE_MISC, lucan.questType());
	}

	@Test
	void nothingYetOrMidWriteIsNoTable() {
		assertNull(QuestTable.decode(table(0, 0, 0)), "an older plugin's zeros");
		assertNull(QuestTable.decode(table(7, 1, 0x3C)), "Skyrim writing");
		assertNull(QuestTable.decode(ByteBuffer.allocate(100)), "too short");
		QuestTable.Snapshot empty = QuestTable.decode(table(2, 0, 0));
		assertNotNull(empty);
		assertTrue(empty.targets().isEmpty());
	}

	@Test
	void aBrokenCountOrPositionNeverBecomesAMarker() {
		ByteBuffer b = table(4, 1000, 0x3C);
		for (int i = 0; i < Proto.MAX_QUEST_TARGETS; i++) {
			record(b, i, i, 0.0F, 0.0F, i, i, 0, Proto.QUEST_TRACKED, 8, utf8("Quest " + i), new byte[0]);
		}
		record(b, 3, Float.NaN, 0.0F, 0.0F, 3, 3, 0, Proto.QUEST_TRACKED, 8, utf8("Broken"), new byte[0]);
		QuestTable.Snapshot t = QuestTable.decode(b);
		assertNotNull(t);
		assertEquals(Proto.MAX_QUEST_TARGETS - 1, t.targets().size());
		assertTrue(t.targets().stream().noneMatch(q -> q.name().equals("Broken")));
		assertEquals(0, QuestTable.decode(table(4, -5, 0x3C)).targets().size());
	}

	@Test
	void textsEndAtTheirNulOrTheirFieldAndLoseControlCharacters() {
		ByteBuffer b = table(2, 1, 0x3C);
		byte[] full = new byte[Proto.QR_NAME_BYTES];
		java.util.Arrays.fill(full, (byte) 'x');
		record(b, 0, 0.0F, 0.0F, 0.0F, 1, 1, 0, Proto.QUEST_TRACKED, 8, full, utf8("Find\u0007 Ölfréd"));
		QuestTable.Target t = QuestTable.decode(b).targets().getFirst();
		assertEquals("x".repeat(Proto.QR_NAME_BYTES), t.name());
		assertEquals("Find Ölfréd", t.objectiveText());
	}

	@Test
	void movesIntoItsSpace() {
		QuestTable.Target t = new QuestTable.Target(1, 2, 3, Proto.QUEST_TRACKED, 8, 10.0, 64.0, -20.0, "A", "B");
		QuestTable.Target moved = t.moved(32768.0, -32768.0);
		assertEquals(32778.0, moved.x(), 0.0);
		assertEquals(64.0, moved.y(), 0.0);
		assertEquals(-32788.0, moved.z(), 0.0);
		assertEquals(t.name(), moved.name());
		assertTrue(moved.tracked());
	}
}
