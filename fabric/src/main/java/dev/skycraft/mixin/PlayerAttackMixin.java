package dev.skycraft.mixin;

import dev.skycraft.combat.SkyrimActorEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Power attacks on Skyrim's NPCs: how charged a player's swing at a Skyrim actor's stand-in is, and whether they sprint,
 * read as the attack begins. Minecraft resets the attack strength before the hit lands, so the stand-in can't read it
 * itself; it decides with this once the tick's hits are in (CombatRules.hitFlags).
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void skycraft$noteSwingAtSkyrim(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (target instanceof SkyrimActorEntity proxy && !self.level().isClientSide()) {
			proxy.noteSwing(self.getAttackStrengthScale(0.5F), self.isSprinting());
		}
	}
}
