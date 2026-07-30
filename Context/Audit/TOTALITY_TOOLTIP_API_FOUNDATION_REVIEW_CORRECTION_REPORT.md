# Totality Tooltip API Foundation — Review-Correction Report

A narrow correction pass against the accepted-in-principle Tooltip API foundation (`Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_IMPLEMENTATION_REPORT.md`). This pass does not replace the foundation architecture (`TooltipContext`/document/section model, ordered contributor registry, explicit `TooltipProfileComponent` opt-in, the temporary documented rarity fallback, multiple classifications, Artifact→Ancient, Shift/Ctrl disclosure, central scrolling, or the representative battery/Grimoire/weapon/Ring/weight/healing-potion contributors) and does not begin vanilla weapon/armor migration.

---

## 1. Starting branch and HEAD

Branch: `feature/general-resource-api`
HEAD: `6998d3222ddc5274a6fca7e0dbd83c71fc3b7408`

Verified identical to the checkpoint before any file in this pass was touched.

## 2. Starting task-scoped status

`git diff --stat` scoped to every foundation-pass file, and `git status --short` for the whole repository, matched the foundation implementation report's own §39/§40 exactly — the same 20 modified files, 2 deletions, and 33 new files (25 main, 8 test), plus the same pre-existing unrelated dirty tree (`build.gradle`, 18 generated JSON files, 23 pre-existing review-bundle zips, `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`). No unexplained change had occurred since the reviewed foundation bundle; the checkpoint was clean to correct.

## 3–4. Every review finding and its exact correction

