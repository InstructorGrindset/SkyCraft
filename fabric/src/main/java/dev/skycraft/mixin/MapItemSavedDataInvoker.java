package dev.skycraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A map's decorations, as vanilla adds its banners' (maps.SkyMaps puts Skyrim's map markers on maps with them). Adding one
 * that's already there unchanged changes nothing; one off the map is taken off.
 */
@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataInvoker {
	@Invoker("addDecoration")
	void skycraft$addDecoration(Holder<MapDecorationType> type, @Nullable LevelAccessor level, String id, double x, double z, double rotation,
		@Nullable Component name);

	@Invoker("removeDecoration")
	void skycraft$removeDecoration(String id);
}
