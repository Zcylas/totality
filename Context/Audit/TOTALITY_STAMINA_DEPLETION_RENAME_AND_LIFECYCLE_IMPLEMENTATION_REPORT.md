# TOTALITY — STAMINA DEPLETION TERMINOLOGY AND LIFECYCLE CORRECTION — IMPLEMENTATION REPORT

**Date:** 2026-08-01
**Type:** Narrow terminology rename + lifecycle defect correction. No commit, stage, or push occurred.
**Branch:** `feature/general-resource-api`
**Committed HEAD (unchanged throughout):** `07d5200b1430d59da83707460f1154aab3978599` ("Polish player HUD and chat layout")

---

## 1. Executive Summary

Totality's immediate, Stamina-derived exertion/depletion mechanic — previously named `ExhaustionManager`/`ExhaustionState` in package `api/rpg/combat/exhaustion` — has been renamed to `StaminaDepletionManager`/`StaminaDepletionState` in package `api/rpg/combat/stamina/depletion`, per the 2026-07-31 audit's finding that this naming collided with, and risked being confused with, Totality's future D&D-inspired persistent, multi-source Exhaustion condition (influenced by Fatigue, Rest, starvation, dehydration, environmental hazards, disease, curses, spells, overwork, and special abilities). This is a terminology and lifecycle correction only — it does not implement any part of that future condition.

The audit's confirmed lifecycle defect — `ExhaustionManager.onPlayerJoin(ServerPlayer)` existed specifically to prevent a spurious "you are exhausted" notification on join, but was never wired into `PlayerConnectionEvents.JOIN` — has been fixed. `StaminaDepletionManager.onPlayerJoin(player)` is now called in the JOIN handler, immediately after the existing `StaminaServerTick.syncStamina(player)` call, which already proves the player's Stamina component is loaded (it reads `PlayerStaminaManager.getStamina(player)` internally).

All current numerical behavior (30% inclusive WINDED boundary, 0.75×/0.50× regen multipliers, −20% movement / −25% attack transient penalties, `ADD_MULTIPLIED_TOTAL` operation) is preserved exactly. No gameplay balance value changed.

---

## 2. Repository Checkpoint

| Check | Before | After |
|---|---|---|
| Branch | `feature/general-resource-api` | `feature/general-resource-api` (unchanged) |
| HEAD | `07d5200b1430d59da83707460f1154aab3978` | unchanged |
| HEAD subject | "Polish player HUD and chat layout" | unchanged |
| `origin/feature/general-resource-api` | 0 ahead / 0 behind | unchanged (no push occurred) |
| Staged changes | None | None |

Pre-existing dirty/untracked entries (27 modified generated files, 37 untracked review bundles/screenshots/logs/caches, including the just-completed `TOTALITY_FOOD_FATIGUE_EXHAUSTION_AND_DORMANT_RESOURCES_AUDIT.md` and its bundle) were recorded before any edit and are confirmed unchanged in §24/final verification.

---

## 3. Approved Terminology

| Old | New |
|---|---|
| `ExhaustionManager` | `StaminaDepletionManager` |
| `ExhaustionState` | `StaminaDepletionState` |
| `WARNING` | `WINDED` |
| `EXHAUSTED` | `DEPLETED` |
| `isExhausted(...)` | `isDepleted(...)` |
| `penalizedPlayers` | `depletedPlayers` |

Package: `zcylas.totality.api.rpg.combat.stamina.depletion` (matches the existing `api.rpg.combat.<subsystem>` convention already used by `combat.bow`, `combat.weapon`, `combat.armor` — no better existing convention was found).

---

## 4. Previous Architecture

`api/rpg/combat/exhaustion/ExhaustionManager.java` + `ExhaustionState.java`. Static, in-memory-only, UUID-keyed (`Map<UUID, ExhaustionState> previousStates`, `Set<UUID> penalizedPlayers`), recomputed every server tick by `StaminaServerTick` from `PlayerStaminaManager.getStamina/getMaxStamina`. Never persisted, never synchronized, never a Resource/component. `onPlayerJoin(ServerPlayer)` existed but had zero call sites — confirmed dead code by the audit and independently reconfirmed by this task's own pre-flight grep.

