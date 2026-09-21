# Totality Food 0-100 Authority & TotalityFoodItem — Implementation Report (2026-09-17)

> **This report has been corrected six times, all on 2026-09-17** (the same day as the original
> implementation — all seven passes happened today). The **first correction pass** fixed a stale
> vanilla mirror on Generic-only mutations, a mirror hardcoded to a fixed `/5`, vanilla still owning
> sprint gating and starvation damage, and two closeable vanilla mutation-path holes — see **§31
> "First Correction Pass"**. The **second correction pass** fixed two further real bugs the
> first correction pass's own mirror/translation logic still had — see **§32 "Second Correction
> Pass"**. The **third correction pass** fixed what the first real-client manual test exposed — a
> stale 0-20 client HUD presentation path, Peaceful's automatic Food restore (now intentionally
> removed as a design decision), and missing tooltip metadata on both Pizza items — see **§33 "Third
> Correction Pass — Real-Client Manual Test Findings"**. The **fourth correction pass** fixed a second,
> independent Peaceful gap review found in §33's own fix — vanilla also suppresses *normal* hunger-
> exhaustion depletion on Peaceful — see **§34 "Fourth Correction Pass — Peaceful Also Suppressed
> Normal Food Depletion"**. The **fifth correction pass** investigated a second real-client test
> showing sustained Survival sprinting/jumping not depleting Food at all, on both Peaceful and Normal
> — see **§35 "Fifth Correction Pass — Real Upstream Activity-Exhaustion Investigation"** (a *third*,
> later real-client test then confirmed real Normal-difficulty activity does deplete Food correctly —
> §35 has been updated in place to record this; the upstream movement/exhaustion chain is no longer an
> open concern). The **sixth (final) correction pass** fixed two further real-client findings: Peaceful
> was still passively regenerating Saturation, and vanilla's own zero-nutrition food-consumption path
> was silently destroying real Saturation whenever a Totality food item was eaten — see **§36 "Sixth
> (Final) Correction Pass — Peaceful Saturation Regeneration and Totality Food Consumption Saturation
> Corruption"**, the authoritative summary of the currently-true implementation state — no further
> code changes followed it. **§37 "Final Status — Real-Client Verification Complete"** (not a seventh
> correction pass — no code changed) records that the user's final real-client manual test, performed
> after §36, confirmed the whole Food system works correctly end to end. Sections below are updated in
> place where earlier text is now factually wrong; read §37 first for the final status, then §36 if you
> want the last pass's technical detail.

## 1. Starting branch/HEAD

Branch `feature/food-system`, HEAD `b74b7a6c22f41e3e3c106368ccb683bcdad27f0b`
("docs(resources): finalize V1 readiness audit") — exactly the expected commit. Confirmed via
`git rev-parse HEAD` before any work began.

## 2. Dirty-tree baseline

`git status --short` showed exactly 109 paths before any Food work — matching the expected baseline
exactly. Nothing staged.

## 3. Partial files inherited from the interrupted run

**None.** A path-by-path diff of the pre-task `git status` against the expected 109-path baseline
(reconstructed from the task's own description and cross-checked against `ModGroups.java`, which was
already untracked-but-wired into `Totality.java` before this task started) showed the interrupted run
made **zero** code changes. The only Food-adjacent artifact already present was the finished
`pizza_margherita.png` texture (untracked, dated Sep 11 — pre-existing asset work, not code from an
interrupted run). `ModGroups.java`, `iron_sword`/`petty_soul_gem`/`platinum_coin` assets, and several
test files were all untracked but confirmed to be **unrelated Soul Gem / general-infra work**, not
Food work — `ModGroups.java` had zero Food references before this task edited it.

## 4. Pre-change Food authority

`totality:food` was a **query-only `EXTERNAL_ADAPTER`** wrapping vanilla `Player.getFoodData()
.getFoodLevel()` (`FoodResourceAdapter`, `NATIVE_MAXIMUM = 20`). The HUD displayed it via a
presentation-only `ResourceDisplayConversion.HEALTH_FOOD` (×5) formatter — mechanically still 0-20,
only the *displayed number* was multiplied by 5. This is exactly the forbidden pattern the task
named ("authoritative vanilla FoodData remains 0-20, HUD just displays value ×5").

## 5. Final authoritative Food owner

> **Superseded in part by §33.2**: the sentence below still lists Peaceful restore among the
> vanilla-triggered changes translated into the true resource. That is no longer accurate — Peaceful's
> automatic Food restore is now suppressed entirely, not translated. Everything else in this section
> remains true.

