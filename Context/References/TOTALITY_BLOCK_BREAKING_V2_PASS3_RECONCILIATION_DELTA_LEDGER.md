# TOTALITY — Block Breaking V2: Pass 3 Reconciliation Delta

**Status:** accepted design handoff; **not yet implemented**.
**Target:** Minecraft 26.2 / Fabric / Java 25.
**Basis:** the originally uploaded Pass 3 `TOTALITY_BLOCK_BREAKING_V2_PASS3_FALLBACK_AND_UNRESOLVED.csv` and the explicit accepted decisions in this design conversation. This file changes only the **176 `minecraft:` rows** originally marked `UNRESOLVED_DESIGN`. The accompanying CSV is the canonical exact-ID mapping.

## 1. Coverage accounting

- Original live snapshot: 1,226 registry blocks = 1,196 vanilla + 30 Totality. Pass 3 had 176 vanilla `UNRESOLVED_DESIGN` and 28 Totality `COMPAT_FALLBACK`.
- The 176 IDs in the original unresolved inventory have **176 unique decisions**, represented exactly once in the companion CSV: **147 ORDINARY** and **29 SPECIAL**. These figures are **planned classification deltas**, not a fresh runtime verification.
- Do **not** equate full design resolution with zero actual runtime fallback until the code and refreshed coverage CSV verify it. The 28 deferred Totality entries remain separate. Ruby’s two already-authored provisional ore values remain unchanged.
- Note count correction: the original sulfur group has 15 entries: 12 structural, one Potent Sulfur, one Sulfur Spike, plus chiseled? The exact CSV is authoritative; do not use conversational progress counters as an implementation checklist.

## 2. Source-of-truth and resolution rules

1. Apply the original accepted vanilla ledger, then this **later accepted delta** for the 176 originally unresolved IDs. For conflicts in historical suggestions or counters, the exact-ID delta and latest explicit user decisions govern.
2. The CSV is keyed by `registry_id`. Every ID must exist in the actual runtime registry. Fail/report missing IDs rather than silently dropping them.
3. For `ORDINARY`, `new_default_hp` is the authored maximum for the default state. For authored slabs, additionally use the explicitly specified **double** maximum in `notes`. No automatic geometric formulas.
4. `VANILLA` in `effective_tool` means **keep the preexisting vanilla effective-tool tags/behavior**, not a new custom tool override. `NEUTRAL` means explicitly no preferred tool. For SPECIAL, effective tool is vanilla-authoritative.
5. Keep every Required Mining Tier vanilla-derived: no custom overrides in this delta. Keep terminal vanilla destruction, loot, contents, block entities, permissions, Silk Touch/Fortune and special interactions intact.
6. Material identities are names for grouping and separation; the `material_identity` strings in the CSV are descriptive handoff identifiers, **not mandatory literal Java resource IDs**. Map to consistent actual profile IDs used by the repo.
7. The `ownership` column uses existing semantics. Ordinary entries not marked otherwise are per-position; Nether doors share their lower-half owner. A Copper Chest remains independent per position, not a shared double-chest pool.
8. A SPECIAL profile is explicitly vanilla-owned and has no finite HP; SPECIAL does not automatically force instant breaking. It must not show a numerical Block Durability row.

## 3. Exact accepted entries by group

All registry IDs below are written without the common `minecraft:` prefix for readability. The companion CSV includes full IDs, baseline values, category and explicit new fields.

### Copper utilities (21 registry IDs)
- **ORDINARY**, **75 HP**, tool **VANILLA**, material `copper_utility`, form `lantern`: `exposed_copper_lantern`, `weathered_copper_lantern`, `oxidized_copper_lantern`, `waxed_copper_lantern`, `waxed_exposed_copper_lantern`, `waxed_weathered_copper_lantern`, `waxed_oxidized_copper_lantern`.
  - All oxidized/waxed forms use base Lantern value.
