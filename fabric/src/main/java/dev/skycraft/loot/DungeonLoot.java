package dev.skycraft.loot;

import dev.skycraft.SkyCraft;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * The first time the player takes anything from a chest in a Skyrim dungeon, a Minecraft chest loot
 * table is rolled into their inventory too. Which table comes from weighted lines in
 * {@code config/skycraft-dungeons.txt}; opened chests are remembered in
 * {@code config/skycraft-opened-chests.txt} so each chest pays out once.
 */
public final class DungeonLoot {
	public static final String DEFAULT_TEXT = """
		# SkyCraft dungeon chests. The first time you loot a chest in a Skyrim dungeon, one of these
		# Minecraft loot tables is rolled into your inventory as well. Format: <loot table> <weight>
		minecraft:chests/simple_dungeon 6
		minecraft:chests/abandoned_mineshaft 4
		minecraft:chests/ruined_portal 2
		minecraft:chests/stronghold_corridor 1
		minecraft:chests/buried_treasure 1
		""";
	private static final String FILE = "skycraft-dungeons.txt";
	private static final String OPENED_FILE = "skycraft-opened-chests.txt";
	private static final Random RANDOM = new Random();

	public record Table(String id, int weight) {
	}

	private static List<Table> tables;
	private static Set<Integer> opened;

	private DungeonLoot() {
	}

	/** Parses weighted loot table lines; bad lines go to {@code problems}. */
	public static List<Table> parse(String text, List<String> problems) {
		List<Table> out = new ArrayList<>();
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
			String[] parts = line.split("\\s+");
			int weight = 1;
			if (parts.length > 2) {
				problems.add("line " + n + ": expected '<loot table> <weight>'");
				continue;
			}
			if (parts.length == 2) {
				try {
					weight = Integer.parseInt(parts[1]);
				} catch (NumberFormatException e) {
					problems.add("line " + n + ": weight '" + parts[1] + "' is not a number");
					continue;
				}
			}
			if (weight <= 0 || !parts[0].matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
				problems.add("line " + n + ": bad loot table or weight");
				continue;
			}
			out.add(new Table(parts[0], weight));
		}
		return out;
	}

	/** Picks one table by weight, or null if there are none. */
	public static String pick(List<Table> tables, Random random) {
		int total = 0;
		for (Table t : tables) {
			total += t.weight();
		}
		if (total <= 0) {
			return null;
		}
		int r = random.nextInt(total);
		for (Table t : tables) {
			r -= t.weight();
			if (r < 0) {
				return t.id();
			}
		}
		return tables.getLast().id();
	}

	/** Whether this looted item should roll a dungeon chest (and marks the chest as rolled). */
	static boolean shouldRoll(SkyLink.Item it, int place, Set<Integer> opened) {
		if (it.source() != Proto.SRC_CONTAINER || !it.chest() || it.container() == 0 || (place & Proto.PLACE_DUNGEON) == 0) {
			return false;
		}
		return opened.add(it.container());
	}

	/** Called for every item the player loots in Skyrim, before conversion. Server thread. */
	public static void onLooted(ServerPlayer player, SkyLink.Item it, int place) {
		Set<Integer> seen = opened();
		if (!shouldRoll(it, place, seen)) {
			return;
		}
		remember(it.container());
		String table = pick(tables(), RANDOM);
		if (table == null) {
			return;
		}
		var server = player.level().getServer();
		var source = server.createCommandSourceStack().withSuppressedOutput().withLevel(player.level()).withPosition(player.position());
		server.getCommands().performPrefixedCommand(source, "loot give " + player.getScoreboardName() + " loot " + table);
		SkyCraft.LOG.info("SkyCraft-loot: first loot of dungeon chest {} rolled {}", String.format("%08X", it.container()), table);
	}

	private static List<Table> tables() {
		if (tables == null) {
			List<String> problems = new ArrayList<>();
			tables = parse(Config.read(FILE, DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-loot: {} {}", FILE, p));
		}
		return tables;
	}

	private static Set<Integer> opened() {
		if (opened == null) {
			opened = new HashSet<>();
			Path path = FabricLoader.getInstance().getConfigDir().resolve(OPENED_FILE);
			try {
				if (Files.exists(path)) {
					for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
						line = line.strip();
						if (!line.isEmpty()) {
							opened.add(Integer.parseUnsignedInt(line, 16));
						}
					}
				}
			} catch (IOException | NumberFormatException e) {
				SkyCraft.LOG.warn("SkyCraft-loot: couldn't read {}: {}", path, e.toString());
			}
		}
		return opened;
	}

	private static void remember(int container) {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(OPENED_FILE);
		try {
			Files.writeString(path, String.format("%08X%n", container), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			SkyCraft.LOG.warn("SkyCraft-loot: couldn't write {}: {}", path, e.toString());
		}
	}
}
