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

Every feature can be switched off in `skycraft-features.txt` (restart Minecraft after editing), or in game with
**SkyCraft settings** in the pause menu ([Settings screen](#settings-screen)).
Minecraft side paths are under `fabric/src/main/java/dev/skycraft/`, Skyrim side under `skse/src/`.

| Feature | Switch | What it does | Minecraft side | Skyrim side | Config | Log tag | Status |
|---|---|---|---|---|---|---|---|
| Kill drops | `kill_drops` | Every Skyrim creature you kill (animals, spiders, chaurus, Falmer, draugr, skeletons, ghosts, atronachs, Dwemer machines, trolls, giants, DLC creatures, dragons) drops what its Minecraft counterpart would, and people carry coin and odds and ends (bandits, Forsworn, necromancers, Silver Hand, vampires, mages, guards and soldiers; contributed by Higor Machado). Drops land above Minecraft blocks and on Skyrim's ground, never under it: a rule can roll a vanilla mob's loot table (`table minecraft:entities/spider`) as that mob's death at your hands, so vanilla does counts, chances, Looting, kill-only drops (spider eyes) and cooked meat; others drop `item min-max` (Looting adds 0 to 1 per level) or `item 1 2.5%` (Looting adds 1% per level). Bosses (dragons, dragon priests) pay more. An unedited old `skycraft-loot.txt` gets the new rules (old file kept as `.old`, `config/Config.read` with old defaults). Full table in `docs/CONVERSIONS.md` | `loot/SkyLoot` (`killer`, `lootingLevel`), `loot/LootRules`, `combat/SkyrimActorEntity` (last player hit) | actor table | `skycraft-loot.txt` | `SkyCraft-loot` | Built |
| Kill XP | `kill_xp` | Credited kills drop XP orbs, more for higher levels | `loot/KillXp`, `loot/SkyLoot` | actor table level | none | `SkyCraft-loot` | Built |
| Gear drops | `gear_drops` | A Skyrim person or creature you kill sometimes drops the Minecraft version of each weapon or armor piece it wore, as a Minecraft mob drops its equipment (26.3's numbers: 8.5% a piece plus 1% per Looting level, randomly worn; arrows as a skeleton's, 0 to 2 plus 0 to 1 per Looting level). Its Skyrim gear stays on the corpse. Your followers and summoned or raised actors never drop. The piece is the one Skyrim types it as (weapon type, armor slot), so Zephyr drops a bow. Table in `docs/CONVERSIONS.md` | `loot/GearDrops`, `loot/GearMap`, `loot/SkyBridge` (worn items never reach the player), `loot/SkyLoot` (credited kill) | `Loot.cpp` (`WatchWorn`, `SendWorn`), `Combat.cpp` (actor table) | `skycraft-gear-drops.txt` | `SkyCraft-loot` | Built |
| Item conversion | `item_conversion` | Food, potions, ores, hides and salvaged gear you loot in Skyrim (weapons and armor of every material salvage into their smithing material) become Minecraft items, and so do torches, tools, kitchenware, Dwemer clutter, Hearthfire building materials, animal parts, alchemy reagents, fish and gems (`+` gives several items for one); quest items never convert. The defaults are a file in the jar (`dev/skycraft/loot/skycraft-items.txt`, too big for a Java string), and an unedited old `skycraft-items.txt` upgrades by itself (`ItemRules.OLD_DEFAULTS`). Full table in `docs/CONVERSIONS.md` | `loot/SkyBridge`, `loot/ItemRules` | `Loot.cpp` (container sink, Take) | `skycraft-items.txt` | `SkyCraft-loot` | Tested |
| More conversions | `item_conversion` | The reviewed conversion proposals: treasure maps, scrolls (the potion of their spell, runes TNT) and the Flames and Healing tomes (books), dishes and DLC ingredients, big pelts, silver, dragon bone, Harming and elemental arrows, armor sets of no smithing material (guards' chainmail, Blades steel, Forsworn leather, ...) when looted or bought, and the gear of materials SkyCraft has no set for (Nordic, Stalhrim, Amber, Madness, ...: the nearest tier when bought, salvage when looted). Every default rule is reachable (`ItemRulesTest.everyDefaultRuleCanBeReached`) | `loot/ItemRules` (the defaults file) | none | `skycraft-items.txt` | `SkyCraft-loot` | Written |
| Enchanted gear | `item_conversion` | Skyrim's leveled enchanted weapons and armor ("Ebony Sword of the Inferno", "Glass Helmet of Eminent Alchemy"; about 4,000 names in 3,000 rules) become the gear of their material's tier with the nearest Minecraft enchantment, looted, bought or picked up, at a level that follows the enchantment word's place in Skyrim's ladder and within Minecraft's own maximum, and only on gear that can carry it: Fire Damage is Fire Aspect on swords and Flame on bows, Absorb Health Sharpness and Mending, Fortify Health Protection, the magic schools Mending, Waterbreathing Respiration and Aqua Affinity, ... Plain gear still salvages or is bought as before. Tables in `docs/CONVERSIONS.md` | `loot/ItemRules` (the defaults file's "Enchanted weapons and armor" section), `loot/SkyBridge` (`enchant`) | none | `skycraft-items.txt` | `SkyCraft-loot` | Written |
| Named artifacts | `item_conversion` | The Daedric artifacts and unique weapons and armor (Dawnbreaker, Mehrunes' Razor, Volendrung, Chillrend, Dragonbane, Windshear, the Ebony Blade, the Mace of Molag Bal, Auriel's Bow, Zephyr, the Nightingale set, the Gauldur Blackblade and Blackbow, the Bloodskal Blade, Miraak's gear, Spellbreaker, the Ebony Mail, Savior's Hide, the Masque of Clavicus Vile, the Dragon Priest masks and about 120 more) become Minecraft gear of the nearest tier with their Skyrim name and the enchantments nearest their own, from anywhere; the Mace of Molag Bal and Volendrung are maces. Staffs, the Wabbajack, the Sanguine Rose, the Skull of Corruption, Azura's Star and the Black Star stay in Skyrim. Table in `docs/CONVERSIONS.md` | `loot/ItemRules` (the defaults file's "Named weapons and armor" section), `loot/SkyBridge` (custom name, `enchant`) | none | `skycraft-items.txt` | `SkyCraft-loot` | Written |
| Clothing and jewelry | `item_conversion` | Looted or bought clothing becomes leather armor for its slot dyed like it (robes chestplates, hoods helmets, gloves leggings, boots boots; mage robes keep Mending); any other clothing goes by the slot Skyrim reports (rule condition `kind`). Plain rings and necklaces melt into gold or iron nuggets plus their gem, circlets are a dyed leather helmet and their gem; enchanted and unique jewelry stays. Drinks are named potions (Saturation, a little Nausea); bought dragon armor is the daedric tier (nothing is netherite); horse armor is Minecraft's. What you make or enchant yourself stays. Rules dye leather with `{#rrggbb}` | `loot/ItemRules` (`KINDS`, `DYEABLE`), `loot/SkyBridge` (`DYED_COLOR`) | `Loot.cpp` (the item's kind, already sent) | `skycraft-items.txt` | `SkyCraft-loot` | Written |
| Potions | `item_conversion` | Every Skyrim potion becomes a Minecraft potion with effects of its own, named like the Skyrim one (health red, magicka blue, stamina green), and every poison a splash potion: 57 alchemy effects map to Minecraft effects (Restore Health is Instant Health, magicka is Absorption, Barter is Hero of the Village, Smithing is Haste, Paralysis is Slowness V, ...), with level and seconds growing with the name's tier (Minor and Weak up to Ultimate, Grand and Prime). Cure Disease and Cure Poison give a milk bucket; the White Phial and the quest poisons stay. Rules write `minecraft:potion{haste:0-2:60-180 #rrggbb "Name"}`, or `minecraft:potion[healing]` for a vanilla potion, also for `splash_potion`, `lingering_potion` and `tipped_arrow`. Long names cut to 27 letters come back whole. Full table in `docs/CONVERSIONS.md` | `loot/ItemRules` ("Potions and poisons" section, `Effect`, `TIERS`, `LONG_NAMES`), `loot/SkyBridge` (potion contents, custom name) | `Loot.cpp` (VendorItemPotion, VendorItemPoison tags) | `skycraft-items.txt` | `SkyCraft-loot` | Built |
| Dungeon chests | `dungeon_chests` | First loot of each dungeon chest also rolls a Minecraft chest loot table | `loot/DungeonLoot` | `WorldState::Place` (dungeon flag), item event chest flag and container id | `skycraft-dungeons.txt`, `skycraft-opened-chests.txt` | `SkyCraft-loot` | Built |
| Merchants | `merchants` | Right click a Skyrim vendor with an empty hand for a villager trade screen; town prices, supply and demand, Speech XP (selling for Skyrim gold and Skooma dealers: below) | `trade/` (SkyTrade, TradeRules, Currency, Demand), `combat/SkyrimActorEntity.interact` | `WorldState::VendorMask`, `WorldState::Place` (town) | `skycraft-trades.txt`, `skycraft-towns.txt` | `SkyCraft-trade` | Built |
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
| Settings screen | none | **SkyCraft settings**, beside **Skyrim destruction** in the top left of the pause menu (O), lists every switch of `skycraft-features.txt` under a header for its part of the game, each an On/Off button with its description as the tooltip, in vanilla's scrolling option list with Done at the bottom. A click saves the switch's line (comments and other lines kept) and counts at once; `minecraft_view` and `natural_spawns` are read once, so they say "(restart)" and take effect after Minecraft restarts. In a friend's world the switches show greyed out with a note: the host's file decides | `config/Features` (`set`, `rewrite`, `needsRestart`), `config/FeatureAreas`, client `FeaturesScreen` | none (`McState.features` carries a switched Skyrim bit the next frame) | `skycraft-features.txt` | `SkyCraft` | Written |
| Conversion messages | `conversion_messages` | The action bar says what each item you loot in Skyrim became: "Steel Sword → Iron Sword", "Iron Ore ×3 → Raw Iron ×3", "Ruby → Gold Nugget ×3 + Diamond", or "Gold ×25 → stays in Skyrim" when the rules keep it ("(quest item)" for one). Pickups within a second of the first share its line, "Steel Sword → Iron Sword, +3 more", so taking everything from a chest is one line; a conversion takes the line from an item that stayed. Names are Minecraft's own (a renamed potion's name) | `loot/ConversionNotes` (the lines and their batching), `loot/SkyBridge` (`note`, `showNotes`) | none | none | `SkyCraft-loot` (each conversion, as before) | Written |
| Bug reports | none | `/skycraft report` (any player, or the server console) packs what a tester should send into `skycraft-report-<date-time>.zip` in Minecraft's game folder: `logs/latest.log`, the three newest crash reports, every `config/skycraft-*.txt` and the Skyrim plugin's `SkyCraft.log` (Documents found the way SKSE finds it, Steam's or GOG's `SKSE` folder), with a `README.txt` of what's in it and what wasn't found, and nothing else. Chat gives the zip's path (click to copy it). A guest's own client writes theirs, of their own games. See [Bug reports](#bug-reports) | `report/` (ReportPlan, SkyReport), the `report_ask` packet's receiver in client `SkyCraftClient` | none (its log is read) | none | `SkyCraft-report` | Written |
| Guide book | `guide_book` | The first time a player (host or guest) is in a world whose Skyrim is linked, a written book "SkyCraft" goes in their inventory (at their feet if it's full): a short page each on crouching as Skyrim's sneak, looting into Minecraft items, G and right click, merchants with an empty hand, portals bound with soul gems, the SkyCraft settings button and `/skycraft report`. It comes once: a data attachment on the player (`skycraft:guide_book`, kept on death) remembers it. With the switch off nothing is given or remembered | `guide/` (GuideBook, GuidePages) | none | none | `SkyCraft-guide` | Written |
| Sneak skill | `sneak_skill` | The host's Skyrim Sneak makes a crouching host harder for Minecraft's mobs to notice: from 1 of the distance at Sneak 15 down to 0.6 at 100, on top of Skooma's and Stealth's (0.36 with Skooma I at Sneak 100), never under their 0.2. See [Skills](#skills) | `skills/` (SkySkills, SkillRules), `mixin/LivingEntityStealthMixin` | `WorldState.cpp` (`Skills`: SkyState skills, once a second), `Game.cpp`, `main.cpp` | `skycraft-skills.txt` | `SkyCraft-skills` (both halves) | Written |
| Archery skill | `archery_skill` | Arrows the host shoots from a bow or crossbow hit harder with Skyrim Archery: x1 at 15 to x1.5 at 100, once on the arrow's base damage as it's shot (Power adds on top; Skyrim NPCs take it too) | `skills/SkySkills`, `mixin/ArcheryMixin` | as Sneak skill | `skycraft-skills.txt` | `SkyCraft-skills` | Written |
| Smithing skill | `smithing_skill` | Anvil repairs and combining cost the host a level less for every 25 Smithing above 15 (3 less at 100), never under 1; a rename alone, and "Too Expensive!", stay as they were | `skills/SkySkills`, `mixin/AnvilSmithingMixin` | as Sneak skill | `skycraft-skills.txt` | `SkyCraft-skills` | Written |
| Enchanting skill | `enchanting_skill` | The enchanting table counts a bookshelf more for the host for every 6 Enchanting above 15 (14 more at 100), 15 at most, so better offers with fewer shelves; a Filled Soul Gem goes in the lapis slot, one gem for each lapis (anyone's) | `skills/SkySkills`, `skills/SoulGemFuelSlot`, `mixin/EnchantingTableMixin`, `mixin/EnchantingCostMixin`, `mixin/EnchantingLapisSlotMixin` | as Sneak skill | `skycraft-skills.txt` | `SkyCraft-skills` | Written |
| Dragon souls | `dragon_souls` | Each dragon soul the host absorbs in Skyrim gives 5 Minecraft XP levels, a chat line and the level-up sound. The count last seen is kept on the player (per world), so reconnecting never pays twice, and a save loaded or a new game only records its count; souls spent on shouts aren't taken back | `skills/SkySkills` (attachment `skycraft:dragon_souls`), `skills/SkillRules` (`SoulLedger`) | `WorldState.cpp` (`Skills`: dragonSouls, skillsLoad; `SkillsReloaded` on every game loaded) | `skycraft-skills.txt` | `SkyCraft-skills` (both halves) | Written |
| Alchemy brewing | `alchemy_brewing` | Potions the host takes out of a brewing stand last longer with Skyrim Alchemy: 0.5% a level above 15 (x1.425 at 100), counted when drunk, splashed or left as a cloud, and in the tooltip. An allium (what Purple Mountain Flower, Skyrim's Fortify Sneak flower, becomes) brews an awkward potion into a Potion of Stealth (3:00; redstone 8:00, glowstone Stealth II 1:30) | `skills/SkyAlchemy`, `mixin/BrewingTakeMixin` | as Sneak skill | `skycraft-skills.txt` | `SkyCraft-skills` | Written |
| Walls hide you | `walls_hide_you` | A Skyrim NPC can't see you through solid Minecraft blocks: when Skyrim's own line of sight says it sees you, the lines from its eyes to your head and to your chest are walked block by block, and with a block on both it doesn't. Detection, its combat and the stealth eye follow; it can still hear you. Every block with a collision shape counts (glass, leaves and fences too), but not the block the NPC's eyes or your head are in (a slab, a carpet). Each NPC's answer is kept six frames. While Minecraft drives the player. See [Combat and stealth](#combat-and-stealth) | `link/SkyLink` (eye and feet, features) | `Stealth.cpp` (every call of `Actor::CalculateLOS` hooked), `NpcBlocks.cpp` (`SolidBetween`), `BlockWalk.h` | none | `walls hide you` in SkyCraft.log | Written |
| Sneak attacks | `sneak_attacks` | A hit while crouching on a Skyrim NPC that hasn't detected you (Skyrim's detection level for you is 0 or less) is a Skyrim sneak attack: Skyrim's base for the weapon it lands as (three times for swords, axes, maces, tools and tridents in hand; twice for arrows, thrown tridents and fists) times your perks through Skyrim's Mod Sneak Attack Mult perk entry point (Backstab, Assassin's Blade, Deadly Aim), "Sneak attack for 6.0x damage!" in the corner, and Sneak experience. An arrow counts by whether you crouch as it lands | `combat/SkyrimActorEntity` (crouching), `combat/CombatRules` | `Combat.cpp` (`SneakAttackMult`, `ApplyHit`), `Stealth.h` | none | `SkyCraft` (", crouching" on the hit line), `sneak attack` in SkyCraft.log | Written |
| Power attacks | `power_attacks` | A fully charged melee hit (attack strength over 0.9, Minecraft's own threshold) that's a sprint hit or a critical hit is a Skyrim power attack: the hit carries Skyrim's power attack flag and at least the stagger of the vanilla weapon it lands as, so Skyrim's own stagger and hit reactions follow and its hit event says power attack. Damage stays Minecraft's. Off: sprint hits and knockback enchantments count as power attacks, as before | `mixin/PlayerAttackMixin` (the swing's charge and sprint), `combat/SkyrimActorEntity`, `combat/CombatRules` | `Combat.cpp` (`ApplyHit`) | none | `SkyCraft` (", power attack" on the hit line), `power attack` in SkyCraft.log | Written |
| Light and stealth | `light_stealth` | How lit you are in Minecraft counts for Skyrim's detection. Skyrim's own light on you (its sun, the hour, the weather, its lamps) is shaded by Minecraft's blocks as far as they shut out Minecraft's sky, and Minecraft's torches, lava and glowstone light you up, whichever is brighter: a tunnel dug into the ground is dark, a torch beside you is about as bright as a Skyrim day. Where no Minecraft blocks are over you (outdoors, Skyrim's interiors) Skyrim's light stands | `client/SkyClient` (`publishEyeLight`: block and sky light at the eyes), `link/SkyLink` | `Stealth.cpp` (the player's light level each frame), `Stealth.h` (`PlayerLight`) | none | `light and stealth` in SkyCraft.log | Written |
| Weather sync | `weather_sync` | Skyrim's weather is Minecraft's: clear and cloudy skies are clear, rain and snow are rain (Minecraft's own snow in Tamriel Wilds' cold biomes and Home's, which share the mirror's weather), a thunderstorm is thunder; indoors Minecraft's stays as it is. The mirror's only biome (the void) has no precipitation, so there it's the sky's darkness that follows, and snow settling: while snow falls in Skyrim, snow layers settle on the tops of Minecraft blocks open to the sky (no block and no Skyrim roof, bridge or cliff over them) near you, never on Skyrim's ground, at most a layer a block each pass, up to `max_layers`, and melt again in clear weather; Skyrim's rain waters farmland open to the sky. The other way: `/weather` (or a mod) changes Skyrim's weather to the nearest of its region's own weathers (rain is snow in a region that only snows, thunder rain where there are no storms), picked by their classification, never by form id; indoors or while a quest holds the weather Skyrim says no and Minecraft's goes back to Skyrim's. Only Minecraft asks, only for changes it didn't make, and Skyrim answers each request once (a sequence number), so the two never ping-pong. See [World](#world) | `weather/` (SkyWeather, WeatherRules), `link/SkyLink` (SkyState weather, McState request) | `Weather.cpp` (`Sky::ForceWeather` on the region's weathers), `WeatherKinds.h` (unit tested), `Game::FeatureOn(kFeatWeatherSync)` | `skycraft-weather.txt` | `SkyCraft-weather` (both halves) | Written |
| Skyrim farming | `skyrim_farming` | A hoe on Skyrim's grass, dirt or mud (the land, or a mound of earth, facing up) tills it: that cell becomes a Minecraft farmland block, dug out of Skyrim's geometry the way digging does (its mesh cut, the pit's walls drawn), its top within half a block of the ground. Seeds, potatoes and carrots plant on it and grow by Minecraft's rules; water within four blocks (or Skyrim's rain, Weather sync) keeps it moist. Skyrim's own crops (wheat, cabbages, potatoes, leeks, gourds, ...) are harvested by activating them, G or a right click (click to activate), with a hoe in hand too: Skyrim tells Minecraft when its crosshair is on a plant to harvest, and the hoe leaves the ground alone; the produce converts like any loot. Needs Skyrim destruction on. See [World](#world) | `farming/` (SkyFarming, FarmRules), client `FarmingClient`, `net/SkyNet` (FarmTill) | `Farming.cpp` (SkyState `aim`) | none | `SkyCraft-farm` | Written |
| Tamriel Wilds villagers | `wilds_villagers` | Villagers in Tamriel Wilds get a Skyrim name by profession, "Hrodmar, Nord Blacksmith", "Livia Mallius, Imperial Cartographer", "Ilvesa, Dark Elf Priest of Arkay" (race and name from the villager, mostly Nords; the title follows its job; a name tag's name stays), and sell Skyrim wares beside Minecraft's trades: armorers SkyCraft armor, weaponsmiths SkyCraft weapons, toolsmiths Skyrim's ingots, leatherworkers leather strips, clerics Skyrim potions as SkyCraft converts them; one at novice, another at journeyman and at master. Villagers anywhere else stay vanilla. See [World](#world) | `villagers/` (SkyVillagers, WildsVillagers), `loot/SkyBridge.converted` | none | none | `SkyCraft-wilds` | Written |
| Shrine blessings | `shrine_blessings` | A Skyrim shrine's blessing (a spell named "Blessing of ..." on the player, new or cast again) gives a Minecraft effect for as long as it has left in real time: 8 game hours at Skyrim's timescale (24 minutes at 20). Talos and Stendarr Resistance, Kynareth Speed, Mara Regeneration, Arkay Health Boost, Dibella Luck, Julianos and Akatosh Haste, Zenithar Hero of the Village, Dawnguard's Auriel Night Vision, any other shrine Luck. Guests get theirs too. See [World](#world) | `blessings/` (SkyBlessings, Blessings), client `InputBridge`, `net/SkyNet` (Blessing) | `Blessings.cpp` (the player's active effects), `BlessingKinds.h` (unit tested), `kInBlessing` | none | `SkyCraft-blessing` (both halves) | Written |
| Selling to merchants | `sell_to_merchants` | Sneak and right click a Skyrim merchant with Minecraft goods in your main hand to sell them for Skyrim gold, put in your Skyrim inventory. Only what its shop deals in (its VendorBuys kinds, the `gold` lines of the trades file: gems, ingots, leather, food, weapons, ...), in whole lots, out of the merchant's own gold (its merchant chest, as Skyrim's barter menu shows it), priced by town and demand like the trade screen, worn gear for what's left of it; trains Speech. Host only. See [Economy](#economy) | `trade/SkySell`, `trade/TradeRules` (gold lines, `goldPrices`), `combat/SkyrimActorEntity.interact`, client `InputBridge` (the answer) | `Economy.cpp` (`kEvMerchantGold`, `kEvSellToMerchant`) | `skycraft-trades.txt` | `SkyCraft-trade`, `SkyCraft-economy` in SkyCraft.log | Written |
| Skyrim food fills hunger | `skyrim_food_hunger` | Food you eat in Skyrim (from Skyrim's inventory while Skyrim has the player, riding for one, or `player.equipitem`) fills Minecraft's hunger and saturation as the Minecraft food most like it would: stews like rabbit stew, cooked meat like cooked beef, raw meat, fish, bread, sweets, cheese, drinks, fruit and vegetables, by the food's name (and Skyrim's raw keyword). Guests too. See [Economy](#economy) | `food/SkyFood`, client `InputBridge`, `net/SkyNet` (`SkyrimFood`) | `Economy.cpp` (TESEquipEvent), `FoodValues.h` (the table, tested in `tests/FoodValuesTest.cpp`), Skyrim bit `kFeatSkyrimFood` | none | `SkyCraft-food`, `SkyCraft-economy` in SkyCraft.log | Written |
| Skooma brewing | `skooma_brewing` | `skycraft:moon_sugar`, and Skooma and Redwater Skooma as Minecraft potions (the effects item conversion's Skooma gives). In a brewing stand an awkward potion and moon sugar make Skooma, Skooma and glowstone dust Redwater Skooma (stronger, shorter, as glowstone makes any potion); gunpowder and dragon's breath make splash and lingering ones. See [Economy](#economy) | `skooma/SkoomaBrewing`, `config/FeatureCondition`, recipes in `data/skycraft/recipe/brewing` | none | none | `SkyCraft-skooma` | Written |
| Skooma dealers | `merchants` | The Khajiit caravans (Ri'saad, Ahkari, Ma'dran, Kharjo, Zaynabi, Atahbah, Ma'jhad, Khayla, Ra'zhinda, Dro'marash, Ma'randru-jo) always sell Skooma, Redwater Skooma and moon sugar on their trade screen, and buy moon sugar and Skooma for gold. A named Khajiit Skyrim doesn't make a merchant (a caravan guard) opens a shop of these alone | `trade/TradeRules` (`dealer` lines), `trade/SkyTrade.vendorOf`, `combat/SkyCombat` | none | `skycraft-trades.txt` | `SkyCraft-trade` | Written |
| Gear melting | none | Skyrim Iron, Steel and Orcish gear melts into iron nuggets and Dwarven gear into copper nuggets in a furnace or blast furnace, as vanilla's iron and copper gear does | recipes in `data/skycraft/recipe` (`iron_nugget_from_*`, `copper_nugget_from_*`) | none | data in the jar | none | Written |
| Guest parity | `guest_parity` | A guest's own Skyrim gets from Minecraft play what the host's gets, through the host's server, which stays in charge: the guest's NPCs near them get stand-ins of their own to fight (hits reach the guest's Skyrim and train its weapon skills; kills pay kill XP, kill drops and gear drops), their loot converts by the host's item rules, each of their dungeon chests rolls once for them, their merchants trade at their town's prices and train their Speech, their animals tame, Block, Armor and Smithing train their Skyrim, a bed's night passes theirs, Minecraft's explosions and arrows reach their NPCs, and they travel through the host's portals. The server holds their claims to distances and rates (`skycraft-party.txt`); both the host's and the guest's switch must be on. `/skyparty` shows who's who. See [Multiplayer](#multiplayer) | `party/` (GuestParity, PartyCodec, PartyRules, GuestChests), `net/SkyNet` (GuestActors, GuestLoot, GuestEvent), `combat/SkyrimActorEntity` (owner), hooks in `combat/SkyCombat`, `loot/` (SkyLoot.payKill, SkyBridge.convertForGuest, GearDrops, DungeonLoot), `trade/SkyTrade`, `companion/SkyCompanions`, `portal/SkyPortals` (guestReach), `mixin/SmithingMixin`, `mixin/AbstractArrowMixin`, `mixin/ServerExplosionMixin`; client `PartyClient`, `ProxySync` | none: each guest's Skyrim gets the usual events on its own link | `skycraft-party.txt`; `skycraft-opened-chests-guests.txt` | `SkyCraft-party` | Written |
| Shared world events | `shared_world_events` | A dragon attacking a player, a dragon slain, the host entering a dungeon and a shout in the host's Skyrim are told to every other player in the Skyrim mirror: a title (dragons) or an action bar line, a sound, and for dragons and shouts a small shake of their own Skyrim's camera. Nothing else in their Skyrim changes. A guest's own dragon fights are told the same way. Cooldowns per kind in `skycraft-party.txt`. `/skyparty test <dragon\|slain\|dungeon\|shout>` shows one to you. See [Multiplayer](#multiplayer) | `party/` (SharedWorldEvents, WorldEvents), `net/SkyNet` (GuestDragon, WorldEventShake); client `PartyClient` | `Party.cpp` (dragon bit, shout watch, camera shake), `Combat.cpp` (actor table, `kEvPartyShake`), `Game.cpp` (SkyState shout fields, the shake on Minecraft's camera) | `skycraft-party.txt` | `SkyCraft-party` (both halves) | Written |
| Guests' arrows at blocks | none | A guest's Skyrim arrow or bolt stopped at a Minecraft block sticks a Minecraft arrow there too, as the host's do: their client moves the spot into the mirror's terms and the host's server sticks it (within 128 blocks of the guest, at most 20 at once and 10 a second) | client `InputBridge`, `PartyClient`; `net/SkyNet` (ArrowBlocked), `party/GuestParity.arrowBlocked`, `combat/SkyCombat.stickSkyrimArrow` | `Arrows.cpp` (as for the host) | none | none | Written |
| NPC doors and ladders | `npc_doors_ladders` | Skyrim's NPCs use Minecraft's doors: one walking into a closed door (any but iron) has Minecraft open it, walks through, and Minecraft closes it again once its doorway has been empty 3 seconds; NPC paths go through doors instead of round them, and open doors and ladders are no longer walls for NPCs. Skyrim's NPCs can't climb, so one standing in a ladder column whose path goes up is lifted up it and stepped onto the block the ladder leans on (it rises in whatever pose it was in; with nowhere to step off, or after 10 s, it's let back down, never dropped). See [Towns and building](#towns-and-building) | `town/SkyTown` (`npcOpensDoor`, doors closing), `town/DoorTimers`, client `render/WorldExporter` (doors and ladders per section, open doors and ladders out of the NPC walls), client `TownInput` | `Passages.cpp` (doors and ladders, the climb), `NpcBlocks.cpp` (asks at a closed door), `PathAvoid.cpp` (paths through doors), `WorldRender.cpp` (`kRenPassages`) | `skycraft-town-rules.txt` (`door_close_after`) | `SkyCraft-town` | Written |
| Town crime | `town_crime` | Griefing a Skyrim town is a crime in Skyrim if anyone sees it: digging out its ground, roads, rocks, buildings or other objects, breaking a block of its ground that digging revealed, or breaking a block someone else placed there. Minecraft adds up a few seconds of it into one crime with a value in gold; Skyrim looks for a witness with its own detection (the nearest person with a crime faction who notices the player, guards first) and raises Skyrim's own steal alarm for the witness's crime faction, so the bounty, the witnesses and the guards behave as for a theft of that value. Only the host's play counts (the town is the host's Skyrim's); your own blocks are never a crime | `town/SkyTown`, `town/TownRules`, `town/CrimeTally`, `town/TownBlocks` (who placed each block in a town, kept with the chunk), hook in `world/SkyDig.open` | `Towns.cpp` (`OnCrime`: witness, scene, `Actor::StealAlarm`) | `skycraft-town-rules.txt` (`crime_ground`, `crime_object`, `crime_block`, `crime_batch`) | `SkyCraft-town` | Written |
| Guard comments | `guard_comments` | A guard within 16 blocks who sees you build, break blocks or dig in town now and then says something about it, in Skyrim's corner with their name ("Whiterun Guard: Watch it."): never more than once a minute per guard, at most one guard every 20 seconds. A notification, not speech: no CommonLib function makes an NPC say a line of ours | `town/SkyTown` (`EV_TOWN_BUILD`, only with a guard's stand-in near) | `Towns.cpp` (`OnBuild`), `TownKinds.h` (the lines), `Combat.cpp` (`kActorGuard` in the actor table) | none | `SkyCraft-town` | Written |
| Hearthfire blocks | `hearthfire_blocks` | Minecraft blocks placed while you stand on a Hearthfire house's land (Lakeview Manor, Windstad Manor, Heljarchen Hall, as Skyrim reckons it) count for that plot while they stand: 80 make a small house, 320 a main hall, 720 the wings (you're told on the action bar, and in chat with a level-up sound when a stage is reached). Partial: Skyrim's own house parts aren't built or its quest moved by this (see [Towns and building](#towns-and-building)) | `town/SkyHearthfire`, `town/PlotTally` | `Towns.cpp` (the plot underfoot: a HearthFires.esm house location, `SkyState.hearthPlot`; stage events logged) | `skycraft-town-rules.txt` (`small_house`, `main_hall`, `wings`), `skycraft-hearthfire.txt` in the world's save folder | `SkyCraft-town` | Written (partial) |
| Dwemer redstone | `dwemer_redstone` | A Skyrim lever, pull chain, button or pressure plate used gives a Minecraft redstone pulse at its block (a redstone block for 1 to 1.5 s, only in an empty cell; your own Skyrim doesn't draw it). Redstone power at the foot of a Dwemer door near you (24 blocks) opens it with Skyrim's own activation; when the power goes the door closes. Locked doors and doors it would be a crime to open stay shut | `town/SkyRedstone`, `town/PulseTimers`, `town/RedstoneDoors`, client `TownInput`, client `render/WorldExporter` (leaves the pulse's block out) | `Redstone.cpp` (activate events, Dwemer doors, `kEvRedstoneDoor`), `TownKinds.h` (switches and Dwemer doors by model path) | none | `SkyCraft-town` | Written |
| Mobs and NPCs fight | `mobs_fight_npcs` | Minecraft's monsters in the mirror (from spawners, eggs and commands) go for Skyrim's people and creatures near them: one with nothing to do takes the nearest actor within 16 blocks, one after the player turns on an actor right in front of it (6 blocks) when the player is clearly further. Minecraft's undead leave Skyrim's undead be; creepers, endermen and zombified piglins keep to Minecraft's rules. They're walked straight at their target (the mirror has no paths). Their hits are real damage in Skyrim credited to no player (no crime, no kill credit, the actor doesn't turn on you), with Skyrim's blood and stagger; a melee monster strikes once a second as in Minecraft. Skyrim's guards, your followers, your enemies and whoever a monster goes for swing back when it's next to them: they turn, play an attack, and the monster takes their weapon's damage (Skyrim's damage / 5, as their hits on you) as from the actor, so it fights that actor in turn. NPCs keep fighting you as before. See [Creatures](#creatures) | `creatures/SkyCreatures`, `creatures/CreatureRules`, `combat/SkyrimActorEntity` (creature hits kept apart), `combat/SkyCombat` (`proxies`), client `InputBridge` (`IN_MOB_HIT`) | `Creatures.cpp` (`FightBack`, `TakeHit`), `CreatureKinds.h` (`FightsBack`), `Combat.cpp` (`kEvMob`) | none | `SkyCraft-creatures` | Written |
| Tamed wolves fight | `wolves_fight_skyrim` | Your tamed Minecraft wolf (not sitting) goes for a Skyrim actor that is hostile to you and fighting (within 16 blocks of you), as a Minecraft wolf goes for whatever fights its owner. Its bites are Minecraft hits by you in Skyrim (Skyrim's hit pipeline: the enemy reacts, the kill is yours) but train none of your skills; the kill's drops and XP are yours, as vanilla Minecraft counts a tamed wolf's hits as its owner's. Your enemies next to the wolf swing at it | `creatures/SkyCreatures`, `combat/SkyrimActorEntity` (`creditPlayer`), `loot/SkyLoot` (credited kill) | `Combat.cpp` (`MobEvent`, `ApplyHit` by the wolf), `Creatures.cpp` | none | `SkyCraft-creatures` | Written |
| Night monsters | `night_monsters` (off at first) | At Skyrim's night (20 to 5 o'clock), outdoors, out of towns and where Minecraft's block light is 0, now and then (a try every 60 to 120 s, 40% each) a skeever or skeleton comes out on Skyrim's ground 16 to 32 blocks from you where nobody can see it; draugr and skeletons within 64 blocks of a Nordic barrow's entrance. Through the deep creatures' spawn: they count with them (one cap, never past `skycraft-deep.txt`'s `max_alive`) and are taken away the same way. Never in interiors | `creatures/NightMonsters`, `creatures/NightRules`, `deep/SkyDeep` (shared count) | `Spawn.cpp` (`kSpawnAtNight`: on the ground, not under the land) | `skycraft-night.txt` | `SkyCraft-night` (both halves) | Written |
| Horses carry over | `horses_carry_over` | Your tamed Minecraft horse and your Skyrim horse are one animal. Ride (or lead) a tamed horse into the Skyrim world, outdoors, and it becomes your Skyrim horse there (the one it was before, or a new horse you own) with its speed (SpeedMult 50 to 150%) and health; the Minecraft horse goes once Skyrim has it. Leave the Skyrim world (a portal, `/skyview`) with your Skyrim horse within 16 blocks and it comes along as a tamed, saddled Minecraft horse with its kept stats; Skyrim's is disabled until it comes back. Its death in Minecraft is its death. See [Creatures](#creatures) | `creatures/SkyHorses`, `creatures/HorseRules`, `link/SkyLink` (SkyState `horse`) | `Horses.cpp`, `CoSave.cpp` (record `HRSE`), `Game.cpp` (SkyState) | none | `SkyCraft-horse` (both halves) | Written |
| Creatures and light | `creatures_and_light` | Skeevers and Falmer keep out of strong Minecraft light: within a torch's 6 blocks (a lantern's or glowstone's 7, light level 8 or more as Minecraft's light falls off) they're pushed back the way NPCs are pushed out of blocks. Skyrim's undead (draugr, skeletons, vampires; not essential ones) outdoors in Skyrim's daylight (6 to 19:30) under open sky, not wet, catch fire the way zombies do; the fire's damage reaches Skyrim credited to no one, with flames on the body. Which actors are light-shy or undead comes from the race (`CreatureKinds.h`) | `creatures/SkyCreatures` (`sunlight`), `combat/SkyrimActorEntity` (sun fire) | `Creatures.cpp` (`ShyFromLight`), `CreatureKinds.h`, `BlockLights.cpp` (`LightAt`), `Combat.cpp` (actor table bits) | none | `SkyCraft-creatures` | Written |
| Paired backups | `paired_backups` | Each time Skyrim loads a save with Minecraft connected, both are backed up as a pair under one stamp (the time of the load): Skyrim copies the save (its `.ess` and `.skse` co-save) to `Documents\My Games\Skyrim Special Edition\SkyCraft Backups\<stamp>`, Minecraft zips its world to `skycraft-backups\<stamp> <world>.zip` in the instance's `.minecraft` folder. The newest 5 pairs are kept. Minecraft saves its world first and holds its autosaves while the zip is written; both copies run on threads of their own, so neither game waits. A save loaded before Minecraft is connected is backed up once it is; a guest's world is backed up when they're back in their own. Death reloads and quickloads make pairs too. See [Paired backups](#paired-backups) | `backup/` (SkyBackups, BackupRequest, BackupNames, WorldZip, BackupRules), client `InputBridge` (`IN_PAIRED_BACKUP`) | `Backups.cpp`, `BackupNames.h`, `main.cpp` (kPreLoadGame, kPostLoadGame), `Combat.cpp` (`kEvPairedBackup`), bit `kFeatPairedBackups` | `skycraft-backups.txt` | `SkyCraft-backup`; `paired backups` in SkyCraft.log | Written |
| Far block detail | `far_block_detail` (off by default) | Block sections more than 64 blocks from the camera aren't drawn into the sun's shadow map, so each is drawn once less a frame: they cast no sun shadows (on Skyrim's ground or on other blocks) and aren't shaded by other far blocks; Skyrim's own shadows still fall on them, and everything nearer is as before. With it on or off, SkyCraft.log's perf line (every minute) has the block drawing time and how many sections and triangles were drawn, and latest.log what meshing them cost Minecraft. See [Far block detail and profiling](#far-block-detail-and-profiling) | client `render/WorldExporter` (meshing stats) | `WorldRender.cpp` (`RenderPasses`), `Perf.h` (`kBlocks`, block amounts), bit `kFeatFarBlockDetail` | none | `perf:` and `far block detail` in SkyCraft.log; `SkyCraft-render` | Written |
| Skyrim maps | `skyrim_maps` | A Minecraft map filled in the Skyrim world draws Skyrim. Where vanilla's map finds no block in a column, the pixel shows Skyrim's highest surface there, from the collision Minecraft holds round where you've been (so a map fills in as you explore, as vanilla's does): grass, dirt, rock, snow, ice, sand, mud and roads (the land's gravel and cobbles) by what the ground is made of, grass on a slope as dirt and cliffs as rock, buildings a light stone gray, trees and wood brown, and Skyrim's lakes and rivers in vanilla's water colour, darker where deeper. It's shaded north to south as vanilla shades its own ground, so hills read as hills. A block you place still shows over Skyrim's ground under it. Further out, the cells Skyrim has loaded (about 150 blocks round you) fill in coarsely from Skyrim's own land heights. The places on Skyrim's own map within 1024 blocks (cities, towns, dungeons, camps, shrines, ...) go on maps of that area as banners with their names, coloured by the kind of place (gray: heard of, not visited yet). Never in other dimensions. See [Maps](#maps) | `maps/` (SkyMaps, MapRules, MapSurface, MapMarkers, LandSamples, SkyMapLink), `mixin/MapItemMixin`, `mixin/MapItemSavedDataInvoker`, `world/SkyCollision` (its regions by column) | `Maps.cpp` (markers, land samples), `MapKinds.h` | none | `SkyCraft-maps` | Written |

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
would act like the mirror). The host travels as below; a guest (guest parity) goes through portals the host bound or opened, as far as their own Skyrim can follow (see [Multiplayer](#multiplayer)).

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
chat, as Minecraft tells a pet's owner. Each player tames and orders the animals of their own Skyrim: a guest's clicks on the host's animals
are left alone, and with guest parity a guest's own animals have stand-ins of theirs.

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

## Settings screen

Press **O** for Minecraft's pause menu: **SkyCraft settings** is in its top left corner, right of **Skyrim
destruction**. It opens a screen like Minecraft's own option screens (vanilla's option list, scrolling, **Done** at the
bottom) with every switch of `skycraft-features.txt` as an On/Off button, under a header for its part of the game: loot
and items, dungeons and the deep, merchants, food and potions, combat and stealth, skills, time and weather, worlds and
portals, towns and building, creatures and companions, controls and messages, multiplayer, saves and performance
(`config/FeatureAreas`; a switch none of them lists shows under **More**, and `FeatureAreasTest` fails until it's
listed). Each button's tooltip is the switch's description from `Features.Feature.about` and its key in the file.

**A click** (`Features.set`) saves the switch at once: only its `key = on|off` line changes, with its spacing and
trailing comment; comments, blank lines, unknown lines, the other switches and the file's line endings stay as written,
and a switch the file has no line for yet (a feature newer than the file) is added at the end with its comment
(`Features.rewrite`, unit tested). `Features.on` answers the new value from then on, on the server (the integrated
server reads the same switches) and the client alike, and a feature Skyrim runs part of gets its bit in
`McState.features` the next frame. Two switches are read only once, so the screen marks them **(restart)** and a change
shows as **(after restart)**: `minecraft_view` (`SkyDimension` keeps it for the whole game; every system asks it which
world is Skyrim's, and changing that under a player in the Nether isn't safe) and `natural_spawns` (the spawn game rules
are set as the world opens). `Features.on` keeps their launch value until Minecraft restarts. A feature that reads its
switch only once belongs in `Features.AT_RESTART`, so the screen says so.

**In a friend's world** the host's file decides for everyone (their integrated server asks their switches), so the
screen shows your own switches greyed out, with a note at the top. A few things still follow your own file there (click
to activate, and the Skyrim bits sent to your own Skyrim); change them from your own world. Editing the file by hand
still works as before (restart Minecraft after).

**Try it:**

1. In your own world, O, **SkyCraft settings**: the notes, then the headers and switches; scroll to the end
   (**Saves and performance**). Hover a button: its description.
2. Click **Merchants**: "Merchants: Off". Done, then right click a vendor with an empty hand: no trade screen.
   `latest.log` has `SkyCraft: feature merchants switched off in game`; `skycraft-features.txt` says
   `merchants = off`, its comments as they were. Switch it on again: trading works without a restart.
3. Click **Minecraft view**: "Minecraft view: Off (after restart)". `/skyview` still goes to the Nether until Minecraft
   restarts; after the restart the button reads "Minecraft view: Off (restart)" and other dimensions act like the mirror.
4. Join a friend's world (`/join`): the buttons are greyed out, and the note says their settings decide.

## Conversion messages and the guide book

**Conversion messages** (switch `conversion_messages`). Each Skyrim pickup item conversion handles gets a line on the
action bar (`loot/ConversionNotes`, unit tested; `loot/SkyBridge` sends it once a tick when it changed): what it was,
with its count, an arrow and what it became, by Minecraft's own names (a converted potion carries its Skyrim name), or
"stays in Skyrim" when the rules keep it or name an item Minecraft doesn't have, "(quest item)" for a quest item. A line
takes every pickup in the second after its first one and counts them ("Steel Sword → Iron Sword, +3 more"); a pickup
that converted takes the line from one that stayed. Gear a dying actor wore (Gear drops) never shows. To try it: loot a
weapon or an ore; take everything from a dungeon chest (one line with "+N more"); pick up coins ("Gold ×25 → stays in
Skyrim"). Switched off, conversions happen as before without a line.

**Guide book** (switch `guide_book`). Once a second the server looks for players who have never had the book while
Skyrim is linked (`SkyLink.active`), so it comes after the join's starter kit, and gives it: the written book
"SkyCraft" by SkyCraft (`guide/GuidePages`, eight pages, each checked to fit a book page), made as Steve's journal is
(`HomeCamp.journal`). The `skycraft:guide_book` attachment (`guide/GuideBook.GIVEN`, saved with the player, kept on death)
says it was given, so dying, losing it or rejoining never brings a second one. To try it: start a new SkyCraft world (or
the dev client with another `SKYCRAFT_USERNAME`) with Skyrim running: the book is in the inventory within a second, and
`latest.log` says `SkyCraft-guide: gave the SkyCraft guide book to <name>`. Die, or leave and come back: no second book.
With the switch off nobody gets it, and switching it on gives it to whoever hasn't had it.

## Bug reports

Type **`/skycraft report`** in chat. "Writing a SkyCraft report..." and a moment later "SkyCraft report saved:
C:\...\.minecraft\skycraft-report-2026-10-17_15-04-05.zip (click to copy). Send it with your bug report." The zip
(`report/ReportPlan`, unit tested) holds only:

| In the zip | From |
|---|---|
| `minecraft/logs/latest.log` | Minecraft's game folder (Prism's `instances/SkyCraft/.minecraft`) |
| `minecraft/crash-reports/*.txt` | The three newest crash reports there, if any |
| `minecraft/config/skycraft-*.txt` | Every SkyCraft config file (not `.old` copies, not `skycraft.properties`, which holds a friend's link) |
| `skyrim/SkyCraft.log` | The plugin's log, where `skse/src/main.cpp` `SetupLog` writes it: SKSE's `log_directory`, `Documents/My Games/Skyrim Special Edition/SKSE` (`Skyrim Special Edition GOG` for the GOG game). Documents is asked of Windows (`SHGetKnownFolderPath`, as SKSE does, so a moved or OneDrive Documents is found), then `%USERPROFILE%\Documents` and the like; the newest log found wins |
| `README.txt` | What's in the zip, when it was written, and notes: anything not found (no Skyrim log: where it should be) or unreadable |

Nothing else goes in: no worlds, no options, no other mods' files, never a link to somewhere else. It's written as
`.part` and renamed when done. The player who asks gets a report of their own machine: the server asks their client to
write it (`SkyReport.Ask`), so a guest in a friend's world packs their own logs, in their own game folder. From the
server console the server writes one of its own folders. `latest.log` gets a `SkyCraft-report` line with what went in.

**Try it:**

1. `/skycraft report`; click the path in chat and paste it into Explorer's address bar: the zip, with the files above.
2. In the dev client (`gradlew runClient` in `fabric`) on a PC without Skyrim's log: chat says Skyrim's log wasn't
   found, and `README.txt` in the zip says where it should be.
3. As a guest in a friend's world: the zip is in your own `.minecraft`, with your logs; the host's folder gets none.

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

## Combat and stealth

Skyrim's own combat and stealth take over where Minecraft's world reaches into them (switches `walls_hide_you`,
`sneak_attacks`, `power_attacks`, `light_stealth`, all on by default; each has a Skyrim bit, `McState.features`, since
Skyrim runs them). Minecraft's crouch is Skyrim's sneak (`Game.cpp` SyncSneak), so Skyrim's stealth eye shows how Skyrim's
NPCs see you.

**Walls hide you** (`Stealth.cpp`). Skyrim's line of sight, `Actor::CalculateLOS` (CommonLib wraps it, AE ID 37768; it
says which of a target's eye, head, torso and feet an actor can see), is called directly, so every call of it in the game's
code is redirected (the way `Game.cpp` hooks PlayerCamera::Update); SkyCraft.log says how many. When it says an NPC sees the
player, the lines from the NPC's eye point (Skyrim's own, `CalculateLOSLocation`) to Minecraft's eye and to the chest
(three quarters of the eye height up) are walked through the solid blocks Minecraft sends (`NpcBlocks::SolidBetween`, the
walk in `BlockWalk.h` that Skyrim's arrows use too). A solid block on both lines hides the player from that NPC. The blocks
the NPC's eyes and the player's head are in don't count, so a carpet or a slab you stand in hides nothing. Each NPC's answer
is worked out again every six frames at most. What it doesn't cover: blocks are solid by their collision shape, so glass,
leaves, fences and iron bars hide you too; sounds still give you away (Skyrim's detection hears through blocks); scripts
and conditions that ask for a line of sight another way (`HasLOS`, `GetLineOfSight`) aren't changed; and a call of the
function the game makes other than a plain `call` (none is expected) isn't hooked.

**Sneak attacks** (`Combat.cpp`). A hit with Minecraft's "crouching" bit on an actor whose detection level for the player
(Skyrim's `Actor::RequestDetectionLevel`, the detection its stealth eye shows; over 0 is detected) is 0 or less, checked
before the hit lands. That level already takes in combat: an NPC fighting you that sees you has you detected, and as in
Skyrim an NPC that lost you in a fight can be sneak attacked again. The multiplier starts at Skyrim's base for the vanilla
weapon the hit lands as (Iron Sword for swords, Iron War Axe for axes, Iron Dagger for tridents and spears in hand, Iron
Mace for maces and tools: one-handed, three times; a projectile lands as the Hunting Bow and fists count as hand to hand:
twice) and goes through Skyrim's Mod Sneak Attack Mult perk entry point with that weapon and the NPC, so Backstab (six
times), Assassin's Blade (tridents and spears land as a dagger: fifteen times) and Deadly Aim (arrows: three times) apply as
in Skyrim; which argument is the weapon is read from Skyrim's own entry point table and logged once. The damage is
Minecraft's times the multiplier; "Sneak attack for 6.0x damage!" shows in the corner, and Sneak trains by 30 skill uses for
a melee sneak attack, 2.5 for a ranged one. Skyrim's own sneak attack handling of the hit stays off (its multiplier is in
the damage already), so Skyrim's hit event doesn't call it a sneak attack.

**Power attacks** (`Combat.cpp`, `mixin/PlayerAttackMixin`). Minecraft resets a swing's charge before the hit lands, so the
charge and the sprint are read as `Player.attack` begins; at the end of the tick the stand-in sends "power attack" for a
melee swing over 0.9 charged that was a sprint hit or a critical hit (Minecraft only crits a fully charged swing). Skyrim sets
its power attack flag on the hit and gives it at least the stagger value of the weapon it lands as (the Iron Dagger a
trident lands as has none, which leaves it SkyCraft's own sprint and critical stagger), and its hit processing does the
rest. The damage stays Minecraft's, with its own 1.5 for a critical hit; Skyrim's power attack damage comes with its attack animations, which a
Minecraft swing has none of. Arrows, thrown tridents and sweeps are never power attacks.

**Light and stealth** (`Stealth.cpp`, `Stealth.h`). Every tick in the mirror Minecraft's client reads the block light and the
sky light at its player's eyes (`McState` 0xDC). Every frame Skyrim puts in the player's light level (`HighProcessData::lightLevel`,
the light Skyrim's detection and the `GetLightLevel` condition read) the brighter of

- Skyrim's own light on the player, times Minecraft's brightness for its sky light there: all of it in the open, or anywhere
  without Minecraft blocks overhead (outdoors, Skyrim's interiors and buildings, day or night: Skyrim's light already carries
  its sun, the hour, the weather and its lamps), about four fifths one block in under an overhang, half under a deeper roof,
  nothing sealed into a dug tunnel;
- Minecraft's block light on Skyrim's scale: Minecraft's brightness curve times 100 (15 is about an exterior in daylight; a
  torch's own block 78, two blocks off 50, four off 33, eight off 14).

Skyrim keeps working out its own light; SkyCraft remembers it and replaces it each frame, and gives it back when the switch is
off or Minecraft isn't driving the player. Minecraft's sky light is used only as cover, not with the time of day folded in:
the mirror has no Minecraft blocks over Skyrim's interiors, so Minecraft's sky there would make every dungeon broad daylight.

**Try it** (Skyrim console and Minecraft chat, cheats on):

1. Walls: stand in the open twenty blocks from a guard or a bandit camp until they notice you (the stealth eye opens), then
   build a wall of stone two blocks high between you and crouch behind it. The eye closes, SkyCraft.log says "walls hide
   you: ... can't see the player through Minecraft blocks", and enemies lose you and search. Glass hides you too; a wall one
   block high (they see your head over it) doesn't. `walls_hide_you = off`: they see through it.
2. Sneak attacks: crouch up behind an NPC that hasn't noticed you (a sleeping or wandering bandit, the eye closed) and hit
   it with a sword: "Sneak attack for 3.0x damage!", the log says `sneak attack 3.0x`, and Sneak goes up (`player.getav
   sneak` before and after). With Backstab (`player.addperk 00058210`, Skyrim.esm's) it's 6.0x; add Assassin's Blade
   (`player.addperk 00058211`) and a trident in hand makes it 15.0x. Crouch and shoot one with a bow: 2.0x (Deadly Aim,
   `player.addperk 00105F19`: 3.0x). Hit one that's fighting you and sees you: no message, and the log says "no sneak attack
   ... detection level". `sneak_attacks = off`: crouched hits are plain hits.
3. Power attacks: wait for the attack indicator to fill, then sprint into an NPC and hit it (or jump and hit it on the way
   down): it staggers like a Skyrim power attack, and both logs say "power attack". A half charged sprint hit doesn't.
4. Light: at noon dig a tunnel five blocks into a hillside and seal the entrance. In the console `player.getlightlevel` (the
   GetLightLevel condition) reads near 0 (Skyrim's own reading outside is the daylight figure); place a torch beside you:
   about 78. SkyCraft.log's `light and stealth` lines show Skyrim's light, Minecraft's and the result. Bandits walking past
   an unlit tunnel mouth no longer see you in it.

Headless: `tools/fake_skyrim.py` prints the light Minecraft reads at its eyes and names the hit's flags ("power attack",
"crouching").

Not played yet: whether Skyrim's detection reads the line of sight and the light through these (CommonLib names both for
what detection uses; the hooked call count and `player.getlightlevel` show they're reached), Skyrim's light scale (100 for
Minecraft's brightest is a guess at a sunlit exterior), the Mod Sneak Attack Mult argument order (checked against Skyrim's
own table at run time), the vanilla sneak attack bases and Sneak skill uses (from what vanilla Skyrim gives), and how often
detection asks.

## World

Four features that tie Minecraft's world to Skyrim's: its weather, its farms, the villages of Tamriel Wilds and its
shrines. Their switches are in the "World" group of `skycraft-features.txt`.

### Weather sync

Switch `weather_sync` (Skyrim's half needs it too: `kFeatWeatherSync`, bit 12 of `McState.features`), numbers in
`skycraft-weather.txt`. Both halves log `SkyCraft-weather`.

**Skyrim to Minecraft.** Every frame Skyrim says what its sky is doing where the player is (`SkyState.weather`): the
current weather's classification (Skyrim's weather records are flagged pleasant, cloudy, rainy or snow; rain or snow
with a thunder sound is a thunderstorm; a weather with no flag, like fog, counts as cloudy), and whether rain or snow is
falling right now (`Sky::IsRaining`, `IsSnowing`, which follow the fade in and out). Indoors it says nothing. Once a
second Minecraft sets its weather to match with `/weather` (clear and cloudy: clear; rain and snow: rain; a storm:
thunder).

What that looks like where: the mirror's only biome is `minecraft:the_void`, which has no precipitation, so Minecraft's
own rain and snow never fall there (no rain drawn, no fires put out, no lightning strikes, no snow of its own); its sky
and the light on your blocks darken under rain and thunder. Tamriel Wilds and Home share the mirror's weather (their
levels take the overworld's), so there it rains, snows in the cold biomes and thunders the Minecraft way while Skyrim's
does, Skyrim's parked player's weather. The Nether has none.

**Snow settles.** While snow falls in Skyrim and you're outdoors in the mirror, every `settle_every` ticks
`settle_columns` random block columns within `settle_radius` of the host are looked at. Where the top of a column is a
Minecraft block within 24 blocks of your height, with nothing of Minecraft's over it and nothing of Skyrim's either
(no roof, bridge or cliff within 48 blocks up), a snow layer settles with `settle_chance`, or a layer is added to the snow
already there, up to `max_layers`; never more than one a column each pass, never on Skyrim's own ground or on
something like a torch or a flower. In clear weather (pleasant, nothing falling) a layer melts with `melt_chance` a pass,
snow you placed yourself in the mirror included (set `melt_chance = 0` to keep it); cloudy and wet weather keep it. While
rain falls, farmland open to the sky is watered (`rain_waters_farmland`).

| Setting | Default | |
|---|---|---|
| `settle_every` | 40 | Ticks between passes |
| `settle_columns` | 64 | Columns looked at a pass |
| `settle_radius` | 16 | Blocks around the host |
| `settle_chance` | 0.25 | A column's chance of a layer a pass while it snows |
| `max_layers` | 2 | Most layers snow settles to (1 to 8) |
| `melt_chance` | 0.1 | A column's chance of losing a layer a pass in clear weather |
| `rain_waters_farmland` | on | Skyrim's rain waters farmland open to the sky |

At the defaults a block in the open near you gets its first layer in a minute or two of snowfall.

**Minecraft to Skyrim.** A change of Minecraft's weather that SkyCraft didn't make (a player's `/weather`, a mod),
once it has held five seconds (SkyCraft's own changes fade in for ten seconds first and don't count), is a request:
`McState.weatherSeq` goes up by one and `weatherWant` says the kind (clear, rain, thunderstorm). Skyrim handles each
number once: it looks at the weathers its current region can have (the region's weather data, else its climate's) and
forces the nearest (`Sky::ForceWeather`, no override, so the region's own weather takes over again in time): rain falls
back to snow, then a storm; a storm to rain, then snow; clear to cloudy; the likeliest of the best. Nothing is picked by
form id. It refuses indoors, while a quest holds the weather (`Sky::overrideWeather`), when the region has nothing of
the sort, or with its switch off. Then it answers in `SkyState.weatherAck` (with `kWeatherRefused` when it said no).
Meanwhile Minecraft keeps the player's weather; after the answer it follows Skyrim's again, so a refusal puts
Minecraft's weather back, with a line on the action bar ("Skyrim's sky can't turn to thunderstorms here, ..."), and so
does a nearest that isn't the same ("Skyrim has no thunderstorms here: rain instead"). An older plugin never answers;
after 15 seconds Minecraft gives up and follows Skyrim's weather. Skyrim never asks, and Minecraft asks only about
changes it didn't make, so the two never take turns changing each other's weather.

**Try it.** Outside in Skyrim, wait for rain or force a weather with Skyrim's console (`fw <weather id>`: Skyrim.esm's
SkyrimStormRain, SkyrimStormSnow and SkyrimClear, their ids from xEdit or UESP): Minecraft's log says `Minecraft's
weather set to thunder for Skyrim's thunderstorms` and the light on your blocks darkens. Force a snow storm by some
blocks you placed in the open: snow layers settle on them within a few minutes, never on the ground around them nor
under a Skyrim roof or walkway; force a clear weather and they melt. In Minecraft, `/weather thunder` outside Whiterun:
Skyrim's sky turns stormy and SkyCraft.log has `Minecraft asked for thunderstorm: Skyrim's weather is now ...`;
`/weather rain` in Winterhold brings snow; `/weather clear` in an inn: "can't turn" on the action bar and Minecraft's
weather goes back. With `weather_sync = off`, neither follows the other. (If `/weather` is refused right after a console
`fw`, the console may have made it an override, like a quest's: `rw` releases it.)

### Skyrim farming

Switch `skyrim_farming`; logs `SkyCraft-farm`. **What worked already:** dig into Skyrim's grass and the dirt blocks it
reveals can be hoed and farmed like any Minecraft dirt; and Skyrim's own crops (wheat, cabbages, potatoes, leeks,
gourds, ...) are Skyrim flora, harvested by activating them with G or a right click Minecraft has no use for (click to
activate: an empty hand, a sword, a hoe), their produce converting like any loot (`skycraft-items.txt`: wheat to
wheat, potatoes to potatoes, cabbages and leeks to beetroot, carrots to carrots, a gourd to bread). A hoe on Skyrim's
ground itself did nothing (there's no block there for it), and seeds on it fail (crops need farmland under them).

**Now** a right click with a hoe on Skyrim soil tills it. Skyrim's surfaces carry what they're made of (`SkyTri.material`,
a `Proto.DIG_*` Skyrim picks from the Havok material: on the land the material of its land texture, so a farm's plowed
soil is dirt or mud and its edges grass); grass, dirt and mud facing up no steeper than you can walk count. The hoeing
player's client (it has Skyrim's geometry) picks the cell whose farmland top comes nearest the ground and asks the
server (`SkyNet.FarmTill`), which digs the cell out of Skyrim's geometry the way digging does (and the cell over it when
the ground reaches into it, so the crop isn't buried), puts farmland there, plays the hoe's sound and wears the hoe. The
pit's walls are Skyrim's ground, as for any dug cell. From then on it's Minecraft farmland: plant seeds, potatoes or
carrots; crops grow by Minecraft's random ticks (the mirror's sky light reaches them, day and night); water within four
blocks keeps the soil moist and the crops quick, and so does Skyrim's rain (Weather sync). It needs Skyrim destruction on
(pause menu), like digging. While Skyrim's crosshair is on a plant it can harvest (`SkyState.aim`, `kAimHarvest`: flora
or a tree with produce, not yet picked), the hoe leaves the ground alone and the click harvests the plant.

**Try it.** At a farm (Pelagia Farm outside Whiterun's gate, the farms by Rorikstead), hoe the soil between the
crops: farmland, a little pit, `SkyCraft-farm: ... tilled Skyrim's soil` in the log. `/give @s
minecraft:wheat_seeds 8`, plant, `/tick sprint 1d` (or wait): wheat. Point the hoe at a cabbage and right click: it's
harvested, a beetroot in your inventory. Hoe stone or a road: nothing.

### Tamriel Wilds villagers

Switch `wilds_villagers`; logs `SkyCraft-wilds`. Every five seconds the villagers within 64 blocks of a player in Tamriel
Wilds are named in Skyrim's way for their profession: "Hrodmar, Nord Blacksmith" (weaponsmith), "Ulfar, Nord Armorer",
"Gaston Belmont, Breton Smith" (toolsmith), "Livia Mallius, Imperial Cartographer", "Dravis, Dark Elf Priest of
Mara" (cleric), "Sigvard, Nord Tanner" (leatherworker), Scholar (librarian), Stonemason, Farmer, Fisherman, Fletcher,
Butcher, Shepherd, Beggar (nitwit), Laborer (no job yet), Child. Race and name come from the villager's UUID, so they
never change (half are Nords; then Imperials, Bretons, Redguards, Dark Elves, a few High and Wood Elves, Khajiit and
Argonians); the title follows its job. A name a player gave with a name tag is left alone.

They also sell Skyrim wares beside Minecraft's own trades, for emeralds:

| Profession | Novice | Journeyman | Master |
|---|---|---|---|
| Armorer | Steel Helmet, 7 | Orcish Cuirass, 18 | Elven Cuirass, 28 |
| Weaponsmith | Steel Sword, 7 | Orcish War Axe, 15 | Dwarven Sword, 24 |
| Toolsmith | 3 Steel Ingots, 4 | 2 Orichalcum Ingots, 6 | 2 Refined Malachite, 12 |
| Leatherworker | 4 Leather Strips, 2 | | |
| Cleric | Potion of Minor Healing, 3 | Potion of Resist Fire, 6 | Potion of Ultimate Healing, 12 |

The potions are what SkyCraft converts those Skyrim potions into (`skycraft-items.txt`). A villager that changes job
starts its trades over, Skyrim's with the new one's. Villagers in the mirror, the Nether or Home are never touched.

**Try it.** `/execute in skycraft:tamriel_wilds run locate structure #minecraft:village`, go there: the villagers' names
show when you look at them; trade with an armorer or a cleric. `/summon minecraft:villager ~ ~ ~
{VillagerData:{profession:"minecraft:weaponsmith",level:5}}` there: within five seconds a Skyrim name and three Skyrim
trades. The same in the Nether: a plain villager.

### Shrine blessings

Switch `shrine_blessings` (Minecraft's half only: with it off Minecraft ignores Skyrim's blessing input). Logs
`SkyCraft-blessing` in both halves. Once a second Skyrim looks at the player's active effects for a spell named
"Blessing of ..." (the shrines of the Nine, Dawnguard's Auriel, any other shrine's); a new one, or one cast again,
goes to Minecraft (`kInBlessing`) with the game hours it has left of its 8 and Skyrim's timescale, and Minecraft gives
the effect for as long in real time: 8 hours x 3600 / timescale seconds, 24 minutes at the default 20. A blessing
Minecraft missed (it wasn't running) is sent when it's back, with what's left of it.

| Blessing | Minecraft effect |
|---|---|
| Akatosh | Haste |
| Arkay | Health Boost |
| Dibella | Luck |
| Julianos | Haste |
| Kynareth | Speed |
| Mara | Regeneration |
| Stendarr | Resistance |
| Talos | Resistance |
| Zenithar | Hero of the Village |
| Auriel (Dawnguard) | Night Vision |
| Any other "Blessing of ..." | Luck |

All level I, ambient like a beacon's; the action bar says "Blessing of Talos: Resistance". A guest's own Skyrim blessing
reaches the host's server (`SkyNet.Blessing`).

**Try it.** Pray at the shrine of Talos below Whiterun's Gildergreen (activate it, G or a right click): Resistance for 24
minutes, and both logs have a `SkyCraft-blessing` line. In Skyrim's console `set timescale to 10`, then pray at another
shrine: twice as long. Pray at the same shrine again later: the effect starts over.

## Economy

Selling Minecraft goods to Skyrim's merchants, Skyrim food for Minecraft's hunger, and Skooma made and sold.

**Selling for Skyrim gold** (switch `sell_to_merchants`). Sneak and right click a merchant with the goods in your main
hand. Minecraft asks Skyrim how much gold the merchant has (`kEvMerchantGold`; Skyrim reads the merchant's chest, the one
its barter menu trades from, or its own purse if it has none), then takes as many whole lots from your hand as it can pay
for and has Skyrim move that gold into your Skyrim inventory (`kEvSellToMerchant`). The action bar says what sold and for
how much ("Sold 8 Iron Ingot to Belethor for 20 gold": 3 gold an ingot, a little less each), or why not ("Belethor doesn't buy Dirt.", "... buys Coal 8 at a
time.", "... has only 12 gold."). What a merchant buys and pays is the trades file's `gold` lines in the kinds its Skyrim
shop deals in (`ActorRecord.vendor`: a smith buys ingots and gear, a grocer food, a fence stolen goods, a general store
everything but stolen goods):

```
# category gold count item price   (price: Skyrim gold for count items)
ingot gold 1 minecraft:iron_ingot 3
jewelry gold 1 minecraft:diamond 150
potion gold 1 minecraft:potion[healing] 8
```

The price is times the town's multiplier for its kind (`skycraft-towns.txt`, as on the trade screen), and every lot sold
lowers the next 5% (the shared supply and demand, down to half, easing back every two minutes). Worn gear sells for the
share of its durability left. The sale trains Speech by the gold paid, as Skyrim's barter does. Only the host sells: the
merchants and the gold are the host's Skyrim's. With an older Skyrim plugin, which never answers, the sale is called off
after two seconds and nothing is taken. The defaults pay about what Skyrim's own merchants pay for the nearest Skyrim
goods, always less than Skyrim sells them for, so gold can't be farmed between the two games.

**Upgrading the trades file.** `skycraft-trades.txt` still holding an earlier build's defaults, never edited, gets the new
ones (gold lines, Skooma) by itself, the old file kept as `.txt.old` (`TradeRules.OLD_TRADES`). An edited file is left
alone: add the gold and Skooma lines from a fresh default (delete the file to get one).

**Skyrim food fills hunger** (switch `skyrim_food_hunger`). When the player eats a food in Skyrim, Skyrim works out how
filling it is (`skse/src/FoodValues.h`, by the words of its English name and the VendorItemFoodRaw keyword) and sends it
(`kInSkyrimFood`); Minecraft adds it to its player's hunger and saturation (`FoodData.eat`, guests through the host).
Skyrim's inventory can't be opened while Minecraft drives the player (Tab and I are Minecraft's), so this happens while
Skyrim has the player (riding, a crafting station), or through the console.

| Skyrim food | Food points, saturation modifier | Like Minecraft's |
|---|---|---|
| Beef, Horker, Venison Stew | 10, 0.6 | rabbit stew |
| Apple Cabbage Stew; Clam Chowder, Elsweyr Fondue, Potage le Magnifique | 8, 0.6 | |
| Vegetable, Tomato, Cabbage (Potato) Soup | 6, 0.6 | mushroom stew |
| Cooked Beef, Horker Loaf, Mammoth Steak, Venison Chop, Horse Haunch, Leg of Goat Roast | 8, 0.8 | cooked beef |
| Grilled Chicken Breast, Pheasant Roast, Rabbit Haunch | 6, 0.6 | cooked chicken |
| Salmon Steak, Steamed Mudcrab Legs | 6, 0.8 | cooked salmon |
| Raw Beef, Venison, Horse Meat, Horker Meat, Dog Meat, Leg of Goat | 3, 0.3 | raw beef |
| Chicken or Pheasant Breast, Raw Rabbit Leg | 2, 0.3 | raw chicken |
| Salmon Meat, Clam Meat, Mudcrab Legs | 2, 0.1 | raw salmon |
| Apple Pie | 8, 0.3 | pumpkin pie |
| Snowberry, Jazbay, Juniper Berry Crostata | 6, 0.3 | |
| Bread, Braided and Garlic Bread, dumplings | 5, 0.6 | bread |
| Sweet Roll, Honey Nut Treat, Long Taffy Treat | 3, 0.1 | cookie |
| Cheese wheel, Mammoth Cheese Bowl / wedge / slice | 6 / 3 / 2, 0.6 | |
| Red and Green Apple, Gourd | 4, 0.3 | apple |
| Carrot / Cabbage / Leek, Tomato, Ash Yam / Potato | 3 / 2 / 2 / 1 | carrot, beetroot, potato |
| Baked Potatoes, Grilled Leeks | 5, 0.6 | baked potato |
| Honey / Butter | 6 / 1, 0.1 | honey bottle |
| Ale, mead, wine, brandy, Sujamma / Jug of Milk | 2, 0.1 / 3, 0.3 | |
| Anything else (mods, other languages) | 3, 0.3 (2 if raw) | |

**Skooma brewing** (switch `skooma_brewing`). Moon sugar is an item of its own, `skycraft:moon_sugar`, and Skooma and
Redwater Skooma are Minecraft potions, `skycraft:skooma` (Skooma High I for 180 s and a moment's Saturation) and
`skycraft:redwater_skooma` (II for 150 s, Saturation II), the effects item conversion's Skooma gives. In a brewing stand:

| Bottle | Reagent | Makes |
|---|---|---|
| Awkward potion (any bottle) | moon sugar | Skooma |
| Skooma (any bottle) | glowstone dust | Redwater Skooma: stronger and shorter, as glowstone makes any potion |
| Skooma or Redwater Skooma | gunpowder | the splash one |
| Splash Skooma or Redwater Skooma | dragon's breath | the lingering one (and from it, tipped arrows) |

Brewing is data in 26.3 (`minecraft:brewing` recipes; Fabric API 26.3 has no brewing builder any more), so the recipes are
`data/skycraft/recipe/brewing/*.json` in vanilla's format, each with a Fabric load condition (`skycraft:feature_on`,
`config/FeatureCondition`) that keeps it out while the switch is off. Drinking any of them is a dose (Skooma and
addiction). Item conversion still turns Skyrim's Skooma into a potion with effects of its own and Moon Sugar into sugar
until its rules name the new ones (`minecraft:potion[skycraft:skooma]`, `skycraft:moon_sugar`): Skooma converted that way
(effects of its own, no potion id) doesn't brew into Redwater Skooma.

**Skooma dealers** (switch `merchants`). A `dealer` line of the trades file names who deals in a kind of goods no Skyrim
shop lists by itself:

```
skooma sell 1 minecraft:potion[skycraft:skooma] 30
skooma sell 1 skycraft:moon_sugar 50
dealer skooma = Ri'saad, Ahkari, Ma'dran, Kharjo, Zaynabi, Atahbah, Ma'jhad, Khayla, Ra'zhinda, Dro'marash, Ma'randru-jo
```

Those merchants always show these trades first on their screen (the other offers fill the rest of the twelve), and buy
moon sugar and Skooma for gold. A named Khajiit Skyrim doesn't make a merchant (a caravan guard) opens a shop of these
alone on an empty-hand right click; G still talks to them. Names match Skyrim's, ignoring case.

**Gear melting.** Skyrim's ores convert straight into SkyCraft's ingots and refined gems (iron, gold and corundum ore into
raw iron, gold and copper, which vanilla smelts), so there are no ore items to smelt. SkyCraft's metal gear now melts like
vanilla's: Skyrim Iron, Steel and Orcish pieces into an iron nugget, Dwarven into a copper nugget, in a furnace or blast
furnace. Elven, Glass, Ebony and Daedric gear doesn't, like diamond and netherite gear.

**Try it** (cheats on, the host):

1. Stand by Belethor in Whiterun. `/give @s minecraft:iron_ingot 10`, sneak and right click him with them: "Sold 10 Iron
   Ingot to Belethor for ... gold", fewer ingots in hand, more gold in Skyrim (`player.getitemcount 0000000F` in the
   console). The logs: `SkyCraft-trade: ... sold 10 x minecraft:iron_ingot` and `SkyCraft-economy: Belethor (...) paid ...`.
   Again: each ingot pays a little less. With dirt: "Belethor doesn't buy Dirt.". Arcadia (an apothecary) won't buy ingots.
2. Sell Belethor diamonds (`/give @s minecraft:diamond 10`) until his gold runs out: only the diamonds he can pay for
   are taken, then "Belethor has only N gold.". The log's `SkyCraft-economy` line says what his chest held.
3. A guest sneak-clicks a merchant: "Only the host sells ...". With `sell_to_merchants = off`, sneak-clicking with goods
   does what it did before.
4. Food: get hungry in Minecraft (`/effect give @s minecraft:hunger 30 9`), then in the Skyrim console
   `help "Horker Stew" 4` for its form id, `player.additem <id> 1` and `player.equipitem <id>`: hunger rises by 10 points
   (`SkyCraft-food: ... hunger a -> b`, and SkyCraft.log's `SkyCraft-economy: ate Horker Stew ...`). Any food works; riding
   a horse, Tab opens Skyrim's inventory to eat from. With `skyrim_food_hunger = off` nothing is sent.
5. Brewing: `/give @s skycraft:moon_sugar 3`, a brewing stand, awkward potions (water and nether wart, or
   `/give @s minecraft:potion[potion_contents={potion:"minecraft:awkward"}]`): Skooma. Add glowstone dust: Redwater Skooma.
   With `skooma_brewing = off` and a restart, moon sugar brews nothing.
6. Dealers: Ri'saad's caravan outside Whiterun: Skooma, Redwater Skooma and moon sugar on his screen. Kharjo (Ahkari's
   guard, at Dawnstar or Windhelm): a shop of those alone.
7. Melting: `/give @s skycraft:steel_sword`, smelt it: an iron nugget.

Not played yet: all of it. Skyrim's side isn't built yet (new CommonLib calls: TESEquipEvent sink, `AddObjectToContainer`,
reading a vendor faction's merchant chest), nor is Minecraft's against 26.3 (see the probe lines).

## Multiplayer

Friends who also run SkyCraft join the host's Minecraft world, each with their own Skyrim on their own PC. The host's
integrated server runs the world; the host's Skyrim reaches it through shared memory, a guest's through their own client
(SkyNet packets). Before this, a guest's Skyrim only told the server when it hit them, and only the host's Skyrim got
anything back from Minecraft play.

**Guest parity** (switch `guest_parity`, on in both the host's and the guest's `skycraft-features.txt`; numbers in the
host's `skycraft-party.txt`). A guest's client sends what their Skyrim reports and the host's server answers their Skyrim
the way it answers the host's (`party/GuestParity`):

- Ten times a second, their Skyrim's place (world, place flags, town, hour) and its actors near them, nearest first
  (`SkyNet.GuestActors`). The server gives the actors within `guest_range` of the guest (at most `guest_actors`) stand-ins
  of their own: invisible, owned by the guest (`SkyrimActorEntity.owner`, synced as the guest's entity id). Each client
  picks and follows only its own Skyrim's stand-ins, so nobody's crosshair lands on an actor their Skyrim doesn't draw;
  projectiles still hit any stand-in in their way (the world is shared). Hits on a guest's stand-in go to their Skyrim
  (`EV_HIT_ACTOR` in `SkyNet.GuestEvent`), which trains their weapon skills as the host's; an actor hit in the last ten
  seconds and then seen dead is a kill, paid by `SkyLoot.payKill` as the host's are (kill XP, kill drops, gear drops) to
  whoever landed the last hit, at most `guest_kill_burst` at once and `guest_kills_per_minute` after.
- Whatever lands in their Skyrim inventory (`SkyNet.GuestLoot`): the host's item rules convert it into their inventory
  and their Skyrim is told to remove it (`EV_TAKE_ITEM`); at most `guest_loot_burst` items at once and
  `guest_loot_per_minute` after, and the rest stay in their Skyrim with a line on their action bar. Gear a dying actor of
  theirs wore is taken only for an actor the server saw in their table in the last ten seconds, and drops only for a kill
  it credited. A dungeon chest rolls once for each player (theirs are in `skycraft-opened-chests-guests.txt`), at most
  `guest_chest_burst` at once and `guest_chests_per_hour` after.
- The server tells their Skyrim what it tells the host's: skill uses (Block and Light or Heavy Armor from hits taken,
  Smithing from crafting, Speech from trading), arrows stuck in their actors, Minecraft explosions within 64 blocks in the
  same Skyrim world's space, a full night in a bed (by their Skyrim's own hour; the world's clock stays the host's), taming
  and orders for their animals, and portal trips. Their client passes on only these event types (`GuestParity.RELAYED`).

| Feature | Host | Guest before | Guest now |
|---|---|---|---|
| Fighting Skyrim's NPCs | Their NPCs have stand-ins | The host's NPCs' stand-ins only, invisible to them; their own NPCs couldn't be hit | Their own NPCs have stand-ins of theirs; hits reach their Skyrim |
| Kill drops, kill XP | For credited kills | Only for a kill of one of the host's NPCs | Also for their own NPCs' kills, rate limited |
| Gear drops | For credited kills | Only for the host's NPCs | Also for their own NPCs, gear checked against their stand-ins |
| Weapon skills (One-Handed, Two-Handed, Archery) | From hits | None | From hits on their own NPCs |
| Block, Light and Heavy Armor | From hits taken | None (`trainDefence` was the host's) | From hits taken |
| Smithing | From crafting | None | From crafting |
| Item conversion and potions | Their Skyrim loot converts | Never (their item ring was never read) | Converts by the host's rules, rate limited |
| Dungeon chests | Once a chest | Never | Once a chest for each player, rate limited |
| Merchants and Speech | Their merchants, their town's prices, Speech | The host's merchants (invisible to them), priced for the host's town, no Speech | Their own merchants, priced for their own town, with shops of their own; Speech |
| Companions | Tame and order their animals | Clicks on the host's animals left alone | Tame and order their own animals; "died" told to them |
| Arrows stuck in NPCs | Pinned in Skyrim | Into the host's Skyrim (a host's NPC of that id) | Into their own Skyrim |
| Skyrim's arrows at blocks | Stick a Minecraft arrow | Ended at the block, no Minecraft arrow | Stick a Minecraft arrow (no switch: always) |
| Explosions in Skyrim | Their Skyrim feels them | Only the host's Skyrim | Their Skyrim too, within 64 blocks |
| Bed sleep | Passes Skyrim's night | Nothing | Passes their own Skyrim's night; the world's clock stays the host's |
| Portal network | Binds, opens and travels | "The portal answers only to the host's Skyrim" | Travels through portals the host bound or opened: into other worlds and back to a portal in the Skyrim world their Skyrim is parked in; a far trip between Skyrim places is their own Skyrim's (`EV_PORTAL_TRAVEL`). Binding, keys, Seamstone and the Time-Wound stay the host's |
| Skooma and addiction | Everyone | Everyone | Everyone; their Skooma now also comes from their own loot |
| Hits from Skyrim, dying, digging, click to activate, Minecraft view | Everyone | Everyone | Everyone; a guest's Skyrim attacker is now their own NPC's stand-in |
| Time sync | The host's Skyrim is the world's clock | The world's clock | The world's clock (one clock can't follow two Skyrims) |
| Breach doors | The host's dungeons | "The seal answers only to the host's Skyrim" | The same: the breach table is the host's Skyrim's |
| Deep creatures | Spawn in the host's Skyrim | Not for guests | Not for guests (their Skyrim would have to spawn them near them) |

Features other branches of this wave add (stealth, skills, economy, world, towns, creatures) aren't in this table yet.

**Shared world events** (switch `shared_world_events`; cooldowns in `skycraft-party.txt`). Twice a second the host's
server reads its Skyrim (`party/SharedWorldEvents`, rules in `party/WorldEvents`) and tells every other player in the
Skyrim mirror:

| Event | When | They see | Sound | Camera shake |
|---|---|---|---|---|
| Dragon attack | A dragon in the host's actor table (Skyrim's `kActorDragon`, or a dragon's name from an older plugin) turns hostile and in combat; once a fight, then not for `dragon_attack_cooldown` | Title "Dragon attack!", "Mirmulnir attacks Host" | Ender dragon growl | 0.5 for 1.5 s |
| Dragon slain | A dragon seen alive is dead (anyone's kill; a corpse found isn't one); not again for `dragon_slain_cooldown` | Title "Dragon slain", "Mirmulnir falls before Host" | Ender dragon death, quiet | 0.7 for 2 s |
| Dungeon | The host goes into a dungeon (SkyState's place flags) with a name; the same one once in `dungeon_cooldown` | "Host enters Bleak Falls Barrow" on the action bar | Iron door | none |
| Shout | Skyrim's shout sequence moves: the host, or an actor in their table, started a shout (its voice recovery jumped up); not again for `shout_cooldown` | "Host shouts (3 words)" or "Mirmulnir shouts at Host" on the action bar | A higher growl | 0.3 for 0.6 s |

A guest's client watches its own Skyrim's actor table for dragon attacks and dragons slain the same way and tells the
host's server (`SkyNet.GuestDragon`), which tells the others ("... in their Skyrim") once a cooldown per guest. The shake
is the receiver's own Skyrim's: `kEvPartyShake` through their link (a guest's client passes it on only with its own
switch on). While Minecraft drives the camera Skyrim's shake would be undone by SkyCraft pinning the camera to
Minecraft's eye, so `Party::CameraOffset` jitters that pin instead (up to a tenth of a block, fading); when Skyrim drives
the camera, Skyrim's own `ShakeCamera` does it. Where the player is when watching starts (joining, a load) isn't news.

**`skycraft-party.txt`** (the host's; seconds and blocks):

| Setting | Default | |
|---|---|---|
| `dragon_attack_cooldown` | 120 | Between dragon attacks told about one player's Skyrim |
| `dragon_slain_cooldown` | 15 | Between dragons slain |
| `dungeon_cooldown` | 300 | The same dungeon told again only after this |
| `shout_cooldown` | 30 | Between shouts |
| `guest_range` | 96 | A guest's actors this close to them get stand-ins |
| `guest_actors` | 64 | At most this many of them |
| `guest_loot_burst`, `guest_loot_per_minute` | 64, 120 | Items a guest's Skyrim hands over at once, then a minute |
| `guest_chest_burst`, `guest_chests_per_hour` | 3, 30 | Dungeon chest rolls at once, then an hour |
| `guest_kill_burst`, `guest_kills_per_minute` | 12, 30 | Kills of a guest's own actors that pay, at once, then a minute |

**`/skyparty`** (anyone) lists the players: the host and whether their Skyrim is linked, each guest's Skyrim world and
town and how many stand-ins they have, or that nothing came from them (an older SkyCraft, or a switch off).
**`/skyparty test dragon|slain|dungeon|shout`** shows you one made-up event as the others would see it (title, sound,
shake), nobody else.

**Try it.** Two PCs, each with Skyrim and this build: the host opens the world to LAN, the guest joins (`/join`). Both
logs say `SkyCraft-party: <guest>'s own Skyrim reaches this world` on the host. Then, on the guest's side:

1. Walk up to an NPC of your own Skyrim and hit it: it reacts in your Skyrim (host's log: `SkyCraft-party: hit <guest>'s
   <name>`). Kill a wolf: `SkyCraft-party: <guest>'s Skyrim: Wolf killed by <guest>` and the usual `SkyCraft-loot` lines;
   its drops land at the corpse. Kill a bandit: sometimes their gear drops.
2. Loot a dungeon chest in your Skyrim: the items become Minecraft items in your inventory and leave Skyrim, and the first
   loot of the chest rolls a Minecraft chest (`first loot of dungeon chest ...`). Take a big pile at once: past 64, "Too
   much Skyrim loot at once" and the rest stay in Skyrim.
3. Block a hit with a shield, take hits in iron armor, craft an iron sword, trade with your own merchant: Block, Heavy
   Armor, Smithing and Speech rise in your Skyrim (its `SkyCraft.log`: `skill ...`).
4. Feed bones to a dog of your Skyrim: it's tamed in your Skyrim. Sleep in a bed for a night: your Skyrim passes the night.
5. Let a Skyrim archer shoot at a block next to you: a Minecraft arrow sticks there.
6. Step into a portal the host bound to the Nether: you go there and your Skyrim parks; step back: you're back at the
   portal in your Skyrim.
7. Shared world events: have the host meet a dragon (Skyrim console: `help dragon 4` lists the dragon actor bases, then `player.placeatme <one of them>`): the guest gets
   "Dragon attack!", a growl and a little shake; kill it: "Dragon slain". The host goes into Bleak Falls Barrow: "Host
   enters Bleak Falls Barrow". Alone: `/skyparty test dragon`.
8. Switches off (`guest_parity = off`, `shared_world_events = off`, either side) and restart: none of the above, and a
   guest's crosshair picks the host's stand-ins again as before.

Not played yet: everything here; the stand-ins' positions over an internet connection (ten updates a second, the guest's
own client moves its stand-ins to its Skyrim's actors every frame), how a dragon's breath counts as shouts (it is a shout
in Skyrim, so the shout cooldown keeps it quiet), and whether `GetCurrentShoutLevel` gives an NPC's words (else they read 0).

## Towns and building

Skyrim's towns, its Hearthfire plots and its Dwemer machinery meet Minecraft's blocks. Minecraft decides when something
happened in its world; Skyrim decides who saw it and answers with its own systems. Settings are in
`skycraft-town-rules.txt`; `/skytown` (any player, read only) says what Minecraft sees now: the town, griefing waiting to
be told, doors NPCs opened, the Hearthfire plot and its count, the Dwemer doors near you and their power. Both logs say
`SkyCraft-town`.

**Doors and ladders** (`npc_doors_ladders`). Minecraft sends each section's doors and ladders to Skyrim
(`kRenPassages`). With a plugin that reads them (`kTownPassages`), open doors and ladders aren't NPC walls any more;
closed doors are. An NPC that walks into a closed door it may open (wooden, copper: any but iron) asks Minecraft to open
it (`kInOpenDoor`, at most once a second a door) and waits at it rather than walking round; Minecraft opens it the way a
villager does (`DoorBlock.setOpen`) and closes it again once nobody (a player, an NPC's stand-in, a mob) has been in the
doorway for `door_close_after` seconds. NPC paths go through such doors rather than round them. A door you opened
yourself is left alone. Ladders: Skyrim's engine has no climbing for NPCs, so an NPC standing in a ladder column whose
latest path goal is at least 1.5 blocks above it is lifted up the column (2.4 blocks a second, its position set each
frame, as the push out of blocks does) and, over the top, stepped onto the block the ladder leans on if there's room to
stand there; otherwise, or after 10 seconds, it's let back down to where it started. It plays no climbing animation.
Going down a ladder isn't helped.

**Town crime** (`town_crime`). In a town (Skyrim's `kPlaceTown`: a city, town or settlement, or a building in one), the
host's griefing is a crime: digging Skyrim's geometry out of a cell (`crime_object` gold a block if anything but its land
was there, else `crime_ground`), breaking a block of its ground that digging revealed (`crime_ground`), or breaking a
block someone else placed there (`crime_block`). Who placed a block is kept with the chunk for blocks placed in a town
since this build; blocks placed before, and your own, are never a crime. Griefing within `crime_batch` seconds of the
first is one crime (`kEvTownCrime`, its values added up). Skyrim then looks for a witness: the nearest person within 30
blocks with a crime faction, not your follower, not a summon, not already fighting you, whom Skyrim's own detection says
notices you (`RequestDetectionLevel`), guards first. With one, it raises Skyrim's steal alarm (`Actor::StealAlarm`) for
the witness's crime faction, worth the value, reported at the nearest container or object of Skyrim's (the alarm wants a
reference, and a hole in the ground has none), without the "give it back" warning. Nobody sees it, nothing happens. The
log says who saw it and the faction's bounty before and after.

**Guard comments** (`guard_comments`). When the host places, breaks or digs blocks in town with a guard's stand-in within
16 blocks (`kActorGuard`), Minecraft tells Skyrim (`kEvTownBuild`, at most every 2 seconds). Skyrim takes 45% of those
chances, and only if it's been 20 seconds since any guard spoke: the nearest guard within 16 blocks who sees you and
hasn't spoken in the last minute says a line for what you did (`TownKinds.h`: building, breaking or digging), as
Skyrim's corner notification with the guard's name. It's a notification because CommonLib has no function that makes an
NPC speak a line of ours (`Say` needs a dialogue topic in a plugin file).

**Hearthfire plots** (`hearthfire_blocks`). Skyrim says when you stand outdoors on a Hearthfire house's land: your
location, or a parent of it, is defined in HearthFires.esm and has `LocTypePlayerHouse` or `LocTypeHouse` (no FormIDs,
no names, so it works in any language). Every block placed there by the host (or a guest within 48 blocks of them)
counts for that plot while it stands; a broken one stops counting. Stages: `small_house` (80), `main_hall` (320),
`wings` (720) blocks standing; each is reached once, with a chat line and Minecraft's level-up sound, and Skyrim gets
`kEvHearthfireStage` (logged) and the live count (`McState.hearthBlocks`, logged as it changes). What remains: Skyrim's
own house parts aren't built and its Hearthfire quest isn't moved. Hearthfire builds the house from plans crafted at its
drafting table and carpenter's workbench, through its own scripts; driving them would mean setting quest stages or adding
plan items whose stage numbers and FormIDs can't be found without guessing, and CommonLib wraps no quest SetStage. The
plot's area is Skyrim's location boundary (its cells), not the house's footprint, and the plot needn't be bought.

**Dwemer redstone** (`dwemer_redstone`). Skyrim's own activate event (`TESActivateEvent`) for an activator whose model is
a lever, pull bar, pull chain, button or pressure plate (by its model path, `TownKinds.h`), used by the player or anyone
else, or a pressure plate setting off what it's linked to, sends `kInRedstonePulse` with the block its origin is in.
Minecraft puts a redstone block there for 30 ticks (a lever) or 20 (a button or plate), only if that cell is empty and
nobody stands in it, and takes it away again (and on server stop). Vanilla has no invisible power source, so the host's
exporter leaves that block out of what Skyrim draws; a guest's Skyrim shows it for that moment. Every 2 seconds Skyrim
names the Dwemer doors within 24 blocks (doors, not load doors, whose model is Dwemer: `kInDwemerDoor`, the block at the
door's foot, the cell its origin is in). Minecraft watches redstone power there and in the block above, as an iron door
would feel it (`hasNeighborSignal`); when power comes it sends `kEvRedstoneDoor` and Skyrim opens the door with its own
activation (`ActivateRef`, as the G key does), when it goes Skyrim closes it. A door found already powered isn't worked;
locked doors and doors it would be a crime to open stay shut. Dwemer gates that are activators, not doors, aren't worked.

**Try it** (cheats on; the host):

1. Doors: in Whiterun, build a small room with an oak door between a guard's patrol and the street, or stand inside a
   closed room and let a hostile NPC come for you (anger a bandit, or click one in the console and `startcombat player`).
   When one walks into the door, `SkyCraft.log` says `asked Minecraft to open it`, the door opens, the NPC comes through, and three seconds after
   the doorway is empty `latest.log` says `closed the door ... behind the NPCs`. An iron door stays shut.
2. Ladders: a 4-high pillar of blocks with ladders up one side and the player on top; an enemy that comes for you and
   stands in the ladder column rises up it and steps onto the pillar (`climbs the Minecraft ladder`, `is up the ladder`).
3. Crime: in Whiterun in daylight, near people, dig the road (`/give @s minecraft:diamond_shovel`). After a few seconds
   `latest.log` says `told Skyrim of griefing`, and `SkyCraft.log` either `seen by <name> ... bounty a -> b` (guards come for
   you as for a theft) or `nobody saw it`. Sneak somewhere unseen and dig: nobody saw it. Break your own blocks: no crime.
4. Guards: build next to a guard: now and then `Whiterun Guard: Never seen stone cut that square.` in the top left
   corner; never the same guard twice in a minute.
5. Hearthfire: go to Lakeview Manor's plot (by Lake Ilinalta, near Falkreath); `/skytown` says `on Lakeview Manor`.
   Place blocks: the action bar counts them toward the small house; at 80, the chat says it stands. Break some: the count
   goes down, the stage stays. Set `small_house = 5` in `skycraft-town-rules.txt` for a quicker test.
6. Redstone: in a Dwemer ruin (Mzinchaleft, Nchuand-Zel), pull a lever or press a button: `a redstone pulse at x y z`, and
   redstone dust you placed beside it lights for a moment. Lay dust from a lever (or place a redstone torch) to the foot of a
   Dwemer door that isn't a load door (`/skytown` lists the ones Skyrim named): it opens; break the torch: it closes.

Not played yet: all of it. In particular whether `Actor::StealAlarm` with an object of Skyrim's as the scene behaves as a
theft from a container does, what `RequestDetectionLevel` returns for an NPC who plainly sees the player (anything above 0
counts as seen), whether the lifted NPC stays where it's put each frame, whether pressure plates send activate events,
and which model paths the switches and Dwemer doors really have (the log names each one used).

## Creatures

Minecraft's creatures and Skyrim's people and creatures share the mirror (switches `mobs_fight_npcs`, `wolves_fight_skyrim`,
`night_monsters`, `horses_carry_over`, `creatures_and_light`). Minecraft side: `creatures/` (SkyCreatures, NightMonsters,
SkyHorses, and the pure, unit tested CreatureRules, NightRules, HorseRules); Skyrim side: `Creatures.cpp`, `Horses.cpp`,
`CreatureKinds.h` (pure, unit tested in `skse/tests/CreatureKindsTest.cpp`). All of it is the host's: a guest's Skyrim has no
stand-ins in the host's world.

**Who's what.** From the race's editor ID (`CreatureKinds.h`, cached per race, sent in the actor table): "skeever" and "falmer"
are light-shy (`kActorLightShy`), "draugr", "skeleton" and "vamp" are undead (`kActorUndead`; Dawnguard's frozen Falmer vampires
are both). Ghosts, dragon priests and summoned undead are neither.

**Mobs and NPCs fight.** Every quarter second Minecraft looks at the mobs within 48 blocks of the host. A monster (the monster
category, but not creepers, which would blow craters in Skyrim's towns, nor endermen and zombified piglins, neutral until
provoked) with nothing to do goes for the nearest Skyrim actor within 16 blocks; one after the player goes for an actor within 6
blocks if the player is at least 4 blocks further. Minecraft's undead never go for Skyrim's undead. Monsters run by a brain
(piglins, hoglins, wardens, breezes) ignore it. Minecraft finds no paths across Skyrim's ground (it's collision, not blocks), so a
creature after a stand-in is walked straight at it (`MoveControl`) until it's in reach (archers stop 12 blocks off). Hits on a
stand-in by a creature are kept apart from the player's (`SkyrimActorEntity.CreatureHit`) and sent as `kEvMob` `kMobHit`:
Skyrim applies the damage scaled like the player's hits (Minecraft damage x (5 + level / 4)) with no attacker, with the impact's
blood and sound and a stagger for a strong push. A melee creature's hit on a stand-in counts once a second (its attack goal keeps
restarting without a path and would strike twice as often). Twice a second Minecraft tells Skyrim where its monsters near the host
are (`kMobNear`, at most 24). Five times a second Skyrim looks for actors with one in reach (their bound radius, the creature's,
and their weapon's reach, 1 to 3 blocks; within 2.5 blocks up or down) that fight back (`CreatureKinds::FightsBack`): guards, the
player's followers, the player's enemies, and whoever the creature goes for; children never, and Skyrim's undead leave
Minecraft's undead be unless one goes for them. The actor turns to it, plays `attackStart` (`bashStart` with a bow or staff) on
its animation graph, and 0.3 s later the blow lands in Minecraft (`kInMobHit`): the weapon's damage (a fist or claw: the race's
unarmed damage) x (1 + level / 40) / 5, as from the actor's stand-in, so the creature turns on that actor. Then it waits 1.4 to
2.4 s before the next swing. Skyrim's own combat AI is left alone: NPCs keep fighting the player as before.

**Tamed wolves.** The host's tamed wolf that isn't sitting, with nothing to do, goes for the actor nearest the host (within 16
blocks of them) that is hostile and fighting (`kActorHostile` and `kActorInCombat`), and lets go once it isn't. Its hits are sent
as `kMobByWolf`; Skyrim takes them as a Minecraft hit by the player (its hit pipeline, kill credit; the actor was fighting the
player anyway, so no crime) without training a skill. Decided: the kill's drops and XP are the player's, because vanilla Minecraft
credits a tamed wolf's hits to its owner (`lastHurtByPlayer`): the wolf's owner becomes the stand-in's last hitter and the hit is
recorded for `SkyLoot` (10 seconds, as for the player's own). The player's enemies swing at a wolf next to them; guards and
followers don't.

**Night monsters** (`skycraft-night.txt`, off until `night_monsters = on`). A try every `cooldown` seconds (60 to 120) while it's
Skyrim's night (`night = 20-5`), the player is outdoors and not in a town (`PlaceFlags`), Minecraft's difficulty isn't peaceful and
fewer spawned creatures are alive than the cap; each try brings one out with `chance` (0.4). The spot: a random column 16 to 32
blocks off, searched from 8 blocks over the player's feet to 12 under for Skyrim's ground (or a Minecraft block's top) with room for
a body, out of Skyrim's rock and water, Minecraft's blocks and fluids, block light at most `max_block_light` (0), not a dug cell
(tunnels are the deep creatures'), and out of every player's sight (the deep creatures' check). Within `barrow_distance` (64) of a
Nordic dungeon's entrance (the breach table) the `barrow_creatures` (draugr 3, skeleton 1) come, else the `creatures` (skeever 3,
skeleton 2). Minecraft sends `kEvSpawnCreature` with `kSpawnAtNight`; Skyrim checks the spot isn't in a block or more than half a
block under the land (`TES::GetLandHeight`) and places it like a deep creature: the player's level picks the creature, it counts
against the same cap (`max_alive` here, never past `skycraft-deep.txt`'s), is kept in the co-save and is taken away past
`despawn_distance` (72) or when the player leaves.

| Setting | Default | |
|---|---|---|
| `night` | 20-5 | Skyrim's night, game hours (wraps past midnight) |
| `cooldown` | 60-120 | Seconds between tries |
| `chance` | 0.4 | A try's chance to bring one out |
| `max_alive` | 2 | Night monsters and deep creatures alive at once (never past the deep's `max_alive`) |
| `distance` | 16-32 | Blocks from the player (8 to 48) |
| `despawn_distance` | 72 | Taken away past this |
| `max_block_light` | 0 | Minecraft block light at the spot at most |
| `creatures` | skeever 3, skeleton 2 | Who comes out, with weights |
| `barrow_distance` | 64 | Blocks from a Nordic dungeon's entrance |
| `barrow_creatures` | draugr 3, skeleton 1 | Who comes out near one |

**Horses carry over.** The design: one horse, two forms, never both at once.

- *Into Skyrim.* Once a second Minecraft looks within 12 blocks of the host, in the mirror, outdoors, for their tamed horse (a
  horse, not a donkey or mule). It sends `kEvHorse` `kHorseArrive` with the horse's spot and its speed and health; Skyrim moves the
  Skyrim horse it was before (or, the first time, makes a new one from the first plain horse in the game's data: not unique, not
  essential, Skyrim.esm's "Horse" if there is one) to the spot with Papyrus MoveTo's own function, enables it, makes it the
  player's (ownership, as a bought or tamed horse), sets its SpeedMult from the Minecraft speed (an average 0.225 is 100%, 50 to
  150%) and its health to the same share, and shows `kHorseArrived` for two seconds. Only then does Minecraft put down any rider
  and let its horse go, keeping its stats (speed, jump, health, name) with the host (`skycraft:horse` attachment, saved with the
  player). No answer within five seconds: the Minecraft horse stays, and it's tried again 30 seconds later.
- *Out of Skyrim.* Skyrim reports the player's horse in `SkyState.horse` (the carried one, else the owned horse they last rode,
  if it isn't summoned or essential) with whether it's near (alive, loaded, nobody on it, within 16 blocks). When the host leaves
  the mirror (a portal, `/skyview`) with it near, Minecraft makes a tamed, saddled Minecraft horse at their side with the kept stats
  (or the Skyrim horse's SpeedMult and an average horse's health and jump) and sends `kHorseDepart`; Skyrim disables its horse
  (`kHorseAway`, kept in the co-save) until it comes back the same way.
- *Death.* The Minecraft horse dying sends `kHorseLost`: Skyrim's disabled horse stays gone. A Skyrim horse marked away that this
  Minecraft world has no horse out for (another world, a lost record) comes back to the player instead.

Partial: the coat, markings and armor don't cross (each form keeps its own look), Skyrim's SpeedMult may only show once the horse's
movement updates, the Minecraft form is made with the `summon` command (its owner, tameness and saddle in the entity data, both ways a
saddle has been written) and is checked tamed afterwards, and whether a ridden horse goes through SkyCraft's portals with its rider
(rather than being left at the portal) only playing can tell; a horse led or ridden back into the mirror any way works.

**Creatures and light.** Every frame Skyrim pushes light-shy actors within 64 blocks out of light level 8 or more (the strongest of
the light-emitting blocks Minecraft sends, `kRenLights`, falling off a level a block; walls in between aren't counted): away from
the brightest block at 1.5 blocks a second, faster the brighter (up to 6), never into a block. A torch keeps them about 6 blocks
off (a lantern or glowstone 7), a soul torch 2, a redstone torch not at all. Once a second Minecraft looks at undead stand-ins: outdoors, in Skyrim's daylight
(6:00 to 19:30), with open sky (no Minecraft block above, no Skyrim geometry within 24 blocks above), not in water or rain, not
burning, not essential, each catches fire with a zombie's odds (56% a second in full daylight) for 8 seconds. The stand-in's fire
damage (1 a second) is sent as `kMobBySun` with the fire flag: Skyrim takes it with no attacker and casts Skyrim's campfire burn
on the body for the flames.

**Try it** (cheats on; Minecraft chat `/summon`, Skyrim console `player.placeatme`):

1. In Whiterun, `/summon minecraft:zombie ~4 ~ ~`. It goes for the nearest guard or citizen (`SkyCraft-creatures: Zombie goes for
   ...`); the guard turns and swings, the zombie fights the guard, and the guard's health drops in Skyrim without anyone
   turning on you. A Skyrim citizen it goes for punches back. Stand closer to the zombie than any NPC: it comes for you.
2. `/summon minecraft:skeleton` near a draugr in a barrow's entrance, or `player.placeatme 0003B547` (a draugr): a zombie
   leaves the draugr be, a spider (`/summon minecraft:spider`) doesn't, and the draugr swings at the spider but not the zombie.
3. Tame a wolf (`/summon minecraft:wolf`, bones), then pick a fight with bandits (Embershard Mine's camp outside Riverwood): the
   wolf goes for the bandit fighting you; the bandit swings at it. Kill the bandit with the wolf alone: its drops and XP are yours
   (`SkyCraft-loot`), and your skills don't move.
4. `night_monsters = on`, restart, outdoors at night away from towns (`set gamehour to 23` in Skyrim's console): within a few
   minutes `SkyCraft-night` lines, and a skeever or skeleton nearby; near Bleak Falls Barrow, draugr. None in Riverwood, none
   indoors, none by your torches.
5. Place torches, then `player.placeatme 00023AB7` (a skeever): it won't come within about 6 blocks of them. In Blackreach or any
   Falmer hive, Falmer keep off your glowstone.
6. Daylight: `set gamehour to 12`, outdoors, `player.placeatme 0003B547` (a draugr): it catches fire within a few seconds and
   burns down; under a bridge, in rain or indoors it doesn't.
7. Horses: `/summon minecraft:horse`, tame it with apples or by riding, in Tamriel Wilds (portal), ride it through the portal
   back into Skyrim: it becomes your Skyrim horse (`SkyCraft-horse`); ride it in Skyrim, then take a portal out with it near: it
   comes along as a Minecraft horse, and Skyrim's is gone until you bring it back. Kill it in Minecraft: it's gone in Skyrim too.

Not played yet: everything here; in particular how Skyrim's actors take the injected attacks, how often Minecraft's monsters
switch back to the player, and the night monsters' and horses' numbers.

## Tooling

### Paired backups

Switch `paired_backups` (on by default; Skyrim's side needs a Minecraft that sends the switch, `kFeatPairedBackups`). Every
time Skyrim loads a save (from the menu, a quickload, the reload after dying) while Minecraft is connected, the save and
Minecraft's world are backed up together, as a pair named by one stamp, the local time of the load (`YYYY-MM-DD_hh-mm-ss`):

| Half | Where | What |
|---|---|---|
| Skyrim | `Documents\My Games\Skyrim Special Edition\SkyCraft Backups\<stamp>\` (beside the `Saves` folder, or beside the folder `sLocalSavePath` in Skyrim.ini names) | The loaded save's `.ess` and its SKSE co-save `.skse` |
| Minecraft | `skycraft-backups\<stamp> <world>.zip` in the instance's `.minecraft` folder (the bundled one: `%LOCALAPPDATA%\SkyCraft\Prism\instances\SkyCraft\.minecraft`) | The whole world folder (every dimension), laid out like Minecraft's own backups, without `session.lock`. The zip's comment names the Skyrim save |

How it goes: the plugin notes the save's name when SKSE says a load starts (`kPreLoadGame`). Once the load is done and Minecraft
is connected with the switch on, it picks the stamp, sends Minecraft `kInPairedBackup` (the stamp and the save's name) and copies
the save on a thread of its own (into `<stamp>.partial`, renamed to `<stamp>` when done, so a copy cut short never looks like a
backup). Minecraft, on its integrated server: saves the players and every chunk to disk, holds autosaves off (as `/save-off` does),
and zips the world on a thread of its own (into `<name>.zip.part`, renamed when done); then autosaves come back, the zips past the
newest `keep` are deleted, and it answers `kEvPairedBackup` with `keep`, so Skyrim deletes its older copies too. A save loaded
before Minecraft has connected (Minecraft still starting) is copied once it connects. A request that comes while Minecraft has no
world of its own open (the title screen, or a guest in a friend's world) waits until one opens, and a newer load replaces it.

**`skycraft-backups.txt`**: `keep = 5`, the pairs to keep (0 keeps all). The Minecraft world can be hundreds of MB once Tamriel
Wilds, Home or the Nether have been explored, so mind the disk.

**Going back to a pair** (close both games first): copy the two files from `SkyCraft Backups\<stamp>` into Skyrim's `Saves`
folder (with Mod Organizer's profile-specific saves, the profile's `saves` folder), and unzip `<stamp> SkyCraft.zip` into the
instance's `.minecraft\saves` folder, replacing the `SkyCraft` folder. Load that save.

**Try it**: start Skyrim with SkyCraft and load a save. SkyCraft.log says `paired backups: <save> was loaded: copying it as
<stamp> ...`, then `copied to ...` and `Minecraft backed up its world for <stamp> (x MB); keeping the newest 5 pairs`.
Minecraft's latest.log says `SkyCraft-backup: <stamp> SkyCraft.zip written for Skyrim's <save>: n files, x MB (saving a ms,
zipping b ms)`. Both folders have the pair. Load six more times (a quickload is quickest): the oldest pair goes from both.
Load a save from the main menu before Minecraft is ready: the pair is made when it connects. `paired_backups = off` in
`skycraft-features.txt`, restart Minecraft: no more pairs.

Not tried in a game yet: the save folder under Mod Organizer's profile-specific saves (the copy reads through MO2's virtual
folder, which should work), and how long a big world's zip takes.

### Far block detail and profiling

**Where the frame time goes.** SkyCraft.log has a `perf:` line every minute (every 10 seconds with `bDiagnostics = 1`):
Skyrim's frame rate and frame times, then each part of SkyCraft's work per frame. For the blocks:

| In the perf line | What it is |
|---|---|
| `block drawing 1.84 ms/frame (worst call 6.2 ms, 1.0 calls/frame)` | The CPU time of drawing Minecraft's blocks, entities and their shadows into Skyrim's frame (`RenderPasses`), averaged over every frame; the worst call is the worst frame |
| `in-frame draw` | The same plus setting it up inside Skyrim's frame |
| `block sections drawn 212 a frame (most 240)` | 16x16x16 sections drawn into the scene, over the frames that drew blocks |
| `block triangles 1200000 a frame (most ...)` | Triangles sent to the graphics card for blocks in all passes: sun shadows, the scene, water and glass, and Skyrim's depth copies (each solid section goes into each copy) |
| `shadow-casting sections` | Sections drawn into the sun's shadow map |
| `far sections without shadows` | With far_block_detail on: sections left out of the shadow map for being far |

The GPU time of each pass is logged too (`SkyCraft's drawing per frame: GPU ...`), with `bDiagnostics = 1` only. Minecraft's side,
every minute in latest.log: `SkyCraft-render: last minute, n block sections meshed for Skyrim (t triangles): a ms a frame on
average, worst frame b ms, over f frames; w sections waiting, s in Skyrim`. Meshing happens when blocks change or come into
range, at most 12 sections and 3 ms a frame.

**The switch.** `far_block_detail` (off by default; Skyrim's side reads it as `kFeatFarBlockDetail`). Every solid section is drawn
two to five times a frame: into the sun's shadow map, into the scene, and into each of the depth copies Skyrim's post-processing
reads (up to three). The shadow pass took every section within its reach, about 250 blocks round the camera, though the shadow
map covers only 64 blocks either side of the camera across the sun's path. With the switch on, a section whose nearest corner is
more than 64 blocks away is left out of it. The trade-off:

- Kept: everything within 64 blocks; far blocks are still drawn whole, lit, fogged, in Skyrim's depth (so Skyrim's fog, depth of
  field and light shafts treat them as before) and darkened by Skyrim's own shadows (trees, buildings, mountains).
- Lost: far blocks' sun shadows, on Skyrim's ground and on each other (a far wall no longer shades the far blocks behind it), and
  the shadow a far, tall build casts toward you along a low sun.
- Saved: one of a far section's two to five draws a frame, its triangles in the shadow pass, and the draw calls. Most where
  much is built far away; nothing where everything built is near. The perf line's `shadow-casting sections` and
  `far sections without shadows` show how much.

Not chosen: leaving far sections out of Skyrim's depth copies (more saved, but Skyrim's fog, depth of field and light shafts
would see through far blocks), and leaving small decorative blocks out far away (Minecraft's meshes don't tell them apart
from leaves and glass, which would vanish too).

**Try it**: build or find a big area of blocks, play a minute, and read the perf line. Set `far_block_detail = on`, restart
Minecraft: SkyCraft.log says `far block detail on: block sections more than 64 blocks away cast no sun shadows`; the next perf
lines show fewer shadow-casting sections and triangles, and `block drawing` should be lower. Far blocks' shadows go; near ones
stay.

## Maps

### Skyrim maps

Minecraft's overworld is the mirror of Skyrim's world: a void where Skyrim's ground is triangles you collide with, plus the
blocks players placed. A filled map there used to be empty apart from placed blocks. Now vanilla's own map update (on the
server, for a map held in a hand) draws it, and where it finds no block in a column, the pixel shows Skyrim's instead
(`MapItemMixin` wraps the one call that stores a pixel's colour, `MapItemSavedData.updateColor`, so vanilla's block search,
shading and water stay as they are for every other pixel):

- **What it shows.** Skyrim's highest surface over the column, from the triangles Minecraft holds (`SkyCollision`: the
  regions Skyrim streams round the player, about 45 blocks each way, kept until you leave that Skyrim world). The land by
  the material Skyrim sends with it: grass green, dirt, mud, sand, snow, ice, rock gray, and roads (the land's gravel and
  cobbled stone) a pale earth tone. Grass on a slope over about 37 degrees shows as dirt and any land over about 60 degrees
  as rock (snow holds to about 70), as Skyrim paints its cliffs. Rocks and cliffs that are objects are rock; trees (their
  trunks: Skyrim's leaves have no collision) and wood are brown; buildings and other things that can't be dug a light stone
  gray (Skyrim sends no material for them). The rules are `maps/MapRules` (unit tested).
- **Shading.** Vanilla's own north to south shading: a pixel higher than the one north of it is brighter, lower darker, with
  its checkered dither, so hills read as hills. A pixel next to nothing known (the edge of what's been seen) is shaded level,
  not as a drop into the void.
- **Water.** Skyrim's water over its ground is vanilla's water colour, darker where deeper: the water grid round you (16
  blocks), remembered as you walk, and the water of the cells Skyrim has loaded (below).
- **Your blocks.** A Minecraft block at or above Skyrim's ground in its column wins: the map shows it as vanilla does. A
  block buried under Skyrim's ground (in a tunnel) doesn't show, as nothing under the ground does. A dug hole shows its floor.
- **Further out.** Where Minecraft holds no collision yet, Minecraft asks Skyrim for a coarse square of samples over the map
  near Skyrim's player (`kEvMapSample`, 20 x 20, at least a pixel apart). Skyrim answers for the cells it has loaded (about
  150 blocks round its player): the land's height (`TES::GetLandHeight`), its texture's material (as digging reads it) and
  the cell's water there. Minecraft keeps the answers (the newest 48) and draws them blended between samples, but only if
  their land agrees with its own collision where both are known (a median within 2.5 blocks); otherwise it logs
  `DISAGREES` and leaves them out. It asks again once Skyrim's player has moved 48 blocks, or after 30 seconds.
- **Markers.** Skyrim lists, about every two seconds, the map markers its own map shows within 1024 blocks of the player
  (seen or heard of, or discovered; the nearest 48). Minecraft puts each on a map of that area as a banner with the place's
  name: yellow for cities, towns and the holds' castles, lime for farms, mills and other homesteads, red for caves, ruins,
  mines and other dungeons, orange for camps and forts, purple for shrines, standing stones and other sacred places, cyan for
  the ships to and from Solstheim, white for landmarks and anything else; light gray while it's only been heard of. A
  marker comes off a map once Skyrim no longer shows it (and would have listed it). Indoors the banners stay as they were.
- **Never in other dimensions**: a map of the Nether, Tamriel Wilds or Home is vanilla's.

**Try it**: `/give @s minecraft:map`, then right click with it in Skyrim's outdoors (near Riverwood, say) and hold the
filled map.

1. The ground round you appears at once: green grass, the road through the village pale, its houses gray, the river blue,
   the slopes shaded. Within a few seconds the rest of the map fills in more coarsely. The log says
   `SkyCraft-maps: Skyrim's land sampled from X Z every 7 blocks: N of 400 known; agrees with its collision at M points`
   (Skyrim's SkyCraft.log: `land under a Minecraft map sampled from ...: N of 400 in loaded cells`).
2. Walk on with the map in hand: it fills in as you go, as a vanilla map does.
3. Place a few blocks: they show over the ground. Dig a hole: its floor shows darker.
4. Riverwood shows as a yellow banner named Riverwood; Bleak Falls Barrow, if heard of but not visited, as a gray one. The
   log says `SkyCraft-maps: Skyrim's markers on a map at X Z: N shown, 0 taken off` (Skyrim's: `N map markers in Tamriel`,
   then `N map markers within 1024 blocks of the player (D discovered), the nearest ...`). Discover a new place: its banner
   turns its colour within a couple of seconds.
5. Take a map in the Nether: vanilla's. Switch `skyrim_maps` off: maps draw as before, and Skyrim's banners come off the next
   time a map is held.

Limits: a map shows only what Minecraft has of Skyrim's world (round where you've walked in that Skyrim world, and Skyrim's
loaded cells), though what's drawn stays on the map. Skyrim's banners aren't saved with the map (vanilla saves only its own
banners): they come back when it's held near the place again. In multiplayer only the host's Skyrim counts: its collision,
its land samples, its markers and discoveries. A map made inside a dungeon draws the tops of its rooms.

## Planned

| Feature | Notes |
|---|---|
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

Combat and stealth added these within 13, without a version bump (an older half ignores them: an older Minecraft never sets
the bits and sends zeros, an older plugin ignores them):

| Addition | Direction | Used by |
|---|---|---|
| `FeatureBits` `kFeatWallsHideYou` (bit 0), `kFeatSneakAttacks` (1), `kFeatPowerAttacks` (2), `kFeatLightStealth` (3) in `McState.features` | Minecraft to Skyrim | The four switches, which Skyrim runs |
| `HitFlags` `kHitPowerAttack` (bit 4), `kHitCrouching` (bit 5) in `kEvHitActor`'s flags | Minecraft to Skyrim | Power attacks: a fully charged melee swing that was a sprint or critical hit. Sneak attacks: the player who landed it was crouching |
| `McState.eyeBlockLight` (0xDC), `eyeSkyLight` (0xDD), `eyeLightFlags` (0xDE, `kEyeLightValid` bit 0), 5 bytes pad (was `reservedStealth`) | Minecraft to Skyrim | Light and stealth: block light and sky light (0-15) at the player's eyes, valid in the mirror with the switch on |

The world features (weather sync, Skyrim farming, shrine blessings) added these within 13, without a version bump. They
fill the space the groundwork set aside (`SkyState.reservedWeather` 0x80, `McState.reservedWeather` 0xE4), so an older
plugin sends zeros (no weather known, nothing aimed at, no request answered) and an older Minecraft never asks; an older
plugin ignores the request and the input type, an older Minecraft the new fields:

| Addition | Direction | Used by |
|---|---|---|
| `SkyState.weather` (0x80, u8: `WeatherKind` 0 none or indoors, 1 clear, 2 cloudy, 3 rain, 4 snow, 5 thunderstorm), `weatherFlags` (0x81: `kWeatherRaining` 1, `kWeatherSnowing` 2, `kWeatherRefused` 4), `weatherAck` (0x84, u32: the last `McState.weatherSeq` Skyrim handled) | Skyrim to Minecraft | Weather sync: Skyrim's weather where its player is, what's falling now, and its answer to Minecraft's last request |
| `McState.weatherSeq` (0xE4, u32), `weatherWant` (0xE8, u8 `WeatherKind`, 0 once answered) | Minecraft to Skyrim | Weather sync: Minecraft's weather was changed by someone (`/weather`, a mod); Skyrim handles each number once |
| `FeatureBits` `kFeatWeatherSync` (bit 12 of `McState.features`) | Minecraft to Skyrim | Weather sync's switch: off, Skyrim refuses requests |
| `SkyState.aim` (0x82, u8: `AimFlags` `kAimHarvest` 1) | Skyrim to Minecraft | Skyrim farming: the crosshair is on a plant Skyrim can harvest, so a hoe doesn't till under it |
| `kInBlessing` (17) in the input ring, `BlessingKind` 1 Akatosh to 9 Zenithar, 10 Auriel, 11 other | Skyrim to Minecraft | Shrine blessings: code = the kind, a = game hours left x 1000, b = Skyrim's timescale x 1000 |

Economy added these within 13, without a version bump (an older plugin ignores the events, so Minecraft calls a sale off
when no answer comes; an older Minecraft ignores the input types and leaves the feature bit off, so Skyrim sends no food):

| Addition | Direction | Used by |
|---|---|---|
| `FeatureBits` `kFeatSkyrimFood` (bit 8) in `McState.features` | Minecraft to Skyrim | Skyrim food fills hunger: Skyrim sends `kInSkyrimFood` only while it's set |
| `kInSkyrimFood` (15), `FoodKind` | Skyrim to Minecraft | The player ate a food in Skyrim: code = Minecraft food points, a = saturation modifier x 1000, b = the food's FormID, c = FoodKind (`FoodValues.h`) |
| `kEvMerchantGold` (19) | Minecraft to Skyrim | Selling to merchants: formId = the merchant, flags = a request number. Skyrim answers with `kInMerchantGold` |
| `kInMerchantGold` (16) | Skyrim to Minecraft | a = the merchant's FormID, b = the gold it can pay (its merchant chest's, else its purse; -1: not here to trade), c = the request number |
| `kEvSellToMerchant` (20) | Minecraft to Skyrim | Minecraft took the goods: formId = the merchant, a = gold to pay, b = items (log), flags = the request number. Skyrim moves that much Gold001 from the merchant's chest (or purse) into the player's inventory, never more than it has |

Shared world events added these within 13, without a version bump (an older half ignores them: an older plugin never sets
the bit or the fields and ignores the event type and feature bit; an older Minecraft ignores the bit and fields and never
sends the event):

| Addition | Direction | Used by |
|---|---|---|
| `ActorFlags` `kActorDragon` (bit 18) in `ActorRecord.flags` | Skyrim to Minecraft | The actor's race has the ActorTypeDragon keyword: dragon attacks and dragons slain |
| `SkyState` `shoutSeq` (u16, 0xA0), `shoutWords` (u8, 0xA2), `shoutFlags` (u8, 0xA3, `ShoutFlags` `kShoutByPlayer` 1, `kShoutByDragon` 2), `shoutActor` (u32, 0xA4); were `reservedParty[8]` | Skyrim to Minecraft | The sequence bumps each time the player or an actor in the actor table starts a shout (its voice recovery jumps up); who, and the words of power when Skyrim can tell (0 otherwise). Still 0 with the switch off |
| `FeatureBits` `kFeatSharedWorldEvents` (bit 26) in `McState.features` | Minecraft to Skyrim | Skyrim watches for shouts and shakes its camera only while it's set |
| `kEvPartyShake` (29) | Minecraft to Skyrim | a = strength (0 to 1), b = seconds (0.1 to 3): a small camera shake for something that happened to another player |

Guest parity adds nothing to the protocol: a guest's Skyrim gets the events it already knows (`kEvHitActor`, `kEvTakeItem`,
`kEvSkillUse`, `kEvArrowStuck`, `kEvExplosion`, `kEvSleep`, `kEvCompanion`, `kEvPortalTravel`) on its own link, from its
own client, which got them from the host's server.
Towns and building added these within 13, without a version bump (an older half ignores them: an older plugin skips the
render type and the event types and never sets the fields and bits; an older Minecraft skips the input types and ignores
the fields and the actor bit):

| Addition | Direction | Used by |
|---|---|---|
| `SkyState.townFlags` (0x90, `TownFlags`: `kTownPassages` bit 0, `kTownHearthfirePlot` bit 1), `SkyState.hearthPlot` (0x94; was `reservedTown`) | Skyrim to Minecraft | `kTownPassages`: this plugin reads `kRenPassages`, so Minecraft leaves ladders and open doors out of `kRenSolids` (with an older plugin they stay solid, as before). `kTownHearthfirePlot`: the player is outdoors on a Hearthfire house's land, `hearthPlot` its BGSLocation FormID |
| `McState.hearthPlot` (0xEC), `McState.hearthBlocks` (0xF0; was `reservedBuild`) | Minecraft to Skyrim | The plot Minecraft counts for and the blocks standing on it (Skyrim logs them) |
| `FeatureBits` `kFeatNpcDoorsLadders` (16), `kFeatDwemerRedstone` (17), `kFeatHearthfire` (18) | Minecraft to Skyrim | The switches whose Skyrim side Skyrim runs itself. 19 to 21 are set aside for towns |
| `ActorFlags` `kActorGuard` (bit 16) | Skyrim to Minecraft | Guard comments: Minecraft sends `kEvTownBuild` only with a guard near. Bit 17 is set aside for towns |
| `kRenPassages` (14): `RenPassages {sx, sy, sz, count}` + `RenPassage {u8 x, y, z, flags}[count]`, `PassageFlags` (bits 0-1 kind: 1 door, 2 iron door, 3 ladder; bit 2 open; bits 3-4 facing, Minecraft's 2D value; bit 5 a door's upper half) | Minecraft to Skyrim | NPC doors and ladders: each section's, after its `kRenSolids`. 15 is set aside for towns |
| `kInOpenDoor` (19): a, b, c = the door's block | Skyrim to Minecraft | An NPC walked into a closed door: Minecraft opens it (not iron) and closes it once the doorway is empty |
| `kInRedstonePulse` (20): a, b, c = the switch's block, code = ticks | Skyrim to Minecraft | Dwemer redstone: a lever, button or plate was used |
| `kInDwemerDoor` (21): a, b, c = the block at the door's foot, code = 1 open | Skyrim to Minecraft | Dwemer redstone: resent every 2 s per door within 24 blocks; forgotten after 6 s. 22 is set aside for towns |
| `kEvTownCrime` (23): formId = `TownCrimeKind` (1 ground, 2 object, 3 block), a/b/c = where, d = gold, flags = blocks | Minecraft to Skyrim | Town crime: Skyrim finds a witness and raises a steal alarm |
| `kEvTownBuild` (24): a/b/c = where, flags = `TownBuildKind` (1 place, 2 break, 3 dig) | Minecraft to Skyrim | Guard comments |
| `kEvHearthfireStage` (25): formId = the plot, flags = `HearthfireStage` (1 small house, 2 main hall, 3 wings), a = blocks | Minecraft to Skyrim | Hearthfire blocks: a stage reached (logged) |
| `kEvRedstoneDoor` (26): a/b/c = the door block's centre, flags = 1 powered | Minecraft to Skyrim | Dwemer redstone: open the door on power, close it when the power goes |

Creatures added these within 13, without a version bump (an older plugin never sets the bits or fields and drops the event
types; an older Minecraft ignores the bits, the fields and the input type):

| Addition | Direction | Used by |
|---|---|---|
| `ActorFlags` `kActorLightShy` (bit 14), `kActorUndead` (bit 15), in `ActorRecord.flags` | Skyrim to Minecraft | From the race (`CreatureKinds.h`): light-shy creatures keep out of strong light; undead burn in daylight and Minecraft's undead leave them be |
| `SkyState.horse` (0x98), `SkyState.horseInfo` (0x9C; was `reservedCreatures`) | Skyrim to Minecraft | Horses carry over: the player's horse, and `HorseInfo` in bits 0-7 (`kHorseNear` 1, `kHorseAway` 2, `kHorseArrived` 4, `kHorseCarried` 8), its SpeedMult in bits 8-15, its health (percent) in bits 16-23 |
| `FeatureBits` `kFeatMobsFightNpcs` (22), `kFeatWolvesFightSkyrim` (23), `kFeatCreaturesAndLight` (24), `kFeatHorsesCarryOver` (25) | Minecraft to Skyrim | The switches whose Skyrim side Skyrim runs itself (NPCs swinging back, light-shy pushes, the horse in SkyState) |
| `kEvMob` (27), `MobEvent` `kMobHit` 1 / `kMobNear` 2 in flags bits 0-7 | Minecraft to Skyrim | `kMobHit`: formId = the actor, a = Minecraft damage, b/c = push direction, d = push strength, flags bits 8-15 = `HitFlags`, bits 16-19 = `MobAttacker` (0 monster, 1 the player's wolf, 2 the sun), weapon = `HitWeapon`. `kMobNear`: formId = the creature's entity id, a/b/c = its feet, d = half its width, flags bits 8-15 = `MobTraits` (`kMobUndead` 1, `kMobPlayersWolf` 2), weapon = the actor it goes for |
| `kEvHorse` (28), `HorseAction` `kHorseArrive` 1, `kHorseDepart` 2, `kHorseLost` 3 in flags | Minecraft to Skyrim | formId = the Skyrim horse (0: none yet), a/b/c = the arrival's spot, d = its yaw, weapon = the Minecraft horse's speed x 10000 (bits 0-11), maximum health (12-19), health (20-27) |
| `kInMobHit` (23) in the input ring | Skyrim to Minecraft | An actor's blow on a Minecraft creature: a = its entity id, b = Minecraft damage x 100, c = the actor's FormID, code = `HitWeapon` |
| `SpawnOrigin` in `kEvSpawnCreature`'s weapon: `kSpawnFromDark` 0, `kSpawnAtNight` 1 | Minecraft to Skyrim | Night monsters: on Skyrim's ground outdoors rather than in a dug tunnel. An older plugin checks for a dug tunnel and refuses the spot |

Paired backups added these within 13, without a version bump (an older Minecraft never sets the bit, so an older plugin's
Skyrim side stays off; an older plugin ignores the event type and an older Minecraft the input type):

| Addition | Direction | Used by |
|---|---|---|
| `FeatureBits` `kFeatPairedBackups` (bit 28) in `McState.features` | Minecraft to Skyrim | `paired_backups` is on: Skyrim copies each save it loads |
| `kInPairedBackup` (28) in the input ring | Skyrim to Minecraft | One text in a run of events: code 0 with a = the request id, b = the text's length in bytes (1 to `kPairedBackupTextMax`, 512), then code 1, 2, ... each with the next 12 bytes in a, b, c (little endian). The text is UTF-8 `<stamp>\|<save name>` |
| `kEvPairedBackup` (32) | Minecraft to Skyrim | The world backup for a request is done: formId = the request id, flags = 1 if it was written, a = the pairs to keep (0 all), b = its size in MB |

Far block detail added this within 13 (an older Minecraft never sets the bit, so the shadows stay as they were):

| Addition | Direction | Used by |
|---|---|---|
| `FeatureBits` `kFeatFarBlockDetail` (bit 29) in `McState.features` | Minecraft to Skyrim | `far_block_detail` is on: block sections more than 64 blocks away cast no sun shadows |

Skyrim maps added these within 13, without a version bump (an older Minecraft never sets the bit, so Skyrim leaves the table
alone, and never reads it; an older plugin never writes the table, which stays all zeros, "nothing yet", and ignores the event,
so Minecraft, getting no answer three times running, stops asking for a minute):

| Addition | Direction | Used by |
|---|---|---|
| `FeatureBits` `kFeatSkyrimMaps` (bit 7) in `McState.features` | Minecraft to Skyrim | `skyrim_maps` is on: Skyrim lists its map markers in the map table and answers `kEvMapSample` |
| `MapTable` at `kOffMapTable` 0x16100 (0xE00 bytes, in the 0xF00 set aside for maps between the actor table's end, 0x16040, and the event ring; seqlock, written whole when something in it changed) | Skyrim to Minecraft | worldId (0x04, the worldspace the markers are in; 0 indoors), markerCount (0x08), markerVersion (0x0C, moves when the markers change), centerX, centerZ (0x10, where the player was), markerReach (0x18: every visible marker closer than this to the centre is listed, 1024 or less when more than 48 were), then 48 `MapMarkerRecord`s from 0x20, nearest first: x, z (floats, MC coords), formId (the marker reference), u16 type (`MapMarkerType`, RE::MARKER_TYPE's numbering, places only), u8 flags (`kMarkerVisible` 1: on Skyrim's map; `kMarkerDiscovered` 2: can fast travel there), name[24]. From 0x7A0 the answer to the last `kEvMapSample`: sampleRequest (its request number), sampleX, sampleZ (the north-west sample's block), sampleSpacing, sampleWorld, sampleKnown, then 20 x 20 `MapSample`s from 0x7C0: i16 height (MC y x 4 of the land, or of the water's surface when water > 0; `kSampleUnknown` -32768 outside the loaded cells), u8 material (DigMaterial), u8 water (blocks deep) |
| `kEvMapSample` (39) | Minecraft to Skyrim | A map wants Skyrim's land under it: a, c = the north-west sample's x and z (MC block coords, the current world's terms), b = blocks between samples (1 to 256), flags = request number (never 0). Event 40 and input types 35 and 36 are set aside for maps |

## Testing a build

1. Back up the Skyrim saves folder.
2. Install the jar into the instance's `mods` folder and the DLL into `Data/SKSE/Plugins`, keeping
   the previous ones in `dev-backups`.
3. Play the feature tests for the build.
4. Read `Documents/My Games/Skyrim Special Edition/SKSE/SkyCraft.log` and the instance's
   `logs/latest.log`, searching for the log tags above.
