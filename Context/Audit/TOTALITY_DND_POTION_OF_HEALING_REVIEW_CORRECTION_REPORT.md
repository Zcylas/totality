# Totality — D&D Potion of Healing — Review-Correction Report

## 1. Starting branch and HEAD

- Branch: `feature/general-resource-api`
- HEAD: `6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09` (verified via `git rev-parse HEAD` before any
  change; confirmed unchanged via the same command after all corrections and validation)

## 2. Starting task-scoped status

Before any correction was applied, the task-scoped `git status --short` (restricted to the D&D
Potion of Healing implementation files) was:

```
 M src/main/generated/assets/totality/lang/en_us.json
 M src/main/generated/data/totality/tags/item/potions.json
 M src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java
 M src/main/java/zcylas/totality/datagen/ModItemTagProvider.java
 M src/main/java/zcylas/totality/datagen/ModModelProvider.java
 M src/main/java/zcylas/totality/init/ModItems.java
?? "Context/Audit/Review Bundles/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_BUNDLE.zip"
?? Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md
?? src/main/generated/assets/totality/items/dnd_potion_of_healing.json
?? src/main/java/zcylas/totality/init/items/DndPotionItems.java
?? src/main/java/zcylas/totality/item/potion/dnd/
?? src/test/java/zcylas/totality/item/potion/dnd/
```

This matched the implementation report's own file list (§14) exactly — no unexpected changes were
present, so correction work proceeded without stopping.

## 3–4. Review findings and exact corrections applied

### Finding 1 — indirect Alchemy rendering dependency

**Finding:** `ModModelProvider.java`'s model-registration call for the D&D potion used
`new PotionTintSource(0xB43A3A)`. `PotionTintSource` (`zcylas.totality.client.color.PotionTintSource`)
imports and reads `PotionData`/`PotionDataComponent`, and references `AlchemyPotionItem` in its
`calculate(...)` fallback branch. Even though the D&D item always reaches that fallback branch
(never having a `PotionDataComponent` or being an `AlchemyPotionItem`), its *rendering* still
depended on an Alchemy-owned class — violating the "must not use the current Alchemy API"
requirement at the rendering layer, even though the item's own Java classes were already Alchemy-
free.

**Correction applied:** Inspected the actual Minecraft 26.2 client-model API (via the mapped
Minecraft sources jar at
`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.2/*-sources.jar`,
`net/minecraft/client/color/item/`). Confirmed `net.minecraft.client.color.item.Constant` is a
built-in `record Constant(int value) implements ItemTintSource`, already registered vanilla-side
under id `minecraft:constant` in `ItemTintSources.bootstrap()`. Replaced the tint source in
`ModModelProvider.java`:

```java
// before
ItemModelUtils.tintedModel(standardPotionModel, new PotionTintSource(0xB43A3A))
// after
ItemModelUtils.tintedModel(standardPotionModel, new net.minecraft.client.color.item.Constant(0xB43A3A))
```

No new Totality class was needed (the "preferred solution" branch of the task applied). No existing
Alchemy potion's model registration was touched — all still use `PotionTintSource.INSTANCE`
unchanged. The regenerated `dnd_potion_of_healing.json` now declares `"type": "minecraft:constant"`
and contains no `"totality:potion_color"` reference (verified — see §10).

### Finding 2 — missing reusable-item constructor invariants

**Finding:** `HealingPotionItem`'s constructor stored `healingAmount` and `useDurationTicks`
unchecked, so a future mis-registered tier (e.g. a forgotten duration, or a `null` formula) would
silently produce a broken item instead of failing at registration time.

**Correction applied:**

```java
public HealingPotionItem(HealingAmount healingAmount, int useDurationTicks, Properties properties) {
    super(properties);
    this.healingAmount = Objects.requireNonNull(healingAmount, "healingAmount");
    if (useDurationTicks <= 0) {
        throw new IllegalArgumentException("useDurationTicks must be greater than zero: " + useDurationTicks);
    }
    this.useDurationTicks = useDurationTicks;
}
```

No general consumable-configuration framework was introduced — this is exactly the two-field
validation the review asked for, inline in the existing constructor.

### Finding 3 — missing negative-bonus rejection in `HealingAmount`

