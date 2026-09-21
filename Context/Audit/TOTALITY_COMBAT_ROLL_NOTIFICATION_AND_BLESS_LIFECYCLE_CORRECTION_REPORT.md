# Totality — Combined Combat-Roll Notification and Bless Lifecycle Correction Report

## 1. Starting branch and HEAD

- Branch: `feature/general-resource-api`
- HEAD: `6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09` (verified via `git rev-parse HEAD` before any
  change; confirmed unchanged after all implementation and validation)

## 2. Starting repository-wide status

`git status --short` reported **71** entries before this pass: `build.gradle`, 22 generated
loot-table/worldgen/recipe JSONs, 4 Trading Test screenshots, `log4j-dev.xml`, `logs/`,
`src/main/generated/.cache/`, all 18 pre-existing Phase 3A/3B Resource-API review-bundle ZIPs, the 3
D&D-potion-and-notification review-bundle ZIPs, the 3 D&D-potion-and-notification reports, and the
full set of accepted D&D Potion of Healing / Shuriken-fix / Notification-wrapping implementation and
test files from the three prior passes.

## 3. Starting task-scoped status

Restricted to the accepted task-scoped file set (per the three prior reports' file lists), the
status matched exactly, with no unexpected changes — spot-checked `HealingPotionItem.java`,
`ModModelProvider.java`'s `Constant` tint, `ThrownShurikenEntity.java`'s `weaponName` argument, and
`NotificationManager.java`'s `PREFERRED_NOTIFICATION_WIDTH`/`effectiveWidth`/`splitIntoParagraphs`
against the three prior reports' documented final state — all matched exactly. No unexplained
changes were present, so implementation proceeded.

## 4. Existing combat-notification flow (before this pass)

`CombatResolver.handleHit` (the shared hit/miss resolution for both weapon and spell attacks) sent
two structurally different notifications depending on outcome:
- **Miss**: a single bare line, `label + " — Miss!"`, sent via `SendNotificationPayload` directly
  with color `GRAY` — no attack-roll detail (no natural roll, ability mod, proficiency, Bless, or
  target AC) shown at all.
- **Hit/critical**: `DamageRollNotification.send(...)`, a two-line damage-only notification (e.g.
  `"Fireball — 8d6 → [4,2,6,1,5,3,2,1] = 24"` style) showing the damage-roll breakdown and total,
  with color `GOLD` — but nothing about the attack roll that determined the hit, since
  `AttackRoll.Result` (the record `AttackRoll.roll(...)` returns) only ever retained `outcome` and
  `bonuses`; the natural d20 roll(s), ability modifier, proficiency bonus, target AC, and attack
  total were all computed locally inside `AttackRoll.roll` and then discarded.

`DamageRollNotification.send` had exactly one production call site (inside `handleHit`) — confirmed
by an exhaustive grep across `src/main/java` before making any change.

## 5. Final combined notification design

Two coordinated changes:

1. **`AttackRoll.Result` retains everything needed for presentation.** Expanded from
   `record Result(RollOutcome outcome, List<DiceBonus> bonuses)` to
   `record Result(int roll1, int roll2, RollType rollType, int usedRoll, AbilityScore abilityScore,
   int abilityMod, int proficiencyBonus, int targetAc, int total, RollOutcome outcome,
   List<DiceBonus> bonuses)`. `AttackRoll.roll(...)`'s own resolution logic (hit/miss/crit
   determination, nat-1/nat-20 rules) is byte-for-byte unchanged — every new field was already a
   local variable computed exactly once inside the existing method; this change only stops
   discarding them.
2. **`CombatRollNotification`** (new, `zcylas.totality.networking.combat`, parallel to but replacing
   `DamageRollNotification`) formats and sends one notification per resolved attack, built entirely
   from the retained `AttackRoll.Result` plus (when the attack hit) the retained `DamageRollResult`
   — nothing is rerolled. `CombatResolver.handleHit` now calls it exactly once on every path (miss
   or hit), instead of the previous two different notification mechanisms.

## 6. Exact semantic-line limit

