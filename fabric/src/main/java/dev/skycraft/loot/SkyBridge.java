package dev.skycraft.loot;

import dev.skycraft.SkyCraft;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
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
	private static ItemRules rules;

	private SkyBridge() {
	}

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();

	/** Converts every queued Skyrim item for the host player. */
	public static void tick(ServerPlayer host) {
		SkyLink.drainItems(item -> convert(host, item));
	}

	private static void convert(ServerPlayer player, SkyLink.Item it) {
		if (it.count() <= 0) {
			return;
		}
		if (it.source() == Proto.SRC_CONTAINER && SkyLink.readSkyState(SKY)) {
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
		int total = it.count() * result.perItem();
		int max = new ItemStack(item).getMaxStackSize();
		for (int left = total; left > 0; ) {
			int n = Math.min(left, max);
			ItemStack stack = new ItemStack(item, n);
			player.getInventory().add(stack);
			if (!stack.isEmpty()) {
				// Inventory full: drop the rest at the player's feet.
				ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
				dropped.setDefaultPickUpDelay();
				player.level().addFreshEntity(dropped);
			}
			left -= n;
		}
		SkyLink.pushEvent(Proto.EV_TAKE_ITEM, it.formId(), it.count(), 0.0F, 0.0F, 0.0F, 0);
		SkyCraft.LOG.info("SkyCraft-loot: {} x {} became {} {}", it.count(), it.name(), total, result.item());
	}

	private static ItemRules rules() {
		if (rules != null) {
			return rules;
		}
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		String text = ItemRules.DEFAULT_TEXT;
		try {
			if (Files.exists(path)) {
				text = Files.readString(path);
			} else {
				Files.createDirectories(path.getParent());
				Files.writeString(path, ItemRules.DEFAULT_TEXT);
				SkyCraft.LOG.info("SkyCraft-loot: wrote default conversion rules to {}", path);
			}
		} catch (IOException e) {
			SkyCraft.LOG.warn("SkyCraft-loot: couldn't read {}, using defaults: {}", path, e.toString());
			text = ItemRules.DEFAULT_TEXT;
		}
		List<String> problems = new ArrayList<>();
		rules = ItemRules.parse(text, problems);
		problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-loot: {} {}", FILE, p));
		SkyCraft.LOG.info("SkyCraft-loot: {} conversion rules loaded", rules.size());
		return rules;
	}
}
