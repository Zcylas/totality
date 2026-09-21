# Totality — Universal Class-Change Resource Reconciliation — Implementation Report (2026-09-16)

## 1. Task

Design and implement a universal, class-agnostic reconciliation lifecycle so that any authoritative
change to class ownership or class level correctly reconciles all class-derived state — triggered by a
manual-testing crash after `/totality resetall` + `/totality showclass` + Wizard selection + a later
Barbarian re-acquisition at a low class level.

## 2. Baseline

- Branch: `feature/general-resource-api`
- HEAD before and after this task: `8329af8cad437ef1e41d287f9a32f597eee10ffd` (`feat(classes): support
  per-class subclasses and class tab leveling`) — **unchanged**; no commit was made.
- Dirty paths before this task: **109** (pre-existing, unrelated). Dirty paths after this task's edits:
  **120** (109 pre-existing + 2 new files + 9 modified files, one of which —
  `TotalityCommands.java` — was already among the 109 pre-existing dirty paths; see §8 "mixed-hunks
  file" below).
- None of the 109 pre-existing dirty paths were touched, reset, or absorbed by this task.

## 3. Root cause

`/totality resetall` does **not** touch `PlayerClassComponent` at all (confirmed by reading
`TotalityCommands.java`: it resets `PlayerStats`, skills, masteries, non-default abilities, and
Mana/Stamina, but never `classLevels`). `/totality showclass` does — it calls
`PlayerClassComponent.resetClass()`, clearing `classLevels`/`subclassIds`/`covenantId` — but that call
had **zero accompanying reconciliation**: no resource-grant re-evaluation, no spell-slot recalculation,
nothing.

`totality:rage`'s Generic Resource grant (`BarbarianRageResources`) is `CLASS`-owned with removal policy
`REMOVE_STATE` — but the only place that ever invoked `BarbarianRageResources.reconcile` /
`ResourceGrantReconciler.reconcile` for a player was:
- `SelectClassHandler`, gated behind `if (classId.equals(BARBARIAN_ID))` (first-time class selection),
- `BarbarianClass`'s `ClassLevelUpRegistry` handler (`BarbarianRageAbility.updateChargePool`, which only
  self-heals via `ensureInstantiated` when Rage state is **absent** — a no-op once state already
  exists, stale or not),
- ordinary join/respawn/dimension-transfer (`BaselineResourceLifecycleEvents`).

**None of these ever ran on the class-loss path.** So the exact reproduction is:

1. A level-18 Barbarian has `totality:rage` Generic Resource state at `current=6, maximum=6`
   (`RAGE_CHARGES[17] == 6`).
2. `/totality resetall` — no-op for `PlayerClassComponent`/Rage.
3. `/totality showclass` — `PlayerClassComponent.resetClass()` clears the class map. **Rage's
   Generic Resource state is never touched.** It is now an orphaned `current=6, maximum=6` with no
   grant source at all.
4. Player selects Wizard (`SelectClassHandler`) — not Barbarian, so the old
   `if (classId.equals(BARBARIAN_ID))` branch never runs. Rage state is still untouched (still
   `current=6`, stale).
5. Player levels Wizard — Wizard's own `ClassLevelUpRegistry` handler doesn't touch Rage.
6. Player multiclasses back into Barbarian at level 1 (`AddClassLevelHandler.addClassLevel` +
   `ClassLevelUpRegistry.fire` → `BarbarianClass`'s handler → `BarbarianRageAbility.updateChargePool` →
   `BarbarianRageResources.ensureInstantiated`). Because `state.hasState(RAGE)` is **already true**
   (the stale value from step 1), `ensureInstantiated`'s `!state.hasState(...)` guard is false, so
   **no reconciliation runs** and the stale `current=6` survives untouched.
7. `updateChargePool` marks Rage dirty. The next sync tick queries Rage: `current=6`,
   `maximum` resolves fresh for the *new* level-1 Barbarian → `RAGE_CHARGES[0] == 2`. `queryOutcome`
   builds a `ResourceScalarWireSnapshot` with `currentUnits=6 > maximumUnits=2` → the record's own
   canonical constructor throws
   `IllegalArgumentException: currentUnits must not exceed maximumUnits + overflowUnits` → crash.

This matches the reported stack trace (`ResourceScalarWireSnapshot` ← `ResourceSyncManager.queryOutcome`
← `sendDelta` ← `flush`) exactly, and confirms the task's leading hypothesis (stale class-owned
Generic Resource state surviving a class-reset/removal path) as the actual, sole cause. The
newly-implemented multiclass system itself is not at fault — a clean-world Wizard → Barbarian
multiclass never creates or removes Rage's grant mid-flight, so the stale-reactivation gap never
surfaces there.

