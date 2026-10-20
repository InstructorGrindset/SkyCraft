package dev.skycraft.client;

import dev.skycraft.combat.SkyCombat;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.link.SkySpace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.sdl.SDLKeyboard;

/**
 * Replays Skyrim-captured input into Minecraft's own input handlers, as if the (hidden) MC
 * window had focus. Keeps a virtual keyboard so InputConstants.isKeyDown() still works.
 */
public final class InputBridge {
	private static final boolean[] KEYS = new boolean[512];
	private static final boolean[] BUTTONS = new boolean[8];
	private static double cursorX, cursorY;
	private static int modifiers;
	private static int clickLogs;

	private InputBridge() {
	}

	public static boolean isKeyDown(int scancode) {
		return scancode >= 0 && scancode < KEYS.length && KEYS[scancode];
	}

	public static void drain(Minecraft minecraft) {
		SkyLink.drainInput((type, code, a, b, c) -> dispatch(minecraft, type, code, a, b, c));
	}

	private static void dispatch(Minecraft minecraft, int type, int code, int a, int b, int c) {
		long handle = minecraft.getWindow().handle();
		switch (type) {
			case Proto.IN_KEY -> key(minecraft, handle, code, a != 0);
			case Proto.IN_MOUSE_BUTTON -> {
				if (code > 0 && code < BUTTONS.length) {
					BUTTONS[code] = a != 0;
				}
				if (a != 0 && clickLogs++ < 20) {
					var hit = minecraft.hitResult;
					dev.skycraft.SkyCraft.LOG.info("SkyCraft: click {} -> {} {} (grabbed {}, screen {})", code, hit == null ? "null" : hit.getType(),
						hit instanceof net.minecraft.world.phys.EntityHitResult eh ? eh.getEntity().getName().getString() : hit == null ? "" : hit.getLocation(),
						minecraft.mouseHandler.isMouseGrabbed(), minecraft.gui.screen());
				}
				minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(code, modifiers), a != 0 ? 1 : 0);
			}
			case Proto.IN_SCROLL -> minecraft.mouseHandler.onScroll(handle, 0.0, a / 120.0);
			case Proto.IN_CURSOR -> {
				double dx = a - cursorX;
				double dy = b - cursorY;
				cursorX = a;
				cursorY = b;
				minecraft.mouseHandler.onMove(handle, a, b, dx, dy);
			}
			case Proto.IN_LOOK -> {
				// Minecraft view: raw mouse motion, turned into a look by Minecraft itself (sensitivity,
				// invert, smooth camera, spyglass) as from a grabbed mouse. Skyrim's look is for the mirror.
				if (MinecraftView.away()) {
					minecraft.mouseHandler.onMove(handle, cursorX, cursorY, a, b);
				}
			}
			case Proto.IN_TEXT -> {
				if (minecraft.gui.screen() != null) {
					minecraft.keyboardHandler.textInput(handle, new String(Character.toChars(a)));
				}
			}
			case Proto.IN_RELEASE_ALL -> releaseAll();
			case Proto.IN_HURT -> hurt(minecraft, code, a / 100.0F, b, c);
			case Proto.IN_ARROW_BLOCKED -> arrowBlocked(minecraft, code, a / 1024.0, b / 1024.0, c / 1024.0);
			case Proto.IN_BLESSING -> blessing(minecraft, code, a / 1000.0F, b / 1000.0F);
			case Proto.IN_SKYRIM_FOOD -> skyrimFood(minecraft, code, a / 1000.0F, b, c);
			case Proto.IN_MERCHANT_GOLD -> merchantGold(minecraft, a, b, c);
			case Proto.IN_OPEN_DOOR, Proto.IN_REDSTONE_PULSE, Proto.IN_DWEMER_DOOR -> TownInput.accept(minecraft, type, code, a, b, c);
			case Proto.IN_MOB_HIT -> mobHit(minecraft, a, b, c);
			case Proto.IN_OPEN_MENU -> {
				if (minecraft.gui.screen() == null && minecraft.player != null) {
					releaseAll();
					minecraft.gui.setScreen(new PauseScreen(true));
				}
			}
			default -> {
			}
		}
	}

	/** Skyrim hit the player: apply it as Minecraft damage on the integrated server (or the host's). */
	private static void hurt(Minecraft minecraft, int kind, float skyrimDamage, int attacker, int flags) {
		var server = minecraft.getSingleplayerServer();
		if (minecraft.player == null || MinecraftView.away()) {
			return; // (in another dimension Skyrim's player is parked: a late hit from before)
		}
		if (server == null) {
			// A guest in a friend's world: the host's server applies it.
			if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.skycraft.net.SkyNet.Hurt.TYPE)) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.skycraft.net.SkyNet.Hurt(kind, skyrimDamage, attacker, flags));
			}
			return;
		}
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				SkyCombat.hurtPlayer(player, kind, skyrimDamage, attacker, flags);
			}
		});
	}

	/** The Skyrim player ate a food in Skyrim: fill the Minecraft player's hunger on the integrated server (or the host's). */
	private static void skyrimFood(Minecraft minecraft, int nutrition, float saturation, int formId, int kind) {
		var server = minecraft.getSingleplayerServer();
		if (minecraft.player == null) {
			return;
		}
		if (server == null) {
			// A guest in a friend's world: the host's server fills it.
			if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.skycraft.net.SkyNet.SkyrimFood.TYPE)) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.skycraft.net.SkyNet.SkyrimFood(nutrition, saturation, formId, kind));
			}
			return;
		}
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				dev.skycraft.food.SkyFood.eat(player, nutrition, saturation, formId, kind);
			}
		});
	}

	/** Skyrim says how much gold a merchant has, for a sale to it the host's server is waiting on (trade.SkySell). */
	private static void merchantGold(Minecraft minecraft, int merchant, int gold, int request) {
		var server = minecraft.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> dev.skycraft.trade.SkySell.answer(merchant, gold, request));
		}
	}

	/**
	 * A Skyrim arrow stopped at a Minecraft block: one of Minecraft's sticks in the block there, on the integrated server, or
	 * for a guest on the host's (SkyNet.ArrowBlocked).
	 */
	private static void arrowBlocked(Minecraft minecraft, int code, double x, double y, double z) {
		var server = minecraft.getSingleplayerServer();
		if (minecraft.player == null || MinecraftView.away()) {
			return;
		}
		// Into the mirror's terms, as every position from this Skyrim (a guest's by its copy of the host's spaces; until
		// the host has said where this world's blocks are, the arrow just ends at the block in Skyrim).
		int world = SkyLink.skyrimWorld();
		if (!SkySpace.known(world)) {
			return;
		}
		Vec3 at = new Vec3(x + SkySpace.originX(world), y, z + SkySpace.originZ(world));
		if (server == null) {
			PartyClient.arrowBlocked(at, code); // a guest in a friend's world: the host's server sticks it
			return;
		}
		double[] flight = Proto.arrowFlight(code);
		Vec3 toward = new Vec3(flight[0], flight[1], flight[2]);
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				SkyCombat.stickSkyrimArrow(player, at, toward);
			}
		});
	}

	/** Skyrim's player got a shrine's blessing: the effect for it on the integrated server (or the host's, for a guest). */
	private static void blessing(Minecraft minecraft, int kind, float hours, float timescale) {
		if (minecraft.player == null || !dev.skycraft.config.Features.on(dev.skycraft.config.Features.Feature.SHRINE_BLESSINGS)) {
			return;
		}
		var server = minecraft.getSingleplayerServer();
		if (server == null) {
			if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.skycraft.net.SkyNet.Blessing.TYPE)) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.skycraft.net.SkyNet.Blessing(kind, hours, timescale));
			}
			return;
		}
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				dev.skycraft.blessings.SkyBlessings.give(player, kind, hours, timescale);
			}
		});
	}

	/** A Skyrim actor's swing landed on a Minecraft creature next to it (mobs and NPCs fight): the integrated server hurts it. */
	private static void mobHit(Minecraft minecraft, int entityId, int hundredths, int attackerFormId) {
		var server = minecraft.getSingleplayerServer();
		if (minecraft.player == null || server == null || MinecraftView.away()) {
			return; // (a guest's Skyrim has no stand-ins in the host's world)
		}
		float damage = dev.skycraft.creatures.CreatureRules.swingDamage(hundredths);
		server.execute(() -> dev.skycraft.creatures.SkyCreatures.npcHitMob(server, entityId, damage, attackerFormId));
	}

	private static void key(Minecraft minecraft, long handle, int scancode, boolean down) {
		if (scancode <= 0 || scancode >= KEYS.length) {
			return;
		}
		boolean wasDown = KEYS[scancode];
		KEYS[scancode] = down;
		updateModifiers();
		int action = down ? (wasDown ? -1 : 1) : 0; // -1 = repeat
		int keycode = SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) modifiers, true);
		minecraft.keyboardHandler.keyPress(handle, action, new KeyEvent(scancode, keycode, modifiers));
	}

	private static void updateModifiers() {
		int m = 0;
		if (KEYS[225]) m |= 0x0001; // SDL_KMOD_LSHIFT
		if (KEYS[229]) m |= 0x0002; // SDL_KMOD_RSHIFT
		if (KEYS[224]) m |= 0x0040; // SDL_KMOD_LCTRL
		if (KEYS[228]) m |= 0x0080; // SDL_KMOD_RCTRL
		if (KEYS[226]) m |= 0x0100; // SDL_KMOD_LALT
		if (KEYS[230]) m |= 0x0200; // SDL_KMOD_RALT
		modifiers = m;
	}

	/** Lift every key and button we think is held (focus moved to Skyrim, link dropped, ...). */
	public static void releaseAll() {
		Minecraft minecraft = Minecraft.getInstance();
		long handle = minecraft.getWindow().handle();
		for (int sc = 0; sc < KEYS.length; sc++) {
			if (KEYS[sc]) {
				KEYS[sc] = false;
				updateModifiers();
				minecraft.keyboardHandler.keyPress(handle, 0, new KeyEvent(sc, SDLKeyboard.SDL_GetKeyFromScancode(sc, (short) 0, true), modifiers));
			}
		}
		for (int button = 1; button < BUTTONS.length; button++) {
			if (BUTTONS[button]) {
				BUTTONS[button] = false;
				minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(button, 0), 0);
			}
		}
	}
}
