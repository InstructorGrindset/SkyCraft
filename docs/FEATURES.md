# SkyCraft fork features

Features this fork (InstructorGrindset/SkyCraft) adds on top of upstream SkyCraft 0.1.2. The base
game features (movement, blocks, digging, combat, skills) are described in the README and
[DESIGN.md](DESIGN.md). Every config file below lives in the Minecraft instance's `config` folder
and is written with defaults the first time it's needed; delete a file to get the new defaults
back.

Status: **Tested** means played and confirmed in game, **Built** means it compiles and passes unit
tests but hasn't been played yet.

## Feature map

Every feature can be switched off in `skycraft-features.txt` (restart Minecraft after editing).
Minecraft side paths are under `fabric/src/main/java/dev/skycraft/`, Skyrim side under `skse/src/`.

| Feature | Switch | What it does | Minecraft side | Skyrim side | Config | Log tag | Status |
|---|---|---|---|---|---|---|---|
| Kill drops | `kill_drops` | Skyrim animals you kill drop Minecraft meat, leather and bones | `loot/SkyLoot`, `loot/LootRules` | actor table | `skycraft-loot.txt` | `SkyCraft-loot` | Tested |
| Kill XP | `kill_xp` | Credited kills drop XP orbs, more for higher levels | `loot/KillXp`, `loot/SkyLoot` | actor table level | none | `SkyCraft-loot` | Built |
| Item conversion | `item_conversion` | Food, potions, ores, hides and salvaged gear you loot in Skyrim become Minecraft items, and so do torches, tools, kitchenware, Dwemer clutter, Hearthfire building materials, animal parts, alchemy reagents, fish and gems (`+` gives several items for one); clothing and jewelry rules wait, commented out, for a decision; quest items never convert. Full table in `docs/CONVERSIONS.md` | `loot/SkyBridge`, `loot/ItemRules` | `Loot.cpp` (container sink, Take) | `skycraft-items.txt` | `SkyCraft-loot` | Tested |
| Potions | `item_conversion` | Every Skyrim potion becomes a Minecraft potion with effects of its own, named like the Skyrim one (health red, magicka blue, stamina green), and every poison a splash potion: 57 alchemy effects map to Minecraft effects (Restore Health is Instant Health, magicka is Absorption, Barter is Hero of the Village, Smithing is Haste, Paralysis is Slowness V, ...), with level and seconds growing with the name's tier (Minor and Weak up to Ultimate, Grand and Prime). Cure Disease and Cure Poison give a milk bucket; the White Phial and the quest poisons stay. Rules write `minecraft:potion{haste:0-2:60-180 #rrggbb "Name"}`, or `minecraft:potion[healing]` for a vanilla potion, also for `splash_potion`, `lingering_potion` and `tipped_arrow`. Long names cut to 27 letters come back whole. Full table in `docs/CONVERSIONS.md` | `loot/ItemRules` ("Potions and poisons" section, `Effect`, `TIERS`, `LONG_NAMES`), `loot/SkyBridge` (potion contents, custom name) | `Loot.cpp` (VendorItemPotion, VendorItemPoison tags) | `skycraft-items.txt` | `SkyCraft-loot` | Built |
| Dungeon chests | `dungeon_chests` | First loot of each dungeon chest also rolls a Minecraft chest loot table | `loot/DungeonLoot` | `WorldState::Place` (dungeon flag), item event chest flag and container id | `skycraft-dungeons.txt`, `skycraft-opened-chests.txt` | `SkyCraft-loot` | Built |
| Merchants | `merchants` | Right click a Skyrim vendor with an empty hand for a villager trade screen; town prices, supply and demand, Speech XP | `trade/` (SkyTrade, TradeRules, Currency, Demand), `combat/SkyrimActorEntity.interact` | `WorldState::VendorMask`, `WorldState::Place` (town) | `skycraft-trades.txt`, `skycraft-towns.txt` | `SkyCraft-trade` | Built |
| Time sync | `time_sync` | Minecraft's clock follows Skyrim's game hour | `time/SkyTime` | SkyState game hour | none | `SkyCraft-time` | Built |
| Bed sleep | `bed_sleep` | A full night in a Minecraft bed passes the night in Skyrim (needs time sync for beds to work at night) | `time/SkyTime` | `WorldState::PassTime` | none | `SkyCraft-time` | Built |
| Deep mining | none | Bedrock 64 blocks under the land (however deep, under buried rocks, ore veins and cave mouths too; in interiors 64 under the floor above), ores by depth, deepslate layer. Ground under the land never opens into nothing: undug cells beside a hole are solid, old holes close as you pass, and a player who still ends up in the void, or put inside rock by Skyrim, goes back to safe open ground | `world/SkyDig`, `world/SkyGround`, `world/SafeSpot`, client `FallGuard` | `Dig.cpp`, `Collision.cpp` (the land over a deep tunnel), `DigPhysics.cpp` (no Skyrim land rescue for the player) | none | `SkyCraft` | Built |
| Deep creatures | `deep_creatures` | Skeevers, skeletons and draugr spawn in dark tunnels you dug, by depth | `deep/` (SkyDeep, DeepRules) | `Spawn.cpp` | `skycraft-deep.txt` | `SkyCraft-deep` | Built |
| Breach doors | `breach_doors` | A hidden antechamber under each dungeon entrance (12 blocks down for low levels, deeper for higher ones), walled in the dungeon's theme (Nordic, Dwemer, Falmer, mine, fort, lair, cave). Its walls need a good enough pickaxe (iron for Dwemer and lairs, stone for Nordic, Falmer and forts); its sealed iron door takes you into the real dungeon. Particles and sounds leak through the rock near it, its name shows when you break in, and the first breach of an uncleared dungeon has a loot chest | `breach/` (SkyBreach, BreachPlanner, BreachRoom, BreachStore, BreachBlocks), hooks in `world/SkyDig` and `world/SkyDigBlast` | breach table, `kEvEnterDungeon` | `skycraft-dungeons.txt` (chest tables), `skycraft-breaches.txt` in the world's save folder | `SkyCraft-breach` | Built |
| Click to activate | `click_to_activate` | Right click with nothing usable in hand activates the Skyrim door, container, NPC, item, furniture or lever under the crosshair, as G does. Minecraft goes first: the held item is used (blocks placed, food eaten, bows drawn, shields raised), Minecraft blocks and entities take the click, and an empty hand on a merchant still opens the trade screen. Only a fresh press counts, so holding right click never activates anything; at most one activation per 0.4 s | client source set: `client/ClickActivate`, `client/mixin/MinecraftUseMixin`; `combat/SkyrimActorEntity` (merchant vendor mask synced so the client sees the trade take the click) | `Input.cpp` (`UpdateClickActivate`, same activation as G), `kEvActivate` | none | `SkyCraft` | Built |
| Minecraft view | `minecraft_view` | In any dimension other than the Skyrim mirror (the Nether, Tamriel Wilds, Home, ...) the screen shows Minecraft's own world: Minecraft draws its world, hand and HUD and sends the whole picture (at most 1080 high, `-Dskycraft.viewMaxHeight`, Skyrim scales it up) to cover Skyrim's. Skyrim keeps running underneath and parks its player where they stood: held in place, god mode, nobody fighting them, HUD hidden, its world not drawn. Every Skyrim-coupled system stands down meanwhile (collision, dug ground, water, NPC stand-ins, loot and items, time, breaches, deep creatures, click to activate, Skyrim's teleports and look); keys and mouse go to Minecraft, which turns the player itself. Back in the mirror, Skyrim's player follows to wherever Minecraft's comes back in (Skyrim leads instead if its player died there or is in another Skyrim world). Dying over there kills the Skyrim player as anywhere. Debug: `/skyview` (host only) goes to the Nether, or any other dimension the world has, and back; `/skyview <dimension id>`, `/skyview mirror`. Off: other dimensions act like the mirror, as before | `SkyDimension` (the guard every system asks), client `MinecraftView`, `FrameExporter` (opaque frames), `SkyClient`, `InputBridge` (look), guards in the `mixin/` and `client/mixin/` classes, `combat/SkyCombat`, `net/SkyNet`, `breach/SkyBreach` | `View.cpp` (park, hand back), `Overlay.cpp` (full screen frame), `Input.cpp` (routing, `kInLook`), `Game.cpp` | `SkyCraft.ini` `[MinecraftView] bHideSkyrimWorld` | `SkyCraft`, `Minecraft view` in SkyCraft.log | Built |
| Gear sets | none | Skyrim Iron through Daedric weapons and armor, new materials, recipes, original textures | `gear/` (SkyGear, GearTiers), `mixin/SmithingMixin` | none | data in the jar | none | Built |
| Portal network | `portals` | Portals are cracks between places that resemble each other. An obsidian frame lit in Skyrim makes a real Nether portal, but vanilla Nether travel never happens from the mirror world. A filled Greater, Grand or Black soul gem from Skyrim (bought or looted filled) becomes a Filled Soul Gem; used on a lit portal it becomes an Attuned Soul Gem, and used on a second lit portal it binds the two for good and is spent. After Minecraft's usual portal wait a bound portal takes the host to its partner behind a loading screen, like a load door, between worldspaces and interiors alike. Unbound portals do nothing but say how to bind them; breaking a frame unbinds its partner. Merchants sell obsidian dear (smiths, cheapest in Markarth, and general stores) | `portal/` (SkyPortals, PortalStore, PortalItems, SoulGemItem), `mixin/NetherPortalBlockMixin`, soul gem rules in `loot/ItemRules`, obsidian in `trade/TradeRules` | `Travel.cpp` (MoveTo_Impl with no target reference), `Loot.cpp` (soul gem tags), `kEvPortalTravel` | `skycraft-portals.txt` in the world's save folder, `skycraft-items.txt` ("Soul gems" section), `skycraft-trades.txt` | `SkyCraft-portal` | Built |
| World spaces | none | Every Skyrim world (each worldspace and each interior cell) keeps its Minecraft blocks in a space of its own in the mirror, so what you build, dig or reveal in one interior never shows in another or in the sky over Tamriel. Tamriel's space is the origin (outdoor builds stay put); the others are 32,768 blocks apart on a spiral round it, in the order you first visit them. A world made before this sorts itself out once: other worlds' dug cells move to their spaces with the blocks in and against them (see [DESIGN](DESIGN.md) section 4) | `link/SkySpace`, `link/WorldSpaces`, `link/SkyLink` (every position crossing the link), `world/SkyCollision`, `world/SpaceMigration`, client `render/WorldExporter`, guests' copy via `net/SkyNet` | none (the protocol stays in each world's own terms); `kColClear` names the world, `Game.cpp` logs McState `spaceWorld` | `skycraft-spaces.txt` in the world's save folder | `SkyCraft-space` | Built |
| Worlds | none | Three dimensions beside the mirror world: the Nether, Tamriel Wilds and Home (Steve's seed, ending in the Far Lands). See [Worlds](#worlds) | `worlds/` (Worlds, SkyWorlds, HomeChunkGenerator, HomeTerrain, ClassicNoise, FarLands, SteveCamp, HomeCamp, WorldArrivals), `worlds/mixin/` | none | data in the jar | `SkyCraft-worlds` | Built |

Shared pieces: `config/Config` reads and writes the config files, `config/Features` holds the
switches, `link/` is the shared memory link, and `WorldState.cpp` on the Skyrim side reports the
player's town and place and merchants' buy lists. Place changes are logged as `SkyCraft-place`.

## Worlds

Three dimensions sit beside the mirror world, for the Sygmension story ([LORE.md](LORE.md)). Travel
between them is still to be wired (portal work); `dev.skycraft.worlds.Worlds` holds their keys, seeds and
arrival points for that.

| World | Dimension | Seed | What's in it |
|---|---|---|---|
| The Nether | `minecraft:the_nether` | the world's | Vanilla Nether generator and biomes, fortresses and bastions |
| Tamriel Wilds | `skycraft:tamriel_wilds` | 4201 (4E 201), pinned | Skyrim one step away. Amplified height in the mountains, normal height on open land: tall jagged snowy ranges, broad flat valleys, sea cliffs. Granite cliffs in wavy bands of tuff, andesite and diorite. Ten biomes, two structures, mineshafts, ruined portals in the mountains |
| Home | `skycraft:home` | Steve's (3257840388504953787), pinned | Steve's old seed: classic terrain (octave noise hills, lakes, beaches, oaks) that breaks into the Far Lands at 12,550,821 on both axes. Steve's last camp and the crack |

**Tamriel Wilds biomes** (`data/skycraft/worldgen/biome`): Whiterun Tundra (golden grass, dry grass,
andesite boulders, scattered pines, allium and bluet), Falkreath Pines (dense old growth spruce, ferns,
mossy boulders, moss and podzol), The Rift (autumn poplars in orange, red and yellow, birches, leaf litter,
rivers), Eastmarch Springs (basalt and sulfur ground, hot springs with sulfur rims and magma floors that
bubble, snowy pines, sulfur haze), The Reach (tuff and andesite crags, poppies and rose bushes, goats),
The Pale (snowfields, snowy spruce), Winterhold Peaks (every high peak, and the cold north: snow, packed
ice, ice), Solstheim Ash (grey ash plains, dark basalt and blackstone cliffs, huge mushroom groves on
mycelium, falling ash), Tamriel River and the Sea of Ghosts (cold ocean with ice floes and icebergs).
Each has its own grass, foliage, water, fog and sky colors. Above about y 195 every biome turns to snow.

**Structures**: Dwemer towers (waxed cut copper with weathered bands, copper grates, redstone lamps lit by
hidden redstone blocks, copper bulbs, a domed top with a lightning rod, a loot chest) in the Reach,
Eastmarch, Whiterun, the Rift and the Pale; Nordic barrows (grassy burial mounds with a stone doorway,
a crypt with a sarcophagus, urn, candles and a loot chest) in the holds. Single piece jigsaw structures;
templates in `data/skycraft/structure`.

**Steve's camp** (Home, centred on 12,550,700, 71, 0): a 5 by 5 dirt hut with a red bed, a furnace, a
crafting table, a chest of Beta era things (porkchops, bread, apples, torches, cobblestone, a stone pickaxe,
flint and steel, a compass, a clock) and a lectern holding the written book "Journal" by Steve, whose
pages are placeholders marked `[PLACEHOLDER]` (text in `SteveCamp.JOURNAL_PAGES`). Seven waxed signs
along the gravel trail count Day 2417 to Day 2423. The trail runs east on levelled ground to the wall,
where the crack (3 wide, 48 deep, open to the sky) cuts into the Far Lands. Arrival
(`Worlds.HOME_ARRIVAL`): 12,550,705.5, 71, 0.5, facing east at the wall.

**How they load into the existing SkyCraft world.** The dimensions are data in the mod's own data pack
(`data/minecraft/dimension/the_nether.json`, `data/skycraft/dimension/*.json`). When a world opens,
Minecraft 26.3 merges the data pack's dimensions with the ones saved in the world
(`WorldDimensions.bake`, called from `LevelStorageSource.getLevelDataAndDimensions`), so a world made
with the mirror preset (overworld only) gains the Nether, Tamriel Wilds and Home the next time it opens.
Nothing in the save changes; take the mod out and the dimensions go away (their region files stay under
`dimensions/`). The data pack must not define `minecraft:overworld`: a data pack dimension replaces the
saved one with the same key. The SkyCraft world is created with structures off and seed 0, so two mixins
in `worlds/mixin/` adjust these worlds only: `ServerLevelSeedMixin` gives Tamriel Wilds and Home their
lore seeds (terrain, structures and decoration all read `ServerLevel.getSeed`), and
`ChunkStatusTasksMixin`, `StructureManagerMixin` and `ChunkGeneratorLocateMixin` let structures generate
and be located in the Nether, Tamriel Wilds and Home. The mirror overworld keeps its settings. The world
border (default 29,999,984) reaches past the Far Lands.

Tested on a headless 26.3 server (Fabric Loader 0.19.5, Fabric API 0.161.0): a world created with the
mirror preset, seed 0 and structures off, reopened with the worlds code, reports seeds 0, 4201 and
3257840388504953787 for the overworld, Tamriel Wilds and Home, locates the Dwemer tower at 96, 288 and a
fortress in the Nether, and generates the camp with its chest, journal and signs.

**Visit** (cheats on):

```
/execute in skycraft:tamriel_wilds run tp @s 91.5 120 306.5 180 0
/execute in minecraft:the_nether run tp @s 0 70 0
/execute in skycraft:home run tp @s 12550705.5 71 0.5 -90 0
/execute in skycraft:tamriel_wilds run locate biome skycraft:the_rift
/execute in skycraft:tamriel_wilds run locate structure skycraft:dwemer_tower
```

**Preview without Skyrim.** `python3 tools/build_worlds_preview.py` writes `dist/skycraft-worlds-preview.zip`,
a data pack for a plain vanilla 26.3 world (no mod): the Nether and Tamriel Wilds with its biomes,
features and structures. Add it under Data Packs when creating a world (or put it in
`<world>/datapacks` before the first start) and use the commands above. Without the mod, Tamriel Wilds
uses the world seed; create the world with seed 4201 to see the same land as the mod. Home is Java code,
so it needs the mod: in any world running SkyCraft (the SkyCraft world, or the dev client from
`./gradlew runClient` in `fabric`), use the Home command.

**Editing.** Tamriel Wilds' JSON and both structure templates are written by
`python3 tools/gen_worlds_data.py --jar <minecraft-26.3-client.jar>` (the jar is in Prism's
`libraries/com/mojang/minecraft/26.3`); it covers biome colors, features, climate ranges, surface rules,
the terrain blend and the structure layouts. Edit the tables there and run it again. Home's
terrain is `worlds/HomeTerrain` (noise in `ClassicNoise`, the edge in `FarLands`), the camp layout
`worlds/SteveCamp`.

Things to know: the Skyrim side features (collision, digging, combat bridges) don't know about these
worlds yet, so view mode needs dimension guards before anyone plays in them. With a Nether present, a
lit Nether portal in the mirror world now works the vanilla way.

## Planned

| Feature | Notes |
|---|---|
| Farming on Skyrim soil | May already work with dug dirt; needs a test first |
| Tamed companions | A Skyrim dog or horse shows up as a Minecraft wolf or horse that follows you |
| Artifacts | About 20 named Skyrim artifacts as unique Minecraft items with real enchantments |

## Protocol 13

The shared memory protocol went from 12 to 13 for these features. Both halves must match.

| Addition | Direction | Used by |
|---|---|---|
| `SkyState.town`, `SkyState.place` (PlaceFlags) | Skyrim to Minecraft | Merchants, dungeon chests |
| `ActorRecord.vendor` (VendorBuys) | Skyrim to Minecraft | Merchants |
| `ActorFlags::kActorSpawned` | Skyrim to Minecraft | Deep creatures cap |
| `ItemEvent.chest`, `ItemEvent.container` | Skyrim to Minecraft | Dungeon chests |
| `ItemEvent.quest` | Skyrim to Minecraft | Item conversion skips quest items |
| `kEvSleep` | Minecraft to Skyrim | Bed sleep |
| `kEvSpawnCreature` | Minecraft to Skyrim | Deep creatures |
| `BreachTable` | Skyrim to Minecraft | Breach doors |
| `kEvEnterDungeon` | Minecraft to Skyrim | Breach doors |
| `kEvActivate` (10) | Minecraft to Skyrim | Click to activate. Added within 13 without a version bump: an older Skyrim plugin ignores the event type |
| `kEvPortalTravel` (11), `PortalTravelFlags` | Minecraft to Skyrim | Portal network: formId = destination worldspace, or interior cell with `kPortalInterior`; a/b/c = feet (MC coords), d = yaw, weapon = the world it was sent from (stale requests dropped). Added within 13 |
| `ItemTags` `kTagSoulGem`, `kTagSoulFilled` (bits 17, 18) | Skyrim to Minecraft | Portal network: soul gems that come filled convert to Filled Soul Gems. Added within 13; an older plugin never sets them |

The Minecraft view added these within 13, without a version bump (an older half ignores them):

| Addition | Direction | Used by |
|---|---|---|
| `McFlags::kMcOtherDimension` (bit 8) | Minecraft to Skyrim | The player is in a dimension other than the mirror. Skyrim parks its player while it's set and takes Minecraft's position on the falling edge |
| `OverlayFlags::kOverlayOpaque` (`OverlaySlotHdr.flags` bit 1) | Minecraft to Skyrim | The frame is Minecraft's whole picture, drawn full screen with its alpha ignored |
| `kInLook` (9) | Skyrim to Minecraft | Raw mouse motion while away; Minecraft turns its player itself |
| `McViewEventType` `kEvEnterOtherDimension` (12), `kEvLeaveOtherDimension` (13) | Minecraft to Skyrim | Reserved and not sent: Skyrim follows `kMcOtherDimension`, a state a dropped event or a reconnect can't leave stale |

World spaces added these within 13, without a version bump (an older half ignores them):

| Addition | Direction | Used by |
|---|---|---|
| `kColClear` payload `u32 worldId` after the epoch | Skyrim to Minecraft | Minecraft puts the epoch's collision in that world's space. Without it Minecraft waits for SkyState to show the epoch |
| `McState.spaceWorld`, `spaceX`, `spaceZ` (0xC8..0xD3; McState is 0xD8 bytes now) | Minecraft to Skyrim | Which Skyrim world's space Minecraft's player is in, and its origin; Skyrim logs it. Every coordinate on the link stays in the current world's own terms |

## Testing a build

1. Back up the Skyrim saves folder.
2. Install the jar into the instance's `mods` folder and the DLL into `Data/SKSE/Plugins`, keeping
   the previous ones in `dev-backups`.
3. Play the feature tests for the build.
4. Read `Documents/My Games/Skyrim Special Edition/SKSE/SkyCraft.log` and the instance's
   `logs/latest.log`, searching for the log tags above.
