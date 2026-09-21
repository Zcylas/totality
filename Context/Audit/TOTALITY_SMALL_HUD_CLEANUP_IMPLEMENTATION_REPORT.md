# Totality Small HUD Cleanup — Implementation Report

**Date:** 2026-07-31
**Branch:** `feature/general-resource-api`
**Scope:** contained player-HUD bar geometry/embedded-text pass + temporary Mob Display direct-look HP visibility

---

## 1. Task purpose

Slightly enlarge the four main player bars (Health, Mana, Stamina, Food), move their current/max values from external text to centered-inside-the-bar text, and temporarily make direct crosshair-targeting of any valid non-player `LivingEntity` show its HP bar (not just combat targets) — a single contained presentation pass, explicitly not the full HUD redesign, not Food 0-100, not the Mob Health/Mob HUD API, and not the Contextual Interaction API expansion.

## 2. Starting branch

`feature/general-resource-api`

## 3. Starting HEAD and subject

HEAD `69717c6be85b891c6dea0fe7b0751cab4da73b3c`, subject `Migrate client resource presentation consumers`.

## 4. Local/remote synchronization

`git fetch origin feature/general-resource-api` confirmed `origin/feature/general-resource-api` at the identical commit — local and origin were synchronized before this task began.

## 5. Starting repository-wide status

