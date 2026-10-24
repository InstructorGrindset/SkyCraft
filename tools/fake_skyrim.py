"""Stand-in for the SKSE plugin, for testing the Minecraft mod without Skyrim.

Creates the shared mapping, streams a flat floor + a staircase of collision, holds W / Space for a
bit, prints Minecraft's reported player state, and saves the overlay frame to a PNG.

    python tools/fake_skyrim.py [seconds] [out.png]

SKYCRAFT_TOWN (default Whiterun) and SKYCRAFT_PLACE (PlaceFlags: 1 interior, 2 dungeon, 4 town;
default 4) set where the fake player is; SKYCRAFT_PLACE=3 makes it a dungeon interior.
SKYCRAFT_CARRIED (CarriedFlags, default 0; 1 = an Elder Scroll) sets SkyState.carried.
SKYCRAFT_COMPANION=1 adds a wild wolf to tame with bones (Companions): see COMPANION below.
SKYCRAFT_GEAR=1 kills the fake bandit you punch and sends what it wore (Gear drops): see GEAR below.
SKYCRAFT_SKILL sets every one of the 18 skill levels in SkyState.skills (default 0: unknown, no bonus), so
SKYCRAFT_SKILL=100 gives the host the full Sneak, Archery, Smithing, Enchanting and Alchemy bonuses. SKYCRAFT_SOULS
sets the Dragon Souls (default 0), and SKYCRAFT_SOUL_EVERY=30 absorbs one more every 30 seconds (Dragon souls).

Breach doors: outside (no interior place flag) it publishes two dungeon entrances near the fake
player, a Nordic crypt 40 blocks east and a mine 60 blocks south, and prints the kEvEnterDungeon
events Minecraft sends back (SKYCRAFT_BREACHES=0 publishes none).

Shouts and spells: SKYCRAFT_MAGIC=1 publishes a magic table and answers Minecraft's shouts and casts as the plugin
does, then presses the magic keys after the main script: see MAGIC below.

World spaces: the fake player is in a made-up Skyrim world (SKYCRAFT_WORLD, hex; default FF0FA4E0),
so Minecraft gives it a space of its own and the test blocks never land in a real Skyrim world's.
Every coordinate here is in that world's own terms, as a real Skyrim's are; kColClear names the world.
After the first script it goes through a load door into a second made-up world (an interior), checks
that Minecraft reports the player in that world's terms and that the cobblestone placed in the first
world isn't drawn there, then goes back and checks it's drawn again. It prints which space Minecraft
says each world is in (McState spaceWorld).
"""

import mmap
import os
import struct
import sys
import time
import zlib

MAGIC = 0x43594B53
VERSION = 13
NAME = os.environ.get("SKYCRAFT_LINK", "Local\\SkyCraft_v1")  # fake_guest.py runs one beside a real Skyrim
OFF_SKY = 0x100
OFF_MC = 0x200
OFF_OVL = 0x300
OFF_OVL_HDR = 0x340
OFF_IN = 0x1000
OFF_ACTORS = 0x12000
OFF_EVENTS = 0x17000
OFF_ITEMS = 0x1B100
OFF_ENTITIES = 0x1C000
OFF_BREACHES = 0x1FC40
OFF_COL = 0x20000
COL_BYTES = 32 << 20
OFF_PIX = OFF_COL + COL_BYTES
SLOT = 3840 * 2160 * 4
OFF_RENDER = OFF_PIX + SLOT * 3
RENDER_BYTES = 64 << 20
SIZE = OFF_RENDER + RENDER_BYTES
COL_DATA = COL_BYTES - 0x80

W, H = 1280, 720
FLOOR_Y = 100  # blocks
X0 = 0         # test area origin (blocks, in the fake world's own terms); must be a multiple of 8
TOWN = os.environ.get("SKYCRAFT_TOWN", "Whiterun")
PLACE = int(os.environ.get("SKYCRAFT_PLACE", "4"), 0)
CARRIED = int(os.environ.get("SKYCRAFT_CARRIED", "0"), 0)  # SkyState.carried: 1 = an Elder Scroll
BREACHES = os.environ.get("SKYCRAFT_BREACHES", "1") != "0"
SKILL = max(0, min(255, int(os.environ.get("SKYCRAFT_SKILL", "0"))))  # SkyState.skills: every skill this level
SOULS = int(os.environ.get("SKYCRAFT_SOULS", "0"))  # SkyState.dragonSouls
SOUL_EVERY = float(os.environ.get("SKYCRAFT_SOUL_EVERY", "0"))  # seconds between souls absorbed; 0: never
SKILLS_LOAD = (int(time.time() * 1000) & 0xFFFFFFFF) or 1  # SkyState.skillsLoad: a new game each run
STARTED = time.time()
# A made-up worldspace FormID (load order FF, as no plugin uses), as in SkyState.worldId: Minecraft gives
# it a space of its own (SkySpace). 0x3C (Tamriel) puts the test blocks at the real Tamriel's origin.
WORLD_ID = int(os.environ.get("SKYCRAFT_WORLD", "FF0FA4E0"), 16)
WORLD_B = 0xFF0FA4E1  # a second made-up world (an interior cell) for the world-change check
PLACE_INTERIOR = 1

