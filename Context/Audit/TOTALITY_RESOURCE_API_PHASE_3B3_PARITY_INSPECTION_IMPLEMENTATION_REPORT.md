# TOTALITY — Generic Player Resource API — Phase 3B-3 Implementation Report
## On-Demand Client Parity Inspection Command and Phase 3C Readiness

Scope actually implemented: a development-only Fabric **client** command, `/totalitydebug resource
parity`, that reads the already-trusted Phase 3B-1 client Resource façade (Health/Food/Breath) and
the already-existing Phase 3B-2B/3B-2C read-only shadow-parity observation snapshot
(Mana/Stamina/Standard Spell Slots/Rage) and renders a bounded, deterministic report to client chat
feedback: **exactly seven Resource lines, one fixed header, eight chat lines total** — never an
ambiguous "seven-line report" (corrected wording, §27.1; the header is a distinct, always-present
eighth line, not one of the seven). No Resource authority, value, formula, packet, sync-eligibility,
trust-calculation, comparison/grace semantics, or persistent-mismatch logging-episode behavior was
touched. No Phase 3C consumer migration was performed. No ancestry, Provisioner, Rest, or combat bug
was fixed. Nothing was staged, committed, or pushed.

**The command path changed during this session's manual validation — see §28.** The command was
originally registered as `/totality resource parity`, sharing the existing server-side `/totality ...`
command tree's own root literal. Manual validation (performed by the user, not by this automated
session) confirmed this was a genuine runtime defect: registering anything under the shared `totality`
literal client-side caused Fabric's client command dispatch to intercept and break every other server-
side `/totality` branch. The command is now registered as **`/totalitydebug resource parity`** — a
root no server command shares. Every command-path reference in this report reflects the corrected
path; §28 retains the original path and the confirmed failure as historical record.

