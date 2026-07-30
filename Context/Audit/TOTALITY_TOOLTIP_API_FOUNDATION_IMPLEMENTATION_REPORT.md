# Totality Tooltip API — Foundation Implementation Report

Implements the first architectural foundation of Totality's improved Tooltip API, following the read-only audit at `Context/Audit/TOTALITY_TOOLTIP_API_AUDIT.md`. This report documents what was built, why, exactly what changed, and what remains deliberately deferred.

> **Review-correction addendum.** A subsequent narrow review-correction pass fixed eight findings against this foundation (classification/theme coupling that contradicted §10 below, incomplete wrapping, an inferred-rather-than-declared disclosure-capability model, contributors not yet applying `TooltipKnowledgeView`/`TooltipVisibility`, a documentation self-contradiction in §15, non-deterministic decimal formatting, an unsafe viewport-height formula, and two contributors independently fetching `Minecraft.getInstance()` instead of using `TooltipContext`). Full details, exact corrections, and updated policy statements are in `Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_REVIEW_CORRECTION_REPORT.md`. Two specific corrections are called out inline below (marked **Correction**); everything else in this document describes the foundation pass as originally written and should be read alongside the correction report for the current, corrected state.
>
> **Final-correction addendum.** A third, narrower correction pass fixed three further findings: `TooltipKnowledgeView.of(stack)` incorrectly defaulted a *missing* identification component to unidentified (hiding content on every existing Netherite Shuriken/Ring of Protection stack) rather than to identified; the renderer's global `kindRank`-based section sort (described in §8 below) destroyed contributor grouping and has been replaced with a `TooltipSectionGroup`-based per-contributor block ordering; and `StatRow`/`StatBlock` label wrapping plus footer hint-line splitting were completed (previously only values wrapped, and an individual over-wide footer hint was never split). Full details are in `Context/Audit/TOTALITY_TOOLTIP_API_FOUNDATION_FINAL_CORRECTION_REPORT.md`. §16 and §8 below describe the pre-final-correction state and should be read alongside that report for the current, corrected state.
>
> **Micro-correction addendum.** A static bundle review then found that `AttunementContributor` — despite importing `TooltipSectionGroup` and despite the final-correction report's own worked examples describing it — never actually wrote the `sectionGroup()` override, so it silently used the default `PRIMARY` instead of `ATTUNEMENT`. Fixed, along with a stale `TooltipContributorRegistry` Javadoc paragraph still describing the deleted `kindRank` section-kind ordering. Full details in `TOTALITY_TOOLTIP_API_FOUNDATION_FINAL_CORRECTION_REPORT.md` §18.
>
> **Foundation-closure addendum (final).** A subsequent visual-correction pass (and its own micro-correction, presentation-cleanup, and scrolling event-consumption follow-ups) corrected the renderer's compact-width policy, badge flow, icon-to-label spacing, header/body and body/footer breathing room, one contributor's advanced-tooltip line ownership, and the scroll-input wiring's true root cause (a `MouseHandler` deferred-execution mechanism, decompiled and documented). Manual validation of representative items — including the Netherite Shuriken and the D&D Potion of Healing — is now complete and confirms the foundation behaves as designed, with one accepted, deferred limitation (Creative-inventory smooth-scroll fallthrough during a fast gesture — not a blocker). §35 below describes the pre-visual-correction state; the authoritative, final closure record is `TOTALITY_TOOLTIP_API_FOUNDATION_VISUAL_CORRECTION_REPORT.md` §28. Generic Player Resource API Phase 3C is the next major task.

---

## 1. Starting branch and HEAD

Branch: `feature/general-resource-api`
HEAD: `6998d3222ddc5274a6fca7e0dbd83c71fc3b7408`
Commit subject: "Add healing potion and combat feedback"

Verified identical to the checkpoint recorded in the audit before any file was touched.

## 2. Starting repository-wide status

`git status --short` at the start of this task showed the same pre-existing dirty tree documented in the audit: `build.gradle` (modified), 18 generated datagen JSON files (loot tables, recipes, noise settings — modified), one comment-only diff in `TooltipColors.java` (already present before this task), plus 23 untracked review-bundle zips, several untracked `Context/Trading Test/` screenshots, and `Context/Audit/TOTALITY_TOOLTIP_API_AUDIT.md` (the audit itself, added in the prior session). Also present but not previously enumerated in the audit: untracked `log4j-dev.xml`, `logs/`, and `src/main/generated/.cache/` — these are local run/build artifacts unrelated to any task and were left untouched throughout.

## 3. Starting Tooltip-task-scoped status

`git diff --stat` scoped to every file later touched by this pass showed exactly one pre-existing change: the same comment-only diff in `TooltipColors.java` documented in the audit (three section-header comments — `// Normal Rarities`, `// Special Cases`, etc. — no logic changed). No other Tooltip-scoped file had any unexplained change since the audit; the checkpoint was clean to build on.

## 4. Audit findings used

Every locked design decision in this pass traces to a specific audit finding:

