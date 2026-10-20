package dev.skycraft.guide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuidePagesTest {
	/** A book page: 114 pixels wide, 14 lines. */
	private static final int PAGE_WIDTH = 114;
	private static final int PAGE_LINES = 14;

	/** Minecraft's default font, close enough: most glyphs advance 6 pixels, these less. */
	private static int width(char c) {
		return switch (c) {
			case 'i', '.', ',', ':', ';', '!', '\'', '|' -> 2;
			case 'l' -> 3;
			case 't', 'I', ' ' -> 4;
			case 'f', 'k', '(', ')', '<', '>', '"' -> 5;
			default -> 6;
		};
	}

	/** The page as the book wraps it: at spaces, never wider than the page. */
	private static List<String> wrap(String page) {
		List<String> lines = new ArrayList<>();
		for (String paragraph : page.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			int w = 0;
			for (String word : paragraph.split(" ")) {
				int ww = word.chars().map(c -> width((char) c)).sum();
				if (line.length() > 0 && w + width(' ') + ww > PAGE_WIDTH) {
					lines.add(line.toString());
					line.setLength(0);
					w = 0;
				}
				if (line.length() > 0) {
					line.append(' ');
					w += width(' ');
				}
				line.append(word);
				w += ww;
			}
			lines.add(line.toString());
		}
		return lines;
	}

	@Test
	void everyPageFitsOnItsPage() {
		assertTrue(GuidePages.TITLE.length() <= 32, "a written book's title is at most 32 characters");
		assertTrue(!GuidePages.PAGES.isEmpty() && GuidePages.PAGES.size() <= 100);
		for (String page : GuidePages.PAGES) {
			List<String> lines = wrap(page);
			// A line short of the page, since the font here is only close to Minecraft's.
			assertTrue(lines.size() <= PAGE_LINES - 1, lines.size() + " lines: " + page);
		}
	}

	@Test
	void coversTheBigFeatures() {
		String all = String.join("\n", GuidePages.PAGES);
		for (String needed : List.of("Crouch", "Skyrim's sneak", "loot in Skyrim becomes Minecraft items", "empty hand", "soul gem",
			"SkyCraft settings", "/skycraft report")) {
			assertTrue(all.contains(needed), "the book doesn't say: " + needed);
		}
	}
}
