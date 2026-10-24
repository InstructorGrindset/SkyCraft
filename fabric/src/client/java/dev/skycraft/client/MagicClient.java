package dev.skycraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skycraft.SkyCraft;
import dev.skycraft.config.Features;
import dev.skycraft.config.Features.Feature;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.magic.CastKey;
import dev.skycraft.magic.MagicEffects;
import dev.skycraft.magic.MagicHudLayout;
import dev.skycraft.magic.MagicRequests;
import dev.skycraft.magic.MagicRules;
import dev.skycraft.magic.MagicTable;
import dev.skycraft.magic.ShoutCharge;
import dev.skycraft.magic.SpellCycle;
import dev.skycraft.net.SkyNet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shouts and Spells, the client's part (switches {@code shouts} and {@code spells}): four keys in Minecraft's Controls under
 * SkyCraft, Skyrim's magic table read for what the player knows, and the HUD's state ({@link MagicHud} draws it).
 *
 * <ul>
 * <li><b>Shout</b> (V): shouts the selected shout, Skyrim's equipped one unless <b>Next shout</b> (B; sneak for the one
 * before) picked another. Tap for one word, hold for two and three ({@link ShoutCharge}). Minecraft sends Proto.EV_SHOUT
 * with the words and the look; Skyrim checks the voice and shouts for real, and answers. Shouted, Minecraft adds its own
 * side ({@link MagicEffects} on the server; Whirlwind Sprint's dash here).
 * <li><b>Cast spell</b> (R): casts the selected spell, picked with <b>Next spell</b> (N; sneak for the one before) from
 * Skyrim's favourite spells, or every spell cast by hand ({@link SpellCycle}): once a press, a concentration spell in pulses
 * while held ({@link CastKey}). Skyrim takes the magicka and casts it, and answers; cast, Minecraft adds its own side.
 * </ul>
 *
 * A guest's keys go to their own Skyrim, as the host's do; the Minecraft side goes to the host's server (SkyNet.MagicCast).
 * The client thread runs all of this (ticks, Skyrim's answers through InputBridge, the HUD). Log: SkyCraft-magic.
 */
public final class MagicClient {
	/** Requests Skyrim hasn't answered in this long are given up on (an older plugin never answers). */
	private static final double ANSWER_SECONDS = 3.0;
	/** A line on the HUD stays this long. */
	private static final double STATUS_SECONDS = 2.5;
	/** The voice bar shows white this long once the voice is ready. */
	private static final double READY_SECONDS = 1.5;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "skycraft"));
	private static @Nullable KeyMapping shoutKey, nextShoutKey, castKey, nextSpellKey;

	private static final byte[] TABLE_BYTES = new byte[Proto.MAGIC_TABLE_BYTES];
	private static MagicTable table = MagicTable.NONE;
	private static int tableGeneration = -1;
	private static final ShoutCharge CHARGE = new ShoutCharge();
	private static final CastKey CAST = new CastKey();
	private static final MagicRequests REQUESTS = new MagicRequests((int) System.nanoTime());
	/** The shout and spell the keys use (FormIDs, 0 none), and the equipped shout Skyrim last said. */
	private static int selectedShout, selectedSpell, lastEquipped;

	// The HUD's state (seconds on the nanoTime clock).
	private static double voiceEnds, voiceTotal, voiceShownUntil, spellsShownUntil, magickaWarnUntil, statusUntil;
	private static @Nullable String statusText;
	private static int statusColor;

	// Whirlwind Sprint's dash: ticks left and the velocity (blocks a tick).
	private static int dashTicks;
	private static double dashX, dashZ;

	private static boolean warnedNoAnswer;
	private static int logged;

	private MagicClient() {
	}

	/** The keys (Minecraft's Controls, SkyCraft), the tick and the HUD. At client start. */
	public static void register() {
		shoutKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skycraft.shout", InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY));
		nextShoutKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skycraft.next_shout", InputConstants.Type.KEYSYM, InputConstants.KEY_B, CATEGORY));
		castKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skycraft.cast_spell", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY));
		nextSpellKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skycraft.next_spell", InputConstants.Type.KEYSYM, InputConstants.KEY_N, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(MagicClient::tick);
		MagicHud.register();
	}

	private static double now() {
		return System.nanoTime() / 1.0E9;
	}

	private static void tick(Minecraft minecraft) {
		double now = now();
		readTable(now);
		for (MagicRequests.Pending p : REQUESTS.expired(now, ANSWER_SECONDS)) {
			if (!warnedNoAnswer) {
				warnedNoAnswer = true;
				SkyCraft.LOG.info("SkyCraft-magic: Skyrim never answered a {} ({}); is its SkyCraft plugin older than this mod?", p.shout() ? "shout" : "cast", p.name());
			}
			status("Skyrim didn't answer (is its SkyCraft plugin up to date?)", MagicHudLayout.WARN, now);
		}
		LocalPlayer player = minecraft.player;
		SkyLink.SkyState sky = SkyClient.sky();
		boolean ready = player != null && player.isAlive() && SkyClient.linked() && !MinecraftView.away() && minecraft.gui.screen() == null && sky.inGame()
			&& !sky.menuOpen() && !sky.loading();
		if (!ready || shoutKey == null || nextShoutKey == null || castKey == null || nextSpellKey == null) {
			for (KeyMapping key : new KeyMapping[] { shoutKey, nextShoutKey, castKey, nextSpellKey }) {
				drain(key);
			}
			CHARGE.reset();
			CAST.reset();
			dashTicks = 0;
			return;
		}
		dash(player);
		MagicRules.Settings s = MagicEffects.settings();
		shoutKeys(player, now, s);
		spellKeys(player, now, s);
	}

	/** Skyrim's magic table, decoded again whenever Skyrim has refreshed it. */
	private static void readTable(double now) {
		if (SkyLink.generation() != tableGeneration) {
			tableGeneration = SkyLink.generation(); // another Skyrim: what this one asked is forgotten
			REQUESTS.clear();
			table = MagicTable.NONE;
		}
		int refresh = SkyLink.magicRefresh();
		if (refresh == 0) {
			table = MagicTable.NONE;
			return;
		}
		if (refresh == table.refresh() || SkyLink.readMagic(TABLE_BYTES) <= 0) {
			return; // unchanged, or Skyrim was writing it: next tick
		}
		boolean first = !table.known();
		table = MagicTable.decode(TABLE_BYTES);
		if (table.shoutsOn()) {
			// Skyrim's own count of the voice recovery keeps the HUD's in step. One that jumped up is a shout this client
			// didn't hear of (made in Skyrim itself, riding say): its whole recovery is the bar's length.
			double ours = Math.max(0.0, voiceEnds - now);
			float skyrims = table.voiceRecovery();
			if (skyrims > ours + 0.5) {
				voiceTotal = skyrims;
				voiceEnds = now + skyrims;
			} else if (skyrims > 0.0F || ours > 0.0) {
				voiceEnds = now + skyrims; // (recovered sooner than counted: ready now, and the bar shows it)
			}
		}
		if (first && logged < 10) {
			logged++;
			MagicTable.Shout equipped = table.shout(table.equippedShout());
			SkyCraft.LOG.info("SkyCraft-magic: Skyrim knows {} shouts (equipped: {}) and {} spells cast by hand; magicka {}/{}", table.shouts().size(),
				equipped != null ? equipped.name() + ", " + equipped.words() + " words" : "none", table.spells().size(), Math.round(table.magicka()),
				Math.round(table.magickaMax()));
		}
	}

	// ---- shouts -----------------------------------------------------------------------------------

	private static void shoutKeys(LocalPlayer player, double now, MagicRules.Settings s) {
		boolean next = drain(nextShoutKey);
		boolean pressed = drain(shoutKey);
		if (!Features.on(Feature.SHOUTS) || !table.shoutsOn()) {
			if (pressed || next) {
				status(!Features.on(Feature.SHOUTS) ? "Shouts are switched off (SkyCraft settings)" : "Skyrim hasn't said which shouts you know",
					MagicHudLayout.NOTE, now);
			}
			CHARGE.reset();
			return;
		}
		List<Integer> order = SpellCycle.shouts(table.shouts());
		if (table.equippedShout() != lastEquipped) {
			lastEquipped = table.equippedShout(); // Skyrim's slot changed (in Skyrim's own menu): the keys follow it
			if (order.contains(lastEquipped)) {
				selectedShout = lastEquipped;
			}
		}
		selectedShout = SpellCycle.keep(order, selectedShout);
		if (next) {
			selectedShout = SpellCycle.step(order, selectedShout, player.isShiftKeyDown() ? -1 : 1);
			voiceShownUntil = now + s.hudSeconds();
		}
		MagicTable.Shout shout = table.shout(selectedShout);
		if ((pressed || next) && shout == null) {
			status("You have no shout with a word unlocked", MagicHudLayout.WARN, now);
		}
		int words = CHARGE.update(pressed, shoutKey != null && shoutKey.isDown(), now, shout != null ? shout.words() : 0, s);
		if (pressed || CHARGE.holding()) {
			voiceShownUntil = now + s.hudSeconds();
		}
		if (words > 0 && shout != null) {
			int id = REQUESTS.open(true, shout.formId(), shout.name(), now);
			SkyLink.pushEvent(Proto.EV_SHOUT, shout.formId(), player.getYRot(), player.getXRot(), 0.0F, 0.0F, words, id);
		}
	}

	/** Skyrim's answer to a shout (Proto.IN_SHOUT_RESULT, from InputBridge). */
	public static void onShoutResult(Minecraft minecraft, int code, int request, int words, int milliseconds) {
		MagicRequests.Pending asked = REQUESTS.answered(request);
		if (asked == null) {
			return; // given up on already, or from before Skyrim restarted
		}
		double now = now();
		int result = code & 0xFF;
		int kind = code >>> Proto.MAGIC_KIND_SHIFT & 0xFF;
		float seconds = Math.max(0, milliseconds) / 1000.0F;
		if (result == Proto.SHOUT_DONE) {
			voiceTotal = seconds;
			voiceEnds = now + seconds;
			voiceShownUntil = now + MagicEffects.settings().hudSeconds();
			int w = Math.max(1, Math.min(words, 3));
			if (kind == Proto.SHOUT_KIND_SPRINT) {
				startDash(minecraft.player, w);
			} else if (MagicRules.shoutOnServer(kind)) {
				mirror(minecraft, MagicEffects.SHOUT, kind, w, 0.0F, 0);
			}
			if (logged < 30) {
				logged++;
				SkyCraft.LOG.info("SkyCraft-magic: Skyrim shouted {} ({} words; voice recovers in {} s)", asked.name(), w, String.format("%.1f", seconds));
			}
			return;
		}
		if (result == Proto.SHOUT_RECOVERING) {
			voiceEnds = now + seconds;
			voiceTotal = Math.max(voiceTotal, seconds);
		}
		String says = MagicRules.shoutAnswer(result, asked.name(), seconds);
		if (says != null) {
			status(says, MagicHudLayout.WARN, now);
		}
	}

	/** Whirlwind Sprint: Minecraft's player dashes the way it looks, as Skyrim's does (it follows Minecraft's). */
	private static void startDash(@Nullable LocalPlayer player, int words) {
		if (player == null) {
			return;
		}
		MagicRules.Settings s = MagicEffects.settings();
		double[] v = MagicRules.sprintVelocity(player.getYRot(), s);
		dashX = v[0];
		dashZ = v[1];
		dashTicks = MagicRules.sprintTicks(words, s);
	}

	private static void dash(LocalPlayer player) {
		if (dashTicks <= 0) {
			return;
		}
		dashTicks--;
		player.setDeltaMovement(new Vec3(dashX, player.getDeltaMovement().y, dashZ));
	}

	// ---- spells -----------------------------------------------------------------------------------

	private static void spellKeys(LocalPlayer player, double now, MagicRules.Settings s) {
		boolean next = drain(nextSpellKey);
		boolean pressed = drain(castKey);
		if (!Features.on(Feature.SPELLS) || !table.spellsOn()) {
			if (pressed || next) {
				status(!Features.on(Feature.SPELLS) ? "Spells are switched off (SkyCraft settings)" : "Skyrim hasn't said which spells you know",
					MagicHudLayout.NOTE, now);
			}
			CAST.reset();
			return;
		}
		List<Integer> order = SpellCycle.spells(table.spells());
		selectedSpell = SpellCycle.keep(order, selectedSpell);
		if (next) {
			selectedSpell = SpellCycle.step(order, selectedSpell, player.isShiftKeyDown() ? -1 : 1);
			spellsShownUntil = now + s.hudSeconds();
		}
		MagicTable.Spell spell = table.spell(selectedSpell);
		if ((pressed || next) && spell == null) {
			status("You know no spell to cast by hand", MagicHudLayout.WARN, now);
		}
		int act = CAST.update(pressed, castKey != null && castKey.isDown(), now, spell != null && spell.concentration(), s);
		if (pressed || CAST.pulsing()) {
			spellsShownUntil = now + s.hudSeconds();
		}
		if (act != CastKey.NONE && spell != null) {
			// A concentration spell always goes as a pulse: Skyrim takes its magicka per second for the pulse's stretch.
			boolean pulse = spell.concentration();
			int id = REQUESTS.open(false, spell.formId(), spell.name(), now);
			SkyLink.pushEvent(Proto.EV_CAST_SPELL, spell.formId(), player.getYRot(), player.getXRot(), pulse ? (float) s.pulseSeconds() : 0.0F, 0.0F,
				pulse ? Proto.CAST_PULSE : 0, id);
		}
	}

	/** Skyrim's answer to a cast (Proto.IN_CAST_RESULT, from InputBridge). */
	public static void onCastResult(Minecraft minecraft, int code, int request, int magickaHundredths, int formId) {
		MagicRequests.Pending asked = REQUESTS.answered(request);
		if (asked == null) {
			return;
		}
		double now = now();
		int result = code & 0xFF;
		int kind = code >>> Proto.MAGIC_KIND_SHIFT & 0xFF;
		float magicka = Math.max(0, magickaHundredths) / 100.0F;
		if (result == Proto.CAST_DONE) {
			spellsShownUntil = Math.max(spellsShownUntil, now + MagicEffects.settings().hudSeconds());
			if (MagicRules.spellAction(kind) != MagicRules.ACTION_NONE) {
				MagicTable.Spell spell = table.spell(formId != 0 ? formId : asked.formId());
				mirror(minecraft, MagicEffects.SPELL, kind, 0, magicka, spell != null ? spell.delivery() : Proto.DELIVERY_AIMED);
			}
			if (logged < 30) {
				logged++;
				SkyCraft.LOG.info("SkyCraft-magic: Skyrim cast {} ({} magicka)", asked.name(), String.format("%.1f", magicka));
			}
			return;
		}
		if (result == Proto.CAST_NO_MAGICKA) {
			magickaWarnUntil = now + 1.0;
			spellsShownUntil = Math.max(spellsShownUntil, now + 2.0);
		}
		String says = MagicRules.castAnswer(result, asked.name(), magicka);
		if (says != null) {
			status(says, MagicHudLayout.WARN, now);
		}
	}

	// ---- Minecraft's side -------------------------------------------------------------------------

	/**
	 * What Skyrim carried out gets its Minecraft side: on the integrated server for the host, through the host's server for a
	 * guest (SkyNet.MagicCast, with their guest_parity on).
	 */
	private static void mirror(Minecraft minecraft, int what, int kind, int words, float magicka, int delivery) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		var server = minecraft.getSingleplayerServer();
		if (server != null) {
			UUID id = player.getUUID();
			server.execute(() -> {
				ServerPlayer host = server.getPlayerList().getPlayer(id);
				if (host != null) {
					MagicEffects.apply(host, what, kind, words, magicka, delivery);
				}
			});
		} else if (Features.on(Feature.GUEST_PARITY) && ClientPlayNetworking.canSend(SkyNet.MagicCast.TYPE)) {
			ClientPlayNetworking.send(new SkyNet.MagicCast(what, kind, words, magicka, delivery));
		}
	}

	// ---- the HUD ----------------------------------------------------------------------------------

	/** What the HUD draws now on a GUI {@code width} x {@code height} (MagicHud). Client thread. */
	static List<MagicHudLayout.Op> hudOps(int width, int height) {
		double now = now();
		if (!SkyClient.linked() || MinecraftView.away()) {
			return List.of();
		}
		MagicHudLayout.Bar voice = null;
		MagicTable.Shout shout = table.shout(selectedShout != 0 ? selectedShout : table.equippedShout()); // (before the keys picked one)
		if (Features.on(Feature.SHOUTS) && table.shoutsOn() && shout != null) {
			double left = Math.max(0.0, voiceEnds - now);
			boolean recovering = left > 0.05;
			boolean justReady = !recovering && voiceTotal > 0.0 && now - voiceEnds < READY_SECONDS;
			if (recovering || justReady || now < voiceShownUntil || CHARGE.holding()) {
				String label = shout.name();
				if (recovering) {
					label += "  " + (int) Math.ceil(left) + "s";
				} else if (CHARGE.holding()) {
					label += "  " + "I".repeat(MagicRules.words(CHARGE.held(now), shout.words(), MagicEffects.settings()));
				}
				float fill = voiceTotal > 0.0 ? (float) (1.0 - left / voiceTotal) : 1.0F;
				voice = new MagicHudLayout.Bar(label, fill, recovering ? MagicHudLayout.VOICE : MagicHudLayout.VOICE_READY);
			}
		}
		MagicHudLayout.Bar spellBar = null;
		MagicTable.Spell spell = table.spell(selectedSpell);
		if (Features.on(Feature.SPELLS) && table.spellsOn() && spell != null && (now < spellsShownUntil || CAST.pulsing())) {
			float fill = table.magickaMax() > 0.0F ? table.magicka() / table.magickaMax() : 0.0F;
			spellBar = new MagicHudLayout.Bar(spell.name(), fill, now < magickaWarnUntil ? MagicHudLayout.WARN : MagicHudLayout.MAGICKA);
		}
		String status = now < statusUntil ? statusText : null;
		return MagicHudLayout.layout(width, height, spellBar, voice, status, statusColor);
	}

	private static void status(String text, int color, double now) {
		statusText = text;
		statusColor = color;
		statusUntil = now + STATUS_SECONDS;
	}

	/** Whether the key was pressed since the last tick (its presses all taken). */
	private static boolean drain(@Nullable KeyMapping key) {
		boolean any = false;
		while (key != null && key.consumeClick()) {
			any = true;
		}
		return any;
	}
}
