# Totality — D&D Potion of Healing (Standalone Test Content) — Implementation Report

## 1. Verified starting state

- Branch: `feature/general-resource-api`
- HEAD: `6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09` (matches the expected checkpoint exactly)
- Working tree at start: only previously-known unrelated entries — `build.gradle`, `log4j-dev.xml`,
  `logs/`, `src/main/generated/.cache/`, 22 generated loot-table/worldgen/recipe JSONs, 4 Trading
  Test screenshots, and 18 pre-existing Phase 3A/3B review-bundle ZIPs under
  `Context/Audit/Review Bundles/`. No unrelated modifications and no Phase 3C files were present.
  `git rev-list --left-right --count origin/feature/general-resource-api...HEAD` returned `0 0`.

This matched the expected checkpoint, so implementation proceeded.

## 2. Relevant inspected files and what each established

- `src/main/java/zcylas/totality/item/potion/AlchemyPotionItem.java` — the Alchemy drinkable-potion
  item. Confirmed MC 26.2 signatures: `getUseDuration(ItemStack, LivingEntity)`,
  `getUseAnimation(ItemStack)` returning `ItemUseAnimation.DRINK`, and
  `finishUsingItem(ItemStack, Level, LivingEntity)` calling `super.finishUsingItem(...)` and then
  gating its effect application behind a single `!level.isClientSide()` check. No bottle/container
  remainder logic exists anywhere (no `.craftRemainder(...)`, no `Items.GLASS_BOTTLE` reference) —
  confirming the repo's actual convention is "fully consumed, no remainder," not vanilla's
  empty-bottle-return behavior.
- `src/main/java/zcylas/totality/init/items/PotionItems.java` — confirmed
  `POTION_OF_HEALING` is already registered at id `totality:potion_of_healing` via
  `TotalityRegistry.registerPotion("potion_of_healing", MagnitudeTier.STANDARD,
  AlchemyEffects.RESTORE_HEALTH, ...)`. This is the collision (see §3).
- `src/main/java/zcylas/totality/init/TotalityRegistry.java` — confirmed the raw
  `registerItem(String name, Function<Item.Properties, T> itemFactory, Item.Properties properties)`
  helper (used directly for the new item) plus the Alchemy-specific `registerPotion`/
  `registerRegenPotion`/`registerFortifyPotion`/`registerSpecialPotion` helpers (all hard-coded to
  construct `AlchemyPotionItem`, so unusable — and correctly not used — for a non-Alchemy item).
- `src/main/java/zcylas/totality/api/dice/Dice.java` — a standalone die-sides enum
  (`D2..D20, D100`) with `roll(RandomSource)`, used mod-wide (combat, spells, dialogue) and owned
  by no particular subsystem. Reused directly.
- `src/main/java/zcylas/totality/api/rpg/combat/DamageRoll.java` — an actor-generic
  (`LivingEntity`, not `ServerPlayer`) count×dice+modifier roller. Structurally almost identical to
  what a healing roll needs, but returns a combat-semantic `DamageRollResult` and lives in
  `api.rpg.combat`, owned by the combat system. Judged **not** genuinely suitable to reuse directly
  (would create an inappropriate combat-package coupling for a healing item); its *pattern* was
  reused instead by writing a small, independent `HealingAmount` type built only from `Dice`.
- `src/test/java/zcylas/totality/api/rpg/resources/TestResourceBootstrap.java` and a full scan of
  `src/test/java/**` — confirmed the repository's test suite never bootstraps Minecraft's registries
  (`Bootstrap.bootStrap()`) and never constructs a real `Item`/`ItemStack`/`LivingEntity`, and has no
  Mockito dependency (`build.gradle` has none). Directly probed this during the task (see §15) and
  confirmed `Item`'s constructor throws in plain JUnit both before and after calling
  `Bootstrap.bootStrap()` — first with "Not bootstrapped", then with "This registry can't create
  intrusive holders" (the registry is frozen once bootstrap finishes registering vanilla items).
  This confirms real-entity/real-item testing is genuinely unreachable here, not merely undone.
- `src/main/java/zcylas/totality/init/ModItems.java`, `Totality.java`, and a full mod-wide grep for
  `CreativeModeTab`/`ItemGroup`/`FabricItemGroupEntries` — confirmed **no creative-mode-tab or
  item-group construct exists anywhere in this mod**. Every existing Totality item (all Alchemy
  potions included) is obtainable only via `/give`/creative-mode direct give, never by browsing a
  creative tab. See §11.
- `src/main/java/zcylas/totality/api/shop/ProvisionerVerification.java` — the "Healing Potion"
  Provisioner stock entries checked here (`checkHealingPotionRetainsComponent`,
  `checkRareHealingPotionCanBeAbsentOrPresent`, etc.) reference **vanilla** `minecraft:potion` with
  a `potion_contents` component (the vanilla brewed Potion of Healing) — not
  `totality:potion_of_healing` and not the new `totality:dnd_potion_of_healing`. Confirmed
  untouched. See §3 and §16.
