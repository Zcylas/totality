# TOTALITY — FOOD, FATIGUE, EXHAUSTION, AND DORMANT GENERIC PLAYER RESOURCE STATE — COMPREHENSIVE AUDIT

**Date:** 2026-07-31
**Type:** Read-only architecture and implementation audit. No production or test source was modified. No commit, stage, or push occurred.
**Branch:** `feature/general-resource-api`
**Committed HEAD at audit time:** `07d5200b1430d59da83707460f1154aab3978599` ("Polish player HUD and chat layout")
**Scope:** `src/main/java/zcylas/totality/**`, `src/test/java/zcylas/totality/**`, `Context/Audit/**`, and MC 26.2's own mapped/decompiled dependency source (via the real `minecraft-merged-deobf-26.2.jar` in the Gradle/Loom cache, `javap`-disassembled — never assumed from memory of older Minecraft versions).

---

## Evidence classification legend (used throughout this document)

Every substantive claim below is tagged with one of the following labels. Where a claim mixes categories, both labels are given.

| Tag | Meaning |
|---|---|
| **[CODE FACT]** | Verified by directly reading the cited repository source line(s) during this audit or by a research agent whose output is quoted/paraphrased with exact paths. |
| **[VANILLA FACT]** | Verified by decompiling/disassembling the real, current MC 26.2 mapped jar (`minecraft-merged-deobf-26.2.jar`) during this audit. Never repository code. Never assumed from an older Minecraft version. |
| **[HISTORICAL DOC]** | A statement recorded in an older audit/design document, presented as *what that document said at the time*, not necessarily still true today. |
| **[ACCEPTED DESIGN]** | A decision recorded in a canonical, currently-authoritative design document (`TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`, `TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md`, or a dedicated document those two defer to) that has **not** yet been implemented in code — a target state, not a current fact. |
| **[INFERENCE]** | A conclusion this audit draws from combining two or more **[CODE FACT]**/**[VANILLA FACT]** findings, not itself a single directly-read line. |
| **[RECOMMENDATION]** | This audit's own proposal. Not implemented. Not yet accepted design. |
| **[UNKNOWN]** | Explicitly not established by this audit — flagged rather than guessed. |

---

## 1. Executive Summary

Totality's player Food is, today, **exactly and only** vanilla Minecraft's native `FoodData` on its native `0–20` scale **[CODE FACT]**. A Generic Player Resource API adapter (`FoodResourceAdapter`) exists and is registered, but it is strictly **query-only** and **presentation-only** — it exposes a `×5` display conversion (`0–20` mechanical → `0–100` shown to the player) that was deliberately designed, in the canonical Resource API document itself, to **never become the authoritative scale** **[ACCEPTED DESIGN, confirmed still true by CODE FACT]**. The Phase 2A implementation report that built this adapter states outright: *"A future **mechanical** Food `0–100` scale... [is] explicitly **later scope** — none of that was designed, implemented, or implied by this phase."* This audit's central finding is therefore that **the "authoritative Food 0–100" migration this task is scoping has never been started, and is not merely an extension of existing work — it is new architecture on top of a system explicitly designed to keep the mechanical scale at 0–20 indefinitely.**

A second major finding: Totality's stated design intent that *"ordinary vanilla Food-based health regeneration should be disabled"* **[ACCEPTED DESIGN, per the task prompt itself]** is **not implemented**. No gamerule is changed, no mixin targets `FoodData`/health-regen logic, and the one plausibly-relevant mixin (`LivingEntityHurtMixin`) was read in full during this audit and is unrelated (sword-parry/shield-blocking only) **[CODE FACT]**. Vanilla's natural regeneration (both the "well-fed" slow-regen and "saturated" fast-regen paths) is fully active today, gated only by vanilla's own default-`true` `natural_health_regeneration` gamerule.

Third: **Fatigue is entirely absent from code** — zero references anywhere in `src/main/java` or `src/test/java` **[CODE FACT, cross-agent confirmed]** — consistent with its design-document status of "RECOVERED — PARTIAL... a confirmed DIRECTION with UNRESOLVED FORMULAS" **[ACCEPTED DESIGN]**. What *does* exist is `ExhaustionManager`/`ExhaustionState` (`api/rpg/combat/exhaustion/`), a real, shipping, Stamina-derived short-timescale combat mechanic (30%/0% thresholds, movement/attack penalties, regen throttling) that the canonical documents explicitly warn **must not be confused** with the future long-term Fatigue/Rest Need concept **[ACCEPTED DESIGN + CODE FACT]**. This audit independently confirms that warning is well-founded: the naming collision is real, and this system has a confirmed dead-code bug (its own join-hook is never wired) and zero automated test coverage.

Fourth: the Generic Player Resource registry currently contains exactly **7 resources** (Health, Food, Breath, Mana, Stamina, Spell Slots, Rage), all `EXTERNAL_ADAPTER`-authority, all frozen at mod init **[CODE FACT]**. Every other resource named in Totality's design corpus (Thirst, Fatigue/Rest Need, Sanity, Ki, Solar Charge, Chakra, Pact Magic, Cursed Energy, Reiatsu) exists **only** in one design document (`TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md`) and has **zero code footprint** — no `PlayerResourceIds` constant, no adapter, no definition **[CODE FACT]**. Registration itself is proven inert by multiple independent test suites and by direct reading of the registration/query code paths: nothing can currently be accidentally instantiated, synchronized, or persisted for an ungranted resource.

This audit does not implement anything. It establishes ground truth so that the six-step future sequence in the task's PURPOSE section can be planned against reality rather than assumption.

---

## 2. Audit Scope and Method

This audit was conducted read-only against the local repository and the locally-cached MC 26.2 mapped dependency jar only. No GitHub plugin was used. Five parallel research passes (each independently re-verifying claims against current source, not relying on memory of prior sessions) covered: (A) current Food architecture end-to-end, (B) MC 26.2 vanilla Food/health/regeneration mechanics via direct decompilation, (D/E/F) Fatigue, Totality's own Exhaustion system, and their collision risk, (G) the Generic Player Resource registry and dormancy safety, (J) the existing test inventory. This author additionally read the canonical design documents directly and performed one targeted manual verification (`LivingEntityHurtMixin.java`) to resolve an ambiguity none of the automated passes had fully closed. Every finding below traces to a specific file, line, or decompiled class — no evidence was fabricated, and every open question is stated as such rather than silently resolved.

---

## 3. Repository Checkpoint

Verified at the start of this audit:

| Check | Result |
|---|---|
| Branch | `feature/general-resource-api` |
| HEAD | `07d5200b1430d59da83707460f1154aab3978599` |
| HEAD subject | "Polish player HUD and chat layout" |
| `origin/feature/general-resource-api` | Identical to HEAD — **0 ahead / 0 behind** |
| Staged changes | None (`git diff --cached` empty) |

Repository-wide `git status --short` at audit start recorded 27 pre-existing modified files (all generated JSON/`build.gradle`, unrelated to this task) and 37 pre-existing untracked entries (review-bundle ZIPs, screenshots, logs, caches, one prior unrelated audit doc `TOTALITY_HUD_AND_MOB_DISPLAY_AUDIT.md`) — 64 entries total, matching every prior session's baseline exactly. This state is preserved unchanged by this audit; see §33 (Final Verification, in the accompanying response) for the post-audit comparison.

---

## 4. Sources and Canonical Documents Reviewed

Read directly by this audit (not delegated), with authority classification per each document's own stated status:

| Document | Status as stated in the document | Relevance |
|---|---|---|
| `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` | **CANONICAL** — "implementation-ready architectural specification," 2026-07-13 | Defines Food/Health/Temperature as `EXTERNAL_ADAPTER` resources with owner-authoritative state and a presentation-only `×5` conversion; defines Universal vs Source-Specific resources; defines the grant/entitlement rule (§4.4/§4.5) |
| `Context/Audit/TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md` | Second in the doc's own authority order | Confirms Rest/Fatigue integration remains a P3, not-yet-designed follow-up item |
| `Context/Audit/TOTALITY_REST_AND_FATIGUE.txt` | "RECOVERED — PARTIAL" for Fatigue/Rest Need (§10); "RECOVERED — VERIFY AGAINST CODE" for Rest itself | The single authoritative source for Fatigue's intended shape (Stamina ≠ Rest Need ≠ Fatigue ≠ Sleep Debt) and its explicit warning not to conflate it with the existing `ExhaustionManager` |
| `Context/Audit/TOTALITY_FOOD_ECOSYSTEM_CURRENT.txt` | "FOURTH REVISION PASS," Diet (§12) explicitly "RECOVERED FRAGMENTS, STILL NOT IMPLEMENTABLE," Survival (§13) partial | Confirms Diet/nutrition/food-group mechanics are design-only and explicitly not ready to implement; establishes Cooking/Diet build-order dependencies |
| `Context/Audit/TOTALITY_RESOURCE_API_PHASE_2A_HEALTH_FOOD_IMPLEMENTATION_REPORT.md` | "READY TO COMMIT," manual-verified, 214/214 tests passing (as of 2026-07-19) | The single most load-bearing document for this audit — the actual implementation record of the Food adapter, including its explicit "later scope" boundary around mechanical 0–100 |
| `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md` | Living implementation-status log, updated through Phase 2E (2026-07-20) | Confirms Health/Food/Breath/Mana/Stamina/Spell Slots/Rage are the seven production resources today |
| `Context/Audit/TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md` | Design document; "finalizes the previously-undesigned Unlock, Access and Entitlement API" | Source of every "exotic" resource identifier (Thirst, Fatigue/Rest Need, Sanity, Ki, Solar Charge, Chakra, Pact Magic, Cursed Energy, Reiatsu) found anywhere in the design corpus; confirms no formal entitlement/grant machinery exists in code |
| `Context/Audit/TOTALITY_IMPLEMENTATION_AUDIT_REPORT_2026-07-13.md` | Read-only audit, dated 2026-07-12 | Historical corroboration: independently found the same `ExhaustionManager` naming-collision risk (its own finding REST-19) and the same Fatigue absence (REST-18) roughly three weeks before this audit — this audit's own fresh code search reaches the identical conclusion today, i.e. nothing has changed on either front in the interim |
| `Context/Audit/TOTALITY_BALANCE_DESIGN.txt` | Balance/numbers reference | Checked for Food/Fatigue/Exhaustion numeric content — **zero matches**, confirmed by direct grep; Food scale/balance has never been addressed here |
| `Context/Audit/TOTALITY_SHARED_CROSS_SYSTEM_FOUNDATIONS.txt` | Cross-system terminology reference | Confirms the unrelated "Exposure" (route/dose/duration hazard) concept, useful only as a negative check that it does not collide with Exhaustion terminology |

**Superseding note applied throughout this document, per the task's explicit authority-order instruction:** where `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` (canonical) and the Phase 2A implementation report agree, that combination is treated as the current accepted design. Where an older document (e.g. the 2026-07-12 implementation audit) and a newer one disagree, the newer one governs — in practice, on every topic this audit touches, the two agree with each other, which is itself informative: nothing about Food's architecture has changed since Phase 2A shipped.

---

## 5. Current Food Architecture

**[CODE FACT unless noted]**

Player Food has exactly one authoritative store: vanilla's own `net.minecraft.world.food.FoodData`, attached to every `Player`/`ServerPlayer` by vanilla itself. Totality has never created a competing store. The Generic Player Resource API's Food adapter is a thin, read-only, presentation-oriented wrapper around it:

```java
// src/main/java/zcylas/totality/api/rpg/resources/external/FoodResourceAdapter.java
public final class FoodResourceAdapter implements ExternalPlayerResourceAdapter {
    public static final Identifier ID = PlayerResourceIds.FOOD_ADAPTER;
    public static final int NATIVE_MAXIMUM = 20;

    @Override
    public ResourceQueryResult snapshot(Player player, PlayerResourceDefinition definition) {
        int level = player.getFoodData().getFoodLevel();
        return new ResourceQueryResult.Success(new ResourceSnapshot(definition.id(), level, NATIVE_MAXIMUM, definition.unitScale()));
    }

    @Override
    public Set<ExternalResourceOperationSupport> supportedOperations() {
        return Set.of(ExternalResourceOperationSupport.QUERY);
    }

    @Override
    public ExternalResourceClientMirrorMode clientMirrorMode() {
        return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
    }
}
```

Key architectural facts:

- **Model**: `ResourceModel.SCALAR`, `unitScale = 1` (Food is integral; no fractional value exists anywhere).
- **Authority**: `ResourceStateAuthority.EXTERNAL_ADAPTER` — Food **never** gets a `PlayerResourceStateComponent` entry. `PlayerResourceStateComponent.instantiateScalar`/`instantiatePartitioned` actively **reject** the Food identifier (see §21).
- **Operations declared**: `QUERY` only. No `SPENDABLE`, `RESTORABLE`, or `DIRECT_DRAIN` capability exists anywhere for Food.
- **Maximum**: hardcoded `NATIVE_MAXIMUM = 20` — vanilla has no `getMaxFoodLevel()` accessor, so this is a Totality-authored constant mirroring vanilla's own hardcoded ceiling (confirmed independently correct against the real vanilla `FoodConstants.MAX_FOOD = 20`, see §7).
- **Saturation/exhaustion are deliberately excluded** from the adapter's snapshot — the class's own Javadoc states this is intentional ("Saturation and exhaustion remain owner-specific state/metadata... deliberately never read here"), and `ResourceSnapshot`'s record shape structurally cannot carry them (proven by a dedicated test, `FoodResourceAdapterConversionTest.saturationAndExhaustionHaveNoFieldOnResourceSnapshot`).
- **Registered identifiers** (`PlayerResourceIds.java`): `FOOD = totality:food`, `FOOD_ADAPTER = FOOD` (adapter ID intentionally equals resource ID, matching every other adapter's convention).
- **Display conversion**: `ResourceDisplayConversion.HEALTH_FOOD = new ResourceDisplayConversion(5, 1)` — shared with Health, presentation-only. The class's own Javadoc states the conversion "must never be used to derive an authoritative mechanical value."

**No `TotalityFoodItem`, `ConsumableItem`, or `AbstractConsumable` base class exists anywhere** — confirmed by a repo-wide grep (zero matches) and a glob for `**/item/**/*Food*.java` (zero matches). Every current food-adjacent item extends `net.minecraft.world.item.Item` directly and attaches vanilla `FoodProperties` at registration. See §12 for the full item inventory.

---

## 6. Food Read/Write Inventory

**[CODE FACT]**, exact citations from the Food architecture research pass, cross-checked against this author's own reading of `FoodResourceAdapter.java` and `ProductionResourceDefinitions.java`.

| # | Path | Class / Method | Reads / Writes / Derives / Displays / Syncs / Persists | Assumes 0–20? | Migration work needed? |
|---|---|---|---|---|---|
| 1 | `client/resource/MinecraftNativeResourceAccess.java:47-49` | `foodLevel()` | Reads (client, raw int) | Yes (implicit) | Yes — feeds the client Resource API path |
| 2 | `client/renderer/hud/TotalityHudRenderer.java:195` | render lambda | Reads directly, **bypasses Resource API** | Yes | Yes — this is the bar's fill-ratio source |
| 3 | `client/renderer/hud/TotalityHudRenderer.java:200` | `hungerPct = hunger / 20.0` | Derives (bar fill %) | Yes, hardcoded `20.0` literal | Yes |
| 4 | `client/renderer/hud/TotalityHudRenderer.java:267-268` | fallback display calc | Derives (fallback only) | Yes, hardcoded `20` literal | Yes |
| 5 | `networking/inventory/InventoryActionHandler.java:180,185-188` | eat-eligibility gate | Reads `needsFood()` directly, **bypasses Resource API** | No (boolean gate) | Yes — server-side eat gate |
| 6 | `api/rpg/resources/external/FoodResourceAdapter.java:29` | `NATIVE_MAXIMUM` | Defines ceiling | Yes, explicit constant | Yes — this constant *is* the current authority boundary |
| 7 | `api/rpg/resources/external/FoodResourceAdapter.java:38` | `snapshot()` | Reads (the one Resource-API-sanctioned read) | Yes | Yes |
| 8 | `api/rpg/resources/client/NativeClientResourceReader.java:52-53` | `query()` | Reads (client Resource API path) | Yes | Yes |
| 9 | `api/rpg/resources/client/NativeResourceAccess.java:25-26` | interface `foodLevel()` | Declares contract; Javadoc explicitly documents "0-20 scale" | Yes, documented | Yes |
| 10 | `api/rpg/resources/ProductionResourceDefinitions.java:132-146` | `registerDefinitions()` | Registers definition metadata | Descriptive only | Yes — definition's `authoredBaseMaximum` |
| 11 | `api/rpg/resources/external/ExternalResourceClientMirrorMode.java:12-16` | enum Javadoc | Documents native sync reliance | N/A | Yes — sync strategy decision needed |
| 12 | `screen/inventory/InventoryItemDetail.java:93,154-163` | `drawStats()` | Displays item-level nutrition (×5 converted) and saturation (raw) | Nutrition converted, saturation not | Yes — tooltip formatting |
| 13 | `client/resource/parity/ClientResourceParityInspectionCommand.java:107-108` | dev command | Reads (indirect, via item 8) | Indirect | No — dev-only tool |

**No hits anywhere for `setFoodLevel`, `setSaturationLevel`, `addExhaustion`, `getExhaustionLevel`, or `exhaustionLevel`** in `src/main/java` **[CODE FACT, explicit repo-wide grep, zero matches]** — Totality currently never *writes* to vanilla Food state at all; every citation above is a read.

**Central finding [INFERENCE]:** the Resource API today supplies only the *numeric label* on the Food HUD bar. The bar's *visual fill percentage* (item 3) and the server's *eat-eligibility gate* (item 5) both still bypass the Resource API entirely and read `FoodData` directly. A future migration to an authoritative 0–100 scale would need to touch **at minimum** items 1–5, 7–11 above — not merely the adapter.

---

## 7. Minecraft 26.2 Vanilla Food Mechanics

**[VANILLA FACT — from `jar xf` + `javap -p -c -constants` against `minecraft-merged-deobf-26.2.jar`]**. Class is still at `net.minecraft.world.food.FoodData` in 26.2 (confirmed via `jar tf | grep -i food`, not renamed).

### 7.1 `FoodData` fields and defaults

| Field | Type | Default |
|---|---|---|
| `foodLevel` | `int` | `20` |
| `saturationLevel` | `float` | `5.0f` |
| `exhaustionLevel` | `float` | `0.0f` |
| `tickTimer` | `int` | `0` |

### 7.2 `FoodConstants` (companion class, all `public static final` with `ConstantValue` attributes — direct constant-pool read)

```
MAX_FOOD = 20                       MAX_SATURATION = 20.0f
START_SATURATION = 5.0f             SATURATION_FLOOR = 2.5f
EXHAUSTION_DROP = 4.0f              HEALTH_TICK_COUNT = 80
HEALTH_TICK_COUNT_SATURATED = 10    HEAL_LEVEL = 18
SPRINT_LEVEL = 6                    STARVE_LEVEL = 0
EXHAUSTION_HEAL = 6.0f              EXHAUSTION_JUMP = 0.05f
EXHAUSTION_SPRINT_JUMP = 0.2f       EXHAUSTION_MINE = 0.005f
EXHAUSTION_ATTACK = 0.1f            EXHAUSTION_WALK = 0.0f
EXHAUSTION_CROUCH = 0.0f            EXHAUSTION_SPRINT = 0.1f
EXHAUSTION_SWIM = 0.01f
```
`saturationByModifier(int nutrition, float modifier) = nutrition * modifier * 2.0f`. `addExhaustion(float)` caps at a literal `40.0f` (not a named constant).

### 7.3 `FoodData.add(int nutrition, float saturation)` — the core mutation

```
foodLevel = Mth.clamp(foodLevel + nutrition, 0, 20)
saturationLevel = Mth.clamp(saturationLevel + saturation, 0.0f, (float) foodLevel)
```
**Important precision note [VANILLA FACT]**: saturation is clamped to the **current `foodLevel`**, not to `MAX_SATURATION=20.0f` directly. A migration to 0–100 Food must re-derive this relationship, not just multiply the cap by 5.

`eat(int nutrition, float modifier)` computes `saturationByModifier` then calls `add()`. `eat(FoodProperties)` calls `add(props.nutrition(), props.saturation())` directly — `FoodProperties.saturation()` is **already the precomputed final value**, not a raw modifier (see §7.6).

### 7.4 `FoodData.tick(ServerPlayer)` — the complete per-tick state machine

Called once per tick from `ServerPlayer.doTick()` **[VANILLA FACT, direct bytecode call-site read]**. Full logic, in order:

1. **Exhaustion → saturation/food conversion**:
   ```
   if (exhaustionLevel > 4.0f) {
       exhaustionLevel -= 4.0f;
       if (saturationLevel > 0.0f) saturationLevel = max(saturationLevel - 1.0f, 0.0f);
       else if (difficulty != PEACEFUL) foodLevel = max(foodLevel - 1, 0);
   }
   ```
2. `naturalRegen = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)` (Boolean).
3. **Fast/saturated regen** — `if (naturalRegen && saturationLevel>0 && isHurt() && foodLevel>=20)`: every 10 ticks, `heal(min(saturation,6)/6)`, `addExhaustion(min(saturation,6))`.
4. **Slow/well-fed regen** (`else if`) — `if (naturalRegen && foodLevel>=18 && isHurt())`: every 80 ticks, `heal(1.0f)`, `addExhaustion(6.0f)`.
5. **Starvation** (`else if`) — `if (foodLevel<=0)`: every 80 ticks, difficulty-gated `hurtServer(..., starve(), 1.0f)`. Damage lands if `health>10.0` OR `difficulty==HARD` OR (`health>1.0` AND `difficulty==NORMAL`) — net effect: **Easy floors starvation at 10 HP, Normal at 1 HP, Hard has no floor.**

Regeneration table:

| Path | Gate | Interval | Effect |
|---|---|---|---|
| Fast (saturated) | gamerule true, sat>0, hurt, food≥20 | 10 ticks | `heal(min(sat,6)/6)`, `+exhaustion` |
| Slow (well-fed) | gamerule true, food≥18, hurt | 80 ticks | `heal(1.0)`, `+6.0 exhaustion` |
| Starvation | food≤0 | 80 ticks | difficulty-gated 1.0 damage |

**There is no separate "healFromFood" method** on `Player`/`LivingEntity` in 26.2 — the entire mechanic is encapsulated inside `FoodData.tick()` itself **[VANILLA FACT]**, a structural fact worth flagging since older-version documentation may describe a different call shape.

### 7.5 Persistence and sync (vanilla)

NBT keys (via `ValueInput`/`ValueOutput`, MC 26.x's newer serialization interfaces, not raw `CompoundTag`): `foodLevel` (int, default 20), `foodTickTimer` (int, default 0), `foodSaturationLevel` (float, default 5.0), `foodExhaustionLevel` (float, default 0.0).

**No `ClientboundSetFoodPacket` exists** — food is folded into `ClientboundSetHealthPacket(float health, int food, float saturation)`, sent event-driven (dirty-checked every tick against `lastSentHealth`/`lastSentFood`/`lastFoodSaturationZero`) from `ServerPlayer.doTick()`, not on a fixed timer.

### 7.6 `FoodProperties` and `Consumable` (26.2-specific shape)

```java
public final class FoodProperties extends Record implements ConsumableListener {
    int nutrition; float saturation; boolean canAlwaysEat;
}
```
Codec fields: `nutrition`, `saturation`, `can_always_eat` (optional, default false). **`FoodProperties.saturation()` is the already-computed absolute restore amount** — the raw 0–1 *modifier* only exists on `FoodProperties.Builder.saturationModifier(float)`, converted at `build()` time via `saturationByModifier`. This is a real naming/shape difference from older MC versions worth flagging for anyone authoring against older reference material.

**Eat duration/animation is NOT on `FoodProperties`** in 26.2 — it lives on a separate item data component, `net.minecraft.world.item.component.Consumable` (`consumeSeconds` default `1.6f`, `animation`, `sound`, particles). Any future reauthoring of food items must account for this split across two components.

### 7.7 Sprint gating and exhaustion funnel (vanilla)

`Player.hasEnoughFoodToDoExhaustiveManoeuvres() = foodData.hasEnoughFood() || abilities.mayfly`, where `FoodData.hasEnoughFood() = foodLevel > 6.0f` (i.e. **food level 7+** required to sprint). `LocalPlayer.isSprintingPossible(boolean)` consults this client-side. `Player.causeFoodExhaustion(float)` is the single funnel every vanilla gameplay action goes through server-side (`level().isClientSide()` guard present) before calling `foodData.addExhaustion(f)` — confirmed melee attacks apply `0.1f` (matches `EXHAUSTION_ATTACK`).

### 7.8 Gamerule and difficulty

`GameRules.NATURAL_HEALTH_REGENERATION`, key `"natural_health_regeneration"`, **default `true`**. Difficulty: `PEACEFUL` suppresses only the food-level-drain half of the exhaustion conversion (saturation still drains); starvation floor is HARD=none, NORMAL=1HP, EASY=10HP.

---

## 8. Health Regeneration Audit

**Totality's design intent, stated in this task's own PURPOSE section: "ordinary vanilla Food-based health regeneration should be disabled, with Health restored through rests, spells, rituals, potions, medicine, abilities, effects, and other authored systems."** This is treated here as **[ACCEPTED DESIGN]** exactly as the task frames it, not yet confirmed as implemented — auditing that gap is the point of this section.

| Question | Answer |
|---|---|
| 1. Is vanilla natural regeneration from Food actually disabled? | **No — [CODE FACT].** Nothing in the repository sets `GameRules.NATURAL_HEALTH_REGENERATION` to `false`. Repo-wide grep for `NATURAL_HEALTH_REGENERATION`/`naturalRegeneration`/`natural_health_regeneration` in `src/main/java`: **zero matches.** |
| 2. Which exact code disables or intercepts it? | **None found.** `totality.mixins.json`'s complete mixin list (read directly by this audit) contains no mixin targeting `FoodData`, `Player.aiStep`, or any health-regeneration method. The one mixin whose name suggested it might be relevant, `src/main/java/zcylas/totality/mixin/LivingEntityHurtMixin.java`, was read in full during this audit — it only overrides `LivingEntity.isBlocking()` to fix a sword-parry/shield-blocking animation conflict; it has nothing to do with Food, healing, or regeneration. |
| 3. Does the `naturalRegeneration` gamerule still affect anything? | **Yes, fully — [VANILLA FACT + INFERENCE].** Since nothing changes it, it remains at vanilla's own default (`true`) and vanilla's fast/slow regen paths (§7.4) run exactly as stock Minecraft would. |
| 4. Are any alternate/duplicate regeneration paths active? | **[UNKNOWN — not fully verified.]** No custom Totality healing-over-time mechanic was found by any research pass performed this audit (Rest recovery, D&D Potion of Healing, and rune `HealEffect` are all instant/discrete, not passive regen — confirmed by the test-inventory pass, §23). However, this audit did not exhaustively search every ability/spell/effect class for a passive-heal-over-time pattern; only the specific systems named in the task and turned up by the five research passes were checked. |
| 5. Can high Food still heal the player under any current circumstance? | **Yes — [INFERENCE from §7.4 + Q1/Q2].** Since nothing disables the vanilla mechanic, a player at food≥18 (well-fed) or food≥20 with saturation (fully saturated) who is hurt will regenerate exactly as in stock Minecraft, unaffected by any Totality system. |
| 6. Do peaceful difficulty or vanilla special cases bypass Totality's intended rule? | **Not applicable — the rule itself isn't implemented**, so there is nothing to bypass. Vanilla's own peaceful-difficulty/gamerule interactions (§7.8) apply exactly as vanilla intends. |
| 7. Which tests prove the current behavior? | **None.** The test-inventory research pass found **zero tests** anywhere asserting natural regeneration is disabled, or asserting it is enabled — a repo-wide grep of `src/test/java` for `naturalRegeneration`/`NATURAL_REGENERATION`/`GameRules` returned zero matches. |
| 8. What migration risks exist when Food becomes 0–100? | **[RECOMMENDATION-adjacent inference]**: if natural regeneration is disabled *before* or *alongside* a 0–100 migration, the migration is simpler (no regen-threshold rescaling needed). If it is disabled *after*, whoever implements the disable must also decide whether the vanilla `HEAL_LEVEL=18`/`foodLevel>=20` thresholds (or their eventual 0–100 equivalents) still matter for anything else Totality might reuse (e.g. a future "well-fed" buff), since disabling regen doesn't remove those literal thresholds from vanilla's own code — it only prevents the `heal()` call from firing. |

**Audit conclusion for this section:** the accepted design's central health-regeneration rule is **not currently implemented in any form**. This is one of the highest-priority gaps this audit identifies, independent of the Food-scale question, and should likely be resolved (or explicitly deferred with a written decision) before or alongside any Food 0–100 work, since both touch the same `FoodData.tick()` vanilla method conceptually.

---

## 9. Food Persistence and Synchronization

**[CODE FACT + VANILLA FACT]**. Food persistence is **100% vanilla** — no Totality code reads or writes food-related NBT anywhere. Explicitly checked and confirmed absent: `api/core/component/PlayerComponentEvents.java`, `api/core/component/RespawnStrategy.java`, `mixin/MixinServerPlayer.java` (zero matches for `FoodData`/`getFoodLevel`/`setFoodLevel` in the entire mixin package). Food relies purely on vanilla's automatic `FoodData` NBT save/load (§7.5) and vanilla's own `ClientboundSetHealthPacket`-folded sync (§7.5) — `ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION` is Food's declared mode, and a dedicated test (`ResourceSyncManagerEligibilityTest`) **proves** Health/Food/Breath are structurally *ineligible* for Totality's generic Resource-API sync system, precisely because they're native-synchronized. `networking/resource/ResourceSyncManager.java` was checked directly — zero Food references.

**Migration implication [INFERENCE]:** an authoritative 0–100 Food scale that is no longer "the same vanilla int, just displayed ×5" would need an explicit decision about whether to (a) keep riding vanilla's `ClientboundSetHealthPacket` by keeping the *mechanical* value at 0–20 forever (Option 1/3 below), or (b) introduce a new sync path, since vanilla's own packet cannot carry a Totality-authored 0–100 mechanical value without Totality re-deriving it client-side anyway.

---

## 10. Food Death/Respawn/Dimension Behavior

**[CODE FACT]**. No Totality code touches Food on respawn, clone, or dimension transfer. `RespawnStrategy.java` (Totality's own component-copy-on-respawn interface) has zero food references. Since Food is `EXTERNAL_ADAPTER`-authority, not `GENERIC_COMPONENT`, it was never in scope for Totality's component clone-copy machinery to begin with — it is handled entirely by vanilla's own `Player#copyFrom` behavior, unmodified. **No test exists** covering Food (or any Resource) across a death/respawn or dimension-change scenario — confirmed by the test-inventory pass (a regex sweep for `respawn|clone\(|changeDimension` in `src/test/java` found only incidental comment mentions, never an actual test scenario).

