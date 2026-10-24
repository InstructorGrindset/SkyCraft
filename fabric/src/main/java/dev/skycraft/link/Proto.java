package dev.skycraft.link;

/**
 * Mirror of protocol/skycraft_protocol.h. Keep the two in sync.
 */
public final class Proto {
	private Proto() {
	}

	public static final int MAGIC = 0x43594B53;
	public static final int VERSION = 13;
	// A second client on the same PC (multiplayer testing) talks to its own stand-in Skyrim:
	// -Dskycraft.link=Local\SkyCraft_guest (see tools/fake_guest.py).
	public static final String MAPPING_NAME = System.getProperty("skycraft.link", "Local\\SkyCraft_v1");
	public static final double UNITS_PER_BLOCK = 70.0;

	public static final long OFF_HEADER = 0x0;
	public static final long OFF_SKY_STATE = 0x100;
	public static final long OFF_MC_STATE = 0x200;
	public static final long OFF_WATER_GRID = 0x400;
	public static final int WATER_GRID_SIZE = 16;
	public static final long WG_SEQ = 0x0, WG_ORIGIN_X = 0x4, WG_ORIGIN_Z = 0x8, WG_WORLD_ID = 0xC, WG_SURFACE = 0x10;
	public static final long OFF_OVERLAY_CTL = 0x300;
	public static final long OFF_OVERLAY_SLOT_HDR = 0x340;
	public static final long OFF_INPUT_RING = 0x1000;
	public static final long OFF_COLLISION_RING = 0x20000;
	public static final long COLLISION_RING_BYTES = 32L << 20;
	public static final long OFF_OVERLAY_PIXELS = OFF_COLLISION_RING + COLLISION_RING_BYTES;
	public static final int MAX_OVERLAY_W = 3840;
	public static final int MAX_OVERLAY_H = 2160;
	public static final long OVERLAY_SLOT_BYTES = (long) MAX_OVERLAY_W * MAX_OVERLAY_H * 4;
	public static final int OVERLAY_SLOTS = 3;
	public static final long OFF_ACTOR_TABLE = 0x12000;
	public static final long OFF_EVENT_RING = 0x17000;
	public static final long OFF_ITEM_RING = 0x1B100;
	public static final long OFF_WORLD_ENTITIES = 0x1C000;
	public static final long OFF_BREACH_TABLE = 0x1FC40;
	/** Quest markers (added within 13): Skyrim's compass quest targets, see QuestTargetTable in the protocol header. */
	public static final long OFF_QUEST_TARGETS = 0x11100;
	public static final long OFF_RENDER_RING = OFF_OVERLAY_PIXELS + OVERLAY_SLOT_BYTES * OVERLAY_SLOTS;
	public static final long RENDER_RING_BYTES = 64L << 20;
	public static final long MAPPING_BYTES = OFF_RENDER_RING + RENDER_RING_BYTES;

	// Input types added in v5
	public static final int IN_HURT = 7;
	public static final int IN_OPEN_MENU = 8;
	public static final int HURT_MELEE = 0;
	public static final int HURT_PROJECTILE = 1;
	public static final int HURT_MAGIC = 2;
	public static final int HURT_OTHER = 3;
	public static final int HURT_BLOCKED_IN_SKYRIM = 1;
	public static final int HURT_POWER_ATTACK = 2;

	// Actor table (relative to OFF_ACTOR_TABLE)
	public static final int MAX_ACTORS = 256;
	public static final long AT_SEQ = 0x00;
	public static final long AT_COUNT = 0x04;
	public static final long AT_RECORDS = 0x40;
	public static final long ACTOR_RECORD_BYTES = 64;
	public static final int ACTOR_HOSTILE = 1;
	public static final int ACTOR_DEAD = 1 << 1;
	public static final int ACTOR_ESSENTIAL = 1 << 2;
	public static final int ACTOR_IN_COMBAT = 1 << 3;
	/** A creature EV_SPAWN_CREATURE brought. */
	public static final int ACTOR_SPAWNED = 1 << 4;
	/**
	 * Companions (added within protocol 13; an older Skyrim plugin never sets it): the player's animal. Tamed through
	 * Minecraft (EV_COMPANION), Skyrim's own animal follower (Meeko, Vigilance, a stray or bought dog, ...), another dog,
	 * wolf or horse that is the player's teammate, or a horse the player owns.
	 */
	public static final int ACTOR_COMPANION = 1 << 5;
	/** A companion that isn't following now: told to wait (sit), or another animal holds Skyrim's one animal follower slot. */
	public static final int ACTOR_COMPANION_WAITING = 1 << 6;
	/**
	 * Shared world events (added within protocol 13; an older Skyrim plugin never sets it): a dragon, by its race's
	 * ActorTypeDragon keyword (party.WorldEvents also knows dragons by name, for an older plugin).
	 */
	public static final int ACTOR_DRAGON = 1 << 18;
	/** Bits 8-11 of the actor flags: the actor's ANIMAL_* kind, from its race (Skyrim's AnimalKinds.h). Added within 13. */
	public static final int ACTOR_KIND_SHIFT = 8;
	public static final int ACTOR_KIND_MASK = 0xF << ACTOR_KIND_SHIFT;
	// AnimalKind
	public static final int ANIMAL_NONE = 0;
	/** Dogs, huskies, death hounds: tamed like a Minecraft wolf. */
	public static final int ANIMAL_DOG = 1;
	/** Wolves (not werewolves): tamed like a Minecraft wolf. */
	public static final int ANIMAL_WOLF = 2;
	public static final int ANIMAL_HORSE = 3;
	/** Towns (added within protocol 13; an older Skyrim plugin never sets it): a guard (Actor::IsGuard). Bit 17 is set aside for towns. */
	public static final int ACTOR_GUARD = 1 << 16;
	/** Creatures (added within protocol 13; an older Skyrim plugin never sets it): keeps away from strong light (skeevers, Falmer). */
	public static final int ACTOR_LIGHT_SHY = 1 << 14;
	/** Draugr, skeletons, vampires: burns in daylight outdoors; Minecraft's undead monsters leave it alone. Added within 13. */
	public static final int ACTOR_UNDEAD = 1 << 15;

