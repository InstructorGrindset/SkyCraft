package dev.skycraft.skooma;

import dev.skycraft.SkyCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * SkyCraft's mob effects, for Skyrim's potions (item conversion names them: {@code skycraft:skooma} for Skooma, level II
 * for Redwater Skooma, {@code skycraft:stealth} for Fortify Sneak). Each level (amplifier + 1) adds again, as vanilla's
 * attribute effects do ({@code MobEffect.AttributeTemplate.create}: amount times level).
 *
 * <ul>
 * <li>Skooma High: Speed's +20% movement, Jump Boost's height (+0.1 jump strength, and a block more safe fall, as
 * Jump Boost gives), +0.15 sneaking speed (Swift Sneak's per level) and stealth. Getting it is a dose (SkySkooma).
 * <li>Stealth: the sneaking speed and the stealth, nothing else.
 * <li>Skooma Withdrawal: -10% movement and attack speed and -3% jump strength a level (level III still clears a
 * block), and hunger at half Hunger's rate a level.
 * </ul>
 *
 * <p>Stealth: a crouching player under Skooma or Stealth is noticed from a shorter distance
 * ({@link SkoomaRules#stealthScale}, mixin.LivingEntityStealthMixin on the visibility mobs target by).
 */
public final class SkyEffects {
	/** Skooma's purple, as the potions are colored. */
	public static final int SKOOMA_COLOR = 0x9B5FC0;
	public static final int STEALTH_COLOR = 0x3E4A5E;
	public static final int WITHDRAWAL_COLOR = 0x8C8A3A;

	public static final Holder<MobEffect> SKOOMA = register(SkoomaRules.SKOOMA, new SkyEffect(MobEffectCategory.BENEFICIAL, SKOOMA_COLOR, 0.0F, true)
		.addAttributeModifier(Attributes.MOVEMENT_SPEED, id("effect.skooma"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
		.addAttributeModifier(Attributes.JUMP_STRENGTH, id("effect.skooma"), 0.1, AttributeModifier.Operation.ADD_VALUE)
		.addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, id("effect.skooma"), 1.0, AttributeModifier.Operation.ADD_VALUE)
		.addAttributeModifier(Attributes.SNEAKING_SPEED, id("effect.skooma"), 0.15, AttributeModifier.Operation.ADD_VALUE));
	public static final Holder<MobEffect> STEALTH = register(SkoomaRules.STEALTH, new SkyEffect(MobEffectCategory.BENEFICIAL, STEALTH_COLOR, 0.0F, false)
		.addAttributeModifier(Attributes.SNEAKING_SPEED, id("effect.stealth"), 0.15, AttributeModifier.Operation.ADD_VALUE));
	/** Hunger's exhaustion is 0.005 a tick a level (HungerMobEffect); withdrawal's is half that. */
	public static final Holder<MobEffect> WITHDRAWAL = register(SkoomaRules.WITHDRAWAL, new SkyEffect(MobEffectCategory.HARMFUL, WITHDRAWAL_COLOR, 0.0025F, false)
		.addAttributeModifier(Attributes.MOVEMENT_SPEED, id("effect.skooma_withdrawal"), -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
		.addAttributeModifier(Attributes.ATTACK_SPEED, id("effect.skooma_withdrawal"), -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
		.addAttributeModifier(Attributes.JUMP_STRENGTH, id("effect.skooma_withdrawal"), -0.03, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	private SkyEffects() {
	}

	/** Loads the class: the effects register as it does. */
	public static void init() {
		SkyCraft.LOG.info("SkyCraft-skooma: registered effects {}, {} and {}", BuiltInRegistries.MOB_EFFECT.getKey(SKOOMA.value()),
			BuiltInRegistries.MOB_EFFECT.getKey(STEALTH.value()), BuiltInRegistries.MOB_EFFECT.getKey(WITHDRAWAL.value()));
	}

	private static Holder<MobEffect> register(String name, MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id(name), effect);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, path);
	}

	/** Stealth's level on an entity (Skooma's or Stealth's, whichever is higher) while it crouches; 0 otherwise. */
	public static int stealthLevel(LivingEntity entity) {
		if (!entity.isDiscrete()) {
			return 0;
		}
		int level = 0;
		MobEffectInstance skooma = entity.getEffect(SKOOMA);
		if (skooma != null) {
			level = skooma.getAmplifier() + 1;
		}
		MobEffectInstance stealth = entity.getEffect(STEALTH);
		if (stealth != null) {
			level = Math.max(level, stealth.getAmplifier() + 1);
		}
		return level;
	}

	/** A SkyCraft effect: a public way in to MobEffect's constructor, Skooma's dose and withdrawal's hunger. */
	static final class SkyEffect extends MobEffect {
		/** Food exhaustion a tick, per level (0: none). */
		private final float exhaustion;
		/** Getting this effect is a dose of Skooma. */
		private final boolean dose;

		SkyEffect(MobEffectCategory category, int color, float exhaustion, boolean dose) {
			super(category, color);
			this.exhaustion = exhaustion;
			this.dose = dose;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return this.exhaustion > 0.0F;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			if (entity instanceof Player player) {
				player.causeFoodExhaustion(this.exhaustion * (amplifier + 1));
			}
			return true;
		}

		/** Called every time the effect is given (LivingEntity.addEffect), new or not: a potion, a splash, a command. */
		@Override
		public void onEffectStarted(LivingEntity entity, int amplifier) {
			if (this.dose && entity instanceof ServerPlayer player) {
				SkySkooma.dosed(player, amplifier);
			}
		}
	}
}
