package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.skills.SkySkills;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The host's Skyrim Enchanting at the enchanting table. While the menu works out its three offers (slotsChanged, which
 * counts the bookshelves and calls EnchantmentHelper.getEnchantmentCost on the server), the user's Enchanting is set aside
 * for mixin.EnchantingCostMixin to count more bookshelves with. And a shift-clicked filled soul gem goes to the lapis slot
 * as lapis does (SoulGemFuelSlot takes it there); that part is optional: if this Minecraft moves lapis some other way, the
 * gem can still be put in the slot by hand.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantingTableMixin {
	@Inject(method = "slotsChanged", at = @At("HEAD"))
	private void skycraft$enchantingOffers(Container container, CallbackInfo ci) {
		SkySkills.enchantingOffers((EnchantmentMenu) (Object) this);
	}

	@Inject(method = "slotsChanged", at = @At("RETURN"))
	private void skycraft$enchantingOffersDone(Container container, CallbackInfo ci) {
		SkySkills.enchantingOffersDone((EnchantmentMenu) (Object) this);
	}

	@WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"), require = 0)
	private boolean skycraft$soulGemLikeLapis(ItemStack stack, Item item, Operation<Boolean> original) {
		return original.call(stack, item) || item == Items.LAPIS_LAZULI && SkySkills.soulGemFuel(stack);
	}
}
