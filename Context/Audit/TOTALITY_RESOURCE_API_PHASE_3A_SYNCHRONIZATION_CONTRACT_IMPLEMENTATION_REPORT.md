# TOTALITY RESOURCE API — PHASE 3A: SYNCHRONIZATION CONTRACT IMPLEMENTATION REPORT

**Status:** Phase 3A complete and manually validated. Corrected per external review 2026-07-22 (see §20 below), with a further client resync-retry correction the same day (§20.9/§20.10), and manual smoke-test confirmation the same day (§21).
**Date:** 2026-07-21
**Branch:** `feature/general-resource-api`

---

## 1. Starting branch and commit

- Branch: `feature/general-resource-api` (confirmed via `git branch --show-current`).
- HEAD at start: `ab8c534b02803056e1fb3d095cf3d7d9de2911dc` — "Add Rage resource adapter" (confirmed via `git rev-parse HEAD` and `git log -1`).
- `git status` at start matched the task's expected baseline exactly: 22 modified `src/main/generated/**` JSON files, 4 untracked `Context/Trading Test/*.png`, an untracked `logs/` directory, and an untracked `src/main/generated/.cache/` directory. No discrepancy was found; none of these files were staged, committed, reset, restored, cleaned, deleted, or otherwise modified during this work.

---

## 2. Exact files created and changed

### 2.1 New production files (15)

`api/rpg/resources/sync/` (pure, no `ServerPlayer`/networking dependency beyond `Identifier`):
- `ResourceSyncProtocol.java` — `PROTOCOL_VERSION` constant.
- `ResourceScalarWireSnapshot.java` — client-safe scalar wire record + codec helpers.
- `ResourcePartitionWireEntry.java` — one partition's current/max/overflow triple.
- `ResourcePartitionedWireSnapshot.java` — client-safe partitioned wire record + codec helpers.
- `PlayerResourceSyncState.java` — per-player revision/dirty/last-known-sent bookkeeping and the pure diff/coalesce algorithm.
- `ClientResourceSyncState.java` — pure receive-side full/delta apply logic with explicit `ApplyResult`.
- `ResourceResyncRateLimiter.java` — pure per-player minimum-interval limiter.

`networking/resource/` (Fabric/Minecraft glue):
- `ResourceFullSyncPayload.java` — S2C full snapshot payload.
- `ResourceDeltaSyncPayload.java` — S2C revisioned delta payload.
- `ResourceResyncRequestPayload.java` — C2S empty resync-request payload.
- `ResourceResyncRequestHandler.java` — C2S handler, rate-limited, self-only.
- `ResourceSyncManager.java` — the single server-side orchestration boundary (dirty tracking → query → diff → send).
- `ResourceSyncServerTick.java` — registers the one `END_SERVER_TICK` flush.
- `ResourceSyncLifecycleEvents.java` — schedules full snapshots on join/respawn/dimension-change, clears state on disconnect.
- `ClientResourceSyncManager.java` — client-side singleton wrapping `ClientResourceSyncState`, auto-resync on revision gap.

### 2.2 New test files (8, 52 test methods total)

- `api/rpg/resources/sync/ResourceScalarWireSnapshotTest.java` (8)
- `api/rpg/resources/sync/ResourcePartitionedWireSnapshotTest.java` (7)
- `api/rpg/resources/sync/PlayerResourceSyncStateTest.java` (10)
- `api/rpg/resources/sync/ClientResourceSyncStateTest.java` (10)
- `api/rpg/resources/sync/ResourceResyncRateLimiterTest.java` (5)
- `networking/resource/ResourceFullSyncPayloadTest.java` (4)
- `networking/resource/ResourceDeltaSyncPayloadTest.java` (6)
- `networking/resource/ResourceResyncRequestPayloadTest.java` (2)

### 2.3 Existing files edited (9)

- `Totality.java` — registers `ResourceSyncServerTick.register()` (in `registerServerTickEvents()`) and `ResourceResyncRequestHandler.register()` (in `registerRPGHandlers()`). Two additive lines, no reordering of existing calls.
- `TotalityClient.java` — registers a `ClientPlayConnectionEvents.JOIN`/`DISCONNECT` pair that clears `ClientResourceSyncManager`, mirroring the existing `ClientRestManager.reset()` precedent immediately above it.
- `init/ModEvents.java` — one additive line, `ResourceSyncLifecycleEvents.register()`, placed last so its JOIN/AFTER_RESPAWN listeners fire after every other listener.
- `networking/TotalityPackets.java` — registers the 2 new S2C payloads and 1 new C2S payload alongside the existing ones.
- `networking/TotalityClientPacketHandlers.java` — two additive `registerGlobalReceiver` calls routing to `ClientResourceSyncManager`.
- `api/rpg/mana/PlayerManaManager.java` — one-line dirty-mark hook appended to `setMana`.
- `api/rpg/stamina/PlayerStaminaManager.java` — one-line dirty-mark hook appended to `setStamina`.
- `api/magic/spell/SpellSlotComponent.java` — one-line dirty-mark hook appended to the private `sync()` choke point.
- `api/rpg/classes/PlayerChargesComponent.java` — one-line dirty-mark hook appended to the public `sync()` choke point.

No other file was touched. No file under `/Inspiration Mods` was read, searched, or referenced.

---

## 3. Final architecture

```
zcylas.totality.api.rpg.resources.sync/      (pure — unit-testable without Minecraft runtime)
    ResourceSyncProtocol                     PROTOCOL_VERSION = 1
    ResourceScalarWireSnapshot               scalar wire DTO + write/read
    ResourcePartitionWireEntry               one partition's wire triple
    ResourcePartitionedWireSnapshot          partitioned wire DTO + write/read
    PlayerResourceSyncState                  server-side per-player revision/dirty/diff state
    ClientResourceSyncState                  client-side receive/apply state
    ResourceResyncRateLimiter                pure per-player interval limiter

zcylas.totality.networking.resource/          (Fabric/Minecraft glue)
    ResourceFullSyncPayload      (S2C)       full replacement snapshot
    ResourceDeltaSyncPayload     (S2C)       revisioned coalesced delta
    ResourceResyncRequestPayload (C2S)       "resync me", no target field
    ResourceResyncRequestHandler              rate-limited C2S handler
    ResourceSyncManager                       orchestration: dirty→query→diff→send
    ResourceSyncServerTick                    one END_SERVER_TICK registration
    ResourceSyncLifecycleEvents               JOIN/AFTER_RESPAWN/dimension/DISCONNECT hooks
    ClientResourceSyncManager                 client singleton + auto-resync-on-gap
```

Data flow (server): a mutation seam (`PlayerManaManager.setMana`, `PlayerStaminaManager.setStamina`, `SpellSlotComponent.sync()`, `PlayerChargesComponent.sync()`) calls `ResourceSyncManager.markDirty(uuid, resourceId)` — a cheap `Set.add`, no query, no packet. Once per tick, `ResourceSyncServerTick` calls `ResourceSyncManager.flush(server)`, which for each player with pending work either (a) queries every generic-sync-eligible registered resource and sends one full snapshot, or (b) queries only the currently-dirty resource ids, diffs against last-known-sent state via `PlayerResourceSyncState`, and sends one delta only if something actually changed.

Data flow (client): `TotalityClientPacketHandlers` routes both payload types to `ClientResourceSyncManager`, which applies them to a single `ClientResourceSyncState` instance and, on `REVISION_GAP`, sends `ResourceResyncRequestPayload` back. Nothing else reads this state in Phase 3A.

---

## 4. Payload IDs and direction

| Payload | Identifier | Direction |
|---|---|---|
| `ResourceFullSyncPayload` | `totality:resource_sync_full` | S2C |
| `ResourceDeltaSyncPayload` | `totality:resource_sync_delta` | S2C |
| `ResourceResyncRequestPayload` | `totality:resource_sync_resync_request` | C2S |

