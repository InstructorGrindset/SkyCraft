package dev.skycraft.mixin;

import dev.skycraft.skills.SkySkills;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The host's Skyrim Smithing: anvil repairs and combining cost fewer XP levels (SkySkills.anvilCost). Once vanilla has
 * worked out the result and its cost, the cost comes down; what's taken from the player when they take the result is
 * that cost. Only a repair or combination (something in the second slot) that has a result: a rename alone costs what it
 * did, and a "Too Expensive!" one stays too expensive. The client works the cost out too (its screen shows its own), with
 * the levels its own server read, so the host's screen shows what the server takes.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilSmithingMixin {
	@Shadow
	@Final
	private DataSlot cost;

	@Inject(method = "createResult", at = @At("RETURN"))
	private void skycraft$smithing(CallbackInfo ci) {
		AnvilMenu self = (AnvilMenu) (Object) this;
		int cost = this.cost.get();
		// Slot 1 is the anvil's second input, slot 2 its result.
		if (cost <= 1 || !self.getSlot(1).hasItem() || !self.getSlot(2).hasItem()) {
			return;
		}
		int cheaper = SkySkills.anvilCost(self, cost);
		if (cheaper != cost) {
			this.cost.set(cheaper);
		}
	}
}
