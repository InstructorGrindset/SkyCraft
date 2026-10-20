package dev.skycraft.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TownProtocolTest {
	private static String bit(int value) {
		return "1u << " + Integer.numberOfTrailingZeros(value);
	}

	/** Proto.java and protocol/skycraft_protocol.h agree on everything the towns and building features send. */
	@Test
	void matchesTheProtocolHeader() throws IOException {
		Path header = null;
		for (Path dir = Path.of("").toAbsolutePath(); dir != null && header == null; dir = dir.getParent()) {
			Path candidate = dir.resolve("protocol/skycraft_protocol.h");
			if (Files.exists(candidate)) {
				header = candidate;
			}
		}
		assertNotNull(header, "protocol/skycraft_protocol.h not found above " + Path.of("").toAbsolutePath());
		String text = Files.readString(header);
		String[] expected = {
			"kInOpenDoor = " + Proto.IN_OPEN_DOOR,
			"kInRedstonePulse = " + Proto.IN_REDSTONE_PULSE,
			"kInDwemerDoor = " + Proto.IN_DWEMER_DOOR,
			"kEvTownCrime = " + Proto.EV_TOWN_CRIME,
			"kEvTownBuild = " + Proto.EV_TOWN_BUILD,
			"kEvHearthfireStage = " + Proto.EV_HEARTHFIRE_STAGE,
			"kEvRedstoneDoor = " + Proto.EV_REDSTONE_DOOR,
			"kCrimeGround = " + Proto.CRIME_GROUND,
			"kCrimeObject = " + Proto.CRIME_OBJECT,
			"kCrimeBlock = " + Proto.CRIME_BLOCK,
			"kBuildPlace = " + Proto.BUILD_PLACE,
			"kBuildBreak = " + Proto.BUILD_BREAK,
			"kBuildDig = " + Proto.BUILD_DIG,
			"kStageSmallHouse = " + Proto.STAGE_SMALL_HOUSE,
			"kStageMainHall = " + Proto.STAGE_MAIN_HALL,
			"kStageWings = " + Proto.STAGE_WINGS,
			"kRenPassages = " + Proto.REN_PASSAGES,
			"kPassageDoor = " + Proto.PASSAGE_DOOR,
			"kPassageIronDoor = " + Proto.PASSAGE_IRON_DOOR,
			"kPassageLadder = " + Proto.PASSAGE_LADDER,
			"kPassageKindMask = " + Proto.PASSAGE_KIND_MASK,
			"kPassageOpen = " + bit(Proto.PASSAGE_OPEN),
			"kPassageFacingShift = " + Proto.PASSAGE_FACING_SHIFT,
			"kPassageFacingMask = 3u << " + Proto.PASSAGE_FACING_SHIFT,
			"kPassageUpper = " + bit(Proto.PASSAGE_UPPER),
			"kActorGuard = " + bit(Proto.ACTOR_GUARD),
			"kFeatNpcDoorsLadders = " + bit(Proto.FEATURE_NPC_DOORS_LADDERS),
			"kFeatDwemerRedstone = " + bit(Proto.FEATURE_DWEMER_REDSTONE),
			"kFeatHearthfire = " + bit(Proto.FEATURE_HEARTHFIRE),
			"kTownPassages = " + bit(Proto.TOWN_PASSAGES),
			"kTownHearthfirePlot = " + bit(Proto.TOWN_HEARTHFIRE_PLOT),
			"offsetof(SkyState, townFlags) == 0x" + Long.toHexString(Proto.SS_TOWN_FLAGS).toUpperCase(),
			"offsetof(SkyState, hearthPlot) == 0x" + Long.toHexString(Proto.SS_HEARTH_PLOT).toUpperCase(),
			"offsetof(McState, hearthPlot) == 0x" + Long.toHexString(Proto.MS_HEARTH_PLOT).toUpperCase(),
			"offsetof(McState, hearthBlocks) == 0x" + Long.toHexString(Proto.MS_HEARTH_BLOCKS).toUpperCase(),
		};
		for (String e : expected) {
			assertTrue(text.contains(e), "the header should say: " + e);
		}
	}

	@Test
	void theTownsNumbersAreTheirsAlone() {
		// FeatureBits 16-18 of the 16-21 set aside for towns; ActorFlags 16 of 16-17; render type 14 of 14-15.
		assertEquals(0, (Proto.FEATURE_NPC_DOORS_LADDERS | Proto.FEATURE_DWEMER_REDSTONE | Proto.FEATURE_HEARTHFIRE) & ~(0x3F << 16));
		assertEquals(0, Proto.ACTOR_GUARD & ~(0x3 << 16));
		assertEquals(0, Proto.ACTOR_GUARD & (Proto.ACTOR_HOSTILE | Proto.ACTOR_DEAD | Proto.ACTOR_ESSENTIAL | Proto.ACTOR_IN_COMBAT | Proto.ACTOR_SPAWNED
			| Proto.ACTOR_COMPANION | Proto.ACTOR_COMPANION_WAITING | Proto.ACTOR_KIND_MASK));
		int passageFields = Proto.PASSAGE_KIND_MASK | Proto.PASSAGE_OPEN | Proto.PASSAGE_FACING_MASK | Proto.PASSAGE_UPPER;
		assertEquals(Integer.bitCount(Proto.PASSAGE_KIND_MASK) + 1 + 2 + 1, Integer.bitCount(passageFields)); // no field overlaps another
		assertTrue(passageFields < 0x100); // fits the record's byte
	}
}
