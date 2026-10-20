package dev.skycraft.villagers;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import dev.skycraft.loot.SkyBridge;
import dev.skycraft.worlds.Worlds;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * Tamriel Wilds' villagers, server side (switch {@code wilds_villagers}, rules in {@link WildsVillagers}). Every five
 * seconds the villagers within 64 blocks of a player in Tamriel Wilds get their Skyrim name for their profession (unless a
 * player named them) and the Skyrim trades of their profession and level they don't have yet, after Minecraft's own
 * (a villager's trades start over when it changes profession, and these come back with the new one's). Only Tamriel
 * Wilds' level is ever looked at: villagers anywhere else stay vanilla.
 */
public final class SkyVillagers {
	private static final int EVERY = 100;
	private static final double RADIUS = 64.0;
	/** Vanilla's price multiplier for most trades: demand moves the price this much. */
	private static final float PRICE_MULTIPLIER = 0.05F;
	private static int logged;

	private SkyVillagers() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SkyVillagers::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0 || !Features.on(Features.Feature.WILDS_VILLAGERS)) {
			return;
		}
		ServerLevel wilds = server.getLevel(Worlds.TAMRIEL_WILDS);
		if (wilds == null || wilds.players().isEmpty()) {
			return;
		}
		Set<UUID> done = new HashSet<>();
		for (ServerPlayer player : wilds.players()) {
			for (Villager villager : wilds.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(RADIUS))) {
				if (villager.isAlive() && done.add(villager.getUUID())) {
					dress(villager);
				}
			}
		}
	}

	private static void dress(Villager villager) {
		UUID uuid = villager.getUUID();
		long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
		String profession = villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
		String wanted = WildsVillagers.displayName(seed, profession, villager.isBaby());
		Component name = villager.getCustomName();
		if (name == null || (!name.getString().equals(wanted) && WildsVillagers.isOurs(name.getString(), seed))) {
			villager.setCustomName(Component.literal(wanted));
			if (logged++ < 20) {
				SkyCraft.LOG.info("SkyCraft-wilds: a villager in Tamriel Wilds is {}", wanted);
			}
		}
		List<WildsVillagers.Trade> trades = WildsVillagers.tradesFor(profession, villager.getVillagerData().level());
		if (trades.isEmpty() || villager.isBaby()) {
			return;
		}
		MerchantOffers offers = villager.getOffers(); // Minecraft's own first (made now if it hasn't yet)
		for (WildsVillagers.Trade t : trades) {
			ItemStack goods = stack(t);
			if (goods == null || has(offers, goods)) {
				continue;
			}
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, t.emeralds()), Optional.empty(), goods, 0, t.maxUses(), t.xp(), PRICE_MULTIPLIER));
		}
	}

	/** What a trade sells, or null when its item is unknown (or a Skyrim potion SkyCraft's rules keep in Skyrim). */
	private static @Nullable ItemStack stack(WildsVillagers.Trade t) {
		if (t.potion()) {
			ItemStack potion = SkyBridge.converted(t.skyrimName(), 1 << dev.skycraft.loot.ItemRules.TAGS.indexOf("potion"));
			return potion.isEmpty() ? null : potion;
		}
		Identifier id = Identifier.tryParse(t.item());
		Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : null;
		return item == null || item == Items.AIR ? null : new ItemStack(item, t.count());
	}

	private static boolean has(MerchantOffers offers, ItemStack goods) {
		for (MerchantOffer offer : offers) {
			if (ItemStack.isSameItemSameComponents(offer.getResult(), goods) && offer.getResult().getCount() == goods.getCount()) {
				return true;
			}
		}
		return false;
	}
}
