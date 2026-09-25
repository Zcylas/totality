# TOTALITY — Block Breaking V2, Implementation Pass 2
## Integrity Safeguard and Conventional Tool Completion

**Date:** 2026-09-25 · **Target:** Minecraft 26.2 / Fabric / Java 25 · **Branch/HEAD:** `master` / `7213407` (unchanged; nothing committed, staged or pushed)
**Builds on:** `TOTALITY_BLOCK_BREAKING_V2_PASS1_CADENCE_AND_PROFILES_REPORT.md`, `TOTALITY_BLOCK_BREAKING_V2_PASS1_FOLLOWUP_REPORT.md`
**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_BLOCK_BREAKING_V2_PASS2_REVIEW.zip`

**Before editing:**
- **Master reference:** still not on disk; a filesystem search again found no copy. The Pass 2 brief and the two accepted Pass 1 reports were treated as the authority.
- **Baseline:** `src/` was snapshotted at the start of Pass 2, and `TASK_ONLY.patch` is computed against that snapshot. Applying the patch to the snapshot reproduces the current `src/` exactly (verified with `diff -r`, excluding the git-ignored datagen `.cache`). Pass 1, the follow-up and all unrelated uncommitted work are excluded from this patch.

---

## 1. Same-ID replacement safeguard

### Mechanism
- **Hook: `mixin/mining/LevelChunkBlockRemovalMixin`.** A common (server-effective) mixin at the HEAD of `LevelChunk.setBlockState(BlockPos, BlockState, int)`.
  - Every server-side block mutation goes through this method: `Level.setBlock`, `destroyBlock`, `removeBlock`, explosions, piston movement, commands, and vanilla `ServerPlayerGameMode.destroyBlock`.
  - At HEAD it reads the block currently in that section and compares it with the incoming state's `Block`. Only a **different block** counts as a removal.
  - **Why HEAD:** the method's early exits either change nothing (`oldState == state`) or start from air, so this comparison is exact.
- **Handler: `BlockDamageStorage.onBlockRemoved(level, pos)`.** It deletes the record at exactly that position, together with its crack (and the partner crack id). It happens at the removal itself, not at a later id comparison.
- **Costs and safety:**
  - **Common path:** one `getBlockState` on the chunk already being written, plus an `is()` check. Storage is touched only when the block really changes, and it returns at once when the level has no records.
  - **No chunk loading, no scan, no recursion:** the handler only removes a map entry and sends crack packets, and it never sets blocks.
- **Ordinary state changes are preserved.** A same-`Block` change (door open/close, log axis, piston base extend/retract, furnace lit, …) is ignored by the hook.
- **Shared assemblies:**
  - The record lives on the owner (door lower half, bed head, piston base). Removing the partner half while the owner stands keeps it; removing the owner deletes it.
  - Piston behaviour is unchanged: the base changes state only, and the head position never holds a record.
- **The authorized transformation is protected.** `transformBlock` brackets its own `setBlock` with a suppression marker (that level and position only, restored in `finally`, so nested changes elsewhere are unaffected). The hook therefore cannot clear the record the transaction is migrating; the transaction decides that record's fate itself, exactly as in the follow-up.
- **Lazy old-save reconciliation is untouched.** Read/sweep reconcile, the start-up records pass, the legacy owner fold, and the isolated sweep tests (which run on a throwaway storage the hook never touches) all still apply.

### Consequence to note
A damaged block that is **pushed by a piston** is replaced at its old position by `moving_piston`, so its record is deleted and it arrives intact. The Pass 1 lazy rule already produced this result; it now happens at once.

---

## 2. Conventional tool completion

| Source | Mining Damage | Mining Speed | Tier | Pipeline |
|---|---|---|---|---|
| Wood / Stone / Copper / Iron / Diamond / Netherite: Pickaxe, Axe, Shovel **and Hoe** | 20 / 35 / 40 / 50 / 75 / 100 | 1.5 / 2.0 / 2.1 / 2.25 / 2.4 / 2.5 | 1 / 2 / 2 / 3 / 4 / 4 | profiled |
| **Gold** Pickaxe, Axe, Shovel, Hoe | **25** | **3.0/s** | **1** | profiled (was legacy) |
| Swords, Shears | legacy (intrinsic × 6 + STR) | legacy cadence ratio | specialized (see below) | legacy, non-profiled |

### Gold
- A row in the single `MiningSourceProfile` table.
- Its Tier comes from the same lazy pickaxe probe as the other rows and resolves to 1 (asserted live).
- Vanilla item durability 32 is untouched (asserted).
- **Behaviour through the standard profiled pipeline:**
  - supports Impact (now in the generated Impact tag);
  - normal correct/wrong-tool effectiveness (Gold Pickaxe on Dirt: 25 × 0.10 = 2.5);
  - profiled cadence (sustained 20/3 ≈ 6.67-tick contact interval, measured live);
  - profiled Power zones;
  - ordinary wear.
- **The legacy automatic STR on ordinary strikes is gone:** 25 at both STR 10 and STR 16.

### Hoes
- Every material row carries its Hoe, with exactly that row's Damage/Speed/Tier.
- **Effectiveness:** `BlockProfile.Tool.HOE`. `TargetEffectiveness` maps `#minecraft:hoes` to HOE, and the compatibility fallback adds `#minecraft:mineable/hoe`, so authored profiles can name HOE as well.
- **Standard behaviour:** Impact, Efficiency (Iron Hoe 2.25 → 7.25/s with Efficiency V), Power Mining zones, Mining Tier and wear (1 per damaging impact, asserted through the real strike path).
- **Tilling is untouched:** `useOn` on Dirt still makes Farmland (asserted live). Mining is left-click and tilling is use, and nothing in their interaction code changed.
- **Tooltip V2:** the block Effective Tool row can now read "Hoe" (e.g. Hay Block, checked in the client).