## 4. Class-mutation entry points audited

| Entry point | Mutates `PlayerClassComponent` via | Previously ran resource/spell-slot reconciliation? |
|---|---|---|
| `SelectClassHandler` (first-time class selection) | `selectClass` | Partially — `SpellSlotRecalculator.recalculate` always; Rage-specific `registerChargePool` **only** if the selected class was Barbarian |
| `AddClassLevelHandler` (multiclass / level-up) | `addClassLevel` | `SpellSlotRecalculator.recalculate` always; `ClassLevelUpRegistry.fire` runs each class's own registered per-class-level-up callback (Barbarian's calls `updateChargePool`, which self-heals only when state is *absent*) |
| `SelectSubclassHandler` (subclass choice on an already-owned class) | `selectSubclass` | No resource/spell-slot reconciliation at all (no subclass-owned resource exists today, so this was latent, not yet a live bug) |
| `/totality showclass` command | `resetClass()` | **None whatsoever** — the actual bug |
| `/totality resetall` / `resetstats` / `resetlevel` | *(none — confirmed these never call any `PlayerClassComponent` mutator)* | N/A — out of scope; class ownership is unaffected by these commands |

`resetall` intentionally does not reset class ownership (it resets stats/skills/masteries/abilities/
Mana/Stamina/HP only) — this task did not change that product semantic, since the discovered bug did
not require it: the bug is in `showclass`'s missing reconciliation, not in `resetall`'s scope.

## 5. Universal reconciliation seam

Two new, small, class-agnostic classes:

- **`zcylas.totality.api.rpg.resources.integration.ResourceGrantReconciliation`** — the one shared
  "reconcile every registered `ResourceGrantProvider` against `player`'s live state, then mark
  whatever changed dirty" facade. Both `PlayerBaselineResources.reconcile` and
  `BarbarianRageResources.reconcile` previously duplicated an identical private `ResourceGrantReconciler`
  construction + instantiated/removed dirty-marking loop; both now delegate to this one shared instance
  instead (pure deduplication, behavior-preserving — see §9 test evidence).
- **`zcylas.totality.api.rpg.classes.ClassChangeReconciler`** — the universal post-class-mutation
  lifecycle seam. `reconcile(ServerPlayer player)`:
  1. `ResourceGrantReconciliation.reconcileAndSync(player)` — re-evaluates **every** registered
     `ResourceGrantProvider` (Rage's `BarbarianRageResources` included, and any future class-owned
     provider — Ki, Pact Magic, etc. — with zero code changes needed here) against the player's
     *current* class ownership. A lost grant is removed per its own declared `ResourceRemovalPolicy`
     (`REMOVE_STATE` for Rage); a freshly (re)granted resource is instantiated per its own declared
     `ResourceGrantInitialization` (`AtMaximum` for Rage) — never a carried-over stale value.
  2. `clampScalarResourcesAboveResolvedMaximum(player)` — a defensive safety net: for every
     `GENERIC_COMPONENT` **scalar** resource the player still has live state for (i.e. one step 1 did
     not touch because its grant survived the mutation), if `current` now exceeds the freshly resolved
     maximum, clamps it via the pre-existing (previously zero-production-caller)
     `PlayerResourceService.reconcileMaximum(..., MaximumChangePolicy.CLAMP_CURRENT, ...)`. No current
     production class-derived maximum table can actually decrease while its grant stays continuously
     active (`RAGE_CHARGES` is monotonic, matching `BarbarianRageAbility`'s own prior documented
     reasoning), so this has no live trigger today — it closes the gap generically rather than trusting
     every future maximum table to stay monotonic forever.
  3. `SpellSlotRecalculator.recalculate(player)` — the existing multiclass spell-slot derivation,
     folded in here instead of being called separately at each site.

`ClassChangeReconciler` has **zero knowledge of Rage, Ki, Pact Magic, or any other specific resource** —
there is no `if (classId.equals(BARBARIAN_ID))` branch anywhere in it, and there must never be one
added. It is called from exactly four places, all of which already mutate `PlayerClassComponent`
authoritatively:

