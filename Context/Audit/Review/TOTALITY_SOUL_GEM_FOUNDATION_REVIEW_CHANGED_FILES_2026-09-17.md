# Soul Gem Foundation — Changed Files

> **Updated 2026-09-17 (review-correction pass).** This list now reflects the foundation pass PLUS
> the small correction pass (stacked-vessel capture fix, Common rarity fix, MobRank
> provisional-color documentation). No new files were introduced by the correction pass — it only
> edited files already listed below. See `TOTALITY_SOUL_GEM_FOUNDATION_REVIEW_SUMMARY_2026-09-17.md` for exactly what changed in each.

## New (Soul Gem API / item)

- `src/main/java/zcylas/totality/api/soulgem/SoulCategory.java`
- `src/main/java/zcylas/totality/api/soulgem/CapturedSoul.java`
- `src/main/java/zcylas/totality/api/soulgem/CapturedSoulComponent.java`
- `src/main/java/zcylas/totality/api/soulgem/SoulGemAcceptanceRule.java`
- `src/main/java/zcylas/totality/api/soulgem/SoulCaptureEligibility.java`
- `src/main/java/zcylas/totality/api/soulgem/SoulCaptureResult.java`
- `src/main/java/zcylas/totality/api/soulgem/SoulCaptureService.java`
- `src/main/java/zcylas/totality/api/soulgem/verification/SoulGemSystemVerification.java`
- `src/main/java/zcylas/totality/item/soulgem/SoulGemItem.java`

## New (tests)

- `src/test/java/zcylas/totality/api/mob/stats/MobRankTest.java`
- `src/test/java/zcylas/totality/api/mob/stats/MobRankNetworkSourceRegressionTest.java`
- `src/test/java/zcylas/totality/api/soulgem/SoulCategoryTest.java`
- `src/test/java/zcylas/totality/api/soulgem/CapturedSoulTest.java`
- `src/test/java/zcylas/totality/api/soulgem/SoulGemAcceptanceRuleTest.java`
- `src/test/java/zcylas/totality/api/soulgem/SoulCaptureEligibilityTest.java`
- `src/test/java/zcylas/totality/api/soulgem/SoulGemArchitectureSourceRegressionTest.java`

## New (datagen output, untracked before this task's `runDatagen`)

- `src/main/generated/assets/totality/items/common_soul_gem.json`
- `src/main/generated/assets/totality/items/petty_soul_gem.json` (Petty's own generated output — was
  already untracked pre-existing content from the 2026-09-15 Petty session, unmodified by this task;
  included here only because it lives alongside Common's, for reviewer convenience)

## New (user's pre-existing assets, untouched — included per task instruction to bundle current-delta assets)

- `src/main/resources/assets/totality/models/item/common_soul_gem.json`
- `src/main/resources/assets/totality/textures/item/common_soul_gem.png`
- `src/main/resources/assets/totality/models/item/petty_soul_gem.json`
- `src/main/resources/assets/totality/textures/item/petty_soul_gem.png`

## New (documentation)

- `Context/Audit/TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md`
- `Context/Audit/Review/TOTALITY_SOUL_GEM_FOUNDATION_REVIEW_BASELINE_2026-09-17.md`

## Edited — clean, entirely Soul-Gem-attributable diff

- `src/main/java/zcylas/totality/api/mob/stats/MobRank.java`
- `src/main/java/zcylas/totality/api/mob/stats/MobStatBlock.java`
- `src/main/java/zcylas/totality/api/mob/stats/MobCombatStats.java`
- `src/main/java/zcylas/totality/client/renderer/hud/MobHealthBarHud.java`
- `src/test/java/zcylas/totality/client/renderer/hud/TotalityHudCleanupSourceRegressionTest.java`
- `src/main/java/zcylas/totality/Totality.java`

## Edited — MIXED content (this task's diff is a small fraction; see TOTALITY_SOUL_GEM_FOUNDATION_REVIEW_SUMMARY_2026-09-17.md for the exact
## Soul-Gem-attributable hunk/lines in each; full file diffs are included in `REVIEW_GIT_DIFF.patch` (a temporary review artifact, not retained in the repository)
## per the task's instruction for mixed files)

- `src/main/java/zcylas/totality/init/items/MagicItems.java`
- `src/main/java/zcylas/totality/datagen/ModModelProvider.java`
- `src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java`
- `src/main/java/zcylas/totality/init/ModGroups.java`
- `src/main/generated/assets/totality/lang/en_us.json`

## Explicitly NOT included

- No Soul Gem, Iron Sword, or any other unrelated content beyond what's listed above.
- None of the other ~109 pre-existing unrelated dirty paths from the Food-task baseline.
- No build/cache/runtime/world/log files.
