package dev.skycraft.client;

import dev.skycraft.SkyDimension;
import dev.skycraft.config.Features;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import dev.skycraft.quests.QuestCodec;
import dev.skycraft.quests.QuestTable;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Quest markers for a guest in a friend's world: this client's own Skyrim's quest targets (its link's quest table, moved into
 * the mirror's terms with the host's spaces) go to the host's server twice a second when they change, and every few seconds
 * anyway (SkyNet.QuestTargets); the server puts them on this player's locator bar (quests.SkyQuests). Both the host's and
 * this player's quest_markers switch must be on. On the host's own machine the server reads its link itself, so this does
 * nothing there.
 */
final class QuestClient {
	private static final int EVERY_TICKS = 10;
	/** The server forgets targets not heard of for 10 seconds: the same ones are sent again before that. */
	private static final int RESEND_TICKS = 100;

	private static final List<QuestTable.Target> TARGETS = new ArrayList<>();
	private static @Nullable List<Integer> last;
	private static int ticks, sentAt;

	private QuestClient() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(QuestClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		if (minecraft.player == null || minecraft.getSingleplayerServer() != null || !ClientPlayNetworking.canSend(SkyNet.QuestTargets.TYPE)) {
			last = null; // a new server hears everything again
			return;
		}
		if (ticks % EVERY_TICKS != 0) {
			return;
		}
		List<QuestTable.Target> targets = read(minecraft);
		if (targets == null) {
			return; // a torn read, or the host hasn't said where this Skyrim world's space is yet: next time
		}
		List<Integer> data = QuestCodec.encode(targets);
		if (!data.equals(last) || (!targets.isEmpty() && ticks - sentAt >= RESEND_TICKS)) {
			ClientPlayNetworking.send(new SkyNet.QuestTargets(data));
			last = data;
			sentAt = ticks;
		}
	}

	/** This Skyrim's targets in the mirror's terms (none while they can't be shown), or null for "ask again next time". */
	private static @Nullable List<QuestTable.Target> read(Minecraft minecraft) {
		if (!Features.on(Features.Feature.QUEST_MARKERS) || !SkyClient.linked() || MinecraftView.away() || !SkyDimension.inMirror(minecraft.level)) {
			return List.of();
		}
		SkyLink.SkyState sky = SkyClient.sky();
		if (!sky.inGame() || sky.loading()) {
			return List.of();
		}
		int world = SkyLink.readQuestTargets(TARGETS);
		if (world < 0) {
			return null;
		}
		return world != 0 && world == sky.worldId ? TARGETS : List.of();
	}
}
