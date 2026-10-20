package dev.skycraft.blessings;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Shrine blessings, server side (switch {@code shrine_blessings}, table in {@link Blessings}). Skyrim's player got a
 * shrine's blessing (Proto.IN_BLESSING: the host's through the client's InputBridge, a guest's through SkyNet.Blessing);
 * Minecraft's player gets the fitting effect for as long as the blessing has left in real time. With the switch off,
 * nothing happens. The effect is ambient, like a beacon's: fainter particles.
 */
public final class SkyBlessings {
	private static int logged;

	private SkyBlessings() {
	}

	/** Gives {@code player} the effect for blessing {@code kind} (Proto.BLESSING_*): {@code hours} of game time left at {@code timescale}. */
	public static void give(ServerPlayer player, int kind, float hours, float timescale) {
		Blessings.Gift gift = Blessings.gift(kind);
		if (!Features.on(Features.Feature.SHRINE_BLESSINGS) || gift == null || !player.isAlive()) {
			return;
		}
		int ticks = Blessings.ticks(hours, timescale);
		Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(gift.effect())).orElse(null);
		if (effect == null || ticks <= 0) {
			return;
		}
		player.addEffect(new MobEffectInstance(effect, ticks, gift.amplifier(), true, true, true));
		player.sendOverlayMessage(Component.literal("Blessing of " + gift.god() + ": ").append(effect.value().getDisplayName()));
		if (logged++ < 20) {
			SkyCraft.LOG.info("SkyCraft-blessing: {} has the blessing of {}: {} for {} seconds ({} game hours at timescale {})", player.getName().getString(),
				gift.god(), gift.effect(), ticks / 20, String.format("%.1f", hours), String.format("%.0f", timescale));
		}
	}
}
