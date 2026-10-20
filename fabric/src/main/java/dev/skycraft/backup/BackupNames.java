package dev.skycraft.backup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Names of the paired backups on Minecraft's side: {@code <stamp> <world>.zip} in the backup folder, the stamp the one
 * Skyrim chose for the pair ({@code YYYY-MM-DD_hh-mm-ss}, so names sort by age), and which ones go when only the newest
 * pairs are kept. Pure.
 */
public final class BackupNames {
	/** The stamp both halves name a pair's backups by: Skyrim's local time when it took its copy. */
	public static final Pattern STAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}");
	/** What a zip being written is called until it's whole. */
	public static final String PART = ".part";
	private static final int STAMP_LENGTH = 19;

	private BackupNames() {
	}

	public static boolean isStamp(String text) {
		return STAMP.matcher(text).matches();
	}

	/** The zip of world {@code world} for the pair {@code stamp}. Characters Windows won't have in a file name become _. */
	public static String zipName(String stamp, String world) {
		return stamp + " " + world.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_") + ".zip";
	}

	/** The stamp a backup file's name starts with (a zip, or a zip still being written), or null if it isn't one of ours. */
	public static @Nullable String stampOf(String fileName) {
		if (fileName.length() < STAMP_LENGTH + 1 || fileName.charAt(STAMP_LENGTH) != ' ' || !isStamp(fileName.substring(0, STAMP_LENGTH))) {
			return null;
		}
		return fileName.endsWith(".zip") || fileName.endsWith(".zip" + PART) ? fileName.substring(0, STAMP_LENGTH) : null;
	}

	/**
	 * The files in the backup folder to delete so that only the newest {@code keep} pairs stay (0 or less: every pair
	 * stays): the zips of older stamps, and zips left half written (a {@link #PART}, from a Minecraft that closed while
	 * writing one; never called while one is being written). Files that aren't backups are never named.
	 */
	public static List<String> toDelete(Collection<String> fileNames, int keep) {
		TreeSet<String> stamps = new TreeSet<>();
		for (String name : fileNames) {
			String stamp = stampOf(name);
			if (stamp != null && name.endsWith(".zip")) {
				stamps.add(stamp);
			}
		}
		TreeSet<String> kept = new TreeSet<>();
		for (String stamp : stamps.descendingSet()) {
			if (keep > 0 && kept.size() >= keep) {
				break;
			}
			kept.add(stamp);
		}
		List<String> out = new ArrayList<>();
		for (String name : fileNames) {
			String stamp = stampOf(name);
			if (stamp != null && (name.endsWith(PART) || !kept.contains(stamp))) {
				out.add(name);
			}
		}
		out.sort(null);
		return out;
	}
}
