// SkyCraft shared-memory protocol (Skyrim SKSE plugin <-> Minecraft Fabric mod).
//
// This header is the single source of truth for the byte layout. The Java side mirrors it in
// fabric/src/main/java/dev/skycraft/link/Proto.java; if you change anything here, change it
// there too and bump kVersion.
//
// Version 13: SkyState town and place flags, merchant buy lists in ActorRecord, container id and
// chest and quest flags in ItemEvent, kEvSleep, kEvSpawnCreature, kActorSpawned, kEvEnterDungeon,
// BreachTable.
//
// All multi-byte values are little-endian. Skyrim creates the mapping; Minecraft opens it.
// Coordinates in this protocol are always Minecraft space (blocks, Y up, Z south) unless noted, in the
// terms of the current Skyrim world: its own position / 70 (Link.h SkyToMc), the same for every world.
// Inside Minecraft each Skyrim world (each worldspace, each interior cell, as SkyState::worldId names
// them) keeps its blocks in a space of its own in the mirror, so worlds that overlap in Skyrim (every
// interior sits near 0,0) never share blocks. Minecraft owns that table (skycraft-spaces.txt in its
// world, beside the blocks) and moves every coordinate into and out of the world's space itself (Java
// SkySpace/SkyLink); Skyrim never sees a space. McState::spaceWorld says which one Minecraft's player
// is in, for the log.
#pragma once

#include <cstddef>
#include <cstdint>

namespace skycraft::proto
{
	inline constexpr std::uint32_t kMagic = 0x43594B53;  // "SKYC"
	inline constexpr std::uint32_t kVersion = 13;
	inline constexpr wchar_t       kMappingName[] = L"Local\\SkyCraft_v1";

	// 1 Minecraft block == 70 Skyrim units (Skyrim player ~128 units tall, MC player 1.8 blocks).
	inline constexpr double kUnitsPerBlock = 70.0;

	// ---- region offsets ---------------------------------------------------------------------
	inline constexpr std::uint64_t kOffHeader = 0x0;
	inline constexpr std::uint64_t kOffSkyState = 0x100;
	inline constexpr std::uint64_t kOffMcState = 0x200;
	inline constexpr std::uint64_t kOffOverlayCtl = 0x300;
	inline constexpr std::uint64_t kOffOverlaySlotHdr = 0x340;  // 3 x 0x40
	inline constexpr std::uint64_t kOffWaterGrid = 0x400;       // Skyrim -> MC, see WaterGrid
	inline constexpr std::uint64_t kOffInputRing = 0x1000;
	inline constexpr std::uint64_t kOffCollisionRing = 0x20000;
	inline constexpr std::uint64_t kCollisionRingBytes = 32ull << 20;
	inline constexpr std::uint64_t kOffOverlayPixels = kOffCollisionRing + kCollisionRingBytes;
	inline constexpr std::uint32_t kMaxOverlayW = 3840;
	inline constexpr std::uint32_t kMaxOverlayH = 2160;
	inline constexpr std::uint64_t kOverlaySlotBytes = std::uint64_t(kMaxOverlayW) * kMaxOverlayH * 4;
	inline constexpr std::uint32_t kOverlaySlots = 3;
	inline constexpr std::uint64_t kOffActorTable = 0x12000;   // Skyrim -> MC, see ActorTable
	inline constexpr std::uint64_t kOffEventRing = 0x17000;    // MC -> Skyrim, see McEvent
	inline constexpr std::uint64_t kOffItemRing = 0x1B100;     // Skyrim -> MC, see ItemEvent
	inline constexpr std::uint64_t kOffWorldEntities = 0x1C000;  // MC -> Skyrim, see WorldEntities
	inline constexpr std::uint64_t kOffBreachTable = 0x1FC40;    // Skyrim -> MC, see BreachTable
	inline constexpr std::uint64_t kOffRenderRing = kOffOverlayPixels + kOverlaySlotBytes * kOverlaySlots;
	inline constexpr std::uint64_t kRenderRingBytes = 64ull << 20;
	inline constexpr std::uint64_t kMappingBytes = kOffRenderRing + kRenderRingBytes;

	// ---- header @0x0 ------------------------------------------------------------------------
	struct Header
	{
		std::uint32_t magic;
		std::uint32_t version;
		std::uint32_t skyrimPid;
		std::uint32_t mcPid;
		std::uint64_t skyrimHeartbeatMs;  // GetTickCount64() at last Skyrim frame
		std::uint64_t mcHeartbeatMs;      // GetTickCount64() at last MC frame
	};
	static_assert(sizeof(Header) == 0x20);

	// ---- Skyrim -> MC state @0x100 (seqlock: seq odd while writing) -------------------------
	enum SkyFlags : std::uint32_t
	{
		kSkyInGame = 1u << 0,    // a save is loaded and the player exists
		kSkyMenuOpen = 1u << 1,  // a Skyrim menu owns input; MC should drop held keys
		kSkyLoading = 1u << 2,   // loading screen / cell transition in progress
	};

	// Skyrim's water (lakes, rivers, the sea) around the player, for Minecraft to treat as its own
	// water: swimming, floating, drowning. Seqlock like SkyState.
	inline constexpr std::uint32_t kWaterGridSize = 16;
	inline constexpr float         kNoWater = -1.0e30f;

	struct WaterGrid
	{
		std::uint32_t seq;
		std::int32_t  originX, originZ;  // Minecraft block column of surface[0]
		std::uint32_t worldId;           // as in SkyState
		float         surface[kWaterGridSize * kWaterGridSize];  // [z * size + x]: MC y of the water surface; kNoWater: none
	};
	static_assert(sizeof(WaterGrid) <= 0xC00);

	// SkyState::skills: how many, and the ActorValue of the first (kOneHanded). Proto.SKILL_COUNT, SKILL_FIRST.
	inline constexpr std::uint32_t kSkillCount = 18;
	inline constexpr std::uint32_t kSkillFirst = 6;

	struct SkyState
	{
		std::uint32_t seq;
		std::uint32_t flags;           // SkyFlags
		std::uint32_t worldId;         // worldspace FormID (exterior) or cell FormID (interior)
		std::uint32_t collisionEpoch;  // bumps on world change; MC drops all collision data
		double        posX, posY, posZ;  // Skyrim player feet, MC coords
		float         yaw, pitch;        // authoritative look (MC degrees)
		std::uint32_t teleportSeq;       // MC teleports its player to pos when this changes
		std::uint32_t viewportW, viewportH;
		float         gameHour;
		char          town[32];  // the city/town/settlement the player is in (or their location's name), UTF-8, NUL-terminated
		std::uint32_t place;     // PlaceFlags
		std::uint32_t carried;   // CarriedFlags. Was the reserved field (always 0), so an older plugin reads as "carries nothing"

		// Added within version 13, zeros from an older plugin.
		// Skills (Skyrim's skill levels making Minecraft better), refreshed about once a second and at once after a game
		// loads, never while loading. skills[i] is the player's level in ActorValue kSkillFirst + i (One-Handed 6 to
		// Enchanting 23, the order RE::ActorValue has them): Skyrim's current value, rounded and clamped to 0-255. 0 means
		// unknown (an older plugin, or no game loaded yet), and Minecraft gives no bonus for it.
		std::uint8_t  skills[kSkillCount];  // 0x68
		std::uint16_t dragonSouls;          // 0x7A: the Dragon Souls the player holds (ActorValue kDragonSouls, unspent), up to 65535
		std::uint32_t skillsLoad;           // 0x7C: a new number each time a game is loaded or started (never 0 once skills are
		                                    // written; it starts somewhere new each Skyrim run), so Minecraft tells a soul absorbed
		                                    // (dragonSouls up, same skillsLoad) from another save's count and pays only for the first
		// weather and the world (added within version 13, zeros from an older plugin: no weather known, nothing aimed at)
		std::uint8_t  weather;       // 0x80: WeatherKind of Skyrim's sky where the player is (kWeatherNone indoors)
		std::uint8_t  weatherFlags;  // 0x81: WeatherFlags
		std::uint8_t  aim;           // 0x82: AimFlags, what Skyrim's crosshair is on
		std::uint8_t  worldPad;      // 0x83
		std::uint32_t weatherAck;    // 0x84: the last McState::weatherSeq Skyrim has handled (switched its weather, or refused)
		// Set aside for the features that fill them in:
		// stealth and detection
		std::uint8_t reservedStealth[8];  // 0x88
		// towns (added within version 13, zeros from an older plugin)
		std::uint32_t townFlags;   // 0x90: TownFlags
		std::uint32_t hearthPlot;  // 0x94: with kTownHearthfirePlot, the Hearthfire house (its BGSLocation FormID) whose land the
		                           // player stands on outdoors, else 0
		// creatures
		std::uint8_t reservedCreatures[8];  // 0x98
		// multiplayer: shared world events (the shared_world_events switch, kFeatSharedWorldEvents). Skyrim bumps
		// shoutSeq each time the player, or an actor in the actor table, starts a shout (its voice recovery
		// begins), with who it was; Minecraft tells the other players in the world. Added within version 13: zeros
		// from an older plugin, and while the switch is off (the sequence never moves, so nothing is told).
		std::uint16_t shoutSeq;    // 0xA0
		std::uint8_t  shoutWords;  // 0xA2: words of power in the shout (1 to 3), 0 when Skyrim can't tell
		std::uint8_t  shoutFlags;  // 0xA3: ShoutFlags
		std::uint32_t shoutActor;  // 0xA4: who shouted: the actor's FormID (0x14 for the player)
	};
	static_assert(sizeof(SkyState) == 0xA8);
	static_assert(offsetof(SkyState, shoutSeq) == 0xA0 && offsetof(SkyState, shoutWords) == 0xA2 && offsetof(SkyState, shoutFlags) == 0xA3 &&
	              offsetof(SkyState, shoutActor) == 0xA4);  // Proto.SS_SHOUT_*
	static_assert(offsetof(SkyState, gameHour) == 0x3C && offsetof(SkyState, town) == 0x40 && offsetof(SkyState, place) == 0x60 &&
	              offsetof(SkyState, carried) == 0x64);  // Proto.SS_*
	static_assert(offsetof(SkyState, skills) == 0x68 && offsetof(SkyState, dragonSouls) == 0x7A && offsetof(SkyState, skillsLoad) == 0x7C &&
	              offsetof(SkyState, weather) == 0x80);  // Proto.SS_SKILLS, SS_DRAGON_SOULS, SS_SKILLS_LOAD

