> This is the review-bundle copy of the Phase 2A implementation report, including the Correction Pass Addendum, Cleanup Pass Addendum, and the final "Final Status — Ready to Commit" section recording the passed manual smoke test. The canonical, up-to-date copy lives at `Context/Audit/TOTALITY_RESOURCE_API_PHASE_2A_HEALTH_FOOD_IMPLEMENTATION_REPORT.md`.

# TOTALITY RESOURCE API — PHASE 2A: HEALTH & FOOD EXTERNAL ADAPTERS — IMPLEMENTATION REPORT

**Date:** 2026-07-19 (original); correction pass 2026-07-19 (same day, see "Correction Pass Addendum" at the end of this document)
**Branch:** `feature/general-resource-api`
**Starting commit:** `debfcee39b9ba679bf4fc97a495de19594d7c85e` ("Add generic player resource API foundation")
**Scope:** `src/main/java/zcylas/totality/**` and `src/test/java/zcylas/totality/**` only. `/Inspiration Mods` was excluded from every search, audit, status conclusion, and artifact, per the task instructions.
**Status:** **READY TO COMMIT** — automated tests pass (214/214), build succeeds, and the manual smoke test has passed on the real 26.2 client. See **"Final Status — Ready to Commit"** at the very end of this document for the current, authoritative summary. That section supersedes every earlier "pending"/"not executed" manual-test statement anywhere else in this file. The sections below (1–15) are preserved as the original implementation record; the "Correction Pass Addendum" and "Cleanup Pass Addendum" that follow record two later fix passes — all three are historical layers leading up to the final status at the end.

---

## 1. Exact scope

This phase implements a narrow, query-only slice of Phase 2 of `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` (§27 "Phase 2 — Adapters over existing resources"), restricted to exactly:

1. Health external query integration (`totality:health`, `EXTERNAL_ADAPTER`).
2. Food external query integration (`totality:food`, `EXTERNAL_ADAPTER`).
3. Shared display conversion and formatting infrastructure (`ResourceDisplayConversion`, `ResourceValueFormatter`, `ResourceValueFormatterRegistry`).
4. Hunger's ×5 player-facing presentation (native `0–20` → displayed `0–100`).
5. Proof that these two external resources never create duplicate Generic-component state.

**Not implemented** (explicit scope exclusions, all confirmed absent from the diff): Health/Food mutation through the generic service, damage/healing/eating operations, `ExternalResourceChangeBridge`, generic Health/Food packets, client prediction, transaction support, modifiers, maximum resolvers beyond reading the authoritative owner, regeneration strategies, Rest integration, Hit Dice, Breath, Temperature, Mana/Stamina/Rage/spell-slot migration, contextual HUD layout, the HUD overlap fix, or any dormant resource definition.

**Current later decisions honored:** Temperature was not implemented or registered. No Temperature stub adapter was created. Breath was not registered or implemented — it remains the recommended next patch (§14 below). No Stamina, Mana, Rage, or spell-slot migration occurred. Natural HP regeneration, Long/Short Rest healing, Food balance, starvation, and eating rules were not touched.

---

## 2. Files created

`src/main/java/zcylas/totality/api/rpg/resources/`
- `PlayerResourceIds.java` — stable `totality:health`/`totality:food` resource+adapter identifier constants.
- `ResourceSnapshot.java` — read-only current/max/unitScale record returned by a query.
- `ResourceQueryFailureReason.java` — the five structured failure reasons a query can return.
- `ResourceQueryResult.java` — sealed `Success(ResourceSnapshot)` / `Failure(reason, id)`.
- `PlayerResourceService.java` — the query-only façade; routes `EXTERNAL_ADAPTER` definitions to their adapter and `GENERIC_COMPONENT` definitions to `PlayerResourceStateComponent`.
- `ProductionResourceDefinitions.java` — registers both adapters, both definitions, and both formatters, then freezes all three registries in order; called once from `Totality.registerApi()`.

`src/main/java/zcylas/totality/api/rpg/resources/external/`
- `ExternalPlayerResourceAdapter.java` — the query-time adapter contract, typed against `Player` (see §3).
- `ExternalPlayerResourceAdapterRegistry.java` — instantiable registry (production singleton `INSTANCE` + isolated test instances), matching `PlayerResourceRegistry`'s Phase 1 shape.
- `ExternalResourceOperationSupport.java` — `QUERY`/`RESTORE`/`DRAIN`/`SET` declaration enum; Health/Food declare only `QUERY`.
- `ExternalResourceClientMirrorMode.java` — `NATIVE_SYNCHRONIZATION`/`GENERIC_SYNCHRONIZATION`; Health/Food declare `NATIVE_SYNCHRONIZATION`.
- `HealthResourceAdapter.java` — wraps `Player.getHealth()`/`getMaxHealth()`, fixed-point unit scale 1000.
- `FoodResourceAdapter.java` — wraps `Player.getFoodData().getFoodLevel()`, unit scale 1, native maximum 20.

`src/main/java/zcylas/totality/api/rpg/resources/presentation/`
- `ResourceDisplayConversion.java` — rational, checked, deterministic-rounding mechanical→display conversion.
- `ResourceDisplayType.java`, `ResourceHudRole.java` — presentation enums (canonical §19.3/§19.4; only `BAR`/`CORE_CONSTANT` used this phase).
- `ResourcePresentationDefinition.java` — the minimal presentation metadata record attached to a `PlayerResourceDefinition`.
- `ResourceValueFormatter.java` — the one authoritative display-value converter interface, plus `ofConversion(...)` factory.
- `ResourceValueFormatterRegistry.java` — instantiable registry (production singleton + isolated test instances).

`src/test/java/zcylas/totality/api/rpg/resources/`
- `TestResourceBootstrap.java` — idempotent test-only helper that triggers `ProductionResourceDefinitions.register()` exactly once per test JVM (production code only registers via `Totality.onInitialize()`, which never runs under plain JUnit).
- `PlayerResourceRegistryExternalAdapterFreezeTest.java`, `PlayerResourceServiceTest.java`, `PlayerResourceStateComponentExternalSafetyTest.java` — see §9.

`src/test/java/zcylas/totality/api/rpg/resources/external/`
- `ExternalPlayerResourceAdapterRegistryTest.java`, `HealthResourceAdapterConversionTest.java`, `FoodResourceAdapterConversionTest.java`.

`src/test/java/zcylas/totality/api/rpg/resources/presentation/`
- `ResourceDisplayConversionTest.java`, `ResourceValueFormatterRegistryTest.java`.

## 3. Files modified

