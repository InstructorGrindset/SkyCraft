package dev.skycraft.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestCodecTest {
	private static QuestTable.Target target(int i) {
		return new QuestTable.Target(0x0002610C + i, 0x00012345 + i, i, Proto.QUEST_TRACKED | Proto.QUEST_VIA_DOOR, Proto.QUEST_TYPE_MAIN, 32768.25 + i,
			64.5, -12.75, "Dragon Rising", "Kill the dragon");
	}

	@Test
	void targetsComeBackAsTheyWent() {
		List<QuestTable.Target> sent = List.of(target(0), target(1),
			new QuestTable.Target(7, 8, Proto.QUEST_NO_OBJECTIVE, 0, Proto.QUEST_TYPE_MISC, -1.5, 0.0, 2.5, "", ""));
		List<QuestTable.Target> got = QuestCodec.decode(QuestCodec.encode(sent));
		assertEquals(sent, got);
		assertTrue(QuestCodec.encode(sent).size() <= QuestCodec.MAX_INTS);
	}

	@Test
	void atMostATablesWorth() {
		List<QuestTable.Target> many = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			many.add(target(i));
		}
		List<Integer> data = QuestCodec.encode(many);
		assertTrue(data.size() <= QuestCodec.MAX_INTS, data.size() + " ints");
		assertEquals(Proto.MAX_QUEST_TARGETS, QuestCodec.decode(data).size());
	}

	@Test
	void textsAreCutAtWholeCharactersAndControlCharactersDropped() {
		String longName = "Æ" + "x".repeat(60);
		QuestTable.Target t = new QuestTable.Target(1, 2, 3, 0, 8, 0, 0, 0, longName, "Find\u0007 it");
		QuestTable.Target got = QuestCodec.decode(QuestCodec.encode(List.of(t))).getFirst();
		assertEquals("Æ" + "x".repeat(Proto.QR_NAME_BYTES - 2), got.name());
		assertEquals("Find it", got.objectiveText());
	}

	@Test
	void brokenPacketsAreRefused() {
		List<Integer> good = QuestCodec.encode(List.of(target(0)));
		assertThrows(IllegalArgumentException.class, () -> QuestCodec.decode(List.of()));
		assertThrows(IllegalArgumentException.class, () -> QuestCodec.decode(List.of(2, 0)));
		assertThrows(IllegalArgumentException.class, () -> QuestCodec.decode(List.of(QuestCodec.VERSION, 17)));
		assertThrows(IllegalArgumentException.class, () -> QuestCodec.decode(good.subList(0, good.size() - 1)));
		List<Integer> extra = new ArrayList<>(good);
		extra.add(0);
		assertThrows(IllegalArgumentException.class, () -> QuestCodec.decode(extra));
		List<Integer> nan = new ArrayList<>(good);
		nan.set(5, Float.floatToRawIntBits(Float.NaN));
		assertThrows(IllegalArgumentException.class, () -> QuestCodec.decode(nan));
		List<Integer> longText = new ArrayList<>(good);
		longText.set(8, 41);
		assertThrows(IllegalArgumentException.class, () -> QuestCodec.decode(longText));
	}
}