# McEvent types (skycraft_protocol.h)
EV_HIT_ACTOR, EV_PLAYER_DIED, EV_EXPLOSION, EV_ARROW_STUCK, EV_SKILL_USE, EV_TAKE_ITEM, EV_SLEEP, EV_SPAWN_CREATURE, EV_ENTER_DUNGEON = range(1, 10)
EV_PORTAL_TRAVEL = 11  # portal network: printed, not acted on (a real Skyrim moves the player)
# HitFlags (skycraft_protocol.h), named when a hit is printed; power attack and crouching are combat's (sneak and power attacks)
HIT_FLAG_NAMES = ((1, "critical"), (1 << 1, "projectile"), (1 << 2, "sweep"), (1 << 3, "fire"), (1 << 4, "power attack"), (1 << 5, "crouching"))
# McState eyeLightFlags (light_stealth)
EYE_LIGHT_VALID = 1
EV_COMPANION = 14  # companions: answered for the fake wolf (SKYCRAFT_COMPANION=1)
COMPANION_TAME, COMPANION_FOLLOW, COMPANION_WAIT, COMPANION_HEAL = 1, 2, 3, 4
# ActorFlags and AnimalKind (skycraft_protocol.h)
ACTOR_HOSTILE, ACTOR_DEAD, ACTOR_COMPANION, ACTOR_COMPANION_WAITING = 1, 1 << 1, 1 << 5, 1 << 6
ACTOR_KIND_SHIFT = 8
ANIMAL_DOG, ANIMAL_WOLF, ANIMAL_HORSE = 1, 2, 3
# Companions: SKYCRAFT_COMPANION=1 publishes a wild wolf five blocks behind the fake bandit (west of the start). Give
# yourself bones in Minecraft (/give @s minecraft:bone 16) and right click it: Minecraft's one-in-three roll sends
# kEvCompanion and this answers as Skyrim does (tamed, sitting; an empty hand toggles follow; meat heals it).
COMPANION = os.environ.get("SKYCRAFT_COMPANION", "0") != "0"
FAKE_WOLF = 0xFF00ABCE
SRC_CONTAINER = 2
SRC_WORN = 5
TAG_ORE_INGOT = 1 << 3
TAG_WEAPON, TAG_ARMOR, TAG_ARROW = 1 << 7, 1 << 8, 1 << 13
# ItemKind in tags bits 24-27 (kTagKindShift): what Skyrim types the piece as
TAG_KIND_SHIFT = 24
KIND_SWORD, KIND_BOW, KIND_HELMET, KIND_BOOTS = 1, 7, 11, 14
MAT_IRON, MAT_STEEL, MAT_HIDE = 2, 3, 13
# Gear drops: SKYCRAFT_GEAR=1 kills the fake bandit at t=6 s, after the script's punch, and sends what it wore as worn
# ItemEvents (count negated, container = the bandit), as Loot.cpp does. Minecraft credits the kill and rolls each piece
# at 8.5%; put "chance = 1" in config/skycraft-gear-drops.txt to see every piece drop at the corpse.
GEAR = os.environ.get("SKYCRAFT_GEAR", "0") != "0"
FAKE_BANDIT = 0xFF00ABCD
FAKE_BANDIT_GEAR = [
    (0x00012EB7, 1, TAG_WEAPON | KIND_SWORD << TAG_KIND_SHIFT, MAT_IRON, "Iron Sword"),
    (0x00012E4D, 1, TAG_ARMOR | KIND_HELMET << TAG_KIND_SHIFT, MAT_IRON, "Iron Helmet"),
    (0x00013910, 1, TAG_ARMOR | KIND_BOOTS << TAG_KIND_SHIFT, MAT_HIDE, "Hide Boots"),
    (0x00013985, 1, TAG_WEAPON | KIND_BOW << TAG_KIND_SHIFT, MAT_STEEL, "Hunting Bow"),
    (0x0001397D, 12, TAG_ARROW, MAT_IRON, "Iron Arrow"),
]

# Shouts and spells: SKYCRAFT_MAGIC=1 publishes a magic table (MagicTable @0x11800: three shouts, Unrelenting Force
# equipped, and four spells, 100 of 150 magicka) refreshed twice a second, and answers kEvShout / kEvCastSpell as
# Magic.cpp does (the voice recovers, magicka is taken), printing each. From t=38 s of the script (a run of 55 s or more)
# it presses the keys at their defaults: Shout tapped (one word), Shout again while the voice recovers (refused), Next
# shout and Shout held (Whirlwind Sprint, both its words), Next spell and Cast spell (Firebolt), Next spell and Cast spell
# held two seconds (Healing, in pulses); then checks what came and saves the overlay frame with the HUD as
# <out>_magic.png. Minecraft's own side (the dash, a mob pushed) shows in Minecraft.
MAGIC = os.environ.get("SKYCRAFT_MAGIC", "0") != "0"
OFF_MAGIC = 0x11800
EV_SHOUT, EV_CAST_SPELL = 35, 36
IN_SHOUT_RESULT, IN_CAST_RESULT = 31, 32
SHOUT_DONE, SHOUT_RECOVERING, SHOUT_UNKNOWN = 1, 2, 3
CAST_DONE, CAST_NO_MAGICKA, CAST_UNKNOWN = 1, 2, 3
CAST_PULSE = 1
MAGIC_SHOUTS, MAGIC_SPELLS = 1, 2
FEATURE_SHOUTS, FEATURE_SPELLS = 1 << 5, 1 << 6
SDL_V, SDL_B, SDL_R, SDL_N = 25, 5, 21, 17
# FormID (made up, load order FF), words unlocked, ShoutKind, voice recovery for 1, 2, 3 words, name
FAKE_SHOUTS = [
    (0xFF0DA001, 3, 1, (2.0, 5.0, 10.0), "Unrelenting Force"),  # recovers quickly, so the script's next shout is free
    (0xFF0DA002, 2, 4, (20.0, 25.0, 35.0), "Whirlwind Sprint"),
    (0xFF0DA003, 1, 2, (30.0, 50.0, 100.0), "Fire Breath"),
]
# FormID, magicka cost (per second for concentration), school (ActorValue), SpellKind, MagicSpellFlags (1 concentration,
# 8 hostile), delivery (0 self, 2 aimed), name
FAKE_SPELLS = [
    (0xFF0DB001, 14.0, 20, 1, 1 | 8, 2, "Flames"),
    (0xFF0DB002, 41.0, 20, 1, 8, 2, "Firebolt"),
    (0xFF0DB003, 25.0, 22, 4, 1, 0, "Healing"),
    (0xFF0DB004, 30.0, 22, 5, 1, 0, "Lesser Ward"),
]
MAGICKA_MAX = 150.0
MAGICKA_REGEN = 5.0  # a second

# BreachTheme / BreachFlags (skycraft_protocol.h)
THEME_CAVE, THEME_NORDIC, THEME_DWEMER, THEME_FALMER, THEME_MINE, THEME_FORT, THEME_LAIR = range(7)
BREACH_DISCOVERED, BREACH_CLEARED = 1, 2
MAX_BREACHES = 16


def fake_breaches(spawn):
    """Two dungeon entrances near the fake player (BreachRecord fields; positions in MC coords)."""
    x, y, z = spawn
    return [
        dict(door=0xFF0BEE01, location=0xFF0BEE81, pos=(x + 40.0, y, z), yaw=90.0, theme=THEME_NORDIC, flags=0, level=6, name="Fake Barrow"),
        dict(door=0xFF0BEE02, location=0xFF0BEE82, pos=(x, y, z + 60.0), yaw=180.0, theme=THEME_MINE, flags=BREACH_DISCOVERED | BREACH_CLEARED, level=0,
             name="Fake Mine"),
    ]


