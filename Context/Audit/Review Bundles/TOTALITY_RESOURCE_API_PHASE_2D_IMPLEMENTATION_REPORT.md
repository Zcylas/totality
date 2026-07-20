# TOTALITY RESOURCE API — PHASE 2D: STANDARD SPELL-SLOT LEGACY ADAPTER — IMPLEMENTATION REPORT

Branch: `feature/general-resource-api`
Starting commit: `fbb836a` ("Add Mana and Stamina resource adapters")
Status at report time: uncommitted working-tree content. Nothing staged, nothing committed.

---

## 1. Purpose and scope

Expose the existing standard, multiclass-pooled spell-slot store (`SpellSlotComponent`) through the
Generic Player Resource API as one query-only, `PARTITIONED_POOL` external resource,
`totality:spell_slots`, with ten stable partitions (spell levels 1–10). `SpellSlotComponent` remains
the sole authoritative owner of every gameplay operation. This phase adds:

1. The smallest coherent partitioned external-query extension to the Generic Resource API itself
   (`PartitionedResourceSnapshot`, `ResourceQueryResult.PartitionedSuccess`, and routing/validation in
   `PlayerResourceService`).
2. `StandardSpellSlotsResourceAdapter`, the standard spell-slot external adapter.
3. Production registration of `totality:spell_slots`.
4. Automated characterization and safety coverage, including the first automated tests for the
   previously-untested legacy `SpellSlotComponent`/`SpellSlotTable`/`SpellSlotRecalculator` system.
5. This report and the review bundle.

No casting behavior, restoration behavior, packet, synchronization, HUD, menu, or spell-radial code
was changed. The intended (and verified, at the automated level) observable result is no gameplay
change.

## 2. Starting state verification

Confirmed before any edit: `git status --short` matched exactly the known unrelated set (datagen
JSON, Trading Test screenshots, `logs/`, `src/main/generated/.cache/`) plus nothing else. The Phase
2D read-only audit from the prior turn was used as the factual basis for every design decision below;
no additional re-audit was performed, since the audit's file/line citations were re-verified against
current source during this implementation pass (see §11).

## 3. Executive result

**Implemented as scoped.** All Phase 2D objectives were completed: the partitioned query extension,
the adapter, production registration, 388/388 automated tests passing (up from 301 at the end of
Phase 2C), a clean `compileJava`/`compileTestJava`/`build -x runDatagen`, zero new `runDatagen` output,
and a clean `git diff --check`. **Superseded by the Correction Pass (2026-07-20) appended at
the end of this report — the final automated count after that pass is 390/390, and manual smoke
testing has since been executed and PASSED (§21). This section describes the initial implementation
pass only; treat §21 and the Correction Pass section as authoritative for the current state.
Phase 2D is COMPLETE and READY TO COMMIT.**

## 4. Files added

- `src/main/java/zcylas/totality/api/rpg/resources/PartitionedResourceSnapshot.java` — the generic
  partitioned query-result snapshot type (with nested `ResourcePartitionSnapshot` record).
- `src/main/java/zcylas/totality/api/rpg/resources/external/StandardSpellSlotsResourceAdapter.java`
  — the standard spell-slot adapter.
- `src/test/java/zcylas/totality/api/rpg/resources/PartitionedResourceSnapshotTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/external/StandardSpellSlotsResourceAdapterTest.java`
- `src/test/java/zcylas/totality/api/magic/spell/SpellSlotTableTest.java`
- `src/test/java/zcylas/totality/api/magic/spell/SpellSlotComponentTest.java`
- `src/test/java/zcylas/totality/api/magic/spell/SpellSlotRecalculatorCharacterizationTest.java`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_2D_STANDARD_SPELL_SLOT_ADAPTER_IMPLEMENTATION_REPORT.md` (this file)

## 5. Files modified

- `src/main/java/zcylas/totality/api/rpg/resources/ResourceQueryResult.java` — added `PartitionedSuccess`.
- `src/main/java/zcylas/totality/api/rpg/resources/external/ExternalPlayerResourceAdapter.java` — contract doc update only; no interface method added or removed.
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceService.java` — `queryExternal` now routes by `definition.model()`; new `validatePartitionedExternalSnapshot`.
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceIds.java` — added `SPELL_SLOTS`/`SPELL_SLOTS_ADAPTER`.
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java` — registers the adapter and definition.
- `src/main/java/zcylas/totality/api/magic/spell/SpellSlotComponents.java` — added `maybeGet(ServerPlayer)`.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java` — count 5→6, new spell-slot-shape assertions, corrected the stale "no spell-slot definition yet" test.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryExternalAdapterFreezeTest.java` — adapter count 5→6, new spell-slot adapter-resolution/client-query tests.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceServiceTest.java` — 12 new partitioned-routing/validation tests; existing scalar tests untouched.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalSafetyTest.java` — spell-slots external-authority-rejection coverage.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalEntryPathTest.java` — new partitioned-shape stale-NBT quarantine test.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentTest.java` — **incidental fix**: two pre-existing (Phase 1) tests used the literal placeholder id `"spell_slots"` for an arbitrary unregistered `PARTITIONED_POOL` example. Now that `totality:spell_slots` is a real registered resource, that placeholder collided with production state and both tests started throwing `IllegalArgumentException`. Renamed the placeholder to `"test_partitioned_resource"` — no behavioral or intent change to either test, purely an id-collision fix made necessary by this phase's own registration. See §14 for the full failure/fix record.

