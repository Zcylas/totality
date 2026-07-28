# TOTALITY — Generic Player Resource API — Phase 3B-2C Implementation Report
## Bounded Client Shadow-Parity Logging

Scope actually implemented: bounded, client-only DEBUG diagnostics for a Resource's transition
into, or recovery out of, `PERSISTENT_MISMATCH` — a new, separate observer of the already-existing
`ClientResourceParityObservations` read-only snapshot. No change to Resource authority, values,
formulas, mutation, packet ordering/payloads, sync eligibility, trust calculation, parity
comparison semantics, grace-period semantics, lifecycle behavior, HUD/class/spell/Rage/legacy-mirror
behavior, and no Phase 3B-3/3C work. Nothing was staged, committed, or pushed.

**This report includes two dated external-review correction passes — see §22 and §23** — plus a
documentation-only correction pass (no runtime/test-behavior change) that brought this report itself
into agreement with the corrected source. §22 made the diagnostic persistent-mismatch episode memory
connection-scoped (cleared only on JOIN/DISCONNECT, never on a dimension change or player-identity
replacement). §23 corrected the episode-continuation semantics themselves — `GENERIC_NOT_READY` and
`TRANSITIONAL_MISMATCH` do not close an active episode — and added the step-by-step regression test
that proves it. The corrected lifecycle/dependency/semantics descriptions are reflected in place
throughout §2/§4/§6/§7/§9/§14/§16/§19; §22 and §23 each record what their respective prior state got
wrong, why, and the exact fix. Do not read the numbered sections as "as originally implemented" —
they describe the corrected, current state of the code; §22/§23 are the dated history of how they got
there.

---

## 1. Starting branch and commit — CONFIRMED FACT

- Branch: `feature/general-resource-api`.
- Starting `HEAD`: `5ee736ceecf3308b809732d117991d3aab8ca69c` ("Add client resource parity integration") — confirmed matching the expected checkpoint before any change.
- Tracking `origin/feature/general-resource-api`, 0 ahead / 0 behind at start.
- `git status --short` at start showed only the known-unrelated entries (modified generated datagen JSON, untracked review-bundle ZIPs, `Context/Trading Test/*.png`, `logs/`, `src/main/generated/.cache/`) — no `.java` file appeared modified or untracked.
- Nothing was staged at start.
- No Phase 3B-2C or Phase 3B-3 implementation, temporary worktree, or temporary extraction directory existed at start (confirmed by search: no `ParityLog`/`ParityDiagnostic`/`TransitionLog`-named file anywhere in `src`, `git worktree list` showed only the primary worktree).
- Automated baseline confirmed before any change: `./gradlew test` (fresh run) summed to **727 tests, 0 failed, 0 errors, 0 skipped** across every `build/test-results/test/*.xml` — matching the expected checkpoint exactly.
- `/Inspiration Mods` was excluded from every search performed for this implementation.

---

## 2. Exact files changed — CORRECTED (§23)

**Created (production, pure — `zcylas.totality.api.rpg.resources.client.parity`, no Minecraft/Fabric/logging dependency):**
- `ClientResourceParityLogTransition.java` — the bounded three-value decision outcome enum (`NONE`, `ENTERED_PERSISTENT_MISMATCH`, `RECOVERED_FROM_PERSISTENT_MISMATCH`). Unchanged by either correction pass.
- `ClientResourceParityLogTransitionTracker<K>.java` — the single owner of per-resource "am I currently inside a persistent-mismatch episode" transition memory and the entry/recovery decision itself. **Generic over its key type `K`** (first correction, §22 — originally hard-coded to `Identifier`, which contradicted its own "no Minecraft dependency" Javadoc claim; the client-only boundary now supplies `Identifier` as the concrete `K`, see §7). **`classify`'s episode-continuation logic corrected a second time (§23) — now an exhaustive `switch` over every `ClientResourceParityClassification` value, since `GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH` must not close an active episode.**
- `ClientResourceParitySummaryText.java` — pure, bounded, deterministic text rendering of a `ClientResourceParitySummary` (scalar/partitioned/unavailable), capped at `MAX_PARTITIONS_IN_TEXT = 16` partition entries regardless of input size. Unchanged by either correction pass.
- `ClientResourceParityLogDiagnostics.java` — pure formatter producing the SLF4J parameterized template (`{}` placeholders) plus a bounded `Object[]` argument array for the entry/recovery diagnostics; mirrors `ClientResourceSyncRejectionDiagnostics`'s established precedent. Unchanged by either correction pass.

**Created (production, client-only — new file in the existing `zcylas.totality.client.resource.parity` package):**
- `ClientResourceParityLogObserver.java` (`@Environment(EnvType.CLIENT)`) — the thin logger-facing boundary; `tick()`/`clear()` public entry points, a package-private `transitions()` test accessor mirroring `ClientResourceParityCoordinator.tracker()`'s established pattern. Holds a `ClientResourceParityLogTransitionTracker<Identifier>` (first correction, §22). **Second correction (§23): gained a new package-private `decide(Identifier, ClientResourceParityObservation)` deterministic decision seam — the exact method `tick()` itself now calls — so tests can assert the precise transition for each individual observation.**

**Modified (one file, unchanged by this second correction pass):**
- `src/main/java/zcylas/totality/TotalityClient.java` — new lines appended after the existing `ClientResourceParityCoordinator.tick()`/`clear()` registrations: one new `END_CLIENT_TICK` registration (`ClientResourceParityLogObserver.tick()`) and **two** new lifecycle-clear registrations (`JOIN`/`DISCONNECT` only → `ClientResourceParityLogObserver.clear()`). **Corrected in the first pass (§22): the original implementation additionally registered a third clear on `AFTER_CLIENT_LEVEL_CHANGE`; that registration was removed.** This second correction pass did not touch this file at all — confirmed by an empty `diff` against the first correction bundle's copy (§23.5).