---

## 11. Current Food HUD and Resource Presentation

**[CODE FACT]**. `TotalityHudRenderer.java` renders the Food/Hunger bar (mirrored, right-to-left fill, sharing Health's Y-position per `HudBarLayout.foodY(screenH) == healthY(screenH)`, both confirmed by `HudBarLayoutTest`). Two source-regression tests are directly load-bearing for this audit:

- `TotalityHudCleanupSourceRegressionTest.nativeHealthAndFoodRemainUnchanged()` asserts the HUD still computes `hungerPct = hunger / 20.0` from a direct `client.player.getFoodData().getFoodLevel()` read — i.e. **the bar's fill is still raw native 0–20**, not any Resource-API value, as of the most recent HUD work (this same session's own prior "Polish player HUD and chat layout" commit, HEAD `07d5200`).
- `Phase3CConsumerMigrationSourceRegressionTest.hudRendererFoodBarStillUsesTheNativeZeroToTwentyRatio()` and `...hudRendererDoesNotRouteHealthOrFoodThroughTheGenericPresentationResolver()` independently confirm Health/Food are **deliberately excluded** from the `ClientResourcePresentationResolver` migration that Mana/Stamina/Rage/Spell Slots already went through (Phase 3C).

**Only the displayed numeric text** (not the bar fill) goes through `PlayerResourceService.query(...)` + the registered `totality:food` formatter, with a fallback that computes the identical `×5` conversion directly from the raw native value if the query fails — so even the "Resource API path" ultimately just re-derives the same native-based number. `InventoryItemDetail.java`'s "Nutrition" tooltip line uses the same formatter (native `6` displays as `30`); "Saturation" is shown raw, unconverted.

