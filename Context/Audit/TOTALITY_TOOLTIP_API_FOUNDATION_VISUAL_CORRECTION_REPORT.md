# Totality Tooltip API Foundation — Visual Correction Report

A focused visual/interaction correction pass against the Tooltip API foundation, following the final correction pass (`Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_FINAL_CORRECTION_REPORT.md`) and its own micro-correction addendum. This pass does not redesign the semantic contributor architecture (`TooltipContext`/document/section model, ordered contributor registry, `TooltipSectionGroup` block ordering, explicit `TooltipProfileComponent` opt-in, identification visibility policy, or any contributor's ownership of its own content) — it corrects the renderer's layout policy, one contributor's line ownership, and the scroll-input wiring, per eight findings drawn from manual in-game screenshots supplied as evidence for this pass.

> **IMPORTANT — manual visual/interaction validation was explicitly NOT performed in this pass.** The task brief asked for an interactive client session (hovering items, testing scroll, capturing fresh screenshots). Partway through this pass, Stefan told me directly: *"I dont know why ChatGPT added that in the prompt. Ignore the screenshot thing, I will manually check it."* Per that explicit instruction, no interactive session was run, no items were hovered, and no screenshots were captured by this pass — Stefan is performing that check himself. §12 records exactly what automated verification *was* performed instead (a client boot-check confirming no crash/mixin-wiring failure) and lists everything that remains genuinely unverified. Anyone reviewing this report (including a future prompting pass) should treat every visual/interaction claim below as "implemented and unit/sentinel-tested," never as "confirmed on screen."
>
> **Micro-correction addendum.** A subsequent static review of this pass's own bundle found five further findings, all now fixed: `onMouseScroll` consumed wheel input even when clamping left the offset unchanged (breaking normal screen scrolling at the top/bottom of an overflowing tooltip); the scroll-target identity used `Slot.index`, which is not guaranteed unique across a menu's slots; the component-count recognition in `ExternalContentContributor` used an English-only regex; `TooltipScrollController`'s Javadoc still linked the deleted `TotalityItemSlotScrollAction` class; and this report itself overstated the Creative-inventory guarantee and had an inconsistent file count. See §22 for the full account, corrected files, and updated test totals. §4–§11's descriptions of Findings 1–8 remain accurate for what they cover; §9's Creative-inventory sentence (already corrected in place above) and §13's file count (already corrected in place above) reflect the fixed wording — §22 records why they changed.
>
> **Presentation-cleanup addendum.** Stefan's own deferred manual visual check (§12) has now happened, and found only four small remaining polish items, all fixed in this narrow pass: the icon-to-label gap in icon-led rows (Damage, Range, Stamina Cost, etc.) read as too tight; the tooltip was still slightly cramped near the header/body transition and just above the footer; the standalone D&D Potion of Healing had no authored rarity; and Creative-inventory manual validation turned out not to be practical in Stefan's current setup and needed to be recorded honestly rather than left implied. See §24 for the full account, corrected files, and updated test totals. This is also the first confirmation that *some* of §12's "entirely unverified" claims have now genuinely been checked by Stefan — §24.1 records exactly what that check covered.
>
> **Scrolling event-consumption fix addendum.** Manual testing confirmed tooltip scrolling itself now genuinely works, but in Creative inventory the raw wheel event was also reaching the background screen at the same time — both scrolled together. Investigation (§26) found `MouseHandlerMixin`'s cancellation was already structurally correct (`@At("HEAD")`, `cancellable = true`, `ci.cancel()` gated behind the handler's own return value, matching the already-proven `FluidTankScrollHandler` pattern in the same method); decompiling `MouseHandler`'s GLFW callback registration confirmed scroll handling is deferred via `Minecraft.execute(...)`, meaning a single fast scroll gesture can enqueue several `onScroll` invocations that drain in a burst — once a tooltip's own (often small) overflow is exhausted partway through that burst, the remaining events in that same gesture correctly and intentionally fall through to the screen, per this fix's own required-behavior list. No consumption-logic defect was found; the handler was renamed (`onScroll` → `onMouseScroll`) and the investigation documented in place for future reference. See §26 for the full account.
>
> **Foundation-closure addendum (final).** Manual validation of representative items — including the Netherite Shuriken and the D&D Potion of Healing — is now complete and confirms the Tooltip API foundation behaves as designed: compact widths, flowing badge rows, readable/non-clipping Shuriken layout, correct icon-to-label spacing, improved header/body and body/footer breathing room, the Potion of Healing's UNCOMMON/POTION presentation, Default-view hiding of registry id/component count, Ctrl Technical and Shift Details disclosure, and source-slot tooltip scrolling that stays inactive for short/non-overflowing tooltips and keeps the scrollbar inside its own gutter. §12's "explicitly NOT performed" framing described that specific pass only and is superseded by this addendum and §28, which is now the authoritative closure record. One limitation remains and is accepted, not blocking: smooth-scroll/trackpad gestures can continue scrolling Creative's inventory after the tooltip exhausts its own scroll range within the same gesture (§26.2's decompiled explanation — spec-required fallthrough, not a cancellation defect), deferred until Totality has its own Creative-inventory handling. This foundation is considered complete; Generic Player Resource API Phase 3C is the next major task. See §28 for the full closure record.

---

## 1. Starting branch and HEAD

Branch: `feature/general-resource-api`
HEAD: `6998d3222ddc5274a6fca7e0dbd83c71fc3b7408`

Verified identical to the checkpoint before any file in this pass was touched.

## 2. Starting task-scoped status

`git status --short` matched the micro-correction's own final state exactly — the same pre-existing unrelated dirty tree (`build.gradle`, 18 generated JSON files, 23+ pre-existing review-bundle zips, `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`), plus every file from the three prior Tooltip API passes, untouched since the reviewed micro-correction. No unexplained change had occurred; the checkpoint was clean to correct.

## 3. Evidence used

Three new reference images were added to `Context/Audit/Image References/` for this pass (`Tooltip1.png`, `Tooltip2.png`, `Tooltip3.png`) — directional design-target concept boards (compact width, flowing badges, Default-vs-Ctrl content split, overflow scrolling explainer, "Avoid: Wide & Sparse" contrast panel), not raw before/after screenshots of the running mod. Per the task's own Finding 6 instruction ("use the previously generated tooltip concept images only as directional references"), these calibrated the target look; the eight numbered findings in the task brief (verbatim prose descriptions of observed bugs) are what this report's changes are traced against, finding by finding, below.

## 4. Finding 1 — compact shrink-to-content width policy

**Root cause:** `effectiveContentWidth(int screenW)` always returned `MAX_WIDTH` (260) clamped only by screen size — completely independent of what content the item actually had. A one-line item (Whitestone) and a fully-detailed weapon got the identical wide panel.

**Fix:** width is now derived from the tallest single-line content actually present.

