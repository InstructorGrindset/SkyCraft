package dev.skycraft.config;

import dev.skycraft.SkyCraft;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * On/off switches for the fork's features, from {@code config/skycraft-features.txt}. Every feature
 * is on unless its line says off. Read once per game launch; restart Minecraft after editing the file.
 * The pause menu's SkyCraft settings screen switches them in game ({@link #set}): that rewrites the
 * file's line and counts at once, except for the switches that {@link #needsRestart}.
 */
public final class Features {
	public enum Feature {
		KILL_DROPS("kill_drops", "Skyrim creatures you kill drop Minecraft items as their Minecraft counterparts would, Looting included (skycraft-loot.txt)"),
		KILL_XP("kill_xp", "Skyrim kills drop Minecraft XP orbs"),
		ITEM_CONVERSION("item_conversion", "loot picked up in Skyrim becomes Minecraft items (skycraft-items.txt)"),
		DUNGEON_CHESTS("dungeon_chests", "first loot of a dungeon chest also rolls a Minecraft chest (skycraft-dungeons.txt)"),
		MERCHANTS("merchants", "right click a Skyrim vendor with an empty hand to trade (skycraft-trades.txt, skycraft-towns.txt)"),
		TIME_SYNC("time_sync", "Minecraft's clock follows Skyrim's"),
		BED_SLEEP("bed_sleep", "a night in a Minecraft bed passes the night in Skyrim"),
		MINECRAFT_VIEW("minecraft_view", "in any dimension but the Skyrim mirror (the Nether, ...) the screen shows Minecraft's own world and Skyrim waits; off: those act like the mirror"),
		DEEP_CREATURES("deep_creatures", "Skyrim creatures spawn in dark tunnels you dug (skycraft-deep.txt)"),
		BREACH_DOORS("breach_doors", "a hidden antechamber under each dungeon entrance; dig in and open its sealed door to enter the dungeon"),
		CLICK_TO_ACTIVATE("click_to_activate", "right click with nothing usable in hand activates Skyrim doors, containers and NPCs like G does"),
		PORTALS("portals", "lit Nether portals bound in pairs with a filled soul gem take you between Skyrim places; a Daedra heart or a tonal key opens one onto the Nether or Tamriel Wilds, an Elder Scroll at the Time-Wound onto Home, and Seamstone anywhere you've been (skycraft-portals.txt in the world)"),
		COMPANIONS("companions", "bones tame a Skyrim dog or wolf and horse food a horse, as in Minecraft; right click a tamed one with an empty hand to make it sit or follow (sneak for a horse), feed it to heal it"),
		NATURAL_SPAWNS("natural_spawns", "animals and monsters spawn the Minecraft way in the Nether, Tamriel Wilds and Home, never in the Skyrim mirror; off: no natural spawning anywhere, as before"),
		GEAR_DROPS("gear_drops", "Skyrim people and creatures you kill sometimes drop the Minecraft version of a weapon or armor piece they wore, like a Minecraft zombie (skycraft-gear-drops.txt)"),
		SKOOMA_ADDICTION("skooma_addiction", "Skooma can make a player addicted: withdrawal without it, cured after days clean; a comedown when a high wears off (skycraft-skooma.txt)"),

		// Combat and stealth
		WALLS_HIDE_YOU("walls_hide_you", "Skyrim NPCs can't see you through Minecraft blocks"),
		SNEAK_ATTACKS("sneak_attacks", "a crouched hit on a Skyrim NPC that hasn't noticed you is a Skyrim sneak attack"),
		POWER_ATTACKS("power_attacks", "a fully charged Minecraft hit on a Skyrim NPC is a Skyrim power attack: it staggers"),
		LIGHT_STEALTH("light_stealth", "how lit you are in Minecraft (torches, dark tunnels) is how lit Skyrim's detection thinks you are"),

		// Skills and progression
		SNEAK_SKILL("sneak_skill", "your Skyrim Sneak level makes Minecraft mobs notice you later"),
		ARCHERY_SKILL("archery_skill", "your Skyrim Archery level makes Minecraft bows hit harder"),
		SMITHING_SKILL("smithing_skill", "your Skyrim Smithing level makes anvil repairs cheaper"),
		ENCHANTING_SKILL("enchanting_skill", "your Skyrim Enchanting level raises the enchanting table's offers; soul gems can stand in for lapis"),
		DRAGON_SOULS("dragon_souls", "every dragon you kill gives Minecraft XP levels"),
		ALCHEMY_BREWING("alchemy_brewing", "your Skyrim Alchemy level makes brewed potions last longer, and Skyrim ingredients brew"),

