package dev.skycraft.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackupRulesTest {
	@Test
	void theDefaultsParseAsTheDefaults() {
		List<String> problems = new ArrayList<>();
		assertEquals(BackupRules.Settings.DEFAULTS, BackupRules.parse(BackupRules.DEFAULT_TEXT, problems));
		assertTrue(problems.isEmpty(), problems.toString());
		assertTrue(BackupRules.DEFAULT_TEXT.contains("Documents\\My Games\\Skyrim Special Edition\\SkyCraft Backups"));
	}

	@Test
	void keepAnyNumberOrAll() {
		List<String> problems = new ArrayList<>();
		assertEquals(12, BackupRules.parse("keep = 12  # a dozen", problems).keep());
		assertEquals(0, BackupRules.parse("KEEP=0", problems).keep());
		assertTrue(problems.isEmpty(), problems.toString());
	}

	@Test
	void badLinesKeepTheDefault() {
		List<String> problems = new ArrayList<>();
		assertEquals(5, BackupRules.parse("keep = -1\nkeep = many\nkeep 3\ncopies = 2\nkeep = 5000", problems).keep());
		assertEquals(5, problems.size(), problems.toString());
	}
}
