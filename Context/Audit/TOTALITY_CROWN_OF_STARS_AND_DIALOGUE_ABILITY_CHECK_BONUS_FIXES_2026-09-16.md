# Crown of Stars mote-fire fix & dialogue Ability Check bonus routing fix — 2026-09-16

Two small, independent post-Phase-6 investigations, performed on top of HEAD
`501ddfbffa5dad545ecaf701ffde78673a5ed3a0` (`feat(resources): migrate standard spell slots to
generic authority`). Neither touches the Generic Resource API's own behavior beyond calling its
existing public methods exactly as every other spell already does. Phase 7 was not started.

---

## Issue 1 — Crown of Stars follow-up mote bug

### 1.1 Investigation

Traced the real, current code — not assumed from the historical report.

**A. Initial cast, current code path** (`ActivateAbilityHandler.handle`,
`src/main/java/zcylas/totality/networking/ability/ActivateAbilityHandler.java`, pre-fix lines
43-111):
1. Line 65: `!spell.isCantrip()` is true (Crown is 7th-level) → pre-check queries
   `PlayerResourceService` for a 7th-level slot; requires one.
2. Line 95-97: `Spell.resetCastResult()` → `ability.onActivate(player, context)` →
   `CrownOfStarsSpell.onActivate` (`src/main/java/zcylas/totality/api/magic/spell/destruction/CrownOfStarsSpell.java`,
   lines 157-169): `charges.get(uuid)` is `null` → `summonCrown` (sets `charges = 7`).
3. `Spell.didCastSucceed()` is `true` (never calls `markNoEffect()`).
4. Line 102-110: spends one 7th-level slot. **Correct.**

**B. Firing a mote, pre-fix code path** — identical entry point, identical code, no branch
distinguishing it from a new cast:
- Pre-check runs again. If the player has **zero** 7th-level slots left (the exact "cast with the
  last slot" scenario the report describes), `hasSlot` is `false` and the handler returns **before
  `onActivate` is ever called** — the mote never fires; motes become permanently stranded.
- If the player still has a 7th-level slot (multiclass/high-level scenario), `onActivate` runs,
  `CrownOfStarsSpell.fireMote` correctly decrements the mote counter, but the handler then
  unconditionally spends **another** 7th-level slot for what should be a free follow-up action — a
  second, related bug not explicitly named in the original report but directly implied by "must NOT
  consume another 7th-level slot."

`SpellBoltEntity` (the mote projectile,
`src/main/java/zcylas/totality/entity/magic/SpellBoltEntity.java`) has zero references to
`onActivate`/`ActivateAbilityHandler`/`trySpend`/`PlayerResourceService` — **confirmed the
projectile itself is not at fault**, contrary to the original suspicion.

**Confirmed: both halves of the bug are real on current HEAD.**

### 1.2 Root cause

`ActivateAbilityHandler` treats every activation of a non-cantrip `Spell` identically. There was no
seam anywhere for a spell to say "this activation is a follow-up action on an already-active
instance, not a new cast." `CrownOfStarsSpell.onActivate` decides summon-vs-fire internally
(`charges` map), but that decision was invisible to the handler, which always ran the same
slot-precheck/spend regardless.

### 1.3 Exact fix

**New virtual method on `Spell`**
(`src/main/java/zcylas/totality/api/magic/spell/Spell.java`), default `false`:

```java
public boolean isActiveInstanceAction(ServerPlayer player) { return false; }
```

Every existing spell inherits the default unchanged — this is why no other spell's slot behavior
changes.

**`CrownOfStarsSpell` override**, reusing its own already-public `hasMotes(UUID)` helper — no new
per-spell state, no duplicated bookkeeping:

```java
@Override
public boolean isActiveInstanceAction(ServerPlayer player) { return hasMotes(player.getUUID()); }
```

**`ActivateAbilityHandler.handle`**: a single `chargesSlot` boolean is computed once, *before*
`onActivate` runs (so it reflects the spell's state as of the moment the activation began, not
after `onActivate` may have mutated it — important, since Crown's own `onActivate` flips the mote
counter):

```java
boolean chargesSlot = ability instanceof Spell spell
        && !spell.isCantrip() && !spell.isActiveInstanceAction(player);
```

Both the slot pre-check (previously gated on `!spell.isCantrip()`) and the post-cast spend
(previously gated on `!spell.isCantrip()` again, separately) now gate on this single
`chargesSlot` value instead of re-deriving the condition twice — so the pre-check and the spend can
never disagree with each other.

No `if (spell instanceof CrownOfStarsSpell)` was added anywhere; no change was made to
`PlayerResourceService`, `ClassChangeReconciler`, or any other Generic Resource code; no "don't
charge slots for projectiles" rule was added (the seam is per-activation, on the abstract `Spell`
base, keyed to an explicit spell-declared predicate — not to entity type).

