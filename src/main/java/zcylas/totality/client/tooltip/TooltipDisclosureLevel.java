package zcylas.totality.client.tooltip;

import zcylas.totality.util.TotalityKeyHelper;

/**
 * Central progressive-disclosure level, resolved once per tooltip rather than polled
 * independently by every contributor/block (the old design had four separate raw-GLFW Shift
 * polls). Default = no modifier, Details = Shift, Technical = Ctrl. If both are held, Technical
 * wins. This is Totality's own disclosure control — it is intentionally independent of
 * vanilla's F3+H "advanced tooltips" flag.
 */
public enum TooltipDisclosureLevel {
    DEFAULT,
    DETAILS,
    TECHNICAL;

    public boolean atLeast(TooltipDisclosureLevel other) {
        return this.ordinal() >= other.ordinal();
    }

    /** Resolves via the shared {@link TotalityKeyHelper} — client-only, call once per frame. */
    public static TooltipDisclosureLevel resolve() {
        if (TotalityKeyHelper.isCtrlPressed()) return TECHNICAL;
        if (TotalityKeyHelper.isShiftPressed()) return DETAILS;
        return DEFAULT;
    }
}
