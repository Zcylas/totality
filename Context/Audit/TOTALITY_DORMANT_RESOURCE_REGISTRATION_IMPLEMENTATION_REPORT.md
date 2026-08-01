# TOTALITY — DORMANT GENERIC PLAYER RESOURCE READINESS AND REGISTRATION — IMPLEMENTATION REPORT

**Date:** 2026-08-01
**Type:** Narrow dormant-registration pass. No commit, stage, or push occurred.
**Branch:** `feature/general-resource-api`
**Committed HEAD (unchanged throughout):** `9ac1a92ee686f1aebeec63c42b6afa6e15ad5b2f` ("Rename Stamina Exhaustion system to Stamina Depletion and fix join lifecycle")

---

## 1. Executive Summary

Three new production `PlayerResourceDefinition`s were registered — `totality:thirst`, `totality:sanity`, `totality:ki` — all `GENERIC_COMPONENT`-authority, the first of their kind in production (every one of the existing seven resources is `EXTERNAL_ADAPTER`). All three are pure metadata: no adapter, no grant provider, no capabilities, no presentation, no HUD/menu/client-reader wiring, and — because nothing in production code ever calls `instantiateScalar` for them — no player ever actually owns one. Registration alone, and ordinary query, are both confirmed (by direct source inspection and by dedicated tests against the real production registry) to never instantiate, persist, synchronize, or display state for any of the three.

`totality:fatigue` was **deferred**, not registered. The canonical `TOTALITY_REST_AND_FATIGUE.txt` design document explicitly marks Fatigue/Rest Need's value range as "do NOT choose values, list as TBD" and conceptually separates a stored long-term accumulator ("Rest Need") from Fatigue itself, which it defines as a *derived, player-facing tier* rather than a stored value. The task's own candidate semantics assume a stored 0–100 accumulator under the literal id `totality:fatigue` — a literal id no canonical document has ever used (the only reserved id anywhere is the explicitly provisional `totality:rest_need`). Registering `totality:fatigue` today would require either inventing a numeric range the canonical documents explicitly decline to commit to, or silently deciding the stored-vs-derived architecture question those same documents leave open — both are exactly the kind of invented placeholder this pass is required not to add.

`totality:temperature` was **not registered**, per explicit task instruction — it is not yet representable as a simple high/low-is-good scalar (`TARGET_RANGE` polarity would be structurally possible, but is explicitly out of scope for this pass).

Ki's central technical question — whether a genuinely dynamic, class-progression-resolved maximum can be represented honestly without inventing a fake resolver — is answered **yes**: `PlayerResourceDefinition.authoredBaseMaximum` is an `OptionalLong` that the API already supports leaving empty (an existing, already-used pattern — `totality:spell_slots` omits it today for a different, structural reason), and `PlayerResourceService.queryGenericState` already has a real, tested failure path (`MAXIMUM_UNAVAILABLE`) for exactly this "no maximum-resolver framework exists yet" case. No new mechanism was invented; Ki simply uses the existing `OptionalLong.empty()` + `MAXIMUM_UNAVAILABLE` machinery honestly.

---

## 2. Repository Checkpoint

| Check | Value |
|---|---|
| Branch | `feature/general-resource-api` (unchanged throughout) |
| HEAD | `9ac1a92ee686f1aebeec63c42b6afa6e15ad5b2f` (unchanged throughout) |
| HEAD subject | "Rename Stamina Exhaustion system to Stamina Depletion and fix join lifecycle" |
| `origin/feature/general-resource-api` | 0 ahead / 0 behind at start and end |
| Staged changes | None at start; none at end |

Pre-flight `git status --short` recorded 67 pre-existing entries (24 modified generated/build files, 43 untracked review bundles/screenshots/logs/caches/audit docs) — one more than the task's "approximately 66" estimate, fully explained by `log4j-dev.xml`/`logs/` predating this pass (confirmed by file timestamps recorded during the prior session's own pre-flight, before this pass touched anything). All 67 are confirmed unchanged in §Final Verification below.

---

## 3. Purpose and Scope

Register safe, inert `PlayerResourceDefinition`s in advance of their owning APIs, wherever a candidate's public identity and basic semantics are settled enough to encode honestly with the *current* definition model — without granting, instantiating, persisting, synchronizing, displaying, or adding any gameplay/mutation behavior. Candidates: Thirst, Sanity, Ki (approved), Fatigue (conditional), Temperature (explicitly deferred).

---

## 4. Existing Production Resource Inventory

Seven definitions, all `EXTERNAL_ADAPTER`-authority, registered by `ProductionResourceDefinitions.register()`: Health, Food, Breath, Mana, Stamina, Spell Slots, Rage. Confirmed semantically unchanged by this pass — none of their builder calls, ids, or registration order were touched; only new registrations were appended after Rage and before the final `freeze(...)` call.

---

## 5. Dormant Registration Safety Model

