# TOTALITY — Generic Player Resource API — Phase 3B-2 Readiness Audit
## Shadow-Parity Architecture — Read-Only Report

Status: **read-only audit — no source, test, or build file was modified to produce this report.**
Scope: Phase 3B-2 design only. Does not implement parity. Does not begin Phase 3B-3 or Phase 3C.

Citation legend (matching the Phase 3B readiness audit's own convention):
- **[FACT]** — confirmed by direct inspection of the cited file during this audit.
- **[INFERENCE]** — a conclusion drawn from combining multiple confirmed facts, not itself a direct quotation.
- **[RECOMMENDATION]** — a Phase 3B-2 design proposal, not a statement about existing code.
- **[OPEN QUESTION]** — genuinely unresolved; needs a decision before or during Phase 3B-2 implementation.

---

## 1. Baseline: branch, HEAD, tracking, working-tree status

- **[FACT]** Current branch: `feature/general-resource-api`.
- **[FACT]** `HEAD` = `2fbdee92f28c23614701ad04a44533d065c5342d`, subject "Add trusted client resource view" — matches the expected checkpoint exactly.
- **[FACT]** Tracking `origin/feature/general-resource-api`; `git rev-list --left-right --count origin/feature/general-resource-api...HEAD` returned `0 0` — fully in sync, nothing to push or pull.
- **[FACT]** `git status --short` at audit start showed only the anticipated "known unrelated" entries:
  - Modified generated datagen JSON under `src/main/generated/data/**` (worldgen noise settings, loot tables, gear recipes).
  - Untracked `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3A_*_REVIEW_BUNDLE.zip` (3 zips) and `TOTALITY_RESOURCE_API_PHASE_3B1_*_REVIEW_BUNDLE.zip` (2 zips).
  - Untracked `Context/Trading Test/trade_screen2-5.png`.
  - Untracked `logs/` and `src/main/generated/.cache/`.
  - No `.java` file under `src/main` or `src/test` appears anywhere in the modified/untracked list.
- **[FACT]** `git diff --cached --name-only` returned zero entries — nothing staged.
- **[FACT]** `/Inspiration Mods` was excluded from every search performed for this report.
- **[FACT]** Automated baseline confirmed: `./gradlew test` returned `BUILD SUCCESSFUL` (`UP-TO-DATE`, no recompilation needed since HEAD already reflects the validated Phase 3B-1 state); summing `tests="…"` across all `build/test-results/test/*.xml` gives **572** — matching the expected baseline exactly.

**Conclusion: checkpoint fully satisfied. Safe to proceed with a read-only Phase 3B-2 audit.**

---

## 2. Phase 3B-1 façade summary relevant to parity

All confirmed by direct reading of the current source (not merely the pre-implementation Phase 3B readiness audit, which was written *before* Phase 3B-1 was implemented and its external-review correction applied — several details below differ from that document's original recommendation).

### 2.1 Public entry points **[FACT]**
`zcylas.totality.api.rpg.resources.client.ClientResourceService` (`ClientResourceService.java`):
- `INSTANCE` — process-wide singleton over `PlayerResourceRegistry.INSTANCE` + a fresh `ClientResourceReaderRegistry`.
- `query(Identifier)` — resolves the canonical `PlayerResourceDefinition`, looks up a registered `ClientResourceReader`, and defense-in-depth re-validates the reader's returned shape against the definition (`validateShape`, lines 101-115).
- `queryScalar(Identifier)` / `queryPartitioned(Identifier)` — pre-check the definition's canonical model before calling any reader; a shape mismatch is `MODEL_MISMATCH` even before the first sync.
- `registerReader(Identifier, ClientResourceReader)` — delegates to `ClientResourceReaderRegistry.register`, which throws on a duplicate id (never a silent replace).

### 2.2 Result shape — as actually implemented, not as originally proposed **[FACT]**
`ClientResourceQueryResult` (sealed interface, `ClientResourceQueryResult.java`):
- `Scalar(resourceId, currentUnits, maximumUnits, overflowUnits, unitScale, source, trust)` — validates `unitScale>=1`, all quantities `>=0`, `current<=maximum+overflow` (`Math.addExact`-guarded).
- `Partitioned(resourceId, NavigableMap<Integer,Partition>, unitScale, source, trust)` — compact constructor defensively rebuilds into an unmodifiable `TreeMap` (deterministic ascending order regardless of caller order); `Partitioned.of(...)` factory rejects a duplicate partition id before any map exists.
- `Unavailable(resourceId, reason)` — no numeric fields at all; a caller cannot read a fabricated value out of it.

`ClientResourceSource` (`ClientResourceSource.java`): exactly two members — `NATIVE_CLIENT_VIEW`, `GENERIC_SYNCHRONIZED_VIEW`.

`ClientResourceTrust` (`ClientResourceTrust.java`): exactly two members — `FRESH`, `PENDING_RESYNC`. **Deviates from the pre-implementation Phase 3B readiness audit's §6.2/§9.1 proposal**, which recommended a third `NEVER_SYNCED_YET` trust member: the implemented design instead treats "never synced" as `Unavailable(NOT_SYNCHRONIZED_YET)`, never a successful value with weak trust (documented explicitly in the enum's own Javadoc, `ClientResourceTrust.java` lines 4-7). This is the correct, stricter design and Phase 3B-2 must build against it, not against the original proposal.

`ClientResourceUnavailableReason` (`ClientResourceUnavailableReason.java`): **seven** members, not the six originally proposed — `RESOURCE_UNREGISTERED`, `CLIENT_SOURCE_NOT_CONFIGURED`, `NOT_SYNCHRONIZED_YET`, `NOT_AVAILABLE_TO_PLAYER`, `NO_LOCAL_PLAYER`, `MODEL_MISMATCH`, and `MALFORMED_SOURCE_STATE` (added by the Phase 3B-1 external-review correction, 2026-07-23, for a native source reporting a structurally invalid value — e.g. a non-positive Breath maximum). Phase 3B-2's parity classification model must account for this seventh reason explicitly (see §8).

### 2.3 Reader strategies **[FACT]**
- `NativeClientResourceReader` (Health/Food/Breath) — pure, reads only through `NativeResourceAccess`. Health reuses `HealthResourceAdapter.toUnits` (fixed-point, `UNIT_SCALE=1000`); Food reuses `FoodResourceAdapter.NATIVE_MAXIMUM=20` and stays on the raw 0-20 scale (no ×5 display conversion applied here); Breath guards `rawMaximum<=0` before any clamping (mirrors `BreathResourceAdapter.normalize`'s full policy post-correction) and clamps `current` into `[0, maximum]` only once the maximum is confirmed positive. All three route numeric construction through a shared `safeScalar` helper that narrowly catches `IllegalArgumentException`/`ArithmeticException` and converts either into `MALFORMED_SOURCE_STATE` — never lets a validation failure escape the façade.
- `GenericSyncClientResourceReader` (Mana/Stamina/Standard Spell Slots/Rage) — pure, reads only through `GenericSyncResourceAccess` (`hasSynced()`, `isResyncPending()`, `scalar(Identifier)`, `partitioned(Identifier)` — no mutating method exists on this interface at all, enforced by a reflection-based test, `ClientResourceServiceTest.genericSyncResourceAccessDeclaresNoMutatingMethod`). Never calls `PlayerResourceService.query(...)` — structurally unavailable client-side for these four `LEGACY_BESPOKE_SYNCHRONIZATION` resources.
- `ClientResourceSyncBridge` (`networking/resource/ClientResourceSyncBridge.java`, `@Environment(EnvType.CLIENT)`, singleton `INSTANCE`) — the sole production `GenericSyncResourceAccess` implementation, living in the same package as `ClientResourceSyncManager` specifically so it can call that class's package-private `state()`/`isResyncPending()` accessors. `ClientResourceSyncManager.state()` remains package-private — never made public.

### 2.4 Production wiring **[FACT]**
`TotalityClientResourceReaders.register()` (`client/resource/TotalityClientResourceReaders.java`, `@Environment(EnvType.CLIENT)`) is called once from `TotalityClient.onInitializeClient()` (`TotalityClient.java` line 138), registering one shared `NativeClientResourceReader` for `totality:health`/`totality:food`/`totality:breath` and one shared `GenericSyncClientResourceReader` for `totality:mana`/`totality:stamina`/`totality:spell_slots`/`totality:rage`. **No production consumer calls any of this yet** — registration only, confirmed by the Phase 3B-1 report's §20 consumer-preservation list and independently re-confirmed in this audit's §12 consumer inventory below (unchanged since Phase 3B-1).

### 2.5 Malformed/incompatible logging (already built, Phase 3B-1) **[FACT]**
`ClientResourceSyncManager.applyFull`/`applyDelta` (`networking/resource/ClientResourceSyncManager.java` lines 51-83) log at DEBUG via `Totality.LOGGER.debug(...)` exactly when the outcome is `MALFORMED`/`INCOMPATIBLE_SCHEMA`, using bounded metadata from the pure `ClientResourceSyncRejectionDiagnostics.describeFull`/`describeDelta` (payload type, schema version, base/current/resulting revision, `ApplyResult` — never a resource id or numeric value). This is the precedent Phase 3B-2's own logging (§16) should follow exactly.

---

## 3. Exact legacy mirror map

### 3.1 `ClientManaManager` **[FACT]**
`src/main/java/zcylas/totality/networking/mana/ClientManaManager.java` (14 lines, unchanged since the Phase 3B readiness audit):
```java
public class ClientManaManager {
    private static int mana = 100;
    private static int maxMana = 100;
    public static int getMana() { return mana; }
    public static int getMaxMana() { return maxMana; }
    public static void sync(int mana, int maxMana) { ... }
}
```
- Shape: two bare `static int`s, default `100/100`, indistinguishable from a real synced `100/100`.
- Never cleared on disconnect/reconnect/respawn/dimension-change anywhere in the codebase.
- Update trigger: `SyncManaPayload`, received at `TotalityClientPacketHandlers.register()` lines 46-50, dispatched directly to `ClientManaManager.sync(...)`.
- Send sites (server-side, `ServerPlayNetworking.send(player, new SyncManaPayload(...))`): `ManaServerTick.syncMana(ServerPlayer)` (the every-20-tick regen loop, `ManaServerTick.java` line 26), `GrimoireItem.java:124`, `AlchemyEffects.java:137,146`, `HeatVisionAbility.java:90-93` (inline, not via `ManaServerTick.syncMana`), `PlayerResourceRecalculator.java:67-69,105-107` (inline), `TotalityCommands.java:99,623` (a `/totality` admin command setting max Mana). **No push on player JOIN** — confirmed: `PlayerConnectionEvents.register()`'s `ServerPlayConnectionEvents.JOIN` handler (`init/events/PlayerConnectionEvents.java` lines 47-124) never calls any Mana sync, only `StaminaServerTick.syncStamina(player)` (line 65).
- **[FACT — real, pre-existing gap independent of the Resource API]** `FormulaResolver.tryCast` (`api/magic/grimoire/context/FormulaResolver.java` lines 25-37, the rune-casting cost-deduction path) calls `PlayerManaManager.removeMana(player, cost)` (line 35) but sends **no** legacy Mana sync packet at all afterward — no call to `ManaServerTick.syncMana` and no inline `SyncManaPayload` construction anywhere in that method or its caller chain that this audit found. See §5's timing analysis for why this means `ClientManaManager` can go stale after a rune cast while the generic Resource view (dirty-marked via `PlayerManaManager.setMana`'s own `ResourceSyncManager.markDirty` call, `PlayerManaManager.java` line 36) is flushed and correct by the end of that same server tick.
- Consumers: `TotalityHudRenderer` (HUD mana bar), `OverviewTab` (character screen resources panel) — per the Phase 3B readiness audit §15, re-confirmed unchanged in this audit's §12.

### 3.2 `ClientStaminaManager` **[FACT — directly re-read this session]**
`src/main/java/zcylas/totality/networking/stamina/ClientStaminaManager.java` (14 lines) — identical shape to Mana: `static int stamina=100, maxStamina=100`, `sync(int,int)`.
- **Known asymmetry with Mana, confirmed**: `PlayerConnectionEvents.register()` line 65 explicitly calls `StaminaServerTick.syncStamina(player)` inside the `ServerPlayConnectionEvents.JOIN` handler, with an inline comment: *"Sync stamina so the client HUD shows the correct value immediately rather than defaulting to 100 until the first drain/regen event."* Mana has no equivalent join-time push — this is a pre-existing, already-tolerated inconsistency between the two legacy mirrors (see §11's `EXPECTED_SEMANTIC_DIFFERENCE` classification).
- Send sites: the every-20-tick regen block in `StaminaServerTick.java` (lines 144-171), plus ~20 explicit call sites across `ShurikenItem`, `TotalityCommands` (×2), `TridentItemMixin`, `PiercingWeaponMixin`, `KineticWeaponMixin`, `MaceItemMixin`, `OffhandAttackHandler`, `MovementStaminaHandler`, `PlayerResourceRecalculator` (×2), `AlchemyEffects` (×2), `BowStaminaHandler` (×2), `WeaponStaminaHandler`, `VeinminerAbility` (×2), `GroundSlamAbility` — all confirmed by direct grep of `StaminaServerTick.syncStamina` call sites. Unlike Mana, this audit found no Stamina-spending call site analogous to `FormulaResolver.tryCast` that omits the legacy sync call.
- Consumers: `TotalityHudRenderer`, `OverviewTab`, and `TotalityMovementHandler` (a **gameplay-authoritative prediction/gating read**, not a display consumer — must never be treated as a parity-observable UI consumer; see §12 row 8).

### 3.3 `ClientSpellSlotManager` **[FACT — directly re-read this session]**
`src/main/java/zcylas/totality/api/magic/spell/ClientSpellSlotManager.java` (28 lines):
```java
public final class ClientSpellSlotManager {
    private static final int[] maxSlots  = new int[SpellSlotComponent.MAX_SPELL_LEVEL];
    private static final int[] usedSlots = new int[SpellSlotComponent.MAX_SPELL_LEVEL];
    public static void apply(int[] newMax, int[] newUsed) { ... }
    public static int getMax(int spellLevel) { ... }
    public static int getRemaining(int spellLevel) { return maxSlots[i] - usedSlots[i]; }
}
```
- 10 fixed-size int arrays (levels 1-10 via `spellLevel-1` indexing), `getRemaining` computed on the fly from `maxSlots[i]-usedSlots[i]`, never stored as its own field.
- Absence semantics: all-zero arrays are Java's default and are **also** the legitimate "non-caster" state — ambiguity by design on both legacy and generic sides, not a divergence to reconcile.
- Update trigger: piggybacks on the generic per-component `ComponentSync` packet. `SpellSlotComponent.writeSyncPacket`/`applySyncPacket` (`api/magic/spell/SpellSlotComponent.java` lines 113-128) — `applySyncPacket` reads `maxSlots[i]`/`usedSlots[i]` for all 10 levels then calls `ClientSpellSlotManager.apply(maxSlots, usedSlots)` (line 127). Routed through `TotalityClientPacketHandlers.register()`'s generic `ComponentSync.PACKET_TYPE` receiver (lines 64-85), which looks up the matching `ComponentKey` and calls `synced.applySyncPacket(buf)`.
- Send trigger: `SpellSlotComponent.sync()` (private method, lines 130-137) — called from `recalculate`, `useSlot`, `restoreAll`, `restoreSome`. **Sends the legacy `ComponentSync` packet synchronously first** (`SpellSlotComponents.SPELL_SLOTS.sync((ComponentProvider) player)`, line 132) and only **then** calls `ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.SPELL_SLOTS)` (line 135) — the generic notification is a deferred dirty-mark only, not an immediate send (see §5).
- Never explicitly cleared on disconnect.
- Consumer: `SpellRadialScreen` (`getMax`/`getRemaining` per level).

### 3.4 `PlayerChargesComponent` — Rage client mirror **[FACT — directly re-read this session]**
`src/main/java/zcylas/totality/api/rpg/classes/PlayerChargesComponent.java` (211 lines). No dedicated `ClientRageManager` class — the same `PlayerChargesComponent` class is attached client-side with a `null` player: `ChargeComponents.register()` (`api/rpg/classes/ChargeComponents.java` lines 17-30) calls `PlayerComponentEvents.registerClientComponent(PLAYER_CHARGES, () -> new PlayerChargesComponent(null))`.
- Storage: `Map<Identifier, ChargePool> pools` (a `LinkedHashMap`), where `ChargePool(int current, int max, RestType rechargeType, int rechargeAmount)`. Rage's key is `BarbarianRageAbility.CHARGE_ID = totality:barbarian_rage` (`api/ability/impl/barbarian/BarbarianRageAbility.java` line 20) — deliberately a different `Identifier` from the Resource API's `totality:rage` (`PlayerResourceIds.RAGE`).
- Absence semantics: the client map is **sparse** — `getCurrent(id)`/`getMax(id)` (lines 62-70) return `0` for a missing key, so "never granted" and "granted, currently 0" both read as `0/0` client-side. Confirmed read site: `TotalityClient.java` lines 165-191's `ISecondaryResource` registration wraps every `getCurrent`/`getMax` call in `try { ... } catch (Exception e) { return 0; }` — a **third** possible source of an observed `0`, structurally indistinguishable from the other two at the read site (an exception, e.g. a null `client.player`, also collapses to `0`).
- Clear/reset: tied to `ComponentContainer` attachment lifecycle (a fresh empty `pools` map on new `LocalPlayer` instantiation — join/respawn/dimension change create a new client-side component instance via the registered supplier), not an explicit clear call. This is a **structurally different reset trigger** than `ClientResourceSyncManager.clear()`, which is wired to three explicit Fabric lifecycle events (`ClientPlayConnectionEvents.JOIN`/`DISCONNECT`, `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`, `TotalityClient.java` lines 106-123). The two reset mechanisms are independent and not necessarily synchronized to the same instant (see §14).
- Update trigger: `writeSyncPacket`/`applySyncPacket` (lines 106-129) on the component itself, routed through the same generic `ComponentSync.PACKET_TYPE` receiver as `SpellSlotComponent`. **Confirmed asymmetry, re-verified this session**: `writeSyncPacket` sends both `current` and `max` per pool (lines 109-111), but `applySyncPacket` (lines 116-129) only applies `current` to an already-existing client-side pool via `existing.withCurrent(current)` (line 123) — `max` is read off the wire (`buf.readInt()`, consumed) but discarded for an existing pool; `max` is only actually used when the client-side pool doesn't exist yet, in which case a brand-new `ChargePool(current, max, RestType.LONG, -1)` is fabricated (line 126). **This means a live maximum change to an already-granted Rage pool (e.g. a Barbarian's Rage-charge-count increasing at a class level-up) is never reflected in `getMax(...)`'s client-side value once the pool object already exists** — a genuine, real semantic gap this audit newly confirms by direct code reading (the Phase 3B readiness audit flagged this only as a hypothetical "worth flagging," §4.4/§17.8; this audit confirms the exact mechanism: `applySyncPacket`'s `existing.withCurrent(current)` branch structurally cannot update `max`).
- Send trigger: `PlayerChargesComponent.sync()` (private, lines 168-181) — calls `ChargeComponents.PLAYER_CHARGES.sync((ComponentProvider) player)` (legacy `ComponentSync`, synchronous) first, **then** `ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE)` (line 179) — identical ordering pattern to `SpellSlotComponent.sync()`.
- Consumers: `TotalityClient.java`'s `ISecondaryResource` HUD registration (Rage pip bar, lines 165-191, gated by `ClientClassManager.hasClass() && TotalityClasses.BARBARIAN_ID.equals(ClientClassManager.getPrimaryClassId())`, lines 181-183), `ClassTab.java` line 358-367 (class resource panel, also wrapped in `try/catch (Exception ignored)`).