	// Event ring (relative to OFF_EVENT_RING)
	public static final int EVENT_RING_ENTRIES = 512;
	public static final long ER_HEAD = 0x00;
	public static final long ER_TAIL = 0x40;
	public static final long ER_DATA = 0x80;
	public static final long EVENT_BYTES = 32;
	public static final int EV_HIT_ACTOR = 1;
	public static final int EV_PLAYER_DIED = 2;
	public static final int EV_EXPLOSION = 3;
	public static final int EV_ARROW_STUCK = 4;
	public static final int EV_SKILL_USE = 5;
	/** Minecraft converted a looted item: formId = the item's base form, a = how many Skyrim removes. */
	public static final int EV_TAKE_ITEM = 6;
	/** The player slept in a Minecraft bed: a = game hours Skyrim passes. */
	public static final int EV_SLEEP = 7;
	/**
	 * A Skyrim creature comes out of the dark in a dug tunnel: formId = SPAWN_* kind, a/b/c = its feet (MC coords),
	 * d = despawn distance (blocks), flags = most spawned creatures alive at once.
	 */
	public static final int EV_SPAWN_CREATURE = 8;
	/** The player opened a breach door underground: formId = the dungeon's exterior load door (BreachRecord doorRef). */
	public static final int EV_ENTER_DUNGEON = 9;
	/**
	 * A fresh right click Minecraft had no use for (client ClickActivate): Skyrim activates whatever its crosshair is
	 * on, as G does, at most once per 0.4 s. No fields. Added within protocol 13; an older Skyrim plugin ignores it.
	 */
	public static final int EV_ACTIVATE = 10;
	/**
	 * The host stood in a bound portal (portal.SkyPortals): Skyrim moves the player to its partner behind a loading screen,
	 * as a load door does. formId = the destination Skyrim world as SkyState worldId names it (a worldspace FormID outside,
	 * an interior cell FormID when flags has PORTAL_INTERIOR), a/b/c = where the feet go (MC coords, just in front of the
	 * partner portal on its exit side), d = the yaw to face (MC degrees), weapon = SkyState worldId when sent (Skyrim drops
	 * the request once the player is anywhere else). Added within protocol 13; an older Skyrim plugin ignores it.
	 */
	public static final int EV_PORTAL_TRAVEL = 11;
	/** EV_PORTAL_TRAVEL flags: the destination formId is an interior cell. */
	public static final int PORTAL_INTERIOR = 1;
	/**
	 * Companions (companion.SkyCompanions): Minecraft's taming rules on a Skyrim animal. formId = the actor, flags = a
	 * COMPANION_* action, weapon = the ANIMAL_* kind read for it in the actor table (Skyrim refuses a taming whose kind
	 * isn't its own), a = COMPANION_HEAL's share of the animal's maximum health (0..1). Added within protocol 13 (event
	 * 12 and 13 are the Minecraft view's); an older Skyrim plugin ignores it.
	 */
	public static final int EV_COMPANION = 14;
	/** The taming roll came up: dogs and wolves become Skyrim's animal follower, waiting (sitting); horses the player's own. */
	public static final int COMPANION_TAME = 1;
	/** Follow the player (taking Skyrim's one animal follower slot). */
	public static final int COMPANION_FOLLOW = 2;
	/** Wait here (sit). */
	public static final int COMPANION_WAIT = 3;
	/** Fed: restore a share of its health. */
	public static final int COMPANION_HEAL = 4;
	/**
	 * Selling to merchants (trade.SkySell): how much Skyrim gold can this merchant pay? formId = the merchant actor, flags =
	 * a request number Skyrim answers with in IN_MERCHANT_GOLD. Added within protocol 13; an older Skyrim plugin ignores it
	 * (no answer comes, and the sale is called off).
	 */
	public static final int EV_MERCHANT_GOLD = 19;
	/**
	 * Selling to merchants: Minecraft took goods the merchant bought. formId = the merchant actor, a = the Skyrim gold it pays,
	 * b = how many items (for the log), flags = the request number. Skyrim moves that much gold from the merchant's chest (or
	 * its own purse) into the player's inventory, never more than the merchant has. Added within protocol 13.
	 */
	public static final int EV_SELL_TO_MERCHANT = 20;
	/**
	 * Shared world events (party.SharedWorldEvents): something another player in the Minecraft world went through shakes
	 * this player's Skyrim camera a little. a = strength (0 to 1), b = seconds (0.1 to 3). Added within protocol 13; an
	 * older Skyrim plugin ignores it.
	 */
	public static final int EV_PARTY_SHAKE = 29;

	// Towns and building (added within protocol 13; an older Skyrim plugin ignores them). a/b/c are a place in the mirror,
	// sent with SkyLink.pushEventAt in the current Skyrim world's terms.
	/**
	 * The host griefed a town (town.SkyTown): formId = a CRIME_* kind, a/b/c = where, d = what it's worth in gold (a few
	 * seconds' worth added up), flags = how many blocks. Skyrim decides who saw it and raises a steal alarm.
	 */
	public static final int EV_TOWN_CRIME = 23;
	/** The host placed, broke or dug blocks in a town with a guard near: a/b/c = where, flags = a BUILD_* kind. At most one every 2 s. */
	public static final int EV_TOWN_BUILD = 24;
	/** Enough blocks stand on a Hearthfire plot for a stage: formId = the plot (SS_HEARTH_PLOT), flags = a STAGE_*, a = blocks standing. */
	public static final int EV_HEARTHFIRE_STAGE = 25;
	/** Redstone power at a Dwemer door's block (IN_DWEMER_DOOR) came or went: a/b/c = the block's centre, flags = 1 powered, 0 not. */
	public static final int EV_REDSTONE_DOOR = 26;
	// EV_TOWN_CRIME kinds
	/** Skyrim's own ground dug out, or a block of it that digging revealed broken. */
	public static final int CRIME_GROUND = 1;
	/** A Skyrim building, wall or other object dug into. */
	public static final int CRIME_OBJECT = 2;
	/** A Minecraft block another player placed in the town broken. */
	public static final int CRIME_BLOCK = 3;
	// EV_TOWN_BUILD kinds
	public static final int BUILD_PLACE = 1;
	public static final int BUILD_BREAK = 2;
	public static final int BUILD_DIG = 3;
	// EV_HEARTHFIRE_STAGE stages
	public static final int STAGE_SMALL_HOUSE = 1;
	public static final int STAGE_MAIN_HALL = 2;
	public static final int STAGE_WINGS = 3;
	/**
	 * Minecraft's creatures and Skyrim's actors (creatures.SkyCreatures; added within protocol 13, an older Skyrim plugin ignores it).
	 * flags bits 0-7 = MOB_HIT or MOB_NEAR. MOB_HIT: formId = the actor, a = Minecraft damage, b/c = push direction x/z, d = push
	 * strength, flags bits 8-15 = HIT_* flags, bits 16-19 = a MOB_BY_* attacker, weapon = WEAPON_*. MOB_NEAR: formId = the creature's
	 * entity id, a/b/c = its feet (a place: sent with pushEventAt), d = half its width, flags bits 8-15 = MOB_UNDEAD / MOB_PLAYERS_WOLF,
	 * weapon = the FormID of the actor it goes for (0: none).
	 */
	public static final int EV_MOB = 27;
	public static final int MOB_HIT = 1;
	public static final int MOB_NEAR = 2;
	public static final int MOB_HIT_FLAGS_SHIFT = 8;
	public static final int MOB_ATTACKER_SHIFT = 16;
	public static final int MOB_TRAITS_SHIFT = 8;
	/** A Minecraft monster: credited to no player. */
	public static final int MOB_BY_MONSTER = 0;
	/** The player's tamed wolf: a Minecraft hit as the player's (vanilla credits a tamed wolf's hits to its owner), no skill trained. */
	public static final int MOB_BY_WOLF = 1;
	/** Daylight burning an undead actor: no attacker. */
	public static final int MOB_BY_SUN = 2;
	public static final int MOB_UNDEAD = 1;
	public static final int MOB_PLAYERS_WOLF = 1 << 1;
	/**
	 * Horses carry over (creatures.SkyHorses; added within protocol 13, an older Skyrim plugin ignores it): flags = HORSE_*, formId = the
	 * Skyrim horse (0: none yet), a/b/c = HORSE_ARRIVE's place (sent with pushEventAt), d = its yaw, weapon = HORSE_ARRIVE's stats
	 * (HorseRules.packStats).
	 */
	public static final int EV_HORSE = 28;
	public static final int HORSE_ARRIVE = 1;
	public static final int HORSE_DEPART = 2;
	public static final int HORSE_LOST = 3;
	public static final int HORSE_STAT_MAX_HEALTH_SHIFT = 12;
	public static final int HORSE_STAT_HEALTH_SHIFT = 20;

