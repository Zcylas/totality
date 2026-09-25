# TOTALITY — Block Breaking V2 accepted vanilla ledger

**Design handoff for Pass 3 | 25 September 2026 | Minecraft 26.2.**

## Authority and limits

This is a consolidated ledger of the decisions accepted in the Block Breaking V2 design conversation, updated through the Pass 2 handoff. It is **a design specification, not an assertion that the entries have been implemented**. The older v3.6 master (§26L/§26M) provides architecture/history; later explicit decisions in this ledger supersede its older status, Gold, Hoe, Force Tolerance and Power statements. The 2026-09-24 readiness-audit CSV is evidence for the registry and *old fallback values*, not a numerical design authority.

**Do not invent an HP number for a registry ID absent from this ledger.** Assign the accepted profiles below to actual verified 26.2 IDs/tags/classes, flag unsupported/ambiguous coverage as `UNRESOLVED_DESIGN`, and reconcile counts against a fresh runtime registry. A family applies only where its listed material and form genuinely match; e.g. generic `slabs` mixes stone and wood. Do not use substring guessing as the only membership proof. A tag-backed family can require exact per-block overrides.

Legend: all HP figures are **maximum Block Durability**, not current placed Integrity, Structure Strength, vanilla hardness, or tool durability. `neutral` means explicitly no preferred tool, **not** a wrong-tool penalty. Unless a verified accepted exception appears, retain vanilla-derived Required Mining Tier and existing harvesting/drops. `S` = SPECIAL behavior, with no finite HP; `U` = UNBREAKABLE, with no finite HP; `NA` = not applicable. `instant` describes break timing separately from S. An entry not tagged `S`/`U` is Ordinary finite-HP unless otherwise stated.

## Profile architecture and lifecycle (locked)

- Resolve exact block/state > exact block > authored material + form > material family default > current compatibility fallback; each field independently resolves first non-null. Explicit behavior classification takes precedence over numeric HP. Never derive form values from a universal geometric formula.
- Full and state-specific forms are *authored*. For double slabs, use the accepted full-equivalent HP; merging a second slab preserves remaining Integrity percentage. State changes such as lit/facing/open/waterlogged, container contents, copper oxidation, etc. must not automatically heal a block.
- The owner is per position by default. Both halves of each Door and Bed share a canonical owner, as do Piston base/head when extended. Both halves of a double Chest have **independent** damage. Legacy per-half Door/Bed records fold into owner by most damaged **recovered percentage**, never sum.
- Legitimate authored physical transformations and profile-max changes preserve remaining percentage, with rounding/clamping. *Only the explicit transaction `BlockDamageStorage.transformBlock` may preserve across block IDs*: a lazy ID mismatch must delete the record. Different-ID and same-ID remove/replacement starts intact. An Ordinary ↔ SPECIAL/UNBREAKABLE classification boundary clears finite Integrity, with no dormant record.
- Non-ordinary classification is not a zero-HP ordinary block. Explicit SPECIAL preserves its authored interaction/vanilla behavior; U has no numerical HP. Existing unbreakable/protected vanilla rules remain. Exceptional authorized interaction hook is reserved for future Core.
- A finite-HP block's maximum, preferred tool and required tier in Tooltip V2 come from the same resolved profile as mining. Placed Integrity is world state, not a BlockItem tooltip value; neutral shows `No preferred tool`, tier is separate.
- Conventional Wood/Gold/Stone/Copper/Iron/Diamond/Netherite Pickaxe/Axe/Shovel/Hoe have authored Damage/Speed 20/1.50; 25/3.00; 35/2.00; 40/2.10; 50/2.25; 75/2.40; 100/2.50. Tool tiers 1/1/2/2/3/4/4; Tier 5 reserved for Core. Gold durability 32, supports Impact. Sword and Shears are specialized sources, *excluded from Alt/Power Mining*, not conventional profiles. Bare-hand Power remains. Force Tolerance data reserved, numeric tooltip hidden.
- Four Power zones and wear remain as accepted; extraordinary destructive-system integration is deferred to Totality Core.