**Answering the pre-flight questions directly:** the HUD displays **converted 0–100 numbers** for the text label, but the underlying **fill percentage and all gameplay logic remain native 0–20** — there is no layer anywhere that treats Food as authoritatively 0–100 today.

---

## 12. Current Food Item Architecture

**[CODE FACT]**. No `TotalityFoodItem` or generic consumable base class exists (§5). Current food-adjacent items:

| Item | Class | Food properties |
|---|---|---|
| True Wheat | `item/alchemy/TrueWheatItem.java:33` | `SKIngredientItems.INGREDIENT_FOOD`: nutrition 0, saturationModifier 0, alwaysEdible |
| Salmon Roe | `SalmonRoeItem.java:32` | same |
| Rock Warbler Egg | `RockWarblerEggItem.java:32` | same |
| Red Mountain Flower | `RedMountainFlowerItem.java:18` | same |
| Garlic | `GarlicItem.java:23` | same |
| Blue/Purple Mountain Flowers | registered via `init/TotalityRegistry.java:133` | same, applied generically |

All extend `net.minecraft.world.item.Item` directly, implementing `AlchemyIngredient`/`TooltipExtension` — none carry real nutrition/saturation (all zero), so these are "technically edible ingredients," not meaningful Food-restoring foods.

**Potions coupled to Food purely as a UI mechanism — [CODE FACT, repeated pattern, 8+ occurrences]:** `HealingPotionItem` (`item/potion/dnd/HealingPotionItem.java`) and `AlchemyPotionItem` (`item/potion/AlchemyPotionItem.java`, 6 registered variants in `init/TotalityRegistry.java`) both register `.food(new FoodProperties.Builder().alwaysEdible().build(), Consumables.DEFAULT_DRINK)` — no nutrition/saturation set (defaults to 0) — purely to make the item consumable via vanilla's right-click-hold drink interaction pipeline. `finishUsingItem` delegates to `super.finishUsingItem(...)` (vanilla), meaning drinking triggers vanilla's `player.eat(...)` path with 0 nutrition — a functional no-op on hunger. **This is a deliberate, repeated convention, not accidental coupling** — but it is a real example of "Food properties" being reused for an unrelated purpose, worth being careful not to break when reauthoring food values individually (per the task's Scope H).

No generic consumable abstraction exists to separate "restores hunger" from "uses the eat/drink interaction pipeline" — every current consumable conflates the two by using vanilla `FoodProperties` for both purposes simultaneously.

---

## 13. Authoritative Food 0–100 Architecture Options

Per the task's explicit instruction, this section evaluates architectures using current source evidence — it does not choose one on Stefan's behalf, though it does state this audit's recommendation clearly.

### Option 1 — Continue storing vanilla `FoodData` at 0–20, expose a 0–100 semantic layer

This is **exactly what exists today** **[CODE FACT]** — not a future option so much as the status quo, already fully built and tested (214+ tests across Phase 2A alone). Server authority: vanilla, unchanged. Persistence: vanilla, unchanged. Sync: vanilla's own packet, unchanged, already proven ineligible-by-design for Totality's generic sync (§9). Vanilla/mod compatibility: maximal — any other mod reading `FoodData` sees exactly what it expects. Food-item consumption: fully vanilla, zero migration. Saturation/hunger-exhaustion: fully vanilla, unchanged. Sprint gating/starvation: fully vanilla, unchanged. Commands: none currently exist to break. HUD: already ×5-converted for display. Resource API consistency: already the canonical, currently-accepted shape (§6.5 of `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`). Respawn/cloning: fully vanilla, zero migration. Migration complexity: **zero** — nothing to migrate. Rounding: none needed (already integral). Duplicated-state risk: **none** — this is precisely what the `EXTERNAL_ADAPTER` authority model exists to prevent. Feedback-loop risk: none. Testability: already extensively tested. Later Diet/Cooking compatibility: the canonical design's own §22.7 already describes Cooking/Diet querying `totality:food` through the existing façade in mechanical (0–20) units — Option 1 requires **no rework** of that already-planned integration point. Later nutrition/calorie/protein data: unaffected either way — that's a separate Diet-owned concern regardless of Food's own scale (§12/Diet doc).

**Downside:** does not achieve the stated goal of individually varied, granular 0–100 food-item authoring — 0–20 with only 20 discrete integer levels is the actual limiting factor motivating this whole task, and Option 1 does not remove that limitation, it only relabels it for display.

### Option 2 — Change/reinterpret `FoodData` so its own actual authoritative values become 0–100

**[INFERENCE, this audit's own analysis — no such work exists in code today]**. This would mean either (a) subclassing/replacing vanilla's `FoodData` via mixin so `foodLevel` itself ranges 0–100, or (b) redefining what the *existing* `int foodLevel` field means (i.e. silently treating vanilla's own field as if it already were 0–100). Option 2(a) requires re-deriving **every single vanilla threshold** in §7 (regen at 18/20, sprint at 7, starvation at 0, exhaustion conversion at 4.0, `MAX_SATURATION=20.0`, saturation-clamped-to-current-food-level) at 5× scale, and requires either a deep mixin into `FoodData.tick()` (fragile — this method's entire logic, not just constants, would need overriding, since e.g. "every 10/80 ticks" intervals interact with the *rate* of change, not just the *scale*) or full replacement of the class (very invasive, breaks any other mod's vanilla-`FoodData` assumptions, breaks vanilla's own `ClientboundSetHealthPacket` semantics since its `food` field is a raw int other clients/mods expect to be 0–20). Option 2(b) is actively dangerous — silently reinterpreting an unchanged vanilla field's meaning would desync any other mod, any command (`/effect give ... hunger`), and vanilla's own displayed HUD (if ever shown) without any code change signaling the reinterpretation. **This audit recommends against Option 2 in both forms** — it maximizes vanilla/mod incompatibility and migration risk for the least architectural benefit of the three options.

### Option 3 — Dedicated authoritative Totality Food Resource at 0–100, with a compatibility bridge to vanilla systems

**[INFERENCE, this audit's own analysis]**. This would mean a **new** `GENERIC_COMPONENT`-authority resource (e.g. `totality:food` reassigned, or a new ID) storing its own 0–100 `ScalarResourceState`, persisted/synced through the Resource API's own existing (already-built, already-tested) generic sync/persistence machinery (§21) — while a bridge keeps vanilla's own `FoodData` at some derived/clamped/mirrored value so vanilla mechanics (sprint gating via `hasEnoughFood()`, starvation damage, vanilla's own regen if not yet disabled per §8) still function without every vanilla call site needing a rewrite. Server authority: Totality's own component, clean. Persistence: already-built generic component NBT (`PlayerResourceStateComponent`), already tested extensively (§21/§23). Client sync: already-built generic delta/full-snapshot sync (§23), already tested. Vanilla compatibility: **requires an explicit, carefully-designed bridge** — every vanilla mechanic in §7 (regen, starvation, sprint, exhaustion) either needs to be redirected to read the new authoritative value, or vanilla's own `FoodData` needs to be kept in a synchronized, clamped mirror (e.g. `vanillaFoodLevel = totalityFood / 5`, rounded) purely so those vanilla code paths keep working unmodified — this is real, non-trivial design work not yet started anywhere. Mod compatibility: another mod reading `player.getFoodData()` would see the *mirrored*, not authoritative, value — a real compatibility risk that must be documented if chosen. Food-item consumption: would need every food item's eat-application to route through the new authority instead of (or in addition to) vanilla's own `FoodProperties.onConsume` → `foodData.eat(...)` path. Saturation/hunger-exhaustion: would need their own decision (§15) — likely also migrated to the new authoritative store, or deliberately left vanilla-owned-and-mirrored. Sprint gating/starvation: would need to read the new authority (or continue reading the vanilla mirror, if the bridge keeps it in sync closely enough that the threshold semantics still hold at the new scale). Commands: none exist to migrate. HUD: would finally be able to show a *true* 0–100 fill percentage with 100 discrete levels, not just a ×5-relabeled 20-level bar. Resource API consistency: fully consistent — this becomes structurally identical to Mana/Stamina today (`GENERIC_COMPONENT`, already-built persistence/sync). Respawn/cloning: uses the already-built, already-tested `RespawnStrategy` machinery. Migration complexity: **highest of the three options**, but the *infrastructure* (generic component storage, sync, persistence, orphan-quarantine safety) is already built and tested for other resources — the genuinely new work is narrowly the vanilla-compatibility bridge and the one-time player-data migration (§14), not the underlying plumbing. Rounding: real risk, must be decided explicitly (§14). Duplicated-state risk: **real and must be actively guarded against** — this is the one option where vanilla `FoodData` and Totality's own store could disagree if the bridge has a bug; the existing `isRegisteredExternalAdapterAuthority` guard pattern (§21) would need a mirror-image guard ensuring the *new* authoritative resource's `GENERIC_COMPONENT` state is never treated as also externally-adapter-backed. Feedback-loop risk: real — if the bridge mirrors bidirectionally (vanilla changes update Totality's store AND Totality's store updates vanilla), an infinite-loop or double-application bug is a genuine implementation hazard requiring careful one-directional-authority design. Testability: high — reuses already-extensively-tested generic-resource test patterns. Later Diet/Cooking compatibility: **best of the three** for the stated end goal — a true 0–100 (or even finer) scale gives Diet/Cooking meaningfully more granular values to work with than 20 discrete vanilla levels ever could.

**This audit's recommendation:** **Option 3**, but only after Scope B/§8's Health-regeneration gap is resolved (since the regen mechanic reads `foodLevel` thresholds directly, and those thresholds are exactly what a bridge would need to reconcile), and only with the vanilla-compatibility bridge explicitly designed and reviewed *before* implementation begins — not discovered ad hoc during the migration. See §28 for the recommended phase sequence.

### Option 4 — other architecture

No fourth architecture was surfaced by this audit's source evidence beyond hybrids of the above (e.g. "Option 3 but keep vanilla `FoodData` as the sync/persistence backing and only add a display-time nonlinear curve instead of a linear ×5" — this is really a refinement of Option 1, not a fourth option, since it never changes mechanical authority).

---

## 14. Save Migration Analysis

**[RECOMMENDATION — no migration code exists today; this section evaluates the question, it does not implement an answer.]**

If Option 3 is chosen, a proportional migration (`14/20 → 70/100`) is the stated accepted approach. Key considerations, each currently **unresolved** in code (there is no migration framework of any kind for Food specifically, though the Resource API's `PlayerResourceStateComponent` does already carry a general `schemaVersion`/`revision` pair usable as the versioning mechanism, per §6.6 area of the canonical design and confirmed present in code by the registry-audit research pass):

- **When migration should run [RECOMMENDATION]:** on first player load after the new authoritative resource is registered, gated by a schema-version check — read vanilla `FoodData.getFoodLevel()` once, compute the proportional 0–100 value, write it into the new `GENERIC_COMPONENT` state, and record that this player's migration has run (e.g. via the existing `schemaVersion` field already present on `PlayerResourceStateComponent`, or a dedicated migration-marker resource-specific flag) — **not** on every join, to avoid re-applying it.
- **Versioning [RECOMMENDATION]:** reuse the existing `PlayerResourceStateComponent.schemaVersion` mechanism already in code, incrementing it specifically for this change, rather than inventing a parallel Food-only version field.
- **Avoiding double-application [RECOMMENDATION]:** the migration must check "does this player's new Food resource state already exist" (via the same `getScalar(id)`/`Optional.isEmpty()` pattern §21 already uses for dormancy safety) before writing — if it already exists, skip. This reuses an already-proven-safe code pattern rather than inventing a new one.
- **Fractional results [RECOMMENDATION]:** `14/20 × 5 = 70` is exact for every vanilla food level (0–20 × 5 is always an integer 0–100), so **no rounding ambiguity actually exists for the food-level migration itself** — this is a genuinely simple case, unlike Health's HP-to-display conversion which does need `HALF_UP` rounding (§7 of the Phase 2A report) because HP can be fractional. Saturation, however, is a `float` (§7.1) and *would* need an explicit rounding decision if migrated proportionally (§15).
- **Corrupt/out-of-range data [RECOMMENDATION]:** clamp defensively (`Mth.clamp` or equivalent) to `[0, 20]` before computing the proportional value, matching the existing defensive posture already used throughout `PlayerResourceStateComponent`'s malformed-data handling (§21's orphan-quarantine pattern is the established precedent for "don't trust persisted data blindly").
- **Old-world / new-player handling [RECOMMENDATION]:** a new player (no prior save) should simply initialize the new resource directly from vanilla's own default (`foodLevel=20` → `100`), no migration branch needed — this is a strict subset of the general migration logic, not a special case requiring separate code.
- **Respawn/clone after migration [RECOMMENDATION]:** once migrated, this becomes an ordinary `GENERIC_COMPONENT` resource and automatically benefits from the already-built, already-tested `RespawnStrategy` copy-on-respawn machinery (§10) — no special-case logic needed *after* the one-time migration completes.
- **Saturation proportional migration [UNKNOWN — genuinely undecided]:** the task doesn't specify, and no canonical document addresses it. This audit recommends treating it as a separate, explicit decision (§15) rather than assuming it migrates automatically alongside food level.
- **Vanilla hunger-exhaustion conversion [UNKNOWN — genuinely undecided]:** same — see §15.
- **Old packets/components [INFERENCE]:** since Food has never had a Totality-authored packet or component (§9), there is no legacy packet/component shape to reconcile — this is actually a *simplifying* factor for Option 3 relative to, say, a hypothetical Mana-style migration, since there's no pre-existing bespoke sync path to retire.

---

## 15. Saturation and Hunger-Exhaustion Analysis

**[CODE FACT + UNKNOWN]**. Today, both saturation and vanilla's `exhaustionLevel` are **entirely vanilla-owned and untouched** by Totality (§5, §6) — the Food adapter's own Javadoc explicitly documents excluding them "by design." Whether a future authoritative 0–100 Food resource should also migrate saturation proportionally, and whether vanilla's hunger-exhaustion accumulator needs any conversion at all, are **both explicitly undecided** by any canonical document this audit reviewed. This audit's recommendation: if Option 3 is chosen, saturation most likely should **remain vanilla-owned** (mirrored, not migrated) initially, since it directly drives vanilla's own regen math (§7.4) which — per §8 — is not yet even disabled; entangling saturation migration with an still-undecided regen-disable decision multiplies risk unnecessarily. Vanilla's hunger-exhaustion accumulator likely needs **no conversion at all** if the vanilla-compatibility bridge (Option 3) keeps a mirrored vanilla `FoodData` alive purely to drive vanilla mechanics — exhaustion would simply keep accumulating against that mirror exactly as it does today.

---

## 16. Current Fatigue Implementation

**[CODE FACT, cross-agent verified — zero matches]**. Fatigue does not exist in the Totality codebase in any form. A case-insensitive repo-wide search for `fatigue`, `tired`, `tiredness`, `restNeed`/`RestNeed`, `overwork` across the entirety of `src/main/java` and `src/test/java` returns **zero matches** — no `PlayerResourceIds` entry, no component, no field, no comment, no TODO, no test. `PlayerResourceIds.java`'s complete, exhaustive constant list (§19) contains no `FATIGUE` entry. The Rest system's own package (`src/main/java/zcylas/totality/**/rest/**`, 24 files) was searched in full and contains zero Fatigue references.

Answering the task's direct questions: **(1)** not registered as a Resource. **(2)** no player owns it — it does not exist. **(3–6)** nothing creates, mutates, synchronizes, or persists it. **(7)** no HUD/UI reads it. **(8)** no gameplay behavior consumes it. **(9)** Rest does not modify it (Rest's own recovery table, §4 of `TOTALITY_REST_AND_FATIGUE.txt`, treats Fatigue/Rest Need as future work, "ALL of Section 10... genuinely undecided"). **(10)** it is **planned only** — "RECOVERED — PARTIAL... a confirmed DIRECTION with UNRESOLVED FORMULAS," per the canonical document's own status marker **[ACCEPTED DESIGN]**. **(11)** no conflicting historical definitions were found — every document this audit reviewed agrees Fatigue is unresolved. **(12)** per the canonical `TOTALITY_REST_AND_FATIGUE.txt §10b`, the eventual owning API should be a dedicated Rest Need/Fatigue system distinguishing Stamina (immediate), Rest Need (long-term accumulator), Fatigue (derived player-facing tier), and possibly Sleep Debt — explicitly **not** the existing `ExhaustionManager` (§17/§18).

---

## 17. Current Totality Exhaustion/Winded Implementation

**[CODE FACT]**. Lives entirely in `src/main/java/zcylas/totality/api/rpg/combat/exhaustion/`, two files:

**`ExhaustionState.java`** (enum):
```java
public enum ExhaustionState {
    NORMAL, WARNING, EXHAUSTED;

    public static ExhaustionState fromStamina(int stamina, int maxStamina) {
        if (maxStamina <= 0) return NORMAL;
        if (stamina <= 0) return EXHAUSTED;
        float pct = (float) stamina / maxStamina;
        if (pct <= 0.30f) return WARNING;
        return NORMAL;
    }
}
```

**`ExhaustionManager.java`** — static, in-memory-only server state:
```java
private static final Map<UUID, ExhaustionState> previousStates = new HashMap<>();
private static final Set<UUID> penalizedPlayers = new HashSet<>();
```
Recomputed from `PlayerStaminaManager` every server tick — **not** NBT-persisted, **not** a Totality component, **not** synchronized to the client (no packet, no HUD reference anywhere in `client/`).

**State-transition description (actual source behavior, per the task's requested format):**

```
NORMAL (stamina > 30% of max)
  → stamina drops to ≤30% but >0
  → WARNING: PLAYER_BREATH sound plays once (on the NORMAL→WARNING transition only)
  → [no movement/attack/regen penalty yet at WARNING — only a 0.75× stamina-regen multiplier]
  → stamina drops to 0
  → EXHAUSTED: red "⚠ You are exhausted!" notification; player added to penalizedPlayers
  → movement-speed attribute modifier −0.20 (ADD_MULTIPLIED_TOTAL) applied
  → attack-damage attribute modifier −0.25 (ADD_MULTIPLIED_TOTAL) applied
  → stamina-regen multiplier drops to 0.5×
  → sprint force-cancelled (independent raw stamina<=0 check, not via ExhaustionManager)
  → [player recovers stamina above 0]
  → onRecovered(): green "✔ You have recovered." notification; attribute modifiers removed; penalizedPlayers entry removed
  → back to WARNING or NORMAL depending on current percentage
```

Exact penalty code (`networking/stamina/StaminaServerTick.java`):
```java
private static final double SPEED_PENALTY = -0.20;   // id: totality:exhaustion_speed
private static final double ATTACK_PENALTY = -0.25;  // id: totality:exhaustion_attack
```
Regen multiplier (`ExhaustionManager.getRegenMultiplier`): `EXHAUSTED → 0.5f`, `WARNING → 0.75f`, `NORMAL → 1.0f`.

**Answering the task's direct questions:** **(1)** `pct <= 0.30f` (30% of max Stamina) for WARNING; `stamina <= 0` for EXHAUSTED. **(2)** yes, purely current/max Stamina. **(3)** `ExhaustionManager.tick(ServerPlayer)`, called from `StaminaServerTick`. **(4)** the same `tick()` method, via `onRecovered()`. **(5)** a bare static `Map`/`Set` inside a manager class — none of: status effect, formal component, boolean field on a component, tag, or attribute modifier itself (the *penalties* are attribute modifiers; the *state* driving them is not). **(6)** not persisted. **(7)** not synchronized. **(8)** `−0.20` movement speed, multiplicative, at EXHAUSTED only. **(9)** `0.5×`/`0.75×`/`1.0×` regen multiplier at EXHAUSTED/WARNING/NORMAL. **(10)** sprint is force-cancelled by an **independent** raw `stamina<=0` check in `StaminaServerTick.java:108-110`, not by consulting `ExhaustionManager`. **(11)** **No** — `PowerAttackManager.java` was read in full and contains **zero** references to `ExhaustionManager`/`isPenalized`/`isExhausted`/`isWarning`; Power Attack has its own entirely independent raw-Stamina-sufficiency gate. **(12)** no other actions are gated by this system. **(13)** **no** — "Winded" does not exist as a name anywhere in the codebase (zero matches); the WARNING tier is the closest conceptual analogue but is never called that. **(14)** N/A, since only NORMAL/WARNING/EXHAUSTED exist and are used consistently. **(15)** **No** — confirmed hardcoded Stamina-only with no extensibility hook (no event class analogous to `MaxStaminaCalcEvent`), and only 7 call sites total, all within the Stamina subsystem itself. **(16)** **Yes** — Stamina is currently the sole, hardcoded owner; nothing else can apply this state. **(17)** **None** — zero test coverage confirmed by the test-inventory pass. **(18)** two confirmed defects: (a) `ExhaustionManager.onPlayerJoin(ServerPlayer)` exists specifically to prevent a spurious exhaustion notification on join, but is **never called anywhere** — `PlayerConnectionEvents.java` wires only `onPlayerLeave` (line 155); a player rejoining at low/zero Stamina will get a spurious "⚠ You are exhausted!" notification on the first tick after join. (b) `isExhausted()`/`isWarning()` are dead public API, never called outside the class.

---

## 18. Vanilla Hunger Exhaustion vs Totality Exhaustion

**[CODE FACT]** — a dedicated collision analysis, per the task's Scope F.

| | Vanilla hunger exhaustion | Totality Stamina Exhausted/Winded | Future persistent Exhaustion condition | Future Fatigue/Rest Need |
|---|---|---|---|---|
| **Owner** | Vanilla `FoodData` | `api/rpg/combat/exhaustion/ExhaustionManager` | Not designed | Not designed (`TOTALITY_REST_AND_FATIGUE.txt §10`) |
| **Storage** | `FoodData.exhaustionLevel` (float, NBT-persisted, vanilla) | Static in-memory `Map<UUID,ExhaustionState>` (not persisted) | N/A | N/A |
| **Numeric/categorical** | Numeric (float, accumulates to 40.0 cap) | Categorical (3-value enum, derived) | Undecided | Undecided ("ONE persistent authoritative `restNeed` value," proposed but not adopted) |
| **Source events** | Jumping, sprinting, mining, attacking, swimming (fixed vanilla constants) | Stamina depletion only | Undecided — future doc says combat/mining/sprinting/abilities/environment/injuries should contribute | Undecided — awake time + weighted activity events (proposed, unresolved) |
| **Gameplay consequences** | Drains saturation, then food level; feeds starvation | Movement −20%, attack −25%, regen ×0.5/0.75 | Undecided | Undecided — "governs whether a Long Rest is genuinely restorative" |
| **Persistence** | Yes (vanilla NBT) | No | Undecided | Undecided |
| **Synchronization** | Yes (folded into `ClientboundSetHealthPacket`) | No | Undecided | Undecided |
| **UI representation** | None (no vanilla exhaustion HUD) | One-shot text/sound notifications only, no persistent HUD indicator | Undecided | Undecided |
| **Naming risk** | Uses the word "exhaustion" in its own vanilla API (`causeFoodExhaustion`, `getExhaustionLevel`) | Uses the word "Exhausted" as an enum value, in an unrelated package | Not yet named | Explicitly warned in canonical docs not to be conflated with the above |

**One incidental collision point exists today** **[CODE FACT]**: `item/magic/rune/effect/HealEffect.java:72` calls vanilla's own `player.causeFoodExhaustion(2.5f)` as a side effect of a Heal rune — this is a legitimate use of the *vanilla* API, correctly unrelated to `ExhaustionManager`, but it is a real example of both "exhaustion" systems appearing in the same codebase under the same word.

### Recommended unambiguous internal terminology **[RECOMMENDATION — not applied to code by this audit]**

| Concept | Current code name | Recommended internal term |
|---|---|---|
| Vanilla's own hunger-exhaustion accumulator | `FoodData.exhaustionLevel` (unchanged, vanilla-owned, cannot rename) | Refer to it in Totality's own docs/comments as **"vanilla hunger-exhaustion"** or **"food exhaustion"** — never bare "exhaustion" |
| Totality's existing Stamina-derived short-timescale state | `ExhaustionManager`/`ExhaustionState` | Given how entrenched this name already is (7 call sites, notification strings, attribute-modifier IDs `totality:exhaustion_speed`/`totality:exhaustion_attack`), **this audit recommends against a disruptive rename** — instead, disambiguate going forward by consistently calling it **"combat Exhaustion"** or **"Stamina Exhaustion"** in documentation and new code comments, reserving bare "Exhaustion" for the future persistent condition once it exists |
| The WARNING tier specifically (≤30%, not yet 0) | `ExhaustionState.WARNING` | **"Winded"** is a reasonable *player-facing/documentation* label for this tier without renaming the enum constant itself — introduces the term without a breaking code change |
| Future persistent, multi-source condition | Does not exist | **`ExhaustionCondition`** (matches the task's own suggested list) — deliberately distinct from `ExhaustionManager` to signal it is the extensible, multi-owner successor, not a rename of the Stamina-only system |
| Future long-term need/resource | Does not exist | **`RestNeed`** (numeric, `GENERIC_COMPONENT` resource) with **`Fatigue`** as the derived player-facing tier name — matches `TOTALITY_REST_AND_FATIGUE.txt §10b`'s own already-adopted conceptual split exactly |

---

## 19. Current Generic Player Resource Inventory

**[CODE FACT]**. Exactly **7 resources**, registered once from `ProductionResourceDefinitions.register()`, called last inside `Totality.registerApi()`, itself called from mod init before any player can join:

```java
// PlayerResourceIds.java — complete, exhaustive list
public static final Identifier HEALTH = ...;      // totality:health
public static final Identifier FOOD = ...;        // totality:food
public static final Identifier BREATH = ...;       // totality:breath
public static final Identifier MANA = ...;         // totality:mana
public static final Identifier STAMINA = ...;      // totality:stamina
public static final Identifier SPELL_SLOTS = ...;  // totality:spell_slots
public static final Identifier RAGE = ...;         // totality:rage
```

| Resource | Model | Authority | Native/legacy-backed | HUD consumer |
|---|---|---|---|---|
| Health | SCALAR | EXTERNAL_ADAPTER | Native (`Player.getHealth()`) | `TotalityHudRenderer` (bar) |
| Food | SCALAR | EXTERNAL_ADAPTER | Native (`FoodData`) | `TotalityHudRenderer` (bar) |
| Breath | SCALAR | EXTERNAL_ADAPTER | Native (air supply) | None — deliberately left to vanilla's own air-bubble HUD |
| Mana | SCALAR | EXTERNAL_ADAPTER | Legacy `PlayerResourceComponent` | `TotalityHudRenderer` (bar, via `ClientResourcePresentationResolver`) |
| Stamina | SCALAR | EXTERNAL_ADAPTER | Legacy `PlayerResourceComponent` | `TotalityHudRenderer` (bar, via resolver) |
| Spell Slots | PARTITIONED_POOL (10 levels) | EXTERNAL_ADAPTER | Legacy `SpellSlotComponent` | `SpellRadialScreen` (menu, not always-on HUD) |
| Rage | SCALAR | EXTERNAL_ADAPTER | Legacy `PlayerChargesComponent` entry | `ClassTab`/secondary-HUD (menu/contextual, not always-on bar) |

**All 7 are `EXTERNAL_ADAPTER`-authority — zero are `GENERIC_COMPONENT` today** **[CODE FACT]**, confirmed by `PlayerResourceRegistry.java`'s own Javadoc and by reading every `.register(...)` call in `registerDefinitions()`. This means **any future `GENERIC_COMPONENT` resource (a true Food 0–100 under Option 3, or a future Fatigue/Thirst) would be the first of its kind in production** — the storage/sync/persistence machinery exists and is extensively tested (§21/§23), but has never actually carried a live production resource yet.

Formal grant/entitlement machinery **does not exist** — `ResourceGrantInitialization` is a declaration-only sealed interface with zero runtime logic; "grant" for Mana/Stamina is really just "the legacy component is unconditionally attached to every player"; for Rage/Spell Slots it's "the legacy map/array happens to be sparse/zero for non-eligible players," inspected passively, never enforced by the Resource API itself. The actual entitlement layer described in `TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md` remains prose-only.

---

## 20. Dormant Resource Matrix

Per the task's list of candidate resources, cross-checked against both `src/main/java` and `Context/Audit/*.md`/`*.txt` **[CODE FACT for the "found in code" column; HISTORICAL DOC/ACCEPTED DESIGN for the "found in doc" column]**:

| Candidate | In code? | In design doc? | Disposition |
|---|---|---|---|
| Health | **Yes** | — | Production-active |
| Food | **Yes** | — | Production-active (native-authoritative) |
| Breath | **Yes** | — | Production-active (HUD-deliberately-excluded) |
| Mana | **Yes** | — | Production-active |
| Stamina | **Yes** | — | Production-active |
| Rage | **Yes** | — | Production-active (menu/contextual) |
| Spell Slots | **Yes** | — | Production-active (menu) |
| Thirst | No | `TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md` (`totality:thirst`); also `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md §8.1` | **Explicitly deferred** — canonical doc already reserves the ID and polarity (`HIGH_IS_GOOD`) but the owning Survival/Thirst system is unimplemented |
| Fatigue/Rest Need | No | `TOTALITY_REST_AND_FATIGUE.txt §10`; provisional ID `totality:rest_need` | **Explicitly deferred, unresolved formulas** — see §16 |
| Sanity | No | Same entitlement doc | **Never formally approved beyond a placeholder ID** — "the future Sanity design owns its exact thresholds" |
| Ki | No | Same doc | **Owned by a future API** — "Monk Ki has no backing resource" (confirmed still true) |
| Solar Charge | No | Same doc | **Owned by a future API** — Kryptonian species-specific, unimplemented |
| Chakra | No | Same doc | **Owned by a future API** — Ninjutsu discipline-specific, unimplemented |
| Pact Magic | No | Same doc | **Owned by a future API** — "Warlock Pact Magic has no live player pool" (confirmed still true) |
| Cursed Energy | No | Same doc | **Owned by a future API** — Jujutsu discipline-specific, unimplemented |
| Reiryoku / Reiatsu | No | Reiatsu only, same doc | Reiatsu: **owned by a future API**. Reiryoku: **never mentioned anywhere** |
| Haki, Hamon, Nature Energy, God Ki, Regeneration Energy, Speed Force, Rc Cells, Psionic Energy, Lantern Energy | No | No | **Never mentioned anywhere in code or any design document searched** — these appear to be purely illustrative/hypothetical examples in the audit task itself, not any actual Totality design intent found by this audit |
| Radiation | No | No (only unrelated mob/particle code matches) | **Not a resource concept in this codebase today** |

**This audit's disposition recommendation for each unfound-but-documented candidate: "better left unregistered until implementation."** None of Thirst, Fatigue, Sanity, Ki, Solar Charge, Chakra, Pact Magic, or Reiatsu has a resolved formula, threshold, or owning-system implementation today — registering a `PlayerResourceDefinition` for any of them now would create dead metadata with no consumer, and (per §21) would not even be *unsafe*, just premature and disconnected from the system that should actually own its rules.

---

## 21. Dormancy Safety and Accidental-State Analysis

**[CODE FACT]**. This is the most safety-critical section of the audit; findings are stated with high confidence since they come from direct reading of the registration/query code paths plus extensive existing test coverage.

1. **Does registration alone create player state?** **No.** `PlayerResourceRegistry.register(definition)` is a pure `synchronized` method that validates and inserts into a static `Map<Identifier, PlayerResourceDefinition>` — no `ServerPlayer`, no component, no NBT is touched.
2. **Can opening the HUD instantiate state?** **No.** The HUD path (`TotalityHudRenderer` → `PlayerResourceService.query(...)`) for a `GENERIC_COMPONENT` resource resolves via `state.getScalar(id)`, a pure `Map.get` — a miss returns `Failure(STATE_NOT_INSTANTIATED)`, it never calls `instantiateScalar`. Confirmed by grep: `instantiateScalar`/`instantiatePartitioned` have **zero production call sites** anywhere in `src/main/java`.
3. **Can opening a menu or tooltip instantiate state?** **No** — same query path, same guarantee; no menu/tooltip code was found calling anything but `PlayerResourceService.query`.
4. **Can client presentation resolution instantiate state?** **No** — `ClientResourcePresentationResolver` and `NativeClientResourceReader` are both read-only query paths; neither has a production call site that writes state.
5. **Can synchronization instantiate state?** **No** — sync only ever *sends* already-existing live state; `applySyncPacket` (client-side receive) actively **rejects** any entry whose id is `EXTERNAL_ADAPTER`-authority rather than creating it.
6. **Can a missing Resource snapshot create defaults accidentally?** **No** — a missing `GENERIC_COMPONENT` entry produces a structured `Failure`, never a fabricated zero/default `Success` (this exact bug class was found and fixed during Phase 2A's own correction pass — `ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE` was added specifically to stop a prior fabricated-maximum bug, per the Phase 2A report §"No fabricated generic maximum").
7. **What grant/entitlement mechanism prevents ownership?** **None formally exists** (§19) — dormancy safety today works *because nothing has been granted anything yet*, not because a tested entitlement layer actively enforces exclusion. This is a real, honestly-flagged gap: the safety net is "nothing calls the unsafe method," not "an authority actively blocks it," though the practical effect for currently-registered resources is identical.
8. **Are dormant Resources serialized?** For the 7 *currently registered* resources: no dormant state exists to serialize (all are `EXTERNAL_ADAPTER`, never enter `PlayerResourceStateComponent`). For a *hypothetical future* `GENERIC_COMPONENT` resource with no grant: also no, by the same query-path guarantee — nothing writes an entry without an explicit `instantiateScalar` call, which nothing currently makes automatically.
9. **Are dormant Resources sent over the network?** **No**, by the same reasoning — `writeSyncPacket` only iterates the live `states` map, which stays empty for ungranted resources.
10. **Do they appear in debug commands or inspection tools?** The one dev-only tool (`/totalitydebug resource parity`, `FabricLoader.isDevelopmentEnvironment()`-gated) only inspects the 7 already-registered resources and reports `comparison=native-only`/failure states cleanly per-resource without crashing on a missing one — it does not "discover" or expose any undocumented dormant resource, since none exists to discover.
11. **Do tests prove dormant definitions stay inert?** **Yes, extensively**, though not under the literal word "dormant" (that word is used elsewhere in the test suite for unrelated dead HUD-draw-method cleanup, not resource entitlement). The functionally-equivalent guarantee is proven by: `PlayerResourceStateComponentTest.queryingUngrantedResourceDoesNotInstantiateIt`, `...definitionRegistrationAloneDoesNotInstantiatePlayerState`, `PlayerResourceStateComponentExternalSafetyTest.registrationAloneCreatesNoPlayerState`, `...healthAndFoodProduceNoGenericStateEntriesForAnOrdinaryPlayer`, `PlayerResourceServiceTest.unknownResourceProducesAStructuredFailureNotAnException`, `...queryDoesNotInstantiateGenericState`, `PlayerResourceRegistryTest.unknownIdLookupReturnsEmptyNotException`, `ExternalPlayerResourceAdapterRegistryTest.safeLookupForUnknownIdReturnsEmptyNotException` — 8+ distinct tests across 4 files.
12. **Is a dormant definition genuinely safe today?** **Yes**, for the mechanisms this audit could verify. The one honest caveat: this conclusion holds because nothing in the current 7-resource production set has ever exercised the `GENERIC_COMPONENT` instantiation path in anger — the *code* that would reject a misuse is present and tested in isolation, but no real dormant `GENERIC_COMPONENT` resource has ever existed in production to prove the end-to-end path under real gameplay conditions. This is a reasonable, evidence-based confidence level, not a claim of exhaustive proof.

**Recommendation on adding dormant definitions [RECOMMENDATION]:** per the task's explicit options, this audit recommends **"Not added at all until requirements are clearer,"** specifically for every candidate in §20 except Food itself. Registering, say, `totality:fatigue` today with no owning system would create metadata with a `maximumResolverId` pointing nowhere useful and no consumer — it does not meaningfully de-risk future work, and the canonical design's own §4.4 ("A registered resource definition represents a resource type... not... owned by every player") plus the "one authority per axis" principle (§1.3 of the canonical doc) both argue for registering a resource *alongside or after* its owning system is designed, not speculatively ahead of it.

---

## 22. TotalityFoodItem Minimum Foundation

**[RECOMMENDATION]**. Per the task's explicit instruction not to design the full Diet/Cooking API, this section states only the minimum responsibility boundary, informed by §12's findings.

Given that **8+ existing items already misuse `FoodProperties.alwaysEdible()` purely to hook the vanilla eat/drink interaction pipeline** (§12), and that MC 26.2 has already split "nutrition/saturation" (`FoodProperties`) from "consumption timing/animation/sound" (`Consumable`, §7.6) at the vanilla level, this audit recommends `TotalityFoodItem`'s minimum foundation **explicitly separate these two concerns** rather than inheriting the current codebase's conflation of them:

**Should author (immediate, minimum):**
- Base Food restoration value, expressed in whatever the *current* mechanical scale is at implementation time (0–20 if built before a migration, 0–100 if built after) — this is the one thing that must not be hardcoded ambiguously.
- Consumption duration and animation — already a distinct vanilla component (`Consumable`); `TotalityFoodItem` should expose this cleanly rather than repeating the `Consumables.DEFAULT_DRINK` copy-paste pattern seen 8+ times today.
- Remainder/container item (e.g. an empty bowl) — a genuinely minimal, low-risk vanilla-item-property concern.
- Optional effects on consumption — already a vanilla `Consumable`/`ConsumeEffect` concept; `TotalityFoodItem` should provide a clean authoring hook, not reinvent the mechanism.

**Should NOT author yet (later extension points, explicitly deferred per Diet's "RECOVERED FRAGMENTS, STILL NOT IMPLEMENTABLE" status, §4):**
- Diet tags/categories (food-group mapping) — `TOTALITY_FOOD_ECOSYSTEM_CURRENT.txt §12` explicitly states the food-group taxonomy is "STILL MISSING (do not invent)."
- Nutrition metadata hooks beyond a placeholder extension point — same reason.
- Spoilage/condition hooks — belongs to the separately-designed `ProductConditionComponent`/`FreshnessData` system (`TOTALITY_FOOD_ECOSYSTEM_CURRENT.txt §6`), which is itself already a stable, adopted design distinct from `TotalityFoodItem`'s own scope; `TotalityFoodItem` should be *compatible with* attaching that component later, not implement its logic.
- Quality/provenance hooks — same reasoning, belongs to `ProductGrade`/`ProductOriginSummary`.
- Actor-generic (non-player) consumption — **[UNKNOWN]** whether any current Totality mob/animal ever needs to eat a `TotalityFoodItem`; no evidence either way was found by this audit, and speculative generalization risks the exact kind of premature abstraction this document's own house style (per the user's global CLAUDE.md instructions) warns against.

The item-consumption architecture question ("is consumption player-only or LivingEntity-compatible") is answered by vanilla itself, not something `TotalityFoodItem` needs to decide independently: vanilla's `FoodProperties.onConsume(Level, LivingEntity, ItemStack, Consumable)` is already `LivingEntity`-generic at the vanilla layer — whatever `TotalityFoodItem` builds on top of it inherits that generality for free, without needing its own design decision here.

---

## 23. Existing Test Coverage

**[CODE FACT]**. 102 test files exist under `src/test/java`. Directly relevant counts (exact `@Test` method counts, verified by the test-inventory research pass):

| Area | Key files | Test count |
|---|---|---|
| Food adapter conversion | `FoodResourceAdapterConversionTest` | 10 |
| Food display characterization | `HungerDisplayCharacterizationTest` | 4 |
| Formatter registry (Food-relevant subset) | `ResourceValueFormatterRegistryTest` | 9 (2 Food-specific) |
| Other resource adapters (parallel pattern) | `HealthResourceAdapterConversionTest` (19), `BreathResourceAdapterTest` (15), `ManaResourceAdapterTest` (18), `StaminaResourceAdapterTest` (18), `RageResourceAdapterTest` (20), `StandardSpellSlotsResourceAdapterTest` (19) | 109 combined |
| Adapter registry / registration | `ExternalPlayerResourceAdapterRegistryTest` (12), `PlayerResourceRegistryExternalAdapterFreezeTest` (14) | 26 |
| Query service | `PlayerResourceServiceTest` | 36 |
| Definition registry | `PlayerResourceRegistryTest` | 41 |
| Grant-initialization value type | `ResourceGrantInitializationTest` | 12 |
| Generic component storage / dormancy | `PlayerResourceStateComponentTest` (13), `PlayerResourceStateComponentExternalEntryPathTest` (19), `PlayerResourceStateComponentExternalSafetyTest` (17), `PlayerResourceStateComponentMalformedDataTest` (2) | 51 |
| State containers | `OrphanedResourceStateTest` (5), `PartitionedResourceStateTest` (6), `ScalarResourceStateTest` (3) | 14 |
| Sync (pure logic) | `ClientResourceSyncStateTest` (17), `PlayerResourceSyncStateTest` (15), `ClientResyncRequestGateTest` (17), `ResourceResyncRateLimiterTest` (5), `ClientResourceSyncRejectionDiagnosticsTest` (3), `ResourcePartitionedWireSnapshotTest` (8), `ResourceScalarWireSnapshotTest` (8) | 73 |
| Sync (packets) | `ResourceFullSyncPayloadTest` (6), `ResourceDeltaSyncPayloadTest` (10), `ResourceResyncRequestPayloadTest` (2), `ResourceSyncManagerEligibilityTest` (3) | 21 |
| Parity/diagnostics | `ClientResourceParityLogObserverTest`, `ClientResourceParityObservationsTest`, `LegacyClientResourceParityReadersTest`, `ClientResourceParityReportAssemblerTest`, `ClientResourceParityTrackerTest`, + ~13 more | ~120+ combined |
| HUD | `HudBarLayoutTest` (25), `TotalityHudCleanupSourceRegressionTest` (37), `Phase3CConsumerMigrationSourceRegressionTest` (19), `ClientResourcePresentationResolverTest` (16), `HudValueFormatterTest` (16), `PersistentHudVisibilitySourceRegressionTest` (8), `ChatCompatibilityCorrectionSourceRegressionTest` (24) | 145 |
| Debug command | `ClientResourceParityInspectionCommandTest` | 32 |

**Most load-bearing single finding for this task:** `ResourceSyncManagerEligibilityTest` (3 tests) directly proves Health/Food/Breath are structurally ineligible for the generic Resource-API sync system — this is the one existing test that most directly constrains any Option 3 (§13) implementation, since it currently encodes "Food never uses generic sync" as an asserted invariant that a migration would need to deliberately and explicitly change.

---

## 24. Missing Test Coverage

**[CODE FACT — confirmed absences, not speculation]**, per the test-inventory research pass's explicit negative-result reporting:

1. No natural-health-regeneration test exists — nothing asserts `naturalRegeneration` is disabled or enabled.
2. No Fatigue test exists anywhere.
3. No Exhaustion/Winded/sprint-restriction/Power-Attack-restriction test exists — `ExhaustionManager`/`ExhaustionState` (§17) have **zero** automated coverage of their own logic (only one incidental structural test proving an unrelated record shape lacks a `saturation`/`exhaustion` field).
4. No respawn/clone/dimension-transfer test exists for **any** Resource, Food included.
5. No FoodData NBT round-trip test exists (Totality's own quarantine-safety tests are thorough, but nothing tests vanilla `FoodData`'s own native persistence, since it's entirely outside Totality's component framework).
6. No Food consumable/eating-item test exists.
7. `ResourceSyncManagerEligibilityTest` + `Phase3CConsumerMigrationSourceRegressionTest` + `TotalityHudCleanupSourceRegressionTest` together constitute a **de facto regression fence** against Food being migrated at all — any Option 3 work will need these specific, currently-passing tests to be deliberately and knowingly updated, not accidentally broken.
8. No test exists for `/totality inspect` (only the separate `/totalitydebug resource parity` command has coverage).
9. Food's mutation capabilities (`SPENDABLE`/`RESTORABLE`/`DIRECT_DRAIN`) are untested because they don't exist — `PlayerResourceRegistryTest` repeatedly confirms Food declares query-only capabilities today.

### Proposed future test plan (per task instruction — proposed, not added by this audit)

Migration-specific: 0–20→0–100 save-migration correctness (including the exact-integer-multiply case, §14); migration idempotency (running twice produces the same result as once); native-vanilla-compatibility (whatever bridge Option 3 chooses); food-item consumption through the new authority; individually-reauthored food values round-tripping correctly; saturation behavior under the chosen §15 decision; hunger-exhaustion behavior under the chosen §15 decision; starvation still triggering correctly at the new scale's zero-equivalent; sprint gating still triggering at the correct new-scale threshold; natural-regeneration-disabled behavior (once §8's gap is closed) staying disabled across the migration; death/respawn preserving the new authoritative value; dimension transfer preserving it; server/client sync of the new value; low-Stamina state transitions (if Exhaustion/Fatigue work proceeds in parallel); Power Attack restrictions (if ownership is corrected per §18's terminology recommendation); dormant Fatigue/Thirst/etc. definitions (if ever added) staying genuinely unowned under real gameplay load, not just unit-test isolation; no HUD/menu/tooltip interaction creating state for an ungranted resource under real gameplay load; old-world-save compatibility (a save file from before this migration loading correctly).

---

## 25. Risks and Defects

Ranked by this audit's assessment of severity, all **[CODE FACT]** unless noted:

1. **Highest — Health regeneration design intent is unimplemented.** (§8) Not a code defect per se (nothing is broken), but a real gap between accepted design and shipped behavior that a Food-scale migration would otherwise interact with by coincidence (both touch `FoodData` thresholds) if not addressed deliberately first.
2. **High — `ExhaustionManager.onPlayerJoin` dead-code bug.** (§17) A confirmed, reproducible defect: players rejoining at low/zero Stamina receive a spurious "exhausted" notification. Independent of the Food task, but surfaced by this audit's research and worth fixing regardless, especially before building any new system (Fatigue) that might reuse similar join-hook patterns.
3. **High — zero test coverage of `ExhaustionManager`/`ExhaustionState`.** (§17, §24) A real, shipping, player-facing gameplay system (movement/attack penalties) has no automated regression protection at all.
4. **Medium — naming collision risk between vanilla hunger-exhaustion and Totality combat Exhaustion.** (§18) Confirmed real by both this audit and the independent 2026-07-12 audit reaching the same conclusion three weeks apart — a stable, recurring risk, not a one-off observation.
5. **Medium — HUD/eat-gate bypass the Resource API.** (§6, §11) The Food HUD bar's fill percentage and the server eat-gate both read `FoodData` directly rather than through `PlayerResourceService`, meaning a future migration cannot simply "swap the adapter" — it must also touch these bypass sites individually.
6. **Medium — no formal entitlement/grant enforcement layer exists.** (§19, §21) Current dormancy safety is real but relies on "nothing has been granted anything yet" rather than an actively-tested authority — acceptable today, but a latent gap once a genuinely sparse `GENERIC_COMPONENT` resource (e.g. a real future Fatigue) is added and something *does* need to decide who owns it.
7. **Low-Medium — repeated `FoodProperties.alwaysEdible()` misuse for potions.** (§12) Not currently harmful, but a real coupling that any "reauthor food values individually" pass must not accidentally disturb (these items must keep restoring zero nutrition even after individual food-item reauthoring).
8. **Low — saturation-clamped-to-current-foodLevel vanilla behavior.** (§7.3) An easy-to-miss precision detail (`Mth.clamp(saturationLevel + delta, 0, foodLevel)`, not `0, MAX_SATURATION`) that a naive ×5 migration of saturation alongside food level could get subtly wrong if not read carefully.

---

## 26. Contradictions Between Source and Documents

**[INFERENCE]**. This audit found **no direct contradictions** between the canonical design documents and the current code — an unusual and positive finding, worth stating plainly: `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`'s Food section (§6.5) and the Phase 2A implementation report agree with each other and with the actual code in every particular this audit checked. The one place a naive reader could perceive a contradiction is the canonical document's own §7.4 worked example (*"Food: mechanical maximum 20, display conversion ×5, displayed maximum 100"*) juxtaposed against this task's framing (*"Change player Food from vanilla-authoritative 0–20 to authoritative 0–100"*) — but this is not a contradiction, it is this task correctly proposing a **change to** the canonical design, which the canonical document itself anticipates as future, undesigned work (its own §6.5 Food subsection never claims the 0–20 mechanical scale is permanent, only that it is *current*). The Phase 2A report's explicit "later scope" framing (§1) is the closest thing to an authoritative acknowledgment that this exact migration was always expected to be a distinct, future undertaking, not an oversight.

---

## 27. Recommended Decisions

**[RECOMMENDATION]**, summarizing the analysis above into discrete, individually-approvable decisions:

1. Resolve the Health-regeneration gap (§8) — either implement the disable, or explicitly re-accept vanilla regen as intended (a written decision either way is better than the current silent gap).
2. Adopt **Option 3** (§13) for authoritative Food 0–100, contingent on decision 1 being resolved first, given both touch the same vanilla thresholds.
3. Explicitly decide saturation's fate (§15) — this audit recommends "remains vanilla-owned/mirrored" as the lower-risk default.
4. Explicitly decide vanilla hunger-exhaustion's fate (§15) — this audit recommends "no conversion needed" if a vanilla mirror is kept alive for compatibility.
5. Adopt the terminology recommendations in §18 going forward (no code rename required for `ExhaustionManager` itself).
6. Fix the `ExhaustionManager.onPlayerJoin` dead-code bug (§17/§25) independently of the Food work, since it's cheap, unambiguous, and currently causing a real (if minor) player-facing bug.
7. Do not register any dormant resource beyond Food's own eventual reauthoring (§20/§21) until its owning system is concretely being built.
8. Reauthor vanilla and Totality food items individually rather than a flat ×5 multiply (per the task's own accepted direction) — §29 lists every location that would need review.

---

## 28. Recommended Implementation Sequence

**[RECOMMENDATION]**. The task's candidate 8-phase sequence is evaluated and revised based on source dependencies actually found by this audit.

| Phase | Purpose | Subsystem boundary | Likely files | Tests required | Migration risk | Manual validation | Stop/commit boundary | Must remain deferred |
|---|---|---|---|---|---|---|---|---|
| **1. Terminology & authority decisions** | Close §27's open decisions in writing (Health regen, saturation, hunger-exhaustion, naming) | Documentation only — no code | None (or a new decisions doc) | None | None | N/A | A written decision doc, reviewed and accepted | Everything else |
| **2. Health-regeneration disable** | Implement the already-accepted-but-unbuilt design rule (§8) | `FoodData.tick()` interception (mixin) or a Totality-side gate before `heal()` fires | New mixin under `mixin/` targeting `FoodData` or a `LivingEntityHurtMixin`-adjacent hook; `totality.mixins.json` | New tests proving regen no longer fires at any food/saturation level, across all three difficulties | Low — additive gate, doesn't touch persisted data | In-world: eat to full, take damage, confirm no natural regen occurs on any difficulty | Commit once tests + manual validation pass | Food scale migration itself |
| **3. Migration/versioning foundation** | Build the one-time-migration mechanism (§14) reusing `PlayerResourceStateComponent.schemaVersion` | `api/rpg/resources/` migration helper | New migration class + tests | Idempotency, corrupt-data clamping, old-world/new-player branches (§24) | Medium — first real use of `GENERIC_COMPONENT` persistence in production | Load an old save, confirm exactly-once migration | Commit once fully tested, before any consumer reads the new value | Wiring HUD/gameplay to actually use it yet |
| **4. Food authoritative 0–100 (Option 3 core)** | New `GENERIC_COMPONENT` Food resource + vanilla-compatibility bridge | New `FoodResourceAdapter` replacement or new resource ID; bridge mixin/hook keeping vanilla `FoodData` mirrored for sprint/starvation/exhaustion | `FoodResourceAdapter.java` rewrite, new bridge class, `ProductionResourceDefinitions.java` | Bridge correctness (both directions never desync), sprint/starvation still trigger at correct thresholds | **Highest** — this is the core architecture change | Extensive in-world play: eat, starve, sprint-gate, regen (still disabled per phase 2), save/reload | Commit only after full manual playtest, per this project's established pattern for HUD/Resource work | Individual food-item reauthoring |
| **5. Vanilla Food mechanics adaptation** | Update the §6 bypass sites (HUD fill %, eat-gate) to read the new authority | `TotalityHudRenderer.java`, `InventoryActionHandler.java`, `NativeClientResourceReader.java` | Same files as §6's inventory | Update `TotalityHudCleanupSourceRegressionTest`/`Phase3CConsumerMigrationSourceRegressionTest` deliberately (§24 item 7) | Medium — touches already-tested HUD code | Visual HUD check at GUI Scale 4, matching this project's established HUD-work pattern | Commit after HUD/gameplay parity confirmed | Reauthoring |
| **6. Reauthor vanilla and Totality foods individually** | Replace the flat-×5 assumption with individually-chosen 0–100 values per item | Every location in §29 | Item-by-item value review, no single "migration" test possible | Low technical risk, high design/balance effort | Manual playtest of hunger pacing | Incremental commits per item batch are reasonable here, unlike phases 2-5 | `TotalityFoodItem` foundation (can happen in either order relative to this phase) |
| **7. `TotalityFoodItem` foundation** | Establish the shared base class per §22's minimum scope | New `item/` base class | Tests mirroring existing item-registration test patterns | Low | Confirm existing items migrate to the base class with no behavior change | Commit once existing items are ported without regression | Diet tags, nutrition metadata, spoilage/quality hooks |
| **8. Winded/Exhaustion/Fatigue ownership correction** | Fix §17's dead-code bug; apply §18's terminology; decide whether to build the real Fatigue system now or defer further | `ExhaustionManager.java` (bug fix only, unless Fatigue is greenlit), possibly new Fatigue files | Bug-fix regression test at minimum; full Fatigue test suite if that system is greenlit | Low for the bug fix; unresolved-formula risk (per `TOTALITY_REST_AND_FATIGUE.txt §10d`) if Fatigue itself is attempted | In-world: confirm no spurious exhaustion notification on rejoin | Commit the bug fix independently of everything above — it has zero dependency on Food | Full Fatigue system, if its formulas are still undecided at this point |

**Key deviation from the task's candidate order [RECOMMENDATION, justified]:** this audit moves Health-regeneration-disable (originally implicit/unstated) to **Phase 2**, immediately after decisions, and **before** the Food-scale migration — because §7.4/§8 establish that natural regeneration and Food's own thresholds are read by the *same* vanilla method (`FoodData.tick()`), so building the 0–100 migration first and the regen-disable second would mean touching that method's logic twice, with the second pass needing to re-verify the first pass's work didn't get disturbed. Phase 8 (Exhaustion/Fatigue) is also explicitly noted as **independent** of every other phase and could be done first, last, or in parallel without any dependency conflict — its only forced ordering is that the bug fix (§17) has zero Food dependency and could ship on its own at any time.

---

## 29. Exact Files Likely Affected Later

**[INFERENCE, compiled from §6, §12, §13 findings]** — not a commitment, a scoping aid for future planning:

**Food core (Phase 3-5):** `api/rpg/resources/external/FoodResourceAdapter.java`, `api/rpg/resources/ProductionResourceDefinitions.java`, `api/rpg/resources/client/NativeResourceAccess.java`, `api/rpg/resources/client/NativeClientResourceReader.java`, `client/resource/MinecraftNativeResourceAccess.java`, `client/renderer/hud/TotalityHudRenderer.java` (3 call sites), `networking/inventory/InventoryActionHandler.java`, `screen/inventory/InventoryItemDetail.java`, `client/resource/parity/ClientResourceParityInspectionCommand.java` (list update only).

**Vanilla-compatibility bridge (new, Phase 4):** a new mixin targeting `net.minecraft.world.food.FoodData` and/or `ServerPlayer.doTick()`, registered in `totality.mixins.json`.

**Health-regeneration disable (new, Phase 2):** likely a new mixin targeting `FoodData.tick()`'s regen branches specifically, or `LivingEntity.heal()`/`Player.aiStep()` depending on the chosen interception point — genuinely new file(s), no existing mixin is reusable for this (confirmed, §8).

**Food items (Phase 6-7):** `item/alchemy/TrueWheatItem.java`, `SalmonRoeItem.java`, `RockWarblerEggItem.java`, `RedMountainFlowerItem.java`, `GarlicItem.java`, `init/items/SKIngredientItems.java`, `init/TotalityRegistry.java` (flower/food registration block), `item/potion/dnd/HealingPotionItem.java`, `item/potion/AlchemyPotionItem.java`, `init/items/DndPotionItems.java` (must NOT gain real nutrition — §12's deliberate zero-nutrition pattern must be preserved).

**Exhaustion (Phase 8, independent):** `api/rpg/combat/exhaustion/ExhaustionManager.java`, `init/events/PlayerConnectionEvents.java` (wire the missing `onPlayerJoin` call).

**Tests requiring deliberate update (all phases):** `TotalityHudCleanupSourceRegressionTest.java`, `Phase3CConsumerMigrationSourceRegressionTest.java`, `ResourceSyncManagerEligibilityTest.java`, `FoodResourceAdapterConversionTest.java`, `HungerDisplayCharacterizationTest.java`.

---

## 30. Audit Conclusions

1. Food is, today, exactly vanilla `FoodData` at 0–20, with a presentation-only ×5 display layer already built and tested — not a partial migration toward 0–100, but a deliberately bounded, already-complete piece of work whose own documentation explicitly scopes the mechanical-scale question out.
2. The accepted design rule disabling vanilla Food-based natural health regeneration is **not implemented** — a real, previously-unflagged gap this audit surfaces as its single highest-priority finding.
3. Fatigue is genuinely, entirely absent from code — design-only, consistent with its own documented "unresolved formulas" status, unchanged in the ~3 weeks since the last independent audit reached the same conclusion.
4. Totality's existing `ExhaustionManager` is a real, shipping, but untested and currently Stamina-only combat mechanic, correctly distinct from both vanilla hunger-exhaustion and the future Fatigue concept — with one confirmed dead-code bug affecting player-rejoin behavior.
5. The Generic Player Resource registry (7 resources, all `EXTERNAL_ADAPTER`) is provably safe against accidental dormant-resource instantiation today, though that safety currently rests on the absence of any `GENERIC_COMPONENT` production resource ever having exercised the relevant code paths under real gameplay load, and on the absence of any formal entitlement-enforcement layer (which itself remains a separate, unimplemented design).
6. No canonical document contradicts current code anywhere this audit checked — the design corpus is internally consistent and explicitly anticipates exactly the kind of future work this task is scoping.

---

## 31. Unresolved Questions

Explicitly flagged as **[UNKNOWN]**, not silently resolved:

- Whether any Totality ability/spell/effect beyond the ones the five research passes checked implements a passive heal-over-time mechanic that would interact with a Health-regen-disable pass (§8, Q4).
- Whether saturation and vanilla hunger-exhaustion should migrate proportionally or stay vanilla-mirrored under Option 3 (§15) — this audit recommends the latter but the decision is Stefan's.
- Whether any current Totality mob/animal needs `TotalityFoodItem`-compatible consumption (§22).
- The exact final formulas, thresholds, and data model for Fatigue/Rest Need — explicitly and repeatedly marked "genuinely undecided" by the canonical document itself (§16), not something this audit could or should resolve.
- Whether "Winded" should ever become a distinct code-level enum value/state rather than just a documentation label for the existing `WARNING` tier (§18) — a naming decision, not a technical blocker either way.

---

## 32. Appendix: Search Terms and Evidence Index

Representative (not exhaustive) search terms used across this audit's five research passes and this author's own direct reading, for traceability:

`getFoodLevel`, `setFoodLevel`, `FoodData`, `foodLevel`, `saturationLevel`, `exhaustionLevel`, `addExhaustion`, `getExhaustionLevel`, `FoodProperties`, `FoodResourceAdapter`, `PlayerResourceIds`, `ProductionResourceDefinitions`, `TotalityFoodItem`, `ConsumableItem`, `AbstractConsumable`, `fatigue`/`Fatigue`, `tired`/`tiredness`, `restNeed`/`RestNeed`, `overwork`, `Exhaustion`/`Exhausted`, `Winded`/`winded`, `ExhaustionManager`, `ExhaustionState`, `naturalRegeneration`/`NATURAL_HEALTH_REGENERATION`, `instantiateScalar`/`instantiatePartitioned`, `dormant`/`Dormant`, `respawn`/`clone(`/`changeDimension`, `Thirst`, `Sanity`, `Ki`, `Solar Charge`, `Chakra`, `Pact Magic`, `Cursed Energy`, `Reiatsu`/`Reiryoku`, `Haki`, `Hamon`, `Radiation`.

Vanilla classes decompiled: `net.minecraft.world.food.FoodData`, `FoodConstants`, `FoodProperties` (+`$Builder`), `Foods`, `ClientboundSetHealthPacket`, `ClientboundPlayerCombatKillPacket`, `ClientboundSetExperiencePacket`, `Player`, `LivingEntity`, `LocalPlayer`, `ServerPlayer`, `GameRules`, `DamageSources`, `DamageTypes`, `Consumable` — extracted from `minecraft-merged-deobf-26.2.jar` at `C:/Users/andre/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar`, disassembled via `javap -p -c -constants`, scratch files deleted after analysis (no repository files touched).

Canonical documents read in full or targeted-grepped: see §4.