| Call site | What changed |
|---|---|
| `SelectClassHandler.handle` | Removed the hardcoded `if (classId.equals(BARBARIAN_ID)) BarbarianRageAbility.registerChargePool(player)` resource call and the standalone `SpellSlotRecalculator.recalculate(player)` call; both are now covered unconditionally by one `ClassChangeReconciler.reconcile(player)` call. The Barbarian-specific *ability unlock* lines (unrelated to Generic Resources) are untouched. |
| `AddClassLevelHandler.handle` | Replaced the standalone `SpellSlotRecalculator.recalculate(player)` call (after `ClassLevelUpRegistry.fire`) with `ClassChangeReconciler.reconcile(player)`. |
| `SelectSubclassHandler.handle` | Added `ClassChangeReconciler.reconcile(player)` after a successful subclass application (previously ran no reconciliation of any kind — latent gap, future-proofed now). |
| `/totality showclass` (`TotalityCommands.java`) | Added `ClassChangeReconciler.reconcile(player)` immediately after `ClassComponents.get(player).resetClass()` — **this is the actual fix for the reported crash.** The command's user-facing behavior (reopen class selection) is unchanged. |

No reset/showclass **command** contains, or ever contained after this change, any resource-specific
(Rage/Ki/etc.) logic — they mutate `PlayerClassComponent` through its own authoritative API and then
call the one universal, class-agnostic seam.

## 6. Generic CLASS-owned resource behavior, before → after

| Trigger | Before | After |
|---|---|---|
| **Grant** (class selected) | Rage instantiated at `AtMaximum` — already correct for a fresh character | Unchanged — still correct, now reached uniformly through `ClassChangeReconciler` rather than a Barbarian-only branch |
| **Level change** | Maximum resolved live on every query (no stored value to go stale) — already correct | Unchanged, plus a defensive `CLAMP_CURRENT` safety net if a future maximum table ever decreases while the grant stays active |
| **Removal** (class lost) | **Never reconciled — stale state silently survived** | `REMOVE_STATE` now actually runs: the resource is deleted the moment its last grant source disappears |
| **Reacquisition** (class regained, possibly at a different level) | Silently inherited the stale value if state already existed (`ensureInstantiated`'s absent-only guard) | State was already removed by the step above, so reacquisition is a genuine first instantiation at the *freshly resolved* maximum — no stale value can survive |

## 7. Maximum-decrease clamping

`PlayerResourceService.reconcileMaximum` / `reconcileMaximumGenericState` already existed, fully
implemented and unit-tested (`PlayerResourceServiceMaximumResolutionTest`), but had **zero production
callers** anywhere in the codebase. This task did not need to invent new logic for it — only to (a) give
it its first production caller, inside `ClassChangeReconciler`'s defensive sweep, and (b) add a
regression test proving it produces genuinely wire-safe state (`maximumDecreaseWithClampCurrentLeaves
StateSafeToSerializeOntoTheWire`), not merely a smaller number, since the existing test only checked the
clamped value, not wire-snapshot safety.

## 8. Reset/showclass changes — exact diff

- `TotalityCommands.java` is a **mixed-hunks file**: it already had 213 lines of pre-existing,
  unrelated diff (an `EntityArgument`/`CreditPaymentHelper` import addition and a `give`-command
  restructuring under the economy command tree) before this task began. This task's *entire* change to
  that file is two lines: one new import (`zcylas.totality.api.rpg.classes.ClassChangeReconciler`) and
  one new call (`ClassChangeReconciler.reconcile(player);`) inside the `showclass` block. See the review
  bundle's `TotalityCommands.java.pre-existing.diff` for the baseline-only portion, isolated from this
  task's two lines.
- `showclass`'s user-facing contract (Skyrim-style "reset and reopen class selection") is unchanged —
  it still clears the class, still reopens `OpenClassSelectionPayload`. The only behavioral difference
  is that class-derived Generic Resource state is now actually cleaned up when the class is cleared,
  instead of being silently left behind.
- No reset/showclass command contains any Rage/Ki/resource-specific logic — confirmed by inspection and
  by the source-scanning `DormantResourceScopeRegressionTest`/`ClientResourceParityInspectionCommandTest`
  suites, both still green.

## 9. Files changed

**New:**
- `src/main/java/zcylas/totality/api/rpg/resources/integration/ResourceGrantReconciliation.java`
- `src/main/java/zcylas/totality/api/rpg/classes/ClassChangeReconciler.java`

**Modified (production):**
- `src/main/java/zcylas/totality/api/rpg/resources/integration/PlayerBaselineResources.java` (dedup — delegates to the new facade)
- `src/main/java/zcylas/totality/api/rpg/resources/integration/BarbarianRageResources.java` (dedup — delegates to the new facade; corrected stale Javadoc claiming REMOVE_STATE was "never exercised")
- `src/main/java/zcylas/totality/networking/classes/SelectClassHandler.java`
- `src/main/java/zcylas/totality/networking/classes/AddClassLevelHandler.java`
- `src/main/java/zcylas/totality/networking/classes/SelectSubclassHandler.java`
- `src/main/java/zcylas/totality/init/TotalityCommands.java` (mixed-hunks file — see §8)
- `src/main/java/zcylas/totality/networking/resource/ResourceSyncManager.java` (observability only — see §10)
- `src/main/java/zcylas/totality/api/rpg/resources/verification/BarbarianRageMigrationVerification.java` (new dev-server-gated regression checks)

**Modified (tests):**
- `src/test/java/zcylas/totality/api/rpg/resources/integration/ResourceGrantReconcilerTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceServiceMaximumResolutionTest.java`

## 10. Observability improvement

`ResourceSyncManager.queryOutcome`'s `ScalarOutcome` construction (the exact call in the reported crash
stack trace) now catches `IllegalArgumentException` from `ResourceScalarWireSnapshot.from` and rethrows
an `IllegalStateException` carrying the resource id, the player's name/UUID, and the offending
snapshot's values, with the original exception preserved as the cause. The exception is **never
swallowed** and the invalid state is **never normalized** — this purely adds context to the same crash,
so the next occurrence of this class of bug identifies which resource and which player immediately,
instead of a bare "currentUnits must not exceed maximumUnits + overflowUnits".

