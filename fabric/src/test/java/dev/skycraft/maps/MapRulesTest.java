package dev.skycraft.maps;

import static dev.skycraft.link.Proto.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.maps.MapRules.Ground;
import dev.skycraft.maps.MapRules.Pixel;
import dev.skycraft.maps.MapRules.Tone;
import org.junit.jupiter.api.Test;

class MapRulesTest {
	private static final double LEVEL = 1.0, SLOPE = 0.7, CLIFF = 0.3;

	@Test
	void theLandIsColouredByWhatItsMadeOfAndHowSteepItIs() {
		assertEquals(Tone.GRASS, MapRules.tone(DIG_GRASS, true, true, LEVEL));
		assertEquals(Tone.DIRT, MapRules.tone(DIG_GRASS, true, true, SLOPE), "grass on a slope shows its earth");
		assertEquals(Tone.ROCK, MapRules.tone(DIG_GRASS, true, true, CLIFF), "Skyrim paints its cliffs with rock");
		assertEquals(Tone.DIRT, MapRules.tone(DIG_DIRT, true, true, SLOPE));
		assertEquals(Tone.MUD, MapRules.tone(DIG_MUD, true, true, LEVEL));
		assertEquals(Tone.SAND, MapRules.tone(DIG_SAND, true, true, LEVEL));
		assertEquals(Tone.ROCK, MapRules.tone(DIG_SAND, true, true, CLIFF));
		assertEquals(Tone.SNOW, MapRules.tone(DIG_SNOW, true, true, 0.4), "snow holds on steep slopes");
		assertEquals(Tone.ROCK, MapRules.tone(DIG_SNOW, true, true, 0.3));
		assertEquals(Tone.ICE, MapRules.tone(DIG_ICE, true, true, CLIFF));
		assertEquals(Tone.ROCK, MapRules.tone(DIG_STONE, true, true, LEVEL));
		assertEquals(Tone.ROCK, MapRules.tone(DIG_NONE, true, true, LEVEL));
		assertEquals(Tone.ASH, MapRules.tone(DIG_ASH, true, true, LEVEL));
	}

	@Test
	void roadsAreTheLandsGravelAndCobbles() {
		assertEquals(Tone.ROAD, MapRules.tone(DIG_GRAVEL, true, true, LEVEL));
		assertEquals(Tone.ROAD, MapRules.tone(DIG_COBBLE, true, true, SLOPE));
		assertEquals(Tone.ROCK, MapRules.tone(DIG_COBBLE, true, true, CLIFF));
		// Gravel and cobbles on an object (a rock, a ruin's rubble) are rock, not road.
		assertEquals(Tone.ROCK, MapRules.tone(DIG_COBBLE, false, true, LEVEL));
		assertEquals(Tone.ROCK, MapRules.tone(DIG_GRAVEL, false, true, LEVEL));
	}

	@Test
	void objectsAreAStoneOrWoodTone() {
		assertEquals(Tone.BUILDING, MapRules.tone(DIG_NONE, false, false, LEVEL), "buildings can't be dug and carry no material");
		assertEquals(Tone.BUILDING, MapRules.tone(DIG_PLANKS, false, false, CLIFF));
		assertEquals(Tone.ROCK, MapRules.tone(DIG_STONE, false, true, LEVEL), "a rock or cliff");
		assertEquals(Tone.ROCK, MapRules.tone(DIG_STONE, false, true, CLIFF));
		assertEquals(Tone.WOOD, MapRules.tone(DIG_OAK_LOG, false, true, LEVEL));
		assertEquals(Tone.WOOD, MapRules.tone(DIG_SPRUCE_LOG, false, true, LEVEL));
		assertEquals(Tone.WOOD, MapRules.tone(DIG_BIRCH_LOG, false, true, LEVEL));
		assertEquals(Tone.WOOD, MapRules.tone(DIG_PLANKS, false, true, LEVEL));
		assertEquals(Tone.MOSS, MapRules.tone(DIG_ORGANIC, false, true, LEVEL));
		assertEquals(Tone.METAL, MapRules.tone(DIG_METAL, false, true, LEVEL));
		assertEquals(Tone.SNOW, MapRules.tone(DIG_SNOW, false, true, CLIFF), "a snowy rock's top");
		assertEquals(Tone.GRASS, MapRules.tone(DIG_GRASS, false, true, SLOPE), "a grassy mound");
		assertEquals(Tone.BUILDING, MapRules.tone(DIG_GLASS, false, true, LEVEL));
	}

