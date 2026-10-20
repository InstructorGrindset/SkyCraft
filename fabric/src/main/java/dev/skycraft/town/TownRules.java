package dev.skycraft.town;

import dev.skycraft.link.Proto;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The numbers for towns and building, from {@code config/skycraft-town-rules.txt}: how soon a door a Skyrim NPC opened
 * closes again, what griefing a town is worth, and how many blocks each Hearthfire stage takes. Also the rules that
 * decide what counts as griefing. Pure Java (no Minecraft classes), so it's unit tested without Minecraft.
 */
public final class TownRules {
	public static final String FILE = "skycraft-town-rules.txt";
	public static final int TICKS_PER_SECOND = 20;

	/** Shipped defaults, written to the config folder on first run. */
	public static final String DEFAULT_TEXT = """
		# SkyCraft towns and building. Restart Minecraft after editing.
		#
		# Doors (npc_doors_ladders): a Minecraft door a Skyrim NPC opened closes again once nobody has been in its
		# doorway for this many seconds.
		door_close_after = 3
		#
		# Crime (town_crime): what griefing a Skyrim town is worth, in gold. Skyrim takes it as a theft of that value:
		# if someone sees it, they report it and the hold's bounty grows by Skyrim's own rules.
		# Skyrim's own ground (its land and roads), a block's worth dug out, or a block of it that digging revealed broken:
		crime_ground = 6
		# A Skyrim building, wall, rock or other object, a block's worth dug into:
		crime_object = 20
		# A Minecraft block someone else placed in the town, broken:
		crime_block = 4
		# Griefing within this many seconds of the first is one crime (one alarm, the values added up).
		crime_batch = 3
		#
		# Hearthfire plots (hearthfire_blocks): Minecraft blocks standing on a house's land for each stage.
		small_house = 80
		main_hall = 320
		wings = 720
		""";

	private final double doorCloseSeconds;
	private final int crimeGround;
	private final int crimeObject;
	private final int crimeBlock;
	private final double crimeBatchSeconds;
	private final int[] stages; // blocks for STAGE_SMALL_HOUSE .. STAGE_WINGS

	private TownRules(double doorCloseSeconds, int crimeGround, int crimeObject, int crimeBlock, double crimeBatchSeconds, int[] stages) {
		this.doorCloseSeconds = doorCloseSeconds;
		this.crimeGround = crimeGround;
		this.crimeObject = crimeObject;
		this.crimeBlock = crimeBlock;
		this.crimeBatchSeconds = crimeBatchSeconds;
		this.stages = stages;
	}

	/** Ticks a door an NPC opened stays open after its doorway was last occupied. */
	public int doorCloseTicks() {
		return (int) Math.round(this.doorCloseSeconds * TICKS_PER_SECOND);
	}

	/** Gold a crime of this Proto.CRIME_* kind is worth per block (0 for a kind this doesn't know). */
	public int crimeValue(int kind) {
		return switch (kind) {
			case Proto.CRIME_GROUND -> this.crimeGround;
			case Proto.CRIME_OBJECT -> this.crimeObject;
			case Proto.CRIME_BLOCK -> this.crimeBlock;
			default -> 0;
		};
	}

	/** Ticks over which griefing adds up into one crime. */
	public int crimeBatchTicks() {
		return Math.max(1, (int) Math.round(this.crimeBatchSeconds * TICKS_PER_SECOND));
	}

	/** Blocks standing on a plot that a Proto.STAGE_* stage takes (Integer.MAX_VALUE for an unknown stage). */
	public int stageBlocks(int stage) {
		return stage >= 1 && stage <= this.stages.length ? this.stages[stage - 1] : Integer.MAX_VALUE;
	}

	/** The last stage. */
	public static int lastStage() {
		return Proto.STAGE_WINGS;
	}

