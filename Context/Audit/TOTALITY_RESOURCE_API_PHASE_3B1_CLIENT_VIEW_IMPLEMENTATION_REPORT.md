# TOTALITY — Generic Player Resource API — Phase 3B-1 Implementation Report
## Trusted Client Resource View and Presentation-Only Query Façade

Scope actually implemented: the narrow Phase 3B-1 slice only — an immutable client query/result
model, a public presentation-only façade, native Health/Food/Breath readers, generic Mana/Stamina/
spell-slots/Rage readers, trust/freshness metadata, and bounded malformed/incompatible-payload
logging. No shadow parity, no consumer migration, no Phase 3C.

---

## 1. Starting branch and commit

- Branch: `feature/general-resource-api`.
- Starting `HEAD`: `00857506dbd07410778e0b97c348d066eab5da0e` ("Add generic resource synchronization contract") — confirmed matching the expected checkpoint before any change was made.
- Tracking `origin/feature/general-resource-api`, 0 ahead/0 behind at start.
- The Phase 3B readiness audit (`Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B_CLIENT_VIEW_AND_PARITY_READINESS.md`) was present, untracked, uncommitted — preserved unchanged by this session; it is a design document, not rewritten into this implementation report.
- No Phase 3A source, test, or audit file was uncommitted at start; nothing was staged.

---

## 2. Exact files created and changed

**Created (production, pure — `zcylas.totality.api.rpg.resources.client`):**
- `src/main/java/zcylas/totality/api/rpg/resources/client/ClientResourceSource.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/ClientResourceTrust.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/ClientResourceUnavailableReason.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/ClientResourceQueryResult.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/ClientResourceReader.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/ClientResourceReaderRegistry.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/ClientResourceService.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/NativeResourceAccess.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/NativeClientResourceReader.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/GenericSyncResourceAccess.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/GenericSyncClientResourceReader.java`

**Created (production, pure — `zcylas.totality.api.rpg.resources.sync`):**
- `src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResourceSyncRejectionDiagnostics.java`

