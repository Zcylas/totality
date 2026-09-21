# TOTALITY RESOURCE API — PHASE 2C: MANA & STAMINA LEGACY-STORE ADAPTERS — IMPLEMENTATION REPORT

**Date:** 2026-07-19
**Branch:** `feature/general-resource-api`
**Starting commit:** `03c85a86` ("Add breath resource adapter") — confirmed the completed, committed Phase 2B commit via `git log`; `git diff --cached` was empty before any change was made.
**Scope:** `src/main/java/zcylas/totality/**`, `src/test/java/zcylas/totality/**` only. `/Inspiration Mods` was excluded from every search performed in this phase.

---

## 1. Purpose and scope

Add **query-only** Generic Resource adapters over the currently authoritative legacy Mana and Stamina implementations, proving `PlayerResourceService` can represent them without migrating storage, creating duplicate state, or changing any existing formula, cost, regeneration, packet, HUD, Rest, or lifecycle behavior. This is a transitional adapter phase — Mana and Stamina remain fully owned by their existing legacy systems after this phase; only a read-only query surface was added on top.

## 2. Starting state verification

- Branch: `feature/general-resource-api`.
- `HEAD`: `03c85a86`, "Add breath resource adapter" — confirmed as the completed, committed Phase 2B commit (not staged-only) via `git log -3`.
- `git diff --cached`: empty.
- Initial `git status --short` unrelated inventory (pre-existing, not touched by this phase): 21 modified generated JSON files under `src/main/generated/data/**`, 4 untracked PNGs under `Context/Trading Test/`, untracked `src/main/generated/.cache/`, untracked `logs/`. All remain in the exact same state at the end of this phase.

## 3. Exact audited Mana architecture

| Aspect | Finding |
|---|---|
| Resource id (this phase) | `totality:mana` |
| Storage field/type | `PlayerResourceComponent.mana` (`int`, `api/rpg/resources/PlayerResourceComponent.java:21`) |
| Sentinel/uninitialized value | `-1`. `isManaInitialized()` returns `mana >= 0` — **any** negative value (not only `-1`) is treated as uninitialized by the component's own contract. |
| Public getter behavior | `PlayerResourceComponent.getMana()`/`isManaInitialized()` are pure reads, no side effect. `PlayerManaManager.getMana(Player)` (the ordinary gameplay-facing getter) **does** have a side effect: `if (!comp.isManaInitialized()) comp.setMana(getMaxMana(player))` — lazily initializes the component to its live maximum on first read. |
| Does the getter mutate via lazy init? | Yes — `PlayerManaManager.getMana`, not `PlayerResourceComponent.getMana`. This distinction is exactly why the adapter reads the component directly. |
| Setter/add/remove | `PlayerManaManager.setMana/addMana/removeMana/hasMana` — all route through `setMana`, which clamps to `[0, getMaxMana(player)]` via `Math.clamp`. `removeMana` no-ops for creative players. |
| Maximum formula | `PlayerManaManager.getMaxMana(Player)`: `BASE_MAX_MANA (100)` + `PlayerStats.getMaxManaBonus()` (`INT modifier × 10`, confirmed live in `PlayerStats.java:182-184`) + armor-slot `ManaItem` bonuses (`ManaSource.ARMOR`) + `ModEffects.FORTIFY_MANA` (`amplifier + 1`) + held-item `ManaItem` bonus (`ManaSource.ITEM`, deduplicated against armor by `ItemStack.is(...)` per slot) + `MaxManaCalcEvent` hook (posted via `ManaEvents.postMaxMana`). Pure computation — confirmed no write anywhere in this method. |
| Numeric precision | Purely integral (`int`) end to end — no fractional Mana value exists anywhere. |
| Min/max clamping | `setMana` clamps to `[0, getMaxMana(player)]`. **Current can transiently exceed maximum** between clamping passes (e.g. immediately after a `FORTIFY_MANA` effect expires) — `PlayerResourceRecalculator` and `ManaServerTick` both explicitly re-clamp on their own next pass; this is existing, accepted behavior, not a bug. |
| Regeneration timing | `ManaServerTick`: every 20 ticks (`ServerTickEvents.END_SERVER_TICK`, `tickCounter % 20 == 0`), unconditional (no combat/activity gating, unlike Stamina). |
| Regeneration amount | `PlayerManaManager.calculateRegenAmount`: `max(1, (int)(maxMana × regenPercent))`. `getRegenPercent`: base `BASE_REGEN_PERCENT = 0.02f` (2%/sec) + armor `ManaRegenItem` bonus + `ModEffects.REGENERATE_MANA` (`× (1 + (amplifier+1)/100)`) + held-item `ManaRegenItem` bonus (deduplicated the same way) + `ManaRegenCalcEvent` hook. |
| Drain sources | `FormulaResolver` (spell cast cost), `HeatVisionAbility`, `GrimoireItem` — all call `PlayerManaManager.removeMana`. |
| NBT keys | `"mana"` (`int`, default `-1`) — `PlayerResourceComponent.writeData/readData`. |
| Component copy behavior | `PlayerResourceComponent.copyFrom` **unconditionally resets `mana = -1`** on every respawn copy (death, dimension change, End return — identical, no `alive` check). |
| Native/generic sync | `PlayerResourceComponent` implements `SyncedComponent`/`writeSyncPacket`/`applySyncPacket`, but `.sync()` is never called anywhere in production code (confirmed via `grep` — the only `comp.sync()` call sites found belong to an unrelated class component). No vanilla native sync exists for Mana at all. |
| Bespoke packet sync | `SyncManaPayload` (`totality:sync_mana`, `int mana, int maxMana`) sent from `ManaServerTick.syncMana` and `PlayerResourceRecalculator`. |
| Client cache | `ClientManaManager` — two static `int` fields (`mana`, `maxMana`), updated only by the `SyncManaPayload` handler. |
| HUD reader | `TotalityHudRenderer.java:113-114,160-163` reads `ClientManaManager.getMana()/getMaxMana()` directly, smoothed via `manaSmooth`, drawn only `if (maxMana > 0)`. |
| Rest integration | **None.** Confirmed absent from every `RestEventBus`/`RestManager` registration (only Rage, Abilities, and Spell Slots are registered). Phase 2C does not add this — explicitly out of scope. |
| Death behavior | Full reset to uninitialized (`-1`) on every `copyFrom`, including death — next read lazily reinitializes to max via `PlayerManaManager.getMana`. |
| Dimension behavior | Same `copyFrom` reset — dimension change also resets Mana to uninitialized, identical to death. This is existing, shipped behavior; Phase 2C changes nothing about it. |
| Logout/rejoin behavior | Persisted via NBT (`"mana"` key) across logout/rejoin (not a `copyFrom` event) — the stored value round-trips normally. |