## Accepted ordinary material and form HP ledger

The named members are an inclusion specification to map against verified IDs. Include color/wood/oxidation/wax variants only where stated; a variant not explicitly specified is not permission to infer new balance.

| Material / matched block family | Maximum HP by authored form / exact exception | Effective tool |
|---|---|---|
| Ordinary Stone: Stone, Cobblestone, Andesite, Diorite, Granite, Smooth Stone, Stone Bricks and normal polished/chiseled/mossy variants | Full 100; single slab 50; double slab 100; stairs/walls 75; **Cracked Stone Bricks 75** | Pickaxe |
| Ordinary overworld Wood: logs, wood, stripped logs/wood, planks and ordinary matching wood building variants | Full/log/planks 100; single slab 50; double 100; stairs 75; fence 50; gate 75; whole 2-block Door **shared 100**; trapdoor 50 | Axe |
| Deepslate: natural/Cobbled/Polished/Bricks/Tiles/Chiseled matching family | Full 150; slab 75; double 150; stairs/walls 115; Cracked Deepslate Bricks/Tiles 115 | Pickaxe |
| Ordinary dirt/grass | Dirt and Grass Block 100 | Shovel |
| Sand, Red Sand, Gravel | Full 75 | Shovel |
| Clay and wet Mud | Full 100 | Shovel |
| Packed Mud, Mud Bricks | Full 120; Mud Brick slab 60; double 120; stairs/wall 90 | Pickaxe |
| Sandstone and Red Sandstone (normal/smooth/cut/chiseled) | Full 100; slab 50; double 100; stairs 75 | Pickaxe |
| Bricks; Terracotta, dyed Terracotta and Glazed Terracotta | Full 150; Brick slab 75; double 150; stairs/wall 115 | Pickaxe |
| Glass, Stained Glass, Tinted Glass | Full 50; panes/stained panes 25 | Neutral; no tier |
| Ice family | Ice 50; Packed Ice 100; Blue Ice 150 | Neutral; no tier |
| Snow | Snow Block 50; Snow Layers **5 per layer** (5–40) | Shovel; no tier |
| Netherrack and Crimson/Warped Nylium | Full 50 | Pickaxe for netherrack; preserve Nylium tool/harvest specifics |
| Basalt, Polished Basalt, Smooth Basalt | Full 150 | Pickaxe |
| Blackstone/Gilded/Polished/Brick/Chiseled | Full 120; slab 60; double 120; stairs/wall 90; cracked brick 90 | Pickaxe |
| Nether Bricks and Red Nether Bricks, normal/chiseled | Full 150; slab 75; double 150; stairs/walls 115; cracked 115; fence 75 | Pickaxe |
| End Stone / End Stone Bricks | Full 200; slab 100; double 200; stairs/wall 150 | Pickaxe |
| Purpur / Purpur Pillar | Full 150; slab 75; double 150; stairs 115 | Pickaxe |
| Ordinary ores: matched **Stone-host / Deepslate-host / Netherrack-host** ore variants | Host +20: Stone 120; Deepslate 170; Netherrack 70. Other/new hosts require a separately accepted value. **Ancient Debris is exact 300** | Pickaxe; preserve vanilla-derived tier/loot |
| Mineral storage blocks | Coal Block 100; Raw Copper/Iron/Gold Blocks 150; Copper/Gold/Lapis/Redstone Blocks 150; Iron/Emerald Blocks 200; Diamond Block 250; Netherite Block 400 | Pickaxe; vanilla-derived tiers |
| Copper construction variants (normal, cut, chiseled, oxidation, waxed) | Full 150; slab 75; double 150; stairs 115; grate 75; shared 2-block door 100; trapdoor 75. Oxidation/wax **no HP change** | Preserve accepted/vanilla appropriate tool, generally Pickaxe; check per ID |
| Iron details | Bars 100; 2-block Door shared 150; Trapdoor 100; Chain 50 | Pickaxe |
| Anvil (normal/chipped/damaged) | 400 / 300 / 200; preserve remaining Integrity percentage through degradation | Pickaxe |
| Hopper / Cauldrons | Hopper 150; Cauldron (all fill/content states) 150, preserving absolute accumulated damage across contents | Pickaxe |
| Other metal utility | Lightning Rod 75; Copper Bulb 150; Heavy Weighted Plate 75; Light Weighted Plate 60 | Vanilla-appropriate; Pickaxe for rod/bulb; plates preserve established tool behavior |
| Furnace-family | Furnace 150; Smoker 150; Blast Furnace 250; lit/unlit preserve damage | Pickaxe |
| Mechanisms | Dispenser/Dropper/Observer 150; Piston/Sticky Piston 200 shared with extended head | Pickaxe |
| Tables/workstations | Crafting/Cartography/Fletching 100 Axe; Smithing 150 Axe; Stonecutter 150 Pickaxe; Grindstone 100 Pickaxe; Brewing Stand 75 Pickaxe; Loom/Composter 75 Axe; Lectern 100 Axe | As listed |
| Wooden functional blocks | Barrel/Bookshelf/Chiseled Bookshelf/Jukebox/Note Block 100 | Axe |
| Storage containers | Chest/Trapped Chest 100 **per half** Axe; all-color Shulker Boxes 150 neutral; Ender Chest 500 Pickaxe | As listed; preserve contents and drops |
| Obsidian / Crying Obsidian | 600 each | Pickaxe, vanilla-derived requirement |
| Lighting | Glowstone 50 neutral; Sea Lantern 100 neutral; Shroomlight 100 Hoe; Redstone Lamp 150 Pickaxe; Lantern/Soul Lantern 50 Pickaxe; Copper Lantern 75 (tool per actual vanilla); End Rod 50 neutral | As listed |
| Rails and circuitry | All rail variants 25 Pickaxe; Repeater/Comparator 25 neutral; Daylight Detector 75 Axe; Target Block 100 Hoe | As listed |
| Organic | All Leaves 25 Hoe; Moss Block 50 Hoe; Hay Bale/Dried Kelp/Wart Blocks 100 Hoe | As listed; preserve shears/leaf decay |
| Cactus | 50 **per segment** | Neutral |
| Crafted Bamboo construction | Full/planks/mosaic 100; single slab 50/double100; stairs75; fence50/gate75; shared door100; trapdoor50 | Axe |
| Giant Mushrooms | Caps 50; stems 75 | Axe |
| Gourds and bees | Pumpkin/Carved Pumpkin/Jack o'Lantern/Melon 75 Axe; Cocoa Pod 25 Axe; Bee Nest/Beehive 100 Axe; Honeycomb Block 75 neutral | As listed |
| Wool and bedding | Wool all colors 75 neutral with special shears handling; Bed all colors **shared 100 neutral** | As listed |
| Concrete | Concrete Powder all colors 75 Shovel; solid Concrete all colors 150 Pickaxe; solidification preserves percentage via authorized event path | As listed |
| Quartz construction | Normal/Smooth/Chiseled/Pillar/Bricks full 150; slab75/double150; stairs115 | Pickaxe |
| Prismarine and Prismarine Bricks | Full 150; slab75/double150; stairs/wall115 | Pickaxe |
| Dark Prismarine | Full 200; slab100/double200; stairs150 | Pickaxe |
| Sponge / Wet Sponge | 50 each, preserve percent through verified wet/dry transformation | Hoe |
| Full Coral blocks (living/dead) | 50 each; dying preserves percent through verified transformation | Pickaxe |
| Tuff/Polished/Bricks/Chiseled | Full 120; slab60/double120; stairs/wall90 | Pickaxe |
| Calcite / Dripstone Block | 100 each | Pickaxe |
| Amethyst | Amethyst Block/Budding Amethyst 150; Amethyst Cluster 25; Budding remains non-obtainable | Pickaxe |
| Sculk family | Sculk/Sensor/Calibrated Sensor 100; Catalyst/Shrieker 150; Vein instant SPECIAL | Hoe |
| Reinforced Deepslate | Exact **1,000**, destructible but non-obtainable | Pickaxe |
| Nether terrain | Soul Sand 75 Shovel; Soul Soil 100 Shovel; Magma Block 150 Pickaxe | As listed |
| Enchantment/transport/respawn utility | Enchanting Table 400 Pickaxe; Beacon 250 neutral; Lodestone 300 Pickaxe; Respawn Anchor 400 Pickaxe; Conduit 150 neutral | As listed |
| Encounter/reward blocks | Monster Spawner 300 Pickaxe; Trial Spawner 500 neutral; Vault/Ominous Vault 500 neutral; all non-obtainable | As listed |
| Misc functional | Campfire/Soul Campfire 100 Axe; Bell 150 Pickaxe; Bone Block 100 Pickaxe; all Froglights 100 Hoe | As listed |
| Climb and utility | Ladder 25 Axe; Scaffolding 50 Axe; Hanging/Wall Hanging Signs 25, ordinary standing/wall Signs SPECIAL instant | Preserve relevant drops and support behavior |
| Eggs | Sniffer Egg 25 neutral; Turtle Eggs SPECIAL; Dragon Egg SPECIAL | As listed |
| Infested host variants | Infested Stone 100; Cobblestone 100; Stone Bricks/Mossy/Chiseled 100; Cracked Stone Bricks 75; Infested Deepslate 150 | Pickaxe; preserve silverfish release and vanilla loot behavior |