- **ORDINARY**, **150 HP**, tool **VANILLA**, material `copper_utility`, form `bulb`: `exposed_copper_bulb`, `weathered_copper_bulb`, `oxidized_copper_bulb`, `waxed_copper_bulb`, `waxed_exposed_copper_bulb`, `waxed_weathered_copper_bulb`, `waxed_oxidized_copper_bulb`.
  - All oxidized/waxed forms use base Bulb value; preserve block states and copper transformations.
- **ORDINARY**, **75 HP**, tool **VANILLA**, material `copper_utility`, form `rod`: `exposed_lightning_rod`, `weathered_lightning_rod`, `oxidized_lightning_rod`, `waxed_lightning_rod`, `waxed_exposed_lightning_rod`, `waxed_weathered_lightning_rod`, `waxed_oxidized_lightning_rod`.
  - All oxidized/waxed forms use base Lightning Rod value.

### Nether structural wood (22 registry IDs)
- **ORDINARY**, **100 HP**, tool **AXE**, material `nether_wood`, form `full`: `warped_stem`, `stripped_warped_stem`, `warped_hyphae`, `stripped_warped_hyphae`, `crimson_stem`, `stripped_crimson_stem`, `crimson_hyphae`, `stripped_crimson_hyphae`, `crimson_planks`, `warped_planks`.
  - Separate from Overworld Wood; do not change fire resistance.
- **ORDINARY**, **50 HP**, tool **AXE**, material `nether_wood`, form `slab`: `crimson_slab`, `warped_slab`.
  - Double slab 100; percentage-preserving same-block state change.
- **ORDINARY**, **50 HP**, tool **AXE**, material `nether_wood`, form `fence_or_trapdoor`: `crimson_fence`, `warped_fence`, `crimson_trapdoor`, `warped_trapdoor`.
- **ORDINARY**, **75 HP**, tool **AXE**, material `nether_wood`, form `stairs_or_gate`: `crimson_fence_gate`, `warped_fence_gate`, `crimson_stairs`, `warped_stairs`.
- **ORDINARY**, **100 HP**, tool **AXE**, material `nether_wood`, form `door`, owner `DOOR_LOWER_HALF`: `crimson_door`, `warped_door`.
  - Shared across two door halves.

### Sandstone walls (2 registry IDs)
- **ORDINARY**, **75 HP**, tool **PICKAXE**, material `sandstone`, form `wall`: `red_sandstone_wall`, `sandstone_wall`.

### Soil (6 registry IDs)
- **ORDINARY**, **100 HP**, tool **SHOVEL**, material `soil`, form `full`: `coarse_dirt`, `podzol`, `farmland`, `mycelium`, `dirt_path`, `rooted_dirt`.
  - In-place cultivation/reversion preserves percentage only via event-safe transaction; farmland moisture same-ID change.

### Archaeology (2 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `suspicious_sand`, `suspicious_gravel`.
  - Brushing, archaeology block entity and loot vanilla-authoritative.

### Raw resin (2 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `resin_clump`, `resin_block`.
  - Raw zero-hardness forms, vanilla-owned.

### Resin brick (5 registry IDs)
- **ORDINARY**, **150 HP**, tool **PICKAXE**, material `resin_brick`, form `full`: `resin_bricks`, `chiseled_resin_bricks`.
- **ORDINARY**, **115 HP**, tool **PICKAXE**, material `resin_brick`, form `stairs_or_wall`: `resin_brick_stairs`, `resin_brick_wall`.
- **ORDINARY**, **75 HP**, tool **PICKAXE**, material `resin_brick`, form `slab`: `resin_brick_slab`.
  - Double slab 150, percentage preservation.

### Sulfur natural/polished (8 registry IDs)
- **ORDINARY**, **100 HP**, tool **PICKAXE**, material `sulfur_natural`, form `full`: `sulfur`, `polished_sulfur`.
- **ORDINARY**, **50 HP**, tool **PICKAXE**, material `sulfur_natural`, form `slab`: `sulfur_slab`, `polished_sulfur_slab`.
  - Double slab 100.
- **ORDINARY**, **75 HP**, tool **PICKAXE**, material `sulfur_natural`, form `stairs_or_wall`: `sulfur_stairs`, `sulfur_wall`, `polished_sulfur_stairs`, `polished_sulfur_wall`.

