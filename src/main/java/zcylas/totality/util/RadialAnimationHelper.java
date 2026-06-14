package zcylas.totality.util;

import net.minecraft.util.Mth;

/**
 * Utilities for radial menu animation, ported from Traveler's Backpack RadialToolsOverlay.
 *
 * TB uses a cubic smoothstep (ease-in-out) for open/close animation plus
 * GuiGraphics pose scale so the radial "pops" open from the center.
 *
 * Usage in a HUD renderer:
 * <pre>
 *   // Track a float openProgress in [0, 1] — advance each tick
 *   openProgress = RadialAnimationHelper.advance(openProgress, isOpen, partialTick);
 *   float scale  = RadialAnimationHelper.smoothstep(openProgress);
 *
 *   graphics.pose().pushMatrix();
 *   graphics.pose().translate(centerX, centerY);
 *   graphics.pose().scale(scale, scale);
 *   graphics.pose().translate(-centerX, -centerY);
 *   // ... draw radial ...
 *   graphics.pose().popMatrix();
 * </pre>
 */
public final class RadialAnimationHelper {

    /** Speed of open/close in progress units per tick (1.0 = fully open in 1 tick). */
    private static final float OPEN_SPEED  = 0.15f;
    private static final float CLOSE_SPEED = 0.20f;

    private RadialAnimationHelper() {}

    /**
     * Advance openProgress toward 1 (opening) or 0 (closing) each frame.
     * Call this in your renderer with {@code partialTick} from the render context.
     */
    public static float advance(float current, boolean open, float partialTick) {
        float delta = (open ? OPEN_SPEED : -CLOSE_SPEED) * partialTick;
        return Mth.clamp(current + delta, 0f, 1f);
    }

    /**
     * Cubic smoothstep: t² × (3 − 2t). Maps [0,1] → [0,1] with ease-in-out.
     * Use this as the scale factor for radial drawing.
     */
    public static float smoothstep(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Returns the segment index (0-based) that the mouse is hovering,
     * given a radial with {@code segmentCount} equal slices around a center.
     * Returns -1 if inside the dead zone.
     *
     * @param deadZoneRadius ignore input when mouse is within this many pixels of center
     */
    public static int getHoveredSegment(int centerX, int centerY,
                                        int mouseX, int mouseY,
                                        int segmentCount, int deadZoneRadius) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        if (dx * dx + dy * dy < (double) deadZoneRadius * deadZoneRadius) return -1;

        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
        if (angle < 0) angle += 360.0;

        double sliceSize = 360.0 / segmentCount;
        return (int)(angle / sliceSize) % segmentCount;
    }
}