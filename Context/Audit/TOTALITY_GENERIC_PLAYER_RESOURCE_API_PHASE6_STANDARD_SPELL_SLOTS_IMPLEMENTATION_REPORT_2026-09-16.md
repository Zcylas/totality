# Totality — Generic Player Resource API Phase 6: Standard Spell Slots — Implementation Report (2026-09-16)

## 1. Baseline

- Branch: `feature/general-resource-api`
- Baseline commit: `7bd5d76607d91f00828d09b89db737f6585c4bef` (`fix(classes): reconcile class-owned state after class changes`)
- HEAD unchanged throughout this task — **no commit was made.**
- Pre-existing dirty paths at task start: **109**, all unrelated (confirmed via `git status --short` diffed against the captured baseline). None were touched. None of this task's 27 files were among the 109 — no mixed-hunk isolation was required this time (unlike Phase "class-change reconciliation," which had one).

## 2. Characterization (before any change)

Answered by direct code read, not from old summaries — see `StandardSpellSlotsResourceAdapter`'s and `SpellSlotComponent`'s own class Javadoc for the exact pre-Phase-6 shape this section characterizes.

1. **Which class combinations produce ordinary slots?** Any combination of `CasterProgression.FULL`/`HALF`/`THIRD` classes, pooled via `SpellSlotTable.combinedCasterLevel`. Only Wizard (`FULL`) exists as an implemented caster today; Barbarian/Monk have no entry (non-casters); Warlock is registered as `WARLOCK` and explicitly excluded from pooling.
2. **Exact formula:** `combinedCasterLevel = min(30, fullLevels + halfLevels/2 + thirdLevels/3)`, then `SpellSlotTable.forFullCaster(combinedLevel)`.
3. **Which tiers exist today (pre-migration):** 1st–10th (`SpellSlotComponent.MAX_SPELL_LEVEL = 10`).
4. **Where tier 10 appears:** `SpellSlotTable.FULL_CASTER` rows for class levels 25 (`1`) and 30 (`2`) only — every other row's 10th column is `0`. `HALF_CASTER`'s 10th column is always `0`. `StandardSpellSlotsResourceAdapter`/`ClientSpellSlotParityComparator` iterate/compare all 10 partitions unconditionally.
5. **Persistence:** `SpellSlotComponent.writeData/readData`, keys `"max_0".."max_9"`/`"used_0".."used_9"`.
6. **Maximum calculation:** `SpellSlotRecalculator.recalculate(ServerPlayer)`, called from `ClassChangeReconciler.reconcile` and directly from `PlayerConnectionEvents`' JOIN handler; wrote the result into the legacy component via `SpellSlotComponents.get(player).recalculate(maxSlots)`.
7. **Synchronization:** No bespoke packet — rides the shared generic `ComponentSync` channel (`SpellSlotComponents.SPELL_SLOTS.sync(...)`), feeding client-side `ClientSpellSlotManager`. A parallel, query-only Generic Resource view (`totality:spell_slots`, `EXTERNAL_ADAPTER`) already existed since Phase 2D, read by `SpellRadialScreen` via `ClientResourcePresentationResolver` with legacy fallback.
8. **UI read path:** `SpellRadialScreen.drawSlotIndicator` already called `ClientResourcePresentationResolver.INSTANCE.resolvePartition(SPELL_SLOTS, level, legacyCurrent, legacyMax)` — Generic preferred, legacy fallback only when Generic is unavailable. **No UI code change was needed** — once authority flips, the "Generic" branch simply becomes live/authoritative instead of an adapter mirror.
9. **Casting availability check:** `ActivateAbilityHandler.handle`, `SpellSlotComponent.hasSlot(spellLevel)`, called before `ability.onActivate`.
10. **Exactly when a slot is spent:** `ActivateAbilityHandler.handle`, *after* `ability.onActivate` returns, gated on `castSucceeded = Spell.didCastSucceed()` (itself gated on `Spell.resetCastResult()`/`markNoEffect()`). A failed/no-effect cast never reaches the spend call. **Confirmed unchanged by this migration — see §7.**
11. **Long Rest:** `SpellSlotComponent.onRest(LONG)` → `restoreAll()` (every level to its max).
12. **Short Rest:** No-op for standard slots (`onRest` only handles `LONG`).
13. **Death:** `SpellSlotComponents.register()` uses `RespawnStrategy.ALWAYS_COPY` — blanket preserve, matching `ResourceDeathPolicy.KEEP_CURRENT` (the `ResourceLifecyclePolicy.DEFAULT` value — no override needed).
14. **Reconnect:** `SpellSlotRecalculator.recalculate` ran on JOIN (inside a `primaryClass != null` guard) and the legacy component's own persisted data loaded normally.
15. **Dimension change:** No spell-slot-specific hook existed at all pre-migration (component state simply persists with the player).
16. **Old-save behavior:** `SpellSlotComponent` has no initialization sentinel — an all-zero component is indistinguishable from "never touched." This is the reason the migration import (§8 below) cannot gate on the legacy store's own state and must gate on current class entitlement instead.
17. **Warlock interaction:** `SpellSlotRecalculator`'s `switch` has an explicit `case WARLOCK -> { /* not tracked here */ }` no-op — **confirmed correct pre-migration, no bug found.** Warlock does not, and never did, contribute to the standard pool.
18. **Dead APIs:** `restoreSome(int, int)` existed on `SpellSlotComponent` but was never wired into `onRest` for any `RestType` — confirmed dead/unreachable by direct trace and by an existing test's own comment (`SpellSlotComponentTest.shortRestDoesNotRestoreStandardSlots`). **Removed** (task explicitly permits removing confirmed-dead code it names).

## 3. Old vs. new authority model

| | Before | After |
|---|---|---|
| `totality:spell_slots` `stateAuthority` | `EXTERNAL_ADAPTER` (query-only, `definitionVersion=1`) | `GENERIC_COMPONENT` (`definitionVersion=2`) |
| Authoritative current value | `SpellSlotComponent.usedSlots[]` | `PlayerResourceStateComponent`'s `PartitionedResourceState` |
| Authoritative maximum | `SpellSlotComponent.maxSlots[]`, written by `SpellSlotRecalculator` | Resolved live, every query, by `StandardSpellSlotMaximumResolver` |
| Mutation entry point | `SpellSlotComponent.useSlot/recalculate/restoreAll` | `PlayerResourceService.trySpend/restore` |
| Cast pre-check | `SpellSlotComponent.hasSlot` | `PlayerResourceService.query` → partition current |
| Grant/removal | None (component always attached, always "has" the resource) | `StandardSpellSlotResources` (`ResourceGrantProvider`, `CLASS`, `REMOVE_STATE`) |

**Exact resource ID confirmed from existing code, not invented:** `PlayerResourceIds.SPELL_SLOTS = totality:spell_slots` (already canonical since Phase 2D; the adapter constant `SPELL_SLOTS_ADAPTER` is the same value, by design).

## 4. Resource shape / grant ownership