**Created (production, client-only impure):**
- `src/main/java/zcylas/totality/networking/resource/ClientResourceSyncBridge.java` (`@Environment(EnvType.CLIENT)`, in the existing `networking.resource` package so it can call `ClientResourceSyncManager`'s package-private accessors)
- `src/main/java/zcylas/totality/client/resource/MinecraftNativeResourceAccess.java` (`@Environment(EnvType.CLIENT)`, new package)
- `src/main/java/zcylas/totality/client/resource/TotalityClientResourceReaders.java` (`@Environment(EnvType.CLIENT)`, new package)

**Modified:**
- `src/main/java/zcylas/totality/networking/resource/ClientResourceSyncManager.java` — added a package-private `isResyncPending()` accessor next to the existing package-private `state()`; added bounded DEBUG logging for `MALFORMED`/`INCOMPATIBLE_SCHEMA` outcomes in both `applyFull` and `applyDelta`. `state()`'s visibility was **not** changed (still package-private). No change to any apply/clear/revision/mutation logic.
- `src/main/java/zcylas/totality/TotalityClient.java` — added one call, `TotalityClientResourceReaders.register()`, at the end of `onInitializeClient()`, alongside a short comment. No other line changed.

**Created (tests):**
- `src/test/java/zcylas/totality/api/rpg/resources/client/ClientResourceQueryResultTest.java` (14 tests)
- `src/test/java/zcylas/totality/api/rpg/resources/client/NativeClientResourceReaderTest.java` (5 tests)
- `src/test/java/zcylas/totality/api/rpg/resources/client/ClientResourceServiceTest.java` (18 tests)
- `src/test/java/zcylas/totality/api/rpg/resources/client/FakeGenericSyncResourceAccess.java` (test-only support class, no `@Test` methods)
- `src/test/java/zcylas/totality/api/rpg/resources/sync/ClientResourceSyncRejectionDiagnosticsTest.java` (3 tests)

**Not touched:** every other file in `src/main` and `src/test`, including every legacy manager, every HUD/screen/radial consumer, every existing Phase 1-3A Resource API file other than the two listed above, `/Inspiration Mods`, and all "known unrelated" working-tree entries (generated datagen JSON, `Context/Trading Test/*.png`, `logs/`, `src/main/generated/.cache/`, the Phase 3A review ZIPs).

---

## 3. Final public client API

The façade's public surface (all in `zcylas.totality.api.rpg.resources.client`, pure, no Minecraft/Fabric imports):

```java
ClientResourceService.INSTANCE.query(Identifier resourceId)              // -> ClientResourceQueryResult
ClientResourceService.INSTANCE.queryScalar(Identifier resourceId)        // -> ClientResourceQueryResult
ClientResourceService.INSTANCE.queryPartitioned(Identifier resourceId)   // -> ClientResourceQueryResult
ClientResourceService.INSTANCE.registerReader(Identifier, ClientResourceReader)
```

`ClientResourceService` is also independently instantiable (constructor takes a `PlayerResourceRegistry` and a `ClientResourceReaderRegistry`), mirroring `PlayerResourceService`'s own instantiable-but-singleton-backed convention — tests construct fresh instances rather than sharing the process-wide `INSTANCE`.

Production wiring (`TotalityClientResourceReaders.register()`, called once from `TotalityClient.onInitializeClient()`) registers:
- one shared `NativeClientResourceReader` for `totality:health`, `totality:food`, `totality:breath`;
- one shared `GenericSyncClientResourceReader` for `totality:mana`, `totality:stamina`, `totality:spell_slots`, `totality:rage`.

No production consumer calls any of this yet — registration only.

---

## 4. Result, source, trust, and unavailable semantics

`ClientResourceQueryResult` is a sealed interface with three cases, all immutable:

- `Scalar(resourceId, currentUnits, maximumUnits, overflowUnits, unitScale, source, trust)` — validates `unitScale >= 1`, all quantities `>= 0`, `current <= maximum + overflow` (overflow-safe via `Math.addExact`), exactly mirroring `ResourceScalarWireSnapshot`'s own invariants.
- `Partitioned(resourceId, partitions, unitScale, source, trust)` — `partitions` is a `NavigableMap<Integer, Partition>`, defensively rebuilt into an unmodifiable `TreeMap` in the compact constructor (deterministic ascending order regardless of caller-supplied order); each `Partition(partitionId, currentUnits, maximumUnits, overflowUnits)` self-validates the same non-negative/`current<=max+overflow` rule. A `Partitioned.of(id, List<Partition>, ...)` factory rejects a duplicate partition id before any map is built (mirrors `ResourcePartitionedWireSnapshot`'s own list-based duplicate check).
- `Unavailable(resourceId, reason)` — no numeric fields at all, so a caller cannot accidentally read a fabricated `0` out of it; the compiler forces an explicit branch.

`ClientResourceSource`: `NATIVE_CLIENT_VIEW`, `GENERIC_SYNCHRONIZED_VIEW`.

`ClientResourceTrust`: `FRESH`, `PENDING_RESYNC` only — no `NEVER_SYNCED_YET` member, per the task's explicit instruction; an unsynchronized generic Resource returns `Unavailable(NOT_SYNCHRONIZED_YET)`, never a successful value with a weak trust level.

`ClientResourceUnavailableReason`: `RESOURCE_UNREGISTERED`, `CLIENT_SOURCE_NOT_CONFIGURED`, `NOT_SYNCHRONIZED_YET`, `NOT_AVAILABLE_TO_PLAYER`, `NO_LOCAL_PLAYER`, `MODEL_MISMATCH` — the exact six requested, kept distinct from the server's `ResourceQueryFailureReason` (no shared enum; see the readiness audit §6.1 for the reasoning this implementation follows).

---

## 5. Reader registry architecture

`ClientResourceReader` is the reader-strategy contract (`ClientResourceQueryResult query(PlayerResourceDefinition definition)`), the client-side counterpart to `ExternalPlayerResourceAdapter`. `ClientResourceReaderRegistry` maps `Identifier -> ClientResourceReader`, rejecting a duplicate id registration with `IllegalArgumentException` (never a silent replace), mirroring `PlayerResourceRegistry.register`'s own convention.

`ClientResourceService.query` resolves the canonical `PlayerResourceDefinition` from `PlayerResourceRegistry.INSTANCE` first (never a second hardcoded model/capability database), then looks up a reader for that id; a missing definition is `RESOURCE_UNREGISTERED`, a missing reader is `CLIENT_SOURCE_NOT_CONFIGURED`. After a reader answers, `validateShape` defensively re-checks the returned shape actually matches `definition.model()` and `definition.id()` — exactly like `PlayerResourceService.queryExternal`'s own adapter-output validation — converting any mismatch into `MODEL_MISMATCH` rather than trusting the reader blindly.

`queryScalar`/`queryPartitioned` check the definition's canonical model **before** ever calling a reader: asking for the wrong shape (e.g. `queryScalar(totality:spell_slots)`) is a caller error independent of synchronization state, so it always returns `MODEL_MISMATCH` immediately, even before the first full snapshot.

---

## 6. Narrow Phase 3A read-only bridge

`GenericSyncResourceAccess` (pure interface, `api.rpg.resources.client`) declares exactly four read-only methods: `hasSynced()`, `isResyncPending()`, `scalar(Identifier)`, `partitioned(Identifier)`. It has no apply/clear/request/set/mutate method of any kind — enforced both by design and by a reflection-based test (`ClientResourceServiceTest.genericSyncResourceAccessDeclaresNoMutatingMethod`).

The production implementation, `ClientResourceSyncBridge` (`networking.resource` package, `@Environment(EnvType.CLIENT)`), deliberately lives in the **same package** as `ClientResourceSyncManager` so it can call that class's existing package-private `state()` and the newly-added package-private `isResyncPending()` directly — **`ClientResourceSyncManager.state()` was never made public**, satisfying the task's explicit constraint literally. `ClientResourceSyncBridge` itself exposes no method beyond the four the interface declares; it cannot apply a packet, advance a revision, clear state, or request a resync.

For tests, `FakeGenericSyncResourceAccess` implements the same interface by wrapping freshly-constructed real `ClientResourceSyncState`/`ClientResyncRequestGate` instances (not the process-wide singleton), so façade/reader tests can drive full/delta application and pending-resync transitions exactly like the existing `ClientResourceSyncStateTest`/`ClientResyncRequestGateTest` do, with no shared global state between tests.

---

## 7. Native Health/Food/Breath readers

`NativeResourceAccess` (pure interface) abstracts "the active local player's native vanilla state" into six methods (`hasLocalPlayer`, `health`, `maxHealth`, `foodLevel`, `airSupply`, `maxAirSupply`). `NativeClientResourceReader` (pure) implements `ClientResourceReader` against this interface only — it never touches `Minecraft`/`LocalPlayer` directly, so it is fully unit-testable with a synthetic fake (`NativeClientResourceReaderTest`).

- **Health**: reuses `HealthResourceAdapter.toUnits(float, long)` directly (the same `BigDecimal`/`HALF_UP` fixed-point rounding the server-side adapter uses) for both current and maximum — no second rounding convention introduced. Verified in `healthQueryReturnsNativeSourceFreshTrustAndMatchesAdapterRounding`, which asserts the reader's output equals `HealthResourceAdapter.toUnits(...)` called directly on the same inputs.
- **Food**: reuses `FoodResourceAdapter.NATIVE_MAXIMUM` (`20`) as the maximum; current is the raw `foodLevel()` value. Stays on the native 0-20 mechanical scale — the ×5 display conversion is **not** applied here (see §12).
- **Breath**: reads raw `airSupply()`/`maxAirSupply()`. **[CORRECTED 2026-07-23 — see "Phase 3B-1 External Review Correction" below.]** The original text here claimed this reader matched `BreathResourceAdapter.normalize` "exactly," but the original implementation only reproduced the clamp half of that method's policy (`current = clamp(rawAirSupply, 0, liveMaximum)`) and omitted its `rawMaximum <= 0 → MALFORMED_OWNER_STATE` guard entirely, so a non-positive native maximum fell through to `Math.max(0, rawMaximum)` and produced a fabricated, structurally-valid-looking `0/0` `Scalar` success instead of a failure. The reader now checks `rawMaximum <= 0` first and returns `Unavailable(MALFORMED_SOURCE_STATE)` before any clamping is attempted — only once the maximum is confirmed positive does it clamp current into `[0, maximum]`, which **is** now a complete, corrected mirror of `BreathResourceAdapter.normalize`'s full policy, not just its clamp formula. A synthetic negative (mid-drowning) `airSupply` value with a valid maximum is verified to clamp to `0` (`breathQueryClampsNegativeDrowningRangeToZero`); a non-positive maximum is verified to produce `MALFORMED_SOURCE_STATE`, never a numeric result (`breathZeroMaximumReturnsMalformedSourceState`, `breathNegativeMaximumReturnsMalformedSourceState`, `invalidBreathMaximumNeverBecomesScalarZeroZeroSuccess`).
- **No local player**: `hasLocalPlayer() == false` (production wiring checks both `Minecraft.getInstance()` and `Minecraft.getInstance().level` non-null, in addition to `player`) returns `Unavailable(NO_LOCAL_PLAYER)` before any of the three resource-specific branches run.

The real production access, `MinecraftNativeResourceAccess` (`client.resource` package, `@Environment(EnvType.CLIENT)`, singleton), is a thin wrapper with no normalization logic of its own — all rounding/clamping/result-construction lives in the pure `NativeClientResourceReader`.

---

## 8. Generic Mana/Stamina/slots/Rage readers

`GenericSyncClientResourceReader` (pure) reads only through `GenericSyncResourceAccess`. For a given `PlayerResourceDefinition`:

1. `!hasSynced()` → `Unavailable(NOT_SYNCHRONIZED_YET)`.
2. Otherwise `trust = isResyncPending() ? PENDING_RESYNC : FRESH`.
3. `SCALAR` model: if a partitioned entry is unexpectedly present for this id → `MODEL_MISMATCH`; if no scalar entry is present → `Unavailable(NOT_AVAILABLE_TO_PLAYER)`; otherwise convert the `ResourceScalarWireSnapshot` into a `ClientResourceQueryResult.Scalar` with `source = GENERIC_SYNCHRONIZED_VIEW`.
4. `PARTITIONED_POOL` model: symmetric handling via `ResourcePartitionedWireSnapshot` → `ClientResourceQueryResult.Partitioned`.

This never calls `PlayerResourceService.query(...)` — confirmed structurally unavailable client-side for these four `LEGACY_BESPOKE_SYNCHRONIZATION` resources per the readiness audit §2.5 (`ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE`). The reader instead reads the Phase 3A wire snapshots directly through the bridge described in §6.

---

## 9. Rage absence-versus-0/0 behavior

Verified by `ClientResourceServiceTest`:
- `omittedGenericResourceReturnsNotAvailableToPlayer` — a full snapshot that never includes `totality:rage` produces `Unavailable(NOT_AVAILABLE_TO_PLAYER)` when Rage is queried, never a fabricated `0/0` success.
- `presentRageZeroZeroIsAValidScalarSuccess` — a full snapshot explicitly including `totality:rage` at `0/0` produces a genuine `Scalar` success with `currentUnits=0, maximumUnits=0` — never misclassified as unavailable.

Both cases are distinguished purely by whether `access.scalar(RAGE)` is present, exactly mirroring the Phase 3A wire-level distinction already proven by `PlayerResourceSyncStateTest`'s `missingRageIsOmittedRatherThanEncodedAsAValidZeroPool`/`validRageZeroZeroRemainsRepresentable`. No new logic was needed to preserve this — the façade simply does not collapse "absent" and "present-but-zero" the way the legacy `PlayerChargesComponent` client mirror does.

---

## 10. Spell-slot partition behavior

- Partition keys are preserved exactly as received (1-based spell levels), never renumbered to 0-based.
- `spellSlotKeysRemainOneToTenAscending` feeds ten partitions in descending insertion order (10 down to 1) and asserts the resulting `Partitioned.partitions().keySet()` iterates as `[1, 2, ..., 10]` — deterministic ascending order regardless of wire/insertion order, matching `ResourcePartitionedWireSnapshot`'s own guarantee.
- `allZeroSpellSlotsRemainValid` confirms an all-zero ten-partition snapshot is a genuine `Partitioned` success (the valid "non-caster" state), never `NOT_AVAILABLE_TO_PLAYER`.
- No flattening: `queryPartitioned`/`query` never collapse partitions into a single scalar value, and `queryScalar(totality:spell_slots)` is rejected as `MODEL_MISMATCH` rather than silently returning some aggregate.

---

## 11. Pending-resync behavior

`pendingResyncPreservesLastValueAndReportsPendingResync`: after a full snapshot is accepted and `access.gate.requestIfNotPending()` is invoked (simulating a detected revision gap), querying the same resource again still returns the **same** `currentUnits`/`maximumUnits` as before — the value is never blanked — but `trust()` is now `PENDING_RESYNC` instead of `FRESH`. This is a direct consequence of `GenericSyncClientResourceReader` reading `isResyncPending()` only to choose the trust label, never to gate whether a value is returned at all.

---

## 12. Display-conversion boundary

No display conversion is applied anywhere in this façade:
- Health stays in its `HealthResourceAdapter.UNIT_SCALE` (1000) fixed-point mechanical units.
- Food stays on the native 0-20 mechanical scale (`foodQueryReturnsRawNativeZeroToTwentyScale` explicitly asserts `maximumUnits == 20`, not `100`).
- Mana/Stamina/spell-slot/Rage units are passed through unchanged from their wire snapshots (`unitScale = 1` for all four in production).
- No new `×5` (or any other) display literal was introduced anywhere in this change. `ResourceDisplayConversion.HEALTH_FOOD` (the existing shared conversion) is not referenced by any new file — applying it remains entirely the responsibility of a future presentation-layer caller, exactly as the task specifies.

---

## 13. Respawn decision and rationale

No new client-side respawn-clear hook was added, per the task's explicit instruction. Rationale recorded (matching the task's own reasoning):
- The server already schedules an authoritative full snapshot after `ServerPlayerEvents.AFTER_RESPAWN` (`ResourceSyncLifecycleEvents`, unchanged).
- That full snapshot atomically replaces the client's entire generic view (`ClientResourceSyncState.applyFull` clears and repopulates both maps in one call) — there is no partial-application window a pre-clear could meaningfully shorten.
- No production consumer reads the façade yet, so there is no user-visible flicker risk to weigh against added complexity.
- No clearly superior client respawn lifecycle event was identified or needed.

Existing clear hooks (`JOIN`, `DISCONNECT`, `AFTER_CLIENT_LEVEL_CHANGE`) were left completely untouched. See §19 for the corresponding manual validation case.

---

## 14. Revision-visibility decision

No raw Phase 3A revision number is exposed anywhere in `ClientResourceQueryResult`, `ClientResourceTrust`, or any other public façade type — consumers get `source`, `FRESH`/`PENDING_RESYNC`, and structured availability only, never protocol revision arithmetic. No broad diagnostics API was added. The only revision-adjacent addition is the narrow, package-private `isResyncPending()` accessor on `ClientResourceSyncManager` (used solely by `ClientResourceSyncBridge`) and the bounded log messages described below, which include revision numbers only inside a DEBUG-level diagnostic string, not as part of any typed public API. The final diagnostics surface remains explicitly deferred to Phase 3B-3, per the task.

---

## 15. Malformed/incompatible logging

`ClientResourceSyncManager.applyFull`/`applyDelta` now log at DEBUG (via `Totality.LOGGER.debug(...)`) exactly when the outcome is `MALFORMED` or `INCOMPATIBLE_SCHEMA` — never for `STALE_IGNORED`/`REVISION_GAP` (unchanged from Phase 3A behavior; `REVISION_GAP` still only logs its existing "requesting resync" message). The log message is built by the new pure `ClientResourceSyncRejectionDiagnostics.describeFull`/`describeDelta`, which includes only: payload type ("full snapshot"/"delta"), schema version, base revision (delta only), payload revision, current-held revision, and the `ApplyResult` itself — never resource ids, scalar values, or partition contents. `ClientResourceSyncRejectionDiagnosticsTest` verifies the message contains exactly this bounded metadata and never contains a `totality:`-namespaced string (i.e., never a resource id).

No apply behavior changed: `STATE.applyFull(payload)`/`STATE.applyDelta(payload)` are called exactly as before Phase 3A; the new code only branches on the already-existing `ApplyResult` afterward to decide whether to log. No new resync is triggered by this logging (only the pre-existing `REVISION_GAP` branch sends a resync request, unchanged). No gameplay-visible chat/HUD/toast output was added.

---

## 16. Client/server classloading boundary

- All eleven new files under `api.rpg.resources.client` plus `ClientResourceSyncRejectionDiagnostics` (in `api.rpg.resources.sync`) have **zero** Minecraft-client-only or Fabric-client-only imports — confirmed by successful `compileJava`/`compileTestJava`/`test` runs with no client bootstrap, and by the reader tests running as plain JUnit against synthetic access implementations.
- `ClientResourceSyncBridge`, `MinecraftNativeResourceAccess`, and `TotalityClientResourceReaders` are the three new classes that do touch client-only state (directly or by depending on `ClientResourceSyncManager`); all three are annotated `@Environment(EnvType.CLIENT)` — a new annotation added only to these new classes, not retrofitted onto any pre-existing Resource API class (`ClientResourceSyncManager`, `ClientResourceSyncState`, etc. remain exactly as Phase 3A left them).
- No new client-only class is referenced from `Totality`, `ModEvents`, server networking registration, server lifecycle code, or any common static initializer a dedicated server loads. The single new call site is `TotalityClient.onInitializeClient()` (the existing `ClientModInitializer` entrypoint) — the same place Phase 3A's own `ClientResourceSyncManager` lifecycle hooks were wired.
- Fabric's loader ordering guarantee (`ModInitializer.onInitialize()` before any `ClientModInitializer.onInitializeClient()`) was confirmed by inspection: `Totality.onInitialize()` calls `registerApi()` → `ProductionResourceDefinitions.register()` at line 167, so `PlayerResourceRegistry.INSTANCE` is always frozen and populated before `TotalityClientResourceReaders.register()` runs.
- Logical-client-thread assumption (documented, not enforced with new locking): `GenericSyncClientResourceReader`/`NativeClientResourceReader`/`ClientResourceService` all assume single-threaded access consistent with the existing Fabric callback model (packet receipt and tick callbacks on the client network/render thread) — exactly the same unstated-but-established assumption `ClientResourceSyncState`/`ClientResourceSyncManager` already rely on. No lock or `synchronized` block was added to any new class beyond `ClientResourceReaderRegistry.register`'s existing `synchronized` (mirroring `PlayerResourceRegistry.register`'s own registration-time-only synchronization, not a runtime query-path lock).

