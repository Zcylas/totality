package zcylas.totality.screen.inventory;

/** Shared layout constants and computed positions for all inventory screen components. */
public final class InventoryLayout {

    // ── Fixed constants ───────────────────────────────────────────────────────
    public static final int BOTTOM_H   = 28;
    public static final int TAB_H      = 16;
    public static final int CAT_TAB_H  = 14;
    public static final int ROW_H      = 16;
    public static final int LEFT_W_PCT = 45;
    public static final int PADDING    = 10;
    public static final int ICON_SIZE  = 16;
    public static final int COIN_SQ    = 7;

    // ── Computed from screen dimensions ──────────────────────────────────────
    public final int listX, listY, listW, listH, visibleRows;
    public final int detailX, detailY, detailW, detailH;

    public InventoryLayout(int screenWidth, int screenHeight) {
        listX       = PADDING;
        listY       = PADDING + TAB_H + 4 + CAT_TAB_H + 2;
        listW       = screenWidth * LEFT_W_PCT / 100;
        listH       = screenHeight - listY - BOTTOM_H - PADDING;
        visibleRows = Math.max(1, (listH - ROW_H) / ROW_H);
        detailX     = listX + listW + PADDING;
        detailY     = PADDING + TAB_H + 4;
        detailW     = screenWidth - detailX - PADDING;
        detailH     = screenHeight - detailY - BOTTOM_H - PADDING;
    }

    private InventoryLayout() { throw new UnsupportedOperationException(); }
}