package zcylas.totality.screen.inventory;

/** Shared color palette for all inventory screen components. */
public final class InventoryColors {
    public static final int BG           = 0xFF000005;
    public static final int BORDER       = 0xFF0A5070;
    public static final int BORDER_GLOW  = 0x440A8FBF;
    public static final int VALUE        = 0xFF00CCFF;
    public static final int LABEL        = 0xFF5599BB;
    public static final int SEPARATOR    = 0xFF0A3A5A;
    public static final int ROW_SEL      = 0xCC001830;
    public static final int ROW_HOV      = 0x88000E20;
    public static final int ROW_EVEN     = 0x11FFFFFF;
    public static final int TAB_ACTIVE   = 0xCC001830;
    public static final int TAB_INACTIVE = 0x88000010;
    public static final int BOTTOM       = 0xDD000005;
    public static final int EQUIP_SLOT   = 0xBB000010;
    public static final int EQUIP_FILLED = 0xBB001830;
    public static final int GOLD         = 0xFFFFCC44;
    public static final int SILVER       = 0xFFCCCCCC;
    public static final int COPPER       = 0xFFCC7722;
    public static final int STAT_UP      = 0xFF44FF88;
    public static final int STAT_DOWN    = 0xFFFF4444;
    public static final int EQUIP_BADGE  = 0xFF44DDAA;

    /** Blend an ARGB color with a 0–255 alpha multiplier. */
    public static int withAlpha(int color, int alpha) {
        return ((((color >> 24) & 0xFF) * alpha / 255) << 24) | (color & 0x00FFFFFF);
    }

    private InventoryColors() {}
}