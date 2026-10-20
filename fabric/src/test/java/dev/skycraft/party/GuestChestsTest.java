package dev.skycraft.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestChestsTest {
	private static final UUID A = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final UUID B = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

	@Test
	void linesReadBackPerGuest() {
		Map<UUID, Set<Integer>> out = new HashMap<>();
		int bad = GuestChests.parse(List.of(GuestChests.line(A, 0xABC).strip(), GuestChests.line(B, 0xFF0A1234).strip(), GuestChests.line(A, 0xABD).strip()),
			out);
		assertEquals(0, bad);
		assertEquals(Set.of(0xABC, 0xABD), out.get(A));
		assertEquals(Set.of(0xFF0A1234), out.get(B));
	}

	@Test
	void brokenLinesAreCountedAndSkipped() {
		Map<UUID, Set<Integer>> out = new HashMap<>();
		int bad = GuestChests.parse(List.of("", "000ABC", A + " zz", "not-a-uuid 0ABC", A + " 0000ABCD", A + " 0000AB"), out);
		assertEquals(3, bad);
		assertTrue(out.get(A).contains(0xABCD));
		assertTrue(out.get(A).contains(0xAB));
	}
}