	static_assert(offsetof(SkyState, weather) == 0x80 && offsetof(SkyState, weatherFlags) == 0x81 && offsetof(SkyState, aim) == 0x82 &&
	              offsetof(SkyState, weatherAck) == 0x84 && offsetof(SkyState, reservedStealth) == 0x88);  // Proto.SS_WEATHER*, SS_AIM

	// Skyrim's weather as both games share it (SkyState::weather, McState::weatherWant; Weather sync), from the weather's
	// classification flags (TESWeather DATA: pleasant, cloudy, rainy, snow) and its sounds. Added within version 13.
	enum WeatherKind : std::uint8_t
	{
		kWeatherNone = 0,    // not known: indoors, no sky, an older plugin. Minecraft leaves its weather as it is
		kWeatherClear = 1,   // pleasant
		kWeatherCloudy = 2,  // cloudy, fog, or no classification at all
		kWeatherRain = 3,    // rainy
		kWeatherSnow = 4,    // snow
		kWeatherStorm = 5,   // rainy or snow with a thunder sound: a thunderstorm
	};

	// SkyState::weatherFlags. Added within version 13.
	enum WeatherFlags : std::uint8_t
	{
		kWeatherRaining = 1u << 0,  // rain is falling right now (Sky::IsRaining: past the fade in, or the last weather's still fading out)
		kWeatherSnowing = 1u << 1,  // snow is falling right now (Sky::IsSnowing)
		kWeatherRefused = 1u << 2,  // the request weatherAck names found no weather of that kind in the region (or a quest holds
		                            // the weather, or the player is indoors): Skyrim's weather stayed
	};

	// SkyState::aim. Added within version 13.
	enum AimFlags : std::uint8_t
	{
		kAimHarvest = 1u << 0,  // Skyrim's crosshair is on a plant it can harvest (flora, or a tree with produce, not yet picked):
		                        // a right click harvests it (click to activate), and a hoe doesn't till the ground under it
	};

	static_assert(offsetof(SkyState, townFlags) == 0x90 && offsetof(SkyState, hearthPlot) == 0x94);  // Proto.SS_TOWN_FLAGS, SS_HEARTH_PLOT

	// What the plugin tells Minecraft about towns (SkyState::townFlags). Added within version 13: zero from an older plugin.
	enum TownFlags : std::uint32_t
	{
		kTownPassages = 1u << 0,        // this plugin reads kRenPassages (NPCs open Minecraft doors and climb its ladders), so
		                                // Minecraft may leave ladders and open doors out of kRenSolids. Without it they stay solid
		kTownHearthfirePlot = 1u << 1,  // the player is outdoors on a Hearthfire house's land (Lakeview Manor, Windstad Manor,
		                                // Heljarchen Hall: a location HearthFires.esm defines with LocTypePlayerHouse or
		                                // LocTypeHouse); hearthPlot names it
	};

	// What kind of place the player is in (SkyState::place).
	enum PlaceFlags : std::uint32_t
	{
		kPlaceInterior = 1u << 0,  // an interior cell
		kPlaceDungeon = 1u << 1,   // a cave, crypt, ruin, mine, camp, lair, ... (LocTypeDungeon and kin)
		kPlaceTown = 1u << 2,      // inside a city, town or settlement
	};

	// Quest items in the Skyrim player's inventory that Minecraft asks about (SkyState::carried; they never
	// convert, so Minecraft can't see them itself). Added within version 13: the field was reserved and zero.
	enum CarriedFlags : std::uint32_t
	{
		kCarryElderScroll = 1u << 0,  // an Elder Scroll (Skyrim.esm DA04ElderScroll 0x02D513 and its furled copy
		                              // 0x048782; Dawnguard.esm DLC1ElderScrollDragon 0x0126DC, Sun 0x011A13,
		                              // Blood 0x0118F9): opens the portal at the Time-Wound onto Home
	};
	// Who SkyState::shoutActor is (SkyState::shoutFlags).
	enum ShoutFlags : std::uint8_t
	{
		kShoutByPlayer = 1u << 0,  // the Skyrim player shouted (Skyrim had the player: riding, a scene, ...)
		kShoutByDragon = 1u << 1,  // a dragon shouted (kActorDragon: its breath is a shout too)
	};
	static_assert(kOffSkyState + sizeof(SkyState) <= kOffMcState);

	// ---- MC -> Skyrim state @0x200 (seqlock) ------------------------------------------------
	enum McFlags : std::uint32_t
	{
		kMcInWorld = 1u << 0,
		kMcScreenOpen = 1u << 1,  // an MC GUI screen (inventory, chat, ...) is open
		kMcOnGround = 1u << 2,
		kMcSneaking = 1u << 3,
		kMcSprinting = 1u << 4,
		kMcDead = 1u << 5,
		kMcSwimming = 1u << 6,
		kMcFlying = 1u << 7,

		// Minecraft view (added within version 13; an older plugin ignores the bit): the player is in a
		// dimension other than the Skyrim mirror (the Nether, Tamriel Wilds, Home, ...). Minecraft draws
		// its own world and publishes full opaque frames (kOverlayOpaque); its x/y/z are in that
		// dimension. Skyrim shows the frame full screen, stops driving its player and camera from
		// Minecraft, parks its player, and on the falling edge takes Minecraft's position (back in the
		// mirror) as where its player is now. Minecraft takes raw mouse motion (kInLook) meanwhile.
		kMcOtherDimension = 1u << 8,
	};

	// Event ring types (McEventType, below) kept for the Minecraft view: the player left the Skyrim mirror
	// for another dimension (12) and came back (13). Not sent: Skyrim follows McState's kMcOtherDimension
	// instead, a state that a dropped event or a reconnect can't leave stale.
	enum McViewEventType : std::uint32_t
	{
		kEvEnterOtherDimension = 12,
		kEvLeaveOtherDimension = 13,
	};

	struct McState
	{
		std::uint32_t seq;
		std::uint32_t flags;          // McFlags
		double        x, y, z;        // interpolated feet position (MC coords)
		float         yaw, pitch;     // MC rotation (degrees)
		float         eyeHeight;      // blocks above feet
		float         sensitivity;    // MC mouse sensitivity option (0..1)
		std::uint32_t teleportAck;    // last SkyState::teleportSeq applied
		std::uint32_t guiScale;
		std::uint64_t frameCounter;
		float         fovDeg;         // effective vertical FOV (includes sprint / fluid modifiers)
		float         bobPhase;       // MC walk-bob phase (interpolated walk distance); 0 if bobbing is off
		float         bobAmount;      // MC walk-bob amplitude
		std::uint32_t pad4C;
		double        eyeX, eyeY, eyeZ;  // MC camera position (interpolated, includes sneak eye lerp)

