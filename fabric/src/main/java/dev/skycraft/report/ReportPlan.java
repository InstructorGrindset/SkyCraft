package dev.skycraft.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * What {@code /skycraft report} packs for a tester to send, and the packing: Minecraft's {@code logs/latest.log}, its
 * newest crash reports, every {@code config/skycraft-*.txt} and the Skyrim plugin's {@code SkyCraft.log}, and nothing
 * else, plus a {@code README.txt} saying what's in the zip and what wasn't found. Only regular files are taken, never
 * a link to somewhere else. Pure (files only): SkyReport finds the folders and runs it.
 */
public final class ReportPlan {
	/** How many of Minecraft's newest crash reports go in. */
	public static final int CRASH_REPORTS = 3;
	public static final String SKYRIM_LOG = "SkyCraft.log";
	/**
	 * Where the plugin writes SkyCraft.log under the user's Documents folder (skse/src/main.cpp SetupLog, in CommonLib's
	 * SKSE::log::log_directory): the Steam game's folder, then GOG's.
	 */
	public static final List<List<String>> SKYRIM_LOG_DIRS = List.of(
		List.of("My Games", "Skyrim Special Edition", "SKSE"),
		List.of("My Games", "Skyrim Special Edition GOG", "SKSE"));
	public static final String README = "README.txt";
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

	/** One file in the zip: its name there, and where it's read from. */
	public record Entry(String name, Path source) {
	}

	/** What {@link #write} did: the entries it packed and the notes for the player (missing and unreadable files). */
	public record Written(List<String> names, List<String> notes) {
	}

	private final List<Entry> entries;
	private final List<String> notes;
	private final boolean skyrimLogFound;

	private ReportPlan(List<Entry> entries, List<String> notes, boolean skyrimLogFound) {
		this.entries = List.copyOf(entries);
		this.notes = List.copyOf(notes);
		this.skyrimLogFound = skyrimLogFound;
	}

	/** The files the zip will hold, in order. */
	public List<Entry> entries() {
		return this.entries;
	}

	/** What wasn't found, for the README and the player. */
	public List<String> notes() {
		return this.notes;
	}

	/** Whether the Skyrim plugin's SkyCraft.log was found. */
	public boolean skyrimLogFound() {
		return this.skyrimLogFound;
	}

	/** {@code skycraft-report-2026-10-17_15-04-05.zip}. */
	public static String fileName(LocalDateTime when) {
		return "skycraft-report-" + FILE_TIME.format(when) + ".zip";
	}

	/** Where SkyCraft.log may be: under each of {@code documents} (the user's Documents folders, best first), Steam's then GOG's. */
	public static List<Path> skyrimLogs(List<Path> documents) {
		List<Path> out = new ArrayList<>();
		for (Path doc : documents) {
			for (List<String> dir : SKYRIM_LOG_DIRS) {
				Path p = doc;
				for (String part : dir) {
					p = p.resolve(part);
				}
				Path log = p.resolve(SKYRIM_LOG);
				if (!out.contains(log)) {
					out.add(log);
				}
			}
		}
		return out;
	}