**Finding:** `HealingAmount.DiceHealing` validated `diceCount` and `die`, but allowed a negative
`bonus`, which would silently clamp toward zero via the existing `Math.max(0, sum + bonus)` in
`roll(...)` rather than failing fast on a clearly mis-authored formula.

**Correction applied:**

```java
public DiceHealing {
    if (diceCount <= 0) throw new IllegalArgumentException("diceCount must be >= 1: " + diceCount);
    Objects.requireNonNull(die, "die");
    if (bonus < 0) throw new IllegalArgumentException("bonus must be >= 0: " + bonus);
}
```

Zero remains valid for both `Fixed` amounts and `bonus` (no repository convention argued
otherwise). Added `HealingAmountTest#diceRejectsNegativeBonus` (asserts
`IllegalArgumentException`) and `HealingAmountTest#diceAllowsZeroBonus` (asserts no exception) —
both pass. No broadening into attribute modifiers, combat rolls, or an expression framework was
introduced.

### Finding 4 — source tests over-broad / formatting-fragile / overclaiming runtime proof

**Finding (four sub-issues in `DndPotionOfHealingSourceRegressionTest`):**
1. The whole-file token scan (`noneOfTheThreeNewFilesReferenceAForbiddenAlchemyTokenAnywhereInSource`)
   forbade the word "Alchemy" anywhere in the three new files' text, which is stricter than the
   actual required boundary (no *production dependency*) and would false-positive on legitimate
   documentation (e.g. a class Javadoc explaining "independent of Totality's Alchemy API").
2. The server-gate test compared raw line indices and, while its assertion names were reasonably
   worded, nothing in the test explicitly disclaimed that comparing line order is not equivalent to
   proving runtime control flow.
3. The registration-formula sentinel matched the literal string
   `"HealingAmount.dice(2, Dice.D4, 2), 32"` with no whitespace normalization, so it would break on
   harmless reformatting (e.g. wrapping the constructor call across two lines).
4. There was no sentinel at all confirming the *generated* client-item JSON avoided the Alchemy tint
   type — Finding 1's fix could regress silently in the future without a test catching it.

**Correction applied** (full rewrite of `DndPotionOfHealingSourceRegressionTest.java`):
- Removed the whole-file forbidden-token scan entirely; **only** the import-boundary test remains
  for the Alchemy dependency question (`noneOfTheThreeNewFilesImportAnyAlchemyClass`), which scans
  **import lines only**, case-insensitively, for the substring `"alchemy"`.
- Added a detailed class-level Javadoc explicitly stating that every test in the file is a
  **source-regression sentinel**, not a runtime-behavior proof, with the specific limitation spelled
  out (cannot execute the method, cannot observe actual control flow, can be defeated by a
  behavior-preserving refactor). Each sentinel method's own comment repeats this where relevant.
- `normalPotionOfHealingIsRegisteredWithExactlyTwoD4PlusTwoAndThirtyTwoTicks` now normalizes
  whitespace (`source.replaceAll("\\s+", " ")`) before matching the formula/duration substring, so
  it survives line-wrapping.
- Added `generatedClientItemJsonDoesNotUseTheAlchemyOwnedTintType` (asserts the generated JSON does
  **not** contain `"totality:potion_color"`), `generatedClientItemJsonUsesTheVanillaConstantTintType`
  (asserts it **does** contain `"minecraft:constant"`), and
  `modelProviderSourceDoesNotUsePotionTintSourceForTheDndItem` (scans a small window of
  `ModModelProvider.java` right after the `DndPotionItems.POTION_OF_HEALING` reference for the
  literal token `PotionTintSource` and asserts its absence).
- Added `healingPotionItemConstructorSourceContainsBothInvariantChecks` — a sentinel (not runtime
  proof, same limitation as above) confirming the constructor's validation source text exists, since
  the constructor itself cannot be invoked (valid or invalid arguments) under plain JUnit — see §7.
- No JavaParser, ArchUnit, Mockito, or other framework was added; every check remains plain string/
  line-based text inspection, consistent with the repository's existing
  `ClientResourceParityReportPureBoundaryTest` convention.

## 5. Final rendering path and why it is independent of Alchemy

