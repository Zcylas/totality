# Class Tab Quick Level-Up ("+") Button — Implementation Report

**Date:** 2026-09-16
**Branch:** `feature/general-resource-api`
**Baseline commit:** `ab398b17dcce74a589d75dbb6946d4fc62612125` ("feat(resources): migrate rage to generic resource authority") — confirmed via `git rev-parse HEAD` before any edit, and unchanged throughout both this task and its continuation (nothing committed).

**This report covers two passes over the same still-uncommitted work:** the original "+" button implementation (§1-§12 below, largely as originally written), and a continuation pass (§13) that migrated subclass storage from one global slot to a per-class map after a design review found the global model could not support multiclass characters. Both passes sit on top of the same baseline commit above.

## 1. Baseline / pre-existing dirty files touched

`git status --short` at task start showed **109 dirty paths**. Two are directly on the class-progression files this task needed to read and edit:

1. `src/main/java/zcylas/totality/api/rpg/classes/ClassLevelUpRegistry.java` — already carried an uncommitted fix (before this task) making `fire()` source `classLevel` from `PlayerClassComponent#getClassLevel(Identifier)` instead of the buggy player-global `toClassLevel(playerLevel)`. **Not touched by either pass of this task** — verified byte-identical to its pre-task diff after every edit in both passes.
2. `src/main/java/zcylas/totality/api/rpg/classes/PlayerClassComponent.java` — already carried an uncommitted fix to the `toClassLevel`/`getAvailableClassPoints` formula (off-by-one starting-level bug). **Not touched by the original "+" button pass.** The continuation pass (§13) *does* touch this file — it is the authoritative subclass-storage owner and could not be avoided — but only in regions disjoint from the pre-existing fix (which lives entirely inside `toClassLevel`/`getClassLevel(int)`/`getAvailableClassPoints`, lines ~40-71/104-131 of the original file). The pre-existing hunk was captured verbatim before the continuation pass touched anything and reconfirmed byte-identical afterward — see §13's own subsection on this file for the exact hunk-level breakdown, prepared for partial staging at commit time exactly as the Rage Phase 5 milestone commit already did for a different file.

Two new, pre-existing, untracked test files (`ClassLevelUpRegistryMulticlassTest.java`, `PlayerClassComponentClassLevelProgressionTest.java`) already existed proving the two fixes above — also untouched by both passes (neither references subclass state at all).

## 2. Files created / modified

**New (original "+" button pass):**
- `src/main/java/zcylas/totality/networking/classes/SelectSubclassPayload.java` — client→server payload for applying a subclass to an already-owned class.
- `src/main/java/zcylas/totality/networking/classes/SelectSubclassHandler.java` — server-authoritative handler (the bug fix — see §3). Further modified in the continuation pass (§13.5).
- `src/test/java/zcylas/totality/networking/classes/SelectSubclassHandlerTest.java` — executing unit tests for the new handler's pure validation core. Rewritten for the per-class API in the continuation pass (§13.9).
- `src/test/java/zcylas/totality/screen/classes/ConfirmClassScreenSubclassRoutingSourceRegressionTest.java` — source-text sentinel for the `ConfirmClassScreen` routing fix.
- `src/test/java/zcylas/totality/screen/character/tabs/ClassTabQuickLevelUpSourceRegressionTest.java` — source-text sentinel for the new "+" button.

**Modified (original "+" button pass):**
- `src/main/java/zcylas/totality/Totality.java` — registers `SelectSubclassHandler.register()` (1 line).
- `src/main/java/zcylas/totality/networking/TotalityPackets.java` — registers `SelectSubclassPayload` on the serverbound channel (1 import + 1 line).
- `src/main/java/zcylas/totality/screen/classes/ConfirmClassScreen.java` — CONFIRM branch now routes to the new payload for an already-owned class's subclass choice (see §3).
- `src/main/java/zcylas/totality/screen/character/tabs/ClassTab.java` — the new "+" button (see §5-§8). Further modified in the continuation pass (§13.6) to display each class's own subclass.