	// Breach table (relative to OFF_BREACH_TABLE): Skyrim -> MC, seqlock, see BreachTable in the protocol header.
	public static final int MAX_BREACHES = 16;
	public static final long BT_SEQ = 0x00;
	public static final long BT_COUNT = 0x04;
	public static final long BT_WORLD_ID = 0x08;
	public static final long BT_RECORDS = 0x10;
	public static final long BREACH_RECORD_BYTES = 56;
	// BreachRecord fields
	public static final long BR_DOOR_REF = 0;
	public static final long BR_LOCATION = 4;
	public static final long BR_X = 8;
	public static final long BR_Y = 12;
	public static final long BR_Z = 16;
	public static final long BR_YAW = 20;
	public static final long BR_THEME = 24;
	public static final long BR_FLAGS = 25;
	public static final long BR_LEVEL = 26;
	public static final long BR_NAME = 28;
	public static final int BR_NAME_BYTES = 28;
	// BreachTheme
	public static final int THEME_CAVE = 0;
	public static final int THEME_NORDIC = 1;
	public static final int THEME_DWEMER = 2;
	public static final int THEME_FALMER = 3;
	public static final int THEME_MINE = 4;
	public static final int THEME_FORT = 5;
	public static final int THEME_LAIR = 6;
	// BreachFlags
	public static final int BREACH_DISCOVERED = 1;
	public static final int BREACH_CLEARED = 1 << 1;
	// SpawnKind
	public static final int SPAWN_SKEEVER = 1;
	public static final int SPAWN_SKELETON = 2;
	public static final int SPAWN_DRAUGR = 3;
	/** EV_SPAWN_CREATURE weapon (SpawnOrigin, added within 13): a dug tunnel (deep creatures), or outdoors at night (night monsters). */
	public static final int SPAWN_FROM_DARK = 0;
	public static final int SPAWN_AT_NIGHT = 1;

	// Quest target table (relative to OFF_QUEST_TARGETS): Skyrim -> MC, seqlock (seq 0: never written, an older plugin), see
	// QuestTargetTable in the protocol header. Added within 13. Read by SkyLink.readQuestTargets, decoded by quests.QuestTable.
	public static final int MAX_QUEST_TARGETS = 16;
	public static final long QT_SEQ = 0x00;
	public static final long QT_COUNT = 0x04;
	public static final long QT_WORLD_ID = 0x08;
	public static final long QT_RECORDS = 0x10;
	public static final int QUEST_RECORD_BYTES = 104;
	/** The whole table: the header and every record. */
	public static final int QUEST_TABLE_BYTES = (int) QT_RECORDS + MAX_QUEST_TARGETS * QUEST_RECORD_BYTES;
	// QuestTargetRecord fields: f32 x, y, z (MC coords, the Skyrim world's terms), u32 quest, u32 ref, u16 objective, u8 flags,
	// u8 questType, char name[40], char objectiveText[40].
	public static final int QR_X = 0, QR_Y = 4, QR_Z = 8, QR_QUEST = 12, QR_REF = 16, QR_OBJECTIVE = 20, QR_FLAGS = 22, QR_QUEST_TYPE = 23,
		QR_NAME = 24, QR_OBJECTIVE_TEXT = 64;
	public static final int QR_NAME_BYTES = 40, QR_OBJECTIVE_TEXT_BYTES = 40;
	/** QuestTargetRecord objective when Skyrim found none. */
	public static final int QUEST_NO_OBJECTIVE = 0xFFFF;
	// QuestTargetFlags
	/** The quest is active in Skyrim's journal: Skyrim's compass shows it. */
	public static final int QUEST_TRACKED = 1;
	/** The target is in another Skyrim world: the marker is on the load door Skyrim's compass points to. */
	public static final int QUEST_VIA_DOOR = 1 << 1;
	/** The target itself is in an interior cell. */
	public static final int QUEST_INSIDE = 1 << 2;
	/** The main quest (its type is QUEST_TYPE_MAIN). */
	public static final int QUEST_MAIN = 1 << 3;
	/** The marker is on an actor (it moves). */
	public static final int QUEST_ACTOR = 1 << 4;
	/** QuestTargetRecord questType (Skyrim's QUEST_DATA::Type) of the main quest; 6 is miscellaneous. */
	public static final int QUEST_TYPE_MAIN = 1, QUEST_TYPE_MISC = 6;
	/**
	 * Skyrim maps (maps.SkyMaps; added within protocol 13): the map table (relative to OFF_MAP_TABLE), Skyrim -> MC, seqlock,
	 * see MapTable in the protocol header. It sits in the unused space between the actor table and the event ring, so an
	 * older plugin leaves it all zeros: nothing yet.
	 */
	public static final long OFF_MAP_TABLE = 0x16100;
	public static final long MAP_TABLE_BYTES = 0xE00;
	public static final int MAX_MAP_MARKERS = 48;
	/** Land samples along each side of an EV_MAP_SAMPLE square. */
	public static final int MAP_SAMPLE_SIDE = 20;
	/** Blocks: Skyrim lists the markers this close to its player. */
	public static final float MAP_MARKER_REACH = 1024.0F;
	public static final long MAPT_SEQ = 0x00, MAPT_WORLD_ID = 0x04, MAPT_MARKER_COUNT = 0x08, MAPT_MARKER_VERSION = 0x0C;
	public static final long MAPT_CENTER_X = 0x10, MAPT_CENTER_Z = 0x14, MAPT_MARKER_REACH = 0x18, MAPT_MARKERS = 0x20;
	public static final long MAP_MARKER_BYTES = 40;
	// MapMarkerRecord fields
	public static final long MM_X = 0, MM_Z = 4, MM_FORM_ID = 8, MM_TYPE = 12, MM_FLAGS = 14, MM_NAME = 16;
	public static final int MM_NAME_BYTES = 24;
	// MapMarkerFlags
	/** Shown on Skyrim's own map: seen, or heard of. */
	public static final int MARKER_VISIBLE = 1;
	/** Discovered: the player can fast travel there. */
	public static final int MARKER_DISCOVERED = 1 << 1;
	/** MapMarkerType kMarkerTypeCount: marker types from here on aren't places. */
	public static final int MARKER_TYPE_COUNT = 60;
	// The answer to the last EV_MAP_SAMPLE.
	public static final long MAPT_SAMPLE_REQUEST = 0x7A0, MAPT_SAMPLE_X = 0x7A4, MAPT_SAMPLE_Z = 0x7A8, MAPT_SAMPLE_SPACING = 0x7AC;
	public static final long MAPT_SAMPLE_WORLD = 0x7B0, MAPT_SAMPLE_KNOWN = 0x7B4, MAPT_SAMPLES = 0x7C0;
	/** MapSample: i16 height (MC y x 4, of the water's surface when water > 0), u8 DIG_* material, u8 water depth (blocks). */
	public static final long MAP_SAMPLE_BYTES = 4;
	/** MapSample height: not in a cell Skyrim has loaded. */
	public static final int SAMPLE_UNKNOWN = -32768;
	/**
	 * A Minecraft map in the mirror wants Skyrim's land under it: a, c = the north-west sample's x and z (sent with
	 * SkyLink.pushEventAt, so in the current world's terms), b = blocks between samples (1 to 256), flags = a request number
	 * (never 0). Skyrim answers into the map table's samples. Added within protocol 13; an older plugin ignores it.
	 */
	public static final int EV_MAP_SAMPLE = 39;
	/** skyrim_maps: Skyrim lists its map markers in the map table and answers EV_MAP_SAMPLE. Added within protocol 13. */
	public static final int FEATURE_SKYRIM_MAPS = 1 << 7;