		// Raw 20 Hz physics ticks, so Skyrim can interpolate on its own frame clock exactly like
		// Minecraft's renderer does with partial ticks (no judder from the two games' frame phase).
		std::int64_t tickQpc;             // QueryPerformanceCounter at the (remainder-corrected) tick
		double       prevX, prevY, prevZ;  // feet at the previous tick
		double       curX, curY, curZ;     // feet at the latest tick
		float        tickEyeO, tickEye;      // Camera's smoothed eye height, previous/latest tick
		float        walkDistO, walkDist;    // walk-bob phase inputs
		float        bobO, bob;              // walk-bob amplitude inputs
		float        tickMs;                 // milliseconds per tick (50 unless /tick rate changed)
		std::uint32_t tickPad;

		// Minecraft's camera (F5): 0 first person, 1 third person behind, 2 third person in front
		// (looking back at the player). cameraDistance is how far Minecraft's camera sits from
		// the eye, after its own zoom collision (Minecraft blocks and Skyrim's triangles).
		std::uint32_t cameraMode;
		float         cameraDistance;

		// Where Minecraft keeps the blocks of the Skyrim world its player stands in (added within version 13:
		// zeros from an older Minecraft). spaceWorld is that world as SkyState::worldId names it (0: none
		// known, or the player is in another dimension), spaceX/spaceZ its space's origin in Minecraft blocks.
		// Every position in this protocol is already in the world's own terms; these are for the log only.
		std::uint32_t spaceWorld;
		std::int32_t  spaceX, spaceZ;
		std::uint32_t spacePad;

		// Added within version 13, zeros from an older Minecraft.
		std::uint32_t features;  // 0xD8: FeatureBits, the skycraft-features.txt switches Skyrim's side needs
		// Light and stealth (light_stealth): Minecraft's light at its player's eyes, read once a tick in the mirror.
		// Skyrim folds it into its own light for its detection (Stealth.h PlayerLight): Minecraft's blocks shade the
		// player from Skyrim's sun, sky and lamps as far as they shut out Minecraft's sky, and Minecraft's lights
		// (torches, lava, glowstone) light the player. Zeros from an older Minecraft: no kEyeLightValid, and Skyrim
		// keeps its own light.
		std::uint8_t eyeBlockLight;  // 0xDC: block light there, 0-15
		std::uint8_t eyeSkyLight;    // 0xDD: sky light there, 0-15, as Minecraft's blocks let it in (15 under open sky, day
		                             // or night: Skyrim's own light carries the hour and the weather; less under a roof; 0
		                             // sealed in, as in a tunnel dug into the ground)
		std::uint8_t eyeLightFlags;  // 0xDE: EyeLightFlags
		std::uint8_t stealthPad[5];  // 0xDF
		// weather (added within version 13, zeros from an older Minecraft: it never asks)
		std::uint32_t weatherSeq;      // 0xE4: bumps for each weather change Minecraft asks Skyrim for (a player's /weather, a mod)
		std::uint8_t  weatherWant;     // 0xE8: the WeatherKind asked for; kWeatherNone once Skyrim answered (SkyState::weatherAck)
		std::uint8_t  weatherPad[3];   // 0xE9
		// building (added within version 13, zeros from an older Minecraft)
		std::uint32_t hearthPlot;    // 0xEC: the Hearthfire plot Minecraft counts blocks for (SkyState::hearthPlot as it last
		                             // saw it), 0 if none
		std::uint32_t hearthBlocks;  // 0xF0: Minecraft blocks standing on that plot
		// Set aside for features to come.
		std::uint8_t reserved[12];  // 0xF4
	};
	static_assert(sizeof(McState) == 0x100);
	static_assert(offsetof(McState, features) == 0xD8);  // Proto.MS_FEATURES
	static_assert(offsetof(McState, eyeBlockLight) == 0xDC && offsetof(McState, eyeSkyLight) == 0xDD && offsetof(McState, eyeLightFlags) == 0xDE &&
	              offsetof(McState, weatherSeq) == 0xE4);  // Proto.MS_EYE_*

	// McState::eyeLightFlags (light_stealth).
	enum EyeLightFlags : std::uint8_t
	{
		kEyeLightValid = 1u << 0,  // eyeBlockLight and eyeSkyLight are this tick's: the player is in the mirror, its level is
		                           // loaded and light_stealth is on. Clear: Skyrim keeps its own light
	};
	static_assert(offsetof(McState, weatherSeq) == 0xE4 && offsetof(McState, weatherWant) == 0xE8 && offsetof(McState, hearthPlot) == 0xEC);  // Proto.MS_WEATHER_*
	static_assert(offsetof(McState, hearthPlot) == 0xEC && offsetof(McState, hearthBlocks) == 0xF0);  // Proto.MS_HEARTH_*

	// McState::features. Minecraft sets kFeaturesValid with the bits of every switched on feature whose Skyrim
	// side Skyrim runs itself; an older Minecraft leaves it all 0, and those features then stay off.
	enum FeatureBits : std::uint32_t
	{
		// Combat and stealth (Stealth.cpp, Combat.cpp).
		kFeatWallsHideYou = 1u << 0,  // walls_hide_you: an NPC whose sight of the player (head and chest) is cut by solid
		                              // Minecraft blocks can't see them (Skyrim's line of sight, Actor::CalculateLOS)
		kFeatSneakAttacks = 1u << 1,  // sneak_attacks: a hit with kHitCrouching on an actor that hasn't detected the player is a
		                              // Skyrim sneak attack (multiplier with its perks, message, Sneak experience)
		kFeatPowerAttacks = 1u << 2,  // power_attacks: a hit with kHitPowerAttack is a Skyrim power attack (HitData kPowerAttack
		                              // and the weapon's stagger); without it sprint hits and knockback count, as before
		kFeatLightStealth = 1u << 3,  // light_stealth: Skyrim's light on the player for detection takes in McState's eye light
		// world
		kFeatWeatherSync = 1u << 12,  // weather_sync: Skyrim switches its weather when Minecraft asks (McState::weatherSeq)
		// Economy (added within version 13): Skyrim food eaten in Skyrim fills Minecraft's hunger. Skyrim
		// sends kInSkyrimFood only while this is set.
		kFeatSkyrimFood = 1u << 8,

		// shared_world_events (added within version 13): Skyrim watches for shouts (SkyState::shoutSeq) and
		// shakes its camera for kEvPartyShake. Off, it does neither.
		kFeatSharedWorldEvents = 1u << 26,
		kFeaturesValid = 1u << 31,

		// Towns and building (added within version 13):
		kFeatNpcDoorsLadders = 1u << 16,  // npc_doors_ladders: an NPC walking into a closed Minecraft door asks Minecraft to
		                                  // open it (kInOpenDoor) and paths through doors; an NPC standing in a ladder
		                                  // column whose path goes up is lifted up it
		kFeatDwemerRedstone = 1u << 17,   // dwemer_redstone: levers, buttons and pressure plates send kInRedstonePulse,
		                                  // nearby Dwemer doors go to Minecraft (kInDwemerDoor), kEvRedstoneDoor works them
		kFeatHearthfire = 1u << 18,       // hearthfire_blocks: Skyrim reports the Hearthfire plot the player stands on
		                                  // (SkyState::hearthPlot). Bits 19 to 21 are set aside for towns
	};
	static_assert(offsetof(McState, spaceWorld) == 0xC8 && offsetof(McState, spaceX) == 0xCC && offsetof(McState, spaceZ) == 0xD0);  // Proto.MS_SPACE_*
	static_assert(sizeof(McState) <= 0x100);

	// ---- overlay triple buffer @0x300 --------------------------------------------------------
	// state: bits 0-1 = index of the "middle" slot, bit 2 = middle holds an unread frame.
	// Writer (MC) renders into its private back slot, then xchg(state, back | kDirty) and keeps
	// the returned index as its new back slot. Reader (Skyrim) does xchg(state, front) only when
	// the dirty bit is set and keeps the returned index as its new front slot.
	inline constexpr std::uint32_t kOverlayDirty = 1u << 2;

	struct OverlayCtl
	{
		std::uint32_t state;
		std::uint32_t pad;
		std::uint64_t framesPublished;
	};

	enum OverlayFlags : std::uint32_t
	{
		kOverlayBottomUp = 1u << 0,  // rows are bottom-up
		kOverlayOpaque = 1u << 1,    // Minecraft view: a full opaque frame (its world, hand and GUI), shown over all
		                             // of Skyrim's picture with its alpha ignored, not blended over it
	};

	struct OverlaySlotHdr
	{
		std::uint32_t width;
		std::uint32_t height;
		std::uint32_t flags;  // OverlayFlags
		std::uint32_t pad;
		std::uint64_t frameId;
		std::uint8_t  reserved[0x40 - 0x18];
	};
	static_assert(sizeof(OverlaySlotHdr) == 0x40);

