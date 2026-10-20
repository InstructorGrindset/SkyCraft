package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.skycraft.skills.SkillRules;
import dev.skycraft.skills.SkySkills;
import dev.skycraft.skooma.SkoomaRules;
import dev.skycraft.skooma.SkyEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Skooma's and Stealth's sneaking, and the host's Skyrim Sneak: mobs notice a crouching player from a shorter distance.
 * Mobs target by this visibility (TargetingConditions.test scales its range by it), which already takes crouching's 0.8,
 * invisibility and mob heads; the effect's level scales it down further (SkoomaRules.stealthScale: 0.6 at I, 0.45 at II),
 * and Sneak on top of that (SkillRules.sneakScale: 0.6 at Sneak 100 by default), never under SkillRules.MIN_STEALTH in all.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStealthMixin {
	@ModifyReturnValue(method = "getVisibilityPercent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)D", at = @At("RETURN"))
	private double skycraft$skoomaStealth(double visibility) {
		LivingEntity self = (LivingEntity) (Object) this;
		int level = SkyEffects.stealthLevel(self);
		double scale = SkillRules.stealth(level > 0 ? SkoomaRules.stealthScale(level) : 1.0, SkySkills.sneakScale(self));
		return scale < 1.0 ? visibility * scale : visibility;
	}
}
