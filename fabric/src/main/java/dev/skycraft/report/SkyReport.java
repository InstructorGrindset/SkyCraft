package dev.skycraft.report;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

import dev.skycraft.SkyCraft;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /skycraft report}: packs what a tester should send ({@link ReportPlan}: Minecraft's latest.log, its newest crash
 * reports, SkyCraft's config files and the Skyrim plugin's SkyCraft.log) into {@code skycraft-report-<date-time>.zip}
 * in Minecraft's game folder and says where in chat (click the path to copy it).
 *
 * <p>Those files are on the machine of the player who asks, so the server asks that player's client to write it
 * ({@link Ask}): a guest in a friend's world gets a report of their own games, not the host's. From the server console
 * the server writes one of its own machine. Written on a thread of its own: a long log takes a moment. Log tag
 * {@code SkyCraft-report}.
 */
public final class SkyReport {
	private static final DateTimeFormatter README_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

	/** Server -> client: write your SkyCraft report, with this many of the newest crash reports. */
	public record Ask(int crashReports) implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "report_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Ask::crashReports, Ask::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A report written ({@code zip}), or why not ({@code error}); {@code skyrimLogFound} says whether Skyrim's log is in it. */
	public record Result(@Nullable Path zip, int files, boolean skyrimLogFound, @Nullable String error) {
	}

