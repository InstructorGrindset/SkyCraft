package dev.skycraft.maps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MapProtocolTest {
	private static String header() throws IOException {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path candidate = dir.resolve("protocol/skycraft_protocol.h");
			if (Files.exists(candidate)) {
				return Files.readString(candidate);
			}
		}
		throw new AssertionError("protocol/skycraft_protocol.h not found above " + Path.of("").toAbsolutePath());
	}

	private static String hex(long v) {
		return "0x" + Long.toHexString(v).toUpperCase();
	}

	/** Proto.java and protocol/skycraft_protocol.h agree on everything Skyrim maps sends. */
	@Test
	void matchesTheProtocolHeader() throws IOException {
		String text = header();
		String[] expected = {
			"kOffMapTable = 0x" + Long.toHexString(Proto.OFF_MAP_TABLE) + ";",
			"kMaxMapMarkers = " + Proto.MAX_MAP_MARKERS,
			"kMapSampleSide = " + Proto.MAP_SAMPLE_SIDE,
			"kMapMarkerReach = " + (int) Proto.MAP_MARKER_REACH + ".0f",
			"kSampleUnknown = " + Proto.SAMPLE_UNKNOWN,
			"kFeatSkyrimMaps = 1u << " + Integer.numberOfTrailingZeros(Proto.FEATURE_SKYRIM_MAPS),
			"kEvMapSample = " + Proto.EV_MAP_SAMPLE,
			"kMarkerVisible = 1u << " + Integer.numberOfTrailingZeros(Proto.MARKER_VISIBLE),
			"kMarkerDiscovered = 1u << " + Integer.numberOfTrailingZeros(Proto.MARKER_DISCOVERED),
			"kMarkerTypeCount = " + Proto.MARKER_TYPE_COUNT,
			"sizeof(MapMarkerRecord) == " + Proto.MAP_MARKER_BYTES,
			"offsetof(MapMarkerRecord, formId) == " + Proto.MM_FORM_ID,
			"offsetof(MapMarkerRecord, type) == " + Proto.MM_TYPE,
			"offsetof(MapMarkerRecord, flags) == " + Proto.MM_FLAGS,
			"offsetof(MapMarkerRecord, name) == " + Proto.MM_NAME,
			"char          name[" + Proto.MM_NAME_BYTES + "]",
			"sizeof(MapSample) == " + Proto.MAP_SAMPLE_BYTES,
			"sizeof(MapTable) == " + hex(Proto.MAP_TABLE_BYTES),
			"offsetof(MapTable, markers) == " + hex(Proto.MAPT_MARKERS),
			"offsetof(MapTable, sampleRequest) == " + hex(Proto.MAPT_SAMPLE_REQUEST),
			"offsetof(MapTable, sampleSpacing) == " + hex(Proto.MAPT_SAMPLE_SPACING),
			"offsetof(MapTable, samples) == " + hex(Proto.MAPT_SAMPLES),
		};
		for (String e : expected) {
			assertTrue(text.contains(e), "the header should say: " + e);
		}
	}

	@Test
	void theTablesFieldsFollowEachOther() {
		// The table's own layout, as the header's static_asserts have it.
		assertEquals(Proto.MAPT_MARKERS + Proto.MAX_MAP_MARKERS * Proto.MAP_MARKER_BYTES, Proto.MAPT_SAMPLE_REQUEST);
		assertEquals(Proto.MAPT_SAMPLE_REQUEST + 0x20, Proto.MAPT_SAMPLES);
		assertEquals(Proto.MAPT_SAMPLES + (long) Proto.MAP_SAMPLE_SIDE * Proto.MAP_SAMPLE_SIDE * Proto.MAP_SAMPLE_BYTES, Proto.MAP_TABLE_BYTES);
		assertEquals(Proto.MM_NAME + Proto.MM_NAME_BYTES, Proto.MAP_MARKER_BYTES);
	}

	@Test
	void theMapsNumbersAreTheirsAlone() {
		// The table at 0x16100, at most 0xF00 bytes; FeatureBits 7; McEventType 39 (40 set aside).
		assertEquals(0x16100L, Proto.OFF_MAP_TABLE);
		assertTrue(Proto.OFF_MAP_TABLE + Proto.MAP_TABLE_BYTES <= 0x17000L && Proto.MAP_TABLE_BYTES <= 0xF00);
		assertTrue(Proto.OFF_MAP_TABLE >= Proto.OFF_ACTOR_TABLE + Proto.AT_RECORDS + Proto.MAX_ACTORS * Proto.ACTOR_RECORD_BYTES);
		assertTrue(Proto.OFF_MAP_TABLE + Proto.MAP_TABLE_BYTES <= Proto.OFF_EVENT_RING);
		assertEquals(1 << 7, Proto.FEATURE_SKYRIM_MAPS);
		assertEquals(39, Proto.EV_MAP_SAMPLE);
		assertEquals(dev.skycraft.config.Features.Feature.SKYRIM_MAPS.skyrimBit, Proto.FEATURE_SKYRIM_MAPS);
	}

	/** The marker kinds read the header's numbering (RE::MARKER_TYPE's). */
	@Test
	void markerKindsFollowTheHeadersTypes() throws IOException {
		Map<String, Integer> types = new HashMap<>();
		Matcher m = Pattern.compile("kMarker([A-Za-z]+) = (\\d+)").matcher(header());
		while (m.find()) {
			types.put(m.group(1), Integer.parseInt(m.group(2)));
		}
		Map<String, MapMarkers.Kind> expected = Map.ofEntries(Map.entry("City", MapMarkers.Kind.TOWN), Map.entry("Town", MapMarkers.Kind.TOWN),
			Map.entry("Settlement", MapMarkers.Kind.HOMESTEAD), Map.entry("Cave", MapMarkers.Kind.DUNGEON), Map.entry("Camp", MapMarkers.Kind.CAMP),
			Map.entry("Fort", MapMarkers.Kind.CAMP), Map.entry("NordicRuin", MapMarkers.Kind.DUNGEON), Map.entry("DwemerRuin", MapMarkers.Kind.DUNGEON),
			Map.entry("Shipwreck", MapMarkers.Kind.DUNGEON), Map.entry("Grove", MapMarkers.Kind.SACRED), Map.entry("Landmark", MapMarkers.Kind.LANDMARK),
			Map.entry("DragonLair", MapMarkers.Kind.DUNGEON), Map.entry("Farm", MapMarkers.Kind.HOMESTEAD), Map.entry("Mine", MapMarkers.Kind.DUNGEON),
			Map.entry("ImperialCamp", MapMarkers.Kind.CAMP), Map.entry("StormcloakCamp", MapMarkers.Kind.CAMP), Map.entry("Doomstone", MapMarkers.Kind.SACRED),
			Map.entry("Stable", MapMarkers.Kind.HOMESTEAD), Map.entry("Pass", MapMarkers.Kind.LANDMARK), Map.entry("Altar", MapMarkers.Kind.SACRED),
			Map.entry("OrcStronghold", MapMarkers.Kind.HOMESTEAD), Map.entry("GiantCamp", MapMarkers.Kind.CAMP), Map.entry("Shrine", MapMarkers.Kind.SACRED),
			Map.entry("FirstCastle", MapMarkers.Kind.TOWN), Map.entry("LastCastle", MapMarkers.Kind.TOWN), Map.entry("MiraakTemple", MapMarkers.Kind.DUNGEON),
			Map.entry("RavenRock", MapMarkers.Kind.TOWN), Map.entry("StandingStone", MapMarkers.Kind.SACRED), Map.entry("ToSkyrim", MapMarkers.Kind.TRAVEL),
			Map.entry("ToSolstheim", MapMarkers.Kind.TRAVEL), Map.entry("CastleKarstaag", MapMarkers.Kind.DUNGEON));
		for (Map.Entry<String, MapMarkers.Kind> e : expected.entrySet()) {
			Integer type = types.get(e.getKey());
			assertNotNull(type, "the header should name kMarker" + e.getKey());
			assertEquals(e.getValue(), MapMarkers.kind(type), "kMarker" + e.getKey() + " = " + type);
		}
		assertEquals(Proto.MARKER_TYPE_COUNT, (int) types.get("TypeCount"));
	}
}