Identical to the previously-documented baseline: `build.gradle` + 22 modified generated JSON files, 27 pre-existing untracked review bundles (including the just-closed Phase 3C bundle and the HUD audit bundle), the HUD audit report, Tooltip/trade-screen screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`. No HUD production file was modified; no unexplained HUD or Resource-related drift was present.

## 6. Starting task-scoped status

`git status --short` for `TotalityHudRenderer.java`, `MobHealthBarHud.java`, existing HUD tests, and `Phase3CConsumerMigrationSourceRegressionTest.java` was empty before any edit — confirmed clean.

## 7. Audit documents read

- `Context/Audit/TOTALITY_HUD_AND_MOB_DISPLAY_AUDIT.md` (read in full before editing).
- This task's own prompt explicitly narrows/supersedes several audit recommendations with concrete, user-approved geometry — treated as authoritative per the task's own instruction.

## 8. User-approved geometry

Frame 96×10, fill 84×5, fill offset 10×2, edge margin 6, vertical gap between left bars 2, bottom margin 2. Left side: Health, Mana, Stamina; right side: Food (aligned with Health); Rage pips remain beneath Food. Approved absolute Y positions for screen height `H`: Stamina `H-12`, Mana `H-24`, Health/Food `H-36`, Rage `H-24` — all reproduced exactly by the shared formulas implemented in `HudBarLayout` (§11, §14).

## 9. Current HUD architecture preserved

Per the audit's inventory: `TotalityHudRenderer`'s single `HudElementRegistry` layer, the vanilla `HEALTH_BAR`/`ARMOR_BAR`/`FOOD_BAR` no-op replacement (unchanged — still only those three), `MobHealthBarHud`'s separate layer + tick registration, the Rage `ISecondaryResource` data/registration in `TotalityClient` (untouched this pass), `AbilityContextHud`/`MagicContextHud` (untouched), `NotificationManager`/`CombatTextRenderer`/`RestHud`/`QuestTrackerHud`/`PowerAttackFlash`/`CastBarHud` (untouched), and the dormant `SecondaryResourceHud`/`drawBar`/`drawBarMirrored`/`MobHealthBarHud.getRank`/`COLOR_BOSS` (still present, still dead, not removed).

## 10. Exact main-bar changes

`TotalityHudRenderer`'s active render path (`register()`'s lambda, `drawBarSmooth`, `drawBarMirroredSmooth`) now:
- Computes `leftX`/`staminaY`/`manaY`/`hpY`/`rightX`/the secondary-resource row's Y via new `HudBarLayout` static formulas instead of local hardcoded arithmetic.
- Draws the frame and fill sprites at the new `96×10`/`84×5` destination size (texture-reference size passed to `blitSprite` matches the destination 1:1, exactly mirroring the pre-cleanup code's own convention — the underlying PNG assets were **not** re-exported or edited; see §11).
- Draws the current/max text centered inside the fill lane (via `HudBarLayout.textX`/`textY`) instead of external to the frame.
- Keeps stamina/mana as `long` end-to-end from `ClientResourcePresentationResolver.ScalarPresentation` through to the draw call, removing the intermediate `(int)` cast that existed pre-cleanup.
- The pre-cleanup `formatValue`/`buildText`/`drawBar`/`drawBarMirrored` methods and their `BG_WIDTH=83`/`BG_HEIGHT=8`/`DRAW_FILL_W=73`/`DRAW_FILL_H=4`/`FILL_OFFSET_X=9`/`FILL_OFFSET_Y=2` constants are **left completely untouched** — they were already confirmed dead code by the audit (never called), and the task's "do not remove dormant code" instruction was applied to them; they now exist purely as an unused, isolated dead-code island that still compiles.
- `BAR_SPACING`/`BOTTOM_MARGIN` (the two old constants) are no longer referenced by any active code (the active path now uses `HudBarLayout.VERTICAL_GAP`/`BOTTOM_MARGIN`) — left in place, unused, rather than deleted, for the same dormant-code reason.

## 11. Exact geometry constants

New file `src/main/java/zcylas/totality/client/renderer/hud/HudBarLayout.java` (package-private, pure, no Minecraft dependency):
```
FRAME_WIDTH = 96      FRAME_HEIGHT = 10
FILL_WIDTH  = 84      FILL_HEIGHT  = 5
FILL_OFFSET_X = 10    FILL_OFFSET_Y = 2
EDGE_MARGIN = 6       VERTICAL_GAP = 2      BOTTOM_MARGIN = 2
```
Shared Y formulas: `staminaY(H) = H - BOTTOM_MARGIN - FRAME_HEIGHT`; `manaY(H) = staminaY(H) - VERTICAL_GAP - FRAME_HEIGHT`; `healthY(H) = manaY(H) - VERTICAL_GAP - FRAME_HEIGHT`; `foodY(H) = healthY(H)`; `secondaryResourceY(H) = foodY(H) + FRAME_HEIGHT + VERTICAL_GAP`. Reproduces the approved `H-12/H-24/H-36/H-36/H-24` values exactly for any `H` (verified by `HudBarLayoutTest.approvedAbsoluteYPositionsMatchTheAuthoredFormulasForAnyScreenHeight` across four sample heights). `leftX() = EDGE_MARGIN`; `rightX(W) = W - FRAME_WIDTH - EDGE_MARGIN`.

The underlying sprite PNG files (`bar_background.png` 166×16, `*_filled.png` 144×8, all measured native resolution per the audit) were **not modified or re-exported** — only the Java-side destination/texture-reference size passed to `blitSprite` changed, exactly the same pattern the pre-cleanup code already used (texture-reference size always equal to destination size, letting Minecraft's sprite system resample the real PNG to fit).

## 12. Text-placement method

`HudBarLayout.textX(barX, textWidth) = barX + FILL_OFFSET_X + (FILL_WIDTH - textWidth) / 2` and `textY(barY, fontLineHeight) = barY + (FRAME_HEIGHT - fontLineHeight) / 2` — exactly the integer-rounding formula the task specified, using `client.font.width(text)` for `textWidth` and `client.font.lineHeight` for `fontLineHeight`. Text color is `0xFFCCCCCC` (identical to the pre-cleanup external text color) with the existing drop-shadow (`true` argument to `graphics.text(...)`) preserved. No backing panel was added. No font scaling is applied anywhere in the active draw path (confirmed by `TotalityHudCleanupSourceRegressionTest.activeBarDrawMethodsDoNotScaleTheFont`, an absence-of-`.scale(`-call sentinel).

## 13. Long-safe formatting behavior

New file `src/main/java/zcylas/totality/client/renderer/hud/HudValueFormatter.java` (package-private, pure): `abbreviate(long value)` throws on negative input, returns plain digits below 1000, and abbreviates to one decimal (trimmed when `.0`) followed by `k`/`M`/`B` at the 1,000 / 1,000,000 / 1,000,000,000 thresholds — verified: `1000→"1k"`, `1500→"1.5k"`, `10000→"10k"`, `1,200,000→"1.2M"`, `3,000,000→"3M"`, `2,000,000,000→"2B"`, `4,000,000,000→"4B"` (the last two exceed `Integer.MAX_VALUE`, confirming no int-narrowing anywhere in the path — `HudValueFormatterTest.longValuesAreNotNarrowedToInt` asserts this explicitly with a >2.1-billion value). `display(current, max, maxWidth, widthFn)` takes a `ToIntFunction<String>` for width measurement (in production, `client.font::width`) rather than requiring a live `Font`, keeping the formatter's core decision logic directly unit-testable without a bootstrapped client.

## 14. Compact fallback behavior

`display(...)` always builds the spaced `"cur / max"` form first; if `widthFn` measures it within `maxWidth` (in production, `HudBarLayout.FILL_WIDTH = 84`), that form is used. Only when the spaced form doesn't fit does it fall back to the compact `"cur/max"` form (spaces removed, digits never truncated). Verified deterministic via `HudValueFormatterTest.compactSlashFormIsUsedOnlyWhenMeasuredWidthRequiresIt` (same value, two different `maxWidth`s, two different results) and confirmed the realistic tested values (`100/100`, `110/110`, `999/999`, `78/110`, `1500/2000`) all fit the approved 84px lane under a conservative 6px/char measuring function (`HudValueFormatterTest.textBoundsRemainWithinTheApprovedFillLaneForTestedValues`).

## 15. Health authority preservation

Unchanged. `TotalityHudRenderer` still reads `client.player.getHealth()`/`getMaxHealth()` directly for the fill ratio, and still resolves the displayed number through `PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.HEALTH)` + `RpgDisplayUtils.toDisplayHp` fallback — the exact pre-cleanup pattern, byte-identical except that the resulting `long[]` is now passed straight into the draw call without an intermediate `(int)` cast.

## 16. Mana Phase 3C preservation

Unchanged. Still `ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.MANA, ...)` with the legacy `ClientManaManager` supplier as fallback. `ClientResourcePresentationResolver.java` itself was not touched by this task (confirmed by `git status`), so FRESH/PENDING_RESYNC preference and the no-fabricated-zero policy are provably unchanged.

## 17. Stamina Phase 3C preservation

Unchanged, same pattern as Mana with `PlayerResourceIds.STAMINA`/`ClientStaminaManager`.

## 18. Food native-authority preservation

Unchanged. `hungerPct = hunger / 20.0` (native 0-20 ratio) is untouched; the displayed number still goes through `PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.FOOD)` + the `ResourceDisplayConversion.HEALTH_FOOD` (×5) fallback conversion, exactly as before. No Food 0-100 work was started.

## 19. Rage Phase 3C preservation

Unchanged — `TotalityClient.java` (where the Rage `ISecondaryResource` and its `ClientResourcePresentationResolver.resolveScalar(PlayerResourceIds.RAGE, ...)` call live) was not touched by this task at all (confirmed by `git status`). The pip drawing code (`drawSecondaryResources`) keeps its own `pipSz=10`/`pipGap=2` constants unchanged; only its row's Y position now derives from `HudBarLayout.secondaryResourceY(screenH)` (which follows Food's new geometry, per the approved design) instead of the old `hpY + BG_HEIGHT + BAR_SPACING` formula. Barbarian-only visibility (`shouldShow`) is untouched since `TotalityClient.java` was not edited.

## 20. AC behavior

Unchanged formula (`leftX`, `hpY - client.font.lineHeight - 2`, same color, same "AC ##" text). Its position naturally moves up along with the taller Health bar's new `hpY` — no code change was needed beyond `hpY` itself now coming from `HudBarLayout.healthY(screenH)`.

## 21. XP/hotbar/offhand preservation

Unchanged. Vanilla's `EXPERIENCE_LEVEL` HUD element was never touched (still not in the three-element `replaceElement` list). The vanilla `HOTBAR` element (which also renders the offhand slot) was never touched. No Main-Hand-preference-dependent code was added anywhere — the fix is purely "recover horizontal space by moving text inside the frame," which is agnostic to which side the offhand renders on.

## 22. Chat unchanged decision

No chat-related file was read, referenced, or modified. No chat-aware movement, fading, stacking, or reservation logic was added anywhere in this pass.

## 23. Context interaction unchanged decision

`AbilityContextHud.java` was not modified. The hardcoded `"Z"` label (identified as a defect by the prior HUD audit) remains exactly as-is — not touched in this pass, per the task's explicit instruction that it "remains a separately documented future correctness task."

## 24. Temporary direct-look Mob HP decision

`MobHealthBarHud.render(...)`'s `showHealthBar` boolean (renamed from `showBars`) is now hardcoded `true` rather than `inCombat || perception >= 1`. Since `render(...)` is only ever invoked with a non-null `target` that already passed `getDisplayTarget()`'s existing crosshair/combat validity rules (must be a `LivingEntity`, must not be a `Player` via the normal crosshair path, must be within the existing 24-block range, must be alive for the combat-target branch), "we are rendering at all" already means "a valid direct-look or combat target exists" — so making `showHealthBar` unconditional is sufficient to satisfy "directly looking at a valid non-player LivingEntity shows its HP bar," with no change to target-acquisition logic itself. No development flag or configuration toggle was added, per the task's explicit instruction. This is documented in-source as temporary, pending the dedicated Mob Health/Mob HUD API (see the comment block directly above the `showHealthBar` declaration).

## 25. Neutral versus combat Mob Display separation

`showHealthBar` (HP-bar visibility) is now fully decoupled from every combat-information gate, which are all **left with their exact pre-cleanup formulas**: `showNameColor = inCombat || perception >= 2` (still gates the threat-colored name — a neutral direct-look target keeps the neutral `COLOR_UNKNOWN` grey name), `showRank = inCombat` (rank suffix stays combat-only), `showAc = inCombat` (the "AC ##" line stays combat-only), `showExtraBars = inCombat && perception >= 3` (still unreachable/unimplemented, unchanged). The rarity-prefix logic inside `buildName` was already unconditional (not gated by combat state) before this task and remains exactly as unconditional after it — this task did not touch that behavior in either direction. `getPerceptionMasteryLevel()` is still hardcoded to return `0` — this change must not be read as Perception mastery being implemented, and the in-source comment says so explicitly.

## 26. Mob Display responsibilities deliberately unchanged

Per the task's explicit "do not change" list, none of the following were touched: `MobStatsSyncPayload`, `MobStatsClientCache` lifecycle (still read-only `get(...)`, no `update`/`clear` call added), entity tracking, `MobRank` enum, `SpawnRarity`, threat-color calculations, the Perception stub, boss handling, companion handling, long-name sizing, panel positioning/dimensions/artwork, `SmoothValue` HP interpolation, `COMBAT_DISPLAY_TICKS = 80` combat-retention duration, line-of-sight behavior (still whatever `mc.crosshairPickEntity` itself provides — no new LOS check was added or removed), target range (still 24 blocks), or `NotificationManager` behavior.

## 27. Deferred hardcoded-Z issue

Not touched this task — remains exactly the defect the HUD audit documented, still pending a dedicated future correctness fix in `AbilityContextHud`.

## 28. Deferred AC correctness issue

Not touched — no client/server AC-divergence fix, no shield background, no combat-contextual AC behavior was added. `calculateClientAC` is byte-identical to before this task.

## 29. Deferred MobStats cache lifecycle issue

Not touched — `MobStatsClientCache` is read via the same single `get(entityId)` call as before; no lifecycle/eviction/clearing behavior was added or changed.

## 30. Deferred long-name sizing issue

Not touched — `getPanelW`'s name-width-based clamping formula (`nameW = mc.font.width(...) + 24`, clamped `80-320` depending on state) is unchanged.

## 31. Deferred contextual/cast overlap issue

Not touched — no coordination was added between `AbilityContextHud`, `MagicContextHud`, `CastBarHud`, or the Mob Display; none of those files were modified.

## 32. Deferred full HUD design

Not started — no new art, no animation beyond the existing `SmoothValue` lerp, no general layout/reservation framework (none was needed — the geometry is a small, fixed set of shared formulas, not a dynamic engine), no hotbar-relative repositioning, no Thirst/Temperature/Sanity/Fatigue/Cleanliness/Exhaustion presentation.

## 33. Exact production files changed

- `src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java` (modified)
- `src/main/java/zcylas/totality/client/renderer/hud/MobHealthBarHud.java` (modified)
- `src/main/java/zcylas/totality/client/renderer/hud/HudBarLayout.java` (**new**)
- `src/main/java/zcylas/totality/client/renderer/hud/HudValueFormatter.java` (**new**)

## 34. Exact test files changed or added

- `src/test/java/zcylas/totality/client/renderer/hud/HudBarLayoutTest.java` (**new**, 15 tests)
- `src/test/java/zcylas/totality/client/renderer/hud/HudValueFormatterTest.java` (**new**, 13 tests)
- `src/test/java/zcylas/totality/client/renderer/hud/TotalityHudCleanupSourceRegressionTest.java` (**new**, 20 tests)
- `src/test/java/zcylas/totality/api/rpg/resources/client/presentation/Phase3CConsumerMigrationSourceRegressionTest.java` (modified — one stale 83×8 sentinel replaced with an active-geometry sentinel plus a new sentinel confirming the old constants survive only as dead-code references; every other Phase 3C assertion left untouched; net 18→19 tests)

## 35. Focused test command and exact result

```
./gradlew test --tests "zcylas.totality.client.renderer.hud.*"
./gradlew test --tests "zcylas.totality.api.rpg.resources.client.presentation.Phase3CConsumerMigrationSourceRegressionTest"
```
**HudBarLayoutTest: 15/0/0/0. HudValueFormatterTest: 13/0/0/0. TotalityHudCleanupSourceRegressionTest: 20/0/0/0. Phase3CConsumerMigrationSourceRegressionTest: 19/0/0/0.** Combined focused total: **67 tests, 0 failures, 0 errors, 0 skipped.**

## 36. Full-suite command and exact result

```
./gradlew test
```
**Tests: 1254, Failures: 0, Errors: 0, Skipped: 0**, across 96 test classes (up from the pre-task baseline of 1205/93 — exactly +49 tests, +3 test classes, matching §35's new/updated test counts).

## 37. Datagen result

```
./gradlew runDatagen
```
Log: `Caching: total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0`. `git status --short` captured immediately before and after this run is byte-for-byte identical — zero task-unrelated files written.

## 38. Clean-build result

```
./gradlew clean build
```
**BUILD SUCCESSFUL.** The `:test` task inside this run reproduced the identical **1254/0/0/0** totals from §36.

## 39. `git diff --check` result

Exit code `0`. Output was only pre-existing/expected CRLF-vs-LF advisory warnings (the 22 already-dirty generated JSON files, plus `MobHealthBarHud.java` and the updated `Phase3CConsumerMigrationSourceRegressionTest.java` — both edited via a line-ending-preserving tool, advisory only) — no actual whitespace error anywhere.

## 40. Dedicated-server result where applicable

Run because this task introduced new client-only helper classes (`HudBarLayout`, `HudValueFormatter`). `./gradlew runServer` (bounded ~100s run) reached `Done (0.374s)! For help, type "help"` with **no `ClassNotFoundException`/`NoClassDefFoundError`**. The same three pre-existing, unrelated self-test failures (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) were observed — explicitly out of scope per this task's own "do not modify unrelated systems to address stale development-world failures" instruction, unrelated to the Resource API or HUD.

## 41. Manual validation completed

- `./gradlew runClient` (bounded ~60s run): the client launched, **auto-joined the existing "New Testing World" singleplayer dev save**, rendered the world for roughly 15 real seconds (chunk loading, multiple GPU buffer resizes as real geometry streamed in — direct evidence the new HUD render path executed live, since `TotalityHudRenderer`'s `HudElementRegistry` layer runs every frame while a world is rendering and `client.player`/`client.gui.hud.isHidden()` are both satisfied in that state), then saved and shut down cleanly with **no exception or crash** traceable to any file this task touched (the only exception in the log was an expected, pre-existing, unrelated Mojang authentication 401 in an offline dev environment).
- Full/focused automated test suites (§35/§36).
- Datagen and clean-build checks (§37/§38).
- The dedicated-server classloading check (§40).

## 42. Manual validation remaining for the user

**Not performed by this agent** — this environment has no way to send keyboard/mouse input into the running Minecraft window or capture what it visually renders, so the following from the task's 23-item manual-validation list still need a human pass:
1. Confirm Health/Mana/Stamina/Food are visibly slightly larger.
2. Confirm all four current/max values are centered inside the frames.
3. Confirm `110 / 110` fits clearly.
4. Confirm text stays readable at empty/partial/full fill.
5. Confirm values no longer crowd the vanilla hotbar/offhand area.
6/7. Confirm both right-handed and left-handed offhand remain unobstructed.
8. Confirm AC remains readable.
9. Confirm Rage pips remain readable and correctly positioned.
10. Confirm the Mana-hidden state doesn't break spacing.
11/12. Confirm chat is unchanged and chat-open behavior isn't worsened.
13. Confirm vanilla XP is unchanged.
14. Confirm F1 still hides the HUD normally.
15. Confirm Resource values continue updating.
16. Confirm a dimension transition doesn't introduce a fabricated 0/0.
17-19. Confirm direct-look-at-a-neutral-mob shows its HP bar with a neutral name and no *new* Rank/AC/Level disclosure — and no *new* rarity disclosure specifically (see the review-correction addendum, §55, for the precise distinction: the pre-existing Rare+ name prefix was never gated by combat state to begin with, so it is unaffected either way, not something direct-look newly "exposes").
20-22. Confirm combat still activates threat-colored presentation, recently-hit targets retain display for the existing duration, and smoothing remains.
23. Confirm no notification/cast-bar/context-prompt/boss-bar behavior changed.

Testing specifically at GUI Scale 4 (the authoritative stress case) and at least one smaller GUI scale, per the task's instruction.

## 43. Screenshots produced, if any

None. No screenshot capture is available in this environment (no input injection into the running client, no in-game screenshot key can be triggered programmatically here). None were staged or committed — none exist to stage.

## 44. Known limitations

- Visual/pixel-level correctness of the new geometry (centering, readability against fill colors at various fill levels, exact offhand clearance at GUI Scale 4) is unverified by this agent — see §42.
- The dormant pre-cleanup constants/methods (`BG_WIDTH`, `drawBar`, `drawBarMirrored`, `formatValue`, `buildText`, `SecondaryResourceHud`, `MobHealthBarHud.getRank`, `COLOR_BOSS`) remain in source, unused, per the task's explicit "do not remove dormant code" instruction — a future cleanup pass may want to remove them once nobody needs a pre-cleanup reference point.
- `MobHealthBarHud`'s compact (name-only, non-combat) panel width branch (`minW=80,maxW=160`) is now unreachable in production (since `showHealthBar` is unconditionally `true`), analogous to how `showExtraBars`'s branch was already unreachable before this task — this is the intended, documented, temporary effect of the direct-look change, not a new defect.

## 45. Confirmation that Food 0-100 was not started

Confirmed. `hungerPct = hunger / 20.0` (native 0-20 scale) is unchanged; no `FoodResourceAdapter`, `ResourceDisplayConversion`, or Food-scale file was touched.

## 46. Confirmation that Thirst/Temperature/Sanity/Fatigue/Cleanliness/Exhaustion HUD work was not started

Confirmed. The `// TODO: Thirst aligned with Stamina` / `// TODO: Temperature aligned with Mana` comments in `TotalityHudRenderer.java` are unchanged (still TODOs, not implemented). No Sanity/Fatigue/Cleanliness/Exhaustion file was read, referenced, or created.

