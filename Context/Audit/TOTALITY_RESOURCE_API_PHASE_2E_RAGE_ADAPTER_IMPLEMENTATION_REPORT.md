# TOTALITY RESOURCE API — PHASE 2E: RAGE LEGACY CHARGE ADAPTER — IMPLEMENTATION REPORT

Branch: `feature/general-resource-api`
Starting commit: `1bc199f62c8fa510942df769f4775bbcac33f845` ("Add standard spell-slot resource adapter")
Status at report time: **READY TO COMMIT** — manual smoke testing PASSED (§29). Uncommitted working-tree
content; nothing staged, nothing committed.

---

## 1. Purpose and scope

Expose the existing Barbarian Rage charge pool through the Generic Player Resource API as one
query-only external `SCALAR` resource, `totality:rage`, reading one entry
(`BarbarianRageAbility.CHARGE_ID = totality:barbarian_rage`) of the legacy-authoritative,
generically `Identifier`-keyed `PlayerChargesComponent` charge-pool map. `PlayerChargesComponent`
remains the sole authoritative owner of every Rage gameplay operation. No casting/activation,
consumption, rest, persistence, synchronization, HUD, Class-tab, or effect behavior was changed. The
intended (and, at the automated level, verified) observable result is no gameplay change.

## 2. Executive result

**Implemented as scoped and READY TO COMMIT.** `RageResourceAdapter`, `ChargeComponents.maybeGet`,
production registration, and focused characterization/safety tests are complete. 428/428 automated
tests pass (up from 390 at the end of Phase 2D's correction pass). Clean `compileJava`/
`compileTestJava`/`build -x runDatagen`, zero new `runDatagen` output, clean `git diff --check`.
**Manual smoke testing PASSED (2026-07-20)** for every currently practical scenario — see §29. Two
Strength-advantage mechanics were not practically testable in the current manual environment; this is
not a failure and not a Phase 2E blocker (§29).

## 3. Files added

- `src/main/java/zcylas/totality/api/rpg/resources/external/RageResourceAdapter.java`
- `src/test/java/zcylas/totality/api/rpg/resources/external/RageResourceAdapterTest.java`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_2E_RAGE_ADAPTER_IMPLEMENTATION_REPORT.md` (this file)

## 4. Files modified

- `src/main/java/zcylas/totality/api/rpg/classes/ChargeComponents.java` — added `maybeGet(ServerPlayer)`.
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceIds.java` — added `RAGE`/`RAGE_ADAPTER`.
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java` — registers the adapter, definition, and formatter.
- `src/test/java/zcylas/totality/api/rpg/classes/PlayerChargesRageCharacterizationTest.java` — corrected stale `PARTITIONED_POOL` doc comment; added `updatePoolMax`/persistence/sync/`copyFrom`/unrelated-pool/level-clamp coverage.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java` — count 6→7; new Rage shape/capability/presentation assertions; fixed the stale "no Rage definition yet" test.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryExternalAdapterFreezeTest.java` — adapter count 6→7; new Rage adapter-resolution/client-query tests.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalSafetyTest.java` — Rage external-authority-rejection coverage.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalEntryPathTest.java` — Rage stale-NBT/sync-payload quarantine tests.
- `src/test/java/zcylas/totality/api/rpg/resources/presentation/ResourceValueFormatterRegistryTest.java` — Rage formatter test.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentTest.java` — **incidental fix**: one pre-existing (Phase 1) test used the literal placeholder id `"rage"` for an arbitrary unregistered example. Now that `totality:rage` is real, that placeholder collided with production state and the test began throwing. Renamed to `"test_model_mismatch_resource"` — no behavioral or intent change. Same category of fix Phase 2D's correction pass made for `"spell_slots"`.

**Untouched, as required:** `PlayerChargesComponent.java` (its existing `getAllPools()` already
provided a safe, presence-aware read-only view — no new accessor was needed, per the task's own
"do not add redundant production API" instruction); `BarbarianRageAbility.java`; `RageEffect.java`;
`PlayerConnectionEvents.java`; `StatsServerEvents.java`; `TotalityClient.java`; `ClassTab.java`;
`PlayerResourceService.java`; `ResourceQueryResult.java`; `ResourceSnapshot.java`;
`PartitionedResourceSnapshot.java`; `ResourceQueryFailureReason.java`;
`ExternalPlayerResourceAdapter.java`'s method signature (only referenced, not modified — its existing
contract already fully supports Rage's scalar shape); Health/Food/Breath/Mana/Stamina/spell-slot
adapters and their tests.

## 5. Exact resource definition

```
ID:                totality:rage
Model:              SCALAR
Authority:           EXTERNAL_ADAPTER
Polarity:            HIGH_IS_GOOD
Unit scale:          RageResourceAdapter.UNIT_SCALE (1)
Absolute minimum:    0
Authored baseline:   2 (descriptive only — level-1 RAGE_CHARGES entry; never consulted live)
External adapter:    RageResourceAdapter.ID (= totality:rage)
Definition version:  1
Capabilities:        HUD_VISIBLE, MENU_VISIBLE
Presentation:        IDENTITY conversion, PIPS display, CONTEXTUAL_ACCESS HUD role
Client mirror mode:  LEGACY_BESPOKE_SYNCHRONIZATION
```

Registered in `ProductionResourceDefinitions.registerAdapters()`/`registerDefinitions()`/
`registerFormatters()`, in that order, exactly like every prior resource. Deliberately does **not**
declare `SPENDABLE`/`RESTORABLE`/`MAXIMUM_MODIFIERS`/`PARTITIONED_SPENDING` — matching every prior
transitional adapter's precedent (Health/Food/Breath/Mana/Stamina/spell slots all declare zero
mutation capability regardless of what their legacy owner can itself do), since
`supportedOperations()` is `QUERY`-only. A formatter (`ResourceDisplayConversion.IDENTITY`) is
registered, matching Mana/Stamina's precedent for an already-plain-integer legacy store.

