package dev.skycraft.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BackupNamesTest {
	@Test
	void stamps() {
		assertTrue(BackupNames.isStamp("2026-10-17_15-45-23"));
		assertFalse(BackupNames.isStamp("2026-10-17 15-45-23"));
		assertFalse(BackupNames.isStamp("2026-10-17_15-45-2"));
		assertFalse(BackupNames.isStamp(""));
	}

	@Test
	void zipNames() {
		assertEquals("2026-10-17_15-45-23 SkyCraft.zip", BackupNames.zipName("2026-10-17_15-45-23", "SkyCraft"));
		assertEquals("2026-10-17_15-45-23 a_b_c_.zip", BackupNames.zipName("2026-10-17_15-45-23", "a:b/c?"));
		assertEquals("2026-10-17_15-45-23", BackupNames.stampOf("2026-10-17_15-45-23 SkyCraft.zip"));
		assertEquals("2026-10-17_15-45-23", BackupNames.stampOf("2026-10-17_15-45-23 SkyCraft.zip.part"));
		assertNull(BackupNames.stampOf("2026-10-17_15-45-23 notes.txt"));
		assertNull(BackupNames.stampOf("2026-10-17_15-45-23.zip"));
		assertNull(BackupNames.stampOf("SkyCraft.zip"));
	}

	@Test
	void keepsTheNewestPairs() {
		List<String> files = List.of("2026-10-19_08-00-00 SkyCraft.zip", "2026-10-17_10-00-00 SkyCraft.zip", "2026-10-18_12-30-00 SkyCraft.zip",
			"2026-10-16_09-00-00 SkyCraft.zip.part", "readme.txt", "2026-10-15_00-00-00 my notes.txt");
		assertEquals(List.of("2026-10-16_09-00-00 SkyCraft.zip.part", "2026-10-17_10-00-00 SkyCraft.zip"), BackupNames.toDelete(files, 2));
		assertEquals(List.of("2026-10-16_09-00-00 SkyCraft.zip.part"), BackupNames.toDelete(files, 3));
		assertEquals(List.of("2026-10-16_09-00-00 SkyCraft.zip.part"), BackupNames.toDelete(files, 0)); // 0 keeps every pair
		assertEquals(List.of("2026-10-16_09-00-00 SkyCraft.zip.part", "2026-10-17_10-00-00 SkyCraft.zip", "2026-10-18_12-30-00 SkyCraft.zip"),
			BackupNames.toDelete(files, 1));
	}

	@Test
	void aPairIsOneStampWhateverTheWorld() {
		// Two worlds backed up under one stamp (the player switched worlds) count as one pair.
		List<String> files = List.of("2026-10-19_08-00-00 SkyCraft.zip", "2026-10-19_08-00-00 Test.zip", "2026-10-18_08-00-00 SkyCraft.zip");
		assertEquals(List.of("2026-10-18_08-00-00 SkyCraft.zip"), BackupNames.toDelete(files, 1));
	}
}