	// Item ring (relative to OFF_ITEM_RING): Skyrim -> MC, see ItemEvent in the protocol header.
	public static final int ITEM_RING_ENTRIES = 64;
	public static final long ITR_HEAD = 0x00;
	public static final long ITR_TAIL = 0x40;
	public static final long ITR_DATA = 0x80;
	public static final long ITEM_BYTES = 48;
	// ItemSource
	public static final int SRC_WORLD = 0;
	public static final int SRC_CORPSE = 1;
	public static final int SRC_CONTAINER = 2;
	public static final int SRC_ACTOR = 3;
	/** Bought from a merchant in Skyrim's barter menu. */
	public static final int SRC_BOUGHT = 4;
	/**
	 * Worn by an actor that just died (Gear drops; container = the actor): never the player's. Skyrim sends the
	 * count negated, so a jar from before this (it skips counts under 1) can't hand it over. Added within 13.
	 */
	public static final int SRC_WORN = 5;
	/**
	 * Bits 24-27 of an item event's tags: the KIND_* of weapon or armor piece, as Skyrim types it (a weapon's type, the
	 * biped slots armor covers). 0 from an older plugin. Added within 13.
	 */
	public static final int TAG_KIND_SHIFT = 24;
	public static final int TAG_KIND_MASK = 0xF << TAG_KIND_SHIFT;
	// ItemKind
	public static final int KIND_NONE = 0;
	public static final int KIND_SWORD = 1;
	public static final int KIND_DAGGER = 2;
	/** Also pickaxes and woodcutter's axes. */
	public static final int KIND_WAR_AXE = 3;
	public static final int KIND_MACE = 4;
	public static final int KIND_GREATSWORD = 5;
	/** Battleaxes and warhammers. */
	public static final int KIND_BATTLEAXE = 6;
	public static final int KIND_BOW = 7;
	public static final int KIND_STAFF = 8;
	public static final int KIND_CROSSBOW = 9;
	public static final int KIND_HAND_TO_HAND = 10;
	public static final int KIND_HELMET = 11;
	public static final int KIND_CUIRASS = 12;
	public static final int KIND_GAUNTLETS = 13;
	public static final int KIND_BOOTS = 14;
	public static final int KIND_SHIELD = 15;
	/** Skyrim skill (ActorValue) trained by trading with a Minecraft merchant screen. */
	public static final int SKILL_SPEECH = 17;
	// Skyrim skills (ActorValue) Minecraft reports use of; weapon skills come from EV_HIT_ACTOR.
	public static final int SKILL_BLOCK = 9;
	public static final int SKILL_SMITHING = 10;
	public static final int SKILL_HEAVY_ARMOR = 11;
	public static final int SKILL_LIGHT_ARMOR = 12;
	// Skyrim skills (ActorValue) whose levels make Minecraft better (skills.SkySkills).
	public static final int SKILL_ARCHERY = 8;
	public static final int SKILL_SNEAK = 15;
	public static final int SKILL_ALCHEMY = 16;
	public static final int SKILL_ENCHANTING = 23;
	public static final int HIT_CRITICAL = 1;
	public static final int HIT_PROJECTILE = 1 << 1;
	public static final int HIT_SWEEP = 1 << 2;
	public static final int HIT_FIRE = 1 << 3;
	/**
	 * power_attacks: a fully charged melee hit (attack strength over 0.9) that was a sprint or critical hit; Skyrim makes it a
	 * power attack. Added within protocol 13 (an older plugin ignores it).
	 */
	public static final int HIT_POWER_ATTACK = 1 << 4;
	/** sneak_attacks: the player who landed the hit was crouching (Skyrim's sneak). Added within protocol 13. */
	public static final int HIT_CROUCHING = 1 << 5;
	public static final int WEAPON_UNARMED = 0;
	public static final int WEAPON_BLADE = 1;
	public static final int WEAPON_AXE = 2;
	public static final int WEAPON_BLUNT = 3;
	public static final int WEAPON_PIERCE = 4;
	public static final int WEAPON_ARROW = 5;

	// World entities (relative to OFF_WORLD_ENTITIES)
	public static final int MAX_WORLD_ENTITIES = 160;
	public static final long WE_SEQ = 0x00;
	public static final long WE_COUNT = 0x04;
	public static final long WE_HAS_SELECTION = 0x08;
	public static final long WE_SEL_MIN = 0x0C;
	public static final long WE_SEL_MAX = 0x18;
	public static final long WE_RECORDS = 0x40;
	public static final long WORLD_ENTITY_BYTES = 96;
	public static final int WE_ARROW = 1;
	public static final int WE_ITEM = 2;
	public static final int WE_TRIDENT = 3;
	public static final int WE_BLOCK = 4;
	public static final int WE_CRACK = 5;
	public static final int WE_SHADOW = 6;

	// Render ring (relative to OFF_RENDER_RING)
	public static final long RR_HEAD = 0x00;
	public static final long RR_TAIL = 0x40;
	public static final long RR_DATA = 0x80;
	public static final long RR_DATA_BYTES = RENDER_RING_BYTES - RR_DATA;
	public static final int REN_PAD = 0;
	public static final int REN_ATLAS = 1;
	public static final int REN_SECTION = 2;
	public static final int REN_CLEAR_ALL = 3;
	public static final int REN_TEXTURE = 4;
	public static final int REN_AVATAR = 5;
	public static final int REN_SCENE = 6;
	public static final int REN_ATLAS_REGION = 7;
	public static final int REN_LIGHTS = 8;
	public static final int REN_RAGDOLL = 9;
	public static final int REN_SOLIDS = 10;
	public static final int REN_DUG = 11;
	/**
	 * A section's doors and ladders (npc_doors_ladders): {sx, sy, sz, count} then count records {u8 x, y, z, flags}
	 * (PASSAGE_*). Added within protocol 13; an older plugin skips it.
	 */
	public static final int REN_PASSAGES = 14;
	/** PASSAGE_* kinds (flags bits 0-1): a door NPCs may open, an iron door, a ladder. */
	public static final int PASSAGE_DOOR = 1, PASSAGE_IRON_DOOR = 2, PASSAGE_LADDER = 3, PASSAGE_KIND_MASK = 3;
	public static final int PASSAGE_OPEN = 1 << 2;
	/** Bits 3-4: the way the door or ladder faces, Direction.get2DDataValue (0 south, 1 west, 2 north, 3 east). */
	public static final int PASSAGE_FACING_SHIFT = 3, PASSAGE_FACING_MASK = 3 << 3;
	public static final int PASSAGE_UPPER = 1 << 5;
	public static final int PART_HEAD = 1, PART_BODY = 2, PART_RIGHT_ARM = 3, PART_LEFT_ARM = 4, PART_RIGHT_LEG = 5, PART_LEFT_LEG = 6;
	public static final int LIGHT_STEADY = 0, LIGHT_FLAME = 1, LIGHT_LAVA = 2;
	public static final int REN_VERTEX_BYTES = 32;

	// Header
	public static final long H_MAGIC = 0x00;
	public static final long H_VERSION = 0x04;
	public static final long H_SKYRIM_PID = 0x08;
	public static final long H_MC_PID = 0x0C;
	public static final long H_SKYRIM_HEARTBEAT = 0x10;
	public static final long H_MC_HEARTBEAT = 0x18;