	private SkyReport() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Ask.TYPE, Ask.CODEC);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("skycraft")
			.then(Commands.literal("report").executes(c -> report(c.getSource())))));
	}

	private static int report(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			if (!ServerPlayNetworking.canSend(player, Ask.TYPE)) {
				source.sendSuccess(() -> Component.literal("Your game can't write a SkyCraft report: it needs this version of SkyCraft."), false);
				return 0;
			}
			ServerPlayNetworking.send(player, new Ask(ReportPlan.CRASH_REPORTS));
			source.sendSuccess(() -> Component.literal("Writing a SkyCraft report..."), false);
			return 1;
		}
		// The server console: this machine's files.
		MinecraftServer server = source.getServer();
		source.sendSuccess(() -> Component.literal("Writing a SkyCraft report..."), false);
		writeInBackground(ReportPlan.CRASH_REPORTS, result -> server.execute(() -> source.sendSuccess(() -> message(result), false)));
		return 1;
	}

	/** Writes a report on a thread of its own, then hands {@code done} the result (on that thread). Client or server. */
	public static void writeInBackground(int crashReports, Consumer<Result> done) {
		Thread thread = new Thread(() -> done.accept(write(crashReports)), "SkyCraft report");
		thread.setDaemon(true);
		thread.start();
	}

	/** Writes {@code skycraft-report-<date-time>.zip} in Minecraft's game folder from this machine's files. */
	public static Result write(int crashReports) {
		LocalDateTime now = LocalDateTime.now();
		Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
		Path zip = game.resolve(ReportPlan.fileName(now));
		try {
			ReportPlan plan = ReportPlan.plan(game, FabricLoader.getInstance().getConfigDir(), ReportPlan.skyrimLogs(documentsFolders()), crashReports);
			ReportPlan.Written written = plan.write(zip, README_TIME.format(now));
			SkyCraft.LOG.info("SkyCraft-report: wrote {} with {}{}", zip, String.join(", ", written.names()),
				written.notes().isEmpty() ? "" : "; " + String.join(" ", written.notes()));
			return new Result(zip, written.names().size(), plan.skyrimLogFound(), null);
		} catch (Exception e) {
			SkyCraft.LOG.warn("SkyCraft-report: couldn't write {}: {}", zip, e.toString());
			return new Result(null, 0, false, e.getMessage() != null ? e.getMessage() : e.toString());
		}
	}

	/** The chat line for a report: where it is (click to copy the path), or why there isn't one. */
	public static Component message(Result result) {
		if (result.zip() == null) {
			return Component.literal("Couldn't write the SkyCraft report: " + result.error()).withStyle(ChatFormatting.RED);
		}
		String path = result.zip().toString();
		MutableComponent line = Component.literal("SkyCraft report saved: ");
		line.append(Component.literal(path).withStyle(ChatFormatting.UNDERLINE).withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(path))));
		line.append(Component.literal(" (click to copy). Send it with your bug report."));
		if (!result.skyrimLogFound()) {
			line.append(Component.literal(" Skyrim's SkyCraft.log wasn't found, so it isn't in it; README.txt in the zip says where it looked."));
		}
		return line;
	}

	/**
	 * Where the user's Documents folder may be, best first: Windows' own answer (SHGetKnownFolderPath, as SKSE's
	 * log_directory asks it, so a moved or OneDrive Documents folder is found), then the usual places.
	 */
	static List<Path> documentsFolders() {
		List<Path> out = new ArrayList<>();
		Path known = windowsDocuments();
		if (known != null) {
			out.add(known);
		}
		try {
			String profile = System.getenv("USERPROFILE");
			if (profile != null && !profile.isBlank()) {
				out.add(Path.of(profile, "Documents"));
				out.add(Path.of(profile, "OneDrive", "Documents"));
			}
			out.add(Path.of(System.getProperty("user.home"), "Documents"));
		} catch (RuntimeException e) {
			// An odd path in the environment: the others still count.
		}
		return out.stream().distinct().toList();
	}

	/** FOLDERID_Documents, {FDD39AD0-238F-46AF-ADB4-6C85480369C7}. */
	private static final byte[] FOLDERID_DOCUMENTS_TAIL = { (byte) 0xAD, (byte) 0xB4, 0x6C, (byte) 0x85, 0x48, 0x03, 0x69, (byte) 0xC7 };

	/** The Documents folder Windows knows for this user (shell32 SHGetKnownFolderPath), or null off Windows or on failure. */
	private static @Nullable Path windowsDocuments() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
			return null;
		}
		try (Arena arena = Arena.ofConfined()) {
			Linker linker = Linker.nativeLinker();
			MethodHandle getPath = linker.downcallHandle(SymbolLookup.libraryLookup("shell32", arena).find("SHGetKnownFolderPath").orElseThrow(),
				FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
			MethodHandle free = linker.downcallHandle(SymbolLookup.libraryLookup("ole32", arena).find("CoTaskMemFree").orElseThrow(),
				FunctionDescriptor.ofVoid(ADDRESS));
			MemorySegment id = arena.allocate(16);
			id.set(JAVA_INT_UNALIGNED, 0, 0xFDD39AD0);
			id.set(JAVA_SHORT_UNALIGNED, 4, (short) 0x238F);
			id.set(JAVA_SHORT_UNALIGNED, 6, (short) 0x46AF);
			for (int i = 0; i < FOLDERID_DOCUMENTS_TAIL.length; i++) {
				id.set(JAVA_BYTE, 8 + i, FOLDERID_DOCUMENTS_TAIL[i]);
			}
			MemorySegment out = arena.allocate(ADDRESS);
			int result = (int) getPath.invokeExact(id, 0, MemorySegment.NULL, out);
			MemorySegment text = out.get(ADDRESS, 0);
			try {
				if (result != 0 || text.address() == 0) {
					return null;
				}
				// A wide string ending in 0; a Windows path is at most 32767 characters.
				MemorySegment chars = text.reinterpret(2L * 32768);
				StringBuilder sb = new StringBuilder();
				for (long at = 0; at < chars.byteSize(); at += 2) {
					char c = chars.get(JAVA_CHAR_UNALIGNED, at);
					if (c == 0) {
						break;
					}
					sb.append(c);
				}
				return sb.isEmpty() ? null : Path.of(sb.toString());
			} finally {
				if (text.address() != 0) {
					free.invokeExact(text);
				}
			}
		} catch (Throwable t) {
			SkyCraft.LOG.warn("SkyCraft-report: couldn't ask Windows where Documents is: {}", t.toString());
			return null;
		}
	}
}
