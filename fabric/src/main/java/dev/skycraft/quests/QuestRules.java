package dev.skycraft.quests;

import static dev.skycraft.link.Proto.MAX_QUEST_TARGETS;
import static dev.skycraft.link.Proto.QUEST_TYPE_MISC;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Quest markers' rules (switch {@code quest_markers}, numbers in {@code skycraft-quests.txt}): which of the quest targets
 * Skyrim's compass has become markers on a player's locator bar, what each looks like and is called, and the action bar line
 * that names the quest when the player looks toward its marker. Pure Java; SkyQuests runs it on the server.
 */
public final class QuestRules {
	public static final String FILE = "skycraft-quests.txt";
	/** A marker's name is at most this long (the quest's, as Skyrim's table holds it, is shorter). */
	public static final int LABEL_MAX = 40;
	/** Closer than this (blocks) a marker counts as reached: the line says "here", and looking toward it says nothing. */
	public static final double HERE_BLOCKS = 3.0;

	/**
	 * maxMarkers: the most markers on the bar at once (1 to 16); untracked: also the targets of quests not active in Skyrim's
	 * journal (Skyrim's compass shows only the active ones); style: the waypoint style's id; the colours (RGB) of the main
	 * quest's, other tracked quests' and untracked quests' markers; lookAngle: how near the middle of the view (degrees either
	 * side) a marker names its quest; locatorBar: off, the action bar names the nearest tracked target now and then instead.
	 */
	public record Settings(int maxMarkers, boolean untracked, String style, int mainColor, int trackedColor, int untrackedColor, double lookAngle,
		boolean locatorBar) {
		public static final Settings DEFAULTS = new Settings(MAX_QUEST_TARGETS, false, "skycraft:quest_marker", 0xFFC93C, 0xFFFFFF, 0x9A9A9A, 5.0, true);
	}

	public static final String DEFAULT_TEXT = """
		# SkyCraft quest markers: the targets of your Skyrim quests on Minecraft's locator bar (the bar over the hotbar), in the
		# Skyrim world only. The switch is quest_markers in skycraft-features.txt. Restart Minecraft after editing.

		# The most markers on the bar at once (1 to 16). Skyrim sends its tracked quests' targets first, nearest first.
		max_markers = 16

		# Also show the targets of quests that aren't active in Skyrim's journal (Skyrim's compass shows only active ones).
		untracked = off

		# How a marker looks: a waypoint style. skycraft:quest_marker is Skyrim's quest arrow; minecraft:default and
		# minecraft:bowtie are Minecraft's own.
		style = skycraft:quest_marker

		# Marker colours (RRGGBB): the main quest's, other active quests', and with untracked = on the others'.
		main_color = FFC93C
		tracked_color = FFFFFF
		untracked_color = 9A9A9A

		# Looking toward a marker (within this many degrees, 1 to 45) names its quest on the action bar.
		look_angle = 5

		# off: no markers on the locator bar; the action bar names the nearest active quest's target every few seconds instead.
		locator_bar = on
		""";

	private QuestRules() {
	}

