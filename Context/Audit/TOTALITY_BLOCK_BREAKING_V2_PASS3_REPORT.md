# TOTALITY — Block Breaking V2, Implementation Pass 3
## Authored Vanilla Block Dataset and Registry Coverage

**Date:** 2026-09-25 · **Target:** Minecraft 26.2 / Fabric / Java 25 · **Branch/HEAD:** `master` / `7213407` (unchanged; nothing committed, staged or pushed)

**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_BLOCK_BREAKING_V2_PASS3_REVIEW.zip`

**Coverage CSVs (also in the bundle):**
- `Context/Audit/TOTALITY_BLOCK_BREAKING_V2_PASS3_BLOCK_PROFILE_COVERAGE.csv`: one row per registry block.
- `Context/Audit/TOTALITY_BLOCK_BREAKING_V2_PASS3_FALLBACK_AND_UNRESOLVED.csv`: remaining fallback and unresolved cases.

---

## 0. Authority and method

- **Numerical authority:** `Context/References/TOTALITY_BLOCK_BREAKING_V2_ACCEPTED_VANILLA_LEDGER_2026-09-25.md`.
  - Architecture/history: `Context/References/TOTALITY_MASTER_v3.6_SECTIONS_26L_26M_EXTRACT.md` (§26L/§26M). Where they conflict (Gold, Hoe, Force Tolerance, Power), the ledger supersedes it.
  - Read before any authoring.
- **Baseline:** `src/` was snapshotted before the first edit. `TASK_ONLY.patch` is computed against it, and applying it reproduces the current `src/` exactly (verified with `diff -r`). Pass 1, Pass 2, the follow-up and all unrelated uncommitted work are excluded from the patch.
- **Inventory:** a **fresh runtime inventory** was generated from the live 26.2 registry before authoring (`coverage/pre-authoring-inventory/`), and again after it. The readiness-audit CSV was used only for planning.
- **No invented values.** Every number comes from a ledger row. Membership is explicit registry ids, or a vanilla tag whose membership matches the ledger wording exactly.
  - Colour, species and oxidation variants are included **only where the ledger row states them**.
  - Every id is resolved against the live registry at registration. A missing id is recorded and fails verification; `missingIds()` is empty in the live run.
- **Unresolved means fallback, reported separately.** An entry no accepted rule covers keeps the pre-existing compatibility fallback in game and is reported `UNRESOLVED_DESIGN`, never promoted to "accepted".

---

## 1. Sword / Shears excluded from Alt/Power Mining (implemented)

- **Shared rule:** `MiningTier.excludedFromPowerMining(stack)`, true for `#minecraft:swords` and `ShearsItem`. It is used by both the client and the server.
- **Server (authority):**
  - `POWER_START` is refused with a Sword/Shears in hand;
  - `POWER_RELEASE` queues no force if the hand holds one at release, so a hold started with a pickaxe and swapped cannot fire;
  - `tryStartSwing` drops any queued Power swing whose source is excluded.
- **Consequence:** neither profiled Power zones nor the legacy Power multiplier / Force Tolerance stress can ever apply to them.
- **Client:** Alt is ignored for these items, so no meter opens and LMB is an ordinary hold.
- **Unchanged:** ordinary specialized breaking and harvesting; Sword vs Shears drops (Cobweb, now vanilla-owned, see §4); bare-hand Power (Totality melee weapons have no `TOOL` component, so they count as bare hands); the four conventional bands.

---

## 2. Registry inventory and coverage

**Live 26.2 registry:** 1,226 block ids = **1,196 `minecraft`** + **30 `totality`**.

| Category | Before Pass 3 | After Pass 3 |
|---|---|---|
| ACCEPTED_AUTHORED (ordinary, authored HP) | 42 | **697** (695 vanilla + 2 provisional Ruby ores) |
| SPECIAL (explicitly authored) | 0 | **304** |
| UNBREAKABLE | 15 | **15** |
| NOT_APPLICABLE | 6 | **6** |
| COMPAT_FALLBACK (Totality, deferred to Core) | 30 | **28** |
| UNRESOLVED_DESIGN (vanilla, fallback kept) | 1,133 | **176** |

**Authored provenance:**
- **Durability:** 458 material + form, 238 exact block, 1 state override (snow). Slab doubles and snow layers are further state overrides that resolve through material form.
- **SPECIAL:** 207 via tag-backed SPECIAL groups, 97 exact.

**Flags:**
- 50 MULTIBLOCK (doors, beds, pistons, tall plants);
- 10 DOUBLE_CHEST_INDEPENDENT;
- 59 STATE_DEPENDENT_MAX (slabs, snow);
- 193 BLOCK_ENTITY;
- 78 NO_BLOCKITEM, all included in the coverage.