def tick():
    import ctypes

    return ctypes.windll.kernel32.GetTickCount64()


class Link:
    def __init__(self):
        import ctypes

        ctypes.windll.kernel32.GetTickCount64.restype = ctypes.c_uint64
        self.m = mmap.mmap(-1, SIZE, tagname=NAME)
        self.m[0:0x100] = bytes(0x100)
        self.m[OFF_SKY:OFF_SKY + 0x100] = bytes(0x100)  # SkyState is 0xA8 bytes
        self.m[OFF_OVL:OFF_OVL + 0x100] = bytes(0x100)
        self.m[OFF_IN:OFF_IN + 0x80] = bytes(0x80)
        self.m[OFF_COL:OFF_COL + 0x80] = bytes(0x80)
        self.m[OFF_ACTORS:OFF_ACTORS + 0x40] = bytes(0x40)
        self.m[OFF_EVENTS:OFF_EVENTS + 0x80] = bytes(0x80)
        self.m[OFF_ITEMS:OFF_ITEMS + 0x80] = bytes(0x80)
        self.m[OFF_ENTITIES:OFF_ENTITIES + 0x40] = bytes(0x40)
        self.m[OFF_BREACHES:OFF_BREACHES + 0x10] = bytes(0x10)
        self.m[OFF_MAGIC:OFF_MAGIC + 0x800] = bytes(0x800)
        self.m[OFF_RENDER:OFF_RENDER + 0x80] = bytes(0x80)
        struct.pack_into("<IIII", self.m, 0, MAGIC, VERSION, os.getpid(), 0)
        self.front = 2
        self.sky_seq = 0
        self.col_head = 0
        self.actor_seq = 0
        self.breach_seq = 0
        self.breaches = {}  # door FormID -> name, as last published
        self.item_head = 0
        self.game_hour = 12.0
        self.events = []
        self.sections = 0
        self.atlas = None
        self.world = WORLD_ID
        self.since_clear = []  # (sx, sy, sz, vertices) of every block section since the last clear-all
        self.clears = 0
        self.companions = {}  # FormID -> "waiting" / "following": what Minecraft tamed (SKYCRAFT_COMPANION)
        self.wolf_health = 0.5  # the fake wolf starts hurt, so meat heals it once it's tamed
        self.magic_seq = 0
        self.magic_refresh = int(time.time()) | 1
        self.magicka = 100.0
        self.voice_ends = 0.0
        self.equipped = FAKE_SHOUTS[0][0]
        self.shouts = []  # (FormID, words asked, answer) of every kEvShout
        self.casts = []   # (FormID, flags, pulse seconds, answer) of every kEvCastSpell

    def heartbeat(self):
        struct.pack_into("<Q", self.m, 0x10, tick())
        self.drain_events()
        self.drain_render()

    def drain_events(self):
        head, tail = struct.unpack_from("<Q", self.m, OFF_EVENTS)[0], struct.unpack_from("<Q", self.m, OFF_EVENTS + 0x40)[0]
        while tail < head:
            typ, form, a, b, c, d, flags = struct.unpack_from("<IIffffI", self.m, OFF_EVENTS + 0x80 + (tail % 512) * 32)
            if typ == EV_TAKE_ITEM:
                print(f"  event from Minecraft: take item {form:08X} x {int(a)} (converted)")
            elif typ == EV_SLEEP:
                self.game_hour = (self.game_hour + a) % 24.0
                print(f"  event from Minecraft: slept {a:.1f} h, game hour now {self.game_hour:.2f}")
            elif typ == EV_SPAWN_CREATURE:
                print(f"  event from Minecraft: spawn creature kind {form} at ({a:.1f},{b:.1f},{c:.1f}), despawn {d:.0f} blocks, max {flags} (ignored)")
            elif typ == EV_ENTER_DUNGEON:
                known = self.breaches.get(form)
                what = f"{known!r} (published)" if known else "a door that was never published (Skyrim would refuse it)"
                print(f"  event from Minecraft: enter dungeon through door {form:08X}: {what}")
            elif typ == EV_PORTAL_TRAVEL:
                came_from = struct.unpack_from("<I", self.m, OFF_EVENTS + 0x80 + (tail % 512) * 32 + 28)[0]
                kind = "interior cell" if flags & 1 else "worldspace"
                print(f"  event from Minecraft: portal travel to {kind} {form:08X} at ({a:.2f},{b:.2f},{c:.2f}) facing {d:.0f}, sent from {came_from:08X}")
            elif typ == EV_COMPANION:
                kind = struct.unpack_from("<I", self.m, OFF_EVENTS + 0x80 + (tail % 512) * 32 + 28)[0]
                self.companion_event(form, flags, a, kind)
            elif typ in (EV_SHOUT, EV_CAST_SPELL):
                request = struct.unpack_from("<I", self.m, OFF_EVENTS + 0x80 + (tail % 512) * 32 + 28)[0]
                if typ == EV_SHOUT:
                    self.shout_event(form, flags, a, b, request)
                else:
                    self.cast_event(form, flags, a, b, c, request)
            else:
                named = "".join(f", {name}" for bit, name in HIT_FLAG_NAMES if typ == EV_HIT_ACTOR and flags & bit)
                print(f"  event from Minecraft: type={typ} form={form:08X} damage={a:.2f} push=({b:.2f},{c:.2f})x{d:.2f} flags={flags:#x}{named}")
            self.events.append(typ)
            tail += 1
        struct.pack_into("<Q", self.m, OFF_EVENTS + 0x40, tail)

    def write_magic(self):
        """Skyrim -> MC: the magic table (MagicTable @0x11800, seqlock), as Magic.cpp fills it."""
        self.magic_seq += 1
        self.magic_refresh = (self.magic_refresh + 1) & 0xFFFFFFFF or 1
        struct.pack_into("<I", self.m, OFF_MAGIC, self.magic_seq * 2 - 1)
        voice = max(0.0, self.voice_ends - time.time())
        shouts = sorted(FAKE_SHOUTS, key=lambda s: s[0] != self.equipped)  # the equipped one first
        struct.pack_into("<IIffffBBBB", self.m, OFF_MAGIC + 4, self.magic_refresh, self.equipped, voice, self.magicka, MAGICKA_MAX, 1.0,
                         len(shouts), len(FAKE_SPELLS), MAGIC_SHOUTS | MAGIC_SPELLS, 0)
        for i, (form, words, kind, recovery, name) in enumerate(shouts):
            struct.pack_into("<IBBBB3f20s", self.m, OFF_MAGIC + 0x40 + i * 40, form, words, kind, 1 if form == self.equipped else 0, 0, *recovery,
                             name.encode("utf-8")[:19])
        for i, (form, cost, school, kind, flags, delivery, name) in enumerate(FAKE_SPELLS):
            struct.pack_into("<IfBBBB24s", self.m, OFF_MAGIC + 0x360 + i * 36, form, cost, school, kind, flags, delivery, name.encode("utf-8")[:23])
        struct.pack_into("<I", self.m, OFF_MAGIC, self.magic_seq * 2)

    def shout_event(self, form, words, yaw, pitch, request):
        """kEvShout, answered as Magic.cpp answers it: the voice checked, then shouted and its recovery begun."""
        shout = next((s for s in FAKE_SHOUTS if s[0] == form), None)
        if shout is None:
            self.push_input(IN_SHOUT_RESULT, SHOUT_UNKNOWN, request, 0, 0)
            self.shouts.append((form, words, "unknown"))
            print(f"  event from Minecraft: shout {form:08X}: not one the fake player knows")
            return
        form, unlocked, kind, recovery, name = shout
        left = self.voice_ends - time.time()
        if left > 0.05:
            self.push_input(IN_SHOUT_RESULT, SHOUT_RECOVERING | kind << 8, request, 0, int(left * 1000))
            self.shouts.append((form, words, "recovering"))
            print(f"  event from Minecraft: shout {name} ({words} words): the voice needs {left:.1f} s more")
            return
        words = max(1, min(words, unlocked))
        self.voice_ends = time.time() + recovery[words - 1]
        self.equipped = form
        self.push_input(IN_SHOUT_RESULT, SHOUT_DONE | kind << 8, request, words, int(recovery[words - 1] * 1000))
        self.shouts.append((form, words, "done"))
        print(f"  event from Minecraft: shout {name}, {words} words, looking yaw {yaw:.0f} pitch {pitch:.0f}: shouted (recovery {recovery[words - 1]:.0f} s)")

    def cast_event(self, form, flags, yaw, pitch, seconds, request):
        """kEvCastSpell, answered as Magic.cpp answers it: the magicka checked and taken."""
        spell = next((s for s in FAKE_SPELLS if s[0] == form), None)
        if spell is None:
            self.push_input(IN_CAST_RESULT, CAST_UNKNOWN, request, 0, form)
            self.casts.append((form, flags, seconds, "unknown"))
            print(f"  event from Minecraft: cast {form:08X}: not a spell the fake player knows")
            return
        form, cost, school, kind, spell_flags, delivery, name = spell
        if flags & CAST_PULSE and spell_flags & 1:
            cost *= max(0.05, min(seconds, 1.0))
        if self.magicka < cost:
            self.push_input(IN_CAST_RESULT, CAST_NO_MAGICKA | kind << 8, request, int(cost * 100), form)
            self.casts.append((form, flags, seconds, "no magicka"))
            print(f"  event from Minecraft: cast {name}: not enough magicka ({self.magicka:.0f} of {cost:.0f})")
            return
        self.magicka -= cost
        self.push_input(IN_CAST_RESULT, CAST_DONE | kind << 8, request, int(cost * 100), form)
        self.casts.append((form, flags, seconds, "done"))
        pulse = f", a {seconds:.2f} s pulse" if flags & CAST_PULSE else ""
        print(f"  event from Minecraft: cast {name}{pulse}: {cost:.1f} magicka taken, {self.magicka:.0f} left")

    def drain_render(self):
        base = OFF_RENDER
        size = RENDER_BYTES - 0x80
        head, tail = struct.unpack_from("<Q", self.m, base)[0], struct.unpack_from("<Q", self.m, base + 0x40)[0]
        while tail < head:
            pos = tail % size
            typ, n = struct.unpack_from("<II", self.m, base + 0x80 + pos)
            if typ == 0:
                tail += size - pos
                continue
            if typ == 1:
                w, h = struct.unpack_from("<II", self.m, base + 0x88 + pos)
                print(f"  render: atlas {w}x{h}")
                start = base + 0x90 + pos
                self.atlas = (w, h, bytes(self.m[start:start + w * h * 4]))
            elif typ == 2:
                sx, sy, sz, verts = struct.unpack_from("<iiiI", self.m, base + 0x88 + pos)
                self.sections += 1
                self.since_clear.append((sx, sy, sz, verts))
                if self.sections <= 5:
                    print(f"  render: section ({sx},{sy},{sz}) {verts} vertices")
                    vbase = base + 0x98 + pos
                    for k in range(min(verts, 6)):
                        x, y, z, u, v, r, g, b, a, light, flags = struct.unpack_from("<5f4BII", self.m, vbase + k * 32)
                        texel = ""
                        if self.atlas:
                            aw, ah, px = self.atlas
                            tx, ty = min(int(u * aw), aw - 1), min(int(v * ah), ah - 1)
                            o = (ty * aw + tx) * 4
                            texel = f" atlas texel ({tx},{ty}) = rgba{tuple(px[o:o + 4])}"
                        print(f"    v{k}: pos ({x:.2f},{y:.2f},{z:.2f}) uv ({u:.4f},{v:.4f}) rgba ({r},{g},{b},{a}) light {light:#x} flags {flags}{texel}")
            elif typ == 3:
                print("  render: clear all")
                self.since_clear = []
                self.clears += 1
            tail += (8 + n + 7) & ~7
        struct.pack_into("<Q", self.m, base + 0x40, tail)

    def write_actor(self, form, x, y, z, vendor=0, name="Test Bandit", dead=False):
        """One hostile actor (or its corpse); vendor is its VendorBuys mask (0: not a merchant). With
        SKYCRAFT_COMPANION=1 the fake wolf is published beside it."""
        actors = [(form, ACTOR_DEAD if dead else ACTOR_HOSTILE, x, y, z, 0.6, 1.8, vendor, name)]
        if COMPANION:
            actors.append(self.fake_wolf(x - 5.0, y, z))
        self.write_actors(actors)

    def fake_wolf(self, x, y, z):
        """The fake wolf (SKYCRAFT_COMPANION=1): a hostile wolf until Minecraft tames it, then the player's (companion
        bits as Companion.cpp sets them: tamed ones wait until told to follow)."""
        state = self.companions.get(FAKE_WOLF)
        flags = ANIMAL_WOLF << ACTOR_KIND_SHIFT
        if state is None:
            flags |= ACTOR_HOSTILE
        else:
            flags |= ACTOR_COMPANION | (ACTOR_COMPANION_WAITING if state == "waiting" else 0)
        return (FAKE_WOLF, flags, x, y, z, 0.7, 0.9, 0, "Wolf")

    def write_actors(self, actors):
        """The actor table: (form, flags, x, y, z, width, height, vendor, name) per actor."""
        struct.pack_into("<I", self.m, OFF_ACTORS, self.actor_seq * 2 + 1)
        for i, (form, flags, x, y, z, width, height, vendor, name) in enumerate(actors):
            health = self.wolf_health if form == FAKE_WOLF else 1.0
            rec = struct.pack("<IIfffffffHH24s", form, flags, x, y, z, 90.0, width, height, health, 10, vendor, name.encode("utf-8")[:23])
            self.m[OFF_ACTORS + 0x40 + i * 64:OFF_ACTORS + 0x40 + (i + 1) * 64] = rec
        struct.pack_into("<I", self.m, OFF_ACTORS + 4, len(actors))
        self.actor_seq += 1
        struct.pack_into("<I", self.m, OFF_ACTORS, self.actor_seq * 2)

    def companion_event(self, form, action, share, kind):
        """kEvCompanion, answered the way Companion.cpp does for the fake wolf (anything else: refused, as Skyrim would
        for an actor that isn't there)."""
        names = {COMPANION_TAME: "tame", COMPANION_FOLLOW: "follow", COMPANION_WAIT: "wait", COMPANION_HEAL: "heal"}
        print(f"  event from Minecraft: companion {names.get(action, action)} {form:08X} (kind {kind}){f', heal {share:.2f}' if action == COMPANION_HEAL else ''}")
        if form != FAKE_WOLF:
            print("    (not the fake wolf: Skyrim would refuse)")
            return
        if action == COMPANION_TAME and kind == ANIMAL_WOLF:
            self.companions[form] = "waiting"  # a freshly tamed wolf sits
        elif action in (COMPANION_FOLLOW, COMPANION_WAIT) and form in self.companions:
            self.companions[form] = "following" if action == COMPANION_FOLLOW else "waiting"
        elif action == COMPANION_HEAL and form in self.companions:
            self.wolf_health = min(1.0, self.wolf_health + share)
        print(f"    the fake wolf is {self.companions.get(form, 'wild')}, health {self.wolf_health:.2f}")

    def write_breaches(self, records, world_id=None):
        """Skyrim -> MC: the dungeon entrances near the player (BreachTable, seqlock: seq odd while writing)."""
        world_id = self.world if world_id is None else world_id
        records = records[:MAX_BREACHES]
        self.breach_seq += 1
        struct.pack_into("<I", self.m, OFF_BREACHES, self.breach_seq * 2 - 1)
        struct.pack_into("<III", self.m, OFF_BREACHES + 4, len(records), world_id, 0)
        for i, r in enumerate(records):
            struct.pack_into("<IIffffBBH28s", self.m, OFF_BREACHES + 16 + i * 56, r["door"], r["location"], *r["pos"], r["yaw"], r["theme"], r["flags"],
                             r["level"], r["name"].encode("utf-8")[:27])
        struct.pack_into("<I", self.m, OFF_BREACHES, self.breach_seq * 2)
        self.breaches = {r["door"]: r["name"] for r in records}

    def mc_alive(self):
        (beat,) = struct.unpack_from("<Q", self.m, 0x18)
        return beat != 0 and tick() - beat < 3000

    def write_sky(self, flags, pos, yaw, pitch, teleport_seq, epoch, town=None, place=None):
        self.sky_seq += 1
        struct.pack_into("<I", self.m, OFF_SKY, self.sky_seq * 2 - 1)
        struct.pack_into("<IIIdddffIIIf", self.m, OFF_SKY + 4, flags, self.world, epoch, pos[0], pos[1], pos[2],
                         yaw, pitch, teleport_seq, W, H, self.game_hour)
        town = TOWN if town is None else town
        place = PLACE if place is None else place
        struct.pack_into("<32sII", self.m, OFF_SKY + 0x40, town.encode("utf-8")[:31], place, CARRIED)  # town[32] NUL-padded, place, carried
        souls = SOULS + (int((time.time() - STARTED) / SOUL_EVERY) if SOUL_EVERY > 0 else 0)
        struct.pack_into("<18sHI", self.m, OFF_SKY + 0x68, bytes([SKILL] * 18), min(souls, 0xFFFF), SKILLS_LOAD)  # skills, dragonSouls, skillsLoad
        struct.pack_into("<I", self.m, OFF_SKY, self.sky_seq * 2)

    def push_item(self, form, count, tags, name, material=0, source=SRC_CONTAINER, chest=1, container=0, quest=0):
        """Skyrim -> MC: an item the player just got (ItemEvent). Dropped if Minecraft is a full ring behind."""
        (tail,) = struct.unpack_from("<Q", self.m, OFF_ITEMS + 0x40)
        if self.item_head - tail >= 64:
            return False
        struct.pack_into("<IiIBBBB28sI", self.m, OFF_ITEMS + 0x80 + (self.item_head % 64) * 48, form, count, tags, material, source, chest, quest,
                         name.encode("utf-8")[:27], container)
        self.item_head += 1
        struct.pack_into("<Q", self.m, OFF_ITEMS, self.item_head)
        return True

    def read_mc(self):
        raw = self.m[OFF_MC:OFF_MC + 0x68]
        seq, flags, x, y, z, yaw, pitch, eye, sens, ack, gui, frame, fov, bph, bam, _, ex, ey, ez = struct.unpack("<IIdddffffIIQfffIddd", raw)
        # McState spaceWorld, spaceX, spaceZ (@0xC8): where Minecraft keeps the blocks of the world its player is in.
        space_world, space_x, space_z = struct.unpack_from("<Iii", self.m, OFF_MC + 0xC8)
        # McState features (@0xD8) and the light at the player's eyes (light_stealth, @0xDC): block, sky, flags.
        features, block_light, sky_light, light_flags = struct.unpack_from("<IBBB", self.m, OFF_MC + 0xD8)
        light = (block_light, sky_light) if light_flags & EYE_LIGHT_VALID else None
        return dict(flags=flags, pos=(x, y, z), yaw=yaw, pitch=pitch, eye=eye, ack=ack, frame=frame, space=(space_world, space_x, space_z),
                    features=features, light=light)

    def push_input(self, typ, code, a=0, b=0, c=0):
        a, b, c = (v - (1 << 32) if v >= 1 << 31 else v for v in (a, b, c))
        head, = struct.unpack_from("<Q", self.m, OFF_IN)
        struct.pack_into("<HHiii", self.m, OFF_IN + 0x80 + (head % 4096) * 16, typ, code, a, b, c)
        struct.pack_into("<Q", self.m, OFF_IN, head + 1)

    def write_col(self, typ, payload):
        msg = 8 + len(payload)
        msg = (msg + 7) & ~7
        pos = self.col_head % COL_DATA
        if pos + msg > COL_DATA:
            struct.pack_into("<II", self.m, OFF_COL + 0x80 + pos, 0, 0)
            self.col_head += COL_DATA - pos
            pos = 0
        base = OFF_COL + 0x80 + pos
        struct.pack_into("<II", self.m, base, typ, len(payload))
        self.m[base + 8:base + 8 + len(payload)] = payload
        self.col_head += msg
        struct.pack_into("<Q", self.m, OFF_COL, self.col_head)

    def acquire_overlay(self):
        (state,) = struct.unpack_from("<I", self.m, OFF_OVL)
        if not state & 4:
            return None
        struct.pack_into("<I", self.m, OFF_OVL, self.front)
        self.front = state & 3
        w, h, flags = struct.unpack_from("<III", self.m, OFF_OVL_HDR + self.front * 0x40)
        start = OFF_PIX + self.front * SLOT
        return w, h, flags, bytes(self.m[start:start + w * h * 4])