	/** Parses {@code key = value} lines; bad ones keep the default and go to {@code problems}. */
	public static Settings parse(String text, List<String> problems) {
		Settings d = Settings.DEFAULTS;
		int max = d.maxMarkers(), main = d.mainColor(), tracked = d.trackedColor(), untrackedColor = d.untrackedColor();
		boolean untracked = d.untracked(), bar = d.locatorBar();
		String style = d.style();
		double look = d.lookAngle();
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			int hash = line.indexOf('#');
			if (hash >= 0) {
				line = line.substring(0, hash).strip();
			}
			if (line.isEmpty()) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				problems.add("line " + n + ": expected 'key = value'");
				continue;
			}
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).strip();
			try {
				switch (key) {
					case "max_markers" -> max = whole(value, 1, MAX_QUEST_TARGETS);
					case "untracked" -> untracked = onOff(value);
					case "style" -> style = id(value);
					case "main_color" -> main = rgb(value);
					case "tracked_color" -> tracked = rgb(value);
					case "untracked_color" -> untrackedColor = rgb(value);
					case "look_angle" -> look = whole(value, 1, 45);
					case "locator_bar" -> bar = onOff(value);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad value '" + value + "' for " + key);
			}
		}
		return new Settings(max, untracked, style, main, tracked, untrackedColor, look, bar);
	}

	private static int whole(String value, int min, int max) {
		int v = Integer.parseInt(value);
		if (v < min || v > max) {
			throw new NumberFormatException(value);
		}
		return v;
	}

	private static boolean onOff(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "on", "true", "yes" -> true;
			case "off", "false", "no" -> false;
			default -> throw new NumberFormatException(value);
		};
	}

	/** RRGGBB (0x before it allowed; a # would start a comment). */
	private static int rgb(String value) {
		String v = value.toLowerCase(Locale.ROOT).startsWith("0x") ? value.substring(2) : value;
		if (v.length() != 6) {
			throw new NumberFormatException(value);
		}
		return Integer.parseInt(v, 16);
	}

	/** A namespaced id as Minecraft writes them (namespace:path, lower case). */
	private static String id(String value) {
		if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
			throw new NumberFormatException(value);
		}
		return value;
	}

	// ---- markers ---------------------------------------------------------------------------------------

	/**
	 * One marker on a player's locator bar: its waypoint id (the same for as long as the marker is on the same quest's same
	 * reference), the target, its name, its colour (RGB), the block it's at, and how many more of the targets shown share
	 * that block (one marker for them all).
	 */
	public record Marker(UUID id, QuestTable.Target target, String label, int color, int x, int y, int z, int also) {
		/** The same marker with the same look in the same place (nothing to send again). */
		public boolean sameAs(Marker o) {
			return this.x == o.x && this.y == o.y && this.z == o.z && this.color == o.color;
		}
	}

	/**
	 * The markers for {@code targets} (in the mirror's terms, in Skyrim's order: tracked quests first, nearest first): the
	 * tracked ones (and with untracked on the others too), one marker a block, at most maxMarkers.
	 */
	public static List<Marker> select(List<QuestTable.Target> targets, Settings s) {
		List<Marker> out = new ArrayList<>();
		for (QuestTable.Target t : targets) {
			if (!t.tracked() && !s.untracked()) {
				continue;
			}
			int x = (int) Math.floor(t.x()), y = (int) Math.floor(t.y()), z = (int) Math.floor(t.z());
			int same = -1;
			for (int i = 0; i < out.size() && same < 0; i++) {
				Marker m = out.get(i);
				same = m.x() == x && m.y() == y && m.z() == z ? i : -1;
			}
			if (same >= 0) {
				Marker m = out.get(same);
				out.set(same, new Marker(m.id(), m.target(), m.label(), m.color(), x, y, z, m.also() + 1));
				continue;
			}
			if (out.size() >= s.maxMarkers()) {
				continue;
			}
			out.add(new Marker(id(t.quest(), t.ref()), t, label(t), color(t, s), x, y, z, 0));
		}
		return out;
	}

	/** A waypoint id of its own for each quest and reference (a name-based UUID, never a player's). */
	public static UUID id(int quest, int ref) {
		return UUID.nameUUIDFromBytes(("skycraft:quest_marker:" + Integer.toHexString(quest) + ":" + Integer.toHexString(ref)).getBytes(StandardCharsets.UTF_8));
	}

	/** The marker's colour (RGB): the main quest's, an active quest's, or an untracked one's. */
	public static int color(QuestTable.Target t, Settings s) {
		return !t.tracked() ? s.untrackedColor() : t.main() ? s.mainColor() : s.trackedColor();
	}

	/**
	 * What a marker is called: the quest's name; for a quest with none (Skyrim lists those objectives under Miscellaneous), the
	 * objective's text; "Miscellaneous" when neither can be shown as it is (Skyrim's journal fills {@code <Alias=...>} tags in
	 * from the quest, which Minecraft can't), or "Quest". At most {@link #LABEL_MAX} characters.
	 */
	public static String label(QuestTable.Target t) {
		String name = t.name().strip();
		if (shownAsIs(name)) {
			return cut(name, LABEL_MAX);
		}
		String objective = t.objectiveText().strip();
		if (shownAsIs(objective)) {
			return cut(objective, LABEL_MAX);
		}
		return t.questType() == QUEST_TYPE_MISC || name.isEmpty() ? "Miscellaneous" : "Quest";
	}

	/** A text with something in it and none of the tags Skyrim's journal fills in. */
	private static boolean shownAsIs(String text) {
		return !text.isEmpty() && !(text.indexOf('<') >= 0 && text.indexOf('>') > text.indexOf('<'));
	}

	/** {@code text} at most {@code max} characters: cut at the last space that keeps most of it, with "..." after. */
	public static String cut(String text, int max) {
		if (text.length() <= max) {
			return text;
		}
		if (max < 4) {
			return text.substring(0, Math.max(0, max)); // no room for the dots
		}
		int room = max - 3;
		int space = text.lastIndexOf(' ', room);
		int end = space >= room / 2 ? space : room;
		if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(0, end).stripTrailing() + "...";
	}

	// ---- the bar's changes -----------------------------------------------------------------------------

	/** What to send a player's client: markers to show (new, moved or recoloured), and the ids of markers to take away. */
	public record Changes(List<Marker> show, List<UUID> remove) {
		public boolean none() {
			return this.show.isEmpty() && this.remove.isEmpty();
		}
	}

	/** From the markers a player's client has ({@code shown}, by id) to {@code now}. */
	public static Changes changes(Map<UUID, Marker> shown, List<Marker> now) {
		List<Marker> show = new ArrayList<>();
		Map<UUID, Marker> next = new HashMap<>();
		for (Marker m : now) {
			Marker before = shown.get(m.id());
			if (before == null || !before.sameAs(m)) {
				show.add(m);
			}
			next.put(m.id(), m);
		}
		List<UUID> remove = new ArrayList<>();
		for (UUID id : shown.keySet()) {
			if (!next.containsKey(id)) {
				remove.add(id);
			}
		}
		remove.sort(null);
		return new Changes(show, remove);
	}

	// ---- looking toward one ----------------------------------------------------------------------------

	/** Minecraft's yaw (degrees: 0 south, +z; 90 west, -x) of the way from a place {@code dx, dz} blocks off. */
	public static double yawToward(double dx, double dz) {
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** How far a heading is from the player's (degrees, -180 to 180): positive to their right, negative to their left. */
	public static double relative(double playerYaw, double yaw) {
		double d = (yaw - playerYaw) % 360.0;
		if (d > 180.0) {
			d -= 360.0;
		} else if (d <= -180.0) {
			d += 360.0;
		}
		return d;
	}

	/** A heading relative to the player's in words: ahead, ahead-right, right, behind-right, behind, behind-left, left, ahead-left. */
	public static String direction(double relative) {
		double a = Math.abs(relative);
		String side = relative > 0 ? "right" : "left";
		if (a <= 22.5) {
			return "ahead";
		}
		if (a <= 67.5) {
			return "ahead-" + side;
		}
		if (a <= 112.5) {
			return side;
		}
		if (a <= 157.5) {
			return "behind-" + side;
		}
		return "behind";
	}

	/**
	 * The marker the player (eyes at {@code x, z}, facing {@code yaw}) looks toward: the one nearest the middle of the view,
	 * within {@code maxAngle} degrees of it, not one they've reached. -1 if none.
	 */
	public static int lookedAt(List<Marker> markers, double x, double z, double yaw, double maxAngle) {
		int best = -1;
		double bestAngle = Double.MAX_VALUE;
		for (int i = 0; i < markers.size(); i++) {
			Marker m = markers.get(i);
			double dx = m.x() + 0.5 - x, dz = m.z() + 0.5 - z;
			if (dx * dx + dz * dz < HERE_BLOCKS * HERE_BLOCKS) {
				continue;
			}
			double angle = Math.abs(relative(yaw, yawToward(dx, dz)));
			if (angle <= maxAngle && angle < bestAngle) {
				best = i;
				bestAngle = angle;
			}
		}
		return best;
	}

	/** The nearest marker of an active quest to {@code x, y, z}, -1 if none. */
	public static int nearestTracked(List<Marker> markers, double x, double y, double z) {
		int best = -1;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < markers.size(); i++) {
			Marker m = markers.get(i);
			double d = distance(m, x, y, z);
			if (m.target().tracked() && d < bestDistance) {
				best = i;
				bestDistance = d;
			}
		}
		return best;
	}

	private static double distance(Marker m, double x, double y, double z) {
		double dx = m.x() + 0.5 - x, dy = m.y() + 0.5 - y, dz = m.z() + 0.5 - z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * The action bar line for a marker seen from {@code x, y, z} facing {@code yaw}: "Bleak Falls Barrow, 230 blocks,
	 * ahead-left", "..., 40 blocks to the door, ahead" for a target behind a door, ", here" once reached, and " and 2 more"
	 * after the name when other targets share the marker.
	 */
	public static String line(Marker m, double x, double y, double z, double yaw) {
		String name = m.also() > 0 ? m.label() + " and " + m.also() + " more" : m.label();
		double d = distance(m, x, y, z);
		if (d < HERE_BLOCKS) {
			return name + (m.target().viaDoor() ? ", at the door" : ", here");
		}
		long blocks = Math.round(d);
		String where = direction(relative(yaw, yawToward(m.x() + 0.5 - x, m.z() + 0.5 - z)));
		return name + ", " + blocks + (blocks == 1 ? " block" : " blocks") + (m.target().viaDoor() ? " to the door, " : ", ") + where;
	}
}