- **Explicit opt-in, not rarity-gating** — audit §4/§7: the mixin's only gate was `stack.has(ItemComponents.getRarity())`, which is exactly why Copper Battery got the custom panel and Iron/Gold/Diamond/Netherite didn't (a registration omission, not a renderer bug).
- **Section model / contributor registry** — audit §5/§17-20: "no registration list, no ordering, no disclosure levels... new semantic content must fit into one of exactly three slots."
- **Multiple classifications** — audit §9: `ItemType` was a single value per item, "no multi-badge composition."
- **Ancient replacing Artifact** — audit §9: `ARTIFACT` was miscoded as a rarity rung with no `ANCIENT` constant at all.
- **Central disclosure** — audit §10: "four separate, byte-identical, hand-rolled GLFW polls," with an existing unused `TotalityKeyHelper` already supporting Ctrl.
- **Preservation policy** — audit §12: "the vanilla-assembled `text` list... is captured as a parameter and never read or forwarded."
- **Wrapping/viewport/scroll** — audit §11: "no viewport, no scroll... a tall combination... can be positioned at `panelY = 6` and still draw past the bottom of the screen."
- **Weapon contributor architecture** — audit §8: `WeaponDataResolver`/`VanillaWeaponStats` already implement the exact adapter pattern needed, just never consulted by the tooltip layer (kept in mind, not built this pass — vanilla weapon migration is explicitly deferred).
- **AC Bonus label / Ring of Protection drift** — audit §14/§16 and §9: `RingOfProtectionItem`'s hardcoded "+1 bonus to AC and saving throws" text was flagged as disconnected from the real `getAcBonus()==1` value.
- **Battery regression test** — audit §13: "for every registered `BatteryItem`... assert `stack.has(ItemComponents.getRarity()) && stack.has(ItemComponents.getItemType())`."

## 5. Final explicit opt-in design

A new marker data component, `TooltipProfileComponent` (`api/core/rpgutils/rarity/TooltipProfileComponent.java`), registered as `ItemComponents.TOOLTIP_PROFILE`. It is an empty record (`Codec` via `MapCodec.unit(...).codec()`) — the narrowest possible signal: presence means "this item explicitly wants Totality tooltip presentation," absence means vanilla rendering, independent of rarity, classification, or any capability interface.

`ItemComponents.hasTooltipPresentation(ItemStack)` is the single source of truth the mixin calls:

```java
public static boolean hasTooltipPresentation(ItemStack stack) {
    var profileType = getTooltipProfile();
    if (profileType != null && stack.has(profileType)) return true;

    var rarityType = getRarity();
    return rarityType != null && stack.has(rarityType);
}
```

## 6. Why rarity is no longer the renderer gate

Rarity described "how special is this item," not "does this item want Totality's UI." Conflating the two is exactly what caused the Copper/Netherite mismatch: any future item author who forgot (or chose not) to set a rarity would silently fall back to vanilla, and any item that DID want a rarity number shown had no way to opt out of getting the whole custom panel forced on it. The explicit `TooltipProfileComponent` separates "wants Totality presentation" from "has an authored rarity" — an opted-in item can have no rarity at all (see §12), and (in principle, not built this pass) a future item could theoretically have a rarity without opting into presentation.

## 7. Semantic context/document/section architecture

New types, all in `client/tooltip/`:

- **`TooltipTarget`** — `HOVER_TOOLTIP` / `INVENTORY_DETAIL` (only the former is rendered this pass; see §17).
- **`TooltipDisclosureLevel`** — `DEFAULT` / `DETAILS` / `TECHNICAL`, with `atLeast(other)` and a static `resolve()` reading `TotalityKeyHelper`.
- **`TooltipVisibility`** — `ALWAYS` / `WHEN_RECOGNIZED` / `WHEN_IDENTIFIED` / `TECHNICAL`.
- **`TooltipKnowledgeView`** — wraps the item's existing `IdentificationStatus` (see §16).
- **`TooltipContext`** — record of `(stack, target, disclosure, knowledge, originalLines, originalComponent, player, level)`. Deliberately excludes `Font`/`GuiGraphicsExtractor` — contributors decide *what*, never *how* to draw.
- **`TooltipDocument`** + **`TooltipDocument.Builder`** — an ordered, immutable list of sections.
- **`TooltipSection`** (`client/tooltip/section/`) — a sealed interface with 12 record kinds: `Header`, `RarityBadge`, `ClassificationBadges`, `Heading`, `StatRow`, `StatBlock` (+ nested `StatLine`), `ProgressBar`, `PropertyBadges`, `Description`, `Requirement`, `ExternalContent`, `TechnicalInfo`. Every section carries `visibility()`/`minDisclosure()` (both default to `ALWAYS`/`DEFAULT`). None carries a pixel coordinate, scissor region, or width.

The Totality footer (credit line + dynamic disclosure hints) is deliberately **not** a section — it is renderer-owned chrome, always drawn last, so no contributor can suppress, duplicate, or reorder it.

## 8. Contributor registry and ordering

`TooltipContributor` is a one-method interface: `List<TooltipSection> contribute(TooltipContext ctx)`. `TooltipContributorRegistry.ordered()` returns a fixed `List.of(...)` of 11 contributors, built once:

```
MetadataContributor -> WeaponContributor -> EnergyContributor -> FuelContributor
-> AttunementContributor -> WeightContributor -> GrimoireContributor
-> HealingPotionContributor -> LegacyExtensionAdapterContributor
-> ExternalContentContributor -> TechnicalInfoContributor
```

The renderer runs every contributor unconditionally and iterates the resulting document; each contributor decides applicability itself (an `instanceof` check, a component-presence check). Adding a future Food/Armor/Spell/Machine contributor means appending one line to this list — the renderer's draw sequence never needs to change.

Within the renderer, body sections (everything except Header/RarityBadge/ClassificationBadges, which are handled as the panel shell) are stable-sorted by a narrow, content-driven `kindRank(section)` function matching the audit's requested order (primary identity → stats/properties → requirements → lore → external → technical), rather than by raw contributor-registration order. This is the "narrow priority/order value" called for — implemented as a function of section *kind*, not a mutable field every contributor must set, since at most one "primary identity" contributor (Weapon/Energy/Grimoire/Healing) ever applies to a single item in this pass.

The renderer contains **no** direct `instanceof UEItem` or `instanceof TotalityWeaponItem` checks — confirmed by an automated source-regression test (§30).

