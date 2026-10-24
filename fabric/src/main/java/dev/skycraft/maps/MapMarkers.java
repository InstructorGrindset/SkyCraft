package dev.skycraft.maps;

import static dev.skycraft.link.Proto.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skyrim's own map markers on Minecraft maps (pure, unit tested; SkyMaps puts them on the maps). Skyrim lists the markers
 * near its player in the map table: where, what kind of place, its name, and whether the player has seen or discovered it.
 * A marker Skyrim's own map shows (seen or heard of, or discovered) goes on a Minecraft map of that area as a banner with
 * the place's name, coloured by the kind of place (a gray banner while it's only been heard of, not visited).
 */
public final class MapMarkers {
	private MapMarkers() {
	}

	/** What kind of place a marker is: the colour of its banner. */
	public enum Kind {
		/** Cities, towns, the holds' castles and capitols, Raven Rock: yellow. */
		TOWN,
		/** Settlements, farms, mills, stables, docks, lighthouses, shacks, Orc strongholds: lime. */
		HOMESTEAD,
		/** Caves, ruins, mines, towers, shipwrecks, dragon lairs, Miraak's temple: red. */
		DUNGEON,
		/** Camps and forts (bandits', giants', the Legion's and the Stormcloaks'): orange. */
		CAMP,
		/** Groves, standing stones, doomstones, shrines, altars: purple. */
		SACRED,
		/** Landmarks, clearings, passes, rocks, anything else: white. */
		LANDMARK,
		/** The ships between Skyrim and Solstheim: cyan. */
		TRAVEL,
	}

	/** A marker as Skyrim listed it, at its place in the mirror. */
	public record Marker(int formId, double x, double z, int type, int flags, String name) {
		/** Skyrim's own map shows it: seen or heard of, or discovered. */
		public boolean visible() {
			return (this.flags & (MARKER_VISIBLE | MARKER_DISCOVERED)) != 0;
		}

		/** The player has been there: it's a fast travel point. */
		public boolean discovered() {
			return (this.flags & MARKER_DISCOVERED) != 0;
		}
	}

	/** The kind of place a MapMarkerType is (RE::MARKER_TYPE's numbering). */
	public static Kind kind(int type) {
		return switch (type) {
			case 1, 2, 54 -> Kind.TOWN; // city, town, Raven Rock
			case 3, 13, 14, 19, 20, 21, 27, 28, 30, 32, 33 -> Kind.HOMESTEAD; // settlement, farm, mills, smelter, stable, lighthouse, ...
			case 4, 7, 8, 9, 12, 15, 22, 31, 53, 56, 59 -> Kind.DUNGEON; // cave, ruins, shipwreck, dragon lair, mine, towers, ...
			case 5, 6, 16, 17, 29 -> Kind.CAMP; // camp, fort, Imperial and Stormcloak camps, giant camp
			case 10, 18, 25, 34, 55 -> Kind.SACRED; // grove, doomstone, altar, shrine, standing stone
			case 57, 58 -> Kind.TRAVEL; // the ships to Skyrim and to Solstheim
			default -> type >= 35 && type <= 52 ? Kind.TOWN : Kind.LANDMARK; // the holds' castles and capitols
		};
	}

	/** The decoration id a marker has on a map (a colon, which no player name has, keeps it apart from players'). */
	public static String id(int formId) {
		return String.format("skyrim:%08X", formId);
	}

	/** The name on its banner. */
	public static String label(Marker marker) {
		String name = marker.name() == null ? "" : marker.name().strip();
		return name.isEmpty() ? "?" : name;
	}

	/**
	 * Whether a place is on a map whose centre is {@code centerX, centerZ} at {@code blocksPerPixel}: vanilla places a
	 * decoration only within 63 pixels of the centre each way.
	 */
	public static boolean onMap(double x, double z, int centerX, int centerZ, int blocksPerPixel) {
		double fx = (x - centerX) / blocksPerPixel, fz = (z - centerZ) / blocksPerPixel;
		return fx >= -63.0 && fz >= -63.0 && fx <= 63.0 && fz <= 63.0;
	}

	/** What to do on one map: the markers to put on it (again; putting one on twice changes nothing), the ids to take off. */
	public record Plan(List<Marker> add, List<String> remove) {
	}

	/**
	 * The plan for one map. {@code listed}: the table's markers; {@code centerX, centerZ, reach}: Skyrim listed every
	 * visible marker closer than {@code reach} to there. {@code placed}: the markers this map has from earlier, by id, with
	 * where they are ({x, z}). A marker comes off only when Skyrim would have listed it and didn't (no longer visible, or
	 * gone); one out of Skyrim's reach now stays. {@code switchOn}: skyrim_maps is on (off: every marker comes off).
	 */
	public static Plan plan(List<Marker> listed, double centerX, double centerZ, double reach, Map<String, double[]> placed, int mapCenterX,
		int mapCenterZ, int blocksPerPixel, boolean switchOn) {
		List<Marker> add = new ArrayList<>();
		Set<String> wanted = new HashSet<>();
		if (switchOn) {
			for (Marker m : listed) {
				if (m.visible() && m.type() < MARKER_TYPE_COUNT && onMap(m.x(), m.z(), mapCenterX, mapCenterZ, blocksPerPixel)) {
					add.add(m);
					wanted.add(id(m.formId()));
				}
			}
		}
		List<String> remove = new ArrayList<>();
		for (Map.Entry<String, double[]> e : placed.entrySet()) {
			if (wanted.contains(e.getKey())) {
				continue;
			}
			double dx = e.getValue()[0] - centerX, dz = e.getValue()[1] - centerZ;
			if (!switchOn || dx * dx + dz * dz < reach * reach) {
				remove.add(e.getKey());
			}
		}
		return new Plan(add, remove);
	}
}