### Swords and Shears
- They remain specialized, non-profiled sources: no conventional Mining Damage, no Impact (not in the tag), and no profiled Power zones.
- **Legacy Tier 0 interference, corrected:**
  - Their Tier was the pickaxe probe, which is 0, so a Sword or Shears was INEFFECTIVE on Cobweb (Required Tier 1), the very block they are made for.
  - New `MiningTier.specializedTier`: on a block the tool's **own vanilla rules** make it correct for drops (`isCorrectToolForDrops`), the block's Required Tier no longer gates it. Everywhere else it keeps the probe Tier (a Sword on Stone is still Tier 0 < 1).
  - Vanilla still decides drops at the terminal break: Sword → string, Shears → cobweb.

### Mining progression
- Diamond and Netherite both stay at Tier 4, and no authored tool exceeds 4 (asserted). Tier 5 (`TIER_MAX`) stays reserved for Totality Core.
- Vanilla harvesting requirements are untouched: `requiresCorrectToolForDrops` and `needs_*_tool`.

### Force Tolerance
- The field (`ResolvedMiningSource.forceTolerance`, `MiningTuning.forceTolerance`) is preserved; Gold still reads `TOLERANCE_GOLD`.
- The numeric SHIFT row in `MiningToolContributor` is removed, because profiled tools take Power wear from the four zone bands and the number was misleading.
- No mechanics were invented. The four Power bands are unchanged (asserted at 0.2 / 0.5 / 0.7 / 0.9).

### Datagen
- `runDatagen` was run. The **only** change to `src/main/generated` is `data/totality/tags/item/enchantable/mining_damage.json`: 18 → 28 entries (+6 Hoes, +4 Gold). Datagen's git-ignored hash cache also updated.
- Nothing else was regenerated differently (a before/after `diff -r` of the directory confirms it).

---

## 3. Behaviour changes a reviewer should know
1. **Records are deleted when their block is removed**, so a fresh same-id block can no longer inherit damage. Pushed blocks lose their damage (§1).
2. **Gold no longer uses the V1 formula.** It went from 72 damage on Stone, plus STR, on the legacy cadence to 25 at 3.0/s, profiled. **Gold Power Mining now uses zone extra wear**, not the Force-Tolerance stress formula: with a 32-durability item, a RED-zone swing at high STR can destroy it quickly. This follows the accepted rule, but is worth playtesting.
3. **Hoes on non-hoe blocks** were legacy Tier 0 (INEFFECTIVE on anything requiring a tool). They now have their material Tier with wrong-tool effectiveness (×0.10 damage / ×0.50 speed): an Iron Hoe can now slowly damage Stone. This is the direct consequence of the accepted "same Tier as the material" rule.
4. **Swords and Shears on Cobweb** can now damage and break it through Totality mining, with vanilla drops.
5. **Force Tolerance** is no longer shown on any tool tooltip.

---

## 4. Test evidence

| Suite | Result |
|---|---|
| JUnit (`build --rerun-tasks`: 7/7 tasks re-executed from scratch) | **1,972 tests, 0 failures, 0 errors** (follow-up ended at 1,962; +10 new) |
| Live, disposable `runVerificationServer`, existing opt-in gate | **every suite passed**; MiningVerification **309/309** (was 284); DurabilityRegression 14/14; the rest unchanged and passing |
| Client, disposable `runClientGameTest` | TooltipV2Pass1 **162/0**, TooltipV2Enchantments **47/0**, new BlockBreakingPass2 **11/0** |
| Mutation: removal handler disabled | exactly the **4** removal-dependent live checks fail (`4/309`); file restored byte-identical before the passing run |

**New JUnit tests:**
- `BlockRemovalHookSourceRegressionTest` (3): common-mixin registration; HEAD injection with the server and same-block guards before the notification and no re-entry; transformation exemption, an exact-position lookup, and suppression bracketing exactly the block change.
- `ConventionalToolCompletionSourceRegressionTest` (6): the Gold row; the Hoe in every row; the generated tag holds exactly 28 items including Gold and Hoes and no swords/shears; the specialized-tier rule; hoe tags; Tier 4 max with 5 reserved.
- `BlockDurabilityContributorEffectiveToolTest`: +1 test, the Hoe label.
- `TooltipApiFoundationSourceRegressionTest`: the pinned "Force Tolerance must remain Shift-only" assertion was updated to "Force Tolerance must not be displayed". This is the one intentional change to an existing test.

