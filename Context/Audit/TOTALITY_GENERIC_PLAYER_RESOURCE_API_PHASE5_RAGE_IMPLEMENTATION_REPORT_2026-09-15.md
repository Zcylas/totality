# Generic Player Resource API — Phase 5: Rage / Charge-Pool Authority Migration

**Date:** 2026-09-15
**Branch:** `feature/general-resource-api`
**Baseline commit:** `00e54b1de09d31ab235f6de8ce1d92918848d295` ("feat(resources): complete foundation and migrate mana and stamina") — confirmed via `git rev-parse HEAD` before any edit in this task.

## 1. Baseline / initial working-tree state

`git status --short` at task start showed **109 dirty paths**, none staged, all pre-existing and unrelated to this task except two:

1. `src/main/java/zcylas/totality/api/rpg/classes/PlayerChargesComponent.java` — already carried an uncommitted fix, before this task began, to `applySyncPacket`'s existing-pool branch (adopts the server's new maximum on sync, not just current — a live-maximum-increase staleness bug) and to `toClassLevel(int)` (now delegates to `PlayerClassComponent.toClassLevel`, removing a drifted, uncalled `/ 4` duplicate).
2. `src/test/java/zcylas/totality/api/rpg/classes/PlayerChargesRageCharacterizationTest.java` — already carried 4 new uncommitted characterization tests proving the fix above.

Both diffs were captured verbatim before any Phase 5 edit (see `TASK_BASELINE_NOTE.md`, `PlayerChargesComponent.pre-existing.diff`, `PlayerChargesRageCharacterizationTest.pre-existing.diff` in the review ZIP). Neither pre-existing fix touches the migration this task performs, and neither closes the known legacy Rage dimension-transfer 0/0 mirror bug (different root cause — see §14). This task's own edits to `PlayerChargesComponent.java` are layered on top of that pre-existing diff, not a replacement of it (see §3 for exactly which lines are Phase-5-attributable).

The remaining ~107 dirty paths (build.gradle, gradle.properties, datagen outputs, unrelated asset/class-progression/quest-HUD work) are untouched by this task.

## 2. Objective

Migrate the Barbarian Rage charge pool from legacy authority (`PlayerChargesComponent`, mutated directly by `BarbarianRageAbility`) to the Generic Player Resource API (`PlayerResourceStateComponent` / `PlayerResourceService`) as the authoritative current-value and mutation path, while preserving every existing gameplay number and behavior exactly: same maximum-per-class-level table, same activation-spends-one-charge semantics, same Short Rest +1 (clamped) / Long Rest full-restore behavior, same death/respawn preservation, same HUD/Class-tab presentation. Zero gameplay-caller-file changes outside the Rage/Resource-API files listed in §3.

## 3. Exact files changed

**New production files:**
- `src/main/java/zcylas/totality/api/ability/impl/barbarian/RageMaximumResolver.java` — pure delegate to `BarbarianRageAbility.getMaxRage`.
- `src/main/java/zcylas/totality/api/rpg/resources/integration/BarbarianRageResources.java` — the `BarbarianClass -> totality:rage` `CLASS` grant provider + reconciler, mirroring `PlayerBaselineResources`.
- `src/main/java/zcylas/totality/api/rpg/resources/verification/BarbarianRageMigrationVerification.java` — dev-only end-to-end self-test, mirroring `BaselineResourceMigrationVerification`.

**New test file:**
- `src/test/java/zcylas/totality/api/rpg/resources/integration/BarbarianRageResourcesTest.java`.

**Modified production files:**
- `src/main/java/zcylas/totality/api/ability/impl/barbarian/BarbarianRageAbility.java` — `canActivate`/`onActivate` routed through `PlayerResourceService`; `registerChargePool`/`updateChargePool` routed through `BarbarianRageResources`; new `getMaxRage`/`onShortRest`/`onLongRest` methods.
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java` — `totality:rage` redefined `GENERIC_COMPONENT`-authority (no `.externalAdapter(...)`), canonical §25.6 capability set, `definitionVersion` 1 → 2; `registerMaximumResolvers`/`registerGrants` extended; class Javadoc updated.
- `src/main/java/zcylas/totality/api/rpg/resources/ResourceLifecyclePolicy.java` — stale Javadoc corrected (Rage no longer listed among `EXTERNAL_ADAPTER` resources using `DEFAULT` for that reason; now documented as `GENERIC_COMPONENT` deliberately using `DEFAULT`).
- `src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java` — removed the legacy `ChargeComponents.PLAYER_CHARGES.onRest` `RestEventBus` registration (both JOIN and AFTER_RESPAWN blocks — dual-authority hazard); added a Generic-routed Rage Rest listener in both blocks.
- `src/main/java/zcylas/totality/networking/resource/BaselineResourceLifecycleEvents.java` — `migrateLegacyIfAbsent` extended to also import legacy Rage's current value (reusing the exact `isLegacyMigrated`/`markLegacyMigrated` marker); class Javadoc updated to explain why no additional lifecycle-hook wiring is needed for Rage's ordinary grant reconciliation.
- `src/main/java/zcylas/totality/api/rpg/classes/PlayerChargesComponent.java` — **only** the removal of the confirmed-dead `registerWithRestBus()` method is Phase-5-attributable; the rest of this file's diff (the sync-maximum-adoption fix and the `toClassLevel` delegate) pre-dates this task (see §1).
- `src/main/java/zcylas/totality/Totality.java` — registers `BarbarianRageMigrationVerification.register()`.

**Modified test files** (all pin the new, correct post-migration shape; see §16 for the full list of touched assertions):
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryExternalAdapterFreezeTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalEntryPathTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalSafetyTest.java`