**Untouched, as required:** Health, Food, Breath, Mana, Stamina adapters and their tests;
`ExternalPlayerResourceAdapterRegistryTest.java` (uses only isolated registries — no production count
assumption to update); `ActivateAbilityHandler`, `Spell`, `SpellSlotComponent`, `SpellSlotTable`,
`SpellSlotRecalculator`, `CasterProgression`, `SpellcastingProgressionRegistry`, `ClientSpellSlotManager`,
`SpellRadialScreen` (all read, none modified); any casting, combat, damage, or check code.

## 6. Partitioned query-result design

`PartitionedResourceSnapshot` (new, generic — no spell-level knowledge baked in):

```java
public record PartitionedResourceSnapshot(
        Identifier resourceId,
        NavigableMap<Integer, ResourcePartitionSnapshot> partitions,
        long unitScale
) {
    public record ResourcePartitionSnapshot(long currentUnits, long maximumUnits) {}
}
```

Design choices, matching the task's decisions exactly:

- **One map of complete per-partition entries**, not two parallel current/maximum maps — a partition
  can never have a current with no matching maximum key or vice versa.
- **Defensive copy into an internal `TreeMap`** in the compact constructor: enforces deterministic
  ascending order regardless of the caller's map implementation/order, rejects null keys/values
  (`Objects.requireNonNull` per entry), and makes the map itself immutable after construction
  (`Collections.unmodifiableNavigableMap`) — a caller's later mutation of their original map cannot
  retroactively change an already-returned snapshot.
- **Duplicate partition identities are impossible by construction** (a `Map` cannot hold a duplicate
  key) — no separate runtime check needed or added.
- **No overflow field.** No consumer needs it — the spell-slot adapter this type was built for has no
  use for it — and `PartitionedResourceState`'s (storage-layer) overflow concept is deliberately not
  reproduced at this query-snapshot layer; a future genuine need should extend this type
  deliberately, not have overflow invented here speculatively.
- **No spell-level-specific validation** (e.g. "must be levels 1–10") lives in this type — that
  resource-specific invariant belongs to the adapter (`StandardSpellSlotsResourceAdapter`), matching
  the instruction not to hard-code any single resource into the generic shape.

`ResourceQueryResult` gained one new sealed-interface case:

```java
sealed interface ResourceQueryResult {
    record Success(ResourceSnapshot snapshot) implements ResourceQueryResult {}
    record PartitionedSuccess(PartitionedResourceSnapshot snapshot) implements ResourceQueryResult {}
    record Failure(ResourceQueryFailureReason reason, Identifier resourceId) implements ResourceQueryResult {}
}
```

`isSuccess()` now returns `true` for either success variant. The existing `Success` record is
byte-for-byte unchanged (source-compatible) — every existing scalar adapter/test compiles and passes
unmodified.

`ExternalPlayerResourceAdapter.snapshot(...)`'s **signature is unchanged** — still one method
returning `ResourceQueryResult`. Only its Javadoc contract changed, to state explicitly that the
result shape must match the queried definition's `ResourceModel` (`Success` for `SCALAR`,
`PartitionedSuccess` for `PARTITIONED_POOL`), and that `PlayerResourceService` treats a mismatch as
`CORRUPT_ADAPTER_SNAPSHOT` rather than coercing one shape into the other. No parallel
`scalarSnapshot(...)`/`partitionedSnapshot(...)` methods were added — the single-method design
remained coherent and safe, since each adapter only ever needs to serve one model.

## 7. PlayerResourceService routing and validation — exact changes

`queryExternal` no longer rejects non-`SCALAR` models up front (that check was the exact thing
blocking `PARTITIONED_POOL` adapters per the audit). After the existing adapter-lookup/QUERY-support/
failure-passthrough logic (all unchanged), the result is now validated against `definition.model()`:

```java
return switch (definition.model()) {
    case SCALAR -> {
        if (!(adapterResult instanceof ResourceQueryResult.Success success))
            yield Failure(CORRUPT_ADAPTER_SNAPSHOT, ...);
        yield validateExternalSnapshot(success.snapshot(), definition);   // unchanged scalar validation
    }
    case PARTITIONED_POOL -> {
        if (!(adapterResult instanceof ResourceQueryResult.PartitionedSuccess partitionedSuccess))
            yield Failure(CORRUPT_ADAPTER_SNAPSHOT, ...);
        yield validatePartitionedExternalSnapshot(partitionedSuccess.snapshot(), definition);
    }
};
```

`ResourceModel` has exactly two values, so this `switch` is exhaustive — there is no third "unsupported
model" branch needed in `queryExternal` anymore (the task's "retain existing `UNSUPPORTED_MODEL`
behaviour" refers to `queryGenericState`, which is untouched — see §9).

New `validatePartitionedExternalSnapshot` (generic — no `totality:spell_slots` reference anywhere in
`PlayerResourceService`):

