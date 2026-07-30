# Totality Tooltip API Foundation — Final Correction Report

A second, narrow correction pass against the Tooltip API foundation, following the review-correction pass documented in `Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_REVIEW_CORRECTION_REPORT.md`. This pass does not touch the foundation architecture (`TooltipContext`/document/section model, ordered contributor registry, explicit `TooltipProfileComponent` opt-in, the temporary documented rarity fallback, multiple classifications, Artifact→Ancient, Shift/Ctrl disclosure, central scrolling, the eight review-correction fixes, or the representative battery/Grimoire/weapon/Ring/weight/healing-potion contributors) and does not begin Resource API Phase 3C. The previous eight-correction architecture remains exactly as it was; only the three findings below were corrected.

> **Micro-correction addendum.** A subsequent static review of the resulting bundle found that §5/§8 below overstated this pass's own work: `AttunementContributor` imported `TooltipSectionGroup` and was reported/manifested as declaring `ATTUNEMENT`, but the class never actually wrote the `@Override` — it silently inherited `TooltipContributor`'s default `PRIMARY`. This was a real production-wiring gap (Netherite Shuriken's and Ring of Protection's attunement content would not have grouped where §5's own worked examples claimed), not a documentation-only slip. Fixed; see §18 for the full correction, updated file list, and updated test totals. Every other claim in §1–§17 remains accurate.

---

## 1. Starting branch and HEAD

Branch: `feature/general-resource-api`
HEAD: `6998d3222ddc5274a6fca7e0dbd83c71fc3b7408`

Verified identical to the checkpoint before any file in this pass was touched.

## 2. Starting task-scoped status

`git status --short` for the whole repository matched the review-correction report's own §20 exactly — the same pre-existing unrelated dirty tree (`build.gradle`, 18 generated JSON files, 23+ pre-existing review-bundle zips, `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`), plus the foundation and review-correction passes' own files, untouched since the reviewed bundle. No unexplained change had occurred; the checkpoint was clean to correct.

## 3. The three findings and their exact corrections

| # | Finding | Correction |
|---|---|---|
| 1 | `TooltipKnowledgeView.of(stack)` read `TotalityItem.getIdentificationStatus(stack)`, which defaults a *missing* `IDENTIFICATION_STATUS` component to `UNIDENTIFIED` — a reasonable gameplay default, but one that incorrectly hid content on every existing Netherite Shuriken/Ring of Protection stack today, since nothing in the repository has ever granted `IDENTIFIED` status. | `of(ItemStack)` now reads the raw component directly — `stack.get(TotalityItemComponents.IDENTIFICATION_STATUS)` — instead of going through `getIdentificationStatus`. An absent component resolves to `IDENTIFIED`; an explicitly-present component (`UNIDENTIFIED`/`PARTIALLY`/`IDENTIFIED`) is honored exactly as authored. `TotalityItem.getIdentificationStatus`'s own gameplay-facing default is untouched — still `UNIDENTIFIED` — since this correction is deliberately scoped to the tooltip-compatibility read only. |
| 2 | Body sections were stable-sorted by `Comparator.comparingInt(kindRank)`, a single global rank per Java record *kind* — this destroyed contributor grouping. A weapon's `PropertyBadges` (rank 5) sorted after an unrelated contributor's `StatRow` (rank 4); a Ring's attunement status could sort ahead of its own AC/save-bonus and Requires-Attunement lines emitted by the very same contributor; a hypothetical multi-heading contributor would have every heading collected together (`Heading` was always rank 3) instead of staying attached to its own rows. | New `TooltipSectionGroup` enum (9 stable values: `PRIMARY`, `PROPERTIES`, `REQUIREMENTS`, `RESOURCES`, `ATTUNEMENT`, `WEIGHT`, `LORE`, `EXTERNAL`, `TECHNICAL`) plus `TooltipContributor.sectionGroup()` (default `PRIMARY`). The renderer now processes each contributor's output as one unit — every section a single `contribute()` call emits into the body becomes one `ContributorBlock` tagged with that contributor's declared group. Sections *within* a block are never reordered; only the resulting blocks are stable-sorted by `group.ordinal()`. `kindRank` and the old flat sort are deleted. |
| 3 | `StatRow`/`StatBlock` only wrapped the *value*; the label remained an unbounded raw string (drawn via `graphics.text(font, row.label(), ...)` with no width check at all). `footerHintLines` packed whole hint strings onto lines but never split an individual hint that was itself wider than the available width — a single over-wide hint (e.g. `"CTRL: Technical"` on a very narrow panel) would draw un-split, potentially past the panel edge. | `StatRow`/`StatBlock` layout now wraps the label via `Font.split` (icon reserved on the first line only) whenever the fast "fits on one line" check fails; the value is always placed below the full wrapped label. Height is computed from the real wrapped label-line count plus wrapped value-line count. `footerHintLines` now routes every packed line — including a single hint too wide to pack with anything else — through a new `splitHintLine` helper that calls `Font.split` directly, so no hint line can ever exceed the available width. `drawFooter`'s right-aligned hint-line X is clamped to `Math.max(minX, ...)` so it can never sit left of the panel's own inner padding. |

## 4. Finding 1 — exact mechanism

```java
public static TooltipKnowledgeView of(ItemStack stack) {
    if (!(stack.getItem() instanceof TotalityItem)) {
        return IDENTIFIED;
    }
    IdentificationStatus explicit = stack.get(TotalityItemComponents.IDENTIFICATION_STATUS);
    return explicit != null ? new TooltipKnowledgeView(explicit) : IDENTIFIED;
}
```

`stack.get(...)` (component-map lookup) returns `null` when the component is absent, as opposed to `stack.getOrDefault(..., UNIDENTIFIED)` (what `TotalityItem.getIdentificationStatus` calls) which manufactures a value. This is the entire fix: reading through the component map directly instead of through the gameplay-default-carrying accessor. **Nothing writes the component** — `of()` remains a pure read, so opening a tooltip still cannot create persistent identification state. The class Javadoc was rewritten to document this "deliberately narrower than `TotalityItem`'s own default" rule explicitly, including that a future Identification/Knowledge API becomes responsible for ever explicitly authoring `UNIDENTIFIED`/`PARTIALLY` on a stack — at which point this same check honors that authored state immediately, with no further change needed here. A repository-wide grep confirmed the premise this fix relies on: no production code anywhere calls `setIdentificationStatus(...)` (the only match is the default method's own declaration in `TotalityItem.java`) — every existing Shuriken/Ring/etc. stack today has no explicit component at all, so this fix is what actually restores their full tooltip.

`MetadataContributor`'s class Javadoc previously claimed "`TooltipKnowledgeView.of` always reports fully identified [for non-`TotalityItem` items]... it only matters for `TotalityWeaponItem`/`TotalityArmorItem`-family items, which default to unidentified" — this was true before this pass and **false** after it (those items now also default to identified). The paragraph was rewritten to state the corrected, current behavior: identification gating on rarity/lore has no visible effect on *any* current stack today, `TotalityItem`-based or not, since nothing yet authors an explicit unidentified/partially-identified component.

## 5. Finding 2 — exact grouping mechanism

```java
record ContributorBlock(TooltipSectionGroup group, List<TooltipSection> sections) {}

static List<TooltipSection> orderedBody(List<ContributorBlock> blocks) {
    List<ContributorBlock> sorted = new ArrayList<>(blocks);
    sorted.sort(Comparator.comparingInt(b -> b.group().ordinal()));
    List<TooltipSection> body = new ArrayList<>();
    for (ContributorBlock block : sorted) body.addAll(block.sections());
    return body;
}
```

`render()` now builds one `ContributorBlock` per contributor (skipped when a contributor's filtered body output is empty), in the same single pass that already builds the full `TooltipDocument` and extracts Header/RarityBadge/ClassificationBadges into shell state — no second pass over `document.sections()` is needed. `orderedBody` is `List.sort`, which is TimSort — **stable** — so multiple blocks sharing a group keep their contributor-registration relative order (verified by `blocksSharingAGroupPreserveContributorRegistrationOrder`, §7). `ContributorBlock` and `orderedBody` are package-private (not `private`), specifically so the ordering algorithm itself is unit-testable with real `TooltipSection` records and no `ItemStack`/`Font` involved, matching this repository's established pattern for `effectiveContentWidth`/`availableBodyHeight`/`fitsOnOneLine`/`footerHintFlags`.

**Group assignments given to the six contributors that needed a non-default tag** (the remaining five — `WeaponContributor`, `EnergyContributor`, `GrimoireContributor`, `HealingPotionContributor` — keep the default `PRIMARY`, minimizing the diff, since "primary functional identity" is exactly what they are):

| Contributor | Group |
|---|---|
| `AttunementContributor` | `ATTUNEMENT` |
| `FuelContributor` | `RESOURCES` |
| `WeightContributor` | `WEIGHT` |
| `LegacyExtensionAdapterContributor` | `EXTERNAL` |
| `ExternalContentContributor` | `EXTERNAL` |
| `TechnicalInfoContributor` | `TECHNICAL` |
| `MetadataContributor` | `LORE` (only its `Description`/lore section ever reaches the body — Header/RarityBadge/ClassificationBadges are shell, extracted before grouping runs) |

**Verified against the two named representative items, by inspecting each contributor's own internal emission order** (both already matched the required final order with zero internal reordering needed — only the group tag was new):

- **Netherite Shuriken**: `WeaponContributor` (`PRIMARY`) emits, in order, `Heading("Weapon")` → Damage `StatRow` → `PropertyBadges` → Category/Range/Stamina `StatRow`s, all as one block — exactly "Weapon heading → damage → properties → category/range/stamina." `AttunementContributor` (`ATTUNEMENT`, ordinal 4) sorts after. `MetadataContributor`'s lore (`LORE`, ordinal 6) and `ExternalContentContributor`/`LegacyExtensionAdapterContributor` (`EXTERNAL`, ordinal 7) sort last.
- **Ring of Protection**: `AttunementContributor` emits, in order, the AC/save-bonus `Requirement` → "Requires Attunement" `Requirement` → Attuned/Not-Attuned `StatRow`, all as one `ATTUNEMENT` block — exactly "AC/save bonus → Requires Attunement → Attunement status." `MetadataContributor`'s lore sorts after (`LORE`, ordinal 6).

## 6. Finding 3 — exact wrapping mechanism

**StatRow** (`layout()`'s `StatRow` case): the existing `fitsOnOneLine(iconW, labelW, valueW, gap, width)` fast-path check is unchanged — when it passes, both label and value still draw as raw single-line text (already provably safe, since the check confirms they fit together). When it fails, the label is now wrapped via `font.split(Component.literal(r.label()), width - iconW)` (icon reserved only on the first line), the value is wrapped via the same `font.split` call as before, and the laid-out height becomes `labelLines.size() * rowH + valueLines.size() * rowH` — the value always drawn below the *entire* wrapped label, so it can never overlap a later label line. `drawStatRow` gained a second draw path for this case; the fast-path draw is unchanged.

**StatBlock**: identical mechanism, per-line, inside the existing per-line `fitsOnOneLine` check — each `StatBlockLine` now carries both `labelLines` and `valueLines` (previously only `valueLines`), and `drawStatBlock` mirrors `drawStatRow`'s two-path structure.

**Footer**: `footerHintLines` still greedily packs whole hint strings onto as few lines as fit (unchanged packing logic), but every packed line is now routed through a new `splitHintLine(text, font, maxWidth)` helper before being added to the result — `font.split(Component.literal(text), maxWidth)`, falling back to a single-space split if the result is ever empty. This is what makes a single hint wider than `maxWidth` (the required proof case: `"CTRL: Technical"` under `MIN_SAFE_INNER_WIDTH = 60`) still wrap safely across multiple lines instead of drawing un-split. `footerHintLines` now returns `List<FormattedCharSequence>` (was `List<String>`) — `drawFooter` was updated to draw `FormattedCharSequence` lines via `TotalityGuiGraphics.drawString`, and its right-aligned X is now `Math.max(minX, panelX + panelW - PADDING - font.width(hintLine))` where `minX = panelX + PADDING`, so a wrapped line can never compute a negative/left-of-panel X. Footer height (`footerRowH * (1 + hintLines.size()) + footerDotsH + footerPadding`) already used the real line count before this pass and still does — it now reflects the *correctly split* count instead of a potentially-under-counted one.

## 7. Test coverage added

**Pure tests, no `ItemStack`/`Font`** — new file `TotalityTooltipRendererGroupingTest` (6 tests), exercising `TotalityTooltipRenderer.orderedBody` directly with real `TooltipSection` records:
- `netheriteShurikenOrderIsWeaponThenAttunementThenLore` — mirrors `WeaponContributor`'s and `AttunementContributor`'s exact internal emission order, fed to `orderedBody` in scrambled block order, asserts the exact required final sequence.
- `ringOfProtectionOrderIsAcBonusThenRequiresAttunementThenStatusThenLore` — same, for the Ring's required order.
- `eachHeadingStaysAttachedToItsOwnRowsRatherThanBeingGroupedWithOtherHeadings` — the required synthetic two-heading-block proof: `[headingA, rowA, headingB, rowB]` in one block, asserts the output keeps each heading immediately before its own row rather than both headings collected together.
- `blocksSharingAGroupPreserveContributorRegistrationOrder` — proves the stable-sort guarantee.
- `externalAndTechnicalGroupsAlwaysSortAfterEverySemanticGroup` — proves `EXTERNAL`/`TECHNICAL` always sort last regardless of input order.
- `emptyBlockListProducesAnEmptyBody` — trivial boundary case.

**Pure tests extended** — `TooltipKnowledgeViewTest` gained 4 tests (`explicitUnidentifiedIsHonoredExactly`, `explicitPartiallyIdentifiedIsHonoredExactly`, `explicitIdentifiedIsHonoredExactly`, `identifiedOnlyFilteringStillWorksAgainstAnExplicitUnidentifiedContext`) proving Finding 1's proof obligations #2, #3, and #5 (explicit statuses honored exactly; identified-only filtering still works against an explicit unidentified context) directly against the record's real `isVisible` logic.

**Source-regression sentinels extended** — `TooltipApiFoundationSourceRegressionTest` grew from 25 to 35 tests. Finding 1 (proof obligations #1 and #4 — missing component → identified; existing Shuriken/Ring presentation not unintentionally hidden — cannot be proven by direct execution since `of(ItemStack)` needs a real `ItemStack`, which cannot be constructed under plain JUnit in this repository): `tooltipKnowledgeViewOfReadsTheRawComponentInsteadOfGetIdentificationStatus`, `tooltipKnowledgeViewOfDefaultsAMissingComponentToIdentifiedNotUnidentified`, `tooltipKnowledgeViewOfNeverWritesTheIdentificationComponent`, `tooltipKnowledgeViewDocumentsTheFutureIdentificationApiResponsibility`, `totalityItemGetIdentificationStatusGameplayDefaultIsUnchangedByThisCorrectionPass`, `noProductionCodeExplicitlyAuthorsIdentificationStatusYet`. Finding 3 (needs a real `Font`, so remains sentinel-only, per the finding's own explicit allowance for "tests/sentinels"): `statRowLayoutWrapsBothTheLabelAndTheValue`, `statBlockLayoutWrapsBothTheLabelAndTheValueOfEachLine`, `footerHintLinesSplitsAnIndividualHintThatIsWiderThanMaxWidth`, `drawFooterClampsHintLineXSoItNeverDrawsLeftOfThePanel`.

**Not built, and why (unchanged limitation from both prior reports)**: direct execution of `TooltipKnowledgeView.of(ItemStack)`, any contributor's `contribute(TooltipContext)`, or the renderer's `layout()`/`drawStatRow`/`drawStatBlock`/`footerHintLines` against real glyph widths. All require a real `ItemStack`/`Item` or a bootstrapped `Font`, neither constructible under plain JUnit in this repository (`HealingPotionItemContractTest`'s class Javadoc). Finding 3's four required proofs (long StatRow label, long StatBlock label, long value, a footer width narrower than `"CTRL: Technical"`) are therefore sentinels confirming the wrapping *code path exists and is wired correctly* — not proof of the actual wrapped pixel output. That remains manual visual validation (§10).

## 8. Exact files changed

**Modified (main, 10 files):**
```
src/main/java/zcylas/totality/client/tooltip/TooltipKnowledgeView.java              (Finding 1)
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipRenderer.java           (Findings 2 + 3 — major rewrite)
src/main/java/zcylas/totality/client/tooltip/contributor/TooltipContributor.java    (sectionGroup() default method)
src/main/java/zcylas/totality/client/tooltip/contributor/MetadataContributor.java   (sectionGroup() override; stale doc fix)
src/main/java/zcylas/totality/client/tooltip/contributor/AttunementContributor.java (sectionGroup() override)
src/main/java/zcylas/totality/client/tooltip/contributor/FuelContributor.java       (sectionGroup() override)
src/main/java/zcylas/totality/client/tooltip/contributor/WeightContributor.java     (sectionGroup() override)
src/main/java/zcylas/totality/client/tooltip/contributor/LegacyExtensionAdapterContributor.java (sectionGroup() override)
src/main/java/zcylas/totality/client/tooltip/contributor/ExternalContentContributor.java        (sectionGroup() override)
src/main/java/zcylas/totality/client/tooltip/contributor/TechnicalInfoContributor.java          (sectionGroup() override)
```

**New (main, 1 file):**
```
src/main/java/zcylas/totality/client/tooltip/TooltipSectionGroup.java
```

**Modified (docs, 1 file):**
```
Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_IMPLEMENTATION_REPORT.md   (second addendum pointer)
```

**New (test, 1 file):**
```
src/test/java/zcylas/totality/client/tooltip/TotalityTooltipRendererGroupingTest.java   (6 tests)
```

**Extended (test, 2 files):**
```
src/test/java/zcylas/totality/client/tooltip/TooltipKnowledgeViewTest.java              (6 -> 10 tests)
src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java (25 -> 35 tests)
```

**New (this report + bundle, 2 files):**
```
Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_FINAL_CORRECTION_REPORT.md
Context/Audit/Review Bundles/TOTALITY_TOOLTIP_API_FOUNDATION_FINAL_CORRECTION_BUNDLE.zip
```

No file was deleted this pass. Net new test count: 20 (6 new file + 4 extended `TooltipKnowledgeViewTest` + 10 extended `TooltipApiFoundationSourceRegressionTest`) — exactly the difference between this pass's full-suite total (§9) and the review-correction pass's own recorded total of 1094.

## 9. Focused/full test commands and totals

```
./gradlew test --tests "zcylas.totality.client.tooltip.*"
```
**111 tests, 0 failures, 0 errors, 0 skipped** (aggregated from 11 `TEST-*.xml` files).

```
./gradlew test
```
**1114 tests, 0 failures, 0 errors, 0 skipped** (aggregated from all 91 `TEST-*.xml` files under `build/test-results/test/`) — 20 more than the review-correction pass's 1094, exactly matching §8's net-new test count. Re-confirmed identically as part of the final `clean build` (§11 below).

## 10. Datagen result

`./gradlew runDatagen` completed successfully: client fully bootstrapped, all four mod self-tests passed (`NotificationTimingVerification`, `PowerAttackFlashVerification`, `ProvisionerRendererVerification`, `KeybindVerification`), and the datagen cache report read **"total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"** — zero generated files changed. `git status --porcelain` taken immediately before and immediately after the run were byte-for-byte identical (diffed directly), independently confirming no unrelated churn was produced.

## 11. Build result

`./gradlew clean build` — **BUILD SUCCESSFUL**, all tasks executed (`compileJava`, `processResources`, `classes`, `processIncludeJars`, `jar`, `sourcesJar`, `assemble`, `compileTestJava`, `testClasses`, `test`, `validateAccessWidener`, `check`, `build`). Full-suite test totals after `clean build` matched §9 exactly (1114/0/0/0, 91 XML files). `git diff --check` afterward exited 0 — only informational LF/CRLF line-ending notices on pre-existing and newly-touched files, no actual whitespace-conflict errors.

## 12. Manual validation performed and remaining

**Performed: none.** This pass, like both before it, did not launch an interactive client session. All validation is through `test`/`runDatagen`/`clean build` — genuine proof of compilation and unit-level correctness, not of on-screen rendering, label wrapping, footer hint wrapping, or body-content ordering.

**Remaining** (unchanged scope from the review-correction report §18, plus this pass's own specific additions):
- An item with a genuinely long `StatRow`/`StatBlock` label (none exists in the current representative item set) — confirm the label wraps onto multiple lines, the icon stays with the first line only, and the value draws cleanly below the full wrapped label without overlap.
- A footer at the narrowest supported panel width — confirm `"CTRL: Technical"` wraps rather than being clipped or drawn past the panel edge, and that hint lines never visually overlap the footer dots.
- Netherite Shuriken and Ring of Protection specifically, now that Finding 1 restores their default-identified presentation — confirm both show their full tooltip (rarity, lore, attunement) by default in a live game, not the hidden-by-default presentation the review-correction pass's Finding 8 consequence (§8 of that report) had introduced.
- Netherite Shuriken's body order live — Weapon heading/damage/properties/category/range/stamina, then Attunement, then lore/external — confirm it visually reads as one coherent weapon block followed by one coherent attunement block, not interleaved.
- Ring of Protection's body order live — AC/save bonus, then Requires Attunement, then Attuned/Not Attuned, then lore.

Plus every item from the review-correction report's own still-outstanding list (all five batteries, all three Grimoires, a long custom-named item, Shift/Ctrl disclosure, a scrolling tall tooltip, a narrow window/multiple GUI scales, external-content preservation behind `WHEN_IDENTIFIED`, a structured `TooltipComponent` fallback, Ancient-versus-Legendary visual identity) — none of that list has been performed by any pass to date.

## 13. Known limitations (unchanged from prior passes, restated for completeness)

Every limitation recorded in the implementation report's §36 and the review-correction report's own findings remains exactly as documented — the legacy rarity fallback, unembedded structured `TooltipComponent` content, `TooltipPainter`'s dead `isShiftDown()`, `EnergyCellItem`'s unmigrated legacy tooltip, and `TooltipFrameRenderer`'s 2-of-21 textured-frame coverage. This pass adds one new, narrow limitation:

- **Finding 3's four required tests remain source-regression sentinels, not executed proof** — identical in kind to every other `Font`-dependent limitation already recorded in this codebase; not a new category of gap, just one more instance of the pre-existing, documented constraint.

## 14. Final repository-wide status

`git status --short` after this pass: the same pre-existing dirty tree from prior reports' §2/§20 (`build.gradle`, 18 generated JSON files, 23+ pre-existing review-bundle zips, `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`), unchanged — plus every file from the foundation and review-correction passes, further modified where §8 indicates, plus this pass's own 1 new main file, 10 modified main files, 1 new test file, 2 extended test files, 1 modified doc, and 2 new report/bundle files. No file outside this pass's declared scope was created, modified, staged, or touched.

## 15. Confirmation that unrelated dirty-tree files were preserved

Confirmed. `build.gradle`, all 18 generated datagen JSON files, all pre-existing untracked review-bundle zips, the `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, and `src/main/generated/.cache/` are exactly as they were at the start of this pass — not read for modification, not staged, not deleted, not reformatted. `runDatagen`'s own cache report ("removed stale: 0, written: 0") and the byte-for-byte-identical before/after `git status --porcelain` diff (§10) independently confirm no generated or unrelated file was altered.

## 16. Confirmation that Phase 3C remains unstarted

Confirmed. No file under `api/rpg/resources/**` (or any other Resource API Phase 3C scope) was read for modification or touched by this pass.

## 17. Confirmation that nothing was committed or pushed

Confirmed. No `git commit`, `git add`, or `git push` command was run at any point in this pass. `git status --short` throughout shows only working-tree modifications/additions, never an index change.

## 18. Micro-correction (post-bundle-review fix)

A static review of the assembled final-correction bundle found that `AttunementContributor` — despite importing `TooltipSectionGroup` and despite §5/§8's own text claiming it declared `ATTUNEMENT` — never actually wrote the `sectionGroup()` `@Override`. It therefore silently inherited `TooltipContributor`'s default `PRIMARY`. This was caught before any commit, so no gameplay build was ever affected, but it means every claim in this report about Netherite Shuriken's and Ring of Protection's attunement content grouping into its own `ATTUNEMENT` block (§5's two worked examples) was **not yet true in the actual production source** at the time this report was first written — the pure `TotalityTooltipRendererGroupingTest` tests proved the *grouping algorithm* correct in isolation (they construct `ContributorBlock`s by hand), but nothing had proven `AttunementContributor` itself actually fed that algorithm the right tag.

**Fix:**
```java
@Override
public TooltipSectionGroup sectionGroup() {
    return TooltipSectionGroup.ATTUNEMENT;
}
```
Added to `AttunementContributor`, immediately before its existing `contribute(TooltipContext)` override. No other line in the class changed.

**Also corrected — stale ordering claim in `TooltipContributorRegistry`'s Javadoc:** it still read "the renderer is still responsible for the final on-screen ordering of section *kinds*", describing the old, now-deleted `kindRank` global sort rather than the current per-contributor `TooltipSectionGroup` block ordering. Rewritten to state the actual final-correction-pass architecture: contributor blocks are ordered by declared group, individual sections are never reordered relative to their own contributor's other sections, and this list's order only matters as the group tie-breaker and for same-stack multi-contributor cases (e.g. `LegacyExtensionAdapterContributor`'s fallback positioning).

**Test coverage added, proving the real contributor, not only a hand-built block:**
- `TooltipContributorRegistryTest#attunementContributorSectionGroupIsAttunement` — calls `new AttunementContributor().sectionGroup()` directly and asserts `ATTUNEMENT`. This is the direct proof the review specifically asked for: it exercises the actual production class, not a manually-constructed `ContributorBlock` standing in for it.
- Seven further `TooltipContributorRegistryTest` tests calling `sectionGroup()` directly on every other contributor with a non-default tag (`FuelContributor` → `RESOURCES`, `WeightContributor` → `WEIGHT`, `LegacyExtensionAdapterContributor`/`ExternalContentContributor` → `EXTERNAL`, `TechnicalInfoContributor` → `TECHNICAL`, `MetadataContributor` → `LORE`) plus one test confirming the four contributors that deliberately rely on the default (`WeaponContributor`, `EnergyContributor`, `GrimoireContributor`, `HealingPotionContributor`) still report `PRIMARY` — added so this exact class of bug (import present, report claims an override exists, `@Override` silently missing) cannot recur undetected for any other contributor either.
- `TooltipApiFoundationSourceRegressionTest#attunementContributorActuallyOverridesSectionGroupToReturnAttunement` — a source-regression sentinel confirming the `@Override` annotation, the method declaration, and the `return TooltipSectionGroup.ATTUNEMENT;` body all exist in the actual file text, as a second, independent line of evidence alongside the pure test above.

**Verification that production code — not only tests — now uses `TooltipSectionGroup.ATTUNEMENT`:** confirmed by direct grep; the only non-test match is `src/main/java/zcylas/totality/client/tooltip/contributor/AttunementContributor.java:39`, inside the new `sectionGroup()` override.

**Files touched by this micro-correction:**
```
src/main/java/zcylas/totality/client/tooltip/contributor/AttunementContributor.java       (added sectionGroup() override)
src/main/java/zcylas/totality/client/tooltip/contributor/TooltipContributorRegistry.java  (stale Javadoc corrected)
src/test/java/zcylas/totality/client/tooltip/contributor/TooltipContributorRegistryTest.java     (+8 tests)
src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java       (+1 test, 35 -> 36)
```

**Updated test totals:**
- Focused (`--tests "zcylas.totality.client.tooltip.*"`): **120 tests, 0 failures, 0 errors, 0 skipped** (11 XML files) — up from §9's 111, exactly the +9 tests added by this micro-correction.
- Full suite (`./gradlew test`): **1123 tests, 0 failures, 0 errors, 0 skipped** (91 XML files) — up from §9's 1114, exactly +9.
- `./gradlew runDatagen`: **"total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"** — zero generated files changed; `git status --porcelain` immediately before and after were identical.
- `./gradlew clean build`: **BUILD SUCCESSFUL**, full-suite totals after clean build matched the 1123/0/0/0 above exactly.
- `git diff --check`: exit 0 — only informational LF/CRLF notices, no whitespace-conflict errors.

**Bundle:** `Context/Audit/Review Bundles/TOTALITY_TOOLTIP_API_FOUNDATION_FINAL_CORRECTION_BUNDLE.zip` was rebuilt in place (same filename/path, same overall manifest categories) to carry the two corrected main files and two extended test files. See the rebuilt archive's own `MANIFEST.md` for the exact current file-by-file breakdown.

**Not committed, not pushed, Phase 3C not started** — same confirmations as §15–§17, re-verified after this micro-correction.

---

*Totality Tooltip API foundation final correction pass (incl. micro-correction) · `feature/general-resource-api` · HEAD `6998d322` throughout, no commit created.*
