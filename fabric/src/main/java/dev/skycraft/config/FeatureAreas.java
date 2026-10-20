package dev.skycraft.config;

import dev.skycraft.config.Features.Feature;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The SkyCraft settings screen's sections: every feature switch under the part of the game it belongs to, in the
 * order a player reads them, and each switch's name as the screen shows it. A feature not listed here yet (one added
 * since) is shown under {@link #MORE} at the end; FeatureAreasTest says when one is missing.
 */
public final class FeatureAreas {
	/** One section of the screen: a header and its switches. */
	public record Area(String name, List<Feature> features) {
	}

	/** The section for features no area lists. */
	public static final String MORE = "More";

	private static final List<Area> AREAS = List.of(
		new Area("Loot and items", List.of(Feature.ITEM_CONVERSION, Feature.KILL_DROPS, Feature.KILL_XP, Feature.GEAR_DROPS)),
		new Area("Dungeons and the deep", List.of(Feature.DUNGEON_CHESTS, Feature.BREACH_DOORS, Feature.DEEP_CREATURES)),
		new Area("Merchants, food and potions", List.of(Feature.MERCHANTS, Feature.SELL_TO_MERCHANTS, Feature.SKYRIM_FOOD_HUNGER,
			Feature.SKOOMA_BREWING, Feature.SKOOMA_ADDICTION)),
		new Area("Combat and stealth", List.of(Feature.WALLS_HIDE_YOU, Feature.SNEAK_ATTACKS, Feature.POWER_ATTACKS, Feature.LIGHT_STEALTH)),
		new Area("Skills and progression", List.of(Feature.SNEAK_SKILL, Feature.ARCHERY_SKILL, Feature.SMITHING_SKILL, Feature.ENCHANTING_SKILL,
			Feature.DRAGON_SOULS, Feature.ALCHEMY_BREWING)),
		new Area("Time, weather and land", List.of(Feature.TIME_SYNC, Feature.BED_SLEEP, Feature.WEATHER_SYNC, Feature.SKYRIM_FARMING,
			Feature.SHRINE_BLESSINGS)),
		new Area("Worlds and portals", List.of(Feature.MINECRAFT_VIEW, Feature.PORTALS, Feature.NATURAL_SPAWNS, Feature.WILDS_VILLAGERS)),
		new Area("Towns and building", List.of(Feature.NPC_DOORS_LADDERS, Feature.TOWN_CRIME, Feature.GUARD_COMMENTS, Feature.HEARTHFIRE_BLOCKS,
			Feature.DWEMER_REDSTONE)),
		new Area("Creatures and companions", List.of(Feature.COMPANIONS, Feature.MOBS_FIGHT_NPCS, Feature.WOLVES_FIGHT_SKYRIM,
			Feature.NIGHT_MONSTERS, Feature.HORSES_CARRY_OVER, Feature.CREATURES_AND_LIGHT)),
		new Area("Controls and messages", List.of(Feature.CLICK_TO_ACTIVATE, Feature.CONVERSION_MESSAGES, Feature.GUIDE_BOOK)),
		new Area("Multiplayer", List.of(Feature.GUEST_PARITY, Feature.SHARED_WORLD_EVENTS)),
		new Area("Saves and performance", List.of(Feature.PAIRED_BACKUPS, Feature.FAR_BLOCK_DETAIL))
	);

	/** Words a switch's name spells their own way: names and initials. */
	private static final Map<String, String> WORDS = Map.of("npc", "NPC", "npcs", "NPCs", "xp", "XP", "skyrim", "Skyrim", "minecraft", "Minecraft",
		"skooma", "Skooma", "dwemer", "Dwemer", "hearthfire", "Hearthfire", "wilds", "Wilds");
	/** Names a key can't spell well. */
	private static final Map<Feature, String> NAMES = Map.of(Feature.NPC_DOORS_LADDERS, "NPC doors and ladders");

	private FeatureAreas() {
	}

	/** Every section in order, each feature once: the listed areas, then {@link #MORE} for any feature none lists. */
	public static List<Area> areas() {
		List<Area> out = new ArrayList<>(AREAS);
		Set<Feature> listed = EnumSet.noneOf(Feature.class);
		AREAS.forEach(a -> listed.addAll(a.features()));
		List<Feature> more = new ArrayList<>();
		for (Feature f : Feature.values()) {
			if (!listed.contains(f)) {
				more.add(f);
			}
		}
		if (!more.isEmpty()) {
			out.add(new Area(MORE, List.copyOf(more)));
		}
		return out;
	}

	/** A switch's name for players, from its key: {@code kill_xp} is "Kill XP", {@code wolves_fight_skyrim} "Wolves fight Skyrim". */
	public static String title(Feature feature) {
		String named = NAMES.get(feature);
		if (named != null) {
			return named;
		}
		StringBuilder sb = new StringBuilder();
		for (String word : feature.key.toLowerCase(Locale.ROOT).split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			if (WORDS.containsKey(word)) {
				sb.append(WORDS.get(word));
			} else if (sb.isEmpty()) {
				sb.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
			} else {
				sb.append(word);
			}
		}
		return sb.toString();
	}

	/** A feature's {@code about} text as a sentence for the screen's tooltip: capitalized, ending in a full stop. */
	public static String description(Feature feature) {
		String about = feature.about.strip();
		if (about.isEmpty()) {
			return "";
		}
		String s = Character.toUpperCase(about.charAt(0)) + about.substring(1);
		return s.endsWith(".") ? s : s + ".";
	}
}
