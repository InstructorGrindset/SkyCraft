package dev.skycraft.skooma;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skycraft.SkyCraft;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.skooma.SkoomaRules.Dose;
import dev.skycraft.skooma.SkoomaRules.Habit;
import dev.skycraft.skooma.SkoomaRules.Settings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;

/**
 * Skooma's addiction, Minecraft side (switch {@code skooma_addiction}, numbers in {@code skycraft-skooma.txt}): the
 * glue between Minecraft and {@link SkoomaRules}. Every player, host or guest, in every dimension.
 *
 * <ul>
 * <li>A dose: the Skooma effect given to a player (SkyEffects.SkyEffect.onEffectStarted, from LivingEntity.addEffect).
 * It's handled on the next server tick, outside addEffect: ending a withdrawal takes an effect away.
 * <li>Once a second per player: the comedown when a high ran out by itself (Slowness and Hunger), and for an addict the
 * withdrawal (the Skooma Withdrawal effect, endless, at the level the rules say; now and then a craving line on the
 * action bar and a spell of nausea), until the cure.
 * <li>Milk (or anything else clearing effects) takes the withdrawal away; it comes back {@code symptoms_return} later
 * while the addiction lasts.
 * </ul>
 *
 * <p>The clock is the overworld's game time, which every dimension shares and which keeps running while SkyCraft holds
 * the sun still. Each player's {@link Habit} is a Fabric data attachment, saved with the player and kept on death.
 * {@code /skyskooma} tells a player their own habit. Server thread only. Log tag {@code SkyCraft-skooma}.
 */
public final class SkySkooma {
	private static final String FILE = "skycraft-skooma.txt";
	/** Players are looked at this often (ticks). */
	private static final int CHECK_TICKS = 20;
	/** A high last seen with this little left ran out by itself (milk or a command take it with more left). */
	private static final int WORN_OFF = CHECK_TICKS + 5;
	private static final int NAUSEA_TICKS = 160;

