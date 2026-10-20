package dev.skycraft.mixin;

import dev.skycraft.skills.SkySkills;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The host's Skyrim Enchanting: an enchanting table's offers count more bookshelves than stand round it
 * (SkySkills.bookshelves, up to vanilla's 15). getEnchantmentCost(random, slot, bookshelves, item) is where the count
 * becomes the offers' levels; only an enchanting table working out its offers for the host (mixin.EnchantingTableMixin)
 * gets more, every other caller what it asked for.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantingCostMixin {
	@ModifyVariable(method = "getEnchantmentCost", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private static int skycraft$enchantingSkill(int bookshelves) {
		return SkySkills.bookshelves(bookshelves);
	}
}