	// ---- input ring @0x1000 (Skyrim produces, MC consumes) ----------------------------------
	inline constexpr std::uint32_t kInputRingEntries = 4096;  // power of two
	inline constexpr std::uint64_t kInputRingHeadOff = 0x00;  // u64, written by Skyrim
	inline constexpr std::uint64_t kInputRingTailOff = 0x40;  // u64, written by MC
	inline constexpr std::uint64_t kInputRingDataOff = 0x80;

	enum InputType : std::uint16_t
	{
		kInKey = 1,          // code = SDL scancode, a = 1 press / 0 release
		kInMouseButton = 2,  // code = SDL button (1 L, 2 M, 3 R, 4 X1, 5 X2), a = 1 press / 0 release
		kInScroll = 3,       // a = wheel notches * 120 (positive = up)
		kInCursor = 4,       // a, b = absolute cursor position in overlay pixels
		kInText = 5,         // a = unicode code point
		kInReleaseAll = 6,   // release every held key/button (input focus left MC)
		kInHurt = 7,         // Skyrim hit the player: code = HurtKind, a = Skyrim damage * 100, b = attacker FormID, c = HurtFlags
		kInOpenMenu = 8,     // open Minecraft's pause/options menu
		kInLook = 9,         // Minecraft view only (kMcOtherDimension): a, b = raw mouse motion since the last one (x right,
		                     // y down); Minecraft turns its player itself. Added within version 13
		kInArrowBlocked = 10,  // a Skyrim arrow or bolt reached a solid Minecraft block, and Skyrim ended it there: a, b, c =
		                       // where it reached the block (MC coords x 1024), code = which way it flew: yaw in the high
		                       // byte (256ths of a turn from +z toward +x), pitch in the low byte (0 straight down, 255
		                       // straight up). Minecraft sticks an arrow of its own there. Added within version 13
		kInSkyrimFood = 15,    // the Skyrim player ate a food in Skyrim (FoodValues.h): code = Minecraft food points to add
		                       // (nutrition, 0-20), a = Minecraft's saturation modifier x 1000 (saturation gained is
		                       // nutrition x modifier x 2), b = the food's FormID, c = FoodKind. Minecraft fills its player's
		                       // hunger. Sent only while McState::features has kFeatSkyrimFood. Added within version 13
		kInMerchantGold = 16,  // Skyrim's answer to kEvMerchantGold: a = the merchant's FormID, b = the Skyrim gold it can pay
		                       // now (its merchant chest's, else its own purse; -1: no merchant Skyrim can trade with here:
		                       // gone, dead, unloaded or too far), c = the request number. Added within version 13
		kInBlessing = 17,      // the player has a shrine's blessing (a spell named "Blessing of ..." among their active effects,
		                       // new or cast again): code = BlessingKind, a = game hours of it left x 1000, b = Skyrim's
		                       // timescale x 1000. Minecraft gives a fitting effect for as long. Added within version 13
		// Towns and building (added within version 13; an older Minecraft ignores them). Block coordinates are in the
		// current Skyrim world's terms, like every position on the link.
		kInOpenDoor = 19,       // an NPC walked into a closed Minecraft door (kRenPassages): a, b, c = the door's block (MC
		                        // block coords). Minecraft opens it if NPCs may (any door but iron) and closes it again once
		                        // nobody has been in the doorway for a few seconds. At most once a second per door
		kInRedstonePulse = 20,  // a Skyrim lever, button or pressure plate was used (TESActivateEvent): a, b, c = the block its
		                        // origin is in (MC block coords), code = how many ticks Minecraft powers that block
		kInDwemerDoor = 21,     // a Dwemer door (not a load door) near the player: a, b, c = the block at its foot (MC block
		                        // coords), code = 1 open, 0 closed. Resent every 2 s for each one within 24 blocks; Minecraft
		                        // forgets one not resent for 6 s and watches redstone power at that block and the one above
		// 22: set aside for towns
	};

	// Which shrine's blessing kInBlessing is, by the god the spell names. Added within version 13.
	enum BlessingKind : std::uint16_t
	{
		kBlessingNone = 0,
		kBlessingAkatosh = 1,
		kBlessingArkay = 2,
		kBlessingDibella = 3,
		kBlessingJulianos = 4,
		kBlessingKynareth = 5,
		kBlessingMara = 6,
		kBlessingStendarr = 7,
		kBlessingTalos = 8,
		kBlessingZenithar = 9,
		kBlessingAuriel = 10,  // Dawnguard
		kBlessingOther = 11,   // any other "Blessing of ..." (the DLCs' and mods' shrines)
	};

	// What kind of food kInSkyrimFood's food was (its c), from its name and VendorItemFoodRaw keyword (FoodValues.h).
	enum FoodKind : std::uint32_t
	{
		kFoodOther = 0,
		kFoodDrink = 1,           // ale, mead, wine, brandy, milk
		kFoodStew = 2,            // stews
		kFoodSoup = 3,            // soups, chowder, fondue
		kFoodPie = 4,             // pies and crostatas
		kFoodSweet = 5,           // sweet rolls, treats, cakes
		kFoodBread = 6,           // breads and dumplings
		kFoodCheese = 7,          // cheese wheels, wedges and slices
		kFoodDairy = 8,           // butter, honey
		kFoodCookedMeat = 9,
		kFoodRawMeat = 10,
		kFoodCookedFish = 11,     // fish and seafood
		kFoodRawFish = 12,
		kFoodFruit = 13,
		kFoodVegetable = 14,
		kFoodCookedVegetable = 15,
	};

	enum HurtKind : std::uint16_t
	{
		kHurtMelee = 0,
		kHurtProjectile = 1,
		kHurtMagic = 2,
		kHurtOther = 3,
	};

	enum HurtFlags : std::uint32_t
	{
		kHurtBlockedInSkyrim = 1u << 0,
		kHurtPowerAttack = 1u << 1,
	};

	// ---- actor table @0x12000 (Skyrim -> MC, seqlock) ----------------------------------------
	// Nearby Skyrim actors, mirrored in Minecraft as invisible hittable proxy entities.
	inline constexpr std::uint32_t kMaxActors = 256;

	enum ActorFlags : std::uint32_t
	{
		kActorHostile = 1u << 0,    // hostile to the player right now
		kActorDead = 1u << 1,
		kActorEssential = 1u << 2,
		kActorInCombat = 1u << 3,
		kActorSpawned = 1u << 4,    // a creature kEvSpawnCreature brought (Minecraft counts the live ones)

		// Companions (added within version 13: an older plugin never sets these bits, an older Minecraft ignores them).
		kActorCompanion = 1u << 5,         // the player's animal: one Minecraft tamed (kEvCompanion), Skyrim's own animal
		                                   // follower (the animal slot of the DialogueFollower quest: Meeko, Vigilance, a stray
		                                   // or bought dog, a husky, ...), another dog, wolf or horse that is the player's
		                                   // teammate, or a horse the player owns
		kActorCompanionWaiting = 1u << 6,  // a companion that isn't following the player now: told to wait (Minecraft's sit),
		                                   // or left behind because another animal holds Skyrim's one animal follower slot

		// Towns (added within version 13: an older plugin never sets it, an older Minecraft ignores it).
		kActorGuard = 1u << 16,  // a guard (Actor::IsGuard): Minecraft sends kEvTownBuild only with one near. Bit 17 is set aside
		                         // for towns
		// Shared world events (added within version 13: an older plugin never sets it, an older Minecraft ignores it).
		kActorDragon = 1u << 18,  // a dragon: its race has the ActorTypeDragon keyword (every dragon, Alduin, Durnehviir,
		                          // skeletal and DLC dragons). A hostile one in combat near the player is a dragon attack
	};

	// Bits 8-11 of ActorRecord::flags: what kind of animal the actor is, from its race (AnimalKind; Skyrim's
	// AnimalKinds.h decides). Added within version 13 (zero from an older plugin: no animal).
	inline constexpr std::uint32_t kActorKindShift = 8;
	inline constexpr std::uint32_t kActorKindMask = 0xFu << kActorKindShift;

	enum AnimalKind : std::uint32_t
	{
		kAnimalNone = 0,   // not one Minecraft's taming rules know (people, trolls, foxes, summoned creatures, ...)
		kAnimalDog = 1,    // tamed like a Minecraft wolf: dogs, huskies, death hounds
		kAnimalWolf = 2,   // tamed like a Minecraft wolf: wolves, ice wolves (not werewolves)
		kAnimalHorse = 3,  // tamed like a Minecraft horse
	};

	struct ActorRecord
	{
		std::uint32_t formId;
		std::uint32_t flags;       // ActorFlags
		float         x, y, z;     // feet, MC coords
		float         yaw;         // MC degrees
		float         width;       // blocks
		float         height;      // blocks
		float         healthFrac;  // 0..1
		std::uint16_t level;
		std::uint16_t vendor;      // VendorBuys: what this merchant trades in (0 = not a merchant)
		char          name[24];    // display name, UTF-8, NUL-terminated (truncated)
	};
	static_assert(sizeof(ActorRecord) == 64);
	static_assert(offsetof(ActorRecord, level) == 36 && offsetof(ActorRecord, vendor) == 38 && offsetof(ActorRecord, name) == 40);  // SkyLink.readActors

