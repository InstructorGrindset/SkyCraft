package dev.skycraft.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MagicTableTest {
	/** A table as Skyrim's plugin writes it (Magic.cpp), field by field at the protocol header's offsets. */
	private static final class Writer {
		final byte[] bytes = new byte[Proto.MAGIC_TABLE_BYTES];
		final ByteBuffer b = ByteBuffer.wrap(this.bytes).order(ByteOrder.LITTLE_ENDIAN);
		int shouts, spells;

		Writer header(int refresh, int flags, int equipped, float voice, float magicka, float max, float mult) {
			this.b.putInt(Proto.MT_REFRESH, refresh);
			this.b.put(Proto.MT_FLAGS, (byte) flags);
			this.b.putInt(Proto.MT_EQUIPPED_SHOUT, equipped);
			this.b.putFloat(Proto.MT_VOICE_RECOVERY, voice);
			this.b.putFloat(Proto.MT_MAGICKA, magicka);
			this.b.putFloat(Proto.MT_MAGICKA_MAX, max);
			this.b.putFloat(Proto.MT_SHOUT_RECOVERY_MULT, mult);
			return this;
		}

		Writer shout(int formId, int words, int kind, int flags, float r1, float r2, float r3, String name) {
			int r = Proto.MT_SHOUTS + this.shouts++ * Proto.MAGIC_SHOUT_BYTES;
			this.b.putInt(r, formId);
			this.b.put(r + Proto.MSH_WORDS, (byte) words);
			this.b.put(r + Proto.MSH_KIND, (byte) kind);
			this.b.put(r + Proto.MSH_FLAGS, (byte) flags);
			this.b.putFloat(r + Proto.MSH_RECOVERY, r1);
			this.b.putFloat(r + Proto.MSH_RECOVERY + 4, r2);
			this.b.putFloat(r + Proto.MSH_RECOVERY + 8, r3);
			name(r + Proto.MSH_NAME, Proto.MSH_NAME_BYTES, name);
			this.b.put(Proto.MT_SHOUT_COUNT, (byte) this.shouts);
			return this;
		}

		Writer spell(int formId, float cost, int school, int kind, int flags, int delivery, String name) {
			int r = Proto.MT_SPELLS + this.spells++ * Proto.MAGIC_SPELL_BYTES;
			this.b.putInt(r, formId);
			this.b.putFloat(r + Proto.MSP_COST, cost);
			this.b.put(r + Proto.MSP_SCHOOL, (byte) school);
			this.b.put(r + Proto.MSP_KIND, (byte) kind);
			this.b.put(r + Proto.MSP_FLAGS, (byte) flags);
			this.b.put(r + Proto.MSP_DELIVERY, (byte) delivery);
			name(r + Proto.MSP_NAME, Proto.MSP_NAME_BYTES, name);
			this.b.put(Proto.MT_SPELL_COUNT, (byte) this.spells);
			return this;
		}

		/** Like strncpy_s with _TRUNCATE: at most max - 1 bytes and a NUL. */
		private void name(int at, int max, String name) {
			byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
			System.arraycopy(utf8, 0, this.bytes, at, Math.min(utf8.length, max - 1));
		}
	}

	@Test
	void theLayoutFitsBetweenTheInputRingAndTheActorTable() {
		assertEquals(Proto.MT_SHOUTS + Proto.MAX_MAGIC_SHOUTS * Proto.MAGIC_SHOUT_BYTES, Proto.MT_SPELLS);
		assertEquals(Proto.MT_SPELLS + Proto.MAX_MAGIC_SPELLS * Proto.MAGIC_SPELL_BYTES, Proto.MAGIC_TABLE_BYTES);
		assertTrue(Proto.OFF_MAGIC_TABLE >= Proto.OFF_INPUT_RING + Proto.IR_DATA + 16L * Proto.INPUT_RING_ENTRIES);
		assertTrue(Proto.OFF_MAGIC_TABLE + Proto.MAGIC_TABLE_BYTES <= Proto.OFF_MAGIC_TABLE + 0x800);
		assertTrue(Proto.OFF_MAGIC_TABLE + 0x800 <= Proto.OFF_ACTOR_TABLE);
		assertEquals(Proto.MSH_NAME + Proto.MSH_NAME_BYTES, Proto.MAGIC_SHOUT_BYTES);
		assertEquals(Proto.MSP_NAME + Proto.MSP_NAME_BYTES, Proto.MAGIC_SPELL_BYTES);
	}

	@Test
	void aTableNeverWrittenIsNone() {
		assertEquals(MagicTable.NONE, MagicTable.decode(new byte[Proto.MAGIC_TABLE_BYTES]));
		assertFalse(MagicTable.NONE.known());
		assertEquals(MagicTable.NONE, MagicTable.decode(new byte[16])); // too short to be one
	}

	@Test
	void decodesShoutsAndSpells() {
		Writer w = new Writer().header(7, Proto.MAGIC_SHOUTS | Proto.MAGIC_SPELLS | Proto.MAGIC_FAVOURITES, 0x0A000001, 12.5F, 80.0F, 150.0F, 0.8F)
			.shout(0x0A000001, 3, Proto.SHOUT_KIND_FORCE, Proto.MAGIC_SHOUT_EQUIPPED, 15.0F, 20.0F, 45.0F, "Unrelenting Force")
			.shout(0x0A000002, 1, Proto.SHOUT_KIND_SPRINT, 0, 20.0F, 25.0F, 35.0F, "Whirlwind Sprint")
			.spell(0x0B000001, 13.5F, Proto.SCHOOL_DESTRUCTION, Proto.SPELL_KIND_FIRE, Proto.MAGIC_SPELL_CONCENTRATION | Proto.MAGIC_SPELL_HOSTILE,
				Proto.DELIVERY_AIMED, "Flames")
			.spell(0x0B000002, 25.0F, Proto.SCHOOL_RESTORATION, Proto.SPELL_KIND_HEAL, Proto.MAGIC_SPELL_CONCENTRATION | Proto.MAGIC_SPELL_FAVOURITE,
				Proto.DELIVERY_SELF, "Healing");
		MagicTable t = MagicTable.decode(w.bytes);
		assertTrue(t.known() && t.shoutsOn() && t.spellsOn());
		assertEquals(7, t.refresh());
		assertEquals(0x0A000001, t.equippedShout());
		assertEquals(12.5F, t.voiceRecovery(), 1e-6F);
		assertEquals(80.0F, t.magicka(), 1e-6F);
		assertEquals(150.0F, t.magickaMax(), 1e-6F);
		assertEquals(0.8F, t.shoutRecoveryMult(), 1e-6F);
		assertEquals(2, t.shouts().size());
		MagicTable.Shout force = t.shout(0x0A000001);
		assertNotNull(force);
		assertEquals("Unrelenting Force", force.name());
		assertEquals(3, force.words());
		assertEquals(Proto.SHOUT_KIND_FORCE, force.kind());
		assertTrue(force.equipped());
		assertEquals(20.0F, force.recovery(2), 1e-6F);
		assertEquals(0.0F, force.recovery(4), 1e-6F);
		assertFalse(t.shouts().get(1).equipped());
		assertEquals(2, t.spells().size());
		MagicTable.Spell flames = t.spells().get(0);
		assertEquals("Flames", flames.name());
		assertEquals(13.5F, flames.cost(), 1e-6F);
		assertEquals(Proto.SCHOOL_DESTRUCTION, flames.school());
		assertEquals(Proto.SPELL_KIND_FIRE, flames.kind());
		assertEquals(Proto.DELIVERY_AIMED, flames.delivery());
		assertTrue(flames.concentration());
		assertFalse(flames.favourite());
		MagicTable.Spell healing = t.spell(0x0B000002);
		assertNotNull(healing);
		assertTrue(healing.favourite());
		assertNull(t.spell(0x12345678));
		assertNull(t.shout(0));
	}

	@Test
	void namesAreCutAtTheirRoomAndKeepUtf8() {
		Writer w = new Writer().header(1, Proto.MAGIC_SHOUTS, 0, 0.0F, 0.0F, 0.0F, 1.0F)
			.shout(1, 1, 0, 0, 1, 2, 3, "A shout with a name far too long for it")
			.shout(2, 1, 0, 0, 1, 2, 3, "Ruf der Tapferkeit")
			.shout(3, 1, 0, 0, 1, 2, 3, "Œil de Dragon");
		MagicTable t = MagicTable.decode(w.bytes);
		assertEquals("A shout with a name", t.shouts().get(0).name()); // 19 bytes and the NUL
		assertEquals("Ruf der Tapferkeit", t.shouts().get(1).name());
		assertEquals("Œil de Dragon", t.shouts().get(2).name());
	}

	@Test
	void countsPastTheRoomAndBadNumbersAreTamed() {
		Writer w = new Writer().header(3, Proto.MAGIC_SHOUTS | Proto.MAGIC_SPELLS, 0, Float.NaN, Float.POSITIVE_INFINITY, 100.0F, -1.0F);
		w.b.put(Proto.MT_SHOUT_COUNT, (byte) 200);
		w.b.put(Proto.MT_SPELL_COUNT, (byte) 255);
		w.b.put(Proto.MT_SHOUTS + Proto.MSH_WORDS, (byte) 9);
		MagicTable t = MagicTable.decode(w.bytes);
		assertEquals(Proto.MAX_MAGIC_SHOUTS, t.shouts().size());
		assertEquals(Proto.MAX_MAGIC_SPELLS, t.spells().size());
		assertEquals(3, t.shouts().get(0).words()); // never more than three words
		assertEquals(0.0F, t.voiceRecovery(), 0.0F);
		assertEquals(0.0F, t.magicka(), 0.0F);
		assertEquals(1.0F, t.shoutRecoveryMult(), 0.0F); // a nonsense multiplier is taken as none
	}

	@Test
	void halvesFollowTheSwitches() {
		MagicTable spellsOnly = MagicTable.decode(new Writer().header(5, Proto.MAGIC_SPELLS, 0, 0.0F, 50.0F, 50.0F, 1.0F).bytes);
		assertTrue(spellsOnly.known());
		assertFalse(spellsOnly.shoutsOn());
		assertTrue(spellsOnly.spellsOn());
	}
}