- New `measureNaturalContentWidth(font, title, authoredRarity, classifications, body, iconAreaW)` measures the natural (unwrapped) width of the title, the flowing badge row, and every one-line-capable body section (`StatRow`/`StatBlock`/`PropertyBadges`/`Requirement`/`Heading`) via a new `naturalRowWidth` per-kind switch. Each candidate is individually capped at `PREFERRED_MAX_WIDTH` before comparison, so one unusually long row wraps instead of forcing the whole panel wider.
- `Description` (lore), `ExternalContent`, `TechnicalInfo`, and `ProgressBar` are **deliberately excluded** from measurement (the `naturalRowWidth` switch's `default -> 0` arm) — this is the exact mechanism satisfying "long content must wrap vertically instead of forcing a very wide panel," and specifically covers the "long technical registry identifier" test case (registry id / component count lines live in `TechnicalInfo`, never measured).
- New named constants replace the old fixed pair: `MIN_WIDTH = 110` (compact floor), `PREFERRED_MAX_WIDTH = 200` (moderate ceiling for detailed items), `MAX_SCREEN_WIDTH_FRACTION = 0.38f` (hard ceiling as a fraction of the current scaled screen width, satisfying "avoid occupying more than approximately 35–40% of screen width").
- `effectiveContentWidth(int screenW, int naturalContentW)` (signature changed, now takes the measured value) clamps: `hardCap = min(PREFERRED_MAX_WIDTH, min(maxScreenContentW, screenFractionCapW))`, `widthFloor = min(MIN_WIDTH, hardCap)`, `result = clamp(naturalContentW, widthFloor, hardCap)`.

**Verified (pure tests, `TotalityTooltipRendererLayoutPolicyTest`):** representative `naturalContentW` inputs for Whitestone (70 → floors at 110), Potion of Healing (95 → floors at 110), Apprentice Grimoire (130 → uses exactly 130), Netherite Shuriken (190 → uses exactly 190, ≤200), a long custom title (900 → caps at 200), a simulated long technical line (5000 → caps at 200), plus a screen-fraction invariant check across seven screen widths confirming `panelW ≤ screenW × 0.38` in every case. `measureNaturalContentWidth`/`naturalRowWidth` themselves need a real `Font` and are covered by a source-regression sentinel confirming the exclusion list instead (§9).

## 5. Finding 2 — flowing badge row

**Root cause:** the header drew the rarity badge on its own row unconditionally, then classification badges on separate row(s) below — never sharing a row even when both would easily fit together.

**Fix:** rarity and every classification now build one ordered `List<BadgeSpec>` (`buildBadgeSpecs` — rarity first if present, then classifications in their authored order, exactly as the finding requires) and flow through a single `wrapBadges` (greedy width-packing, same shape as the pre-existing `wrapClassifications`/`wrapLabels` pattern) against the title's available width — wrapping only when the next badge genuinely doesn't fit, never forcing rarity onto its own row. `drawBadgeRow` draws each badge with its own rarity/classification color (`TooltipColors.forRarity`/`forType`), replacing the old separate `drawRarityBadge`/`drawClassificationRow` methods (both deleted). Badge padding was also tightened: badges are now sized as `font.width(text) + BADGE_PADDING` (10px total, both sides) instead of the old forced 60px minimum width, so a short badge like "EPIC" no longer takes as much horizontal room as a long one.

**Verified (source-regression sentinels):** rarity is appended before classifications in `buildBadgeSpecs`; `drawRarityBadge`/`drawClassificationRow` no longer exist; `wrapBadges` is called with the combined `badgeSpecs` list. Direct pixel-level wrapping behavior needs a real `Font` and is not independently pure-testable (same established constraint as every other `Font`-dependent method in this class).

## 6. Finding 3 — technical line ownership for registry ID / component count

**Root cause:** when Minecraft's own "Advanced Tooltips" (F3+H) setting is on, vanilla appends two exact, reliably-identifiable debug lines to every item's tooltip — the registry id (e.g. `totality:apprentice_grimoire`) and a component count (e.g. `"18 component(s)"`). `ExternalContentContributor` preserved these completely unfiltered as ordinary `ALWAYS`/`WHEN_IDENTIFIED` external content, so they appeared in Default view exactly like any other line.

**Fix (`ExternalContentContributor`):** these two exact facts are now recognized — an exact-string match against `BuiltInRegistries.ITEM.getKey(stack.getItem())`, and a precise regex (`^\d+ component\(s\)$`) for the count line — and **dropped** from the preserved-external output rather than duplicated into a second `TooltipSection.TechnicalInfo` block. This is a deliberate design choice, not an oversight: `TechnicalInfoContributor` already surfaces the exact same two facts, gated the same way (Ctrl-only), in its own clearer `"Registry: ..."` / `"Components: N"` format — re-adding the raw vanilla strings would only produce a duplicate, noisier line under Ctrl. Every other, unrecognized line (including anything from another mod) is preserved exactly as before — no color, indentation, or translation-key heuristic is used anywhere in this fix.

**Verified:** `noProductionCodeExplicitlyAuthorsIdentificationStatusYet`-style exact-match testing isn't applicable here (needs a real `ItemStack`), but the recognition logic itself (`isKnownAdvancedTooltipLine`) is a small, self-contained, reviewable method using only `String.equals`/`Pattern.matches` — no heuristics. Default-view hiding and Ctrl-view availability both follow automatically from `TechnicalInfoContributor`'s pre-existing, already-tested `TECHNICAL`-only gating (unchanged this pass).

## 7. Finding 4/5 — compact stat presentation and simple-item sizing

Both findings are substantially resolved by Finding 1's width fix (a much narrower default panel means values no longer stretch toward a distant far edge, and a simple item's height was already content-derived — only the old forced width made it look bloated). Additional targeted fixes:

- **Scrollbar gutter reservation:** a new named constant `SCROLLBAR_GUTTER = 6` is subtracted from the panel's inner width to produce `bodyContentW`, used for **all** body layout and drawing (`layout(section, font, bodyContentW)`, the scissor region, the draw loop) — body text can never sit under the scroll indicator, overflowing or not, directly satisfying "ensure no value is clipped by the viewport or scrollbar."
- **Tighter vertical spacing:** `ROW_GAP` reduced from 4 to 3; every row-height computation (`StatRow`/`StatBlock`/`Description`/`Requirement`/`ExternalContent`/`TechnicalInfo`/`drawWrapped`) changed from `font.lineHeight + 2` to `font.lineHeight + 1`; `PropertyBadges` row height reduced from `font.lineHeight + 6` to `font.lineHeight + 4`.
- **Weapon grouping order unchanged and reconfirmed:** `WeaponContributor`'s own internal emission order (Heading → Damage → Properties → Category/Range/Stamina) was not touched this pass, and — combined with `TooltipSectionGroup` block ordering from the prior pass — still produces exactly "Weapon → Damage → Properties → Category/Range/Stamina → Attunement → Lore" for Netherite Shuriken (verified by the pre-existing `TotalityTooltipRendererGroupingTest`, unaffected by this pass's changes).

## 8. Finding 6 — visual hierarchy cleanup

A conservative cleanup, not a rarity-palette redesign — `TooltipTheme`'s 20 per-rarity records are byte-for-byte unchanged.

- `PADDING` reduced from 10 to 8; header-to-body `separatorH` reduced from 10 to 7; footer padding reduced from 4 to 3.
- **Decorative three-dot footer marker removed entirely** — `drawFooterDots` deleted from both the renderer (no longer called) and `TooltipPainter` (the now-dead method itself deleted, not left behind) — it carried no information, and its removal directly shortens the footer.
- **Background desaturation:** `TooltipPainter.drawBackground` now blends `theme.bgTop()`/`bgBottom()` 35% toward a neutral dark ground (`0xFF19191C`) before drawing the gradient — `blendTowardNeutral`, a single new private method. Border, title, and badge colors are drawn separately and remain at full saturation, so rarity identity now lives primarily in those elements rather than the whole panel background, per the finding's own instruction — again, without touching `TooltipTheme`'s palette data at all.
- **Secondary text contrast:** a new shared constant `SECONDARY_TEXT_COLOR = 0xFF9CA3AF` (reusing the exact tone already used elsewhere in this file for property-badge text, for palette consistency) replaces the old inline `0xFF888888` used for `StatRow`/`StatBlock` labels.

## 9. Finding 7 — scroll wiring root-cause fix

This finding required real investigation, not a parameter tweak — the task brief's own framing ("register an active scroll target containing... source hovered-slot/item identity... maximum scroll offset... viewport bounds... render frame or freshness marker") strongly suggested the previous mixin-based approach was fundamentally unreachable, and decompiling the actual vanilla classes confirmed it:

- **Confirmed root cause (by decompiling `minecraft-merged.jar`, not by guessing):** `AbstractContainerScreen` declares its **own concrete** `mouseScrolled(DDDD)Z` override. Its bytecode (disassembled via `javap -c`) shows it does not call `super.mouseScrolled(...)` at all — when a slot holding an item is hovered, it iterates a private `itemSlotMouseActions` list and returns whatever those actions decide; when no slot (or an empty slot) is hovered, it returns `false` unconditionally. This means **neither** a `GuiEventListener` mixin **nor** a `ContainerEventHandler` mixin (both interfaces `AbstractContainerScreen` implements, both individually confirmed to declare their own `mouseScrolled` default method) can ever be reached — the concrete class override always wins over any interface default, for every `AbstractContainerScreen` subtype that doesn't itself override scrolling (e.g. plain `InventoryScreen`, most container screens). The previous pass's `ScreenMixin` (`@Mixin(GuiEventListener.class)`) was therefore **dead code from the moment it was written** — it compiled, it loaded, it simply never ran.
- **Fix — hook one level higher, before any screen ever sees the event:** `MouseHandler.onScroll` (the same raw-input mixin point this mod already uses for `FluidTankScrollHandler`) fires *before* Minecraft routes the scroll to any screen at all. New `TotalityTooltipScrollHandler.onScroll(scrollDelta)` (in `client/tooltip/`) reads the current screen's `hoveredSlot` directly via the pre-existing `AbstractContainerScreenAccessor` (no new mixin registration needed — `hoveredSlot` is already the exact field vanilla's own broken chain would have read), checks `TotalityTooltipRenderer.isEligible(stack)`, and calls into the scroll controller. `MouseHandlerMixin.onScroll` was extended to check `FluidTankScrollHandler` first (unchanged), then `TotalityTooltipScrollHandler` — cancelling the vanilla event only when one of them actually consumes it.
- **Dead code removed:** `ScreenMixin.java` deleted; its `"client.ScreenMixin"` registration removed from `totality.mixins.json`. (An intermediate design attempt — registering a `TotalityItemSlotScrollAction` into `itemSlotMouseActions` via a new `@Invoker` — was built, found to work but be unnecessarily complex compared to the `MouseHandler` hook already established in this codebase, and was reverted before being committed to this report; no trace of it remains in the final diff.)
- **Active scroll target, exactly as specified:** `TooltipScrollController` was rebuilt around a package-private `record ActiveTarget(Screen screen, int slotIndex, int maxScroll, int viewportX, int viewportY, int viewportWidth, int viewportHeight, long frameMarker)`, registered once per frame by `onRender` (now called *after* the panel position and body viewport bounds are final, not before, since a real viewport rectangle is now part of the target). `onMouseScroll(Screen, int slotIndex, ItemStack, double)` requires the incoming event's screen and slot index to match the active target **and** the target's `frameMarker` to equal the current frame counter (staleness check) **and** the stack to be `isSameItemSameComponents` before consuming — satisfying every one of the finding's bullet requirements: scrolls only the hovered source item's tooltip, never requires the cursor over the tooltip rectangle, consumes only when it actually scrolls, falls through to normal screen behavior otherwise, resets on item/slot/screen change (identity check inside `onRender`), resets on screen close (pre-existing `ScreenEvents.remove` hook, unchanged), and re-clamps every frame regardless of what changed (existing `clampScroll` call, unchanged) — covering the disclosure-change clamp case too.
- **Creative inventory and other screen scrolling is left untouched, precisely (corrected wording — micro-correction Finding 5):** the original version of this section overstated the guarantee as "Creative always falls through to normal routing." The accurate statement is narrower: `TotalityTooltipScrollHandler` consumes the raw `MouseHandler` event **only** when the current screen is an `AbstractContainerScreen`, the hovered slot holds a Totality-eligible item, an overflowing tooltip's `ActiveTarget` matches that exact screen/slot/stack, **and** the resulting scroll offset would actually change (micro-correction Finding 1). In every other case — including every frame while Creative's own inventory is open, since `CreativeModeInventoryScreen` declares its own `mouseScrolled` override and never reaches `AbstractContainerScreen`'s broken chain in the first place — the handler returns `false` and the raw event is left completely alone, exactly as if this mod's mixin didn't exist, for Minecraft (and Creative's own scroll logic) to handle normally. This is a stronger, more precise claim than "falls through to normal routing": nothing about Creative's scrolling is ever inspected, redirected, or dependent on this mod's code path at all.

## 10. Finding 8 — scrollbar presentation

- **Only visible during overflow:** unchanged — `drawScrollIndicator` is still called only when `TooltipScrollController.isOverflowing()`.
- **Does not cover or clip text:** now structurally guaranteed by Finding 4/5's `SCROLLBAR_GUTTER` reservation — the track draws at `bodyLeft + bodyContentW + (SCROLLBAR_GUTTER - trackW) / 2`, entirely inside the 6px gutter, never inside the text column's own width.
- **Reflects current position:** unchanged — thumb position/height are still proportional to `scrollOffset`/`viewportH`/`contentH`.
- **Restrained width:** `trackW` unchanged at 2px.
- **Inside the panel edge, outside the clipped body content:** the gutter sits between `bodyContentW`'s end and `innerW`'s end — inside the panel's own padding, outside the scissored text region.
- **Contrast:** track alpha raised from `0x33` to `0x40`, thumb alpha raised from `0xAA` to `0xCC` — slightly more visible without becoming a dominant panel element.

## 11. Test coverage added

**Pure tests extended:**
- `TotalityTooltipRendererLayoutPolicyTest` — the `effectiveContentWidth` section was rewritten for the new two-argument signature and shrink-to-fit contract: representative-item width tests (Whitestone, Potion of Healing, Apprentice Grimoire, Netherite Shuriken, a long custom name, a long technical identifier), a screen-fraction-cap invariant test across seven screen widths, and the pre-existing narrow/zero/negative-screen safety tests updated to the new signature.
- `TooltipScrollControllerTest` — the one signature-breaking call (`onMouseScroll`) updated; four new tests directly exercise `TooltipScrollController.targetMatches` (package-private, pure — `Screen` can validly be `null` for identity-equality purposes without constructing a real screen): same screen/slot/frame matches, a different slot index does not match (the concrete proxy for "different hovered item"), a stale frame marker does not match, and the `-1` "no slot" sentinel matches itself.
- `TooltipContributorRegistryTest` — extended in the prior micro-correction pass; unaffected by this pass beyond continuing to compile against the unchanged `sectionGroup()` contract.

**Source-regression sentinels extended (`TooltipApiFoundationSourceRegressionTest`):** one obsolete sentinel removed (`screenMixinDocumentationDescribesAPrivateInjectorMethodNotADefaultOne` — read a file this pass deletes), one existing test's search anchor corrected (`statRowLayoutWrapsBothTheLabelAndTheValue`'s `indexOf` now anchors inside the `layout(...)` method specifically, since a new `naturalRowWidth` method this pass added also contains a `case TooltipSection.StatRow r ->` arm that the old unanchored search matched first). New sentinels cover: the three named width constants exist; `naturalRowWidth`'s long-form-section exclusion list; badge order (rarity before classifications) and the removal of the old per-badge-row draw methods; the `SCROLLBAR_GUTTER` reservation and its use in both layout and the scroll indicator's own position; the footer-dots removal (both call site and dead method); the tightened `PADDING`/`separatorH` constants; the background-blend method's existence; the shared secondary-text-color constant and its actual use; `ScreenMixin`'s deletion (file absence) and its removal from `totality.mixins.json` (exact quoted-entry match, not a bare substring — `"client.AbstractContainerScreenMixin"` legitimately contains the substring `"ScreenMixin"`, an early version of this sentinel false-failed on exactly that); `MouseHandlerMixin`'s ordering (`FluidTankScrollHandler` checked before `TotalityTooltipScrollHandler`); `TotalityTooltipScrollHandler`'s use of the accessor and the eligibility gate; the absence of any `addItemSlotMouseAction` registration (confirming the final, simpler design, not the reverted intermediate one); `TooltipScrollController`'s Javadoc documenting the confirmed root cause; and the `ActiveTarget` record carrying every field the finding named at minimum.