	@Test
	void landIsShadedAsVanillaShadesItsOwn() {
		// Level with the pixel north: normal either side of the dither.
		assertEquals(MapRules.NORMAL, MapRules.landShade(64, 64, 1, 0, 0));
		assertEquals(MapRules.NORMAL, MapRules.landShade(64, 64, 1, 0, 1));
		// A block up is bright on both sides of the dither: vanilla's own arithmetic, 0.8 - 0.2, comes out a hair over 0.6.
		assertEquals(MapRules.HIGH, MapRules.landShade(65, 64, 1, 0, 0));
		assertEquals(MapRules.HIGH, MapRules.landShade(65, 64, 1, 1, 0));
		// And a block down dark on both, the same hair the other way.
		assertEquals(MapRules.LOW, MapRules.landShade(63, 64, 1, 1, 0));
		assertEquals(MapRules.LOW, MapRules.landShade(63, 64, 1, 0, 0));
		// Two up: bright; two down: dark.
		assertEquals(MapRules.HIGH, MapRules.landShade(66, 64, 1, 0, 0));
		assertEquals(MapRules.LOW, MapRules.landShade(62, 64, 1, 0, 0));
		// A zoomed out map needs more height for the same shading: 2 up at 8 blocks a pixel is 8/12 = 0.67, less 0.2.
		assertEquals(MapRules.NORMAL, MapRules.landShade(66, 64, 8, 0, 0));
		assertEquals(MapRules.HIGH, MapRules.landShade(68, 64, 8, 0, 0));
	}

	@Test
	void waterIsShadedByDepth() {
		assertEquals(MapRules.HIGH, MapRules.waterShade(1, 0, 0));
		assertEquals(MapRules.HIGH, MapRules.waterShade(1, 1, 0));
		assertEquals(MapRules.NORMAL, MapRules.waterShade(5, 0, 0));
		assertEquals(MapRules.LOW, MapRules.waterShade(10, 0, 0));
		assertEquals(3, MapRules.waterDepth(63.9, 60.2));
		assertEquals(1, MapRules.waterDepth(64.2, 64.1), "at least a block");
		assertEquals(MapRules.UNKNOWN_DEPTH, MapRules.waterDepth(64.0, Double.NaN));
	}

	@Test
	void pixelsStartWhereVanillaPutsThem() {
		assertEquals(0, MapRules.columnStart(0, 1, 64));
		assertEquals(-64, MapRules.columnStart(0, 1, 0));
		assertEquals(-28, MapRules.columnStart(100, 2, 0));
		// Vanilla's integer division rounds toward zero: -100 / 8 is -12.
		assertEquals((-12 - 64) * 8, MapRules.columnStart(-100, 8, 0));
	}

	@Test
	void waterOverTheGroundShowsAsWater() {
		Ground lake = MapRules.ground(60.2, Tone.SAND, 63.9);
		assertNotNull(lake);
		assertEquals(Tone.WATER, lake.tone());
		assertEquals(3, lake.waterDepth());
		assertEquals(63, lake.shadeY());
		Ground bank = MapRules.ground(65.0, Tone.GRASS, 63.9);
		assertNotNull(bank);
		assertEquals(Tone.GRASS, bank.tone(), "ground above the water's surface is dry");
		assertFalse(bank.water());
		Ground open = MapRules.ground(Double.NaN, null, 63.9);
		assertNotNull(open);
		assertEquals(MapRules.UNKNOWN_DEPTH, open.waterDepth());
		assertNull(MapRules.ground(Double.NaN, null, Double.NaN));
		assertEquals(63, new Ground(64.0, Tone.GRASS, 0).shadeY(), "a top at 64 is block 63's");
		assertEquals(64, new Ground(64.3, Tone.GRASS, 0).shadeY());
	}