- snapshot's `resourceId` must equal the definition's id → else `CORRUPT_ADAPTER_SNAPSHOT`
- `unitScale` must match the definition's → else `CORRUPT_ADAPTER_SNAPSHOT`
- for every partition: `currentUnits >= 0`, `maximumUnits >= 0`, `currentUnits <= maximumUnits` → else `CORRUPT_ADAPTER_SNAPSHOT`
- null container/keys/values and duplicate identities are **structurally impossible** to reach this
  point at all, since `PartitionedResourceSnapshot`'s own compact constructor already guarantees them
  — the service does not re-derive guarantees the snapshot type itself already enforces.

Existing scalar validation (`validateExternalSnapshot`) is **untouched** — same code, same behavior.

## 8. StandardSpellSlotsResourceAdapter behaviour — exact

1. Non-`ServerPlayer` (client-side query) → `STATE_UNAVAILABLE_ON_THIS_SIDE`.
2. `SpellSlotComponents.maybeGet(serverPlayer)` (new, non-throwing) empty → `MALFORMED_OWNER_STATE`
   (**not** `STATE_UNAVAILABLE_ON_THIS_SIDE` — deliberately different from Mana/Stamina; see §10).
3. Otherwise, `resolve(...)` iterates levels 1 through `SpellSlotComponent.MAX_SPELL_LEVEL` (10) in
   ascending order, reading only `getMax(level)`/`getUsed(level)` — both pure array reads.
4. Per level: `maximum < 0 || used < 0 || used > maximum` → `MALFORMED_OWNER_STATE` immediately, no
   partial/successful result for any other level.
5. Otherwise `current = maximum - used`, stored into the partition map.
6. Returns `PartitionedSuccess` wrapping all ten levels, including any level whose max is 0.
7. Never calls `recalculate`, `useSlot`, `restoreAll`, `restoreSome`, or anything that triggers `sync()`.
8. `supportedOperations()` = `{QUERY}` only. `clientMirrorMode()` = `LEGACY_BESPOKE_SYNCHRONIZATION`.

## 9. All-zero state — exact behaviour

`SpellSlotComponent` has no initialization sentinel (confirmed during the audit and re-confirmed
during implementation: the arrays default to all-zero `int[]`, with no boolean/`-1`-style flag
anywhere). Per the canonical decision for this phase: **an attached component whose every level reads
0/0 is a valid, successful ten-partition snapshot** — never `STATE_UNINITIALIZED`. The adapter's code
has no branch that could produce `STATE_UNINITIALIZED` at all; it is simply never one of the three
reasons this adapter can return. `StandardSpellSlotsResourceAdapterTest.allZeroStateIsAValidSuccessNotUninitialized`
and `.freshComponentProducesAllTenLevelsAtZeroZero` characterize this directly.

## 10. Malformed-state behaviour — exact, including the deliberate Mana/Stamina divergence

| Situation | Reason | Matches Mana/Stamina precedent? |
|---|---|---|
| Client-side query | `STATE_UNAVAILABLE_ON_THIS_SIDE` | Yes |
| Missing `SpellSlotComponent` on a real server player | `MALFORMED_OWNER_STATE` | **No — deliberate divergence** |
| Negative maximum, any level | `MALFORMED_OWNER_STATE` | Yes (shape) |
| Negative used, any level | `MALFORMED_OWNER_STATE` | New case, same reason family |
| Used > maximum, any level | `MALFORMED_OWNER_STATE` | New case (Mana/Stamina explicitly allow current > max; spell slots do not — see below) |
| Adapter snapshot resourceId/unitScale mismatch | `CORRUPT_ADAPTER_SNAPSHOT` (service-level) | Yes |
| Any partition current/maximum invariant violated at the service layer | `CORRUPT_ADAPTER_SNAPSHOT` | Yes |
| Unexpected adapter-returned failure reason | `CORRUPT_ADAPTER_SNAPSHOT` | Yes |

Two deliberate, canonically-instructed divergences from Mana/Stamina, both implemented exactly as
specified and documented in the adapter's own Javadoc so a future reader does not "fix" them back to
match Mana/Stamina by mistake:

- **Missing component → `MALFORMED_OWNER_STATE`, not `STATE_UNAVAILABLE_ON_THIS_SIDE`.** Unlike
  Mana/Stamina's legacy component, `SpellSlotComponent` is unconditionally attached to every
  `ServerPlayer` at construction (`MixinServerPlayer` → `PlayerComponentEvents.attachComponentsTo`),
  so its absence for a real server player is not an ordinary transitional state — it indicates the
  owner itself is broken.
- **`current > maximum` is rejected, not permitted.** Mana/Stamina's adapters deliberately let current
  transiently exceed a live-recomputed maximum (e.g. right after a buff expires) because their
  maximum is computed fresh on every query. Spell slots have no live maximum computation at query
  time — `maxSlots`/`usedSlots` are both already-stored values, and normal legacy behavior
  (`recalculate` clamps `used` to the new `max` whenever it runs) cannot produce `used > max` without
  corrupted/malformed persisted data. The adapter therefore treats it as `MALFORMED_OWNER_STATE`
  rather than passing it through unclamped.

## 11. Server/client authority behaviour