- **Model:** `PARTITIONED_POOL`, unchanged. **Partitions 1–9 only** — partition 10 no longer exists in the definition, the resolver, the grant, the legacy array, the client cache, or the parity comparator.
- **Grant source:** `StandardSpellSlotResources` (new), source id `totality:standard_caster_progression`, `ResourceGrantSourceType.CLASS`. Condition: `SpellSlotRecalculator.computeCombinedCasterLevel(player) > 0` — the exact same formula the maximum resolver uses, generalized from "one specific class" (Rage's Barbarian-only precedent) to "any class combination currently contributing to the shared pool." **Not** class-by-class independent resources — one shared multiclass pool, matching the task's explicit requirement.
- **Initialization:** `AtMaximum` — a newly-qualifying caster starts with every tier full.
- **Removal:** `REMOVE_STATE` — a player who loses all caster-contributing classes has the pool deleted, not preserved, mirroring Rage's own default choice.
- **Warlock:** Excluded structurally — `computeCombinedCasterLevel` never counts `WARLOCK`-progression levels, so a Warlock-only character's combined level is 0 and the provider grants nothing. Verified by a new dev-server check (a real Warlock ServerPlayer gains no `totality:spell_slots` state) and by the existing `SpellSlotRecalculatorCharacterizationTest` coverage of the exclusion at the pure-logic level.

### Grant engine extension (foundational, needed by this migration)

`ResourceGrantReconciler.instantiateFromGrant` was **scalar-only** before this task — the first production `PARTITIONED_POOL` grant needed real support added. `instantiatePartitionedFromGrant` (new) implements `AtMaximum` for `PARTITIONED_POOL` (resolves the partitioned maximum, seeds every partition's `current` at its own resolved max); every other `ResourceGrantInitialization` variant is logged and deferred rather than guessed at, matching this class's own established pattern for unsupported combinations. This is generic infrastructure, not Spell-Slot-specific — proven with a **synthetic** (non-spell-slot) resource id in `ResourceGrantReconcilerTest`.

## 5. Maximum resolution — one authoritative calculation

`StandardSpellSlotMaximumResolver` delegates to `SpellSlotRecalculator.computeMaxSlots(player)`, which delegates to `computeCombinedCasterLevel(player)` (the exact per-class bucketing loop that previously lived inline in the retired `recalculate(ServerPlayer)`) and `SpellSlotTable.forFullCaster(combinedLevel)`. **No second max table exists anywhere** — not in the resolver, not in any UI code (`SpellRadialScreen` never computed a max itself; it always read from the component/resolver), not in casting code.

`SpellSlotTable.FULL_CASTER`/`HALF_CASTER` are now genuinely 9 columns wide (not 10-with-a-zero-column). The obsolete level-25/30 tier-10 entitlement is **removed entirely** — those rows simply have no 10th value to read; no replacement reward was invented, matching the task's explicit instruction. `STANDARD_SLOT_LEVELS = 9` is the new named constant other code (the resolver, the grant provider) reads instead of a bare literal.

## 6. Tier-10 removal — explicit audit

| Location | Classification | Action |
|---|---|---|
| `SpellSlotTable.FULL_CASTER`/`HALF_CASTER` 10th column | Obsolete standard-slot logic | Removed (tables now 9 columns) |
| `SpellSlotComponent.MAX_SPELL_LEVEL` (was 10) | Obsolete standard-slot logic | Changed to 9 |
| `StandardSpellSlotsResourceAdapter` (iterates `1..MAX_SPELL_LEVEL`) | Obsolete standard-slot logic | Automatically 9 now (bound comes from the constant) — adapter itself untouched |
| `ProductionResourceDefinitions`'s spell-slot definition | Obsolete standard-slot logic | Model unchanged (`PARTITIONED_POOL`); authority flip is the real change, partition count follows from the resolver/legacy constant |
| `ClientSpellSlotParityComparator.MAX_LEVEL` (was 10) | Obsolete standard-slot logic | Changed to 9 |
| `ClientSpellSlotManager` arrays (sized by `SpellSlotComponent.MAX_SPELL_LEVEL`) | Obsolete standard-slot logic | Automatically 9 now — no code change needed |
| Persisted NBT keys `max_9`/`used_9` on an old save | Obsolete standard-slot state | Never read again (loop bound is 9); orphaned key, harmless, never resurrected |
| `restoreSome(int count, int maxLevel)` | Dead API (already unreachable pre-migration) | Removed |
| `Spell` class Javadoc ("spellLevel 0 = cantrip, 1–9 = leveled spell") | Pre-existing correct assumption | No production spell content is above level 9 today — grepped, confirmed, nothing to migrate away from |
| `GrimoireScreen.currentSpellSlot` | Unrelated naming coincidence (rune-crafting UI "slot," not a D&D spell slot) | Left untouched |
| `CrownOfStarsSpell`'s "10 extra minutes" (ritual casting) / spell level 7 | Unrelated numeric literal / legitimate 7th-level spell content | Left untouched |
| `WARLOCK_PACT` table, `SpellSlotTable.forWarlock` | Pact Magic (Phase 7), structurally separate | Left untouched |
| "10th-level magic" as a lore/content concept anywhere else | Out of scope (this removes ordinary *slot* tier 10, not the concept of powerful magic) | Nothing found needing this distinction in code |

No global "replace every literal 10" was performed — every occurrence above was traced to its actual meaning first.

## 7. Successful-cast-only spending — preserved exactly

`ActivateAbilityHandler.handle`'s control flow is **structurally unchanged**: pre-check (now a Generic partitioned query instead of `SpellSlotComponent.hasSlot`) happens *before* `ability.canActivate`/`ability.onActivate`; the spend (now `PlayerResourceService.trySpend` with `ResourceCost.Partitioned(..., EXACT_TIER)` instead of `SpellSlotComponent.useSlot`) happens *after*, gated on the exact same `castSucceeded = Spell.didCastSucceed()` check as before. No line was moved earlier. `EXACT_TIER` is the only `PartitionSelectionPolicy` implemented for player spending (canonical §14.2's own stated intent) — there is no upcast picker, no fallback to another tier; the selected spell's own level is paid exactly, or the spend fails structurally.

Confirmed by direct code read (before and after diff of `ActivateAbilityHandler.java`) and exercised end-to-end (via `PlayerResourceService.trySpend`, the exact call the handler makes) in `StandardSpellSlotMigrationVerification` against a real `ServerPlayer`.

## 8. Old-save migration

`BaselineResourceLifecycleEvents.migrateSpellSlots` (new), reusing the exact `isLegacyMigrated`/`markLegacyMigrated` durable-marker mechanism Mana/Stamina/Rage already established — no new migration framework was invented.

- **Gate:** Since `SpellSlotComponent` has no "never touched" sentinel (§2 item 16), the import is gated on the player's *current* `computeCombinedCasterLevel(player) > 0` rather than any legacy-store flag — a player entitled now was, by construction, entitled under the same class levels before this migration shipped (class data is untouched by this migration), so their legacy remaining values are meaningful; a player with no current entitlement has nothing meaningful to import (a genuinely untouched all-zero legacy array must never pre-empt a future genuine first grant with a frozen 0/0).
- **Values:** For levels 1–9, `remaining = max(0, legacyMax - legacyUsed)`, clamped against the freshly resolved maximum for that partition (task requirement: "clamp against newly resolved maxima if necessary").
- **Tier 10:** Never read from the legacy array at all during migration — discarded by construction, not by an explicit filter.
- **Idempotent, no dual-write, no resurrection:** Gated by the marker exactly like the other three resources; verified directly (`StandardSpellSlotMigrationVerification`: spend after migration, re-run migration, confirm the spent value survives, confirm the marker is set).
- **Ordering:** Runs inside `migrateLegacyIfAbsent`, called before `PlayerBaselineResources.reconcile(player)` in the JOIN handler — consistent with the existing Mana/Stamina/Rage ordering, and safe since `ClassComponents`/the resolver are both available at that point (already relied on by Rage's own grant provider).

## 9. Rest behavior

- **Long Rest:** `StandardSpellSlotResources.onLongRest` restores every partition to its currently resolved maximum, via `PlayerResourceService.restore` with the exact per-partition deficit (mirroring `BarbarianRageAbility.onLongRest`'s overflow-safe pattern). Registered in `PlayerConnectionEvents` for `RestType.LONG` only.
- **Short Rest:** No hook registered at all for Standard Spell Slots — matches legacy behavior exactly (§2 item 12) and the task's explicit instruction not to give Wizard slots Short Rest restoration.
- **`restoreSome`:** Removed (§2 item 18, §6).

## 10. Death behavior

No death-policy override was added — `ResourceLifecyclePolicy.DEFAULT`'s `KEEP_CURRENT` matches the legacy `RespawnStrategy.ALWAYS_COPY` blanket-preserve exactly (§2 item 13). `PlayerResourceStateComponent.copyFrom` is already model-agnostic (works identically for `PARTITIONED_POOL` and `SCALAR`), so no new death-handling code was needed. No duplicate grant, no double-migration, and no tier-10 resurrection is possible on respawn: the migration marker is copied unconditionally across respawn (existing, unrelated-to-this-migration behavior), and grant reconciliation re-runs via `ServerPlayerEvents.COPY_FROM` exactly like every other resource.

## 11. Class-change reconciliation

`ClassChangeReconciler` needed **no new parallel hook** — `StandardSpellSlotResources`'s provider is registered on the same shared `ResourceGrantRegistry.INSTANCE` that step 1 (`ResourceGrantReconciliation.reconcileAndSync`) already iterates in full, so every existing class-mutation call site (`SelectClassHandler`, `AddClassLevelHandler`, `SelectSubclassHandler`, `/totality showclass`) transitively reconciles the Standard Spell Slot grant automatically.

`SpellSlotRecalculator.recalculate(ServerPlayer)` — the old explicit "recalculate and write" step `ClassChangeReconciler` and `PlayerConnectionEvents`'s JOIN handler both called — was **removed** as redundant: once granted, `totality:spell_slots`' maximum is resolved live on every query (§5), so there is nothing left to eagerly recompute or write down. This directly satisfies the task's "avoid duplicate recalculation... one authoritative sequence only" requirement. `ClassChangeReconciler`'s own defensive maximum-decrease clamp (`clampResourcesAboveResolvedMaximum`, generalized from scalar-only to also cover `PARTITIONED_POOL` — a small, generic extension, not spell-slot-specific) now also protects Standard Spell Slots, defense-in-depth, even though no current table can decrease while continuously granted (same caveat as Rage's own equivalent note).

## 12. Generic sync / legacy authority removal

- `totality:spell_slots` was already generic-sync-eligible pre-migration (`EXTERNAL_ADAPTER` with `LEGACY_BESPOKE_SYNCHRONIZATION` still qualifies); after the authority flip it is unconditionally eligible (`GENERIC_COMPONENT` always qualifies) — no `ResourceSyncManager` change was needed.
- **Legacy packet:** There never was a bespoke one (§2 item 7) — nothing to retire there.
- **Legacy mutation methods** (`useSlot`, `recalculate`, `restoreAll`): no longer called by any production code (confirmed by removing every call site and recompiling clean). Left on `SpellSlotComponent` itself, unremoved — matching the established `PlayerResourceComponent`/`PlayerChargesComponent` precedent of leaving a retired legacy store's methods in place (only its *callers* are removed), so the class's own characterization tests keep exercising real behavior rather than being deleted.
- **`onRest`:** No longer registered as a `RestListener` in `PlayerConnectionEvents` (both JOIN and AFTER_RESPAWN) — the exact same "remove the old registration, add the new Generic one" pattern the Phase 5 Rage migration already established (and documents inline, in the same file, right next to this change).
- **Legacy component persistence/attachment:** Deliberately **not removed** — `SpellSlotComponents.register()` is unchanged, so the component stays attached and its NBT stays readable, solely so the one-time migration import (§8) can still find pre-Phase-6 data on a player's first post-update join. This mirrors `PlayerResourceComponent`/`PlayerChargesComponent` exactly.
- **No dual writing:** Nothing writes to both the legacy component and Generic state for the same operation — the legacy component's stored values, from this point forward, are simply frozen wherever they were left (or all-zero for a new player), never touched again.
- **Client parity/debug tooling:** `ClientSpellSlotParityComparator`/`ClientResourceParityPoll` remain wired (matching the identical, already-accepted post-migration state of Rage's own parity tooling — its legacy mirror also goes stale after Rage's authority flip, and that was not "fixed" during Phase 5 either) — only the tier-10 boundary was corrected so the comparator does not permanently misclassify the now-nonexistent partition 10 as a structural mismatch.

## 13. UI migration

**No UI code was changed.** `SpellRadialScreen` already read through `ClientResourcePresentationResolver.resolvePartition`, which already prefers the Generic client view over the legacy fallback whenever Generic is available. Flipping authority makes the "Generic" branch live and authoritative instead of an adapter mirror — the UI's own code is unaffected. Partition 10 is never requested (no spell has level 10), so no "hide tier 10 row" logic was needed either — there is structurally nothing to hide.

## 14. Files changed

**New (production):**
- `src/main/java/zcylas/totality/api/magic/spell/StandardSpellSlotMaximumResolver.java`
- `src/main/java/zcylas/totality/api/rpg/resources/integration/StandardSpellSlotResources.java`
- `src/main/java/zcylas/totality/api/rpg/resources/verification/StandardSpellSlotMigrationVerification.java`

**New (test):**
- `src/test/java/zcylas/totality/api/rpg/resources/integration/StandardSpellSlotResourcesTest.java`

**Modified (production):**
- `src/main/java/zcylas/totality/Totality.java` (registers the new verification)
- `src/main/java/zcylas/totality/api/magic/spell/SpellSlotComponent.java`
- `src/main/java/zcylas/totality/api/magic/spell/SpellSlotRecalculator.java`
- `src/main/java/zcylas/totality/api/magic/spell/SpellSlotTable.java`
- `src/main/java/zcylas/totality/api/rpg/classes/ClassChangeReconciler.java`
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java`
- `src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientSpellSlotParityComparator.java`
- `src/main/java/zcylas/totality/api/rpg/resources/integration/ResourceGrantReconciler.java`
- `src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java`
- `src/main/java/zcylas/totality/networking/ability/ActivateAbilityHandler.java`
- `src/main/java/zcylas/totality/networking/resource/BaselineResourceLifecycleEvents.java`

**Modified (tests):**
- `src/test/java/zcylas/totality/api/magic/spell/SpellSlotComponentTest.java`
- `src/test/java/zcylas/totality/api/magic/spell/SpellSlotRecalculatorCharacterizationTest.java`
- `src/test/java/zcylas/totality/api/magic/spell/SpellSlotTableTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryExternalAdapterFreezeTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalEntryPathTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponentExternalSafetyTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityPollTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientSpellSlotParityComparatorTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/external/StandardSpellSlotsResourceAdapterTest.java`
- `src/test/java/zcylas/totality/api/rpg/resources/integration/ResourceGrantReconcilerTest.java`
- `src/test/java/zcylas/totality/client/resource/parity/LegacyClientResourceParityReadersTest.java`

## 15. Tests

- **Resource definition / no tier 10:** `PlayerResourceRegistryTest` (new/updated methods), `StandardSpellSlotsResourceAdapterTest` (9-partition shape).
- **Maximum resolution:** `SpellSlotTableTest` (9-column tables, `noFullCasterRowAtAnyLevelHasATenthSlotTier` covering all 30 levels), `SpellSlotRecalculatorCharacterizationTest` (registry/bucketing composition) — the resolver/`computeMaxSlots` themselves require a real `ServerPlayer` (same established limitation as the retired `recalculate`), covered end-to-end by `StandardSpellSlotMigrationVerification` instead.
- **Warlock exclusion:** `SpellSlotRecalculatorCharacterizationTest` (pure logic), `StandardSpellSlotMigrationVerification` (real Warlock `ServerPlayer`, confirms no state), `StandardSpellSlotResourcesTest`.
- **Grant lifecycle (generic, synthetic resource):** `ResourceGrantReconcilerTest` — 6 new tests covering partitioned `AtMaximum` instantiation, `REMOVE_STATE` removal, reacquisition at a lower resolved maximum (the exact bug shape from the prior class-change task, generalized to partitioned), idempotence, and rejection of unsupported initialization variants.
- **Migration:** two layers, corrected for precision in the 2026-09-16 correction pass (see §21) —
  `SpellSlotComponentTest#readDataIgnoresHistoricalTierTenKeysFromAGenuinelyOldPreMigrationSave` (a
  pure unit test, hand-building the raw `max_9`/`used_9` NBT keys a genuinely old save would contain
  and reading them through the real `SpellSlotComponent#readData`) is the actual proof that historical
  tier-10 data is structurally discarded; `StandardSpellSlotMigrationVerification` (real `ServerPlayer`)
  proves the full production migration pipeline correctly imports and clamps valid tiers 1-9 and that
  the resulting Generic state has no partition 10 — using the legacy component's own current API,
  which (post-Phase-6) cannot itself hold a tier-10 value, so it does not by itself prove discard.
- **Casting (success/exact-tier/insufficient/no-effect):** `StandardSpellSlotMigrationVerification`.
  Success/exact-tier/insufficient are proven via the exact `PlayerResourceService.trySpend`/`EXACT_TIER`
  call `ActivateAbilityHandler` makes. No-effect is proven, as of the 2026-09-16 correction pass (see
  §21), through the real `ActivateAbilityHandler.handle` entry point itself (a synthetic `markNoEffect`
  spell unlocked and cast), not merely by never calling `trySpend`. The generic partitioned `trySpend`
  mechanism itself is already covered resource-agnostically by the pre-existing
  `PlayerResourceServiceMutationTest`/`PlayerResourceServiceTransactionTest`.
- **Rest:** `StandardSpellSlotMigrationVerification` (Long Rest full restore, Short Rest no-op).
- **Class change:** `StandardSpellSlotMigrationVerification` — **corrected in the 2026-09-16 §22
  level-up-regression correction pass:** a Wizard level-up now grants the newly gained capacity from a
  maximum increase while leaving already-spent capacity spent (`MaximumChangePolicy.PRESERVE_DEFICIT`),
  not "without refilling" as originally (incorrectly) asserted — see §22 for the full root-cause trace,
  the fix, and the four numeric examples (`MaximumChangePolicyTest`, real-production-path checks in
  `StandardSpellSlotMigrationVerification`).
- **Client presentation / Generic sync:** `ClientSpellSlotParityComparatorTest`, `ClientResourceParityPollTest`, `LegacyClientResourceParityReadersTest` (all updated to the 9-partition shape).

## 16. Dedicated verification

`StandardSpellSlotMigrationVerification` (new, dev-server-gated, mirrors `BarbarianRageMigrationVerification`'s established pattern exactly) — registered in `Totality.java`, gated on `VerificationReporter.isDevEnvironment()`.

## 17. Test/build results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **PASS** |
| `./gradlew compileTestJava` | **PASS** |
| Focused Spell Slot / Grant / Resource-registry test classes | **PASS** |
| `./gradlew test` (full suite) | **PASS** — 1595 tests → 1596 after the four-finding correction pass → 1607 after the §22 level-up-regression correction pass → **1611** after the §23 final cleanup pass (+4: 2 `MaximumChangePolicyTest` + 2 `ClassChangeReconcilerMaximumSyncSourceRegressionTest`), 0 failures, 0 errors |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) — reconfirmed after §23 |
| `git diff --check` | **PASS** — no real whitespace errors (only benign CRLF-normalization warnings) — reconfirmed after §23 |
| Bounded dedicated-server startup (`./gradlew runServer`, ~90s to first idle) | **PASS** — reached `Done (0.313s)!`, then every existing verification passed, and **`[StandardSpellSlotMigrationVerification] All 23 self-test checks passed.`** (14 original → 18 after the four-finding correction pass → 21 after §22 → 23 after §23's +2 maximum-only-sync-edge checks). `[ResourceFoundationVerification] All 6`, `[BaselineResourceMigrationVerification] All 11`, `[BarbarianRageMigrationVerification] All 18` — all unchanged, confirming Rage is unaffected throughout every pass. The same two unrelated, pre-existing verification failures observed in every prior phase's run (`ProvisionerEntityBackedSmokeTest` 3/4 FAIL, `OffhandAttackVerification` 3/5 FAIL) appeared again, identically — confirming this task neither caused nor worsened them. |

## 18. Manual test checklist

**A. Wizard**
1. Create/select Wizard.
2. Verify the Spell Radial shows valid Standard slots.
3. Confirm only tiers 1–9 can ever appear (no 10th row/pip at any level, including 25/30).
4. Cast a spell successfully and confirm exactly one correct-tier slot is consumed.
5. If a practical spell/path exists that can report no effect (e.g. no valid target), attempt it and confirm no slot is lost.
6. Long Rest → slots fully restore.
7. Short Rest → Standard slots do not restore.

**B. Class leveling**
1. Increase Wizard class level.
2. Confirm maxima update immediately — **and confirm this happens live, in the same session, without
   needing to log out and back in** (2026-09-16 §22 level-up-regression correction pass).
3. Spend slots, then level up — confirm the level-up **immediately grants any newly gained capacity**
   (e.g. a partially-spent tier whose maximum just grew becomes more available; a brand-new tier that
   just unlocked is fully available right away) **while any already-spent capacity stays spent** — not
   "does not refill" as this checklist previously (incorrectly) asked for; see §22.
3a. (2026-09-16 §22) Specifically reproduce the originally reported bug: reach Wizard class level 12,
    level normally to 13, and confirm the newly unlocked 7th-level slot appears **immediately** in the
    Spell HUD as `1/1` (not absent until reconnect, and not `0/1` after reconnect pending a Long Rest).
    **CONFIRMED by the user's own manual gameplay testing (§23.1): the slot now appears immediately as
    `1/1`, with no reconnect and no Long Rest required.**
3b. (2026-09-16 §23) The partitioned maximum-only sync edge fixed in §23.2 has not yet been separately
    manually reproduced in gameplay — it needs a shape less common than an ordinary level-up (e.g. a
    fully-spent tier whose maximum shrinks further, so its current stays at 0 while only the maximum
    changes). If practical, force such a case (e.g. via `/totality resetlevel` after fully spending a
    high tier) and confirm the Spell HUD's maximum for that tier updates without a reconnect.
4. (2026-09-16 correction pass, Finding 1) At a high Wizard class level, use `/totality resetlevel`
   (or `resetstats`/`resetall`), then reconnect. Confirm no crash on join, confirm Standard Spell
   Slots show a valid, clamped current/max for the now-lower class level (never a stale high value),
   and confirm the same for Rage if the player is also Barbarian.

**C. Multiclass**
1. If a second Standard-slot-contributing class becomes available, add it and confirm the shared pool recalculates correctly with no duplicate pool.

**D. Warlock**
1. Select Warlock.
2. Confirm Warlock does not gain or inflate Standard slots.
3. Do not expect Pact Magic (Phase 7).

**E. Persistence**
1. Reconnect — remaining slots persist correctly.
2. Change dimension — remaining slots persist correctly.

**F. High level**
1. Reach class level 25 and 30 (the old tier-10 milestones).
2. Confirm no ordinary 10th-level slot appears anywhere.

## 19. Remaining limitations

- `PlayerResourceService`/`ResourceGrantReconciler`'s `PARTITIONED_POOL` support remains `AtMaximum`-only for grant initialization and `EXACT_TIER`-only for player spending — sufficient for Standard Spell Slots today; the other declared-but-unimplemented variants remain exactly as unimplemented as they were before this task, unchanged.
- `ClientSpellSlotParityComparator`/parity polling for Standard Spell Slots will, like Rage's own already-accepted post-migration state, eventually observe a stale legacy side once the legacy component is never written to again — this is a pre-existing, already-accepted transitional-tooling characteristic (not something this task introduces or was asked to fix for Rage, so not fixed here for Spell Slots either), and is dev-diagnostic-only, never gameplay-affecting.
- `StandardSpellSlotMaximumResolver`/`SpellSlotRecalculator.computeCombinedCasterLevel`/`computeMaxSlots` are not directly unit-tested (require a real `ServerPlayer`) — covered by the dedicated dev-server verification instead, matching the exact precedent the pre-existing `SpellSlotRecalculatorCharacterizationTest` already established and documents for the retired `recalculate` method.

## 20. Phase 7 boundary — explicitly not started

Confirmed **not implemented, not started, not designed** by this task: Pact Magic, Ki, Hit Dice, Epic Magic, any ordinary 10th+ slot design, Warlock leveled-slot functionality of any kind. Also not touched: the Spell Radial's design, Class/Character/subclass UI, Food, the Minecraft 26.3 migration, the pre-existing `ProvisionerEntityBackedSmokeTest`/`OffhandAttackVerification` failures, and the previously-corrupted old test world (not opened, not touched, save repair remains out of scope). **No commit was created.**

## 21. Correction pass (2026-09-16) — four review findings

This section documents a follow-up correction pass performed before manual gameplay testing, on the
same uncommitted Phase 6 work. It does **not** re-baseline the task — the original pre-Phase-6 HEAD
(`7bd5d76607d91f00828d09b89db737f6585c4bef`) remains authoritative, and Phase 6 itself is still
uncommitted.

### 21.1 Finding 1 — join-time class-level mutation bypassing `ClassChangeReconciler` — **CONFIRMED, FIXED**

**Root cause, confirmed by direct source read:** `ModEvents.register()` registers
`BaselineResourceLifecycleEvents.register()` before `PlayerConnectionEvents.register()`, so on
`ServerPlayConnectionEvents.JOIN` Fabric fires them in that order:

1. `BaselineResourceLifecycleEvents`'s JOIN handler reconciles every class-owned Generic Resource
   grant (Rage, `totality:spell_slots`) using the player's **currently stored** class level.
2. `PlayerConnectionEvents`'s JOIN handler then normalizes the primary class's stored level down to
   `PlayerClassComponent.toClassLevel(playerLevel)` whenever `stored > available` — a real, reachable
   path: `/totality resetlevel`/`resetstats`/`resetall` lower the player's overall level but never
   touch `PlayerClassComponent` (confirmed in the prior "class-change reconciliation" task's own
   audit), so the very next join finds `stored > available`.
3. `PlayerClassComponent.setClassLevel` only mutates the map and calls `sync()` — **no reconciliation
   of any kind.**

So a class-owned resource is granted/clamped at the *old*, higher level, the class level then drops,
and nothing re-clamps the already-instantiated state — the exact `current > maximum` shape
`ClassChangeReconciler` exists to prevent, reachable through a lifecycle path that predates
`ClassChangeReconciler`'s own introduction and was never updated to use it. This is a genuine defect,
not a false positive, and matches the same lifecycle family as the original stale-Rage bug.

**Exact fix** (`PlayerConnectionEvents.java`, inside the existing `if (stored > available)` block):

```java
classComp.setClassLevel(primaryClass, available);
ClassChangeReconciler.reconcile(player);
```

**Why this is universal, not Spell-Slot-specific:** `ClassChangeReconciler.reconcile` (i) re-evaluates
every registered `ResourceGrantProvider` against the player's *current* class ownership and (ii)
defensively clamps every `GENERIC_COMPONENT` resource (scalar or partitioned) whose current exceeds
its freshly resolved maximum. It has no knowledge of Rage, Spell Slots, or any specific resource — it
was already the exact seam `showclass` uses for the analogous bug in the prior task. Rage benefits
automatically today (if the primary class happens to be Barbarian); any future Ki/Pact Magic grant
provider registered on the same shared `ResourceGrantRegistry.INSTANCE` benefits automatically too,
with zero changes to this fix.

**Ordering vs. fix-in-place:** reordering `ModEvents.register()` (so class-level normalization runs
before resource reconciliation) was considered and rejected as *not* the smaller/cleaner fix —
`BaselineResourceLifecycleEvents`'s own extensive Javadoc documents multiple *other* ordering
dependencies (legacy Mana/Stamina sync-packet timing, `ResourceSyncLifecycleEvents`' full-snapshot
inclusion) that already rely on its current registration position. Moving it would risk those, for no
benefit over simply reconciling again after the one mutation that needs it — the same "reconcile
immediately after every class mutation" principle `ClassChangeReconciler` already embodies everywhere
else.

**Stale claim corrected:** `ClassChangeReconciler`'s own Javadoc previously stated "No current
production class-derived maximum table can actually decrease this way today" — this investigation
disproves that. No progression *table* decreases as a function of level (every table is monotonically
non-decreasing by row), but the *level itself* can be forced downward outside ordinary level-up flow
via this exact JOIN path, which resolves to the same lower-maximum shape. The Javadoc has been
corrected in place to describe the real trigger.

**Regression added** (`StandardSpellSlotMigrationVerification`, real `ServerPlayer` via
`TotalityFakePlayer`) — the nearest real production lifecycle seam, since `TotalityFakePlayer` cannot
fire `ServerPlayConnectionEvents.JOIN` (the same established limitation `BaselineResourceLifecycleEvents
.migrateLegacyIfAbsent`'s own tests already work around by calling the production method directly
instead of firing the event): a level-20 Wizard is seeded with real `AtMaximum` state (1st-level 4/4,
2nd-level 3/3), 3 of 4 1st-level slots are spent, then `PlayerClassComponent.setClassLevel` lowers the
stored level to 1 directly (exactly what the fixed JOIN code now does) followed by the exact
`ClassChangeReconciler.reconcile(player)` call the fix adds. Proven: every partition satisfies
`current <= maximum`; the still-valid 1st-level partition is left at its preserved value 1/2 (not
refilled to 2, proving no free refill); the now-over-maximum 2nd-level partition is clamped from 3 down
to exactly 0 (its new maximum); no partition 10 exists; and `ResourcePartitionedWireSnapshot.from(...)`
does not throw (wire-safe).

### 21.2 Finding 2 — canonical Resource API document was stale — **CONFIRMED, CORRECTED**

`Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` contained several Standard-Spell-Slot-specific
statements still describing the pre-Phase-6 1–10 shape:

| Location | Before | After |
|---|---|---|
| §6.2 example | "partitioned by spell level `1..10`" | "partitioned by spell level `1..9` (there is no ordinary tier 10...)" |
| §14.3 | No explicit tier-count statement | Added: partitions `1..9` only, historical tier-10 removed/not migrated/not replaced, Epic Magic (if ever designed) is separate |
| §24.4 step 4 | "import every current tier exactly" | "import every valid tier (`1..9`) exactly; any historical tier-10 data... is discarded, never migrated" |
| §25.8 spec block | `Tiers: 1..10` | `Tiers: 1..9 (no ordinary tier 10...)`, plus an explicit no-tier-10 rule bullet |
| Phase 6 plan bullets | "Import every tier." / "Preserve level 1–10 capacity." | Corrected to "1–9", with an explicit superseded-canon note |
| §28.8 | "Existing multiclass table output is unchanged." | Corrected to state the valid 1–9 output is preserved exactly and the tier-10 entitlement is intentionally removed with no invented replacement |

**Generic partition id 10 was preserved, not deleted, per explicit instruction:** §28.7's "Level 10
survives persistence" is **generic `PARTITIONED_POOL` infrastructure** (any owner may use partition id
`10` — e.g. Hit Dice's own `d10`, per §28.9, which is completely unaffected by this correction), not a
Standard Spell Slot statement. It was reworded to: *"A synthetic/non-Spell-Slot partition id such as
`10` survives persistence... This is infrastructure-level, not a Standard Spell Slot statement:
Standard Spell Slots are restricted to partitions `1..9` only... and must never expose or resurrect a
partition 10."* No canonical Epic Magic system was invented anywhere in this document — it never
mentioned "Epic Magic" before this correction pass either (grepped, confirmed zero hits), so nothing
was retroactively asserted about it beyond "if it is ever designed, it is separate."

### 21.3 Finding 3 — old tier-10 save test precision — **REPORT OVERSTATEMENT CONFIRMED AND CORRECTED, TRUE RAW-NBT REGRESSION ADDED**

**Investigation:** `SpellSlotComponent.writeData`/`readData` loop `for (i = 0; i < MAX_SPELL_LEVEL; i++)`
— now `MAX_SPELL_LEVEL = 9` — so the current reader/writer never touches keys `max_9`/`used_9` at all.
An old, pre-Phase-6 save's persisted `max_9`/`used_9` (the zero-based index that represented the old
ordinary 10th-level slot) is therefore structurally unreadable, not actively filtered — it simply sits
in the NBT tag, inert, forever.

**Overstatement found and corrected:** the original `StandardSpellSlotMigrationVerification` migration
check was labeled "...discards legacy tier 10 entirely" and its comment claimed to simulate "a stray
legacy tier-10 value that must never migrate," but its code only ever called
`SpellSlotComponent#recalculate`/`#useSlot` — the *current* component API, which (post-Phase-6) has no
way to represent a tier-10 value at all. The check's `partition(10).isEmpty()` assertion therefore
passed vacuously (partition 10 was never in the resource definition to begin with), proving nothing
about historical NBT data. The check's label/comment have been corrected to state exactly what it does
and does not prove, and to point at the new true regression below.

**True raw-NBT regression added** — a pure unit test,
`SpellSlotComponentTest#readDataIgnoresHistoricalTierTenKeysFromAGenuinelyOldPreMigrationSave`: hand-
builds a `TagValueOutput` with the exact keys a genuinely old, pre-Phase-6 save would contain (`max_0`
`..max_8`/`used_0..used_8` for valid tiers 1–9, plus historical `max_9=1`/`used_9=1` for the old spent
10th-level slot), reads it through the real, current `SpellSlotComponent#readData`, and proves: tiers
1–9 round-trip exactly; `getMax(10)`/`getUsed(10)` return `0` (the historical keys are never read into
any array slot — there is no index 9 to read them into); `hasSlot(10)` is `false`. No fake production
path was invented — this exercises the real `readData` method directly, matching this test file's own
established `TagValueOutput`/`TagValueInput` precedent.

**Exact behavior of old `max_9`/`used_9` data, stated plainly:** it is never read by any current code
path, never enters memory, never reaches Generic state, and cannot resurrect tier 10 — not because
anything actively strips it, but because the reader's loop bound is structurally `9`, matching the
task's own instruction not to overclaim beyond what's actually proven.

The dev-server migration check (`BaselineResourceLifecycleEvents.migrateLegacyIfAbsent`, real
`ServerPlayer`) remains valuable and unchanged in substance — it is still the only proof that the full
production migration *pipeline* (legacy component → Generic state, including clamping and the
idempotent marker) works correctly for valid tiers — but its claim is now scoped accurately to that,
not to tier-10 discard.

### 21.4 Finding 4 — no-effect cast coverage precision — **REPORT OVERSTATEMENT CONFIRMED AND CORRECTED, OPTION A (REAL HANDLER-LEVEL TEST) IMPLEMENTED**

**Investigation:** the original `StandardSpellSlotMigrationVerification` "no-effect" check queried
`totality:spell_slots` before and after doing *nothing* (deliberately never calling `trySpend`) and
asserted the value was unchanged — true, but vacuous: it proves the resource layer is inert when
nothing touches it, not that `ActivateAbilityHandler` itself correctly withholds spending after a
real `markNoEffect()` cast.

**Option A implemented** (small and feasible, not a large networking/game bootstrap):
`ActivateAbilityHandler.handle` was changed from `private` to `public` (correction pass, 2026-09-16)
so a dev-only verification can call the real production entry point directly — the same "expose pure/
impure logic for direct testing" precedent `SelectSubclassHandler#apply` already established, just
`public` rather than merely package-visible since the caller is a different package (matching every
other production entry point these verifications already call — `PlayerResourceService.trySpend`,
`BarbarianRageAbility.registerChargePool`, etc. are all `public` too). Two small dev-only synthetic
level-1 `Spell` subclasses (`NoEffectTestSpell`, `AlwaysSucceedsTestSpell`) are registered into the
real, shared `AbilityRegistry` via its own existing public `#add(Ability)` seam (documented on that
method as exactly "sub-registries inject entries without declaring them in `AbilityRegistry` itself"),
gated inside the same `VerificationReporter.isDevEnvironment()` check as everything else in this class
(never in a static initializer, which would load the class — and register the spells — unconditionally
regardless of environment).

**Proven, through the real `ActivateAbilityHandler.handle` entry point** (constructing a real
`ActivateAbilityPayload`, not a reimplementation of its control flow):
- A spell whose `onActivate` calls `Spell.markNoEffect()` consumes **no** slot.
- A control spell whose `onActivate` does nothing (so `Spell.didCastSucceed()` stays `true`) **does**
  consume exactly one slot — proving the no-effect result above is genuinely discriminating real
  cast-result branching, not merely a handler that never spends anything.

**A real production behavior change was required to make this testable** (`ActivateAbilityHandler
.handle`'s visibility) — no behavior of the handler itself changed, only its visibility.

**Distinguishing the three layers of proof, precisely, as required:**
1. **Automated proof of Generic `EXACT_TIER` spending itself** — resource-agnostic, pre-existing:
   `PlayerResourceServiceMutationTest`/`PlayerResourceServiceTransactionTest`.
2. **Automated proof that `ActivateAbilityHandler` only spends after `Spell.didCastSucceed()`** — now
   both a direct source-control-flow read (§7 above, unchanged) *and*, as of this correction pass, a
   real executing test through the actual handler (§21.4 above).
3. **Actual no-effect gameplay behavior** (a real player casting a real content spell that reports no
   effect, e.g. no valid target) — still requires manual validation; the manual checklist's item A.5
   remains, unchanged, for exactly this reason.

### 21.5 Files changed in this correction pass

- `src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java` — Finding 1 fix.
- `src/main/java/zcylas/totality/api/rpg/classes/ClassChangeReconciler.java` — Finding 1 stale-claim correction.
- `src/main/java/zcylas/totality/networking/ability/ActivateAbilityHandler.java` — Finding 4, `handle` made `public`.
- `src/main/java/zcylas/totality/api/rpg/resources/verification/StandardSpellSlotMigrationVerification.java` — Finding 1 regression (3 new checks), Finding 4 regression (2 new checks replacing 1 weak one), Finding 3 check label/comment correction, a small state-restoring line so the Finding 4 addition cannot shift any pre-existing check's hardcoded expectations.
- `src/test/java/zcylas/totality/api/magic/spell/SpellSlotComponentTest.java` — Finding 3 true raw-NBT regression.
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` — Finding 2 corrections (newly touched by Phase 6 work for the first time).
- This implementation report and its review bundle — updated in place.

### 21.6 Updated total Phase 6 changed-file set

27 files (§14 above) **+ 1** (`Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`, touched for the
first time by this correction pass) **= 28 files**, plus the report and review bundle artifacts
themselves. No file was added to or removed from the *production/test* set beyond what §14 already
listed — every other file this correction pass touched was already in that list.

### 21.7 Validation (this correction pass)

| Check | Result |
|---|---|
| Focused tests (`SpellSlotComponentTest`, then full suite) | **PASS** |
| `./gradlew test` (full suite) | **PASS** — 1596 tests, 0 failures, 0 errors (was 1595 before this pass — +1 new raw-NBT unit test) |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` | **PASS** — no real whitespace errors |
| Bounded dedicated-server startup (`./gradlew runServer`) | First run surfaced a real self-inflicted regression (see below); **second run PASS**: `[ResourceFoundationVerification] All 6 self-test checks passed.`, `[BaselineResourceMigrationVerification] All 11 self-test checks passed.`, `[BarbarianRageMigrationVerification] All 18 self-test checks passed.` (unchanged — confirms the `ClassChangeReconciler` Javadoc-only edit did not affect Rage behavior), **`[StandardSpellSlotMigrationVerification] All 18 self-test checks passed.`** (was 14 before this pass: +3 Finding 1, +2 Finding 4 replacing 1 removed = net +4). The same two unrelated, pre-existing failures (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) appeared again, identically. |

**Self-inflicted regression caught and fixed during this pass, worth recording honestly:** the first
`runServer` attempt after adding the Finding 4 checks failed one *pre-existing* check
("`EXACT_TIER` never falls back to another tier...") — not a real production bug, but a test-isolation
bug in the verification itself: the new `AlwaysSucceedsTestSpell` check legitimately spends one real
slot from the shared `wizard` player (that is the check's own point), which shifted the hardcoded
expected value the next, pre-existing check relied on. Fixed by restoring the exact expected state
(Long Rest + one deliberate re-spend) immediately after the new checks, before the pre-existing
sequence resumes. Caught by actually running the dedicated server rather than assuming success — the
task's own "do not claim a runtime scenario passed unless it actually executed" instruction, applied
to this pass's own new code.

### 21.8 Confirmations

No commit was created. Phase 7 (Pact Magic, Ki, Hit Dice, Epic Magic) was not started. Reset command
semantics, the Spell Radial, Class/Character UI, Food, the Minecraft 26.3 migration, the pre-existing
`ProvisionerEntityBackedSmokeTest`/`OffhandAttackVerification` failures, and the previously-corrupted
old test world were not touched by this correction pass either.

## 22. Correction pass (2026-09-16) — level-up regression discovered during manual testing

This section documents a second, later correction pass, performed on the same still-uncommitted
Phase 6 work after §21's four findings were already fixed, in response to a genuine bug found during
the user's own manual gameplay testing. It does not re-baseline the task — the original pre-Phase-6
HEAD (`7bd5d76607d91f00828d09b89db737f6585c4bef`) remains authoritative.

### 22.1 Manual reproduction (as reported)

1. Wizard class level 12 → level normally to 13 (Wizard 13 unlocks a 7th-level Standard Spell Slot:
   `SpellSlotTable.FULL_CASTER` row 12 tier 7 = 0, row 13 tier 7 = 1).
2. The new 7th-level slot did not appear at all in the Spell HUD immediately after leveling.
3. After logging out and back in, the slot appeared, but empty (`0/1`).
4. After a Long Rest, it became `1/1`.

### 22.2 Root cause, confirmed by direct source read

Two bugs sharing one root cause:

**A. Server-side `current` was never granted the newly available capacity.** `ResourceGrantReconciler
.reconcile` (step 1 of `ClassChangeReconciler.reconcile`) explicitly never refills a resource that
stays continuously granted across a class mutation (canonical §16.6: "the stored value is never
refilled by re-reconciliation") — correct for the ordinary "nothing changed" case, but it means a
resource whose *maximum* just grew (a multiclass level-up unlocking a new tier, or raising an
already-partially-filled tier's cap) was **never reconciled against that growth at all**.
`ClassChangeReconciler`'s only other step, the old `clampResourcesAboveResolvedMaximum`, exclusively
handled the shrink direction (`current > max` → clamp down) — it had no symmetric branch for growth.
So the freshly resolved maximum (`StandardSpellSlotMaximumResolver`, resolved live on every query) was
always correct, but the stored `current` for a still-granted resource never moved, no matter how much
its maximum grew.

**B. The client was never told, because nothing marked the resource dirty.** Since `current` never
changed on a maximum-only growth, and the only thing that ever calls `ResourceSyncManager.markDirty`
for `totality:spell_slots` is a successful mutation (`trySpend`/`drain`/`restore`/`reconcileMaximum`),
**nothing at all** called `markDirty` after an ordinary level-up that only changed the resolved
maximum. `PlayerResourceService.queryGenericPartitioned` (the query the sync path uses to build
packets) has *always* resolved the maximum live and would have reported the new tier correctly on any
query — the bug was never in query correctness, only in nothing ever triggering that query to be sent
to the client. A reconnect "self-heals" the display because `BaselineResourceLifecycleEvents`'
JOIN handler schedules a full snapshot regardless of dirty state — explaining exactly the reported
`0/1`-after-reconnect symptom: the maximum syncs correctly on the forced full snapshot, but `current`
is still genuinely `0` server-side (bug A), until the next Long Rest.

**Prior art for bug B, found during this investigation:** `BarbarianRageAbility.updateChargePool`
already solves the identical sync problem for Rage (external review, 2026-09-15) — it explicitly calls
`ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE)` unconditionally on every
Barbarian level-up, wired through `ClassLevelUpRegistry`'s per-class callback (`BarbarianClass.java`).
Rage's own Javadoc explicitly documents that Rage does *not* need bug A's fix ("`RAGE_CHARGES` is
monotonically non-decreasing and no respec/level-down path exists in production, so no clamp/
reconcileMaximum call is needed") — i.e. Rage's design deliberately never auto-grants a new charge on
level-up (only rests do), so only its sync-marking half of this bug class ever mattered for Rage. When
Phase 6 migrated Standard Spell Slots to `GENERIC_COMPONENT` authority, no equivalent wire-up was ever
added for it — this is exactly the gap this correction pass closes, generically rather than by adding
a fourth bespoke per-class `updateXxx` method.

### 22.3 Exact fix

**New per-resource opt-in policy field**, `ResourceGrantPolicy.maximumChangePolicy()`
(`src/main/java/zcylas/totality/api/rpg/resources/integration/ResourceGrantPolicy.java`) — defaults to
`MaximumChangePolicy.CLAMP_CURRENT` (a 2-arg compact constructor overload preserves every pre-existing
call site's behavior unchanged, including the 3 direct constructions in `ResourceGrantReconcilerTest`).
Canonical §10.5 is explicit that this must be an opt-in, per-resource choice, not a blanket behavior
change: *"A maximum increase caused by level-up does not automatically refill the newly added capacity.
The owning progression/class system must explicitly choose whether to preserve current, add the gained
difference, or fill."* `StandardSpellSlotResources.register()` now registers
`MaximumChangePolicy.PRESERVE_DEFICIT` for `totality:spell_slots` — Rage, Mana, and Stamina are
untouched and keep today's `CLAMP_CURRENT` behavior exactly.

**`MaximumChangePolicy.reconcileCurrent(current, previousMaximumUnits, newMaximumUnits, floor)`** — the
§11.4 formula, extracted (byte-for-byte identical) from `PlayerResourceService.reconcileMaximumGenericState`'s
existing inline `switch` so scalar and the new partitioned path can never drift apart. `PlayerResourceService`
itself is otherwise unchanged for scalar resources.

**`ClassChangeReconciler`** (`src/main/java/zcylas/totality/api/rpg/classes/ClassChangeReconciler.java`)
— the old clamp-only `clampResourcesAboveResolvedMaximum`/`clampScalar`/`clampPartitioned` were replaced
with a policy-driven `reconcileResourcesAgainstResolvedMaximum`/`reconcileScalar`/`reconcilePartitioned`:

- **New:** `public static Map<Identifier, ResourceMaximum> captureResolvedMaximums(ServerPlayer player)`
  — snapshots every currently-active `GENERIC_COMPONENT` resource's resolved maximum. Must be called
  by the caller *before* its mutation, since `ClassChangeReconciler` itself is always invoked *after*
  the mutation at every existing call site — there is no way for this class to recover a genuine
  "before" value on its own.
- **New overload:** `reconcile(ServerPlayer player, Map<Identifier, ResourceMaximum> resolvedMaximumsBeforeMutation)`.
  The existing `reconcile(ServerPlayer player)` now simply calls this with an empty map.
- **Degenerate-safe by construction:** when no real "before" value exists for a resource (the empty-map
  case, or a resource newly instantiated this same pass), `previousMaximumUnits` defaults to the
  *freshly resolved* maximum itself — which makes every policy's formula (including `PRESERVE_DEFICIT`)
  collapse to plain clamp-down, byte-for-byte identical to the old `clampResourcesAboveResolvedMaximum`
  behavior. This is why every pre-existing call site (`SelectClassHandler`, `SelectSubclassHandler`,
  `TotalityCommands`' `showclass` reset, `PlayerConnectionEvents`' Finding-1 JOIN normalization, and
  every verification's own plain `reconcile(player)` calls) needed **zero changes** and their existing
  assertions (including the Finding 1 regression) continue to pass unmodified.
- **`reconcilePartitioned`** has no `PlayerResourceService.reconcileMaximum` primitive to delegate to
  (that method is scalar-only) — it applies `MaximumChangePolicy.reconcileCurrent` per partition
  directly and moves the resulting delta through the existing `PlayerResourceService.restore`/`drain`
  primitives, which already mark the resource dirty for the Generic sync path on success — this is
  exactly what closes bug B (see §22.5).

**`AddClassLevelHandler`** (`src/main/java/zcylas/totality/networking/classes/AddClassLevelHandler.java`)
— the one call site actually reproducing the reported bug (the ordinary multiclass level-up path) now
captures the "before" snapshot immediately before `comp.addClassLevel(classId)` and passes it to the
new `reconcile(player, before)` overload:

```java
var resolvedMaximumsBeforeLevelUp = ClassChangeReconciler.captureResolvedMaximums(player);
comp.addClassLevel(classId);
comp.sync();
ClassLevelUpRegistry.fire(player, classId, playerLevel);
ClassChangeReconciler.reconcile(player, resolvedMaximumsBeforeLevelUp);
```

No other call site was changed — `SelectClassHandler` always instantiates a fresh grant (`AtMaximum`
already correctly initializes it); `SelectSubclassHandler`/`showclass` reset do not currently affect
any resource's maximum. If a future subclass-scoped or reset-adjacent resource does need
growth-preserving reconciliation, that call site would need the same two-line `captureResolvedMaximums`
/`reconcile(player, before)` treatment — noted here rather than speculatively applied everywhere
("do not over-generalize merely for elegance").

### 22.4 Maximum-change semantics — the four required examples, all verified

| Scenario | Old max | Old current | New max | Expected new current | Verified by |
|---|---|---|---|---|---|
| New tier unlock | 0 | 0 | 1 (or 2) | 1 (or 2, i.e. fully available) | `MaximumChangePolicyTest#preserveDeficitGrantsANewlyUnlockedTierImmediately`; `StandardSpellSlotMigrationVerification`'s tier-2 check (level 2→3) and the exact-manual-reproduction tier-7 check (level 12→13) |
| Increase, partially spent | 3 (2 here) | 1 | 4 (3 here) | 2 | `MaximumChangePolicyTest#preserveDeficitGrantsTheGainedDifferenceWhilePartiallySpent`; `StandardSpellSlotMigrationVerification`'s "leveling the Wizard class up" check (level 1→2, tier 1: 2→3) |
| Decrease, spent slots preserved | 4 | 2 | 3 | 1 | `MaximumChangePolicyTest#preserveDeficitClampsAShrinkingMaximumWhilePreservingSpentCapacity`; `StandardSpellSlotMigrationVerification`'s dedicated `growthPreservation` decrease check (level 3→2, tier 1: 4→3), using a genuine captured "before" — distinct from the pre-existing Finding-1 `classLevelDecrease` check, which deliberately exercises the *degenerate* no-before-captured path |
| Fully spent | 3 | 0 | 4 | 1 | `MaximumChangePolicyTest#preserveDeficitGrantsOnlyTheNewlyGainedCapacityWhenFullySpent`; `StandardSpellSlotMigrationVerification`'s `growthPreservation` tier-1 check (level 2→3, tier 1: 3→4, fully spent beforehand) |

All four are the exact `spent = oldMax - oldCurrent; newCurrent = max(0, min(newMax, newMax - spent))`
formula the task specified, verified both as a pure formula and through the real
`ClassChangeReconciler`/`totality:spell_slots` production pipeline with a real `ServerPlayer`.

### 22.5 Immediate client synchronization — how it is now guaranteed

`reconcilePartitioned`'s delta is applied via `PlayerResourceService.restore`/`.drain` — the exact same
primitives every ordinary spend/rest-restore call already uses, and those primitives unconditionally
call `ResourceSyncManager.markDirty` on a successful, non-zero mutation (pre-existing, proven
behavior — this task did not need to and did not change it). `ResourceSyncManager.flush` runs once per
server tick, after ordinary server logic, for every player with pending work — so a level-up that
grants new capacity is delivered to the client within one tick (~50 ms), never requiring a reconnect.
`reconcileScalar` gets the same guarantee for free through the pre-existing
`PlayerResourceService.reconcileMaximum`, which already marks dirty unconditionally on success — this
also means a maximum-only change with **no** current adjustment (e.g. Rage under its default
`CLAMP_CURRENT` policy) now also gets marked dirty whenever a real "before" snapshot shows its maximum
moved, closing the same class of staleness `BarbarianRageAbility.updateChargePool` already closed for
Rage by a different, bespoke path — redundantly but harmlessly (`markDirty` is an idempotent set
operation).

Because `ResourceSyncManager`'s per-player dirty-tracking state is private static and exposes no
test-safe inspection point (the same constraint `BarbarianRageAbilityMaximumSyncSourceRegressionTest`
already documents and works around), this guarantee is proven two ways, matching that exact precedent:

1. **Behaviorally**, real production path: `StandardSpellSlotMigrationVerification`'s checks query
   `PlayerResourceService.query` immediately after `ClassChangeReconciler.reconcile` returns and observe
   the corrected value with no intervening step — this is the exact query the sync path itself uses to
   build outgoing packets, so a correct query result here is precisely what a subsequent flush delivers.
2. **By source-text sentinel**, `ClassChangeReconcilerMaximumSyncSourceRegressionTest` (new): proves
   `reconcileScalar`/`reconcilePartitioned` route every maximum-driven change through
   `PlayerResourceService.reconcileMaximum`/`.restore`/`.drain` (the primitives already proven elsewhere
   to mark dirty on success) rather than a raw `ScalarResourceState`/`PartitionedResourceState` mutation
   that would silently bypass sync, and that the no-op guard prevents marking every
   `GENERIC_COMPONENT` resource dirty on every class change regardless of whether its own maximum moved.

True end-to-end proof that a client actually receives a packet before reconnecting would require real
network I/O, which this test harness does not have — consistent with how the equivalent Rage guarantee
was proven previously, and with this task's own instruction not to build a large new testing framework
for this.

### 22.6 Tests added/changed

- **New:** `src/test/java/zcylas/totality/api/rpg/resources/MaximumChangePolicyTest.java` — 8 tests,
  the pure formula, covering all four task examples plus `CLAMP_CURRENT`/degenerate-default/
  `ALLOW_OVERFLOW` sanity.
- **New:** `src/test/java/zcylas/totality/api/rpg/classes/ClassChangeReconcilerMaximumSyncSourceRegressionTest.java`
  — 3 tests, the source-text sync-routing sentinel (§22.5).
- **Modified:** `StandardSpellSlotMigrationVerification.java` — the pre-existing "leveling the Wizard
  class up..." check corrected (it previously asserted the *bug's* behavior — `current` unchanged
  across a maximum increase — as expected; now asserts the fix, using the real
  `captureResolvedMaximums`/`addClassLevel`/`reconcile(player, before)` sequence). Three new checks
  added: the combined new-tier-unlock + fully-spent example (`growthPreservation` player, level 2→3),
  the genuine captured-before decrease example (`growthPreservation` player, level 3→2), and the exact
  manual-reproduction check (`tierSevenUnlock` player, level 12→13, tier 7). Net: 18 → 21 checks.

### 22.7 Files changed (this correction pass)

**Modified (production):**
- `src/main/java/zcylas/totality/api/rpg/resources/MaximumChangePolicy.java` (new `reconcileCurrent` method; updated class Javadoc)
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceService.java` (inline switch replaced with a call to `MaximumChangePolicy.reconcileCurrent` — behavior unchanged)
- `src/main/java/zcylas/totality/api/rpg/resources/integration/ResourceGrantPolicy.java` (new `maximumChangePolicy` field, defaulted, with a 2-arg compact-constructor overload)
- `src/main/java/zcylas/totality/api/rpg/resources/integration/StandardSpellSlotResources.java` (registers `PRESERVE_DEFICIT`)
- `src/main/java/zcylas/totality/api/rpg/classes/ClassChangeReconciler.java` (new `captureResolvedMaximums`/`reconcile(player, before)`; clamp-only steps replaced with policy-driven reconciliation)
- `src/main/java/zcylas/totality/networking/classes/AddClassLevelHandler.java` (captures "before" and uses the new overload)

**New (test):**
- `src/test/java/zcylas/totality/api/rpg/resources/MaximumChangePolicyTest.java`
- `src/test/java/zcylas/totality/api/rpg/classes/ClassChangeReconcilerMaximumSyncSourceRegressionTest.java`

**Modified (test):**
- `src/main/java/zcylas/totality/api/rpg/resources/verification/StandardSpellSlotMigrationVerification.java`

**Updated total Phase 6 changed-file set:** 34 files (28 after the four-finding correction pass, +6:
2 new test files, 4 modified production files beyond `ClassChangeReconciler`/`StandardSpellSlotMigrationVerification`,
which were already in the 28; `AddClassLevelHandler.java` is newly touched by Phase 6 work for the
first time). §14's file list above reflects the original Phase 6 pass only and has not been rewritten
wholesale — this section and §21 together are the authoritative record of everything added since.

### 22.8 Validation (this correction pass)

| Check | Result |
|---|---|
| Focused tests (`MaximumChangePolicyTest`, `ClassChangeReconcilerMaximumSyncSourceRegressionTest`) | **PASS** |
| `./gradlew test` (full suite) | **PASS** — 1607 tests, 0 failures, 0 errors (was 1596 before this pass — +11) |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` | **PASS** — no real whitespace errors (only benign CRLF-normalization warnings) |
| Bounded dedicated-server run (`./gradlew runServer`) | **PASS** — `[ResourceFoundationVerification] All 6`, `[BaselineResourceMigrationVerification] All 11`, `[BarbarianRageMigrationVerification] All 18` (all unchanged — Rage unaffected), **`[StandardSpellSlotMigrationVerification] All 21 self-test checks passed.`** (was 18). The same two unrelated, pre-existing failures (`ProvisionerEntityBackedSmokeTest` 3/4 FAIL, `OffhandAttackVerification` 3/5 FAIL) appeared again, identically. |

### 22.9 Remaining concerns

- True end-to-end network-packet delivery is not exercised by this test harness (see §22.5) — the
  manual checklist item B.2/B.3a above asks the user to confirm this visually.
- `Rage`/`Mana`/`Stamina` now also get marked dirty on a class change whenever a real `before` snapshot
  shows their own maximum moved (even though their `CLAMP_CURRENT` policy leaves `current` unchanged)
  — this is a strictly positive side effect (closes the same staleness class `updateChargePool` already
  closed for Rage via a different path, redundantly but harmlessly), not a regression; confirmed by the
  unchanged `BarbarianRageMigrationVerification` (18/18, same as before this pass).
- `SelectSubclassHandler`/`showclass` reset were deliberately left on the plain, degenerate
  `reconcile(player)` overload since no current resource's maximum depends on subclass or is affected
  by a class reset short of removal — flagged in §22.3 as the thing to revisit if that ever changes.

### 22.10 Confirmations

No commit was created (`git rev-parse HEAD` still `7bd5d76607d91f00828d09b89db737f6585c4bef`). Phase 7
(Pact Magic, Ki, Hit Dice, Epic Magic) was not started. Crown of Stars' separate, pre-existing
active-mote/follow-up-cast bug was not touched. The Spell Radial's design, Class/Character UI, Food,
the Minecraft 26.3 migration, the pre-existing `ProvisionerEntityBackedSmokeTest`/`OffhandAttackVerification`
failures, and the previously-corrupted old test world were not touched by this correction pass either.

## 23. Final cleanup pass (2026-09-16) — after manual confirmation, before commit

This section documents a final, small cleanup pass performed after §22's level-up fix was manually
confirmed working end-to-end in real gameplay, on the same still-uncommitted Phase 6 work. It does not
re-baseline the task — the original pre-Phase-6 HEAD (`7bd5d76607d91f00828d09b89db737f6585c4bef`)
remains authoritative.

### 23.1 Manual confirmation (Wizard 12 → 13, live)

The user manually retested the exact §22.1 reproduction after §22's fix: Wizard class level 12 →
level normally to 13 → the newly unlocked 7th-level Standard Spell Slot **now appears immediately in
the Spell HUD as `1/1`, with no reconnect and no Long Rest required.** This is a live, human-observed
confirmation of the end-to-end client delivery §22.5 could only argue for indirectly (behavioral query
correctness + a source-regression sentinel, since the automated test harness has no real network I/O)
— it is now directly confirmed, not merely inferred from server-side proxies. This behavior was **not**
redesigned or touched by this cleanup pass.

### 23.2 Finding 1 — partitioned maximum-only sync edge — **CONFIRMED, FIXED**

**Investigation, traced against the actual current code (not assumed):** `ClassChangeReconciler
.reconcilePartitioned` computes, per partition, `reconciled = policy.reconcileCurrent(current,
previousMaximumUnits, newMax, floor)` and `delta = reconciled - current`, then `if (delta == 0)
continue;` — skipping straight to the next partition with **no call of any kind** when the reconciled
value happens to equal the already-stored current. Since the *only* thing that ever calls {@code
ResourceSyncManager.markDirty} for a partitioned resource is a successful `PlayerResourceService
.restore`/`.drain` call (confirmed by re-reading `applyClampedDelta`, unchanged since §22), a partition
whose **maximum genuinely changed** (a real captured `previousMax` differs from the freshly resolved
`newMax`) but whose reconciled current happens to land back on the same value it already had produces
**zero drain/restore calls anywhere in the resource**, and therefore **nothing marks the resource
dirty** — even though the client's cached maximum for that partition is now stale. `PlayerResourceService
.queryGenericPartitioned` (traced again) always resolves the maximum live and would report it
correctly on any subsequent query, so this is purely a "nothing ever triggers that query to be sent"
gap — the exact same shape as §22's original bug B, just for a case §22's fix did not cover.

This is reachable in real production: any `PRESERVE_DEFICIT`/`CLAMP_CURRENT` partition that is already
at the floor (fully spent, `current = 0`) whose maximum shrinks further still reconciles to `0` (the
floor can't go lower), or any `CLAMP_CURRENT` partition whose `current` is already below both the old
and new maximum (the task's own literal example: old max 4, current 2, new max 5) — neither ever
produces a nonzero delta, so neither would have marked the resource dirty before this fix.

**Exact fix** (`ClassChangeReconciler.reconcilePartitioned`): two booleans, `mutated` and
`maximumChanged`, are tracked across the per-partition loop — `maximumChanged` is set whenever a real
`previousMaximumUnits != newMax` for any partition (this can only ever be true when a genuine
`previousMax` was supplied — the `previousMax == null` degenerate default sets every partition's
"previous" equal to its own freshly resolved maximum, so it can never trip on an ordinary class change
with no captured "before"); `mutated` is set whenever a partition's `delta != 0` (i.e. a real
drain/restore call ran, which already marks dirty on its own). After the loop:

```java
if (maximumChanged && !mutated) {
    ResourceSyncManager.markDirty(player.getUUID(), resourceId);
}
```

**Why this is generic, not Standard-Spell-Slot-specific:** the fix lives entirely inside the same
model-agnostic `reconcilePartitioned` method every `PARTITIONED_POOL` `GENERIC_COMPONENT` resource
already routes through (there is no `if (resourceId.equals(SPELL_SLOTS))` branch, and there must never
be one added, matching this class's own established constraint). It benefits any future partitioned
resource (e.g. Hit Dice) automatically, exactly like every other step in this class.

**Why it does not force every partitioned resource dirty on every class change:** the explicit
`markDirty` call is gated on `maximumChanged`, which is derived purely from a real, per-partition
`previousMaximumUnits != newMax` comparison — a class change that captures no real "before" (every
call site except `AddClassLevelHandler`) or that genuinely changes nothing for a given resource never
sets it, so the fallback is a true no-op in the overwhelming majority of calls, matching the task's
explicit "only mark dirty when something relevant actually changed" constraint.

### 23.3 Finding 2 — stale Javadoc reference — **CONFIRMED, CORRECTED**

`ClassChangeReconciler`'s class-level Javadoc (`@link #clampResourcesAboveResolvedMaximum`) still
referenced the method §22's fix removed and replaced with `reconcileResourcesAgainstResolvedMaximum`.
Confirmed by a direct grep of `src/main/java` — this was the only remaining reference to the old method
name anywhere in the codebase. Corrected the `@link` and surrounding prose to describe the current
method and its actual (bidirectional, policy-driven) behavior; no other documentation was rewritten.

### 23.4 Regression coverage added

- **Pure formula** (`MaximumChangePolicyTest`, +2): the task's own literal `CLAMP_CURRENT` example
  (old max 4, current 2, new max 5 → reconciled stays 2) and a `PRESERVE_DEFICIT` floor-clamp example
  (an already-empty partition whose maximum shrinks further stays at 0) — both prove the formula-level
  precondition (`previousMaximumUnits != newMaximumUnits` with `delta == 0`) that makes the sync edge
  reachable at all. The pre-existing `PRESERVE_DEFICIT` examples (old max=3→4, current 1→2; old max=0→1,
  current 0→1) were re-run unchanged and still pass, confirming this cleanup pass did not disturb them.
- **Source-regression sentinel** (`ClassChangeReconcilerMaximumSyncSourceRegressionTest`, +2, same
  established precedent as `BarbarianRageAbilityMaximumSyncSourceRegressionTest` for the same
  untestable-private-static-dirty-state constraint): proves `reconcilePartitioned`'s body contains the
  exact `if (maximumChanged && !mutated) { ResourceSyncManager.markDirty(player.getUUID(),
  resourceId); }` guard, and that `maximumChanged`/`previousMaximumUnits`'s degenerate default are
  derived correctly rather than set unconditionally.
- **Real production path** (`StandardSpellSlotMigrationVerification`, +2 checks, a new dedicated
  `maximumOnlyChange` player): engineered so that forcing a level 7 → 6 decrease changes only tier 4's
  maximum (1 → 0, `SpellSlotTable.FULL_CASTER` rows 6 and 7 are identical in every other column) while
  every partition's reconciled delta is exactly zero (tier 4 was pre-spent to `0/1`, so it floor-clamps
  to `0/0`) — proving the reconciled STATE itself comes out correct (not negative, not resurrected,
  every other tier untouched, wire-safe) in precisely the scenario that exercises the new fallback.
  True packet-delivery proof is out of this harness's reach (§22.5's own documented limitation); the
  source sentinel above proves the code path that would deliver it is actually taken.

### 23.5 Standard Spell Slot behavior — unchanged

No change to: `GENERIC_COMPONENT` authority, tiers 1–9 only, no tier 10, the Wizard 12→13 immediate-grant
behavior (§22, now manually confirmed live — §23.1), `PRESERVE_DEFICIT` semantics (re-verified by the
unchanged pre-existing formula tests plus the two new ones), `markNoEffect()` spending nothing,
successful-cast-only spending, Long/Short Rest behavior, Warlock exclusion, Pact Magic remaining Phase
7, JOIN class-level normalization, or Rage (confirmed unchanged by the still-passing, unchanged
`BarbarianRageMigrationVerification`, 18/18).

### 23.6 Files changed (this cleanup pass)

**Modified (production):**
- `src/main/java/zcylas/totality/api/rpg/classes/ClassChangeReconciler.java` — the maximum-only sync
  fallback in `reconcilePartitioned`; the stale `clampResourcesAboveResolvedMaximum` Javadoc reference
  corrected; new `ResourceSyncManager` import.

**Modified (test):**
- `src/test/java/zcylas/totality/api/rpg/resources/MaximumChangePolicyTest.java` (+2 tests)
- `src/test/java/zcylas/totality/api/rpg/classes/ClassChangeReconcilerMaximumSyncSourceRegressionTest.java` (+2 tests)
- `src/main/java/zcylas/totality/api/rpg/resources/verification/StandardSpellSlotMigrationVerification.java`
  (+2 checks, new `maximumOnlyChange` player block)

**Updated total Phase 6 changed-file set:** unchanged at 34 files — this pass touched only files
already in that set (no new file was created).

### 23.7 Validation (this cleanup pass)

| Check | Result |
|---|---|
| Focused tests (`MaximumChangePolicyTest`, `ClassChangeReconcilerMaximumSyncSourceRegressionTest`) | **PASS** |
| `./gradlew test` (full suite) | **PASS** — 1611 tests, 0 failures, 0 errors (was 1607 before this pass — +4) |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` | **PASS** — no real whitespace errors (only benign CRLF-normalization warnings) |
| Bounded dedicated-server run (`./gradlew runServer`) | **PASS** — `[ResourceFoundationVerification] All 6`, `[BaselineResourceMigrationVerification] All 11`, `[BarbarianRageMigrationVerification] All 18` (all unchanged — Rage unaffected), **`[StandardSpellSlotMigrationVerification] All 23 self-test checks passed.`** (was 21, +2 maximum-only-sync-edge checks). The same two unrelated, pre-existing failures (`ProvisionerEntityBackedSmokeTest` 3/4 FAIL, `OffhandAttackVerification` 3/5 FAIL) appeared again, identically. |

### 23.8 Remaining Phase 6 concerns

- True end-to-end network-packet delivery is still not exercised by the automated test harness — but
  is no longer a purely theoretical gap: the Wizard 12→13 case (which *does* take the ordinary,
  nonzero-delta `mutated` path, not the new maximum-only fallback) has now been manually confirmed live
  (§23.1). The maximum-only fallback path itself (max changes, current does not) has not been
  separately manually reproduced in gameplay — it requires a specific decrease shape (e.g. a
  fully-spent tier whose maximum shrinks further) that is less commonly hit than an ordinary level-up;
  flagged here rather than claimed as manually verified.
- No other stale references to retired method names were found in `src/main/java` (checked by grep
  across the whole source tree, not just this file).

### 23.9 Confirmations

No commit was created (`git rev-parse HEAD` still `7bd5d76607d91f00828d09b89db737f6585c4bef`). Phase 7
(Pact Magic, Ki, Hit Dice, Epic Magic) was not started. Crown of Stars was not touched. The Spell
Radial's design, Class/Character UI, Food, the Minecraft 26.3 migration, the pre-existing
`ProvisionerEntityBackedSmokeTest`/`OffhandAttackVerification` failures, and the previously-corrupted
old test world were not touched by this cleanup pass either.