`totality:food` is now a real **`GENERIC_COMPONENT`** resource (`PlayerResourceStateComponent`),
natively baseline-`100` (resolver-driven, see §31.3 — not a hardcoded ceiling), `definitionVersion =
2`. Vanilla's own `FoodData` engine (exhaustion accumulation, ordinary eating) remains the *trigger*
for every ordinary change — four `*AuthorityMixin` classes (exhaustion, ordinary `FoodProperties`
eating, Cake, the Saturation mob effect — see §31.6) translate every known vanilla `FoodData`
mutation into this resource at a fixed, exact `×5` **delta** rate (this exact rate is unaffected by
the maximum-extensibility correction — see §31.3) and write back an accurate, **proportional** mirror
(`current / max` mapped into vanilla's 0-20 domain — see §31.2) into vanilla's own `foodLevel` field.
Vanilla's Food-based **sprint gate is now removed**, its **direct starvation damage is now disabled**
(§31.4/§31.5), and (real-client correction, §33.2) its **Peaceful-difficulty automatic Food restore
is now suppressed entirely** — these are no longer "kept working against the mirror," they are
corrected/removed Totality gameplay decisions. `FoodMirrorServerTick` (§31.1) additionally re-syncs
the mirror once per player every server tick, so authoritative mutations with no vanilla call site
(Pizza, `/totality food set`, any future caller) also keep the mirror consistent — never a second,
independent authority.

`FoodResourceAdapter` remains registered in `ExternalPlayerResourceAdapterRegistry` but is no longer
referenced by the definition — the same deferred-cleanup precedent Mana/Stamina/Rage/Spell Slots
already established (`ProductionResourceDefinitions#registerAdapters`'s own Javadoc).

## 6. Why this authority was chosen (Option A)

This is **Option A** from the task's own decision list: "A dedicated Totality-owned 0-100 Food
authority exposed through `totality:food`, with vanilla FoodData used only as a compatibility
projection." It was chosen because it is **exactly the same shape** as four already-shipped,
tested, precedented migrations in this codebase — Mana, Stamina, Rage, and Standard Spell Slots all
went `EXTERNAL_ADAPTER` → `GENERIC_COMPONENT` the same way (Phases 4/5/6, 2026-09-15/16). Reusing
that exact idiom (including the durable `PlayerResourceStateComponent.isLegacyMigrated`/
`markLegacyMigrated` one-time-import marker, `PlayerBaselineResources`'s universal grant, and
`instantiateScalar(id, computedInitialValue)`) is the smallest coherent architecture the current
source already supports — no new mechanism was invented, and Generic Resource API V1's foundation
(`PlayerResourceDefinition`, `PlayerResourceService`, the grant/reconciliation machinery) was not
touched at all, only used through its existing, established extension points. V1 remains CLOSED.

Option B (reinterpreting vanilla FoodData itself as 0-100) was rejected: vanilla's `FoodData` is
hardcoded to `[0, 20]` in multiple places (`add`'s `Mth.clamp(food + foodLevel, 0, 20)`,
`readAdditionalSaveData`'s `20` default) — "safely reinterpreting" it in place is not possible
without extensive vanilla-internals surgery, which is a larger, riskier diff than Option A.

Option C (a Generic Resource-backed Food state — i.e. Option A) is what was actually built; the task
listed A and C as effectively the same outcome for Food's shape once V1 is closed.

## 7. Exact 0-100 behavior

- `absoluteMinimum = 0`. **Corrected 2026-09-17**: the maximum is *not* a literal
  `authoredBaseMaximum(100)` — it is resolved by `FoodMaximumResolver` (registered in
  `registerMaximumResolvers()`), which today simply returns `100` but is a real extension point a
  future exceptional Origin/Species/effect could use to resolve higher without touching this
  resource's definition. See §31.3.
- `unitScale = 1` — the true value natively represents `37`, `48`, `73`, `99`, `100`, etc.; there is
  no hidden 0-20 value anywhere behind it.
- Presentation conversion is now `IDENTITY`, not `HEALTH_FOOD` (5/1) — a native `73` displays as
  `73`, not `365`.
- `PlayerResourceService.restore`/`.drain` clamp at `[absoluteMinimum, resolvedMaximum]` — at the
  100 baseline, proven directly in `FoodResourceDefinitionTest` (restoring 48 onto 92 clamps at 100,
  applying only 8; draining 5 from 3 clamps at 0); at a resolved maximum above 100, proven separately
  in §32's regression tests (e.g. 140 + (nutrition 4 × 5) clamps at 150, not 100).

## 8. Save migration

Reuses the **exact** one-time-import idiom Mana/Stamina/Rage/Spell Slots already established in
`BaselineResourceLifecycleEvents.migrateLegacyIfAbsent`/`migrateOne`, guarded by
`PlayerResourceStateComponent.isLegacyMigrated`/`markLegacyMigrated` (a durable, schema-versioned,
per-resource marker distinct from mere state presence). One new line:

```java
migrateOne(state, PlayerResourceIds.FOOD, true, player.getFoodData().getFoodLevel() * 5);
```

"Legacy initialized" is unconditionally `true` (unlike Mana/Stamina's `-1` sentinel) because vanilla
`FoodData` always has a real value — even a brand-new player's default `foodLevel = 20` is a
meaningful value to import, and it naturally migrates to exactly `100`, satisfying "new players begin
in the intended current Food domain" through the *same* code path as existing-player migration, with
no special-casing. The conversion is an **exact** `×5` (100/20 = 5), never a rounding approximation:
`20→100, 18→90, 14→70, 10→50, 1→5, 0→0` — matching every locked example in the task verbatim.

Idempotency: `isLegacyMigrated` is checked *before* any legacy value is read, and is set regardless
of whether an import happened — proven in `FoodSystemVerification` (migrating a player already at
70, then externally mutating their vanilla `foodLevel` to 3, then re-running migration, leaves the
true value at 70, not re-imported as 15).

## 9. Persistence/sync/respawn handling

- **Persistence**: `totality:food` now persists through `PlayerResourceStateComponent`'s existing
  NBT read/write (schema v4), the same path Mana/Stamina/Rage already use. No new persistence code.
- **Sync**: `PlayerResourceStateComponent.writeSyncPacket`/`applySyncPacket` already include every
  `GENERIC_COMPONENT` resource automatically — Food is now transparently included, using the exact
  same wire path Mana/Stamina/Rage/Spell Slots already use. No new networking code was written.
- **Respawn**: death policy is `RESET_TO_MAXIMUM` (100), matching vanilla's own "hunger always
  refills to full on death" behavior exactly, the same override shape Mana/Stamina already used for
  their own legacy reset-on-respawn parity.
- **Reconnect/dimension change**: migration only runs on `JOIN`; `PlayerBaselineResources.reconcile`
  (also called on respawn/dimension-transfer) only instantiates if state is absent — never rescales
  an already-granted value. Proven directly in `FoodSystemVerification`.

## 10. Saturation handling

> **Updated (§36, Pizza/Saturation correction pass) — this section's original "left entirely
> vanilla-internal, untouched" framing is no longer accurate as written.** Saturation is still not a
> Generic Resource, still not canonical long-term, still not scaled to Totality's 0-100 Food domain,
> and vanilla's own exhaustion math (`if (saturationLevel > 0) saturationLevel -= 1 else <decrement
> Food>`) still runs exactly as before — Saturation absorbs vanilla hunger-exhaustion first, and only
> once depleted does authoritative Food actually drain. That much remains true and unchanged. What
> changed is that Totality no longer leaves *every* vanilla mutation of Saturation untouched: real-
> client testing found two places where vanilla's own Saturation behavior actively conflicted with
> Totality's physical Food model, and both are now deliberately mediated:
>
> - **Peaceful's automatic Saturation regeneration is suppressed** (§36.1,
>   `ServerPlayerPeacefulSaturationRestoreAuthorityMixin`) — vanilla's own
>   `ServerPlayer#tickRegeneration` regenerated Saturation by `+1.0` every second on Peaceful via a
>   direct field write bypassing `FoodData#add`'s own clamp; that conflicted with Food representing
>   physical fullness the same way Peaceful's automatic Food refill did, so it is now a no-op.
> - **`TotalityFoodItem`'s consumption path no longer lets vanilla's zero-nutrition `eat` corrupt
>   Saturation** (§36.2/§36.3) — vanilla's real `FoodData#add(int, float)` clamps `saturationLevel`
>   down to the *current* `foodLevel` mirror even for a literal `add(0, 0.0F)`, which was silently
>   destroying real Saturation on every Totality food consumption. `FoodVanillaCompatibilityBridge
>   #interceptFoodDataEat` now skips vanilla's own `eat` entirely for a zero intended nutrition, and
>   `TotalityFoodItem` instead applies its own authored, explicitly temporary Saturation contribution
>   directly (additive, clamped against the freshly-resynced post-eating mirror ceiling — never a
>   stale pre-eating one).
>
> Saturation is still owner-specific metadata (canonical §6.5's own framing), never player-facing on
> its own, and rescaling it to a 0-100 domain or promoting it to a second Food authority remains
> explicitly out of scope — this pass mediates *specific, narrow* vanilla mutation points found to
> conflict with the physical Food model, it does not adopt Saturation as a Totality-owned value.
> **This role remains explicitly transitional**: vanilla Saturation's future direction is still to be
> redesigned as/replaced by **Metabolic Reserve** during the later Diet/Metabolism/Fatigue design pass
> (§31.9) — nothing in §36 implements or begins that future system; it only corrects vanilla behavior
> that actively conflicted with Totality's current, temporary compatibility model.

## 11. Hunger-exhaustion handling

Vanilla's own exhaustion accumulation (`addExhaustion`, scattered across ~12 vanilla call sites —
sprinting, jumping, mining, etc.) is **completely untouched**. Only the *decrement it eventually
triggers* is redirected: `FoodDataExhaustionAuthorityMixin` intercepts the single
`Math.max(this.foodLevel - 1, 0)` call inside `FoodData#tick` (verified against the real decompiled
26.2 vanilla source), replacing vanilla's raw -1 with a real `-5` on the true resource, then writes
back the corrected, now-proportional mirror (§31.2). This is Totality's Stamina/Winded system's own,
separate, pre-existing Exhaustion concept — never conflated with vanilla hunger-exhaustion, and never
touched by this pass. Sprint does **not** rely on this system (§31.4) — vanilla hunger-exhaustion
remains purely a Food-depletion trigger.

## 12. Sprint/starvation/regen handling — CORRECTED 2026-09-17

- **Natural Health regen from Food**: already fully disabled by the pre-existing
  `FoodDataNaturalRegenerationMixin`/`ServerPlayerPeacefulRegenerationMixin` (Totality's accepted
  policy: Health regen only through authored systems). Untouched by this pass, and remains disabled.
- **Starvation — REMOVED, not "kept working."** The original pass left vanilla's direct starvation
  Health damage active; review correctly identified this as undesired. `FoodDataStarvationDamageAuthorityMixin`
  now redirects `player.hurtServer(level, damageSources().starve(), 1.0F)` (inside `FoodData#tick`'s
  `foodLevel <= 0` branch) to a no-op. Food can reach 0 with **no** direct HP damage. See §31.5.
- **Sprint gating — REMOVED, not "kept working proportionally."** The original pass left vanilla's
  `hasEnoughFoodToDoExhaustiveManoeuvres()` (the sole real vanilla sprint-food gate — verified to be
  called only from the client-only `LocalPlayer#isSprintingPossible`, no server-side food-sprint
  check exists anywhere in vanilla) fully active; review correctly identified this as undesired.
  `PlayerFoodSprintGateAuthorityMixin` now forces this method to always return `true`. Totality's
  Stamina system (untouched) is now the sole sprint-endurance authority. See §31.4.
- **Peaceful-difficulty auto-restore — REMOVED (real-client correction, §33.2), not "translated."**
  This bullet originally described `ServerPlayerPeacefulFoodRestoreAuthorityMixin` redirecting
  `ServerPlayer#tickRegeneration`'s `foodData.setFoodLevel(getFoodLevel() + 1)` so Peaceful's food
  auto-refill landed on the true resource. Real-client manual testing found this undesirable — Food
  represents physical fullness and must not passively regenerate on Peaceful. The mixin now redirects
  the same call site to a pure no-op instead of translating it. See §33.2.
- **Future undernutrition consequences**: intentionally absent. Reaching 0 Food has no consequence
  today beyond being empty; sustained-underfeeding consequences belong to the future Diet/
  Metabolism/Fatigue direction (§31.9), not to a new mechanic invented in this pass.

## 13. Final `totality:food` integration

`ProductionResourceDefinitions` now registers Food with no `.externalAdapter(...)` call (defaults to
`GENERIC_COMPONENT`) and, as of the 2026-09-17 correction, **no `.authoredBaseMaximum(...)` either**
— `FoodMaximumResolver` is registered in `registerMaximumResolvers()` instead (§31.3). Capabilities
`{RESTORABLE, DIRECT_DRAIN, HUD_VISIBLE, MENU_VISIBLE}` (no `SPENDABLE` — nothing "spends" Food the
way a spell spends Mana), `IDENTITY` presentation, `RESET_TO_MAXIMUM` death policy,
`definitionVersion = 2`. `PlayerBaselineResources` (the same universal grant Mana/Stamina use) now
also grants Food. The HUD (`TotalityHudRenderer`) now resolves Food through
`ClientResourcePresentationResolver.INSTANCE.resolveScalar` — the same modern per-tick resolver
Stamina/Mana already use — instead of reading `client.player.getFoodData().getFoodLevel()` directly
and applying a hardcoded ×5. The vanilla read is now only the resolver's *fallback* supplier (used
before the first Generic sync packet arrives).

## 14. `TotalityFoodItem` API

> **Updated (§36, Pizza/Saturation correction pass):** the constructor gained a
> `temporarySaturationRestoration` parameter, and the stale claim below that zero-nutrition
> `FoodProperties` makes vanilla `FoodData` "harmlessly unchanged" is corrected — see §36.2/§36.3 for
> the real bug this section's original wording missed (vanilla's own zero-nutrition `eat` was
> silently corrupting Saturation).

```java
public class TotalityFoodItem extends Item {
    public TotalityFoodItem(Item.Properties properties, long foodRestoration,
                             float temporarySaturationRestoration, float consumeSeconds)
}
```

Internally attaches a zero-nutrition, `canAlwaysEat = true` `FoodProperties` — still needed for two
real reasons, both independent of Food itself: `canAlwaysEat = true` makes `Consumable#canConsume`/
`Player#canEat` return `true` unconditionally, and merely having a `FoodProperties` component present
at all is what satisfies `InventoryActionHandler`'s `stack.has(DataComponents.FOOD)` gate for the
inventory quick-use path. `FoodProperties#onConsume` normally calls vanilla `FoodData#eat` for *any*
item that carries the component, regardless of its values — zero nutrition does **not** by itself make
that call harmless, since vanilla's real `FoodData#add(int, float)` still clamps `saturationLevel` down
to the current `foodLevel` mirror even for a literal `add(0, 0.0F)`. Totality's own interception
(`FoodVanillaCompatibilityBridge#interceptFoodDataEat`, reached via the pre-existing
`FoodPropertiesEatAuthorityMixin`) now treats a zero intended nutrition as a **true no-op** — it skips
vanilla's own `eat` call entirely rather than still running it — so `FoodData#add(0, 0)` can never
clamp/corrupt existing Saturation again.

A custom `Consumable` (via `Consumable.builder().consumeSeconds(...).animation(EAT)`) supplies the
authored consume duration/animation. `finishUsingItem` calls `super.finishUsingItem` first (handles
stack consumption, sound, particles, stat/criteria triggers — all untouched by anything below), then:
restores `foodRestoration` directly onto `totality:food` via `PlayerResourceService.restore`; if a
temporary Saturation amount is authored (`temporarySaturationRestoration > 0.0F`), explicitly resyncs
the vanilla mirror (`FoodVanillaCompatibilityBridge#resyncMirrorIfStale`) so it reflects the Food this
same call just restored, then adds the authored amount to whatever Saturation already exists (never
replacing it) clamped into vanilla's own real invariant range `[0, freshMirrorFoodLevel]` — the
*current, valid* compatibility ceiling, never a stale pre-eating one and never Totality's own 0-100
Food maximum. This is the real, current MC 26.2 API shape — confirmed by reading the actual decompiled
vanilla `Item`/`Consumable`/`FoodProperties`/`FoodData` sources (`Item#use` delegates to
`Consumable#startConsuming`, `Item#finishUsingItem` delegates to `Consumable#onConsume`,
`Item#getUseDuration`/`getUseAnimation` read `Consumable#consumeTicks()`/`animation()` directly) —
not remembered older-MC APIs.

## 15. Full-Food eating behavior

`canAlwaysEat = true` makes both `Consumable#canConsume` (`player.canEat(foodProperties
.canAlwaysEat())`) and `Player#canEat(true)` return `true` unconditionally, regardless of hunger —
satisfying "eat while full" for both the normal right-click-hold path and Totality's inventory
quick-use action (`InventoryActionHandler`, which gates on `!food.canAlwaysEat() && !player
.getFoodData().needsFood()`) with zero extra gating code. `PlayerResourceService.restore` clamps at
the resolved Food maximum (baseline 100 — see §31.3/§32) — eating never overflows Food, but the item
is always consumed. Proven directly in `FoodSystemVerification`: eating a Slice at exactly the
resolved maximum keeps Food there while the stack is still consumed.

## 16. Per-food consumption-duration support

Uses MC 26.2's real `Consumable.consumeSeconds`/`consumeTicks()` mechanism directly — no bespoke
duration system. Pizza Margherita: `consumeSeconds = 16.0F` → `320` ticks. Slice:
`consumeSeconds = 2.0F` → `40` ticks. Verified directly against the real registered items in
`FoodSystemVerification` via `getUseDuration`.

## 17. Pizza Margherita

> **Stack size corrected (real-client correction pass, superseded by §36's authored temporary
> Saturation)**: the "Stack size: 1" bullet below was the original pass's placeholder default. The
> user later explicitly decided a whole Pizza should not be stack-size 1 (a whole meal stays bulkier
> than an individual slice without being excessively inventory-hostile) — the stack size is now **8**,
> thematically consistent with "one Pizza = 8 Slices." See §33/§35's history for exactly when this
> changed; §36 records the further authored temporary Saturation value added in this pass.

- ID: `totality:pizza_margherita`
- Food restored: **48**
- Temporary Saturation restored: **12.0** (added in §36 — explicitly transitional compatibility
  value, not a canonical Diet/Metabolism figure)
- Consume duration: **16.0 seconds** (320 ticks)
- Texture: `src/main/resources/assets/totality/textures/item/pizza_margherita.png` — already present
  in the repository (finished asset work), used as-is, not regenerated.
- Stack size: **8** (corrected from the original placeholder of 1 — a user-approved balance decision,
  not a reasonable-default placeholder anymore).

## 18. Pizza Margherita Slice

- ID: `totality:pizza_margherita_slice`
- Food restored: **6**
- Temporary Saturation restored: **1.5** (added in §36 — explicitly transitional compatibility
  value, not a canonical Diet/Metabolism figure; `8 x 1.5 = 12.0`, matching Pizza Margherita exactly,
  the same ratio as Food's `8 x 6 = 48`)
- Consume duration: **2.0 seconds** (40 ticks)
- Texture: **present**, at `src/main/resources/assets/totality/textures/item/
  pizza_margherita_slice.png` — user-authored, appeared during the first correction pass, used
  as-is (never overwritten, regenerated, resized, or recolored by any pass). The item, model
  (`src/main/generated/assets/totality/models/item/pizza_margherita_slice.json`, datagen'd, points at
  `totality:item/pizza_margherita_slice`), and lang entry are all fully wired and now render
  correctly with this texture in place.
- Stack size: 64 — unchanged by the Pizza Margherita stack-size decision (that change explicitly
  does not apply to the Slice).

## 19. One Food ModGroup

Added exactly one `ModGroups.FOOD` creative tab (`totality:food`, lang key
`itemGroup.totality.food` = "Totality: Food"), containing both Pizza items. No Ingredients/Meals/
Snacks/Drinks split — deliberately deferred.

## 20. Future subgroup note

Recorded in `FoodItems`' own class Javadoc and here: if Totality's food catalog eventually grows
enough to justify it, the single Food group may later split into Ingredients/Meals/Snacks/Drinks/etc.
Not implemented now.

## 21. 8-slice relationship and deferred cutting mechanic

Documented (in `FoodItems`' class Javadoc and here) as a fixed conceptual relationship — 8 × 6 Food
= 48 Food, one whole Pizza — with **no** cutting/crafting recipe implemented. Both items are
independently, directly registered and edible; the Slice is creative/dev-accessible until a future
Cooking/Processing pass defines the real portioning interaction (Knife + surface, per the one prior
design note found in `Context/totality_design_session_notes.txt`). The same ratio was extended to the
temporary Saturation values added in §36: 8 × 1.5 = 12.0, matching Pizza Margherita's own authored
temporary Saturation exactly.

## 22. Future fast-eating/risk note

Documented here and in `TotalityFoodItem`'s class Javadoc as explicitly deferred: foods have an
authored *normal* consume duration; a future system may let Skills/Abilities/Species/items/effects
accelerate eating, with consequences (choking/nausea/reduced satisfaction/debuffs) for eating
substantially faster than the authored duration. No choking API, nausea API, eating-speed stat,
keybind, fast-eat action, or risk roll was added — today's design (a plain authored
`consumeSeconds` per item) does not make any of that impossible later.

## 23. Files changed

**New (original pass, 2026-09-17):**
- `src/main/java/zcylas/totality/item/food/TotalityFoodItem.java`
- `src/main/java/zcylas/totality/init/items/FoodItems.java`
- `src/main/java/zcylas/totality/api/rpg/resources/food/FoodVanillaCompatibilityBridge.java`
- `src/main/java/zcylas/totality/mixin/FoodDataExhaustionAuthorityMixin.java`
- `src/main/java/zcylas/totality/mixin/ServerPlayerPeacefulFoodRestoreAuthorityMixin.java`
- `src/main/java/zcylas/totality/mixin/FoodPropertiesEatAuthorityMixin.java`
- `src/main/java/zcylas/totality/api/rpg/resources/verification/FoodSystemVerification.java`
- `src/test/java/zcylas/totality/api/rpg/resources/FoodResourceDefinitionTest.java`
- `src/main/generated/assets/totality/{items,models/item}/pizza_margherita{,_slice}.json` (datagen'd)

**New (2026-09-17 correction pass — see §31 for what each does):**
- `src/main/java/zcylas/totality/api/rpg/resources/food/FoodMaximumResolver.java`
- `src/main/java/zcylas/totality/networking/food/FoodMirrorServerTick.java`
- `src/main/java/zcylas/totality/mixin/PlayerFoodSprintGateAuthorityMixin.java`
- `src/main/java/zcylas/totality/mixin/FoodDataStarvationDamageAuthorityMixin.java`
- `src/main/java/zcylas/totality/mixin/CakeBlockEatAuthorityMixin.java`
- `src/main/java/zcylas/totality/mixin/SaturationMobEffectEatAuthorityMixin.java`

**Changed (original pass):**
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java` (Food
  definition + formatter)
- `src/main/java/zcylas/totality/api/rpg/resources/integration/PlayerBaselineResources.java` (Food
  grant)
- `src/main/java/zcylas/totality/networking/resource/BaselineResourceLifecycleEvents.java`
  (migration line)
- `src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceIds.java` (doc note)
- `src/main/java/zcylas/totality/api/rpg/resources/ResourceLifecyclePolicy.java` (doc accuracy fix)
- `src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java` (Food HUD
  modernization)
- `src/main/java/zcylas/totality/init/ModGroups.java` (Food tab)
- `src/main/java/zcylas/totality/init/ModItems.java`, `init/TotalityCommands.java`
  (`/totality food get|set`), `Totality.java` (verification registration)
- `src/main/java/zcylas/totality/datagen/ModModelProvider.java`,
  `datagen/ModEnglishLangProvider.java`
- `src/main/resources/totality.mixins.json`
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` (§25.2 factual status update)
- Nine pre-existing test files updated for the new authority shape (see §24/§25).

**Changed further (2026-09-17 correction pass):**
- `src/main/java/zcylas/totality/api/rpg/resources/food/FoodVanillaCompatibilityBridge.java`
  (proportional `mirrorOf(current, max)`, `resyncMirrorIfStale`, shared `interceptFoodDataEat`)
- `src/main/java/zcylas/totality/mixin/FoodPropertiesEatAuthorityMixin.java` (refactored onto the
  shared helper)
- `src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java` (removed
  `authoredBaseMaximum`, registered `FoodMaximumResolver`)
- `src/main/java/zcylas/totality/Totality.java` (`FoodMirrorServerTick.register()`)
- `src/main/java/zcylas/totality/init/TotalityCommands.java` (`/totality food set` no longer
  hardcodes an upper bound of 100; reports the actual resolved maximum)
- `src/main/java/zcylas/totality/api/rpg/resources/verification/FoodSystemVerification.java`
  (expanded from 11 to 20 checks; the stale-mirror assertion was reversed, not merely extended)
- `src/test/java/zcylas/totality/api/rpg/resources/FoodResourceDefinitionTest.java` (variable-
  maximum tests added; `mirrorOf` call sites updated to the 2-arg signature)
- `src/test/java/zcylas/totality/mixin/NaturalFoodRegenerationMixinSourceRegressionTest.java`
  (exempted `FoodSystemVerification.java`'s reflective `tickRegeneration` call from its guard)
- `src/main/resources/totality.mixins.json` (four new mixins registered)
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` (§25.2 correction)

**Changed further — second correction pass, same day (see §32; superseded as "final" by §36, four
further correction passes later):**
- `src/main/java/zcylas/totality/api/rpg/resources/food/FoodVanillaCompatibilityBridge.java`
  (endpoint-preserving `mirrorOf`; `interceptFoodDataEat` now takes the intended nutrition directly
  instead of measuring a delta)
- `src/main/java/zcylas/totality/mixin/FoodPropertiesEatAuthorityMixin.java` (passes
  `self.nutrition()`)
- `src/main/java/zcylas/totality/mixin/CakeBlockEatAuthorityMixin.java` (passes the literal `food`
  argument)
- `src/main/java/zcylas/totality/mixin/SaturationMobEffectEatAuthorityMixin.java` (passes the
  literal `food` argument)
- `src/main/java/zcylas/totality/mixin/ServerPlayerPeacefulFoodRestoreAuthorityMixin.java`
  (simplified to pass the literal semantic `+1` instead of a measured delta)
- `src/main/java/zcylas/totality/mixin/FoodDataExhaustionAuthorityMixin.java` (Javadoc only — code
  was already correct; see §32.3)
- `src/test/java/zcylas/totality/api/rpg/resources/FoodResourceDefinitionTest.java` (endpoint-
  invariant and >100-maximum near-boundary regression tests)
- `src/main/java/zcylas/totality/api/rpg/resources/verification/FoodSystemVerification.java`
  (real vanilla Apple-eating check; low-positive-Food-reaches-0 check)
- This report and `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` (date corrections,
  stale-text corrections, §32 added)

## 24. Tests added

- `FoodResourceDefinitionTest` (original 13 + 9 first-correction + 7 second-correction = **29
  tests**): GENERIC_COMPONENT authority, absolute minimum, definitionVersion, capabilities, death
  policy, IDENTITY formatter, values above 20 representable, restore/drain clamping (including "eat
  while full"), the fixed ×5 delta-translation constant, the locked migration examples, no authored
  maximum (resolver-driven), the production resolver resolves the 100 baseline, an isolated
  synthetic resolver legitimately resolves >100 without any Food-specific architecture change,
  current clamps to a resolved >100 maximum, the proportional mirror formula at both 100 and 150
  maxima, mirror clamping/fail-safe at the domain edges, **plus (second correction pass, §32)**:
  mirror is 0/20 only at true empty/full, positive interior Food never mirrors to 0, below-max
  interior Food never mirrors to 20, the full interior range at max 150 stays strictly within
  `[1,19]`, vanilla nutrition 4 at 140/150 restores to exactly 150 (not 145), Cake nutrition 2 near
  max restores correctly, and Food at a small positive value (2) can reach exactly 0 via one drain.
- `FoodSystemVerification` (original 11 + 9 first-correction + 2 second-correction = **22 checks**,
  dev-server, real `ServerPlayer`/`ItemStack`/reflection): join-time migration, idempotency,
  reconnect-safety, new-player migration, the **stale-mirror bug reproduced and then fixed** by
  `resyncMirrorIfStale`, Pizza/Slice eating (exact restoration, mirror correctness after
  reconciliation, no double restoration), eating at maximum, authored consume durations,
  `canAlwaysEat`, the exhaustion-mixin's exact math, **starvation damage disabled** (80 real ticks at
  0 Food never reduce Health), **sprint gate bypassed** (`hasEnoughFoodToDoExhaustiveManoeuvres()`
  is `true` even at 0 Food, via reflection), **Cake eating** (via reflective `CakeBlock.eat`
  invocation) and **the Saturation mob effect** (via `MobEffects.SATURATION.value().applyEffectTick`)
  both correctly update the true resource exactly once and refresh the mirror, **Peaceful
  auto-restore** (via reflective `tickRegeneration`, gated to skip gracefully off-Peaceful —
  **re-purposed in the third correction pass, §33.2, to instead assert Food stays unchanged**, since
  auto-restore is now suppressed rather than translated), **plus (second correction pass, §32)**: a
  real vanilla Apple (nutrition 4) restores exactly 20 true Food, and a small positive Food value (2)
  reaches exactly 0 via one real exhaustion-threshold event.

## 25. Verification results

Nine pre-existing JUnit tests needed updates in the original pass (mirroring the exact pattern the
Mana/Stamina/Rage/Spell Slot migrations already established for their own sibling tests — each
hardcoded "Food is EXTERNAL_ADAPTER"/"×5 formatter"/"native 0-20 ratio" assumption became a renamed,
corrected test): `PlayerResourceRegistryTest`, `PlayerResourceRegistryExternalAdapterFreezeTest`,
`PlayerResourceStateComponentExternalEntryPathTest`, `PlayerResourceStateComponentExternalSafetyTest`,
`ResourceValueFormatterRegistryTest`, `Phase3CConsumerMigrationSourceRegressionTest`,
`TotalityHudCleanupSourceRegressionTest`, `NaturalFoodRegenerationMixinSourceRegressionTest`,
`ResourceSyncManagerEligibilityTest`. The 2026-09-17 correction pass needed one further update to
`NaturalFoodRegenerationMixinSourceRegressionTest` (exempting `FoodSystemVerification`'s reflective
`tickRegeneration` call from its "no file outside the dedicated mixin references this" guard).

## 26. Full test/build/diff-check results

**First correction pass:**
- `./gradlew test`: 1654 tests, 0 failures.
- `./gradlew runServer`: `[FoodSystemVerification] All 20 self-test checks passed.`
- One real bug was found and fixed *during* this pass's own validation:
  `PlayerFoodSprintGateAuthorityMixin` was initially registered in `totality.mixins.json`'s
  client-only list; `./gradlew runServer` (a dedicated server, which never loads client-only
  mixins) correctly caught that the mixin had no effect there. Since the target method
  (`Player#hasEnoughFoodToDoExhaustiveManoeuvres`) is a common class with no server-side caller,
  moving the mixin to the common list is safe and fixes both the real client behavior and this
  pass's own ability to verify it via a dedicated-server run.

**Second correction pass — see §32 for the numbers as of that pass (not the overall final numbers —
§36 is now the actual final correction pass):**
- `./gradlew test`: **1662 tests, 0 failures**.
- `./gradlew clean build`: **BUILD SUCCESSFUL**.
- `git diff --check`: no whitespace errors (only benign pre-existing LF/CRLF autocrlf warnings,
  repo-wide, unrelated to this change).
- Dedicated dev-server run (`./gradlew runServer`, real world, real `ServerLifecycleEvents
  .SERVER_STARTED`): `[FoodSystemVerification] All 22 self-test checks passed.` Every other
  registered verification also passed: `ResourceFoundationVerification` (6),
  `BaselineResourceMigrationVerification` (11), `BarbarianRageMigrationVerification` (18),
  `StandardSpellSlotMigrationVerification` (23), `CrownOfStarsActiveInstanceActionVerification` (11),
  `HealthRecoveryDiceResourceVerification` (14) — confirming no regression to any other Generic
  Resource.

## 27. Known unrelated failures

`ProvisionerEntityBackedSmokeTest` (3/4 failed) and `OffhandAttackVerification` (3/5 failed) both
failed in the same dev-server run — exactly the two pre-existing, unrelated dev-server failures the
task explicitly named in advance. Not touched.

## 28. Manual checklist

See the final chat response for the updated 21-point real-client checklist, covering full/lowered
Food display, eating both Pizza items (amount and duration), clamping at the resolved maximum,
vanilla Apple and Cake, sprint with low/zero Food while Stamina is available, no starvation HP
damage at 0 Food, no unexpected Health regen, normal activity eventually depleting Food after
Saturation is exhausted, reconnect, dimension change, death/respawn, and the debug command.

## 29. Deferred Food/Cooking/Diet work

Cutting/portioning mechanic, fast-eating/risk system, nutrition/food-groups/quality/freshness/
spoilage/provenance, Cooking recipes/stations, portion-serving UI, advanced saturation/satiety, and
the entire future Metabolic Reserve/Metabolism/Fatigue direction (§31.9) — all explicitly out of
scope for this focused foundation pass, per the task's own instructions. Nothing beyond
documentation was added for any of these.

## 30. No Soul Gem work

Confirmed: no `SoulGemItem`/Soul Gem file was created, edited, or referenced anywhere in this pass.
The pre-existing untracked Soul Gem assets (`petty_soul_gem.*`, from a separate, earlier, unrelated
pass) were left completely untouched.

## 31. First Correction Pass — authoritative summary (superseded in part by §32)

This section is the single source of truth for everything the *first* correction pass changed. Two
of the mechanisms it introduced (the mirror formula in §31.2, and the vanilla-eating translation in
§31.6) were themselves found to have real bugs and were fixed again in the **second correction
pass — see §32**, which is authoritative for those two topics. Everything else in this
section remains true today.

### 31.1 The stale-mirror bug and its fix

**Bug**: a direct authoritative Generic Food mutation with no vanilla call site (a `TotalityFoodItem`
eating, `/totality food set`, any future caller) never refreshed vanilla's own `foodLevel`
compatibility mirror — only vanilla-triggered mutations (routed through the mixins) did. Example
from the review: Generic Food at 0, mirror at 0; a Pizza restores +48; Generic Food becomes 48, but
the mirror could remain 0 indefinitely.

**Fix**: rather than scattering a manual `syncMirror()` call into Pizza and the debug command (which
the task explicitly rejected — it would leave every future caller unsafe), a new
`zcylas.totality.networking.food.FoodMirrorServerTick` runs once per player every real server tick
(the same established `ServerTickEvents.END_SERVER_TICK`-per-player idiom `ManaServerTick`/
`StaminaServerTick` already use) and calls `FoodVanillaCompatibilityBridge.resyncMirrorIfStale`,
which recomputes the mirror from the current authoritative current/max and writes it only if it has
actually drifted. This is purely one-directional (authoritative → mirror, never the reverse), so it
can never create a feedback loop and never touches the authoritative value. Audited extension points
in the task's preferred order: (1) no existing "external-change" bridge exists on
`PlayerResourceService` itself — its mutation methods (`restore`/`drain`/`set`) call
`ResourceSyncManager.markDirty(UUID, Identifier)` after success, but that method has no `ServerPlayer`
reference and hooking it further would mean editing `PlayerResourceService`'s own mutation methods,
which is V1 foundation code; (2) no listener/hook mechanism exists anywhere in the Resource API
package (confirmed by search); (3) no centralized Food compatibility service existed yet. Given (1)-
(3) were all genuine dead ends without touching V1 foundation, (4) — a small, centralized
reconciliation mechanism reusing this codebase's own established per-tick idiom — was the correct,
smallest-diff choice, not a casual reopening of V1.

### 31.2 The mirror is now proportional, not a fixed `/5`

> **Superseded by §32.1**: the naive `round(current/max*20)` formula this subsection originally
> described falsely mapped near-boundary interior values to the true endpoints (e.g. `1/100 → 0`,
> `99/100 → 20`) — fixed in the second correction pass. The rest of this subsection (proportional,
> not fixed-`/5`; absolute deltas unaffected) remains true.

`FoodVanillaCompatibilityBridge.mirrorOf(long current, long max)` (previously `mirrorOf(long
current)`, which implicitly assumed `max == 100`) now computes
`round(current / max * 20)`, clamped to `[0, 20]`, with a zero/negative `max` failing safe to `0`
rather than dividing by zero. Locked examples verified in `FoodResourceDefinitionTest`:
`100/100 → 20`, `50/100 → 10`, `150/150 → 20`, `75/150 → 10`. This is deliberately different from
the **delta-translation** rate used for vanilla-triggered changes (`VANILLA_TO_TRUE_SCALE = 5`,
unchanged, still fixed) and from the one-time save-migration conversion (also fixed `×5`) — Food
deltas (Pizza's +48, migration's exact historical conversion, a vanilla exhaustion tick's -5) are
always authored/derived as **absolute** amounts, matching the locked canonical model's "Pizza
remains +48 regardless of maximum." Only the *mirror's own projection* of current-against-maximum
needed to become proportional — that is what requirement §3 of the review actually asked for.

### 31.3 Food's maximum is now genuinely extensible

`totality:food`'s definition no longer declares `.authoredBaseMaximum(100)`. Canonical §10.2's
central resolution path lets an authored base win outright over a registered resolver for `SCALAR`
resources — keeping the old hardcoded `100` would have silently short-circuited any future resolver,
exactly the trap `ManaMaximumResolver`/`StaminaMaximumResolver`/`RageMaximumResolver` already
document and avoid. `FoodMaximumResolver` (registered in `registerMaximumResolvers()`) is now the
maximum's real source, and today it simply returns the same `100` baseline — **no gameplay changes
at all** from this correction alone. What changes is architectural: a future exceptional Origin/
Species/transformation/effect can extend or replace this resolver (or a future generic maximum-
modifier pipeline can apply on top of it) to legitimately resolve above 100, without touching this
resource's id, authority, capabilities, or any other Food code. No such modifier was authored by
this pass — `FoodResourceDefinitionTest` proves the *pipeline* already supports it end to end using
a fully isolated `PlayerResourceRegistry`/`ResourceMaximumResolverRegistry`/`PlayerResourceService`
trio (the real production registries are never touched by these tests): a synthetic resolver
resolving 150 is honored by `resolveMaximum`, and a 140/150 player eating a 48-Food item clamps at
150, not 100.

Per the locked canonical Food model: a normal player stays at the 100 baseline; an exceptional
future maximum (120, 150) is plausible through this resolver; a Speedster-style enormous maximum
(e.g. 500) is explicitly **not** what this resolver is for — high-metabolism physiology belongs
primarily to the future Metabolic Reserve/Metabolism direction (§31.9), not to an oversized Food bar.

### 31.4 Sprint no longer depends on Food

Audited the real decompiled 26.2 vanilla source: `Player#hasEnoughFoodToDoExhaustiveManoeuvres()`
(`return this.getFoodData().hasEnoughFood() || this.getAbilities().mayfly;`) is called from exactly
one place in all of vanilla — the **client-only** `LocalPlayer#isSprintingPossible`, which gates
whether the local player's client will even attempt to start/continue sprinting. There is no
server-side food-based sprint validation anywhere in vanilla. `PlayerFoodSprintGateAuthorityMixin`
now forces this method to always return `true` (the same value vanilla itself uses for a
mayfly-enabled player), permanently satisfying vanilla's own check and leaving Totality's existing
Stamina system (`StaminaServerTick`/`TotalityMovementHandler`/`PowerSprintStateHandler` — none
touched by this pass) as the sole remaining sprint-endurance authority. Nothing else about sprinting
(jumping, riding, shallow water, every other `isSprintingPossible`/`canStartSprinting` condition) is
touched. Verified directly in `FoodSystemVerification` (via reflection, since the method is
`protected`): `hasEnoughFoodToDoExhaustiveManoeuvres()` returns `true` even at 0 Food.

One implementation detail worth recording: this mixin was initially placed in
`totality.mixins.json`'s client-only list (matching its real-world caller), which meant `./gradlew
runServer` (a dedicated server) never loaded it and the first verification run correctly failed.
Since `Player` is a common class and nothing server-side ever calls this method, moving the mixin to
the common mixin list is harmless and was the actual fix — a genuine bug this pass's own validation
process caught and corrected.

### 31.5 Vanilla starvation direct damage is disabled

Audited the real decompiled 26.2 vanilla source: `FoodData#tick`'s `foodLevel <= 0` branch calls
`player.hurtServer(level, player.damageSources().starve(), 1.0F)` after an 80-tick timer.
`FoodDataStarvationDamageAuthorityMixin` redirects only that exact call to a no-op — the surrounding
`tickTimer`/difficulty bookkeeping in the same branch is untouched (no new starvation timer, stage,
or state was introduced). Food can now reach and remain at 0 with no direct HP damage. No
replacement mechanic (timers, malnutrition debuffs, Constitution checks, Health drain) was added —
future sustained-underfeeding consequences belong to the later Diet/Metabolism/Fatigue design
(§31.9). Natural Food-based Health regeneration remains disabled, unchanged from the original pass
(`FoodDataNaturalRegenerationMixin`/`ServerPlayerPeacefulRegenerationMixin`, both pre-existing).
Verified in `FoodSystemVerification`: 80+ real `tick()` calls at 0 Food never reduce Health.

### 31.6 Vanilla mutation-path audit — Cake and the Saturation effect closed

Re-audited the real decompiled 26.2 vanilla source for every caller of `FoodData`'s mutating
methods. Confirmed exactly four call sites across all of vanilla:

1. `FoodProperties#onConsume` → `FoodData#eat(FoodProperties)` — already closed by
   `FoodPropertiesEatAuthorityMixin` in the original pass.
2. `CakeBlock#eat(...)` → `player.getFoodData().eat(2, 0.1F)` — a **known, documented gap** in the
   original pass, **closed** by the new `CakeBlockEatAuthorityMixin`.
3. `SaturationMobEffect#applyEffectTick` → `player.getFoodData().eat(amplification + 1, 1.0F)`
   (e.g. from `/effect give ... minecraft:saturation`) — also a **known, documented gap**, **closed**
   by the new `SaturationMobEffectEatAuthorityMixin`.
4. `ClientPacketListener` — merely receives the server's authoritative food level over the network
   for client-side display; not a mutation path at all.

> **Superseded by §32.2**: the paragraph below originally described measuring the real, vanilla-
> clamped delta before/after letting vanilla's own `eat` run. That measurement is itself lossy near
> vanilla's 20-point ceiling and under-restores true Food for a resolved maximum above 100 — fixed
> in the second correction pass, which changed all three "eat" mixins (plus the Peaceful-restore
> mixin) to pass the *intended* nutrition value directly instead of measuring anything.

Both new mixins reuse a single shared helper,
`FoodVanillaCompatibilityBridge#interceptFoodDataEat(FoodData, LivingEntity, Runnable)`, extracted
from `FoodPropertiesEatAuthorityMixin`'s own original measure-before/apply/measure-after/translate/
resync sequence — no duplicated logic across the three "eat" mixins. Every one measures the real,
vanilla-clamped delta (never an item's raw unclamped nutrition) and only translates for a
`ServerPlayer` (client-side prediction and non-player `LivingEntity` targets are left to vanilla's
own unmodified behavior). Verified end to end in `FoodSystemVerification`: a reflective `CakeBlock
.eat(...)` invocation and a direct `MobEffects.SATURATION.value().applyEffectTick(...)` call each
update the true resource by exactly the expected amount and refresh the mirror.

**Remaining known, narrow gap** (unchanged from the original pass, not closed by this correction):
nothing else in vanilla mutates `FoodData` directly outside these four call sites, per this audit —
no further gaps are known to exist.

### 31.7 `/totality food get|set` corrected

`set`'s argument no longer hardcodes `IntegerArgumentType.integer(0, 100)` — it now accepts any
non-negative integer, and `PlayerResourceService.set`'s own clamping against the *resolved* maximum
does the real clamping (reported back to the command's caller, including an explicit "(clamped from
requested N)" note when the input exceeded the resolved maximum). `get` was already resolver-correct
(it always read `success.snapshot().maximumUnits()` directly, never a literal `100`) and needed no
change.

### 31.8 Locked canonical Food model (recorded, not newly decided)

- **Food** represents physical fullness/hunger. Default normal-player maximum = 100 (baseline,
  resolver-driven — §31.3).
- Pizza and other foods restore authored **absolute** Food amounts, never percentage-based, and this
  never changes regardless of a player's resolved maximum (Pizza's +48 at max 150 still applies +48,
  clamped to 150 — see the regression test in §11 of `FoodResourceDefinitionTest`'s equivalent
  dev-server case, `currentFoodClampsToTheResolvedMaximumNotALiteralOneHundred`).
- Exceptional future Origins/Species/transformations/effects MAY alter Food capacity (e.g. 120 or
  150) where their physiology genuinely warrants it — no such modifier exists yet.
- High-metabolism physiology (e.g. a Speedster-style character) is explicitly **not** primarily
  represented by an enormous Food maximum — that belongs to the future Metabolic Reserve/Metabolism
  direction (§31.9).

### 31.9 Future Metabolic Reserve / Metabolism / Fatigue direction — documented only, NOT implemented

Recorded here for the future Diet/Metabolism/Fatigue design pass. **Nothing in this section was
built, and no code changes were made toward it.**

- **Food** (this pass's scope): physical fullness/hunger, 0-100 baseline, as described above.
- **Metabolic Reserve**: usable dietary/caloric energy — the eventual successor/evolution of
  vanilla Saturation (§10's "explicitly transitional" note). Not a Generic Resource today; not
  scaled to 0-100; not implemented.
- **Metabolism**: physiology-specific processing rules a future system may add — calorie
  conversion/burn rate, Metabolic Reserve capacity/use, alcohol clearance, medicine/drug clearance,
  toxins/poisons, stimulants/sedatives, other metabolized substances. None implemented.
- **Fatigue**: a future longer-term consequence system for sustained underfeeding, depleted
  Metabolic Reserve, sleep debt, overexertion, and other physiological deficits. None implemented.

This direction exists so a future extremely calorie-dense small item can provide high metabolic
energy without absurdly restoring hundreds of points of physical Food/fullness, and so accelerated
physiology can eventually metabolize alcohol/medicine/etc. differently without Food itself needing
to represent those effects. No Metabolic Reserve, Metabolism API, calorie system, Diet change,
alcohol, drug, medicine-metabolism, or Fatigue-integration code was added by this pass.

## 32. Second Correction Pass — authoritative summary (superseded in part by §33)

The first correction pass (§31) was fundamentally approved, but review found two further real
correctness bugs in the mechanisms it introduced. This section is the single source of truth for
both fixes; where §31 above still describes the pre-fix behavior for historical context, this
section is what is actually true today. No architecture was reopened: both fixes are confined to
`FoodVanillaCompatibilityBridge` and the four mixins that call it.

### 32.1 Bug #1 — the mirror did not preserve the real empty/full endpoints

**Bug**: `mirrorOf(current, max) = clamp(round(current/max*20), 0, 20)` rounds near-boundary
interior values to the true endpoints — `1/100 → round(0.2) → 0` and `99/100 → round(19.8) → 20` —
falsely reporting "truly empty" (vanilla code treats mirror `0` as starving/`needsFood()`-false) or
"truly full" (`needsFood()` gates Peaceful's restore branch on it) for a player who is actually
neither.

**Fix**: `mirrorOf` now checks the true endpoints explicitly before any rounding —
`current <= 0` always returns exactly `0`, `current >= max` always returns exactly `20` — and only
values strictly between are proportionally rounded, then clamped into the *open* range `[1, 19]`
(never touching `0` or `20`). Locked examples now hold exactly: `1/100 → 1`, `50/100 → 10`,
`99/100 → 19`, `100/100 → 20`; identically `1/150 → 1`, `75/150 → 10`, `149/150 → 19`,
`150/150 → 20`. Proven in `FoodResourceDefinitionTest`'s new endpoint-invariant tests, including an
exhaustive loop over every interior value at max 150.

### 32.2 Bug #2 — vanilla restoration was derived from the lossy mirror delta, not the intended amount

**Bug**: `interceptFoodDataEat` let vanilla's own `FoodData#eat(...)` run, then measured
`foodLevel` before and after to compute a "vanilla delta," which it multiplied by 5 and applied to
the true resource. Vanilla's `eat` clamps its own field to `[0, 20]` internally
(`FoodData#add`), so this measurement silently loses amount once the *mirror* — not the true
resource — is near its own ceiling. Concrete example the review gave: true Food 140/150 (mirror
19/20); eating something with real nutrition 4 should restore true +20 (140 → 160, clamped to
150), but vanilla's own field only moves 19 → 20 (clamped, an observed delta of +1), which the old
logic would translate as only +5, landing at 145 instead of the correct 150.

**Fix**: every call site that intercepts a vanilla `eat` now passes the **intended, authored
nutrition value** — a literal already known at that exact call site, never something measured from
vanilla's own field — directly into `translateAndResync`/`interceptFoodDataEat`:

| Call site | Intended amount passed |
|---|---|
| `FoodPropertiesEatAuthorityMixin` (ordinary vanilla food, e.g. Apple) | `self.nutrition()` |
| `CakeBlockEatAuthorityMixin` | the literal `food` argument (Cake's real nutrition, `2`) |
| `SaturationMobEffectEatAuthorityMixin` | the literal `food` argument (`amplification + 1`) |
| `ServerPlayerPeacefulFoodRestoreAuthorityMixin` | **superseded by §33.2** — this pass had it pass the literal constant `1`; the third correction pass instead removed the translation entirely and made Peaceful's restore a pure no-op |
| `FoodDataExhaustionAuthorityMixin` | unchanged — already used a fixed `-1` per event, never a lossy top-clamped measurement (decrementing only ever clamps at the *bottom*, at `0`, which Bug #1's fix already makes exact) |

`applyVanillaEat.run()` (vanilla's own `eat(...)` call) is still invoked in every case, so
vanilla's own Saturation side effect keeps updating exactly as before (§4's requirement) — only the
*authoritative Food amount* no longer comes from reading vanilla's own field. `PlayerResourceService`
itself still owns the real clamp against the resolved maximum (never vanilla's mirror). No double
application, no recursion, no feedback loop: each mixin calls the resource service exactly once per
real vanilla event, and the mirror write that follows only ever reads the *now-updated* true
current/max — it never feeds back into the true value.

Proven in `FoodResourceDefinitionTest` (isolated resolver, resolved max 150: nutrition 4 at 140/150
→ exactly 150, not 145; Cake nutrition 2 at 145/150 → exactly 150) and in `FoodSystemVerification`
(a real vanilla Apple, nutrition 4, at the production 100 baseline restores exactly +20; a small
positive Food value of 2 reaches exactly 0 via one real exhaustion event, never stranded by mirror
quantization).

### 32.3 What did NOT need to change

- `FoodDataExhaustionAuthorityMixin`'s actual redirect logic was already correct (see the table in
  §32.2) — only its Javadoc was stale (claimed vanilla sprint/starvation "keep working" against the
  mirror, and a fixed `floor(trueValue/5)` mirror; both corrected to describe the current design:
  sprint is Stamina-owned, starvation damage is disabled, the mirror is proportional).
- Vanilla Saturation's own update inside `FoodData#add` is untouched and still runs on every real
  vanilla eat — §4's explicit requirement.
- No new Metabolic Reserve/Metabolism/Fatigue code, no Origin/Species >100 modifier, no change to
  Pizza/Slice's locked absolute values, no change to the one Food ModGroup, no Soul Gem work.

### 32.4 Final validation (this pass)

- `./gradlew test`: **1662 tests, 0 failures**.
- `./gradlew clean build`: **BUILD SUCCESSFUL**.
- `git diff --check`: clean (only benign pre-existing CRLF warnings).
- `./gradlew runServer`: `[FoodSystemVerification] All 22 self-test checks passed` (20 from the
  first correction pass + 2 new: real vanilla Apple eating, low-positive-Food-reaches-0). Every
  other registered resource verification passed unchanged. Known unrelated failures unchanged:
  `ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification` — not touched.

## 33. Third Correction Pass — Real-Client Manual Test Findings (superseded in part by §34, §35, §36)

The first two correction passes (§31, §32) were validated entirely by the automated test suite and
the dedicated dev-server verification harness — **the first real-client manual test** (an actual
Minecraft client connected to a real server) then exposed two genuine bugs the automated suite could
not catch, plus one missed integration. This section is the single source of truth for all three;
where §5, §12, and §32.2's table above still describe the pre-fix behavior, they are annotated
in place pointing here, not silently rewritten.

### 33.1 Bug #3 — the client Food HUD displayed vanilla's stale 0-20 mirror, not the true resource

**Observed**: on entering the game, the Totality Food HUD showed `20 / 20` instead of `100 / 100`.
After running `/totality food set 50`, it showed `10 / 20` instead of `50 / 100`. The server-side
Food mechanics themselves (restoration amounts, durations, migration) were already correct — only
the client's *presentation* of the value was wrong, and by exactly vanilla's `×5` mirror ratio in
reverse (20 = 100/5, 10 = 50/5), which is the signature of the client reading vanilla's compatibility
mirror instead of the true resource.

**Root cause, found by tracing the actual client query path (not by guessing and multiplying by 5)**:
`TotalityHudRenderer` was already correctly calling
`ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.FOOD, ...)` — the same
Phase 3C façade Mana/Stamina already use, with a vanilla-mirror-derived fallback (`foodLevel * 5`,
baseline max `100`) intended only for use *before* the first Generic sync packet arrives. That
resolver in turn calls `ClientResourceService.queryScalar(FOOD)`, which dispatches to whichever
reader `TotalityClientResourceReaders.register()` registered for `totality:food`. That registration
was the actual bug: it registered Food with `NativeClientResourceReader` — a Phase 3B-1 client reader
that answers straight out of vanilla `FoodData` (`access.foodLevel()`, native ceiling `20`) — instead
of `GenericSyncClientResourceReader`, the reader Mana/Stamina/Rage/Spell Slots already use to read
the real synced Generic Resource state. This registration was never updated when the Food 0-100
migration (§1-§32 above) changed Food's authority from `EXTERNAL_ADAPTER` to `GENERIC_COMPONENT` —
it is a stale *client wiring* bug, not a stale comment and not a server-side bug. Because
`NativeClientResourceReader` always returned a structurally valid `Scalar` for Food (a real
`foodLevel` between 0 and 20 is never "malformed"), `ClientResourceService` never fell through to the
resolver's legacy fallback at all — the vanilla mirror silently won every single query, which is
exactly the observed behavior.

A previous documentation-only cleanup pass (same day, `feature/food-system`) had noticed
`NativeClientResourceReader` still contained this pre-migration Food-handling code and considered it
"believed to be unused," based on `TotalityClientResourceReaders`' own Javadoc claiming "no production
consumer queries the façade yet." That claim was itself stale — `TotalityHudRenderer`'s separate,
earlier Phase 3C migration had already made the HUD a real consumer of this façade for Mana/Stamina,
and the Food 0-100 migration later made it one for Food too, without anyone updating either the
wiring or that comment. This pass is the direct verification that cleanup pass called for, and it
confirms the code was genuinely live and genuinely wrong, not dead.

**Fix**: `TotalityClientResourceReaders.register()` now registers `totality:food` with the
generic-synchronized reader, in the same group as Mana/Stamina/Rage/Spell Slots — Health and Breath
remain on the native reader, since they are still genuinely `EXTERNAL_ADAPTER`. To make this class of
bug structurally harder to reintroduce, `NativeClientResourceReader` no longer has a Food-handling
branch at all (previously `if (id.equals(PlayerResourceIds.FOOD)) { ... }`, using
`FoodResourceAdapter.NATIVE_MAXIMUM`) — a Food query reaching this reader now falls through to the
same `CLIENT_SOURCE_NOT_CONFIGURED` case any other unrecognized resource id would, rather than
silently succeeding with a stale value. The now-unused `FoodResourceAdapter` import was removed from
this file; `FoodResourceAdapter` itself remains registered elsewhere exactly as before (§5).

No Generic Resource API V1 foundation code was touched — the fix is entirely in Phase 3B-1/3C client
wiring layered on top of V1, exactly the "stale Food client integration/registration" category the
task asked to prefer over touching foundation code.

Proven in `ClientResourceServiceTest#foodResourceRepresentsTrueValueNotRawVanillaMirror` (a synthetic
full-sync payload of 100/100 and 50/100 through the real `GenericSyncClientResourceReader` class
production code now uses for Food, asserting the query returns the true values, never vanilla's
mirror) and `#foodResourceRepresentsTrueValueAtAResolvedMaximumAboveOneHundred` (75/150),
`NativeClientResourceReaderTest#foodIsNoLongerAnsweredByTheNativeReaderAfterTheFoodMigration` (a Food
query against this reader now returns `Unavailable(CLIENT_SOURCE_NOT_CONFIGURED)` at any
`foodLevel`), five new tests in `ClientResourcePresentationResolverTest` (true 100/100, true 50/100,
true 75/150 at a future resolved maximum, the legacy vanilla-mirror fallback is never evaluated once
Generic Food exists, and the fallback still works correctly before the first sync), and a source-text
sentinel in `TotalityClientResourceReadersSourceRegressionTest` pinning the exact registration line.
**This is a client-side presentation fix only — it has not yet been confirmed against a real
Minecraft client by the user. Manual re-verification of the actual HUD is required before this bug
can be considered closed** (see §33.5).

### 33.2 Peaceful's automatic Food restore is now intentionally removed

**Observed**: on a Peaceful-difficulty world, after setting Food to 50 via the debug command, Food
began climbing back up on its own. This was explained by the (at the time, deliberately preserved)
compatibility bridge translating vanilla Peaceful's `+1` auto-refill into a true `+5` — a decision
made in the first correction pass (§31) that real-client testing has now shown is undesirable.

**New canonical rule**: Peaceful difficulty does **not** automatically restore Food. Food represents
physical fullness/hunger, and being on Peaceful is not a reason for a player's stomach to fill
itself. At Food 50, waiting on Peaceful now leaves Food at exactly 50 unless some real authored Food
effect changes it; at Food 0, there is still no starvation HP damage, no automatic Peaceful refill, no
sprint dependency, and no natural Health regen from Food — Food simply stays at 0. Future
consequences for prolonged underfeeding remain deferred to the Diet/Metabolism/Metabolic
Reserve/Fatigue direction (§31.9), unchanged.

**Fix**: `ServerPlayerPeacefulFoodRestoreAuthorityMixin` still redirects the exact same call site
(`FoodData;setFoodLevel(I)V` inside `ServerPlayer#tickRegeneration`), but the redirect body is now a
pure no-op instead of calling `FoodVanillaCompatibilityBridge.translateAndResync(player, 1, ...)` —
neither vanilla's own field nor the true resource is touched by this call site anymore. This is the
smallest possible correct implementation: no new gate, flag, or state was introduced, and the mixin's
target/registration are completely unchanged (`totality.mixins.json` needed no edit). The sibling
`ServerPlayerPeacefulRegenerationMixin` (Peaceful's automatic Health heal) remains untouched.

> **Superseded by §36.1**: the sentence below originally said Peaceful's own saturation-restore
> statement "remain[ed] untouched, exactly as before" — that was accurate at the time (a deliberate
> scope decision, not an oversight), but real-client testing later found Peaceful still passively
> regenerating Saturation conflicted with Totality's physical Food model just as much as the Food
> refill did. §36.1 closes that statement too, with its own dedicated mixin.

Peaceful's own saturation-restore statement remained untouched, exactly as before, **as of this pass**.

Every other vanilla-triggered translation (exhaustion, ordinary eating, Cake, the Saturation mob
effect) is unaffected — this correction touches only the one Peaceful-Food call site.

Proven directly in `FoodSystemVerification`'s existing Peaceful check, re-purposed rather than
removed: it now asserts Food stays at exactly 70 (not 75) and the vanilla mirror is unchanged after a
real `tickRegeneration()` call on Peaceful, confirmed passing against a real dev server
(`[FoodSystemVerification] All 22 self-test checks passed` — same check count as before; the
assertion inside one existing check changed, no check was added or removed here).

### 33.3 Pizza tooltip integration was missing entirely

**Observed**: neither Pizza Margherita nor Pizza Margherita Slice carried any Totality tooltip
metadata — an integration step the original implementation pass (§1-§23) never did, unrelated to
the Food 0-100 authority work itself.

**Fix**: both items now set the same three components every other opted-in Totality item uses
(`IngredientItems` is the direct template for this exact convention): `RarityComponent(ItemRarity
.COMMON)`, `ItemTypeComponent(ItemType.FOOD)`, and `LoreComponent` with the authored English text:

- Pizza Margherita: "A classic pizza topped with tomato, mozzarella, and basil."
- Pizza Margherita Slice: "A slice of a classic Margherita pizza."

No new tooltip API was invented and the Tooltip renderer itself was not touched — both items opt into
the existing custom tooltip renderer through the same documented `ItemComponents.hasTooltipPresentation`
"carries `RARITY`" compatibility fallback `IngredientItems` already relies on, so they automatically
render with the existing Food tooltip theme/border/color. The newer `ClassificationsComponent`/
`TooltipProfileComponent` explicit-opt-in convention (seen in some other, later-registered items) was
deliberately not introduced here, matching the task's "do not invent new tooltip APIs" instruction and
mirroring the established, still-supported `IngredientItems` pattern instead.

Proven with a source-text regression sentinel (`FoodItemsTooltipMetadataSourceRegressionTest`) rather
than a runtime component-lookup test: both `FoodItems`' registrations and `ItemComponents`' static
component-type fields are only populated once `Totality.onInitialize()` runs, which never happens
under plain JUnit — this is the same constraint `TooltipApiFoundationSourceRegressionTest` documents
for the rest of this codebase's tooltip-metadata coverage. **The rendered tooltip has not yet been
visually confirmed by the user** (see §33.5).

### 33.4 What did NOT change

- Pizza restores +48 Food, Slice restores +6 Food, consume durations remain 16.0s/2.0s — all
  confirmed already correct by the real-client test and untouched by this pass.
- Both Pizza items remain eatable while full; restoration still clamps at the resolved Food maximum.
- Ordinary vanilla foods, Cake, and the Saturation mob effect are still bridged exactly as §32
  describes — only the Peaceful-restore call site changed.
- The endpoint-preserving mirror (§32.1), the exhaustion `-5` translation, and the server-tick
  authoritative → mirror reconciliation (`FoodMirrorServerTick`) are all untouched.
- No starvation HP damage, no natural Food-based Health regen, Stamina still owns sprint — unchanged.
- `FoodMaximumResolver`'s 100 baseline and future >100 support, migration behavior, the one Food
  ModGroup, and both Pizza textures are all untouched.
- No Generic Player Resource API V1 foundation code was touched.
- No Soul Gem code or asset was touched.

### 33.5 Final validation (this pass) and honest manual-test status

- `./gradlew test`: **1672 tests, 0 failures** (1662 carried over + 10 new: 2 removed/1 added in
  `NativeClientResourceReaderTest`, 2 added in `ClientResourceServiceTest`, 5 added in
  `ClientResourcePresentationResolverTest`, 2 in the new
  `TotalityClientResourceReadersSourceRegressionTest`, 3 in the new
  `FoodItemsTooltipMetadataSourceRegressionTest`).
- `./gradlew clean build`: **BUILD SUCCESSFUL**.
- `git diff --check`: clean (only benign pre-existing CRLF warnings).
- `./gradlew runServer`: `[FoodSystemVerification] All 22 self-test checks passed` (the Peaceful
  check's assertion was updated, not its count). Every other registered resource verification passed
  unchanged. Known unrelated failures reproduced exactly as before, unrelated to this pass:
  `ProvisionerEntityBackedSmokeTest` (3/4 failed), `OffhandAttackVerification` (3/5 failed).
- **Honest manual-test status, per the task's explicit instruction not to falsify results (status at
  the time this pass was written — see §37 for the final, fully-confirmed real-client outcome):**
  - **PASS / apparently correct** (from the user's own first real-client test, unchanged by this
    pass): Pizza/Slice restoration amounts and consumption durations.
  - **FAILED, now fixed but NOT yet re-verified by the user on a real client**: the HUD showing
    20/20 instead of 100/100, and 10/20 instead of 50/100 after `/totality food set 50` (§33.1).
  - **OBSERVED / design changed, NOT yet re-verified by the user on a real client**: Peaceful
    automatically restoring Food — this behavior is now intentionally removed (§33.2).
  - **Newly added this pass, NOT yet visually confirmed by the user**: Pizza/Slice tooltip metadata
    (§33.3).
  - Everything else in this report (sprint bypass, starvation disabled, natural regen disabled,
    Saturation bridging, exhaustion translation, migration, mirror endpoints, >100 maximum support)
    remains only automated-test-verified and dev-server-verified, exactly as §32.4 already stated —
    unchanged by this pass, and still not independently reconfirmed by a real-client manual test
    beyond what the user's own first real-client pass already covered.

## 34. Fourth Correction Pass — Peaceful Also Suppressed Normal Food Depletion

§33.2 correctly removed Peaceful's automatic Food *refill*. Review of that fix found a second,
independent Peaceful-specific gap: vanilla itself also suppresses *normal* hunger-exhaustion
depletion on Peaceful, so a Peaceful player's Food could never drop through ordinary play at all —
`FoodSystemVerification`'s own exhaustion checks even skipped themselves on Peaceful for exactly this
reason ("exhaustion never decrements Food on Peaceful (vanilla `FoodData#tick`'s own gate)"). This
conflicts with Totality's canonical Food model: Food represents physical fullness, and difficulty must
not determine whether it depletes. Only Peaceful's undesired auto-refill/Health-healing behaviors are
meant to differ from other difficulties — not physical depletion itself.

### 34.1 Exact vanilla root cause

Audited the real decompiled 26.2 `FoodData#tick`:

```java
if (this.exhaustionLevel > 4.0F) {
    this.exhaustionLevel -= 4.0F;
    if (this.saturationLevel > 0.0F) {
        this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
    } else if (difficulty != Difficulty.PEACEFUL) {
        this.foodLevel = Math.max(this.foodLevel - 1, 0);
    }
}
```

Once Saturation is exhausted, the `else if (difficulty != Difficulty.PEACEFUL)` guard means vanilla
never even reaches `this.foodLevel = Math.max(this.foodLevel - 1, 0);` on Peaceful — the statement
`FoodDataExhaustionAuthorityMixin` already redirects (`Math.max(int, int)`) is simply never called on
Peaceful, not because of anything in this codebase, but because vanilla's own bytecode never executes
that branch. `difficulty` is read once, at the top of `tick` (`Difficulty difficulty =
level.getDifficulty();`), and used in exactly two places: this guard, and the `foodLevel <= 0`
starvation branch's decision of whether to call `hurtServer` (already unconditionally redirected to a
no-op by `FoodDataStarvationDamageAuthorityMixin`, regardless of that decision).

### 34.2 Fix — `FoodDataPeacefulExhaustionDepletionAuthorityMixin`

A new, single-purpose mixin (`src/main/java/zcylas/totality/mixin/FoodDataPeacefulExhaustionDepletionAuthorityMixin.java`,
registered in `totality.mixins.json` immediately after `FoodDataExhaustionAuthorityMixin`) substitutes
`Difficulty.EASY` for `Difficulty.PEACEFUL` at the single `difficulty` local's STORE inside
`FoodData#tick`, using the exact same `@ModifyVariable(method = "tick", at = @At("STORE"), ordinal =
0)` idiom `FoodDataNaturalRegenerationMixin` already established for its own local boolean:

```java
@ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
private Difficulty totality$treatPeacefulAsNonPeacefulForExhaustionDepletion(Difficulty difficulty) {
    return difficulty == Difficulty.PEACEFUL ? Difficulty.EASY : difficulty;
}
```

This unblocks the exhaustion-decrement branch — vanilla's own `Math.max(int, int)` call now executes
on Peaceful exactly as on any other difficulty, so `FoodDataExhaustionAuthorityMixin`'s existing,
already-tested redirect (never itself modified) runs and translates the event into a real `-5` on the
true resource, exactly as it already did for every other difficulty. No second interception, no
duplicated `tick()` logic, and no new hunger timer was introduced — the fix is confined to making
vanilla's own existing branch reachable.

Since `difficulty` is read in exactly one other place (the starvation branch's `hurtServer`-gating
condition, itself already a no-op via `FoodDataStarvationDamageAuthorityMixin`, with `tickTimer`
bookkeeping in that branch independent of `difficulty`'s value), substituting the local's value has
zero observable effect there — confirmed by §34.4's dedicated "starvation still disabled on Peaceful"
check, unaffected by this change.

### 34.3 Resulting Peaceful model

- Saturation absorbs hunger-exhaustion depletion first, identically to every other difficulty.
- Once Saturation is exhausted, authoritative Food now drains by 5 on a qualifying exhaustion-
  threshold event, and vanilla's compatibility mirror re-syncs proportionally — identically to every
  other difficulty.
- A small positive Food value (e.g. 2) can still reach exactly 0 via one qualifying depletion event.
- Peaceful's automatic Food refill remains disabled (§33.2, untouched).
- Vanilla's direct starvation HP damage remains disabled (unchanged, `FoodDataStarvationDamageAuthorityMixin`).
- Natural Food-based Health regeneration remains disabled (unchanged, pre-existing mixins).
- Stamina remains the sole sprint-endurance authority (unchanged, `PlayerFoodSprintGateAuthorityMixin`).
- No Diet/Metabolism/Fatigue work was added — Saturation remains temporary compatibility machinery,
  unchanged from §10's framing.

### 34.4 `FoodSystemVerification` rework

The two pre-existing exhaustion checks that used to skip themselves on Peaceful now run unconditionally
at the dev server's actual current difficulty (`easy`, per `run/server.properties`) — their stale
Peaceful-skip clauses were removed rather than reworked, since exhaustion no longer needs special-
casing there at all. A new, dedicated block runs entirely under a real, server-forced
`Difficulty.PEACEFUL` (`server.setDifficulty(Difficulty.PEACEFUL, true)`, restored to the original
difficulty in a `finally` block regardless of outcome, so no other verification in the same server
process observes a changed difficulty) — this proves the new rule deterministically rather than only
when the dev server happens to already be configured for Peaceful. Five new checks: Saturation
absorbs depletion first, Food drains by 5 once Saturation is 0, a low positive Food value reaches
exactly 0, starvation damage remains disabled, and natural Health regen remains disabled — plus the
pre-existing auto-restore-disabled check, now run inside the same forced-Peaceful block instead of
skipping itself when the ambient difficulty wasn't already Peaceful.

### 34.5 Validation (this pass)

- `./gradlew test`: **1672 tests, 0 failures** (unchanged — no new plain-JUnit tests were needed;
  this correction is proven entirely via the real dev-server verification below).
- `./gradlew clean build`: **BUILD SUCCESSFUL**.
- `git diff --check`: clean (only benign pre-existing CRLF warnings).
- `./gradlew runServer`: `[FoodSystemVerification] All 27 self-test checks passed` (22 carried over +
  5 new, all passing under a real, server-forced `Difficulty.PEACEFUL`). Every other registered
  resource verification passed unchanged. Known unrelated failures reproduced exactly as before,
  unrelated to this pass: `ProvisionerEntityBackedSmokeTest` (3/4 failed), `OffhandAttackVerification`
  (3/5 failed).
- No Generic Player Resource API V1 foundation code was touched; the HUD/client reader fix (§33.1) and
  Pizza tooltip metadata (§33.3) were not modified and remain covered by their own existing tests,
  all still passing.

## 35. Fifth Correction Pass — Real Upstream Activity-Exhaustion Investigation

> **Status update (sixth correction pass, §36): RESOLVED.** A third, later real-client test
> confirmed that in Survival on Normal difficulty, real running eventually caused visible Totality
> Food to decrease — proving the actual movement-packet path, server sprint/movement exhaustion,
> `causeFoodExhaustion`, `FoodData#addExhaustion`, and the downstream exhaustion -> Saturation -> Food
> chain all genuinely work in the real client, exactly as this section's own new "REAL ACTIVITY" tests
> already proved they should. The concern below that real network movement might not be reaching the
> exhaustion machinery is no longer an open question — no further upstream movement/exhaustion
> investigation is needed. `/totality food debug` remains in the codebase for its ongoing debugging
> value. The remaining, now-separately-diagnosed issues (Peaceful automatically regenerating
> Saturation, and Totality food consumption silently corrupting Saturation) are §36's subject.

The user ran a second real-client manual test after §34's Peaceful-depletion fix. The first attempt
was in Creative and was correctly discarded — vanilla does not accumulate ordinary hunger exhaustion
for an invulnerable player, so Creative never depleting Food is expected, not a bug. Switching to
**Survival**, the user sprinted and jumped continuously for several minutes on **both Peaceful and
Normal** — Food did not decrease at all, on either difficulty. Since Normal was never affected by
§34's Peaceful-only fix, this proved the bug was not (only) the difficulty gate §34 already fixed —
it was something further upstream, common to every difficulty: real player activity was not reaching
the exhaustion machinery in the first place.

**Why the existing tests could not see this**: every exhaustion check in §11/§12/§31.6/§32/§34 (and
the ones already in this report before this pass) sets up state with `FoodData#setSaturation`/
`setFoodLevel`, then injects exhaustion directly via `FoodData#addExhaustion`, then calls
`FoodData#tick`. That proves the DOWNSTREAM chain (existing exhaustion → Saturation → Food -5)
exhaustively, but it never once exercised the UPSTREAM chain a real client is supposed to drive:
real sprint/jump activity → `Player#causeFoodExhaustion` → `FoodData#addExhaustion`. This pass's own
task explicitly named that blind spot, and it was real — no existing automated check would have
caught an upstream break.

### 35.1 Investigation — the real MC 26.2 upstream activity-exhaustion chain

Audited the real decompiled 26.2 vanilla sources (not remembered older-version behavior):

- **`ServerPlayer#checkMovementStatistics(dx, dy, dz)`** (`server/level/ServerPlayer.java`) — the
  real sprint/walk/swim/climb/fly distance-tracking method. Called from exactly two places in all of
  vanilla, both inside **`ServerGamePacketListenerImpl`**'s real `ServerboundMovePlayerPacket`/
  `ServerboundMoveVehiclePacket` handlers (i.e. driven by real movement packets from a real
  connected client, not by anything in `Player`/`LivingEntity`'s own tick loop). On ground, sprinting:
  `causeFoodExhaustion(0.1F * horizontalDistanceCm * 0.01F)` — exactly `0.1` exhaustion per block of
  real sprint distance. Walking/crouching on ground: `causeFoodExhaustion(0.0F * ...)` — **exactly
  zero** in this MC version; only sprinting, swimming, and being in/under water cause ground-movement
  exhaustion. This is real, current vanilla behavior, not a Totality gap.
- **`ServerPlayer#jumpFromGround()`** — overrides `LivingEntity#jumpFromGround()` (pure jump physics,
  no world/network dependency), adds `awardStat(Stats.JUMP)` then
  `causeFoodExhaustion(isSprinting() ? 0.2F : 0.05F)`.
- **`Player#causeFoodExhaustion(float amount)`** (`world/entity/player/Player.java`) — the single
  choke point both of the above (and combat's `0.1F` hit-exhaustion) funnel through:
  `if (!this.abilities.invulnerable) { if (!this.level().isClientSide()) { this.foodData
  .addExhaustion(amount); } }`. Creative/Spectator's `abilities.invulnerable = true` is the entire
  reason Creative never accumulates exhaustion — confirmed exactly matching the user's own correct
  Creative-test conclusion.
- **`ServerPlayer#doTick()`** unconditionally calls `this.foodData.tick(this)` every real server tick
  (called from `ServerGamePacketListenerImpl#tickPlayer`, itself called from that listener's own
  `tick()`, once per real connected player per real server tick) — the exhaustion accumulated by the
  calls above is consumed here, exactly as already proven by every existing exhaustion check.

### 35.2 Totality code audit — searching the current worktree, not memory

Grepped the entire current `src/main/java` tree for every symbol in the chain above
(`causeFoodExhaustion`, `checkMovementStatistics`, `jumpFromGround`, `isSprinting`, `setSprinting`,
`ServerboundMovePlayerPacket`, `handleMovePlayer`, `handlePlayerInput`) and read every mixin
registered in `totality.mixins.json` (both the server-authoritative and client-only lists) plus every
`ServerTickEvents`/`ClientTickEvents` registration in the codebase (`StaminaServerTick`,
`FoodMirrorServerTick`, `ManaServerTick`, `ResourceSyncServerTick`, `AbilityServerTick`,
`ConditionServerTick`, `RestSessionManager`, `CrownOfStarsSpell`, `BlessSpell`,
`TotalityMovementHandler`). **Result: no Totality mixin, tick handler, or networking handler
intercepts, redirects, cancels, or otherwise touches `causeFoodExhaustion`, `FoodData#addExhaustion`,
`checkMovementStatistics`, `jumpFromGround`, `ServerPlayer#doTick`, or the real movement-packet
handling path in `ServerGamePacketListenerImpl`.** `TotalityMovementHandler`/`PowerSprintStateHandler`
(the special-key-gated Power Sprint/Super Leap/flight movement modes) only run any logic at all while
Totality's own dedicated movement key is held, and neither touches `isSprinting()`'s underlying
vanilla state or any exhaustion call. `StaminaServerTick` calls `player.setSprinting(false)` only once
Stamina reaches exactly 0 (to stop a Stamina-exhausted sprint), which momentarily desyncs server-side
sprint state from the client but does not, by itself, block exhaustion from accumulating during the
periods `isSprinting()` is legitimately true. `PlayerFoodSprintGateAuthorityMixin` only affects the
client-only `LocalPlayer#isSprintingPossible` gate (confirmed against the same decompiled source in
§31.4) and has no server-side effect on exhaustion at all.

**Conclusion**: this investigation found no code-level interception of the upstream chain anywhere in
the current Totality codebase. Per the task's own explicit A-H breakdown, this rules out B through E
being broken by anything this codebase controls — the question of whether A ("the player is actually
considered sprinting by the server") holds true during the user's specific real-client session cannot
be settled by static code reading alone, since it depends on real network packet timing/state that a
synthetic, no-op-packet-handler `TotalityFakePlayer` cannot reproduce (its own `NoopPacketHandler`
drops every inbound packet, including movement, by design — it exists for server-side automation, not
network simulation). §35.3 proves B-H are correct when the real production methods ARE invoked;
§35.5 adds a live diagnostic so the user can observe A directly during their next test.

### 35.3 Fix — no code defect found; real-production-method test coverage added instead

No source code in the exhaustion/movement chain was changed by this pass — §35.2 found nothing to
fix. What changed is test coverage: a new "REAL ACTIVITY" block in `FoodSystemVerification` (10 new
dev-server checks, all passing) now calls the exact real production methods listed in §35.1 —
`ServerPlayer#checkMovementStatistics`, `ServerPlayer#jumpFromGround` — directly on a real
`ServerPlayer` (`TotalityFakePlayer`), never `FoodData#addExhaustion` directly, closing the blind spot
§35's own investigation named. This positively proves the entire B-H chain is intact and correct when
these methods are actually invoked, including on a forced `Difficulty.PEACEFUL` (proving §34's fix
and this real-activity chain compose correctly together). A narrowly-scoped diagnostic command,
`/totality food debug`, was also added (§35.5) so the user's *next* real-client test can observe
server-side sprint state directly, rather than this investigation guessing further without evidence.

One real bug was found and fixed **in this pass's own new test**, not in production code: the first
draft of the "Saturation > 0" real-activity check reused the same player object as the preceding
sprint-distance check, which had already added +5.0 exhaustion via `checkMovementStatistics` without
ever consuming it (that check never called `tick()`). The next check's own 9 real activity+tick
repetitions then added on top of that leftover exhaustion, crossing the threshold twice instead of
once and draining Saturation by 2 instead of 1 — caught immediately by the dev-server run itself
(`saturation=4.0 (expected 5.0)`). Fixed by giving every stateful real-activity check its own fresh
`TotalityFakePlayer`, exactly like every other player-object-per-scenario check elsewhere in this
file, with a comment recorded in the source explaining why shared state across
`checkMovementStatistics`-calling checks is unsafe.

### 35.4 Design constraints upheld

- Real player activity → vanilla hunger exhaustion → vanilla Saturation → authoritative Food is
  confirmed as the correct, intended *temporary* chain until the future Metabolism/Metabolic Reserve
  system exists — nothing here changes that model.
- Stamina remains immediate exertion/endurance, completely independent of Food. No `Stamina drain ->
  Food drain` coupling was introduced — proven directly by the new "Stamina is untouched by this
  correction" check (`PlayerStaminaManager.getStamina` unchanged before/after real sprint/jump
  activity calls).
- No Food drain-every-X-ticks-while-sprinting mechanic, no second exhaustion counter, no duplicated
  movement-statistics logic, and no percentage-based Food alteration were added.
- No Generic Player Resource API V1 foundation code was touched.
- No Soul Gem code or asset was touched.

### 35.5 Diagnostic — `/totality food debug`

Added as a new subcommand under the existing `/totality food get|set` convention
(`TotalityCommands.java`). Reports, in one line: `sprinting` (`player.isSprinting()`), `onGround`,
`invulnerable` (`player.getAbilities().invulnerable`), `exhaustionLevel` (read via reflection — vanilla
`FoodData` has no public getter for it, only the write-only `addExhaustion`), `saturationLevel`
(public getter), the authoritative `totality:food` current/maximum, the vanilla compatibility mirror
`foodLevel`, and the current difficulty. Not a permanent per-tick log — it is a single on-demand
command, run whenever the tester wants a snapshot, matching the task's explicit "do not leave noisy
per-tick logging permanently enabled" instruction. Kept in the codebase after this investigation (not
removed) since it has genuine ongoing debugging value for any future Food-depletion report, exactly
the "unless it has genuine future debugging value" exception the task itself allows.

**Recommended next step for the user**: during the next real-client Survival sprint/jump test, run
`/totality food debug` before starting, once partway through, and once after — if `sprinting=true` and
`onGround=true` are confirmed while `exhaustionLevel` never increases across the readings, that would
point to something genuinely outside this investigation's reach (e.g. a fundamentally different
runtime/environment factor); if `exhaustionLevel` DOES increase but `authoritative Food` still never
drops, that would point to a translation-layer issue this pass's tests did not anticipate — either
result gives a concrete, evidence-based next step rather than further speculation.

### 35.6 What did NOT change

- Every already-fixed HUD/client-reader behavior (§33.1): Food still uses
  `GenericSyncClientResourceReader`, `NativeClientResourceReader` still does not answer Food, true
  100/100/50/100/75-150 presentation is untouched and re-confirmed passing.
- Peaceful's automatic Food refill remains disabled (§33.2); Peaceful's normal depletion fix (§34)
  is untouched and re-confirmed passing under this pass's own forced-Peaceful real-activity check.
- No starvation HP damage, no Food-based natural Health regeneration, Stamina still owns sprint
  endurance, the endpoint-preserving compatibility mirror, the semantic ×5 vanilla-nutrition
  translation, the Cake bridge, the Saturation-effect bridge, the exhaustion `-5` translation, the
  server-tick mirror reconciliation, the 100-baseline maximum resolver, >100 future support, and
  migration/idempotency are all untouched.
- Pizza Margherita (+48, 16.0s), Pizza Margherita Slice (+6, 2.0s), both edible while full, COMMON
  rarity, `ItemType.FOOD`, and the existing lore text are all untouched by this pass. Pizza Margherita's
  stack size was already changed from 1 to 8 before this pass began (a separately user-approved
  change, confirmed already present in `FoodItems.java` at the start of this pass — not touched here
  beyond confirming it and adding the source-regression sentinel in §35.7). The Slice stack size (64)
  is unchanged. Both Pizza textures are unchanged (verified byte-identical before and after this pass).

### 35.7 Pizza Margherita stack-size sentinel

`FoodItemsTooltipMetadataSourceRegressionTest` (from the previous pass) did not pin stack size, only
tooltip metadata. Added `pizzaMargheritaStackSizeIsEightNotOne` and
`pizzaMargheritaSliceStackSizeRemainsSixtyFour` to that same test class, asserting the exact source
text `new Item.Properties().stacksTo(8)` for Pizza Margherita and `.stacksTo(64)` for the Slice — a
regression sentinel for the user's explicit 1→8 decision, and confirmation the Slice was never
touched by it.

### 35.8 Final validation (this pass)

- `./gradlew test`: **1674 tests, 0 failures** (1672 carried over + 2 new: the stack-size sentinels
  in §35.7, `pizzaMargheritaStackSizeIsEightNotOne`/`pizzaMargheritaSliceStackSizeRemainsSixtyFour`).
- `./gradlew clean build`: **BUILD SUCCESSFUL**.
- `git diff --check`: clean (only benign pre-existing CRLF warnings).
- `./gradlew runServer`: `[FoodSystemVerification] All 37 self-test checks passed` (27 carried over +
  10 new REAL ACTIVITY checks, all passing — including the forced-Peaceful real-activity check).
  Every other registered resource verification passed unchanged. Known unrelated failures reproduced
  exactly as before, unrelated to this pass: `ProvisionerEntityBackedSmokeTest` (3/4 failed),
  `OffhandAttackVerification` (3/5 failed).
- **Honest status at the time this pass was written**: this pass conclusively proved the server-side
  exhaustion machinery is correct when real production methods are invoked with real activity, on
  every difficulty. It did **not** claim the user's real-client sprint/jump test would now succeed —
  that required the user to physically re-test. **Status update (§36): a later, third real-client test
  did exactly that and confirmed Normal-difficulty activity depletion genuinely works — see the note
  at the top of this section. This concern is now closed.**

## 36. Sixth (Final) Correction Pass — Peaceful Saturation Regeneration and Totality Food Consumption Saturation Corruption

A third real-client test confirmed §35's investigation was correct — real Survival running on Normal
eventually decreased Food, closing that concern for good (§35's status note above). The same testing
round surfaced two further, unrelated findings: Peaceful still appeared to regenerate Saturation, and
eating a whole Pizza appeared to reset Saturation to 0 (or otherwise misbehave). Both were audited
against the real decompiled MC 26.2 source before any fix was written, per this pass's own explicit
instruction not to assume the working hypothesis was correct without auditing first.

### 36.1 Peaceful Saturation auto-regeneration — root cause and fix

Audited the real decompiled 26.2 `ServerPlayer#tickRegeneration`:

```java
protected void tickRegeneration() {
    if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
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

The `this.foodData.setSaturation(saturation + 1.0F)` statement, inside the same `tickCount % 20 == 0`
block as the already-suppressed automatic heal, is a genuine, real, currently-live vanilla behavior:
every second on Peaceful, Saturation climbs by `+1.0` (up to a hardcoded ceiling of `20.0F`, not the
current `foodLevel`) — a **direct field write via `setSaturation`, bypassing `FoodData#add`'s own
`Mth.clamp(..., 0.0F, this.foodLevel)` ceiling entirely**. This is exactly why Peaceful can push
Saturation to 20.0F regardless of Food. §33.2's own fix for the sibling Food-refill statement
(`this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1)`, gated on `tickCount % 10 == 0`) never
touched this statement — a deliberate scope decision at the time, later found to conflict with
Totality's physical Food model the same way the Food refill did.

**Fix**: a new, single-purpose mixin, `ServerPlayerPeacefulSaturationRestoreAuthorityMixin`,
redirects only `FoodData;setSaturation(F)V` inside `tickRegeneration` to a no-op — the same
`@Redirect`-to-no-op idiom already used for the sibling heal and Food-refill statements in this exact
method. Only this one call site, inside this one method, is affected: normal eating-created Saturation
(vanilla `FoodData#add`, still running for every real-nutrition food), the Saturation mob effect, and
`TotalityFoodItem`'s own new authored temporary Saturation contribution (§36.3, a completely different
call site in a completely different class) are all untouched. Easy/Normal/Hard difficulties never
entered this `if` branch in the first place (gated on `Difficulty.PEACEFUL`), so they are unaffected
by construction, not merely by testing.

Proven directly on a real dev server (forced `Difficulty.PEACEFUL`): setting Saturation to 10.0 and
calling the real `tickRegeneration()` leaves it at exactly 10.0, not 11.0; a real exhaustion-depletion
event (Saturation 6.0 -> 5.0) followed immediately by a real `tickRegeneration()` call leaves Saturation
at exactly 5.0, never creeping back toward 6.0.

### 36.2 Totality Food consumption corrupting Saturation — root cause

Audited the real decompiled 26.2 food-consumption pipeline in full, in order:

1. **`Consumable#onConsume(Level, LivingEntity, ItemStack)`**: emits particles/sounds, awards
   `Stats.ITEM_USED`/`CriteriaTriggers.CONSUME_ITEM`, then
   `stack.getAllOfType(ConsumableListener.class).forEach(component -> component.onConsume(level, user, stack, this));`
   — dispatches to every stack component that implements `ConsumableListener` — then runs any
   authored `onConsumeEffects`, fires the eat/drink `GameEvent`, and finally `stack.consume(1, user)`
   (the real stack-shrink point).
2. **`FoodProperties`** (`record FoodProperties(int nutrition, float saturation, boolean
   canAlwaysEat) implements ConsumableListener`) — its own `onConsume` plays the consume sound, and
   **unconditionally** (whenever `user instanceof Player`) calls `player.getFoodData().eat(this)`,
   regardless of what `nutrition`/`saturation` actually are, then plays the burp sound.
3. **`FoodData#eat(FoodProperties)`** → **`FoodData#add(int food, float saturation)`**:
   ```java
   private void add(final int food, final float saturation) {
       this.foodLevel = Mth.clamp(food + this.foodLevel, 0, 20);
       this.saturationLevel = Mth.clamp(saturation + this.saturationLevel, 0.0F, this.foodLevel);
   }
   ```

**Answering the task's own audit questions directly:**

1. *Can a `FoodProperties` with nutrition `0` and saturation `0.0F` still reduce/clamp existing
   Saturation?* **Yes.**
2. *Under what exact condition?* Always, whenever `FoodData#add` runs — even `add(0, 0.0F)` still
   executes `this.saturationLevel = Mth.clamp(0.0F + this.saturationLevel, 0.0F, this.foodLevel)`,
   which clamps Saturation down to the *current* vanilla `foodLevel` mirror if that mirror happens to
   be lower than existing Saturation (e.g. true Food near 0, mirror near 0, real Saturation still 8
   from earlier play). Nothing was actually being restored, yet Saturation was still forced down.
3. *Does `TotalityFoodItem#finishUsingItem -> super.finishUsingItem(...)` trigger that code?* **Yes.**
   `TotalityFoodItem` attaches `new FoodProperties(0, 0.0F, true)` specifically so `Consumable
   #canConsume`/`Player#canEat` are satisfied and so `InventoryActionHandler`'s `stack.has
   (DataComponents.FOOD)` gate recognizes it as food — but merely having that component present is
   enough to trigger step 2 above via `ConsumableListener` dispatch, already routed through the
   pre-existing `FoodPropertiesEatAuthorityMixin`/`interceptFoodDataEat` translation point. That
   mixin's own Javadoc had explicitly (and incorrectly) assumed "this redirect is always a real `0`
   no-op" for `TotalityFoodItem` — this audit disproves that assumption.
4. *Smallest way to preserve normal UX while preventing the wrong mutation?* Removing `FoodProperties`
   entirely was considered and rejected: `InventoryActionHandler`'s `isFood` gate
   (`stack.has(DataComponents.FOOD)`) and `Consumable#canConsume`'s `canAlwaysEat` check both require
   the component to be present, so removing it would silently break the inventory quick-use path for
   both Pizza items. All of `Consumable#onConsume`'s own side effects (particles, sound, stat trigger,
   `stack.consume`) and `FoodProperties#onConsume`'s own two `playSound` calls live in the method
   bodies themselves, entirely independent of whatever `FoodData#eat` does internally — so the fix is
   isolated to the one internal mutation, not the surrounding UX.

### 36.3 Fix — skip vanilla's own eat for zero intended nutrition; author temporary Saturation deliberately

**Fix part 1** — `FoodVanillaCompatibilityBridge#interceptFoodDataEat` now returns immediately when
`intendedNutrition == 0`, never calling `applyVanillaEat` at all:

```java
public static void interceptFoodDataEat(FoodData foodData, LivingEntity user, int intendedNutrition, Runnable applyVanillaEat) {
    if (intendedNutrition == 0) {
        return;
    }
    applyVanillaEat.run();
    ...
}
```

A genuinely zero intended nutrition has nothing for vanilla to legitimately apply: skipping the call
entirely is a *true* no-op (the mirror is never written, and `translateAndResync`'s own
`vanillaDelta == 0` fast path would have produced the identical mirror value anyway), unlike the old
behavior of still running `add(0, 0.0F)` and accepting its Saturation-clamping side effect. This is
the one shared translation point already used by `FoodPropertiesEatAuthorityMixin`,
`CakeBlockEatAuthorityMixin`, and `SaturationMobEffectEatAuthorityMixin` — the latter two never pass a
genuinely zero amount in practice (Cake's nutrition is a hardcoded `2`; the Saturation effect's
`amplification + 1` is always >= `1`), so this change is effectively scoped to `TotalityFoodItem`'s
own consumption alone, with no effect on ordinary vanilla foods (whose nutrition is always > 0) or the
Cake/Saturation-effect bridges.

**Fix part 2** — `TotalityFoodItem` gained a new `temporarySaturationRestoration` constructor
parameter (explicitly a *temporary* compatibility-buffer value, not a canonical Diet/Metabolism
figure, not exposed as a Generic Resource, and not a new Saturation API — a plain `float` applied
directly to vanilla's own `FoodData#setSaturation`). In `finishUsingItem`, after restoring the true
Food resource:

```java
if (temporarySaturationRestoration > 0.0F) {
    FoodVanillaCompatibilityBridge.resyncMirrorIfStale(player);
    FoodData foodData = player.getFoodData();
    float ceiling = foodData.getFoodLevel();
    float newSaturation = Mth.clamp(
            foodData.getSaturationLevel() + temporarySaturationRestoration, 0.0F, ceiling);
    foodData.setSaturation(newSaturation);
}
```

**Ordering is the critical correctness point the task explicitly warned about**: `resyncMirrorIfStale`
is called *before* reading `foodData.getFoodLevel()` as the clamp ceiling, specifically so the ceiling
reflects the *freshly post-eating* mirror (the Food this same call just restored), never the stale
pre-eating one. Clamping a Pizza's `+12.0` against a stale `0` mirror (true Food `0` before eating)
would incorrectly floor the authored Saturation to `0` — exactly the reported bug — instead of the
correct post-eating ceiling. `resyncMirrorIfStale` is the same existing, already-tested, idempotent
method `FoodMirrorServerTick` calls every server tick; calling it eagerly here is a safe, deliberate
use of existing infrastructure, not a new mechanism. The addition is applied to whatever Saturation
the player already has (`foodData.getSaturationLevel() + temporarySaturationRestoration`), never
replacing it outright — this is exactly what fixes the reported "existing Saturation gets destroyed"
symptom, since the old zero-nutrition vanilla path was *replacing* (clamping) rather than *adding*.

**Clamp bounds respect vanilla's own real invariant** (`[0, foodLevel]`, the same ceiling
`FoodData#add` itself enforces) rather than inventing a new one — per the task's own instruction not
to use the Totality Food maximum as a Saturation ceiling, not to convert Saturation to a 0-100 scale,
and not to multiply it by 5. The authored amount is therefore a *maximum* contribution, subject to
clamping, not a guaranteed exact addition — worked example: true Food `0/100`, mirror `0/20`,
Saturation `0` -> eat whole Pizza -> true Food `48/100`, fresh mirror `10/20`
(`mirrorOf(48,100) = round(9.6) = 10`), temporary Saturation `min(0 + 12.0, 10) = 10.0` — clamped to
`10.0`, not the full `12.0`, and critically **not `0`**.

### 36.4 Authored temporary values (this pass)

| Item | Food | Temporary Saturation | Consume duration | Stack size |
|---|---|---|---|---|
| Pizza Margherita | 48 | **12.0** | 16.0s | 8 |
| Pizza Margherita Slice | 6 | **1.5** | 2.0s | 64 |

`8 x 1.5 = 12.0`, matching Pizza's own value exactly — the same "8 slices = one whole Pizza" ratio
Food already uses (`8 x 6 = 48`). **These Saturation figures are explicitly transitional**: vanilla
Saturation remains only a temporary compatibility metabolic buffer until the future Diet/Metabolism/
Metabolic Reserve system exists (§10's framing, unchanged), and these two authored values are expected
to be replaced wholesale, not refined, once that system exists. No Metabolic Reserve, Metabolism API,
or Diet system was implemented by this pass.

### 36.5 What did NOT change

- The downstream Food authority (`PlayerResourceService.restore`, `FoodMaximumResolver`, the 100
  baseline, >100 future support) — untouched.
- The client Generic-synced Food presentation (100/100, 50/100, 75/150), the endpoint-preserving
  mirror, and `FoodMirrorServerTick`'s own per-tick reconciliation — untouched (only *called* eagerly
  from one new place, never modified).
- Migration/idempotency, the semantic vanilla-nutrition `x5` translation, the vanilla exhaustion `-5`
  translation, ordinary vanilla food/Cake/Saturation-effect bridging (all still route through
  `interceptFoodDataEat`, unaffected since their intended amounts are never `0`) — untouched.
- Peaceful's normal Food depletion (§34), no Peaceful Food auto-refill (§33.2), no starvation damage,
  no Food-based natural Health regen, Stamina-owned sprint — all untouched and re-confirmed passing.
- The client HUD/reader fix (§33.1) and Pizza/Slice tooltip metadata (§33.3) — untouched.
- `/totality food debug` — untouched, still present for its ongoing debugging value.
- No Generic Player Resource API V1 foundation code was touched.
- No Diet/Metabolism/Metabolic Reserve redesign was attempted.
- No direct Stamina -> Food coupling was introduced (Stamina code was not touched by this pass at
  all).
- No Soul Gem code or asset was touched.

### 36.6 Tests added/updated

- `NaturalFoodRegenerationMixinSourceRegressionTest` — the new
  `ServerPlayerPeacefulSaturationRestoreAuthorityMixin` file added to the existing
  "no production file outside the dedicated mixin references `tickRegeneration`" guard's exemption
  list (it is a third legitimate, independent dedicated mixin on that same method).
- `FoodSystemVerification` (dev-server, 37 -> 42 checks): two new Peaceful checks (Saturation
  auto-restore disabled; Saturation stays reduced after a real depletion event instead of freely
  regenerating on the very next `tickRegeneration` opportunity) and three new EATING checks (existing
  nonzero Saturation not destroyed by a Slice; whole Pizza from empty Food/Saturation restores the
  exact worked example in §36.3; Slice from empty Food/Saturation). Two pre-existing EATING checks
  were updated, not merely patched: `finishUsingItem` now deliberately resyncs the mirror itself (for
  the Saturation clamp ceiling), so the mirror is correct immediately rather than only after
  `FoodMirrorServerTick`'s own next-tick reconciliation — the two checks that used to assert "mirror
  stays stale until reconciled" now correctly assert "mirror is already correct, and a redundant
  resync afterward is a true no-op."
- `FoodItemsBalanceSourceRegressionTest` (new file): pins the exact authored constructor arguments for
  both items, plus a direct arithmetic assertion that 8 Slices' Food and temporary Saturation equal
  one Pizza's, before any runtime clamping.

### 36.7 Validation (this pass)

- `./gradlew test`: **1677 tests, 0 failures** (1674 carried over + 3 new, all in
  `FoodItemsBalanceSourceRegressionTest`).
- `./gradlew clean build`: **BUILD SUCCESSFUL**.
- `git diff --check`: clean (only benign pre-existing CRLF warnings).
- `./gradlew runServer`: `[FoodSystemVerification] All 42 self-test checks passed` (37 carried over +
  5 new). Every other registered resource verification passed unchanged. Known unrelated failures
  reproduced exactly as before, unrelated to this pass: `ProvisionerEntityBackedSmokeTest` (3/4
  failed), `OffhandAttackVerification` (3/5 failed).

## 37. Final Status — Real-Client Verification Complete

After §36, the user performed a final real-client manual test and **confirmed the Food system works
correctly end to end.** This closes every "NOT yet re-verified"/"NOT yet visually confirmed" caveat
recorded in §33.5, §34, and §35 — those caveats were accurate *at the time each pass was written*, and
are preserved above as history rather than erased, but none of them describe the current, final state
anymore. The Food implementation is now:

**IMPLEMENTED + AUTOMATED-VERIFIED + REAL-CLIENT-VERIFIED**

### 37.1 Confirmed real-client behavior

- The Food HUD displays the true Totality 0-100 values correctly (§33.1's fix holds).
- `/totality food set` displays/syncs correctly.
- Normal-difficulty Survival running/activity eventually consumes Saturation, then Food (§35's
  upstream-chain concern is closed — no residual doubt about real movement packets reaching the
  exhaustion machinery).
- Peaceful no longer regenerates Food (§33.2) or Saturation (§36.1).
- Normal physical Food depletion still works on Peaceful once Saturation is consumed (§34).
- Eating a Pizza no longer destroys/resets existing Saturation (§36.2/§36.3).
- Pizza restores its authored Food (48) and temporary Saturation (12.0, clamped) correctly.
- The Slice restores its authored Food (6) and temporary Saturation (1.5, clamped) correctly.
- Eating while full works; item consumption still occurs correctly in every case.
- Pizza's 16.0s and the Slice's 2.0s consume durations both work.
- Pizza's stack size of 8 and the Slice's stack size of 64 both work.
- Pizza/Slice Totality tooltip metadata (Rarity/ItemType/Lore, §33.3) renders correctly.
- Sprint remains Stamina-owned, never Food-gated.
- Food reaching 0 causes no vanilla starvation HP damage.
- Food-based natural Health regeneration remains disabled.

### 37.2 What remains explicitly out of scope

The future Diet/Metabolism/Metabolic Reserve/Fatigue system is **not** implemented by any pass in
this report, including this final status update. Vanilla Saturation remains temporary compatibility
machinery (§10) — the two corrections in §36 made its *existing* behavior consistent with Totality's
physical Food model; neither began, nor claims to have begun, its eventual replacement.

### 37.3 Automated vs. real-client verification, clearly distinguished

**AUTOMATED** (this report, all passes combined, current state):
- `./gradlew test`: **1677 JUnit tests, 0 failures**.
- `./gradlew clean build`: **BUILD SUCCESSFUL**.
- `git diff --check`: clean (only benign pre-existing CRLF/LF autocrlf warnings).
- `./gradlew runServer`: **`[FoodSystemVerification] All 42 self-test checks passed`**.
- Known unrelated dev-server failures remain, and are not Food regressions —
  `ProvisionerEntityBackedSmokeTest` (3/4 failed) and `OffhandAttackVerification` (3/5 failed) are
  pre-existing, explicitly out-of-scope failures unrelated to any Food code, reproduced identically
  across every pass in this report.

**REAL CLIENT**:
- Final manual Food behavior test **passed** — see §37.1's full confirmed-behavior list.
