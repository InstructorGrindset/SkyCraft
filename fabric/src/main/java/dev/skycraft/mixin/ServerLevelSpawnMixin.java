package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The mirror never spawns monsters, whatever SPAWN_MONSTERS says (see ServerChunkCacheSpawnMixin). Zombies
 * calling reinforcements, creaking hearts and Nether portals spawning zombified piglins all ask this.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSpawnMixin {
	@ModifyReturnValue(method = "isSpawningMonsters", at = @At("RETURN"))
	private boolean skycraft$noMonstersInMirror(boolean original) {
		return original && !dev.skycraft.SkyDimension.inMirror((ServerLevel) (Object) this);
	}
}