### 1.4 Files changed

- `src/main/java/zcylas/totality/api/magic/spell/Spell.java` — new `isActiveInstanceAction`.
- `src/main/java/zcylas/totality/api/magic/spell/destruction/CrownOfStarsSpell.java` — override.
- `src/main/java/zcylas/totality/networking/ability/ActivateAbilityHandler.java` — single
  `chargesSlot` computed once, reused for both the pre-check and the spend.
- `src/main/java/zcylas/totality/api/rpg/resources/verification/CrownOfStarsActiveInstanceActionVerification.java`
  (new) — dev-server-gated self-test.
- `src/main/java/zcylas/totality/Totality.java` — registers the new verification.

### 1.5 Tests added

`CrownOfStarsActiveInstanceActionVerification` (dev-server, real `ServerPlayer` via
`TotalityFakePlayer`, real `ActivateAbilityHandler.handle` calls, real
`totality:spell_slots` Generic Resource queries — mirrors `StandardSpellSlotMigrationVerification`'s
established pattern exactly). A level-13 Wizard (exactly one 7th-level slot —
`SpellSlotTable.FULL_CASTER` row 13 tier 7 = 1, the precise "last available slot" shape) proves, in
order:

1. Initial cast consumes exactly one 7th-level slot (1 → 0).
2. Initial cast creates the intended 7 motes.
3. Firing a mote does not consume another slot (still 0), including while at the player's last
   available 7th-level slot.
4. Firing consumes exactly one mote (7 → 6).
5. Firing the remaining motes leaves the slot untouched throughout, and firing the 7th (final) mote
   clears the active crown instance entirely.
6. With no active motes and no 7th-level slot available, a genuine `NEW_CAST` attempt is correctly
   **rejected** (no free re-summon, no negative slot state).
7. After restoring the slot (Long Rest), a genuine second `NEW_CAST` still requires and consumes
   another slot, and creates a fresh set of 7 motes.
8. An ordinary level-1 synthetic spell that does **not** override `isActiveInstanceAction` still
   consumes exactly one slot per cast, on two separate casts — proving no other spell's behavior
   changed.

11 checks total, all passing (see §3 below).

### 1.6 Manual tests still needed

The task's 8-step manual test sequence (cast Crown with exactly one 7th-level slot remaining, fire
a mote at 0 remaining, confirm the mote works and the slot stays at 0, confirm the mote count drops
by exactly one) has **not** been performed by a human or a real playable client. This report does
**not** claim manual PASS for Crown of Stars — automated/dev-server coverage above is the only
verification performed.

---

## Issue 2 — Dialogue Ability Check bonus routing

### 2.1 Investigation, verified against current local HEAD (not the old snapshot)

**1. Does Bless currently add +1d4 to a Banker Persuasion check?** Yes, confirmed by direct trace.
`DialogueSessionManager.handleChoice`
(`src/main/java/zcylas/totality/api/dialogue/DialogueSessionManager.java`, pre-fix line 107) built
every dialogue roll's bonus list with:

```java
bonuses.addAll(RollModifierRegistry.resolveSaveBonusList(player, scoreForModifiers));
```

unconditionally, for any dialogue roll with a `DiceRollSpec`. The Banker's dialogue
(`banker_greeting.json`) defines a real Persuasion check (`"skill": "Persuasion", "subtype":
"Charisma Check"`), a genuine Ability Check. `BlessEffect.onEffectAdded`
(`src/main/java/zcylas/totality/effect/BlessEffect.java`) registers a `RollModifier` whose
`getSaveBonusList` unconditionally returns `[DiceBonus("Bless", 1d4)]`. Chain: Banker Persuasion
choice → `DiceRollSpec` → `handleChoice` → `resolveSaveBonusList(player, CHA)` → Bless's modifier →
+1d4 included in the roll's `DiceRollContext.bonuses`.

**2. Does that happen because dialogue Ability Checks reuse `resolveSaveBonusList`?** Yes — that was
the only bonus-list call in `DialogueSessionManager`, with no branch on roll kind.