**New/modified in the continuation pass (per-class subclass migration) — see §13 for full detail:**
- Modified: `PlayerClassComponent.java` (partial overlap with a pre-existing diff, see §1), `ClientClassManager.java`, `BaseCharacterScreen.java`, `screen/character/tabs/OverviewTab.java`, `barbarian/BarbarianClass.java`, `monk/MonkClass.java`, `wizard/WizardClass.java`, `networking/classes/SelectClassHandler.java`, `networking/classes/SelectSubclassHandler.java`, `screen/character/tabs/ClassTab.java`, `test/.../client/resource/parity/LegacyClientResourceParityReadersTest.java` (API-signature-change fix).
- New tests: `test/.../api/rpg/classes/PlayerClassComponentSubclassMigrationTest.java`, `test/.../api/rpg/classes/ClassSubclassMilestoneNormalizationTest.java`.

## 3. Pre-existing bug discovered (and fixed)

**The subclass-selection confirmation path was broken for every class whose subclass unlocks after level 1** (at the time of discovery: Wizard at class level 2 — since normalized to level 3, see §13.4; Barbarian/Monk at class level 3) — discovered during the mandatory audit of the existing class-level-up flow this task required before writing any Class Tab code.

Trace of the broken path: `AddClassLevelHandler` levels a class and fires `ClassLevelUpRegistry`, whose per-class handler (`WizardClass`/`BarbarianClass`/`MonkClass`) checks `classLevel == subclassUnlockClassLevel && !hasSubclass()` and sends `OpenSubclassSelectionPayload`, forcing `SubclassSelectionScreen` open. The player picks a subclass and proceeds to `ConfirmClassScreen(cls, selSub, null)`. On CONFIRM, `ClassScreenMode.IS_MULTICLASSING` — a one-shot flag already consumed and reset by the *earlier* `AddClassLevelPayload` send that produced this milestone — is `false`, so the screen fell into its `else` branch and sent `SelectClassPayload(classId, subclassId, null)`. `SelectClassHandler.handle` rejects this outright (`if (comp.hasAnyClass()) { warn; return; }`), since the player already owns the class. **The subclass choice was silently discarded** — the player was left at the new class level with no subclass, with only a server-log warning, no player-visible error.

This affects the *existing* "SPEND CLASS POINT → Class Screen" flow exactly as much as the new "+" button would have, and is exactly the class of problem this task's own stop conditions call out ("ordinary class progression currently contains a bug that must be fixed before the shortcut can use it"). Per the user's explicit direction, this was fixed as approved scope expansion rather than routed around.

**Fix:** a new server-authoritative path, `SelectSubclassPayload` / `SelectSubclassHandler`, is the missing "apply a subclass to an already-owned class" counterpart to `SelectClassHandler`'s first-time-only path. It reuses `PlayerClassComponent#selectSubclass` — the exact same method the first-time path already calls — with no new subclass state and no duplicated validation framework. `ConfirmClassScreen`'s CONFIRM branch now checks whether the class is already owned (via the already-synced `ClientClassManager` mirror, not the exhausted `IS_MULTICLASSING` flag) and, only when a subclass was chosen for an already-owned class, sends the new payload instead of `SelectClassPayload`. The two pre-existing branches (multiclass-new-class → `AddClassLevelPayload`; first-time selection → `SelectClassPayload`) are unchanged and still reachable exactly as before.

### Server-side validation (`SelectSubclassHandler.apply`, package-visible, pure)

