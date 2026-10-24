package dev.skycraft.magic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which spells the Next spell key goes through, and which shouts the Next shout key does (Spells, Shouts). Spells: the
 * player's Skyrim magic favourites that are spells, when there are any, else every hand-cast spell they know; either way
 * sorted by school, then magicka cost, then name. Shouts: those with a word unlocked, in the table's order (the equipped one
 * first). Pure.
 */
public final class SpellCycle {
	private SpellCycle() {
	}

	private static final Comparator<MagicTable.Spell> BY_SCHOOL_AND_COST = Comparator.comparingInt(MagicTable.Spell::school)
		.thenComparingDouble(MagicTable.Spell::cost).thenComparing(MagicTable.Spell::name).thenComparingInt(MagicTable.Spell::formId);

	/** The spells to cycle through, as FormIDs: favourites if any, else all; sorted by school, cost and name. */
	public static List<Integer> spells(List<MagicTable.Spell> spells) {
		List<MagicTable.Spell> pick = new ArrayList<>();
		for (MagicTable.Spell s : spells) {
			if (s.favourite()) {
				pick.add(s);
			}
		}
		if (pick.isEmpty()) {
			pick.addAll(spells);
		}
		pick.sort(BY_SCHOOL_AND_COST);
		List<Integer> out = new ArrayList<>(pick.size());
		for (MagicTable.Spell s : pick) {
			out.add(s.formId());
		}
		return out;
	}

	/** The shouts to cycle through, as FormIDs: every one with a word unlocked, in the table's order. */
	public static List<Integer> shouts(List<MagicTable.Shout> shouts) {
		List<Integer> out = new ArrayList<>();
		for (MagicTable.Shout s : shouts) {
			if (s.words() > 0) {
				out.add(s.formId());
			}
		}
		return out;
	}

	/**
	 * The one {@code step} places on from {@code current} (1 next, -1 the one before), going round; the first when
	 * {@code current} isn't in {@code order}; 0 for an empty order.
	 */
	public static int step(List<Integer> order, int current, int step) {
		if (order.isEmpty()) {
			return 0;
		}
		int at = order.indexOf(current);
		if (at < 0) {
			return order.get(0);
		}
		int n = order.size();
		return order.get(Math.floorMod(at + step, n));
	}

	/** {@code current} while it's still in {@code order}, else the first (0 for an empty order): a selection kept across refreshes. */
	public static int keep(List<Integer> order, int current) {
		return order.contains(current) ? current : order.isEmpty() ? 0 : order.get(0);
	}
}
