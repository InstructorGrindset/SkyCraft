package dev.skycraft.creatures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Horses carry over ({@code horses_carry_over}): a tamed Minecraft horse and the player's Skyrim horse are one animal in two
 * forms, and it takes whichever form the world it's in has. Host only, server thread only.
 *
 * <ul>
 * <li>Into the mirror: the host's tamed Minecraft horse (a horse, not a donkey or mule) within 12 blocks of them in the
 * Skyrim world, outdoors, becomes their Skyrim horse there. Minecraft asks (EV_HORSE HORSE_ARRIVE, with the horse's speed and
 * health); Skyrim moves its horse to the spot (the one it was before, or a new horse owned by the player), with the Minecraft
 * horse's speed (SpeedMult) and health, and says so in SkyState (HORSE_INFO_ARRIVED); only then does the Minecraft horse go
 * (riders put down first), its stats kept with the host ({@link #CARRIED}). Skyrim not answering within five seconds leaves
 * the Minecraft horse be.
 * <li>Out of the mirror: the host leaving the Skyrim world (a portal, the Minecraft view) with their Skyrim horse near
 * (HORSE_INFO_NEAR: alive, nobody on it, within 16 blocks) takes it along: a tamed, saddled Minecraft horse with its kept stats
 * (or speed from its SpeedMult) comes out beside them, and Skyrim disables its own (HORSE_DEPART) until it comes back.
 * <li>A Minecraft horse that was the Skyrim horse dies: Skyrim forgets it (HORSE_LOST; its disabled horse stays gone).
 * </ul>
 *
 * Partial: the Minecraft horse's coat and markings and Skyrim's horse's own look don't carry over (each form keeps its own),
 * and the Minecraft form is made with a summon command (owner and saddle in its data), since no horse class is used here.
 */
public final class SkyHorses {
	private static final String TAG = "skycraft_carried_horse";
	/** Blocks from the host a tamed horse is taken into Skyrim. */
	private static final double LOOK_RANGE = 12.0;
	/** Ticks Skyrim has to answer an arrival before the Minecraft horse is left be, and to wait before asking again. */
	private static final int CONFIRM_TICKS = 100;
	private static final int RETRY_TICKS = 600;

	/**
	 * The host's horse as it crosses: the Skyrim horse it is (formId), the Minecraft horse it is while in Minecraft (uuid,
	 * inMinecraft), and the Minecraft stats it keeps either way.
	 */
	public record Carried(int formId, String uuid, double speed, double jump, float maxHealth, float health, String name, boolean inMinecraft) {
	}

	private static final Codec<Carried> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("form_id", 0).forGetter(Carried::formId),
		Codec.STRING.optionalFieldOf("uuid", "").forGetter(Carried::uuid),
		Codec.DOUBLE.optionalFieldOf("speed", HorseRules.SPEED_AVERAGE).forGetter(Carried::speed),
		Codec.DOUBLE.optionalFieldOf("jump", HorseRules.JUMP_AVERAGE).forGetter(Carried::jump),
		Codec.FLOAT.optionalFieldOf("max_health", HorseRules.HEALTH_AVERAGE).forGetter(Carried::maxHealth),
		Codec.FLOAT.optionalFieldOf("health", HorseRules.HEALTH_AVERAGE).forGetter(Carried::health),
		Codec.STRING.optionalFieldOf("name", "").forGetter(Carried::name),
		Codec.BOOL.optionalFieldOf("in_minecraft", false).forGetter(Carried::inMinecraft)
	).apply(i, Carried::new));

	/** The host's horse: saved with the player, kept when they die. */
	public static final AttachmentType<Carried> CARRIED = AttachmentRegistry.<Carried>create(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "horse"),
		builder -> builder.persistent(CODEC).copyOnDeath());

	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();
	private static int lastInMirror = Integer.MIN_VALUE;
	private static @Nullable UUID arriving;
	private static @Nullable Carried arrivingStats;
	private static int arrivingSince;
	private static int nextLook;
	private static int askedBack;
	private static boolean toldAnother;

	private SkyHorses() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(SkyHorses::died);
		SkyCraft.LOG.info("SkyCraft-horse: horses carry over ({})", CARRIED.identifier());
	}

	/** Every server tick, for the host, in any dimension. */
	static void tick(MinecraftServer server, ServerPlayer host, int tick) {
		boolean inMirror = dev.skycraft.SkyDimension.inMirror(host);
		boolean left = !inMirror && lastInMirror == tick - 1; // the host was in the mirror on the tick before (not a login elsewhere)
		if (inMirror) {
			lastInMirror = tick;
		}
		if (!Features.on(Features.Feature.HORSES_CARRY_OVER)) {
			return;
		}
		if (left) {
			arriving = null;
			depart(server, host);
			return;
		}
		if (!inMirror) {
			return;
		}
		if (arriving != null) {
			confirm(host, tick);
		} else if (tick >= nextLook && tick % 20 == 0) {
			look(host, tick);
		}
	}

	/** The host's tamed horses near them in the Skyrim world: the first one becomes their Skyrim horse there. */
	private static void look(ServerPlayer host, int tick) {
		if (!SkyLink.readSkyState(SKY) || !SKY.inGame() || SKY.loading() || SKY.menuOpen() || (SKY.place & Proto.PLACE_INTERIOR) != 0) {
			return;
		}
		ServerLevel level = host.level();
		Carried carried = host.getAttached(CARRIED);
		// Skyrim says its horse is out in Minecraft, but Minecraft has no horse out there for it (another Minecraft world, a
		// lost record): it comes back here rather than staying disabled for good.
		if (SKY.horse != 0 && HorseRules.away(SKY.horseInfo) && askedBack != SKY.horse
			&& (carried == null || !carried.inMinecraft() || carried.formId() != SKY.horse)) {
			askedBack = SKY.horse;
			double speed = HorseRules.minecraftSpeed(HorseRules.infoSpeedMult(SKY.horseInfo));
			SkyLink.pushEventAt(Proto.EV_HORSE, SKY.horse, host.getX(), host.getY(), host.getZ(), host.getYRot(), Proto.HORSE_ARRIVE,
				HorseRules.packStats(speed, HorseRules.HEALTH_AVERAGE, HorseRules.HEALTH_AVERAGE));
			SkyCraft.LOG.info("SkyCraft-horse: Skyrim's horse {} is away in Minecraft, but this world has no horse out for it; it comes back to you",
				String.format("%08X", SKY.horse));
			return;
		}
		List<Entity> horses = level.getEntities((Entity) null, host.getBoundingBox().inflate(LOOK_RANGE),
			e -> e.getType() == EntityType.HORSE && e.isAlive() && ownedBy(e, host));
		for (Entity entity : horses) {
			if (!(entity instanceof LivingEntity horse)) {
				continue;
			}
			boolean same = carried != null && carried.inMinecraft() && carried.uuid().equals(horse.getUUID().toString());
			if (!same && carried != null && carried.inMinecraft()) {
				// Another of the player's horses is the Skyrim horse, out in another world: this one stays a Minecraft horse.
				if (!toldAnother) {
					toldAnother = true;
					host.sendOverlayMessage(Component.literal("Your Skyrim horse is still in another world; this horse stays a Minecraft horse"));
				}
				continue;
			}
			int formId = same ? carried.formId() : 0;
			double speed = base(horse, Attributes.MOVEMENT_SPEED, HorseRules.SPEED_AVERAGE);
			double jump = base(horse, Attributes.JUMP_STRENGTH, HorseRules.JUMP_AVERAGE);
			var name = horse.getCustomName();
			arrivingStats = new Carried(formId, "", speed, jump, horse.getMaxHealth(), horse.getHealth(), name != null ? name.getString() : "", false);
			arriving = horse.getUUID();
			arrivingSince = tick;
			SkyLink.pushEventAt(Proto.EV_HORSE, formId, horse.getX(), horse.getY(), horse.getZ(), horse.getYRot(), Proto.HORSE_ARRIVE,
				HorseRules.packStats(speed, horse.getMaxHealth(), horse.getHealth()));
			SkyCraft.LOG.info("SkyCraft-horse: your horse {} comes into Skyrim ({}; speed {}, health {} of {})", horse.getName().getString(),
				formId != 0 ? "as Skyrim's " + String.format("%08X", formId) : "as a new Skyrim horse", String.format(Locale.ROOT, "%.3f", speed),
				horse.getHealth(), horse.getMaxHealth());
			return;
		}
	}

	/** Skyrim carried out the arrival (HORSE_INFO_ARRIVED): the Minecraft horse goes, its stats kept. */
	private static void confirm(ServerPlayer host, int tick) {
		UUID id = arriving;
		Carried stats = arrivingStats;
		if (id == null || stats == null) {
			arriving = null;
			return;
		}
		if (tick - arrivingSince > CONFIRM_TICKS) {
			SkyCraft.LOG.info("SkyCraft-horse: Skyrim didn't take the horse; it stays a Minecraft horse for now");
			arriving = null;
			nextLook = tick + RETRY_TICKS;
			return;
		}
		if (!SkyLink.readSkyState(SKY) || !HorseRules.arrived(SKY.horseInfo) || SKY.horse == 0) {
			return;
		}
		Entity horse = host.level().getEntity(id);
		if (horse != null) {
			horse.ejectPassengers();
			horse.discard();
		}
		host.setAttached(CARRIED, new Carried(SKY.horse, "", stats.speed(), stats.jump(), stats.maxHealth(), stats.health(), stats.name(), false));
		host.sendOverlayMessage(Component.literal((stats.name().isEmpty() ? "Your horse" : stats.name()) + " is your Skyrim horse now"));
		SkyCraft.LOG.info("SkyCraft-horse: the horse is Skyrim's {} now", String.format("%08X", SKY.horse));
		arriving = null;
		toldAnother = false;
		nextLook = tick + CONFIRM_TICKS;
	}

	/** The host just left the Skyrim world: their Skyrim horse near them comes along as a Minecraft horse. */
	private static void depart(MinecraftServer server, ServerPlayer host) {
		if (!SkyLink.readSkyState(SKY) || !HorseRules.comesAlong(SKY.horse, SKY.horseInfo)) {
			return;
		}
		Carried kept = host.getAttached(CARRIED);
		boolean known = kept != null && kept.formId() == SKY.horse && !kept.inMinecraft();
		double speed = known ? kept.speed() : HorseRules.minecraftSpeed(HorseRules.infoSpeedMult(SKY.horseInfo));
		double jump = HorseRules.jump(known ? kept.jump() : 0.0);
		float maxHealth = HorseRules.maxHealth(known ? kept.maxHealth() : 0.0F);
		float health = HorseRules.health(maxHealth, HorseRules.infoHealthShare(SKY.horseInfo, SKY.horse));
		String name = known ? kept.name() : "";
		LivingEntity horse = summon(server, host, speed, jump, maxHealth, health, name);
		if (horse == null) {
			return; // Skyrim's horse stays where it is
		}
		host.setAttached(CARRIED, new Carried(SKY.horse, horse.getUUID().toString(), speed, jump, maxHealth, health, name, true));
		SkyLink.pushEvent(Proto.EV_HORSE, SKY.horse, 0.0F, 0.0F, 0.0F, 0.0F, Proto.HORSE_DEPART, 0);
		host.sendOverlayMessage(Component.literal((name.isEmpty() ? "Your horse" : name) + " came with you"));
		SkyCraft.LOG.info("SkyCraft-horse: Skyrim's horse {} came along into {} as a Minecraft horse (speed {}, health {} of {})", String.format("%08X", SKY.horse),
			host.level().dimension().identifier(), String.format(Locale.ROOT, "%.3f", speed), health, maxHealth);
	}

	/**
	 * A tamed, saddled Minecraft horse beside the host with these stats, or null. Made with Minecraft's summon command (its
	 * owner, tameness and saddle in the entity data; both ways a saddle has been written), then given its stats.
	 */
	private static @Nullable LivingEntity summon(MinecraftServer server, ServerPlayer host, double speed, double jump, float maxHealth, float health, String name) {
		ServerLevel level = host.level();
		double yaw = Math.toRadians(host.getYRot());
		double x = host.getX() - Math.cos(yaw) * 1.5, y = host.getY(), z = host.getZ() - Math.sin(yaw) * 1.5; // at the host's side
		int[] owner = UUIDUtil.uuidToIntArray(host.getUUID());
		String data = String.format(Locale.ROOT,
			"{Tame:1b,Owner:[I;%d,%d,%d,%d],Tags:[\"%s\"],equipment:{saddle:{id:\"minecraft:saddle\",count:1}},SaddleItem:{id:\"minecraft:saddle\",count:1}}",
			owner[0], owner[1], owner[2], owner[3], TAG);
		String command = String.format(Locale.ROOT, "summon minecraft:horse %.2f %.2f %.2f %s", x, y, z, data);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
		List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, new AABB(x - 2.0, y - 2.0, z - 2.0, x + 2.0, y + 3.0, z + 2.0),
			e -> e.entityTags().contains(TAG));
		if (found.isEmpty()) {
			SkyCraft.LOG.warn("SkyCraft-horse: couldn't make the Minecraft horse ({}); Skyrim's horse stays in Skyrim", command);
			return null;
		}
		LivingEntity horse = found.getFirst();
		horse.removeTag(TAG);
		setBase(horse, Attributes.MOVEMENT_SPEED, speed);
		setBase(horse, Attributes.JUMP_STRENGTH, jump);
		setBase(horse, Attributes.MAX_HEALTH, maxHealth);
		horse.setHealth(health);
		if (!name.isEmpty()) {
			horse.setCustomName(Component.literal(name));
		}
		if (!ownedBy(horse, host)) {
			SkyCraft.LOG.warn("SkyCraft-horse: the Minecraft horse came out untamed (this Minecraft didn't take the summon's owner); tame it again");
		}
		return horse;
	}

	/** A horse died: if it was the host's Skyrim horse out in Minecraft, Skyrim forgets its own. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (entity.getType() != EntityType.HORSE || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		String uuid = entity.getUUID().toString();
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			Carried carried = player.getAttached(CARRIED);
			if (carried == null || !carried.inMinecraft() || !carried.uuid().equals(uuid)) {
				continue;
			}
			player.removeAttached(CARRIED);
			if (dev.skycraft.net.SkyNet.isHost(player)) {
				SkyLink.pushEvent(Proto.EV_HORSE, carried.formId(), 0.0F, 0.0F, 0.0F, 0.0F, Proto.HORSE_LOST, 0);
			}
			SkyCraft.LOG.info("SkyCraft-horse: {}'s horse (Skyrim's {}) died in Minecraft", player.getName().getString(), String.format("%08X", carried.formId()));
		}
	}

	private static boolean ownedBy(Entity entity, ServerPlayer player) {
		if (!(entity instanceof OwnableEntity ownable)) {
			return false;
		}
		Entity owner = ownable.getOwner();
		return owner != null && owner.getUUID().equals(player.getUUID());
	}

	private static double base(LivingEntity entity, Holder<Attribute> attribute, double fallback) {
		AttributeInstance instance = entity.getAttribute(attribute);
		return instance != null ? instance.getBaseValue() : fallback;
	}

	private static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}
}
