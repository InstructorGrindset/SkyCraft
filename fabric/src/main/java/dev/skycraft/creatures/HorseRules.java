package dev.skycraft.creatures;

import dev.skycraft.link.Proto;

/**
 * Horses carry over: how a Minecraft horse's stats and the player's Skyrim horse's line up, and how they cross the link
 * (EV_HORSE's packed stats, SkyState's horseInfo). Pure Java, unit tested without Minecraft.
 *
 * <p>A Minecraft horse's movement speed is 0.1125 to 0.3375 (AbstractHorse.generateSpeed: 0.45 x (0.25 + three randoms of
 * a third), 0.225 on average); Skyrim's horses run at their race's speed times SpeedMult (100 for every vanilla horse). So
 * an average Minecraft horse is a SpeedMult 100 Skyrim horse, and the extremes are half again and half as fast.
 */
public final class HorseRules {
	private HorseRules() {
	}

	public static final double SPEED_AVERAGE = 0.225;
	public static final double SPEED_MIN = 0.1125;
	public static final double SPEED_MAX = 0.3375;
	/** A Minecraft horse's jump strength on average (AbstractHorse.generateJumpStrength: 0.4 to 1.0). */
	public static final double JUMP_AVERAGE = 0.7;
	public static final double JUMP_MIN = 0.4;
	public static final double JUMP_MAX = 1.0;
	/** A Minecraft horse's health on average (AbstractHorse.generateMaxHealth: 15 to 30). */
	public static final float HEALTH_AVERAGE = 22.5F;
	public static final float HEALTH_MIN = 15.0F;
	public static final float HEALTH_MAX = 30.0F;
	public static final int SPEED_MULT_MIN = 50;
	public static final int SPEED_MULT_MAX = 150;

	/** Skyrim's SpeedMult (percent) for a Minecraft horse's movement speed. */
	public static int speedMult(double minecraftSpeed) {
		double s = Double.isFinite(minecraftSpeed) ? minecraftSpeed : SPEED_AVERAGE;
		return (int) Math.clamp(Math.round(100.0 * s / SPEED_AVERAGE), SPEED_MULT_MIN, SPEED_MULT_MAX);
	}

	/** A Minecraft horse's movement speed for a Skyrim horse's SpeedMult (percent; 0 when Skyrim didn't say: average). */
	public static double minecraftSpeed(int speedMult) {
		if (speedMult <= 0) {
			return SPEED_AVERAGE;
		}
		return Math.clamp(SPEED_AVERAGE * speedMult / 100.0, SPEED_MIN, SPEED_MAX);
	}

	/** EV_HORSE HORSE_ARRIVE's weapon: speed x 10000 in bits 0-11, maximum health in 12-19, health in 20-27 (rounded, clamped). */
	public static int packStats(double speed, float maxHealth, float health) {
		int s = (int) Math.clamp(Math.round(speed * 10000.0), 0, 0xFFF);
		int m = (int) Math.clamp(Math.round(maxHealth), 0, 0xFF);
		int h = (int) Math.clamp(Math.round(health), 0, 0xFF);
		return s | m << Proto.HORSE_STAT_MAX_HEALTH_SHIFT | h << Proto.HORSE_STAT_HEALTH_SHIFT;
	}

	public static double unpackSpeed(int stats) {
		return (stats & 0xFFF) / 10000.0;
	}

	public static int unpackMaxHealth(int stats) {
		return stats >>> Proto.HORSE_STAT_MAX_HEALTH_SHIFT & 0xFF;
	}

	public static int unpackHealth(int stats) {
		return stats >>> Proto.HORSE_STAT_HEALTH_SHIFT & 0xFF;
	}

	/** SkyState horseInfo: the player's horse would come along into another dimension (near, and not in Minecraft already). */
	public static boolean comesAlong(int horse, int info) {
		return horse != 0 && (info & Proto.HORSE_INFO_NEAR) != 0 && (info & Proto.HORSE_INFO_AWAY) == 0;
	}

	/** SkyState horseInfo: Skyrim carried out an arrival in the last two seconds. */
	public static boolean arrived(int info) {
		return (info & Proto.HORSE_INFO_ARRIVED) != 0;
	}

	/** SkyState horseInfo: Skyrim's horse is in Minecraft (disabled there). */
	public static boolean away(int info) {
		return (info & Proto.HORSE_INFO_AWAY) != 0;
	}

	/** SkyState horseInfo: the Skyrim horse's SpeedMult, percent (0: not said). */
	public static int infoSpeedMult(int info) {
		return info >>> Proto.HORSE_INFO_SPEED_SHIFT & 0xFF;
	}

	/** SkyState horseInfo: the Skyrim horse's health, a share of its maximum (1 when not said). */
	public static float infoHealthShare(int info, int horse) {
		int percent = info >>> Proto.HORSE_INFO_HEALTH_SHIFT & 0xFF;
		return horse == 0 || percent == 0 ? 1.0F : Math.clamp(percent / 100.0F, 0.05F, 1.0F);
	}

	/** The Minecraft horse's health for a share of its maximum: never under a heart, so it never comes over dying. */
	public static float health(float maxHealth, float share) {
		return Math.clamp(maxHealth * share, Math.min(2.0F, maxHealth), maxHealth);
	}

	/** Jump strength kept within a Minecraft horse's own (a stored one, or the average when there's none). */
	public static double jump(double stored) {
		return Double.isFinite(stored) && stored > 0.0 ? Math.clamp(stored, JUMP_MIN, JUMP_MAX) : JUMP_AVERAGE;
	}

	/** Maximum health kept within a Minecraft horse's own (a stored one, or the average). */
	public static float maxHealth(float stored) {
		return Float.isFinite(stored) && stored > 0.0F ? Math.clamp(stored, HEALTH_MIN, HEALTH_MAX) : HEALTH_AVERAGE;
	}
}
