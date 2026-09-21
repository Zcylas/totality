package zcylas.totality.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.client.util.CloseableScissor;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.client.renderer.gui.TotalityGuiGraphics;
import zcylas.totality.client.tooltip.contributor.TooltipContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributorRegistry;
import zcylas.totality.client.tooltip.renderer.*;
import zcylas.totality.client.tooltip.section.TooltipSection;
import zcylas.totality.client.tooltip.theme.TooltipBorderStyle;
import zcylas.totality.client.tooltip.theme.TooltipColors;
import zcylas.totality.client.tooltip.theme.TooltipTheme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * {@link ItemRarity} selects the panel theme; classifications only ever produce badges.
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
    private static final int ROW_GAP = 3;
    /** Absolute floor for the panel's inner content width — guarantees a positive, drawable width even on a tiny screen. */
    private static final int MIN_SAFE_INNER_WIDTH = 60;
    /** Finding 2: horizontal gap between adjacent badges in the flowing rarity/classification row. */
    private static final int BADGE_GAP = 4;
    /** Finding 2: vertical gap between wrapped badge rows. */
    private static final int BADGE_ROW_GAP = 3;
    /** Finding 2: compact per-side badge padding — replaces the old forced 60px minimum badge width. */
    private static final int BADGE_PADDING = 10;
    /** Finding 4/8: width reserved for the scroll indicator so body text can never sit under it, overflowing or not. */
    private static final int SCROLLBAR_GUTTER = 6;
    /** Finding 6: shared secondary-text color (stat labels) — a touch lighter than the old 0xFF888888 for readability. */
    private static final int SECONDARY_TEXT_COLOR = 0xFF9CA3AF;
    /**
     * Presentation-cleanup pass, Finding 1: fixed horizontal gap between an icon glyph and the
     * label that follows it (Damage, Range, Stamina Cost, and every similar icon-led StatRow/
     * StatBlock line) — was an unnamed {@code + 3}, which read as the icon sitting too close to
     * its label. Compact but clearly visible.
     */
    private static final int ICON_LABEL_GAP = 5;
    /**
     * Presentation-cleanup pass, Finding 2: small extra breathing room between the body's last
     * row and the footer's own text — previously exactly zero (the footer's reserved chrome
     * height began immediately where the body viewport ended). Light polish only, not a return to
     * a larger panel: this adds a few pixels, not a new row of content.
     */
    private static final int BODY_FOOTER_GAP = 4;

    /**
     * Explicit opt-in check — the single gate deciding custom vs. vanilla rendering. Delegates
     * to {@link ItemComponents#hasTooltipPresentation}, which documents the temporary rarity
     * compatibility fallback for pre-existing registrations.
     */
    public static boolean isEligible(ItemStack stack) {
        return ItemComponents.hasTooltipPresentation(stack);
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
        List<ItemType> classifications = List.of();
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
                    case TooltipSection.ClassificationBadges badges -> classifications = badges.classifications();
                    default -> bodySections.add(section);
                }
            }
            if (!bodySections.isEmpty()) {
                blocks.add(new ContributorBlock(contributor.sectionGroup(), bodySections));
            }
        }
        TooltipDocument document = builder.build();
        List<TooltipSection> body = orderedBody(blocks);

        // Neutral fallback theme for explicitly opted-in items without an authored rarity —
        // never invented, just a stable default appearance (COMMON's flat frame/no animation).
        // Rarity is the ONLY input to theme/border resolution — classifications never are.
        ItemRarity themeRarity = authoredRarity != null ? authoredRarity : ItemRarity.COMMON;
        TooltipTheme theme = resolveTheme(themeRarity);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        String titleText = title.getString();
        int iconSize = 16;
        int iconAreaW = iconSize + 8;

        // Width policy (Finding 1): measure the actual semantic content's natural (unwrapped)
        // width, then clamp into a compact preferred range and the current screen's constraints.
        // Long-form content (lore, preserved external lines, technical info) is deliberately
        // excluded from this measurement — see measureNaturalContentWidth.
        int naturalContentW = measureNaturalContentWidth(font, title, authoredRarity, classifications, body, iconAreaW);
        int contentW = effectiveContentWidth(screenW, naturalContentW);
        int panelW = PADDING + contentW + PADDING;
        int innerW = panelW - PADDING * 2;
        // Body content never sits under the scroll indicator's reserved gutter, overflowing or not.
        int bodyContentW = Math.max(1, innerW - SCROLLBAR_GUTTER);

        // Title: native Font wrapping against the final (already-decided) width — never a single
        // unbounded line, never crosses the panel boundary.
        int titleAreaW = Math.max(1, innerW - iconAreaW);
        List<FormattedCharSequence> titleLines = font.split(title, titleAreaW);
        if (titleLines.isEmpty()) titleLines = font.split(Component.literal(" "), titleAreaW);
        int titleLineCount = Math.max(1, titleLines.size());
        int titleLineH = font.lineHeight + 1;

        // Header badges (Finding 2): rarity and every classification flow together in one ordered
        // row, wrapping only when the next badge genuinely doesn't fit — never forced onto their
        // own separate rows.
        List<BadgeSpec> badgeSpecs = buildBadgeSpecs(authoredRarity, classifications);
        List<List<BadgeSpec>> badgeRows = badgeSpecs.isEmpty() ? List.of() : wrapBadges(badgeSpecs, font, titleAreaW);
        int badgeRowH = font.lineHeight + 4;
        int headerContentH = titleLineCount * titleLineH
                + (badgeRows.isEmpty() ? 0 : badgeRows.size() * (badgeRowH + BADGE_ROW_GAP));
        int headerH = PADDING + Math.max(iconSize, headerContentH) + PADDING;

        // Lay out body sections against the final content width — long-form sections wrap to
        // fit rather than growing the panel, fixing the old width/wrap mismatch. StatRow/StatBlock
        // values that don't fit alongside their label wrap onto following lines instead of
        // overlapping or being silently clipped.
        List<LaidOutSection> laidOut = new ArrayList<>();
        int bodyContentH = 0;
        for (TooltipSection section : body) {
            LaidOutSection laid = layout(section, font, bodyContentW);
            laidOut.add(laid);
            bodyContentH += laid.height() + ROW_GAP;
        }

        // Presentation-cleanup pass, Finding 2: separatorH grew from 7 to 9 for a touch more
        // breathing room at the header/body transition (light polish only, per the finding).
        int separatorH = 9;
        int footerRowH = font.lineHeight + 2;
        int footerPadding = 3;
        int maxViewportH = Math.max(0, screenH - SCREEN_MARGIN * 2);

        // Footer capacity is derived from the explicit disclosure-capability model — never by
        // scanning which sections happen to be present at the current level (contributors often
        // only emit Details/Technical content when that level is already selected).
        FooterHintFlags hintFlags = footerHintFlags(ctx.disclosure(), document.availableLevels());
        List<String> staticHints = new ArrayList<>();
        if (hintFlags.showShift()) staticHints.add("SHIFT: Details");
        if (hintFlags.showCtrl()) staticHints.add("CTRL: Technical");

        // Two-pass footer/viewport sizing to break the circular dependency between "does the
        // scroll hint need to be shown" and "how tall is the footer" (which affects how much
        // room the body viewport has, which affects whether scrolling is needed at all). Pass 1
        // sizes the footer from the hints we already know about (Shift/Ctrl); pass 2 adds the
        // Scroll hint if that provisional sizing already implies overflow. The tiny (~1 footer
        // row) difference between the two passes can only disagree in an extreme edge case where
        // content height sits within a few pixels of the threshold either way.
        List<FormattedCharSequence> hintLines1 = footerHintLines(staticHints, font, innerW);
        int footerH1 = footerRowH * (1 + hintLines1.size()) + footerPadding + BODY_FOOTER_GAP;
        int availableBodyH1 = availableBodyHeight(maxViewportH, headerH, separatorH, footerH1);
        boolean overflowingGuess = bodyContentH > availableBodyH1;

        List<String> finalHints = new ArrayList<>(staticHints);
        if (overflowingGuess) finalHints.add("Scroll: More");
        List<FormattedCharSequence> hintLines = footerHintLines(finalHints, font, innerW);
        // + BODY_FOOTER_GAP reserves the extra breathing room between the body's last row and
        // the footer's own text (drawFooter offsets its draw position down by the same amount).
        int footerH = footerRowH * (1 + hintLines.size()) + footerPadding + BODY_FOOTER_GAP;

        int chromeH = headerH + separatorH + footerH;
        // Never force a minimum body height beyond what's actually available — a tiny window
        // fails safely (clamped to zero) rather than drawing the panel past the screen edge.
        int availableBodyH = availableBodyHeight(maxViewportH, headerH, separatorH, footerH);
        int bodyViewportH = Math.max(0, Math.min(bodyContentH, availableBodyH));

        int panelH = chromeH + bodyViewportH;

        int panelX = x + 12;
        int panelY = y - 12;
        if (panelX + panelW > screenW - SCREEN_MARGIN) panelX = x - panelW - 12;
        if (panelX < SCREEN_MARGIN) panelX = SCREEN_MARGIN;
        if (panelY + panelH > screenH - SCREEN_MARGIN) panelY = screenH - panelH - SCREEN_MARGIN;
        if (panelY < SCREEN_MARGIN) panelY = SCREEN_MARGIN;

        int separatorY = panelY + headerH;
        int bodyTop = separatorY + separatorH;
        int bodyLeft = panelX + PADDING;

        // Scroll state/target is registered only now that the panel position and viewport bounds
        // are final (Finding 7) — the active target's viewport bounds are the real on-screen
        // rectangle, not a placeholder computed before layout finished.
        TooltipScrollController.onRender(screen, slot, stack, disclosure, bodyContentH, bodyViewportH,
                bodyLeft, bodyTop, bodyContentW, bodyViewportH);
        int scrollOffset = TooltipScrollController.scrollOffset();
        boolean overflowing = TooltipScrollController.isOverflowing();

        TooltipPainter.drawBackground(graphics, panelX, panelY, panelW, panelH, theme);
        TooltipFrameRenderer.drawBorder(graphics, panelX, panelY, panelW, panelH, theme, themeRarity);

        int iconX = panelX + PADDING;
        int iconY = panelY + PADDING;
        TooltipPainter.drawItem(graphics, stack, iconX, iconY);

        int nameX = iconX + iconAreaW;
        int nameY = panelY + PADDING;
        long timeMs = System.currentTimeMillis();
        if (titleLineCount == 1) {
            drawAnimatedTitle(graphics, font, titleText, nameX, nameY, theme.name(), authoredRarity, timeMs);
        } else {
            int lineY = nameY;
            for (FormattedCharSequence line : titleLines) {
                TotalityGuiGraphics.of(graphics).drawString(line, nameX, lineY, theme.name(), 0, true);
                lineY += titleLineH;
            }
        }

        if (!badgeRows.isEmpty()) {
            int badgeY = nameY + titleLineCount * titleLineH + 2;
            for (List<BadgeSpec> row : badgeRows) {
                drawBadgeRow(graphics, font, row, nameX, badgeY);
                badgeY += badgeRowH + BADGE_ROW_GAP;
            }
        }

        TooltipPainter.drawSeparator(graphics, panelX + PADDING, separatorY, innerW, theme);

        if (bodyViewportH > 0) {
            try (var ignored = new CloseableScissor(graphics, bodyLeft - 2, bodyTop, bodyContentW + 4, bodyViewportH)) {
                int cursorY = bodyTop - scrollOffset;
                for (LaidOutSection laid : laidOut) {
                    draw(graphics, font, laid, bodyLeft, cursorY, bodyContentW, theme);
                    cursorY += laid.height() + ROW_GAP;
                }
            }
            // Scroll indicator is drawn AFTER the scissor closes so it is never itself clipped,
            // and lives entirely inside SCROLLBAR_GUTTER — outside the clipped text column, so it
            // can never cover or clip body content (Finding 8).
            if (overflowing) {
                drawScrollIndicator(graphics, bodyLeft, bodyTop, bodyContentW, bodyViewportH, bodyContentH, scrollOffset);
            }
        }

        drawFooter(graphics, font, panelX, panelY, panelW, panelH, footerH, hintLines);
    }

    // ── Section visibility / ordering ────────────────────────────────────────

    private static boolean isVisible(TooltipSection section, TooltipContext ctx) {
        return ctx.disclosure().atLeast(section.minDisclosure())
                && ctx.knowledge().isVisible(section.visibility(), ctx.disclosure());
    }

    /**
     * One contributor's entire filtered body output, tagged with the {@link TooltipSectionGroup}
     * it declared. See {@link TooltipSectionGroup} for why grouping happens per-contributor
     * rather than per-section. Package-private (not private) so the ordering algorithm below can
     * be unit-tested directly with real {@link TooltipSection} records — no {@code ItemStack} or
     * {@code Font} required.
     */
    record ContributorBlock(TooltipSectionGroup group, List<TooltipSection> sections) {}

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
     * need to avoid wrapping — title, the flowing badge row, and every one-line-capable body
     * section (StatRow/StatBlock/PropertyBadges/Requirement/Heading). Each candidate is
     * individually capped at {@link #PREFERRED_MAX_WIDTH} before comparison, so one unusually long
     * row can never blow the whole panel past the compact ceiling — it simply wraps that one row
     * instead (exactly "long content must wrap vertically instead of forcing a very wide panel").
     * Long-form content — Description (lore), ExternalContent, TechnicalInfo, ProgressBar — is
     * deliberately excluded: these are meant to wrap at whatever width the rest of the content
     * decided, never to drive the panel wider themselves.
     */
    static int measureNaturalContentWidth(Font font, Component title, @Nullable ItemRarity authoredRarity,
                                          List<ItemType> classifications, List<TooltipSection> body, int iconAreaW) {
        int natural = 0;
        natural = Math.max(natural, Math.min(font.width(title) + iconAreaW, PREFERRED_MAX_WIDTH));

        int badgeRowW = naturalBadgeRowWidth(font, authoredRarity, classifications);
        if (badgeRowW > 0) {
            natural = Math.max(natural, Math.min(badgeRowW + iconAreaW, PREFERRED_MAX_WIDTH));
        }

        for (TooltipSection section : body) {
            int rowW = naturalRowWidth(section, font);
            if (rowW > 0) {
                natural = Math.max(natural, Math.min(rowW, PREFERRED_MAX_WIDTH));
            }
        }
        return natural;
    }

    /** Natural (unwrapped) full width one section would need on a single line, or 0 if excluded from measurement. */
    private static int naturalRowWidth(TooltipSection section, Font font) {
        return switch (section) {
            case TooltipSection.Heading h -> font.width(h.label());
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
            default -> 0;
        };
    }

    private static int naturalBadgeRowWidth(Font font, @Nullable ItemRarity authoredRarity, List<ItemType> classifications) {
        int w = 0;
        boolean any = false;
        if (authoredRarity != null) {
            w += badgeWidth(font, authoredRarity.getSerializedName().toUpperCase()) + BADGE_GAP;
            any = true;
        }
        for (ItemType t : classifications) {
            w += badgeWidth(font, t.getSerializedName().toUpperCase()) + BADGE_GAP;
            any = true;
        }
        return any ? w - BADGE_GAP : 0;
    }

    // ── Header badges (Finding 2) ────────────────────────────────────────────

    private record BadgeSpec(String text, int color) {}

    private static List<BadgeSpec> buildBadgeSpecs(@Nullable ItemRarity authoredRarity, List<ItemType> classifications) {
        if (authoredRarity == null && classifications.isEmpty()) return List.of();
        List<BadgeSpec> specs = new ArrayList<>();
        if (authoredRarity != null) {
            specs.add(new BadgeSpec(authoredRarity.getSerializedName().toUpperCase(), TooltipColors.forRarity(authoredRarity)));
        }
        for (ItemType type : classifications) {
            specs.add(new BadgeSpec(type.getSerializedName().toUpperCase(), TooltipColors.forType(type)));
        }
        return specs;
    }

    private static int badgeWidth(Font font, String text) {
        return font.width(text) + BADGE_PADDING;
    }

    /**
     * Flows rarity and every classification badge into one ordered row (preserving authored
     * order — rarity first, then classifications exactly as registered), wrapping only when the
     * next badge genuinely doesn't fit within {@code maxWidth}. Rarity never gets a mandatory
     * separate row.
     */
    static List<List<BadgeSpec>> wrapBadges(List<BadgeSpec> badges, Font font, int maxWidth) {
        List<List<BadgeSpec>> rows = new ArrayList<>();
        List<BadgeSpec> current = new ArrayList<>();
        int currentWidth = 0;
        for (BadgeSpec badge : badges) {
            int w = badgeWidth(font, badge.text());
            int candidateWidth = current.isEmpty() ? w : currentWidth + BADGE_GAP + w;
            if (!current.isEmpty() && candidateWidth > maxWidth) {
                rows.add(current);
                current = new ArrayList<>();
                currentWidth = w;
            } else {
                currentWidth = candidateWidth;
            }
            current.add(badge);
        }
        if (!current.isEmpty()) rows.add(current);
        return rows;
    }

    private static void drawBadgeRow(GuiGraphicsExtractor graphics, Font font, List<BadgeSpec> row, int x, int y) {
        int cursorX = x;
        int h = font.lineHeight + 4;
        for (BadgeSpec badge : row) {
            int w = badgeWidth(font, badge.text());
            int bg = darken(badge.color(), 0.4f);
            graphics.fill(cursorX, y, cursorX + w, y + h, bg);
            TooltipPainter.drawText(graphics, font, badge.text(), cursorX + (w - font.width(badge.text())) / 2, y + 2,
                    lighten(badge.color(), 0.4f));
            cursorX += w + BADGE_GAP;
        }
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
            default -> new LaidOutSection(section, 0, List.of(), List.of(), List.of());
        };
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, LaidOutSection laid,
                             int x, int y, int width, TooltipTheme theme) {
        switch (laid.section()) {
            case TooltipSection.Heading h -> TooltipPainter.drawText(graphics, font, h.label(), x, y, theme.sectionHeader());
            case TooltipSection.StatRow r -> drawStatRow(graphics, font, r, x, y, width, laid.wrapped(), laid.labelLines());
            case TooltipSection.StatBlock ignored -> drawStatBlock(graphics, font, laid.statLines(), x, y, width, theme);
            case TooltipSection.ProgressBar p -> drawProgressBar(graphics, p, x, y, width);
            case TooltipSection.PropertyBadges p -> drawPropertyBadges(graphics, font, p, x, y, width);
            case TooltipSection.Description ignored -> drawWrapped(graphics, font, laid.wrapped(), x, y, theme.body());
            case TooltipSection.Requirement r -> drawWrapped(graphics, font, laid.wrapped(), x, y,
                    r.warning() ? 0xFFFF5555 : 0xFFAAAAAA);
            case TooltipSection.ExternalContent ignored -> drawWrapped(graphics, font, laid.wrapped(), x, y, 0xFFAAAAAA);
            case TooltipSection.TechnicalInfo ignored -> drawWrapped(graphics, font, laid.wrapped(), x, y, 0xFF888888);
            default -> {}
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
            graphics.text(font, row.label(), cursorX, y, SECONDARY_TEXT_COLOR, false);

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
            TotalityGuiGraphics.of(graphics).drawString(labelLine, cursorX, lineY, SECONDARY_TEXT_COLOR, 0, false);
            lineY += rowH;
            first = false;
        }
        for (FormattedCharSequence valueLine : wrappedValue) {
            TotalityGuiGraphics.of(graphics).drawString(valueLine, x, lineY, row.valueColor(), 0, false);
            lineY += rowH;
        }
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

    // ── Scroll indicator ─────────────────────────────────────────────────────

    /**
     * A restrained track + thumb drawn inside {@link #SCROLLBAR_GUTTER} — the reserved region
     * immediately to the right of the body content column — only when the body is overflowing.
     * Must be called after the body's {@link CloseableScissor} has closed — drawing it inside
     * that scissor region would clip it out of view for exactly the case it exists to signal.
     */
    private static void drawScrollIndicator(GuiGraphicsExtractor graphics, int bodyLeft, int bodyTop,
                                            int bodyContentW, int viewportH, int contentH, int scrollOffset) {
        int trackW = 2;
        int trackX = bodyLeft + bodyContentW + (SCROLLBAR_GUTTER - trackW) / 2;
        graphics.fill(trackX, bodyTop, trackX + trackW, bodyTop + viewportH, 0x40FFFFFF);

        int maxScroll = Math.max(1, contentH - viewportH);
        int thumbH = Math.min(viewportH, Math.max(6, (int) ((long) viewportH * viewportH / contentH)));
        int thumbTravel = Math.max(0, viewportH - thumbH);
        int thumbY = bodyTop + (int) ((long) scrollOffset * thumbTravel / maxScroll);
        graphics.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, 0xCCFFFFFF);
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    /**
     * Greedily packs hint strings onto as few lines as fit within {@code maxWidth}, then runs
     * every packed line through the native {@link Font#split} splitter. This is what lets a
     * single hint that is itself wider than {@code maxWidth} (e.g. "CTRL: Technical" under
     * {@link #MIN_SAFE_INNER_WIDTH}) still wrap safely instead of drawing past the panel edge —
     * the old packing-only version left an over-wide single hint unsplit.
     */
    static List<FormattedCharSequence> footerHintLines(List<String> hints, Font font, int maxWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (hints.isEmpty()) return lines;
        int safeWidth = Math.max(1, maxWidth);

        StringBuilder current = new StringBuilder();
        for (String hint : hints) {
            String candidate = current.isEmpty() ? hint : current + "   " + hint;
            if (font.width(candidate) > safeWidth && !current.isEmpty()) {
                lines.addAll(splitHintLine(current.toString(), font, safeWidth));
                current = new StringBuilder(hint);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.addAll(splitHintLine(current.toString(), font, safeWidth));
        return lines;
    }

    private static List<FormattedCharSequence> splitHintLine(String text, Font font, int maxWidth) {
        List<FormattedCharSequence> split = font.split(Component.literal(text), maxWidth);
        return split.isEmpty() ? font.split(Component.literal(" "), maxWidth) : split;
    }

    /**
     * "Totality" always occupies its own first footer row; hint lines (already wrapped by the
     * caller) are drawn below it, right-aligned, one per line — this guarantees hints can never
     * overlap the credit line by construction, regardless of how many hints are showing. Each
     * line's right-aligned X is clamped to never sit left of the panel's own inner padding, so an
     * (already-wrapped) line wider than the panel still draws from a valid, positive X rather
     * than a negative/left-of-panel one.
     *
     * <p>Finding 6: the decorative three-dot footer marker (no informational value) has been
     * removed entirely, and the footer's own padding tightened — the footer is now exactly as
     * tall as "Totality" plus however many hint lines are actually showing, plus
     * {@link #BODY_FOOTER_GAP} of breathing room above it (presentation-cleanup pass, Finding 2).
     */
    private static void drawFooter(GuiGraphicsExtractor graphics, Font font, int panelX, int panelY,
                                   int panelW, int panelH, int footerH, List<FormattedCharSequence> hintLines) {
        int footerRowH = font.lineHeight + 2;
        int footerTop = panelY + panelH - footerH + BODY_FOOTER_GAP;

        Component totalityLine = Component.literal("Totality")
                .withStyle(s -> s.withColor(0xFF5588FF).withItalic(true).withFont(FontDescription.DEFAULT));
        TooltipPainter.drawText(graphics, font, totalityLine, panelX + PADDING, footerTop, 0xFF5588FF);

        int minX = panelX + PADDING;
        int lineY = footerTop + footerRowH;
        for (FormattedCharSequence hintLine : hintLines) {
            int lineX = Math.max(minX, panelX + panelW - PADDING - font.width(hintLine));
            TotalityGuiGraphics.of(graphics).drawString(hintLine, lineX, lineY, 0xFF666666, 0, false);
            lineY += footerRowH;
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

    private static int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
     * How much vertical space is left for the scrollable body after the header, separator, and
     * footer chrome, bounded by the current scaled screen height. Never negative — a tiny window
     * fails safely (zero body height) rather than a negative/overflowing viewport.
     */
    static int availableBodyHeight(int maxViewportH, int headerH, int separatorH, int footerH) {
        int chromeH = headerH + separatorH + footerH;
        return Math.max(0, maxViewportH - chromeH);
    }

    /** Whether an icon + label + value combination fits on one row within {@code maxWidth}. */
    static boolean fitsOnOneLine(int iconW, int labelW, int valueW, int gap, int maxWidth) {
        return iconW + labelW + gap + valueW <= maxWidth;
    }

    /** Which footer hints apply at the given disclosure level — Ctrl outranks Shift by construction. */
    record FooterHintFlags(boolean showShift, boolean showCtrl) {}

    /**
     * Decides SHIFT/CTRL footer hint visibility purely from the explicit
     * {@link TooltipDocument#availableLevels()} capability set and the current disclosure level
     * — never by inspecting which sections a contributor happened to emit. Shift is only offered
     * at {@code DEFAULT} (no point suggesting it once already viewing Details); Ctrl is offered
     * at both {@code DEFAULT} and {@code DETAILS} (hidden only once already at {@code TECHNICAL}).
     */
    static FooterHintFlags footerHintFlags(TooltipDisclosureLevel disclosure, Set<TooltipDisclosureLevel> availableLevels) {
        boolean showShift = disclosure == TooltipDisclosureLevel.DEFAULT
                && availableLevels.contains(TooltipDisclosureLevel.DETAILS);
        boolean showCtrl = disclosure != TooltipDisclosureLevel.TECHNICAL
                && availableLevels.contains(TooltipDisclosureLevel.TECHNICAL);
        return new FooterHintFlags(showShift, showCtrl);
    }

    private TotalityTooltipRenderer() {}
}