## 11. Tests added/updated

1. `ResourceGrantReconcilerTest.reacquiringAClassOwnedGrantAtALowerResolvedMaximumSeedsFreshValidState
   NotAStaleHighValue` — a **synthetic** (non-Rage) resource id proves, at the `ResourceGrantReconciler`
   layer, the exact three-phase bug shape: high-maximum grant → total loss (`REMOVE_STATE`) →
   reacquisition at a lower resolved maximum → the resulting state is genuinely safe to serialize onto
   the wire (`ResourceScalarWireSnapshot.from` does not throw). Directly satisfies the task's required
   scenarios #1 and #2, generically.
2. `PlayerResourceServiceMaximumResolutionTest.maximumDecreaseWithClampCurrentLeavesStateSafeToSerialize
   OntoTheWire` — proves `reconcileMaximum`/`CLAMP_CURRENT` produces wire-safe state, not merely a
   smaller number. Satisfies scenario #3.
3. `BarbarianRageMigrationVerification` (dev-server-gated, real `ServerPlayer` via `TotalityFakePlayer`)
   gained five new checks reproducing the *exact* discovered bug end-to-end through the *real*
   `PlayerClassComponent`/`ClassChangeReconciler`/`ClassLevelUpRegistry`/`AddClassLevelHandler`-shaped
   sequence: level-18 Barbarian at Rage 6/6 → `resetClass()` + reconcile (state removed) → select
   Wizard + reconcile (no resurrection) → re-add Barbarian at level 1 + `ClassLevelUpRegistry.fire` +
   reconcile (fresh 2/2, not stale 6) → the final state is proven safe to serialize onto the wire.
   Satisfies scenarios #1, #2, #4 (a companion pre-existing check in the same suite already proves
   ordinary Wizard→Barbarian multiclass without reset still works), and #5 (level-up itself, via the
   pre-existing `RAGE_CHARGES` threshold check in the same suite).
