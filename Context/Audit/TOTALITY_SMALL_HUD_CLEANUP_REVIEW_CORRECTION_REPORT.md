# Totality Small HUD Cleanup — Review Correction Report

**Date:** 2026-07-31
**Branch:** `feature/general-resource-api`
**Scope:** narrow review-correction pass on top of the uncommitted small HUD cleanup

---

## 1. Starting branch/HEAD

Branch `feature/general-resource-api`, committed HEAD `69717c6be85b891c6dea0fe7b0751cab4da73b3c` ("Migrate client resource presentation consumers") — unchanged, since the entire small-HUD-cleanup pass (including this correction) remains uncommitted working-tree state. Verified at the start of this pass and confirmed still true at the end (§18).

## 2. Reason for correction pass

Live visual testing of the first small-HUD-cleanup pass exposed two real presentation defects the automated tests could not have caught (they only verify source structure, not rendered pixels — an established, documented limitation of every `*SourceRegressionTest` in this codebase). An independent static review of the same working tree separately found four smaller wording/formatting/test-quality issues. This pass fixes all six together, in place, on top of the existing uncommitted work — it does not restart or discard anything.

## 3. Exact visual issues found by user

1. **Sprite stretching**: the enlarged 96×10 main bars were still `blitSprite`-drawing the original (much smaller, effectively half-resolution-at-this-size) `bar_background.png`/`*_filled.png` sprites stretched up to the new geometry — this read as blurry/low-fidelity, not an acceptable "enlarged presentation." The user was explicit that the original native sprites must remain untouched and must not be visually stretched for the new size.
2. **AC/chat collision**: AC's text (positioned directly above the Health bar, at the top of the left 3-bar stack) sat in the same screen region vanilla chat renders into, and visibly collided with chat text. The user indicated (via screenshot) a preferred temporary relocation: left of the hotbar, low on screen, above the vanilla XP/hotbar row.

The user also explicitly confirmed two things from the first pass are **not** being revisited here: the offhand crowding fix is working, and the Mob Display temporary direct-look HP behavior is acceptable as-is.

## 4. Exact static-review findings

1. `HudValueFormatter.abbreviate` could round a value up to exactly the next tier's threshold (e.g. `999_950` → `999.95k` → rounds to `"1000.0k"` after `%.1f` formatting) instead of promoting to the next suffix (`"1M"`).
2. The implementation report's rarity-disclosure wording (from the prior pass) needed a precision correction: direct-look HP visibility must not introduce any *new* rarity disclosure, but the pre-existing unconditional Rare+ name prefix (which was never gated by combat state in the first place) is unaffected and remains exactly as it was.
3. A comment above `MobHealthBarHud`'s `showHealthBar` declaration claimed the crosshair-target path had "already passed... alive" validation — false: only the `combatTarget` branch inside `getDisplayTarget()` calls `.isAlive()` explicitly; the `crosshairTarget` assignment has no explicit alive check of its own.
4. A test assertion combined two independent regression checks into one `assertFalse(A && B)`, which only fails if *both* conditions regress simultaneously — silently missing a regression in just one of the two.

## 5. Exact production files changed

- `src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java` (further modified)
- `src/main/java/zcylas/totality/client/renderer/hud/MobHealthBarHud.java` (further modified — comment only, no behavior change)
- `src/main/java/zcylas/totality/client/renderer/hud/HudValueFormatter.java` (further modified — rollover fix)
- `src/main/java/zcylas/totality/client/renderer/hud/HudBarLayout.java` — **not touched this pass** (approved geometry constants are unchanged, confirmed by `git status` showing no further edit beyond its original untracked-new-file state)

## 6. Exact test files changed

- `src/test/java/zcylas/totality/client/renderer/hud/HudValueFormatterTest.java` (3 new rollover-promotion tests)
- `src/test/java/zcylas/totality/client/renderer/hud/TotalityHudCleanupSourceRegressionTest.java` (1 weak assertion split into 2; 7 new sentinels added: code-drawn-bar confirmation ×4, AC-relocation confirmation ×2, corrected-comment confirmation ×1)
- `src/test/java/zcylas/totality/api/rpg/resources/client/presentation/Phase3CConsumerMigrationSourceRegressionTest.java` — **not touched this pass** (no Phase 3C-scoped finding in this correction)
- `src/test/java/zcylas/totality/client/renderer/hud/HudBarLayoutTest.java` — **not touched this pass** (geometry unchanged)

## 7. Exact rendering-strategy correction

`TotalityHudRenderer.drawBarSmooth`/`drawBarMirroredSmooth` no longer call `graphics.blitSprite(...)` for the frame or fill at all. Two new small private helpers were added:
- `drawBarFrame(graphics, x, y, bgColor)`: draws a 1px border via the existing `GuiHelper.fillFrame` utility, then fills the interior with a per-resource dark background color.
- `drawBarFill(graphics, fillX, fillY, fillW, fillH, fillColor)`: fills the colored rectangle, plus a top highlight row (`ColorUtils.blend(fillColor, WHITE, 0.45f)`) and a bottom shadow row (`0x44000000`) — the exact same depth treatment `MobHealthBarHud.drawBarSmooth` already uses for mob HP bars, reused here for visual consistency rather than inventing a new style.

Both draw methods' signatures changed from `Identifier fillSprite` to `int fillColor, int bgColor`. Four new named color constants approximate each resource's prior sprite-art color family, reusing the exact hex values `OverviewTab` already uses for the same three resources (`HEALTH_FILL_COLOR = 0xFFCC3333`, `MANA_FILL_COLOR = 0xFF4466CC`, `STAMINA_FILL_COLOR = 0xFF44AA44`) plus a new brown/orange `FOOD_FILL_COLOR = 0xFFCC8833`, each paired with a darker desaturated background variant. Food's right-to-left fill direction is preserved — only the fill rectangle's X anchor differs between the two draw methods now (grows rightward from the lane's left edge vs. leftward from the lane's right edge); a plain rectangle has no texture-space "direction" to flip, unlike the old sprite approach's U-coordinate mirroring. No new texture file was added; `bar_background.png`/`*_filled.png` remain on disk untouched, simply unreferenced by this active path (the dormant `drawBar`/`drawBarMirrored` methods from the original cleanup pass still reference them, per the established "do not remove dormant code" precedent). `TotalityGuiSprites.HUD_HEALTH_FILL`/`HUD_MANA_FILL`/`HUD_STAMINA_FILL`/`HUD_HUNGER_FILL`/`HUD_BAR_BACKGROUND` constants are untouched in `TotalityGuiSprites.java` (not edited this pass).

