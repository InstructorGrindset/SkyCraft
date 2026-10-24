package dev.skycraft.magic;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where the magic HUD goes and what it draws (Shouts, Spells), as plain rectangles and text the client's HUD element hands to
 * Minecraft's GUI drawing. Beside the hotbar on its right, level with it, as wide as the hearts: at the bottom the shout's
 * name over its voice bar (filling as the voice recovers, white once it's ready), above that the selected spell's name over
 * the magicka bar, and above both a line when Skyrim said no (not enough magicka, the voice still recovering). The bars
 * are drawn the way Minecraft's own are: a dark frame round a dim trough, the fill inside. Pure, so it's unit tested.
 */
public final class MagicHudLayout {
	private MagicHudLayout() {
	}

	/** Something to draw: a filled rectangle or a line of text. */
	public sealed interface Op permits Fill, Text {
	}

	/** A rectangle from (x0, y0) to (x1, y1), exclusive, in GUI pixels, ARGB. */
	public record Fill(int x0, int y0, int x1, int y1, int argb) implements Op {
	}

	/** Text with its top left at (x, y), ARGB. */
	public record Text(int x, int y, String text, int argb) implements Op {
	}

	/** A bar to show: its label, how full (0 to 1) and the fill's colour (ARGB). */
	public record Bar(String label, float fill, int color) {
	}

	/** Minecraft's font: most letters are 5 pixels and a gap (narrow ones less: {@link #width}). */
	public static final int CHAR_WIDTH = 6;
	/** A line of text (Minecraft's font is 9 high with its gap). */
	public static final int LINE = 9;
	public static final int BAR_HEIGHT = 5;
	/** As wide as the row of hearts. */
	public static final int MAX_WIDTH = 81;
	public static final int MIN_WIDTH = 32;
	/** Pixels between the hotbar's right end (182 wide, centred) and the HUD. */
	public static final int GAP = 8;
	/** One label and bar, with the space under it. */
	public static final int BLOCK = LINE + 1 + BAR_HEIGHT + 3;

	/** Skyrim's magicka blue. */
	public static final int MAGICKA = 0xFF3B6FD4;
	/** The voice bar while it fills: Skyrim's pale shout meter. */
	public static final int VOICE = 0xFFC9BC94;
	/** The voice bar once the voice is ready. */
	public static final int VOICE_READY = 0xFFFFFFFF;
	public static final int FRAME = 0xFF000000;
	public static final int TROUGH = 0x90202020;
	public static final int LABEL = 0xFFFFFFFF;
	/** Skyrim said no (Minecraft's red). */
	public static final int WARN = 0xFFFF5555;
	/** A note, not a refusal (Minecraft's gray). */
	public static final int NOTE = 0xFFAAAAAA;

	/**
	 * What to draw on a GUI {@code width} x {@code height} (scaled pixels): the {@code spell} bar and the {@code voice} bar, each
	 * null when not shown, and a {@code status} line (null: none) in {@code statusColor}. Nothing at all to show, nothing drawn.
	 */
	public static List<Op> layout(int width, int height, @Nullable Bar spell, @Nullable Bar voice, @Nullable String status, int statusColor) {
		List<Op> ops = new ArrayList<>();
		if (spell == null && voice == null && status == null) {
			return ops;
		}
		int x = width / 2 + 91 + GAP;
		int w = Math.min(MAX_WIDTH, width - 4 - x);
		if (w < MIN_WIDTH) {
			w = MIN_WIDTH; // a very narrow window: against the right edge, over the hotbar's end if it must
			x = Math.max(0, width - 4 - w);
		}
		int barTop = height - 3 - BAR_HEIGHT; // the lowest bar's bottom is level with the hotbar's
		int labelTop = barTop - LINE - 1;
		int room = Math.max(w, width - 4 - x); // a label may run on past its bar, to the window's edge
		for (Bar bar : new Bar[] { voice, spell }) {
			if (bar == null) {
				continue;
			}
			ops.add(new Text(x, labelTop, fit(bar.label(), room), LABEL));
			bar(ops, x, barTop, w, bar.fill(), bar.color());
			barTop -= BLOCK;
			labelTop -= BLOCK;
		}
		if (status != null) {
			ops.add(new Text(x, labelTop, fit(status, room), statusColor));
		}
		return ops;
	}

	/** A bar {@code w} wide with its top at {@code y}: frame, trough, and the fill {@code fill} (0 to 1) of the way along. */
	private static void bar(List<Op> ops, int x, int y, int w, float fill, int color) {
		ops.add(new Fill(x, y, x + w, y + BAR_HEIGHT, FRAME));
		ops.add(new Fill(x + 1, y + 1, x + w - 1, y + BAR_HEIGHT - 1, TROUGH));
		float f = Float.isFinite(fill) ? Math.max(0.0F, Math.min(fill, 1.0F)) : 0.0F;
		int filled = Math.round((w - 2) * f);
		if (filled > 0) {
			ops.add(new Fill(x + 1, y + 1, x + 1 + filled, y + BAR_HEIGHT - 1, color));
		}
	}

	/** {@code text} cut to fit {@code pixels} in Minecraft's font ({@link #width}), ending in "..." when it was cut. */
	public static String fit(String text, int pixels) {
		if (width(text) <= pixels) {
			return text;
		}
		int dots = width("...");
		int end = 0;
		int used = 0;
		while (end < text.length() && used + advance(text.charAt(end)) + dots <= pixels) {
			used += advance(text.charAt(end));
			end++;
		}
		return text.substring(0, end).stripTrailing() + "...";
	}

	/** About how wide {@code text} is in Minecraft's default font (GUI pixels): its letters' advances, narrow ones narrower. */
	public static int width(String text) {
		int w = 0;
		for (int i = 0; i < text.length(); i++) {
			w += advance(text.charAt(i));
		}
		return w;
	}

	/** One character's advance in Minecraft's default font: its glyph and the pixel after it. */
	private static int advance(char c) {
		return switch (c) {
			case 'i', '!', '.', ',', ':', ';', '\'', '|' -> 2;
			case 'l', '`' -> 3;
			case 't', 'I', '[', ']', ' ' -> 4;
			case 'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 5;
			case '@', '~' -> 7;
			default -> CHAR_WIDTH;
		};
	}
}
