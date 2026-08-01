# TOTALITY — VANILLA FOOD/PEACEFUL AUTOMATIC HEALTH REGENERATION CORRECTION — IMPLEMENTATION REPORT

**Date:** 2026-08-01
**Type:** Narrow mixin-based behavior correction. No commit, stage, or push occurred.
**Branch:** `feature/general-resource-api`
**Committed HEAD (unchanged throughout):** `4b29d27bd2aa3d6fb905eb0acba6ff4388dffc52` ("Register dormant player resources")

---

## 1. Executive Summary

Totality's accepted Health-recovery policy states that ordinary passive Health regeneration caused by vanilla Food/hunger mechanics must be disabled, with Health restored only through authored systems (Rest, spells, runes, rituals, potions, medicine, conditions, abilities). The 2026-07-31 Food/Fatigue/Exhaustion audit confirmed this was never implemented — natural regeneration remained fully vanilla.

This pass implements the correction with two narrow, server-authoritative mixins, informed by direct inspection of the real Minecraft 26.2 mapped source (extracted from the Loom-cached sources jar, not decompiled bytecode guesswork and not memory of older versions):

1. **`FoodDataNaturalRegenerationMixin`** — targets `net.minecraft.world.food.FoodData#tick(ServerPlayer)`. Both vanilla Food-driven automatic Health branches (fast/saturated and slow/well-fed) are gated by one local boolean, `naturalRegen`, copied from the real `NATURAL_HEALTH_REGENERATION` gamerule at the top of the method. A `@ModifyVariable` forces only this method's local copy to `false`, making both branches — and their coupled `addExhaustion(...)` calls — structurally unreachable. The real gamerule is never written; starvation and all saturation/exhaustion processing are completely unaffected.
2. **`ServerPlayerPeacefulRegenerationMixin`** — targets `net.minecraft.server.level.ServerPlayer#tickRegeneration()`, Peaceful difficulty's own, entirely separate automatic Health path (unrelated to `FoodData`). A `@Redirect` suppresses only the `this.heal(1.0F)` call site; the sibling saturation-restoration and separate Food-level-restoration statements in the same method are untouched.

Both mixins are comment-only/logic-minimal, server-side (no client-only reference), and rely on the project's existing `defaultRequire: 1` mixin-config setting to fail loudly if either target ever disappears under a future Minecraft update.

---

## 2. Repository Checkpoint

| Check | Value |
|---|---|
| Branch | `feature/general-resource-api` (unchanged throughout) |
| HEAD | `4b29d27bd2aa3d6fb905eb0acba6ff4388dffc52` (unchanged throughout) |
| HEAD subject | "Register dormant player resources" |
| `origin/feature/general-resource-api` | 0 ahead / 0 behind at start and end |
| Staged changes | None at start; none at end |

Pre-flight `git status --short` recorded 68 pre-existing entries (22 modified generated/build files, 46 untracked review bundles/screenshots/logs/caches/audit docs). All are confirmed unchanged in §Final Verification below.

---

## 3. Accepted Health-Recovery Policy

Ordinary passive Health regeneration caused by vanilla Food/hunger mechanics — both the Food-driven fast/slow paths and Peaceful difficulty's own separate path — is disabled. Health is restored only through authored Totality systems: Short/Long Rest, spells, runes, rituals, potions, medicine, conditions such as `REGENERATING`, species/class abilities, and other authored recovery systems (present or future). Explicit healing, starvation, saturation, Food consumption, and general (non-regeneration) hunger exhaustion are all preserved unchanged.

---

## 4. Previous Behavior