	/**
	 * The plan for Minecraft's game folder {@code gameDir} and config folder {@code configDir}, with the newest of
	 * {@code skyrimLogs} that exists as Skyrim's log and the {@code crashReports} newest crash reports.
	 */
	public static ReportPlan plan(Path gameDir, Path configDir, List<Path> skyrimLogs, int crashReports) {
		List<Entry> entries = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		Path latest = gameDir.resolve("logs").resolve("latest.log");
		if (regular(latest)) {
			entries.add(new Entry("minecraft/logs/latest.log", latest));
		} else {
			notes.add("Minecraft's logs/latest.log wasn't found.");
		}
		List<Path> crashes = files(gameDir.resolve("crash-reports"), name -> name.endsWith(".txt"));
		crashes.sort(Comparator.comparing(ReportPlan::modified).thenComparing(p -> p.getFileName().toString()).reversed());
		for (Path crash : crashes.subList(0, Math.min(Math.max(0, crashReports), crashes.size()))) {
			entries.add(new Entry("minecraft/crash-reports/" + crash.getFileName(), crash));
		}
		if (crashes.isEmpty()) {
			notes.add("Minecraft has no crash reports.");
		}
		List<Path> configs = files(configDir, name -> name.startsWith("skycraft-") && name.endsWith(".txt"));
		configs.sort(Comparator.comparing(p -> p.getFileName().toString()));
		for (Path config : configs) {
			entries.add(new Entry("minecraft/config/" + config.getFileName(), config));
		}
		if (configs.isEmpty()) {
			notes.add("There are no config/skycraft-*.txt files.");
		}
		Path skyrim = skyrimLogs.stream().filter(ReportPlan::regular).max(Comparator.comparing(ReportPlan::modified)).orElse(null);
		if (skyrim != null) {
			entries.add(new Entry("skyrim/" + SKYRIM_LOG, skyrim));
		} else {
			notes.add("Skyrim's " + SKYRIM_LOG + " wasn't found. SkyCraft writes it in Documents/My Games/Skyrim Special Edition/SKSE "
				+ "(Skyrim Special Edition GOG for the GOG game) when Skyrim starts with SkyCraft; if yours is somewhere else, send it too.");
		}
		return new ReportPlan(entries, notes, skyrim != null);
	}

	/**
	 * Packs the plan into {@code zip}: written beside it first and moved into place when done, so a half written report
	 * never has the name. A file that can't be read is left out with a note. {@code writtenAt} goes in the README.
	 */
	public Written write(Path zip, String writtenAt) throws IOException {
		List<String> names = new ArrayList<>();
		List<String> notes = new ArrayList<>(this.notes);
		Path part = zip.resolveSibling(zip.getFileName() + ".part");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(part))) {
			for (Entry e : this.entries) {
				try {
					out.putNextEntry(new ZipEntry(e.name()));
					Files.copy(e.source(), out);
					out.closeEntry();
					names.add(e.name());
				} catch (IOException ex) {
					// Some of it may be in the zip already: the note says it's incomplete.
					out.closeEntry();
					notes.add(e.name() + " couldn't be read (" + ex.getMessage() + "); it may be missing or cut short.");
				}
			}
			out.putNextEntry(new ZipEntry(README));
			out.write(readme(writtenAt, names, notes).getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		} catch (IOException e) {
			Files.deleteIfExists(part);
			throw e;
		}
		Files.move(part, zip, StandardCopyOption.REPLACE_EXISTING);
		return new Written(names, notes);
	}

	/** The README.txt: when, what's in the zip, and what isn't. */
	static String readme(String writtenAt, List<String> names, List<String> notes) {
		StringBuilder sb = new StringBuilder();
		sb.append("SkyCraft report, written ").append(writtenAt).append("\n\n");
		sb.append("Send this zip with your bug report. It holds only these files:\n\n");
		for (String name : names) {
			sb.append("  ").append(name).append('\n');
		}
		sb.append("\nminecraft/ is from Minecraft's game folder: its log, crash reports and SkyCraft's config files.\n");
		sb.append("skyrim/ is the SkyCraft plugin's log from Skyrim's SKSE folder.\n");
		sb.append("Logs hold your Minecraft name and the folders the games run from.\n");
		if (!notes.isEmpty()) {
			sb.append("\nNotes:\n\n");
			for (String note : notes) {
				sb.append("  ").append(note).append('\n');
			}
		}
		return sb.toString().replace("\n", "\r\n"); // read on Windows
	}

	private static boolean regular(Path p) {
		return Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS);
	}

	private static FileTime modified(Path p) {
		try {
			return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS);
		} catch (IOException e) {
			return FileTime.fromMillis(0);
		}
	}

	/** The regular files directly in {@code dir} whose name {@code wanted} takes; none if there's no such folder. */
	private static List<Path> files(Path dir, java.util.function.Predicate<String> wanted) {
		List<Path> out = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path p : stream) {
				if (regular(p) && wanted.test(p.getFileName().toString())) {
					out.add(p);
				}
			}
		} catch (IOException e) {
			// Unreadable folder: nothing from it.
		}
		return out;
	}
}
