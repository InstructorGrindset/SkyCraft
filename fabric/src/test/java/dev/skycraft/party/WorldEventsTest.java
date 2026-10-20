package dev.skycraft.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.link.Proto;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldEventsTest {
	private static final PartyRules.Settings S = PartyRules.Settings.DEFAULTS;
	private static final int FIGHTING = Proto.ACTOR_DRAGON | Proto.ACTOR_HOSTILE | Proto.ACTOR_IN_COMBAT;
	private static final int CIRCLING = Proto.ACTOR_DRAGON;
	private static final int DEAD = Proto.ACTOR_DRAGON | Proto.ACTOR_DEAD;

	private static WorldEvents.Seen dragon(int id, int flags) {
		return new WorldEvents.Seen(id, flags, "Mirmulnir");
	}

	@Test
	void aDragonsFightIsToldOnce() {
		WorldEvents w = new WorldEvents();
		assertTrue(w.actors(List.of(dragon(1, CIRCLING)), 0, S).isEmpty());
		List<WorldEvents.Event> e = w.actors(List.of(dragon(1, FIGHTING)), 1000, S);
		assertEquals(1, e.size());
		assertEquals(WorldEvents.Kind.DRAGON_ATTACK, e.getFirst().kind());
		assertEquals("Mirmulnir", e.getFirst().name());
		assertTrue(w.actors(List.of(dragon(1, FIGHTING)), 2000, S).isEmpty(), "still the same fight");
		assertTrue(w.actors(List.of(dragon(1, FIGHTING)), 1000 + S.dragonAttack() * 1000L + 1, S).isEmpty(), "still the same fight, after the cooldown");
	}

	@Test
	void aSecondFightWaitsForTheCooldown() {
		WorldEvents w = new WorldEvents();
		assertEquals(1, w.actors(List.of(dragon(1, FIGHTING)), 0, S).size());
		assertTrue(w.actors(List.of(dragon(1, CIRCLING)), 10_000, S).isEmpty());
		assertTrue(w.actors(List.of(dragon(1, FIGHTING), dragon(2, FIGHTING)), 20_000, S).isEmpty(), "within the cooldown");
		// Those two fights started in the cooldown: they aren't told when it runs out either.
		assertTrue(w.actors(List.of(dragon(1, FIGHTING), dragon(2, FIGHTING)), S.dragonAttack() * 1000L + 1, S).isEmpty());
		assertTrue(w.actors(List.of(dragon(1, CIRCLING)), S.dragonAttack() * 1000L + 2, S).isEmpty());
		assertEquals(1, w.actors(List.of(dragon(1, FIGHTING)), S.dragonAttack() * 1000L + 3, S).size(), "a new fight after the cooldown");
	}

	@Test
	void aDragonSeenAliveThenDeadIsSlain() {
		WorldEvents w = new WorldEvents();
		w.actors(List.of(dragon(1, FIGHTING)), 0, S);
		List<WorldEvents.Event> e = w.actors(List.of(dragon(1, DEAD)), 5000, S);
		assertEquals(1, e.size());
		assertEquals(WorldEvents.Kind.DRAGON_SLAIN, e.getFirst().kind());
		assertTrue(w.actors(List.of(dragon(1, DEAD)), 6000, S).isEmpty(), "once");
		assertTrue(w.actors(List.of(dragon(7, DEAD)), 60_000, S).isEmpty(), "a corpse found isn't a kill");
	}

	@Test
	void otherActorsAndAnOlderPluginsNamedDragons() {
		WorldEvents w = new WorldEvents();
		int bandit = Proto.ACTOR_HOSTILE | Proto.ACTOR_IN_COMBAT;
		assertTrue(w.actors(List.of(new WorldEvents.Seen(3, bandit, "Bandit Chief")), 0, S).isEmpty());
		assertEquals(1, w.actors(List.of(new WorldEvents.Seen(4, bandit, "Frost Dragon")), 10, S).size());
	}

	@Test
	void aDragonThatLeavesTheTableStartsOver() {
		WorldEvents w = new WorldEvents();
		w.actors(List.of(dragon(1, FIGHTING)), 0, S);
		w.actors(List.of(), 1000, S);
		assertTrue(w.actors(List.of(dragon(1, DEAD)), 2000, S).isEmpty(), "died out of sight: not seen alive since it came back");
		assertEquals(1, w.actors(List.of(dragon(1, FIGHTING)), S.dragonAttack() * 1000L + 5, S).size());
	}

	@Test
	void enteringADungeon() {
		WorldEvents w = new WorldEvents();
		assertNull(w.place(Proto.PLACE_DUNGEON, "Embershard Mine", 0, S), "already there when watching starts");
		assertNull(w.place(0, "Riverwood", 1000, S));
		WorldEvents.Event e = w.place(Proto.PLACE_DUNGEON | Proto.PLACE_INTERIOR, "Bleak Falls Barrow", 2000, S);
		assertEquals(WorldEvents.Kind.DUNGEON, e.kind());
		assertEquals("Bleak Falls Barrow", e.name());
		assertNull(w.place(Proto.PLACE_DUNGEON | Proto.PLACE_INTERIOR, "Bleak Falls Barrow", 3000, S), "still in it");
		assertNull(w.place(0, "", 4000, S));
		assertNull(w.place(Proto.PLACE_DUNGEON, "Bleak Falls Barrow", 5000, S), "the same dungeon within its cooldown");
		assertEquals("Bleak Falls Sanctum", w.place(Proto.PLACE_DUNGEON, "Bleak Falls Sanctum", 6000, S).name());
		assertNull(w.place(Proto.PLACE_DUNGEON, "  ", 7000, S), "a dungeon without a name");
		w.place(0, "", 8000, S);
		assertEquals("Bleak Falls Barrow", w.place(Proto.PLACE_DUNGEON, "Bleak Falls Barrow", 2000 + S.dungeon() * 1000L, S).name());
	}

	@Test
	void shouts() {
		WorldEvents w = new WorldEvents();
		assertNull(w.shout(4, 0, 0, "Ulfric", 0, S), "the first sight of the sequence");
		assertNull(w.shout(4, 0, 0, "Ulfric", 100, S));
		WorldEvents.Event e = w.shout(5, Proto.SHOUT_BY_DRAGON, 3, "Mirmulnir", 200, S);
		assertEquals(WorldEvents.Kind.SHOUT, e.kind());
		assertEquals(3, e.words());
		assertEquals(Proto.SHOUT_BY_DRAGON, e.flags());
		assertNull(w.shout(6, 0, 1, "Draugr", 1000, S), "within the cooldown");
		assertEquals("Draugr", w.shout(7, 0, 1, "Draugr", 200 + S.shout() * 1000L, S).name());
	}

	@Test
	void anOlderPluginNeverShouts() {
		WorldEvents w = new WorldEvents();
		for (int t = 0; t < 10; t++) {
			assertNull(w.shout(0, 0, 0, "", t * 100_000L, S));
		}
	}

	@Test
	void resetForgetsWhatWasThereButKeepsTheCooldowns() {
		WorldEvents w = new WorldEvents();
		assertEquals(1, w.actors(List.of(dragon(1, FIGHTING)), 0, S).size());
		w.reset();
		assertTrue(w.actors(List.of(dragon(2, FIGHTING)), 1000, S).isEmpty(), "the cooldown holds over a reset");
		assertNull(w.place(Proto.PLACE_DUNGEON, "Ustengrav", 2000, S), "where the player is after a reset isn't news");
	}
}