	// What a merchant trades in, from their vendor faction's buy/sell keyword list.
	enum VendorBuys : std::uint16_t
	{
		kVendorAny = 1u << 0,         // buys everything (no list, or a "buys all but" list)
		kVendorWeapon = 1u << 1,      // VendorItemWeapon
		kVendorArmor = 1u << 2,       // VendorItemArmor, VendorItemClothing
		kVendorIngot = 1u << 3,       // VendorItemOreIngot
		kVendorPotion = 1u << 4,      // VendorItemPotion, VendorItemPoison
		kVendorIngredient = 1u << 5,  // VendorItemIngredient
		kVendorFood = 1u << 6,        // VendorItemFood, VendorItemFoodRaw
		kVendorArrow = 1u << 7,       // VendorItemArrow
		kVendorJewelry = 1u << 8,     // VendorItemJewelry, VendorItemGem
		kVendorBook = 1u << 9,        // VendorItemBook, VendorItemSpellTome, VendorItemScroll, VendorItemStaff
		kVendorHide = 1u << 10,       // VendorItemAnimalHide, VendorItemAnimalPart
		kVendorTool = 1u << 11,       // VendorItemTool, VendorItemClutter, VendorItemFireword
		kVendorStolen = 1u << 12,     // a fence
	};

	struct ActorTable
	{
		std::uint32_t seq;
		std::uint32_t count;
		std::uint8_t  pad[0x40 - 8];
		ActorRecord   actors[kMaxActors];
	};
	static_assert(sizeof(ActorTable) == 0x40 + 64 * kMaxActors);

	// ---- event ring @0x17000 (MC -> Skyrim) ---------------------------------------------------
	inline constexpr std::uint32_t kEventRingEntries = 512;  // power of two
	inline constexpr std::uint64_t kEventRingHeadOff = 0x00;  // u64, written by MC
	inline constexpr std::uint64_t kEventRingTailOff = 0x40;  // u64, written by Skyrim
	inline constexpr std::uint64_t kEventRingDataOff = 0x80;

	enum McEventType : std::uint32_t
	{
		kEvHitActor = 1,    // formId, a = MC damage (after MC's own modifiers), b/c = knockback dir x/z (MC), d = knockback strength
		kEvPlayerDied = 2,  // the Minecraft player died: kill the Skyrim player
		kEvExplosion = 3,   // a Minecraft explosion (TNT, creeper, ...): a/b/c = centre (MC coords), d = radius (blocks)
		kEvArrowStuck = 4,  // an arrow stuck in a Skyrim actor: formId, a/b/c = where it hit (MC coords), d = flight yaw,
		                    // flags = flight pitch (float bits), weapon = arrow texture (0 plain, 1 tipped, 2 spectral)
		kEvSkillUse = 5,    // the player used a Skyrim skill in Minecraft: formId = Skyrim skill (ActorValue: 9 Block,
		                    // 10 Smithing, 11 Heavy Armor, 12 Light Armor), a = uses (as Skyrim's AdvanceSkill counts them)
		kEvTakeItem = 6,    // Minecraft converted a looted item: formId = the item's base form, a = how many to remove
		                    // from the Skyrim player's inventory (see ItemEvent)
		kEvSleep = 7,       // the player slept in a Minecraft bed: a = game hours to pass in Skyrim
		kEvSpawnCreature = 8,  // a Skyrim creature comes out of the dark in a dug tunnel: formId = SpawnKind, a/b/c = its
		                       // feet (MC coords, on top of a solid block), d = despawn distance (blocks), flags = most
		                       // spawned creatures alive at once (Skyrim refuses the spawn past it)
		kEvEnterDungeon = 9,   // the player opened a breach door underground: formId = the dungeon's exterior load door
		                       // (BreachRecord::doorRef). Skyrim marks the dungeon discovered and moves the player inside,
		                       // locked or not, exactly where that door leads
		kEvActivate = 10,      // a fresh right click Minecraft had no use for (no block, entity or item in either hand took
		                       // it): Skyrim activates whatever its own crosshair is on when this arrives, as the G key does,
		                       // at most once per 0.4 s; nothing there, nothing happens. Added within version 13 (an older
		                       // plugin ignores the type)
		kEvPortalTravel = 11,  // the player stood in a bound Minecraft portal: Skyrim moves them to its partner behind a
		                       // loading screen, as a load door does (Travel.cpp). formId = the destination Skyrim world as
		                       // SkyState::worldId names it: a worldspace FormID outside, an interior cell FormID when flags
		                       // has kPortalInterior. a/b/c = where the player's feet go (MC coords: just in front of the
		                       // partner portal, on its exit side), d = the yaw they face there (MC degrees), flags =
		                       // PortalTravelFlags, weapon = SkyState::worldId when Minecraft sent it: Skyrim drops the request
		                       // if the player is no longer in that world. Added within version 13 (an older plugin ignores it)
		// (12 and 13 are McViewEventType's, below the McFlags.)
		kEvCompanion = 14,     // Minecraft's taming rules on a Skyrim animal (Companions): formId = the actor, flags =
		                       // CompanionAction, weapon = the AnimalKind Minecraft read for it in the actor table (Skyrim
		                       // refuses a taming whose kind isn't its own), a = kCompanionHeal: the share of the animal's
		                       // maximum health to restore (0..1). Skyrim checks the actor is alive, near the player and
		                       // (for every action but kCompanionTame) the player's companion. Added within version 13 (an
		                       // older plugin ignores the type)
		// (15 to 18 are other features'.)
		kEvMerchantGold = 19,  // Selling to merchants: how much Skyrim gold can this merchant pay? formId = the merchant actor,
		                       // flags = a request number. Skyrim answers with kInMerchantGold. Added within version 13 (an
		                       // older plugin ignores it, so Minecraft, getting no answer, calls the sale off)
		kEvSellToMerchant = 20,  // Selling to merchants: Minecraft took goods the merchant bought. formId = the merchant actor,
		                         // a = the Skyrim gold it pays, b = how many items it bought (for the log), flags = the request
		                         // number kEvMerchantGold asked with. Skyrim moves that much gold (Gold001, 0x0000000F) from
		                         // the merchant's chest (its vendor faction's merchant container; its own purse if it has
		                         // none) into the player's inventory, never more than the merchant has. Added within version 13
		// Towns and building (added within version 13; an older plugin ignores them). a/b/c are a place (MC coords).
		kEvTownCrime = 23,        // the player griefed a town (town_crime): formId = TownCrimeKind, a/b/c = where, d = what it is
		                          // worth in gold (a few seconds' worth added up), flags = how many blocks. Skyrim decides who
		                          // saw it (its own detection) and raises a steal alarm (Actor::StealAlarm) for the witness's
		                          // crime faction: bounty, witnesses and guards as for a theft of that value
		kEvTownBuild = 24,        // the host placed, broke or dug blocks in a town with a guard near (kActorGuard): a/b/c =
		                          // where, flags = TownBuildKind. At most one every 2 s. A guard who sees the player may remark on
		                          // it (guard_comments), never more than once a minute per guard
		kEvHearthfireStage = 25,  // enough Minecraft blocks stand on a Hearthfire plot for a stage (hearthfire_blocks): formId =
		                          // the plot (SkyState::hearthPlot), flags = HearthfireStage, a = blocks standing
		kEvRedstoneDoor = 26,     // redstone power at a Dwemer door's block (kInDwemerDoor) came or went: a/b/c = the block's
		                          // centre, flags = 1 powered, 0 not. Skyrim opens the door when power comes and closes it when
		                          // it goes (an unlocked door only, with Skyrim's own activation)
		kEvPartyShake = 29,    // shared world events (kFeatSharedWorldEvents): something another player in the Minecraft world
		                       // went through shakes this player's camera a little (a dragon attacking them, a dragon slain):
		                       // a = strength (0 to 1, Skyrim's ShakeCamera), b = seconds (0.1 to 3). Nothing in this Skyrim
		                       // changes but the camera. Added within version 13 (an older plugin ignores the type)
	};

	enum PortalTravelFlags : std::uint32_t
	{
		kPortalInterior = 1u << 0,  // kEvPortalTravel's formId is an interior cell, not a worldspace
	};

