# Phase 7A — Health Recovery Dice — Implementation Report (2026-09-16)

**This report replaces and supersedes
`TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE7_HIT_DICE_IMPLEMENTATION_REPORT_2026-09-16.md`**, which
described the same still-uncommitted Phase 7A work under the working name "Hit Dice." That file has
been deleted from the working tree (it was task-owned and never committed) so no misleading
terminology survives. This report documents the corrected implementation: the Generic Resource is
named `totality:health_recovery_dice`, and Totality's separate, unimplemented future **Hit Die API**
(a Character Creation/Progression system) now has its own canon, recorded in
`TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` §32, so the two concepts can never be confused again.

Generic Player Resource API Phase 7, item 1 of 7. Pact Magic, Ki, species resources,
Thirst/Temperature, Rest Need/Fatigue, and Sanity are explicitly NOT started.

## 1. Baseline

- Branch: `feature/general-resource-api`
- HEAD, verified with `git rev-parse HEAD` (full SHA): `7b48387a9456b8ab2a03f6ebc0ebd9985cdf1c75`
  (`fix(checks): separate ability check and saving throw bonuses`) — unchanged from the start of the
  original Phase 7A pass and unchanged by this correction pass.
- `git status --short` before this correction pass began: **121 dirty paths** (109 unrelated
  pre-existing + 12 from the original, uncommitted Phase 7A "Hit Dice" pass) — verified before any
  edit in this correction pass, matching the task's own stated expectation exactly.
- No commit exists for either the original Phase 7A pass or this correction — both remain fully
  uncommitted, as instructed.

## 2. Why the original `hit_dice` name was corrected before commit

Totality separately reserves the term **Hit Die API** for a future Character Creation / Character
Progression system that performs real, permanent class/resource growth rolls (starting-class
maximum, reroll-1, a pending/resolved roll ledger — see §14 below and canonical §32). The Generic
Resource implemented in the original Phase 7A pass — a plain, finite, spendable/restorable
`PARTITIONED_POOL` intended for future Short Rest health recovery — is a different, much simpler
concept that happened to be implemented first under the same working name, "Hit Dice." Left
uncorrected, that collision would have made `totality:hit_dice` ambiguous between "the Generic
Resource pool" and "the future permanent-progression API" the moment the latter is designed —
exactly the kind of confusion this correction pass exists to prevent, before either name becomes
canonical by being committed.

## 3. Final resource ID/name

`totality:health_recovery_dice` (`PlayerResourceIds.HEALTH_RECOVERY_DICE`). The obsolete working
name `totality:hit_dice` is **not** registered anywhere in the corrected tree — confirmed by two
new regression assertions (§16) that explicitly check its absence, one in
`PlayerResourceRegistryTest` and one in `HealthRecoveryDiceResourcesTest`, plus a dedicated
dev-server check in `HealthRecoveryDiceResourceVerification`.

## 4. Final resource model

Unchanged from the original Phase 7A implementation — this was a pure rename, not a redesign:
`GENERIC_COMPONENT` state authority, `PARTITIONED_POOL` model, `HIGH_IS_GOOD` polarity, unit scale
1, absolute minimum 0. Capabilities: `SPENDABLE`, `RESTORABLE`, `PARTITIONED_SPENDING`,
`MENU_VISIBLE`. Presentation: `ResourceDisplayType.SLOTS`, `ResourceHudRole.MENU_ONLY`. No
`.lifecycle(...)` override (default `KEEP_CURRENT` death policy). `definitionVersion(1)`.

## 5. Relationship to `ClassData.hpDie`

**`ClassData.hpDie` was NOT renamed** — it remains the class's real, canonical HP Hit Die metadata,
exactly as the task required, and it is the field the future Hit Die API will itself eventually
read for HP progression rolls. Health Recovery Dice's maximum resolver
(`HealthRecoveryDiceMaximumResolver`) also reads this same field to derive its die-size partitions
— this sharing is intentional and explicitly documented (in the resolver's own Javadoc and in
canonical §32.1) as a deliberate, related-but-distinct-systems design choice, not a naming mistake:
the same `Dice.D12` on Barbarian can mean both "a d12 partition unit in the Health Recovery Dice
pool" (this task) and, eventually, "roll/take-max a d12 for HP maximum growth" (the future Hit Die
API, not implemented).