**[FACT, newly significant for §9]** The exact same client-side "is this player a Barbarian" check (`ClientClassManager.hasClass()` + `TotalityClasses.BARBARIAN_ID.equals(ClientClassManager.getPrimaryClassId())`) that gates the legacy Rage HUD's visibility already exists in production code (`TotalityClient.java` lines 181-183) and requires no new accessor to reach from a Phase 3B-2 parity coordinator.

### 3.5 Native vanilla state (Health/Food/Breath) **[FACT, unchanged since the Phase 3B readiness audit — re-confirmed no regression]**
No parity pair exists or is needed — see §7.

---

## 4. Exact generic-to-legacy comparison pairs

**[FACT/RECOMMENDATION]**

| Resource | Generic query | Legacy comparison | Normalization |
|---|---|---|---|
| Mana | `ClientResourceService.INSTANCE.queryScalar(totality:mana)` → `Scalar.currentUnits/maximumUnits` | `ClientManaManager.getMana()/getMaxMana()` | Direct 1:1 — both `unitScale=1`, no conversion (confirmed: `ManaResourceAdapter`/`ProductionResourceDefinitions` register Mana at `unitScale(1)`, `ResourceDisplayConversion.IDENTITY`) |
| Stamina | `queryScalar(totality:stamina)` → `Scalar.currentUnits/maximumUnits` | `ClientStaminaManager.getStamina()/getMaxStamina()` | Direct 1:1, `unitScale=1` both sides |
| Standard Spell Slots | `queryPartitioned(totality:spell_slots)` → `Partitioned.partitions()[level].currentUnits/maximumUnits` for levels 1-10 | `ClientSpellSlotManager.getMax(level)` / `getRemaining(level)` | **`currentUnits` = remaining slots, not used slots** — see §4.1 below for the exact derivation. `maximumUnits` ↔ `getMax(level)` directly. |
| Rage | `queryScalar(totality:rage)` → `Scalar.currentUnits/maximumUnits` | `PlayerChargesComponent` client instance's `getCurrent(BarbarianRageAbility.CHARGE_ID)`/`getMax(BarbarianRageAbility.CHARGE_ID)` | Direct 1:1, `unitScale=1` both sides, **but see §9 for the absence-vs-zero distinction that must not be flattened** |

### 4.1 Spell-slot `currentUnits` semantics — resolved definitively **[FACT]**
`StandardSpellSlotsResourceAdapter.resolve(...)` (`api/rpg/resources/external/StandardSpellSlotsResourceAdapter.java` lines 135-148):
```java
int maximum = component.getMax(level);
int used = component.getUsed(level);
long current = (long) maximum - used;
partitions.put(level, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(current, maximum));
```
**Confirmed: the generic wire's (and therefore the façade's) `currentUnits` = `maximum - used` = REMAINING slots** — the exact same quantity `ClientSpellSlotManager.getRemaining(spellLevel) = maxSlots[i] - usedSlots[i]` already computes. This resolves the task's explicit open question definitively: **the correct parity comparison is `partition.currentUnits() == legacy.getRemaining(level)` and `partition.maximumUnits() == legacy.getMax(level)`** — never a naive comparison against `usedSlots[i]` directly (which corresponds to nothing in the generic wire at all; `usedSlots` must be derived as `maximumUnits - currentUnits` if needed for a diagnostic, never compared directly against a generic field).

### 4.2 Native resources are structurally excluded, not compared **[RECOMMENDATION, confirmed correct approach]**
Health/Food/Breath have no second generic copy and never will while `ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION` holds (`ResourceSyncManager.isEligibleForGenericSync`, confirmed unchanged in this audit's §5 re-read of `ResourceSyncManager.java` lines 152-173, excludes any `NATIVE_SYNCHRONIZATION` adapter from ever appearing in a full/delta packet). **Recommend structural exclusion from the parity engine** (the engine's resource-id iteration is seeded from the four `LEGACY_BESPOKE_SYNCHRONIZATION` ids only, never from a full resource list that then has to skip three), not a runtime `NOT_APPLICABLE` classification computed per-query — there is no legacy/generic pair to even attempt for these three, so "comparing" them would be comparing the native reader's own output against itself. See §8 for why a `NOT_APPLICABLE` enum value is still worth keeping for diagnostic completeness/logging symmetry even though the engine never actually invokes a comparison for these three ids.

---

## 5. Packet/update timing analysis

### 5.1 Server tick registration order — the decisive, previously-unconfirmed fact **[FACT, newly confirmed this session]**
`Totality.java`, `registerServerTickEvents()` (lines 144-153):
```java
private void registerServerTickEvents(){
    ManaServerTick.register();        // line 145
    StaminaServerTick.register();     // line 146
    AbilityServerTick.register();
    ConditionServerTick.register();
    ServerScheduler.register();
    zcylas.totality.api.rpg.rest.RestSessionManager.register();
    zcylas.totality.networking.resource.ResourceSyncServerTick.register();   // line 151
    registerPassiveTicker();
}
```
Each of `ManaServerTick.register()`/`StaminaServerTick.register()`/`ResourceSyncServerTick.register()` registers exactly one `ServerTickEvents.END_SERVER_TICK` listener (confirmed: `ManaServerTick.java` line 13, `StaminaServerTick.java` line 31, `ResourceSyncServerTick.java` line 10). Fabric's `Event<T>` (array-backed invoker) invokes registered listeners in registration order. **Confirmed: `ManaServerTick`'s and `StaminaServerTick`'s `END_SERVER_TICK` callbacks always run, in full, before `ResourceSyncManager.flush(server)` runs, within the same server tick.**

