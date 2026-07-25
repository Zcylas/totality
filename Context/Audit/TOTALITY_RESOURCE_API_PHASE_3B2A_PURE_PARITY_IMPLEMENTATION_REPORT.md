# TOTALITY — Generic Player Resource API — Phase 3B-2A Implementation Report
## Pure Shadow-Parity Model, Comparators, and Grace Tracker

Scope actually implemented: the narrow Phase 3B-2A slice only — immutable parity value
representations, immutable comparison results, a scalar comparator, a spell-slot partitioned
comparator, a Rage absence/expectation policy, the classification vocabulary, and an
elapsed-client-tick grace tracker. No client integration, no lifecycle hooks, no logging, no
legacy manager modification, no Phase 3B-2B/3B-2C/3B-3/3C.

---

## 1. Starting branch and commit

- Branch: `feature/general-resource-api`.
- Starting `HEAD`: `2fbdee92f28c23614701ad04a44533d065c5342d` ("Add trusted client resource view") — confirmed matching the expected checkpoint before any change.
- Tracking `origin/feature/general-resource-api`, 0 ahead / 0 behind at start.
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B2_SHADOW_PARITY_READINESS.md` was present, untracked, uncommitted — preserved unchanged by this session; it is a design document, not rewritten into this implementation report.
- No Phase 3B-1 source, test, or audit file was uncommitted at start; nothing was staged.

---

## 2. Exact files created and modified

**Created (production, pure — `zcylas.totality.api.rpg.resources.client.parity`):**
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityClassification.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityOutcome.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParitySummary.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityObservation.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceScalarParityComparator.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientSpellSlotParityComparator.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientRageParityPolicy.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityTracker.java`

**Created (tests, same package under `src/test`):**
- `ClientResourceParitySummaryTest.java` (12 tests)
- `ClientResourceScalarParityComparatorTest.java` (9 tests)
- `ClientSpellSlotParityComparatorTest.java` (10 tests)
- `ClientRageParityPolicyTest.java` (11 tests)
- `ClientResourceParityTrackerTest.java` (28 tests)

**Modified:** no existing file was modified. `TotalityClient.java`, `TotalityClientPacketHandlers.java`, `ClientResourceSyncManager.java`, `ClientResourceSyncBridge.java`, `ClientManaManager.java`, `ClientStaminaManager.java`, `ClientSpellSlotManager.java`, `PlayerChargesComponent.java`, `ClientClassManager.java`, and every HUD/screen/radial/tooltip/movement class remain byte-for-byte as Phase 3B-1 left them.

**Not touched:** every other file in `src/main`/`src/test`, `/Inspiration Mods`, and all "known unrelated" working-tree entries (generated datagen JSON, `Context/Trading Test/*.png`, `logs/`, `src/main/generated/.cache/`, all review ZIPs, the Phase 3B-2 readiness audit document).

---

## 3. External-review refinements that override the readiness audit

Per this task's explicit instruction, the following override the Phase 3B-2 readiness audit's original recommendations:

1. **Elapsed-tick grace, not generation-only grace.** The readiness audit (§7.1) proposed advancing grace only on a fresh source-triggered comparison. This implementation instead requires the coordinator (Phase 3B-2B, not built here) to re-evaluate every pending mismatch on every `END_CLIENT_TICK`, whether or not either source reports a change — `graceTicksConsumed` accumulates from the real tick delta between successive calls, not from a call counter. **[CORRECTED 2026-07-25 — see the external-review correction section below]** the primary mechanism for this per-tick re-evaluation is a fresh comparison fed to `ClientResourceParityTracker.observe` every tick (Phase 3B-2B's full four-resource polling design), not `ClientResourceParityTracker.advanceDeadline` — `advanceDeadline` is a narrower, purely diagnostic state-machine helper, not the primary integration route; this passage originally implied otherwise.
2. **Pending deadline rechecks are a first-class tracker operation** (`resourcesRequiringRecheck()`), not an implicit side effect — giving the future coordinator an explicit set of resource ids that still need continued deadline advancement. **[CORRECTED 2026-07-25]** this method (originally named `pendingDeadlineRechecks()`) originally excluded a frozen `TRANSITIONAL_MISMATCH` from its result — external review found this made a frozen mismatch invisible to the one set Phase 3B-2B was told to revisit. It now includes frozen transitional mismatches and excludes `PERSISTENT_MISMATCH` (which no longer needs continued deadline advancement); see the correction section for the full reasoning.
3. **Mana join/rune staleness is an ordinary mismatch candidate, never `EXPECTED_SEMANTIC_DIFFERENCE`.** Confirmed by this implementation's design: neither `ClientResourceScalarParityComparator` nor `ClientRageParityPolicy` special-cases Mana at all — a stale legacy Mana value (from the confirmed `FormulaResolver.tryCast` gap, or the missing join-time push) simply produces `MISMATCH`, which the tracker will escalate to `PERSISTENT_MISMATCH` after 2 ticks of continued disagreement like any other resource. Only Rage's documented sparse-map absence convention is treated as `EXPECTED_SEMANTIC_DIFFERENCE`, and only in the specific non-expected + generic-absent + legacy-0/0 combination (§7 below).
4. **No `RAGE_ABSENCE_AMBIGUOUS` classification.** `ClientRageParityPolicy` fully disambiguates Rage absence using the caller-supplied `rageExpectedForPlayer` boolean — every combination of expectation × generic-presence × legacy-value maps deterministically to one of the seven required classifications; no residual "ambiguous" bucket is needed.
5. **Typed immutable summaries, not stored formatted strings.** `ClientResourceParityObservation` stores the actual `ClientResourceParitySummary` values (`Scalar`/`Partitioned`/`Unavailable`), never a pre-rendered display string — formatting is explicitly deferred to Phase 3B-2C/3B-3.

---

## 4. Classification vocabulary

`ClientResourceParityClassification` (7 members, exactly as specified, no more): `EXACT_MATCH`, `TRANSITIONAL_MISMATCH`, `PERSISTENT_MISMATCH`, `EXPECTED_SEMANTIC_DIFFERENCE`, `GENERIC_NOT_READY`, `MODEL_MISMATCH`, `NOT_APPLICABLE`.

A second, narrower enum, `ClientResourceParityOutcome` (`MATCH`, `MISMATCH`, `GENERIC_NOT_READY`, `MODEL_MISMATCH`, `EXPECTED_SEMANTIC_DIFFERENCE`, `NOT_APPLICABLE`), is what the three comparators actually return — `MISMATCH` is a bare "these differ and are otherwise comparable" signal, never pre-escalated to transitional/persistent by a comparator. Only `ClientResourceParityTracker.observe`/`advanceDeadline` ever produces a final `ClientResourceParityClassification`, translating `MISMATCH` into `TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH` based on elapsed grace and mapping every other outcome 1:1 onto its matching classification. This two-enum split is what lets the comparators stay completely free of tick/time concepts and the tracker stay completely free of Resource-shape concepts — each is independently unit-testable.

---

## 5. Scalar comparator

`ClientResourceScalarParityComparator.compare(generic, legacy)` — used directly for Mana/Stamina, and by `ClientRageParityPolicy` once Rage's own absence branching has resolved to "generic reports a present value":

1. `generic` is `Unavailable(NOT_SYNCHRONIZED_YET)` → `GENERIC_NOT_READY`, no comparison attempted.
2. `generic` is any other `Unavailable` reason → `MODEL_MISMATCH` (structurally unexpected for a resource that should always be a plain synced scalar once ready).
3. `generic` is `Partitioned` (shape contradiction) → `MODEL_MISMATCH`.
4. `legacy` is not `Scalar` (shape contradiction or unavailable) → `MODEL_MISMATCH`.
5. **(external-review correction, 2026-07-25)** Either side's `unitScale() != CANONICAL_UNIT_SCALE` (`1`) → `MODEL_MISMATCH` — checked before any numeric comparison. Two sides that happen to share the same non-canonical scale are still `MODEL_MISMATCH`; scales are never compared merely to each other, only each independently against the canonical constant. No dedicated `UNIT_SCALE_MISMATCH` classification was introduced — this folds into the existing `MODEL_MISMATCH` vocabulary, per the external review's explicit instruction.
6. `generic.overflowUnits() != 0 || legacy.overflowUnits() != 0` → `MODEL_MISMATCH` — **(corrected: originally only the generic side's overflow was checked; the legacy side is now checked too)** no legacy mirror in this codebase (`ClientManaManager`, `ClientStaminaManager`, `PlayerChargesComponent`'s Rage pool) can represent overflow at all, on either side.
7. `current`/`maximum` equal → `MATCH`.
8. Either differs → `MISMATCH` (never clamped, never converted — raw comparison only).

---

## 6. Spell-slot comparator and remaining-slot mapping

`ClientSpellSlotParityComparator.compare(generic, legacy)` implements the exact mapping confirmed in the Phase 3B-2 readiness audit §4.1: `StandardSpellSlotsResourceAdapter.resolve` computes `current = maximum - used`, the identical quantity `ClientSpellSlotManager.getRemaining(level) = maxSlots[i] - usedSlots[i]` already returns. This comparator therefore has **no concept of "used slots" at all** — both `generic` and `legacy` are expected as `currentUnits = remaining`, `maximumUnits = maximum`; the caller (Phase 3B-2B) is responsible for feeding `getRemaining(level)`, never `getUsed(level)`, into the legacy side's `currentUnits`. `currentRepresentsRemainingNotUsed` (test) explicitly proves feeding the *used* count instead produces a `MISMATCH`, confirming the comparator is sensitive to using the correct field rather than tolerant of either.

Rules, checked by integer key (1-10), never by raw map/array position:
- `generic`/`legacy` not both `Partitioned` → `MODEL_MISMATCH`.
- **(external-review correction, 2026-07-25)** Either side's `Partitioned.unitScale() != CANONICAL_UNIT_SCALE` (`1`) → `MODEL_MISMATCH`, checked immediately after shape confirmation and before any per-level comparison — same reasoning as the scalar comparator's unit-scale check.
- Any level 1-10 missing from either side → `MODEL_MISMATCH`.
- Any partition key outside 1-10 present on either side → `MODEL_MISMATCH`.
- Any level's `overflowUnits() > 0` on either side → `MODEL_MISMATCH` (the fixed-size legacy int arrays have no overflow concept) — unchanged, retained exactly as before.
- All ten levels agree (including all-zero) → `MATCH`.
- Any level's `currentUnits`/`maximumUnits` disagree → `MISMATCH` (resource-level, not per-level — the whole `totality:spell_slots` id is one tracked resource).
- Duplicate partition ids cannot reach the comparator at all — rejected at `ClientResourceParitySummary.Partitioned`/`Partitioned.of` construction time.

---

## 7. Rage expectation policy

`ClientRageParityPolicy.compare(rageExpectedForPlayer, generic, legacy)` — a pure function; never touches `ClientClassManager` or any real class-manager object (Phase 3B-2B's job, per this task's explicit scope boundary):

1. `generic` is `Unavailable(NOT_SYNCHRONIZED_YET)` → `GENERIC_NOT_READY`.
2. `generic` is `Unavailable(NOT_AVAILABLE_TO_PLAYER)`:
   - `rageExpectedForPlayer == true` → `MISMATCH` unconditionally, regardless of the legacy value (a player who should have Rage but the generic side reports none granted is always a real gap).
   - `rageExpectedForPlayer == false` and `legacy` is the **exact, well-formed sparse-map representation** — a `Scalar` at `currentUnits == 0`, `maximumUnits == 0`, `overflowUnits == 0`, **and** `unitScale == 1` **(external-review correction, 2026-07-25 — the scale and overflow checks were added; originally only current/maximum were checked)** → `EXPECTED_SEMANTIC_DIFFERENCE`.
   - `rageExpectedForPlayer == false` and `legacy` fails any part of that exact shape (nonzero current/maximum, nonzero overflow, or a non-canonical scale) → `MISMATCH` (stray/stale/malformed legacy state, never suppressed).
3. `generic` is any other `Unavailable` reason → `MODEL_MISMATCH`.
4. `generic` reports a present value (`Scalar`) → delegates to `ClientResourceScalarParityComparator.compare` unconditionally, regardless of `rageExpectedForPlayer` — a present generic value is always comparable (covers present-0/0-exact-match, present-nonzero-exact-match, and present-nonzero-vs-legacy-0/0-mismatch uniformly), and so also picks up that comparator's own unit-scale/overflow checks automatically.

No `hasPool(...)` accessor was added — not needed for this policy (per the readiness audit §9.4's own conclusion, confirmed unchanged) and explicitly out of scope for this slice regardless.

---

## 8. Tracker state machine

`ClientResourceParityTracker` holds one `TrackedState` per observed resource id in a `LinkedHashMap`, bounded to exactly the number of distinct ids ever observed (never a growing history). Two internal fields drive the grace calculation independently of the publicly exposed `firstMismatchTick`:
- `graceTicksConsumed` (long) — accumulates the real tick delta between successive `MISMATCH` observations, only while not frozen (§10).
- `lastObservedTick` (long) — updated on every call (frozen or not), so a delta is always computed against the true previous call, never against a stale pre-freeze baseline.

`observe(resourceId, tick, outcome, genericSummary, legacySummary, pendingResync)` is the sole state-mutating entry point:
- `outcome == MISMATCH`: if this does not continue an already-pending `TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH` episode, a fresh episode begins (`firstMismatchTick = tick`, `graceTicksConsumed = 0`, `observationCount = 1`). Otherwise, `graceTicksConsumed += (tick - lastObservedTick)` unless frozen, `observationCount += 1`, and `firstMismatchTick` is left untouched. Classification becomes `PERSISTENT_MISMATCH` once `graceTicksConsumed >= graceDurationTicks`, else `TRANSITIONAL_MISMATCH`.
- Any other outcome: `firstMismatchTick`/`graceTicksConsumed`/`graceFrozen` are all reset (cleared), classification is set directly from the outcome, and `observationCount` resets to 1 unless the classification is unchanged from the immediately prior observation (in which case it increments) — giving `EXPECTED_SEMANTIC_DIFFERENCE`/`MODEL_MISMATCH`/`GENERIC_NOT_READY`/`NOT_APPLICABLE` a meaningful, bounded repeat-observation count without ever touching grace state.

`advanceDeadline(resourceId, tick, pendingResync)` (renamed from `advance` — **[CORRECTED 2026-07-25]**) re-invokes `observe` with the resource's own already-stored `genericSummary`/`legacySummary` and `MISMATCH`, but only when the resource is currently tracking a mismatch — it performs **no fresh parity comparison of any kind**. **[CORRECTED 2026-07-25]** this method is *not* the primary Phase 3B-2B integration route (an earlier version of this passage implied it was); see the correction section's "Phase 3B-2B integration decision" for the actual polling design (a fresh comparison fed to `observe` every tick). `advanceDeadline` remains a narrow, purely pure-state-machine operation useful for proving a deadline can advance without any source-side notification when values are already known unchanged. Calling `advanceDeadline` on a resource that is not currently mismatch-tracking (or has never been observed) is a safe no-op.

`latest(resourceId)`, `resourcesRequiringRecheck()`, `clear(resourceId)`, `clearAll()` round out the required operation set. No method returns a mutable backing map; `resourcesRequiringRecheck()` returns an unmodifiable `Set` snapshot.

---

## 9. Exact grace off-by-one semantics

With the default 2-tick grace, verified by `firstMismatchAtTIsTransitional`/`continuedMismatchAtTPlusOneRemainsTransitional`/`continuedMismatchAtTPlusTwoBecomesPersistent`:
- Tick `T` (first mismatch): `TRANSITIONAL_MISMATCH`, `graceTicksConsumed = 0`.
- Tick `T+1` (still mismatched): `TRANSITIONAL_MISMATCH`, `graceTicksConsumed = 1` (`1 < 2`).
- Tick `T+2` (still mismatched): `PERSISTENT_MISMATCH`, `graceTicksConsumed = 2` (`2 >= 2`).

`configurableGraceDurationChangesTheDeadline` proves a custom `graceDurationTicks` (e.g. 3) shifts the deadline accordingly (`T+2` still transitional, `T+3` persistent), and the constructor rejects `< 1`.

Because `graceTicksConsumed` accumulates the real tick delta (not a call count), a coordinator that skips ticks (e.g. observes at `T` then next at `T+5` with continued disagreement) escalates immediately to `PERSISTENT_MISMATCH` rather than requiring exactly 2 more calls — consistent with "elapsed-client-tick deadline," not "call-count deadline." `continuousMismatchCannotRemainTransitionalForever` drives 50 consecutive per-tick mismatched observations and confirms the final state is `PERSISTENT_MISMATCH`, never stuck at `TRANSITIONAL_MISMATCH`.

---

## 10. `PENDING_RESYNC` freeze behavior

**[CORRECTED 2026-07-25 — this section originally described a version of this behavior that only worked correctly when the coordinator called the tracker on every single frozen tick; see the dated external-review correction section below ("Skipped frozen-span correction") for the actual, current mechanism and its two additional test walkthroughs. The description below is updated in place to match the corrected implementation.]**

Verified by `pendingResyncFreezesGraceProgression`/`matchWhilePendingResyncClearsTheMismatch`/`graceResumesFromRemainingDurationAfterPendingResyncEnds`/`skippedFrozenSpanWithZeroGraceConsumedBeforeFreezingDoesNotCountTheGap`/`skippedFrozenSpanWithOneGraceTickConsumedBeforeFreezingPreservesThatOneTick`:
- A `MISMATCH` observation with `pendingResync = true` sets `graceFrozen = true` on the observation and does **not** add any elapsed delta to `graceTicksConsumed` — but `lastObservedTick` is still updated every call (frozen or not), so any subsequent delta calculation is always measured from the true previous call.
- A `MATCH` observation clears the pending mismatch unconditionally, regardless of `pendingResync` — freeze status is never consulted for a `MATCH`/other-non-mismatch outcome.
- The specific **frozen-to-unfrozen transition call** (the first observation reporting `pendingResync == false` immediately after a previous observation reported `pendingResync == true`) contributes **zero newly-consumed grace**, regardless of how many real ticks elapsed since the last frozen observation (including ticks the coordinator never called the tracker for at all) — it only establishes a fresh timing baseline. Only strictly subsequent, still-unfrozen observations accumulate grace normally from that baseline. This is the specific mechanism that makes the freeze correct even when the coordinator does not poll a frozen resource on every tick.

---

## 11. Immutable/bounded state guarantees

- `ClientResourceParitySummary.Scalar`/`Partitioned`/`Partition`/`Unavailable` are all records with compact-constructor validation (`unitScale >= 1`, all quantities `>= 0`, `current <= maximum + overflow` via `Math.addExact`, duplicate partition ids rejected, partition maps rebuilt into an unmodifiable ascending `TreeMap` regardless of input order) — directly mirroring `ClientResourceQueryResult`'s existing Phase 3B-1 validation conventions, confirmed by `ClientResourceParitySummaryTest`.
- `ClientResourceParityObservation` is a record whose compact constructor enforces `firstMismatchTick` is set if and only if the classification is `TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH` — `observationConstructorRejectsMismatchedFirstMismatchTickInvariant` proves both directions of this invariant.
- `ClientResourceParityTracker` never exposes its backing `Map` — `latest`/`advanceDeadline` return `Optional<ClientResourceParityObservation>` (a single immutable snapshot), `resourcesRequiringRecheck` returns an unmodifiable `Set`. `latestReturnsASingleImmutableObservationNeverAHistory` drives 25 observations and confirms `observationCount` is a bounded `int` reflecting only the latest state, never an accumulating list.
- No packet payload, mutable legacy array/map, `PlayerChargesComponent`, `ClientResourceSyncState`, client player reference, callback, or mutation operation is referenced anywhere in any of the 8 new production classes — confirmed by inspection (no import of any such type exists in this package).

---

## 12. Pure/client classloading boundary

Confirmed by direct inspection of all 8 new files' imports: **zero** references to `Minecraft`, `LocalPlayer`, any Fabric client event (`ClientTickEvents`, `ClientPlayConnectionEvents`, etc.), any networking API, any client-only legacy manager (`ClientManaManager`, `ClientStaminaManager`, `ClientSpellSlotManager`, `PlayerChargesComponent`, `ClientClassManager`), `Totality.LOGGER`, or any `@Environment` annotation. Every class imports only `net.minecraft.resources.Identifier` (a common, non-client-only type already used pervasively by the existing pure Resource API code, e.g. `ClientResourceQueryResult`) and `zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason` (an existing pure enum in the same parent package). All 70 new tests run as plain JUnit with no client bootstrap, exactly like `ClientResourceSyncStateTest`/`ClientResyncRequestGateTest`/`ClientResourceQueryResultTest`.

No `@Environment` annotation was added to any of these classes, per the task's explicit instruction not to add it to pure classes.

---

## 13. Tests added

70 new tests across 5 files (572 pre-existing + 70 new = 642 total, all passing):

| File | Count | Covers (test-plan items) |
|---|---|---|
| `ClientResourceParitySummaryTest` | 12 | Validation/immutability of the summary types (supports items 15-16 and general shape guarantees) |
| `ClientResourceScalarParityComparatorTest` | 9 | Items 1-4, 6-7 |
| `ClientSpellSlotParityComparatorTest` | 10 | Items 8-18 |
| `ClientRageParityPolicyTest` | 11 | Items 19-25 |
| `ClientResourceParityTrackerTest` | 28 | Items 26-46 |

Item 47 (all existing 572 tests remain unchanged and passing) is confirmed by the full-suite run below — no existing test file was read, modified, weakened, or removed.

---

## 14. Validation results

Run from `feature/general-resource-api`, in order:

- **`compileJava`/`compileTestJava`**: `BUILD SUCCESSFUL`. No compiler warnings.
- **`test`** (forced `--rerun`): `BUILD SUCCESSFUL`. **Exact total: 642 tests, 0 failed, 0 errors, 0 skipped** (summed directly across every `build/test-results/test/*.xml`'s `tests`/`skipped`/`failures`/`errors` attributes).
- **New tests added**: 70 (12+9+10+11+28), confirmed by `grep -c '@Test'` across the five new test files; 642 − 70 = 572, matching the Phase 3B-1 baseline exactly.
- **`runDatagen`**: `BUILD SUCCESSFUL in 14s`. `total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0` — zero generated-file impact, as expected for a pure-Java-only change.
- **`git status --short src/main/generated`** after `runDatagen`: the identical 21 pre-existing modified files (noise settings, loot tables, gear recipes) plus the untracked `.cache/` directory — no new or additionally-changed generated file.
- **`build`**: `BUILD SUCCESSFUL in 3s` (`UP-TO-DATE` for `test`, fresh `jar`/`sourcesJar`/`assemble`/`check`). Only pre-existing Gradle/Loom deprecation notices (unrelated to this change, present before this session).
- **`git diff --check -- src/main/java src/test/java`**: no output — no whitespace errors in any new or existing file.

---

## 15. Known limitations deferred to Phase 3B-2B

- No real legacy reader reads `ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager`/`PlayerChargesComponent` — every test in this slice constructs synthetic `ClientResourceParitySummary` values directly.
- No wiring to `ClientResourceService`/`ClientResourceSyncManager`'s `applyFull`/`applyDelta` — nothing in this slice reads the real generic façade.
- No `END_CLIENT_TICK` registration, no notification hooks on the four legacy classes, no lifecycle-reset wiring to `ClientResourceSyncManager.clear()`'s call sites.
- No derivation of `rageExpectedForPlayer` from real `ClientClassManager` state — Phase 3B-2A only defines the pure function that will consume that boolean once Phase 3B-2B supplies it.
- No logging of any kind (Phase 3B-2C scope).
- No debug command, telemetry export, or consumer migration (Phase 3B-3/3C scope).

---

## 16. Confirmation: no integration, logging, legacy manager, authority, gameplay, consumer, or visual behavior changed

- **Integration**: zero new call sites into any of the 8 new classes from anywhere in `src/main` — nothing calls `ClientResourceParityTracker`/the comparators/`ClientRageParityPolicy` except this slice's own tests.
- **Logging**: no `Totality.LOGGER` call, no log-message-formatting helper, anywhere in the 8 new classes.
- **Legacy managers**: `ClientManaManager.java`, `ClientStaminaManager.java`, `ClientSpellSlotManager.java`, `PlayerChargesComponent.java` are byte-for-byte unchanged (not read by this diff at all beyond this session's earlier audit reading).
- **Authority/gameplay**: no adapter, definition, component, server-tick, or packet-handling file was modified; nothing in this slice can mutate any Resource value.
- **Consumers/visual output**: no HUD, screen, menu, radial, or tooltip file was modified; nothing new renders or displays anything.
- **`TotalityClient`/`TotalityClientPacketHandlers`/`ClientResourceSyncManager`/`ClientResourceSyncBridge`/`ClientClassManager`**: all confirmed untouched by this diff.

## 17. Confirmation: unrelated files and `/Inspiration Mods` untouched

- `/Inspiration Mods` was not read, searched, or referenced at any point during this implementation.
- The pre-existing "known unrelated" working-tree entries (modified generated datagen JSON under `src/main/generated/data/**`, `Context/Trading Test/*.png`, `logs/`, `src/main/generated/.cache/`, the five Phase 3A/3B1 review ZIPs under `Context/Audit/Review Bundles/`) were not modified, staged, committed, reset, restored, or cleaned — `git status --short` before and after this implementation shows the identical set of these entries.
- The Phase 3B-2 readiness audit document was preserved exactly as written by the prior audit pass; this report is a new, separate file.

---

# EXTERNAL REVIEW CORRECTION PASS (2026-07-25)

A narrow, targeted correction pass applied after external review of the Phase 3B-2A implementation
above. Scope: exactly the six corrections below, plus the Phase 3B-2B integration-decision
documentation the review requested be recorded now. No client integration, no lifecycle hooks, no
Phase 3B-2B/3B-2C/3B-3/3C, and no change to Resource authority, gameplay, networking, or visual
behavior. This section's conclusions **override** any conflicting statement made earlier in this
report or in the Phase 3B-2 readiness audit — later corrections take precedence, per this task's
explicit instruction; the readiness audit itself was not rewritten.

## C1. Unit-scale comparison correction + legacy-overflow validation

**Defect:** `ClientResourceScalarParityComparator`/`ClientSpellSlotParityComparator` stored
`unitScale` on every summary but never read it before comparing, and only checked the *generic*
side's overflow — allowing e.g. `generic{100,100,scale=1}` vs `legacy{100,100,scale=1000}` to be
classified `MATCH`, or a legacy-side overflow to pass through uninspected.

**Fix:**
- `ClientResourceScalarParityComparator` now defines `CANONICAL_UNIT_SCALE = 1L` and requires
  **both** sides' `unitScale()` to equal it exactly (never merely equal to each other) before any
  numeric comparison — a non-canonical scale on either side, or two sides sharing the same
  non-canonical scale, is `MODEL_MISMATCH`. The overflow check now inspects **both**
  `generic.overflowUnits()` and `legacy.overflowUnits()` (`!= 0` on either → `MODEL_MISMATCH`),
  not just the generic side as before.
- `ClientSpellSlotParityComparator` defines its own `CANONICAL_UNIT_SCALE = 1L` and checks both
  `Partitioned.unitScale()` values immediately after shape confirmation, before any per-level loop.
  Per-partition overflow checks (both sides) and the exact remaining-slot mapping/levels-1-10 checks
  are unchanged.
- `ClientRageParityPolicy`'s sparse-0/0 check (`isExpectedSparseZeroZero`, renamed from
  `isZeroZeroScalar`) now requires `currentUnits==0 && maximumUnits==0 && overflowUnits==0 &&
  unitScale==1` — all four, not just the first two — before classifying `EXPECTED_SEMANTIC_DIFFERENCE`;
  a malformed scale or nonzero overflow on that fallback now falls through to an ordinary `MISMATCH`.
- No `UNIT_SCALE_MISMATCH` classification was introduced — every scale contradiction folds into the
  existing `MODEL_MISMATCH` vocabulary, per the review's explicit instruction.

**Tests added:** `differentUnitScalesBecomeModelMismatchEvenWhenNumbersAgree`,
`bothSidesSharingTheSameNonCanonicalScaleStillBecomesModelMismatch`, `legacyOverflowAloneBecomesModelMismatch`,
`canonicalUnitScaleConstantIsOne` (`ClientResourceScalarParityComparatorTest`);
`differentUnitScalesBecomeModelMismatchEvenWhenLevelsAgree`,
`bothSidesSharingTheSameNonCanonicalScaleStillBecomesModelMismatch`, `canonicalUnitScaleConstantIsOne`
(`ClientSpellSlotParityComparatorTest`); `nonBarbarianAbsentRageWithLegacyWrongScaleIsNotExpected`,
`nonBarbarianAbsentRageWithLegacyOverflowIsNotExpected` (`ClientRageParityPolicyTest`).

## C2. Frozen mismatch recheck discovery

**Defect:** `pendingDeadlineRechecks()` excluded any resource whose `graceFrozen` was `true` —
making a frozen transitional mismatch invisible to the one set the report told Phase 3B-2B to
revisit, so the coordinator would never learn `PENDING_RESYNC` had ended for it.

**Fix:** Renamed to `resourcesRequiringRecheck()`. Now returns every `TRANSITIONAL_MISMATCH`
resource **regardless of `graceFrozen`** — frozen and unfrozen transitional mismatches are both
included. `PERSISTENT_MISMATCH` is (newly, deliberately) **excluded**: it no longer needs continued
*deadline* advancement (the deadline has already been reached), though Phase 3B-2B's full four-resource
per-tick polling design (see C-Integration below) still freshly compares it every tick regardless —
that recovery path runs through `observe(...)` directly, not through this discovery set. `EXACT_MATCH`,
`GENERIC_NOT_READY`, `MODEL_MISMATCH`, `EXPECTED_SEMANTIC_DIFFERENCE`, `NOT_APPLICABLE` remain excluded.
Still returns an immutable `Set` snapshot; the backing map is never exposed.

**Tests added/updated:** `resourcesRequiringRecheckIncludesUnfrozenTransitionalMismatch`,
`resourcesRequiringRecheckIncludesFrozenTransitionalMismatch` (new — proves the fix directly),
`resourcesRequiringRecheckExcludesNonMismatchClassifications`,
`resourcesRequiringRecheckExcludesModelMismatch`, `resourcesRequiringRecheckExcludesPersistentMismatch`,
`resourcesRequiringRecheckReturnsImmutableSnapshot`. The old test
`pendingDeadlineRechecksExcludesFrozenAndNonMismatchResources`, which asserted the now-incorrect
exclusion behavior, was **removed** and replaced by the tests above.

## C3. Resync freeze across skipped ticks

**Defect:** the tracker decided whether to add the elapsed delta using only the *current* call's
`pendingResync` value. If the coordinator did not call `observe`/`advanceDeadline` on every single
frozen tick (a real, expected pattern — Phase 3B-2B is not required to poll a frozen resource every
tick), the first call after resuming would compute a delta spanning the *entire* frozen gap
(including ticks never observed at all) and charge it in full to `graceTicksConsumed` — incorrectly
counting frozen time as consumed grace.

**Fix:** `applyMismatch` now captures `wasFrozen = state.graceFrozen` (the *previous* observation's
frozen status) before processing the current call. The specific **frozen-to-unfrozen transition**
(`wasFrozen == true && pendingResync == false` for this call) contributes **zero** newly-consumed
grace — it only updates `lastObservedTick` to establish a fresh baseline. Only strictly subsequent,
still-unfrozen calls accumulate `graceTicksConsumed` normally from that baseline. Verified against
both examples in the correction task:

- *Zero grace consumed before freezing*: mismatch at T=10 (unfrozen); frozen at T=11; no calls
  T=12-99; resumption at T=100 (zero newly consumed, still `TRANSITIONAL_MISMATCH`); T=101 (one
  tick consumed, still transitional); T=102 (two ticks consumed, `PERSISTENT_MISMATCH`) — test
  `skippedFrozenSpanWithZeroGraceConsumedBeforeFreezingDoesNotCountTheGap`.
- *One grace tick consumed before freezing*: mismatch at T=10; unfrozen continuation at T=11 (one
  tick consumed); frozen at T=12; no calls T=13-99; resumption at T=100 (remains at exactly one
  consumed tick); T=101 (second tick consumed, `PERSISTENT_MISMATCH`) — test
  `skippedFrozenSpanWithOneGraceTickConsumedBeforeFreezingPreservesThatOneTick`.

Also added: `frozenPersistentMismatchRemainsPersistentAcrossASkippedSpan` (a mismatch already
`PERSISTENT_MISMATCH` before freezing stays persistent across a freeze-then-long-skip-then-resume
cycle — grace bookkeeping pausing never regresses an already-reached classification),
`exactMatchClearsAMismatchEvenWhileFrozen` (retained/re-verified), and
`decreasingTickStillSafelyIgnoredAfterAFreezeResumption` (monotonicity still holds across a freeze
transition). The pre-existing test `graceResumesFromRemainingDurationAfterPendingResyncEnds` asserted
the old (incorrect) "resumption call counts as the first of the remaining grace ticks" behavior — it
was **updated in place** to assert the corrected sequence (resumption call consumes zero, then two
more real ticks are needed to reach the deadline), not removed, since the scenario it covers (freeze
then prompt resumption) remains a valid and important case.

## C4. `advanceDeadline` naming/semantic clarification

**Defect:** `advance(...)`'s name and surrounding prose did not clearly distinguish "reuse stored
summaries and check the deadline" from "perform a fresh parity comparison" — risking a future
Phase 3B-2B implementer using it as the primary per-tick integration route instead of the intended
narrow diagnostic helper.

**Fix:** Renamed `advance` → `advanceDeadline` throughout the production class and all tests. Its
Javadoc now states explicitly: it reuses stored summaries verbatim; it performs **no** fresh
comparison of any kind; it must **not** be used as the primary Phase 3B-2B integration route; a
caller performing a real recheck must first read current generic/legacy state and run the
appropriate comparator, then call `observe` with the fresh outcome. No callback, supplier, client
reader, or manager import was added to the pure tracker — it remains exactly as
Minecraft/Fabric-independent as before. The pure ability to prove a deadline can advance without any
source-side notification (when values are known unchanged) is preserved via
`advanceDeadlineEscalatesWithoutAnyNewSourceNotification`/`advanceDeadlineOnUnobservedResourceReturnsEmpty`/
`advanceDeadlineOnNonMismatchStateIsANoOp` (renamed from their `advance*` equivalents, behavior unchanged).

## Phase 3B-2B integration decision (documented only — not implemented in this pass)

Recorded per the external review's explicit request, to guide (not preempt) Phase 3B-2B:

- Phase 3B-2B should have its coordinator poll all **four** eligible parity pairs
  (`totality:mana`, `totality:stamina`, `totality:spell_slots`, `totality:rage`) once per
  `END_CLIENT_TICK`.
- Each poll reads current generic state (via the Phase 3B-1 façade, `ClientResourceService`) and
  current legacy state (via new, narrow read-only legacy readers — not built in this pass), producing
  fresh immutable `ClientResourceParitySummary` values for both sides.
- Each poll runs the appropriate comparator (`ClientResourceScalarParityComparator`,
  `ClientSpellSlotParityComparator`, or `ClientRageParityPolicy`) against those fresh summaries.
- Each poll submits the fresh `ClientResourceParityOutcome` plus both summaries to
  `ClientResourceParityTracker.observe(...)` — this is the **primary** integration route.
- This is bounded, deliberate steady-state work: at most four small comparisons per client tick, every
  tick, not zero work and not unbounded work.
- This design deliberately avoids adding any diagnostic notification hook to `ClientManaManager`,
  `ClientStaminaManager`, `ClientSpellSlotManager`, or `PlayerChargesComponent` — none of these four
  legacy classes needs to know the parity system exists at all.
- Avoiding those hooks also prevents a missed/forgotten manager-update hook from becoming a silent
  parity blind spot — polling reads whatever the manager's current state is at poll time,
  unconditionally, rather than depending on every mutation call site remembering to notify.
- No source manager is ever modified merely to support diagnostics.
- `advanceDeadline(...)` remains available as a pure, testable helper for the narrower case of
  re-checking a deadline without a fresh comparison, but is **not** the primary route described above.

## Strengthened observation invariants (Correction 5)

`ClientResourceParityObservation`'s compact constructor now additionally enforces:
- `graceFrozen` may be `true` only alongside `TRANSITIONAL_MISMATCH` or `PERSISTENT_MISMATCH` —
  rejected (`IllegalArgumentException`) for any other classification.
- When `firstMismatchTick` is present, it must be `>= 0` and `<= lastObservedTick`.
- The pre-existing invariants (non-mismatch classifications require `firstMismatchTick == null`;
  mismatch classifications require it non-null) are unchanged.

**Tests added:** `exactMatchWithGraceFrozenTrueIsRejected`, `negativeFirstMismatchTickIsRejected`,
`firstMismatchTickAfterLastObservedTickIsRejected`, `validFrozenTransitionalObservationIsAccepted`,
`validFrozenPersistentObservationIsAccepted`.

## Saturating observation counts (Correction 6)

`observationCount` increments now route through a new package-private `saturatingIncrement(int)`
helper on `ClientResourceParityTracker` (package-private specifically so a test can exercise the
boundary directly, without driving billions of real observations): increments normally below
`Integer.MAX_VALUE`, holds at `Integer.MAX_VALUE` once reached, never wraps negative. Applied to both
the mismatch-continuing branch and the non-mismatch same-classification-repeat branch.

**Tests added:** `saturatingIncrementIncrementsOrdinaryValuesNormally`,
`saturatingIncrementStopsExactlyAtIntegerMaxValue`, `saturatingIncrementNeverWrapsNegativeOnceAtTheCeiling`
(direct boundary tests, per the task's explicit "do not attempt billions of loop iterations"
instruction), plus `mismatchObservationCountAtIntegerMaxValueIsARepresentableObservation`/
`nonMismatchObservationCountAtIntegerMaxValueIsARepresentableObservation` (confirming the record
itself accepts the ceiling value) and `observationCountIncrementsAcrossOrdinaryBoundedRunsWithoutBecomingNegative`
(a bounded 1000-tick sanity run).

## Tests added (this correction pass)

30 new tests across the existing five test files (642 pre-existing + 30 new = 672 total, all
passing); one pre-existing test (`graceResumesFromRemainingDurationAfterPendingResyncEnds`) was
updated in place to assert the corrected sequence; one pre-existing test
(`pendingDeadlineRechecksExcludesFrozenAndNonMismatchResources`) was removed, its coverage replaced
by six new, more precise tests. No other existing test was weakened or deleted.

| File | New test count (this pass) |
|---|---|
| `ClientResourceParitySummaryTest` | 0 (unchanged) |
| `ClientResourceScalarParityComparatorTest` | 4 |
| `ClientSpellSlotParityComparatorTest` | 3 |
| `ClientRageParityPolicyTest` | 2 |
| `ClientResourceParityTrackerTest` | 21 |

## Final validation

- **`compileJava`/`compileTestJava`**: `BUILD SUCCESSFUL`. No compiler warnings.
- **`test`** (forced `--rerun`): initial run surfaced exactly one failure —
  `graceResumesFromRemainingDurationAfterPendingResyncEnds`, whose assertions encoded the
  pre-correction freeze semantics; updated in place per C3 above. Second run:
  **`BUILD SUCCESSFUL`. Exact total: 672 tests, 0 failed, 0 errors, 0 skipped.**
- **Composition**: 642 pre-existing (572 Phase 1-through-3B-1 + 70 original Phase 3B-2A) + 30 new
  correction-pass tests = 672.
- **`runDatagen`**: zero generated-file impact, `old count: 349, new count: 349, written: 0` —
  unchanged, since this correction pass touches no datagen provider.
- **`build`**: `BUILD SUCCESSFUL`.
- **`git diff --check -- src/main/java src/test/java`**: exit 0, no whitespace errors.
- No compiler, test, datagen, or build warning beyond the pre-existing Gradle/Loom deprecation
  boilerplate (present before this session, unrelated to this correction).

## Confirmation: no integration, logging, legacy manager, authority, gameplay, consumer, networking, or visual behavior changed

- **Client integration**: none added — no `END_CLIENT_TICK` registration, no packet handler, no
  lifecycle hook, anywhere in this correction pass.
- **Legacy managers**: `ClientManaManager.java`, `ClientStaminaManager.java`,
  `ClientSpellSlotManager.java`, `PlayerChargesComponent.java`, `ClientClassManager.java` remain
  byte-for-byte unchanged.
- **Resource consumers**: none migrated — no HUD/screen/radial/tooltip/movement file touched.
- **Networking/gameplay/visual behavior**: unchanged — every file touched by this correction pass is
  one of the 8 pure production classes or their 5 test files, none of which has ever had a gameplay,
  networking, or rendering effect.
- **Phase 3B-2B**: not started — no real legacy reader, no coordinator, no `ClientClassManager`
  wiring, no logging, no debug command exists anywhere in this repository as of this correction pass.
- **Unrelated files and `/Inspiration Mods`**: untouched — confirmed by this pass's own final
  `git status --short`, identical to the pre-correction baseline plus the two modified audit/report
  files and the modified/added source and test files listed above.

## Stop point — status (correction pass)

- Correction implementation, tests, validation, and this report update are complete.
- Nothing was staged, committed, or pushed.
- The existing final review bundle ZIP was not modified.
- Phase 3B-2B was not started.

---

# EXTERNAL REVIEW ACCEPTANCE (2026-07-25)

The Phase 3B-2A correction review bundle
(`Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2A_CORRECTION_REVIEW_BUNDLE.zip`) was
inspected as part of this dated review-acceptance pass.

**Bundle contents confirmed:**
- The correction bundle contained **59 entries**: **58 checksummed content files** plus **1
  checksum manifest** (`PHASE_3B2A_CORRECTION_SHA256SUMS.txt`, excluded from its own checksum list
  by design).
- **All 58 checksums passed** — verified via `sha256sum -c PHASE_3B2A_CORRECTION_SHA256SUMS.txt`
  from the extracted bundle root.
- `sha256sum -c` **returned exit 0 with no format-warning output** — the manifest contains only
  strict `<sha256><two spaces><relative/path>` lines, with explanatory text kept in the separate
  `PHASE_3B2A_CORRECTION_CHECKSUM_README.txt`.
- The **complete corrected patch** (`PHASE_3B2A_CORRECTED_COMPLETE_DIFF.patch`) applied cleanly
  (`git apply --check` and a real `git apply`, both exit 0) against
  `2fbdee92f28c23614701ad04a44533d065c5342d` in an isolated detached worktree, and the resulting
  15 files were confirmed content-identical to the live repository.
- The **correction-only patch** (`PHASE_3B2A_EXTERNAL_REVIEW_CORRECTION_DIFF.patch`) was applied
  against the bundle's `original_review/` copies and confirmed to reproduce the corrected live
  files exactly (content-identical, modulo the expected CRLF/LF checkout artifact) — proving the
  patch accurately transforms the original reviewed state into the corrected state.

**All six external-review correction categories accepted:**
1. Canonical unit-scale (`= 1`) and bilateral overflow validation — both
   `ClientResourceScalarParityComparator` and `ClientSpellSlotParityComparator` now require each
   side's `unitScale()` to equal the canonical constant independently, and check
   `overflowUnits() != 0` on both sides; `ClientRageParityPolicy`'s sparse-fallback check requires
   the same on the legacy side before classifying `EXPECTED_SEMANTIC_DIFFERENCE`.
2. Frozen mismatch recheck discovery — `resourcesRequiringRecheck()` now includes frozen
   `TRANSITIONAL_MISMATCH` resources, correctly excludes `PERSISTENT_MISMATCH`.
3. Skipped frozen-span handling — the frozen-to-unfrozen transition call consumes zero grace
   regardless of how long or how sparsely-polled the frozen span was.
4. Fresh comparison versus deadline advancement — `advanceDeadline` (renamed from `advance`) is
   documented as a narrow diagnostic helper, not the primary Phase 3B-2B integration route.
5. Observation invariants — `ClientResourceParityObservation` rejects `graceFrozen` outside a
   mismatch-tracking classification and rejects an out-of-range `firstMismatchTick`.
6. Saturating observation counts — `observationCount` increments never wrap negative, holding at
   `Integer.MAX_VALUE`.

**No remaining Phase 3B-2A blocker was found.**

**Phase 3B-2A is approved for commit.**

**Phase 3B-2B has not started.**

---
