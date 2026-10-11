package dev.skycraft.loot;

import dev.skycraft.SkyCraft;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Looting bridge, Minecraft side. Skyrim reports every item that lands in the player's Skyrim
 * inventory; items that match a conversion rule in {@code config/skycraft-items.txt} are given to
 * the Minecraft player and Skyrim is told to remove the original (EV_TAKE_ITEM). Everything else
 * stays in Skyrim. Server thread only.
 */
public final class SkyBridge {
	private static final String FILE = "skycraft-items.txt";
	/** Most Minecraft items one Skyrim pickup turns into (64 stacks); the rest stays in Skyrim. */
	static final int MAX_ITEMS = 64 * 64;
	private static ItemRules rules;

	private SkyBridge() {
	}

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();

	/** Converts every queued Skyrim item for the host player. */
	public static void tick(ServerPlayer host) {
		SkyLink.drainItems(item -> convert(host, item));
	}

	/** How many of {@code count} Skyrim items convert at {@code perItem} each, keeping the total within {@link #MAX_ITEMS}. */
	static int convertible(int count, int perItem) {
		return Math.min(count, MAX_ITEMS / Math.max(1, perItem));
	}

	/** Puts {@code stack} in the player's inventory; whatever doesn't fit drops at their feet. */
	static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
			dropped.setDefaultPickUpDelay();
			player.level().addFreshEntity(dropped);
		}
	}

	private static void convert(ServerPlayer player, SkyLink.Item it) {
		if (it.count() <= 0) {
			return;
		}
		if (it.source() == Proto.SRC_CONTAINER && it.chest() && SkyLink.readSkyState(SKY)) {
			DungeonLoot.onLooted(player, it, SKY.place);
		}
		ItemRules.Result result = rules().match(new ItemRules.Item(it.tags(), it.material(), it.source(), it.name()));
		if (result.keep()) {
			SkyCraft.LOG.info("SkyCraft-loot: {} x {} stays in Skyrim [{}]", it.count(), it.name(), ItemRules.tagNames(it.tags()));
			return;
		}
		Identifier key = Identifier.tryParse(result.item());
		Item item = key != null ? BuiltInRegistries.ITEM.getValue(key) : null;
		if (item == null || item == Items.AIR) {
			SkyCraft.LOG.warn("SkyCraft-loot: unknown item '{}' in {}, {} stays in Skyrim", result.item(), FILE, it.name());
			return;
		}
		int count = convertible(it.count(), result.perItem());
		int total = count * result.perItem();
		int max = new ItemStack(item).getMaxStackSize();
		for (int left = total; left > 0; ) {
			int n = Math.min(left, max);
			give(player, new ItemStack(item, n));
			left -= n;
		}
		SkyLink.pushEvent(Proto.EV_TAKE_ITEM, it.formId(), count, 0.0F, 0.0F, 0.0F, 0);
		SkyCraft.LOG.info("SkyCraft-loot: {} x {} became {} {}{}", count, it.name(), total, result.item(),
			count < it.count() ? " (" + (it.count() - count) + " more stay in Skyrim)" : "");
	}

	private static ItemRules rules() {
		if (rules == null) {
			List<String> problems = new ArrayList<>();
			rules = ItemRules.parse(Config.read(FILE, ItemRules.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-loot: {} {}", FILE, p));
			SkyCraft.LOG.info("SkyCraft-loot: {} conversion rules loaded", rules.size());
		}
		return rules;
	}
}