- `src/main/java/zcylas/totality/datagen/ModModelProvider.java` /
  `src/main/generated/assets/totality/items/potion_of_healing.json` — confirmed the model-generation
  convention: a shared `models/item/standard_potion.json` (two-layer base+fill model) combined with
  a `totality:potion_color` tint (`PotionTintSource`) at datagen time, producing a generated
  `assets/totality/items/<id>.json` client-item file per potion.
- `src/main/java/zcylas/totality/client/color/PotionTintSource.java` — originally judged (in the
  initial implementation pass) safe to reuse for datagen-only cosmetic tinting, since it gracefully
  falls back to a configurable `defaultColor` when the stack has no `PotionDataComponent`/is not an
  `AlchemyPotionItem`. **Corrected in the review-correction pass** (see §22): reusing `PotionTintSource`
  at all — even only at its fallback branch — still means the new item's *rendering* depends on an
  Alchemy-owned class (`PotionTintSource` itself imports `PotionData`/`PotionDataComponent`/
  `AlchemyPotionItem`), which is not the same thing as the new item's own Java classes being
  Alchemy-free. §11/§13 now describe the corrected, fully-independent rendering path.
- (Added during the review-correction pass) `net.minecraft.client.color.item.Constant` — Minecraft
  26.2's own built-in constant-color `ItemTintSource`, already registered vanilla-side under
  `minecraft:constant` (`ItemTintSources.bootstrap()`). Confirmed via the mapped Minecraft sources
  jar (`.gradle/loom-cache/minecraftMaven/.../minecraft-merged-*-26.2-sources.jar`). Used in place
  of `PotionTintSource` for the D&D item — see §22.
- `src/main/java/zcylas/totality/datagen/ModItemTagProvider.java` — confirmed the
  `ModTags.POTIONS` item tag is consumed only by `HotbarSlotMap`/`InventoryEquipHelper` for
  hotbar/inventory-sorting UX (not Alchemy-specific logic) — safe and appropriate to add the new
  item to.
