# TOTALITY — Block Breaking V2, Pass 1 Follow-up
## Transformation gating, confirmed decisions, partner-crack clearing

**Date:** 2026-09-25 · **Branch/HEAD:** `master` / `7213407` (unchanged; nothing committed, staged or pushed)
**Builds on:** `TOTALITY_BLOCK_BREAKING_V2_PASS1_CADENCE_AND_PROFILES_REPORT.md`
**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_BLOCK_BREAKING_V2_PASS1_FOLLOWUP_REVIEW.zip`

**Scope and baseline:**
- **Scope:** this correction only. The vanilla dataset, tool completion and Force Tolerance were not started.
- **Baseline:** `src/` was snapshotted at the start of this follow-up; `TASK_ONLY.patch` is computed against it. Applying the patch to that snapshot reproduces the current `src/` exactly (verified with `diff -r`). Pass 1 and all unrelated uncommitted work are therefore excluded from this patch.

---

## 1. Primary correction: transformations migrate only when the block actually transforms

### Defect (confirmed in the Pass 1 code)
`BlockDamageStorage.reconcile` is the lazy read/sweep path. It treated **any** block-id mismatch between the record and the current block as a transformation whenever `from → to` was a registered pair.

**Failure case:**
1. A damaged Stone was removed by something outside the strike pipeline (explosion, another mod, a command).
2. The record had not been read or swept yet.
3. Deepslate was placed later at the same position.
4. With Stone → Deepslate registered, the Deepslate inherited Stone's damage.

A lazy read cannot tell a real transformation from "removed, then replaced".

### Fix
- **Lazy path** (`IntegrityReconciliation.reconcile`, used by every read and sweep): it now has **no transformation input at all**. A different block id always deletes the record, whatever pairs are registered.
  - Unchanged: same-id state changes are kept, and a changed maximum is still rescaled by percentage.
- **Event path** (new `BlockDamageStorage.transformBlock(pos, newState, flags)`, backed by the pure `IntegrityReconciliation.transform`): the one place where Integrity can cross block ids. In a single call, it:
  1. reads the block **standing at the position right now**;
  2. confirms that the record belongs to that live block (`recordIsLiveBlock`);
  3. confirms that `live block → newState` is an authored pair;
  4. applies the block change itself (`Level.setBlock`);
  5. only then migrates the record by percentage (`round(newMax × old / oldMax)`, same clamp as Pass 1).

  In every other case the record is deleted. If the block change is refused, the record is left as it was.
- **Consequences:**
  - A stale record can never be revived by a later transformation call, because the removed block is no longer the live block.
  - Transforming the block without going through `transformBlock` counts as a replacement.
  - Future transformation hooks (stripping, weathering…) must perform the change **through** `transformBlock`.
- **No production transformations are authored** (decision 6). The registry and `transformBlock` are exercised only by verification, with a pair that is registered temporarily and withdrawn afterwards.

### Preserved
Percentage migration (now on the event path), ordinary state-change preservation, max-change rescale, and deletion on unrelated replacement or a classification change.

---

## 2. Confirmed decisions

| # | Decision | Status |
|---|---|---|
| 1 | Legacy door/bed records: the more damaged **recovered** percentage wins; never summed | Already implemented (`memberWinsFold` compares `currentIntegrity(now)/max`; the winner's raw integrity and last-impact move together). A test now asserts explicitly that records are not summed. |
| 2 | Stale-target correction also applies to bare hands and legacy Gold | Already implemented (legacy cadence-ratio branch of `correctRecovery`). No change. |
| 3 | Hardness-0 blocks stay vanilla-owned | Already the case: `MiningOwnership.owns` requires hardness > 0, whatever the classification. No change. |
| 4 | Tool-neutral ORDINARY blocks show "Effective Tool: No preferred tool"; Required Mining Tier is shown separately | **Implemented** (see below). |
| 5 | Keep the four Power Mining bands; defer Force Tolerance | Untouched: `MiningTuning`, `PlayerMiningPower` and the Power HUD are not in this patch. |
| 6 | Author no production transformations until event/lifecycle safety is established | None authored; the event-safe API from §1 is the prerequisite. |

**Decision 4 details** (`BlockDurabilityContributor`):
- **New pure `effectiveToolText(ToolAffinity)`:**
  - neutral → "No preferred tool";
  - preferred categories → "Pickaxe, Axe, …" (enum order, same text as before);
  - no conventional tool → no row (unchanged).
- **Unchanged:** the Required Mining Tier row is still emitted unconditionally, as its own row.
- **Icon:** the neutral row uses the existing generic tool icon (Iron Pickaxe), because the row type requires one. There is no other visual change.
- **No production block is tool-neutral yet**, so the text is covered by unit tests rather than a screenshot.

---

## 3. Stale partner crack on piston retraction

**Before:** when an extended piston retracted, the crack sent to the old head position under id `crackId + 1` was never cleared; it only expired client-side after up to 400 ticks. The Pass 1 report called it "invisible" because that position becomes air or a moving piston. It was never actively cleared, though, and would show again if a block was placed there.

**Now:**
- Each record remembers where it last sent its partner crack (`Entry.sentPartner`, transient).
- When a refresh finds the assembly split, it sends an explicit clear (`-1`) for that id and position. Any refresh counts: a strike, or the regular 40-tick sweep.
- `clearCrack` also resets it.
- **Bound:** the stale crack now clears within one sweep interval (≤ 40 ticks, 2 s), or immediately on the next strike, instead of up to 400 ticks.
- **Why not instant:** clearing on the exact retraction tick would need a piston-movement hook (a mixin into vanilla piston logic). That was judged out of proportion for a cosmetic overlay on an air position, and is listed under deferred work.

---

## 4. Test evidence

### JUnit (`./gradlew test`): 1,962 tests, 0 failures, 0 errors
Pass 1 ended at 1,952; this follow-up adds 10 net tests.

| Class | New / changed |
|---|---|
| `IntegrityReconciliationTest` (14) | new: a lazy read never transfers across ids even for an authored pair (removal → replacement); a transformation call after removal doesn't migrate a stale record; transforming into a non-ORDINARY or invalid block deletes; an explicit "no summing" assertion. Updated to the split `reconcile`/`transform` API. |
| `StorageTransformationSourceRegressionTest` (3) | `transformsTo` is consulted only inside `transformBlock`; the live-block check precedes the block change; the lazy `reconcile` has no transformation input |
| `BlockDurabilityContributorEffectiveToolTest` (4) | "No preferred tool"; category text and order; no row for NONE; the tier row stays independent |

All pre-existing tests pass, including `TooltipApiFoundationSourceRegressionTest` (106) and the live-world gate guard.

### Live world: disposable `runVerificationServer`, existing opt-in gate
- **Every suite passed.** MiningVerification is **284/284**. The 5 new named checks:
  - `transition`: an authored transformation **of the live block** (through `transformBlock`) migrates 60% of 100 → 120 of 200;
  - `transition`: **removal, then replacement with an authored-pair block, does NOT inherit** the damage. No read or sweep runs between removal and replacement, so the stale record is present;
  - `transition`: a direct block change between an authored pair, with no transformation call, counts as a replacement;
  - `transition`: a transformation call on a position whose block was already removed migrates nothing;
  - `piston`: the partner crack is sent to the head while the piston is extended and is cleared by the next refresh after retraction.
- **All 30 Pass 1 checks still pass.**

### Mutation check (does the regression test catch the defect?)
- **Setup:** the Pass 1 lazy transfer was temporarily reinstated in `BlockDamageStorage.reconcile`, and the live suite was run.
- **Result:** exactly the two lazy-path checks failed, "removal then replacement…" and "direct block change…" (`2/284 FAILED`, log in the bundle).
- **Cleanup:** the file was restored byte-identical (verified with `cmp`); the passing run above was made on that exact source.

### Client: disposable `runClientGameTest`
TooltipV2Pass1 162/0 and TooltipV2Enchantments 47/0; the client self-tests passed.

### Not run
- A manual in-game session.
- Anything against a development or personal save (`run/` was not launched).

---

## 5. Deferred work and residual notes
- **Same-id destroy-and-replace window.** This rule predates the follow-up and was accepted as-is. Suppose a damaged block is destroyed outside the strike pipeline and a block with the **same id** is placed before the next read or sweep (≤ 40 ticks in a loaded chunk). The new block inherits the record, because a lazy id comparison cannot see the removal.
  - Closing this needs a world block-change hook (e.g. a `LevelChunk.setBlockState` mixin that drops the record when the block at its position changes).
  - It was not changed here: that is a new world-mutation hook, and the brief says to preserve the accepted replacement-clearing rules. **Recommended as its own small pass.**
- Instant clearing of the piston partner crack on the retraction tick; see §3.
- Production transformation hooks. Each must call `transformBlock`.

---

## 6. Changed files (task-only; see `CHANGED_FILES.txt`)
**Modified:**
- `api/mining/BlockDamageStorage.java`: lazy reconcile without transformations; `transformBlock`; partner-crack tracking.
- `api/mining/IntegrityReconciliation.java`: split into `reconcile` (lazy) and `transform` (event).
- `api/mining/MiningVerification.java`: nested gated checks, 5 new plus 1 updated transformation check.
- `client/tooltip/contributor/BlockDurabilityContributor.java`: "No preferred tool".
- `src/test/.../api/mining/IntegrityReconciliationTest.java`

**New:**
- `src/test/.../api/mining/StorageTransformationSourceRegressionTest.java`
- `src/test/.../client/tooltip/contributor/BlockDurabilityContributorEffectiveToolTest.java`

**Repository safety:**
- **No reset, clean, stash, branch switch, commit or push;** nothing is staged.
- **`git status`:** identical to the pre-follow-up snapshot except for one new untracked test file.
- **Runs happened only in the disposable `build/verification-run` and `build/run/clientGameTest`.**