At most three explicitly authored semantic lines, joined by exactly two `\n` at most:
1. `<label> — <HIT|MISS|CRITICAL HIT>`
2. `ATK [<roll notation>] <modifiers...> = <total> vs AC <targetAc>`
3. `DMG [<rolls>] <modifiers...> = <total> (<display>)` — **only present when damage was rolled**
   (i.e. never on a miss)

Verified structurally: `CombatRollNotificationSourceRegressionTest` confirms exactly 2 `append('\n')`
call sites in the formatter's source (never more), and
`missOmitsTheDamageLineEntirely`/`naturalOneIsAMissLabelNotACriticalMissLabel` in
`CombatRollNotificationFormatMessageTest` confirm a miss produces exactly 2 rendered lines. Any
further **wrapping** of these lines onto additional *visual* lines (e.g. for a long weapon name) is
`NotificationManager`'s existing central responsibility (Part C, untouched by this pass) — confirmed
by a source sentinel that `CombatRollNotification.java` never references `Font`, `font.split`, or
`guiWidth`.

## 7. Uppercase structural-label policy

Uppercase, always: `ATK`, `DMG`, `PROF`, `AC`, `HIT`, `MISS`, `CRITICAL HIT`, and every ability-score
abbreviation (`STR`, `DEX`, `CON`, `INT`, `WIS`, `CHA`, `FTH`, `END`) — the last of these come
directly from `AbilityScore.name()` (the enum constant's own name, e.g. `AbilityScore.DEX.name()` →
`"DEX"`), not a separately-authored abbreviation table, so there is exactly one place these strings
are defined. Named effects/abilities (`Bless`, `Rage`, `Divine Favor`, `Battle Focus`, `Weapon
Enchantment`) are rendered in their authored display case exactly as already stored in each
`DiceBonus`/`DamageBonus`'s own `label` field — the formatter never calls `.toUpperCase()` on a
bonus label, and never uppercases the whole message. Verified by
`namedEffectsRetainAuthoredDisplayCaseWhileStructuralLabelsAreUppercase`.

## 8. Attack-roll data included

Directly from the retained `AttackRoll.Result`, with no reroll: natural roll(s) and the selected
roll (`roll1`/`roll2`/`usedRoll`, compactly notated — see §11), the ability score used and its
modifier (`abilityScore`/`abilityMod`, shown as e.g. `+3 DEX`, omitted entirely when `abilityMod ==
0`), proficiency bonus (`proficiencyBonus`, shown as e.g. `+2 PROF`, omitted when `0` — i.e. an
unproficient attacker shows no `PROF` segment at all), every labeled attack-roll bonus in `bonuses`
(e.g. Bless, in registration order, each as `<signed value> <label>`), the final total (`total`),
and the target's AC (`targetAc`) — always shown, regardless of outcome, since it is informational
context, not a claim about what determined the outcome (nat-1/nat-20 auto-resolve regardless of AC,
unchanged — see §16).

## 9. Damage-roll data included

Directly from the retained `DamageRollResult` (only present on a hit): the exact rolled dice
(`dmg.rolls()`, e.g. `[6]` or `[4, 6]` for a doubled critical-hit dice count — dice-doubling logic
itself is unchanged, still in `handleHit`), the damage ability-score contribution (computed as
`dmg.modifier() - sum(damageBonuses)`, shown as e.g. `+3 STR`, omitted when zero or when no damage
ability score applies, e.g. for some spells), every named damage bonus (e.g. Rage, exactly as
before), the final rolled mechanical total (`dmg.total()`), and the existing Totality ×5 display
equivalent — computed via the same `RpgDisplayUtils.HP_DISPLAY_MULTIPLIER` the old
`DamageRollNotification` already used (no new/independent literal introduced). This remains the
**rolled** damage result — pre-mitigation, not actual health loss; floating combat text remains
responsible for actual-health-loss presentation, unchanged (§29).

## 10. Hit, miss, and critical examples

Verified by real, executing tests (`CombatRollNotificationFormatMessageTest`), not sentinels:

```
Iron Sword — HIT
ATK [12] +3 DEX +2 PROF +3 Bless = 20 vs AC 15
DMG [6] +3 STR +2 Rage = 11 (55)
```