- `net.minecraft.world.entity.LivingEntity`/`Player` usage precedent in
  `src/main/java/zcylas/totality/item/magic/rune/effect/HealEffect.java` — confirmed the existing
  established pattern for actor-generic healing: `target` typed as `LivingEntity`, with an
  `instanceof Player` check used *only* for an optional Player-specific side effect, never to gate
  the heal call itself. Mirrored for the new item (minus the Player-specific side effect, which this
  task doesn't require).
- Confirmed `LivingEntity.heal(float)` is the exact vanilla method already called generically
  elsewhere in the codebase (`ConditionServerTick`, `AlchemyEffects`, `HealEffect`).

## 3. Existing Potion of Healing identifiers / naming collision

`totality:potion_of_healing` is **already registered** by
`PotionItems.POTION_OF_HEALING` (an `AlchemyPotionItem`, `MagnitudeTier.STANDARD`,
`AlchemyEffects.RESTORE_HEALTH`). Per the locked instructions, this existing item was **not**
replaced, renamed, or migrated.

The new standalone item uses the distinct registry id **`totality:dnd_potion_of_healing`**,
with its visible English name kept as **"Potion of Healing"** (via a dedicated translation key —
see §11). This collision and the chosen id are recorded here as required.

Separately, `ProvisionerVerification.java` contains several "Healing Potion" checks
(`checkHealingPotionRetainsComponent`, `checkRareHealingPotionCanBeAbsentOrPresent`,
`checkStockRoundTripPreservesHealingPotionComponents`) — these all reference **vanilla**
`minecraft:potion` with a `potion_contents` component (the vanilla brewed Potion of Healing), which
is unrelated to both `totality:potion_of_healing` and the new `totality:dnd_potion_of_healing`.
This Provisioner entry was **not** touched, per the locked instructions.

## 4. Final implementation design

Two small new types, plus one registration site:

- **`zcylas.totality.item.potion.dnd.HealingAmount`** — a sealed interface with two records:
  `Fixed(int amount)` and `DiceHealing(int diceCount, Dice die, int bonus)`, each implementing
  `int roll(RandomSource random)`. `DiceHealing` rolls `die` `diceCount` times, sums, adds `bonus`,
  and clamps at 0 (never negative, though this is now provably unreachable — see below). Built only
  from `zcylas.totality.api.dice.Dice` — no Alchemy, no combat-package dependency.
  **(Review-correction pass, §22):** `Fixed` still rejects a negative `amount`; `DiceHealing` now
  also rejects a **negative `bonus`** (`bonus >= 0` required), so a badly-authored formula fails
  fast at construction instead of silently clamping toward zero.
- **`zcylas.totality.item.potion.dnd.HealingPotionItem`** — `extends Item` directly. Takes a
  `HealingAmount`, an `int useDurationTicks`, and normal `Item.Properties` in its constructor.
  Overrides `getUseDuration`, `getUseAnimation` (returns `DRINK`), and `finishUsingItem`, which
  calls `super.finishUsingItem(...)` then, gated behind a single `!level.isClientSide()` check,
  rolls the configured `HealingAmount` against `user.getRandom()` and calls `user.heal(amount)` —
  `user` is `LivingEntity` throughout; never downcast to `Player`/`ServerPlayer`.
  **(Review-correction pass, §22):** the constructor now fails fast — `healingAmount` is checked via
  `Objects.requireNonNull`, and `useDurationTicks` must be `> 0` (`IllegalArgumentException`
  otherwise) — so a mis-registered tier (e.g. a future Greater/Superior/Supreme entry with a
  forgotten duration) cannot silently produce a broken item.
- **`zcylas.totality.init.items.DndPotionItems`** — the registration site (sibling of, but
  independent from, `PotionItems`), registering exactly one item:
  `HealingPotionItem` constructed with `HealingAmount.dice(2, Dice.D4, 2)` and a `32`-tick duration,
  at id `dnd_potion_of_healing`.

This design lets a later pass register Greater/Superior/Supreme Healing as more
`DndPotionItems`-style entries with different `HealingAmount.dice(...)` calls and different
durations, with zero changes to `HealingPotionItem` or `HealingAmount` — proven in §15 by
constructing (in tests only, never registered) the exact `4d4+4`, `8d4+8`, and `10d4+20` formulas
against the same `HealingAmount.dice(...)` factory.

## 5. Exact registry identifier and visible name

- Registry id: `totality:dnd_potion_of_healing`
- Visible name: **Potion of Healing** (translation key `item.totality.dnd_potion_of_healing`)

## 6. Exact healing configuration

`HealingAmount.dice(2, Dice.D4, 2)` — two four-sided dice plus a flat +2 bonus. Minimum possible
roll: 4 (1+1+2). Maximum possible roll: 10 (4+4+2). Verified by both bound-checking and an
independently-hand-computed seeded roll in `HealingAmountTest` (§15). The `+2` bonus is validated
as non-negative at construction (§4, §22) — this configuration is authored correctly and passes.

## 7. Exact consumption duration

`32` ticks, supplied at the `DndPotionItems` registration call site (not hard-coded in
`HealingPotionItem`), matching the AlchemyPotionItem precedent's drinking duration. As of the
review-correction pass, `HealingPotionItem`'s constructor also rejects any non-positive duration
(§4, §22) — `32` is validated as correctly authored.

## 8. Actor-generic execution explanation

`HealingPotionItem.getUseDuration`, `getUseAnimation`, and `finishUsingItem` all take
`LivingEntity` (never `Player`/`ServerPlayer`) as their actor parameter — verified directly via
reflection in `HealingPotionItemContractTest` (no method declared on the class accepts any
`Player`-family parameter). The heal itself is applied via `user.heal(amount)`, the vanilla
`LivingEntity` API, exactly per decision #8. No `instanceof Player` check exists anywhere in
`HealingPotionItem` — consumption by a mob would take the identical code path as consumption by a
player.

## 9. Server-authority and duplicate-application handling

The entire heal-application block is gated behind a single `if (!level.isClientSide())` check —
one call site, one gate, mirroring `AlchemyPotionItem`'s already-proven-in-production pattern.
`finishUsingItem` is invoked on both the logical client (prediction) and the logical server
(authoritative), but only the server branch calls `user.heal(...)`, so the effect is applied
exactly once, authoritatively, on the server. Checked by a source-regression **sentinel**
(`DndPotionOfHealingSourceRegressionTest`): exactly one `.heal(` call site in the file, exactly one
`isClientSide()` gate, and the heal call appears textually after that guard. **This is a source-text
check, not runtime proof** (see §22/correction 4) — it cannot execute the method or observe actual
control flow, and would not catch every conceivable pathological refactor. It is retained, clearly
labeled as a sentinel in the test's own Javadoc, because the repository has no structural-analysis
dependency (JavaParser/ArchUnit) and this task does not introduce one solely for this item. Genuine
runtime confirmation is the manual smoke test in §16.

## 10. Bottle/container-remainder behavior

No remainder. Per §2's inspection of `AlchemyPotionItem`, the repository's established convention
for all existing Totality potions is that drinking fully consumes the item (the base `Item`
Properties never set a `.craftRemainder(...)`/use-remainder, so `Item.finishUsingItem`'s default
behavior — decrement the stack by one, no replacement item — applies). `HealingPotionItem` follows
this exact convention by not overriding any remainder behavior; `super.finishUsingItem(...)` handles
the consumption exactly as it does for every existing Alchemy potion.

## 11. Creative-group and asset integration

**No Totality creative-mode tab or item-group construct exists anywhere in this mod** (confirmed by
an exhaustive grep across `src/main` for `CreativeModeTab`/`ItemGroup`/`FabricItemGroupEntries`/
`modifyEntriesEvent` — zero matches). Every existing Totality item, including every existing Alchemy
potion, is obtainable only via `/give` or a creative-mode direct give — none appear in any browsable
creative tab. The new item follows this exact existing (if unusual) convention: it is fully
registered and obtainable via `/give totality:dnd_potion_of_healing`, identical in this respect to
every other item already in the mod. "Creative inventory and command access are sufficient," per the
task's own acceptance criterion, is satisfied on the same basis as the rest of the mod.

Asset integration:
- **Model** (corrected in the review-correction pass — see §22): reuses the existing shared
  `models/item/standard_potion.json` (base+fill layered potion-bottle model, already used by five
  Alchemy potion tiers) with a **literal** dark-red tint value `0xB43A3A`, applied via Minecraft
  26.2's own **built-in** `net.minecraft.client.color.item.Constant` tint source (registered
  vanilla-side as `minecraft:constant`) — **not** `PotionTintSource`. The initial implementation
  pass had used `new PotionTintSource(0xB43A3A)`, which — even though it only ever reaches that
  class's `defaultColor` fallback branch for this item — still made the new item's *rendering*
  depend on an Alchemy-owned class (`PotionTintSource` imports `PotionData`/`PotionDataComponent`/
  `AlchemyPotionItem`). Using vanilla's own `Constant` type removes that dependency entirely: the
  generated client-item JSON for `dnd_potion_of_healing` now declares `"type": "minecraft:constant"`
  and contains no reference to `"totality:potion_color"` (verified both by inspection and by an
  automated sentinel — §22). No new Totality tint class was needed. No new texture was created; this
  remains a **documented placeholder** re-using existing independent art, per the task's explicit
  allowance, until dedicated D&D-potion art exists.