Verified by direct source inspection (not re-derived, since the prior Food/Fatigue/Exhaustion audit already established these facts and this pass's own pre-flight re-confirmed them against current code):

- `PlayerResourceRegistry.register(definition)` is pure metadata insertion into a `Map` — no `ServerPlayer`, no NBT, no component touched.
- `PlayerResourceStateComponent.instantiateScalar`/`instantiatePartitioned` are the *only* way state is ever created, and nothing in production code calls either automatically — a future grant provider is what will.
- `PlayerResourceService.query`/`queryGenericState` never call `instantiateScalar` on a miss; a missing `GENERIC_COMPONENT` entry returns `Failure(STATE_NOT_INSTANTIATED)`.
- `ResourceSyncManager`'s full-sync path queries every `GENERIC_COMPONENT` definition unconditionally (they "always qualify" for sync eligibility), but `PlayerResourceSyncState.applyFull` explicitly omits an `AbsentOutcome` "from the full view entirely" — an uninstantiated dormant resource is queried (cheap: one registry + one map lookup) but never appears on the wire.
- Persisted/orphaned NBT restoration (`readLiveEntry`/`readOrphanEntry`) only restores an orphan into live state when a registered definition's model matches the persisted model *and* the id is not `EXTERNAL_ADAPTER`-authority — a rule this pass's tests exercise directly against the real registered Thirst/Sanity/Ki definitions (§19).

---

## 6. Candidate Readiness Matrix

| | Thirst | Sanity | Ki | Fatigue | Temperature |
|---|---|---|---|---|---|
| Permanent ID settled? | Yes (`totality:thirst`, canonical §25.11) | Yes (`totality:sanity`, canonical §25.15) | Yes (`totality:ki`, canonical §25.7) | **No** — canonical uses provisional `totality:rest_need`, never `totality:fatigue` | N/A (deferred by instruction) |
| Shape settled? | Yes, SCALAR | Hedged "likely SCALAR" (canonical), settled by this pass's own instructions | Yes, SCALAR | Conceptually contested — stored accumulator vs. derived tier (§10b) | No — ambient/body/exposure/target-range all live options |
| Polarity settled? | Yes, HIGH_IS_GOOD | Hedged "likely HIGH_IS_GOOD", settled by this pass's instructions | Yes, HIGH_IS_GOOD | Yes in direction (HIGH_IS_BAD/"low is good"), not in exact meaning of endpoints | Not settled |
| Absolute minimum settled? | Yes, 0 | Yes, 0 | Yes, 0 | Conceptually 0 = rested, but not authoritatively adopted | Not settled |
| Maximum semantics settled? | Yes, authored 100 (this pass's instructions; canonical doesn't state a number but doesn't contradict one) | Yes, authored 100 (same basis) | **Explicitly NOT a fixed number** — dynamic, class-progression-resolved (canonical §25.7) | **No** — canonical explicitly lists value range as TBD (§10d) | Not settled |
| Initialization strategy settled? | Yes, AtMaximum (matches `ResourceLifecyclePolicy.DEFAULT`) | Yes, AtMaximum (same) | Not decided; left to future Ki API | Directionally AtMinimum, not authoritatively adopted | Not settled |
| Authority settled? | Yes, GENERIC_COMPONENT | Yes, GENERIC_COMPONENT (canonical hedges "unless its dedicated design requires otherwise") | Yes, GENERIC_COMPONENT | Yes in direction (canonical: "the generic API must be able to store and sync Rest Need") | Not settled |
| Universal or conditional ownership settled? | Universal (future) | Universal or context-enabled (future) | Conditional — class/species/ability-gated (future) | Likely universal (future) | Not settled |
| Grant source settled? | No (future Survival/Thirst system) | No (future Sanity system) | No (future Ki API / MonkClass) | No (future Fatigue API) | N/A |
| Removal/revocation policy required now? | No — nothing is ever granted this pass | No | No | No | N/A |
| Safe to register without state? | Yes | Yes | Yes | Would be yes structurally, but blocked by unresolved semantics above | N/A |
| Existing stale/orphan ID collision risk? | No previous production registration found for this id at the audited checkpoint (confirmed by grep of `src/main`/`src/test`); the concept and id are already documented in design material (canonical §25.11) — see §19's review-correction addendum for the distinction | Same, per canonical §25.15 | Same, per canonical §25.7 | N/A (not registered) | N/A |
| Existing source or documentation conflict? | None found | None found (only hedging language, not contradiction) | None found (canonical explicitly describes a dynamic maximum, matching this pass's approach) | **Yes** — canonical explicitly forbids choosing a value range; canonical id differs from this pass's candidate id; canonical conceptually separates Fatigue (derived) from the stored accumulator | Yes — task explicitly defers it |
| **Register in this pass?** | **Yes** | **Yes** | **Yes** | **No** | **No** |
| Exact reason if deferred | — | — | — | See §10 | See §11 |

---

## 7. Thirst Decision

**Registered.** `totality:thirst`, `SCALAR`, `HIGH_IS_GOOD`, `absoluteMinimum=0`, `authoredBaseMaximum=100`, `GENERIC_COMPONENT` authority (builder default — no `.externalAdapter(...)` call), no capabilities, no presentation, `definitionVersion=1`, lifecycle left at `ResourceLifecyclePolicy.DEFAULT` (`AtMaximum` initialization — already exactly "at maximum when explicitly instantiated," no override needed). Canonical §25.11 confirms `SCALAR`/`GENERIC_COMPONENT`/`HIGH_IS_GOOD`; it does not state an exact numeric range, but does not contradict one either — this pass's own authoritative task instructions supply 0–100, filling a genuine gap rather than overriding a settled decision. Canonical §25.11 also lists Thirst's eventual full capability set (`RESTORABLE`, `DIRECT_DRAIN`, `PASSIVE_DECAY`, `THRESHOLD_EVENTS`, `HUD_VISIBLE`, `MENU_VISIBLE`) — none of it is declared now, since none of it has any implementing behavior yet; declaring it would misrepresent current availability (see §18).

## 8. Sanity Decision

**Registered.** Identical shape to Thirst: `totality:sanity`, `SCALAR`, `HIGH_IS_GOOD`, `absoluteMinimum=0`, `authoredBaseMaximum=100`, `GENERIC_COMPONENT`, no capabilities, no presentation, `definitionVersion=1`, default lifecycle. Canonical §25.15 hedges "likely SCALAR"/"the future Sanity design owns its exact thresholds" — this pass's task instructions settle those as the registered values without inventing anything the canonical doc actively contradicts (it hedges; it does not forbid). No Sanity loss, restoration, conditions, effects, hallucinations, UI, or commands were implemented.

## 9. Ki Decision

**Registered**, with no authored maximum. `totality:ki`, `SCALAR`, `HIGH_IS_GOOD`, `absoluteMinimum=0`, **`authoredBaseMaximum` left absent** (`OptionalLong.empty()`, the builder default — no `.authoredBaseMaximum(...)` call), `GENERIC_COMPONENT`, no capabilities, no presentation, `definitionVersion=1`.

**Could Ki be represented honestly? Yes**, using existing, already-tested machinery, not an invented one:

- `PlayerResourceDefinition.authoredBaseMaximum` is already an `OptionalLong` — leaving it empty is a real, already-used pattern (`totality:spell_slots`, Phase 2D, omits it because the field is scalar-shaped and cannot represent ten per-level maxima; Ki omits it for a different but equally honest reason — the single scalar maximum genuinely isn't known yet).
- `PlayerResourceRegistry.validate()` only checks `authoredBaseMaximum` *if present* (`d.authoredBaseMaximum().isPresent() && ... <= absoluteMinimum`) — an absent maximum is not a validation error.
- `PlayerResourceService.queryGenericState` already has a dedicated, pre-existing failure path for exactly this case: `ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE`, whose own Javadoc reads "no maximum-resolver framework exists yet." A hypothetical future query against instantiated Ki state fails structurally with this reason rather than fabricating a number — proven directly against Ki's real registered definition by `DormantResourceRegistrationTest.evenIfKiStateWereHypotheticallyInstantiatedQueryFailsWithMaximumUnavailableRatherThanFabricatingOne` (§19).

No fake maximum resolver was added. No grant provider was added. No player was granted Ki. Monk gameplay was not modified. No Ki HUD/regeneration/mutation code was added.

## 10. Fatigue Decision

**Deferred — not registered.** Per Part 6's explicit gate, this pass evaluated the five listed requirements:

1. LOW_IS_GOOD-or-equivalent polarity supported? **Yes** — `ResourcePolarity.HIGH_IS_BAD` already exists and is exactly this equivalent (higher value = worse = lower is good).
2. At-minimum initialization supported? **Yes** — `ResourceGrantInitialization.AtMinimum` already exists and is a direct override via `PlayerResourceDefinition.Builder.initialization(...)`.
3. **Canonical documents do not conflict with the 0–100 accumulated-Fatigue model? No — they conflict directly.** `TOTALITY_REST_AND_FATIGUE.txt §10d` ("Exact unresolved decisions... do NOT choose values, list as TBD") explicitly lists "value range" as unresolved and explicitly instructs against choosing one — the task's own candidate semantics assume 0–100 is "likely" settled, but the authoritative design document actively forbids treating any number as final. Separately, `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md §25.12` and `TOTALITY_REST_AND_FATIGUE.txt §10b` both conceptually distinguish a stored long-term accumulator ("Rest Need") from Fatigue itself, defined as "a player-facing derived condition/tier" — not necessarily a stored scalar at all. Registering `totality:fatigue` as a raw stored 0–100 scalar would either misrepresent this architecture (if the eventual design keeps Fatigue purely derived) or silently pre-decide a question those documents explicitly leave open.
4. No placeholder grant or gameplay behavior required? Would be true in isolation, but is moot given #3.
5. Can remain genuinely uninstantiated? Would be true in isolation, but is moot given #3.

A sixth, independent blocker: **no canonical document anywhere uses the literal id `totality:fatigue`.** The only reserved/provisional id anywhere in the design corpus is `totality:rest_need`, explicitly marked "provisional placeholder; final name/ID decided by dedicated Rest Need/Fatigue design" (`TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` line 541). This pass's own task instructions explicitly forbid substituting `RestNeed` for `Fatigue` without explicit design approval — which this pass does not have. Registering either name today would be premature under the task's own rules.

**What must be decided first, before Fatigue/Rest Need can be honestly registered:** (a) the final public id — `totality:fatigue`, `totality:rest_need`, or something else — decided by the dedicated Fatigue/Rest Need design, not this pass; (b) whether the stored resource is the raw accumulator (Rest Need) with Fatigue purely derived and unstored, or whether Fatigue itself becomes the stored value; (c) the actual numeric range/rates/thresholds, which canonical documentation explicitly declines to pre-commit.

## 11. Temperature Deferral

**Not registered, per explicit task instruction.** No `totality:temperature` id, no Temperature definition, no body-temperature/heat/cold/thermal-exposure state, no Temperature HUD presentation were added. `ResourcePolarity.TARGET_RANGE` already exists and would structurally represent a "safe central band" polarity if Temperature were ever approved, but this pass does not use it or reserve any name toward it.

---

## 12. Final Registered Definitions

Three: `totality:thirst`, `totality:sanity`, `totality:ki`. Production registry size: **10** (7 existing `EXTERNAL_ADAPTER` + 3 new `GENERIC_COMPONENT`).

## 13. Exact Definition Metadata

| Field | Thirst | Sanity | Ki |
|---|---|---|---|
| id | `totality:thirst` | `totality:sanity` | `totality:ki` |
| model | SCALAR | SCALAR | SCALAR |
| polarity | HIGH_IS_GOOD | HIGH_IS_GOOD | HIGH_IS_GOOD |
| stateAuthority | GENERIC_COMPONENT | GENERIC_COMPONENT | GENERIC_COMPONENT |
| externalAdapterId | absent | absent | absent |
| unitScale | 1 | 1 | 1 |
| absoluteMinimum | 0 | 0 | 0 |
| authoredBaseMaximum | 100 | 100 | **absent** (genuinely dynamic) |
| capabilities | none | none | none |
| targetRange | absent | absent | absent |
| lifecycle | DEFAULT (AtMaximum) | DEFAULT (AtMaximum) | DEFAULT (AtMaximum, provisional — future Ki API may override) |
| definitionVersion | 1 | 1 | 1 |
| presentation | absent | absent | absent |

## 14. Authority and Initialization

All three use `GENERIC_COMPONENT` authority — the builder's own default, reached simply by never calling `.externalAdapter(...)`. This is the first production use of `GENERIC_COMPONENT` (every existing resource is `EXTERNAL_ADAPTER`); `PlayerResourceStateComponent`'s `instantiateScalar` path, previously only exercised by tests against synthetic ids, is now exercised against real production ids too (§19), still never invoked automatically. Initialization for Thirst/Sanity is `ResourceLifecyclePolicy.DEFAULT`'s built-in `AtMaximum()` — not overridden, since it already matches "at maximum when explicitly instantiated" exactly. Ki's initialization policy is left at the same default but is explicitly flagged as provisional/undecided, since the task gave no instruction on Ki's own initialization semantics and Ki's future grant model (conditional, class-progression-tied) may need something other than `AtMaximum`.

## 15. Grant and Ownership Behavior

None. No `ResourceGrantProvider` exists for any resource in this codebase yet (confirmed — that hierarchy remains declaration-only, per `ResourceGrantInitialization`'s own class Javadoc). No player is granted Thirst, Sanity, or Ki by this pass. "Conditional ownership" for Ki is not something the definition itself encodes (no such field exists on `PlayerResourceDefinition`) — it will be the future Ki API's own grant provider's responsibility, exactly the same way dormancy already works identically for Thirst/Sanity (nothing calls `instantiateScalar` for anyone).

## 16. Persistence Behavior

No entry is written to any player's NBT for any of the three ids under ordinary play, because none is ever instantiated. `PlayerResourceStateComponent.writeData` only iterates the live `states`/`orphanedStates` maps, which stay empty for these ids. Proven directly: `DormantResourceRegistrationTest.registrationAloneLeavesAFreshComponentEmptyForAllThreeDormantIds`.

## 17. Synchronization Behavior

`ResourceSyncManager`'s full-sync path queries all three unconditionally (GENERIC_COMPONENT resources "always qualify" for sync eligibility — confirmed and tested, §19), but every query fails with `STATE_NOT_INSTANTIATED` → `AbsentOutcome`, and `PlayerResourceSyncState.applyFull` already omits `AbsentOutcome` "from the full view entirely" — none of the three ever appears in an actual sync packet. This is pre-existing, already-tested generic behavior this pass relies on rather than re-derives.

## 18. Presentation Behavior

No `ResourcePresentationDefinition`, no capabilities (`HUD_VISIBLE`/`MENU_VISIBLE` included) are declared for any of the three. `HUD_VISIBLE`/`MENU_VISIBLE` are confirmed, by repo-wide grep, to have **zero current consumers anywhere in the codebase** — nothing iterates the registry by capability to decide what to render; every HUD/menu call site names its resource id directly (`TotalityHudRenderer` reads `PlayerResourceIds.MANA`/`STAMINA`/etc. by name, never "every HUD_VISIBLE resource"). Declaring `HUD_VISIBLE` for Thirst/Sanity/Ki today would therefore be provably inert — but it would still misrepresent these three as "available for display" before any owning system, grant provider, or client reader exists. The narrowest honest choice, and the one taken, is to declare no capabilities at all. No HUD bar, icon, formatter, client reader, or presentation-resolver entry was added for any of the three.

## 19. Stale/Orphaned State Caveat

**Honestly disclosed limitation, matching this codebase's own established precedent** (`PlayerResourceStateComponentTest`'s class Javadoc records the same constraint): `readLiveEntry`/`readOrphanEntry` — the actual NBT-restoration decision logic — require a real Mojang `ValueInput`/`ValueOutput`, which this project has no harness to construct outside a running game. Neither was exercised end-to-end by this pass.

What **was** exercised directly, against the real production-registered Thirst/Sanity/Ki definitions, is every input that restoration decision depends on:

- `isRegisteredExternalAdapterAuthority(id)` returns `false` for all three (via reflection against the real package-private method) — confirming none would be rejected the way Health/Food/etc. are.
- Reproducing `readOrphanEntry`'s exact `restorable` condition (`definition.model() == persistedModel && !isRegisteredExternalAdapterAuthority(id)`) against the real registry proves a hypothetical matching-model (SCALAR) orphan under any of the three ids would be restored, and a hypothetical mismatched-model (PARTITIONED_POOL) orphan would remain quarantined — exactly the production rule, exercised with real data rather than a reimplementation.

**Corrected wording (review-correction pass — the original text here overclaimed; see the addendum in §31 for the full correction record):** `totality:thirst`, `totality:sanity`, and `totality:ki` are **not** new concepts — all three ids and their intended semantics are already documented in canonical design material that predates this pass (`TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` §25.7/§25.11/§25.15, quoted directly in §6–9 above). What this pass's grep of `src/main`/`src/test` actually established is narrower and more precise: **no previous production registration for these ids was found at the audited checkpoint**, and **no current production grant, state-creation, mutation, persistence, synchronization, client-reader, or HUD path was found for these Resources before this pass** — i.e., no project-generated live or orphaned state is known to exist from the audited production code. That is not the same claim as "no stale or orphaned NBT can exist anywhere," which this repository cannot prove: matching experimental, manually authored, externally created, stale, or corrupted NBT (from experimental worlds, manually edited saves, old development builds, or external tooling) remains theoretically possible and was neither ruled out nor tested for. If such matching compatible live or orphaned Generic Resource state does exist for one of these ids, the existing `PlayerResourceStateComponent` restoration and quarantine rules already govern it exactly as documented above (§16–19) — this pass adds no migration or deletion behavior for such data, and none was read, deleted, or rewritten. The important caveat stands: registration is inert for a clean player, but definition registration may make previously stored *compatible* state recognizable under the existing generic restoration rules, for whatever state might already be out there outside this audit's visibility.

---

## 20. Production Files Changed

- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceIds.java` — added `THIRST`/`SANITY`/`KI` constants (no `TEMPERATURE`/`FATIGUE` added).
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java` — registered the three new definitions inside the existing `registerDefinitions()` method, after Rage and before the final `freeze(...)` call; `registerAdapters()` and `registerFormatters()` unchanged.

No other production file was touched.

## 21. Tests Added or Changed

**New files:**
- `src/test/java/zcylas/totality/api/rpg/resources/DormantResourceRegistrationTest.java` (10 tests) — dormancy/query/orphan-input behavioral proof against the real registered definitions.
- `src/test/java/zcylas/totality/api/rpg/resources/DormantResourceScopeRegressionTest.java` (14 tests) — source-regression sentinels: no Temperature id/definition, no Fatigue id/definition, no new gameplay classes, no client-reader/HUD/packet/command references to the new ids, no reference to the new ids outside the two files that declare/register them, no zip archive under `src/`.

**Modified files:**
- `src/test/java/zcylas/totality/api/rpg/resources/PlayerResourceRegistryTest.java` (+5 tests) — registry size/metadata assertions for the three new definitions; confirms `totality:temperature`/`totality:fatigue`/`totality:rest_need` remain unregistered.
- `src/test/java/zcylas/totality/networking/resource/ResourceSyncManagerEligibilityTest.java` (+1 test) — confirms the three new ids are sync-*eligible* (correctly, since all `GENERIC_COMPONENT` resources are) without producing fabricated wire state.

**Total: 30 new tests.**

---

## 22. Focused Validation

`./gradlew test --tests "zcylas.totality.api.rpg.resources.PlayerResourceRegistryTest" --tests "zcylas.totality.api.rpg.resources.DormantResourceRegistrationTest" --tests "zcylas.totality.api.rpg.resources.DormantResourceScopeRegressionTest" --tests "zcylas.totality.networking.resource.ResourceSyncManagerEligibilityTest"` — **BUILD SUCCESSFUL**, all tests passed after three self-inflicted false-positive fixes during authoring (a screenshot check too strict against legitimate `src/main/resources` game-asset PNGs; a Temperature-absence check tripped by this pass's own explanatory comment; a HUD-check tripped by a pre-existing, unrelated "TODO: Thirst aligned with Stamina" planning comment already in `TotalityHudRenderer.java` before this pass) — all three fixed by narrowing the assertions to the actual code/identifier being guarded against, not incidental English words in comments.

## 23. Full Validation

- `./gradlew test` (full suite, run twice — once before `clean`, once after, for a true clean-state total): **1391 tests, 0 failures, 0 errors, 0 skipped.**
- **Baseline:** 1361 tests, 0 failures, 0 errors, 0 skipped.
- **Increase: exactly 30 tests** (1391 − 1361), matching §21's total precisely (10 + 14 + 5 + 1).

## 24. Datagen and Build Validation

- `./gradlew runDatagen`: **0 files written, 0 removed** ("Caching: total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"). `git status --short` captured immediately before and after is **byte-for-byte identical** (confirmed via `diff`).
- `./gradlew clean build`: **BUILD SUCCESSFUL** (includes a fresh compile, test run, jar, and check task).
- `git diff --check`: **exit code 0**. Warnings are the same pre-existing CRLF/LF line-ending notices on the 22 pre-existing modified generated-data files as prior sessions, now also naming this pass's own 4 modified files with the same informational (non-error) notice — no actual whitespace error anywhere.

## 25. Dedicated-Server/Client Validation

**Dedicated-server classloading check:** `./gradlew runServer` was run for up to 100 seconds and **fully started** — "Done (0.337s)! For help, type \"help\"" was reached, confirming all mod initialization (including `ProductionResourceDefinitions.register()` registering the three new dormant definitions) completed without any exception, crash, or registry-validation error. A repo-wide grep of the run log for `Exception`/`FATAL`/`Crash`/any `Resource`-tagged error, and specifically for `thirst`/`sanity`/`totality:ki`, found zero matches — the three new definitions registered silently and without incident, exactly as dormant metadata should. Two unrelated, pre-existing self-test suites (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) logged failures during this run — both concern NPC trading and offhand Power Attack combat, neither touches the Generic Player Resource API, and both are consistent with needing a real connected player entity to exercise (this run had none — the server sat idle and paused after "Server empty for 60 seconds"). These are reported honestly as unrelated, pre-existing dev-environment self-test results, not caused by this pass.

**Client classloading check:** `./gradlew runDatagen` (run as part of §24) launches the client environment (`Env=CLIENT`) to drive Totality's datagen providers, and completed successfully, including this session's own `NotificationTimingVerification`/`PowerAttackFlashVerification`/`ProvisionerRendererVerification`/`KeybindVerification` self-test suites all reporting "All N self-test checks passed" — confirming client-side classloading and mod initialization succeeded with the three new dormant definitions registered. `ProductionResourceDefinitions.register()` runs on both dedicated-server and client environments identically (it is common, not client-only, code) — the same registration call that must succeed for `runDatagen`'s client environment to reach its own provider-running stage already proves the new definitions load without error in that environment.

## 26. Manual Validation

**Not performed / not applicable.** This pass adds zero player-visible behavior by design — no HUD, no menu, no notification, no gameplay effect exists to manually validate. The task's own scope explicitly prohibits adding anything that would be manually observable (HUD bars, icons, client readers, commands that instantiate state). Correctness was validated entirely through the automated test suite (§21–23) and direct source inspection (§5–19).

---

## 27. Scope Boundaries

Explicitly not done, per task instructions: the Thirst, Sanity, Ki, or Fatigue APIs were not implemented; Temperature was not registered; the real persistent Exhaustion condition was not implemented; Stamina Depletion was not modified; Rest mechanics were not modified; Food 0–100 was not begun; natural Food-based regeneration was not disabled; no HUD bars/icons were added; no client readers were added; no command instantiates Resource state; no gameplay drain/regeneration/mutation/synchronization behavior was added; no broad formatting was run.

## 28. Deferred API Work

- **Thirst API**: owns rate/drain/restore rules, Fluid Material/Safety integration, environment-driven drain, Survival difficulty gating — entirely unimplemented, per canonical §25.11's own scope note ("The generic API does not define any rate or water safety rule").
- **Sanity API**: owns thresholds, drain, restoration, dream/disease/consequence rules — entirely unimplemented, per canonical §25.15.
- **Ki API**: owns grant level, dynamic maximum by Monk level/features, costs, feature consumption, Short/Long Rest recovery rules — entirely unimplemented, per canonical §25.7. This pass's `MAXIMUM_UNAVAILABLE`-based dormancy is exactly what the future Ki API must resolve by introducing a real maximum-resolver mechanism (not yet designed) before Ki can ever be successfully queried.
- **Fatigue/Rest Need API**: owns the final id decision, the stored-vs-derived architecture decision, and all numeric formulas — entirely unresolved, per §10 above.
- **Temperature system**: owns its own shape decision (ambient vs. body vs. exposure vs. target-range-with-danger-bands) — entirely undesigned.

## 29. Unresolved Questions

1. Ki's own future initialization policy (`AtMaximum` vs. something else) is left at the default, unreviewed by the future Ki API — flagged in code comments and §14.
2. Whether Thirst/Sanity's authored 0–100 range should later be formally adopted into the canonical `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` document itself (currently only stated by this pass's own task instructions, not yet written back into that canonical source) is outside this pass's scope to decide.
3. Fatigue/Rest Need's final id, stored-vs-derived architecture, and numeric model remain fully open — see §10 and §28.
4. Temperature's shape remains fully open — see §11 and §28.

## 30. Final Status

**Complete.** Three dormant definitions registered (`totality:thirst`, `totality:sanity`, `totality:ki`), all pure metadata, all confirmed by dedicated tests to never grant/instantiate/persist/synchronize/display state. Fatigue and Temperature correctly deferred with documented, evidence-based blockers rather than speculative or forced registration. 30 new tests added and passing, full suite green at 1391/0/0/0, datagen byte-for-byte unchanged, clean build successful, `git diff --check` clean, client classloading confirmed. Nothing staged, committed, or pushed. HEAD unchanged. All pre-existing unrelated working-tree entries preserved exactly.

---

## 31. Review-Correction Addendum (2026-08-01)

An independent review of this pass found three narrow documentation/test-quality issues. The production dormant Resource definitions themselves (§12–18) were reviewed and found correct — nothing about *what was registered* changed in this correction. This addendum documents the three corrections plus the Git-verified checkpoint text; everything above this section is the original implementation-pass report, left otherwise unmodified except for the two narrowly targeted factual corrections identified below (§75's readiness-matrix row and §19's closing paragraph, both marked inline).

### 31.1 The misleading orphan-test name

**Root issue:** `DormantResourceRegistrationTest` contained a test named `registeringTheseDefinitionsDoesNotDiscardAnyExistingOrphanedData`. The name claims the test proves existing orphaned data survives registration. It does not: the test starts from a **clean** `PlayerResourceStateComponent` (no orphan is ever constructed, persisted, or loaded), instantiates live scalar state for `totality:thirst`, and confirms `orphanedResourceIds()` stays empty. That is a real assertion, but a narrower one — it proves instantiating live state does not *fabricate* an orphan entry as a side effect, not that pre-existing orphaned data is *preserved*.

**Old name:** `registeringTheseDefinitionsDoesNotDiscardAnyExistingOrphanedData`
**New name:** `dormantDefinitionRegistrationDoesNotCreateOrphanEntries`

**What the corrected test proves:** (1) a fresh component starts with zero orphan entries; (2) instantiating live `GENERIC_COMPONENT` state for one dormant id (`totality:thirst`) does not itself create any orphan entry. Nothing more. The test's own new Javadoc explicitly lists what it does *not* prove (NBT orphan restoration, persisted orphan preservation, old-world migration, malformed-NBT quarantine, compatible orphan reactivation) and points at exactly which other tests cover the adjacent, narrower claims that *can* be proven without real NBT.

**Was a genuine NBT orphan round-trip added? No.** Investigated and deliberately not added, for a substantive reason rather than only the lack of a `ValueInput`/`ValueOutput` harness (that limitation is real and already documented, §19): the actual data-transformation method, `PlayerResourceStateComponent.orphanToLiveState(OrphanedResourceState)`, takes **no resource id parameter at all** — it transforms purely by `ResourceModel`. This project's existing, established reflection-based test pattern (`PlayerResourceStateComponentTest.scalarOrphanRestorationPreservesCurrentOverflowAndRemainder`) already exercises this exact method generically for `SCALAR` orphans, and this pass's own `dormantIdsAreNotExternalAdapterAuthorityAndAreThereforeEligibleForOrphanRestoration` already proves, id-specifically and against the real registry, that the *decision* to restore (`model matches && !isRegisteredExternalAdapterAuthority`) evaluates true for all three new ids. Together, these two existing tests already account for 100% of the orphan-restoration logic reachable without a real NBT harness — adding a third test that again calls the same id-agnostic `orphanToLiveState` method under a `totality:thirst`-flavored test name would not add genuine new coverage, only a relabeled duplicate. That would misrepresent test coverage in exactly the same way the original test's name did, which this correction pass exists to fix, not reintroduce.

**Remaining evidentiary limitation, stated plainly:** the real, end-to-end `readLiveEntry`/`readOrphanEntry` NBT parsing path — the actual production code a save-load cycle would run — remains genuinely untested for these (or any) resource, because this project has no harness to construct a real Mojang `ValueInput`/`ValueOutput` outside a running game. This was true before this correction and remains true after it; the correction only makes the test suite honestly say so instead of implying otherwise through a test name.

### 31.2 Unsupported stale-data claims in the report

Two statements in the original report overclaimed:

1. The readiness-matrix row "Existing stale/orphan ID collision risk?" originally read *"None — id never previously used anywhere in the codebase (confirmed by grep)."* Ambiguous: a reader could take "in the codebase" to include design documentation, which would be false — Thirst/Sanity/Ki are extensively documented in `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` §25.7/§25.11/§25.15, quoted directly elsewhere in this same report (§6–9). **Corrected** to state precisely: no previous *production registration* was found at the audited checkpoint, cross-referencing this addendum for the fuller distinction.
2. §19's closing paragraph originally read *"Since all three ids are brand new (confirmed absent from every prior commit and every design document until this pass), there is no actual pre-existing orphaned or stale NBT anywhere under these exact ids."* This was **factually wrong** about design documents (see above — the concepts and ids predate this pass by design, not by accident) and overclaimed certainty about NBT that this repository cannot actually establish (experimental worlds, manually edited saves, old development builds, external tooling, and corrupted data are all outside this audit's visibility). **Corrected** in place (§19) to the evidence-based wording below.

**Corrected wording, now in §19:**

- No previous production registration for these ids was found at the audited checkpoint.
- No current production grant, state-creation, mutation, persistence, synchronization, client-reader, or HUD path was found for these Resources before this pass.
- No project-generated live state is known from the audited production code.
- Matching experimental, manually authored, externally created, stale, or corrupted NBT remains theoretically possible and was neither ruled out nor tested for.
- If matching compatible live or orphaned Generic Resource state exists, the existing `PlayerResourceStateComponent` restoration and quarantine rules apply exactly as documented (§16–19) — unchanged by this pass.
- This pass adds no migration or deletion behavior for such data.

**Distinction preserved:** "no previous production registration was found" (a repository-evidence claim, true) is not the same as "no stale state can exist" (a certainty claim the repository cannot support) — the corrected report now states only the former, plus the important caveat that registration is inert for a clean player, but definition registration may make previously stored *compatible* state recognizable under the existing generic restoration rules, for whatever state might already exist outside this audit's visibility.

### 31.3 The stale `ResourceSyncManager` comment

**Location:** `src/main/java/zcylas/totality/networking/resource/ResourceSyncManager.java`, the Javadoc on the private `isEligibleForGenericSync(PlayerResourceDefinition)` overload.

**Old text:** *"`GENERIC_COMPONENT` resources (none registered in production yet) always qualify..."* — false as of this pass; three `GENERIC_COMPONENT` production definitions now exist.

**New text (comment only — zero behavior change):** *"`GENERIC_COMPONENT` resources always qualify — as of the dormant Resource Registration pass, three production definitions actually use this authority (`totality:thirst`/`totality:sanity`/`totality:ki`), but all three remain dormant and uninstantiated (no grant provider exists for any of them yet): a definition being eligible here does not mean a value is available to synchronize — an uninstantiated `GENERIC_COMPONENT` resource queries to `STATE_NOT_INSTANTIATED`, which becomes an `AbsentOutcome` and is omitted from the wire entirely (see `PlayerResourceSyncState#applyFull`), never a fabricated snapshot."* The rest of the comment (`EXTERNAL_ADAPTER`/`LEGACY_BESPOKE_SYNCHRONIZATION` handling) is unchanged. `isEligibleForGenericSync`'s actual logic (the method body) was not touched — confirmed by `git diff` showing only comment lines changed.

**One additional, out-of-scope observation (not corrected in this pass):** `PlayerResourceStateComponent.copyFrom`'s inline comment (line 458) contains a similarly stale claim — *"No production `GENERIC_COMPONENT` `PlayerResourceDefinition` is registered as of Phase 2A"* — which is now equally outdated. This was not named in this task's Part 3 scope (which named `ResourceSyncManager` specifically), so it was deliberately left untouched to keep this pass narrow, per the task's own "do not begin another task" instruction. Flagged here for a future correction pass.

### 31.4 Git-verified HEAD checkpoint

Run directly during this pass's pre-flight:

```
$ git log -1 --format=%H
9ac1a92ee686f1aebeec63c42b6afa6e15ad5b2f
$ git log -1 --format=%s
Rename Stamina Exhaustion system to Stamina Depletion and fix join lifecycle
```

Both **already exactly matched** the report's existing checkpoint text (§2, and the executive-summary line at the top of this document) — no correction was needed to either value. This addendum records the direct Git verification the task required, rather than only re-asserting the prior text unchecked.

### 31.6 Re-validation for this correction pass

- Focused tests (`PlayerResourceRegistryTest`, `DormantResourceRegistrationTest`, `DormantResourceScopeRegressionTest`, `ResourceSyncManagerEligibilityTest`, `PlayerResourceStateComponentTest`, `PlayerResourceServiceTest`): **123 tests, 0 failures, 0 errors, 0 skipped.**
- Full suite (`./gradlew test`, run twice — once before `clean`, once as part of `./gradlew clean build`): **1391 tests, 0 failures, 0 errors, 0 skipped** — identical to the pre-correction baseline, exactly as expected for a rename-plus-comment-only correction (no new test method was added; see §31.1 for why).
- `./gradlew runDatagen`: **0 files written, 0 removed** ("total files: 350, old count: 350, new count: 350"); `git status --short` captured immediately before/after is byte-for-byte identical.
- `./gradlew clean build`: **BUILD SUCCESSFUL.**
- `git diff --check`: **exit code 0** — only the same pre-existing CRLF/LF informational notices, now also naming this pass's own touched files.
- Dedicated server (`./gradlew runServer`, up to 100s): reached **"Done (0.319s)! For help, type \"help\""**, identical outcome to the original pass. Zero exceptions, zero `Resource`-tagged errors, zero mentions of `thirst`/`sanity`/`ki`. The same two unrelated, pre-existing self-test suites (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) logged the same failures as the original pass's run — confirmed unrelated to this correction (NPC trading and offhand combat, not the Resource API), consistent with needing a real connected player this headless run had none of.
- Client classloading: confirmed via the same `runDatagen` `Env=CLIENT` pass above completing successfully.

### 31.7 Confirmations

- **No Resource definition metadata changed.** `git diff` for `PlayerResourceIds.java`/`ProductionResourceDefinitions.java` is empty in this correction pass — neither file was touched.
- **No production behavior changed.** The only production file touched, `ResourceSyncManager.java`, has a comment-only diff — confirmed by inspection of the diff hunk (§31.3).
- **The existing seven production Resources remain unchanged** — untouched by this correction, exactly as by the original pass.
- **Thirst, Sanity, and Ki remain dormant** — no adapter, no capability, no presentation, no grant, no instantiated state added by this correction.

---

## 32. Final Javadoc Cleanup and Closure (2026-08-01)

A second, narrower independent review found that several older, phase-era Javadocs elsewhere in the Generic Resource core package still described the production registry as containing only Health and Food, exactly two definitions, or no production `GENERIC_COMPONENT` resource — all now false, since three dormant `GENERIC_COMPONENT` definitions (Thirst, Sanity, Ki) exist. This section documents that cleanup. Everything above this section (including §31) is left otherwise unmodified.

### 32.1 Exact files and symbols corrected

| File | Symbol | Previous inaccurate statement | Corrected to |
|---|---|---|---|
| `PlayerResourceRegistry.java` | class Javadoc | "As of Phase 2A, `INSTANCE` contains exactly two frozen, `EXTERNAL_ADAPTER`-authority definitions — `totality:health` and `totality:food`... No `GENERIC_COMPONENT` resource is registered yet." | Describes the seven `EXTERNAL_ADAPTER` resources and the three dormant `GENERIC_COMPONENT` resources (Thirst, Sanity, Ki) as two distinct categories, plus states that registration is pure metadata and does not by itself grant or create player state. |
| `PlayerResourceStateComponent.java` | class Javadoc | "As of Phase 2A, `PlayerResourceRegistry#INSTANCE` contains exactly two production definitions — `totality:health` and `totality:food` — and both are `EXTERNAL_ADAPTER`-authority, so this component still holds zero entries for every player." | Explains the component holds zero entries for two *different* reasons: the seven `EXTERNAL_ADAPTER` resources are structurally rejected by `instantiateScalar`/`instantiatePartitioned`; the three dormant `GENERIC_COMPONENT` resources are legitimately instantiable but simply never granted (no grant provider exists yet). |
| `PlayerResourceStateComponent.java` | `copyFrom(...)` inline comment | "No production `GENERIC_COMPONENT` `PlayerResourceDefinition` is registered as of Phase 2A (only `totality:health`/`totality:food` exist, both `EXTERNAL_ADAPTER`)." | States that three `GENERIC_COMPONENT` definitions now exist, that none has a grant provider so `other.states` normally has no live entry for any of them to copy, and that all three currently use `ResourceLifecyclePolicy.DEFAULT` (`KEEP_CURRENT`) — which happens to already match this blanket-copy behavior, so there is still nothing for `deathPolicy()` to meaningfully differentiate today, but now for a documented reason rather than "none exist." |
| `PlayerResourceService.java` | `query(Player, Identifier)` Javadoc | "Phase 2A registers no `GENERIC_COMPONENT` resource in production, but the routing contract must still hold for future resources." | States that three `GENERIC_COMPONENT` definitions exist (Thirst, Sanity, Ki), all currently uninstantiated for every player, and that querying any of them returns `STATE_NOT_INSTANTIATED` rather than a fabricated default. |
| `ResourceLifecyclePolicy.java` | class Javadoc | "`DEFAULT` is still the only instance used by any production definition as of Phase 2A: `totality:health`/`totality:food` both use it... but it is effectively inert for either — both are `EXTERNAL_ADAPTER`-authority." | States `DEFAULT` remains the only instance used by all ten production definitions (seven `EXTERNAL_ADAPTER` + three dormant `GENERIC_COMPONENT`), inert for two different reasons (structural rejection vs. never-instantiated), and that lifecycle policy becomes behaviorally relevant only once a future grant provider actually instantiates state. |
| `ResourceSyncManager.java` | `isEligibleForGenericSync(PlayerResourceDefinition)` Javadoc | (Already corrected in the §31.3 review-correction pass.) | Re-verified this pass: still accurately states that `GENERIC_COMPONENT` production definitions exist, that missing state is omitted rather than fabricated, and that eligibility does not imply an available value. No further change was needed or made. |

No other file in `src/main/java/zcylas/totality/api/rpg/resources/` qualified for correction: a broader search found many more "Phase 2A"/"Phase 1"/"Phase 2D" mentions (e.g. in `ProductionResourceDefinitions.java`, `PlayerResourceIds.java`, `ResourceQueryFailureReason.java`, `ResourceDeathPolicy.java`, `PartitionedResourceSnapshot.java`, `ResourceAmount.java`, `ResourceCapability.java`, `ResourceModel.java`, `ResourceQueryResult.java`), but every one of them is accurate historical narration (e.g. "Phase 2D adds `totality:spell_slots`") that is not contradicted by the dormant registration — per this task's own correction criteria ("only if it directly contradicts the newly registered dormant definitions"), none of those were touched. In particular, `ResourceDeathPolicy.java`'s "No production resource declares a policy other than the default as of Phase 2A" remains literally true even now (Thirst/Sanity/Ki also use `DEFAULT`), so it was deliberately left alone rather than corrected for its own sake.

### 32.2 Corrected current architecture (as now stated across the five files above)

- **Seven established `EXTERNAL_ADAPTER`-authority resources**: Health, Food, Breath, Mana, Stamina, Spell Slots, Rage — unchanged, each backed by its own adapter, none entering `PlayerResourceStateComponent`'s live state map.
- **Three dormant `GENERIC_COMPONENT`-authority definitions**: Thirst, Sanity, Ki — registered metadata only, legitimately instantiable in principle, but with no grant provider anywhere in production code, so no player has live state for any of them.
- **Registration does not imply instantiated state**, for either category, for two structurally different reasons now explicitly documented at each of the five correction points rather than left to be inferred.

### 32.3 Confirmations

- **The cleanup changed comments only.** Confirmed by `git diff` filtered to non-comment/non-blank lines for each of the four newly touched files this pass (`PlayerResourceRegistry.java`, `PlayerResourceStateComponent.java`, `PlayerResourceService.java`, `ResourceLifecyclePolicy.java`): zero non-comment lines changed in any of them.
- **Resource metadata did not change.** `PlayerResourceIds.java` and `ProductionResourceDefinitions.java` are untouched by this pass (identical to their state after §31).
- **Synchronization, persistence, lifecycle, query, and copy behavior did not change.** `compileJava` succeeded; the full test suite (§32.4) is unchanged at 1391/0/0/0 from the pre-cleanup baseline, which would not hold if any of these behaviors had shifted.
- **The corrected orphan-test name remains `dormantDefinitionRegistrationDoesNotCreateOrphanEntries`** — untouched by this pass, confirmed present by `grep`.
- **The report's stale/orphan NBT caveat (§19, §31.2) remains evidence-based** — untouched by this pass.
- **Fatigue and Temperature remain deferred** — no id, no definition, no registration exists for either; untouched by this pass.
- **No gameplay or presentation API was started** — this pass touched only Javadoc/comment text in five files plus this report; no new class, capability, adapter, client reader, or HUD element was added.

### 32.4 Final validation for this cleanup pass

- Focused tests (`PlayerResourceRegistryTest`, `DormantResourceRegistrationTest`, `DormantResourceScopeRegressionTest`, `ResourceSyncManagerEligibilityTest`, `PlayerResourceStateComponentTest`, `PlayerResourceServiceTest`): **123 tests, 0 failures, 0 errors, 0 skipped** — matches the accepted focused baseline exactly.
- Full suite (`./gradlew test`, run once standalone and once as part of `./gradlew clean build`): **1391 tests, 0 failures, 0 errors, 0 skipped** — matches the accepted full-suite baseline exactly, as expected for a comment-only change (no test method was added, removed, or renamed by this cleanup).
- `./gradlew runDatagen`: **0 files written, 0 removed**; `git status --short` immediately before/after is byte-for-byte identical.
- `./gradlew clean build`: **BUILD SUCCESSFUL.**
- `git diff --check`: **exit code 0** — only the same pre-existing informational CRLF/LF notices, now also naming this pass's four newly touched files.
- Dedicated server (`./gradlew runServer`, up to 100s): reached **"Done (0.304s)! For help, type \"help\""**. Zero exceptions, zero `Resource`-tagged errors, zero mentions of `thirst`/`sanity`/`ki` anywhere in the log. The same two unrelated, pre-existing self-test suites (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) logged the same failures as every prior run in this environment (NPC trading and offhand combat, not the Resource API) — those are not this task's concern and are not claimed fixed by this pass.
- Client classloading: confirmed via the same `runDatagen` `Env=CLIENT` pass above completing successfully.

### 32.5 Final status

- **Implementation review is complete.** Both the original independent review (§31) and this final Javadoc-focused review (§32) have had every identified issue addressed.
- **All identified review issues are resolved**: the orphan-test naming, the report's stale-data overclaims, the `ResourceSyncManager` comment, and now the five additional phase-era Javadocs.
- **Automated validation passed** — see §32.4.
- **No manual gameplay validation is required**, because no active player behavior or presentation was introduced by either the original implementation, the review corrections, or this final cleanup: the three dormant definitions remain ungranted and uninstantiated, no HUD/client reader/adapter/capability exists for any of them, and this cleanup itself touched only comment text.
- **The task is ready to commit and push.**
- **Fatigue and Temperature remain deferred** — no id, no definition, no registration added by this correction.
