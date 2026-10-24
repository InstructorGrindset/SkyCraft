package dev.skycraft.magic;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.net.SkyNet;
import dev.skycraft.world.SkyClip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shouts and Spells on Minecraft's side (switches {@code shouts} and {@code spells}), on the server. Skyrim has already
 * shouted or cast for real when this runs (its answer said done): this adds what Minecraft's own world gets, around the
 * player whose Skyrim it was, the host's through their client (client MagicClient), a guest's through SkyNet.MagicCast.
 *
 * <ul>
 * <li>Unrelenting Force pushes the mobs in front back, harder with each word; Fire Breath sets them alight and hurts them;
 * Frost Breath slows and hurts them. Mobs only (never players, Skyrim's stand-ins or anyone's tamed animals), within the
 * shout's reach and cone, not behind a wall (Minecraft's blocks or Skyrim's own).
 * <li>Become Ethereal: Minecraft's Resistance V for its time, so nothing hurts the player (but the void and /kill).
 * <li>Slow Time: the mobs around are slowed for its time. Clear Skies: Minecraft's weather clears.
 * <li>A fire, frost or shock spell hurts the mob it's aimed at (a fire spell sets it alight, or lights a fire on the
 * Minecraft block it hits, frost slows); a healing spell heals the player (or, aimed, whoever it hits); a ward gives a
 * moment's Resistance.
 * </ul>
 *
 * Whirlwind Sprint is the client's own dash. Numbers in {@code skycraft-magic.txt} ({@link MagicRules}). Log: SkyCraft-magic.
 */
public final class MagicEffects {
	/** SkyNet.MagicCast's {@code what}: a shout. */
	public static final int SHOUT = 1;
	/** SkyNet.MagicCast's {@code what}: a spell. */
	public static final int SPELL = 2;
	private static MagicRules.@Nullable Settings settings;
	/** What the server takes from each guest's Skyrim: shouts (their voice recovers between them anyway) and spells (pulses). */
	private static final Map<UUID, MagicRules.Rate> GUEST_SHOUTS = new HashMap<>();
	private static final Map<UUID, MagicRules.Rate> GUEST_SPELLS = new HashMap<>();
	private static int logged;

	private MagicEffects() {
	}

	/** The numbers in {@code config/skycraft-magic.txt}, read once. Any thread: the client's keys and the server's effects use them. */
	public static synchronized MagicRules.Settings settings() {
		if (settings == null) {
			List<String> problems = new ArrayList<>();
			settings = MagicRules.parse(Config.read(MagicRules.FILE, MagicRules.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-magic: {} {}", MagicRules.FILE, p));
		}
		return settings;
	}

	/**
	 * The Skyrim of {@code player} shouted ({@code what} SHOUT: a Proto.SHOUT_KIND_* {@code kind} with {@code words}) or cast
	 * (SPELL: a Proto.SPELL_KIND_* that took {@code magicka}, delivered as {@code delivery}): Minecraft's side of it, with the
	 * switch on and the player in the Skyrim mirror. Server thread.
	 */
	public static void apply(ServerPlayer player, int what, int kind, int words, float magicka, int delivery) {
		boolean on = what == SHOUT ? Features.on(Features.Feature.SHOUTS) : what == SPELL && Features.on(Features.Feature.SPELLS);
		if (!on || !player.isAlive() || !SkyDimension.inMirror(player)) {
			return;
		}
		if (what == SHOUT) {
			shout(player, kind, Math.max(1, Math.min(words, 3)), settings());
		} else {
			spell(player, kind, delivery, magicka, settings());
		}
	}

	/**
	 * A guest's own Skyrim shouted or cast (SkyNet.MagicCast): {@link #apply}, as for the host, with guest_parity on and held
	 * to a rate (two shouts in two seconds, eight spells a second) and sane sizes, whatever the guest's client claims. Server thread.
	 */
	public static void fromGuest(ServerPlayer player, int what, int kind, int words, float magicka, int delivery) {
		if (SkyNet.isHost(player) || !Features.on(Features.Feature.GUEST_PARITY) || (what != SHOUT && what != SPELL)) {
			return;
		}
		double now = System.nanoTime() / 1.0E9;
		MagicRules.Rate rate = (what == SHOUT ? GUEST_SHOUTS : GUEST_SPELLS).computeIfAbsent(player.getUUID(),
			id -> what == SHOUT ? new MagicRules.Rate(2, 2.0) : new MagicRules.Rate(8, 1.0));
		if (!rate.take(now)) {
			return;
		}
		float spent = Float.isFinite(magicka) ? Math.max(0.0F, Math.min(magicka, 1000.0F)) : 0.0F;
		apply(player, what, kind, words, spent, delivery);
	}

	// ---- shouts -----------------------------------------------------------------------------------

	private static void shout(ServerPlayer player, int kind, int words, MagicRules.Settings s) {
		ServerLevel level = player.level();
		int touched = 0;
		switch (kind) {
			case Proto.SHOUT_KIND_FORCE, Proto.SHOUT_KIND_FIRE_BREATH, Proto.SHOUT_KIND_FROST_BREATH -> touched = cone(player, level, kind, words, s);
			case Proto.SHOUT_KIND_ETHEREAL -> {
				effect(player, "resistance", MagicRules.etherealTicks(words, s), 4); // Resistance V: no damage gets through
				touched = 1;
			}
			case Proto.SHOUT_KIND_SLOW_TIME -> {
				int ticks = MagicRules.slowTimeTicks(words, s);
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(s.slowTimeReach()), m -> m.isAlive() && !tamed(m))) {
					effect(mob, "slowness", ticks, s.slowTimeLevel() - 1);
					touched++;
				}
			}
			case Proto.SHOUT_KIND_CLEAR_SKIES -> {
				clearSkies(level.getServer(), s);
				touched = 1;
			}
			default -> {
				return; // only its Skyrim part
			}
		}
		if (logged < 40) {
			logged++;
			SkyCraft.LOG.info("SkyCraft-magic: {} shouted (kind {}, {} words): {} in Minecraft", player.getName().getString(), kind, words,
				kind == Proto.SHOUT_KIND_ETHEREAL ? "ethereal" : kind == Proto.SHOUT_KIND_CLEAR_SKIES ? "the weather cleared" : touched + " mobs");
		}
	}

