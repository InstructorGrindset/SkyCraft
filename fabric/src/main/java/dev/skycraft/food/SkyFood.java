package dev.skycraft.food;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

/**
 * Skyrim food fills hunger (switch skyrim_food_hunger): food the player eats in Skyrim (from Skyrim's inventory while
 * Skyrim has the player, riding for one) fills Minecraft's hunger and saturation as the Minecraft food most like it would.
 * Skyrim's plugin decides how filling it is from the food's name (skse/src/FoodValues.h) and sends IN_SKYRIM_FOOD; the
 * host's client hands it to the server, a guest's sends it in a SkyNet.SkyrimFood packet. Server thread only.
 */
public final class SkyFood {
	/** Minecraft's own most filling food is 10 points (rabbit stew); a Skyrim one never fills more than a full bar. */
	public static final int MAX_NUTRITION = 20;
	/** Golden carrot's modifier is the highest of Minecraft's foods (1.2). */
	public static final float MAX_SATURATION = 1.2F;
	private static int logs;

	private SkyFood() {
	}

	/**
	 * The Skyrim player ate a food in Skyrim: {@code nutrition} food points at Minecraft's saturation modifier
	 * {@code saturation} (saturation gained is points x modifier x 2, as for Minecraft's own food), {@code formId} and
	 * {@code kind} (Proto.FOOD_KINDS) for the log.
	 */
	public static void eat(ServerPlayer player, int nutrition, float saturation, int formId, int kind) {
		if (!Features.on(Features.Feature.SKYRIM_FOOD_HUNGER) || player.isRemoved()) {
			return;
		}
		int points = Math.max(0, Math.min(MAX_NUTRITION, nutrition));
		float modifier = Float.isFinite(saturation) ? Math.max(0.0F, Math.min(MAX_SATURATION, saturation)) : 0.0F;
		if (points == 0) {
			return;
		}
		FoodData food = player.getFoodData();
		int levelBefore = food.getFoodLevel();
		float saturationBefore = food.getSaturationLevel();
		food.eat(points, modifier);
		if (logs < 20) {
			logs++;
			String what = kind >= 0 && kind < Proto.FOOD_KINDS.size() ? Proto.FOOD_KINDS.get(kind) : "food";
			SkyCraft.LOG.info("SkyCraft-food: {} ate Skyrim {} {}: hunger {} -> {}, saturation {} -> {}", player.getName().getString(), what,
				String.format("%08X", formId), levelBefore, food.getFoodLevel(), String.format("%.1f", saturationBefore),
				String.format("%.1f", food.getSaturationLevel()));
		}
	}
}
