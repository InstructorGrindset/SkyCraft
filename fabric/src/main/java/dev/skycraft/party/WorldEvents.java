package dev.skycraft.party;

import dev.skycraft.link.Proto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Shared world events (switch shared_world_events): what in one player's Skyrim is worth telling everyone else in the
 * Minecraft world, worked out from what that Skyrim reports, with each kind's cooldown ({@link PartyRules.Settings}).
 * One of these per Skyrim: the host's (SharedWorldEvents reads its actor table, place and shouts) and each guest's
 * dragons (their client watches its own actor table). Times are milliseconds on any steady clock. Pure Java.
 *
 * <ul>
 * <li>A dragon attack: a dragon in the actor table turns hostile and in combat. Told once a fight (a dragon that stays
 * in the fight isn't told again), and not again for that Skyrim within {@code dragon_attack_cooldown}.
 * <li>A dragon slain: a dragon seen alive is dead.
 * <li>A dungeon: the player goes into a dungeon (SkyState's place flags) whose name is known; the same dungeon is told
 * once in {@code dungeon_cooldown}. Where the player already is when this starts watching isn't news.
 * <li>A shout: SkyState's shout sequence moved (the player, or an actor near them, shouted).
 * </ul>
 */
public final class WorldEvents {
	public enum Kind {
		DRAGON_ATTACK, DRAGON_SLAIN, DUNGEON, SHOUT
	}

	/**
	 * Something to tell. {@code name}: the dragon's, the dungeon's, or who shouted (as the caller named them);
	 * {@code flags}: a shout's Proto.SHOUT_* bits; {@code words}: its words of power (0 unknown).
	 */
	public record Event(Kind kind, String name, int flags, int words) {
	}

	/** One actor of an actor table, as much of it as the events need. */
	public record Seen(int formId, int flags, String name) {
		boolean dragon() {
			return PartyRules.dragon(this.flags, this.name);
		}

		boolean dead() {
			return (this.flags & Proto.ACTOR_DEAD) != 0;
		}

		boolean fighting() {
			return (this.flags & Proto.ACTOR_HOSTILE) != 0 && (this.flags & Proto.ACTOR_IN_COMBAT) != 0 && !this.dead();
		}
	}

	private static final long NEVER = Long.MIN_VALUE;
	/** Dungeons told are remembered at most this many (the oldest go). */
	private static final int MAX_DUNGEONS = 64;

	/** Dragons in the latest table: alive or not. */
	private final Map<Integer, Boolean> dragons = new HashMap<>();
	/** Dragons in a fight that was seen start (told or not): not told again until they've left it. */
	private final Set<Integer> fighting = new HashSet<>();
	/** Dungeons told, and when. */
	private final Map<String, Long> dungeons = new HashMap<>();
	private long lastAttack = NEVER, lastSlain = NEVER, lastShout = NEVER;
	private boolean placeKnown;
	private @Nullable String inDungeon;
	private int shoutSeq = -1;

	/** Starts watching over (the link went away, the player left the mirror): what's there next time isn't news. The cooldowns hold. */
	public void reset() {
		this.dragons.clear();
		this.fighting.clear();
		this.placeKnown = false;
		this.inDungeon = null;
		this.shoutSeq = -1;
	}

	/** A fresh read of the actor table at {@code now}: dragon attacks and dragons slain to tell. */
	public List<Event> actors(List<Seen> table, long now, PartyRules.Settings s) {
		List<Event> out = new ArrayList<>();
		Set<Integer> present = new HashSet<>();
		for (Seen a : table) {
			if (!a.dragon()) {
				continue;
			}
			present.add(a.formId());
			Boolean wasAlive = this.dragons.put(a.formId(), !a.dead());
			if (a.dead()) {
				this.fighting.remove(a.formId());
				if (Boolean.TRUE.equals(wasAlive) && ready(this.lastSlain, s.dragonSlain(), now)) {
					this.lastSlain = now;
					out.add(new Event(Kind.DRAGON_SLAIN, a.name(), 0, 0));
				}
				continue;
			}
			if (!a.fighting()) {
				this.fighting.remove(a.formId());
			} else if (this.fighting.add(a.formId()) && ready(this.lastAttack, s.dragonAttack(), now)) {
				this.lastAttack = now;
				out.add(new Event(Kind.DRAGON_ATTACK, a.name(), 0, 0));
			}
		}
		// A dragon out of the table (out of range, or gone) is forgotten; back in a fight, it's a new one.
		this.dragons.keySet().retainAll(present);
		this.fighting.retainAll(present);
		return out;
	}

	/** SkyState's place flags and town (the location's name) at {@code now}: a dungeon entered, or null. */
	public @Nullable Event place(int place, String town, long now, PartyRules.Settings s) {
		String name = (place & Proto.PLACE_DUNGEON) != 0 && town != null && !town.isBlank() ? town.strip() : null;
		boolean first = !this.placeKnown;
		String before = this.inDungeon;
		this.placeKnown = true;
		this.inDungeon = name;
		if (first || name == null || name.equals(before)) {
			return null;
		}
		Long told = this.dungeons.get(name);
		if (told != null && !ready(told, s.dungeon(), now)) {
			return null;
		}
		if (this.dungeons.size() >= MAX_DUNGEONS) {
			this.dungeons.values().stream().min(Long::compare).ifPresent(oldest -> this.dungeons.values().remove(oldest));
		}
		this.dungeons.put(name, now);
		return new Event(Kind.DUNGEON, name, 0, 0);
	}

	/** SkyState's shout sequence and its last shout ({@code who} named by the caller) at {@code now}: a shout to tell, or null. */
	public @Nullable Event shout(int seq, int flags, int words, String who, long now, PartyRules.Settings s) {
		int before = this.shoutSeq;
		this.shoutSeq = seq;
		if (before < 0 || seq == before || !ready(this.lastShout, s.shout(), now)) {
			return null;
		}
		this.lastShout = now;
		return new Event(Kind.SHOUT, who, flags, words);
	}

	private static boolean ready(long last, int cooldownSeconds, long now) {
		return last == NEVER || now - last >= cooldownSeconds * 1000L;
	}
}
