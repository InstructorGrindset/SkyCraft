package dev.skycraft.farming;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.config.Features;
import dev.skycraft.world.SkyDig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Skyrim farming, server side (switch {@code skyrim_farming}, rules in {@link FarmRules}). The client that hoed Skyrim's
 * soil knows Skyrim's geometry there and works out the farmland's cell (client FarmingClient); this checks the request
 * and makes it so, as Minecraft's hoe does on a dirt block: the cell (and the one over it, when the ground reaches into
 * it) is dug out of Skyrim's geometry, a farmland block goes in, the hoe sounds and wears. From then on it's Minecraft's
 * farmland: seeds, potatoes and carrots plant on it, crops grow by Minecraft's random ticks and light (the mirror's sky
 * light reaches it), water within four blocks keeps it moist (so does Skyrim's rain, weather sync), it dries back to dirt
 * when nothing grows on it, and trampling it breaks it.
 *
 * <p>What already worked without this: dig into Skyrim's grass and the dirt blocks it reveals can be hoed like any; and
 * Skyrim's own crops (wheat, cabbages, potatoes, leeks, gourds, and the other plants of Skyrim's farms and wilds) are
 * Skyrim flora, harvested by activating them (G, or a right click Minecraft has no use for: click to activate), and the
 * produce converts like any loot (skycraft-items.txt: wheat to wheat, potatoes to potatoes, cabbages and leeks to
 * beetroot, ...). A hoe pointed at one of those plants still harvests it: Skyrim says when its crosshair is on one
 * (SkyState aim), and then the hoe leaves the ground alone.
 */
public final class SkyFarming {
	private static final double REACH = 8.0;
	private static int logged;

	private SkyFarming() {
	}

	/**
	 * A player hoed Skyrim's soil: farmland at {@code farm} in Skyrim world {@code world} (its cell dug out of Skyrim's
	 * geometry first), with the cell over it dug too when {@code digAbove}. Server thread.
	 */
	public static void till(ServerPlayer player, int world, BlockPos farm, boolean digAbove, InteractionHand hand) {
		ServerLevel level = player.level();
		ItemStack hoe = player.getItemInHand(hand);
		if (!Features.on(Features.Feature.SKYRIM_FARMING) || !SkyDimension.inMirror(player) || !SkyDig.destruction || player.isSpectator() || !player.mayBuild()
			|| !hoe.is(ItemTags.HOES) || !level.isLoaded(farm)) {
			return;
		}
		if (player.getEyePosition().distanceToSqr(farm.getX() + 0.5, farm.getY() + 0.5, farm.getZ() + 0.5) > REACH * REACH) {
			return;
		}
		BlockPos above = farm.above();
		if (!level.getBlockState(farm).isAir() || !level.getBlockState(above).isAir() || occupied(level, farm)) {
			return; // a Minecraft block is there, or someone stands in it
		}
		if (dev.skycraft.breach.SkyBreach.claim(level, world, farm)) {
			return; // a breach antechamber's cell (built whole the first time it's reached)
		}
		SkyDig.markDug(level, world, farm);
		if (digAbove) {
			SkyDig.markDug(level, world, above);
		}
		level.setBlock(farm, Blocks.FARMLAND.defaultBlockState(), 3);
		level.playSound(null, farm, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		if (!player.isCreative()) {
			hoe.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
		if (logged++ < 20) {
			SkyCraft.LOG.info("SkyCraft-farm: {} tilled Skyrim's soil into farmland at {}{}", player.getName().getString(), farm.toShortString(),
				digAbove ? " (the ground over it dug too)" : "");
		}
	}

	/** Whether a player (not a spectator) is in the farmland's cell or the one over it. */
	private static boolean occupied(ServerLevel level, BlockPos farm) {
		AABB cells = new AABB(farm.getX(), farm.getY(), farm.getZ(), farm.getX() + 1, farm.getY() + 2, farm.getZ() + 1);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.getBoundingBox().intersects(cells)) {
				return true;
			}
		}
		return false;
	}
}