The D&D Potion of Healing's generated client-item JSON
(`src/main/generated/assets/totality/items/dnd_potion_of_healing.json`) is now:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "totality:item/standard_potion",
    "tints": [
      { "type": "minecraft:constant", "value": -4965830 }
    ]
  }
}
```

- `"model": "totality:item/standard_potion"` — the existing shared base+fill potion-bottle model
  (a plain JSON model file, not a Java class; reusing existing independent art, per the task's
  explicit allowance).
- `"type": "minecraft:constant"` — Minecraft 26.2's **own built-in** tint source
  (`net.minecraft.client.color.item.Constant`), registered vanilla-side, not a Totality or Alchemy
  type at all.
- `"value": -4965830` — the literal `0xB43A3A` as a signed 32-bit int with an opaque alpha byte set
  (`ARGB.opaque(...)`, applied inside `Constant`'s own compact constructor) — a plain number, not a
  reference to `PotionData.COLOR_RED` or any other Alchemy constant.

No Alchemy-owned Java class appears anywhere in this item's rendering path — not the item class,
not the datagen registration, not the generated output. This is independent of Alchemy at every
layer, not merely at the item-class layer. Existing Alchemy potions' generated JSONs are unchanged
(still `"type": "totality:potion_color"`) — confirmed by `git diff --stat` against
`src/main/generated/assets/totality/items/` showing only `dnd_potion_of_healing.json` touched.

## 6. Constructor and formula-validation changes

See §4 (Findings 2 and 3) for the exact code. Summary of the full validation surface after this
pass:

| Type | Field | Rule |
|---|---|---|
| `HealingAmount.Fixed` | `amount` | `>= 0` (unchanged from the original implementation) |
| `HealingAmount.DiceHealing` | `diceCount` | `>= 1` (unchanged) |
| `HealingAmount.DiceHealing` | `die` | non-null (unchanged) |
| `HealingAmount.DiceHealing` | `bonus` | **`>= 0` (new this pass)** |
| `HealingPotionItem` constructor | `healingAmount` | **non-null (new this pass)** |
| `HealingPotionItem` constructor | `useDurationTicks` | **`> 0` (new this pass)** |

The registered normal Potion of Healing (`HealingAmount.dice(2, Dice.D4, 2)`, 32-tick duration)
satisfies every one of these rules; none of the new validation rejects the actual registered
configuration.

## 7. Test changes and their actual evidentiary limits

- **`HealingAmountTest`** — added `diceRejectsNegativeBonus` and `diceAllowsZeroBonus`. Both are
  genuine, fully-executed unit tests (`HealingAmount` is a plain sealed interface/records with no
  Minecraft registry dependency — confirmed instantiable in plain JUnit, unlike `HealingPotionItem`).
  These are real runtime proof, not sentinels.
- **`HealingPotionItemContractTest`** — unchanged this pass; already reflection-only (no
  instantiation), documented as such since the original implementation.
- **`DndPotionOfHealingSourceRegressionTest`** — every test in this file is a source-text
  **sentinel**. This was true before this pass too, but the original report did not say so plainly
  and one test (the server-gate line-order check) implied more than it could prove. This is now
  fixed:
  - The constructor-invariant sentinel and the server-gate/guard sentinels **cannot** prove the
    corresponding code actually throws or actually executes exactly-once-server-side at runtime —
    they can only prove the expected tokens exist in the expected relative textual position. A
    behavior-preserving refactor that changes wording could defeat them without changing behavior;
    conversely, in principle a change could break behavior while accidentally preserving the tokens
    the sentinel checks (an unlikely but real gap).
  - The import-boundary, rendering-independence, and registration-formula sentinels are similarly
    text-based, but check narrower, more mechanical facts (an import line's content; a generated
    JSON's literal content; a registration call's literal arguments) where a sentinel is a
    reasonably strong proxy for the real property.
  - **Genuine runtime proof for server-authoritative exactly-once healing, and for the constructor's
    validation actually throwing, remains the manual smoke test** (§11) — this was already true and
    remains the honest position.

## 8. Exact files changed by the correction pass

- `src/main/java/zcylas/totality/item/potion/dnd/HealingPotionItem.java` — constructor validation
  (Finding 2)
- `src/main/java/zcylas/totality/item/potion/dnd/HealingAmount.java` — negative-bonus validation
  (Finding 3)
- `src/main/java/zcylas/totality/datagen/ModModelProvider.java` — tint-source swap (Finding 1)
- `src/test/java/zcylas/totality/item/potion/dnd/HealingAmountTest.java` — 2 new tests (Finding 3)
- `src/test/java/zcylas/totality/item/potion/dnd/DndPotionOfHealingSourceRegressionTest.java` —
  rewritten (Finding 4)
- `src/main/generated/assets/totality/items/dnd_potion_of_healing.json` — regenerated by
  `runDatagen` (consequence of Finding 1's fix)
- `Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md` — corrected (§§2, 4, 6, 7,
  9, 11, 13, 15, 17, 19, 20, 21, and new §22)
- `Context/Audit/TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_REPORT.md` — this report (new)

No other file — production, test, generated, or otherwise — was touched. No unrelated pre-existing
dirty-tree file was modified. No Alchemy source file was modified. No Greater/Superior/Supreme
Healing item was added or exposed. No recipe/loot/merchant/acquisition path was added.

## 9. Exact validation commands and results

```
./gradlew test --tests "zcylas.totality.item.potion.dnd.*"
```
Result: **30 tests, 0 failures, 0 errors, 0 skipped** (3 test classes), computed from
`build/test-results/test/TEST-zcylas.totality.item.potion.dnd.*.xml`.

```
./gradlew test
```
Result: **68 test classes, 909 tests, 0 failures, 0 errors, 0 skipped**, computed from
`build/test-results/test/TEST-*.xml` (up from 904 before this pass — net +5: +2 in
`HealingAmountTest`, and +3 net in the rewritten regression test, which went from 7 tests to 10:
removed the over-broad whole-file token scan (−1), kept 6 tests (one renamed for clarity, with no
behavior change), and added 4 new sentinels (constructor-invariant, two JSON-rendering checks, and
the `ModModelProvider` source check) — 7 − 1 + 4 = 10.

```
./gradlew runDatagen
```
Result: log line `Caching: total files: 350, old count: 350, new count: 350, removed stale: 0,
written: 1`. Exactly one generated file changed.

```
./gradlew build
```
Result: **BUILD SUCCESSFUL**. Re-ran after a `clean` as an extra check; also **BUILD SUCCESSFUL**.

## 10. Generated-file changes

`git status --short src/main/generated` after `runDatagen`, filtered to non-pre-existing entries,
showed exactly:

```
?? src/main/generated/assets/totality/items/dnd_potion_of_healing.json
```

(This file was already untracked/new from the original implementation pass; `runDatagen` rewrote its
*content* in place — `git status` does not distinguish "new untracked file, content changed again"
from "new untracked file," so the log line's `written: 1` is the authoritative signal that exactly
one file's bytes changed.) The `lang/en_us.json` and `tags/item/potions.json` files, already
regenerated during the original implementation pass, were **not** touched again this pass (their
content already matched what `runDatagen` would produce — no new lang or tag entries were added by
this correction). `git diff --stat` against `src/main/generated/assets/totality/items/` confirmed no
existing Alchemy potion's generated client-item JSON changed — only `dnd_potion_of_healing.json`
(new/untracked) is present in that diff's scope at all.

## 11. Remaining manual validation

Unchanged from the implementation report §16 — **not performed this pass** (no interactive game
client was launched). Still required before final acceptance:
- `/give @s totality:dnd_potion_of_healing`
- Drinking animation and 32-tick duration
- Healing range of 4–10 HP
- Maximum-health clamping
- Exactly one item consumed, no empty bottle
- Existing Alchemy `Potion of Healing` (`totality:potion_of_healing`) unchanged
- Visual confirmation that the item now renders with vanilla's constant tint (should look
  identical to before — same literal color value, different tint mechanism)
- Non-player `LivingEntity` consumption — still not exposed by any current gameplay hook (unchanged
  limitation from the implementation report)

## 12. Final task-scoped diff/stat

```
 src/main/generated/assets/totality/lang/en_us.json               | 1 +
 src/main/generated/data/totality/tags/item/potions.json          | 3 ++-
 .../java/zcylas/totality/datagen/ModEnglishLangProvider.java     | 3 +++
 src/main/java/zcylas/totality/datagen/ModItemTagProvider.java    | 2 ++
 src/main/java/zcylas/totality/datagen/ModModelProvider.java      | 9 +++++++++
 src/main/java/zcylas/totality/init/ModItems.java                 | 1 +
 6 files changed, 18 insertions(+), 1 deletion(-)