## 6. Confirmation no migration was added, and why

**None was added.** This work has never been committed and has no legitimate persisted production
save format under either name. A repo-wide search before the original Phase 7A pass already
confirmed no legacy Hit Dice/Health Recovery Dice state or authority of any kind ever existed to
migrate from. Investigation for this correction pass found no save created against the uncommitted
`totality:hit_dice` working name either (nothing has been released, and no test/dev world data
persists it in a way that needs preserving). Per the task's explicit instruction, the resource ID
was simply corrected before it becomes canonical — no `totality:hit_dice -> totality:health_recovery_dice`
migration marker, import step, or compatibility shim was written, and none should be.

## 7. Short Rest status

**Unchanged from the original pass — still not implemented, and still not a defect.** Canonical
§25.10 assigns "Whether the current Short Rest permits spending," "Player choice of an available die
partition," "Server-side die roll," "CON modifier and other healing modifiers," and "Applying
healing to authoritative Health" to a Rest/Health integration layer that does not exist anywhere in
Totality yet. No CON-modifier-plus-dice healing formula precedent exists anywhere in the current
codebase (re-confirmed during this correction pass; unchanged from the original investigation). What
remains implemented and proven: the generic spend primitive itself
(`PlayerResourceService.trySpend` against a selected partition — exact decrement, empty/wrong
partition rejected, no cross-partition fallback, no partial-overspend fulfillment). There is no
`onShortRest` listener — firing a real Short Rest event leaves Health Recovery Dice completely
untouched (dev-server check 8).

## 8. Long Rest status

**Unchanged from the original pass.** Restores ALL Health Recovery Dice, every partition, in full —
canonical §25.10/§28.9, stated twice, not a half-pool rule. `HealthRecoveryDiceResources.onLongRest`
mirrors `StandardSpellSlotResources.onLongRest`'s exact-deficit-restore pattern. Registered as a
`LONG`-only `RestEventBus` listener in both `PlayerConnectionEvents`' JOIN and AFTER_RESPAWN blocks.
Proven: full restore, and a second consecutive Long Rest does not overfill or error (dev-server
check 9).

## 9. Exact future Hit Die API canon documented (new, this correction pass)

A new §32 was added to `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` (after §31, before "END OF
CANONICAL DOCUMENT" — appended rather than inserted mid-document, so no existing section number
needed to change). It records, as **design-only, not implemented**:

- **Scope:** belongs to Character Creation/Progression/Classes/Dice API, not the Generic Resource
  API; a class may define a Hit Die for multiple resources (Health, Stamina, Mana, future Chakra/
  Reiryoku/others). Only the two already-supplied canonical die/modifier pairs are recorded —
  Barbarian HP Hit Die = d12 (CON modifier), Barbarian Mana Hit Die = d4 (INT modifier). No other
  class/resource die size or modifier was invented or extrapolated.
- **Roll semantics:** (1) real, visible rolls through the Dice API/presentation machinery, not a
  silent background random number; (2) reroll any result of 1 on an ordinary progression roll; (3)
  the character's first/starting class receives the die's MAXIMUM result (not a roll) for its first
  applicable progression die per resource — and this rule is tied specifically to starting-class
  identity, never triggered merely by another class reaching its own level 1; (4) starting-class
  identity must be durably persisted, since storing only accumulated die totals cannot express rule
  3 indefinitely; (5) once the Hit Die API/state exists, `/showclass`'s dev/test class-reset must
  also reset stored starting-class identity and whatever future progression state a genuinely fresh
  test character requires — not implemented now, recorded as a required future integration point.
- **Resources acquired after existing class levels:** a class may define a Hit Die for a resource
  the character does not yet possess; that die stays hidden/inactive until the resource is actually
  obtained, at which point the system must expose the implied historical rolls as **pending**
  (never auto-resolved in bulk) for the player to later resolve deliberately through a Character/
  Class UI action — with the starting-class-maximum rule still applying to the first applicable
  pending roll.