## 5. Final Architecture

`api/rpg/combat/stamina/depletion/StaminaDepletionManager.java` + `StaminaDepletionState.java`. Identical in-memory, UUID-keyed, per-tick-derived design. The only structural addition is a `TransitionEvent` enum (`NONE`/`ENTERED_WINDED`/`ENTERED_DEPLETED`/`RECOVERED_TO_NORMAL`) returned by a new private UUID-keyed `advance(UUID, int, int)` method, which separates the pure transition *decision* from the `ServerPlayer`-dependent side effects (sound, notification) performed by the public `tick(ServerPlayer)` method. This mirrors the existing UUID-keyed test-seam pattern already used by `RollModifierRegistry` in this codebase (see `RollModifierRegistryLifecycleTest`'s class Javadoc for the same rationale) and was the minimum refactor needed to make the transition sequencing genuinely testable under plain JUnit — no broader framework was introduced.

`onPlayerJoin` now correctly wires to `PlayerConnectionEvents.JOIN`; `onPlayerLeave` (still delegating to the same UUID-keyed cleanup) remains wired to `DISCONNECT`.

---

## 6. Exact Type and Package Renames

| Old path | Disposition |
|---|---|
| `src/main/java/zcylas/totality/api/rpg/combat/exhaustion/ExhaustionManager.java` | **Deleted** |
| `src/main/java/zcylas/totality/api/rpg/combat/exhaustion/ExhaustionState.java` | **Deleted** |

| New path | Disposition |
|---|---|
| `src/main/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionManager.java` | **New** |
| `src/main/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionState.java` | **New** |

No compatibility alias was retained — this is a private mod with no verified external/public API dependency on the old names (confirmed by the pre-flight call-site inventory: only `StaminaServerTick.java` and `PlayerConnectionEvents.java` referenced the old types, both updated in place).

---

## 7. State Mapping

```
NORMAL   → Stamina above 30% of maximum
WINDED   → Stamina > 0 and ≤ 30% of maximum (boundary inclusive to WINDED)
DEPLETED → Stamina ≤ 0
```

`maxStamina ≤ 0` safely returns `NORMAL` (unchanged safe-clamp behavior). A new named constant, `StaminaDepletionState.WINDED_THRESHOLD = 0.30f`, replaces the old inline `0.30f` literal — no behavior change, purely a clarity/testability improvement.

---

## 8. Preserved Numerical Behavior

| Value | Before | After |
|---|---|---|
| WINDED regen multiplier | 0.75 | 0.75 (unchanged) |
| DEPLETED regen multiplier | 0.50 | 0.50 (unchanged) |
| NORMAL regen multiplier | 1.00 | 1.00 (unchanged) |
| Movement penalty | −0.20, `ADD_MULTIPLIED_TOTAL` | unchanged |
| Attack penalty | −0.25, `ADD_MULTIPLIED_TOTAL` | unchanged |
| Sprint force-cancel | independent raw `stamina<=0` check in `StaminaServerTick` | unchanged, untouched |
| Power Attack Stamina gate | independent, own gate | unchanged, untouched (confirmed zero references to the old/new manager in `PowerAttackManager.java`) |

---

## 9. Transition Behavior

Identical to the pre-existing behavior, now expressed via the `TransitionEvent` enum:

- **NORMAL → WINDED**: `ENTERED_WINDED` — breathing sound (`SoundEvents.PLAYER_BREATH`) plays once. No movement/attack penalty. No notification.
- **→ DEPLETED** (from any other state): `ENTERED_DEPLETED` — red "You are out of stamina!" notification once; player added to `depletedPlayers`.
- **Remaining DEPLETED**: no event, no repeated notification, `depletedPlayers` membership is idempotent (a `Set`, cannot duplicate).
- **DEPLETED → WINDED** (partial recovery, still ≤30%): no event fires (matches old `onWarning` behavior exactly) — **penalties remain active**, since `depletedPlayers` is only cleared on the transition into `NORMAL`. This "recovering through WINDED still penalized" behavior is a deliberate preservation of the original code's exact semantics, verified by `penaltiesRemainActiveWhileRecoveringThroughWindedAfterDepleted` in the new test suite.
- **→ NORMAL** (from WINDED or DEPLETED): `RECOVERED_TO_NORMAL` — green "Your stamina has recovered." notification once; `depletedPlayers` entry removed.
- **Repeated NORMAL ticks**: no event, no spam.

---

## 10. Attribute Modifier Changes

`StaminaServerTick.java`'s modifier identifiers were renamed (amounts and operation untouched, confirmed transient/not-NBT-persisted so no migration was needed):

| Old ID | New ID |
|---|---|
| `totality:exhaustion_speed` | `totality:stamina_depleted_movement_penalty` |
| `totality:exhaustion_attack` | `totality:stamina_depleted_attack_penalty` |

---

## 11. Notification Wording Changes

| Transition | Old text | New text |
|---|---|---|
| Entering DEPLETED | "⚠ You are exhausted!" | "You are out of stamina!" |
| Recovering to NORMAL | "✔ You have recovered." | "Your stamina has recovered." |

Color, channel (`SendNotificationPayload`), and delivery mechanism unchanged. The WINDED transition still uses breathing sound feedback only — no new notification or HUD icon was added, per scope.

---

## 12. Player-Join Lifecycle Defect

**Root cause (confirmed by this task's own pre-flight re-verification of the 2026-07-31 audit's finding):** `ExhaustionManager.onPlayerJoin(ServerPlayer)` was a real, correctly-implemented method whose entire purpose was to seed `previousStates`/`penalizedPlayers` silently on join — but `PlayerConnectionEvents.java`'s `ServerPlayConnectionEvents.JOIN` handler never called it. Only `StaminaServerTick.syncStamina(player)` was called for Stamina-related join setup; the depletion-state seed step was simply missing. A player rejoining at ≤30% or 0 Stamina would have their first server tick treat them as transitioning from the default `NORMAL` baseline, firing a spurious breathing sound or "you are exhausted"/"out of stamina" notification (and, at zero Stamina, appearing to apply penalties as a fresh "transition" even though the player was already depleted before the notification-suppressing seed ever ran).

---

## 13. Final Join Initialization

`PlayerConnectionEvents.java`, inside `ServerPlayConnectionEvents.JOIN`:

```java
StaminaServerTick.syncStamina(player);
// Seed the Stamina depletion state now that authoritative Stamina is loaded (proven
// by the getStamina() read inside syncStamina() above) — without this, a player
// rejoining at zero/low Stamina gets a spurious transition notification on the next tick.
StaminaDepletionManager.onPlayerJoin(player);
```

**Ordering justification:** `StaminaServerTick.syncStamina(player)` was already present at this exact point in the JOIN handler and internally calls `PlayerStaminaManager.getStamina(player)` — the same accessor `StaminaDepletionManager.onPlayerJoin` itself calls. Since `syncStamina` already relies on this read returning the player's real, persisted Stamina value (its own comment states it sends "the correct value immediately... rather than defaulting to 100"), placing `onPlayerJoin` immediately after it guarantees the Stamina component is loaded and authoritative — no additional scheduling (e.g. `server.execute(...)`) was needed or added, since none was needed for `syncStamina` either at this same point.

`onPlayerJoin`/`seed(...)` writes `previousStates`/`depletedPlayers` directly without calling `advance(...)`, so no `TransitionEvent` is ever produced by a join — no sound, no notification, regardless of the seeded state (NORMAL, WINDED, or DEPLETED). A player joining already DEPLETED is correctly seeded into `depletedPlayers` so `StaminaServerTick`'s attribute-penalty block (which reads `isPenalized` every tick, independent of transition events) applies the movement/attack modifiers starting on the very first tick — without re-firing the one-shot notification.

---

## 14. Disconnect/Respawn/Dimension Handling

- **Disconnect**: `PlayerConnectionEvents.java`'s `DISCONNECT` handler already called `ExhaustionManager.onPlayerLeave(handler.player)`; renamed in place to `StaminaDepletionManager.onPlayerLeave(handler.player)`. Removes both `previousStates` and `depletedPlayers` entries for the UUID — confirmed by the new `disconnectCleanupRemovesAllBookkeeping` test.
- **Respawn**: **No dedicated respawn hook was added.** Source inspection (`PlayerStaminaManager`/`ResourceComponents.java`) confirms Stamina uses `RespawnStrategy.ALWAYS_COPY` — the player's Stamina value is *not* reset on death/respawn, it carries over unchanged. Since `StaminaDepletionManager`'s bookkeeping is keyed by the stable player UUID (unchanged across respawn) and recomputed every tick from the carried-over Stamina value, there is no discontinuity for a dedicated hook to correct — the very next `StaminaServerTick.tick(player)` call after respawn naturally continues from the correct prior state. `ServerPlayerEvents.AFTER_RESPAWN` was read in full; it has no reason to touch Stamina depletion, and none was added.
- **Dimension changes**: no Totality code branches on dimension for Stamina or its depletion state; `StaminaServerTick`'s per-tick loop iterates `server.getPlayerList().getPlayers()` unconditionally regardless of dimension, so no duplicate modifiers or notifications are possible — the state machine is dimension-agnostic by construction, exactly as before this rename.

---

## 15. Production Call-Site Migration

Exactly two production call sites referenced the old types (confirmed by pre-flight grep and re-confirmed by the post-change source-regression test scanning all of `src/main/java/zcylas/totality`):

- `src/main/java/zcylas/totality/networking/stamina/StaminaServerTick.java` — import, `tick(player)`, `isPenalized(player)`, `getRegenMultiplier(player)`, and both `Identifier.fromNamespaceAndPath` modifier-ID strings.
- `src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java` — import, new `onPlayerJoin(player)` call, renamed `onPlayerLeave(handler.player)` call.

No other file referenced `ExhaustionManager`/`ExhaustionState` in `src/main/java` or `src/test/java` (the only pre-existing test hit, `FoodResourceAdapterConversionTest`, concerns an unrelated record-shape assertion about the *absence* of a `saturation`/`exhaustion` field and needed no change).

---

## 16. Test Coverage

Three new files, `src/test/java/zcylas/totality/api/rpg/combat/stamina/depletion/`:

- **`StaminaDepletionStateTest.java`** (7 tests) — pure classification tests for `fromStamina(int, int)`, including proportional-not-absolute-threshold proof.
- **`StaminaDepletionManagerLifecycleTest.java`** (21 tests) — real, executing tests against new UUID-keyed test hooks (`seedForTest`/`advanceForTest`/`isPenalizedForTest`/`regenMultiplierForTest`/`previousStateForTest`/`clearPlayerForTest`/`clearForTest`), mirroring the existing `RollModifierRegistryLifecycleTest` pattern since a live `ServerPlayer` requires a bootstrapped Minecraft registry unreachable under plain JUnit. Covers regen multipliers, one-shot transitions in both directions, no-repeat/no-stack guarantees, the WINDED-after-DEPLETED penalty-retention behavior, and full join/disconnect/rejoin bookkeeping lifecycle.
- **`StaminaDepletionSourceMigrationRegressionTest.java`** (17 tests) — source-regression sentinels proving: old package/files absent; no production file anywhere references the old names; no `WARNING`/`EXHAUSTED` in the new files' executable code; no "you are exhausted" wording; approved wording present; modifier IDs renamed and amounts unchanged; `PlayerConnectionEvents` calls `onPlayerJoin` *after* the Stamina-sync proof point; `DISCONNECT` calls `onPlayerLeave`; `StaminaServerTick` still delegates `tick`/`isPenalized`/`getRegenMultiplier`; no `ExhaustionCondition`/Fatigue/RestNeed/`GENERIC_COMPONENT` implementation code was introduced (comments describing the future condition are explicitly permitted and excluded from the scan).

**Total: 45 new tests** (7 + 21 + 17).

---

## 17. Focused Validation

`./gradlew test --tests "zcylas.totality.api.rpg.combat.stamina.depletion.*"` — **BUILD SUCCESSFUL**, all 45 new tests passed on first clean run after one self-inflicted test-authoring fix (a missing `clearPlayerForTest` hook, added before any test ran against it) and one test-assertion relaxation (the scope-guard test originally banned the bare word "Fatigue" even inside explanatory comments, which the task explicitly permits — fixed by scanning code lines only, excluding comments).

---

## 18. Full Validation

- `./gradlew test` (full suite, twice — once before `clean`, once after, to get a true clean-state total): **1361 tests, 0 failures, 0 errors, 0 skipped.**
- **Baseline:** 1316 tests, 0 failures, 0 errors, 0 skipped.
- **Increase: exactly 45 tests** (1361 − 1316), matching the 45 new tests added in §16 precisely — no other test count changed.
- `./gradlew runDatagen`: **0 files written, 0 removed** ("Caching: total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"). `git status --short` was captured immediately before and after datagen and is **byte-for-byte identical** — confirmed via `diff`.
- `./gradlew clean build` (test excluded from this specific invocation since the full suite was already run separately with fresh XML capture immediately after; `clean` then `test`+`build`-equivalent tasks all ran and passed): **BUILD SUCCESSFUL**.
- `git diff --check`: **exit code 0**, no errors. The only warnings emitted are pre-existing CRLF/LF line-ending notices on the 22 pre-existing modified generated-data files (unrelated to this task, present before any edit) — none of this task's changed/new files are mentioned.

---

## 19. Manual Validation

**Not performed** — this task's implementation was validated via the automated test suite (§16–18), source inspection, and the classloading/smoke checks in §"Dedicated-server / client-classloading" below, rather than interactive in-game play. Per the task's own instruction to "record anything not manually verified honestly": Part 8's 18-step manual play-test checklist (join at NORMAL/WINDED/DEPLETED, drain to exactly 30%, recover, disconnect/rejoin in each state, etc.) was **not performed live in-game** in this session. The automated lifecycle tests in §16 exercise the identical underlying transition/bookkeeping logic the manual checklist would exercise (same `advance`/`seed` code path the `ServerPlayer`-facing methods delegate to), and the dedicated-server smoke test below confirms the renamed classes load and initialize correctly server-side with no crash prior to an unrelated environment-level world-lock error. A live playtest is recommended before considering this fully closed, consistent with how the source `ExhaustionManager` itself had zero prior automated coverage per the 2026-07-31 audit.

**Dedicated-server classloading check:** `./gradlew runServer` was run for up to 100 seconds. FabricLoader/Mixin subsystem initialized successfully in `Env=SERVER` ("SpongePowered MIXIN Subsystem Version=0.8.7 ... Env=SERVER", "Compatibility level set to JAVA_25", MixinExtras initialized), and the environment resolved successfully — meaning all mod initializers (including the renamed classes and both updated call-site files) loaded and registered without any classloading error. The server subsequently failed to fully start with an unrelated `java.io.IOException: The process cannot access the file because another process has locked a portion of the file` at `DirectoryLock.create` while acquiring `run/world/session.lock` — a pre-existing environment condition (a stale/concurrently-held world lock in the gitignored `run/` dev directory, unrelated to any file this task touched) that occurs deep inside vanilla's own `LevelStorageSource`, well after all Totality classloading had already succeeded. This is reported honestly as an incomplete server smoke test (no "Done!" startup message was reached) rather than claimed as a full pass.

**Client classloading check:** `./gradlew runDatagen` (run as part of §18) launches the client environment (`Env=CLIENT`) to drive Totality's datagen providers. It completed successfully, including this session's own `NotificationTimingVerification`/`PowerAttackFlashVerification`/`ProvisionerRendererVerification`/`KeybindVerification` self-test suites all reporting "All N self-test checks passed" — confirming client-side classloading and mod initialization succeeded with the renamed package in place (the renamed classes are server-side-only logic living outside any `client.*` package, confirmed by import inspection: no `net.minecraft.client.*` import exists in either new file).

---

## 20. Scope Boundaries

Explicitly NOT done, per task instructions: no real persistent Exhaustion condition, `ExhaustionCondition` type, Exhaustion levels, Exhaustion persistence/synchronization, Fatigue integration, Rest-mechanic modification, survival/environment integration, new status effects, or new dormant Resource definitions were added. No Tooltip item Value, HUD layout, or Mob Display changes were made. No broad formatting was run. No other roadmap task was begun.

---

## 21. Future Persistent Exhaustion Separation

Stamina Depletion (`StaminaDepletionManager`/`StaminaDepletionState`) is an **immediate**, purely Stamina-derived state, recomputed every server tick with no independent lifecycle, persistence, or synchronization. `WINDED` and `DEPLETED` are **not** D&D Exhaustion levels and carry no relationship to a tiered persistent condition. Totality's future Exhaustion condition — persistent, tiered, multi-source, influenced especially by Fatigue and Rest and applicable via starvation, dehydration, environmental hazards, disease, curses, spells, overwork, and special abilities — **remains entirely unimplemented** and is a separately-owned future system. This rename does not build any part of it, does not reserve its eventual type name (`ExhaustionCondition`, per the audit's own recommendation, remains available and unused), and does not couple Stamina Depletion's lifecycle to Rest or Fatigue in any way.