**Created (tests, additive only):**
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityLogTransitionTrackerTest.java` (30 tests — 24 original + 2 first-correction + net 4 second-correction: 2 formerly-incorrect tests replaced, 6 new added; see §9/§23.4)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParitySummaryTextTest.java` (9 tests, unchanged by either correction pass)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityLogDiagnosticsTest.java` (5 tests, unchanged by either correction pass)
- `src/test/java/zcylas/totality/client/resource/parity/ClientResourceParityLogObserverTest.java` (12 tests — 8 original + 5 first-correction − 2 replaced + 1 comprehensive step-by-step replacement + 1 new reflection assertion on `decide(...)`; see §9/§23.4)

**Not touched:** every existing Phase 3B-2A/3B-2B production class (`ClientResourceParityTracker`, `ClientResourceParityObservation`, `ClientResourceParitySummary`, `ClientResourceParityOutcome`, `ClientResourceParityClassification`, `ClientResourceScalarParityComparator`, `ClientSpellSlotParityComparator`, `ClientRageParityPolicy`, `ClientGenericParitySummaryMapper`, `ClientResourceParityPoll`, `ClientResourceParityLifecycle`, `ClientResourceParityGenericAccess`, `ClientResourceParityLegacyAccess`, `ClientResourceParityCoordinator`, `LegacyClientResourceParityReaders`, `ClientResourceParityObservations`) and their tests; every legacy manager (`ClientManaManager`, `ClientStaminaManager`, `ClientSpellSlotManager`, `PlayerChargesComponent`); every packet receiver/sender; `ClientResourceSyncManager`/`ClientResourceSyncBridge`; `ClientClassManager`; `FormulaResolver`; every HUD/screen/radial/tooltip/movement class; `/Inspiration Mods`; and all "known unrelated" working-tree entries (generated datagen JSON, `Context/Trading Test/*.png`, `logs/`, `.cache/`, every existing review bundle ZIP, every prior audit document).

---

## 3. Architecture — IMPLEMENTATION DECISION

Five small production types organized into four responsibilities, matching the task's suggested
structure:

1. **Bounded decision vocabulary and pure transition/diagnostic decision type** (two files):
   `ClientResourceParityLogTransition` — the three-value outcome enum (`NONE`,
   `ENTERED_PERSISTENT_MISMATCH`, `RECOVERED_FROM_PERSISTENT_MISMATCH`) every decision in this slice
   is expressed in terms of — and `ClientResourceParityLogTransitionTracker<K>`, the type that
   produces it. The tracker consumes only a `ClientResourceParityClassification` per resource key of
   type `K` (never the full `ClientResourceParityObservation`, never a comparator/outcome type) and
   returns a `ClientResourceParityLogTransition`. Genuinely generic — **no Minecraft/Fabric/logging/legacy-manager/networking dependency of any kind**, confirmed by import inspection (§7; this is the corrected state — see §22 for the defect this fixes).
2. **Pure bounded formatter** (two files): `ClientResourceParitySummaryText` (renders one `ClientResourceParitySummary` to a short, capped string) plus `ClientResourceParityLogDiagnostics` (assembles the SLF4J template and bounded `Object[]` args from a full `ClientResourceParityObservation`). Split into two files because the summary-rendering logic is independently useful/testable (bounding partitioned content is its own concern) and the template/args assembly is a distinct, thin concern.
3. **Small per-resource transition state**: the private `Map<K, Boolean>` inside `ClientResourceParityLogTransitionTracker<K>` (responsibility 1's own type, not a separate file) — bounded to one entry per distinct resource key ever classified, exactly mirroring `ClientResourceParityTracker`'s own backing-map bound. The client-only boundary (`ClientResourceParityLogObserver`) instantiates it as `ClientResourceParityLogTransitionTracker<Identifier>`.
4. **Client-only logger integration** (one file): `ClientResourceParityLogObserver`, called once per `END_CLIENT_TICK` after `ClientResourceParityCoordinator.tick()`. It is a **separate observer of `ClientResourceParityObservations`'s existing snapshot** — per that class's own Javadoc (written during Phase 3B-2B: *"Intended future consumers, not built in this slice: Phase 3B-2C's bounded diagnostics..."*), it is the first real consumer of that read boundary, and it does not modify `ClientResourceParityCoordinator`, `ClientResourceParityPoll`, or `ClientResourceParityTracker` at all. Exposes a package-private `decide(Identifier, ClientResourceParityObservation)` seam (added in §23) that `tick()` itself calls before dispatching the DEBUG log.

That is five files total (2 + 2 + 0 + 1, since responsibility 3 names no new file of its own) across
four numbered responsibilities: `ClientResourceParityLogTransition`,
`ClientResourceParityLogTransitionTracker`, `ClientResourceParitySummaryText`,
`ClientResourceParityLogDiagnostics`, and `ClientResourceParityLogObserver`.

No existing Phase 3B-2A/3B-2B class was modified. The one production file touched
(`TotalityClient.java`) received only new, additive registrations — not an "unavoidable compile-only
import adjustment" exception, just ordinary new wiring at the file's existing client-entrypoint
registration site, following the exact pattern already used for `ClientResourceParityCoordinator`.

---

## 4. Transition ownership — CORRECTED (§23; agrees with §6/§7/§23 and the source)

`ClientResourceParityLogTransitionTracker<K>` is the **single** owner of the "should this observation
log" decision, per the task's explicit "no duplicate transition ownership" requirement. It is a
`Map<K, Boolean>` recording, per resource key, "is a persistent-mismatch episode currently active."
`classify(resourceId, classification)`'s full, exhaustive contract (a `switch` with no `default` arm
over every `ClientResourceParityClassification` constant when an episode is active — §23.2):

**No episode currently active for the resource:**
- `PERSISTENT_MISMATCH` → marks the episode active, returns `ENTERED_PERSISTENT_MISMATCH`.
- Every other classification → returns `NONE`; the resource remains inactive.

**An episode is currently active for the resource:**
- `PERSISTENT_MISMATCH` → returns `NONE`; the episode remains active.
- `GENERIC_NOT_READY` → returns `NONE`; the episode remains active (observation uncertainty, not a
  recovery).
- `TRANSITIONAL_MISMATCH` → returns `NONE`; the episode remains active (still an ordinary mismatch,
  merely within the elapsed-tick grace window, not a recovery).
- `EXACT_MATCH` → marks the episode inactive, returns `RECOVERED_FROM_PERSISTENT_MISMATCH`.
- `MODEL_MISMATCH` → marks the episode inactive, returns `RECOVERED_FROM_PERSISTENT_MISMATCH`.
- `EXPECTED_SEMANTIC_DIFFERENCE` → marks the episode inactive, returns `RECOVERED_FROM_PERSISTENT_MISMATCH`.
- `NOT_APPLICABLE` → marks the episode inactive, returns `RECOVERED_FROM_PERSISTENT_MISMATCH`.

No other class — not `ClientResourceParityTracker`, not `ClientResourceParityObservation`, not
`ClientResourceParityPoll`, not `ClientResourceParityCoordinator`, not `ClientResourceParityLogObserver`
itself — tracks this memory a second time. `ClientResourceParityLogObserver` holds exactly one static
instance of the tracker and calls `classify` (via its own `decide(...)` seam, §23.4) exactly once per
resource per tick, from a single call site.

**Corrected below (§23) — this section previously stated an obsolete, pre-second-correction
contract** that treated *any* non-`PERSISTENT_MISMATCH` classification as closing an active episode,
including a claim that a direct `PERSISTENT_MISMATCH → TRANSITIONAL_MISMATCH` observation would log a
recovery and that a later re-escalation to `PERSISTENT_MISMATCH` would then be a fresh entry, and a
claim that `TRANSITIONAL_MISMATCH` was "deliberately not special-cased." All of that is now known
incorrect (§23.1) and has been removed from this section — `GENERIC_NOT_READY` and
`TRANSITIONAL_MISMATCH` are now explicitly special-cased (they keep an active episode active, per the
table above), which is exactly what the confirmed live dimension-transfer/return sequence (§17, §23.3)
requires.

---

## 5. Logging level and bounded-content policy — IMPLEMENTATION DECISION

- **Level**: `Totality.LOGGER.debug(...)` exclusively — no `info`/`warn`/`error` call anywhere in the new code (confirmed by grep, §7).
- **Parameterized, not eager concatenation — corrected claim (§23)**: `ClientResourceParityLogDiagnostics` returns a fixed SLF4J template string (`{}` placeholders) plus a bounded `Object[]`; the client-only call site is `Totality.LOGGER.debug(TEMPLATE, args)` — a genuinely parameterized SLF4J call, not a pre-concatenated `String` as `ClientResourceSyncRejectionDiagnostics`'s original precedent used. **What is actually deferred, precisely**: SLF4J's own `{}` placeholder substitution inside `debug(String, Object...)` is deferred until the logging framework actually needs the rendered message, so a disabled DEBUG level can skip that substitution step. **What is not deferred**: `entryArgs(observation)`/`recoveryArgs(observation)` — including the bounded `ClientResourceParitySummaryText.format(...)` calls that build the generic/legacy summary strings — run eagerly, synchronously, every time `processObservation` reaches an `ENTERED_PERSISTENT_MISMATCH`/`RECOVERED_FROM_PERSISTENT_MISMATCH` transition, regardless of whether DEBUG is enabled. This is bounded and cheap (at most one scalar/partitioned summary pair per transition, never per tick — see §6), so no `Logger.isDebugEnabled()` guard was added; this report must not claim the summary-string preparation itself is deferred, since no such guard exists in the code.
- **Bounded fields only** — entry diagnostic: resource id, generic summary text, legacy summary text, `firstMismatchTick`, `lastObservedTick`. Recovery diagnostic: resource id, the new classification, generic summary text, legacy summary text, `lastObservedTick`. No player UUID, world path, coordinate, inventory contents, or unrelated gameplay state anywhere in either template or its arguments.
- **Scalar summary text**: `"<current>/<maximum>"`, with a `+overflowN` suffix only if overflow is non-zero and a `@scaleN` suffix only if the unit scale is non-canonical (`!= 1`) — both conditions are already impossible for the four production resources under normal operation, so the ordinary case is always the short two-number form.
- **Partitioned summary text**: capped at `ClientResourceParitySummaryText.MAX_PARTITIONS_IN_TEXT = 16` rendered entries (already in ascending order, per `ClientResourceParitySummary.Partitioned`'s own compact constructor), with a bounded `,...(+N more)` suffix if the partition count exceeds the cap. Standard spell slots (the only production partitioned resource) uses exactly 10 partitions, well under the cap — the cap exists as a structural guarantee against a future or adversarial partitioned resource, not because any current resource needs it. Proven never to exceed the bound with a 5000-partition adversarial input test (`largePartitionInputProducesBoundedOutput`).
- **Never a raw map/collection dump, never a raw `ClientResourceParityObservation`/`ClientResourceParitySummary` reference in the logged arguments** — `entryArgsNeverIncludesRawObservationOrCollectionReference` proves the argument array contains only `Identifier`/`String`/`Long`/`long`/enum values.

---

## 6. Entry/recovery semantics — CORRECTED (§23)

**No persistent episode currently active for the resource:**
1. `PERSISTENT_MISMATCH` → exactly one DEBUG entry diagnostic (from any prior classification,
   including "never observed before").
2. Every other classification (`EXACT_MATCH`, `TRANSITIONAL_MISMATCH`, `GENERIC_NOT_READY`,
   `EXPECTED_SEMANTIC_DIFFERENCE`, `NOT_APPLICABLE`, `MODEL_MISMATCH`) → no diagnostic.

**A persistent episode is currently active for the resource — corrected, second external-review
pass (§23):**
3. `PERSISTENT_MISMATCH` → no diagnostic; the episode continues (proven with 20 repeated
   observations in `repeatedPersistentObservationsProduceNoDuplicateEntryEvent`).
4. `GENERIC_NOT_READY` → **no diagnostic; the episode continues.** This is observation uncertainty
   (the generic side has temporarily lost its full snapshot, e.g. mid-rebuild after a dimension
   change), never a recovery. **Corrected**: an earlier version of this class treated this as a
   recovery — see §23.1 for the defect this fixes.
5. `TRANSITIONAL_MISMATCH` → **no diagnostic; the episode continues.** The two sides still disagree;
   they are merely within the elapsed-tick grace window before persistence is (re-)declared — still
   a mismatch, never a recovery. **Corrected**: an earlier version of this class also treated this as
   a recovery — see §23.1.
6. `EXACT_MATCH`, `MODEL_MISMATCH`, `EXPECTED_SEMANTIC_DIFFERENCE`, or `NOT_APPLICABLE` → exactly one
   DEBUG recovery diagnostic, naming the new classification, and the episode closes. These four are
   documented "stable exit" classifications (§23.2): each means the previous persistent numeric-
   mismatch comparison no longer applies at all, not merely a transient reading.
7. A closed episode later re-entering `PERSISTENT_MISMATCH` → exactly one new entry diagnostic (a
   fresh episode) — unaffected by this correction.

**Unaffected by this correction:**
8. Each resource id's memory is fully independent — `manaAndRageTransitionsAreIndependent` proves
   Mana's episode neither suppresses nor duplicates Rage's, and vice versa.
9. The initial `GENERIC_NOT_READY` readiness period before the first full snapshot (no episode active
   yet) never logs, and a `GENERIC_NOT_READY → TRANSITIONAL_MISMATCH → PERSISTENT_MISMATCH` sequence
   from that initial state logs exactly one entry event (not one per intermediate step) — this was
   already correct before this correction, since no active episode existed to falsely close.
10. The transition decision consumes only the tracker's already-committed final classification per
    observation — it never invents a second mismatch/grace algorithm, and never re-derives grace/
    pending-resync state itself (that remains exclusively `ClientResourceParityTracker`'s job,
    unmodified).
11. The full four-resource `ClientResourceParityClassification` domain is handled by an **exhaustive
    `switch`** inside `ClientResourceParityLogTransitionTracker.classify` (no `default` arm) — a
    future classification added to that enum forces a compile error here rather than silently
    falling through to an unexamined, possibly-incorrect behavior (the task's explicit instruction).

---

## 7. Lifecycle/reset semantics — CORRECTED (§22)

Two independently-scoped resets exist, and they are deliberately **not** wired to the same set of
Fabric events:

**A. `ClientResourceParityCoordinator.clear()` — the parity *observation tracker*'s reset (unmodified
by this correction pass, unmodified by Phase 3B-2C at all).** Registered on `ClientPlayConnectionEvents.JOIN`/
`DISCONNECT`, `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`, **and** (indirectly, via
`ClientResourceParityLifecycle.beginTick`'s reference-change detection) a `LocalPlayer` identity
replacement (death/respawn). This tracker's stale generic/legacy comparison values are meaningless
across a dimension swap or a fresh post-respawn `LocalPlayer`, so it must reset on all four of these
triggers — exactly as Phase 3B-2B originally built it, and exactly as it still works today.

**B. `ClientResourceParityLogObserver.clear()` — the diagnostic *persistent-mismatch episode
memory*'s reset (connection-scoped).** Registered **only** on `ClientPlayConnectionEvents.JOIN`/
`DISCONNECT` — **not** on `AFTER_CLIENT_LEVEL_CHANGE`, and not on any `LocalPlayer`-replacement path.
This diagnostic memory answers a coarser question than (A)'s: "has this resource's persistent
mismatch already been reported for the current play connection." It must survive both a dimension
change and a respawn within the same connection, because the confirmed live sequence (§17) is:

0. **Before the transfer, in the Overworld, Rage is `EXACT_MATCH`** (generic 1/2, legacy 1/2) — no
   episode exists yet. **Corrected (§23): earlier drafts of this report incorrectly implied a
   persistent episode already existed before the dimension transfer; per the actual manually observed
   chronology (Phase 3B-2B §30.5), it does not — the episode begins only after entering the Nether.**
1. The player enters the Nether. (A) clears (dimension change) and rebuilds from a fresh readiness
   sequence: `GENERIC_NOT_READY` → `TRANSITIONAL_MISMATCH` → `PERSISTENT_MISMATCH` (legacy Rage has
   become 0/0; generic Rage remains 1/2). (B) has no active episode for Rage yet, so per §23's
   corrected episode-continuation rules the `GENERIC_NOT_READY` and `TRANSITIONAL_MISMATCH`
   observations both return `NONE` (no episode to keep active, and neither begins one), and the
   final `PERSISTENT_MISMATCH` observation is a genuine fresh entry — **exactly one entry line is
   logged here.**
2. The player returns to the Overworld. (A) clears and rebuilds again through the identical
   `GENERIC_NOT_READY` → `TRANSITIONAL_MISMATCH` → `PERSISTENT_MISMATCH` sequence — the real-world
   mismatch has not changed. This time (B) **does** have an active episode (from step 1), so per
   §23's corrected rules: `GENERIC_NOT_READY` and `TRANSITIONAL_MISMATCH` both return `NONE` and
   **keep the episode active** (they are observation uncertainty / an ordinary within-grace
   mismatch, never a recovery), and the rebuilt `PERSISTENT_MISMATCH` also returns `NONE` (the
   episode was never closed, so this is not a fresh entry either) — **no duplicate entry, and no
   false recovery from the intermediate `GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH` readings.**
3. The player dies and respawns. (A) clears (the `LocalPlayer`-replacement path) and rebuilds; the
   legacy Rage mirror is restored to 1/2, so the rebuilt observation is `EXACT_MATCH`. `EXACT_MATCH`
   is a stable exit classification (§23), so (B)'s still-active episode from step 1 closes —
   **exactly one recovery line, correlated back to the episode actually reported in step 1.**
4. The player disconnects, or joins a new world/session. **Only now** does (B) clear — a persistent
   observation in the new connection is always a fresh entry, never a continuation of the old
   connection's episode (`explicitClearRepresentsAConnectionResetSoTheNextPersistentObservationIsTreatedAsANewEntry`).

- A `clear()` call never itself returns or emits a transition — `clearAll()`/`clear(id)` are `void`
  and never invoke `classify`, so no recovery can be fabricated by the act of clearing
  (`clearAllErasesEveryResourcesMemoryWithoutFabricatingRecovery`,
  `clearResetsTransitionMemoryWithoutFabricatingARecovery`).
- (A) and (B) are entirely independent structures with independent reset triggers — clearing one
  never reaches into or depends on the other. `TotalityClient.java` registers each with its own
  explicit call, never a shared helper, so a bug in one reset path cannot silently affect the other's
  memory.
- **What was wrong before the first correction, in one sentence**: the original implementation
  registered (B) on the same three events as (A) (including `AFTER_CLIENT_LEVEL_CHANGE`), which meant
  a dimension change wiped the diagnostic episode memory too — the exact opposite of the
  connection-scoped behavior this section now describes. See §22 for the full defect/fix history.
- **What was additionally wrong before the second correction, in one sentence**: even after (B)
  became connection-scoped, its `classify` logic still treated *any* non-`PERSISTENT_MISMATCH`
  classification as closing the episode — so step 2's `GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH`
  rebuild readings falsely closed the episode (a spurious recovery) and the rebuilt
  `PERSISTENT_MISMATCH` then falsely reopened it (a spurious second entry). See §23 for the full
  defect/fix history.

---

## 8. Bounded diagnostic fields — CONFIRMED FACT

Entry diagnostic (`ClientResourceParityLogDiagnostics.ENTRY_TEMPLATE`):
```
Resource parity entered persistent mismatch: resource={} generic={} legacy={} firstMismatchTick={} lastObservedTick={}
```
Recovery diagnostic (`ClientResourceParityLogDiagnostics.RECOVERY_TEMPLATE`):
```
Resource parity recovered from persistent mismatch: resource={} classification={} generic={} legacy={} lastObservedTick={}
```
Both proven to have a placeholder count exactly matching their argument array length
(`entryTemplatePlaceholderCountMatchesArgsLength`, `recoveryTemplatePlaceholderCountMatchesArgsLength`).

---

## 9. Test matrix — CORRECTED (§23)

| File | Tests | Covers |
|---|---|---|
| `ClientResourceParityLogTransitionTrackerTest` | 30 | Original items 1-16 + first-correction generic-key/import-scan tests + second-correction episode-continuation tests (§23.4): 2 replaced (formerly-incorrect `GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH`-as-recovery expectations), 6 new (stable-exit `NOT_APPLICABLE`; `GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH` keep-episode-active proofs; full rebuild sequence with and without a final recovery; persistent-stays-active-through-transitional) |
| `ClientResourceParitySummaryTextTest` | 9 | Unchanged by either correction pass |
| `ClientResourceParityLogDiagnosticsTest` | 5 | Unchanged by either correction pass |
| `ClientResourceParityLogObserverTest` | 12 | Original items 21/22/24 unchanged; the first correction's two step-batched regression tests were **replaced** by one comprehensive step-by-step regression test driving the new package-private `decide(...)` seam after every intermediate rebuild observation (§23.4) — net -1 test, +1 reflection assertion on `decide(...)`'s visibility |
| **Total new (relative to the 727-test Phase 3B-2B baseline)** | **56** | 46 original + 7 first-correction + 3 net second-correction |

Item 23 ("no production logger call occurs for steady-state persistent observations") is proven at
two levels: exhaustively at the pure decision layer (`ClientResourceParityLogTransitionTrackerTest`,
which is the sole gate the observer's logger call sites are conditioned on — the observer contains
zero `Totality.LOGGER` calls outside the `ENTERED_PERSISTENT_MISMATCH`/`RECOVERED_FROM_PERSISTENT_MISMATCH`
switch arms, confirmed by direct reading of `ClientResourceParityLogObserver.processObservation`), and
end-to-end through the observer's own real `decide(...)` seam (§23.4), which returns the exact
transition for each individual observation rather than requiring inference from final tracker state.
No SLF4J test-capture/appender was introduced to assert on actual log records — the codebase has no
existing precedent for that (the Phase 3B-1 `ClientResourceSyncManager` DEBUG logging is likewise
never asserted via a captured log record in its own tests), and the pure-layer proof plus the exact
per-step `decide(...)` proof together are exhaustive and sufficient.

**Two previously-passing tests asserted now-corrected-as-incorrect semantics and were replaced, not
merely deleted** (§23.1, §23.4): `persistentToGenericNotReadyProducesOneRecoveryEvent` (asserted
`GENERIC_NOT_READY` closes an active episode — now known wrong) and
`persistentThenTransitionalThenPersistentLogsRecoveryThenEntryAgain` (asserted `TRANSITIONAL_MISMATCH`
closes an active episode — now known wrong). No other existing test was weakened, deleted, or had its
expected behavior changed.

---

## 10. Automated results — CORRECTED (§23)

**As originally implemented (pre-correction):**
- `test --rerun`: `BUILD SUCCESSFUL`, **773 tests, 0 failed, 0 errors, 0 skipped** (727 baseline + 46 new).

**After the first correction pass (§22):**
- `test --rerun`: `BUILD SUCCESSFUL`, **780 tests, 0 failed, 0 errors, 0 skipped** (773 + 7).

**After this second correction pass (§23), run fresh:**
- **`compileJava`**: `BUILD SUCCESSFUL`. No compiler warnings.
- **`compileTestJava`**: `BUILD SUCCESSFUL`. No compiler warnings.
- **`test --rerun`**: `BUILD SUCCESSFUL` on the first run — no failures. **Exact total: 783 tests, 0
  failed, 0 errors, 0 skipped.**
- **Net change**: 783 − 780 = **+3** (30 + 9 + 5 + 12 = 56 new relative to the 727 baseline; two
  previously-passing tests that asserted now-corrected-incorrect semantics were replaced — see §9 —
  not merely added to).
- **`runDatagen`**: `BUILD SUCCESSFUL`. `total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0` — zero generated-file impact.
- **`build`**: `BUILD SUCCESSFUL`. Only the pre-existing Gradle/Loom "deprecated features, incompatible with Gradle 10" notice — present before this session, unrelated to this change.
- **`git diff --check -- src/main/java src/test/java`**: exit 0, no output — no whitespace/line-ending errors in any new or modified file.
- No compiler, Gradle, Loom, test, datagen, or build warning of any kind beyond the pre-existing Gradle/Loom deprecation boilerplate was observed anywhere in this session.
- Unlike the first correction pass, this second pass's own new/updated tests passed on the first
  `test --rerun` run — no test-only defect was found during this pass's validation.

---

## 11. Datagen results — CONFIRMED FACT

See §10 — `349 → 349`, `written: 0`. `git status --short src/main/generated` after `runDatagen` shows the identical pre-existing 21 modified files plus the untracked `.cache/` directory — no new or additionally-changed generated file.

---

## 12. Build result — CONFIRMED FACT

See §10 — `BUILD SUCCESSFUL`, `check`/`test`/`jar`/`sourcesJar`/`assemble` all succeeded or were up-to-date.

---

## 13. Line-ending warnings — CONFIRMED FACT

None. `git diff --check` produced no output for any file in this change (§10).

---

## 14. Dedicated-server checklist — MANUAL VALIDATION REQUIRED (not performed in this session)

Not empirically re-verified by starting a dedicated server in this session — consistent with the
existing precedent set by the Phase 3B-2B report's own §18, which likewise deferred the *first*
empirical dedicated-server check to manual validation (later performed and confirmed in that
report's §30.10). Confirmed instead by source/import inspection.

**Type inventory (five production types total, §3): four pure, one client-only.** The four pure
types — `ClientResourceParityLogTransition`, `ClientResourceParityLogTransitionTracker<K>`,
`ClientResourceParitySummaryText`, `ClientResourceParityLogDiagnostics` — carry no `@Environment`
annotation and import nothing beyond `java.util.*`/other pure parity types (`ClientResourceParityLogTransitionTracker<K>`,
corrected in §22, no longer imports `net.minecraft.resources.Identifier` either, confirmed by direct
grep and by `trackerSourceImportsNoMinecraftFabricLoggerClientNetworkingOrLegacyManagerType`). The
fifth, `ClientResourceParityLogObserver`, is the *only* one of the five carrying `@Environment(EnvType.CLIENT)`
— confirmed by direct import inspection, it imports `net.fabricmc.api.EnvType`/`Environment`,
`net.minecraft.resources.Identifier`, and `Totality` (for `Totality.LOGGER`); it does not import
`Minecraft`/`LocalPlayer` directly — those live in `ClientResourceParityCoordinator`/
`LegacyClientResourceParityReaders`, unchanged by this slice.

**What this does and does not imply for a dedicated server**: because the four pure types have zero
Minecraft/Fabric dependency of any kind, loading any one of them server-side — if something ever did
reference one — would not by itself be a classloading failure; a pure type with no Minecraft import is
exactly as safe to load on a dedicated server as any ordinary `java.util`-only class. The actual
dedicated-server risk this checklist item guards against is narrower and specific to
`ClientResourceParityLogObserver`: it is the one type that would fail to load if the server's
classloader ever tried to resolve it, because Fabric's client/server split does not put client-only
dependencies on the dedicated-server classpath at all. No common/server initializer
(`Totality.onInitialize()`, `ModEvents.register()`, any server-side registration path) references
`ClientResourceParityLogObserver` — confirmed by a repository-wide search: the only source references
outside its own package are `TotalityClient.java` (the client-only entrypoint wiring) and one Javadoc
`{@code ...}` mention inside a sibling test file's class comment (not an import or call).

Item K of the manual checklist (§16) restates this as an explicit live check: start a dedicated server
and confirm no `ClassNotFoundException`/`NoClassDefFoundError` referencing any of the five types —
`ClientResourceParityLogObserver` (the type actually at risk), `ClientResourceParityLogTransition`,
`ClientResourceParityLogTransitionTracker`, `ClientResourceParitySummaryText`, or
`ClientResourceParityLogDiagnostics` (the four pure types, included for completeness of the check, not
because any of them is independently expected to fail).

---

## 15. Manual checklist — see §16 below (full detail)

---

## 16. Manual validation checklist — NOT PERFORMED IN THIS SESSION

None of the following was performed or fabricated in this automated session. This checklist is
built from the real, committed Phase 3B-2C implementation plus what Phase 3B-2B's own completed
manual validation (`TOTALITY_RESOURCE_API_PHASE_3B2B_CLIENT_PARITY_INTEGRATION_REPORT.md` §30,
2026-07-25) already established as historical input — cited explicitly below where relevant, never
presented as though re-observed by this session.

**A. Normal settled state**
Start a client, join a world, allow all four Resources to settle to `EXACT_MATCH` (Phase 3B-2B §30.1
already observed this settling behavior for the parity classification itself). Expected: no
persistent-mismatch DEBUG entry appears in the log for Mana, Stamina, spell slots, or Rage.

**B. Stamina spend/regeneration**
Spend and regenerate Stamina through ordinary gameplay (Phase 3B-2B §30.2 already observed this stays
`EXACT_MATCH`). Expected: no persistent-entry log; ordinary transient `TRANSITIONAL_MISMATCH` ticks
(if any occur) must not spam the log — only a `PERSISTENT_MISMATCH` transition would.

**C. Rage spend**
Spend a Rage charge such that generic and legacy values continue to match (Phase 3B-2B §30.3 already
observed this stays `EXACT_MATCH`). Expected: no persistent-entry log.

**D. Dimension transfer with current Rage state 1/2**
Already confirmed live behavior (Phase 3B-2B §30.5): generic Rage remains 1/2, legacy Rage becomes
0/0, classification becomes `PERSISTENT_MISMATCH`, the existing legacy Rage HUD disappears. Expected
Phase 3B-2C logging behavior (not yet observed): exactly one DEBUG entry line for Rage entering
persistent mismatch, at the moment the classification first becomes `PERSISTENT_MISMATCH` (which per
the tracker's own T/T+1/T+2 grace semantics is two ticks after the mismatch first appears, not
immediately at the dimension-change tick) — and no repeated per-tick Rage log while it remains 0/0.
Mana, Stamina, and spell slots must produce no unexpected persistent logs during the same transfer.

**E. Return to Overworld — CORRECTED (§22, §23)**
Already observed (Phase 3B-2B §30.5): the Rage mismatch remains persistent after returning. Expected:
no duplicate entry log merely because another dimension transition occurred while the same persistent
episode remained continuously active. Mechanism (corrected description, second correction): `ClientResourceParityCoordinator.clear()`
runs on `AFTER_CLIENT_LEVEL_CHANGE` and rebuilds the parity *observation* from scratch — this rebuild
passes through `GENERIC_NOT_READY` → `TRANSITIONAL_MISMATCH` → `PERSISTENT_MISMATCH` again, not
straight back to `PERSISTENT_MISMATCH`. `ClientResourceParityLogObserver`'s own diagnostic *episode*
memory (§7, part B) is **not** cleared by `AFTER_CLIENT_LEVEL_CHANGE` (first correction) **and**, per
the corrected episode-continuation rules (§23), none of those three intermediate/final classifications
closes an already-active episode — `GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH` are observation
uncertainty and an ordinary within-grace mismatch respectively, not a recovery, so they return `NONE`
and keep the episode active; the rebuilt `PERSISTENT_MISMATCH` then also returns `NONE` (never a
fresh entry, since the episode was never closed). This manual check should confirm **no** log line
appears anywhere during this rebuild — not a false recovery at the `GENERIC_NOT_READY`/
`TRANSITIONAL_MISMATCH` stage, and not a second entry at the final `PERSISTENT_MISMATCH`. (The
original pre-first-correction implementation would have failed this check by wiping the episode
outright — §22.1; the implementation after only the first correction, before the second, would have
failed it differently — a false recovery followed by a false re-entry, §23.1 — while potentially
still leaving the final tracked state looking superficially correct.)

**F. Death and respawn — CORRECTED (§22, §23)**
Already observed (Phase 3B-2B §30.6): legacy Rage returns to 1/2, generic Rage remains 1/2,
classification returns to `EXACT_MATCH`, the existing Rage HUD returns. Expected Phase 3B-2C logging
behavior (not yet observed): exactly one Rage recovery DEBUG entry, no repeated recovery logs.
Mechanism (corrected description): a respawn's fresh `LocalPlayer` clears `ClientResourceParityCoordinator`'s
tracker (via the `LocalPlayer`-identity-change path) and rebuilds the observation to `EXACT_MATCH`;
`ClientResourceParityLogObserver`'s diagnostic episode memory is deliberately **not** cleared by this
path either, so it still recognizes the still-active Rage episode from the original dimension-transfer
entry (which, per §23's corrected rules, survived every intermediate `GENERIC_NOT_READY`/
`TRANSITIONAL_MISMATCH` rebuild reading along the way) and correctly emits exactly one recovery line
correlated to that episode, since `EXACT_MATCH` is a stable exit classification. This manual check
should confirm the recovery log fires exactly once here — not zero (a silently dropped recovery) and
not duplicated.

**G. Disconnect/null player**
Disconnect to a no-player screen (Phase 3B-2B §30.8 already observed the parity snapshot itself
becomes and stays empty, with no exception). Expected: transition-memory state also clears (via
`ClientResourceParityLogObserver.clear()` on `DISCONNECT`); no fabricated recovery log; no exception;
no logging loop while no player exists — `tick()` simply iterates an empty snapshot.

**H. Reconnect**
Reconnect after G (Phase 3B-2B §30.4 already observed the four observations rebuild and settle to
`EXACT_MATCH`). Expected: the initial `GENERIC_NOT_READY` readiness period creates no log; once the
four observations rebuild and settle, no log appears for the ordinary settling sequence.

**I. FormulaResolver Mana path**
Status from Phase 3B-2B (§30.7): the tested ordinary Grimoire spell did not invoke
`FormulaResolver.tryCast`; its breakpoint did not trigger; Mana remained `EXACT_MATCH`; current
gameplay reachability of that source-level gap was **not** established. This Phase 3B-2C checklist:
exercise the `FormulaResolver.tryCast` rune-casting path only if a concrete live route can be
identified during manual validation; if exercised and it produces a stale legacy Mana value long
enough to reach `PERSISTENT_MISMATCH`, confirm exactly one entry log appears. Otherwise, record this
item as **not manually exercised** — do not claim the missing source-level legacy Mana sync was
disproven, and do not modify `FormulaResolver`.

**J. Rage maximum-change path**
The readiness audit and Phase 3B-2B (§23 item 3) identified that `PlayerChargesComponent.applySyncPacket`
discards a new maximum for an already-existing legacy Rage pool. Prepare a focused manual test for
increasing Barbarian Rage maximum during the same connection, **only if a safe existing class-level/
admin route is available** (e.g. an existing, already-implemented class-level-up flow — do not add a
new command or gameplay mutation solely to make this test possible). Expected possible result: generic
maximum updates, legacy maximum remains stale, parity enters `PERSISTENT_MISMATCH`, one entry log
appears. If no safe existing route exists, record this item as **not manually exercised**.

**K. Dedicated server**
Start a dedicated server. Expected: normal startup (Phase 3B-2B §30.10 already confirmed this for the
pre-3B-2C client-only classes, reaching `Done (...)!`); no `ClassNotFoundException`/`NoClassDefFoundError`
referencing `ClientResourceParityLogObserver`, `ClientResourceParityLogTransition`,
`ClientResourceParityLogTransitionTracker`, `ClientResourceParitySummaryText`, or
`ClientResourceParityLogDiagnostics` — the five production types this slice added (§3/§14). Per §14's
corrected framing: `ClientResourceParityLogObserver` is the one type actually at risk of a
classloading failure (it is client-only and nothing server-side should ever reference it); the four
pure types have no Minecraft dependency and would load safely even if something did reference them —
this check simply confirms nothing does.

**L. Actual DEBUG log line inspection**
Not part of Phase 3B-2B's checklist (logging did not exist yet) — new for Phase 3B-2C. With the
client log level set to DEBUG for the `totality` logger, confirm the entry/recovery lines rendered in
§8's exact templates appear at the moments described in D/F above, and confirm no other parity-related
DEBUG line appears outside those moments (i.e., confirm the absence claims in A/B/C/E/H are actually
absence, not merely "not specifically looked for").

---

## 17. Confirmed Phase 3B-2B Rage dimension-transfer finding — HISTORICAL INPUT, CITED NOT REPEATED

Per `TOTALITY_RESOURCE_API_PHASE_3B2B_CLIENT_PARITY_INTEGRATION_REPORT.md` §30.5 (2026-07-25 manual
validation, already completed): before dimension transfer, generic Rage 1/2, legacy Rage 1/2,
`EXACT_MATCH`. After entering the Nether: generic Rage remains 1/2, legacy Rage reads 0/0,
classification becomes `PERSISTENT_MISMATCH`, the existing legacy Rage HUD disappears. Returning to
the Overworld does not repair the legacy state. This is a confirmed live legacy Rage
dimension-resynchronization bug, detected (not caused) by the parity system, and remains completely
unfixed by Phase 3B-2C — no change was made to `PlayerChargesComponent`, dimension-change event
handlers, or any legacy sync path in this session.

---

## 18. FormulaResolver live-test limitation — HISTORICAL INPUT, RISK UNCHANGED

Per the same report's §30.7: an ordinary tested Grimoire spell did not invoke `FormulaResolver.tryCast`
(breakpoint did not trigger); Mana remained `EXACT_MATCH` throughout that manual pass. The
source-level gap (`FormulaResolver.tryCast` spends Mana via `PlayerManaManager.removeMana` without
sending the legacy `SyncManaPayload`, confirmed unchanged and untouched in every phase including this
one) remains **unfixed and unproven either way** — live gameplay reachability of that exact code path
was not established by Phase 3B-2B and is not established by this Phase 3B-2C implementation session
either (no manual validation was performed here at all, per this task's explicit constraint). See
checklist item I (§16) for how a future manual pass should treat this.

---

## 19. Explicit out-of-scope confirmation — CONFIRMED FACT

Confirmed by direct inspection of this session's full diff:
- **Resource authority/values/formulas/mutation**: unchanged — no adapter, definition, component, server-tick, or packet-handling file was touched.
- **Packet ordering/payloads/sync eligibility/trust calculation**: unchanged — no networking class was touched; `ClientResourceSyncManager`/`ClientResourceSyncBridge`/every packet payload class are byte-for-byte as Phase 3B-2B left them.
- **Parity comparison semantics/grace-period semantics**: unchanged — `ClientResourceParityTracker`, the three comparators/policy, and `ClientResourceParityPoll` are byte-for-byte unchanged; the new code only ever *reads* their already-committed output via the existing `ClientResourceParityObservations` snapshot.
- **Lifecycle behavior for `ClientResourceParityCoordinator`/`ClientResourceParityTracker`/`ClientResourceParityLifecycle`/`ClientResourceParityPoll`**: unchanged by either the original implementation or this correction pass — confirmed by empty `git diff --stat` against all four (§22.6). Only `ClientResourceParityLogObserver`'s own separate diagnostic-episode reset wiring was ever touched, and this correction pass narrowed it (removed one of its three registrations) rather than widening it — see §7 and §22.
- **HUD/class/spell casting/Rage/legacy-mirror behavior**: unchanged — no HUD, screen, menu, radial, tooltip, movement, class, spell, or legacy-manager file was touched, in the original implementation or in this correction pass.
- **No fix applied** to the Rage dimension-transfer mismatch (§17), the Rage-maximum-sync gap, the missing `FormulaResolver` legacy Mana sync (§18), the missing Mana join-time push, the ancestry/origin dimension-transfer issue, `ProvisionerEntityBackedSmokeTest`, or `OffhandAttackVerification`/Stamina, or any Food/Hunger issue — none of these files were opened for editing at any point, including this correction pass. (The correction fixes this diagnostic *logging* system's own lifecycle defect — never a legacy Resource bug.)
- **No INFO/WARN/ERROR logging, no per-tick spam, no chat/toast/HUD/screen/tooltip output, no gameplay restriction, no parity debug command, no packet export, no file logging, no telemetry, no consumer migration, no legacy packet/manager removal** anywhere in the new code — confirmed by grep across all 5 production files: the only logging call site is the single `Totality.LOGGER.debug(...)` pair inside `ClientResourceParityLogObserver.processObservation` (the current name — see §23.4 for the rename from the original `logTransitionIfAny`), unchanged in shape by either correction.
- **No Phase 3B-3 or Phase 3C code** — no debug command, no verification tooling, no consumer migration exists anywhere in the source, before or after this correction pass.

---

## 20. Phase 3B-3 readiness criteria — pending manual exercise/review

Phase 3B-2C's automated slice is complete and self-consistent, but Phase 3B-3 readiness depends on
work this session explicitly could not perform:

- **Pending**: the full manual checklist in §16 (items A-L), none of which was exercised in this
  automated session — this now includes confirming the corrected §7/§22/§23 dimension-reset and
  respawn-recovery behavior actually holds in a real client, not merely in the automated regression
  simulations added in §22.4/§23.4.
- **Resolved by the first correction pass, still pending live confirmation**: the dimension-transfer
  duplicate-entry defect (§22.1) that would have violated checklist item E is fixed in source.
- **Resolved by this second correction pass, still pending live confirmation**: the false-recovery/
  duplicate-re-entry defect (§23.1) in the episode-continuation logic — checklist items D/E/F should
  specifically confirm the corrected step-by-step behavior (no log during the `GENERIC_NOT_READY`/
  `TRANSITIONAL_MISMATCH` rebuild, no duplicate entry, exactly one eventual recovery) holds in a real
  Nether round-trip and respawn, not merely in the automated `decide(...)`-driven simulation.
- **This second correction pass itself has not yet received a further external review** — mirroring
  the correction passes every prior Phase 3B-2A/3B-2B/first-Phase-3B-2C-correction slice received
  before being treated as final.
- **Known, intentionally unfixed, and expected to still be observable once logging is manually
  verified**: the Rage dimension-transfer legacy mismatch (§17) and the `FormulaResolver`
  reachability gap (§18) — Phase 3B-3 should expect these to be the first real entry/recovery log
  pairs a live session produces, not new defects.
- **Not blocking, but worth carrying forward**: no dedicated-server empirical run was performed in
  this session (§14) — only source/import inspection. Checklist item K covers this.

Phase 3B-2C should not be treated as ready for Phase 3B-3 until at minimum checklist items A, D, E, F,
and K (§16) have been manually exercised and their results recorded, consistent with how Phase 3B-2A
and Phase 3B-2B were each treated as provisional until their own external-review/manual-validation
passes completed.

---

## 21. Stop-point status (original implementation)

- Implementation, automated validation (`compileJava`, `compileTestJava`, `test`, `runDatagen`,
  `build`, `git diff --check`, classloading/import boundary inspection), this report, and the review
  bundle (see the final response) are complete.
- No manual validation was performed in this session, per the task's explicit instruction.
- Nothing was staged, committed, or pushed.
- Phase 3B-3 was not started. Phase 3C was not started.

**Superseded in part by §22 and §23 below** — the first external-review correction pass fixed a
genuine lifecycle defect in this original implementation (§22.1); the second fixed a further genuine
episode-continuation-logic defect the first correction's own fix did not catch (§23.1) — both before
any manual validation or commit occurred.

---

# EXTERNAL REVIEW CORRECTION PASS — 2026-07-28

## 22. Correction: diagnostic episode memory must be connection-scoped, not dimension-scoped

### 22.1 The defect

As originally implemented, `ClientResourceParityLogObserver.clear()` was registered against three
Fabric client lifecycle events in `TotalityClient.java`: `ClientPlayConnectionEvents.JOIN`,
`ClientPlayConnectionEvents.DISCONNECT`, **and** `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`. This
matched `ClientResourceParityCoordinator.clear()`'s own three registrations exactly — a reasonable
first guess, but wrong for a fundamentally different reason: the coordinator's tracker holds
*comparison state* that is genuinely meaningless across a dimension change (stale generic/legacy
values from the old dimension must never be compared against the new one), but the log observer's
episode memory holds a *reporting* fact — "has this resource's persistent mismatch already been
reported" — which has nothing to do with which dimension the player is currently in.

Concretely, this broke the confirmed live Rage dimension-transfer sequence (§17). **Corrected
chronology (§23 also corrects an earlier draft of this same passage, which incorrectly implied the
persistent episode already existed before the dimension transfer)**:
0. Before the transfer, in the Overworld, Rage is `EXACT_MATCH` (generic 1/2, legacy 1/2) — no
   episode exists yet.
1. The player enters the Nether. The tracker rebuilds through `GENERIC_NOT_READY` →
   `TRANSITIONAL_MISMATCH` → `PERSISTENT_MISMATCH` (legacy Rage has become 0/0) — one entry logged
   here, both the coordinator's tracker and the observer's episode memory now reflect it.
2. The player returns to the Overworld. `AFTER_CLIENT_LEVEL_CHANGE` fires, clearing **both** the
   coordinator's tracker (correct) **and** the observer's episode memory (incorrect — this is the
   defect). The tracker rebuilds through the same `GENERIC_NOT_READY` → `TRANSITIONAL_MISMATCH` →
   `PERSISTENT_MISMATCH` sequence; the real-world Rage mismatch has not changed. But because the
   observer's episode memory was wiped, it has no record that this resource was already reported, so
   it logs a **second, spurious entry**. This directly violates the task's confirmed-expected item 9
   ("No second entry DEBUG line may appear" on returning to the Overworld).
3. Symmetrically, at respawn: the coordinator's tracker clears and rebuilds to `EXACT_MATCH`, but
   the observer's episode memory was already wiped by the dimension-change event that happened
   earlier in the same sequence — so the recovery transition has no prior "entered" state to
   correlate against, and either logs nothing (a silently dropped recovery) or, depending on exact
   ordering, could misrepresent the transition. Either way, the required "exactly one recovery DEBUG
   line" guarantee (item 13) was not reliably satisfiable.

### 22.2 The fix

`ClientResourceParityLogObserver.clear()`'s registration was narrowed to exactly two Fabric events —
`ClientPlayConnectionEvents.JOIN` and `ClientPlayConnectionEvents.DISCONNECT` — and its
`AFTER_CLIENT_LEVEL_CHANGE` registration was removed entirely from `TotalityClient.java`. The
diagnostic episode memory is now genuinely connection-scoped: it survives every dimension change and
every `LocalPlayer` replacement (respawn) within the same connection, and is only ever forgotten on a
genuine new connection (JOIN) or a disconnect. `ClientResourceParityCoordinator.clear()`'s own three-
plus-`LocalPlayer`-replacement registration set is completely unchanged — it still must reset on all
of those triggers, for the unrelated, legitimate reason given in §22.1. See the corrected §7 above for
the full mechanism, restated with the exact confirmed live sequence walked through step by step.

### 22.3 Generic transition tracker

Independently, `ClientResourceParityLogTransitionTracker` previously imported
`net.minecraft.resources.Identifier` directly and hard-coded `Map<Identifier, Boolean>` as its
backing state — while its own Javadoc claimed "No Minecraft/Fabric/logging dependency." The claim was
correct in spirit (the class never touches any Minecraft *behavior*, only uses `Identifier` as an
inert map key) but wrong in fact once read literally, and the task required the implementation to
match the claim rather than weakening the claim.

**Fix**: the class is now `ClientResourceParityLogTransitionTracker<K>`, generic over its key type,
backed by `Map<K, Boolean>`. Its public API (`classify`, `clear`, `clearAll`) is unchanged in shape
beyond the type parameter; `Objects.requireNonNull` guards are unchanged. It imports nothing beyond
`java.util.LinkedHashMap`/`Map`/`Objects` — confirmed by direct inspection and by
`trackerSourceImportsNoMinecraftFabricLoggerClientNetworkingOrLegacyManagerType`, which scans its own
source for `net.minecraft`/`fabricmc`/`Logger`/`LocalPlayer`/`networking`/legacy-manager tokens (in
both import lines and the class body) and fails if any appear.

`ClientResourceParityLogObserver` (the one client-only boundary that needs a concrete key type)
instantiates it as `ClientResourceParityLogTransitionTracker<Identifier>` — `Identifier` was
deliberately **not** replaced with `Object` or a raw `String` at that boundary, per the task's
explicit instruction; the boundary retains full type safety, only the pure class beneath it gave up
its accidental Minecraft-specific hard-coding.

### 22.4 Regression tests added

7 new tests across two files (773 pre-correction + 7 = 780 total):

| Test | File | Proves |
|---|---|---|
| `trackerWorksWithAnArbitraryNonIdentifierKeyType` | `ClientResourceParityLogTransitionTrackerTest` | The tracker is genuinely generic — a plain `String` key exercises the identical entry/recovery/steady-state behavior the `Identifier`-keyed tests cover. |
| `trackerSourceImportsNoMinecraftFabricLoggerClientNetworkingOrLegacyManagerType` | `ClientResourceParityLogTransitionTrackerTest` | Source-scans the tracker's own file for forbidden tokens in both import lines and the class body — required-coverage item 6. |
| `explicitClearRepresentsAConnectionResetSoTheNextPersistentObservationIsTreatedAsANewEntry` | `ClientResourceParityLogObserverTest` | `clear()` (JOIN/DISCONNECT) clears episode memory, fabricates no recovery, and the next persistent observation is a fresh entry — required-coverage item 4. |
| `clearingOnlyTheParityObservationTrackerDoesNotProduceASecondEntryForAContinuingPersistentMismatch` | `ClientResourceParityLogObserverTest` | Simulates the exact confirmed dimension-transfer sequence: seeds a persistent episode, consumes its entry, clears **only** `ClientResourceParityCoordinator.tracker()` (never the observer), rebuilds the same resource through a fresh readiness/transitional/persistent sequence, and proves no fresh entry remains to consume — required-coverage item 2. This is a **simulation of the coordinator-level reset**, not a real dimension change; it proves the diagnostic logic is correct given that reset pattern, not that Fabric actually delivers events in this order (that remains a manual-validation item, §16 D/E). |
| `sameEpisodeSurvivesAParityTrackerResetAndLaterRecoversExactlyOnce` | `ClientResourceParityLogObserverTest` | Simulates the death/respawn tail of the same sequence: after the entry, clears only the parity tracker, rebuilds to `EXACT_MATCH`, and proves the observer's next `tick()` produces exactly one recovery (and that further steady-state exact-match ticks produce no more) — required-coverage item 3. Same simulation caveat as above. |
| `allFourProductionResourceIdsAreTrackedThroughTheTypedObserver` | `ClientResourceParityLogObserverTest` | All four production `Identifier` keys (Mana/Stamina/spell slots/Rage) reach `PERSISTENT_MISMATCH` and are each independently consumed by one `tick()` pass through the typed `ClientResourceParityLogTransitionTracker<Identifier>` integration — required-coverage item 7. |
| `totalityClientRegistersObserverClearOnlyOnJoinAndDisconnectNeverOnDimensionChange` | `ClientResourceParityLogObserverTest` | Reads `TotalityClient.java`'s own source and confirms `ClientResourceParityLogObserver.clear()` appears exactly twice (JOIN, DISCONNECT), and that no `AFTER_CLIENT_LEVEL_CHANGE.register(...)` statement anywhere in the file mentions it — required-coverage item 5. |

**What these tests do *not* prove, stated explicitly per the task's instruction not to pretend to
verify logger-call counts through unrelated state**: none of these tests intercepts or counts actual
`Totality.LOGGER.debug(...)` invocations — no SLF4J test-capture/appender exists in this codebase (see
§9's original reasoning, unchanged by this correction). Each test instead proves its claim at the
level the code actually makes the decision: by directly re-querying
`ClientResourceParityLogObserver.transitions()` (the same package-private `ClientResourceParityLogTransitionTracker<Identifier>`
instance `tick()` itself uses) after driving `tick()`, and confirming the pure decision function
(`classify`) returns `NONE`/the expected transition for a steady-state re-observation. Since the
observer's only two `Totality.LOGGER.debug(...)` call sites are unconditionally gated behind exactly
these two transition values (confirmed by direct source reading, §9), this is a complete and accurate
proof of logging behavior — not a proxy for unrelated state — but it is a proof by construction of the
gating logic, not an observed log record.

### 22.5 Two test-only defects found and fixed while validating this correction

Both were bugs in the *new regression tests themselves*, discovered by their own first failing run —
never a production-code defect, and neither required any change to `ClientResourceParityLogObserver.java`
or `ClientResourceParityLogTransitionTracker.java` beyond what §22.1-22.3 already describe:

1. **CRLF-sensitive raw string match.** `totalityClientRegistersObserverClearOnlyOnJoinAndDisconnectNeverOnDimensionChange`'s
   original assertion embedded a literal `"...->\n                zcylas...."` substring to match
   against `TotalityClient.java`'s source — but this repository checks out that file with CRLF line
   endings (`core.autocrlf=true`, consistent with every prior phase's line-ending notes), so the raw
   `\n` never matched the file's actual `\r\n`. **Fix**: the test now normalizes
   `Files.readString(path).replace("\r\n", "\n")` once before any substring comparison, documented
   inline.
2. **Self-referential forbidden-token check.** `trackerSourceImportsNoMinecraftFabricLoggerClientNetworkingOrLegacyManagerType`'s
   first version failed against the tracker's *own* explanatory Javadoc, which (before this fix)
   literally wrote out `net.minecraft.resources.Identifier` and `LocalPlayer` while describing the
   defect being fixed — tripping the very token scan the comment was explaining. **Fix**: reworded
   the two Javadoc passages in `ClientResourceParityLogTransitionTracker.java` to describe the same
   facts without embedding the literal forbidden substrings (e.g. "Minecraft's own resource-location
   key type" instead of the fully-qualified name, "a player-identity replacement" instead of
   `LocalPlayer`) — a wording change only, no semantic change to the Javadoc's meaning.

Both were caught by the first `test --rerun` of this correction pass (2 failures, 778 passing out of
780 attempted) and fixed before any further validation step ran; the final `test --rerun` reported in
§10 is the clean, all-780-passing run after both fixes.

### 22.6 Confirmation: no other file touched by this correction

`git diff --stat` against every existing Phase 3B-2A/3B-2B production class
(`ClientResourceParityTracker`, `ClientResourceParityObservation`, `ClientResourceParitySummary`,
`ClientResourceParityOutcome`, `ClientResourceParityClassification`, `ClientResourceScalarParityComparator`,
`ClientSpellSlotParityComparator`, `ClientRageParityPolicy`, `ClientGenericParitySummaryMapper`,
`ClientResourceParityPoll`, `ClientResourceParityLifecycle`, `ClientResourceParityCoordinator`,
`LegacyClientResourceParityReaders`, `ClientResourceParityObservations`, `ClientResourceSyncManager`,
`FormulaResolver`, `PlayerChargesComponent`) returns empty output — confirmed unchanged by this
correction pass. `ClientResourceParityLogDiagnostics.java`, `ClientResourceParityLogTransition.java`,
`ClientResourceParitySummaryText.java`, and their two test files are also byte-for-byte unchanged by
this correction (verified against the original `TOTALITY_RESOURCE_API_PHASE_3B2C_REVIEW_BUNDLE.zip`).
Exactly five files were changed by this correction: `TotalityClient.java`,
`ClientResourceParityLogTransitionTracker.java`, `ClientResourceParityLogObserver.java`,
`ClientResourceParityLogTransitionTrackerTest.java`, and `ClientResourceParityLogObserverTest.java` —
plus this report as a sixth.

### 22.7 Stop-point status (correction pass)

- Correction implementation, regression tests, fresh automated validation (`compileJava`,
  `compileTestJava`, `test`, `runDatagen`, `build`, `git diff --check`), this report update, and the
  correction review bundle (see the final response) are complete.
- No manual validation was performed in this correction pass, per the task's explicit instruction —
  §16's checklist remains entirely unexercised; only its items D/E/F's expected-mechanism text was
  updated to describe the corrected implementation.
- No legacy Resource bug was fixed — §17/§18's known mismatches remain completely unfixed and
  untouched.
- Nothing was staged, committed, or pushed.
- Phase 3B-3 was not started. Phase 3C was not started.

---

# SECOND EXTERNAL REVIEW CORRECTION PASS — 2026-07-28

## 23. Correction: episode-continuation semantics for GENERIC_NOT_READY/TRANSITIONAL_MISMATCH

### 23.1 The defect

The first correction (§22) correctly made the diagnostic episode memory connection-scoped, but left
`ClientResourceParityLogTransitionTracker.classify`'s actual decision logic unchanged: while an
episode was active, *any* classification other than `PERSISTENT_MISMATCH` closed it and produced
`RECOVERED_FROM_PERSISTENT_MISMATCH`. This is correct for `EXACT_MATCH`, but wrong for
`GENERIC_NOT_READY` and `TRANSITIONAL_MISMATCH` — both of which the parity tracker's own rebuild
sequence (after a dimension change or a respawn's `LocalPlayer` replacement) routes through on its
way back to `PERSISTENT_MISMATCH`, for a mismatch that never actually resolved.

Concretely, using the corrected chronology (§23.3 restates this precisely — an earlier draft of this
report incorrectly implied the persistent episode already existed before the dimension transfer):

1. Rage enters the Nether: the tracker rebuilds `GENERIC_NOT_READY` then `TRANSITIONAL_MISMATCH` then
   `PERSISTENT_MISMATCH`; one legitimate entry is logged at the end of that sequence.
2. Rage returns to the Overworld: the tracker rebuilds through the same three-step sequence (the
   real-world mismatch is unchanged). With the pre-second-correction logic: the `GENERIC_NOT_READY`
   observation closes the still-active episode from step 1 (a false recovery — nothing actually
   recovered), and the subsequent `PERSISTENT_MISMATCH` observation then reopens it (a false,
   duplicate entry). Depending on exactly which intermediate observations reached the observer's
   `tick()`, this could produce a spurious recovery-then-entry pair, or in adversarial timing a
   recovery with no matching entry — and per the task's own caution, this can happen even when the
   *final* tracked boolean state ("episode active") looks identical to the correct outcome, silently
   masking the defect from a test that only checks final state.

This directly violated the required behavior: no recovery merely because the generic side is
temporarily not ready, and no duplicate entry when the same mismatch returns after rebuild.

### 23.2 The fix

`ClientResourceParityLogTransitionTracker.classify` now uses an exhaustive `switch` (no `default`
arm) over every `ClientResourceParityClassification` constant when an episode is active:

- `PERSISTENT_MISMATCH` — episode continues, returns `NONE`.
- `GENERIC_NOT_READY` — episode continues (corrected), returns `NONE`.
- `TRANSITIONAL_MISMATCH` — episode continues (corrected), returns `NONE`.
- `EXACT_MATCH` — episode closes, returns `RECOVERED_FROM_PERSISTENT_MISMATCH`.
- `MODEL_MISMATCH` — episode closes, returns `RECOVERED_FROM_PERSISTENT_MISMATCH`.
- `EXPECTED_SEMANTIC_DIFFERENCE` — episode closes, returns `RECOVERED_FROM_PERSISTENT_MISMATCH`.
- `NOT_APPLICABLE` — episode closes, returns `RECOVERED_FROM_PERSISTENT_MISMATCH`.

`GENERIC_NOT_READY` and `TRANSITIONAL_MISMATCH` are excluded from closing an episode because neither
represents a real change in the underlying comparison: the former is observation uncertainty (the
generic side has temporarily lost its full snapshot), the latter is still an ordinary mismatch, merely
within the elapsed-tick grace window before persistence is (re-)declared. The four "stable exit"
classifications (`EXACT_MATCH`, `MODEL_MISMATCH`, `EXPECTED_SEMANTIC_DIFFERENCE`, `NOT_APPLICABLE`)
all represent the previous persistent numeric-mismatch comparison no longer applying at all — a
genuine, stable end to the episode, even though only `EXACT_MATCH` is a "recovery" in the everyday
sense. No fifth `ClientResourceParityLogTransition` value was added for this distinction — the task's
required-coverage list does not call for one, and `RECOVERED_FROM_PERSISTENT_MISMATCH` accurately
means "the previously-reported episode is over" in all four cases.

The exhaustive `switch` (no `default`) is deliberate per the task's explicit instruction: if a future
classification is ever added to `ClientResourceParityClassification`, this method fails to compile
until its semantics for that new value are explicitly decided — it can never silently fall through to
an unexamined, possibly-incorrect default.

No change to `ClientResourceParityClassification` or `ClientResourceParityTracker` — both remain
byte-for-byte unchanged (empty `git diff --stat`), per the task's explicit constraint. Only the
diagnostic-episode decision logic in `ClientResourceParityLogTransitionTracker` changed.

### 23.3 Corrected Rage chronology (restated precisely, supersedes any earlier phrasing)

- **Overworld, before the transfer**: generic Rage 1/2, legacy Rage 1/2, `EXACT_MATCH`. No episode
  exists.
- **Entering the Nether**: generic Rage remains 1/2; legacy Rage becomes 0/0. The tracker rebuilds
  `GENERIC_NOT_READY` then `TRANSITIONAL_MISMATCH` then `PERSISTENT_MISMATCH`. The first
  persistent-mismatch episode begins here — one entry is expected, logged only at the final
  `PERSISTENT_MISMATCH` step, not at either intermediate reading.
- **Returning to the Overworld**: the mismatch remains (legacy Rage stays 0/0 — a confirmed, unfixed,
  pre-existing legacy bug, §17). The tracker rebuilds through the identical three-step sequence. Per
  §23.2's corrected rules, none of these three observations may create a recovery or re-entry log —
  no second entry is expected, and no false recovery at the intermediate steps either.
- **Death/respawn**: legacy Rage returns to 1/2; the rebuilt observation is `EXACT_MATCH`, a stable
  exit classification, so the episode from the Nether-entry step closes — one recovery is expected.

Every earlier passage in this report that could be read as implying the persistent episode already
existed before the dimension transfer (§7 step 1's original wording, §22.1's original step 1) has been
corrected in place to match this chronology exactly.

### 23.4 Regression tests

**Pure tracker level** (`ClientResourceParityLogTransitionTrackerTest`, net +4: 2 replaced, 6 added):

- `persistentToGenericNotReadyProducesOneRecoveryEvent` (asserted `GENERIC_NOT_READY` closes an
  active episode) replaced by `persistentToGenericNotReadyReturnsNoneAndKeepsTheEpisodeActive`, which
  asserts `NONE` and then proves the episode is still active (not merely that this one call returned
  `NONE`) via a follow-up `PERSISTENT_MISMATCH` observation that must also return `NONE`.
- `persistentThenTransitionalThenPersistentLogsRecoveryThenEntryAgain` (asserted `TRANSITIONAL_MISMATCH`
  closes an active episode) replaced by `persistentStaysActiveThroughTransitionalWithoutClosingTheEpisode`,
  asserting `NONE` throughout and that a subsequent `PERSISTENT_MISMATCH` is never a fresh entry.
- New: `persistentToTransitionalMismatchReturnsNoneAndKeepsTheEpisodeActive` — same "still active"
  proof pattern, via a follow-up `EXACT_MATCH` that must be a real recovery.
- New: `persistentToNotApplicableProducesOneRecoveryEvent` — the fourth stable-exit classification,
  previously only tested "alone" (no active episode), not as an episode-closing case.
- New: `fullDimensionRebuildSequenceProducesNoRecoveryAndNoDuplicateEntry` — drives the exact
  `PERSISTENT_MISMATCH` then `GENERIC_NOT_READY` then `TRANSITIONAL_MISMATCH` then
  `PERSISTENT_MISMATCH` sequence and asserts `NONE` at every step after the first entry.
- New: `fullDimensionRebuildSequenceFollowedByExactMatchProducesExactlyOneRecovery` — the same
  sequence, then `EXACT_MATCH` (exactly one recovery), then ten repeated `EXACT_MATCH` observations
  (no duplicate).

**Observer/integration level** (`ClientResourceParityLogObserverTest`, net -1: 2 removed, 1 added):

The two batched-observation tests from the first correction
(`clearingOnlyTheParityObservationTrackerDoesNotProduceASecondEntryForAContinuingPersistentMismatch`,
`sameEpisodeSurvivesAParityTrackerResetAndLaterRecoversExactlyOnce`) were removed — per the task's
explicit finding, they called `tick()` only after batching three `observe(...)` calls, then inferred
success solely from `transitions().classify(...)`'s final boolean state, which cannot distinguish "no
event occurred" from "a false recovery immediately followed by a fresh re-entry" (both leave the same
final active/inactive boolean — exactly the failure mode §23.1 describes).

Replacement: `dimensionRebuildSequenceDecidedStepByStepProducesNoFalseRecoveryAndNoDuplicateEntryThenExactlyOneRealRecovery`,
built on a new package-private seam:

- `ClientResourceParityLogObserver.decide(Identifier resourceId, ClientResourceParityObservation observation)`
  — the exact method `tick()` itself now calls internally (the previous private `logTransitionIfAny`
  was renamed to `processObservation` and now calls `decide` and then dispatches the DEBUG log based
  on its return value). `decide` performs no logging itself and does not introduce a second owner of
  transition state — it is a thin, one-line delegation to
  `ClientResourceParityLogTransitionTracker.classify`, package-private (never public, confirmed by a
  new reflection assertion in `observerExposesOnlyTickClearAndPackagePrivateTransitionsAccessor`).
- The new test drives one real `ClientResourceParityCoordinator.tracker().observe(...)` call per
  step, then calls `decide(...)` on that exact observation and asserts the exact
  `ClientResourceParityLogTransition` returned — for every one of: the original entry; `clearAll()`
  on the tracker (simulating a dimension-change/respawn reset); `GENERIC_NOT_READY` (asserted `NONE`);
  a fresh `MISMATCH` observation that is `TRANSITIONAL_MISMATCH` (asserted `NONE`); a second
  `MISMATCH` still `TRANSITIONAL_MISMATCH` (asserted `NONE`); a third `MISMATCH` that reaches
  `PERSISTENT_MISMATCH` (asserted `NONE` — no duplicate entry); a `MATCH` observation (asserted
  `RECOVERED_FROM_PERSISTENT_MISMATCH` — exactly one real recovery); and ten further `MATCH`
  observations (each asserted `NONE`). This satisfies the task's explicit requirement to exercise the
  real decision path after every intermediate state, not batch observations before one `tick()` call,
  and to distinguish "no event"/"recovery"/"entry" even when the final tracker-memory boolean would be
  identical across a correct and an incorrect implementation.

Unaffected, confirmed still passing: every other existing test in both files — including the "no
active episode" alone-tests, the independent-resources test, the four-production-ids test, the
connection-clear tests, and the readiness-period tests — none of which exercised the now-corrected
active-episode continuation logic in a way the fix changes.

### 23.5 Confirmation: no other file touched by this second correction pass

Diffing the current working tree against the first correction review bundle
(`TOTALITY_RESOURCE_API_PHASE_3B2C_CORRECTION_REVIEW_BUNDLE.zip`) confirms exactly four files changed:
`ClientResourceParityLogTransitionTracker.java`, `ClientResourceParityLogObserver.java`,
`ClientResourceParityLogTransitionTrackerTest.java`, and `ClientResourceParityLogObserverTest.java` —
plus this report as a fifth. `TotalityClient.java`, `ClientResourceParityLogTransition.java`,
`ClientResourceParitySummaryText.java`, `ClientResourceParityLogDiagnostics.java`, and both of their
test files are byte-for-byte unchanged by this pass. `git diff --stat` against every existing Phase
3B-2A/3B-2B production class (`ClientResourceParityTracker`, `ClientResourceParityObservation`,
`ClientResourceParitySummary`, `ClientResourceParityOutcome`, `ClientResourceParityClassification`,
`ClientResourceScalarParityComparator`, `ClientSpellSlotParityComparator`, `ClientRageParityPolicy`,
`ClientGenericParitySummaryMapper`, `ClientResourceParityPoll`, `ClientResourceParityLifecycle`,
`ClientResourceParityCoordinator`, `LegacyClientResourceParityReaders`, `ClientResourceParityObservations`,
`ClientResourceSyncManager`, `FormulaResolver`, `PlayerChargesComponent`) returns empty output.

### 23.6 Final validation (this second correction pass)

- `compileJava`: `BUILD SUCCESSFUL`, no warnings.
- `compileTestJava`: `BUILD SUCCESSFUL`, no warnings.
- `test --rerun`: `BUILD SUCCESSFUL` on the first run. **783 tests, 0 failed, 0 errors, 0 skipped**
  (780 + 3 net).
- `runDatagen`: `BUILD SUCCESSFUL`, `349 -> 349`, `written: 0`.
- `build`: `BUILD SUCCESSFUL`, only the pre-existing Gradle/Loom deprecation notice.
- `git diff --check -- src/main/java src/test/java`: exit 0, no output.

### 23.7 Confirmations

- Connection-scoped episode memory (first correction, §22) remains unchanged — `clear()` still
  registered only on `ClientPlayConnectionEvents.JOIN`/`DISCONNECT`, confirmed unchanged by this pass
  (§23.5) and re-confirmed by the still-passing `totalityClientRegistersObserverClearOnlyOnJoinAndDisconnectNeverOnDimensionChange`.
- No false recovery during `GENERIC_NOT_READY` — §23.2's exhaustive switch, proven by
  `persistentToGenericNotReadyReturnsNoneAndKeepsTheEpisodeActive` and the full-rebuild-sequence tests.
- No false recovery during `TRANSITIONAL_MISMATCH` — same, proven by
  `persistentToTransitionalMismatchReturnsNoneAndKeepsTheEpisodeActive` and
  `persistentStaysActiveThroughTransitionalWithoutClosingTheEpisode`.
- No duplicate entry after a rebuild — proven by `fullDimensionRebuildSequenceProducesNoRecoveryAndNoDuplicateEntry`
  and the observer-level `dimensionRebuildSequenceDecidedStepByStepProducesNoFalseRecoveryAndNoDuplicateEntryThenExactlyOneRealRecovery`.
- `EXACT_MATCH` still emits exactly one recovery — unaffected, re-proven by the same tests plus the
  unchanged `persistentToExactMatchProducesOneRecoveryEvent`.
- No parity/sync/legacy class changed — §23.5.
- No UI, command, consumer migration, or gameplay mutation — confirmed by grep across all changed
  files (only `Totality.LOGGER.debug` in the observer, unchanged call-site shape from the first
  correction).
- No legacy Resource bug fixed — §17/§18 remain completely untouched and unfixed.
- No Phase 3B-3 or Phase 3C work — no debug command, no verification tooling, no consumer migration
  exists anywhere in the source.

### 23.8 Stop-point status (second correction pass)

- Correction implementation, regression tests, fresh automated validation, this report update, and
  the second correction review bundle (see the final response) are complete.
- No manual validation was performed in this correction pass, per the task's explicit instruction.
- Nothing was staged, committed, or pushed.
- Phase 3B-3 was not started. Phase 3C was not started.

---

# MANUAL VALIDATION — 2026-07-28

## 24. Manual validation confirmed

Manual, in-client validation performed by Stefan against the corrected Phase 3B-2C implementation
(the same 11-file state reviewed in the final documentation review bundle). This section records only
what was actually observed — no packet trace, exact timing beyond what was logged, packet ordering
measurement, hidden transition count, or raw component identity is claimed beyond what was reported.

### 24.1 DEBUG logger activation

DEBUG output was enabled through a local Loom development Log4j configuration — a tracked
`build.gradle` addition (`loom.log4jConfigs.from "log4j-dev.xml"`) plus an untracked project-root
`log4j-dev.xml`. Neither file is part of the Phase 3B-2C changed-file manifest; both are local
manual-validation support only and are excluded from this commit. `Totality.LOGGER.isDebugEnabled()`
behavior was effectively confirmed by the appearance of the expected DEBUG transition messages below.
An earlier attempt to enable DEBUG output via `-Dfabric.log.level=debug` in Minecraft's program
arguments did not work — Minecraft reported that value as ignored; the successful validation used the
Loom Log4j configuration instead.

### 24.2 Normal settled state

Before triggering the known Rage discrepancy, the four monitored Resources (Mana, Stamina, standard
spell slots, Rage) settled normally. Rage was: generic 1/2, legacy 1/2, classification `EXACT_MATCH`.
No persistent-entry or recovery log appeared in this normal settled state. No parity-created UI,
toast, chat message, screen, tooltip, command, or gameplay restriction appeared. Exact tick values for
this normal settled state were not separately recorded in this pass.

### 24.3 Nether entry — first persistent episode

Entering the Nether exposed the already-confirmed legacy Rage dimension-transfer bug (§17): generic
Rage remained 1/2, legacy Rage became 0/0, and Rage reached `PERSISTENT_MISMATCH`. Exactly one
persistent-entry DEBUG diagnostic appeared:

```
[22:35:34] [Render thread/DEBUG] (totality) Resource parity entered persistent mismatch: resource=totality:rage generic=1/2 legacy=0/0 firstMismatchTick=0 lastObservedTick=2
```

(Wrapped above only for report readability — the actual line was emitted as one log line.)

The existing legacy Rage HUD disappeared because the legacy mirror was 0/0; the parity logger did not
create or modify that HUD behavior — the HUD is existing, pre-Phase-3B-2C code reading the legacy
mirror directly. This confirms entry-transition logging. The mismatch was detected, not repaired; no
legacy synchronization fix is part of Phase 3B-2C.

### 24.4 Steady persistent state

The client remained in the mismatch for an extended period. No repeated persistent-entry line
appeared, no per-tick logging spam appeared, and no false recovery appeared. Mana, Stamina, and
standard spell slots produced no unexpected persistent-mismatch logs.

Final observed count for the first episode before recovery: Rage entries 1, duplicate entries 0,
false recoveries 0, other Resource parity logs 0.

### 24.5 Return to Overworld

Returning to the Overworld did not repair the legacy Rage mirror — the same persistent-mismatch
episode continued. The parity observation lifecycle rebuilt (per §23.3's confirmed chronology); the
temporary rebuild classifications did not generate a recovery, no second persistent-entry line
appeared, and no duplicate episode was created.

This manually confirms both external-review lifecycle corrections: (1) diagnostic episode memory
survives dimension replacement (§22), and (2) `GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH` do not close
an active persistent episode (§23). The exact intermediate classifications were not individually
viewed in a debugger during this pass — the absence of any false entry/recovery log across the
round-trip confirms the externally reviewed behavioral contract in the exercised lifecycle, without
claiming the intermediate states were separately inspected.

### 24.6 Death and respawn — recovery

Stefan died and respawned. Generic Rage remained 1/2, legacy Rage was restored to 1/2, classification
returned to `EXACT_MATCH`, and the existing Rage HUD returned. Exactly one recovery diagnostic
appeared, with no repeated recovery afterward:

```
[22:38:11] [Render thread/DEBUG] (totality) Resource parity recovered from persistent mismatch: resource=totality:rage classification=EXACT_MATCH generic=1/2 legacy=1/2 lastObservedTick=0
```

Final first-session count: Rage persistent entries 1, Rage recoveries 1, duplicate entries 0, false
recoveries 0, other Resource parity logs 0.

Respawn restored the legacy mirror; the logging observer correlated that recovery with the original
connection-scoped persistent episode. This confirms recovery behavior across `LocalPlayer` replacement.

### 24.7 Disconnect and reconnect

Disconnect to the title screen was exercised. No fabricated recovery appeared during disconnect, and
no title-screen logging loop appeared. Reconnect created a fresh connection-scoped diagnostic state —
no stale episode leaked into the new connection, and initial readiness plus normal rebuilding did not
create unrelated logs.

A second real Rage mismatch episode was then exercised in the new connection:

```
[22:39:31] [Render thread/DEBUG] (totality) Resource parity entered persistent mismatch: resource=totality:rage generic=1/2 legacy=0/0 firstMismatchTick=0 lastObservedTick=2
```

```
[22:39:51] [Render thread/DEBUG] (totality) Resource parity recovered from persistent mismatch: resource=totality:rage classification=EXACT_MATCH generic=1/2 legacy=1/2 lastObservedTick=1
```

The new connection correctly began a new episode: exactly one entry and one recovery appeared, no
state leaked from the previous connection, and disconnect after recovery produced no fabricated log.
The first session's episode (§24.3–24.6) and this second session's episode are two independently
valid connection-scoped episodes, not combined into one count.

### 24.8 Dedicated server

Fabric environment `SERVER`, Minecraft 26.2. The server reached `Done (0.365s)! For help, type
"help"`. No `ClassNotFoundException`, no `NoClassDefFoundError`, and no attempted client-parity
observer or diagnostics classloading failure — no failure involving the Phase 3B-2C client-only
logging boundary. Dedicated-server classloading validation passed; the client-only observer remained
correctly isolated from server startup.

Unrelated observed verification failures at this time: `ProvisionerEntityBackedSmokeTest` (3/4 checks
failed), `OffhandAttackVerification` (3/5 checks failed). These remain separate, deferred issues — not
Phase 3B-2C logging or classloading failures, and not modified or fixed in this slice. They are not
claimed to be deterministic: other client runs showed these self-tests can also pass.

### 24.9 Other observed unrelated warnings

`Received passengers for unknown entity` appeared once during the client test and remains the known
unrelated rest/passenger issue. Mojang development authentication/profile-key HTTP 401 messages
appeared and are unrelated to parity. Untranslated item-tag warnings and known missing development
assets remained unrelated. Normal Gradle/Loom deprecation noise remained. None of these are Phase
3B-2C regressions.

### 24.10 Not manually exercised

**FormulaResolver Mana path**: not manually exercised. The previously tested Grimoire spell did not
invoke `FormulaResolver.tryCast` (§18); gameplay reachability remains unestablished; the source-level
synchronization risk remains unfixed.

**Rage maximum-change path**: not manually exercised — no safe existing live route was used in this
pass. The known legacy maximum-sync risk (§7 part A background, `PlayerChargesComponent.applySyncPacket`)
remains unfixed. No command or gameplay mutation was added to exercise either path.

### 24.11 Manual validation conclusion

**Phase 3B-2C manual validation is complete.**

Passed: DEBUG logger activation; normal-state silence; one persistent-entry transition; no
steady-state spam; no duplicate entry across dimension reconstruction; no false recovery during
`GENERIC_NOT_READY`/transitional rebuilding; one correlated recovery after death/respawn; no repeated
recovery; connection-scoped clearing; reconnect isolation; a fresh episode in a fresh connection; no
parity-created presentation or gameplay effect; dedicated-server classloading boundary.

Detected but intentionally unfixed: legacy Rage becomes 0/0 across the tested dimension transfer while
generic Rage remains correct at 1/2 (§17, confirmed again live in this pass).

Not manually exercised: `FormulaResolver` Mana discrepancy; Rage maximum-change discrepancy (§24.10).

### 24.12 Stop-point status (manual validation)

- Manual validation is complete and recorded above from Stefan's actual observations only.
- No legacy Resource bug was fixed during or as a result of this validation pass.
- The local `build.gradle` Log4j addition and `log4j-dev.xml` are validation support only and are not
  part of the Phase 3B-2C commit.
- Phase 3B-3 was not started. Phase 3C was not started.