---

## 17. Tests added

40 new tests across four files (519 pre-existing + 40 new = 559 total, all passing):

| File | Count | Covers |
|---|---|---|
| `ClientResourceQueryResultTest` | 14 | Items 1-8: scalar/partitioned construction+validation, immutable partition map, deterministic ordering, overflow preservation (scalar+partition), duplicate partition id rejection, negative/invalid value rejection |
| `NativeClientResourceReaderTest` | 5 | Items 24-28: native Health/Food/Breath reads, negative-air clamp, no-local-player, Health rounding parity with `HealthResourceAdapter.toUnits` |
| `ClientResourceServiceTest` | 18 | Items 9-23, 29-31: registered scalar/partitioned success after full sync, pre-full `NOT_SYNCHRONIZED_YET`, omitted-resource `NOT_AVAILABLE_TO_PLAYER` (incl. Rage), unknown id, no-reader-configured, both typed model-mismatch directions, stored-shape-contradicts-definition, pending-resync value preservation, fresh trust, Rage 0/0 success, spell-slot ordering (1-10) and all-zero validity, duplicate reader registration rejection, reflection-based "no mutating method" checks on both access interfaces, lifecycle-clear-to-`NOT_SYNCHRONIZED_YET` |
| `ClientResourceSyncRejectionDiagnosticsTest` | 3 | Items 32-33 (at the layer that is actually pure-Java-testable — see rationale below) |

