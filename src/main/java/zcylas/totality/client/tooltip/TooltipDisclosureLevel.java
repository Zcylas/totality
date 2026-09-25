package zcylas.totality.client.tooltip;

import zcylas.totality.util.TotalityKeyHelper;

/**
 * Central progressive-disclosure state, resolved once per tooltip rather than polled
 * independently by every contributor/block (the old design had four separate raw-GLFW Shift
 * polls). Default = no modifier, Details = Shift, Technical = Ctrl, and both held together show
 * both — Details and Technical are independent disclosures, not a ladder: Ctrl alone shows the
 * technical information without the Shift details. This is Totality's own disclosure control — it
 * is intentionally independent of vanilla's F3+H "advanced tooltips" flag.
 *
 * <p>Technical mode was introduced largely for development testing; its long-term player-facing
 * availability (e.g. behind a tooltip setting) is still provisional.
 */
public enum TooltipDisclosureLevel {
    DEFAULT,
    DETAILS,
    TECHNICAL,
    DETAILS_AND_TECHNICAL;

    /**
     * Whether content gated at {@code required} is shown at this level: {@code DEFAULT} content always;
     * {@code DETAILS} content with Shift; {@code TECHNICAL} content with Ctrl; content needing both only
     * when both are held.
     */
    public boolean includes(TooltipDisclosureLevel required) {
        return switch (required) {
            case DEFAULT -> true;
            case DETAILS -> this == DETAILS || this == DETAILS_AND_TECHNICAL;
            case TECHNICAL -> this == TECHNICAL || this == DETAILS_AND_TECHNICAL;
            case DETAILS_AND_TECHNICAL -> this == DETAILS_AND_TECHNICAL;
        };
    }

    /** The level for the given held modifiers. */
    public static TooltipDisclosureLevel of(boolean shift, boolean ctrl) {
        if (shift && ctrl) return DETAILS_AND_TECHNICAL;
        if (ctrl) return TECHNICAL;
        if (shift) return DETAILS;
        return DEFAULT;
    }

    /** Resolves via the shared {@link TotalityKeyHelper} — client-only, call once per frame. */
    public static TooltipDisclosureLevel resolve() {
        return of(TotalityKeyHelper.isShiftPressed(), TotalityKeyHelper.isCtrlPressed());
    }
}