```

(Restricted to tracked D&D-potion-related files; `HealingPotionItem.java`, `HealingAmount.java`,
`DndPotionItems.java`, and all three test files are untracked/new, so they do not appear in a
tracked `git diff` — their content is captured directly in the review bundle instead.)

Task-scoped `git status --short` (same pathspec as §2):

```
 M src/main/generated/assets/totality/lang/en_us.json
 M src/main/generated/data/totality/tags/item/potions.json
 M src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java
 M src/main/java/zcylas/totality/datagen/ModItemTagProvider.java
 M src/main/java/zcylas/totality/datagen/ModModelProvider.java
 M src/main/java/zcylas/totality/init/ModItems.java
?? "Context/Audit/Review Bundles/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_BUNDLE.zip"
?? Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md
?? src/main/generated/assets/totality/items/dnd_potion_of_healing.json
?? src/main/java/zcylas/totality/init/items/DndPotionItems.java
?? src/main/java/zcylas/totality/item/potion/dnd/
?? src/test/java/zcylas/totality/item/potion/dnd/
```

Unchanged in shape from §2 — the correction pass only edited file *contents*, not which files exist,
except for adding this report and (once written) the correction bundle ZIP.

## 13. Final repository-wide `git status --short`

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
?? "Context/Audit/Review Bundles/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3A_CORRECTED_FINAL_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3A_FINAL_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3A_RESYNC_RETRY_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B1_CORRECTION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B1_FINAL_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2A_CORRECTION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2A_FINAL_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2B_CORRECTION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2B_FINAL_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2C_CORRECTION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2C_FINAL_DOCUMENTATION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2C_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B2C_SECOND_CORRECTION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B3_CLOSURE_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B3_COMMAND_ROOT_CORRECTION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B3_CORRECTION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B3_FINAL_VALIDATION_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B3_REVIEW_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_RESOURCE_API_PHASE_3B3_SECOND_CORRECTION_REVIEW_BUNDLE.zip"
?? Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md
?? "Context/Trading Test/trade_screen2.png"
?? "Context/Trading Test/trade_screen3.png"
?? "Context/Trading Test/trade_screen4.png"
?? "Context/Trading Test/trade_screen5.png"
?? log4j-dev.xml
?? logs/
?? src/main/generated/.cache/
?? src/main/generated/assets/totality/items/dnd_potion_of_healing.json
?? src/main/java/zcylas/totality/init/items/DndPotionItems.java
?? src/main/java/zcylas/totality/item/potion/dnd/
?? src/test/java/zcylas/totality/item/
```