### Sulfur bricks (5 registry IDs)
- **ORDINARY**, **150 HP**, tool **PICKAXE**, material `sulfur_brick`, form `full`: `sulfur_bricks`, `chiseled_sulfur`.
- **ORDINARY**, **75 HP**, tool **PICKAXE**, material `sulfur_brick`, form `slab`: `sulfur_brick_slab`.
  - Double slab 150.
- **ORDINARY**, **115 HP**, tool **PICKAXE**, material `sulfur_brick`, form `stairs_or_wall`: `sulfur_brick_stairs`, `sulfur_brick_wall`.

### Sulfur exception (2 registry IDs)
- **ORDINARY**, **100 HP**, tool **PICKAXE**, material `potent_sulfur`, form `full`: `potent_sulfur`.
  - Preserve block entity and states.
- **SPECIAL**, no finite HP, vanilla-owned: `sulfur_spike`.
  - Preserve environmental and shape behavior.

### Cinnabar natural/polished (8 registry IDs)
- **ORDINARY**, **100 HP**, tool **PICKAXE**, material `cinnabar_natural`, form `full`: `cinnabar`, `polished_cinnabar`.
- **ORDINARY**, **50 HP**, tool **PICKAXE**, material `cinnabar_natural`, form `slab`: `cinnabar_slab`, `polished_cinnabar_slab`.
  - Double slab 100.
- **ORDINARY**, **75 HP**, tool **PICKAXE**, material `cinnabar_natural`, form `stairs_or_wall`: `cinnabar_stairs`, `cinnabar_wall`, `polished_cinnabar_stairs`, `polished_cinnabar_wall`.

### Cinnabar bricks (5 registry IDs)
- **ORDINARY**, **150 HP**, tool **PICKAXE**, material `cinnabar_brick`, form `full`: `cinnabar_bricks`, `chiseled_cinnabar`.
- **ORDINARY**, **75 HP**, tool **PICKAXE**, material `cinnabar_brick`, form `slab`: `cinnabar_brick_slab`.
  - Double slab 150.
- **ORDINARY**, **115 HP**, tool **PICKAXE**, material `cinnabar_brick`, form `stairs_or_wall`: `cinnabar_brick_stairs`, `cinnabar_brick_wall`.

### Wooden shelves (12 registry IDs)
- **ORDINARY**, **100 HP**, tool **AXE**, material `species_wood`, form `functional`: `acacia_shelf`, `bamboo_shelf`, `birch_shelf`, `cherry_shelf`, `crimson_shelf`, `dark_oak_shelf`, `jungle_shelf`, `mangrove_shelf`, `oak_shelf`, `pale_oak_shelf`, `spruce_shelf`, `warped_shelf`.
  - Independent Integrity, preserve item contents and block entity; Crimson/Warped use nether_wood identity.

### Copper bars (8 registry IDs)
- **ORDINARY**, **100 HP**, tool **PICKAXE**, material `copper_bars`, form `functional`: `copper_bars`, `exposed_copper_bars`, `weathered_copper_bars`, `oxidized_copper_bars`, `waxed_copper_bars`, `waxed_exposed_copper_bars`, `waxed_weathered_copper_bars`, `waxed_oxidized_copper_bars`.
  - Eight wax/oxidation variants. Preserve damage through event-safe weathering/waxing/scraping. 

### Copper chains (8 registry IDs)
- **ORDINARY**, **50 HP**, tool **PICKAXE**, material `copper_chain`, form `functional`: `copper_chain`, `exposed_copper_chain`, `weathered_copper_chain`, `oxidized_copper_chain`, `waxed_copper_chain`, `waxed_exposed_copper_chain`, `waxed_weathered_copper_chain`, `waxed_oxidized_copper_chain`.
  - Eight wax/oxidation variants. Preserve damage through event-safe weathering/waxing/scraping. 

