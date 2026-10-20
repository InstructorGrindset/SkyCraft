package dev.skycraft.loot;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Looting bridge, Minecraft side. Skyrim reports every item that lands in the player's Skyrim
 * inventory; items that match a conversion rule in {@code config/skycraft-items.txt} are given to
 * the Minecraft player and Skyrim is told to remove the original (EV_TAKE_ITEM). Everything else
 * stays in Skyrim. Gear a dying actor wore comes the same way but goes to {@link GearDrops}, never
 * to the player. Server thread only.
 */
public final class SkyBridge {
	private static final String FILE = "skycraft-items.txt";
	/** Most Minecraft stacks one Skyrim pickup turns into; the rest stays in Skyrim. */
	static final int MAX_STACKS = 64;
	private static ItemRules rules;

	private SkyBridge() {
	}

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	/** Each player's action bar line of what their pickups became (conversion_messages). */
	private static final java.util.Map<java.util.UUID, ConversionNotes> NOTES = new java.util.HashMap<>();

	/** Converts every queued Skyrim item for the host player, then says on the action bar what they became. */
	public static void tick(ServerPlayer host) {
		SkyLink.drainItems(item -> convert(host, item));
		showNotes(host);
	}

	/** A pickup's line for the player's action bar (conversion_messages): collected now, shown at the end of the tick. */
	private static void note(ServerPlayer player, String line, boolean converted) {
		if (Features.on(Features.Feature.CONVERSION_MESSAGES)) {
			NOTES.computeIfAbsent(player.getUUID(), id -> new ConversionNotes()).add(line, converted, player.level().getServer().getTickCount());
		}
	}

	/** The player's conversion line, when this tick's pickups changed it. Pickups within a second share one line. */
	private static void showNotes(ServerPlayer player) {
		ConversionNotes notes = NOTES.get(player.getUUID());
		String line = notes != null ? notes.poll() : null;
		if (line != null) {
			player.sendOverlayMessage(Component.literal(line));
		}
	}

	/**
	 * How many of {@code count} Skyrim items convert at {@code perItem} each, keeping the total within
	 * {@link #MAX_STACKS} stacks of {@code maxStack} (potions don't stack, so 64 potions at most).
	 */
	static int convertible(int count, int perItem, int maxStack) {
		return Math.min(count, MAX_STACKS * Math.max(1, maxStack) / Math.max(1, perItem));
	}

	/**
	 * {@code n} of {@code item}, holding {@code potion} and {@code effects} when it has them (potions, splash and
	 * lingering potions, tipped arrows), in {@code color} (0xRRGGBB, -1 for Minecraft's mix), named {@code name}.
	 */
	static ItemStack stack(Item item, int n, Holder<Potion> potion, List<MobEffectInstance> effects, int color, String name) {
		ItemStack stack = new ItemStack(item, n);
		if (potion != null || !effects.isEmpty()) {
			stack.set(DataComponents.POTION_CONTENTS,
				new PotionContents(Optional.ofNullable(potion), color >= 0 ? Optional.of(color) : Optional.empty(), effects, Optional.empty()));
		}
		if (name != null) {
			// A potion's own name comes from its contents, so the Skyrim name goes in CUSTOM_NAME, not italic like a renamed item's.
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
		}
		return stack;
	}