All three registered in `TotalityPackets.java` alongside every other payload, using the same `StreamCodec<FriendlyByteBuf, X>` manual-encode/decode pattern already used by `MobStatsSyncPayload`/`DiceRollResultPayload` (registered against `PayloadTypeRegistry<RegistryFriendlyByteBuf>`, which accepts `StreamCodec<? super RegistryFriendlyByteBuf, T>` — `FriendlyByteBuf` qualifies, same as the existing `SyncManaPayload`/`SyncStaminaPayload`). No registry-aware type is ever serialized, so plain `FriendlyByteBuf` — not `RegistryFriendlyByteBuf` — is sufficient and was used throughout, which is also what made these payloads directly unit-testable with `new FriendlyByteBuf(Unpooled.buffer())` (no `RegistryAccess` needed).

---

## 5. Wire snapshot structures

**`ResourceScalarWireSnapshot(Identifier resourceId, long unitScale, long currentUnits, long maximumUnits, long overflowUnits)`**
Converts from the existing `ResourceSnapshot` via `.from(...)`. `overflowUnits` is always `0` in Phase 3A — no production resource declares `OVERFLOW` capability, and `ResourceSnapshot` itself has no overflow field; the field exists so a future `OVERFLOW`-capable resource needs no wire-shape change. Compact constructor rejects negative values and `current > maximum + overflow`.

**`ResourcePartitionWireEntry(int partition, long currentUnits, long maximumUnits, long overflowUnits)`** + **`ResourcePartitionedWireSnapshot(Identifier resourceId, long unitScale, List<ResourcePartitionWireEntry> partitions)`**
Converts from the existing `PartitionedResourceSnapshot` via `.from(...)`. The constructor deterministically re-sorts partitions into ascending integer order (via an internal `TreeMap`) regardless of input order, and rejects duplicate partition ids. Standard spell slots (`totality:spell_slots`) always convert to exactly 10 ordered partitions — verified by `ResourcePartitionedWireSnapshotTest.tenSpellSlotLevelsSurviveConversionFromAPartitionedResourceSnapshot` and `PlayerResourceSyncStateTest.tenSpellSlotPartitionsSurviveAFullBatch`.

Neither type serializes an adapter, `ResourceContext`, owner state, or callback — both are pure data.

---

## 6. Full snapshot semantics

`ResourceFullSyncPayload(int schemaVersion, long revision, List<ResourceScalarWireSnapshot> scalars, List<ResourcePartitionedWireSnapshot> partitioned)`.

- Constructor rejects a duplicate `resourceId` appearing more than once across the combined scalar+partitioned lists (including the same id appearing in both — a shape mismatch, not two valid entries).
- `ResourceSyncManager.sendFull` queries every generic-sync-eligible registered resource (see §12), builds the complete view, and calls `PlayerResourceSyncState.applyFull`, which **replaces** the entire last-known-sent server-side view (entries omitted from the fresh query are simply absent from the new maps — nothing carries over).
- `ClientResourceSyncState.applyFull` mirrors this on the client: it always fully replaces `scalars`/`partitioned`, so an entry present before a full snapshot but absent from the new one becomes absent client-side too (`ClientResourceSyncStateTest.fullSnapshotReplacesThePreviousView`).
- The full snapshot's `revision` becomes the new baseline for subsequent deltas — the server always bumps `PlayerResourceSyncState.revision` exactly once when sending a full snapshot, and the client sets its own revision to whatever the full snapshot carries, unconditionally.

---

## 7. Delta semantics

`ResourceDeltaSyncPayload(int schemaVersion, long baseRevision, long revision, List<ResourceScalarWireSnapshot> upsertScalars, List<ResourcePartitionedWireSnapshot> upsertPartitioned, List<Identifier> invalidated)`.

- **Corrected 2026-07-22 (see §20):** constructor requires `revision == baseRevision + 1` exactly (originally only `revision > baseRevision`, which permitted an arbitrary revision jump) and rejects `baseRevision == Long.MAX_VALUE` outright rather than computing `+ 1` against it. Also rejects duplicate resource ids within the combined upsert lists, rejects duplicate ids within `invalidated`, and rejects a resource id appearing in both an upsert list and `invalidated` in the same delta.
- Coalescing: `ResourceSyncManager.sendDelta` queries **only** `state.dirtyIds()` (never all registered resources), passes the results to `PlayerResourceSyncState.computeDeltaAndApply`, which diffs each against the last-known-sent wire value. An outcome identical (by record equality) to what was already sent produces **no** upsert entry (`PlayerResourceSyncStateTest.unchangedOutcomeProducesNoUpsert`). If the resulting batch is empty, `sendDelta` returns without sending a packet or bumping revision at all — no packet spam for a no-op mutation.
- Multiple mutations to the same resource within one tick collapse to one entry automatically: `markDirty` only ever adds an id to a `Set`; the actual value used is whatever a single fresh query returns at flush time, so three calls to `setMana` in one tick still produce exactly one upsert with the final value (`PlayerResourceSyncStateTest.multipleChangesToOneResourceInOneTickSendOnlyTheFinalValue`).
- Invalidation: a resource id that was previously present in the last-known-sent maps but whose fresh query now fails (or omits it, e.g. `STATE_UNINITIALIZED`/`STATE_NOT_INSTANTIATED`/any other failure reason) is added to `invalidated` and removed from the server's last-known maps. A resource that was **never** previously synchronized and is still absent produces neither an upsert nor an invalidation (nothing to retract) — `PlayerResourceSyncStateTest.missingRageIsOmittedRatherThanEncodedAsAValidZeroPool`.
- Rage's uninitialized-vs-zero distinction is preserved end-to-end: `RageResourceAdapter` already returns `STATE_UNINITIALIZED` (not a fabricated `0/0`) for a non-Barbarian, `ResourceSyncManager.queryOutcome` reduces any `Failure` to `AbsentOutcome`, and `AbsentOutcome` never becomes a `ScalarOutcome` — so a missing Rage pool can only ever be omitted/invalidated, never encoded as a valid `0/0`. A genuine granted Rage pool at `0/0` (e.g. a Barbarian who has spent every charge) is a real `Success` from the adapter and upserts normally (`PlayerResourceSyncStateTest.validRageZeroZeroRemainsRepresentable`).

---

## 8. Revision and resync semantics

`ClientResourceSyncState` (client) tracks a single `long revision`, `-1` meaning "never synced" (`hasSynced() == false`).

**Corrected 2026-07-22 (see §20):** the table below reflects the post-correction contract. Originally, `applyFull` unconditionally replaced state on any structurally-valid full regardless of revision ordering (no `STALE_IGNORED` row existed for full snapshots), and delta validation only checked `revision > baseRevision`, not exact succession.