1. The class exists (`ClassRegistry.get(classId)`).
2. The subclass exists **and** its `parentClassId()` equals the requested class — rejects a subclass/class mismatch.
3. The player owns the target class (`comp.hasClass(classId)`).
4. The player's stored level for that class has reached (`>=`, not `==`, so a player who somehow leveled past the milestone can still catch up) its `subclassUnlockClassLevel`.
5. The player does not already have a subclass — the current design treats a subclass choice as permanent (`ConfirmClassScreen`'s own "⚠ This choice is permanent" warning; no replacement path exists anywhere in the codebase), so this handler does not invent one.

Any failure is a silent no-op (matching `AddClassLevelHandler`/`SelectClassHandler`'s own established style) — no partial state change on rejection.

**Known limitation, out of scope:** no currently-registered subclass with `subclassUnlockClassLevel > 1` has any `availableCovenantCategories()` (Berserker/Wild Heart, School of Destruction/Necromancy, Open Hand/Shadow are all empty), so this fix does not handle a covenant-selection step reached via this path. If a future class needs both a late subclass unlock *and* covenant selection, that is a new, separate piece of work.

## 4. Existing level-up flow reused (as required — nothing duplicated)

- **Ordinary levels:** the "+" sends the exact same `AddClassLevelPayload` the Class Screen's own "SPEND CLASS POINT" flow already sends for an already-owned class. `AddClassLevelHandler` performs all existing validation (class exists, unspent points available) and fires `ClassLevelUpRegistry.fire`, which triggers every class's existing level-up hooks unchanged (Barbarian's Rage-maximum update, `ClassFeatureRegistry`, `SpellSlotRecalculator`, etc.) — nothing about this dispatch path was touched.
- **Subclass milestone:** unchanged — the same `ClassLevelUpRegistry`-driven `OpenSubclassSelectionPayload` push, the same `SubclassSelectionScreen`, now correctly landing in the repaired `SelectSubclassHandler` path (§3) instead of being silently dropped.

## 5. Server authority

The client never mutates class-level state directly. `ClassTab`'s new button only ever sends `AddClassLevelPayload` — verified by source-regression sentinel (`ClassTabQuickLevelUpSourceRegressionTest`) that the file never references `PlayerClassComponent`'s mutation methods, `ClassComponents.get(...)`, or `SelectSubclassPayload` directly.

## 6. Button state / cap handling

There is no per-class maximum field in `ClassData` — the only existing restriction on continuing to level *any* specific class is the player's shared pool of unspent class points (`PlayerClassComponent.getAvailableClassPoints`/`getSpentClassPoints`, itself capped at 30 total). The "+" is therefore enabled exactly when `unspentPoints > 0` — the identical condition that already gates the existing "SPEND CLASS POINT" button — and disabled (still visible, but drawn dimmed and non-interactive) otherwise. No new cap concept was invented. No tooltip was added: the character-screen framework has no existing tooltip mechanism (grepped for one before starting), and the task explicitly says not to build one solely for this.

## 7. UI placement

- **Single-class characters:** a small 13×13 "+" box directly below the existing big class-level number, centered.
- **Multiclass characters:** a "+" box at the right edge of each existing `"ClassName  Lv. N"` row in the already-existing per-class list (previously plain text with no controls at all).

No other layout was changed. The existing "SPEND CLASS POINT" button (used for starting a brand-new class via multiclassing, which per-class "+" buttons cannot do) is untouched and coexists with the new buttons.

## 8. Downstream `ClassLevelUpRegistry` behavior

Untouched and fully exercised: `AddClassLevelHandler.handle` (unmodified) calls `ClassLevelUpRegistry.fire(player, classId, playerLevel)` exactly as it always has for the "SPEND CLASS POINT" flow. Barbarian's Rage-maximum-on-level-up (Phase 5), Monk's and Wizard's subclass-milestone dispatch, and `ClassFeatureRegistry`/`SpellSlotRecalculator` all fire identically regardless of whether the request originated from the full Class Screen or the new "+" button — verified by construction (the button sends the same payload type, not a new one) and confirmed via the bounded dedicated-server run (§10).

## 9. Per-class subclass storage migration (continuation pass)

A design review after the original "+" button pass found the subclass storage model itself was wrong for multiclassing: `PlayerClassComponent` stored exactly one global `subclassId` for the whole player. A Barbarian subclass choice made `hasSubclass()` true for the *entire character*, which would have incorrectly blocked ever choosing a Wizard subclass on the same multiclass character. This section covers the migration to `Map<classId, subclassId>` storage.

### 9.1 Consumer audit (performed before touching storage)

Grepped the full repository for `subclassId`, `getSubclassId`, `hasSubclass`, `selectSubclass`, `SubclassRegistry`, `subclassUnlockClassLevel`, and `getSubclassData` before changing anything. Complete list of production consumers found and migrated:

| File | Old usage | New usage |
|---|---|---|
| `PlayerClassComponent.java` | `@Nullable Identifier subclassId` field; parameterless `getSubclassId()`/`hasSubclass()`/`selectSubclass(id)` | `Map<Identifier, Identifier> subclassIds`; `getSubclassId(classId)`/`hasSubclass(classId)`/`selectSubclass(classId, id)`/`getAllSubclassIds()` |
| `ClientClassManager.java` | Same shape, client-side mirror | Same migration, client-side mirror; `apply(...)` now takes a `Map<Identifier, Identifier>` instead of a single `@Nullable Identifier` |
| `BarbarianClass.java`, `MonkClass.java`, `WizardClass.java` (`ClassLevelUpRegistry` handlers) | `!ClassComponents.get(player).hasSubclass()` | `!ClassComponents.get(player).hasSubclass(TotalityClasses.<OWN_CLASS>_ID)` — each class checks only its own subclass status |
| `SelectClassHandler.java` | `comp.selectSubclass(subclassId)` | `comp.selectSubclass(classId, subclassId)` |
| `SelectSubclassHandler.java` | `comp.hasSubclass()` / `comp.selectSubclass(subclassId)` | `comp.hasSubclass(classId)` / `comp.selectSubclass(classId, subclassId)` — the exact fix requirement 7 of this task named directly |
| `ClassTab.java` | `ClientClassManager.getSubclassData()` (identity panel) | `ClientClassManager.getSubclassData(classData.id())`; the multiclass row list (added in the original pass) now also shows each class's own subclass name underneath its level line |
| `BaseCharacterScreen.java`, `OverviewTab.java` | `ClientClassManager.getSubclassData()` (summary/overview display of the primary class) | `ClientClassManager.getSubclassData(primaryClassId)` — both displays are explicitly about the *primary* class, so this is the correct one-argument migration, not a design change |
| `ClassSelectionScreen.java`, `SubclassSelectionScreen.java`, `ConfirmClassScreen.java` | Reference `SubclassRegistry`/`subclassUnlockClassLevel` but never call the mutated getters/setters directly | **No change needed** |
| `SelectSubclassHandlerTest.java` (test) | Old-API assertions | Rewritten for the per-class API, plus new multiclass-specific scenarios (§10) |
| `LegacyClientResourceParityReadersTest.java` (test) | `ClientClassManager.apply(Map.of(), null, null)` | `ClientClassManager.apply(Map.of(), Map.of(), null)` — a required fix once `apply`'s second parameter became a `Map`; caught by the full test suite (3 call sites, all fixed) |

No consumer was left calling a parameterless subclass method — the old `hasSubclass()`/`getSubclassId()`/`selectSubclass(Identifier)` overloads were removed entirely rather than kept as deprecated compatibility shims, per the task's explicit preference ("prefer removing them entirely if the repository can be safely migrated without expanding scope excessively"). The full consumer set was small and closed (9 production files), so full migration was the correct choice over ambiguous parallel APIs.

### 9.2 Authoritative per-class API (`PlayerClassComponent`)

```java
public @Nullable Identifier getSubclassId(Identifier classId)
public boolean hasSubclass(Identifier classId)
public void selectSubclass(Identifier classId, Identifier subclassId)
public Map<Identifier, Identifier> getAllSubclassIds()
```

`PlayerClassComponent` remains the sole authoritative owner — no new state-holding class was introduced. `SelectSubclassHandler.apply`'s validation is unchanged in shape, only re-scoped to the target class: class exists, subclass exists and belongs to that class, player owns the class, class level has reached the milestone, and — the one check requirement 7 specifically calls out — **the target class itself** does not already have a subclass (`comp.hasSubclass(classId)`, not a global check). A subclass already chosen for a *different* class never appears in this check at all.

`covenantId` (Warlock's Patron) was **not** touched — it remains a single global field, unaffected by this migration (see §9.7).

### 9.3 Legacy save migration

Old NBT format: one `"SubclassId"` string key (`"none"` sentinel for absent), with no record of which class it belonged to. New format: `"SubclassCount"` + per-entry `"SubclassClassId_i"`/`"SubclassSubclassId_i"` pairs, mirroring the existing `"ClassCount"`/`"ClassId_i"`/`"ClassLvl_i"` convention already used for class levels in the same file.

`readData` distinguishes the two formats by reading `SubclassCount` with a `-1` sentinel default (a real count is never negative): if present (`>= 0`), the save already uses the new per-class format — including the legitimate case of a save with zero subclasses chosen (`SubclassCount == 0`), which must not be confused with "predates this format entirely." If absent, the save predates this migration; the old `"SubclassId"` value (if not `"none"`) is inferred onto its real owning class via `SubclassRegistry.get(id).parentClassId()` — the exact same authoritative class/subclass relationship `SelectSubclassHandler` and every other subclass consumer already relies on. An unresolvable (unknown/malformed) legacy id migrates nothing rather than guessing, and never throws.

`writeData` always emits the new format and **never writes the old `"SubclassId"` key again** — so once a character is saved once after this change, every subsequent load takes the `SubclassCount >= 0` branch permanently; the legacy branch is a pure, stateless re-derivation that only ever runs for a save that has never been written under the new format, making it naturally idempotent (a load that is never followed by a save can be repeated indefinitely with identical results) and impossible to double-migrate or corrupt. Verified directly by `PlayerClassComponentSubclassMigrationTest` (§10).

### 9.4 Wizard subclass milestone normalized to class level 3

Per explicit direction, Wizard's `ClassData.subclassUnlockClassLevel()` moved from `2` (stale) to `3`, matching Barbarian and Monk (D&D 2024-style normalization). The corresponding `ClassLevelUpRegistry` handler in each of the three classes was also changed from a re-typed literal (`classLevel == 2` / `classLevel == 3`) to `classLevel == DATA.subclassUnlockClassLevel()` — reading the single source of truth instead of a second, independently-maintained copy of the same number. This directly prevents a recurrence of the exact drift that made Wizard's literal go stale in the first place, and was a minimal, localized change to lines already being edited for the per-class `hasSubclass` migration. `ClassTab`'s "+" button remains completely generic and contains no milestone-level literal of any kind, for any class — it only ever calls `AddClassLevelPayload`.

### 9.5 Client sync / mirror

`PlayerClassComponent.writeSyncPacket`/`applySyncPacket` now serialize the whole `subclassIds` map (`int count` + repeated `classId`/`subclassId` UTF pairs) instead of one optional identifier, mirroring the existing `classLevels` map's own wire format. `ClientClassManager.apply` takes the received map directly. The client can now ask "what is the subclass for Wizard?" and "what is the subclass for Barbarian?" independently and correctly — verified by `PlayerClassComponentSubclassMigrationTest#syncPacketRoundTripsMultipleClassSubclassMappings`.

`ClassTab`'s multiclass row list (added in the original "+" button pass) now shows each class's own subclass name on a second line beneath its level, e.g.:

```
Barbarian  Lv. 3          [+]
Berserker

Wizard  Lv. 3             [+]
School of Necromancy
```

Never one class's subclass rendered under a different class's row — each row explicitly queries `ClientClassManager.getSubclassData(entry.getKey())` for that row's own class id.

### 9.6 `SelectSubclassHandler` update

Updated exactly as requirement 7 specified: the rejection check moved from `comp.hasSubclass()` (global) to `comp.hasSubclass(classId)` (the target class only). Example from the task now behaves correctly: Barbarian already has Berserker, Wizard has none — selecting Necromancy for Wizard **succeeds**; selecting Wild Heart for Barbarian (which already has a subclass) still correctly **fails**. Every other validation step (class exists, subclass exists, subclass belongs to class, player owns class, milestone reached, no partial mutation on failure) is unchanged in substance, only re-scoped to the target class where relevant.

### 9.7 Warlock — provisional exception preserved, no architecture change

Audited `WarlockClass.java` directly before assuming anything, per the task's explicit "STOP and report before guessing" instruction. Confirmed: Warlock's `subclassId` usage (`THE_HEXBLADE`/`THE_ARCHFEY`, `subclassUnlockClassLevel = 1`) is genuine subclass data — chosen once, at initial class selection, via the existing `SelectClassHandler`/`SelectClassPayload` path, exactly like Warlock/Cleric/Sorcerer-style classes already do. Covenant/Patron (`covenantId`, `CovenantRegistry`, `Artagan`/`Uk'otoa`) is a **completely separate field and registry**, never conflated with subclass storage — Warlock's Patron was never secretly stored in the subclass slot. No stop condition was triggered; no architecture question needed to be raised.

Consequently, Warlock required only the same mechanical migration every other class needed (its subclass now lives in the per-class map like everyone else's), with **zero** behavioral, architectural, or design changes: no `ClassLevelUpRegistry` handler exists for Warlock at all ("No level-up handler yet" — unchanged, still absent), so there is no leveling-triggered subclass milestone to worry about for Warlock in this task. Nothing about Pact Boons, Pact Magic, spell slots, or Patron/Covenant design was touched, added, or redesigned.

### 9.8 `PlayerClassComponent.java`'s pre-existing-diff overlap (for commit-time partial staging)

This file already carried an unrelated, pre-existing, uncommitted fix (the `toClassLevel`/`getAvailableClassPoints` off-by-one bug, §1) before this continuation pass began. That pre-existing hunk lives entirely within `toClassLevel(int)`/`getClassLevel(int)`/`getAvailableClassPoints(int)` (originally lines ~40-71 and ~104-107); this pass's subclass-migration edits live in the field declaration, the "Subclass & Covenant" section, `selectClass`/`selectSubclass`, `writeSyncPacket`/`applySyncPacket`, `writeData`/`readData`, and `copyFrom`/`resetClass` — a disjoint set of regions. `git diff` on the current working tree produces 9 separate hunks for this file; hunks 2 and 4 are exactly the pre-existing fix (confirmed byte-identical to the diff captured before this pass began), and every other hunk is this pass's own subclass-migration work. This is captured in the review ZIP's `PlayerClassComponent.pre-existing.diff` (from before this pass) alongside the file's current full diff, so a future commit can stage only the subclass-migration hunks exactly the way the Rage Phase 5 milestone commit partially staged a different file with the same kind of overlap.

## 10. Tests

**Original "+" button pass:**
- **`SelectSubclassHandlerTest`** (GUI-independent executing tests, since rewritten for the per-class API — see below): milestone-reached selection persists; wrong-class/subclass pairing rejected; unknown class rejected; unknown subclass rejected; unowned class rejected; below-milestone level rejected; above-milestone level still allowed; existing subclass on the *same* class cannot be replaced; rejection never partially mutates state.
- **`ConfirmClassScreenSubclassRoutingSourceRegressionTest`** (4 source-text sentinels — GUI class, no bootstrapped `Minecraft`/`Font` available under plain JUnit, matching this codebase's established `Phase3CConsumerMigrationSourceRegressionTest` precedent): the already-owned-class branch exists and sends `SelectSubclassPayload`; the multiclass-new-class branch is preserved and still sends `AddClassLevelPayload`; the first-time-selection branch is preserved and still sends `SelectClassPayload`; the new branch is checked before the other two.
- **`ClassTabQuickLevelUpSourceRegressionTest`** (5 source-text sentinels — same GUI-testability constraint): the button sends `AddClassLevelPayload` and respects `enabled`; the file never calls any class-state mutation method directly and never sends `SelectSubclassPayload` itself; no per-class (Wizard/Barbarian/...) branching exists; enablement is tied to the shared unspent-points pool; buttons are only ever created at the two legitimate call sites, structurally scoped to `ClientClassManager.getClassLevels()` entries only — an unowned class can never receive a button.

**Continuation pass (per-class subclass migration) — new/updated:**
- **`SelectSubclassHandlerTest`** (12 executing tests total; rewritten for the per-class API, +3 new): all prior coverage re-expressed with `hasSubclass(classId)`/`getSubclassId(classId)`, plus: two different owned classes can each hold a different subclass simultaneously; selecting a subclass for one class does not make another owned class report `hasSubclass == true`; a subclass already chosen for one class does not block choosing a subclass for a *different* owned class (the exact Barbarian-then-Wizard scenario from the task).
- **`PlayerClassComponentSubclassMigrationTest`** (13 executing tests, new): storage/query API (one class selects a subclass; two owned classes hold independent subclasses; selecting for one never affects another; `selectClass` wipes all subclass entries on a fresh first-time selection; `resetClass` clears all; `copyFrom` preserves per-class subclasses independently); NBT persistence (new-format round-trip with multiple class/subclass mappings; `SubclassCount == 0` is not confused with "predates this format"); legacy migration (a legacy single-subclass save migrates to the correct class-keyed entry via `SubclassRegistry`; an unknown/malformed legacy id fails safely with no crash and no entry; a legacy save with no subclass at all migrates to an empty map; a save already re-written under the new format is never re-migrated and never duplicates on repeated loads); sync-packet round-trip for multiple class/subclass mappings.
- **`ClassSubclassMilestoneNormalizationTest`** (5 tests, new): executing assertions that `WizardClass.DATA.subclassUnlockClassLevel() == 3` (was 2) and that Barbarian/Monk remain at 3; source-text sentinels confirming each of the three classes' `ClassLevelUpRegistry` handler compares against `DATA.subclassUnlockClassLevel()` (not a re-typed literal) and checks `hasSubclass(TotalityClasses.<OWN_CLASS>_ID)` (its own class only, never another's).
- **`LegacyClientResourceParityReadersTest`** (pre-existing file, fixed): 3 call sites updated from `ClientClassManager.apply(Map.of(), null, null)` to `ClientClassManager.apply(Map.of(), Map.of(), null)` — a required consequence of `apply`'s second parameter changing from `@Nullable Identifier` to `Map<Identifier, Identifier>`; caught immediately by the full test suite (`NullPointerException` on `Map.putAll(null)`), not discovered manually.
- **Untouched and still passing:** all pre-existing `ClassLevelUpRegistryMulticlassTest`/`PlayerClassComponentClassLevelProgressionTest` methods (proving the two unrelated pre-existing fixes independently of this task).

## 11. Validation results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **PASS** |
| `./gradlew compileTestJava` | **PASS** |
| Focused class-progression/subclass/ClassTab/Rage test classes | **PASS** (all green) |
| `./gradlew test` (full suite, after both passes) | **PASS** — 1578 tests, 0 failures, 0 errors |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` (all files touched by this task) | **PASS** — no whitespace errors (only benign CRLF-normalization warnings) |
| Bounded dedicated-server startup (`./gradlew runServer`, ~100s, re-run after the continuation pass) | **PASS** — reached `Done (0.345s)!`; every Totality self-test suite passed (`ResourceFoundationVerification` 6/6, `BaselineResourceMigrationVerification` 11/11, `BarbarianRageMigrationVerification` 13/13, plus the unrelated Provisioner/PowerAttack suites), confirming the storage-format change did not break mod init or the Rage-integrated class-level-up path. The only failure observed (`OffhandAttackVerification`) is the same pre-existing, unrelated failure documented in every prior phase's report. |

## 12. Manual testing required (Stefan)

**A. Fresh Wizard**
1. Wizard 1 → 2 with "+" — no subclass screen (milestone is now level 3).
2. Wizard 2 → 3 with "+" — subclass screen opens.
3. Select a subclass, confirm it persists (Class Tab shows the correct subclass name).

**B. Fresh Barbarian**
4. Reach Barbarian 3 via "+", choose a subclass, verify it persists.

**C. Multiclass — the scenario this migration exists for**
5. A character has Barbarian 3 + a chosen subclass. Add/level a Wizard on the same character.
6. Reach Wizard 3 — verify the Wizard subclass screen still opens (the Barbarian subclass must not suppress it).
7. Choose a Wizard subclass — verify **both** subclasses (Barbarian's and Wizard's) remain correct afterward, each shown under its own class row.

**D. Reload**
8. Disconnect and reconnect — verify both class/subclass pairs are still correct.

**E. Dimension**
9. Change dimension — verify both class/subclass pairs remain correct.

**F. Existing Class Screen route**
10. Use the original "SPEND CLASS POINT" → Class Screen path (not the new "+" button) to level a class into its subclass milestone — verify subclass selection persists there too (confirms the underlying fix, not just the shortcut).

**G. "+" button regressions**
11. Verify ordinary class levels (no milestone involved) still advance normally with no extra screens.
12. Verify "+" becomes disabled once all class points are spent.
13. Level a Barbarian across a Rage-maximum threshold via "+" — verify the Rage HUD/Class-tab resource maximum still updates correctly (Phase 5 behavior, unchanged, exercised through the button).
14. On a multiclass character, verify each class's own "+" only levels that specific class.

## 13. Future confirmation-popup note

Not implemented, per the task's explicit instruction. The "+" click handler is a single, isolated block in `ClassTab.mouseClicked` that only ever sends `AddClassLevelPayload` for an enabled button; a future confirmation dialog can be inserted between the click and that send without any further restructuring of the tab.

---

**Existing subclass progression bug:** FIXED
**Class Tab "+" shortcut:** COMPLETE
**Subclass milestone through "+":** WORKING
**First-time class selection regression:** PASS
**Existing-class subclass selection regression:** PASS
**Safe for manual testing:** YES

## 14. Continuation pass — final status

**Per-class subclass storage:** COMPLETE
**Legacy subclass migration:** PASS
**Wizard subclass now level 3:** YES
**Barbarian subclass level 3 preserved:** YES
**Monk subclass level 3 preserved:** YES
**Warlock provisional behavior preserved:** YES
**Barbarian + Wizard simultaneous subclasses:** PASS
**Class Tab "+" regression:** PASS
**Existing Class Screen regression:** PASS
**Full tests:** 1578/1578
**Safe for manual testing:** YES
**Safe to commit:** NO — wait for Stefan manual test/review
**Phase 6 started:** NO

## 15. Scrolling follow-up (separate task, same overall effort)

The right-side/multiclass owned-class list did not scroll, so a character with more owned classes than fit in the visible box could not reach the lower rows or their "+" buttons. Fixed as a small, separate follow-up task (see `TOTALITY_CLASS_TAB_MULTICLASS_SCROLL_FIX_IMPLEMENTATION_2026-09-16.md` for full detail) by mirroring the LEFT column's existing `progScroll` pattern: a new `mcListScroll` offset, a viewport scoped to just the list (the CLASS LEVEL header / SPEND CLASS POINT button above it keep their own unscrolled clip), two-sided clamping, and an off-viewport click-exclusion check folded into each button's stored `enabled` flag. No class progression, subclass storage, networking, or resource code was touched by that follow-up; its own report and review ZIP (`Context/Audit/Review/TOTALITY_CLASS_TAB_MULTICLASS_SCROLL_FIX_REVIEW_2026-09-16.zip`) are committed alongside this one as part of the same overall class-system effort.

## 16. Manual validation — COMPLETE (PASS)

All of the following have now been manually verified in-game by Stefan and are recorded here as **PASS**:

- Wizard class leveling through the Class Tab "+" control.
- Wizard subclass selection at the correct class milestone (class level 3).
- Multiclassing on a clean world (Barbarian added as a second class).
- Per-class subclass state working independently — a Barbarian subclass choice does not overwrite or block a Wizard subclass choice on the same character.
- The right-side owned-class/multiclass list scrolling (the §15 follow-up), including previously-hidden classes becoming reachable and their "+" controls moving/working correctly with the scroll.
- The "+" control remaining visible but non-highlighted/disabled when no Class Levels are available to spend.
- Classes and subclasses persisting correctly through a dimension change.

**Manual validation: PASS.**

### Separately discovered bug — explicitly NOT fixed here, deferred to a future task

During manual testing, an **old** pre-existing test-world character (previously Barbarian around level 18 with ~6 Rage) crashed the integrated server after: `/totality resetall` → `showclass` → rebuilding as Wizard → later multiclassing back into a low-level Barbarian. The crash occurred in `ResourceScalarWireSnapshot` ("currentUnits must not exceed maximumUnits + overflowUnits"), consistent with stale class-owned Generic Resource state (old Rage current = 6) surviving the reset/class-removal path while the newly-reacquired Barbarian resolves a maximum of 2.

**This is not a bug in the per-class subclass/Class Tab work completed and committed here.** A clean-world multiclass test (no prior `resetall`/`showclass` history) works correctly with no crash — confirmed above. This is a separate, pre-existing lifecycle/reset-reconciliation gap between `/totality resetall`/`showclass` and the Generic Player Resource API's class-owned resources (Rage today; Ki/Pact Magic in the future). It is **intentionally left unmodified** and deferred to a dedicated future task ("a universal class-change reconciliation task covering Rage and future class-owned resources"). No code related to `resetall`, `showclass`, Rage, or Generic Resource reconciliation was touched by this commit.
