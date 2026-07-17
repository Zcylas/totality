package zcylas.totality.api.shop;

import org.jetbrains.annotations.Nullable;

/**
 * Pure Trading Screen geometry/display math (Phase 4 GUI refinement pass) — the layout companion
 * to {@link TradingQuantityMath}, and deliberately shaped the same way: no client-only (GL/render/
 * {@code Screen}) dependencies, so {@link TradingScreenVerification} (which runs server-side at
 * {@code SERVER_STARTED}) can assert non-overlap and scroll-bound properties directly against the
 * exact code the real screen renders with, rather than against a parallel re-implementation that
 * could drift.
 *
 * <p><b>The GUI Scale 4 root cause this class fixes:</b> at GUI Scale 4 on a 1080p display the
 * logical screen is 480x270. The previous layout used fixed margins ({@code width/20},
 * {@code height/14}), a 30px header, and a 20px tab strip around the fixed-height 102px inventory
 * block — leaving only <b>68px</b> of content height for the catalog + detail panels. The BUY
 * detail panel's top-down field flow (icon block + Price + Stock, ~52px) and its bottom-anchored
 * action controls (quantity row + total + buttons, ~46px) need ~98px combined, so at 68px they
 * physically overlapped. {@link #compute(int, int)} now falls back to COMPACT metrics (tight
 * margins, 24px header, 16px tabs) whenever the normal metrics would leave less than
 * {@link #MIN_CONTENT_H} of content height — at 480x270 this recovers a 108px content area, which
 * fits both the essential field flow and the reserved bottom action band
 * ({@link #DETAIL_BOTTOM_BAND_H}) with no overlap. Larger screens (GUI Scale 1-3, or Scale 4 on a
 * 1440p+ display) never trigger the fallback and keep their existing proportions unchanged.
 */
public final class TradingScreenLayout {

    // ── Shared geometry constants (moved here from TradingScreen so layout math and the
    //    screen can never disagree about them) ─────────────────────────────────────────
    public static final int PAD = 6;
    public static final int SLOT_SIZE = 18;
    public static final int SLOT_GAP = 2;
    public static final int INV_COLS = 9;
    public static final int INV_STORAGE_ROWS = 3;
    public static final int CATALOG_COLS = 3;
    /** 35px rows (29px card + 6px gap) — sized for the icon-left card layout (icon 16px, name row,
     *  price row) with no interior collisions, and chosen so the compact GUI Scale 4 content
     *  height (106px, with the 26px compact header) shows exactly three full rows instead of
     *  two-and-a-half chopped ones. */
    public static final int CATALOG_ROW_H = 35;

    /** Height reserved at the bottom of the detail panel for the action band (quantity row,
     *  total line, confirm/cancel buttons) — the detail panel's top field flow must never draw
     *  into this band; the screen both skips lines that would cross it and scissors the top
     *  region to it as belt-and-braces. */
    public static final int DETAIL_BOTTOM_BAND_H = 46;

    /** Minimum content-area height for the detail panel's essential rows (icon block + Price +
     *  Stock ≈ 52px) plus {@link #DETAIL_BOTTOM_BAND_H} to coexist without overlap. When the
     *  normal layout metrics can't provide this, {@link #compute} switches to compact metrics. */
    public static final int MIN_CONTENT_H = 100;

    /** The 9-column slot grid's total width (per-mode layouts: SELL hosts the full grid in its
     *  left panel, BUY shows only the hotbar row in a bottom strip — the references' split). */
    public static final int GRID_W = INV_COLS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

    /** BUY/BUYBACK bottom strip height — one hotbar row plus padding (trade_screen_buy_v1's
     *  inventory treatment; the previous four-row block is SELL-only now, hosted in the left
     *  panel per trade_screen_sell_v1). */
    public static final int HOTBAR_STRIP_H = SLOT_SIZE + PAD * 2;

    private static final int HEADER_H = 30;
    private static final int TABS_H = 20;
    private static final int COMPACT_HEADER_H = 26;
    private static final int COMPACT_TABS_H = 16;
    private static final int COMPACT_MARGIN_X = 8;
    private static final int COMPACT_MARGIN_Y = 4;

    private TradingScreenLayout() {}

    /** Simple integer rectangle — the unit every region is expressed in. */
    public record Rect(int x, int y, int w, int h) {
        public int right()  { return x + w; }
        public int bottom() { return y + h; }

        public boolean contains(int mx, int my) {
            return mx >= x && mx < right() && my >= y && my < bottom();
        }

        public boolean intersects(Rect o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }
    }

    /** The complete screen region set for one (width, height). {@code catalog} and {@code detail}
     *  share the same y/h (the content band); {@code compact} reports whether the small-screen
     *  fallback metrics were used (true at GUI Scale 4 on 1080p, false on larger screens). */
    public record Regions(
            Rect panel, Rect header, Rect tabs, Rect catalog, Rect detail, Rect inventory, boolean compact) {

        public int contentY() { return catalog.y(); }
        public int contentH() { return catalog.h(); }

        /** The full content band (catalog + gap + detail) — used by the BUYBACK placeholder and
         *  the rejection banner, which span both columns. */
        public Rect content() {
            return new Rect(catalog.x(), catalog.y(), detail.right() - catalog.x(), catalog.h());
        }
    }

    /** BUY-layout convenience overload (also what BUYBACK uses). */
    public static Regions compute(int width, int height) {
        return compute(width, height, false);
    }

