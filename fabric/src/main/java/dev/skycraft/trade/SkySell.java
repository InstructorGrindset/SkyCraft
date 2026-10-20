package dev.skycraft.trade;

import dev.skycraft.SkyCraft;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Selling Minecraft goods to Skyrim merchants for Skyrim gold, server side (switch sell_to_merchants). Sneak and right
 * click a merchant's stand-in with the goods in the main hand. If its shop deals in them (a gold line of
 * skycraft-trades.txt in one of its categories, {@link TradeRules#goldPriceFor}), Minecraft asks Skyrim how much gold the
 * merchant has (EV_MERCHANT_GOLD). On Skyrim's answer (IN_MERCHANT_GOLD, handed over by the client's InputBridge) it
 * takes as many whole lots from the hand as the merchant can pay for and has Skyrim move that gold from the merchant's
 * chest into the player's Skyrim inventory (EV_SELL_TO_MERCHANT). Each lot lowers what merchants in that town pay for the
 * item for a while ({@link Demand}, shared with the trade screen), worn gear sells for what's left of it, and the sale
 * trains Speech. Only the host sells: the merchants and the gold are the host's Skyrim's. Server thread only.
 */
public final class SkySell {
	/** How long Skyrim has to answer before the sale is called off (two seconds; an answer takes a frame or two). */
	static final int ANSWER_TICKS = 40;
	private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

	/** A sale waiting for Skyrim to say how much gold the merchant has. */
	private record Pending(ServerPlayer player, SkyrimActorEntity merchant, String item, int askedTick) {
	}

	private static final Map<Integer, Pending> PENDING = new HashMap<>();
	private static int nextRequest = 1;
	private static int now;

	private SkySell() {
	}

	/** The player sneaked and right clicked {@code merchant} with something in the main hand. */
	public static void offer(ServerPlayer player, SkyrimActorEntity merchant) {
		if (!Features.on(Features.Feature.SELL_TO_MERCHANTS)) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		String name = merchant.skyrimName();
		if (held.isEmpty()) {
			return;
		}
		if (!dev.skycraft.net.SkyNet.isHost(player)) {
			player.sendOverlayMessage(Component.literal("Only the host sells to Skyrim's merchants: they and their gold are in the host's Skyrim."));
			return;
		}
		if (!SkyLink.active()) {
			player.sendOverlayMessage(Component.literal(name + " can't hear you: Skyrim isn't connected."));
			return;
		}
		for (Pending p : PENDING.values()) {
			if (p.player() == player) {
				return; // one sale at a time; this one is waiting for Skyrim
			}
		}
		String id = SkyTrade.idOf(held);
		TradeRules.GoldPrice line = SkyTrade.rules().goldPriceFor(merchant.getVendor(), name, id);
		String itemName = held.getHoverName().getString();
		if (line == null) {
			player.sendOverlayMessage(Component.literal(name + " doesn't buy " + itemName + "."));
			return;
		}
		if (held.getCount() < line.count()) {
			player.sendOverlayMessage(Component.literal(name + " buys " + itemName + " " + line.count() + " at a time."));
			return;
		}
		int request = nextRequest;
		nextRequest = nextRequest == Integer.MAX_VALUE ? 1 : nextRequest + 1;
		PENDING.put(request, new Pending(player, merchant, id, now));
		SkyLink.pushEvent(Proto.EV_MERCHANT_GOLD, merchant.formId(), 0.0F, 0.0F, 0.0F, 0.0F, request);
	}

	/** Skyrim's answer to EV_MERCHANT_GOLD: {@code merchantId} has {@code gold} Skyrim gold (-1: it can't trade now). */
	public static void answer(int merchantId, int gold, int request) {
		Pending p = PENDING.remove(request);
		if (p == null) {
			return; // called off already, or not ours
		}
		ServerPlayer player = p.player();
		SkyrimActorEntity merchant = p.merchant();
		String name = merchant.skyrimName();
		if (player.isRemoved() || !Features.on(Features.Feature.SELL_TO_MERCHANTS)) {
			return;
		}
		if (merchant.formId() != merchantId || merchant.isRemoved() || player.distanceToSqr(merchant) > MAX_DISTANCE_SQR || gold < 0) {
			player.sendOverlayMessage(Component.literal(name + " can't trade with you now."));
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty() || !SkyTrade.idOf(held).equals(p.item())) {
			return; // the goods left the hand while Skyrim answered: nothing sold
		}
		TradeRules rules = SkyTrade.rules();
		TradeRules.GoldPrice line = rules.goldPriceFor(merchant.getVendor(), name, p.item());
		if (line == null) {
			return;
		}
		SkyLink.SkyState state = new SkyLink.SkyState();
		String town = SkyLink.readSkyState(state) ? state.town.strip() : "";
		// Worn gear sells for what's left of it.
		double share = held.getMaxDamage() > 0 ? (held.getMaxDamage() - held.getDamageValue()) / (double) held.getMaxDamage() : 1.0;
		int lots = held.getCount() / line.count();
		List<Integer> prices = rules.goldPrices(line, town, SkyTrade.DEMAND, lots, share);
		int sold = TradeRules.affordable(prices, gold);
		String itemName = held.getHoverName().getString();
		if (sold == 0) {
			player.sendOverlayMessage(Component.literal(gold == 0 ? name + " has no gold left." : name + " has only " + gold + " gold."));
			SkyCraft.LOG.info("SkyCraft-trade: {} couldn't sell {} to {}: {} gold wanted, {} had", player.getName().getString(), p.item(), name,
				prices.isEmpty() ? 0 : prices.getFirst(), gold);
			return;
		}
		int paid = 0;
		for (int i = 0; i < sold; i++) {
			paid += prices.get(i);
			SkyTrade.DEMAND.sold(town, line.item());
		}
		int items = sold * line.count();
		held.shrink(items);
		SkyLink.pushEvent(Proto.EV_SELL_TO_MERCHANT, merchantId, paid, items, 0.0F, 0.0F, request);
		// Skyrim's barter trains Speech by the gold that changes hands.
		SkyLink.pushEvent(Proto.EV_SKILL_USE, Proto.SKILL_SPEECH, paid, 0.0F, 0.0F, 0.0F, 0);
		player.sendOverlayMessage(Component.literal("Sold " + items + " " + itemName + " to " + name + " for " + paid + " gold"
			+ (sold < Math.min(lots, TradeRules.MAX_GOLD_BATCHES) ? " (all the gold " + name + " had for them)" : "")));
		SkyCraft.LOG.info("SkyCraft-trade: {} sold {} x {} to {} in {} for {} Skyrim gold (it had {})", player.getName().getString(), items, p.item(), name,
			town.isEmpty() ? "*" : town, paid, gold);
	}

	/** Every server tick: calls off sales Skyrim hasn't answered (an older Skyrim plugin never does). */
	static void tick(int serverTick) {
		now = serverTick;
		for (Iterator<Pending> it = PENDING.values().iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (serverTick - p.askedTick() > ANSWER_TICKS || serverTick < p.askedTick()) {
				it.remove();
				if (!p.player().isRemoved()) {
					p.player().sendOverlayMessage(Component.literal(p.merchant().skyrimName() + " didn't answer. Is SkyCraft's Skyrim plugin as new as its Minecraft mod?"));
				}
				SkyCraft.LOG.warn("SkyCraft-trade: Skyrim didn't answer how much gold {} has; sale called off", p.merchant().skyrimName());
			}
		}
	}
}
