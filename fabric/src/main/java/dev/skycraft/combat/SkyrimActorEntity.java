package dev.skycraft.combat;

import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An invisible stand-in for one Skyrim actor, so Minecraft's own combat (swords, crits, sweeps,
 * enchantments, attack cooldown, bows, tridents) can target and hit Skyrim NPCs. What it receives is
 * collected into one hit per tick and forwarded to the real actor; its own health never drops.
 */
public class SkyrimActorEntity extends LivingEntity {
	private static final EntityDataAccessor<Integer> FORM_ID = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.FLOAT);
	// Proto.VENDOR_* mask of the Skyrim merchant behind us, 0 if not a merchant (trade.TradeRules.DEALER_ONLY for a Skooma
	// dealer Skyrim doesn't make a merchant). Synced so the client knows a right click on a merchant opens the shop (and
	// isn't one for Skyrim, client.ClickActivate).
	private static final EntityDataAccessor<Integer> VENDOR = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.INT);
	// The Skyrim actor's Proto.ACTOR_* flags (hostile, dead, essential, companion, waiting, its animal kind). Synced so
	// the client knows a click on a Skyrim animal is for the companions feature (companion.SkyCompanions), not Skyrim.
	private static final EntityDataAccessor<Integer> SKY_FLAGS = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.INT);
	// Whose Skyrim the actor is in: the entity id of the guest whose client sent it (party.GuestParity), 0 for the host's
	// (on the shared-memory link). Synced so each client picks and follows only its own Skyrim's actors.
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SkyrimActorEntity.class, EntityDataSerializers.INT);

	/**
	 * Client side: whether an OWNER entity id is this client's own Skyrim (0 on the host's machine, the player's own id
	 * on a guest's), set by the client; and whether only those can be picked (guest parity on: nobody hits or clicks an
	 * actor their own Skyrim doesn't draw). Off, every stand-in is picked as before.
	 */
	private static volatile java.util.function.IntPredicate localOwner = id -> id == 0;
	private static volatile boolean ownOnly;

	// The actor's Skyrim name (server side), without what the stand-in's own name adds to it ("(tamed)").
	private String skyrimName = "";
	// The last player whose attack reached us (melee, their arrow or trident), server side: the kill and
	// its loot are theirs (loot.SkyLoot.killer).
	private @Nullable UUID lastPlayerHit;
	// The guest whose Skyrim this actor is in (server side), null for the host's.
	private @Nullable UUID owner;

	// This tick's hit, flushed to Skyrim by SkyCombat after all attacks for the tick have landed
	// (Player.attack adds its sprint/enchantment knockback after hurtServer returns).
	private float pendingDamage;
	private int pendingFlags;
	private int pendingWeapon;
	private double pushX, pushZ;
	private float pushStrength;
	private boolean hitThisTick;
	// For Skyrim's sneak and power attacks (CombatRules.hitFlags): the swing a player began on us this tick (its attack
	// strength and sprint, before Minecraft resets them), whether a player's own swing landed, and whether a player who
	// landed a hit was crouching.
	private float swingStrength;
	private boolean swingSprinting;
	private boolean meleeThisTick;
	private boolean crouchedThisTick;

	/**
	 * This tick's hits from Minecraft's own creatures (a monster, a player's tamed wolf) and from daylight, which aren't the
	 * player's: flushed to Skyrim as EV_MOB by creatures.SkyCreatures once the tick's attacks have landed.
	 */
	public static final class CreatureHit {
		public float damage;
		/** Proto.HIT_* */
		public int flags;
		/** Proto.WEAPON_* */
		public int weapon;
		/** Proto.MOB_BY_* */
		public int attacker;
		/** MOB_BY_WOLF: the wolf's owner, whose hit Minecraft counts it as. */
		public @Nullable UUID owner;
		public double pushX, pushZ;
		public float push;
	}

	private @Nullable CreatureHit creatureHit;
	// Minecraft's melee monsters strike once a second, but in the mirror (no paths to follow) their attack goal keeps
	// starting over and would strike twice as often: each creature's melee hit counts once per CreatureRules.MELEE_COOLDOWN_TICKS.
	private final java.util.Map<Integer, Integer> creatureMelee = new java.util.HashMap<>();
	// Set alight by the sun (creatures_and_light): its fire is no player's doing. Cleared once the fire is out.
	private boolean sunBurning;

	public SkyrimActorEntity(EntityType<? extends SkyrimActorEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true;
		this.setInvisible(true);
		this.setSilent(true);
	}

	public int formId() {
		return this.entityData.get(FORM_ID);
	}

	public void setFormId(int formId) {
		this.entityData.set(FORM_ID, formId);
	}

	public int getVendor() {
		return this.entityData.get(VENDOR);
	}

	public void setVendor(int vendor) {
		this.entityData.set(VENDOR, vendor);
	}

	public int getSkyFlags() {
		return this.entityData.get(SKY_FLAGS);
	}

	public void setSkyFlags(int flags) {
		this.entityData.set(SKY_FLAGS, flags);
	}

	/** The guest whose Skyrim this actor is in (party.GuestParity), or null for the host's. Server side. */
	public @Nullable UUID owner() {
		return this.owner;
	}

	/** This stand-in is for an actor of {@code guest}'s own Skyrim (party.GuestParity). */
	public void setOwner(ServerPlayer guest) {
		this.owner = guest.getUUID();
		this.entityData.set(OWNER, guest.getId());
	}

	/** Client side: how to tell this client's own Skyrim's stand-ins, and whether only those are picked (see {@link #shownHere}). */
	public static void setClientOwnership(java.util.function.IntPredicate local, boolean onlyOwn) {
		localOwner = local;
		ownOnly = onlyOwn;
	}

	/** Client side: the actor is in this client's own Skyrim (ProxySync moves it to where that Skyrim has it). */
	public boolean isLocalPlayers() {
		return localOwner.test(this.entityData.get(OWNER));
	}

	/** Client side: the actor can be picked here (crosshair, clicks): this client's own Skyrim's, or any with guest parity off. */
	public boolean shownHere() {
		return !ownOnly || this.isLocalPlayers();
	}

	@Override
	public boolean isPickable() {
		// Another player's Skyrim's actor is an invisible box this player's Skyrim never draws: their crosshair passes it.
		// The server's own checks (projectiles) still find it: the world is shared.
		return super.isPickable() && (!this.level().isClientSide() || this.shownHere());
	}

	/** The actor's Skyrim name, as the actor table has it. */
	public String skyrimName() {
		return this.skyrimName.isEmpty() ? this.getName().getString() : this.skyrimName;
	}

	/** The last player whose attack reached this stand-in, or null if no player has hit it. */
	public @Nullable UUID lastPlayerHit() {
		return this.lastPlayerHit;
	}

	/** A hit Minecraft counts as this player's (their tamed wolf's): the kill and its loot are theirs, as for their own hit. */
	public void creditPlayer(UUID player) {
		this.lastPlayerHit = player;
	}

	/** The sun just set this undead stand-in alight. */
	public void markSunBurning() {
		this.sunBurning = true;
	}

	/** It's burning because of the sun (not a player's Fire Aspect or flame arrow). */
	public boolean sunBurning() {
		return this.sunBurning;
	}

	private CreatureHit creatureHit() {
		if (this.creatureHit == null) {
			this.creatureHit = new CreatureHit();
		}
		return this.creatureHit;
	}

	/** Returns this tick's hits from Minecraft's creatures and daylight and clears them; null if there were none. */
	public @Nullable CreatureHit takeCreatureHit() {
		CreatureHit hit = this.creatureHit;
		this.creatureHit = null;
		return hit;
	}

	/** Its Skyrim name, and the stand-in's own name ({@code shown}: the Skyrim name, or "Meeko (tamed)"), when they change. */
	public void setNames(String skyrim, String shown) {
		this.skyrimName = skyrim;
		if (shown.isEmpty()) {
			return;
		}
		var current = this.getCustomName();
		if (current == null || !current.getString().equals(shown)) {
			this.setCustomName(net.minecraft.network.chat.Component.literal(shown));
		}
	}

	/**
	 * Right-clicking a Skyrim merchant with an empty hand opens its shop as a villager trade screen;
	 * sneaking and right-clicking it with goods in hand sells them for Skyrim gold (trade.SkySell).
	 * Both sides take the click, as a villager does, so the client counts it as used and Skyrim
	 * doesn't also start a conversation (client.ClickActivate), and the held item isn't used (eaten,
	 * placed). On a Skyrim dog, wolf or horse the companions feature may take it (a bone, horse food,
	 * an empty hand on a tamed one: see companion.SkyCompanions). Any other click passes, and with
	 * nothing else to use it on, Skyrim activates the NPC (talks to them, rides the horse) as G does.
	 */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
		if (hand == InteractionHand.MAIN_HAND && !player.getMainHandItem().isEmpty() && player.isSecondaryUseActive() && this.getVendor() != 0
			&& dev.skycraft.config.Features.on(dev.skycraft.config.Features.Feature.SELL_TO_MERCHANTS)) {
			if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
				dev.skycraft.trade.SkySell.offer(serverPlayer, this);
			}
			return InteractionResult.SUCCESS;
		}
		if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty() && this.getVendor() != 0
			&& dev.skycraft.config.Features.on(dev.skycraft.config.Features.Feature.MERCHANTS)) {
			if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
				dev.skycraft.trade.SkyTrade.open(serverPlayer, this);
			}
			return InteractionResult.SUCCESS;
		}
		if (this.getVendor() == 0) {
			InteractionResult companion = dev.skycraft.companion.SkyCompanions.interact(this, player, hand);
			if (companion != null) {
				return companion;
			}
		}
		return super.interact(player, hand, at);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(FORM_ID, 0);
		builder.define(WIDTH, 0.6F);
		builder.define(HEIGHT, 1.8F);
		builder.define(VENDOR, 0);
		builder.define(SKY_FLAGS, 0);
		builder.define(OWNER, 0);
	}

	public void setSize(float width, float height) {
		if (Math.abs(this.entityData.get(WIDTH) - width) > 0.01F || Math.abs(this.entityData.get(HEIGHT) - height) > 0.01F) {
			this.entityData.set(WIDTH, width);
			this.entityData.set(HEIGHT, height);
			this.refreshDimensions();
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (WIDTH.equals(accessor) || HEIGHT.equals(accessor)) {
			this.refreshDimensions();
		}
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return EntityDimensions.scalable(this.entityData.get(WIDTH), this.entityData.get(HEIGHT));
	}

	@Override
	protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
		// Minecraft has applied everything (crit, sharpness, strength, cooldown, invulnerability
		// frames). Hand the result to Skyrim instead of lowering our own health.
		if (this.isInvulnerableTo(level, source) || dmg <= 0.0F) {
			return;
		}
		// A Minecraft creature's bite, swing or arrow, or daylight on the undead (mobs_fight_npcs, wolves_fight_skyrim,
		// creatures_and_light): Skyrim takes it as nobody's hit, or as the wolf owner's, not as this player's.
		int by = dev.skycraft.creatures.SkyCreatures.creatureAttacker(this, source);
		if (by >= 0) {
			boolean projectile = source.getDirectEntity() instanceof Projectile;
			Entity cause = source.getEntity();
			if (cause != null && !projectile && by != Proto.MOB_BY_SUN) {
				int now = level.getServer().getTickCount();
				Integer last = this.creatureMelee.get(cause.getId());
				if (last != null && now >= last && now - last < dev.skycraft.creatures.CreatureRules.MELEE_COOLDOWN_TICKS) {
					return;
				}
				this.creatureMelee.put(cause.getId(), now);
				if (this.creatureMelee.size() > 32) {
					this.creatureMelee.clear();
				}
			}
			CreatureHit hit = this.creatureHit();
			hit.damage += dmg;
			hit.attacker = by;
			hit.weapon = weaponClass(source);
			if (projectile) {
				hit.flags |= Proto.HIT_PROJECTILE;
			}
			if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
				hit.flags |= Proto.HIT_FIRE;
			}
			if (by == Proto.MOB_BY_WOLF) {
				hit.owner = dev.skycraft.creatures.SkyCreatures.ownerOf(cause);
			}
			this.getCombatTracker().recordDamage(source, dmg);
			return;
		}
		this.pendingDamage += dmg;
		if (source.getEntity() instanceof Player player) {
			this.lastPlayerHit = player.getUUID();
			if (source.getDirectEntity() == player) {
				this.meleeThisTick = true;
			}
			if (player.isCrouching()) {
				this.crouchedThisTick = true;
			}
		}
		if (source.getDirectEntity() instanceof Projectile) {
			this.pendingFlags |= Proto.HIT_PROJECTILE;
		}
		this.pendingWeapon = weaponClass(source);
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
			this.pendingFlags |= Proto.HIT_FIRE;
		}
		this.hitThisTick = true;
		this.getCombatTracker().recordDamage(source, dmg);
	}

	@Override
	public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
		// Skyrim owns this actor's position. Remember the strongest push for Skyrim's stagger:
		// Minecraft pushes towards -(xd, zd).
		double len = Math.sqrt(xd * xd + zd * zd);
		int by = dev.skycraft.creatures.SkyCreatures.creatureAttacker(this, source);
		if (by >= 0) {
			// A creature's push goes with its hit, never with the player's (Skyrim would take it as theirs).
			CreatureHit hit = this.creatureHit();
			hit.attacker = by;
			if (len > 1e-6 && power > hit.push) {
				hit.push = (float) power;
				hit.pushX = -xd / len;
				hit.pushZ = -zd / len;
			}
			return;
		}
		if (len > 1e-6 && power > this.pushStrength) {
			this.pushStrength = (float) power;
			this.pushX = -xd / len;
			this.pushZ = -zd / len;
		}
		this.hitThisTick = true;
	}

	/** Player.crit() was called on us this tick. */
	public void markCritical() {
		this.pendingFlags |= Proto.HIT_CRITICAL;
	}

	/**
	 * A player began a melee attack on us (Player.attack, mixin.PlayerAttackMixin): how charged it was and whether they
	 * sprinted, read before Minecraft resets them, for Skyrim's power attacks.
	 */
	public void noteSwing(float attackStrength, boolean sprinting) {
		if (attackStrength >= this.swingStrength) {
			this.swingStrength = attackStrength;
			this.swingSprinting = sprinting;
		}
	}

	/** Which kind of Skyrim weapon impact this hit should look and sound like. */
	private static int weaponClass(DamageSource source) {
		if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident) {
			return Proto.WEAPON_PIERCE;
		}
		if (source.getDirectEntity() instanceof Projectile) {
			return Proto.WEAPON_ARROW;
		}
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null && source.getEntity() instanceof LivingEntity attacker) {
			weapon = attacker.getMainHandItem();
		}
		if (weapon == null || weapon.isEmpty()) {
			return Proto.WEAPON_UNARMED;
		}
		if (weapon.is(ItemTags.SWORDS)) {
			return Proto.WEAPON_BLADE;
		}
		if (weapon.is(ItemTags.AXES)) {
			return Proto.WEAPON_AXE;
		}
		if (weapon.is(Items.TRIDENT)) {
			return Proto.WEAPON_PIERCE;
		}
		return Proto.WEAPON_BLUNT;
	}

	/** Returns this tick's hit (damage, flags, push, weapon) and clears it; null if nothing hit us. */
	public float[] takeHit() {
		int flags = this.pendingFlags | CombatRules.hitFlags(this.meleeThisTick, this.swingStrength, this.swingSprinting,
			(this.pendingFlags & Proto.HIT_CRITICAL) != 0, this.crouchedThisTick, Features.on(Features.Feature.POWER_ATTACKS),
			Features.on(Features.Feature.SNEAK_ATTACKS));
		this.swingStrength = 0.0F; // a swing that didn't land counts for nothing later
		this.swingSprinting = false;
		this.meleeThisTick = false;
		this.crouchedThisTick = false;
		if (!this.hitThisTick) {
			return null;
		}
		float[] hit = { this.pendingDamage, (float) this.pushX, (float) this.pushZ, this.pushStrength, Float.intBitsToFloat(flags),
			Float.intBitsToFloat(this.pendingWeapon) };
		this.pendingDamage = 0.0F;
		this.pendingFlags = 0;
		this.pushX = this.pushZ = 0.0;
		this.pushStrength = 0.0F;
		this.hitThisTick = false;
		return hit;
	}

	@Override
	public void tick() {
		// Position and rotation come from Skyrim (SkyCombat); keep hurt timers and fire ticking.
		this.baseTick();
		if (this.sunBurning && !this.isOnFire()) {
			this.sunBurning = false;
		}
		this.setHealth(this.getMaxHealth());
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return null; // Skyrim plays the NPC's own pain sounds
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}
}