4. Idempotence (#6) and non-class-resource isolation (#7) were already covered generically by the
   pre-existing `ResourceGrantReconcilerTest` suite (`reRunningReconciliationWithNoOwnershipChangeIs
   Idempotent`, and the file's exclusively-synthetic, non-Rage resource ids throughout) and by
   `PlayerBaselineResourcesTest`; both remain green and are exercised by the same dedup refactor.
5. Scenario #8 (prove the lifecycle is CLASS-ownership-driven, not a hardcoded Rage id) — the entire
   pre-existing `ResourceGrantReconcilerTest` suite already uses exclusively synthetic `test_grant_*`
   resource ids with fake `ResourceGrantProvider`s, never Rage; the new test added in this task
   (#1 above) follows the same convention.

## 12. Test/build results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **PASS** |
| `./gradlew compileTestJava` | **PASS** |
| `./gradlew test --tests ResourceGrantReconcilerTest --tests PlayerResourceServiceMaximumResolutionTest` (focused) | **PASS** |
| `./gradlew test` (full suite) | **PASS** — 1590 tests, 0 failures, 0 errors |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` | **PASS** — no real whitespace errors (only benign pre-existing CRLF-normalization warnings on files this task also happens to touch) |
| Bounded dedicated-server startup (`./gradlew runServer`, ~90s to first idle) | **PASS** — reached `Done (0.343s)!`, then `[ResourceFoundationVerification] All 6 self-test checks passed.`, `[BaselineResourceMigrationVerification] All 11 self-test checks passed.`, **`[BarbarianRageMigrationVerification] All 18 self-test checks passed.`** (13 pre-existing + 5 new — the new checks reproduce the exact discovered bug end-to-end and confirm it no longer occurs). Two unrelated, pre-existing verification failures were observed in the same run (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) — identical failure text to the ones documented as pre-existing in the Phase 5 report, confirming this task neither caused nor worsened them; neither references any file this task touched. |

## 13. Manual test checklist

**Manual test result: PASS (2026-09-16).** The original development world that first exposed this bug
was already left in an invalid persisted state (`current > maximum`) from *before* this fix existed, and
crashes immediately on open regardless of this correction — it is not a valid fixture for testing a
*preventative* fix, and repairing/migrating already-corrupted saves is explicitly out of scope for this
task (see §14). The relevant lifecycle (high-level Barbarian with high Rage → class reset/reselection →
Wizard → later multiclass back into a low-level Barbarian) was instead reproduced end-to-end in a fresh,
disposable world: no crash occurred, and Rage returned with a valid low-level current/maximum rather than
the stale high-level value. This matches the dedicated-server `BarbarianRageMigrationVerification`
regression (§12) and confirms the fix holds in live gameplay for newly executed class-reset/reacquisition
transitions.

**A. Clean world**
1. Start Wizard.
2. Gain class levels.
3. Multiclass into Barbarian.
4. Confirm Rage appears with the correct low-level maximum.
5. Level Barbarian enough for the Rage maximum to increase.
6. Confirm existing Rage current is preserved (not reset) across that increase.

**B. Reset reproduction** — prefer a *copy* of the old world that originally triggered the crash.
1. Become (or already be) a high-level Barbarian with Rage at a high current/maximum (e.g. 6/6).
2. `/totality resetall`, then `/totality showclass`.
3. Select Wizard.
4. Gain class levels normally.
5. Multiclass back into Barbarian at a low level.
6. Confirm **no crash**.
7. Confirm Rage shows the correct low-level maximum with no stale high-level value.

**C. Persistence**
1. Reconnect.
2. Change dimension.
3. Confirm the class-owned resource remains valid in both cases.

**D. Other resources**
1. Confirm Mana/Stamina are unaffected by any of the above (never removed/reset by class reconciliation).

## 14. Remaining known issues

- No production class-derived maximum can currently decrease while its grant stays continuously
  active, so `ClassChangeReconciler`'s defensive `CLAMP_CURRENT` sweep (step 2) has no live trigger
  today — it is forward-looking safety, proven only by direct unit tests against `reconcileMaximum`
  itself, not by an end-to-end production scenario (none exists to construct).
- The two dev-server verification failures observed (`ProvisionerEntityBackedSmokeTest`,
  `OffhandAttackVerification`) are pre-existing and unrelated (confirmed against the Phase 5 report's
  own documented baseline) — not fixed by, or introduced by, this task.
- Long-term reset-command semantics (`resetlevel`/`resetstats`/`resetall`) remain acknowledged technical
  debt, unchanged by this task per its explicit scope boundary.
- The original development world that first exposed this bug is a **pre-existing invalid save
  fixture**: its persisted `totality:rage` state was already in an invalid `current > maximum` shape
  before this fix existed, and it crashes immediately on open independent of this correction.
  Repairing or migrating saves that were already persisted in an invalid state before this fix is
  **explicitly out of scope** — this task is preventative (stops new invalid state from being created
  going forward), not retroactive save recovery. That world was not opened, modified, or otherwise
  touched by this task; the fresh disposable-world reproduction in §13 is the substitute evidence.

## 15. Confirmations

- **No Rage-specific reset patch exists anywhere.** `ClassChangeReconciler` and
  `ResourceGrantReconciliation` are both fully class/resource-agnostic; the only Rage-specific code
  removed by this task (`SelectClassHandler`'s `if (classId.equals(BARBARIAN_ID))
  BarbarianRageAbility.registerChargePool(player)`) is now redundant with, and superseded by, the
  universal call.
- Phase 6 Spell Slot migration, Ki, Pact Magic, Hit Dice, Food, and the Minecraft version migration were
  **not started**.
- **No commit was created.** HEAD remains `8329af8`.
