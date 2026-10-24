package dev.skycraft.maps;

import static dev.skycraft.link.Proto.*;
import static java.lang.foreign.ValueLayout.*;

import dev.skycraft.link.SkyLink;
import dev.skycraft.link.SkySpace;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Reads the map table Skyrim writes (MapTable in the protocol header; seqlock, added within protocol 13): its map markers
 * near the player and its answer to the last land sample asked for, moved into the mirror's terms (the space of the world
 * Skyrim names). An older plugin never writes it: all zeros, nothing yet. Any thread.
 */
public final class SkyMapLink {
	private static final VarHandle INT = JAVA_INT.varHandle();

	private SkyMapLink() {
	}

	/** The table's seqlock count: it moves on every write (0: nothing written yet, or no link). */
	public static int seq() {
		MemorySegment s = SkyLink.segment();
		return s == null ? 0 : (int) INT.getAcquire(s, OFF_MAP_TABLE + MAPT_SEQ);
	}

	/**
	 * Where Skyrim listed its markers from: every visible marker closer than {@code reach} to {@code x, z} is listed. World 0:
	 * none (Skyrim's player is indoors).
	 */
	public record Listing(int world, int version, double x, double z, double reach) {
	}

	/** Reads the markers into {@code out} (mirror coords). Null (and {@code out} empty) with no link, nothing yet, or a torn read. */
	public static @Nullable Listing readMarkers(List<MapMarkers.Marker> out) {
		out.clear();
		MemorySegment s = SkyLink.segment();
		if (s == null) {
			return null;
		}
		long b = OFF_MAP_TABLE;
		for (int attempt = 0; attempt < 16; attempt++) {
			int seq1 = (int) INT.getAcquire(s, b + MAPT_SEQ);
			if (seq1 == 0) {
				return null;
			}
			if ((seq1 & 1) != 0) {
				Thread.onSpinWait();
				continue;
			}
			int world = s.get(JAVA_INT, b + MAPT_WORLD_ID);
			int count = Math.max(0, Math.min(s.get(JAVA_INT, b + MAPT_MARKER_COUNT), MAX_MAP_MARKERS));
			int version = s.get(JAVA_INT, b + MAPT_MARKER_VERSION);
			double ox = SkySpace.originX(world), oz = SkySpace.originZ(world);
			Listing listing = new Listing(world, version, s.get(JAVA_FLOAT, b + MAPT_CENTER_X) + ox, s.get(JAVA_FLOAT, b + MAPT_CENTER_Z) + oz,
				s.get(JAVA_FLOAT, b + MAPT_MARKER_REACH));
			for (int i = 0; i < count; i++) {
				long r = b + MAPT_MARKERS + i * MAP_MARKER_BYTES;
				out.add(new MapMarkers.Marker(s.get(JAVA_INT, r + MM_FORM_ID), s.get(JAVA_FLOAT, r + MM_X) + ox, s.get(JAVA_FLOAT, r + MM_Z) + oz,
					Short.toUnsignedInt(s.get(JAVA_SHORT, r + MM_TYPE)), Byte.toUnsignedInt(s.get(JAVA_BYTE, r + MM_FLAGS)), name(s, r + MM_NAME)));
			}
			VarHandle.loadLoadFence();
			if ((int) INT.getAcquire(s, b + MAPT_SEQ) == seq1) {
				return listing;
			}
			out.clear();
		}
		return null;
	}

	/** Skyrim's answer to land sample request {@code request}, in the mirror's terms; null until it has answered that one. */
	public static LandSamples.@Nullable Patch readSamples(int request) {
		MemorySegment s = SkyLink.segment();
		if (s == null || request == 0) {
			return null;
		}
		long b = OFF_MAP_TABLE;
		int n = MAP_SAMPLE_SIDE * MAP_SAMPLE_SIDE;
		for (int attempt = 0; attempt < 16; attempt++) {
			int seq1 = (int) INT.getAcquire(s, b + MAPT_SEQ);
			if (seq1 == 0) {
				return null;
			}
			if ((seq1 & 1) != 0) {
				Thread.onSpinWait();
				continue;
			}
			if (s.get(JAVA_INT, b + MAPT_SAMPLE_REQUEST) != request) {
				return null;
			}
			int world = s.get(JAVA_INT, b + MAPT_SAMPLE_WORLD);
			int x0 = s.get(JAVA_INT, b + MAPT_SAMPLE_X) + SkySpace.originX(world), z0 = s.get(JAVA_INT, b + MAPT_SAMPLE_Z) + SkySpace.originZ(world);
			int spacing = Math.max(1, s.get(JAVA_INT, b + MAPT_SAMPLE_SPACING));
			short[] height = new short[n];
			byte[] material = new byte[n];
			byte[] water = new byte[n];
			for (int i = 0; i < n; i++) {
				long e = b + MAPT_SAMPLES + i * MAP_SAMPLE_BYTES;
				height[i] = s.get(JAVA_SHORT, e);
				material[i] = s.get(JAVA_BYTE, e + 2);
				water[i] = s.get(JAVA_BYTE, e + 3);
			}
			VarHandle.loadLoadFence();
			if ((int) INT.getAcquire(s, b + MAPT_SEQ) == seq1) {
				return new LandSamples.Patch(x0, z0, spacing, height, material, water);
			}
		}
		return null;
	}

	private static String name(MemorySegment s, long off) {
		byte[] bytes = new byte[MM_NAME_BYTES];
		int n = 0;
		while (n < MM_NAME_BYTES) {
			byte c = s.get(JAVA_BYTE, off + n);
			if (c == 0) {
				break;
			}
			bytes[n++] = c;
		}
		return new String(bytes, 0, n, StandardCharsets.UTF_8);
	}
}