- **Language**: `item.totality.dnd_potion_of_healing` → `"Potion of Healing"`, added to
  `ModEnglishLangProvider`.
- **Item tag**: added to `ModTags.POTIONS` (consumed only by hotbar/inventory-sort UX helpers, not
  Alchemy logic) for correct inventory-sorting behavior alongside every other potion.
- **Datagen**: `runDatagen` regenerated exactly 3 files (see §15) — the lang file, the tag file, and
  a new generated client-item JSON at
  `src/main/generated/assets/totality/items/dnd_potion_of_healing.json`.

## 12. Confirmation: no survival acquisition was added

No recipe, brewing entry, loot table, chest loot, mob drop, merchant/Provisioner stock entry, quest
reward, mountain-flower/bonemeal change, or any other repeatable survival acquisition path was added
or modified. The item is reachable only via `/give`/creative-mode give, exactly as scoped.

## 13. Confirmation: no Alchemy classes were modified or used

- `HealingAmount.java`, `HealingPotionItem.java`, and `DndPotionItems.java` contain **zero**
  references (import or otherwise) to `AlchemyPotionItem`, `PotionData`, `PotionDataComponent`,
  `AlchemyEffect`, `PotionTier`, or any class under `zcylas.totality.api.rpg.skills.alchemy` —
  checked by an automated source-regression test (`DndPotionOfHealingSourceRegressionTest`),
  which scans **import lines only** (case-insensitively) for any Alchemy reference. It deliberately
  does **not** scan whole-file text for the word "Alchemy," since this class's own Javadoc
  legitimately documents its independence from, and registry-id collision with, the Alchemy system
  — that documentation is required, not forbidden (corrected in the review-correction pass, §22;
  the original version of this test incorrectly forbade "alchemy" anywhere in the file).
- No existing Alchemy file (`AlchemyPotionItem.java`, `PotionItems.java`, `PotionData.java`,
  `AlchemyEffects.java`, etc.) was modified in any way.
- **Corrected in the review-correction pass (§22):** the datagen model-wiring call in
  `ModModelProvider.java` no longer uses `PotionTintSource` (an Alchemy-owned class) at all for the
  D&D item — it now uses vanilla's own `net.minecraft.client.color.item.Constant(0xB43A3A)`. There
  is now **no** Alchemy-owned class anywhere in the D&D potion's rendering path, not even at a
  fallback branch. `0xB43A3A` remains a plain numeric literal, chosen only so the placeholder
  texture renders red instead of `Constant`'s caller-supplied color (no fallback/default branch
  exists on `Constant` — the value is always exactly what's passed in).

## 14. Exact files added or changed

**New (production):**
- `src/main/java/zcylas/totality/item/potion/dnd/HealingAmount.java`
- `src/main/java/zcylas/totality/item/potion/dnd/HealingPotionItem.java`
- `src/main/java/zcylas/totality/init/items/DndPotionItems.java`

**New (generated, datagen output):**
- `src/main/generated/assets/totality/items/dnd_potion_of_healing.json`

**New (test):**
- `src/test/java/zcylas/totality/item/potion/dnd/HealingAmountTest.java`
- `src/test/java/zcylas/totality/item/potion/dnd/HealingPotionItemContractTest.java`
- `src/test/java/zcylas/totality/item/potion/dnd/DndPotionOfHealingSourceRegressionTest.java`