## 6. Exact adapter behaviour

`RageResourceAdapter.snapshot(player, definition)`:
1. Non-`ServerPlayer` → `STATE_UNAVAILABLE_ON_THIS_SIDE`.
2. `ChargeComponents.maybeGet(serverPlayer)` empty → `MALFORMED_OWNER_STATE`.
3. Otherwise delegates to package-visible `resolve(Identifier, PlayerChargesComponent)`.

`resolve(resourceId, component)`:
1. `component.getAllPools().get(BarbarianRageAbility.CHARGE_ID)` — a pure, already-safe read on the
   component's existing unmodifiable/read-only map view (`Collections.unmodifiableMap` around the
   live backing map, not a defensive copy — callers cannot mutate the pool set through the returned
   `Map`, though a later legitimate component mutation would still be reflected by it). No new
   accessor was added to `PlayerChargesComponent`.
2. `pool == null` → `STATE_UNINITIALIZED` (Rage not yet granted for this player).
3. Otherwise validates `current >= 0`, `maximum >= 0`, `current <= maximum` → any violation is
   `MALFORMED_OWNER_STATE`, no clamping, no partial success.
4. Otherwise returns `ResourceQueryResult.Success` wrapping a `ResourceSnapshot(resourceId, current,
   maximum, RageResourceAdapter.UNIT_SCALE)`.

Never calls `registerPool`, `ensurePool`, `updatePoolMax`, `setMax`, `consume`, `restore`,
`restoreAll`, `onRest`, `BarbarianRageAbility.registerChargePool`/`updateChargePool`, any class-level
lookup, or any status-effect/toggle method. Never inspects `rechargeType`, `rechargeAmount`, active
Rage duration, status-effect amplifier, toggle state, or combat timer — none of those fields are read
anywhere in `resolve`.

## 7. Exact legacy backing-key mapping

Resource API resource id: `totality:rage` (`PlayerResourceIds.RAGE`).
Legacy `PlayerChargesComponent` pool key: `totality:barbarian_rage` (`BarbarianRageAbility.CHARGE_ID`,
referenced directly, never duplicated as a literal string in the adapter).
These are two deliberately different, independent identifiers — neither was renamed, migrated, or
merged. `RageResourceAdapterTest.legacyBackingKeyIsUnchangedAndDistinctFromTheResourceId` pins both
facts directly.

## 8. Missing component behaviour

A real `ServerPlayer` without an attached `PlayerChargesComponent` → `MALFORMED_OWNER_STATE`. The
component is unconditionally attached at construction (`MixinServerPlayer` →
`PlayerComponentEvents.attachComponentsTo`); its absence indicates a broken owner, not an ordinary
transitional state — matching Phase 2D's `SpellSlotComponent` precedent exactly.

## 9. Missing pool behaviour

