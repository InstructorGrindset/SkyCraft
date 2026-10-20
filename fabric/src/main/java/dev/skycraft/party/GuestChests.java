package dev.skycraft.party;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The dungeon chests each guest has had a Minecraft roll from (loot.DungeonLoot, guest parity), as lines of
 * {@code config/skycraft-opened-chests-guests.txt}: the guest's id and the chest's form id in hex. Every guest's
 * Skyrim has its own chests, so the same chest pays out once for each player (the host's are in
 * skycraft-opened-chests.txt). Pure Java.
 */
public final class GuestChests {
	private GuestChests() {
	}

	/** Adds each line's chest to its guest's set in {@code out}; returns how many lines were not one (a line cut short by a crash). */
	public static int parse(List<String> lines, Map<UUID, Set<Integer>> out) {
		int bad = 0;
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			String[] parts = line.split("\\s+");
			if (parts.length != 2) {
				bad++;
				continue;
			}
			try {
				UUID guest = UUID.fromString(parts[0]);
				int chest = Integer.parseUnsignedInt(parts[1], 16);
				out.computeIfAbsent(guest, id -> new HashSet<>()).add(chest);
			} catch (IllegalArgumentException e) {
				bad++; // (NumberFormatException is one too)
			}
		}
		return bad;
	}

	/** The line that records {@code guest} rolling {@code chest}, with its line break. */
	public static String line(UUID guest, int chest) {
		return guest + " " + String.format("%08X", chest) + System.lineSeparator();
	}
}