**This report includes three source-correction passes (§26, §27, §28), one manual-validation-completion
pass (§29), and one documentation-and-bundle-metadata closure pass (§32).** The first pass (§26) fixed
three architectural defects (the report layer was not actually Minecraft/Fabric-independent; native
Resources were assigned a fabricated parity classification; a single Resource's access failure could
abort the whole report) plus a bounded-header gap and a command-tree wording overreach. The second
pass (§27) fixed two further defects the first pass's own fixes did not fully close: (1) bounding the
header and each individual line did not bound the *complete report*, which still accepted any number
of lines at all, including zero; and (2) the first pass's own access-failure containment used a single
`catch` spanning both the accessor call and the separate step of converting its result, which made the
first pass's own claim ("`ACCESS_ERROR` means the accessor threw") false whenever a conversion failure
actually occurred. The third pass (§28) is the first pass in this phase driven by **actual manual
validation** (performed by the user) rather than static review alone: it records the manual findings
obtained so far and fixes the shared-root command collision those findings confirmed. §29 records the
completed manual-validation results (all ten post-correction checklist items) and the resulting Phase
3C readiness-matrix update. §32 (this session) makes **no source-code change at all** — final source
review confirmed all 12 production/test files byte-identical to the previously-approved bundle — and
instead corrects this report's own documentation: distinct, non-inferred evidence for checklist items
N.4/N.5 (`/totality wallet`, the ancestry command, the class command, and autocomplete are each
recorded as separately-confirmed evidence, never one inferred from another, and the class command is
never described as "wallet-equivalent"); the readiness count (**three of six** consumers READY, three
of six BLOCKED — never "four of six"); internally consistent Rage reasoning (the dimension-transfer
bug, §17, is documented migration *motivation*, while the Rage-maximum synchronization gap, §19, is
the actual remaining blocker); and the final review bundle's file-status metadata (`TotalityClient
.java` is a tracked, modified file — not "untracked"). All five passes were found/completed/corrected
before any commit occurred. §26/§27/§28/§29/§32 each record what was wrong (or what evidence was newly
confirmed) and the exact fix or correction; the numbered sections above them (§3-§12, §16-§25) describe
the corrected, current state and are marked accordingly where their content changed.

---

## 1. Starting branch, HEAD, and checkpoint verification

- Branch: `feature/general-resource-api`.
- Starting `HEAD`: `59916f17a148d8394c0a545c7ad9c8220027b597`, subject "Add bounded client parity
  diagnostics" — matches the expected checkpoint exactly.
- Upstream: `origin/feature/general-resource-api`; `git rev-list --left-right --count @{u}...HEAD` →
  `0 0` — fully in sync, nothing to push or pull.
- `git status --short` at session start showed only the known-unrelated entries the task
  anticipated: modified generated datagen JSON under `src/main/generated/data/**`, modified
  `build.gradle` (manual Phase 3B-2C DEBUG validation support), untracked `log4j-dev.xml`, every
  prior `Context/Audit/Review Bundles/*.zip`, `Context/Trading Test/trade_screen2-5.png`, `logs/`,
  and `src/main/generated/.cache/`. No `.java` file appeared modified or untracked at session start.
- `git diff --cached --stat` was empty — nothing staged.
- `git worktree list` showed only the primary worktree — no stray temporary worktree.
- No Phase 3B-3 or Phase 3C implementation existed anywhere in `src` at session start (searched for
  `*3B3*`, `*ParityInspect*`, `*ParityReport*` — no matches outside `.git/objects`).
- Automated baseline confirmed fresh (`./gradlew test --rerun`), summed across every
  `build/test-results/test/*.xml`: **783 tests, 0 failed, 0 errors, 0 skipped** — matching the
  expected checkpoint exactly.
- `/Inspiration Mods` was excluded from every search performed during this session.

**Conclusion: checkpoint fully satisfied. Implementation proceeded as authorized.**

---

## 2. Canonical inputs read

- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B_CLIENT_VIEW_AND_PARITY_READINESS.md` (read in full,
  two pages).
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B2C_BOUNDED_PARITY_LOGGING_IMPLEMENTATION_REPORT.md`
  (read in full, including both external-review correction passes, §22/§23) — this is the most
  recent implementation report and was treated as authoritative for the actual committed shape of
  the parity/logging system, per the task's instruction that committed code outranks older
  readiness-doc recommendations where they differ.
- The committed Phase 3A/3B production code itself, read directly rather than inferred from any
  report: `ClientResourceQueryResult`, `ClientResourceService`, `ClientResourceSource`,
  `ClientResourceTrust`, `ClientResourceUnavailableReason`, `NativeClientResourceReader`,
  `ClientResourceParityObservation`, `ClientResourceParitySummary`,
  `ClientResourceParityClassification`, `ClientResourceParitySummaryText`,
  `ClientResourceParityObservations`, `ClientResourceParityCoordinator`,
  `ClientResourceParityLogObserver`, `ClientGenericParitySummaryMapper`,
  `StandardSpellSlotsResourceAdapter` (to confirm spell-slot remaining-vs-used semantics directly
  from the adapter's own `resolve()` method), `PlayerResourceIds`, `TotalityClient.java`,
  `TotalityCommands.java` (existing server-command precedent), and `VerificationReporter.java` (the
  existing dev-environment gating precedent, `FabricLoader.getInstance().isDevelopmentEnvironment()`).
- The `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B2A_*`/`3B2B_*` reports were not re-read in full
  this session (their content is superseded/summarized inside the 3B-2C report's own citations and
  the directly-read production code); nothing in this report relies on a claim from either that
  wasn't independently confirmed against the live source.

---

## 3. Exact files changed — THIRD CORRECTION (see §27, §28)

**Created (production, pure — `zcylas.totality.api.rpg.resources.client.parity`, zero Minecraft/
Fabric/command/networking/logging/legacy-manager import — corrected, §26.1):**
- `ClientResourceParityReportLine.java` — one bounded, immutable `(String resourceId, String text)`
  line (corrected: `resourceId` is a plain `String`, not `Identifier` — §26.1), with a hard
  `MAX_TEXT_LENGTH = 400` truncation invariant enforced in its own compact constructor. **Unchanged by
  the second correction pass.**
- `ClientResourceParityReport.java` — the complete bounded report: a fixed `HEADER` constant (no
  longer a constructor parameter — §26.4) plus an immutable list of lines; `chatLines()` returns the
  plain `String`s (header + each line's text) the command boundary sends to chat. **Second correction
  (§27.1):** gained `RESOURCE_LINE_COUNT = 7`/`CHAT_LINE_COUNT = 8` constants; the compact constructor
  now rejects any `lines` size other than exactly 7.
- `ClientResourceParityReportAssembler.java` — the pure report assembler. Takes a single already-
  ordered `List<ClientResourceParityReportInput>` (corrected: no longer two `Map<Identifier, ...>`
  parameters — §26.1) and produces a `ClientResourceParityReport`; never queries Minecraft, never
  re-derives a parity comparison, never mutates its input list, and holds no canonical-ordering
  knowledge of its own. **Second correction (§27.1/§27.2):** `assemble(...)` now validates the input
  list's size against `ClientResourceParityReport.RESOURCE_LINE_COUNT` before allocating any output
  list; the real per-input render step (`renderLine`) is now package-private (not private) so tests
  can exercise it directly; two new `switch` arms render `Native.Status.ConversionError`/
  `ShadowParity.Status.ConversionError` as `RESULT_CONVERSION_ERROR(...)`/
  `OBSERVATION_CONVERSION_ERROR(...)`, textually distinct from `ACCESS_ERROR(...)`/
  `OBSERVATION_ACCESS_ERROR(...)`.
- `ClientResourceParityReportInput.java` (added by the first correction pass, §26.1) — the pure,
  Minecraft-independent conversion-boundary vocabulary: sealed `Native`/`ShadowParity` records, each
  with its own sealed `Status`. **Second correction (§27.2):** gained a new `ConversionError` variant
  on each `Status` (`Native.Status.ConversionError`/`ShadowParity.Status.ConversionError`) — a
  distinct type from `AccessError`, never a second meaning overloaded onto it. Reuses already-pure
  existing types where appropriate (`ClientResourceTrust`, `ClientResourceUnavailableReason`,
  `ClientResourceParityClassification`, `ClientResourceParitySummary`) rather than re-encoding them.

**Created (production, client-only — new file in the existing `zcylas.totality.client.resource.parity`
package):**
- `ClientResourceParityInspectionCommand.java` (`@Environment(EnvType.CLIENT)`) — registers
  `/totalitydebug resource parity` (**third correction, §28.1: moved off the originally-shared
  `totality` root** — see §4) via Fabric's `ClientCommandRegistrationCallback`/`ClientCommands`
  (`fabric-command-api-v2`), gated on `FabricLoader.getInstance().isDevelopmentEnvironment()`. Owns
  the canonical seven-Resource ordering and every conversion from live `Identifier`-carrying results
  to the pure input vocabulary (§26.1). **Second correction (§27.2):** `gatherNative`/`gatherShadow`
  each now use **two separate** `try`/`catch (RuntimeException)` blocks instead of one — the first
  wrapping only the accessor call (`nativeQuery.apply(id)`/`shadowLookup.apply(id)`), the second
  wrapping only the subsequent, separate conversion step (`toNativeInput`/`toShadowInput`) — so an
  accessor failure and a conversion failure are structurally distinct events, never conflated by a
  shared catch scope. Exposes a package-private zero-argument `gatherReport()` production entry point
  plus a package-private two-argument `gatherReport(Function, Function)` seam used by tests to inject
  a deliberately-throwing (or deliberately-null-returning) accessor.

**Modified (one file, additive only, comment-only change in the third correction pass):**
- `src/main/java/zcylas/totality/TotalityClient.java` — one new line in `onInitializeClient()`
  calling `ClientResourceParityInspectionCommand.registerIfDevelopmentEnvironment()`. No existing
  functional line was changed or removed; the third correction pass updated only the explanatory
  comment above that line to reflect the new command path and the root-collision finding (§28.1).

**Created/modified (tests):**
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityReportLineTest.java`
  (3 tests, unchanged by the second correction pass)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityReportTest.java`
  (13 tests — **second correction (§27.1): +9 net** — the empty-report-is-valid test was removed, one
  test was renamed/extended, and 9 new tests cover the exact-seven-line invariant, rejection of every
  invalid size, an adversarial 50,000-line rejection, and the provable total-character bound)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityReportInputTest.java`
  (8 tests, unchanged by the second correction pass)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityReportPureBoundaryTest.java`
  (2 tests, unchanged by the second correction pass)
- `src/test/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityReportAssemblerTest.java`
  (38 tests — **second correction (§27.1/§27.2): +6** — `render(...)` now calls the package-private
  `renderLine` directly instead of wrapping a single input in `assemble(List.of(input))` [which would
  now always throw, since `assemble` requires exactly seven]; ordering/mutation/determinism tests now
  build full seven-input lists; new tests cover assembler-level rejection of 6/8/100,000-input lists
  and `ConversionError` rendering)
- `src/test/java/zcylas/totality/client/resource/parity/ClientResourceParityInspectionCommandTest.java`
  (32 tests — second correction (§27.2): +5 — new tests cover a native/shadow accessor returning
  `null` producing the distinct conversion-error line, access-vs-conversion label non-confusion,
  repeated-conversion-failure determinism, and conversion-failure non-mutation of parity/logging state.
  **Third correction (§28.4): +4** — `ROOT_LITERAL` equals `"totalitydebug"`; the registration method
  registers `ClientCommands.literal(ROOT_LITERAL)`; the production command never registers the shared
  `ClientCommands.literal("totality")`; the full command path equals exactly `"totalitydebug resource
  parity"`)

**Not touched:** every existing Phase 3A/3B-1/3B-2A/3B-2B/3B-2C production class and test (in
particular `ClientResourceParityTracker`, `ClientResourceParityCoordinator`,
`ClientResourceParityLogObserver`, `ClientResourceParityLogTransitionTracker`,
`ClientResourceParitySummaryText`, `ClientResourceService`, every reader, every legacy manager,
every packet/payload class, `ClientResourceSyncManager`); `TotalityCommands.java` (the existing
server-side `/totality` command tree); every HUD/screen/radial/tooltip/movement/class/spell file;
`/Inspiration Mods`; and every "known unrelated" working-tree entry (`build.gradle`, `log4j-dev.xml`,
generated datagen JSON, `Context/Trading Test/*.png`, `logs/`, `.cache/`, every prior review bundle
ZIP, every prior audit document).

---

## 4. Command registration and development gating — THIRD CORRECTION (see §28.1)

Exact command path: **`/totalitydebug resource parity`** — corrected by manual validation (§28); this
was `/totality resource parity` through the first and second correction passes.

Registered as a genuine Fabric **client**-executed command via `fabric-command-api-v2`'s
`ClientCommandRegistrationCallback.EVENT` + `ClientCommands.literal(...)` (confirmed present in this
project's resolved `fabric-command-api-v2-3.1.0` dependency by direct `javap` inspection of the jar —
this Minecraft/Fabric version's client-command API surface is `ClientCommands`, not the older
`ClientCommandManager` name). It is now registered under the entirely distinct root literal
`totalitydebug` (`ClientResourceParityInspectionCommand.ROOT_LITERAL`) — no server command shares this
root, by construction.

**Corrected — confirmed defect, not merely an unproven claim (§28.1)**: through both prior correction
passes, this section stated the command was registered "under the same `totality` literal the existing
server-side `/totality ...` tree also uses," narrowed in the second pass to "whether the two trees
actually coexist correctly at runtime... has not been empirically verified." **Manual validation
(performed by the user) has now empirically verified this, and the answer is that they did not
coexist**: registering any child under the shared `totality` literal client-side caused Fabric's
client-command dispatch (`ClientCommandInternals`) to intercept every other server-side `/totality`
branch — confirmed live for `totality showancestry`, `totality ancestry`, and `totality wallet`, each
failing with a client-side Brigadier syntax exception (`Incorrect argument for command at position 9:
totality <--[HERE]`) instead of ever reaching the server. See §28.1 for the full manual-validation
record and the root-cause explanation. The command now registers under `totalitydebug` instead,
specifically to eliminate this class of collision structurally — a shared root is never retained even
as an alias, per the correction's own explicit instruction, because any client-side registration under
`totality` at all was the defect, not merely this particular subtree's shape.

**Still not yet re-verified**: whether the *corrected* `totalitydebug` root and the server's own
`totality` tree coexist correctly at runtime — i.e. that every previously-broken server-side `/totality`
branch now executes normally again, and that `/totalitydebug resource parity` itself still works after
the rename. This is the post-correction manual checklist (§16.N, added by §28) — not yet performed as
of this report.

**Development gating (unchanged by this or either prior correction pass)**: `ClientResourceParityInspectionCommand
.registerIfDevelopmentEnvironment()` checks `FabricLoader.getInstance().isDevelopmentEnvironment()`
and returns immediately (registering no event listener at all) when false. This reuses the exact
existing dev-gating convention already established by `VerificationReporter.isDevEnvironment()` —
rather than inventing a second convention. In a normal (non-development) launch, the command literally
does not exist in the client's Brigadier dispatcher; it is not merely hidden, permission-gated, or
config-flagged. No `.requires(...)` permission/operator predicate was added (confirmed by source-scan
test `noPermissionOrOperatorCheckSubstitutesForDevelopmentGating`). No config option, no entitlement,
no debug item, no keybind, and no permanent HUD panel were added.

---

## 5. Architecture — CORRECTED (see §26.1, §27.1, §27.2)

Four small pure production types plus one client-only boundary, across four responsibilities:

1. **Pure conversion-boundary input vocabulary** (one file, new — §26.1): `ClientResourceParityReportInput`.
   A sealed hierarchy of plain-`String`-keyed, Minecraft-independent records — `Native` (Health/Food/
   Breath, with a nested `Status` of `Available`/`Unavailable`/`AccessError`) and `ShadowParity`
   (Mana/Stamina/Standard Spell Slots/Rage, with a nested `Status` of `Observed`/`NoObservationYet`/
   `AccessError`). Reuses already-pure existing value types directly — `ClientResourceTrust`,
   `ClientResourceUnavailableReason`, `ClientResourceParityClassification`, `ClientResourceParitySummary`
   — rather than re-encoding any of them into a second vocabulary.
2. **Pure line/report model** (two files): `ClientResourceParityReportLine` (one bounded, length-
   capped immutable `(String, String)` line — corrected, no `Identifier` — §26.1) and
   `ClientResourceParityReport` (a fixed `HEADER` constant, not a constructor parameter — §26.4 — plus
   an immutable line list, with a `chatLines()` convenience view). **Second correction (§27.1):** the
   line list is no longer accepted at any size — the compact constructor now requires exactly
   `RESOURCE_LINE_COUNT = 7` entries, rejecting 0, 1, 6, 8, or any other count (including an
   adversarially large one) with an `IllegalArgumentException`, so the report's total possible output
   size is a static, provable fact rather than a function of caller input.
3. **Pure bounded report assembler** (one file): `ClientResourceParityReportAssembler`. Takes a single
   already-ordered `List<ClientResourceParityReportInput>` (corrected — §26.1: no longer two
   `Map<Identifier, ...>` parameters, and the assembler no longer holds `NATIVE_RESOURCE_IDS`/
   `SHADOW_PARITY_RESOURCE_IDS` constants of its own — ordering is entirely the command boundary's
   responsibility) and produces a `ClientResourceParityReport`, preserving the caller's order exactly.
   **Second correction (§27.1):** `assemble(...)` now validates `inputs.size() ==
   ClientResourceParityReport.RESOURCE_LINE_COUNT` *before* allocating the output `ArrayList` — an
   adversarial list of 100,000 inputs is rejected at an O(1) size check, never copied or iterated.
   **Second correction (§27.2):** the real per-input render step, `renderLine`, is now package-private
   (was `private`) specifically so line-rendering tests can call it directly without first having to
   construct a full seven-entry list — the exact same method `assemble(...)` itself calls, not a
   parallel test-only copy. Native lines render the fixed presentation-only token
   `comparison=native-only` (first correction, §26.2: never a `ClientResourceParityClassification`
   constant); shadow-parity lines echo the classification already supplied on the input's `Observed`
   status verbatim — the assembler never constructs a `ClientResourceParityClassification` value
   itself. Every per-resource rendering call is wrapped in a narrow `catch (RuntimeException)` that
   falls back to a bounded `"<id> | FORMAT_ERROR"` line — this is the *formatter*-failure guard,
   distinct from both the command boundary's *access*-failure containment and its *conversion*-
   failure containment (§10, §27.2).
4. **Client-only command boundary** (one file): `ClientResourceParityInspectionCommand`. The **only**
   code in this slice that ever touches `Identifier`, `PlayerResourceIds`, `ClientResourceService`,
   `ClientResourceParityObservations`, `ClientResourceQueryResult`, or `ClientResourceParityObservation`
   (first correction, §26.1). Its package-private `gatherReport()` wires the real accessors into the
   testable `gatherReport(Function, Function)` seam, which converts each Resource's live result into
   the pure input vocabulary before ever calling the pure assembler. **Second correction (§27.2):**
   `gatherNative`/`gatherShadow` each now perform this in two separate `try`/`catch (RuntimeException)`
   steps — accessor call, then conversion — rather than one combined `try` spanning both, so an
   accessor failure (`AccessError`) and a conversion failure (`ConversionError`) are never conflated.
   `execute(...)` (the Brigadier command handler) calls `gatherReport()` once and sends each of
   `report.chatLines()` as a separate `FabricClientCommandSource.sendFeedback(Component.literal(...))`
   call.

Confirmed by this package's own import-scan regression test
(`ClientResourceParityReportPureBoundaryTest`): none of the four pure files (responsibilities 1-3)
imports anything beyond `java.*` and this same pure package family (plus `ClientResourceTrust`/
`ClientResourceUnavailableReason`, themselves zero-import pure types) — no `net.minecraft.*`, no
`net.fabricmc.*`, no `FabricLoader`, no command API, no `Minecraft`/`LocalPlayer`, no networking type,
no legacy manager, no logger. The trusted façade was never made to depend on this command (no new
public method was added to `ClientResourceService`, `ClientResourceParityObservations`, or
`ClientResourceParityCoordinator`); only the smallest genuinely-needed read-only accessor already
existed and was reused as-is.

---

## 6. Report model and bounded-output policy — CORRECTED (see §26.4, §27.1)

- **Exact report-size invariant (second correction, §27.1) — the complete report structurally
  requires exactly seven Resource lines, never fewer, never more**: `ClientResourceParityReport`'s
  compact constructor rejects any `lines` whose size is not exactly
  `RESOURCE_LINE_COUNT = 7` with an `IllegalArgumentException`, and `ClientResourceParityReportAssembler
  .assemble(...)` independently validates its own input list against that same constant *before*
  allocating any output list. A bounded header and bounded individual lines are necessary but were not
  themselves sufficient — the first correction pass left the line-list size completely unconstrained,
  including zero (the first correction's own test suite explicitly asserted an empty report was
  valid); this second pass closes that gap structurally, not by convention.
- **Line count**: exactly 7 resource lines + 1 fixed header = **`CHAT_LINE_COUNT = 8` chat lines per
  invocation**, always — never a function of caller input, since the size is now a rejected-or-exactly-
  seven binary rather than an unbounded range
  (`ClientResourceParityReportTest.chatLinesAlwaysBeginsWithTheFixedHeaderRegardlessOfLineContentAndReturnsExactlyEight`,
  `ClientResourceParityReportAssemblerTest.everyRenderedLineStaysWithinTheBoundAndHeaderStaysFixed`).
- **Header is structurally bounded, not merely conventionally short (first correction, §26.4)**:
  `ClientResourceParityReport.HEADER` is a fixed `public static final String` constant
  (`"[Resource Parity]"`) and is **not a record component at all** — the record's sole component is
  `lines`. There is no constructor parameter through which a caller could supply an arbitrary or
  unbounded header string; `ClientResourceParityReportTest.headerIsNotARecordComponentAtAll` proves
  this structurally (via `getRecordComponents()`), not merely by asserting a length limit a future
  edit could quietly relax.
- **Line length**: every `ClientResourceParityReportLine.text()` is hard-capped at
  `MAX_TEXT_LENGTH = 400` characters by the record's own compact constructor, which truncates and
  appends a fixed `...(truncated)` marker rather than trusting every call site to have already
  bounded its own input. This is a second, independent bound layered on top of
  `ClientResourceParitySummaryText.MAX_PARTITIONS_IN_TEXT`'s own existing 16-partition cap — proven
  jointly by `largeAdversarialPartitionInputStaysWithinTheLineBound` (a synthetic 5000-partition
  spell-slot summary still produces a line under 400 characters).
- **Total maximum rendered size is now a provable static fact (second correction, §27.1)**: with the
  line count fixed at exactly 7 and each line capped at `MAX_TEXT_LENGTH = 400`, the report's maximum
  possible total rendered character count is exactly `HEADER.length() + 7 × 400` — a fixed upper bound
  derivable without running anything, proven by
  `ClientResourceParityReportTest.totalRenderedCharacterBoundIsProvableFromFixedHeaderAndSevenCappedLines`.
- **Resource id and access/conversion-error token are also bounded at the input layer (first
  correction, §26.1/§26.3; extended to `ConversionError` by the second correction, §27.2)**:
  `ClientResourceParityReportInput.MAX_RESOURCE_ID_LENGTH` (128) and `MAX_TOKEN_LENGTH` (100) bound the
  id and any `AccessError`/`ConversionError.exceptionSimpleName` before the line-level 400-character
  bound is ever reached — proven by `ClientResourceParityReportInputTest`.
- **No hover/click events, no `toString()` dump, no UUID/coordinate/world-path/inventory data**: the
  renderer only ever emits `String`/enum-name/primitive-number tokens joined with fixed literal
  separators; no `Component` builder anywhere in the new code attaches a hover or click event, and
  `Component.literal(...)` is the only `Component` factory call used, at the command boundary only.
- **Determinism**: repeated `assemble(...)` calls with identical input produce identical
  `chatLines()`, and the seven-id iteration order is exactly whatever order the caller's
  `List<ClientResourceParityReportInput>` supplied — the assembler holds no ordering knowledge of its
  own (first correction, §26.1).
- **No input mutation**: `assembleDoesNotMutateTheSuppliedInputList` passes a plain mutable
  `ArrayList` (now with exactly seven entries — second correction, §27.1) and confirms both its size
  and its first element are unchanged after assembly. `ClientResourceParityReportTest
  .linesListIsDefensivelyCopiedAndImmutable` proves the same for the report's own `lines` list.

---

## 7. All-seven-Resource handling and native-vs-shadow-parity distinction — CORRECTED (see §26.1/§26.2)

Canonical order (matches the task's required ordering exactly): Health, Food, Breath, Mana, Stamina,
Standard Spell Slots, Rage — `PlayerResourceIds.HEALTH/FOOD/BREATH` then
`MANA/STAMINA/SPELL_SLOTS/RAGE`, hardcoded as two ordered `List.of(...)` constants **on the client
command boundary** (corrected, §26.1 — moved off the pure assembler, which held no `Identifier`/
`PlayerResourceIds` knowledge before or after this correction, but which the review found was still
being handed `Identifier`-carrying inputs directly rather than a converted pure vocabulary).

**Native lines (Health/Food/Breath)** render the trusted Phase 3B-1 façade's own result, already
converted to the pure `ClientResourceParityReportInput.Native` shape by the command boundary: `<id> |
native | <TRUSTED|PENDING_RESYNC|UNAVAILABLE(reason)|ACCESS_ERROR(exceptionSimpleName)> |
<current>/<maximum>[+overflowN] | comparison=native-only`. **Corrected (§26.2)**: the suffix is now
the fixed presentation-only token `comparison=native-only`
(`ClientResourceParityReportAssembler.NATIVE_COMPARISON_LABEL`) — plain text, never a
`ClientResourceParityClassification` enum constant. The previous implementation reused
`ClientResourceParityClassification.NOT_APPLICABLE` for this suffix; the external review correctly
identified that as a fabricated classification, since `ClientResourceParityTracker` never observes
Health/Food/Breath at all and no `ClientResourceParityObservation` exists for them. No native-
rendering code path imports or references `ClientResourceParityClassification` any longer — confirmed
by `nativeLineNeverContainsAnyParityClassificationName`/`nativeLinesFromTheRealCommandNeverContainAParityClassificationName`,
which check every native line against every one of the seven classification names
(`EXACT_MATCH`/`GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH`/`MODEL_MISMATCH`/
`EXPECTED_SEMANTIC_DIFFERENCE`/`NOT_APPLICABLE`). No native line ever computes or displays a
mismatch/match verdict of any kind.

**Shadow-parity lines (Mana/Stamina/Standard Spell Slots/Rage)** render the existing shadow-parity
observation — converted to `ClientResourceParityReportInput.ShadowParity.Status.Observed` by the
command boundary, carrying the tracker's own already-decided `classification` field verbatim — never
recomputed: `<id> | <classification> | generic=<summary> | legacy=<summary>[ | firstMismatchTick=N] |
lastObservedTick=N`. Both summary strings are produced by `ClientResourceParitySummaryText.format(...)`
— the exact same bounded formatter Phase 3B-2C's logging already uses — never a second/duplicate
formatter. `firstMismatchTick` is included only when non-null; `lastObservedTick` is always included.
When no observation has ever been recorded for a Resource, the line renders the bounded placeholder
`<id> | NO_OBSERVATION_YET` — a report-only label, never a fabricated `GENERIC_NOT_READY`
classification the tracker itself never actually assigned.

**The assembler cannot independently classify a shadow Resource (§26.2's explicit requirement)**: no
code path inside `ClientResourceParityReportAssembler` ever constructs a
`ClientResourceParityClassification` value — every occurrence in its source is either a `switch` over
an already-supplied enum value or a string-interpolation of one already supplied by the caller,
confirmed by direct inspection (`grep`-verified: zero occurrences of
`ClientResourceParityClassification.<CONSTANT>` anywhere in the assembler's source).

No second parity algorithm exists anywhere in the new code: the assembler never compares a generic
value against a legacy value itself; it only ever renders whatever classification/summary the
existing, unmodified `ClientResourceParityTracker`/`ClientResourceParityObservation` already decided,
now passed through the pure conversion boundary rather than read directly.

---

## 8. Spell-slot remaining semantics

Confirmed directly from `StandardSpellSlotsResourceAdapter.resolve(...)` (line 143):
`long current = (long) maximum - used;` — the adapter's own `current` field is already "remaining
slots," not "used slots," at the wire/façade/summary level. Unchanged in substance by this correction
pass — only the carrier type changed: the command boundary now sets
`ClientResourceParityReportInput.ShadowParity.remainingLabel = true` only for
`PlayerResourceIds.SPELL_SLOTS`, and the assembler prefixes both summary strings with the literal
label `"remaining "` (e.g. `generic=remaining 1:2/4,2:0/1`) whenever that flag is set — the word "used"
never appears anywhere in the rendered output for this Resource — proven by
`ClientResourceParityInspectionCommandTest.gatherReportLabelsSpellSlotsAsRemainingNeverUsed` (an
end-to-end test through the actual command's `gatherReport()`) and by
`ClientResourceParityReportAssemblerTest.spellSlotExactMatchRendersRemainingLabelForBothSides`. No
other Resource needed this treatment.

---

## 9. Rage absence-vs-numeric-zero distinction

Unchanged in substance by this correction pass. The assembler never converts an `Unavailable` legacy/
generic summary into a numeric `0/0` — it always calls `ClientResourceParitySummaryText.format(...)`,
which already renders `Unavailable` as `"unavailable:<reason>"` and a genuine zero pool as `"0/0"` —
two textually distinct, never-confusable outputs. Proven directly by
`rageUnavailableLegacyDiffersFromGenuineZeroZeroLegacy`. No new classification or inference was added
for this distinction — it was already fully solved at the wire/tracker level per the Phase 3B
readiness audit §8.3, and this command only makes the existing distinction visible on demand.

---

## 10. Failure behavior — CORRECTED (see §26.3, §27.2)

| Scenario | Behavior | Proof |
|---|---|---|
| No local player (native) | `NativeClientResourceReader` returns `Unavailable(NO_LOCAL_PLAYER)`; renders as `UNAVAILABLE(NO_LOCAL_PLAYER)` | `noLocalPlayerRendersBoundedUnavailableNativeLines` |
| No client-side reader registered (this session's plain-JUnit context, or a genuinely unconfigured id) | `ClientResourceService.query` returns `Unavailable(CLIENT_SOURCE_NOT_CONFIGURED)` without touching `Minecraft.getInstance()` at all | `gatherReportNativeResultsAreBoundedWhenNoReaderIsRegistered` |
| Generic snapshot not synchronized yet | Shadow observation's `genericSummary` is `Unavailable(NOT_SYNCHRONIZED_YET)`, classification `GENERIC_NOT_READY`; both sides still rendered | `genericNotReadyRendersUnavailableGenericSideWithLegacyStillShown` |
| Pending resync | Native `ClientResourceTrust.PENDING_RESYNC` renders a distinct label from `TRUSTED` | `pendingResyncNativeTrustRendersDistinctLabelFromTrusted` |
| Malformed/incompatible source state | `Unavailable(MALFORMED_SOURCE_STATE)` renders bounded, no exception | `malformedSourceStateRendersBoundedUnavailableLine` |
| Missing parity observation | Bounded `NO_OBSERVATION_YET` placeholder, never a fabricated classification | `missingObservationRendersBoundedPlaceholderNotAFabricatedClassification` |
| Unavailable Resource (Rage absent) | `Unavailable(NOT_AVAILABLE_TO_PLAYER)` rendered distinctly from `0/0` | §9 above |
| Empty spell-slot partitions | `ClientResourceParitySummary.Partitioned.of(List.of(), 1)` renders without exception | `emptySpellSlotPartitionsRenderWithoutException` |
| **Native Resource accessor failure (first correction, §26.3)** | Command boundary catches `RuntimeException` around only the native-query call itself (`nativeQuery.apply(id)`) for that one Resource, in its own dedicated `try`; converts to bounded `Native.Status.AccessError(exceptionSimpleName)`; renders `ACCESS_ERROR(<name>)`; the other six Resources are gathered normally | `oneThrowingNativeReaderDoesNotAbortTheReport` |
| **Shadow-parity observation accessor failure (first correction, §26.3)** | Command boundary catches `RuntimeException` around only the observation-lookup call itself (`shadowLookup.apply(id)`) for that one Resource, in its own dedicated `try`; converts to bounded `ShadowParity.Status.AccessError(exceptionSimpleName)`; renders the *distinct* label `OBSERVATION_ACCESS_ERROR(<name>)`; the other six Resources are gathered normally | `oneThrowingShadowLookupDoesNotAbortTheReport` |
| **Native result-conversion failure (new, §27.2)** | The accessor call itself succeeds (e.g. returns a result, or — as tested — returns `null`); converting that result into the pure input vocabulary (`toNativeInput`) then throws, in a *separate* `try`/`catch` from the accessor call; converts to bounded `Native.Status.ConversionError(exceptionSimpleName)`; renders the distinct label `RESULT_CONVERSION_ERROR(<name>)`, never `ACCESS_ERROR` | `nativeAccessorReturningNullResultProducesTheDistinctConversionErrorLine` |
| **Shadow-parity observation-conversion failure (new, §27.2)** | Same pattern for the shadow-parity side: the lookup call succeeds (or returns `null`); converting its result (`toShadowInput`) throws separately; renders `OBSERVATION_CONVERSION_ERROR(<name>)`, never `OBSERVATION_ACCESS_ERROR` | `shadowAccessorReturningNullLookupProducesTheDistinctConversionErrorLine` |
| **Adversarially long exception message** | Only the exception's simple class name (bounded to `MAX_TOKEN_LENGTH`) ever reaches the report — the message, if any, never does; true for both accessor and conversion failures | `extremelyLongExceptionMessageNeverEntersTheOutput` |
| **Diagnostic formatter failure (distinct from both accessor and conversion failure)** | Narrow `catch (RuntimeException)` inside the pure assembler's own `renderLine`, around rendering an already-gathered input; falls back to `"<id> \| FORMAT_ERROR"` | code-level guard in `ClientResourceParityReportAssembler.renderLine`; no test can force a genuine exception through the immutable, constructor-validated pure input records, so this remains a structural/code-level proof, not a forced-failure test — reported honestly as such |

**Three distinct failure boundaries, never conflated — corrected (§27.2)**: an *accessor* failure
means the command boundary's own call into `ClientResourceService.INSTANCE.query(id)` or
`ClientResourceParityObservations.latest(id)` itself threw — caught in its own `try`/`catch`, before
any result exists, producing `AccessError`. A *conversion* failure means that call **succeeded** (a
result was returned, however malformed — e.g. `null`), but the separate, subsequent step of converting
that result into the pure input vocabulary threw instead — caught in its own, later `try`/`catch`,
producing the distinct `ConversionError`. A *formatter* failure means the pure assembler's own
rendering of an already-successfully-gathered input threw; caught inside the assembler itself,
producing `FORMAT_ERROR`. **The first correction pass's report claimed the second and third of these
were already distinct, but its own source did not yet make the first two distinct from each other** —
`gatherNative`/`gatherShadow` used one `try` spanning both the accessor call and the conversion step,
so any conversion-step exception was mislabeled `AccessError` even though the accessor itself had not
failed. §27.2 corrects the source (two separate `try`/`catch` blocks per method) and this table
correspondingly.

No `Throwable` is caught anywhere in the new code — only the narrowly-scoped `RuntimeException` catch
described above, now at **five** distinct catch sites: the native accessor call, the native conversion
step, the shadow-parity accessor call, the shadow-parity conversion step (all four in
`ClientResourceParityInspectionCommand`), and the assembler's per-line rendering step (in
`ClientResourceParityReportAssembler`). No DEBUG/WARN/ERROR log line is emitted anywhere in the new
code — the command produces only chat feedback, and the Phase 3B-2C logging system is never invoked
(§13 below).

---

## 11. Test matrix — THIRD CORRECTION (see §28)

| File | Tests | Covers |
|---|---|---|
| `ClientResourceParityReportLineTest` | 3 | Line-length bound (unchanged by any correction pass) |
| `ClientResourceParityReportTest` | 13 | First correction (§26.4): the bounded-header invariant. Second correction (§27.1): the exact-seven-line invariant. Unchanged by the third correction pass. |
| `ClientResourceParityReportInputTest` | 8 | Unchanged by any correction pass |
| `ClientResourceParityReportPureBoundaryTest` | 2 | Unchanged by any correction pass |
| `ClientResourceParityReportAssemblerTest` | 38 | Required-coverage items 1-27 (first correction) plus the exact-size/`ConversionError` coverage (second correction). Unchanged by the third correction pass. |
| `ClientResourceParityInspectionCommandTest` | 32 | Required-coverage items 28-33 plus Blocker 3 (first correction) and the access-vs-conversion coverage (second correction). **Third correction (§28.1, net +4)**: `ROOT_LITERAL` equals `"totalitydebug"`; the registration method registers `ClientCommands.literal(ROOT_LITERAL)`; the production command never registers the shared `ClientCommands.literal("totality")`; the full command path is exactly `totalitydebug resource parity`. |
| **Total new (relative to the 783-test Phase 3B-2C baseline)** | **96** | 3 + 13 + 8 + 2 + 38 + 32 |

Item 34 ("existing persistent logging tests remain unchanged and passing") is proven by the full
automated run below: every existing Phase 3B-2A/3B-2B/3B-2C test file was left byte-for-byte
untouched, and all 875 pre-third-correction tests still pass, unchanged in expected behavior, inside
the 879-test total. No test was removed or weakened by the third correction pass — only 4 new tests
were added, all in `ClientResourceParityInspectionCommandTest`.

Two items from the required list remain covered structurally rather than by a forced-exception test,
reported explicitly rather than silently claimed: **diagnostic formatter failure** (§10 — the guard
exists and is code-reviewable, but the immutable, constructor-validated pure input types make a
genuine runtime exception impossible to force through a test without violating those types' own
invariants) and **static/source-only proof of development gating and server-independence** (explicitly
permitted by the task). The *accessor*-failure and *conversion*-failure containment (§26.3, §27.2), by
contrast, **are each** exercised by genuine forced-failure tests. The third correction's own command-
root fix (§28.1) is likewise proven by source-scan tests, per the task's own instruction that live
command-tree coexistence cannot be claimed proven by an automated test — that remains a manual check
(item N, §16.1).

---

## 12. Exact automated results — THIRD CORRECTION (see §28)

- **`compileJava`**: `BUILD SUCCESSFUL`. No compiler warnings.
- **`compileTestJava`**: `BUILD SUCCESSFUL`. No compiler warnings.
- **`test --rerun`** (fresh run, not up-to-date cache): `BUILD SUCCESSFUL`. Summed across every
  `build/test-results/test/*.xml`: **879 tests, 0 failed, 0 errors, 0 skipped.**
- **Net change**: 879 − 875 = **+4** (§11) — four new tests in `ClientResourceParityInspectionCommandTest`
  proving the command-root correction; no existing test was removed, weakened, or changed in expected
  behavior.
- **`runDatagen`**: `BUILD SUCCESSFUL`. `total files: 349, old count: 349, new count: 349, removed
  stale: 0, written: 0` — zero generated-file impact, matching the expected `349 → 349`.
- **`build`**: `BUILD SUCCESSFUL` (`compileJava`, `processResources`, `classes`,
  `processIncludeJars`, `jar`, `sourcesJar`, `assemble`, `compileTestJava`, `test`,
  `validateAccessWidener`, `check` all succeeded or were up-to-date). Only the pre-existing
  "[Incubating] Problems report" notice and the standing "Deprecated Gradle features... incompatible
  with Gradle 10" warning — both present before this session and unrelated to this change.
- **`git diff --check -- src/main/java src/test/java`**: exit code `0`, no output — no whitespace/
  line-ending error in any new or modified file.
- No compiler, Gradle, Loom, test, datagen, or build warning of any kind beyond the two pre-existing
  Gradle/Loom notices above was observed anywhere in this session.

---

## 13. Datagen results

See §12 — `349 → 349`, `written: 0`. No `.java` change in this phase touches any datagen provider,
recipe, loot table, or worldgen definition, so this result was expected rather than merely hoped for.

---

## 14. Build/diff-check results

See §12. `git diff --check` clean; `build` successful with no new warning.

---

## 15. Warnings

None beyond the two pre-existing Gradle/Loom deprecation notices described in §12, both unrelated to
any file this phase touched.

---

## 16. Manual-validation checklist — CHECKLIST ITEM N NOW COMPLETE (see §29); items A-M below unchanged historical record

Manual validation was **actually performed by the user**, in two rounds: first (§16.0) using the
original (now-superseded) `/totality resource parity` command path, before the shared-root defect was
discovered; second (§29.1), after the command-root correction, against the corrected
`/totalitydebug resource parity` path — this second round **completed every sub-item of checklist item
N** (§16.1), including the previously-blocked spell-slot remaining-value test. Items A-M below are
left as the historical record of what was known/performed/blocked at the time of the second correction
pass; §29 records the final, completed state. No fabricated values, logs, production-gating results,
or dedicated-server results are added anywhere beyond what was actually supplied, in either round.

### 16.0 Manual validation actually performed (old path, before the command-root correction)

Performed by the user, using the original `/totality resource parity` command — retained here as the
exact evidence obtained, per the task's explicit instruction not to fabricate anything beyond what was
actually supplied:

- The command registered and executed: `/fcc help` (Fabric's client-command-list command) listed
  `/totality resource parity`, and invoking it produced output rather than "unknown command."
- The report's shape matched the automated contract: one header line plus exactly seven Resource
  messages (§6's `CHAT_LINE_COUNT = 8` invariant).
- Health, Food, and Breath rendered with the `comparison=native-only` label (§7), confirming the
  native lines carry no parity classification in a live client, not just in unit tests.
- Mana, Stamina, Standard Spell Slots, and Rage initially reported `EXACT_MATCH` for all four shadow-
  parity Resources.
- Observed initial values: Mana `100/100` (generic and legacy), Stamina `110/110` (generic and
  legacy), Rage `1/2` (generic and legacy).
- Spending Resources kept the generic and legacy sides synchronized, re-invoking the command after
  each spend:
  - Mana `86/100` generic and legacy, `EXACT_MATCH`.
  - Stamina `92/110` generic and legacy, `EXACT_MATCH`.
  - Rage `0/2` generic and legacy, `EXACT_MATCH`.
- The user reports having worked through the remainder of the practical checklist exercises **except**
  spell-slot spending — that exercise was blocked because the player could not switch to the Wizard
  class (a caster class is required to have any non-zero standard-spell-slot maximum to spend from).
  This report does not claim which other specific items (B, C, E-G, I-L) were exercised beyond what is
  listed above, since no further specific values, logs, or observations for those items were supplied —
  recording more than what was actually captured would be fabrication.
- **Not performed and not claimed**: production/non-development absence verification, dedicated-server
  classloading verification, and the FormulaResolver/Rage-maximum live routes (items I/J) — no evidence
  for any of these was supplied.

**Command-root coexistence result: FAILED.** While exercising the checklist above, the user discovered
that ordinary server-side `/totality ...` commands no longer worked from the client — see §28.1 for
the full manual-validation record of that failure, its confirmed cause, and the fix. **Overall manual
validation for Phase 3B-3 is not complete**: the spell-slot remaining-value test (checklist item H)
remains blocked, and every item below must be re-exercised against the corrected `/totalitydebug
resource parity` path (new checklist item N, §28.2) since the command students used no longer exists
under its original path.

### 16.1 Remaining checklist items (not performed in this session)

**A. Development gating — CLOSED (§29.1)**
- ✅ Launch a development client: `/totalitydebug resource parity` appeared in tab-completion (`/fcc
  help`) and executed. **Confirmed.**
- ✅ Launch (build and run) a non-development/production client: the normal compiled Totality JAR was
  placed in a regular Fabric instance/modpack; `/totalitydebug resource parity` did not appear.
  **Confirmed.**
- ✅ Start a dedicated server: confirmed no client-command classloading exception (see item K below).
  **Confirmed.**

**B. Initial join**
- Invoke the command as early as possible after joining a world, before the four shadow-parity
  Resources have settled. Confirm each of Mana/Stamina/Standard Spell Slots/Rage shows a bounded
  `GENERIC_NOT_READY` (or `NO_OBSERVATION_YET` if invoked before the very first parity tick)
  line, never a fabricated numeric value.
- After settling (a few seconds), invoke again and confirm all seven Resources show a real line,
  Health/Food/Breath as `native`/`TRUSTED`, and the four shadow-parity Resources at whatever their
  live classification actually is.

**C. Normal state cross-check**
Compare the command's output line-by-line against the existing, unmodified consumers:
- Health/Food HUD numeric text (`TotalityHudRenderer`), vanilla air bubble bar (Breath).
- Mana/Stamina HUD bars.
- Spell radial's displayed remaining slots per level (see item H below — the specific semantic
  cross-check).
- Rage HUD pip bar / Class tab, only if currently playing a Barbarian with an active Rage pool.

**D. Spend/restore**
Exercise, one at a time: Mana spend + regen, Stamina spend + regen, spell-slot spend + Long Rest
restore, Rage spend + Long Rest restore. At each step, invoke the command and compare its
`generic=`/`legacy=` pair and classification against the corresponding live HUD/menu display.
**Performed (§16.0 old path, §29.1 confirmed after the correction)**: Mana spend (`86/100`,
`EXACT_MATCH`), Stamina spend (`92/110`, `EXACT_MATCH`), and Rage spend (`0/2`, `EXACT_MATCH`) were
manually confirmed. Spell-slot spend was **also since confirmed** (§29.1): one level-1 slot spent,
generic and legacy both `1/2` remaining, `EXACT_MATCH`. Regen/Long Rest restoration for any Resource
was not specifically confirmed with captured values in either round — not claimed here.

**E. Reconnect**
Disconnect, then reconnect. Confirm the very next invocation, before the four Resources have
resettled, again shows a bounded `NO_OBSERVATION_YET`/`GENERIC_NOT_READY` line rather than a stale
value from the previous connection, and that all seven rebuild correctly afterward.

**F. Dimension transfer (the known live Rage finding — see §17 below)**
With Rage at a confirmed 1/2 in the Overworld: invoke the command (expect `EXACT_MATCH`, `generic=1/2
| legacy=1/2`). Enter the Nether; invoke again (expect `PERSISTENT_MISMATCH`, `generic=1/2 |
legacy=0/0`, per the confirmed Phase 3B-2B finding). Return to the Overworld; invoke again (expect
the same `PERSISTENT_MISMATCH` reading to persist — no false recovery). Confirm invoking the command
itself, at any point in this sequence, never changes what the next invocation reports (no state
mutation from reading).

**G. Death/respawn**
Following F, die and respawn. Invoke the command; expect a return to `EXACT_MATCH`,
`generic=1/2 | legacy=1/2`, matching the confirmed Phase 3B-2B respawn-recovery finding.

**H. Spell-slot semantic cross-check — CLOSED (§29.1)**
Spend exactly one known spell slot (e.g. a level-1 slot with 2 previously unspent). Invoke the
command; confirm the `remaining` value shown for that partition (e.g. `1:1/2`) matches the spell
radial's own displayed remaining-slot count for that level, not the used count — explicitly ruling
out a remaining/used inversion. This is the "major Phase 3C readiness concern" the task named
directly; this session's automated tests (§8, §11) prove the *labeling and source semantics* are
correct. **Originally confirmed blocked** (the player could not switch to Wizard, so no standard
spell-slot maximum was available to spend from), then **closed by manual validation as item N.8**
(§29.1): the player became a Wizard, spent one level-1 slot, and confirmed generic `1/2` remaining,
legacy `1/2` remaining, `EXACT_MATCH`, agreeing with the spell radial's own display — no remaining/
used inversion. This item is now complete.

**I. FormulaResolver Mana route**
Per the Phase 3B-2B/3B-2C reports: the previously-tested ordinary Grimoire spell cast did not invoke
`FormulaResolver.tryCast`; live gameplay reachability of that code path remains unestablished. This
session did not identify or exercise a concrete live route either — record as **not manually
exercised** unless a future session identifies one. Do not modify `FormulaResolver` to force this.

**J. Rage maximum-change route**
Per the Phase 3B-2C report: no safe existing live route to increase Barbarian Rage maximum mid-
connection was identified. Exercise only if an existing, already-implemented class-level-up flow
provides one; otherwise record as **not manually exercised**. Do not add a route solely for this
test.

**K. Dedicated server — CLOSED (§29.1)**
Start a dedicated server; confirm it reaches `Done (...)!` with no `ClassNotFoundException`/
`NoClassDefFoundError` referencing `ClientResourceParityInspectionCommand`,
`ClientResourceParityReportAssembler`, `ClientResourceParityReport`, `ClientResourceParityReportLine`,
or `ClientResourceParityReportInput`. Per §22's own inspection, only
`ClientResourceParityInspectionCommand` (the sole `@Environment(EnvType.CLIENT)` type among the five)
is actually at risk of a classloading failure if something ever referenced it server-side — nothing
does (§3, §22). **✅ Confirmed**: the server reached `Done (0.332s)! For help, type "help"` with no
client-command or parity-report classloading exception. Two **unrelated, pre-existing** verification
failures (Provisioner and OffhandAttack self-test verifications — see §29.1) were also observed on
this same dedicated-server run; they are documented as unrelated and were **not** fixed, per explicit
instruction.

**L. Repeated invocation**
Invoke the command several times in a row at each of: settled `EXACT_MATCH`, mid-transition
`GENERIC_NOT_READY`/`TRANSITIONAL_MISMATCH`, steady `PERSISTENT_MISMATCH`, and just-recovered states.
Confirm: identical output on identical underlying state; no additional Phase 3B-2C
entry/recovery DEBUG log line is produced merely by invoking the command (this session's automated
tests already prove this at the unit level — §9/§11 — this item closes the loop with a real client
log inspection).

**M. Command-root coexistence — CONFIRMED FAILED, then corrected (§28.1)**
This item, as originally written in the second correction pass, asked whether the shared `totality`
root actually coexisted correctly at runtime. **Manual validation answered this: it did not.** With
the original `/totality resource parity` registration, ordinary server-side `/totality` commands
(`showancestry`, `ancestry`, `wallet`) failed client-side with a Brigadier syntax exception instead of
reaching the server — see §28.1 for the full record. The command has since been moved to
`/totalitydebug resource parity` specifically to eliminate this class of collision, and the shared
`totality` root is no longer registered client-side at all (not retained even as an alias). This
former item M is now closed as **root cause confirmed and corrected**; whether the *new* root
coexists cleanly is item **N** below.

**N. Post-correction re-verification (added by §28.2) — ALL 10 ITEMS NOW CONFIRMED (§29.1)**
1. ✅ `/fcc help` lists `/totalitydebug resource parity`. **Confirmed.**
2. ✅ `/totalitydebug resource parity` executes. **Confirmed.**
3. ✅ `/totality resource parity` no longer exists as a client command. **Confirmed** — the obsolete
   client alias was removed; ordinary `/totality` commands now reach the server instead.
4. ✅ Existing commands execute normally. **Confirmed** directly, as distinct pieces of evidence — not
   inferred from one another: `/totality wallet` executes normally; the ancestry command executes
   normally (ancestry reset); the class command executes normally (class reset); and representative
   additional `/totality` branches execute normally. `/totality wallet` and the class command are
   distinct evidence — the class command is never described as "wallet-equivalent" anywhere in this
   report.
5. ✅ Existing server-side `/totality` autocomplete remains available. **Confirmed directly** — tested
   separately from command execution, not inferred from it: existing server-side `/totality` branches
   appear through normal autocomplete.
6. ✅ No `ClientCommandInternals` syntax exception for ordinary server-side `/totality` commands.
   **Confirmed** — none occurred for `/totality wallet`, the ancestry command, the class command, or
   the other representative branches exercised in item 4.
7. ✅ Smoke test after the path change. **Confirmed** — one fixed `[Resource Parity]` header plus
   exactly seven Resource messages; chat's own visual line-wrapping of a long message was confirmed to
   not represent additional report entries (§29.1).
8. ✅ Became a Wizard and completed the previously-blocked spell-slot remaining-value test.
   **Confirmed** — see §20 (now closed) and §29.1: generic and legacy both read `1/2` remaining after
   spending one level-1 slot, `EXACT_MATCH`, agreeing with the spell radial's own display.
9. ✅ Dedicated server, no client-command/parity-report classloading failure. **Confirmed** — server
   reached `Done (0.332s)! For help, type "help"` with no such exception. Two **unrelated**, pre-
   existing verification failures (Provisioner, OffhandAttack) were also observed and are recorded as
   unrelated in §29.1 — not fixed, not in scope.
10. ✅ Production/non-development absence verified where practical. **Confirmed** — the normal compiled
    Totality JAR was placed in a regular Fabric instance/modpack; `/totalitydebug resource parity` did
    not appear; development-only registration passed.

All ten items were performed by the user and are recorded in full in §29.1 — this checklist is now
complete, not merely proposed.

---

## 17. Known live Rage dimension-transfer discrepancy — inspectable, not fixed; documented migration motivation, not the blocker (see §29.4)

Per the Phase 3B-2B report §30.5 (already manually confirmed, cited not repeated): before a
dimension transfer, in the Overworld, Rage reads generic 1/2, legacy 1/2, `EXACT_MATCH`. After
entering the Nether: generic Rage remains 1/2, legacy Rage becomes 0/0, classification becomes
`PERSISTENT_MISMATCH`, and the existing legacy Rage HUD disappears. Returning to the Overworld does
not repair the legacy state on its own — only a death/respawn does (per §30.6). This is a confirmed
live legacy Rage dimension-resynchronization bug, detected — not caused — by the parity system.

This Phase 3B-3 command is now the concrete tool that makes this discrepancy on-demand inspectable
(`/totalitydebug resource parity` would show exactly `totality:rage | PERSISTENT_MISMATCH |
generic=1/2 | legacy=0/0 | ...` at that moment) — **no fix was applied**. `PlayerChargesComponent`, its
sync packet, and every dimension-change event handler were not opened for editing at any point in this
session.

**Triage decision — Phase 3C Rage presentation migration relative to this bug**: recorded as
evidence + recommendation only, per the task's explicit instruction not to silently decide.
- Evidence: the *generic* side (the side any Phase 3C-migrated presentation would read) remains
  correct (1/2) throughout the entire dimension-transfer/return/respawn sequence — it is only the
  *legacy* mirror that goes stale. A Phase 3C consumer that switches its presentation source from
  the legacy `PlayerChargesComponent` read to the generic façade would therefore not inherit this
  specific bug — it would in fact stop displaying a stale 0/0 during the exact window this bug
  currently causes the legacy HUD to disappear.
- Recommendation (not a decision): Rage presentation migration is **not source-level blocked** by
  this specific bug — migrating to the generic side plausibly *fixes* the symptom as an incidental
  side effect, rather than requiring a prior narrow fix. **This bug is therefore documented as
  migration motivation, not the current migration blocker** (§21, §29.4) — the actual remaining
  blocker for Rage presentation readiness is the separate Rage-maximum synchronization/fallback
  concern (§19), not this one. This recommendation is offered without a fresh live re-verification of
  the dimension-transfer sequence itself in this session (§16 item F is still pending, and no new
  evidence for it was supplied during the manual-validation-completion round either) — a full,
  unconditional migration decision should still wait for that live confirmation. Phase 3B-3 does not
  fix this bug and does not change Rage's presentation source.

---

## 18. FormulaResolver Mana synchronization gap — status unchanged

Per the Phase 3B-2B/3B-2C reports: `FormulaResolver.tryCast` spends Mana via
`PlayerManaManager.removeMana` without sending the legacy `SyncManaPayload` — a source-level gap that
remains present and unmodified (confirmed: `FormulaResolver.java` was not opened for editing at any
point this session). The previously-tested ordinary Grimoire spell cast did not reach this code path
in manual testing; live gameplay reachability remains **not manually proven either way**. This
session did not identify a new concrete live route to exercise it.

**Recommendation on Mana consumer migration**: should remain blocked pending a call-site
reachability audit (confirming whether any real player-facing spell-casting path actually reaches
`FormulaResolver.tryCast` in current content), rather than proceeding on a documented-fallback-risk
basis — the risk here is a silently stale legacy Mana HUD during whatever window a player uses that
specific route, and no evidence yet establishes how exposed that window actually is. This is a
recommendation, not a decision this session is authorized to finalize.

---

## 19. Rage maximum synchronization gap — status unchanged; this is the actual remaining Rage-presentation blocker (see §29.4)

Per the Phase 3B-2C report: `PlayerChargesComponent.applySyncPacket` may retain a stale maximum for
an already-existing legacy Rage pool; no safe existing live route to exercise a maximum change
(without adding new gameplay mutation solely for testing) was identified in this or any prior
session, including this manual-validation-completion session. `PlayerChargesComponent` was not
opened for editing this session.

**Recommendation**: Rage presentation migration should wait for either (a) a focused live route
using an already-existing class-level-up flow, if one exists and can be confirmed safe, or (b) a
source-level fix to `applySyncPacket`'s maximum-handling — whichever is cheaper to arrange — before
being treated as fully validated. This is offered as a recommendation only. **This gap, not §17's
dimension-transfer finding, is the actual reason Rage's two presentation consumers remain classified
`BLOCKED BY SPECIFIC GAP` in §21** — §17's dimension-transfer finding is evidence *for* migration
being plausible (the generic side stays correct throughout), not a reason to block it; this separate
maximum-sync gap is what the classification is actually waiting on. Phase 3B-3 does not fix this gap
and does not change Rage's presentation source or its maximum-handling.

---

## 20. Standard spell-slot remaining/used interpretation — CLOSED (see §29.1)

Confirmed at the source level (§8): `StandardSpellSlotsResourceAdapter.resolve(...)` stores
`current = maximum - used`, so every existing wire/façade/summary/parity value for this Resource is
already "remaining," not "used." This session's command and its tests make that semantic explicit in
every rendered label (`remaining=...`) and prove the word "used" never appears in the command's own
output for this Resource (§8/§11).

**Closed by manual validation (§29.1).** The player became a Wizard, spent one level-1 spell slot, and
invoked the command: generic level 1 read `1/2` remaining, legacy level 1 read `1/2` remaining,
classification `EXACT_MATCH`, and the command's `remaining` labeling agreed with the spell radial's own
displayed remaining-slot count for that level — no remaining/used inversion. This was the single most
important open item before treating standard-spell-slot Phase 3C migration as ready; it is now closed.

---

## 21. Per-consumer Phase 3C readiness matrix — UPDATED (see §29.2)

| Consumer | Classification | Evidence |
|---|---|---|
| 1. Breath debug/menu presentation | **READY** | Breath has no shadow-parity concern at all (native-only, `NATIVE_SYNCHRONIZATION`) — the façade is the only source and was already validated in Phase 3B-1. Manual validation (§29's confirmed results, item 4) exercised the live native-façade read for Health/Food/Breath together and confirmed trusted values with no fabricated classification. |
| 2. Rage secondary HUD | **BLOCKED BY SPECIFIC GAP** — corrected reasoning (§29.4) | **The dimension-transfer legacy-mirror bug (§17) is documented migration motivation, not the current blocker**: the generic side stays correct throughout a dimension transfer, so migrating this consumer to the generic view would plausibly stop it from ever showing the stale legacy `0/0` in the first place. **The actual remaining blocker is the separate, unresolved Rage-maximum synchronization/fallback concern (§19)** — `PlayerChargesComponent.applySyncPacket` may retain a stale maximum for an already-existing pool, and no safe live route to prove or disprove this has been exercised. Ordinary (non-maximum-changing) Rage spend was manually confirmed synchronized (§29.1 item 6: `0/2` generic/legacy, `EXACT_MATCH`), but this does not exercise the maximum-change path §19 concerns. Neither gap was fixed this session, per explicit instruction. |
| 3. Class-tab Rage presentation | **BLOCKED BY SPECIFIC GAP** — corrected reasoning (§29.4) | Same reasoning as #2 applies identically — the Class tab reads the same legacy `PlayerChargesComponent` mirror, so the same migration-motivation-vs-actual-blocker distinction holds. |
| 4. Spell-radial slot presentation | **READY** | Closed by manual validation (§20, §29.1) — generic and legacy both read `1/2` remaining after spending one level-1 slot, `EXACT_MATCH`, and the command's labeling was confirmed to agree with the spell radial's own remaining-slot display. The prior "requires manual validation" blocker is resolved. |
| 5. Mana HUD | **BLOCKED BY SPECIFIC GAP** — unchanged | The `FormulaResolver` legacy-sync/reachability gap (§18) remains unproven either way; this session did not identify or exercise the specific `FormulaResolver.tryCast` route, per explicit instruction not to fix or force it. Ordinary Mana spend was manually confirmed synchronized (§29.1 item 6: `86/100`, `EXACT_MATCH`), but this does not exercise the specific rune-casting path the gap concerns. |
| 6. Stamina HUD | **READY** | Manually confirmed synchronized spend (§29.1 item 6: `92/110` generic and legacy, `EXACT_MATCH`). No open source-level gap comparable to Mana/Rage's exists for Stamina specifically (only the pre-existing, already-tolerated join-time-push asymmetry vs. Mana, classified `EXPECTED_TRANSITIONAL`/`EXPECTED_SEMANTIC_DIFFERENCE` at the tracker level, not a bug, and not itself a migration blocker). |

**Corrected overall Phase 3C readiness decision (§29.3/§29.4)**: **three of six consumers are READY**
(Breath, spell-radial, Stamina); **three of six remain BLOCKED BY SPECIFIC GAP** (Rage secondary HUD,
Class-tab Rage, Mana HUD). The table and this prose agree exactly — no "four of six" framing is used
anywhere in this report. For Rage's two consumers specifically, the dimension-transfer bug (§17) is
recorded as migration motivation (evidence *for* migrating, since the generic side is the one that
stays correct), not the reason they remain blocked; the actual blocker is the separate Rage-maximum
synchronization/fallback concern (§19). Phase 3C migration may reasonably proceed for Breath, spell-
radial, and Stamina; Mana and both Rage consumers should remain blocked until their respective named
gaps (§18 for Mana; §19 for Rage) are separately addressed — not as part of this closure — or a
documented-fallback decision is made by external review. This report does not itself authorize
starting Phase 3C — that remains a separate decision, and no Resource behavior or migration was
changed in reaching this classification.

---

## 22. Dedicated-server checklist — CONFIRMED BY MANUAL VALIDATION (see §29.1)

See §16 items K/M/N. Confirmed by source/import inspection: only `ClientResourceParityInspectionCommand`
carries `@Environment(EnvType.CLIENT)` among the **five** production types this slice contains. The
other four — `ClientResourceParityReportLine`, `ClientResourceParityReport`,
`ClientResourceParityReportAssembler`, and `ClientResourceParityReportInput` — have **zero**
Minecraft/Fabric dependency at all and would load safely even if referenced server-side (they are not;
confirmed by `ClientResourceParityReportPureBoundaryTest`'s import-scan, §26.1). No common/server
initializer references any of the five new types (`commandClassIsNotReferencedFromServerInitialization`
scans `Totality.java`, `ModEvents.java`, and `TotalityCommands.java` directly for this;
`serverCommandTreeFileWasNotModifiedByThisSlice` further confirms `TotalityCommands.java` gained no
`resource`/`parity` branch).

**Empirically confirmed by manual validation (§29.1, item 8/N.9)**: a dedicated server reached
`Done (0.332s)! For help, type "help"` with no client-command or parity-report classloading exception
— the source/import-based prediction above is now also confirmed live, not merely predicted. Two
unrelated, pre-existing verification failures (Provisioner, OffhandAttack) were also observed on that
same run and are documented as unrelated in §29.1/§29.3 — not fixed, not in scope.

---

## 23. Explicit out-of-scope confirmation

Confirmed by direct inspection of this session's complete diff (§3):
- **Resource authority/values/formulas/mutation**: unchanged — no adapter, definition, component,
  server-tick, or packet-handling file was touched.
- **Packet ordering/payloads/sync eligibility/trust calculation**: unchanged — no networking class
  was touched.
- **Parity comparison/grace-period semantics**: unchanged — `ClientResourceParityTracker`, every
  comparator/policy, and `ClientResourceParityPoll` are byte-for-byte as Phase 3B-2C left them; the
  new code only ever reads their already-committed output via the existing
  `ClientResourceParityObservations` snapshot.
- **Persistent-mismatch logging-episode behavior**: unchanged — `ClientResourceParityLogObserver`,
  `ClientResourceParityLogTransitionTracker`, `ClientResourceParityLogDiagnostics` were not modified;
  the new command never references `ClientResourceParityLogObserver` at all from its executable code
  (`gatherReportMethodBodyNeverReferencesTheLogObserver`), and repeated command invocation is proven
  not to disturb the logging episode tracker's state (`repeatedGatherReportDoesNotDisturbTheLoggingEpisodeTracker`).
- **No HUD/class/spell-casting/Rage/legacy-mirror behavior change**: no HUD, screen, menu, radial,
  tooltip, movement, class, spell, or legacy-manager file was touched.
- **No Resource mutation, packet-send, legacy-manager write, UI overlay, entitlement, keybind, or
  consumer migration** anywhere in the new code — confirmed by grep across the new production file
  for a blacklist of forbidden tokens (`productionFilesIntroduceNoForbiddenDependency`).
- **No ancestry, Provisioner, Rest, or combat/power-attack file was opened for editing** at any point
  this session.
- **No Phase 3C migration** — no existing HUD/menu/radial/class-tab consumer's read source was
  changed to point at the generic façade or the parity observation; they all still read exactly what
  they read before this session.
- Nothing was staged, committed, or pushed (verified immediately before writing this report — see
  final response for the exact final `git status --short`).
- **This correction pass (§26) additionally confirmed**: no Resource/ancestry/Provisioner/Rest/combat
  bug was fixed while correcting the three blockers plus the bounded-header and command-root-wording
  items; no consumer migration occurred; Phase 3C did not start; nothing was staged, committed, or
  pushed by the correction either.
- **The second correction pass (§27) additionally confirmed**: exactly seven Resource lines and eight
  chat lines are structurally guaranteed; no unbounded input-count allocation is possible in either
  `ClientResourceParityReport` or `ClientResourceParityReportAssembler`; accessor and result/observation-
  conversion failures are now structurally distinct (five separate catch sites, never one shared
  scope); one Resource's failure (of either kind) still cannot abort another; the pure report files
  remain Minecraft/Fabric-independent (re-confirmed — `ClientResourceParityReportPureBoundaryTest` was
  not modified by this pass and still passes); native Resources still receive no parity classification;
  shadow classifications still come only from existing observations; no state mutation, no parity
  logging-episode mutation, no packet, no legacy-manager write, no consumer migration, no legacy bug
  fix, no Phase 3C work, and no unrelated bug fix occurred in this second pass either.
- **The third correction pass (§28) additionally confirmed**: the command now registers exactly
  `totalitydebug`, never `totality`, client-side; no server command-registration file
  (`TotalityCommands.java`, `Totality.java`, `ModEvents.java`) was modified; report gathering,
  formatting, ordering, boundedness, and read-only behavior are all unchanged — the report still
  produces exactly seven Resource lines and eight chat lines (unchanged from §6/§11); no second parity
  algorithm, no Resource mutation, no packet, no consumer migration, no legacy bug fix, and no Phase 3C
  work occurred in this third pass either. Live command-tree coexistence under the corrected root is
  explicitly **not** claimed proven by any automated test — that remains item N of the manual
  checklist (§16.1).
- **This manual-validation-completion session (§29) additionally confirmed**: no source-code file was
  opened for editing at all (§29.5) — only the report and the final review bundle were produced; none
  of Rage dimension-transfer (§17), `FormulaResolver` (§18), Rage-maximum sync (§19), Provisioner,
  Offhand Stamina, ancestry dimensions, or the Rest passenger warning were fixed (§29.3); no consumer
  migration occurred; Phase 3C did not start; nothing was staged, committed, or pushed.

---

## 24. Manual results still pending — MOSTLY CLOSED (see §29)

Checklist item **N** (all 10 sub-items, §16.1) is now **fully complete** (§29.1), closing the command-
root re-verification, the spend-synchronization confirmation, the previously-blocked spell-slot test,
the dedicated-server run, and the production/non-development gating check. What remains genuinely
pending, with no fabricated evidence added for any of it:
- The live dimension-transfer/respawn Rage sequence (§16 F/G) — needed to convert §17's
  recommendation into a fully closed decision. **Not performed this session** — no new evidence was
  supplied for the Nether-transfer/respawn sequence specifically; §17's own citation of the historical
  Phase 3B-2B manual confirmation stands as before.
- The FormulaResolver/Rage-maximum live-route checks (§16 I/J) — remain **not manually exercised**, per
  the task's own instruction not to add a route solely for testing. This is an acceptable, expected
  outcome, not a blocker to Phase 3B-3 closure — §18/§19 already record these as separately-tracked,
  deliberately-unfixed gaps.
- Items B, C, E, and L (initial-join settling behavior, a full HUD-by-HUD cross-check beyond the
  native/spend checks already confirmed, reconnect behavior, and repeated-invocation log inspection)
  were not independently re-confirmed with newly captured values this session — the user's own report
  that the practical checklist was substantially exercised (§16.0, §29.1) stands, but this report does
  not invent specific numeric values or log lines for these items beyond what was actually supplied.
- **The command-root coexistence check (§16 M)** is fully closed: manual validation confirmed the
  original failure (§16.0, §28.1), the fix was applied in source (§28.1), and the corrected root's own
  coexistence was then manually re-verified (§16 N, §29.1) — existing server-side `/totality` commands
  (ancestry reset, class reset) now execute normally again.
- **The third correction pass's checklist item N is closed in full** (§28.2 defined it; §29.1 completed
  it) — this is the first time in Phase 3B-3's history that every item of a manual-validation checklist
  section has been completed with real, captured evidence.

---

## 25. Stop-point status (original implementation)

- Implementation, automated validation (`compileJava`, `compileTestJava`, `test`, `runDatagen`,
  `build`, `git diff --check`), this report, and the review bundle (see final response) are
  complete.
- No manual validation was performed in this session, per the task's explicit instruction.
- No Resource, ancestry, Provisioner, Rest, or combat bug was fixed.
- Nothing was staged, committed, or pushed.
- Phase 3C was not started — no consumer migration occurred anywhere in this diff.

**Superseded in part by §26 below** — the external-review correction pass fixed three genuine
architectural defects this original implementation had (a non-pure report boundary that still
imported `Identifier`; a fabricated `NOT_APPLICABLE` classification on native lines; an unbounded/
mutable report header) plus one containment gap (a single Resource's access failure could abort the
whole report) and one wording overreach (an unproven "structurally separate" command-tree claim) —
all before any manual validation or commit occurred.

**Further superseded in part by §27 below** — a second external-review correction pass found that
§26's own fixes did not fully close two of those same concerns: the bounded header (§26.4) did not
bound the *complete report*, which still accepted any number of lines including zero; and the access-
failure containment (§26.3) used one catch scope spanning both the accessor call and a separate
conversion step, making its own documented `ACCESS_ERROR` claim false whenever a conversion failure
occurred instead. Both were fixed, again before any manual validation or commit occurred.

---

# EXTERNAL REVIEW CORRECTION PASS — 2026-07-29

## 26. Correction: pure boundary, native-only wording, access containment, bounded header, command-root wording

### 26.1 Blocker 1 — the report layer was not actually pure

**The defect.** As originally implemented, `ClientResourceParityReportLine` declared
`record ClientResourceParityReportLine(Identifier resourceId, String text)`,
`ClientResourceParityReportAssembler.assemble(...)` accepted
`Map<Identifier, ClientResourceQueryResult>` and `Map<Identifier, Optional<ClientResourceParityObservation>>`
directly, and both `ClientResourceQueryResult`/`ClientResourceParityObservation` themselves carry
`net.minecraft.resources.Identifier` fields. The original report's §5 acknowledged this ("import
nothing... beyond `Identifier`") as an accepted exception rather than meeting the task's explicit
requirement that the pure report layer carry no Minecraft/Fabric dependency at all.

**The fix.** A new pure type, `ClientResourceParityReportInput` (§3), is now the only vocabulary the
pure assembler ever sees. `ClientResourceParityReportLine`'s `resourceId` is now a plain `String`
(the `Identifier`'s own `toString()` form). `ClientResourceParityReportAssembler.assemble(...)` now
takes a single `List<ClientResourceParityReportInput>` and holds no `Identifier`/`PlayerResourceIds`
knowledge of its own at all — not even the canonical seven-Resource ordering, which moved entirely to
the client command boundary. `ClientResourceParityInspectionCommand` is now the **only** file in this
slice that imports `Identifier`, `PlayerResourceIds`, `ClientResourceService`,
`ClientResourceParityObservations`, `ClientResourceQueryResult`, or `ClientResourceParityObservation` —
it converts each live result to the pure vocabulary via `toNativeInput`/`toShadowInput` before ever
calling the assembler.

**Reuse, not re-encoding.** Per the task's explicit instruction not to introduce a redundant
vocabulary, `ClientResourceParityReportInput` directly reuses already-pure existing Totality types
where appropriate: `ClientResourceTrust` and `ClientResourceUnavailableReason` (both confirmed zero-
import enums) on the native side, and `ClientResourceParityClassification`/`ClientResourceParitySummary`
(confirmed to import nothing beyond `java.util`/each other) on the shadow-parity side. No `Object`
escape hatch, no reflection, and no second copy of any of these four types was introduced.

**Proof.** `ClientResourceParityReportPureBoundaryTest` (new, 2 tests) scans every pure file's actual
`import` lines (not whole-file text, to avoid false-positiving on Javadoc prose that legitimately
mentions `Identifier` to explain its absence) and fails on any import outside `java.*` or this same
pure package family; a second test confirms none of the four pure files carries an `@Environment`
annotation. `ClientResourceParityReportInputTest` (new, 8 tests) covers the new type's own bounding/
validation invariants.

### 26.2 Blocker 2 — native Resources were assigned a fabricated parity classification

**The defect.** The original `renderNativeLine` appended
`"| parity=" + ClientResourceParityClassification.NOT_APPLICABLE` to every Health/Food/Breath line.
No `ClientResourceParityObservation` exists for these three Resources — `ClientResourceParityTracker`
never observes them at all (they are `NATIVE_SYNCHRONIZATION`-mode, structurally outside the shadow-
parity engine's four-Resource scope) — so this classification was invented by the command, not echoed
from the tracker, violating the task's "the parity tracker remains the only classification owner"
requirement even though the specific enum value happened to carry an accurate-sounding name.

**The fix.** Native lines now render the fixed, plain-text presentation label
`comparison=native-only` (`ClientResourceParityReportAssembler.NATIVE_COMPARISON_LABEL`) — a `String`
constant, not an enum member — and no native-rendering code path references
`ClientResourceParityClassification` at all. Shadow-parity lines are unaffected: they still render
`observed.classification()` — the tracker's own already-decided value — verbatim, and the assembler
never constructs a `ClientResourceParityClassification` value anywhere in its own source (confirmed by
direct grep: zero `ClientResourceParityClassification.<CONSTANT>` occurrences in
`ClientResourceParityReportAssembler.java`).

**Proof.** `nativeLineNeverContainsAnyParityClassificationName`/
`everyNativeStatusVariantUsesTheFixedComparisonLabelNeverAClassification` (assembler-level) and
`nativeLinesFromTheRealCommandNeverContainAParityClassificationName` (command-level, through the real
`gatherReport()`) each check every native line against all seven
`ClientResourceParityClassification` constant names. Two tests from the original assembler suite that
asserted the now-rejected `parity=NOT_APPLICABLE` wording were rewritten, not merely deleted, to assert
`comparison=native-only` instead, as part of the same file's broader rewrite for the new
`List<ClientResourceParityReportInput>` API (§11).

### 26.3 Blocker 3 — a single Resource's access failure could have aborted the whole report

**The defect.** The original `gatherReport()` called `ClientResourceService.INSTANCE.query(id)` and
`ClientResourceParityObservations.latest(id)` directly, inside the loop building the two `Map`s, with
no exception handling at that call site at all. The assembler's own `catch (RuntimeException)` only
guarded its *own* rendering step — by the time rendering ran, gathering had already either succeeded
for every Resource or already thrown out of `gatherReport()` entirely, silently discarding the other
six Resources' worth of otherwise-available diagnostic information.

**The fix.** `ClientResourceParityInspectionCommand` gained a package-private two-argument seam,
`gatherReport(Function<Identifier, ClientResourceQueryResult> nativeQuery, Function<Identifier,
Optional<ClientResourceParityObservation>> shadowLookup)`, which the production zero-argument
`gatherReport()` delegates to, wiring the real `ClientResourceService.INSTANCE::query` and
`ClientResourceParityObservations::latest` accessors. `gatherNative`/`gatherShadow` each wrap their one
accessor call for one Resource in its own `catch (RuntimeException)` — never `catch (Throwable)` — and
convert a caught failure into a bounded `Native.Status.AccessError`/`ShadowParity.Status.AccessError`
input carrying only the exception's simple class name (via `boundedExceptionName`, itself bounded to
`ClientResourceParityReportInput.MAX_TOKEN_LENGTH` independently of that input type's own compact-
constructor bound — belt and suspenders, not a single point of truncation). Rendering then produces
`ACCESS_ERROR(<name>)` for a native failure and the *distinct* `OBSERVATION_ACCESS_ERROR(<name>)` for a
shadow-parity failure, so a reader can always tell which boundary actually failed. No message, stack
trace, coordinate, UUID, path, or arbitrary object text ever reaches the report — confirmed by
`extremelyLongExceptionMessageNeverEntersTheOutput`, which injects a 14,000-character exception
message and asserts none of it appears in the rendered line.

**Distinguished from formatter failure.** An access failure is caught at the *gathering* step, before
the pure assembler ever sees that Resource; a formatter failure is caught inside the assembler's own
`renderLine`, after gathering already succeeded. §10's table now documents both, with their distinct
wording, side by side.

**Proof (10 new/extended tests in `ClientResourceParityInspectionCommandTest`)**: one throwing native
reader does not abort the report and the other six lines remain correct
(`oneThrowingNativeReaderDoesNotAbortTheReport`); the symmetric shadow-lookup case
(`oneThrowingShadowLookupDoesNotAbortTheReport`); the adversarial-message case above; repeated failing
invocations remain deterministic and still produce exactly 8 chat lines
(`repeatedFailingInvocationsRemainDeterministic`); an access failure mutates no unrelated parity
observation (`accessFailuresDuringGatherReportMutateNoParityObservationOrLoggingEpisodeState`); and a
source-scan confirms `catch (Throwable` never appears while `catch (RuntimeException` does
(`neverCatchesThrowableOnlyRuntimeException`). No fake production logger or packet was added anywhere
in this correction.

### 26.4 Bounded-header invariant

**The defect.** `ClientResourceParityReport` originally declared
`record ClientResourceParityReport(String header, List<ClientResourceParityReportLine> lines)` — a
public, arbitrary-`String` constructor parameter, while the class's own Javadoc described the complete
report as "bounded."

**The fix.** `header` was removed from the record entirely. `ClientResourceParityReport.HEADER` is now
a `public static final String` constant (`"[Resource Parity]"`), and `chatLines()` prepends that
constant rather than an instance field. There is no constructor path by which a caller — production or
adversarial test — could ever supply a different or unbounded header string.

**Proof.** `ClientResourceParityReportTest` (new, 5 tests) proves this structurally via
`getRecordComponents()` (exactly one component, `lines`, no `header`), asserts the constant's exact
value and a short length bound, and confirms `chatLines()` always begins with it — including for an
empty line list, and confirms the line list itself remains defensively copied/immutable.

### 26.5 Command-root wording correction

**The defect.** §4 of the original report stated the client command tree is "structurally separate"
from the existing server-side `/totality ...` tree, presented as an established fact rather than a
claim requiring a live-client demonstration this session never performed.

**The fix.** §4 now states only the narrower, source-provable claims: the new client branch is
registered under the shared `totality` literal; `TotalityCommands.java` (the server tree) was not
modified by this slice; no server command exposes any client state. Whether the two trees actually
coexist correctly at runtime — i.e., the new client branch does not suppress or shadow the existing
server branches in tab-completion/execution, and vice versa — is now explicit manual-validation
checklist item **M** (§16), not a claim of fact. The command path itself (`/totality resource parity`)
was **not** changed because of this pending check, per the task's own instruction.

**Proof.** `serverCommandTreeFileWasNotModifiedByThisSlice` (new) confirms `TotalityCommands.java`
gained no `resource`/`parity` branch; `commandClassIsNotReferencedFromServerInitialization` (existing,
unchanged) continues to confirm no server initializer references the client-only command class.

### 26.6 Automated results after this correction pass

- `compileJava`/`compileTestJava`: `BUILD SUCCESSFUL`, no warnings.
- `test --rerun`: `BUILD SUCCESSFUL` — **856 tests, 0 failed, 0 errors, 0 skipped** (829 pre-correction
  + 27 new/rewritten — see §11/§12 for the exact per-file breakdown).
- `runDatagen`: `BUILD SUCCESSFUL`, `349 → 349`, `written: 0`.
- `build`: `BUILD SUCCESSFUL`, only the pre-existing Gradle/Loom deprecation notice.
- `git diff --check -- src/main/java src/test/java`: exit 0, no output.
- No test's expected *behavior* was weakened — the two tests that changed expectation (from
  `parity=NOT_APPLICABLE` to `comparison=native-only`) did so because the correction itself changed
  the wording those tests were always meant to pin down, not because a bug was hidden.

### 26.7 What remains unchanged by this correction

Per the task's explicit instruction, this correction pass did not re-open or alter §17 (the known Rage
dimension-transfer discrepancy), §18 (`FormulaResolver`), §19 (Rage-maximum sync), or §21 (the Phase 3C
per-consumer readiness matrix) — none of those conclusions' underlying evidence changed as a result of
this purely architectural/wording correction. §20 (spell-slot remaining semantics) is unchanged in
substance; only the carrier type changed (§8, §26.1). No manual validation was performed or recorded
as complete anywhere in this correction pass. Nothing was staged, committed, or pushed.

**Superseded in part by §27 below** — the second external-review correction pass found two further
defects the first pass's own fixes did not fully close: the report's total line count remained
completely unbounded (§27.1), and the first pass's own access-failure containment used a single catch
scope that made its own documented `ACCESS_ERROR` claim false whenever a conversion failure actually
occurred (§27.2) — both found and fixed before any manual validation or commit occurred.

---

# SECOND EXTERNAL REVIEW CORRECTION PASS — 2026-07-29

## 27. Correction: exact report-size invariant, and access-vs-conversion failure separation

### 27.1 Blocker 1 — the report's total line count was still structurally unbounded

**The defect.** The first correction pass (§26.4) made the *header* fixed and each *individual line*
length-capped, but left `ClientResourceParityReport`'s `lines` list accepting any size at all —
including zero. `ClientResourceParityReportAssembler.assemble(...)` mirrored this: it allocated its
output `ArrayList` sized to whatever `inputs.size()` the caller happened to supply, with no validation
at all. The first correction pass's own test suite made this concrete and wrong in two ways: it
explicitly asserted an **empty** report was valid
(`ClientResourceParityReportTest.emptyLinesStillProducesExactlyOneHeaderLine`), and several assembler
tests called `assemble(List.of(oneInput))` — a **one**-line report — as an accepted, ordinary case. A
bounded header and bounded individual lines are necessary conditions for a bounded report, but they
are not sufficient: nothing previously prevented a caller (or a future bug) from handing the assembler
3, 12, or 100,000 inputs and getting a correspondingly-sized report and a correspondingly-sized
`ArrayList` allocation back.

**The fix.** `ClientResourceParityReport` gained two named constants —
`RESOURCE_LINE_COUNT = 7` and `CHAT_LINE_COUNT = RESOURCE_LINE_COUNT + 1 = 8` — and its compact
constructor now rejects any `lines` whose `size()` is not exactly `RESOURCE_LINE_COUNT`, via an
`IllegalArgumentException`, checked *before* `List.copyOf(lines)` ever runs (an O(1) size comparison,
never a copy proportional to an adversarial input's size).
`ClientResourceParityReportAssembler.assemble(...)` independently performs the identical check against
`inputs.size()` before allocating its output `ArrayList` — so an adversarial 100,000-entry input list
is rejected at the same O(1) check, never iterated or copied. Per the task's explicit instruction, the
pure assembler was **not** taught canonical Resource ids or ordering to achieve this — it only enforces
the fixed *count*; the client command boundary remains the sole owner of which seven ids populate that
count and in what order (unchanged from §26.1).

**Never silently truncated.** An invalid-sized input is always rejected deterministically
(`IllegalArgumentException`), never silently truncated to seven entries or padded — dropping a real
Resource's line would make the diagnostic output actively misleading (a reader would not know a
Resource went missing), which is worse than a hard failure during development-only tooling.

**Preserving focused line-rendering tests without a test-only production API.** Per the task's
explicit menu of acceptable approaches, this correction chose to make
`ClientResourceParityReportAssembler.renderLine` (the exact per-input render step `assemble(...)`
itself calls in its loop) package-private instead of `private` — not a new, parallel test-only method,
the literal same method production code runs. Every existing single-input rendering test in
`ClientResourceParityReportAssemblerTest` was updated to call `renderLine(input)` directly instead of
wrapping the input in `assemble(List.of(input))` (which would now unconditionally throw, since
`assemble` requires exactly seven). Tests that must exercise `assemble(...)` itself (ordering,
mutation, determinism, and the new rejection tests) now construct a full seven-entry input list via a
small `sevenInputsInOrder(...)` test helper.

**The empty-report test was removed, not weakened.** Per the task's explicit instruction,
`ClientResourceParityReportTest.emptyLinesStillProducesExactlyOneHeaderLine` was deleted rather than
adjusted — it asserted exactly the invalid contract this correction closes, and no valid replacement
assertion exists for "an empty report is acceptable."

**Proof (14 tests, §11: 9 new/replaced in `ClientResourceParityReportTest`, 6 new in
`ClientResourceParityReportAssemblerTest`, minus the one removed test — net effect across both files
matches the task's required-coverage list items 1-14):** exactly seven lines accepted;
`chatLines()` returns exactly eight entries; 0/1/6/8-line lists rejected; a 50,000-line list rejected;
an assembler input list of 6 or 8 entries rejected; a 100,000-entry assembler input list rejected
before proportional allocation; normal command gathering and access/conversion-failure gathering alike
still produce exactly seven Resource lines and eight chat lines
(`gatherReportCoversAllSevenCanonicalResourcesEvenWithNothingSeeded`,
`oneThrowingNativeReaderDoesNotAbortTheReport`'s `assertEquals(7, ...)`,
`repeatedFailingInvocationsRemainDeterministic`'s `assertEquals(8, ...)`); the total rendered
character bound is proven from the fixed line count and per-line cap
(`totalRenderedCharacterBoundIsProvableFromFixedHeaderAndSevenCappedLines`); the line list remains
defensively copied and immutable for a full seven-entry report.

### 27.2 Blocker 2 — accessor failure and result-conversion failure were caught by the same scope

**The defect.** The first correction pass's `gatherNative`/`gatherShadow` each wrapped the *entire*
sequence — calling the accessor **and** converting its result — in one `try`/`catch (RuntimeException)`:

```java
try {
    return toNativeInput(resourceId, nativeQuery.apply(id));
} catch (RuntimeException accessFailure) { ... }
```

The first correction's own implementation report (§10 as it then read) stated that `ACCESS_ERROR`
means specifically "the command boundary's own call into the trusted façade... threw." That statement
was false as written: if `nativeQuery.apply(id)` succeeded but `toNativeInput(...)` — the separate
conversion step — then threw (for example because the accessor returned `null` and the conversion
code's own `switch` expression threw a `NullPointerException` evaluating `switch (null)`), the single
catch block would still produce `AccessError`, mislabeling a conversion failure as an accessor failure.
No test in the first correction's suite exercised this distinction, because none of its injected test
`Function`s ever returned a malformed (e.g. `null`) result — they only ever threw directly, which is
exactly the one case the combined catch handled correctly. The gap was invisible until a reviewer
asked precisely this question: "does the catch scope match the claim?"

**The fix.** `gatherNative` and `gatherShadow` were each split into two sequential, independent
`try`/`catch (RuntimeException)` blocks:

```java
ClientResourceQueryResult result;
try {
    result = nativeQuery.apply(id);
} catch (RuntimeException accessFailure) {
    return ...AccessError(boundedExceptionName(accessFailure));
}
try {
    return toNativeInput(resourceId, result);
} catch (RuntimeException conversionFailure) {
    return ...ConversionError(boundedExceptionName(conversionFailure));
}
```

A new pure input status variant was added to each `Status` sealed interface — `Native.Status
.ConversionError`/`ShadowParity.Status.ConversionError` — a distinct type from `AccessError`, per the
task's explicit instruction not to overload `AccessError` with a second meaning. The assembler renders
these as `RESULT_CONVERSION_ERROR(<name>)`/`OBSERVATION_CONVERSION_ERROR(<name>)` — textually distinct
from `ACCESS_ERROR(<name>)`/`OBSERVATION_ACCESS_ERROR(<name>)` for the identical exception type, so a
reader can never confuse the two by name alone.

**Same bounding discipline as the first correction's `AccessError`.** `ConversionError` reuses the
identical `boundedExceptionName(...)` helper and the same `MAX_TOKEN_LENGTH` bound on
`exceptionSimpleName` — no message, no stack trace, no arbitrary object text, ever. A conversion
failure for one Resource still cannot abort gathering the other six — each Resource's gather step is
independently wrapped, exactly as the first correction pass already established for accessor failures.
No log line is emitted, no state is mutated, and no resync is requested merely because a conversion
failed — identical guarantees to the first correction's accessor-failure containment, now extended to
this second failure class.

**Proof (10 tests, §11, matching the task's required-coverage list items 1-10):** a native accessor
throwing still produces `ACCESS_ERROR` (unchanged, `oneThrowingNativeReaderDoesNotAbortTheReport`); a
shadow accessor throwing still produces `OBSERVATION_ACCESS_ERROR` (unchanged,
`oneThrowingShadowLookupDoesNotAbortTheReport`); a native accessor returning `null` now produces the
distinct `RESULT_CONVERSION_ERROR`
(`nativeAccessorReturningNullResultProducesTheDistinctConversionErrorLine`); a shadow accessor
returning `null` now produces the distinct `OBSERVATION_CONVERSION_ERROR`
(`shadowAccessorReturningNullLookupProducesTheDistinctConversionErrorLine`); access and conversion
labels for the identical exception type are proven textually distinct, at both the assembler layer
(`accessAndConversionErrorLabelsAreTextuallyDistinctForTheSameExceptionName`) and the command layer
(`accessAndConversionFailureLabelsCannotBeConfusedForTheSameResourceAndExceptionType`); the other six
Resource lines remain present after either a native or a shadow conversion failure (asserted inline in
the two conversion tests above via `assertEquals(7, report.lines().size())`); exception messages never
enter the conversion-error output (the injected `null`-returning functions carry no message at all,
and the existing `extremelyLongExceptionMessageNeverEntersTheOutput` continues to prove the accessor-
failure case); repeated conversion failures remain deterministic and still produce exactly 8 chat
lines (`repeatedConversionFailuresRemainDeterministicAndStillProduceExactlyEightChatLines`); and a
conversion failure mutates no unrelated parity observation or logging-episode state
(`conversionFailuresMutateNoParityObservationOrLoggingEpisodeState`). The existing accessor-failure
tests (`oneThrowingNativeReaderDoesNotAbortTheReport`,
`oneThrowingShadowLookupDoesNotAbortTheReport`, `extremelyLongExceptionMessageNeverEntersTheOutput`,
`neverCatchesThrowableOnlyRuntimeException`) were **not** weakened — they still pass unchanged, proving
the split did not regress the first correction's own guarantees.

### 27.3 Bundle-count accuracy correction

The previous turn's final response claimed the first-correction review bundle
(`Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B3_CORRECTION_REVIEW_BUNDLE.zip`) had "28
entries, all 28 SHA-256 checksums pass." That is corrected here: the archive contains **28 entries**,
but `checksums.sha256` — one of those 28 entries — necessarily does not checksum itself, so it lists
exactly **27** checksums, and all **27** passed. This is the same counting convention every bundle in
this phase has used (the checksums manifest is generated by hashing every file already written to the
bundle directory, which does not yet include the manifest file being written); it was correctly applied
when building the bundle, but was misstated as "28 checksums" when reported back afterward. No bundle
file was regenerated or altered to make this correction — it is a reporting-accuracy fix only.

### 27.4 Automated results after this second correction pass

- `compileJava`/`compileTestJava`: `BUILD SUCCESSFUL`, no warnings.
- `test --rerun`: `BUILD SUCCESSFUL` — **875 tests, 0 failed, 0 errors, 0 skipped** (856 pre-second-
  correction + 19 net new — see §11/§12 for the exact per-file breakdown, including the one
  deliberately-removed invalid-contract test).
- `runDatagen`: `BUILD SUCCESSFUL`, `349 → 349`, `written: 0`.
- `build`: `BUILD SUCCESSFUL`, only the pre-existing Gradle/Loom deprecation notice.
- `git diff --check -- src/main/java src/test/java`: exit 0, no output.
- No existing test's expected *behavior* was weakened by this pass. The single exception —
  `emptyLinesStillProducesExactlyOneHeaderLine` — was removed outright because it pinned down exactly
  the invalid contract this pass corrects, per the task's own explicit instruction to do so
  transparently.

### 27.5 What remains unchanged by this second correction pass

This pass did not re-open or alter §17 (Rage dimension-transfer), §18 (`FormulaResolver`), §19
(Rage-maximum sync), §20 (spell-slot remaining semantics — unchanged in substance), or §21 (the Phase
3C per-consumer readiness matrix) — none of their underlying evidence changed as a result of this
purely structural/architectural correction. No Resource, ancestry, Provisioner, Rest, or combat bug was
fixed. No consumer migration occurred. No manual validation was performed or recorded as complete
anywhere in this pass. Nothing was staged, committed, or pushed.

**Superseded in part by §28 below** — this second correction pass's own automated validation was
sound, but it was still only static/structural review; no manual validation had yet been performed
against either the first or second correction's command path. A third pass, driven by **actual manual
validation performed by the user** (not by this automated session), found that the command path itself
— unchanged by either of the first two passes — had a genuine live-client defect: the shared `totality`
root literal caused the client to intercept and break ordinary server-side `/totality` commands. §28
records that manual-validation evidence and the command-root fix.

---

# THIRD CORRECTION PASS — MANUAL-VALIDATION FINDING — 2026-07-29

## 28. Correction: command-root collision confirmed by manual validation, and its fix

### 28.1 The confirmed defect

**This is the first defect in Phase 3B-3's history discovered by actual manual validation, not by
static/source review.** The user manually validated the command using its original path,
`/totality resource parity`, and captured real evidence (§16.0): the command registered, executed, and
produced a correctly-shaped report with correct native/shadow-parity content, including confirmed
spend-synchronization for Mana, Stamina, and Rage. While working through the checklist, the user
attempted ordinary, pre-existing server-side `/totality` commands and found they no longer worked:

```
totality showancestry
totality ancestry
totality wallet
```

each failed **locally, client-side**, with:

```
Incorrect argument for command at position 9: totality <--[HERE]
```

— a Brigadier syntax exception, never reaching the server at all. Autocomplete still listed the
existing server-side `/totality` branches (so the server-side command tree itself was never touched,
consistent with §23's confirmed-unmodified `TotalityCommands.java`), but attempting to actually execute
any of them failed. This also meant the player could not use the existing class-change command to
become a Wizard, which blocked the spell-slot remaining-value manual-validation exercise (§16.0,
checklist item H) entirely.

**Root cause.** Fabric's client-command dispatch (`fabric-command-api-v2`'s `ClientCommandInternals`)
maintains its own client-side Brigadier `CommandDispatcher`. When a player types a command, the client
first attempts to parse it against this client dispatcher; Fabric's own internal logic commits to a
client-registered root literal once that literal matches, and does **not** fall back to forwarding the
remaining, unmatched input to the server if no client-side child node matches it — it raises a syntax
exception locally instead. `ClientResourceParityInspectionCommand` had registered a child under the
literal `totality` (`resource` → `parity`) — the *same* root literal the existing server-side command
tree also uses. Once any node exists under `totality` on the client, the client dispatcher "owns" that
whole root: typing `/totality showancestry` matches the client's `totality` literal, then fails to find
a client-side `showancestry` child, and errors out locally instead of ever being forwarded to the
server. This is a general, well-known Fabric client-command pitfall — registering anything at all under
an existing root literal risks hijacking that entire root client-side — not specific to this command's
particular subtree shape. **Retaining `/totality resource parity` as an alias would not have fixed
this**: the defect is triggered by the mere existence of any client-side registration under `totality`,
regardless of what its children are named.

### 28.2 The fix

`ClientResourceParityInspectionCommand` now registers exclusively under a new, entirely distinct root
literal, `totalitydebug` (exposed as the package-private constant `ROOT_LITERAL`), which no server
command begins with — so the client dispatcher has no reason to ever intercept a server-side
`/totality` command again. The command's full path is now **`/totalitydebug resource parity`**. The
`totality` literal is **not** retained as a client-side alias in any form — per the task's explicit
instruction, since the defect is the mere existence of a client registration under that root, not this
particular subtree.

**Everything else is explicitly unchanged**: `registerIfDevelopmentEnvironment()`'s development-only
gating (`FabricLoader.getInstance().isDevelopmentEnvironment()`) is untouched; no server command-
registration file (`TotalityCommands.java`, `Totality.java`, `ModEvents.java`) was modified; the report
gathering (`gatherReport`/`gatherNative`/`gatherShadow`), the pure input conversion, the assembler, the
formatting/wording, the seven-Resource ordering, the exact-seven-line/eight-chat-line invariant (§6,
§11), and the read-only/no-mutation guarantees are byte-for-byte unchanged by this pass — only the
registered root literal (one changed string constant and its one call site) and one explanatory
comment in `TotalityClient.java` were touched.

### 28.3 Manual-validation record (§16.0) — retained here as the authoritative source

See §16.0 for the full record. Summary: old path registered and executed; `/fcc help` listed it; report
showed one header + seven Resource lines; native lines showed `comparison=native-only`; Mana/Stamina/
Rage showed initial `EXACT_MATCH` (100/100, 110/110, 1/2); spending kept all three synchronized
(86/100, 92/110, 0/2, all `EXACT_MATCH`); spell-slot spending was blocked (could not become Wizard,
itself a symptom of the command-root defect, since the class-change command is also under `/totality`).
No value, log line, production-gating result, or dedicated-server result beyond what was actually
supplied is claimed anywhere in this report.

### 28.4 Tests added

`ClientResourceParityInspectionCommandTest` gained 4 new tests (§11), all source/constant-based
(the task's own instruction: live command-tree coexistence cannot be claimed proven by an automated
test):
- `rootLiteralConstantIsTotalityDebugNotTotality` — `ClientResourceParityInspectionCommand.ROOT_LITERAL`
  equals exactly `"totalitydebug"`.
- `registrationMethodRegistersTheTotalityDebugRootLiteral` — the registration method's body contains
  `ClientCommands.literal(ROOT_LITERAL)`.
- `productionCommandNeverRegistersTheSharedTotalityRootLiteral` — the production source never contains
  `ClientCommands.literal("totality")` or any `.literal("totality")` call, under any name.
- `commandPathIsExactlyTotalityDebugResourceParity` — the fully-qualified path string equals exactly
  `"totalitydebug resource parity"`.

No existing test was weakened or removed. All 8 previously-existing development-gating/server-
independence tests (`classIsAnnotatedClientOnly`, `registrationMethodGuardsOnFabricLoaderDevelopmentEnvironmentBeforeRegistering`,
`noPermissionOrOperatorCheckSubstitutesForDevelopmentGating`, `commandClassIsNotReferencedFromServerInitialization`,
`serverCommandTreeFileWasNotModifiedByThisSlice`, `totalityClientRegistersTheCommand`,
`productionFilesIntroduceNoForbiddenDependency`, and the two `gatherReport`-visibility tests) continue
to pass unchanged, proving the command-root fix did not regress any prior correction's guarantees. All
report-behavior tests (§11's other five files, 64 tests) are untouched and unchanged by this pass,
proving report gathering/formatting/ordering/boundedness/read-only behavior are unaffected by the
root-literal change.

### 28.5 Automated results after this third correction pass

- `compileJava`/`compileTestJava`: `BUILD SUCCESSFUL`, no warnings.
- `test --rerun`: `BUILD SUCCESSFUL` — **879 tests, 0 failed, 0 errors, 0 skipped** (875 pre-third-
  correction + 4 new, all in `ClientResourceParityInspectionCommandTest`).
- `runDatagen`: `BUILD SUCCESSFUL`, `349 → 349`, `written: 0`.
- `build`: `BUILD SUCCESSFUL`, only the pre-existing Gradle/Loom deprecation notice.
- `git diff --check -- src/main/java src/test/java`: exit 0, no output.
- No existing test's expected behavior was weakened or removed by this pass.

### 28.6 What remains unchanged, and what remains pending

Unchanged: §17 (Rage dimension-transfer), §18 (`FormulaResolver`), §19 (Rage-maximum sync), §20
(spell-slot remaining semantics), §21 (Phase 3C readiness matrix) — none of their underlying evidence
changed. No Resource, ancestry, Provisioner, Rest, or combat bug was fixed. No consumer migration
occurred. No Phase 3C work occurred. Nothing was staged, committed, or pushed.

**Pending, as of §28**: manual validation remained incomplete. The command-root defect was fixed in
source, but its fix had not itself been manually re-verified in a running client — that was checklist
item N (§16.1), entirely unperformed as of §28. Phase 3B-3 was not to be considered closed until item N
was completed.

**Superseded by §29 below** — the user has since performed exactly that re-verification, completing
all ten sub-items of checklist item N, including the previously-blocked spell-slot test. §29 records
the complete manual-validation results, the corresponding readiness-matrix updates, and the final
Phase 3B-3 readiness decision.

---

# MANUAL VALIDATION COMPLETION — 2026-07-29

## 29. Manual validation completed: final results and Phase 3B-3 readiness decision

### 29.1 Confirmed manual results (checklist item N, all 10 sub-items — performed by the user)

All values below are recorded exactly as supplied; nothing beyond this was captured or is claimed.

**1. Development command registration**
- `/fcc help` listed `/totalitydebug resource parity`.
- `/totalitydebug resource parity` executed successfully.

**2. Command-root correction** — corrected (closure pass) to record each piece of evidence distinctly,
never one inferred from another:
- `/totality wallet` executes normally.
- Existing server-side `/totality` branches appear through normal autocomplete — **tested directly**,
  not inferred from successful execution.
- `/totality resource parity` is no longer handled as the client diagnostic command.
- `/totalitydebug resource parity` remains the correct, working, development-only client command.
- Existing representative `/totality` commands, including the ancestry command (ancestry reset) and
  the class command (class reset), execute normally, without `ClientCommandInternals` interception. The
  class command is distinct evidence from `/totality wallet` — it is never described as
  "wallet-equivalent" anywhere in this report.

**3. Report structure**
- One fixed `[Resource Parity]` header.
- Exactly seven Resource messages.
- Chat's own visual line-wrapping of a message was confirmed to not represent additional report
  entries — i.e. a long single message wrapping across multiple visual lines in the chat box is a
  rendering detail of Minecraft's chat widget, not a sign that the report produced more than seven
  Resource entries. (The automated `CHAT_LINE_COUNT = 8` invariant, §6/§11, was already proven at the
  string level — this manual observation confirms it reads correctly as such in the actual chat UI.)

**4. Native Resources**
- Health, Food, and Breath reported trusted native values.
- Each used `comparison=native-only`.
- No fabricated parity classification appeared.

**5. Initial exact-match examples**
- Mana `100/100` generic and legacy.
- Stamina `110/110` generic and legacy.
- Rage `1/2` generic and legacy.
- All reported `EXACT_MATCH`.

**6. Spend synchronization**
- Mana `86/100` generic and legacy, `EXACT_MATCH`.
- Stamina `92/110` generic and legacy, `EXACT_MATCH`.
- Rage `0/2` generic and legacy, `EXACT_MATCH`.

**7. Spell-slot semantics**
- After spending one level-1 slot: generic level 1 = `1/2` remaining; legacy level 1 = `1/2` remaining;
  parity classification = `EXACT_MATCH`.
- The command and the spell radial presentation agreed that the values represented **remaining**
  slots, not used slots — no remaining/used inversion (closing §20's concern and §21's item 4 blocker).

**8. Dedicated server**
- Server reached: `Done (0.332s)! For help, type "help"`.
- No client-command or parity-report classloading exception occurred.
- **Unrelated, pre-existing** known Provisioner and OffhandAttack verification failures remained on
  this same run. These are explicitly documented here as **unrelated to Phase 3B-3** — they are not
  produced by, and were not investigated or fixed by, any Resource-API parity-inspection work in this
  phase. No Provisioner, OffhandAttack, ancestry, Rest, or combat file was opened for editing in this
  session.

**9. Production/non-development gating**
- The normal compiled Totality JAR was placed in a regular Fabric instance/modpack.
- `/totalitydebug resource parity` did not appear.
- Development-only registration therefore passed.

**10. Other checklist exercises**
- Per the user's own prior statement (§16.0), all practical checklist exercises other than the spell-
  slot test had already been exercised before the spell-slot test was completed in this round.
- This report preserves exactly the captured evidence above (items 1-9 plus the spell-slot result) and
  does **not** invent uncaptured numeric values or log lines for any other checklist letter (B, C, E,
  F, G, I, J, L) beyond what is already recorded in §16.0/§16.1's own item-by-item notes.

### 29.2 Readiness-matrix updates resulting from this evidence

See the corrected §21 table directly. Summary: Breath (native path confirmed), spell-radial (the
previously-blocking item now closed), and Stamina (spend/sync confirmed, no open gap) move to
**READY** — **three of six consumers**. Rage's two consumers and Mana HUD remain **BLOCKED BY SPECIFIC
GAP** — **three of six consumers**. For Rage specifically, the blocker is the Rage-maximum
synchronization/fallback concern (§19), not the dimension-transfer bug (§17) — §17 is documented as
migration motivation (the generic side stays correct throughout a dimension transfer, so migrating
would plausibly stop the stale-legacy-value symptom rather than cause it). Neither the dimension-
transfer bug, the Rage-maximum gap, nor the `FormulaResolver` gap (§18, Mana's blocker) were
re-exercised this session — no new evidence was supplied for the Nether-transfer sequence or the
FormulaResolver/Rage-maximum routes specifically — and none were fixed, per explicit instruction.
Ordinary Mana and Rage spend synchronization being confirmed (§29.1 item 6) does not touch either
Mana's or Rage's actual blocking gap, since both are specific to a different trigger (a Rage-maximum
change; a specific rune-casting call path for Mana) that this round of manual validation did not
exercise.

### 29.3 Explicit non-fixes — confirmed still open, still untouched

Per the task's explicit instruction, none of the following were fixed, investigated for a fix, or had
their underlying source opened for editing at any point in this session:
- Rage dimension-transfer legacy mirror bug (§17) — documented as migration motivation, not fixed,
  and not itself the reason Rage's consumers remain blocked (see §19, §21, §29.2/§29.4).
- `FormulaResolver` Mana immediate legacy-sync/reachability gap (§18) — Mana's actual blocker.
- Rage existing-pool maximum synchronization gap (§19) — Rage's actual blocker.
- Provisioner issues (observed as an unrelated dedicated-server verification failure, item 8 above).
- Offhand Stamina/`OffhandAttack` issues (same).
- Ancestry dimension issues.
- Rest passenger warning.

All seven remain exactly as previously documented, carried forward for separate Phase 3C planning or
independent correction — not as part of this phase's closure. No Resource behavior changed and no
migration began in the course of clarifying this reasoning.

### 29.4 Phase 3B-3 status — explicit decision (closure pass)

- **Phase 3B-3 implementation: COMPLETE.** All three correction passes (§26, §27, §28) plus the manual-
  validation round (§29.1) are reflected in the current source; no further architectural defect is
  open.
- **Required manual validation: COMPLETE** for the post-correction checklist (item N, all 10 sub-items,
  §29.1) and the core functional/diagnostic checks (report structure, native Resources, shadow-parity
  exact-match, spend synchronization for Mana/Stamina/Rage, and the previously-blocking spell-slot
  remaining-value semantic check, plus — this closure pass — distinct, directly-confirmed evidence for
  `/totality wallet`, the ancestry command, the class command, and autocomplete availability, §16 N.4/
  N.5). **Not exercised, and not required to be**: the FormulaResolver/Rage-maximum live routes (no
  safe route was ever identified, and none was added solely for testing, per explicit instruction) and
  the live Nether dimension-transfer/respawn sequence (no new evidence was supplied this round; the
  historical Phase 3B-2B confirmation of that specific bug stands unchanged). **Nothing in this closure
  pass claims the FormulaResolver or Rage-maximum live routes were exercised.**
- **Final source review: PASSED.** All 12 production/test files were confirmed byte-identical to the
  previously-approved command-root correction bundle (§25); no source-code correction was required or
  performed in this closure pass.
- **Documentation correction: APPLIED** in this closure pass — checklist N.4/N.5 evidence recorded with
  distinct, non-inferred confirmation; the readiness count corrected to three-of-six/three-of-six; the
  Rage dimension-transfer-vs-maximum-gap reasoning made internally consistent across §17, §19, §21, and
  this section; bundle metadata corrected to distinguish tracked-modified from untracked files (§33).
- **Ready to commit and push: YES, after final bundle verification** (§33) — this report does not
  itself perform the commit or push; per the task's explicit instruction, neither was performed in
  this session.
- **Known gaps remain, explicitly documented, not fixed, for Phase 3C planning or separate
  correction**:
  - The `FormulaResolver` Mana immediate legacy-sync/reachability concern (§18).
  - The Rage existing-pool maximum synchronization concern (§19) — Rage's actual presentation-
    readiness blocker.
  - The Rage dimension-transfer legacy mirror bug (§17) — documented as migration *motivation*, not
    the current migration blocker.
  - Provisioner, OffhandAttack, ancestry-dimension, and Rest-passenger-warning issues are confirmed
    **unrelated** to Phase 3B-3 and are not this phase's responsibility to resolve.
- **Phase 3C: NOT STARTED.** This report's readiness-matrix update (§21, §29.2) narrows which
  consumers could reasonably migrate first (three of six: Breath, spell-radial, Stamina), but the
  decision to begin Phase 3C migration work is explicitly out of scope for this session and remains
  for a separate, future phase.

### 29.5 No unrelated fix occurred

This session (the manual-validation finalization pass) made **no source-code change at all** —
`compileJava`/`compileTestJava` reported every task `UP-TO-DATE` before a forced `test --rerun` (§30)
confirmed the suite fresh. No production or test file was opened for editing. Only this report and
that session's own review bundle, `TOTALITY_RESOURCE_API_PHASE_3B3_FINAL_VALIDATION_REVIEW_BUNDLE.zip`,
were produced. **That bundle is now historical**: two further passes (§32's closure-documentation
correction and its regenerated bundle) have superseded it. The current, corrected closure bundle is
recorded in §33 — that is the bundle to externally verify before commit/push, not the one this
section's own session produced.

---

## 30. Fresh automated validation (manual-validation finalization pass)

Run fresh, exactly as required, even though no source file changed this session:

- **`compileJava`**: `BUILD SUCCESSFUL` (`UP-TO-DATE` — no source change since the third correction
  pass).
- **`compileTestJava`**: `BUILD SUCCESSFUL` (`UP-TO-DATE`).
- **`test --rerun`** (forced fresh run, not relying on the up-to-date cache): `BUILD SUCCESSFUL`.
  Summed across every `build/test-results/test/*.xml`: **879 tests, 0 failed, 0 errors, 0 skipped** —
  unchanged from the third correction pass, as expected for a documentation-only session.
- **`runDatagen`**: `BUILD SUCCESSFUL`. `total files: 349, old count: 349, new count: 349, removed
  stale: 0, written: 0` — matching the expected `349 → 349`, `0 written`.
- **`build`**: `BUILD SUCCESSFUL` (all tasks `UP-TO-DATE`, consistent with no source change).
- **`git diff --check -- src/main/java src/test/java`**: exit code `0`, no output.
- No compiler, Gradle, Loom, test, datagen, or build warning of any kind was observed.

---

# CLOSURE PASS — DOCUMENTATION AND BUNDLE-METADATA CORRECTION — 2026-07-29

## 31. Fresh automated validation (closure pass)

Run fresh, exactly as required, even though no source file changed this session — this session
corrected only this report's own wording/reasoning/evidence-recording and the final review bundle's
metadata:

- **`compileJava`**: `BUILD SUCCESSFUL` (`UP-TO-DATE` — no source change since the manual-validation-
  completion pass).
- **`compileTestJava`**: `BUILD SUCCESSFUL` (`UP-TO-DATE`).
- **`test --rerun`** (forced fresh run, not relying on the up-to-date cache): `BUILD SUCCESSFUL`.
  Summed across every `build/test-results/test/*.xml`: **879 tests, 0 failed, 0 errors, 0 skipped** —
  unchanged, as expected for a documentation-only session.
- **`runDatagen`**: `BUILD SUCCESSFUL`. `total files: 349, old count: 349, new count: 349, removed
  stale: 0, written: 0` — matching the expected `349 → 349`, `0 written`.
- **`build`**: `BUILD SUCCESSFUL` (all tasks `UP-TO-DATE`).
- **`git diff --check -- src/main/java src/test/java`**: exit code `0`, no output.
- No compiler, Gradle, Loom, test, datagen, or build warning of any kind was observed.

## 32. Closure pass: documentation correction and bundle-metadata correction

### 32.1 Final external-review result confirmed

Before making any change this session, all 12 production/test files from
`TOTALITY_RESOURCE_API_PHASE_3B3_FINAL_VALIDATION_REVIEW_BUNDLE.zip` were re-extracted and diffed
byte-for-byte against the current working tree — all 12 were identical. **No source-code correction
was required or performed.** Only this report and the final closure review bundle were produced.

### 32.2 Newly confirmed manual results recorded (checklist N.4/N.5)

The user directly confirmed, as five separate, non-inferred pieces of evidence:
1. `/totality wallet` executes normally.
2. Existing server-side `/totality` branches appear through normal autocomplete — tested directly,
   not inferred from successful command execution.
3. `/totality resource parity` is no longer handled as the client diagnostic command.
4. `/totalitydebug resource parity` remains the correct, working, development-only client command.
5. Existing representative `/totality` commands, including the ancestry command and the class
   command, execute normally without `ClientCommandInternals` interception.

§16 (checklist item N.4/N.5) and §29.1 (item 2) were corrected to record all five pieces of evidence
distinctly. Every "wallet-equivalent" description of the class-reset command was removed — the wallet
command and the class command are recorded as distinct evidence, and successful execution is never
used to imply autocomplete availability (or vice versa) anywhere in this report.

### 32.3 Readiness count corrected

§21's summary prose previously read "four of six consumers... now have direct manual evidence," which
did not match its own table (3 `READY` + 3 `BLOCKED BY SPECIFIC GAP` = 6). Corrected to state plainly:
**three of six consumers are READY** (Breath, spell-radial, Stamina); **three of six remain BLOCKED BY
SPECIFIC GAP** (Rage secondary HUD, Class-tab Rage, Mana HUD). §29.2 was corrected to match. No other
"four of six" framing exists anywhere in this report (confirmed by full-file search).

### 32.4 Rage reasoning made internally consistent

§17, §19, §21, and §29.2/§29.4 now uniformly state: the confirmed dimension-transfer behavior (generic
Rage stays correct; legacy Rage becomes incorrect; the legacy-backed Rage presentation disappears;
death/respawn restores the legacy mirror) is **documented as migration motivation** — evidence that
migrating Rage's presentation source to the generic view would plausibly *prevent* the stale-value
symptom, not evidence to block that migration. The **actual remaining blocker** for Rage presentation
readiness is the separate, unresolved Rage existing-pool maximum synchronization/fallback concern
(§19). Neither issue was fixed, and Rage's presentation source/behavior was not changed, in the course
of making this reasoning consistent.

### 32.5 Overall readiness wording corrected

Stated exactly, in §29.4: Phase 3B-3 implementation is **COMPLETE**; required manual validation is
**COMPLETE**; final source review **PASSED** (§32.1); documentation correction **APPLIED** (this
section); the phase is **ready to commit and push after final bundle verification** (§33); Phase 3C
is **NOT STARTED**. Known gaps remain, explicitly documented and not fixed: the `FormulaResolver`
Mana immediate legacy-sync/reachability concern (§18); the Rage existing-pool maximum synchronization
concern (§19); and the Rage dimension-transfer legacy mirror bug (§17), documented as migration
motivation rather than the current migration blocker. Neither the `FormulaResolver` nor the Rage-
maximum live routes are claimed exercised anywhere in this report.

### 32.6 Bundle metadata correction

The prior final-validation bundle's `diffstat.txt` labeled `src/main/java/zcylas/totality/
TotalityClient.java` as "current, untracked." This was inaccurate: `git status --short` shows this
file as ` M ` (tracked, modified relative to `HEAD`) — it has been a tracked, modified file since the
first correction pass (§26), which added the command's registration call inside it. The closure
bundle's metadata (§33) generates file-status labels directly from `git status --short`/`git ls-files`
output rather than a blanket "current, untracked" label applied to every file in the changed-file
list, so tracked-modified, untracked, and excluded-unrelated files are now distinguished correctly.

---

# FINAL INTERNAL-REFERENCE AND CLOSURE-BUNDLE CORRECTION — 2026-07-29

## 33. Closure review bundle and final stop point

### 33.1 Internal-reference correction (this pass)

The previous closure pass (§32) left two dangling/misdirected section references, found by external
review:
- §29.4 and §29.5 referred to "§31" for the review bundle, but §31 had since become "Fresh automated
  validation (closure pass)" — a different section. Both references are now corrected to point here,
  to §33.
- §32.5 and §32.6 already referred to "§33" prospectively, anticipating this exact section; that
  section did not yet exist. It now does.
- §29.5 additionally described "the final review bundle" without naming it; it now explicitly names
  `TOTALITY_RESOURCE_API_PHASE_3B3_FINAL_VALIDATION_REVIEW_BUNDLE.zip`, states plainly that it is
  **historical** (superseded by the closure pass and its regenerated bundle), and points to this
  section for the bundle that should actually be externally verified before commit/push.

A full-file search for every `§31`/`§33` occurrence was performed as part of this pass; each now
points to its intended current section (confirmed by direct grep, not by assumption).

**No change was made to the readiness matrix (§21), the Rage reasoning (§17/§19/§21/§29), the recorded
manual evidence (§16/§29.1/§32.2), or the known-gap conclusions (§18/§19/§29.3/§29.4/§32.5) — this pass
is a pure internal-reference and bundle-regeneration correction.**

### 33.2 Closure review bundle

**Path:** `Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B3_CLOSURE_REVIEW_BUNDLE.zip`
(recreated in place by this pass, superseding the prior copy that had the dangling §31 references
baked into its captured report copy).

Verified:
- **29 archive entries.**
- **28 checksum-manifest entries** (`checksums.sha256` does not checksum itself — the same convention
  every bundle in this phase has used).
- **All 28 checksums passed** (`sha256sum -c --quiet`).
- **Archive integrity passed** (`zipfile.testzip()` returned `None` — no corruption).
- **Manifests matched** — `manifest_all.txt`/`manifest_production.txt`/`manifest_test.txt`/
  `manifest_audit.txt` list exactly the 13 files present under `files/`.
- **Documentation-only impact relative to the final-validation bundle
  (`TOTALITY_RESOURCE_API_PHASE_3B3_FINAL_VALIDATION_REVIEW_BUNDLE.zip`): exactly the implementation
  report changed** — `patches/documentation_only_impact.patch` contains exactly one `diff --git` entry,
  for the report; `manifest_documentation_only_impact.txt` lists the other 12 production/test files as
  unchanged.
- **All 12 production/test files were unchanged** relative to both the final-validation bundle and the
  command-root correction bundle before it (re-confirmed by direct byte-for-byte diff at the start of
  this pass).
- **`TotalityClient.java` was correctly identified as tracked-modified** — `diffstat.txt` and
  `manifest_production.txt` both label it via its actual `git status --short` token (` M`), not
  "current, untracked."
- **No previous review ZIP was included.**
- **No `build.gradle`.**
- **No `log4j-dev.xml`.**
- **No generated JSON.**
- **No screenshots.**
- **No logs/cache.**
- **No `/Inspiration Mods`.**
- **No temporary bundle directory remained** after regeneration (verified removed).

### 33.3 Final stop point

- **Phase 3B-3 implementation: COMPLETE.**
- **Required manual validation: COMPLETE** (checklist item N, all 10 sub-items, §16/§29.1, including
  the closure pass's distinct N.4/N.5 evidence, §32.2).
- **Final source review: PASSED** — all 12 production/test files byte-identical across the command-
  root correction, final-validation, and closure bundles; re-confirmed again at the start of this pass.
- **Documentation closure: COMPLETE** — this pass closes the last known documentation defect (dangling
  §31/§33 references); no further documentation correction is pending.
- **879/879 tests, 0 failed, 0 errors, 0 skipped** (fresh `test --rerun`, this pass).
- **`runDatagen`: `349 → 349`, `0 written`.**
- **`build`: `BUILD SUCCESSFUL`.**
- **`git diff --check -- src/main/java src/test/java`: exit `0`, no output.**
- **Nothing staged, nothing committed, nothing pushed** (verified immediately before and after this
  pass).
- **Phase 3C: NOT STARTED.**
- **Ready to commit and push after this corrected bundle (§33.2) is externally verified.**
