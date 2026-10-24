package dev.skycraft.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MagicHudLayoutTest {
	private static List<MagicHudLayout.Fill> fills(List<MagicHudLayout.Op> ops) {
		List<MagicHudLayout.Fill> out = new ArrayList<>();
		for (MagicHudLayout.Op op : ops) {
			if (op instanceof MagicHudLayout.Fill f) {
				out.add(f);
			}
		}
		return out;
	}

	private static List<MagicHudLayout.Text> texts(List<MagicHudLayout.Op> ops) {
		List<MagicHudLayout.Text> out = new ArrayList<>();
		for (MagicHudLayout.Op op : ops) {
			if (op instanceof MagicHudLayout.Text t) {
				out.add(t);
			}
		}
		return out;
	}

	@Test
	void nothingToShowDrawsNothing() {
		assertTrue(MagicHudLayout.layout(480, 270, null, null, null, 0).isEmpty());
	}

	@Test
	void theVoiceBarBesideTheHotbar() {
		var ops = MagicHudLayout.layout(480, 270, null, new MagicHudLayout.Bar("Unrelenting Force", 0.5F, MagicHudLayout.VOICE), null, 0);
		List<MagicHudLayout.Fill> f = fills(ops);
		assertEquals(3, f.size()); // frame, trough, fill
		int x = 480 / 2 + 91 + MagicHudLayout.GAP;
		assertEquals(new MagicHudLayout.Fill(x, 270 - 8, x + 81, 270 - 3, MagicHudLayout.FRAME), f.get(0)); // level with the hotbar's bottom
		assertEquals(x + 1 + 40, f.get(2).x1()); // half of the 79 inside
		assertEquals(MagicHudLayout.VOICE, f.get(2).argb());
		List<MagicHudLayout.Text> t = texts(ops);
		assertEquals(1, t.size());
		assertEquals("Unrelenting Force", t.get(0).text());
		assertEquals(270 - 8 - MagicHudLayout.LINE - 1, t.get(0).y());
	}

	@Test
	void theSpellSitsAboveTheVoiceAndTheStatusAboveBoth() {
		var ops = MagicHudLayout.layout(480, 270, new MagicHudLayout.Bar("Flames", 1.0F, MagicHudLayout.MAGICKA),
			new MagicHudLayout.Bar("Fire Breath", 0.0F, MagicHudLayout.VOICE), "Not enough magicka", MagicHudLayout.WARN);
		List<MagicHudLayout.Text> t = texts(ops);
		assertEquals(List.of("Fire Breath", "Flames", "Not enough magicka"), t.stream().map(MagicHudLayout.Text::text).toList());
		assertEquals(MagicHudLayout.BLOCK, t.get(0).y() - t.get(1).y());
		assertEquals(MagicHudLayout.BLOCK, t.get(1).y() - t.get(2).y());
		assertEquals(MagicHudLayout.WARN, t.get(2).argb());
		// An empty voice bar has no fill; a full magicka bar fills its whole inside.
		List<MagicHudLayout.Fill> f = fills(ops);
		assertEquals(5, f.size());
		MagicHudLayout.Fill magicka = f.get(4);
		assertEquals(f.get(3).x1(), magicka.x1());
		assertEquals(MagicHudLayout.MAGICKA, magicka.argb());
	}

	@Test
	void aSpellAloneTakesTheBottomPlace() {
		var ops = MagicHudLayout.layout(480, 270, new MagicHudLayout.Bar("Healing", 0.25F, MagicHudLayout.MAGICKA), null, null, 0);
		assertEquals(270 - 8, fills(ops).get(0).y0());
	}

	@Test
	void aNarrowWindowStillFits() {
		var ops = MagicHudLayout.layout(320, 240, null, new MagicHudLayout.Bar("Slow Time", 2.0F, MagicHudLayout.VOICE_READY), null, 0);
		List<MagicHudLayout.Fill> f = fills(ops);
		assertTrue(f.get(0).x1() <= 320 - 4, "inside the window");
		assertTrue(f.get(0).x1() - f.get(0).x0() >= MagicHudLayout.MIN_WIDTH);
		assertEquals(f.get(1).x1(), f.get(2).x1()); // a fill past full is full
	}

	@Test
	void longNamesAreCut() {
		assertEquals("Flames", MagicHudLayout.fit("Flames", 81));
		String cut = MagicHudLayout.fit("Conjure Flame Atronach and more", 81);
		assertTrue(cut.endsWith("..."), cut);
		assertTrue(MagicHudLayout.width(cut) <= 81, cut);
		assertTrue(cut.startsWith("Conjure Flame"), cut);
	}

	@Test
	void narrowLettersAreNarrow() {
		assertEquals(6 + 6 + 3 + 2 + 4 + 4, MagicHudLayout.width("Ablit "));
		assertEquals(0, MagicHudLayout.width(""));
	}

	@Test
	void aLabelMayRunOnPastItsBar() {
		var ops = MagicHudLayout.layout(480, 270, null, new MagicHudLayout.Bar("Unrelenting Force 12s", 0.2F, MagicHudLayout.VOICE), null, 0);
		assertEquals("Unrelenting Force 12s", texts(ops).get(0).text()); // wider than the bar, but the window has room
	}
}
