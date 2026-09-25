package zcylas.totality.client.tooltip.renderer;

import zcylas.totality.util.color.ColorUtils;

/**
 * Bar colors of the tooltip's resource sections. Pure and unit-tested.
 * <ul>
 *   <li><b>Energy</b> stays in the canonical UE blue family and simply darkens as it depletes — it never turns
 *       orange or red.</li>
 *   <li><b>Durability</b> steps GREEN → ORANGE → RED as the item wears. Initial thresholds: green above 50%
 *       remaining, orange above 20%, red at 20% and below. The 50% boundary matches Totality's existing item-bar
 *       convention ({@code UEItem#getEnergyBarColor}); its 5% red point is too late to warn of critical wear, so
 *       20% is used. Presentation only — no gameplay meaning is attached to these bands.</li>
 * </ul>
 */
public final class TooltipResourceColors {

    /** Canonical UE blue — the Energy stat color used across Totality's tooltips. */
    public static final int UE_BLUE = 0xFF42C8F5;
    /** The darkest shade an almost-empty Energy bar reaches (still recognisably UE blue). */
    public static final int UE_BLUE_DEPLETED = 0xFF14506E;

    public static final int DURABILITY_GREEN = 0xFF5CC45C;
    public static final int DURABILITY_ORANGE = 0xFFE8912E;
    public static final int DURABILITY_RED = 0xFFE0453A;
    public static final float DURABILITY_ORANGE_BELOW = 0.50f;
    public static final float DURABILITY_RED_AT_OR_BELOW = 0.20f;

    /** Energy fill color for a charge fraction: full UE blue when full, progressively darker as it empties. */
    public static int energy(float fraction) {
        return ColorUtils.blend(UE_BLUE_DEPLETED, UE_BLUE, clamp01(fraction)) | 0xFF000000;
    }

    /** Durability fill color for a remaining fraction. */
    public static int durability(float fraction) {
        float f = clamp01(fraction);
        if (f <= DURABILITY_RED_AT_OR_BELOW) return DURABILITY_RED;
        if (f <= DURABILITY_ORANGE_BELOW) return DURABILITY_ORANGE;
        return DURABILITY_GREEN;
    }

    static float clamp01(float f) {
        return Float.isNaN(f) ? 0f : Math.max(0f, Math.min(1f, f));
    }

    private TooltipResourceColors() {}
}
