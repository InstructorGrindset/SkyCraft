package dev.skycraft.client;

import dev.skycraft.config.Features;
import dev.skycraft.farming.FarmRules;
import dev.skycraft.link.Proto;
import dev.skycraft.net.SkyNet;
import dev.skycraft.world.SkyClip;
import dev.skycraft.world.SkyRay;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Skyrim farming, the hoeing player's side (switch {@code skyrim_farming}). A right click with a hoe on Skyrim's soil (the
 * crosshair's SkyrimHitResult on grass, dirt or mud facing up, FarmRules) works out where the farmland goes from Skyrim's
 * geometry, which only this client has, and asks the server to till it (SkyNet.FarmTill, farming.SkyFarming). The click
 * is taken, as a hoe on a dirt block takes it. Anywhere else, or while Skyrim's crosshair is on a plant it can harvest
 * (SkyState aim), the click goes on as before: the hoe does nothing there, so the click activates what Skyrim's
 * crosshair is on (click to activate), and the plant is harvested.
 */
public final class FarmingClient {
	private FarmingClient() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() || !(hit instanceof SkyClip.SkyrimHitResult sky) || player.isSpectator() || !player.getItemInHand(hand).is(ItemTags.HOES)) {
				return InteractionResult.PASS;
			}
			if (!Features.on(Features.Feature.SKYRIM_FARMING) || !dev.skycraft.SkyDimension.inMirror(level) || (SkyClient.sky().aim & Proto.AIM_HARVEST) != 0) {
				return InteractionResult.PASS;
			}
			SkyRay.Hit h = sky.hit;
			if (!FarmRules.tillable(h.tri(), h.ny())) {
				return InteractionResult.PASS;
			}
			FarmRules.Till till = FarmRules.till(h.x(), h.y(), h.z(), h.tri().heightAt(Math.floor(h.x()) + 0.5, Math.floor(h.z()) + 0.5));
			BlockPos farm = new BlockPos(till.x(), till.y(), till.z());
			if (!level.getBlockState(farm).isAir() || !level.getBlockState(farm.above()).isAir() || !ClientPlayNetworking.canSend(SkyNet.FarmTill.TYPE)) {
				return InteractionResult.PASS;
			}
			ClientPlayNetworking.send(new SkyNet.FarmTill(SkyDigClient.world(), farm, till.digAbove(), hand == InteractionHand.MAIN_HAND));
			return InteractionResult.SUCCESS;
		});
	}
}