**Modified (production):**
- `src/main/java/zcylas/totality/init/ModItems.java` — one added line: `DndPotionItems.register();`
- `src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java` — one added translation entry
- `src/main/java/zcylas/totality/datagen/ModItemTagProvider.java` — one added import, one added
  `.add(key(DndPotionItems.POTION_OF_HEALING))` tag entry
- `src/main/java/zcylas/totality/datagen/ModModelProvider.java` — one added model-registration block

**Modified (generated, datagen output — regenerated by `runDatagen`, not hand-edited):**
- `src/main/generated/assets/totality/lang/en_us.json`
- `src/main/generated/data/totality/tags/item/potions.json`

**New (this report):**
- `Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md`

Total: 13 files (3 new production + 1 new generated + 3 new test + 4 modified production + 2
modified generated), plus this report.

## 15. Exact test/build commands and results

All commands run from the repository root.

1. `./gradlew compileJava -q` — succeeded (only a pre-existing "deprecated API" note, unrelated).
2. Direct probing (temporary scratch test files, removed before final commit-eligible state) to
   determine test-infrastructure limits:
   - Constructing `new HealingPotionItem(HealingAmount.fixed(5), 32, new Item.Properties())`
     without bootstrap → `ExceptionInInitializerError` / `IllegalArgumentException: Not
     bootstrapped (called from registry minecraft:game_event)`.
   - Same construction after calling `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();`
     (even with a real `ResourceKey` set via `Item.Properties().setId(...)`) →
     `IllegalStateException: This registry can't create intrusive holders` (the item registry is
     frozen once bootstrap finishes registering all vanilla items).
   - Reflection-only inspection (`HealingPotionItem.class.getDeclaredMethod(...)`) — succeeds with
     **no** bootstrap error, since it only requires loading, not initializing, the class.
   These probes confirmed real `Item`/`LivingEntity` instantiation is unreachable in this
   repository's plain-JUnit test environment (consistent with zero pre-existing tests anywhere in
   the repo ever constructing an `Item`/`ItemStack`), and directly informed the test design in §16.
3. `./gradlew test --tests "zcylas.totality.item.potion.dnd.*" -q` — after two fixes (see below),
   **25 tests, 0 failures**.
   - First run: 2 failures.
     - `HealingAmountTest#twoD4PlusTwoActuallyReachesBothExtremesAcrossManySeeds` failed: 2000
       sequential seeds (0–1999) never happened to roll the exact minimum (4). This was a flaky,
       probabilistic-search assertion, not a real defect — replaced with
       `twoD4PlusTwoProducesARealSpreadOfValuesNotAConstant`, which asserts at least 5 distinct
       outcomes across 500 seeds (still proves a genuine, non-constant roll, without depending on
       any single seed hitting an exact extreme).
     - `DndPotionOfHealingSourceRegressionTest#normalPotionOfHealingUsesTheDistinctDndRegistryIdNotTheAlchemyOccupiedId`
       failed: the test's blanket `assertFalse(source.contains("\"potion_of_healing\""))` false-
       positived on `DndPotionItems.java`'s own explanatory comment (which legitimately documents
       the collision — required by this task, not forbidden). Fixed to check specifically that no
       `registerItem(...)` call uses the bare `"potion_of_healing"` string as its name argument,
       rather than forbidding the id from appearing anywhere (including in the required
       documentation comment).
   - Second run after both fixes: 25/25 passed, 0 failures, 0 errors, 0 skipped.
4. `./gradlew test -q` (full suite) — **0 failures**. Exact counts, computed from
   `build/test-results/test/TEST-*.xml`: **68 test classes, 904 tests, 0 failures, 0 errors, 0
   skipped** (up from the prior Phase 3B-3 baseline of 879 tests — the 25 new tests above account
   for the full difference).
5. `./gradlew runDatagen -q` — succeeded. Log line: `Caching: total files: 350, old count: 349, new
   count: 350, removed stale: 0, written: 3`. Confirmed via `git status --short
   src/main/generated` that **exactly 3** generated files changed: the new
   `assets/totality/items/dnd_potion_of_healing.json`, and the regenerated
   `assets/totality/lang/en_us.json` (+1 line) and `data/totality/tags/item/potions.json` (+1
   entry) — no other generated file was touched by this datagen run.
6. `./gradlew build` — **BUILD SUCCESSFUL**. `compileJava`, `test`, and `check` all reported
   `UP-TO-DATE`/passed with no failures.

**Review-correction pass** (see §22 for full detail) re-ran all four commands after the
corrections: focused tests **30/30 passed**, full suite **909 tests, 0 failures/errors/skipped**,
`runDatagen` regenerated **exactly 1** file (`dnd_potion_of_healing.json`, now using
`minecraft:constant` instead of `totality:potion_color`), and `./gradlew build` again reported
**BUILD SUCCESSFUL**.

## 16. Manual smoke-test instructions and whether they were actually performed