Approved geometry (frame 96×10, fill 84×5, offset 10×2, edge margin 6, vertical gap 2, bottom margin 2), bar ordering, centered in-bar text, fill directions, smoothing (`SmoothValue`, unchanged durations), and Rage's positional formula are all **unchanged** — confirmed by `HudBarLayout.java` having zero further edits.

## 8. Exact AC relocation

AC's text draw call moved from `leftX, hpY - client.font.lineHeight - 2` (directly above the Health bar, at the top of the left stack — the position that collided with chat) to `leftX, screenH - VANILLA_HOTBAR_HEIGHT - client.font.lineHeight - 2`, where `VANILLA_HOTBAR_HEIGHT = 22` is a new named constant documented as a read-only reference point (vanilla's own stable hotbar sprite height) — it is never used to move, resize, or otherwise touch vanilla's actual hotbar rendering, only to place this text a small gap above it. `leftX` (the same X anchor the whole left bar column already uses) is unchanged, keeping AC clearly left of the horizontally-centered vanilla hotbar. The displayed value (`calculateClientAC(client)`), text content (`"AC " + ac`), color (`0xFF00CCFF`), and drop-shadow are all byte-identical to before — only the position changed. No shield-background redesign, no server-authority change.

## 9. Exact formatter correction

`HudValueFormatter.abbreviate` was restructured from three independent `if (value >= threshold)` branches into an iterative tier-promotion loop: it computes the one-decimal-rounded value at the current tier, and if that rounds to `>= 1000.0` *and* a higher tier exists, it promotes to that tier and re-rounds, repeating until the result is either `< 1000.0` or already at the top tier (`B`, which has no higher tier to promote to, matching the "k/M/B" suffix support this formatter documents). Verified: `999_950 → "1M"` (was `"1000.0k"`→`"1000k"` before the fix), `999_950_000 → "1B"` (was `"1000.0M"`→`"1000M"`), while legitimate near-boundary values that don't actually reach 1000 after rounding are unaffected (`999_499 → "999.5k"`, `999_499_000 → "999.5M"`). All previously-passing formatting behavior (`1000→"1k"`, `1500→"1.5k"`, `10000→"10k"`, spaced/compact selection, long-safety) is unchanged.

## 10. Exact documentation/test corrections

- **Rarity wording**: this correction report and the implementation-report addendum (§ below) state precisely: direct-look HP visibility introduces **no new** rarity disclosure; the pre-existing, always-unconditional Rare+ name prefix (never gated by combat state, confirmed unchanged in source both before and after the original cleanup pass) is unaffected.
- **Misleading comment**: `MobHealthBarHud`'s comment above `showHealthBar` no longer claims the crosshair-target path itself performed an explicit alive check. It now states precisely that `isAlive()` is only explicitly called on the `combatTarget` branch inside `getDisplayTarget()`, and that the `crosshairTarget` branch relies on whatever liveness behavior vanilla's own `mc.crosshairPickEntity` raytrace has, which the comment does not claim to have verified. **No behavior changed** — confirmed by a new test asserting `crosshairTarget = ...` still contains no `isAlive()` call.
- **Weak combined assertion**: `resolverValuesStayLongThroughTheActiveDrawPath`'s single `assertFalse(source.contains("(int) staminaView") && source.contains("(int) manaView"))` is now two independent assertions, one per resource, each capable of failing on its own.

## 11. Focused test results

```
./gradlew test --tests "zcylas.totality.client.renderer.hud.*"
./gradlew test --tests "zcylas.totality.api.rpg.resources.client.presentation.Phase3CConsumerMigrationSourceRegressionTest"
```
**HudBarLayoutTest: 15/0/0/0** (unchanged). **HudValueFormatterTest: 16/0/0/0** (+3 rollover tests). **TotalityHudCleanupSourceRegressionTest: 26/0/0/0** (+6 new sentinels, +1 split-assertion test, comment-correction test). **Phase3CConsumerMigrationSourceRegressionTest: 19/0/0/0** (unaffected, re-run to confirm no regression). Combined: **76 tests, 0 failures, 0 errors, 0 skipped.**

## 12. Full-suite results