**New or updated live checks (31 Pass 2 check lines in `evidence/pass2-live-checks.txt`):**
- **Safeguard:**
  - **same-id replacement** (damaged Stone → air → fresh Stone, no read or sweep in between; the new Stone starts intact);
  - different-id replacement deleted at the removal;
  - ordinary state change kept (log axis);
  - authorized `transformBlock` still migrates with the hook active;
  - a shared door keeps its record through the open state and the partner's removal, and loses it with the owner;
  - breaking a door deletes the owner record immediately.
- **Tools:**
  - Gold profile values, durability 32, no STR, wrong-tool effectiveness, Impact, profiled Power, the Force Tolerance field, and a sustained 3.0/s cadence;
  - Hoe baselines per material, effectiveness, Impact, Efficiency, Power, wear and tilling;
  - Sword and Shears Tier behaviour on Cobweb and Stone, no Impact, no profiled Power;
  - the four bands;
  - Tier ≤ 4.
- **Superseded checks updated to the accepted Pass 2 behaviour:**
  - "Gold has NO profile" and "hoes are NOT authored" became the new value checks;
  - the legacy Efficiency/Haste/Fatigue cadence-ratio assertions move from Gold on Stone (no longer legacy) to Shears on Wool, a still-legacy specialized source with intrinsic speed 5, so vanilla Efficiency still applies;
  - participating items went from 18 to 28.

**Not run, and why:**
- **`gradle clean`:** it would delete `build/`, including previous tasks' test artefacts and the disposable run directories. The requested clean build was done as `build --rerun-tasks` instead, which re-executes every task from scratch without deleting anything.
- **Nothing ran against a personal or development save;** `run/` was not launched.

---

## 5. Manual-only checks
- **In-game Gold feel:** the 3.0/s swing animation, and Power RED-zone wear on a 32-durability tool.
- **Crack visuals:**
  - blowing up or `/setblock`-replacing a damaged block clears its crack at once;
  - piston-pushing a damaged block: it arrives intact and no crack is left behind.
- **Hoes:** Sword/Shears on a real Cobweb (string vs cobweb drop); a Hoe on Leaves or Hay in real play; tilling a Grass Block/Dirt path with a damaged neighbour.
- **Tooltip look:** the Hoe icon in the Effective Tool row; tool tooltips without the Force Tolerance row.

---

## 6. Decisions and ambiguities for Stefan
- **Specialized sources and Power Mining.** Swords and Shears keep their existing **legacy** Power behaviour (legacy damage multiplier and Force-Tolerance stress). The brief says not to grant them *profiled* Power, so they were not; whether they should be excluded from Power Mining entirely is open.
- **Gold Tier source.** Gold's Tier (1) comes from the same pickaxe probe as every row. The pinned source test requires the lazy probe, and it matches the accepted value. If authored tiers are later preferred over the probe, that is a table-wide change.
- **Piston-pushed damaged blocks arrive intact.** Preserving damage across piston movement would need its own authored transfer.

## 7. Boundaries respected
- No vanilla block dataset.
- No fictional-material rebalance.
- No extraordinary destruction integration.
- No production physical transformations.
- No Tooltip V2 or Enchanting API redesign; the only tooltip changes are the removed Force Tolerance row and the new Hoe label.
- No new verification gate.

## 8. Changed files (task-only; `CHANGED_FILES.txt`, 20 files)
**New:**
- `mixin/mining/LevelChunkBlockRemovalMixin.java`
- `src/test/.../BlockRemovalHookSourceRegressionTest.java`
- `src/test/.../ConventionalToolCompletionSourceRegressionTest.java`
- `src/gametest/.../BlockBreakingPass2ClientGameTest.java`

**Modified:**
- `BlockDamageStorage` (removal handler, transformation suppression)
- `BlockProfile` (HOE)
- `MiningSourceProfile` (Gold, Hoes)
- `MiningTier` (`specializedTier`)
- `PlayerMiningPower`
- `PlayerMiningManager` (comments)
- `ResolvedMiningSource` (comments)
- `TargetEffectiveness` (Hoe)
- `MiningVerification` (live checks)
- `BlockDurabilityContributor` (Hoe label)
- `MiningToolContributor` (Force Tolerance row hidden)
- `totality.mixins.json`
- gametest `fabric.mod.json`
- generated `mining_damage.json`
- `BlockDurabilityContributorEffectiveToolTest`
- `TooltipApiFoundationSourceRegressionTest`

**Repository safety:**
- **No reset, clean, stash, branch switch, commit or push;** nothing is staged.
- **`git status`:** identical to the pre-Pass-2 snapshot except for the one new untracked mixin file. The other new files sit in already-untracked directories.
- **Runs happened only in disposable `build/verification-run` and `build/run/clientGameTest`.**
