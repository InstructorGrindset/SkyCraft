package dev.skycraft.magic;

import dev.skycraft.link.Proto;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim's magic table as Minecraft reads it (Shouts and Spells; MagicTable in the protocol header, at Proto.OFF_MAGIC_TABLE):
 * the shouts and hand-cast spells the Skyrim player knows, the shout in their voice slot, their voice recovery and magicka.
 * Skyrim refreshes it about twice a second. Pure: {@link #decode} takes the table's bytes as SkyLink copied them out of
 * shared memory, so it's unit tested on its own.
 *
 * @param refresh Skyrim's refresh number (0: nothing written yet, an older plugin)
 * @param flags Proto.MAGIC_SHOUTS, MAGIC_SPELLS (which halves the switches filled), MAGIC_FAVOURITES
 * @param equippedShout FormID of the shout in the voice slot, 0 none
 * @param voiceRecovery seconds of voice recovery left when Skyrim wrote it
 * @param shoutRecoveryMult the player's ShoutRecoveryMult
 */
public record MagicTable(int refresh, int flags, int equippedShout, float voiceRecovery, float magicka, float magickaMax, float shoutRecoveryMult,
	List<Shout> shouts, List<Spell> spells) {
	/** Nothing known: no Skyrim, an older plugin, or both switches off. */
	public static final MagicTable NONE = new MagicTable(0, 0, 0, 0.0F, 0.0F, 0.0F, 1.0F, List.of(), List.of());

	/**
	 * A shout the player knows.
	 *
	 * @param words its words of power unlocked, in order (0 to 3; 0: it can't be shouted yet)
	 * @param kind Proto.SHOUT_KIND_*: what Minecraft does for it
	 * @param recovery seconds of voice recovery after 1, 2 and 3 words, before the player's ShoutRecoveryMult
	 */
	public record Shout(int formId, int words, int kind, int flags, float[] recovery, String name) {
		/** In the player's voice slot. */
		public boolean equipped() {
			return (this.flags & Proto.MAGIC_SHOUT_EQUIPPED) != 0;
		}

		/** Seconds of voice recovery after {@code words} words (before ShoutRecoveryMult), 0 if Skyrim didn't say. */
		public float recovery(int words) {
			return words >= 1 && words <= this.recovery.length ? this.recovery[words - 1] : 0.0F;
		}
	}

	/**
	 * A spell the player knows and casts by hand.
	 *
	 * @param cost magicka it costs the player now (per second for a concentration spell)
	 * @param school ActorValue of its school (Proto.SCHOOL_*), 0 none
	 * @param kind Proto.SPELL_KIND_*: what Minecraft does for it
	 * @param delivery Proto.DELIVERY_*
	 */
	public record Spell(int formId, float cost, int school, int kind, int flags, int delivery, String name) {
		/** Cast in pulses while the key is held; its cost is per second. */
		public boolean concentration() {
			return (this.flags & Proto.MAGIC_SPELL_CONCENTRATION) != 0;
		}

		/** Among Skyrim's magic favourites. */
		public boolean favourite() {
			return (this.flags & Proto.MAGIC_SPELL_FAVOURITE) != 0;
		}
	}

	/** Whether Skyrim has written the table at all. */
	public boolean known() {
		return this.refresh != 0;
	}

	/** The shouts half is filled (Skyrim runs shouts). */
	public boolean shoutsOn() {
		return (this.flags & Proto.MAGIC_SHOUTS) != 0;
	}

	/** The spells half is filled (Skyrim runs spells). */
	public boolean spellsOn() {
		return (this.flags & Proto.MAGIC_SPELLS) != 0;
	}

	/** The shout with this FormID, or null. */
	public @Nullable Shout shout(int formId) {
		for (Shout s : this.shouts) {
			if (s.formId() == formId) {
				return s;
			}
		}
		return null;
	}

	/** The spell with this FormID, or null. */
	public @Nullable Spell spell(int formId) {
		for (Spell s : this.spells) {
			if (s.formId() == formId) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Decodes the table from its bytes (little endian, {@code bytes[0]} is its sequence number; at least
	 * Proto.MAGIC_TABLE_BYTES long). Counts past the table's room are cut to it; {@link #NONE} for a table never written.
	 */
	public static MagicTable decode(byte[] bytes) {
		if (bytes.length < Proto.MAGIC_TABLE_BYTES) {
			return NONE;
		}
		ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		int refresh = b.getInt(Proto.MT_REFRESH);
		if (refresh == 0) {
			return NONE;
		}
		int shoutCount = Math.min(Byte.toUnsignedInt(b.get(Proto.MT_SHOUT_COUNT)), Proto.MAX_MAGIC_SHOUTS);
		int spellCount = Math.min(Byte.toUnsignedInt(b.get(Proto.MT_SPELL_COUNT)), Proto.MAX_MAGIC_SPELLS);
		List<Shout> shouts = new ArrayList<>(shoutCount);
		for (int i = 0; i < shoutCount; i++) {
			int r = Proto.MT_SHOUTS + i * Proto.MAGIC_SHOUT_BYTES;
			float[] recovery = new float[3];
			for (int k = 0; k < 3; k++) {
				recovery[k] = finite(b.getFloat(r + Proto.MSH_RECOVERY + k * 4));
			}
			shouts.add(new Shout(b.getInt(r), Math.min(Byte.toUnsignedInt(b.get(r + Proto.MSH_WORDS)), 3), Byte.toUnsignedInt(b.get(r + Proto.MSH_KIND)),
				Byte.toUnsignedInt(b.get(r + Proto.MSH_FLAGS)), recovery, name(bytes, r + Proto.MSH_NAME, Proto.MSH_NAME_BYTES)));
		}
		List<Spell> spells = new ArrayList<>(spellCount);
		for (int i = 0; i < spellCount; i++) {
			int r = Proto.MT_SPELLS + i * Proto.MAGIC_SPELL_BYTES;
			spells.add(new Spell(b.getInt(r), finite(b.getFloat(r + Proto.MSP_COST)), Byte.toUnsignedInt(b.get(r + Proto.MSP_SCHOOL)),
				Byte.toUnsignedInt(b.get(r + Proto.MSP_KIND)), Byte.toUnsignedInt(b.get(r + Proto.MSP_FLAGS)), Byte.toUnsignedInt(b.get(r + Proto.MSP_DELIVERY)),
				name(bytes, r + Proto.MSP_NAME, Proto.MSP_NAME_BYTES)));
		}
		float mult = b.getFloat(Proto.MT_SHOUT_RECOVERY_MULT);
		return new MagicTable(refresh, Byte.toUnsignedInt(b.get(Proto.MT_FLAGS)), b.getInt(Proto.MT_EQUIPPED_SHOUT), finite(b.getFloat(Proto.MT_VOICE_RECOVERY)),
			finite(b.getFloat(Proto.MT_MAGICKA)), finite(b.getFloat(Proto.MT_MAGICKA_MAX)), mult >= 0.0F && mult < 100.0F ? mult : 1.0F,
			List.copyOf(shouts), List.copyOf(spells));
	}

	/** A NUL-terminated UTF-8 name of at most {@code max} bytes. */
	private static String name(byte[] bytes, int at, int max) {
		int n = 0;
		while (n < max && bytes[at + n] != 0) {
			n++;
		}
		return new String(bytes, at, n, StandardCharsets.UTF_8);
	}

	/** Skyrim's float, or 0 when it isn't a number (never NaN or infinite downstream). */
	private static float finite(float f) {
		return Float.isFinite(f) ? f : 0.0F;
	}
}