**3. Are all dialogue Ability Checks receiving Saving-Throw-only effects?** Yes — every dialogue
roll with a `roll` spec (Persuasion, Intimidation, Investigation, confirmed across
`banker_greeting.json`, `banker_intro.json`, `example_trader.json`) went through the same call.

**4. Is there already a dedicated Ability-Check bonus-list mechanism dialogue simply failed to
use?** **No.** `RollModifierRegistry` only had `getAttackBonusList`/`resolveAttackBonusList` and
`getSaveBonusList`/`resolveSaveBonusList` (plus `modifyCheck`/`RollMode`, an unrelated
advantage/disadvantage mechanism). `AbilityCheckResolver.roll`
(`src/main/java/zcylas/totality/api/rpg/check/AbilityCheckResolver.java`) — the *other*
Ability-Check entry point (exploration/skill checks outside dialogue) — does not call any
`RollModifierRegistry` bonus-list method at all. A grep across `src/main/java` confirmed the only
real call sites of the save-bonus methods were `SavingThrow.java` (correct — real saves) and the
dialogue bug; `AttackRoll.java` correctly used `resolveAttackBonusList`. **No mechanism existed for
dialogue to have used — one needed to be added.**

**5. Smallest generic API addition:** mirror the existing `getSaveBonusList`/`resolveSaveBonusList`
pair exactly, as `getCheckBonusList`/`resolveCheckBonusList` — see §2.3.

### 2.2 Intended roll semantics — confirmed, not assumed

No canonical Totality documentation was found stating otherwise. D&D 5e Bless is +1d4 to attack
rolls and saving throws only, never Ability Checks — the exact behavior this fix restores.

### 2.3 Exact fix

**`RollModifierRegistry.RollModifier`** — new default method mirroring
`getSaveBonusList`/`saveBonus` exactly:

```java
default List<DiceBonus> getCheckBonusList(AbilityScore score) { return List.of(); }
default int checkBonus(AbilityScore score) {
    return getCheckBonusList(score).stream().mapToInt(DiceBonus::value).sum();
}
```

**`RollModifierRegistry`** — new static methods mirroring `resolveSaveBonusList`/`resolveSaveBonus`
exactly:

```java
public static List<DiceBonus> resolveCheckBonusList(ServerPlayer player, AbilityScore score) { ... }
public static int resolveCheckBonus(ServerPlayer player, AbilityScore score) { ... }
```

Plus UUID-keyed test-only hooks (`resolveSaveBonusListForTest`, `resolveCheckBonusListForTest`,
`resolveCheckModeForTest`) mirroring the existing `resolveAttackBonusListForTest` precedent.

**`DialogueSessionManager.handleChoice`** — the one-line routing fix:

```java
bonuses.addAll(RollModifierRegistry.resolveCheckBonusList(player, scoreForModifiers));
```

**`BlessEffect` needed no change.** It already only overrides `getAttackBonusList`/
`getSaveBonusList`; the new `getCheckBonusList` default of `List.of()` correctly excludes it from
Ability Checks with zero edits — exactly the intended D&D semantics. Confirmed by grep (Bless does
not mention `getCheckBonusList` anywhere) and by a new source-regression sentinel (§2.4).

**`modifyCheck`/`RollMode` (advantage/disadvantage) is untouched** — a completely separate
mechanism from the numeric bonus lists, resolved via `RollModifierRegistry.resolveCheck`.
`DialogueSessionManager` does not call it at all (a separate, pre-existing gap, not part of this
bug and not touched here).

**Deliberately out of scope:** `AbilityCheckResolver.roll` (the non-dialogue Ability-Check path)
still does not consume any bonus list — this is a pre-existing architectural gap, not something
this bug caused or regressed, and wiring it up was not requested. Noted here for future reference,
not implemented.

### 2.4 Files changed

- `src/main/java/zcylas/totality/api/rpg/combat/RollModifierRegistry.java` — new
  `getCheckBonusList`/`checkBonus` interface members; new `resolveCheckBonusList`/`resolveCheckBonus`
  static methods; new UUID-keyed test-only hooks.
- `src/main/java/zcylas/totality/api/dialogue/DialogueSessionManager.java` — the one-line routing
  fix.
- `src/test/java/zcylas/totality/api/rpg/combat/RollModifierRegistryCheckBonusChannelTest.java`
  (new) — real, executing tests.