	/** Unrelenting Force and the breaths: every mob in the cone in front within reach and in sight. Returns how many. */
	private static int cone(ServerPlayer player, ServerLevel level, int kind, int words, MagicRules.Settings s) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double reach = MagicRules.shoutReach(kind, words, s);
		DamageSource source = level.damageSources().indirectMagic(player, player);
		float damage = MagicRules.shoutDamage(kind, words, s);
		int count = 0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(reach), m -> m.isAlive() && !tamed(m))) {
			Vec3 at = middle(mob);
			if (at.distanceToSqr(eye) > reach * reach || !MagicRules.inCone(at.x - eye.x, at.y - eye.y, at.z - eye.z, look.x, look.y, look.z, s.coneDegrees())
				|| walled(level, player, eye, at)) {
				continue;
			}
			switch (kind) {
				case Proto.SHOUT_KIND_FORCE -> {
					if (damage > 0.0F) {
						mob.hurtServer(level, source, damage);
					}
					// Away from the player (knockback pushes towards -(x, z)), harder with each word; three words throw it up too.
					mob.knockback(MagicRules.forcePush(words, s), player.getX() - mob.getX(), player.getZ() - mob.getZ(), source, damage);
					if (words >= 3) {
						mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, s.forceLift(), 0.0));
					}
					particles(level, ParticleTypes.CLOUD, at, 6);
				}
				case Proto.SHOUT_KIND_FIRE_BREATH -> {
					mob.igniteForSeconds(MagicRules.breathTicks(words, s) / 20.0F);
					mob.hurtServer(level, source, damage);
					particles(level, ParticleTypes.FLAME, at, 8);
				}
				default -> {
					effect(mob, "slowness", MagicRules.breathTicks(words, s), words); // Slowness II to IV
					mob.hurtServer(level, source, damage);
					particles(level, ParticleTypes.SNOWFLAKE, at, 8);
				}
			}
			count++;
		}
		return count;
	}

	/** Clear Skies: Minecraft's weather clears, as /weather clear does (weather sync then asks Skyrim for clear skies too). */
	private static void clearSkies(@Nullable MinecraftServer server, MagicRules.Settings s) {
		if (server == null) {
			return;
		}
		ServerLevel overworld = server.overworld(); // the mirror's weather, which Tamriel Wilds and Home share
		var source = server.createCommandSourceStack().withSuppressedOutput().withLevel(overworld);
		server.getCommands().performPrefixedCommand(source, "weather clear " + s.clearSkiesSeconds() + "s");
	}

	// ---- spells -----------------------------------------------------------------------------------

	private static void spell(ServerPlayer player, int kind, int delivery, float magicka, MagicRules.Settings s) {
		int action = MagicRules.spellAction(kind);
		if (action == MagicRules.ACTION_NONE) {
			return; // only its Skyrim part
		}
		ServerLevel level = player.level();
		boolean self = delivery == Proto.DELIVERY_SELF;
		double reach = delivery == Proto.DELIVERY_TOUCH ? s.touchReach() : s.spellReach();
		String what;
		switch (action) {
			case MagicRules.ACTION_WARD -> {
				effect(player, "resistance", MagicRules.ticks(s.wardSeconds()), s.wardLevel() - 1);
				what = "warded";
			}
			case MagicRules.ACTION_HEAL -> {
				LivingEntity healed = self ? player : aimed(player, level, reach, true).entity();
				if (healed == null) {
					return;
				}
				healed.heal(MagicRules.spellHeal(magicka, s));
				particles(level, ParticleTypes.HEART, middle(healed), 2);
				what = "healed " + healed.getName().getString();
			}
			default -> {
				if (self) {
					return; // a damage spell on oneself (a cloak) isn't aimed at anything Minecraft has
				}
				Aim aim = aimed(player, level, reach, false);
				float damage = MagicRules.spellDamage(kind, magicka, s);
				if (aim.entity() != null) {
					LivingEntity target = aim.entity();
					target.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
					if (action == MagicRules.ACTION_IGNITE) {
						target.igniteForSeconds((float) s.fireSeconds());
						particles(level, ParticleTypes.FLAME, middle(target), 8);
					} else if (action == MagicRules.ACTION_CHILL) {
						effect(target, "slowness", MagicRules.ticks(s.frostSeconds()), 1);
						particles(level, ParticleTypes.SNOWFLAKE, middle(target), 8);
					} else {
						particles(level, ParticleTypes.ELECTRIC_SPARK, middle(target), 10);
					}
					what = "hit " + target.getName().getString() + " for " + String.format("%.1f", damage);
				} else if (action == MagicRules.ACTION_IGNITE && aim.block() != null) {
					what = "burned a block area" + (ignite(player, level, aim.block(), s) ? ", lit a fire" : "");
				} else {
					return; // into the air, or at Skyrim's own walls: only Skyrim's part
				}
			}
		}
		if (logged < 40) {
			logged++;
			SkyCraft.LOG.info("SkyCraft-magic: {} cast a spell (kind {}, {} magicka): {}", player.getName().getString(), kind, String.format("%.1f", magicka), what);
		}
	}

	/** What an aimed spell reaches first: a living entity, else a block (Minecraft's or Skyrim's), else nothing. */
	private record Aim(@Nullable LivingEntity entity, @Nullable BlockHitResult block) {
	}

	/**
	 * Along the player's look up to {@code reach}: the nearest living entity before any wall (a mob, or with
	 * {@code anyone} a player or a tamed animal too; never Skyrim's stand-ins, whose actors Skyrim's own spell has met already).
	 */
	private static Aim aimed(ServerPlayer player, ServerLevel level, double reach, boolean anyone) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
		BlockHitResult wall = clip(level, player, eye, end);
		double limit = wall.getType() == HitResult.Type.MISS ? reach : Math.sqrt(eye.distanceToSqr(wall.getLocation()));
		LivingEntity best = null;
		double bestDistance = limit;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0), e -> e != player && e.isAlive()
			&& !(e instanceof SkyrimActorEntity) && (anyone || e instanceof Mob && !tamed(e)))) {
			Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent()) {
				double d = Math.sqrt(eye.distanceToSqr(hit.get()));
				if (d < bestDistance) {
					bestDistance = d;
					best = e;
				}
			}
		}
		return new Aim(best, best == null && wall.getType() != HitResult.Type.MISS ? wall : null);
	}

	/**
	 * A fire spell that hit a block: the mobs within fire_splash catch fire, and with fire_lights_blocks a fire is lit on a Minecraft
	 * block's face where there's room for it (never on Skyrim's own ground or walls). Returns whether it lit one.
	 */
	private static boolean ignite(ServerPlayer player, ServerLevel level, BlockHitResult hit, MagicRules.Settings s) {
		Vec3 at = hit.getLocation();
		double r = s.fireSplash();
		AABB splash = new AABB(at.x - r, at.y - r, at.z - r, at.x + r, at.y + r, at.z + r);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, splash, m -> m.isAlive() && !tamed(m))) {
			mob.igniteForSeconds((float) s.fireSeconds());
		}
		particles(level, ParticleTypes.FLAME, at, 6);
		if (!s.fireLightsBlocks() || hit instanceof SkyClip.SkyrimHitResult || !player.mayBuild()) {
			return false;
		}
		BlockPos firePos = hit.getBlockPos().relative(hit.getDirection());
		if (!level.getBlockState(firePos).isAir() || !BaseFireBlock.canBePlacedAt(level, firePos, hit.getDirection())) {
			return false;
		}
		level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
		return true;
	}

	// ---- helpers ----------------------------------------------------------------------------------

	/** The first block on the way from {@code from} to {@code to}: Minecraft's, or in the mirror Skyrim's own geometry when nearer. */
	private static BlockHitResult clip(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		BlockHitResult vanilla = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return SkyDimension.inMirror(level) ? SkyClip.refine(from, to, vanilla, SkyClip.Use.PROJECTILE) : vanilla;
	}

	/** Whether a wall stands between the player's eyes and {@code at}. */
	private static boolean walled(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 at) {
		return clip(level, player, eye, at).getType() != HitResult.Type.MISS;
	}

	/** Someone's tamed animal or horse: never a target. */
	private static boolean tamed(Entity entity) {
		return entity instanceof OwnableEntity ownable && ownable.getOwner() != null;
	}

	private static Vec3 middle(Entity entity) {
		AABB box = entity.getBoundingBox();
		return new Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
	}

	/** A vanilla effect by its id ("slowness", "resistance") at {@code amplifier} (0 is level I), if there's time for it. */
	private static void effect(LivingEntity entity, String id, int ticks, int amplifier) {
		Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(id)).orElse(null);
		if (effect != null && ticks > 0) {
			entity.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amplifier)));
		}
	}

	private static void particles(ServerLevel level, ParticleOptions particle, Vec3 at, int count) {
		level.sendParticles(particle, at.x, at.y, at.z, count, 0.3, 0.3, 0.3, 0.02);
	}
}