	/**
	 * Puts a rule's enchantments on {@code stack}, the way an enchanting table would (an enchanted book stores them):
	 * Skyrim gear that kept its enchantment. One Minecraft doesn't know is left off, with a warning.
	 */
	static void enchant(ItemStack stack, List<ItemRules.Enchant> enchants, ServerPlayer player, String skyrimName) {
		if (enchants.isEmpty()) {
			return;
		}
		var registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		EnchantmentHelper.updateEnchantments(stack, mutable -> {
			for (ItemRules.Enchant e : enchants) {
				registry.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(e.id())))
					.ifPresentOrElse(holder -> mutable.set(holder, e.level()),
						() -> SkyCraft.LOG.warn("SkyCraft-loot: unknown enchantment '{}' in {}, {} converts without it", e.id(), FILE, skyrimName));
			}
		});
	}

	/** One Minecraft item a rule gives, found in the registries; {@code max} is its stack size. */
	private record Output(ItemRules.Result rule, Item item, Holder<Potion> potion, List<MobEffectInstance> effects, int max) {
	}

	/** {@code out}'s item, potion and effects from the registries; null (and a warning) when one is unknown. */
	private static Output resolve(ItemRules.Result out, String skyrimName) {
		Identifier key = Identifier.tryParse(out.item());
		Item item = key != null ? BuiltInRegistries.ITEM.getValue(key) : null;
		if (item == null || item == Items.AIR) {
			SkyCraft.LOG.warn("SkyCraft-loot: unknown item '{}' in {}, {} stays in Skyrim", out.item(), FILE, skyrimName);
			return null;
		}
		Holder<Potion> potion = null;
		if (out.potion() != null) {
			// ItemRules only lets vanilla potion ids and SkyCraft's (skycraft:skooma, with their namespace) through, so this finds it.
			Identifier potionId = out.potion().indexOf(':') >= 0 ? Identifier.parse(out.potion()) : Identifier.withDefaultNamespace(out.potion());
			potion = BuiltInRegistries.POTION.get(potionId).orElse(null);
			if (potion == null) {
				SkyCraft.LOG.warn("SkyCraft-loot: unknown potion '{}' in {}, {} stays in Skyrim", out.potion(), FILE, skyrimName);
				return null;
			}
		}
		List<MobEffectInstance> effects = new ArrayList<>();
		for (ItemRules.Effect e : out.effects()) {
			// ItemRules only lets 26.3 and SkyCraft effect ids through, and match() already picked the values for the name's tier.
			// Vanilla effects are written without a namespace, SkyCraft's with theirs (skycraft:skooma).
			Identifier effectId = e.id().indexOf(':') >= 0 ? Identifier.parse(e.id()) : Identifier.withDefaultNamespace(e.id());
			Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(effectId).orElse(null);
			if (effect == null) {
				SkyCraft.LOG.warn("SkyCraft-loot: unknown effect '{}' in {}, {} stays in Skyrim", e.id(), FILE, skyrimName);
				return null;
			}
			effects.add(new MobEffectInstance(effect, Math.max(1, e.seconds() * 20), e.amplifier()));
		}
		return new Output(out, item, potion, List.copyOf(effects), new ItemStack(item).getMaxStackSize());
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
		if (it.source() == Proto.SRC_WORN) {
			// Gear a dying Skyrim actor wore: never the player's, it only ever drops as Minecraft gear (Gear drops).
			GearDrops.onWorn(player.level(), it, player.level().getServer().getTickCount());
			return;
		}
		if (it.count() <= 0) {
			return;
		}
		if (it.source() == Proto.SRC_CONTAINER && it.chest() && Features.on(Features.Feature.DUNGEON_CHESTS) && SkyLink.readSkyState(SKY)) {
			DungeonLoot.onLooted(player, it, SKY.place);
		}
		if (!Features.on(Features.Feature.ITEM_CONVERSION)) {
			return;
		}
		if (it.quest()) {
			SkyCraft.LOG.info("SkyCraft-loot: {} is a quest item, stays in Skyrim", it.name());
			note(player, ConversionNotes.kept(it.name(), it.count(), true), false);
			return;
		}
		ItemRules.Result result = rules().match(new ItemRules.Item(it.tags(), it.material(), it.source(), it.name()));
		if (result.keep()) {
			SkyCraft.LOG.info("SkyCraft-loot: {} x {} stays in Skyrim [{}]", it.count(), it.name(), ItemRules.tagNames(it.tags()));
			note(player, ConversionNotes.kept(it.name(), it.count(), false), false);
			return;
		}
		// The rule's item, then any it gives along with it ("+" in the rule).
		List<ItemRules.Result> wanted = new ArrayList<>();
		wanted.add(result);
		wanted.addAll(result.also());
		List<Output> outputs = new ArrayList<>();
		int count = it.count();
		for (ItemRules.Result out : wanted) {
			Output o = resolve(out, it.name());
			if (o == null) {
				note(player, ConversionNotes.kept(it.name(), it.count(), false), false);
				return;
			}
			outputs.add(o);
			count = Math.min(count, convertible(it.count(), out.perItem(), o.max()));
		}
		List<ConversionNotes.Output> gave = new ArrayList<>();
		for (Output o : outputs) {
			String name = null;
			for (int left = count * o.rule().perItem(); left > 0; ) {
				int n = Math.min(left, o.max());
				ItemStack stack = stack(o.item(), n, o.potion(), o.effects(), o.rule().color(), o.rule().name());
				enchant(stack, o.rule().enchantments(), player, it.name());
				if (name == null) {
					name = stack.getHoverName().getString(); // before give, which may empty the stack into the inventory
				}
				give(player, stack);
				left -= n;
			}
			gave.add(new ConversionNotes.Output(name != null ? name : o.rule().label(), count * o.rule().perItem()));
		}
		note(player, ConversionNotes.converted(it.name(), count, gave), true);
		SkyLink.pushEvent(Proto.EV_TAKE_ITEM, it.formId(), count, 0.0F, 0.0F, 0.0F, 0);
		int taken = count;
		String became = outputs.stream().map(o -> taken * o.rule().perItem() + " " + o.rule().label()).collect(Collectors.joining(" + "));
		SkyCraft.LOG.info("SkyCraft-loot: {} x {} became {}{}", count, it.name(), became,
			count < it.count() ? " (" + (it.count() - count) + " more stay in Skyrim)" : "");
	}

	/**
	 * One of what a Skyrim item called {@code name} (with {@code tags}, Proto ItemTags) converts into by skycraft-items.txt,
	 * as Minecraft would give it for a pickup: an empty stack when no rule takes it or a rule keeps it in Skyrim. Tamriel
	 * Wilds' villagers sell Skyrim's potions this way (villagers.SkyVillagers). Server thread.
	 */
	public static ItemStack converted(String name, int tags) {
		ItemRules.Result result = rules().match(new ItemRules.Item(tags, 0, Proto.SRC_BOUGHT, name));
		if (result.keep()) {
			return ItemStack.EMPTY;
		}
		Output o = resolve(result, name);
		return o == null ? ItemStack.EMPTY : stack(o.item(), Math.max(1, Math.min(result.perItem(), o.max())), o.potion(), o.effects(), result.color(), result.name());
	}

	private static ItemRules rules() {
		if (rules == null) {
			List<String> problems = new ArrayList<>();
			rules = ItemRules.parse(Config.read(FILE, ItemRules.DEFAULT_TEXT, ItemRules.OLD_DEFAULTS), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-loot: {} {}", FILE, p));
			SkyCraft.LOG.info("SkyCraft-loot: {} conversion rules loaded", rules.size());
		}
		return rules;
	}
}
