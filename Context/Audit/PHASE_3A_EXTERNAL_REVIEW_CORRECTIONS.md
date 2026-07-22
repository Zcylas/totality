# PHASE 3A — EXTERNAL REVIEW CORRECTIONS

Exact files and methods implementing each corrected/hardened behavior. All changes are additive or
tightening — no Resource authority, formula, or legacy synchronization path was altered. Full
narrative context lives in §20 of the implementation report
(`Context/Audit/TOTALITY_RESOURCE_API_PHASE_3A_SYNCHRONIZATION_CONTRACT_IMPLEMENTATION_REPORT.md`).

**Updated 2026-07-22 (second pass):** added the "Bounded client resync retry" section below,
documenting the client/server rate-limit deadlock fix. This update also corrects a statement in the
original "Pending-resync clearing rules" section that implied a pending resync request could clear
"naturally" — it cannot, and never could: only an accepted `APPLIED_FULL` or an explicit lifecycle
`clear()` ever un-pends the gate. That original wording is struck through below and replaced with an
accurate description of what actually resolves a pending request now that the bounded retry exists.

---

## Stale full-snapshot rejection

**File:** `src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResourceSyncState.java`
**Method:** `applyFull(ResourceFullSyncPayload)`

Added `if (hasSynced() && payload.revision() < revision) return ApplyResult.STALE_IGNORED;` — placed
after the schema/negative-revision checks and before any map mutation. A full snapshot carrying a
revision lower than the one already held (e.g. reordered on the wire) is ignored outright: the
existing `scalars`/`partitioned` maps and `revision` field are left completely untouched.

## Equal-revision full behavior

**File:** same as above, same method.

A full snapshot carrying **exactly** the currently-held revision is deliberately **not** treated as
stale — it falls through to the normal replace path and is reported as `APPLIED_FULL`, not a
distinct no-op result. Rationale: an explicit resync (see "Bounded client resync retry" below) or a
server-side resend can legitimately re-deliver the same authoritative view at the same revision;
treating that as "stale" would be indistinguishable from a genuine reorder and would leave the
client refusing a harmless, idempotent replacement.

## Atomic full replacement

**File:** same as above, same method (`applyFull`) plus `PlayerResourceSyncState.applyFull(Map)` on
the server-authoritative side (`src/main/java/zcylas/totality/api/rpg/resources/sync/PlayerResourceSyncState.java`).

Both build a complete new `scalars`/`partitioned` view in local variables (`newScalars`/
`newPartitioned` client-side; a `TreeMap`-ordered batch server-side) **before** touching any field
of the receiving object. Only after the new view is fully constructed are the object's own maps
cleared and repopulated, and `revision` updated. No exception can occur mid-mutation because the
payload's own compact constructor (`ResourceFullSyncPayload`) already guarantees no duplicate
resource ids before an instance can even exist — so there is no failure mode between "start
mutating" and "finish mutating."

## ClientResyncRequestGate

**File:** `src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResyncRequestGate.java`

A small, pure, dependency-free class. As of the 2026-07-22 second pass it has:
- `boolean requestIfNotPending()` — returns `true` and marks pending (resetting the retry timer)
  exactly once per pending window; returns `false` (no side effect) while already pending.
- `boolean isPending()`
- `boolean tick()` — added in the second pass; see "Bounded client resync retry" below.
- `void clear()` — resets the pending flag **and** the retry timer.
- `ClientResyncRequestGate()` / `ClientResyncRequestGate(long retryIntervalTicks)` — the second
  pass added the parameterized constructor and the `DEFAULT_RETRY_INTERVAL_TICKS = 150` constant the
  no-arg constructor delegates to.

No Minecraft/network import anywhere in this file, so it is directly unit-testable
(`src/test/java/zcylas/totality/api/rpg/resources/sync/ClientResyncRequestGateTest.java`) without a
live connection — unlike `ClientResourceSyncManager`, which sends a real packet and cannot be
exercised the same way.

## Single-flight resync behavior

**File:** `src/main/java/zcylas/totality/networking/resource/ClientResourceSyncManager.java`
**Method:** `applyDelta(ResourceDeltaSyncPayload)`

```java
if (result == ClientResourceSyncState.ApplyResult.REVISION_GAP) {
    if (RESYNC_GATE.requestIfNotPending()) {
        Totality.LOGGER.debug(...);
        ClientPlayNetworking.send(new ResourceResyncRequestPayload());
    }
}
```