    /**
     * Computes the region set for the given logical screen size and mode layout. Per the
     * references, the two modes place the player inventory differently:
     * <ul>
     *   <li>{@code sellLayout = false} (BUY/BUYBACK, trade_screen_buy_v1): catalog left + detail
     *       right, with a single-hotbar-row strip along the bottom ({@link #HOTBAR_STRIP_H}).
     *   <li>{@code sellLayout = true} (SELL, trade_screen_sell_v1): the LEFT panel hosts the full
     *       four-row inventory grid ({@code inventory() == catalog()}), the detail sits right,
     *       and there is no bottom strip — the content band runs to the panel bottom.
     * </ul>
     * Two-pass: normal metrics first; if they leave less than {@link #MIN_CONTENT_H} for the
     * content band, recompute with compact metrics (with the per-mode strips this no longer
     * triggers at GUI Scale 4 @ 1080p — only at genuinely shorter windows). Deterministic,
     * allocation-only, safe to call every frame and from every input handler — nothing is cached
     * across resizes.
     */
    public static Regions compute(int width, int height, boolean sellLayout) {
        Regions normal = compute(width, height, width / 20, height / 14, HEADER_H, TABS_H, false, sellLayout);
        if (normal.contentH() >= MIN_CONTENT_H) return normal;
        return compute(width, height,
                Math.min(width / 20, COMPACT_MARGIN_X), Math.min(height / 14, COMPACT_MARGIN_Y),
                COMPACT_HEADER_H, COMPACT_TABS_H, true, sellLayout);
    }

    private static Regions compute(int width, int height, int px, int py,
                                   int headerH, int tabsH, boolean compact, boolean sellLayout) {
        int pw = width - px * 2, ph = height - py * 2;

        int tabsY = py + headerH;
        int contentY = tabsY + tabsH + PAD;
        int invY = py + ph - HOTBAR_STRIP_H;
        int contentBottom = sellLayout ? py + ph - PAD : invY - PAD;
        int contentH = Math.max(20, contentBottom - contentY);

        int contentX = px + PAD;
        int contentW = pw - PAD * 2;
        int catalogW = (int) (contentW * 0.48f);
        if (sellLayout) {
            // The SELL left panel must fit the 9-column grid; only concede below that when the
            // window is too narrow to give the detail panel any room at all.
            catalogW = Math.max(catalogW, Math.min(GRID_W + PAD * 2, contentW - 60));
        }
        int detailX = contentX + catalogW + PAD;
        int detailW = contentX + contentW - detailX;

        Rect catalog = new Rect(contentX, contentY, catalogW, contentH);
        Rect inventory = sellLayout ? catalog : new Rect(contentX, invY, contentW, HOTBAR_STRIP_H);

        return new Regions(
                new Rect(px, py, pw, ph),
                new Rect(px, py, pw, headerH),
                new Rect(px, tabsY, pw, tabsH),
                catalog,
                new Rect(detailX, contentY, detailW, contentH),
                inventory,
                compact);
    }

    // ── Catalog scroll math (draw, click-hit-testing, and mouse-wheel all share these, so the
    //    scrolled hitboxes can never drift from the scrolled visuals) ──────────────────────────

    public static int visibleCatalogRows(int contentH) {
        return Math.max(1, contentH / CATALOG_ROW_H);
    }

    public static int totalCatalogRows(int entryCount) {
        return Math.max(1, (entryCount + CATALOG_COLS - 1) / CATALOG_COLS);
    }

    public static int maxScrollRows(int entryCount, int contentH) {
        return Math.max(0, totalCatalogRows(entryCount) - visibleCatalogRows(contentH));
    }

    /** Clamps a scroll position into {@code [0, maxScrollRows]} — the single clamp used by the
     *  draw path, the wheel handler, and the click hit-test alike. */
    public static int clampScroll(int scroll, int entryCount, int contentH) {
        return Math.max(0, Math.min(scroll, maxScrollRows(entryCount, contentH)));
    }

    // ── Display formatting ────────────────────────────────────────────────────────────────────

    /** Thousands-grouped Credit amount (e.g. 1234567 → "1,234,567" in an English locale) — used
     *  for EVERY displayed Credit value (header balances, card prices, Price/Payout Each, totals),
     *  not just the header, so large values stay readable everywhere. Grouping character follows
     *  the JVM's default locale, matching the pre-existing header behavior. */
    public static String formatCredits(long amount) {
        return String.format("%,d", amount);
    }

    // ── SELL-slot rejection reasons (client-displayable) ──────────────────────────────────────

    /**
     * The localized reason key explaining why an inventory slot's item cannot be sold, or
     * {@code null} when it is sellable. Distinguishes the two established reasons (design
     * document Section 5: acceptance and value are independent conditions):
     * <ul>
     *   <li>not in the merchant's accepted-category tag → {@code totality.trading.reject.not_accepted}
     *   <li>accepted category but no central item value  → {@code totality.trading.reject.no_value}
     * </ul>
     * Reuses the EXACT keys {@link TradeRejectionKeys} already resolves server-side for a
     * committed-SELL rejection, so the hover explanation and an actual server rejection can never
     * word the same reason differently. The inputs come from server-derived data only (the
     * vanilla-synced accepted-items tag, and {@code ShowShopStatePayload.valuedInventorySlots}) —
     * this method invents no client-side acceptance rule of its own, it only picks which
     * established reason to show.
     */
    @Nullable
    public static String sellSlotIssueKey(boolean acceptedByTag, boolean hasKnownValue) {
        if (!acceptedByTag) return "totality.trading.reject.not_accepted";
        if (!hasKnownValue) return "totality.trading.reject.no_value";
        return null;
    }
}
