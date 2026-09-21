# TOTALITY — Generic Player Resource API — Phase 3B Readiness Audit
## Client View & Shadow-Parity Architecture — Read-Only Report

Status: **read-only audit — no source, test, or build file was modified to produce this report.**
Scope: Phase 3B planning only. Does not begin Phase 3C consumer migration.

Citation legend used throughout:
- **[FACT]** — confirmed by direct inspection of the cited file during this audit (either by the author of this report directly reading the file, or by a read-only research pass whose exact quoted line ranges were spot-verified against the live file).
- **[INFERENCE]** — a conclusion drawn from combining multiple confirmed facts; flagged where it is not itself a single direct quotation.
- **[RECOMMENDATION]** — a Phase 3B design proposal, not a statement about existing code.
- **[OPEN QUESTION]** — genuinely unresolved; needs a decision before or during Phase 3B implementation.

---

## 1. Baseline: branch, HEAD, working tree

- **[FACT]** Current branch: `feature/general-resource-api`.
- **[FACT]** `HEAD` = `00857506dbd07410778e0b97c348d066eab5da0e`, subject "Add generic resource synchronization contract" — matches the expected checkpoint exactly.
- **[FACT]** Local branch tracks `origin/feature/general-resource-api`; `git rev-list --left-right --count origin/feature/general-resource-api...HEAD` returned `0 0` — fully in sync with origin, nothing to push or pull.
- **[FACT]** `git status --short` at audit start showed only:
  - Modified generated datagen JSON under `src/main/generated/data/**` (worldgen noise settings, loot tables, gear recipes) — build-artifact regeneration noise, not Phase 3A source.
  - Untracked `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3A_*_REVIEW_BUNDLE.zip` (3 zips) — audit review bundles, not source.
  - Untracked `Context/Trading Test/trade_screen2-5.png` — unrelated screenshots.
  - Untracked `logs/` and `src/main/generated/.cache/` — build/log ephemera.
  - No `.java` file under `src/` or `src/test/` appears anywhere in the modified/untracked list.
- **[FACT]** No Phase 3A source, test, or audit file is uncommitted. The working tree exactly matches the "known unrelated entries" the task description anticipated. Nothing was cleaned, reset, restored, staged, or deleted to reach this state — it was already this way at session start.
- **[FACT]** `/Inspiration Mods` was excluded from all searches performed for this report.

**Conclusion: checkpoint fully satisfied. Safe to proceed with a read-only Phase 3B audit.**

---

## 2. Phase 3A architecture summary (relevant to Phase 3B)

Phase 3A built a parallel, generic, revisioned client/server Resource synchronization contract that runs **alongside** (not replacing) every pre-existing legacy synchronization path. Nothing reads its client-side output yet.

### 2.1 Wire model **[FACT]**
- `ResourceSyncProtocol` (`src/main/java/zcylas/totality/api/rpg/resources/sync/ResourceSyncProtocol.java`): `PROTOCOL_VERSION = 1`; `MAX_RESOURCE_ENTRIES = 256`; `MAX_PARTITION_ENTRIES = 64` (bounds checked before allocation in payload codecs — a decode-time DoS guard, not a business rule).
- `ResourceScalarWireSnapshot(Identifier resourceId, long unitScale, long currentUnits, long maximumUnits, long overflowUnits)` — immutable record; validates `unitScale>=1`, all quantities `>=0`, `current<=maximum+overflow` (overflow-safe via `Math.addExact`). `overflowUnits` is always `0` in Phase 3A (no resource declares an `OVERFLOW` capability yet).
- `ResourcePartitionWireEntry(int partition, long currentUnits, long maximumUnits, long overflowUnits)` and `ResourcePartitionedWireSnapshot(Identifier resourceId, long unitScale, List<ResourcePartitionWireEntry> partitions)` — the constructor re-sorts partitions through an internal `TreeMap<Integer,...>`, rejects duplicate partition ids, and stores an immutable `List.copyOf(...)`.
- `ResourceFullSyncPayload` (S2C, `totality:resource_sync_full`) — `int schemaVersion, long revision, List<ResourceScalarWireSnapshot> scalars, List<ResourcePartitionedWireSnapshot> partitioned`; compact constructor defensively copies both lists and rejects a resource id appearing in both.
- `ResourceDeltaSyncPayload` (S2C, `totality:resource_sync_delta`) — `int schemaVersion, long baseRevision, long revision, List<...> upsertScalars, List<...> upsertPartitioned, List<Identifier> invalidated`; constructor enforces `baseRevision != Long.MAX_VALUE` and `revision == baseRevision + 1` **exactly**, rejects duplicate/overlapping ids across upserts and invalidations.
- `ResourceResyncRequestPayload` (C2S, `totality:resource_sync_resync_request`) — zero-field record; structurally cannot name a target player.
- Registration: `TotalityPackets.java` (serverbound: `ResourceResyncRequestPayload` lines ~131-132; clientbound: `ResourceFullSyncPayload` lines ~165-166, `ResourceDeltaSyncPayload` lines ~167-168). Client receivers wired in `TotalityClientPacketHandlers.java` (~lines 167-174) directly to `ClientResourceSyncManager.applyFull`/`applyDelta`, with an explicit code comment: "not read by gameplay/HUD code yet."

### 2.2 Client-side state machine **[FACT — directly verified, see §1 code excerpts already read this session]**
- `ClientResourceSyncState` (`api/rpg/resources/sync/ClientResourceSyncState.java`, 145 lines) — the pure, Minecraft-independent receive-side mirror:
  - `revision` (`long`, `-1` = never synced), `scalars`/`partitioned` (`LinkedHashMap<Identifier,...>`, private, no map-returning getter — only single-value `Optional` lookups via `scalar(id)`/`partitioned(id)`).
  - `hasSynced()` = `revision >= 0` — this *is* the entire "synchronized yet" representation; there is no separate boolean.
  - `applyFull(...)` returns one of `APPLIED_FULL / STALE_IGNORED / MALFORMED / INCOMPATIBLE_SCHEMA`; rejects a full with `revision < current` as stale (post-correction; see §2.4); treats an equal-revision resend as an idempotent `APPLIED_FULL`, not a no-op-specific result.
  - `applyDelta(...)` returns one of `APPLIED_DELTA / STALE_IGNORED / REVISION_GAP / MALFORMED / INCOMPATIBLE_SCHEMA`; requires exact `baseRevision == revision` to apply; a delta before any full is `REVISION_GAP`.
  - `clear()` resets both maps and `revision=-1` — documented for "disconnect, world change, or connection replacement."
  - Class Javadoc states explicitly: *"This is not the trusted client Resource query façade... The public façade is Phase 3B scope."*
