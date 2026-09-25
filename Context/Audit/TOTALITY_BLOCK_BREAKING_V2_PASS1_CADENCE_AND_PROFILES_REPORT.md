# TOTALITY — Block Breaking V2, Implementation Pass 1
## Cadence Correction and Block/Material Profile Foundation

**Date:** 2026-09-25 · **Target:** Minecraft 26.2 / Fabric / Java 25 · **Branch/HEAD:** `master` / `7213407` (unchanged; nothing committed, staged or pushed)

**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_BLOCK_BREAKING_V2_PASS1_REVIEW.zip`

---

## 0. Authority and scope

- **Master reference unavailable.** `STEFAN_TOTALITY_MASTER_REFERENCE` (§26L, §26M) is not on disk; a filesystem search found no copy (same finding as the readiness audit).
  - Behaviour was implemented from the accepted decisions in the Pass 1 task brief and from `TOTALITY_BLOCK_BREAKING_V2_READINESS_AUDIT.md`.
  - Where the brief left a decision open, it is listed in §6 rather than invented.
- **The repository was inspected directly before editing.** The readiness audit was treated as historical evidence.
- **Pre-existing uncommitted work.** 7 of the mining files touched here already carried uncommitted, pre-existing edits.
  - `src/` was snapshotted before any edit, and the task-only patch is computed against that snapshot, not against `HEAD`.
  - Applying the patch to the snapshot reproduces the current `src/` exactly (verified with `diff -r`). So this task changed exactly the 22 files listed in §7 and nothing else in `src/`.
- **Deliberately not done:**
  - the full vanilla block dataset;
  - Ruby/Vibranium/fictional-material rebalance;
  - Veinminer, Heat Vision, Ground Slam, Break/Smelt runes and other destructive systems;
  - Tooltip V2 visual changes;
  - Force Tolerance;
  - conventional tool balance;
  - the next tool-completion pass.

---

## 1. Implemented behaviour

### 1.1 Stale-target cadence correction

**Defect (confirmed in code before the fix):** the swing's windup and recovery were computed once, at swing start, from the block under the crosshair. The contact frame struck the re-raycast block but kept the stale recovery.

**Correction** (`PlayerMiningManager.contact → contactNow(…, onActualTarget) → correctRecovery`):
- **The windup and contact moment are untouched.** The correction runs *at* contact.
- **The actual block** comes from the existing server re-raycast (`pickBlock`). The correction runs only after the existing gates pass:
  1. the tool-swap check (`sameSource`);
  2. the miss check;
  3. the ownership/permission check (`ownsMining`).

  It runs **before** `strike`, so it sees the block as it was struck, even when that strike breaks it.
- **The cycle is re-derived from the actual target's effective Mining Speed.** It uses the same functions as swing start (`ResolvedMiningSource`, `PlayerMiningPower.effectiveMiningSpeed`, including target effectiveness).
  - The fractional carry is **rewound** to its value before this swing (`carryBeforeSwing`), so long-run averages stay exact across retargets.
  - `recovery = max(MIN_RECOVERY_TICKS, actualCycle − windUpSpent)`: the minimum-recovery floor is preserved.
- **One strike per swing:** the correction only changes the remaining recovery.
- **A miss, a voided swing (tool swap) or a non-owned target keeps the scheduled cycle.**
- **Power swings are excluded:** `s.swingIsPower ? null : …`, so the charge/release semantics are untouched.
- **Client animation sync:** the new S2C `MiningRecoveryPayload(recoveryTicks)` is sent only when the recovery actually changed. `ClientMiningController` applies it via `MiningHandAnimation.correctRecovery`, which only affects a NORMAL swing in flight.
- **Refactor:** the profiled-cycle arithmetic was extracted verbatim into the pure `MiningCadence`. A JUnit test proves it identical to the previous inline code over 12 speeds × 4 baselines × 40 cycles, including the carry.
- **Legacy (unprofiled: Gold, bare hands) swings get the same correction** through their existing cadence-ratio cycle (`MiningTuning.cycleTicks`). See decision D3.

| Example (Netherite Pickaxe, 6-tick swing) | Before | After |
|---|---|---|
| Scheduled on Grass (16-tick cycle), struck Stone | contact gap 13 | contact gap **5** (swing = 8-tick Stone cycle) |
| Scheduled on Stone (8-tick cycle), struck Grass | contact gap 11 | contact gap **19** (swing = 16-tick Grass cycle) |

### 1.2 Layered Block/Material Profiles

**New types:**
- `BlockProfile` (pure data): one layer, with every field nullable.
  - Fields: `material`, `form`, `classification`, `maxDurability`, `tools`, `requiredTier`, `ownership`.
  - Nested types: `Material`, `Form` (`FULL_BLOCK`), `Classification` (ORDINARY / SPECIAL / UNBREAKABLE / NOT_APPLICABLE), `Ownership`, `Tool`, `ToolAffinity` (explicit `NEUTRAL` versus `NONE`), `Resolved` and `Layer` (provenance).
- `BlockProfileResolver<S,K>`: pure and generic, so the precedence rules are JUnit-tested without a Minecraft bootstrap.
- `BlockProfiles`: the resolver bound to `BlockState`/`Block`, plus the compatibility fallback and the geometry of assembly owners.

**Resolution:** each field resolves independently, to its first non-null value:
1. exact block/state override (the first registered matching predicate for that block);
2. exact block profile;
3. authored material + form profile;
4. material-family default;
5. compatibility fallback. It reproduces the prior behaviour exactly:
   - hardness × 100/1.5;
   - `MiningTier.fallbackRequiredTier` (vanilla-derived);
   - the vanilla `mineable/*` tags;
   - classification by hardness: < 0 UNBREAKABLE, 0 SPECIAL, air/fluid/bubble column NOT_APPLICABLE;
   - ownership by block class.

**Material and form** come from, in order: the override, the exact profile, the exact material assignment, then the first matching tag assignment. Exact entries always beat broad tags and families. Form values are authored only: there is no geometric formula (a JUnit test asserts that an unauthored slab does *not* derive from its full block).

**Integration: one authority, no duplicate pipelines.**
- `BlockDurability` is now a thin view over profiles. `register(Block, Definition)` is kept as shorthand for an exact block profile; existing call sites and pinned source tests rely on it.
- `BlockDurabilityDefinitions`:
  - the six exact 100s are unchanged;
  - the `#logs_that_burn` family became material `totality:overworld_log` (full-block form) with a family default of 100;
  - the values are identical.
- `TargetEffectiveness.resolve` reads the profile's `ToolAffinity`. The `mineable/*` tag rule moved into `TargetEffectiveness.fallbackTools` as the compatibility fallback, so resolution for every unauthored block is unchanged. A tool-neutral profile penalises no tool.
- `BlockBreaking.applyImpact` resolves the Integrity **owner** and uses the owner's profile. Only `ORDINARY` blocks are struck; SPECIAL, UNBREAKABLE and NOT_APPLICABLE blocks return INVALID.
- `MiningOwnership.owns` (shared by client and server) also requires classification ORDINARY or UNBREAKABLE:
  - an explicit SPECIAL (or NOT_APPLICABLE) block with hardness stays with vanilla;
  - an explicit UNBREAKABLE block with hardness is owned, so no vanilla progress can break it.
- Tooltip V2 `BlockDurabilityContributor` reads the same profile: max, tier and effective tools. The rows, icons and colours are unchanged; the client game-test screenshot shows Stone at 100 / Tier 1 / Pickaxe.
- **Block Durability stays separate:** it is only the mining Integrity budget. No Structural Strength or other material property was added or merged into it.

### 1.3 Integrity ownership and transitions (`BlockDamageStorage`)

| Rule | Implementation |
|---|---|
| Independent ordinary positions | `Ownership.POSITION` (default) |
| Doors share one owner | `DOOR_LOWER_HALF`: the upper half resolves to the lower half, if it really is the matching lower half |
| Beds share one owner | `BED_HEAD`: the foot resolves to the head |
| Pistons share one owner | `PISTON_BASE`: the head of an *extended* piston resolves to its base (facing and extension checked) |
| Double chest halves are independent | the chest falls to `POSITION` |
| Cracks | the owner record's crack is shown on both positions (partner id = `crackId + 1`; ids are issued in steps of 2) |
| Ordinary non-structural state change | same block id keeps the record (door open, piston retract) |
| Authored physical transformation | `BlockProfiles.transformation(from, to)`: the record re-identifies and rescales by percentage |
| Profile maximum changed | percentage rescale on read |
| Unrelated replacement | record deleted |
| Destruction/removal | existing strike-break removal, plus sweep/lazy read on air or a mismatch; clears both crack ids |
| Old saves: record on a non-owner half | folded into the owner on read or sweep; the more damaged record wins (raw integrity and last-impact are kept together so lazy recovery isn't double-counted) |

- **Migration formula** (`IntegrityReconciliation.rescale`): `round(newMax × oldIntegrity / oldMax)`, clamped to `[min(1, newMax), newMax]`.
  - A result at full deletes the record.
  - An invalid or zero old maximum, a non-finite integrity, or an invalid new maximum deletes the record: the block reads as full, and nothing crashes.
- **Laziness:**
  - All position-dependent reconciliation happens when a record is read (`get`) or swept (`validate`). There is no world rewrite.
  - One position-free pass (`reconcileRecords`) runs per level at `SERVER_STARTED` and touches only the `totality:block_damage` map. It drops records whose block id is no longer registered, whose saved values are corrupt, or whose block is non-ORDINARY in **every** state.

### 1.4 Classification boundary

- **Ordinary → SPECIAL/UNBREAKABLE/NOT_APPLICABLE:** the record is deleted, on read, on sweep, or by the start-up pass.
- **SPECIAL/UNBREAKABLE → ordinary:** no record survives, so the block starts at full.
- **No dormant finite damage is retained.**
- **SPECIAL keeps vanilla authoritative:** it is not owned by Totality mining, and hardness-0 blocks stay vanilla instant-break as before.

---

## 2. Test evidence

### 2.1 JUnit (`./gradlew test`)
- **Baseline before the task:** 1,917 tests, 0 failures.
- **After:** **1,952 tests, 0 failures, 0 errors.** 35 are new, and every pre-existing test passes, including the pinned source-regression suites (`TooltipApiFoundationSourceRegressionTest` 106/106, `LiveWorldVerificationGateSourceRegressionTest`).

| New test class | Tests | Covers |
|---|---|---|
| `BlockProfileResolverTest` | 15 | full precedence, field-wise deferral, exact vs tag, first tag wins, material/form reassignment, authored-not-derived forms, classification (fallback and explicit, not HP), tool-neutral vs none, tier fallback, ownership data, transformations |
| `IntegrityReconciliationTest` | 11 | state change keeps, formula and rounding, clamps, rounding-to-full deletes, authored transformation, unrelated replacement, classification boundary (all three), no dormant damage, invalid old/new max, legacy fold policy |
| `MiningCadenceTest` | 6 | extraction identical to the old code, slow→fast, fast→slow, unchanged-target identity, minimum recovery, fractional carry exact across 60 alternating retargets |
| `StaleTargetCadenceSourceRegressionTest` | 3 | Power excluded; order of swap/miss/ownership gates → notify → one strike; carry rewind, spent windup and payload |

### 2.2 Live world: `runVerificationServer`
- Runs in the disposable `build/verification-run`: the world is deleted before launch, and the run uses the existing `-Dtotality.liveWorldVerification=true` gate. No new gate was added.
- The new live checks are a nested `BlockProfileChecks` section of the already-gated `MiningVerification`, inside its existing snapshot/restore of the damage map. They restore every block they touch and remove their records.
  - A first draft as a separate `BlockProfileVerification` class was rejected by the repo's live-gate guard test, because it had no gated `register()`. It was folded into the gated suite rather than renamed around the guard.
- **Final run (verbose): every suite passed.** MiningVerification **279/279**, including all **30 new named checks** (3 stale-target cadence + 27 profile/ownership/transition/classification). DurabilityRegression 14/14, PowerAttack 12/12, and all other suites passed as well.
- The pre-existing sustained-cadence checks still pass: 10/8/2-tick spacing, and the 2.4/s and 2.25/s long-run averages.
- The two Provisioner `ERROR` log lines come from that suite's own negative-path self-tests (71/71 passed).

### 2.3 Client: `runClientGameTest`
- Runs in the disposable `build/run/clientGameTest`.
- **Results:** TooltipV2Pass1 162 PASS / 0 FAIL; TooltipV2Enchantments 47 PASS / 0 FAIL; the client dev self-tests passed.
- The Stone properties screenshot is included in the bundle.
- This ran before the live checks were moved into `MiningVerification`. That move touched only server-side, gated verification code, which the client run does not load.

### 2.4 Not run
- **No manual in-game session.** The *visual* recovery correction on a real client (`MiningRecoveryPayload` → `correctRecovery`) is compiled and registered, but no automated client test drives mining. It is on the manual checklist (§5).
- **Nothing was run against a development or personal save.** `run/` was not launched.

---

## 3. Behaviour changes a reviewer should know

1. **Contact-frame recovery now follows the actual target, including effective-speed state at contact.** Haste, Fatigue, water and airborne are read at contact, so a jump during windup affects that swing's recovery.
2. **Hardness-0 (SPECIAL) BlockItems no longer show "Block Durability 0"** in Tooltip V2. The profile says they hold no Integrity; the audit had called this row a quirk. Ordinary blocks look identical.
3. **`BlockBreaking.applyImpact` on a SPECIAL block now returns INVALID.** Previously a non-player source could "break" a hardness-0 block at max 0. There is no production caller; players never reach it because of ownership.
4. **Integrity records for doors, beds and extended pistons now live on the owner position**, and older per-half records are folded into it lazily.

---

## 4. Deferred work
- The complete vanilla block dataset: material families and form values for the 981 fallback blocks.
- **Production physical transformations.** The mechanism exists and is tested, but none is authored (see D1).
- Material identity for the six exact-profile blocks (Stone, Cobblestone, Diorite, Andesite, Dirt, Grass Block). They remain exact blocks without a family.
- Hoe effectiveness (not wired, unchanged). A Tooltip V2 row for tool-neutral blocks (a presentation decision).
- Tool completion (Gold, hoes, swords, shears), authored tiers, Force Tolerance, and destructive systems outside the strike pipeline.

---

## 5. Unresolved issues and residual risks
- **Residual dormant-damage edge.** A classification that differs *by state* and flips twice while the chunk stays unloaded. The start-up pass removes records only when the block is non-ORDINARY in every state; anything narrower is reconciled on the next read or sweep. There are no authored state-specific classifications today.
- **Stale partner crack after a retract.** When an extended piston retracts, a partner crack sent to the old head position expires client-side (≤ 400 ticks) instead of being cleared immediately. It is invisible, because that position is air or moving piston.
- **Sticky versus normal piston type** is not cross-checked when pairing a head to its base; facing and extension are.
- **Rescale floor.** A rescale lifts a denied-break marker (0.001) to 1 Integrity, because of the lower clamp.
- **Manual checklist:**
  - retarget mid-swing in-game (slow→fast and fast→slow) and watch that the swing animation ends on the corrected recovery;
  - strike an upper door half and a bed foot to see the shared crack on both halves;
  - open a door and retract a piston without losing damage.

---

## 6. Design decisions for Stefan (not invented)
- **D1. Which physical transformations preserve Integrity?** Candidates: axe stripping (vanilla `AxeItem.STRIPPABLES`), copper weathering and waxing, dirt→path/farmland, grass→dirt. The mechanism is ready; this pass authored none.
- **D2. Legacy fold rule for old two-position records.** Implemented: the more damaged record wins. Alternatives: sum, or owner-only.
- **D3. Should unprofiled (legacy Gold/bare-hand) swings get the cadence correction?** Implemented: yes, via their cadence ratio. Gold remains provisional.
- **D4. Explicit ORDINARY on a hardness-0 block.** It does **not** take ownership from vanilla (ownership still requires hardness > 0). Making instant blocks strikable is the audit's open decision 3.
- **D5. Tool-neutral tooltip presentation:** today no Effective Tool row is shown.
- **D6. §26L/§26M cross-check** once the master reference is available.

---

## 7. Changed files (task-only)

**New:**
- `api/mining/BlockProfile.java`
- `api/mining/BlockProfileResolver.java`
- `api/mining/BlockProfiles.java`
- `api/mining/IntegrityReconciliation.java`
- `api/mining/MiningCadence.java`
- `networking/mining/MiningRecoveryPayload.java`
- the 4 test classes under `src/test/java/zcylas/totality/api/mining/`

**Modified** (some already carried unrelated pre-existing edits, which were preserved):
- `BlockBreaking`
- `BlockDamageStorage`
- `BlockDurability`
- `BlockDurabilityDefinitions`
- `MiningOwnership`
- `MiningVerification`
- `PlayerMiningManager`
- `TargetEffectiveness`
- `client/mining/ClientMiningController`
- `client/mining/MiningHandAnimation`
- `client/tooltip/contributor/BlockDurabilityContributor`
- `networking/TotalityPackets`

The exact list is in `CHANGED_FILES.txt`; `TASK_ONLY.patch` has 22 file diffs.

## 8. Repository safety
- **No reset, clean, stash, branch switch, commit or push.** Nothing is staged.
- **Every pre-existing modification, world, run directory and artefact was preserved.**
- **Runs happened only in the disposable `build/verification-run` and `build/run/clientGameTest`.**
- **Post-task `git status`:** 90 M, 1 D, 83 ?? entries, recorded in `evidence/git-status-post.txt`.
  - This task added 4 tracked modifications (`BlockBreaking`, `MiningOwnership`, `ClientMiningController`, `MiningHandAnimation`) and 7 untracked entries (5 new API classes, the payload and the new test directory).
  - The other task-touched files were already modified or untracked.