---

## 22. Files Changed

**Deleted:**
- `src/main/java/zcylas/totality/api/rpg/combat/exhaustion/ExhaustionManager.java`
- `src/main/java/zcylas/totality/api/rpg/combat/exhaustion/ExhaustionState.java`

**New (production):**
- `src/main/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionManager.java`
- `src/main/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionState.java`

**Modified (production):**
- `src/main/java/zcylas/totality/networking/stamina/StaminaServerTick.java`
- `src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java`

**New (tests):**
- `src/test/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionStateTest.java`
- `src/test/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionManagerLifecycleTest.java`
- `src/test/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionSourceMigrationRegressionTest.java`

**Report:**
- `Context/Audit/TOTALITY_STAMINA_DEPLETION_RENAME_AND_LIFECYCLE_IMPLEMENTATION_REPORT.md` (this file)

No other file was touched. All pre-existing unrelated dirty/untracked entries (27 modified generated files, 37 untracked entries) are unchanged — confirmed in §24 of the accompanying final-verification response.

---

## 23. Unresolved Issues

- Manual in-game playtest (Part 8's 18-step checklist) was not performed live this session — see §19.
- The dedicated-server smoke test did not reach a full "Done!" startup due to an unrelated, pre-existing world-lock environment condition — see §19.
- No other unresolved issues within this task's scope were identified.

---

## 24. Final Status

**Complete**, within the honestly-disclosed limits of §19 and §23. Terminology and lifecycle correction fully implemented, all production call sites migrated, old package removed with no compatibility shim, 45 new focused tests added and passing, full suite green at 1361/0/0/0, datagen byte-for-byte unchanged, clean build successful, `git diff --check` clean, nothing staged/committed/pushed, HEAD unchanged, all pre-existing unrelated working-tree entries preserved exactly.

---

## 25. Final Manual Validation (Closure Addendum, 2026-08-01)

This section closes the one open item flagged in §19 (no live in-game playtest performed) and §23 (item 1). Everything above this addendum is the original implementation-pass report and is left unmodified — this section only appends the result of the subsequent, separate manual-validation pass the user performed against the built jar.

**Result: the user completed the in-game manual validation and confirmed that everything appears correct.** No regressions, crashes, or lifecycle anomalies were observed.

Accepted manual results, matching the Part 8 checklist from the original task:

- Joining with normal Stamina produced no false notification.
- Crossing into `WINDED` produced the expected breathing feedback exactly once.
- Remaining `WINDED` across subsequent ticks did not repeat the breathing feedback.
- `WINDED` alone applied no movement or attack penalty.
- Reaching zero Stamina correctly entered `DEPLETED`.
- The `You are out of stamina!` notification appeared exactly once on entering `DEPLETED`.
- The existing movement-speed and attack-damage penalties behaved correctly (matching pre-rename behavior — no change in feel, magnitude, or trigger condition).
- Sprint cancellation and other existing Stamina-gated actions (Power Attack affordability, bow draw, etc.) behaved exactly as before the rename.
- Recovering back above the 30% threshold correctly removed the transient penalties.
- The `Your stamina has recovered.` notification appeared exactly once on recovery.
- No repeated or stacked attribute modifiers were observed across any transition or across remaining in a state for multiple ticks.
- No crashes, exceptions, or visible lifecycle regressions occurred during join, play, disconnect, or rejoin.

This manual pass is independent confirmation of, and consistent with, the automated lifecycle tests in §16 (`StaminaDepletionManagerLifecycleTest`), which exercise the identical underlying `seed`/`advance` transition logic the `ServerPlayer`-facing methods delegate to. With this pass complete, the one caveat recorded in §19 ("a live playtest is recommended before considering this fully closed") is now satisfied.

### Final architecture confirmation

- `StaminaDepletionState` is derived, each tick, purely from current/max Stamina (`StaminaDepletionState.fromStamina(int, int)`) — it has no independent state of its own beyond that computation.
- It is **not persisted independently** — no NBT, no save data, no schema version. It lives only in `StaminaDepletionManager`'s in-memory, UUID-keyed maps, exactly as the original `ExhaustionManager` did.
- It is **not synchronized as a state enum** — no packet carries `StaminaDepletionState`/`TransitionEvent` to the client; only the pre-existing, unrelated Stamina scalar sync (`StaminaServerTick.syncStamina`) and the one-shot notification/sound side effects cross the network.
- It is **not a Generic Player Resource** — no `PlayerResourceDefinition`, no `PlayerResourceIds` entry, no `GENERIC_COMPONENT`/`EXTERNAL_ADAPTER` registration of any kind was added for it. Stamina itself (the resource it derives from) remains the only Resource-API-registered entity in this area, and its own `EXTERNAL_ADAPTER` definition (`ProductionResourceDefinitions.java`) was not touched.
- `WINDED` and `DEPLETED` are **not real Exhaustion levels.** They carry no D&D-style tier semantics, no stacking, no multi-source accumulation, and no relationship to a persistent condition.
- Totality's real Exhaustion remains a **future, D&D-inspired, persistent, tiered, multi-source condition** — entirely unimplemented by this task or any prior one. `ExhaustionCondition` (the audit's own suggested future type name) remains unused and unreserved by this rename.
- **Fatigue and Rest are expected to influence that future Exhaustion condition but remain unimplemented** — no Fatigue system, no Rest-mechanic change, and no coupling between `StaminaDepletionManager` and either concept was introduced at any point across the implementation or closure passes.

### Final status

- Automated validation: **passed** (45 new tests; full suite 1361/0/0/0; datagen byte-for-byte unchanged; clean build successful; `git diff --check` clean).
- Server/client classloading validation: **passed** (Mixin subsystem and mod initialization succeeded in both `Env=SERVER` and `Env=CLIENT`; the one incomplete server smoke-test detail in §19 was an unrelated, pre-existing world-lock condition outside this task's touched files).
- User manual validation: **passed** — see accepted results above.
- **No additional correction is required.**
- **The implementation is ready to commit and push.**
