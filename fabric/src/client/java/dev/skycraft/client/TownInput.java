package dev.skycraft.client;

import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.town.SkyRedstone;
import dev.skycraft.town.SkyTown;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Skyrim's towns and building requests from the input ring (IN_OPEN_DOOR, IN_REDSTONE_PULSE, IN_DWEMER_DOOR), handed
 * to the integrated server, which owns the blocks. The block they name is in the terms of the Skyrim world Skyrim is in
 * now, which is sent along. A guest's client does nothing with them: the doors and switches are in the host's world,
 * and the host's own Skyrim speaks for it. Render thread.
 */
public final class TownInput {
	private TownInput() {
	}

	static void accept(Minecraft minecraft, int type, int code, int a, int b, int c) {
		var server = minecraft.getSingleplayerServer();
		if (minecraft.player == null || server == null || MinecraftView.away()) {
			return; // (in another dimension Skyrim's player is parked, away from its doors and switches)
		}
		int world = SkyLink.skyrimWorld();
		UUID host = minecraft.player.getUUID();
		switch (type) {
			case Proto.IN_OPEN_DOOR -> server.execute(() -> SkyTown.npcOpensDoor(server, host, world, a, b, c));
			case Proto.IN_REDSTONE_PULSE -> server.execute(() -> SkyRedstone.pulse(server, host, world, a, b, c, code));
			case Proto.IN_DWEMER_DOOR -> server.execute(() -> SkyRedstone.door(server, world, a, b, c, code != 0));
			default -> {
			}
		}
	}
}
