package dev.skycraft.blessings;

import static dev.skycraft.link.Proto.BLESSING_AKATOSH;
import static dev.skycraft.link.Proto.BLESSING_ARKAY;
import static dev.skycraft.link.Proto.BLESSING_AURIEL;
import static dev.skycraft.link.Proto.BLESSING_DIBELLA;
import static dev.skycraft.link.Proto.BLESSING_JULIANOS;
import static dev.skycraft.link.Proto.BLESSING_KYNARETH;
import static dev.skycraft.link.Proto.BLESSING_MARA;
import static dev.skycraft.link.Proto.BLESSING_OTHER;
import static dev.skycraft.link.Proto.BLESSING_STENDARR;
import static dev.skycraft.link.Proto.BLESSING_TALOS;
import static dev.skycraft.link.Proto.BLESSING_ZENITHAR;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Shrine blessings (switch {@code shrine_blessings}): what a Skyrim shrine's blessing gives in Minecraft, and for how
 * long. Pure Java and unit tested; SkyBlessings gives it. Skyrim tells which god's blessing the player received
 * (Proto.IN_BLESSING, from the spell's name) and how many of its 8 game hours are left; at Skyrim's timescale (20 by
 * default: a game hour is three real minutes) that is 24 real minutes, and the Minecraft effect lasts as long.
 *
 * <p>Each god's effect echoes the Skyrim blessing: Akatosh's faster magicka (Haste), Arkay's health (Health Boost),
 * Dibella's speech (Luck), Julianos's magicka (Haste), Kynareth's stamina (Speed), Mara's restoration (Regeneration),
 * Stendarr's block and Talos's shouts (Resistance), Zenithar's prices (Hero of the Village), Auriel's light (Night
 * Vision, Dawnguard), any other shrine's (Luck).
 */
public final class Blessings {
	/** How long a shrine's blessing lasts in Skyrim, in game hours. */
	public static final double HOURS = 8.0;
	/** Skyrim's default timescale (game seconds per real second), when Skyrim sends none. */
	public static final double DEFAULT_TIMESCALE = 20.0;

	/** A blessing's Minecraft side: who gave it, the effect (a 26.3 effect id) and its amplifier (0 is level I). */
	public record Gift(String god, String effect, int amplifier) {
	}

	/** By Proto.BLESSING_* kind. */
	public static final Map<Integer, Gift> GIFTS = Map.ofEntries(
		Map.entry(BLESSING_AKATOSH, new Gift("Akatosh", "haste", 0)),
		Map.entry(BLESSING_ARKAY, new Gift("Arkay", "health_boost", 0)),
		Map.entry(BLESSING_DIBELLA, new Gift("Dibella", "luck", 0)),
		Map.entry(BLESSING_JULIANOS, new Gift("Julianos", "haste", 0)),
		Map.entry(BLESSING_KYNARETH, new Gift("Kynareth", "speed", 0)),
		Map.entry(BLESSING_MARA, new Gift("Mara", "regeneration", 0)),
		Map.entry(BLESSING_STENDARR, new Gift("Stendarr", "resistance", 0)),
		Map.entry(BLESSING_TALOS, new Gift("Talos", "resistance", 0)),
		Map.entry(BLESSING_ZENITHAR, new Gift("Zenithar", "hero_of_the_village", 0)),
		Map.entry(BLESSING_AURIEL, new Gift("Auriel", "night_vision", 0)),
		Map.entry(BLESSING_OTHER, new Gift("the shrine", "luck", 0)));

	private Blessings() {
	}

	/** The gift for a Proto.BLESSING_* kind, null for none (or a kind this version doesn't know). */
	public static @Nullable Gift gift(int kind) {
		return GIFTS.get(kind);
	}

	/**
	 * Minecraft ticks (20 a second) that {@code gameHours} of Skyrim's game time last at its {@code timescale}: one game
	 * hour is 3600 / timescale real seconds. A timescale that isn't positive is taken as the default.
	 */
	public static int ticks(double gameHours, double timescale) {
		double scale = timescale > 0.0 ? timescale : DEFAULT_TIMESCALE;
		double seconds = Math.max(0.0, gameHours) * 3600.0 / scale;
		return (int) Math.min(Integer.MAX_VALUE, Math.round(seconds * 20.0));
	}

	/** The effect's ticks for a kInBlessing's a and b (game hours left and timescale, both x 1000). */
	public static int ticksFromInput(int hoursThousandths, int timescaleThousandths) {
		return ticks(hoursThousandths / 1000.0, timescaleThousandths / 1000.0);
	}
}