- `src/main/java/zcylas/totality/Totality.java` — one line added to `registerApi()`: `ProductionResourceDefinitions.register();`, placed last in that method so every other Phase 1 foundation class has already loaded.
- `src/main/java/zcylas/totality/api/core/rpgutils/RpgDisplayUtils.java` — `toDisplayHp` now delegates to `HealthResourceAdapter.toUnits` + `ResourceDisplayConversion.HEALTH_FOOD` instead of `Math.round(vanillaHp * 5)`. `toVanillaHp`, `conModifierToVanillaHp`, `formatHp`, `endModifierToStaminaBonus`, `intModifierToManaBonus` are unchanged. No method was deleted.
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceDefinition.java` — added `Optional<ResourcePresentationDefinition> presentation` field (record + builder + null-check); the two direct-constructor tests in `PlayerResourceRegistryTest` were updated for the new arity.
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistry.java` — added `freeze(ExternalPlayerResourceAdapterRegistry)` overload that validates every `EXTERNAL_ADAPTER` definition resolves a registered adapter before freezing; the original zero-arg `freeze()` is unchanged and still used by every Phase 1 test that has no external definitions.
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponent.java` — `instantiateScalar`/`instantiatePartitioned` now reject `EXTERNAL_ADAPTER`-authority ids; `readLiveEntry`/`readOrphanEntry` now quarantine (rather than restore) persisted generic-component data for an id that is now `EXTERNAL_ADAPTER`-authority; extracted the shared package-visible predicate `isRegisteredExternalAdapterAuthority`. See §8.
- `src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java` — HP and Hunger bar text now go through `PlayerResourceService.query(...)` + the registered formatter (with a same-value fallback); bar fill ratios, smoothing, sprites, layout, and animation are unchanged. See §7.
- `src/main/java/zcylas/totality/screen/inventory/InventoryItemDetail.java` — the "Nutrition" tooltip line now routes through the `totality:food` formatter's `toDisplayDelta`; "Saturation" is unchanged/unconverted.
- `src/test/java/zcylas/totality/api/core/rpgutils/HungerDisplayCharacterizationTest.java` — rewritten: the Phase 1 "no Food conversion exists yet" characterization is superseded; now confirms the conversion exists via the shared formatter registry (not via `RpgDisplayUtils`).
- `src/test/java/zcylas/totality/api/core/rpgutils/RpgDisplayUtilsCharacterizationTest.java` — added an exhaustive (0.0–200.0 in 0.1 steps, freshly computed per sample) equivalence test proving the refactored `toDisplayHp` matches the pre-refactor raw formula for every realistic value, plus explicit fractional/half-point cases.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java` — the Phase 1 `productionSingletonHasNoDefinitionsInThisPatch` test is replaced with `productionSingletonContainsExactlyHealthAndFoodInPhase2A` and a new `productionSingletonHasNoTemperatureOrBreathDefinition` test.

No other file — including every pre-existing generated JSON, `src/main/generated/.cache/`, and the `Context/Trading Test/*.png` files — was modified.

---

## 4. External adapter architecture

`ExternalPlayerResourceAdapter` is typed against `net.minecraft.world.entity.player.Player`, **not** `ServerPlayer` as canonically illustrated. This is a deliberate, documented deviation (see the class Javadoc): Health and Food both read values vanilla already keeps synchronized to the client through its own native packets (`ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION`) — `player.getHealth()`/`getMaxHealth()`/`getFoodData()` are correct and already-synchronized on `LocalPlayer` exactly as on `ServerPlayer`. Typing against the common `Player` superclass lets `PlayerResourceService.query(...)` serve both a future authoritative server caller and the client HUD through one query path, with no second client-only surface and no duplicate packet. This is safe specifically because Phase 2A is query-only; a future mutating adapter (restore/drain/set) will need to re-narrow to `ServerPlayer` for those operations, since only the server may authoritatively mutate.

`ExternalPlayerResourceAdapterRegistry` mirrors `PlayerResourceRegistry`'s Phase 1 shape exactly: instantiable (not a pure static singleton) so tests get isolated state; production code uses `INSTANCE`. `register()` rejects a null adapter, a null id, and any registration after `freeze()`; duplicate ids are rejected without replacing the existing entry.

Both adapters declare `supportedOperations() = {QUERY}` and `clientMirrorMode() = NATIVE_SYNCHRONIZATION` — no generic Health/Food packet is introduced, and no mutation method is implemented.

---

## 5. Query service architecture

`PlayerResourceService.query(Player, Identifier)`:
1. Resolves the definition from the registry; an unknown id returns `Failure(RESOURCE_NOT_REGISTERED)`.
2. `EXTERNAL_ADAPTER` definitions route to `adapters.get(externalAdapterId)`, then `adapter.snapshot(player, definition)`. A missing adapter (defensive — registry-freeze validation should already prevent this) returns `Failure(ADAPTER_NOT_REGISTERED)`.
3. `GENERIC_COMPONENT` definitions route to `PlayerResourceStateComponent` via the package-visible pure core `queryGenericState(definition, state)` — resolvable only for a `ServerPlayer` in production (`Failure(STATE_UNAVAILABLE_ON_THIS_SIDE)` for a client-side player); an uninstantiated resource returns `Failure(STATE_NOT_INSTANTIATED)` without ever calling `instantiateScalar`; a `PARTITIONED_POOL` definition returns `Failure(UNSUPPORTED_MODEL)` (no partitioned resource is registered by this phase).
4. Never writes NBT, never sends a packet, never mutates Health, Food, or any generic state.

No production `GENERIC_COMPONENT` resource is registered by Phase 2A, so this routing branch is exercised only by `PlayerResourceServiceTest`'s isolated fake-definition tests, using the same `new PlayerResourceStateComponent(null)` pattern Phase 1 tests already established for exercising this component without a real `ServerPlayer`.

---

## 6. Health fixed-point scale and rounding

