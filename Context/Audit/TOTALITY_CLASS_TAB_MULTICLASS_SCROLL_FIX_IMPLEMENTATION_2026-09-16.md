# Class Tab — Multiclass Owned-Class List Scroll Fix — Implementation Report

**Date:** 2026-09-16
**Branch:** `feature/general-resource-api`
**Baseline commit:** `ab398b17dcce74a589d75dbb6946d4fc62612125` ("feat(resources): migrate rage to generic resource authority") — confirmed via `git rev-parse HEAD` before any edit, and unchanged throughout (nothing committed).

This is a small, surgical follow-up to the already-manually-tested Class Tab "+" level-up / per-class subclass work (see `TOTALITY_CLASS_TAB_QUICK_LEVEL_UP_IMPLEMENTATION_2026-09-16.md`). It does **not** redesign the Class Tab, and does **not** touch the separate reset/showclass → stale-Rage-resource issue found during manual testing — that remains a distinct follow-up task.

## 1. Baseline

`git status --short` at task start: 129 dirty paths, none staged — identical in composition to the state left at the end of the prior class-tab task (the "+" button + per-class subclass migration work, all still uncommitted). `ClassTab.java` was already dirty from that prior work; its diff at task start (before this task's own edits) is captured verbatim in `ClassTab.pre-existing-before-this-task.diff` in the review ZIP, for reference. This file is **not** foreign/unrelated pre-existing work requiring exclusion — it is this same continuing Class Tab effort — so no partial-staging concern applies here, unlike `PlayerClassComponent.java`/`ClassLevelUpRegistry.java` in the prior task's report.

No other file was touched by this task.

## 2. Root cause

`drawProgressionPanel`'s right column drew the owned-class list (added by the prior "+" button task) with an unconditional cursor advance and **no scroll offset or clamped viewport at all** — every row was drawn at `rcy`, which only ever increased, with a single `screen.sc(g, rightX, topY, rightW, clipH)` covering the *entire* right column (header, SPEND CLASS POINT button, and the list together) for the whole method's duration. With four or more owned classes, rows past the second or third simply rendered below the bottom of that clip region with no way to scroll down to them — their "+" buttons existed and had correctly-computed hitboxes, but those hitboxes sat outside the visible area with no input path that could ever reach them.

This is exactly the class of gap the LEFT column (weapon/armor/save proficiencies) had already solved a few lines above in the same method, via `progScroll` — a plain scroll offset subtracted from that column's own drawing cursor (`int lcy = topY + PAD - progScroll;`), inside its own `sc()`/`esc()` pair, adjusted by `mouseScrolled` when the mouse hovers that column's cached bounds. The right column's list never got the same treatment when it was added.

## 3. Fix — mirrors the left column's existing pattern

- **New scroll field**, `mcListScroll`, following `progScroll`'s exact shape (plain `int`, reset in `onOpen`, subtracted from a drawing cursor).
- **New viewport fields**, `mcListPanelX/Y/W/H`, following `progPanelX/Y/W/H`'s exact shape — but scoped to *only* the list portion of the right column (below the CLASS LEVEL header / SPEND CLASS POINT button, which never scroll and keep their own unchanged, un-scrolled `sc()`/`esc()` pair exactly as before).
- **New content-height measurement**, `measureOwnedClassListContentHeight()` — a pure, side-effect-free function that mirrors, term for term, the vertical advances the draw loop performs (row height, optional subclass line, per-row gap), so the scroll ceiling can never drift from what is actually rendered.
- **Clamping in two places**, matching the task's explicit requirement for top-and-bottom clamping (which `progScroll` itself does not have — it only clamps at zero, a pre-existing, unrelated gap this task does not touch):
  - In `draw()`, every frame, before the list is drawn: `mcListMaxScroll = Math.max(0, measureOwnedClassListContentHeight() - listH); mcListScroll = Math.clamp(mcListScroll, 0, mcListMaxScroll);` — this means a content-height change (leveling a class, gaining a subclass, reopening the tab on a different character) is corrected on the very frame it happens, not one frame late.
  - In `mouseScrolled()`, on every wheel tick: `mcListScroll = Math.clamp(mcListScroll - amount, 0, mcListMaxScroll);`.
- **Off-viewport click exclusion**: `drawQuickLevelButton` now checks `boolean fullyVisible = y >= mcListPanelY && y + size <= mcListPanelY + mcListPanelH;` and stores `enabled && fullyVisible` in the button's record — a row scrolled outside the list's own clip region is still drawn (harmlessly invisible, since the GL scissor already clips the actual pixels) at its raw off-screen coordinates, but can never be registered as clickable there. `mouseClicked` itself needed **zero changes** — it already only acts on `btn.enabled()`, which now also encodes visibility.
- **Independent scroll regions**: `mouseScrolled` checks the multiclass list's (narrower) viewport *before* the (wider) progression-panel region used for `progScroll` — since the list's viewport is a strict geometric subset of the panel's overall bounds, checking it first is what makes hovering the right list scroll only that list, without needing to narrow or otherwise touch `progPanelX/Y/W/H`'s own existing assignment (still set exactly as before, in `drawCenterColumn`).

No new scrolling framework was introduced; no reusable/shared scroll-widget abstraction was created. The four new fields and one new pure helper method are the entire addition.

## 4. Files changed

- `src/main/java/zcylas/totality/screen/character/tabs/ClassTab.java` — the only file modified.
- `src/test/java/zcylas/totality/screen/character/tabs/ClassTabMulticlassScrollSourceRegressionTest.java` — new test file.

No class progression, subclass storage, networking, resource, or server-authority code was touched.

## 5. How render clipping and click bounds stay synchronized

Both the drawing calls and the stored `QuickLevelButton` hitboxes for a given frame are produced from the *same* `mcy` cursor variable inside the *same* loop iteration — there is no second, independently-maintained coordinate system for hit-testing. The GL scissor rect (`mcListPanelX/Y/W/H`) determines what is visually drawn; the `fullyVisible` check (using the same `mcListPanelY`/`mcListPanelH` fields the scissor was set from) determines what remains clickable. Both draw from the identical set of fields computed once at the top of the list-rendering block each frame, so there is no path by which rendering and click-eligibility could disagree.

## 6. Tests

`ClassTabMulticlassScrollSourceRegressionTest` (10 new source-text sentinels — `ClassTab` is a client GUI class needing a bootstrapped `Minecraft`/`Font` to render or click-test, unavailable under plain JUnit, the same constraint already documented by `Phase3CConsumerMigrationSourceRegressionTest`/`ClassTabQuickLevelUpSourceRegressionTest` elsewhere in this suite):

1. The list maintains its own scroll offset field (`mcListScroll`).
2. The drawing cursor is derived from that offset before any row is drawn.
3. Scrolling is bounded on both ends, both in `draw()` and in `mouseScrolled()`.
4. The scroll ceiling is recomputed from measured content height every frame.
5. Rendering and click positioning use the same scrolled cursor (`mcy`) for both the single-class and multiclass button call sites.
6. Off-viewport buttons are excluded from click handling (`fullyVisible` check, folded into the stored `enabled` flag).
7. The multiclass list's viewport is checked *before* the wider left-column region in `mouseScrolled`, so the two scroll independently.
8. `onOpen` resets the multiclass scroll alongside every pre-existing scroll field.
9. The `AddClassLevelPayload` click path is textually unchanged, and no `SelectSubclassPayload` reference was introduced.
10. The left column's own scroll field, `mouseScrolled` branch, and cursor computation are all still present and untouched.

The pre-existing `ClassTabQuickLevelUpSourceRegressionTest` (5 tests, from the prior task) was re-run and still passes unmodified — its own assertions about button count, enablement wiring, and the absence of per-class branching are unaffected by this change.

## 7. Validation results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **PASS** |
| `./gradlew compileTestJava` | **PASS** |
| Focused ClassTab/class-progression/subclass test classes | **PASS** (all green) |
| `./gradlew test` (full suite) | **PASS** — 1588 tests, 0 failures, 0 errors |
| `./gradlew clean build` | **PASS** (`BUILD SUCCESSFUL`) |
| `git diff --check` | **PASS** — no whitespace errors (only benign CRLF-normalization warnings) |

No networking/component registration was touched, so a bounded dedicated-server startup was not required for this change (client-GUI-only) and was not run.

## 8. Limitations

- The LEFT column's own `progScroll` still has no upper-bound clamp (`Math.max(0, ...)` only) — a pre-existing characteristic, not touched or "fixed" here, since it was explicitly out of scope and not reported as broken.
- The measurement helper (`measureOwnedClassListContentHeight`) must be kept in sync by hand with the draw loop's own vertical advances if that loop's layout constants ever change — the same maintenance burden the LEFT column's own scrolling already has implicitly (it has no separate measurement function at all, since it never clamped an upper bound to begin with). This is an accepted, minimal cost of adding real two-sided clamping, and is a natural target for consolidation whenever the Class Tab's temporary UI is eventually replaced by a dedicated Class Screen.
- This fix is scoped exclusively to the right-side owned-class list's scrolling. It does not address the separately-tracked reset/showclass → stale-Rage-resource issue.

## 9. Manual test checklist (Stefan)

1. Build a character with four owned classes (multiclass across four different classes).
2. Confirm the classes that were previously hidden below the visible box can now be reached by scrolling the mouse wheel while hovering the right-side owned-class list.
3. Click "+" on a class that was previously off-screen, after scrolling it into view — confirm only that specific class levels up.
4. Scroll back to a different class and level it via its own "+" — confirm it, and only it, advances.
5. Verify that clicking at a screen position where an off-screen "+" would geometrically exist (e.g., right below the SPEND CLASS POINT button, before scrolling down) does nothing.
6. Verify the LEFT-side proficiency list still scrolls correctly, and that scrolling it does not move the right-side list (and vice versa).
7. Spend all available Class Levels — confirm every currently-visible "+" button becomes visually disabled and non-interactive.
8. (Separate, deferred follow-up — not part of this fix) Dimension-change persistence for classes/subclasses remains to be checked afterward, as previously noted.

---

**Right-side owned-class list scrolling:** COMPLETE
**Left-side scrolling regression:** PASS
**"+" button / `AddClassLevelPayload` regression:** PASS
**Class progression / subclass / resource / server-authority logic touched:** NO
**Full tests:** 1588/1588
**Safe for manual testing:** YES
**Safe to commit:** NO — wait for Stefan manual test/review
