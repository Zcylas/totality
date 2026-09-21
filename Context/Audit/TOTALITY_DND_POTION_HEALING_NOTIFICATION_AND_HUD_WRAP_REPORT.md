# Totality — Shuriken Label Fix, Potion of Healing Notification, and Notification API Wrapping

## 1. Starting branch and HEAD

- Branch: `feature/general-resource-api`
- HEAD: `6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09` (verified via `git rev-parse HEAD` before any
  change; confirmed unchanged after all implementation and validation)

## 2. Starting repository-wide status

`git status --short` before this pass reported **63** entries: `build.gradle`, 22 generated loot-
table/worldgen/recipe JSONs, 4 Trading Test screenshots, `log4j-dev.xml`, `logs/`,
`src/main/generated/.cache/`, all 18 pre-existing Phase 3A/3B Resource-API review-bundle ZIPs, the
2 D&D-potion review-bundle ZIPs (implementation + correction), the D&D-potion implementation report,
the D&D-potion correction report, and the full set of D&D Potion of Healing implementation/test/
generated files from the two prior passes.

## 3. Starting task-scoped status

Restricted to the known D&D-potion task-scoped file set (per the implementation report §14 and the
correction report's manifest), the status matched exactly, with no unexpected changes:

```
 M src/main/generated/assets/totality/lang/en_us.json
 M src/main/generated/data/totality/tags/item/potions.json
 M src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java
 M src/main/java/zcylas/totality/datagen/ModItemTagProvider.java
 M src/main/java/zcylas/totality/datagen/ModModelProvider.java
 M src/main/java/zcylas/totality/init/ModItems.java
?? "Context/Audit/Review Bundles/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_BUNDLE.zip"
?? Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md
?? Context/Audit/TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_REPORT.md
?? src/main/generated/assets/totality/items/dnd_potion_of_healing.json
?? src/main/java/zcylas/totality/init/items/DndPotionItems.java
?? src/main/java/zcylas/totality/item/potion/dnd/
?? src/test/java/zcylas/totality/item/
```

Spot-checked `HealingPotionItem.java`, `HealingAmount.java`, `DndPotionItems.java`, and the
generated `dnd_potion_of_healing.json` against the correction report's documented final state
(`Objects.requireNonNull(healingAmount)`, `useDurationTicks <= 0` check, `bonus < 0` check,
`"type": "minecraft:constant"`) — all matched exactly. No unexplained changes were present, so
implementation proceeded.

## 4. Shuriken bug cause

`ThrownShurikenEntity.onHitEntity(EntityHitResult)` already computed
`String weaponName = shurikenStack.getHoverName().getString();` from the projectile's own stored
item stack (the authoritative identity — it survives the thrown Shuriken being the last item in a
stack, the attacker switching held items in flight, or the attacker's hand going empty on impact),
but never passed `weaponName` into `CombatResolver.resolveAttack(...)`. It called the 8-argument
convenience overload instead, which internally calls the 9-argument
`(..., int damageModifier, TotalityDamageType damageType)` overload using
`CombatResolver.resolveWeaponName(attacker)` — a private helper that reads
`attacker.getMainHandItem().getHoverName()` (or `"Unarmed Strike"` if empty) — deriving the
notification label from whatever the attacker happens to be holding **at the moment the projectile
lands**, not from the Shuriken that was actually thrown.

## 5. Exact Shuriken overload correction

Changed the call in `ThrownShurikenEntity.onHitEntity` from the 8-argument convenience overload to
the existing 9-argument explicit-name overload, appending `weaponName` as the final argument:

```java
CombatResolver.resolveAttack(
        attacker, target,
        effective,               // finesse-aware ability score
        weapon.isProficient(attacker),
        weapon.modifyRollType(attacker, target, RollType.NORMAL),
        weapon.getDiceCount(),
        weapon.getDamageDie(),
        weapon.getDamageType(),
        weaponName               // explicit-name overload — the projectile's stored item identity
                                  // is authoritative for the notification label
);
```

No new `CombatResolver` overload was added — the explicit-name overload
(`resolveAttack(LivingEntity, LivingEntity, AbilityScore, boolean, RollType, int, Dice,
TotalityDamageType, String)`) already existed, unused by this call site. Attack-roll resolution,
finesse/Dexterity handling, proficiency, damage dice, damage type, roll type, pickup behavior,
projectile physics, durability, and item consumption are all untouched — verified both by the
minimal one-line diff shape and by the source sentinels in §20/§Part A.

## 6. Healing-roll result design

Added `zcylas.totality.item.potion.dnd.HealingRollResult`, a pure immutable record:

```java
public record HealingRollResult(
        int diceCount,
        @Nullable Dice die,
        List<Integer> rolls,
        int modifier,
        int total
) {
    public boolean isDiceBased() { return die != null && diceCount > 0; }
    public String diceExpression() { return isDiceBased() ? diceCount + die.name().toLowerCase() : ""; }
}
```

`rolls` is defensively copied (`List.copyOf`) in the compact constructor, so it is immutable and
independent of any list the caller passed in. Supports the non-dice `Fixed` form: `diceCount = 0`,
`die = null`, `rolls = []`, `modifier = <the fixed amount>`, `total = <the fixed amount>` —
`isDiceBased()` returns `false` in that case, and the notification formatter (§7) branches on it.

`HealingAmount` gained `HealingRollResult rollDetailed(RandomSource random)` as its one abstract
roll method; the pre-existing `int roll(RandomSource random)` is now a `default` method that
delegates to `rollDetailed(random).total()` — a roll is never performed twice, and existing callers
of the simple `roll(...)` API are unaffected. Both `Fixed` and `DiceHealing` implement
`rollDetailed` directly (mirroring their pre-existing `roll` logic, moved rather than duplicated).

## 7. Exact notification formatting rules

`zcylas.totality.networking.potion.HealingRollNotification` (new package, parallel to but
independent of `zcylas.totality.networking.combat.DamageRollNotification`) owns formatting and
delivery. `send(ServerPlayer, String label, HealingRollResult roll, float actualHealing)` delegates
to a pure, independently-testable `static String formatMessage(...)`:

- **Dice-based** (`roll.isDiceBased()`): line 1 is `label + " — " + diceExpression + " → " +
  rolls`; line 2 starts with `"<modifier> = "` only when `modifier != 0` (an exact `+`/`-` sign,
  never an awkward standalone `+0`), then always `"<total> (<rolledDisplay> HP)"`.
- **Fixed**: line 1 is `label + " — " + total + " (" + rolledDisplay + " HP)"`; no dice breakdown,
  no modifier segment.
- Both forms always append `"  •  Restored <restoredDisplay> HP"` to line 2 (or as the entirety of
  line 2, for the fixed form).
- Sent as one `SendNotificationPayload` with color `SendNotificationPayload.GREEN`.
- `label` is the caller-supplied displayed name (`stack.getHoverName().getString()`, read in
  `HealingPotionItem` **before** `super.finishUsingItem(...)` consumes the stack — the same
  established precedent `AlchemyPotionItem` already uses for reading stack data pre-consumption),
  so a renamed potion, or a future Greater/Superior/Supreme variant with its own name, is labeled
  correctly without any change to the formatter.
- The expression/modifier are derived entirely from the registered `HealingAmount`'s retained roll
  result — `"2d4 + 2"` is never hardcoded in the formatter.

## 8. Representative notification examples

Verified by real, executing unit tests (`HealingRollNotificationFormatMessageTest`), not sentinels:

```
Potion of Healing — 2d4 → [2, 4]
+2 = 8 (40 HP)  •  Restored 30 HP
```
(rolls `[2, 4]`, modifier `+2`, total `8`, but only 6 vanilla HP was actually missing/restored)

```
Potion of Healing — 2d4 → [2, 4]
+2 = 8 (40 HP)  •  Restored 40 HP
```
(the complete roll applied — rolled and restored match)

```
Potion of Healing — 2d4 → [3, 3]
6 (30 HP)  •  Restored 30 HP
```
(zero modifier — no standalone `+0`)

```
Potion of Healing — 5 (25 HP)
Restored 25 HP
```
(a hypothetical `Fixed(5)` formula — no dice breakdown line)

```
Potion of Greater Healing — 4d4 → [1, 2, 3, 4]
+4 = 14 (70 HP)  •  Restored 70 HP
```
(proves the formatter is reusable for a differently-configured, differently-named formula — this
item itself remains unregistered/unexposed, per scope)

## 9. Rolled versus restored healing semantics

`HealingPotionItem.finishUsingItem` calls `healingAmount.rollDetailed(random)` **exactly once**,
computing `amount = rollResult.total()` (the rolled mechanical healing — what the formula says
should be restored). It then records `healthBefore = user.getHealth()`, calls
`user.heal(amount)` (vanilla clamps internally at `getMaxHealth()`), and records
`actualHealing = Math.max(0.0F, user.getHealth() - healthBefore)` (the restored mechanical healing —
what was actually gained, post-clamping). These are deliberately distinct values: `rollResult.total()`
never changes after the roll; `actualHealing` reflects the entity's actual health state and can be
less than (clamped) or equal to (unclamped) the rolled total, but never more. Both are converted to
display space independently (§10) and both appear in the notification (§7/§8).

## 10. Shared Health display conversion path

Both the rolled mechanical total and the actual restored healing are converted via
`RpgDisplayUtils.toDisplayHp(float vanillaHp)` — the same public convenience method already used by
the player HUD health bar, mob health bars, and (per its own Javadoc) explicitly intended for
"Damage numbers (when added)" and "Potion tooltips." That method itself delegates to
`HealthResourceAdapter.toUnits(...)` and `ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(...)`
— the one authoritative, round-half-away-from-zero, fixed-point Health conversion pipeline (see
`ResourceDisplayConversion`'s own Javadoc). No independent literal `5` (or `* 5`/`*5`) was
introduced anywhere in `HealingRollNotification` — checked by an automated source sentinel
(`healingRollNotificationUsesTheSharedHealthDisplayConversionNotAnIndependentLiteral`) in addition
to the behavioral proof of the exact display numbers in §8's test-verified examples (e.g. `8 → 40
HP`, `6 → 30 HP`, both matching the registered `×5` conversion exactly).

## 11. Full-health suppression behavior

The positive healing notification is sent only when `actualHealing > 0 && user instanceof
ServerPlayer`. At full health, `user.heal(amount)` is a vanilla no-op (health is already at
`getMaxHealth()`), so `healthAfter == healthBefore`, `actualHealing == 0`, and the `actualHealing >
0` guard suppresses the notification entirely — no misleading "healing occurred" message is ever
sent. Per the task's explicit instruction, **no new "already at full health" notification was
added** — inspection of `AlchemyPotionItem` and the rest of the Alchemy potion ladder found no
established Totality convention for such a notification on any existing potion, so none was
invented here. Item-consumption behavior at full health is unchanged from the existing
implementation: the potion is still consumed (no existing rule anywhere in this item's history
prevents consumption at full health — see the original implementation report §10 on the "no
remainder, fully consumed" convention, which this pass does not alter).

## 12. Confirmation that healing remains `LivingEntity`-generic

`HealingPotionItem.finishUsingItem(ItemStack, Level, LivingEntity)`'s signature is unchanged.
`user.heal(amount)` is called unconditionally whenever `amount > 0`, **before and independently of**
the `user instanceof ServerPlayer` check — a non-player `LivingEntity` (a mob) heals exactly as
before, with zero Notification API involvement, zero exceptions, and no behavior change. Verified:
- Reflection (`HealingPotionItemContractTest`, unchanged from the prior pass): no declared method on
  `HealingPotionItem` accepts a `Player`-family parameter.
- Source sentinel (`healSiteIsNotNestedInsideTheServerPlayerCheck`): `user.heal(` appears on an
  earlier source line than `instanceof ServerPlayer`.
- Source sentinel: `HealingAmount`/`HealingRollResult` import neither `ServerPlayer` nor any
  notification class (confirmed by import-line scan).

## 13. Notification wrapping design

Implemented entirely in `NotificationManager.renderActive` (the sole renderer), via two new pure,
independently-tested helpers plus one native-API-calling helper:

```
message
  → splitIntoParagraphs(message)      // split at explicit \n, preserving blank paragraphs (pure)
  → for each paragraph:
      wrapParagraph(font, paragraph, effectiveWidth(guiWidth))   // native font wrap (needs live Font)
  → flatten into visual lines, in order
  → render each with the existing LINE_HEIGHT, advancing y every visual line
```

`DamageRollNotification` and `HealingRollNotification` are completely unaware of this — they still
just build a message string with explicit `\n` for semantic paragraph breaks, exactly as before.
Automatic width-based wrapping is a rendering/layout concern owned entirely by `NotificationManager`.

## 14. Exact preferred and effective width policy

Named constants (scaled GUI pixels):

```java
private static final int PREFERRED_NOTIFICATION_WIDTH = 180;
private static final int RIGHT_SAFETY_MARGIN = 8;
private static final int MIN_EFFECTIVE_WIDTH = 1;
// PADDING_X (= 4, pre-existing) doubles as the named left origin/padding.
```

```java
static int effectiveWidth(int guiWidth) {
    int availableWidth = Math.max(MIN_EFFECTIVE_WIDTH, guiWidth - PADDING_X - RIGHT_SAFETY_MARGIN);
    return Math.min(PREFERRED_NOTIFICATION_WIDTH, availableWidth);
}
```

Never returns zero or negative (floored at `MIN_EFFECTIVE_WIDTH = 1`); never forces a width larger
than the screen actually has room for (the `Math.min` against `availableWidth`). Recomputed every
render call from the current `graphics.guiWidth()` — no cache, so it responds immediately to
window-resize or GUI-scale changes, with negligible cost at the existing 5-notification cap.
Verified behaviorally (real tests, not sentinels) in `NotificationManagerLayoutTest`: a wide screen
uses exactly `180`; a `100`px-wide screen uses exactly `100 - 4 - 8 = 88`; screens from `-50` to
`400`px wide (stepped) never exceed their own available space and never go non-positive.

## 15. Exact Minecraft font-splitting method used

`net.minecraft.client.gui.Font#split(net.minecraft.util.FormattedText input, int maxWidth)` →
`List<FormattedCharSequence>`. Confirmed via the mapped Minecraft 26.2 sources jar
(`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.2/*-sources.jar`,
`net/minecraft/client/gui/Font.java`) — it delegates to
`Language.getInstance().getVisualOrder(this.splitter.splitLines(input, maxWidth, Style.EMPTY))`,
Minecraft's own native word-wrap/bidi-aware line splitter. Called as
`font.split(Component.literal(paragraph), maxWidth)` (a `Component` implements `FormattedText`).
Each resulting `FormattedCharSequence` is rendered via the existing access-widened
`GuiGraphicsExtractor.text(Font, FormattedCharSequence, int x, int y, int color, boolean
dropShadow)` overload — the same rendering primitive the file already used for the plain-`String`
overload, just switched to the `FormattedCharSequence` overload for wrapped lines. No character
count or `String.length()`-based wrapping was used anywhere (checked by source sentinel).

## 16. Explicit-newline and blank-line handling

`splitIntoParagraphs(String message)` is `message.split("\n", -1)` — limit `-1` preserves trailing
(and, as `String.split` already does by default, interior) empty strings, so `"above\n\nbelow"`
yields `["above", "", "below"]` and `"text\n"` yields `["text", ""]` (both verified by real tests).
An empty paragraph is handled narrowly in `wrapParagraph`: Minecraft's native splitter returns **no**
lines at all for empty text, so an explicit check (`paragraph.isEmpty()`) short-circuits to exactly
one `FormattedCharSequence.EMPTY` — preserving the blank line as a real, `y`-advancing visual line
rather than silently merging the surrounding sections.

## 17. Vertical stacking behavior

`renderActive` tracks a single running `y`, starting at `PADDING_Y` (unchanged), and increments it
by `LINE_HEIGHT` (unchanged, `11`) for **every** visual line produced — across every paragraph,
across every notification, in queue order. Because wrapping happens inline in the same loop that
advances `y`, a notification with more wrapped lines than another simply consumes more vertical
space before the next notification's first line begins; there is no separate pass, no fixed
per-notification height assumption, and no possibility of two notifications' text overlapping
regardless of how much any individual one wraps.

## 18. Confirmation that lifetime and queue semantics remain unchanged

`LIFETIME_TICKS` (80), `FADE_TICKS` (20), `MAX_NOTIFICATIONS` (5), the `active.remove(0)`
oldest-eviction rule, `add(...)`'s signature, `tickActive()`, `computeAlpha(...)`, `clear()`, the
`ClientTickEvents.END_CLIENT_TICK`/`HudElementRegistry`/`ClientPlayConnectionEvents.DISCONNECT`
wiring, and the `Notification` inner class are all **byte-for-byte unchanged** except for
`renderActive`'s body and the addition of the three new width constants and three new private/
package-private helper methods. Wrapping produces multiple **visual lines** per notification but
never creates additional **queue entries** — `active` still holds exactly one `Notification` per
original `add(...)` call, confirmed by inspection (no code path anywhere adds to `active` except the
pre-existing `add(...)`) and by the pre-existing `NotificationTimingVerification` dev-self-test
suite (unmodified, still exercises `tickActive`/`computeAlpha`/`addForTest`/`sizeForTest`/
`ticksLeftForTest`/`clear` directly) continuing to report "All 9 self-test checks passed" during
`runDatagen`'s client-init self-test run (§21/§22).

## 19. Exact files changed

**New (production):**
- `src/main/java/zcylas/totality/item/potion/dnd/HealingRollResult.java`
- `src/main/java/zcylas/totality/networking/potion/HealingRollNotification.java`

**Modified (production):**
- `src/main/java/zcylas/totality/entity/base_weapon/ThrownShurikenEntity.java` (Part A: pass
  `weaponName` to the explicit-name `CombatResolver.resolveAttack` overload)
- `src/main/java/zcylas/totality/item/potion/dnd/HealingAmount.java` (Part B: added
  `rollDetailed(RandomSource)`; `roll(RandomSource)` now delegates to it)
- `src/main/java/zcylas/totality/item/potion/dnd/HealingPotionItem.java` (Part B: records health
  before/after, computes `actualHealing`, sends the notification for a `ServerPlayer`)
- `src/main/java/zcylas/totality/client/renderer/hud/notification/NotificationManager.java`
  (Part C: `renderActive` rewritten to wrap; three new named width constants; three new helper
  methods)

**New (test):**
- `src/test/java/zcylas/totality/entity/base_weapon/ThrownShurikenEntityWeaponNameTest.java`
- `src/test/java/zcylas/totality/item/potion/dnd/HealingRollResultTest.java`
- `src/test/java/zcylas/totality/item/potion/dnd/HealingNotificationSourceRegressionTest.java`
- `src/test/java/zcylas/totality/networking/potion/HealingRollNotificationFormatMessageTest.java`
- `src/test/java/zcylas/totality/client/renderer/hud/notification/NotificationManagerLayoutTest.java`
- `src/test/java/zcylas/totality/client/renderer/hud/notification/NotificationWrappingSourceRegressionTest.java`

**Modified (test):**
- `src/test/java/zcylas/totality/item/potion/dnd/HealingAmountTest.java` (+7 tests covering
  `rollDetailed`)

**Modified (reports):**
- `Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md` (new §23)
- `Context/Audit/TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_REPORT.md` (new §17)

**New (this report and its bundle):**
- `Context/Audit/TOTALITY_DND_POTION_HEALING_NOTIFICATION_AND_HUD_WRAP_REPORT.md` (this file)
- `Context/Audit/Review Bundles/TOTALITY_DND_POTION_HEALING_NOTIFICATION_AND_HUD_WRAP_BUNDLE.zip`

No generated file was regenerated with different content by this pass (`runDatagen` reported
`written: 0` — see §22).

## 20. Test categories and their evidentiary limits

- **Runtime behavioral tests** (execute for real, no bootstrap needed): `HealingAmountTest`
  (including all new `rollDetailed` tests), `HealingRollResultTest`,
  `HealingRollNotificationFormatMessageTest`, `NotificationManagerLayoutTest`. These are genuine
  proof of the computed values/text they assert on.
- **Reflection architecture tests**: `HealingPotionItemContractTest` (unchanged from the prior
  pass) plus the one reflection check in `ThrownShurikenEntityWeaponNameTest`
  (`combatResolverHasAnExplicitWeaponNameOverload`) — real, executing proof that a specific method
  signature exists; does not execute the method body.
- **Source-regression sentinels** (cannot execute the code under test; `ThrownShurikenEntity` and
  `HealingPotionItem` both extend Minecraft classes that require a live, unfrozen registry to
  construct — confirmed unreachable under plain JUnit by direct probing in the prior passes,
  documented in each sentinel file's class Javadoc): the remaining tests in
  `ThrownShurikenEntityWeaponNameTest`, all of `HealingNotificationSourceRegressionTest`, and all of
  `NotificationWrappingSourceRegressionTest`. These check source text only — import lines, literal
  argument shape, named-constant presence — never runtime control flow or actual argument values.
- **Manual verification**: required for the actual native `Font.split(...)` wrapping behavior, the
  actual in-game notification text/color, the actual Shuriken impact label in flight, and actual
  visual vertical stacking — see §24.

## 21. Exact focused and full validation commands and results

```
./gradlew test --tests "zcylas.totality.entity.base_weapon.*"
```
**6 tests, 0 failures, 0 errors, 0 skipped** (1 test class).

```
./gradlew test --tests "zcylas.totality.item.potion.dnd.*" --tests "zcylas.totality.networking.potion.*"
```
**59 tests, 0 failures, 0 errors, 0 skipped** (6 test classes — includes the 15 pre-existing tests
in `HealingPotionItemContractTest`/`DndPotionOfHealingSourceRegressionTest`, unchanged, plus this
pass's additions).

```
./gradlew test --tests "zcylas.totality.client.renderer.hud.notification.*"
```
**15 tests, 0 failures, 0 errors, 0 skipped** (2 test classes, both new).

```
./gradlew test
```
**74 test classes, 959 tests, 0 failures, 0 errors, 0 skipped** — computed from
`build/test-results/test/TEST-*.xml` (up from 909 before this pass; net +50 exactly matches the sum
of the three focused runs' new tests: 6 (Part A) + 29 (Part B: 22 in three new classes + 7 net-new
in `HealingAmountTest`) + 15 (Part C) = 50).

```
./gradlew runDatagen
```
Log line: `Caching: total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0`.

```
./gradlew clean build
```
**BUILD SUCCESSFUL** (ran as a clean rebuild specifically to rule out stale client classes, per the
task's own instruction — not merely an incremental `build`).

## 22. Datagen result and whether any generated files changed

`written: 0` — no generated file's content changed. Confirmed via
`git status --short src/main/generated`: the only entries present are the same ones already known
from the prior D&D-potion passes (`lang/en_us.json`, `tags/item/potions.json` modified;
`items/dnd_potion_of_healing.json` new/untracked; `.cache/` untracked) — none of them newly touched
by this pass. This is expected: Parts A/B/C touch item behavior, notification formatting, and HUD
rendering, none of which participate in datagen. The prior pass's accepted `minecraft:constant`
tint output (§Correction Report §5) is confirmed still present and unchanged.

## 23. Build result

`./gradlew clean build` → **BUILD SUCCESSFUL**, 8 actionable tasks, all executed (not
`UP-TO-DATE`, since `clean` ran first) — `compileJava`, `compileTestJava`, `test`, and `check` all
passed with the same 959/0/0/0 totals as §21's direct `./gradlew test` run.

## 24. Manual validation performed and remaining

**Update (final closeout pass):** the project owner has since manually confirmed the reachable
gameplay paths below work correctly. This supersedes this section's original "not performed this
pass" status; it does not imply every originally-listed scenario was individually exercised — only
what is recorded here as confirmed.

**Confirmed manually:**
- Thrown Shuriken notifications retain the projectile's correct stored name (naming fix from this
  report remains correct in live play).
- Potion of Healing behavior and its healing-roll notification: regression-confirmed working
  correctly (drinking, healing, and the green notification format).
- Central Notification API rendered-width wrapping: confirmed working in live play (long
  notifications wrap through the central renderer rather than overflowing or losing text).

**Not individually confirmed as manually tested, and not claimed here:** the specific granular
scenarios originally listed below remain unconfirmed at that level of detail — e.g. a literal
last-Shuriken-in-stack throw, a custom-renamed Shuriken, drinking at exactly-full health, a full
5-notification queue with a 6th eviction, multiple distinct GUI-scale settings, or an explicitly
authored blank notification line. These are not known to be broken; they simply were not
individually recorded as confirmed and so are not claimed as passed. The original checklist below is
retained for whatever manual re-verification is done in a future session:

*Shuriken:* throw the last Shuriken from a stack and confirm the impact notification still
identifies it; throw a Shuriken and switch held items before impact, confirm the label is still
correct; test a custom-named Shuriken if renaming is supported; confirm attack roll, damage,
pickup, and projectile behavior are otherwise unchanged.

*Potion:* `/give @s totality:dnd_potion_of_healing`; drink while missing more than the maximum
possible roll and confirm a green notification with matching dice/total/display values; drink while
missing less than the rolled amount and confirm rolled vs. restored values differ correctly; drink
at full health and confirm no misleading positive-restoration notification; confirm exactly one item
is consumed with no empty bottle; confirm no healing floating number appears; confirm the existing
Alchemy Potion of Healing is unaffected.

*Notification wrapping:* test at multiple GUI scales and window widths, including a deliberately
narrow window; test the two example weapon-damage notifications from the task spec (Iron Sword +
Bless; Legendary Dragonbone Greatsword with four named modifiers) and confirm no information loss;
test the long Potion of Healing notification; test multiple simultaneous notifications, a full
5-notification queue, and a 6th notification (confirm only the oldest is evicted, not a wrapped
visual line); test alongside the mob health panel; test explicit authored newlines and a
deliberately authored blank line; confirm lifetime/fading/disconnect-clearing/F1-pause behavior is
visually unchanged.

## 25. Confirmation that no healing floating-number work was added

No floating combat-text number, no new floating-number rendering path, and no change to
`CombatTextRenderer`/`CombatTextPayload` were added. `HealingRollNotification` sends a chat-style
top-left HUD text notification via the existing `SendNotificationPayload`/`NotificationManager`
pipeline — an entirely different, pre-existing presentation mechanism, not a new one, and not the
floating-number mechanism the task explicitly deferred.

## 26. Confirmation that future floating-number work remains deferred

No Visual Effect API design or implementation work was started. The task's explicit note that
healing floating numbers belong to a future Visual Effect API is unaffected — nothing in this pass
assumes, names, or scaffolds such an API.

## 27. Confirmation that no Alchemy integration was added

`HealingRollResult`, `HealingRollNotification`, and the modified portions of `HealingAmount`/
`HealingPotionItem` contain zero imports of any Alchemy class — checked by automated source
sentinels (import-line scan, case-insensitive on the substring `"alchemy"`) in
`HealingNotificationSourceRegressionTest`. No existing Alchemy file was modified. The Thrown
Shuriken fix and Notification API wrapping are both entirely unrelated to Alchemy.

## 28. Final task-scoped diff and status

Task-scoped `git diff --stat` (restricted to tracked files touched by this pass):

```
 .../hud/notification/NotificationManager.java      | 66 ++++++++++++++++++++--
 .../entity/base_weapon/ThrownShurikenEntity.java   |  5 +-
 2 files changed, 65 insertions(+), 6 deletions(-)
```

(`HealingRollResult.java`, `HealingRollNotification.java`, and all six new/modified test files are
untracked/new, so they do not appear in a tracked `git diff` — their full content is captured
directly in the review bundle instead.)

Task-scoped `git status --short`, restricted to every file this pass and the two prior D&D-potion
passes together touched:

```
 M src/main/generated/assets/totality/lang/en_us.json
 M src/main/generated/data/totality/tags/item/potions.json
 M src/main/java/zcylas/totality/client/renderer/hud/notification/NotificationManager.java
 M src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java
 M src/main/java/zcylas/totality/datagen/ModItemTagProvider.java
 M src/main/java/zcylas/totality/datagen/ModModelProvider.java
 M src/main/java/zcylas/totality/entity/base_weapon/ThrownShurikenEntity.java
 M src/main/java/zcylas/totality/init/ModItems.java
?? "Context/Audit/Review Bundles/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_BUNDLE.zip"
?? "Context/Audit/Review Bundles/TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_BUNDLE.zip"
?? Context/Audit/TOTALITY_DND_POTION_OF_HEALING_IMPLEMENTATION_REPORT.md
?? Context/Audit/TOTALITY_DND_POTION_OF_HEALING_REVIEW_CORRECTION_REPORT.md
?? src/main/generated/assets/totality/items/dnd_potion_of_healing.json
?? src/main/java/zcylas/totality/init/items/DndPotionItems.java
?? src/main/java/zcylas/totality/item/potion/dnd/
?? src/main/java/zcylas/totality/networking/potion/
?? src/test/java/zcylas/totality/client/renderer/
?? src/test/java/zcylas/totality/entity/
?? src/test/java/zcylas/totality/item/
?? src/test/java/zcylas/totality/networking/potion/
```

(This report and its bundle will also appear untracked once written.)

## 29. Final repository-wide status

`git status --short` reports **69** entries (up from 63 at the start of this pass — the expected
+6: 2 tracked files newly modified, `NotificationManager.java` and `ThrownShurikenEntity.java`, plus
4 new untracked directory entries: `src/main/java/zcylas/totality/networking/potion/`,
`src/test/java/zcylas/totality/client/renderer/`, `src/test/java/zcylas/totality/entity/`, and
`src/test/java/zcylas/totality/networking/potion/` — `src/test/java/zcylas/totality/item/` and
`src/main/java/zcylas/totality/item/potion/dnd/` already existed as untracked directory entries from
the prior passes and simply gained files inside them, so they do not add new top-level status
lines). All previously-known unrelated entries (`build.gradle`, 22 generated JSONs, 4 screenshots,
`log4j-dev.xml`, `logs/`, `.cache/`, 18 Resource-API bundles) remain present and untouched.

## 30. Confirmation that unrelated dirty-tree files were preserved

No broad staging (`git add -A`), cleanup, or formatting command was run. Every edit in this pass
targeted a single named file. All pre-existing unrelated dirty-tree entries listed in §29 remain
present and unmodified.

## 31. Confirmation that Phase 3C remains unstarted

No Resource API parity/synchronization file was touched by this pass. This work is entirely scoped
to a projectile notification-label bug fix, a standalone potion's notification presentation, and
central HUD notification rendering — unrelated to the Generic Player Resource API in every respect.

## 32. Confirmation that no commit or push occurred

No `git add`, `git commit`, or `git push` was run at any point during this pass. HEAD remained
`6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09` throughout, verified both before and after all
implementation and validation.

## 33. Manual-testing finding and combined-notification follow-up

Manual testing of this pass's accepted work (Potion of Healing, Shuriken-label correction,
Notification API wrapping) surfaced two follow-up items, addressed in a separate subsequent pass:

1. **Notification-content finding**: the existing damage-only combat notification
   (`DamageRollNotification`, a bare "Miss!" text on a miss) did not show the attack roll itself —
   the natural d20, ability modifier, proficiency, Bless/other attack-roll bonuses, final total, or
   target AC — making it hard to understand *why* an attack hit or missed. This was not a defect in
   the wrapping work itself; §13–§18 above (wrapping design, width policy, native font API,
   newline/blank-line handling, vertical stacking, unchanged lifetime/queue semantics) remain
   accurate and unaffected.
2. **Real Bless lifecycle bug**: manual testing also confirmed a pre-existing, unrelated bug —
   Bless's attack-roll bonus kept applying after its visible status effect expired, until the player
   disconnected and reconnected. This is independent of anything in this report.

Both are fully addressed in
`Context/Audit/TOTALITY_COMBAT_ROLL_NOTIFICATION_AND_BLESS_LIFECYCLE_CORRECTION_REPORT.md`: the
damage-only notification was replaced with a combined attack-and-damage notification
(`CombatRollNotification`, superseding `DamageRollNotification`, which was removed), and the Bless
(and Rage) attack-bonus lifecycle was corrected at the shared `RollModifierRegistry` level. Neither
change modified anything this report describes — the Potion of Healing's own notification format
(§7/§8) is explicitly unchanged (confirmed in the new report's own regression checks), and
`NotificationManager`'s central wrapping (§13–§18) was not touched at all in that pass.
