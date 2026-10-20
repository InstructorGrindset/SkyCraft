package dev.skycraft.town;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Hearthfire house plots built with Minecraft blocks (hearthfire_blocks), Minecraft side. Skyrim says when the host
 * stands outdoors on a Hearthfire house's land (Lakeview Manor, Windstad Manor, Heljarchen Hall: SkyState hearthPlot);
 * every block placed there then counts for that plot while it stands (PlotTally), and enough of them reach the small
 * house, the main hall and the wings (skycraft-town-rules.txt). The host is told on the action bar and in chat, and
 * Skyrim gets EV_HEARTHFIRE_STAGE and the live count (McState hearthBlocks). Skyrim's own house parts aren't built by
 * this: Hearthfire builds them from its own scripts, which nothing here can drive safely yet. The tally is kept in the
 * world's save folder as skycraft-hearthfire.txt.
 */
public final class SkyHearthfire {
	public static final String FILE = "skycraft-hearthfire.txt";
	/** A guest's block counts if it's placed this close to the host (the host's Skyrim says where the plot is). */
	private static final double GUEST_REACH = 48.0;

	private static @Nullable PlotTally tally;
	private static @Nullable MinecraftServer tallyServer;
	private static boolean dirty;
	private static long lastSave;
	private static volatile int shownPlot;
	private static volatile int shownBlocks;

	private SkyHearthfire() {
	}

	/** McState hearthPlot: the plot whose blocks are counted for Skyrim's player now (0: none). Any thread. */
	public static int shownPlot() {
		return shownPlot;
	}

	/** McState hearthBlocks: the blocks standing on it. Any thread. */
	public static int shownBlocks() {
		return shownBlocks;
	}

	private static boolean on() {
		return Features.on(Features.Feature.HEARTHFIRE_BLOCKS);
	}

	private static boolean onPlot(SkyLink.SkyState sky) {
		return (sky.townFlags & Proto.TOWN_HEARTHFIRE_PLOT) != 0 && sky.hearthPlot != 0;
	}

	private static PlotTally tally(MinecraftServer server) {
		PlotTally t = tally;
		if (t == null || tallyServer != server) {
			t = load(server);
			tally = t;
			tallyServer = server;
		}
		return t;
	}

	private static Path path(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE).normalize();
	}

	private static PlotTally load(MinecraftServer server) {
		Path path = path(server);
		if (!Files.exists(path)) {
			return new PlotTally();
		}
		try {
			List<String> problems = new ArrayList<>();
			PlotTally t = PlotTally.parse(Files.readAllLines(path, StandardCharsets.UTF_8), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-town: {} {}", path, p));
			SkyCraft.LOG.info("SkyCraft-town: {} Hearthfire plot(s) built on in this world", t.plots().size());
			return t;
		} catch (IOException e) {
			SkyCraft.LOG.warn("SkyCraft-town: couldn't read {}: {}", path, e.toString());
			return new PlotTally();
		}
	}

	/** Writes the tally if it changed. */
	static void save(MinecraftServer server) {
		PlotTally t = tally;
		if (t == null || tallyServer != server || !dirty) {
			return;
		}
		dirty = false;
		Path path = path(server);
		Path tmp = path.resolveSibling(FILE + ".tmp");
		try {
			Files.write(tmp, t.lines(), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			SkyCraft.LOG.warn("SkyCraft-town: couldn't write {}: {}", path, e.toString());
		}
	}

	static void stopped() {
		tally = null;
		tallyServer = null;
		dirty = false;
		shownPlot = 0;
		shownBlocks = 0;
	}

	/** A player placed a block (SkyTown spotted it). It counts if the host's Skyrim stands on a plot. */
	static void placed(ServerPlayer player, ServerLevel level, BlockPos pos, SkyLink.SkyState sky, boolean skyOk, TownRules rules) {
		if (!on() || !skyOk || !onPlot(sky)) {
			return;
		}
		MinecraftServer server = level.getServer();
		ServerPlayer host = SkyTown.host(server);
		if (host == null || (player != host && player.distanceToSqr(host) > GUEST_REACH * GUEST_REACH)) {
			return;
		}
		PlotTally t = tally(server);
		String name = sky.town == null || sky.town.isBlank() ? "the Hearthfire plot" : sky.town.strip();
		List<Integer> reached = t.add(sky.hearthPlot, name, PlotTally.key(pos.getX(), pos.getY(), pos.getZ()), rules);
		dirty = true;
		int count = t.count(sky.hearthPlot);
		host.sendOverlayMessage(Component.literal(name + ": " + t.progress(sky.hearthPlot, rules)));
		for (int stage : reached) {
			host.sendSystemMessage(Component.literal(name + ": the " + TownRules.stageName(stage) + " stands, built of " + count + " Minecraft blocks."));
			level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
			SkyLink.pushEvent(Proto.EV_HEARTHFIRE_STAGE, sky.hearthPlot, count, 0.0F, 0.0F, 0.0F, stage);
			SkyCraft.LOG.info("SkyCraft-town: {} ({}) reached the {} with {} blocks", name, Integer.toHexString(sky.hearthPlot), TownRules.stageName(stage), count);
		}
	}

	/** A block was broken: it no longer counts for its plot. */
	static void broken(ServerLevel level, BlockPos pos, TownRules rules) {
		MinecraftServer server = level.getServer();
		if (tally == null && !Files.exists(path(server))) {
			return; // nothing built on any plot yet
		}
		PlotTally t = tally(server);
		int plot = t.remove(PlotTally.key(pos.getX(), pos.getY(), pos.getZ()));
		if (plot == 0) {
			return;
		}
		dirty = true;
		ServerPlayer host = SkyTown.host(server);
		if (host != null && on()) {
			host.sendOverlayMessage(Component.literal(t.name(plot) + ": " + t.progress(plot, rules)));
		}
	}

	/** Every tick: the count Skyrim sees, and the tally saved now and then. */
	static void tick(MinecraftServer server, SkyLink.SkyState sky, boolean skyOk, long now) {
		if (on() && skyOk && onPlot(sky)) {
			shownPlot = sky.hearthPlot;
			shownBlocks = tally(server).count(sky.hearthPlot);
		} else {
			shownPlot = 0;
			shownBlocks = 0;
		}
		if (dirty && now - lastSave >= 200) {
			lastSave = now;
			save(server);
		}
	}

	/** For /skytown. */
	static String describe(MinecraftServer server, SkyLink.SkyState sky, boolean skyOk, TownRules rules) {
		if (!on()) {
			return "Hearthfire: switched off (hearthfire_blocks)";
		}
		PlotTally t = tally(server);
		StringBuilder sb = new StringBuilder();
		if (skyOk && onPlot(sky)) {
			sb.append("Hearthfire: on ").append(sky.town).append(" (").append(Integer.toHexString(sky.hearthPlot)).append("): ")
				.append(t.progress(sky.hearthPlot, rules));
		} else {
			sb.append("Hearthfire: not on a Hearthfire plot");
		}
		for (int plot : t.plots()) {
			if (!skyOk || plot != sky.hearthPlot) {
				sb.append("; ").append(t.name(plot)).append(": ").append(t.progress(plot, rules));
			}
		}
		return sb.toString();
	}
}