	/** How a stage is named to the player. */
	public static String stageName(int stage) {
		return switch (stage) {
			case Proto.STAGE_SMALL_HOUSE -> "small house";
			case Proto.STAGE_MAIN_HALL -> "main hall";
			case Proto.STAGE_WINGS -> "wings";
			default -> "house";
		};
	}

	/** Parses settings text. Bad lines are skipped (the default stays) and reported to {@code problems}. */
	public static TownRules parse(String text, List<String> problems) {
		double doorClose = 3.0;
		int ground = 6, object = 20, block = 4;
		double batch = 3.0;
		int[] stages = { 80, 320, 720 };
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			int hash = line.indexOf('#');
			if (hash >= 0) {
				line = line.substring(0, hash).strip();
			}
			if (line.isEmpty()) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				problems.add("line " + n + ": expected 'key = value'");
				continue;
			}
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).strip();
			try {
				switch (key) {
					case "door_close_after" -> doorClose = atLeast(Double.parseDouble(value), 0.5, n, key, problems, doorClose);
					case "crime_ground" -> ground = (int) atLeast(Integer.parseInt(value), 0, n, key, problems, ground);
					case "crime_object" -> object = (int) atLeast(Integer.parseInt(value), 0, n, key, problems, object);
					case "crime_block" -> block = (int) atLeast(Integer.parseInt(value), 0, n, key, problems, block);
					case "crime_batch" -> batch = atLeast(Double.parseDouble(value), 0.05, n, key, problems, batch);
					case "small_house" -> stages[0] = (int) atLeast(Integer.parseInt(value), 1, n, key, problems, stages[0]);
					case "main_hall" -> stages[1] = (int) atLeast(Integer.parseInt(value), 1, n, key, problems, stages[1]);
					case "wings" -> stages[2] = (int) atLeast(Integer.parseInt(value), 1, n, key, problems, stages[2]);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": '" + value + "' isn't a number");
			}
		}
		// Each stage takes more than the one before it.
		for (int i = 1; i < stages.length; i++) {
			if (stages[i] <= stages[i - 1]) {
				problems.add("the " + stageName(i + 1) + " needs more blocks than the " + stageName(i) + "; it takes " + (stages[i - 1] + 1));
				stages[i] = stages[i - 1] + 1;
			}
		}
		return new TownRules(doorClose, ground, object, block, batch, stages);
	}

	private static double atLeast(double v, double min, int line, String key, List<String> problems, double fallback) {
		if (v < min || Double.isNaN(v)) {
			problems.add("line " + line + ": " + key + " must be at least " + min);
			return fallback;
		}
		return v;
	}

	/** The defaults. */
	public static TownRules defaults() {
		return parse(DEFAULT_TEXT, new java.util.ArrayList<>());
	}

	// ---- what counts as griefing a town ---------------------------------------------------------

	/**
	 * The Proto.CRIME_* that breaking a Minecraft block in a town is, or 0 for none: a block someone else placed there
	 * (whoever placed it is known) is CRIME_BLOCK; one nobody is known to have placed that stands in a cell dug out of
	 * Skyrim's world is Skyrim's own ground, revealed by digging (CRIME_GROUND). The breaker's own blocks, and blocks
	 * placed before anyone kept track, are never a crime.
	 */
	public static int brokenBlockCrime(String breaker, @Nullable String placedBy, boolean inDugCell) {
		if (placedBy != null) {
			return placedBy.equals(breaker) ? 0 : Proto.CRIME_BLOCK;
		}
		return inDugCell ? Proto.CRIME_GROUND : 0;
	}

	/**
	 * The Proto.CRIME_* that digging Skyrim's geometry out of a cell in a town is: an object (a building, a wall, a rock,
	 * a tree) if any of what's dug there isn't Skyrim's land, else its ground.
	 */
	public static int digCrime(boolean anyObject) {
		return anyObject ? Proto.CRIME_OBJECT : Proto.CRIME_GROUND;
	}
}