A present component with no `barbarian_rage` entry (the ordinary case for any non-Barbarian, or a
Barbarian who has not yet had `registerChargePool` called for them) → `STATE_UNINITIALIZED`. This is
query-time classification only — it does not mean the player can never receive Rage, and the adapter
never infers Barbarian ownership or grants anything itself. This is the first adapter in this series
to ground `STATE_UNINITIALIZED` in "sparse map key genuinely absent" rather than "sentinel value
present but marked unset" (Mana/Stamina's `-1` sentinel). Confirmed distinct from an unrelated pool's
mere presence: `RageResourceAdapterTest.anUnrelatedPoolAloneStillReturnsStateUninitializedForRage` and
`.onlyBarbarianRageChargeIdIsEverRead` both prove an unrelated pool cannot be mistaken for Rage's own.

## 10. Present 0/0 behaviour

A present pool with `current = 0, maximum = 0` is a valid `Success` (a hypothetical zero-maximum
Rage grant — not reachable via current production paths, since `registerChargePool` always uses
`Math.max(1, classLevel)`, but the adapter does not reject it either). A present pool with `current =
0, maximum > 0` (fully spent charges) is likewise a valid `Success`. Both are directly tested
(`presentZeroZeroPoolIsAValidSuccess`, `zeroCurrentPositiveMaximumIsAValidSuccessRepresentingFullySpentCharges`).

## 11. Malformed owner-state behaviour

`current < 0`, `maximum < 0`, or `current > maximum` → `MALFORMED_OWNER_STATE`, no clamping, no
partial success. Unlike Mana/Stamina (whose adapters permit a transient `current > maximum` against a
live-recomputed maximum), Rage has **no live maximum computation at query time** — `pool.max()` is
already a stored, cached value, so normal legacy behavior cannot produce any of these three without
corrupted persisted data (see §14, deferred issue 4). All three malformed scenarios are constructed
in tests only via direct reflection into `PlayerChargesComponent`'s private backing map — no
production mutator can produce them, matching the task's explicit instruction not to weaken the
component's normal invariants for testing.

## 12. Unit-scale ownership

`RageResourceAdapter.UNIT_SCALE = 1L` — canonical, adapter-owned, never taken from
`definition.unitScale()`. `resolve(...)` always constructs its snapshot at this constant.
`ProductionResourceDefinitions` declares `.unitScale(RageResourceAdapter.UNIT_SCALE)` rather than an
independent literal, so the definition and the adapter cannot drift apart — the same boundary
established in Phase 2D's correction pass for `StandardSpellSlotsResourceAdapter.UNIT_SCALE`. Tested
directly: `unitScaleConstantIsExactlyOne`, `resourceIdAndUnitScaleArePreservedOnTheSnapshot`, and
`PlayerResourceRegistryTest.rageDefinitionIsScalarExternalAdapter`'s
`RageResourceAdapter.UNIT_SCALE == definition.unitScale()` assertion.

## 13. Client/server authority behaviour

Server-authoritative. `clientMirrorMode()` = `LEGACY_BESPOKE_SYNCHRONIZATION`: unlike every prior
adapter, Rage's "client mirror" is not a separate manager class — it is simply another instance of
`PlayerChargesComponent` itself (`ChargeComponents.registerClientComponent`), populated by
`applySyncPacket` and read directly by `TotalityClient`'s `ISecondaryResource` HUD registration and
`ClassTab`'s "CLASS RESOURCE" panel. The adapter is deliberately never given a code path that reads
that client-side instance for generic-query purposes. A client-side generic query returns
`STATE_UNAVAILABLE_ON_THIS_SIDE` (`nullPlayerIsTreatedAsNotAServerPlayerAndReturnsStateUnavailableOnThisSide`,
plus the full production-path
`productionRageQueryOnANonServerPlayerReturnsStateUnavailableOnThisSideNotAnException`). No new
synchronization was added.

## 14. Rage active-state boundary

`RageEffect` (the Minecraft `MobEffect` marking active Rage), its duration, amplifier, damage bonus,
STR advantage, resistances, casting restriction, and ending conditions are entirely untouched and
entirely unread by this adapter. The adapter reads only `PlayerChargesComponent`'s stored
current/maximum for one key — it never touches `AbilityComponent`'s toggle state, combat timer, or
`RageEffect` in any way. This boundary was already clean in production code before this phase (the
only prior coupling point, `BarbarianRageAbility.onActivate`'s single `consume(CHARGE_ID)` call, is
untouched); this phase adds a second, entirely separate read-only path alongside it.

