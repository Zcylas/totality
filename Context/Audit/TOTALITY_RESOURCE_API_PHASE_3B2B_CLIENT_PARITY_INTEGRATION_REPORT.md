# TOTALITY — Generic Player Resource API — Phase 3B-2B Implementation Report
## Client Shadow-Parity Integration

Scope actually implemented: the narrow Phase 3B-2B slice only — a pure poll orchestrator, a pure
generic-result mapper, a pure tick/lifecycle state machine, the real client-only legacy readers,
the real client-only coordinator wiring the poll into `END_CLIENT_TICK`, and a narrow read-only
observation view. No logging, no debug command, no HUD/UI, no consumer migration, no legacy bug
fixes, no Phase 3B-2C/3B-3/3C.

---

## 1. Starting branch and commit

- Branch: `feature/general-resource-api`.
- Starting `HEAD`: `4b3d7cef4b8b3ed7ecc045772994c15d319b43a5` ("Add client resource parity core") — confirmed matching the expected checkpoint before any change.
- Tracking `origin/feature/general-resource-api`, 0 ahead / 0 behind at start.
- Automated baseline confirmed before any change: `./gradlew test` returned `BUILD SUCCESSFUL` (`UP-TO-DATE`); summing every `build/test-results/test/*.xml`'s `tests`/`skipped`/`failures`/`errors` gave **672/672**, matching the expected Phase 3B-2A baseline exactly.
- No Phase 3B-2B class or integration hook existed at start — confirmed by search (`Coordinator`/`ParityPoll`/`ParityReader` name patterns, and the absence of a `TOTALITY_RESOURCE_API_PHASE_3B2B_*` report file).

---

## 2. Exact files created and modified

**Created (production, pure — `zcylas.totality.api.rpg.resources.client.parity`, alongside the existing committed Phase 3B-2A classes, none of which were modified):**
- `ClientResourceParityGenericAccess.java` — the generic-façade read seam (interface).
- `ClientResourceParityLegacyAccess.java` — the legacy-mirror read seam (interface).
- `ClientGenericParitySummaryMapper.java` — `ClientResourceQueryResult` → `ClientResourceParitySummary` mapper.
- `ClientResourceParityPoll.java` — the pure four-resource poll orchestrator.
- `ClientResourceParityLifecycle.java` — the pure tick-counter/player-identity lifecycle state machine.

**Created (production, client-only — new package `zcylas.totality.client.resource.parity`):**
- `ClientResourceParityCoordinator.java` (`@Environment(EnvType.CLIENT)`) — `tick()`/`clear()` entry points; implements `ClientResourceParityGenericAccess` itself (direct `ClientResourceService.INSTANCE` delegation).
- `LegacyClientResourceParityReaders.java` (`@Environment(EnvType.CLIENT)`) — implements `ClientResourceParityLegacyAccess`; reads `ClientManaManager`, `ClientStaminaManager`, `ClientSpellSlotManager`, the client-side `PlayerChargesComponent`, and `ClientClassManager`.
- `ClientResourceParityObservations.java` (`@Environment(EnvType.CLIENT)`) — the narrow read-only observation view.

**Modified:**
- `src/main/java/zcylas/totality/TotalityClient.java` — added one `END_CLIENT_TICK` registration (`ClientResourceParityCoordinator.tick()`) and three lifecycle-clear registrations (`JOIN`/`DISCONNECT`/`AFTER_CLIENT_LEVEL_CHANGE` → `ClientResourceParityCoordinator.clear()`), all appended after the existing `TotalityClientResourceReaders.register()` call. No other line changed.