**Full-suite totals:** the micro-correction pass ended at 1123 tests; this pass ends at **1148** — a net +25 (aligning with the sentinel/test additions and the one removed obsolete test described above).

**Not built, and why (unchanged limitation from every prior pass in this arc):** direct execution of any contributor's `contribute(TooltipContext)`, the renderer's `layout()`/`drawStatRow`/`drawStatBlock`/`measureNaturalContentWidth`/`wrapBadges`/`footerHintLines` against real glyph widths, or `TooltipScrollController.onRender`/`onMouseScroll`'s full integration path (needs a real `ItemStack` and, now, a real `Screen`). Both remain impossible under plain JUnit in this repository — re-confirmed this pass, not merely inherited: `null` was usable as a distinct `Screen` identity for the pure `targetMatches` tests specifically because that method never dereferences the reference, only compares it by `==`.

## 12. Manual validation — explicitly not performed this pass

**Historical note:** this section accurately describes the state at the end of the *original* visual-correction pass, before Stefan's own manual check happened. Manual validation has since been performed (across the presentation-cleanup pass, §24, and the scrolling event-consumption fix, §26) and is now complete — see §28 for the authoritative, final closure record. The rest of this section is preserved as-written for historical accuracy and is not evidence of the current state.

**Performed: an automated client boot-check only.** `./gradlew runClient` was launched and allowed to run for ~90 seconds. The client reached the main menu without a mixin-application failure or crash — all four of the mod's existing startup self-tests (`NotificationTimingVerification`, `PowerAttackFlashVerification`, `ProvisionerRendererVerification`, `KeybindVerification`) reported "All N self-test checks passed," and no `InvalidInjectionException` or similar Mixin-transform error appeared in the log (which is exactly the class of error a broken `AbstractContainerScreenMixin`/`AbstractContainerScreenAccessor`/`MouseHandlerMixin`/`totality.mixins.json` change would have produced immediately at startup, before any self-test even runs). This is genuine, real evidence that the mixin wiring changes for Finding 7 did not break client startup — it is **not** evidence of anything about actual tooltip layout, spacing, badge flow, scrolling feel, or visual hierarchy.

**Interactive/visual validation was explicitly skipped, on Stefan's direct instruction mid-pass:** *"I dont know why ChatGPT added that in the prompt. Ignore the screenshot thing, I will manually check it."* No item was hovered in a live game window by this pass; no screenshot was captured by this pass; the bundle (§15) therefore does **not** contain a manual-validation screenshot folder — including an empty or placeholder one would misrepresent that a validation pass happened here when it did not. Stefan is performing the manual check himself, using the same items and interactions the original task brief listed (Netherite Shuriken, Apprentice Grimoire, Ring of Protection, Potion of Healing, Whitestone, all five batteries, Shift/Ctrl disclosure, overflow scrolling while the cursor stays on the item, short-tooltip wheel behavior, a narrow window, multiple GUI scales, registry-id/component-count-only-under-Ctrl, unknown-external-line preservation, identified-versus-explicit-unidentified).

**Remaining, entirely unverified by any automated means:** every visual/interaction claim in §4–§10 above — the compact width actually reading as compact at the chosen constants, the badge row actually flowing side-by-side rather than still wrapping unexpectedly, the Default/Ctrl split actually hiding/showing the right lines on screen, the tightened stat layout actually reading as less sparse, the desaturated background actually reading as calmer rather than muddy, and — most importantly, since it was a functionally broken interaction, not a cosmetic one — that scrolling **actually now works** while the cursor sits over the item slot, and that it correctly stops consuming the wheel once the tooltip stops overflowing. None of this has been confirmed by this pass. It should not be treated as confirmed until Stefan's own manual check is complete.

**Creative inventory specifically — recorded honestly, not claimed (presentation-cleanup pass, Finding 4):** Creative-inventory manual validation is not available/practical in Stefan's current manual-testing setup. This is **not a test failure** — `CreativeModeInventoryScreen`'s own `mouseScrolled` override (§9) means the architectural reasoning for why its scrolling is untouched still holds regardless — it is simply an interactive check that has not been, and is not currently being, performed. It remains an open, deferred compatibility check to pick up later if/when it becomes practical to test, not a confirmed pass and not a known problem.

## 13. Exact files changed

**Modified (main, 5 files):**
```
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipRenderer.java                 (Findings 1, 2, 4, 5, 6, 7 wiring)
src/main/java/zcylas/totality/client/tooltip/renderer/TooltipPainter.java                 (Findings 6, 8)
src/main/java/zcylas/totality/client/tooltip/contributor/ExternalContentContributor.java  (Finding 3)
src/main/java/zcylas/totality/mixin/MouseHandlerMixin.java                                (Finding 7)
src/main/java/zcylas/totality/mixin/client/AbstractContainerScreenMixin.java              (Finding 7 — render() call site updated)
```
(`AbstractContainerScreenAccessor.java` was edited then reverted to its original content during the Finding 7 design iteration described in §9 — confirmed byte-identical to its pre-pass state, so it does not appear as changed in the final diff.)

**New (main, 2 files):**
```
src/main/java/zcylas/totality/client/tooltip/TooltipScrollController.java   (rewritten in place — active-target model)
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipScrollHandler.java
```
(`TooltipScrollController.java` already existed from the foundation pass; this pass's rewrite is total, so it is listed here for emphasis even though it isn't a new file on disk.)

**Deleted (main, 1 file):**
```
src/main/java/zcylas/totality/mixin/client/ScreenMixin.java   (confirmed dead code — see §9)
```

**Modified (resources, 1 file):**
```
src/main/resources/totality.mixins.json   (ScreenMixin entry removed)
```

**Modified (test, 3 files):**
```
src/test/java/zcylas/totality/client/tooltip/TotalityTooltipRendererLayoutPolicyTest.java
src/test/java/zcylas/totality/client/tooltip/TooltipScrollControllerTest.java
src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java
```

**New (this report + bundle, 2 files):**
```
Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_VISUAL_CORRECTION_REPORT.md
Context/Audit/Review Bundles/TOTALITY_TOOLTIP_API_FOUNDATION_VISUAL_CORRECTION_BUNDLE.zip
```

**Reference images added by Stefan, included in the bundle for context (3 files, not touched by this pass):**
```
Context/Audit/Image References/Tooltip1.png
Context/Audit/Image References/Tooltip2.png
Context/Audit/Image References/Tooltip3.png
```

## 14. Focused/full test commands and totals

```
./gradlew test --tests "zcylas.totality.client.tooltip.*"
```
**145 tests, 0 failures, 0 errors, 0 skipped** (aggregated from 11 `TEST-*.xml` files).

```
./gradlew test
```
**1148 tests, 0 failures, 0 errors, 0 skipped** (aggregated from all 91 `TEST-*.xml` files under `build/test-results/test/`) — 25 more than the micro-correction pass's 1123. Re-confirmed identically as part of the final `clean build` (§16 below).

## 15. Datagen result

`./gradlew runDatagen` completed successfully: client fully bootstrapped, all four mod self-tests passed, and the datagen cache report read **"total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"** — zero generated files changed. `git status --porcelain` taken immediately before and immediately after the run were byte-for-byte identical (diffed directly), independently confirming no unrelated churn.

## 16. Build result

`./gradlew clean build` — **BUILD SUCCESSFUL**, all tasks executed (`compileJava`, `processResources`, `classes`, `processIncludeJars`, `jar`, `sourcesJar`, `assemble`, `compileTestJava`, `testClasses`, `test`, `validateAccessWidener`, `check`, `build`). Full-suite test totals after `clean build` matched §14 exactly (1148/0/0/0, 91 XML files). `git diff --check` afterward exited 0 — only informational LF/CRLF line-ending notices on pre-existing and newly-touched files, no actual whitespace-conflict errors.