	// SkyState (relative to OFF_SKY_STATE)
	public static final long SS_SEQ = 0x00;
	public static final long SS_FLAGS = 0x04;
	public static final long SS_WORLD_ID = 0x08;
	public static final long SS_COLLISION_EPOCH = 0x0C;
	public static final long SS_POS_X = 0x10;
	public static final long SS_POS_Y = 0x18;
	public static final long SS_POS_Z = 0x20;
	public static final long SS_YAW = 0x28;
	public static final long SS_PITCH = 0x2C;
	public static final long SS_TELEPORT_SEQ = 0x30;
	public static final long SS_VIEWPORT_W = 0x34;
	public static final long SS_VIEWPORT_H = 0x38;
	public static final long SS_GAME_HOUR = 0x3C;
	public static final long SS_TOWN = 0x40;   // char[32]
	public static final long SS_PLACE = 0x60;  // PlaceFlags
	/** CarriedFlags: quest items the Skyrim player carries. Was the reserved field (0), added within 13. */
	public static final long SS_CARRIED = 0x64;
	/** SkyState.townFlags (TOWN_*), added within 13: zero from an older plugin. */
	public static final long SS_TOWN_FLAGS = 0x90;
	/** SkyState.hearthPlot: with TOWN_HEARTHFIRE_PLOT, the Hearthfire house (BGSLocation FormID) whose land the player stands on. */
	public static final long SS_HEARTH_PLOT = 0x94;
	/** The plugin reads REN_PASSAGES: ladders and open doors may be left out of REN_SOLIDS. */
	public static final int TOWN_PASSAGES = 1;
	/** The player is outdoors on a Hearthfire house's land (SS_HEARTH_PLOT names it). */
	public static final int TOWN_HEARTHFIRE_PLOT = 1 << 1;
	public static final int PLACE_INTERIOR = 1;
	public static final int PLACE_DUNGEON = 1 << 1;
	public static final int PLACE_TOWN = 1 << 2;
	/** SkyState.carried: an Elder Scroll is in the Skyrim player's inventory (opens the Time-Wound portal onto Home). */
	public static final int CARRY_ELDER_SCROLL = 1;
	/**
	 * Skills (added within 13; zeros from an older plugin): SKILL_COUNT skill levels, one byte each, in ActorValue order from
	 * SKILL_FIRST (One-Handed) to SKILL_ENCHANTING: Skyrim's current value clamped to 0-255, 0 = unknown. Refreshed about once
	 * a second, never while loading.
	 */
	public static final long SS_SKILLS = 0x68;
	/** u16: the Dragon Souls the Skyrim player holds (unspent). */
	public static final long SS_DRAGON_SOULS = 0x7A;
	/** u32: a new number each time Skyrim loads or starts a game (0: no skills written yet), so a soul absorbed can be told from another save's count. */
	public static final long SS_SKILLS_LOAD = 0x7C;
	public static final int SKILL_COUNT = 18;
	/** The ActorValue of SkyState's first skill, One-Handed. */
	public static final int SKILL_FIRST = 6;
	/**
	 * Weather sync (added within protocol 13, zeros from an older plugin): SkyState.weather is the WEATHER_* kind of Skyrim's
	 * sky where the player is (WEATHER_NONE indoors), weatherFlags its WEATHER_RAINING / SNOWING / REFUSED bits, weatherAck the
	 * last McState weatherSeq Skyrim has handled.
	 */
	public static final long SS_WEATHER = 0x80;    // u8
	public static final long SS_WEATHER_FLAGS = 0x81;  // u8
	/** SkyState.aim (u8): AIM_* bits, what Skyrim's crosshair is on. Added within 13. */
	public static final long SS_AIM = 0x82;
	public static final long SS_WEATHER_ACK = 0x84;  // u32
	// WeatherKind
	/** Not known (indoors, no sky, an older plugin): Minecraft leaves its weather as it is. */
	public static final int WEATHER_NONE = 0;
	public static final int WEATHER_CLEAR = 1;
	/** Cloudy, fog, or no classification at all. */
	public static final int WEATHER_CLOUDY = 2;
	public static final int WEATHER_RAIN = 3;
	public static final int WEATHER_SNOW = 4;
	/** Rain or snow with thunder. */
	public static final int WEATHER_STORM = 5;
	// WeatherFlags
	/** Rain is falling in Skyrim right now. */
	public static final int WEATHER_RAINING = 1;
	/** Snow is falling in Skyrim right now. */
	public static final int WEATHER_SNOWING = 1 << 1;
	/** The request weatherAck names found no weather of its kind in Skyrim's region (or a quest holds the weather, or indoors). */
	public static final int WEATHER_REFUSED = 1 << 2;
	/** SkyState.aim: Skyrim's crosshair is on a plant it can harvest (right click harvests it; a hoe doesn't till under it). */
	public static final int AIM_HARVEST = 1;
	/**
	 * Shared world events (added within protocol 13, zeros from an older plugin): u16 shoutSeq, bumped each time the
	 * player or an actor in the actor table starts a shout; u8 its words of power (1 to 3, 0 unknown); u8 SHOUT_* flags;
	 * u32 who shouted (0x14, the player).
	 */
	public static final long SS_SHOUT_SEQ = 0xA0;
	public static final long SS_SHOUT_WORDS = 0xA2;
	public static final long SS_SHOUT_FLAGS = 0xA3;
	public static final long SS_SHOUT_ACTOR = 0xA4;
	public static final int SHOUT_BY_PLAYER = 1;
	public static final int SHOUT_BY_DRAGON = 1 << 1;
	 /**
	 * SkyState.horse (Horses carry over, added within 13: 0 from an older plugin): FormID of the player's horse; horseInfo: HORSE_INFO_*
	 * flags in bits 0-7, its SpeedMult (percent) in bits 8-15, its health (percent) in bits 16-23.
	 */
	public static final long SS_HORSE = 0x98;
	public static final long SS_HORSE_INFO = 0x9C;
	public static final int HORSE_INFO_NEAR = 1;
	public static final int HORSE_INFO_AWAY = 1 << 1;
	public static final int HORSE_INFO_ARRIVED = 1 << 2;
	public static final int HORSE_INFO_CARRIED = 1 << 3;
	public static final int HORSE_INFO_SPEED_SHIFT = 8;
	public static final int HORSE_INFO_HEALTH_SHIFT = 16;

	// VendorBuys (ActorRecord.vendor): what a Skyrim merchant trades in, 0 = not a merchant.
	public static final int VENDOR_ANY = 1;
	public static final int VENDOR_WEAPON = 1 << 1;
	public static final int VENDOR_ARMOR = 1 << 2;
	public static final int VENDOR_INGOT = 1 << 3;
	public static final int VENDOR_POTION = 1 << 4;
	public static final int VENDOR_INGREDIENT = 1 << 5;
	public static final int VENDOR_FOOD = 1 << 6;
	public static final int VENDOR_ARROW = 1 << 7;
	public static final int VENDOR_JEWELRY = 1 << 8;
	public static final int VENDOR_BOOK = 1 << 9;
	public static final int VENDOR_HIDE = 1 << 10;
	public static final int VENDOR_TOOL = 1 << 11;
	public static final int VENDOR_STOLEN = 1 << 12;

	public static final int SKY_IN_GAME = 1;
	public static final int SKY_MENU_OPEN = 1 << 1;
	public static final int SKY_LOADING = 1 << 2;

