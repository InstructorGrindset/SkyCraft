package dev.skycraft.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpellCycleTest {
	private static MagicTable.Spell spell(int formId, float cost, int school, boolean favourite, String name) {
		return new MagicTable.Spell(formId, cost, school, Proto.SPELL_KIND_OTHER, favourite ? Proto.MAGIC_SPELL_FAVOURITE : 0, Proto.DELIVERY_AIMED, name);
	}

	private static final List<MagicTable.Spell> KNOWN = List.of(
		spell(1, 41.0F, Proto.SCHOOL_DESTRUCTION, false, "Firebolt"),
		spell(2, 14.0F, Proto.SCHOOL_DESTRUCTION, false, "Flames"),
		spell(3, 25.0F, Proto.SCHOOL_RESTORATION, false, "Healing"),
		spell(4, 50.0F, Proto.SCHOOL_ALTERATION, false, "Oakflesh"),
		spell(5, 14.0F, Proto.SCHOOL_DESTRUCTION, false, "Frostbite"));

	@Test
	void everySpellBySchoolThenCost() {
		// Alteration (18) before Destruction (20) before Restoration (22); Flames and Frostbite cost the same: by name.
		assertEquals(List.of(4, 2, 5, 1, 3), SpellCycle.spells(KNOWN));
	}

	@Test
	void favouritesWhenThereAreAny() {
		List<MagicTable.Spell> some = List.of(KNOWN.get(0), spell(3, 25.0F, Proto.SCHOOL_RESTORATION, true, "Healing"),
			spell(2, 14.0F, Proto.SCHOOL_DESTRUCTION, true, "Flames"), KNOWN.get(3));
		assertEquals(List.of(2, 3), SpellCycle.spells(some));
		assertTrue(SpellCycle.spells(List.of()).isEmpty());
	}

	@Test
	void steppingGoesRound() {
		List<Integer> order = List.of(4, 2, 5, 1, 3);
		assertEquals(2, SpellCycle.step(order, 4, 1));
		assertEquals(4, SpellCycle.step(order, 3, 1)); // round to the first
		assertEquals(3, SpellCycle.step(order, 4, -1)); // and back to the last
		assertEquals(4, SpellCycle.step(order, 99, 1)); // nothing selected yet: the first
		assertEquals(0, SpellCycle.step(List.of(), 4, 1));
	}

	@Test
	void aSelectionKeptAcrossRefreshes() {
		List<Integer> order = List.of(4, 2, 5);
		assertEquals(5, SpellCycle.keep(order, 5));
		assertEquals(4, SpellCycle.keep(order, 1)); // forgotten in Skyrim: the first
		assertEquals(0, SpellCycle.keep(List.of(), 1));
	}

	@Test
	void shoutsWithAWordUnlocked() {
		float[] r = { 1, 2, 3 };
		List<MagicTable.Shout> shouts = List.of(new MagicTable.Shout(10, 2, 0, Proto.MAGIC_SHOUT_EQUIPPED, r, "Equipped"),
			new MagicTable.Shout(11, 0, 0, 0, r, "Learned, never unlocked"), new MagicTable.Shout(12, 3, 0, 0, r, "Another"));
		assertEquals(List.of(10, 12), SpellCycle.shouts(shouts));
	}
}
