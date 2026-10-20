package dev.skycraft.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyCodecTest {
	private static PartyCodec.Actor actor(int formId, String name) {
		return new PartyCodec.Actor(formId, 0x9, 32768.25F + formId, 64.5F, -12.75F, 90.0F, 0.6F, 1.8F, 0.5F, 12, 0x41, name);
	}

	@Test
	void anActorTableComesBackAsItWent() {
		PartyCodec.Header header = new PartyCodec.Header(0x0001A26F, 3, 14.5F, false, "Bleak Falls Barrow");
		List<PartyCodec.Actor> actors = List.of(actor(0x14A, "Draugr Overlord"), actor(0x2B, "Mirmulnir"), actor(7, ""));
		PartyCodec.ActorTable table = PartyCodec.readActors(PartyCodec.actors(header, actors));
		assertEquals(header, table.header());
		assertEquals(3, table.actors().size());
		PartyCodec.Actor a = table.actors().getFirst();
		assertEquals(0x14A, a.formId());
		assertEquals(0x9, a.flags());
		assertEquals(32768.25F + 0x14A, a.x(), 0.0F);
		assertEquals(-12.75F, a.z(), 0.0F);
		assertEquals(0.6F, a.width(), 0.001F);
		assertEquals(1.8F, a.height(), 0.001F);
		assertEquals(12, a.level());
		assertEquals(0x41, a.vendor());
		assertEquals("Draugr Overlord", a.name());
		assertEquals("", table.actors().get(2).name());
	}

	@Test
	void namesAreCutAtWholeCharactersAndControlCharactersDropped() {
		PartyCodec.Header header = new PartyCodec.Header(1, 0, 0.0F, true, "Ørnstedt\u0007 Vale and a very long tail on it");
		PartyCodec.ActorTable table = PartyCodec.readActors(PartyCodec.actors(header, List.of(actor(1, "Ysgramor's ghost, Ænd lots more"))));
		assertTrue(table.header().away());
		String town = table.header().town();
		assertTrue(town.startsWith("Ørnstedt Vale"), town);
		assertTrue(town.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= PartyCodec.TOWN_BYTES, town);
		String name = table.actors().getFirst().name();
		assertTrue(name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= PartyCodec.ACTOR_NAME_BYTES, name);
		assertFalse(name.contains("�"), name); // never half a character
	}

	@Test
	void atMostSixtyFourActorsGo() {
		List<PartyCodec.Actor> many = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			many.add(actor(i, "Skeever"));
		}
		List<Integer> data = PartyCodec.actors(new PartyCodec.Header(1, 0, 1.0F, false, ""), many);
		assertTrue(data.size() <= PartyCodec.MAX_ACTOR_INTS, data.size() + " ints");
		assertEquals(PartyCodec.MAX_ACTORS, PartyCodec.readActors(data).actors().size());
	}

	@Test
	void brokenTablesAreRefused() {
		List<Integer> good = PartyCodec.actors(new PartyCodec.Header(1, 0, 1.0F, false, "Riverwood"), List.of(actor(5, "Wolf")));
		List<Integer> cut = good.subList(0, good.size() - 1);
		assertThrows(IllegalArgumentException.class, () -> PartyCodec.readActors(cut));
		List<Integer> longer = new ArrayList<>(good);
		longer.add(0);
		assertThrows(IllegalArgumentException.class, () -> PartyCodec.readActors(longer));
		List<Integer> otherVersion = new ArrayList<>(good);
		otherVersion.set(0, 99);
		assertThrows(IllegalArgumentException.class, () -> PartyCodec.readActors(otherVersion));
		// The count after the header (version, world, place, hour, flags, the town's length and its 3 ints).
		List<Integer> tooMany = new ArrayList<>(good);
		tooMany.set(9, 65);
		assertThrows(IllegalArgumentException.class, () -> PartyCodec.readActors(tooMany));
		List<Integer> notANumber = new ArrayList<>(good);
		notANumber.set(12, Float.floatToRawIntBits(Float.NaN)); // the first actor's x
		assertThrows(IllegalArgumentException.class, () -> PartyCodec.readActors(notANumber));
		List<Integer> longName = new ArrayList<>(good);
		longName.set(5, 200); // the town's length
		assertThrows(IllegalArgumentException.class, () -> PartyCodec.readActors(longName));
	}

	@Test
	void sizesAndHealthStayWithinSkyrimsRange() {
		PartyCodec.Actor odd = new PartyCodec.Actor(1, 0, 0, 0, 0, 0, 50.0F, 0.01F, 7.0F, 70000, 0, "Giant");
		PartyCodec.Actor a = PartyCodec.readActors(PartyCodec.actors(new PartyCodec.Header(1, 0, 0, false, ""), List.of(odd))).actors().getFirst();
		assertEquals(12.0F, a.width(), 0.0F);
		assertEquals(0.3F, a.height(), 0.0F);
		assertEquals(1.0F, a.health(), 0.0F);
		assertEquals(70000 & 0xFFFF, a.level());
	}

	@Test
	void itemsComeBackAsTheyWent() {
		List<PartyCodec.Item> items = List.of(new PartyCodec.Item(0x0003AD5B, 2, 0x40 | 3 << 24, 4, 2, true, false, 0x000F1234, "Daedra Heart"),
			new PartyCodec.Item(0x00012EB7, -1, 0x80, 2, 5, false, true, 0x00055555, "Iron Sword"));
		List<PartyCodec.Item> back = PartyCodec.readItems(PartyCodec.items(items));
		assertEquals(items, back);
	}

	@Test
	void absurdItemCountsAreRefused() {
		List<Integer> data = PartyCodec.items(List.of(new PartyCodec.Item(0xF, 5, 0, 0, 0, false, false, 0, "Gold")));
		data.set(3, PartyCodec.MAX_COUNT + 1); // the count
		assertThrows(IllegalArgumentException.class, () -> PartyCodec.readItems(data));
	}

	@Test
	void aNameOnItsOwn() {
		assertEquals("Odahviing", PartyCodec.readName(PartyCodec.name("Odahviing", 24), 24));
		assertEquals("", PartyCodec.readName(PartyCodec.name(null, 24), 24));
		assertThrows(IllegalArgumentException.class, () -> PartyCodec.readName(PartyCodec.name("Odahviing", 24), 4));
	}
}