	// McState (relative to OFF_MC_STATE)
	public static final long MS_SEQ = 0x00;
	public static final long MS_FLAGS = 0x04;
	public static final long MS_X = 0x08;
	public static final long MS_Y = 0x10;
	public static final long MS_Z = 0x18;
	public static final long MS_YAW = 0x20;
	public static final long MS_PITCH = 0x24;
	public static final long MS_EYE_HEIGHT = 0x28;
	public static final long MS_SENSITIVITY = 0x2C;
	public static final long MS_TELEPORT_ACK = 0x30;
	public static final long MS_GUI_SCALE = 0x34;
	public static final long MS_FRAME_COUNTER = 0x38;
	public static final long MS_FOV = 0x40;
	public static final long MS_BOB_PHASE = 0x44;
	public static final long MS_BOB_AMOUNT = 0x48;
	public static final long MS_EYE_X = 0x50;
	public static final long MS_EYE_Y = 0x58;
	public static final long MS_EYE_Z = 0x60;
	public static final long MS_TICK_QPC = 0x68;
	public static final long MS_PREV_X = 0x70;
	public static final long MS_CUR_X = 0x88;
	public static final long MS_EYE_HEIGHT_O = 0xA0;
	public static final long MS_EYE_HEIGHT_T = 0xA4;
	public static final long MS_WALK_O = 0xA8;
	public static final long MS_WALK = 0xAC;
	public static final long MS_BOB_O = 0xB0;
	public static final long MS_BOB = 0xB4;
	public static final long MS_TICK_MS = 0xB8;
	public static final long MS_CAMERA_MODE = 0xC0;
	public static final long MS_CAMERA_DISTANCE = 0xC4;
	/**
	 * The Skyrim world whose space (SkySpace) Minecraft's player stands in, 0 if none or not in the mirror, and that
	 * space's origin (Minecraft blocks). Added within protocol 13; for Skyrim's log only.
	 */
	public static final long MS_SPACE_WORLD = 0xC8;
	public static final long MS_SPACE_X = 0xCC;
	public static final long MS_SPACE_Z = 0xD0;
	/** McState.features: FEATURES_VALID with the FEATURE_* bits of the switched on features Skyrim runs itself. */
	public static final long MS_FEATURES = 0xD8;
	public static final int FEATURES_VALID = 1 << 31;
	// FeatureBits: combat and stealth, run by Skyrim (Stealth.cpp, Combat.cpp).
	public static final int FEATURE_WALLS_HIDE_YOU = 1;
	public static final int FEATURE_SNEAK_ATTACKS = 1 << 1;
	public static final int FEATURE_POWER_ATTACKS = 1 << 2;
	public static final int FEATURE_LIGHT_STEALTH = 1 << 3;
	/** quest_markers: Skyrim writes its compass's quest targets into the quest table (OFF_QUEST_TARGETS). Added within 13. */
	public static final int FEATURE_QUEST_MARKERS = 1 << 4;
	/**
	 * light_stealth: Minecraft's block light and sky light (0-15 each, bytes) at the player's eyes, and EYE_LIGHT_VALID in the
	 * flags byte when they're this tick's. Added within protocol 13 (was reserved and zero).
	 */
	public static final long MS_EYE_BLOCK_LIGHT = 0xDC;
	public static final long MS_EYE_SKY_LIGHT = 0xDD;
	public static final long MS_EYE_LIGHT_FLAGS = 0xDE;
	public static final int EYE_LIGHT_VALID = 1;
	/** weather_sync: Skyrim switches its weather when Minecraft asks (McState weatherSeq). Added within 13. */
	public static final int FEATURE_WEATHER_SYNC = 1 << 12;
	/**
	 * McState weather request (added within protocol 13): weatherSeq (u32) bumps for each weather change Minecraft asks Skyrim
	 * for, weatherWant (u8) is the WEATHER_* kind asked for, WEATHER_NONE once Skyrim answered.
	 */
	public static final long MS_WEATHER_SEQ = 0xE4;
	public static final long MS_WEATHER_WANT = 0xE8;
	/** FeatureBits kFeatSkyrimFood: Skyrim food eaten in Skyrim fills Minecraft's hunger (Skyrim sends IN_SKYRIM_FOOD). */
	public static final int FEATURE_SKYRIM_FOOD = 1 << 8;
	/** shared_world_events: Skyrim watches for shouts (SS_SHOUT_*) and shakes its camera for EV_PARTY_SHAKE. */
	public static final int FEATURE_SHARED_WORLD_EVENTS = 1 << 26;
	// FeatureBits of the towns and building features (added within 13).
	public static final int FEATURE_NPC_DOORS_LADDERS = 1 << 16;
	public static final int FEATURE_DWEMER_REDSTONE = 1 << 17;
	public static final int FEATURE_HEARTHFIRE = 1 << 18;
	/** McState.hearthPlot, hearthBlocks (added within 13): the Hearthfire plot Minecraft counts for and its blocks standing. */
	public static final long MS_HEARTH_PLOT = 0xEC;
	public static final long MS_HEARTH_BLOCKS = 0xF0;
	// Creatures: the switches whose Skyrim side Skyrim runs itself.
	public static final int FEATURE_MOBS_FIGHT_NPCS = 1 << 22;
	public static final int FEATURE_WOLVES_FIGHT_SKYRIM = 1 << 23;
	public static final int FEATURE_CREATURES_AND_LIGHT = 1 << 24;
	public static final int FEATURE_HORSES_CARRY_OVER = 1 << 25;
	/** paired_backups: Skyrim copies each save it loads and tells Minecraft (IN_PAIRED_BACKUP). Added within protocol 13. */
	public static final int FEATURE_PAIRED_BACKUPS = 1 << 28;
	/** far_block_detail: Skyrim's block renderer draws far sections without sun shadows. Added within protocol 13. */
	public static final int FEATURE_FAR_BLOCK_DETAIL = 1 << 29;

	public static final int MC_IN_WORLD = 1;
	public static final int MC_SCREEN_OPEN = 1 << 1;
	public static final int MC_ON_GROUND = 1 << 2;
	public static final int MC_SNEAKING = 1 << 3;
	public static final int MC_SPRINTING = 1 << 4;
	public static final int MC_DEAD = 1 << 5;
	public static final int MC_SWIMMING = 1 << 6;
	public static final int MC_FLYING = 1 << 7;
	/** Minecraft view: the player is in a dimension other than the Skyrim mirror (client MinecraftView). */
	public static final int MC_OTHER_DIMENSION = 1 << 8;
	/**
	 * Minecraft view event types (event ring): kept for leaving the mirror for another dimension (12) and coming back
	 * (13). Not sent: Skyrim follows MC_OTHER_DIMENSION in McState instead.
	 */
	public static final int EV_ENTER_OTHER_DIMENSION = 12;
	public static final int EV_LEAVE_OTHER_DIMENSION = 13;

	// Overlay
	public static final long OC_STATE = 0x00;
	public static final long OC_FRAMES_PUBLISHED = 0x08;
	public static final int OVERLAY_DIRTY = 1 << 2;
	public static final long SLOT_HDR_SIZE = 0x40;
	public static final long SH_WIDTH = 0x00;
	public static final long SH_HEIGHT = 0x04;
	public static final long SH_FLAGS = 0x08;
	public static final long SH_FRAME_ID = 0x10;
	// OverlaySlotHdr.flags
	public static final int OVERLAY_BOTTOM_UP = 1;
	/** Minecraft view: a full opaque frame, shown over all of Skyrim's picture. */
	public static final int OVERLAY_OPAQUE = 1 << 1;

