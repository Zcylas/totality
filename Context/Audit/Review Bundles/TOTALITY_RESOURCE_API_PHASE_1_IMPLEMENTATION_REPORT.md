# TOTALITY GENERIC PLAYER RESOURCE API — PHASE 1 IMPLEMENTATION REPORT

**Purpose:** standalone review-bundle report summarizing the Phase 0/1 inert foundation implementation, including the pre-commit correction pass, for another reviewer inspecting the ZIP/patch independent of the full session history. This is a companion document to the canonical `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md`, not a replacement for it — that report remains authoritative for the full audit, the Oxygen-to-Breath addendum, the original Stage 2 implementation record, and the Stage 2 Pre-Commit Correction Record.

**Revision note (2026-07-18, correction pass):** this report was substantially updated after a focused pre-commit correction pass. Section 10 (Known limitations) previously described `ResourceInitializationPolicy` as a deliberate simplification of the canonical grant-initialization contract — that simplification has since been replaced with the canonical contract itself (§4 below); see §13 for the full correction summary.

**Revision note (2026-07-18, finalization):** the manual client smoke test has since been executed and passed — see §14 (new). Every earlier statement in this report saying the manual smoke test was "not executed" (§10, §13) describes the state as of the correction pass and is now superseded by §14; both are left in place, clearly marked, rather than deleted, so the sequence of what was verified when remains traceable.

---

## 1. Scope

**Branch:** `feature/general-resource-api`, based on `master` @ `bc16cc3` ("Merge Provisioner Phase 4"). All work described here is uncommitted on this branch.

**Purpose of Phase 0/1:** land the minimal, additive, behavior-neutral registry/definition/state foundation for the Generic Player Resource API — the layer every later migration (Health/Food adapters, then Mana/Stamina/Rage/spell-slot migration) will build on — without migrating any existing resource or changing any observable behavior. This follows the canonical design's own Phase 0 ("reconnaissance and frozen behavior tests") and Phase 1 ("core registry, definitions, state, and service — do not migrate production resources yet") sequencing.

**Confirmation: no existing resource was migrated.** HP, Stamina, Mana, Rage, Food/Hunger, and Breath/Oxygen all continue to use exactly their pre-existing storage, managers, packets, and Rest wiring. `PlayerResourceRegistry.INSTANCE` contains zero registered definitions after this patch (still true after the correction pass — confirmed by test). Nothing in production code calls `instantiateScalar`/`instantiatePartitioned` on the new component, and nothing calls its `sync()` method.

**Confirmation: gameplay and HUD behavior are unchanged.** `./gradlew runDatagen` reports `written: 0` (349 cached files, 0 changed) — no loot table, recipe, or other generated data changed, re-verified after the correction pass. No HUD rendering file, ability, class, species, or Rest file was touched. The only production behavioral surface touched is `ModComponents.register()`, which now additionally registers one new, empty, never-synced component alongside the existing ones.

---

## 2. Files created and modified

All paths are project-relative. This reflects the state after the pre-commit correction pass — every file below is still uncommitted against `master` (nothing in this patch has ever been committed), so files edited during the correction pass remain classified relative to git HEAD (untracked = CREATED; the two originally-tracked files remain MODIFIED).

### Production files (17 created, 3 modified since master)