- **Future data/ledger requirement:** the eventual design will likely need durable, per-entry
  progression records (class source, class level, resource, die used, MAXIMUM-vs-ROLLED, resolved
  result, pending-vs-resolved) rather than a single accumulated total — reasoning given (permanence,
  randomness, late unlocks, level changes, testability, reroll/respec exploit prevention). No Java
  schema was locked.

## 10. Confirmation future Hit Die API was NOT implemented

Confirmed — §32 is documentation only. No Java source, no persisted state, no command, no UI, no
roll logic, no starting-class tracking, and no ledger of any kind was written for it. The only code
in this task is the Health Recovery Dice Generic Resource (a rename/correction of already-existing
Phase 7A work), which canonical §32.1 and every renamed file's own Javadoc now explicitly
distinguish from the future API.

## 11. Files removed/renamed/added/modified

**Removed (old working name, never committed):**
- `src/main/java/zcylas/totality/api/rpg/classes/HitDiceMaximumResolver.java`
- `src/main/java/zcylas/totality/api/rpg/resources/integration/HitDiceResources.java`
- `src/main/java/zcylas/totality/api/rpg/resources/verification/HitDiceResourceVerification.java`
- `src/test/java/zcylas/totality/api/rpg/resources/integration/HitDiceResourcesTest.java`
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE7_HIT_DICE_IMPLEMENTATION_REPORT_2026-09-16.md`
- `Context/Audit/Review/TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE7_HIT_DICE_REVIEW_2026-09-16.zip`

**Added (corrected name):**
- `src/main/java/zcylas/totality/api/rpg/classes/HealthRecoveryDiceMaximumResolver.java`
- `src/main/java/zcylas/totality/api/rpg/resources/integration/HealthRecoveryDiceResources.java`
- `src/main/java/zcylas/totality/api/rpg/resources/verification/HealthRecoveryDiceResourceVerification.java`
- `src/test/java/zcylas/totality/api/rpg/resources/integration/HealthRecoveryDiceResourcesTest.java`
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE7_HEALTH_RECOVERY_DICE_IMPLEMENTATION_REPORT_2026-09-16.md` (this file)
- `Context/Audit/Review/TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE7_HEALTH_RECOVERY_DICE_REVIEW_2026-09-16.zip`

**Modified (already-dirty-from-original-pass files, updated in place):**
- `src/main/java/zcylas/totality/Totality.java` — verification registration line updated.
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceIds.java` — `HIT_DICE` →
  `HEALTH_RECOVERY_DICE`.
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java` — resource
  id, resolver, and grant-provider references updated; the definition-block comment rewritten to
  document the correction and the `ClassData.hpDie` sharing relationship.
- `src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java` — both `LONG`-only Rest
  listener registrations (JOIN and AFTER_RESPAWN) updated to call
  `HealthRecoveryDiceResources.onLongRest`.
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java` — renamed test
  method and assertions; +1 new test guarding against the obsolete id resurfacing; registry-size
  assertion (11) and dormant-count doc comment left correct (the registry size itself did not
  change — this was a rename, not an addition/removal of a definition).
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` — §25.10 and §28.9 renamed to Health
  Recovery Dice with an explicit naming-note/distinction from the future Hit Die API; every other
  pre-existing "Hit Dice" mention throughout the document (lines in §2/§4/§7/§9/§22/§26/§29/§31 —
  general Generic Resource examples, partition/descriptor examples, integration-ownership bullets,
  and the handoff checklist) updated to Health Recovery Dice for full consistency, since all of them
  described the same Generic Resource concept, not the newly-documented future API; one instance
  ("Hit Die size") deliberately left unchanged where it correctly referred to the class metadata
  concept, not the Generic Resource. New §32 appended (see §9 above). No unrelated section was
  rewritten.

## 12. Tests/verification

**`HealthRecoveryDiceResourcesTest`** (4 tests — 3 renamed from `HitDiceResourcesTest`, +1 new):
null-player yields no grant; `SOURCE_ID` is `class_progression_health_recovery_dice`; the registered
`ResourceGrantPolicy` is exactly `SHARED_RESOURCE`/`REMOVE_STATE`/`PRESERVE_DEFICIT`; **new** — the
obsolete `totality:hit_dice` id is not registered anywhere.