	// What kEvCompanion asks for (its flags).
	enum CompanionAction : std::uint32_t
	{
		kCompanionTame = 1,    // Minecraft's taming roll came up (a bone for a dog or wolf, horse food for a horse): the animal
		                       // becomes the player's. Dogs and wolves become Skyrim's animal follower (the way a stray dog
		                       // joins: DialogueFollowerScript.SetAnimal) told to wait, as a freshly tamed Minecraft wolf
		                       // sits; an animal that held the slot before stays the player's, left behind. Horses become the
		                       // player's own, as a bought horse is (ownership)
		kCompanionFollow = 2,  // follow the player: the animal takes Skyrim's animal follower slot if it hasn't got it, and
		                       // stops waiting (the WaitingForPlayer actor value Skyrim's follower packages read)
		kCompanionWait = 3,    // wait here (Minecraft's sit): WaitingForPlayer 1
		kCompanionHeal = 4,    // fed: restore a = a share of its maximum health
	};

	// What a kEvTownCrime was (its formId).
	enum TownCrimeKind : std::uint32_t
	{
		kCrimeGround = 1,  // Skyrim's own ground dug out (its land, rocks, roads), or a block of it that digging revealed broken
		kCrimeObject = 2,  // a Skyrim building, wall or other object dug into
		kCrimeBlock = 3,   // a Minecraft block another player placed in the town broken
	};

	// What a kEvTownBuild was (its flags).
	enum TownBuildKind : std::uint32_t
	{
		kBuildPlace = 1,  // blocks placed
		kBuildBreak = 2,  // Minecraft blocks broken
		kBuildDig = 3,    // Skyrim's ground or objects dug
	};

	// Which Hearthfire stage a kEvHearthfireStage reached (its flags).
	enum HearthfireStage : std::uint32_t
	{
		kStageSmallHouse = 1,
		kStageMainHall = 2,
		kStageWings = 3,
	};

	// What kEvSpawnCreature asks for (Skyrim picks the actor base by the player's level).
	enum SpawnKind : std::uint32_t
	{
		kSpawnSkeever = 1,
		kSpawnSkeleton = 2,
		kSpawnDraugr = 3,
	};

	enum HitFlags : std::uint32_t
	{
		kHitCritical = 1u << 0,
		kHitProjectile = 1u << 1,
		kHitSweep = 1u << 2,
		kHitFire = 1u << 3,

		// Combat (added within version 13: an older Minecraft never sets them, an older plugin ignores them).
		kHitPowerAttack = 1u << 4,  // power_attacks: a fully charged melee hit (attack strength over 0.9, Minecraft's own
		                            // "full") that was a sprint hit or a critical hit
		kHitCrouching = 1u << 5,    // sneak_attacks: the player who landed it was crouching (Skyrim's sneak) as it landed
	};

	// What landed a kEvHitActor (Skyrim plays that weapon class's impact effect and sounds).
	enum HitWeapon : std::uint32_t
	{
		kWeaponUnarmed = 0,
		kWeaponBlade = 1,   // swords
		kWeaponAxe = 2,
		kWeaponBlunt = 3,   // maces, pickaxes, shovels, hoes, anything else held
		kWeaponPierce = 4,  // tridents, spears
		kWeaponArrow = 5,   // arrows and other projectiles
	};

	struct McEvent
	{
		std::uint32_t type;
		std::uint32_t formId;
		float         a, b, c, d;
		std::uint32_t flags;
		std::uint32_t weapon;  // HitWeapon for kEvHitActor
	};
	static_assert(sizeof(McEvent) == 32);

	// ---- item ring @0x1B100 (Skyrim -> MC) ----------------------------------------------------
	// Every item that lands in the Skyrim player's inventory (looted, picked up, bought, crafted).
	// Minecraft decides from its rules file whether it becomes a Minecraft item; if it does, it
	// gives the Minecraft items and sends kEvTakeItem back so Skyrim removes the original. Also the
	// gear a dying actor wore (kSrcWorn), which stays in Skyrim.
	inline constexpr std::uint32_t kItemRingEntries = 64;     // power of two
	inline constexpr std::uint64_t kItemRingHeadOff = 0x00;  // u64, written by Skyrim
	inline constexpr std::uint64_t kItemRingTailOff = 0x40;  // u64, written by MC
	inline constexpr std::uint64_t kItemRingDataOff = 0x80;

	// What kind of item it is, from its form type and Skyrim's vendor/material keywords.
	enum ItemTags : std::uint32_t
	{
		kTagFood = 1u << 0,        // VendorItemFood
		kTagFoodRaw = 1u << 1,     // VendorItemFoodRaw
		kTagHide = 1u << 2,        // VendorItemAnimalHide
		kTagOreIngot = 1u << 3,    // VendorItemOreIngot
		kTagPotion = 1u << 4,      // VendorItemPotion
		kTagPoison = 1u << 5,      // VendorItemPoison
		kTagIngredient = 1u << 6,  // VendorItemIngredient
		kTagWeapon = 1u << 7,      // form type Weapon
		kTagArmor = 1u << 8,       // form type Armor, not clothing or jewelry
		kTagClothing = 1u << 9,    // ArmorClothing
		kTagJewelry = 1u << 10,    // ArmorJewelry
		kTagGold = 1u << 11,       // gold coins (form 0xF)
		kTagKey = 1u << 12,        // form type KeyMaster
		kTagArrow = 1u << 13,      // form type Ammo
		kTagGem = 1u << 14,        // VendorItemGem
		kTagClutter = 1u << 15,    // VendorItemClutter
		kTagBook = 1u << 16,       // form type Book or Scroll
		kTagSoulGem = 1u << 17,    // form type SoulGem (VendorItemSoulGem; not the misc soul gem fragments)
		kTagSoulFilled = 1u << 18, // a soul gem that holds a soul as it comes (the filled base forms Skyrim sells and loots)
	};

	// Bits 24-27 of ItemEvent::tags: what kind of weapon or armor piece the item is, as Skyrim types it (a weapon's
	// type, the biped slots an armor piece or piece of clothing covers), for every item event. Added within version 13:
	// an older plugin sends 0 (unknown), and an older Minecraft's rules only test the tag bits they name.
	inline constexpr std::uint32_t kTagKindShift = 24;
	inline constexpr std::uint32_t kTagKindMask = 0xFu << kTagKindShift;

	enum ItemKind : std::uint32_t
	{
		kKindNone = 0,        // not a weapon or armor, or not a kind below (jewelry, a ring or amulet slot only)
		kKindSword = 1,       // one-handed sword
		kKindDagger = 2,
		kKindWarAxe = 3,      // also pickaxes and woodcutter's axes, which Skyrim types as war axes
		kKindMace = 4,
		kKindGreatsword = 5,
		kKindBattleaxe = 6,   // two-handed axe type: battleaxes and warhammers
		kKindBow = 7,
		kKindStaff = 8,
		kKindCrossbow = 9,
		kKindHandToHand = 10,
		kKindHelmet = 11,     // armor or clothing on the head, hair, long hair or circlet slot (and not the body)
		kKindCuirass = 12,    // the body slot
		kKindGauntlets = 13,  // hands or forearms
		kKindBoots = 14,      // feet or calves
		kKindShield = 15,     // the shield slot
	};

	// Weapon/armor material from WeapMaterial*/ArmorMaterial* keywords.
	enum ItemMaterial : std::uint8_t
	{
		kMatNone = 0, kMatWood, kMatIron, kMatSteel, kMatImperial, kMatOrcish, kMatDwarven, kMatElven,
		kMatGlass, kMatEbony, kMatDaedric, kMatDragon, kMatSilver, kMatHide, kMatDraugr, kMatFalmer,
	};

	// Where the item came from.
	enum ItemSource : std::uint8_t
	{
		kSrcWorld = 0,      // picked up, crafted, or otherwise from nowhere
		kSrcCorpse = 1,     // a dead actor
		kSrcContainer = 2,  // a chest, barrel, merchant chest, ...
		kSrcActor = 3,      // a living actor (gift, pickpocket)
		kSrcBought = 4,     // bought from a merchant (Skyrim's barter menu was open)
		kSrcWorn = 5,       // Gear drops: worn by an actor in the actor table the frame it died (container = the actor),
		                    // one event per piece: both hands' weapons, the shield, armor and clothes, the equipped ammo
		                    // (count = the quiver). It never came into the player's inventory: Minecraft may drop its own
		                    // gear for it at the corpse, never gives it to the player and never sends kEvTakeItem. count
		                    // is negated (-1 for a piece) so a Minecraft jar from before this, which skips counts under 1,
		                    // can't convert it. Not sent for the player's followers or summoned and raised actors. Added
		                    // within version 13
	};