**Created (tests):**
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientGenericParitySummaryMapperTest.java` (7 tests)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityPollTest.java` (10 tests)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityLifecycleTest.java` (13 tests)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/FakeClientResourceParityGenericAccess.java` (test-only fake, no `@Test` methods)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/FakeClientResourceParityLegacyAccess.java` (test-only fake, no `@Test` methods)
- `src/test/java/zcylas/totality/client/resource/parity/LegacyClientResourceParityReadersTest.java` (8 tests)
- `src/test/java/zcylas/totality/client/resource/parity/ClientResourceParityObservationsTest.java` (3 tests)

**Not touched:** every committed Phase 3B-2A pure class (`ClientResourceParityTracker`, `ClientResourceParityObservation`, `ClientResourceParitySummary`, `ClientResourceParityOutcome`, `ClientResourceParityClassification`, `ClientResourceScalarParityComparator`, `ClientSpellSlotParityComparator`, `ClientRageParityPolicy`) and their tests; every legacy manager (`ClientManaManager`, `ClientStaminaManager`, `ClientSpellSlotManager`, `PlayerChargesComponent`); every packet receiver (`TotalityClientPacketHandlers`); `ClientResourceSyncManager`/`ClientResourceSyncBridge`; `ClientClassManager`; every HUD/screen/radial/tooltip/movement class; `FormulaResolver`; `PlayerManaManager`; `ManaServerTick`; `PlayerConnectionEvents`; the two audit documents from Phase 3B-2A (`TOTALITY_RESOURCE_API_PHASE_3B2_SHADOW_PARITY_READINESS.md`, `TOTALITY_RESOURCE_API_PHASE_3B2A_PURE_PARITY_IMPLEMENTATION_REPORT.md`); `/Inspiration Mods`; and all "known unrelated" working-tree entries.

---

## 3. Phase 3B-2B scope

Integrates the committed Phase 3B-2A pure parity core with real client state, strictly
observationally: each of `totality:mana`/`totality:stamina`/`totality:spell_slots`/`totality:rage`
is read fresh from both the Phase 3B-1 generic façade and its legacy client mirror once per
`END_CLIENT_TICK`, compared via the already-committed pure comparators/policy, and recorded via
`ClientResourceParityTracker.observe(...)`. No logging, no debug command, no HUD/UI, no consumer
migration, and no legacy-bug fix of any kind is included — see §23/§27.

---

## 4. Generic façade mapping

`ClientGenericParitySummaryMapper.map(ClientResourceQueryResult)` (pure, no Minecraft/Fabric
dependency):
- `ClientResourceQueryResult.Scalar` → `ClientResourceParitySummary.Scalar(currentUnits, maximumUnits, overflowUnits, unitScale)`, `pendingResync = (trust == ClientResourceTrust.PENDING_RESYNC)`.
- `ClientResourceQueryResult.Partitioned` → `ClientResourceParitySummary.Partitioned` rebuilding every partition 1:1 (`partitionId`, `currentUnits`, `maximumUnits`, `overflowUnits`), same `pendingResync` extraction from `trust()`.
- `ClientResourceQueryResult.Unavailable` → `ClientResourceParitySummary.Unavailable(reason)` (the exact same `ClientResourceUnavailableReason`), `pendingResync = false` unconditionally — an unavailable result has no successful trust value to extract, confirmed by `ClientResourceTrust`'s own two-member enum (`FRESH`, `PENDING_RESYNC`) having no member for the unsynchronized case at all.
- Never fabricates a numeric value for `Unavailable` — proven by `mapperNeverFabricatesValuesForUnavailableResults` (`ClientGenericParitySummaryMapperTest`), which asserts the mapped summary is an `Unavailable` instance, structurally incapable of carrying current/maximum/overflow fields.

Uses exactly `ClientResourceService.INSTANCE.queryScalar(...)`/`queryPartitioned(...)` (via `ClientResourceParityCoordinator implements ClientResourceParityGenericAccess`) — never the untyped `query(...)`, never `PlayerResourceService.query(...)` client-side, never raw `ClientResourceSyncState`/`ClientResourceSyncManager.state()` access, never a second wire-snapshot parser. Confirmed by import inspection: `ClientResourceParityCoordinator.java` imports only `ClientResourceQueryResult` and `ClientResourceService` from the façade package.

---

## 5. Legacy Mana reader

`LegacyClientResourceParityReaders.manaSummary()` reads only `ClientManaManager.getMana()`/`getMaxMana()`, producing `Scalar(current, max, overflowUnits=0, unitScale=1)`. No clamping, no normalization, no write-back, no initialized flag, no join packet, no `FormulaResolver` fix, and `ClientManaManager.java` itself is byte-for-byte unchanged (confirmed: not in the changed-file list, §2). The known rune-cast (`FormulaResolver.tryCast`, which spends Mana without sending `SyncManaPayload`) and initial-join staleness (Mana has no dedicated join-time push, unlike Stamina) remain fully observable as ordinary `MISMATCH`/`TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH` outcomes — nothing in this reader or in `ClientRageParityPolicy`/`ClientResourceScalarParityComparator` special-cases Mana at all. Verified by `manaLegacySnapshotUsesCurrentMaxOverflowZeroScaleOne` (`LegacyClientResourceParityReadersTest`).

---

## 6. Legacy Stamina reader

`LegacyClientResourceParityReaders.staminaSummary()` reads only `ClientStaminaManager.getStamina()`/`getMaxStamina()`, producing the identical `Scalar` shape. `ClientStaminaManager.java` is unchanged; `TotalityMovementHandler`'s own gameplay-gating read of the same manager is untouched and unaware parity exists — parity remains diagnostic-only and never becomes movement authority. Verified by `staminaLegacySnapshotUsesCurrentMaxOverflowZeroScaleOne`.

---

## 7. Legacy spell-slot reader

`LegacyClientResourceParityReaders.spellSlotSummary()` reads only `ClientSpellSlotManager.getRemaining(level)`/`getMax(level)` for `level` in `ClientSpellSlotParityComparator.MIN_LEVEL..MAX_LEVEL` (1-10, reusing the comparator's own canonical range constants rather than a second hardcoded literal), producing a `Partitioned` summary with `currentUnits = getRemaining(level)` (never `getUsed`/an internal used-count) and `overflowUnits = 0` per level. Never reads or retains `ClientSpellSlotManager`'s internal mutable `maxSlots`/`usedSlots` arrays — only the two public accessor methods every existing consumer (`SpellRadialScreen`) already trusts. `ClientSpellSlotManager.java` is unchanged. Verified by `spellSlotLegacySnapshotContainsExactlyLevelsOneThroughTen`, `spellSlotCurrentEqualsRemainingNotUsed`, `spellSlotPartitionsAreAscendingAndImmutable`.

---

## 8. Legacy Rage reader

**Corrected 2026-07-25 — see §29.** `LegacyClientResourceParityReaders.rageSummary()` now always returns a `ClientResourceParitySummary` (never an `Optional`):
1. Reads `Minecraft.getInstance().player`; returns `Unavailable(NO_LOCAL_PLAYER)` if `null` — defensive representation, since the coordinator normally prevents polling in this condition.
2. Reads the client-side `PlayerChargesComponent` via `ChargeComponents.PLAYER_CHARGES.maybeGet((ComponentProvider) player)` — the safe, non-throwing lookup (never `.get(...)`, which throws if absent; never a broad `catch (Exception)` wrapping it). Returns `Unavailable(MALFORMED_SOURCE_STATE)` if the component itself is absent (a genuine structural anomaly, not the sparse-map "never granted" case, and not a signal the poll may skip this Resource).
3. Otherwise reads `component.getCurrent(BarbarianRageAbility.CHARGE_ID)`/`getMax(BarbarianRageAbility.CHARGE_ID)` — the established Rage charge-pool identifier, unchanged — and returns `Scalar(current, max, 0, 1)`.

No pool creation, no `ensurePool`/`registerPool` call, no `hasPool` accessor added, no mutation of any kind, and `PlayerChargesComponent.java`/its synchronization are byte-for-byte unchanged. The known live maximum-update gap (`PlayerChargesComponent.applySyncPacket` not updating an already-existing pool's maximum) is left completely unfixed so parity can observe it — nothing in this reader or elsewhere in Phase 3B-2B special-cases or repairs it. `rageSummary()` itself is not exercised by an automated test in this slice (it requires a real `Minecraft.getInstance().player` and attached component) — confirmed correct only by direct source inspection here and reserved for the manual validation checklist (§22).

---

## 9. Rage expectation derivation

`LegacyClientResourceParityReaders.rageExpectedForPlayer()` returns `ClientClassManager.hasClass() && TotalityClasses.BARBARIAN_ID.equals(ClientClassManager.getPrimaryClassId())` — the exact same check already precedented in production code for the Rage HUD pip bar's own visibility gate (`TotalityClient.java`'s `ISecondaryResource` registration, confirmed unchanged at that call site). `ClientClassManager` is read only inside this client-only reader class and is never imported by, or passed into, `ClientRageParityPolicy` or any other pure parity class — the pure policy receives only the resulting `boolean`. This class check is diagnostic context only and has no gameplay effect anywhere in this slice. Verified by `rageExpectedTrueForBarbarian`, `rageExpectedFalseForAnotherClass`, `rageExpectedFalseWhenNoClassIsPresent` — all three run without a Minecraft client, since `ClientClassManager` itself has zero Minecraft-client imports.

---

## 10. Four-resource `END_CLIENT_TICK` polling

`ClientResourceParityCoordinator.tick()` (called once per `END_CLIENT_TICK` from `TotalityClient.java`): reads `Minecraft.getInstance().player`, delegates to `ClientResourceParityLifecycle.beginTick(player)`, and — only when that returns a present tick (i.e. a local player exists) — calls `ClientResourceParityPoll.pollOnce(TRACKER, currentTick, coordinator, legacyReaders)`. `pollOnce` always performs exactly four generic façade queries, four legacy reads, four pure comparisons, and — **corrected 2026-07-25, see §29** — always exactly four `tracker.observe(...)` calls, unconditionally, including when the legacy Rage source is structurally unavailable. Bounded, intentionally non-optimized work, never reduced to update-notification hooks in this slice. Verified end-to-end (without booting Minecraft) by `onePollComparesExactlyFourResourceIds`, `pollDispatchesScalarComparatorForMana`/`...ForStamina`, `pollDispatchesSpellSlotComparatorForSlots`, `pollDispatchesRagePolicyForRage`, `pollSubmitsFreshSummariesToTracker`, `pollPassesPendingResyncToTracker`, `onePollPerformsExactlyFourGenericQueriesNeverMoreNeverFewer`, `onePollPerformsEachLegacyReadExactlyOnce` (`ClientResourceParityPollTest`).

---

## 11. Poll order

Confirmed via direct inspection of `TotalityClient.onInitializeClient()`: `ClientResourceSyncManager.tick()` is registered against `END_CLIENT_TICK` at the existing call site (unchanged); the new `ClientResourceParityCoordinator.tick()` registration is appended strictly afterward, at the end of the method. Fabric's `Event<T>` invokes `END_CLIENT_TICK` listeners in registration order, so on every real tick: (1) `ClientResourceSyncManager`'s own tick/resync handling (already-applied packets, revision-gap retry) runs first; (2) parity polls the already-settled façade and legacy state second. No packet ordering was changed; Resource packet application was not moved into any tick callback — both remain exactly where Phase 3A/3B-1 left them.

---

## 12. Tick-counter semantics

`ClientResourceParityLifecycle<P>` (generic over the caller's identity-token type; the real coordinator instantiates it as `ClientResourceParityLifecycle<LocalPlayer>`):
- **The first poll after construction (or after `clear()`) uses tick `0`, not `1`** — documented explicitly in the class Javadoc and proven by `firstPollUsesTickZero` (`ClientResourceParityLifecycleTest`). This choice preserves the already-committed tracker's own T/T+1/T+2 off-by-one semantics unchanged: `preservesEstablishedTrackerOffByOneSemanticsAcrossRealPolls` drives three real `beginTick`/`observe` pairs and confirms `TRANSITIONAL_MISMATCH` at tick 0, still `TRANSITIONAL_MISMATCH` at tick 1, `PERSISTENT_MISMATCH` at tick 2 — identical to the tracker's own committed unit tests.
- Advances by exactly one per poll (`tickIncrementsOncePerPoll`) — never derived from world time, server tick time, or wall-clock milliseconds; the counter is a private `long` field incremented only inside `beginTick`.
- Overflow-protected via a package-private `safeIncrementTick(long)` static helper (mirroring `ClientResourceParityTracker.saturatingIncrement`'s exact established pattern): holds at `Long.MAX_VALUE` rather than wrapping negative — proven directly by `tickOverflowIsSafelyHandled`, exercising the boundary without driving an unreasonable number of real ticks.
- Never decreases within one lifecycle: the counter only ever increments or resets to `0` via `clear()`, never decrements.

---

## 13. Lifecycle resets

Three explicit lifecycle triggers, all calling `ClientResourceParityCoordinator.clear()` → `ClientResourceParityLifecycle.clear()` → `tracker.clearAll()` + tick counter reset to `0` + player-identity forgotten:
- `ClientPlayConnectionEvents.JOIN` (registered in `TotalityClient.java`, alongside the existing `ClientResourceSyncManager.clear()` JOIN registration — a separate, independent hook; `ClientResourceSyncManager` itself was not modified to depend on parity in any way).
- `ClientPlayConnectionEvents.DISCONNECT` (same pattern).
- `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE` (same pattern — covers dimension changes that fire neither JOIN nor DISCONNECT).

Proven at the pure-lifecycle level (a stand-in for each of these three real Fabric events, since the actual event registration itself cannot be unit-tested without a client) by `joinResetEquivalentClearsObservations`, `disconnectResetEquivalentClearsObservationsAndTickCounter`, `dimensionChangeResetEquivalentClearsObservations`.

---

## 14. LocalPlayer replacement detection

`ClientResourceParityLifecycle.beginTick(P player)` compares `player` against the previously-seen reference using `!=` (never `.equals()`/UUID) — a respawn produces a new `LocalPlayer` object that retains the same UUID but is a different reference, and the task's explicit instruction is that this replacement must still reset parity state. On a reference change, `clear()` is called and the new reference becomes the baseline (a subsequent poll at that same call resumes from tick `0`). On an identical reference, no reset occurs and the tick counter continues uninterrupted. Proven by `localPlayerObjectReplacementClearsObservations` (two distinct `Object` references — the second poll after the swap returns tick `0` and the prior observation is gone) and `sameLocalPlayerObjectDoesNotClearObservations` (same reference across two `beginTick` calls — tick continues to `1`, prior observation survives). The `null`-transition and repeated-null-idle behaviors are covered by `nullPlayerEntersIdleStateWithoutPolling`, `repeatedNullPlayerTicksDoNotRepeatedlyResetUnnecessarily`, `newPlayerAfterNullStateStartsFromACleanBaseline`.

---

## 15. Comparator dispatch

`ClientResourceParityPoll.pollOnce` dispatches:
- Mana, Stamina → `ClientResourceScalarParityComparator::compare` (a method reference, not a re-implementation).
- Spell slots → `ClientSpellSlotParityComparator.compare(...)` (direct call).
- Rage → `ClientRageParityPolicy.compare(rageExpectedForPlayer, generic, legacy)` (direct call). **Corrected 2026-07-25 (see §29):** `legacy` can now itself be `ClientResourceParitySummary.Unavailable`, which the policy classifies as `MODEL_MISMATCH` (after the generic `NOT_SYNCHRONIZED_YET` readiness gate, which still takes precedence) — a structurally unavailable legacy mirror is never suppressed as `EXPECTED_SEMANTIC_DIFFERENCE`, an ordinary mismatch, or an exact match.

No classification (`EXACT_MATCH`/`MODEL_MISMATCH`/`EXPECTED_SEMANTIC_DIFFERENCE`/mismatch outcome) is ever assigned manually inside `ClientResourceParityPoll` — confirmed by direct inspection: the class contains zero references to any `ClientResourceParityOutcome`/`ClientResourceParityClassification` enum constant anywhere in its body; every `ClientResourceParityOutcome` value used comes from a comparator/policy call's return value. The coordinator owns only obtaining fresh inputs, selecting the correct comparator, and supplying tick/pending-resync metadata to `tracker.observe(...)`.

---

## 16. `PENDING_RESYNC` propagation

`ClientGenericParitySummaryMapper.MappedResult.pendingResync()` is threaded unconditionally through every one of `pollOnce`'s four `tracker.observe(...)` calls, regardless of the comparator outcome — the tracker itself only actually consumes the flag inside its `MISMATCH`-handling branch (per the already-committed, unchanged `ClientResourceParityTracker.applyMismatch`), so passing it uniformly for every outcome is harmless and requires no special-casing in the polling code. Proven end-to-end by `pollPassesPendingResyncToTracker`, which asserts `graceFrozen()` is `true` on the resulting observation after a `PENDING_RESYNC`-trust generic result produces a mismatch.

---

## 17. Observation access boundary

`ClientResourceParityObservations` (client-only, same package as the coordinator) exposes exactly two public static methods — `latest(Identifier)` and `snapshot()` — both delegating to `ClientResourceParityCoordinator.tracker()`, a package-private accessor (mirroring `ClientResourceSyncManager.state()`'s established package-private pattern) that no class outside this package can reach. `snapshot()` builds a fresh `LinkedHashMap` from the four known tracked resource ids (`PlayerResourceIds.MANA`/`STAMINA`/`SPELL_SLOTS`/`RAGE`) each call, wrapped in `Collections.unmodifiableMap` — never a live view into the tracker's own backing map (the committed `ClientResourceParityTracker` has no method to enumerate its own keys at all, and none was added — see §19 for why this was not a blocker). Verified structurally by `exposesOnlyLatestAndSnapshotAsPublicApi`, `declaresNoMutatingMethod`, `coordinatorTrackerAccessorIsNotPublic` (`ClientResourceParityObservationsTest`) — all three run via reflection on method signatures only, without invoking any Minecraft-dependent behavior. No gameplay consumer, HUD, or screen reads either accessor; no logging, no command, no packet export, no persistence exists for this data in this slice. Reserved for **Phase 3B-2C**'s bounded diagnostics and **Phase 3B-3**'s verification tooling, neither of which is built here.

---

## 18. Client/server classloading boundary

- `ClientResourceParityCoordinator`, `LegacyClientResourceParityReaders`, `ClientResourceParityObservations` all carry `@Environment(EnvType.CLIENT)` — confirmed present on all three files.
- None of the 5 new pure classes (`ClientResourceParityGenericAccess`, `ClientResourceParityLegacyAccess`, `ClientGenericParitySummaryMapper`, `ClientResourceParityPoll`, `ClientResourceParityLifecycle`) carries `@Environment` or imports anything beyond `net.minecraft.resources.Identifier`, `java.util.*`, and other pure `zcylas.totality.api.rpg.resources(.client(.parity))` types — confirmed by direct `grep "^import"` inspection of all five files (§ validation results below).
- No common initializer (`Totality.onInitialize()`, `ModEvents.register()`, any server-side registration path) references `ClientResourceParityCoordinator`, `LegacyClientResourceParityReaders`, or `ClientResourceParityObservations` — confirmed by a repository-wide grep for all three class names outside their own package and outside `TotalityClient.java`, which returned zero code references (only two unrelated Javadoc `{@code ...}` mentions inside the pure interfaces' own documentation, not imports or calls).
- The single new wiring call site is `TotalityClient.onInitializeClient()` — the existing `ClientModInitializer` entrypoint, the same place every other Phase 3B-1/3B-2A-era client-only registration already lives.
- Dedicated-server classloading was **not** empirically re-verified by actually starting a dedicated server in this session — confirmed only by the source/import inspection above, per the task's explicit instruction not to claim runtime dedicated-server validation without actually starting one. This remains on the manual validation checklist (§22, item 15).

---

## 19. Error-handling policy

- **Local player absent**: `ClientResourceParityLifecycle.beginTick(null)` returns `OptionalLong.empty()`; the coordinator's `tick()` simply does not poll that tick. No exception, no fabricated value, no repeated reset (only the null-transition itself resets).
- **Generic unavailable**: represented through the existing `ClientResourceQueryResult.Unavailable`/`ClientResourceParitySummary.Unavailable` shapes — no new failure representation was invented.
- **Rage component genuinely absent (corrected 2026-07-25, see §29)**: `LegacyClientResourceParityReaders.rageSummary()` returns `ClientResourceParitySummary.Unavailable(MALFORMED_SOURCE_STATE)` — never an `Optional.empty()`, and never a signal to skip. `ClientResourceParityPoll.pollRage` always queries the generic side and always calls `tracker.observe(...)`; `ClientRageParityPolicy` classifies a legacy-`Unavailable` comparison as `MODEL_MISMATCH`, replacing any stale prior Rage observation rather than leaving it untouched. No broad `catch (Exception)` exists anywhere in this reader — the safe `ChargeComponents.PLAYER_CHARGES.maybeGet(...)` lookup is used instead of the throwing `.get(...)`, so no exception is ever expected to occur in the first place.
- **No broad catch-all anywhere in this slice**: confirmed by direct inspection — no `catch (Exception` (or broader) appears in any of the 8 new production files. A genuine unexpected programming error (e.g. a `ClassCastException` from a future accidental misuse) remains visible during development rather than being silently converted into an exact match.
- **No logging, no chat, no repeated-log coalescing**: none of this exists in Phase 3B-2B at all (see §24).

---

## 20. Tests added

41 new tests across 7 files as originally implemented (672 pre-existing + 41 new = 713 total, all passing). **Superseded by the 2026-07-25 external-review correction pass — see §29 for the corrected counts** (16/16/8 respectively for `ClientResourceParityPollTest`/`ClientRageParityPolicyTest`/`ClientResourceParityObservationsTest`, plus `LegacyClientResourceParityReadersTest` gaining `@AfterEach` cleanup with no new `@Test` methods); the table below is preserved as the original as-implemented snapshot:

| File | Count | Covers (test-plan items) |
|---|---|---|
| `ClientGenericParitySummaryMapperTest` | 7 | Items 1-6 |
| `ClientResourceParityPollTest` | 10 (→ 16, §29) | Items 17-25 |
| `ClientResourceParityLifecycleTest` | 13 | Items 26-36 |
| `LegacyClientResourceParityReadersTest` | 8 | Items 7-11, 14-16 |
| `ClientResourceParityObservationsTest` | 3 (→ 8, §29) | Items 37-39 |
| `FakeClientResourceParityGenericAccess` | 0 (support class) | — |
| `FakeClientResourceParityLegacyAccess` | 0 (support class) | — |

Note: `ClientRageParityPolicyTest` (13 → 16, §29) is a Phase 3B-2A committed test file, not originally listed in this Phase 3B-2B table — it gained 3 new tests as part of the 2026-07-25 correction to the committed `ClientRageParityPolicy` class.

Items 12-13 (Rage snapshot's exact charge-pool identifier usage and read-only nature) are confirmed only by direct source inspection (§8) — `rageSummary()` requires a real `Minecraft.getInstance().player` and attached `PlayerChargesComponent`, consistent with the established Phase 3B-1 precedent that a genuine client-bootstrap-dependent read is untestable without a running client.

Items 40-47 are confirmed by inspection/full-suite run rather than a dedicated new test:
- **40** (no production consumer reads parity observations): confirmed by repository-wide grep for `ClientResourceParityObservations` usage — zero call sites outside its own test file.
- **41** (no legacy manager modified): confirmed by the changed-file list (§2) — none of the four legacy managers appears.
- **42** (no packet receiver modified): `TotalityClientPacketHandlers.java` is not in the changed-file list.
- **43** (no server/common initializer references client parity integration): §18.
- **44/45** (no logging/no packet send in any Phase 3B-2B class): §19, confirmed by grep.
- **46** (no HUD/screen/radial/tooltip/movement class changed): confirmed by the changed-file list — only `TotalityClient.java` (registration only) was modified among client-facing files.
- **47** (existing 672 tests remain passing): confirmed by the fresh 713-test run in §21 (672 unchanged + 41 new).

---

## 21. Validation results

Run fresh from `feature/general-resource-api`, in order:

- **`compileJava`/`compileTestJava`**: `BUILD SUCCESSFUL`. No compiler warnings.
- **`test`** (forced `--rerun`): `BUILD SUCCESSFUL` on the first run (no failures encountered during this integration). **Exact total: 713 tests, 0 failed, 0 errors, 0 skipped** (summed directly across every `build/test-results/test/*.xml`'s `tests`/`skipped`/`failures`/`errors` attribute).
- **New tests added**: 41, confirmed by `grep -c '@Test'` across the five test files that contain any; 713 − 41 = 672, matching the Phase 3B-2A (post-correction) baseline exactly.
- **`runDatagen`**: `BUILD SUCCESSFUL`. `total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0` — zero generated-file impact, as expected for a pure-Java/client-registration-only change.
- **`git status --short src/main/generated`** after `runDatagen`: the identical 21 pre-existing modified files (worldgen noise settings, loot tables, gear recipes) plus the untracked `.cache/` directory — no new or additionally-changed generated file.
- **`build`**: `BUILD SUCCESSFUL`. Only pre-existing Gradle/Loom deprecation notices (Gradle 10 compatibility warning), present before this session, unrelated to this change.
- **`git diff --check -- src/main/java src/test/java`**: exit 0, no whitespace errors.
- **Classloading/import boundary checks** (§18): all 5 pure classes confirmed to import only `Identifier`/`java.util.*`/other pure parity types; all 3 client-only classes confirmed to carry `@Environment(EnvType.CLIENT)`; zero common/server code references to any of the three client-only classes.

No warning of any kind (compiler, Gradle, Loom, datagen, test, build) beyond the pre-existing Gradle/Loom deprecation boilerplate was observed anywhere in this session.

---

## 22. Manual validation checklist — explicitly NOT yet completed

The following has **not** been performed in this session and must not be claimed as validated:

1. Start a client and join a world as a Barbarian.
2. Confirm the coordinator polls all four IDs (e.g. via a temporary breakpoint on `ClientResourceParityPoll.pollOnce`).
3. Confirm initial observations become available (`ClientResourceParityObservations.snapshot()` non-empty after the first tick with a local player).
4. Confirm stable Stamina reports `EXACT_MATCH`.
5. Confirm stable spell slots report `EXACT_MATCH`.
6. Confirm stable Rage reports `EXACT_MATCH` where appropriate.
7. Spend/regenerate Stamina and observe transitional-then-exact behavior.
8. Spend/restore Rage and observe parity.
9. Reconnect and confirm observations reset and rebuild.
10. Change dimension and confirm observations reset and rebuild.
11. Die/respawn and confirm `LocalPlayer` replacement resets observations.
12. Confirm no parity message appears in the log, chat, HUD, toast, or any screen.
13. Exercise rune-based Mana spending (`FormulaResolver.tryCast`) and determine whether the known legacy staleness becomes a persistent mismatch.
14. Exercise a Rage-maximum change where practical (e.g. a Barbarian class level-up mid-session) and determine whether the known maximum-sync gap becomes a persistent mismatch.
15. Start a dedicated server and confirm no client parity classloading failure (`NoClassDefFoundError`/`ClassNotFoundException` referencing any Phase 3B-2B client-only class).

The known mismatches (§23) must be **recorded**, not fixed, during this manual pass.

---

## 23. Known legacy discrepancies intentionally left unfixed

1. `FormulaResolver.tryCast` (`api/magic/grimoire/context/FormulaResolver.java`) spends Mana via `PlayerManaManager.removeMana(...)` without sending the legacy `SyncManaPayload` — confirmed unchanged; `FormulaResolver.java` is not in the changed-file list (§2).
2. Mana has no dedicated join-time legacy synchronization (unlike Stamina's `StaminaServerTick.syncStamina(player)` call inside `PlayerConnectionEvents`'s `JOIN` handler) — confirmed unchanged; `PlayerConnectionEvents.java` is not in the changed-file list.
3. `PlayerChargesComponent.applySyncPacket` does not update the maximum of an already-existing client-side charge pool (only `current` is applied via `existing.withCurrent(current)`) — confirmed unchanged; `PlayerChargesComponent.java` is not in the changed-file list.

None of these is pre-classified as `EXPECTED_SEMANTIC_DIFFERENCE` anywhere in this integration — `ClientRageParityPolicy`/`ClientResourceScalarParityComparator` are the committed, unmodified Phase 3B-2A classes, and nothing in `ClientResourceParityPoll`/the legacy readers adds any special-casing for these three scenarios. They remain ordinary `MISMATCH` candidates and may become `PERSISTENT_MISMATCH` once observed in a live session (§22, items 13-14).

---

## 24. Confirmation that no logging exists yet

Confirmed by direct grep across all 8 new production files (§21) — zero occurrences of `LOGGER`, `Logger`, `System.out`, or `System.err`. No log statement of any kind, at any level, exists anywhere in Phase 3B-2B.

---

## 25. Confirmation that no consumer migration occurred

`TotalityHudRenderer`, `OverviewTab`, `ClassTab`, `SpellRadialScreen`, `TotalityClient`'s `ISecondaryResource` registration, and `TotalityMovementHandler` are all untouched — confirmed by the changed-file list (§2), which contains only `TotalityClient.java`'s new registration block (appended, not modifying any existing line) among client-facing files. No existing consumer was changed to read `ClientResourceParityObservations`, `ClientResourceService`, or any parity type.

---

## 26. Phase 3B-2C handoff

- `ClientResourceParityObservations.latest(Identifier)`/`.snapshot()` are ready to be consumed by Phase 3B-2C's bounded diagnostics (log-level, coalesced persistent-mismatch reporting) without any further plumbing — the read boundary already exists and is exercised structurally by this slice's own tests.
- Phase 3B-2C should decide the exact log level/coalescing policy (already discussed at length in the Phase 3B-2 readiness audit and the Phase 3B-2A implementation report's correction section) and implement it as a new, separate observer of `ClientResourceParityObservations`'s snapshot — not by modifying `ClientResourceParityPoll`/`ClientResourceParityTracker`/the coordinator.
- The two intentionally-unfixed legacy gaps (§23) are likely to be the first real `PERSISTENT_MISMATCH` entries a live session produces — Phase 3B-2C/3B-3 should expect and correctly attribute them, not treat them as new defects.

---

## 27. Explicit out-of-scope list

Per the task's constraints, confirmed as understood and respected throughout this implementation:
- No parity logging, mismatch coalescing, or diagnostic formatting.
- No debug command, parity HUD/UI, packet export, telemetry, or persistence.
- No Phase 3B-2C, Phase 3B-3, or Phase 3C work.
- No consumer migration or authority migration.
- No legacy manager cleanup.
- No fix for the missing Mana synchronization, the Rage maximum synchronization gap, the ancestry dimension bug, the Provisioner verification bug, the offhand Stamina bug, or the Food/Hunger or Exhaustion/Winded redesigns.
- No modification of any Phase 3B-2A pure class's accepted semantics **as originally implemented** — all eight committed classes remained byte-for-byte as committed at that time. **Superseded 2026-07-25 (see §29):** the external review identified a genuine Phase 3B-2B integration blocker (the silent Rage-skip defect) that could only be correctly resolved by also classifying a structurally unavailable legacy Rage summary in `ClientRageParityPolicy` — one of the eight committed classes. That class (and its committed test, `ClientRageParityPolicyTest`) were deliberately, narrowly corrected; the other seven committed classes remain byte-for-byte unchanged.

---

## 28. Confirmation: unrelated files and `/Inspiration Mods` untouched

- `/Inspiration Mods` was not read, searched, or referenced at any point during this implementation.
- The pre-existing "known unrelated" working-tree entries (modified generated datagen JSON under `src/main/generated/data/**`, `Context/Trading Test/*.png`, `logs/`, `src/main/generated/.cache/`, all prior-phase review ZIPs under `Context/Audit/Review Bundles/`) were not modified, staged, committed, reset, restored, or cleaned — `git status --short` before and after this implementation shows the identical set of these entries, plus this session's own new/modified files.
- Both Phase 3B-2A audit documents (`TOTALITY_RESOURCE_API_PHASE_3B2_SHADOW_PARITY_READINESS.md`, `TOTALITY_RESOURCE_API_PHASE_3B2A_PURE_PARITY_IMPLEMENTATION_REPORT.md`) were preserved exactly as committed; this report is a new, separate file.

---

## 29. External-review correction — 2026-07-25

The external review of the Phase 3B-2B final review bundle accepted the overall architecture
(generic/legacy read seams, the generic-result mapper, the four-resource pure poll orchestrator,
elapsed-tick lifecycle state, the client-only coordinator, `END_CLIENT_TICK` wiring, the
observation façade, lifecycle clearing, the client/server classloading split, and the absence of
logging/consumer migration) but identified one production blocker and several test/report gaps.

### 29.1 The silent Rage-skip defect

As originally implemented, `ClientResourceParityLegacyAccess.rageSummary()` returned an
`Optional<ClientResourceParitySummary>`, and `ClientResourceParityPoll.pollRage` returned early
(no generic query, no comparison, no `tracker.observe` call) whenever that `Optional` was empty —
i.e. whenever the local player or `PlayerChargesComponent` was structurally absent. This violated
the Phase 3B-2B contract that every valid-player poll freshly processes all four Resources
(`totality:mana`/`totality:stamina`/`totality:spell_slots`/`totality:rage`) every tick. Concretely,
this meant a Rage observation recorded before the structural anomaly began (e.g. a stale
`EXACT_MATCH` from an earlier tick) could remain visible via `ClientResourceParityObservations`
indefinitely — silently, with no indication anything had gone wrong. A stale, silently-surviving
observation is a worse failure mode than an explicit fresh classification of the anomaly itself.

### 29.2 Corrected Rage access model

`ClientResourceParityLegacyAccess.rageSummary()` now always returns a `ClientResourceParitySummary`
— the `Optional` return type and import were removed entirely from the interface and from
`LegacyClientResourceParityReaders`. The real implementation now returns:
- `Unavailable(NO_LOCAL_PLAYER)` when `Minecraft.getInstance().player` is `null` (defensive — the
  coordinator normally prevents polling in this condition in the first place);
- `Unavailable(MALFORMED_SOURCE_STATE)` when the player exists but `PlayerChargesComponent` is
  absent (a genuine structural legacy-source failure, not the sparse-map "never granted" case);
- the existing `Scalar(current, maximum, 0, 1)` when the component exists, exactly as before.

No pool creation, no `hasPool` accessor, no component mutation, no `catch (Exception)`, and no fix
to the known Rage-maximum synchronization gap (`PlayerChargesComponent.applySyncPacket` not
updating an already-existing pool's maximum) — none of that changed.

### 29.3 `ClientRageParityPolicy` correction (committed Phase 3B-2A class)

`ClientRageParityPolicy.compare(...)` now explicitly handles a `legacy` argument that is itself
`ClientResourceParitySummary.Unavailable`. The corrected ordering:
1. Generic `NOT_SYNCHRONIZED_YET` → `GENERIC_NOT_READY` (unchanged — still the first check, still
   takes precedence over every other condition, including a structurally unavailable legacy side).
2. Legacy `Unavailable` (any reason) → `MODEL_MISMATCH` (new) — a structurally unavailable legacy
   mirror can never be meaningfully compared, so it is never suppressed as
   `EXPECTED_SEMANTIC_DIFFERENCE`, an ordinary numeric mismatch, or an exact match.
3. The established generic Rage-absence-expectation reasoning (non-Barbarian plus a genuine,
   well-formed legacy `0/0` → `EXPECTED_SEMANTIC_DIFFERENCE`; otherwise `MISMATCH`) is unchanged and
   now only ever runs once `legacy` is known to be a genuine, comparable `Scalar`.
4. A present generic and legacy scalar Rage value still flows through
   `ClientResourceScalarParityComparator` unchanged.

No new `ClientResourceParityClassification`/`ClientResourceParityOutcome` value was added — the
correction is entirely a matter of dispatch ordering within the existing vocabulary. This is the
one Phase 3B-2A committed class modified in this correction pass; the other seven remain
byte-for-byte identical to the base commit (`4b3d7cef4b8b3ed7ecc045772994c15d319b43a5`), confirmed
by `git diff` returning empty output against each.

### 29.4 `ClientResourceParityPoll.pollRage` correction

The early return was removed. `pollRage` now unconditionally: reads the legacy Rage summary, queries
the generic façade for `totality:rage`, maps the result, invokes `ClientRageParityPolicy`, and calls
`tracker.observe(...)` exactly once — identical in shape to `pollScalar`'s Mana/Stamina handling. A
structural Rage-source failure now replaces any prior Rage observation with a fresh `MODEL_MISMATCH`
observation rather than leaving stale tracker state untouched. All Javadoc/comments in
`ClientResourceParityPoll`, `ClientResourceParityLegacyAccess`, and
`LegacyClientResourceParityReaders` claiming Rage "may be skipped" were corrected (see §8, §10, §15,
§19 above, each now cross-referencing this section).

### 29.5 Test-seam strengthening

`FakeClientResourceParityGenericAccess` gained a bounded, test-only invocation history (`Invocation`
record: query kind + resource id, in call order) with `invocations()`, `scalarQueryCount(id)`,
`partitionedQueryCount(id)`, and `clearInvocations()` accessors. `FakeClientResourceParityLegacyAccess`
gained per-method call counters (`manaCallCount()`, `staminaCallCount()`, `spellSlotCallCount()`,
`rageCallCount()`, `rageExpectedCallCount()`, `clearCallCounts()`) and switched `setRage(...)` from
accepting an `Optional` to accepting a `ClientResourceParitySummary` directly, plus a new
`setRageUnavailable(reason)` convenience method. Neither fake is referenced by any production code —
both remain test-only, in the existing test source set.

### 29.6 Tests added/replaced

`rageAbsentLegacyPollIsSkippedRatherThanFabricated` (encoded the now-rejected skip behavior) was
removed and replaced with `rageUnavailableLegacyPollIsObservedAsModelMismatch`. New tests added
across three files (14 net new `@Test` methods; final counts confirmed from live source):

| File | Before | After | Delta |
|---|---|---|---|
| `ClientResourceParityPollTest` | 10 | 16 | +6 |
| `ClientRageParityPolicyTest` (Phase 3B-2A committed) | 13 | 16 | +3 |
| `ClientResourceParityObservationsTest` | 3 | 8 | +5 |
| `LegacyClientResourceParityReadersTest` | 8 | 8 | 0 (gained `@AfterEach` cleanup only) |

New coverage includes: legacy-Unavailable → `MODEL_MISMATCH`; generic-not-ready still takes
precedence over a legacy-Unavailable; the generic Rage query is still performed when legacy Rage is
unavailable; a prior Rage observation is replaced (not left stale) when the legacy source becomes
unavailable; exactly four generic queries (one scalar Mana, one scalar Stamina, one partitioned
spell-slots, one scalar Rage) per poll, via the new invocation history; exactly one call to each of
the five `ClientResourceParityLegacyAccess` methods per poll, via the new call counters; successive
polls perform fresh reads rather than reusing a stored summary (proven by changing both sides
between two polls and confirming the classification and call counts both update); and behavioral
(not just reflection-based) tests of `ClientResourceParityObservations.latest(...)`/`.snapshot()` —
seeded-value retrieval, snapshot contents, snapshot immutability (`UnsupportedOperationException` on
mutation attempt), and snapshot detachedness from later tracker changes, each via the same-package
`ClientResourceParityCoordinator.tracker()` accessor with no `Minecraft.getInstance()` call anywhere
in the new tests.

`LegacyClientResourceParityReadersTest` gained a `@AfterEach resetStaticLegacyState()` method that
restores `ClientManaManager`/`ClientStaminaManager` to 100/100, `ClientSpellSlotManager` to
all-zero max/used arrays, and `ClientClassManager` to no classes/no primary/no subclass/no origin
(via its existing public `apply(Map.of(), null, null)` path) after every test, so static-holder
mutation in one test can never leak into another regardless of JUnit execution order. This method
runs even when a test's own assertion fails, since JUnit always invokes `@AfterEach`.

No existing Phase 3B-2A tracker, comparator (other than the one described in §29.3), lifecycle, or
mapping test was weakened or removed.

### 29.7 Final validation (this correction pass)

Run fresh, in order, from `feature/general-resource-api` at the same starting `HEAD`
(`4b3d7cef4b8b3ed7ecc045772994c15d319b43a5`) with the Phase 3B-2B work still uncommitted:
- **Pre-correction baseline** (`test --rerun`): `BUILD SUCCESSFUL`, **713 tests, 0 failed, 0 errors,
  0 skipped** — confirmed identical to the Phase 3B-2B final review bundle's own recorded baseline
  before any correction was made.
- **`compileJava`**: `BUILD SUCCESSFUL`, no errors.
- **`compileTestJava`**: `BUILD SUCCESSFUL`, no errors (after updating the two fakes and the one
  existing test call site affected by the `Optional`-to-`ClientResourceParitySummary` interface
  change).
- **`test --rerun`** (post-correction, first full run after all corrections and new tests): one
  failure on the first attempt (`rageUnavailableLegacyPollIsObservedAsModelMismatch` — the test
  itself left the generic Rage entry unconfigured, so the fake's `NOT_SYNCHRONIZED_YET` default
  correctly took precedence over the legacy-unavailable check per §29.3's ordering rule 1; the test
  was corrected to configure a genuinely present generic value, not a production defect). Second run
  after that test fix: `BUILD SUCCESSFUL`, **727 tests, 0 failed, 0 errors, 0 skipped**
  (713 + 14 net new correction tests).
- **`runDatagen`**: `BUILD SUCCESSFUL`. `total files: 349, old count: 349, new count: 349, removed
  stale: 0, written: 0` — identical to the pre-correction baseline, zero generated-file impact.
- **`build`**: `BUILD SUCCESSFUL`. Only the pre-existing Gradle/Loom deprecation notice (unrelated,
  present before this session).
- **`git diff --check -- src/main/java src/test/java`**: exit 0. The only output was two
  informational `autocrlf`-driven "LF will be replaced by CRLF" notices for the two files edited in
  this pass that originated as Phase 3B-2A committed content (`ClientRageParityPolicy.java`,
  `ClientRageParityPolicyTest.java`) — not whitespace errors, and consistent with this repository's
  established `core.autocrlf=true` convention affecting every edited file, not specific to this
  change.

### 29.8 Confirmations

- No logging was added anywhere in this correction (re-confirmed by grep across all touched files).
- No debug command, HUD, screen, or consumer reads any parity type — unchanged from Phase 3B-2B.
- No packet handling changed; no legacy sync sender/receiver changed; `PlayerChargesComponent.java`,
  `ClientManaManager.java`, `ClientStaminaManager.java`, `ClientSpellSlotManager.java`,
  `ClientClassManager.java` all remain byte-for-byte unchanged (confirmed via `git diff --stat`
  against each, empty output).
- No gameplay behavior changed and no visual behavior changed — this correction touches only the
  pure/client-only parity classes and their tests.
- The known Mana staleness/join-sync gap and the Rage-maximum synchronization gap remain completely
  unfixed and observable — neither `FormulaResolver.java` nor `PlayerConnectionEvents.java` nor
  `PlayerChargesComponent.java` was touched (confirmed via `git diff --stat`, all empty).
- No manual validation was performed at any point in this correction pass.
- Phase 3B-2C was not started — no reference to it exists anywhere in the corrected source.
- `/Inspiration Mods` and every other unrelated working-tree entry (generated datagen JSON, Trading
  Test screenshots, `logs/`, `.cache/`, every existing review bundle ZIP including
  `TOTALITY_RESOURCE_API_PHASE_3B2B_FINAL_REVIEW_BUNDLE.zip` itself) were not read, modified, staged,
  or touched in any way during this correction pass.
- Nothing was staged, committed, or pushed at any point in this correction pass.
- No correction review bundle was created — reserved for a separate, later step.

---

## 30. PHASE 3B-2B MANUAL VALIDATION CONFIRMATION — 2026-07-25

Manual, in-client validation performed by Stefan against the corrected Phase 3B-2B implementation
(the same 20-file corrected state reviewed in the correction bundle). This section records only
what was actually observed — no packet trace, exact timing, revision value, or internal component
identity is claimed beyond what was reported.

### 30.1 Initial client state

- The first client parity poll occurred at tick 0, before the generic full Resource snapshot was
  ready. All four Resources initially reported `GENERIC_NOT_READY`.
- After allowing the world to settle, at a later tick observed as 343, all four Resources were
  present and reported `EXACT_MATCH`: Mana, Stamina, standard spell slots, and Rage.
- The snapshot contained exactly the four expected resource ids: `totality:mana`,
  `totality:stamina`, `totality:spell_slots`, `totality:rage`.
- Confirms: the initial readiness gate works; the full snapshot later becomes available; every
  valid-player poll observes all four Resources; Rage is not silently skipped.

### 30.2 Stamina validation

Stamina was spent through normal gameplay. The parity observation remained or settled as
`EXACT_MATCH`; Stamina regeneration also remained or settled as `EXACT_MATCH`. No persistent
Stamina mismatch was observed.

### 30.3 Rage spend/restore validation

Rage spending was exercised (Stefan had previously Long Rested, then used one Rage charge, leaving
the correct authoritative state at current: 1, maximum: 2). The Rage observation remained present
throughout; current and maximum agreed between the generic and legacy views; Rage reported
`EXACT_MATCH`. No silent skip occurred.

### 30.4 Reconnect validation

Disconnect/reconnect was exercised. All four observations rebuilt; Mana, Stamina, spell slots, and
Rage all settled to `EXACT_MATCH`. No old mismatch survived the reconnect lifecycle. (Exact internal
lifecycle-counter values were not observed and are not claimed.)

### 30.5 Dimension-transfer finding (confirmed live legacy discrepancy)

Before dimension transfer: generic Rage 1/2, legacy Rage 1/2, classification `EXACT_MATCH`, the
existing Rage HUD was visible.

After entering the Nether: Mana `EXACT_MATCH`, Stamina `EXACT_MATCH`, standard spell slots
`EXACT_MATCH`; generic Rage remained 1/2, but legacy Rage read 0/0, and Rage classification became
`PERSISTENT_MISMATCH`. The existing Rage HUD disappeared, because it reads the legacy
`PlayerChargesComponent` mirror directly.

Returning to the Overworld did not repair the legacy Rage state: generic Rage remained correct,
legacy Rage remained 0/0, and Rage remained `PERSISTENT_MISMATCH`.

**Assessment**: parity behaved correctly throughout — all four observations rebuilt, and the generic
full snapshot preserved the correct Rage value the entire time. The existing legacy client Rage pool
was lost or not resynchronized across the dimension transfer. This is a confirmed live legacy Rage
dimension-resynchronization bug, **not** a Phase 3B-2B parity defect. It was detected, not repaired
— no fix belongs in this commit. (The exact root cause beyond this observed lifecycle and source
evidence — e.g. which specific dimension-change hook drops the pool — was not established and is
not claimed here.)

### 30.6 Death/respawn validation

Death and respawn were exercised after the dimension-transfer Rage mismatch above. The `LocalPlayer`
replacement/reset path rebuilt observations; legacy Rage was restored from 0/0 to 1/2; generic Rage
remained 1/2; Rage returned to `EXACT_MATCH`; the existing Rage HUD returned.

**Narrow conclusion**: respawn synchronization restores the legacy Rage mirror. The missing behavior
observed in §30.5 is specific to the tested dimension-transfer lifecycle, not permanent corruption
of authoritative Rage state.

### 30.7 Rune/formula Mana test — NOT MANUALLY EXERCISED

An ordinary tested Grimoire spell left Mana at `EXACT_MATCH`. A breakpoint placed inside
`FormulaResolver.tryCast` on the Mana-removal line did not trigger — the tested spell therefore did
not use the `FormulaResolver` path.

**Correct conclusion**: the source-level `FormulaResolver` path still lacks an explicit legacy Mana
synchronization (unchanged, confirmed elsewhere in this report); current gameplay reachability of
that path was not established by this manual pass; the tested Grimoire route uses another casting
path. Parity did **not** manually detect a rune-cast Mana mismatch, and the source-level risk was
**not** disproved either way. `FormulaResolver` and every Mana synchronization path remain unfixed
and untouched.

### 30.8 Null-player / disconnect validation

Disconnect to a no-player screen was exercised. `ClientResourceParityObservations.snapshot()` became
empty and remained empty on subsequent null-player ticks. No Rage 0/0 observation was fabricated; no
repeated exception or visible error occurred.

### 30.9 No parity-specific presentation

No parity-created HUD element, chat message, toast, screen, tooltip, gameplay restriction, or
mismatch-logging output appeared at any point. The Rage HUD disappearance recorded in §30.5 is the
*existing* legacy Rage HUD reacting to the real legacy mirror becoming 0/0 — it is not UI created by
the parity system.

### 30.10 Dedicated-server validation

A dedicated server was started (Fabric environment `SERVER`, Minecraft 26.2) and reached
`Done (0.385s)! For help, type "help"` successfully. No `ClassNotFoundException`, no
`NoClassDefFoundError`, and no attempted client-parity classloading failure of any kind — no failure
involving `ClientResourceParityCoordinator`, `LegacyClientResourceParityReaders`, or
`ClientResourceParityObservations` occurred.

Unrelated existing verification results also observed at this time: `ProvisionerEntityBackedSmokeTest`
(3/4 checks failed) and `OffhandAttackVerification` (3/5 checks failed). These remain separate,
pre-existing, deferred bugs — they are not Phase 3B-2B client-classloading failures and were not
fixed in this slice. Beyond what is recorded here and elsewhere in the project's existing audit
trail, no further claim is made about whether they are regressions or pre-existing.

### 30.11 Manual validation conclusion

**Phase 3B-2B manual validation is complete.**

Passed: initial `GENERIC_NOT_READY` behavior; later four-resource `EXACT_MATCH` state; exact
four-resource presence; Stamina gameplay parity; Rage spend parity; reconnect reset/rebuild;
dimension reset/rebuild; persistent mismatch detection; stale-observation replacement; death/respawn
reset and recovery; null-player clearing; invisible diagnostic behavior; dedicated-server
classloading boundary.

Detected but intentionally unfixed: the legacy Rage mirror loses or fails to regain its pool across
dimension transfer; generic Rage remains correct throughout; respawn restores the legacy mirror.

Not manually exercised: `FormulaResolver.tryCast` Mana discrepancy (gameplay reachability of that
specific path was not established by this pass).

---

## Stop point — status

- Implementation, tests, validation (`compileJava`, `compileTestJava`, `test`, `runDatagen`, `build`, `git diff --check`, classloading boundary checks), and this report are complete as of the original 2026-07-25 implementation.
- **2026-07-25 external-review correction pass also complete** — see §29. The corrected total is 727/727 tests passing.
- **2026-07-25 manual validation also complete** — see §30. All checks passed; one pre-existing legacy Rage dimension-resynchronization bug detected and intentionally left unfixed; the `FormulaResolver` rune-cast Mana path was not manually exercised.
- Nothing was staged, committed, or pushed.
- No review bundle was created by this manual-validation update (both existing bundles remain as previously produced).
- Phase 3B-2C was not started.

---
