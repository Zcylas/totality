package zcylas.totality.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.client.util.CloseableScissor;
import zcylas.totality.api.core.rpgutils.rarity.Classification;
import zcylas.totality.api.core.rpgutils.rarity.ClassificationTypes;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.client.tooltip.group.TooltipGlyphSupport;
import zcylas.totality.client.tooltip.group.TooltipGroup;
import zcylas.totality.client.tooltip.group.TooltipGroupIcon;
import zcylas.totality.client.tooltip.group.TooltipGroupOrdering;
import zcylas.totality.api.core.rpgutils.rarity.TooltipCompanionPreview;
import zcylas.totality.client.renderer.gui.TotalityGuiGraphics;
import zcylas.totality.client.tooltip.contributor.TooltipContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributorRegistry;
import zcylas.totality.client.tooltip.footer.TooltipFooter;
import zcylas.totality.client.tooltip.renderer.*;
import zcylas.totality.client.tooltip.presentation.TooltipIdentityLines;
import zcylas.totality.client.tooltip.presentation.TooltipPresentation;
import zcylas.totality.client.tooltip.preview.TooltipCompanionCard;
import zcylas.totality.client.tooltip.preview.TooltipGroupLayout;
import zcylas.totality.client.tooltip.preview.TooltipHeaderPreview;
import zcylas.totality.client.tooltip.preview.TooltipPreviewLayout;
import zcylas.totality.client.tooltip.section.TooltipSection;
import zcylas.totality.client.tooltip.theme.TooltipBorderStyle;
import zcylas.totality.client.tooltip.theme.TooltipColors;
import zcylas.totality.client.tooltip.theme.TooltipTheme;
import zcylas.totality.util.color.ColorUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates the Totality custom tooltip panel. Responsibilities that used to be split
 * between this class and two hardcoded "block" classes are now: (1) run the ordered
 * {@link TooltipContributorRegistry} to build a {@link TooltipDocument}, (2) own layout,
 * wrapping, disclosure filtering, ordering, and the bounded/scrollable viewport, (3) draw the
 * generic panel shell plus a small per-section-kind dispatch for body content.
 *
 * The renderer contains no direct gameplay capability checks against {@code UEItem},
 * {@code TotalityWeaponItem}, or similar interfaces — those checks live inside the relevant
 * contributor instead. It also contains no classification-driven theme/border decision — only
 * {@link ItemRarity} selects the panel theme; classifications only ever produce the header's
 * category/type text line.
 *
 * <p><b>Visual-correction pass — compact shrink-to-content width (Finding 1):</b> the panel width
 * used to be a single stable preferred value ({@code MAX_WIDTH}, unconditionally clamped down only
 * by screen size), so every item — a plain block or a fully-detailed weapon — got the same wide
 * panel, wasting horizontal space on simple items. The width is now derived from the tallest
 * single-line content actually present ({@link #measureNaturalContentWidth}), clamped into a
 * compact preferred range ({@link #MIN_WIDTH}..{@link #PREFERRED_MAX_WIDTH}) and never allowed to
 * exceed {@link #MAX_SCREEN_WIDTH_FRACTION} of the screen. Long-form content (lore, preserved
 * external lines, technical info) is deliberately excluded from this measurement — it wraps
 * vertically at whatever width the rest of the content decided, never forcing the panel wider.
 */
public class TotalityTooltipRenderer {

    private static final int PADDING = 8;
    /** Finding 1: absolute floor — keeps a simple item (e.g. Whitestone) genuinely narrow. */
    private static final int MIN_WIDTH = 110;
    /** Finding 1: compact preferred ceiling — a detailed weapon/energy tooltip may reach this, never far beyond it. */
    private static final int PREFERRED_MAX_WIDTH = 200;
    /** Finding 1: hard ceiling as a fraction of the current scaled screen width, regardless of content. */
    private static final float MAX_SCREEN_WIDTH_FRACTION = 0.38f;
    private static final int SCREEN_MARGIN = 6;
    /** Tooltip-layout-cleanup pass (2026-09-22): tightened from 3 to 2 — a modest, conservative
     *  reduction, not a crush; most of the new vertical compactness comes from {@link #BODY_TEXT_SCALE}
     *  shrinking row heights themselves, not from this gap. Stays a fixed VISUAL gap regardless of
     *  whether the row above/below it is text-scaled — spacing between rows is a layout-density
     *  concern, deliberately independent of any one row's own font scale. */
    private static final int ROW_GAP = 2;
    /** Absolute floor for the panel's inner content width — guarantees a positive, drawable width even on a tiny screen. */
    private static final int MIN_SAFE_INNER_WIDTH = 60;
    /**
     * Tooltip V2 header: row height of each centred classification line (font line + 1). The lines stay at
     * full, pixel-crisp size (a fractional scale drops glyph rows at GUI scale 1) and are kept quieter than
     * the name and the rarity plaque by the muted {@link #SECONDARY_TEXT_COLOR}.
     */
    private static final int IDENTITY_LINE_H = 10;
    /** Tooltip V2 header: gap above the rarity plaque. */
    private static final int PLAQUE_GAP_ABOVE = 2;
    /** Tooltip V2 header: gap below the rarity plaque. */
    private static final int PLAQUE_GAP_BELOW = 3;
    /** Tooltip V2 header: gap between the preview viewport and the item name (no divider there). */
    private static final int PREVIEW_NAME_GAP = 3;
    /** Tooltip V2 header: gap between the last identity line and the body (the first group heading supplies the divider). */
    private static final int HEADER_BOTTOM_GAP = 2;
    /** Extra space between the last semantic group and the unheaded tail (lore, vanilla lines, technical info). */
    private static final int UNHEADED_TAIL_GAP = 5;
    /**
     * Lead before a body that starts with unheaded content (e.g. lore only): the same space a group heading reserves
     * above its title, so the first content always starts at the same offset under the header now that there is no
     * universal header divider (Tooltip V2 Pass 1 final corrections). Spacing only — not a separator.
     */
    private static final int UNHEADED_BODY_LEAD = TooltipGroupHeadingPainter.GAP_ABOVE;
    /** Finding 6: shared secondary-text color (stat labels) — a touch lighter than the old 0xFF888888 for readability. */
    private static final int SECONDARY_TEXT_COLOR = 0xFF9CA3AF;
    /**
     * Presentation-cleanup pass, Finding 1: fixed horizontal gap between an icon glyph and the
     * label that follows it (Damage, Range, Stamina Cost, and every similar icon-led StatRow/
     * StatBlock line) — was an unnamed {@code + 3}, which read as the icon sitting too close to
     * its label. Compact but clearly visible.
     */
    private static final int ICON_LABEL_GAP = 5;
    /** Real-sprite IconStatRow (§9/§11 of the V2 balance pass): 16px icon + gap, and a row tall enough for it. */
    private static final int REAL_ICON_SIZE = 16;
    private static final int REAL_ICON_BOX_W = REAL_ICON_SIZE + ICON_LABEL_GAP;
    private static final int REAL_ICON_ROW_H = REAL_ICON_SIZE + 2;
    private static final int PROVENANCE_INDENT = 10;
    private static final int PROVENANCE_VALUE_COLOR = 0xFFAAAAAA;
    /**
     * Presentation-cleanup pass, Finding 2: small extra breathing room between the body's last
     * row and the footer's own text — previously exactly zero (the footer's reserved chrome
     * height began immediately where the body viewport ended). Light polish only, not a return to
     * a larger panel: this adds a few pixels, not a new row of content.
     *
     * <p>Tooltip-layout-cleanup pass (2026-09-22): tightened from 4 to 3 as part of the overall
     * vertical-density pass — modest, conservative, alongside the body text scale-down below
     * (which already shrinks most row heights on its own).
     */
    private static final int BODY_FOOTER_GAP = 3;

    /**
     * Tooltip-layout-cleanup pass (2026-09-22): every "stat"-shaped body row (Heading, StatRow,
     * StatBlock, PropertyBadges, Requirement, IconStatRow, ProvenanceGroup) — including its own
     * stat icon — now renders at this fraction of its previous (1.0x) size, ~87.5%, within the
     * requested 85-90% range. This single transform is what also satisfies §10's icon-size ask:
     * an {@link TooltipSection.IconStatRow}'s icon is drawn INSIDE the same scaled block as its
     * label/value (see the body draw loop in {@link #render}), so the Jump Boost effect icon and
     * every real item icon shrink together with the text, proportionally, with no separate
     * icon-specific constant to keep in sync.
     *
     * <p>Deliberately NOT applied to {@link TooltipSection.Description} (lore — keeps its own
     * existing hierarchy, per this pass's own instruction), {@link TooltipSection.ExternalContent}
     * (preserved vanilla/third-party lines, e.g. "When in Main Hand" + attack attributes — kept at
     * their real vanilla size so they read exactly as vanilla itself would show them, and so they
     * visually stay distinct from the now-smaller custom stat rows rather than blending into them),
     * {@link TooltipSection.TechnicalInfo} (diagnostic, Ctrl-only), or {@link TooltipSection.ProgressBar}
     * (not text) — see {@link #isScaledSection}. The title/name and rarity/classification badges
     * are not {@code TooltipSection}s at all (handled separately in {@link #render}) and are
     * therefore automatically unaffected, preserving the title's normal intended prominence.
     */
    private static final float BODY_TEXT_SCALE = 0.875f;

    /**
     * Whether the hovered stack is drawn by Tooltip V2 — see {@link TooltipRouting}: every item is, except a stack
     * whose structured tooltip component or hidden tooltip keeps vanilla's own path. Depends on the stack alone, so
     * SHIFT/CTRL change disclosure but never the renderer (Tooltip V2 Pass 1; previously the gate was
     * content-driven and CTRL's technical section could flip an otherwise-vanilla item into V2).
     */
    public static boolean isEligible(ItemStack stack) {
        return TooltipRouting.of(stack) == TooltipRouting.TOTALITY;
    }

    /**
     * @param screen the currently open screen, if any — forwarded to {@link TooltipScrollController}
     *               so wheel input can be matched back to exactly this render (Finding 7); {@code null}
     *               when the tooltip isn't rendering inside a tracked container screen.
     * @param slot   the hovered {@link Slot}, or {@code null} if not applicable — part of the same
     *               scroll-target identity as {@code screen}. Matched by reference identity, never by
     *               {@link Slot#index} (not guaranteed unique across a menu's slots — visual-correction
     *               pass, micro-correction Finding 2).
     */
    public static void render(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y,
                              List<Component> originalLines, Optional<TooltipComponent> originalComponent,
                              @Nullable Screen screen, @Nullable Slot slot) {

        TooltipDisclosureLevel disclosure = TooltipDisclosureLevel.resolve();
        Minecraft mc = Minecraft.getInstance();
        TooltipContext ctx = TooltipContext.hover(stack, disclosure, originalLines, originalComponent,
                mc.player, mc.level);

        // Each contributor's output is processed as one unit: its sections feed the full
        // (unfiltered) TooltipDocument, AND — filtered to what's actually visible — become one
        // ContributorBlock tagged with that contributor's declared TooltipSectionGroup. Sections
        // within a block are never reordered; only the resulting blocks are ordered relative to
        // each other, by group. This replaces sorting individual sections by Java record kind,
        // which used to scatter a single contributor's own output across the body.
        TooltipDocument.Builder builder = TooltipDocument.builder();
        Component title = stack.getHoverName();
        ItemRarity authoredRarity = null;
        List<Classification> classifications = List.of();
        List<ContributorBlock> blocks = new ArrayList<>();

        for (TooltipContributor contributor : TooltipContributorRegistry.ordered()) {
            List<TooltipSection> contributed = contributor.contribute(ctx);
            builder.addAll(contributed);
            builder.addAvailable(contributor.availableDisclosureLevels(ctx));

            List<TooltipSection> bodySections = new ArrayList<>();
            for (TooltipSection section : contributed) {
                if (!isVisible(section, ctx)) continue;
                switch (section) {
                    case TooltipSection.Header header -> title = header.title();
                    case TooltipSection.RarityBadge badge -> authoredRarity = badge.rarity();
                    case TooltipSection.ClassificationBadges badges -> classifications = badges.entries();
                    default -> bodySections.add(section);
                }
            }
            if (!bodySections.isEmpty()) {
                blocks.add(new ContributorBlock(contributor.sectionGroup(), contributor.bodyGroup(ctx), bodySections));
            }
        }
        TooltipDocument document = builder.build();
        // Semantic body groups (Mining, Combat, ...): contributors naming the same group are merged under
        // one centred heading; groups are ordered by the item's authored order, then its primary
        // classification, then default priority. Unheaded content (lore, vanilla, technical) follows.
        List<BodyPart> bodyParts = groupedBody(blocks, TooltipGroupOrdering.authoredOrder(stack), primaryCategory(classifications));
        List<TooltipSection> body = flattenWithHeadings(bodyParts);
        int unheadedTailStart = headedLength(bodyParts);

        // Neutral fallback theme for explicitly opted-in items without an authored rarity —
        // never invented, just a stable default appearance (COMMON's flat frame/no animation).
        // Rarity is the ONLY input to theme/border resolution — classifications never are.
        ItemRarity themeRarity = authoredRarity != null ? authoredRarity : ItemRarity.COMMON;
        TooltipTheme theme = resolveTheme(themeRarity);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        // Compact body text only where it stays pixel-intact (see TooltipTextScale): unscaled at GUI scale 1.
        float bodyScale = TooltipTextScale.pixelSafe(BODY_TEXT_SCALE, mc.getWindow().getGuiScale());

        // Detached SHIFT / ALT / CTRL panels under the tooltip, offered from the explicit disclosure-capability
        // model (never by scanning which sections the current level happened to emit). ALT has no interaction
        // system yet, so it is never offered. The panels are part of the tooltip's vertical footprint.
        List<TooltipModifierPanels.Panel> modifierPanels = TooltipModifierPanels.visible(
                document.availableLevels().contains(TooltipDisclosureLevel.DETAILS), false,
                document.availableLevels().contains(TooltipDisclosureLevel.TECHNICAL), disclosure);
        int panelsBlockH = TooltipModifierPanels.blockHeight(modifierPanels);
        int maxViewportH = Math.max(0, screenH - SCREEN_MARGIN * 2 - panelsBlockH);

        // Tooltip V2 presentation: HOW this tooltip is presented (preview mode/motion, companion card,
        // vignette, divider) — resolved from the item's authored TooltipProfileComponent, falling back to
        // AUTO inference from its own GUI model and then Totality defaults. Never WHAT the content is.
        // The item's GUI render state is resolved once here and reused for inference and drawing.
        TrackingItemStackRenderState previewState = TooltipHeaderPreview.resolveRenderState(stack);
        TooltipPresentation presentation = TooltipHeaderPreview.resolvePresentation(stack, previewState);
        int rarityColor = TooltipColors.forRarity(themeRarity);

        String titleText = title.getString();
        String rarityText = TooltipIdentityLines.rarityLine(authoredRarity);
        List<String> classificationTexts = TooltipIdentityLines.classificationLines(classifications,
                type -> Component.translatableWithFallback(ClassificationTypes.translationKey(type),
                        ClassificationTypes.fallbackName(type)).getString());

        // Width policy (Finding 1): measure the actual semantic content's natural (unwrapped)
        // width, then clamp into a compact preferred range and the current screen's constraints.
        // Long-form content (lore, preserved external lines, technical info) is deliberately
        // excluded from this measurement — see measureNaturalContentWidth. The V2 preview never
        // decides the width: it spans whatever width the content decided.
        int naturalContentW = measureNaturalContentWidth(font, title, rarityText, classificationTexts, body, bodyScale);
        int contentW = effectiveContentWidth(screenW, naturalContentW);
        int panelW = PADDING + contentW + PADDING;
        int innerW = panelW - PADDING * 2;
        // No scroll indicator is drawn (scrolling stays wheel-only and visually unobtrusive), so the body spans the
        // full inner width — no gutter is reserved.
        int bodyContentW = Math.max(1, innerW);

        // V2 identity header, composed vertically: large preview, then centred Name / rarity plaque /
        // one line per classification pair. No icon column beside the name any more, so every line wraps
        // against the full inner width. The header stays fixed (never scrolls), exactly like V1's header did.
        List<FormattedCharSequence> titleLines = font.split(title, innerW);
        if (titleLines.isEmpty()) titleLines = font.split(Component.literal(" "), innerW);
        int titleLineCount = Math.max(1, titleLines.size());
        int titleLineH = font.lineHeight + 1;
        int plaqueH = rarityText.isEmpty() ? 0 : PLAQUE_GAP_ABOVE + TooltipRarityPlaquePainter.HEIGHT + PLAQUE_GAP_BELOW;
        List<FormattedCharSequence> categoryLines = new ArrayList<>();
        for (String text : classificationTexts) categoryLines.addAll(font.split(Component.literal(text), innerW));
        int classificationH = categoryLines.size() * IDENTITY_LINE_H;
        int previewH = TooltipPreviewLayout.viewportHeight(maxViewportH);
        int identityH = titleLineCount * titleLineH + plaqueH + classificationH;
        int headerH = headerHeight(previewH, identityH);

        // Lay out body sections against the final content width — long-form sections wrap to
        // fit rather than growing the panel, fixing the old width/wrap mismatch. StatRow/StatBlock
        // values that don't fit alongside their label wrap onto following lines instead of
        // overlapping or being silently clipped.
        // Tooltip-layout-cleanup pass (2026-09-22): a text-scaled row (see isScaledSection/
        // BODY_TEXT_SCALE) must be WRAPPED against its logical (pre-scale) width — the space it
        // will actually occupy once rendered smaller is larger in logical font units than the
        // visual budget it's given — and its logical height converted back to a visual (screen
        // pixel) contribution for the body's own vertical accounting. An unscaled row (lore,
        // preserved vanilla content, technical info, the progress bar) is laid out exactly as
        // before, untouched.
        List<LaidOutSection> laidOut = new ArrayList<>();
        int bodyContentH = 0;
        for (TooltipSection section : body) {
            if (laidOut.size() == unheadedTailStart && unheadedTailStart > 0) bodyContentH += UNHEADED_TAIL_GAP;
            if (laidOut.isEmpty() && unheadedTailStart == 0) bodyContentH += UNHEADED_BODY_LEAD;
            boolean scaled = isScaledSection(section);
            int wrapWidth = scaled ? logicalForScale(bodyContentW, bodyScale) : bodyContentW;
            LaidOutSection laid = layout(section, font, wrapWidth);
            laidOut.add(laid);
            int visualHeight = scaled ? visualForScale(laid.height(), bodyScale) : laid.height();
            bodyContentH += visualHeight + ROW_GAP;
        }

        // No universal header/body divider (Tooltip V2 Pass 1 final corrections): the body starts right after the
        // header, and each semantic group heading draws its own divider lines, so the first group's heading is the
        // only divider under the header. A bodiless item's panel therefore simply ends after its header and footer.
        int footerRowH = font.lineHeight + 2;
        int footerPadding = 3;

        // Footer: Weight left, content Origin centred, Price right — each omitted when unknown. The SHIFT/CTRL
        // hints are no longer here (detached panels), and there is no scrolling hint row.
        TooltipFooter.Info footerInfo = TooltipFooter.resolve(stack);
        Component footerWeight = footerInfo.weight() == null ? null
                : TotalityIcons.iconLabel(TotalityIcons.WEIGHT, FOOTER_WEIGHT_COLOR, footerInfo.weight());
        List<TooltipFooter.Placement> footerPlacements = TooltipFooter.layout(innerW, footerWeight == null ? -1 : font.width(footerWeight),
                footerWidth(font, footerInfo.origin()), footerWidth(font, footerInfo.price()));
        int footerH = BODY_FOOTER_GAP + footerPadding + footerRowH * TooltipFooter.rows(footerPlacements);

        int chromeH = headerH + footerH;
        // Never force a minimum body height beyond what's actually available — a tiny window
        // fails safely (clamped to zero) rather than drawing the panel past the screen edge.
        int availableBodyH = availableBodyHeight(maxViewportH, headerH, footerH);
        int bodyViewportH = Math.max(0, Math.min(bodyContentH, availableBodyH));

        int panelH = chromeH + bodyViewportH;

        // Placement: the main panel plus an optional companion card are positioned as one group, so
        // neither is pushed off-screen. Without a companion this is exactly V1's placement rule. All
        // sizes are fixed for the hovered item, so an animating preview never moves the group.
        boolean withCompanion = presentation.companionPreview() == TooltipCompanionPreview.EQUIPPED_PLAYER;
        TooltipGroupLayout.Placement placement = TooltipGroupLayout.place(x, y, panelW, panelH + panelsBlockH,
                withCompanion ? TooltipCompanionCard.WIDTH : 0, withCompanion ? TooltipCompanionCard.HEIGHT : 0,
                screenW, screenH, SCREEN_MARGIN);
        int panelX = placement.panelX();
        int panelY = placement.panelY();

        int bodyTop = panelY + headerH;
        int bodyLeft = panelX + PADDING;

        // Scroll state/target is registered only now that the panel position and viewport bounds
        // are final (Finding 7) — the active target's viewport bounds are the real on-screen
        // rectangle, not a placeholder computed before layout finished.
        TooltipScrollController.onRender(screen, slot, stack, disclosure, bodyContentH, bodyViewportH,
                bodyLeft, bodyTop, bodyContentW, bodyViewportH);
        int scrollOffset = TooltipScrollController.scrollOffset();

        long timeMs = System.currentTimeMillis();

        // Companion card (e.g. the player wearing hovered armor): its own content-sized card beside
        // the main tooltip, drawn first so the main panel always wins if anything ever touched.
        if (withCompanion && placement.cardSide() != TooltipGroupLayout.CardSide.HIDDEN) {
            int cardX = placement.cardX(), cardY = placement.cardY();
            int cardW = TooltipCompanionCard.WIDTH, cardH = TooltipCompanionCard.HEIGHT;
            TooltipPainter.drawBackground(graphics, cardX, cardY, cardW, cardH, theme);
            TooltipVignettePainter.draw(graphics, presentation.vignetteStyle(), cardX, cardY, cardW, cardH, rarityColor);
            TooltipFrameRenderer.drawBorder(graphics, cardX, cardY, cardW, cardH, theme, themeRarity);
            TooltipCompanionCard.drawPlayer(graphics, stack, cardX, cardY);
        }

        // Layer order: background -> whole-tooltip vignette -> frame -> preview -> identity text ->
        // body -> footer (no universal divider). The vignette sits under the frame and every piece of text.
        TooltipPainter.drawBackground(graphics, panelX, panelY, panelW, panelH, theme);
        TooltipVignettePainter.draw(graphics, presentation.vignetteStyle(), panelX, panelY, panelW, panelH, rarityColor);
        TooltipFrameRenderer.drawBorder(graphics, panelX, panelY, panelW, panelH, theme, themeRarity);

        int previewY = panelY + PADDING;
        TooltipHeaderPreview.draw(graphics, stack, previewState, presentation, panelX + PADDING, previewY, innerW, previewH, timeMs);

        // Identity block — no divider between the preview and the name.
        int centerX = panelX + panelW / 2;
        int lineY = previewY + previewH + PREVIEW_NAME_GAP;
        if (titleLineCount == 1) {
            drawAnimatedTitle(graphics, font, titleText, centerX - font.width(titleText) / 2, lineY,
                    theme.name(), authoredRarity, timeMs);
            lineY += titleLineH;
        } else {
            for (FormattedCharSequence line : titleLines) {
                TotalityGuiGraphics.of(graphics).drawString(line, centerX - font.width(line) / 2, lineY, theme.name(), 0, true);
                lineY += titleLineH;
            }
        }
        if (!rarityText.isEmpty()) {
            TooltipRarityPlaquePainter.draw(graphics, font, rarityText, centerX, lineY + PLAQUE_GAP_ABOVE, rarityColor);
            lineY += plaqueH;
        }
        for (FormattedCharSequence line : categoryLines) {
            TotalityGuiGraphics.of(graphics).drawString(line, centerX - font.width(line) / 2, lineY, SECONDARY_TEXT_COLOR, 0, true);
            lineY += IDENTITY_LINE_H;
        }

        if (bodyViewportH > 0) {
            try (var ignored = new CloseableScissor(graphics, bodyLeft - 2, bodyTop, bodyContentW + 4, bodyViewportH)) {
                int cursorY = bodyTop - scrollOffset;
                for (int i = 0; i < laidOut.size(); i++) {
                    LaidOutSection laid = laidOut.get(i);
                    if (i == unheadedTailStart && unheadedTailStart > 0) cursorY += UNHEADED_TAIL_GAP;
                    if (i == 0 && unheadedTailStart == 0) cursorY += UNHEADED_BODY_LEAD;
                    boolean scaled = isScaledSection(laid.section());
                    if (scaled) {
                        // Same pushMatrix/scale/popMatrix idiom this codebase already uses
                        // extensively for scaled text elsewhere (e.g. the character-screen tabs'
                        // drawSmallAt/drawTinyAt helpers) — draw at the LOGICAL (pre-scale)
                        // position so the transform lands it back at the correct VISUAL position,
                        // exactly like every other scaled-text call site in this codebase.
                        graphics.pose().pushMatrix();
                        graphics.pose().scale(bodyScale, bodyScale);
                        draw(graphics, font, laid, logicalForScale(bodyLeft, bodyScale),
                                logicalForScale(cursorY, bodyScale),
                                logicalForScale(bodyContentW, bodyScale), theme, rarityColor);
                        graphics.pose().popMatrix();
                        cursorY += visualForScale(laid.height(), bodyScale) + ROW_GAP;
                    } else {
                        draw(graphics, font, laid, bodyLeft, cursorY, bodyContentW, theme, rarityColor);
                        cursorY += laid.height() + ROW_GAP;
                    }
                }
            }
        }

        drawFooter(graphics, font, panelX, panelY, panelH, footerH, footerInfo, footerWeight, footerPlacements);

        if (!modifierPanels.isEmpty()) {
            int modifierPanelW = TooltipModifierPanels.panelWidth(font, modifierPanels);
            int rowW = TooltipModifierPanels.rowWidth(modifierPanelW, modifierPanels.size());
            int rowX = TooltipModifierPanels.rowX(panelX, panelW, rowW, screenW, SCREEN_MARGIN);
            TooltipModifierPanels.draw(graphics, font, modifierPanels, theme, rarityColor, rowX,
                    panelY + panelH + TooltipModifierPanels.GAP_ABOVE, modifierPanelW);
        }
    }

    // ── Section visibility / ordering ────────────────────────────────────────

    private static boolean isVisible(TooltipSection section, TooltipContext ctx) {
        return ctx.disclosure().includes(section.minDisclosure())
                && ctx.knowledge().isVisible(section.visibility(), ctx.disclosure());
    }

    /**
     * One contributor's entire filtered body output, tagged with the {@link TooltipSectionGroup}
     * it declared. See {@link TooltipSectionGroup} for why grouping happens per-contributor
     * rather than per-section. Package-private (not private) so the ordering algorithm below can
     * be unit-tested directly with real {@link TooltipSection} records — no {@code ItemStack} or
     * {@code Font} required.
     */
    record ContributorBlock(TooltipSectionGroup group, @Nullable TooltipGroup bodyGroup, List<TooltipSection> sections) {
        /** A block shown without a semantic group heading, at its {@link TooltipSectionGroup} position. */
        ContributorBlock(TooltipSectionGroup group, List<TooltipSection> sections) {
            this(group, null, sections);
        }
    }

    /** One part of the body: a semantic group and its merged entries, or ({@code group == null}) the unheaded tail. */
    record BodyPart(@Nullable TooltipGroup group, List<TooltipSection> sections) {}

    /**
     * Merges and orders the body. Blocks naming the same semantic group are merged into one part, in
     * contributor-registration order — so a stat row and its SHIFT breakdown, emitted together, stay together
     * — and an entry identical to one already in that group is not shown twice. Only groups with content
     * exist, so an empty group never gets a heading, while a single populated group keeps its heading. Groups
     * are ordered by {@link TooltipGroupOrdering}; blocks without a group follow as one unheaded part, ordered
     * exactly as before by {@link #orderedBody}. Pure — unit-tested.
     */
    static List<BodyPart> groupedBody(List<ContributorBlock> blocks, List<Identifier> authoredOrder,
                                      @Nullable ItemType primaryCategory) {
        Map<TooltipGroup, List<TooltipSection>> merged = new LinkedHashMap<>();
        List<ContributorBlock> unheaded = new ArrayList<>();
        for (ContributorBlock block : blocks) {
            if (block.sections().isEmpty()) continue;
            if (block.bodyGroup() == null) {
                unheaded.add(block);
                continue;
            }
            List<TooltipSection> entries = merged.computeIfAbsent(block.bodyGroup(), g -> new ArrayList<>());
            for (TooltipSection section : block.sections()) {
                if (!entries.contains(section)) entries.add(section);
            }
        }
        List<BodyPart> parts = new ArrayList<>();
        for (TooltipGroup group : TooltipGroupOrdering.order(merged.keySet(), authoredOrder, primaryCategory)) {
            parts.add(new BodyPart(group, List.copyOf(merged.get(group))));
        }
        List<TooltipSection> tail = orderedBody(unheaded);
        if (!tail.isEmpty()) parts.add(new BodyPart(null, tail));
        return parts;
    }

    /**
     * Number of body entries (headings included) before the unheaded tail — where {@link #UNHEADED_TAIL_GAP}
     * separates lore/vanilla/technical content from the last group, so it never reads as part of that group.
     */
    static int headedLength(List<BodyPart> parts) {
        int n = 0;
        for (BodyPart part : parts) if (part.group() != null) n += 1 + part.sections().size();
        return n;
    }

    /** The body as drawn: each group's heading followed by its entries, then the unheaded tail. */
    static List<TooltipSection> flattenWithHeadings(List<BodyPart> parts) {
        List<TooltipSection> body = new ArrayList<>();
        for (BodyPart part : parts) {
            if (part.group() != null) body.add(new TooltipSection.GroupHeading(part.group()));
            body.addAll(part.sections());
        }
        return body;
    }

    /** A group's localized, upper-cased heading title. */
    private static String groupTitle(TooltipGroup group) {
        return Component.translatableWithFallback(group.translationKey(), group.fallbackName()).getString()
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Lore text color: the theme's body color pulled a third of the way toward a neutral gray — quieter than the
     * mechanical rows above it, never mistaken for another stat group.
     */
    static int loreColor(int bodyColor) {
        return ColorUtils.blend(bodyColor | 0xFF000000, 0xFF8A8F98, 0.35f);
    }

    /** The item's primary (first) classification category, which picks its leading body group. */
    static @Nullable ItemType primaryCategory(List<Classification> classifications) {
        return classifications.isEmpty() ? null : classifications.get(0).category();
    }

    /**
     * Orders contributor blocks by {@link TooltipSectionGroup#ordinal()} and flattens them into
     * the final body — a stable sort (List.sort is TimSort), so blocks sharing a group keep their
     * relative contributor-registration order. Sections within a single block are never
     * reordered; grouping only decides where each whole block sits relative to the others.
     */
    static List<TooltipSection> orderedBody(List<ContributorBlock> blocks) {
        List<ContributorBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(b -> b.group().ordinal()));
        List<TooltipSection> body = new ArrayList<>();
        for (ContributorBlock block : sorted) body.addAll(block.sections());
        return body;
    }

    // ── Width policy (Finding 1) ─────────────────────────────────────────────

    /**
     * Measures the widest single-line natural width any piece of <em>compact</em> content would
     * need to avoid wrapping — the centred identity lines (name, rarity plaque, classification pairs), and every one-line-capable body
     * section (StatRow/StatBlock/PropertyBadges/Requirement/Heading). Each candidate is
     * individually capped at {@link #PREFERRED_MAX_WIDTH} before comparison, so one unusually long
     * row can never blow the whole panel past the compact ceiling — it simply wraps that one row
     * instead (exactly "long content must wrap vertically instead of forcing a very wide panel").
     * Long-form content — Description (lore), ExternalContent, TechnicalInfo, ProgressBar — is
     * deliberately excluded: these are meant to wrap at whatever width the rest of the content
     * decided, never to drive the panel wider themselves.
     */
    static int measureNaturalContentWidth(Font font, Component title, String rarityLine, List<String> classificationLines,
                                          List<TooltipSection> body, float bodyScale) {
        // V2 identity header: the name, the rarity plaque and each classification line are centred on
        // their own row (no icon column beside them any more), so each contributes only its own width —
        // the plaque its full ornamental width, flourishes included, so they are never clipped.
        int natural = 0;
        natural = Math.max(natural, Math.min(font.width(title), PREFERRED_MAX_WIDTH));
        if (!rarityLine.isEmpty()) {
            natural = Math.max(natural, Math.min(TooltipRarityPlaquePainter.totalWidth(font.width(rarityLine)), PREFERRED_MAX_WIDTH));
        }
        for (String line : classificationLines) {
            natural = Math.max(natural, Math.min(font.width(line), PREFERRED_MAX_WIDTH));
        }

        for (TooltipSection section : body) {
            int rowW = naturalRowWidth(section, font);
            if (rowW > 0) {
                // Body rows are laid out against bodyContentW, which is the full inner width (no
                // scroll gutter is reserved any more), so a row's natural width decides the panel
                // width directly.
                //
                // Tooltip-layout-cleanup pass (2026-09-22): naturalRowWidth measures a row's width
                // in LOGICAL (pre-scale) font units. A text-scaled row (isScaledSection) visually
                // needs less room than that — its real on-screen contribution is
                // visualForScale(rowW, BODY_TEXT_SCALE), never the raw logical rowW — so the panel
                // is sized to the row's actual visual footprint, not an oversized one that would
                // leave unwanted empty space around every scaled row.
                int visualRowW = isScaledSection(section) ? visualForScale(rowW, bodyScale) : rowW;
                natural = Math.max(natural, Math.min(visualRowW, PREFERRED_MAX_WIDTH));
            }
        }
        return natural;
    }

    /**
     * Tooltip-layout-cleanup pass (2026-09-22): whether a body section kind participates in the
     * body-text-scale-down ({@link #BODY_TEXT_SCALE}) — every "stat"-shaped row does; long-form or
     * preserved content does not. See {@link #BODY_TEXT_SCALE}'s own Javadoc for the full
     * per-kind rationale.
     */
    private static boolean isScaledSection(TooltipSection section) {
        return switch (section) {
            case TooltipSection.GroupHeading ignored -> false;
            case TooltipSection.ExternalContent ignored -> false;
            case TooltipSection.TechnicalInfo ignored -> false;
            case TooltipSection.ProgressBar ignored -> false;
            default -> true;
        };
    }

    /**
     * Converts a LOGICAL (pre-scale) pixel width or height into its VISUAL (on-screen) size once
     * rendered at {@code scale} — pure arithmetic, directly unit-testable (unlike the Font-dependent
     * layout functions around it).
     */
    static int visualForScale(int logical, float scale) {
        return Math.round(logical * scale);
    }

    /**
     * Converts a VISUAL (on-screen) pixel budget into the LOGICAL (pre-scale) size a scaled row's
     * own {@code Font.split}/{@code Font.width} measurements must be evaluated against, so wrapping
     * is decided relative to the space the text will actually occupy once scaled down — never the
     * raw, larger visual budget, which would under-wrap and let scaled text overflow it. Floored at
     * 1 so a degenerate (zero or negative) visual budget never produces a zero/negative wrap width.
     */
    static int logicalForScale(int visual, float scale) {
        return Math.max(1, Math.round(visual / scale));
    }

    /** Natural (unwrapped) full width one section would need on a single line, or 0 if excluded from measurement. */
    private static int naturalRowWidth(TooltipSection section, Font font) {
        return switch (section) {
            case TooltipSection.Heading h -> font.width(h.label());
            case TooltipSection.ResourceGauge r -> font.width(r.figures());
            case TooltipSection.GroupHeading g -> TooltipGroupHeadingPainter.naturalWidth(
                    TooltipGroupHeadingPainter.iconWidth(font, TooltipGlyphSupport.resolveIcon(font, g.group().icon())),
                    font.width(groupTitle(g.group())));
            case TooltipSection.StatRow r -> {
                int iconW = r.iconGlyph() != null ? font.width(r.iconGlyph()) + ICON_LABEL_GAP : 0;
                yield iconW + font.width(r.label()) + 4 + font.width(r.value());
            }
            case TooltipSection.StatBlock b -> {
                int max = 0;
                for (TooltipSection.StatLine line : b.lines()) {
                    int iconW = line.iconGlyph() != null ? font.width(line.iconGlyph()) + ICON_LABEL_GAP : 0;
                    max = Math.max(max, iconW + font.width(line.label()) + 4 + font.width(line.value()) + 8);
                }
                yield max;
            }
            case TooltipSection.PropertyBadges p -> {
                int w = 0;
                for (String label : p.labels()) w += font.width(label) + 8 + 3;
                yield w;
            }
            case TooltipSection.Requirement r -> font.width(r.text());
            case TooltipSection.IconStatRow r -> REAL_ICON_BOX_W + font.width(r.label()) + 4 + font.width(r.value());
            case TooltipSection.ProvenanceGroup g -> {
                int max = 0;
                for (TooltipSection.ProvenanceLine line : g.lines()) {
                    max = Math.max(max, PROVENANCE_INDENT + font.width("└ " + line.label() + ": ") + font.width(line.value()));
                }
                yield max;
            }
            default -> 0;
        };
    }

    // ── V2 identity header ───────────────────────────────────────────────────

    /**
     * Fixed header height: top padding, the fixed-height preview viewport, the preview-to-name gap, the
     * identity lines, and a small gap before the body. Depends only on the screen and the text, never
     * on the preview's animation, so it cannot change from frame to frame.
     */
    static int headerHeight(int previewH, int identityH) {
        return PADDING + previewH + PREVIEW_NAME_GAP + identityH + HEADER_BOTTOM_GAP;
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private record StatBlockLine(TooltipSection.StatLine line,
                                 List<FormattedCharSequence> labelLines, List<FormattedCharSequence> valueLines) {}

    private record LaidOutSection(TooltipSection section, int height,
                                  List<FormattedCharSequence> wrapped, List<FormattedCharSequence> labelLines,
                                  List<StatBlockLine> statLines) {}

    private static LaidOutSection layout(TooltipSection section, Font font, int width) {
        int rowH = font.lineHeight + 1;
        return switch (section) {
            case TooltipSection.Heading h -> new LaidOutSection(h, rowH, List.of(), List.of(), List.of());
            case TooltipSection.GroupHeading g -> new LaidOutSection(g, TooltipGroupHeadingPainter.HEIGHT, List.of(), List.of(), List.of());
            case TooltipSection.ResourceGauge r -> new LaidOutSection(r, TooltipResourceGaugePainter.height(font.lineHeight), List.of(), List.of(), List.of());
            case TooltipSection.StatRow r -> {
                int iconW = r.iconGlyph() != null ? font.width(r.iconGlyph()) + ICON_LABEL_GAP : 0;
                if (fitsOnOneLine(iconW, font.width(r.label()), font.width(r.value()), 4, width)) {
                    yield new LaidOutSection(r, rowH, List.of(), List.of(), List.of());
                }
                // Label and value both wrap safely against the panel width — the label never
                // stays an unbounded raw string, and the value is always placed below the full
                // wrapped label so it can never collide with a later label line.
                int labelAreaW = Math.max(1, width - iconW);
                List<FormattedCharSequence> labelLines = font.split(Component.literal(r.label()), labelAreaW);
                if (labelLines.isEmpty()) labelLines = font.split(Component.literal(" "), labelAreaW);
                List<FormattedCharSequence> valueLines = font.split(Component.literal(r.value()), Math.max(1, width));
                int h = labelLines.size() * rowH + valueLines.size() * rowH;
                yield new LaidOutSection(r, h, valueLines, labelLines, List.of());
            }
            case TooltipSection.StatBlock b -> {
                int blockInnerW = Math.max(1, width - 8);
                List<StatBlockLine> lines = new ArrayList<>();
                int totalRows = 0;
                for (TooltipSection.StatLine line : b.lines()) {
                    int iconW = line.iconGlyph() != null ? font.width(line.iconGlyph()) + ICON_LABEL_GAP : 0;
                    if (fitsOnOneLine(iconW, font.width(line.label()), font.width(line.value()), 4, blockInnerW)) {
                        lines.add(new StatBlockLine(line, List.of(), List.of()));
                        totalRows += 1;
                    } else {
                        int labelAreaW = Math.max(1, blockInnerW - iconW);
                        List<FormattedCharSequence> labelLines = font.split(Component.literal(line.label()), labelAreaW);
                        if (labelLines.isEmpty()) labelLines = font.split(Component.literal(" "), labelAreaW);
                        List<FormattedCharSequence> valueLines = font.split(Component.literal(line.value()), blockInnerW);
                        lines.add(new StatBlockLine(line, labelLines, valueLines));
                        totalRows += labelLines.size() + valueLines.size();
                    }
                }
                int h = 6 + totalRows * rowH;
                yield new LaidOutSection(b, h, List.of(), List.of(), lines);
            }
            case TooltipSection.ProgressBar p -> new LaidOutSection(p, 8, List.of(), List.of(), List.of());
            case TooltipSection.PropertyBadges p -> {
                int rows = wrapLabels(p.labels(), font, width).size();
                yield new LaidOutSection(p, Math.max(1, rows) * (font.lineHeight + 4), List.of(), List.of(), List.of());
            }
            case TooltipSection.Description d -> {
                List<FormattedCharSequence> lines = font.split(
                        Component.literal(d.text()).withStyle(s -> s.withItalic(true)), width);
                yield new LaidOutSection(d, lines.size() * rowH + 3, lines, List.of(), List.of());
            }
            case TooltipSection.Requirement r -> {
                List<FormattedCharSequence> lines = font.split(Component.literal(r.text()), width);
                yield new LaidOutSection(r, lines.size() * rowH, lines, List.of(), List.of());
            }
            case TooltipSection.ExternalContent e -> {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component c : e.lines()) lines.addAll(font.split(c, width));
                yield new LaidOutSection(e, lines.size() * rowH + 3, lines, List.of(), List.of());
            }
            case TooltipSection.TechnicalInfo t -> {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (String s : t.lines()) lines.addAll(font.split(Component.literal(s), width));
                yield new LaidOutSection(t, lines.size() * rowH + 3, lines, List.of(), List.of());
            }
            case TooltipSection.IconStatRow r -> {
                if (fitsOnOneLine(REAL_ICON_BOX_W, font.width(r.label()), font.width(r.value()), 4, width)) {
                    yield new LaidOutSection(r, REAL_ICON_ROW_H, List.of(), List.of(), List.of());
                }
                int labelAreaW = Math.max(1, width - REAL_ICON_BOX_W);
                List<FormattedCharSequence> labelLines = font.split(Component.literal(r.label()), labelAreaW);
                if (labelLines.isEmpty()) labelLines = font.split(Component.literal(" "), labelAreaW);
                List<FormattedCharSequence> valueLines = font.split(Component.literal(r.value()), Math.max(1, width));
                int h = iconRowValueTop(labelLines.size(), rowH) + valueLines.size() * rowH;
                yield new LaidOutSection(r, h, valueLines, labelLines, List.of());
            }
            case TooltipSection.ProvenanceGroup g -> new LaidOutSection(g, g.lines().size() * rowH, List.of(), List.of(), List.of());
            default -> new LaidOutSection(section, 0, List.of(), List.of(), List.of());
        };
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, LaidOutSection laid,
                             int x, int y, int width, TooltipTheme theme, int rarityColor) {
        switch (laid.section()) {
            case TooltipSection.GroupHeading g -> TooltipGroupHeadingPainter.draw(graphics, font,
                    TooltipGlyphSupport.resolveIcon(font, g.group().icon()), groupTitle(g.group()), x, y, width, rarityColor);
            case TooltipSection.ResourceGauge r -> TooltipResourceGaugePainter.draw(graphics, font, r.current(), r.max(), r.fillColor(),
                    r.figures(), r.figuresColor(), x, y, width);
            case TooltipSection.Heading h -> TooltipPainter.drawText(graphics, font, h.label(), x, y, theme.sectionHeader());
            case TooltipSection.StatRow r -> drawStatRow(graphics, font, r, x, y, width, laid.wrapped(), laid.labelLines());
            case TooltipSection.StatBlock ignored -> drawStatBlock(graphics, font, laid.statLines(), x, y, width, theme);
            case TooltipSection.ProgressBar p -> drawProgressBar(graphics, p, x, y, width);
            case TooltipSection.PropertyBadges p -> drawPropertyBadges(graphics, font, p, x, y, width);
            case TooltipSection.Description ignored -> drawWrapped(graphics, font, laid.wrapped(), x, y, loreColor(theme.body()));
            case TooltipSection.Requirement r -> drawWrapped(graphics, font, laid.wrapped(), x, y,
                    r.warning() ? 0xFFFF5555 : 0xFFAAAAAA);
            case TooltipSection.ExternalContent ignored -> drawWrapped(graphics, font, laid.wrapped(), x, y, 0xFFAAAAAA);
            case TooltipSection.TechnicalInfo ignored -> drawWrapped(graphics, font, laid.wrapped(), x, y, 0xFF888888);
            case TooltipSection.IconStatRow r -> drawIconStatRow(graphics, font, r, x, y, width, laid.wrapped(), laid.labelLines());
            case TooltipSection.ProvenanceGroup g -> drawProvenanceGroup(graphics, font, g, x, y, width);
            default -> {}
        }
    }

    /** A real item/status-effect sprite (Block Breaking V2 §11) — never the {@link TotalityIcons} glyph font. */
    private static void drawStatIcon(GuiGraphicsExtractor graphics, TooltipSection.StatIcon icon, int x, int y) {
        switch (icon) {
            case TooltipSection.StatIcon.Item i -> TooltipPainter.drawItem(graphics, i.stack(), x, y);
            case TooltipSection.StatIcon.Effect e -> e.effect().unwrapKey().ifPresent(key -> {
                // Status-effect icons are stitched into the "gui" sprite atlas from textures/mob_effect/*
                // under the "mob_effect/" prefix (assets/minecraft/atlases/gui.json) — same sprite the
                // vanilla effects HUD/inventory panel draws for this effect.
                Identifier sprite = Identifier.fromNamespaceAndPath(key.identifier().getNamespace(),
                        "mob_effect/" + key.identifier().getPath());
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, REAL_ICON_SIZE, REAL_ICON_SIZE);
            });
        }
    }

    private static void drawIconStatRow(GuiGraphicsExtractor graphics, Font font, TooltipSection.IconStatRow row,
                                        int x, int y, int width, List<FormattedCharSequence> wrappedValue,
                                        List<FormattedCharSequence> wrappedLabel) {
        int rowH = font.lineHeight + 1;
        int textY = y + (REAL_ICON_ROW_H - font.lineHeight) / 2;
        drawStatIcon(graphics, row.icon(), x, y + iconTop());
        int labelX = x + REAL_ICON_BOX_W;

        if (wrappedLabel.isEmpty()) {
            graphics.text(font, row.label(), labelX, textY, SECONDARY_TEXT_COLOR, false);
            if (wrappedValue.isEmpty()) {
                String value = row.value();
                graphics.text(font, value, x + width - font.width(value), textY, row.valueColor(), true);
            } else {
                int valueY = y + REAL_ICON_ROW_H;
                for (FormattedCharSequence line : wrappedValue) {
                    TotalityGuiGraphics.of(graphics).drawString(line, x, valueY, row.valueColor(), 0, false);
                    valueY += rowH;
                }
            }
            return;
        }

        // Label + value didn't fit on one line — icon stays with the first label line, following label
        // lines start flush at x. Final correction pass: the label is centred on the icon row exactly
        // like the one-line case, and the value starts below the full icon row (the height layout()
        // reserves, max(icon row, label lines)) in the right-aligned value column — it previously
        // started one text row down at x, under the icon sprite, and overlapped it.
        int lineY = y + iconRowLabelTop(wrappedLabel.size(), rowH);
        boolean first = true;
        for (FormattedCharSequence labelLine : wrappedLabel) {
            int cursorX = first ? labelX : x;
            TotalityGuiGraphics.of(graphics).drawString(labelLine, cursorX, lineY, SECONDARY_TEXT_COLOR, 0, false);
            lineY += rowH;
            first = false;
        }
        int valueY = y + iconRowValueTop(wrappedLabel.size(), rowH);
        for (FormattedCharSequence valueLine : wrappedValue) {
            TotalityGuiGraphics.of(graphics).drawString(valueLine, x + width - font.width(valueLine), valueY, row.valueColor(), 0, false);
            valueY += rowH;
        }
    }

    /** Offset of a wrapped IconStatRow's first label line: centred on the icon row, like the one-line row's text. */
    static int iconRowLabelTop(int labelLines, int rowH) {
        return Math.max(0, (REAL_ICON_ROW_H - labelLines * rowH) / 2);
    }

    /** Offset of a wrapped IconStatRow's first value line: below both the full icon row and the full wrapped label. */
    static int iconRowValueTop(int labelLines, int rowH) {
        return Math.max(REAL_ICON_ROW_H, iconRowLabelTop(labelLines, rowH) + labelLines * rowH);
    }

    /** Icon sprite's vertical offset inside its row (drawn centred in {@link #REAL_ICON_ROW_H}). */
    static int iconTop() {
        return (REAL_ICON_ROW_H - REAL_ICON_SIZE) / 2;
    }

    /** Indented "├/└ Label: value" provenance lines under the {@link TooltipSection.IconStatRow} above them. */
    private static void drawProvenanceGroup(GuiGraphicsExtractor graphics, Font font, TooltipSection.ProvenanceGroup group,
                                            int x, int y, int width) {
        int rowH = font.lineHeight + 1;
        int cursorY = y;
        int count = group.lines().size();
        for (int i = 0; i < count; i++) {
            TooltipSection.ProvenanceLine line = group.lines().get(i);
            String connector = (i == count - 1) ? "└ " : "├ ";
            String label = connector + line.label() + ":";
            graphics.text(font, label, x + PROVENANCE_INDENT, cursorY, PROVENANCE_VALUE_COLOR, false);
            String value = line.value();
            graphics.text(font, value, x + width - font.width(value), cursorY, PROVENANCE_VALUE_COLOR, false);
            cursorY += rowH;
        }
    }

    private static void drawWrapped(GuiGraphicsExtractor graphics, Font font,
                                    List<FormattedCharSequence> lines, int x, int y, int color) {
        int cursorY = y;
        int rowH = font.lineHeight + 1;
        for (FormattedCharSequence line : lines) {
            TotalityGuiGraphics.of(graphics).drawString(line, x, cursorY, color, 0, false);
            cursorY += rowH;
        }
    }

    private static void drawStatRow(GuiGraphicsExtractor graphics, Font font, TooltipSection.StatRow row,
                                    int x, int y, int width, List<FormattedCharSequence> wrappedValue,
                                    List<FormattedCharSequence> wrappedLabel) {
        int rowH = font.lineHeight + 1;
        if (wrappedLabel.isEmpty()) {
            int cursorX = x;
            if (row.iconGlyph() != null) {
                graphics.text(font, TotalityIcons.icon(row.iconGlyph(), row.iconColor()), x, y, row.iconColor(), false);
                cursorX += font.width(row.iconGlyph()) + ICON_LABEL_GAP;
            }
            graphics.text(font, row.label(), cursorX, y, labelColor(row), false);

            if (wrappedValue.isEmpty()) {
                String value = row.value();
                graphics.text(font, value, x + width - font.width(value), y, row.valueColor(), true);
                return;
            }

            // Value didn't fit alongside the label — continue on the following line(s), preserving
            // label/value association by keeping the value directly under its own label.
            int valueY = y + rowH;
            for (FormattedCharSequence line : wrappedValue) {
                TotalityGuiGraphics.of(graphics).drawString(line, x, valueY, row.valueColor(), 0, false);
                valueY += rowH;
            }
            return;
        }

        // The label itself didn't fit on one line — the icon stays with only the first wrapped
        // label line, every following label line starts flush at x, and the value is always
        // placed below the full wrapped label so it can never overlap a later label line.
        int lineY = y;
        boolean first = true;
        for (FormattedCharSequence labelLine : wrappedLabel) {
            int cursorX = x;
            if (first && row.iconGlyph() != null) {
                graphics.text(font, TotalityIcons.icon(row.iconGlyph(), row.iconColor()), x, lineY, row.iconColor(), false);
                cursorX += font.width(row.iconGlyph()) + ICON_LABEL_GAP;
            }
            TotalityGuiGraphics.of(graphics).drawString(labelLine, cursorX, lineY, labelColor(row), 0, false);
            lineY += rowH;
            first = false;
        }
        for (FormattedCharSequence valueLine : wrappedValue) {
            TotalityGuiGraphics.of(graphics).drawString(valueLine, x, lineY, row.valueColor(), 0, false);
            lineY += rowH;
        }
    }

    private static int labelColor(TooltipSection.StatRow row) {
        return row.labelColor() == TooltipSection.StatRow.DEFAULT_LABEL_COLOR ? SECONDARY_TEXT_COLOR : row.labelColor();
    }

    private static void drawStatBlock(GuiGraphicsExtractor graphics, Font font, List<StatBlockLine> lines,
                                      int x, int y, int width, TooltipTheme theme) {
        int rowH = font.lineHeight + 1;
        int totalRows = 0;
        for (StatBlockLine sbl : lines) {
            totalRows += sbl.labelLines().isEmpty() ? 1 : sbl.labelLines().size() + sbl.valueLines().size();
        }
        int boxH = 6 + totalRows * rowH;

        int outline = lighten(theme.separator(), 0.15f);
        graphics.fill(x, y, x + width, y + 1, outline);
        graphics.fill(x, y + boxH, x + width, y + boxH + 1, outline);
        graphics.fill(x, y, x + 1, y + boxH + 1, outline);
        graphics.fill(x + width - 1, y, x + width, y + boxH + 1, outline);

        int cursorY = y + 4;
        int innerX = x + 4;
        int innerW = width - 8;
        for (StatBlockLine sbl : lines) {
            TooltipSection.StatLine line = sbl.line();

            if (sbl.labelLines().isEmpty()) {
                int cursorX = innerX;
                if (line.iconGlyph() != null) {
                    graphics.text(font, TotalityIcons.icon(line.iconGlyph(), line.iconColor()), cursorX, cursorY, line.iconColor(), false);
                    cursorX += font.width(line.iconGlyph()) + ICON_LABEL_GAP;
                }
                graphics.text(font, line.label(), cursorX, cursorY, SECONDARY_TEXT_COLOR, false);
                graphics.text(font, line.value(), innerX + innerW - font.width(line.value()), cursorY, line.valueColor(), true);
                cursorY += rowH;
                continue;
            }

            // Label (and/or value) didn't fit on one row — the full label wraps first (icon
            // stays with only its first line), then the value continues below it, so neither
            // can ever overlap the other or cross the block's own boundary.
            boolean first = true;
            for (FormattedCharSequence labelLine : sbl.labelLines()) {
                int cursorX = innerX;
                if (first && line.iconGlyph() != null) {
                    graphics.text(font, TotalityIcons.icon(line.iconGlyph(), line.iconColor()), cursorX, cursorY, line.iconColor(), false);
                    cursorX += font.width(line.iconGlyph()) + ICON_LABEL_GAP;
                }
                TotalityGuiGraphics.of(graphics).drawString(labelLine, cursorX, cursorY, SECONDARY_TEXT_COLOR, 0, false);
                cursorY += rowH;
                first = false;
            }
            for (FormattedCharSequence valueLine : sbl.valueLines()) {
                TotalityGuiGraphics.of(graphics).drawString(valueLine, innerX, cursorY, line.valueColor(), 0, false);
                cursorY += rowH;
            }
        }
    }

    private static void drawProgressBar(GuiGraphicsExtractor graphics, TooltipSection.ProgressBar bar, int x, int y, int width) {
        float fraction = Math.clamp(bar.fraction(), 0f, 1f);
        int filled = (int) (width * fraction);
        graphics.fill(x, y, x + width, y + 4, 0xFF1A1A2E);
        graphics.fill(x, y, x + filled, y + 4, bar.color() | 0xFF000000);
    }

    private static void drawPropertyBadges(GuiGraphicsExtractor graphics, Font font, TooltipSection.PropertyBadges badges,
                                           int x, int y, int width) {
        List<List<String>> rows = wrapLabels(badges.labels(), font, width);
        int rowH = font.lineHeight + 4;
        int cursorY = y;
        for (List<String> row : rows) {
            int cursorX = x;
            for (String label : row) {
                int badgeW = font.width(label) + 8;
                int badgeH = font.lineHeight + 4;
                graphics.fill(cursorX, cursorY, cursorX + badgeW, cursorY + badgeH, 0xFF1A1C22);
                graphics.fill(cursorX, cursorY, cursorX + badgeW, cursorY + 1, 0xFF6B7280);
                graphics.fill(cursorX, cursorY + badgeH - 1, cursorX + badgeW, cursorY + badgeH, 0xFF6B7280);
                TooltipPainter.drawText(graphics, font, label, cursorX + 4, cursorY + 2, SECONDARY_TEXT_COLOR);
                cursorX += badgeW + 3;
            }
            cursorY += rowH;
        }
    }

    private static List<List<String>> wrapLabels(List<String> labels, Font font, int maxWidth) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentWidth = 0;
        for (String label : labels) {
            int labelW = font.width(label) + 8 + 3;
            if (!current.isEmpty() && currentWidth + labelW > maxWidth) {
                rows.add(current);
                current = new ArrayList<>();
                currentWidth = 0;
            }
            current.add(label);
            currentWidth += labelW;
        }
        if (!current.isEmpty()) rows.add(current);
        return rows;
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private static final int FOOTER_WEIGHT_COLOR = 0xFF8E949C;
    private static final int FOOTER_ORIGIN_COLOR = 0xFF5588FF;
    private static final int FOOTER_PRICE_COLOR = 0xFFD9B24C;

    /** Text width of an optional footer field, or -1 when the field is absent. */
    private static int footerWidth(Font font, @Nullable String text) {
        return text == null ? -1 : font.width(text);
    }

    /**
     * The footer: Weight (left; the Weight icon then the value), content Origin (centred, italic — the old fixed
     * "Totality" credit's style, now the item's actual origin), Price (right), laid out by {@link TooltipFooter#layout}.
     * {@link #BODY_FOOTER_GAP} of breathing room sits above it.
     */
    private static void drawFooter(GuiGraphicsExtractor graphics, Font font, int panelX, int panelY, int panelH, int footerH,
                                   TooltipFooter.Info info, @Nullable Component weight, List<TooltipFooter.Placement> placements) {
        int footerRowH = font.lineHeight + 2;
        int footerTop = panelY + panelH - footerH + BODY_FOOTER_GAP;
        int innerX = panelX + PADDING;
        for (TooltipFooter.Placement placement : placements) {
            int x = innerX + placement.x(), y = footerTop + placement.row() * footerRowH;
            switch (placement.slot()) {
                case LEFT -> TooltipPainter.drawText(graphics, font, weight, x, y, FOOTER_WEIGHT_COLOR);
                case CENTER -> TooltipPainter.drawText(graphics, font, Component.literal(info.origin())
                        .withStyle(s -> s.withColor(FOOTER_ORIGIN_COLOR).withItalic(true).withFont(FontDescription.DEFAULT)),
                        x, y, FOOTER_ORIGIN_COLOR);
                case RIGHT -> graphics.text(font, info.price(), x, y, FOOTER_PRICE_COLOR, false);
            }
        }
    }

    // ── Theme resolution — rarity-only. Classifications never influence theme/border. ─────

    private static TooltipTheme resolveTheme(ItemRarity rarity) {
        int rarityStyle = rarityBorderStyle(rarity);
        // Classifications are never consulted here — badges only, per the locked rule that
        // classification must not determine the tooltip theme or border. TooltipTheme still
        // carries a typeStyle field for now (unused by any current drawing code — verified: no
        // renderer or TooltipFrameRenderer code reads TooltipTheme.typeStyle()); it is always
        // TooltipBorderStyle.NONE rather than derived from a classification.
        int typeStyle = TooltipBorderStyle.NONE;

        return switch (rarity) {
            case UNCOMMON   -> TooltipTheme.uncommon(rarityStyle, typeStyle);
            case RARE       -> TooltipTheme.rare(rarityStyle, typeStyle);
            case EPIC       -> TooltipTheme.epic(rarityStyle, typeStyle);
            case LEGENDARY  -> TooltipTheme.legendary(rarityStyle, typeStyle);
            case MYTHICAL   -> TooltipTheme.mythical(rarityStyle, typeStyle);
            case ANCIENT    -> TooltipTheme.ancient(rarityStyle, typeStyle);
            case CURSED     -> TooltipTheme.cursed(rarityStyle, typeStyle);
            case QUEST      -> TooltipTheme.quest(rarityStyle, typeStyle);
            case BLESSED    -> TooltipTheme.blessed(rarityStyle, typeStyle);
            case SACRED     -> TooltipTheme.sacred(rarityStyle, typeStyle);
            case CELESTIAL  -> TooltipTheme.celestial(rarityStyle, typeStyle);
            case DIVINE     -> TooltipTheme.divine(rarityStyle, typeStyle);
            case GODFORGED  -> TooltipTheme.godforged(rarityStyle, typeStyle);
            case FORBIDDEN  -> TooltipTheme.forbidden(rarityStyle, typeStyle);
            case CRUDE      -> TooltipTheme.crude(rarityStyle, typeStyle);
            case CALIBRATED -> TooltipTheme.calibrated(rarityStyle, typeStyle);
            case REINFORCED -> TooltipTheme.reinforced(rarityStyle, typeStyle);
            case PROTOTYPE  -> TooltipTheme.prototype(rarityStyle, typeStyle);
            case OVERCHARGED -> TooltipTheme.overcharged(rarityStyle, typeStyle);
            case MASTERWORK -> TooltipTheme.masterwork(rarityStyle, typeStyle);
            default         -> TooltipTheme.common(rarityStyle, typeStyle);
        };
    }

    private static int rarityBorderStyle(ItemRarity rarity) {
        return switch (rarity) {
            case UNCOMMON  -> TooltipBorderStyle.TICK;
            case RARE      -> TooltipBorderStyle.GEM;
            case EPIC      -> TooltipBorderStyle.RUNE;
            case LEGENDARY -> TooltipBorderStyle.CROWN;
            case MYTHICAL  -> TooltipBorderStyle.PRISMATIC;
            case ANCIENT   -> TooltipBorderStyle.ANCIENT;
            case CURSED -> TooltipBorderStyle.CURSED;
            case FORBIDDEN -> TooltipBorderStyle.FORBIDDEN;
            case BLESSED   -> TooltipBorderStyle.BLESSED;
            case SACRED    -> TooltipBorderStyle.SACRED;
            case CELESTIAL -> TooltipBorderStyle.CELESTIAL;
            case DIVINE    -> TooltipBorderStyle.DIVINE;
            case GODFORGED -> TooltipBorderStyle.GODFORGED;
            case CRUDE       -> TooltipBorderStyle.CRUDE;
            case CALIBRATED  -> TooltipBorderStyle.CALIBRATED;
            case REINFORCED  -> TooltipBorderStyle.REINFORCED;
            case PROTOTYPE   -> TooltipBorderStyle.PROTOTYPE;
            case OVERCHARGED -> TooltipBorderStyle.OVERCHARGED;
            case MASTERWORK  -> TooltipBorderStyle.MASTERWORK;
            case QUEST -> TooltipBorderStyle.QUEST;
            default        -> TooltipBorderStyle.NONE;
        };
    }

    private static int lighten(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) + (255 - (color & 0xFF)) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawAnimatedTitle(GuiGraphicsExtractor graphics, Font font, String text,
                                          int x, int y, int color, @Nullable ItemRarity rarity, long timeMs) {
        if (rarity == null) {
            graphics.text(font, text, x, y, color, true);
            return;
        }
        switch (rarity) {
            case UNCOMMON -> TooltipAnimator.drawUncommonText(graphics, font, text, x, y, TooltipColors.forRarity(rarity), timeMs);
            case RARE      -> TooltipAnimator.drawSlowShineText(graphics, font, text, x, y, TooltipColors.forRarity(rarity), timeMs);
            case EPIC      -> TooltipAnimator.drawEpicWaveText(graphics, font, text, x, y, color, timeMs);
            case LEGENDARY -> TooltipAnimator.drawHeatDistortText(graphics, font, text, x, y, color, timeMs);
            case MYTHICAL  -> TooltipAnimator.drawMythicalText(graphics, font, text, x, y, color, timeMs);
            case ANCIENT  -> TooltipAnimator.drawAncientText(graphics, font, text, x, y, color, timeMs);
            case BLESSED -> TooltipAnimator.drawBlessedText(graphics, font, text, x, y, color, timeMs);
            case SACRED    -> TooltipAnimator.drawSacredText(graphics, font, text, x, y, color, timeMs);
            case CELESTIAL -> TooltipAnimator.drawCelestialText(graphics, font, text, x, y, color, timeMs);
            case DIVINE    -> TooltipAnimator.drawRadianceText(graphics, font, text, x, y, color, timeMs);
            case GODFORGED -> TooltipAnimator.drawGodforgedText(graphics, font, text, x, y, color, timeMs);
            case CURSED     -> TooltipAnimator.drawCursedText(graphics, font, text, x, y, color, timeMs);
            case FORBIDDEN     -> TooltipAnimator.drawForbiddenText(graphics, font, text, x, y, color, timeMs);
            case QUEST      -> TooltipAnimator.drawQuestText(graphics, font, text, x, y, color, timeMs);
            case CRUDE      -> TooltipAnimator.drawCrudeText(graphics, font, text, x, y, color, timeMs);
            case CALIBRATED -> TooltipAnimator.drawCalibratedText(graphics, font, text, x, y, color, timeMs);
            case REINFORCED -> TooltipAnimator.drawReinforcedText(graphics, font, text, x, y, color, timeMs);
            case PROTOTYPE  -> TooltipAnimator.drawPrototypeGlitchText(graphics, font, text, x, y, color, timeMs);
            case OVERCHARGED -> TooltipAnimator.drawOverchargedText(graphics, font, text, x, y, color, timeMs);
            case MASTERWORK -> TooltipAnimator.drawMasterworkShineText(graphics, font, text, x, y, color, timeMs);
            default        -> graphics.text(font, text, x, y, color, true);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ── Pure layout-policy helpers ────────────────────────────────────────────
    // Extracted (package-private, not private) specifically so they can be unit-tested in
    // isolation without a real Font/Item/ItemStack, none of which can be constructed under plain
    // JUnit in this repository.

    /**
     * Clamps the measured natural content width ({@link #measureNaturalContentWidth}) into the
     * compact preferred range ({@link #MIN_WIDTH}..{@link #PREFERRED_MAX_WIDTH}), then further
     * caps it at whatever the current scaled screen can actually offer AND at
     * {@link #MAX_SCREEN_WIDTH_FRACTION} of the screen width — whichever is smaller. Floored at
     * {@link #MIN_SAFE_INNER_WIDTH} so the result is always positive.
     */
    static int effectiveContentWidth(int screenW, int naturalContentW) {
        int maxScreenContentW = Math.max(MIN_SAFE_INNER_WIDTH, screenW - SCREEN_MARGIN * 2 - PADDING * 2);
        int screenFractionCapW = Math.max(MIN_SAFE_INNER_WIDTH, (int) (screenW * MAX_SCREEN_WIDTH_FRACTION) - PADDING * 2);
        int hardCap = Math.min(PREFERRED_MAX_WIDTH, Math.min(maxScreenContentW, screenFractionCapW));
        int widthFloor = Math.min(MIN_WIDTH, hardCap);
        return clamp(naturalContentW, widthFloor, hardCap);
    }

    /**
     * How much vertical space is left for the scrollable body after the header and footer
     * chrome, bounded by the current scaled screen height. Never negative — a tiny window
     * fails safely (zero body height) rather than a negative/overflowing viewport.
     */
    static int availableBodyHeight(int maxViewportH, int headerH, int footerH) {
        int chromeH = headerH + footerH;
        return Math.max(0, maxViewportH - chromeH);
    }

    /** Whether an icon + label + value combination fits on one row within {@code maxWidth}. */
    static boolean fitsOnOneLine(int iconW, int labelW, int valueW, int gap, int maxWidth) {
        return iconW + labelW + gap + valueW <= maxWidth;
    }


    private TotalityTooltipRenderer() {}
}
