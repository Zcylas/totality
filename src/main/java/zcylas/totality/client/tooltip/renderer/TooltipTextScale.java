package zcylas.totality.client.tooltip.renderer;

/**
 * Pixel-safe text scaling for Minecraft's bitmap font.
 *
 * <p>Cause of the GUI-scale-1 corruption: text drawn at a pose scale {@code s} with GUI scale {@code g} maps every
 * font texel to {@code s × g} screen pixels, sampled nearest. When that is below 1 — e.g. the compact body scale
 * 0.875 at GUI scale 1 — some texel rows and columns get no screen pixel at all and vanish ("Mining Damage"
 * loses rows, "TWO-HANDED" reads "TWO-INNDED"). At GUI 2 and above, 0.875 still gives every texel at least one
 * screen pixel, so nothing is lost.
 *
 * <p>The rule: keep the requested compact scale wherever it gives at least one screen pixel per font texel, and
 * otherwise draw at the smallest scale that does ({@code 1 / guiScale}, i.e. unscaled at GUI 1). Compactness is
 * preserved at GUI 2+, and readability wins at GUI 1.
 */
public final class TooltipTextScale {

    /** The scale to draw compact text at: {@code target}, raised just enough to keep every font texel visible. */
    public static float pixelSafe(float target, int guiScale) {
        if (guiScale <= 0) return 1f;
        return Math.max(target, 1f / guiScale);
    }

    /** Screen pixels per font texel at this scale and GUI scale. */
    public static float screenPixelsPerTexel(float scale, int guiScale) {
        return scale * guiScale;
    }

    private TooltipTextScale() {}
}