```
Iron Sword — MISS
ATK [7] +3 DEX +2 PROF +1 Bless = 13 vs AC 15
```

```
Iron Sword — CRITICAL HIT
ATK [20] +3 DEX +2 PROF +2 Bless = 27 vs AC 15
DMG [4, 6] +3 STR +2 Rage = 15 (75)
```

Additional verified edge cases: a natural 1 renders as `MISS` (not a separate "critical miss"
label); zero ability-mod and zero-proficiency both cleanly omit their segments
(`"Unarmed Strike — HIT\nATK [12] = 12 vs AC 10"`); a zero damage-ability-contribution omits only
that segment while keeping named damage bonuses (`"...ATK [12] +2 PROF = 14 vs AC 10\nDMG [4] +2
Rage = 6 (30)"`).

## 11. Advantage/disadvantage representation

Compact notation using only already-retained data — `roll1`, `roll2` (both natural rolls actually
made), and `usedRoll` (the one selected per `rollType`): `"<roll1>, <roll2> → <usedRoll>"`, e.g.
`ATK [8, 16 → 16]` for advantage picking the higher roll, or `ATK [14, 5 → 5]` for disadvantage
picking the lower. For a normal roll (`rollType == NORMAL`, `roll2 == -1`, no second roll was ever
made), the notation is just the single value, e.g. `ATK [12]`. Verified by
`advantageUsesTheCompactRollNotationWithoutRerolling`/`disadvantageUsesTheCompactRollNotationWithoutRerolling`
— both construct the `AttackRoll.Result` directly with pre-determined roll values and confirm the
formatter reproduces them verbatim, proving no second roll is made for presentation.

## 12. Shuriken-label preservation

`ThrownShurikenEntity.onHitEntity` was **not modified** in this pass — the accepted fix (passing the
projectile-stored `weaponName` into `CombatResolver`'s explicit-name `resolveAttack` overload) is
untouched, confirmed by `git diff` showing no changes to that file and by
`ThrownShurikenEntityWeaponNameTest` (all 6 tests, unchanged) continuing to pass. Because that
overload flows into the same `handleHit` this pass modified, a thrown Shuriken's combined
notification now correctly shows the full `ATK`/`DMG` breakdown labeled with the projectile's own
stored item name — the same authoritative label as before, just richer in content.

## 13. Bless bug reproduction

Manually confirmed steps (as reported): Bless applied and correctly contributed to attack rolls;
the visible Bless status effect expired naturally; Bless continued contributing to later attack
rolls in the same world session; only leaving and re-entering the world (disconnect/reconnect)
cleared the stale contribution.

## 14. Root cause

Traced the full Bless attack-bonus path:
- `BlessSpell.applyBless` calls `target.addEffect(new MobEffectInstance(ModEffects.BLESS,
  DURATION_TICKS, ...))`, a normal vanilla-duration status effect.
- `BlessEffect.onEffectAdded` registers an anonymous `RollModifierRegistry.RollModifier` under key
  `BlessSpell.ID`, whose `getAttackBonusList`/`getSaveBonusList` roll and return a `+1d4 Bless`
  `DiceBonus` **unconditionally, every time they are called** — with no check of whether Bless is
  still actually active.
- `BlessEffect.onEffectRemoved` (overriding Fabric API's `FabricMobEffect.onEffectRemoved`, a
  callback interface `MobEffect` implements — confirmed via `javap` against the actual compiled
  `net.minecraft.world.effect.MobEffect` class on this project's classpath, which implements
  `net.fabricmc.fabric.api.entity.event.v1.effect.FabricMobEffect`) is the **only** code path that
  calls `RollModifierRegistry.remove(sp, BlessSpell.ID)` short of a full player-registry wipe.
- `RollModifierRegistry` itself is a purely **push-based** registration store: once registered, a
  `RollModifier` stays in the map and is included in every `resolveAttackBonusList`/etc. call
  forever, until something explicitly calls `remove(...)`.
- Separately, `BlessSpell` maintains its **own, independent, hand-rolled duration tracker** — a
  `Map<UUID, BlessEntry(targets, expiryTick)>` plus a `ServerTickEvents.END_SERVER_TICK` listener
  that recomputes `now >= bless.expiryTick()` against `server.overworld().getGameTime()` every tick
  and calls `target.removeEffect(ModEffects.BLESS)` when expired — duplicating, rather than
  delegating to, the vanilla `MobEffectInstance`'s own duration countdown (which independently
  decrements once per entity tick and triggers its own natural-expiration removal via
  `LivingEntity.tickEffects()` → `onEffectsRemoved(...)`). This second system exists for a
  legitimate reason (early termination on lost concentration, which vanilla's own per-target
  duration cannot express), but duplicates the pure-duration case unnecessarily.