def clear_payload(epoch, world):
    """kColClear: the new epoch and the Skyrim world it describes (Minecraft puts its regions in that world's space)."""
    return struct.pack("<II", epoch, world)


def region_payload(rx, ry, rz, epoch, block_fn):
    blocks = []
    for by in range(8):
        for bz in range(8):
            for bx in range(8):
                x, y, z = rx * 8 + bx, ry * 8 + by, rz * 8 + bz
                bits = block_fn(x, y, z)
                if bits and any(bits):
                    blocks.append(struct.pack("<iiiI8Q", x, y, z, 0, *bits))
    head = struct.pack("<iiiiiiII", rx * 8, ry * 8, rz * 8, rx * 8 + 7, ry * 8 + 7, rz * 8 + 7, epoch, len(blocks))
    return head + b"".join(blocks)


def floor_tris_payload(rx, ry, rz, epoch):
    """Exact triangles for the flat floor top (y = FLOOR_Y) inside one region (the player collides with these)."""
    y = float(FLOOR_Y)
    tris = []
    if ry * 8 <= FLOOR_Y - 1 <= ry * 8 + 7:
        x0, z0, x1, z1 = rx * 8.0, rz * 8.0, rx * 8.0 + 8.0, rz * 8.0 + 8.0
        tris.append(struct.pack("<9fI", x0, y, z0, x1, y, z0, x1, y, z1, 0))
        tris.append(struct.pack("<9fI", x0, y, z0, x1, y, z1, x0, y, z1, 0))
    head = struct.pack("<iiiiiiII", rx * 8, ry * 8, rz * 8, rx * 8 + 7, ry * 8 + 7, rz * 8 + 7, epoch, len(tris))
    return head + b"".join(tris)