### Copper chests (8 registry IDs)
- **ORDINARY**, **150 HP**, tool **PICKAXE**, material `copper_chest`, form `functional`: `copper_chest`, `exposed_copper_chest`, `weathered_copper_chest`, `oxidized_copper_chest`, `waxed_copper_chest`, `waxed_exposed_copper_chest`, `waxed_weathered_copper_chest`, `waxed_oxidized_copper_chest`.
  - Eight wax/oxidation variants. Preserve damage through event-safe weathering/waxing/scraping. Preserve inventory and loot.

### Copper golem statues (8 registry IDs)
- **ORDINARY**, **150 HP**, tool **PICKAXE**, material `copper_golem_statue`, form `functional`: `copper_golem_statue`, `exposed_copper_golem_statue`, `weathered_copper_golem_statue`, `oxidized_copper_golem_statue`, `waxed_copper_golem_statue`, `waxed_exposed_copper_golem_statue`, `waxed_weathered_copper_golem_statue`, `waxed_oxidized_copper_golem_statue`.
  - Eight wax/oxidation variants. Preserve damage through event-safe weathering/waxing/scraping. Preserve statue poses.

### Mob heads and skulls (14 registry IDs)
- **ORDINARY**, **50 HP**, tool **NEUTRAL**, material `mob_head`, form `standing_or_wall`: `skeleton_skull`, `skeleton_wall_skull`, `wither_skeleton_skull`, `wither_skeleton_wall_skull`, `zombie_head`, `zombie_wall_head`, `player_head`, `player_wall_head`, `creeper_head`, `creeper_wall_head`, `dragon_head`, `dragon_wall_head`, `piglin_head`, `piglin_wall_head`.
  - Preserve block entity data, head skins, redstone/animation, summoning, and vanilla drops.

### Copper torch (2 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `copper_torch`, `copper_wall_torch`.
  - Do not inherit Lantern value.

### Crafter (1 registry IDs)
- **ORDINARY**, **150 HP**, tool **PICKAXE**, material `mechanism`, form `full`: `crafter`.
  - Preserve inventories, disabled slots and redstone; ordinary state changes retain damage.

### Heavy core (1 registry IDs)
- **ORDINARY**, **600 HP**, tool **PICKAXE**, material `heavy_core`, form `full`: `heavy_core`.
  - Independent; keep vanilla Mace use and loot.

### Creaking heart (1 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `creaking_heart`.
  - Preserve Creaking linkage, activation, Silk Touch and loot.

### Zero-hardness flora/technical (12 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `fire`, `soul_fire`, `cactus_flower`, `structure_void`, `dried_ghast`, `sea_pickle`, `spore_blossom`, `leaf_litter`, `small_dripleaf`, `hanging_roots`, `pale_hanging_moss`, `firefly_bush`.
  - Explicit special, retain vanilla timing and behavior.

### Moss (1 registry IDs)
- **ORDINARY**, **50 HP**, tool **HOE**, material `moss`, form `full`: `pale_moss_block`.

### Moss carpets (2 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `moss_carpet`, `pale_moss_carpet`.
  - Vanilla-owned thin growth; no 10-HP profile.

### Mangrove roots (1 registry IDs)
- **ORDINARY**, **50 HP**, tool **AXE**, material `mangrove_roots`, form `full`: `mangrove_roots`.

### Muddy mangrove roots (1 registry IDs)
- **ORDINARY**, **100 HP**, tool **SHOVEL**, material `muddy_mangrove_roots`, form `full`: `muddy_mangrove_roots`.

### Interaction-specific (2 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `cake`, `glow_lichen`.
  - Preserve eating or face attachment/waterlogging/shears.

### Petrified slab (1 registry IDs)
- **ORDINARY**, **50 HP**, tool **PICKAXE**, material `petrified_wood`, form `slab`: `petrified_oak_slab`.
  - Double slab 100. Not ordinary wooden tool behavior.

### Chorus (2 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `chorus_plant`, `chorus_flower`.
  - Preserve growth, support, cascading destruction, loot.

### Frosted ice (1 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `frosted_ice`.
  - Preserve age, melt, and Frost Walker behavior.