| Situation | Result |
|---|---|
| `payload.schemaVersion() != PROTOCOL_VERSION` (full or delta) | `INCOMPATIBLE_SCHEMA` — no mutation |
| Delta arrives before any full snapshot | `REVISION_GAP` — no mutation |
| `delta.baseRevision() == Long.MAX_VALUE`, or `delta.revision() != delta.baseRevision() + 1` | `MALFORMED` — no mutation (re-checked independently of the payload constructor's own guard) |
| `delta.baseRevision() < revision` | `STALE_IGNORED` — no mutation |
| `delta.baseRevision() > revision` | `REVISION_GAP` — no mutation |
| `delta.baseRevision() == revision` | `APPLIED_DELTA` — full mutation, `revision = delta.revision()` |
| `full.revision() < 0` | `MALFORMED` — no mutation |
| `hasSynced() && full.revision() < revision` | `STALE_IGNORED` — no mutation, existing maps and revision untouched |
| `full.revision() >= revision` (or never synced) | `APPLIED_FULL` — replace, `revision = full.revision()` (equal-revision case is treated as an idempotent authoritative replacement, not a no-op — see §20) |

Every non-`APPLIED_*` branch returns before touching any field — verified directly (`ClientResourceSyncStateTest.staleDeltaIsIgnoredWithoutMutatingState`, `.revisionGapIsReportedAndNotApplied`, `.incompatibleSchemaDeltaFailsSafelyWithoutMutation`, and the §20 correction-pass tests for stale/equal/rejected full). `ClientResourceSyncManager.applyDelta` sends `ResourceResyncRequestPayload` back to the server automatically whenever `REVISION_GAP` occurs — since the §20 correction, at most one such request is ever outstanding at a time (see §20's single-flight gate).

**Resync request path:** `ResourceResyncRequestPayload` is a zero-component record (`ResourceResyncRequestPayloadTest.payloadCarriesNoFields` asserts `getRecordComponents().length == 0`) — it is structurally incapable of naming a target player. `ResourceResyncRequestHandler` always resolves against `context.player()` (the sending connection's own player) and is gated by `ResourceResyncRateLimiter` (100-tick / 5-second minimum interval per player, tested in isolation in `ResourceResyncRateLimiterTest`), cleared on disconnect.

---

## 9. Failure-to-omission/invalidation mapping

All nine `ResourceQueryFailureReason` values are handled identically by `ResourceSyncManager.queryOutcome` — every `Failure` becomes an `AbsentOutcome`, which `PlayerResourceSyncState` then reduces to either "omitted from a full snapshot" or "invalidated in a delta if previously present":

| Failure reason | Treatment | Logged? |
|---|---|---|
| `STATE_UNINITIALIZED` | Omit/invalidate | No — routine (e.g. every non-Barbarian querying Rage) |
| `STATE_NOT_INSTANTIATED` | Omit/invalidate | No — routine (ungranted `GENERIC_COMPONENT` resource) |
| `RESOURCE_NOT_REGISTERED` | Omit/invalidate | Yes, `Totality.LOGGER.debug`, id + reason only |
| `ADAPTER_NOT_REGISTERED` | Omit/invalidate | Yes |
| `STATE_UNAVAILABLE_ON_THIS_SIDE` | Omit/invalidate | Yes — should not occur for a real `ServerPlayer` query; a real occurrence indicates a bug |
| `UNSUPPORTED_MODEL` | Omit/invalidate | Yes |
| `OPERATION_UNSUPPORTED` | Omit/invalidate | Yes |
| `MAXIMUM_UNAVAILABLE` | Omit/invalidate | Yes |
| `CORRUPT_ADAPTER_SNAPSHOT` | Omit/invalidate | Yes |

No log statement ever includes adapter-internal exception text or any server-only detail — only the `Identifier` and the `ResourceQueryFailureReason` enum name, at `DEBUG` level (off by default). Malformed adapter output can never reach a client packet: `PlayerResourceService.query` itself already converts any structurally-inconsistent adapter response into `CORRUPT_ADAPTER_SNAPSHOT` before `ResourceSyncManager` ever sees it (Phase 2 guarantee, unchanged).

---

## 10. Lifecycle hooks and their ordering

`ResourceSyncLifecycleEvents.register()` is called **last** inside `ModEvents.register()` (after `PlayerComponentEvents.init()`, `StatsServerEvents`, `MagicServerEvents`, `CombatServerEvents`, `PlayerConnectionEvents`, `ServerEntityEvents.ENTITY_LOAD`, `VanillaDamageInterceptor`, `RestBedInteraction`). Fabric fires same-event listeners in registration order, so:

- **Join**: schedules a full snapshot only after `StatsServerEvents`' `JOIN` (recalculate+restore), `MagicServerEvents`' `JOIN`, `CombatServerEvents`' `JOIN`, and the large `PlayerConnectionEvents` `JOIN` handler (component syncs, Rest registration, `SpellSlotRecalculator.recalculate`, ancestry) have already run for that connection.
- **Respawn**: `ServerPlayerEvents.AFTER_RESPAWN` — scheduled after `PlayerConnectionEvents`' own `AFTER_RESPAWN` registrant (which runs `DamageResistanceRecalculator`, re-registers Rest listeners, calls `BarbarianRageAbility.registerChargePool` + Rage's own `ComponentSync`, and restores class features). No new pre-respawn mutation was added — Phase 3A only schedules, per the task's explicit constraint.
- **Dimension change**: `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` — the only Fabric API 26.2 event for this (confirmed against the resolved `fabric-entity-events-v1-5.0.5+06488ac19e.jar`; Totality had zero listeners against it before this change). Fires with the destination `ServerPlayer`, after the transfer completes.
- **Disconnect**: clears `ResourceSyncManager`'s per-player state and the resync rate limiter's per-player entry.
- **Explicit resync request**: `ResourceResyncRequestHandler` schedules a full snapshot the same way join/respawn/dimension-change do.

In every case, scheduling only sets a flag (`PlayerResourceSyncState.scheduleFullSnapshot()`); the actual query+send happens at the next `ResourceSyncManager.flush(server)` call (end of the current tick, since `ServerTickEvents.END_SERVER_TICK` fires after all synchronous event-driven mutation for that tick has already happened), which is what makes it reflect the settled state rather than a snapshot taken mid-event.

**Manual verification required:** none of join/respawn/dimension-change/disconnect ordering can be exercised by an automated test in this repository's current harness — `ServerPlayer`/`MinecraftServer`/Fabric event dispatch cannot be constructed outside a running game (same limitation already documented in `PlayerResourceStateComponentTest`'s class Javadoc for NBT/sync-packet paths). See §16 for the manual smoke-test checklist.

---

## 11. Dirty tracking and batching behavior

`PlayerResourceSyncState` is the pure core; `ResourceSyncManager` is the impure orchestration boundary — there is exactly one `END_SERVER_TICK` registration (`ResourceSyncServerTick`) for the entire Resource sync contract, not one per resource.

- `flush()` is a cheap no-op for any player with no pending work: one `HashMap.get` plus `hasPendingWork()` (an `OR` of a boolean and `!Set.isEmpty()`), no resource queries, no allocation.
- A delta flush queries only `state.dirtyIds()` — never all seven registered resources — so an idle server with only Stamina regenerating for one player never re-queries Mana, spell slots, or Rage for that player.
- A full flush queries every generic-sync-eligible resource once; there is no per-registered-definition maximum recalculation loop beyond the existing `PlayerResourceService.query` calls each adapter already performs on every call (Phase 2 behavior, unchanged — Phase 3A adds no new recalculation).
- One batch (full or non-empty delta) increments `PlayerResourceSyncState.revision` exactly once and results in exactly one packet.

---

## 12. Treatment of all seven production Resources

Per `ExternalResourceClientMirrorMode`:

| Resource | Mirror mode | Included in generic sync packets in 3A? | Rationale |
|---|---|---|---|
| `totality:health` | `NATIVE_SYNCHRONIZATION` | **No** | Vanilla `ClientboundSetHealthPacket` already reliable; including it would double-apply |
| `totality:food` | `NATIVE_SYNCHRONIZATION` | **No** | Vanilla `FoodData` sync already reliable |
| `totality:breath` | `NATIVE_SYNCHRONIZATION` | **No** | Vanilla air-supply sync already reliable |
| `totality:mana` | `LEGACY_BESPOKE_SYNCHRONIZATION` | **Yes** (parallel) | Legacy `SyncManaPayload`/`ClientManaManager` remains the real client source of truth; the new generic packet is additional and inert on the client |
| `totality:stamina` | `LEGACY_BESPOKE_SYNCHRONIZATION` | **Yes** (parallel) | Same — `SyncStaminaPayload`/`ClientStaminaManager` untouched |
| `totality:spell_slots` | `LEGACY_BESPOKE_SYNCHRONIZATION` | **Yes** (parallel) | Same — generic `ComponentSync` full-state packet untouched |
| `totality:rage` | `LEGACY_BESPOKE_SYNCHRONIZATION` | **Yes** (parallel) | Same — `PlayerChargesComponent`'s own `ComponentSync` untouched |

`ResourceSyncManager.isEligibleForGenericSync` implements this filter: any `EXTERNAL_ADAPTER` resource whose adapter reports `NATIVE_SYNCHRONIZATION` is excluded from both full and delta generic packets; everything else (including a hypothetical future `GENERIC_COMPONENT` resource, which has no "native" concept at all) is included. This is the exact mapping requested by the task — Health/Food/Breath remain native-client-view sources for Phase 3B; Mana/Stamina/spell slots/Rage emit generic packet values in parallel during 3A, ahead of the Phase 3B migration that will make the generic view their actual client source of truth.

---

## 13. Exact legacy paths intentionally preserved

Every one of the following was verified to still compile, still be registered, and (via the full 480-test suite, including every pre-existing Phase 1/2A–2E test) still pass unchanged:

`SyncManaPayload`, `ClientManaManager`, `ManaServerTick`, `SyncStaminaPayload`, `ClientStaminaManager`, `StaminaServerTick`, `PlayerResourceComponent`, `SpellSlotComponent`'s own `ComponentSync` path, `ClientSpellSlotManager`, `PlayerChargesComponent`'s own `ComponentSync` path, current HUD readers (`TotalityHudRenderer`, `SecondaryResourceHud`/`ISecondaryResource` Rage panel), `SpellRadialScreen`'s `ClientSpellSlotManager` reads, Class-tab Rage reading, existing Rest recovery listeners (`RestEventBus` registrations), existing gameplay mutation managers (`PlayerManaManager`, `PlayerStaminaManager`, `SpellSlotComponent`, `PlayerChargesComponent` — only a trailing dirty-mark call was appended to each, no existing statement was reordered, removed, or altered).

No authoritative value is dual-written: every dirty-mark hook is a pure side-channel notification appended **after** the existing authoritative mutation/sync statement, never replacing it.

---

## 14. Automated tests added

52 new tests across 8 files (§2.2), covering the task's numbered requirements 1–24 and 31–34 directly:

1–6: `ResourceScalarWireSnapshotTest`, `ResourcePartitionedWireSnapshotTest` (codec round trips, deterministic ordering).
7–8: `ClientResourceSyncStateTest.fullSnapshotReplacesThePreviousView`, `.correctNextRevisionDeltaApplies`.
9–12: `ClientResourceSyncStateTest.staleDeltaIsIgnoredWithoutMutatingState`, `.revisionGapIsReportedAndNotApplied`, `.incompatibleSchemaFullSnapshotFailsSafelyWithoutMutation`/`.incompatibleSchemaDeltaFailsSafelyWithoutMutation`.
13–14: `ResourceFullSyncPayloadTest.duplicateResourceIdWithinScalarsIsRejected`/`.duplicateResourceIdAcrossScalarAndPartitionedIsRejected`, `ResourceDeltaSyncPayloadTest.duplicateResourceIdInUpsertScalarsIsRejected`/`.duplicateResourceIdInInvalidatedIsRejected`, `ResourcePartitionedWireSnapshotTest.duplicatePartitionIdsAreRejected`.
15–16: `ResourceScalarWireSnapshotTest`/`ResourcePartitionWireEntry` numeric-range rejection tests (negative values, current > max+overflow, unit scale < 1).
17–20: `PlayerResourceSyncStateTest.multipleResourcesDirtiedInOneTickCoalesceIntoOneBatch`, `.multipleChangesToOneResourceInOneTickSendOnlyTheFinalValue`, `.oneBatchIncrementsRevisionExactlyOnce`, `.unchangedOutcomeProducesNoUpsert`.
21–23: `PlayerResourceSyncStateTest.resourceBecomingAbsentInvalidatesAPreviouslySyncedEntry`, `.missingRageIsOmittedRatherThanEncodedAsAValidZeroPool`, `.validRageZeroZeroRemainsRepresentable`.
24: `ResourcePartitionedWireSnapshotTest.tenSpellSlotLevelsSurviveConversionFromAPartitionedResourceSnapshot`, `PlayerResourceSyncStateTest.tenSpellSlotPartitionsSurviveAFullBatch`.
31–32: `ResourceResyncRequestPayloadTest.payloadCarriesNoFields` (structural proof), `ResourceResyncRateLimiterTest` (5 tests).
33–34: the full existing 428-test suite ran unchanged and green alongside the new 52.

**Item 25 ("full snapshot generation is query-only") and items 26–30 (lifecycle scheduling)** are not exercisable by an automated test in this repository — they require a real `ServerPlayer`/`MinecraftServer`, which nothing in this test harness can construct (see §10's "Manual verification required" note and §16 below). Item 25 is upheld by inspection: `ResourceSyncManager` only ever calls `PlayerResourceService.INSTANCE.query(...)`, which is Phase 1/2's own documented query-only guarantee (never instantiates state, never writes NBT, never mutates Health/Food) — `ResourceSyncManager` adds no mutation of its own anywhere in the query path.

---

## 15. Validation results

| Step | Result |
|---|---|
| `compileJava` | Success, no errors (one pre-existing deprecation note, unrelated) |
| `compileTestJava` | Success, no errors |
| Full test suite (`test`) | **480/480 passing, 0 failures, 0 errors** (428 pre-existing + 52 new) |
| `runDatagen` | Success; cache report `old count: 349, new count: 349, removed stale: 0, written: 0` — zero generated-file changes caused by this task |
| `build` | Success, exit code 0 |
| `git diff --check` | No errors — only pre-existing LF→CRLF line-ending notices on files this task touched or that were already dirty; no trailing-whitespace/conflict-marker errors |
| Final `git status` | Exactly: the same 22 pre-existing modified generated JSON files + 4 PNGs + `logs/` + `.cache/` (untouched, unchanged) **plus** the 15 new production files, 8 new test files, and 9 edited files listed in §2 — nothing else |

---

## 16. Manual smoke-test checklist

The following require a running client+server and are not covered by the automated suite:

1. Log in as an existing character with Mana/Stamina/spell slots/Rage already granted. Confirm (via a debug log statement or breakpoint, since no client UI reads the generic view yet) that a `ResourceFullSyncPayload` is received exactly once shortly after join, containing Mana, Stamina, spell slots (10 partitions), and Rage (if Barbarian) — and **not** Health/Food/Breath.
2. Trigger a Mana/Stamina change (cast a spell, sprint) and confirm a `ResourceDeltaSyncPayload` follows within the same or next tick, with `baseRevision` equal to the client's last known revision.
3. Die and respawn; confirm a fresh `ResourceFullSyncPayload` arrives after the existing Rage-repair/class-restoration logic in `AFTER_RESPAWN` has run (i.e. Rage's charges in the packet match `PlayerChargesComponent`'s post-repair state, not a stale pre-repair value).
4. Change dimension (Nether portal or `/execute in`); confirm a fresh full snapshot arrives and no resource is reset/duplicated/re-initialized as a side effect.
5. Disconnect and reconnect; confirm no stale values from the previous session ever appear before the new full snapshot lands (client `ClientResourceSyncManager` is cleared on both `DISCONNECT` and the next `JOIN`).
6. Force a dropped delta (e.g. temporarily inject a packet-drop) and confirm the client automatically sends `ResourceResyncRequestPayload` and recovers via a fresh full snapshot.
7. Spam the resync path (rapid manual triggers, if a debug command is added later) and confirm the server drops requests faster than one per 5 seconds per player.
8. Confirm existing HUD bars (Mana/Stamina/Hunger/Health), the Rage pip HUD, the Class-tab Rage panel, and the spell radial's remaining-slot display are all pixel-identical to pre-Phase-3A behavior — nothing visual should differ, since nothing reads the new path yet.

---

## 17. Known limitations intentionally deferred to Phase 3B

- No public `ClientResourceRegistry`/`ClientResourceState`/`ClientResourceManager` query façade — `ClientResourceSyncManager`/`ClientResourceSyncState` are internal-only, undocumented for gameplay use, and explicitly marked as such in their class Javadoc.
- No shadow-parity comparison against `ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager`/`PlayerChargesComponent`'s client mirror.
- No HUD, menu, radial, or tooltip migration.
- No gameplay decision anywhere reads the synchronized view.
- No client-side prediction (Stamina or otherwise) — out of scope per the task.
- `ResourceScalarWireSnapshot`/`ResourcePartitionWireEntry`'s `overflowUnits` field is always `0` — wired for a future `OVERFLOW`-capable resource but not yet sourced from anywhere, since `ResourceSnapshot` has no overflow concept yet.
- Mana/Stamina/spell slots/Rage remain `LEGACY_BESPOKE_SYNCHRONIZATION` — migrating any of them to `GENERIC_SYNCHRONIZATION` (making the generic path their real client source of truth) is explicitly Phase 3B+ scope.
- Lifecycle-hook ordering (join/respawn/dimension-change/disconnect) is verified by code inspection and the manual checklist above, not by an automated integration test — no harness in this repository can construct a live `ServerPlayer`/`MinecraftServer`.

---

## 18. Confirmation: no Resource authority or gameplay rule changed

No `trySpend`/`restore`/`drain`/`set` capability was added to `PlayerResourceService` (it remains query-only, exactly as Phase 1/2 left it). No adapter's `supportedOperations()` changed. No maximum resolver, regeneration strategy, or death handler was added or altered. No existing formula (Mana/Stamina max calculation, spell-slot recalculation, Rage charge count per class level) was touched. The four dirty-mark hooks added to `PlayerManaManager.setMana`, `PlayerStaminaManager.setStamina`, `SpellSlotComponent.sync()`, and `PlayerChargesComponent.sync()` are each a single trailing statement that calls `ResourceSyncManager.markDirty(...)` — a `Set.add` with no return value consulted, incapable of altering the calling method's own behavior or return value.

## 19. Confirmation: unrelated files and `/Inspiration Mods` untouched

Confirmed via `git status` (§15) — the only changed/created files are exactly those listed in §2. `/Inspiration Mods` was never read, searched, or referenced at any point in this task's research or implementation. The 22 pre-existing modified generated JSON files, the 4 `Context/Trading Test/*.png` files, `logs/`, and `src/main/generated/.cache/` are byte-for-byte as they were at the start of this task (datagen's own cache report confirms `written: 0`).

---

## 20. PHASE 3A EXTERNAL REVIEW CORRECTION PASS (2026-07-22)

**Status:** Corrections implemented, tested, validated. Still uncommitted, on the same starting HEAD (`ab8c534`). Scope held entirely within the Phase 3A synchronization contract — no Phase 3B façade, no consumer migration, no Resource authority change, no legacy synchronization removed.

### 20.1 Starting checkpoint

Confirmed before making any change: branch `feature/general-resource-api`, `HEAD = ab8c534b02803056e1fb3d095cf3d7d9de2911dc` ("Add Rage resource adapter"), and `git status` matched the expected baseline exactly (the same 22 modified generated JSON files, 4 `Context/Trading Test/*.png`, `logs/`, `src/main/generated/.cache/`, plus every Phase 3A file from §2 of this report, all still uncommitted). The review ZIP at `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3A_FINAL_REVIEW_BUNDLE.zip` was not opened or modified.

### 20.2 Issues corrected and exact files changed

**Correction 1 — stale full snapshot protection.**
`ClientResourceSyncState.applyFull` (`src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResourceSyncState.java`) previously replaced the entire client view unconditionally on any structurally-valid full snapshot, so a reordered-on-the-wire older full could overwrite newer delta-derived state. Now: schema/negative-revision checks run first (unchanged); a new check — `hasSynced() && payload.revision() < revision` — returns `STALE_IGNORED` before any map is touched. Equal-revision fulls are accepted as an **idempotent authoritative replacement** (reported as `APPLIED_FULL`, not a distinct no-op result) — a resent full carrying the same revision as already held (e.g. from an explicit resync producing the same authoritative view) applies cleanly. A strictly higher revision, or the very first full ever received, also goes through the same replace path. Building the new `scalars`/`partitioned` maps still happens entirely before any field is mutated, so the replacement remains atomic; malformed/incompatible-schema fulls return before touching anything (unchanged from before, now also true of stale fulls).

**Correction 2 — single-flight client resync request.**
New file `src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResyncRequestGate.java` — a small pure boolean gate (`requestIfNotPending()`, `isPending()`, `clear()`) with no Minecraft/network dependency, so it is directly unit-testable. `ClientResourceSyncManager` (`src/main/java/zcylas/totality/networking/resource/ClientResourceSyncManager.java`) now owns one `ClientResyncRequestGate` alongside its `ClientResourceSyncState`: `applyDelta` only sends `ResourceResyncRequestPayload` when `gate.requestIfNotPending()` returns `true` (i.e. no request is currently outstanding); `applyFull` clears the gate **only** when the result is `APPLIED_FULL` — a stale/malformed/incompatible-schema full leaves the gate pending, since the underlying gap is still unresolved. `ClientResourceSyncManager.clear()` (called on connection JOIN/DISCONNECT and, per Correction 6, client level change) clears the gate too, so a fresh session always starts with no request pending.

**Correction 3 — exact delta revision succession.**
`ResourceDeltaSyncPayload`'s compact constructor (`src/main/java/zcylas/totality/networking/resource/ResourceDeltaSyncPayload.java`) previously only required `revision > baseRevision`, permitting an arbitrary jump. Now requires `revision == baseRevision + 1` exactly, and rejects `baseRevision == Long.MAX_VALUE` outright (checked before any `+ 1` arithmetic, so no silent wraparound to `Long.MIN_VALUE` is possible). `ClientResourceSyncState.applyDelta` independently re-verifies the same two conditions on every delta it receives — it does not trust the payload's own constructor alone, since a payload instance could in principle reach this method through some other construction path in the future.

**Correction 4 — shared generic-sync eligibility.**
`ResourceSyncManager` (`src/main/java/zcylas/totality/networking/resource/ResourceSyncManager.java`) previously applied `isEligibleForGenericSync(PlayerResourceDefinition)` only on the full-snapshot path (`queryAllEligible`); `sendDelta` queried every dirty id unconditionally, so a dirty mark against an ineligible (native-mirrored) or unregistered id could in principle reach a delta upsert. Added an `Identifier`-taking overload, `isEligibleForGenericSync(Identifier)`, that looks the id up in `PlayerResourceRegistry.INSTANCE` first (an unknown id is ineligible by construction) and otherwise delegates to the existing definition-based check — this is now the single shared gate both `queryAllEligible` and `sendDelta` route through. `sendDelta` now skips querying (and therefore ever upserting) any dirty id that fails this check; `PlayerResourceSyncState.computeDeltaAndApply`'s existing "missing outcome → no-op" handling means a filtered-out id still gets cleared from the dirty set (never lingers pending) but can never appear in a delta. If every dirty id in a flush is filtered out, the resulting batch is empty, so `sendDelta` sends no packet and does not bump the revision — no code change was needed for that guarantee, since it already followed from the existing empty-batch early return once the outcomes map itself is filtered upstream. `isEligibleForGenericSync(Identifier)` was made package-private (rather than `private`) specifically so it can be exercised directly in tests without a `ServerPlayer`.

**Correction 5 — maximum-only change notification.**
Audited `PlayerManaManager`, `PlayerStaminaManager`, `ManaServerTick`, `StaminaServerTick`, `PlayerResourceRecalculator`, `SpellSlotComponent`, and `PlayerChargesComponent`. Finding: `SpellSlotComponent.recalculate(...)` and every `PlayerChargesComponent` mutator (`setMax`, `updatePoolMax`, `ensurePool`, `onRest`) already call their own `sync()` unconditionally, which already marks the corresponding Resource dirty regardless of whether current changed — so Spell Slots and Rage already correctly observe maximum-only changes. Mana and Stamina did not: `PlayerResourceRecalculator.recalculate`/`recalculateAndRestore` — the legacy boundary explicitly documented as "call whenever any stat changes... item equip/unequip" — only calls `PlayerManaManager.setMana`/`PlayerStaminaManager.setStamina` (which carry the dirty-mark hook) when `current > newMaximum`, i.e. only to clamp. A maximum increase with current already below both the old and new maximum (the task's own `80/100 → 80/150` example) never touched `setMana`, so the generic Resource path never observed it as a change (separately, `ManaServerTick`'s 20-tick regen heartbeat happens to call `setMana` unconditionally every second regardless of this gap, but that is incidental, not a documented guarantee, and `StaminaServerTick`'s regen tick is gated by `current < max` so it would miss a maximum decrease that leaves current already at the new maximum). Fix: added two unconditional `ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.MANA/STAMINA)` calls at the existing "sync everything to client" step of both `PlayerResourceRecalculator.recalculate` and `.recalculateAndRestore` (`src/main/java/zcylas/totality/api/rpg/stats/PlayerResourceRecalculator.java`) — the same boundary that already unconditionally sends the legacy `SyncManaPayload`/calls `StaminaServerTick.syncStamina` regardless of whether either clamp fired, so it already observes both current and maximum every time it runs. No maximum formula changed; no current value is mutated to force synchronization; no new loop (per-player or per-resource) was added — these are two additional statements at an existing single call site. `PlayerResourceSyncState.computeDeltaAndApply`'s existing equality-based diff (unchanged) suppresses the packet/revision-bump entirely on any tick where the fresh query is unchanged, so this adds no packet spam. This is the one correction whose live-game trigger (an actual stat/equipment recalculation) cannot be exercised by a `ServerPlayer`-free unit test; see the manual smoke-test addition in §20.7 and the pure characterization test in §20.6.

**Correction 6 — client world-state clearing.**
Disassembled the resolved `fabric-lifecycle-events-v1-4.1.3+4575b05f9e.jar` (already a project dependency — its `ClientTickEvents`/`ClientPlayConnectionEvents` siblings are already used elsewhere in `TotalityClient`) to find `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`. Confirmed via the compiled `MinecraftMixin` bytecode that this event fires exactly when `Minecraft` installs a new **non-null** `ClientLevel` — both the very first level on join and every subsequent dimension change/respawn-to-new-level — and is skipped entirely when the level is torn down to `null` (disconnect), which remains covered by the existing `ClientPlayConnectionEvents.DISCONNECT` listener. Registered a new listener in `TotalityClient.onInitializeClient()` (`src/main/java/zcylas/totality/TotalityClient.java`) calling `ClientResourceSyncManager.clear()` on every `AFTER_CLIENT_LEVEL_CHANGE` firing. This closes the one gap connection-level JOIN/DISCONNECT could not cover: a dimension change (Nether portal, `/execute in`, respawn anchor) replaces the client's `ClientLevel` while the same play connection — and therefore the same `ClientResourceSyncManager` singleton — stays alive, so without this listener stale Resource values from the previous dimension could persist client-side until the next unrelated delta/full arrived. Ordering is safe: on the server, a dimension change only *schedules* a full snapshot via `ResourceSyncLifecycleEvents`' `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` listener (§10, unchanged) — the actual send waits for that server's next `ResourceSyncManager.flush` at end-of-tick — so the client-side level swap (and this clear) always happens strictly before the corresponding fresh full snapshot packet is even generated, let alone received; a late clear racing ahead of and erasing a just-received full snapshot is not possible. No Resource value or server authority changes as a result — this is client-side bookkeeping only.

### 20.3 Protocol hardening applied

**Deterministic delta ordering.** `PlayerResourceSyncState.computeDeltaAndApply` previously built `upsertScalars`/`upsertPartitioned`/`invalidated` in `dirty`-set iteration order (a `LinkedHashSet`, i.e. insertion order — which depends on incidental same-tick mutation order, not resource identity). Now sorts all three lists by resource id (string form) immediately before returning the `DeltaBatch`, mirroring `applyFull`'s existing `TreeMap`-based ordering. Partition entries within a single `ResourcePartitionedWireSnapshot` were already deterministically sorted (its compact constructor already re-sorts via an internal `TreeMap<Integer,...>`) — unchanged, already correct.

**Bounded collection decoding.** Added `ResourceSyncProtocol.MAX_RESOURCE_ENTRIES = 256` and `MAX_PARTITION_ENTRIES = 64` (`src/main/java/zcylas/totality/api/rpg/resources/sync/ResourceSyncProtocol.java`) — both comfortably above production's 7 registered resources and 10 spell-slot partitions respectively. `ResourceFullSyncPayload.CODEC`'s decoder now validates `scalarCount`/`partitionedCount` against `MAX_RESOURCE_ENTRIES` immediately after `readVarInt()` and before `new ArrayList<>(count)` is ever constructed; `ResourceDeltaSyncPayload.CODEC`'s decoder does the same for `scalarCount`/`partitionedCount`/`invalidatedCount`; `ResourcePartitionedWireSnapshot.read` does the same for its partition-entry count against `MAX_PARTITION_ENTRIES`. Each check throws `IllegalArgumentException` (matching the existing validation style already used throughout this contract's record constructors) before any allocation sized by the untrusted count, so a corrupt or hostile length prefix cannot even attempt an oversized allocation, let alone partially decode.

**Received-state accessor visibility.** `ClientResourceSyncManager.state()` was `public` but had zero callers anywhere in the codebase (confirmed by a project-wide search). Narrowed to package-private — nothing outside `zcylas.totality.networking.resource` needs it in Phase 3A, and the Phase 3B public query façade is explicitly out of scope for this pass.

**Packet IDs unchanged.** No wire-incompatible change was made to any payload's byte layout beyond the new upper-bound rejection (which only rejects values no valid Phase 3A sender would ever produce) — `resource_sync_full`, `resource_sync_delta`, and `resource_sync_resync_request` keep their existing identifiers and field order.

### 20.4 Intentionally unchanged

- Resource authority, Mana/Stamina formulas and regeneration, spell-slot calculation/spending, Rage progression/spending/Rest recovery, Health/Food/Breath native synchronization, Rest behavior, death behavior, existing HUD/Class-tab/spell-radial readers, existing bespoke packets, `ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager`, `PlayerResourceComponent`, `SpellSlotComponent`/`PlayerChargesComponent` authority — none of these were touched.
- No Phase 3B public client mirror/query façade, no parity telemetry, no consumer migration, no entitlement/tooltip/spell-API work.
- `ResourceResyncRateLimiter`, `ResourceResyncRequestHandler`, `ResourceSyncLifecycleEvents`, `ResourceSyncServerTick`, `ResourceScalarWireSnapshot`, `ResourcePartitionWireEntry` — reviewed, found already correct for this pass's scope, left untouched.
- The report's reviewed concern about lifecycle-hook ordering being unverifiable by automated test (§10, §14) remains true and is not resolved here — it still requires a live `ServerPlayer`/`MinecraftServer`/Fabric client event dispatch, none of which this harness can construct. Correction 6's event choice is verified by bytecode inspection instead (§20.2), consistent with how the original report verified `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`.

### 20.5 Earlier report statements corrected

- §7 (Delta semantics): "Constructor requires `revision > baseRevision`" corrected to reflect the new exact-succession (`revision == baseRevision + 1`) requirement and the `Long.MAX_VALUE` guard.
- §8 (Revision and resync semantics) table: added the `MALFORMED` exact-succession row, and replaced the previous "Any full snapshot, valid schema → unconditional replace" row with the three-way stale/equal-or-higher split introduced by Correction 1.

### 20.6 Automated tests added (27 new; 480 pre-existing retained unchanged in assertions)

- `ClientResourceSyncStateTest` (+7): `olderFullIsIgnoredAfterNewerDeltaState`, `noMutationOccursAfterAStaleFull`, `equalRevisionFullIsAcceptedAsIdempotentReplacement`, `newerFullReplacesTheViewAtomically`, `rejectedFullPreservesPreviousValidStateAtomically`, `deltaRevisionJumpLargerThanOneIsRejectedAtConstructionTime`, `baseRevisionAtLongMaxValueIsRejectedToAvoidOverflow`.
- `ClientResyncRequestGateTest` (new file, 5): `firstGapRequestsOnce`, `repeatedGapsWhilePendingDoNotRequestAgain`, `validFullClearsPendingAndReEnablesFutureRequests`, `rejectedFullDoesNotReEnableRequestsWithoutAnExplicitClear`, `clearIsSafeAndIdempotentWhenNothingWasPending`.
- `ResourceDeltaSyncPayloadTest` (+4): `revisionMustBeExactlyBaseRevisionPlusOne`, `baseRevisionAtLongMaxValueIsRejectedRatherThanOverflowing`, `oversizedScalarCountIsRejectedBeforeAllocating`, `oversizedInvalidatedCountIsRejectedBeforeAllocating`.
- `ResourceFullSyncPayloadTest` (+2): `oversizedScalarCountIsRejectedBeforeAllocating`, `oversizedPartitionedCountIsRejectedBeforeAllocating`.
- `ResourcePartitionedWireSnapshotTest` (+1): `oversizedPartitionCountIsRejectedBeforeAllocating`.
- `ResourceSyncManagerEligibilityTest` (new file, 3): `nativelyMirroredResourcesAreIneligible` (Health/Food/Breath), `legacyBespokeMirroredResourcesAreStillEligibleInParallel` (Mana/Stamina/spell slots/Rage), `unregisteredIdIsIneligible` — exercises `ResourceSyncManager.isEligibleForGenericSync(Identifier)` directly against the real production registry via `TestResourceBootstrap`, with no `ServerPlayer` needed.
- `PlayerResourceSyncStateTest` (+5): `dirtyIdMissingFromQueriedOutcomesIsDiscardedButDirtyIsStillCleared` and `batchWhereEveryDirtyIdIsMissingFromOutcomesIsEmpty` (Correction 4's pure-layer contract — a filtered-out dirty id is never upserted but is still cleared, and an all-filtered batch is empty), `maximumOnlyDifferenceWithUnchangedCurrentStillProducesAnUpsert` (Correction 5's pure characterization: `ResourceScalarWireSnapshot` is a record over `currentUnits`/`maximumUnits` both, so an 80/100 → 80/150 outcome is never record-equal to the previous one and always upserts — this is the exact diff-layer property Correction 5's dirty-mark fix depends on), `deltaUpsertsAndInvalidationsAreSortedByResourceIdRegardlessOfDirtyOrder` and `deltaInvalidationsAreSortedByResourceId` (protocol hardening's deterministic ordering).
- One pre-existing test fixed to remain constructible under the new exact-succession rule: `ClientResourceSyncStateTest.staleDeltaIsIgnoredWithoutMutatingState` previously built `new ResourceDeltaSyncPayload(..., 5L, 7L, ...)` (a base-revision-5-to-7 jump) to simulate a stale/duplicate delta; changed the literal `7L` to `6L` (`baseRevision + 1`, satisfying the new constructor invariant) — the test's assertions, intent, and the scenario it proves (a delta whose base revision is behind the client's already-held revision is ignored without mutation) are completely unchanged; only the previously-invalid input literal was corrected. No assertion was weakened or removed.

