package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.skycraft.maps.SkyMaps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft maps draw Skyrim (skyrim_maps, maps.SkyMaps). Vanilla's update of a held map works out each pixel's colour
 * from its column's highest block and stores it with MapItemSavedData.updateColor: that one call is where a pixel whose
 * column has no block above Skyrim's ground gets Skyrim's colour instead (everything vanilla decided, its block search,
 * its shading, its water, stays as it was for every other pixel). At the update's start, Skyrim's map markers near the
 * player go on the map.
 */
@Mixin(MapItem.class)
public abstract class MapItemMixin {
	@WrapOperation(
		method = "update",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;updateColor(IIB)Z")
	)
	private boolean skycraft$skyrimGround(MapItemSavedData data, int x, int z, byte color, Operation<Boolean> original, @Local(argsOnly = true) Entity viewer) {
		return original.call(data, x, z, SkyMaps.pixel(viewer.level(), data, x, z, color));
	}

	@Inject(method = "update", at = @At("HEAD"))
	private void skycraft$skyrimMarkers(CallbackInfo ci, @Local(argsOnly = true) Entity viewer, @Local(argsOnly = true) MapItemSavedData data) {
		SkyMaps.beforeUpdate(viewer.level(), viewer, data);
	}
}
