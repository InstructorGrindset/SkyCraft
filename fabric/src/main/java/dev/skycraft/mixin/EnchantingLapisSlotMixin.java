package dev.skycraft.mixin;

import dev.skycraft.skills.SoulGemFuelSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A filled soul gem stands in for lapis lazuli at the enchanting table (switch {@code enchanting_skill}): the second slot an
 * EnchantmentMenu adds is its lapis slot, and a SoulGemFuelSlot takes its place, accepting what it does and a filled soul
 * gem. Every other menu's slots are left alone.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class EnchantingLapisSlotMixin {
	@ModifyVariable(method = "addSlot", at = @At("HEAD"), argsOnly = true)
	private Slot skycraft$soulGemFuel(Slot slot) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		return self instanceof EnchantmentMenu && self.slots.size() == 1 ? new SoulGemFuelSlot(slot) : slot;
	}
}