**The 176 UNRESOLVED_DESIGN, by reason** (full list in the fallback/unresolved CSV):
- **147: no ledger rule matches.**
  - Nether (crimson/warped) wood and all its forms;
  - 26.2 families absent from the ledger: sulfur (15), cinnabar (13), wooden shelves (12), copper bars/chains/chests/golem statues, sulfur spike, dried ghast, creaking heart, crafter, heavy core;
  - resin family;
  - mob heads;
  - Sandstone/Red Sandstone **walls** (the ledger gives no wall value for that row);
  - petrified oak slab, mangrove roots, chorus, frosted ice, cake, glow lichen, moss/pale-moss carpets, pale moss block, big dripleaf;
  - copper torches (not named in the torch row);
  - 16 hardness-0 flora/technical ids already vanilla-instant (fire, spore blossom, small dripleaf, hanging roots, leaf litter, firefly bush, cactus flower, pale hanging moss, sea pickle, structure void, resin clump/block, …).
- **21: Copper Bulb / Lightning Rod / Copper Lantern oxidation and wax variants.** Their rows name the base block only; the "oxidation/wax no HP change" rule is stated for copper *construction*.
- **6: explicitly parked by the ledger:** Coarse Dirt, Rooted Dirt, Podzol, Mycelium, Dirt Path, Farmland.
- **2: explicitly parked by the ledger:** Suspicious Sand and Suspicious Gravel.

**The fallback count is not zero.** 176 vanilla blocks still resolve through the compatibility fallback, and the 28 Totality blocks remain deferred.

---

## 3. Authored dataset (`api/mining/VanillaBlockProfiles`)

Materials carry a default, authored forms and a tool. Exact block profiles hold exceptions and single-block rows. All ledger rows are implemented against verified ids:
- **Ordinary Stone:** 100 / slab 50 / double 100 / stairs & walls 75 / **Cracked Stone Bricks 75** (an exact entry beats the family; the material is kept).
- **Overworld Wood** (9 species): logs via the existing `#logs_that_burn` assignment; material renamed `totality:overworld_wood`. 100 / slab 50 / double 100 / stairs 75 / fence 50 / gate 75 / door 100 (shared) / trapdoor 50.
- **Crafted Bamboo:** the same forms; `bamboo_block`, stripped, planks and mosaic count as full blocks.
- **Deepslate:** 150 / 75 / 150 / stairs & walls 115; cracked 115.
- **Tuff:** 120 / 60 / 120 / stairs & walls 90.
- **Blackstone:** 120 / 60 / 120 / stairs & walls 90; cracked 90.
- **Nether Bricks:** 150 / 75 / 150 / stairs & walls 115; cracked 115; fence 75.
- **End Stone:** 200 / 100 / 200 / stairs & wall 150.
- **Purpur:** 150 / 75 / 150 / stairs 115.
- **Sandstone:** 100 / 50 / 100 / stairs 75.
- **Brick/Terracotta/Glazed:** 150 / 75 / 150 / stairs & wall 115.
- **Quartz:** 150 / 75 / 150 / stairs 115.
- **Prismarine:** 150 / 75 / 150 / stairs & wall 115.
- **Dark Prismarine:** 200 / 100 / 200 / stairs 150.
- **Packed Mud / Mud Bricks:** 120 / 60 / 120 / stairs & wall 90.
- **Earth:** Dirt/Grass 100; Sand/Red Sand/Gravel 75; Clay/Mud 100; Soul Sand 75, Soul Soil 100 (Shovel). Netherrack and Nylium 50; Basalt ×3 150; Magma 150; Calcite/Dripstone 100.
- **Ores:** host +20 → Stone 120 (8), Deepslate 170 (8), Netherrack 70 (2); Ancient Debris 300.
  - **Ruby: provisional 120 / 170**, as the ledger states. Its tier is untouched.
- **Mineral storage blocks** as listed; Obsidian and Crying Obsidian 600; Reinforced Deepslate 1,000.
- **Copper construction** (all 8 oxidation/wax states): 150 / 75 / 150 / stairs 115 / grate 75 / door 100 / trapdoor 75.
- **Iron and metal utility:**
  - Iron: bars 100, door 150, trapdoor 100, `iron_chain` 50.
  - Anvils: 400 / 300 / 200.
  - Hopper 150; Cauldrons ×4 150.
  - Lightning Rod 75, Copper Bulb 150 (base ids only).
  - Weighted plates 75 / 60.