Confirmed identical to the audit: no vanilla-native or generic-API client sync exists for spell
slots — only the legacy bespoke packet (`SpellSlotComponent#writeSyncPacket`/`applySyncPacket`) and
`ClientSpellSlotManager`, read directly by `SpellRadialScreen`. `clientMirrorMode()` declares
`LEGACY_BESPOKE_SYNCHRONIZATION`. The adapter contains no import of and no code path reaching
`ClientSpellSlotManager`. No new packet, no new synchronization, no HUD/menu/radial change was added.
The pre-existing possible client-mirror staleness after respawn (`ClientSpellSlotManager` isn't
explicitly re-synced on `AFTER_RESPAWN`, only on join/mutation) is confirmed still present and is
**not** fixed by this phase — recorded as a deferred issue (§19) exactly as instructed.

## 12. Standard slots vs. Pact Magic boundary

Confirmed unchanged and untouched: `CasterProgression.WARLOCK` remains excluded from
`SpellSlotRecalculator`'s pooling; `SpellSlotTable.WARLOCK_PACT`/`forWarlock(...)` were not modified
(only read by a new characterization test, `SpellSlotTableTest`, which asserts values without
changing any). No Pact Magic partition, item charge, Sorcery Point, or granted-use data can appear in
a `totality:spell_slots` snapshot — the adapter's loop only ever reads `SpellSlotComponent`'s own
1..10 arrays; there is no code path by which anything else could enter the map.
`StandardSpellSlotsResourceAdapterTest.snapshotNeverContainsMoreThanTenPartitions` and
`SpellSlotRecalculatorCharacterizationTest.noPactMagicDataResultsFromTheStandardPoolComposition`
characterize this directly. Per the task's explicit note, this boundary is about storage/query
identity only — it does not prohibit a future Spell API from letting an eligible spell be paid from
either pool; that is out of scope here and unaffected by anything in this phase.

## 13. Legacy spell-slot characterization findings (new automated coverage)

The prior audit found **zero** automated coverage for `SpellSlotComponent`, `SpellSlotTable`,
`SpellSlotRecalculator`, or `CasterProgression`'s spell-slot behavior. This phase adds:

- **`SpellSlotTableTest`** (17 tests): table dimensions (30 levels × 10 tiers for Full/Half; 30×2 for
  Warlock Pact), full-caster level 1 and level 20 (D&D 5e cap) exact values, the authored epic
  extension's 10th-level-slot unlock at level 25 and second unlock at level 30, half-caster level 1
  (zero) and level 20, Warlock's permanent 5th-tier cap from level 9 onward and growing slot count,
  `combinedCasterLevel`'s full/half(÷2 floor)/third(÷3 floor) pooling and 30-level cap, and the
  structural fact that `combinedCasterLevel` has no Warlock parameter at all.
- **`SpellSlotComponentTest`** (13 tests): fresh all-zero default, `recalculate` storing new maxima
  and clamping `used` down when maximum shrinks, `useSlot` consuming/rejecting correctly, Long Rest
  full restoration, **Short Rest not restoring standard slots** (characterizes `restoreSome`'s
  continued non-wiring), invalid external levels (0, 11) reading as 0 rather than throwing, full NBT
  persistence round-trip (real `TagValueOutput`/`TagValueInput`, matching the Resource API's own
  established real-serialization test precedent), `copyFrom`/`ALWAYS_COPY` behavior, and repeated
  read-only queries never mutating state.