- **Given two independent systems that can each end up calling (or failing to call, or racing to
  call) `target.removeEffect(ModEffects.BLESS)` for the same logical "Bless has ended" event, and
  given the actual removal of the `RollModifierRegistry` entry depends entirely on
  `onEffectRemoved` firing correctly for whichever removal path actually runs**, the registration is
  fragile by construction: correctness depends on every single removal-triggering code path
  reliably invoking the callback, with no independent verification at read time. The general defect
  is architectural, not a one-line typo: `RollModifierRegistry` trusts a push notification instead
  of consulting the modifier's own current truth.

## 15. Why world reload previously cleared the issue

`RollModifierRegistry.clearPlayer(UUID)` — called only from `PlayerConnectionEvents` on
disconnect — unconditionally wipes **every** registered modifier for that player, regardless of
whether any individual modifier's underlying effect is actually still active or not. This is a blunt
full reset, entirely independent of whichever specific removal-callback path did or didn't fire
correctly — so it "fixes" a stuck Bless bonus (or any other stuck modifier) as a side effect,
without addressing why the bonus was stuck in the first place. This is exactly consistent with the
reported symptom.

## 16. Exact lifecycle correction

Added `boolean isActive()` to the `RollModifierRegistry.RollModifier` interface (default `true`, for
backward compatibility with a modifier that has no independent liveness signal). Every `resolve*`
method now calls a private `activeModifiers(UUID)` helper that filters the registered modifiers down
to only those reporting `isActive() == true`, **lazily evicting** (removing from the underlying map)
any that report `false` in the same pass — so a stale modifier is both excluded from the current
result and permanently cleaned up, not merely re-checked-and-skipped forever. `BlessEffect`'s and
`RageEffect`'s registered `RollModifier`s now implement `isActive()` as `sp.hasEffect(ModEffects.BLESS)`
/ `sp.hasEffect(ModEffects.RAGE)` respectively — Minecraft's own active-effects state, which is
always correct the instant an effect's duration ends, entirely independent of whether any particular
removal callback fired for that specific removal path. This is the **general** correction the task
preferred: it applies to the shared `RollModifierRegistry` infrastructure (both Bless and Rage use
it), not a Bless-specific special case, and it satisfies the architectural boundary "attack-roll
collection reads only currently active attack bonuses" literally — the registry no longer blindly
trusts a push-based entry; it verifies liveness against the real source of truth on every read.

The existing explicit-removal path (`BlessEffect.onEffectRemoved`/`RageEffect.onEffectRemoved`
calling `RollModifierRegistry.remove(...)`) was **not removed** — it remains as the immediate,
zero-latency removal path for the common case; `isActive()` is the safety net that guarantees
correctness even if that path is ever missed, raced, or delayed by exactly one resolve call.
`BlessSpell`'s own redundant tick-based duration tracker was **not modified** — it still serves the
legitimate, non-duplicated purpose of early termination on lost concentration, which the vanilla
per-effect duration cannot express on its own; removing or restructuring it was judged out of the
proven scope of this bug (see §14's "do not broaden beyond what inspection proves").

## 17. Natural-expiration behavior

After natural expiration, Bless's `RollModifier.isActive()` returns `false` (vanilla's own
`hasEffect` check) the instant the effect ends. The very next `resolveAttackBonusList`/etc. call for
that player excludes it from the result **and** evicts it from the registry — verified by
`modifierStopsContributingImmediatelyAfterGoingInactive`,
`expiredModifierDiceAreNotRolledAfterExpiration` (confirms the dice-rolling
`getAttackBonusList`/`getAttackBonusList`'s roll-counting call is never made again once inactive —
Bless dice are not rolled after expiration), `inactiveModifierIsEvictedFromTheRegistryNotJustSkipped`,
and `worldReloadIsNotRequiredToClearAnExpiredModifier` (explicitly never calls `clearPlayer`/
`clearForTest`, proving eviction happens through ordinary resolution alone).