- **Furnaces, mechanisms, workstations:**
  - Furnace and Smoker 150, Blast Furnace 250.
  - Dispenser, Dropper and Observer 150.
  - Piston, Sticky Piston and head 200 (shared).
  - Workstations with their ledger tools.
- **Wooden functional blocks** 100 Axe.
- **Containers:** Chest and Trapped Chest 100 **per half**; Shulker Boxes ×17 150 neutral; Ender Chest 500.
- **Lighting, rails, circuitry:** the ledger values and tools; Copper Lantern 75, with its tool kept vanilla ("per actual vanilla").
- **Organic:**
  - All leaves 25 Hoe (tag); Moss 50 Hoe; Hay, Dried Kelp and Wart Blocks 100 Hoe.
  - Cactus 50 neutral; Giant Mushrooms 50 / 75; gourds and bees as listed.
  - Wool ×16 75 neutral; Beds ×16 100 shared neutral.
  - Sponges 50 Hoe; coral blocks ×10 50; Froglights 100 Hoe.
- **Concrete:** Powder ×16 75 Shovel; Concrete ×16 150.
- **Cave and deep:** Amethyst Block and Budding 150, Cluster 25; Sculk 100 / 150 Hoe.
- **Functional and encounter:**
  - Enchanting Table and Respawn Anchor 400; Beacon 250 neutral; Lodestone 300; Conduit 150 neutral.
  - Spawner 300; Trial Spawner and Vault 500 neutral.
  - Campfires 100; Bell 150; Bone Block 100.
- **Climb and signs:** Ladder 25, Scaffolding 50, all hanging and wall-hanging signs 25 (tag).
- **Eggs:** Sniffer Egg 25 neutral.
- **Infested:** Stone, Cobble and Stone Bricks (normal/mossy/chiseled) 100; Cracked 75; Deepslate 150.

**Forms and states:**
- **Slabs:** each authored slab gets a state override for `type=double` that switches it to its material's authored **double-slab form**. The value is authored, not computed.
- **Snow:** exact state overrides give **5 HP per layer** (5–40).
- **Merging keeps the percentage.** Merging a second slab, or growing snow, is a same-id state change whose maximum changes, so the record keeps its remaining percentage (verified live: slab 25/50 → 50/100; snow 6/10 → 12/20).

**Effective tools:**
- Every ledger tool is authored, including all **neutral** entries: glass, panes, ice, shulker boxes, glowstone, sea lantern, end rod, wool, beds, honeycomb, repeater/comparator, beacon, conduit, cactus, sniffer egg, trial spawner and vault.
- Where the ledger says "vanilla-appropriate" (copper construction, nylium, copper lantern, weighted plates, hanging signs), the vanilla `mineable/*` tags remain.

**Required Mining Tier:** never authored. A live check confirms every ordinary block's tier equals the vanilla-derived value. Diamond and Netherite stay at 4; 5 stays reserved.

---

## 4. SPECIAL, UNBREAKABLE and technical

**SPECIAL (304), vanilla-owned.** Totality never strikes these. Vanilla break timing, interactions and drops stay authoritative. Authored as explicit ids and tag-backed groups:
- **Explicit ids:** Cobweb (**not instant**, confirmed); Decorated Pot; TNT; Dragon Egg; Turtle Egg; Frogspawn; Powder Snow; Sculk Vein; Pointed Dripstone; the three Amethyst Buds; all 30 small corals and fans; Bamboo stalk and sapling; Sugar Cane; Kelp; Honey and Slime Blocks; lever, redstone wire, tripwire and hook; torches, redstone torches and soul torches; vines, cave, weeping and twisting vines; explicit small flora, tall flowers, stems and fungi.
- **Tag-backed groups** (tags are bound after mod init, so they are lazy group assignments): crops, saplings, small flowers, flower pots, wool carpets, banners, standing and wall signs, candles, candle cakes, buttons, wooden and stone pressure plates.
- **Timing:** SPECIAL keeps vanilla timing. The ledger's "(instant)" describes the feel, so blocks such as banners and signs (hardness 1.0) or buttons and plates (0.5) break at vanilla speed and are not forced instant. This is recorded in the CSV issue column.

**UNBREAKABLE (15):** exactly the 15 runtime unbreakable ids.
- Bedrock, Barrier, Light;
- the three command blocks;
- Structure, Jigsaw, Test and Test Instance blocks;
- End Portal Frame and Moving Piston;
- **Nether Portal, End Portal, End Gateway:** unbreakable in the 26.2 registry, so they stay portal-owned and unbreakable.

**NOT_APPLICABLE (6):** air, cave air, void air, water, lava, bubble column.