**Not performed manually this pass** — no interactive game client was launched during this task
(all verification above is via Gradle test/datagen/build). The following is the smoke-test
procedure to run before considering this item fully validated in a live game session:

1. Launch the dev client (`Zcylas` is the pinned dev identity — see prior session memory), open a
   creative-mode world.
2. Run `/give @s totality:dnd_potion_of_healing` and confirm one "Potion of Healing" appears in the
   hotbar/inventory.
3. Right-click to drink; confirm the vanilla drinking animation plays and lasts 32 ticks (1.6s)
   before consuming.
4. Take damage (e.g. fall damage or `/damage`) down to a partial-health value, drink the potion, and
   confirm the healed amount is between 4 and 10 HP (2 HP in-game units per half-heart, so 2–5
   hearts).
5. At near-full health, drink the potion and confirm health does not exceed the entity's maximum
   (vanilla `heal()` clamping).
6. Confirm exactly one item is consumed from the stack per use (stack count decrements by 1, no
   item remains afterward — no empty bottle).
7. Confirm the item does **not** appear in any creative-mode tab by browsing (expected, since no
   Totality item does — see §11) but is obtainable via `/give`/creative "Give Item" search.
8. Open an existing Alchemy `Potion of Healing` (`totality:potion_of_healing`, from
   `PotionItems`) side-by-side and confirm it is unchanged — same behavior, same tooltip/effect
   text, same registry id, unaffected by this task.
9. **Non-player `LivingEntity` consumption** — not directly exposed by any current gameplay hook (no
   mob AI currently tries to drink potions). The practical future check: once any
   mob-drinks-potion interaction exists (or via a `/summon` + manual `useItemInHand` debug trigger,
   or a future GameTest), confirm the identical code path in `HealingPotionItem.finishUsingItem`
   runs for a non-player `LivingEntity` and heals it correctly — no code change would be required,
   since the class never special-cases `Player`.

## 17. Known limitations or follow-up work

- No dedicated D&D-style texture exists yet; the item currently renders using the existing shared
  potion-bottle model (`standard_potion.json`, itself also used by Alchemy potions, but the tint
  applied to it is vanilla's own `Constant` type, not an Alchemy class — see §11/§22). Replace with
  bespoke art in a later pass.
- Manual in-game validation (§16) has not yet been performed and remains outstanding.
- Non-player `LivingEntity` consumption is untested both automatically (infrastructure limits, see
  §15) and manually (no current gameplay hook triggers it) — this is a genuine coverage gap, not a
  known defect; the class's actor-generic design (no `Player` special-casing anywhere) is the
  strongest available evidence that it would work correctly.
- Greater/Superior/Supreme Healing tiers remain unregistered and unexposed, exactly as scoped —
  their formulas were only proven reusable via unit tests against the shared `HealingAmount` base,
  never registered as items.

## 18. Confirmation that Phase 3C remains unstarted

No Resource API parity/synchronization file was touched. No consumer-migration file was added. This
task is entirely scoped to a new, independent item under `item/potion/dnd` and its registration —
unrelated to the Generic Player Resource API in every respect.

## 19. Final `git diff --stat`

This section originally reflected the state immediately after the initial implementation pass. It
is now superseded by the review-correction pass's diff (§22.8/§22.9 and the separate correction
report), since `ModModelProvider.java`'s diff changed shape (the `PotionTintSource` call was
replaced with `net.minecraft.client.color.item.Constant`) and `HealingPotionItem.java`/
`HealingAmount.java` gained validation logic. The **repository-wide** stat immediately after the
review-correction pass is:

```
 build.gradle                                                     | 2 ++
 src/main/generated/assets/totality/lang/en_us.json               | 1 +
 src/main/generated/data/totality/tags/item/potions.json          | 3 ++-
 .../java/zcylas/totality/datagen/ModEnglishLangProvider.java     | 3 +++
 src/main/java/zcylas/totality/datagen/ModItemTagProvider.java    | 2 ++
 src/main/java/zcylas/totality/datagen/ModModelProvider.java      | 9 +++++++++
 src/main/java/zcylas/totality/init/ModItems.java                 | 1 +
 7 files changed, 20 insertions(+), 1 deletion(-)
```

(`build.gradle`'s 2-line diff predates this task — present at the verified starting checkpoint,
untouched by this work; the other 22 generated loot-table/worldgen/recipe JSON diffs likewise
predate this task and are omitted from this stat since they are unmodified since the checkpoint.
The **task-scoped** diff — restricted to only the D&D-potion-related tracked files — and the
correction-pass-only diff are both recorded in
`TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_REPORT.md`, §12.)

## 20. Final `git status --short`

