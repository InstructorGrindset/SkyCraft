package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.skycraft.skills.SkySkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The host's Skyrim Archery: an arrow shot from a bow or crossbow hits harder (SkySkills.archery, once on its base
 * damage). createProjectile makes each arrow a bow or crossbow shoots (a crossbow's own version calls this one for
 * arrows), with its shooter as owner, before it flies; skeletons' and dispensers' arrows are made elsewhere.
 */
@Mixin(ProjectileWeaponItem.class)
public abstract class ArcheryMixin {
	@ModifyReturnValue(method = "createProjectile", at = @At("RETURN"))
	private Projectile skycraft$archery(Projectile projectile) {
		Object weapon = this;
		if ((weapon instanceof BowItem || weapon instanceof CrossbowItem) && projectile instanceof AbstractArrow arrow
			&& arrow.getOwner() instanceof ServerPlayer shooter) {
			SkySkills.archery(arrow, shooter);
		}
		return projectile;
	}
}
