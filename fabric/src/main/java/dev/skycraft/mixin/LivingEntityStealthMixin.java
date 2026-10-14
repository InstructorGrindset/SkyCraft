package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.skycraft.skooma.SkoomaRules;
import dev.skycraft.skooma.SkyEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Skooma's and Stealth's sneaking: mobs notice a crouching player under either from a shorter distance. Mobs target
 * by this visibility (TargetingConditions.test scales its range by it), which already takes crouching's 0.8,
 * invisibility and mob heads; the effect's level scales it down further (SkoomaRules.stealthScale: 0.6 at I, 0.45 at II).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStealthMixin {
	@ModifyReturnValue(method = "getVisibilityPercent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)D", at = @At("RETURN"))
	private double skycraft$skoomaStealth(double visibility) {
		int level = SkyEffects.stealthLevel((LivingEntity) (Object) this);
		return level > 0 ? visibility * SkoomaRules.stealthScale(level) : visibility;
	}
}