## 18. Explicit-removal behavior

Unchanged and still tested: `RollModifierRegistry.remove(...)` (called by
`BlessEffect.onEffectRemoved` on any removal, natural or explicit) immediately clears the
registration — verified by `explicitRemovalClearsTheModifier`.

## 19. Reapplication and refresh behavior

`register(...)` always `.put()`s under the same source `Identifier` (`BlessSpell.ID`), so
re-registration replaces rather than stacks — verified by
`reapplicationRestoresExactlyOneBonusEntry` (an expired-then-reapplied Bless leaves exactly one
registered entry with the new roll values) and
`repeatedRegistrationUnderTheSameSourceIdDoesNotDuplicateTheBonus` (three consecutive registrations
under the same key still resolve to exactly one `DiceBonus`, not three). Existing Bless stacking
rules (one Bless bonus per player, D&D-style, unaffected by number of casts) are unchanged — no
change was made to `BlessSpell.onActivate`'s targeting/stacking logic at all.

## 20. Exact files changed

**New (production):**
- `src/main/java/zcylas/totality/networking/combat/CombatRollNotification.java`

**Modified (production):**
- `src/main/java/zcylas/totality/api/rpg/combat/AttackRoll.java` — `Result` record expanded to
  retain roll1/roll2/rollType/usedRoll/abilityScore/abilityMod/proficiencyBonus/targetAc/total;
  resolution logic unchanged
- `src/main/java/zcylas/totality/api/rpg/combat/CombatResolver.java` — `handleHit` now takes the
  full `AttackRoll.Result`; both branches call `CombatRollNotification.send` instead of the old
  bare-text miss notification and `DamageRollNotification`
- `src/main/java/zcylas/totality/api/rpg/combat/DamageRoll.java` — one comment updated (no longer
  references the removed class)
- `src/main/java/zcylas/totality/api/rpg/combat/RollModifierRegistry.java` — added
  `RollModifier.isActive()`, UUID-keyed internal implementation, lazy-eviction `activeModifiers(...)`
  helper, UUID-keyed test-only hooks; all existing public `ServerPlayer`-taking method signatures
  unchanged
- `src/main/java/zcylas/totality/effect/BlessEffect.java` — registered `RollModifier` now overrides
  `isActive()`
- `src/main/java/zcylas/totality/effect/RageEffect.java` — registered `RollModifier` now overrides
  `isActive()`

**Deleted (production):**
- `src/main/java/zcylas/totality/networking/combat/DamageRollNotification.java` — its one production
  call site was replaced; per this repository's own stated convention ("if you are certain that
  something is unused, you can delete it completely"), it was removed rather than left as dead code

**New (test):**
- `src/test/java/zcylas/totality/api/rpg/combat/RollModifierRegistryLifecycleTest.java`
- `src/test/java/zcylas/totality/effect/BlessLifecycleSourceRegressionTest.java`
- `src/test/java/zcylas/totality/networking/combat/CombatRollNotificationFormatMessageTest.java`
- `src/test/java/zcylas/totality/networking/combat/CombatRollNotificationSourceRegressionTest.java`

**Modified (reports):**
- `Context/Audit/TOTALITY_DND_POTION_HEALING_NOTIFICATION_AND_HUD_WRAP_REPORT.md` (new §33)

**New (this report and its bundle):**
- `Context/Audit/TOTALITY_COMBAT_ROLL_NOTIFICATION_AND_BLESS_LIFECYCLE_CORRECTION_REPORT.md`
- `Context/Audit/Review Bundles/TOTALITY_COMBAT_ROLL_NOTIFICATION_AND_BLESS_LIFECYCLE_CORRECTION_BUNDLE.zip`

No generated file was regenerated with different content by this pass (`runDatagen` reported
`written: 0` — see §24).

## 21. Test categories and evidentiary limits