	struct ItemEvent
	{
		std::uint32_t formId;  // base form of the item
		std::int32_t  count;   // negative for kSrcWorn
		std::uint32_t tags;    // ItemTags, and the ItemKind in bits 24-27 (kTagKindShift)
		std::uint8_t  material;  // ItemMaterial
		std::uint8_t  source;    // ItemSource
		std::uint8_t  chest;    // 1 when the container is a chest, strongbox or coffer (not a barrel, sack, urn, ...)
		std::uint8_t  quest;    // 1 when it's a quest item: Minecraft leaves it in Skyrim
		char          name[28];   // display name, UTF-8, NUL-terminated (truncated)
		std::uint32_t container;  // FormID of the reference it came from (chest, corpse), 0 if none
	};
	static_assert(sizeof(ItemEvent) == 48);
	static_assert(offsetof(ItemEvent, chest) == 14 && offsetof(ItemEvent, quest) == 15 && offsetof(ItemEvent, name) == 16 && offsetof(ItemEvent, container) == 44);  // SkyLink.drainItems
	static_assert(kOffItemRing >= kOffEventRing + kEventRingDataOff + sizeof(McEvent) * kEventRingEntries);
	static_assert(kOffItemRing + kItemRingDataOff + sizeof(ItemEvent) * kItemRingEntries <= kOffWorldEntities);

	// ---- world entities @0x1C000 (MC -> Skyrim, seqlock) -------------------------------------
	// Minecraft things Skyrim draws itself each frame (arrows, dropped items, block cracks) + the
	// block outline.
	inline constexpr std::uint32_t kMaxWorldEntities = 160;

	enum WorldEntityKind : std::uint32_t
	{
		kWeArrow = 1,    // uv[0]: the arrow's item icon
		kWeItem = 2,     // dropped/thrown item: a flat sprite (uv[0]) turning about the vertical
		kWeTrident = 3,  // uv[0]: the trident's item icon
		kWeBlock = 4,    // dropped block item: a spinning cube of side `scale`, uv[0..2] = side, top, bottom
		kWeCrack = 5,    // block-breaking cracks over the box at (x, y, z) of size ext, uv[0] = crack stage
		kWeShadow = 6,   // a player's or mob's feet at (x, y, z), `scale` wide: its soft contact shadow
	};

	struct WorldEntity
	{
		std::uint32_t kind;        // WorldEntityKind
		std::uint32_t id;          // MC entity id (stable while it exists)
		float         x, y, z;     // MC coords (interpolated at MC's render time)
		float         yaw, pitch;  // MC degrees
		float         scale;
		float         ext[3];      // kWeCrack: box size
		float         uv[3][4];    // atlas rects {u0, v0, u1, v1}
		std::uint32_t tint;        // RGBA8 multiplier for the top face (grass, leaves); 0 = none
	};
	static_assert(sizeof(WorldEntity) == 96);

	struct WorldEntities
	{
		std::uint32_t seq;
		std::uint32_t count;
		std::uint32_t hasSelection;           // draw an outline around the targeted block
		float         selMin[3], selMax[3];   // MC coords
		std::uint8_t  pad[0x40 - 36];
		WorldEntity   entities[kMaxWorldEntities];
	};
	static_assert(sizeof(WorldEntities) == 0x40 + sizeof(WorldEntity) * kMaxWorldEntities);
	static_assert(kOffWorldEntities + sizeof(WorldEntities) <= kOffBreachTable);

	// ---- breach table (Skyrim -> MC, seqlock) --------------------------------------------------
	// Dungeon entrances near the player (exterior load doors in loaded cells whose destination is a
	// dungeon interior), refreshed about once a second. Minecraft builds a hidden antechamber under each
	// one; opening its sealed door sends kEvEnterDungeon. Empty (count 0) in interiors.
	inline constexpr std::uint32_t kMaxBreaches = 16;

	enum BreachTheme : std::uint8_t
	{
		kThemeCave = 0,     // caves, animal dens, anything else
		kThemeNordic = 1,   // draugr crypts, Nordic ruins and barrows, dragon priest lairs
		kThemeDwemer = 2,   // Dwarven ruins (LocTypeDwarvenAutomatons)
		kThemeFalmer = 3,   // Falmer hives
		kThemeMine = 4,     // mines
		kThemeFort = 5,     // bandit camps, forts, Forsworn camps
		kThemeLair = 6,     // vampire, warlock and hagraven lairs
	};

	enum BreachFlags : std::uint8_t
	{
		kBreachDiscovered = 1u << 0,  // its map marker is already discovered
		kBreachCleared = 1u << 1,     // the location is cleared
	};

	struct BreachRecord
	{
		std::uint32_t doorRef;   // exterior load door reference FormID (send back in kEvEnterDungeon)
		std::uint32_t location;  // BGSLocation FormID of the dungeon, 0 if none
		float         x, y, z;   // the door's position, MC coords
		float         yaw;       // the door's facing, MC degrees
		std::uint8_t  theme;     // BreachTheme
		std::uint8_t  flags;     // BreachFlags
		std::uint16_t level;     // the location's encounter zone minimum level, 0 if unknown
		char          name[28];  // dungeon name, UTF-8, NUL-terminated (truncated)
	};
	static_assert(sizeof(BreachRecord) == 56);
	static_assert(offsetof(BreachRecord, theme) == 24 && offsetof(BreachRecord, level) == 26 && offsetof(BreachRecord, name) == 28);

	struct BreachTable
	{
		std::uint32_t seq;      // seqlock: odd while Skyrim writes
		std::uint32_t count;    // records in use
		std::uint32_t worldId;  // worldspace FormID these belong to
		std::uint32_t pad;
		BreachRecord  records[kMaxBreaches];
	};
	static_assert(sizeof(BreachTable) == 16 + 56 * kMaxBreaches);
	static_assert(kOffBreachTable + sizeof(BreachTable) <= kOffCollisionRing);

	// ---- render ring (MC -> Skyrim) -----------------------------------------------------------
	// Byte ring like the collision ring. Minecraft ships its own block meshes (built by Minecraft's
	// block renderer: models, tint, AO, lighting) and its block atlas; Skyrim draws them in its own
	// frame so blocks stay locked to the world and are occluded by Skyrim geometry.
	inline constexpr std::uint64_t kRenRingHeadOff = 0x00;
	inline constexpr std::uint64_t kRenRingTailOff = 0x40;
	inline constexpr std::uint64_t kRenRingDataOff = 0x80;
	inline constexpr std::uint64_t kRenRingDataBytes = kRenderRingBytes - kRenRingDataOff;

	enum RenType : std::uint32_t
	{
		kRenPad = 0,
		kRenAtlas = 1,     // RenAtlas + RGBA8 pixels (w * h * 4), top row first
		kRenSection = 2,   // RenSection + RenVertex[vertexCount] (triangle list); 0 vertices = remove
		kRenClearAll = 3,  // drop every section (world change)
		kRenTexture = 4,   // RenTexture + RGBA8 pixels: an entity texture (player skin, armour, ...)
		kRenAvatar = 5,    // RenAvatar + RenBatch[batchCount] + RenVertex[vertexCount]: the player's
		                   // model this frame; 0 batches = not shown (first person)
		kRenScene = 6,     // RenScene + RenBatch[batchCount] + RenVertex[vertexCount]: every other
		                   // entity and all particles this frame, relative to RenScene's origin
		kRenAtlasRegion = 7,  // RenAtlasRegion + RGBA8 pixels: an animated sprite's current frame
		kRenLights = 8,       // RenLights + RenLight[count]: a section's light-emitting blocks (sent
		                      // after its kRenSection; 0 = none)
		kRenSolids = 10,      // RenSolids + 512-byte bitset (bit x + 16z + 256y): which blocks of a
		                      // section NPCs collide with (sent after its kRenSection; 0 = none)
		kRenDug = 11,         // RenDug + 512-byte bitset (bit x + 16z + 256y): which blocks of a section
		                      // were dug out of Skyrim's world (its geometry there is gone); 0 = none
		kRenRagdoll = 9,      // RenAvatar + RenBatch[] + RenVertex[]: the player's body standing still,
		                      // relative to the feet and facing +Z, split into its parts (RenBatch
		                      // flags bits 8-11: RagdollPart). Sent about once a second while alive;
		                      // Skyrim hangs the parts on its ragdoll when the player dies.
		kRenPassages = 14,    // RenPassages + RenPassage[count]: a section's doors and ladders (npc_doors_ladders; sent
		                      // after its kRenSolids; 0 = none). Added within version 13: an older plugin skips it.
		                      // 15 is set aside for towns
	};

	struct RenPassages
	{
		std::int32_t  sx, sy, sz;  // section coords, as in RenSection
		std::uint32_t count;       // RenPassage records that follow
	};