### 20.7 Manual smoke-test addition

Correction 5's actual gameplay trigger (a `PlayerResourceRecalculator.recalculate`/`.recalculateAndRestore` call whose maximum changes while current does not) requires a live `ServerPlayer` and cannot be automated in this harness. Manual case to add to the existing checklist (§16): equip/unequip a Mana- or Stamina-boosting armor piece (or apply/remove a `FORTIFY_MANA`/`FORTIFY_STAMINA` effect) while current Mana/Stamina is already below both the old and new maximum, and confirm (via a debug log statement or breakpoint on `ResourceSyncManager.markDirty`) that the generic Resource path still emits a delta for the affected resource even though current did not change.

### 20.8 Validation (fresh run, this pass)

| Step | Result |
|---|---|
| `compileJava` | Success, no errors |
| `compileTestJava` | Success, no errors |
| Full test suite (`test`) | **507/507 passing, 0 failures, 0 errors** (480 pre-existing + 27 new) |
| `runDatagen` | Success; cache report `total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0` — zero generated-file changes |
| `build` | Success, exit code 0 (only the same pre-existing Gradle-10-deprecation notice as §15, unrelated to this pass) |
| `git diff --check` | No errors — only the same pre-existing LF→CRLF notices on already-dirty files; no trailing-whitespace/conflict-marker errors |
| Final `git status --short` | Exactly: the same 22 pre-existing modified generated JSON files + 4 PNGs + `logs/` + `.cache/` (untouched) + the untracked review ZIP and this report, **plus** the Phase 3A `sync`/`networking.resource` main and test directories (now further modified by this pass) and `TotalityClient.java`/`PlayerResourceRecalculator.java` (newly modified by this pass, on top of their Phase 3A baseline edits) — nothing else |