## SPECIAL, instant, and UNBREAKABLE

`SPECIAL` is an explicit behavior classification, not shorthand for `instant`. Preserve original loot, interactions and any survival restrictions.

**Accepted SPECIAL/vanilla-owned:** Cobweb (**not instant**; vanilla Sword/Shears speed and drops); Decorated Pot; TNT (its special/instant behavior); Dragon Egg; Turtle Eggs; Frogspawn (instant); Powder Snow; portals (their portal behavior); Sculk Vein (`sculk_vein`) (instant); Pointed Dripstone (instant); small, medium and large Amethyst Buds (instant); small coral/coral fans (instant); growing Bamboo, Sugar Cane, Kelp (instant); ordinary growing crops and small flora (instant); Carpet/Banners (instant); Vines (instant); standing/wall Signs (instant); Flower Pots/Potted Plants (instant); Torches/Redstone Torches/Soul Torches, candles, ordinary buttons/levers/redstone wire/tripwire/hooks and ordinary wooden/stone pressure plates (instant); Candle Cakes and other relevant interactive hybrids SPECIAL (retain behavior). Honey Block and Slime Block **instant** as accepted; only mark SPECIAL where vanilla path/accepted handling genuinely requires it.

**Accepted explicit UNBREAKABLE / protected:** Bedrock, Barrier, command-block family, structure/jigsaw/test family, Light, End Portal Frame, and technical Moving Piston; any existing vanilla unbreakable portal or similar technical ID must remain unbreakable/portal-owned according to actual registry and behavior. Do not put finite HP on these. The readiness audit listed 15 unbreakable entries; its CSV supplies the exact runtime IDs, which must be checked rather than reconstructed from this sentence.