**Deliberate deviation on tests 32/33**: the task asked for tests proving "malformed payload logging does not mutate state" directly against `ClientResourceSyncManager`. This codebase's own established precedent (`ClientResyncRequestGateTest`'s Javadoc) is that `ClientResourceSyncManager` is not directly unit-tested because of its network dependency. Rather than break that precedent by touching the process-wide static singleton in a new test, this implementation instead: (a) extracted the log-message construction into a pure, dependency-free `ClientResourceSyncRejectionDiagnostics` class, fully unit-tested for exactly the bounded-content requirement (item's real intent), and (b) relies on code review plus the **unchanged, still-passing** `ClientResourceSyncStateTest` suite (which already exhaustively proves `STATE.applyFull`/`applyDelta` never mutate on `MALFORMED`/`INCOMPATIBLE_SCHEMA`) to establish that the new logging — which only reads `STATE.revision()` before the call and branches on the already-existing `ApplyResult` afterward — cannot itself introduce a mutation. This is recorded here transparently as a deliberate scope decision, not a silent gap.

No existing test was modified, weakened, or removed. `ClientResourceSyncManagerTest` still does not exist, consistent with prior precedent.

---

## 18. Validation results

Run from `feature/general-resource-api`, in order:

- **`compileJava`**: `BUILD SUCCESSFUL in 14s`, 1 actionable task executed. No compiler warnings.
- **`compileTestJava`**: `BUILD SUCCESSFUL in 2s`. No compiler warnings.
- **`test`**: first run failed 1 of 560 (`genericSyncResourceAccessDeclaresNoMutatingMethod` — a bug in the *test's own* forbidden-word list, which included the substring `"resync"` and so flagged the legitimate read-only method `isResyncPending()`; fixed by narrowing the forbidden-word list to actual mutating verbs `apply`/`clear`/`request`/`set`/`mutate`). Second run: **`BUILD SUCCESSFUL`**, all tests passed.
- **Exact total test count**: **559** (confirmed by summing `<testsuite tests="...">` across all `build/test-results/test/*.xml`).
- **Exact passed/failed count**: **559 passed, 0 failed, 0 errors, 0 skipped.**
- **New tests added**: 40 (14 + 5 + 18 + 3), confirmed by `grep -c '@Test'` across the four new test files; 559 − 40 = 519, matching the Phase 3A baseline exactly.
- **`runDatagen`**: `BUILD SUCCESSFUL in 16s`. Datagen summary: `total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0` — **zero generated-file changes** from this session's code (this Phase 3B-1 change touches no datagen provider, block, item, recipe, or loot table).
- **`build`**: `BUILD SUCCESSFUL in 3s` (mostly `UP-TO-DATE` after the prior `test`/`compileJava` runs already validated everything). Only pre-existing Gradle/Loom deprecation notices appeared (unrelated to this change, present before this session).
- **`git diff --check`**: no whitespace errors reported for any changed/new `src/main/java`/`src/test/java` file (only a pre-existing LF→CRLF normalization notice on the one modified file, consistent with the repository's existing line-ending convention).
- **Post-datagen diff inspection**: `git status --short src/main/generated` after `runDatagen` shows the **exact same 21 files** as the pre-existing baseline noted in the Phase 3B checkpoint — no new or additionally-changed generated file.

---

## 19. Manual smoke-test checklist

Per the task's instruction, this is **documented, not fabricated** — none of the following were actually run against a live client/server this session (that would require launching the game, which was out of scope for this implementation pass):

1. Client startup — not run.
2. Dedicated-server startup — not run.
3. Join an existing character — not run.
4. Query all seven Resources through a temporary debugger/breakpoint without changing a production consumer — not run (would require attaching a debugger to a live `runClient` session against `ClientResourceService.INSTANCE.query(...)`).
5. Confirm Health/Food/Breath report `NATIVE_CLIENT_VIEW`/`FRESH` — logic verified by unit test (`NativeClientResourceReaderTest`); not manually verified in a running client.
6. Confirm Mana/Stamina/slots/Rage report `GENERIC_SYNCHRONIZED_VIEW` after the first full snapshot — logic verified by unit test (`ClientResourceServiceTest`); not manually verified live.
7. Confirm ungranted Rage reports `NOT_AVAILABLE_TO_PLAYER` — unit-tested; not manually verified live.
8. Confirm granted Rage at 0/0 remains a valid success — unit-tested; not manually verified live.
9. Confirm spell slots expose levels 1-10 — unit-tested; not manually verified live.
10. Disconnect/reconnect and confirm pre-full queries report `NOT_SYNCHRONIZED_YET` — the underlying `ClientResourceSyncManager.clear()` → `hasSynced()==false` path is unchanged from Phase 3A (already covered by `ClientResourceSyncStateTest.clearResetsToNeverSyncedState`); the façade's translation of that into `NOT_SYNCHRONIZED_YET` is unit-tested (`lifecycleClearReturnsGenericQueriesToNotSynchronizedYet`); not manually verified live.
11. Change dimension and confirm the clear/full replacement lifecycle — unchanged Phase 3A hook (`AFTER_CLIENT_LEVEL_CHANGE`); not manually verified live this session.
12. Die/respawn and confirm the subsequent full snapshot replaces the old view without requiring a client pre-clear — this is the exact behavior the §13 respawn decision relies on; not manually verified live this session.
13. Trigger a resync gap if practical and confirm last values remain readable with `PENDING_RESYNC` — unit-tested at the façade layer (`pendingResyncPreservesLastValueAndReportsPendingResync`); the underlying gap/retry mechanics are unchanged Phase 3A behavior already covered by `ClientResyncRequestGateTest`; not manually verified live this session.
14. Confirm all current HUDs/screens/radials remain visually and behaviorally unchanged — no production consumer file was modified (see §20), so no visual/behavioral change is possible from this diff; not manually re-screenshotted this session.

No temporary production UI was added for this checklist, per the task's instruction.

---

## 20. Exact legacy consumers intentionally preserved

None of the following were read, modified, or otherwise touched by this implementation:
- `TotalityHudRenderer` (Mana/Stamina reads via `ClientManaManager`/`ClientStaminaManager`; native Health/Food HUD reads; the existing `resourceDisplayCurrentMax` generic-query usage for Health/Food numeric text).
- `OverviewTab`.
- The Rage secondary-HUD `ISecondaryResource` registration in `TotalityClient.registerRenderers()`.
- `ClassTab`.
- `SpellRadialScreen`.
- `ClientManaManager`, `SyncManaPayload`, `ManaServerTick`.
- `ClientStaminaManager`, `SyncStaminaPayload`, `StaminaServerTick`.
- `ClientSpellSlotManager`.
- `PlayerChargesComponent` (both its authority and its existing client-side mirror behavior).
- `TotalityMovementHandler` — explicitly left untouched; it remains gameplay/prediction logic reading `ClientStaminaManager` directly and must never be changed to trust this presentation façade.
- `PlayerResourceComponent`, `SpellSlotComponent` authority, `PlayerResourceRecalculator`, and every existing Resource dirty-marking hook.
- The full Phase 3A full/delta/resync protocol semantics (`ResourceSyncProtocol`, `ResourceFullSyncPayload`, `ResourceDeltaSyncPayload`, `ResourceResyncRequestPayload`, `PlayerResourceSyncState`, `ResourceSyncManager`, `ResourceSyncLifecycleEvents`, `ResourceSyncServerTick`, `ResourceResyncRateLimiter`, `ResourceResyncRequestHandler`) — none of these files were modified.
- Existing Rest recovery and existing resource formulas (Mana/Stamina max calculation, spell-slot table, Rage charge table) — untouched.

---

## 21. Known limitations deferred to Phase 3B-2/3B-3/Phase 3C

- No shadow-parity engine, legacy-mirror comparison, grace window, mismatch classification, parity logging, or parity debug command — all explicitly out of scope for this slice, none implemented.
- No HUD/menu/radial/tooltip/debug consumer reads `ClientResourceService` yet — Phase 3C scope.
- No raw-revision diagnostics UI or broad diagnostics API — Phase 3B-3 scope; only the narrow, non-public `isResyncPending()` bridge accessor and the bounded DEBUG log strings exist today.
- The open question recorded in the readiness audit (§17.1: whether a client-side respawn-clear hook is ever needed) remains explicitly **not** resolved by adding one — resolved instead by the documented decision in §13 to rely on the server's atomic full-snapshot replacement.
- The open question about class-aware Rage parity classification (readiness audit §17.2) is entirely Phase 3B-2 scope and was not touched.
- `ClientResourceSyncManager` itself remains untested directly (network-dependency precedent preserved, per §17's deliberate-deviation note above).
- `TotalityClientResourceReaders`/`MinecraftNativeResourceAccess`/`ClientResourceSyncBridge` are the three genuinely untestable-without-a-client classes in this change (mirroring the same limitation `ClientResourceSyncManager` already had); all logic beyond thin delegation was deliberately kept out of them.

---

## 22. Confirmation: no authority, gameplay, visual output, or production consumer changed

- **Authority**: unchanged for all seven Resources — Health/Food/Breath remain vanilla-authoritative; Mana/Stamina remain `PlayerResourceComponent`/`PlayerManaManager`/`PlayerStaminaManager`-authoritative; standard spell slots remain `SpellSlotComponent`-authoritative; Rage remains `PlayerChargesComponent`-authoritative. No adapter, definition, or component file was modified.
- **Gameplay**: no spell casting, combat, resource spend/restore/regeneration, or class logic was touched. The façade has no mutation capability at all (§6, §8 — both access interfaces are read-only by construction).
- **Visual output**: no HUD, screen, menu, or tooltip file was modified; nothing new renders anything. `TotalityClientResourceReaders.register()` only populates an internal reader registry that nothing yet queries for display.
- **Production consumers**: confirmed zero changes to any of the files listed in §20.

## 23. Confirmation: unrelated files and `/Inspiration Mods` untouched

- `/Inspiration Mods` was not read, searched, or referenced at any point during this implementation.
- The pre-existing "known unrelated" working-tree entries (modified generated datagen JSON under `src/main/generated/data/**`, `Context/Trading Test/*.png`, `logs/`, `src/main/generated/.cache/`, the three Phase 3A review ZIPs under `Context/Audit/Review Bundles/`) were not modified, staged, committed, reset, restored, or cleaned — `git status --short` before and after this implementation shows the identical set of these entries (see §18's post-datagen diff inspection).
- The Phase 3B readiness audit document was preserved exactly as written by the prior audit pass; this report is a new, separate file.

---

## Stop point — status

- Implementation, tests, validation (`compileJava`, `compileTestJava`, `test`, `runDatagen`, `build`, `git diff --check`), and this report are complete.
- Nothing was staged, committed, or pushed.
- Phase 3B-2 (shadow parity) was not started.

---

# PHASE 3B-1 EXTERNAL REVIEW CORRECTION (2026-07-23)

A narrow, targeted correction applied after external review of the Phase 3B-1 implementation above.
Scope: exactly the four corrections below. No shadow parity, no consumer migration, no Phase 3B-2
work, and no change to Resource authority, Phase 3A synchronization, legacy packets/managers, HUD/
screen/radial/movement-handler code, display conversion, client lifecycle hooks, the respawn decision,
or malformed/incompatible-packet logging.

## The defect

`NativeClientResourceReader.breath(...)` computed:

```java
long maximum = Math.max(0, rawMaximum);
long current = Math.max(0, Math.min(rawCurrent, rawMaximum));
return scalar(id, current, maximum, unitScale);   // always a Scalar success
```

For a non-positive `rawMaximum` (`0` or negative), this produced `maximum = 0` and
`current = Math.max(0, Math.min(rawCurrent, rawMaximum))` — which, since `rawMaximum <= 0`, always
evaluates to `0` — and returned that as a genuine `Scalar` **success** at `0/0`. The authoritative
server-side `BreathResourceAdapter.normalize(Identifier, int rawCurrent, int rawMaximum, long unitScale)`
does not do this: it checks `rawMaximum <= 0` **first** and returns
`ResourceQueryResult.Failure(MALFORMED_OWNER_STATE, resourceId)` — never a snapshot of any kind — and
only clamps `current` into `[0, rawMaximum]` once the maximum is confirmed positive.

### Why the fabricated 0/0 was incorrect

A `0/0` Breath value is indistinguishable, at the type level, from a genuine "no reserve, fully
depleted, valid maximum of 0" state — except no such state can ever legitimately occur (vanilla's
`Entity.getMaxAirSupply()` baseline is `300`; a non-positive value can only arise from a future
override or a genuinely corrupt/misbehaving source). Silently accepting it as a success would let a
malformed native source masquerade as ordinary depleted Breath to any future HUD/tooltip/debug
consumer — exactly the "malformed native source becomes a fabricated valid Resource" failure mode the
whole façade's design (§6, §9 above) exists to prevent structurally. The client implementation
report's original §7 claimed this reader matched `BreathResourceAdapter.normalize` "exactly," which
was true only for the clamp formula, not for the guard that precedes it — that statement has been
corrected in place above.

## Correction 1 — new unavailable reason: `MALFORMED_SOURCE_STATE`

Added to `ClientResourceUnavailableReason` (`api/rpg/resources/client/ClientResourceUnavailableReason.java`):

> The Resource is registered and its client source exists (a reader answered), but the source
> returned state that cannot be represented as a valid client Resource result.

Kept structurally distinct from all six existing reasons — in particular from `NOT_AVAILABLE_TO_PLAYER`,
which means "this Resource genuinely does not apply to/has not been granted to this player" (a
well-formed absence), never "the source's answer is corrupt." No existing reason was renamed,
removed, or repurposed.

## Correction 2 — Breath normalization parity

`NativeClientResourceReader.breath(Identifier, long)` now:

1. Reads `rawMaximum = access.maxAirSupply()`.
2. If `rawMaximum <= 0`, returns `Unavailable(MALFORMED_SOURCE_STATE)` immediately — no clamping, no
   `Scalar` construction attempted at all.
3. Otherwise clamps `current = Math.max(0, Math.min(rawCurrent, rawMaximum))` (negative
   drowning-timer values become `0`; a value above `rawMaximum` becomes `rawMaximum`) and returns a
   normal `NATIVE_CLIENT_VIEW`/`FRESH` `Scalar` success.

This is now a complete mirror of `BreathResourceAdapter.normalize`'s full policy (guard **and**
clamp), not just its clamp formula. Vanilla air authority, synchronization, depletion, drowning
damage, and the vanilla air-bubble HUD element are entirely untouched — this reader only ever reads
`access.airSupply()`/`access.maxAirSupply()`, exactly as before.

## Correction 3 — safe native source validation (Health, Food, Breath)

The three native readers can no longer let a validation exception escape the public façade. A new
private helper, `NativeClientResourceReader.safeScalar(Identifier, long, long, long, long)`, attempts
to construct a `ClientResourceQueryResult.Scalar` and narrowly catches exactly the two exception
types that record's own compact constructor (and `HealthResourceAdapter.toUnits`) are documented to
throw — `IllegalArgumentException` and `ArithmeticException` — converting either into
`Unavailable(MALFORMED_SOURCE_STATE)`. No other exception type is caught; this is not a broad
catch-all.

- **Health** (`health(Identifier, long)`): `HealthResourceAdapter.toUnits` is called for both current
  and maximum inside its own narrow try/catch (the same two exception types), covering non-finite
  input and fixed-point overflow before `safeScalar` is ever reached; `safeScalar` itself then catches
  a negative converted value or `current > maximum` (no overflow capability represented) — the exact
  same invariants `ClientResourceQueryResult.Scalar`'s compact constructor already enforces. Health is
  **not** clamped — its authoritative adapter defines no current-value clamp policy, only rejection.
- **Food** (`query`, `FOOD` branch): passes `access.foodLevel()`/`FoodResourceAdapter.NATIVE_MAXIMUM`
  straight into `safeScalar` with no separate range check — a negative or above-native-maximum Food
  level is caught by the exact same shared validation, converting it to `MALFORMED_SOURCE_STATE`
  rather than a clamp. Food is likewise never clamped — `FoodResourceAdapter` defines no clamp policy
  either.
- **Breath**: see Correction 2 — the one native Resource whose authoritative adapter explicitly
  defines a current-value clamp, applied only once the maximum guard has already passed.

Ordinary valid reads are completely unchanged: Health still uses `HealthResourceAdapter.toUnits`,
Food still returns raw native 0-20 units, Breath still returns raw native air units, and all three
still report `source = NATIVE_CLIENT_VIEW` / `trust = FRESH` on success.

## Correction 4 — canonical unit-scale validation (generic synchronized reads)

`GenericSyncClientResourceReader.query` now compares the Phase 3A wire snapshot's `unitScale()`
against the canonical `PlayerResourceDefinition.unitScale()` for both the `SCALAR` and
`PARTITIONED_POOL` branches, immediately after the existing scalar-vs-partitioned contradiction and
absence checks and immediately before converting the wire snapshot into a public result. A mismatch
returns `Unavailable(MODEL_MISMATCH)` — the wire value is never converted between scales, never
reinterpreted at either scale, and `ClientResourceSyncState`/the wire records themselves are never
touched or modified (this is a pure read-then-compare check against an already-retrieved
`Optional` value). All pre-existing checks are unchanged and unaffected: the scalar-vs-partitioned
contradiction check, `NOT_AVAILABLE_TO_PLAYER` absence handling, Rage absence-vs-`0/0`, and spell-slot
partition ordering/all-zero validity all still apply exactly as before, now simply followed by one
additional scale check before a result is finally returned.

## Exact files changed

- `src/main/java/zcylas/totality/api/rpg/resources/client/ClientResourceUnavailableReason.java` —
  added `MALFORMED_SOURCE_STATE`.
- `src/main/java/zcylas/totality/api/rpg/resources/client/NativeClientResourceReader.java` —
  rewritten: `breath` now guards `rawMaximum <= 0`; new `health` method wraps
  `HealthResourceAdapter.toUnits` calls; new shared `safeScalar` helper replaces the old unconditional
  `scalar` helper for Health/Food/Breath, catching `IllegalArgumentException`/`ArithmeticException`
  narrowly.
- `src/main/java/zcylas/totality/api/rpg/resources/client/GenericSyncClientResourceReader.java` —
  added a `wire.unitScale() != definition.unitScale()` check in both the `SCALAR` and
  `PARTITIONED_POOL` branches.
- `src/test/java/zcylas/totality/api/rpg/resources/client/NativeClientResourceReaderTest.java` — 10
  new tests (see below).
- `src/test/java/zcylas/totality/api/rpg/resources/client/ClientResourceServiceTest.java` — 3 new
  tests plus a `scalarWithScale(...)` test helper.

No other file was changed by this correction. `ClientResourceQueryResult`, `ClientResourceService`,
`ClientResourceSource`, `ClientResourceTrust`, `ClientResourceReaderRegistry`, `ClientResourceReader`,
`NativeResourceAccess`, `GenericSyncResourceAccess`, `ClientResourceSyncBridge`,
`MinecraftNativeResourceAccess`, `TotalityClientResourceReaders`, `ClientResourceSyncManager`,
`ClientResourceSyncRejectionDiagnostics`, `TotalityClient`, and every legacy manager/HUD/screen/radial/
movement-handler file are all untouched by this correction.

## Tests added (13 new; 559 pre-existing + 13 = 572)

`NativeClientResourceReaderTest` (5 → 15, +10):
- `breathZeroMaximumReturnsMalformedSourceState`
- `breathNegativeMaximumReturnsMalformedSourceState`
- `breathCurrentAboveValidMaximumClampsToMaximum`
- `invalidBreathMaximumNeverBecomesScalarZeroZeroSuccess`
- `nonFiniteHealthCurrentReturnsMalformedSourceState`
- `nonFiniteHealthMaximumReturnsMalformedSourceState`
- `overflowingHealthConversionReturnsMalformedSourceState`
- `healthCurrentAboveMaximumReturnsMalformedSourceStateRatherThanThrowing`
- `foodBelowZeroReturnsMalformedSourceState`
- `foodAboveNativeMaximumReturnsMalformedSourceState`

(`breathQueryClampsNegativeDrowningRangeToZero`, already present, continues to cover "negative
current with a valid maximum clamps to zero"; `healthQueryReturnsNativeSourceFreshTrustAndMatchesAdapterRounding`,
`foodQueryReturnsRawNativeZeroToTwentyScale`, and `breathQueryReturnsRawAirSupplyValues`, all
unmodified, continue to cover "valid Health/Food/Breath results remain unchanged.")

`ClientResourceServiceTest` (18 → 21, +3):
- `scalarUnitScaleMismatchReturnsModelMismatch`
- `partitionedUnitScaleMismatchReturnsModelMismatch`
- `matchingUnitScaleStillSucceeds`

(`missingRageIsOmittedRatherThanEncodedAsAValidZeroPool`-equivalent tests
`omittedGenericResourceReturnsNotAvailableToPlayer`/`presentRageZeroZeroIsAValidScalarSuccess` and the
spell-slot tests `spellSlotKeysRemainOneToTenAscending`/`allZeroSpellSlotsRemainValid`, all unmodified
and all still using unit-scale `1L` matching their canonical definitions, continue to pass unaffected
by the new unit-scale check.)

## Final validation results

- `compileJava`: `BUILD SUCCESSFUL`. No compiler warnings.
- `compileTestJava`: `BUILD SUCCESSFUL`. No compiler warnings.
- `test` (forced `--rerun`): `BUILD SUCCESSFUL`. **572 tests completed, 0 failed, 0 skipped, 0 errors.**
  559 pre-existing (519 Phase-1-through-3A + 40 original Phase 3B-1) + 13 new correction tests = 572.
- `runDatagen`: `BUILD SUCCESSFUL`. `total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0` — unchanged, zero generated-file impact.
- `build`: `BUILD SUCCESSFUL`.
- `git diff --check` (`src/main/java`, `src/test/java`): exit 0, no whitespace errors (only the
  pre-existing LF→CRLF normalization notice on `ClientResourceSyncManager.java`, unrelated to this
  correction and present since Phase 3B-1).
- No compiler, test, datagen, or build warning beyond the pre-existing Gradle/Loom deprecation
  boilerplate (present before this session, unrelated to this correction).

## Confirmation: no authority, gameplay, visual output, consumer, lifecycle, or synchronization behavior changed

- **Authority**: unchanged — this correction touches only client-side presentation/validation code;
  no adapter, definition, component, or server-side file was modified.
- **Gameplay**: unchanged — no spend/restore/regeneration/casting logic exists in any touched file.
- **Visual output**: unchanged — no HUD, screen, menu, radial, or tooltip file was touched.
- **Production consumers**: unchanged — the same zero production consumers read the façade as before
  this correction (see the original report's §20); this correction only changes what the façade
  itself returns for previously-mishandled edge cases.
- **Client lifecycle hooks**: unchanged — `JOIN`/`DISCONNECT`/`AFTER_CLIENT_LEVEL_CHANGE` clearing and
  the respawn decision are untouched; `TotalityClient.java` was not modified by this correction.
- **Phase 3A synchronization**: unchanged — `ClientResourceSyncState`, `ClientResourceSyncManager`,
  `ClientResourceSyncBridge`, the full/delta/revision/resync protocol, and the malformed/incompatible
  packet logging added in the original Phase 3B-1 pass are all untouched by this correction; the new
  unit-scale check in `GenericSyncClientResourceReader` only reads an already-retrieved `Optional`
  value, never the sync state itself.

## Stop point — status (correction)

- Correction implementation, tests, validation, and this report update are complete.
- Nothing was staged, committed, or pushed.
- Phase 3B-2 (shadow parity) was not started.

---

# PHASE 3B-1 MANUAL VALIDATION CONFIRMATION (2026-07-23)

Stefan completed the requested Phase 3B-1 manual validation checklist (see §19 above and the
external-review correction's checklist) on a live client and a dedicated server, and reported that all
requested Phase 3B-1 checks were correct. This section records only evidence actually observed or
explicitly reported by Stefan — no packet capture, timing measurement, raw revision number, network
ordering evidence, or value not reported below is claimed.

1. **Client startup** succeeded.

2. **Native Health query** succeeded, returning:
   - `resourceId`: `totality:health`
   - `currentUnits`: `22000`
   - `maximumUnits`: `22000`
   - `overflowUnits`: `0`
   - `unitScale`: `1000`
   - `source`: `NATIVE_CLIENT_VIEW`
   - `trust`: `FRESH`

   This corresponds to `22.0` mechanical Health and Totality's existing `110` displayed Health through
   the separate, unmodified ×5 presentation conversion (`ResourceDisplayConversion.HEALTH_FOOD`) —
   confirming the façade's raw units and the existing display layer remain correctly related without
   this reader applying any display conversion itself (§12 above).

3. **Native Breath query** succeeded, returning:
   - `resourceId`: `totality:breath`
   - `currentUnits`: `300`
   - `maximumUnits`: `300`
   - `overflowUnits`: `0`
   - `unitScale`: `1`
   - `source`: `NATIVE_CLIENT_VIEW`
   - `trust`: `FRESH`

4. **Standard spell slots** succeeded as a `Partitioned` result with 10 partitions, integer levels 1
   through 10, `GENERIC_SYNCHRONIZED_VIEW` source, `FRESH` trust. The tested character was a Barbarian
   (no spellcasting class), so the valid all-zero partition state was expected and observed — matching
   the "all-zero ten-partition state remains valid" behavior proven by
   `allZeroSpellSlotsRemainValid`/`spellSlotKeysRemainOneToTenAscending`.

5. **Rage** succeeded for the Barbarian, returning:
   - `currentUnits`: `1`
   - `maximumUnits`: `2`
   - `overflowUnits`: `0`
   - `unitScale`: `1`
   - `source`: `GENERIC_SYNCHRONIZED_VIEW`
   - `trust`: `FRESH`

6. **Reconnecting** triggered a new full Resource snapshot breakpoint and execution resumed correctly.

7. **Dimension-transfer lifecycle** testing completed successfully.

8. **Death and respawn** testing completed successfully.

9. **Existing Resource HUDs, screens, Rage display, Class tab, Overview tab, and spell radial**
   remained operational and unchanged — confirming no visual/behavioral regression from this façade's
   addition, consistent with §20's "no production consumer changed" confirmation.

10. **Dedicated-server startup** succeeded:
    - Fabric environment was `SERVER`.
    - Minecraft reached the normal `Done (...)! For help, type "help"` state.
    - No `NoClassDefFoundError`.
    - No `ClassNotFoundException`.
    - No accidental client-class loading involving `net.minecraft.client`,
      `MinecraftNativeResourceAccess`, `ClientResourceService`, or another Phase 3B-1 client class —
      confirming §16's client/server classloading boundary claim empirically, not just by code
      inspection.

11. **Automated-only edge cases were not manually forced and are not claimed as manually validated**:
    malformed Health/Food/Breath source values, a Breath maximum ≤ 0, a generic unit-scale mismatch,
    malformed/incompatible packets, a forced `PENDING_RESYNC`, immutable-collection mutation attempts,
    and fabricated wire corruption were all exercised only by the automated test suite (see §17 and
    the correction's 13 new tests) — none of these was manually reproduced live, and this report makes
    no claim that they were.

**State: Phase 3B-1 complete and manually validated.**

---

# UNRELATED OBSERVATIONS FROM MANUAL TESTING (2026-07-23)

Manual testing also exposed three issues unrelated to Phase 3B-1. **None of these is a Phase 3B-1
regression, none is fixed in this commit, and no conclusion is being drawn yet about root cause** —
they are recorded here only because they surfaced during this validation pass, and are explicitly
**deferred until after Phase 3B is complete**.

1. **Ancestry/origin physical dimensions are not reliably reapplied across dimension changes.**
   Observed example: a Kryptonian entered the Nether and became stuck in blocks; changing ancestry
   again while in the Nether refreshed the dimensions and fixed the problem there; returning to the
   Overworld could reproduce the stale collision dimensions until ancestry was changed again. Likely
   area for later investigation: canonical ancestry-driven entity dimension refresh, dimension change,
   respawn, reconnect, and collision box/eye-height reapplication.

2. **Dedicated-server `ProvisionerEntityBackedSmokeTest` reported 3/4 failures**: live Provisioner
   entity lookup resolved null; `BUY` returned `NPC_INVALID`; `SELL` returned `NO_SESSION`.

3. **Dedicated-server `OffhandAttackVerification` reported 3/5 failures**: a legal offhand Power Attack
   did not spend Stamina; a legal normal offhand attack did not spend Stamina; an insufficient-Power-
   Attack downgrade also left Stamina unchanged.

These three issues are unrelated to the Generic Player Resource API and are not addressed by this or
any prior Phase 3B-1 commit. **The dedicated-server classloading test itself (item 10 above) still
passed** — these three issues do not implicate the Phase 3B-1 client/server boundary. No fix, source
change, or root-cause conclusion for any of the three is included here.

---

## Stop point — status (manual validation)

- Manual validation is complete and confirmed by Stefan; this report has been updated accordingly.
- Three unrelated issues were documented for future investigation and were not fixed.
- Nothing was staged, committed, or pushed by this update.
- Phase 3B-2 (shadow parity) was not started.