| # | Finding | Correction |
|---|---|---|
| 1 | Renderer derived a "primary classification" (`classifications.get(0)`) and passed it into `resolveTheme(rarity, type)`, which fed a `typeBorderStyle(ItemType)` mapping into `TooltipTheme`'s `typeStyle` field — contradicting the locked rule that classification must never determine theme/border. | Removed the `ItemType primaryType` derivation entirely. `resolveTheme` now takes only `(ItemRarity)`. `typeStyle` is always `TooltipBorderStyle.NONE` — a literal constant, never derived. `typeBorderStyle(ItemType)` was deleted outright (confirmed zero remaining uses; `TooltipTheme.typeStyle()` itself is read by no drawing code anywhere in the renderer or `TooltipFrameRenderer` — verified by a full-package grep before removal). |
| 2 | Titles, `StatRow` values, `StatBlock` values, and footer hints were drawn as single unbounded lines — no wrapping, no dynamic sizing, risk of overlap/overflow with a long name, an exact battery figure, or multiple simultaneous hints. | Title now wraps via `Font.split` against the final panel width, with header height computed from the real wrapped-line count. `StatRow`/`StatBlock` values that don't fit alongside their label continue on following line(s) instead of overlapping, sized from the actual wrap. Footer redesigned: "Totality" always owns its own first row; hint lines wrap below it, right-aligned, with dynamically-computed footer height — hints can no longer collide with the credit line by construction. |
| 3 | Footer "Details available"/"Technical available" was inferred by scanning `document.sections()` for a section whose `minDisclosure()` happened to equal `DETAILS`/`TECHNICAL` at the *currently selected* level — but contributors like `EnergyContributor`/`HealingPotionContributor` only emit their extra content when that level is *already* selected, so the inference always found nothing at Default view. | Added `TooltipContributor.availableDisclosureLevels(ctx)` (default `Set.of()`) and `TooltipDocument.availableLevels()` (aggregated by the renderer from every contributor, independent of what each contributor's `contribute()` actually emitted this call). `EnergyContributor`, `HealingPotionContributor` (when dice-based), and `TechnicalInfoContributor` (unconditionally) now declare their real capability explicitly. Footer hint logic reads `document.availableLevels()` only — never section content. |
| 4 | `TooltipKnowledgeView`/`TooltipVisibility` existed in the foundation but no production contributor used them — everything rendered as if always identified. | `MetadataContributor` gates the `RarityBadge` and lore `Description` behind `WHEN_IDENTIFIED`. `AttunementContributor` gates all three of its outputs (requirement line, status row, AC/save-bonus line) behind `WHEN_IDENTIFIED`. `GrimoireContributor` gates the selected-spell row (not Tier) behind `WHEN_IDENTIFIED`. `ExternalContentContributor` gates the whole preserved-lines block behind `WHEN_IDENTIFIED`. `WeaponContributor`, `EnergyContributor`, `WeightContributor`, `FuelContributor` are unchanged — everything they show is an "obvious/basic fact" per the locked policy. |
| 5 | Investigate whether any representative migrated item still has a legacy tooltip-line producer duplicating a new contributor. | Investigated all five representative items plus `TotalityItem.addTooltipLines()`'s default (the one remaining candidate). **No duplication found and no code change was needed** — see §9 for the full finding. |
| 6 | New `String.format` calls used the platform default locale. | `HealingPotionContributor` (2 call sites) and `WeightContributor` (1 call site) now use `Locale.ROOT` explicitly. A repository-wide grep of `client/tooltip/**` and `api/core/rpgutils/rarity/**` confirmed no other locale-sensitive formatting calls exist in the new Tooltip API code. |
| 7 | The body viewport formula forced a minimum 20px body (`Math.max(Math.min(bodyContentH, 20), ...)`) even when fewer than 20px were actually available, and could in principle push the panel past the screen. No scroll indicator existed. | Viewport formula rewritten as `Math.max(0, Math.min(bodyContentH, availableBodyH))` with `availableBodyH` itself floored at 0 — never forces a minimum beyond what's actually available; a tiny window now fails safely (a zero-height body) instead of overdrawing. A restrained 2px track+thumb scroll indicator is drawn on the panel's right edge, outside the body's scissor region, only when overflowing. |
| 8 | `AttunementContributor` and `FuelContributor` called `Minecraft.getInstance().player`/`.level` directly instead of using the player/level `TooltipContext` already carries. Several stale/contradictory doc comments. | Both contributors now read `ctx.player()`/`ctx.level()`. `TooltipProfileComponent`'s Javadoc now explicitly acknowledges the temporary rarity fallback. `ScreenMixin`'s Javadoc no longer claims a `default` injector method (it's `private`). The implementation report's self-contradictory §15 sentence (claims removed, then says left as an exception) is corrected; its §35 manual-validation claim was already accurate and remains so; §10's classification/theme claim is now annotated as correct only as of this correction. |

## 5. Final classification/theme boundary

`resolveTheme(ItemRarity rarity)` — single parameter, verified by a reflection test (`TotalityTooltipRendererThemeDecouplingTest`) that no overload accepts more than one argument and that `typeBorderStyle` no longer exists as a method on the class at all. `TooltipTheme` still carries a `typeStyle` field (kept for compatibility per the correction brief's own allowance — "if `TooltipTheme` must temporarily retain a typeStyle field for compatibility, pass `TooltipBorderStyle.NONE`"), but it is now *always* `TooltipBorderStyle.NONE`, a literal constant with no data-flow from `ItemType`/classifications whatsoever. Classifications flow through exactly one path: `MetadataContributor` → `ClassificationBadges` section → `wrapClassifications`/`drawClassificationRow` (badges only). There is no remaining code path by which reordering, adding, or removing a classification can change the resolved `TooltipTheme` or border style.

## 6. Final title/stat/footer wrapping behavior

- **Title:** wrapped via `Font.split(title, titleAreaW)` where `titleAreaW = innerW - iconAreaW`, against the panel's final (content-independent) width. Header height is `titleLineCount * titleLineH + badge-row height`, computed from the real wrapped-line count — never assumed to be one line. A single-line title still uses the existing per-rarity animated draw path (visual continuity for the common case); a wrapped multi-line title draws each line plainly in the theme's name color (the character-animation logic operates on a single string and was not extended to multi-line — documented trade-off, not a silent gap).
- **StatRow:** if `iconW + labelW + gap + valueW` exceeds the row width, the value wraps onto its own line(s) beneath the label (native `Font.split`), preserving label/value association and growing the row's laid-out height accordingly. Below the threshold, behavior is unchanged (single line, value right-aligned).
- **StatBlock:** each `StatLine` is measured independently against the box's inner width; a line whose value doesn't fit continues on wrapped line(s) beneath its own label, and the box height grows to include every wrapped line. Verified structurally via `TotalityTooltipRendererLayoutPolicyTest`'s `fitsOnOneLine` boundary tests using widths representative of Netherite Battery's exact-figure Details view.
- **Footer:** "Totality" always occupies its own first footer row (left-aligned); hint lines (already packed by `footerHintLines`) are drawn below it, right-aligned, one per wrapped line. This makes hint/credit-line overlap structurally impossible rather than merely unlikely. Footer height is computed dynamically from the real hint-line count via a two-pass sizing (see §11).

## 7. Disclosure-capability model

`TooltipContributor.availableDisclosureLevels(TooltipContext ctx)` — default `Set.of()`. `TooltipDocument` gained an `availableLevels()` field (a `Set<TooltipDisclosureLevel>`), populated by the renderer unioning every contributor's declaration alongside its `contribute()` output, in the same pass over the registry. Contributors declare capability independent of the currently-selected level:

- `EnergyContributor` — `Set.of(DETAILS)` whenever the stack is a `UEItem` (energy always has an exact-figure Details view).
- `HealingPotionContributor` — `Set.of(DETAILS)` when the formula is `HealingAmount.DiceHealing` (min/max roll detail exists), else `Set.of()`.
- `TechnicalInfoContributor` — `Set.of(TECHNICAL)` unconditionally for any opted-in item, with no dependency on `ctx.disclosure()` inside the declaration itself (verified by a source sentinel).

The renderer's `footerHintFlags(disclosure, availableLevels)` (a new pure, testable method) decides: Shift hint shown only at `DEFAULT` when `DETAILS` is available; Ctrl hint shown at `DEFAULT` and `DETAILS` (not `TECHNICAL`) when `TECHNICAL` is available — Ctrl's own availability is independent of and unaffected by Shift, and nothing in this model lets Shift outrank Ctrl (both are computed from the same disclosure/availability pair with no shared mutable state).

## 8. Identification visibility assignments

| Content | Contributor | Visibility |
|---|---|---|
| Exact rarity badge | `MetadataContributor` | `WHEN_IDENTIFIED` |
| Lore / description | `MetadataContributor` | `WHEN_IDENTIFIED` |
| Classification badges | `MetadataContributor` | `ALWAYS` (an observable fact) |
| "Requires Attunement" | `AttunementContributor` | `WHEN_IDENTIFIED` |
| Attunement status (Attuned/Not Attuned/Free) | `AttunementContributor` | `WHEN_IDENTIFIED` |
| AC/Save Bonus line | `AttunementContributor` | `WHEN_IDENTIFIED` |
| Grimoire selected spell | `GrimoireContributor` | `WHEN_IDENTIFIED` |
| Grimoire tier | `GrimoireContributor` | `ALWAYS` (an observable fact — tome thickness/ornamentation) |
| Weapon damage/type/properties/category/range/stamina | `WeaponContributor` | `ALWAYS` (unchanged — all "obvious/basic" facts) |
| Energy figures, progress bar, active/inactive | `EnergyContributor` | `ALWAYS` (unchanged — "a plainly technological battery") |
| Weight | `WeightContributor` | `ALWAYS` (unchanged — physically observable) |
| Fuel burn time | `FuelContributor` | `ALWAYS` (unchanged) |
| Preserved vanilla/third-party lines | `ExternalContentContributor` | `WHEN_IDENTIFIED` (whole block — conservative, uniform; no per-line content heuristics) |

**Important consequence, documented not hidden:** only items implementing `TotalityItem` actually default to `UNIDENTIFIED` (`TotalityItem.startsUnidentified() = true`, and nothing in the repository calls `setIdentificationStatus(IDENTIFIED)` anywhere — confirmed by a full-repository grep). Of the five representative migrated items, only **Netherite Shuriken** and **Ring of Protection** are `TotalityItem`-based (via `TotalityWeaponItem`/`TotalityArmorItem`); Batteries, Grimoires, and the D&D Potion of Healing are plain `Item` subclasses, so `TooltipKnowledgeView.of(stack)` always reports them fully identified regardless of this gating, and this correction has no visible effect on those three items today. For Netherite Shuriken and Ring of Protection, this means their rarity/lore/attunement content will now render in its unidentified (hidden) presentation by default in a live game, since no code path currently grants identified status — this is the correct, intended consequence of finally consuming the pre-existing `IdentificationStatus` contract as instructed, not a bug, but it is a real, visible behavior change that manual validation must specifically check (see §18).

## 9. External-content ownership policy

Two policies, kept separate:

1. **Preservation** (unchanged from the foundation pass): `ExternalContentContributor` still surfaces every original vanilla/third-party line except an exact string match against the item's own display name — no color/indentation/translation-key filtering.
2. **Identification gating** (new, this pass): the entire preserved block is now `WHEN_IDENTIFIED`, chosen specifically as the conservative option the correction brief offered ("gate external content behind `WHEN_IDENTIFIED`, or preserve only explicitly recognized basic lines through a narrow ownership policy") — the second option was rejected because distinguishing "basic" from "secret" third-party content without translation-key/color heuristics is not possible for genuinely unknown content, and such heuristics are explicitly forbidden.

**Dedupe investigation (finding, no code change):** all five representative items were inspected for a legacy tooltip-line producer still duplicating a new contributor. `BatteryItem.appendHoverText`, `GrimoireItem.addTooltipLines`, and `RingOfProtectionItem.addTooltipLines` were already deleted in the foundation pass — confirmed still absent. `HealingPotionItem` never had an `appendHoverText` override. `ShurikenItem.appendHoverText()`'s generic flavor line (`item.totality.shuriken.tooltip`) is not duplicated by `WeaponContributor` — it correctly flows through as preserved external content. The one remaining candidate, `TotalityItem.addTooltipLines()`'s default (attunement line), is **not dead code**: it is bypassed within the hover-tooltip pipeline (`LegacyExtensionAdapterContributor` explicitly excludes `instanceof TotalityItem`) but is independently and legitimately called by `TradingScreen.tooltipLines()` (`screen/shop/TradingScreen.java:996`) for an unrelated shop-preview panel outside the Tooltip API's scope. Removing or emptying it would silently regress that feature, so it was deliberately left untouched — confirmed by a source sentinel (`totalityItemAddTooltipLinesDefaultIsIntentionallyKeptBecauseTradingScreenStillUsesIt`) that pins down both the method's continued existence and `TradingScreen`'s continued dependency on it, so a future pass does not "clean it up" by mistake.

## 10. Locale policy

Every `String.format` call added by the Tooltip API foundation (in `HealingPotionContributor` and `WeightContributor`) now passes `Locale.ROOT` explicitly. No other locale-sensitive formatting calls exist in `client/tooltip/**` or the new `api/core/rpgutils/rarity/**` files (confirmed by grep). Intentional localized `Component`/translation-key text (e.g. `Component.translatable(...)` call sites elsewhere in the mod) was not touched — this policy applies only to raw numeric `String.format`, not to translation infrastructure.

## 11. Viewport-height policy

`availableBodyHeight(maxViewportH, headerH, separatorH, footerH) = Math.max(0, maxViewportH - (headerH + separatorH + footerH))`, and `bodyViewportH = Math.max(0, Math.min(bodyContentH, availableBodyH))` — both floored at zero, never forcing a minimum beyond what the screen actually has left. Because footer height itself depends on which hints are shown, and whether the Scroll hint is needed depends on whether the body overflows (which depends on footer height), a two-pass calculation breaks the cycle: pass 1 sizes the footer from the Shift/Ctrl hints alone and derives a provisional `overflowingGuess`; pass 2 adds the Scroll hint if that guess was true, and the *final* footer/viewport/panel dimensions are derived from pass 2. The two passes can only disagree in the extreme edge case where body content height sits within about one footer-row-height of the overflow threshold — documented as an accepted, narrow limitation rather than solved with a third pass, since the actual scroll behavior (via `TooltipScrollController`, computed once with the final dimensions) remains correct regardless.

## 12. Scroll-indicator design

A 2px-wide vertical track (`0x33FFFFFF`, ~20% white) spans the full body viewport height at the panel's right inner edge; a brighter thumb (`0xAAFFFFFF`) sized proportionally to `viewportH² / contentH` (floored at 6px so it's never invisibly thin) and positioned proportionally to the current scroll offset is drawn on top. Drawn only when `TooltipScrollController.isOverflowing()` is true, and drawn **after** the body's `CloseableScissor` block closes — inside the scissor it would be clipped out of view for exactly the case it exists to signal. The footer remains entirely outside the scissored/indicator region and is always drawn in full, so it stays reachable regardless of scroll state.

## 13. Exact files changed by the correction pass

**Modified (main, 16 files):**
```
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipRenderer.java   (corrections 1, 2, 3, 7 — major rewrite)
src/main/java/zcylas/totality/client/tooltip/TooltipDocument.java            (availableLevels field)
src/main/java/zcylas/totality/client/tooltip/TooltipScrollController.java    (null sentinel fix; extracted pure computeMaxScroll/clampScroll)
src/main/java/zcylas/totality/client/tooltip/section/TooltipSection.java     (withVisibility on RarityBadge/Description/Requirement/ExternalContent)
src/main/java/zcylas/totality/client/tooltip/contributor/TooltipContributor.java        (availableDisclosureLevels default method)
src/main/java/zcylas/totality/client/tooltip/contributor/EnergyContributor.java         (availableDisclosureLevels)
src/main/java/zcylas/totality/client/tooltip/contributor/HealingPotionContributor.java  (availableDisclosureLevels; Locale.ROOT)
src/main/java/zcylas/totality/client/tooltip/contributor/TechnicalInfoContributor.java  (availableDisclosureLevels)
src/main/java/zcylas/totality/client/tooltip/contributor/MetadataContributor.java       (identification gating)
src/main/java/zcylas/totality/client/tooltip/contributor/AttunementContributor.java     (identification gating; ctx.player())
src/main/java/zcylas/totality/client/tooltip/contributor/FuelContributor.java           (ctx.level())
src/main/java/zcylas/totality/client/tooltip/contributor/GrimoireContributor.java       (identification gating)
src/main/java/zcylas/totality/client/tooltip/contributor/ExternalContentContributor.java (identification gating)
src/main/java/zcylas/totality/client/tooltip/contributor/WeightContributor.java         (Locale.ROOT)
src/main/java/zcylas/totality/api/core/rpgutils/rarity/TooltipProfileComponent.java     (doc fix)
src/main/java/zcylas/totality/mixin/client/ScreenMixin.java                             (doc fix)
```

**Modified (docs, 1 file):**
```
Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_IMPLEMENTATION_REPORT.md   (addendum; §15/§10 corrections)
```

**New (tests, 4 files):**
```
src/test/java/zcylas/totality/client/tooltip/TooltipScrollControllerTest.java
src/test/java/zcylas/totality/client/tooltip/TotalityTooltipRendererLayoutPolicyTest.java
src/test/java/zcylas/totality/client/tooltip/TotalityTooltipRendererThemeDecouplingTest.java
src/test/java/zcylas/totality/client/tooltip/TooltipIdentificationVisibilityTest.java
```

**Extended (tests, 1 file):**
```
src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java  (11 -> 25 tests)
```

**New (this report + bundle, 2 files):**
```
Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_REVIEW_CORRECTION_REPORT.md
Context/Audit/Review Bundles/TOTALITY_TOOLTIP_API_FOUNDATION_REVIEW_CORRECTION_BUNDLE.zip
```

No file was deleted this pass. `TotalityItem.java` was inspected (§9) but deliberately not modified. `git diff --stat` for `TotalityTooltipRenderer.java` against HEAD (cumulative — includes both the original foundation pass and this correction pass, since nothing was committed between them): **608 insertions(+), 154 deletions(-)**. The other 15 modified main files were untracked (created by the foundation pass, never committed), so git cannot isolate a "this pass only" diff for them against HEAD; their current sizes are recorded instead — `wc -l` across all 16 modified main files plus the 5 test files totals **1,678 lines** in their current (post-correction) state.

## 14. Test categories and evidentiary limits

- **Pure semantic/arithmetic tests** (no Minecraft bootstrap): `TotalityTooltipRendererLayoutPolicyTest` (17 tests — `effectiveContentWidth`, `availableBodyHeight`, `fitsOnOneLine`, `footerHintFlags`, all extracted as package-private static methods specifically for this), `TooltipScrollControllerTest` (7 tests — `computeMaxScroll`/`clampScroll`, extracted the same way after discovering `ItemStack.EMPTY` itself cannot be referenced under plain JUnit), `TooltipIdentificationVisibilityTest` (11 tests — constructs the exact section shapes each contributor builds and checks them against real `TooltipKnowledgeView` instances).
- **Reflection tests**: `TotalityTooltipRendererThemeDecouplingTest` (4 tests) — proves `resolveTheme` takes only `ItemRarity` and `typeBorderStyle` no longer exists, structurally rather than behaviorally.
- **Source-regression sentinels**: `TooltipApiFoundationSourceRegressionTest` grew from 11 to 25 tests, covering: disclosure-capability declarations exist and are unconditional where required; `MetadataContributor`/`AttunementContributor`/`GrimoireContributor`/`ExternalContentContributor` contain the expected `WHEN_IDENTIFIED` gates in the expected places; `WeaponContributor` contains none; the `TotalityItem.addTooltipLines`/`TradingScreen` dedupe finding; `ctx.player()`/`ctx.level()` usage; `Locale.ROOT` on every `String.format` call; and the two documentation corrections (`TooltipProfileComponent`, `ScreenMixin`). These prove source text, not runtime behavior.
- **Not built, and why (an extension of the foundation report's own established limit, not a new gap):** live behavioral tests of any contributor's `contribute(TooltipContext)` output, and true pixel-level wrapping/rendering tests. Both need a real `ItemStack`/`Item` or `Font`, neither of which can be constructed under plain JUnit in this repository — re-confirmed directly this pass: even referencing the pre-existing `ItemStack.EMPTY` singleton (not constructing a new stack) throws `ExceptionInInitializerError` before the registry is bootstrapped, because it sits in a static field initializer that forces the whole `ItemStack` class to load. This is exactly why `TooltipScrollController`'s own `currentStack` field was changed from an `ItemStack.EMPTY` default to a `null` sentinel — the old code was, incidentally, itself untestable for the same reason before this pass.

## 15. Focused/full test commands and totals

```
./gradlew test --tests "zcylas.totality.client.tooltip.*" \
                --tests "zcylas.totality.item.potion.dnd.*" \
                --tests "zcylas.totality.api.core.rpgutils.rarity.*"
```
**156 tests, 0 failures, 0 errors, 0 skipped** (aggregated from 17 `TEST-*.xml` files).

```
./gradlew test
```
**1094 tests, 0 failures, 0 errors, 0 skipped** (aggregated from all 90 `TEST-*.xml` files under `build/test-results/test/`) — 53 more than the foundation pass's 1041, exactly matching the 53 tests added this pass (17 + 7 + 4 + 11 + 14 net-new source-sentinel tests).

## 16. Datagen result

`./gradlew runDatagen` completed successfully: client fully bootstrapped, all four mod self-tests passed, and the datagen cache report read **"total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"** — zero generated files changed. No unrelated churn.

## 17. Build result

`./gradlew clean build` — **BUILD SUCCESSFUL**, all tasks executed (`compileJava`, `processResources`, `classes`, `processIncludeJars`, `jar`, `sourcesJar`, `assemble`, `compileTestJava`, `testClasses`, `test`, `validateAccessWidener`, `check`, `build`). `git diff --check` afterward exited 0 — only informational LF/CRLF line-ending notices on both pre-existing and newly-touched files, no actual whitespace-conflict errors.

## 18. Manual validation performed and remaining

**Performed: none.** This correction pass, like the foundation pass before it, did not launch an interactive client session. All validation is through `test`/`runDatagen`/`clean build` — genuine proof of compilation and unit-level correctness, not of on-screen rendering, wrapping feel, scroll behavior, or visual identity.

**Remaining** (unchanged scope from the foundation report §35, plus items this correction pass specifically requires checking):
- All five batteries, all three Grimoires, Netherite Shuriken, Ring of Protection, D&D Potion of Healing.
- A long custom-named item (title wrapping).
- Shift and Ctrl, including the corrected footer hint behavior at Default/Details/Technical.
- A tall tooltip that scrolls — confirm the new scroll indicator appears only when overflowing, tracks position correctly, and stays outside the clipped region.
- A narrow window and multiple GUI scales — confirm `effectiveContentWidth`'s screen-based clamping behaves correctly live, not just in the pure arithmetic tests.
- **An unidentified item — specifically Netherite Shuriken or Ring of Protection** (the only two representative items actually affected by the new identification gating, per §8's documented consequence): confirm rarity/lore/attunement are hidden while basic weapon facts remain visible, and that this doesn't read as "broken."
- Enchantment/external-content preservation, now behind the `WHEN_IDENTIFIED` gate — confirm preserved lines appear once identified and are absent (not erroring) while unidentified.
- A structured `TooltipComponent` fallback.
- Ancient versus Legendary visual identity.

None of this has been performed; it remains the concrete next step before this branch is considered visually verified.

## 19. Final task-scoped diff/stat

See §13 for the exact file-by-file breakdown. Summary: 16 main files modified, 1 doc file modified, 5 test files new/extended (4 new + 1 extended), 2 report/bundle files new. No deletions this pass.

## 20. Final repository-wide status

`git status --short` after this pass: the same pre-existing dirty tree from the foundation report's §2/§40 (`build.gradle`, 18 generated JSON files, 23 pre-existing review-bundle zips, `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`), unchanged — plus the foundation pass's original 20 modified/2 deleted/33 new files (16 of the 20 further modified by this correction pass, per §13), plus this pass's own 4 new test files, 1 extended test file, and 2 new report/bundle files. No file outside this pass's declared scope was created, modified, staged, or touched.

## 21. Confirmation that unrelated files were preserved

Confirmed. `build.gradle`, all 18 generated datagen JSON files, all pre-existing untracked review-bundle zips, the `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, and `src/main/generated/.cache/` are exactly as they were at the start of this pass — not read for modification, not staged, not deleted, not reformatted. `runDatagen`'s own cache report ("removed stale: 0, written: 0") independently confirms no generated file was altered.

## 22. Confirmation that Phase 3C remains unstarted

Confirmed. No file under `api/rpg/resources/**` (or any other Phase 3C scope) was read for modification or touched by this pass. Phase 3C remains the next major implementation task after this Tooltip foundation (now corrected) is reviewed and committed.

## 23. Confirmation that nothing was committed or pushed

Confirmed. No `git commit`, `git add`, or `git push` command was run at any point in this pass. `git status --short` throughout shows only working-tree modifications/additions, never an index change.

---

*Totality Tooltip API foundation review-correction pass · `feature/general-resource-api` · HEAD `6998d322` throughout, no commit created.*
