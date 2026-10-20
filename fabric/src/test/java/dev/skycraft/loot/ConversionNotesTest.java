package dev.skycraft.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConversionNotesTest {
	private static final String SWORD = ConversionNotes.converted("Steel Sword", 1, List.of(new ConversionNotes.Output("Iron Sword", 1)));

	@Test
	void writesWhatBecameWhat() {
		assertEquals("Steel Sword → Iron Sword", SWORD);
		assertEquals("Iron Ore ×3 → Raw Iron ×3",
			ConversionNotes.converted("Iron Ore", 3, List.of(new ConversionNotes.Output("Raw Iron", 3))));
		assertEquals("Ruby → Gold Nugget ×3 + Diamond",
			ConversionNotes.converted("Ruby", 1, List.of(new ConversionNotes.Output("Gold Nugget", 3), new ConversionNotes.Output("Diamond", 1))));
		assertEquals("Gold ×25 → stays in Skyrim", ConversionNotes.kept("Gold", 25, false));
		assertEquals("Amulet of Talos → stays in Skyrim (quest item)", ConversionNotes.kept("Amulet of Talos", 1, true));
		assertEquals("An item → stays in Skyrim", ConversionNotes.kept(" ", 1, false));
	}

	@Test
	void oneLineAtOnceThenNothingUntilTheNextPickup() {
		ConversionNotes notes = new ConversionNotes();
		assertNull(notes.poll());
		notes.add(SWORD, true, 100);
		assertEquals(SWORD, notes.poll());
		assertNull(notes.poll());
	}

	@Test
	void pickupsWithinASecondShareTheFirstOnesLine() {
		ConversionNotes notes = new ConversionNotes();
		notes.add(SWORD, true, 100);
		notes.add("Leather → Leather", true, 100);
		notes.add("Iron Ingot → Iron Ingot", true, 100);
		assertEquals(SWORD + ", +2 more", notes.poll());
		notes.add("Arrow → Arrow", true, 100 + ConversionNotes.WINDOW_TICKS - 1);
		assertEquals(SWORD + ", +3 more", notes.poll());
	}

	@Test
	void aSecondLaterStartsANewLine() {
		ConversionNotes notes = new ConversionNotes();
		notes.add(SWORD, true, 100);
		notes.add("Leather → Leather", true, 105);
		assertEquals(SWORD + ", +1 more", notes.poll());
		notes.add("Arrow → Arrow", true, 100 + ConversionNotes.WINDOW_TICKS);
		assertEquals("Arrow → Arrow", notes.poll());
	}

	@Test
	void aConversionTakesTheLineFromAnItemThatStayed() {
		ConversionNotes notes = new ConversionNotes();
		String gold = ConversionNotes.kept("Gold", 25, false);
		notes.add(gold, false, 100);
		assertEquals(gold, notes.poll());
		notes.add(ConversionNotes.kept("Iron Key", 1, false), false, 101);
		assertEquals(gold + ", +1 more", notes.poll());
		notes.add(SWORD, true, 102);
		assertEquals(SWORD + ", +2 more", notes.poll());
		notes.add("Leather → Leather", true, 103);
		assertEquals(SWORD + ", +3 more", notes.poll());
	}
}