**Explicitly Ordinary despite special drops/obtaining restrictions:** Budding Amethyst (150, non-obtainable), Reinforced Deepslate (1,000, non-obtainable), Ancient Debris (300), Monster/Trial Spawners (300/500, non-obtainable), Vaults (500, non-obtainable), Sniffer Egg (25). Their destructive terminal interactions and drop restrictions remain authoritative.

## Effective-tool and tier constraints

Use the third column above as authoritative when it differs from vanilla tags. Populate tag-based matches using the **actual 26.2 registry and actual mining/hoe tags**. `neutral` is deliberate (full/pane glass, ice, Shulker Box, glowstone, Sea Lantern, End Rod, Wool/Bed, Honeycomb, Repeater/Comparator, Beacon, Conduit, Cactus, Sniffer Egg, Trial Spawner/Vault, etc.). Where this ledger says preserve vanilla-appropriate tool rather than an exact category, use the actual vanilla behavior and mark unresolved if ambiguous; do not guess.

**Required Mining Tier:** retain vanilla-derived block tier/drop qualification unless an explicit override is accepted. Existing Diamond-level Ruby requirement is preserved (provisional Ruby ore 120 Stone / 170 Deepslate). Gold tool Tier 1; Diamond/Netherite both Tier 4; Tier 5 reserved. Ordinary ore host +20 does not imply identical harvest requirements among ore minerals. Harvest/obtainability is separate from HP; correct tool vs under-tier semantics remain V2's accepted rules.