	@Test
	void aPlacedBlockWinsOverSkyrimsGroundBeneathIt() {
		Ground ground = new Ground(64.3, Tone.GRASS, 0); // block level 64
		assertTrue(MapRules.skyrimShows(MapRules.NO_BLOCK, ground));
		assertFalse(MapRules.skyrimShows(64, ground), "a block standing on the ground");
		assertFalse(MapRules.skyrimShows(70, ground), "a tower");
		assertTrue(MapRules.skyrimShows(63, ground), "a block buried under the ground (in a tunnel) doesn't show");
		assertNull(MapRules.pixel(ground, 64, false, MapRules.NO_BLOCK, false, 1, 0, 0), "vanilla's colour for the block");
		assertEquals(64, MapRules.shownY(ground, MapRules.NO_BLOCK));
		assertEquals(70, MapRules.shownY(ground, 70));
		assertEquals(MapRules.NO_BLOCK, MapRules.shownY(null, MapRules.NO_BLOCK));
	}

	@Test
	void theGroundIsDrawnShadedAgainstWhatShowsNorthOfIt() {
		Ground hill = new Ground(66.5, Tone.GRASS, 0); // block level 66
		Pixel up = MapRules.pixel(hill, MapRules.NO_BLOCK, false, 64, true, 1, 0, 0);
		assertNotNull(up);
		assertEquals(Tone.GRASS, up.tone());
		assertEquals(MapRules.HIGH, up.brightness());
		Pixel down = MapRules.pixel(hill, MapRules.NO_BLOCK, false, 68, false, 1, 0, 0);
		assertNotNull(down);
		assertEquals(MapRules.LOW, down.brightness(), "below a block north of it");
		Pixel alone = MapRules.pixel(hill, MapRules.NO_BLOCK, false, MapRules.NO_BLOCK, false, 1, 0, 0);
		assertNotNull(alone);
		assertEquals(MapRules.NORMAL, alone.brightness(), "nothing known north: level, not the void's drop vanilla would see");
		Pixel buried = MapRules.pixel(hill, 60, false, 66, true, 1, 0, 0);
		assertNotNull(buried);
		assertEquals(Tone.GRASS, buried.tone());
	}

	@Test
	void skyrimsWaterIsDrawnAsWater() {
		Ground lake = MapRules.ground(55.0, Tone.MUD, 63.9);
		Pixel p = MapRules.pixel(lake, MapRules.NO_BLOCK, false, 70, true, 1, 0, 0);
		assertNotNull(p);
		assertEquals(Tone.WATER, p.tone());
		assertEquals(MapRules.waterShade(8, 0, 0), p.brightness());
		// A dock built over the water wins: vanilla's colour, shaded against the water north of it.
		Pixel dock = MapRules.pixel(lake, 64, false, 63, true, 1, 0, 0);
		assertNotNull(dock);
		assertNull(dock.tone());
		assertEquals(MapRules.landShade(64, 63, 1, 0, 0), dock.brightness());
	}

	@Test
	void aBlockNorthOfSkyrimsGroundIsShadedAgainstThatGround() {
		Ground ground = new Ground(64.3, Tone.GRASS, 0);
		// A wall 2 blocks over the ground north of it: vanilla saw the void there and would make it bright whatever.
		Pixel wall = MapRules.pixel(ground, 64, false, 62, true, 1, 0, 0);
		assertNotNull(wall);
		assertNull(wall.tone(), "vanilla's colour");
		assertEquals(MapRules.HIGH, wall.brightness());
		Pixel level = MapRules.pixel(ground, 64, false, 64, true, 1, 0, 0);
		assertNotNull(level);
		assertEquals(MapRules.NORMAL, level.brightness());
		assertNull(MapRules.pixel(ground, 64, false, 64, false, 1, 0, 0), "a block north of a block: vanilla's own shading");
		assertNull(MapRules.pixel(ground, 64, false, 64, true, 2, 0, 0), "zoomed out, vanilla's height is an average: left alone");
		assertNull(MapRules.pixel(ground, 64, true, 64, true, 1, 0, 0), "placed water is shaded by its depth");
	}
}
