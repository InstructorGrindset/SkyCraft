# SkyCraft fork features

Features this fork (InstructorGrindset/SkyCraft) adds on top of upstream SkyCraft 0.1.2. The base
game features (movement, blocks, digging, combat, skills) are described in the README and
[DESIGN.md](DESIGN.md). Every config file below lives in the Minecraft instance's `config` folder
and is written with defaults the first time it's needed; delete a file to get the new defaults
back.

Status: **Tested** means played and confirmed in game, **Built** means it compiles and passes unit
tests but hasn't been played yet.

## Feature map

| Feature | What it does | Minecraft side | Skyrim side | Config | Log tag | Status |
|---|---|---|---|---|---|---|
| Kill drops | Skyrim animals you kill drop Minecraft meat, leather and bones | `loot/SkyLoot`, `loot/LootRules` | actor table (existing) | `skycraft-loot.txt` | `SkyCraft-loot` | Tested |
| Kill XP | Credited kills drop XP orbs, more for higher levels | `loot/KillXp`, `loot/SkyLoot` | actor table level | none | `SkyCraft-loot` | Built |
| Item conversion | Food, potions, ores, hides and salvaged gear you loot in Skyrim become Minecraft items | `loot/SkyBridge`, `loot/ItemRules` | `Loot.cpp` container sink, `Loot::Take` | `skycraft-items.txt` | `SkyCraft-loot` | Tested |
| Dungeon chests | First loot of each dungeon chest also rolls a Minecraft chest loot table | `loot/DungeonLoot` | `Loot::Place` (dungeon flag), container id and chest flag in item events | `skycraft-dungeons.txt`, `skycraft-opened-chests.txt` | `SkyCraft-loot` | Built |
| Merchants | Right click a Skyrim vendor with an empty hand for a villager trade screen; town prices and supply and demand; Speech XP | `trade/SkyTrade`, `trade/TradeRules`, `trade/Currency`, `trade/Demand`, `combat/SkyrimActorEntity.interact` | `Loot::VendorMask`, `Loot::Place` (town) | `skycraft-trades.txt`, `skycraft-towns.txt` | `SkyCraft-trade` | Built |
| Time sync | Minecraft's clock follows Skyrim's game hour | `loot/SkyTime` | `SkyState.gameHour` (existing) | none | `SkyCraft` | Built |
| Bed sleep | A full night in a Minecraft bed passes the night in Skyrim | `loot/SkyTime` | `Loot::PassTime` (kEvSleep) | none | `SkyCraft` | Built |
| Deep mining | Bedrock 64 blocks down, ores by depth, deepslate layer | `world/SkyDig` | `Dig.cpp` (existing) | none | `SkyCraft` | Tested |
| Deep creatures | Skeevers, skeletons and draugr spawn in dark tunnels you dug, by depth | `deep/SkyDeep`, `deep/DeepRules` | `Spawn.cpp` (kEvSpawnCreature, kActorSpawned) | `skycraft-deep.txt` | `SkyCraft deep` | Built |
| Gear sets | Iron through daedric weapons and armor, new materials, recipes, original textures | `gear/SkyGear`, `gear/GearTiers`, `mixin/SmithingMixin` | none | recipes are data in the jar | none | Built |

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
| `kEvSleep` | Minecraft to Skyrim | Bed sleep |
| `kEvSpawnCreature` | Minecraft to Skyrim | Deep creatures |

## Testing a build

1. Back up the Skyrim saves folder.
2. Install the jar into the instance's `mods` folder and the DLL into `Data/SKSE/Plugins`, keeping
   the previous ones in `dev-backups`.
3. Play the feature tests for the build.
4. Read `Documents/My Games/Skyrim Special Edition/SKSE/SkyCraft.log` and the instance's
   `logs/latest.log`, searching for the log tags above.