- **Runtime behavioral tests** (execute for real, no bootstrap needed):
  `RollModifierRegistryLifecycleTest` (10 tests — exercises the real eviction/liveness logic via
  UUID-keyed test hooks and a fake `RollModifier`, genuine proof of the registry's own behavior) and
  `CombatRollNotificationFormatMessageTest` (9 tests — exercises the real pure formatter with
  directly-constructed `AttackRoll.Result`/`DamageRollResult` values, genuine proof of the exact
  notification text produced).
- **Source-regression sentinels** (cannot execute the code under test — `CombatResolver.handleHit`
  operates on real `LivingEntity`/`ServerPlayer` attackers, and `BlessEffect`'s registered
  `RollModifier` is only ever constructed inside `onEffectAdded(LivingEntity, int)`; both require a
  bootstrapped, unfrozen Minecraft registry, unreachable under plain JUnit — same constraint
  documented in `HealingPotionItemContractTest`'s class Javadoc from an earlier pass):
  `BlessLifecycleSourceRegressionTest` (4 tests — confirms `isActive()` is overridden and checks
  `hasEffect` in source text; cannot prove the callback actually fires or that `hasEffect` returns
  correctly at runtime) and `CombatRollNotificationSourceRegressionTest` (8 tests — confirms call-site
  shape, argument passing, and the old class's removal in source text; cannot prove the notification
  is actually sent or received in a live game).
- **Manual validation**: required for confirming Bless actually stops applying in a live game after
  expiration, for confirming the combined notification actually renders correctly in-game, and for
  the full checklist in §26 — not performed this pass (see §26).

## 22. Focused test commands and totals

```
./gradlew test --tests "zcylas.totality.api.rpg.combat.RollModifierRegistryLifecycleTest"
```
**10 tests, 0 failures, 0 errors, 0 skipped.**

```
./gradlew test --tests "zcylas.totality.effect.*"
```
**4 tests, 0 failures, 0 errors, 0 skipped** (`BlessLifecycleSourceRegressionTest`, the only test
class in this package).

```
./gradlew test --tests "zcylas.totality.networking.combat.*"
```
**17 tests, 0 failures, 0 errors, 0 skipped** (9 in `CombatRollNotificationFormatMessageTest`, 8 in
`CombatRollNotificationSourceRegressionTest`).

```
./gradlew test --tests "zcylas.totality.entity.base_weapon.*"
```
**6 tests, 0 failures, 0 errors, 0 skipped** — the accepted Shuriken test, re-run to confirm it
still passes unaffected by this pass's `CombatResolver`/`AttackRoll` changes.

## 23. Full test command and totals

```
./gradlew test
```
**78 test classes, 990 tests, 0 failures, 0 errors, 0 skipped** — computed from
`build/test-results/test/TEST-*.xml` (up from 959 before this pass; net +31 exactly matches the sum
of the four new test classes: 10 + 4 + 9 + 8 = 31).

## 24. Datagen result

```
./gradlew runDatagen
```
Log line: `Caching: total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0`
— zero files written. Confirmed via `git status --short src/main/generated` that only the same
already-known entries from prior passes remain (`lang/en_us.json`, `tags/item/potions.json`
modified; `items/dnd_potion_of_healing.json` new/untracked, still using the accepted
`minecraft:constant` tint; `.cache/` untracked) — no new or unexpected generated churn. This is
expected: this pass touches only combat-resolution logic, effect lifecycle, and notification
formatting, none of which participate in datagen.

## 25. Build result

```
./gradlew clean build
```
**BUILD SUCCESSFUL**, 8 actionable tasks, all executed (not `UP-TO-DATE`, since `clean` ran first —
this specifically rules out stale client classes). Re-verified the same 990/0/0/0 totals as §23
after the clean rebuild.

## 26. Manual validation performed and remaining

**Update (final closeout pass):** the project owner has since manually confirmed the reachable
gameplay paths below work correctly. This supersedes this section's original "not performed this
pass" status.

**Confirmed manually:**
- The combined combat-roll notification's `HIT` layout.
- The combined combat-roll notification's `MISS` layout.
- The combined combat-roll notification's `CRITICAL HIT` layout.
- Uppercase structural labels rendering correctly: `ATK`, `DMG`, `PROF`, ability abbreviations, and
  `AC`.
- Bless appearing on the `ATK` line while active.
- Bless correctly stopping after natural expiration, without needing to leave and re-enter the
  world — the Part E fix's core reported symptom is confirmed resolved in live play.
- Rage remaining conceptually on the `DMG` line (i.e. its damage-bonus presentation is unaffected by
  this pass's changes).
- Thrown Shuriken notifications retaining the projectile's correct stored name within the combined
  notification.
- Central Notification API rendered-width wrapping, in the context of these combined notifications.
- Potion of Healing behavior and its healing-roll notification: regression-confirmed unaffected by
  this pass's changes.

**Explicit Bless removal — not testable, not a failed validation:**
Explicit manual Bless removal (dispelling/cancelling Bless before it naturally expires) could not be
exercised through currently reachable gameplay, because Concentration-based or player-controlled
spell cancellation/toggling has not yet been implemented in this codebase. This is an **unavailable
gameplay path**, not a validation failure. The explicit-removal code path itself
(`RollModifierRegistry.remove(...)` / `BlessEffect.onEffectRemoved`) is covered by automated
verification at the currently testable layer — `explicitRemovalClearsTheModifier` in
`RollModifierRegistryLifecycleTest` (§21/§22) — which is real, executing proof of the registry's own
removal behavior, though it cannot prove the specific in-game trigger that would call it once one
exists. **When Concentration or spell cancellation is implemented, Bless's explicit-removal path
must be included in that feature's own manual and automated removal-regression suite.**

**Rage — deferred, not claimed as manually tested:**
Rage was **not** manually retested during this final pass. It uses the same generalized
`RollModifier.isActive()` liveness safeguard added for Bless (§16), and its automated validation
(`otherActiveModifiersStillFunctionWhenOneExpires`,
`attackTotalChangesOnlyByTheExpiredContributionsValue` in `RollModifierRegistryLifecycleTest`, plus
`rageEffectOverridesIsActiveAndChecksItsOwnEffect` in `BlessLifecycleSourceRegressionTest`) passed.
No Rage formula, activation rule, or other intended behavior was changed by this pass. Manual Rage
regression (confirming Rage still behaves correctly end-to-end in live combat, including its own
expiration) may be performed during the next relevant combat test session — it is not claimed as
passed here.

**Not individually confirmed, and not claimed:** several named bonuses simultaneously; advantage;
disadvantage; a long weapon name specifically under combined-notification wrapping; the last
Shuriken in a stack specifically; a Shuriken thrown while switching held items, specifically under
the combined notification (the underlying naming fix itself remains covered — see §12/§27 of the
prior report); reapplying/refreshing Bless in live play (covered automatically, not manually);
changing dimensions with Bless active.

## 27. Confirmation that Potion of Healing behavior remained unchanged

No file under `zcylas.totality.item.potion.dnd` or `zcylas.totality.networking.potion` was modified
by this pass — confirmed by `git status --short` showing no changes to `HealingAmount.java`,
`HealingRollResult.java`, `HealingPotionItem.java`, or `HealingRollNotification.java`. The full
`HealingRollNotificationFormatMessageTest` and `DndPotionOfHealingSourceRegressionTest` suites
re-ran clean as part of §23's full-suite run, with the same test counts as before this pass.

## 28. Confirmation that Notification wrapping remained centralized

`NotificationManager.java` was **not modified** by this pass — confirmed by `git status --short`
showing no change to it. `CombatRollNotification` was checked by source sentinel
(`combatRollNotificationDoesNotGuessPixelWidthsOrInsertManualWrapping`) to contain no `Font`/
`font.split`/`guiWidth` reference — it only ever authors up to two `\n` semantic-line breaks, exactly
like `HealingRollNotification` before it; all rendered-width wrapping remains
`NotificationManager`'s sole responsibility.

## 29. Confirmation that floating numbers and Visual Effect API were not changed

No file under `zcylas.totality.client.combat` (`CombatTextRenderer`) or
`zcylas.totality.networking.combat.CombatTextPayload` was modified. No Visual Effect API file or
design work was started. `CombatRollNotification`'s DMG line remains the **rolled** (pre-mitigation)
damage description, exactly as `DamageRollNotification` was before it — it does not claim to
represent actual post-mitigation health loss, which floating combat text continues to own.

## 30. Confirmation that no Alchemy integration was added

None of the six changed production files import any Alchemy class — checked by source sentinel
(`neitherFileGainedAnAlchemyOrCombatTextDependency` in `BlessLifecycleSourceRegressionTest`, import-
line scan) for `BlessEffect.java`/`RageEffect.java`; `AttackRoll.java`, `CombatResolver.java`,
`DamageRoll.java`, and `RollModifierRegistry.java` were already Alchemy-free before this pass and no
import was added to any of them.

## 31. Final task-scoped diff and status

Task-scoped `git diff --stat` (tracked files touched by this pass):

```
 .../zcylas/totality/api/rpg/combat/AttackRoll.java |  46 ++++++--
 .../totality/api/rpg/combat/CombatResolver.java    |  23 ++--
 .../zcylas/totality/api/rpg/combat/DamageRoll.java |   3 +-
 .../api/rpg/combat/RollModifierRegistry.java       | 116 +++++++++++++++++----
 .../java/zcylas/totality/effect/BlessEffect.java   |   8 ++
 .../java/zcylas/totality/effect/RageEffect.java    |   8 ++
 .../networking/combat/DamageRollNotification.java  |  64 ------------
 7 files changed, 167 insertions(+), 101 deletions(-)
```

(`CombatRollNotification.java` and all four new test files are untracked/new, so they do not appear
in a tracked `git diff` — their full content is captured directly in the review bundle instead.)

Task-scoped `git status --short`:

```
 M src/main/java/zcylas/totality/api/rpg/combat/AttackRoll.java
 M src/main/java/zcylas/totality/api/rpg/combat/CombatResolver.java
 M src/main/java/zcylas/totality/api/rpg/combat/DamageRoll.java
 M src/main/java/zcylas/totality/api/rpg/combat/RollModifierRegistry.java
 M src/main/java/zcylas/totality/effect/BlessEffect.java
 M src/main/java/zcylas/totality/effect/RageEffect.java
 D src/main/java/zcylas/totality/networking/combat/DamageRollNotification.java
?? src/main/java/zcylas/totality/networking/combat/CombatRollNotification.java
?? src/test/java/zcylas/totality/api/rpg/combat/
?? src/test/java/zcylas/totality/effect/
?? src/test/java/zcylas/totality/networking/combat/
```

## 32. Final repository-wide status

`git status --short` reports **82** entries (up from 71 at the start of this pass — the expected
+11: 6 tracked files modified, 1 tracked file deleted, 1 new untracked file
(`CombatRollNotification.java`), and 3 new untracked test-package directory entries
(`src/test/java/zcylas/totality/api/rpg/combat/`, `src/test/java/zcylas/totality/effect/`,
`src/test/java/zcylas/totality/networking/combat/`) — this report and its bundle will add 2 more
once written. All previously-known unrelated entries (`build.gradle`, 22 generated JSONs, 4
screenshots, `log4j-dev.xml`, `logs/`, `.cache/`, 18 Resource-API bundles, 3 prior D&D-potion/
notification bundles and reports) remain present and untouched.

## 33. Confirmation that unrelated dirty-tree files were preserved

No broad staging (`git add -A`), cleanup, or formatting command was run. Every edit in this pass
targeted a single named file (or, for the one deletion, a single named file via `rm`). All
pre-existing unrelated dirty-tree entries listed in §32 remain present and unmodified.

## 34. Confirmation that Phase 3C remains unstarted

No Resource API parity/synchronization file was touched by this pass. This work is entirely scoped
to combat-roll notification presentation and the Bless/Rage attack-bonus lifecycle — unrelated to
the Generic Player Resource API in every respect.

## 35. Confirmation that no commit or push occurred

No `git add`, `git commit`, or `git push` was run at any point during this pass. HEAD remained
`6a2a2b5a0dbe38408fb039e19dcffe83a00fcf09` throughout, verified both before and after all
implementation and validation.
