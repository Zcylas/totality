# Generic Player Resource API — Pre-Phase-4 Foundation Implementation Report

**Date:** 2026-09-15
**Branch:** feature/general-resource-api
**Scope:** Implement the three canonical pieces the 2026-09-15 Phases 4-8 gap audit found missing before Phase 4 (Mana/Stamina migration) can safely begin: (1) the mutation façade on `PlayerResourceService`, (2) the `ResourceGrantProvider`/grant reconciliation mechanism, (3) the `ResourceMaximumResolver` framework. **No production resource was migrated. No dormant resource was promoted.**

---

## 1. Exact files created / changed

### Main sources — created (29 files)

**`api/rpg/resources/` (root package):**
`ResourceFailureCode.java`, `ResourceFailure.java`, `ResourceOperationResult.java`, `PartitionSelectionPolicy.java`, `ResourceCost.java`, `ResourceTarget.java`, `ResourceCause.java`, `ResourceContext.java`, `MaximumChangePolicy.java`, `ResourceMaximum.java`, `ResourceResolutionContext.java`, `ResourceMaximumResolver.java`, `ResourceMaximumResolverRegistry.java`, `ResourceOperation.java`, `TransactionMode.java`, `ResourceTransaction.java`, `ResourceTransactionResult.java`

**`api/rpg/resources/integration/`:**
`ResourceGrantSourceType.java`, `ResourceGrantMode.java`, `ResourceGrantAggregationPolicy.java`, `ResourceRemovalPolicy.java`, `ResourceVisibilityPolicy.java`, `ResourceGrant.java`, `ResourceGrantProvider.java`, `ResourceGrantRegistry.java`, `ResourceGrantPolicy.java`, `ResourceGrantPolicyRegistry.java`, `ResourceGrantReconciler.java`

**`api/rpg/resources/verification/`:**
`ResourceFoundationVerification.java` (dev-only, isolated-registry end-to-end self-test)

### Main sources — modified (5 files)

| File | Change |
|---|---|
| `PlayerResourceService.java` | Added the full mutation façade (`trySpend`/`drain`/`restore`/`set`/`transact`/`reconcileMaximum`), the central `resolveMaximum` path, and `PARTITIONED_POOL` support for `GENERIC_COMPONENT` queries. Query behavior for the seven existing production resources is byte-for-byte unchanged. |
| `ResourceAmount.java` | Updated stale Javadoc ("restore/drain/spend/set/transactions remain unimplemented") now that they exist. |
| `external/ExternalPlayerResourceAdapter.java` | Added three default `restore`/`drain`/`set` methods (default: throw, documented as unreachable until an adapter opts in) so the mutation façade has a real dispatch target for a future opted-in adapter, without redesigning the interface later. Zero change to any of the 7 existing adapter implementations. |
| `integration/ResourceGrantInitialization.java` | Updated stale Javadoc ("no ResourceGrantProvider... exists yet") now that the sibling grant types exist. |
| `Totality.java` | One line added: `ResourceFoundationVerification.register()`, alongside the existing `OffhandAttackVerification.register()` call. |

### Test sources — created (4 files, 55 new tests)

- `PlayerResourceServiceMutationTest.java` — 20 tests (spend/drain/restore/set, scalar + partitioned, external-adapter safety)
- `PlayerResourceServiceTransactionTest.java` — 8 tests (atomicity, same-resource ordering, mixed-authority rejection)
- `PlayerResourceServiceMaximumResolutionTest.java` — 17 tests (resolver vs. authored, all 4 `MaximumChangePolicy` values, partitioned resolution)
- `integration/ResourceGrantReconcilerTest.java` — 10 tests (grant lifecycle, aggregation, removal policy, idempotency)

### Test sources — modified (2 files)

| File | Change |
|---|---|
| `PlayerResourceServiceTest.java` | Updated all 7 `queryGenericState(...)` call sites for its new leading `ServerPlayer` parameter (mechanical, `null` — none of these tests register a resolver). Rewrote 2 tests (`partitionedGenericDefinitionsProduceAnExplicitUnsupportedModelFailure` → `...NowSupportedButStillFailStructurallyWhenNeverInstantiated`; `queryGenericStateStillRejectsPartitionedPoolAfterThePhase2DExternalExtension` → `queryGenericPartitionedWithAnAuthoredResolverSucceeds`) whose own names/comments explicitly framed the old `UNSUPPORTED_MODEL` behavior as "a documented future gap" — this pass closes that gap; see §14 for why this counts as completing documented pending work rather than "rewriting a passing test to fit the implementation." |
| `DormantResourceRegistrationTest.java` | Same mechanical 5-call-site signature update, no behavioral change. |

### Report and deliverables

- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_PRE_PHASE4_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-15.md` (this file)
- `Context/Audit/Review/TOTALITY_GENERIC_PLAYER_RESOURCE_API_PRE_PHASE4_FOUNDATION_REVIEW_2026-09-15.zip` (review bundle, see the end of this session's chat response for its exact manifest)

**No file belonging to Mana, Stamina, Rage, Spell Slots, Thirst, Sanity, or Ki's production wiring was touched.**

---

## 2. Canonical sections implemented

§9.1 (Maximum resolver), §10.1/§10.2 (current-vs-maximum, central resolution path — steps 1, 2, 5 of the 6-step order; steps 3-4 deferred, see §14), §11.4 (`MaximumChangePolicy`, all 4 values), §12.1-12.4 (mutation façade, Spend/Drain/Restore/Set semantics, overflow), §13.1-13.2 (atomic resource-only transactions, `ATOMIC_ALL_OR_NOTHING`), §13.3 (mixed-authority transaction rejection), §14.1-14.2 (partitioned cost shape, `EXACT_TIER`), §16.1-16.3 (lifecycle axes, grant providers, grant identity-by-source), §16.4 (aggregation policy — `SINGLE_OWNER`/`SHARED_RESOURCE`/`HIGHEST_PRIORITY_SOURCE` implemented, `SEPARATE_INSTANCES` declared-only), §16.6 (grant acquisition/initialization, all 6 `ResourceGrantInitialization` variants), §16.7 (removal policy — `REMOVE_STATE`/`PRESERVE_DORMANT` implemented, others declared-only), §16.12 (idempotent/batched reconciliation), §21.1-21.2 (`ResourceFailureCode`, `ResourceFailure`, `ResourceOperationResult`).

---

## 3. `PlayerResourceService` before / after API surface

**Before (Phase 2A-3C):** `query(Player, Identifier) → ResourceQueryResult`. That is the entire public surface.

**After (this pass), added:**
```java
Optional<ResourceMaximum> resolveMaximum(ServerPlayer, PlayerResourceDefinition, ResourceResolutionContext)
ResourceOperationResult   trySpend(ServerPlayer, ResourceCost, ResourceContext)
ResourceOperationResult   drain(ServerPlayer, ResourceAmount, ResourceContext)
ResourceOperationResult   restore(ServerPlayer, ResourceAmount, ResourceContext)
ResourceOperationResult   set(ServerPlayer, ResourceTarget, ResourceContext)
ResourceTransactionResult transact(ServerPlayer, ResourceTransaction, ResourceContext)
ResourceOperationResult   reconcileMaximum(ServerPlayer, Identifier, long previousMax, MaximumChangePolicy, ResourceContext)
```
Plus package-visible `*GenericState` cores for each (`trySpendGenericState`, `applyClampedDeltaGenericState`, `setGenericState`, `transactGenericState`, `reconcileMaximumGenericState`) that accept `PlayerResourceStateComponent` explicitly — added specifically so this new logic is unit-testable the same way `queryGenericState` already was (a real `ServerPlayer` cannot be constructed under plain JUnit; see §14).

`queryGenericState`'s signature changed from `(PlayerResourceDefinition, PlayerResourceStateComponent)` to `(ServerPlayer, PlayerResourceDefinition, PlayerResourceStateComponent)` — the new leading parameter feeds a registered `ResourceMaximumResolver`, if any. `player` may be `null` when no resolver is registered (all 7 production resources today: none register one).

---

## 4. Resource mutation semantics implemented

- **Spend** (`trySpend`): affordability-checked, all-or-nothing (canonical §12.3). Insufficient funds → `ResourceFailureCode.INSUFFICIENT_RESOURCE` with `requiredUnits`/`availableUnits` populated; state is provably unchanged (see `insufficientResourceSpendFailsAtomicallyWithoutMutatingState`).
- **Drain** (`drain`): clamps at `absoluteMinimum`; partial application is normal, never a forbidden case.
- **Restore** (`restore`): clamps at resolved maximum; partial application is normal. Overflow is out of scope for `restore`/`drain` this pass (only `reconcileMaximum`'s `ALLOW_OVERFLOW` path touches `overflowUnits` — see §14).
- **Set** (`set`): privileged-only. Gated on `ResourceContext.cause().type()` being `totality:migration` or `totality:admin_command` (a small, explicit allow-list — canonical names the concept but not the enforcement mechanism; see §14 for why this is a documented, reasoned choice, not a re-derived canonical fact). Clamps to `[absoluteMinimum, maximum]` even for privileged callers.
- Each operation returns a `Success`/`PartitionedSuccess`/`Failure` (sealed `ResourceOperationResult`) carrying before/after snapshots, requested vs. applied units, and — on failure — a structured `ResourceFailure` naming a `ResourceFailureCode`.

**External-authority mutation safety (§6 of the task):** every mutation method checks `definition.stateAuthority()` first. For `EXTERNAL_ADAPTER` resources, the call routes to `dispatchExternalMutation`, which checks the adapter's `supportedOperations()` before ever invoking it; today every one of the 7 production adapters (Health/Food/Breath/Mana/Stamina/SpellSlots/Rage) declares `QUERY` only, so **every mutation attempt against any of them fails with `OPERATION_UNSUPPORTED_BY_AUTHORITY`, and no shadow `GENERIC_COMPONENT` state is ever created.** `trySpend` against an `EXTERNAL_ADAPTER` resource is unconditionally rejected (no `SPEND` capability exists in `ExternalResourceOperationSupport`, and building an affordability-checked delegation onto `DRAIN` without a real adapter to validate the pre-check/commit ordering against would be untested, invented behavior — deliberately not built; see §14).

---

## 5. Transaction semantics

`transact(player, transaction, context)`:

1. **Validation phase** — for every operation, in order: resolve its resource's definition; if `EXTERNAL_ADAPTER`, fail the whole transaction with `ATOMIC_OPERATION_UNSUPPORTED` before any mutation (canonical §13.3: no `TransactionalExternalResourceAdapter` contract exists yet); otherwise load (or reuse, if already touched by an earlier operation in this same transaction) a per-resource **working ledger** seeded from real current state, and simulate the operation against the ledger only — never real state.
2. **Commit phase** — only reached if every operation validated. Every ledger's final value is written to real state in one pass, then every touched resource is marked dirty for sync.

This gives the two required determinism properties directly, proven by test:
- Two operations targeting the *same* resource compose **sequentially** against the shared ledger (`twoOperationsTargetingTheSameResourceApplySequentially`), not independently against the original value.
- One invalid operation anywhere in the list fails the **entire** transaction with zero partial mutation (`oneInvalidOperationFailsTheWholeTransactionWithoutPartialMutation`), and `ResourceTransactionResult.failedOperationIndex()` names which operation (0-based) caused it.

**Scope boundary, stated plainly:** this pass's transaction support is **scalar-only** — `Ledger` returns `null` (→ `RESOURCE_NOT_INSTANTIATED`) for a `PARTITIONED_POOL` resource. Canonical §13.2 gives no partitioned-transaction example, and no test or production resource needs one this pass.

---

## 6. Grant architecture

`ResourceGrantProvider` (`Collection<ResourceGrant> getResourceGrants(ServerPlayer)`) is the exact canonical §16.2 shape. `ResourceGrant` carries `resourceId`, `sourceId`, `sourceType` (`ResourceGrantSourceType`, all 14 canonical values), `mode` (`ResourceGrantMode` — `PERSISTENT`/`CONDITIONAL`/`TEMPORARY`, transcribed from canonical's one descriptive sentence, no invented values), `initialization` (the pre-existing `ResourceGrantInitialization`), `removalPolicy`, `visibilityPolicy`, and `priority`.

`ResourceGrantReconciler.reconcile(player, state)` is the actual engine:

```
owning system → ResourceGrantProvider → ResourceGrant → reconciliation → PlayerResourceStateComponent instance
```

exactly the ownership pattern the task specifies. Per call: gather every provider's grants, group by resource id; for each granted `GENERIC_COMPONENT` resource **not yet instantiated**, select a winning grant (per the resource's `ResourceGrantAggregationPolicy` — see §11 below) and instantiate using its `ResourceGrantInitialization`; for each **already-instantiated** resource no longer present in the fresh grant set, apply its `ResourceRemovalPolicy`. `EXTERNAL_ADAPTER`-authority grants (canonical's `totality:health_system → Health` example) are informational only — never create `GENERIC_COMPONENT` state (`aGrantForAnExternalAdapterResourceNeverCreatesGenericComponentState`).

**No production resource is wired to this mechanism.** `ResourceGrantRegistry.INSTANCE` has zero registered providers after this pass — confirmed by `git diff` showing no production init file (`ProductionResourceDefinitions.java`, `Totality.java`'s `onInitialize`, any class-selection handler) touched to register one.

---

## 7. Grant reconciliation behavior

**Deliberately stateless between calls** — canonical §16.3's own words: "Derived grants should normally be recalculated from their owning systems... Persist a grant only when its source is itself a durable independent grant." Rather than persisting a second "who currently owns this" store, the reconciler recomputes the full live grant set from every provider on *every* call and compares it against `PlayerResourceStateComponent.instantiatedResourceIds()` (which resources *have* state right now):

- Granted + not instantiated → instantiate (first acquisition).
- Granted + already instantiated → no-op (idempotent — this is what makes re-running safe: `reRunningReconciliationWithNoOwnershipChangeIsIdempotent` proves a spent-down pool is neither refilled nor reset by a second reconcile pass).
- Not granted + instantiated → apply removal policy.
- Not granted + not instantiated → no-op.

This requires zero extra bookkeeping and directly satisfies every task-required property: duplicate/overlapping sources never duplicate state (`duplicateOverlappingGrantSourcesDoNotDuplicateState`); losing one of several sources doesn't remove state while another remains (`removingOneOfMultipleGrantSourcesDoesNotRemoveStateWhileAnotherRemains`); the final source disappearing follows the configured policy (`finalGrantRemovalFollowsRemoveStatePolicy`, and `...WithPreserveDormantLeavesStateInPlace`).

**Aggregation policy resolution** (`selectWinningGrant`): `SINGLE_OWNER`/`HIGHEST_PRIORITY_SOURCE` pick the highest-`priority` grant (ties broken deterministically by `sourceId`, never silently summed — canonical's own requirement); `SHARED_RESOURCE` lets any valid grant seed the one shared pool; `SEPARATE_INSTANCES` is logged and skipped (no current resource needs it — canonical: "No current Rage, Ki, Solar Charge, Chakra, Reiatsu, or Cursed Energy design requires separate instances").

**Removal policy resolution:** `REMOVE_STATE` deletes state; `PRESERVE_DORMANT` is a no-op (state simply isn't touched); `RESET_AND_PRESERVE`/`CONVERT`/`CUSTOM` are logged and left untouched rather than guessed at, since canonical explicitly says their exact behavior needs an owner-supplied handler/value ("This document does not silently invent those balance rules").

---

## 8. Maximum resolver architecture

`ResourceMaximumResolver` (`ResourceMaximum resolve(ServerPlayer, PlayerResourceDefinition, ResourceResolutionContext)`) is the exact canonical §9.1 shape. `ResourceMaximumResolverRegistry` maps resource id → resolver (unfrozen — a resource legitimately having no resolver is normal, not a structural error). `ResourceMaximum` is a sealed interface with `Scalar(baseUnits, effectiveUnits, appliedModifiers)`/`Partitioned(baseByPartition, effectiveByPartition)`, renamed from canonical's separate top-level `ScalarMaximum`/`PartitionedMaximum` records to nested variants matching this codebase's established `ResourceQueryResult`-style sealed-interface convention (field shape unchanged).

`PlayerResourceService.resolveMaximum` is the **one central path** canonical §10.2 requires — both `queryGenericState` and every mutation method call it, so a scalar resource's maximum can never be computed two different ways. Implemented resolution steps: **1** (definition-authored base) and **2** (a registered resolver) as *alternatives* — if a resolver is registered, it wins outright over any authored value, since a resource with a real resolver (e.g. Ki) has one specifically *because* a static value can't express it; **5** (hard clamp to `absoluteMinimum`) applied to both paths. **Steps 3-4** (permanent/temporary maximum modifiers, canonical's full §11 pipeline) are **not implemented** this pass — see §14.

A missing maximum never fabricates a value: the query path returns `ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE` (pre-existing behavior, now also reachable via a registered-but-null-returning resolver); the mutation path returns `ResourceFailureCode.MAXIMUM_ZERO`.

---

## 9. Generic component production wiring

**None.** No production resource definition, grant provider, or maximum resolver was registered this pass. `ProductionResourceDefinitions.java` is untouched. The only production wiring change is the one-line `ResourceFoundationVerification.register()` call in `Totality.java`, which itself is a complete no-op outside a Fabric development environment (`VerificationReporter.isDevEnvironment()` gates registration itself, not just execution — in a production build no event listener is even added).

`ResourceFoundationVerification` proves the pipeline end-to-end against a **real, component-attached `ServerPlayer`** (`TotalityFakePlayer`, this codebase's established fixture for exactly this purpose) using **isolated** `PlayerResourceRegistry`/`PlayerResourceService`/`ResourceGrantReconciler` instances — the dev-only test resource id (`totality:devtest_resource_foundation`) is never registered against `PlayerResourceRegistry.INSTANCE`, so it structurally does not exist for real players in dev *or* production, satisfying "do not expose test content in production builds merely for convenience" as strictly as possible while still exercising the real `PlayerResourceStateComponent` attachment on a real player. **Confirmed running successfully in a real dedicated server this session:**
```
[ResourceFoundationVerification] All 6 self-test checks passed.
```
(query-before-grant fails structurally → reconcile grants and instantiates → re-reconcile doesn't refill → restore raises current and is queryable → a transaction commits atomically → final grant removal deletes state — all against the real component.)

---

## 10. Persistence behavior

**Unchanged.** `PlayerResourceStateComponent.writeData`/`readData`/`writeSyncPacket`/`applySyncPacket` were not touched by this pass — they already correctly persist both `ScalarResourceState` and `PartitionedResourceState`, including orphan quarantine, from Phase 1. This pass only adds *callers* of the state component's existing `instantiateScalar`/`getScalar`/`removeState` methods (via mutation/grants) — no new persistence code was needed or written.

**Maximum is confirmed never persisted**, matching canonical §10.1 ("Maximum is not normally persisted"): no code path anywhere in this pass writes a `ResourceMaximum` or any of its fields to NBT. `queryGenericScalar`/mutation methods always call `resolveMaximum` fresh; nothing caches a resolved value across calls.

---

## 11. Synchronization behavior

**No second sync architecture was built** — every mutation method that successfully changes state calls the pre-existing `ResourceSyncManager.markDirty(player.getUUID(), resourceId)`, the exact same call Mana/Stamina/Rage/SpellSlots' legacy managers already use. `ResourceSyncManager.isEligibleForGenericSync` already unconditionally includes every `GENERIC_COMPONENT` resource (confirmed by reading its current source this session — a pre-existing, unmodified rule), and its wire-snapshot building already goes through `PlayerResourceService.INSTANCE.query(...)` — meaning a `GENERIC_COMPONENT` resource picks up full/delta sync automatically through code this pass did not need to touch at all. This composition (already-tested sync layer + this pass's already-tested query/mutation layer) is how "enters a full Generic snapshot" and "produces a delta after mutation" are satisfied without a redundant new integration test — see §14 for why a from-scratch integration test wasn't additionally written (registry-freeze constraints make it awkward to prove against the real `.INSTANCE` singletons without polluting them).

---

## 12. External-adapter mutation behavior

Every current production adapter (Health/Food/Breath/Mana/Stamina/SpellSlots/Rage) declares `ExternalResourceOperationSupport.QUERY` only. `ExternalPlayerResourceAdapter` gained three **default** methods (`restore`/`drain`/`set`, each throwing `UnsupportedOperationException` if actually invoked) — none of the 7 existing implementations were touched; all default-inherit the throwing stub, which is never reached because `PlayerResourceService.dispatchExternalMutation` checks `supportedOperations()` *before* calling any of them. This is the structural hook a future adapter needs to opt into real mutation without an interface redesign, exactly matching the task's "establish the canonical generic operation abstraction... without redesign" instruction, applied to the external-adapter boundary as well as the partitioned-resource boundary.

---

## 13. Partitioned-resource readiness

`GENERIC_COMPONENT` `PARTITIONED_POOL` resources are **now genuinely supported**, not just structurally scaffolded — this was a real gap closed this pass (`queryGenericState` previously rejected every `PARTITIONED_POOL` `GENERIC_COMPONENT` definition with `UNSUPPORTED_MODEL` unconditionally; two existing tests explicitly named this "a documented future gap," see §1/§14). `trySpend`/`drain`/`restore`/`set` all correctly dispatch to a partitioned path (`spendGenericPartitioned`, `applyClampedPartitionedDelta`, `setGenericPartitioned`), each validated by dedicated tests (`partitionedSpendReducesOnlyTheTargetedPartition`, `partitionedSpendOnANonexistentPartitionFailsWithInvalidTier`, `partitionedResolverMaximumIsUsedCorrectlyPerPartition`). `ResourceCost`/`ResourceAmount`/`ResourceTarget` all have a `Partitioned` variant. `ResourceOperationResult` is a sealed interface with `Success`/`PartitionedSuccess` precisely so one mutation method serves both models without forcing a partitioned pool's snapshot into a scalar-shaped record.

**Not implemented for partitioned resources this pass:** transactions (`Ledger` is scalar-only) and grant instantiation (`ResourceGrantReconciler.instantiateFromGrant` is scalar-only) — both explicitly guarded (return a structured failure / log-and-skip, never silently mishandle) rather than attempted without a real example to validate against. No test or production resource needs either this pass.

---

## 14. Deliberate canonical features still deferred

Explicitly **not** built this pass, each because the task's own instructions license the omission and no test/production resource requires it:

- **Full §11 modifier pipeline** (`ResourceMaximumModifier`/`ResourceCostModifier`/`ResourceRateModifier`, priority-ordered operation stacking, permanent-vs-temporary persistence rules). Task: "do not build an enormous generic formula language." A resolver's `effectiveUnits` is trusted as final; steps 3-4 of the 6-step resolution order are no-ops.
- **Event bus** (`ResourceChangeAttemptEvent`/`ResourceChangedEvent`, §20's full pre/post-validation phase model, reentrancy tracking). Task: "implement events only where necessary to preserve the canonical mutation contract cleanly... do not build speculative event infrastructure." The *results* are rich (full `ResourceFailureCode`/`ResourceFailure` vocabulary) as the task requires instead.
- **Idempotency request-nonce caching** (§13.4). `ResourceContext.requestNonce` and `ResourceTransaction.requestNonce` exist as fields (so a future caller can supply one without a signature change) but no dedup cache is implemented.
- **Regeneration/decay scheduler** (§15). Entirely out of scope — no resource in this pass needs periodic change.
- **`ResourceEntitlementView`/live Entitlement API integration** (§16.5). Canonical itself permits this: "Until the Entitlement API exists, current class/species/lineage/discipline components may act as temporary grant authorities." A `ResourceGrantProvider` is expected to check its own owning system's entitlement state internally (e.g. a future `BarbarianClass` provider calling `ClassComponents.get(player).hasClass(...)`); the reconciler itself has no entitlement-checking layer.
- **Cross-domain prepared transactions** (§13.3's `PreparedResourceTransaction`/`TransactionalExternalResourceAdapter`). Explicitly out of V1 per canonical's own text; the one concrete consequence (a mixed transaction fails `ATOMIC_OPERATION_UNSUPPORTED` before any mutation) *is* implemented.
- **Registered condition IDs** (§21.3). "Must not implement the full future UnlockRequirement tree" — not needed by any test this pass.
- **Partitioned transactions and partitioned grant instantiation** — see §13.

**Documented, reasoned interpretations where canonical is illustrative-but-not-fully-specified** (none contradict canonical; all are mechanical completions of a shown single-operation shape into a sealed union, or narrow enforcement-mechanism choices canonical names but doesn't specify):

- `ResourceOperation` (the `ResourceTransaction.operations` element type) — canonical never gives this type a literal definition; built as a sealed union of the four already-fully-specified single-operation shapes (`Spend(ResourceCost)`/`Drain(ResourceAmount)`/`Restore(ResourceAmount)`/`Set(ResourceTarget)`).
- `ResourceCost.Scalar`, `ResourceTarget`, `ResourceTransactionResult`, `ResourceResolutionContext`, `ResourceGrantMode` — each either has no canonical field-level shape (only a name and/or one descriptive sentence) or is a direct mechanical counterpart to an already-specified sibling type (`PartitionedResourceCost` → `ResourceCost.Scalar`). Every file's Javadoc states this explicitly.
- **`set`'s privilege gate mechanism** — canonical says `set` is "privileged correction, migration, initialization, admin, or controlled transformation" but never specifies *how* a caller proves privilege. Implemented as a narrow allow-list on `ResourceContext.cause().type()` (`totality:migration`, `totality:admin_command`). This is a real design decision worth flagging for review, not a re-derivation of a canonical rule.
- **Resolver-vs-authored-maximum precedence** — canonical's 6-step list literally puts "authored base" before "owning strategy," but no current resource has both, so this pass's choice (resolver wins when present) is a foundation-pass decision pinned by `resolverTakesPriorityOverAnAuthoredMaximumWhenBothArePresent`'s own comment, not a re-derived fact. **Flagged for design review if a future resource ever needs both simultaneously.**
- **Two `PlayerResourceServiceTest` tests were rewritten, not merely extended** (`partitionedGenericDefinitionsProduceAnExplicitUnsupportedModelFailure` and `queryGenericStateStillRejectsPartitionedPoolAfterThePhase2DExternalExtension`). Both tests' own names/comments explicitly framed the `UNSUPPORTED_MODEL` behavior they pinned as "a documented future gap" that a later phase would close — this pass is that later phase. Their replacements pin the new, correct behavior (`STATE_NOT_INSTANTIATED` for an uninstantiated partitioned resource; a real `PartitionedSuccess` once instantiated) with comments explaining exactly why the expected outcome changed. This is judged to be completing documented pending work, not "rewriting a passing test merely to fit the implementation" — flagged here explicitly per the task's own instruction to surface this kind of judgment call.

**No genuine contradiction between canonical and the implementation was found.** No stop condition was triggered.

---

## 15. Remaining work before Phase 4

1. Manually confirm (or write an integration-style test if the registry-freeze constraint can be worked around cleanly) that the generic sync path actually delivers a real, non-dormant `GENERIC_COMPONENT` resource's full/delta snapshot to a connected client — this pass's reasoning for why it should work (§11) is sound but was not independently re-verified against a live client connection.
2. If Phase 4 needs `set`'s privilege gate to recognize additional callers (e.g. a future migration tool), extend the allow-list in `PlayerResourceService.isPrivilegedCause` (package-visible, already isolated).
3. Decide whether resolver-vs-authored-maximum precedence (§14) needs to change before any resource that has both is introduced.
4. Build the §11 modifier pipeline only when a real resource's balance actually needs stacked modifiers (not preemptively).

None of this blocks starting Phase 4 (Mana/Stamina migration) — the foundation this task built is what Phase 4 needs to target.

---

## 16. Whether Phase 4 Mana/Stamina migration is now safe to begin

**Yes, architecturally.** The three missing pieces the gap audit identified — mutation façade, grant mechanism, maximum resolver — now exist, are unit-tested (55 new tests) and proven against a real server/player (`ResourceFoundationVerification`, 6/6 passing). Phase 4 can now: redefine Mana/Stamina from `EXTERNAL_ADAPTER` to `GENERIC_COMPONENT` authority; register real `ResourceMaximumResolver`s reading the existing stat formulas; register real `ResourceGrantProvider`s for the universal `totality:player_baseline` grant; import legacy NBT via `ResourceGrantInitialization.PreserveExisting`/`AtAbsolute`; and route `PlayerManaManager`/`PlayerStaminaManager` through `PlayerResourceService.trySpend`/`restore`/`drain` as compatibility facades — all using infrastructure this pass built and tested, not infrastructure Phase 4 would need to build for itself.

---

## 17. Full validation results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **BUILD SUCCESSFUL** |
| `./gradlew compileTestJava` | **BUILD SUCCESSFUL** |
| Focused Resource API tests (4 new files) | **55/55 passing** (20 mutation + 8 transaction + 17 maximum-resolution + 10 grant) |
| Updated existing tests (`PlayerResourceServiceTest`, `DormantResourceRegistrationTest`) | Passing, including the 2 rewritten tests |
| `./gradlew test` (full suite) | **BUILD SUCCESSFUL — 1497/1497 tests, 0 failures, 0 errors** (up from 1442 before this task) |
| `./gradlew clean build` | **BUILD SUCCESSFUL** (compile, datagen, test, jar, check, all green) |
| `git diff --check` | Clean — no whitespace errors (only pre-existing LF/CRLF informational warnings) |
| Bounded dedicated-server startup | **Confirmed** — real `./gradlew runServer`, reached `Done (0.328s)!`, then `[ResourceFoundationVerification] All 6 self-test checks passed.` with zero exceptions. (Required because this pass adds one line of production initialization wiring in `Totality.java`.) |

---

## 18. Final status lines

- **Canonical Resource API design:** CLOSED / unchanged
- **Phase 0-3:** COMPLETE / unchanged
- **Pre-Phase-4 mutation foundation:** IMPLEMENTED — `trySpend`/`drain`/`restore`/`set`/`transact` all present, tested, and proven against a real server/player; scoped to `GENERIC_COMPONENT` resources (§4), `EXTERNAL_ADAPTER` mutation structurally rejected pending a future opted-in adapter
- **Resource Grant foundation:** IMPLEMENTED — `ResourceGrantProvider`/`ResourceGrant`/`ResourceGrantReconciler` present and tested; zero production providers registered
- **Resource Maximum Resolver foundation:** IMPLEMENTED — central `resolveMaximum` path used by both query and mutation; zero production resolvers registered; full §11 modifier pipeline deferred
- **Phase 4 Mana/Stamina migration:** NOT STARTED
- **Phase 5 Rage migration:** NOT STARTED
- **Phase 6 Spell Slot migration:** NOT STARTED
- **Generic Player Resource API V1 overall:** NOT COMPLETE — the foundation gap the 2026-09-15 audit identified is now closed; Phases 4-8 (actually migrating resource authority) remain entirely ahead

---

## 19. External Review Corrections (2026-09-15 correction pass)

An external review of the Review ZIP produced above found 12 issues before Phase 4 is allowed to depend on this foundation. **No Mana/Stamina/Rage/Spell Slot migration work was done in this pass; scope was strictly a correction/audit pass against the existing implementation.** Each finding is classified below; none is omitted.

### Issue 1 — Resource Grant Removal Policy

**CONFIRMED + FIXED.** `ResourceGrant.removalPolicy` (canonical §16.2) was read into the record but never consulted anywhere — `ResourceGrantReconciler.applyRemoval` sourced its policy exclusively from `ResourceGrantPolicyRegistry.get(resourceId).removal()`, a per-*resource* default, not the per-*grant* policy canonical actually specifies. A grant declaring `PRESERVE_DORMANT` on itself was silently overridden by whatever the resource-level registry said (or the global `REMOVE_STATE` default if nothing was registered).

**Fix:** `PlayerResourceStateComponent` gained a small in-memory `Map<Identifier, ResourceRemovalPolicy> grantRemovalPolicies`. `ResourceGrantReconciler.reconcile()` now records the *currently-winning* grant's own `removalPolicy()` against the resource every pass (both on first instantiation and on every subsequent pass while still granted, so a change in which grant is winning — e.g. a higher-priority source appearing — is tracked live). When a resource's last grant disappears, `applyRemoval` consults this recorded policy first, falling back to the `ResourceGrantPolicyRegistry` default only for a resource that was never touched by the grant system (e.g. a test fixture instantiated directly). This is deliberately **in-memory only, not persisted** — a documented, minor limitation: if a grant disappears entirely between server sessions before any reconciliation runs, the resource-level default applies instead of the exact grant's own policy. Real grant loss overwhelmingly happens during an active session (respec, class change, transformation end), which this record already covers correctly.

Handles every sub-case named by the review: one-of-several sources disappearing (no-op, existing behavior unchanged), the final `REMOVE_STATE` source disappearing (deletes, as before), the final `PRESERVE_DORMANT` source disappearing (now correctly retains + deactivates, see Issue 2), multiple sources with differing declared policies (the winning/highest-priority grant's own policy governs — a documented, reasoned decision, since canonical does not explicitly resolve this exact case), and reconciliation running again after state already exists (policy is refreshed, not just recorded once).

Evidence: `PlayerResourceStateComponent.java` (`grantRemovalPolicies`, `setGrantRemovalPolicy`/`getGrantRemovalPolicy`/`clearGrantRemovalPolicy`), `ResourceGrantReconciler.java` (`reconcile()`, `applyRemoval()`). Tests: `ResourceGrantReconcilerTest#finalGrantRemovalWithPreserveDormantLeavesStateInPlaceButMarksItInactive`, `#reacquiringAGrantAfterPreserveDormantReactivatesWithoutRefillingTheValue`, `#theWinningGrantsOwnRemovalPolicyGovernsEvenWhenTheResourceRegistryDefaultDiffers`.