Confirmed by direct source inspection at the start of this pass (matching the 2026-07-31 audit's own findings, re-verified fresh): Food remained fully vanilla-authoritative through `FoodData`, mechanically 0–20; saturation and hunger exhaustion were fully vanilla; Totality had zero Food-related regeneration mixin of any kind (`grep` for `FoodData`/`tickRegeneration`/`NATURAL_HEALTH_REGENERATION` in `src/main/java` returned no production hits before this pass); natural Food-based and Peaceful-difficulty automatic Health regeneration were both fully active exactly as stock Minecraft 26.2 provides them.

---

## 5. Minecraft 26.2 Vanilla Source Inspection

**Method:** extracted directly from the Fabric Loom-cached sources jar for this exact project checkpoint — `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-043a8b3edf/26.2/minecraft-merged-043a8b3edf-26.2-sources.jar` — via `unzip`, not decompiled bytecode and not memory of any older Minecraft version. The two relevant files (`net/minecraft/world/food/FoodData.java`, `net/minecraft/world/entity/player/Player.java`, plus `net/minecraft/server/level/ServerPlayer.java` and `net/minecraft/world/entity/LivingEntity.java` for the `heal` declaration check) were extracted and read in full.

### 5.1 `FoodData.java` — the complete file, class `net.minecraft.world.food.FoodData`

```java
public void tick(final ServerPlayer player) {
    ServerLevel level = player.level();
    Difficulty difficulty = level.getDifficulty();
    if (this.exhaustionLevel > 4.0F) {
        this.exhaustionLevel -= 4.0F;
        if (this.saturationLevel > 0.0F) {
            this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
        } else if (difficulty != Difficulty.PEACEFUL) {
            this.foodLevel = Math.max(this.foodLevel - 1, 0);
        }
    }

    boolean naturalRegen = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
    if (naturalRegen && this.saturationLevel > 0.0F && player.isHurt() && this.foodLevel >= 20) {
        this.tickTimer++;
        if (this.tickTimer >= 10) {
            float saturationSpent = Math.min(this.saturationLevel, 6.0F);
            player.heal(saturationSpent / 6.0F);
            this.addExhaustion(saturationSpent);
            this.tickTimer = 0;
        }
    } else if (naturalRegen && this.foodLevel >= 18 && player.isHurt()) {
        this.tickTimer++;
        if (this.tickTimer >= 80) {
            player.heal(1.0F);
            this.addExhaustion(6.0F);
            this.tickTimer = 0;
        }
    } else if (this.foodLevel <= 0) {
        this.tickTimer++;
        if (this.tickTimer >= 80) {
            if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD
                    || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
                player.hurtServer(level, player.damageSources().starve(), 1.0F);
            }
            this.tickTimer = 0;
        }
    } else {
        this.tickTimer = 0;
    }
}
```

Method descriptor: `tick(Lnet/minecraft/server/level/ServerPlayer;)V`. Exactly one boolean local is declared in the whole method: `naturalRegen`.

### 5.2 `ServerPlayer.java` — `tickRegeneration()` override, class `net.minecraft.server.level.ServerPlayer`

```java
@Override
protected void tickRegeneration() {
    if (this.level().getDifficulty() == Difficulty.PEACEFUL
            && this.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
        if (this.tickCount % 20 == 0) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }
            float saturation = this.foodData.getSaturationLevel();
            if (saturation < 20.0F) {
                this.foodData.setSaturation(saturation + 1.0F);
            }
        }
        if (this.tickCount % 10 == 0 && this.foodData.needsFood()) {
            this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
        }
    }
}
```

Method descriptor: `tickRegeneration()V`, `protected`, overriding `Player#tickRegeneration()` (an empty no-op hook, `protected void tickRegeneration() {}`, called unconditionally once per tick from `Player#aiStep()` via `this.tickRegeneration();`). `heal(1.0F)` resolves, by inheritance, to `LivingEntity#heal(float)` (`public void heal(final float heal)` — confirmed the sole declaration site by grep; neither `Player` nor `ServerPlayer` overrides it). Following this codebase's own established convention (`PlayerRestSleepMixin`/`LivingEntityRestSleepMixin`, both of which target inherited-method call sites using the *mixed-into* class as the `@At` owner, since that is the class the compiler resolves `this.method()` against at the call site), the redirect target owner used is `ServerPlayer`, not `LivingEntity`.

### 5.3 Category separation (per task's Part 1 requirement)

| Category | Location | Disposition this pass |
|---|---|---|
| **A. Food-driven automatic Health regeneration** | `FoodData#tick`, both `if`/`else if` branches gated by `naturalRegen` | **Suppressed** (§6, §7) |
| **B. Peaceful automatic Health regeneration** | `ServerPlayer#tickRegeneration`, the `this.heal(1.0F)` call | **Suppressed** (§8) |
| **C. Regeneration status effects** | Vanilla `MobEffects.REGENERATION`/Totality's `Conditions.REGENERATING` (`ConditionServerTick.java:59`, `entity.heal(0.2f)`) | **Untouched** — entirely separate call site (§13) |
| **D. Explicit `LivingEntity`/`Player` heal calls** | `HealingPotionItem.java`, `HealEffect.java`, `AlchemyEffects.java`, vanilla instant-health/regeneration effects | **Untouched** — entirely separate call sites (§13) |
| **E. Starvation damage** | `FoodData#tick`, the `else if (this.foodLevel <= 0)` branch — condition does not reference `naturalRegen` at all | **Untouched** (§14) |
| **F. Hunger and saturation processing** | The exhaustion→saturation/food conversion at the top of `FoodData#tick` (runs before `naturalRegen` is even computed); `ServerPlayer#tickRegeneration`'s saturation/Food-level restoration statements (siblings of, not inside, the suppressed `heal` call) | **Untouched** (§14) |

---

## 6. Food-Based Fast Regeneration Path

`FoodData#tick`, first branch: `if (naturalRegen && this.saturationLevel > 0.0F && player.isHurt() && this.foodLevel >= 20)` — every 10 ticks (`this.tickTimer >= 10`), calls `player.heal(saturationSpent / 6.0F)` then `this.addExhaustion(saturationSpent)` where `saturationSpent = Math.min(this.saturationLevel, 6.0F)`. Suppressed by forcing `naturalRegen` false for this method (§10).

## 7. Food-Based Slow Regeneration Path

`FoodData#tick`, second branch (`else if`): `naturalRegen && this.foodLevel >= 18 && player.isHurt()` — every 80 ticks, calls `player.heal(1.0F)` then `this.addExhaustion(6.0F)`. Suppressed by the same `naturalRegen` local-variable interception (§10) — both branches share one gating local, so one interception point suppresses both.

## 8. Peaceful Automatic Healing Path

`ServerPlayer#tickRegeneration()`, gated by `Difficulty.PEACEFUL && NATURAL_HEALTH_REGENERATION`. Every 20 ticks, `this.heal(1.0F)` fires if `getHealth() < getMaxHealth()` — this exact call site is redirected to a no-op (§11). Health restoration and Food/saturation restoration are **not** one shared branch inside `tickRegeneration()` — the outer `if` gates all of it, but Health, saturation, and Food restoration are three separate sibling statements inside (two under the 20-tick sub-check, one under a separate 10-tick sub-check). Redirecting only the `heal(F)` invocation leaves the saturation and Food-level statements executing exactly as before.

---

## 9. `naturalRegeneration` Gamerule Semantics

- The real `NATURAL_HEALTH_REGENERATION` gamerule (Minecraft's own `GameRules` object) is **never written** by this pass — confirmed by source-regression test and by direct inspection: neither mixin contains a `GameRules`/`.set(...)` call in its code body.
- `naturalRegeneration=false` remains fully compatible with vanilla/mod behavior — nothing about how the gamerule itself works changed.
- `naturalRegeneration=true` **does not** override Totality's Health policy: the two suppressed branches in `FoodData#tick` are unreachable regardless of the real gamerule's value, because the mixin substitutes this one method's own local copy, not the gamerule storage.
- Totality's explicit healing systems (`HealingPotionItem`, `HealEffect`, `AlchemyEffects`, `Conditions.REGENERATING`) do not consult this gamerule before or after this pass — confirmed unchanged, zero references to `NATURAL_HEALTH_REGENERATION` in any of them.
- This task does not redefine the gamerule globally, and does not add a replacement Totality gamerule.

---

## 10. Final Mixin Architecture

**`FoodDataNaturalRegenerationMixin`** (`src/main/java/zcylas/totality/mixin/FoodDataNaturalRegenerationMixin.java`):

```java
@Mixin(FoodData.class)
public abstract class FoodDataNaturalRegenerationMixin {
    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private boolean totality$suppressVanillaFoodDrivenHealthRegeneration(boolean naturalRegen) {
        return false;
    }
}
```

`@ModifyVariable` with `@At("STORE")` intercepts the value immediately after it is stored into the local variable slot — this substitutes only the value used *for the remainder of this one method invocation*; it never touches `GameRules`. `ordinal = 0` selects the first (and, per §5.1, the *only*) `boolean`-typed local in the method, making the match unambiguous without depending on LocalVariableTable name-debug data (which official Mojang-mapped jars are not guaranteed to preserve for arbitrary locals).

**`ServerPlayerPeacefulRegenerationMixin`** (`src/main/java/zcylas/totality/mixin/ServerPlayerPeacefulRegenerationMixin.java`):

```java
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPeacefulRegenerationMixin {
    @Redirect(method = "tickRegeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
    private void totality$suppressPeacefulAutomaticHeal(ServerPlayer player, float amount) {
        // no-op — does not call player.heal(amount)
    }
}
```

Neither mixin cancels a whole method, a whole branch containing unrelated logic, or any `heal`/tick call outside its one exact target. Neither references a client-only class (confirmed: `FoodData` and `ServerPlayer` are both common/server-side classes; source-regression test confirms no `.client.` import in either file).

## 11. Exact Injection Targets

| Mixin | Target class | Target method | Descriptor | Injector | Selector |
|---|---|---|---|---|---|
| `FoodDataNaturalRegenerationMixin` | `net.minecraft.world.food.FoodData` | `tick` | `(Lnet/minecraft/server/level/ServerPlayer;)V` | `@ModifyVariable` | `@At("STORE")`, `ordinal = 0` (sole boolean local) |
| `ServerPlayerPeacefulRegenerationMixin` | `net.minecraft.server.level.ServerPlayer` | `tickRegeneration` | `()V` | `@Redirect` | `@At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V")` |

Both rely on the project-wide `"injectors": { "defaultRequire": 1 }` setting already present in `totality.mixins.json` (applies to every mixin in the project, not newly added by this pass) — if either target ever disappears or shifts (e.g. a future Minecraft update restructures `FoodData#tick` or removes the single-boolean-local shape, or `tickRegeneration` stops calling `heal` directly), Mixin's own bootstrap will throw and fail loudly rather than silently no-op.

---

## 12. Regeneration-Specific Hunger Exhaustion

`FoodData#tick`'s `addExhaustion(saturationSpent)`/`addExhaustion(6.0F)` calls live inside the same `if`/`else if` bodies as the now-unreachable `heal(...)` calls (§6, §7) — forcing `naturalRegen` false makes the entire branch body, heal call and exhaustion charge together, structurally unreachable in one interception. No separate exhaustion-suppression call was needed or added. `ServerPlayer#tickRegeneration()`'s Peaceful path charges **no** exhaustion at all in vanilla (confirmed by §5.2 — no `addExhaustion`/`causeFoodExhaustion` call anywhere in that method), so nothing extra needed suppressing there either. `HealEffect`'s own explicit `player.causeFoodExhaustion(2.5f)` call (an unrelated, deliberate design choice by that rune effect) is a completely separate call site, confirmed byte-for-byte unchanged by source-regression test. All other vanilla hunger-exhaustion sources (movement, sprinting, jumping, swimming, combat, mining) are untouched — none of them are reachable from either mixin's target method.

## 13. Explicit Healing Preservation

Confirmed unchanged (zero production diff) for every known explicit healing path, verified both by `git status`/`git diff` scope (neither mixin touches these files) and by dedicated source-regression assertions:

- `item/potion/dnd/HealingPotionItem.java:63` — `user.heal(amount);` (D&D Potion of Healing).
- `api/rpg/skills/alchemy/AlchemyEffects.java:36,42,45` — `entity.heal(5f);` / `entity.heal(entity.getMaxHealth());` / `entity.heal(RpgDisplayUtils.toVanillaHp(...))` (Alchemy healing effects).
- `api/combat/condition/ConditionServerTick.java:59` — `entity.heal(0.2f);` under `Conditions.REGENERATING` (Totality's regeneration condition — separate from and unaffected by vanilla `MobEffects.REGENERATION`, which itself is also untouched, since neither mixin's target method is anywhere on that effect's tick path).
- `item/magic/rune/effect/HealEffect.java:72,74` — `player.causeFoodExhaustion(2.5f);` then `target.heal(amount);` (HealEffect rune healing).
- Rest healing: no direct `heal(...)`/Health-affecting call currently exists anywhere in `api/rpg/rest/` (confirmed by grep) — Rest does not currently restore Health through any code path this pass could have broken; this is an honestly-recorded pre-existing gap, not something newly introduced or hidden by this correction.
- Vanilla Instant Health/Regeneration `MobEffect`s, Commands/debugging heal tools, and any other direct `LivingEntity.heal` call site anywhere else in the codebase: unaffected, since neither mixin intercepts `LivingEntity.heal`/`Player.heal` globally — only the two exact call sites named in §11.

## 14. Starvation and Food-Mechanic Preservation

- Starvation (`FoodData#tick`'s third branch, `else if (this.foodLevel <= 0)`) does not reference `naturalRegen` in its condition at all — completely unaffected by the `@ModifyVariable` interception.
- The exhaustion→saturation/food-level conversion at the very top of `FoodData#tick` (lines preceding the `naturalRegen` assignment) is unaffected — it runs before the modified local even exists.
- Food remains native 0–20 (`FoodData.foodLevel`, `Mth.clamp(..., 0, 20)`) — untouched.
- Saturation remains fully vanilla (`FoodData.saturationLevel`) — untouched, including `ServerPlayer#tickRegeneration()`'s own Peaceful saturation-restoration statement, which is a sibling of (not inside) the suppressed `heal` call.
- `FoodData.hasEnoughFood()`/sprint-gating threshold (`getFoodLevel() > 6.0F`) — untouched; not read anywhere on either mixin's target method.
- Vanilla hunger exhaustion from movement/jumping/sprinting/attacking/mining/swimming — all funnel through `Player#causeFoodExhaustion(float)`, which neither mixin touches.

---

## 15. Production Files Added or Changed

**New:**
- `src/main/java/zcylas/totality/mixin/FoodDataNaturalRegenerationMixin.java`
- `src/main/java/zcylas/totality/mixin/ServerPlayerPeacefulRegenerationMixin.java`

**Modified:**
- `src/main/resources/totality.mixins.json` — registered both new mixins in the server-authoritative `"mixins"` array (not the `"client"` array).

No other production file was touched. `HealthResourceAdapter.java`, `FoodResourceAdapter.java`, `ProductionResourceDefinitions.java`, and every explicit-healing file named in §13 are confirmed byte-for-byte unchanged.

## 16. Mixin Configuration Changes

`totality.mixins.json`'s `"mixins"` array gained two entries, `"FoodDataNaturalRegenerationMixin"` and `"ServerPlayerPeacefulRegenerationMixin"`, inserted after the existing `"PlayerRestSleepMixin"` entry. The `"client"` array, `"injectors"` block (`defaultRequire: 1`, unchanged), and `"overwrites"` block (`requireAnnotations: true`, unchanged) are otherwise untouched.

## 17. Tests Added or Changed

**New:** `src/test/java/zcylas/totality/mixin/NaturalFoodRegenerationMixinSourceRegressionTest.java` (14 tests) — source-regression sentinels confirming: exact mixin targets/annotations/descriptors for both new mixins; neither writes the real gamerule; neither's redirect/modify body itself calls a suppressed method; both are registered in the server-authoritative (not client) mixin-config section; the project's `defaultRequire: 1` safeguard remains in place; neither mixin imports a client-only class; no other production file forcibly sets the gamerule; the four known explicit-healing call sites remain textually present (a source-presence sentinel — see §28.2 for the exact, narrower claim and its rename); no production file outside `ServerPlayerPeacefulRegenerationMixin.java` references `tickRegeneration` (§28.2); the Resource-API files named in §15 contain no reference to either new mixin or to `tickRegeneration`.

No pure policy helper (e.g. a `VanillaAutomaticHealthRegenerationPolicy` class) was extracted — deliberately. Both mixins are unconditional (`return false` / no-op redirect, no branching decision that varies by input), so there is no meaningful decision logic to isolate for a "pure policy" unit test; the actual correctness question — whether the mixin's *injection point* is still valid against the real game — is answerable only by loading the real Minecraft classes, which is exactly what the dedicated-server/client checks in §21 do. Per the task's own Part 7 guidance ("only if it materially improves deterministic tests" / "do not create a broad healing framework"), adding an unused pure-policy class here would be premature abstraction, not genuine test value.

---

## 18. Focused Validation

`./gradlew test --tests "zcylas.totality.mixin.NaturalFoodRegenerationMixinSourceRegressionTest"` — **BUILD SUCCESSFUL**, all 14 tests passed after two self-inflicted false-positive fixes during authoring (two assertions were tripped by the mixins' own explanatory comments mentioning `GameRules`/`player.heal(` in prose — fixed by scanning code lines only, excluding comments, matching the same established pattern used in prior sessions' source-regression tests in this codebase).

## 19. Full Validation

- `./gradlew test` (full suite, run twice — once standalone, once as part of `./gradlew clean build`): **1405 tests, 0 failures, 0 errors, 0 skipped.**
- **Baseline:** 1391 tests, 0 failures, 0 errors, 0 skipped.
- **Increase: exactly 14 tests** (1405 − 1391), matching the 14 new tests in §17 precisely.

## 20. Datagen and Build Validation

- `./gradlew runDatagen`: **0 files written, 0 removed** ("Caching: total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"). **[Corrected — see §28]** This run launches the client/Fabric-Loader environment and completed without any mixin configuration, target-resolution, or classloading error, which is startup/classloading evidence, not by itself proof that both mixin targets were specifically transformed — see §28 for the distinction and for the explicit transformation evidence actually obtained this pass. `git status --short` captured immediately before and after is **byte-for-byte identical**.
- `./gradlew clean build`: **BUILD SUCCESSFUL.**
- `git diff --check`: **exit code 0** — only the same pre-existing CRLF/LF informational notices on the 22 pre-existing modified generated-data files; the two new mixin files are untracked so do not appear in `git diff` at all, and `totality.mixins.json`'s diff produced no whitespace warning.

## 21. Dedicated-Server and Client Validation

**Dedicated-server:** `./gradlew runServer` was run for up to 100 seconds. Reached **"Done (0.351s)! For help, type \"help\""**. `Env=SERVER` was confirmed in the Mixin subsystem startup log ("SpongePowered MIXIN Subsystem Version=0.8.7 ... Env=SERVER"). Zero exceptions, zero `MixinApply`/`MixinTransformer`/`InvalidInjectionException` errors anywhere in the log. **[Corrected — see §28]** At ordinary log verbosity this establishes that mixin configuration loaded and the server started without any classloading or injection-resolution error — it does not, by itself, name either target class as actually transformed. §28 records the explicit per-mixin transformation evidence obtained separately, during the review-correction pass, at elevated Mixin log verbosity. The same two unrelated, pre-existing self-test suites (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) logged the same failures as every prior run in this environment (NPC trading and offhand combat, not Food/Health/Resource-related) — clearly separated from this task and not claimed fixed by it.

**Client:** confirmed via `./gradlew runDatagen` (§20) — its `Env=CLIENT` launch succeeded end-to-end, including this session's own `NotificationTimingVerification`/`PowerAttackFlashVerification`/`ProvisionerRendererVerification`/`KeybindVerification` self-tests all reporting "All N self-test checks passed." **[Corrected — see §28]** This is client-side startup/classloading evidence — it did not run at elevated Mixin verbosity, so it does not itself name either mixin as transformed (unlike the dedicated-server run recorded in §28.1, which does).

## 22. Manual Validation

**[Superseded — see §29]** At the time this section was originally written, manual in-game validation had not yet been performed. The user has since completed it and accepted the results; §29 records exactly what was established, honestly distinguished from source-level, startup, and transformation evidence. This section is left in place, unedited beyond this note, as an accurate record of the implementation pass's own state at the time it was written.

---

## 23. Scope Boundaries

Explicitly not done, per task instructions: Food 0–100 was not begun; Food authority/native 0–20 storage was not changed; the Food HUD was not touched; no food item was reauthored; `TotalityFoodItem` was not added; saturation/starvation behavior was not changed; vanilla sprint hunger requirements were not changed; general vanilla hunger exhaustion was not changed; Stamina Depletion was not modified; Fatigue was not implemented; the real persistent Exhaustion condition was not implemented; Rest was not modified; Thirst/Sanity/Ki were not activated; Fatigue/Temperature were not registered; Tooltip item Value was not touched; no other roadmap task was begun; no broad formatting was run.

## 24. Risks and Limitations

- **Mapping/version fragility, by design, fails loudly.** Both injection points depend on the exact current shape of `FoodData#tick` (one boolean local) and `ServerPlayer#tickRegeneration` (one direct `heal(F)` call). A future Minecraft version that restructures either method would cause Mixin's `defaultRequire: 1` to throw at mod-init time — an intentional, honestly-accepted tradeoff over silently doing nothing.
- **No automated proof of the actual numeric outcome in a live world.** Source-regression tests prove the mixin *targets* are correct; they cannot prove, without a real game session, that health visibly stops regenerating. That gap is closed by the dedicated-server check (§21, mixin application succeeds without error) and — **[now closed, see §29]** — by the user's own completed manual validation.
- **Rest currently has no Health-restoration call site to protect or break** — an honestly-recorded pre-existing gap (§13), not a defect introduced by this pass.

## 25. Deferred Food 0–100 Work

Entirely untouched and out of scope, exactly as instructed: no Food authority change, no native-scale migration, no new Food item architecture. This correction operates purely at the automatic-regeneration mixin layer, several layers below any future Food-scale decision, and does not presuppose or foreclose any particular resolution of that separate, later work.

## 26. Files Changed

- `src/main/java/zcylas/totality/mixin/FoodDataNaturalRegenerationMixin.java` (new)
- `src/main/java/zcylas/totality/mixin/ServerPlayerPeacefulRegenerationMixin.java` (new)
- `src/main/resources/totality.mixins.json` (modified — two entries added)
- `src/test/java/zcylas/totality/mixin/NaturalFoodRegenerationMixinSourceRegressionTest.java` (new)
- `Context/Audit/TOTALITY_NATURAL_FOOD_REGENERATION_CORRECTION_IMPLEMENTATION_REPORT.md` (this file)

No other file was touched. All pre-existing unrelated dirty/untracked entries are unchanged — confirmed in the accompanying final-verification response.

## 27. Final Status

**Complete**, within the honestly-disclosed limits of §22 and §24. Both automatic Health regeneration paths (Food-driven fast/slow, Peaceful) are suppressed at their exact, source-verified Minecraft 26.2 call sites; explicit healing, starvation, saturation, Food consumption, and general hunger exhaustion are all confirmed preserved; the real `naturalRegeneration` gamerule is never written; 14 new tests added and passing; full suite green at 1405/0/0/0; datagen byte-for-byte unchanged; clean build successful; `git diff --check` clean; nothing staged/committed/pushed; HEAD unchanged; all pre-existing unrelated working-tree entries preserved exactly.

---

## 28. Review-Correction Addendum (2026-08-01)

An independent review found three evidence-quality issues in this pass's tests and report wording. The production implementation itself (both mixins and `totality.mixins.json`) was reviewed and found correct — **nothing about the production mixin behavior changed in this correction**, confirmed by `git diff` showing zero change to either mixin file or to `totality.mixins.json` during this pass. Everything above this section is the original implementation-pass report, left otherwise unmodified except for four narrowly targeted inline wording corrections (marked `[Corrected — see §28]` at each point) and the description of the test suite in §17.

### 28.1 Explicit mixin-transformation evidence obtained this pass

The original report inferred mixin application from successful `runDatagen`/`runServer` completion alone — a real but weaker form of evidence than direct transformation logging, and stated more strongly than that evidence actually supported. During this review-correction pass, `./gradlew runServer` was re-run once with Mixin's verbose transformation logging enabled (`-Dmixin.debug.verbose=true`, passed only via the `JAVA_TOOL_OPTIONS` environment variable for that one diagnostic invocation — no checked-in file was changed to obtain this). The resulting log **explicitly names both mixins being applied to their exact target classes**:

```
[17:46:28] [main/INFO] (FabricLoader/Mixin) Mixing ServerPlayerPeacefulRegenerationMixin from totality.mixins.json into net.minecraft.server.level.ServerPlayer
[17:46:35] [Server thread/INFO] (FabricLoader/Mixin) Mixing FoodDataNaturalRegenerationMixin from totality.mixins.json into net.minecraft.world.food.FoodData
```

Zero exceptions, zero `MixinApply`/`MixinTransformer`/`InvalidInjectionException` errors anywhere in that log; the server reached `Done (0.353s)!`. This is genuine, explicit, per-mixin transformation evidence — stronger than the original report's inferential reasoning — obtained specifically for this correction, not assumed from the original pass's plain (non-verbose) logs, which did not contain per-mixin lines.

**The distinction that still matters, and is not weakened by this stronger evidence:** confirming both mixins were *applied* (their bytecode transformation ran) is not the same as confirming the *suppressed branches execute correctly in a live world* — that requires an actual damaged player with qualifying Food/saturation/Peaceful conditions ticking in real gameplay, which is exactly what the pending manual validation (§22, still not performed) exists to prove.

### 28.2 Corrected test names

| | Old name | New name |
|---|---|---|
| Explicit-healing sentinel | `explicitHealingCallSitesRemainPresentAndUntouched` | `knownExplicitHealingCallSitesRemainPresent` |
| tickRegeneration scope guard | `noProductionFileOutsideTheTwoNewMixinsInterceptsHealOrTickRegeneration` | `noProductionFileOutsideTheDedicatedMixinReferencesTickRegeneration` |

**`knownExplicitHealingCallSitesRemainPresent`** now proves exactly, and only: four named production files each still contain a specific, exact source expression (e.g. `user.heal(amount);`, `player.causeFoodExhaustion(2.5f);`). This is a source-presence regression sentinel — it does not prove the surrounding files are byte-for-byte identical to any prior state, and it does not prove any of these call sites executes correctly, or at all, at runtime. **Separately, and verified directly by this correction pass via `git status --short` against all four files, `git` reports zero changes to any of them** — genuine byte-for-byte-untouched confirmation, but established by `git`, not by the JUnit test, and now stated as such in both the test's own Javadoc and §17 above.

**`noProductionFileOutsideTheDedicatedMixinReferencesTickRegeneration`** now proves exactly, and only: no production file other than `ServerPlayerPeacefulRegenerationMixin.java` contains the literal string `"tickRegeneration"` (the scan also excludes `FoodDataNaturalRegenerationMixin.java`, vacuously — that file never contained the string). The test never inspects `heal(` call sites and never proves anything about heal interception, global or otherwise; the corrected Javadoc states this explicitly.

### 28.3 Confirmations

- **No production behavior changed during this review correction.** `git diff` for `FoodDataNaturalRegenerationMixin.java`, `ServerPlayerPeacefulRegenerationMixin.java`, and `totality.mixins.json` is empty — none of the three was touched by this pass.
- **Manual in-game validation remains pending** — not performed by this pass, not claimed complete anywhere in this report. The corrected §21/§22 wording now explicitly names it as the decisive behavioral proof still outstanding.
- **Food remains native 0–20.** Unaffected by this correction (a test-name/report-wording pass only).
- **Explicit healing, starvation, saturation, and general hunger exhaustion remain unchanged** — reconfirmed by the same `git diff`-verified-empty check in §28.2 for the four explicit-healing files, and by the untouched-mixin confirmation above for the two suppression call sites.
- **Final validation totals for this correction pass:** focused and full-suite totals were re-run after these corrections and are reported in the accompanying final response, together with the datagen byte-for-byte comparison and `git diff --check` result for this pass specifically.
- **Nothing was staged, committed, or pushed** by this correction pass.

---

## 29. Final Manual Validation and Closure (2026-08-01)

The user completed the practical in-game validation checklist from Part 9 of the implementation task and accepted the results. This section records exactly what was established — no more, no less — and closes the one item §22/§24/§28.3 left open.

### 29.1 Results established by the user's manual validation

1. **Vanilla automatic Food-based Health regeneration no longer occurs** in Survival — neither the fast/saturated nor the slow/well-fed vanilla path restores Health automatically.
2. **Peaceful automatic Health regeneration no longer occurs** — a damaged player on Peaceful difficulty no longer heals passively over time.
3. **Peaceful Food/saturation restoration remains functional** — the sibling statements in `ServerPlayer#tickRegeneration()` that this correction deliberately left untouched (saturation increase every 20 ticks, Food-level increase every 10 ticks) continue to work as before.
4. **Explicit healing paths tested in-game remained functional** — healing that is not routed through either suppressed vanilla call site continued to restore Health normally.
5. **Eating and ordinary Food behavior remain functional** — consuming food items still raises Food level normally.
6. **Saturation remains functional** — unaffected by this correction, confirmed in play.
7. **Sprinting, jumping, and ordinary hunger-exhaustion-generating actions remain functional** — vanilla hunger exhaustion from movement/actions was not disrupted.
8. **An initial apparent concern that hunger exhaustion had stopped working was investigated and disproven.** It had not stopped — see §29.2 for the explanation.
9. **No crashes, mixin failures, or other relevant runtime errors were observed** during manual validation.

### 29.2 The apparent hunger-pacing observation, investigated and explained

During validation, visible Food appeared to decrease more slowly than expected, which initially looked like hunger exhaustion had been broken. Investigation established this is not a regression, for two compounding, already-documented-in-source reasons:

- **Hidden saturation is consumed first.** Vanilla's own exhaustion→Food conversion (`FoodData#tick`, the block preceding the `naturalRegen` local — see §5.1, §6, §7) drains `saturationLevel` before it ever touches `foodLevel`. Saturation is not shown on the vanilla Food HUD; a player perceives no visible Food loss at all while saturation absorbs accumulated exhaustion, exactly as in unmodified vanilla. This behavior is untouched by this correction.
- **The removed automatic-regeneration branches no longer add their own regeneration-specific exhaustion cost.** Before this correction, every time the fast (`+saturationSpent`, up to 6.0) or slow (`+6.0`) vanilla Health-regen branch fired, it also charged its own `addExhaustion(...)` — extra exhaustion the player never directly caused through their own actions, layered on top of ordinary movement/action exhaustion. With both branches now unreachable (§6, §7, §10), that extra exhaustion source is gone, exactly as intended by suppressing "the regeneration-specific hunger exhaustion charges located inside those healing branches" (§12) — its removal is not a separate defect, it is the direct, correct consequence of the accepted policy.

Together, these mean the *rate* at which visible Food (not saturation) ticks down is now slower than the pre-correction, unmodified-vanilla baseline — because part of what used to consume it (the suppressed regeneration branches' own exhaustion charges) no longer exists. **Ordinary hunger exhaustion from movement, sprinting, jumping, combat, and other vanilla sources is unaffected and still fully functional** — nothing about `Player#causeFoodExhaustion(float)` or any of its call sites was touched by either mixin. The slower visible pacing is an accepted, expected balance consequence of the Health-recovery policy itself, not an unintended side effect and not evidence of a broken mechanic.

**This task does not rebalance hunger consumption or pacing.** No exhaustion constant, no `FoodConstants` value, no drain rate, and no threshold was added, removed, or changed anywhere in this pass. Whether the new, slightly slower Food-depletion pacing should itself be tuned is explicitly deferred to the future Food 0–100 redesign (§25) — a separate, later, and not-yet-begun piece of work — not something this narrow correction is scoped to address.

### 29.3 Healing-spell clarification

Totality currently has **no implemented D&D healing spell**. This is not a gap in this task's validation coverage: there is no such spell in the codebase to manually test, so none was tested, and none is claimed to have been tested. When a D&D healing spell (or any other future explicit healing implementation) is eventually built, it should be authored to heal through its own explicit call site — exactly like `HealingPotionItem`, `HealEffect`, `AlchemyEffects`, and `Conditions.REGENERATING` already do (§13) — and will remain entirely outside the two narrow interception sites this correction adds, which target only `FoodData#tick`'s vanilla regeneration branches and `ServerPlayer#tickRegeneration`'s vanilla Peaceful heal call. Every existing explicit healing implementation remains unaffected by this architecture, confirmed unchanged both in this pass and the prior review-correction pass (§13, §28.2).

### 29.4 Evidence tiers, stated precisely

Four genuinely distinct kinds of evidence now support this correction, none substituting for another:

| Tier | What it establishes | Where recorded |
|---|---|---|
| **Mapped-source evidence** | The exact Minecraft 26.2 `FoodData#tick`/`ServerPlayer#tickRegeneration`/`LivingEntity#heal` source, extracted directly from the project's own Loom-cached sources jar | §5, source-evidence index |
| **Source-regression tests** | The mixins' exact targets/annotations/descriptors/configuration match what was authored; no broad interception pattern was introduced; known explicit-healing expressions remain textually present | §17, §28.2 |
| **Startup/transformation evidence** | Mixin configuration loaded and both target classes were explicitly transformed — confirmed by name in verbose Mixin logs (`Mixing ServerPlayerPeacefulRegenerationMixin ... into ... ServerPlayer`, `Mixing FoodDataNaturalRegenerationMixin ... into ... FoodData`, §28.1) — with zero injection/classloading errors | §21, §28.1 |
| **In-world behavioral evidence** | The suppressed branches do not fire in real gameplay, sibling Peaceful Food/saturation restoration still does, explicit healing still works, and the visible pacing change is explained and expected | **§29.1–§29.2, this section — now complete** |

None of the first three tiers alone proves runtime behavior; together with this section's completed §29.1–§29.2, all four tiers are now satisfied.

### 29.5 Final confirmations

- Food remains native 0–20 — untouched by manual validation or this closure.
- Saturation, starvation, and general (non-regeneration) hunger exhaustion remain unchanged — confirmed both by source inspection/tests and now by manual play.
- Explicit healing remains fully outside the interception scope of both mixins.
- Food 0–100 was not started, and is not started by this closure.
- No production file was modified to produce this section — it is a report-only update, exactly like the §28 review-correction pass before it.

### 29.6 Final status

- Implementation: **complete.**
- Independent review corrections: **complete** (§28).
- Automated validation: **complete** (§18–§20, re-confirmed this pass — see the accompanying final response for this pass's exact totals).
- Explicit mixin-transformation evidence: **obtained** (§28.1).
- Manual gameplay validation: **complete** (§29.1–§29.2).
- **No unresolved blocker remains.**
- **Task ready for commit and push.**