FULL = [0xFFFFFFFFFFFFFFFF] * 8


def world(x, y, z):
    """A flat floor at FLOOR_Y-1, plus a staircase of half-block steps towards +X, and a 1/8 slope towards -X."""
    x -= X0
    if y == FLOOR_Y - 1:
        return FULL
    if 3 <= x < 11 and -2 <= z <= 2 and y == FLOOR_Y + (x - 3) // 2:
        return FULL if (x - 3) % 2 == 1 else [0xFFFFFFFFFFFFFFFF] * 4 + [0] * 4
    if -12 <= x <= -4 and -2 <= z <= 2:
        # gentle 1/8-step ramp going up towards -X: height rises one sub-voxel per sub-voxel... per block: 1 voxel/8 voxels
        rise = (-4 - x)  # sub-voxels of height at this block
        base = FLOOR_Y + rise // 8
        if y == base:
            layers = rise % 8 + 1
            return [0xFFFFFFFFFFFFFFFF] * layers + [0] * (8 - layers)
    return None


def save_png(path, w, h, rgba, bottom_up):
    rows = [rgba[r * w * 4:(r + 1) * w * 4] for r in range(h)]
    if bottom_up:
        rows.reverse()
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def check_arrow_texture(link):
    # An arrow's two atlas rects (side view, back plate) should cover the arrow entity texture.
    count, _ = struct.unpack_from("<II", link.m, OFF_ENTITIES + 4)
    for i in range(min(count, 16)):
        base = OFF_ENTITIES + 0x40 + i * 96
        (kind,) = struct.unpack_from("<I", link.m, base)
        if kind != 1 or not link.atlas:
            continue
        aw, ah, px = link.atlas
        for name, k in (("side", 0), ("back", 1)):
            u0, v0, u1, v1 = struct.unpack_from("<4f", link.m, base + 44 + k * 16)
            x0, y0, x1, y1 = round(u0 * aw), round(v0 * ah), round(u1 * aw), round(v1 * ah)
            rows = []
            for y in range(y0, y1):
                rows.append("".join("#" if px[(y * aw + x) * 4 + 3] > 127 else "." for x in range(x0, x1)))
            print(f"  arrow {name} rect ({x0},{y0})-({x1},{y1}):")
            for r in rows:
                print("    " + r)
        return