**Cobweb confirmation:** SPECIAL and **vanilla-owned**; not instant. Therefore Pass 2's Totality Sword/Shears strikes on Cobweb cease to apply when its explicit SPECIAL profile is authored. Preserve vanilla breaking speed, Sword string and Shears cobweb drop. Never silently treat it as an Ordinary finite-HP block to retain Pass 2's behavior.

## Physical transformations — implementation gate

Only author and hook a transform when its actual mutation path is safely routed through `BlockDamageStorage.transformBlock`. Pair registration alone does not preserve damage. Accepted conceptually: slab merging; Concrete Powder → Concrete; Anvil degradation; Sponge wet/dry; living → dead Coral; Copper weathering/waxing; Log stripping; Dirt/grass → Path/Farmland/Dirt where applicable. If a vanilla event cannot safely call the transactional API without regressions, leave it unhooked and list as deferred; **do not fake preservation by matching two IDs later**. Piston-pushed block arrival intact is the current documented V2 behavior, pending future authored movement transfer.

## Explicitly parked, not a Pass 3 balance source

- **Totality-specific** fictional materials, storage, machines and vibranium: defer numerical tuning to Totality Core. Ruby host baseline 120/170 and Diamond harvest requirement are accepted *provisionally*, not permission to rebalance the other custom ores. Vibranium tag defect requires its own scoped future resolution, not guessed mining tier or HP.
- Veinminer, Heat Vision, Ground Slam, Break/Smelt runes and other extraordinary destruction: Totality Core.
- Structural Strength, density, resistance, Force Tolerance mechanics and material provenance: Totality Core / Architecture owners.
- No authored number was confirmed here for generic Coarse Dirt, Rooted Dirt, Podzol, Mycelium, Dirt Path and Farmland as an entire 100-HP family; these were suggested in an earlier pass but lack explicit confirmation in the available preserved decisions. **Mark as review if the full conversation record does not resolve them.** Likewise Suspicious Sand/Gravel inheriting 75 was proposed rather than conclusively locked: do not silently label as accepted.
- Other examples not explicitly covered in tables, including the entire pool of block-entity and state variants, must be mapped by actual membership and classified `UNRESOLVED_DESIGN` if no accepted rule fits. Do not mistake zero missing registry IDs under the compatibility fallback for zero outstanding authored designs.

## Pass 3 evidence and stop rule

Produce one record per actual 26.2 block ID with classification, material, form, finite HP if any, tool affinity, tier, provenance and ambiguity flag. Separate `ACCEPTED_AUTHORED`, `SPECIAL`, `UNBREAKABLE`, `NA`, `COMPAT_FALLBACK`, and `UNRESOLVED_DESIGN`; show counts without converting unresolved into made-up profiles. Preserve all existing drops and test in isolated worlds. Do not commit/push until the whole V2 milestone is reviewed and separately authorized.