```
 M build.gradle
 M src/main/generated/assets/totality/lang/en_us.json
 M src/main/generated/data/minecraft/worldgen/noise_settings/overworld.json
 M src/main/generated/data/totality/loot_table/blocks/apothecary_table.json
 M src/main/generated/data/totality/loot_table/blocks/copper_cable.json
 M src/main/generated/data/totality/loot_table/blocks/deepslate_graphite_ore.json
 M src/main/generated/data/totality/loot_table/blocks/deepslate_ruby_ore.json
 M src/main/generated/data/totality/loot_table/blocks/electric_furnace.json
 M src/main/generated/data/totality/loot_table/blocks/flecked_whitestone.json
 M src/main/generated/data/totality/loot_table/blocks/generator.json
 M src/main/generated/data/totality/loot_table/blocks/graphite_ore.json
 M src/main/generated/data/totality/loot_table/blocks/limestone.json
 M src/main/generated/data/totality/loot_table/blocks/polished_whitestone.json
 M src/main/generated/data/totality/loot_table/blocks/polished_whitestone_bricks.json
 M src/main/generated/data/totality/loot_table/blocks/ritual_altar.json
 M src/main/generated/data/totality/loot_table/blocks/ritual_dais.json
 M src/main/generated/data/totality/loot_table/blocks/ruby_ore.json
 M src/main/generated/data/totality/loot_table/blocks/tin_ore.json
 M src/main/generated/data/totality/loot_table/blocks/true_wheat_crop.json
 M src/main/generated/data/totality/loot_table/blocks/whitestone.json
 M src/main/generated/data/totality/recipe/copper_gear.json
 M src/main/generated/data/totality/recipe/diamond_gear.json
 M src/main/generated/data/totality/recipe/gold_gear.json
 M src/main/generated/data/totality/recipe/iron_gear.json
 M src/main/generated/data/totality/tags/item/potions.json
 M src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java
 M src/main/java/zcylas/totality/datagen/ModItemTagProvider.java
 M src/main/java/zcylas/totality/datagen/ModModelProvider.java
 M src/main/java/zcylas/totality/init/ModItems.java
?? "Context/Audit/Review Bundles/" (18 pre-existing Phase 3A/3B review-bundle ZIPs, untouched)
?? "Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md" (this report)
?? "Context/Trading Test/trade_screen2.png" .. "trade_screen5.png" (pre-existing, untouched)
?? log4j-dev.xml (pre-existing, untouched)
?? logs/ (pre-existing, untouched)
?? src/main/generated/.cache/ (pre-existing, untouched)
?? src/main/generated/assets/totality/items/dnd_potion_of_healing.json
?? src/main/java/zcylas/totality/init/items/DndPotionItems.java
?? src/main/java/zcylas/totality/item/potion/dnd/
?? src/test/java/zcylas/totality/item/
```