- `src/test/java/zcylas/totality/api/dialogue/DialogueSessionManagerCheckBonusRoutingSourceRegressionTest.java`
  (new) — source-regression sentinel.
- `src/test/java/zcylas/totality/effect/BlessLifecycleSourceRegressionTest.java` — one new sentinel
  test.

### 2.5 Tests added

`RollModifierRegistryCheckBonusChannelTest` (7 real, executing tests, UUID-keyed hooks — matching
`RollModifierRegistryLifecycleTest`'s established precedent that a real `ServerPlayer` is
unreachable under plain JUnit):

1. A save-only modifier shaped exactly like Bless's real registered modifier (attack + save
   only) contributes nothing to the check-bonus channel.
2. The same modifier still contributes to save and attack channels.
3. A check-only ("Guidance-shaped") synthetic modifier contributes to the check channel.
4. The same modifier contributes nothing to save or attack channels.
5. A save-only and a check-only modifier registered together each appear on only their own
   channel.
6. A modifier overriding neither list contributes nothing to any of the three channels.
7. `modifyCheck`/`RollMode` resolution (advantage/disadvantage) still works correctly after adding
   the new bonus channel — a synthetic modifier granting `ADVANTAGE` is still resolved correctly.

`DialogueSessionManagerCheckBonusRoutingSourceRegressionTest` (1 test, source-regression sentinel —
`DialogueSessionManager.handleChoice` needs a live `ServerPlayer` and an active dialogue session,
unreachable under plain JUnit): confirms `handleChoice` calls `resolveCheckBonusList` and no longer
calls `resolveSaveBonusList` at all.

`BlessLifecycleSourceRegressionTest` (+1 test): confirms `BlessEffect` does not override
`getCheckBonusList`.

### 2.6 Manual tests still needed

The task's 5-step manual test (apply Bless, talk to Banker, choose Persuasion, confirm the roll
breakdown does not show Bless, confirm Bless still appears on attack/save rolls) has **not** been
performed by a human or a real playable client. This report does **not** claim manual PASS.

---

## 3. Validation (both issues together)

| Check | Result |
|---|---|
| Focused tests (Crown: dev-server only, no JUnit unit tests exist for a static-map spell like this — see §1.5; Ability Checks: `RollModifierRegistryCheckBonusChannelTest`, `DialogueSessionManagerCheckBonusRoutingSourceRegressionTest`, `BlessLifecycleSourceRegressionTest`, `RollModifierRegistryLifecycleTest`) | **PASS** |
| `./gradlew test` (full suite) | **PASS** — 1620 tests, 0 failures, 0 errors (was 1611 before this task — +9: 7 `RollModifierRegistryCheckBonusChannelTest` + 1 `DialogueSessionManagerCheckBonusRoutingSourceRegressionTest` + 1 `BlessLifecycleSourceRegressionTest` addition) |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` | **PASS** — no real whitespace errors (only benign CRLF-normalization warnings) |
| Bounded dedicated-server run (`./gradlew runServer`) | **PASS** — `[ResourceFoundationVerification] All 6`, `[BaselineResourceMigrationVerification] All 11`, `[BarbarianRageMigrationVerification] All 18`, `[StandardSpellSlotMigrationVerification] All 23` (all unchanged — confirms neither fix disturbed the Generic Resource API), **`[CrownOfStarsActiveInstanceActionVerification] All 11 self-test checks passed.`** (new). Known unrelated pre-existing failures unchanged: `ProvisionerEntityBackedSmokeTest` 3/4 FAIL, `OffhandAttackVerification` 3/5 FAIL — not touched by this task. |

## 4. Confirmations

- The 109 unrelated pre-existing dirty paths were preserved exactly (verified before and after
  editing — see the review bundle's baseline/changed-files notes).
- Phase 7 (Pact Magic, Ki, Hit Dice, Epic Magic) was not started.
- Neither fix altered `PlayerResourceService`, `ClassChangeReconciler`, `MaximumChangePolicy`, or any
  other Generic Resource API behavior — both fixes call existing public APIs exactly as every other
  caller already does.
- The two fixes are structurally independent: no file is required by both (Crown of Stars touches
  `Spell`/`CrownOfStarsSpell`/`ActivateAbilityHandler`/its own new verification/`Totality.java`'s
  registration line; the Ability Check fix touches `RollModifierRegistry`/`DialogueSessionManager`/
  its own new tests) — no partial/selective hunk staging was required for either commit.