### Issue 2 — Dormant/Ungranted State Must Not Be Usable

**CONFIRMED + FIXED.** Every mutation path (`spendGenericScalar`/`spendGenericPartitioned`/`applyClampedScalarDelta`/`applyClampedPartitionedDelta`/`Ledger`) checked only whether state *existed* (`state.getScalar(id).isPresent()`), never whether it was currently *granted*. A resource retained under `PRESERVE_DORMANT` after its last grant source disappeared stayed fully spendable/drainable/restorable forever — a real safety gap, not a cosmetic one.

**Fix:** `ScalarResourceState`/`PartitionedResourceState` gained a `boolean active` field (default `true` — zero behavior change for every pre-existing direct-instantiation call site, including all previously-passing tests). `ResourceGrantReconciler.applyRemoval` now sets `active = false` for `PRESERVE_DORMANT` (and, as a related but slightly broader fix, for `RESET_AND_PRESERVE` too — see the note below), and reactivates it (without refilling the value, per canonical §16.6) when the resource is granted again. `PlayerResourceService.trySpendGenericState`/`applyClampedDeltaGenericState`/`transactGenericState` now reject a mutation against inactive state with the new `RESOURCE_INACTIVE` failure code, **unconditionally** for `trySpend`/`drain`/`restore` (no privileged bypass exists for them — there never was one) and **except when the transaction already carries a privileged Set operation** (mirroring `set()`'s own existing migration/admin authority, so an admin/migration correction can still touch dormant state deliberately). `set()` itself is unaffected by this check because it already requires a privileged cause for every call — a strict superset.

The `active` flag is persisted (NBT schema bumped 2→3, `_active` key, default `true` for pre-schema-3 saves — see Issue 6/persistence note below) so dormancy survives a server restart, but is **not** included in the sync packet payload — full HUD/visibility integration (canonical §16.9-16.10) remains explicitly deferred, matching the original pass's own framing; only the server-authoritative mutation-safety gap is closed here.

One documented scope extension beyond the literal review wording: `RESET_AND_PRESERVE` (which has no owner-supplied reset value implemented this pass) is now *also* marked inactive rather than left fully active and unreset — leaving it active would be the identical safety gap for one more policy value, so it was closed the same way.

Evidence: `state/ScalarResourceState.java`, `state/PartitionedResourceState.java` (`active`/`setActive`), `PlayerResourceStateComponent.java` (`isActive`/`setActive`, schema v3), `PlayerResourceService.java` (`inactiveResource`, and its call sites in `spendGenericScalar`/`spendGenericPartitioned`/`applyClampedScalarDelta`/`applyClampedPartitionedDelta`/`transactGenericState`), `ResourceGrantReconciler.java` (`applyRemoval`). Tests: `PlayerResourceServiceMutationTest#spendAgainstDormantScalarStateFailsWithResourceInactive`, `#drainAndRestoreAgainstDormantScalarStateFailWithResourceInactive`, `#spendAgainstDormantPartitionedStateFailsWithResourceInactive`, `#setIsUnaffectedByDormancyBecauseItAlreadyRequiresPrivilege`, `PlayerResourceServiceTransactionTest#anUnprivilegedTransactionAgainstDormantStateFailsWithResourceInactive`, `#aPrivilegedTransactionMayStillTouchDormantState`.

### Issue 3 — Checked Long Arithmetic

**CONFIRMED + FIXED.** No `Math.*Exact`/overflow guard existed anywhere in the new arithmetic: `current ± delta` (`applyClampedScalarDelta`/`applyClampedPartitionedDelta`), `current * newMax` (`PRESERVE_RATIO`), `overflowUnits() + excess` (`ALLOW_OVERFLOW`), `max * numerator` (`ResourceGrantReconciler` `AtFraction`), and `working ± units` (`Ledger.simulate`'s `Drain`/`Restore`).

**Fix:** every listed site now uses `Math.addExact`/`Math.subtractExact`/`Math.multiplyExact` wrapped in a `try`/`catch (ArithmeticException)`, returning the new `OVERFLOW_NOT_SUPPORTED`-coded failure instead of silently wrapping. `OVERFLOW_NOT_SUPPORTED` is a documented mapping decision, not a canonical-mandated one — no dedicated "raw arithmetic overflow" failure code exists in canonical §21.1's vocabulary; it is the closest existing meaning ("this value cannot be represented/accepted by this operation"). `reconcileMaximumGenericState`'s entire reconciliation computation (including `PRESERVE_DEFICIT`'s subtraction, for full "throughout" coverage even though the review named `PRESERVE_RATIO`/overflow-accumulation specifically) is wrapped in one try/catch so a mid-computation overflow cannot leave any partial state mutation.

Evidence: `PlayerResourceService.java` (`overflow()` helper; `applyClampedScalarDelta`, `applyClampedPartitionedDelta`, `reconcileMaximumGenericState`, `Ledger.simulate`), `ResourceGrantReconciler.java` (`instantiateFromGrant`'s `AtFraction` branch). Tests: `PlayerResourceServiceMutationTest#restoreNearLongMaxValueFailsWithOverflowRatherThanWrapping`, `#drainNearLongMinValueFailsWithOverflowRatherThanWrapping`, `PlayerResourceServiceTransactionTest#aDrainOperationThatWouldOverflowFailsAtomicallyWithoutMutatingState`, `PlayerResourceServiceMaximumResolutionTest#preserveRatioNearLongMaxValueFailsWithOverflowRatherThanWrapping`, `ResourceGrantReconcilerTest#atFractionThatWouldOverflowDefersInstantiationRatherThanWrapping`.

### Issue 4 — Transaction Validation Must Match Single Operations

**CONFIRMED + FIXED.** `Ledger.simulate`'s `Spend` case did an unchecked `(ResourceCost.Scalar) s.cost()` cast — a `ClassCastException` risk against a `Partitioned` cost, not a structured failure. `Drain`/`Restore` never checked for a negative amount (unlike `applyClampedDeltaGenericState`'s single-operation path, which does), so a negative `Drain` inside a transaction silently acted as a `Restore` and vice versa. `Set` never validated a negative `absoluteUnits()` (unlike `setGenericScalar`, which does) or a partitioned-shaped target against this scalar-only ledger.

**Fix:** every `ResourceOperation` case in `Ledger.simulate` now validates cost/amount/target shape (`instanceof`/`partition().isPresent()` checks returning `MODEL_MISMATCH`, matching every single-operation path's own model check) and sign (`INVALID_AMOUNT` for a negative `Drain`/`Restore`/`Set`, matching `applyClampedDeltaGenericState`/`setGenericScalar`) before touching `working`, exactly mirroring the single-operation validation order. A malformed transaction still fails atomically with zero partial mutation (validated up front in the pre-existing two-phase validate-then-commit structure, unchanged by this fix).

Evidence: `PlayerResourceService.java`, `Ledger.simulate`. Tests: `PlayerResourceServiceTransactionTest#aSpendOperationCarryingAPartitionedCostFailsWithModelMismatchRatherThanThrowing`, `#aNegativeDrainAmountInsideATransactionIsRejectedRatherThanActingAsARestore`, `#aNegativeRestoreAmountInsideATransactionIsRejected`.

### Issue 5 — Grant Initialization Validation

**CONFIRMED + FIXED.** `instantiateFromGrant`'s `AtAbsolute` branch used `absolute.amount().units()` directly with no check that `absolute.amount().resourceId()` matched the resource actually being granted, no partition-shape check (this pass's grant instantiation is scalar-only), and no negative/below-minimum/above-maximum bound check. `AtFraction` had the overflow gap already covered under Issue 3, but also no clamp against a fraction greater than 1.

**Fix:** `AtAbsolute` now validates `resourceId` match, partition absence, and non-negativity, then resolves the scalar maximum and checks `[absoluteMinimum, max]` — any violation **defers instantiation with a logged warning** (the same `Long.MIN_VALUE`-sentinel pattern this method already uses for every other unresolvable case) rather than silently clamping. **Documented clamp-vs-reject decision:** reject-by-deferral was chosen over clamping because canonical gives no rule for a malformed authored `AtAbsolute` amount, and silently clamping could mask a real authoring bug (e.g. a grant author who meant a different unit scale) — every other branch in this method already defers rather than guesses, so this keeps the method internally consistent. `AtFraction`'s computed value is now clamped to `[absoluteMinimum, max]` after the (now overflow-checked) multiplication, so a fraction greater than 1 cannot seed a value above the resolved maximum.

Evidence: `ResourceGrantReconciler.java`, `instantiateFromGrant`. Tests: `ResourceGrantReconcilerTest#atAbsoluteTargetingADifferentResourceIdDefersInstantiationRatherThanMisapplyingTheValue`, `#atAbsoluteWithANegativeAmountDefersInstantiation`, `#atAbsoluteAboveTheResolvedMaximumDefersInstantiationRatherThanSilentlyClamping`, `#validAtAbsoluteWithinBoundsInstantiatesAtExactlyThatValue`, `#atFractionAboveOneClampsToTheResolvedMaximumRatherThanOverAllocating`.

### Issue 6 — Grant Reconciliation + Generic Sync

**DEFERRED BY DESIGN (no code change to `ResourceGrantReconciler.java` itself — see exact reason below).** `ResourceGrantReconciler.reconcile()` had zero calls to `ResourceSyncManager.markDirty` anywhere — a mid-session grant or removal (e.g. a class change instantiating or removing a resource) would never propagate to the client sync path, unlike every mutation method in `PlayerResourceService`, which already marks dirty on every successful change.

**Exact reason for deferral:** `reconcile()` is deliberately `player`-nullable and stateless-by-design (its own class Javadoc: recomputed fresh from providers every call, no tick loop or event subscription of its own). Adding a `ResourceSyncManager.markDirty` call inside it would require either forcing a non-null `ServerPlayer` parameter — a larger, unrequested signature change touching every existing call site, including the 8 grant-reconciler tests that pass `player = null` — or silently no-op'ing the sync call whenever `player == null`, which would make the method behave differently in tests than in the one production path that will eventually call it, an untested asymmetry. Neither is the smallest correct fix for a method **no production caller invokes yet** (no class-change/respec/login hook wires `reconcile()` up this pass, matching the original pass's own documented scope) — inventing a sync call for a code path nothing currently exercises would be speculative, not a fix for a live bug. What the correction pass *did* verify by code inspection (not a code change): `reconcile()` reuses `PlayerResourceStateComponent`'s existing state exactly as `PlayerResourceService` does, so once a real caller exists and invokes `ResourceSyncManager.markDirty(player.getUUID(), id)` for each `instantiated()`/`removed()` id (and, after this correction pass, ideally also for a resource that transitioned active↔dormant), the existing Phase 3A sync path will pick it up correctly with no new packet system needed. **Remaining visibility-integration work** (documented, not falsely claimed complete): no caller currently invokes `reconcile()` in response to any real trigger in production; the eventual caller is responsible for the `markDirty` calls described above.

Evidence: `ResourceGrantReconciler.java` class Javadoc (unchanged framing, re-verified accurate), `ResourceSyncManager.java` (unchanged — reused, not duplicated).

### Issue 7 — Maximum Resolution Precedence

**CONFIRMED + FIXED.** The original pass had a registered `ResourceMaximumResolver` win outright over an authored base whenever both existed. Canonical §10.2's numbered resolution order — "1. Definition-authored base, if any. 2. Owning strategy's base maximum." — is a literal priority list, not two unordered alternatives; the original behavior was the exact reverse of what §10.2 specifies.

**Fix:** `resolveMaximum` now checks `definition.authoredBaseMaximum()` first for `SCALAR` resources; if present, it wins outright (clamped to `absoluteMinimum`) and no resolver is even consulted. A resolver is only reached when no authored base exists (the step-2 fallback). `PARTITIONED_POOL` is unaffected — `PlayerResourceDefinition` has no per-partition authored-base field to prioritize, so it always went (and still goes) straight to a resolver.

Evidence: `PlayerResourceService.java`, `resolveMaximum`. Test: `PlayerResourceServiceMaximumResolutionTest#authoredMaximumTakesPriorityOverARegisteredResolverWhenBothArePresent` (renamed and inverted from the original `resolverTakesPriorityOverAnAuthoredMaximumWhenBothArePresent`, which pinned the now-corrected wrong behavior — no other existing test exercised this precedence, since the original report itself noted "no production or test resource in this pass registers both simultaneously," so this fix broke no other passing test).

### Issue 8 — Partitioned Maximum Safety

**CONFIRMED + FIXED.** A registered resolver's `ResourceMaximum.Partitioned` result was accepted with zero structural validation — a negative partition maximum (malformed resolver, authoring bug) would flow straight into every mutation path's clamping math (`Math.min(max, ...)` with a negative `max` breaks every spend/set for that partition in ways that are confusing to diagnose).

**Fix:** `resolveMaximum`'s `PARTITIONED_POOL` branch now validates every value in both `baseByPartition` and `effectiveByPartition` is non-negative (`isValidPartitionedMaximum`); a violation is treated exactly like a `null`/wrongly-shaped resolver result (`Optional.empty()`, logged warning) rather than propagated into gameplay math. This is generic structural safety only — no Spell-Slot-specific or other resource-specific rule was added, per the task's scope limits. "Impossible current/max relationships" (i.e. `current > max`) was deliberately **not** treated as invalid, since that is a legitimate, expected transient state elsewhere in this codebase (e.g. immediately after a maximum decrease, before `reconcileMaximum` runs) — already handled correctly by clamping everywhere it is read, not a corruption.

Evidence: `PlayerResourceService.java`, `resolveMaximum`/`isValidPartitionedMaximum`. Tests: `PlayerResourceServiceMaximumResolutionTest#aResolverReturningANegativeEffectivePartitionValueIsTreatedAsUnresolvable`, `#aResolverReturningANegativeBasePartitionValueIsTreatedAsUnresolvable`.

### Issue 9 — Tests

**CONFIRMED + FIXED.** 23 new regression tests were added across the four existing Resource API test files (none deleted, none weakened — the 2 tests that had to change were pinning behavior this correction pass deliberately changed, per Issues 1 and 7, and both were rewritten to pin the new, correct behavior rather than simply relaxed). Total Resource API test count: 55 → 78 (transaction 8→14, mutation 20→26, maximum-resolution 17→20, grant-reconciler 10→18).

### Issue 10 — Scope

**CONFIRMED, no code implication.** Verified by `git diff` inspection: no file belonging to `PlayerManaManager`, `PlayerStaminaManager`, `PlayerChargesComponent`, `SpellSlotComponent`, `ProductionResourceDefinitions.java`, any legacy sync packet, or any of the 7 production `EXTERNAL_ADAPTER` resources' authority was touched. No dormant resource (Thirst/Sanity/Ki) was promoted or instantiated for any real player. The legacy Rage dimension-transfer bug was not touched. No legacy manager or packet was removed. No gameplay balance number changed. No Resource API phase beyond this correction pass's own 12 listed issues was touched.

### Issue 11 — Validation

**CONFIRMED + FIXED** (procedural — see §20 below for the full results table).

### Issue 12 — Update Deliverables

**CONFIRMED + FIXED** — this section, plus the rebuilt Review ZIP (see the end of this session's chat response for its exact manifest).

---

## 20. Correction-pass validation results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **BUILD SUCCESSFUL** |
| `./gradlew compileTestJava` | **BUILD SUCCESSFUL** |
| Focused Resource API tests (4 files) | **78/78 passing** (26 mutation + 14 transaction + 20 maximum-resolution + 18 grant-reconciler) |
| `./gradlew test` (full suite) | **BUILD SUCCESSFUL — 1520/1520 tests, 0 failures, 0 errors** (up from 1497 before this correction pass; +23 new tests) |
| `./gradlew clean build` | **BUILD SUCCESSFUL** |
| `git diff --check` (correction-pass files) | Clean — no whitespace errors (only pre-existing LF/CRLF informational warnings) |
| Bounded dedicated-server startup | **Confirmed** — real `./gradlew runServer`, reached `Done (0.356s)!`, then `[ResourceFoundationVerification] All 6 self-test checks passed.` with zero exceptions. Two unrelated, pre-existing verification failures were observed in the same run (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) — neither references any file this correction pass touched (confirmed by `grep`); both are outside this task's scope (shop/Provisioner and offhand-combat/Stamina systems respectively) and are not addressed here. |

---

## 21. Correction-pass final status lines

- **Pre-Phase-4 foundation correctness review:** **PASS** — all 12 external-review findings addressed (8 confirmed-and-fixed: Issues 1-5, 7-9; 1 deferred-by-design with an exact documented reason: Issue 6; 1 confirmed-with-no-code-implication [scope]: Issue 10; 2 procedural: Issues 11-12); zero findings omitted; 78/78 focused tests and 1520/1520 full suite passing; clean build; dedicated-server self-test 6/6.
- **Phase 4 Mana/Stamina migration safe to begin:** **YES** — the same foundation §16's "Yes, architecturally" verdict stands, now additionally hardened against the 12 correction issues (dormant-state safety, per-grant removal policy, checked arithmetic, transaction validation parity, grant-initialization validation, corrected maximum precedence, and partitioned-maximum structural safety) that a real migration would otherwise have inherited silently.