## 4. Exact audited Stamina architecture

| Aspect | Finding |
|---|---|
| Resource id (this phase) | `totality:stamina` |
| Storage field/type | `PlayerResourceComponent.stamina` (`int`, same class, `:20`) |
| Sentinel/uninitialized value | `-1`; `isStaminaInitialized()` returns `stamina >= 0` — same "any negative = uninitialized" contract as Mana. |
| Public getter behavior | `PlayerResourceComponent.getStamina()`/`isStaminaInitialized()` are pure. `PlayerStaminaManager.getStamina(Player)` lazily initializes on first read exactly like the Mana equivalent. |
| Setter/add/remove | `PlayerStaminaManager.setStamina/addStamina/removeStamina/hasStamina`, same clamp-to-`[0, max]` and creative no-op pattern as Mana. |
| Maximum formula | `PlayerStaminaManager.getMaxStamina(Player)`: `BASE_MAX_STAMINA (100)` + `PlayerStats.getMaxStaminaBonus()` (`END modifier × 10`, confirmed live) + armor `StaminaItem` bonuses + `ModEffects.FORTIFY_STAMINA` + held-item `StaminaItem` bonus (deduplicated) + `MaxStaminaCalcEvent` hook. Pure computation, no write. |
| Numeric precision | Purely integral. |
| Min/max clamping | Same transient-overflow-then-reclamp pattern as Mana (`PlayerResourceRecalculator`, `StaminaServerTick`'s own `setStamina(getStamina(...))` re-clamp calls). |
| Regeneration timing | `StaminaServerTick`: every 20 ticks, gated off while actively sprinting-with-stamina>0, Power Sprinting, bow drawn, or actively flying. |
| Regeneration amount | `calculateRegenAmount`: `max(1, (int)(maxStamina × regenPercent))`, then further multiplied by `ExhaustionManager.getRegenMultiplier(player)` (**0.5** if combat-penalized/exhausted, **0.75** if in the Warning exhaustion state, **1.0** otherwise). `getRegenPercent`: base rate depends on combat state — `IN_COMBAT_REGEN_PERCENT = 0.02f` if `CombatStateManager.isInCombat`, else `BASE_REGEN_PERCENT = 0.05f` — plus armor/held-item `StaminaRegenItem` bonuses, `ModEffects.REGENERATE_STAMINA`, and `StaminaRegenCalcEvent`. |
| Drain sources | Sprint (`1` every `MovementStaminaCosts.NORMAL_SPRINT_DRAIN_INTERVAL_TICKS = 3` ticks, ground-only, not while Power Sprinting/bow-drawn/creative), biological flight (`MovementStaminaCosts.FLIGHT_DRAIN_COST = 1` every `FLIGHT_DRAIN_INTERVAL_TICKS = 3` ticks), `BowStaminaHandler.tick` (bow/crossbow draw cost), `WeaponStaminaHandler` (melee), `OffhandAttackHandler`/`PowerAttackManager`/`PowerAttackVerification`, `GroundSlamAbility`, `VeinminerAbility`, `ShurikenItem`, `MovementStaminaHandler`, `PowerSprintStateHandler`, `ToggleFlightHandler`. |
| Combat-state multiplier | Applied at the regen-percent selection step (`IN_COMBAT_REGEN_PERCENT` vs `BASE_REGEN_PERCENT`), via `CombatStateManager.isInCombat`. |
| Exhaustion multiplier | `ExhaustionManager.getRegenMultiplier`: `0.5f` if `isPenalized` (combat-attribute-penalized), else `0.75f` if the player's tracked state is `WARNING`, else `1.0f`. Applied multiplicatively on top of the regen amount. `StaminaServerTick` also applies `-20%` movement speed and `-25%` attack-damage transient attribute modifiers while `isPenalized`. |
| Pause conditions | Regen blocked while `(isSprinting() && currentStamina > 0)`, or Power Sprinting, or bow drawn, or actively flying. |
| NBT keys | `"stamina"` (`int`, default `-1`) — same component as Mana. |
| Component copy behavior | Same unconditional reset to `-1` on every `copyFrom` as Mana. |
| Native/generic sync | Same as Mana — `.sync()` never called; no vanilla native sync exists. |
| Bespoke packet sync | `SyncStaminaPayload` (`totality:sync_stamina`), sent from `StaminaServerTick.syncStamina` and `PlayerResourceRecalculator`. |
| Client cache | `ClientStaminaManager` — same two-static-`int`-field shape as `ClientManaManager`. |
| HUD reader | `TotalityHudRenderer.java:111-112,158-159` reads `ClientStaminaManager` directly, smoothed via `staminaSmooth`, always drawn (no `> 0` gate, unlike Mana). |
| Rest integration | **None** — same absence as Mana, confirmed by the same `RestEventBus` audit. |
| Death/dimension/logout behavior | Identical pattern to Mana — full reset on `copyFrom` (death and dimension change alike), NBT round-trip across logout/rejoin. |

## 5. Sentinel/lazy-initialization findings

Both `PlayerManaManager.getMana`/`PlayerStaminaManager.getStamina` — the methods every existing gameplay caller uses — **silently write** the legacy component the first time they are called for a player whose value is still the `-1` sentinel: `if (!comp.isManaInitialized()) comp.setMana(getMaxMana(player))`. This is a real, intentional side effect for ordinary gameplay (it's how a freshly-joined player ends up at full Mana/Stamina without an explicit initialization step anywhere), but it is exactly the kind of "a read silently becomes a write" the task's read-only query contract forbids. `PlayerResourceComponent`'s own `getMana()`/`getStamina()`/`isManaInitialized()`/`isStaminaInitialized()` methods, by contrast, are genuinely pure — confirmed by direct inspection (no field writes, no calls to anything else).

**Uninitialized-state policy determination:** an ordinarily-joined player is expected to have both initialized almost immediately — the very next Mana/Stamina server tick (every 20 ticks, unconditional for the tick loop itself even if regen is blocked) calls the legacy manager's getter internally and initializes it, and any existing gameplay action (spending, the HUD's own client-side render triggering a sync) does the same. This is a narrow, real, but short-lived window (at most ~1 second after join, before the first server tick), not evidence that a derived "would-initialize-to-max" view is needed for correctness or that callers commonly need to see the value while genuinely uninitialized.

**Chosen policy:** structured failure. `ManaResourceAdapter`/`StaminaResourceAdapter` read the component directly and return `ResourceQueryFailureReason.STATE_UNINITIALIZED` (new) when uninitialized, rather than a derived/fabricated max-value view. This follows the task's explicit preference ("prefer structured failure unless the repository demonstrates that the derived view is required for compatibility") — nothing in the audit demonstrated the derived view was required.

## 6. Resource and adapter identifiers

Added to `PlayerResourceIds`:

```java
public static final Identifier MANA = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mana");
public static final Identifier STAMINA = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "stamina");
public static final Identifier MANA_ADAPTER = MANA;
public static final Identifier STAMINA_ADAPTER = STAMINA;
```

No existing persisted NBT field (`"mana"`/`"stamina"` inside `PlayerResourceComponent`) or packet identifier (`totality:sync_mana`/`totality:sync_stamina`) was renamed or touched.

## 7. Transitional adapter authority

`ManaResourceAdapter`/`StaminaResourceAdapter` (`api/rpg/resources/external/`) are query-only adapters over the legacy-authoritative `PlayerResourceComponent` store. Both are registered as `EXTERNAL_ADAPTER`-authority definitions at **`definitionVersion = 1`**, explicitly documented (in both the adapter class Javadoc and `ProductionResourceDefinitions`) as transitional: a future migration of Mana/Stamina's actual storage onto `GENERIC_COMPONENT` authority (`PlayerResourceStateComponent`) requires an explicit `definitionVersion` increase and a real migration step at that time — this phase does not implement or imply any silent structural hot-swap of what `totality:mana`/`totality:stamina` mean. No duplicate generic Mana/Stamina state is created anywhere (see §15/§12 below).

## 8. Server-authoritative query boundary

Neither adapter imports `ClientManaManager`/`ClientStaminaManager` or any other client-only cache class. `snapshot(Player, definition)` first checks `player instanceof ServerPlayer`; if not (a client-side `LocalPlayer`), it returns `ResourceQueryResult.Failure(STATE_UNAVAILABLE_ON_THIS_SIDE, ...)` immediately — no client-only class is ever touched, no untrusted client cache is read, no value is fabricated. A new `ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION` documents this honestly: Mana/Stamina currently reach the client only through their existing bespoke packet/cache, not through vanilla native sync or the generic Resource API sync path; generic client-side querying is deferred to a future synchronization/client-presentation phase. Existing HUD readers (`TotalityHudRenderer`) continue reading `ClientManaManager`/`ClientStaminaManager` directly, completely untouched by this phase. No new Mana/Stamina packet was added.

## 9. Read-only query must not initialize legacy state

**Superseded by the correction pass — see "Correction Pass Addendum" at the end of this report for the current code.** `ManaResourceAdapter.snapshot`/`StaminaResourceAdapter.snapshot` never call `PlayerManaManager.getMana`/`PlayerStaminaManager.getStamina` (the mutating getters). Current shape:

```java
var component = ResourceComponents.maybeGet(serverPlayer);   // new, non-throwing, non-mutating
if (component.isEmpty()) { return Failure(STATE_UNAVAILABLE_ON_THIS_SIDE); }
return resolve(definition.id(), component.get(), () -> PlayerManaManager.getMaxMana(player), definition.unitScale());
```

`resolve` (new in the correction pass) checks `isManaInitialized()` **before** ever invoking the maximum supplier — see the addendum for why the original version (which computed the maximum unconditionally) was corrected.

`ResourceComponents.maybeGet(ServerPlayer)` is a new, minimal, additive public accessor (mirroring the existing `ComponentKey.maybeGet`) — it never throws and never instantiates a component, unlike the existing `ResourceComponents.get(ServerPlayer)` (which throws if absent). Neither `PlayerResourceComponent` nor any mutable component map was exposed publicly; the adapter only ever calls the component's existing, already-pure public getters.

A read-only query never writes the legacy component, never converts `-1` to maximum, never calls `sync()`, never dirties NBT, never triggers a packet, and never creates any Generic Resource state — all confirmed by code review of the adapter (it has no reference to any mutation method) and by the external-state-safety tests in §15/§16.

## 10. Numeric representation

Both Mana and Stamina are confirmed purely integral (`int` throughout — component field, manager methods, packet payload, client cache) — no fractional value exists anywhere. Both definitions use `unitScale = 1` and `ResourceDisplayConversion.IDENTITY` (already existing, `1/1`) — no rescaling of any current gameplay value. The live maximum is never hardcoded: both adapters call `PlayerManaManager.getMaxMana(player)`/`PlayerStaminaManager.getMaxStamina(player)` fresh on every query, exactly like `HealthResourceAdapter`/`BreathResourceAdapter`'s established precedent of never trusting `authoredBaseMaximum` for the live query path. The registered `authoredBaseMaximum` (100 for both, from `PlayerManaManager.BASE_MAX_MANA`/`PlayerStaminaManager.BASE_MAX_STAMINA`) is purely descriptive metadata.

## 11. Production registry entries

```java
PlayerResourceRegistry.INSTANCE.register(
        PlayerResourceDefinition.builder(PlayerResourceIds.MANA, ResourceModel.SCALAR)
                .polarity(ResourcePolarity.HIGH_IS_GOOD)
                .externalAdapter(PlayerResourceIds.MANA_ADAPTER)
                .unitScale(1)
                .absoluteMinimum(0)
                .authoredBaseMaximum(PlayerManaManager.BASE_MAX_MANA)
                .capabilities(ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                .presentation(new ResourcePresentationDefinition(
                        ResourceDisplayConversion.IDENTITY, ResourceDisplayType.BAR, ResourceHudRole.CORE_CONSTANT))
                .definitionVersion(1)
                .build());
// totality:stamina registered identically, substituting Stamina's own adapter/manager/constant.
```

No `SPENDABLE`/`RESTORABLE`/`DIRECT_DRAIN` capability on either — query-only, matching Health/Food/Breath's precedent.

**Final production registry contents (after this phase):** exactly `totality:health`, `totality:food`, `totality:breath`, `totality:mana`, `totality:stamina` — all `ResourceModel.SCALAR`, all `ResourceStateAuthority.EXTERNAL_ADAPTER`, all `ResourcePolarity.HIGH_IS_GOOD`, all frozen deterministically. No `totality:rage`/`totality:barbarian_rage`/`totality:spell_slots`/`totality:spell_slot` definition exists — verified by test.

**Final external-adapter registry contents:** exactly `HealthResourceAdapter.INSTANCE`, `FoodResourceAdapter.INSTANCE`, `BreathResourceAdapter.INSTANCE`, `ManaResourceAdapter.INSTANCE`, `StaminaResourceAdapter.INSTANCE` — verified by test.

## 12. Snapshot validation

**Correction pass note:** the pure `normalize` core no longer handles the uninitialized case (that moved to `resolve`, §9) — see the addendum. Current shape, `ManaResourceAdapter.normalize`/`StaminaResourceAdapter.normalize` (package-visible pure cores, called only by `resolve` once `current` is already known-initialized):

```java
static ResourceQueryResult normalize(Identifier resourceId, int current, int liveMaximum, long unitScale) {
    if (liveMaximum <= 0) {
        return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, resourceId);
    }
    if (current < 0) {
        return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, resourceId);
    }
    return new ResourceQueryResult.Success(new ResourceSnapshot(resourceId, current, liveMaximum, unitScale));
}
```

`liveMaximum <= 0` is **reachable in practice** (unlike Breath's purely-defensive case): neither `getMaxMana`/`getMaxStamina` floors its result above zero, and a sufficiently negative INT/END ability-score modifier can drive the live maximum to zero or below — confirmed and pinned by a new `PlayerStatsCharacterizationTest` test (`negativeEndAndIntModifiersProduceNegativeStaminaAndManaBonuses`). Current is **never clamped down to maximum** — it passes through unchanged even when it transiently exceeds the live maximum, matching the existing owner's own accepted permissive behavior (§3/§4's "current can transiently exceed maximum" finding) — the adapter does not "repair" that. `PlayerResourceService`'s pre-existing `validateExternalSnapshot` structural checks (resource id match, unit scale match, maximum ≥ absolute minimum) still apply unchanged to every `Success` snapshot. No `null` is ever returned from either adapter's `snapshot()`; a misbehaving hypothetical adapter returning a literal `null` `ResourceQueryResult` is still defensively caught by `PlayerResourceService.queryExternal`. As of the correction pass, an adapter's returned `Failure` reason is additionally constrained to the three reasons an adapter is actually permitted to determine — see the addendum.

## 13. No generic mutation

Both adapters declare `supportedOperations() = Set.of(ExternalResourceOperationSupport.QUERY)` — nothing else. No spend/restore/drain/set/transaction entry point was added to `PlayerResourceService` for any resource; Mana/Stamina spending/regeneration continues exclusively through `PlayerManaManager`/`PlayerStaminaManager`. `PlayerResourceService`'s pre-existing operation-support enforcement (both at `ExternalPlayerResourceAdapterRegistry.register` time and independently at query time) applies automatically to these two adapters with no changes needed.

## 14. No synchronization or HUD migration

No generic full-snapshot or delta packet, no client resource registry/state/manager, no revision counter, no client prediction reconciliation, no HUD/menu migration, and no packet removal were implemented. `SyncManaPayload`/`SyncStaminaPayload`, the generic `ComponentSync` machinery (still unused for this component), `ClientManaManager`/`ClientStaminaManager`, and `TotalityHudRenderer`'s existing Mana/Stamina bars are all left completely untouched. The pre-existing duplicated-synchronization finding (readiness audit §4.1: a written-but-unread generic `ComponentSync` mirror alongside the real bespoke packet) is unchanged and is documented, not fixed, by this phase. The HUD overlap defect (readiness audit §5/§10) was not touched.

## 15. External-state safety

Because Mana and Stamina are (transitionally) `EXTERNAL_ADAPTER`-authority, every existing external-state protection in `PlayerResourceStateComponent` applies to them automatically — the rejection logic is fully registry-driven (`isRegisteredExternalAdapterAuthority` looks up `PlayerResourceRegistry.INSTANCE` at call time, with no per-resource hardcoding), confirmed by direct inspection before writing any adapter code. Verified by test (§16): `instantiateScalar`/`instantiatePartitioned` both reject Mana and Stamina; stale generic-shaped NBT for either is quarantined into `orphanedStates`, never restored live; a hand-built generic sync payload carrying either id is discarded, not installed; `copyFrom`-injected corrupted live state is quarantined rather than copied; `writeData`/`writeSyncPacket` exclude any (defensively-injected-only) live entry for either id; production queries always route to the legacy adapters (`queryGenericState` is structurally unreachable for `EXTERNAL_ADAPTER`-authority definitions); registration alone creates no player state. The separate legacy `PlayerResourceComponent` (a different class entirely, `ResourceComponents.RESOURCES`) is untouched by any of this — the two systems coexist without interference.

## 16. Tests

All 240 pre-existing tests preserved (8 files touched only to adapt to the `ExternalPlayerResourceAdapter#snapshot` signature change, with zero behavioral change to what they assert; `PlayerResourceStateComponentTest`'s pre-existing placeholder ids `"mana"`/`"stamina"` were renamed to `"test_scalar_a"`/`"test_scalar_b"` — those tests predate Phase 2C and never intended to collide with the now-real production ids, since `PlayerResourceRegistry.INSTANCE` is shared static state across the whole test JVM). Added/changed coverage:

| Area | File | Coverage |
|---|---|---|
| Registration | `PlayerResourceRegistryTest` (+7 net) | Exactly 5 production definitions, all `EXTERNAL_ADAPTER`/`SCALAR`; no Rage/spell-slot/Temperature/Oxygen/Air definition; Mana/Stamina capabilities (`HUD_VISIBLE`+`MENU_VISIBLE` only); Mana/Stamina authored baseline (100) and explicit `definitionVersion = 1`. |
| Registration | `PlayerResourceRegistryExternalAdapterFreezeTest` (+4) | Exactly 5 adapters registered and frozen; Mana/Stamina definitions resolve their registered adapters with `definitionVersion = 1`; full production query path (`PlayerResourceService.INSTANCE.query(null, MANA/STAMINA)`) returns `STATE_UNAVAILABLE_ON_THIS_SIDE` structurally, not an exception — exercised against the *real* production adapter, no fake/mock. |
| Adapter behavior | `ManaResourceAdapterTest`, `StaminaResourceAdapterTest` (new, 16 each) | `normalize()` pure logic: full, partial, zero, dynamic/bonus maximum, current-above-maximum passthrough (unclamped), uninitialized → `STATE_UNINITIALIZED`, zero/negative maximum → `MALFORMED_OWNER_STATE` (reachable, unlike Breath), negative-current-other-than-sentinel → `MALFORMED_OWNER_STATE` (defensive, unreachable through the real read path — documented as such), exact integer (no float conversion), unit scale = 1, resource id preserved, `supportedOperations()`/`clientMirrorMode()`, adapter id constant, `BASE_MAX_MANA`/`BASE_MAX_STAMINA` = 100. |
| Lazy initialization | `ManaResourceAdapterTest`/`StaminaResourceAdapterTest`'s `uninitializedRawCurrentProducesStateUninitializedFailure` + the adapter's own design (§9) | Proves deterministically, at the pure-function level, that an uninitialized input produces a structured failure, never a fabricated max-value snapshot. The adapter's `snapshot()` method itself (which is what actually never touches the mutating legacy getters) is verified by code review + the manual smoke-test checklist, matching established precedent for methods requiring a real `ServerPlayer`. |
| Maximum formulas | `PlayerStatsCharacterizationTest` (extended, +1) | Already pinned `getMaxStaminaBonus`/`getMaxManaBonus` = END/INT modifier × 10 (not × 5) before this phase; added `negativeEndAndIntModifiersProduceNegativeStaminaAndManaBonuses` proving a sufficiently negative modifier produces a negative bonus — the concrete evidence behind §12's "MALFORMED_OWNER_STATE is reachable in practice" claim. |
| Query service | `PlayerResourceServiceTest` (interface-adaptation only, 0 net new; existing coverage of routing/validation/operation-support enforcement applies unchanged to the new `ResourceQueryResult`-returning contract) | Renamed two tests to reflect the new contract (`nullOptionalReferenceFromAdapter...` → `nullResultFromAdapter...`, `emptyAdapterSnapshot...` → `declinedAdapterSnapshot...`); added `mismatchedResourceIdInFailureReasonProducesCorruptAdapterSnapshotFailure` (an adapter's `Failure` naming a different resource than queried is not trusted as-is). |
| External-state safety | `PlayerResourceStateComponentExternalSafetyTest` (+6), `PlayerResourceStateComponentExternalEntryPathTest` (+4, plus 2 existing tests extended) | Full Mana/Stamina coverage mirroring the existing Health/Food/Breath tests exactly — see §15. |
| Presentation | `ResourceValueFormatterRegistryTest` (+1) | Mana/Stamina both have an `IDENTITY` formatter registered (unlike Breath, which deliberately has none). |

**Test count: 240 → 287** (47 new/net-added tests). **Superseded by the correction pass: 287 → 301** — see "Correction Pass Addendum" at the end of this report.

**No runtime coverage is claimed that was not actually executed.** `snapshot()`'s `ServerPlayer`-branch logic (the `ResourceComponents.maybeGet` call, the `instanceof ServerPlayer` check, and the full end-to-end read against a real `PlayerResourceComponent`) is not exercised by an automated test — constructing a real `ServerPlayer` outside the Minecraft runtime is impractical, matching every prior phase's established, stated limitation (`HealthResourceAdapterConversionTest`'s own Javadoc). The pure `normalize()` core, which contains 100% of the adapter's actual decision logic, is fully tested; the thin wrapper around it (§9's snippet) is verified by code review and is the subject of the manual smoke-test checklist (§20).

## 17. Performance and side effects

No new tick loop, per-player polling loop, packet, or continuously-running event listener was added. `PlayerManaManager.getMaxMana`/`PlayerStaminaManager.getMaxStamina` (called once per query, per resource, **only when the legacy component is actually initialized — see the correction pass addendum**) post a `MaxManaCalcEvent`/`MaxStaminaCalcEvent` — this is the same event-dispatch-during-computation pattern `HealthResourceAdapter`'s call to `player.getMaxHealth()` already relies on (vanilla attribute modifier computation), not a new or different category of side effect, and no listener anywhere currently mutates player state in response (confirmed — no masteries are registered against these events yet). Querying one resource calculates only that resource's snapshot; no cross-resource computation occurs. No query writes any state.

## 18. Documentation

Updated `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md` (Phase 2C Implementation Record + Final Status sections). This report is new. The closed canonical design document (`TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`) was not modified.

## 19. Validation

- `./gradlew compileJava` — **BUILD SUCCESSFUL**.
- `./gradlew test` — **287/287 passed, 0 failures.**
- `./gradlew runDatagen` — **BUILD SUCCESSFUL**, `Caching: total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0`. `git status --short` confirmed byte-identical before/after.
- `./gradlew build -x runDatagen` — **BUILD SUCCESSFUL**.
- `git diff --cached` — empty throughout; nothing staged or committed at any point in this phase.

## 20. Manual smoke-test checklist (for Stefan — not executed by Claude Code; no GUI/game-client access available in this session)

1. Launch Minecraft 26.2 and join the existing test world.
2. Confirm no Resource API error appears in logs at any point.
3. Confirm Mana current/max display is unchanged from before this phase.
4. Spend Mana through an existing feature (e.g. casting a spell).
5. Confirm Mana decreases and regenerates exactly as before.
6. Confirm Stamina display is unchanged.
7. Sprint and confirm Stamina drain is unchanged.
8. Stop sprinting and confirm regeneration resumes as before.
9. Test one additional Stamina spender (flight, bow draw, melee attack, Power Sprint, or an ability) and confirm the cost is unchanged.
10. Confirm combat Exhaustion still affects Stamina regeneration exactly as before (0.5×/0.75×/1.0× multiplier at the appropriate states).
11. Save and exit with partial Mana and Stamina.
12. Reopen the world and confirm the existing persistence behavior is unchanged (Mana/Stamina round-trip via NBT exactly as before — no new behavior expected or required here).
13. Change dimensions and confirm existing behavior (reset to uninitialized, then lazily reinitialized to full on next legacy-manager read) is unchanged.
14. Die and respawn; confirm existing behavior (same reset-then-reinitialize pattern) is unchanged.
15. Confirm Health, Food, and Breath remain unchanged (no regression from this phase's shared-file changes to `PlayerResourceService`/`ExternalPlayerResourceAdapter`/`ResourceQueryFailureReason`).
16. Confirm no duplicate Mana/Stamina bars or packets are visible/observable.
17. Confirm no new Totality HUD element related to the Resource API's Mana/Stamina definitions appears anywhere (none should — none was added).
18. Exit cleanly and inspect logs for anything related to `totality:mana`, `totality:stamina`, `ManaResourceAdapter`, or `StaminaResourceAdapter` — none expected.

Long Rest restoration is explicitly **not** part of this phase and is not expected to be present.

**Status:** ~~not executed by Claude Code (no GUI/display or game-client tool available in this session)~~ **SUPERSEDED — executed by Stefan on the real 26.2 client and PASSED.** See "Final Status — Ready to Commit" at the end of this report for the full result.

## 21. Known limitations

- As with every prior phase, `snapshot()`'s `ServerPlayer`-branch logic is not exercised by an automated test — only the pure `normalize()` core is (see §16's closing paragraph).
- `getMaxMana`/`getMaxStamina` resolving to zero-or-below is real and reachable (unlike Breath's purely-defensive case), but requires an extreme, likely rarely-if-ever-reached ability-score modifier in current gameplay balance — documented, not treated as unreachable.
- No presentation formatter decision beyond `IDENTITY` was made — if a future HUD wants Mana/Stamina displayed through the Resource API rather than the current bespoke path, `IDENTITY` is already lossless and ready, but nothing currently consumes it.

## 22. Explicit scope exclusions confirmed absent from the diff

No Mana/Stamina formula, spender, regeneration rule, multiplier, pause condition, NBT key, packet, client cache, or HUD reader was changed. No generic mutation (spend/restore/drain/set/transaction) exists for either resource. No generic synchronization, client resource manager, revision counter, prediction reconciliation, HUD/menu migration, or packet removal was implemented. No Long Rest restoration was added. No Rage or spell-slot definition was registered. Confirmed by code review of the full diff (§6–§14) and by the test suite in §16 explicitly proving no generic state can ever be created for `totality:mana`/`totality:stamina` and that registration alone changes no gameplay behavior.

## 23. Recommended next Resource API slice (recommendation only, not implemented)

**Phase 2D: Standard spell-slot legacy adapter**, followed by **Phase 2E: Rage/charge-pool legacy adapter** — both using this same transitional, query-only, legacy-store adapter pattern established by Phase 2C. Only after all existing-store adapters (Mana, Stamina, standard spell slots, Rage) are accepted should generic synchronization/client presentation begin, per the readiness audit's own sequencing. Neither Phase 2D nor Phase 2E is designed or implemented here — recommendation only.

---

## Correction Pass Addendum (2026-07-19, same day)

A narrowly scoped correction pass, driven by review of the original implementation above, fixed two issues.

### 1. Maximum computed before the initialized check

**Problem:** `ManaResourceAdapter.snapshot`/`StaminaResourceAdapter.snapshot` correctly read the legacy component's initialized status, but then unconditionally called `PlayerManaManager.getMaxMana`/`PlayerStaminaManager.getMaxStamina` — even when the query was about to fail with `STATE_UNINITIALIZED` anyway. Those methods post a `MaxManaCalcEvent`/`MaxStaminaCalcEvent`. The audit found no listener that mutates player state in response, but an event dispatch is still an unnecessary observable side effect for a query that cannot produce a snapshot.

**Fix:** both adapters now route through a new package-visible `resolve` method:

```java
// ManaResourceAdapter (StaminaResourceAdapter is identical, substituting Stamina's own types)
@Override
public ResourceQueryResult snapshot(Player player, PlayerResourceDefinition definition) {
    if (!(player instanceof ServerPlayer serverPlayer)) {
        return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, definition.id());
    }
    var component = ResourceComponents.maybeGet(serverPlayer);
    if (component.isEmpty()) {
        return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, definition.id());
    }
    return resolve(definition.id(), component.get(), () -> PlayerManaManager.getMaxMana(player), definition.unitScale());
}

static ResourceQueryResult resolve(Identifier resourceId, PlayerResourceComponent component, IntSupplier maximumSupplier, long unitScale) {
    if (!component.isManaInitialized()) {
        return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNINITIALIZED, resourceId);
    }
    return normalize(resourceId, component.getMana(), maximumSupplier.getAsInt(), unitScale);
}
```

`resolve` checks `isManaInitialized()`/`isStaminaInitialized()` **first** and returns `STATE_UNINITIALIZED` immediately — the `maximumSupplier` (a lambda wrapping `PlayerManaManager.getMaxMana(player)`/`PlayerStaminaManager.getMaxStamina(player)`) is never invoked for uninitialized state. The pure `normalize(Identifier, int current, int liveMaximum, long unitScale)` core was narrowed to take an already-known-initialized `int` instead of an `OptionalInt` — `resolve` is its only production caller, and the uninitialized case never reaches it.

**Testability:** `IntSupplier` was chosen specifically so a test can substitute a plain counting lambda for the real (Minecraft-runtime-dependent) maximum calculation — no mocking framework needed. New tests (`ManaResourceAdapterTest`/`StaminaResourceAdapterTest`, 4 each):

- `resolveNeverInvokesTheMaximumSupplierWhenUninitialized` — a fresh `PlayerResourceComponent` (via the existing `new PlayerResourceComponent(null)` construction pattern) plus a counting `IntSupplier`; asserts the counter is `0` after `resolve` returns `STATE_UNINITIALIZED`.
- `resolveInvokesTheMaximumSupplierExactlyOnceWhenInitialized` — a component with `setMana(42)`/`setStamina(41)` called first; asserts the counter is exactly `1` and the returned snapshot's current/maximum are correct.
- `resolvePreservesCurrentAndDynamicMaximumForInitializedState` — a dedicated test confirming the exact current/maximum values pass through unchanged.
- `resolveDoesNotMutateTheComponent` — calls `resolve` against both an initialized and a fresh (uninitialized) component, then asserts neither component's stored value changed and the uninitialized one is still uninitialized afterward.

### 2. Adapter-returned failure reasons were not constrained

**Problem:** `PlayerResourceService.queryExternal` checked that a returned `Failure`'s `resourceId` matched the definition queried, but trusted *any* `ResourceQueryFailureReason` at that point. Several reasons, however, are registry/service/generic-state-layer determinations an adapter has no authority or visibility to make about itself: `RESOURCE_NOT_REGISTERED`, `ADAPTER_NOT_REGISTERED`, `STATE_NOT_INSTANTIATED`, `UNSUPPORTED_MODEL`, `OPERATION_UNSUPPORTED`, `MAXIMUM_UNAVAILABLE`, and `CORRUPT_ADAPTER_SNAPSHOT` itself.

**Fix:** a new `PlayerResourceService.ALLOWED_ADAPTER_FAILURE_REASONS` set (`MALFORMED_OWNER_STATE`, `STATE_UNINITIALIZED`, `STATE_UNAVAILABLE_ON_THIS_SIDE`) is now checked alongside the resource-id match:

```java
if (!failure.resourceId().equals(definition.id())
        || !ALLOWED_ADAPTER_FAILURE_REASONS.contains(failure.reason())) {
    return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
}
return failure;
```

An adapter returning any reason outside that set — even with a matching `resourceId` — is treated as malformed output and turned into `CORRUPT_ADAPTER_SNAPSHOT`, naming the resource actually queried. New tests in `PlayerResourceServiceTest` (10 total): three prove all three allowed reasons propagate unchanged (`malformedOwnerStateFromAdapterPropagatesUnchanged`, `stateUninitializedFromAdapterPropagatesUnchanged`, `stateUnavailableOnThisSideFromAdapterPropagatesUnchanged`); five prove each disallowed reason becomes `CORRUPT_ADAPTER_SNAPSHOT` (`resourceNotRegisteredReturnedByAnAdapterBecomesCorrupt`, `adapterNotRegisteredReturnedByAnAdapterBecomesCorrupt`, `stateNotInstantiatedReturnedByAnAdapterBecomesCorrupt`, `maximumUnavailableReturnedByAnAdapterBecomesCorrupt`, `operationUnsupportedReturnedByTheAdapterItselfBecomesCorrupt` — the last one specifically distinguishing "the service's own pre-invocation `OPERATION_UNSUPPORTED` check" from "the adapter claiming that reason from inside `snapshot()`, which is self-contradictory since it was only invoked because that check already passed"); the pre-existing `mismatchedResourceIdInFailureReasonProducesCorruptAdapterSnapshotFailure` and `declinedAdapterSnapshotProducesMalformedOwnerStateFailure` tests continue to pass unchanged, confirming successful snapshots and the resource-id check remain unaffected.

`ExternalPlayerResourceAdapter`'s class and `snapshot()` Javadoc were rewritten to reflect the current mixed adapter set (Health/Food/Breath answerable on either side; Mana/Stamina transitionally server-only) and to explicitly enumerate the three allowed reasons, replacing wording that described the interface only in terms of the original Phase 2A Health/Food pair. The Javadoc also now instructs that a future genuinely-new adapter failure mode should get its own deliberately-added, documented `ResourceQueryFailureReason` (added to the allowed set) rather than reusing one of the unrelated service-owned reasons to approximate it.

### 3. Preserved behavior (confirmed unchanged by this correction pass)

Mana/Stamina storage, sentinel values, formulas, regeneration, costs, packets, client caches, HUD readers, persistence, death/respawn/dimension/logout behavior, Health/Food/Breath behavior, and all presentation metadata are all unchanged. The production registries remain exactly five definitions and five adapters, confirmed by the unmodified registration tests continuing to pass.

### 4. Build and test results (this pass)

- `./gradlew compileJava` — **BUILD SUCCESSFUL**.
- `./gradlew test` — **301/301 passed, 0 failures** (287 → 301: 14 new tests — 8 for the maximum-supplier-invocation ordering, 6 for the allowed/rejected adapter-failure-reason set).
- `./gradlew runDatagen` — **BUILD SUCCESSFUL**, `written: 0`, confirmed byte-identical `git status` before/after.
- `./gradlew build -x runDatagen` — **BUILD SUCCESSFUL**.
- `git diff --cached` — empty throughout this pass.

### 5. Manual smoke test

Not re-executed in this pass at the time this addendum was originally written (no GUI/game-client tool available in this session, same as the original implementation pass) — this correction pass changed no gameplay-observable behavior (both fixes are internal to the query path: an avoided-but-previously-harmless event dispatch, and a stricter internal trust boundary between the service and its adapters), so the same 18-item checklist recorded in §20 above still applied unchanged. **Since superseded — see "Final Status — Ready to Commit" below: Stefan has since run the manual smoke test on the real 26.2 client and it passed.**

### 6. Genuinely unresolved issues

None identified by this correction pass.

---

## Final Status — Ready to Commit (2026-07-19)

This section is the authoritative, current status of Phase 2C, superseding every earlier "not executed"/"pending" manual-test statement in this report (§20, Correction Pass Addendum §5).

### Manual smoke test — PASSED

Stefan manually tested the final Phase 2C implementation (301/301 automated tests, post correction pass) on the real Minecraft 26.2 client against the existing test world. Stefan's reported result, in full: **"Manual smoke testing passed. Mana and Stamina behaved exactly as before, with no visible gameplay changes or regressions."**

This is a general/overall confirmation, not an item-by-item walkthrough of the 18-item checklist in §20 — no individual checklist item (specific spender, specific regen pause condition, dimension travel, death/respawn, logout/rejoin, etc.) was separately itemized in Stefan's report. Treat this as a passed playtest of Mana/Stamina's overall observable behavior on the test world used, on this one pass — not exhaustive per-item verification of every line of the checklist.

### Final Phase 2C status

- **Automated tests:** 301/301 passed (`./gradlew test`).
- **Compile:** succeeded (`./gradlew compileJava`).
- **Datagen:** wrote 0 files (`./gradlew runDatagen`, `written: 0`).
- **Full build:** succeeded (`./gradlew build -x runDatagen`).
- **Manual smoke test:** passed (general confirmation; see above).
- **Phase 2C is ready to commit.**

### Scope boundaries carried forward unchanged

Mana and Stamina remain owned by their existing legacy managers/component/packets/HUD readers — nothing about their current gameplay behavior changed. The Resource API can now query both on the authoritative server through `ManaResourceAdapter`/`StaminaResourceAdapter`, transitionally, at `definitionVersion = 1`. The canonical closed design (`TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`) was not reopened or modified to record this status.