- **`SpellSlotRecalculatorCharacterizationTest`** (7 tests): `SpellcastingProgressionRegistry`
  register/get round-trips for Full/Half/Third/Warlock, a hand-composed multiclass bucketing test
  reproducing exactly what `recalculate` does internally (5 Full + 4 Half + 6 Third = combined level
  9, Warlock's 3 levels contributing 0), and confirmation that a Warlock-only composition never
  touches `WARLOCK_PACT`/`forWarlock`. **`SpellSlotRecalculator.recalculate(ServerPlayer)` itself is
  not directly unit-tested** — it requires a real component-attached `ServerPlayer`
  (`ClassComponents.get`/`SpellSlotComponents.get` both cast to `ComponentProvider`), which cannot be
  constructed outside a running Minecraft server without an excessive fake-gameplay scaffold built
  purely to exercise one helper method. Its correctness is characterized by proving its two real
  dependencies (the registry mapping, and `SpellSlotTable`'s combining/lookup functions) correctly in
  isolation — the method itself has no additional branching logic beyond composing them. This is
  documented here per the task's own permission to document rather than over-engineer test scaffolding.

Existing behavior confirmed **unchanged** by reading (not modifying) `Spell.java` and
`ActivateAbilityHandler.java`: cantrips remain free; leveled spells still pre-check `hasSlot` and
post-consume via `useSlot` only after `castSucceeded`; there is still no upcast picker (always the
spell's own declared minimum level).

## 14. Test failures found and fixed during implementation

Running the full suite after the initial implementation pass surfaced **three** failures, all
diagnosed and fixed (none weakened or deleted):

1. **`PlayerResourceStateComponentTest.instantiatePartitionedCreatesStateExactlyOnce`** and
   **`.copyFromDuplicatesStatesIndependently`** — both pre-existing Phase 1 tests used the literal
   string `"spell_slots"` as an arbitrary example `PARTITIONED_POOL` resource id (chosen before this
   phase existed, when that id was unregistered and therefore harmless). Registering the real
   `totality:spell_slots` `EXTERNAL_ADAPTER` definition made that placeholder collide with
   production state, so `instantiatePartitioned`'s `rejectExternalAuthority` guard began correctly
   throwing `IllegalArgumentException` for it. **Fix:** renamed the placeholder id in both tests to
   `"test_partitioned_resource"` — identical test intent and assertions, just a non-colliding id. This
   is a necessary, minimal consequence of this phase's own registration, not a weakening.
2. **`StandardSpellSlotsResourceAdapterTest.resolveDoesNotMutateTheComponent`** — a genuine authoring
   mistake in this phase's own new test: it called `useSlot(1)` once (used should be 1) but asserted
   `getUsed(1) == 3`. Fixed the expected value to `1`; the test's actual purpose (proving `resolve`
   never mutates the component) was otherwise correct and unaffected.

After both fixes: 388/388 tests passed, 0 failures, 0 errors.

## 15. Automated tests added or updated — summary

New test files: `PartitionedResourceSnapshotTest` (14 tests), `StandardSpellSlotsResourceAdapterTest`
(20 tests), `SpellSlotTableTest` (17), `SpellSlotComponentTest` (13), `SpellSlotRecalculatorCharacterizationTest` (7).
Updated: `PlayerResourceRegistryTest` (+5 new, 1 corrected), `PlayerResourceRegistryExternalAdapterFreezeTest`
(+3 new, 1 renamed for the count bump), `PlayerResourceServiceTest` (+12 new partitioned-routing tests),
`PlayerResourceStateComponentExternalSafetyTest` (+3 new), `PlayerResourceStateComponentExternalEntryPathTest`
(+2 new: partitioned-shape stale-quarantine test, plus SPELL_SLOTS lines added to two existing broad
assertions), `PlayerResourceStateComponentTest` (2 tests fixed, see §14).
`ExternalPlayerResourceAdapterRegistryTest` required **no change** — every test in it constructs
isolated registries and asserts no production-count-specific value.

## 16. Exact test count and results

**388 tests total, 388 passed, 0 failed, 0 errors** (`./gradlew test --rerun-tasks`, aggregated from
`build/test-results/test/*.xml`: `tests="388"`, `failures="0"`, `errors="0"`, `skipped="0"`).

## 17. Compile result

`./gradlew compileJava` — **BUILD SUCCESSFUL** (only a pre-existing deprecated-API note, unrelated to
this phase). `./gradlew compileTestJava` — **BUILD SUCCESSFUL**, no warnings.

## 18. Datagen result

`git status --short` recorded before and after `./gradlew runDatagen`; the two snapshots are
byte-identical (`diff` produced no output). Datagen log: `Caching: total files: 349, old count: 349,
new count: 349, removed stale: 0, written: 0`. **No new generated file was written or modified.** The
pre-existing datagen JSON diffs in the working tree (loot tables, recipes, noise settings) are
unrelated to Phase 2D and were not touched, regenerated, or included in any Phase 2D artifact.

## 19. Full build result

`./gradlew build -x runDatagen` — **BUILD SUCCESSFUL** (compile, resources, jar, sourcesJar, assemble,
test — all up-to-date/succeeded, `check` succeeded).

`git diff --check` — exit code 0. Output consisted only of pre-existing `LF will be replaced by CRLF`
advisory notices (the repository's own `core.autocrlf` line-ending behavior, present for the
unrelated datagen files too) — **no actual whitespace-error or conflict-marker finding**.

## 20. Deferred issues (per explicit task instruction — documented, not fixed)

- **Client mirror staleness after respawn** (pre-existing, unrelated to this phase):
  `ClientSpellSlotManager` is not explicitly re-synced on `AFTER_RESPAWN` — only on join or the next
  mutating action. Confirmed still present; not fixed here.
- **`queryGenericState` still rejects `PARTITIONED_POOL`** for `GENERIC_COMPONENT`-authority
  resources. Deliberately **not** extended in this phase (Phase 2D scoped the extension to the
  `EXTERNAL_ADAPTER` path only, per the task's explicit instruction not to broaden into a full
  `GENERIC_COMPONENT` partitioned-query implementation). Confirmed still returns `UNSUPPORTED_MODEL`
  via a new regression-guard test (`queryGenericStateStillRejectsPartitionedPoolAfterThePhase2DExternalExtension`).
  This remains a documented future Resource API gap, not silently/partially implemented.
- **`authoredBaseMaximum` omitted** for `totality:spell_slots` — that field is a single `long`
  (inherently scalar-shaped) and cannot represent ten independent per-level maxima. No workaround was
  invented; the field is simply left absent (`OptionalLong.empty()`), matching the builder's own
  default and passing registry validation cleanly (that check only fires when the field is present).
- **`ResourceStateAuthority`'s Javadoc drift**: its class-level Javadoc still lists "spell slots" as
  an example of a `GENERIC_COMPONENT` resource (a stale forward-reference from before this phase's
  design was finalized as `EXTERNAL_ADAPTER`). Not corrected in this pass — a one-line doc fix,
  deliberately left for a future pass that touches that file for its own reasons, per the task's
  instruction to document rather than perform unrelated cleanup.
- **`restoreSome(...)` remains unused** — confirmed still dead code (no call site anywhere in the
  repository). It must **not** be read as evidence of Pact Magic ownership by `SpellSlotComponent`;
  Pact Magic is not implemented anywhere yet. Left entirely untouched.
- **No formatter registered for `totality:spell_slots`.** `ResourceValueFormatter`'s
  `toDisplayCurrent`/`toDisplayMaximum` methods take a single scalar `ResourceSnapshot` and have no
  partitioned counterpart — registering one for a 10-partition resource is not currently meaningful.
  A future phase extending `ResourceValueFormatter` itself (not part of this phase's narrow scope)
  would be required before this resource could register one.
- **Future cast-commitment vs. combat-outcome distinction** (documented per the task's explicit
  request, not implemented — no casting/combat/damage code was touched): the current
  `ActivateAbilityHandler`/`Spell.markNoEffect()`/`didCastSucceed()` behavior is legacy
  characterization only. A future generic Spell API must distinguish (a) a cast rejected before
  commitment — must not consume a slot — from (b) a cast that successfully commits — a slot or other
  payment should be consumed at that point — from (c) combat/effect resolution after commitment. The
  following post-commit outcomes must **not** refund a slot once consumed: an attack-roll spell
  misses; a target succeeds on its saving throw; Mirror Image intercepts a confirmed hit; immunity
  prevents the effect; resistance reduces damage to zero; a reaction prevents/redirects the effect; a
  projectile later misses or is destroyed; any other resolved combat outcome producing no effect. This
  is a future Spell API / Combat API / Damage API design concern — `ActivateAbilityHandler`,
  `Spell.markNoEffect()`, `didCastSucceed()`, the Combat API, the Damage API, Mirror Image, spell
  attacks, and saving throws were all read but not modified in this phase.

## 21. Manual smoke-test result — PASSED (2026-07-20)

Stefan executed the manual smoke-test plan on the real Minecraft 26.2 client. **Result: PASSED for
every currently applicable scenario.**

1. Existing spell-slot display remained unchanged.
2. `/totality spellslots` displayed sensible maximum, used, and remaining values.
3. Casting a leveled spell consumed the correct standard slot exactly once.
4. Attempting to cast without an available suitable slot produced the existing rejection behaviour.
5. Cantrips consumed no spell slot.
6. Long Rest restored standard spell slots exactly as before.
7. Spent spell-slot state persisted correctly across logout and relog.
8. Death and respawn preserved the existing spell-slot lifecycle behaviour.
9. No new Resource API, partitioned-snapshot, adapter-registration, or spell-slot errors were
   observed in the logs.
10. No visible gameplay, HUD, radial, synchronization, persistence, casting, or restoration
    regression was observed.

**Not applicable — not a failure or blocker:** the planned standard-spellcasting multiclass smoke
test (pooling a Full/Half/Third caster combination in-game) was not exercisable with the currently
implemented classes. Warlock is the only other currently relevant arcane class, Warlock Pact Magic is
not implemented yet, and Pact Magic is intentionally separate from the standard multiclass spell-slot
pool in any case (§12) — there is no second real caster class available yet to combine with the
first for an in-game multiclass check. The existing automated characterization tests
(`SpellSlotTableTest`'s `combinedCasterLevel*` tests, §13) remain the evidence for pooled
full/half/third-caster calculations; this gap is pre-existing (no multiclass caster combination has
ever been manually smoke-tested for this system) and not something Phase 2D introduced or could have
closed on its own.

**One log warning observed, classified as unrelated:** `Received passengers for unknown entity`
appeared once during the Long Rest test. This is believed to originate from the existing Rest
animation/passenger mechanism used to place the player into the sleeping pose — **unrelated to Phase
2D, non-blocking, associated with existing Rest animation synchronization/ordering, not a Resource
API or spell-slot regression.** No visible stuck pose, mount, dismount, movement, or other gameplay
problem was observed alongside it. Not investigated or fixed as part of Phase 2D, per instruction.

**Phase 2D is COMPLETE and READY TO COMMIT.**

## 22. `git diff --check` result

Exit code 0. Only pre-existing LF→CRLF advisory notices (also present for unrelated datagen files) —
no whitespace-error or conflict-marker finding.

## 23. `git status --short`

```
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
 M src/main/java/zcylas/totality/api/magic/spell/SpellSlotComponents.java
 M src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceIds.java
 M src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceService.java
 M src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java
 M src/main/java/zcylas/totality/api/rpg/resources/ResourceQueryResult.java
 M src/main/java/zcylas/totality/api/rpg/resources/external/ExternalPlayerResourceAdapter.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryExternalAdapterFreezeTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceServiceTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalEntryPathTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalSafetyTest.java
 M src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentTest.java
?? "Context/Audit/TOTALITY_RESOURCE_API_PHASE_2D_STANDARD_SPELL_SLOT_ADAPTER_IMPLEMENTATION_REPORT.md"
?? "Context/Trading Test/trade_screen2.png"
?? "Context/Trading Test/trade_screen3.png"
?? "Context/Trading Test/trade_screen4.png"
?? "Context/Trading Test/trade_screen5.png"
?? logs/
?? src/main/generated/.cache/
?? src/main/java/zcylas/totality/api/rpg/resources/PartitionedResourceSnapshot.java
?? src/main/java/zcylas/totality/api/rpg/resources/external/StandardSpellSlotsResourceAdapter.java
?? src/test/java/zcylas/totality/api/magic/
?? src/test/java/zcylas/totality/api/rpg/resources/PartitionedResourceSnapshotTest.java
?? src/test/java/zcylas/totality/api/rpg/resources/external/StandardSpellSlotsResourceAdapterTest.java
```

(The readiness doc update, review-bundle artifacts, and this report itself appear once those steps
complete — see the final structured response for the fully up-to-date status.)

## 24. Explicit confirmations

- Nothing was staged (`git diff --cached` empty throughout).
- Nothing was committed.
- No unrelated working-tree file was modified, discarded, restored, or cleaned — the pre-existing
  datagen JSON diffs, Trading Test screenshots, `logs/`, and `src/main/generated/.cache/` are
  byte-identical to their state at the start of this session.
- `/Inspiration Mods` was excluded from every search, decision, and artifact in this phase.

---

## Correction Pass (2026-07-20, same day)

A narrowly scoped, architecturally-approved review-correction pass fixed three issues found on
review of the initial implementation above. No gameplay behavior changed.

### 1. Spell-slot unit scale made adapter-owned, not definition-borrowed

**Problem:** `StandardSpellSlotsResourceAdapter.resolve(...)` took the queried definition's own
`unitScale()` as a parameter and copied it directly into the produced `PartitionedResourceSnapshot`.
This meant a `PlayerResourceDefinition` for `totality:spell_slots` accidentally registered with a
different `unitScale` (e.g. `5` instead of `1`) would cause the adapter to silently relabel its
unconverted, already-discrete slot counts as if they were expressed at that scale — and would make
`PlayerResourceService`'s unit-scale mismatch check (`validatePartitionedExternalSnapshot`) a
tautology, since the adapter's output would always trivially match whatever the definition declared.

**Fix:**
- Added `public static final long UNIT_SCALE = 1L;` to `StandardSpellSlotsResourceAdapter` — the
  canonical, adapter-owned scale, documented as a property of the legacy owner (`SpellSlotComponent`
  stores discrete counts, no fixed-point scaling) rather than of any definition.
- `resolve(Identifier, SpellSlotComponent)` no longer takes a `unitScale` parameter at all — it
  always constructs the snapshot with `UNIT_SCALE`, structurally incapable of producing anything else.
- `snapshot(...)`'s call to `resolve(...)` no longer passes `definition.unitScale()`.
- `ProductionResourceDefinitions` now declares `.unitScale(StandardSpellSlotsResourceAdapter.UNIT_SCALE)`
  instead of the literal `1` — the definition and the adapter can no longer drift apart, since both
  read from the same constant.

**Tests added/updated** (`StandardSpellSlotsResourceAdapterTest`): `resolveDoesNotMutateTheComponent`
and every other `resolve(...)` call site updated to the new two-argument signature (the `, 1` argument
removed everywhere — 14 call sites); `resourceIdAndUnitScaleArePreservedOnTheSnapshot` renamed to
`resourceIdIsPreservedAndUnitScaleIsAlwaysTheCanonicalConstant` and now asserts against
`StandardSpellSlotsResourceAdapter.UNIT_SCALE` rather than the literal `1L`; new
`unitScaleConstantIsExactlyOne` (proves the constant's value directly); new
`resolveAlwaysProducesTheCanonicalUnitScaleRegardlessOfAnyDefinitionConfiguration` (documents that
`resolve` cannot be swayed by any definition configuration, since it no longer accepts one at all).
`PlayerResourceRegistryTest.spellSlotsDefinitionIsPartitionedPoolExternalAdapter` now asserts
`definition.unitScale()` equals `StandardSpellSlotsResourceAdapter.UNIT_SCALE` rather than the literal
`1L`, proving the definition and adapter constant stay in lockstep. The existing generic service
unit-scale mismatch tests (`PlayerResourceServiceTest.mismatchedUnitScaleInSnapshotProducesCorruptAdapterSnapshotFailure`
for scalar, `.partitionedUnitScaleMismatchBecomesCorruptAdapterSnapshot` for partitioned) required no
change — both already construct their own hand-built definitions/adapters independent of
`StandardSpellSlotsResourceAdapter`, and both still pass unchanged, confirming this correction did not
weaken that validation.

No gameplay behavior changed: the legacy `SpellSlotComponent` store, its slot counts, and every
consumer of `totality:spell_slots` snapshots are unaffected — this was a query-result-shape
correctness fix at the Resource API layer only.

### 2. Deterministic-ordering test corrected to a genuine non-ascending input

**Problem:** `PartitionedResourceSnapshotTest.enforcesDeterministicAscendingOrderRegardlessOfInsertionOrder`
built its out-of-order source as `new TreeMap<>(outOfOrder)` using natural (ascending) ordering — which
itself re-sorts to ascending order immediately upon construction, before `PartitionedResourceSnapshot`'s
own compact constructor ever saw the map. The test therefore proved nothing about the constructor's own
reordering behavior; it could not have failed even if that behavior were removed entirely.

**Fix:** The test now builds the source map as `new TreeMap<>(Comparator.reverseOrder())`, inserts
partitions `9`, `1`, `5` (in that order), asserts the source map itself genuinely iterates descending
(`9, 5, 1`) as an explicit sanity check *before* constructing the snapshot, and then asserts
`PartitionedResourceSnapshot`'s own output iterates ascending (`1, 5, 9`) afterward. This proves the
constructor's own defensive `TreeMap` copy — not an accident of the caller's map choice — is
responsible for the ascending guarantee. `PartitionedResourceSnapshot`'s production ordering
implementation was not changed; the corrected test confirms it already behaves correctly.

### 3. Documentation corrections

- **Test-count accuracy:** every Phase 2D report occurrence describing the pre-Phase-2D test count as
  "~292" has been corrected to the precise figure, **301** (the exact count at the end of Phase 2C,
  confirmed by the Phase 2C report/readiness record). The corrected transition, end to end: **301**
  (end of Phase 2C) → **388** (initial Phase 2D implementation pass) → **390** (after this correction
  pass, which added 2 net new tests — see §1 above). The readiness document's own Phase 2D record did
  not contain the "~292" inaccuracy and required no change on this point.
- **`PartitionedResourceSnapshot` null-rejection comment:** the compact constructor's comment
  previously claimed "rejects a null key/value outright (`TreeMap#put` throws `NullPointerException`
  for either)" — inaccurate for the value case. `TreeMap` (with natural ordering) does independently
  reject a null *key* (comparing it against existing keys throws `NullPointerException`), but it does
  **not** reject a null *value* — `TreeMap`, like every `java.util.Map` implementation, permits null
  values freely, since values are never compared for ordering. The actual null-value rejection comes
  entirely from the explicit `Objects.requireNonNull(value, ...)` check already present in the
  constructor's loop, not from the `TreeMap#put` call itself. The comment was rewritten to state this
  precisely: the explicit `Objects.requireNonNull` checks reject both null keys and null values; the
  `TreeMap` copy is responsible only for deterministic natural ascending ordering (and, incidentally,
  would itself also reject a null key, but that is not what the explicit checks exist to guarantee).
  **No runtime behavior changed** — this was a comment-only correction; the explicit null checks were
  already present and already being exercised by `PartitionedResourceSnapshotTest`'s existing
  `nullPartitionKeyIsRejected`/`nullPartitionValueIsRejected` tests, which continue to pass unchanged.

### 4. Validation (this pass)

`./gradlew compileJava compileTestJava` — **BUILD SUCCESSFUL**. `./gradlew test --rerun-tasks` —
**390/390 tests passed, 0 failures, 0 errors** (aggregated from `build/test-results/test/*.xml`:
`tests="390"`, `failures="0"`, `errors="0"`, `skipped="0"`). `./gradlew runDatagen` — **BUILD
SUCCESSFUL**, `written: 0`; `git status --short` recorded immediately before and immediately after
`runDatagen` is byte-identical (`diff` produced no output). `./gradlew build -x runDatagen` — **BUILD
SUCCESSFUL**. `git diff --check` — exit code 0, only pre-existing LF→CRLF advisories (same files as
every prior validation pass), no whitespace-error or conflict-marker finding. `git diff --cached` —
empty.

### 5. Files changed by this correction pass

- `src/main/java/zcylas/totality/api/rpg/resources/external/StandardSpellSlotsResourceAdapter.java`
  (added `UNIT_SCALE`; `resolve(...)` signature narrowed; `snapshot(...)` call site updated)
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java`
  (`.unitScale(...)` now references the adapter constant)
- `src/main/java/zcylas/totality/api/rpg/resources/PartitionedResourceSnapshot.java` (comment
  correction only — no logic change)
- `src/test/java/zcylas/totality/api/rpg/resources/external/StandardSpellSlotsResourceAdapterTest.java`
  (14 call sites updated to the new `resolve` signature; one test renamed/updated; two new tests added)
- `src/test/java/zcylas/totality/api/rpg/resources/PartitionedResourceSnapshotTest.java` (the
  ordering test corrected to a genuinely non-ascending input)
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java` (one assertion
  updated to reference the adapter constant instead of a literal)
- This report (executive-result note plus this Correction Pass section)
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md` (Phase 2D record's
  test-count figures updated from 388 to 390; correction pass noted)

No other production, test, or documentation file was touched by this pass.

### 6. Final status after this pass

**Correction pass complete. 390/390 automated tests pass.** Architecturally approved per the
correction-pass instructions. **Manual smoke testing has since been executed by Stefan and PASSED
for every currently applicable scenario — see §21 above for the full result, including the one
noted-but-unrelated Rest-animation log warning and the not-applicable-yet multiclass scenario.
Phase 2D is COMPLETE and READY TO COMMIT.**

---

## Final Status — Ready to Commit (2026-07-20)

**Manual smoke test: PASSED.** See §21 for the complete itemized result (10/10 applicable scenarios
passed; 1 scenario not yet applicable and not a blocker, per §21; 1 unrelated log warning classified
and left uninvestigated, per instruction).

- **Automated tests:** 390/390 passed.
- **Compile:** succeeded.
- **Datagen:** wrote 0 files.
- **Full build:** succeeded.
- **Manual smoke test:** PASSED.

**Phase 2D is COMPLETE and READY TO COMMIT.** The scope boundaries recorded throughout this report
carry forward unchanged: `SpellSlotComponent` remains the sole authoritative owner of standard
spell-slot state; the adapter is query-only; no casting, rest, persistence, synchronization, HUD, or
radial behavior was changed; Pact Magic remains unimplemented and unaffected.