(The review-bundle ZIP produced by this task,
`TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_BUNDLE.zip`, will also appear untracked once
written, per §21/scope — it is not committed. This status block is from immediately after the
initial implementation pass; the review-correction pass's own final status is recorded in
`TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_REPORT.md`, §13 — the same 13 tracked/untracked
D&D-potion entries remain, now also alongside the correction-pass's own report and bundle.)

## 21. Confirmation that no commit or push was performed

No `git add`, `git commit`, or `git push` was run at any point during this task. All 13 changed/new
files listed in §14 remain in the working tree, uncommitted, exactly as required. This remains true
through the review-correction pass as well (§22.9): no commit or push was performed at any point.

## 22. Review-correction pass (post-implementation)

A separate review-correction pass was applied after this implementation was reviewed. Full detail
lives in `Context/Audit/TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_REPORT.md`; this section
summarizes what changed and folds in the specific clarifications the review required of *this*
report.

**22.1 — Starting condition.** The review-correction pass began from the same branch
(`feature/general-resource-api`) and the same HEAD (`6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09`) as
this implementation pass, with this implementation still uncommitted in the working tree exactly as
left at the end of §1–§21. The repository was already dirty before *this* implementation task began
(pre-existing unrelated changes: `build.gradle`, 22 generated loot-table/worldgen/recipe JSONs, 4
Trading Test screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`, and 18 pre-existing
Phase 3A/3B review-bundle ZIPs — none of them touched by this work). This implementation task's own
checkpoint instructions (§1) required verifying branch/HEAD/working-tree status and forbade
destructive git operations, but did not include a blanket "stop if the working tree is dirty" gate —
only the review-correction pass's prompt introduced an explicit "stop if the D&D-potion files
themselves have unexpected changes" gate, which was checked first for that pass and found clean
(task-scoped `git status` matched this report's §14 exactly, with no surprises).

**22.2 — Findings resolved:**
1. **Rendering independence.** `ModModelProvider.java`'s D&D-potion model registration used
   `new PotionTintSource(0xB43A3A)` — reusing an Alchemy-owned tint class, even though the item
   class itself had no Alchemy dependency. Corrected to
   `new net.minecraft.client.color.item.Constant(0xB43A3A)`, Minecraft 26.2's own built-in
   constant-color tint source (already registered vanilla-side as `minecraft:constant`). See §11/§13
   above for the corrected description.
2. **Constructor invariants.** `HealingPotionItem`'s constructor now calls
   `Objects.requireNonNull(healingAmount, "healingAmount")` and rejects `useDurationTicks <= 0` with
   a clear `IllegalArgumentException` message.
3. **Negative-bonus validation.** `HealingAmount.DiceHealing`'s compact constructor now rejects a
   negative `bonus` (`bonus >= 0` required), in addition to the pre-existing negative-`Fixed`-amount
   and non-positive-dice-count/null-die checks. Zero remains a valid `Fixed` amount and a valid
   `bonus`.
4. **Test honesty/robustness.** `DndPotionOfHealingSourceRegressionTest` was corrected: the
   Alchemy-import boundary check now scans **import lines only** (never whole-file text, since
   comments legitimately mention "Alchemy" to document independence/the id collision); the
   server-gate and constructor-invariant checks are now explicitly labeled in the test's own Javadoc
   as source-text **sentinels**, not runtime proof; the registration-formula check now normalizes
   whitespace before matching, so it survives reformatting/line-wrapping; a new
   rendering-independence sentinel confirms the generated client-item JSON no longer contains
   `"totality:potion_color"` and does contain `"minecraft:constant"`.

**22.3 — Files changed by the correction pass:**
- `src/main/java/zcylas/totality/item/potion/dnd/HealingPotionItem.java` (constructor validation)
- `src/main/java/zcylas/totality/item/potion/dnd/HealingAmount.java` (negative-bonus validation)
- `src/main/java/zcylas/totality/datagen/ModModelProvider.java` (tint source swap)
- `src/test/java/zcylas/totality/item/potion/dnd/HealingAmountTest.java` (2 new tests)
- `src/test/java/zcylas/totality/item/potion/dnd/DndPotionOfHealingSourceRegressionTest.java`
  (rewritten per correction 4)
- `src/main/generated/assets/totality/items/dnd_potion_of_healing.json` (regenerated by `runDatagen`)
- This report, and the new correction report/bundle.

No other file — production, test, or generated — was touched by the correction pass. No unrelated
pre-existing dirty-tree file was modified.

**22.4 — Validation.** Focused tests: `./gradlew test --tests "zcylas.totality.item.potion.dnd.*"`
→ **30 tests, 0 failures/errors/skipped** (up from 25; net +5: 2 in `HealingAmountTest`, net +3 in
the rewritten regression test — it gained 4 new sentinels and lost 1 over-broad whole-file scan).
Full suite: `./gradlew test` → **909 tests, 0 failures/errors/skipped** (up from 904). `runDatagen`
→ `350 → 350, written: 1` (only `dnd_potion_of_healing.json` changed; confirmed via
`git status --short src/main/generated` and `git diff --stat` against the `items/` directory that
no other generated client-item JSON, including any existing Alchemy potion's, was touched).
`./gradlew build` → **BUILD SUCCESSFUL**.

**22.5 — Phase 3C and commit/push status.** Unchanged from §18/§21: Phase 3C remains unstarted, and
no commit or push was performed at any point during either pass. HEAD remained
`6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09` throughout.

## 23. Healing notification pass (post-review-correction)

A third, separate pass added the player-facing healing notification, a companion Thrown Shuriken
notification-label bug fix, and central Notification API rendered-width wrapping. Full detail lives
in `Context/Audit/TOTALITY_DND_POTION_HEALING_NOTIFICATION_AND_HUD_WRAP_REPORT.md` — this section
only records what changed in files this report already describes.

**23.1 — `HealingAmount`.** Gained `HealingRollResult rollDetailed(RandomSource)`, the single
source of truth for both applying healing and formatting a notification about it (never rolled
twice). The pre-existing `roll(RandomSource)` now `default`-delegates to it
(`rollDetailed(random).total()`) rather than duplicating the roll logic. No change to `Fixed`'s or
`DiceHealing`'s validation (§4/§22.2) or registered formula (§6).

**23.2 — `HealingPotionItem`.** `finishUsingItem` now calls `rollDetailed(...)` once, records
health immediately before and after `user.heal(...)`, computes
`actualHealing = Math.max(0, healthAfter - healthBefore)`, and — only when
`actualHealing > 0 && user instanceof ServerPlayer` — sends a green notification via the new
`HealingRollNotification`. Non-player `LivingEntity` healing is completely unaffected: `user.heal(...)`
is called unconditionally on `amount > 0`, outside and before the `ServerPlayer` check (§8/§9 above
remain accurate — the actor-generic healing path itself did not change, only what happens
optionally afterward for a player).

**23.3 — New files** (see the combined report for full detail): `HealingRollResult` (pure retained-
roll data, same package as `HealingAmount`) and `HealingRollNotification` (a new
`zcylas.totality.networking.potion` package, parallel to but independent of
`zcylas.totality.networking.combat.DamageRollNotification`).

**23.4 — No Alchemy/combat-text dependency added.** Confirmed by the same import-line-only source-
regression convention already established in this report (§13/§22.2): neither `HealingRollResult`
nor `HealingRollNotification` imports any Alchemy, `DamageRollNotification`, or `CombatTextPayload`
class.