Nothing was committed, staged, or pushed at any point during this pass.

### 20.9 Client/server resync rate-limit deadlock correction (second correction pass, 2026-07-22)

**The defect.** `ResourceResyncRequestHandler`'s server-side `ResourceResyncRateLimiter` accepts at most one resync request per player per 100 ticks and **silently drops** anything more frequent — no rejection packet, no scheduled full snapshot, nothing the client can observe. Before this correction, `ClientResyncRequestGate` only ever cleared its pending flag on an accepted `APPLIED_FULL` (§20's Correction 2) or an explicit lifecycle `clear()`. Combine the two: if a second legitimate `REVISION_GAP` occurred within the same 100-tick window as an already-serviced request, the client would send a request, the server would drop it, and the gate would remain pending **forever** — every later gap's request permanently suppressed as a "duplicate" of one that would never be acknowledged. §20's own Correction 2 writeup already flagged the general shape of this risk ("a future gap must still be allowed to request again once it clears naturally") but that phrasing itself was imprecise — nothing about this gate ever clears *naturally*; only an accepted full or an explicit lifecycle reset was ever going to un-pend it, and neither is guaranteed to occur after a silently-dropped request. This pass closes that gap with a bounded client-side retry, and also corrects that imprecise phrasing (see §20.9's cross-reference below and `PHASE_3A_EXTERNAL_REVIEW_CORRECTIONS.md`).

**Exact retry design.** `ClientResyncRequestGate` (`src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResyncRequestGate.java`) gained:
- `private long ticksSincePending` — a plain incrementing counter, reset to `0` whenever a request becomes pending (`requestIfNotPending()`) and whenever `clear()` runs; **not** derived from world/game time in any way, so it cannot jump, reset, or stall as a side effect of a level replacement — only this class's own `clear()` (itself only called by an accepted full or an explicit lifecycle reset) ever resets it out of band.
- `private final long retryIntervalTicks` — a per-instance interval, defaulting to the new `public static final long DEFAULT_RETRY_INTERVAL_TICKS = 150` (a new no-arg constructor delegates to this; a new `ClientResyncRequestGate(long)` constructor accepts any positive interval, validated with the same `IllegalArgumentException` style already used by `ResourceResyncRateLimiter`'s constructor). 150 sits in the middle of the task's suggested 120–200-tick range and is modestly above the server's 100-tick throttle.
- `public boolean tick()` — a no-op (`false`, no state change) while not pending; while pending, increments `ticksSincePending` and returns `true` **exactly once** per full `retryIntervalTicks`-tick window, resetting the counter to `0` at that point so a subsequent call cannot fire again until another full interval elapses. Calling this every single client tick — pending or not, connected or not — is always safe and never causes more than one retry per interval.

**Client tick integration.** `ClientResourceSyncManager.tick()` (new method) is registered against the existing `ClientTickEvents.END_CLIENT_TICK` event in `TotalityClient.onInitializeClient()` — the same Fabric event two other client tick handlers (`FluidTankScrollHandler`, `MobHealthBarHud`) are already registered against; no new tick-loop mechanism was introduced, just one more listener attached to the event already firing every client tick. `tick()` first checks `ClientPlayNetworking.canSend(ResourceResyncRequestPayload.TYPE)` and returns immediately if there is no active play connection (this event fires even at the main menu / while disconnected, and `RESYNC_GATE` should already be un-pended by then via the JOIN/DISCONNECT/`AFTER_CLIENT_LEVEL_CHANGE` listeners regardless — this check is a defensive guarantee, not something expected to matter in the ordinary case). Only if connected does it call `ClientResyncRequestGate.tick()`; a `true` result resends the same zero-field `ResourceResyncRequestPayload` the immediate-request path already sends. No Resource value is read or mutated anywhere in this method.

**Valid-full clearing behavior.** `ClientResourceSyncManager.applyFull` is unchanged in shape — it still calls `RESYNC_GATE.clear()` only when `STATE.applyFull(payload)` returns `APPLIED_FULL`. `ClientResyncRequestGate.clear()` now resets `ticksSincePending` to `0` in addition to `pending = false`, so an accepted full both un-pends the gate and cancels any already-scheduled retry outright — the next gap, whenever it occurs, gets a fresh immediate request and a fresh retry window, never a stale partial countdown left over from the previous gap.

**Lifecycle clearing behavior.** `TotalityClient`'s existing JOIN/DISCONNECT/`AFTER_CLIENT_LEVEL_CHANGE` listeners already call `ClientResourceSyncManager.clear()`, which already called `RESYNC_GATE.clear()` — unchanged by this pass, and (per the point above) that call now also resets the retry timer, so a fresh session or a dimension change never carries over a stale pending-with-partial-countdown state from a previous session/level.

**Tests added (12 new, all in `ClientResyncRequestGateTest`, all pure):** `tickNeverFiresWhileNotPending`, `noRetryOneTickBeforeTheConfiguredInterval`, `retryBecomesDueExactlyAtTheConfiguredInterval`, `retryTimingResetsAfterFiringPreventingPerTickSpam`, `retryFiresFromTickAloneWithoutAnyDeltaOrFullEverBeingInvolved`, `validFullClearsPendingStateAndCancelsAnyScheduledRetry`, `staleFullDoesNotCancelRetries`, `malformedFullDoesNotCancelRetries`, `incompatibleSchemaFullDoesNotCancelRetries` (these three model `ClientResourceSyncManager.applyFull`'s single `if (result == APPLIED_FULL)` branch by simply never calling `clear()` — the gate cannot itself distinguish which of the three non-`APPLIED_FULL` outcomes occurred, so all three tests assert the identical "retry still fires on schedule" property, cross-referencing the manager's one-line guard as the inspection-verified link between "which `ApplyResult`" and "was `clear()` called"), `lifecycleClearResetsPendingAndRetryTiming`, `afterAValidFullANewLaterGapCanRequestImmediately`, and the scenario centerpiece `systemIsNotPermanentlyStuckWhenAResyncIsSilentlyThrottledByTheServer` (models the exact deadlock: request accepted, full clears the gate, a second gap requests immediately, 149 ticks pass with no acknowledgment simulating the server's silent drop, and a bounded retry fires at tick 150 — proving the client is never permanently stuck). `ClientResourceSyncManager` itself still cannot be unit-tested end-to-end (its `tick()`/`applyDelta` methods call real `ClientPlayNetworking` methods that require a live client), so the manager's wiring is verified by direct code inspection, matching the precedent already established for every other Phase 3A networking-adjacent class this harness cannot construct a live connection for.

**Server rate limiting confirmation.** `ResourceResyncRateLimiter` and `ResourceResyncRequestHandler` (`src/main/java/zcylas/totality/api/rpg/resources/sync/ResourceResyncRateLimiter.java`, `src/main/java/zcylas/totality/networking/resource/ResourceResyncRequestHandler.java`) were not touched by this pass at all — the server still accepts at most one resync request per player per 100 ticks and still silently drops the rest, exactly as before. This correction only changes how many times the *client* is willing to ask, never what the *server* is willing to accept; the server-side anti-spam guarantee is completely intact.

**No gameplay or Resource authority change confirmation.** No `PlayerResourceService` capability, adapter, formula, or death/rest/regeneration behavior was touched. The only production files changed are `ClientResyncRequestGate.java` (new fields/methods, no removed behavior), `ClientResourceSyncManager.java` (one new method, `tick()`, plus updated comments — `applyFull`/`applyDelta`/`clear()`'s own logic is unchanged), and `TotalityClient.java` (one new `ClientTickEvents.END_CLIENT_TICK` registration). Every method touched either sends the same pre-existing zero-field `ResourceResyncRequestPayload` or performs pure in-memory counter bookkeeping — nothing reads or writes any Mana/Stamina/spell-slot/Rage/Health/Food/Breath value anywhere in this correction.

### 20.10 Validation (fresh run, this final correction pass)

| Step | Result |
|---|---|
| `compileJava` | Success, no errors |
| `compileTestJava` | Success, no errors |
| Full test suite (`test`) | **519/519 passing, 0 failures, 0 errors** (507 pre-existing + 12 new) |
| `runDatagen` | Success; cache report `total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0` — zero generated-file changes |
| `build` | Success, exit code 0 (only the same pre-existing Gradle-10-deprecation notice as §15/§20.8, unrelated to this pass) |
| `git diff --check` | No errors — only the same pre-existing LF→CRLF notices on already-dirty files |
| Files changed by this pass | `src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResyncRequestGate.java`, `src/main/java/zcylas/totality/networking/resource/ClientResourceSyncManager.java`, `src/main/java/zcylas/totality/TotalityClient.java`, `src/test/java/zcylas/totality/api/rpg/resources/sync/ClientResyncRequestGateTest.java`, and this report — 5 files, all already part of Phase 3A's tracked scope |

Nothing was committed, staged, or pushed at any point during this pass. Both existing review ZIPs
(`TOTALITY_RESOURCE_API_PHASE_3A_FINAL_REVIEW_BUNDLE.zip`,
`TOTALITY_RESOURCE_API_PHASE_3A_CORRECTED_FINAL_REVIEW_BUNDLE.zip`) were left completely untouched —
a future bundle covering this final correction would be a new, separate artifact.

---

## 21. Manual smoke-test confirmation (2026-07-22)

Stefan completed the manual smoke-test checklist against the corrected Phase 3A implementation
(including the client resync-retry correction, §20.9/§20.10) on a running client and dedicated
server, and reported that everything appeared to work correctly, with no regression observed,
across:

- client startup;
- dedicated-server startup;
- existing-character join;
- disconnect and reconnect;
- Mana spending and restoration;
- Stamina draining and regeneration;
- spell-slot spending and existing presentation;
- Rage spending and restoration;
- death and respawn;
- class/resource restoration after respawn;
- dimension transfer into and out of the Nether;
- no Resource reset, duplication, or stale presentation after dimension transfer;
- maximum-only Mana and Stamina recalculation behavior;
- existing HUD, Rage display, Class-tab display, and spell-radial presentation remaining
  operational.

This is a report of what the user observed and confirmed, not a claim independently verified by
packet capture, log inspection, or timing measurement — none of that instrumentation was run, and
none is claimed here. In particular, the artificial dropped-delta/resync-throttle scenario
(§20.9's "client and server rate-limit deadlock") was not separately manually packet-tested as
part of this checklist; that scenario's correctness is established by the automated retry state
machine tests in `ClientResyncRequestGateTest` (§20.9/§20.10), not by manual instrumentation, and
this section does not claim otherwise.

**Status: Phase 3A complete and manually validated.**