### 5.2 What this means for the every-20-tick regen cycle **[FACT + INFERENCE]**
On a regen tick (`tickCounter % 20 == 0`): `ManaServerTick`'s callback calls `PlayerManaManager.addMana`/`setMana` (which itself calls `ResourceSyncManager.markDirty(MANA)`, `PlayerManaManager.java` line 36) and then, still within the same callback, `syncMana(player)` (`ManaServerTick.java` line 26) — an immediate `ServerPlayNetworking.send(player, new SyncManaPayload(...))`. This happens **before** `ResourceSyncManager.flush(server)` runs later in the same tick (registered after, §5.1), which is where the generic delta/full packet for the now-dirty `totality:mana` would actually be queried and sent. **[INFERENCE, strong, network-layer]** Since a Minecraft client connection is a single ordered stream (packets are written to one Netty channel in program order and Fabric's payload-codec receivers are invoked in arrival order on the client's main thread), the client is guaranteed to receive `SyncManaPayload` before the corresponding `ResourceDeltaSyncPayload`/`ResourceFullSyncPayload` for a change originating in the same server tick's regen cycle. This is a materially stronger and more precise conclusion than the pre-implementation Phase 3B readiness audit's hedged "not guaranteed to be simultaneous" language (§10.3 of that document) — the *relative* order is guaranteed by tick-registration order plus single-channel delivery; only the *absolute* number of client ticks between the two arrivals is not guaranteed (that gap is what the grace window in §7 exists to absorb).
- Identical structural reasoning applies to Stamina's regen block (`StaminaServerTick.java` lines 144-171, same file/callback as every other Stamina drain case, all registered before `ResourceSyncServerTick`).

### 5.3 Explicit non-tick-loop spend/restore call sites — same relative ordering, different absolute timing **[FACT]**
`SpellSlotComponent.sync()` (lines 130-137) and `PlayerChargesComponent.sync()` (lines 168-181) both: (1) synchronously send the legacy `ComponentSync` packet immediately at the point of mutation (`useSlot`, `restoreAll`, `consume`, `restore`, etc. — which may fire at any point in a tick, e.g. during spell-cast or ability-activation packet handling, not only inside a tick-loop callback), and (2) only afterward call `ResourceSyncManager.markDirty(...)`, which is a pure bookkeeping call — the actual generic packet is not sent until `ResourceSyncManager.flush(server)` runs at `END_SERVER_TICK`, i.e. strictly after every other server-tick event has processed that tick's mutations. **This ordering (legacy send immediate and synchronous; generic send deferred to end-of-tick batch) is structurally guaranteed for every current spell-slot and Rage mutation site**, since both components' private `sync()` method is the sole path either legacy packet or dirty-mark can be triggered through, and both hard-code legacy-send-then-mark-dirty in that literal order.
- Mana/Stamina's `setMana`/`setStamina` (`PlayerManaManager.java` line 30-37, `PlayerStaminaManager.java` line 46-53) mark dirty **unconditionally on every call** (not gated behind an explicit legacy sync), but do **not** themselves send the legacy packet — each call site must separately call `ManaServerTick.syncMana(player)`/`StaminaServerTick.syncStamina(player)` (or construct `SyncManaPayload`/`SyncStaminaPayload` inline). This decoupling is the root cause of §5.4's finding.

### 5.4 Confirmed counter-example: a real, current case where legacy lags *behind* generic **[FACT — newly discovered this session, not present in any prior Phase 3A/3B document]**
`FormulaResolver.tryCast` (`api/magic/grimoire/context/FormulaResolver.java` lines 25-37, the rune-based spell-casting mana-cost deduction path):
```java
if (!PlayerManaManager.hasMana(player, cost)) return false;
PlayerManaManager.removeMana(player, cost);
return true;
```
This calls `PlayerManaManager.removeMana` → `setMana` → `ResourceSyncManager.markDirty(MANA)` (generic dirty-mark, will flush at end of this tick) but **sends no legacy `SyncManaPayload` at all** — confirmed by grep across the codebase for every `ManaServerTick.syncMana`/inline `SyncManaPayload` construction call site (§3.1); `FormulaResolver`/its callers are not among them. **Consequence: after a rune cast, `ClientManaManager`'s cached value is stale (shows pre-cast Mana) until the next 20-tick regen cycle (up to ~1 second later, or until some other Mana-affecting action happens to also call `ManaServerTick.syncMana`), while the generic Resource view is already correct by the end of the very same tick the rune was cast in** (assuming `totality:mana` is eligible for generic sync, which it is — `LEGACY_BESPOKE_SYNCHRONIZATION` resources qualify, `ResourceSyncManager.isEligibleForGenericSync`).
- **This is the single most important timing finding of this audit.** It falsifies the "legacy is always first/authoritative-in-practice" assumption a naïve grace-window design might otherwise bake in. The parity engine's grace window (§7) must tolerate mismatches in **either direction** (generic-ahead-of-legacy, as here, or legacy-ahead-of-generic, as in the ordinary regen-tick case of §5.2) — it must never assume a fixed "which side updates first" direction when deciding what counts as an ordinary transitional lag versus a real divergence.
- **[RECOMMENDATION]** This finding is a legitimate, pre-existing legacy-client-staleness bug, entirely independent of the Resource API and outside Phase 3B-2's scope to fix (per the task's explicit "do not modify production code" instruction) — but it is exactly the kind of real discrepancy a shadow-parity engine's whole purpose is to surface. Recommend flagging it explicitly as a known, currently-tolerated `EXPECTED_TRANSITIONAL`-turned-`PERSISTENT`-if-uncorrected case for whoever eventually reviews Phase 3B-2's first real parity logs, rather than silently pre-classifying it away — unlike the Stamina-join-time asymmetry (§3.2) or the Rage-non-Barbarian-zero case (§9), this one is not an intentional design choice recorded anywhere and deserves to surface as a genuine `PERSISTENT_MISMATCH` if it reoccurs during Phase 3B-3 manual validation, so it can be triaged (fixed or formally accepted) on its own merits later.

### 5.5 Join / reconnect / respawn / dimension-change ordering **[FACT, re-confirmed this session against current source]**
- **Join**: `ResourceSyncLifecycleEvents.register()`'s `ServerPlayConnectionEvents.JOIN` listener (`networking/resource/ResourceSyncLifecycleEvents.java` line 19-20) calls `ResourceSyncManager.scheduleFullSnapshot(...)`. This is registered **last** within `ModEvents.register()` (`init/ModEvents.java` line 33, explicit comment: *"Registered last so its JOIN/AFTER_RESPAWN listeners fire after every other listener above has already settled that lifecycle event's authoritative state"*) — i.e. after `PlayerConnectionEvents.register()`'s own JOIN listener (which calls `StaminaServerTick.syncStamina(player)` synchronously at line 65, among many other component syncs). Both are `ServerPlayConnectionEvents.JOIN` listeners; Fabric invokes JOIN listeners in registration order, so the legacy Stamina join-push (and every other component's JOIN-time sync) completes before the generic full-snapshot is even *scheduled* — and the actual full-snapshot *send* only happens later still, at that tick's `ResourceSyncManager.flush`. **Legacy-first ordering is doubly guaranteed on join**: both by JOIN-listener registration order and by the flush-is-end-of-tick rule.
- **Respawn**: `ServerPlayerEvents.AFTER_RESPAWN` — `ResourceSyncLifecycleEvents` registers its listener last within `ModEvents.register()`, same reasoning as JOIN. `PlayerConnectionEvents.register()`'s own `AFTER_RESPAWN` listener (lines 127-146) re-registers Rest listeners and re-grants the Rage charge pool via `BarbarianRageAbility.registerChargePool(newPlayer)` + `ChargeComponents.PLAYER_CHARGES.sync((ComponentProvider) newPlayer)` (lines 137-138, legacy Rage sync, synchronous) — again strictly before the generic full snapshot is scheduled/flushed.
- **Dimension change**: `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` (`ResourceSyncLifecycleEvents.java` line 25-26) schedules the full snapshot; client-side, `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE` (`TotalityClient.java` lines 122-123) clears `ClientResourceSyncManager`. The inline comment at `TotalityClient.java` lines 111-121 explicitly documents the safety argument: the server's fresh full snapshot is "not sent until that server's next tick flush — strictly after this client-side level swap has already happened — so clearing here can never race ahead of and erase a full snapshot that arrives afterward." **[FACT]** No client-side equivalent clears any of the four legacy mirrors on dimension change — `ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager` are never cleared at all (§3.1-3.3); `PlayerChargesComponent`'s client mirror resets only via fresh `LocalPlayer`/component-attachment (§3.4), a different and independent trigger from `ClientResourceSyncManager.clear()`'s three explicit Fabric event registrations.
- **Disconnect**: `ServerPlayConnectionEvents.DISCONNECT` — `ResourceSyncLifecycleEvents.register()`'s listener calls `ResourceSyncManager.onDisconnect(...)` + `ResourceResyncRequestHandler.clearRateLimit(...)` (lines 28-31); client-side, `ClientPlayConnectionEvents.DISCONNECT` clears `ClientResourceSyncManager` (`TotalityClient.java` line 108-109). No legacy mirror is cleared client-side on disconnect either.

