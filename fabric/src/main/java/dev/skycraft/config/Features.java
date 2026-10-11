package dev.skycraft.config;

import dev.skycraft.SkyCraft;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * On/off switches for the fork's features, from {@code config/skycraft-features.txt}. Every feature
 * is on unless its line says off. Read once per game launch; restart Minecraft after editing.
 */
public final class Features {
	public enum Feature {
		KILL_DROPS("kill_drops", "Skyrim animals you kill drop Minecraft items (skycraft-loot.txt)"),
		KILL_XP("kill_xp", "Skyrim kills drop Minecraft XP orbs"),
		ITEM_CONVERSION("item_conversion", "loot picked up in Skyrim becomes Minecraft items (skycraft-items.txt)"),
		DUNGEON_CHESTS("dungeon_chests", "first loot of a dungeon chest also rolls a Minecraft chest (skycraft-dungeons.txt)"),
		MERCHANTS("merchants", "right click a Skyrim vendor with an empty hand to trade (skycraft-trades.txt, skycraft-towns.txt)"),
		TIME_SYNC("time_sync", "Minecraft's clock follows Skyrim's"),
		BED_SLEEP("bed_sleep", "a night in a Minecraft bed passes the night in Skyrim"),
		DEEP_CREATURES("deep_creatures", "Skyrim creatures spawn in dark tunnels you dug (skycraft-deep.txt)");

		public final String key;
		public final String about;

		Feature(String key, String about) {
			this.key = key;
			this.about = about;
		}
	}

	public static final String FILE = "skycraft-features.txt";
	public static final String DEFAULT_TEXT = defaultText();

	private static Map<Feature, Boolean> switches;

	private Features() {
	}

	/** Whether this feature is switched on. */
	public static boolean on(Feature feature) {
		if (switches == null) {
			List<String> problems = new ArrayList<>();
			switches = parse(Config.read(FILE, DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft: {} {}", FILE, p));
			List<String> off = new ArrayList<>();
			switches.forEach((f, v) -> {
				if (!v) {
					off.add(f.key);
				}
			});
			SkyCraft.LOG.info("SkyCraft: features {}", off.isEmpty() ? "all on" : "off: " + String.join(", ", off));
		}
		return switches.getOrDefault(feature, true);
	}

	/** Parses {@code key = on|off} lines. Unknown keys and values go to {@code problems}. */
	public static Map<Feature, Boolean> parse(String text, List<String> problems) {
		Map<Feature, Boolean> out = new EnumMap<>(Feature.class);
		for (Feature f : Feature.values()) {
			out.put(f, true);
		}
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
				problems.add("line " + n + ": expected '<feature> = on' or '<feature> = off'");
				continue;
			}
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).strip().toLowerCase(Locale.ROOT);
			Feature feature = null;
			for (Feature f : Feature.values()) {
				if (f.key.equals(key)) {
					feature = f;
				}
			}
			if (feature == null) {
				problems.add("line " + n + ": unknown feature '" + key + "'");
				continue;
			}
			switch (value) {
				case "on", "true", "yes" -> out.put(feature, true);
				case "off", "false", "no" -> out.put(feature, false);
				default -> problems.add("line " + n + ": '" + value + "' should be on or off");
			}
		}
		return out;
	}

	private static String defaultText() {
		StringBuilder sb = new StringBuilder();
		sb.append("# SkyCraft fork features. Set a line to off to switch that feature off, then restart Minecraft.\n");
		for (Feature f : Feature.values()) {
			sb.append("\n# ").append(f.about).append('\n').append(f.key).append(" = on\n");
		}
		return sb.toString();
	}
}