**Activation robustness note (documentation correction per instruction):** Rage activation is
**synchronous**, not a formal transaction: `consume(CHARGE_ID)` succeeds, and the Rage effect/toggle
are applied in the same synchronous server call with no ordinary false/failure-return path after
consumption. This is **not** the same as "atomic" in the transactional sense — an unexpected
exception after `consume()` returns `true` but before `onToggleOn`/`activateToggle` complete could
theoretically leave a spent charge without full activation. This is recorded here only as a future
Ability API transaction/commit robustness note (§22, deferred issue 8); activation ordering and
rollback logic were not changed in Phase 2E.

## 15. Legacy Rage characterization findings

`PlayerChargesRageCharacterizationTest` previously covered: `CHARGE_ID` stability, fresh-component
baseline, register+consume, consume-fails-when-empty, Short Rest +1 clamped, Long Rest full, rest
priority, and `ensurePool` preserving current across a max increase. This phase adds: `updatePoolMax`
preserving current below the new maximum and clamping it when the maximum decreases (distinct from
`ensurePool`, previously untested); a no-op-on-missing-pool check for `updatePoolMax`; `copyFrom`
preserving multiple independent pools (death/respawn characterization); full NBT persistence
round-trip for current/maximum/rechargeType/rechargeAmount, and for multiple independent pools by
identifier, using the established `TagValueOutput`/`TagValueInput` real-serialization harness; full
sync-packet round-trip; and a pure-table test proving Barbarian class levels 25–30 clamp to the
level-25 `RAGE_CHARGES` value (the exact clamp expression `BarbarianRageAbility` itself uses),
confirming the audit's finding that no distinct authored value exists beyond entry 25. Not directly
testable without excessive scaffolding (documented, not built): `BarbarianRageAbility.canActivate`/
`onActivate`'s full activation sequence and `RageEffect.onEffectAdded`/`onEffectRemoved` — both
require a real `ServerPlayer`/`LivingEntity`/`MobEffectInstance` and multiple other component
systems.

## 16. Documentation corrections

- **`Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md` §2.4**: the
  "Status" row previously described Rage as "the best existing reference pattern for the new
  `PARTITIONED_POOL`/keyed-charge shape" — corrected in place to state Rage is `SCALAR` per canonical
  §6.1/§6.3/§25.6; `PlayerChargesComponent`'s generic `Identifier`-keyed map is the reusable pattern,
  not any individual pool's shape. A new "Phase 2E Implementation Record" section was also added,
  restating this correction and recording the phase's full scope.
