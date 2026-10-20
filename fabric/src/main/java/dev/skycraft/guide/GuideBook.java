package dev.skycraft.guide;

import com.mojang.serialization.Codec;
import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import dev.skycraft.link.SkyLink;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * The SkyCraft guide book (switch {@code guide_book}): the written book "SkyCraft" ({@link GuidePages}), given once to
 * each player, host or guest, the first time they're in a world whose Skyrim is linked, into their inventory (at their
 * feet when it's full). That it was given is a Fabric data attachment on the player ({@code skycraft:guide_book}, as
 * SkySkooma keeps the Skooma habit): saved with them and kept when they die, so the book never comes twice, even if it's
 * lost. With the switch off nothing is given or remembered, so switching it on later gives it. Checked once a second,
 * after the join's own kits (SkyCraft.giveStarterKit wants an empty inventory). Server thread. Log tag {@code SkyCraft-guide}.
 */
public final class GuideBook {
	/** Whether this player has had the guide book: saved with them, kept when they die. */
	public static final AttachmentType<Boolean> GIVEN = AttachmentRegistry.<Boolean>create(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "guide_book"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** Players are looked at this often (ticks). */
	private static final int CHECK_TICKS = 20;

	private GuideBook() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GuideBook::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0 || !SkyLink.active() || !Features.on(Features.Feature.GUIDE_BOOK)) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !Boolean.TRUE.equals(player.getAttached(GIVEN))) {
				give(player);
			}
		}
	}

	private static void give(ServerPlayer player) {
		ItemStack book = book();
		player.getInventory().add(book);
		if (!book.isEmpty()) {
			// A full inventory: it waits at their feet, as an item conversion that doesn't fit does.
			ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), book);
			dropped.setDefaultPickUpDelay();
			player.level().addFreshEntity(dropped);
		}
		player.setAttached(GIVEN, true);
		SkyCraft.LOG.info("SkyCraft-guide: gave the SkyCraft guide book to {}", player.getName().getString());
	}

	/** The written book, made as the Home camp's journal is (worlds/HomeCamp.journal). */
	static ItemStack book() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = GuidePages.PAGES.stream()
			.map(page -> Filterable.<Component>passThrough(Component.literal(page)))
			.toList();
		book.set(DataComponents.WRITTEN_BOOK_CONTENT,
			new WrittenBookContent(Filterable.passThrough(GuidePages.TITLE), GuidePages.AUTHOR, 0, pages, true));
		return book;
	}
}
