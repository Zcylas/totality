# Soul Gem Foundation — Review Summary

Full detail: `Context/Audit/TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md`.

## What this bundle is

The first small Soul Gem foundation: two registered vessels (Petty converted in place, Common newly
added), a minimal persistent captured-soul data model (`CapturedSoul` + `CapturedSoulComponent`), an
authored acceptance-rule architecture (`SoulGemAcceptanceRule`, `SoulCaptureEligibility`,
`SoulCaptureService`/`SoulCaptureResult`), and a MobRank safety fix (added F/Z/ZERO ranks, replaced
two ordinal-dependent network encode/decode sites with an explicit authored-order surface). No commit
was made — this is uncommitted working-tree state on `feature/soul-gems`, reviewed here only.

**Updated 2026-09-17 (review-correction pass)** — three small corrections found in review, layered
on top of the same uncommitted foundation, still no commit:

1. **Stacked-vessel capture bug (real correctness fix).** `SoulCaptureService.attemptCapture` would
   have written one `CapturedSoul` onto every physical gem in a `count > 1` stack. Fixed with a new
   `SoulCaptureResult.STACKED_VESSEL_REQUIRES_SPLIT`, checked after `ALREADY_FILLED` (deliberately
   preserved in its original first position) and before the soul-vs-rule checks. No auto-splitting,
   no inventory search, no second `ItemStack` — still a one-vessel-only operator.
2. **Common Soul Gem rarity correction.** `UNCOMMON` (wrongly inferred from the Grimoire tier
   pattern) → `COMMON` (same as Petty). Soul Gem vessel tier is now explicitly documented as a
   separate concept from `ItemRarity` — one must never be inferred from the other.
3. **MobRank colors documented as provisional.** No color values changed; `F`/`Z`/`ZERO`'s hex
   literals are now explicitly marked as placeholders, not a finished, canonical color design. Rank
   *order* remains fully canonical.

Full detail: implementation report §24.

## Exact Soul-Gem-attributable lines in each MIXED file

These files already carried substantial unrelated, pre-existing uncommitted content before this task
started (documented in `TOTALITY_SOUL_GEM_FOUNDATION_REVIEW_BASELINE_2026-09-17.md`). `REVIEW_GIT_DIFF.patch` (a temporary review artifact, not retained in the repository) included their full diffs per
the task's instruction for mixed files; here is exactly which part is this task's:

- **`MagicItems.java`** (37 insertions total in the diff, was 33 before the correction pass): the
  entire diff is this task's — the Petty registration's `Item::new` →
  `new SoulGemItem(properties, SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.F))`
  conversion, the new `COMMON_SOUL_GEM` registration block (rarity `COMMON`, corrected from the
  original pass's `UNCOMMON` — see the correction-pass note below), the explanatory comment above it,
  and the 4 new imports (`MobRank`, `SoulCategory`, `SoulGemAcceptanceRule`, `SoulGemItem`). Nothing
  unrelated changed in this particular diff.
- **`ModModelProvider.java`** (16 insertions): the entire diff is this task's — one new
  `generators.itemModelOutput.accept(MagicItems.COMMON_SOUL_GEM, ...)` block (5 lines) plus its
  comment; no unrelated hunk appears in this diff (the file's other unrelated dirty content, from
  earlier sessions' Iron Sword/other model wiring, was already present before this task and is
  unchanged by it, so it does not appear as a diff line here).
- **`ModEnglishLangProvider.java`** (39 insertions): only 1 of those lines is this task's —
  `translationBuilder.add("item.totality.common_soul_gem", "Common Soul Gem");`, inserted directly
  after the existing Petty lang line. The remaining ~38 insertions in this diff are unrelated
  pre-existing dirty content from earlier sessions (other item/effect/entity lang entries) that was
  already uncommitted before this task touched the file.
- **`ModGroups.java`** (274 insertions): only 1 of those lines is this task's —
  `output.accept(MagicItems.COMMON_SOUL_GEM);`, inserted directly after the existing Petty line in
  the Magic tab. The remaining ~273 insertions are unrelated pre-existing dirty content (the file's
  full 5-tab working-tree state, most of it from other in-progress sessions).
- **`src/main/generated/assets/totality/lang/en_us.json`** (38 insertions total, datagen output):
  only the `"item.totality.common_soul_gem": "Common Soul Gem"` line is this task's; the rest was
  already regenerated/dirty from the Food task and other prior sessions' source lang changes.

## Clean, entirely-attributable edits

`MobRank.java` (now 89 insertions, was 82 — the correction pass added the provisional-color
Javadoc/inline comments, no color values changed), `MobStatBlock.java`, `MobCombatStats.java`,
`MobHealthBarHud.java`, `TotalityHudCleanupSourceRegressionTest.java`, `Totality.java` — every line
in each of these diffs is this task's own change (no unrelated content was present in these specific
files before this task).

## Correction-pass-only files (untracked, no unrelated content ever mixed in)

`SoulCaptureResult.java` (+1 enum constant), `SoulCaptureService.java` (+1 stacked-vessel check,
expanded Javadoc documenting the exact check order), `SoulGemSystemVerification.java` (12→16 checks)
— all three were already new/untracked files from the original foundation pass; the correction pass
only edited their content, introducing no new files.

## Validation

1717 tests / 0 failures (unchanged — the stacked-vessel fix's new coverage lives in the dev-server
verification, since it needs a real `ItemStack.getCount()`/component mutation plain JUnit cannot
construct here), clean `./gradlew clean build`, clean `git diff --check` (only benign pre-existing
LF→CRLF notices), and a real dev-server run showed
`[SoulGemSystemVerification] All 16 self-test checks passed.` (was 12; +4 for the stacked-vessel fix)
alongside the pre-existing `[FoodSystemVerification] All 42 self-test checks passed.` (unaffected).
Two known, pre-existing, unrelated failures remain (`ProvisionerEntityBackedSmokeTest`,
`OffhandAttackVerification`) — neither touched nor caused by either pass.

## Explicitly out of scope (see implementation report §21-23)

Full gem ladder (Lesser/Greater/Grand), Black Soul Gems/categories, Soul Trap, mob-death capture
integration, soul spending/enchanting, Mob API/level/CR redesign.
