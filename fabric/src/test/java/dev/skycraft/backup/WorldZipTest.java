package dev.skycraft.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class WorldZipTest {
	private static Path world(Path root) throws IOException {
		Path world = root.resolve("SkyCraft");
		Files.createDirectories(world.resolve("region"));
		Files.createDirectories(world.resolve("dimensions/skycraft/home/region"));
		Files.writeString(world.resolve("level.dat"), "level");
		Files.writeString(world.resolve("session.lock"), "lock");
		Files.write(world.resolve("region/r.0.0.mca"), new byte[10_000]);
		Files.writeString(world.resolve("dimensions/skycraft/home/region/r.-1.0.mca"), "home");
		return world;
	}

	private static void delete(Path root) throws IOException {
		try (Stream<Path> all = Files.walk(root)) {
			for (Path p : all.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(p);
			}
		}
	}

	@Test
	void zipsTheWorldLikeMinecraftsOwnBackup() throws IOException {
		Path root = Files.createTempDirectory("skycraft-zip");
		try {
			Path world = world(root);
			Path backups = Files.createDirectories(root.resolve("skycraft-backups"));
			Path target = backups.resolve(BackupNames.zipName("2026-10-17_15-45-23", "SkyCraft"));
			WorldZip.Result result = WorldZip.zip(world, target, "for Save 3");
			assertEquals(3, result.files());
			assertEquals(0, result.skipped());
			assertEquals(Files.size(target), result.bytes());
			try (ZipFile zip = new ZipFile(target.toFile())) {
				List<String> names = new ArrayList<>();
				Collections.list(zip.entries()).forEach(e -> names.add(e.getName()));
				Collections.sort(names);
				assertEquals(List.of("SkyCraft/dimensions/skycraft/home/region/r.-1.0.mca", "SkyCraft/level.dat", "SkyCraft/region/r.0.0.mca"), names);
				assertEquals("for Save 3", zip.getComment());
				assertEquals("level", new String(zip.getInputStream(zip.getEntry("SkyCraft/level.dat")).readAllBytes(), StandardCharsets.UTF_8));
				assertEquals(10_000, zip.getEntry("SkyCraft/region/r.0.0.mca").getSize());
			}
			try (Stream<Path> left = Files.list(backups)) {
				assertEquals(List.of(target.getFileName().toString()), left.map(p -> p.getFileName().toString()).toList()); // no .part left over
			}
		} finally {
			delete(root);
		}
	}

	@Test
	void replacesAnEarlierZipOfTheSameName() throws IOException {
		Path root = Files.createTempDirectory("skycraft-zip");
		try {
			Path world = world(root);
			Path target = root.resolve("2026-10-17_15-45-23 SkyCraft.zip");
			Files.writeString(target, "not a zip");
			WorldZip.zip(world, target, "");
			try (ZipFile zip = new ZipFile(target.toFile())) {
				assertTrue(zip.getEntry("SkyCraft/level.dat") != null);
			}
			assertFalse(Files.exists(root.resolve("2026-10-17_15-45-23 SkyCraft.zip.part")));
		} finally {
			delete(root);
		}
	}

	@Test
	void aMissingWorldLeavesNoPartBehind() throws IOException {
		Path root = Files.createTempDirectory("skycraft-zip");
		try {
			Path target = root.resolve("2026-10-17_15-45-23 Gone.zip");
			boolean failed = false;
			try {
				WorldZip.zip(root.resolve("Gone"), target, "");
			} catch (IOException e) {
				failed = true;
			}
			// Walking a missing folder reports it as one unreadable file, or fails: either way nothing half written stays.
			assertFalse(Files.exists(root.resolve("2026-10-17_15-45-23 Gone.zip.part")));
			assertTrue(failed || Files.exists(target));
		} finally {
			delete(root);
		}
	}
}