**Preserved vanilla behaviour, verified in a real world** (client game test, default game rules, real player):
- Infested Stone releases a Silverfish on break, and Silk Touch prevents it (1 vs 0).
- A damaged chest keeps its contents, and the vanilla terminal break drops them (3/3 diamonds).
- Cobweb is not owned by Totality (a strike is INVALID); vanilla Sword → string, Shears → cobweb.
- Non-obtainable blocks (Reinforced Deepslate, Budding Amethyst, Spawners, Vault) keep their vanilla loot.

---

## 5. Integrity ownership and transitions

- **Unchanged Pass 1/2 foundation, re-verified with the new values:**
  - Doors, Beds and Piston base/head share one owner record;
  - double-chest halves are independent;
  - non-structural state changes keep damage;
  - removal and same-id replacement start intact (removal hook);
  - a SPECIAL/UNBREAKABLE boundary clears the record;
  - the lazy block-id transfer stays removed.
- **Old saves:** a record saved under the **pre-Pass-3 fallback maximum** is rescaled by percentage on first read. For example, a Netherrack record at 50% of 26.67 becomes 25/50 (verified live).

**Physical transformations: none hooked this pass.** Only slab merging and snow growth preserve their percentage, because they are same-id maximum changes. Each item below needs its own call-site integration through `BlockDamageStorage.transformBlock`. Until then, the removal hook starts the new block **intact**: correct and safe, just not preserved.

| Accepted transformation | Vanilla mutation path inspected | Current behaviour |
|---|---|---|
| Log stripping; copper scraping / wax-off | `AxeItem.useOn` → `level.setBlock` | new block intact |
| Copper waxing | `HoneycombItem.useOn` → `level.setBlock` | intact |
| Copper weathering | `ChangeOverTimeBlock.changeOverTime` (random tick) → `setBlockAndUpdate` | intact |
| Concrete Powder → Concrete | `ConcretePowderBlock` (`onLand`, `updateShape`, placement) | intact |
| Anvil degradation | `AnvilBlock.damage` (use / falling) | intact |
| Sponge wet/dry | `SpongeBlock` absorption, `WetSpongeBlock` placement in the Nether | intact |
| Living → dead coral | `CoralBlock.tick` | intact |
| Cauldron contents | `CauldronInteraction` lambdas | intact |
| Dirt/Grass → Path/Farmland | `ShovelItem` / `HoeItem.useOn` | intact; the targets are UNRESOLVED_DESIGN anyway |

---

## 6. Tooltip integration

- **One source:** BlockItem rows come from the same `BlockProfiles.resolveStatic` the engine uses.
  - Rows shown: maximum Block Durability, Required Mining Tier (always its own row), and Effective Tool ("No preferred tool" when neutral).
  - SPECIAL, UNBREAKABLE and not-applicable blocks show **no** block rows, so there is no "Block Durability 0".
- **Checked across the whole registry.** The client game test compares the contributor's rows with the engine profile for **every registered BlockItem**: 1,083 checked (855 with rows, 228 without), 0 mismatches.
- **Screenshots** (real hover path, GUI 2): Glass, Deepslate Tile Slab, Oak Leaves, Cobweb, Cracked Stone Bricks, Snow.
- **Presentation observation, not changed.** "No preferred tool" is longer than the row's inline space. Tooltip V2's existing wrap rule therefore puts it on its own line, flush with the row's left edge (see `screenshots/p3_01_glass_no_preferred_tool.png`). This is accepted Tooltip V2 behaviour and the brief says not to redesign it; a shorter label or width tweak is your call.

---

## 7. Test evidence

| Suite | Result |
|---|---|
| Build + JUnit (`build --rerun-tasks`, 7/7 tasks executed) | **1,982 tests, 0 failures, 0 errors** (Pass 2 ended at 1,972; +10) |
| Live, disposable `runVerificationServer`, existing opt-in gate only | **every suite passed**; MiningVerification **330/330** (was 309); DurabilityRegression 14/14 |
| Client, disposable `runClientGameTest` | TooltipV2Pass1 162/0, TooltipV2Enchantments 47/0, BlockBreakingPass2 11/0, **BlockBreakingPass3 16/0** (incl. 6 screenshots) |

**New JUnit tests:**
- `BlockProfileResolverTest` +3: the double-slab form switch; per-field provenance (a fallback SPECIAL is not authored); tag-backed SPECIAL groups with exact precedence.
- `BlockCoverageCategoryTest` (4): the category rules; unresolved design is never reported as accepted.
- `SpecializedPowerExclusionSourceRegressionTest` (3): the rule, and the server guards (start, release, queued swing) and client guard.