The `ResourceResyncRequestPayload` send is now gated behind `RESYNC_GATE.requestIfNotPending()` — a
run of consecutive `REVISION_GAP` deltas (which the server keeps producing while the client stays
behind) sends exactly one immediate request, not one per gap. (Unchanged by the second pass — the
bounded retry below is a separate, additional path, not a replacement for this one.)

## Pending-resync clearing rules

**File:** same as above (`ClientResourceSyncManager`).
**Methods:** `applyFull(ResourceFullSyncPayload)`, `clear()`

- `applyFull` clears the gate **only** when the underlying `STATE.applyFull(payload)` result is
  `APPLIED_FULL`:
  ```java
  if (result == ClientResourceSyncState.ApplyResult.APPLIED_FULL) {
      RESYNC_GATE.clear();
  }
  ```
  A stale, malformed, or incompatible-schema full leaves the gate pending — the underlying gap is
  still genuinely unresolved.

  ~~so a future gap must still be allowed to request again once it clears naturally.~~
  **Corrected 2026-07-22:** this gate never clears "naturally." Before the second pass, the *only*
  two things that could ever un-pend it were an accepted `APPLIED_FULL` and an explicit lifecycle
  `clear()` (JOIN/DISCONNECT/`AFTER_CLIENT_LEVEL_CHANGE`) — and since the server's own
  `ResourceResyncRequestHandler` rate limiter *silently drops* any resync request faster than one per
  100 ticks (no rejection notice, no scheduled full), a request dropped that way had no path back to
  either of those two clearing conditions at all. That was the exact client/server rate-limit
  deadlock this update fixes: see "Bounded client resync retry" below. The gate now also resolves a
  pending request via a bounded, self-driven retry (`ClientResyncRequestGate.tick()`) that requires
  no acknowledgment of the previously-dropped request — but retrying is still not "clearing
  naturally"; it is a third, explicit, scheduled path that itself still only succeeds once an
  `APPLIED_FULL` is eventually accepted or a lifecycle reset occurs.
