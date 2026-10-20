# SkyCraft fork features

Features this fork (InstructorGrindset/SkyCraft) adds on top of upstream SkyCraft 0.1.2. The base
game features (movement, blocks, digging, combat, skills) are described in the README and
[DESIGN.md](DESIGN.md). Every config file below lives in the Minecraft instance's `config` folder
and is written with defaults the first time it's needed; delete a file to get the new defaults
back. `skycraft-items.txt` and `skycraft-loot.txt` still holding an earlier build's defaults, never
edited, get the new ones by themselves (the old file is kept beside it as `.txt.old`); an edited file
is left alone (`config/Config.read`, the old defaults in `ItemRules.OLD_DEFAULTS` and `LootRules.OLD_DEFAULTS`).

Status: **Tested** means played and confirmed in game, **Built** means it compiles and passes unit
tests but hasn't been played yet, **Written** means the code is done and its pure logic passes unit tests,
but it hasn't been compiled against Minecraft 26.3 or built for Skyrim yet.

## Feature map

Every feature can be switched off in `skycraft-features.txt` (restart Minecraft after editing).
Minecraft side paths are under `fabric/src/main/java/dev/skycraft/`, Skyrim side under `skse/src/`.

| Feature | Switch | What it does | Minecraft side | Skyrim side | Config | Log tag | Status |
|---|---|---|---|---|---|---|---|
| Kill drops | `kill_drops` | Every Skyrim creature you kill (animals, spiders, chaurus, Falmer, draugr, skeletons, ghosts, atronachs, Dwemer machines, trolls, giants, DLC creatures, dragons) drops what its Minecraft counterpart would, and people carry coin and odds and ends (bandits, Forsworn, necromancers, Silver Hand, vampires, mages, guards and soldiers; contributed by Higor Machado). Drops land above Minecraft blocks and on Skyrim's ground, never under it: a rule can roll a vanilla mob's loot table (`table minecraft:entities/spider`) as that mob's death at your hands, so vanilla does counts, chances, Looting, kill-only drops (spider eyes) and cooked meat; others drop `item min-max` (Looting adds 0 to 1 per level) or `item 1 2.5%` (Looting adds 1% per level). Bosses (dragons, dragon priests) pay more. An unedited old `skycraft-loot.txt` gets the new rules (old file kept as `.old`, `config/Config.read` with old defaults). Full table in `docs/CONVERSIONS.md` | `loot/SkyLoot` (`killer`, `lootingLevel`), `loot/LootRules`, `combat/SkyrimActorEntity` (last player hit) | actor table | `skycraft-loot.txt` | `SkyCraft-loot` | Built |
| Kill XP | `kill_xp` | Credited kills drop XP orbs, more for higher levels | `loot/KillXp`, `loot/SkyLoot` | actor table level | none | `SkyCraft-loot` | Built |
| Gear drops | `gear_drops` | A Skyrim person or creature you kill sometimes drops the Minecraft version of each weapon or armor piece it wore, as a Minecraft mob drops its equipment (26.3's numbers: 8.5% a piece plus 1% per Looting level, randomly worn; arrows as a skeleton's, 0 to 2 plus 0 to 1 per Looting level). Its Skyrim gear stays on the corpse. Your followers and summoned or raised actors never drop. The piece is the one Skyrim types it as (weapon type, armor slot), so Zephyr drops a bow. Table in `docs/CONVERSIONS.md` | `loot/GearDrops`, `loot/GearMap`, `loot/SkyBridge` (worn items never reach the player), `loot/SkyLoot` (credited kill) | `Loot.cpp` (`WatchWorn`, `SendWorn`), `Combat.cpp` (actor table) | `skycraft-gear-drops.txt` | `SkyCraft-loot` | Built |
| Item conversion | `item_conversion` | Food, potions, ores, hides and salvaged gear you loot in Skyrim (weapons and armor of every material salvage into their smithing material) become Minecraft items, and so do torches, tools, kitchenware, Dwemer clutter, Hearthfire building materials, animal parts, alchemy reagents, fish and gems (`+` gives several items for one); clothing and jewelry rules wait, commented out, for a decision; quest items never convert. Full table in `docs/CONVERSIONS.md` | `loot/SkyBridge`, `loot/ItemRules` | `Loot.cpp` (container sink, Take) | `skycraft-items.txt` | `SkyCraft-loot` | Tested |
| Potions | `item_conversion` | Every Skyrim potion becomes a Minecraft potion with effects of its own, named like the Skyrim one (health red, magicka blue, stamina green), and every poison a splash potion: 57 alchemy effects map to Minecraft effects (Restore Health is Instant Health, magicka is Absorption, Barter is Hero of the Village, Smithing is Haste, Paralysis is Slowness V, ...), with level and seconds growing with the name's tier (Minor and Weak up to Ultimate, Grand and Prime). Cure Disease and Cure Poison give a milk bucket; the White Phial and the quest poisons stay. Rules write `minecraft:potion{haste:0-2:60-180 #rrggbb "Name"}`, or `minecraft:potion[healing]` for a vanilla potion, also for `splash_potion`, `lingering_potion` and `tipped_arrow`. Long names cut to 27 letters come back whole. Full table in `docs/CONVERSIONS.md` | `loot/ItemRules` ("Potions and poisons" section, `Effect`, `TIERS`, `LONG_NAMES`), `loot/SkyBridge` (potion contents, custom name) | `Loot.cpp` (VendorItemPotion, VendorItemPoison tags) | `skycraft-items.txt` | `SkyCraft-loot` | Built |
| Dungeon chests | `dungeon_chests` | First loot of each dungeon chest also rolls a Minecraft chest loot table | `loot/DungeonLoot` | `WorldState::Place` (dungeon flag), item event chest flag and container id | `skycraft-dungeons.txt`, `skycraft-opened-chests.txt` | `SkyCraft-loot` | Built |
| Merchants | `merchants` | Right click a Skyrim vendor with an empty hand for a villager trade screen; town prices, supply and demand, Speech XP | `trade/` (SkyTrade, TradeRules, Currency, Demand), `combat/SkyrimActorEntity.interact` | `WorldState::VendorMask`, `WorldState::Place` (town) | `skycraft-trades.txt`, `skycraft-towns.txt` | `SkyCraft-trade` | Built |
| Time sync | `time_sync` | Minecraft's clock follows Skyrim's game hour, in the mirror and, sharing its clock, in Tamriel Wilds and Home while the host is there | `time/SkyTime` | SkyState game hour | none | `SkyCraft-time` | Built |
| Bed sleep | `bed_sleep` | A full night in a Minecraft bed passes the night in Skyrim, in Tamriel Wilds and Home too (needs time sync for beds to work at night) | `time/SkyTime` | `WorldState::PassTime` | none | `SkyCraft-time` | Built |
| Deep mining | none | Bedrock 64 blocks under the land (however deep, under buried rocks, ore veins and cave mouths too; in interiors 64 under the floor above), ores by depth, deepslate layer. Ground under the land never opens into nothing: undug cells beside a hole are solid, old holes close as you pass, and a player who still ends up in the void, or put inside rock by Skyrim, goes back to safe open ground | `world/SkyDig`, `world/SkyGround`, `world/SafeSpot`, client `FallGuard` | `Dig.cpp`, `Collision.cpp` (the land over a deep tunnel), `DigPhysics.cpp` (no Skyrim land rescue for the player) | none | `SkyCraft` | Built |
| Deep creatures | `deep_creatures` | Skeevers, skeletons and draugr spawn in dark tunnels you dug, by depth | `deep/` (SkyDeep, DeepRules) | `Spawn.cpp` | `skycraft-deep.txt` | `SkyCraft-deep` | Built |
| Breach doors | `breach_doors` | A hidden antechamber under each dungeon entrance (12 blocks down for low levels, deeper for higher ones), walled in the dungeon's theme (Nordic, Dwemer, Falmer, mine, fort, lair, cave). Its walls need a good enough pickaxe (iron for Dwemer and lairs, stone for Nordic, Falmer and forts); its sealed iron door takes you into the real dungeon. Particles and sounds leak through the rock near it, its name shows when you break in, and the first breach of an uncleared dungeon has a loot chest | `breach/` (SkyBreach, BreachPlanner, BreachRoom, BreachStore, BreachBlocks), hooks in `world/SkyDig` and `world/SkyDigBlast` | breach table, `kEvEnterDungeon` | `skycraft-dungeons.txt` (chest tables), `skycraft-breaches.txt` in the world's save folder | `SkyCraft-breach` | Built |
| Click to activate | `click_to_activate` | Right click with nothing usable in hand activates the Skyrim door, container, NPC, item, furniture or lever under the crosshair, as G does. Minecraft goes first: the held item is used (blocks placed, food eaten, bows drawn, shields raised), Minecraft blocks and entities take the click, and an empty hand on a merchant still opens the trade screen. Only a fresh press counts, so holding right click never activates anything; at most one activation per 0.4 s | client source set: `client/ClickActivate`, `client/mixin/MinecraftUseMixin`; `combat/SkyrimActorEntity` (merchant vendor mask synced so the client sees the trade take the click) | `Input.cpp` (`UpdateClickActivate`, same activation as G), `kEvActivate` | none | `SkyCraft` | Built |
| Minecraft view | `minecraft_view` | In any dimension other than the Skyrim mirror (the Nether, Tamriel Wilds, Home, ...) the screen shows Minecraft's own world: Minecraft draws its world, hand and HUD and sends the whole picture (at most 1080 high, `-Dskycraft.viewMaxHeight`, Skyrim scales it up) to cover Skyrim's. Skyrim keeps running underneath and parks its player where they stood: held in place, god mode, nobody fighting them, HUD hidden, its world not drawn. Every Skyrim-coupled system stands down meanwhile (collision, dug ground, water, NPC stand-ins, loot and items, breaches, deep creatures, click to activate, Skyrim's teleports and look), except time: the clock keeps following Skyrim's (Time sync); keys and mouse go to Minecraft, which turns the player itself. Back in the mirror, Skyrim's player follows to wherever Minecraft's comes back in (Skyrim leads instead if its player died there or is in another Skyrim world). A portal back to a Skyrim place in another worldspace, an interior or far off moves Skyrim's parked player there first, then Minecraft's follows (Portal network). Dying over there kills the Skyrim player as anywhere. Debug: `/skyview` (host only) goes to the Nether, or any other dimension the world has, and back; `/skyview <dimension id>`, `/skyview mirror`. Off: other dimensions act like the mirror, as before | `SkyDimension` (the guard every system asks), client `MinecraftView`, `FrameExporter` (opaque frames), `SkyClient`, `InputBridge` (look), guards in the `mixin/` and `client/mixin/` classes, `combat/SkyCombat`, `net/SkyNet`, `breach/SkyBreach` | `View.cpp` (park, hand back), `Overlay.cpp` (full screen frame), `Input.cpp` (routing, `kInLook`), `Game.cpp` | `SkyCraft.ini` `[MinecraftView] bHideSkyrimWorld` | `SkyCraft`, `Minecraft view` in SkyCraft.log | Built |
| Gear sets | none | Skyrim Iron through Daedric weapons and armor, new materials, recipes, original textures | `gear/` (SkyGear, GearTiers), `mixin/SmithingMixin` | none | data in the jar | none | Built |
| Portal network | `portals` | Portals are cracks between places that resemble each other. An obsidian frame lit in Skyrim makes a real Nether portal, but vanilla Nether travel never happens, in the mirror or in any other world: a portal outside the network leads nowhere. A filled Greater, Grand or Black soul gem from Skyrim (bought or looted filled) becomes a Filled Soul Gem; used on a lit portal it becomes an Attuned Soul Gem, and used on a second lit portal it binds the two for good and is spent. After Minecraft's usual portal wait a bound portal takes the host to its partner behind a loading screen, like a load door, between worldspaces and interiors alike. Keys open a lit portal in Skyrim onto another world, building the partner portal at that world's arrival point (obsidian frame, solid floor, lava and water sealed off) and binding the pair; the key is spent and the partner leads back: a Daedra Heart opens onto the Nether, a Tonal Key (crafted from a dwarven metal ingot, redstone and an amethyst shard) onto Tamriel Wilds. A portal lit at the Time-Wound on the Throat of the World's summit opens one way onto Home when Skyrim's player carries an Elder Scroll. In Home the Far Lands' strata (past 12,550,821 on either axis, the crack too) drop Seamstone; the first one picked up is the ending (a title, a few lines, and a lit portal opens in the crack). Seamstone held to any lit portal opens a chest of every place you've been (the worlds' arrival points, every portal you stood at, bound or built); pick one and step in. It's never spent. Obsidian frames light in Tamriel Wilds and Home too. Unbound portals do nothing but say how to open them; breaking a frame unbinds its partner. Merchants sell obsidian dear (smiths, cheapest in Markarth, and general stores). See [Portals into the worlds](#portals-into-the-worlds) | `portal/` (SkyPortals, PortalStore, PortalItems, SoulGemItem, PortalKeyItem, PortalKeys, PortalPlan, PortalBuilder, TimeWound, Destinations, SeamMenu, Ending), `mixin/NetherPortalBlockMixin`, `mixin/BaseFireBlockMixin`, soul gem rules in `loot/ItemRules`, obsidian in `trade/TradeRules` | `Travel.cpp` (MoveTo_Impl with no target reference), `Loot.cpp` (soul gem tags), `WorldState.cpp` (`Carried`: the Elder Scrolls), `kEvPortalTravel`, `SkyState.carried` | `skycraft-portals.txt` in the world's save folder, `skycraft-items.txt` ("Soul gems" section), `skycraft-trades.txt` | `SkyCraft-portal` | Built |
| World spaces | none | Every Skyrim world (each worldspace and each interior cell) keeps its Minecraft blocks in a space of its own in the mirror, so what you build, dig or reveal in one interior never shows in another or in the sky over Tamriel. Tamriel's space is the origin (outdoor builds stay put); the others are 32,768 blocks apart on a spiral round it, in the order you first visit them. A world made before this sorts itself out once: other worlds' dug cells move to their spaces with the blocks in and against them (see [DESIGN](DESIGN.md) section 4) | `link/SkySpace`, `link/WorldSpaces`, `link/SkyLink` (every position crossing the link), `world/SkyCollision`, `world/SpaceMigration`, client `render/WorldExporter`, guests' copy via `net/SkyNet` | none (the protocol stays in each world's own terms); `kColClear` names the world, `Game.cpp` logs McState `spaceWorld` | `skycraft-spaces.txt` in the world's save folder | `SkyCraft-space` | Built |
| Worlds | none | Three dimensions beside the mirror world: the Nether, Tamriel Wilds and Home (Steve's seed, ending in the Far Lands), reached through the portal network. See [Worlds](#worlds) | `worlds/` (Worlds, SkyWorlds, HomeChunkGenerator, HomeTerrain, ClassicNoise, FarLands, SteveCamp, HomeCamp, WorldArrivals), `worlds/mixin/` | none | data in the jar | `SkyCraft-worlds` | Built |
| Companions | `companions` | Minecraft's taming rules on Skyrim's animals. A bone fed to a Skyrim dog or wolf tames it one time in three (hearts, or smoke), unless you hit it in the last 30 seconds; apples, wheat, sugar, carrots, golden carrots and golden apples tame a horse by temper, each meal standing for one mount attempt. A tamed dog or wolf becomes Skyrim's animal follower the way a stray dog joins (ally, teammate, follows the player through load doors, fights for them) and sits, as a freshly tamed wolf does; a tamed horse becomes the player's own, like a bought one. Right click a companion with an empty hand to make it sit or follow (sneak for a horse: a plain click rides it, Skyrim's way); its food heals it (meat and fish for dogs and wolves, horse food for horses). Skyrim's own animal followers (Meeko, Vigilance, stray and bought dogs, huskies), dog, wolf or horse teammates and owned horses count as tamed. Skyrim keeps one animal follower: the one you last told to follow has it, the others stay behind, still yours. Stand-ins are named "Meeko (tamed)"; a companion's death is told in chat. Kept in the SKSE co-save. See [Companions](#companions) | `companion/` (SkyCompanions, CompanionRules), `combat/SkyrimActorEntity` (actor flags synced, interact), `combat/SkyCombat` | `Companion.cpp`, `AnimalKinds.h` (which races are dogs, wolves, horses), `CoSave.cpp` (co-save record `CMPN`), `Combat.cpp` (actor table bits, `kEvCompanion`) | none | `SkyCraft-companion` (both halves) | Built |
| Natural spawns | `natural_spawns` | Animals and monsters spawn the Minecraft way in the Nether, Tamriel Wilds and Home, never in the Skyrim mirror: the spawn game rules go on, set on every start, and the mirror turns away what they allow (natural spawns, the custom spawners, zombie reinforcements, creaking hearts, zombified piglins from portals, trial spawners, skeleton horse traps). Night comes out there with Skyrim's (Time sync). Phantoms, patrols and wandering traders stay off. Needs `minecraft_view` (with it off every world counts as the mirror). Off: no natural spawning anywhere, as before. See [Worlds](#worlds) | `SkyCraft.configureServer`, `mixin/ServerChunkCacheSpawnMixin`, `mixin/ServerLevelSpawnMixin`, `mixin/TrialSpawnerMixin`, `time/SkyTime`; spawn lists and biome tags in the worlds data | none | none | `SkyCraft` | Built |
| Skooma and addiction | `skooma_addiction` | Three SkyCraft effects for Skyrim's potions to give (item conversion's rules name them): Skooma High (`skycraft:skooma`, level II for Redwater Skooma), per level +20% speed, higher jumps (+0.1 jump strength and a block more safe fall, as Jump Boost), +0.15 sneaking speed and stealth; Stealth (`skycraft:stealth`, for Fortify Sneak), the sneaking speed and stealth alone; Skooma Withdrawal. Stealth: mobs notice a crouching player under either from 0.6 of the distance at level I, 0.45 at II. The switch is the addiction, per player and saved with them: each dose may hook (5%, more for doses close together and for Redwater), a day without a dose brings withdrawal (I to III, worse each day: slower, weaker, hungrier, cravings on the action bar, now and then nausea; milk clears it for a while), a dose ends it, three days clean cure it once a day of withdrawal is lived through; a high that wears off leaves a comedown. The effects work with the switch off. `/skyskooma` shows your habit. See [Skooma and addiction](#skooma-and-addiction) | `skooma/` (SkyEffects, SkySkooma, SkoomaRules), `mixin/LivingEntityStealthMixin` | none | `skycraft-skooma.txt` | `SkyCraft-skooma` | Built |
| Sneak skill | `sneak_skill` | The host's Skyrim Sneak makes a crouching host harder for Minecraft's mobs to notice: from 1 of the distance at Sneak 15 down to 0.6 at 100, on top of Skooma's and Stealth's (0.36 with Skooma I at Sneak 100), never under their 0.2. See [Skills](#skills) | `skills/` (SkySkills, SkillRules), `mixin/LivingEntityStealthMixin` | `WorldState.cpp` (`Skills`: SkyState skills, once a second), `Game.cpp`, `main.cpp` | `skycraft-skills.txt` | `SkyCraft-skills` (both halves) | Written |
| Archery skill | `archery_skill` | Arrows the host shoots from a bow or crossbow hit harder with Skyrim Archery: x1 at 15 to x1.5 at 100, once on the arrow's base damage as it's shot (Power adds on top; Skyrim NPCs take it too) | `skills/SkySkills`, `mixin/ArcheryMixin` | as Sneak skill | `skycraft-skills.txt` | `SkyCraft-skills` | Written |
| Smithing skill | `smithing_skill` | Anvil repairs and combining cost the host a level less for every 25 Smithing above 15 (3 less at 100), never under 1; a rename alone, and "Too Expensive!", stay as they were | `skills/SkySkills`, `mixin/AnvilSmithingMixin` | as Sneak skill | `skycraft-skills.txt` | `SkyCraft-skills` | Written |
| Enchanting skill | `enchanting_skill` | The enchanting table counts a bookshelf more for the host for every 6 Enchanting above 15 (14 more at 100), 15 at most, so better offers with fewer shelves; a Filled Soul Gem goes in the lapis slot, one gem for each lapis (anyone's) | `skills/SkySkills`, `skills/SoulGemFuelSlot`, `mixin/EnchantingTableMixin`, `mixin/EnchantingCostMixin`, `mixin/EnchantingLapisSlotMixin` | as Sneak skill | `skycraft-skills.txt` | `SkyCraft-skills` | Written |
| Dragon souls | `dragon_souls` | Each dragon soul the host absorbs in Skyrim gives 5 Minecraft XP levels, a chat line and the level-up sound. The count last seen is kept on the player (per world), so reconnecting never pays twice, and a save loaded or a new game only records its count; souls spent on shouts aren't taken back | `skills/SkySkills` (attachment `skycraft:dragon_souls`), `skills/SkillRules` (`SoulLedger`) | `WorldState.cpp` (`Skills`: dragonSouls, skillsLoad; `SkillsReloaded` on every game loaded) | `skycraft-skills.txt` | `SkyCraft-skills` (both halves) | Written |
| Alchemy brewing | `alchemy_brewing` | Potions the host takes out of a brewing stand last longer with Skyrim Alchemy: 0.5% a level above 15 (x1.425 at 100), counted when drunk, splashed or left as a cloud, and in the tooltip. An allium (what Purple Mountain Flower, Skyrim's Fortify Sneak flower, becomes) brews an awkward potion into a Potion of Stealth (3:00; redstone 8:00, glowstone Stealth II 1:30) | `skills/SkyAlchemy`, `mixin/BrewingTakeMixin` | as Sneak skill | `skycraft-skills.txt` | `SkyCraft-skills` | Written |

Shared pieces: `config/Config` reads and writes the config files, `config/Features` holds the
switches, `link/` is the shared memory link, and `WorldState.cpp` on the Skyrim side reports the
player's town and place and merchants' buy lists. Place changes are logged as `SkyCraft-place`.

## Worlds

Three dimensions sit beside the mirror world, for the Sygmension story ([LORE.md](LORE.md)). The portal
network leads into them ([Portals into the worlds](#portals-into-the-worlds)); `dev.skycraft.worlds.Worlds`
holds their keys, seeds and arrival points.

| World | Dimension | Seed | What's in it |
|---|---|---|---|
| The Nether | `minecraft:the_nether` | the world's | Vanilla Nether generator and biomes, fortresses and bastions |
| Tamriel Wilds | `skycraft:tamriel_wilds` | 4201 (4E 201), pinned | Skyrim one step away. Amplified height in the mountains, normal height on open land: tall jagged snowy ranges, broad flat valleys, sea cliffs. Granite cliffs in wavy bands of tuff, andesite and diorite. Ten biomes with their animals and monsters, two structures of its own and vanilla ones (villages, outposts, trial chambers, ...) |
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

**Vanilla structures** (the Wilds biomes join vanilla's biome tags, `"replace": false`, in
`data/minecraft/tags/worldgen/biome`): villages (plains houses on Whiterun's tundra and in the Rift, spruce in
Falkreath and Eastmarch, snowy in the Pale), pillager outposts (Whiterun, the Reach, the Pale), igloos (the Pale,
Winterhold), trail ruins (Falkreath, the Rift), trial chambers under every biome, shipwrecks and cold ocean ruins
in the Sea of Ghosts, mineshafts under the land, abandoned camps in Falkreath and the Rift, ruined portals in the
Reach and Winterhold. An existing world gets them only in land it hasn't generated yet; one whose start lands next
to explored land is cut off at its edge.

**Life** (switch `natural_spawns`). Animals and monsters spawn the Minecraft way in the Nether, Tamriel Wilds and
Home, never in the mirror. The spawn lists (`attributes."minecraft:gameplay/natural_mob_spawns"` in each biome) use
vanilla 26.3's categories and monster weights: bats; at night spiders, zombies, skeletons, creepers, endermen,
witches and slimes in slime chunks, with strays in the cold holds (Eastmarch, the Pale, Winterhold), zombie horses
on Whiterun's tundra and in the Pale, husks on Solstheim's ash, drowned in the river and the Sea of Ghosts; glow
squid in cave water; salmon and squid in the river; cod, salmon, squid and nautilus in the Sea of Ghosts. Animals
by hold: Whiterun sheep, cows, chickens, pigs, horses, donkeys and rabbits; Falkreath wolves, foxes, rabbits and
pigs; the Rift sheep, cows, wolves, foxes and rabbits; Eastmarch foxes, wolves, rabbits and goats; the Reach goats,
foxes and rabbits; the Pale foxes, wolves, rabbits and now and then a polar bear; Winterhold goats; Solstheim
mooshrooms; polar bears on the Sea of Ghosts' ice. Foxes and rabbits are white in the Pale and Winterhold, and
pigs, cows and chickens wear their cold coats in Falkreath, Eastmarch, the Reach, the Pale and Winterhold. Villages
bring their villagers, cats and iron golems, outposts their pillagers. Home has sheep, pigs, chickens and cows
(placed with the land too, `HomeChunkGenerator.spawnOriginalMobs`) and the same monsters at night. The Nether is
vanilla. Phantoms, pillager patrols and wandering traders stay off: Minecraft runs them for the overworld, the
mirror.

**Day and night.** Tamriel Wilds and Home keep the mirror's clock (their dimension types' `default_clock` is
`minecraft:overworld`; Minecraft's own time stays frozen), so time sync goes on while the host is out there: the
sun follows Skyrim's hour, which runs on while Skyrim's player is parked (a Skyrim day lasts 72 real minutes at the
default timescale, its night about 30), monsters come out at Skyrim's night, and a bed passes Skyrim's night as in
the mirror. The Nether has no clock. With `time_sync` off the clock stays where it is, so night never comes and
monsters spawn only in the dark underground.

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
/execute in skycraft:tamriel_wilds run locate structure #minecraft:village
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

Things to know: the Skyrim coupled features stand down in these worlds (the Minecraft view, `SkyDimension`), except time.
Vanilla Nether travel never happens in any of them: every Nether portal belongs to the portal network.

## Portals into the worlds

The portal network ([Portal network](#feature-map)) is the way into the Seed family's worlds and out again.
Everything is kept in the world's `skycraft-portals.txt` with the rest of the network and is under the
`portals` switch; travel into another world also needs `minecraft_view` on (with the view off those worlds
would act like the mirror). Only the host travels.

| Way in | Where it opens | Partner | Back |
|---|---|---|---|
| Daedra Heart on a lit portal in Skyrim (spent) | The Nether: a spot found once near 0, 0 between y 64 and 100 with solid ground and no lava, fire or water within two blocks (a pocket is carved at y 80 if none), recorded as `arrival minecraft:the_nether` | Built at the arrival, facing south | Step into the partner |
| Tonal Key on a lit portal in Skyrim (spent) | Tamriel Wilds: the surface a dozen blocks south of the Dwemer tower (`Worlds.TAMRIEL_WILDS_ARRIVAL`), facing north at it | Built at the arrival | Step into the partner |
| A lit portal at the Time-Wound with an Elder Scroll in Skyrim's inventory | Home: `Worlds.HOME_ARRIVAL` in front of Steve's camp | None: one way, the story ends there | Seamstone |
| Seamstone on any lit portal | Any place you've been: the arrival points of the worlds you've visited and every portal in the network | The portal itself, for one trip | Seamstone again |

**Partner portals.** A key used on an unbound portal in the mirror builds a 2 by 3 portal in an obsidian
frame just behind the world's arrival point (`PortalPlan.behind`), so you step out onto it facing the
arrival's way: it seals lava, water and fire within a block of the work, clears two rows of air in front,
gives them a floor (obsidian where the ground isn't solid) and lights the frame the way flint and steel
would (`PortalShape`). Each key makes its own pair: a second key builds the next partner five blocks along
from the first (`PortalPlan.sites`). The partner is recorded with its dimension (`<id> minecraft:the_nether
...` in the store) and bound to the portal in Skyrim like a soul gem pair; breaking either frame breaks the
bond, and the next key builds a new partner.

**The way back.** Stepping into a partner (or a Seamstone portal opened onto a Skyrim place) takes you back
to the portal in Skyrim. If Skyrim's parked player is in that portal's Skyrim world and within 64 blocks,
Minecraft moves its player into the mirror in front of it and Skyrim's follows (the Minecraft view's
handover: Minecraft leads). Otherwise Skyrim goes first: `kEvPortalTravel` moves the parked player to the
portal behind a loading screen (Whiterun's portal while the body waits in Riften, an interior, Solstheim),
and once SkyState shows them there Minecraft moves its player into the mirror at the same spot. Skyrim then
sees its player came back in another world than the one it parked them in and leads the handover, putting
Minecraft's player where its own already stands. Skyrim not arriving within 45 seconds calls the trip off.

**The Time-Wound.** Skyrim.esm's `MQ206TimeWoundRef` (0x000430A3) stands in Tamriel (0x3C) at 56044, -47760,
36055 Skyrim units, about 800.6, 515.1, 682.3 in Minecraft. A portal whose middle is within 16 blocks of it
(flat, and 16 up or down) opens one way onto Home while `SkyState.carried` has `kCarryElderScroll`: the
Skyrim plugin sets it once a second when the player's inventory holds an Elder Scroll (Skyrim.esm
`DA04ElderScroll` 0x02D513 and `DA04ElderScrollFurled` 0x048782; Dawnguard.esm `DLC1ElderScrollDragon`
0x0126DC, `DLC1ElderScrollSun` 0x011A13, `DLC1ElderScrollBlood` 0x0118F9). The scrolls are quest items, so
they never convert and stay in Skyrim; the scroll isn't used up. Without one, standing in the portal says
what it wants.

**Seamstone and the ending.** In Home, mining any block a pickaxe or shovel takes past 12,550,821 on either
axis (the Far Lands' strata, the crack in front of the camp too) drops a Seamstone for a player who has none.
The first Seamstone a player picks up is the ending: the title "Past the Far Lands", a line under it, a few
lines in chat (the story lines are `[PLACEHOLDER]` text in `portal/Ending`), and a lit portal opens in the
mouth of the crack facing the camp, so there's always a way out of Home. From then on Seamstone held to any
lit portal, anywhere, opens a chest (a vanilla chest screen, so the client needs nothing new) listing every
place you've been: the arrival points of Home, Tamriel Wilds and the Nether once you've visited them, and
every portal of the network (Skyrim places where you stood in a portal or bound one, partners, the crack),
at most 54. Click one, then step in within five minutes: the portal opens onto it for one trip. The stone
isn't spent.

**Lighting.** Vanilla lights Nether portals only in the overworld and the Nether. `BaseFireBlockMixin` lets
obsidian frames light in Tamriel Wilds and Home too, so Seamstone works on portals you build there. A
portal outside the network leads nowhere in every world (`NetherPortalBlockMixin`): vanilla would send it
into the mirror at scaled coordinates (from the Nether) or into the Nether (from the others).

Tested on a headless 26.3 server (Fabric Loader 0.19.5, Fabric API 0.161.0, the mirror world from the Worlds
test): the Nether arrival came out at 0.5, 76, 0.5; partner portals built and lit at it (and a second one
five blocks along), at the Dwemer tower (on Eastmarch sulfur at y 67) and in the crack, each with all 6
portal blocks, all 14 frame blocks, a solid floor under the exit and no lava, fire or water round it; fire
in an obsidian frame lit a portal in Tamriel Wilds and in Home; and a pig standing in a portal in the
Nether, Tamriel Wilds and Home stayed where it was (the network was asked and gave no destination).

**Try it** (cheats on, the host): in Skyrim's console `player.additem 0003AD5B 1` gives a Daedra heart
(Skyrim.esm `DaedraHeart`; it converts to a Minecraft Daedra Heart), or in Minecraft `/give @s
skycraft:daedra_heart`; `/give @s skycraft:tonal_key`; `/give @s skycraft:seamstone`. For the ending,
`player.additem 0002D513 1` in Skyrim gives the Elder Scroll (it stays in Skyrim) and `player.moveto 0009E096`
takes you to the summit (`MQ305ReturnToTamrielMarker`, nine blocks from the Time-Wound; the map marker
`000162C4` is twelve blocks off). Build and light the frame within 16 blocks of the wound.

## Companions

Minecraft's taming rules on Skyrim's animals, so a Minecraft player's habits work on Skyrim's creatures
(switch `companions`). Skyrim decides what each actor is and whose it is and says so in the actor table
(`AnimalKind` and the companion bits); Minecraft rolls the taming and sends `kEvCompanion`; Skyrim makes it so
with its own means and checks every request (the actor is alive, loaded, within 64 blocks, of the kind
Minecraft named, not essential, not summoned, nobody riding it).

**Which animals.** By race editor ID (`skse/src/AnimalKinds.h`, unit tested in `skse/tests/AnimalKindsTest.cpp`):
werewolves and werebears never; "horse" is a horse; "dog", "husky" and "hound" are dogs; "wolf" is a wolf.
So DogRace, DogCompanionRace, WolfRace, HorseRace, Dawnguard's huskies and death hounds are covered, and so are
most mods' races. Foxes, sabre cats, trolls and the like are not.

| You do | On | What happens (vanilla 26.3's numbers) |
|---|---|---|
| Right click with a bone | a dog or wolf that isn't yours | The bone is eaten; one time in three (`Wolf.tryToTame`) it's tamed: hearts, and "Wolf is tamed and sits". Otherwise smoke. One you hit in the last 30 seconds is angry and won't take it |
| Right click with wheat, sugar, an apple, a carrot, a golden carrot or a golden apple | a horse that isn't yours | Eaten; its temper rises as `AbstractHorse.handleEating` says (3, 3, 3, 3, 5, 10), then the meal counts as a mount attempt (`RunAroundLikeCrazyGoal`): tamed if a roll under 100 is below the temper (hearts, "is your horse now"), else it shies (smoke, angry neigh) and gains 5 temper. About five apples on average. Hay only heals a hurt horse. Temper is remembered per horse until Minecraft restarts |
| Right click with an empty hand | a dog, wolf or other animal companion | Sits (waits) or follows, by turns; the action bar says which |
| Sneak and right click with an empty hand | your horse | Stays or follows |
| Right click with an empty hand | your horse | Rides it: the click goes to Skyrim's activation (click to activate) and Skyrim takes the player while riding |
| Right click with its food | a hurt companion | Heals it: meat, fish or rabbit stew heal a dog or wolf twice their nutrition of a wolf's 40 health (cooked beef 40%); horse food heals as for a Minecraft horse of 22.5 health (hay 89%, an apple 13%). Nothing is eaten at full health |

**Skyrim's side** (`Companion.cpp`). A tamed dog or wolf becomes Skyrim's animal follower exactly as a stray dog
does when you ask it along: SkyCraft calls the vanilla `DialogueFollowerScript.SetAnimal` on the DialogueFollower
quest (found by editor ID), which ranks it an Ally of the player, makes it a teammate and puts it in the quest's
animal alias, whose packages follow the player, through load doors too, and fight beside them. Wild wolves also
stop their fight and are made no more than Aggressive (they never pick fights with neutral folk). Sitting is the
`WaitingForPlayer` actor value those packages read, set directly (the vanilla wait without its three-day timer: a
sitting Minecraft wolf waits as long as it takes). A tamed horse becomes the player's own (ownership, as a bought
horse) and keeps to its own ways (a stable's horse stays by its stable) until told to follow; following puts it in the
same animal slot. Skyrim keeps one animal follower: whichever companion you last told to follow has the slot, and the
one it took it from stays yours but is left behind (it goes about its own business near where it was, and shows as
sitting in Minecraft). What
Minecraft tamed is kept in the SKSE co-save (record `CMPN`, `CoSave.cpp`), so it's still tamed when that game is
loaded again; a game saved before this build has no record and nothing is tamed. Counted as tamed already: the
actor in the animal slot (Meeko, Vigilance, a stray, bought or Hearthfire dog, a husky), any dog, wolf or horse that
is the player's teammate, and horses the player owns. Barbas counts only while Skyrim makes him a teammate or he
holds the slot; on his own quest he is essential and his quest leads him, so a bone does nothing to him.

**In Minecraft** the stand-in of a companion is named "Meeko (tamed)" (stand-ins aren't drawn, so the name shows in
Minecraft's own messages and logs; a collar tint can't be shown, Skyrim draws the animal), the action bar answers
each order, hearts and smoke are Minecraft particles (drawn in Skyrim like any), and a companion's death is told in
chat, as Minecraft tells a pet's owner. Only the host tames and orders: a guest's clicks on the host's animals are
left alone.

**Riding.** Mounting already works while Minecraft drives the player: a right click on a horse with an empty hand
(or G) is Skyrim's activation, and once Skyrim's player is on a mount Skyrim takes the player ("Skyrim takes the
player (riding)" in SkyCraft.log): Skyrim's controls and camera ride the horse, and Minecraft picks the player up
again where they dismount. Riding your tamed horse is riding your own; riding an untamed one is stealing it, as in
Skyrim. Not played yet with this feature.

**Try it** (cheats on). Skyrim console: `player.placeatme 00023ABE` (a Wolf; `00023ABF` an Ice Wolf),
`player.placeatme 00023A92` (a Dog), `player.placeatme 00109487` (a Stray Dog), `player.placeatme 00109E3D` (a
stables horse, unowned when placed); `player.moveto 000D95EA` (Meeko, at his shack), `player.moveto 0009A7AB`
(Vigilance, Markarth stables). Minecraft chat: `/give @s minecraft:bone 16`, `/give @s minecraft:cooked_beef 8`,
`/give @s minecraft:apple 16`, `/give @s minecraft:golden_carrot 8`, `/give @s minecraft:hay_block 2`.

1. Place a wolf and let it come at you. Right click it with bones until hearts show. It stops fighting, sits, and
   both logs have a `SkyCraft-companion` line saying it's tamed. In the Skyrim console (click it): `getplayerteammate`
   is 1, `getav WaitingForPlayer` is 1, `getav Aggression` at most 1; `sqv DialogueFollower` shows it in the animal
   alias.
2. Right click it with an empty hand: "Wolf follows you"; walk off, through a load door: it follows. Again: it sits.
3. Hit a fresh wolf, then feed it a bone at once: nothing (angry for 30 seconds). Feed one to a Dog: the same odds.
4. Hurt your wolf (console, click it: `damageav health 50`), feed it cooked beef: health goes up 40% of its maximum
   (`getav health`, and the log line `fed ... health a -> b`). At full health beef stays in your hand.
5. `player.moveto 000D95EA`, talk to Meeko and ask him along: in Minecraft an empty-hand click sits him and makes him
   follow, by turns; his stand-in is "Meeko (tamed)". Tame a wolf now: the wolf takes the slot (sitting), Meeko
   stays yours but behind; tell Meeko to follow and the wolf is the one left behind.
6. Place a horse; feed it apples until hearts (about five). Skyrim's prompt over it no longer marks riding it a theft
   (no red text), and an empty-hand right click rides it. Sneak and right click: "follows you", and it follows on foot;
   again: "waits here".
7. Save, load the save: the tamed wolf and horse are still "(tamed)"; SkyCraft.log says how many the co-save had.
   Load a save from before the taming (or before this build): nothing is tamed, no errors.
8. Kill a tamed companion (console `kill`): Minecraft's chat says "Wolf died".
9. Set `companions = off` in `skycraft-features.txt` and restart Minecraft: bones and empty hands do what they did
   before (Skyrim's activation), and no stand-in is named "(tamed)".

Headless: `SKYCRAFT_COMPANION=1 python tools/fake_skyrim.py` adds a wild wolf five blocks west of the start and
answers `kEvCompanion` as Skyrim would (tamed and sitting, follow and wait, healing).

## Skooma and addiction

Skooma is Skyrim's drug, and Minecraft gets its high and its hold (switch `skooma_addiction`, numbers in
`skycraft-skooma.txt`). Item conversion's potion rules turn Skyrim's Skooma into a Minecraft potion of Skooma High
(`minecraft:potion{skycraft:skooma:0:180 ...}`, amplifier 1 for Redwater Skooma) and Fortify Sneak potions into
Stealth (`skycraft:stealth`); the effects themselves are always there, the switch only governs the addiction.

**The effects** (`skooma/SkyEffects`; each level adds again, as vanilla's attribute effects do):

| Effect | Id | Per level | |
|---|---|---|---|
| Skooma High | `skycraft:skooma` | +20% movement speed (Speed's), +0.1 jump strength (Jump Boost's: about 1.8 blocks high at I, 2.5 at II) with +1 safe fall distance (Jump Boost's, so the higher jump doesn't hurt), +0.15 sneaking speed (Swift Sneak's), stealth | Beneficial, Skooma's purple. Getting it is a dose |
| Stealth | `skycraft:stealth` | +0.15 sneaking speed, stealth | Beneficial. No addiction |
| Skooma Withdrawal | `skycraft:skooma_withdrawal` | -10% movement speed, -10% attack speed, -3% jump strength (III still clears a block), food exhaustion at half Hunger's rate | Harmful. Given by the addiction, endless until relieved or cured |

**Stealth.** While a player crouches under Skooma or Stealth, the visibility mobs target by
(`LivingEntity.getVisibilityPercent`, which already has crouching's 0.8, invisibility and mob heads; `TargetingConditions`
multiplies a mob's range by it, never under 2 blocks) is scaled by 0.8 x 0.75^level: 0.6 at I, 0.45 at II, 0.34 at III,
never under 0.2 (`SkoomaRules.stealthScale`, `mixin/LivingEntityStealthMixin`). A zombie that would spot a crouching player
at 28 blocks spots one on Skooma at 17, on Redwater at 13. Under both, the higher level's stealth counts (their sneaking
speeds do add up). This is Minecraft's mobs' sight: Skyrim's people and creatures in the mirror notice the player by
Skyrim's own detection.

**The addiction** (`skooma/SkoomaRules`, pure Java and unit tested; `skooma/SkySkooma` runs it once a second for every
player, host and guests, in every dimension). Its clock is the world's game time, which keeps running while SkyCraft
holds the sun still (`/time` doesn't move it; `/tick sprint` does).

- A dose is any time a player gets Skooma High: a potion, a splash or lingering cloud, a command. Skooma again within a
  minute of a dose is the same dose (a lingering cloud gives it every second; two bottles in a row are one binge).
- Tolerance: each dose adds one, and it halves every half day (`tolerance_half_life`).
- Each dose may hook a player who isn't addicted: `chance` 5%, plus `chance_per_tolerance` 15% for each point of tolerance
  before the dose, plus `chance_per_strength` 15% for each level above I (Redwater). The first ever dose: 5% (Redwater 20%);
  one every two days stays near 6%; three a third of a day apart: about one in three to be hooked by the third; another
  bottle a minute after one: 19%. Hooked: "You have become addicted to Skooma." in chat.
- The comedown: when a high runs out by itself (not when milk or a command takes it), Slowness and Hunger for 30 seconds
  (`comedown`), level II after Redwater. "The Skooma wears off."
- Withdrawal: an addict a day (`withdrawal_after`) without a dose gets Skooma Withdrawal I, a level worse every further day
  (`withdrawal_step`) up to III, with its first line on the action bar ("You crave Skooma.", "Your hands won't stop
  shaking.", "Every bone in your body aches for Skooma."), then now and then (about every two minutes) another craving line
  and a spell of nausea (8 seconds, about every five minutes at I, every hundred seconds at III). Milk (or anything clearing
  effects, dying too) takes it away; it comes back three minutes later (`symptoms_return`) while the addiction lasts.
- Relief and relapse: a dose during withdrawal ends it at once ("The Skooma steadies your hands."). Every dose taken while
  addicted is a relapse: it puts the cure off by half a day (`recovery_per_relapse`), at most four times.
- Recovery: three days clean (`recovery_after`, plus the relapses') since the last dose cure the addiction ("The craving is
  gone."), but only once the player has lived through a day of withdrawal while playing (`recovery_endured`). Played
  straight through, that's simply day 3 (withdrawal I on day 1, II on day 2). Time away while the world runs on (a guest
  logged off while the host plays) counts as clean time, but the withdrawal has to be sat through on coming back, at the
  level the days away earned: there's no sleeping it off. Milk masks the symptoms but doesn't stop the days. A cured
  player keeps their dose count and tolerance; the next dose rolls as any other.

Each player's habit (doses, the last dose's game time, tolerance, addicted, relapses, withdrawal lived through) is a Fabric
data attachment on the player (`skycraft:skooma`): saved with the player and kept when they die. With the switch off,
doses aren't counted and any withdrawal the addiction gave is taken away; the habit stays saved for when it's back on.

**`skycraft-skooma.txt`** (ticks: 20 a second, 24000 a Minecraft day):

| Setting | Default | |
|---|---|---|
| `chance` | 0.05 | A dose's chance to hook |
| `chance_per_tolerance` | 0.15 | Added for each point of tolerance |
| `chance_per_strength` | 0.15 | Added for each level above I |
| `tolerance_half_life` | 12000 | Tolerance halves in this long |
| `withdrawal_after` | 24000 | Withdrawal I this long after the last dose |
| `withdrawal_step` | 24000 | A level worse every this long after |
| `recovery_after` | 72000 | Clean this long cures |
| `recovery_per_relapse` | 12000 | Added to it for each relapse (up to four) |
| `recovery_endured` | 24000 | Withdrawal to live through before the cure |
| `symptoms_return` | 3600 | Symptoms back this long after milk |
| `comedown` | 600 | Slowness and Hunger this long when a high wears off |

**Seeing it.** `/skyskooma` (any player, read only) prints your habit: doses, how long since the last, tolerance, the next
dose's chance, or for an addict the withdrawal level, when the cure is due and how much withdrawal is lived through. The
log has a `SkyCraft-skooma` line for every dose (level, tolerance, the chance it had and whether it hooked), each
withdrawal level reached, symptoms cleared by milk and the cure, and the settings at start.

**Try it** (cheats on):

1. `/effect give @s skycraft:skooma 60 0`: faster, a 1.8 block jump with no fall damage from it, quicker crouching. Crouch
   near a zombie at night (or `/summon minecraft:zombie ~10 ~ ~`): it notices you closer than without. `/skyskooma`: one
   dose, tolerance 1.00. The log: `SkyCraft-skooma: <you> took Skooma (level 1): dose 1, ...`.
2. Let it run out: "The Skooma wears off.", Slowness and Hunger for 30 seconds. Drink milk during another high instead: no
   comedown.
3. `/effect give @s skycraft:skooma 60 1` (Redwater) a minute apart until "You have become addicted to Skooma." (or set
   `chance = 1` and restart).
4. `/tick sprint 1d`: withdrawal I, "You crave Skooma.", slower, hungrier. `/tick sprint 1d` again: II. Drink milk: it goes,
   and comes back three minutes later. `/effect give @s skycraft:skooma 10 0`: "The Skooma steadies your hands.", withdrawal
   gone; `/skyskooma` shows one relapse.
5. `/tick sprint 4d`: on the way, "The craving is gone." (three days clean, plus half a day for the relapse; `/skyskooma`:
   not addicted). For a quicker run, shorten the times in `skycraft-skooma.txt` (say `withdrawal_after = 600`,
   `withdrawal_step = 600`, `recovery_after = 2400`, `recovery_per_relapse = 0`, `recovery_endured = 600`,
   `symptoms_return = 200`) and restart.
6. Die while addicted: still addicted after respawning (`/skyskooma`). A guest gets all of it too.
7. `/effect give @s skycraft:stealth 60 1` and crouch: quicker crouching, the same stealth as Redwater, no dose
   (`/skyskooma` unchanged).
8. `skooma_addiction = off` in `skycraft-features.txt`, restart: the effects still work, no dose is counted, no withdrawal.

Not played yet: the stealth distances against real mobs, how the icons and names look in the HUD and inventory, and the
balance of the numbers.

## Skills

Minecraft play already trains Skyrim's skills (README, "Skyrim progression"); these go the other way: the host's
Skyrim skill levels make Minecraft better. Each has its switch (`sneak_skill`, `archery_skill`, `smithing_skill`,
`enchanting_skill`, `dragon_souls`, `alchemy_brewing`), and the numbers are in `skycraft-skills.txt`.

**Where the levels come from.** Once a second (and at once after a game loads, never during a loading screen) the
Skyrim plugin writes the player's 18 skill levels into SkyState, one byte each in ActorValue order (One-Handed to
Enchanting): Skyrim's current value, the one its own formulas use, rounded and clamped to 0 to 255. With them go the
Dragon Souls the player holds and `skillsLoad`, a number that's new each time a game is loaded or started
(`WorldState::Skills`). Minecraft reads them once a second on the server (`skills/SkySkills`; kept through a loading
screen, forgotten when Skyrim goes away). A level of 0 is unknown (a plugin from before the skills, or no game loaded
yet) and gives no bonus.

**Host only.** Only the host's own Skyrim is on the link, so only the host gets the bonuses, as only the host's Skyrim
is trained (`SkyCombat.trainDefence`). A guest plays with vanilla numbers; giving guests theirs needs each guest's
Skyrim levels sent to the host's world (a SkyNet packet from the guest's own link), a follow-up. The bonuses work in
every dimension: they're the character's.

**What the levels give** (`skills/SkillRules`, unit tested). Every bonus grows in a line from 15, a new character's
skill (none), to 100 (the full bonus), and stops there (a fortified skill above 100 counts as 100).

| Skill | Bonus | At 15 | At 50 | At 100 | Where |
|---|---|---|---|---|---|
| Sneak | A crouching player is noticed from this share of the distance mobs otherwise would (after crouching's own 0.8), times Skooma's or Stealth's (0.6 at I, 0.45 at II), never under 0.2 in all | 1 | 0.84 | 0.6 (0.36 with Skooma I, 0.27 with Redwater) | `mixin/LivingEntityStealthMixin` on `getVisibilityPercent`, which mobs' targeting scales its range by |
| Archery | Arrows shot from a bow or crossbow hit this many times as hard (their base damage, once as they're made; Power adds on top) | x1 | x1.21 | x1.5 | `mixin/ArcheryMixin` on `ProjectileWeaponItem.createProjectile` |
| Smithing | Anvil repairs and combining cost a level less for every 25 above 15, never under 1. A rename alone costs what it did, and a "Too Expensive!" one stays so | 0 less | 1 less | 3 less | `mixin/AnvilSmithingMixin` on `AnvilMenu.createResult` |
| Enchanting | The enchanting table counts a bookshelf more for every 6 above 15, 15 in all at most (vanilla's limit), so a table with few shelves offers what a full one would | 0 more | 5 more | 14 more | `mixin/EnchantingTableMixin`, `mixin/EnchantingCostMixin` on `EnchantmentHelper.getEnchantmentCost` |
| Alchemy | A potion taken out of a brewing stand lasts 0.5% longer for every level above 15 | x1 | x1.175 | x1.425 | `mixin/BrewingTakeMixin` on `Slot.mayPickup`, `skills/SkyAlchemy` |

Smithing on the anvil: Minecraft takes the cost shown when you take the result, so the host's cheaper cost is what's
paid. The host's own screen works the cost out with the same levels (its game runs the server). Enchanting: the offers'
levels are rolled with the larger count; what they enchant is vanilla's for that level. A 30 level offer, vanilla's
best, needs 15 bookshelves; at Enchanting 100 one shelf does it.

**Soul gems for lapis** (`enchanting_skill`, anyone, any level): a Filled Soul Gem (what a filled Greater, Grand or
Black Skyrim soul gem becomes, the portal network's key) goes in the enchanting table's lapis slot (shift-click puts it
there too, `mixin/EnchantingTableMixin`) and stands in for lapis lazuli one for one: the first offer takes one gem, the
third three, as with lapis (`skills/SoulGemFuelSlot` takes the vanilla slot's place, `mixin/EnchantingLapisSlotMixin`).
In Skyrim a filled soul gem is what powers an enchantment.

**Dragon souls.** When Skyrim's Dragon Souls count goes up within the same game (a soul absorbed), the host gets 5 XP
levels a soul, "Dragon soul absorbed: +5 levels" in chat and the level-up sound. The count last seen is kept on the host
(Fabric attachment `skycraft:dragon_souls`, saved with the player in that world, kept on death), with the game it was
seen in (`skillsLoad`, `SkillRules.SoulLedger`): reconnecting or restarting Minecraft never pays twice; another game (a
save loaded, a new character, the first look at a Skyrim) only records its count, so loading a save with ten souls pays
nothing; souls spent on shouts are only taken off the count, and the next soul absorbed pays again. With the switch off
the count is followed and nothing is paid. A soul absorbed while Minecraft is away is paid when it reconnects to the same
Skyrim game, unless Skyrim loaded a game meanwhile.

**Brewing Stealth** (`alchemy_brewing`). Skyrim's Purple Mountain Flower (Fortify Sneak) becomes an allium with item
conversion, and an allium brews an awkward potion into a Potion of Stealth (SkyCraft's own effect: quicker crouching and
the Stealth above), as a vanilla potion goes: 3:00; redstone makes it 8:00, glowstone Stealth II for 1:30; gunpowder and
dragon's breath make splash and lingering ones, and fermented spider eye does nothing to it. The potions
(`skycraft:stealth`, `long_stealth`, `strong_stealth`) are always registered; the recipes need the switch. Skooma isn't
brewed here (`skooma_brewing`). Alchemy's longer potions use Minecraft's own `potion_duration_scale` component (a
lingering potion's 0.25, times Alchemy's): the potion stays the potion it is, so it brews on as before, and the tooltip
shows the longer time. Taking it again with the same Alchemy changes nothing; a potion a guest takes stays as brewed.

**`skycraft-skills.txt`:**

| Setting | Default | |
|---|---|---|
| `sneak_at_100` | 0.6 | Share of the distance a crouching player is noticed from at Sneak 100 (0.2 to 1) |
| `archery_at_100` | 1.5 | Arrow damage multiplier at Archery 100 (1 to 10) |
| `smithing_per_level` | 25 | Smithing levels above 15 for each anvil level less (1 to 100) |
| `enchanting_per_bookshelf` | 6 | Enchanting levels above 15 for each bookshelf more (1 to 100) |
| `levels_per_soul` | 5 | XP levels for each dragon soul absorbed (0 to 100) |
| `alchemy_per_level` | 0.005 | How much longer potions last for each Alchemy level above 15 (0 to 0.1) |

**Seeing it.** `/skyskills` (any player, read only) prints the host's 18 levels and what Sneak, Archery, Smithing,
Enchanting, Alchemy and the Dragon Souls give now (a guest is told only the host's are known). Minecraft's log has a
`SkyCraft-skills` line with the levels whenever Skyrim loads a game, and one for each of the first ten arrows, anvil
works and enchanting tables a skill changed, the first ten potions taken, and every dragon soul paid; SkyCraft.log has
`SkyCraft-skills: game <n>: One-Handed ...` at each load and `dragon souls a -> b` when the count changes.

**Try it** (cheats on, the host). Skyrim console: `player.setav sneak 100`, `player.setav archery 100`,
`player.setav smithing 100`, `player.setav enchanting 100`, `player.setav alchemy 100`, `player.modav dragonsouls 1`.
Then `/skyskills` in Minecraft shows them (within a second).

1. Sneak: at night, `/summon minecraft:zombie ~20 ~ ~` and crouch: at Sneak 15 it comes for you (a zombie spots a
   crouching player at 28 blocks), at 100 it doesn't until it's within about 17. `/effect give @s skycraft:stealth 60 0`
   on top: about 10.
2. Archery: shoot a pig or a Skyrim NPC with a full bow draw at Archery 15 and at 100: about half again the damage (the
   log line gives the base damage). A crossbow too.
3. Smithing: `/give @s minecraft:anvil`, `/give @s minecraft:iron_sword` (damage it a little, or two damaged swords),
   `/give @s minecraft:iron_ingot 4`: the repair cost is 3 less at 100 than at 15, never under 1; renaming alone is 1.
4. Enchanting: `/give @s minecraft:enchanting_table`, `/give @s minecraft:lapis_lazuli 9`, `/xp add @s 30 levels`, no
   bookshelves: at 100 the bottom offer for a book is 28 or 29 levels, at 15 at most 8, and one bookshelf makes it 30.
   `/give @s skycraft:filled_soul_gem 3`: the gems go in the lapis slot and pay for an offer as lapis would.
5. Dragon souls: `player.modav dragonsouls 1` in Skyrim's console: "Dragon soul absorbed: +5 levels". Quit Minecraft to
   the title and back in: no second payment. Load a save: nothing paid; `player.modav dragonsouls 1` again pays.
6. Alchemy: `/give @s minecraft:brewing_stand`, `/give @s minecraft:blaze_powder 2`, `/give @s minecraft:nether_wart 3`,
   `/give @s minecraft:sugar 3`, `/give @s minecraft:glass_bottle 3` filled with water: Swiftness taken out at Alchemy 100
   says 4:16 instead of 3:00. `/give @s minecraft:allium 3` on awkward potions: Potions of Stealth; redstone on one: 8:00.
7. Each switch off in `skycraft-features.txt`, restart: that bonus is gone (the soul gem slot and the Stealth recipes go
   with theirs).

Headless: `SKYCRAFT_SKILL=100 python tools/fake_skyrim.py` gives every skill 100; `SKYCRAFT_SOULS=2
SKYCRAFT_SOUL_EVERY=30` absorbs a soul every 30 seconds.

Not built or played yet: everything above is Written (the rules are unit tested). The Minecraft calls the mixins rely on
are listed in `.github/probe.txt` to check against 26.3.

## Planned

| Feature | Notes |
|---|---|
| Farming on Skyrim soil | May already work with dug dirt; needs a test first |
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
| `SkyState.carried` (offset 0x64, was `reserved`), `CarriedFlags` `kCarryElderScroll` (bit 0) | Skyrim to Minecraft | Portal network: the player's inventory holds an Elder Scroll (a quest item that never converts), so the portal at the Time-Wound opens onto Home. Rechecked once a second. Added within 13: the field was reserved and always 0, so an older plugin reads as "carries nothing" and an older Minecraft ignores it |

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

Companions added these within 13, without a version bump (an older half ignores them: an older plugin never sets the bits
and drops the event type, an older Minecraft ignores the bits):

| Addition | Direction | Used by |
|---|---|---|
| `ActorFlags` `kActorCompanion` (bit 5), `kActorCompanionWaiting` (bit 6), in `ActorRecord.flags` (offset 4 of each 64-byte record; records from 0x12040) | Skyrim to Minecraft | The actor is the player's animal; it isn't following now (told to wait, or left behind because another holds the animal follower slot) |
| `ActorRecord.flags` bits 8 to 11: `AnimalKind` (`kActorKindShift` 8, `kActorKindMask` 0xF00; 0 none, 1 dog, 2 wolf, 3 horse) | Skyrim to Minecraft | Which taming rules apply, from the race (`AnimalKinds.h`) |
| `kEvCompanion` (14), `CompanionAction` 1 tame, 2 follow, 3 wait, 4 heal | Minecraft to Skyrim | `McEvent` (32 bytes from 0x17080): formId @4 = the actor, flags @24 = the action, weapon @28 = the AnimalKind Minecraft saw (Skyrim refuses a taming of another kind), a @8 = heal: the share of maximum health to restore. Event 14 because 12 and 13 are the Minecraft view's |

Gear drops added these within 13, without a version bump. An older plugin never sends worn items; an older
Minecraft skips them, since every protocol 13 jar drops an item event whose count is under 1 before reading anything
else (so it can't hand worn gear to the player, which its rules without a `from` condition, like `tag arrow`, would):

| Addition | Direction | Used by |
|---|---|---|
| `ItemSource` `kSrcWorn` (5) in `ItemEvent.source`, with `ItemEvent.count` negated | Skyrim to Minecraft | Gear drops: one event per piece an actor in the actor table wore (weapons, shield, armor and clothes, ammo with the quiver's count) the frame it's first seen dead, container = the actor. Never given to the player, never answered with `kEvTakeItem` |
| `ItemEvent.tags` bits 24 to 27: `ItemKind` (`kTagKindShift` 24, `kTagKindMask` 0xF000000; 0 unknown, 1 sword, 2 dagger, 3 war axe, 4 mace, 5 greatsword, 6 battleaxe or warhammer, 7 bow, 8 staff, 9 crossbow, 10 hand to hand, 11 helmet, 12 cuirass, 13 gauntlets, 14 boots, 15 shield) | Skyrim to Minecraft | Every item event: the weapon's type, or the biped slots armor and clothing cover (body first, then head, hair or circlet, hands or forearms, feet or calves; the shield slot before all). Gear drops picks the piece by it; item conversion's rules don't read it yet. An older plugin sends 0, and an older Minecraft's rules only test the tag bits they name |

Arrows at blocks added this within 13, without a version bump (an older Minecraft ignores the input type; with an older
plugin Skyrim's arrows go through blocks as before):

| Addition | Direction | Used by |
|---|---|---|
| `kInArrowBlocked` (10) in the input ring | Skyrim to Minecraft | A Skyrim arrow or bolt reached a solid Minecraft block, where Skyrim ended it (`Arrows.cpp`): a, b, c = where it reached the block (MC coords x 1024), code = its flight, yaw in the high byte (256ths of a turn from +z toward +x), pitch in the low byte (0 down, 255 up; `Proto.arrowFlight`). Minecraft flies an arrow of its own the last 0.3 blocks into the block, where it sticks (`SkyCombat.stickSkyrimArrow`); it can be picked up |

Skills added these within 13, without a version bump (they were `SkyState.reservedSkills`, always zero: an older plugin
reads as no skills known, no bonus and no souls paid; an older Minecraft ignores the bytes):

| Addition | Direction | Used by |
|---|---|---|
| `SkyState.skills[kSkillCount]` (0x68, 18 bytes; `kSkillCount` 18, `kSkillFirst` 6) | Skyrim to Minecraft | Skills: the player's level in ActorValue `kSkillFirst + i`, One-Handed (6) to Enchanting (23): Skyrim's current value rounded and clamped to 0-255, 0 = unknown. Refreshed about once a second and at once after a game loads, never while loading |
| `SkyState.dragonSouls` (0x7A, u16) | Skyrim to Minecraft | Dragon souls: the player's unspent Dragon Souls (ActorValue `kDragonSouls`) |
| `SkyState.skillsLoad` (0x7C, u32) | Skyrim to Minecraft | Dragon souls: a new number each time a game is loaded or started (never 0 once written; it starts somewhere new each Skyrim run), so Minecraft pays only for a rise within the same game |

## Testing a build

1. Back up the Skyrim saves folder.
2. Install the jar into the instance's `mods` folder and the DLL into `Data/SKSE/Plugins`, keeping
   the previous ones in `dev-backups`.
3. Play the feature tests for the build.
4. Read `Documents/My Games/Skyrim Special Edition/SKSE/SkyCraft.log` and the instance's
   `logs/latest.log`, searching for the log tags above.
