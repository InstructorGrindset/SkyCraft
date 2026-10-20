package dev.skycraft.skooma;

import dev.skycraft.SkyCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Brewing Skooma (switch skooma_brewing): moon sugar, and Skooma and Redwater Skooma as Minecraft potions, so a brewing
 * stand can make them. An awkward potion and moon sugar brew Skooma; Skooma and glowstone dust brew Redwater Skooma, the
 * stronger, shorter cut, as glowstone strengthens any potion; gunpowder and dragon's breath make splash and lingering
 * ones, and lingering ones tipped arrows. The recipes are data (26.3's minecraft:brewing recipes in
 * data/skycraft/recipe/brewing), loaded only while the switch is on (config.FeatureCondition).
 *
 * <p>The potions hold what item conversion's Skooma rules give (SkyEffects): Skooma is the Skooma High effect for 180 s and
 * a moment's Saturation, Redwater level II for 150 s and Saturation II. The Saturation is hidden, so the bottle is
 * Skooma's purple. Their names are the lang file's item.minecraft.potion.effect.skooma and kin. Merchants' trades sell
 * them as {@code minecraft:potion[skycraft:skooma]} (Khajiit caravans, skycraft-trades.txt).
 */
public final class SkoomaBrewing {
	public static final String MOON_SUGAR_ID = "moon_sugar";
	/** Potion ids (and the names their lang keys end in). */
	public static final String SKOOMA_ID = "skooma", REDWATER_ID = "redwater_skooma";
	/** Skooma's effect seconds and levels (amplifiers), as item conversion's rules have them. */
	public static final int SKOOMA_SECONDS = 180, REDWATER_SECONDS = 150, SKOOMA_AMPLIFIER = 0, REDWATER_AMPLIFIER = 1;

	/** skycraft:moon_sugar: Skyrim's moon sugar, the Skooma brewer's reagent. */
	public static Item MOON_SUGAR;
	public static Holder<Potion> SKOOMA;
	public static Holder<Potion> REDWATER;

	private SkoomaBrewing() {
	}

	/** Registers moon sugar and the two potions. Mod initialization, after SkyEffects. */
	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(MOON_SUGAR_ID));
		MOON_SUGAR = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
		SKOOMA = potion(SKOOMA_ID, SKOOMA_SECONDS, SKOOMA_AMPLIFIER);
		REDWATER = potion(REDWATER_ID, REDWATER_SECONDS, REDWATER_AMPLIFIER);
		SkyCraft.LOG.info("SkyCraft-skooma: registered moon sugar and the potions {} and {}", BuiltInRegistries.POTION.getKey(SKOOMA.value()),
			BuiltInRegistries.POTION.getKey(REDWATER.value()));
	}

	private static Holder<Potion> potion(String name, int seconds, int amplifier) {
		Holder<MobEffect> saturation = BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace("saturation")).orElseThrow();
		Potion potion = new Potion(name, new MobEffectInstance(SkyEffects.SKOOMA, seconds * 20, amplifier),
			new MobEffectInstance(saturation, 1, amplifier, false, false));
		return Registry.registerForHolder(BuiltInRegistries.POTION, id(name), potion);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, path);
	}
}