## 47. Confirmation that Contextual Interaction expansion was not started

Confirmed. `AbilityContextHud.java` was not modified. No hold-behavior, action-cycling, multiple-choice, QuickLoot, corpse/chest inventory, trap, or Detect Magic code was added anywhere.

## 48. Confirmation that Mob Health/Mob HUD API work was not started

Confirmed. No new package, resource type, or API surface for mob health was introduced — `MobHealthBarHud`'s existing architecture, data source (`MobStatsClientCache`), and panel rendering are unchanged beyond the single `showHealthBar` boolean described in §24.

## 49. Confirmation that no full HUD redesign was started

Confirmed. No new textures, no new art, no animation beyond the pre-existing `SmoothValue` lerp, no general layout/reservation framework, and every element the task listed as "leave unchanged" (Notifications, floating combat text, Power Attack flash, offhand attack-readiness indicator, Rest HUD, quest tracker, cast bar, Grimoire context, status effects, boss bars, crosshair, item-name popup, subtitles, air bubbles, vehicle health, dormant `SecondaryResourceHud`) has zero diff against the pre-task baseline (confirmed via `git status` — none of those files appear in the changed-file list, §50).

## 50. Final task-scoped diff/stat

```
 src/main/java/zcylas/totality/client/renderer/hud/MobHealthBarHud.java                     | 30 +++++--
 src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java                 | 95 ++++++++++++----------
 .../presentation/Phase3CConsumerMigrationSourceRegressionTest.java                          | 38 +++++++--
 3 files changed, 108 insertions(+), 55 deletions(-)
```
Plus 2 new production Java files (`HudBarLayout.java`, `HudValueFormatter.java`) and 3 new test Java files (`HudBarLayoutTest.java`, `HudValueFormatterTest.java`, `TotalityHudCleanupSourceRegressionTest.java`, 48 tests) — untracked additions (`?? ` in `git status`), not tracked by `git diff --stat`.

## 51. Final repository-wide status

