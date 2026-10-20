package dev.skycraft.creatures;

import dev.skycraft.link.Proto;

/**
 * Who goes for whom between Minecraft's creatures and Skyrim's actors, and when Skyrim's undead burn: the decisions of
 * {@link SkyCreatures}, pure Java (no Minecraft classes) so they're unit tested without Minecraft.
 *
 * <p>Skyrim says what each actor is in the actor table's flags (Proto.ACTOR_*): hostile to the player, fighting, dead,
 * essential, and from its race light-shy or undead (skse/src/CreatureKinds.h).
 */
public final class CreatureRules {
	private CreatureRules() {
	}

	/** How far (blocks) a Minecraft monster looks for a Skyrim actor to go for when it has nothing else to do. */
	public static final double MONSTER_RANGE = 16.0;
	/** A monster after a player turns on a Skyrim actor this close instead, if the player is further off by... */
	public static final double MONSTER_CLOSE = 6.0;
	/** ...at least this much more (blocks). */
	public static final double MONSTER_PREFERS_BY = 4.0;
	/** How far (blocks) from its owner a tamed wolf goes for the owner's enemies, as Minecraft's wolves keep near their owner. */
	public static final double WOLF_RANGE = 16.0;
	/** Minecraft's melee monsters strike once a second (MeleeAttackGoal's cooldown); a stand-in takes one melee hit per attacker this often. */
	public static final int MELEE_COOLDOWN_TICKS = 20;
	/** Skyrim's daylight (game hours): the sun is up enough to burn from dawn to dusk. */
	public static final float DAWN = 6.0F;
	public static final float DUSK = 19.5F;
	/**
	 * Chance a zombie in full daylight catches fire within a second: Minecraft rolls {@code random * 30 < (brightness - 0.4) * 2}
	 * every tick (Mob.isSunBurnTick, brightness 1 in the open), 4% a tick, so 1 - 0.96^20 a second.
	 */
	public static final double SUN_BURN_PER_SECOND = 1.0 - Math.pow(1.0 - (1.0 - 0.4) * 2.0 / 30.0, 20.0);
	/** Seconds a zombie burns once the sun sets it alight (Zombie.aiStep's igniteForSeconds). */
	public static final float SUN_BURN_SECONDS = 8.0F;

	public static boolean dead(int actorFlags) {
		return (actorFlags & Proto.ACTOR_DEAD) != 0;
	}

	public static boolean undead(int actorFlags) {
		return (actorFlags & Proto.ACTOR_UNDEAD) != 0;
	}

	public static boolean lightShy(int actorFlags) {
		return (actorFlags & Proto.ACTOR_LIGHT_SHY) != 0;
	}

	/**
	 * A Minecraft monster would go for this Skyrim actor: anyone alive, people and creatures alike, except that Minecraft's
	 * undead leave Skyrim's undead (draugr, skeletons, vampires) be, as their own kind.
	 */
	public static boolean monsterTargets(boolean monsterUndead, int actorFlags) {
		return !dead(actorFlags) && !(monsterUndead && undead(actorFlags));
	}

	/** A tamed wolf goes for this Skyrim actor: alive, hostile to the player and fighting (its owner's enemy in the fight). */
	public static boolean wolfTargets(int actorFlags) {
		return !dead(actorFlags) && (actorFlags & Proto.ACTOR_HOSTILE) != 0 && (actorFlags & Proto.ACTOR_IN_COMBAT) != 0;
	}

	/** What a Minecraft creature is after right now, as far as choosing a Skyrim actor for it goes. */
	public enum Current {
		/** Nothing (or a stand-in whose actor is gone or dead). */
		NOTHING,
		/** A player. */
		PLAYER,
		/** A Skyrim actor's stand-in, or another creature: it keeps that. */
		OTHER
	}

	/**
	 * Whether a monster turns on the nearest Skyrim actor it would go for, {@code actorDistance} blocks off: with nothing to do
	 * when one is in {@link #MONSTER_RANGE}, and from a player {@code playerDistance} blocks off only when the actor is right in
	 * front of it ({@link #MONSTER_CLOSE}) and the player clearly further ({@link #MONSTER_PREFERS_BY}). Monsters still prefer
	 * the player otherwise, as Minecraft's own targeting does.
	 */
	public static boolean monsterSwitches(Current current, double actorDistance, double playerDistance) {
		if (!(actorDistance >= 0.0)) {
			return false;
		}
		return switch (current) {
			case NOTHING -> actorDistance <= MONSTER_RANGE;
			case PLAYER -> actorDistance <= MONSTER_CLOSE && actorDistance + MONSTER_PREFERS_BY < playerDistance;
			case OTHER -> false;
		};
	}

	/** Whether a tamed wolf goes for the nearest enemy {@code actorToOwner} blocks from its owner: only when it has nothing else to do. */
	public static boolean wolfSwitches(Current current, double actorToOwner) {
		return current == Current.NOTHING && actorToOwner >= 0.0 && actorToOwner <= WOLF_RANGE;
	}

	/**
	 * Whether to walk a creature straight at its Skyrim target. The mirror's ground is Skyrim's collision, not blocks, so
	 * Minecraft's path finding finds no way there; a creature whose navigation gave up is steered at its target until it's
	 * in reach. Ranged attackers (bows, crossbows) only close in from past {@code rangedReach}.
	 */
	public static boolean steer(double distance, double meleeReach, boolean ranged, double rangedReach, boolean navigating) {
		if (navigating) {
			return false;
		}
		return distance > (ranged ? rangedReach : meleeReach);
	}

	/** Skyrim's sun is up at this game hour (0-24). */
	public static boolean daylight(float hour) {
		return hour >= DAWN && hour < DUSK;
	}

	/**
	 * Whether an undead actor's stand-in catches fire this second, as a Minecraft zombie in daylight does: undead, not
	 * essential (a quest's vampire, a follower), outdoors in Skyrim's daylight with open sky over it (no Minecraft block, no
	 * Skyrim roof), not wet (water, rain) and not burning already; then {@link #SUN_BURN_PER_SECOND}. {@code roll} is a random
	 * number in [0, 1).
	 */
	public static boolean sunBurns(int actorFlags, boolean outdoors, float hour, boolean skyOpen, boolean wet, boolean burning, double roll) {
		if (!undead(actorFlags) || dead(actorFlags) || (actorFlags & Proto.ACTOR_ESSENTIAL) != 0) {
			return false;
		}
		if (!outdoors || !daylight(hour) || !skyOpen || wet || burning) {
			return false;
		}
		return roll < SUN_BURN_PER_SECOND;
	}

	/** EV_MOB MOB_HIT's flags: the hit's Proto.HIT_* flags and its Proto.MOB_BY_* attacker. */
	public static int hitFields(int hitFlags, int attacker) {
		return Proto.MOB_HIT | (hitFlags & 0xFF) << Proto.MOB_HIT_FLAGS_SHIFT | (attacker & 0xF) << Proto.MOB_ATTACKER_SHIFT;
	}

	/** EV_MOB MOB_NEAR's flags: the creature's Proto.MOB_UNDEAD / MOB_PLAYERS_WOLF traits. */
	public static int nearFields(int traits) {
		return Proto.MOB_NEAR | (traits & 0xFF) << Proto.MOB_TRAITS_SHIFT;
	}

	/** Minecraft damage from a Skyrim actor's swing (kInMobHit's b, x 100), never negative, at most 50 (a sanity cap). */
	public static float swingDamage(int hundredths) {
		return Math.clamp(hundredths / 100.0F, 0.0F, 50.0F);
	}
}