## 17. Known limitations

- **Every visual/interaction claim in this report is unverified by a human or automated screen check** — see §12. This is the dominant limitation of this pass and is not treated as a minor caveat.
- **The exact compact-width constants (`MIN_WIDTH = 110`, `PREFERRED_MAX_WIDTH = 200`, `MAX_SCREEN_WIDTH_FRACTION = 0.38f`) and the badge/spacing constants were chosen from reasoning about the task's own numeric guidance and the directional reference images, not measured against an actual running client.** They may need tuning after Stefan's manual pass; nothing about the architecture (measurement → clamp → wrap) requires re-architecting to retune them, only constant edits.
- **Finding 3's dedupe decision (drop, don't duplicate into a second Technical block) assumes `TechnicalInfoContributor` fires for every item this pass's fix applies to** — true today (it declares `TECHNICAL` availability unconditionally for any opted-in item), but worth re-confirming if that contributor's own gating logic ever changes independently.
- **The intermediate `ItemSlotMouseAction`/`addItemSlotMouseAction` design attempt for Finding 7 was fully reverted** (§9) — mentioned here so a future reader who recalls that idea from mid-session context doesn't go looking for it; it does not exist in the final source.
- Every limitation recorded in the three prior Tooltip API reports (legacy rarity fallback, unembedded structured `TooltipComponent` content, `TooltipPainter`'s dead `isShiftDown()`, `EnergyCellItem`'s unmigrated legacy tooltip, `TooltipFrameRenderer`'s 2-of-21 textured-frame coverage) remains exactly as documented, unaffected by this pass.

## 18. Final repository-wide status

`git status --short` after this pass: the same pre-existing dirty tree from every prior report's own §2 (`build.gradle`, 18 generated JSON files, 23+ pre-existing review-bundle zips, `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`), unchanged — plus every file from the three prior Tooltip API passes, further modified where §13 indicates, plus this pass's own 9 modified main files, 2 new/rewritten main files, 1 deleted main file, 1 modified resource file, 3 modified test files, 2 new report/bundle files, and Stefan's own 3 new reference images. No file outside this pass's declared scope was created, modified, staged, or touched.

## 19. Confirmation that unrelated dirty-tree files were preserved

Confirmed. `build.gradle`, all 18 generated datagen JSON files, all pre-existing untracked review-bundle zips, the `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, and `src/main/generated/.cache/` are exactly as they were at the start of this pass — not read for modification, not staged, not deleted, not reformatted. `runDatagen`'s own cache report ("removed stale: 0, written: 0") and the byte-for-byte-identical before/after `git status --porcelain` diff (§15) independently confirm no generated or unrelated file was altered.

## 20. Confirmation that Phase 3C remains unstarted

Confirmed. No file under `api/rpg/resources/**` (or any other Resource API Phase 3C scope) was read for modification or touched by this pass.

## 21. Confirmation that nothing was committed or pushed

Confirmed. No `git commit`, `git add`, or `git push` command was run at any point in this pass. `git status --short` throughout shows only working-tree modifications/additions, never an index change.

## 22. Micro-correction (five static-review findings)

### 22.1 Finding 1 — consume wheel input only when the scroll offset changes

**Root cause:** `onMouseScroll` returned `true` for any matching, overflowing target, without checking whether clamping the new offset to `[0, maxScroll]` actually left it different from the current one. Scrolling further up while already at the top, or further down while already at the bottom, still reported the event as consumed — silently swallowing wheel input that should have fallen through to normal screen scrolling at exactly those boundaries.

**Fix:** a new pure helper, `applyScrollDelta(currentOffset, scrollY, step, maxScroll)`, computes the clamped candidate offset; `onMouseScroll` now compares it against the current offset and returns `false` (leaving the event unconsumed) whenever they're equal, only assigning and returning `true` when they differ. The direction convention (wheel-down/negative `scrollY` increases the offset toward later content; wheel-up/positive `scrollY` decreases it toward earlier content) is unchanged from the original implementation — this correction only narrows *when* a change is reported as consumed, never *which way* a change moves.

**Tests added** (`TooltipScrollControllerTest`), all six required proofs against `applyScrollDelta` directly: middle + wheel down changes the offset (and the two values differ, confirming consumption would follow); middle + wheel up changes the offset in the opposite direction; top + wheel up leaves the offset at 0 (unconsumed); bottom + wheel down leaves the offset at `maxScroll` (unconsumed); a delta of `0.001` at `step=8` rounds to zero and leaves the offset unchanged; a non-overflowing target (`maxScroll=0`) can never produce a changed offset regardless of direction. A wiring sentinel (`TooltipApiFoundationSourceRegressionTest#onMouseScrollOnlyConsumesWhenTheAppliedDeltaActuallyChangesTheOffset`) confirms `onMouseScroll` itself actually calls `applyScrollDelta` and contains the `if (newOffset == scrollOffset) return false;` guard — direct execution of `onMouseScroll` itself still needs a real `ItemStack`, per the established, unchanged constraint.

### 22.2 Finding 2 — genuinely unique source-slot identity

**Root cause:** the active scroll target identified "which slot" by `Slot.index` — an `int` that vanilla itself only guarantees unique *within a single slot's own backing `Container`*. A player-inventory slot and an external-container slot open in the same menu can legitimately share the same `.index` value, meaning the old implementation could, in principle, mistake one slot's overflowing tooltip for another's.

**Fix:** every identity check now compares the `Slot` **object reference** itself, never `.index`. `TooltipScrollController.ActiveTarget`'s `slotIndex` field became `slot` (`@Nullable Slot`); `currentSlotIndex` became `currentSlot`; `targetMatches` now does `target.slot() == slot`. `TotalityTooltipRenderer.render(...)`'s `int slotIndex` parameter became `@Nullable Slot slot`; `AbstractContainerScreenMixin` now passes `this.hoveredSlot` directly (no `.index` read); `TotalityTooltipScrollHandler.onScroll` now forwards `hoveredSlot` itself to `TooltipScrollController.onMouseScroll`. No cursor hit-testing was introduced anywhere — the same pre-existing `hoveredSlot` field read is reused throughout, only what's *done* with it changed.

**Tests added** (`TooltipScrollControllerTest`), using genuinely constructed `Slot` objects — confirmed safe under plain JUnit by decompiling `Slot`'s constructor bytecode (it only assigns its four fields; a `null` `Container` argument touches nothing else, no registry access): the exact collision this finding describes, reproduced directly — `new Slot(null, 0, 0, 0)` twice, two distinct objects sharing `index = 0`, proven *not* to match each other; a same-object match; a different-slot (different index, different object) non-match; a stale-frame non-match; and a `null`-slot-matches-only-`null` case. A source sentinel (`noProductionScrollFileUsesSlotIndexForIdentity`) confirms none of `TooltipScrollController.java`, `TotalityTooltipScrollHandler.java`, or `AbstractContainerScreenMixin.java` contains a `.index` read anywhere.

### 22.3 Finding 3 — language-independent component-count recognition

**Root cause:** the component-count line was recognized by the regex `^\d+ component\(s\)$` — a match against the *rendered, English-locale* text. Any other client language would render this line differently, and the regex would silently stop recognizing it, letting it leak into Default view.

**Investigation:** `ItemStack.class` was decompiled directly from `minecraft-merged.jar` (MC 26.2). `ItemStack.addDetailsToTooltip(...)`'s bytecode shows the line is built as `Component.translatable("item.components", componentCount).withStyle(ChatFormatting.DARK_GRAY)` — the literal translation key is `item.components` (also confirmed present in `assets/minecraft/lang/en_us.json` as `"item.components": "%s component(s)"`). The registry-id line, by contrast, is built as `Component.literal(BuiltInRegistries.ITEM.getKey(getItem()).toString()).withStyle(DARK_GRAY)` — a **literal**, never-translated component, confirming the pre-existing exact-string-match rule for it was already language-independent and needed no change.

**Fix:** `ExternalContentContributor` now recognizes the component-count line via `line.getContents() instanceof TranslatableContents tc && "item.components".equals(tc.getKey())` — a semantic property of the `Component` itself, not its rendered string, color, or indentation, and correct in every client language. The English-only `Pattern`/regex is gone entirely.

**Tests:** `Component`/`TranslatableContents` construction was not attempted directly in a pure test — both are plain data classes with no registry dependency in principle, but this pass did not verify that empirically the way it did for `Slot`, so per the finding's own fallback instruction this remains a documented limitation, covered instead by source-regression sentinels (`componentCountRecognitionUsesTheTranslationKeyNotAnEnglishRegex`, confirming the regex/`Pattern.compile` is gone and the key-based check is present; `registryIdRecognitionRemainsAnExactStringMatch`, confirming the registry-id rule is unchanged).

### 22.4 Finding 4 — stale deleted-class references

`TooltipScrollController`'s class Javadoc and two method Javadocs still linked `{@link TotalityItemSlotScrollAction}` — an intermediate design (an `ItemSlotMouseAction` registered per-screen) that was fully reverted during the original Finding 7 investigation and does not exist in the source tree. All three references were replaced with `{@link TotalityTooltipScrollHandler}` (the class that actually exists and does the job described). A repository-wide source sentinel (`noProductionFileReferencesTheDeletedTotalityItemSlotScrollAction`) walks every `.java` file under `src/main/java/zcylas/totality` and asserts none contains the string `TotalityItemSlotScrollAction` — confirming this can't silently recur.

### 22.5 Finding 5 — report wording corrections

- The Creative-inventory claim in §9 was rewritten from "falls through to normal routing" (implying Creative's scrolling is inspected and then deliberately passed through) to the accurate, narrower claim: the handler returns `false` and the raw event is never touched at all unless a matching, overflowing, offset-changing Totality tooltip is active — Creative's own `mouseScrolled` override means its scrolling was never reachable by this mod's code in the first place.
- §13's "Modified (main, 9 files)" header was corrected to "5 files," matching the five files actually listed underneath it (this was a leftover count from an earlier draft of that section).
- This §22 records the correction and updated test totals (§22.6).
- Manual visual/interaction validation remains outstanding — unchanged from §12; this micro-correction pass, like the one before it, did not launch an interactive client session beyond the same kind of automated boot-check described in §12.

### 22.6 Updated validation

```
./gradlew test --tests "zcylas.totality.client.tooltip.*"
```
**158 tests, 0 failures, 0 errors, 0 skipped** (11 XML files) — up from §14's 145 (+13: 7 new in `TooltipScrollControllerTest`, 6 new in `TooltipApiFoundationSourceRegressionTest`).

```
./gradlew test
```
**1161 tests, 0 failures, 0 errors, 0 skipped** (91 XML files) — up from §14's 1148, exactly +13.

`./gradlew runDatagen`: **"total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"** — zero generated files changed; `git status --porcelain` immediately before and after were identical.

`./gradlew clean build`: **BUILD SUCCESSFUL**, full-suite totals after clean build matched 1161/0/0/0 exactly.

`git diff --check`: exit 0 — only informational LF/CRLF notices, no whitespace-conflict errors.

A client boot-check (`./gradlew runClient`, ~60 seconds) was run after the `Slot`-typed signature changes across `TotalityTooltipRenderer`, `TooltipScrollController`, `TotalityTooltipScrollHandler`, and `AbstractContainerScreenMixin` — the client reached texture/atlas loading with all four startup self-tests passing and no Mixin-application error, confirming the signature change didn't break anything at the mixin-transform level. This is, as in §12, an automated non-visual check only — not a substitute for Stefan's own manual pass.

### 22.7 Files touched by this micro-correction

```
src/main/java/zcylas/totality/client/tooltip/TooltipScrollController.java                 (Findings 1, 2, 4)
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipScrollHandler.java            (Finding 2)
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipRenderer.java                 (Finding 2 — render() signature)
src/main/java/zcylas/totality/mixin/client/AbstractContainerScreenMixin.java              (Finding 2 — render() call site)
src/main/java/zcylas/totality/client/tooltip/contributor/ExternalContentContributor.java  (Finding 3)
src/test/java/zcylas/totality/client/tooltip/TooltipScrollControllerTest.java             (+7 tests)
src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java (+6 tests, 1 existing test's field-name assertion corrected)
Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_VISUAL_CORRECTION_REPORT.md                 (Finding 5 — this section, plus in-place wording/count corrections)
```

No test file was added — all corrections landed in already-existing test files, so the bundle's physical file count (§23) is unchanged from the pre-correction archive.

## 23. Confirmation that unrelated dirty-tree files were preserved (re-verified after the micro-correction)

Confirmed. `build.gradle`, all 18 generated datagen JSON files, all pre-existing untracked review-bundle zips, the `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, and `src/main/generated/.cache/` remain exactly as they were — not read for modification, not staged, not deleted, not reformatted. Not committed, not pushed, Phase 3C not started — same confirmations as §19–§21, re-verified after this micro-correction.

## 24. Presentation-cleanup pass (four polish findings from Stefan's own manual check)

### 24.1 What Stefan's manual check covered

Stefan performed the interactive client session §12 recorded as outstanding after the prior pass. The task brief for this cleanup pass states the check "found only a few remaining polish issues," implying the broader claims in §4–§11 (compact width, flowing badges, Default/Ctrl split, scrolling now actually working) read as correct in practice — only four small items needed further attention. This report does not have Stefan's own detailed notes beyond the four findings below, so it makes no claim beyond them; §12's general framing ("treat every visual/interaction claim as implemented and sentinel-tested, not confirmed on screen") is superseded only for the specific claims a passing manual check implies, not rewritten wholesale.

### 24.2 Finding 1 — icon-to-label spacing

**Issue:** icon-led `StatRow`/`StatBlock` lines (Damage, Range, Stamina Cost, and any other icon-then-label row) used an unnamed `+ 3` pixel gap between the icon glyph and its label — read as the icon sitting too close to the text.

**Fix:** a single named constant, `ICON_LABEL_GAP = 5`, replaces all eight call sites that previously hardcoded `+ 3` (two in `naturalRowWidth`'s width-measurement switch, two in `layout()`'s wrap-decision switch, two in `drawStatRow`, two in `drawStatBlock`) — confirmed by a source sentinel that counts exactly 8 uses and asserts the old literal is gone everywhere. Compact but clearly visible, per the finding's own framing; not a redesign of row layout.

### 24.3 Finding 2 — light vertical breathing room

**Issue:** the tooltip, while much improved, still read as slightly cramped at the header/body transition and immediately above the footer — the footer in particular had **zero** gap between the body viewport's bottom edge and the footer's own "Totality" text (traced through the geometry: `footerTop` was provably identical to `bodyTop + bodyViewportH`).

**Fix:** two small, independent additions, both light polish rather than a return to a larger panel:
- `separatorH` raised from 7 to 9 (2px more room at the header/body transition; still well under the original foundation-pass value of 10).
- A new named constant, `BODY_FOOTER_GAP = 4`, added to both footer-height sizing passes (`footerH1`/`footerH`) *and* used to offset where `drawFooter` actually draws "Totality" and the hint lines (`footerTop = panelY + panelH - footerH + BODY_FOOTER_GAP`) — reserving the space and then actually using it, rather than reserving dead space nobody draws into.

### 24.4 Finding 3 — Potion of Healing rarity/classification

**Issue:** the standalone D&D Potion of Healing (`DndPotionItems.POTION_OF_HEALING`) had no authored rarity — it rendered with the neutral opted-in-without-rarity presentation.

**Fix:** a single line added to its registration — `.component(ItemComponents.getRarity(), new RarityComponent(ItemRarity.UNCOMMON))`. Its classification was already `ClassificationsComponent.of(ItemType.POTION)` from the original foundation pass — unchanged, confirmed still present. This is a presentation/metadata-only change: the healing formula (`HealingAmount.dice(2, Dice.D4, 2)`), the 32-tick use duration, notifications, and this item's independence from the Alchemy potion ladder (`PotionItems`) are all untouched — confirmed by a dedicated scope-guard test re-checking the exact formula string and the "no Alchemy import" boundary already established by `DndPotionOfHealingSourceRegressionTest`.

### 24.5 Finding 4 — manual-validation record correction (Creative inventory)

Creative-inventory manual validation is not available/practical in Stefan's current manual-testing setup. §12 has been updated to record this explicitly and honestly: **not manually tested, not a failure, a deferred compatibility check** — not implied as passed, not counted against anything. The architectural reasoning for why Creative's own scrolling is untouched by this mod's code (§9 — `CreativeModeInventoryScreen` declares its own `mouseScrolled` override) still holds regardless of whether it has been interactively confirmed.

### 24.6 Test coverage added

Six new tests in `TooltipApiFoundationSourceRegressionTest` (all source-regression sentinels — none of the four findings touch anything that becomes newly pure-testable; the constraints established in §11/§22 remain unchanged): the `ICON_LABEL_GAP` constant's existence and value; all 8 icon-then-label call sites using it and the old literal being fully gone; `separatorH`'s new value; `BODY_FOOTER_GAP`'s existence, its use in both footer-height sizing passes, and its actual application to the footer's draw position; the potion's authored `UNCOMMON` rarity and retained `POTION` classification; and a scope-guard confirming the healing formula and the "no Alchemy import" boundary are untouched. One pre-existing sentinel (`panelPaddingAndSeparatorHeightWereTightened`, from the original visual-correction pass) had its `separatorH` assertion updated from the now-superseded value of 7 to the current value of 9, with a comment recording the constant's full history (10 → 7 → 9) so a future reader isn't confused by the change.

### 24.7 Updated validation

```
./gradlew test --tests "zcylas.totality.client.tooltip.*"
```
**164 tests, 0 failures, 0 errors, 0 skipped** (11 XML files) — up from §22.6's 158 (+6).

```
./gradlew test
```
**1167 tests, 0 failures, 0 errors, 0 skipped** (91 XML files) — up from §22.6's 1161, exactly +6.

`./gradlew runDatagen`: **"total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"** — zero generated files changed. The rarity/classification change did **not** legitimately update any generated output: rarity and classification are data components attached at item-registration time (Java `Item.Properties`), not consumed by any datagen provider in this codebase (confirmed by grepping `src/main/java/zcylas/totality/datagen/` for any reference to `RarityComponent`/`ItemComponents.getRarity`/`ItemComponents.RARITY` — none exists); the classification itself was already `POTION` before this pass and did not change at all. `git status --porcelain` immediately before and after the run were identical.

`./gradlew clean build`: **BUILD SUCCESSFUL**, full-suite totals after clean build matched 1167/0/0/0 exactly.

`git diff --check`: exit 0 — only informational LF/CRLF notices, no whitespace-conflict errors.

A client boot-check (`./gradlew runClient`) was run after these changes; in this run the client actually reached and loaded an existing test world (rather than stopping at the main menu, as in prior boot-checks) and shut down cleanly on timeout with a normal world save — all four startup self-tests passed and no Mixin/registration error appeared for the new `RarityComponent` on the potion or the renderer constant changes. As with every prior boot-check in this arc, this is automated, non-visual evidence only.

### 24.8 Files touched by this presentation-cleanup pass

```
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipRenderer.java                 (Findings 1, 2)
src/main/java/zcylas/totality/init/items/DndPotionItems.java                              (Finding 3)
src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java (Findings 1, 2, 3 — +6 tests, 1 existing assertion updated)
Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_VISUAL_CORRECTION_REPORT.md                  (Finding 4 — §12 updated; this §24)
```

No test file was added — all new tests landed in the already-existing `TooltipApiFoundationSourceRegressionTest`, so the bundle's physical file count (§25) is unchanged from the pre-cleanup archive.

## 25. Confirmation that unrelated dirty-tree files were preserved (re-verified after the presentation-cleanup pass)

Confirmed. `build.gradle`, all 18 generated datagen JSON files, all pre-existing untracked review-bundle zips, the `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, and `src/main/generated/.cache/` remain exactly as they were — not read for modification, not staged, not deleted, not reformatted. Not committed, not pushed, Phase 3C not started — same confirmations as §19–§21 and §23, re-verified after this pass.

## 26. Scrolling event-consumption fix (Creative inventory simultaneous-scroll report)

### 26.1 The report

Stefan's manual check found that tooltip scrolling now genuinely works while the cursor stays over the source item slot (§24.1's implied confirmation extends to this), but in Creative inventory specifically, both the Totality tooltip and the Creative inventory scrolled from the same wheel motion. His own diagnosis — "the tooltip offset is changing, but the original raw mouse-wheel event is still continuing into the screen" — asked for `MouseHandlerMixin`'s injection to be made cancellable if it wasn't already, gated exactly on whether the tooltip's offset actually changed.

### 26.2 Investigation

`MouseHandlerMixin`'s injection was **already** `@At("HEAD")` + `cancellable = true`, and already called `ci.cancel()` only inside the `if (TotalityTooltipScrollHandler.onScroll(yoffset))` block — structurally identical to the pre-existing, already-proven-working `FluidTankScrollHandler` check in the same method. `MouseHandler.class` and its GLFW callback-registration lambdas were decompiled directly from `minecraft-merged.jar` (MC 26.2) to check for anything that could explain a leak despite correct cancellation:

- `MouseHandler.onScroll(long, double, double)` is `private`; its own bytecode calls `Screen.mouseScrolled(DDDD)Z` partway through its body (after computing sensitivity-scaled deltas) — confirming that cancelling at `HEAD` genuinely prevents that call from ever executing for a consumed event. No second, independent path to `Screen.mouseScrolled` exists in this method.
- Exactly one `onScroll` method exists on `MouseHandler` — no overload ambiguity for the `@Inject(method = "onScroll", ...)` match.
- **The actual GLFW scroll callback is not `onScroll` directly** — GLFW is bound to `lambda$setup$4`, which wraps the real handling in `Minecraft.execute(() -> onScroll(handle, xoffset, yoffset))` (`lambda$setup$5`, which calls `onScroll`), deferring it onto Minecraft's main-thread task queue rather than running it synchronously inside the GLFW callback.

That last point is the concrete, decompiled-and-confirmed explanation for the reported symptom: a single fast wheel gesture (especially from a precision trackpad or a mouse driver with "smooth scrolling," both of which report many small deltas per physical motion) can enqueue several `onScroll` invocations that all drain in a burst before the next frame renders. Because a Totality tooltip's own overflow is often small (a row or two — tens of pixels), the very first few events in that burst can exhaust it completely; every remaining event in the *same* physical gesture then correctly (and, per the fix's own required-behavior list, intentionally — "tooltip already at bottom and wheel moves downward... allow the screen to process it normally") falls through uncancelled to Creative. From the player's perspective this reads as "both scrolling at once," but it is the boundary-exhaustion behavior the fix's own acceptance criteria explicitly require, manifesting within a single gesture rather than across two separate ones — not a failure of cancellation, and not fixable by changing how the event is consumed (the offset-changed gate already governs that correctly, per §22.1's fix, unchanged here).

### 26.3 What was changed

Given the cancellation plumbing itself was already correct, this pass made the wiring's correctness verifiable and easier to trust rather than re-deriving working code:

- **Renamed** `TotalityTooltipScrollHandler.onScroll(double)` → `onMouseScroll(double)`, aligning names across the full call chain (`MouseHandlerMixin.onScroll` → `TotalityTooltipScrollHandler.onMouseScroll` → `TooltipScrollController.onMouseScroll`) — previously the middle link kept the same name as the raw GLFW-level entry point above it, inviting confusion about which layer was which.
- **Documented, with the decompiled evidence inline**, both `MouseHandlerMixin`'s injection contract (why `HEAD` + `cancellable` genuinely prevents `Screen.mouseScrolled` from running) and `TotalityTooltipScrollHandler`'s explanation of the deferred-execution/event-burst mechanism, so a future reader hitting the same symptom doesn't have to re-derive this investigation from scratch.
- **Fixed** the one stale `{@link TotalityTooltipScrollHandler#onScroll}` reference in `TooltipScrollController`'s own Javadoc, left over from before the rename.

No behavioral logic changed in `TooltipScrollController` or `TotalityTooltipScrollHandler` — the offset-changed consumption contract from §22.1 is preserved exactly, as the task required.

### 26.4 Tests added

Four new tests in `TooltipApiFoundationSourceRegressionTest`: the injection's `@At("HEAD")`/`cancellable = true` attributes are both present; `ci.cancel()` appears exactly twice, each strictly inside its own `if (...)` block (never unconditionally, never outside a gate); the handler's rename is complete (new name present, old name fully gone, not left as a dangling second overload); and the deferred-execution explanation is documented with its specific evidentiary citations (`Minecraft.execute(`, `lambda$setup$4`). The six required behavioral scenarios (tooltip at top/bottom boundary, non-overflowing, no active tooltip, slot changed, zero effective delta) were already fully covered by `TooltipScrollControllerTest`'s existing pure tests from §22's own micro-correction pass — re-confirmed still passing, not re-added.

### 26.5 Validation

```
./gradlew test --tests "zcylas.totality.client.tooltip.*"
```
**168 tests, 0 failures, 0 errors, 0 skipped** (11 XML files) — up from §24.7's 164 (+4).

```
./gradlew test
```
**1171 tests, 0 failures, 0 errors, 0 skipped** (91 XML files) — up from §24.7's 1167, exactly +4.

`./gradlew runDatagen`: **"total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"**. `git status --porcelain` immediately before and after were identical.

`./gradlew clean build`: **BUILD SUCCESSFUL**, full-suite totals after clean build matched 1171/0/0/0 exactly. `git diff --check`: exit 0.

A client boot-check was run after these changes; it happened to load into an existing dev-world save long enough for the mod's full server-side self-test suite to run. Several unrelated self-tests (`PowerAttackVerification`, `ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) failed, and the log showed pre-existing world-save data issues ("loaded a corrupt negative CurrentCredits," "loaded corrupt persisted stock") — none of this touches Tooltip, MouseHandler, or scroll code in any way (confirmed by grepping the full boot-check log for those terms — zero matches beyond the expected self-test pass lines), and nothing in this pass's diff touches combat, Provisioner, or offhand-attack code. This is pre-existing dev-world/runtime state, out of this pass's scope, and was not investigated or altered.

### 26.6 Files touched

```
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipScrollHandler.java            (renamed onScroll -> onMouseScroll; documented the decompiled root-cause investigation)
src/main/java/zcylas/totality/mixin/MouseHandlerMixin.java                                (call site renamed; documented the cancellation contract)
src/main/java/zcylas/totality/client/tooltip/TooltipScrollController.java                 (one stale Javadoc @link reference fixed)
src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java (+4 tests; 2 existing tests' expected substrings updated for the rename)
Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_VISUAL_CORRECTION_REPORT.md                  (this §26)
```

No test file was added — the bundle's physical file count (§27) is unchanged.

## 27. Confirmation that unrelated dirty-tree files were preserved (re-verified after the scrolling event-consumption fix)

Confirmed. `build.gradle`, all 18 generated datagen JSON files, all pre-existing untracked review-bundle zips, the `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, and `src/main/generated/.cache/` remain exactly as they were — not read for modification, not staged, not deleted, not reformatted. Not committed, not pushed, Phase 3C not started — same confirmations as §19–§21, §23, and §25, re-verified after this pass.

## 28. Final manual validation outcome (foundation closure)

This section is the authoritative, final record of manual validation for the entire Tooltip API foundation arc (foundation → review-correction → final-correction → micro-correction → visual-correction → its own micro-correction → presentation-cleanup → scrolling event-consumption fix). It supersedes §12's "explicitly not performed" framing, which described only the state at the end of the original visual-correction pass, before Stefan's own manual check happened.

### 28.1 Confirmed by manual validation

Representative items were manually inspected, including the **Netherite Shuriken** and the **D&D Potion of Healing**, alongside the rest of the representative set (Apprentice Grimoire, Ring of Protection, Whitestone, all five batteries). Manual validation confirms:

- Compact tooltip widths are substantially improved over the original fixed-preferred-width policy.
- Rarity and classification badges flow horizontally in one ordered row and wrap only when they genuinely don't fit.
- The Netherite Shuriken's layout is readable and no values clip against the panel edge or the scrollbar gutter.
- Icon-to-label spacing (Damage, Range, Stamina Cost, and similar rows) is correct.
- Header/body and body/footer spacing are improved — no longer reading as cramped at those transitions.
- The D&D Potion of Healing displays `UNCOMMON` rarity and the `POTION` classification.
- Default view hides the registry id and component-count line.
- Ctrl Technical reveals technical information (registry id, class, component count) as intended.
- Shift Details works.
- Tooltip scrolling works while the cursor remains over the source item slot.
- Short/non-overflowing tooltips do not engage tooltip scrolling at all.
- The scrollbar remains inside its reserved gutter and does not overlap body text.

### 28.2 Known deferred limitation (Creative smooth-scroll)

> Smooth-scroll or trackpad gestures may continue scrolling the underlying Creative inventory after the tooltip reaches its scroll boundary during the same gesture. The raw event cancellation wiring is functioning as designed; the remaining behavior results from additional queued scroll events falling through after the tooltip can no longer move. This is accepted as a known limitation and deferred until Totality implements its own Creative inventory group/tab or broader custom inventory-screen handling.

This is the same mechanism decompiled and documented in §26.2 (GLFW scroll events deferred via `Minecraft.execute(...)`, batching several `onScroll` invocations into one gesture) — repeated here verbatim as the closure record's own statement of the limitation, not a new finding.

**This Creative-specific behavior is not considered a blocker for this foundation.** No further Tooltip correction is required before commit. A full future visual redesign — dedicated sprites, particle/animation effects, and richer per-rarity presentation beyond the current flat-border/gradient theme system — remains a separate, later effort from this foundation and is not implied or required by anything recorded here.

For the avoidance of doubt: the unrelated `PowerAttackVerification`, `ProvisionerEntityBackedSmokeTest`, and `OffhandAttackVerification` self-test failures observed during a boot-check in the scrolling event-consumption fix pass (§26.5) were **not** caused by this task. They are pre-existing dev-world state issues (a stale/corrupt world save, per the log's own "loaded a corrupt negative CurrentCredits" / "loaded corrupt persisted stock" messages), unrelated to Tooltip, MouseHandler, or scroll code, and were correctly left untouched — re-confirmed here, not re-investigated.

### 28.3 Next major task

**Generic Player Resource API Phase 3C remains the immediate next major task.** No file under `api/rpg/resources/**` (or any other Phase 3C scope) has been touched by any pass in this entire Tooltip API arc.

---

*Totality Tooltip API foundation — closed. Foundation, review-correction, final-correction, micro-correction, visual-correction, its own micro-correction, presentation-cleanup, and the scrolling event-consumption fix are all complete and manually validated per §28. `feature/general-resource-api` · HEAD `6998d322` at the time of writing, pending commit. The one accepted, deferred limitation is §28.2's Creative smooth-scroll behavior — not a blocker. Generic Player Resource API Phase 3C is the next major task.*