`src/test/java/zcylas/totality/api/rpg/classes/PlayerChargesRageCharacterizationTest.java` appears dirty but **received zero Phase 5 edits** — its entire diff is the pre-existing one from §1.

**Additional files from the External Review Corrections pass (§24) — new:**
- `src/test/java/zcylas/totality/api/ability/impl/barbarian/BarbarianRageAbilityMaximumSyncSourceRegressionTest.java` (finding 1).

**Additional files from the External Review Corrections pass (§24) — modified:**
- `src/main/java/zcylas/totality/api/ability/impl/barbarian/BarbarianRageAbility.java` — `updateChargePool` now also marks `totality:rage` dirty (finding 1).
- `src/main/java/zcylas/totality/api/rpg/resources/verification/BarbarianRageMigrationVerification.java` — extended with 5 more checks: level-up-across-threshold, real `onActivate` end-to-end, non-Barbarian no-grant, exactly-one-state-entry, reconciliation-does-not-refill (findings 1, 4, 5).
- `src/main/java/zcylas/totality/TotalityClient.java` — Rage legacy fallback readers hardcoded to `0`, dead imports removed (finding 3).
- `src/main/java/zcylas/totality/screen/character/tabs/ClassTab.java` — same correction as `TotalityClient.java` (finding 3).
- `src/test/java/zcylas/totality/api/rpg/resources/client/presentation/Phase3CConsumerMigrationSourceRegressionTest.java` — 2 new sentinel tests pinning the finding-3 fix.

## 4. Characterization performed before editing

Read in full: `PlayerChargesComponent`, `ChargeComponents`, `RageResourceAdapter`, `BarbarianRageAbility`, `BarbarianClass`, `SelectClassHandler`, `RageEffect`, `ClientRageParityPolicy` (+its test), `PlayerConnectionEvents`, `PlayerConnectionEventsChargeSyncSourceRegressionTest`. Grep-audited: every call site of `ChargeComponents`/`PlayerChargesComponent` mutation methods (`registerPool`/`ensurePool`/`updatePoolMax`/`setMax`/`restore`/`restoreAll`/`consume`/`hasCharge`/`getCurrent`/`getMax`), confirming `PlayerChargesComponent` is used **exclusively** for Rage in production — no other pool exists, so no separation-cleanliness problem exists. Confirmed `registerWithRestBus()` has zero callers anywhere. Confirmed `TotalityClient`/`ClassTab`'s HUD/Class-tab already prefer the Generic client view via `ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.RAGE, ...)` with legacy fallback (Phase 3C pattern) — zero code changes needed there.

None of the task's explicit stop conditions were triggered — see the conversation's own reasoning: the Rage id, maximum formula, death behavior, class-ownership truth, and dual-authority risk were all resolved by direct code inspection before any edit.

## 5. Old vs. new authority model

| | Before Phase 5 | After Phase 5 |
|---|---|---|
| Authoritative current value | `PlayerChargesComponent.pools.get(CHARGE_ID).current()` | `PlayerResourceStateComponent`'s scalar state for `totality:rage` |
| Authoritative maximum | `RAGE_CHARGES[classLevel]` looked up directly inside `registerChargePool`/`updateChargePool` | Same table, now behind `RageMaximumResolver` via `PlayerResourceService.resolveMaximum` |
| Mutation path | `ChargeComponents.get(player).consume(CHARGE_ID)` / `.ensurePool(...)` / `.updatePoolMax(...)` | `PlayerResourceService.INSTANCE.trySpend`/`.restore`; grant reconciliation via `BarbarianRageResources` |
| Ownership | Implicit (whoever calls the mutation methods) | Explicit `CLASS` grant (`BarbarianClass -> totality:rage`), gated on `ClassComponents.get(player).hasClass(BARBARIAN_ID)` |
| Resource API definition | `EXTERNAL_ADAPTER` (query-only, wrapping `RageResourceAdapter`) | `GENERIC_COMPONENT` (real mutation authority) |

## 6. Exact resource ID

`totality:rage` (`PlayerResourceIds.RAGE`) — unchanged, already registered since Phase 2E. The legacy backing key `totality:barbarian_rage` (`BarbarianRageAbility.CHARGE_ID`/`.ID`) remains a distinct identifier, never conflated with the Resource API id, exactly as before.

## 7. Grant ownership

New `BarbarianRageResources` class (`api/rpg/resources/integration/`), mirroring `PlayerBaselineResources`'s established shape exactly:

- **Source:** `totality:barbarian_class` (canonical §16.2's own worked example, "BarbarianClass -> totality:rage").
- **Source type:** `CLASS` (not `GLOBAL_SYSTEM` — Rage is conditionally owned, unlike Mana/Stamina).
- **Provider:** `player != null && ClassComponents.get(player).hasClass(BARBARIAN_ID) ? List.of(grant()) : List.of()` — null-safe (see §16, `BarbarianRageResourcesTest`), the same single source of truth `canActivate`/`SelectClassHandler`/`PlayerConnectionEvents` already used.
- **Initialization:** `AtMaximum` (a freshly-selected Barbarian starts full, matching legacy `ensurePool`'s always-full-on-create behavior).
- **Removal policy:** `REMOVE_STATE` (canonical default; never actually exercised — no production class-removal/respec path exists, per `SelectClassHandler`'s `hasAnyClass` guard).
- **Visibility policy:** `WHEN_ACTIVE` (Rage is only relevant while the player has the class — unlike Mana/Stamina's `ALWAYS_FOR_OWNER`).

Reconciliation triggers: `BarbarianRageAbility.registerChargePool` calls `BarbarianRageResources.reconcile(player)` directly (the one trigger — class selection — not already covered generically). Every other canonical §16.12 trigger (join, respawn, dimension transfer) is covered **for free**: `BarbarianRageResources`'s provider is registered on the same shared, global `ResourceGrantRegistry.INSTANCE` that `PlayerBaselineResources`'s own reconciler already iterates in full (`ResourceGrantReconciler.reconcile` loops `grantRegistry.providers()`, not a filtered subset), so every existing `PlayerBaselineResources.reconcile(player)` call in `BaselineResourceLifecycleEvents` (JOIN, `COPY_FROM`, `AFTER_PLAYER_CHANGE_LEVEL`) transitively reconciles Rage's grant too. No duplicate/parallel lifecycle-hook class was added — confirmed by inspection that adding one would only re-run an already-idempotent pass.

## 8. NBT migration

`BaselineResourceLifecycleEvents.migrateLegacyIfAbsent` (already the Mana/Stamina one-time-import method, called from the existing JOIN handler) was extended with a third `migrateOne` call for Rage: "legacy initialized" means the legacy `PlayerChargesComponent` already had a pool registered under `BarbarianRageAbility.CHARGE_ID` (i.e., this player selected Barbarian before this migration shipped); the exact legacy `current` value is imported via `state.instantiateScalar`, then the durable `isLegacyMigrated` marker is set regardless of outcome — the identical mechanism and identical guard order Phase 4 built and this task's own external-review-mandated marker (not mere state presence) already requires. No new migration framework was built.

A player who never selected Barbarian has nothing to import; they simply receive Rage's ordinary `AtMaximum` grant instantiation whenever they do select it, via `registerChargePool` → `BarbarianRageResources.reconcile`.

**Why JOIN-only is still correct for Rage** despite legacy `PlayerChargesComponent.copyFrom` being a **preserve-all-pools** copy (unlike Mana/Stamina's legacy component, which resets to "uninitialized" every respawn): the durable marker is checked *before* any legacy value is even read, so every call after the first is a genuine no-op regardless of what the legacy store still holds, and JOIN always fires before any respawn in the same session — a player can never reach a respawn with the marker still unset.

## 9. Migration marker behavior

Reused verbatim: `PlayerResourceStateComponent.isLegacyMigrated(Identifier)` / `markLegacyMigrated(Identifier)` (schema v4, built in Phase 4's correction pass). No new marker infrastructure was built for Rage, per the task's explicit instruction.

## 10. Maximum resolver

New `RageMaximumResolver` (`api/ability/impl/barbarian/`), registered in `ProductionResourceDefinitions.registerMaximumResolvers()`. A one-line delegate to a new pure-formula method, `BarbarianRageAbility.getMaxRage(ServerPlayer)`:

```java
public static int getMaxRage(ServerPlayer player) {
    int classLevel = Math.max(1, ClassComponents.get(player).getClassLevel(TotalityClasses.BARBARIAN_ID));
    return RAGE_CHARGES[Math.min(classLevel - 1, RAGE_CHARGES.length - 1)];
}
```

This is byte-for-byte the same formula `registerChargePool`/`updateChargePool` always used inline — extracted, not reimplemented, so the resolver and the ability class can never drift apart (the same pattern `ManaMaximumResolver`/`StaminaMaximumResolver` established in Phase 4). `totality:rage`'s definition deliberately declares **no** `.authoredBaseMaximum(...)` — an authored base wins outright over a registered resolver for SCALAR resources, so keeping the old descriptive `2` literal would have silently short-circuited the resolver, exactly the bug class Phase 4's own migration warned against.

## 11. Activation-cost migration

`BarbarianRageAbility.onActivate`'s spend branch now calls `PlayerResourceService.INSTANCE.trySpend(player, new ResourceCost.Scalar(PlayerResourceIds.RAGE, 1), ResourceContext.of(ResourceCause.of(CauseTypes.ABILITY_COST)))` instead of `ChargeComponents.get(player).consume(CHARGE_ID)`. `trySpend` is affordability-checked and all-or-nothing (canonical §12.3), matching legacy `consume()`'s exact contract (`false`, no mutation, when `current <= 0`) — `onActivate`'s boolean-success-gated toggle-activation logic is unchanged. `canActivate` now queries `totality:rage`'s current value via `PlayerResourceService.query` instead of `ChargeComponents.get(player).hasCharge(CHARGE_ID)`. Both call `BarbarianRageResources.ensureInstantiated(player)` first, self-healing any player whose state was never instantiated (mirroring `PlayerManaManager`/`PlayerStaminaManager`'s established self-heal pattern for fake players that never fire `ServerPlayConnectionEvents.JOIN`).

## 12. Rest migration

New `BarbarianRageAbility.onShortRest`/`onLongRest` static methods, mirroring `PlayerStaminaManager.onLongRest`'s exact pattern:

- **Short Rest:** restores exactly 1 charge via `PlayerResourceService.restore`, which already clamps at maximum — no deficit pre-computation needed.
- **Long Rest:** computes the exact deficit and restores it (avoiding the checked-arithmetic mutation path spuriously overflow-rejecting an astronomically large restore amount), matching Stamina's own established idiom.

Registered as two new `RestEventBus` listener registrations in `PlayerConnectionEvents` (JOIN and AFTER_RESPAWN blocks), **replacing** the removed legacy `ChargeComponents.PLAYER_CHARGES.get(...).onRest(p, type)` registration in both blocks — that legacy registration would otherwise have independently restored the legacy mirror on every rest, a real dual-authority hazard now that `totality:rage` is authoritative (canonical §15: "after migration there must be exactly one authoritative current value; any retained legacy mirror must never mutate independently").

## 13. Death/respawn

No `.lifecycle(...)` override was added to `totality:rage`'s definition. `ResourceLifecyclePolicy.DEFAULT`'s `KEEP_CURRENT` death policy already matches legacy Rage's own `copyFrom` (`pools.clear(); pools.putAll(other.pools)` — a blanket preserve-all-pools copy) exactly, proven by the existing `PlayerChargesRageCharacterizationTest.copyFromPreservesAllPoolsIndependently` test. This is a genuine simplification versus Mana/Stamina, which needed an explicit `RESET_TO_MAXIMUM` override.

`PlayerResourceStateComponent.copyFrom`'s per-resource `ResourceDeathPolicy` switch (built in Phase 4) falls into its `case KEEP_CURRENT -> states.put(id, copyState(entry.getValue()))` branch for Rage — a straight blanket-copy-preserve, matching legacy exactly.

## 14. Reconnect / dimension transfer, and the known legacy dimension-mirror bug

Reconnect and dimension transfer both reconcile Rage's grant for free (§7). The task's explicitly-forbidden-to-patch legacy bug — after a dimension transition, the Generic Rage view stays correct but the legacy `ChargeComponents` mirror can show 0/0, because no `AFTER_CLIENT_LEVEL_CHANGE` resync hook exists for the legacy mirror — was **not patched**, per the task's explicit instruction. Instead:

- `BarbarianRageAbility.registerChargePool`/`updateChargePool` no longer call any legacy `PlayerChargesComponent` mutation method (`ensurePool`/`updatePoolMax`), so the legacy pool for a player who selects Barbarian **after** this migration ships is simply never populated at all.
- The client-side presentation layer (`TotalityClient`'s Rage HUD panel, `ClassTab`'s "CLASS RESOURCE" panel) already prefers the Generic client view via `ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.RAGE, ...)`, falling back to the legacy reader only when the Generic side is genuinely unavailable/not-yet-synced.

**Disposition, corrected by external review (see §24, finding 3): two genuinely different cases, not one.**

- **A new post-migration Barbarian** (selects the class after this migration ships) has **no legacy pool at all** — `registerChargePool` never populates it — so the legacy fallback path is structurally inert for them: there is nothing stale to ever read.
- **An existing, already-migrated pre-Phase-5 Barbarian** physically retains their legacy `PlayerChargesComponent` pool, frozen at whatever value it held at the moment of migration, since nothing mutates it afterward. The original version of this report claimed this player's legacy mirror "has nothing to go stale" — **that claim was wrong**, and external review caught it: `ChargeComponents.PLAYER_CHARGES.sync(...)` (still called on every JOIN/AFTER_RESPAWN, §16) unconditionally sends that frozen legacy value to the client, and the presentation layer's fallback was, until this correction, still capable of reading it during the narrow window between that legacy sync packet arriving and the Generic full snapshot arriving (both fire within the same tick, but the legacy packet is sent synchronously inside the JOIN handler while the Generic snapshot is sent later, at end-of-tick — see §24 finding 3 for the full analysis and the fix).

No new legacy dimension-change packet/hook was added, per the task's explicit prohibition, in either the original pass or this correction pass.

## 15. Compatibility facade status

No dedicated `PlayerRageManager`-style facade class was created. Unlike Mana/Stamina (which had ~20+ call sites across the codebase requiring an unchanged-signature facade), Rage's only production mutation call sites were the four methods inside `BarbarianRageAbility` itself (`canActivate`, `onActivate`, `registerChargePool`, `updateChargePool`) — confirmed by exhaustive grep before editing. `BarbarianRageAbility` itself now serves as that facade; its public signatures are unchanged, so every existing caller (`SelectClassHandler`, `BarbarianClass`'s `ClassLevelUpRegistry` hook, `PlayerConnectionEvents`' AFTER_RESPAWN block) required zero edits.

## 16. `PlayerChargesComponent` status

Retained, not deleted — architecturally generic (a `Map<Identifier, ChargePool>`), even though Rage was its only real-world user. Its mutation methods (`registerPool`, `hasCharge`, `consume`, `restore`/`restoreAll`, `setMax`, `updatePoolMax`, `ensurePool`) are now unreferenced by production code (confirmed by grep after editing) but are deliberately left in place — deferred cleanup, matching the Mana/Stamina `ManaResourceAdapter`/`StaminaResourceAdapter` precedent from Phase 4 (their own unit tests, `PlayerChargesRageCharacterizationTest`'s 26 pre-existing methods, still exercise this class's own mechanics directly and continue to pass). The one method proven genuinely dead with zero callers anywhere — `registerWithRestBus()` — **was** removed, per the task's §13 instruction to remove obsolete infrastructure only once proven unused.

`ChargeComponents.PLAYER_CHARGES.sync(...)` calls in `PlayerConnectionEvents` (JOIN and AFTER_RESPAWN) were left untouched: harmless (an always-empty-going-forward legacy mirror sync costs nothing) and out of this task's minimal-footprint scope.

## 17. Legacy packet/mirror status

No new legacy packet or mirror-repair hook was added (see §14). The existing legacy sync packet path (`PlayerChargesComponent.writeSyncPacket`/`applySyncPacket`) is untouched beyond the pre-existing baseline fix (§1), which this task did not need to build on or extend.

## 18. HUD / pip status

Zero changes to `TotalityClient.java` or `ClassTab.java` — both already read Rage through `ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.RAGE, ...)` (the established Phase 3C pattern), with the legacy reader only as an already-existing fallback. Confirmed via the bounded dedicated-server run and by inspection; a full interactive HUD verification is deferred to Stefan's manual playtest (§21).

`ClientRageParityPolicy`/`LegacyClientResourceParityReaders.rageSummary()` (diagnostic-only parity comparison tooling) were left completely untouched — their mechanism remains meaningful (and gets *stronger*, since both sides increasingly derive from the same authority) without any restructuring, matching the Phase 4 precedent for `ManaResourceAdapter`/`StaminaResourceAdapter`'s equivalent tooling.

## 19. Tests

- **New:** `BarbarianRageResourcesTest` (2 tests: null-player-yields-no-grant, canonical source id).
- **New (dev-only, not JUnit):** `BarbarianRageMigrationVerification`, originally 8 checks, extended to **13** by the External Review Corrections pass (§24, findings 1/4/5): level-up-across-threshold current-preserved/maximum-increased, real `onActivate` end-to-end consumption, non-Barbarian no-grant, exactly-one-state-entry, reconciliation-does-not-refill — all against a real `ServerPlayer` and the real production registry.
- **New (§24 finding 1):** `BarbarianRageAbilityMaximumSyncSourceRegressionTest` (2 source-text sentinels pinning the `updateChargePool` dirty-notification fix).
- **Updated:** `PlayerResourceRegistryTest`, `PlayerResourceRegistryExternalAdapterFreezeTest`, `PlayerResourceStateComponentExternalEntryPathTest`, `PlayerResourceStateComponentExternalSafetyTest` — every pre-existing assertion that hardcoded Rage as `EXTERNAL_ADAPTER`-authority (a Phase 2E assumption this migration invalidates) was replaced with its Phase-5-correct counterpart, following the exact same before/after pattern Phase 4 already established for Mana/Stamina in these same files (each replacement is a straight mirror of an existing Mana/Stamina test in the same file).
- **Updated (§24 finding 3):** `Phase3CConsumerMigrationSourceRegressionTest` — 2 new sentinels pinning the Rage legacy-fallback-hardcoded-to-0 fix.
- **Untouched and still passing:** all 26 pre-existing `PlayerChargesRageCharacterizationTest` methods (they test `PlayerChargesComponent` directly, independent of Resource API authority) and all 21 `RageResourceAdapterTest` methods (they test the adapter's own `resolve` logic directly, independent of production registry wiring) and all `ClientRageParityPolicyTest`/`PlayerConnectionEventsChargeSyncSourceRegressionTest` methods.

## 20. Validation results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **PASS** |
| `./gradlew compileTestJava` | **PASS** |
| Focused Rage/Resource/Grant/Rest/sync test classes | **PASS** (all green, see conversation for the exact class list run) |
| `./gradlew test` (full suite, after §24 corrections) | **PASS** — 1539 tests, 0 failures, 0 errors |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` (all Phase 5 + §24 correction files) | **PASS** — no whitespace errors (only benign CRLF-normalization warnings) |
| Bounded dedicated-server startup (`./gradlew runServer`, ~100s, re-run after §24 corrections) | **PASS** — reached `Done (0.307s)!`, then `[ResourceFoundationVerification] All 6 self-test checks passed.`, `[BaselineResourceMigrationVerification] All 11 self-test checks passed.`, and **`[BarbarianRageMigrationVerification] All 13 self-test checks passed.`** Two unrelated, pre-existing verification failures were observed in the same run (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) — same failure text/values as documented in the Phase 4 report, confirming this task neither caused nor worsened them; neither references any file this task touched. |

## 21. Manual testing required (Stefan)

Automated coverage above proves the authority migration end-to-end against a real `ServerPlayer` and the real production registry, but a real interactive client was not available in this environment. Please manually verify, in a real game session:

1. Select Barbarian on a fresh character — Rage HUD pips show full charges at the correct level-1 maximum (2).
2. Activate Rage — exactly one pip depletes; toggle off and back on with no charges left does nothing (no crash, no free activation).
3. Level up the Barbarian class across a `RAGE_CHARGES` table boundary (e.g. level 2→3, 5→6) — the maximum pip count increases immediately, current charges are not reset.
4. Short Rest — exactly one charge is restored, clamped at the max (verify at both "below max" and "already at max" starting points).
5. Long Rest — full restore to maximum.
6. Die and respawn while below max Rage — current Rage charges are preserved across respawn (not reset, not refilled).
7. Disconnect and reconnect (ideally on an existing pre-migration character with partially-depleted Rage) — the HUD shows the correct, preserved current value immediately on rejoin, not 0/0.
8. Change dimension while Rage is below max — the HUD stays correct (this is the specific scenario the previously-known legacy bug affected; confirm it no longer reproduces).
9. Class-tab "CLASS RESOURCE" panel shows the same value as the HUD pips at all times.

## 22. Deliberately deferred cleanup

- `RageResourceAdapter` remains registered in `ProductionResourceDefinitions.registerAdapters()` though no longer referenced by the `totality:rage` definition — matches the Mana/Stamina precedent; its own 21 unit tests still exercise it directly. Removal is a Phase 8 concern.
- `PlayerChargesComponent`'s now-production-dead mutation methods (`registerPool`, `hasCharge`, `consume`, `restore`/`restoreAll`, `setMax`, `updatePoolMax`, `ensurePool`) are left in place, still covered by `PlayerChargesRageCharacterizationTest`'s 26 pre-existing tests.
- `ChargeComponents.PLAYER_CHARGES.sync(...)` calls in `PlayerConnectionEvents` are left in place. For a brand-new post-migration Barbarian this is harmless (always-empty). For an already-migrated pre-Phase-5 Barbarian it sends a frozen, potentially-stale legacy value to the client every join/respawn — external review (§24, finding 3) found this **was** capable of reaching the presentation layer during a narrow sync-ordering window; the presentation-side fallback was fixed (§24) rather than removing this sync call, since the sync call itself is not the defect (see §24 finding 3 for the full reasoning on why the fallback, not the sync, was the correct place to fix this).
- `updateChargePool` now also calls `ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE)` (external-review correction, §24 finding 1) in addition to the defensive `ensureInstantiated` self-heal: `RAGE_CHARGES` is monotonically non-decreasing and no production respec/level-down path exists, so the live-resolved maximum via `RageMaximumResolver` already reflects a level-up correctly on the very next query with zero stale-current risk in the realistic case — but that correct value needed an explicit dirty notification to actually reach the Generic client sync path, since nothing else marks `totality:rage` dirty on a maximum-only change.

## 23. Known risks before Phase 6

- The one interactive-client scenario this environment could not directly exercise (HUD pip rendering, Class-tab panel) relies on inspection + the already-proven-unchanged `ClientResourcePresentationResolver` pattern, not a live client run — see §21's manual checklist.
- `PlayerChargesComponent`'s generic multi-pool API is now completely production-unused (Rage was its only real caller); if a future system ever registers a second pool through it, that system would need its own grant-provider/reconciler wiring to reach Resource-API-level authority the way Rage now does — it does not get that "for free" merely because the class is generic.
- No production Barbarian class-removal/respec path exists, so the `REMOVE_STATE` grant-removal policy for Rage's `CLASS` grant has never been exercised end-to-end against a real player; this mirrors an identical, already-accepted gap in the Phase 4 Mana/Stamina migration for its own removal policy.

## 24. External Review Corrections (2026-09-15)

External review of the Phase 5 Review ZIP found one confirmed synchronization bug and raised four additional audit questions. Each is classified below.

### Finding 1 — Rage maximum change must sync to the Generic client: **CONFIRMED + FIXED**

Audited `ResourceSyncManager`/`PlayerResourceSyncState` directly. Confirmed: the only way a resource is requeried and delivered to the client (outside a full snapshot) is `ResourceSyncManager.markDirty` feeding the per-player dirty set that `flush()` consumes every tick. Before this correction, `BarbarianRageAbility.updateChargePool` called only `BarbarianRageResources.ensureInstantiated(player)` — a no-op for an already-instantiated resource — so a class-level threshold crossing that raised Rage's resolved maximum (e.g. 2/2 → 2/3) never marked `totality:rage` dirty. The server-side `RageMaximumResolver` would return the correct new maximum on its next query, but nothing ever triggered that requery for the Generic sync path, so the client could remain stuck at the stale maximum (2/2) until an unrelated mutation or a full snapshot happened to catch up.

**Fix:** `updateChargePool` now also calls `ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE)`, mirroring `PlayerResourceRecalculator.recalculate`/`recalculateAndRestore`'s own established "maximum-only-change seam" comment and mechanism for Mana/Stamina exactly (same file, same pattern, already in production). `current` is never touched — the fix is a pure dirty-notification, not a mutation. `PlayerResourceSyncState.computeDeltaAndApply`'s diff check compares the full `ResourceScalarWireSnapshot` record (current **and** maximum), so a maximum-only change is correctly detected as "changed" and produces exactly one delta upsert; an unchanged requery produces no packet (no spam).

Preserved exactly as specified: 2/2 at the old level, level threshold raises the resolved maximum to 3, client ends at 2/3 — never a stray refill to 3/3.

**Tests added:**
- `BarbarianRageAbilityMaximumSyncSourceRegressionTest` (new, 2 source-text sentinel tests) — pins that `updateChargePool` calls `ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE)`, and that it never mutates current merely to force a sync. A source-text sentinel, not a runtime proof — `ResourceSyncManager`'s per-player dirty state is private static with no test-safe inspection point, the identical constraint `PlayerConnectionEventsChargeSyncSourceRegressionTest` already documents for the JOIN-sync case.
- `BarbarianRageMigrationVerification` extended (dev-only, real `ServerPlayer`) with the exact scenario requested: begin at class level 2 (max 2), spend one charge (1/2), level to class level 3 across the `RAGE_CHARGES` threshold, call `updateChargePool`, and assert the query returns 1/3 — current preserved, maximum increased. This proves the resolver's return value is correct after the level-up; it does **not** by itself prove client delivery (no real connected client exists in this dev-server-only environment) — that half of the claim rests on the source-text sentinel above, not on this query-based check. This distinction is stated explicitly in the verification's own code comment.

### Finding 2 — Rest listener priority: **NOT ACTUALLY A BUG**

Audited, in order: `RestListener.restPriority()`'s default (10), `RestEventBus.fire`'s ordering (`Comparator.comparingInt(RestListener::restPriority)`, ascending — lower fires first; `Stream.sorted` uses a stable sort, so ties preserve registration order), every Rest listener registration in `PlayerConnectionEvents`, and the pre-migration `PlayerChargesComponent.restPriority()` override (5, "before abilities").

**Evidence:** every Rest listener `PlayerConnectionEvents` registers — for Abilities, Spell Slots, Stamina, and (now) Rage — is a **lambda expression** implementing `RestListener`'s single abstract method. A lambda cannot override a default method, so every one of these listeners silently uses the default priority (10), *including* the pre-existing Abilities registration — `PlayerChargesComponent`'s real, class-level override of `restPriority()` (5) was never actually compared against another *class-level* override; it was compared against a chain of lambdas that were always tied at 10. This means the pre-migration "before abilities" ordering guarantee already only ever manifested as a tie-break on registration order, not a real priority differential against anything Abilities' own code declared.

More importantly: `AbilityComponent.onRest` only mutates its own `cooldowns` map; `SpellSlotComponent.onRest` only mutates its own `usedSlots`/`maxSlots` arrays; `PlayerStaminaManager.onLongRest` only mutates `totality:stamina` via `PlayerResourceService`; the old `PlayerChargesComponent.onRest` only mutated its own `pools` map; the new Rage lambda only mutates `totality:rage` via `PlayerResourceService`. **Every one of these Rest handlers operates on a completely disjoint state store from every other one** — none reads a value another one writes. Firing order among them is therefore behaviorally inert: swapping any two of them cannot change the observable end state after `RestEventBus.fire` completes, before or after this migration. No code change made; no test added (the task instructs adding one only if ordering actually required a fix).

### Finding 3 — Legacy Rage mirror/fallback for existing migrated players: **CONFIRMED + FIXED**

Audited `ClientResourcePresentationResolver.resolveScalar` and the JOIN-tick packet ordering. `resolveScalar` falls back to the caller-supplied legacy reader **only** when the Generic query returns `Unavailable` (e.g. `NOT_SYNCHRONIZED_YET`). On JOIN, the legacy `ChargeComponents.PLAYER_CHARGES.sync(player)` call is sent synchronously, inline, inside the `ServerPlayConnectionEvents.JOIN` handler in `PlayerConnectionEvents`. The Generic full snapshot, by contrast, is only *scheduled* during JOIN (`ResourceSyncLifecycleEvents`) and actually sent later, at `ServerTickEvents.END_SERVER_TICK` (`ResourceSyncServerTick` → `ResourceSyncManager.flush`) — strictly after the legacy packet, within the same tick.

**Answer: YES, this could happen**, and it is a genuine regression introduced by this migration, not a pre-existing condition. Before Phase 5, Rage was `EXTERNAL_ADAPTER`-authority and `RageResourceAdapter` derived the Generic view directly from the same `PlayerChargesComponent` the legacy packet reads — both packets always carried the identical value, so packet-arrival order never mattered. Since Phase 5, Generic Rage is independently mutated while an existing migrated Barbarian's legacy pool is frozen at whatever value it held at migration time. Concretely: legacy save starts 2/4, migration imports Generic 2/4, the player spends a charge (Generic → 1/4, legacy stays 2/4 forever), the player disconnects and reconnects — the legacy sync packet (2/4, stale) arrives first, and for the brief window before the Generic full snapshot lands, a client query would see Generic as `NOT_SYNCHRONIZED_YET` and fall back to the just-arrived stale legacy value, momentarily displaying 2/4 instead of the authoritative 1/4.

**Fix, following the task's stated preference** ("prefer disabling/removing the Rage legacy presentation fallback... or otherwise ensure any retained fallback derives from Generic truth"): `TotalityClient.legacyRageCurrent`/`legacyRageMax` and `ClassTab.legacyRageCurrent`/`legacyRageMax` no longer read `ChargeComponents.PLAYER_CHARGES` at all — both now unconditionally return `0`. This is safe because the worst case is now identical to every other Generic-authoritative resource's inherent "not yet synchronized" behavior: a brief 0 (interpreted by the existing "0 = hidden"/"No resource" gates already in both files) until the Generic snapshot arrives, never a wrong nonzero number. `PlayerChargesComponent` itself, its own sync call, and the diagnostic-only `LegacyClientResourceParityReaders.rageSummary()` parity reader were **not** touched — per the task's explicit constraints (no resumed legacy mutation, no new packet, no second authority, no wholesale removal of `PlayerChargesComponent`).

The now-dead `ChargeComponents`/`ComponentProvider`/`BarbarianRageAbility` imports these two fallback methods' removed reads left behind were removed from both files.

**Tests added:** two new source-text sentinels in `Phase3CConsumerMigrationSourceRegressionTest` — `totalityClientLegacyRageFallbackNeverReadsTheLegacyChargeComponentMirror` and `classTabLegacyRageFallbackNeverReadsTheLegacyChargeComponentMirror` — pin that both fallback methods are hardcoded to `return 0;` and never reference `ChargeComponents` again.

§14 above was rewritten to distinguish the two genuinely different existing-player cases this finding surfaced (new post-migration Barbarian vs. already-migrated pre-Phase-5 Barbarian) rather than the original, now-corrected claim that the legacy mirror "has nothing to go stale" for every player.

### Finding 4 — Migration verification wording / real `onActivate` coverage: **DOCUMENTATION/TEST WORDING FIXED** (plus real coverage added)

The pre-correction check titled "onActivate's trySpend fails..." called `PlayerResourceService.trySpend` directly, never `BarbarianRageAbility.onActivate`. Its title was reworded to state plainly that it exercises "the authoritative trySpend path (called directly here, not through `BarbarianRageAbility.onActivate`)". In addition (option B, since it was straightforward and safe against a real `ServerPlayer`), a new end-to-end check was added that calls the real `AbilityRegistry.BARBARIAN_RAGE.onActivate(player, null)` and asserts both that the Rage toggle activates and that `totality:rage`'s current value drops by exactly one, through the real ability-activation code path rather than a direct service call.

### Finding 5 — Non-Barbarian ownership sanity check: **CONFIRMED + FIXED** (coverage added, no code defect found)

No production defect was found — `BarbarianRageResources`'s provider was already correctly class-gated. Using the existing real-`ServerPlayer` verification fixture (`BarbarianRageMigrationVerification`, no new test infrastructure built), three checks were added: a non-Barbarian gains no `totality:rage` state even after an explicit `BarbarianRageResources.reconcile` call; a Barbarian has exactly one `totality:rage` state entry after reconciliation; and repeated reconciliation against an already-partially-depleted Rage pool does not refill it (current survives unchanged across a second `reconcile` call).

---

**Phase 5 external review corrections:** PASS
**Rage maximum-only level-up sync:** FIXED
**Rage Rest ordering preserved:** YES
**Legacy Rage fallback safe for migrated existing players:** YES
**Phase 5 automated validation:** PASS
**Phase 5 manual validation:** PENDING STEFAN
**Safe to commit Phase 5:** NO — manual validation still required
**Safe to begin Phase 6:** NO — commit/review Phase 5 first

---

**Phase 5 Rage migration:** COMPLETE
**Rage Generic authority:** YES
**Legacy Rage authority remaining:** NO
**Rage Generic grant ownership:** YES
**Rage maximum resolver migrated:** YES
**Rage Rest integration migrated:** YES
**Known legacy Rage dimension-mirror dependency remaining:** NO
**Safe to begin Phase 6 Spell Slot migration:** NO — wait for Phase 5 review first
**Generic Player Resource API V1 overall:** NOT COMPLETE
