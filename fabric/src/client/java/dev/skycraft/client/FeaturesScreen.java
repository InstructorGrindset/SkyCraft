package dev.skycraft.client;

import dev.skycraft.config.FeatureAreas;
import dev.skycraft.config.Features;
import dev.skycraft.config.Features.Feature;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * The SkyCraft settings screen: every feature switch ({@link Features}) as an On/Off button, under a header for its
 * part of the game ({@link FeatureAreas}), its {@code about} text as the tooltip. Opened by the pause menu's
 * "SkyCraft settings" button, beside "Skyrim destruction" in its top left corner. It looks like Minecraft's own option
 * screens because it is one: vanilla's option list (scrolling) under the title, Done at the bottom.
 *
 * <p>A click saves the switch in skycraft-features.txt and counts at once, except the switches that
 * {@link Features#needsRestart}, which the screen marks "(restart)". The host's file decides for everyone in their
 * world, so in a friend's world the buttons only show this player's own switches, greyed out, with a note.
 */
public final class FeaturesScreen extends OptionsSubScreen {
	/** A full width row, as vanilla's big options. */
	private static final int ROW_WIDTH = 310;
	private static final int ROW_HEIGHT = 20;

	/** Whether this player's own world is open (their switches decide): false in a friend's world. */
	private final boolean host;

	private FeaturesScreen(Screen lastScreen, Options options, boolean host) {
		super(lastScreen, options, Component.literal("SkyCraft settings"));
		this.host = host;
	}

	/** The pause menu's "SkyCraft settings" button, right of "Skyrim destruction" (DestructionToggle), clear of the menu at any GUI scale. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu() && minecraft.player != null) {
				Screens.getWidgets(screen).add(Button.builder(Component.literal("SkyCraft settings"), b -> open(minecraft, screen))
					.bounds(158, 4, 150, 20).tooltip(Tooltip.create(Component.literal("Switch SkyCraft's features on and off."))).build());
			}
		});
	}

	private static void open(Minecraft minecraft, Screen pause) {
		// Every switch the server asks is the host's (an integrated server reads this file), so in a friend's world it's theirs.
		minecraft.gui.setScreen(new FeaturesScreen(pause, minecraft.options, minecraft.hasSingleplayerServer()));
	}

	@Override
	protected void addOptions() {
		OptionsList list = this.list;
		if (list == null) {
			return;
		}
		if (this.host) {
			this.line(list, "Changes apply at once, except those marked (restart):", ChatFormatting.GRAY);
			this.line(list, "they take effect after you restart Minecraft.", ChatFormatting.GRAY);
		} else {
			this.line(list, "You're in a friend's world: their settings decide.", ChatFormatting.GRAY);
			this.line(list, "These are yours, for when you play in your own world.", ChatFormatting.GRAY);
		}
		for (FeatureAreas.Area area : FeatureAreas.areas()) {
			this.line(list, area.name(), ChatFormatting.YELLOW);
			for (Feature feature : area.features()) {
				list.addSmall(this.toggle(feature), null);
			}
		}
	}

	/** A row of text: a note, or a section's header. */
	private void line(OptionsList list, String text, ChatFormatting color) {
		list.addSmall(new StringWidget(ROW_WIDTH, ROW_HEIGHT, Component.literal(text).withStyle(color), this.font), null);
	}

	private Button toggle(Feature feature) {
		Button button = Button.builder(label(feature), b -> {
			Features.set(feature, !Features.saved(feature));
			b.setMessage(label(feature));
		}).bounds(0, 0, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(this.tooltip(feature)))).build();
		button.active = this.host;
		return button;
	}

	/** "Kill drops: On"; a switch read once says "(restart)", and "(after restart)" once changed. */
	private static Component label(Feature feature) {
		String text = FeatureAreas.title(feature) + ": " + (Features.saved(feature) ? "On" : "Off");
		if (Features.waitsForRestart(feature)) {
			text += " (after restart)";
		} else if (Features.needsRestart(feature)) {
			text += " (restart)";
		}
		return Component.literal(text);
	}

	private String tooltip(Feature feature) {
		StringBuilder sb = new StringBuilder(FeatureAreas.description(feature));
		if (Features.needsRestart(feature)) {
			sb.append("\n\nA change takes effect after you restart Minecraft.");
		}
		if (!this.host) {
			sb.append("\n\nIn a friend's world, their settings decide.");
		}
		sb.append("\n\n").append(feature.key).append(" in skycraft-features.txt");
		return sb.toString();
	}
}