### Dripleaf (2 registry IDs)
- **SPECIAL**, no finite HP, vanilla-owned: `big_dripleaf`, `big_dripleaf_stem`.
  - Preserve tilt, projectiles, growth, support, waterlogging, drops.

## 4. Implementation boundaries and lifecycle

**Transformations:** A registered `from → to` pair alone is insufficient. The Pass 1 follow-up established `BlockDamageStorage.transformBlock(pos, newState, flags)`, and Pass 2 added a block-mutation removal hook. Only validated **in-place event transactions** may carry proportional Integrity to a new ID; lazy reconciliation must always discard mismatched IDs. A destroyed/replaced block—including a newly placed block with the **same** ID—starts intact.

Integrate these approved families, verifying vanilla call sites and data behavior individually:

- Copper oxidation, waxing and scraping for all accepted copper forms. Preserve percent even where maximum HP is unchanged.
- Nether and ordinary wood stripping; do not assume registration alone intercepts vanilla `setBlock`.
- Soil cultivation/flattening/reversion; keep Farmland moisture changes as ordinary same-ID preservation.
- Other already accepted transformations from the original ledger (concrete setting, anvil degradation, sponge and coral changes, cauldron contents) remain in scope of the **separate transformation pass**. Do not silently claim they are wired by the registry delta.
- Slab stacking and Snow layering use same-ID profile maximum changes; preserve remaining Integrity percentage. The Petrified Oak Slab single/double pair is 50/100.

**Other Pass 3 findings requiring separate closure, not additions to the 176-ID delta:**

- Repeater, Comparator, End Rod and Scaffolding have accepted finite-HP rows but hardness zero currently leaves them vanilla-owned; report them as INERT. Do not claim their HP is operative until ownership is explicitly designed.
- The tooltip’s wrapped “No preferred tool” row needs a presentation check, not a change to its semantic value.
- Verification-server shutdown must distinguish all checks passing from Gradle exit 143; obtain an unambiguous final run before milestone closure.
- Manual in-game mining cadence and animation, specialized Cobweb behavior, Gold wear, slab/snow state changes, shared cracks, Copper Chest inventory, heads and statue mechanics remain on the final checklist.
- Sword/Shears are excluded from Power Mining; bare hands retain Power. This was implemented in Pass 3, so do not re-implement.
- Keep the 28 deferred Totality blocks and extraordinary destructive systems for Totality Core; no fictional-material rebalance.

## 5. Pass 3 reconciliation implementation acceptance

A controlled delta pass should:

1. Snapshot current `src/` and inspect the working tree; preserve all prior uncommitted changes and existing test artifacts.
2. Register only the exact entries and state-dependent slab maxima in the companion CSV. Use reusable families only when membership resolves **exactly** to the intended IDs; verify no tag spillover.
3. Check every CSV row’s resolved classification, HP/state values, tools, tiers and ownership against the actual live 26.2 registry; Tooltip V2 and mining must share the resolved profile.
4. Regenerate coverage CSVs, compare original vs post-delta by ID, and prove 176/176 changed to authored/explicit SPECIAL as intended, with **no remaining vanilla UNRESOLVED_DESIGN** in this captured registry, or report discrepancies honestly.
5. Add tests for data families, explicit SPECIAL, state-dependent doubles, owner records, vanilla interactions and inventories, and the existing Power exclusion. Test state-event integration separately when implemented.
6. Run rerun-task build, JUnit, disposable gated live checks and client GameTests. Document manual-only gaps, runtime gate and shutdown behavior.
7. Deliver Markdown report, full coverage CSV, unresolved/fallback CSV, task-only patch and review ZIP. Do **not** commit, stage or push during the intermediate pass. Final V2 commit/push follows full review and manual checks.

## 6. Provenance note

This is a faithful consolidation of decisions accepted in the conversation, not an independent verification of the physical material properties. The exact member list is taken from the uploaded Pass 3 unresolved CSV. Do not infer additional blocks from name similarity or from a new Minecraft registry release; flag any new IDs for explicit review.