## 9. Legacy extension compatibility

`LegacyExtensionAdapterContributor` triggers for any item still implementing the old `TooltipExtension` interface directly (e.g. `CreditsItem`, not touched this pass) and translates its `addTooltipLines` output into an `ExternalContent` section. It explicitly skips anything implementing `TotalityItem`, because those items are now fully served by `AttunementContributor` — routing them through the adapter too would duplicate attunement lines. Its class Javadoc labels it "NOT the final contributor API" — transitional compatibility only. `GrimoireItem` and `RingOfProtectionItem` were migrated off `TooltipExtension` entirely in this pass (see §23, §28) since dedicated contributors now fully replace what they used to hand-author.

## 10. Multiple-classification model

New `ClassificationsComponent(List<ItemType> ordered)` (`api/core/rpgutils/rarity/ClassificationsComponent.java`), registered as `ItemComponents.CLASSIFICATIONS`. Order is registration-authored and preserved verbatim (list, not a set). `MetadataContributor` emits one `ClassificationBadges` section carrying the full ordered list; the renderer wraps badges onto additional rows automatically (`wrapClassifications`, greedy width-packing against the panel's inner width) rather than truncating or overlapping. Added `ItemType.ENERGY` as a new constant so batteries could demonstrate a genuine multi-classification example (`[BATTERY, ENERGY]`) per the audit brief's own illustrative list.
>
> **Correction:** the sentence "Classification never participates in theme/border resolution — only `ItemRarity` does" stated the *intended* rule, but the renderer as originally written did not actually enforce it — it took the first classification and passed it into `resolveTheme(rarity, type)`, which derived a `typeStyle` from it. The review-correction pass fixed the renderer to match this stated rule exactly (see the correction report §5, "Final classification/theme boundary"). This section's claim is accurate as of the correction pass, not as originally implemented.

## 11. Existing stack/component compatibility

The old singleton `ItemTypeComponent`/`ItemComponents.ITEM_TYPE` was **not** removed or repurposed — every migrated item still sets it alongside the new `ClassificationsComponent`, so any other code in the mod reading the legacy component directly keeps working unchanged. `ItemComponents.classificationsOf(ItemStack)` is the new read path: it prefers `ClassificationsComponent` and falls back to wrapping the legacy singleton as a one-element list, so an item that only ever had the old component (nothing in this repository, but conceivably an old save) still reports a classification rather than silently losing its badge.

## 12. Artifact-to-Ancient migration

`ItemRarity.ARTIFACT` was removed; `ItemRarity.ANCIENT` was added in its place (immediately after `MYTHICAL`, in the `STANDARD` family). A repository-wide search confirmed **no production item registration anywhere referenced `ItemRarity.ARTIFACT`** — only four presentation-layer switch statements did (`TotalityTooltipRenderer`'s theme/border-style/animation dispatch, `TooltipColors.forRarity`), all updated to `ANCIENT` directly. **No item's rarity needed migrating** because none was ever authored with `ARTIFACT`. Both the persistent `ItemRarity.CODEC` and `RarityComponent.STREAM_CODEC` gained a read-time compatibility alias mapping the literal string `"artifact"` to `ANCIENT`, so a hypothetical stack saved before this migration remains readable rather than throwing on an unknown enum value; `"artifact"` is never produced by `getSerializedName()` going forward.

## 13. Ancient theme palette and behavior

`TooltipTheme.ancient(...)`: border/name/sectionHeader `0xFF3ADBC4` (luminous cyan/teal), borderInner/separator/diamondFrameInner `0xFF4A4A8C`/`0xFF2A2A5C` (muted indigo), bgTop/bgBottom `0xFF12143A`/`0xFF0A0B24` (dark slate/indigo), badgeCutout `0xFFE8F6FF` (pale blue-white), footerDot `0xFF8A6F4D` (restrained aged bronze). Reuses the existing `TooltipBorderStyle.ANCIENT` constant (already present, previously mapped from `ARTIFACT`). `TooltipAnimator.drawAncientText` replaces `drawArtifactText`'s bronze→gold→cyan metallic cycle with an indigo→cyan/teal→pale-blue-white "rune activation" cycle, keeping the dormant/unactivated-letter color as dim aged bronze — visually distinct from Legendary's gold/orange and not a generic diamond-blue (the indigo+bronze mixing avoids a single-note cyan look).

## 14. Preserved Industrial and Religious families

New `RarityFamily` enum (`STANDARD`/`SPECIAL`/`RELIGIOUS`/`INDUSTRIAL`), with every existing `ItemRarity` constant now carrying its family via constructor argument. Industrial (`CRUDE`/`CALIBRATED`/`REINFORCED`/`PROTOTYPE`/`OVERCHARGED`/`MASTERWORK`) and Religious (`BLESSED`/`SACRED`/`CELESTIAL`/`DIVINE`/`GODFORGED`) constants, names, and colors are byte-for-byte unchanged — only the enum declaration syntax changed (each constant now takes a `RarityFamily` argument), verified by a dedicated test asserting every constant's `family()` matches its documented ladder.

## 15. Disclosure implementation

`TooltipDisclosureLevel.resolve()` is called exactly once per `render()` call and reads `TotalityKeyHelper.isCtrlPressed()`/`isShiftPressed()` (Ctrl wins if both held) — the pre-existing, previously-unused centralized helper. Three of the four old duplicated raw-GLFW polls are gone: `TooltipStatBlock`/`TooltipWeaponBlock` were deleted outright (superseded), and `BatteryItem.appendHoverText`/`isShiftDown` were removed (superseded by `EnergyContributor`). **Correction (see the review-correction report):** the fourth, `TooltipPainter`'s dead private `isShiftDown()`, was *not* removed by this pass — the original wording here claiming it was removed was incorrect; `TooltipPainter.java` was never touched by this pass at all, so that dead method still exists unchanged. It remains tracked as a known limitation (§36). `EnergyContributor` reads `ctx.disclosure().atLeast(DETAILS)` to pick compact vs. exact energy/I-O text in one place, replacing the old per-block Shift poll.

## 16. Knowledge-visibility contract

`TooltipKnowledgeView` wraps the item's **already-existing** `IdentificationStatus` (`api/item/IdentificationStatus`, three levels: `UNIDENTIFIED`/`PARTIALLY`/`IDENTIFIED`) rather than inventing new state. `TooltipKnowledgeView.of(ItemStack)` reads `TotalityItem.getIdentificationStatus(stack)` when the item implements that interface, defaulting to `IDENTIFIED` otherwise — satisfying "production item contexts may default to identified until the future authoritative system exists." `isVisible(TooltipVisibility, TooltipDisclosureLevel)` implements the four-level contract exactly as specified (`WHEN_RECOGNIZED` ⇒ at least `PARTIALLY`, `WHEN_IDENTIFIED` ⇒ `IDENTIFIED`, `TECHNICAL` ⇒ gated by disclosure, not identification). No persistent state, shop/quest recognition, or Codex memory was added — this pass only makes the tooltip layer *consume* state that already existed and was already being persisted, unused, by `TotalityItem`.

## 17. Vanilla and third-party preservation policy

The mixin now captures `text`/`data` and passes them into `TooltipContext.hover(...)` as `originalLines`/`originalComponent`. `ExternalContentContributor` surfaces every original line except an exact string match against the item's own display name (already drawn by the Header section) — no filtering by color, indentation, or translation key. This is a real, working change from the audit's finding that the old renderer silently discarded this list entirely; it is exercised today whenever an opted-in item's own vanilla `appendHoverText` still contributes a line (e.g. `ShurikenItem`'s translated flavor line, unrelated to Totality's own lore field), which now survives instead of vanishing.

## 18. Structured `TooltipComponent` policy

Per the audit's preferred behavior ("preservation is more important than forcing the Totality frame"), this pass takes the safe branch of the two-option policy: a stack whose vanilla tooltip carries a **non-empty** structured `TooltipComponent` (bundle contents, map/book previews, banner patterns, other mods' custom components) is **not** routed into the Totality panel at all — the mixin's gate is `data.isEmpty() && TotalityTooltipRenderer.isEligible(stack)`, falling back to vanilla's own renderer for that stack. Embedding structured `TooltipComponent` content *inside* the Totality panel (the audit's first-preference option) was not attempted this pass — no Totality item currently produces one, so there was nothing concrete to validate against, and building a generic adapter for arbitrary third-party components speculatively was judged out of scope for a foundation pass. This is a known limitation, not a silent gap (see §36).

## 19. Width and wrapping policy

Named constants `MIN_WIDTH = 160`, `MAX_WIDTH = 260`, `SCREEN_MARGIN = 6` replace the old inline literals. Panel width is derived from the icon area plus title width, clamped to `[MIN_WIDTH, MAX_WIDTH]` — **not** grown further by long-form body content. All wrapped, multi-line sections (`Description`, `Requirement`, `ExternalContent`, `TechnicalInfo`) wrap against this already-final width via `Font.split(Component, width)` — Minecraft's own native component-splitting facility, not string-length math — fixing the audit's noted bug where lore was wrapped against a pre-final width. Classification badges and weapon property badges wrap onto additional rows via width-packing helpers (`wrapClassifications`, `wrapLabels`) that measure real glyph widths per label, so a single unusually long badge label only pushes its own row, never crashes layout.

## 20. Viewport and scrolling implementation

Header (icon/title/badges), separator, and footer are always drawn in full and never scroll. The body section list is measured (`bodyContentH`) and compared against `bodyViewportH = min(bodyContentH, maxViewportH - chromeH)`, where `maxViewportH` is derived from the current scaled screen height minus `SCREEN_MARGIN` on each side. When content fits, `bodyViewportH == bodyContentH` and nothing is clipped. When it overflows, the body is drawn inside a `CloseableScissor` (the mod's existing scissor-clipping utility) shifted up by the current scroll offset. `TooltipScrollController` (new, `client/tooltip/TooltipScrollController.java`) owns the scroll-offset state entirely outside any contributor.

## 21. Scroll-state reset policy

- **Item change**: `TooltipScrollController.onRender` resets `scrollOffset` to 0 whenever the hovered stack differs from the last rendered one (`ItemStack.isSameItemSameComponents`).
- **Disclosure change**: the offset is re-clamped against the newly recomputed `maxScroll` every frame regardless of cause, so a disclosure change that shrinks or grows content never leaves an out-of-range offset.
- **Screen close**: `TooltipScrollController.registerLifecycleHooks()` (called once from `TotalityClient.onInitializeClient()`) registers a Fabric `ScreenEvents.AFTER_INIT` → `ScreenEvents.remove(screen)` listener that calls `reset()`, clearing `currentStack`/`scrollOffset`/`maxScroll`/`visibleThisFrame`.
- **No wheel-input theft when not overflowing**: a new `ScreenMixin` (see §29) injects into `GuiEventListener.mouseScrolled` and only cancels the event when `TooltipScrollController.onMouseScroll(scrollY)` returns true, which itself only returns true when `visibleThisFrame && maxScroll > 0`. `visibleThisFrame` is reset to `false` at the head of every `extractTooltip` call (a new `@Inject` in `AbstractContainerScreenMixin`) and only set back to `true` inside `onRender`, so a tooltip that isn't currently showing at all — not merely "not overflowing" — never consumes a scroll event.

## 22. Battery migration

All five tiers (`init/items/EnergyItems.java`) now explicitly set `.component(ItemComponents.getTooltipProfile(), TooltipProfileComponent.STANDARD)` and `.component(ItemComponents.getClassifications(), ClassificationsComponent.of(ItemType.BATTERY, ItemType.ENERGY))`. **No rarity, weight, or lore was invented** for Iron/Gold/Diamond/Netherite — none exists in the repository for them, so they render through the neutral opted-in-without-rarity theme (no rarity badge; `themeRarity` falls back to `ItemRarity.COMMON`'s flat/no-animation presentation purely for border/theme lookup, without ever claiming the item *has* Common rarity — no `RarityBadge` section is emitted for them). `EnergyContributor` (capability-driven off `UEItem`) supplies current/capacity/percentage/I-O/active-state identically for all five, since they all implement the same interface. `BatteryItem`'s legacy `appendHoverText`/`buildEnergyBar`/`isShiftDown` (dead code the instant all five tiers opt in) were deleted rather than left behind. A direct regression test (`allFiveBatteryTiersExplicitlyOptIntoTooltipPresentation`, §30) locks in that all five carry the opt-in and classification going forward — this is the Copper/Netherite mismatch's regression test.

## 23. Grimoire migration

`GrimoireItem` no longer `implements TooltipExtension` — its tier/spell-state tooltip content moved to `GrimoireContributor`, which reads the exact same narrow `GrimoireCaster.spellName()` accessor the old code did (never touching `ArcaneFormula`/rune internals). All three tiers (Novice/Apprentice/Archmage) share this one contributor, differentiated only by the pre-existing `int maxTier` constructor argument — no per-tier duplication was introduced. Existing rarity (RARE/EPIC/LEGENDARY), Magical classification, and lore per tier are unchanged; all three now also carry the new `ClassificationsComponent.of(MAGICAL)` and explicit `TooltipProfileComponent`.

## 24. Weapon migration

`WeaponContributor` replaces the deleted `TooltipWeaponBlock`, preserving the same information (dice expression via `Dice.getLabel()`, damage type, STR/DEX-or-Finesse, Finesse/Thrown/Light/Heavy/Reach/Versatile properties, category, range, stamina cost) through the new generic section primitives (`Heading`, `StatRow`, `PropertyBadges`). The old widget's boxed "dice die" graphic was simplified to a plain stat row — a deliberate, documented trade-off of moving off bespoke per-block pixel drawing, not a silent regression (the same numbers are shown, just in a row instead of a box). **Netherite Shuriken** is the representative migrated item: it now explicitly carries `TooltipProfileComponent` and `ClassificationsComponent.of(WEAPON)`. The other four Shurikens and both Skyrim swords deliberately keep relying on the documented temporary rarity-based compatibility fallback in `ItemComponents.hasTooltipPresentation` rather than being touched this pass — pinned down by an explicit test (§30) so the scope decision is checked, not implicit. `WeaponContributor` still discovers applicability via `instanceof TotalityWeaponItem` only; a future contributor consuming `WeaponDataResolver`/`VanillaWeaponStats` for vanilla weapons is explicitly deferred (see §38).

## 25. Attunement migration

`AttunementContributor` is the one shared place attunement now renders. It supersedes both `TotalityItem`'s old `TooltipExtension` default (attunement status line) and `RingOfProtectionItem`'s full hand-written override. It also derives an "AC/Save Bonus" requirement line directly from `TotalityArmorItem.getAcBonus()`/`getSaveBonus()` when either is non-zero — replacing Ring of Protection's literal `"+1 bonus to AC and saving throws"` string, which could previously drift from the real bonus value sitting two lines above it in the same file. **Attunement mechanics were not changed** — only the two existing getters (`getAcBonus`, `getSaveBonus`) and the existing `requiresAttunement()`/`isAttuned()`/`getAttunedTo()` contract are read.

## 26. Weight migration

`WeightContributor` renders the existing `WeightComponent` generically, identically to how the old `TooltipStatBlock` did it — but as its own contributor, not folded into energy or weapon rendering. No other contributor duplicates weight logic.

## 27. D&D Potion of Healing migration

`init/items/DndPotionItems.java`'s single registration now carries an explicit `TooltipProfileComponent`, the existing `ItemType.POTION` classification (both legacy singleton and new `ClassificationsComponent`), and **no invented rarity or lore** (none exists for this item). `HealingPotionContributor` reads the item's own registered `HealingAmount`/`useDurationTicks` via two new public getters added to `HealingPotionItem` (`getHealingAmount()`, `getUseDurationTicks()`) — the exact same fields `finishUsingItem` rolls against, so the tooltip cannot drift from what the item actually does. Default view shows the dice formula ("2d4 + 2 HP") and use time in seconds; Details view adds min/max roll and exact tick duration, with HP figures converted through `RpgDisplayUtils.toDisplayHp` (the mod's one authoritative ×5 Health conversion) rather than a new hardcoded multiplier. **Potion mechanics, notification behavior, acquisition, and Alchemy were not touched.**

## 28. Existing custom-item eligibility migration

A repository-wide search found **19 files** currently authoring `ItemComponents.RARITY` (`IngredientItems`, `EnergyItems`, `CurrencyItems`, `BleachItems`, `MagicItems`, `BasicWeaponItems`, `SkyrimSwordItem`, `SKIngredientItems`, `RuneItems`, `NaturalBlocks`, `WhitestoneBlocks`, `RitualItems`, `RitualBlocks`, `FuelItems`, `ReligiousItems`, plus the mixin/renderer/registry files themselves). Rather than hand-editing every registration site (mechanical, high-risk of a missed item, and explicitly discouraged — "do not invent new rarity/classification/lore data while performing this eligibility migration"), this pass uses the brief's explicitly-permitted alternative: `ItemComponents.hasTooltipPresentation` treats a bare `RARITY` component as a **documented, temporary** compatibility signal, so every one of those 19 files' existing items keeps rendering through the custom panel exactly as before, with zero risk of an accidentally-dropped item. Six registrations (5 batteries + Netherite Shuriken, plus the 3 Grimoires and Ring of Protection already carrying rarity) were additionally given the explicit `TooltipProfileComponent` as the representative "real" migration. The remaining rarity-bearing items across those 19 files are unchanged and continue on the fallback — a known, tracked follow-up (see §36), not a silent gap.

## 29. Exact files changed

**New (25 main + 8 test = 33 files):**

```
src/main/java/zcylas/totality/api/core/rpgutils/rarity/ClassificationsComponent.java
src/main/java/zcylas/totality/api/core/rpgutils/rarity/RarityFamily.java
src/main/java/zcylas/totality/api/core/rpgutils/rarity/TooltipProfileComponent.java
src/main/java/zcylas/totality/client/tooltip/TooltipContext.java
src/main/java/zcylas/totality/client/tooltip/TooltipDisclosureLevel.java
src/main/java/zcylas/totality/client/tooltip/TooltipDocument.java
src/main/java/zcylas/totality/client/tooltip/TooltipKnowledgeView.java
src/main/java/zcylas/totality/client/tooltip/TooltipScrollController.java
src/main/java/zcylas/totality/client/tooltip/TooltipTarget.java
src/main/java/zcylas/totality/client/tooltip/TooltipVisibility.java
src/main/java/zcylas/totality/client/tooltip/contributor/AttunementContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/EnergyContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/ExternalContentContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/FuelContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/GrimoireContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/HealingPotionContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/LegacyExtensionAdapterContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/MetadataContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/TechnicalInfoContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/TooltipContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/TooltipContributorRegistry.java
src/main/java/zcylas/totality/client/tooltip/contributor/WeaponContributor.java
src/main/java/zcylas/totality/client/tooltip/contributor/WeightContributor.java
src/main/java/zcylas/totality/client/tooltip/section/TooltipSection.java
src/main/java/zcylas/totality/mixin/client/ScreenMixin.java

src/test/java/zcylas/totality/api/core/rpgutils/rarity/ItemRarityAncientMigrationTest.java
src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java
src/test/java/zcylas/totality/client/tooltip/TooltipDisclosureLevelTest.java
src/test/java/zcylas/totality/client/tooltip/TooltipKnowledgeViewTest.java
src/test/java/zcylas/totality/client/tooltip/contributor/HealingPotionDisplayHpMathTest.java
src/test/java/zcylas/totality/client/tooltip/contributor/TooltipContributorRegistryTest.java
src/test/java/zcylas/totality/client/tooltip/section/TooltipSectionDocumentTest.java
src/test/java/zcylas/totality/item/potion/dnd/HealingPotionItemTooltipGettersTest.java
```

**Deleted (2 files, superseded, no remaining call sites):**

```
src/main/java/zcylas/totality/client/tooltip/renderer/TooltipStatBlock.java
src/main/java/zcylas/totality/client/tooltip/renderer/TooltipWeaponBlock.java
```

**Modified (20 files):**

```
src/main/java/zcylas/totality/TotalityClient.java
src/main/java/zcylas/totality/api/core/rpgutils/rarity/ItemComponents.java
src/main/java/zcylas/totality/api/core/rpgutils/rarity/ItemRarity.java
src/main/java/zcylas/totality/api/core/rpgutils/rarity/ItemType.java
src/main/java/zcylas/totality/api/core/rpgutils/rarity/RarityComponent.java
src/main/java/zcylas/totality/client/tooltip/TotalityTooltipRenderer.java
src/main/java/zcylas/totality/client/tooltip/renderer/TooltipAnimator.java
src/main/java/zcylas/totality/client/tooltip/theme/TooltipColors.java
src/main/java/zcylas/totality/client/tooltip/theme/TooltipTheme.java
src/main/java/zcylas/totality/init/items/BasicWeaponItems.java
src/main/java/zcylas/totality/init/items/DndPotionItems.java
src/main/java/zcylas/totality/init/items/EnergyItems.java
src/main/java/zcylas/totality/init/items/MagicItems.java
src/main/java/zcylas/totality/item/energy/BatteryItem.java
src/main/java/zcylas/totality/item/equipment/RingOfProtectionItem.java
src/main/java/zcylas/totality/item/magic/GrimoireItem.java
src/main/java/zcylas/totality/item/potion/dnd/HealingPotionItem.java
src/main/java/zcylas/totality/item/weapon/SkyrimSwordItem.java
src/main/java/zcylas/totality/mixin/client/AbstractContainerScreenMixin.java
src/main/resources/totality.mixins.json
```

`git diff --stat` for exactly this set: **22 files changed, 684 insertions(+), 693 deletions(-)** (deletions dominated by the two removed renderer-block files).

## 30. Test categories and evidentiary limits

- **Pure semantic model tests** (no Minecraft bootstrap): `TooltipDisclosureLevelTest`, `TooltipKnowledgeViewTest`, `TooltipSectionDocumentTest`, `TooltipContributorRegistryTest`, `ItemRarityAncientMigrationTest`. These construct real objects and assert real behavior.
- **Reflection tests**: `HealingPotionItemTooltipGettersTest` — confirms the two new getters exist with correct signatures, mirroring the pre-existing `HealingPotionItemContractTest` convention.
- **Source-regression sentinels**: `TooltipApiFoundationSourceRegressionTest` — 11 tests reading source files as text and asserting tokens are present/absent (battery-parity fix, renderer architecture constraint, superseded-class cleanup, mixin gate wording, compatibility-fallback documentation). These prove the source text says what it should; they do not execute any code or prove runtime behavior.
- **Not built, and why**: live behavioral tests of any contributor's `contribute(TooltipContext)` output, and pure layout tests of the renderer's private `layout()`/wrapping methods. Both would require constructing a real `ItemStack`/`Item` (for contributors) or a real `Font` (for layout), and this repository has an established, explicitly documented finding (`HealingPotionItemContractTest`'s class Javadoc, confirmed by direct probing in a prior task) that **no test in this codebase constructs a Minecraft `Item`/`ItemStack` under plain JUnit** — the registry throws once frozen, and throws a different "not bootstrapped" error before that. This pass did not introduce a Minecraft-bootstrap test harness (out of scope, and no other test in the repo does either), so contributor/layout runtime correctness rests on source-regression sentinels plus manual visual validation (§35), consistent with the repository's existing convention rather than a new gap this task introduced.
- **Manual visual validation**: recorded in §35 — genuinely performed, not claimed beyond what was actually run.

## 31. Focused test commands/results

```
./gradlew test --tests "zcylas.totality.client.tooltip.*" \
                --tests "zcylas.totality.item.potion.dnd.HealingPotionItemTooltipGettersTest" \
                --tests "zcylas.totality.api.core.rpgutils.rarity.ItemRarityAncientMigrationTest"
```

First run: **51 tests, 2 failures** (both self-inflicted — my own new Javadoc prose in `TotalityTooltipRenderer`/`BatteryItem` contained the literal strings the sentinel tests checked for absence of; fixed by rewording the comments, not the tests). Second run after the wording fix: **51 tests, 0 failures, 0 errors, 0 skipped.**

## 32. Full test totals

`./gradlew test` (whole suite, no filter): **1041 tests, 0 failures, 0 errors, 0 skipped**, aggregated from all 86 `TEST-*.xml` files under `build/test-results/test/`. Re-confirmed after the mixin-target fix (§33) and again as part of the final `clean build` (§34) — identical totals both times.

## 33. Datagen result

`./gradlew runDatagen` first failed at client bootstrap: the new `ScreenMixin` (originally `@Mixin(Screen.class)`, targeting `mouseScrolled(DDDD)Z`) threw `InvalidInjectionException: could not find any targets` — `Screen` does not declare `mouseScrolled` in its own bytecode; it is an inherited default method of `GuiEventListener`. Retargeting `@Mixin(GuiEventListener.class)` as a plain class then failed differently (`@Mixin target type mismatch: ... is an interface` — this Mixin version requires interface targets to be mixed into by an interface). The mixin was corrected to `public interface ScreenMixin` with a `private` (not `default`) injector method, which resolved both errors. After the fix, `runDatagen` completed successfully: client fully bootstrapped, all mod self-tests passed (`NotificationTimingVerification`, `PowerAttackFlashVerification`, `ProvisionerRendererVerification`, `KeybindVerification`), and the datagen cache report read **"total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0"** — zero generated files changed. No unrelated churn was produced or accepted.

## 34. Build result

`./gradlew clean build` — **BUILD SUCCESSFUL**, 8/8 tasks executed (`compileJava`, `processResources`, `classes`, `processIncludeJars`, `jar`, `sourcesJar`, `assemble`, `compileTestJava`, `testClasses`, `test`, `validateAccessWidener`, `check`, `build`). `git diff --check` afterward exited 0 — only informational LF/CRLF line-ending notices on both pre-existing and newly-touched files, no actual whitespace-conflict errors.

## 35. Manual visual validation performed and remaining

**Historical note:** this section describes the state at the end of the original implementation pass only. Manual validation of the completed foundation (including the items and interactions listed below) has since been performed across the visual-correction pass and its follow-ups — see `TOTALITY_TOOLTIP_API_FOUNDATION_VISUAL_CORRECTION_REPORT.md` §28 for the authoritative, final closure record. The rest of this section is preserved as-written for historical accuracy.

**Performed:** none — this implementation pass did not launch an interactive client session; all validation was through `test`/`runDatagen`/`clean build`, which is genuine automated proof of compilation, unit-level correctness, and datagen non-regression, but is **not** proof of on-screen rendering, wrapping, scrolling feel, or the Ancient theme's visual identity.

**Remaining (explicitly not claimed as done):**
- Visual comparison of all five batteries, all three Grimoires, Netherite Shuriken, Ring of Protection, and the D&D Potion of Healing against the audit's reference screenshots.
- Confirming multiple-classification badges actually wrap onto a second row in-game for the Battery `[BATTERY, ENERGY]` case.
- Confirming Shift/Ctrl disclosure actually swaps content live, and that footer hints appear/disappear correctly.
- Confirming the scroll viewport, scissor clipping, and scroll-reset-on-item-change/screen-close behave correctly with a real mouse wheel.
- Confirming an item migrated to `ANCIENT` renders visually distinct from Legendary, with the intended cyan/teal/indigo/bronze identity, in an actual game window.
- Confirming a normal, unopted vanilla item (e.g. a plain dirt block) still renders the ordinary vanilla tooltip untouched.

These require an interactive client session this task did not run; they are the concrete next step before this branch is considered visually verified, not merely compiled and unit-tested.

## 36. Known limitations

- **Legacy rarity fallback is temporary but still live** for the 19 files' worth of pre-existing rarity-bearing items not explicitly migrated this pass (§28) — a real, documented, bounded scope decision, not an oversight, but it means `ItemComponents.hasTooltipPresentation` still has two code paths rather than one.
- **Structured `TooltipComponent` content is never embedded**, only avoided (falls back to vanilla) — the audit's first-preference option (embed safely within the panel) was not attempted (§18).
- **No pure layout/wrapping tests exist** — `Font`-dependent code cannot be unit-tested under plain JUnit in this repository (§30); layout correctness rests on sentinels + the manual validation still outstanding (§35).
- **`TooltipPainter`'s dead private `isShiftDown()`** (noted in the original audit as unused dead code) was not removed — `TooltipPainter.java` was not otherwise touched by this pass and removing an unrelated dead private method from an untouched file was judged outside this task's footprint; it remains a small, harmless, pre-existing piece of debt.
- **`EnergyCellItem`'s own legacy `appendHoverText`/shift-poll** (a second, separate `UEItem` implementor besides `BatteryItem`) was not migrated — only batteries were in scope for the representative energy migration; `EnergyCellItem` still renders via vanilla today since it was never given a `TooltipProfileComponent` or rarity in the first place (unchanged by this pass either way).
- **`TooltipFrameRenderer`'s textured frame overlay** still only covers 2 of 21 rarities (Legendary, Common) — unrelated art-asset debt the audit already flagged as separate from this pass's scope; `Ancient` renders with the flat border only, as instructed ("do not require a new frame texture in this pass").

## 37. Explicit follow-up: Generic Player Resource API Phase 3C

**Not started.** No file under this pass touches Phase 3C scope. Phase 3C remains the next major implementation task after this Tooltip foundation is reviewed and committed, exactly as instructed.

## 38. Deferred vanilla weapon and armor migration

Not attempted this pass, per explicit scope exclusion. `WeaponContributor` still gates on `instanceof TotalityWeaponItem` only; no `WeaponTooltipData`/`ArmorTooltipData` resolver consuming `WeaponDataResolver`/`VanillaWeaponStats`/`VanillaArmorStats` was built. AC was not redesigned, combat calculations were not modified, and no armor contributor exists yet — Ring of Protection's AC/Save Bonus line (§25) reads existing getters for display only, without touching `ArmorClass.calculate()` or any combat math.

## 39. Final task-scoped diff/stat

```
 src/main/java/zcylas/totality/TotalityClient.java                          |   4 +
 .../api/core/rpgutils/rarity/ItemComponents.java                           |  75 +++
 .../api/core/rpgutils/rarity/ItemRarity.java                               |  88 +++-
 .../api/core/rpgutils/rarity/ItemType.java                                 |   1 +
 .../api/core/rpgutils/rarity/RarityComponent.java                          |   9 +-
 .../client/tooltip/TotalityTooltipRenderer.java                            | 526 ++++++++++++++++-----
 .../client/tooltip/renderer/TooltipAnimator.java                           |  48 +-
 .../client/tooltip/renderer/TooltipStatBlock.java                          | 195 --------
 .../client/tooltip/renderer/TooltipWeaponBlock.java                        | 176 -------
 .../client/tooltip/theme/TooltipColors.java                                |   6 +-
 .../client/tooltip/theme/TooltipTheme.java                                 |  17 +-
 .../totality/init/items/BasicWeaponItems.java                              |   2 +
 .../zcylas/totality/init/items/DndPotionItems.java                         |  12 +
 .../zcylas/totality/init/items/EnergyItems.java                            |  18 +
 .../zcylas/totality/init/items/MagicItems.java                             |   8 +
 .../zcylas/totality/item/energy/BatteryItem.java                           |  78 +--
 .../item/equipment/RingOfProtectionItem.java                               |  41 +-
 .../zcylas/totality/item/magic/GrimoireItem.java                           |  39 +-
 .../item/potion/dnd/HealingPotionItem.java                                 |  10 +
 .../totality/item/weapon/SkyrimSwordItem.java                              |   2 +-
 .../mixin/client/AbstractContainerScreenMixin.java                         |  19 +-
 src/main/resources/totality.mixins.json                                    |   3 +-
 22 files changed, 684 insertions(+), 693 deletions(-)
```

(Plus 25 new main files and 8 new test files, and 2 deleted files, all listed exactly in §29 — `git diff --stat` on tracked paths only does not show new/untracked/deleted-then-untracked files.)

## 40. Final repository-wide status

`git status --short` after all work: the same pre-existing dirty tree from §2, unchanged (`build.gradle`, the 18 generated JSON files, the pre-existing `TooltipColors.java` comment diff — now folded into this task's own larger diff to that file — and every pre-existing untracked review bundle, screenshot, and `log4j-dev.xml`/`logs/`/`.cache/` artifact), **plus** this task's 20 modified files, 2 deletions, and 33 new files (25 main, 8 test) as untracked additions. No file outside this task's declared scope was created, modified, staged, or touched.

## 41. Confirmation that unrelated dirty-tree files were preserved

Confirmed. `build.gradle`, all 18 generated datagen JSON files, all 23 pre-existing untracked review-bundle zips, the 5 `Context/Trading Test/` screenshots, `log4j-dev.xml`, `logs/`, and `src/main/generated/.cache/` are exactly as they were at the start of this task — not read for modification, not staged, not deleted, not reformatted. `runDatagen`'s own cache report ("removed stale: 0, written: 0") independently confirms no generated file was altered by this task's datagen run.

## 42. Confirmation that nothing was committed or pushed

Confirmed. No `git commit`, `git add` (beyond none — nothing was staged), or `git push` command was run at any point in this task. `git status --short` throughout shows only working-tree modifications/additions, never an index change.

---

*Totality Tooltip API foundation implementation · `feature/general-resource-api` · starting HEAD `6998d322`, no commit created.*
