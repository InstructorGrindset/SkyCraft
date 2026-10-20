package dev.skycraft.skills;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.skooma.SkyEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Alchemy in Minecraft's brewing stand (switch {@code alchemy_brewing}).
 *
 * <ul>
 * <li>A potion the host takes out of a brewing stand's bottle slots lasts longer with their Skyrim Alchemy
 * ({@link SkillRules#alchemyScale}: 42.5% longer at 100): its {@code potion_duration_scale} is set to its item's own (a
 * lingering potion's 0.25) times that, so drinking it, its splash or its cloud and its tooltip all count the longer time,
 * and it still brews on as the potion it is. mixin.BrewingTakeMixin, on Slot.mayPickup, which every way of taking from a
 * slot asks first. Taken again with the same Alchemy, it stays as it was.
 * <li>Purple Mountain Flower, Skyrim's best known Fortify Sneak ingredient, is an allium once item conversion has it, and
 * an allium brews an awkward potion into a potion of Stealth (SkyCraft's own effect, skooma.SkyEffects: 3:00), redstone
 * makes it last 8:00 and glowstone makes it Stealth II for 1:30, as vanilla's Swiftness goes. The potions are always
 * registered (a potion in a chest stays good with the switch off); the recipes are data
 * (data/skycraft/recipe/brewing, Minecraft's own brewing recipes, as the Skooma ones are) that load only with the switch on
 * (config.FeatureCondition).
 * </ul>
 *
 * <p>Skooma isn't brewed here (switch {@code skooma_brewing} has moon sugar for it).
 */
public final class SkyAlchemy {
	/** Stealth I for 3:00, from an awkward potion and an allium. */
	public static final Holder<Potion> STEALTH = register("stealth", new Potion(SkillRules.STEALTH_POTION, new MobEffectInstance(SkyEffects.STEALTH, 3600, 0)));
	/** Stealth I for 8:00, with redstone. */
	public static final Holder<Potion> LONG_STEALTH = register("long_stealth",
		new Potion(SkillRules.STEALTH_POTION, new MobEffectInstance(SkyEffects.STEALTH, 9600, 0)));
	/** Stealth II for 1:30, with glowstone. */
	public static final Holder<Potion> STRONG_STEALTH = register("strong_stealth",
		new Potion(SkillRules.STEALTH_POTION, new MobEffectInstance(SkyEffects.STEALTH, 1800, 1)));

	/** Log lines so far for potions taken, so the log shows the first few. */
	private static int logged;

	private SkyAlchemy() {
	}

	/** Loads the class, so the potions register as it does (their recipes are data files). */
	static void init() {
		SkyCraft.LOG.info("SkyCraft-skills: registered potions {}, {} and {}", BuiltInRegistries.POTION.getKey(STEALTH.value()),
			BuiltInRegistries.POTION.getKey(LONG_STEALTH.value()), BuiltInRegistries.POTION.getKey(STRONG_STEALTH.value()));
	}

	private static Holder<Potion> register(String name, Potion potion) {
		return Registry.registerForHolder(BuiltInRegistries.POTION, Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, name), potion);
	}

	/**
	 * {@code player} takes (or reaches for) {@code stack} in a brewing stand's bottle slot: for the host, with their Alchemy, it
	 * lasts longer. Server side; anyone else's potion is left alone.
	 */
	public static void taking(Player player, ItemStack stack) {
		if (!(player instanceof ServerPlayer) || stack.isEmpty() || !Features.on(Features.Feature.ALCHEMY_BREWING)) {
			return;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null || !hasEffects(contents)) {
			return; // water, awkward and the like: nothing to last
		}
		int alchemy = SkySkills.level(player, Proto.SKILL_ALCHEMY);
		double scale = SkillRules.alchemyScale(alchemy, SkySkills.settings());
		if (scale <= 1.0) {
			return;
		}
		// The item's own scale (a lingering potion's cloud lasts a quarter), times Alchemy's. Brewing makes a fresh potion, so
		// what a brewed one brings is always the item's own.
		float own = new ItemStack(stack.getItem()).getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
		float scaled = (float) (own * scale);
		if (stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F) == scaled) {
			return;
		}
		stack.set(DataComponents.POTION_DURATION_SCALE, scaled);
		if (logged < 10) {
			logged++;
			SkyCraft.LOG.info("SkyCraft-skills: Alchemy {}: {} takes a potion from a brewing stand that lasts x{}", alchemy, player.getName().getString(),
				String.format(java.util.Locale.ROOT, "%.3f", scale));
		}
	}

	private static boolean hasEffects(PotionContents contents) {
		return !contents.customEffects().isEmpty() || contents.potion().map(potion -> !potion.value().getEffects().isEmpty()).orElse(false);
	}
}
