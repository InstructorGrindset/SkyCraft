package dev.skycraft.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skycraft.SkyCraft;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Who placed the Minecraft blocks in Skyrim's towns (town_crime): breaking a block someone else placed there is
 * griefing. Kept per chunk as a Fabric data attachment saved with the world (server side only, never synced), for the
 * blocks placed while the host's Skyrim was in a town; a broken block is forgotten. Blocks placed before this was kept
 * have no record and are nobody's (never a crime).
 */
public final class TownBlocks {
	/** At most this many records a chunk; past it the oldest go. */
	private static final int MAX_PER_CHUNK = 4096;

	private TownBlocks() {
	}

	/** One block and who placed it (a player's UUID). */
	public record Placed(long pos, String by) {
		static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(Placed::pos),
			Codec.STRING.fieldOf("by").forGetter(Placed::by)
		).apply(i, Placed::new));
	}

	/** A chunk's records. Immutable: a change makes a new one (so the chunk is saved). */
	public record Column(List<Placed> blocks) {
		static final Codec<Column> CODEC = Placed.CODEC.listOf().xmap(Column::new, Column::blocks);

		@Nullable String owner(long pos) {
			for (Placed p : this.blocks) {
				if (p.pos == pos) {
					return p.by;
				}
			}
			return null;
		}

		Column with(long pos, String by) {
			List<Placed> out = new ArrayList<>(this.blocks.size() + 1);
			for (Placed p : this.blocks) {
				if (p.pos != pos) {
					out.add(p);
				}
			}
			out.add(new Placed(pos, by));
			return new Column(List.copyOf(out.size() > MAX_PER_CHUNK ? out.subList(out.size() - MAX_PER_CHUNK, out.size()) : out));
		}

		Column without(long pos) {
			List<Placed> out = new ArrayList<>(this.blocks.size());
			for (Placed p : this.blocks) {
				if (p.pos != pos) {
					out.add(p);
				}
			}
			return out.size() == this.blocks.size() ? this : new Column(List.copyOf(out));
		}
	}

	public static final AttachmentType<Column> PLACED = AttachmentRegistry.<Column>builder()
		.persistent(Column.CODEC)
		.buildAndRegister(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "town_placed"));

	/** Loads the class (registers the attachment) at mod start. */
	static void init() {
		SkyCraft.LOG.info("SkyCraft-town: who placed the blocks in Skyrim's towns is kept with the chunks ({})", PLACED.identifier());
	}

	/** Who placed the block here, or null if nobody is known to have. */
	public static @Nullable String owner(ServerLevel level, BlockPos pos) {
		Column column = level.getChunkAt(pos).getAttached(PLACED);
		return column == null ? null : column.owner(pos.asLong());
	}

	/** A player ({@code by}, their UUID) placed this block. */
	public static void placed(ServerLevel level, BlockPos pos, String by) {
		LevelChunk chunk = level.getChunkAt(pos);
		Column column = chunk.getAttached(PLACED);
		chunk.setAttached(PLACED, (column == null ? new Column(List.of()) : column).with(pos.asLong(), by));
	}

	/** The block here is gone: who placed it (null if nobody is known to have), forgotten. */
	public static @Nullable String take(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Column column = chunk.getAttached(PLACED);
		if (column == null) {
			return null;
		}
		String owner = column.owner(pos.asLong());
		if (owner != null) {
			Column rest = column.without(pos.asLong());
			chunk.setAttached(PLACED, rest.blocks().isEmpty() ? null : rest);
		}
		return owner;
	}
}
