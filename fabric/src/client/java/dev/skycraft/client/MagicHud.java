package dev.skycraft.client;

import dev.skycraft.SkyCraft;
import dev.skycraft.magic.MagicHudLayout;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The magic HUD (Shouts, Spells): the selected shout's name over its voice bar and the selected spell's name over the magicka
 * bar, beside the hotbar on its right, drawn with Minecraft's GUI after the hotbar (so it's in the frame Skyrim shows, and
 * hidden with F1 like the rest). {@link MagicClient#hudOps} decides what shows and {@link MagicHudLayout} where; this only
 * draws it.
 */
final class MagicHud {
	private MagicHud() {
	}

	static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "magic"),
			(graphics, delta) -> draw(graphics));
	}

	private static void draw(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance(); // (F1 hides the hotbar's layer and what's attached to it)
		if (minecraft.player == null) {
			return;
		}
		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		for (MagicHudLayout.Op op : MagicClient.hudOps(width, height)) {
			if (op instanceof MagicHudLayout.Fill f) {
				graphics.fill(f.x0(), f.y0(), f.x1(), f.y1(), f.argb());
			} else if (op instanceof MagicHudLayout.Text t) {
				graphics.text(minecraft.font, Component.literal(t.text()), t.x(), t.y(), t.argb());
			}
		}
	}
}
