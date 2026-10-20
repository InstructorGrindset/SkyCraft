package dev.skycraft.mixin;

import dev.skycraft.skills.SkyAlchemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The host's Skyrim Alchemy: a potion they take out of a brewing stand lasts longer (SkyAlchemy.taking). Every way a
 * player takes from a slot (a click, a shift-click, a number key, a drop, a double-click gathering) asks mayPickup first,
 * so the potion is done while it's still in the stand's bottle slot (0 to 2; 3 is the ingredient, 4 the blaze powder).
 * Hoppers take without asking: their potions stay as brewed.
 */
@Mixin(Slot.class)
public abstract class BrewingTakeMixin {
	@Inject(method = "mayPickup", at = @At("HEAD"))
	private void skycraft$alchemy(Player player, CallbackInfoReturnable<Boolean> cir) {
		Slot self = (Slot) (Object) this;
		if (self.container instanceof BrewingStandBlockEntity && self.getContainerSlot() < 3) {
			SkyAlchemy.taking(player, self.getItem());
		}
	}
}