```
./gradlew test
```
**Tests: 1264, Failures: 0, Errors: 0, Skipped: 0**, across 96 test classes (up from the pre-correction 1254/96 — exactly +10 tests, matching §11's new test counts, 0 new test classes since all edits were to existing files).

## 13. Datagen result

```
./gradlew runDatagen
```
`Caching: total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0`. `git status --short` before/after this run is byte-for-byte identical.

## 14. Clean build result

```
./gradlew clean build
```
**BUILD SUCCESSFUL.** The `:test` task inside this run reproduced the identical **1264/0/0/0** totals from §12.

## 15. `git diff --check` result

Exit code `0`. Only pre-existing/expected CRLF-vs-LF advisory warnings (the 22 already-dirty generated JSON files, plus `MobHealthBarHud.java` and `Phase3CConsumerMigrationSourceRegressionTest.java`, both edited via a line-ending-preserving tool — advisory only, not an error).

## 16. Manual validation performed

`./gradlew runClient` (bounded ~60s run): the client launched, auto-joined the existing "New Testing World" singleplayer dev save (the same dev-convenience auto-join observed in the prior pass), and rendered live in-world for roughly 30 real seconds — multiple GPU buffer resizes as real geometry streamed in, direct evidence the new code-drawn bar rendering path (which runs every frame while the HUD renders) executed repeatedly with no exception or crash traceable to any file this pass touched. The run was cut off by the bounded timeout mid-session (an autosave had just started) rather than exiting via a clean shutdown sequence, but no orphaned process or open port was left behind afterward (verified). The same three pre-existing, unrelated self-test failures (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) were observed, plus the expected offline-environment Mojang-auth 401 — both unrelated to this pass and explicitly out of scope. Full/focused automated test suites (§11/§12), datagen (§13), and clean build (§14) were also run as part of this validation.

## 17. Remaining user validation

**Not performed by this agent** — this environment has no way to send keyboard/mouse input into the running client or capture what it visually renders. The following need a human pass at the keyboard before this correction is considered visually confirmed:
1. Confirm the enlarged bars now visually read as clean, code-drawn Totality HUD bars, not blurry/stretched sprite art.
2. Confirm the current/max values remain centered inside the bars (unchanged formula, but should be re-confirmed against the new visual background).
3. Confirm the offhand remains unobstructed (already accepted from the prior pass — re-check only if the bar-rendering change shifted anything, which it should not have, since geometry is unchanged).
4. Confirm AC now visually sits low-left, above the vanilla XP/hotbar row.
5. Confirm AC no longer collides with chat at GUI Scale 4 in the scenario the user originally screenshotted.
6. Confirm direct-look mob HP still works exactly as accepted from the prior pass.
7. Confirm the Mob Display still looks acceptable overall.
8. Confirm no crash/regression during normal play beyond the ~30-second smoke-test window this agent could observe.

Testing specifically at GUI Scale 4 (the authoritative stress case) per the task's instruction.

## 18. Confirmation nothing was committed or pushed

No `git add`, `git commit`, or `git push` command was run at any point in this pass. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD, identical to §1.

---

## 19. Final-visual-correction addendum (2026-07-31, second pass)

A second round of live visual testing on the §1-18 correction above found the code-drawn bars still didn't read correctly, and AC's relocation still overlapped the left player-bar stack. This addendum documents the resulting rewrite.

### 19.1 Asymmetrical sprite-derived offset discovered in live testing

The §1-18 correction pass replaced `blitSprite` calls with code-drawn rectangles, but kept the *old sprite-era fill geometry as its coordinates*: `FILL_OFFSET_X = 10` (left inset) paired with `FILL_WIDTH = 84` inside a `FRAME_WIDTH = 96` frame — leaving only `96 - 10 - 84 = 2`px on the right. This asymmetry (10px left, ~2px right) was invisible as a problem when it was still literally a sprite's own baked-in offset, but once drawn as flat code rectangles it read as a large empty black block at the left of every bar.

### 19.2 Insufficient 10px height

`FRAME_HEIGHT` was still `10`. Vanilla's default `Font.lineHeight` is `9`; `HudBarLayout.textY`'s formula `(FRAME_HEIGHT - fontLineHeight) / 2` gave `(10-9)/2 = 0` — zero pixels of top margin, so text visually touched the top border.

### 19.3 Overly bright fill treatment

The §1-18 pass's fill used `ColorUtils.blend(fillColor, ColorUtils.WHITE, 0.45f)` for its top highlight, computed at render time from a still-fairly-saturated base fill color (e.g. Health `0xFFCC3333`, unchanged from the very first attempt) paired with a resource-tinted dark background. In live testing this combination read as a glowing button, not a recessed HUD bar.

### 19.4 Incorrect far-left AC anchor

The §1-18 pass moved AC's *Y* position away from the Health-bar-relative spot but left its *X* at `leftX` — the exact same anchor the entire left player-bar column uses. AC therefore still visually overlapped the Health bar's column, just lower on screen, rather than sitting near the hotbar/XP area as the user's screenshot indicated.

### 19.5 New 96×12 geometry

`HudBarLayout.java`:
```
FRAME_WIDTH  = 96      FRAME_HEIGHT = 12   (was 10)
OUTER_BORDER_WIDTH = 1
INNER_FRAME_WIDTH  = 1
TRACK_INSET_X = 3      TRACK_INSET_Y = 3
TRACK_WIDTH  = 90       TRACK_HEIGHT = 6
```
`FILL_WIDTH`, `FILL_HEIGHT`, `FILL_OFFSET_X`, `FILL_OFFSET_Y` no longer exist anywhere in the file (confirmed by a dedicated test checking for the absence of their declarations). Y-position formulas kept their exact prior *structure* (`staminaY = screenH - BOTTOM_MARGIN - FRAME_HEIGHT`; `manaY = staminaY - VERTICAL_GAP - FRAME_HEIGHT`; `healthY = manaY - VERTICAL_GAP - FRAME_HEIGHT`; `foodY = healthY`; `secondaryResourceY = foodY + FRAME_HEIGHT + VERTICAL_GAP`) — only their *values* shifted, purely as a consequence of `FRAME_HEIGHT` changing from 10 to 12: `Stamina = H-14`, `Mana = H-28`, `Health = Food = H-42`, `Rage = H-28`.

### 19.6 Symmetrical 3px track inset

`TRACK_INSET_X = TRACK_INSET_Y = 3`, confirmed symmetric by direct arithmetic in a test: `FRAME_WIDTH - TRACK_INSET_X*2 = 90 = TRACK_WIDTH` and `FRAME_HEIGHT - TRACK_INSET_Y*2 = 6 = TRACK_HEIGHT`. The filled region begins at `barX + TRACK_INSET_X` (`barX+3`) and its rightmost possible pixel is `barX + TRACK_INSET_X + TRACK_WIDTH` (`barX+93`), exactly matching the task's approved bounds.

### 19.7 90×6 track/fill region

`TRACK_WIDTH = 90`, `TRACK_HEIGHT = 6` — both derived arithmetically from the frame size and the symmetric inset, not independently chosen.

### 19.8 Darker recessed bar style

`TotalityHudRenderer` now draws six explicit layers per bar:
1. **Outer border** — `OUTER_BORDER_COLOR = 0xFF0A0A0A` (near-black), 1px, shared by all four bars.
2. **Inner frame** — `INNER_FRAME_COLOR = 0xFF2E2E2E` (muted charcoal), fills the panel behind the track, shared by all four bars.
3. **Track** — `TRACK_COLOR = 0xFF161616` (dark recessed), shared by all four bars — the track no longer carries per-resource tint at all.
4. **Fill** — per-resource, darker/less saturated than before.
5. **Highlight** — per-resource, an explicit named constant (not a runtime blend), restrained rather than bright.
6. **Shadow** — per-resource, an explicit named constant (not a flat translucent overlay), deeper than the highlight.

| Resource | Old fill (§1-18) | New fill | New highlight | New shadow |
|---|---|---|---|---|
| Health | `0xFFCC3333` | `0xFF7A2424` | `0xFF9C3A3A` | `0xFF4A1414` |
| Mana | `0xFF4466CC` | `0xFF28468A` | `0xFF3C5CA8` | `0xFF162A55` |
| Stamina | `0xFF44AA44` | `0xFF2E6E2E` | `0xFF44904A` | `0xFF193A19` |
| Food | `0xFFCC8833` | `0xFF8A5A28` | `0xFFA8753C` | `0xFF553015` |

All twelve resource colors are named constants declared once, at the top of the file, with an inline comment identifying the color family — no unexplained hex values are scattered through the rendering methods themselves (`drawBarFrame`/`drawBarFill` take colors as parameters, never hardcode them).

### 19.9 Hotbar-relative AC placement

```java
int hotbarLeft = screenW / 2 - VANILLA_HOTBAR_HALF_WIDTH; // 91, vanilla's own half hotbar width
int acX = hotbarLeft + AC_HOTBAR_LEFT_INSET;               // +2
int acY = screenH - VANILLA_HOTBAR_HEIGHT - client.font.lineHeight - 2; // unchanged from §1-18
graphics.text(client.font, "AC " + ac, acX, acY, 0xFF00CCFF, true);
```
`VANILLA_HOTBAR_HALF_WIDTH = 91` and `AC_HOTBAR_LEFT_INSET = 2` are new named constants. AC no longer references `leftX` anywhere — confirmed by a test that isolates the AC-drawing block and asserts `leftX` does not appear within it. Value, color (`0xFF00CCFF`), shadow, and content (`"AC " + ac`) are all unchanged from §1-18 — only the X/Y source changed.

### 19.10 Exact automated validation results

Focused: `HudBarLayoutTest` **18/0/0/0** (+3 from §1-18's 15), `HudValueFormatterTest` **16/0/0/0** (unchanged), `TotalityHudCleanupSourceRegressionTest` **34/0/0/0** (+8 from §1-18's 26), `Phase3CConsumerMigrationSourceRegressionTest` **19/0/0/0** (re-confirmed, unaffected — not touched this pass). Full suite: **1274/0/0/0** across 96 test classes. Datagen: `Caching: ... written: 0`; `git status --short` before/after byte-for-byte identical. Clean build: `BUILD SUCCESSFUL`, `:test` reproduced 1274/0/0/0. `git diff --check`: exit 0, only the same pre-existing CRLF advisory warnings (plus the two files already flagged from the prior pass).

Production files changed this pass: `HudBarLayout.java`, `TotalityHudRenderer.java`. `MobHealthBarHud.java` was **not** touched this pass (no MobHealthBarHud finding in this round). Test files changed: `HudBarLayoutTest.java`, `HudValueFormatterTest.java` (one stale constant-name reference fixed), `TotalityHudCleanupSourceRegressionTest.java` (5 stale assertions fixed, 9 new sentinels added). `Phase3CConsumerMigrationSourceRegressionTest.java` not touched this pass.

### 19.11 Remaining user visual validation

Not performed by this agent — no way to see the rendered client in this environment:
- Bars have symmetrical internal padding.
- No large black block remains at the left.
- Bars look recessed rather than bright and flat.
- Text has comfortable vertical spacing.
- AC appears above the left side of the hotbar/XP area.
- AC does not overlap Health.
- AC does not overlap typed chat at GUI Scale 4.
- Offhand remains clear.
- Food remains mirrored.
- Direct-look Mob HP still works.

### 19.12 Confirmation nothing was committed or pushed (this addendum)

No `git add`, `git commit`, or `git push` command was run at any point during this final-visual-correction pass. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD.

## 20. Final player-HUD and vanilla-chat compatibility correction (third pass, 2026-07-31)

### 20.1 Exact visual issues found by user

1. **Border still too thick.** §19's `TRACK_INSET_X = TRACK_INSET_Y = 3` left a visible charcoal bezel between the outer border and the track — a three-layer treatment (border, solid inner-frame panel, inset track) that still read as a thick border at live GUI Scale 4.
2. **Text not perfectly contained.** Current/max numbers, centered against the full 96px frame width, were not perfectly contained within the bar's usable interior once the border/track relationship changed — and the vertical centering formula did not account for the text's own rendered shadow, which can extend 1px below the glyph.
3. **Chat still overlaps Health/Mana; AC still too close to the XP bar.** §19's hotbar-relative AC relocation stopped AC from sitting on top of Health, but vanilla chat itself was never moved — it still renders into the same screen region as the left resource-bar stack, and AC's own Y still sat close enough to the vanilla XP bar to look cramped.

### 20.2 Exact fix — bar geometry

| | §19 (second pass) | §20 (this pass) |
|---|---|---|
| Border | `OUTER_BORDER_WIDTH=1` + `INNER_FRAME_WIDTH=1` (solid charcoal panel fill) | `BORDER_WIDTH=1` only — no separate panel layer |
| Interior offset | `TRACK_INSET_X=TRACK_INSET_Y=3` | `INTERIOR_OFFSET_X=INTERIOR_OFFSET_Y=1` |
| Usable region | `TRACK_WIDTH=90, TRACK_HEIGHT=6` | `INTERIOR_WIDTH=94, INTERIOR_HEIGHT=10` |
| Layer count | 3 (border, panel, track) | 2 (border, interior) |

Required arithmetic, confirmed by test: `96 - 1 - 1 = 94`, `12 - 1 - 1 = 10`. `TotalityHudRenderer.drawBarFrame` now draws exactly two layers: `GuiHelper.fillFrame` for the 1px `OUTER_BORDER_COLOR` ring around the full 96×12 frame, then one `graphics.fill(...)` for the `TRACK_COLOR` interior at `barX+1, barY+1`, 94×10. `drawBarFill` (fill + 1px highlight + 1px shadow) is otherwise unchanged, now scoped to the 94×10 interior — highlight/shadow rows are still derived directly from `INTERIOR_OFFSET_Y`/`INTERIOR_HEIGHT`, so they cannot spill outside it. The resource fill/highlight/shadow palette accepted in §19 (Health `0xFF7A2424`/`0xFF9C3A3A`/`0xFF4A1414`, etc.) was kept unchanged — the border-to-interior contrast remains clear without the old panel layer, so no readability adjustment was made.

### 20.3 Exact fix — text placement

`HudBarLayout.textX`/`textY` are now the one shared formula both `drawBarSmooth` and `drawBarMirroredSmooth` call:
```
textX = interiorX + (INTERIOR_WIDTH - textWidth) / 2
textY = interiorY + (INTERIOR_HEIGHT - (fontLineHeight + 1)) / 2
```
`textY`'s `fontLineHeight + 1` treats the shadow's extra 1px as part of the text's visual height, not just the glyph — for vanilla's default `fontLineHeight=9`, this evaluates to exactly `interiorY` (zero remaining margin: `9+1=10=INTERIOR_HEIGHT`, an exact fit). `textX` centers against the full fixed interior (never the currently-filled width), so text stays stationary as fill % animates. `HudValueFormatter.display`'s width budget changed from the old `TRACK_WIDTH` (90) to `INTERIOR_WIDTH` (94).

### 20.4 Exact fix — AC clearance

One new named constant, `AC_ABOVE_XP_CLEARANCE = 6`: `acY = screenH - VANILLA_HOTBAR_HEIGHT - client.font.lineHeight - 2 - AC_ABOVE_XP_CLEARANCE`. X anchor, value, color, and shadow are unchanged from §19.

### 20.5 Exact fix — vanilla-chat bottom reservation (new scope this pass)

**Single source of truth.** `HudBarLayout` gained `CHAT_EXTRA_CLEARANCE = 4` and `chatBottomReservation()`:
```
leftStackHeight()       = 3*FRAME_HEIGHT + 2*BAR_GAP = 3*12 + 2*2 = 40
chatBottomReservation() = leftStackHeight() + BOTTOM_MARGIN + CHAT_EXTRA_CLEARANCE = 40+2+4 = 46
```
(`VERTICAL_GAP` renamed `BAR_GAP` to match this formula — no behavior change.) A new public class `TotalityChatLayout` (same package as `HudBarLayout`, so it can read the package-private `chatBottomReservation()`) is the single call site: `extraBottomReservation()` returns 46 while `client.player != null && !client.gui.hud.isHidden()` (mirrors `TotalityHudRenderer`'s own gate exactly), else `0` — vanilla behavior is untouched at menus, with no world loaded, or F1-hidden.

**Exact MC 26.2 classes/methods inspected** (decompiled/`javap`'d from the real `minecraft-merged-deobf-26.2.jar`, per the task's explicit "do not assume older class names or signatures" instruction — nothing here was guessed or carried over from an older Minecraft version):

| Class | Method | Literal targeted | Occurrences in method | Governs |
|---|---|---|---|---|
| `ChatComponent` | private `extractRenderState(ChatGraphicsAccess, int, int, DisplayMode)` | `40` | 1 (unique) | Rendered message lines, restricted-prompt background, queued-message indicator, **and** hover/click hit-testing (`captureClickableText` routes through this same private method) |
| `ChatScreen` | `init()` | `12`, ordinal 0 | 2 (2nd is the EditBox's own height, untouched) | Input box Y |
| `CommandSuggestions` | `showSuggestions(boolean)` | `12` | 1 (unique) | Suggestion popup Y (independently hardcoded from `ChatScreen`'s own `12` — does not read the input box's live Y) |
| `CommandSuggestions` | `extractUsage(GuiGraphicsExtractor)` | `27` | 1 (unique; other `12`s in-method are line-height stride/padding, untouched) | Command-usage hint box Y |

**Why one injection covers both rendering and interaction for chat messages.** `ChatComponent`'s public `extractRenderState` (called for on-screen rendering) and `captureClickableText` (called for hover/click hit-testing) are both thin wrappers that immediately delegate into the exact same private 4-arg `extractRenderState` — the one method that computes the `40`-based bottom anchor. A `@ModifyConstant` on that single private method therefore moves rendering and interaction together *by construction*; there is no separate coordinate-conversion step that could be left un-updated, satisfying the task's explicit "a mixin that only translates rendering without updating interaction coordinates is unacceptable" requirement.

**Four new mixins**, all `@ModifyConstant` returning `original + TotalityChatLayout.extraBottomReservation()`, package `zcylas.totality.mixin.client.chat`, registered only under `totality.mixins.json`'s `"client"` array (never the common `"mixins"` array, since every target class is client-only): `ChatComponentBottomMarginMixin`, `ChatScreenInputPositionMixin`, `CommandSuggestionsListPositionMixin`, `CommandSuggestionsUsagePositionMixin`.

**Confirmation of unchanged vanilla chat behavior.** No mixin touches chat width, opacity, background opacity, `MAX_CHAT_HISTORY`, or message wrapping (verified by a dedicated source-regression test); no mixin uses `@ModifyArg` or touches an X-coordinate constant — only vertical position shifts.

### 20.6 Exact files changed this pass

Production: `HudBarLayout.java`, `TotalityHudRenderer.java`, `TotalityChatLayout.java` (new). Mixin (all new): `ChatComponentBottomMarginMixin.java`, `ChatScreenInputPositionMixin.java`, `CommandSuggestionsListPositionMixin.java`, `CommandSuggestionsUsagePositionMixin.java`. Mixin config: `totality.mixins.json` (+4 entries under `"client"`). `MobHealthBarHud.java` **not** touched. Tests: `HudBarLayoutTest.java` (rewritten), `HudValueFormatterTest.java` (stale constant references fixed), `TotalityHudCleanupSourceRegressionTest.java` (stale interior/AC assertions updated), `ChatCompatibilityCorrectionSourceRegressionTest.java` (new). `Phase3CConsumerMigrationSourceRegressionTest.java` not touched.

### 20.7 Automated validation results

Focused: `HudBarLayoutTest` **23/0/0/0** (+5 from §19's 18), `HudValueFormatterTest` **16/0/0/0** (unchanged), `TotalityHudCleanupSourceRegressionTest` **35/0/0/0** (+1 net from §19's 34), `ChatCompatibilityCorrectionSourceRegressionTest` **12/0/0/0** (new), `Phase3CConsumerMigrationSourceRegressionTest` **19/0/0/0** (re-confirmed, unaffected). Full suite: **1292/0/0/0** across **97** classes (was 1274/96). Datagen: `written: 0`, byte-identical `git status` before/after. Clean build: successful, reproduced 1292/0/0/0. `git diff --check`: exit 0, only the same pre-existing CRLF advisory warnings.

**Dedicated-server classloading check (new this pass, since new client-only mixins were introduced).** `./gradlew runServer` reached `Done (0.339s)!` and ran normally — no `ClassNotFoundException`/`NoClassDefFoundError`. The Mixin subsystem log line reports `Env=SERVER`, and the server never attempts to classload `ChatComponent`/`ChatScreen`/`CommandSuggestions`, confirming the four chat mixins are never candidates for server-side application. Pre-existing `ProvisionerEntityBackedSmokeTest`/`OffhandAttackVerification` self-test failures appeared identically to the client run — confirmed pre-existing, unrelated to this pass.

**Client smoke test.** `./gradlew runClient` applied all four chat mixins with no Mixin transform error (a bad `@ModifyConstant` target/ordinal throws at class-load time), joined the dev world, and ran for several minutes with no crash — including a logged in-world chat message rendered through the now-mixed-into `ChatComponent` path.

### 20.8 Remaining user visual validation

Not performed by this agent: 1px border reads as thin; no charcoal frame visible between border and interior; text fully contained within the 94×10 interior, no clipping; text visually centered and stationary as fill % changes; AC clearly above the hotbar/XP area, not overlapping it or Health; ordinary chat renders above the resource-bar stack; ChatScreen input box moves up with messages when chat opens; suggestion popup and usage hint move with the input box; scrollbar/hover/click still target the (now higher) visible text correctly; scrolling chat history still works; offhand indicator remains clear; direct-look Mob HP still works; F1 restores vanilla chat position; no crashes during normal play.

### 20.9 Confirmation nothing was committed or pushed (this addendum)

No `git add`, `git commit`, or `git push` command was run during this pass. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD.

## 21. Next final correction pass (fourth pass, 2026-07-31)

### 21.1 Exact issues found by user

1. **Text one pixel too high.** §20's mathematical centering (zero remaining margin, `fontLineHeight+1` treated as the full glyph+shadow footprint) still read as one pixel too high in the live client.
2. **Chat message/input origin mismatch.** The chat message/history region moved upward, but the input origin didn't move "consistently" with it — messages sat too high with a large empty gap above the input line.
3. **Persistent HUD visible on menu screens.** Totality's Health/Mana/Stamina/Food/AC/Rage stayed visible behind `InventoryScreen` and other ordinary menus, unlike vanilla's own hotbar.
4. **Bar colors still too bright.** The code-drawn bars still didn't match the darker appearance of the original bar sprites.

### 21.2 Fix — VALUE_TEXT_Y_OFFSET

One new named constant, applied strictly after the existing (unchanged) mathematical centering:
```
VALUE_TEXT_Y_OFFSET = 1
textY = baseTextY + VALUE_TEXT_Y_OFFSET     // baseTextY is §20's exact, unchanged formula
```
Containment proof (documented in `HudBarLayout`'s javadoc and a dedicated test): `fontLineHeight` (9) reserves one row for inter-line spacing that a single line of digit/no-descender bar text never inks — real single-line glyph ink height is `fontLineHeight - 1` (8). Text plus shadow, modeled this way, spans exactly 9 rows from the new `textY`, landing flush with the interior's last row without touching the 1px border.

### 21.3 Fix — sampled original-sprite palette

Sampled once during development (PowerShell + `System.Drawing`, never at runtime) from the actual pre-cleanup sprite PNGs under `src/main/resources/assets/totality/textures/gui/sprites/hud/` — all confirmed byte-identical/untouched by `git status`. Method: average every fully-opaque pixel in one representative row per band (border row / track row in `bar_background.png`; top row = highlight, bottom row = fill in each `*_filled.png`); shadow = the single darkest genuinely-sampled pixel in each fill sprite's bottom row (a real pixel from the art — the sprites have their own horizontal edge-darkening — not a computed derivative).

| Constant | §20 (approximated) | §21 (sampled) |
|---|---|---|
| `OUTER_BORDER_COLOR` | `0xFF0A0A0A` | `0xFF030303` |
| `TRACK_COLOR` | `0xFF161616` | `0xFF2D2C2C` |
| `HEALTH_FILL/HIGHLIGHT/SHADOW` | `0xFF7A2424`/`0xFF9C3A3A`/`0xFF4A1414` | `0xFF720A0B`/`0xFF950E0E`/`0xFF350605` |
| `MANA_FILL/HIGHLIGHT/SHADOW` | `0xFF28468A`/`0xFF3C5CA8`/`0xFF162A55` | `0xFF02234E`/`0xFF032859`/`0xFF001C3E` |
| `STAMINA_FILL/HIGHLIGHT/SHADOW` | `0xFF2E6E2E`/`0xFF44904A`/`0xFF193A19` | `0xFF133203`/`0xFF143504`/`0xFF112F02` |
| `FOOD_FILL/HIGHLIGHT/SHADOW` | `0xFF8A5A28`/`0xFFA8753C`/`0xFF553015` | `0xFF482601`/`0xFF4A2702`/`0xFF462501` |

Geometry (96×12, 1px border, 94×10 interior, code-drawn, Food mirrored) is completely unchanged — only the fourteen color values.

### 21.4 Fix — chat single-origin correction

**Root-cause finding.** All four §20 mixins already applied the identical `extraBottomReservation()` (46) to their own vanilla literal, verified algebraically scale-invariant (adding inside the `(guiHeight-C)/scale` numerator produces an exact `C`-pixel screen shift regardless of chat scale) — no double-application, no missing application, no sign error existed at the arithmetic level. The real cause was architectural: `ChatComponent`'s message anchor and `ChatScreen`'s input anchor each independently perturbed two *unrelated* vanilla constants (`BOTTOM_MARGIN`=40 vs. input's own hardcoded 12), which already sit 28px apart in stock vanilla — invisible there because that gap sits above the (hidden-while-typing) hotbar, but glaring once the whole block floats 46px higher with nothing nearby.

**Fix.** `TotalityChatLayout` gained a real single-origin chain:
```
CHAT_INPUT_BOTTOM_MARGIN = 12          MESSAGE_TO_INPUT_GAP = 8 (vanilla's own MESSAGE_BOTTOM_TO_MESSAGE_TOP)
inputBottomMargin()      = CHAT_INPUT_BOTTOM_MARGIN + extraBottomReservation()
messageBottomMargin()    = inputBottomMargin() + MESSAGE_TO_INPUT_GAP      // reuses, does not re-derive
usageHintBottomMargin()  = 27 + extraBottomReservation()                  // unchanged base, not reported broken
```
`ChatComponentBottomMarginMixin` now returns `messageBottomMargin()` directly (no longer `original+reservation`); `ChatScreenInputPositionMixin` and `CommandSuggestionsListPositionMixin` both now return `inputBottomMargin()` — the literal same call, so input and the suggestion popup can never drift apart. The message-to-input gap shrinks from the incidental 28px to a genuine, vanilla-sourced 8px. A dedicated test confirms `messageBottomMargin()` never calls `extraBottomReservation()` itself — only reachable via `inputBottomMargin()`, structurally guaranteeing single application.

### 21.5 Fix — persistent HUD menu-screen gating

**Vanilla inspected.** `GameRenderer.extract` → `Gui.extractRenderState(tracker, renderHud, renderScreen)`, where `renderHud` is gated only on "is a level loaded" (decompiled from `minecraft-merged-deobf-26.2.jar`) — there is no vanilla flag for "screen wants HUD hidden." Vanilla's hotbar keeps rendering under every screen; opaque screens (`InventoryScreen`) simply draw over it. Totality's bars, in the screen corners, were never covered the same way.

**Fix.** New `TotalityHudRenderer.shouldRenderPersistentPlayerHud(Minecraft client)`:
```java
if (client.player == null || client.gui.hud.isHidden()) return false;
Screen screen = client.gui.screen();
return screen == null || screen instanceof ChatScreen;
```
Replaces the old two-condition early return at the top of the render lambda, gating everything drawn afterward: Health, Mana, Stamina, Food, AC, Rage, the offhand indicator, `MagicContextHud`, `AbilityContextHud`. Deliberately not touching (and not referenced anywhere in `TotalityHudRenderer.java`, confirmed by test): `NotificationManager`, `MobHealthBarHud`, `CombatTextRenderer`, `RestHud`, `QuestTrackerHud`, `CastBarHud`, boss bars, status effects, subtitles. No vanilla `Gui`/`Hud`/`GameRenderer` class is mixed into.

### 21.6 Exact files changed this pass

Production: `HudBarLayout.java`, `TotalityHudRenderer.java`, `TotalityChatLayout.java`. Mixin (all modified, none new): `ChatComponentBottomMarginMixin.java`, `ChatScreenInputPositionMixin.java`, `CommandSuggestionsListPositionMixin.java`, `CommandSuggestionsUsagePositionMixin.java`. Mixin config unchanged (already complete from §20). `MobHealthBarHud.java` not touched. Tests: `HudBarLayoutTest.java`, `TotalityHudCleanupSourceRegressionTest.java`, `ChatCompatibilityCorrectionSourceRegressionTest.java` (all modified); `PersistentHudVisibilitySourceRegressionTest.java` (new, 8 tests).

### 21.7 Automated validation results

Focused: `HudBarLayoutTest` **25/0/0/0** (+2 from §20's 23), `TotalityHudCleanupSourceRegressionTest` **37/0/0/0** (+2 from §20's 35), `ChatCompatibilityCorrectionSourceRegressionTest` **16/0/0/0** (+4 from §20's 12), `PersistentHudVisibilitySourceRegressionTest` **8/0/0/0** (new), `HudValueFormatterTest`/`Phase3CConsumerMigrationSourceRegressionTest` unchanged (16/0/0/0, 19/0/0/0). Full suite: **1308/0/0/0** across **98** classes (was 1292/97). Datagen: `written: 0`, byte-identical `git status` before/after. Clean build: successful, reproduced 1308/0/0/0. `git diff --check`: exit 0, same pre-existing CRLF warnings.

**Dedicated-server check.** `./gradlew runServer` reached `Done (0.330s)!`, no classloading errors — chat mixins unchanged in registration (still `"client"`-only); the new `Screen`/`ChatScreen` imports in `TotalityHudRenderer` are standard client-side classes already reachable elsewhere in that file.

**Client smoke test.** `./gradlew runClient` applied all four chat mixins with no transform error, ran ~2 minutes with no crash, exercised `CommandSuggestions.extractUsage` (several "Unknown or incomplete command" chat messages), clean shutdown.

### 21.8 Remaining user visual validation

Not performed by this agent: text visually centered with balanced top/bottom spacing; shadow inside the bar; colors match darker original-sprite appearance; borders thin; Food mirrored; messages sit immediately above input with no large gap; input above the resource stack; suggestions attached to input; scrollbar aligned; click/hover matches visible text; normal gameplay and ChatScreen show the HUD; Inventory/creative/container screens hide it; F1 hides it; offhand clear; AC clear of Health/XP; direct-look Mob HP works; no crashes.

### 21.9 Confirmation nothing was committed or pushed (this addendum)

No `git add`, `git commit`, or `git push` command was run during this pass. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD. Phase 3C, the temporary direct-look Mob Display behavior, and the decision not to add dormant Resource definitions or Tooltip item Value all hold unchanged.

## 22. Final ChatScreen visual correction (fifth/micro pass, 2026-07-31)

### 22.1 Exact issue found by user

Live GUI Scale 4 validation confirmed messages, input origin, and the resource HUD were all correctly positioned — but a full-width translucent black strip remained rendered at the screen's *original* bottom edge, separate from the moved `EditBox`.

### 22.2 Cause

`ChatScreen.extractRenderState(GuiGraphicsExtractor, int, int, float)` (decompiled and confirmed against `minecraft-merged-deobf-26.2.jar`) draws, as its very first statement — before messages, before the `EditBox` widget itself is rendered:
```
graphics.fill(2, this.height - 14, this.width - 2, this.height - 2, <vanilla background color>)
```
This is the black strip — a completely separate draw call from the (borderless, `setBordered(false)`) `EditBox`. `ChatScreen.extractBackground(...)` — a distinct method the task suspected might own it — is confirmed to be an empty no-op in this version; the background lives entirely inside `extractRenderState`. §57's `ChatScreenInputPositionMixin` only ever redirected the `12` inside `ChatScreen.init()` (the `EditBox`'s own construction); this separate `fill` call was never touched, so it kept rendering at the true, unshifted bottom edge.

**Old (unshifted) coordinates:** `x1=2, y1=height-14, x2=width-2, y2=height-2` — a 12px-tall rectangle sitting exactly 2px above the pre-§57 `EditBox`'s own unshifted bounds (`14=12+2`, `2=0+2`).

### 22.3 Fix — same shared origin as the EditBox

Two of the fill's five arguments needed to move: `14` (unique in-method) and the *third* occurrence of int `2` (ordinal 2 — the first two are the fill's left/right X margins, which the task explicitly required stay untouched). New mixin `ChatScreenInputBackgroundMixin`, two `@ModifyConstant` handlers on `ChatScreen.extractRenderState(GuiGraphicsExtractor, int, int, float)`, registered under `totality.mixins.json`'s `"client"` array alongside the existing four.

`TotalityChatLayout` gained two new methods, both built on `inputBottomMargin()` — the exact same origin the `EditBox` itself already uses:
```
inputBackgroundTopMargin()    = inputBottomMargin() + 2     // INPUT_BACKGROUND_ABOVE_EDIT_BOX
inputBackgroundBottomMargin() = inputBottomMargin() - 12 + 2  // - VANILLA_EDIT_BOX_HEIGHT + INPUT_BACKGROUND_ABOVE_EDIT_BOX
```
At `R=0` this reproduces vanilla exactly (`14`, `2`); at `R=46`, top margin `60`, bottom margin `48` — the rectangle's 12px height is unchanged, only translated. Neither method calls `extraBottomReservation()` directly (only `inputBottomMargin()` does), confirmed by a dedicated test — the background is structurally guaranteed to track the box it sits behind. Width, color, opacity, horizontal margins, and height are all untouched.

### 22.4 Exact files changed this pass

Production: `TotalityChatLayout.java`. Mixin (new): `ChatScreenInputBackgroundMixin.java`. Mixin config: `totality.mixins.json` (+1 entry). Nothing else touched — confirmed by `git status`. Tests: `ChatCompatibilityCorrectionSourceRegressionTest.java` (+8 tests).

### 22.5 Validation results

Focused: `ChatCompatibilityCorrectionSourceRegressionTest` **24/0/0/0** (+8 from §21's 16); all focused tests **144/0/0/0** total (was 136). Full suite: **1316/0/0/0** across **98** classes (unchanged class count). Datagen: `written: 0`, byte-identical. Clean build: reproduced 1316/0/0/0. `git diff --check`: exit 0, same pre-existing warnings.

**Dedicated-server check.** `./gradlew runServer` reached `Done (0.354s)!`, no classloading errors — the new mixin is `"client"`-only.

**Client smoke test.** `./gradlew runClient` applied all five chat mixins (including the new ordinal-2-qualified one) with no transform error, exercised chat/command-suggestion rendering, clean shutdown.

### 22.6 Remaining user visual validation

Not performed by this agent: the black strip no longer appears at the true bottom edge; a matching background is visible directly behind the (correctly positioned) input box, same size/color/opacity; background, EditBox, cursor, input text, and suggestions remain aligned; message/suggestion positioning unaffected; no crashes.

### 22.7 Confirmation nothing was committed or pushed (this addendum)

No `git add`, `git commit`, or `git push` command was run during this pass. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD. Bar geometry, bar colors, AC placement, chat message placement, chat input placement, command-suggestion placement, Mob Display behavior, and Phase 3C behavior are all unchanged — this pass touched exactly one previously-unaddressed vanilla draw call.

## 23. Final closure (2026-07-31)

User completed final in-world visual validation at GUI Scale 4 and confirmed everything now appears correct, including the §22 input-background fix — the black strip is gone. No further correction requested. Full final-accepted-behavior summary lives in the implementation report's §60 (bars, AC, chat, HUD visibility, Mob Display, notifications, boundaries) — this entry records the review-report side of closure without repeating it.

**Final validation totals (re-run immediately before commit):** Focused HUD/chat/Phase-3C **144/0/0/0**; full suite **1316/0/0/0** across 98 classes; datagen `written: 0`, byte-identical; clean build reproduced 1316/0/0/0; `git diff --check` exit 0; dedicated server `Done`, no classloading errors — all identical to the accepted baseline from §22, confirming no regression was introduced by this closure pass itself (this pass only appended documentation, it made no production/test change).

**Automated validation: passed. Dedicated-server validation: passed. Client smoke validation: passed. User visual validation: passed. No further HUD/chat correction is required before commit and push. The small HUD cleanup is ready to close.**

No `git add`, `git commit`, or `git push` command was run as part of writing this closure section — commit and push happen as a separate, subsequent step of the same finalization task, after the review bundle is rebuilt one final time and the task-owned file list is re-verified.
