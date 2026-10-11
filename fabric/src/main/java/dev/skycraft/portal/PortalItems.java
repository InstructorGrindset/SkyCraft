package dev.skycraft.portal;

import dev.skycraft.SkyCraft;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The portal network's items, registered like SkyGear's (vanilla 26.3 Items.registerItem: the
 * Item.Properties gets its id first). Models in assets/skycraft/items, textures from
 * tools/gen_portal_textures.py.
 */
public final class PortalItems {
	/** skycraft:filled_soul_gem, from a filled Greater, Grand or Black Skyrim soul gem (skycraft-items.txt). */
	public static Item FILLED_SOUL_GEM;
	/** skycraft:attuned_soul_gem: a filled gem attuned to one portal (CUSTOM_DATA remembers which). */
	public static Item ATTUNED_SOUL_GEM;

	private PortalItems() {
	}

	static void init() {
		FILLED_SOUL_GEM = register(PortalStore.FILLED_GEM_ID, p -> new SoulGemItem(p.stacksTo(16).rarity(Rarity.UNCOMMON), false));
		ATTUNED_SOUL_GEM = register(PortalStore.ATTUNED_GEM_ID,
			p -> new SoulGemItem(p.stacksTo(1).rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), true));
		SkyCraft.LOG.info("SkyCraft-portal: registered the soul gem items");
	}

	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}
}
