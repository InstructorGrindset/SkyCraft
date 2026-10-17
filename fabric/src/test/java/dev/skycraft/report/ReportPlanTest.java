package dev.skycraft.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ReportPlanTest {
	/** A Prism instance's .minecraft and a Documents folder, with things a report must and mustn't take. */
	private static Path game() throws IOException {
		Path root = Files.createTempDirectory("skycraft-report-test");
		Path game = root.resolve(".minecraft");
		write(game.resolve("logs/latest.log"), "the log");
		write(game.resolve("logs/2026-10-16-1.log.gz"), "old log");
		for (int i = 1; i <= 5; i++) {
			Path crash = write(game.resolve("crash-reports/crash-2026-10-0" + i + "_12.00.00-client.txt"), "crash " + i);
			Files.setLastModifiedTime(crash, FileTime.fromMillis(1_000_000L * i));
		}
		write(game.resolve("crash-reports/notes.md"), "not a report");
		write(game.resolve("config/skycraft-features.txt"), "merchants = on\n");
		write(game.resolve("config/skycraft-items.txt"), "rules\n");
		write(game.resolve("config/skycraft-items.txt.old"), "old rules\n");
		write(game.resolve("config/skycraft.properties"), "join=abc-def.e4mc.link\n");
		write(game.resolve("config/othermod-settings.txt"), "not ours\n");
		write(game.resolve("options.txt"), "fov:0.5\n");
		write(game.resolve("saves/SkyCraft/level.dat"), "a world");
		return game;
	}

	private static Path write(Path p, String text) throws IOException {
		Files.createDirectories(p.getParent());
		Files.writeString(p, text);
		return p;
	}

	private static List<String> names(ReportPlan plan) {
		return plan.entries().stream().map(ReportPlan.Entry::name).toList();
	}

	@Test
	void takesTheLogTheNewestCrashReportsOurConfigsAndSkyrimsLog() throws IOException {
		Path game = game();
		Path docs = game.getParent().resolve("Documents");
		Path skyrimLog = write(docs.resolve("My Games/Skyrim Special Edition/SKSE/SkyCraft.log"), "plugin log");
		write(docs.resolve("My Games/Skyrim Special Edition/SKSE/skse64.log"), "not ours");
		ReportPlan plan = ReportPlan.plan(game, game.resolve("config"), ReportPlan.skyrimLogs(List.of(docs)), 3);
		assertEquals(List.of(
			"minecraft/logs/latest.log",
			"minecraft/crash-reports/crash-2026-10-05_12.00.00-client.txt",
			"minecraft/crash-reports/crash-2026-10-04_12.00.00-client.txt",
			"minecraft/crash-reports/crash-2026-10-03_12.00.00-client.txt",
			"minecraft/config/skycraft-features.txt",
			"minecraft/config/skycraft-items.txt",
			"skyrim/SkyCraft.log"), names(plan));
		assertEquals(skyrimLog, plan.entries().getLast().source());
		assertTrue(plan.skyrimLogFound());
		assertTrue(plan.notes().isEmpty(), plan.notes().toString());
	}

	@Test
	void theZipHoldsExactlyThoseFilesAndAReadme() throws IOException {
		Path game = game();
		Path docs = game.getParent().resolve("Documents");
		write(docs.resolve("My Games/Skyrim Special Edition/SKSE/SkyCraft.log"), "plugin log");
		ReportPlan plan = ReportPlan.plan(game, game.resolve("config"), ReportPlan.skyrimLogs(List.of(docs)), 3);
		Path zip = game.resolve(ReportPlan.fileName(LocalDateTime.of(2026, 10, 17, 15, 4, 5)));
		assertEquals("skycraft-report-2026-10-17_15-04-05.zip", zip.getFileName().toString());
		ReportPlan.Written written = plan.write(zip, "2026-10-17 15:04:05");
		assertEquals(names(plan), written.names());
		Map<String, String> contents = read(zip);
		List<String> expected = new ArrayList<>(names(plan));
		expected.add(ReportPlan.README);
		assertEquals(expected, new ArrayList<>(contents.keySet()));
		assertEquals("the log", contents.get("minecraft/logs/latest.log"));
		assertEquals("crash 5", contents.get("minecraft/crash-reports/crash-2026-10-05_12.00.00-client.txt"));
		assertEquals("plugin log", contents.get("skyrim/SkyCraft.log"));
		String readme = contents.get(ReportPlan.README);
		assertTrue(readme.contains("2026-10-17 15:04:05"), readme);
		assertTrue(readme.contains("  skyrim/SkyCraft.log\r\n"), readme);
		assertFalse(Files.exists(zip.resolveSibling(zip.getFileName() + ".part")), "the part file is moved into place");
	}

	@Test
	void missingSkyrimLogIsANoteInTheReadme() throws IOException {
		Path game = game();
		Path docs = game.getParent().resolve("Documents");
		ReportPlan plan = ReportPlan.plan(game, game.resolve("config"), ReportPlan.skyrimLogs(List.of(docs)), 3);
		assertFalse(plan.skyrimLogFound());
		assertFalse(names(plan).contains("skyrim/SkyCraft.log"));
		assertEquals(1, plan.notes().size(), plan.notes().toString());
		Path zip = game.resolve("report.zip");
		ReportPlan.Written written = plan.write(zip, "now");
		String readme = read(zip).get(ReportPlan.README);
		assertTrue(readme.contains("SkyCraft.log wasn't found"), readme);
		assertEquals(plan.notes(), written.notes());
	}

	@Test
	void theNewestSkyrimLogOfSteamsAndGogsFoldersWins() throws IOException {
		Path docs = Files.createTempDirectory("skycraft-report-docs");
		Path steam = write(docs.resolve("My Games/Skyrim Special Edition/SKSE/SkyCraft.log"), "steam");
		Path gog = write(docs.resolve("My Games/Skyrim Special Edition GOG/SKSE/SkyCraft.log"), "gog");
		Files.setLastModifiedTime(steam, FileTime.fromMillis(1_000_000L));
		Files.setLastModifiedTime(gog, FileTime.fromMillis(2_000_000L));
		Path game = Files.createTempDirectory("skycraft-report-empty");
		ReportPlan plan = ReportPlan.plan(game, game.resolve("config"), ReportPlan.skyrimLogs(List.of(docs, docs)), 3);
		assertEquals(gog, plan.entries().getLast().source());
		assertEquals(List.of("skyrim/SkyCraft.log"), names(plan));
		assertEquals(3, plan.notes().size(), "no latest.log, no crash reports, no configs: " + plan.notes());
		assertEquals(2, ReportPlan.skyrimLogs(List.of(docs, docs)).size(), "a folder listed twice is looked in once");
	}

	@Test
	void takesNoCrashReportsWhenAskedForNone() throws IOException {
		Path game = game();
		ReportPlan plan = ReportPlan.plan(game, game.resolve("config"), List.of(), 0);
		assertFalse(names(plan).stream().anyMatch(n -> n.startsWith("minecraft/crash-reports/")));
	}

	private static Map<String, String> read(Path zip) throws IOException {
		Map<String, String> out = new LinkedHashMap<>();
		try (InputStream in = Files.newInputStream(zip); ZipInputStream z = new ZipInputStream(in)) {
			for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
				out.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		return out;
	}
}
