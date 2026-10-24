package dev.skycraft.maps;

import static dev.skycraft.link.Proto.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.maps.MapMarkers.Kind;
import dev.skycraft.maps.MapMarkers.Marker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapMarkersTest {
	private static final int SEEN = MARKER_VISIBLE, BEEN = MARKER_VISIBLE | MARKER_DISCOVERED;

	@Test
	void placesAreSortedIntoKinds() {
		assertEquals(Kind.TOWN, MapMarkers.kind(1)); // city
		assertEquals(Kind.TOWN, MapMarkers.kind(2)); // town
		assertEquals(Kind.TOWN, MapMarkers.kind(39)); // Whiterun's castle
		assertEquals(Kind.TOWN, MapMarkers.kind(52)); // Dawnstar's capitol
		assertEquals(Kind.TOWN, MapMarkers.kind(54)); // Raven Rock
		assertEquals(Kind.HOMESTEAD, MapMarkers.kind(13)); // farm
		assertEquals(Kind.HOMESTEAD, MapMarkers.kind(28)); // Orc stronghold
		assertEquals(Kind.DUNGEON, MapMarkers.kind(4)); // cave
		assertEquals(Kind.DUNGEON, MapMarkers.kind(7)); // Nordic ruin
		assertEquals(Kind.DUNGEON, MapMarkers.kind(8)); // Dwemer ruin
		assertEquals(Kind.DUNGEON, MapMarkers.kind(12)); // dragon lair
		assertEquals(Kind.CAMP, MapMarkers.kind(5));
		assertEquals(Kind.CAMP, MapMarkers.kind(29)); // giant camp
		assertEquals(Kind.SACRED, MapMarkers.kind(55)); // standing stone
		assertEquals(Kind.SACRED, MapMarkers.kind(34)); // shrine
		assertEquals(Kind.TRAVEL, MapMarkers.kind(58)); // the ship to Solstheim
		assertEquals(Kind.LANDMARK, MapMarkers.kind(11));
		assertEquals(Kind.LANDMARK, MapMarkers.kind(0));
	}

	@Test
	void idsAndNames() {
		assertEquals("skyrim:0001A2B3", MapMarkers.id(0x1A2B3));
		assertEquals("skyrim:FE000801", MapMarkers.id(0xFE000801));
		assertEquals("Riverwood", MapMarkers.label(new Marker(1, 0, 0, 2, BEEN, " Riverwood ")));
		assertEquals("?", MapMarkers.label(new Marker(1, 0, 0, 2, BEEN, "")));
	}

	@Test
	void onlyWhatsOnTheMapGoesOnIt() {
		assertTrue(MapMarkers.onMap(63, -63, 0, 0, 1));
		assertFalse(MapMarkers.onMap(63.5, 0, 0, 0, 1));
		assertTrue(MapMarkers.onMap(126, 0, 0, 0, 2));
		assertTrue(MapMarkers.onMap(1000 + 1000, 0, 1000, 0, 16));
		assertFalse(MapMarkers.onMap(1000 + 1020, 0, 1000, 0, 16));
	}

	@Test
	void visiblePlacesGoOnTheMap() {
		List<Marker> listed = List.of(new Marker(0x10, 10, 10, 2, BEEN, "Riverwood"), new Marker(0x11, -20, 30, 4, SEEN, "Bleak Falls Barrow"),
			new Marker(0x12, 5, 5, 7, 0, "Hidden"), // not on Skyrim's map yet
			new Marker(0x13, 500, 5, 1, BEEN, "Whiterun"), // off this map
			new Marker(0x14, 3, 3, 62, BEEN, "Quest target")); // not a place
		MapMarkers.Plan plan = MapMarkers.plan(listed, 0, 0, MAP_MARKER_REACH, Map.of(), 0, 0, 1, true);
		assertEquals(2, plan.add().size());
		assertEquals(0x10, plan.add().get(0).formId());
		assertEquals(0x11, plan.add().get(1).formId());
		assertTrue(plan.add().get(0).discovered());
		assertFalse(plan.add().get(1).discovered(), "heard of, not visited");
		assertTrue(plan.remove().isEmpty());
	}

	@Test
	void aMarkerComesOffOnlyWhenSkyrimWouldHaveListedIt() {
		Map<String, double[]> placed = new HashMap<>();
		placed.put(MapMarkers.id(0x20), new double[] { 10, 10 }); // near: Skyrim lists every visible marker here
		placed.put(MapMarkers.id(0x21), new double[] { 3000, 10 }); // beyond Skyrim's reach now: stays
		placed.put(MapMarkers.id(0x22), new double[] { 20, 20 }); // still listed
		List<Marker> listed = List.of(new Marker(0x22, 20, 20, 2, BEEN, "Riverwood"));
		MapMarkers.Plan plan = MapMarkers.plan(listed, 0, 0, 1024, placed, 0, 0, 1, true);
		assertEquals(List.of(MapMarkers.id(0x20)), plan.remove());
		assertEquals(1, plan.add().size(), "putting one on again changes nothing in vanilla");
		// Fewer listed than were near (more than 48 within 1024): only those within the reach Skyrim covered come off.
		MapMarkers.Plan narrow = MapMarkers.plan(listed, 0, 0, 5, placed, 0, 0, 1, true);
		assertTrue(narrow.remove().isEmpty());
	}

	@Test
	void switchedOffEveryMarkerComesOff() {
		Map<String, double[]> placed = new HashMap<>();
		placed.put(MapMarkers.id(0x20), new double[] { 10, 10 });
		placed.put(MapMarkers.id(0x21), new double[] { 3000, 10 });
		MapMarkers.Plan plan = MapMarkers.plan(List.of(new Marker(0x20, 10, 10, 2, BEEN, "Riverwood")), 0, 0, 1024, placed, 0, 0, 1, false);
		assertTrue(plan.add().isEmpty());
		assertEquals(2, plan.remove().size());
	}
}