`HealthResourceAdapter.UNIT_SCALE = 1000L` (registered as the `totality:health` definition's `unitScale`). `toUnits(float mechanicalHealth, long unitScale)`:
```java
double scaled = Math.round((double) mechanicalHealth * (double) unitScale);
```
— widens to `double` before rounding (never `(long) (mechanicalHealth * unitScale)`, which would truncate toward zero), rejects non-finite input, and range-checks before narrowing to `long`.

`ResourceDisplayConversion.convertUnitsToDisplay(units, unitScale)` computes `units × numerator / (unitScale × denominator)` entirely in integer arithmetic (`Math.multiplyExact`/`Math.addExact`/`Math.negateExact`), rounding half-away-from-zero. Verified: `20.0 → 100`, `10.0 → 50`, `0.2 → 1`, `10.5 → 53`, `0.5 → 3`; an exhaustive 0.0–200.0-in-0.1-steps comparison against the pre-refactor raw `Math.round(hp * 5)` formula passes for every sample (computed fresh per iteration, not accumulated, to avoid float-drift artifacts). Dynamic maximums (e.g. a CON-bonus-scaled `24.0`/`40.0` max HP) convert correctly since the conversion has no hardcoded baseline.

---

## 7. Food mechanical/display mapping

Food uses `unitScale = 1` (already integral — `getFoodLevel()` is an `int` 0–20, no fractional Food value exists anywhere in vanilla or Totality). `FoodResourceAdapter.NATIVE_MAXIMUM = 20` (vanilla's hardcoded ceiling; there is no `getMaxFoodLevel()`). Saturation and exhaustion are never read by the adapter — they stay owner-specific metadata, absent from `ResourceSnapshot` by construction (verified structurally in `FoodResourceAdapterConversionTest.saturationAndExhaustionHaveNoFieldOnResourceSnapshot`). Native `0/6/16/20` → displayed `0/30/80/100`, verified directly.

---

## 8. Registry contents and proof of no duplicate state

`PlayerResourceRegistry.INSTANCE` contains exactly two definitions after `ProductionResourceDefinitions.register()`: `totality:health` and `totality:food`, both `EXTERNAL_ADAPTER`-authority, both frozen. No Temperature or Breath definition exists (asserted directly). `ExternalPlayerResourceAdapterRegistry.INSTANCE` contains exactly `HealthResourceAdapter.INSTANCE`/`FoodResourceAdapter.INSTANCE`, frozen. `ResourceValueFormatterRegistry.INSTANCE` contains exactly the two `5/1` formatters, frozen.

Duplicate-state prevention (canonical §4.2's "their values are not persisted inside `PlayerResourceStateComponent`"):
- `PlayerResourceStateComponent.instantiateScalar`/`instantiatePartitioned` call a new shared predicate, `isRegisteredExternalAdapterAuthority(id)`, and throw `IllegalArgumentException` before creating any state when it's true for either Health or Food.
- `readLiveEntry` (persisted `Resource_i` generic-component data) and `readOrphanEntry` (persisted `Orphaned_i` data, on definition-return) both consult the same predicate: stale/malformed generic NBT for an id that is now `EXTERNAL_ADAPTER`-authority is quarantined into `orphanedStates` (logged) rather than restored into live `states`, so it can never become queryable generic state and never overrides the adapter.
- Registering the definitions is pure metadata registration — a freshly constructed `PlayerResourceStateComponent` has zero instantiated/orphaned entries for Health or Food regardless (`registrationAloneCreatesNoPlayerState` test), and `writeData()` only ever iterates the live/orphaned maps, so an ordinary player's NBT contains zero `Resource_i`/`Orphaned_i` entries for either resource.
- Querying Health or Food always resolves through `queryExternal` → the adapter; `queryGeneric`/`queryGenericState` is structurally unreachable for either id since their `stateAuthority()` is `EXTERNAL_ADAPTER`, not `GENERIC_COMPONENT`.

---

## 9. HUD and tooltip consumers changed

**`TotalityHudRenderer`** — a new private helper, `resourceDisplayCurrentMax(Player, Identifier, fallbackCurrent, fallbackMax)`, queries `PlayerResourceService.INSTANCE` and the registered formatter, falling back to the pre-existing values (never a fabricated zero) if unavailable. Both the HP and Hunger bar draw calls now source their displayed numeric current/max through this helper:
- HP: bar fill (`hpSmooth`, computed from native `hp/maxHp`) is unchanged; displayed numbers are queried through the service and match `RpgDisplayUtils.toDisplayHp` exactly (same underlying conversion).
- Hunger: bar fill (`hungerSmooth`, computed from native `hunger/20.0`) is unchanged; displayed numbers now show `0–100` instead of raw `0–20`.
- Sprites, position, size, animation, smoothing, and the AC indicator/context renderers are untouched.

**`InventoryItemDetail`** — the "Nutrition" line now shows `formatter.toDisplayDelta(food.nutrition(), 1)` instead of the raw `food.nutrition()` int (a mechanical `6` now displays `30`). "Saturation" is unchanged. `InventoryActionHandler.needsFood()`'s eat-gate check was not touched.

---

## 10. Tests and command results

**164 tests, 0 failures** (up from Phase 1's 100), across the additions described in §2 plus the modifications in §3. New coverage by area:
- External adapter registry: unique/duplicate/null/malformed registration, safe lookup, freeze, isolated-registry non-contamination.
- Cross-registry validation: adapter resolution success, missing-adapter freeze failure, `GENERIC_COMPONENT` definitions needing no adapter, production Health/Food resolving their real registered adapters.
- Query service: external routing (fake adapter, deterministic snapshot), generic routing (pure core, no adapter reached), unknown-resource structured failure, no-instantiation-on-query, no-mutation-on-repeated-query, `PARTITIONED_POOL` explicit unsupported-model failure.
- Health conversion: all six required precision cases, dynamic maximum, round-trip stability, explicit truncation-vs-rounding proof, non-finite rejection.
- Food conversion: `0/6/16/20 → 0/30/80/100`, structural absence of saturation/exhaustion on `ResourceSnapshot`, presentation-only proof (mechanical snapshot values stay un-converted).
- External-state safety: instantiate-rejection for both resources (scalar and partitioned), the shared predicate directly, zero-state-on-registration, zero-generic-NBT-entries.
- Presentation: `5/1` validation, zero/negative denominator rejection, checked overflow, deterministic rounding, Health-compatibility exact match (exhaustive), Food delta `6 → 30`, deterministic pair formatting.

```
./gradlew compileJava   → BUILD SUCCESSFUL
./gradlew test          → BUILD SUCCESSFUL (164 tests, 0 failures)
./gradlew runDatagen    → BUILD SUCCESSFUL, "written: 0" (349 cached files, 0 changed)
./gradlew build -x runDatagen → BUILD SUCCESSFUL
```

No pre-existing generated JSON was modified further by any of these runs; no unrelated file changed.

---

## 11. Manual smoke-test status

**SUPERSEDED — the manual smoke test has since been executed by Stefan and passed.** See "Final Status — Ready to Commit" at the very end of this document for the actual, current result. The paragraph below is preserved as the original record of this section's state at the time it was written.

~~**Not executed in this session** — this session has no GUI/display interaction tool available (no way to launch `runClient`, join a world, or visually inspect the HUD from this environment). Stated plainly rather than claimed. §14 in the task lists the exact checklist Stefan should run on the actual 26.2 client; it is reproduced in this report's companion checklist below.~~

---

## 12. Known limitations

**Partially SUPERSEDED — see the notes inline below; both the NBT-harness claim and the manual-test status in this original section are outdated.** Preserved as the original record otherwise.

- ~~NBT/network round-trip for the quarantine logic (`readLiveEntry`/`readOrphanEntry` reading real `ValueInput`) is not covered by an automated test — matching Phase 1's own honestly-documented limitation (no harness exists in this repo to construct `ValueInput`/`ValueOutput`/`RegistryFriendlyByteBuf` outside a running game).~~ **SUPERSEDED — the Correction Pass Addendum's §2 found this claim was actually wrong: `TagValueOutput`/`TagValueInput`/`RegistryFriendlyByteBuf` all construct cleanly without a running game, and `PlayerResourceStateComponentExternalEntryPathTest` now covers real NBT and sync round-trips directly.** The shared decision predicate (`isRegisteredExternalAdapterAuthority`) that both read paths and the instantiate-rejection guard consult is directly unit-tested; the field-reading code itself is now also covered by real-type NBT/sync tests, not only compilation and code review.
- `HealthResourceAdapter.snapshot`/`FoodResourceAdapter.snapshot` themselves (the two-line methods that actually call `player.getHealth()`/`getFoodData()`) are still not exercised by an automated test — constructing a real `Player` outside the Minecraft runtime remains impractical. The pure conversion logic each delegates to is fully tested; the adapter's own trivial field reads are covered only by the manual smoke test, which has since passed (see "Final Status — Ready to Commit" at the end of this document).
- ~~The manual smoke test itself has not yet been run this session (§11).~~ **SUPERSEDED — it has since been run and passed.**

---

## 13. Explicit scope exclusions (confirmed absent from the diff)

Health/Food mutation through the generic service; damage/healing/eating operations; external change events; `ExternalResourceChangeBridge`; generic Health/Food packets; client prediction; transaction support; modifiers; maximum resolvers beyond reading the authoritative owner; regeneration strategies; Rest integration; Hit Dice; natural-regeneration disabling; Breath; Temperature; Mana/Stamina/Rage/spell-slot migration; contextual HUD layout; the larger HUD overlap fix; dormant resource definitions.

---

## 14. Recommended next patch

**Breath external adapter** (per the task's explicit default), per the Oxygen-to-Breath audit addendum already on file in the readiness document: a strictly greenfield `EXTERNAL_ADAPTER` wrapping vanilla air supply, structurally identical to Health/Food — no existing storage, packet, identifier, or species hook to migrate. Temperature must not be reopened as a Resource API resource (current, standing decision).

If review of this phase surfaces a smaller necessary correction first (e.g. a rounding-edge case, a naming adjustment), that correction should take priority over starting Breath.

---

## Correction Pass Addendum (2026-07-19)

Driven by review of `TOTALITY_RESOURCE_API_PHASE_2A_REVIEW_BUNDLE.zip`. Scope stayed strictly within Phase 2A — no Breath, Stamina, Mana, Rage, mutation transactions, regeneration, or Rest integration work was started. Production registry still contains exactly `totality:health` and `totality:food`.

### 1. Health fixed-point conversion — real overflow detection

**Bug found:** `HealthResourceAdapter.toUnits` computed `Math.round((double) value * scale)` and then compared the *already-rounded* result against `Long.MIN_VALUE`/`Long.MAX_VALUE`. `Math.round(double)` itself saturates out-of-range input to those exact bounds instead of signalling overflow, so the comparison could never fire — the overflow guard was dead code.

**Fix:** `toUnits` now converts via `BigDecimal`: `new BigDecimal((double) mechanicalHealth)` captures the float's exact binary value (no truncation), multiplies by the scale as exact integer arithmetic, rounds with `RoundingMode.HALF_UP` (nearest unit, ties away from zero — the one documented rounding rule, applied consistently for positive and negative input), and calls `longValueExact()`, which genuinely throws `ArithmeticException` on overflow. `unitScale < 1` is now also explicitly rejected (it previously was not checked in `toUnits` at all).

**New tests** (`HealthResourceAdapterConversionTest`): `invalidUnitScaleIsRejected` (0 and −1), `zeroHealthConvertsToZeroUnits` (including `-0.0f`), `positiveHalfBoundaryRoundsAwayFromZero`/`negativeHalfBoundaryRoundsAwayFromZero` (exact ties, `0.0625f × 1000 = 62.5` exactly, since `0.0625 = 1/16` is binary-exact), `positiveOverflowIsRejectedNotSaturated`/`negativeOverflowIsRejectedNotSaturated` (`Float.MAX_VALUE`/`-Float.MAX_VALUE` genuinely throw), `valueImmediatelyInsideValidLongBoundaryIsAccepted`/`valueImmediatelyOutsideValidLongBoundaryIsRejected` (`2^62` succeeds, `2^63` — exactly `Long.MAX_VALUE + 1` — throws), and `nonFiniteHealthIsRejectedIncludingNegativeInfinity` (NaN, +∞, −∞ all rejected).

### 2. External-state entry paths — all four closed

**Gap found:** only `instantiateScalar`/`instantiatePartitioned` and the NBT read paths rejected `EXTERNAL_ADAPTER` ids. `applySyncPacket`, `writeSyncPacket`, `writeData`, and `copyFrom` did not defensively filter.

**Fixes** (`PlayerResourceStateComponent`):
- `applySyncPacket` now always fully consumes an entry's payload bytes first (buffer alignment for later entries never depends on the current entry's authority), then discards (does not insert into `states`) any entry whose id is `EXTERNAL_ADAPTER`-authority, logging once.
- `writeSyncPacket` and `writeData` both now filter `states` before writing, excluding any `EXTERNAL_ADAPTER` entry defensively (even though no production entry path can put one there).
- `copyFrom` now quarantines (into `orphanedStates`, via the new `stateToOrphan` inverse of `orphanToLiveState`) rather than copies any `EXTERNAL_ADAPTER` entry found in the source's live `states` map.
- Extracted the shared decision predicate `isRegisteredExternalAdapterAuthority(Identifier)` (package-visible) so all five call sites (`instantiateScalar`, `instantiatePartitioned`, `readLiveEntry`, `readOrphanEntry`, `applySyncPacket`, `writeSyncPacket`, `writeData`, `copyFrom`) share one rule.

**New tests, using real Mojang serialization types (no fabricated harness needed — see below), `PlayerResourceStateComponentExternalEntryPathTest`, 10 tests:**
- `sanityCheckOwnNbtHarnessRoundTripsAnOrdinaryGenericResource` — proves the test harness itself is sound.
- `nbtLoadingQuarantinesStaleGenericDataForHealth`/`...ForFood` — hand-built stale `Resource_0_*` NBT (the shape a pre-Phase-2A save would have) loads into `orphanedStates`, never `states`.
- `writeDataNeverWritesALiveHealthEntryEvenIfCorruptedIntoStates` — reflection-injected corruption (see below) proves `writeData`'s filter actually filters, not just that it's a no-op on an empty map.
- `applyingASyncPayloadCannotCreateLiveHealthGenericState` — hand-built sync payload; asserts no live state AND `buf.readableBytes() == 0` (full consumption).
- `externalEntryDoesNotCorruptParsingOfLaterSyncEntries` — a 2-entry payload (Health, then an ordinary resource) proves the ordinary entry still parses correctly after the discarded one.
- `syncWritingExcludesExternalLiveEntriesEvenIfCorruptedIntoStates` — reflection-injected corruption proves `writeSyncPacket`'s filter.
- `copyingAComponentWithCorruptedExternalLiveStateQuarantinesItRatherThanCopyingItLive` — reflection-injected corruption proves `copyFrom`'s quarantine path.
- `externalSnapshotsAlwaysComeFromTheAdapterNeverFromGenericState` / `healthAndFoodNeverAppearAsOrdinaryLiveComponentStatesOnAFreshComponent` — structural closing proofs.

**On the NBT/sync test harness:** the original report stated NBT/sync round-trip testing was impractical without a running game. That was **incorrect** — `TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING)` / `TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(Stream.of()), tag)` construct real, valid Mojang serialization objects with no registry or running server required (verified empirically this pass), and `RegistryFriendlyByteBuf` only needs `new RegistryFriendlyByteBuf(Unpooled.buffer(), null)` since `registryAccess()` is never called by any code path exercised here. This correction pass's NBT/sync tests use these real types directly — no reflection for the serialization plumbing itself. Reflection is used in exactly three tests, and only to inject a corrupted `states` map entry that no legitimate public API can produce (documented inline in each test).

### 3. Operation-support declarations — enforced, not decorative

**Fix** (`ExternalPlayerResourceAdapterRegistry.register`): now rejects a null operation-support set, an empty set, a set containing a null entry (checked by iteration, never `Set.contains(null)` — `Set.of(...)`, used by every adapter in this codebase, throws `NullPointerException` from `contains(null)` itself), a set missing `QUERY` (every Phase 2A adapter is query-only, so `QUERY` is now mandatory at registration), and a null client-mirror-mode declaration.

**Fix** (`PlayerResourceService.queryExternal`): now defensively re-checks `adapter.supportedOperations().contains(QUERY)` before calling `snapshot(...)`, returning the new `ResourceQueryFailureReason.OPERATION_UNSUPPORTED` rather than executing anyway. Chosen contract: reject unsupported operations at both registration time (primary) and query time (defense-in-depth) — never silently map to `RESOURCE_NOT_REGISTERED`/`ADAPTER_NOT_REGISTERED`.

**New tests:** `ExternalPlayerResourceAdapterRegistryTest` gained `nullOperationSupportSetIsRejected`, `emptyOperationSupportSetIsRejected`, `operationSupportSetContainingANullEntryIsRejected`, `adapterMissingQuerySupportIsRejectedAtRegistration`, `nullClientMirrorModeIsRejected`. `HealthResourceAdapterConversionTest`/`FoodResourceAdapterConversionTest` gained `healthAdapterSupportsQuery`/`foodAdapterSupportsQuery`. `PlayerResourceServiceTest` gained `serviceRejectsQueryWhenAdapterDoesNotDeclareQuerySupport`, using reflection to bypass `register()`'s own enforcement (documented inline) specifically so the *service's* independent check is what is under test, not the registry's.

### 4. No fabricated generic maximum

**Bug found:** `PlayerResourceService.queryGenericState` computed `definition.authoredBaseMaximum().orElse(definition.absoluteMinimum())` — a resource with no authored maximum silently returned a **successful** snapshot with `maximumUnits() == absoluteMinimum()` (commonly `0`), fabricating a value no owning system ever declared.

**Fix:** added `ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE`; `queryGenericState` now returns that structured failure when `authoredBaseMaximum()` is absent, instead of fabricating one. No maximum-resolver framework was built to "properly" solve this — that remains later-phase work; this pass only removes the fabrication.

**New tests** (`PlayerResourceServiceTest`): `genericScalarWithAnAuthoredMaximumSucceeds`, `genericScalarWithoutAnAuthoredMaximumFailsStructurallyRatherThanFabricatingOne`, `noFabricatedZeroZeroSuccessWhenMaximumIsUnavailable` (explicit regression guard naming the exact old bug).

### 5. Adapter snapshot validation

**Fix** (`PlayerResourceService`, new `validateExternalSnapshot`): before returning an external query as `Success`, verifies the snapshot is non-null, `snapshot.resourceId().equals(definition.id())`, `snapshot.unitScale() == definition.unitScale()`, and `snapshot.maximumUnits() >= definition.absoluteMinimum()`. Any violation returns the new `ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT`, naming the *queried* resource id (never the adapter's mismatched one — no silent reattribution). `queryExternal` also now checks `definition.model() == ResourceModel.SCALAR` before ever calling the adapter (mirrors `queryGenericState`'s own `UNSUPPORTED_MODEL` check), since `ResourceSnapshot` has no partitioned representation. `currentUnits()`/`maximumUnits()` need no separate "representable" check — they are already primitive `long`s by the time a `ResourceSnapshot` can exist; any conversion overflow is caught earlier, at `HealthResourceAdapter.toUnits`'s own boundary (see §1).

**New tests** (`PlayerResourceServiceTest`, using a new `FixedSnapshotAdapter` fake): `nullAdapterSnapshotProducesCorruptAdapterSnapshotFailure`, `mismatchedResourceIdInSnapshotProducesCorruptAdapterSnapshotFailure` (also asserts the failure still names the *queried* id), `mismatchedUnitScaleInSnapshotProducesCorruptAdapterSnapshotFailure`, `maximumBelowAbsoluteMinimumInSnapshotProducesCorruptAdapterSnapshotFailure`, `validSnapshotStillSucceedsAfterValidation`.

### 6. Health/Food capabilities and presentation

**Fix** (`ProductionResourceDefinitions`): both definitions now declare `ResourceCapability.HUD_VISIBLE` and `ResourceCapability.MENU_VISIBLE`. Neither declares a mutation capability (`SPENDABLE`/`RESTORABLE`/`DIRECT_DRAIN`) — Phase 2A's generic adapters remain query-only regardless of what their owning vanilla system can itself do.

**New tests** (`PlayerResourceRegistryTest`): `productionHealthAndFoodDeclareExactlyHudVisibleAndMenuVisibleCapabilities` (asserts the exact capability set, and explicitly asserts the three mutation capabilities are absent), `productionHealthAndFoodDeclareCorePresentationMetadata` (BAR/CORE_CONSTANT/5:1, both resources).

### 7. Remaining hardcoded ×5 formula — found and fixed

Full-source grep (excluding `/Inspiration Mods`) for Health/Food display multiplication found:
- **`MobHealthBarHud.java:177-178`** — `Math.round(target.getHealth() * 5)`/`Math.round(target.getMaxHealth() * 5)`, a genuinely independent, hardcoded literal `5`, unrelated to `RpgDisplayUtils`/`ResourceDisplayConversion`. **Fixed**: now calls `RpgDisplayUtils.toDisplayHp(...)` (identical output, single source of truth). Canonical §6.5 explicitly requires "mob health bars ... use the same Health formatter" as the player's own display — this was a real, pre-existing violation of that requirement, now closed.
- `TotalityCommands.java:652`, `DamageRollNotification.java:32`, `TotalityDamage.java:90` — all three reference the named constant `RpgDisplayUtils.HP_DISPLAY_MULTIPLIER` directly, not a bare literal. Reviewed and left unchanged: these read the one canonical constant, so they cannot drift independently of it; only a literal duplicate (like `MobHealthBarHud`'s `* 5`) is the kind of "separately maintained formula" the task's concern targets.
- `PlayerStats.java:175` (`getMaxHpBonus() = CON modifier * 5`) — confirmed via grep to have **zero callers anywhere in production code** (already flagged as dead/unused in Phase 1's own `PlayerStatsCharacterizationTest`). The **live** CON→MaxHealth formula is `RpgDisplayUtils.conModifierToVanillaHp` (×2, applied to vanilla `Attributes.MAX_HEALTH` by `StatAttributeApplier.applyConHp`) — a mechanical bonus formula, not a display conversion, and not touched. `getMaxHpBonus()` itself is unused dead code, out of this correction pass's scope to remove (Stamina/Mana general cleanup is a separate, later concern).
- No hardcoded Food/Hunger `×5` literal exists anywhere outside the shared `ResourceDisplayConversion.HEALTH_FOOD`/`ResourceValueFormatterRegistry` path (confirmed by the semantic grep finding zero `Food`/`Hunger`-adjacent `*5` matches).

### 8. Corrected compatibility claim

**Overclaim found:** the original report's §10 described the Health-display comparison as covering "0.0–200.0 in 0.1 steps... every sample," implying broader equivalence than was actually tested, and the test itself was named `...Exhaustively`.

**Investigated with a real, finer sweep** (2,000,001 samples at 0.0001 granularity, freshly computed per sample): confirmed a genuine, mathematically-inherent "double rounding" divergence exists between the old single-stage formula (`Math.round(hp * 5)`) and the new two-stage fixed-point formula (round to nearest unit at scale 1000, then round the display value), affecting **0.22%** of the sampled range — specifically `float` values within roughly `0.0005` of a boundary approached from below (e.g. `hp = 0.0996` gives old `0`, new `1`). This is a normal, accepted consequence of any two-stage rounding pipeline, not a defect. The audited, currently-authored Health delta samples in this codebase (whole numbers, halves, and the one documented fractional amount, `0.2`) do not land in a known divergence zone — that is evidence about those specific audited samples, not a proof covering every float a future or unaudited code path might produce. An unusual resulting float that happens to land within the divergence band may legitimately display one point differently; authoritative mechanical Health itself is never affected by this either way, and the accepted fixed-point scale remains `1000`.

**Fixes (further corrected in the 2026-07-19 cleanup pass — see that pass's own report/notes for the exact final wording):**
- Renamed the test to `toDisplayHpMatchesTheOldRawFormulaAtOrdinaryGameplayGranularity` and reworded its claim to "ordinary gameplay-range compatibility," not "exhaustive."
- Added `twoStageFixedPointRoundingCanDivergeFromTheOldSingleStageFormulaNearBoundaryValues`, documenting the divergence honestly with the verified `hp = 0.0996` example.
- Added a test (originally named `noCurrentlyUsedGameplayHealthValueFallsInADivergenceZone`, since renamed to `auditedCurrentHealthSamplesDoNotFallInADivergenceZones` — the original name overclaimed "no currently-used value," which is narrower evidence than it sounds; see the cleanup pass), asserting old/new agreement across the audited Health deltas this codebase's gameplay code was found to produce (`0, 0.2, 0.5, 1, 1.5, 2, 10, 10.5, 13.7, 20, 24, 40`).
- The Health fixed-point scale (`1000`) was **not** changed — no gameplay requirement was identified that would justify it, and none of the audited samples above hit the divergence band.

### 9. Stale Phase 1 documentation wording

Corrected in-source and in-doc claims that read as current-state assertions but described Phase 1 (before Health/Food existed):
- `PlayerResourceRegistry.java`'s class Javadoc ("No production definitions are registered... `INSTANCE` is empty at runtime") → now states the current Phase 2A contents.
- `ResourceAmount.java` ("the future `PlayerResourceService`'s responsibility, not implemented by this Phase 1 patch") → `PlayerResourceService` now exists; reworded to state precisely what remains unimplemented (mutation/transactions).
- `ResourceLifecyclePolicy.java`/`ResourceDeathPolicy.java` ("no production resource is registered yet") → reworded to state that Health/Food *are* registered and use `DEFAULT`, but that the policy is effectively inert for either (both are `EXTERNAL_ADAPTER`-authority and never enter the live-state map).
- `ResourceGrantInitialization.java` ("not implemented by this Phase 1 patch") → reworded to state this is still true as of Phase 2A specifically because Health/Food need no grant provider, not because nothing has changed since Phase 1.
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md`: top status banner corrected; the stale test-name reference (`productionSingletonHasNoDefinitionsInThisPatch`, since renamed) and the "Confirmed unchanged by this correction pass" section (which read as a general current-state summary) both now carry explicit "superseded/historical" notes pointing to the current Phase 2A state.
- Reviewed and left unchanged: several other "this Phase 1 patch"/"not this Phase 1 foundation" comments in `ResourceCapability.java`, `ResourceStateComponents.java`, `state/ScalarResourceState.java`, `state/PartitionedResourceState.java`, and two `PlayerResourceStateComponent.java` inline comments about `sync()`/instantiation — all remain factually accurate as of Phase 2A (nothing about them changed), so left as-is rather than rewritten for its own sake.

### 10. Preserved behavior (confirmed unchanged by this correction pass)

Vanilla Health/Food authority, damage, healing, eating rules, starvation, saturation, exhaustion, natural regeneration, Health/Hunger bar style/placement, Health smoothing, Stamina, Mana, Rage, Rest behavior, and packet architecture (still zero generic Health/Food packets — `NATIVE_SYNCHRONIZATION` unchanged) are all untouched by this correction pass. The `Player`-typed query interface decision from the original implementation stands; its Javadoc already documents that a future mutation-capable adapter must re-narrow to `ServerPlayer`.

### 11. Updated totals

- **Test count:** 164 → **202** (38 new tests this pass: 10 in the new `PlayerResourceStateComponentExternalEntryPathTest`, 5 in `ExternalPlayerResourceAdapterRegistryTest`, 12 in `PlayerResourceServiceTest`, 2 in `HealthResourceAdapterConversionTest`/`FoodResourceAdapterConversionTest` combined (QUERY-support checks) plus 8 new Health boundary/overflow tests, 2 in `PlayerResourceRegistryTest` (capabilities/presentation), 3 in `RpgDisplayUtilsCharacterizationTest` (boundary/gameplay-range honesty)).
- **Files modified this pass (14):** `HealthResourceAdapter.java`, `PlayerResourceStateComponent.java`, `ExternalPlayerResourceAdapterRegistry.java`, `PlayerResourceService.java`, `ResourceQueryFailureReason.java`, `ProductionResourceDefinitions.java`, `MobHealthBarHud.java`, `PlayerResourceRegistry.java`, `ResourceAmount.java`, `ResourceLifecyclePolicy.java`, `ResourceDeathPolicy.java`, `ResourceGrantInitialization.java`, `HealthResourceAdapterConversionTest.java`, `RpgDisplayUtilsCharacterizationTest.java`, `HungerDisplayCharacterizationTest.java` (formatter-registry bootstrap fix), `ExternalPlayerResourceAdapterRegistryTest.java`, `PlayerResourceServiceTest.java`, `PlayerResourceRegistryTest.java`, `FoodResourceAdapterConversionTest.java`.
- **Files created this pass (1):** `PlayerResourceStateComponentExternalEntryPathTest.java`.

### 12. Build and datagen results (this pass)

```
./gradlew compileJava   → BUILD SUCCESSFUL
./gradlew test          → BUILD SUCCESSFUL (202 tests, 0 failures)
./gradlew runDatagen    → BUILD SUCCESSFUL, "written: 0" (349 cached files, 0 changed)
./gradlew build -x runDatagen → BUILD SUCCESSFUL
```

No pre-existing generated JSON changed further. A `logs/` directory (gzipped client-session logs) and `build/` output appeared as an expected, harmless byproduct of running `runDatagen`/`build` in this environment — untracked, excluded from the review bundle, not committed.

### 13. Manual smoke test

**SUPERSEDED — since executed and passed.** See "Final Status — Ready to Commit" at the end of this document.

~~Still **not executed** — this correction-pass session, like the original implementation session, has no GUI/display interaction tool available. The checklist in §14 of the task (unchanged) remains open for Stefan to run on the real 26.2 client.~~

### 14. Genuinely unresolved issues

None found requiring a decision beyond what §12 (Manual smoke-test status) already names — the pending manual client verification. **SUPERSEDED — that verification is no longer pending; see "Final Status — Ready to Commit" at the end of this document.**

---

## Cleanup Pass Addendum (2026-07-19, same day)

A narrowly scoped cleanup pass fixed five smaller issues left by the correction pass above. No Breath or later-phase work was started.

### 1. `HP_DISPLAY_MULTIPLIER`/`toVanillaHp` now derive from the shared conversion

**Issue:** `RpgDisplayUtils.HP_DISPLAY_MULTIPLIER` was still an independently maintained literal `5` (only commented as "mirroring" `ResourceDisplayConversion.HEALTH_FOOD`, not actually derived from it), and `toVanillaHp` divided by that literal-backed field rather than the shared conversion object.

**Fix:**
- `HP_DISPLAY_MULTIPLIER` is now computed at class-init time by `deriveMultiplier()`, which reads `ResourceDisplayConversion.HEALTH_FOOD.numerator()` and asserts `denominator() == 1` (throwing `IllegalStateException` — loud failure, not silent drift — if that assumption is ever violated by a future change to `HEALTH_FOOD`).
- Added `ResourceDisplayConversion.invertToMechanical(double displayValue)`: returns `displayValue * denominator / numerator`, throwing `ArithmeticException` for a zero-numerator conversion (which has no well-defined inverse). `toVanillaHp` now delegates to `ResourceDisplayConversion.HEALTH_FOOD.invertToMechanical(displayHp)` directly, not a second division by `HP_DISPLAY_MULTIPLIER`.
- Verified: `100 → 20.0`, `50 → 10.0`, `5 → 1.0` (all three explicitly required cases), plus a 0–200-in-5-steps sweep (`toVanillaHpCannotDriftFromHealthFoodsInverse`) comparing `toVanillaHp` against `HEALTH_FOOD.invertToMechanical` directly, and `hpDisplayMultiplierCannotDriftFromHealthFood` asserting the field numerically equals `HEALTH_FOOD.numerator()`/`denominator()==1` by construction.
- Existing callers (`TotalityCommands.java`, `DamageRollNotification.java`, `TotalityDamage.java`) all still reference `HP_DISPLAY_MULTIPLIER` by name and compile unchanged — its value (`5`) is identical, only its *provenance* changed.

### 2. Hunger's defensive HUD fallback corrected

**Issue:** `TotalityHudRenderer`'s fallback arguments to `resourceDisplayCurrentMax` for `totality:food` were the raw mechanical `hunger, 20` — if the primary query path were ever unavailable, the fallback would have silently reverted to the pre-Phase-2A raw `0–20` display, undermining the "Food aligns to the same 100 baseline" guarantee specifically in the one code path meant to be the safety net.

**Fix:** the fallback now computes `ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(hunger, 1)` and `...convertUnitsToDisplay(20, 1)` — the exact same shared conversion the primary path uses, applied directly rather than through a new hardcoded `* 5`. Bar fill, smoothing, placement, sprites, eating, starvation, saturation, exhaustion, and regeneration are all untouched.
**New tests** (`FoodResourceAdapterConversionTest`, since the private HUD method itself needs a real client `Player` and cannot be unit-tested): `hudHungerFallbackConversionMatchesTheSharedFoodConversionAtAllFourReferenceLevels` (native `0/6/16/20` → display `0/30/80/100`, the exact four values the task required) and `hudHungerFallbackMaximumConversionMatchesNativeTwentyToDisplayOneHundred`.

### 3. Corrected signed `Long.MIN_VALUE` rounding

**Bug found:** `ResourceDisplayConversion.roundHalfAwayFromZero` negated a negative numerator via `Math.negateExact` before dividing. That negation itself overflows for `numerator == Long.MIN_VALUE` (since `-Long.MIN_VALUE` exceeds `Long.MAX_VALUE`) even when the true divided-and-rounded result is perfectly representable — e.g. `Long.MIN_VALUE / 2` is exactly `-4611686018427387904`, well within range, yet the old implementation threw `ArithmeticException` for it anyway.

**Fix:** replaced with an implementation based on `Math.floorDiv`/`Math.floorMod` (both well-defined and overflow-free for any `long` dividend against a positive divisor, `Long.MIN_VALUE` included, since the divisor here is always positive). Ties are detected by comparing `remainder` against `denominator - remainder` (avoiding `2 * remainder`, which could itself overflow for a remainder near `Long.MAX_VALUE / 2`) rather than doubling the remainder. At an exact tie, the sign of the *original* numerator (not a negated copy) decides the away-from-zero direction. The method now never throws for any valid `(numerator, denominator > 0)` pair — the only remaining overflow sources are the checked multiplications in `convertUnitsToDisplay` that produce its inputs, unchanged from before.

### 4. `Long.MIN_VALUE` regression test result

`longMinValueUnitsWithEvenDivisorRoundsExactlyWithoutThrowing` (`ResourceDisplayConversion(1, 2)`, `units = Long.MIN_VALUE`, `unitScale = 1`): **passes**, returns exactly `Long.MIN_VALUE / 2` (`-4611686018427387904`), no exception. Additional new tests: `negativeExactHalfRoundsAwayFromZero` (`-5/2` → `-3`, a genuine tie), `negativeValueJustBelowAHalfRoundsTowardZero` (`-9/4` → `-2`, nearer to `-2` than `-3`), `negativeValueJustAboveAHalfRoundsAwayFromZero` (`-11/4` → `-3`, nearer to `-3` than `-2`), `ordinaryPositiveValuesRemainUnchangedAfterTheRoundingFix` (re-asserts five previously-established positive results unchanged), `genuineMultiplicationOverflowStillThrowsAfterTheRoundingFix` (both the numerator-side and denominator-side checked multiplications still throw `ArithmeticException` for genuine overflow).

### 5. Documentation/test claims corrected

- `PlayerResourceStateComponentExternalEntryPathTest`'s class Javadoc previously stated "No reflection is used anywhere in this file" — **false**; `injectCorruptedExternalStateViaReflection` uses reflection, consumed by exactly three tests (`writeDataNeverWritesALiveHealthEntryEvenIfCorruptedIntoStates`, `syncWritingExcludesExternalLiveEntriesEvenIfCorruptedIntoStates`, `copyingAComponentWithCorruptedExternalLiveStateQuarantinesItRatherThanCopyingItLive`). The Javadoc now states this accurately and names the three tests and the reason reflection is unavoidable for them specifically.
- `noCurrentlyUsedGameplayHealthValueFallsInADivergenceZone` renamed to `auditedCurrentHealthSamplesDoNotFallInADivergenceZones`. Its assertion-failure message and the class-level narrative around it were reworded from "no real gameplay value can reach this"/"every Health value the game actually produces" to the narrower, evidence-supported claim: the specific audited sample values tested do not diverge; an unusual resulting float near a rounding boundary may legitimately display one point differently; authoritative mechanical Health is never affected either way; the accepted fixed-point scale remains `1000`. The same wording correction was applied to this report's own §8 ("Corrected compatibility claim") and to the readiness document's Phase 2A Correction Pass section.
- No exhaustive-proof claim over all possible resulting Health floats is made anywhere in the codebase or these reports as of this pass.

### 6. Validation

```
./gradlew compileJava   → BUILD SUCCESSFUL
./gradlew test          → BUILD SUCCESSFUL (214 tests, 0 failures)
./gradlew runDatagen    → BUILD SUCCESSFUL, "written: 0" (349 cached files, 0 changed)
./gradlew build -x runDatagen → BUILD SUCCESSFUL
```

Test count: 202 → **214** (12 new tests: 8 in `ResourceDisplayConversionTest` — `Long.MIN_VALUE` regression, negative-half/below/above-half, positive-values-unchanged, overflow-still-throws ×2 forms, `invertToMechanical` ×2 forms — plus 2 in `RpgDisplayUtilsCharacterizationTest` (`hpDisplayMultiplierCannotDriftFromHealthFood`, `toVanillaHpCannotDriftFromHealthFoodsInverse`, and `toVanillaHpIsTheExactInverse` gained one more assertion rather than a new test) plus 2 in `FoodResourceAdapterConversionTest` (HUD fallback conversion pins)). No pre-existing generated JSON changed further; no unrelated file changed.

**Files modified this pass (5):** `ResourceDisplayConversion.java`, `RpgDisplayUtils.java`, `TotalityHudRenderer.java`, `PlayerResourceStateComponentExternalEntryPathTest.java`, `RpgDisplayUtilsCharacterizationTest.java`, `ResourceDisplayConversionTest.java`, `FoodResourceAdapterConversionTest.java` — 7 files total (count corrected from the initial 5 estimate above once the actual diff was final).

### 7. Manual smoke test

**SUPERSEDED — since executed and passed.** See "Final Status — Ready to Commit" immediately below.

~~Still not executed — no GUI/display tool available in this session either.~~

### 8. Genuinely unresolved issues

None. **SUPERSEDED — the manual client verification this note referred to is complete; see "Final Status — Ready to Commit" below.**

---

## Final Status — Ready to Commit (2026-07-19)

This section is the authoritative, current status of Phase 2A, superseding every earlier "not executed"/"pending" manual-test statement in this document (§11 of the original record, §13 of the Correction Pass Addendum, §7 of the Cleanup Pass Addendum).

### Manual smoke test — PASSED

Stefan manually tested the final Phase 2A implementation (post cleanup pass, 214/214 automated tests) on the real Minecraft 26.2 client against the existing test world. Result: **passed**. Confirmed:

- The Minecraft 26.2 client launched successfully.
- The existing test world loaded successfully.
- No Resource API component-attachment, NBT, or synchronization errors appeared at any point.
- Health displayed and behaved exactly as before this phase.
- Health damage and healing worked normally.
- The Health bar's fill, smoothing, placement, and sprites appeared unchanged.
- Hunger now displays on the `0–100` scale (previously raw `0–20`).
- The Hunger bar's fill continued to correspond correctly to the underlying vanilla Food level.
- Eating eligibility and eating behavior remained unchanged.
- Food restoration displayed correctly through the ×5 presentation.
- Saturation, exhaustion, starvation, and natural-regeneration behavior showed no observed regression.
- Stamina, Mana, and Rage remained unchanged.
- No duplicate Health or Food bars appeared anywhere in the HUD.
- Partially depleted Health and Hunger persisted correctly across save, close, and reopening the same world.
- A second shutdown after reopening completed cleanly.
- No visible gameplay or HUD regression was observed.

This is a manual playtest checklist, not exhaustive verification of every code path or every possible float/value combination — it confirms the specific behaviors listed above, on the specific test world used, on this one pass. It does not claim to have exercised every Health/Food value, every HUD screen state, or every other mod/system interaction.

### Final Phase 2A status

- **Automated tests:** 214/214 passed (`./gradlew test`).
- **Compile:** succeeded (`./gradlew compileJava`).
- **Datagen:** wrote 0 files (`./gradlew runDatagen`, `written: 0`, 349 cached files unchanged).
- **Full build:** succeeded (`./gradlew build -x runDatagen`).
- **Manual smoke test:** passed (see above).
- **Phase 2A is ready to commit.**

### Scope boundaries carried forward unchanged

- Health and Food remain **vanilla-authoritative external adapters** — `totality:health` wraps `Player.getHealth()`/`getMaxHealth()`, `totality:food` wraps `Player.getFoodData().getFoodLevel()`. Neither adapter owns, duplicates, or overrides its vanilla authority.
- Food's current implementation remains **mechanically vanilla `0–20`**, with a presentation-only `5/1` conversion producing the `0–100` display. The mechanical scale itself was never changed.
- A future **mechanical** Food `0–100` scale, dynamic Food capacity, metabolism, and Diet/Cooking integration are explicitly **later scope** — none of that was designed, implemented, or implied by this phase. This phase is presentation-only for Food.
- **Breath is the recommended next Resource API slice** (per the Oxygen-to-Breath audit addendum already on file), using this same Health/Food external-adapter pattern. Temperature remains out of Resource API scope (owned by a future Environment/Physiology system).

The canonical closed design (`TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`) was not modified by this phase or by recording this status — only implementation-status documents (this report, the readiness audit, the review bundle) were updated.