		// Economy and food
		SELL_TO_MERCHANTS("sell_to_merchants", "Skyrim merchants buy Minecraft goods: gems, ingots and more"),
		SKYRIM_FOOD_HUNGER("skyrim_food_hunger", "eating Skyrim food fills Minecraft's hunger"),
		SKOOMA_BREWING("skooma_brewing", "moon sugar brews into Skooma in a brewing stand"),

		// World
		WEATHER_SYNC("weather_sync", "Skyrim's rain and snow are Minecraft's and the other way round; snow settles on your blocks"),
		SKYRIM_FARMING("skyrim_farming", "Minecraft crops grow on Skyrim's farmland, and Skyrim's crops can be harvested"),
		WILDS_VILLAGERS("wilds_villagers", "villages in Tamriel Wilds have Skyrim-style villagers with Skyrim trades"),
		SHRINE_BLESSINGS("shrine_blessings", "a Skyrim shrine's blessing also gives a Minecraft effect"),

		// Towns
		NPC_DOORS_LADDERS("npc_doors_ladders", "Skyrim NPCs open Minecraft doors and climb Minecraft ladders"),
		TOWN_CRIME("town_crime", "breaking blocks or taking from chests in a Skyrim town is a crime: a bounty, and the guards come"),
		GUARD_COMMENTS("guard_comments", "guards remark on your building near them"),

		// Building
		HEARTHFIRE_BLOCKS("hearthfire_blocks", "a Hearthfire house plot can be built with Minecraft blocks"),
		DWEMER_REDSTONE("dwemer_redstone", "Skyrim levers and pressure plates power redstone, and redstone opens Dwemer doors"),

		// Creatures
		MOBS_FIGHT_NPCS("mobs_fight_npcs", "Minecraft's monsters and Skyrim's NPCs fight each other"),
		WOLVES_FIGHT_SKYRIM("wolves_fight_skyrim", "your tamed Minecraft wolves fight Skyrim enemies"),
		NIGHT_MONSTERS("night_monsters", "at night, Skyrim creatures come out of the dark in the Skyrim world", false, 0),
		HORSES_CARRY_OVER("horses_carry_over", "a tamed Minecraft horse is your Skyrim horse, and the other way round"),
		CREATURES_AND_LIGHT("creatures_and_light", "skeevers and Falmer keep away from torches, and Skyrim's undead burn in sunlight"),

		// Multiplayer
		GUEST_PARITY("guest_parity", "friends in your world get Skyrim drops, skill training and item conversion as you do"),
		SHARED_WORLD_EVENTS("shared_world_events", "dragon attacks and big explosions in your Skyrim are felt by everyone in the world"),

		// Players and testers
		CONVERSION_MESSAGES("conversion_messages", "a message says what each Skyrim item became"),
		GUIDE_BOOK("guide_book", "a new player gets a SkyCraft guide book"),

		// Tooling
		PAIRED_BACKUPS("paired_backups", "loading a save backs up the Skyrim save and the Minecraft world together"),
		FAR_BLOCK_DETAIL("far_block_detail", "far Minecraft blocks are drawn with less detail, for a higher frame rate", false, 0);

		public final String key;
		public final String about;
		/** On unless skycraft-features.txt says otherwise. */
		public final boolean defaultOn;
		/** Its Proto.FEATURE_* bit when Skyrim runs part of it itself (McState.features), else 0. */
		public final int skyrimBit;

		Feature(String key, String about) {
			this(key, about, true, 0);
		}

		Feature(String key, String about, boolean defaultOn, int skyrimBit) {
			this.key = key;
			this.about = about;
			this.defaultOn = defaultOn;
			this.skyrimBit = skyrimBit;
		}
	}

	public static final String FILE = "skycraft-features.txt";
	public static final String DEFAULT_TEXT = defaultText();

	private static volatile Map<Feature, Boolean> switches;

	private Features() {
	}

	/**
	 * Whether this feature is switched on. Any thread: the server asks, and so does the client (click_to_activate).
	 * A switch changed in game ({@link #set}) answers its new value from then on, unless it {@link #needsRestart}.
	 */
	public static boolean on(Feature feature) {
		Map<Feature, Boolean> map = switches;
		if (map == null) {
			map = load();
		}
		return map.getOrDefault(feature, feature.defaultOn);
	}

