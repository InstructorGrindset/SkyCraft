package dev.skycraft.combat;

import dev.skycraft.link.Proto;

/**
 * What Minecraft tells Skyrim about a hit beyond its damage, and about the light the player stands in, for Skyrim's own
 * combat and stealth to take over (sneak attacks, power attacks, light and stealth). Pure: no game, unit tested.
 */
public final class CombatRules {
	/**
	 * Attack strength ({@code Player.getAttackStrengthScale(0.5F)}) over which a swing is fully charged: Minecraft's own
	 * threshold, the one its sprint knockback and critical hits need.
	 */
	public static final float FULL_STRENGTH = 0.9F;

	private CombatRules() {
	}

	/**
	 * A Skyrim power attack: a fully charged melee swing that was a sprint hit or a critical hit (Minecraft only crits a
	 * fully charged swing, so a critical hit always counts).
	 */
	public static boolean powerAttack(float attackStrength, boolean sprinting, boolean critical) {
		return attackStrength > FULL_STRENGTH && (sprinting || critical);
	}

	/**
	 * The Proto.HIT_POWER_ATTACK and Proto.HIT_CROUCHING bits for one tick's hit on a Skyrim actor's stand-in.
	 *
	 * @param melee          the player's own swing landed (not only an arrow, a trident or fire)
	 * @param swingStrength  the attack strength of that swing as it began, 0 when none was seen
	 * @param sprinting      the player was sprinting as the swing began
	 * @param critical       Minecraft made it a critical hit
	 * @param crouching      the player who landed a hit was crouching as it landed
	 * @param powerAttacksOn the power_attacks switch
	 * @param sneakAttacksOn the sneak_attacks switch
	 */
	public static int hitFlags(boolean melee, float swingStrength, boolean sprinting, boolean critical, boolean crouching, boolean powerAttacksOn,
		boolean sneakAttacksOn) {
		int flags = 0;
		if (powerAttacksOn && melee && powerAttack(swingStrength, sprinting, critical)) {
			flags |= Proto.HIT_POWER_ATTACK;
		}
		if (sneakAttacksOn && crouching) {
			flags |= Proto.HIT_CROUCHING;
		}
		return flags;
	}

	/** A Minecraft light level as McState carries it: 0 to 15. */
	public static int lightLevel(int level) {
		return Math.max(0, Math.min(15, level));
	}
}
