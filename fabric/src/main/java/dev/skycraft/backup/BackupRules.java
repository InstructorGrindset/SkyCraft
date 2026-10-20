package dev.skycraft.backup;

import java.util.List;
import java.util.Locale;

/** Paired backups' settings, from {@code config/skycraft-backups.txt}. Pure. */
public final class BackupRules {
	/** The most pairs one can ask to keep. */
	public static final int MAX_KEEP = 1000;

	/** {@code keep}: how many pairs (a Skyrim save copy and a Minecraft world zip under one stamp) stay, newest first; 0 all. */
	public record Settings(int keep) {
		public static final Settings DEFAULTS = new Settings(5);
	}

	public static final String DEFAULT_TEXT = """
		# SkyCraft paired backups (paired_backups in skycraft-features.txt). Each time Skyrim loads a save with
		# Minecraft connected, Skyrim copies that save (its .ess and .skse) into
		# Documents\\My Games\\Skyrim Special Edition\\SkyCraft Backups\\<stamp>, and Minecraft zips its world into
		# skycraft-backups\\<stamp> <world>.zip in this instance's .minecraft folder, under the same stamp (the time
		# of the load). To go back to a pair, put the save files back in Skyrim's Saves folder and unzip the world
		# over Minecraft's saves folder (with Minecraft closed).

		# How many pairs to keep, newest first. Older ones are deleted on both sides. 0 keeps every one (they add
		# up: the Minecraft world can be hundreds of MB once Tamriel Wilds or Home have been explored).
		keep = 5
		""";

	private BackupRules() {
	}

	/** Parses {@code key = value} lines; bad ones keep the default and go to {@code problems}. */
	public static Settings parse(String text, List<String> problems) {
		int keep = Settings.DEFAULTS.keep();
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
			if (!key.equals("keep")) {
				problems.add("line " + n + ": unknown setting '" + key + "'");
				continue;
			}
			try {
				int parsed = Integer.parseInt(value);
				if (parsed < 0 || parsed > MAX_KEEP) {
					throw new NumberFormatException();
				}
				keep = parsed;
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": keep should be a whole number from 0 to " + MAX_KEEP + ", not '" + value + "'");
			}
		}
		return new Settings(keep);
	}
}