- **`PlayerChargesRageCharacterizationTest`'s class Javadoc**: carried the identical inaccuracy
  ("used here as the reference precedent the Resource API's own `PARTITIONED_POOL`/keyed-scalar shape
  should generalize from") — corrected to the same accurate wording: `PlayerChargesComponent` is a
  useful generic owner pattern; each pool, including Rage's, is `SCALAR`; spell slots and future Hit
  Dice remain the true `PARTITIONED_POOL` examples.

No other historical section was rewritten. The legacy backing key `totality:barbarian_rage` was
preserved exactly in both corrections.

## 17. Tests added/updated

New: `RageResourceAdapterTest` (20 tests: id/scale/legacy-key identity, missing-Rage-pool-entry
semantics (directly automated — `freshComponentWithNoRagePoolReturnsStateUninitialized`,
`anUnrelatedPoolAloneStillReturnsStateUninitializedForRage`, `onlyBarbarianRageChargeIdIsEverRead`),
valid-state variations, malformed-state rejection via reflection, purity/exactness,
boundary/unrelated-pool isolation, client-query routing. **Not included:** a real `ServerPlayer` with
a genuinely missing `PlayerChargesComponent`. The production branch that reports
`MALFORMED_OWNER_STATE` for that case (`RageResourceAdapter.snapshot`, §8) is implemented but not
directly unit-tested — every test here calls `resolve(Identifier, PlayerChargesComponent)` with a real
component instance; constructing a real `ServerPlayer`/component-attachment state that is genuinely
broken outside the Minecraft runtime would require disproportionate scaffolding for a branch that
cannot occur through any production code path, since the component is unconditionally attached at
construction. `StandardSpellSlotsResourceAdapterTest`'s own `MALFORMED_OWNER_STATE` tests are the
same kind of limitation in spirit but cover a different case (malformed stored data via reflection,
not a genuinely missing `SpellSlotComponent`) — no adapter test in this codebase directly constructs
a real owner-component-missing `ServerPlayer` for any resource.).
Updated: `PlayerChargesRageCharacterizationTest` (+8 new tests, 1 doc correction, 9 pre-existing tests
unchanged and green); `PlayerResourceRegistryTest` (+5 new, 1 renamed/expanded, 1 corrected);
`PlayerResourceRegistryExternalAdapterFreezeTest` (+2 new, 1 count update);
`PlayerResourceStateComponentExternalSafetyTest` (+2 new, 1 count update);
`PlayerResourceStateComponentExternalEntryPathTest` (+2 new, 2 broad assertions extended);
`ResourceValueFormatterRegistryTest` (+1 new); `PlayerResourceStateComponentTest` (1 test fixed, see
§4). `ExternalPlayerResourceAdapterRegistryTest` required **no change** — every test in it constructs
isolated registries with no production-count assumption.

## 18. Exact final test count

**428 tests, 428 passed, 0 failed, 0 errors** (`./gradlew test --rerun-tasks`, aggregated from
`build/test-results/test/*.xml`: `tests="428"`, `failures="0"`, `errors="0"`, `skipped="0"`).

One failure was found and fixed during this pass: `PlayerResourceStateComponentTest.instantiatingWithModelMismatchThrows`
used the literal placeholder id `"rage"` for an arbitrary unregistered example — colliding with the
newly registered production `totality:rage`. Fixed by renaming the placeholder (§4); no test was
weakened or deleted.

## 19. Compile result

`compileJava`/`compileTestJava`: **BUILD SUCCESSFUL** (only a pre-existing deprecated-API note,
unrelated to this phase).

## 20. Datagen result and before/after status comparison

`git status --short` recorded immediately before and immediately after `./gradlew runDatagen`: the
two snapshots are byte-identical (`diff` produced no output). Datagen log: `Caching: total files:
349, old count: 349, new count: 349, removed stale: 0, written: 0`. **No new generated file was
written or modified.**

## 21. Full build result

`./gradlew build -x runDatagen`: **BUILD SUCCESSFUL**. `git diff --check`: exit code 0 — output
consisted only of pre-existing `LF will be replaced by CRLF` advisory notices (the repository's own
`core.autocrlf` behavior, present for the unrelated datagen files too) — no actual whitespace-error
or conflict-marker finding. `git diff --cached`: empty throughout.

## 22. Manual smoke-test result — PASSED (2026-07-20)

Stefan executed the manual smoke-test plan on the real Minecraft 26.2 client. **Result: PASSED for
every currently practical scenario.**

1. Existing Rage HUD pips remained visually unchanged.
2. The existing Character/Class-tab Rage charge display remained unchanged.
3. Activating Rage consumed exactly one charge.
4. The Rage effect icon, name, and duration appeared normally.
5. The existing Rage damage bonus remained functional.
6. Physical damage resistance remained functional.
7. Spellcasting remained blocked while Rage was active.
8. Toggling Rage off did not spend an additional charge.
9. Toggling Rage off did not refund the spent charge.
10. Spending all charges and attempting Rage again produced the existing rejection behavior.
11. Rage charges did not become negative.
12. Short Rest restored exactly one Rage charge.
13. Short Rest recovery remained clamped to the maximum.
14. Long Rest restored Rage charges fully.
15. Remaining Rage charges persisted correctly across logout and relog.
16. Death while raging ended the active Rage effect.
17. Death and respawn preserved the remaining Rage charge count rather than resetting or refilling it.
18. HUD and Class-tab charge displays remained correct after respawn.
19. No new Resource API, Rage adapter, owner-state, snapshot, synchronization, Rest, or Rage errors
    were observed.
20. No visible gameplay, HUD, Class-tab, effect, persistence, recovery, or synchronization regression
    occurred.

**Not practically testable in the current manual environment — not a failure, not a Phase 2E
blocker:** Strength-check advantage while raging, and Strength-save advantage while raging. The
relevant `RageEffect`/`RollModifierRegistry` production integration was untouched by Phase 2E, and the
existing automated/code-characterization evidence remains the available verification for these two
mechanics. No new commands, encounters, debug tools, or test content were built to exercise them during
Phase 2E.

**Phase 2E is COMPLETE and READY TO COMMIT.**

## 23. Deferred issues (documented, not fixed, per explicit task instruction)

1. `RAGE_CHARGES` is a 25-entry table; Barbarian class levels 25–30 currently remain clamped to the
   level-25 value (6) — no distinct authored Totality extension exists, unlike `SpellSlotTable`'s full
   1–30 authoring. Preserved now; revisit during a future Barbarian/class progression review.
2. `PlayerChargesComponent`'s join-time synchronization happens indirectly, through the
   `ClassLevelUpRegistry` re-fire cascade (`StatsServerEvents`'s JOIN handler → `BarbarianClass`'s
   callback → `updatePoolMax` → `sync()`), rather than an explicit unconditional line in
   `PlayerConnectionEvents.JOIN`'s own sync block. Not changed here.
3. No class-removal/respec path exists in the codebase today, so future Rage-pool revocation behavior
   is genuinely unresolved — not designed around an assumed future behavior in this phase.
4. `PlayerChargesComponent.readData` silently drops hard-parse failures (`try/catch Exception
   ignored`) and accepts semantically invalid integer values (negative, or current > maximum)
   without validation. The adapter rejects any visible instance of this as `MALFORMED_OWNER_STATE`
   but does not repair the legacy component's own lax parsing.
5. `PlayerChargesComponent.registerWithRestBus()` remains confirmed dead/no-op code (rest listening is
   actually wired via inline lambdas in `PlayerConnectionEvents`). Not removed in this query-adapter
   phase.
6. `SPENDABLE`, `RESTORABLE`, and `MAXIMUM_MODIFIERS` capabilities remain deferred to a future phase
   that implements generic mutation and authoritative maximum resolution.
7. Active-effect detailed hover descriptions remain future HUD/effect work, unrelated to this phase.
8. Rage ability activation is synchronous but not a formal rollback-capable transaction — see §14's
   activation robustness note. Not changed here; recorded for a future Ability API review.
9. Client-side Generic Resource API querying for Rage remains unavailable
   (`STATE_UNAVAILABLE_ON_THIS_SIDE`) despite `PlayerChargesComponent`'s own legacy client-side
   mirror — no new synchronization was added, per instruction.

## 24. `git diff --check` result

Exit code 0. Only pre-existing LF→CRLF advisory notices (also present for unrelated datagen files) —
no whitespace-error or conflict-marker finding.

## 25. `git status --short` (final, after the correction pass, before staging)

```
 M Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md
 M src/main/generated/data/minecraft/worldgen/noise_settings/overworld.json
 M src/main/generated/data/totality/loot_table/blocks/apothecary_table.json
 M src/main/generated/data/totality/loot_table/blocks/copper_cable.json
 M src/main/generated/data/totality/loot_table/blocks/deepslate_graphite_ore.json
 M src/main/generated/data/totality/loot_table/blocks/deepslate_ruby_ore.json
 M src/main/generated/data/totality/loot_table/blocks/electric_furnace.json
 M src/main/generated/data/totality/loot_table/blocks/flecked_whitestone.json
 M src/main/generated/data/totality/loot_table/blocks/generator.json
 M src/main/generated/data/totality/loot_table/blocks/graphite_ore.json
 M src/main/generated/data/totality/loot_table/blocks/limestone.json
 M src/main/generated/data/totality/loot_table/blocks/polished_whitestone.json
 M src/main/generated/data/totality/loot_table/blocks/polished_whitestone_bricks.json
 M src/main/generated/data/totality/loot_table/blocks/ritual_altar.json
 M src/main/generated/data/totality/loot_table/blocks/ritual_dais.json
 M src/main/generated/data/totality/loot_table/blocks/ruby_ore.json
 M src/main/generated/data/totality/loot_table/blocks/tin_ore.json
 M src/main/generated/data/totality/loot_table/blocks/true_wheat_crop.json
 M src/main/generated/data/totality/loot_table/blocks/whitestone.json
 M src/main/generated/data/totality/recipe/copper_gear.json
 M src/main/generated/data/totality/recipe/diamond_gear.json
 M src/main/generated/data/totality/recipe/gold_gear.json
 M src/main/generated/data/totality/recipe/iron_gear.json
 M src/main/java/zcylas/totality/api/rpg/classes/ChargeComponents.java
 M src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceIds.java
 M src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java
 M src/test/java/zcylas/totality/api/rpg/classes/PlayerChargesRageCharacterizationTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryExternalAdapterFreezeTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalEntryPathTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalSafetyTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/presentation/ResourceValueFormatterRegistryTest.java
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E.patch"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E_IMPLEMENTATION_REPORT.md"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E_MANIFEST.txt"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E_REVIEW_BUNDLE.zip"
?? "Context/Audit/TOTALITY_RESOURCE_API_PHASE_2E_RAGE_ADAPTER_IMPLEMENTATION_REPORT.md"
?? "Context/Trading Test/trade_screen2.png"
?? "Context/Trading Test/trade_screen3.png"
?? "Context/Trading Test/trade_screen4.png"
?? "Context/Trading Test/trade_screen5.png"
?? logs/
?? src/main/generated/.cache/
?? src/main/java/zcylas/totality/api/rpg/resources/external/RageResourceAdapter.java
?? src/test/java/zcylas/totality/api/rpg/resources/external/RageResourceAdapterTest.java
```

This is the complete, current status as of the end of the correction pass (§27). The four
`Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E.*` artifacts and the readiness-doc
modification are now present and reflected above; nothing is staged and nothing is committed. The
known unrelated files (`src/main/generated/data/**` JSON, the four `Context/Trading Test/trade_screen*.png`
screenshots, `logs/`, `src/main/generated/.cache/`) remain untouched throughout, exactly as at the
start of this session.

## 26. Explicit confirmations

- Nothing was staged (`git diff --cached` empty throughout, both the original implementation pass and
  this correction pass).
- Nothing was committed.
- No unrelated working-tree file was modified, discarded, restored, or cleaned — the pre-existing
  datagen JSON diffs, Trading Test screenshots, `logs/`, and `src/main/generated/.cache/` are
  byte-identical to their state at the start of this session.
- `/Inspiration Mods` was excluded from every search, decision, and artifact in this phase, including
  the correction pass.
- **No production gameplay behavior changed** in the correction pass — see §27. `RageResourceAdapter`'s
  one edit was Javadoc wording only (unmodifiable/read-only map-view terminology, §27.2); no method
  body, return value, or control flow changed.

## 27. Correction pass (2026-07-20)

A narrow review-correction pass was performed after Phase 2E's initial implementation, before manual
smoke testing, addressing four documentation-precision findings. **No architectural correction to
production code was required or made** — the correction pass is documentation-only plus one Javadoc
wording fix in `RageResourceAdapter.java` (§27.2), which does not change any method body, return
value, branch, or control flow.

### 27.1 Stale Rage/`PARTITIONED_POOL` statements corrected

Four passages in `TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md` that could still
be read as assigning `PARTITIONED_POOL` shape to Rage itself (beyond the §2.4 Status-row correction
already made during the initial Phase 2E pass, §16 above) were corrected in place:

1. **Executive Summary, Major Finding 1's Rage bullet** — previously read as if `PlayerChargesComponent`'s
   generic map shape made Rage itself architecturally `PARTITIONED_POOL`-like. Corrected to state:
   `PlayerChargesComponent` is a useful generic `Identifier`-keyed multi-pool *owner* pattern; each
   individual pool, including Rage, is `SCALAR`; the component's generic map shape is not evidence
   that each contained resource is partitioned.
2. **§3.5 Rage/class-charges file-map paragraph** — previously said this file set's structure "should be
   preserved in the new `PARTITIONED_POOL`/`ScalarResourceState` design." Corrected to: the
   `Identifier`-keyed *owner* pattern is worth preserving where useful; Rage itself maps to
   `ScalarResourceState`; standard spell slots and future Hit Dice are the true `PARTITIONED_POOL`
   examples.
3. **Migration Matrix, Rage charge-pool row** — previously said the future Rage owner is
   "`PARTITIONED_POOL`/keyed-scalar state." Corrected to state the canonical Resource API identity is
   already `totality:rage` (`SCALAR`, `EXTERNAL_ADAPTER`, Phase 2E); a future *storage* migration would
   import the exact stored current/maximum into `ScalarResourceState`; the legacy key
   `totality:barbarian_rage` remains the compatibility/migration source identifier until a separately
   reviewed migration retires it; that migration is explicitly not designed or implemented by this
   correction pass.
4. **Phase 2D's "Recommended next patch" conditional** — "if Rage's charge-pool shape also needs
   `PARTITIONED_POOL`..." — preserved verbatim as historical planning context, with an added note
   marking it superseded by the Phase 2E audit: Rage was confirmed `SCALAR` and required no
   partitioned query-result extension.

A full-document search for `PARTITIONED_POOL`, `partitioned`, `keyed-scalar`, and `keyed-charge` near
every Rage occurrence found no other genuinely contradictory or ambiguous statement; no unrelated
historical section was rewritten.

### 27.2 Read-only map-view terminology corrected

`PlayerChargesComponent.getAllPools()` was inspected directly:
`Collections.unmodifiableMap(pools)` — an unmodifiable *wrapper* around the component's live backing
`Map`, not a defensive copy. Three places called this an "immutable map view," which overstates it:

- `RageResourceAdapter.java`'s class Javadoc (the "Presence-aware pool lookup" section) — corrected to
  "unmodifiable/read-only," with an explicit note that callers cannot mutate the pool set through the
  returned `Map`, but a later legitimate `PlayerChargesComponent` mutation would still be reflected
  through the same view.
- This report's §6 (`resolve(resourceId, component)` step 1) — corrected identically.
- The readiness doc's Phase 2E Implementation Record ("What changed" paragraph) — corrected identically.

No genuine safety problem was found — `PlayerChargesComponent` was not modified, per instruction.

### 27.3 Test-coverage description corrected

§17 above previously described `RageResourceAdapterTest`'s 20 tests as including
"missing-entry/missing-component semantics." Verified directly against the test file: all 20 tests are
present and green, but none constructs a real `ServerPlayer` with a genuinely missing
`PlayerChargesComponent` — every test calls `resolve(Identifier, PlayerChargesComponent)` directly with
a real component instance. §17 was corrected to distinguish: missing Rage pool entry (directly
automated, three tests named) vs. missing owner component (production branch implemented as
`MALFORMED_OWNER_STATE` in `snapshot()`, §8, but not directly unit-tested, since constructing a
genuinely broken `ServerPlayer`/component-attachment state outside the Minecraft runtime would require
disproportionate scaffolding for a branch unreachable through any production code path). No Mockito or
fake-`ServerPlayer` framework was added — a repo-wide check confirmed no existing Resource API test
file already has such scaffolding, so building one for this single branch would not be "genuinely small
and trustworthy" per instruction. `RageResourceAdapterTest`'s own section-comment header was corrected
to match. The manual smoke-test plan (§22) is not claimed to exercise this state either — it is not
reachable through ordinary gameplay.

### 27.4 This report made self-contained and final

§25's git-status section previously recorded a pre-review-artifact snapshot with a forward-reference
placeholder ("appear once those steps complete"). Replaced with the final, current status (§25 above),
reflecting the four now-regenerated `Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E.*` files and this
report's own correction-pass edits. §28 (new) records review-artifact integrity results for the
regenerated bundle.

## 28. Review-artifact integrity (this correction pass)

The four `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E.*` artifacts were regenerated
after every correction in §27 was applied and validated (§6 below repeats the build/test results for
this pass). Exact byte sizes and SHA-256 digests for the patch, manifest, and ZIP are recorded in
`Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E_MANIFEST.txt` and in the correction pass's
final structured chat response — **not restated as literal hash strings inside this report**, for the
same self-referential reason the manifest documents about its own hash (§ "MANIFEST" row): this report
is itself embedded as a created file inside the regenerated patch (via `git diff --no-index /dev/null`),
so this report's own bytes cannot contain the exact hash of a patch that embeds those same bytes
without circularity. This mirrors, rather than works around, the manifest's own documented limitation.

Verification results from the regeneration (methods and full digests in the manifest):

- `git apply --check --reverse` against the working tree: **PASSED**, zero errors.
- Manifest hash-verification (every listed file's recomputed SHA-256 against the manifest's recorded
  value): **PASSED** for every entry.
- ZIP integrity (`unzip -t` / archive-open self-check): **PASSED**.
- Real-file count vs. any harmless zero-byte `Compress-Archive` directory-entry: recorded in the
  manifest's TOTALS section; this bundle was built without `Compress-Archive`, so no directory-entry
  padding is expected — confirmed in the manifest.
- Canonical (`Context/Audit/TOTALITY_RESOURCE_API_PHASE_2E_RAGE_ADAPTER_IMPLEMENTATION_REPORT.md`) vs.
  bundle-local (`Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_2E_IMPLEMENTATION_REPORT.md`)
  byte-identity: **PASSED** — both are byte-for-byte identical copies of this file's final content.
- No unrelated datagen JSON, screenshot, log, cache, or `/Inspiration Mods` content is included in the
  regenerated bundle.

## 29. Final Status — Ready to Commit (2026-07-20)

**Manual smoke test: PASSED.** See §22 for the complete itemized result (20/20 practically testable
scenarios passed; 2 Strength-advantage-while-raging scenarios not practically testable in the current
manual environment, classified as such, not as failures or blockers, per §22).

- **Automated tests:** 428/428 passed.
- **Compile:** succeeded.
- **Datagen:** wrote 0 files.
- **Full build:** succeeded.
- **Manual smoke test:** PASSED.

**Phase 2E is COMPLETE and READY TO COMMIT.** The scope boundaries recorded throughout this report
carry forward unchanged: `PlayerChargesComponent` remains the sole authoritative owner of Rage
gameplay state; the adapter is query-only; no casting/activation, consumption, rest, persistence,
synchronization, HUD, Class-tab, or effect behavior was changed.