	// RenPassage::flags: what the block is (bits 0-1), whether a door is open, which way it faces, which half.
	enum PassageFlags : std::uint8_t
	{
		kPassageDoor = 1,      // a Minecraft door NPCs may open (wooden, copper, ...: any but iron)
		kPassageIronDoor = 2,  // an iron door: only redstone opens it
		kPassageLadder = 3,    // a ladder (never NPC solid while kTownPassages is set)
		kPassageKindMask = 3,
		kPassageOpen = 1u << 2,         // the door is open (never NPC solid while kTownPassages is set)
		kPassageFacingShift = 3,        // bits 3-4: the way it faces, Minecraft's 2D value (0 south +z, 1 west -x, 2 north -z,
		kPassageFacingMask = 3u << 3,   // 3 east +x); a ladder hangs on the block behind it, the opposite way
		kPassageUpper = 1u << 5,        // a door's upper half
	};

	struct RenPassage
	{
		std::uint8_t x, y, z;  // block within the section
		std::uint8_t flags;    // PassageFlags
	};
	static_assert(sizeof(RenPassage) == 4);

	struct RenSolids
	{
		std::int32_t  sx, sy, sz;  // section coords, as in RenSection
		std::uint32_t count;       // solid blocks (0: none, and no bitset follows)
	};

	struct RenDug
	{
		std::int32_t  sx, sy, sz;  // section coords, as in RenSection
		std::uint32_t count;       // dug blocks (0: none, and no bitset follows)
		std::uint32_t worldId;     // the Skyrim world (SkyState::worldId) the bits belong to
		std::uint32_t pad;
	};
	static_assert(sizeof(RenDug) == 24);

	// What a piece of diggable Skyrim geometry is made of, as the Minecraft block it digs into
	// (ColTri flags bits 8-15). Chosen on the Skyrim side from Havok materials and object types.
	enum DigMaterial : std::uint8_t
	{
		kDigNone = 0,  // not known: stone
		kDigGrass = 1,
		kDigDirt = 2,
		kDigStone = 3,
		kDigCobble = 4,
		kDigSnow = 5,
		kDigIce = 6,
		kDigSand = 7,
		kDigGravel = 8,
		kDigMud = 9,
		kDigOakLog = 10,
		kDigSpruceLog = 11,
		kDigBirchLog = 12,
		kDigPlanks = 13,
		kDigMetal = 14,
		kDigGlass = 15,
		kDigOrganic = 16,
		kDigCloth = 17,
		kDigBone = 18,
		kDigWeb = 19,
		kDigAsh = 20,
		kDigBedrock = 21,  // Minecraft only: a few blocks under the land
		kDigMaterialCount
	};

	enum RagdollPart : std::uint32_t
	{
		kPartNone = 0,
		kPartHead = 1,
		kPartBody = 2,
		kPartRightArm = 3,
		kPartLeftArm = 4,
		kPartRightLeg = 5,
		kPartLeftLeg = 6,
		kPartCount = 7,
	};

	struct RenLights
	{
		std::int32_t  sx, sy, sz;  // section coords, as in RenSection
		std::uint32_t count;
	};

	enum LightKind : std::uint8_t
	{
		kLightSteady = 0,
		kLightFlame = 1,  // torches, fire, campfires, candles: flicker
		kLightLava = 2,   // lava, magma: a slow glow
	};

	enum BlockHazard : std::uint8_t
	{
		kHazardNone = 0,
		kHazardFire = 1,   // fire, soul fire, campfires: burns what stands in it
		kHazardLava = 2,   // lava: burns hard
		kHazardMagma = 3,  // magma block: hurts what stands on top of it
	};

	struct RenLight
	{
		std::uint8_t  x, y, z;  // block within the section
		std::uint8_t  level;    // Minecraft light emission, 1-15
		std::uint32_t color;    // RGB8 (r low byte); top byte: LightKind in bits 0-3, BlockHazard in 4-7
	};
	static_assert(sizeof(RenLight) == 8);

	struct RenAtlasRegion
	{
		std::uint32_t x, y, width, height;  // pixels in the combined atlas (kRenAtlas)
	};

	struct RenScene
	{
		double        originX, originY, originZ;  // MC block the positions are relative to
		std::uint32_t batchCount;
		std::uint32_t vertexCount;
	};

	struct RenTexture
	{
		std::uint32_t id;  // 1+, referenced by RenBatch::texture
		std::uint32_t width, height;
		std::uint32_t pad;
	};

	// The player as Minecraft's own entity renderer draws it (skin, armour, held items, cape),
	// posed and animated, with positions in blocks relative to the player's feet: Skyrim puts
	// it at its own interpolated feet position, the one its camera follows.
	struct RenAvatar
	{
		std::uint32_t batchCount;
		std::uint32_t vertexCount;
	};

	struct RenBatch
	{
		std::uint32_t texture;  // 0: the block/item atlas, else a RenTexture id
		std::uint32_t first;    // first vertex
		std::uint32_t count;    // vertices (multiple of 3)
		std::uint32_t flags;    // bit0: translucent (blended, after the solid pass; casts no shadow)
	};

	struct RenAtlas
	{
		std::uint32_t width, height;
	};

	struct RenSection
	{
		std::int32_t  sx, sy, sz;   // section coords (16-block cubes)
		std::uint32_t vertexCount;  // multiple of 3
	};

	struct RenVertex
	{
		float         x, y, z;  // MC coords relative to the section origin (sx*16, sy*16, sz*16)
		float         u, v;     // atlas UV
		std::uint32_t color;    // RGBA8 (tint * ambient occlusion; Minecraft's fixed face shading is left out)
		std::uint32_t light;    // low byte: block light 0-15, next byte: sky light 0-15
		std::uint32_t flags;    // bit0: cutout (alpha test), bit1: translucent, bit3: full-detail texture (no mips),
		                        // bits 4-6: face normal as MC Direction ordinal + 1 (0 = none: lit without a normal;
		                        // 7: the triangle's own), bit7: glows, unlit (experience orbs)
	};
	static_assert(sizeof(RenVertex) == 32);

	struct InputEvent
	{
		std::uint16_t type;
		std::uint16_t code;
		std::int32_t  a;
		std::int32_t  b;
		std::int32_t  c;
	};
	static_assert(sizeof(InputEvent) == 16);

	// ---- collision ring @0x20000 (Skyrim produces, MC consumes) -----------------------------
	// Byte ring. Every message starts 8-byte aligned with {u32 type, u32 payloadBytes}.
	// A kColPad message means "skip to the start of the ring".
	inline constexpr std::uint64_t kColRingHeadOff = 0x00;  // u64 total bytes written
	inline constexpr std::uint64_t kColRingTailOff = 0x40;  // u64 total bytes consumed
	inline constexpr std::uint64_t kColRingDataOff = 0x80;
	inline constexpr std::uint64_t kColRingDataBytes = kCollisionRingBytes - kColRingDataOff;

	enum ColType : std::uint32_t
	{
		kColPad = 0,
		kColClear = 1,   // payload: u32 epoch, u32 worldId (the Skyrim world, as SkyState::worldId, the epoch's
		                 // regions describe: Minecraft puts them in its space. Added within version 13; an older
		                 // plugin sends the epoch alone and Minecraft waits for SkyState to show it)
		kColRegion = 2,  // payload: ColRegion + ColBlock[count]
		kColTris = 3,    // payload: ColRegion (count = triangles) + ColTri[count]; sent before kColRegion
	};

	// Exact Skyrim collision triangle (MC space) for the player's smooth collider.
	enum ColTriFlags : std::uint32_t
	{
		kTriStairHelper = 1u << 0,  // Skyrim's invisible stair ramp: walkable, never a wall
		kTriDiggable = 1u << 1,     // ground, rock, trees...: can be dug into (bits 8-15: DigMaterial).
		                            // Its normal faces out of the solid side (winding is outward).
		kTriGhost = 1u << 2,        // a diggable triangle as it was before blocks were dug out of it:
		                            // not collision, only for telling what's inside Skyrim's geometry
		kTriTerrain = 1u << 3,      // the land (a height field)
	};

	inline constexpr std::uint32_t kTriMaterialShift = 8;

	struct ColTri
	{
		float         v[9];
		std::uint32_t flags;
	};
	static_assert(sizeof(ColTri) == 40);

	struct ColMsgHeader
	{
		std::uint32_t type;
		std::uint32_t payloadBytes;
	};

	// Replaces all Skyrim collision inside the inclusive block box [min, max].
	struct ColRegion
	{
		std::int32_t  minX, minY, minZ;
		std::int32_t  maxX, maxY, maxZ;
		std::uint32_t epoch;
		std::uint32_t count;
	};
	static_assert(sizeof(ColRegion) == 32);

	// One block's worth of Skyrim collision as an 8x8x8 occupancy mask.
	// bits[y] bit (z * 8 + x) is sub-voxel (x, y, z), each 1/8 block, in MC axes.
	struct ColBlock
	{
		std::int32_t  x, y, z;
		std::uint32_t pad;
		std::uint64_t bits[8];
	};
	static_assert(sizeof(ColBlock) == 80);
}