- `clear()` (called by `TotalityClient` on connection JOIN/DISCONNECT and the
  `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE` correction) now also calls `RESYNC_GATE.clear()`,
  so a fresh session or a dimension change always starts with no request pending **and** no
  retry timer running (the second pass's `clear()` resets `ticksSincePending` too).

## Exact baseRevision + 1 delta validation

**File:** `src/main/java/zcylas/totality/networking/resource/ResourceDeltaSyncPayload.java`
**Location:** compact constructor.

Was: `if (revision <= baseRevision) throw ...` (only required `revision > baseRevision`, permitting
an arbitrary jump).

Now:
```java
if (baseRevision == Long.MAX_VALUE) {
    throw new IllegalArgumentException("baseRevision must not be Long.MAX_VALUE (would overflow)");
}
if (revision != baseRevision + 1) {
    throw new IllegalArgumentException(...);
}
```

**Client-side independent re-check:** `src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResourceSyncState.java`,
method `applyDelta`:
```java
if (payload.baseRevision() == Long.MAX_VALUE || payload.revision() != payload.baseRevision() + 1) {
    return ApplyResult.MALFORMED;
}
```
This re-verifies the same invariant independently of the payload's own constructor, rather than
trusting that every payload instance necessarily passed through it.

## Long.MAX_VALUE overflow protection

**Files/methods:** the same two locations as above (`ResourceDeltaSyncPayload`'s constructor and
`ClientResourceSyncState.applyDelta`). Both check `baseRevision == Long.MAX_VALUE` **before**
computing `baseRevision + 1`, so the arithmetic that would otherwise silently wrap around to
`Long.MIN_VALUE` never executes — the case is rejected outright instead.

## Shared generic-sync eligibility

**File:** `src/main/java/zcylas/totality/networking/resource/ResourceSyncManager.java`

New package-private overload:
```java
static boolean isEligibleForGenericSync(Identifier resourceId) {
    return PlayerResourceRegistry.INSTANCE.get(resourceId)
            .map(ResourceSyncManager::isEligibleForGenericSync)
            .orElse(false);
}
```
delegating to the pre-existing `isEligibleForGenericSync(PlayerResourceDefinition)` overload. Both
`queryAllEligible` (full-snapshot path) and `sendDelta` (delta path) now route through this single
`Identifier`-keyed gate — previously only the full path filtered by definition; `sendDelta` queried
every dirty id unconditionally.

## Ineligible and unknown dirty-ID handling

**File:** same as above, method `sendDelta(ServerPlayer, PlayerResourceSyncState)`:
```java
for (Identifier id : state.dirtyIds()) {
    if (!isEligibleForGenericSync(id)) {
        continue;
    }
    outcomes.put(id, queryOutcome(player, id));
}
```
An ineligible id (native-mirrored, e.g. Health/Food/Breath) or a completely unregistered/unknown id
is never queried and never enters the `outcomes` map handed to
`PlayerResourceSyncState.computeDeltaAndApply`
(`src/main/java/zcylas/totality/api/rpg/resources/sync/PlayerResourceSyncState.java`). That method's
existing `if (outcome == null) { continue; }` branch means such an id produces no upsert, and its
unconditional `dirty.clear()` at the end still removes it from the dirty set — so it is discarded
safely rather than lingering as pending forever, and can never leak into a delta packet. If every
dirty id in a flush is filtered out this way, the resulting `outcomes` map is empty,
`computeDeltaAndApply` returns an empty `DeltaBatch`, and `sendDelta`'s existing
`if (batch.isEmpty()) { return; }` sends no packet and bumps no revision.

`isEligibleForGenericSync(Identifier)` was made package-private specifically so
`src/test/java/zcylas/totality/networking/resource/ResourceSyncManagerEligibilityTest.java` can
exercise it directly against the real production registry without a `ServerPlayer`.

## Maximum-only Mana/Stamina dirty notifications

**File:** `src/main/java/zcylas/totality/api/rpg/stats/PlayerResourceRecalculator.java`
**Methods:** `recalculate(ServerPlayer)`, `recalculateAndRestore(ServerPlayer)`

Both methods previously only called `PlayerManaManager.setMana`/`PlayerStaminaManager.setStamina`
(which carry the dirty-mark hook) inside an `if (current > newMaximum)` clamp — so a maximum
increase with current already below both the old and new maximum (e.g. 80/100 -> 80/150 via an
equip/stat change) never touched `setMana`/`setStamina` and was never observed by the generic
Resource path. Fix: two unconditional calls added at the existing "sync everything to client" step
of both methods:
```java
ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.MANA);
ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.STAMINA);
```
This boundary already unconditionally sends the legacy `SyncManaPayload`/calls
`StaminaServerTick.syncStamina` regardless of whether either clamp fired, so it already observes
both current and maximum on every call — no new loop (per-player or per-resource) was added, no
maximum formula changed, no current value is mutated to force synchronization.
`PlayerResourceSyncState.computeDeltaAndApply`'s pre-existing equality-based diff still suppresses
the packet/revision-bump on any tick where the fresh query is unchanged from what was last sent, so
this adds no packet spam. (`SpellSlotComponent.recalculate`/`sync()` and every
`PlayerChargesComponent` mutator already called their own `sync()` unconditionally before this
pass, so Spell Slots and Rage already correctly observed maximum-only changes — no change was
needed there.)

## ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE clearing

**File:** `src/main/java/zcylas/totality/TotalityClient.java`
**Method:** `onInitializeClient()`

```java
ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) ->
        zcylas.totality.networking.resource.ClientResourceSyncManager.clear());
```
Confirmed via disassembly of `fabric-lifecycle-events-v1-4.1.3+4575b05f9e.jar`'s `MinecraftMixin`
that this event fires exactly when a new **non-null** `ClientLevel` is installed (both initial join
and every subsequent dimension change), and is skipped when the level is torn down to `null`
(disconnect, already covered by the existing `ClientPlayConnectionEvents.DISCONNECT` listener two
lines above it). This closes the one gap connection-level JOIN/DISCONNECT could not cover: a
dimension change replaces the client's `ClientLevel` while the same play connection stays open, so
without this listener stale Resource values from the previous dimension could persist until the
next unrelated delta/full arrived.

## Deterministic delta ordering

**File:** `src/main/java/zcylas/totality/api/rpg/resources/sync/PlayerResourceSyncState.java`
**Method:** `computeDeltaAndApply(Map)`

Added, immediately before constructing the returned `DeltaBatch`:
```java
upsertScalars.sort(Comparator.comparing(s -> s.resourceId().toString()));
upsertPartitioned.sort(Comparator.comparing(p -> p.resourceId().toString()));
invalidated.sort(Comparator.comparing(Identifier::toString));
```
Previously these three lists were built in `dirty`-set (`LinkedHashSet`) iteration order — insertion
order, which depends on incidental same-tick mutation order, not resource identity. Now sorted by
resource id string form, mirroring `applyFull`'s pre-existing `TreeMap`-based ordering. Partition
entries within a single `ResourcePartitionedWireSnapshot` were already deterministically sorted (its
compact constructor already re-sorts via an internal `TreeMap<Integer,...>`) — unchanged.

## Bounded collection decoding

**Files:**
- `src/main/java/zcylas/totality/api/rpg/resources/sync/ResourceSyncProtocol.java` — new constants
  `MAX_RESOURCE_ENTRIES = 256`, `MAX_PARTITION_ENTRIES = 64`.
- `src/main/java/zcylas/totality/networking/resource/ResourceFullSyncPayload.java` — `CODEC`'s
  decode lambda now validates `scalarCount`/`partitionedCount` against `MAX_RESOURCE_ENTRIES`
  immediately after `readVarInt()`, before `new ArrayList<>(count)`.
- `src/main/java/zcylas/totality/networking/resource/ResourceDeltaSyncPayload.java` — same for
  `scalarCount`/`partitionedCount`/`invalidatedCount`.
- `src/main/java/zcylas/totality/api/rpg/resources/sync/ResourcePartitionedWireSnapshot.java` —
  `read(FriendlyByteBuf)` validates its partition-entry count against `MAX_PARTITION_ENTRIES`.

Each check throws `IllegalArgumentException` before any allocation sized by the untrusted count, so
a corrupt or hostile length prefix cannot even attempt an oversized allocation.

## Narrowed client-state accessor

**File:** `src/main/java/zcylas/totality/networking/resource/ClientResourceSyncManager.java`

`state()` was `public static ClientResourceSyncState state()` with zero callers anywhere in the
codebase (confirmed by a project-wide search before narrowing). Changed to package-private
(`static ClientResourceSyncState state()`, no `public`) — nothing outside
`zcylas.totality.networking.resource` needs it in Phase 3A, and the Phase 3B public query façade is
explicitly out of scope for this pass.

## Bounded client resync retry (second pass, 2026-07-22)

**The deadlock this fixes.** The server's `ResourceResyncRateLimiter`
(`src/main/java/zcylas/totality/api/rpg/resources/sync/ResourceResyncRateLimiter.java`, driven by
`ResourceResyncRequestHandler`) accepts at most one resync request per player per 100 ticks and
**silently drops** anything more frequent — no rejection packet, no scheduled full snapshot. Before
this fix, `ClientResyncRequestGate` only ever cleared on an accepted `APPLIED_FULL` or an explicit
lifecycle reset (see "Pending-resync clearing rules" above, now corrected). If a second legitimate
`REVISION_GAP` occurred within the same 100-tick window as an already-serviced request, the client's
request would be silently dropped, and the gate would remain pending **forever** with no path back
to either clearing condition — every later gap permanently suppressed.

**File:** `src/main/java/zcylas/totality/api/rpg/resources/sync/ClientResyncRequestGate.java`

New state: `private final long retryIntervalTicks` (default `DEFAULT_RETRY_INTERVAL_TICKS = 150`,
configurable via a new `ClientResyncRequestGate(long)` constructor) and `private long
ticksSincePending` (reset to `0` by both `requestIfNotPending()` and `clear()` — a plain incrementing
counter, never derived from world/game time, so it cannot jump or stall across a level replacement).

New method:
```java
public boolean tick() {
    if (!pending) {
        return false;
    }
    ticksSincePending++;
    if (ticksSincePending >= retryIntervalTicks) {
        ticksSincePending = 0;
        return true;
    }
    return false;
}
```
No-op while not pending; while pending, fires `true` exactly once per full `retryIntervalTicks`-tick
window and resets the counter, so calling this every tick never sends more than one retry per
interval.

**File:** `src/main/java/zcylas/totality/networking/resource/ClientResourceSyncManager.java`

New method:
```java
public static void tick() {
    if (!ClientPlayNetworking.canSend(ResourceResyncRequestPayload.TYPE)) {
        return;
    }
    if (RESYNC_GATE.tick()) {
        Totality.LOGGER.debug(...);
        ClientPlayNetworking.send(new ResourceResyncRequestPayload());
    }
}
```
Guards against sending while no play connection exists (this event fires even at the main menu).
`applyFull`/`applyDelta`/`clear()` are otherwise unchanged in shape.

**File:** `src/main/java/zcylas/totality/TotalityClient.java`, method `onInitializeClient()` —
one new registration, `ClientTickEvents.END_CLIENT_TICK.register(client ->
ClientResourceSyncManager.tick());`, reusing the event two other client tick handlers
(`FluidTankScrollHandler`, `MobHealthBarHud`) are already registered against — no new tick-loop
mechanism.

**Tests:** 12 new, all in `ClientResyncRequestGateTest`, all pure (no live connection required) —
see the implementation report's §20.9 for the full list and the "not permanently stuck" scenario
test.

Full narrative, retry-interval rationale, and fresh validation results are in §20.9/§20.10 of the
implementation report.
