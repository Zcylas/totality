# TOTALITY — Block Breaking V2, Pass 4B
## Remaining Physical Transformations and Integrity Completion

**Date:** 2026-09-25 · **Target:** Minecraft 26.2 / Fabric / Java 25 · **Branch/HEAD:** `master` / `7213407` (unchanged; nothing committed, staged or pushed)

**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_BLOCK_BREAKING_V2_PASS4B_REVIEW.zip`

**Baseline:** `src/` was snapshotted before editing. `TASK_ONLY.patch` is computed against it, and applying it reproduces the current `src/` exactly (verified with `diff -r`).

**No new pipeline.** Every hook uses the existing Pass 4A `BlockDamageStorage.transaction`. The lazy id path still has no transformation input. The removal hook still clears unrelated and same-id replacements.

---

## 1. Transformation coverage

| Accepted transformation | Status | Vanilla path (inspected) | Hook |
|---|---|---|---|
| Wood stripping (Overworld + Nether) | **Integrated (4A)** | `AxeItem.useOn` | `ItemUseTransformationMixin` |
| Copper axe scraping; wax removal | **Integrated (4A)** | `AxeItem.useOn` | same |
| Copper waxing (hand; dispenser) | **Integrated (4A)** | `HoneycombItem.useOn`; `DispenseItemBehavior$12` | same; `DispenserHoneycombTransformationMixin` |
| Copper random-tick oxidation | **Integrated (4A)** | 13 `WeatheringCopper` `randomTick` → `changeOverTime` | `WeatheringTransformationMixin` |
| **Copper lightning deoxidation** | **Integrated (4B)** | `LightningBolt.clearCopperOnLightningStrike` (struck block → `getFirst`) and `lambda$randomStepCleaningCopper$0` (nearby copper, one step each) | `EnvironmentalTransformationMixin`: one transaction **per changed position** |
| Soil tilling / flattening / Farmland-Path reversion | **Integrated (4A)** | `HoeItem.useOn`, `ShovelItem.useOn`, `FarmlandBlock.turnToDirt` | 4A mixins |
| **Grass / Mycelium decay to Dirt** | **Integrated (4B)** | `SpreadingSnowyBlock.randomTick` (covered block → base block) | `GrassSpreadTransformationMixin` |
| **Grass / Mycelium spread onto Dirt** | **Integrated (4B)** | same `randomTick`, `setBlockAndUpdate(testPos, …)` | the same mixin: the **target** Dirt keeps its own record; nothing moves from the source |
| **Concrete Powder → Concrete** (16 colours) | **Integrated (4B)** | an *existing* powder: `ConcretePowderBlock.updateShape` (water touches it), applied by `Block.updateOrDestroy` from `NeighborUpdater` | `ConcreteSolidificationMixin` (`@WrapMethod Block.updateOrDestroy`, gated on the old block being Concrete Powder) |
| Concrete placed beside water; falling powder landing in water | **Fresh, not a transformation** | `getStateForPlacement`; `onLand` at the **landing** position. The original block was removed when it started to fall, and the removal hook deleted its record. | not wrapped (by design) |
| **Anvil use degradation** (400 → 300 → 200 → broken) | **Integrated (4B)** | `AnvilMenu.onTake` → `AnvilBlock.damage` → `setBlock(pos, next, 2)`, or `removeBlock` (terminal) | `AnvilDegradationMixin` (at the menu's own position via `ItemCombinerMenuAccessor`) |
| Falling Anvil degradation | **Removal plus fresh placement, not in place** | `FallingBlockEntity` degrades the carried state and lands elsewhere; the original block was removed at fall start | not wrapped (by design) |
| **Sponge → Wet Sponge** | **Integrated (4B)** | `SpongeBlock.tryAbsorbWater`, from `neighborChanged` (existing) and `onPlace` (fresh, no record) | `SpongeAbsorptionMixin` (at the Sponge's position; water removed elsewhere is untouched) |
| Wet Sponge → Sponge | **No existing-block path in 26.2** | only `WetSpongeBlock.onPlace` (drying as it is *placed* in an ultrawarm dimension), so the block is fresh | nothing to integrate; reported |
| **Living → dead full Coral Block** | **Integrated (4B)** | `CoralBlock.tick` (vanilla's own scheduled survival check) | `CoralDeathMixin`; small coral and fans stay SPECIAL and untouched |
| **Cauldron contents: player/item** | **Integrated (4B)** | `AbstractCauldronBlock.useItemOn` → `CauldronInteraction` lambdas (buckets, bottles, powder snow; the water↔lava swaps included) | `CauldronContentsMixin` |
| **Cauldron: rain/snow; dripstone** | **Integrated (4B)** | `CauldronBlock.handlePrecipitation`; `CauldronBlock.receiveStalactiteDrip` | `CauldronFillingMixin` |
| **Cauldron: last level emptied** | **Integrated (4B)** | `LayeredCauldronBlock.lowerFillLevel` (bottles, extinguishing burning entities) | `LayeredCauldronEmptyingMixin` |
| Farmland moisture; Cauldron fill level; slab stacking; snow layers | **Same-id state handling** | a same block with a new state | none needed; preserved (verified) |
| Water bottle on Dirt → Mud | **Not accepted; deferred** | `PotionItem` | not wrapped; a changed block starts intact |

**Pairs** (`VanillaTransformations`, vanilla ids only):
- 16 Concrete Powder → Concrete;
- Anvil → Chipped → Damaged;
- Sponge → Wet Sponge;
- 5 living → dead Coral Blocks;
- all 12 transitions among the 4 Cauldrons;
- weathered copper → `getFirst` (lightning);
- Grass/Mycelium ↔ Dirt.

**Nested-transaction safety (a bug found while building 4B, fixed before any run).** A Cauldron interaction can call `lowerFillLevel`, which is itself wrapped. The inner transaction migrates the record, so the outer one would have seen a record that no longer matched its snapshot and deleted it. The outer reconcile now skips a record already migrated to the block standing there (`e.block == after`). The "3 bottles lower it to empty, still 120/150" check exercises exactly this path.

---

## 2. Pass 4A hardening (chunk boundaries)

- **The flaw:** `tiedPositions` returned only the position itself whenever any chunk in the surrounding 3×3 was unloaded. A loaded Door owner, Bed owner or connected Copper Chest half could be excluded just because an unrelated neighbouring chunk was unloaded.
- **Now:** there is no broad check. `BlockProfiles.integrityOwner` / `integrityPartner` read each neighbour only if **its own** chunk is loaded (`LevelReader.hasChunkAt`), and nothing is ever loaded. The Copper Chest partner already used `isLoaded` for its own position.
- **Live tests:** three scenarios, each in **its own** far chunk loaded alone, with the "west neighbour chunk unloaded" precondition **asserted immediately before each check**:
  - a chunk-edge Copper Door waxed from its upper half keeps the loaded lower-half owner (60/100);
  - a chunk-edge double Copper Chest keeps both records (100 and 130 of 150);
  - a chunk-edge Bed ties its loaded head owner to the foot.
- **Mutation:** with the old broad check reinstated, **all three fail** (3/368). The file was restored byte-identical.
- **An honest note on the first draft of this test.** It put the Chest scenario next to the Door scenario in the same chunk. A debug run showed that vanilla neighbour updates from the Door's waxing had *loaded* the west chunk, which voided the Chest's precondition, so that check could not discriminate. Each scenario now has its own chunk and its own precondition check. The debug log is in the bundle (`evidence/investigation-…`).

---

## 3. Verification

| Suite | Result |
|---|---|
| Build + JUnit (`build --rerun-tasks`, 7/7 executed) | **1,992 tests, 0 failures** (+5) |
| Live, disposable `runVerificationServer`, existing gate, **stopped gracefully via `stop` (Gradle exit 0)** | **every suite passed**; MiningVerification **368/368** (+19); coverage unchanged at 844 / 333 / 15 / 6 / 28 / 0 |
| Client, disposable `runClientGameTest`, real world and ticks | Pass1 162/0, Enchantments 47/0, Pass2 11/0, Pass3 23/0, Pass4A 5/0, **Pass4B 5/0** |
| Mutation (broad chunk check reinstated) | exactly the 3 boundary checks fail |

**Live checks (vanilla entry points):**
- **Concrete:** an existing Lime Powder at 45/75 meets water and becomes Lime Concrete at **90/150** (a differing maximum). It survives save/reload and re-attaches only to the new block. A damaged powder that starts **falling** leaves its position with its record deleted, and nothing follows the entity.
- **Anvil, through its real menu** (result-slot take → `AnvilMenu.onTake`): 300/400 → **225/300** → **150/200**; the terminal break deletes the record.
- **Sponge:** an existing Sponge at 30/50 absorbs water and becomes Wet Sponge at 30/50 (the water is gone). A freshly placed Wet Sponge has no record.
- **Coral:** with water, the tick changes nothing (rejected). Without water, Tube Coral Block 40/50 dies in place to Dead Tube Coral Block 40/50.
- **Cauldron:**
  - empty 120/150 + a Water Bucket → Water Cauldron 120/150, with the bucket returned;
  - two same-id bottle steps, then the last bottle empties it (the nested path): still 120/150;
  - a Lava Bucket in and a Bucket out keep 120/150 and return a Lava Bucket;
  - rain fills it (kept), `lowerFillLevel` empties it (kept), and a lava drip fills it (kept).
- **Lightning:** struck Oxidized Copper 90/150 resets to unaffected Copper 90/150.
- **Grass/Mycelium:**
  - covered Grass 80/100 and Mycelium 50/100 decay to Dirt, keeping their percentage;
  - Grass spreading onto a damaged Dirt converts the **target** with its own 70/100, and the source keeps its own 90/100.

**Client, in a real world with real ticks:**
- a **summoned lightning bolt** deoxidises damaged copper (90/150 kept);
- a full Coral Block dies from **vanilla's scheduled tick** after its water is removed (40/50 kept);
- a Sponge absorbs a placed water source (25/50 kept);
- Concrete solidifies from placed water (45/75 → 90/150);
- Cauldron use with a real player returns the bucket and gives 3 water bottles (120/150 kept).

**Screenshots** (real world): the deoxidised Copper Block and the solidified Lime Concrete, each still showing its crack overlay after transforming. The migrated record re-syncs the client crack.

**JUnit:**
- `Pass4BTransformationSourceRegressionTest` (5): every hook is a common mixin using the transaction; the concrete gate; per-position spread and lightning transactions; the boundary hardening (no broad check, per-neighbour loaded checks); nested safety; the accepted pairs only, with no wet → dry pair.
- The Pass 4A guard's "Pass 4B families are not registered" assertion was removed as intentionally superseded. The rest of that test is unchanged.

---

## 4. Findings and deferrals
- **Wet Sponge → Sponge** has no in-place path for an existing block in 26.2, so there was nothing to integrate.
- **Falling concrete and falling anvils** are removal plus fresh placement by design. No damage follows an entity to another position.
- **Dirt → Mud (water bottle)** is not an accepted transformation; left unwrapped, so the changed block starts intact.
- **No accepted path needed an unsafe approximation.**

## 5. Scope and safety
- **Not changed:** the durability dataset, the four inert hardness-0 profiles, fictional materials and extraordinary destruction systems.
- **Repository safety:**
  - no commit, stage, reset, clean, stash, branch switch or push;
  - `git status`: the only new entries are the untracked Pass 4B mixin files;
  - disposable run directories only.

## 6. Changed files (task-only; `CHANGED_FILES.txt`, 19 files)
**New mixins:**
- `ConcreteSolidificationMixin`, `ItemCombinerMenuAccessor`, `AnvilDegradationMixin`
- `SpongeAbsorptionMixin`, `CoralDeathMixin`
- `CauldronContentsMixin`, `CauldronFillingMixin`, `LayeredCauldronEmptyingMixin`
- `EnvironmentalTransformationMixin`, `GrassSpreadTransformationMixin`

**New tests:**
- `Pass4BTransformationSourceRegressionTest`
- `BlockBreakingPass4BClientGameTest`

**Modified:**
- `BlockDamageStorage` (hardening, nested safety, verification seam)
- `BlockProfiles` (per-neighbour loaded checks)
- `VanillaTransformations` (4B pairs)
- `MiningVerification` (4B and boundary live checks)
- `totality.mixins.json`
- gametest `fabric.mod.json`
- `Pass4ATransformationSourceRegressionTest`

## 7. Manual in-game checks
- **Anvil:** repairing on a damaged Anvil until it degrades; the crack stays.
- **Rain:** rain slowly filling a damaged Cauldron.
- **Dripstone:** a real dripstone setup filling a damaged Cauldron.
- **Lightning rod:** lightning striking a damaged Lightning Rod.
- **Grass spread:** grass spreading across a damaged dirt path in normal play.
