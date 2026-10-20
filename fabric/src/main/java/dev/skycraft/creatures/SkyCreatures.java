package dev.skycraft.creatures;

import dev.skycraft.SkyCraft;
import dev.skycraft.combat.SkyCombat;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyWater;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Minecraft's creatures and Skyrim's actors in the mirror, server side, for the host (whose Skyrim is on the link).
 *
 * <ul>
 * <li>Mobs and NPCs fight ({@code mobs_fight_npcs}): Minecraft's monsters (from spawners, eggs and commands: the mirror has
 * no natural spawns) go for the stand-ins of Skyrim's people and creatures near them ({@link CreatureRules#monsterTargets});
 * their hits reach Skyrim as nobody's (EV_MOB MOB_HIT, MOB_BY_MONSTER). Skyrim hears where each monster is (MOB_NEAR) and its
 * guards, the player's followers and enemies, and whoever a monster goes for, swing back when it's next to them; their swing
 * comes back as Proto.IN_MOB_HIT and hurts the monster as from the actor's stand-in ({@link #npcHitMob}), so the monster
 * fights that actor in turn.
 * <li>Tamed wolves fight Skyrim's enemies ({@code wolves_fight_skyrim}): the host's tamed wolf goes for a stand-in hostile to
 * the player and fighting, as Minecraft's wolves go for whatever fights their owner; its hits are Minecraft hits by the
 * player in Skyrim (MOB_BY_WOLF) and count as the player's for the kill's drops and XP (vanilla credits a tamed wolf's hits to
 * its owner: LivingEntity.lastHurtByPlayer).
 * <li>Skyrim's undead in daylight ({@code creatures_and_light}): the stand-in of an undead actor (Proto.ACTOR_UNDEAD) outdoors
 * in Skyrim's daylight under open sky is set alight the way a zombie is, and the fire's damage reaches Skyrim as nobody's
 * (MOB_BY_SUN) with its flames shown.
 * </ul>
 *
 * Server thread only. The mirror's ground is Skyrim's collision, not blocks, so Minecraft's path finding finds no way across
 * it: a creature after a stand-in whose navigation gave up is walked straight at it ({@link CreatureRules#steer}).
 */
public final class SkyCreatures {
	/** Creatures this far (blocks) from the host are looked at. */
	private static final double HUNT_RANGE = 48.0;
	/** Monsters this near (blocks) the host are told to Skyrim, at most {@link #MAX_NEAR} every half second. */
	private static final double NEAR_RANGE = 32.0;
	private static final int MAX_NEAR = 24;
	/** Archers close in on a stand-in only from further than this (blocks). */
	private static final double RANGED_REACH = 12.0;
	/** Blocks above an undead stand-in's head looked through for Skyrim's own roofs (bridges, ruins, rock). */
	private static final int ROOF_BLOCKS = 24;
	/** A swing from further than this (blocks) is a stale one (the actor or the creature moved on). */
	private static final double SWING_REACH = 8.0;

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	/** Creatures (entity ids) this sent after a stand-in: steered at it until they stop. */
	private static final Set<Integer> HUNTING = new HashSet<>();
	private static int logged;

	private SkyCreatures() {
	}

	public static void init() {
		// After SkyCombat's tick (registered first): the stand-ins are in step with Skyrim's actor table by then.
		ServerTickEvents.END_SERVER_TICK.register(SkyCreatures::serverTick);
		SkyHorses.init();
	}

	private static void serverTick(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		ServerPlayer host = host(server);
		if (!SkyLink.active() || host == null) {
			HUNTING.clear();
			NightMonsters.reset();
			return;
		}
		int tick = server.getTickCount();
		SkyHorses.tick(server, host, tick);
		if (!dev.skycraft.SkyDimension.inMirror(host)) {
			HUNTING.clear(); // the stand-ins are gone while the host is away
			return;
		}
		ServerLevel level = host.level();
		flushHits(tick);
		if (tick % 5 == 0) {
			hunt(level, host);
		}
		if (tick % 2 == 0) {
			steer(level);
		}
		if (tick % 10 == 0) {
			tellSkyrim(level, host);
		}
		if (tick % 20 == 0) {
			sunlight(level);
		}
		NightMonsters.tick(host, players, tick);
	}

	/**
	 * Which Proto.MOB_BY_* this damage on a stand-in is, or -1 when it's the player's (their hit, arrow, TNT, Fire Aspect's
	 * flames: Skyrim takes it as the player's, as before) or its switch is off.
	 */
	public static int creatureAttacker(SkyrimActorEntity proxy, DamageSource source) {
		Entity cause = source.getEntity();
		if (cause instanceof Player || cause instanceof SkyrimActorEntity) {
			return -1;
		}
		if (cause instanceof LivingEntity creature) {
			if (creature.getType() == EntityType.WOLF && ownerOf(creature) != null) {
				return Features.on(Features.Feature.WOLVES_FIGHT_SKYRIM) ? Proto.MOB_BY_WOLF : -1;
			}
			return Features.on(Features.Feature.MOBS_FIGHT_NPCS) ? Proto.MOB_BY_MONSTER : -1;
		}
		if (cause == null && proxy.sunBurning() && source.is(DamageTypeTags.IS_FIRE)) {
			return Proto.MOB_BY_SUN;
		}
		return -1;
	}

	/** The player who tamed this creature (a wolf), or null. */
	public static @Nullable UUID ownerOf(@Nullable Entity creature) {
		if (creature instanceof TamableAnimal tamed && tamed.isTame() && tamed.getOwner() instanceof Player owner) {
			return owner.getUUID();
		}
		return null;
	}

	private static boolean tamedWolfOf(Mob mob, ServerPlayer host) {
		return mob.getType() == EntityType.WOLF && mob instanceof TamableAnimal tamed && tamed.isTame() && tamed.isOwnedBy(host);
	}

	/**
	 * A Minecraft monster that goes for Skyrim's actors: the monster category, but not creepers (they hunt players only, or
	 * they'd blow craters in Skyrim's towns), endermen and zombified piglins (they keep to themselves until provoked).
	 * Monsters run by a brain instead of goals (piglins, hoglins, wardens, breezes) ignore the target set here.
	 */
	private static boolean huntingMonster(Mob mob) {
		EntityType<?> type = mob.getType();
		return type.getCategory() == MobCategory.MONSTER && type != EntityType.CREEPER && type != EntityType.ENDERMAN && type != EntityType.ZOMBIFIED_PIGLIN;
	}

	private static boolean undeadMob(Mob mob) {
		return mob.getType().is(EntityTypeTags.UNDEAD);
	}

	/** Every half second: who goes for whom (monsters and the host's wolves pick Skyrim stand-ins). */
	private static void hunt(ServerLevel level, ServerPlayer host) {
		boolean monsters = Features.on(Features.Feature.MOBS_FIGHT_NPCS);
		boolean wolves = Features.on(Features.Feature.WOLVES_FIGHT_SKYRIM);
		Collection<SkyrimActorEntity> proxies = SkyCombat.proxies();
		if ((!monsters && !wolves) || proxies.isEmpty()) {
			return;
		}
		for (Mob mob : level.getEntitiesOfClass(Mob.class, host.getBoundingBox().inflate(HUNT_RANGE), Mob::isAlive)) {
			boolean wolf = tamedWolfOf(mob, host);
			boolean monster = !wolf && huntingMonster(mob);
			if (!(wolf && wolves) && !(monster && monsters)) {
				continue;
			}
			if (wolf && ((TamableAnimal) mob).isOrderedToSit()) {
				continue; // sitting, as told
			}
			LivingEntity target = mob.getTarget();
			if (target instanceof SkyrimActorEntity proxy && (proxy.isRemoved() || SkyCombat.proxy(proxy.formId()) != proxy
				|| CreatureRules.dead(proxy.getSkyFlags()) || (wolf && !CreatureRules.wolfTargets(proxy.getSkyFlags())))) {
				// Its Skyrim actor is gone, dead, or (for a wolf) no longer fighting the player: let it go.
				mob.setTarget(null);
				target = null;
			}
			CreatureRules.Current current = target == null || !target.isAlive() ? CreatureRules.Current.NOTHING
				: target instanceof Player ? CreatureRules.Current.PLAYER : CreatureRules.Current.OTHER;
			if (current == CreatureRules.Current.OTHER) {
				if (target instanceof SkyrimActorEntity) {
					HUNTING.add(mob.getId()); // (one a Skyrim actor's swing set on it, HurtByTargetGoal)
				}
				continue;
			}
			boolean undead = undeadMob(mob);
			SkyrimActorEntity best = null;
			double bestDistance = Double.MAX_VALUE;
			for (SkyrimActorEntity proxy : proxies) {
				if (proxy.isRemoved() || proxy.level() != level) {
					continue;
				}
				int flags = proxy.getSkyFlags();
				if (wolf ? !CreatureRules.wolfTargets(flags) : !CreatureRules.monsterTargets(undead, flags)) {
					continue;
				}
				// A wolf picks the enemy nearest its owner (the one in the fight), a monster the actor nearest itself.
				double d = Math.sqrt(wolf ? proxy.distanceToSqr(host) : proxy.distanceToSqr(mob));
				if (d < bestDistance) {
					bestDistance = d;
					best = proxy;
				}
			}
			if (best == null) {
				continue;
			}
			boolean go = wolf ? CreatureRules.wolfSwitches(current, bestDistance) && mob.distanceToSqr(best) <= HUNT_RANGE * HUNT_RANGE
				: CreatureRules.monsterSwitches(current, bestDistance, current == CreatureRules.Current.PLAYER ? Math.sqrt(mob.distanceToSqr(target)) : -1.0);
			if (!go) {
				continue;
			}
			mob.setTarget(best);
			HUNTING.add(mob.getId());
			if (logged < 40) {
				logged++;
				SkyCraft.LOG.info("SkyCraft-creatures: {} goes for {} ({}, {} blocks off)", mob.getName().getString(), best.skyrimName(),
					String.format("%08X", best.formId()), Math.round(Math.sqrt(mob.distanceToSqr(best))));
			}
		}
	}

	/** Walks the creatures this sent after a stand-in straight at it while Minecraft's path finding has no way there. */
	private static void steer(ServerLevel level) {
		for (Iterator<Integer> it = HUNTING.iterator(); it.hasNext(); ) {
			Entity entity = level.getEntity(it.next());
			if (!(entity instanceof Mob mob) || !mob.isAlive() || !(mob.getTarget() instanceof SkyrimActorEntity proxy) || proxy.isRemoved()) {
				it.remove();
				continue;
			}
			double distance = Math.sqrt(mob.distanceToSqr(proxy));
			double reach = (mob.getBbWidth() + proxy.getBbWidth()) * 0.5 + 1.0;
			boolean ranged = mob.getMainHandItem().is(Items.BOW) || mob.getMainHandItem().is(Items.CROSSBOW);
			mob.getLookControl().setLookAt(proxy, 30.0F, 30.0F);
			if (CreatureRules.steer(distance, reach, ranged, RANGED_REACH, !mob.getNavigation().isDone())) {
				mob.getMoveControl().setWantedPosition(proxy.getX(), proxy.getY(), proxy.getZ(), 1.0);
			}
		}
	}

	/** Every half second: where the monsters near the host are (and the host's wolves on the hunt), for Skyrim's NPCs to swing at. */
	private static void tellSkyrim(ServerLevel level, ServerPlayer host) {
		boolean monsters = Features.on(Features.Feature.MOBS_FIGHT_NPCS);
		boolean wolves = Features.on(Features.Feature.WOLVES_FIGHT_SKYRIM);
		if (!monsters && !wolves) {
			return;
		}
		int sent = 0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, host.getBoundingBox().inflate(NEAR_RANGE), Mob::isAlive)) {
			int traits = 0;
			if (tamedWolfOf(mob, host)) {
				if (!wolves || !(mob.getTarget() instanceof SkyrimActorEntity)) {
					continue; // only a wolf in the fight: the player's enemies swing at it
				}
				traits |= Proto.MOB_PLAYERS_WOLF;
			} else if (!monsters || mob.getType().getCategory() != MobCategory.MONSTER) {
				continue;
			}
			if (undeadMob(mob)) {
				traits |= Proto.MOB_UNDEAD;
			}
			int target = mob.getTarget() instanceof SkyrimActorEntity proxy ? proxy.formId() : 0;
			SkyLink.pushEventAt(Proto.EV_MOB, mob.getId(), mob.getX(), mob.getY(), mob.getZ(), mob.getBbWidth() * 0.5F, CreatureRules.nearFields(traits), target);
			if (++sent >= MAX_NEAR) {
				break;
			}
		}
	}

	/** This tick's creature and daylight hits on the stand-ins, to Skyrim (after the tick's attacks have all landed). */
	private static void flushHits(int tick) {
		for (SkyrimActorEntity proxy : SkyCombat.proxies()) {
			SkyrimActorEntity.CreatureHit hit = proxy.takeCreatureHit();
			if (hit == null || (hit.damage <= 0.0F && hit.push <= 0.0F)) {
				continue;
			}
			if (hit.attacker == Proto.MOB_BY_WOLF && hit.owner != null) {
				// Minecraft counts a tamed wolf's hits as its owner's: so does the kill's loot and XP (SkyLoot).
				proxy.creditPlayer(hit.owner);
				dev.skycraft.loot.SkyLoot.recordHit(proxy.formId(), tick);
			}
			SkyLink.pushEvent(Proto.EV_MOB, proxy.formId(), hit.damage, (float) hit.pushX, (float) hit.pushZ, hit.push, CreatureRules.hitFields(hit.flags, hit.attacker),
				hit.weapon);
			if (logged < 40) {
				logged++;
				SkyCraft.LOG.info("SkyCraft-creatures: {} hurt {} for {}{}", hit.attacker == Proto.MOB_BY_WOLF ? "your wolf" : hit.attacker == Proto.MOB_BY_SUN ? "daylight" : "a monster",
					proxy.skyrimName(), hit.damage, hit.push > 0.0F ? " (knockback " + hit.push + ")" : "");
			}
		}
	}

	/** Once a second: Skyrim's undead outdoors in its daylight catch fire, as zombies do. */
	private static void sunlight(ServerLevel level) {
		if (!Features.on(Features.Feature.CREATURES_AND_LIGHT) || !SkyLink.readSkyState(SKY) || !SKY.inGame() || SKY.loading()) {
			return;
		}
		boolean outdoors = (SKY.place & Proto.PLACE_INTERIOR) == 0;
		if (!outdoors || !CreatureRules.daylight(SKY.gameHour)) {
			return;
		}
		for (SkyrimActorEntity proxy : SkyCombat.proxies()) {
			int flags = proxy.getSkyFlags();
			if (!CreatureRules.undead(flags) || proxy.isRemoved()) {
				continue;
			}
			BlockPos feet = BlockPos.containing(proxy.getX(), proxy.getY(), proxy.getZ());
			BlockPos head = BlockPos.containing(proxy.getX(), proxy.getY() + proxy.getBbHeight(), proxy.getZ());
			boolean skyOpen = level.canSeeSky(head) && !skyrimRoof(head);
			boolean wet = proxy.isInWater() || level.isRainingAt(head) || SkyWater.depthIn(feet) > 0.0F;
			if (CreatureRules.sunBurns(flags, outdoors, SKY.gameHour, skyOpen, wet, proxy.isOnFire(), level.getRandom().nextDouble())) {
				proxy.igniteForSeconds(CreatureRules.SUN_BURN_SECONDS);
				proxy.markSunBurning();
				if (logged < 40) {
					logged++;
					SkyCraft.LOG.info("SkyCraft-creatures: {} burns in the daylight (Skyrim hour {})", proxy.skyrimName(), String.format("%.1f", SKY.gameHour));
				}
			}
		}
	}

	/** Skyrim's geometry somewhere over this cell (a bridge, a ruin's roof, an overhang): it shades what's under it. */
	private static boolean skyrimRoof(BlockPos head) {
		for (int dy = 1; dy <= ROOF_BLOCKS; dy++) {
			if (SkyCollision.hasGeometry(head.above(dy))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A Skyrim actor's swing landed on a Minecraft creature (Proto.IN_MOB_HIT, from the host's client): the creature is hurt as
	 * from the actor's stand-in, so it turns on that actor (its HurtByTargetGoal), with Minecraft's knockback away from it.
	 * Server thread.
	 */
	public static void npcHitMob(MinecraftServer server, int entityId, float damage, int attackerFormId) {
		if (!Features.on(Features.Feature.MOBS_FIGHT_NPCS) && !Features.on(Features.Feature.WOLVES_FIGHT_SKYRIM)) {
			return;
		}
		ServerPlayer host = host(server);
		if (host == null || !dev.skycraft.SkyDimension.inMirror(host) || !(damage > 0.0F)) {
			return;
		}
		ServerLevel level = host.level();
		Entity entity = level.getEntity(entityId);
		if (!(entity instanceof LivingEntity creature) || !creature.isAlive() || entity instanceof Player || entity instanceof SkyrimActorEntity) {
			return;
		}
		SkyrimActorEntity attacker = SkyCombat.proxy(attackerFormId);
		if (attacker != null && attacker.distanceToSqr(creature) > SWING_REACH * SWING_REACH) {
			return; // a swing from before one of them moved on
		}
		DamageSource source = attacker != null ? level.damageSources().mobAttack(attacker) : level.damageSources().generic();
		float before = creature.getHealth();
		boolean hurt = creature.hurtServer(level, source, damage);
		if (logged < 40) {
			logged++;
			SkyCraft.LOG.info("SkyCraft-creatures: {} hit {} for {}: health {} -> {}{}", attacker != null ? attacker.skyrimName() : String.format("%08X", attackerFormId),
				creature.getName().getString(), damage, before, creature.getHealth(), hurt ? "" : " (no effect)");
		}
	}

	private static @Nullable ServerPlayer host(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (dev.skycraft.net.SkyNet.isHost(player)) {
				return player;
			}
		}
		return null;
	}
}