- `ClientResourceSyncManager` (`networking/resource/ClientResourceSyncManager.java`, 85 lines) — process-wide `static` singleton wrapping one `ClientResourceSyncState` + one `ClientResyncRequestGate`:
  - `state()` is **package-private** — Javadoc: *"nothing outside this package needs it yet"* (explicit Phase 3B deferral).
  - `applyFull`/`applyDelta` delegate to the state machine; only `APPLIED_FULL` clears the resync gate; `REVISION_GAP` on delta triggers an immediate resync request (gated single-flight).
  - `tick()` drives the bounded retry (see §2.4) once per client tick, guarded by `ClientPlayNetworking.canSend(...)`.
  - `clear()` is wired in `TotalityClient.java` to `ClientPlayConnectionEvents.JOIN` (line 106-107), `ClientPlayConnectionEvents.DISCONNECT` (line 108-109), and `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE` (line 122-123, added specifically to catch dimension changes that don't fire JOIN/DISCONNECT). `ClientTickEvents.END_CLIENT_TICK` (line 132-133) drives `tick()`.
- `ClientResyncRequestGate` (`api/rpg/resources/sync/ClientResyncRequestGate.java`, 100 lines) — pure, no Minecraft import: single-flight `requestIfNotPending()`, `isPending()`, and a **fixed-interval (not exponential) bounded retry** `tick()` at `DEFAULT_RETRY_INTERVAL_TICKS = 150`, deliberately chosen longer than the server's 100-tick rate limiter.

### 2.3 Server-side orchestration **[FACT, per Explore-agent direct read, spot-checked against class Javadoc conventions used elsewhere in this codebase]**
- `PlayerResourceSyncState` (server per-player bookkeeping, same `sync` package) — tracks `revision`, `fullSnapshotPending`, a dirty `Set<Identifier>`, and last-sent scalar/partitioned maps; `dirtyIds()` returns a defensive `Set.copyOf(...)` (unlike the client manager's package-private live reference).
- `ResourceSyncManager` (`networking/resource/ResourceSyncManager.java`) — `markDirty(uuid, id)`, `flush(server)` (once/tick via `ResourceSyncServerTick`'s `END_SERVER_TICK` registration), `isEligibleForGenericSync(...)` — the single shared gate that **excludes** `NATIVE_SYNCHRONIZATION` resources (Health/Food/Breath) from the generic packets entirely, and **includes** `LEGACY_BESPOKE_SYNCHRONIZATION` resources (Mana/Stamina/spell slots/Rage) in parallel with their existing bespoke packets.
- `ResourceSyncLifecycleEvents` — schedules a full snapshot on `ServerPlayConnectionEvents.JOIN`, `ServerPlayerEvents.AFTER_RESPAWN`, and `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`; clears server state + rate-limit tracking on `DISCONNECT`. Registered last in `ModEvents.register()` so it runs after Stats/Magic/Combat/Rest restoration has already settled state for the tick.
- `ResourceResyncRequestHandler` — server receiver for the resync request; rate-limited to one accepted request per 100 ticks per player via `ResourceResyncRateLimiter`; **silently drops** anything more frequent (no rejection packet) — this silent-drop behavior is the documented root cause that motivated `ClientResyncRequestGate.tick()`'s bounded retry.

### 2.4 External-review corrections (2026-07-22), all additive/tightening, no authority change **[FACT, per `PHASE_3A_EXTERNAL_REVIEW_CORRECTIONS.md`]**
1. Stale full-snapshot rejection.
2. `ClientResyncRequestGate` single-flight behavior.
3. Exact `revision == baseRevision+1` validation + overflow guard, re-checked independently client-side.
4. Shared `isEligibleForGenericSync` gate applied to both full and delta paths (previously delta queried every dirty id unconditionally).
5. Maximum-only-change dirty-notification fix in `PlayerResourceRecalculator`.
6. Client world-state clearing on dimension change.
7. Deterministic delta ordering.
8. Bounded collection decoding.
9. **Bounded retry** (`ClientResyncRequestGate.tick()`) fixing the silent-drop deadlock — proven by test `systemIsNotPermanentlyStuckWhenAResyncIsSilentlyThrottledByTheServer`.

### 2.5 Explicitly stated Phase 3A→3B boundary **[FACT]**
Direct quotations found in the Phase 3A report and its corrections doc:
- *"No public `ClientResourceRegistry`/`ClientResourceState`/`ClientResourceManager` query façade... The public façade is Phase 3B scope."*
- *"No shadow-parity comparison against `ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager`/`PlayerChargesComponent`'s client mirror"* is listed as a known limitation, never as a design spec — **no document anywhere describes how shadow parity should work.** This means Phase 3B must design that mechanism essentially from scratch (see §11).
- *"Health/Food/Breath remain native-client-view sources for Phase 3B; Mana/Stamina/spell slots/Rage emit generic packet values in parallel during 3A, ahead of the Phase 3B migration that will make the generic view their actual client source of truth."*
- `ClientResourceSyncManager.state()` was deliberately narrowed to package-private specifically because "the Phase 3B public query façade is explicitly out of scope for this pass."

**[FACT — architectural hard boundary, not just a gap]** `ManaResourceAdapter`/`StaminaResourceAdapter`/`StandardSpellSlotsResourceAdapter`/`RageResourceAdapter` all declare `ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION` (confirmed directly in `ExternalResourceClientMirrorMode.java`, lines 26-39). A client-side `PlayerResourceService.query(...)` call for any of these four resources **structurally cannot succeed today** — `ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE` is returned unconditionally on the client side for a legacy-bespoke resource (confirmed directly in `ResourceQueryFailureReason.java`, lines 33-39). This means: **the Phase 3A wire state (`ClientResourceSyncState`) is currently the only client-reachable source of Mana/Stamina/SpellSlots/Rage values from the generic system** — `PlayerResourceService.query` itself is not usable client-side for these four. Any Phase 3B façade for these four resources must read `ClientResourceSyncState`/`ClientResourceSyncManager` directly, not `PlayerResourceService`.

---

## 3. Current client Resource-source map

| Resource | Authoritative owner | Current production client display source | Phase 3A generic wire state available? |
|---|---|---|---|
| Health | vanilla `LivingEntity`/`Player` health fields | native `player.getHealth()/getMaxHealth()` | Yes, but `NATIVE_SYNCHRONIZATION` (never emitted as a generic packet) |
| Food | vanilla `Player.getFoodData()` | native `getFoodData().getFoodLevel()` | Yes, but `NATIVE_SYNCHRONIZATION` (never emitted as a generic packet) |
| Breath | vanilla `Entity.getAirSupply()/getMaxAirSupply()` | vanilla's own `VanillaHudElements.AIR_BAR` (Totality never reads/replaces it) | Yes, but `NATIVE_SYNCHRONIZATION` (never emitted as a generic packet) |
| Mana | `PlayerResourceComponent.mana` via `PlayerManaManager` | `ClientManaManager` (bespoke static cache) | Emitted generically (`LEGACY_BESPOKE_SYNCHRONIZATION`) but unreadable via `PlayerResourceService.query` client-side; only reachable via `ClientResourceSyncState` directly |
| Stamina | `PlayerResourceComponent.stamina` via `PlayerStaminaManager` | `ClientStaminaManager` (bespoke static cache) | same as Mana |
| Standard Spell Slots | `SpellSlotComponent` | `ClientSpellSlotManager` (bespoke static arrays, fed by generic `ComponentSync`) | same as Mana |
| Rage | `PlayerChargesComponent` (pool id `totality:barbarian_rage`) | `PlayerChargesComponent` client instance, read directly by HUD/Class tab | same as Mana |

**[FACT]** No production consumer currently reads `ClientResourceSyncState`/`ClientResourceSyncManager` for anything. The one and only existing production consumer of the *generic query path at all* is `TotalityHudRenderer.resourceDisplayCurrentMax(...)` (used for Health and Food numeric-text display), and even there it is wired with a same-value fallback to the pre-existing native computation so it can never regress what's shown — this is server-callable `PlayerResourceService.query` usage for `NATIVE_SYNCHRONIZATION` resources on the server side or via the common-typed `Player` supertype, not a read of the Phase 3A wire/sync state.

---

## 4. Exact legacy client mirror map

### 4.1 `ClientManaManager` **[FACT — directly read, full file, 14 lines]**
`src/main/java/zcylas/totality/networking/mana/ClientManaManager.java`
```java
public class ClientManaManager {
    private static int mana = 100;
    private static int maxMana = 100;
    public static int getMana() { return mana; }
    public static int getMaxMana() { return maxMana; }
    public static void sync(int mana, int maxMana) { ... }
}
```
- Representation: two bare `static int`s, default `100/100`.
- Absence semantics: **none** — default literal is indistinguishable from a real synced `100/100`.
- Clear/reset: **never cleared** on disconnect/reconnect/respawn/dimension-change anywhere in the codebase (no call site found in `TotalityClient.java` or elsewhere).
- Update source: `SyncManaPayload`, handled at `TotalityClientPacketHandlers.java` (~lines 46-50) → `ClientManaManager.sync(...)`. Sent from `ManaServerTick.syncMana(ServerPlayer)`, invoked on the 20-tick regen loop and ~6 other gameplay call sites. **No push on player JOIN** (unlike Stamina) — a freshly joined client can show a stale/default value until the first regen tick or mana-affecting action.
- Mutability: fully public, unrestricted `sync(...)`.
- Prediction/interpolation: none in the manager itself; smoothing (`manaSmooth`) is applied externally in `TotalityHudRenderer`.
- Consumers: `TotalityHudRenderer` (HUD mana bar), `OverviewTab` (character screen resources panel).

### 4.2 `ClientStaminaManager` **[FACT, structurally identical shape confirmed by research pass; recommend spot-verifying before Phase 3B implementation since it was not re-read verbatim by me this session]**
`src/main/java/zcylas/totality/networking/stamina/ClientStaminaManager.java` — same `static int stamina=100, maxStamina=100` shape.
- **Known asymmetry with Mana**: `PlayerConnectionEvents.java` explicitly calls `StaminaServerTick.syncStamina(player)` inside `ServerPlayConnectionEvents.JOIN` with an inline comment about avoiding a stale default-100 display — Mana has no equivalent join-time push. This is a **pre-existing, already-tolerated inconsistency** between the two legacy mirrors that a shadow-parity system will need to treat as an "expected transitional mismatch" class, not a bug to raise (see §11).
- Update source: `SyncStaminaPayload`, ~20+ call sites (sprint drain, flight, bow draw, weapon mixins, ground slam, alchemy, commands) plus the 20-tick regen block.
- Consumers: `TotalityHudRenderer`, `OverviewTab`, and `TotalityMovementHandler` (gameplay gate, not a display consumer — see §10).

### 4.3 `ClientSpellSlotManager` **[FACT — directly read, full file, 28 lines]**
`src/main/java/zcylas/totality/api/magic/spell/ClientSpellSlotManager.java`
```java
public final class ClientSpellSlotManager {
    private static final int[] maxSlots  = new int[SpellSlotComponent.MAX_SPELL_LEVEL];
    private static final int[] usedSlots = new int[SpellSlotComponent.MAX_SPELL_LEVEL];
    public static void apply(int[] newMax, int[] newUsed) { System.arraycopy(...); }
    public static int getMax(int spellLevel) { ... }
    public static int getRemaining(int spellLevel) { return maxSlots[i] - usedSlots[i]; }
}
```
- 10 fixed-size int-array slots (levels 1-10, `SpellSlotComponent.MAX_SPELL_LEVEL=10`), `getRemaining` computed on the fly, never stored.
- Absence semantics: all-zero arrays are Java's default and are **also the legitimate "non-caster" state** per `StandardSpellSlotsResourceAdapter`'s own documented convention — i.e. this ambiguity is by design on both the legacy and the Phase 3A side, not a divergence to reconcile.
- Update source: piggybacks on the generic per-component `ComponentSync` packet (`SpellSlotComponent.writeSyncPacket`/`applySyncPacket`), routed through `TotalityClientPacketHandlers.java` (~lines 64-85), which then calls `ClientSpellSlotManager.apply(...)` explicitly.
- Never explicitly cleared on disconnect.
- Consumer: `SpellRadialScreen` (`getMax`/`getRemaining` per level).

### 4.4 `PlayerChargesComponent` — Rage client mirror **[FACT, per research pass — no separate manager class, same component instantiated both sides]**
`src/main/java/zcylas/totality/api/rpg/classes/PlayerChargesComponent.java`
- No dedicated `ClientRageManager`; the same class is attached client-side with a `null` player (`ChargeComponents.java`, `PlayerComponentEvents.registerClientComponent(PLAYER_CHARGES, () -> new PlayerChargesComponent(null))`).
- Storage: `Map<Identifier, ChargePool>` where `ChargePool(int current, int max, RestType rechargeType, int rechargeAmount)`. Rage's key is `BarbarianRageAbility.CHARGE_ID = totality:barbarian_rage` — **deliberately a different `Identifier` from the Resource API's `totality:rage`**, both preserved intentionally, per the Phase 2E report.
- Absence semantics: the client map is **sparse** — a non-Barbarian simply has no entry; `getCurrent`/`getMax` return `0` for a missing key, so "never granted" and "granted, currently 0" both read as `0/0` client-side. This is a materially different (weaker) absence model than the Phase 3A server-side adapter, which distinguishes `STATE_UNINITIALIZED` (missing pool) from a genuine `0/0` success (`RageResourceAdapter`, confirmed by `PlayerResourceSyncStateTest`'s `missingRageIsOmittedRatherThanEncodedAsAValidZeroPool` vs `validRageZeroZeroRemainsRepresentable` tests). **This is the single most important legacy/generic semantic gap to reconcile in Phase 3B's parity design** (see §7, §11).
- Clear/reset: tied to `ComponentContainer` attachment lifecycle (fresh empty map on new `LocalPlayer` instantiation: join/respawn/dimension change), not to an explicit clear call — structurally different reset trigger than the other three legacy mirrors, which never reset at all.
- Update source: bespoke `writeSyncPacket`/`applySyncPacket` on the component itself, routed through the same generic `ComponentSync.PACKET_TYPE` receiver as `SpellSlotComponent`. Notably, the wire format sends `max` but the client's `applySyncPacket` only applies `current` to an existing pool (`pool.withCurrent(current)`) — a subtle, currently-inconsequential divergence worth flagging for parity design.
- Also calls `ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE)` on every mutation — an intentionally over-broad dirty flag (any pool changing marks all of `totality:rage` dirty), accepted as harmless per its own inline comment.
- Consumers: `TotalityClient.java`'s `ISecondaryResource` HUD registration (pip bar), `ClassTab` (class resource panel, wrapped in `try/catch (Exception ignored)`).

### 4.5 Native vanilla state (Health/Food/Breath) **[FACT]**
- Health: read directly via `player.getHealth()/getMaxHealth()` at `TotalityHudRenderer` (HUD bar + numeric fallback), `MobHealthBarHud` (mob bars + player-comparison), `OverviewTab` (character screen). All numeric-display sites route through `RpgDisplayUtils.toDisplayHp(...)`, itself backed by `ResourceDisplayConversion.HEALTH_FOOD` (a `5/1` multiplier) — i.e. vanilla 0-20 HP displays as 0-100.
- Food: read via `player.getFoodData().getFoodLevel()`; bar-fill ratio uses a **hardcoded `/20.0`** divisor (`TotalityHudRenderer`, not derived from `FoodResourceAdapter.NATIVE_MAXIMUM`); numeric-text display explicitly reuses `ResourceDisplayConversion.HEALTH_FOOD` (same ×5 constant as Health) per an inline comment noting this replaced an old hardcoded `*5` literal. No `getSaturationLevel()` reads anywhere in HUD/screen code (saturation/exhaustion intentionally excluded from the Resource API's `ResourceSnapshot`).
- Breath: **zero call sites of `getAirSupply()`/`getMaxAirSupply()` anywhere in Totality's client/HUD code** — confirmed by the `BreathResourceAdapter`'s own audit-note Javadoc, which states this was verified via full-source grep. Vanilla's native air-bubble HUD element renders completely untouched; Totality's `HudElementRegistry.replaceElement` calls only ever target `HEALTH_BAR`, `ARMOR_BAR`, `FOOD_BAR`.

---

## 5. Public client façade options

Three shapes were evaluated, following the master doc's own recommended (but never implemented) package skeleton (`api/rpg/resources/client/`: `ClientResourceRegistry`, `ClientResourceState`, `ClientResourceManager`, plus display-conversion helpers).

**Option A — thin read-through façade over `ClientResourceSyncState` only.**
A single `ClientResourceService` (or similarly named) class exposes `query(Identifier)`/`queryPartitioned(Identifier)` that reads *only* `ClientResourceSyncState`, returning a structured result including a `sourceKind` (native-view vs generic-sync) tag. Health/Food/Breath would need a **separate native-reader path** bolted on top, since they're never in `ClientResourceSyncState` at all (excluded by `NATIVE_SYNCHRONIZATION`).
- Pros: minimal, directly reuses the already-tested state machine; no new wire concepts.
- Cons: doesn't unify Health/Food/Breath into the same query shape without an adapter layer on the client side too — risks two parallel client query mechanisms if not designed carefully.

**Option B — unified façade with per-resource client "reader" strategy (native adapter vs generic adapter), mirroring the server's `ExternalPlayerResourceAdapter` pattern on the client side.**
A `ClientResourceQueryService` holds a registry of client-side readers keyed by resource id: a `NativeClientResourceReader` for Health/Food/Breath (wraps `Minecraft.getInstance().player`), and a `GenericSyncClientResourceReader` for Mana/Stamina/SpellSlots/Rage (wraps `ClientResourceSyncManager`/`ClientResourceSyncState`). One query entry point (`ClientResourceQueryService.query(Identifier) -> ClientResourceQueryResult`) regardless of which reader answers.
- Pros: single mental model and single call site for all seven resources; mirrors the server-side adapter architecture Stefan already validated across Phases 2A-2E, so it's a familiar shape; naturally extensible to a future eighth resource without special-casing call sites.
- Cons: slightly more scaffolding than Option A for a Phase 3B-1 slice that's supposed to stay narrow.

**Option C — expose `PlayerResourceService.query(...)`'s existing result types directly to the client, with a client-only resolution layer underneath.**
Reuse `ResourceQueryResult`/`ResourceQueryFailureReason` server-side types wholesale as the client's public result type too.
- Pros: one result vocabulary everywhere, zero new failure-reason enum to design.
- Cons: **not viable as-is** — `ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE` is already overloaded to mean two different things server-side (§2.5), and none of the existing reasons distinguish "resync pending" or "stale-but-still-showable" (client-only freshness concepts that have no server-side analogue — the server is always authoritative-fresh by definition). Reusing the enum verbatim would either require adding client-only members to a server-shared enum (poor separation) or silently mapping distinct client states onto existing overloaded reasons (loses information).

### 5.1 Recommended shape **[RECOMMENDATION]**
**Option B**, with the client's result/failure vocabulary as a **distinct but structurally parallel** type (see §6) rather than literally reusing `ResourceQueryResult`. This satisfies the task's explicit requirement to "remain useful to future HUD, menu, radial, tooltip, and debug consumers" while keeping a clean separation between "what the server unconditionally knows" and "what the client currently believes, with staleness attached" — a distinction that doesn't exist on the server side at all and shouldn't be forced into its enum.

---

## 6. Client query result and failure semantics

### 6.1 Why not reuse `ResourceQueryResult`/`ResourceQueryFailureReason` verbatim **[RECOMMENDATION, reasoning]**
Confirmed by direct reading of both types (`ResourceQueryResult.java`, `ResourceQueryFailureReason.java`):
- `ResourceQueryResult` is a sealed interface (`Success` / `PartitionedSuccess` / `Failure`) — a good shape to *mirror*, but its `Success`/`PartitionedSuccess` wrap `ResourceSnapshot`/`PartitionedResourceSnapshot`, both of which are server-authoritative-instant snapshots with no notion of "as of which revision" or "how stale."
- `ResourceQueryFailureReason` has 9 members, several server-only in meaning (`ADAPTER_NOT_REGISTERED`, `OPERATION_UNSUPPORTED`, `MAXIMUM_UNAVAILABLE`) and one client-relevant-but-overloaded member (`STATE_UNAVAILABLE_ON_THIS_SIDE`, which already means two different things per its own Javadoc). None of the 9 express "resync pending" or "never synced yet" — the closest, `STATE_NOT_INSTANTIATED`, is a server-side generic-component concept, not a client revision concept.

### 6.2 Recommended client result shape **[RECOMMENDATION]**
```java
sealed interface ClientResourceQueryResult {
    record Scalar(Identifier resourceId, long currentUnits, long maximumUnits, long unitScale,
                  ClientResourceSource source, ClientResourceTrust trust) implements ClientResourceQueryResult {}
    record Partitioned(Identifier resourceId, NavigableMap<Integer, PartitionEntry> partitions,
                        long unitScale, ClientResourceSource source, ClientResourceTrust trust) implements ClientResourceQueryResult {}
    record Unavailable(Identifier resourceId, ClientResourceUnavailableReason reason) implements ClientResourceQueryResult {}
}

enum ClientResourceSource { NATIVE_CLIENT_VIEW, GENERIC_SYNCHRONIZED_VIEW }

enum ClientResourceTrust { FRESH, PENDING_RESYNC, NEVER_SYNCED_YET }

enum ClientResourceUnavailableReason {
    RESOURCE_UNREGISTERED,       // no ClientResourceReader registered for this id at all
    NOT_SYNCHRONIZED_YET,        // generic-sync resource, no full snapshot ever received (hasSynced()==false)
    NO_LOCAL_PLAYER,             // native-view resource queried with Minecraft.player == null
    MODEL_MISMATCH               // caller asked scalar() on a partitioned id or vice versa
}
```
This directly satisfies the task's explicit list of distinctions to support: unavailable / not synchronized yet / unregistered / model mismatch / native vs generic source / stale-or-pending state. `Scalar`/`Partitioned` records are immutable by construction (all fields are primitives, a `Identifier`/enum, or an already-immutable `NavigableMap` copy) — trivially safe to hand to any caller without a defensive-copy step, unlike a raw map return.

**[OPEN QUESTION]** Should `ClientResourceTrust` also carry the actual revision number (for a debug/diagnostic overlay) or stay a pure enum? Recommend: keep the public-facing type a pure enum (simplicity for HUD/tooltip consumers who never need the number), but expose the raw revision separately through a diagnostics-only accessor (see §9/§13) so debug tooling isn't blocked.

---

## 7. Native-client-view adapter strategy

### 7.1 Health / Food / Breath — exact current read sites **[FACT]**
- Local player existence: every native read site (`TotalityHudRenderer`, `MobHealthBarHud`, `OverviewTab`) is already guarded by the surrounding HUD/screen render lifecycle (Minecraft only calls HUD render callbacks and opens screens when a local player exists) — no dedicated null-check code was found because the call sites are structurally never reached with a null local player. **[RECOMMENDATION]** the façade's `NativeClientResourceReader` should still explicitly null-check `Minecraft.getInstance().player` (returning `NO_LOCAL_PLAYER`) rather than inheriting that implicit guarantee, since a future consumer (e.g. a background diagnostics poller) might call it outside a render callback.
- Health max: `player.getMaxHealth()` (a live `Attributes.MAX_HEALTH` read, already-synced by vanilla).
- Food current/max: `getFoodData().getFoodLevel()` / vanilla's hardcoded ceiling `20` (there is no `getMaxFoodLevel()` on vanilla `FoodData`; `FoodResourceAdapter.NATIVE_MAXIMUM=20` is Totality's own constant for this reason).
- Breath current/max: `getAirSupply()` / `getMaxAirSupply()`, both natively client-synced `SynchedEntityData` fields (per `BreathResourceAdapter`'s own audit note) — safe to read directly client-side without any Totality-authored sync.

### 7.2 Representing Totality's ×5 Food display scale without changing vanilla authority **[RECOMMENDATION]**
Do **not** bake `×5` into the native reader's raw `currentUnits`/`maximumUnits` — keep those in vanilla's native 0-20 scale (matching `FoodResourceAdapter`'s existing convention of a `unitScale` that represents the *mechanical* scale, not the *display* scale). The ×5 conversion belongs in the presentation layer (`ResourceDisplayConversion.HEALTH_FOOD`, already built and already the single source of truth used by both `RpgDisplayUtils.toDisplayHp` and the Food fallback path in `TotalityHudRenderer`) — a Phase 3B façade consumer applies that conversion the same way existing HUD code already does today. This avoids duplicating the ×5 constant into a second place and keeps the façade's raw units consistent with the server-side `ResourceSnapshot` shape (mechanical units, `unitScale` describing fixed-point precision, not display scale).

### 7.3 Normalizing native values into the same client Resource query shape **[RECOMMENDATION]**
A `NativeClientResourceReader` per resource:
```java
ClientResourceQueryResult health() {
    var player = Minecraft.getInstance().player;
    if (player == null) return new Unavailable(HEALTH, NO_LOCAL_PLAYER);
    return new Scalar(HEALTH, round(player.getHealth()), round(player.getMaxHealth()), UNIT_SCALE_1, NATIVE_CLIENT_VIEW, FRESH);
}
```
`ClientResourceTrust` is always `FRESH` for a native-view resource that resolved successfully (vanilla's own sync guarantees this — there is no "pending resync" concept for vanilla state; a stale native value is a vanilla networking problem entirely outside this system's scope). `float`→fixed-point rounding should reuse whatever convention `HealthResourceAdapter`/`FoodResourceAdapter` already use server-side (both currently produce integral/near-integral units; the façade should not introduce a second rounding rule).

### 7.4 No active local player or world **[FACT/RECOMMENDATION]**
When `Minecraft.getInstance().player == null` or `Minecraft.getInstance().level == null` (main menu, between-worlds), every native reader returns `Unavailable(id, NO_LOCAL_PLAYER)`. This state is common and expected (not an error) — any future HUD/debug consumer must treat it as a normal "nothing to show" case, never log at more than TRACE/DEBUG if at all.

### 7.5 Origin/physiology-based Breath unavailability (future Automaton case) — do not implement now **[RECOMMENDATION, explicitly deferred]**
The task explicitly forbids implementing Automaton logic in Phase 3B. To leave room for it without hardcoding anything: the `Unavailable` reason enum in §6.2 already includes a generic `RESOURCE_UNREGISTERED`-style extensibility point, and nothing in the recommended `NativeClientResourceReader` shape assumes Breath is always applicable to every entity type. When a future physiology system needs to represent "this species doesn't breathe," the natural extension is either (a) a new `ClientResourceUnavailableReason.NOT_APPLICABLE_TO_OWNER` value, or (b) reusing `NO_LOCAL_PLAYER`'s sibling concept at the reader level (the reader itself declines to answer). **No enum member or hook for this is added in this audit** — this is purely a note that the chosen shape doesn't foreclose it.

---

## 8. Generic synchronized-view strategy

### 8.1 How each resource appears in Phase 3A wire state today **[FACT]**
All four (Mana, Stamina, Standard Spell Slots, Rage) are `LEGACY_BESPOKE_SYNCHRONIZATION` and thus **are** included in the generic full/delta packets (per `ResourceSyncManager.isEligibleForGenericSync`), even though nothing reads that generic copy yet. Mana/Stamina/Rage are `ResourceModel.SCALAR`; Standard Spell Slots is the sole `PARTITIONED_POOL` resource (10 partitions, spell levels 1-10, ascending, deterministic — enforced by `ResourcePartitionedWireSnapshot`'s constructor).

### 8.2 Model / unit-scale expectations **[FACT]**
- Mana/Stamina: `unitScale=1`, `IDENTITY` display conversion (already 0-100 native scale, no display conversion needed).
- Standard Spell Slots: `unitScale=1`, `MENU_VISIBLE` only (not `HUD_VISIBLE`) — i.e. this resource is not expected to ever appear on the main HUD bar row, only in menu/radial contexts. A Phase 3B façade consumer for a HUD element should respect `PlayerResourceDefinition.capabilities` rather than assume every resource is HUD-displayable.
- Rage: `unitScale=1`, `authoredBaseMaximum=2` is descriptive only (never consulted live) — the façade must always read the wire-provided `maximumUnits`, never fall back to a hardcoded authored baseline.

### 8.3 Absence semantics — the critical reconciliation point **[FACT + RECOMMENDATION]**
- Mana/Stamina: server-side sentinel `-1` on `PlayerResourceComponent` → `STATE_UNINITIALIZED` at query time → **omitted entirely** from the generic full snapshot (never sent as a scalar with negative values; the resource id simply doesn't appear). Client-side, `ClientResourceSyncState.scalar(MANA)` correctly returns `Optional.empty()` for an omitted resource — this already matches the `NOT_SYNCHRONIZED_YET`-adjacent semantics the façade needs, **except** it cannot distinguish "never synced at all" from "synced, but this particular player has no Mana entry" (e.g. a race/class with no Mana pool) purely from `ClientResourceSyncState`. **[RECOMMENDATION]** the façade's `ClientResourceTrust` should use `hasSynced()` (a snapshot-wide flag) for the "never synced yet" case, and a per-resource `Unavailable(RESOURCE_UNREGISTERED-or-similar)` for "synced, but absent" — these are different situations and must not collapse into the same reason.
- Rage: **[FACT, confirmed by test names `missingRageIsOmittedRatherThanEncodedAsAValidZeroPool` / `validRageZeroZeroRemainsRepresentable` in `PlayerResourceSyncStateTest`]** the server-side sync state already correctly distinguishes "no Rage pool granted" (omitted from the wire entirely) from "Rage pool present, both current and max are 0" (sent as a real `Success`-shaped scalar with `0/0`). This is the exact distinction the task calls out ("Rage uninitialized versus valid 0/0") and it is **already solved correctly at the wire level** — Phase 3B's job is only to **not lose that distinction** when building the façade (i.e. `ClientResourceSyncState.scalar(RAGE).isEmpty()` must map to `Unavailable(NOT_SYNCHRONIZED_YET-or-RESOURCE_UNREGISTERED)`, never silently to a `Scalar(0,0,...)`).
- Standard Spell Slots: no initialization sentinel exists or is needed — an all-zero 10-partition snapshot is itself the valid "non-caster" state (matches `ClientSpellSlotManager`'s own equivalent convention). Partition ordering is guaranteed ascending 1-10 by the wire type's constructor; the façade should preserve integer partition keys exactly as received (no re-indexing to 0-based) since every existing consumer (`ClientSpellSlotManager.getMax(spellLevel)`) already expects 1-based levels.

### 8.4 Pending resync — should a view remain readable? **[RECOMMENDATION]**
Yes. `ClientResourceSyncState` is not cleared or blanked while a resync is pending (`ClientResyncRequestGate` pending state and `ClientResourceSyncState`'s held revision/maps are entirely independent fields) — the last-accepted full+delta-applied view remains fully intact and readable throughout a pending resync; only new deltas that arrive with a revision gap are rejected (not applied), leaving the *existing* view untouched. **Recommend the façade report `ClientResourceTrust.PENDING_RESYNC` as an informational flag alongside the still-valid last-known values**, not as a reason to withhold the value — this matches the task's explicit ask for "last accepted snapshot plus freshness metadata" and avoids a HUD flickering to blank during an ordinary, usually-sub-second resync round-trip.

---

## 9. Trust and freshness model

### 9.1 Recommended states **[RECOMMENDATION]**
| State | Meaning | Trigger |
|---|---|---|
| `NEVER_SYNCED_YET` | No full snapshot has ever been accepted this session (`ClientResourceSyncState.hasSynced()==false`) | Fresh join, before the first full snapshot lands |
| `FRESH` | Last accepted full/delta is not known to be behind; no resync currently pending | Normal steady state |
| `PENDING_RESYNC` | A `REVISION_GAP` was detected and a resync request is in flight or awaiting retry (`ClientResyncRequestGate.isPending()==true`) | Delta arrived with a gap; last-known values still shown |

`NATIVE_CLIENT_VIEW` resources are always `FRESH` when a local player exists (there is no revision/gap concept for vanilla-synced state in this system) and `Unavailable(NO_LOCAL_PLAYER)` otherwise — they never produce `NEVER_SYNCED_YET` or `PENDING_RESYNC`.

### 9.2 Preventing a future consumer from treating an unsynchronized default as authoritative zero **[RECOMMENDATION — this is the task's explicit, named risk]**
The single biggest risk here is a naive future HUD reading, e.g., `ClientResourceSyncState.scalar(MANA).map(...).orElse(0)` — silently turning "never synced" into a displayed `0/0`, indistinguishable from a real empty resource. **Recommendation: the façade's public query method must never return a bare numeric default; every call returns a `ClientResourceQueryResult`, and `Unavailable` has no numeric fields at all** (see §6.2) — this makes it a compile-time impossibility to accidentally treat an absent value as `0` without an explicit, visible `else`-branch decision at the call site. This is a stronger guarantee than a Javadoc warning and is the primary reason to introduce a dedicated sealed result type rather than returning `OptionalLong` or similar.

### 9.3 Relationship to `ResourceQueryFailureReason.STATE_UNINITIALIZED` **[FACT + INFERENCE]**
Server-side, `STATE_UNINITIALIZED` already embodies exactly this "don't fabricate a value" principle for Mana/Stamina (confirmed directly in `ResourceQueryFailureReason.java`, lines 102-114: *"a read-only generic query must never lazily initialize the legacy component as a side effect of being asked a question"*). The client-side trust model in §9.1 is the presentation-layer analogue of that same design principle, applied to revision/sync state rather than component-instantiation state — this is a deliberate continuity with an already-validated pattern in this codebase, not a new invention.

---

## 10. Shadow-parity architecture

### 10.1 What must be compared **[FACT — the four pairs that matter]**
1. Mana: `ClientResourceSyncState.scalar(MANA)` vs `ClientManaManager.getMana()/getMaxMana()`.
2. Stamina: `ClientResourceSyncState.scalar(STAMINA)` vs `ClientStaminaManager.getStamina()/getMaxStamina()`.
3. Standard Spell Slots: `ClientResourceSyncState.partitioned(SPELL_SLOTS)` vs `ClientSpellSlotManager.getMax(level)/getRemaining(level)` for levels 1-10 (compare `max` directly, and `remaining` against `partition.maximumUnits() - partition.currentUnits()`).
4. Rage: `ClientResourceSyncState.scalar(RAGE)` vs `PlayerChargesComponent` client instance's `getCurrent/getMax(BarbarianRageAbility.CHARGE_ID)`.

Health/Food/Breath have **no parity check to perform** — there is no generic wire value to compare against (excluded via `NATIVE_SYNCHRONIZATION`); the native reader *is* the only source, so "parity" is trivially always true by construction for these three. **[RECOMMENDATION]** the parity engine should not even attempt a comparison for `NATIVE_SYNCHRONIZATION`-mode resources — skip them structurally rather than comparing a value against itself.

### 10.2 When to run comparisons **[RECOMMENDATION]**
Recommend comparing **on generic packet receipt** (i.e., inside `ClientResourceSyncManager.applyFull`/`applyDelta`, immediately after a successful apply), not on every client tick and not only on explicit debug request:
- Packet-receipt-triggered comparison is naturally rate-limited to actual state changes (packets are already only sent when something changed, per `ResourceSyncManager`'s dirty-tracking) — this avoids per-tick log spam without needing a separate throttle mechanism.
- A per-tick comparison would run 20×/second for values that mostly don't change, adding cost and requiring its own coalescing logic for no benefit over packet-triggered checking.
- An explicit-debug-request-only mode is valuable *additionally* (see §13) but insufficient alone, since Stefan's manual playtesting won't naturally trigger a debug command at every moment a legacy/generic mismatch could occur — passive on-receipt checking with quiet logging is what actually builds evidence of parity over an ordinary play session.

### 10.3 Grace window for ordinary transitional mismatch **[RECOMMENDATION]**
A short grace window (recommend: **1-2 client ticks**, i.e. re-check on the *next* comparison trigger before escalating a mismatch to "persistent") is needed because the two systems are fed by genuinely independent packets that can arrive in different network frames within the same logical game action (e.g., a Mana-spending spell cast triggers both `SyncManaPayload` and a generic-sync dirty-mark in the same tick, but Fabric's payload delivery order across two different channels is not guaranteed to be simultaneous). A single-tick lag between the two mirrors updating is expected and must not be logged as a mismatch — only a difference that **persists across the grace window** should be classified as `PERSISTENT_MISMATCH`.

### 10.4 Mismatch classification **[RECOMMENDATION]**
```
EXACT_MATCH               — values equal (after any needed scale normalization) at comparison time
TRANSIENT_MISMATCH        — values differ, but this is the first tick they've differed; re-check next trigger
PERSISTENT_MISMATCH       — values still differ after the grace window has elapsed
EXPECTED_TRANSITIONAL     — a known-tolerated asymmetry (e.g. Stamina's join-time push vs Mana's lack thereof,
                             §4.2) — suppressed from escalation entirely, documented inline in the parity engine
MODEL_MISMATCH            — one side reports a value the other structurally cannot represent
                             (e.g. generic side reports PARTITIONED_POOL but the legacy comparator expects scalar)
SOURCE_UNAVAILABLE        — one side has no value yet (e.g. generic side NEVER_SYNCED_YET) — cannot compare, not a mismatch
```
- Scaled-value comparison: normalize both sides to raw mechanical units before comparing (Mana/Stamina/Rage are already `unitScale=1` on both legacy and generic sides, so no conversion is actually needed today — but the comparator should still divide by `unitScale` explicitly rather than assuming `1`, so it doesn't silently break if a future resource's generic `unitScale` diverges from its legacy manager's raw int).
- Partitioned (spell slots) comparison: compare per-partition-key, not by array index blindly — although today `ClientSpellSlotManager`'s array index `i = spellLevel-1` and the wire's `partition` field are both 1-based-to-0-based consistent, an explicit key-based comparison (rather than positional) is safer against a future reordering.
- Rage absence vs zero: the parity engine must treat "generic side `Unavailable`/omitted" + "legacy side reads `0/0` (its sparse-map default)" as `EXPECTED_TRANSITIONAL` for a **non-Barbarian** player (this is not a bug — it's the expected shape given the legacy mirror's weaker absence model, §4.4) — but for an **actual Barbarian** whose pool the server has genuinely granted, the same pairing (generic `Unavailable` + legacy `0/0`) should classify as a real mismatch, since the generic side should have a value once granted. This requires the parity engine to know the player's class (already available client-side via existing class-selection state) to distinguish the two cases — **[OPEN QUESTION]**: is that classification worth the added complexity for a diagnostics-only feature, or should Phase 3B simply treat any Rage `Unavailable`-vs-`0/0` pairing as `EXPECTED_TRANSITIONAL` unconditionally and rely on manual playtesting (as a Barbarian) to catch a genuine gap? Recommend the simpler unconditional treatment for Phase 3B-2, revisit only if it hides a real bug during Phase 3B-3 validation.
- Ordering differences between the two packet systems: covered by the grace window (§10.3); no additional design needed beyond that.

### 10.5 Where this lives **[RECOMMENDATION]**
A new pure class, e.g. `ClientResourceParityEngine` (no Minecraft dependency, testable like `ClientResourceSyncState`), invoked from `ClientResourceSyncManager.applyFull`/`applyDelta` immediately after a successful apply, reading the four legacy manager/component values via simple accessor calls passed in by the (necessarily impure) caller — mirroring the existing split between `ClientResourceSyncState` (pure) and `ClientResourceSyncManager` (impure glue) that Phase 3A already established.

---

## 11. Parity timing and grace-window recommendation (summary)

- **Trigger:** on successful `applyFull`/`applyDelta` (packet receipt), not per-tick, not debug-request-only (though a debug-request path should also exist for on-demand verification, see §13).
- **Grace window:** 1-2 comparison triggers (effectively "next packet, not this one") before escalating to `PERSISTENT_MISMATCH`.
- **Coalescing:** track the previous classification per resource id; only log a *transition* into `PERSISTENT_MISMATCH` (not every tick it remains mismatched) and only log a *transition* back to `EXACT_MATCH` (to confirm resolution) — this bounds log volume to state changes, not steady-state noise.
- **Known-tolerated asymmetries to pre-classify as `EXPECTED_TRANSITIONAL`:** Stamina's join-time push vs Mana's absence of one (§4.2); Rage absence-vs-zero for a non-Barbarian (§10.4).

---

## 12. Logging/diagnostic strategy

**[RECOMMENDATION]**
- Development-only by default: gate parity logging behind the same debug/dev convention already used elsewhere in the mod (check `Totality.LOGGER.debug(...)` usage — `ClientResourceSyncManager` already logs revision-gap/retry events at DEBUG only, §2.2 — the parity engine should follow the identical convention, i.e. DEBUG level, id + classification only, never a raw value dump at INFO/WARN in production).
- No gameplay-visible warnings in Phase 3B (per the task's explicit constraint) — no chat message, no HUD overlay, no toast.
- A debug command (e.g. `/totality resource parity`) is the right vehicle for **on-demand** full-state dump (all seven resources' native/generic/legacy values side by side) — this is the Phase 3B-3 "parity verification tooling" slice, not Phase 3B-1/2.
- Test-visible: the parity classification logic itself (§10.4's pure enum-producing function) should be unit-testable exactly like `ClientResourceSyncState`/`ClientResyncRequestGate` — i.e., design it to take plain values in and return a classification out, with no direct Minecraft/manager coupling, so a test can drive every classification branch without a running client.

---

## 13. Lifecycle analysis

Tracing the scenarios the task lists, against the hooks Phase 3A already established (all in `TotalityClient.java`, confirmed directly):

| Scenario | Phase 3A hook already covers it? | Phase 3B additional need? |
|---|---|---|
| Initial game startup (no world) | N/A — `ClientResourceSyncState`/`ClientResourceSyncManager` simply sit at `hasSynced()==false`; no hook needed | Native readers must handle `NO_LOCAL_PLAYER` (§7.4) |
| Joining a server | `ClientPlayConnectionEvents.JOIN` → `clear()`, server schedules a full snapshot on the same event server-side | None — façade just reads through as `NEVER_SYNCED_YET` until the full snapshot lands |
| Local-player creation | Implicit (JOIN fires once a play session begins) | None |
| First full Resource snapshot | `applyFull` → `APPLIED_FULL`, `hasSynced()` becomes true | Parity engine's first comparison opportunity |
| Disconnect | `ClientPlayConnectionEvents.DISCONNECT` → `clear()` (client) + `onDisconnect`/`clearRateLimit` (server) | None |
| Reconnect | Same as "joining a server" (fresh JOIN) | None |
| Death and respawn | Server: `ServerPlayerEvents.AFTER_RESPAWN` → `scheduleFullSnapshot`. **Client: no explicit clear on respawot** — the client relies entirely on the incoming full snapshot rather than clearing first (confirmed: no `AFTER_RESPAWN`-equivalent client event registered against `ClientResourceSyncManager.clear()`) | **[OPEN QUESTION]** is this a gap? Practically, the very next full snapshot fully replaces both maps (`applyFull` does `scalars.clear(); scalars.putAll(...)`), so a stale pre-death value is only visible for the brief window between respawn and the next server tick's flush — likely acceptable, but worth an explicit decision rather than an assumption, since Phase 3B's façade may be shown to the player for the first time and a decision here directly affects "does the HUD flicker a stale value for one frame on respawn" |
| Dimension change | `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE` → `clear()` (client), `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` → `scheduleFullSnapshot` (server) — ordering explicitly verified safe by inline comment (client clears before the server's scheduled snapshot can arrive) | None |
| Integrated-server restart | Behaves as a fresh JOIN from the client's perspective (new connection) | None beyond ordinary JOIN handling |
| Switching between servers | DISCONNECT then JOIN — both already covered | None |
| Connection replacement | Covered by DISCONNECT/JOIN pair | None |
| Resync retry | `ClientResyncRequestGate.tick()` bounded retry, already built and tested | Façade must expose `PENDING_RESYNC` trust state during this window (§8.4, §9.1) |
| Malformed/incompatible payload | `applyFull`/`applyDelta` return `MALFORMED`/`INCOMPATIBLE_SCHEMA`, silently no-op at the manager layer (no logging even) | **[RECOMMENDATION]** Phase 3B should add at least DEBUG-level logging for `MALFORMED`/`INCOMPATIBLE_SCHEMA` outcomes specifically (currently only `REVISION_GAP` is logged) — a malformed payload from a legitimate server almost certainly indicates a real bug (schema drift, corrupted state) rather than ordinary network reordering (which produces `STALE_IGNORED`/`REVISION_GAP` instead), and silently swallowing it makes such a bug invisible during development. This is a small, additive logging change, not a behavior change — flagging it here as a Phase 3B-1 candidate rather than implementing it in this audit. |

**Overall conclusion: no additional lifecycle *hook* is structurally required for Phase 3B beyond what Phase 3A already registered** — the façade and parity engine are consumers of existing state, not new state that needs its own clearing. The one item worth a deliberate decision (not a code change) is the respawn-clearing question above, and the one recommended addition is malformed-payload logging.

---

## 14. Client/server classloading analysis

- **[FACT]** No class in the Phase 3A sync subsystem (`api/rpg/resources/sync/`, `networking/resource/`) uses `@Environment(EnvType.CLIENT)`/`@Environment(EnvType.SERVER)` — confirmed by the research pass's repo-wide grep returning zero matches. Client-side intent is signaled **only** by class-name prefix (`Client...`) and Javadoc prose.
- **[FACT]** `ClientResourceSyncState` and its server counterpart `PlayerResourceSyncState` share the exact same package (`api.rpg.resources.sync`); `ClientResourceSyncManager` and `ResourceSyncManager` share `networking.resource`. This is a **naming-convention-only boundary**, not a compiler-enforced one — nothing prevents server code from importing `ClientResourceSyncState` (it has zero Minecraft/Fabric imports, so it would even compile and run fine server-side, just meaninglessly). The only thing that would actually fail server-side is a class that imports a genuinely client-only Fabric API (e.g. `ClientResourceSyncManager`'s import of `net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking`), and that failure would be a classloading error at runtime, not a compile-time guarantee.
- **[FACT]** `ClientResourceSyncState` and `ClientResyncRequestGate` are both explicitly documented as "pure logic — no Minecraft client/network dependency," which is exactly why they already have unit tests that run without a Minecraft client instance.

### 14.1 Recommended package boundaries for Phase 3B **[RECOMMENDATION]**
- Keep the **pure query/result types** (`ClientResourceQueryResult` and its nested records, `ClientResourceSource`, `ClientResourceTrust`, `ClientResourceUnavailableReason`, and the parity classification enum from §10.4) in the same kind of Minecraft-independent common package Phase 3A already used for `ClientResourceSyncState`/`ClientResyncRequestGate` (e.g. `api/rpg/resources/client/` per the master doc's original recommended layout) — this keeps them testable via plain JUnit with zero client bootstrap, continuing the precedent Phase 3A set.
- Keep the **impure readers** (`NativeClientResourceReader` — touches `Minecraft.getInstance()`, and `GenericSyncClientResourceReader` — touches `ClientResourceSyncManager`) in the `networking`/`client`-adjacent impure layer, analogous to how `ClientResourceSyncManager` itself is impure glue over the pure `ClientResourceSyncState`.
- This mirrors the existing Phase 2A-2E server-side split (pure `PlayerResourceDefinition`/`ResourceSnapshot` records vs. impure `ExternalPlayerResourceAdapter` implementations that touch actual `ServerPlayer`/vanilla state) — **[INFERENCE]** Stefan has now validated this pure/impure split pattern twice (server adapters, then Phase 3A sync state); continuing it for the client façade is the path of least surprise and requires no new architectural justification.
- **[RECOMMENDATION]** consider whether `@Environment` annotations should finally be introduced for the `Client...`-named classes in this subsystem as part of Phase 3B, purely as compile-time documentation/safety (Fabric Loom can flag an accidental server-side reference to a class annotated `@Environment(EnvType.CLIENT)` at remap/verification time) — this is a nice-to-have hardening, not a blocker, since the naming convention has held so far with zero incidents reported in any Phase 3A document.

### 14.2 Dedicated-server safety **[FACT]**
Because nothing in the pure `sync` package imports any client-only class, and the only classes that *do* import client-only Fabric APIs (`ClientResourceSyncManager`, and any future `NativeClientResourceReader`/`GenericSyncClientResourceReader`) are never referenced from common/server-side registration code (confirmed: `ModEvents.register()` / `ResourceSyncLifecycleEvents` are server-side-only registration, entirely separate from `TotalityClient.onInitializeClient()`), a dedicated server never loads or references any Phase 3A or planned Phase 3B client class. This safety currently rests on **discipline, not enforcement** (§14.1's recommendation addresses that gap).

---

## 15. Exact consumer inventory

All entries below are production consumers of Health/Food/Breath/Mana/Stamina/SpellSlots/Rage found via codebase search. None were modified during this audit.

| # | File : Class : Method | Resource(s) | Current source | Phase 3C classification |
|---|---|---|---|---|
| 1 | `client/renderer/hud/TotalityHudRenderer.java` — HUD element lambda (~lines 109-115) | Health, Food, Mana, Stamina | native (Health/Food) + `ClientManaManager`/`ClientStaminaManager` (Mana/Stamina) | **Phase 3C migration candidate** (Mana/Stamina, once `GENERIC_SYNCHRONIZATION` is live) / **should remain native** (Health/Food, per explicit Phase 3B/3A scope statement) |
| 2 | `client/renderer/hud/TotalityHudRenderer.java` — `resourceDisplayCurrentMax(...)` numeric-text path (~lines 143-222) | Health, Food | `PlayerResourceService.query` (generic, `NATIVE_SYNCHRONIZATION`) with same-value native fallback | Already migrated for numeric text only; **should remain native-backed** per Phase 3B scope (no change intended) |
| 3 | `client/renderer/hud/MobHealthBarHud.java` — `render(...)` (~lines 169,172,181-182,308-309,320,323) | Health (mob + player comparison) | native | **should remain native** — mob health has no Resource API adapter/component at all; out of Resource API scope entirely |
| 4 | `TotalityClient.java` — `ISecondaryResource` HUD registration (~lines 160-186) | Rage | `PlayerChargesComponent` client instance, direct | **Phase 3C migration candidate** (once Rage is `GENERIC_SYNCHRONIZATION`) |
| 5 | `screen/ability/SpellRadialScreen.java` — `extractRenderState`/`drawSlotIndicator` (~lines 162,164) | Standard Spell Slots | `ClientSpellSlotManager` | **Phase 3C migration candidate** |
| 6 | `screen/character/tabs/OverviewTab.java` — `drawResourcesPanel(...)` (~lines 105-110) | Health, Stamina, Mana | native (Health) + `ClientStaminaManager`/`ClientManaManager` | mixed: Health **should remain native**; Stamina/Mana **Phase 3C migration candidates** |
| 7 | `screen/character/tabs/ClassTab.java` — `drawResourcePanel(...)` (~lines 357-367) | Rage | `PlayerChargesComponent` client instance, direct | **Phase 3C migration candidate** |
| 8 | `client/TotalityMovementHandler.java` (~lines 76,123,155) | Stamina | `ClientStaminaManager.getStamina()` | **gameplay-authoritative and must never use client presentation state** — this is a client-side *prediction/gating* read for ability availability, not a display consumer; even after any future migration, a gameplay decision like this must continue to read a value the server will independently re-validate, never trust the client façade as authoritative. Flagging explicitly per the task's classification list even though it's outside the "UI consumer" scope of §10 in the original research pass. |

**[FACT]** No consumer was found for Breath anywhere in Totality's own code (vanilla's own air-bubble HUD element is untouched). No tooltip renderer, debug command, or hidden indirect wrapper was found reading any of the seven resources (confirmed by targeted search across `client/tooltip/*` and command-registration code).

**[FACT — architecturally important]** Item 2 above is the **only** existing production use of the Resource API's generic query path from client-reachable code today, and it is scoped narrowly (numeric text only, with a fallback that guarantees no regression). Items 1, 4, 5, 6, 7 all read exclusively through the four legacy mirrors or native vanilla state — **zero** production consumers read `ClientResourceSyncState`/`ClientResourceSyncManager` today. This confirms the task's premise that Phase 3B is building a facade with no existing callers yet, and that Phase 3C migration is a fully separate, not-yet-started effort.

No consumer in this table is modified by this audit.

---

## 16. Testability

### 16.1 Pure Java (no Minecraft/Fabric runtime) **[RECOMMENDATION, following Phase 3A's own precedent exactly]**
Everything in §6.2's result types, §10.4's parity classification enum/function, and §9.1's trust-state derivation logic can and should be pure-Java-testable, exactly like the existing `ClientResourceSyncStateTest`/`ClientResyncRequestGateTest` (which already test `ClientResourceSyncState`/`ClientResyncRequestGate` — both zero-Minecraft-dependency classes — with plain JUnit).

Recommended test list, mapped to the task's explicit ask:
- Client scalar query — feed a `ClientResourceSyncState` with a known scalar, assert the façade's `Scalar` result matches.
- Client partitioned query — same for spell slots, asserting partition-key preservation (1-10, ascending).
- No-snapshot state — `hasSynced()==false` → `Unavailable(NOT_SYNCHRONIZED_YET)` (or `NEVER_SYNCED_YET` trust, depending on final naming) for every generic-sync resource.
- Native-client-view source — cannot be pure-Java-tested (requires a `Minecraft`/`LocalPlayer` instance) — see §16.2.
- Generic synchronized source — pure, feed a populated `ClientResourceSyncState`, assert `source==GENERIC_SYNCHRONIZED_VIEW`.
- Missing resource — query an unregistered id → `Unavailable(RESOURCE_UNREGISTERED)`.
- Model mismatch — query `scalar()` on a partitioned-only id (or vice versa) → `Unavailable(MODEL_MISMATCH)`.
- Immutable result exposure — assert no returned record exposes a mutable backing collection (straightforward given records + `NavigableMap` copies).
- Trust/freshness state — `FRESH` vs `PENDING_RESYNC` vs `NEVER_SYNCED_YET` transitions, driven purely by feeding `ClientResourceSyncState`/`ClientResyncRequestGate` combinations (both already pure and already tested individually — the façade's derivation logic just needs its own focused tests).
- Parity match / mismatch / transient-mismatch grace period / persistent mismatch — pure, feed two synthetic value pairs plus a synthetic tick counter into the classification function from §10.4.
- Rage uninitialized vs 0/0 — pure, exactly mirrors the existing server-side `PlayerResourceSyncStateTest` tests (`missingRageIsOmittedRatherThanEncodedAsAValidZeroPool` / `validRageZeroZeroRemainsRepresentable`) but on the client-side façade instead.
- Spell-slot partition parity — pure, feed synthetic wire partitions + synthetic `ClientSpellSlotManager`-shaped arrays into the parity comparator.
- Lifecycle reset — pure, assert the façade correctly reports `NEVER_SYNCED_YET` immediately after `ClientResourceSyncState.clear()`.
- Pending resync — pure, assert `ClientResyncRequestGate.isPending()==true` maps to `PENDING_RESYNC` trust without losing the last-known value.

### 16.2 Requires Minecraft/Fabric runtime **[FACT/RECOMMENDATION]**
- Native-client-view reader (`Minecraft.getInstance().player` access) — cannot be unit-tested without a running client, exactly as `ClientResourceSyncManager` itself is already documented as untestable for the same reason ("cannot itself be unit-tested since it also sends a real network packet," per `ClientResyncRequestGateTest`'s own Javadoc). Recommend the same mitigation Phase 3A used: keep the reader as thin as possible (a few lines wrapping `Minecraft.getInstance()`) so the untested surface area is minimal and the actual logic (rounding, result construction) is factored into a pure helper that *is* tested.
- Dedicated-server classloading boundary — cannot be asserted by a unit test in this codebase's current test setup (no multi-process/classloader-isolation test harness exists for any subsystem here); recommend a manual verification step (start a dedicated server build, confirm no `NoClassDefFoundError`/`ClassNotFoundException` referencing any new client-façade class) as part of Phase 3B-1's manual validation, matching how Phase 3A's own manual smoke test was conducted (per the Phase 3A report, §21).

### 16.3 Existing 519 tests **[FACT — not touched]**
No existing test was read, modified, weakened, or replaced during this audit. This report recommends only additive tests for new Phase 3B code.

---

## 17. Risks and unresolved questions

1. **[OPEN QUESTION]** Client-side respawn clearing (§13) — should `ClientResourceSyncManager.clear()` also be wired to a client-side respawn-equivalent event, or is relying on the next full snapshot's wholesale replacement sufficient? Recommend deciding this explicitly in Phase 3B-1 rather than carrying the current implicit assumption forward silently.
2. **[OPEN QUESTION]** Rage absence-vs-zero classification complexity for a genuine Barbarian vs a non-Barbarian (§10.4) — is class-aware parity classification worth the complexity, or should Phase 3B accept a coarser, unconditional `EXPECTED_TRANSITIONAL` treatment and rely on manual Barbarian playtesting to catch real gaps?
3. **[OPEN QUESTION]** Should `ClientResourceTrust` expose the raw revision number for diagnostics, or stay a pure enum with revision access via a separate diagnostics-only accessor (§6.2)?
4. **[RECOMMENDATION, low-risk, small]** Add DEBUG logging for `MALFORMED`/`INCOMPATIBLE_SCHEMA` outcomes in `ClientResourceSyncManager`, currently silent (§13) — flagged as worth doing but not done in this audit (read-only).
5. **[RISK]** The client/server package-sharing convention (`api.rpg.resources.sync`, `networking.resource` hosting both client and server classes side by side, §14) has held so far but is enforced by discipline only — a future contributor (or Stefan working quickly) could accidentally reference a client-only class from server registration code with only a runtime `ClassNotFoundException` on a dedicated server to catch it, not a compile error. Recommend `@Environment` annotations as a Phase 3B or later hardening pass (§14.1), not a blocker.
6. **[RISK]** `ClientResourceSyncManager.state()`'s package-private live-reference exposure (§2.2) means Phase 3B's façade, if placed in the same package, could reach into `ClientResourceSyncState` directly without going through the intended public query surface — a purely internal risk (nothing outside the package can do this), but worth a deliberate decision about which package the façade lives in relative to `networking.resource`, so the façade is forced to go through its own public API even internally (good API hygiene, not a functional risk).
7. **[RISK — already flagged by the code itself, not new]** `ClientResourceSyncState.applyDelta`/`applyFull` mutate live maps with no lock or thread-check (§2.2) — currently safe only because nothing reads concurrently with packet application. Adding a façade that's read from a HUD render thread and mutated from a network-receive callback are (per standard Fabric client architecture) typically the *same* thread already, but this assumption is nowhere enforced in code. **Recommend explicitly documenting this assumption in the façade's Javadoc** (continuing Phase 3A's own documentation-only mitigation style) rather than adding synchronization machinery that Phase 3A itself didn't judge necessary.
8. **[UNRESOLVED, minor]** `PlayerChargesComponent`'s `applySyncPacket` writes `max` in the wire format but the client only ever applies `current` to an existing pool (§4.4) — currently inconsequential (max changes are rare, and a fresh pool does get a max via its fabricated default), but the shadow-parity engine comparing generic-sync `maximumUnits` against this legacy path's `getMax(...)` should be aware this could theoretically drift if a Rage pool's max changes while the client already holds a pool instance. Not a Phase 3B blocker; noted for parity-engine test coverage (§16.1's "Rage uninitialized vs 0/0" test should also cover a max-change scenario).

---

## 18. Recommended Phase 3B implementation slices — confirmation/refinement of the proposed sequence

The task's proposed three-slice sequence is **sound and is confirmed**, with the refinements below.

**Phase 3B-1 (confirmed, with additions):**
- Immutable client query/result model (§6.2).
- Public presentation-only façade (Option B, §5.1) — package-private-to-public boundary decision per §17.6.
- Native Health/Food/Breath readers (§7).
- Generic Mana/Stamina/Slots/Rage readers, reading `ClientResourceSyncState` directly (not `PlayerResourceService`, per the hard boundary in §2.5).
- Trust/freshness metadata (§9).
- **Addition:** the malformed/incompatible-schema logging fix (§17.4) is small enough to bundle into 3B-1 alongside the façade work, since it touches the same files.
- **Addition:** explicitly resolve the respawn-clearing open question (§17.1) as a design decision during this slice, even if the resolution is "no change needed."
- No parity telemetry yet (confirmed, as proposed).

**Phase 3B-2 (confirmed, with refinement):**
- Legacy/native shadow-parity engine (§10) — pure classification logic + impure comparison trigger wired to packet receipt (§10.2), not per-tick.
- Grace window (§10.3, §11).
- Mismatch classification (§10.4), including the `EXPECTED_TRANSITIONAL` pre-classified cases.
- Bounded diagnostics — DEBUG-level, transition-only logging (§12).
- No consumer migration (confirmed, as proposed).
- **Refinement:** resolve the Rage class-awareness open question (§17.2) before finalizing the classification function's signature, since it affects whether the comparator needs access to the player's class.

**Phase 3B-3 (confirmed, with addition):**
- Parity verification tooling (§12: `/totality resource parity` debug command or equivalent).
- Debug command or development report, as proposed.
- Automated and manual validation — recommend explicitly re-running the same manual smoke-test checklist Phase 3A used (join/disconnect/reconnect, spend/restore for all four generic-sync resources, death/respawn, dimension transfer) but now also checking the façade's reported values and parity classifications match expectations at each step.
- Final readiness decision for Phase 3C.
- **Addition:** the dedicated-server classloading manual check (§16.2) belongs here as part of "manual validation," not deferred past this slice.

No implementation of any slice was performed in this audit.

---

## 19. Explicit Phase 3B out-of-scope list

Per the task's constraints, confirmed as understood and respected throughout this audit:
- No change to Resource authority (owners remain: vanilla for Health/Food/Breath; `PlayerResourceComponent`/`PlayerManaManager`/`PlayerStaminaManager` for Mana/Stamina; `SpellSlotComponent` for spell slots; `PlayerChargesComponent` for Rage).
- No change to resource formulas (max-Mana/Stamina calculation, spell slot table, Rage charge table — all untouched).
- No removal of any legacy packet or manager (`SyncManaPayload`, `SyncStaminaPayload`, `ClientManaManager`, `ClientStaminaManager`, `ClientSpellSlotManager`, `PlayerChargesComponent`'s bespoke sync — all remain exactly as-is).
- No migration of any HUD, menu, class screen, radial, or tooltip consumer (§15's table — none modified).
- No change to spell casting.
- No start of Phase 3C.
- No start of Entitlement, Tooltip, or Spell API implementation.
- No Automaton/physiology-specific Breath-unavailability logic implemented (§7.5 — only left room for it conceptually).
- No gameplay-visible warning, toast, or chat message from any parity mismatch (§10, §12).

---

## Stop point — status

1. No implementation build was run; only inspection commands (`git status`, `git log`, file reads, greps) were used.
2. Created file: `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B_CLIENT_VIEW_AND_PARITY_READINESS.md` (this document).
3. Final `git status --short` shown below.
4. No source or test file was changed.
5. Nothing was staged, committed, or pushed.
6. Stopping here for review, per the task's explicit instruction. No Phase 3B implementation prompt has been produced.