def read_world_entities(link):
    count, has_sel = struct.unpack_from("<II", link.m, OFF_ENTITIES + 4)
    kinds = []
    for i in range(min(count, 16)):
        kind, eid, x, y, z = struct.unpack_from("<IIfff", link.m, OFF_ENTITIES + 0x40 + i * 96)
        kinds.append((kind, round(x, 2), round(y, 2), round(z, 2)))
    sel = struct.unpack_from("<6f", link.m, OFF_ENTITIES + 0x0C) if has_sel else None
    return kinds, sel


def main():
    seconds = float(sys.argv[1]) if len(sys.argv) > 1 else 90
    out = sys.argv[2] if len(sys.argv) > 2 else "overlay.png"
    link = Link()
    print("fake Skyrim up; waiting for Minecraft...")
    epoch = 1
    sent = False
    start = time.time()
    # Far from anywhere a real Skyrim worldspace maps to, so test blocks never show up in the game.
    spawn = (X0 + 0.5, FLOOR_Y, 0.5)
    yaw = -90.0  # facing +X
    pitch = 10.0
    script_t0 = None
    gear_sent = False
    last_print = 0
    saved = False
    checked = False
    tseq = int(time.time()) % 100000 + 2  # new teleport every run
    last_breaches = 0.0
    place, town = PLACE, TOWN
    last_space = None
    last_light = None
    door = 0  # world-change check: 0 not yet, 1 in world B, 2 back in world A, 3 done
    results = []
    last_magic = 0.0
    magic_checked = False
    # The magic keys after the main script (SKYCRAFT_MAGIC): (time, SDL scancode, 1 down / 0 up).
    magic_keys = [(38.0, SDL_V, 1), (38.05, SDL_V, 0), (39.0, SDL_V, 1), (39.1, SDL_V, 0), (40.5, SDL_B, 1), (40.55, SDL_B, 0),
                  (41.0, SDL_V, 1), (42.0, SDL_V, 0), (42.5, SDL_N, 1), (42.55, SDL_N, 0), (43.0, SDL_R, 1), (43.05, SDL_R, 0),
                  (44.0, SDL_N, 1), (44.05, SDL_N, 0), (44.5, SDL_R, 1), (46.5, SDL_R, 0)]
    while time.time() - start < seconds:
        link.heartbeat()
        link.write_sky(1, spawn, yaw, pitch, tseq, epoch, town, place)
        if time.time() - last_breaches >= 1.0:  # Skyrim refreshes the table about once a second; empty indoors
            last_breaches = time.time()
            first = not link.breaches
            link.write_breaches(fake_breaches(spawn) if BREACHES and not place & PLACE_INTERIOR else [])
            if first and link.breaches:
                print("breach doors published: " + ", ".join(f"{door:08X} {name}" for door, name in link.breaches.items()))
        if link.mc_alive() and not sent:
            link.write_col(1, clear_payload(epoch, link.world))
            for rx in range(X0 // 8 - 3, X0 // 8 + 3):
                for rz in range(-3, 3):
                    for ry in range(11, 15):
                        link.write_col(3, floor_tris_payload(rx, ry, rz, epoch))
                        link.write_col(2, region_payload(rx, ry, rz, epoch, world))
            sent = True
            print(f"collision sent (world {link.world:08X}, epoch {epoch})")
        if MAGIC and time.time() - last_magic >= 0.5:  # Magic.cpp refreshes its table about twice a second
            link.magicka = min(MAGICKA_MAX, link.magicka + MAGICKA_REGEN * (time.time() - last_magic if last_magic else 0.0))
            last_magic = time.time()
            link.write_magic()
        mc = link.read_mc()
        if mc["light"] != last_light:
            last_light = mc["light"]
            print(f"light at Minecraft's eyes (light_stealth): block {last_light[0]}, sky {last_light[1]}" if last_light else "light at Minecraft's eyes: not sent")
        if mc["space"][0] and mc["space"] != last_space:
            last_space = mc["space"]
            print(f"Minecraft keeps world {mc['space'][0]:08X}'s blocks in its space at {mc['space'][1]} {mc['space'][2]}")
        in_world = mc["flags"] & 1 and mc["ack"] == tseq and sent and abs(mc["pos"][1] - FLOOR_Y) < 0.05 and abs(mc["pos"][0] - spawn[0]) < 1
        if in_world and script_t0 is None:
            script_t0 = time.time()
            print("MC in world at", mc["pos"])
        if script_t0 is not None:
            t = time.time() - script_t0
            # A fake NPC two blocks ahead: punch it, then have it hit back.
            link.write_actor(FAKE_BANDIT, X0 + 2.5, FLOOR_Y, 0.5, dead=GEAR and t >= 6.0)
            if GEAR and 6.0 <= t and not gear_sent:
                gear_sent = True
                for form, count, tags, material, name in FAKE_BANDIT_GEAR:
                    link.push_item(form, -count, tags, name, material=material, source=SRC_WORN, chest=0, container=FAKE_BANDIT)
                print(f"the fake bandit died wearing {', '.join(g[4] for g in FAKE_BANDIT_GEAR)} (worn ItemEvents sent)")
            for click in (0.8, 1.5):  # the first click only grabs the mouse, like focusing a window
                if click <= t < click + 0.05:
                    link.push_input(2, 1, 1)  # left mouse down
                if click + 0.1 <= t < click + 0.15:
                    link.push_input(2, 1, 0)
            if 2.5 <= t < 2.55:
                link.push_input(7, 0, 2000, 0xFF00ABCD, 0)  # 20 Skyrim damage, melee, from the NPC
            # Build: cobblestone (hotbar 8) onto the Skyrim floor, looking down and to the side.
            if 3.0 <= t < 3.05:
                yaw, pitch = 0.0, 55.0  # facing +Z, away from the NPC
                link.push_input(1, 37, 1)  # '8'
            if 3.1 <= t < 3.15:
                link.push_input(1, 37, 0)
            if 3.6 <= t < 3.65:
                link.push_input(2, 3, 1)  # right mouse: place
            if 3.7 <= t < 3.75:
                link.push_input(2, 3, 0)
            # Bow (hotbar 3): draw and loose an arrow into the floor further out.
            if 4.2 <= t < 4.25:
                yaw, pitch = 180.0, 35.0  # facing -Z: open Skyrim floor, no Minecraft blocks
                link.push_input(1, 32, 1)  # '3'
            if 4.3 <= t < 4.35:
                link.push_input(1, 32, 0)
            if 4.6 <= t < 4.65:
                link.push_input(2, 3, 1)
            if 5.8 <= t < 5.85:
                link.push_input(2, 3, 0)
            if 8.0 <= t and checked is False:
                checked = 1
                kinds, sel = read_world_entities(link)
                print(f"world entities at t=8: {kinds} (kind 1 = arrow), block outline {sel}")
            if 12.0 <= t and checked == 1:
                checked = 2
                kinds, sel = read_world_entities(link)
                print(f"world entities at t=12: {kinds} (a stuck arrow hasn't moved)")
                check_arrow_texture(link)
            # Pickup: drop one cobblestone (Q) ahead, walk over it, see whether it's gone.
            if 12.5 <= t < 12.55:
                yaw, pitch = 90.0, 10.0  # facing -X
                link.push_input(1, 37, 1)  # '8'
            if 12.6 <= t < 12.65:
                link.push_input(1, 37, 0)
            if 13.0 <= t < 13.05:
                link.push_input(1, 20, 1)  # Q (drop)
            if 13.1 <= t < 13.15:
                link.push_input(1, 20, 0)
            if 15.0 <= t and checked == 2:
                checked = 3
                kinds, sel = read_world_entities(link)
                print(f"world entities after dropping: {kinds} (kind 2 = dropped item)")
                link.push_input(1, 26, 1)  # W: walk over it
            if 16.5 <= t and checked == 3:
                checked = 4
                link.push_input(1, 26, 0)
            if 18.0 <= t and checked == 4:
                checked = 5
                kinds, sel = read_world_entities(link)
                print(f"world entities after walking over it: {kinds}")
            # Loot: two Iron Ingots out of a chest; Minecraft's rules decide (EV_TAKE_ITEM back if converted).
            if 19.0 <= t and checked == 5:
                checked = 6
                queued = link.push_item(0x0005ACE4, 2, TAG_ORE_INGOT, "Iron Ingot", container=0x000ABC01)
                print(f"looted 2 x Iron Ingot from a chest in {TOWN!r} (place flags {PLACE:#x}): {'sent' if queued else 'item ring full'}")
            # World spaces: through a load door into world B (same coordinates in Skyrim's terms), and back.
            if 21.0 <= t and door == 0:
                door = 1
                blocks_in_a = [s for s in link.since_clear if s[3] > 0]
                print(f"world A has {len(blocks_in_a)} block sections drawn; through a door into world {WORLD_B:08X} (an interior)")
                link.world, place, town = WORLD_B, PLACE_INTERIOR, "Fake Interior"
                epoch += 1
                tseq += 1
                sent = False
                yaw, pitch = 0.0, 55.0
            if 28.0 <= t and door == 1:
                door = 2
                there = mc["ack"] == tseq and abs(mc["pos"][0] - spawn[0]) < 1 and abs(mc["pos"][2] - spawn[2]) < 1
                results.append(("world B: Minecraft reports the player in B's own terms", there, f"pos {mc['pos']}, space {mc['space']}"))
                results.append(("world B: Minecraft's space is B's", mc["space"][0] == WORLD_B, f"space {mc['space']}"))
                leaked = [s for s in link.since_clear if s[3] > 0]
                results.append(("world B: none of world A's blocks drawn", link.clears >= 2 and not leaked, f"{len(leaked)} sections with blocks: {leaked[:4]}"))
                print(f"back through the door into world {WORLD_ID:08X}")
                link.world, place, town = WORLD_ID, PLACE, TOWN
                epoch += 1
                tseq += 1
                sent = False
            if 35.0 <= t and door == 2:
                door = 3
                back = [s for s in link.since_clear if s[3] > 0]
                results.append(("world A again: its blocks drawn again", bool(back), f"{len(back)} sections with blocks"))
                results.append(("world A again: Minecraft's space is A's", mc["space"][0] == WORLD_ID, f"space {mc['space']}"))
                for what, ok, detail in results:
                    print(f"{'PASS' if ok else 'FAIL'} {what} ({detail})")
            if MAGIC:
                for at, key, down in magic_keys:
                    if at <= t < at + 0.05:
                        link.push_input(1, key, down)
                if 45.5 <= t and not magic_checked:
                    magic_checked = True
                    frame = link.acquire_overlay()
                    if frame:
                        w, h, flags, px = frame
                        save_png(os.path.splitext(out)[0] + "_magic.png", w, h, px, flags & 1)
                        print(f"saved overlay with the magic HUD -> {os.path.splitext(out)[0]}_magic.png")
                if 47.5 <= t and magic_checked is True:
                    magic_checked = 2
                    sprint = FAKE_SHOUTS[1][0]
                    firebolt, healing = FAKE_SPELLS[1][0], FAKE_SPELLS[2][0]
                    pulses = [c for c in link.casts if c[0] == healing and c[1] & CAST_PULSE]
                    checks = [
                        ("magic: Minecraft has the shouts and spells switches on", mc["features"] & (FEATURE_SHOUTS | FEATURE_SPELLS) == FEATURE_SHOUTS | FEATURE_SPELLS,
                         f"features {mc['features']:#x}"),
                        ("magic: a tap shouted one word", link.shouts[:1] == [(FAKE_SHOUTS[0][0], 1, "done")], f"{link.shouts[:1]}"),
                        ("magic: a shout while the voice recovers was refused", any(r == "recovering" for _, _, r in link.shouts), f"{link.shouts}"),
                        ("magic: Next shout, then held: Whirlwind Sprint, both words", (sprint, 2, "done") in link.shouts, f"{link.shouts}"),
                        ("magic: Next spell, Cast spell: one Firebolt", sum(1 for c in link.casts if c[0] == firebolt) == 1, f"{link.casts}"),
                        ("magic: Healing held two seconds came in pulses", 3 <= len(pulses) <= 6 and all(abs(c[2] - 0.5) < 0.01 for c in pulses), f"{pulses}"),
                    ]
                    for what, ok, detail in checks:
                        print(f"{'PASS' if ok else 'FAIL'} {what} ({detail})")
            if 10 <= t and not saved:
                frame = link.acquire_overlay()
                if frame:
                    w, h, flags, px = frame
                    save_png(out, w, h, px, flags & 1)
                    nonzero = sum(1 for i in range(3, len(px), 4 * 97) if px[i])
                    print(f"saved overlay {w}x{h} -> {out} (sampled non-transparent pixels: {nonzero})")
                    saved = True
        if time.time() - last_print > 1.0:
            last_print = time.time()
            print(f"t={time.time()-start:5.1f} mc flags={mc['flags']:#x} ack={mc['ack']} pos=({mc['pos'][0]:.3f}, {mc['pos'][1]:.3f}, {mc['pos'][2]:.3f}) yaw={mc['yaw']:.1f} frame={mc['frame']}")
        time.sleep(1 / 60)
    print(f"summary: {link.sections} section meshes, events {link.events}")
    if link.atlas and len(sys.argv) > 3:
        w, h, px = link.atlas
        save_png(sys.argv[3], w, h, px, False)
        print(f"atlas saved to {sys.argv[3]}")


if __name__ == "__main__":
    main()