	// Input ring (relative to OFF_INPUT_RING)
	public static final int INPUT_RING_ENTRIES = 4096;
	public static final long IR_HEAD = 0x00;
	public static final long IR_TAIL = 0x40;
	public static final long IR_DATA = 0x80;
	public static final int IN_KEY = 1;
	public static final int IN_MOUSE_BUTTON = 2;
	public static final int IN_SCROLL = 3;
	public static final int IN_CURSOR = 4;
	public static final int IN_TEXT = 5;
	public static final int IN_RELEASE_ALL = 6;
	/** Minecraft view only: a, b = raw mouse motion since the last one; Minecraft turns its player itself. */
	public static final int IN_LOOK = 9;
	/**
	 * A Skyrim arrow or bolt reached a solid Minecraft block and Skyrim ended it there: a, b, c = where (MC coords x 1024,
	 * the Skyrim world's terms), code = which way it flew ({@link #arrowFlight}). Minecraft sticks an arrow of its own there.
	 */
	public static final int IN_ARROW_BLOCKED = 10;
	/**
	 * The Skyrim player has a shrine's blessing (new, or cast again): code = BLESSING_* kind, a = game hours of it left x 1000,
	 * b = Skyrim's timescale x 1000. Added within protocol 13; Minecraft gives a fitting effect for as long (blessings.Blessings).
	 */
	public static final int IN_BLESSING = 17;
	// BlessingKind
	public static final int BLESSING_NONE = 0;
	public static final int BLESSING_AKATOSH = 1;
	public static final int BLESSING_ARKAY = 2;
	public static final int BLESSING_DIBELLA = 3;
	public static final int BLESSING_JULIANOS = 4;
	public static final int BLESSING_KYNARETH = 5;
	public static final int BLESSING_MARA = 6;
	public static final int BLESSING_STENDARR = 7;
	public static final int BLESSING_TALOS = 8;
	public static final int BLESSING_ZENITHAR = 9;
	/** Dawnguard's Auriel. */
	public static final int BLESSING_AURIEL = 10;
	/** Any other "Blessing of ..." (the DLCs' and mods' shrines). */
	public static final int BLESSING_OTHER = 11;
	/**
	 * The Skyrim player ate a food in Skyrim (food.SkyFood): code = food points to add (nutrition), a = the saturation modifier
	 * x 1000, b = the food's FormID, c = its kind (FOOD_KINDS). Sent only while FEATURE_SKYRIM_FOOD is on. Added within protocol 13.
	 */
	public static final int IN_SKYRIM_FOOD = 15;
	/**
	 * Skyrim's answer to EV_MERCHANT_GOLD: a = the merchant's FormID, b = the gold it can pay now (-1: no merchant Skyrim can
	 * trade with here), c = the request number. Added within protocol 13.
	 */
	public static final int IN_MERCHANT_GOLD = 16;
	/** FoodKind (IN_SKYRIM_FOOD's c), by value: what kind of food Skyrim's FoodValues.h took it for. */
	public static final java.util.List<String> FOOD_KINDS = java.util.List.of("food", "drink", "stew", "soup", "pie", "sweet", "bread", "cheese",
		"dairy", "cooked meat", "raw meat", "cooked fish", "raw fish", "fruit", "vegetable", "cooked vegetable");
	/**
	 * Towns (added within protocol 13): an NPC walked into a closed Minecraft door: a, b, c = the door's block (block
	 * coords, the Skyrim world's terms). Minecraft opens it unless it's iron and closes it again once the doorway is empty.
	 */
	public static final int IN_OPEN_DOOR = 19;
	/** A Skyrim lever, button or pressure plate was used: a, b, c = its block, code = ticks to power that block. */
	public static final int IN_REDSTONE_PULSE = 20;
	/** A Dwemer door near the player: a, b, c = the block at its foot, code = 1 open. Resent every 2 s; forgotten after 6 s. */
	public static final int IN_DWEMER_DOOR = 21;
	 /**
	 * Mobs and NPCs fight (added within protocol 13): a Skyrim actor's swing landed on a Minecraft creature: a = its entity id, b =
	 * Minecraft damage x 100, c = the actor's FormID, code = WEAPON_*. Minecraft hurts it as from the actor's stand-in.
	 */
	public static final int IN_MOB_HIT = 23;
	/**
	 * Paired backups: Skyrim copied the save it just loaded; Minecraft backs its world up under the same stamp. One text in
	 * a run of events: code 0 with a = request id and b = the text's length in bytes, then code 1, 2, ... with the next 12
	 * bytes each in a, b, c (little endian). The text is UTF-8 {@code "<stamp>|<save name>"} (backup.BackupRequest). Added
	 * within protocol 13.
	 */
	public static final int IN_PAIRED_BACKUP = 28;
	/** IN_PAIRED_BACKUP's longest text, in bytes. */
	public static final int PAIRED_BACKUP_TEXT_MAX = 512;
	/**
	 * Paired backups: Minecraft is done with an IN_PAIRED_BACKUP request. formId = its id, flags = 1 if the world backup was
	 * written, a = how many pairs to keep (0 all; Skyrim deletes its older copies past that), b = the backup's size in MB.
	 * Added within protocol 13; an older Skyrim plugin ignores it.
	 */
	public static final int EV_PAIRED_BACKUP = 32;

