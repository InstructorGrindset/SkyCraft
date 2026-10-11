package dev.skycraft.loot;

import dev.skycraft.SkyCraft;
import dev.skycraft.link.SkyLink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Skyrim creatures the player kills drop Minecraft items (meat, leather, bones) at their feet.
 *
 * <p>Skyrim already reports every nearby actor's name and dead flag each frame. A drop happens
 * when an actor seen alive turns dead within {@link #HIT_WINDOW_TICKS} of the player's last hit on
 * it, so corpses walked up to and kills by others drop nothing. Each actor drops at most once.
 * What drops comes from {@code config/skycraft-loot.txt} (see {@link LootRules}).
 */
public final class SkyLoot {
	/** 10 seconds: a kill by bleeding, fire or a follow-up arrow still counts as the player's. */
	public static final int HIT_WINDOW_TICKS = 200;
	private static final String FILE = "skycraft-loot.txt";

	private static final Map<Integer, Boolean> WAS_DEAD = new HashMap<>();
	private static final Map<Integer, Integer> LAST_HIT = new HashMap<>();
	private static final Set<Integer> LOOTED = new HashSet<>();
	private static final Random RANDOM = new Random();
	private static LootRules rules;

	private SkyLoot() {
	}

	/** The player landed a hit on this actor on this server tick. */
	public static void recordHit(int formId, int tick) {
		LAST_HIT.put(formId, tick);
	}

	public static void reset() {
		WAS_DEAD.clear();
		LAST_HIT.clear();
	}

	/** Called with each fresh read of Skyrim's actor table. */
	public static void onActors(ServerLevel level, List<SkyLink.Actor> actors, int tick) {
		Set<Integer> seen = new HashSet<>();
		for (SkyLink.Actor a : actors) {
			int id = a.formId();
			seen.add(id);
			Boolean before = WAS_DEAD.put(id, a.dead());
			if (!a.dead() || !Boolean.FALSE.equals(before) || LOOTED.contains(id)) {
				continue;
			}
			Integer hit = LAST_HIT.get(id);
			if (hit == null || tick - hit > HIT_WINDOW_TICKS) {
				SkyCraft.LOG.info("SkyCraft-loot: {} died without a recent hit from you, no drop", a.name());
				continue;
			}
			LOOTED.add(id);
			int xp = KillXp.forLevel(a.level());
			net.minecraft.world.entity.ExperienceOrb.award(level, new net.minecraft.world.phys.Vec3(a.x(), a.y() + 0.5, a.z()), xp);
			SkyCraft.LOG.info("SkyCraft-loot: killed {} (level {}), {} XP", a.name(), a.level(), xp);
			drop(level, a);
		}
		WAS_DEAD.keySet().retainAll(seen);
		LAST_HIT.keySet().removeIf(id -> tick - LAST_HIT.get(id) > HIT_WINDOW_TICKS * 3);
	}

	private static void drop(ServerLevel level, SkyLink.Actor a) {
		LootRules.Rule rule = rules().match(a.name());
		if (rule == null) {
			SkyCraft.LOG.info("SkyCraft-loot: killed {}, no drop rule for that name", a.name());
			return;
		}
		List<String> dropped = new ArrayList<>();
		for (LootRules.Drop d : LootRules.roll(rule, RANDOM)) {
			Identifier key = Identifier.tryParse(d.item());
			Item item = key != null ? BuiltInRegistries.ITEM.getValue(key) : null;
			if (item == null || item == Items.AIR) {
				SkyCraft.LOG.warn("SkyCraft-loot: unknown item '{}' in {}", d.item(), FILE);
				continue;
			}
			ItemEntity entity = new ItemEntity(level, a.x(), a.y() + 0.5, a.z(), new ItemStack(item, d.min()));
			entity.setDefaultPickUpDelay();
			level.addFreshEntity(entity);
			dropped.add(d.min() + " " + d.item());
		}
		SkyCraft.LOG.info("SkyCraft-loot: killed {}, dropped {}", a.name(), dropped.isEmpty() ? "nothing this time" : String.join(", ", dropped));
	}

	private static LootRules rules() {
		if (rules != null) {
			return rules;
		}
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		String text = LootRules.DEFAULT_TEXT;
		try {
			if (Files.exists(path)) {
				text = Files.readString(path);
			} else {
				Files.createDirectories(path.getParent());
				Files.writeString(path, LootRules.DEFAULT_TEXT);
				SkyCraft.LOG.info("SkyCraft-loot: wrote default drop rules to {}", path);
			}
		} catch (IOException e) {
			SkyCraft.LOG.warn("SkyCraft-loot: couldn't read {}, using defaults: {}", path, e.toString());
			text = LootRules.DEFAULT_TEXT;
		}
		List<String> problems = new ArrayList<>();
		rules = LootRules.parse(text, problems);
		problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-loot: {} {}", FILE, p));
		SkyCraft.LOG.info("SkyCraft-loot: {} drop rules loaded", rules.rules().size());
		return rules;
	}
}