Identical to §5's starting status, plus exactly:
- The 2 new production files and 3 new test files (§33/§34), untracked.
- The 3 modified files listed in §50.
- The new implementation report and its review bundle (added after this section was first drafted — see §53's final confirmation).

No other entry changed. Verified by direct `git status --short` comparison, not assumption.

## 52. Confirmation that unrelated files were preserved

Confirmed. The pre-task and post-datagen/post-build `git status --short` snapshots were diffed directly and found identical for every non-task entry; no unrelated file was reset, stashed, cleaned, or restored at any point.

## 53. Confirmation that nothing was committed or pushed

Confirmed. No `git add`, `git commit`, or `git push` command was run at any point in this task. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD.

## 54. Recommended next step

1. Independent review of this implementation (production diff, `HudBarLayout`/`HudValueFormatter` design, test coverage, and this report).
2. User screenshot/visual validation at GUI Scale 4 (and at least one smaller scale) per §42's remaining checklist.
3. Correction pass if visual validation reveals an issue (e.g. text not centered as expected in the live font, insufficient contrast against a particular fill color).
4. Commit and push once review + visual validation are satisfied.

## 55. Review-correction addendum (2026-07-31)

Live visual testing of this implementation, plus an independent static review, found two real presentation defects and four smaller correctness/wording findings. All six were fixed in a follow-up pass on top of this same uncommitted work — see `TOTALITY_SMALL_HUD_CLEANUP_REVIEW_CORRECTION_REPORT.md` for the complete detail; this addendum summarizes what changed relative to §1-54 above.

**Sprite-stretch issue (found by live visual validation).** §10-14 above described the enlarged bars as drawing the *original* `bar_background.png`/`*_filled.png` sprites at the new `96×10` destination size via `blitSprite`. In practice this read as blurry/stretched, not an acceptable enlarged presentation. **This is no longer how the bars are drawn.**

**AC/chat overlap issue (found by live visual validation).** §20 above described AC as remaining at its pre-cleanup position, directly above the Health bar, with only its Y value shifting automatically as the bar stack grew. That position turned out to still collide with vanilla chat text. **AC has been relocated.**

**Rendering strategy change to code-drawn bars.** `TotalityHudRenderer.drawBarSmooth`/`drawBarMirroredSmooth` no longer call `blitSprite` for the frame or fill. Two new private helpers, `drawBarFrame` (border via the existing `GuiHelper.fillFrame` + a solid interior background fill) and `drawBarFill` (a colored fill rectangle with a top highlight and bottom shadow row, reusing the exact depth treatment `MobHealthBarHud`'s own HP bar already uses), replace the sprite draws. Four new named color constants (`HEALTH_FILL_COLOR`, `MANA_FILL_COLOR`, `STAMINA_FILL_COLOR`, `FOOD_FILL_COLOR`, each paired with a `*_BG_COLOR`) approximate each resource's prior sprite color family — the Health/Mana/Stamina hex values are the exact ones `OverviewTab` already uses for the same resources. No new texture file was added or edited; the original sprite assets remain on disk, untouched, simply unused by this active path (still referenced by the dormant pre-cleanup `drawBar`/`drawBarMirrored` methods, unchanged). Approved geometry, ordering, embedded text placement, fill directions (including Food's right-to-left growth), smoothing, and authority boundaries are all unchanged — `HudBarLayout.java` received zero further edits in the correction pass.

**AC relocation.** AC moved from `leftX, hpY - client.font.lineHeight - 2` to `leftX, screenH - VANILLA_HOTBAR_HEIGHT - client.font.lineHeight - 2`, where `VANILLA_HOTBAR_HEIGHT = 22` is a new, documented, read-only reference constant (vanilla's own stable hotbar height) used only to place this text just above it — vanilla's actual hotbar is never touched. Same X anchor (`leftX`), same displayed value, same text/color/shadow — only the Y position (and, trivially, the X reuse of `leftX` instead of being tied to `hpY`) changed.

**Formatter rollover correction.** `HudValueFormatter.abbreviate` now promotes to the next suffix tier whenever one-decimal rounding at the current tier would reach `1000` (e.g. `999_950 → "1M"` instead of `"1000k"`; `999_950_000 → "1B"` instead of `"1000M"`), while leaving every previously-correct case (`1000→"1k"`, `1500→"1.5k"`, `10000→"10k"`, and legitimate near-boundary values like `999_499→"999.5k"`) unchanged.

**Rarity-wording correction.** §42's manual-validation checklist item 17-19 has been corrected in place (see the updated wording above) — it no longer implies direct-look HP visibility should hide rarity; it now states precisely that no *new* rarity disclosure is introduced, and that the pre-existing unconditional Rare+ name prefix (never gated by combat state, confirmed unchanged before and after this entire cleanup) is simply unaffected.

**Comment correction.** The comment above `MobHealthBarHud.showHealthBar` no longer claims the crosshair-target path already passed an explicit alive check — it now correctly states that `isAlive()` is only explicitly checked on the `combatTarget` branch inside `getDisplayTarget()`. No behavior changed; this is a comment-only fix, confirmed by a new test asserting the `crosshairTarget` assignment still contains no `isAlive()` call.

**Source-test correction.** `Phase3CConsumerMigrationSourceRegressionTest`'s (unrelated-file, from the original Phase 3C pass, not touched by this HUD work) analog aside, this cleanup's own `resolverValuesStayLongThroughTheActiveDrawPath` test in `TotalityHudCleanupSourceRegressionTest` had one `assertFalse(A && B)` combined check split into two independent assertions, so a regression in only one of the two (stamina or mana) can no longer hide behind the other still being correct.

**Updated validation totals.** Focused: `HudBarLayoutTest` 15/0/0/0 (unchanged), `HudValueFormatterTest` 16/0/0/0 (+3), `TotalityHudCleanupSourceRegressionTest` 26/0/0/0 (+6 new, +1 split), `Phase3CConsumerMigrationSourceRegressionTest` 19/0/0/0 (re-confirmed, unaffected) — **76 focused tests, 0/0/0**. Full suite: **1264/0/0/0** across 96 classes (was 1254/96 before this correction pass; +10 tests, 0 new classes). Datagen: `written: 0`, byte-identical `git status` before/after. Clean build: successful, reproduced 1264/0/0/0. `git diff --check`: exit 0, only pre-existing advisory line-ending warnings.

**Updated manual validation findings.** `./gradlew runClient` was re-run after this correction pass: the client launched, auto-joined the existing dev save, and rendered live for roughly 30 real seconds with the new code-drawn rendering path executing every frame — no crash or exception traceable to this pass. Full pixel-level confirmation (bars no longer look stretched, AC visibly clear of chat) still requires a human at the keyboard — see the correction report's §17 for the specific remaining checklist.

This addendum does not change any confirmation in §42-53 above beyond the one wording correction already applied inline; Food 0-100, the full HUD redesign, the Mob Health/Mob HUD API, and the Contextual Interaction expansion remain equally unstarted after this correction pass as they were before it.

## 56. Final-visual-correction addendum (2026-07-31, second pass)

A second round of live visual testing on top of §55's correction found the code-drawn bars still didn't read correctly, plus AC's relocation from §55 still overlapped the left player-bar stack. This addendum summarizes the resulting geometry/style/AC rewrite; full detail lives in `TOTALITY_SMALL_HUD_CLEANUP_REVIEW_CORRECTION_REPORT.md`'s own final addendum.

**Asymmetrical sprite-derived offset (found by live visual testing).** §55's code-drawn bars kept the *old sprite-era* fill geometry (`FILL_OFFSET_X = 10`, `FILL_WIDTH = 84` inside a `FRAME_WIDTH = 96` frame) — a 10px left margin paired with only a ~2px right margin. Visually this read as a large empty black block at the left of every bar. **This geometry no longer exists.**

**Insufficient 10px height (found by live visual testing).** `FRAME_HEIGHT` was still `10` — with vanilla's default 9px font line height, `(10-9)/2 = 0`, leaving zero top margin and text touching the border. **Frame height is now 12.**

**Overly bright fill treatment (found by live visual testing).** §55's fill colors, while darker than the very first attempt, still paired a resource-tinted *background* with a `ColorUtils.blend(fillColor, WHITE, 0.45f)` highlight — bright enough to read as a glowing button rather than a recessed bar. **The background/track is now a single shared, neutral, dark recessed color across all four bars; only the fill itself (now darker/less saturated) plus explicit, restrained, named highlight/shadow constants carry resource color.**

**Incorrect far-left AC anchor (found by live visual testing).** §55 moved AC's Y away from the Health stack but left its X at `leftX` — the same anchor the whole player-bar column uses — so AC still visually overlapped Health. **AC's X is now derived from the vanilla hotbar's own approximate screen position instead of the player-bar stack.**

**New 96×12 geometry, symmetric 3px track inset, 90×6 track/fill region.** See `HudBarLayout.java`: `FRAME_WIDTH=96`, `FRAME_HEIGHT=12`, `TRACK_INSET_X=TRACK_INSET_Y=3`, `TRACK_WIDTH=90`, `TRACK_HEIGHT=6` (`96-3*2=90`, `12-3*2=6`, confirmed symmetric by a dedicated test). Y-position formulas are unchanged in *structure* (each row still derives from the one below it); their *values* shift to `Stamina=H-14`, `Mana=H-28`, `Health=Food=H-42`, `Rage=H-28` purely because `FRAME_HEIGHT` changed from 10 to 12.

**Darker recessed bar style.** New six-layer treatment in `TotalityHudRenderer`: (1) near-black outer border, (2) muted charcoal inner panel, (3) shared dark recessed track, (4) resource-colored fill (now darker/less saturated per resource — e.g. Health `0xFF7A2424` instead of the previous bright `0xFFCC3333`; see the correction report for the full old/new color table), (5) a restrained 1px highlight (explicit named constant, not a 45%-toward-white blend), (6) a deeper 1px shadow (explicit named constant, not a flat translucent overlay). Closer to `MobHealthBarHud`'s own panel aesthetic, per the task's explicit instruction.

**Hotbar-relative AC placement.** `acX = (screenW/2 - VANILLA_HOTBAR_HALF_WIDTH) + AC_HOTBAR_LEFT_INSET` (`91` and `2`, both named constants); `acY` is unchanged from §55 (`screenH - VANILLA_HOTBAR_HEIGHT - lineHeight - 2`). AC no longer references `leftX` anywhere.

**Automated validation results.** Focused: `HudBarLayoutTest` 18/0/0/0 (+3), `HudValueFormatterTest` 16/0/0/0 (unchanged), `TotalityHudCleanupSourceRegressionTest` 34/0/0/0 (+8), `Phase3CConsumerMigrationSourceRegressionTest` 19/0/0/0 (re-confirmed, unaffected). Full suite: **1274/0/0/0** across 96 classes. Datagen: `written: 0`, byte-identical `git status` before/after. Clean build: successful, reproduced 1274/0/0/0. `git diff --check`: exit 0, only pre-existing advisory line-ending warnings.

**Remaining user visual validation.** Not performed by this agent (no way to see the rendered client in this environment): bars have symmetrical internal padding; no large black block remains at the left; bars look recessed rather than bright and flat; text has comfortable vertical spacing; AC appears above the left side of the hotbar/XP area; AC does not overlap Health; AC does not overlap typed chat at GUI Scale 4; offhand remains clear; Food remains mirrored; direct-look Mob HP still works.

Phase 3C, the temporary direct-look Mob Display behavior, native Health/Food authority, and every other previously-accepted fix remain unchanged by this addendum — confirmed by `git status` showing only `TotalityHudRenderer.java` and `HudBarLayout.java` further modified (no other production file touched in this pass).

## 57. Final player-HUD and vanilla-chat compatibility correction (2026-07-31, third pass)

Live GUI Scale 4 testing on top of §56 found three remaining problems: the border was still too thick, the current/max numbers weren't perfectly contained within the bar interior, and vanilla chat still overlapped Health/Mana while AC sat too close to the vanilla XP bar. This pass fixes all three with a narrower bar interior, shadow-aware text centering, a small additional AC clearance, and — the new scope this pass — a geometry-derived reservation that shifts the entire vanilla chat system upward whenever the Totality HUD is eligible to render. Full technical detail lives in `TOTALITY_SMALL_HUD_CLEANUP_REVIEW_CORRECTION_REPORT.md`'s own final addendum; this section summarizes what changed.

### 57.1 Thick-border finding and the new 1px-border/94×10-interior geometry

§56's `TRACK_INSET_X = TRACK_INSET_Y = 3` (a 1px outer border + a solid charcoal inner-frame panel + a symmetrically-inset 90×6 track) read as a border that was still visually too thick at live GUI Scale 4. That three-layer scheme is gone. `HudBarLayout` now declares a genuine one-pixel border (`BORDER_WIDTH = 1`) directly enclosing a single 94×10 usable interior (`INTERIOR_OFFSET_X = INTERIOR_OFFSET_Y = 1`, `INTERIOR_WIDTH = 94`, `INTERIOR_HEIGHT = 10`) — the required arithmetic holds exactly: `96 - 1 - 1 = 94`, `12 - 1 - 1 = 10`. `TRACK_INSET_X`/`TRACK_INSET_Y`/`TRACK_WIDTH`/`TRACK_HEIGHT`/`INNER_FRAME_WIDTH` no longer exist anywhere in the file. `TotalityHudRenderer.drawBarFrame` is down from three fill layers to two: `GuiHelper.fillFrame` draws the 1px `OUTER_BORDER_COLOR` border around the full 96×12 frame, then a single `graphics.fill(...)` draws the `TRACK_COLOR` interior at `barX+1, barY+1`, 94×10 — no separate charcoal panel layer exists between them. `INNER_FRAME_COLOR` is fully removed. `drawBarFill` is otherwise unchanged (fill/highlight/shadow rows), now operating on the 94×10 interior instead of the old 90×6 track; the highlight/shadow rows still cannot render outside the interior, since `fillY`/`fillH` are always derived from `INTERIOR_OFFSET_Y`/`INTERIOR_HEIGHT` directly. The accepted darker resource palette from §56 (Health/Mana/Stamina/Food fill/highlight/shadow triples) was kept unchanged — no readability adjustment was judged necessary after removing the inner-frame panel, since the border-to-interior contrast (`0xFF0A0A0A` near-black vs `0xFF161616` dark recessed) remains clearly distinguishable on its own.

### 57.2 Text alignment finding and shadow-aware centering

§56 centered text against the full 96px frame width, which was mathematically equivalent to centering against the old symmetric track — but with the interior now only 94×10 (down from 96×10-equivalent), and with no accounting for the text's own rendered shadow extending 1px below the glyph, numbers were not perfectly contained within the interior. `HudBarLayout.textX`/`textY` are rewritten as the one shared text-positioning helper every bar calls: `textX = interiorX + (INTERIOR_WIDTH - textWidth) / 2` (text stays stationary as fill % changes, since it centers against the fixed interior, never the currently-filled width); `textY = interiorY + (INTERIOR_HEIGHT - (fontLineHeight + 1)) / 2`, treating the visual footprint as `fontLineHeight + 1` (the glyph plus its 1px shadow) rather than the glyph alone. For vanilla's default `fontLineHeight = 9`, this evaluates to exactly `interiorY` — a zero-margin exact fit, since `9 + 1 = 10 = INTERIOR_HEIGHT`. Both `drawBarSmooth` and `drawBarMirroredSmooth` call these same two formulas (no per-bar duplication), and `HudValueFormatter.display` now measures against `HudBarLayout.INTERIOR_WIDTH` (94) instead of the old `TRACK_WIDTH` (90).

### 57.3 AC clearance

AC's X anchor (hotbar-relative, `screenW/2 - VANILLA_HOTBAR_HALF_WIDTH + AC_HOTBAR_LEFT_INSET`) is unchanged. Its Y gained one new named constant, `AC_ABOVE_XP_CLEARANCE = 6`: `acY = screenH - VANILLA_HOTBAR_HEIGHT - client.font.lineHeight - 2 - AC_ABOVE_XP_CLEARANCE`. Value, color (`0xFF00CCFF`), shadow, and content (`"AC " + ac`) are all unchanged — only the Y source gained one more subtracted term.

### 57.4 Vanilla-chat bottom reservation (new scope)

This pass adds a geometry-derived reservation that shifts vanilla chat — rendered messages, hover/click hit-testing, the `ChatScreen` input box, the command-suggestion popup, and the command-usage hint — upward whenever the Totality HUD is eligible to render, so none of it collides with the left resource-bar stack anymore.

**Single source of truth.** `HudBarLayout` gained one adjustable constant, `CHAT_EXTRA_CLEARANCE = 4`, and one derived method:
```
leftStackHeight()        = 3*FRAME_HEIGHT + 2*BAR_GAP        = 3*12 + 2*2 = 40
chatBottomReservation()  = leftStackHeight() + BOTTOM_MARGIN + CHAT_EXTRA_CLEARANCE
                          = 40 + 2 + 4 = 46
```
(`VERTICAL_GAP` was renamed `BAR_GAP` to match this formula's own terminology; no behavior changed by the rename.) A new public class in the same package, `TotalityChatLayout`, is the single call site every chat mixin uses: `extraBottomReservation()` returns `HudBarLayout.chatBottomReservation()` (46) while `client.player != null && !client.gui.hud.isHidden()` — mirroring `TotalityHudRenderer`'s own eligibility gate exactly — and `0` otherwise, so vanilla behavior is completely untouched at title/menu screens, with no world loaded, or with the HUD F1-hidden. `TotalityChatLayout` is public specifically so the mixins (a different package) can call it, while `HudBarLayout` itself stays package-private, unchanged in visibility.

**Exact MC 26.2 classes/methods inspected.** Per the task's explicit instruction, nothing was assumed from an older Minecraft version — the real `minecraft-merged-deobf-26.2.jar` (Loom's own Mojang-mapped jar for this project's pinned MC 26.2) was extracted and `javap`-disassembled class-by-class:
- `net.minecraft.client.gui.components.ChatComponent` — specifically its **private** `extractRenderState(ChatGraphicsAccess, int, int, DisplayMode)` overload (distinct from the public 7-arg wrapper `Hud.extractChat` calls). Disassembly showed this one private method computes chat's bottom Y anchor as `bottom = floor((guiHeight - 40) / scale)`, and is the *sole* method both the public `extractRenderState` (rendering) and `captureClickableText` (hover/click hit-testing, called separately by whatever owns mouse interaction) route through — the literal `40` appears exactly once in its bytecode.
- `net.minecraft.client.gui.screens.ChatScreen.init()` — builds the input `EditBox` at `y = this.height - 12`. The literal `12` appears twice in this method (the Y position, then immediately after the box's own fixed height parameter); only the first (ordinal 0) is the position.
- `net.minecraft.client.gui.components.CommandSuggestions.showSuggestions(boolean)` — the `anchorToBottom` branch positions the suggestion popup at `y = screen.height - 12` (a separate, independently-hardcoded literal from `ChatScreen`'s own `12` — this popup does not read the input box's live Y). Unique in-method.
- `net.minecraft.client.gui.components.CommandSuggestions.extractUsage(GuiGraphicsExtractor)` — the `anchorToBottom` branch positions the command-usage hint box at `y = screen.height - 27 - 12*i`. The `27` is unique in-method; the other `12`s in the same method are the per-line height stride and box padding, not the bottom anchor, and are deliberately left untouched (they inherit the shift automatically, since they're computed from the already-shifted base).

**Exact render/interaction-coordinate changes.** Four new client-only mixins, all `@ModifyConstant`, all adding `TotalityChatLayout.extraBottomReservation()` to the vanilla literal (package `zcylas.totality.mixin.client.chat`):
- `ChatComponentBottomMarginMixin` — targets `ChatComponent`'s private 4-arg `extractRenderState`, `@Constant(intValue = 40)`. Because this is the one method both rendering and `captureClickableText`'s hover/click hit-testing route through, one injection moves both together *by construction* — there is no separate coordinate-conversion step that could be left behind. It also covers the restricted-chat-prompt background and the queued-message indicator, both computed from the same shifted `bottom` local. This same method backs both ordinary in-world chat (`DisplayMode.BACKGROUND`) and the open `ChatScreen` (`DisplayMode.FOREGROUND`), so one injection covers both contexts.
- `ChatScreenInputPositionMixin` — targets `ChatScreen.init()`, `@Constant(intValue = 12, ordinal = 0)` (the position only, not the box's own height).
- `CommandSuggestionsListPositionMixin` — targets `CommandSuggestions.showSuggestions(boolean)`, `@Constant(intValue = 12)`.
- `CommandSuggestionsUsagePositionMixin` — targets `CommandSuggestions.extractUsage(GuiGraphicsExtractor)`, `@Constant(intValue = 27)`.

All four are registered under `totality.mixins.json`'s `"client"` array only (never the always-loaded common `"mixins"` array), since every targeted class is client-only. Confirmed by the dedicated-server classloading check below: the server logs `Env=SERVER` for the Mixin subsystem and never attempts to load `ChatComponent`/`ChatScreen`/`CommandSuggestions`, so none of these four mixins are even candidates for application there.

**Confirmation vanilla chat's other behavior is unchanged.** No mixin touches chat width, opacity, background opacity, history size (`MAX_CHAT_HISTORY`), or message wrapping — each is a single `@ModifyConstant` on one already-identified bottom-anchor literal, nothing else. Only a vertical shift is applied; no mixin uses `@ModifyArg`/touches an X-coordinate constant.

### 57.5 Exact files changed this pass

Production: `HudBarLayout.java` (interior geometry rewrite, `BAR_GAP` rename, `chatBottomReservation()` added), `TotalityHudRenderer.java` (`drawBarFrame`/`drawBarFill`/`drawBarSmooth`/`drawBarMirroredSmooth` updated for the new interior constants, `AC_ABOVE_XP_CLEARANCE` added), `TotalityChatLayout.java` (new). Mixin: `ChatComponentBottomMarginMixin.java`, `ChatScreenInputPositionMixin.java`, `CommandSuggestionsListPositionMixin.java`, `CommandSuggestionsUsagePositionMixin.java` (all new). Mixin config: `totality.mixins.json` (4 new entries in `"client"`). `MobHealthBarHud.java` was **not** touched this pass. Tests: `HudBarLayoutTest.java` (rewritten for the new geometry + new chat-reservation tests), `HudValueFormatterTest.java` (stale `TRACK_WIDTH` references updated to `INTERIOR_WIDTH`), `TotalityHudCleanupSourceRegressionTest.java` (stale interior/AC assertions updated), `ChatCompatibilityCorrectionSourceRegressionTest.java` (new, 12 tests). `Phase3CConsumerMigrationSourceRegressionTest.java` not touched.

### 57.6 Exact validation results

Focused: `HudBarLayoutTest` **23/0/0/0** (+5 from §56's 18), `HudValueFormatterTest` **16/0/0/0** (unchanged), `TotalityHudCleanupSourceRegressionTest` **35/0/0/0** (+1 net from §56's 34), `ChatCompatibilityCorrectionSourceRegressionTest` **12/0/0/0** (new), `Phase3CConsumerMigrationSourceRegressionTest` **19/0/0/0** (re-confirmed, unaffected). Full suite: **1292/0/0/0** across **97** test classes (was 1274/96 before this pass). Datagen: `written: 0`; `git status --short` before/after byte-for-byte identical. Clean build: `BUILD SUCCESSFUL`, `:test` reproduced 1292/0/0/0. `git diff --check`: exit 0, only the same pre-existing CRLF advisory warnings as every prior pass (no new files added to that list).

**Dedicated-server classloading check.** `./gradlew runServer` reached `Done (0.339s)! For help, type "help"` and ran normally for its full duration — no `ClassNotFoundException`/`NoClassDefFoundError`, confirming none of the four new client-only chat mixins (or `TotalityChatLayout`, or the rest of the HUD renderer package) are reached by the dedicated server. The same pre-existing `ProvisionerEntityBackedSmokeTest`/`OffhandAttackVerification` self-test failures appeared, identical to the client run below — confirmed pre-existing and unrelated to this pass (neither system was touched).

**Client smoke test.** `./gradlew runClient` launched, all four chat mixins applied without any Mixin apply/transform error (a wrong `@ModifyConstant` target or ordinal would have thrown at class-load time, before the client even reached the main menu), joined the existing dev world, and ran for several real minutes — including a logged in-world chat message (`"Zcylas was slain by Zombie"`, rendered through the now-mixed-into `ChatComponent.extractRenderState` path) with no crash or exception traceable to this pass. This confirms the mixins apply and execute without error; it is not pixel-level visual confirmation.

### 57.7 Remaining user visual validation

Not performed by this agent — no way to see the rendered client in this environment. In addition to §56's still-outstanding items: 1px border reads as thin, not thick; no charcoal frame is visible between the border and the interior; current/max text is fully contained within the 94×10 interior with no clipping; text is visually centered and stays still as fill % changes; AC sits clearly above the hotbar/XP area, not on it, and does not overlap Health; ordinary chat (not typing) renders above the resource-bar stack; the ChatScreen input box moves up together with the messages when chat is opened; command-suggestion popup and usage hint move with the input box; the chat scrollbar/hover/click targets still line up with the (now higher) visible text; scrolling chat history still works; offhand indicator remains clear; direct-look Mob HP still works; F1 hides everything normally including the chat shift (chat reverts to vanilla position); no crashes during normal play.

### 57.8 Confirmation nothing was committed or pushed (this addendum)

No `git add`, `git commit`, or `git push` command was run at any point during this pass. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD. Phase 3C, the temporary direct-look Mob Display behavior, Food's native 0-100 non-start, and the decision not to build a general HUD reservation framework (this remains, per the task's own framing, "a contained vanilla-chat compatibility bridge that can later be replaced by the full HUD layout system") all hold unchanged after this pass.

## 58. Next final correction pass — text pixel offset, sampled palette, chat single-origin fix, menu-screen HUD gating (2026-07-31, fourth pass)

Final live testing on top of §57 found four remaining problems: current/max text still read one visible pixel too high; vanilla chat's message stack and input line moved independently, leaving a large empty gap; the persistent Totality HUD stayed visible behind Inventory and other ordinary menu screens, unlike the vanilla hotbar; and the code-drawn bar colors were still too bright compared to the original sprites. This pass fixes all four, narrowly, on top of the existing uncommitted work. Full technical detail lives in `TOTALITY_SMALL_HUD_CLEANUP_REVIEW_CORRECTION_REPORT.md`'s own newest addendum; this section summarizes what changed.

### 58.1 Visually-high text finding and VALUE_TEXT_Y_OFFSET

§57's `textY` formula mathematically centered text with zero remaining margin (treating the glyph+shadow footprint as the full `fontLineHeight + 1` = 10, exactly filling the 10px interior). Live testing found this still read as one pixel too high. `HudBarLayout` gained one named constant, `VALUE_TEXT_Y_OFFSET = 1`, applied strictly *after* the existing mathematical centering:
```
baseTextY = interiorY + (INTERIOR_HEIGHT - (fontLineHeight + 1)) / 2   // unchanged, still the exact math from §57
textY     = baseTextY + VALUE_TEXT_Y_OFFSET
```
The formula shape, horizontal centering, font, font scale, shadow, and text color are all unchanged — only this one additive term is new. Containment reasoning: `fontLineHeight` (9) is a line-*spacing* constant that reserves one extra row for the gap between wrapped lines; a single line of Totality's bar text (always digits, `/`, spaces, and non-descender abbreviation letters k/M/B — never g/j/p/q/y) never inks that reserved row, so the real single-line glyph ink height is `fontLineHeight - 1` (8), one row shorter than the conservative model §57 used for containment. Modeled this way, text plus its shadow spans exactly 9 rows starting at the new `textY`, landing flush with the interior's last row (never touching the 1px border) — confirmed by a dedicated test (`textYPlusShadowExtentStaysWithinTheInteriorForTheDefaultFontLineHeight`, rewritten this pass with the corrected model).

### 58.2 Original-sprite palette sampling

All bar colors are now sampled directly from the original pre-cleanup sprite assets — not approximated, not hand-picked, not derived from `OverviewTab`. Sampling was done once during development with a one-off PowerShell/`System.Drawing` script (never at runtime — confirmed by a test asserting no `Bitmap`/`BufferedImage`/`ImageIO` API appears in production code); the resulting values were authored as named constants.

**Assets sampled** (all under `src/main/resources/assets/totality/textures/gui/sprites/hud/`, all confirmed byte-identical/untouched by this pass — no PNG appears in `git status`):

| Asset | Size | What was sampled |
|---|---|---|
| `bar_background.png` | 166×16 | Border row (y=3) and empty-track row (y=6), each averaged across every fully-opaque (alpha=255) pixel in the row |
| `health_filled.png` | 144×8 | Top row (y=1, highlight) and bottom row (y=6, fill), each averaged across the full width; darkest fully-opaque pixel in the bottom row (shadow) |
| `mana_filled.png` | 144×8 | Same method as health |
| `stamina_filled.png` | 144×8 | Same method as health |
| `hunger_filled.png` | 144×8 | Same method as health (used for Food) |

The fill sprites turned out to have a horizontal brightness gradient (brightest near the horizontal center, darkening toward both edges) layered on top of the vertical 2-band (lighter top / darker bottom) structure — averaging every opaque pixel in each representative row (rather than sampling one arbitrary x) gives a reproducible value representative of the whole sprite rather than its narrow bright center peak. Shadow constants are the single darkest genuinely-sampled pixel in each fill sprite's bottom row — a real pixel already present in the art (the sprites' own edge-darkening), not a computed/invented darkening.

**Final sampled values:**

| Constant | Old (§57, approximated) | New (sampled) | Source |
|---|---|---|---|
| `OUTER_BORDER_COLOR` | `0xFF0A0A0A` | `0xFF030303` | `bar_background.png` border row average |
| `TRACK_COLOR` | `0xFF161616` | `0xFF2D2C2C` | `bar_background.png` track row average |
| `HEALTH_FILL_COLOR` | `0xFF7A2424` | `0xFF720A0B` | `health_filled.png` bottom-row average |
| `HEALTH_HIGHLIGHT_COLOR` | `0xFF9C3A3A` | `0xFF950E0E` | `health_filled.png` top-row average |
| `HEALTH_SHADOW_COLOR` | `0xFF4A1414` | `0xFF350605` | `health_filled.png` darkest sampled pixel |
| `MANA_FILL_COLOR` | `0xFF28468A` | `0xFF02234E` | `mana_filled.png` bottom-row average |
| `MANA_HIGHLIGHT_COLOR` | `0xFF3C5CA8` | `0xFF032859` | `mana_filled.png` top-row average |
| `MANA_SHADOW_COLOR` | `0xFF162A55` | `0xFF001C3E` | `mana_filled.png` darkest sampled pixel |
| `STAMINA_FILL_COLOR` | `0xFF2E6E2E` | `0xFF133203` | `stamina_filled.png` bottom-row average |
| `STAMINA_HIGHLIGHT_COLOR` | `0xFF44904A` | `0xFF143504` | `stamina_filled.png` top-row average |
| `STAMINA_SHADOW_COLOR` | `0xFF193A19` | `0xFF112F02` | `stamina_filled.png` darkest sampled pixel |
| `FOOD_FILL_COLOR` | `0xFF8A5A28` | `0xFF482601` | `hunger_filled.png` bottom-row average |
| `FOOD_HIGHLIGHT_COLOR` | `0xFFA8753C` | `0xFF4A2702` | `hunger_filled.png` top-row average |
| `FOOD_SHADOW_COLOR` | `0xFF553015` | `0xFF462501` | `hunger_filled.png` darkest sampled pixel |

Both the old (§56/§57) and new sets of hex values are confirmed absent/present respectively by dedicated tests. No runtime `ColorUtils` blending was introduced; the geometry (96×12 frame, 1px border, 94×10 interior, code-drawn rectangles, Food's mirrored fill) is completely unchanged — only the fourteen color constants' values changed.

### 58.3 Chat history/input-origin mismatch — cause and fix

**Symptom:** the message stack sat too high, with a large empty gap above the input line.

**Diagnosis.** Every one of §57's four chat mixins already added the identical `TotalityChatLayout.extraBottomReservation()` (46) to its own vanilla literal, and this was verified algebraically scale-invariant (the reservation is added inside the `(guiHeight - C) / chatScale` numerator, which — by construction — produces an exact `C`-pixel screen-space shift regardless of the chat-scale option). So there was no double-application, no missing application, and no sign error in the literal sense. The actual cause was architectural: `ChatComponent`'s message-bottom anchor and `ChatScreen`'s input-Y anchor each independently added the reservation to two *unrelated* vanilla constants — `ChatComponent.BOTTOM_MARGIN` (40) vs. `ChatScreen`'s own hardcoded input margin (12) — which already sit 28px apart in completely unmodified, vanilla Minecraft. That 28px gap is normally invisible because it sits directly above the (hidden-while-typing) hotbar; once the whole chat block floats 46px higher with nothing nearby to visually justify it, the same unchanged 28px gap reads as obviously empty dead space.

**Fix — single shared origin.** The message-bottom anchor no longer derives from `BOTTOM_MARGIN` at all. `TotalityChatLayout` now exposes:
```
CHAT_INPUT_BOTTOM_MARGIN = 12   // mirrors ChatScreen's own vanilla input-Y margin
MESSAGE_TO_INPUT_GAP     = 8    // vanilla's own ChatComponent.MESSAGE_BOTTOM_TO_MESSAGE_TOP constant

inputBottomMargin()      = CHAT_INPUT_BOTTOM_MARGIN + extraBottomReservation()
messageBottomMargin()    = inputBottomMargin() + MESSAGE_TO_INPUT_GAP     // reuses inputBottomMargin(), does not re-derive
usageHintBottomMargin()  = USAGE_HINT_VANILLA_MARGIN(27) + extraBottomReservation()   // unchanged base — not reported broken
```
`ChatComponentBottomMarginMixin`'s handler now returns `TotalityChatLayout.messageBottomMargin()` directly (no longer `original + reservation`); `ChatScreenInputPositionMixin` and `CommandSuggestionsListPositionMixin` both now return `TotalityChatLayout.inputBottomMargin()` — the exact same shared-origin call, guaranteeing the input box and the suggestion popup can never drift apart. `CommandSuggestionsUsagePositionMixin` keeps its own vanilla base (27) plus the shared reservation, since its own vanilla spacing above input (27-12=15px) was not reported broken. `messageBottomMargin()`'s gap above input is now `8` (vanilla's real per-line spacing constant), not the previous incidental `28` — a genuine, deliberate, vanilla-sourced reduction, not an invented number. Verified by a dedicated test that `messageBottomMargin()`'s own body never calls `extraBottomReservation()` directly — it can only reach it by calling `inputBottomMargin()`, so the reservation is structurally guaranteed to apply exactly once per element.

### 58.4 Persistent HUD visibility defect on menu screens

**Vanilla behavior inspected.** `GameRenderer.extract(DeltaTracker, boolean)` (decompiled from the real `minecraft-merged-deobf-26.2.jar`) calls `Gui.extractRenderState(DeltaTracker, boolean renderHud, boolean renderScreen)`, where `renderHud` is `true` whenever a level is loaded (`minecraft.isGameLoadFinished() && renderLevelParam && minecraft.level != null`) — there is **no dedicated vanilla flag** for "hidden because a screen wants it hidden." Vanilla's own hotbar/health/food (reached via `Hud.extractRenderState`, gated only by that same "is a level loaded" condition — the exact condition Totality's HUD already applied via `client.player == null`) keeps rendering underneath every screen; opaque screens like `InventoryScreen` simply draw their own background panel directly over the region where it sits, visually covering it without ever actually stopping it from rendering. Since Totality's bars live in the screen corners — outside where those panels are drawn — they were never covered the same way, which is why they stayed visible.

**Fix.** `TotalityHudRenderer` gained `shouldRenderPersistentPlayerHud(Minecraft client)`, replacing the old `if (client.player == null || client.gui.hud.isHidden()) return;` early-return:
```java
private static boolean shouldRenderPersistentPlayerHud(Minecraft client) {
    if (client.player == null || client.gui.hud.isHidden()) return false;
    Screen screen = client.gui.screen();
    return screen == null || screen instanceof ChatScreen;
}
```
This reproduces the required visual effect by explicit classification rather than reusing a vanilla condition that (per the decompilation above) does not actually exist for this purpose: normal gameplay (`screen == null`) and `ChatScreen` are the only eligible states; every other screen (`InventoryScreen`, creative inventory, container/workstation/trading screens, character/menu screens, pause) is ineligible by construction, without needing to enumerate every blocking-screen subclass individually — the narrowest predicate that satisfies the required behavior list.

**Exact elements gated.** The single early-return sits at the very top of `TotalityHudRenderer`'s render lambda, so it gates everything drawn afterward: Health, Mana, Stamina, Food, AC, Rage/secondary-resource pips, the offhand attack-readiness indicator, `MagicContextHud`, and `AbilityContextHud`. (`PowerAttackFlash`'s check happens before the gate is even reached in the lambda's control flow, but this is a non-issue in practice — a power attack cannot be actively flashing while a blocking screen prevents attacking in the first place.)

**Exact elements deliberately not gated.** `TotalityHudRenderer` does not reference — and this pass did not touch — `NotificationManager`, `MobHealthBarHud`, `CombatTextRenderer`, `RestHud`, `QuestTrackerHud`, `CastBarHud`, boss bars, status effects, or subtitles (confirmed by a dedicated test asserting none of those class names appear in `TotalityHudRenderer.java`); each remains governed entirely by its own independent, previously-established screen rules. Vanilla's own hotbar/Gui/GameRenderer classes are not mixed into or modified at all — this is a pure `HudElementRegistry`-side gate.

### 58.5 Exact files changed this pass

Production: `HudBarLayout.java` (`VALUE_TEXT_Y_OFFSET` added), `TotalityHudRenderer.java` (sampled palette constants replacing the §57 approximated set; `shouldRenderPersistentPlayerHud` added and wired into the render lambda's early return), `TotalityChatLayout.java` (`CHAT_INPUT_BOTTOM_MARGIN`, `MESSAGE_TO_INPUT_GAP`, `USAGE_HINT_VANILLA_MARGIN`, `inputBottomMargin()`, `messageBottomMargin()`, `usageHintBottomMargin()` added). Mixin (all modified, none new): `ChatComponentBottomMarginMixin.java`, `ChatScreenInputPositionMixin.java`, `CommandSuggestionsListPositionMixin.java` (handlers rewritten to call the new shared-origin methods instead of `original + reservation`); `CommandSuggestionsUsagePositionMixin.java` (handler now calls `usageHintBottomMargin()`). Mixin config: unchanged (`totality.mixins.json` already registered all four mixins in §57). `MobHealthBarHud.java` **not** touched this pass. Tests: `HudBarLayoutTest.java` (VALUE_TEXT_Y_OFFSET tests added, containment test corrected), `TotalityHudCleanupSourceRegressionTest.java` (palette assertions updated, old-approximated-palette-removed test added, sampling-provenance test added), `ChatCompatibilityCorrectionSourceRegressionTest.java` (single-origin architecture tests added, stale mixin-body assertions fixed), `PersistentHudVisibilitySourceRegressionTest.java` (new, 8 tests). `Phase3CConsumerMigrationSourceRegressionTest.java` not touched.

### 58.6 Exact validation results

Focused: `HudBarLayoutTest` **25/0/0/0** (+2 from §57's 23), `HudValueFormatterTest` **16/0/0/0** (unchanged), `TotalityHudCleanupSourceRegressionTest` **37/0/0/0** (+2 from §57's 35), `ChatCompatibilityCorrectionSourceRegressionTest` **16/0/0/0** (+4 from §57's 12), `PersistentHudVisibilitySourceRegressionTest` **8/0/0/0** (new), `Phase3CConsumerMigrationSourceRegressionTest` **19/0/0/0** (re-confirmed, unaffected) — **121 focused tests, 0/0/0** (plus the pre-existing `NotificationManagerLayoutTest`/`NotificationWrappingSourceRegressionTest` in the same package, also 0/0/0, unaffected/untouched). Full suite: **1308/0/0/0** across **98** test classes (was 1292/97 before this pass; +16 tests, +1 class). Datagen: `written: 0`; `git status --short` before/after byte-for-byte identical. Clean build: `BUILD SUCCESSFUL`, `:test` reproduced 1308/0/0/0. `git diff --check`: exit 0, only the same pre-existing CRLF advisory warnings as every prior pass (no new files added to that list).

**Dedicated-server classloading check.** `./gradlew runServer` reached `Done (0.330s)! For help, type "help"` and ran normally — no `ClassNotFoundException`/`NoClassDefFoundError`. The four chat mixins are unchanged in registration (still `"client"`-only) and `TotalityHudRenderer`'s new `Screen`/`ChatScreen` imports are standard client-side vanilla classes already reachable from other client-only code in this file, so no new server-classloading risk was introduced. Pre-existing `ProvisionerEntityBackedSmokeTest`/`OffhandAttackVerification` self-test failures appeared identically to prior passes — confirmed pre-existing, unrelated.

**Client smoke test.** `./gradlew runClient` launched with all production changes for this pass already in place, applied all four chat mixins without any Mixin transform error, joined the dev world, and ran for a little over two minutes with no crash — including several "Unknown or incomplete command" chat messages (exercising `CommandSuggestions.extractUsage`, one of the four mixed-into methods) and a clean, orderly shutdown (`BUILD SUCCESSFUL`).

### 58.7 Remaining user visual validation

Not performed by this agent — no way to see the rendered client in this environment: numbers appear visually centered vertically with balanced top/bottom glyph spacing; text shadow remains inside the bar; bar colors match the darker original-sprite appearance; borders remain thin; Food remains mirrored; ordinary messages sit immediately above the input origin with no large empty gap below chat history; chat input sits above the resource stack; command suggestions remain attached to input; chat scrollbar remains aligned; click/hover behavior matches visible chat text; normal gameplay and ChatScreen both show the custom HUD; Inventory, creative inventory, and chest/container screens hide the custom HUD; F1 hides the custom HUD; offhand remains unobstructed; AC remains clear of Health and XP; direct-look Mob HP still works; no crashes or rendering exceptions occur.

### 58.8 Confirmations (this addendum)

No `git add`, `git commit`, or `git push` command was run at any point during this pass. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD. Phase 3C (`ClientResourcePresentationResolver`, Mana/Stamina/Rage presentation, `PENDING_RESYNC`, legacy fallback, native Health/Food authority, Food's native 0-20 fill, `TotalityMovementHandler`, Resource sync/packets/mutation) remains completely unchanged — confirmed by dedicated tests re-run this pass with identical results. The temporary direct-look Mob Display behavior (`MobHealthBarHud.java`) was not touched at all this pass — confirmed by its absence from `git status`. Food 0-100, `TotalityFoodItem`, dormant Resource definitions, a Resource-definition inventory audit, Tooltip item Value, and the full HUD Layout/Reservation API all remain unstarted, exactly as before — none of this pass's four corrections required or introduced any of them.

## 59. Final ChatScreen visual correction — input background strip (2026-07-31, fifth/micro pass)

Live GUI Scale 4 validation on top of §58 confirmed the message stack, chat input origin, and resource HUD were all now correctly positioned — but a full-width translucent black strip remained rendered at the screen's original (unshifted) bottom edge, separate from the moved `EditBox`. This section documents the cause and the narrow, single-purpose fix.

### 59.1 Cause — the separately-rendered ChatScreen input background

**Method inspected.** `ChatScreen.extractRenderState(GuiGraphicsExtractor, int, int, float)` — decompiled and confirmed against MC 26.2's real `minecraft-merged-deobf-26.2.jar` (`net/minecraft/client/gui/screens/ChatScreen.class`), not assumed. Its very first statement, executed *before* `ChatComponent.extractRenderState` (messages) and before delegating to `Screen.extractRenderState` (which renders the actual `EditBox` widget), is:
```
graphics.fill(2, this.height - 14, this.width - 2, this.height - 2, <vanilla background color>)
```
This is the black strip — a completely separate draw call from the `EditBox` itself. `ChatScreen.extractBackground(GuiGraphicsExtractor, int, int, float)` (a distinct, separately-declared method the task hypothesized might own it) is confirmed to be an empty no-op in this version (`{ return; }`) — the background lives entirely inside `extractRenderState`. The `EditBox` itself is borderless (`setBordered(false)`, confirmed in `init()`'s bytecode), so this `fill` is the input's *entire* visual backing, not a decorative extra.

**Previous (unshifted) coordinates.** `x1=2, y1=height-14, x2=width-2, y2=height-2` — a 12px-tall rectangle whose bounds sit exactly 2px above the (pre-§57) `EditBox`'s own unshifted bounding box (`14 = 12(EditBox's own vanilla margin) + 2`; `2 = 0 + 2`, since the box's own bottom sits flush with the true screen edge in vanilla). §57's `ChatScreenInputPositionMixin` only ever redirected the `12` inside `ChatScreen.init()` (the `EditBox`'s own construction) — this separate `fill` call inside `extractRenderState` was never touched, so it kept rendering at the screen's true, unshifted bottom edge while the box itself moved away from it.

### 59.2 Fix — shared shifted origin

Two literals in `graphics.fill(2, height-14, width-2, height-2, ...)` needed to move (`14` and the *third* occurrence of `2`, i.e. the `height - 2` bottom margin) — the *first two* occurrences of `2` (the left and right X margins) must stay untouched per the task's explicit width/horizontal-margin constraint.

`TotalityChatLayout` gained two new derived methods, both built directly on `inputBottomMargin()` — the exact same shared origin `ChatScreenInputPositionMixin` already returns for the `EditBox` itself — rather than independently re-adding the reservation to the raw `14`/`2` literals a second time:
```
INPUT_BACKGROUND_ABOVE_EDIT_BOX = 2   // vanilla's own fixed offset between the background rect and the EditBox's own bounds
VANILLA_EDIT_BOX_HEIGHT         = 12  // the EditBox's own unchanged height

inputBackgroundTopMargin()    = inputBottomMargin() + INPUT_BACKGROUND_ABOVE_EDIT_BOX
inputBackgroundBottomMargin() = inputBottomMargin() - VANILLA_EDIT_BOX_HEIGHT + INPUT_BACKGROUND_ABOVE_EDIT_BOX
```
Sanity check at `R=0` (vanilla, ineligible): `inputBottomMargin()=12` → top margin `=14` ✓, bottom margin `=12-12+2=2` ✓ — exactly reproduces vanilla's own literals. At `R=46`: top margin `=60`, bottom margin `=48` — the background rectangle's full 12px height is preserved (`60-48=12`, unchanged), it is simply translated upward by the same 46px reservation as the `EditBox` it backs.

New mixin `ChatScreenInputBackgroundMixin` (package `zcylas.totality.mixin.client.chat`, registered under `totality.mixins.json`'s `"client"` array), two `@ModifyConstant` handlers on `ChatScreen.extractRenderState(GuiGraphicsExtractor, int, int, float)`:
- `@Constant(intValue = 14)` → `TotalityChatLayout.inputBackgroundTopMargin()` (unique in-method, no ordinal needed).
- `@Constant(intValue = 2, ordinal = 2)` → `TotalityChatLayout.inputBackgroundBottomMargin()` (the *third* of three occurrences of int `2` in this method — the first two are the fill's left/right X margins, confirmed untouched by ordinal targeting).

**Confirmation the EditBox and its background now share one origin.** Both `inputBackgroundTopMargin()`/`inputBackgroundBottomMargin()` call `inputBottomMargin()` internally rather than `extraBottomReservation()` directly — confirmed by a dedicated test — so the background rectangle is structurally guaranteed to translate in lockstep with the `EditBox`, by construction, regardless of any future change to the reservation. Width, color, opacity, horizontal margins, and height are all untouched — only the two Y-anchor literals moved, and by exactly the same amount as each other (preserving the rectangle's own 12px height).

### 59.3 Exact files changed this pass

Production: `TotalityChatLayout.java` (`inputBackgroundTopMargin()`/`inputBackgroundBottomMargin()` added). Mixin (new): `ChatScreenInputBackgroundMixin.java`. Mixin config: `totality.mixins.json` (+1 entry under `"client"`). No other file touched — `TotalityHudRenderer.java`, `HudBarLayout.java`, `MobHealthBarHud.java`, and the other four chat mixins are all untouched this pass (confirmed by `git status` showing no further modification to any of them). Tests: `ChatCompatibilityCorrectionSourceRegressionTest.java` (8 new tests covering the 9-item checklist; existing loop-based tests extended to include the new mixin).

### 59.4 Exact validation results

Focused: `ChatCompatibilityCorrectionSourceRegressionTest` **24/0/0/0** (+8 from §58's 16); all other focused HUD/chat/Phase-3C tests unchanged — **144 focused tests, 0/0/0** total (was 136). Full suite: **1316/0/0/0** across **98** classes (unchanged class count — no new test class, tests added to an existing file). Datagen: `written: 0`, byte-identical `git status` before/after. Clean build: `BUILD SUCCESSFUL`, `:test` reproduced 1316/0/0/0. `git diff --check`: exit 0, same pre-existing CRLF advisory warnings.

**Dedicated-server classloading check.** `./gradlew runServer` reached `Done (0.354s)!` and ran normally — no `ClassNotFoundException`/`NoClassDefFoundError`. The new mixin is registered only under `"client"`, alongside the other four.

**Client smoke test.** `./gradlew runClient` applied all five chat mixins (including the new ordinal-qualified one) with no Mixin transform error — a wrong ordinal/target throws at class-load time, before the client reaches the main menu, and it did not — joined the dev world, exercised chat/command-suggestion rendering (`"Unknown or incomplete command"` messages), and shut down cleanly (`BUILD SUCCESSFUL`).

### 59.5 Remaining user visual validation

Not performed by this agent: the black strip no longer appears at the screen's true bottom edge; a matching translucent background is visible directly behind the (now correctly positioned) input box, at the same size/color/opacity as before; the background, `EditBox`, cursor, input text, and command suggestions all remain visually aligned; message/history positioning is unaffected; command-suggestion positioning is unaffected; no crashes or rendering exceptions occur.

### 59.6 Confirmation nothing was committed or pushed (this addendum)

No `git add`, `git commit`, or `git push` command was run during this pass. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD. Bar geometry, bar colors, AC placement, chat message placement, chat input placement, command-suggestion placement, Mob Display behavior, and Phase 3C behavior are all unchanged by this pass — it touched exactly one previously-unaddressed vanilla draw call.

## 60. Final closure — user manual validation confirmed, small HUD cleanup ready to commit (2026-07-31)

The user completed final in-world visual validation at GUI Scale 4 and confirmed everything now appears correct — the last outstanding item (the §59 input-background black strip) is gone, and no further HUD/chat correction is required. This section records the final accepted behavior of the whole small-HUD-cleanup arc (§1 through §59) as a single closure summary, without rewriting any earlier section's own history.

### 60.1 Final accepted behavior

**Player bars.** Health, Mana, Stamina, and Food are code-drawn (no sprite blit/stretch). Frame is 96×12 with a genuine one-pixel border; interior is 94×10 at offset 1×1. Health/Mana/Stamina fill left-to-right; Food fills right-to-left (mirrored). Current/max values are centered in the full interior; `HudBarLayout.VALUE_TEXT_Y_OFFSET = 1` (applied after the shared mathematical centering formula) provides the correct final visual vertical centering, confirmed by the user — text and its shadow stay inside the bar, never crossing the border. Values no longer crowd the vanilla hotbar or the offhand indicator. Bars use the dark palette sampled directly from the original sprite assets (`bar_background.png`, `health_filled.png`, `mana_filled.png`, `stamina_filled.png`, `hunger_filled.png` — see §58.2's exact sampling method and ARGB table); the original sprite files themselves remain byte-unchanged on disk and are used only as palette references, never blitted.

**AC.** Remains hotbar-relative (`screenW/2 - VANILLA_HOTBAR_HALF_WIDTH + AC_HOTBAR_LEFT_INSET`), positioned above the left side of the XP/hotbar region, and no longer overlaps the player bars or the XP presentation (`AC_ABOVE_XP_CLEARANCE = 6`). Still the same temporary test presentation — no shield artwork, no AC-authority redesign was started at any point in this arc.

**Chat.** Vanilla chat messages, history, input, suggestions, scrollbar, and interaction (hover/click) coordinates all share the single corrected shifted origin (`TotalityChatLayout.inputBottomMargin()`/`messageBottomMargin()`/`usageHintBottomMargin()`/`inputBackgroundTopMargin()`/`inputBackgroundBottomMargin()`, all ultimately derived from `HudBarLayout.chatBottomReservation()`). Ordinary chat messages render above the player-resource stack; the open `ChatScreen` remains aligned. The large empty gap between message history and the input (§58) and the separately-rendered bottom input-background rectangle that stayed at the unshifted screen edge (§59) are both corrected — the background now moves in lockstep with the `EditBox`, and the obsolete black strip at the original bottom edge is gone, confirmed by the user. `CHAT_EXTRA_CLEARANCE = 4` remains the single manually adjustable extra-spacing value; the current geometry-derived reservation (`leftStackHeight()(40) + BOTTOM_MARGIN(2) + CHAT_EXTRA_CLEARANCE(4) = 46`) remains accurately documented. Chat width, scale, opacity, wrapping, message lifetime, and history behavior all remain exactly vanilla — untouched throughout every pass.

**HUD visibility.** The persistent Totality player HUD is visible during normal gameplay and while `ChatScreen` is open; it is hidden on `InventoryScreen`, creative inventory, container, workstation, trading, and other ordinary menu screens where the vanilla hotbar itself is not presented, and hidden with F1 — via `shouldRenderPersistentPlayerHud(Minecraft)` (§58.4). Gated elements: Health, Mana, Stamina, Food, AC, Rage/secondary-resource pips, the custom offhand attack-readiness indicator, `MagicContextHud`, `AbilityContextHud`. Separately registered overlays (`NotificationManager`, `MobHealthBarHud`, `CombatTextRenderer`, `RestHud`, `QuestTrackerHud`, `CastBarHud`, boss bars, status effects, subtitles) were confirmed not referenced anywhere in `TotalityHudRenderer.java` and so were never unintentionally gated.

**Mob Display.** Directly looking at a valid non-player `LivingEntity` shows its name and HP bar unconditionally (`showHealthBar = true`). Neutral direct-look styling remains neutral (`COLOR_UNKNOWN`, no rank/AC disclosure outside combat). Existing combat-only threat color and additional disclosures, combat retention (80 ticks), and smoothing all remain exactly as before this entire arc began — `MobHealthBarHud.java` was not touched by any pass after the original cleanup's comment-only fix (§19/§2 correction reports). This remains explicitly temporary behavior, pending the dedicated future Mob Health/Mob HUD redesign, which was never started.

**Notifications.** Manual testing across this arc confirmed exactly five visible notifications is the appropriate cap before the stack becomes too large — the existing maximum of five (`NotificationManagerLayoutTest`, untouched by any pass in this arc) remains unchanged.

### 60.2 Boundaries confirmed held for the entire arc

Phase 3C (`ClientResourcePresentationResolver`, the Generic client Resource view, `PENDING_RESYNC`, legacy fallback) remains completely unchanged. Health remains native-backed (`getHealth()`/`getMaxHealth()`); Food remains native-backed with its native 0–20 fill ratio. Mana, Stamina, and Rage retain their Phase 3C presentation-resolver paths (`ClientResourcePresentationResolver.INSTANCE.resolveScalar`). No Resource mutation or packet send was introduced anywhere in the HUD render path (`PlayerResourceService.INSTANCE.spend`/`.restore`/`.applyFull`/`.applyDelta`/`ClientPlayNetworking.send` all confirmed absent by dedicated tests, re-run this pass with identical results). `TotalityMovementHandler` remains unchanged. Food 0–100 and `TotalityFoodItem` were never started. No dormant future Resource definitions were added. No Tooltip item Value contributor/component was added. No full HUD Layout/Reservation API was started (this arc's chat-origin work remains, per its own framing throughout, "a contained vanilla-chat compatibility bridge"). No Mob Health/Mob HUD API was started. No Contextual Interaction API expansion was started.

### 60.3 Final status

- Automated validation: **passed** (144/0/0/0 focused, 1316/0/0/0 full suite, reproduced identically across the datagen/clean-build re-run immediately preceding this closure).
- Dedicated-server validation: **passed** (`Done` reached, no classloading errors, across every pass that introduced or modified a client-only mixin).
- Client smoke validation: **passed** (all five chat mixins + the HUD-visibility gate applied without a Mixin transform error, exercised in-world across multiple passes, no crash).
- User visual validation: **passed** — confirmed complete and correct at GUI Scale 4, including the final input-background correction.
- **No further HUD/chat correction is required before commit and push.**
- **The small HUD cleanup is ready to close.**