**`PlayerResourceRegistryTest`** (renamed + 1 new test): `totality:health_recovery_dice` is
`GENERIC_COMPONENT`/`PARTITIONED_POOL` with the correct polarity/minimum/capabilities; **new** — a
dedicated test asserting `totality:hit_dice` is absent from the registry.

**`HealthRecoveryDiceResourceVerification`** (dev-server, 14 checks — 13 renamed/adapted from
`HitDiceResourceVerification`, +1 new): the new first check asserts the corrected id is registered
and the obsolete id is not; the remaining 13 checks are the original Phase 7A coverage, unchanged in
substance, only renamed: no-class-owns-no-state; first qualifying class grants correct initial
partition maxima; re-reconciliation does not duplicate/refill; `PRESERVE_DEFICIT` level-up behavior;
selected-partition spend decrements exactly that partition; empty-partition spend rejected;
overspend rejected atomically; Short Rest does nothing; Long Rest restores all partitions (twice,
proving no overfill); single-class maximum resolution; same-die-size multiclass aggregation
(Monk+Warlock, both d8); different-die-size independence (Barbarian d12 alongside the d8
aggregation); defensive class-level decrease (clamps correctly, never negative, wire-safe).

All 13 coverage points the task required (§ "TESTS / VERIFICATION" in the task prompt) are present
and confirmed passing — see §16 below.

## 13. Full validation

| Check | Result |
|---|---|
| Focused tests (`HealthRecoveryDiceResourcesTest`, `PlayerResourceRegistryTest`) | **PASS** |
| `./gradlew test` (full suite) | **PASS** — 1626 tests, 0 failures, 0 errors (was 1624 before this correction pass — +2: the two new obsolete-id-absence regression tests) |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` | **PASS** — no real whitespace errors (only benign CRLF-normalization warnings) |

## 14. Dedicated verification results

Bounded dedicated-server run (`./gradlew runServer`), reached `Done`, then:

| Verification | Result |
|---|---|
| `ResourceFoundationVerification` | All 6 self-test checks passed |
| `BaselineResourceMigrationVerification` | All 11 self-test checks passed |
| `BarbarianRageMigrationVerification` | All 18 self-test checks passed |
| `StandardSpellSlotMigrationVerification` | All 23 self-test checks passed |
| `CrownOfStarsActiveInstanceActionVerification` | All 11 self-test checks passed |
| **`HealthRecoveryDiceResourceVerification`** | **All 14 self-test checks passed** (was 13 under the old name — +1 new obsolete-id-absence check) |
| `ProvisionerEntityBackedSmokeTest` | 3/4 FAILED — known unrelated pre-existing failure, unchanged |
| `OffhandAttackVerification` | 3/5 FAILED — known unrelated pre-existing failure, unchanged |

Every other verification's result is byte-identical to the original (pre-correction) Phase 7A run —
confirms the rename disturbed nothing outside its own scope.

## 15. Preserved backend semantics (unchanged by this correction)

Per the task's explicit instruction, no mechanic was redesigned — only names changed. Confirmed
unchanged and re-verified: `GENERIC_COMPONENT` authority; `PARTITIONED_POOL` model; class-derived
maxima via `ClassData.hpDie`; same-die-size classes aggregate into one partition; different die
sizes remain independent; `SHARED_RESOURCE` aggregation; `REMOVE_STATE` when no qualifying class
remains; `PRESERVE_DEFICIT` maximum-change policy; first grant `AtMaximum`; generic
persistence/sync (no bespoke packet); death `KEEP_CURRENT`; Long Rest restores all partitions; Short
Rest performs no automatic spend/healing; selected-partition spending through
`PlayerResourceService` remains the only spend path; no cross-partition fallback. No defect was
found during the rename that would have justified a behavior change, so none was made.

## 16. Explicit Phase 7 boundary

Confirmed **not implemented, not started, not designed**: Pact Magic, Ki, species resources (e.g.
Solar Charge), Thirst, Temperature, Rest Need/Fatigue, and Sanity — items 2 through 7 of the Phase 7
roadmap remain entirely untouched. The future Hit Die API (§9/§10 above) is documentation-only, not
implemented. No commit was created for either the original Phase 7A pass or this correction.