**New live checks** (`evidence/pass3-live-checks.txt`):
- Sword, Netherite Sword and Shears cannot start or release Power; a pickaxe hold swapped to a Sword cannot release; bare hands and profiled tools stay eligible.
- No missing ledger ids; the anchors; the forms; exact-over-family precedence; snow layers; tools; no tier drift across the whole registry; SPECIAL/UNBREAKABLE; Totality deferral with Ruby provisional.
- Slab-merge and snow-growth percentage; the pre-Pass-3 save migration; coverage integrity (one row per block, no vanilla block counted as fallback, ledger review items unresolved).

**Existing checks updated to accepted Pass 3 behaviour:**
- "Sword strike on Cobweb damages it" (Pass 2) became "Cobweb SPECIAL, vanilla-owned".
- The Pass 1 "unauthored Granite / Calcite" examples moved to Resin Bricks, which the ledger does not cover.
- The logs provenance layer changed from MATERIAL to MATERIAL_FORM.
- The generic Power-force checks now hold an Iron Pickaxe; they were inadvertently holding Shears from the Pass 2 fixture.

**Not run:**
- `gradle clean`: it would delete earlier test artefacts and the disposable run directories, so `--rerun-tasks` was used instead.
- Nothing ran against a personal or development save.

---

## 8. Design decisions and issues for Stefan
1. **Snow "Shovel; no tier".** The vanilla-derived tier (1) was kept. An explicit Tier 0 would also make bare-hand strikes harvest-eligible through HarvestGrant (snowballs by hand). Confirm the intended reading.
2. **Hardness-0 blocks with a ledger HP** (Repeater, Comparator, End Rod, Scaffolding) are authored but **INERT**: they stay vanilla-owned per your confirmed decision until a finite-HP ownership mechanism exists, and are flagged in the CSV.
3. **SPECIAL "(instant)" wording.** SPECIAL keeps vanilla timing, so banners, signs, buttons, plates, levers, bamboo, amethyst buds and pointed dripstone are not literally instant. Forcing instant breaks would be a new mechanism.
4. **Copper Bulb / Lightning Rod / Copper Lantern variants** (21): should the copper "oxidation/wax, no HP change" rule extend to these rows?
5. **New 26.2 families** absent from the ledger: sulfur, cinnabar, shelves, copper bars/chains/chests/golem statues, resin, sulfur spike, dried ghast, creaking heart, crafter, heavy core. Also nether wood, sandstone walls, mob heads, glow lichen and ambiguous small flora.
6. **Interpretations made; please confirm:**
   - hanging-sign rows cover **all** species via the vanilla hanging-sign tags, including Crimson and Warped;
   - `bamboo_block` and stripped bamboo count as Crafted Bamboo full blocks;
   - the Piston Head carries 200 (the shared record uses the base's profile anyway);
   - copper torches are not treated as "Torches".
7. **Tooltip long value:** see §6.
8. **Transformations:** which call-site integrations to do first (§5).

## 9. Manual in-game checks
- **Feel:** mining with the new values on real blocks (Deepslate 150, Obsidian 600, Reinforced Deepslate 1,000); Glass/Ice/Wool with each tool (neutral effectiveness).
- **SPECIAL blocks:** Cobweb with Sword and Shears (vanilla speed and drops); breaking SPECIAL blocks at vanilla timing.
- **Placement cases:** double-slab placement on a damaged slab; adding snow layers to damaged snow; shared door/bed cracks with the new values.
- **Power exclusion:** Alt+LMB with a Sword or Shears is an ordinary hold with no meter.

## 10. Changed files (task-only; `CHANGED_FILES.txt`, 14 files)
**New:**
- `api/mining/VanillaBlockProfiles.java` (the dataset)
- `api/mining/BlockCoverageReport.java`
- `BlockCoverageCategoryTest`
- `SpecializedPowerExclusionSourceRegressionTest`
- `BlockBreakingPass3ClientGameTest`

**Modified:**
- `BlockProfile` (provenance per field)
- `BlockProfileResolver`
- `BlockDurabilityDefinitions` (dataset registration; material id)
- `MiningTier` (Power exclusion rule)
- `PlayerMiningManager` (server guards)
- `ClientMiningController` (client guard)
- `MiningVerification` (live checks and coverage report)
- `BlockProfileResolverTest`
- gametest `fabric.mod.json`

**Repository safety:**
- **No reset, clean, stash, branch switch, commit, stage or push.**
- **`git status`:** identical to the pre-Pass-3 snapshot except for the two new untracked source files. The report and the two CSV copies in `Context/Audit/` are added docs.
- **Runs happened only in the disposable `build/verification-run` and `build/run/clientGameTest`.**