	/** McState.features: Proto.FEATURES_VALID and the Skyrim bits of every switched on feature. */
	public static int skyrimBits() {
		int bits = dev.skycraft.link.Proto.FEATURES_VALID;
		for (Feature f : Feature.values()) {
			if (f.skyrimBit != 0 && on(f)) {
				bits |= f.skyrimBit;
			}
		}
		return bits;
	}

	private static synchronized Map<Feature, Boolean> load() {
		if (switches == null) {
			List<String> problems = new ArrayList<>();
			Map<Feature, Boolean> map = parse(Config.read(FILE, DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft: {} {}", FILE, p));
			List<String> off = new ArrayList<>();
			map.forEach((f, v) -> {
				if (!v) {
					off.add(f.key);
				}
			});
			SkyCraft.LOG.info("SkyCraft: features {}", off.isEmpty() ? "all on" : "off: " + String.join(", ", off));
			switches = map;
		}
		return switches;
	}

	/** Parses {@code key = on|off} lines. Unknown keys and values go to {@code problems}. */
	public static Map<Feature, Boolean> parse(String text, List<String> problems) {
		Map<Feature, Boolean> out = new EnumMap<>(Feature.class);
		for (Feature f : Feature.values()) {
			out.put(f, f.defaultOn);
		}
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
				problems.add("line " + n + ": expected '<feature> = on' or '<feature> = off'");
				continue;
			}
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).strip().toLowerCase(Locale.ROOT);
			Feature feature = null;
			for (Feature f : Feature.values()) {
				if (f.key.equals(key)) {
					feature = f;
				}
			}
			if (feature == null) {
				problems.add("line " + n + ": unknown feature '" + key + "'");
				continue;
			}
			switch (value) {
				case "on", "true", "yes" -> out.put(feature, true);
				case "off", "false", "no" -> out.put(feature, false);
				default -> problems.add("line " + n + ": '" + value + "' should be on or off");
			}
		}
		return out;
	}

	private static String defaultText() {
		StringBuilder sb = new StringBuilder();
		sb.append("# SkyCraft fork features. Set a line to off to switch that feature off, then restart Minecraft.\n");
		sb.append("# Or switch them in game: SkyCraft settings, in the top left of the pause menu (O).\n");
		for (Feature f : Feature.values()) {
			sb.append("\n# ").append(f.about).append('\n').append(f.key).append(f.defaultOn ? " = on\n" : " = off\n");
		}
		return sb.toString();
	}

	// ---- Switching in game (the pause menu's SkyCraft settings screen) ------------------------------

	/**
	 * Switches read once, whose change waits for Minecraft's next start: SkyDimension keeps minecraft_view for
	 * the whole game (every system asks it which world is Skyrim's, and changing that under a player standing in
	 * the Nether isn't safe), and natural_spawns sets the spawn game rules as the world opens
	 * (SkyCraft.configureServer). A feature that reads its switch only once (at start, or to register something)
	 * belongs here, so the settings screen says so.
	 */
	private static final java.util.Set<Feature> AT_RESTART = java.util.EnumSet.of(Feature.MINECRAFT_VIEW, Feature.NATURAL_SPAWNS);

	/** What skycraft-features.txt says since a change in game: {@link #on}'s switches plus changes waiting for a restart. */
	private static volatile Map<Feature, Boolean> saved;

	/** Whether a change to this switch only takes effect after Minecraft restarts ({@link #on} keeps the old value until then). */
	public static boolean needsRestart(Feature feature) {
		return AT_RESTART.contains(feature);
	}

	/** What skycraft-features.txt says for this feature: {@link #on}'s answer, or a change waiting for a restart. */
	public static boolean saved(Feature feature) {
		Map<Feature, Boolean> map = saved;
		if (map == null) {
			map = switches != null ? switches : load();
		}
		return map.getOrDefault(feature, feature.defaultOn);
	}

	/** Whether this switch was changed in game and the change waits for a restart. */
	public static boolean waitsForRestart(Feature feature) {
		return saved(feature) != on(feature);
	}

	/**
	 * Switches a feature on or off in game (the SkyCraft settings screen) and saves it in skycraft-features.txt, keeping
	 * the file's comments and every other line ({@link #rewrite}). {@link #on} answers the new value at once, unless the
	 * switch {@link #needsRestart}. Render thread; {@link #on} may be asked from any thread meanwhile.
	 */
	public static synchronized void set(Feature feature, boolean value) {
		Map<Feature, Boolean> live = switches != null ? switches : load();
		saved = with(saved != null ? saved : live, feature, value);
		switches = afterSet(live, feature, value);
		java.nio.file.Path path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(FILE);
		try {
			String text = java.nio.file.Files.exists(path) ? java.nio.file.Files.readString(path) : DEFAULT_TEXT;
			java.nio.file.Files.createDirectories(path.getParent());
			java.nio.file.Files.writeString(path, rewrite(text, Map.of(feature, value)));
		} catch (java.io.IOException e) {
			SkyCraft.LOG.warn("SkyCraft: couldn't save {}: {}", path, e.toString());
		}
		SkyCraft.LOG.info("SkyCraft: feature {} switched {} in game{}", feature.key, value ? "on" : "off",
			needsRestart(feature) ? ", from the next start" : "");
	}

	/** {@code live} once {@code feature} is set to {@code value} in game: changed at once, unless it waits for a restart. */
	static Map<Feature, Boolean> afterSet(Map<Feature, Boolean> live, Feature feature, boolean value) {
		return needsRestart(feature) ? live : with(live, feature, value);
	}

	private static Map<Feature, Boolean> with(Map<Feature, Boolean> map, Feature feature, boolean value) {
		Map<Feature, Boolean> copy = new EnumMap<>(Feature.class);
		copy.putAll(map);
		copy.put(feature, value);
		return copy;
	}

	/**
	 * skycraft-features.txt's {@code text} with the switches in {@code values} set: each one's {@code key = on|off}
	 * line gets the new value (every line, if the key is there twice), its spacing and trailing comment kept, and a
	 * line already saying so (yes, true) stays as it is. Comments, blank lines, unknown keys and other switches are
	 * kept as written, with the file's line endings. A switch without a line yet (a feature newer than the file) is
	 * added at the end with its comment, as the defaults write it.
	 */
	public static String rewrite(String text, Map<Feature, Boolean> values) {
		String newline = text.contains("\r\n") ? "\r\n" : "\n";
		String[] lines = text.split("\\R", -1);
		java.util.Set<Feature> written = java.util.EnumSet.noneOf(Feature.class);
		StringBuilder out = new StringBuilder(text.length() + 64);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			Feature feature = lineFeature(line);
			if (feature != null && values.containsKey(feature)) {
				line = withValue(line, values.get(feature));
				written.add(feature);
			}
			out.append(line);
			if (i < lines.length - 1) {
				out.append(newline);
			}
		}
		for (Feature f : Feature.values()) {
			if (values.containsKey(f) && !written.contains(f)) {
				if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
					out.append(newline);
				}
				out.append(newline).append("# ").append(f.about).append(newline).append(f.key).append(values.get(f) ? " = on" : " = off").append(newline);
			}
		}
		return out.toString();
	}

	/** The feature a {@code key = value} line of the file switches, or null (a comment, a blank or unknown line). */
	private static Feature lineFeature(String line) {
		int hash = line.indexOf('#');
		String code = hash >= 0 ? line.substring(0, hash) : line;
		int eq = code.indexOf('=');
		if (eq < 0) {
			return null;
		}
		String key = code.substring(0, eq).strip().toLowerCase(Locale.ROOT);
		for (Feature f : Feature.values()) {
			if (f.key.equals(key)) {
				return f;
			}
		}
		return null;
	}

	/** A switch's line saying {@code on}: its value word replaced, everything else (key, spacing, comment) kept. */
	private static String withValue(String line, boolean on) {
		int hash = line.indexOf('#');
		String code = hash >= 0 ? line.substring(0, hash) : line;
		String comment = hash >= 0 ? line.substring(hash) : "";
		int eq = code.indexOf('=');
		String value = code.substring(eq + 1);
		String word = value.strip().toLowerCase(Locale.ROOT);
		boolean says = on ? word.equals("on") || word.equals("true") || word.equals("yes") : word.equals("off") || word.equals("false") || word.equals("no");
		if (says) {
			return line;
		}
		String wanted = on ? "on" : "off";
		if (word.isEmpty()) {
			return code.substring(0, eq + 1).stripTrailing() + " " + wanted + (comment.isEmpty() ? "" : " " + comment);
		}
		int start = eq + 1 + (value.length() - value.stripLeading().length());
		return code.substring(0, start) + wanted + code.substring(start + value.strip().length()) + comment;
	}
}