	private static final Codec<Habit> HABIT_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("doses", 0).forGetter(Habit::doses),
		Codec.LONG.optionalFieldOf("last_dose", Habit.NEVER).forGetter(Habit::lastDose),
		Codec.DOUBLE.optionalFieldOf("tolerance", 0.0).forGetter(Habit::tolerance),
		Codec.BOOL.optionalFieldOf("addicted", false).forGetter(Habit::addicted),
		Codec.INT.optionalFieldOf("relapses", 0).forGetter(Habit::relapses),
		Codec.LONG.optionalFieldOf("endured", 0L).forGetter(Habit::endured)
	).apply(i, Habit::new));

	/** Each player's habit: saved with the player, kept when they die. */
	public static final AttachmentType<Habit> HABIT = AttachmentRegistry.<Habit>create(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "skooma"),
		builder -> builder.persistent(HABIT_CODEC).copyOnDeath());

	/** A dose waiting for the next tick. */
	private record Pending(UUID player, int amplifier) {
	}

	/** A player's high as last seen. */
	private record High(int duration, int amplifier, boolean infinite) {
	}

	/** A player's withdrawal symptoms: the level shown, whether the effect is on them, when it may come back after milk. */
	private static final class Symptoms {
		int level;
		boolean shown;
		long backAt;
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<UUID, High> HIGHS = new HashMap<>();
	private static final Map<UUID, Symptoms> SYMPTOMS = new HashMap<>();
	private static @Nullable Settings settings;

	private SkySkooma() {
	}

	public static void init() {
		SkyEffects.init();
		SkyCraft.LOG.info("SkyCraft-skooma: addiction {} ({})", Features.on(Features.Feature.SKOOMA_ADDICTION) ? "on" : "off", HABIT.identifier());
		ServerTickEvents.END_SERVER_TICK.register(SkySkooma::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Features.on(Features.Feature.SKOOMA_ADDICTION)) {
				settings(); // writes skycraft-skooma.txt and logs the numbers
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			HIGHS.clear();
			SYMPTOMS.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("skyskooma").executes(c -> {
			ServerPlayer player = c.getSource().getPlayerOrException();
			String text = "Skooma: " + habit(player).describe(now(c.getSource().getServer()), settings());
			c.getSource().sendSuccess(() -> Component.literal(text), false);
			return 1;
		})));
	}

	private static Settings settings() {
		if (settings == null) {
			List<String> problems = new ArrayList<>();
			settings = SkoomaRules.parse(Config.read(FILE, SkoomaRules.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-skooma: {} {}", FILE, p));
			Settings s = settings;
			SkyCraft.LOG.info("SkyCraft-skooma: hook chance {} + {} a tolerance point + {} a level over I; withdrawal after {} ticks, worse every {}; "
				+ "cured {} ticks clean (+{} a relapse) after {} of withdrawal; symptoms back {} after milk; comedown {}", s.chance(), s.chancePerTolerance(),
				s.chancePerStrength(), s.withdrawalAfter(), s.withdrawalStep(), s.recoveryAfter(), s.recoveryPerRelapse(), s.recoveryEndured(),
				s.symptomsReturn(), s.comedown());
		}
		return settings;
	}

	/** The world's game time: the overworld's, which every dimension shares. */
	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static Habit habit(ServerPlayer player) {
		Habit habit = player.getAttached(HABIT);
		return habit != null ? habit : Habit.CLEAN;
	}

	/** A player got the Skooma effect (SkyEffects): a dose, taken on the next tick. */
	static void dosed(ServerPlayer player, int amplifier) {
		if (Features.on(Features.Feature.SKOOMA_ADDICTION)) {
			PENDING.add(new Pending(player.getUUID(), amplifier));
		}
	}

	private static void tick(MinecraftServer server) {
		boolean on = Features.on(Features.Feature.SKOOMA_ADDICTION);
		if (!PENDING.isEmpty()) {
			List<Pending> doses = new ArrayList<>(PENDING);
			PENDING.clear();
			long now = now(server);
			for (Pending dose : doses) {
				ServerPlayer player = server.getPlayerList().getPlayer(dose.player());
				if (player != null) {
					take(player, dose.amplifier(), now);
				}
			}
		}
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		long now = now(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (on) {
				check(player, now);
			} else {
				endSymptoms(player); // switched off: no withdrawal left behind from before
			}
		}
	}

	private static void take(ServerPlayer player, int amplifier, long now) {
		Settings s = settings();
		Habit before = habit(player);
		double chance = before.hookChance(now, amplifier, s);
		Dose dose = before.dose(now, amplifier, player.getRandom().nextDouble(), s);
		if (!dose.counted()) {
			return; // part of the last dose
		}
		Habit after = dose.habit();
		player.setAttached(HABIT, after);
		if (dose.relieved()) {
			endSymptoms(player);
			player.sendOverlayMessage(Component.literal(SkoomaRules.RELIEF));
		}
		if (dose.hooked()) {
			player.sendSystemMessage(Component.literal(SkoomaRules.HOOKED));
		}
		SkyCraft.LOG.info("SkyCraft-skooma: {} took Skooma (level {}): dose {}, tolerance {} -> {}, {}", player.getName().getString(), amplifier + 1,
			after.doses(), String.format(Locale.ROOT, "%.2f", before.toleranceAt(now, s)), String.format(Locale.ROOT, "%.2f", after.tolerance()),
			dose.hooked() ? "now addicted (chance was " + percent(chance) + ")"
				: before.addicted() ? "addicted, relapse " + after.relapses() + (dose.relieved() ? ", withdrawal over" : "")
				: "not addicted (chance was " + percent(chance) + ")");
	}

	private static void check(ServerPlayer player, long now) {
		Settings s = settings();
		UUID id = player.getUUID();
		MobEffectInstance high = player.getEffect(SkyEffects.SKOOMA);
		if (high != null) {
			HIGHS.put(id, new High(high.getDuration(), high.getAmplifier(), high.isInfiniteDuration()));
		} else {
			High was = HIGHS.remove(id);
			if (was != null && !was.infinite() && was.duration() <= WORN_OFF && player.isAlive() && s.comedown() > 0) {
				comedown(player, was.amplifier(), s);
			}
		}

		Habit habit = habit(player);
		if (habit.lastDose() > now) {
			habit = habit.notAfter(now); // brought into a world whose clock is behind: the days start over
			player.setAttached(HABIT, habit);
		}
		if (!habit.addicted()) {
			SYMPTOMS.remove(id);
			return;
		}
		int level = habit.withdrawal(now, s);
		if (level > 0) {
			habit = habit.endure(CHECK_TICKS);
			player.setAttached(HABIT, habit);
		}
		if (habit.recovers(now, s)) {
			player.setAttached(HABIT, habit.cured());
			endSymptoms(player);
			player.sendSystemMessage(Component.literal(SkoomaRules.CURED));
			SkyCraft.LOG.info("SkyCraft-skooma: {} is cured of Skooma ({} ticks since the last dose, {} of withdrawal lived through)",
				player.getName().getString(), now - habit.lastDose(), habit.endured());
			return;
		}
		symptoms(player, level, now, s);
	}

	/** Withdrawal at {@code level} (0: none yet) for an addict. */
	private static void symptoms(ServerPlayer player, int level, long now, Settings s) {
		Symptoms sy = SYMPTOMS.computeIfAbsent(player.getUUID(), k -> new Symptoms());
		if (level == 0) {
			return;
		}
		MobEffectInstance on = player.getEffect(SkyEffects.WITHDRAWAL);
		if (sy.shown && on == null) {
			// Milk, a totem, a command or dying took it: it comes back in a while.
			sy.shown = false;
			sy.backAt = now + s.symptomsReturn();
			SkyCraft.LOG.info("SkyCraft-skooma: {}'s withdrawal was cleared; back in {} ticks", player.getName().getString(), s.symptomsReturn());
		}
		RandomSource random = player.getRandom();
		if (!sy.shown || on == null || on.getAmplifier() < level - 1) {
			if (!sy.shown && now < sy.backAt) {
				return;
			}
			player.addEffect(new MobEffectInstance(SkyEffects.WITHDRAWAL, MobEffectInstance.INFINITE_DURATION, level - 1, false, false, true));
			player.sendOverlayMessage(Component.literal(SkoomaRules.craving(level, level != sy.level ? 0 : random.nextInt(SkoomaRules.CRAVING_LINES))));
			if (level != sy.level) {
				SkyCraft.LOG.info("SkyCraft-skooma: {} is in Skooma withdrawal {}", player.getName().getString(), "I".repeat(level));
			}
			sy.shown = true;
			sy.level = level;
			return;
		}
		if (random.nextDouble() < SkoomaRules.CRAVING_CHANCE) {
			player.sendOverlayMessage(Component.literal(SkoomaRules.craving(level, random.nextInt(SkoomaRules.CRAVING_LINES))));
		}
		if (random.nextDouble() < SkoomaRules.NAUSEA_CHANCE * level && !player.hasEffect(MobEffects.NAUSEA)) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0, false, false, true));
		}
	}

	/** Takes away the withdrawal SkySkooma gave (endless; one from a command runs its course). */
	private static void endSymptoms(ServerPlayer player) {
		SYMPTOMS.remove(player.getUUID());
		MobEffectInstance on = player.getEffect(SkyEffects.WITHDRAWAL);
		if (on != null && on.isInfiniteDuration()) {
			player.removeEffect(SkyEffects.WITHDRAWAL);
		}
	}

	/** A high that wore off: Slowness and Hunger for a while, II after Redwater. */
	private static void comedown(ServerPlayer player, int amplifier, Settings s) {
		int level = Math.min(amplifier, 1);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, s.comedown(), level));
		player.addEffect(new MobEffectInstance(MobEffects.HUNGER, s.comedown(), level));
		player.sendOverlayMessage(Component.literal(SkoomaRules.COMEDOWN));
	}

	private static String percent(double chance) {
		return String.format(Locale.ROOT, "%.0f%%", chance * 100);
	}
}