(This report and the correction bundle ZIP will also appear untracked once written, alongside the
above.)

## 14. Confirmation that unrelated dirty-tree files were preserved

All pre-existing unrelated entries — `build.gradle`, 22 generated loot-table/worldgen/recipe JSONs,
4 Trading Test screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`, and all 18
pre-existing Phase 3A/3B review-bundle ZIPs — remain present and unmodified, exactly as they were
at the start of the implementation task. No broad staging (`git add -A`), cleanup, or formatting
command was run at any point in this correction pass; every file edit targeted a single named path.

## 15. Confirmation that Phase 3C remains unstarted

No Resource API parity/synchronization file was touched by this correction pass. No consumer-
migration file was added. This pass was entirely scoped to the D&D Potion of Healing's rendering
path, its two validation fixes, and its test suite.

## 16. Confirmation that no commit or push occurred

No `git add`, `git commit`, or `git push` was run at any point during this correction pass. HEAD
remained `6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09` throughout, verified both before and after all
corrections and validation.

## 17. Follow-up: healing notification pass

A later, separate pass (not part of this correction pass) added a player-facing green healing
notification (a chat/HUD text notification, explicitly not a floating combat-text number, which
remains out of scope), a companion Thrown Shuriken notification-label fix, and central Notification
API rendered-width wrapping. That work is fully documented in
`Context/Audit/TOTALITY_DND_POTION_HEALING_NOTIFICATION_AND_HUD_WRAP_REPORT.md` and does not modify
anything this correction report describes — `HealingPotionItem`'s constructor validation (§4/§6)
and `HealingAmount`'s validation (§4/§6) are unchanged; `ModModelProvider`'s vanilla-`Constant`
tint fix (§4/§5) is unchanged. HEAD remains `6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09`.
