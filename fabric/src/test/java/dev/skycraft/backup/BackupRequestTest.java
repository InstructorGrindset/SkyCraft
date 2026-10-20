package dev.skycraft.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackupRequestTest {
	private static BackupRequest.Request feed(BackupRequest assembler, List<int[]> events) {
		BackupRequest.Request out = null;
		for (int i = 0; i < events.size(); i++) {
			int[] e = events.get(i);
			BackupRequest.Request r = assembler.accept(e[0], e[1], e[2], e[3]);
			if (i < events.size() - 1) {
				assertNull(r, "complete before its last part");
			}
			out = r;
		}
		return out;
	}

	@Test
	void theBytesSkyrimSends() {
		// The same events skse/tests/BackupNamesTest.cpp expects Skyrim's encoder to write.
		List<int[]> events = BackupRequest.encode(7, "2026-10-17_15-45-23", "Save 3");
		assertEquals(4, events.size());
		assertEquals(List.of(0, 7, 26, 0), List.of(events.get(0)[0], events.get(0)[1], events.get(0)[2], events.get(0)[3]));
		assertEquals(List.of(1, 0x36323032, 0x2D30312D, 0x315F3731), List.of(events.get(1)[0], events.get(1)[1], events.get(1)[2], events.get(1)[3]));
		assertEquals(List.of(2, 0x35342D35, 0x7C33322D, 0x65766153), List.of(events.get(2)[0], events.get(2)[1], events.get(2)[2], events.get(2)[3]));
		assertEquals(List.of(3, 0x3320, 0, 0), List.of(events.get(3)[0], events.get(3)[1], events.get(3)[2], events.get(3)[3]));
	}

	@Test
	void putsTheRequestBackTogether() {
		BackupRequest assembler = new BackupRequest();
		BackupRequest.Request r = feed(assembler, BackupRequest.encode(7, "2026-10-17_15-45-23", "Save 3"));
		assertNotNull(r);
		assertEquals(7, r.id());
		assertEquals("2026-10-17_15-45-23", r.stamp());
		assertEquals("Save 3", r.saveName());
		// And the next one, through the same assembler.
		r = feed(assembler, BackupRequest.encode(8, "2026-10-17_16-00-00", "Save13_8D0E66A5_0_4C79646961_WhiterunWorld_000123_20261017160000_10_1"));
		assertNotNull(r);
		assertEquals("Save13_8D0E66A5_0_4C79646961_WhiterunWorld_000123_20261017160000_10_1", r.saveName());
	}

	@Test
	void textOfExactlyTwelveBytesAndUnicode() {
		BackupRequest.Request r = feed(new BackupRequest(), BackupRequest.encode(1, "2026-01-02_03-04-05", ""));
		assertNotNull(r);
		assertEquals("", r.saveName());
		r = feed(new BackupRequest(), BackupRequest.encode(2, "2026-01-02_03-04-05", "Ærin's quicksave ✓"));
		assertNotNull(r);
		assertEquals("Ærin's quicksave ✓", r.saveName());
	}

	@Test
	void aMissingPartDropsTheRequest() {
		BackupRequest assembler = new BackupRequest();
		List<int[]> events = BackupRequest.encode(3, "2026-10-17_15-45-23", "Save 3");
		int[] h = events.get(0), p1 = events.get(1), p3 = events.get(3);
		assertNull(assembler.accept(h[0], h[1], h[2], h[3]));
		assertNull(assembler.accept(p1[0], p1[1], p1[2], p1[3]));
		assertNull(assembler.accept(p3[0], p3[1], p3[2], p3[3])); // part 2 lost
		assertNull(assembler.accept(p3[0], p3[1], p3[2], p3[3]));
		// A new request after it comes through whole.
		assertNotNull(feed(assembler, BackupRequest.encode(4, "2026-10-17_15-45-24", "Save 4")));
	}

	@Test
	void aNewStartReplacesAHalfReceivedOne() {
		BackupRequest assembler = new BackupRequest();
		int[] h = BackupRequest.encode(5, "2026-10-17_15-45-23", "Save 5").get(0);
		assertNull(assembler.accept(h[0], h[1], h[2], h[3]));
		BackupRequest.Request r = feed(assembler, BackupRequest.encode(6, "2026-10-17_15-46-00", "Save 6"));
		assertNotNull(r);
		assertEquals(6, r.id());
	}

	@Test
	void partsWithoutAStartAndBadTextAreDropped() {
		BackupRequest assembler = new BackupRequest();
		assertNull(assembler.accept(1, 1, 2, 3));
		assertNull(assembler.accept(0, 1, 0, 0)); // no text
		assertNull(assembler.accept(0, 1, Proto.PAIRED_BACKUP_TEXT_MAX + 1, 0)); // too long
		assertNull(feed(assembler, BackupRequest.encode(9, "yesterday", "Save 9")));
		assertNull(BackupRequest.parse(1, "2026-10-17_15-45-23 Save without a bar"));
	}

	@Test
	void aLongNameIsCutAtTheLimit() {
		String name = "x".repeat(600);
		List<int[]> events = BackupRequest.encode(1, "2026-10-17_15-45-23", name);
		assertEquals(Proto.PAIRED_BACKUP_TEXT_MAX, events.get(0)[2]);
		BackupRequest.Request r = feed(new BackupRequest(), events);
		assertNotNull(r);
		assertEquals(Proto.PAIRED_BACKUP_TEXT_MAX - 20, r.saveName().length());
		assertTrue(name.startsWith(r.saveName()));
	}
}
