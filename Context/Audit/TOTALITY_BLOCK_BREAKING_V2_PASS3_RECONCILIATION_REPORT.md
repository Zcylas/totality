# TOTALITY — Block Breaking V2, Pass 3 Reconciliation
## Implementation of the 176-ID delta

**Date:** 2026-09-25 · **Target:** Minecraft 26.2 / Fabric / Java 25 · **Branch/HEAD:** `master` / `7213407` (unchanged; nothing committed, staged or pushed)

**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_BLOCK_BREAKING_V2_PASS3_RECONCILIATION_REVIEW.zip`

**Updated CSVs (also in the bundle):**
- `Context/Audit/TOTALITY_BLOCK_BREAKING_V2_PASS3R_BLOCK_PROFILE_COVERAGE.csv`: full registry, 1,226 rows.
- `Context/Audit/TOTALITY_BLOCK_BREAKING_V2_PASS3R_FALLBACK_AND_UNRESOLVED.csv`: now only the 28 deferred Totality rows.
- `Context/Audit/TOTALITY_BLOCK_BREAKING_V2_PASS3R_RECONCILIATION_BY_ID.csv`: the 176 original ids, before and after.

---

## 0. Inputs and method

- **Authority:**
  - `Context/References/TOTALITY_BLOCK_BREAKING_V2_PASS3_RECONCILIATION_DELTA_LEDGER.md`;
  - its canonical exact-id checklist, `…_RECONCILIATION_DELTA_176.csv`;
  - read together with the Pass 3 report and the original accepted vanilla ledger.
- **CSV integrity:** 176 unique `minecraft:` ids, 147 ORDINARY + 29 SPECIAL (asserted by JUnit). The id set is **identical** to the 176 ids Pass 3 reported `UNRESOLVED_DESIGN`.
- **Baseline:** `src/` was snapshotted before editing. `TASK_ONLY.patch` is computed against it, and applying it reproduces the current `src/` exactly (verified with `diff -r`). Pass 1, 2 and 3 work and all unrelated uncommitted changes are excluded.
- **No inference.** Every member is an exact CSV id; no tag membership is used (a JUnit guard forbids tags in the reconciliation section).
  - Slab doubles are the CSV's explicit "Double slab N" values.
  - `VANILLA` tool rows author no tool, so the vanilla tags remain; `NEUTRAL` means explicitly no preferred tool.
  - No Required Mining Tier is authored anywhere.
  - Material identities map to repo profile ids (`totality:<identity>`).
- **Scope.** Per your instruction, this pass **stops before transformation integration**. The delta ledger's §4 families (copper weathering/waxing/scraping, stripping, soil cultivation) are **not wired**. No transformation pair was registered, and the lazy id-transfer stays removed. Until that pass, the removal hook starts every changed block intact.

---

## 1. Implemented (`VanillaBlockProfiles.reconciliation()`)

| Group | IDs | Result |
|---|---|---|
| Soil | 6 | ORDINARY 100, Shovel (material `soil`) |
| Nether structural wood | 22 | new material `nether_wood`, separate from Overworld Wood, Axe:<br>full 100; slab 50 / double 100; fence & trapdoor 50; stairs & gate 75; door 100 with a shared lower-half owner |
| Wooden shelves | 12 | 100, Axe:<br>9 overworld species → Overworld Wood; Crimson/Warped → `nether_wood`; Bamboo → Crafted Bamboo |
| Sandstone walls | 2 | 75, Pickaxe (existing Sandstone material, new wall form) |
| Resin brick | 5 | full 150; stairs & wall 115; slab 75 / double 150 |
| Sulfur natural/polished | 8 | full 100; slab 50 / double 100; stairs & walls 75 |
| Sulfur bricks | 5 | full 150; slab 75 / double 150; stairs & wall 115 |
| Potent Sulfur | 1 | 100 (own identity) |
| Cinnabar natural/polished; bricks | 8 + 5 | the same values as the matching Sulfur rows |
| Copper utilities (oxidised/waxed) | 21 | Lantern 75, Bulb 150, Lightning Rod 75; tool VANILLA (none authored). Base blocks unchanged. |
| Copper bars / chains / chests / golem statues | 8 each | 100 / 50 / 150 / 150, Pickaxe; the chest is independent per position |
| Mob heads and skulls | 14 | 50, neutral |
| Crafter / Heavy Core / Pale Moss Block | 1 each | 150 Pickaxe / 600 Pickaxe / 50 Hoe |
| Mangrove Roots / Muddy Mangrove Roots | 1 each | 50 Axe / 100 Shovel |
| Petrified Oak Slab | 1 | slab 50 / double 100, Pickaxe (own `petrified_wood` identity) |
| **Explicit SPECIAL** | **29** | archaeology (2); raw resin (2); sulfur spike; creaking heart; copper torches (2); zero-hardness flora/technical (12); moss carpets (2); cake and glow lichen; chorus (2); frosted ice; big dripleaf and stem |

**Resolved parkings.** The Pass 3 parkings the delta resolves are removed: the dirt family, the suspicious blocks and the copper variants. With it goes the now-unused `review()` helper. `reviewNote` remains as the coverage report's hook; no ids are parked any more.

**Wording updated to accepted behaviour (the checks still pass):**
- The pre-Pass-1 check "Crimson Stem is excluded (≠ 100)" now asserts Crimson is excluded from the *Overworld* log family (it is `nether_wood` 100).
- The Pass 3 "block outside the ledger (Resin Bricks) keeps the fallback" check now uses the deferred Totality Tin Ore.
- The temporary-classification test now requires only that Resin Bricks has **no exact profile**, since it is now authored through its material.

---

## 2. Verification of the result

### Registry and id reconciliation (fresh live 26.2 registry, 1,226 ids)

| Category | Pass 3 | After reconciliation |
|---|---|---|
| ACCEPTED_AUTHORED | 697 | **844** (+147) |
| SPECIAL | 304 | **333** (+29) |
| UNBREAKABLE | 15 | 15 |
| NOT_APPLICABLE | 6 | 6 |
| COMPAT_FALLBACK (Totality, deferred to Core) | 28 | 28 |
| **UNRESOLVED_DESIGN** | 176 | **0** |

- **By id:** **176/176** ids match the CSV in coverage category, classification, default-state HP, tool rule and ownership (`RECONCILIATION_BY_ID.csv`, no mismatches).
- **Slab state maxima**, each verified single / double:
  - Resin Brick, Sulfur Brick and Cinnabar Brick: 75 / 150;
  - Crimson, Warped, Sulfur, Polished Sulfur, Cinnabar, Polished Cinnabar and Petrified Oak: 50 / 100.
- **No spillover:** all **1,050 non-delta rows are field-for-field identical** to the Pass 3 coverage, and no category changed outside the delta.
- **No vanilla fallback left:** there is no vanilla `UNRESOLVED_DESIGN` in this captured registry. The only fallback entries are the 28 deferred Totality blocks. Ruby's provisional 120/170 is unchanged.
- **INERT** (unchanged, not part of the delta): Repeater, Comparator, End Rod and Scaffolding stay vanilla-owned (hardness 0), and their HP is not operative.

### Automated suites

| Suite | Result |
|---|---|
| Build + JUnit (`build --rerun-tasks`, 7/7 tasks executed) | **1,984 tests, 0 failures, 0 errors** (+2) |
| Live, disposable `runVerificationServer`, existing opt-in gate | **every suite passed**; MiningVerification **331/331**, including one check that reads the canonical CSV and verifies all **176 rows against the live engine** (classification and authored provenance, default HP, explicit double-slab HP, tool rule, ownership) |
| **Unambiguous shutdown** | the final run was stopped **gracefully** with the server `stop` command over stdin (not killed): "Stopping server / Saving worlds", **Gradle exit 0, BUILD SUCCESSFUL**. This closes the exit-143 ambiguity of earlier runs. |
| Client, disposable `runClientGameTest` | TooltipV2Pass1 162/0, TooltipV2Enchantments 47/0, BlockBreakingPass2 11/0, **BlockBreakingPass3 23/0** |

**Client, in a real world:**
- **Tooltips:** tooltip and engine agree for **all 1,083 BlockItems**; 844 have rows, which equals the authored ordinary count.
- **Copper Chest, Oak Shelf, Crafter:** each keeps its inventory while damaged (150/100/150 max) and drops it on the vanilla break.
- **Skeleton Skull:** breaks at 50 HP and drops its item.
- **Earlier checks still pass:** Silverfish and Silk Touch, the chest, and Cobweb drops.
- **Screenshots:** Copper Chest, Skeleton Skull and Crimson Slab.

**New JUnit:** `Pass3ReconciliationSourceRegressionTest` checks CSV well-formedness (176 unique, 147/29). It also forbids tag membership, authored tiers and transformation pairs in the reconciliation section.

---

## 3. Deferred and open (not part of this delta)
- **The transformation-integration pass** needs event-safe `transformBlock` call sites. Until then, a changed block starts intact.
  - From the delta: copper weathering, waxing and scraping for all accepted copper forms, including chests, bars, chains, statues and utilities; Nether and Overworld stripping; soil cultivation, flattening and reversion.
  - From the original ledger: concrete setting, anvil degradation, sponge, coral and cauldron contents.
  - Farmland moisture, slab stacking and snow layering already preserve their percentage as same-id changes.
- **INERT hardness-0 rows:** needs an explicit ownership design.
- **Tooltip wrap:** the wrapped "No preferred tool" row still needs a presentation check.
- **Final manual checklist:** mining cadence and animation, Cobweb, Gold wear, slab/snow state changes, shared cracks, Copper Chest inventory, heads and statues.
- **Deferred to Totality Core:** the 28 Totality blocks and the extraordinary destruction systems.

## 4. Changed files (task-only; `CHANGED_FILES.txt`, 4 files)
**Modified:**
- `api/mining/VanillaBlockProfiles.java`: the reconciliation section; review parkings removed.
- `api/mining/MiningVerification.java`: the 176-row CSV live check; superseded checks reworded.
- `src/gametest/.../BlockBreakingPass3ClientGameTest.java`: inventories, head drop, screenshots.

**New:**
- `src/test/.../Pass3ReconciliationSourceRegressionTest.java`

**Repository safety:**
- **No reset, clean, stash, branch switch, commit, stage or push.**
- **`git status`:** identical to the pre-reconciliation snapshot. The new test sits in an already-untracked directory; the report and CSV copies are added docs under `Context/Audit/`.
- **Runs happened only in disposable directories:** `build/verification-run` (world deleted per launch) and `build/run/clientGameTest`.
