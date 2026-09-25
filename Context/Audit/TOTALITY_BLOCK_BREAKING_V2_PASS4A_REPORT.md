# TOTALITY — Block Breaking V2, Pass 4A
## Production Transformation Integration

**Date:** 2026-09-25 · **Target:** Minecraft 26.2 / Fabric / Java 25 · **Branch/HEAD:** `master` / `7213407` (unchanged; nothing committed, staged or pushed)

**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_BLOCK_BREAKING_V2_PASS4A_REVIEW.zip`

**Baseline:** `src/` was snapshotted before editing. `TASK_ONLY.patch` is computed against it, and applying it reproduces the current `src/` exactly (verified with `diff -r`).

---

## 1. Core invariant and mechanism

- **The invariant:** a legitimate in-place physical transformation keeps the remaining Integrity percentage. Any removal or replacement starts intact, including a same-id or authored-pair re-placement.
- **No lazy block-id matching.** It stays removed, and the lazy read path still has no transformation input (source guard unchanged).

### The transaction: `BlockDamageStorage.transaction(level, pos, mutation)`
It generalises the follow-up's `transformBlock`, which now delegates to it. It wraps **exactly one vanilla mutation, run unchanged**, with its own flags, sounds, particles, advancements, item use, wear and drops:
1. **Before:** it snapshots the records on the positions that mutation can carry along, together with the block standing on each. Those positions are:
   - the position itself;
   - its Integrity owner and the owner's partner (door halves, bed, extended piston);
   - a connected **double Copper Chest** half. Vanilla converts that partner itself through `CopperChestBlock.updateShape` inside the same `setBlock`, and each half is its own record.

   Owner and partner lookups read neighbours only if those chunks are already loaded; nothing is loaded to reconcile.
2. **During:** the removal hook is suppressed for **exactly those positions**. Nested changes anywhere else behave normally.
3. **After:** each snapshotted position is reconciled.
   - **Same block:** nothing happens (an ordinary state change).
   - **Different block:** the record migrates by percentage only if it belonged to the block that stood there **and** `old → new` is a registered pair. Anything else deletes it, like a removal.
   - **Nothing changed** (rejected, failed, predicate false): every record is untouched.
   - **No records on those positions:** the mutation simply runs, so the common path costs nothing.
- **Why this is not id inference:** only the change produced inside one bracketed, authorised vanilla call is ever evaluated, and only against explicitly registered pairs.
- **Why suppression can't keep a record by accident:** every suppressed position is reconciled afterwards.

### Authorised pairs: `VanillaTransformations`
Taken from vanilla's own tables, `minecraft` ids only:
- `AxeItem.STRIPPABLES`, via an accessor: Overworld and Nether stripping;
- `WeatheringCopper.NEXT_BY_BLOCK` / `PREVIOUS_BY_BLOCK`: oxidation and scraping for **every** copper form;
- `HoneycombItem.WAXABLES` / `WAX_OFF_BY_BLOCK`;
- `ShovelItem.FLATTENABLES`, via an accessor;
- `HoeItem.TILLABLES`: its targets sit inside opaque lambdas, so they are transcribed from the verified 26.2 definition (grass/path/dirt → farmland, coarse → dirt, rooted → dirt);
- `FarmlandBlock.turnToDirt`: farmland and path → dirt.

More than 100 pairs are registered. Pass 4B families are not.

---

## 2. Integrated paths (each vanilla call site inspected first)

| Transformation | Vanilla mutation path (26.2) | Hook |
|---|---|---|
| Wood stripping (Overworld + Nether) | `AxeItem.useOn` → `evaluateNewBlockState` → `level.setBlock(pos, s, 11)`; the axis is copied by vanilla | `ItemUseTransformationMixin` (`@WrapMethod useOn`) |
| Copper axe scraping; axe wax removal | the same `AxeItem.useOn` | same |
| Honeycomb waxing (hand) | `HoneycombItem.useOn` → `setBlock(pos, waxed, 11)`; advancement trigger and item shrink kept | same |
| Honeycomb waxing (dispenser) | the anonymous `OptionalDispenseItemBehavior` for `Items.HONEYCOMB` (`DispenseItemBehavior$12`, the only one referencing HoneycombItem) → `setBlockAndUpdate` | `DispenserHoneycombTransformationMixin` (`@WrapMethod execute`) |
| Random-tick oxidation | all **13** `WeatheringCopper` classes (the only `ChangeOverTimeBlock`) → `randomTick` → `ChangeOverTimeBlock.changeOverTime` → `setBlockAndUpdate`. Doors tick from the lower half (the owner); a double chest ticks from its non-RIGHT half. | `WeatheringTransformationMixin` (`@WrapMethod randomTick`) |
| Soil cultivation (Hoe) | `HoeItem.useOn` → TILLABLES consumer → `setBlock(pos, s, 11)`; Rooted Dirt's Hanging Roots drop is kept | `ItemUseTransformationMixin` |
| Soil flattening (Shovel) | `ShovelItem.useOn` → `setBlock(pos, path, 11)`; the campfire dowse is a same-block change and unaffected | same |
| Soil reversion | `FarmlandBlock.turnToDirt` (static): trampling, drying, and a covered Farmland or Dirt Path | `FarmlandReversionMixin` (`@WrapMethod turnToDirt`) |
| Farmland moisture, slab stacking, snow layers | same-id state changes | no transaction needed; preserved as before |

**Every accepted copper form is covered** through vanilla's tables and the same code paths: blocks, cut, chiseled, stairs, slabs, doors, trapdoors, grates, bulbs, lightning rods, lanterns, bars, chains, chests and golem statues.

**Vanilla already preserves Copper Chest inventories and block entities** (`shouldChangedStateKeepBlockEntity`). The transaction never touches them. Golem Statue pose and facing are copied by vanilla (`withPropertiesOf`).

---

## 3. Paths not integrated (reported, not approximated)

- **Lightning strike deoxidation** (`LightningBolt.clearCopperOnLightningStrike` → `WeatheringCopper.getFirst`). Not one of the four accepted copper paths in the brief. **Current behaviour:** the struck copper starts intact (removal hook). It can be wrapped the same way if accepted.
- **Grass / Mycelium spread and decay** (`SpreadingSnowyDirtBlock` random ticks). Not cultivation, flattening or reversion. **Current behaviour:** intact.
- **Unaffected Golem Statue + Axe → Copper Golem entity.** A genuine removal (`removeBlock`), so it correctly starts nothing.
- **Mod-added strippables/flattenables** (Fabric registries). Not registered (vanilla ids only), so they behave as replacements.
- **Robustness note:** the dispenser hook targets the anonymous class `DispenseItemBehavior$12` by name. It is verified live for 26.2 and would fail loudly at start-up (`defaultRequire = 1`) if a future version renumbers it.

**No path was found that could not be integrated without changing vanilla behaviour.**

---

## 4. Verification

| Suite | Result |
|---|---|
| Build + JUnit (`build --rerun-tasks`, 7/7 executed) | **1,987 tests, 0 failures** (+3) |
| Live, disposable `runVerificationServer`, existing gate, **stopped gracefully** (`stop` via stdin, Gradle exit 0) | **every suite passed**; MiningVerification **349/349** (+18); coverage unchanged: 844 / 333 / 15 / 6 / 28 / 0 |
| Client, disposable `runClientGameTest`, real player and world | TooltipV2Pass1 162/0, Enchantments 47/0, Pass2 11/0, Pass3 23/0, **Pass4A 5/0** |
| Mutation (transaction disabled) | **exactly the 18 transformation-dependent live checks fail**, including the two follow-up `transformBlock` checks. The negative checks (fresh replacement intact) still pass. The file was restored byte-identical. |

**Live checks (vanilla entry points: `ItemStack.useOn` with a fake player, `BlockState.randomTick`, dispenser `tick`, `FarmlandBlock.turnToDirt`):**
- **Stripping:** Oak Log 60/100 → Stripped 60/100 (axis X kept, Axe wear 1); Crimson Stem 75/100 → Stripped 75/100.
- **Copper scrape, wax, wax-off:** 100/150 kept throughout, with 1 Honeycomb consumed and one Axe wear per use. Re-waxing a waxed block is rejected (PASS): no item used, record unchanged.
- **Oxidation:** Copper Block 100/150 → Exposed Copper 100/150.
- **Copper Chest:** waxing keeps 100/150 and the inventory (5 diamonds, same slot, total count exact: no duplication or loss).
- **Double Copper Chest:** waxing one half converts both; the records stay independent (120 and 90 of 150), and both inventories are intact.
- **Golem Statue:** oxidation keeps 100/150, pose, facing and the block entity.
- **Copper door:** the lower-half owner's 70/100 migrates when the door oxidises.
- **Dispenser waxing:** 100/150, with one Honeycomb consumed.
- **Soil:**
  - Dirt 60/100 → Farmland (Hoe wear 1) → a moisture change keeps the record → reverted to Dirt 60/100;
  - tilling under a block is rejected: nothing changes and there is no wear;
  - Grass → Path 80/100 → a covered Path → Dirt 80/100;
  - Coarse → Dirt 90/100; Rooted → Dirt 70/100.
- **Differing maximum** through the same transaction: 60/100 → 90/150.
- **Failed transformation** (vanilla refuses the change): the block and record are unchanged.
- **Fresh replacement:** a removed log plus a newly placed Stripped Log (an authored pair) starts intact.
- **Save/reload after transformation:** Waxed Copper 100/150 survives a real save/reload and re-attaches only to the new block.

**Client, in a real world:**
- **Stripping:** Birch stripped at 70/100, Z axis, wear 1.
- **Cut Copper waxed then unwaxed:** 100/150, and the **Wax On and Wax Off advancements are granted**.
- **Double Copper Chest waxed from its RIGHT half:** 135/150 and 105/150; inventories intact; **0 loose items**.
- **Rooted Dirt tilled:** 80/100, **Hanging Roots dropped**, wear 1.
- **Authored-pair re-placement:** intact.

**JUnit:**
- `Pass4ATransformationSourceRegressionTest` (3): mixin registration, whole-method wrapping, vanilla-table pairs, no Pass 4B families.
- `StorageTransformationSourceRegressionTest` and `BlockRemovalHookSourceRegressionTest` were updated to the transaction structure. Their intent is kept: only the transaction consults pairs; the lazy path has no transformation input; suppression brackets exactly the mutation.

---

## 5. Scope kept
- **Not done:** Concrete, Anvil, Sponge, Coral or Cauldron integration (Pass 4B); any dataset change; any extraordinary destruction or fictional-material change; any change to the four inert hardness-0 profiles.
- **Repository safety:**
  - no commit, stage, reset, clean, stash, branch switch or push;
  - `git status`: the only new entries are the untracked Pass 4A source files;
  - disposable run directories only.

## 6. Changed files (task-only; `CHANGED_FILES.txt`, 16 files)
**New:**
- `api/mining/VanillaTransformations.java`
- `mixin/mining/AxeItemAccessor`, `ShovelItemAccessor`
- `mixin/mining/ItemUseTransformationMixin`, `WeatheringTransformationMixin`, `FarmlandReversionMixin`, `DispenserHoneycombTransformationMixin`
- `Pass4ATransformationSourceRegressionTest`
- `BlockBreakingPass4AClientGameTest`

**Modified:**
- `BlockDamageStorage` (the transaction)
- `BlockDurabilityDefinitions` (pair registration)
- `MiningVerification` (live checks)
- `totality.mixins.json`
- gametest `fabric.mod.json`
- two source guards

## 7. Manual in-game checks
- **Visible cracks** on a damaged log or copper block after stripping, waxing or weathering, in real play.
- **Tilling and flattening** damaged soil by hand.
- **Trampling** damaged Farmland.
- **A dispenser** waxing a damaged copper block.
- **Double Copper Chest** open/close sounds after waxing.