| Path | Status | Purpose |
|---|---|---|
| `src/main/java/zcylas/totality/api/rpg/resources/ResourceModel.java` | CREATED | `SCALAR` / `PARTITIONED_POOL` storage-model enum. |
| `src/main/java/zcylas/totality/api/rpg/resources/ResourcePolarity.java` | CREATED | `HIGH_IS_GOOD` / `HIGH_IS_BAD` / `TARGET_RANGE` / `NEUTRAL` enum. **Correction pass:** Temperature-specific wording removed from the `TARGET_RANGE` Javadoc; replaced with neutral "bounded range" wording and an explicit note that Temperature is owned by a future Environment/Physiology system. |
| `src/main/java/zcylas/totality/api/rpg/resources/ResourceStateAuthority.java` | CREATED | `GENERIC_COMPONENT` / `EXTERNAL_ADAPTER` enum. |
| `src/main/java/zcylas/totality/api/rpg/resources/ResourceCapability.java` | CREATED | 16-value feature-declaration enum (SPENDABLE, RESTORABLE, ...). |
| `src/main/java/zcylas/totality/api/rpg/resources/ResourceDeathPolicy.java` | CREATED | 6-value death-handling enum. |
| `src/main/java/zcylas/totality/api/rpg/resources/ResourceLifecyclePolicy.java` | CREATED | Record combining death policy, persistence flags, and (**correction pass**) the canonical `ResourceGrantInitialization` — field type and field order now match canonical §17.2 exactly (`initializationPolicy` moved to the last position). Replaces the removed `ResourceInitializationPolicy` enum. |
| `src/main/java/zcylas/totality/api/rpg/resources/ResourceTargetRange.java` | CREATED | Record for `TARGET_RANGE`-polarity resources. **Correction pass:** Temperature-specific wording removed; compact constructor now also rejects `warningMinimumUnits > preferredMinimumUnits` and `warningMaximumUnits < preferredMaximumUnits`. |
| `src/main/java/zcylas/totality/api/rpg/resources/ResourceAmount.java` | CREATED (correction pass) | Minimal declaration-only value type (`resourceId`, `units`, optional `partition`) reconstructed from the canonical document's one shown usage example (`ResourceAmount.scalar(...)`), needed to make `ResourceGrantInitialization.AtAbsolute` concrete. |
| `src/main/java/zcylas/totality/api/rpg/resources/integration/ResourceGrantInitialization.java` | CREATED (correction pass) | Canonical sealed interface (§16.6): `AtMinimum`, `AtMaximum`, `AtFraction(numerator, denominator)`, `AtAbsolute(ResourceAmount)`, `PreserveExisting`, `Custom(Identifier strategyId)` — each with its own malformed-declaration validation. Placed under `.integration` to match the canonical package layout. Replaces the Phase 1 simplification `ResourceInitializationPolicy` (removed). |
| `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceDefinition.java` | CREATED | Immutable definition record + `Builder`. **Correction pass:** added `Builder.initialization(ResourceGrantInitialization)` convenience method. |
| `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistry.java` | CREATED | The registry — see §6. **Correction pass:** added `definitionVersion` positivity check, target-range-vs-absolute-bounds cross-check, and `AtAbsolute` initialization cross-checks. |
| `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponent.java` | CREATED | The new player component — see §4/§7. **Correction pass:** schema bumped 1→2; orphan entries now carry a scalar regeneration remainder; `writeOrphanEntry`/`orphanToLiveState` now iterate the corrected current∪overflow partition-key union; `copyFrom` now deep-copies orphaned state instead of sharing instances. |
| `src/main/java/zcylas/totality/api/rpg/resources/ResourceStateComponents.java` | CREATED | Registers the component under `totality:resource_state`. |
| `src/main/java/zcylas/totality/api/rpg/resources/state/ResourceState.java` | CREATED | Sealed interface (`ScalarResourceState`, `PartitionedResourceState`). |
| `src/main/java/zcylas/totality/api/rpg/resources/state/ScalarResourceState.java` | CREATED | Mutable current/overflow/regeneration-remainder holder. |
| `src/main/java/zcylas/totality/api/rpg/resources/state/PartitionedResourceState.java` | CREATED | Mutable per-partition current/overflow maps. **Correction pass:** `partitions()` now returns the union of current and overflow key sets, not just current's keys — an overflow-only partition is no longer silently dropped. |
| `src/main/java/zcylas/totality/api/rpg/resources/state/OrphanedResourceState.java` | CREATED | Raw-state preservation for resources with no matching definition. **Correction pass:** added `scalarRegenerationRemainder` field, `partitions()` union (matching `PartitionedResourceState`'s fix), and a genuine `copy()` method for independent deep-copying. |
| `src/main/java/zcylas/totality/init/ModComponents.java` | MODIFIED | +2 lines: one import, one `ResourceStateComponents.register();` call, added immediately after the existing `ResourceComponents.register();` line. No other line changed. Untouched by the correction pass. |

**Removed during the correction pass:** `src/main/java/zcylas/totality/api/rpg/resources/ResourceInitializationPolicy.java` — the Phase 1 simplified 3-value enum, replaced by `ResourceGrantInitialization` above. This file no longer exists in the working tree and therefore does not appear in this bundle.

### Test files (13 created — no test infrastructure existed in this repository before this whole patch)

| Path | Tests | Status |
|---|---|---|
| `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java` | 26 (+12) | **Correction pass:** added `definitionVersion` positive/zero/negative tests, target-range-vs-absolute-bounds tests, full `AtAbsolute` cross-check matrix, non-`AtAbsolute` variant tests; renamed one test's identifier from `temperature_like` to `bounded_range_resource`. |
| `src/test/java/zcylas/totality/api/rpg/resources/state/ScalarResourceStateTest.java` | 3 | Unchanged. |
| `src/test/java/zcylas/totality/api/rpg/resources/state/PartitionedResourceStateTest.java` | 6 (+2) | **Correction pass:** added overflow-only-partition union test and overflow-only-partition-survives-copy test. |
| `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentTest.java` | 13 (+4) | **Correction pass:** added reflection-based scalar/partitioned orphan-restoration tests (exercising the real private `orphanToLiveState` method) and deep-copy-of-orphans tests. |
| `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentMalformedDataTest.java` | 2 | Unchanged. |
| `src/test/java/zcylas/totality/api/core/rpgutils/RpgDisplayUtilsCharacterizationTest.java` | 6 | Unchanged. |
| `src/test/java/zcylas/totality/api/core/rpgutils/HungerDisplayCharacterizationTest.java` | 2 | Unchanged. |
| `src/test/java/zcylas/totality/api/rpg/stats/PlayerStatsCharacterizationTest.java` | 6 | Unchanged. |
| `src/test/java/zcylas/totality/api/rpg/classes/PlayerChargesRageCharacterizationTest.java` | 9 | Unchanged. |
| `src/test/java/zcylas/totality/api/rpg/resources/integration/ResourceGrantInitializationTest.java` | 12 | CREATED (correction pass). |
| `src/test/java/zcylas/totality/api/rpg/resources/ResourceAmountTest.java` | 3 | CREATED (correction pass). |
| `src/test/java/zcylas/totality/api/rpg/resources/ResourceTargetRangeTest.java` | 7 | CREATED (correction pass). |
| `src/test/java/zcylas/totality/api/rpg/resources/state/OrphanedResourceStateTest.java` | 5 | CREATED (correction pass). |

**Total: 100 tests (up from 55), 0 failures.**

### Build/configuration changes (1 modified since master)

| Path | Change |
|---|---|
| `build.gradle` | Added `testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"`, `testRuntimeOnly "org.junit.platform:junit-platform-launcher"`, and a `test { useJUnitPlatform() }` block. No existing dependency, task, plugin, or run configuration was modified or removed. Untouched by the correction pass. |

### Documentation (1 created)

| Path | Purpose |
|---|---|
| `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md` | The canonical readiness audit, Oxygen-to-Breath addendum, original Stage 2 implementation record, and (new) Stage 2 Pre-Commit Correction Record. Authoritative source for everything summarized in this bundle report. |

### Review-bundle files (this task; not part of the Phase 1 implementation itself)

| Path | Purpose |
|---|---|
| `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_1_IMPLEMENTATION_REPORT.md` | This file. |
| `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_1_MANIFEST.txt` | File inventory with sizes and SHA-256 hashes, regenerated from the corrected file set. |
| `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_1.patch` | Unified diff of every file above, regenerated from the corrected working tree. |
| `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_1_REVIEW_BUNDLE.zip` | This report + manifest + patch + every file in the tables above, at their exact project-relative paths. |

**Explicitly excluded from this bundle** (pre-existing, unrelated uncommitted work found in `git status` but not part of this phase): modified generated data files under `src/main/generated/data/**` (loot tables, recipes, noise settings — present in `git status` before this feature branch's work began), untracked PNGs under `Context/Trading Test/`, and the untracked `src/main/generated/.cache/` directory.

---

## 3. Oxygen-to-Breath audit findings (summary)

Unchanged by this correction pass. Full detail lives in the canonical report's "Oxygen-to-Breath Audit Addendum" section. Summary for this bundle:

- **Authoritative storage:** 100% vanilla `LivingEntity` air supply. No Totality component, mixin, or attribute modifies current/max air anywhere in the codebase.
- **Synchronization and persistence:** entirely vanilla (`SynchedEntityData` sync, vanilla NBT). No Totality packet or NBT key touches air/oxygen.
- **HUD behavior:** Totality replaces vanilla's Health/Armor/Food bars with custom-drawn equivalents but never touches `VanillaHudElements.AIR_BAR` — vanilla's own air-bubble element still renders unmodified. Because Totality relocates the Food bar (which vanilla's air bubbles are normally anchored near) to the HUD's right side, the vanilla air-bubble element likely now renders at a **pre-existing, cosmetic** misalignment — this predates the Resource API work and is out of scope for both the audit and this implementation.
- **Absence of Totality-owned storage/identifiers:** confirmed zero matches for `totality:oxygen`/`totality:air`/`totality:breath` (or bare "oxygen"/"air"/"breath" identifiers) anywhere in the codebase — a completely clean slate, no migration collision risk.
- **Recommendation:** `totality:breath` should later be registered as an `EXTERNAL_ADAPTER`-authority resource wrapping vanilla air supply, structurally identical to the planned Health and Food adapters. Not registered in this patch.

---

## 4. Implemented architecture

- **Registry** (`PlayerResourceRegistry`): holds `PlayerResourceDefinition`s, described in §6.
- **Immutable definitions** (`PlayerResourceDefinition`): record with a `Builder`, capturing id, model, polarity, state authority, external-adapter id, unit scale, absolute minimum, authored base maximum, capabilities, target range, lifecycle policy, and definition version. Never stored in player NBT; contains no player reference.
- **Model declarations** (`ResourceModel`): `SCALAR` (single bounded value) or `PARTITIONED_POOL` (per-partition current/max, e.g. future spell slots or Hit Dice).
- **Polarity** (`ResourcePolarity`): `HIGH_IS_GOOD` / `HIGH_IS_BAD` / `TARGET_RANGE` / `NEUTRAL`.
- **State authority** (`ResourceStateAuthority`): `GENERIC_COMPONENT` (stored in the new component) or `EXTERNAL_ADAPTER` (owned elsewhere, e.g. future Health/Food/Breath).
- **Capabilities** (`ResourceCapability`): the full canonical 16-value set of feature declarations (SPENDABLE, RESTORABLE, DIRECT_DRAIN, PASSIVE_REGENERATION, ..., HUD_VISIBLE, MENU_VISIBLE) — declared, not yet behaviorally wired to anything.
- **Lifecycle contracts** (`ResourceLifecyclePolicy`, `ResourceDeathPolicy`, `ResourceGrantInitialization`): declare death handling and initial-value policy. `ResourceGrantInitialization` (**new in the correction pass**) is the canonical sealed interface — `AtMinimum`, `AtMaximum`, `AtFraction`, `AtAbsolute`, `PreserveExisting`, `Custom` — exactly matching canonical §16.6, replacing the Phase 1 simplified enum. `ResourceAmount` (also new) is the minimal value type `AtAbsolute` needs. Only `ResourceLifecyclePolicy.DEFAULT` (using `AtMaximum`) is used anywhere in production, since no production resource is registered yet.
- **Scalar state** (`ScalarResourceState`): mutable `currentUnits`/`overflowUnits`/`regenerationRemainder` holder, pure data, no clamping/spend logic (deferred to a later phase's transaction service).
- **Partitioned state** (`PartitionedResourceState`): mutable per-partition current/overflow `Map<Integer, Long>` pair, pure data. `partitions()` returns the union of both maps' keys (corrected in this pass).
- **Orphaned state** (`OrphanedResourceState`): preserves raw persisted data — current, overflow, **and now regeneration remainder** — for a resource id whose definition is missing or model-mismatched, so no player data is silently lost if an optional module is temporarily removed. Now supports independent deep-copying via `copy()`.
- **`PlayerResourceStateComponent`**: the player-attached component holding `Map<Identifier, ResourceState> states` and `Map<Identifier, OrphanedResourceState> orphanedStates`. Implements `SyncedComponent` + `CopyableComponent`, matching the existing shared component framework exactly (same pattern as `PlayerChargesComponent`/`PlayerResourceComponent`). Queries never auto-instantiate; state is only ever created via explicit `instantiateScalar`/`instantiatePartitioned` calls, which nothing in production code invokes yet. `copyFrom` now deep-copies orphaned entries.
- **`ResourceStateComponents`**: registers the component (`PlayerComponentEvents.registerForPlayers`/`registerClientComponent`) with `RespawnStrategy.ALWAYS_COPY`, under the new identifier.
- **Component registration**: wired into `ModComponents.register()` immediately after the existing `ResourceComponents.register()` call — the only change to any file outside the new packages and the test tree.

---

## 5. Component identifier

- **New component identifier: `totality:resource_state`.** Unchanged by the correction pass.
- **Legacy component identifier remains unchanged: `totality:resources`** (owned by `ResourceComponents`/`PlayerResourceComponent`, still the sole authority for Stamina and Mana).
- **No legacy data was imported in this phase.** The new component's `readData` only ever recognizes entries it wrote itself (`Resource_i_*`/`Orphaned_i_*` keys under its own NBT child, keyed by its own component id) — it does not read or migrate any `totality:resources` (`stamina`/`mana`) NBT data. `ComponentRegistry.getOrCreate` also mechanically prevents two components from sharing one identifier with different classes, so the two components cannot collide even accidentally.

---

## 6. Registry validation

`PlayerResourceRegistry.register(definition)` rejects, before mutating its internal map:

1. **Duplicate IDs** — `IllegalArgumentException`; the original definition is left untouched (no silent replacement).
2. **`unitScale < 1`** — `IllegalArgumentException`.
3. **`definitionVersion < 1`** — `IllegalArgumentException`. *(new in the correction pass)*
4. **`authoredBaseMaximum <= absoluteMinimum`** (when an authored maximum is declared) — `IllegalArgumentException`.
5. **`TARGET_RANGE` polarity without a `targetRange`** — `IllegalArgumentException`.
6. **A declared `targetRange` falling outside the definition's own `absoluteMinimum`/`authoredBaseMaximum` bounds** — `IllegalArgumentException`. *(new in the correction pass — "the preferred range lies inside the absolute bounds," canonical §8.2)*
7. **`EXTERNAL_ADAPTER` authority without an `externalAdapterId`** — `IllegalArgumentException`.
8. **`GENERIC_COMPONENT` authority with an `externalAdapterId` present** — `IllegalArgumentException`.
9. **`PARTITIONED_SPENDING` capability declared on a `SCALAR`-model definition** — `IllegalArgumentException`.
10. **An `AtAbsolute` initialization whose `ResourceAmount.resourceId()` does not match the definition's own id** — `IllegalArgumentException`. *(new in the correction pass)*
11. **An `AtAbsolute` initialization whose partition presence is inconsistent with the definition's `ResourceModel`** (absent for `SCALAR`, present for `PARTITIONED_POOL`) — `IllegalArgumentException`. *(new in the correction pass)*
12. **Any registration attempted after `freeze()`** — `IllegalStateException`.

Additionally, `ResourceTargetRange`'s own compact constructor (checked at construction time, before the definition even reaches the registry) rejects `preferredMinimumUnits > preferredMaximumUnits` and, new in this pass, `warningMinimumUnits > preferredMinimumUnits` / `warningMaximumUnits < preferredMaximumUnits`. `ResourceGrantInitialization.AtFraction`'s compact constructor rejects a non-positive denominator or negative numerator; `AtAbsolute` and `Custom` reject null arguments.

`get(id)`/`isRegistered(id)` never throw for unknown IDs (return `Optional.empty()`/`false`). `freeze()` finalizes the registry; `isFrozen()` reports state. `PlayerResourceRegistry.INSTANCE` is the production singleton and has zero definitions after this patch; the class is also freely instantiable (`new PlayerResourceRegistry()`) so tests can exercise registration/duplicate/freeze behavior in isolation without sharing mutable static state across the test JVM (a deliberate, documented deviation from the pure-static-singleton style of the existing `ComponentRegistry`).

No strategy framework, mutation service, grant provider, or presentation renderer was added merely to create more validation — every rule above is a pure structural check on already-implemented fields.

---

## 7. State and NBT schema

**Schema version: 2** (bumped from 1 during the correction pass — `SchemaVersion` field, written unconditionally on every save). Safe to bump without a migration path: no real resource had written Phase 1 state under schema 1 (the whole component remains inert; no production definitions are registered).

```
SchemaVersion: int
ResourceCount: int
Resource_{i}_id: string
Resource_{i}_model: string ("SCALAR" | "PARTITIONED_POOL")
  # SCALAR:
  Resource_{i}_current: long
  Resource_{i}_overflow: long
  Resource_{i}_remainder: long
  # PARTITIONED_POOL:
  Resource_{i}_partitionCount: int
  Resource_{i}_partition_{j}_key: int
  Resource_{i}_partition_{j}_current: long
  Resource_{i}_partition_{j}_overflow: long
OrphanedCount: int
Orphaned_{i}_id: string
Orphaned_{i}_model: string
Orphaned_{i}_partitionCount: int           # orphans always use this uniform partition-map shape,
Orphaned_{i}_partition_{j}_key: int        # regardless of declared model — a scalar orphan's
Orphaned_{i}_partition_{j}_current: long   # current/overflow are stored at partition key 0.
Orphaned_{i}_partition_{j}_overflow: long  # partitionCount now reflects the current∪overflow union.
Orphaned_{i}_remainder: long                # NEW in schema 2 — only present for SCALAR-model orphans.
```

**Empty-state behavior:** for a fresh player (every player, in this patch), `writeData` produces exactly `SchemaVersion=2, ResourceCount=0, OrphanedCount=0` — three small ints, no per-resource entries at all. This matches how `PlayerResourceComponent` already writes its own fields unconditionally today.

**Malformed data behavior:** each `Resource_i`/`Orphaned_i` entry is parsed inside its own `try/catch` (mirroring `PlayerChargesComponent.readData`'s existing per-entry tolerance pattern). A corrupt entry is logged via `Totality.LOGGER.warn` and skipped — it does not abort the rest of the player's load.

**Unknown definitions:** if a persisted resource id has no registered `PlayerResourceDefinition`, or its persisted model doesn't match the registered definition's model, the entry is moved into `orphanedStates` instead of being dropped.

**Orphaned state (corrected in this pass):** preserved exactly — current, overflow, **and now regeneration remainder** for `SCALAR`-model orphans — so the data survives save/load cycles even while its definition is absent (e.g. an optional module temporarily removed), with zero precision loss. Partition preservation now uses the current∪overflow key union, so an overflow-only partition is no longer dropped during either the live→orphan transition or the orphan→live restoration. If a matching definition reappears with a compatible model on a later load, the orphan is restored into live `states` automatically with everything intact.

**Why dormant/merely-registered definitions create no player state:** `PlayerResourceRegistry` and `PlayerResourceStateComponent` are deliberately independent data structures. Registering a definition only makes an id resolvable by `PlayerResourceRegistry.get(id)`; it has no reference to, and never touches, any player's `PlayerResourceStateComponent`. State is only created by the component's own `instantiateScalar`/`instantiatePartitioned` methods, which nothing in this patch calls — confirmed by `PlayerResourceStateComponentTest.definitionRegistrationAloneDoesNotInstantiatePlayerState`.

---

## 8. Tests

**Result: 100/100 passed** (up from 55/55 before the correction pass).

| Test class | Tests | What it verifies |
|---|---|---|
| `PlayerResourceRegistryTest` | 26 | Successful registration, duplicate-id rejection, all structural validation rules including the four new in this pass (`definitionVersion` positivity, target-range-vs-absolute-bounds, `AtAbsolute` id/model consistency), freeze blocking further registration, safe lookup for unknown ids, `INSTANCE` has zero definitions. |
| `ResourceGrantInitializationTest` | 12 | Every canonical variant (`AtMinimum`/`AtMaximum`/`AtFraction`/`AtAbsolute`/`PreserveExisting`/`Custom`) constructs correctly; malformed declarations (invalid fraction denominator/numerator, null `AtAbsolute` amount, missing `Custom` strategy id) are rejected. |
| `ResourceAmountTest` | 3 | `scalar()`/`partitioned()` factory correctness, null-id rejection. |
| `ResourceTargetRangeTest` | 7 | Preferred/warning bound validation in isolation from the registry — valid ranges, inverted preferred bounds, warning bounds inside the preferred band (both directions), warning bounds exactly at the preferred edges. |
| `OrphanedResourceStateTest` | 5 | Default-zero for unset partitions, remainder field round-trip, current∪overflow partition union, deep-copy independence (both directions), overflow-only partition survives `copy()`. |
| `ScalarResourceStateTest` | 3 | Constructor defaults, setter mutation, `copy()` producing an independent instance. |
| `PartitionedResourceStateTest` | 6 | Default-zero, independent per-partition storage, `copy()` independence, Hit-Dice-shaped multi-partition scenario, current∪overflow union, overflow-only partition survives `copy()`. |
| `PlayerResourceStateComponentTest` | 13 | Fresh component is empty; queries never auto-instantiate; definition registration alone never instantiates player state; instantiation is idempotent; model-mismatch throws; `removeState` works; `copyFrom` independently copies live state and fully replaces (not merges) target contents; scalar orphan restoration preserves current/overflow/remainder (via reflection into the real private `orphanToLiveState`); partitioned orphan restoration preserves an overflow-only partition; `copyFrom` deep-copies orphaned state (mutation independence both directions) and replaces (not merges) orphan contents. |
| `PlayerResourceStateComponentMalformedDataTest` | 2 | The exact failure conditions (`Identifier.parse` on garbage, `ResourceModel.valueOf` on an unknown name) that the persistence try/catch is designed to swallow really do throw. |
| `RpgDisplayUtilsCharacterizationTest` | 6 | **HP ×5 display**: `HP_DISPLAY_MULTIPLIER == 5`, `toDisplayHp`/`toVanillaHp` exact round-trip, `formatHp` output. `conModifierToVanillaHp` (`*2.0`, the live HP-attribute path). Pins the two dead/unused Stamina/Mana bonus formulas' current (contradictory, `*5`) values. |
| `HungerDisplayCharacterizationTest` | 2 | Confirms no Food/Hunger display-conversion method exists on `RpgDisplayUtils` yet, and that HP's multiplier remains the only display-multiplier constant on the class. |
| `PlayerStatsCharacterizationTest` | 6 | **Stamina/Mana** live formulas: `END modifier * 10` / `INT modifier * 10`, exercised via real `PlayerStats` attribute-score manipulation (not mocks). The ability-modifier formula itself (`floorDiv(score-10, 2)`). The unused `getMaxHpBonus()` (`CON * 5`) pinned as distinct from the live HP path. Attribute-point spending affecting derived bonuses end-to-end. |
| `PlayerChargesRageCharacterizationTest` | 9 | **Rage**, exercised via the real `PlayerChargesComponent` (not a mock): `CHARGE_ID` unchanged; a fresh component has no pools (non-Barbarian baseline); consume decrements and fails at zero; Short Rest restores exactly 1 and clamps at max; Long Rest restores to max; rest priority is 5; `ensurePool` preserves current charges across a max increase (class level-up); the exact 25-entry `RAGE_CHARGES` table (read via reflection, without widening production-code visibility). |

Characterization coverage explicitly present for: **HP** (formula + ×5 display), **Stamina** (live `*10` formula), **Mana** (live `*10` formula), **Rage** (full lifecycle via the real component), **registry behavior** (including all new structural validation), **scalar state**, **partitioned state**, **orphaned state** (including remainder preservation, deep-copy independence, overflow-only partition survival), and **component behavior** (instantiation/query/copy, including orphan restoration and deep-copy).

---

## 9. Validation performed

| Command | Outcome |
|---|---|
| `./gradlew compileJava` | `BUILD SUCCESSFUL` |
| `./gradlew compileTestJava` | `BUILD SUCCESSFUL` |
| `./gradlew test` | `BUILD SUCCESSFUL` — 100/100 tests passed, 0 failures |
| `./gradlew runDatagen` | `BUILD SUCCESSFUL` — `Caching: total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0` — **confirms datagen changed no files** |
| `./gradlew build -x runDatagen` | `BUILD SUCCESSFUL` — full compile + test + jar + sourcesJar + assemble + check |

All commands were re-run after the correction pass, not only during the original implementation session.

---

## 10. Known limitations and risks

- **No automated Mojang `ValueInput`/`ValueOutput`/`RegistryFriendlyByteBuf` runtime round-trip test.** This project has no existing harness to construct these outside a running game, and none was built for this patch — attempting to fabricate one risked introducing unverified guesswork into a "characterization, don't invent" task. Coverage instead comes from: (a) successful compilation of the actual `writeData`/`readData`/`writeSyncPacket`/`applySyncPacket` code, (b) direct unit tests of the pure logic these methods wrap, **including — new in the correction pass — reflection-based tests of the real, private, pure `orphanToLiveState` transformation method** (genuine coverage of production logic, not a reimplementation), and (c) a manual smoke-test checklist recorded in the canonical readiness-audit report.
- **Recommended follow-up:** Fabric's GameTest framework is the standard way to add real in-game-registry-backed automated coverage for the persistence/sync gap above, once real resources are being migrated and a silent NBT-format bug would actually be consequential.
- ~~**Manual smoke test not executed** (correction pass): this session has no GUI/display interaction tool available, so the join/save/reload/HUD checklist could not actually be run.~~ **SUPERSEDED — the manual smoke test has since been executed and passed. See §14.**
- **No other genuine issue was found while packaging this corrected bundle.** `git status`, `git diff`, and a direct file-count cross-check (in the manifest) confirm the file set matches exactly what was implemented; no additional untracked files, partial writes, or stray artifacts were discovered during bundle preparation.

The previously-listed limitation describing `ResourceInitializationPolicy` as a deliberate simplification of the canonical grant-initialization contract **no longer applies** — that simplification was replaced with the canonical `ResourceGrantInitialization` contract itself during this correction pass (§4, §13).

---

## 11. Scope exclusions

This phase did **not**, and the correction pass did not change this:

- Migrate Health.
- Migrate Hunger.
- Migrate Breath (not even registered — only audited).
- Migrate Stamina or Mana.
- Migrate Rage.
- Migrate spell slots.
- Change Rest behavior (no `RestListener` was added, removed, or modified for any resource).
- Change regeneration (no tick loop, `StaminaServerTick`, or `ManaServerTick` was touched).
- Disable food-based natural HP regeneration (vanilla regen is entirely untouched; this is explicitly future Health/Food-integration-phase scope per the canonical report).
- Alter the HUD (`TotalityHudRenderer` and every other rendering file are untouched; datagen confirms zero generated-data changes and no HUD file appears in the diff).
- Register production dormant resource definitions (Ki, Solar Charge, Thirst, Sanity, Fatigue, or any other) — `PlayerResourceRegistry.INSTANCE` has zero entries.
- Replace synchronization (the existing bespoke `SyncStaminaPayload`/`SyncManaPayload` and the generic `ComponentSync` mirror both remain exactly as they were; the new component's own sync method is never called).
- Remove legacy components or packets (`PlayerResourceComponent`, `ResourceComponents`, `SyncStaminaPayload`, `SyncManaPayload`, `PlayerChargesComponent`, `SpellSlotComponent` are all untouched).
- Add a grant-provider framework, strategy resolvers, or presentation rendering — the correction pass's registry validation additions and the canonical initialization contract are all still purely structural/declaration-only.

---

## 12. Recommended next patch

Unchanged by the correction pass. Per the canonical report's §11 stage sequence, the next reviewable slice should be a **Health + Hunger external-adapter patch**, scoped narrowly:

- Register `totality:health` and `totality:food` as `EXTERNAL_ADAPTER`-authority definitions in `PlayerResourceRegistry.INSTANCE`, wrapping vanilla `getHealth()/setHealth()/getMaxHealth()` and `Player.getFoodData()` respectively — read/query only to start, matching the "adapters over existing resources" phase's goal of proving the model without dual-writing.
- Introduce the HP and Hunger **×5 presentation conversion** for Hunger specifically (HP's `RpgDisplayUtils.toDisplayHp` already exists and is already correct — promote it into the registered formatter rather than reimplementing it; Hunger currently has no display conversion at all).
- **Preserve vanilla authority** for both — no duplicate storage, no changed damage/healing/eating mechanics, exactly as the canonical design's external-adapter contract requires.
- **No Stamina, Mana, or Rage migration yet** — those remain scoped to their own later, separate, reviewable patches per the canonical migration matrix.
- **Breath**: this phase's Oxygen-to-Breath audit confirms `totality:breath` is structurally ready for the same external-adapter pattern with zero migration risk. Whether to fold Breath into the same patch as Health/Food, or keep it a separate small follow-up, should be decided at review time.
- **Temperature is explicitly excluded** from this and any current Resource API patch — it is owned by a future Environment/Physiology system per the current decision recorded in §13.

---

## 13. Pre-commit correction pass summary (2026-07-18)

Applied after the original bundle was accepted structurally but before the first commit. Full detail for each item lives in the canonical readiness report's "Stage 2 Pre-Commit Correction Record" section; this is the condensed version for this standalone report.

1. **Canonical initialization contract:** `ResourceInitializationPolicy` (simplified enum) removed; replaced by `ResourceGrantInitialization` (canonical sealed interface, §16.6) plus the new minimal `ResourceAmount` value type. `ResourceLifecyclePolicy` updated to the canonical field type and order.
2. **Scalar regeneration remainder** now survives the full live → orphan → live-again sequence with zero precision loss (schema bumped to version 2).
3. **Orphan state copying is now a genuine deep copy** — `PlayerResourceStateComponent.copyFrom` no longer shares `OrphanedResourceState` instances between components.
4. **Partition-key union preserved everywhere** — an overflow-only partition (no current-value entry) is no longer silently dropped during serialization-related transformations, in both `PartitionedResourceState` and `OrphanedResourceState`.
5. **Temperature removed as a Resource API example** throughout production Javadocs, test names/comments, and both reports. `TARGET_RANGE` polarity and `ResourceTargetRange` remain fully supported generically; Temperature itself is explicitly out of scope, owned by a future Environment/Physiology system.
6. **Additional structural registry validation**: `definitionVersion` positivity, target-range-vs-absolute-bounds cross-check, warning-range internal consistency, and `AtAbsolute` id/model consistency checks.
7. **45 new tests added** (55 → 100 total), all passing; all 55 original tests preserved unchanged.
8. ~~**Manual smoke test**: not executed — no GUI/display interaction tool available in this session.~~ **SUPERSEDED — subsequently executed and passed. See §14.**

No existing resource was migrated by this correction pass. No gameplay or HUD behavior changed. Nothing was committed, staged, or pushed.

---

## 14. Final manual verification (2026-07-18, finalization pass)

The manual client smoke test recorded as not-yet-executed in §10 and §13 above has since been run on the actual Minecraft 26.2 client and **passed**. This section is the current, authoritative manual-verification status for Phase 1 — it supersedes every earlier "not executed" statement in this report.

**Steps and results:**

1. Client started successfully.
2. Integrated server started successfully.
3. Player joined the test world successfully.
4. World saved and closed normally.
5. The same world reopened successfully.
6. Player rejoined successfully.
7. Second save and shutdown completed normally.
8. No Generic Player Resource API component-attachment, synchronization, NBT-read, or NBT-write errors appeared.
9. Existing HP, Hunger, Stamina, Mana, Rage, and HUD behavior appeared unchanged.
10. The run ended with `BUILD SUCCESSFUL`.

Log messages reviewed and confirmed unrelated to this work (not fixed, not claimed to be fixed — noted only as ruled out): development-session Realms/profile-key authentication errors, existing missing-model/missing-texture warnings, deliberately generated Provisioner verification error cases, untranslated item-tag warnings, Gradle deprecation warnings.

**Conclusion:** Phase 1 (the inert `PlayerResourceStateComponent` foundation, schema version 2, zero registered resources) is now verified both automatically (100/100 tests, clean build, zero datagen changes) and manually (full join → save → close → reopen → rejoin → close cycle on a real client, no errors, no observable regression). The remaining open item is the previously-noted recommendation to add automated Fabric GameTest coverage for the NBT/sync round-trip in a later phase — this manual pass does not replace that recommendation, it closes the separate "was this ever actually run in-game" gap.
