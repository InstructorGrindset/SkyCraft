package dev.skycraft.skills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skycraft.SkyCraft;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import dev.skycraft.portal.PortalItems;
import dev.skycraft.skills.SkillRules.Absorbed;
import dev.skycraft.skills.SkillRules.Settings;
import dev.skycraft.skills.SkillRules.SoulLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim's skill levels making Minecraft better, Minecraft side (rules and numbers in {@link SkillRules},
 * {@code skycraft-skills.txt}). Skyrim writes its player's 18 skill levels and Dragon Souls into SkyState about once a
 * second; this reads them once a second on the server and answers the mixins that use them. Only the host's own Skyrim
 * is on the link, so only the host (SkyNet.isHost) gets the bonuses, like the skill training that goes the other way
 * (SkyCombat.trainDefence). Guests' skills would need their Skyrim's levels sent to the host's world: not done yet.
 *
 * <ul>
 * <li>Sneak ({@code sneak_skill}): mixin.LivingEntityStealthMixin, on top of Skooma's stealth.
 * <li>Archery ({@code archery_skill}): mixin.ArcheryMixin, once on an arrow's base damage as a bow or crossbow shoots it.
 * <li>Smithing ({@code smithing_skill}): mixin.AnvilSmithingMixin, the anvil's level cost.
 * <li>Enchanting ({@code enchanting_skill}): mixin.EnchantingTableMixin and mixin.EnchantingCostMixin count more
 * bookshelves; {@link SoulGemFuelSlot} (mixin.EnchantingLapisSlotMixin) takes a filled soul gem for lapis.
 * <li>Dragon souls ({@code dragon_souls}): here, once a second: XP levels for each soul absorbed, with the count last
 * seen kept on the player ({@link #SOULS}) so a reconnect never pays twice.
 * <li>Alchemy ({@code alchemy_brewing}): {@link SkyAlchemy}.
 * </ul>
 *
 * <p>The bonuses work in every dimension: they're the Skyrim character's, wherever the player is. {@code /skyskills}
 * shows the levels and what they give. Log tag {@code SkyCraft-skills}.
 */
public final class SkySkills {
	private static final String FILE = "skycraft-skills.txt";
	/** SkyState is read this often (ticks): Skyrim refreshes the skills about once a second. */
	private static final int CHECK_TICKS = 20;
	private static final SkyLink.SkyState SKY = new SkyLink.SkyState();

	private static final Codec<SoulLedger> LEDGER_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("load", 0).forGetter(SoulLedger::load),
		Codec.INT.optionalFieldOf("souls", 0).forGetter(SoulLedger::souls)
	).apply(i, SoulLedger::new));

	/** The Dragon Souls count each player last saw, and in which Skyrim game: saved with the player (so per world), kept when they die. */
	public static final AttachmentType<SoulLedger> SOULS = AttachmentRegistry.<SoulLedger>create(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "dragon_souls"),
		builder -> builder.persistent(LEDGER_CODEC).copyOnDeath());

	/**
	 * The host's skill levels as last read ([skill - Proto.SKILL_FIRST], 0 = unknown): all 0 while no Skyrim is in a game on
	 * this machine's link or no server of this game runs (forget). Written on the server thread, read on the client's too.
	 */
	private static volatile int[] levels = new int[Proto.SKILL_COUNT];
	private static volatile int dragonSouls;
	/** SkyState skillsLoad as last read: which Skyrim game the levels are from (0: none). */
	private static volatile int skillsLoad;
	private static volatile @Nullable Settings settings;
	/** The Enchanting of whoever's enchanting table offers are being worked out right now, 0 if none (server thread). */
	private static int enchanting;
	/** Log lines so far for each kind of bonus, so the log shows the first few and then stays quiet. */
	private static int loggedArrows, loggedAnvils, loggedTables;

	private SkySkills() {
	}

	public static void init() {
		SkyAlchemy.init();
		SkyCraft.LOG.info("SkyCraft-skills: sneak {}, archery {}, smithing {}, enchanting {}, dragon souls {}, alchemy {} ({})", onOff(Features.Feature.SNEAK_SKILL),
			onOff(Features.Feature.ARCHERY_SKILL), onOff(Features.Feature.SMITHING_SKILL), onOff(Features.Feature.ENCHANTING_SKILL),
			onOff(Features.Feature.DRAGON_SOULS), onOff(Features.Feature.ALCHEMY_BREWING), SOULS.identifier());
		ServerTickEvents.END_SERVER_TICK.register(SkySkills::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> settings()); // writes skycraft-skills.txt and logs the numbers
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> forget());
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("skyskills").executes(c -> {
			describe(c.getSource());
			return 1;
		})));
	}

	private static String onOff(Features.Feature feature) {
		return Features.on(feature) ? "on" : "off";
	}

	/** The numbers in skycraft-skills.txt, read once. */
	public static Settings settings() {
		Settings s = settings;
		if (s == null) {
			synchronized (SkySkills.class) {
				s = settings;
				if (s == null) {
					List<String> problems = new ArrayList<>();
					s = SkillRules.parse(Config.read(FILE, SkillRules.DEFAULT_TEXT), problems);
					problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-skills: {} {}", FILE, p));
					SkyCraft.LOG.info("SkyCraft-skills: crouching noticed from x{} at Sneak 100, arrows x{} at Archery 100, anvils a level less per {} Smithing, "
						+ "a bookshelf per {} Enchanting, {} levels a dragon soul, potions +{} a level of Alchemy", s.sneakAtTop(), s.archeryAtTop(),
						s.smithingPerLevel(), s.enchantingPerBookshelf(), s.levelsPerSoul(), s.alchemyPerLevel());
					settings = s;
				}
			}
		}
		return s;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		refresh();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (SkyNet.isHost(player)) {
				souls(player);
			}
		}
	}

	/** Reads the levels Skyrim last wrote. Kept through a loading screen; forgotten when Skyrim goes away. */
	private static void refresh() {
		if (!SkyLink.active()) {
			forget();
			return;
		}
		if (!SkyLink.readSkyState(SKY) || !SKY.inGame() || SKY.loading()) {
			return;
		}
		int[] now = new int[Proto.SKILL_COUNT];
		for (int i = 0; i < now.length; i++) {
			now[i] = SKY.skill(Proto.SKILL_FIRST + i);
		}
		boolean newGame = SKY.skillsLoad != skillsLoad;
		levels = now;
		dragonSouls = SKY.dragonSouls;
		skillsLoad = SKY.skillsLoad;
		if (newGame && SKY.skillsLoad != 0) {
			StringBuilder line = new StringBuilder();
			for (int i = 0; i < now.length; i++) {
				line.append(i == 0 ? "" : ", ").append(SkillRules.NAMES.get(i)).append(' ').append(now[i]);
			}
			SkyCraft.LOG.info("SkyCraft-skills: Skyrim game {}: {}; dragon souls {}", Integer.toHexString(SKY.skillsLoad), line, SKY.dragonSouls);
		}
	}

	/** No Skyrim, or no server of this game running: no levels known. */
	private static void forget() {
		levels = new int[Proto.SKILL_COUNT];
		dragonSouls = 0;
		skillsLoad = 0;
	}

	/** The host's level of {@code skill} (a Skyrim ActorValue, One-Handed 6 to Enchanting 23), 0 = unknown. */
	public static int level(int skill) {
		int i = skill - Proto.SKILL_FIRST;
		int[] l = levels;
		return i >= 0 && i < l.length ? l[i] : 0;
	}

	/**
	 * The level of {@code skill} that counts for {@code player}: the host's own (SkyNet.isHost) on the server, 0 for a guest,
	 * whose Skyrim this world can't see. On a client it's what this game's own server last read, so the host's screens work
	 * out what its server does; a guest's game, whose own world is closed while it visits, knows none.
	 */
	public static int level(Player player, int skill) {
		if (player instanceof ServerPlayer serverPlayer) {
			return SkyNet.isHost(serverPlayer) ? level(skill) : 0;
		}
		return player.level().isClientSide() ? level(skill) : 0;
	}

	// ---- Sneak ----------------------------------------------------------------------------------

	/**
	 * Sneak's share of the distance mobs notice {@code entity} from while it crouches (before Skooma's): below 1 for the host
	 * with sneak_skill on, 1 for anyone else, or standing.
	 */
	public static double sneakScale(LivingEntity entity) {
		if (!(entity instanceof Player player) || !player.isDiscrete() || !Features.on(Features.Feature.SNEAK_SKILL)) {
			return 1.0;
		}
		return SkillRules.sneakScale(level(player, Proto.SKILL_SNEAK), settings());
	}

	// ---- Archery --------------------------------------------------------------------------------

	/** An arrow {@code shooter} just shot from a bow or crossbow: Archery scales its base damage, once. */
	public static void archery(AbstractArrow arrow, ServerPlayer shooter) {
		if (!Features.on(Features.Feature.ARCHERY_SKILL)) {
			return;
		}
		int archery = level(shooter, Proto.SKILL_ARCHERY);
		double times = SkillRules.archeryMultiplier(archery, settings());
		if (times <= 1.0) {
			return;
		}
		double before = arrow.getBaseDamage();
		arrow.setBaseDamage(before * times);
		if (loggedArrows < 10) {
			loggedArrows++;
			SkyCraft.LOG.info("SkyCraft-skills: Archery {}: {}'s arrow hits x{} (base damage {} -> {})", archery, shooter.getName().getString(), format(times),
				format(before), format(arrow.getBaseDamage()));
		}
	}

	// ---- Smithing -------------------------------------------------------------------------------

	/** The XP level cost of an anvil repair or combination in {@code menu} for whoever uses it, with their Smithing. */
	public static int anvilCost(AbstractContainerMenu menu, int cost) {
		Player player = menuPlayer(menu);
		if (player == null || !Features.on(Features.Feature.SMITHING_SKILL)) {
			return cost;
		}
		int smithing = level(player, Proto.SKILL_SMITHING);
		int cheaper = SkillRules.anvilCost(cost, smithing, settings());
		if (cheaper != cost && player instanceof ServerPlayer && loggedAnvils < 10) {
			loggedAnvils++;
			SkyCraft.LOG.info("SkyCraft-skills: Smithing {}: {}'s anvil work costs {} levels instead of {}", smithing, player.getName().getString(), cheaper, cost);
		}
		return cheaper;
	}

	// ---- Enchanting -----------------------------------------------------------------------------

	/**
	 * The enchanting table {@code menu} is working out its offers (EnchantmentMenu.slotsChanged, server side): until
	 * {@link #enchantingOffersDone}, {@link #bookshelves} counts with its user's Enchanting.
	 */
	public static void enchantingOffers(AbstractContainerMenu menu) {
		// Only the server's menu: the client's works nothing out, and in a game that runs its own server it runs on another
		// thread, which mustn't touch this.
		if (menuPlayer(menu) instanceof ServerPlayer player) {
			enchanting = Features.on(Features.Feature.ENCHANTING_SKILL) ? level(player, Proto.SKILL_ENCHANTING) : 0;
		}
	}

	/** The enchanting table {@code menu} has its offers. */
	public static void enchantingOffersDone(AbstractContainerMenu menu) {
		if (menuPlayer(menu) instanceof ServerPlayer) {
			enchanting = 0;
		}
	}

	/** The bookshelves an enchanting table's offers count, {@code real} of them around it (EnchantmentHelper.getEnchantmentCost). */
	public static int bookshelves(int real) {
		int level = enchanting;
		if (level <= 0) {
			return real;
		}
		int counted = SkillRules.bookshelves(real, level, settings());
		if (counted != real && loggedTables < 10) {
			loggedTables++;
			SkyCraft.LOG.info("SkyCraft-skills: Enchanting {}: the enchanting table counts {} bookshelves for {} around it", level, counted, real);
		}
		return counted;
	}

	/** A filled soul gem (from a filled Greater, Grand or Black Skyrim soul gem) stands in for lapis lazuli, with enchanting_skill on. */
	public static boolean soulGemFuel(ItemStack stack) {
		return PortalItems.FILLED_SOUL_GEM != null && stack.is(PortalItems.FILLED_SOUL_GEM) && Features.on(Features.Feature.ENCHANTING_SKILL);
	}

	// ---- Dragon souls ---------------------------------------------------------------------------

	private static SoulLedger ledger(ServerPlayer player) {
		SoulLedger ledger = player.getAttached(SOULS);
		return ledger != null ? ledger : SoulLedger.NONE;
	}

	/** The host's Dragon Souls as Skyrim last wrote them: XP levels for each one absorbed since the last look. */
	private static void souls(ServerPlayer host) {
		int load = skillsLoad;
		if (load == 0) {
			return;
		}
		SoulLedger before = ledger(host);
		Absorbed seen = before.see(load, dragonSouls);
		if (!seen.ledger().equals(before)) {
			host.setAttached(SOULS, seen.ledger());
		}
		if (seen.souls() <= 0) {
			return;
		}
		int gained = Features.on(Features.Feature.DRAGON_SOULS) ? SkillRules.levelsFor(seen.souls(), settings()) : 0;
		SkyCraft.LOG.info("SkyCraft-skills: {} absorbed {} dragon soul{} ({} -> {}): {}", host.getName().getString(), seen.souls(), seen.souls() == 1 ? "" : "s",
			before.souls(), seen.ledger().souls(), gained > 0 ? "+" + gained + " levels"
				: Features.on(Features.Feature.DRAGON_SOULS) ? "no levels (levels_per_soul is 0)" : "no levels (dragon_souls off)");
		if (gained <= 0) {
			return;
		}
		host.giveExperienceLevels(gained);
		host.sendSystemMessage(Component.literal((seen.souls() == 1 ? "Dragon soul absorbed" : seen.souls() + " dragon souls absorbed") + ": +" + gained
			+ " levels"));
		host.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.0F);
	}

	// ---- /skyskills -----------------------------------------------------------------------------

	private static void describe(CommandSourceStack source) {
		List<String> lines = new ArrayList<>();
		if (source.getEntity() instanceof ServerPlayer player && !SkyNet.isHost(player)) {
			lines.add("Skyrim skills: only the host's own Skyrim is known in this world.");
		} else if (skillsLoad == 0) {
			lines.add("Skyrim skills: none known (Skyrim isn't in a game, or its SkyCraft plugin is older than the skills).");
		} else {
			Settings s = settings();
			StringBuilder all = new StringBuilder("Skyrim skills:");
			for (int i = 0; i < Proto.SKILL_COUNT; i++) {
				all.append(i == 0 ? " " : ", ").append(SkillRules.NAMES.get(i)).append(' ').append(level(Proto.SKILL_FIRST + i));
			}
			lines.add(all.toString());
			lines.add(SkillRules.describe(Proto.SKILL_SNEAK, level(Proto.SKILL_SNEAK), s) + onOffNote(Features.Feature.SNEAK_SKILL));
			lines.add(SkillRules.describe(Proto.SKILL_ARCHERY, level(Proto.SKILL_ARCHERY), s) + onOffNote(Features.Feature.ARCHERY_SKILL));
			lines.add(SkillRules.describe(Proto.SKILL_SMITHING, level(Proto.SKILL_SMITHING), s) + onOffNote(Features.Feature.SMITHING_SKILL));
			lines.add(SkillRules.describe(Proto.SKILL_ENCHANTING, level(Proto.SKILL_ENCHANTING), s) + onOffNote(Features.Feature.ENCHANTING_SKILL));
			lines.add(SkillRules.describe(Proto.SKILL_ALCHEMY, level(Proto.SKILL_ALCHEMY), s) + onOffNote(Features.Feature.ALCHEMY_BREWING));
			lines.add("Dragon souls: " + dragonSouls + " (" + s.levelsPerSoul() + " levels for each one absorbed)" + onOffNote(Features.Feature.DRAGON_SOULS));
		}
		for (String line : lines) {
			source.sendSuccess(() -> Component.literal(line), false);
		}
	}

	private static String onOffNote(Features.Feature feature) {
		return Features.on(feature) ? "" : " (off: " + feature.key + ")";
	}

	// ---- menus ----------------------------------------------------------------------------------

	/** Whoever uses {@code menu}: the owner of the player inventory its slots show. */
	public static @Nullable Player menuPlayer(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory inventory) {
				return inventory.player;
			}
		}
		return null;
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
