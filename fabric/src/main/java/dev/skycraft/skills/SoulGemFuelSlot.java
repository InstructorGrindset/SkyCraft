package dev.skycraft.skills;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The enchanting table's lapis slot, which also takes a filled soul gem (switch {@code enchanting_skill}): in Skyrim a
 * filled soul gem is what powers an enchantment. One gem stands for one lapis lazuli, so an offer takes as many gems as it
 * would lapis (the vanilla menu counts and uses up whatever is in the slot). mixin.EnchantingLapisSlotMixin puts it in
 * the place of the vanilla slot as the menu is built, on the server and the client alike; everything but what it accepts
 * is the vanilla slot's.
 */
public final class SoulGemFuelSlot extends Slot {
	private final Slot lapis;

	public SoulGemFuelSlot(Slot lapis) {
		super(lapis.container, lapis.getContainerSlot(), lapis.x, lapis.y);
		this.lapis = lapis;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return this.lapis.mayPlace(stack) || SkySkills.soulGemFuel(stack);
	}

	/** The vanilla slot's lapis outline while it's empty. */
	@Override
	public @Nullable Identifier getNoItemIcon() {
		return this.lapis.getNoItemIcon();
	}
}