### 5.6 Fabric/Minecraft packet-ordering guarantee — confirmed scope and limits **[FACT + INFERENCE]**
- **[FACT]** No class in `api/rpg/resources/sync/` or `networking/resource/` declares `@Environment` — client/server intent is naming-convention-only (unchanged since the Phase 3B readiness audit's §14, re-confirmed: `ClientResourceSyncBridge`/`MinecraftNativeResourceAccess`/`TotalityClientResourceReaders` are the only three classes in the entire Phase 3B-1+3B-2-relevant surface that do carry `@Environment(EnvType.CLIENT)`).
- **[INFERENCE, not directly provable from this repo's source alone since Fabric API itself is a dependency, not part of this codebase]** A single Minecraft play connection is one ordered network stream; packets sent server-side in a given order are read and dispatched to their registered receivers client-side in that same relative order. This underlies every "legacy-before-generic" and "generic-before-legacy" ordering conclusion in §5.2-§5.5. This should be treated as a well-established platform assumption (consistent with vanilla Minecraft's networking model and with how every existing lifecycle-ordering comment already in this codebase, e.g. `TotalityClient.java` lines 111-121, reasons about ordering) rather than something this audit independently verified via a packet capture.

---

## 6. Recommended trigger architecture

**[RECOMMENDATION]**

### 6.1 Answering the task's explicit questions
- **Is generic-packet receipt alone sufficient?** No — §5.4 proves a real case (`FormulaResolver.tryCast`) where the *legacy* side updates (eventually) without ever being preceded by a generic packet at that exact moment; a trigger keyed only to generic packet receipt would never re-check that pair until the next unrelated generic Mana packet arrives (the next regen tick, up to 20 ticks later). **A parity comparison must be triggered by activity on *either* side, not generic receipt alone.**
- **Would comparing only after generic receipt miss a legacy-late update?** Yes, in the ordinary case (§5.2/§5.3, where generic reliably arrives at or after the same tick as legacy) this is a non-issue, but §5.4 shows the reverse can also happen — a trigger scheme must not assume generic is always the later/authoritative-timing side.
- **Should both generic and legacy update paths notify a parity coordinator?** Yes — see §15 for exactly which four legacy call sites need a notification hook and the least-invasive way to add it.
- **Should notification only mark a Resource dirty, with comparison deferred until `END_CLIENT_TICK`?** Yes — recommended design below.
- **Is one client tick enough for ordinary packet reordering?** Not quite — recommend **2 comparison triggers** (not raw ticks; see §7) given the `FormulaResolver` counter-example already shows a real gap can span up to the *next* regen-tick's generic-Mana packet, which itself could be up to 20 server ticks later, itself subject to further client-tick jitter on arrival. A short, fixed client-tick-count grace window cannot honestly bound that specific gap; see §7.3 for the resolution (mismatch persistence is measured in comparison-triggers-since-first-observed, not raw ticks, precisely because "how long until the next legacy sync happens to fire" is not a bounded quantity today).
- **Is a time-based grace window better than a trigger-count window?** **Trigger-count** (a "generation"/observation counter), not wall-clock/tick-count — see §7.1's reasoning.
- **How should repeated changes during the grace window be handled?** Each new value observation resets the grace counter for that specific resource only if the two sides still disagree; a match at any point clears the pending observation entirely (§7.4).
- **How should a full snapshot compare all four Resources efficiently?** A full snapshot (`APPLIED_FULL`) is the one moment the entire generic view is known-consistent; recommend re-comparing all four generic-sync-eligible resources against their legacy counterparts at that moment (bounded — always exactly 4 comparisons, never proportional to arbitrary state).
- **How should a delta compare only changed Resources?** `ResourceDeltaSyncPayload`'s `upsertScalars`/`upsertPartitioned`/`invalidated` already name exactly which resource ids changed (`ClientResourceSyncState.applyDelta`, `api/rpg/resources/sync/ClientResourceSyncState.java` lines 106-137) — recommend the coordinator re-compare only those ids after a successful `APPLIED_DELTA`, not all four unconditionally.

### 6.2 Recommended design
A single impure **`ClientResourceParityCoordinator`** (client-only, package `zcylas.totality.client.resource` alongside `TotalityClientResourceReaders`) that:
1. Is notified by (a) `ClientResourceSyncManager.applyFull`/`applyDelta` on a successful apply (naming exactly which ids changed, per §6.1's efficiency answer), and (b) each of the four legacy mirrors' own update path (§15) — marking a per-resource-id "dirty for parity" flag, nothing more, in both cases. No comparison logic runs inside either notification call.
2. Performs the actual comparison once per client tick, on `ClientTickEvents.END_CLIENT_TICK` (the same event `ClientResourceSyncManager.tick()` and `MobHealthBarHud.tick()` already use, `TotalityClient.java` lines 94-95, 132-133) — but **only for resource ids marked dirty since the last comparison pass**, never unconditionally re-comparing all four every tick. This directly answers "should notification only mark dirty, with comparison deferred to END_CLIENT_TICK" — yes.
3. This bounds the coordinator's steady-state cost to zero when nothing has changed (an empty dirty set, checked first) and to at most 4 comparisons on any tick where something changed — never a per-tick unconditional 4-comparison sweep, and never a comparison performed synchronously inside a packet-receive callback (keeping packet handling itself untouched in cost/behavior).

---

## 7. Grace-window decision

**[RECOMMENDATION]**

### 7.1 Trigger-count generations, not ticks or wall-clock time
Each resource id's parity state carries an **observation generation counter** — incremented each time that resource is actually re-compared (i.e., each time it was marked dirty by either side and a subsequent `END_CLIENT_TICK` comparison pass runs). This is deliberately **not** a raw tick count: §5.4 shows a real gap that cannot be bounded in ticks without either (a) a window so long it delays legitimate persistent-mismatch detection for every other, well-behaved resource, or (b) a window so short it misclassifies the `FormulaResolver` case as persistent immediately. A generation counter instead asks "how many times has this specific resource actually been re-observed since it first disagreed," which self-adapts to each resource's own real update cadence (Mana/Stamina update far more often than Rage or spell slots) rather than imposing one global tick constant.

### 7.2 Grace depth: 2 generations
Recommend a mismatch first observed at generation *N* escalates to `PERSISTENT_MISMATCH` only if it is still observed at generation *N+2* (i.e., two further independent re-observations, from either side's notification, still show disagreement) — not *N+1*, because a single generation only proves the two sides differed at one instant, which the entire timing analysis in §5 shows is routinely and harmlessly true for one packet-arrival cycle. Two consecutive still-disagreeing observations is a much stronger signal that the two sides have genuinely diverged rather than merely being mid-transition.

### 7.3 When TRANSITIONAL becomes PERSISTENT
- **Generation 0 (first observed mismatch)**: classified `TRANSITIONAL_MISMATCH`. No log line yet (see §16 — only a *transition into* `PERSISTENT_MISMATCH` is logged, to bound volume).
- **Generation 1**: if values now match, clear the pending observation entirely (§7.4). If still mismatched, remain `TRANSITIONAL_MISMATCH` (still within grace).
- **Generation 2**: if still mismatched, escalate to `PERSISTENT_MISMATCH` (one bounded DEBUG log line, per §16). If matched, clear.

### 7.4 Matching during the grace window clears the pending observation
**[RECOMMENDATION]** Yes, unconditionally — a match at any generation within the window resets that resource's parity tracking to "no pending observation" (not merely to generation 0 of a *new* window). This directly answers the task's explicit question and avoids a resource that oscillates match/mismatch/match/mismatch from ever silently accumulating toward a false persistent classification across unrelated transitions.

### 7.5 Does another update restart the grace window?
**[RECOMMENDATION]** Only if the *values actually changed* on the side that updated — a notification that fires but produces the same value (e.g., a legacy `sync()` call for an unrelated pool that happens to also touch Rage's dirty flag per `PlayerChargesComponent.sync()`'s documented "harmless over-notification," §3.4) must not reset an in-progress persistent-mismatch count back to generation 0, or a resource with frequent same-value notifications (Rage's over-broad dirty mark is the concrete example already in production) could never reach `PERSISTENT_MISMATCH` at all. The generation counter advances on every *comparison*, but the "first observed mismatch" baseline is set once and only cleared by a genuine match (§7.4), never reset by an unrelated same-value notification.

### 7.6 Reconnect / dimension-change reset
**[RECOMMENDATION]** Every per-resource parity observation (generation counter, first-observed/last-observed tick, classification) is cleared whenever `ClientResourceSyncManager.clear()` runs — i.e., piggyback on the exact same three call sites already wired (`TotalityClient.java` lines 106-123: JOIN, DISCONNECT, `AFTER_CLIENT_LEVEL_CHANGE`), since a fresh connection/world means both sides' prior state is meaningless for comparison purposes regardless of whether the legacy mirrors themselves were also reset (§3.4's `PlayerChargesComponent` client instance may or may not have been replaced by the same instant — the parity tracker doesn't need to know or care, since clearing its own observations just means "start comparing fresh, from whatever both sides currently show").

### 7.7 Pending resync's effect on escalation
**[RECOMMENDATION]** While `ClientResourceTrust.PENDING_RESYNC` is reported for a resource (i.e., `ClientResourceSyncBridge.isResyncPending()` is true), that resource's parity generation counter should **not advance towards `PERSISTENT_MISMATCH`** — a pending resync is already a known, self-correcting condition (bounded retry every 150 ticks, `ClientResyncRequestGate.DEFAULT_RETRY_INTERVAL_TICKS`) and escalating a mismatch to "persistent" while the generic side has already flagged itself as stale would be double-reporting the same underlying cause. Recommend freezing (not clearing) the observation at whatever generation it was at when `PENDING_RESYNC` began, resuming normal generation advancement once trust returns to `FRESH`.

### 7.8 Avoiding a permanent mismatch hiding forever under continuous updates
**[RECOMMENDATION]** Because escalation only requires 2 consecutive still-mismatched *comparisons* (not 2 consecutive ticks with no further activity), a resource that is dirty-marked every single tick (the theoretical worst case) still reaches `PERSISTENT_MISMATCH` within at most 2 comparison passes of continuous disagreement — the design in §7.1-7.2 does not require a quiet period to detect persistence, only continued disagreement across two observations. This directly satisfies the task's explicit "must not hide a permanent mismatch forever under continuous updates" requirement.

---

## 8. Parity classification model

**[RECOMMENDATION]**

After evaluating the task's suggested vocabulary against what this audit actually found in the current source, the following **eight** states are recommended — narrower than the task's suggested eleven, with reasoning for each merge/drop:

```
EXACT_MATCH                  — values equal (after unit-scale normalization) at comparison time
TRANSITIONAL_MISMATCH        — values differ; within the 2-generation grace window (§7)
PERSISTENT_MISMATCH          — values still differ after the grace window has elapsed (§7.3)
EXPECTED_SEMANTIC_DIFFERENCE — a known, source-documented, intentional asymmetry (§11) — never escalates
GENERIC_NOT_READY            — generic side is Unavailable(NOT_SYNCHRONIZED_YET) — cannot compare, not a mismatch
MODEL_MISMATCH               — the generic side itself reports Unavailable(MODEL_MISMATCH) or
                                MALFORMED_SOURCE_STATE, or the two sides' shapes structurally cannot be compared
                                (e.g. a partitioned generic result paired with a scalar legacy reader)
RAGE_ABSENCE_AMBIGUOUS       — the one resource-specific case requiring its own state (§9) — legacy 0/0 vs
                                generic Unavailable/present-0/0, disambiguated only when class-aware (§9)
NOT_APPLICABLE               — Health/Food/Breath — no legacy/generic pair exists to compare at all (§4.2)
```

Deliberate deviations from the task's suggested list, with reasoning:
- **`LEGACY_NOT_READY` — dropped, merged into `EXPECTED_SEMANTIC_DIFFERENCE`.** No legacy mirror in this codebase has a distinguishable "not ready yet" state separate from its permanent default (`ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager` default to `100/100` or all-zero indistinguishably from a real synced value, §3.1-3.3) — there is no boolean or sentinel to observe "legacy not ready" as a distinct condition at all. The one real asymmetry that looks like this (Stamina's join-time push vs Mana's lack of one, §3.2) is better modeled as a known, permanent, intentional design difference (`EXPECTED_SEMANTIC_DIFFERENCE`) than a transient "not ready" state, since it is not actually resolved by waiting — Mana genuinely has no join push, ever.
- **`SOURCE_UNAVAILABLE` — renamed/split into `GENERIC_NOT_READY` and `NOT_APPLICABLE`.** The task's single `SOURCE_UNAVAILABLE` conflates two structurally different situations this audit confirms are distinct: "the generic side hasn't synced yet" (a real, temporary, per-connection condition — `GENERIC_NOT_READY`) versus "there is no generic side to ever compare for this resource" (Health/Food/Breath, permanent and structural — `NOT_APPLICABLE`). Collapsing them would make a permanent, correct-by-design state indistinguishable from a transient one worth escalating if it never resolves.
- **`UNIT_SCALE_MISMATCH` — dropped, folded into `MODEL_MISMATCH`.** Confirmed by direct reading of `GenericSyncClientResourceReader.query` (lines 55-59, 71-72): a `wire.unitScale() != definition.unitScale()` disagreement already produces `Unavailable(MODEL_MISMATCH)` at the façade layer itself, before the parity engine ever sees a numeric value to compare — there is no code path where the parity engine could observe two same-shaped numeric values that actually differ by scale, since the façade has already rejected that case upstream. A dedicated parity-level classification for a condition the façade already prevents from reaching parity comparison at all would be untestable dead code.
- **`PENDING_GENERIC_RESYNC` — dropped as a top-level classification, folded into the trust-aware generation-freeze behavior (§7.7) instead.** `PENDING_RESYNC` is a *trust* qualifier on an otherwise-valid value (§2.2's `ClientResourceTrust`, confirmed the value itself is never blanked during a pending resync, `GenericSyncClientResourceReader` lines 38 + Phase 3B-1 report §11's `pendingResyncPreservesLastValueAndReportsPendingResync` test) — the value underneath is still comparable and may well already `EXACT_MATCH`. Making `PENDING_RESYNC` its own top-level classification would hide a real, currently-matching or currently-mismatching value behind a state that says nothing about whether they actually agree; §7.7's freeze (comparison still happens and is still classified normally, only escalation is paused) preserves more information for the same purpose.
- **`RAGE_ABSENCE_AMBIGUOUS` — added, not in the task's suggested list.** The task's own §"RAGE ABSENCE DECISION" section explicitly asks for a dedicated resolution; this audit's §9 concludes the ambiguity is real and irreducible without class-awareness, so it earns its own classification rather than being force-fit into `EXPECTED_SEMANTIC_DIFFERENCE` (which would suppress it from ever being escalated even for a genuine Barbarian, §9.3) or `MODEL_MISMATCH` (which is about shape, not absence semantics).

### 8.1 Preventing specific misclassifications (the task's explicit "must prevent" list)
- **An absent generic Resource treated as numeric zero**: structurally impossible — `ClientResourceQueryResult.Unavailable` has no numeric fields (§2.2); the parity comparator's input type for the generic side must itself be `ClientResourceQueryResult`, never a pre-unwrapped `long`, forcing an explicit branch at the comparator's own entry point.
- **`NOT_SYNCHRONIZED_YET` called a mismatch**: mapped to `GENERIC_NOT_READY`, which is not a member of the mismatch family (`TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH`) and never escalates.
- **`PENDING_RESYNC` immediately escalating**: handled by §7.7's generation-freeze.
- **A model contradiction treated as an ordinary value mismatch**: `MODEL_MISMATCH` is its own classification, checked before any numeric comparison is attempted.
- **Native Resources reported as failed parity**: `NOT_APPLICABLE`, and per §4.2 the engine structurally never even attempts a comparison for these three ids.
- **Rage absence semantics flattened**: `RAGE_ABSENCE_AMBIGUOUS`, §9.

### 8.2 Recommended parity result fields
**[RECOMMENDATION]** An immutable `ClientResourceParityObservation` record per resource id:
```java
record ClientResourceParityObservation(
    Identifier resourceId,
    ClientResourceParityClassification classification,
    String genericValueSummary,   // bounded, e.g. "42/100" or "unavailable:NOT_SYNCHRONIZED_YET" — never a raw dump
    String legacyValueSummary,    // bounded, e.g. "40/100"
    int firstObservedGeneration,
    int lastObservedGeneration,
    int observationCount
) {}
```
Deliberately **no timestamp/tick number** (generation-based, per §7.1) and no raw `ClientResourceQueryResult`/legacy object reference held long-term (bounded string summaries only, decided at observation time) — keeps the record's memory footprint fixed and prevents a diagnostic structure from accidentally holding a live reference to mutable legacy state.

---

## 9. Rage absence decision

**[RECOMMENDATION]**

### 9.1 Evaluating the four options
- **Option A (unconditional `EXPECTED_SEMANTIC_DIFFERENCE`)**: Simple, but per the Phase 3B readiness audit's own §10.4 open question, this would suppress a genuine gap for an actual granted Barbarian (generic `Unavailable` while legacy shows `0/0` for a real player who should have a value) — the exact scenario shadow parity exists to catch.
- **Option B (use existing client class data to determine Barbarian-ness)**: **[FACT]** This is already architecturally trivial and already precedented in production code — `TotalityClient.java` lines 181-183 already reads `ClientClassManager.hasClass() && TotalityClasses.BARBARIAN_ID.equals(ClientClassManager.getPrimaryClassId())` to decide whether to show the Rage HUD pip bar at all. A parity coordinator can call the exact same two static methods with no new accessor, no new component, and no new client-side state.
- **Option C (inspect actual pool-presence state via a new `hasPool(...)` accessor)**: Would require a new read-only method on `PlayerChargesComponent` (e.g. `hasPool(Identifier)` returning whether the sparse map actually contains the key, distinct from `getCurrent`/`getMax`'s `0`-on-absence fallback). This is a real, safe, read-only addition (mirrors `getAllPools().containsKey(id)`, already exposed indirectly via the existing `getAllPools()` unmodifiable-map accessor used by `RageResourceAdapter` itself, §also confirmed no new mutation surface would be created) — but it is **strictly more precise than Option B needs to be** for this specific ambiguity: Option B already fully resolves "is this player capable of having Rage at all," and the *only* remaining ambiguity Option C would additionally resolve is "does a non-Barbarian's map somehow contain a stray Rage entry anyway" — a case this audit found no evidence can currently occur (nothing calls `registerPool`/`ensurePool` for `barbarian_rage` except Barbarian-class-gated code, confirmed by the `CHARGE_ID` grep in §3.4/`RageResourceAdapter`'s own Javadoc). Option C is not wrong, but it is added surface area for a distinction that doesn't currently manifest.
- **Option D (`LEGACY_AMBIGUOUS` unconditionally)**: Discards exactly the information Option B recovers for free; strictly worse than B with no offsetting simplicity benefit (it's the same amount of code to *not* check class as to check it, since the classification enum needs a value either way).

### 9.2 Recommendation: Option B
Use `ClientClassManager.hasClass()`/`getPrimaryClassId()` (already-existing, already-precedented client-side class state — no new accessor, no new component, no new mutation surface, no gameplay-authoritative dependency introduced) to disambiguate:
- **Non-Barbarian** (or no class at all) + generic `Unavailable(NOT_AVAILABLE_TO_PLAYER)` + legacy `0/0`: classify `EXPECTED_SEMANTIC_DIFFERENCE` — this is the sparse-map's designed "never granted" fallback exactly matching a genuinely-absent generic Resource; not a bug, never escalates.
- **Barbarian** + generic `Unavailable(NOT_AVAILABLE_TO_PLAYER)` + legacy `0/0` (or any legacy value): classify as an ordinary mismatch (`TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH` per the normal grace-window rules) — a genuinely granted Barbarian's Rage pool should always have a corresponding generic value; a persistent absence here is a real gap worth surfacing.
- **Barbarian** + generic `Scalar` present + legacy present: ordinary numeric comparison, `RAGE_ABSENCE_AMBIGUOUS` does not apply at all — this is a plain `EXACT_MATCH`/mismatch case like any other scalar.
- The only case that still needs `RAGE_ABSENCE_AMBIGUOUS` rather than a clean resolution: a **Barbarian** whose generic side is `Unavailable(NOT_AVAILABLE_TO_PLAYER)` while legacy reads a **non-zero** current/max — this combination cannot occur under this codebase's current mechanics (a Barbarian's Rage pool, once `registerPool`/`ensurePool`'d, is never removed from either side's map — confirmed no `pools.remove(...)` call exists anywhere in `PlayerChargesComponent.java`), so `RAGE_ABSENCE_AMBIGUOUS` is retained in the classification vocabulary (§8) for defensive completeness/future-proofing but is not expected to be reachable given current production mechanics — worth a dedicated test asserting it structurally cannot occur today (§10, test #21).

### 9.3 Constraints satisfied
- **Not gameplay-authoritative**: the coordinator only *reads* `ClientClassManager`'s already-synced state for a diagnostic classification decision; it makes no gameplay decision and nothing it computes feeds back into any gameplay system.
- **No hardcoded class behavior beyond what already exists**: reuses the exact same `TotalityClasses.BARBARIAN_ID` check already hardcoded in `TotalityClient.java` for the identical purpose (HUD visibility) — no new hardcoding introduced, only a second read site for an existing check.
- **No fabricated Rage presence**: the classification never fabricates a value; it only changes which *label* an already-observed absence/value pair receives.
- **No change to Rage synchronization authority**: purely a read-only diagnostic decision, zero writes to `PlayerChargesComponent` or the Resource API.
- **Does not begin Phase 3C**: no consumer is migrated; this is diagnostic classification only.

### 9.4 Is a new `hasPool(...)` accessor architecturally justified?
**[OPEN QUESTION, leaning no for Phase 3B-2]** Not for the Option B design above — Option B's class-check already fully resolves the ambiguity this audit found actually matters. A `hasPool(Identifier)` accessor would be a reasonable, safe, small addition (Option C) if a future need arises to detect the "stray entry despite non-Barbarian" case this audit found no current evidence can occur — recommend deferring it unless Phase 3B-3's manual validation actually surfaces that case in practice.

---

## 10. Spell-slot parity — exact semantics

**[FACT, resolved definitively — see §4.1 for the full derivation]**

- Generic partition entry: `partitionId` (1-10), `currentUnits` (= remaining slots, **not** used), `maximumUnits` (= max slots), `overflowUnits` (always `0` today — no `OVERFLOW` capability declared for `totality:spell_slots`).
- Legacy `ClientSpellSlotManager`: `getMax(level)` (direct), `getRemaining(level)` (`maxSlots[i]-usedSlots[i]`, computed on read, matches generic `currentUnits` exactly).
- **Exact correct comparison formula**: `partition.currentUnits() == ClientSpellSlotManager.getRemaining(level)` AND `partition.maximumUnits() == ClientSpellSlotManager.getMax(level)`, for `level` in `1..10`.

### 10.1 Edge-case handling
- **Missing partition** (a level absent from the generic `Partitioned.partitions()` map): cannot occur for a successfully-synced `totality:spell_slots` result — `StandardSpellSlotsResourceAdapter.resolve` always populates all 10 levels unconditionally (§4.1), and `ResourcePartitionedWireSnapshot`'s constructor rejects duplicate partition ids but does not require all 10 to be present in principle; recommend the comparator treat a genuinely missing level defensively as `MODEL_MISMATCH` (a malformed wire snapshot, since production code never produces one) rather than silently defaulting to `0`.
- **Extra partition** (a level beyond 1-10, or a duplicate): structurally rejected upstream — `ClientResourceQueryResult.Partitioned`'s compact constructor already builds a `TreeMap` keyed by `partitionId`, so a genuine duplicate cannot exist in the object the parity engine receives; an out-of-1-10-range level would have no legacy counterpart to compare against at all (`ClientSpellSlotManager.getMax`/`getRemaining` both bounds-check and return `0` for `i<0||i>=MAX_SPELL_LEVEL`, `ClientSpellSlotManager.java` lines 18,23) — recommend the comparator only iterate levels 1-10 and treat any partition key outside that range as `MODEL_MISMATCH` (a genuinely unexpected wire shape).
- **Duplicate partitions**: structurally impossible once the value has reached a `Map`-backed `Partitioned` result (§2.2's compact constructor already deduplicates/validates).
- **All-zero state**: a valid `EXACT_MATCH` if both sides agree at `0/0` for every level (the non-caster case, confirmed identical convention on both sides, §3.3/§4.1).
- **Future nonzero overflow**: not produced by any current adapter; if `overflowUnits` ever becomes nonzero for a future partition capability, the legacy `ClientSpellSlotManager` has no overflow concept at all — recommend classifying that specific combination `MODEL_MISMATCH` (the legacy side structurally cannot represent what the generic side is reporting) rather than silently ignoring the overflow component.
- **Maximum mismatch / current-remaining mismatch**: ordinary `TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH` per §7's normal rules — no special-casing needed beyond using the correct `currentUnits↔remaining` mapping (§4.1), which is the actual, only real risk this section exists to close off (a naive implementation comparing `currentUnits` against `usedSlots[i]` directly would misclassify every single non-zero-usage state as a permanent mismatch).
- **Malformed legacy arrays**: `ClientSpellSlotManager`'s arrays are fixed-size (`SpellSlotComponent.MAX_SPELL_LEVEL=10`) and bounds-checked at every accessor (`getMax`/`getRemaining` both return `0` for an out-of-range index) — there is no legacy-side malformed-array state reachable through its own public API for the comparator to defend against.
- **Positional array equality is not sufficient — confirmed correct concern**: although today `ClientSpellSlotManager`'s array index (`spellLevel-1`) and the wire's `partition` field (`spellLevel` directly) are consistently related, recommend the comparator explicitly loop `for (level in 1..10) { compare(partition(level), getMax(level), getRemaining(level)) }` — key-based, not raw positional array comparison — so a future partition reordering or a sparse wire snapshot cannot silently misalign the comparison.

---

## 11. Mana and Stamina parity — exact numeric and initialization semantics

**[FACT]**

- **Identical raw units confirmed**: both Mana and Stamina are `unitScale=1` on both the generic wire side (`ProductionResourceDefinitions.java` lines 172-200, `ResourceDisplayConversion.IDENTITY`) and the legacy side (`ClientManaManager`/`ClientStaminaManager` store plain `int`s with no scaling) — no conversion is ever needed for either resource; a direct `==` comparison after confirming both sides answered is correct.
- **Clamping**: `PlayerManaManager.setMana`/`PlayerStaminaManager.setStamina` both clamp server-side via `Math.clamp(amount, 0, max)` (`PlayerManaManager.java` line 33, `PlayerStaminaManager.java` line 49) before either the legacy or generic paths ever observe the value — both mirrors always receive an already-clamped value; the parity engine does not need its own clamping logic, only equality comparison.
- **Legacy default before first synchronization**: both `ClientManaManager`/`ClientStaminaManager` default to `100/100` (literal, indistinguishable from a real synced `100/100`, confirmed §3.1/§3.2) — this is the exact "unsynchronized default read as authoritative zero-or-otherwise" risk class the whole façade design (§2.2, §9.2 of the Phase 3B readiness audit) exists to prevent for the *generic* side, but the *legacy* side has no equivalent protection at all. **[RECOMMENDATION]** the parity engine must never compare against a legacy value while the generic side itself reports `GENERIC_NOT_READY` (§8) — comparing a default-100 legacy value against "no generic value yet" would either wrongly classify a `100` maximum coincidence as `EXACT_MATCH` or wrongly classify a real difference as a mismatch, when in fact neither side has enough information yet to say anything meaningful. Skip the comparison entirely (no observation recorded) while `GENERIC_NOT_READY` holds, exactly mirroring how the façade itself refuses to fabricate a value during this window.
- **Stamina's join-time push avoids this transitional window; Mana does not**: confirmed, §3.2 — `StaminaServerTick.syncStamina(player)` is called explicitly at `ServerPlayConnectionEvents.JOIN` (`PlayerConnectionEvents.java` line 65), so a freshly-joined client's `ClientStaminaManager` reflects the real value almost immediately (bounded by normal network latency, not by waiting for the first regen tick or spend action). Mana has no equivalent join push — `ClientManaManager` genuinely can sit at the stale default `100/100` until the first Mana-affecting server action explicitly calls `ManaServerTick.syncMana`/constructs `SyncManaPayload` inline. **This is a real, permanent, intentional-by-omission asymmetry — recommend classifying it `EXPECTED_SEMANTIC_DIFFERENCE`**, not something Phase 3B-2 should "fix" (out of scope; the task explicitly forbids modifying production code) but something its classification model must not mistake for a bug on every fresh join.
- **Maximum-only changes reach both paths at the same time**: `PlayerResourceRecalculator.recalculate`/`recalculateAndRestore` (`api/rpg/stats/PlayerResourceRecalculator.java` lines 39-112) explicitly send both the legacy packet (inline `SyncManaPayload` construction, `StaminaServerTick.syncStamina`) and mark the generic dirty flags **unconditionally**, regardless of whether either clamp actually fired — confirmed by the inline comment at lines 71-77 explaining exactly this "a stat/equipment change can raise or lower the maximum without necessarily changing current" reasoning. Both paths' sends happen within the same synchronous call to `recalculate`/`recalculateAndRestore` — the legacy packet immediately, the generic notification deferred to end-of-tick flush as usual (§5.3's general pattern).
- **Regeneration timing**: covered fully in §5.2 — legacy-first-within-the-same-tick is the confirmed, structurally-guaranteed ordering for the regen cycle specifically (unlike the `FormulaResolver` spend-path counter-example of §5.4).
- **`TotalityMovementHandler`'s prediction/gating reads must be excluded from parity authority conclusions**: **[FACT, re-confirmed]** `TotalityMovementHandler.java` (lines 76, 123, 155 per the Phase 3B readiness audit's consumer table, unchanged in this audit) reads `ClientStaminaManager.getStamina()` directly for client-side ability-availability *gating*, not display — this is a gameplay-prediction read the server independently re-validates, never a UI consumer the parity engine should treat as evidence of "the legacy value the player sees." **[RECOMMENDATION]** the parity engine's consumer-facing documentation/comments should explicitly note this class of read is out of scope for "does the UI agree" purposes even though it technically reads the same static field the HUD does — conflating the two would risk someone later "fixing" `TotalityMovementHandler` to read the façade instead, which the Phase 3B-1 report already flags as something that "must never" happen (§20 of that report).

---

## 12. Exact consumer inventory (re-confirmed unchanged since Phase 3B-1)

**[FACT]** Re-verified in this audit: no production consumer reads `ClientResourceService` anywhere in `src/main`. The table below is unchanged from the Phase 3B readiness audit's §15 (spot-checked, not fully re-derived line-by-line, since Phase 3B-1/its correction touched none of these files per their own §20/consumer-preservation confirmations).

| # | Consumer | Resource(s) | Current source |
|---|---|---|---|
| 1 | `TotalityHudRenderer` HUD bar lambda | Health, Food, Mana, Stamina | native (Health/Food) + `ClientManaManager`/`ClientStaminaManager` |
| 2 | `TotalityHudRenderer.resourceDisplayCurrentMax(...)` | Health, Food | `PlayerResourceService.query` (native) — unaffected by Phase 3B-2 |
| 3 | `MobHealthBarHud` | Health (mob + comparison) | native — out of Resource API scope entirely |
| 4 | `TotalityClient`'s `ISecondaryResource` HUD registration | Rage | `PlayerChargesComponent` client instance, direct |
| 5 | `SpellRadialScreen` | Standard Spell Slots | `ClientSpellSlotManager` |
| 6 | `OverviewTab` | Health, Stamina, Mana | native (Health) + legacy managers (Stamina/Mana) |
| 7 | `ClassTab` | Rage | `PlayerChargesComponent` client instance, direct |
| 8 | `TotalityMovementHandler` | Stamina | `ClientStaminaManager.getStamina()` — **gameplay-authoritative prediction/gating, never a display consumer; excluded from parity-observable UI classification per §11** |

No consumer in this table is modified by this audit, and Phase 3B-2 must not modify any of them either (per the task's explicit "no consumer migration" constraint).

---

## 13. Testability / pure-impure class boundaries

**[RECOMMENDATION]**

### 13.1 Pure, plain-Java (no Minecraft/Fabric runtime)
Following the exact precedent Phase 3A/3B-1 already established (`ClientResourceSyncState`, `ClientResyncRequestGate`, `ClientResourceQueryResult`, `ClientResourceSyncRejectionDiagnostics` — none import Minecraft/Fabric):
- `ClientResourceParityClassification` (enum, §8).
- `ClientResourceParityObservation` (record, §8.2).
- `ClientResourceParityResult` (if a wrapper beyond the observation record is needed — likely unnecessary; the observation record itself is sufficient).
- `ClientResourceParityComparator` — one pure function per model: a scalar comparator (`ClientResourceQueryResult.Scalar` + legacy `(current,max)` pair → classification) and a partitioned comparator (`ClientResourceQueryResult.Partitioned` + legacy per-level `(max,remaining)` lookups → per-level classifications). Both take plain values in, return a classification out — no Minecraft dependency, directly unit-testable exactly like `ClientResourceSyncStateTest`.
- `ClientResourceParityTracker` — the pure generation/grace-window state machine (§7) — a per-resource-id map of "current pending observation, if any," advanced by explicit method calls (`observe(resourceId, classification, generation)`), never by a tick callback itself (the tick callback lives in the impure coordinator, §13.2).
- Recommended package: `zcylas.totality.api.rpg.resources.client` (alongside the existing pure façade types) for the classification/observation/comparator types, since they have zero Minecraft dependency and belong with `ClientResourceQueryResult` conceptually; the tracker likewise, mirroring how `ClientResourceSyncState` (pure) sits in `api.rpg.resources.sync` alongside the impure `ClientResourceSyncManager` in `networking.resource`.

### 13.2 Impure client glue — requires `@Environment(EnvType.CLIENT)`
- `ClientResourceParityCoordinator` (§6.2) — touches `ClientTickEvents.END_CLIENT_TICK`, `ClientClassManager` (§9.2), and the four legacy manager/component classes directly. Recommended package: `zcylas.totality.client.resource`, alongside `TotalityClientResourceReaders`/`MinecraftNativeResourceAccess` — the existing impure-client-glue package Phase 3B-1 already established.
- The four **legacy readers** (§15) — thin wrappers reading `ClientManaManager.getMana()/getMaxMana()`, `ClientStaminaManager.getStamina()/getMaxStamina()`, `ClientSpellSlotManager.getMax(level)/getRemaining(level)` for levels 1-10, and `PlayerChargesComponent` (client instance) `getCurrent`/`getMax(BarbarianRageAbility.CHARGE_ID)` — each behind a small interface (mirroring `NativeResourceAccess`/`GenericSyncResourceAccess`'s existing pattern) so the comparator functions themselves never import a legacy manager class directly, keeping the comparators pure/testable with synthetic legacy-value inputs.
- Lifecycle registration (§7.6's reset wiring) — one additional line in `TotalityClient.onInitializeClient()` alongside the three existing `ClientResourceSyncManager.clear()` registrations, calling the coordinator's own reset method.

### 13.3 Avoiding common/server classloading
**[FACT, confirmed unchanged]** `ModEvents.register()`/`ResourceSyncLifecycleEvents`/`ResourceSyncManager`/server-side registration code has zero references to any client-only class today (§14.2 of the Phase 3B readiness audit, re-confirmed: no new server-side reference was introduced by Phase 3B-1, and this audit's design introduces none either — the parity coordinator and its legacy readers are referenced only from `TotalityClient.onInitializeClient()`, the same single entry point every other Phase 3B-1 client-only class already uses). **[RECOMMENDATION]** every new class in §13.2 should carry `@Environment(EnvType.CLIENT)`, continuing the precedent `ClientResourceSyncBridge`/`MinecraftNativeResourceAccess`/`TotalityClientResourceReaders` already set (rather than relying on naming convention alone, per the Phase 3B readiness audit's §14.1/§17.5 hardening recommendation, still not acted on as of this audit — Phase 3B-2 is a reasonable moment to start applying it to *new* classes even if not retrofitting it onto the untouched Phase 3A classes).

### 13.4 How the coordinator reads the façade without gaining mutation access
The coordinator reads `ClientResourceService.INSTANCE.queryScalar(...)`/`queryPartitioned(...)` — the same public, read-only entry points any future HUD/menu consumer would use (§2.1) — never `ClientResourceSyncBridge`/`ClientResourceSyncManager` directly. This keeps the parity coordinator architecturally identical to "just another future façade consumer" rather than a privileged internal component, and means Phase 3B-2 exercises the façade's actual public contract end-to-end (a useful side benefit: parity testing is also, incidentally, façade-consumer integration testing).

---

## 14. Lifecycle reset strategy

**[RECOMMENDATION, synthesizing §5.5/§7.6]**

| Scenario | Generic façade state | Legacy mirror state | Parity tracker action |
|---|---|---|---|
| Client startup, no world | `NOT_SYNCHRONIZED_YET` (no player) | Static defaults (100/100 Mana/Stamina, all-zero slots, empty Rage map) | No observations exist yet; nothing to reset |
| Join | `ClientResourceSyncManager.clear()` on JOIN, then first full snapshot | Stamina pushed at JOIN; Mana/slots/Rage wait for first action or fresh component attachment | Clear all parity observations on the same JOIN event (§7.6) |
| Reconnect | Same as join (fresh JOIN) | Same as join | Same as join |
| Dimension change | `clear()` via `AFTER_CLIENT_LEVEL_CHANGE` | `PlayerChargesComponent` client instance replaced (fresh component attachment); Mana/Stamina/slots managers **not** reset (static fields survive) | Clear all parity observations on the same event — critical, since otherwise a pre-dimension-change observation would be compared against a post-change generic view that has no causal relationship to it |
| Death/respawn | No explicit client-side clear (Phase 3B-1's deliberate decision, §13 of that report) — relies on the next full snapshot's atomic replacement | `PlayerChargesComponent` client instance likely replaced (new `LocalPlayer`); Mana/Stamina/slots managers not reset | **[OPEN QUESTION]** Should the parity tracker clear on respawn even though the generic façade itself does not pre-clear? Recommend **yes** — even though the façade's own value is never blanked, the *legacy* side's `PlayerChargesComponent` instance may already have been silently replaced by respawn's fresh-`LocalPlayer`/component-attachment mechanism (a materially different reset trigger than the façade's, §3.4) — comparing a stale pre-respawn generic observation's generation count against a legacy value that already reset independently risks a spurious escalation. Recommend wiring the parity tracker's reset to `ClientPlayerEvents`-equivalent respawn detection if one already exists client-side, or — more simply and consistently with the rest of this section — just to the same three `ClientResourceSyncManager.clear()` call sites plus one additional call at whatever client-side respawn signal already exists for other Totality systems (needs identifying during Phase 3B-2 implementation, not resolved by this audit — see §21). |
| Integrated-server restart / switching servers | DISCONNECT then JOIN — both already covered | Same | Covered by the JOIN/DISCONNECT reset above |
| Connection replacement | DISCONNECT/JOIN pair | Same | Covered |
| Generic revision gap / resync pending | `PENDING_RESYNC` trust, value not blanked | Unaffected | Generation freeze, not clear (§7.7) |
| Accepted replacement full snapshot | `APPLIED_FULL`, all 4 generic-sync-eligible resources re-queryable | Unaffected by this alone | Full re-comparison pass, not a reset (§6.1) — existing observations continue their generation count rather than restarting, since a resync landing successfully is not evidence the *legacy* side's cache is now wrong or right, only that the generic side is now known-fresh |
| Malformed/incompatible payload | `applyFull`/`applyDelta` returns `MALFORMED`/`INCOMPATIBLE_SCHEMA`, no state change (already logged, §2.5) | Unaffected | No parity action — nothing changed on the generic side to re-compare |
| Local-player replacement | Implicit in JOIN/dimension-change/respawn above | — | Covered by the above rows |

**[RECOMMENDATION]** Legacy-cache lifecycle metadata: rather than adding any new clearing behavior to the legacy caches themselves (explicitly out of scope — "do not change legacy cache behavior during this audit," and Phase 3B-2 must not either), the parity tracker's own reset (§7.6) is sufficient and self-contained — it never needs the legacy caches to announce their own reset, since the tracker's reset already discards its *prior comparison state* regardless of whether the legacy value underneath also changed at that exact instant. The tracker asks "do the two sides agree *right now*," starting fresh each time it's told to — it does not need to know *why* either side's value is what it is.

---

## 15. Legacy update-notification strategy

**[RECOMMENDATION]**

### 15.1 Evaluated options
- **Direct calls added to `ClientManaManager.sync(...)`/`ClientStaminaManager.sync(...)`/`ClientSpellSlotManager.apply(...)`/`PlayerChargesComponent.applySyncPacket(...)`**: technically simplest, but couples four small, currently dependency-free legacy classes directly to a new parity-specific class — a real, if modest, coupling cost the task explicitly asks to weigh. `ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager` currently have **zero** imports beyond their own package/`SpellSlotComponent`; adding a parity-coordinator import to each is a small but real new dependency edge into otherwise-minimal classes.
- **Callback/listener registration**: each legacy class would need a new listener-registration mechanism it doesn't have today — more new surface area than the problem needs, and risks becoming a second general-purpose event system alongside Fabric's own.
- **Static coordinator notification (coordinator exposes a static `notifyManaChanged()` etc., called from the four `sync`/`apply` methods)**: functionally identical to "direct calls," just inverted call direction — same coupling cost.
- **Client tick polling with dirty generations**: the coordinator itself polls all four legacy managers' current values every `END_CLIENT_TICK` and diffs against its own last-seen cache to detect a change, needing **no** modification to any of the four legacy classes at all.
- **Wrapper readers plus scheduled compare**: a variant of the above — thin read-only wrapper interfaces (§13.2) already needed regardless of notification strategy; "scheduled compare" is just tick-polling by another name.
- **Notification from packet handlers instead of managers**: add the notification call in `TotalityClientPacketHandlers.register()`'s existing receiver lambdas (`SyncManaPayload`/`SyncStaminaPayload`/`ComponentSync.PACKET_TYPE`) rather than inside the legacy manager classes themselves.
- **Notification from `TotalityClientSyncListeners`** (a name mentioned in the task but not found as a distinct file in this codebase — confirmed by search: no file named `TotalityClientSyncListeners.java` exists with per-resource legacy hooks beyond what `TotalityClientPacketHandlers` and `ClientComponentSyncListeners` already provide; `TotalityClientSyncListeners.register()` is called from `TotalityClient.java` line 85 but its own content was not part of this audit's required file list — worth confirming during implementation, not assumed here).

### 15.2 Recommendation: client tick polling with dirty generations — no modification to any legacy class
**[RECOMMENDATION]** Given that (a) all four legacy managers/components are trivially cheap to read (bare static fields or a small map lookup, §3.1-3.4), (b) the coordinator already runs on `END_CLIENT_TICK` regardless (§6.2), and (c) this avoids touching `ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager`/`PlayerChargesComponent` at all — the four classes the task's own constraints are most cautious about coupling — recommend the coordinator poll all four legacy values once per `END_CLIENT_TICK` pass (four cheap reads) and diff against its own last-seen legacy-side cache to decide whether *that side* considers a resource dirty for parity purposes, exactly mirroring how it already learns of generic-side changes via `applyFull`/`applyDelta` notification (§6.2). This is the **least invasive** option: zero lines changed in any of the four legacy classes, zero lines changed in `TotalityClientPacketHandlers`, and the coordinator's own polling cost is bounded and already within the existing `END_CLIENT_TICK` budget shared with `ClientResourceSyncManager.tick()`/`MobHealthBarHud.tick()`/`FluidTankScrollHandler.tick()` (`TotalityClient.java` lines 94-95, 132-133).
- This design point directly satisfies the task's constraint list: it does not alter the value being applied (pure read), cannot delay a legacy update (the update already happened by the time `END_CLIENT_TICK` polls it), does not swallow exceptions (a defensive `try/catch` around the Rage read, mirroring `TotalityClient.java`'s own existing `ISecondaryResource` pattern at lines 168,174, is the only exception-handling needed and is not "swallowing" a real error, just tolerating the same `null client.player` case the existing HUD code already tolerates), introduces no server-side reference to client-only parity code (the coordinator is entirely client-package-local), and makes no diagnostics required for legacy functionality (the four legacy classes remain completely unaware the coordinator exists).

---

## 16. Logging and diagnostic strategy

**[RECOMMENDATION, following the Phase 3B-1 precedent exactly, §2.5]**

- **Transitional mismatch**: no log at all — this is the expected, ordinary case per §5's timing analysis and must not spam logs at the volume ordinary gameplay would produce.
- **Persistent mismatch**: one `Totality.LOGGER.debug(...)` line, logged only on the **transition into** `PERSISTENT_MISMATCH` (generation crossing the §7.2 threshold), never repeated every subsequent tick/comparison while it remains mismatched — directly mirroring `ClientResourceSyncManager`'s existing "log only on transition" convention for `MALFORMED`/`INCOMPATIBLE_SCHEMA` (§2.5).
- **Exact-match recovery**: recommend also logging a transition **back** to `EXACT_MATCH` from a previously `PERSISTENT_MISMATCH` state, at DEBUG — confirms resolution without requiring a developer to infer it from log absence, at negligible extra volume (this can only fire once per persistent-mismatch episode, by definition).
- **Repeated identical mismatches are coalesced** by construction: only a *transition* is logged (into `PERSISTENT_MISMATCH`, or back out of it), never a steady-state re-log — no separate coalescing/deduplication mechanism is needed beyond "log on transition only," which the tracker's own state (§8.2's `lastObservedGeneration`/classification) already makes trivial to detect (compare the new classification against the previously stored one; log only if different).
- **Bounded fields**: resource id, classification, `genericValueSummary`/`legacyValueSummary` (bounded strings per §8.2 — never a full `ClientResourceQueryResult`/legacy-object dump), `firstObservedGeneration`/`lastObservedGeneration` — the exact same "bounded metadata, never raw payload contents" discipline `ClientResourceSyncRejectionDiagnostics` already established (§2.5).
- **Development/config flag**: **[RECOMMENDATION]** not needed — DEBUG level itself is already the existing convention's gate (production log configuration typically runs at INFO or above; DEBUG-level parity logging is inert noise in a normal production log stream exactly like the existing malformed-payload DEBUG logging already is, requiring no additional flag to keep it out of normal players' logs). A config flag would be new surface area for a problem the existing log-level convention already solves.
- **Small in-memory latest-result map for Phase 3B-3**: **[RECOMMENDATION]** yes — the `ClientResourceParityTracker` (§13.1) is itself already exactly this map (one `ClientResourceParityObservation` per one of the four generic-sync-eligible resource ids, bounded to exactly 4 entries, never growing) — Phase 3B-3's eventual debug command can simply read the tracker's current state directly; no separate storage structure needs to be built now for that future consumer, only a public (or package-visible-to-a-future-command-class) read accessor, which this audit recommends **not** adding yet (the task explicitly reserves the debug command itself for Phase 3B-3) but notes the tracker's shape already anticipates it without redesign.
- **The future debug command belongs strictly to Phase 3B-3**: confirmed, matches the task's explicit constraint — this audit designs the tracker to make that command trivial later, but implements no command-facing accessor now.

---

## 17. Test plan

**[RECOMMENDATION]** Mapped to the task's 35-item list; ✓pure = unit-testable with plain JUnit and synthetic values (no Minecraft/client runtime), ⚠manual = requires a running client/dedicated server (per §13.3's classloading boundary and the established precedent that `ClientResourceSyncManager`-adjacent impure glue is not directly unit-tested, §17 of the Phase 3B-1 report).

| # | Test | Pure/Manual |
|---|---|---|
| 1 | Scalar exact match | ✓ pure — comparator with equal synthetic values |
| 2 | Scalar current mismatch | ✓ pure |
| 3 | Scalar maximum mismatch | ✓ pure |
| 4 | Scalar unit-scale mismatch | ✓ pure — but see §8's note: the façade already converts this to `MODEL_MISMATCH` upstream (`GenericSyncClientResourceReader`), so this test exercises the parity engine's *handling* of an incoming `MODEL_MISMATCH` result, not a raw scale-differing numeric comparison, which the comparator itself should never see |
| 5 | Partitioned exact match | ✓ pure |
| 6 | Missing partition | ✓ pure — synthetic partial partition map |
| 7 | Extra partition | ✓ pure — synthetic out-of-range partition key |
| 8 | Per-partition current mismatch | ✓ pure |
| 9 | Per-partition maximum mismatch | ✓ pure |
| 10 | Deterministic partition ordering | ✓ pure — already guaranteed upstream by `Partitioned`'s `TreeMap` rebuild (§2.2); test confirms the comparator iterates 1-10 in order regardless of input order |
| 11 | First mismatch is transitional | ✓ pure — tracker test, generation 0 |
| 12 | Match during grace clears the observation | ✓ pure — tracker test (§7.4) |
| 13 | Persistent mismatch after deadline | ✓ pure — tracker test, generation 2 (§7.2) |
| 14 | Repeated updates do not postpone persistence forever | ✓ pure — tracker test proving same-value re-notifications don't reset the baseline (§7.5) |
| 15 | Another real value generation restarts grace only when justified | ✓ pure — tracker test distinguishing a genuine new value from a same-value re-notification |
| 16 | Generic `NOT_SYNCHRONIZED_YET` | ✓ pure — classifies `GENERIC_NOT_READY`, never compared |
| 17 | Generic `NOT_AVAILABLE_TO_PLAYER` | ✓ pure — feeds into Rage-specific logic (§9) or ordinary absence handling for Mana/Stamina |
| 18 | Generic `PENDING_RESYNC` | ✓ pure — generation-freeze test (§7.7) |
| 19 | Legacy not initialized | ✓ pure — synthetic legacy default values (100/100, all-zero, empty map) compared against a real generic value |
| 20 | Native Resource parity not applicable | ✓ pure — confirms the engine's resource-id iteration structurally excludes Health/Food/Breath (§4.2), not merely returns `NOT_APPLICABLE` at runtime |
| 21 | Rage absent vs. legacy ambiguous 0/0 | ✓ pure — both the non-Barbarian (`EXPECTED_SEMANTIC_DIFFERENCE`) and Barbarian (ordinary mismatch) branches (§9.2), plus the defensive "cannot currently occur" `RAGE_ABSENCE_AMBIGUOUS` case (§9.2's last bullet) |
| 22 | Rage present 0/0 | ✓ pure — genuine `EXACT_MATCH` |
| 23 | Rage present nonzero | ✓ pure |
| 24 | Spell-slot all-zero state | ✓ pure |
| 25 | Mana legacy default before synchronization | ✓ pure — combined with test 19/16: confirms no comparison is recorded while `GENERIC_NOT_READY` (§11) |
| 26 | Stamina join synchronization | ⚠ manual — the actual join-time push timing (§3.2) requires a live client/server round-trip to observe; the *classification* of the asymmetry itself (`EXPECTED_SEMANTIC_DIFFERENCE`) is ✓ pure |
| 27 | Lifecycle reset | ✓ pure — tracker's own `clear()`/reset method, driven directly rather than through a real `ClientPlayConnectionEvents.JOIN` firing |
| 28 | Connection replacement | ✓ pure — same as 27, tracker-level |
| 29 | Dimension reset | ✓ pure — same as 27 |
| 30 | Full snapshot compares all eligible Resources | ✓ pure — coordinator-level logic can be tested against a fake `ClientResourceService`/fake legacy readers without a real client, following `FakeGenericSyncResourceAccess`'s existing precedent (§ Phase 3B-1 report §6) |
| 31 | Delta compares only affected Resources | ✓ pure — same fake-based approach, asserting only the delta's named ids trigger a comparison |
| 32 | Bounded duplicate logging | ✓ pure — the log-message-formatting helper (mirroring `ClientResourceSyncRejectionDiagnostics`, §2.5) is directly unit-testable for bounded content; whether the *actual* `Totality.LOGGER.debug` call fires is validated the same way Phase 3B-1 validated its own logging addition (§17 of that report's "deliberate deviation" — code review + the underlying pure logic's exhaustive tests, not a direct log-capture test against a static singleton) |
| 33 | Persistent mismatch recovery | ✓ pure — tracker test, `PERSISTENT_MISMATCH` → `EXACT_MATCH` transition |
| 34 | Dedicated-server classloading boundary | ⚠ manual — start a dedicated server build, confirm no `NoClassDefFoundError`/`ClassNotFoundException` referencing any new parity class, exactly matching Phase 3B-1's own manual validation step (§16.2 of the Phase 3B readiness audit; already empirically confirmed working for the existing Phase 3B-1 classes per that report's manual-validation §10) |
| 35 | All existing 572 tests remain unchanged and passing | ✓ pure (in the sense of being a CI-style gate) — re-run the full suite after any Phase 3B-2 implementation slice; this audit's own baseline check (§1) already confirms the starting point |

---

## 18. Risks and unresolved questions

1. **[OPEN QUESTION]** Exact client-side respawn-reset signal for the parity tracker (§14) — this audit did not locate and confirm a specific existing client-side "respawn happened" event distinct from `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`/`ClientPlayConnectionEvents` (a same-dimension respawn, e.g. dying and respawning at a bed in the Overworld, may fire neither) — needs identification during Phase 3B-2 implementation, not resolved here.
2. **[OPEN QUESTION]** Whether `TotalityClientSyncListeners` (referenced by the task, called from `TotalityClient.java` line 85) contains any per-resource hook relevant to notification strategy (§15) — this audit's required file list did not include reading that file's contents in depth; recommend confirming during implementation before finalizing the polling-vs-hook decision, though the polling recommendation (§15.2) does not depend on the answer either way.
3. **[RISK, pre-existing, newly documented]** `FormulaResolver.tryCast`'s missing legacy Mana sync (§5.4) is a real, currently-live staleness bug in the legacy system, unrelated to and unfixed by this audit — flagged for future triage, not a Phase 3B-2 blocker, but likely to produce the first real `PERSISTENT_MISMATCH` log Stefan sees once Phase 3B-2/3B-3 logging is live, and should not be mistaken for a parity-engine bug when it appears.
4. **[RISK]** `PlayerChargesComponent.applySyncPacket`'s discarded-`max`-for-existing-pools behavior (§3.4) means a live Rage maximum increase (e.g. a Barbarian leveling up mid-session) may never reach the legacy client mirror's `getMax(...)` for the remainder of that connection, while the generic side's `maximumUnits` would correctly reflect the new value — a second concrete, real scenario (distinct from §5.4) where the parity engine should expect to observe a genuine, persistent `maximumUnits` mismatch that is not a parity-engine defect. Recommend documenting this expectation before Phase 3B-3 manual validation, so it isn't mistaken for a new bug introduced by the parity engine itself.
5. **[RISK, carried forward from the Phase 3B readiness audit, unresolved]** The client/server package-sharing convention (naming-only client/server boundary, §13.3) still has zero compiler enforcement beyond the three already-`@Environment`-annotated Phase 3B-1 classes — Phase 3B-2's new classes should adopt `@Environment` from the start (§13.3's recommendation) but this does not retrofit protection onto the untouched Phase 3A classes.
6. **[UNRESOLVED, minor]** This audit did not exhaustively verify every one of the ~20+ Stamina spend call sites individually for a `FormulaResolver`-style gap (§3.2 notes none were found, but the grep-based method used here — matching `StaminaServerTick.syncStamina`/`PlayerStaminaManager.(add|remove|set)Stamina` call sites and cross-referencing — is not a formal proof of completeness for every call path, e.g. an ability that calls `removeStamina` indirectly through a further layer of indirection this audit's greps did not trace). Recommend treating any *newly observed* Stamina `PERSISTENT_MISMATCH` during Phase 3B-3 validation as a lead worth investigating rather than assuming it must be a parity-engine defect, given §5.4 already proves this class of gap exists for Mana.
7. **[OPEN QUESTION]** Whether the parity tracker's 4-entry map should be reachable by any code before Phase 3B-3's debug command exists — this audit recommends no public accessor yet (§16), but the exact package-visibility level (fully private vs. package-visible-for-a-future-command-class-in-the-same-package) is left to implementation judgment.

---

## 19. Recommended Phase 3B-2 implementation slices

**[RECOMMENDATION]** Confirming and refining the task's own proposed 3B-2A/3B-2B/3B-2C split:

**Phase 3B-2A — pure values, classifications, comparators, tracker (no integration hooks):**
- `ClientResourceParityClassification` (§8), `ClientResourceParityObservation` (§8.2).
- Scalar and partitioned comparator functions (§13.1), taking plain values in, returning a classification.
- `ClientResourceParityTracker` — the generation/grace-window state machine (§7), driven entirely by explicit method calls in tests, no tick/event wiring yet.
- Rage's class-aware disambiguation logic (§9.2) as a pure function taking `(hasClass: boolean, primaryClassId: Identifier?, generic result, legacy pair)` — not yet wired to real `ClientClassManager` calls.
- Full test coverage for test-plan items 1-25, 27-33 that are marked ✓ pure in §17 (most of the list).
- No production wiring, no `TotalityClient.java` change, no legacy-class touch of any kind.

**Phase 3B-2B — impure legacy readers, coordinator, notifications, lifecycle reset (no logging escalation yet):**
- Four thin legacy-reader interfaces + real implementations (§13.2) reading `ClientManaManager`/`ClientStaminaManager`/`ClientSpellSlotManager`/`PlayerChargesComponent` (client instance).
- `ClientResourceParityCoordinator` (§6.2) wired to `ClientResourceSyncManager.applyFull`/`applyDelta` (generic-side notification) and its own `END_CLIENT_TICK` legacy-polling pass (§15.2) — no modification to any of the four legacy classes themselves.
- Lifecycle reset wiring (§7.6/§14) at the three existing `ClientResourceSyncManager.clear()` call sites, plus resolving open question #1 (§18) for the respawn case during this slice, not deferring it silently.
- Rage's class-aware check wired to the real `ClientClassManager.hasClass()`/`getPrimaryClassId()` (§9.2).
- Manual validation of test-plan items 26, 34 (⚠ manual) begins here, not deferred to 3B-2C.

**Phase 3B-2C — bounded logging, validation, implementation report, manual parity exercise, readiness decision:**
- Bounded DEBUG logging for `PERSISTENT_MISMATCH` transitions (both directions) per §16, following `ClientResourceSyncRejectionDiagnostics`'s exact precedent.
- Full automated test suite re-run (test-plan item 35), full `compileJava`/`compileTestJava`/`test`/`runDatagen`/`build` validation matching Phase 3B-1's own validation checklist (§18 of that report).
- Manual parity exercise: join, spend/restore each of the four generic-sync resources through ordinary gameplay (including deliberately exercising a rune cast to observe §5.4's known gap, and a Barbarian class-level-up to observe §18 risk #4's known gap), reconnect, dimension change, death/respawn — observing the DEBUG log output for expected `EXPECTED_SEMANTIC_DIFFERENCE`/known-gap entries and absence of unexpected `PERSISTENT_MISMATCH` entries elsewhere.
- Readiness decision for Phase 3B-3 (debug command / parity verification tooling) and eventual Phase 3C consumer migration — not started in 3B-2C itself.

No implementation of any slice was performed in this audit.

---

## 20. Explicit out-of-scope list

Per the task's constraints, confirmed as understood and respected throughout this audit:
- No parity implementation of any kind — this document is design/audit only.
- No modification to any production or test file — confirmed by this audit's own final `git status --short` (§22).
- No mutation of Resource state, authority, gameplay, rendering, or packet-handling outcomes by the *design* recommended here (all reads, no writes, throughout §6-§16).
- No migration of any consumer listed in §12.
- No disabling of any legacy packet or manager.
- No removal of any legacy manager.
- No gameplay-visible warning, toast, HUD overlay, or chat message from any parity mismatch (§16).
- No parity debug command (explicitly reserved for Phase 3B-3, §16/§19).
- No start of Phase 3B-3 or Phase 3C.
- The four known-deferred issues listed in the task's "KNOWN DEFERRED ISSUES" section (Ancestry dimension refresh, `ProvisionerEntityBackedSmokeTest` dedicated-server failures, `OffhandAttackVerification` dedicated-server failures, Food/Hunger redesign) were not investigated, touched, or referenced as production evidence anywhere in this audit.
- `/Inspiration Mods` was not read, searched, or referenced.
- The "known unrelated" working-tree entries (generated datagen JSON, Trading Test screenshots, `logs/`, `src/main/generated/.cache/`, all review ZIPs) were not modified, staged, cleaned, reset, or restored.

---

## 21. Phase 3B-3 handoff requirements

**[RECOMMENDATION]**
- The `ClientResourceParityTracker`'s 4-entry observation map (§16) should be directly reusable by Phase 3B-3's debug command with only a new read accessor — no redesign expected.
- Phase 3B-3 must resolve open question #1 (§18) — the exact client-side respawn-reset signal — if Phase 3B-2B does not already resolve it during implementation (recommended to attempt in 3B-2B per §19, but flagged here in case it slips).
- Phase 3B-3 should re-run the manual parity exercise from §19's 3B-2C slice with the debug command available, specifically checking whether the two known, already-anticipated gaps (§5.4's `FormulaResolver` Mana staleness, §18 risk #4's Rage-maximum-change staleness) appear as expected `PERSISTENT_MISMATCH` entries and are correctly attributable, rather than being mistaken for new defects.
- Phase 3B-3's debug command output format should reuse `ClientResourceParityObservation`'s bounded string summaries (§8.2) directly rather than inventing a second display format.
- Phase 3B-3 should decide whether to fix (or formally accept and document) the two known pre-existing legacy gaps (§5.4, §18 risk #4) — Phase 3B-2 only surfaces them, per its read-only/no-production-change mandate.

---

## 22. Phase 3C readiness criteria

**[RECOMMENDATION]**
Before Phase 3C consumer migration begins, recommend confirming:
1. Phase 3B-2's parity engine has run through at least one full manual play session (per §19's 3B-2C exercise) covering join, ordinary regen, all four resources' spend/restore paths, reconnect, dimension change, and death/respawn, with no *unexpected* `PERSISTENT_MISMATCH` (i.e., only the two already-known, already-explained gaps, if they appear at all).
2. The two known legacy gaps (§5.4, §18 risk #4) have an explicit triage decision recorded (fix before migrating the affected consumer, or accept and document why migrating past them is still safe).
3. Phase 3B-3's debug command (once built) has been used at least once to manually cross-check a live session's parity state against what a player actually sees, closing the loop between "the engine says these agree" and "a human confirms the displayed numbers actually match."
4. No new `PERSISTENT_MISMATCH` appears for Standard Spell Slots specifically, given §10's `currentUnits`-means-remaining derivation is the single highest-risk correctness point in this whole audit (a subtle off-by-inversion error here would silently misreport used-vs-remaining without necessarily crashing or failing loudly).
5. The Rage class-aware disambiguation (§9) has been exercised with an actual Barbarian test character specifically, not only inferred from non-Barbarian testing, since the interesting case (§9.2's "Barbarian + generic absent" branch) cannot be observed any other way.

---

## Stop point — status

1. No implementation was performed; only inspection commands (`git status`, `git log`, `git rev-list`, `git diff --cached`, file reads, greps, one `./gradlew test` run that hit `UP-TO-DATE` and changed nothing) were used.
2. Created file: `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B2_SHADOW_PARITY_READINESS.md` (this document).
3. Final `git status --short` is shown in the accompanying response, not embedded here as a stale snapshot.
4. No source, test, or build file was changed by this audit.
5. Nothing was staged, committed, or pushed.
6. Phase 3B-2 implementation has not started. Phase 3B-3 and Phase 3C were not begun.
7. Stopping here for review, per the task's explicit instruction. No Phase 3B-2 implementation prompt has been produced.
