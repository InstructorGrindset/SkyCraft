package dev.skycraft.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownRulesTest {
	@Test
	void theDefaultsReadCleanly() {
		List<String> problems = new ArrayList<>();
		TownRules r = TownRules.parse(TownRules.DEFAULT_TEXT, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(60, r.doorCloseTicks());
		assertEquals(6, r.crimeValue(Proto.CRIME_GROUND));
		assertEquals(20, r.crimeValue(Proto.CRIME_OBJECT));
		assertEquals(4, r.crimeValue(Proto.CRIME_BLOCK));
		assertEquals(0, r.crimeValue(99));
		assertEquals(60, r.crimeBatchTicks());
		assertEquals(80, r.stageBlocks(Proto.STAGE_SMALL_HOUSE));
		assertEquals(320, r.stageBlocks(Proto.STAGE_MAIN_HALL));
		assertEquals(720, r.stageBlocks(Proto.STAGE_WINGS));
		assertEquals(Integer.MAX_VALUE, r.stageBlocks(4));
	}

	@Test
	void editedNumbersAndBadLines() {
		List<String> problems = new ArrayList<>();
		TownRules r = TownRules.parse("door_close_after = 1.5\ncrime_block = 9 # mine\nsmall_house = 10\nmain_hall = 5\nwings = abc\nwho = 3\nnonsense\ncrime_ground = -2\n",
			problems);
		assertEquals(30, r.doorCloseTicks());
		assertEquals(9, r.crimeValue(Proto.CRIME_BLOCK));
		assertEquals(6, r.crimeValue(Proto.CRIME_GROUND)); // -2 refused
		assertEquals(10, r.stageBlocks(Proto.STAGE_SMALL_HOUSE));
		assertEquals(11, r.stageBlocks(Proto.STAGE_MAIN_HALL)); // never fewer than the stage before
		assertEquals(720, r.stageBlocks(Proto.STAGE_WINGS));
		assertEquals(5, problems.size(), problems.toString());
	}

	@Test
	void whatBreakingABlockInTownIs() {
		assertEquals(0, TownRules.brokenBlockCrime("me", "me", false));
		assertEquals(0, TownRules.brokenBlockCrime("me", "me", true)); // my own block in a hole I dug
		assertEquals(Proto.CRIME_BLOCK, TownRules.brokenBlockCrime("me", "friend", false));
		assertEquals(Proto.CRIME_BLOCK, TownRules.brokenBlockCrime("me", "friend", true));
		assertEquals(Proto.CRIME_GROUND, TownRules.brokenBlockCrime("me", null, true)); // the town's ground, revealed by digging
		assertEquals(0, TownRules.brokenBlockCrime("me", null, false)); // nobody kept track: never a crime
	}

	@Test
	void diggingSkyrimIsGroundOrAnObject() {
		assertEquals(Proto.CRIME_GROUND, TownRules.digCrime(false));
		assertEquals(Proto.CRIME_OBJECT, TownRules.digCrime(true));
		assertEquals("small house", TownRules.stageName(Proto.STAGE_SMALL_HOUSE));
		assertEquals("wings", TownRules.stageName(TownRules.lastStage()));
	}
}
