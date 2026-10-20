# Skyrim to Minecraft conversions

What the default rules in `skycraft-items.txt` turn Skyrim items into. The defaults ship in the jar as `fabric/src/main/resources/dev/skycraft/loot/skycraft-items.txt` (`ItemRules.DEFAULT_TEXT`; several thousand rules no longer fit a Java string). Every name below is a real name read from Skyrim.esm, Update.esm, the three DLCs, the Creation Club plugins and USSEP, or one the reviewed conversion proposals confirmed, with the tags the SKSE plugin gives it (its VendorItem keywords). Counts are Minecraft items per Skyrim item.

A `config/skycraft-items.txt` you never edited gets the new defaults by itself when Minecraft starts (`ItemRules.OLD_DEFAULTS`, the old file kept as `skycraft-items.txt.old`); one you edited keeps its rules: delete it, or merge by hand, to get these.

First match wins, so every rule has to be reachable: `ItemRulesTest.everyDefaultRuleCanBeReached` builds, for each rule, the item made of exactly its own conditions and fails when an earlier rule takes it.

[Creature drops](#creature-drops) (`skycraft-loot.txt`) are at the end.

## Items

The household, Dwemer, Hearthfire, animal, plant, fish and gem rules below (163, for 191 real item names) each took an item that used to stay in Skyrim. They sit where no earlier rule can take their items and they can't take an earlier rule's items: tagged rules join their sections, and the household rules come last, after a block of `keep` rules for gear, clothing, jewelry, potions, raw food and ingredients.

### Light, tools and household goods

| Item | Becomes | Why |
|---|---|---|
| Torch, Torch Bright | 4 torch | One Skyrim torch burns as long as a handful of Minecraft ones; four is what coal and sticks craft |
| Lantern | lantern | The same object |
| Candlestick | candle | The candle is the useful part |
| Glazed Candles | 2 candle | Two candles in the model |
| Silver Candlestick | 4 iron_nugget | Minecraft has no silver, so silver melts to its nearest metal, iron |
| Charcoal | charcoal | The same thing |
| Bucket | bucket | The same object; a Minecraft bucket can't be smelted back to iron, so there is no iron farm in it |
| Pickaxe, Notched Pickaxe, Ancient Nordic Pickaxe | iron_pickaxe | Skyrim's mining pick is iron (from anywhere: picked up, looted or bought) |
| Woodcutter's Axe | iron_axe | Skyrim's chopping axe is iron; bought ones used to become a war axe |
| Fishing Rod, Alik'ri Fishing Rod, Argonian Fishing Rod, Dwarven Fishing Rod | fishing_rod | The same tool (Fishing Creation) |
| Shovel | iron_shovel | The same tool |
| Pitchfork | iron_hoe | Minecraft's farming tool |
| Tongs | shears | Minecraft's two armed iron hand tool |
| Broom | brush | Minecraft's sweeping tool |
| Hammer, Saw | 3 iron_nugget | Minecraft has neither tool, so the iron is salvaged |
| Oar | 3 stick | It's wood |
| Linen Wrap | 2 string | Cloth |
| Bloody Rags | string | Cloth |
| Roll of Paper | 3 paper | The same thing |
| Ruined Book | 2 paper | What's left of it |
| Burned Book | charcoal | What's left of it |
| Quill | feather | A quill is a feather |
| Inkwell | ink_sac | Minecraft's ink |
| Bowl, Wooden Bowl | bowl | The same object |
| Tankard, Bloody Tankard, Flagon, Goblet, Cup, Jug | glass_bottle | Minecraft's only drinking vessel |
| Empty Wine Bottle, Empty Skooma Bottle | glass_bottle | The same object |
| Empty Jar, and Bee, Butterfly, Green Butterfly, Purple Butterfly, Dragonfly, Moth, Torchbug and Bliss Bug in a Jar | glass_bottle | The jar carries over; Minecraft has no bottled insects |
| Honey Jar | honey_bottle | The same thing |
| Silver Goblet | 3 iron_nugget | Silver melts to iron |
| Silver Jug, Silver Platter | 4 iron_nugget | Silver melts to iron |
| Cast Iron Pot | 6 iron_nugget | Iron cookware melts to nuggets, more for the heavy pot |
| Kettle, Clothes Iron | 3 iron_nugget | Iron melts to nuggets |
| Pot | 2 iron_nugget | Iron melts to nuggets |
| Fork, Knife (the table knife), Draw Knife, Spoon | iron_nugget | Iron melts to nuggets |
| Burial Urn, Glazed Urn | decorated_pot | Minecraft's urn |
| Lute, Drum, Flute | note_block | Minecraft's instrument |
| Unmelting Snow | snow_block | The same thing, and it doesn't melt either |
| Pyroil Tar | 2 fire_charge | It's for setting things alight |
| Centurion Dynamo Core | redstone_block | A Dwemer power source is a block of power |
| Goat Horns, Horker Tusk | 2 bone | Horn and tusk are bone |
| Mammoth Tusk | 4 bone | A big tusk |
| Chaurus Chitin | turtle_scute | Shell plate |

### Dwemer clutter

| Item | Becomes | Why |
|---|---|---|
| Dwemer Bowl, Dwemer Cup, Dwemer Dish, Dwemer Pan | dwarven_metal_ingot | Dwemer metal melts into the SkyCraft Dwemer ingot |
| Dwemer Pot, Dwemer Urn | 2 dwarven_metal_ingot | Bigger pieces |
| Dwemer Gyro, Dwemer Actuator | 2 dwarven_metal_ingot | Bigger parts |
| Dwemer Lever, Small Dwemer Lever, Dwemer Cog, Dwemer Gear | dwarven_metal_ingot | Small parts |
| Large Decorative Dwemer Strut | 3 dwarven_metal_ingot | The existing strut rule; it now matches because the cut name "Large Decorative Dwemer Str" comes back whole |

### Hearthfire building materials

| Item | Becomes | Why |
|---|---|---|
| Clay | 4 clay_ball | A block of clay's worth |
| Quarried Stone | 4 cobblestone | Building stone |
| Sawn Log | 4 oak_planks | One log's planks |
| Nails | iron_nugget | One iron ingot makes 10 nails in Skyrim; 9 nuggets make an ingot |
| Iron Fittings | 4 iron_nugget | One iron ingot makes 2 |
| Hinge | 2 iron_nugget | Iron and a strip of leather |
| Lock | 3 iron_nugget | Iron and corundum |
| Glass | 2 glass | The same thing |
| Straw | wheat | Straw is wheat stalks (a hay block would be 9) |

### Animal parts and alchemy reagents

| Item | Becomes | Why |
|---|---|---|
| Skeever Tail, Falmer Ear | rotten_flesh | Flesh |
| Giant's Toe | 2 rotten_flesh | A big one |
| Troll Fat | slime_ball | Greasy and sticky |
| Ash Hopper Jelly, Netch Jelly, Scrib Jelly | slime_ball | Jelly |
| Large Antlers, Minotaur Horn, Bear Claws | 2 bone | Antler, horn and claw are bone |
| Small Antlers, Hagraven Claw, Bone Hawk Claw, Sabre Cat Tooth, Boar Tusk | bone | Bone |
| Ice Wraith Teeth, Daedroth Teeth, Ogre's Teeth | bone | Teeth are bone |
| Bone Hawk Skull | 2 bone | Bone |
| Powdered Mammoth Tusk | 3 bone_meal | Ground bone |
| Slaughterfish Scales | 2 bone_meal | Fish meal is a fertilizer |
| Eye of Sabre Cat | spider_eye | Minecraft's brewing eye |
| Mudcrab Chitin, Shellbug Chitin | turtle_scute | Shell plate |
| Kagouti Hide | leather | A hide |
| Daedra Silk | 3 string | Fine thread |
| Wisp Wrappings, Kresh Fiber | 2 string | Thread |
| Ectoplasm | phantom_membrane | Ghost stuff from an undead flyer |
| Glow Dust | 2 glowstone_dust | Glowing dust |
| Torchbug Thorax | glowstone_dust | The glowing part |
| Vampire Dust | gunpowder | Grey ash |
| Fire Salts | blaze_powder | Fire in powder form |
| Frost Salts | packed_ice | Cold made solid |
| Void Salts | ender_pearl | The Void; rare in both games |
| Honeycomb, Beehive Husk | honeycomb | The same thing |

### Plants and fungi

| Item | Becomes | Why |
|---|---|---|
| Bleeding Crown, Blisterwort, Creep Cluster, Ash Creep Cluster | red_mushroom | Red fungi |
| Mora Tapinella, Namira's Rot, Scaly Pholiota, Swamp Fungal Pod | brown_mushroom | Brown fungi |
| Glowing Mushroom, Luminous Russula | glow_lichen | Glowing growth |
| Hanging Moss, Giant Lichen | vine | Hanging greenery |
| Deathbell, Nightshade | wither_rose | Deadly flowers |
| Grass Pod, Ashen Grass Pod | 2 wheat_seeds | Seed pods |
| Comberry | 2 sweet_berries | Berries |
| Juniper Berries | 2 sweet_berries | Berries (they carry a raw food tag, so the old ingredient rule missed them) |
| Wheat | wheat | The same thing (raw food tag, like juniper) |
| Moon Sugar | 2 sugar | Sugar |
| Salt Pile | sugar | Minecraft's white cooking powder; it has no salt |
| Spriggan Sap | resin_clump | Tree resin |
| Burnt Spriggan Wood | charcoal | Burnt wood |

### Fish

| Item | Becomes | Why |
|---|---|---|
| Abecean Longfin, Histcarp, Silverside Perch, Pearlfish, Cod, Carp, Catfish, Glass Catfish, Brook Bass, Angler, Vampire Fish | cod | Plain eating fish |
| Salmon, Arctic Char, Arctic Grayling | salmon | Salmon and its kin |
| Cyrodilic Spadetail, River Betty, Goldfish, Angelfish, Lyretail Anthias, Pygmy Sunfish, Spadefish, Glassfish, Pogfish, Tripod Spiderfish | tropical_fish | Small bright fish |
| Scorpion Fish, Direfish | pufferfish | Fish you shouldn't eat raw |

### Gems and ores

| Item | Becomes | Why |
|---|---|---|
| Ruby | 4 redstone | Red gem, red dust |
| Flawless Ruby | 8 redstone | Flawless gems give twice as much |
| Garnet | 2 redstone | A lesser red gem |
| Flawless Garnet | 4 redstone | Twice as much |
| Sapphire | 4 lapis_lazuli | Blue gem, blue gem |
| Flawless Sapphire | 8 lapis_lazuli | Twice as much |
| Soul Gem Fragment | amethyst_shard | A purple crystal shard (never a portal key) |
| Amber | 2 resin_clump | Amber is resin |
| Refined Amber | 2 resin_brick | Worked resin |
| Stalhrim | blue_ice | Enchanted ice that never melts |

### Rules that changed what an item already became

| Item | Was | Now | Why |
|---|---|---|---|
| Chicken's Egg, Hawk's Egg, Pine Thrush Egg, Rock Warbler Egg | bread | egg | They carry a food tag, so the bread rule took them |
| Snowberries | bread | 2 sweet_berries | Same: a food tag |
| Cooked Carp, Cooked Arctic Char, Cooked Juvenile Mudcrab and the other cooked catches of the Fishing Creation | bread | cooked_cod | Cooked fish |
| Cooked Beef | bread | cooked_beef | The same thing |
| Cooked Boar Meat | bread | cooked_porkchop | Boar is pig |
| Salmon Meat | beef | salmon | It has only the raw food tag, so the meat rule took it |
| Flawless Diamond, Flawless Emerald | 1 | 2 | Flawless gems give twice as much |
| Flawless Amethyst | 2 amethyst_shard | 4 amethyst_shard | Same |
| Woodcutter's Axe, bought | iron war axe | iron_axe | It's a tool |
| Pickaxes and Woodcutter's Axe, looted | iron_ingot | the tool | The tool is worth more than its ingot |
| Orcish, Dwarven, Elven, Glass, Ebony, Daedric, Imperial, Ancient Nord, Falmer, Silver and Dragon weapons and armor, looted | stayed in Skyrim | their smithing material | See [Looted gear](#looted-gear) |
| Enchanted weapons and armor ("... of the Inferno"), looted | their smithing material | the gear with its enchantment | See [Enchanted weapons and armor](#enchanted-weapons-and-armor) |
| Unique gear of a SkyCraft material (Chillrend, Windshear, the Gauldur Blackblade, Zephyr, ...), looted | their smithing material | the named gear | See [Named artifacts](#named-artifacts-and-unique-gear) |
| Wine, ale, mead | stayed in Skyrim | potions named after them | See [Drinks](#drinks) |
| Bear, Cave Bear and Snow Bear Pelt; Sabre Cat pelts, Cow and Horse Hide | leather | 3 leather; 2 leather | Big pelts give more |
| Soups and stews (Vegetable Soup, Clam Chowder, ...) | a raw vegetable, bread or mushroom stew | beetroot_soup, mushroom_stew | The dish, not one of its ingredients |
| Silver Sword, Silver Greatsword, bought | stayed in Skyrim | iron_sword with Smite II | Silver's bane of the undead |
| Netch Leather armor, looted or bought | leather | the leather piece | It's leather armor |
| Falmer Helmet, looted or bought | 2 turtle_scute | turtle_helmet | Chitin is turtle scute here, and this is a helmet of it |
| Notched Pickaxe | iron_pickaxe | iron_pickaxe with Unbreaking III, named | It fortifies Smithing |

### Stays in Skyrim on purpose

Gold, keys, books, spell tomes other than Flames and Healing, scrolls no rule names, lockpicks, milk (Jug of Milk), enchanted and unique jewelry, the Moth Priest's blindfold, staffs, Azura's Star and the Black Star, Salmon Roe, Nirnroot and Crimson Nirnroot, soul gems other than the filled Greater, Grand and Black ones, Sheogorath-Shaped Amber, the Exquisite Sapphire, Ysgramor's Soup Spoon, Balbus' Fork, Michaela's Flagon, Wylandriah's Spoon, Mammoth Tusk Powder, and the Thieves Guild's Jeweled Goblet, Flagon, Pitcher and Candlestick. Quest items never convert in any case.

## Clothing, jewelry and the other open questions

These were left open before (commented-out rules, drinks kept in Skyrim). The decisions:

### Clothing: dyed leather armor by slot

Skyrim's clothes are worn on the same body slots as armor, and Minecraft's only cloth-like armor is leather, which takes any dye. So a robe, an outfit or a hood you loot or buy becomes the leather piece for its slot, dyed the color of the clothing: robes, clothes, tunics and dresses a chestplate, hoods, hats and caps a helmet, gloves, bracers and cuffs leggings (Minecraft has no glove slot, the same choice the gear makes for gauntlets), boots and shoes boots. The 160-odd pieces the reviewed proposals named have a rule each, with their colors (black for Thalmor and Dark Brotherhood robes, red for the Mythic Dawn, the school's color for mage robes, a gold-yellow for Restoration, and so on); any other clothing goes by the slot the SKSE plugin reports for it (a new `kind` condition: `tag clothing kind boots ...`) as plain leather.

- The proposals added extra items to clothing (lapis for mage robes, gold nuggets for fine clothes, a second helmet for hooded robes). They're gone: one piece of clothing is one piece of armor, as the gear drops have it (the body slot wins for a hooded robe).
- Mage robes keep their magic: Novice, Apprentice, Adept and Expert Robes regenerate magicka, the school robes ease a school's spells, and both are Mending on the chestplate, the enchantment the armor table gives magicka and the magic schools. Archmage's Robes, Miraak's Robes and the Robe of the Lich keep their names too; the Emperor's Robes and Nocturnal's Hat their names.
- Only looted or bought clothing converts (`from looted from bought`). Clothing you enchant at an enchanting table comes back into your inventory as if picked up, under its plain name, and would otherwise turn into leather and lose the enchantment; for the same reason any clothing with "of" in its name that no rule names stays in Skyrim (`tag clothing name of = keep`).
- Stays in Skyrim: the Moth Priest's blindfold (nothing in Minecraft fits it), enchanted clothing no rule names, and clothing an older SKSE plugin sends without its slot. A Wedding Wreath is its flowers (a cornflower and two oxeye daisies).

<!-- gen:clothing -->
| Clothing (each dyed its own color) | Becomes |
|---|---|
| Novice Robes of Alteration, Novice Robes of Conjuration, Novice Robes of Destruction, Novice Robes of Illusion, Novice Robes of Restoration, Apprentice Robes of Alteration, Apprentice Robes of Conjuration, Apprentice Robes of Destruction, Apprentice Robes of Illusion, Apprentice Robes of Restoration, Adept Robes of Alteration, Adept Robes of Conjuration, Adept Robes of Destruction, Adept Robes of Illusion, Adept Robes of Restoration, Expert Robes of Alteration, Expert Robes of Conjuration, Expert Robes of Destruction, Expert Robes of Illusion, Expert Robes of Restoration, Master Robes of Alteration, Master Robes of Conjuration, Master Robes of Destruction, Master Robes of Illusion, Master Robes of Restoration, Novice Robes, Apprentice Robes, Adept Robes, Expert Robes | leather_chestplate (Mending) |
| Novice Hood, Apprentice Hood, Adept Hood, Black Mage Hood, Mage Hood, Greybeard's Hood, Psiijic Hood, Shrouded Hood, Executioner's Hood, Cicero's Hat, Jester's Hat, Fine Hat, Mourner's Hat, Chef's Hat, Ragged Cap, Redguard Hood, Alik'r Hood, Vampire Hood, Serana Hood, Temple Priest Hood, Lucky Fishing Hat, Fishing Hat, Hat, Hood, Cowl | leather_helmet |
| Black Mage Robes, Blue Mage Robes, Mage Robes, College Robes, Hooded Thalmor Robes, Thalmor Robes, Hooded Necromancer Robes, Necromancer Robes, Hooded Black Robes, Black Robes, Blue Robes, Hooded Monk Robes, Monk Robes, Greybeard's Robes, Vaermina Robes, Mythic Dawn Robes, Psiijic Robes, Shrouded Robes, Executioner's Robes, Cicero's Clothes, Jester's Clothes, Ulfric's Clothes, Jarl's Clothes, Fine Clothes, Noble Clothes, Fine Raiment, Party Clothes, Embellished Robes, Embroidered Garment, Mourner's Clothes, Arcane Blacksmith's Apron, Blacksmith's Apron, Chef's Tunic, Miner's Clothes, Tavern Clothes, Farm Clothes, Belted Tunic, Fur-Trimmed Cloak, Roughspun Tunic, Prisoner's Rags, Ragged Robes, Child's Clothes, Moth Priest Robes, Telvanni Robes, Temple Priest Robes, Cultist Robes, Dunmer Outfit, General Carius' Armor, Fishing Clothes, Captain Clothes, Cyrus' Clothes, Extravagant Robes, Common Robes, Kenro's Robes, Courier's Clothes, Taron Dreth's Robes, Boy's Blue Tunic, Girl's Blue Dress, Boy's Green Tunic, Girl's Green Dress, Boy's Grey Tunic, Girl's Grey Dress, Boy's Red Tunic, Girl's Red Dress, Boy's Yellow Tunic, Girl's Yellow Dress, Grey Robes, Cyrus' Outfit, Clothes | leather_chestplate |
| Archmage's Robes | leather_chestplate (Mending) "Archmage's Robes" |
| Thalmor Boots, Greybeard's Boots, Mythic Dawn Boots, Psiijic Boots, Shrouded Shoes, Cicero's Boots, Jester's Boots, Ulfric's Boots, Fine Boots, Party Boots, Wedding Sandals, Ragged Boots, Cuffed Boots, Redguard Boots, Temple Priest Boots, Cultist Boots, Fishing Boots, Captain Boots, Cyrus' Boots, Courier's Boots, Footwraps, Boots, Shoes | leather_boots |
| Thalmor Gloves, Mythic Dawn Gloves, Psiijic Gloves, Shrouded Hand Wraps, Cicero's Gloves, Jester's Gloves, Ulfric's Bracers, Prisoner's Cuffs, Ragged Trousers, Vampire Gloves, Cultist Gloves, Gogh's Loin Cloth, Gloves | leather_leggings |
| Emperor's Robes | leather_chestplate "Emperor's Robes" |
| Wedding Wreath | cornflower + 2 oxeye_daisy |
| Miraak's Robes | leather_chestplate (Mending) "Miraak's Robes" |
| Robe of the Lich | leather_chestplate (Mending) "Robe of the Lich" |
| Nocturnal's Hat | leather_helmet "Nocturnal's Hat" |
| Moth Priest Blindfold | stays in Skyrim |
| Vampire Leather Hood | leather_helmet |
| Any other clothing, by the slot Skyrim reports | the leather piece for it, undyed |
<!-- /gen:clothing -->

### Jewelry: plain pieces melt down, enchanted and unique ones stay

A plain ring or necklace is a little worked metal and maybe a gem, which is what it becomes: gold into gold nuggets (3 for a ring, 4 for a necklace), silver into iron nuggets (Minecraft has no silver; SkyCraft melts silver to iron everywhere), plus its gem at about half a loose gem (a diamond or emerald whole, 2 redstone for a ruby, 1 for a garnet, 2 lapis for a sapphire, an amethyst shard). Circlets are worn on the head, so they're clothing: a leather helmet dyed their metal's color (copper, gold, silver, jade) and their gem. Enchanted jewelry stays in Skyrim, because its enchantment is its worth and Minecraft has no ring slot to put it in; so does unique jewelry (the Amulets of the Divines, the Daedric rings, Savos Aren's Amulet, the Gauldur Amulet, ...) and Nordic jewelry, whose metal isn't certain. As with clothing, only looted or bought jewelry converts, so a ring you forge or enchant stays, and anything with "of" in its name stays.

<!-- gen:jewelry -->
| Item | Becomes |
|---|---|
| Gold Diamond Ring | 3 gold_nugget + diamond |
| Gold Emerald Ring | 3 gold_nugget + emerald |
| Gold Sapphire Ring | 3 gold_nugget + 2 lapis_lazuli |
| Silver Amethyst Ring | 3 iron_nugget + amethyst_shard |
| Silver Garnet Ring | 3 iron_nugget + redstone |
| Silver Ruby Ring | 3 iron_nugget + 2 redstone |
| Gold Diamond Necklace | 4 gold_nugget + diamond |
| Gold Ruby Necklace | 4 gold_nugget + 2 redstone |
| Gold Jeweled Necklace | 4 gold_nugget + emerald + redstone |
| Silver Emerald Necklace | 4 iron_nugget + emerald |
| Silver Sapphire Necklace | 4 iron_nugget + 2 lapis_lazuli |
| Silver Jeweled Necklace | 4 iron_nugget + 2 redstone + amethyst_shard |
| Gold Ring | 3 gold_nugget |
| Silver Ring | 3 iron_nugget |
| Gold Necklace | 4 gold_nugget |
| Silver Necklace | 4 iron_nugget |
| Copper and Moonstone Circlet | leather_helmet (dyed #b87333) + quartz |
| Copper and Onyx Circlet | leather_helmet (dyed #b87333) + flint |
| Copper and Ruby Circlet | leather_helmet (dyed #b87333) + 2 redstone |
| Copper and Sapphire Circlet | leather_helmet (dyed #b87333) + 2 lapis_lazuli |
| Gold and Emerald Circlet | leather_helmet (dyed #d4af37) + emerald |
| Gold and Ruby Circlet | leather_helmet (dyed #d4af37) + 2 redstone |
| Jade and Emerald Circlet | leather_helmet (dyed #00a86b) + emerald |
| Jade and Sapphire Circlet | leather_helmet (dyed #00a86b) + 2 lapis_lazuli |
| Silver and Moonstone Circlet | leather_helmet (dyed #c0c0c8) + quartz |
| Silver and Sapphire Circlet | leather_helmet (dyed #c0c0c8) + 2 lapis_lazuli |
<!-- /gen:jewelry -->

### Drinks

Wine, ale, mead, brandy and the Dunmer spirits become potions named after them, in the drink's color: a little Saturation (Skyrim's drinks restore stamina, and stamina is the hunger bar here) and a few seconds of Nausea, the drink going to your head. A few keep a side effect of their own (Colovian Brandy's Hero of the Village, Argonian Bloodwine's Water Breathing, Firebrand Wine's Resistance). Milk stays: Minecraft's milk comes in a bucket, which would be three iron for a jug. Skooma keeps its own rules.

<!-- gen:drinks -->
| Item | Becomes |
|---|---|
| Black-Briar Reserve, Sadri's Sujamma, Sujamma, Ashfire Mead | potion: Saturation, Nausea I 12 s |
| Cyrodilic Brandy, Flin, Balmora Blue, Alto Wine, Spiced Wine, Dragon's Breath Mead | potion: Saturation, Nausea I 10 s |
| Colovian Brandy | potion: Saturation, Hero of the Village I 30 s, Nausea I 8 s |
| Argonian Bloodwine | potion: Water Breathing I 50 s, Regeneration I 20 s, Saturation, Nausea I 8 s |
| Shein, Matze, Surilie Brothers Wine, Wine, Argonian Ale, Ale, Black-Briar Mead, Honningbrew Mead, Nord Mead | potion: Saturation, Nausea I 8 s |
| Sleeping Tree Sap | potion: Health Boost II 45 s, Slowness I 45 s, Nausea I 20 s |
| Velvet LeChance, White-Gold Tower, Cliff Racer | potion: Saturation, Nausea I 4 s |
| Firebrand Wine | potion: Saturation, Resistance I 45 s, Nausea I 8 s |
| Emberbrand Wine | potion: Saturation, Nausea I 20 s |
| Any other mead | potion: Saturation, Nausea I 8 s |
<!-- /gen:drinks -->

### Netherite: none

No Skyrim material becomes netherite. SkyCraft's own tiers already reach it: ebony is about netherite and daedric just above it (fireproof, netherite mining level), and dragon gear is the daedric tier everywhere else in the mod (gear drops, enchanted gear). Netherite stays Minecraft's own reward for going into the Nether, and salvage never gives netherite scrap. So the proposals that made Dragonplate and Dragonscale netherite or diamond became: bought dragon armor is the SkyCraft daedric piece, looted dragon armor salvages as before (2 ebony ingots and 4 bone), and the Konahrik mask is a daedric helmet.

| Item | Becomes |
|---|---|
| Dragonplate or Dragonscale Armor, Dragonplate Insulated Cuirass, Dragonbone Mail, bought | daedric_cuirass |
| Dragonplate or Dragonscale Helmet (and Insulated), bought | daedric_helmet |
| Dragonplate or Dragonscale Gauntlets (and Insulated), bought | daedric_greaves |
| Dragonplate or Dragonscale Boots (and Insulated), bought | daedric_boots |
| Any dragon armor, looted | 2 ebony_ingot + 4 bone (as before) |

### Horse armor

The Elven and Steel Horse Armor (Creation Club) go into your inventory when a saddle change takes them off your horse. Minecraft has horse armor of its own, so they become it: Steel Horse Armor iron_horse_armor, Elven Horse Armor golden_horse_armor (the elven set is gilded). The trade-off: putting armor back on a Skyrim horse costs 500 gold again instead of 50.

## More conversions: the reviewed proposals

5,237 proposed conversions were written for real Skyrim items and checked against the mod's parser (every id a real Minecraft 26.3 or SkyCraft item, every rule catching its own item). The ones that passed are in the defaults, those with names confirmed in the source data first and then the rest, each in the section its items belong to: a more specific rule before a general one, and no rule that takes an item an earlier rule already gives the same to. Where a proposal would have changed an item that already converted, it was one marked as better. The tables group items that become the same thing.

Gear of no smithing material converts only when looted or bought (`from looted from bought`), as the other gear rules do: gear you forge, temper or enchant yourself arrives as picked up (`world`) and stays in Skyrim, so Skyrim smithing and enchanting still work.

### Books and scrolls

<!-- gen:books -->
| Item | Becomes | Why |
|---|---|---|
| Treasure Map I, Treasure Map II, Treasure Map III, Treasure Map IV, Treasure Map V, Treasure Map VI, Treasure Map VII, Treasure Map VIII, Treasure Map IX, Treasure Map X, Treasure Map XI | buried_treasure_map | A Skyrim treasure map is a sketch of where a chest is buried: Minecraft's buried treasure map is the same thing |
| Spell Tome: Wall of Flames, Spell Tome: Healing Hands, Spell Tome: Fast Healing, Spell Tome: Grand Healing | stays in Skyrim | A spell worth learning: kept, so the Flames and Healing tome rules can't take it |
| Spell Tome: Flames, Spell Tome: Healing | book | Every character starts knowing Flames, so this tome can't teach anything and only sells for a few coins: a plain book |
| Scroll of Firebolt | splash_potion: Instant Damage I | A one-shot fire bolt becomes a thrown harming potion, the way Skyrim poisons become splash potions |
| Scroll of Fireball | splash_potion: Instant Damage II | An exploding fireball: a thrown area harming potion, one level up from Firebolt |
| Scroll of Fire Storm | lingering_potion: Instant Damage II | Master fire storm around the caster: a lingering harming cloud |
| Scroll of Ice Spike | splash_potion: Instant Damage I, Slowness II 8 s | Frost damages and slows, like the Frostbite Venom rule |
| Scroll of Ice Storm | lingering_potion: Instant Damage I, Slowness II 10 s | A rolling storm of frost: a lingering harm-and-slow cloud |
| Scroll of Blizzard | lingering_potion: Instant Damage II, Slowness III 15 s | Master frost storm: a stronger lingering harm-and-slow cloud |
| Scroll of Lightning Bolt | splash_potion: Instant Damage I, Weakness I 15 s | Shock damages and drains magicka |
| Scroll of Chain Lightning | splash_potion: Instant Damage II, Weakness I 20 s | Lightning that leaps between targets: a thrown area harm with the shock drain |
| Scroll of Fire Rune, Scroll of Frost Rune, Scroll of Lightning Rune | tnt | A rune is an explosive trap laid on the ground: Minecraft's placed explosive is TNT |
| Scroll of Oakflesh | potion: Resistance I 60 s | Armor skin: this file maps armor buffs to Resistance |
| Scroll of Stoneflesh | potion: Resistance I 120 s | Stronger armor skin than Oakflesh |
| Scroll of Ironflesh | potion: Resistance II 60 s | Expert armor skin: Resistance II |
| Scroll of Ebonyflesh | potion: Resistance II 120 s | Master armor skin: longer Resistance II |
| Scroll of Dragonhide | potion: Resistance IV 30 s | Dragonhide absorbs 80% of physical damage for 30 s |
| Scroll of Candlelight, Scroll of Detect Life | potion: Night Vision I 60 s | A light that follows you for a minute: Night Vision, as this file does for Night Eye |
| Scroll of Magelight | 4 torch | A ball of light that sticks where it lands: torches you place |
| Scroll of Waterbreathing | potion: Water Breathing I 60 s, Conduit Power I 60 s | Same effects as this file's Waterbreathing potions, for the spell's 60 seconds |
| Scroll of Paralyze | splash_potion: Slowness V 10 s, Mining Fatigue III 10 s | Same effects as the Paralysis poison rule, for the spell's 10 seconds |
| Scroll of Mass Paralysis | lingering_potion: Slowness V 15 s, Mining Fatigue III 15 s | Area paralysis: the Paralyze effects as a lingering cloud |
| Scroll of Courage | potion: Health Boost I 60 s, Strength I 60 s | Courage fortifies health and stamina and stops fleeing |
| Scroll of Rally | potion: Health Boost II 60 s, Strength I 60 s | A stronger Courage |
| Scroll of Call to Arms | splash_potion: Health Boost II 120 s, Strength II 120 s | Master Courage for everyone nearby: a splash potion so it reaches your allies |
| Scroll of Calm | splash_potion: Weakness II 30 s | A calmed foe stops fighting: Weakness takes its bite away |
| Scroll of Pacify | splash_potion: Weakness III 60 s | A stronger Calm |
| Scroll of Harmony | lingering_potion: Weakness III 60 s | Master Calm over an area: a lingering cloud |
| Scroll of Fear | splash_potion: Darkness I 30 s, Weakness I 30 s | Same effects as the Fear poison rule |
| Scroll of Rout | splash_potion: Darkness I 60 s, Weakness II 60 s | A stronger Fear |
| Scroll of Hysteria | lingering_potion: Darkness I 60 s, Weakness II 60 s | Master Fear cloak around the caster: a lingering cloud |
| Scroll of Fury | splash_potion: Nausea I 30 s | Same effect as the Frenzy poison rule |
| Scroll of Frenzy | splash_potion: Nausea I 60 s | A stronger Fury, as the Frenzy poison rule |
| Scroll of Mayhem | lingering_potion: Nausea I 60 s | Master Frenzy over an area: a lingering cloud |
| Scroll of Muffle | potion: Invisibility I 60 s | Same effect as this file's Muffle potions |
| Scroll of Invisibility | potion: Invisibility I 30 s | The spell's 30 seconds of invisibility |
| Scroll of Fast Healing | potion: Instant Health II | A big instant self-heal |
| Scroll of Close Wounds | potion: Instant Health III | Expert self-heal, stronger than Fast Healing |
| Scroll of Heal Other, Scroll of Turn Undead, Scroll of Repel Undead | splash_potion: Instant Health II | Healing for someone else: thrown |
| Scroll of Grand Healing, Scroll of Turn Greater Undead | splash_potion: Instant Health III | Heals you and everyone near you: a strong splash heal |
| Scroll of Turn Lesser Undead, Scroll of Repel Lesser Undead | splash_potion: Instant Health I | Instant Health hurts Minecraft's undead, so turning the undead is a thrown healing potion |
| Scroll of Circle of Protection | lingering_potion: Instant Health I | A circle the undead can't cross: a lingering healing cloud that burns them (name is cut to 'Protect' at 27 bytes) |
| Scroll of Guardian Circle | lingering_potion: Instant Health II, Regeneration II 20 s | Master circle that repels the undead and heals whoever stands in it |
| Scroll of Bane of the Undead | lingering_potion: Instant Health II | Master area spell that burns and turns the undead: a lingering cloud of Instant Health |
<!-- /gen:books -->

### Food

<!-- gen:food -->
| Item | Becomes | Why |
|---|---|---|
| Apple Cabbage Stew, Hot Apple Cabbage Stew, Cabbage Soup, Hot Cabbage Soup, Tomato Soup, Hot Tomato Soup, Vegetable Soup, Hot Vegetable Soup, Roasted Tomato Crab Bisque, Hot Roasted Tomato Crab Bisque | beetroot_soup | A cabbage-and-apple vegetable stew |
| Hot Potato Soup, Clam Chowder, Hot Clam Chowder, Potato Crab Chowder, Hot Potato Crab Chowder, Creamy Crab Bisque, Hot Creamy Crab Bisque, Elsweyr Fondue, Hot Elsweyr Fondue | mushroom_stew | A creamy soup, so the soup default (mushroom stew) |
| Potage le Magnifique | rabbit_stew | The Gourmet's famous soup, Skyrim's finest dish |
| Horse Haunch, Venison Chop, Horker Loaf, Cooked Nix-Hound Meat | cooked_beef | raw Horse Meat is beef in the defaults, so the haunch is cooked beef |
| Rabbit Haunch | cooked_rabbit | Roast rabbit is Minecraft's cooked rabbit |
| Leg of Goat Roast | cooked_mutton | Raw Leg of Goat is mutton in the defaults, so its roast is cooked mutton, not the roast rule's cooked beef |
| Pheasant Roast, Chicken Dumpling | cooked_chicken | Roast game bird |
| Mammoth Steak | 2 cooked_beef | A mammoth-sized steak cooked from a Mammoth Snout, which already gives 2 beef raw |
| Grilled Leeks | 2 beetroot | Leek is beetroot in the defaults |
| Cooked Arctic Char, Cooked Arctic Grayling | cooked_salmon | Raw Arctic Char is salmon in the defaults, so cooked char is cooked salmon, not cooked cod |
| Steamed Mudcrab Legs, Crab Cakes | cooked_cod | Raw Mudcrab Legs are cod in the defaults, so steamed legs are cooked cod |
| Raw Rabbit Leg | rabbit | it is raw rabbit |
| Boar Meat | porkchop | Boar is pig: Cooked Boar Meat is already cooked porkchop in the defaults, so the raw meat is a porkchop rather than the meat rule's beef |
| Clam Meat, Crab Meat | cod | seafood is cod in the defaults, like Mudcrab Legs |
| Potato Bread | bread | The potato rule comes first and turns this loaf into a raw potato |
| Jazbay Crostata, Juniper Berry Crostata, Snowberry Crostata, Apple Dumpling | pumpkin_pie | A crostata is a fruit tart: Minecraft's pie, like Apple Pie in the defaults |
| Long Taffy Treat, Boiled Creme Treat | 2 cookie | A small candy |
| Eidar Cheese Wheel, Goat Cheese Wheel, Mammoth Cheese Bowl | cake | A whole round wheel you set down and cut slices from: Minecraft's cake (7 slices, 14 hunger, near the wheel's 15 Health) |
| Gourd | pumpkin | Skyrim's squash is Minecraft's pumpkin (bake it into pumpkin pie) |
| Sack of Flour | 3 wheat | Milled wheat for Hearthfire baking |
| Poisoned Red Apple, Poisoned Green Apple | poisonous_potato | An assassin's apple laced with chokeberry |
<!-- /gen:food -->

### Ingredients

<!-- gen:ingredients -->
| Item | Becomes | Why |
|---|---|---|
| Bee, Dreugh Wax | honeycomb | a bee caught by a hive |
| Butterfly Wing, Luna Moth Wing, Blue Dartwing, Orange Dartwing, Ancestor Moth Wing | phantom_membrane | a monarch butterfly's wing: the thin wing membrane |
| Chicken's Egg, Hawk's Egg | egg | a bird's egg from a hawk nest: an egg |
| Pine Thrush Egg | blue_egg | a small pale blue songbird egg: the blue (cold) egg |
| Rock Warbler Egg | brown_egg | a small speckled brown songbird egg: the brown (warm) egg |
| Spider Egg, Watcher's Eye | spider_eye | a frostbite spider part |
| Chaurus Eggs, Slaughterfish Egg, Elytra Ichor, Sload Soap | slime_ball | a gooey clutch of insect eggs from a chaurus egg sac: a slimeball |
| Canis Root, Trama Root | hanging_roots | a tangle of thin pale roots: hanging roots |
| Taproot | carrot | a thick spriggan taproot |
| Garlic | beetroot | a bulb vegetable |
| Elves Ear | orange_tulip | the orange, ear-shaped blossom of the Reach: an orange flower |
| Frost Mirriam | azure_bluet | a pale, frosty little herb from the tundra: the pale azure bluet |
| Thistle Branch, Coda Flower | allium | a thistle's round purple flower head: the allium looks just like it |
| Nordic Barnacle | sea_pickle | a small shelled sea creature clinging to rocks: the sea pickle |
| Briar Heart | creaking_heart | the thorny heart that animates a Briarheart: Minecraft's creaking heart animates a wooden creature |
| Charred Skeever Hide | rabbit_hide | a scrap of rat-sized hide: rabbit hide (a quarter of leather) |
| Hawk Beak | bone |  |
| Human Flesh, Human Heart, Hunger Tongue, Imp Gall | rotten_flesh | a necromancer's heart: flesh |
| Dwarven Oil | 2 redstone |  |
| Small Pearl | prismarine_crystals | a lustrous gem from the sea: prismarine crystals |
| Pearl | 2 prismarine_crystals | a larger sea pearl: two prismarine crystals |
| Emperor Parasol Moss | moss_carpet | moss growing on Telvanni parasol mushrooms: moss |
| Scathecraw | dead_bush | a dry, spiky Ashlands weed: the dead bush |
| Spawn Ash | gunpowder | grey ash left by an ash spawn |
| Chaurus Hunter Antennae | string | long thin insect feelers: string |
| Gleamblossom | open_eyeblossom | a pale flower that glows in the Forgotten Vale's dark: the open eyeblossom glows at night |
| Poison Bloom | crimson_fungus | a red, poison-spitting fungal bloom from Darkfall: the red crimson fungus |
| Soul Husk | soul_sand | the dried husk of a Soul Cairn plant, full of trapped souls: soul sand |
| Alocasia Fruit | glow_berries | a Shivering Isles fruit whose effects include Light: glow berries |
| Ambrosia | honey_bottle | food of the gods that heals and cures poison: honey heals hunger and cures poison in Minecraft |
| Aster Bloom Core | oxeye_daisy | the heart of an aster, a daisy-like flower: the oxeye daisy |
| Bittergreen Petals | lily_of_the_valley | pale petals of a bitter, poisonous Morrowind plant: the white, poisonous lily of the valley |
| Bog Beacon | shroomlight | a tall swamp mushroom that glows like a beacon: shroomlight, the glowing fungus |
| Bungler's Bane, Hypha Facia, Rot Scale | shelf_mushroom | a bracket fungus that grows on Bitter Coast trees: the shelf mushroom |
| Chokeberry | 2 sweet_berries |  |
| Corkbulb Root | potato | a bulbous, cork-skinned root: the potato |
| Daedra Venin | fermented_spider_eye | Daedric venom: the fermented spider eye, Minecraft's brewing base for harmful potions |
| Fire Petal | red_tulip | red petals of the fire fern: a red flower |
| Flame Stalk | torchflower | a flame-shaped flower on a stalk: the torchflower |
| Fungus Stalk | brown_mushroom | a mushroom stalk: a mushroom |
| Gnarl Bark | pale_oak_wood | bark of a Gnarl, a living tree creature: wood of the pale oak, where Minecraft's living tree creature (the creaking) lives |
| Gold Kanet | sunflower | a tall, golden-yellow flower: the sunflower |
| Hackle-Lo Leaf | fern | a green leaf from the Grazelands: a fern |
| Heart of Order | 2 amethyst_shard | the crystalline heart of a Knight of Order: amethyst shards |
| Hydnum Azure Giant Spore, Steel-Blue Entoloma | warped_fungus | the spore of an azure giant mushroom: the blue warped fungus, which grows into a giant fungus |
| Lichor | experience_bottle | a magical liquor whose effects are all Magicka: a bottle of enchanting |
| Marshmerrow | sugar_cane | a tall, sweet-pulped marsh reed: sugar cane |
| Ogre's Teeth | 2 bone | big ogre teeth (weight 3) are two bones, like Minotaur Horn and Bear Claws |
| Red Kelp Gas Bladder | kelp | the float of a red kelp: kelp |
| Roobrush | bush | a scrubby brush plant: the bush |
| Saltrice | wheat | a grain crop: wheat |
| Scalon Fin | armadillo_scute | a scaled fin of the reptilian Scalon: a scute |
| Scrib Jerky | cooked_beef | dried, cured meat: jerky is cooked meat |
| Stoneflower Petals | pink_petals | loose purple-pink flower petals: pink petals |
| Void Essence | ender_pearl |  |
| Wisp Stalk Caps | 2 brown_mushroom | a few mushroom caps: mushrooms |
| Withering Moon | closed_eyeblossom | a pale, moon-shaped Shivering Isles flower: the pale, night-bound eyeblossom |
| Bloodgrass | crimson_roots | red grass of the Deadlands: crimson roots, the red grass of the Nether |
| Harrada | weeping_vines | a lashing red Deadlands vine: the red Nether weeping vines |
| Spiddal Stick | 2 stick | a dry, thorny stem: sticks |
| Bliss Bug Thorax | glowstone_dust |  |
| Screaming Maw | pitcher_plant | a carnivorous plant with a gaping maw: the pitcher plant |
| Juvenile Mudcrab | turtle_scute |  |
| Angler Larvae | cod |  |
<!-- /gen:ingredients -->

### Hides

<!-- gen:hides -->
| Item | Becomes | Why |
|---|---|---|
| Bear Pelt, Cave Bear Pelt, Snow Bear Pelt | 3 leather | A whole bear hide is the biggest pelt in Skyrim (worth 100 gold) |
| Sabre Cat Pelt, Snowy Sabre Cat Pelt, Vale Sabre Cat Hide, Cow Hide, Horse Hide | 2 leather | A big cat hide, between a wolf and a bear |
<!-- /gen:hides -->

### Ores, ingots and smithing materials

<!-- gen:metals -->
| Item | Becomes | Why |
|---|---|---|
| Silver Ore, Quicksilver Ore | raw_iron | Minecraft has no silver |
| Silver Ingot, Quicksilver Ingot | iron_ingot | A silver ingot is a whole ingot of the nearest Minecraft metal, iron |
| Madness Ore | gilded_blackstone | Shivering Isles ore, a dark brown stone marbled with glowing gold veins: gilded blackstone looks just like it (two ores smelt to one Madness Ingot) |
| Madness Ingot | ebony_ingot |  |
| Heart Stone | redstone_block + 2 magma_cream | Glowing red chunk of the Heart of Lorkhan from Red Mountain: a block of redstone for its raw power (rubies are redstone |
| Dragon Bone | 2 bone_block | A huge dragon bone (15 lb, 500 gold, smithing material for Dragonbone gear) is a pile of bone: two bone blocks (6 bones' worth) |
| Dragon Scales | 2 turtle_scute | Scales are scutes |
<!-- /gen:metals -->

### Arrows and conjured weapons

<!-- gen:arrows -->
| Item | Becomes | Why |
|---|---|---|
| Bound Bow, Bound Arrow | stays in Skyrim | Conjured by the Bound Bow spell and gone when it ends |
| Daedric Arrow, Dragonbone Arrow, Amber Arrow, Madness Arrow, Bone Arrow | tipped_arrow of harming | Skyrim's second-strongest arrow (24 damage): the hardest-hitting Minecraft arrow |
| Stalhrim Arrow | tipped_arrow: Slowness I 8 s | Deathly cold Stalhrim tip: the hit chills and slows |
| Fire Arrow | tipped_arrow: Instant Damage I | Bursts into flame on impact: an instant-damage arrow, named Fire Arrow |
| Ice Arrow | tipped_arrow: Instant Damage I, Slowness II 8 s | Frost burst on impact: damage and a strong slow |
| Lightning Arrow | tipped_arrow: Instant Damage I, Weakness I 10 s | Shock burst on impact: damage, and shock drains (Weakness, as the poison rules map magicka loss) |
| Telekinesis Arrow | tipped_arrow: Levitation I 3 s | Arrows that hang in the air by telekinesis: the hit lifts the target |
| Soul Stealer Arrow | tipped_arrow: Wither I 4 s, Glowing I 10 s | Rips out the target's soul: withering, and the soul shows through walls |
| Sunhallowed Elven Arrow | spectral_arrow | Auriel's sunlit arrow: Minecraft's glowing arrow |
| Bloodcursed Elven Arrow | tipped_arrow: Darkness I 8 s, Wither I 3 s | The vampire's cursed arrow that blots out the sun: darkness and decay |
| Riekling Spear | stone_spear | The Rieklings' crude throwing spear (Skyrim ammo): Minecraft's stone spear |
| Exploding Steel Bolt of Fire | tipped_arrow: Instant Damage I "Exploding Steel Bolt of Fire" | Crossbow bolt that bursts on impact: Minecraft crossbows shoot tipped arrows |
| Exploding Steel Bolt of Ice | tipped_arrow: Instant Damage I, Slowness II 6 s "Exploding Steel Bolt of Ice" | Crossbow bolt that bursts on impact: Minecraft crossbows shoot tipped arrows |
| Exploding Steel Bolt of Shock | tipped_arrow: Instant Damage I, Weakness I 10 s "Exploding Steel Bolt of Shock" | Crossbow bolt that bursts on impact: Minecraft crossbows shoot tipped arrows |
| Exploding Dwarven Bolt of Fire | tipped_arrow: Instant Damage I "Exploding Dwarven Bolt of Fire" | Crossbow bolt that bursts on impact: Minecraft crossbows shoot tipped arrows |
| Exploding Dwarven Bolt of Ice | tipped_arrow: Instant Damage I, Slowness II 8 s "Exploding Dwarven Bolt of Ice" | Crossbow bolt that bursts on impact: Minecraft crossbows shoot tipped arrows |
| Exploding Dwarven Bolt of Shock | tipped_arrow: Instant Damage I, Weakness I 12 s "Exploding Dwarven Bolt of Shock" | Crossbow bolt that bursts on impact: Minecraft crossbows shoot tipped arrows |
<!-- /gen:arrows -->

### Armor sets without a smithing material

Looted or bought (never when made or picked up). Stalhrim, Nordic Carved, Amber, Madness, Dark Seducer and Golden Saint armor have smithing materials, so they're in the next table, bought and looted like their weapons.

<!-- gen:sets -->
| Item | Becomes | Why |
|---|---|---|
| Whiterun Guard's Armor, Solitude Guard's Armor, Markarth Guard's Armor, Riften Guard's Armor, Falkreath Guard's Armor, Pale Guard's Armor, Hjaalmarch Guard's Armor, Winterhold Guard's Armor, Stormcloak Cuirass, Scaled Armor, Penitus Oculatus Armor, Dawnguard Armor | chainmail_chestplate | Whiterun guards' light mail-and-leather cuirass under the hold's tabard: chainmail |
| Whiterun Guard's Helmet, Solitude Guard's Helmet, Markarth Guard's Helmet, Riften Guard's Helmet, Falkreath Guard's Helmet, Pale Guard's Helmet, Hjaalmarch Guard's Helmet, Winterhold Guard's Helmet, Stormcloak Helmet, Scaled Helmet, Penitus Oculatus Helmet, Dawnguard Helmet | chainmail_helmet | Whiterun guards' face-covering light helmet: chainmail helmet like the cuirass |
| Whiterun Guard's Shield, Solitude Guard's Shield, Markarth Guard's Shield, Riften Guard's Shield, Falkreath Guard's Shield, Pale Guard's Shield, Hjaalmarch Guard's Shield, Winterhold Guard's Shield, Windhelm Guard's Shield, Imperial Dragon Shield, Storm-Bear Shield, Stalhrim Shield, Blades Shield, Dawnguard Shield, Bonemold Shield, Chitin Shield, Nordic Shield, Amber Shield, Dark Seducer Shield, Golden Saint Shield, Madness Shield, Netch Leather Shield, Shadowed Netch Leather Shield, Shadowed Netch Leather Tower Shield, Ordinator Shield | shield | Whiterun guards' heraldic shield |
| Stormcloak Officer Armor, Silver Armor | iron_chestplate | Stormcloak captain's bear-pelt armor, a step above the soldier's: Minecraft iron (bracers fill the leggings slot) |
| Stormcloak Officer Helmet, Silver Helmet | iron_helmet | Stormcloak captain's bear-pelt armor, a step above the soldier's: Minecraft iron (bracers fill the leggings slot) |
| Stormcloak Officer Boots, Silver Boots | iron_boots | Stormcloak captain's bear-pelt armor, a step above the soldier's: Minecraft iron (bracers fill the leggings slot) |
| Stormcloak Officer Bracers, Silver Gauntlets | iron_leggings | Stormcloak captain's bear-pelt armor, a step above the soldier's: Minecraft iron (bracers fill the leggings slot) |
| Imperial Dragon Armor, Storm-Bear Armor, Wolf Armor, Blades Armor, Dawnguard Heavy Armor, Bonemold Armor, Vigil Enforcer Armor | SkyCraft steel_cuirass | Imperial champion's heavy plate, Imperial steel at its best: the SkyCraft steel piece |
| Imperial Dragon Helmet, Storm-Bear Helmet, Wolf Helmet, Blades Helmet, Dawnguard Full Helmet, Bonemold Helmet, Vigil Enforcer Helmet | SkyCraft steel_helmet | Imperial champion's heavy plate, Imperial steel at its best: the SkyCraft steel piece |
| Imperial Dragon Boots, Storm-Bear Boots, Wolf Boots, Blades Boots, Dawnguard Heavy Boots, Bonemold Boots, Vigil Enforcer Boots | SkyCraft steel_boots | Imperial champion's heavy plate, Imperial steel at its best: the SkyCraft steel piece |
| Imperial Dragon Gauntlets, Storm-Bear Gauntlets, Wolf Gauntlets, Blades Gauntlets, Dawnguard Heavy Gauntlets, Bonemold Gauntlets, Vigil Enforcer Gauntlets | SkyCraft steel_greaves | Imperial champion's heavy plate, Imperial steel at its best: the SkyCraft steel piece |
| Fur Armor, Forsworn Armor, Armor of the Old Gods, Thieves Guild Master's Armor, Guild Master's Armor, Thieves Guild Armor, Linwe's Armor, Shrouded Armor, Vampire Armor, Morag Tong Armor, Blackguard's Armor, Netch Leather Armor, Shadowed Netch Leather Armor | leather_chestplate | Fur light armor worn by bandits, guards and Stormcloaks: Minecraft leather piece |
| Fur Boots, Forsworn Boots, Boots of the Old Gods, Thieves Guild Master's Boots, Guild Master's Boots, Thieves Guild Boots, Linwe's Boots, Shrouded Boots, Vampire Boots, Morag Tong Boots, Blackguard's Boots, Netch Leather Boots, Shadowed Netch Leather Boots | leather_boots | Fur light armor worn by bandits, guards and Stormcloaks: Minecraft leather piece |
| Fur Gauntlets, Forsworn Gauntlets, Gauntlets of the Old Gods, Thieves Guild Master's Gloves, Guild Master's Gloves, Thieves Guild Gloves, Linwe's Gloves, Shrouded Gloves, Vampire Gauntlets, Morag Tong Bracers, Blackguard's Gloves, Netch Leather Bracers, Painted Netch Leather Bracers, Shadowed Netch Leather Bracers | leather_leggings | Fur light armor worn by bandits, guards and Stormcloaks: Minecraft leather piece |
| Scaled Boots, Penitus Oculatus Boots, Dawnguard Boots | chainmail_boots | Scale-mail light armor (ArmorMaterialScaled, not a SkyCraft material): chainmail piece |
| Scaled Bracers, Penitus Oculatus Bracers, Dawnguard Gauntlets | chainmail_leggings | Scale-mail light armor (ArmorMaterialScaled, not a SkyCraft material): chainmail piece |
| Forsworn Headdress, Helmet of the Old Gods, Guild Master's Hood, Thieves Guild Hood, Linwe's Hood, Shrouded Cowl, Morag Tong Hood, Blackguard's Hood, Netch Leather Helmet, Painted Netch Leather Helmet, Shadowed Netch Leather Helmet | leather_helmet | Forsworn hide-and-bone light armor (the headdress misses the piece rules): leather piece |
| Falmer Helmet, Chitin Helmet | turtle_helmet | Chaurus-chitin Falmer helmet (chitin is turtle scute here): turtle helmet instead of 2 scutes |
| Falmer Heavy Armor | SkyCraft ebony_cuirass | Strongest Falmer chitin plate (beats Falmer Hardened): SkyCraft ebony piece |
| Falmer Heavy Helm | SkyCraft ebony_helmet | Strongest Falmer chitin plate (beats Falmer Hardened): SkyCraft ebony piece |
| Falmer Heavy Boots | SkyCraft ebony_boots | Strongest Falmer chitin plate (beats Falmer Hardened): SkyCraft ebony piece |
| Falmer Heavy Gauntlets | SkyCraft ebony_greaves | Strongest Falmer chitin plate (beats Falmer Hardened): SkyCraft ebony piece |
| Falmer Hardened Armor | SkyCraft orcish_cuirass | Hardened chaurus-chitin heavy armor (Advanced Armors tier, like steel plate): SkyCraft orcish piece |
| Falmer Hardened Helm | SkyCraft orcish_helmet | Hardened chaurus-chitin heavy armor (Advanced Armors tier, like steel plate): SkyCraft orcish piece |
| Falmer Hardened Boots | SkyCraft orcish_boots | Hardened chaurus-chitin heavy armor (Advanced Armors tier, like steel plate): SkyCraft orcish piece |
| Falmer Hardened Gauntlets | SkyCraft orcish_greaves | Hardened chaurus-chitin heavy armor (Advanced Armors tier, like steel plate): SkyCraft orcish piece |
| Ancient Falmer Cuirass | SkyCraft glass_cuirass | Snow Elf light armor, statistically identical to glass: SkyCraft glass piece |
| Ancient Falmer Boots | SkyCraft glass_boots | Snow Elf light armor, statistically identical to glass: SkyCraft glass piece |
| Ancient Falmer Gauntlets | SkyCraft glass_greaves | Snow Elf light armor, statistically identical to glass: SkyCraft glass piece |
| Chitin Armor, Indoril Armor, Her Hand Armor | SkyCraft elven_cuirass | Chitin armor needs Elven Smithing: SkyCraft elven cuirass |
| Chitin Boots, Indoril Boots, Her Hand Boots | SkyCraft elven_boots | Chitin boots (Elven Smithing): SkyCraft elven boots |
| Chitin Bracers, Chitin Heavy Gauntlets, Indoril Gauntlets, Her Hand Gauntlets | SkyCraft elven_greaves | Light chitin bracers (Elven Smithing): SkyCraft elven greaves |
| Indoril Helmet, Her Hand Helmet | SkyCraft elven_helmet | Ordinator chitin-and-gold heavy armor: SkyCraft elven piece, like other chitin armor |
<!-- /gen:sets -->

### Bought and looted gear of other materials

Bought, it's the gear of the nearest tier; looted, it salvages into what it's made of.

<!-- gen:bought -->
| Item | Becomes | Why |
|---|---|---|
| Ebony Scimitar, Stalhrim Sword | SkyCraft ebony_sword | Bought (also catches the line's dagger |
| Wooden Sword | wooden_sword | A child's practice sword (2 damage, improved with firewood): Minecraft's wooden sword |
| Nordic War Axe, Nordic Mace, Nordic Battleaxe, Nordic Warhammer, Forsworn Axe, Falmer War Axe, Skyforge Steel War Axe, Skyforge Steel Battleaxe, Dawnguard War Axe, Dawnguard Warhammer | SkyCraft orcish_war_axe | Bought: Nordic carved steel (no SkyCraft material) is a cut above steel: orcish, like the gear drops |
| Nordic Sword, Falmer Sword, Skyforge Steel Sword, Skyforge Steel Dagger, Skyforge Steel Greatsword, Blades Sword, Scimitar | SkyCraft orcish_sword | Bought (also catches the line's dagger |
| Nordic Dagger, Forsworn Sword | steel_ingot | Looted Nordic gear salvages like other looted gear (one material per weapon) |
| Stalhrim War Axe, Stalhrim Mace, Stalhrim Battleaxe, Stalhrim Warhammer | SkyCraft ebony_war_axe | Bought: Stalhrim (no SkyCraft material) matches ebony, like the gear drops |
| Stalhrim Dagger | blue_ice | Looted Stalhrim gear salvages like other looted gear (one material per weapon) |
| Amber War Axe, Amber Mace, Amber Battleaxe, Amber Warhammer, Madness War Axe, Madness Mace, Madness Battleaxe, Madness Warhammer, Dragonbone War Axe, Dragonbone Mace, Dragonbone Battleaxe, Dragonbone Warhammer | SkyCraft daedric_war_axe | Bought: Amber weapons (Amber Sword 15 damage) are top tier: daedric |
| Amber Sword, Madness Sword, Dragonbone Sword, Dragonbone Dagger, Dragonbone Greatsword | SkyCraft daedric_sword | Bought (also catches the line's dagger |
| Amber Dagger | 2 resin_brick | Looted Amber gear salvages like other looted gear (one material per weapon) |
| Madness Dagger | crying_obsidian | Looted Madness gear salvages like other looted gear (one material per weapon) |
| Dark War Axe, Dark Mace, Dark Battleaxe, Dark Warhammer | SkyCraft glass_war_axe | Bought: Dark Seducer weapons are glass quality (Dark Sword 12 damage): glass |
| Dark Sword | SkyCraft glass_sword | Bought (also catches the line's dagger |
| Dark Dagger | obsidian | Looted Dark gear salvages like other looted gear (one material per weapon) |
| Golden Battleaxe, Golden Warhammer | SkyCraft elven_war_axe | Bought: Golden Saint weapons match elven ones (Golden Sword 11 damage): elven |
| Golden Sword | SkyCraft elven_sword | Bought (also catches the line's dagger |
| Golden Greatsword | gold_ingot | Looted Golden gear salvages like other looted gear (one material per weapon) |
| Honed Ancient Nord Greatsword, Ancient Nord Sword, Ancient Nord Greatsword, Nord Hero Sword, Nord Hero Greatsword, Imperial Sword | SkyCraft steel_sword | Bought Ancient Nord (draugr) gear becomes steel gear, as draugr gear drops do |
| Honed Draugr War Axe, Ancient Nord War Axe, Ancient Nord Battle Axe, Nord Hero War Axe, Nord Hero Battle Axe | SkyCraft steel_war_axe | Bought Ancient Nord (draugr) gear becomes steel gear, as draugr gear drops do |
| Silver Sword, Silver Greatsword | SkyCraft iron_sword (Smite II) | Bought silver becomes iron gear like silver gear drops |
| Imperial Light Armor, Studded Imperial Armor | chainmail_chestplate | Imperial scout's light mail-and-leather piece bought in a shop: the chainmail piece (looted ones keep salvaging to steel) |
| Imperial Light Helmet | chainmail_helmet | Imperial scout's light mail-and-leather piece bought in a shop: the chainmail piece (looted ones keep salvaging to steel) |
| Imperial Light Boots | chainmail_boots | Imperial scout's light mail-and-leather piece bought in a shop: the chainmail piece (looted ones keep salvaging to steel) |
| Imperial Light Bracers | chainmail_leggings | Imperial scout's light mail-and-leather piece bought in a shop: the chainmail piece (looted ones keep salvaging to steel) |
| Imperial Armor, Ancient Nord Armor | SkyCraft steel_cuirass | Heavy Imperial legion steel bought in a shop: the SkyCraft steel piece, the metal looted Imperial gear already salvages to |
| Imperial Helmet, Ancient Nord Helmet | SkyCraft steel_helmet | Heavy Imperial legion steel bought in a shop: the SkyCraft steel piece, the metal looted Imperial gear already salvages to |
| Imperial Boots, Ancient Nord Boots | SkyCraft steel_boots | Heavy Imperial legion steel bought in a shop: the SkyCraft steel piece, the metal looted Imperial gear already salvages to |
| Imperial Bracers, Ancient Nord Gauntlets | SkyCraft steel_greaves | Heavy Imperial legion steel bought in a shop: the SkyCraft steel piece, the metal looted Imperial gear already salvages to |
| Daedric Mail Boots | SkyCraft daedric_boots | Daedric Mail light set boots: SkyCraft daedric boots |
| Daedric Mail Gauntlets | SkyCraft daedric_greaves | Daedric Mail light set gauntlets: SkyCraft daedric greaves |
| Daedric Mail | SkyCraft daedric_cuirass | Daedric Mail light set cuirass, named 'Mail' so no piece rule takes it (boots/gauntlets rules must come first): SkyCraft daedric cuirass |
| Dwarven Mail Boots | SkyCraft dwarven_boots | Dwarven Mail light set boots: SkyCraft dwarven boots |
| Dwarven Mail Gauntlets | SkyCraft dwarven_greaves | Dwarven Mail light set gauntlets: SkyCraft dwarven greaves |
| Dwarven Mail Helmet | SkyCraft dwarven_helmet | Dwarven Mail light set helmet: SkyCraft dwarven helmet |
| Dwarven Mail | SkyCraft dwarven_cuirass | Dwarven Mail light set cuirass, named 'Mail' so no piece rule takes it (boots/gauntlets rules must come first): SkyCraft dwarven cuirass |
<!-- /gen:bought -->

### Household goods

<!-- gen:household -->
| Item | Becomes | Why |
|---|---|---|
| Statue of Dibella | 6 gold_nugget | Small golden figurine of Dibella: melts into gold nuggets |
| Human Skull | skeleton_skull | Necromancer-altar skull: Minecraft's skeleton skull is the same thing |
| Troll Skull | 4 bone | A big beast skull, so bone rather than a placeable head |
| Basket | bundle | A wicker carry-basket: Minecraft's carry-sack is the bundle (1 string + 1 leather), a modest price for very common clutter |
| Bellows | leather + 2 oak_planks | A forge bellows is two wooden boards and a leather bag |
| Embalming Tool | 2 iron_nugget | The four Nordic-ruin embalming tools (scalpel, pick, screw tool, scissors) are small iron tools: two nuggets, like the knife and fork rules |
| Wooden Plate, Wooden Ladle | bowl | Minecraft's only wooden dish is the bowl, as for the Wooden Bowl |
| Silver Bowl, Silver Plate | 3 iron_nugget | like the Silver Goblet (3) and Silver Jug (4) it should melt into nuggets |
<!-- /gen:household -->

### Proposals not taken as written

How many proposals, and why: most were replaced by the decisions above (clothing, jewelry, drinks, dragon gear) or by the named and enchanted gear sections below; the staffs and small soul gems stay in Skyrim.

<!-- gen:left -->
- 167: clothing: replaced by the clothing rules (dyed leather armor by slot, without the extra items)
- 97: jewelry: replaced by the jewelry rules (plain rings, necklaces and circlets convert; enchanted and unique jewelry stays)
- 58: unique armor: replaced by the named armor rules (its Skyrim name and enchantments)
- 44: staffs and other spell-casting artifacts stay in Skyrim: nothing in Minecraft casts their spells
- 27: drinks: taken over by the Drinks rules, without the names in the rules (a potion takes the Skyrim name anyway)
- 24: rewritten: bought is the gear of the nearest tier, looted salvages, like the same family's weapons
- 13: dragon gear: the daedric tier when bought and salvage when looted, like the other gear; nothing in Skyrim is netherite
- 8: soul gems stay as they are: small gems for Skyrim's enchanting, the big filled ones portal keys, the stars Daedric artifacts
- 3: Helmets of Waterbreathing: Respiration and Aqua Affinity on the helmet, like the others, rather than a turtle shell
- 2: already today's rule
- 2: Skooma is already SkyCraft's Skooma (its own section)
- 2: horse armor: taken over by the household rules
<!-- /gen:left -->

## Named artifacts and unique gear

The Daedric artifacts and Skyrim's unique weapons and armor become unique Minecraft gear, from anywhere (a quest's reward arrives as picked up): the gear of the nearest tier, with their Skyrim name and the enchantments the tables below give their own. Dawnbreaker's fire and its undead-blasting flare are Fire Aspect and Smite on a glass sword; the Mace of Molag Bal and Volendrung are Minecraft maces; the Ebony Mail's poison cloak is Thorns; the Gray Cowl is a grey leather hood. A Minecraft shield takes only Unbreaking and Mending, so the artifact shields are Unbreaking shields. Nothing is netherite: Konahrik, the best of the Dragon Priest masks, is a daedric helmet.

Staffs and the other pure magic items stay in Skyrim, since nothing in Minecraft casts their spells: the Wabbajack, the Sanguine Rose, the Skull of Corruption, the Staff of Magnus, Miraak's Staff and every other staff, Azura's Star and the Black Star (soul gems), the Oghma Infinium (a book), and the Daedric rings and amulets (unique jewelry).

<!-- gen:named -->
| Item | Becomes | Why |
|---|---|---|
| Dawnbreaker | SkyCraft glass_sword (Fire Aspect II, Smite III) "Dawnbreaker" | Meridia's sun blade (12 damage, the glass sword's): burns, and its undead-blasting flare is Smite |
| Chillrend | SkyCraft glass_sword (Knockback II) "Chillrend" | Mercer Frey's glass sword: frost and paralysis both map to Knockback |
| Ebony Blade | SkyCraft ebony_sword (Sharpness III, Mending) "Ebony Blade" | Mephala's ebony katana drinks its victims' health: Absorb Health is Sharpness, topped with Mending |
| Mehrunes' Razor | SkyCraft daedric_sword (Sharpness III) "Mehrunes' Razor" | Mehrunes Dagon's daedric dagger (11 damage, a daedric dagger's) |
| Blade of Woe | SkyCraft ebony_sword (Sharpness II) "Blade of Woe" | The Dark Brotherhood's black dagger (12 damage, above any ebony dagger) |
| Keening | SkyCraft elven_sword (Sharpness II) "Keening" | Kagrenac's Dwemer-Falmer dagger (8 damage, an elven dagger's) |
| Mace of Molag Bal | mace (Breach III) "Mace of Molag Bal" | Molag Bal's spiked mace is Minecraft's mace |
| Volendrung | mace (Density III, Mending) "Volendrung" | Malacath's great hammer: the heavy smash weapon is Minecraft's mace |
| Wuuthrad | SkyCraft ebony_war_axe (Sharpness III) "Wuuthrad" | Ysgramor's dark battleaxe (25 damage) |
| Rueful Axe | SkyCraft ebony_war_axe (Unbreaking II) "Rueful Axe" | Clavicus Vile's two-handed axe (22 damage, improved with ebony) |
| Dragonbane | SkyCraft glass_sword (Sharpness III) "Dragonbane" | The Blades' leveled katana (10 to 14 damage) |
| Bolar's Oathblade | SkyCraft orcish_sword (Knockback II) "Bolar's Oathblade" | A cut above steel (11 damage, improved with quicksilver) |
| Ghostblade | SkyCraft glass_sword (Sharpness III) "Ghostblade" | A weightless, translucent spirit blade that ignores armor, so it hits far above its listed 8 damage |
| Nightingale Blade | SkyCraft ebony_sword (Sharpness II, Mending) "Nightingale Blade" | Nocturnal's agent's black sword (up to 14 damage, improved with ebony): absorbs health and stamina |
| Windshear | SkyCraft orcish_sword (Knockback II) "Windshear" | A scimitar a cut above steel (11 damage) |
| Gauldur Blackblade | SkyCraft steel_sword (Sharpness II, Mending) "Gauldur Blackblade" | Ancient Nord blade of the Gauldur sons (draugr gear is steel) |
| The Pale Blade | SkyCraft steel_sword (Knockback II) "The Pale Blade" | Ancient Nord sword of the Forsworn Pact (draugr gear is steel) |
| Red Eagle's Fury | SkyCraft steel_sword "Red Eagle's Fury" | Red Eagle's unenchanted Ancient Nord style sword (8 damage, a steel sword's) |
| Red Eagle's Bane | SkyCraft steel_sword (Fire Aspect I, Smite II) "Red Eagle's Bane" | Red Eagle's sword reforged at the Sunderstone: burns and turns undead |
| Eduj | SkyCraft steel_sword "Eduj" | Ancient Nord sword from Valthume (improved with steel) |
| Okin | SkyCraft steel_war_axe "Okin" | Eduj's twin, an Ancient Nord war axe from Valthume (improved with steel) |
| Grimsever | SkyCraft glass_sword (Knockback I) "Grimsever" | Mjoll's sword (12 damage, improved with refined malachite) |
| Harkon's Sword | SkyCraft steel_sword (Sharpness II, Mending) "Harkon's Sword" | The vampire lord's sword (8 damage, steel) absorbs health, magicka and stamina |
| Queen Freydis's Sword | SkyCraft steel_sword "Queen Freydis's Sword" | An old queen's plain steel sword (8 damage) |
| Herebane's Courage | SkyCraft elven_sword "Herebane's Courage" | Unique sword improved with refined moonstone (11 damage, an elven sword's) |
| Miraak's Sword | SkyCraft daedric_sword (Mending) "Miraak's Sword" | The First Dragonborn's tentacled blade (16 damage, forged with a Daedra heart) |
| Bloodskal Blade | SkyCraft glass_sword (Sweeping Edge III) "Bloodskal Blade" | The Bloodskal greatsword (21 damage, a glass greatsword's): its power-attack energy wave is Sweeping Edge |
| Stormfang | SkyCraft steel_sword "Stormfang" | Unique steel greatsword (17 damage, a steel greatsword's) |
| Drainheart Sword | SkyCraft orcish_sword (Sharpness I, Mending) "Drainheart Sword" | A draining sword (11 damage, no smithing material) |
| Drainblood Battleaxe | SkyCraft orcish_war_axe (Sharpness I, Mending) "Drainblood Battleaxe" | A draining battleaxe (21 damage, no smithing material) |
| Soulrender | SkyCraft ebony_sword "Soulrender" | Unique daedric-style sword (13 damage, an ebony sword's, improved with ebony) |
| Bloodscythe | SkyCraft ebony_sword "Bloodscythe" | Unique daedric-style sword (13 damage, an ebony sword's, improved with ebony) |
| Bloodthorn | SkyCraft steel_sword (Looting II) "Bloodthorn" | A thorned steel dagger that soul traps: Soul Trap is Looting |
| Borvir's Dagger | SkyCraft elven_sword "Borvir's Dagger" | Unique dagger improved with refined moonstone (8 damage, an elven dagger's) |
| Blade of Sacrifice | SkyCraft ebony_sword "Blade of Sacrifice" | Ritual dagger improved with ebony (10 damage, an ebony dagger's) |
| Kahvozein's Fang | SkyCraft steel_sword "Kahvozein's Fang" | The ancient dragon-priest dagger that harvests heartscales (Ancient Nord work is steel) |
| Nettlebane | SkyCraft orcish_sword (Bane of Arthropods III) "Nettlebane" | A hooked dagger (6 damage, an orcish dagger's) made to cut down Spriggans and Hagravens: a bane weapon |
| Rundi's Dagger | SkyCraft steel_sword "Rundi's Dagger" | A steel dagger left in a tomb (5 damage, a steel dagger's) |
| Valdr's Lucky Dagger | SkyCraft steel_sword (Looting II) "Valdr's Lucky Dagger" | A lucky steel dagger: luck in Minecraft is Looting |
| Shiv | SkyCraft iron_sword "Shiv" | The crude prison shank from Cidhna Mine |
| Ghorbash's Ancestral Axe | SkyCraft iron_war_axe "Ghorbash's Ancestral Axe" | Ghorbash's family war axe (8 damage, improved with iron) |
| Ulfric's War Axe | SkyCraft steel_war_axe "Ulfric's War Axe" | Ulfric Stormcloak's steel war axe, looted at the end of the war (it would salvage to a steel ingot) |
| Headsman's Axe | SkyCraft steel_war_axe "Headsman's Axe" | Solitude's executioner's axe (17 damage, a steel battleaxe's) |
| The Woodsman's Friend | SkyCraft steel_war_axe (Efficiency III) "The Woodsman's Friend" | A woodsman's battleaxe (17 damage): the axe that also fells trees fast |
| Steel Battleaxe of Fiery Souls | SkyCraft steel_war_axe (Sharpness I, Fortune I) "Steel Battleaxe of Fiery Souls" | One-of-a-kind enchanted steel battleaxe |
| Aegisbane | SkyCraft iron_war_axe "Aegisbane" | Unique warhammer improved with iron (18 damage, an iron warhammer's) |
| Trollsbane | SkyCraft steel_war_axe "Trollsbane" | Unique steel warhammer (20 damage, a steel warhammer's) |
| The Longhammer | SkyCraft orcish_war_axe "The Longhammer" | Light, fast warhammer improved with orichalcum (21 damage, an orcish warhammer's) |
| Prelate's Mace | SkyCraft elven_war_axe "Prelate's Mace" | Unique mace improved with refined moonstone (13 damage, an elven mace's) |
| Horksbane | SkyCraft steel_war_axe "Horksbane" | Unique steel mace from Solstheim (10 damage, a steel mace's) |
| Champion's Cudgel | SkyCraft glass_war_axe "Champion's Cudgel" | Unique warhammer (24 damage, a glass warhammer's) |
| Umbra | SkyCraft daedric_sword (Looting III, Sharpness II, Mending) "Umbra" | Clavicus Vile's soul-stealing blade: Soul Trap is Looting, absorbing health and stamina Sharpness and Mending |
| Goldbrand | SkyCraft ebony_sword (Fire Aspect II) "Goldbrand" | Boethiah's fire katana, a step above Dawnbreaker |
| Chrysamere | SkyCraft ebony_sword (Mending) "Chrysamere" | The Paladin's Blade, a legendary claymore |
| Sunder | mace (Fire Aspect I, Breach II) "Sunder" | Kagrenac's hammer is Minecraft's mace: elemental hits and stamina/magicka damage are Fire Aspect and Breach |
| Nerveshatter | SkyCraft daedric_war_axe (Sharpness III) "Nerveshatter" | Madness warhammer (29 damage) with heavy shock damage: shock is Sharpness |
| Sword of Jyggalag | SkyCraft glass_sword "Sword of Jyggalag" | The Prince of Order's crystalline sword (unenchanted) |
| Trueflame | SkyCraft ebony_sword (Fire Aspect II) "Trueflame" | Nerevar's flaming blade |
| Hopesfire | SkyCraft ebony_sword "Hopesfire" | Nerevar's other legendary blade, a quest reward from the Tribunal Temple |
| Light of Day | SkyCraft glass_sword (Smite III) "Light of Day" | The undead-burning blade from the Tribunal armory: Smite |
| Skull Crusher | SkyCraft ebony_war_axe "Skull Crusher" | Great warhammer from the Tribunal armory |
| Cleaver of St. Felms | SkyCraft glass_war_axe "Cleaver of St. Felms" | Saint Felms the Bold's axe from the Tribunal armory |
| Auriel's Bow | bow (Power III, Flame) "Auriel's Bow" | Auriel's Elder Scroll bow: its sun damage burns (Flame) and hits hardest at the undead (Power) |
| Zephyr | bow "Zephyr" | The fast-drawing Dwemer bow |
| Nightingale Bow | bow (Power II, Punch I) "Nightingale Bow" | Nocturnal's bow of frost and shock: shock is Power, frost Punch |
| Gauldur Blackbow | bow (Power I, Mending) "Gauldur Blackbow" | Gauldur's son's leveled bow: shock is Power, absorbing magicka Mending |
| Drainspell Bow | bow (Mending) "Drainspell Bow" | A bow that drains magicka: absorbing is Mending |
| Bow of the Hunt | bow (Power II) "Bow of the Hunt" | A hunter's bow with Huntsman's Prowess: Power |
| Firiniel's End | bow "Firiniel's End" | The elven bow that killed Firiniel, keeping its name |
| Froki's Bow | bow "Froki's Bow" | Old Froki's long bow, keeping its name |
| Angi's Bow | bow "Angi's Bow" | Angi's reward bow for finishing her archery lessons, keeping its name |
| Glass Bow of the Stag Prince | bow "Glass Bow of the Stag Prince" | Unique glass bow, keeping its name (given in full: Skyrim cuts it at 27 bytes) |
| Dwarven Black Bow of Fate | bow "Dwarven Black Bow of Fate" | Unique Dwemer bow, keeping its name |
| Bow of Shadows | bow "Bow of Shadows" | Unique bow that hides its wielder (no Minecraft bow enchantment does), keeping its name |
| Ruin's Edge | bow "Ruin's Edge" | Unique Dark Seducer bow, keeping its name |
| Dawnfang | SkyCraft ebony_sword (Fire Aspect II) "Dawnfang" | The day form of the shifting sword, burning by day |
| Duskfang | SkyCraft ebony_sword (Knockback II) "Duskfang" | The night form of the shifting sword, freezing by night: frost is Knockback |
| Notched Pickaxe | iron_pickaxe (Unbreaking III) "Notched Pickaxe" | Skyrim's nod to Notch, found atop the Throat of the World: Minecraft's diamond pickaxe |
| Ancient Nordic Pickaxe | diamond_pickaxe "Ancient Nordic Pickaxe" | The only pick that mines Stalhrim, the way only diamond mines obsidian |
| Poacher's Axe | iron_axe "Poacher's Axe" | A unique woodcutter's axe (5 damage): the Minecraft axe, like the Woodcutter's Axe |
| Reforged Cuirass of the Crusader | chainmail_chestplate "Reforged Cuirass of the Crusader" |  |
| Reforged Boots of the Crusader | chainmail_boots "Reforged Boots of the Crusader" |  |
| Reforged Gauntlets of the Crusader | chainmail_leggings "Reforged Gauntlets of the Crusader" |  |
| Ebony Mail | SkyCraft ebony_cuirass (Thorns III) "Ebony Mail" | Its poison cloak hurts whoever comes close |
| Savior's Hide | leather_chestplate (Protection II) "Savior's Hide" | Resists magic and poison |
| Spellbreaker | shield (Unbreaking III) "Spellbreaker" | Its blocking ward |
| Masque of Clavicus Vile | golden_helmet (Unbreaking II, Mending) "Masque of Clavicus Vile" | Speech and prices, and magicka regeneration |
| Jagged Crown | golden_helmet (Protection II) "Jagged Crown" | A heavy helmet of the old kings |
| Helm of Yngol | SkyCraft steel_helmet (Protection I) "Helm of Yngol" | Resists frost |
| Ancient Helmet of the Unburned | SkyCraft steel_helmet (Fire Protection III) "Ancient Helmet of the Unburned" |  |
| Shield of Ysgramor | shield (Unbreaking III) "Shield of Ysgramor" | Resists magic, fortifies health |
| Targe of the Blooded | shield (Unbreaking I) "Targe of the Blooded" | Its bash makes targets bleed |
| Shield of Solitude | shield (Unbreaking II) "Shield of Solitude" | Fortifies blocking |
| Predator's Grace | leather_boots (Feather Falling II) "Predator's Grace" | Fortifies sneak and archery |
| Tumblerbane Gloves | leather_leggings (Unbreaking II) "Tumblerbane Gloves" | Fortifies lockpicking |
| Gray Cowl of Nocturnal | leather_helmet (Protection I, dyed #4a4a4f) "Gray Cowl of Nocturnal" | The Gray Fox's hood, grey |
| Wraithguard | SkyCraft dwarven_greaves (Protection III) "Wraithguard" | Kagrenac's gauntlet resists magic |
| Boots of Blinding Speed | leather_boots (Feather Falling I) "Boots of Blinding Speed" |  |
| Visage of Mzund | SkyCraft dwarven_helmet (Mending) "Visage of Mzund" |  |
| Auriel's Shield | shield (Unbreaking III) "Auriel's Shield" | Stores the blows it blocks |
| Wooden Mask | carved_pumpkin "Wooden Mask" | A mask over the face |
| General Tullius' Armor | SkyCraft steel_cuirass "General Tullius' Armor" | The general's Imperial armor |
| Konahrik | SkyCraft daedric_helmet (Protection IV, Thorns II) "Konahrik" | Dragon Priest mask |
| Hevnoraak | SkyCraft daedric_helmet (Protection II) "Hevnoraak" | Dragon Priest mask |
| Krosis | SkyCraft elven_helmet (Projectile Protection II, Unbreaking II) "Krosis" | Dragon Priest mask |
| Morokei | SkyCraft glass_helmet (Mending) "Morokei" | Dragon Priest mask |
| Nahkriin | SkyCraft ebony_helmet (Mending) "Nahkriin" | Dragon Priest mask |
| Otar | SkyCraft dwarven_helmet (Protection III) "Otar" | Dragon Priest mask |
| Rahgot | SkyCraft orcish_helmet (Unbreaking III) "Rahgot" | Dragon Priest mask |
| Vokun | SkyCraft ebony_helmet (Mending) "Vokun" | Dragon Priest mask |
| Volsung | golden_helmet (Respiration III, Aqua Affinity, Unbreaking II) "Volsung" | Dragon Priest mask |
| Ahzidal | SkyCraft daedric_helmet (Protection II) "Ahzidal" | Dragon Priest mask |
| Dukaan | diamond_helmet (Protection II) "Dukaan" | Dragon Priest mask |
| Zahkriisos | SkyCraft glass_helmet (Blast Protection II) "Zahkriisos" | Dragon Priest mask |
| Miraak | SkyCraft glass_helmet (Protection II, Mending) "Miraak" | Dragon Priest mask |
| Ahzidal's Armor of Retribution | SkyCraft steel_cuirass (Thorns II) "Ahzidal's Armor of Retribution" |  |
| Ahzidal's Helm of Vision | SkyCraft steel_helmet (Protection I) "Ahzidal's Helm of Vision" |  |
| Ahzidal's Boots of Waterwalking | SkyCraft steel_boots (Frost Walker II) "Ahzidal's Boots of Waterwalking" |  |
| Ahzidal's Gauntlets of Warding | SkyCraft steel_greaves (Protection I) "Ahzidal's Gauntlets of Warding" |  |
| Deathbrand Armor | SkyCraft ebony_cuirass (Protection II) "Deathbrand Armor" |  |
| Deathbrand Helm | SkyCraft ebony_helmet (Respiration III) "Deathbrand Helm" |  |
| Deathbrand Boots | SkyCraft ebony_boots (Feather Falling II) "Deathbrand Boots" |  |
| Deathbrand Gauntlets | SkyCraft ebony_greaves (Thorns II) "Deathbrand Gauntlets" |  |
| Nightingale Armor | SkyCraft ebony_cuirass (Protection II, Unbreaking II) "Nightingale Armor" |  |
| Nightingale Hood | SkyCraft ebony_helmet (Protection I, Mending) "Nightingale Hood" |  |
| Nightingale Boots | SkyCraft ebony_boots (Feather Falling III) "Nightingale Boots" |  |
| Nightingale Gloves | SkyCraft ebony_greaves (Thorns II, Unbreaking II) "Nightingale Gloves" |  |
| Ancient Shrouded Armor | SkyCraft ebony_cuirass (Protection II) "Ancient Shrouded Armor" |  |
| Ancient Shrouded Cowl | SkyCraft ebony_helmet (Projectile Protection II) "Ancient Shrouded Cowl" |  |
| Ancient Shrouded Boots | SkyCraft ebony_boots (Feather Falling III) "Ancient Shrouded Boots" |  |
| Ancient Shrouded Gloves | SkyCraft ebony_greaves (Thorns II) "Ancient Shrouded Gloves" |  |
| Miraak's Boots | leather_boots (Protection I, dyed #3d3324) "Miraak's Boots" |  |
| Miraak's Gloves | leather_leggings (Mending, dyed #3d3324) "Miraak's Gloves" |  |
| Cuirass of the Crusader | diamond_chestplate (Protection II) "Cuirass of the Crusader" |  |
| Helm of the Crusader | diamond_helmet (Protection I) "Helm of the Crusader" |  |
| Boots of the Crusader | diamond_boots (Protection I) "Boots of the Crusader" |  |
| Gauntlets of the Crusader | diamond_leggings (Thorns II) "Gauntlets of the Crusader" |  |
| Shield of the Crusader | shield (Unbreaking II) "Shield of the Crusader" |  |
<!-- /gen:named -->

## Enchanted weapons and armor

Skyrim's leveled enchanted gear ("Ebony Sword of the Inferno", "Glass Helmet of Eminent Alchemy") keeps its enchantment: it becomes the gear of its material's tier with the nearest Minecraft enchantment, whether looted, bought or picked up, instead of salvaging into ingots. Tiers are the gear drops': Imperial and Ancient Nord are steel, Falmer orcish, silver Skyrim Iron, dragon daedric, hide, leather, studded and fur Minecraft leather, Nordic orcish and Stalhrim ebony. A rule names the gear's material, its type word and the enchantment's word (`tag weapon material ebony name sword name inferno = skycraft:ebony_sword{fire_aspect:2} 1`), so plain gear of the same name still salvages or is bought as before.

The level follows the word's place in Skyrim's ladder for that enchantment (Embers, Burning, Scorching, the Blaze, the Inferno, ...): the lowest third is level I, the middle II, the top III, or Minecraft's own top when that's lower (Fire Aspect and Knockback stop at II). An enchantment only goes on gear that can carry it in Minecraft; every level is within Minecraft's own maximum and no two exclusive enchantments meet.

| Skyrim enchantment | On swords | On war axes | On bows |
|---|---|---|---|
| Fire Damage | Fire Aspect | Sharpness | Flame |
| Frost Damage | Knockback | Sharpness | Punch |
| Shock Damage | Sharpness | Sharpness | Power |
| Absorb Health | Sharpness, Mending at the top | Sharpness, Mending at the top | Power |
| Absorb Magicka, Absorb Stamina | Mending | Mending | Mending |
| Magicka Damage, Stamina Damage | Sweeping Edge | Unbreaking | Punch |
| Soul Trap | Looting | Fortune | Infinity at the top, else Unbreaking |
| Turn Undead, Silver | Smite | Smite | Power |
| Fear, Banish, Paralysis | Knockback | Sharpness | Punch |
| Huntsman's Prowess | Bane of Arthropods | | Power |
| Chaos Damage | Fire Aspect and Sharpness | Sharpness | Flame and Power |

Crossbows take Piercing for the damage enchantments, Quick Charge for Magicka and Stamina Damage and Unbreaking for Soul Trap.

| Skyrim enchantment (armor) | Minecraft |
|---|---|
| Fortify Health, Resist Magic, Frost, Poison and Disease, Fortify Heavy and Light Armor | Protection |
| Resist Fire | Fire Protection |
| Resist Shock | Blast Protection |
| Fortify Archery | Projectile Protection |
| Fortify One-Handed, Two-Handed (gauntlets) | Thorns |
| Fortify Magicka and Magicka Regen, the magic schools | Mending |
| Fortify Sneak, Muffle (boots) | Feather Falling |
| Waterbreathing (helmets) | Respiration III and Aqua Affinity |
| Fortify Carry Weight, Stamina, Smithing, Alchemy, Enchanting, Lockpicking, Pickpocket, Speech, Barter | Unbreaking |
| Fortify Block (shields) | Unbreaking on the shield; a shield with any other enchantment is a plain shield |

Skyrim sends 27 letters of a name, so the enchantment word is often cut (`name infer`), and items whose names share their first 27 letters ("Steel Plate Armor of Eminent Health" and "... of Eminent Illusion") necessarily share one rule. Many names follow Skyrim's pattern rather than a list seen item by item, so some rules name gear Skyrim never spawns; they cost nothing.

<!-- gen:enchanted -->
| Skyrim gear | Becomes | Items (names confirmed) | For example |
|---|---|---|---|
| Crossbows | crossbow | 31 (15) | Dwarven Crossbow of Diminishing: crossbow (Quick Charge II) |
| Bows | bow | 278 (12) | Daedric Bow of Debilitation: bow (Punch II) |
| Iron weapons | SkyCraft iron | 162 (31) | Iron War Axe of Frost: SkyCraft iron_war_axe (Sharpness I) |
| Steel weapons | SkyCraft steel | 168 (5) | Steel Warhammer of Chills: SkyCraft steel_war_axe (Sharpness I) |
| Orcish weapons | SkyCraft orcish | 245 (0) | Orcish Greatsword of Absorption: SkyCraft orcish_sword (Sharpness I) |
| Dwarven weapons | SkyCraft dwarven | 245 (0) | Dwarven Greatsword of Devouring: SkyCraft dwarven_sword (Sharpness II) |
| Elven weapons | SkyCraft elven | 246 (2) | Elven War Axe of Thunderbolts: SkyCraft elven_war_axe (Sharpness II) |
| Glass weapons | SkyCraft glass | 259 (36) | Glass Battleaxe of Consuming: SkyCraft glass_war_axe (Sharpness II) |
| Ebony weapons | SkyCraft ebony | 273 (5) | Ebony Dagger of the Vampire: SkyCraft ebony_sword (Sharpness III, Mending) |
| Daedric weapons | SkyCraft daedric | 270 (3) | Daedric Sword of Harvesting: SkyCraft daedric_sword (Mending) |
| Nordic weapons | SkyCraft orcish | 280 (1) | Nordic Greatsword of Harrowing: SkyCraft orcish_sword (Mending) |
| Stalhrim weapons | SkyCraft ebony | 285 (1) | Stalhrim Greatsword of Subsuming: SkyCraft ebony_sword (Mending) |
| Imperial weapons | SkyCraft steel | 24 (1) | Imperial Sword of Fatigue: SkyCraft steel_sword (Sweeping Edge I) |
| Shields | shield | 201 (63) | Glass Shield of Flame Suppression: shield |
| Banded Iron armor | SkyCraft iron | 5 (2) | Banded Iron Armor of the Minor Knight: SkyCraft iron_cuirass (Protection I) |
| Steel Plate armor | SkyCraft steel | 41 (8) | Steel Plate Boots of Hauling: SkyCraft steel_boots (Unbreaking I) |
| Studded armor | SkyCraft steel | 3 (2) | Studded Imperial Armor of Health: SkyCraft steel_cuirass (Protection I) |
| Hide armor | Minecraft leather | 82 (14) | Hide Bracers of Archery: leather_leggings (Projectile Protection I) |
| Leather armor | Minecraft leather | 81 (18) | Leather Bracers of Alchemy: leather_leggings (Unbreaking I) |
| Studded armor | Minecraft leather | 4 (3) | Studded Armor of the Squire: leather_chestplate (Protection I) |
| Imperial armor | SkyCraft steel | 5 (3) | Imperial Armor of the Knight: SkyCraft steel_cuirass (Protection I) |
| Iron armor | SkyCraft iron | 76 (23) | Iron Gauntlets of Wielding: SkyCraft iron_greaves (Thorns I) |
| Steel armor | SkyCraft steel | 79 (18) | Steel Gauntlets of Wielding: SkyCraft steel_greaves (Thorns I) |
| Elven armor | SkyCraft elven | 84 (32) | Elven Bracers of Smithing: SkyCraft elven_greaves (Unbreaking I) |
| Scaled armor | SkyCraft elven | 78 (7) | Scaled Bracers of Wielding: SkyCraft elven_greaves (Thorns I) |
| Dwarven armor | SkyCraft dwarven | 67 (17) | Dwarven Boots of Shock Suppression: SkyCraft dwarven_boots (Blast Protection II) |
| Orcish armor | SkyCraft orcish | 66 (14) | Orcish Boots of Major Sneaking: SkyCraft orcish_boots (Feather Falling II) |
| Glass armor | SkyCraft glass | 73 (28) | Glass Boots of Frost Abatement: SkyCraft glass_boots (Protection III) |
| Ebony armor | SkyCraft ebony | 71 (13) | Ebony Boots of Dwindling Shock: SkyCraft ebony_boots (Blast Protection II) |
| Dragonscale armor | SkyCraft daedric | 26 (8) | Dragonscale Boots of the Mammoth: SkyCraft daedric_boots (Unbreaking III) |
| Dragonplate armor | SkyCraft daedric | 26 (3) | Dragonplate Boots of the Mammoth: SkyCraft daedric_boots (Unbreaking III) |
| Daedric armor | SkyCraft daedric | 56 (19) | Daedric Boots of Brawn: SkyCraft daedric_boots (Unbreaking II) |
| Bonemold armor | SkyCraft steel | 66 (8) | Bonemold Boots of Resist Shock: SkyCraft steel_boots (Blast Protection I) |
| Chitin armor | SkyCraft elven | 77 (10) | Chitin Bracers of Wielding: SkyCraft elven_greaves (Thorns I) |
| Nordic armor | SkyCraft orcish | 64 (8) | Nordic Boots of Major Sneaking: SkyCraft orcish_boots (Feather Falling II) |
| Stalhrim armor | SkyCraft ebony | 57 (12) | Stalhrim Boots of the Mammoth: SkyCraft ebony_boots (Unbreaking III) |
| Lunar weapons | SkyCraft iron | 3 (3) | Lunar Iron War Axe: SkyCraft iron_war_axe (Sharpness I) |
| Lunar weapons | SkyCraft steel | 3 (3) | Lunar Steel War Axe: SkyCraft steel_war_axe (Sharpness I) |
| Dawnguard weapons | SkyCraft orcish | 2 (2) | Dawnguard Rune Hammer: SkyCraft orcish_war_axe (Smite II) |
<!-- /gen:enchanted -->

## Looted gear

Weapons and armor looted from a corpse or a container salvage into their material's smithing material, one for a weapon and two for a piece of armor, as iron and steel always did, so nothing NPCs carry is useless in Minecraft. The material comes from the item's WeapMaterial or ArmorMaterial keyword. Bought gear still becomes the SkyCraft piece itself, and bows, crossbows, pickaxes, woodcutter's axes and arrows keep their own rules from anywhere.

| Material | Weapon | Armor | Why |
|---|---|---|---|
| Iron | iron_ingot | 2 iron_ingot | As before |
| Steel | steel_ingot | 2 steel_ingot | As before |
| Imperial | steel_ingot | 2 steel_ingot | Skyrim's Imperial gear is steel |
| Ancient Nord (draugr) | steel_ingot | 2 steel_ingot | Ancient Nord weapons hit like steel ones |
| Orcish | orichalcum_ingot | 2 orichalcum_ingot | Its Skyrim smithing material, and the SkyCraft Orcish set's |
| Dwarven | dwarven_metal_ingot | 2 dwarven_metal_ingot | The same |
| Elven | refined_moonstone | 2 refined_moonstone | The same |
| Glass | refined_malachite | 2 refined_malachite | The same |
| Ebony | ebony_ingot | 2 ebony_ingot | The same |
| Daedric | ebony_ingot + daedra_heart | 2 ebony_ingot + daedra_heart | Ebony forged with one Daedra heart a piece (Skyrim's recipes and SkyCraft's); Skyrim's apothecaries sell hearts anyway |
| Dragon (dragonbone, dragonscale, dragonplate) | ebony_ingot + 2 bone | 2 ebony_ingot + 4 bone | The top tier, like Daedric, and dragon bone is bone |
| Falmer | turtle_scute | 2 turtle_scute | Falmer gear is chaurus chitin, which is turtle_scute here |
| Silver | 4 iron_nugget | 8 iron_nugget | Silver melts to iron, like Skyrim's other silver |
| Hide, leather, studded, fur | (none) | 2 leather | As before |

Gear of no material keyword has rules of its own by name ([More conversions](#more-conversions-the-reviewed-proposals)); staffs stay in Skyrim.

## Gear drops

Not a rule in `skycraft-items.txt`: a Skyrim person or creature you kill (your hit in its last 10 seconds) rolls each piece it wore, the way a Minecraft mob rolls its equipment, and drops the Minecraft version at the corpse (`loot/GearMap`, switch `gear_drops`). The odds are Minecraft 26.3's, set in `skycraft-gear-drops.txt`: 8.5% a piece (`DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE`), plus 1% per level of Looting on your weapon (Looting's `equipment_drops` effect), with the random wear `Mob.dropCustomDeathLoot` gives a dropped piece. Arrows and bolts drop as a skeleton's arrows: 0 to 2, plus 0 to 1 per level of Looting, never more than it had. The Skyrim gear stays on the corpse, to loot and salvage as ever. Your followers, and actors a spell summoned or raised, never drop.

Which piece it is comes from Skyrim, as the SKSE plugin reads it: a weapon's type, or the body slots an armor piece covers (the body slot wins, so a robe with a hood is a cuirass). So Zephyr is a bow, Spellbreaker a shield and a Dragon Priest mask a helmet, whatever their names say. Pickaxes and woodcutter's axes go by name, since Skyrim types them as war axes. Items from an older plugin, which doesn't send the kind, go by the words in the name (third column).

| Skyrim piece | Words in the name, without a kind | Minecraft gear |
|---|---|---|
| One-handed sword, dagger, greatsword | sword, greatsword, dagger, blade, scimitar, katana; a metal weapon with no weapon word (Chillrend) | `<tier>_sword` |
| War axe, mace, battleaxe, warhammer | axe, battleaxe, mace, warhammer, hammer | `<tier>_war_axe` |
| Head, hair or circlet slot | helmet, helm, hood, mask, crown, hat | `<tier>_helmet` |
| Body slot | anything else worn as armor (Armor, Cuirass, Mail) | `<tier>_cuirass` |
| Hands or forearms slot | gauntlets, bracers, gloves | `<tier>_greaves` (Minecraft has no glove slot) |
| Feet or calves slot | boots, shoes | `<tier>_boots` |
| Shield slot | shield | shield |
| Bow, crossbow | bow, crossbow | bow, crossbow |
| Arrows and bolts | | arrow |
| Pickaxe, Woodcutter's Axe (typed war axe) | pickaxe, woodcutter's axe | iron_pickaxe, iron_axe |
| Staff, hand to hand; spells, clothing, jewelry | staff; a weapon with neither a weapon word nor a metal (Wabbajack) | nothing |

| Material | Tier |
|---|---|
| Iron, Steel, Orcish, Dwarven, Elven, Glass, Ebony, Daedric | Its own SkyCraft set (`skycraft:steel_sword`, ...) |
| Imperial, Ancient Nord (draugr) | Steel |
| Falmer | Orcish |
| Silver | Skyrim Iron |
| Dragon (dragonbone, dragonscale, dragonplate) | Daedric |
| Hide, leather, studded, fur | Minecraft leather armor (leather_helmet, leather_chestplate, leather_leggings, leather_boots) |
| No material keyword (Forsworn, Stormcloak, guards, Nordic) | Weapons Skyrim Iron, armor Minecraft chainmail |
| Wood | wooden_sword, wooden_axe |

Headless: `SKYCRAFT_GEAR=1 python tools/fake_skyrim.py` kills the fake bandit after the script's punch and sends what it wore (an Iron Sword, an Iron Helmet, Hide Boots, a Hunting Bow and 12 Iron Arrows); with `chance = 1` in `skycraft-gear-drops.txt` every piece drops.

## Potions and poisons

Every Skyrim potion becomes a Minecraft potion with effects of its own, named like the Skyrim one, and every poison a splash potion to throw. Health potions are red, magicka blue, stamina green; the rest take Minecraft's mix of their effects' colors. Rules write `minecraft:potion{effect:amplifier:seconds + ...}`; a range like `0-2` grows with the name's tier.

| Tier | Words in the name |
|---|---|
| 0 | Minor, Weak, Brief |
| 1 | none of these (every crafted "Potion of ..." and "Poison of ...") |
| 2 | Plentiful, Draught, Potent, Extended, Enduring, Lasting |
| 3 | Vigorous, Solution, Philter, Virulent, Malign, Persisting, Prolonged |
| 4 | Extreme, Elixir, Deadly, Unceasing |
| 5 | Ultimate, Grand, Prime |

A range runs from tier 0 to tier 5 in proportion, rounded: levels I to III are I at tiers 0 and 1, II at 2 and 3, III at 4 and 5; 60 to 180 seconds is 84 s at tier 1, 156 s at tier 4. Seconds follow Skyrim's durations, stretched for Minecraft. Magicka has no Minecraft meter, so magicka potions give Absorption (a ward of golden hearts); stamina is the hunger bar and running.

57 of Skyrim's alchemy effects map to Minecraft effects (plus Ghostveil, Frostbite Venom and three effects from mods); Cure Disease and Cure Poison become a milk bucket.

| Skyrim effect | Minecraft effect(s), tiers | Example real names |
|---|---|---|
| Restore Health | Instant Health I to III | Potion of Minor Healing (I), Potion of Plentiful Healing (II), Potion of Ultimate Healing (III), Potion of Restore Health (I) |
| Restore Magicka | Absorption I to IV, 20 to 60 s | Potion of Minor Magicka (I, 20 s), Potion of Magicka (II, 28 s), Potion of Ultimate Magicka (IV, 60 s), Potion of Restore Magicka |
| Restore Stamina | Saturation, 4 to 20 food points | Potion of Minor Stamina (4), Potion of Stamina (7), Potion of Ultimate Stamina (20), Potion of Restore Stamina |
| Restore all three (Well-being) | Instant Health I to III, Absorption I to III for 30 to 90 s, Saturation 2 to 10 | Potion of Minor Well-being (I, I 30 s, 2), Potion of Vigorous Well-being (II, II 66 s, 7), Potion of Ultimate Well-being |
| Fortify Health | Health Boost I to IV, 60 to 180 s | Potion of Health (II, 84 s), Elixir of Health (III, 156 s), Potion of Fortify Health |
| Fortify Magicka | Absorption I to III, 60 to 180 s | Potion of Extra Magicka (I, 84 s), Elixir of Extra Magicka (III, 156 s), Potion of Fortify Magicka |
| Fortify Stamina | Speed I to II and Jump Boost I to II, 60 to 180 s | Potion of Enhanced Stamina (I, 84 s), Solution of Enhanced Stamina (II, 132 s), Stallion's Potion (I, 300 s) |
| Regenerate Health | Regeneration I to II, 45 to 180 s | Potion of Regeneration (I, 72 s), Elixir of Regeneration (II, 153 s), Potion of Regenerate Health |
| Regenerate Magicka | Absorption I to II, 150 to 300 s | Potion of Lasting Potency (I, 180 s), Solution of Lasting Potency (II, 240 s), Potion of Regenerate Magicka |
| Regenerate Stamina | Speed I to II for 120 to 300 s, Saturation 1 to 5 | Potion of Vigor (I, 156 s, 2), Elixir of Vigor (II, 264 s, 4), Potion of Regenerate Stamina |
| Fortify One-handed | Strength I to III, 60 to 180 s | Potion of the Warrior (I, 84 s), Elixir of the Warrior (III, 156 s), Potion of Fortify One-handed |
| Fortify Two-handed | Strength I to III, 60 to 180 s | Draught of the Berserker (II, 108 s), Elixir of the Berserker (III), Potion of Fortify Two-handed |
| Fortify One-handed with Light Armor (Conflict) | Strength I to III, Resistance I to II, Speed I, 60 to 180 s | Potion of Conflict (I, I, I), Grand Elixir of Conflict (III, II, I, 180 s) |
| Fortify Archery | Night Vision, 90 to 300 s (no Minecraft effect strengthens bows) | Potion of True Shot (132 s), Elixir of True Shot (258 s), Potion of Fortify Archery |
| Fortify Archery with Regenerate Stamina (Keenshot) | Night Vision 90 to 300 s and Speed I to II | Potion of Keenshot, Prime Elixir of Keenshot |
| Fortify Block | Absorption I to III, 60 to 180 s | Potion of the Defender (I), Philter of the Defender (II, 132 s), Potion of Fortify Block |
| Fortify Heavy Armor | Resistance I to III with Slowness I, 60 to 180 s | Potion of the Knight (I), Elixir of the Knight (III, 156 s), Potion of Fortify Heavy Armor |
| Fortify Light Armor | Resistance I to II with Speed I, 60 to 180 s | Skirmisher's Potion (I), Skirmisher's Elixir (II), Potion of Fortify Light Armor |
| Fortify Sneak | SkyCraft Stealth I to II, 60 to 240 s (quicker sneaking; mobs see a sneaking player later) | Potion of Light Feet (96 s), Draught of Light Feet (132 s), Elixir of Light Feet (II, 204 s), Potion of Fortify Sneak |
| Invisibility | Invisibility, 30 to 180 s | Potion of Brief Invisibility (30 s), Potion of Extended Invisibility (90 s), Potion of Prolonged Invisibility (120 s), Potion of Invisibility |
| Invisibility with Restore Health (Escape) | Invisibility 30 to 180 s and Instant Health I to III | Potion of Escape (60 s, I), Prime Elixir of Escape (180 s, III) |
| Fortify Lockpicking | Luck I to III, 60 to 180 s | Potion of Lockpicking (I), Elixir of Lockpicking (III), Potion of Fortify Lockpicking |
| Fortify Pickpocket | Luck I to III, 60 to 180 s | Potion of Pickpocketing (I), Philter of Pickpocketing (II), Potion of Fortify Pickpocket |
| Fortify Lockpicking with Pickpocket (Larceny) | Luck I to III, 60 to 180 s | Potion of Larceny (I), Prime Elixir of Larceny (III, 180 s) |
| Fortify Barter | Hero of the Village I to III, 60 to 300 s (villagers trade cheaper) | Potion of Haggling (I, 108 s), Elixir of Haggling (III, 252 s), Potion of Fortify Barter |
| Fortify Persuasion | Hero of the Village I to II, 60 to 300 s | Potion of Glibness (I), Elixir of Glibness (II), Potion of Fortify Persuasion |
| Fortify Smithing | Haste I to III, 60 to 180 s | Blacksmith's Potion (I, 84 s), Blacksmith's Elixir (III, 156 s), Potion of Fortify Smithing |
| Fortify Alchemy | Haste I to II, 60 to 180 s | Potion of Fortify Alchemy (I, 84 s) |
| Fortify Enchanting | Haste I to II, 60 to 180 s | Enchanter's Potion (I), Enchanter's Philter (II), Potion of Fortify Enchanting |
| Fortify Alteration | Resistance I to II, 60 to 180 s (Oakflesh and Stoneflesh) | Draught of Alteration (I), Elixir of Alteration (II), Potion of Fortify Alteration |
| Fortify Conjuration | Strength I to II, 60 to 180 s (bound weapons) | Conjurer's Potion (I), Conjurer's Elixir (II), Potion of Fortify Conjuration |
| Fortify Destruction | Strength I to II and Fire Resistance, 60 to 180 s | Potion of Destruction (I), Philter of Destruction (II, 132 s), Potion of Fortify Destruction |
| Fortify Illusion | Invisibility 20 to 60 s and Night Vision 60 to 180 s | Potion of Illusion (28 s, 84 s), Elixir of Illusion, Potion of Fortify Illusion |
| Fortify Restoration | Regeneration I to II, 60 to 180 s | Potion of the Healer (I), Elixir of the Healer (II), Potion of Fortify Restoration |
| Fortify Carry Weight | Strength I to II, 120 to 300 s (Skyrim calls it Potion of Strength) | Potion of Strength (I, 156 s), Elixir of Strength (II, 264 s), Potion of Fortify Carry Weight |
| Fortify Carry Weight with Stamina (Plunder) | Strength I to II and Speed I to II, 120 to 300 s | Potion of Plunder (I, 156 s), Prime Elixir of Plunder (II, 300 s) |
| Resist Fire | Fire Resistance, 60 to 300 s | Potion of Resist Fire (108 s), Philter of Resist Fire (204 s) |
| Resist Frost | Resistance I, 60 to 180 s | Potion of Resist Cold, Elixir of Resist Cold (156 s), Ice Wraith Essence, Potion of Resist Frost |
| Resist Shock | Resistance I and Fire Resistance (lightning sets you alight), 60 to 180 s | Potion of Resist Shock, Draught of Resist Shock (108 s) |
| Resist Magic | Resistance I to II, 60 to 180 s | Potion of Resist Magic (I), Elixir of Resist Magic (II), Soul Husk Extract |
| Resist Poison | Regeneration I to II, 20 to 60 s | Potion of Resist Poison (I, 28 s) |
| Waterbreathing | Water Breathing and Conduit Power, 30 to 240 s | Potion of Waterbreathing (72 s), Elixir of Waterbreathing (198 s) |
| Waterwalking | Dolphin's Grace, 60 to 180 s | Potion of Waterwalking (84 s) |
| Cure Disease, Cure Poison | a milk bucket, Minecraft's cure | Potion of Cure Disease, Potion of Cure Poison |
| Ghostveil | Invisibility, 60 s | Philter of the Phantom |
| Night Eye (mods) | Night Vision, 120 to 480 s | Potion of Night Eye (192 s) |
| Detect Life (mods) | Night Vision, 60 to 240 s | Potion of Detect Life |
| Muffle (mods) | SkyCraft Stealth I, 30 to 120 s | Potion of Muffle |
| Damage Health | splash Instant Damage I to III | Weak Poison (I), Poison (I), Potent Poison (II), Deadly Poison (III), Ice Wraith Bane (II), Poison of Damage Health |
| Lingering Damage Health | splash Poison I to III, 10 to 25 s | Weak Lingering Poison (I, 10 s), Potent Lingering Poison (II, 16 s), Deadly Lingering Poison (III, 22 s), Poison of Lingering Damage Health |
| Ravage Health | splash Wither I to II, 8 to 20 s | Poison of Ravage Health (I, 10 s) |
| Damage Magicka | splash Weakness I to II, 10 to 40 s | Weak Magicka Poison (I, 10 s), Deadly Magicka Poison (II, 34 s), Poison of Damage Magicka |
| Lingering Damage Magicka | splash Weakness I to III with Bad Luck, 20 to 40 s | Lingering Magicka Poison (I, 20 s), Lasting Magicka Poison (II, 30 s), Unceasing Magicka Poison (III, 40 s) |
| Damage Magicka Regen | splash Weakness I and Bad Luck I to II, 20 to 90 s | Weak Recovery Poison (20 s), Deadly Recovery Poison (II, 76 s), Poison of Damage Magicka Regen |
| Ravage Magicka | splash Weakness I to II, 20 to 60 s | Poison of Ravage Magicka (I, 28 s) |
| Damage Stamina | splash Hunger I to III, 10 to 30 s | Stamina Poison (I, 14 s), Virulent Stamina Poison (II, 22 s), Deadly Stamina Poison (III, 26 s) |
| Lingering Damage Stamina | splash Hunger I to III with Slowness I, 20 to 40 s | Lingering Stamina Poison (I, 20 s), Lasting Stamina Poison (II, 30 s), Unceasing Stamina Poison (III, 40 s) |
| Damage Stamina Regen | splash Slowness I and Hunger I, 20 to 90 s | Vigor Poison (34 s), Malign Vigor Poison (62 s), Poison of Damage Stamina Regen |
| Ravage Stamina | splash Mining Fatigue I to II and Hunger I to II, 10 to 40 s | Poison of Ravage Stamina (I, 16 s) |
| Paralysis | splash Slowness V and Mining Fatigue III, 3 to 15 s | Weak Paralysis Poison (3 s), Paralysis Poison (5 s), Virulent Paralysis Poison (10 s), Deadly Paralysis Poison (13 s) |
| Fear | splash Darkness and Weakness I, 10 to 30 s | Weak Fear Poison (10 s), Potent Fear Poison (18 s), Deadly Fear Poison (26 s) |
| Frenzy | splash Nausea, 5 to 20 s | Weak Frenzy Poison (5 s), Frenzy Poison (8 s), Deadly Frenzy Poison (17 s) |
| Slow | splash Slowness I to III, 15 to 45 s | Poison of Slow (I, 21 s) |
| Weakness to Fire | splash Weakness I to II, 15 to 45 s | Weak Aversion to Fire (I, 15 s), Deadly Aversion to Fire (II, 39 s), Poison of Weakness to Fire |
| Weakness to Frost | splash Slowness I to II, 15 to 45 s | Aversion to Frost (I, 21 s), Malign Aversion to Frost (II, 33 s) |
| Weakness to Shock | splash Glowing and Weakness I, 15 to 45 s | Aversion to Shock (21 s), Poison of Weakness to Shock |
| Weakness to Magic | splash Bad Luck I to II and Weakness I, 15 to 45 s | Potent Aversion to Magic (27 s), Poison of Weakness to Magic |
| Weakness to Poison | splash Poison I 5 to 15 s and Weakness I 15 to 45 s | Poison of Weakness to Poison (7 s, 21 s) |
| Frostbite Venom | splash Poison I 6 s and Slowness II 8 s | Frostbite Venom, Netch Poison |

Staying in Skyrim: The White Phial (it refills), Lotus Extract and Nightshade Extract (quest poisons), Unknown Potion, and drinks without a vendor keyword (Potion of Blood, Falmer Blood Elixir). Skooma converts: see below.

Crafted potion names longer than 27 letters arrive cut ("Potion of Fortify Two-hande"); SkyCraft gives back the whole name from `ItemRules.LONG_NAMES` for both the rules and the Minecraft potion's name. The three crafted Lingering Damage poisons share their first 27 letters, so they stay cut and count as Lingering Damage Health.

## Creature drops

What Skyrim creatures drop as Minecraft items when you kill them: the default rules in `skycraft-loot.txt` (`LootRules.DEFAULT_TEXT`), matched by the words of the creature's Skyrim name, first matching line first. They follow Minecraft's own mobs: a creature with a Minecraft counterpart rolls that mob's loot table, and the others drop in the same spirit and amounts. Only bosses (dragons, dragon priests, Alduin) give more than a Minecraft mob would. These come on top of whatever Skyrim leaves on the corpse, which item conversion turns into Minecraft items when you loot it.

A drop is one of:

| Rule | Means |
|---|---|
| `item 0-2` | 0 to 2 of it; `item 2` is exactly 2, `item` alone 1. Looting adds 0 to 1 per level, as Minecraft's `enchanted_count_increase` does |
| `item 1 2.5%` | One, 2.5% of the time. Looting adds 1% per level, as Minecraft's rare drops (`random_chance_with_enchanted_bonus`): 3.5% with Looting I, 5.5% with III |
| `table minecraft:entities/spider` | Minecraft's spider loot table, rolled as if you had killed a spider: the dead creature's stand-in is the mob, you are the killer. Minecraft then does the counts, chances, Looting, kill-only drops (spider eyes, blaze rods) and cooked meat |

Raw meat and fish come out cooked when the creature died burning or to a Fire Aspect weapon, as in Minecraft. Every kill credited to you pays out (a kill is yours when you hit the creature in its last 10 seconds), so "your kill only" drops always count.

An existing `config/skycraft-loot.txt` that still holds the old default rules, unedited, is replaced with these the first time it's read, and the old file is kept as `skycraft-loot.txt.old`. A file you edited stays as it is: delete it, or merge by hand, to get these.

Counts below are without Looting. Tables were read from Minecraft 26.3.

### Dragons

| Creature | Drops | Why |
|---|---|---|
| Alduin | dragon head, 2-4 netherite scrap, 3-6 diamonds, 4-8 gold ingots, 8-12 bones, 2-4 dragon's breath | The World-Eater, killed once: a trophy and a hoard |
| Ancient Dragon, Legendary Dragon, Paarthurnax, Odahviing, Durnehviir, Vulthuryol, Viinturuth, Kruziikrel, Sahrotaar | 5-10 bones, 2-3 phantom membrane, 1-3 dragon's breath, 2-5 gold ingots, 1-3 diamonds, netherite scrap 33%, dragon head 20% | The oldest dragons pay best. Bone and wing membrane from the body, Minecraft's dragon's breath, a dragon's hoard; dragon bone and scale make Skyrim's best armor, so the rare extra is netherite |
| Elder Dragon, Revered Dragon, Nahagliiv, Vuljotnaak, Voslaarum, Naaslaarum | 4-8 bones, 1-3 phantom membrane, 1-2 dragon's breath, 2-4 gold ingots, 1-2 diamonds, netherite scrap 20%, dragon head 10% | The middle tier |
| Dragon, Blood Dragon, Frost Dragon, Serpentine Dragon, Mirmulnir, Sahloknir | 3-6 bones, 1-2 phantom membrane, 0-2 dragon's breath, 1-3 gold ingots, 0-1 diamond, netherite scrap 10%, dragon head 5% | Still a boss fight; dragons come often, so the rare drops stay rare |
| Skeletal Dragon | 6-12 bones, 1-2 bone blocks, 1-3 gold ingots, 0-1 diamond, netherite scrap 10% | All bone, no flesh or wings |
| Dragon Priest, and the named ones (Krosis, Morokei, Hevnoraak, Nahkriin, Otar, Rahgot, Vokun, Volsung, Ahzidal, Dukaan, Zahkriisos) | Minecraft's evoker (totem of undying, 0-1 emerald), 1-3 bones, 1-3 gold ingots | Undead liches and dungeon bosses: the evoker's totem is a lich's phylactery |

### Undead and Daedra

| Creature | Drops | Why |
|---|---|---|
| Ghost, Spectral Draugr and other spectral things, Shade, Mistman | Minecraft's phantom (0-1 phantom membrane) | Undead flyers; ectoplasm is phantom membrane in item conversion |
| Draugr Deathlord, Draugr Overlord (and Death Overlord), Draugr Scourge, Draugr Wight Lord | Minecraft's husk (0-2 rotten flesh, 2.5% iron ingot, carrot or potato), 0-2 bones, 0-3 gold nuggets | Husks are Minecraft's dried dead; the strong draugr guard their tomb's gold |
| Draugr, Restless Draugr, Draugr Wight | Minecraft's husk, 0-1 bone | As above |
| Skeleton, Bonewalker, Boneman | Minecraft's skeleton (0-2 arrows, 0-2 bones) | The same creature |
| Wrathman | Minecraft's wither skeleton (coal, 0-2 bones, 2.5% wither skeleton skull) | The Soul Cairn's dark skeleton warriors |
| Ash Spawn | 0-2 bones, 0-2 charcoal | Risen from burnt ash |
| Flame Atronach (and Thrall) | Minecraft's blaze (0-1 blaze rod) | Minecraft's fire elemental; fire salts are blaze powder |
| Storm Atronach (and Thrall) | Minecraft's breeze (1-2 breeze rods) | Minecraft's storm elemental |
| Frost Atronach (and Thrall) | 0-1 packed ice, 0-3 snowballs | Minecraft has no ice elemental: frost salts are packed ice in item conversion |
| Seeker | Minecraft's squid (1-3 ink sacs) | Hermaeus Mora's floating tentacled things |
| Lurker | Minecraft's guardian (0-2 prismarine shards, cod or prismarine crystals, 2.5% a fish), 0-2 ink sacs | Hermaeus Mora's sea monsters |

### Dwemer, arthropods and Falmer

| Creature | Drops | Why |
|---|---|---|
| Dwarven Centurion | 2-4 dwarven metal ingots, 1-3 redstone, redstone block 25% | Built of dwarven metal (SkyCraft's ingot) and run on power, like an iron golem's 3-5 iron; the dynamo core is a redstone block in item conversion |
| Dwarven Ballista | 1-2 dwarven metal ingots, 0-2 redstone, 0-3 arrows | It shoots bolts |
| Dwarven Sphere | 1-2 dwarven metal ingots, 0-2 redstone, amethyst shard 10% | Its soul gem; a soul gem fragment is an amethyst shard in item conversion |
| Dwarven Spider and any other Dwemer machine | 0-1 dwarven metal ingot, 0-1 redstone | Small. Before the spider line, so it's a machine, not a spider |
| Chaurus Hunter (and Fledgling) | Minecraft's spider, 0-1 phantom membrane, turtle scute 25% | A flying chaurus: wings |
| Chaurus, Chaurus Reaper | Minecraft's spider (0-2 string, 0-1 spider eye when you made the kill), turtle scute 25% | Spider-like, with a chitin shell; chaurus chitin is turtle scute in item conversion |
| Giant Frostbite Spider | Minecraft's spider, 1-3 string | A bigger spider spins more |
| Frostbite Spider, Albino Spider | Minecraft's spider | The feedback's example: string and spider eyes at Minecraft's rates |
| Ash Hopper | 0-1 slime ball, 0-1 string | A big insect; ash hopper jelly is a slime ball in item conversion |
| Falmer Gloomlurker, Shadowmaster, Nightprowler, Warmonger | 1-2 sculk, 0-1 rotten flesh, echo shard 5%, sculk catalyst 5% | The Forgotten Vale's strongest Falmer |
| Falmer (all the others) | 0-1 sculk, 0-1 rotten flesh, echo shard 2.5% | Blind, hunting by sound under the earth like the Warden; a Falmer ear is rotten flesh in item conversion. Their gear is the NPC gear drop's |

### Spirits, beasts and monsters

| Creature | Drops | Why |
|---|---|---|
| Burnt Spriggan | 0-2 charcoal, 0-1 stick | Burnt wood |
| Spriggan, Spriggan Matron, Spriggan Earth Mother | 0-2 sticks, 0-1 resin clump | Tree spirits; spriggan sap is a resin clump in item conversion |
| Hagraven | Minecraft's witch (1-3 of glowstone dust, sugar, spider eye, glass bottle, gunpowder and sticks, 4-8 redstone), 0-2 feathers | Minecraft's witch, with a hagraven's feathers |
| Wispmother | 0-2 glowstone dust, 0-2 string | Glow dust and wisp wrappings, as item conversion has them |
| Wisp | 0-1 glowstone dust | It glows |
| Ice Wraith | 0-1 bone, 0-3 snowballs | Ice wraith teeth are bone |
| Armored Troll, Armored Frost Troll | Minecraft's ravager (a saddle), 0-2 slime balls, 0-2 bones | War beasts in harness, like the ravager |
| Frost Troll | 0-2 slime balls, 0-2 leather, 0-2 snowballs | Troll fat is a slime ball in item conversion |
| Troll | 0-2 slime balls, 0-2 leather, 0-1 bone | As above |
| Werewolf | 0-2 leather, 0-2 bones, 0-1 rotten flesh | Hide and bone |
| Werebear | 1-3 leather, 0-2 bones, 0-1 rotten flesh | A bigger hide |
| Vampire Lord, Harkon | 1-3 gunpowder, 0-2 phantom membrane | Vampire dust is gunpowder in item conversion; wings |
| Death Hound | 0-2 rotten flesh, 0-1 bone | Undead dogs |
| Gargoyle | 0-2 cobblestone, 0-1 flint | Living stone |
| Giant, Frost Giant, Karstaag | 0-2 leather, 1-3 bones, 0-2 rotten flesh, 0-4 gold nuggets | Last of all, so Giant Frostbite Spider and Giant Mudcrab match their own lines first; a giant's toe is rotten flesh in item conversion |

### Animals

| Creature | Drops | Why |
|---|---|---|
| Mammoth | 2-5 beef, 1-3 leather, 1-2 bones | More of a cow's beef and hide, and tusk bone |
| Sabre Cat, Snowy Sabre Cat, Vale Sabre Cat | 0-2 leather, 0-1 bone | Hide and tooth |
| Bear, Cave Bear, Snow Bear | Minecraft's polar bear (0-2 cod or salmon), 0-2 leather | Minecraft's bear, with the pelt |
| Wolf, Ice Wolf | 0-2 bones, 0-1 leather | Minecraft's wolves drop nothing, so they keep their pelt and bones |
| Cow, Highland Cow, Deer, Vale Deer, Elk | Minecraft's cow (0-2 leather, 1-3 beef) | The cow is Minecraft's deer |
| Chicken | Minecraft's chicken (0-2 feathers, 1 chicken) | The same animal |
| Horse | Minecraft's horse (0-2 leather) | The same animal |
| Hare | Minecraft's rabbit (0-1 rabbit hide, 1 rabbit, rabbit's foot 10%) | The same animal |
| Bristleback (and Boar) | Minecraft's hoglin (2-4 porkchops, 0-1 leather) | Minecraft's boar |
| Goat | 1-2 mutton, 0-1 leather | Minecraft's goats drop nothing: a sheep's mutton and the hide |
| Fox | 0-2 rabbit hide | Minecraft's foxes drop nothing: a small pelt |
| Horker | 1-3 beef, 0-2 bones | Horker meat is beef and a tusk two bones in item conversion |
| Mudcrab, Giant Mudcrab | 0-2 cod, turtle scute 10% | Legs as fish, now and then the chitin |
| Slaughterfish | Minecraft's salmon (1 salmon, bone meal 5%) | A fish; slaughterfish scales are bone meal in item conversion |
| Netch, Bull Netch, Betty Netch | 0-2 leather, 0-2 slime balls | Netch leather and netch jelly |
| Venomfang Skeever | 0-1 rotten flesh, 0-1 rabbit hide, spider eye 25% | A poisonous skeever |
| Skeever | 0-1 rotten flesh, 0-1 rabbit hide | A skeever tail is rotten flesh in item conversion; a small hide |
| Riekling | 0-1 bone, 0-1 leather | Small and tribal; their spears are the NPC gear drop's |

### Humanoids

After the creatures and before the giants, so a Vampire Lord matches its own line first and a Vampire this one. People carry coin (gold nuggets: septims are gold coins) and odds and ends. What they wore is a separate feature, the gear drop: each worn piece becomes its SkyCraft tier by kind and material (`GearMap`), for any actor, bandit or creature.

| Creature | Drops | Why |
|---|---|---|
| Bandit Chief, Bandit Marauder | 1-2 gold ingots, 3-8 gold nuggets, emerald 25% | The camp's leaders keep its take: gold and now and then a gem |
| Bandit, Bandit Outlaw, Thug, Highwayman, Plunderer | 1-4 gold nuggets, 0-1 string, 0-1 flint | Coin and a few odds and ends |
| Forsworn (Briarheart, Forager, Ravager, ...) | 0-2 gold nuggets, 0-2 feathers, 0-1 bone | Little coin; feathers and bone from their headdresses and fetishes |
| Necromancer | 1-3 gold nuggets, 0-2 bones, amethyst shard 10% | Bones for their work; a soul gem fragment is an amethyst shard in item conversion |
| Silver Hand | 1-3 gold nuggets, 1-3 iron nuggets | Silver melts to iron nuggets in item conversion |
| Vampire (any name with the word, but a Vampire Lord) | 0-3 gold nuggets, 0-1 gunpowder | Vampire dust is gunpowder in item conversion |
| Mage, Battlemage, Wizard, Sorcerer, Conjurer, Pyromancer, Cryomancer, Electromancer, Warlock, Spellsword | 1-3 gold nuggets, 0-1 paper, 0-1 lapis lazuli | Coin, notes, and lapis for enchanting |
| Guard, Soldier, Legionnaire, Stormcloak | 1-3 gold nuggets, 0-1 bread | A soldier's pay and rations |

Creatures and people that match no rule drop nothing: named NPCs (Lydia) and armed Daedra (Dremora) get no creature drops, only their gear. Dogs drop nothing either, as in Minecraft.

## Skooma

Skooma and Redwater Skooma become SkyCraft potions named after them, holding SkyCraft's Skooma effect: faster
running, higher jumps that don't hurt to land, and quick, quiet sneaking that mobs notice later. Skooma is level I for
180 s, Redwater Skooma level II for 150 s; both give a little saturation, moon sugar's rush of stamina. Drinking it
can make the player addicted (feature `skooma_addiction`, `skycraft-skooma.txt`; see FEATURES.md). An Empty Skooma
Bottle is a glass bottle. Rules write SkyCraft's effects with their namespace:
`minecraft:potion{skycraft:skooma:0:180 + saturation:0:0 #b26be0} 1`, `skycraft:stealth`.

| Item | Becomes |
|---|---|
| Skooma | potion "Skooma": Skooma I, 180 s, Saturation I |
| Redwater Skooma | potion "Redwater Skooma": Skooma II, 150 s, Saturation II |
| Empty Skooma Bottle | glass bottle |

