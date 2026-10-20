package dev.skycraft.backup;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Paired backups (switch paired_backups): each time Skyrim loads a save with Minecraft connected, Skyrim copies the save
 * into its SkyCraft Backups folder and sends the save's name and a stamp (Proto.IN_PAIRED_BACKUP); this backs the world
 * the integrated server runs up under the same stamp, as {@code skycraft-backups/<stamp> <world>.zip} in the game
 * folder, keeps the newest pairs (skycraft-backups.txt) and tells Skyrim (Proto.EV_PAIRED_BACKUP), which then deletes its
 * own older copies. The world is saved to disk first (players and chunks, as /save-all flush does) and its autosaves are
 * held off while the zip is written on a thread of its own, so the game never waits on the copy. A request that arrives
 * while no world of this player's own is open (the title screen, or a guest in a friend's world) waits for the next one
 * to open. Log tag: SkyCraft-backup.
 */
public final class SkyBackups {
	public static final String FILE = "skycraft-backups.txt";
	/** The folder in Minecraft's game folder the world zips go in. */
	public static final String FOLDER = "skycraft-backups";

	private static final BackupRequest ASSEMBLER = new BackupRequest();
	private static BackupRequest.@Nullable Request pending;
	private static volatile BackupRules.@Nullable Settings settings;
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SkyCraft backups");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});
	// Server thread only: backups being written, and the levels whose autosave they turned off.
	private static int writing;
	private static final List<ServerLevel> HELD = new ArrayList<>();

	private SkyBackups() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			writing = 0;
			HELD.clear();
			BackupRequest.Request waiting;
			synchronized (SkyBackups.class) {
				waiting = pending;
				pending = null;
			}
			if (waiting != null) {
				SkyCraft.LOG.info("SkyCraft-backup: a world is open now: backing it up for Skyrim's {}", waiting.saveName());
				start(server, waiting);
			}
		});
	}

	/**
	 * Client render thread, from the input ring: one Proto.IN_PAIRED_BACKUP event. Once a whole request is in, the backup
	 * starts on {@code server} (the integrated server, null when none runs), or waits for one.
	 */
	public static void onInput(int code, int a, int b, int c, @Nullable MinecraftServer server) {
		BackupRequest.Request request;
		synchronized (SkyBackups.class) {
			request = ASSEMBLER.accept(code, a, b, c);
		}
		if (request == null) {
			return;
		}
		if (!Features.on(Features.Feature.PAIRED_BACKUPS)) {
			return; // (Skyrim only asks when the switch is on; an old request from before a restart)
		}
		if (server == null) {
			synchronized (SkyBackups.class) {
				pending = request;
			}
			SkyCraft.LOG.info("SkyCraft-backup: Skyrim loaded {} ({}); this world is backed up with it once a world of your own is open",
				request.saveName(), request.stamp());
			return;
		}
		server.execute(() -> start(server, request));
	}

	private static BackupRules.Settings settings() {
		BackupRules.Settings s = settings;
		if (s == null) {
			List<String> problems = new ArrayList<>();
			s = BackupRules.parse(Config.read(FILE, BackupRules.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-backup: {} {}", FILE, p));
			SkyCraft.LOG.info("SkyCraft-backup: keeping the newest {} pairs", s.keep() == 0 ? "(all)" : s.keep());
			settings = s;
		}
		return s;
	}

	/** Server thread: everything to disk, autosaves held, then the zip on the backup thread. */
	private static void start(MinecraftServer server, BackupRequest.Request request) {
		Path world = server.getWorldPath(LevelResource.ROOT).normalize();
		long started = System.nanoTime();
		try {
			server.getPlayerList().saveAll();
			server.saveAllChunks(true, true, true);
		} catch (RuntimeException e) {
			SkyCraft.LOG.warn("SkyCraft-backup: saving the world before its backup failed; backing up what's on disk", e);
		}
		if (writing++ == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				if (!level.noSave) {
					level.noSave = true; // as /save-off does: the files hold still while they're read
					HELD.add(level);
				}
			}
		}
		long savedMs = (System.nanoTime() - started) / 1_000_000;
		WORKER.execute(() -> write(server, request, world, savedMs));
	}

	/** The backup thread: the zip, the older pairs deleted, Skyrim told. */
	private static void write(MinecraftServer server, BackupRequest.Request request, Path world, long savedMs) {
		int keep = settings().keep();
		Path folder = FabricLoader.getInstance().getGameDir().resolve(FOLDER);
		boolean ok = false;
		long bytes = 0;
		long started = System.nanoTime();
		try {
			Files.createDirectories(folder);
			Path target = folder.resolve(BackupNames.zipName(request.stamp(), String.valueOf(world.getFileName())));
			WorldZip.Result result = WorldZip.zip(world, target,
				"SkyCraft paired backup " + request.stamp() + ": this world as it was when Skyrim loaded " + request.saveName());
			ok = true;
			bytes = result.bytes();
			SkyCraft.LOG.info("SkyCraft-backup: {} written for Skyrim's {}: {} files, {} MB (saving {} ms, zipping {} ms){}", target.getFileName(),
				request.saveName(), result.files(), String.format("%.1f", bytes / 1048576.0), savedMs, (System.nanoTime() - started) / 1_000_000,
				result.skipped() > 0 ? "; " + result.skipped() + " files couldn't be read" : "");
		} catch (IOException | RuntimeException e) {
			SkyCraft.LOG.warn("SkyCraft-backup: couldn't back up {} for Skyrim's {}", world, request.saveName(), e);
		} finally {
			server.execute(() -> {
				if (--writing <= 0) {
					writing = 0;
					HELD.forEach(level -> level.noSave = false);
					HELD.clear();
				}
			});
		}
		prune(folder, keep);
		SkyLink.pushEvent(Proto.EV_PAIRED_BACKUP, request.id(), keep, (float) (bytes / 1048576.0), 0.0F, 0.0F, ok ? 1 : 0);
	}

	/** The backup thread: zips past the newest {@code keep} pairs, and any left half written, are deleted. */
	private static void prune(Path folder, int keep) {
		List<String> names;
		try (Stream<Path> files = Files.list(folder)) {
			names = files.map(p -> p.getFileName().toString()).toList();
		} catch (IOException e) {
			return;
		}
		for (String name : BackupNames.toDelete(names, keep)) {
			try {
				Files.deleteIfExists(folder.resolve(name));
				SkyCraft.LOG.info("SkyCraft-backup: deleted the older {}", name);
			} catch (IOException e) {
				SkyCraft.LOG.warn("SkyCraft-backup: couldn't delete {}: {}", name, e.toString());
			}
		}
	}
}