	// Magic (shouts and spells; added within protocol 13: an older Skyrim plugin leaves the table zero and ignores the events,
	// an older Minecraft never reads the table or sets the bits).
	/** Shouts: Skyrim fills the magic table's shouts and shouts what EV_SHOUT asks for. */
	public static final int FEATURE_SHOUTS = 1 << 5;
	/** Spells: Skyrim fills the magic table's spells and casts what EV_CAST_SPELL asks for. */
	public static final int FEATURE_SPELLS = 1 << 6;
	/**
	 * Shout formId (a TESShout the player knows; Skyrim equips it if another is in the voice slot) with flags = words (1 to 3),
	 * a = look yaw, b = look pitch (MC degrees), weapon = a request number. Answered with IN_SHOUT_RESULT.
	 */
	public static final int EV_SHOUT = 35;
	/**
	 * Cast the hand-cast spell formId toward a = look yaw, b = look pitch, flags = CAST_PULSE for a pulse of a concentration spell
	 * held down (c = the seconds it pays for), weapon = a request number. Answered with IN_CAST_RESULT.
	 */
	public static final int EV_CAST_SPELL = 36;
	/** EV_CAST_SPELL flags: a pulse of a concentration spell held down. */
	public static final int CAST_PULSE = 1;
	/**
	 * Skyrim's answer to EV_SHOUT: code = a SHOUT_* result in bits 0-7 and the shout's SHOUT_KIND_* in bits 8-15, a = the request
	 * number, b = SHOUT_DONE: the words shouted, c = the voice recovery in milliseconds (begun, or left).
	 */
	public static final int IN_SHOUT_RESULT = 31;
	/**
	 * Skyrim's answer to EV_CAST_SPELL: code = a CAST_* result in bits 0-7 and the spell's SPELL_KIND_* in bits 8-15, a = the
	 * request number, b = magicka x 100 (taken, or needed), c = the spell's FormID.
	 */
	public static final int IN_CAST_RESULT = 32;
	/** IN_SHOUT_RESULT / IN_CAST_RESULT code: the kind in bits 8-15. */
	public static final int MAGIC_KIND_SHIFT = 8;
	// ShoutResult
	public static final int SHOUT_DONE = 1;
	public static final int SHOUT_RECOVERING = 2;
	public static final int SHOUT_UNKNOWN = 3;
	public static final int SHOUT_CANT_NOW = 4;
	// CastResult
	public static final int CAST_DONE = 1;
	public static final int CAST_NO_MAGICKA = 2;
	public static final int CAST_UNKNOWN = 3;
	public static final int CAST_CANT_NOW = 4;
	// ShoutKind: what Minecraft does for a shout on its own side
	public static final int SHOUT_KIND_OTHER = 0;
	public static final int SHOUT_KIND_FORCE = 1;
	public static final int SHOUT_KIND_FIRE_BREATH = 2;
	public static final int SHOUT_KIND_FROST_BREATH = 3;
	public static final int SHOUT_KIND_SPRINT = 4;
	public static final int SHOUT_KIND_ETHEREAL = 5;
	public static final int SHOUT_KIND_SLOW_TIME = 6;
	public static final int SHOUT_KIND_CLEAR_SKIES = 7;
	// SpellKind: what Minecraft does for a spell on its own side
	public static final int SPELL_KIND_OTHER = 0;
	public static final int SPELL_KIND_FIRE = 1;
	public static final int SPELL_KIND_FROST = 2;
	public static final int SPELL_KIND_SHOCK = 3;
	public static final int SPELL_KIND_HEAL = 4;
	public static final int SPELL_KIND_WARD = 5;
	public static final int SPELL_KIND_SUMMON = 6;
	public static final int SPELL_KIND_ILLUSION = 7;
	public static final int SPELL_KIND_TELEKINESIS = 8;
	// MagicSystem::Delivery (a spell's delivery in the table)
	public static final int DELIVERY_SELF = 0;
	public static final int DELIVERY_TOUCH = 1;
	public static final int DELIVERY_AIMED = 2;
	public static final int DELIVERY_TARGET_ACTOR = 3;
	public static final int DELIVERY_TARGET_LOCATION = 4;
	// The schools (ActorValue) a spell's school byte names
	public static final int SCHOOL_ALTERATION = 18;
	public static final int SCHOOL_CONJURATION = 19;
	public static final int SCHOOL_DESTRUCTION = 20;
	public static final int SCHOOL_ILLUSION = 21;
	public static final int SCHOOL_RESTORATION = 22;
	/**
	 * Magic table (relative to OFF_MAGIC_TABLE): Skyrim -> MC, seqlock, refreshed about twice a second. Refresh 0: nothing
	 * written yet (an older plugin). See MagicTable in the protocol header; magic.MagicTable decodes it.
	 */
	public static final long OFF_MAGIC_TABLE = 0x11800;
	public static final int MAGIC_TABLE_BYTES = 0x7E0;
	public static final int MAX_MAGIC_SHOUTS = 20;
	public static final int MAX_MAGIC_SPELLS = 32;
	public static final int MT_SEQ = 0x00;
	public static final int MT_REFRESH = 0x04;
	public static final int MT_EQUIPPED_SHOUT = 0x08;
	public static final int MT_VOICE_RECOVERY = 0x0C;
	public static final int MT_MAGICKA = 0x10;
	public static final int MT_MAGICKA_MAX = 0x14;
	public static final int MT_SHOUT_RECOVERY_MULT = 0x18;
	public static final int MT_SHOUT_COUNT = 0x1C;
	public static final int MT_SPELL_COUNT = 0x1D;
	public static final int MT_FLAGS = 0x1E;
	public static final int MT_SHOUTS = 0x40;
	public static final int MT_SPELLS = 0x360;
	// MagicTableFlags
	public static final int MAGIC_SHOUTS = 1;
	public static final int MAGIC_SPELLS = 1 << 1;
	public static final int MAGIC_FAVOURITES = 1 << 2;
	/** MagicShout: u32 formId, u8 words, u8 kind, u8 flags, u8 pad, float recovery[3], char name[20]. */
	public static final int MAGIC_SHOUT_BYTES = 40;
	public static final int MSH_WORDS = 4, MSH_KIND = 5, MSH_FLAGS = 6, MSH_RECOVERY = 8, MSH_NAME = 20, MSH_NAME_BYTES = 20;
	/** MagicShout flags: in the player's voice slot. */
	public static final int MAGIC_SHOUT_EQUIPPED = 1;
	/** MagicSpell: u32 formId, float cost, u8 school, u8 kind, u8 flags, u8 delivery, char name[24]. */
	public static final int MAGIC_SPELL_BYTES = 36;
	public static final int MSP_COST = 4, MSP_SCHOOL = 8, MSP_KIND = 9, MSP_FLAGS = 10, MSP_DELIVERY = 11, MSP_NAME = 12, MSP_NAME_BYTES = 24;
	// MagicSpellFlags
	public static final int MAGIC_SPELL_CONCENTRATION = 1;
	public static final int MAGIC_SPELL_FAVOURITE = 1 << 1;
	public static final int MAGIC_SPELL_TWO_HANDED = 1 << 2;
	public static final int MAGIC_SPELL_HOSTILE = 1 << 3;

	/**
	 * IN_ARROW_BLOCKED's flight direction (MC axes, unit length) from its code: yaw in the high byte (256ths of a turn
	 * from +z toward +x), pitch in the low byte (0 straight down, 255 straight up).
	 */
	public static double[] arrowFlight(int code) {
		double yaw = (code >>> 8 & 0xFF) * (Math.PI * 2.0 / 256.0);
		double pitch = ((code & 0xFF) / 255.0 - 0.5) * Math.PI;
		return new double[] { Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch) };
	}

	// Collision ring (relative to OFF_COLLISION_RING)
	public static final long CR_HEAD = 0x00;
	public static final long CR_TAIL = 0x40;
	public static final long CR_DATA = 0x80;
	public static final long CR_DATA_BYTES = COLLISION_RING_BYTES - CR_DATA;
	public static final int COL_PAD = 0;
	/** Payload: u32 epoch, then (added within protocol 13) u32 worldId: the Skyrim world the epoch's regions are of. */
	public static final int COL_CLEAR = 1;
	public static final int COL_REGION = 2;
	public static final int COL_TRIS = 3;
	public static final int COL_TRI_BYTES = 40;
	public static final int TRI_STAIR_HELPER = 1;
	public static final int TRI_DIGGABLE = 2;
	public static final int TRI_GHOST = 4;
	public static final int TRI_TERRAIN = 8;
	public static final int TRI_MATERIAL_SHIFT = 8;
	// DigMaterial (skycraft_protocol.h)
	public static final int DIG_NONE = 0, DIG_GRASS = 1, DIG_DIRT = 2, DIG_STONE = 3, DIG_COBBLE = 4, DIG_SNOW = 5, DIG_ICE = 6, DIG_SAND = 7,
		DIG_GRAVEL = 8, DIG_MUD = 9, DIG_OAK_LOG = 10, DIG_SPRUCE_LOG = 11, DIG_BIRCH_LOG = 12, DIG_PLANKS = 13, DIG_METAL = 14, DIG_GLASS = 15,
		DIG_ORGANIC = 16, DIG_CLOTH = 17, DIG_BONE = 18, DIG_WEB = 19, DIG_ASH = 20, DIG_BEDROCK = 21, DIG_MATERIAL_COUNT = 22;
	public static final int COL_REGION_HEADER_BYTES = 32;
	public static final int COL_BLOCK_BYTES = 80;
}
