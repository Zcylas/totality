package zcylas.totality.client.tooltip.preview;

/**
 * Pure sizing rules for the tooltip header's large preview viewport — no Minecraft state, so they are
 * unit-tested directly. Every tunable preview dimension lives here.
 *
 * <p>The viewport has a fixed height (only reduced on very short screens) and spans the tooltip's
 * content width; it never decides the tooltip's width. The subject is scale-to-fit inside it with an
 * internal margin, keeps its proportions, is centred, and is never cropped. The fitted scale depends
 * only on the model's bounds and the viewport, never on the animation angle, so a rotating preview
 * never changes size or layout from frame to frame.
 */
public final class TooltipPreviewLayout {

    /** Baseline preview viewport height in GUI units. Single tuning point for the header preview. */
    public static final int VIEWPORT_HEIGHT = 56;
    /** Smallest preview height used on very short screens. */
    public static final int MIN_VIEWPORT_HEIGHT = 28;
    /** The preview may use at most this fraction of the available tooltip height. */
    public static final float MAX_SCREEN_FRACTION = 0.25f;
    /** Internal margin between the viewport edge and the rendered subject. */
    public static final int MARGIN = 4;
    /** GUI units per model unit for a normal 16×16 inventory item. */
    public static final float STANDARD_ITEM_SCALE = 16.0f;
    /** Upper bound for scaling small models up (3× the inventory size). */
    public static final float MAX_MODEL_SCALE = STANDARD_ITEM_SCALE * 3;
    /** Upper bound for integer sprite magnification. */
    public static final int MAX_SPRITE_SCALE = 4;
    /** One full turntable revolution, in milliseconds — deliberately slow (30°/s). */
    public static final long TURNTABLE_PERIOD_MS = 12_000L;
    /** Pitch of the standard GUI block view (vanilla's block item {@code gui} display rotation {@code [30, 225, 0]}). */
    public static final float BLOCK_VIEW_PITCH_DEGREES = 30.0f;
    /** Yaw of the standard GUI block view. */
    public static final float BLOCK_VIEW_YAW_DEGREES = 225.0f;

    /** Preview viewport height for the current screen: stable per screen size, never per frame. */
    public static int viewportHeight(int maxTooltipHeight) {
        int byScreen = (int) (maxTooltipHeight * MAX_SCREEN_FRACTION);
        return Math.max(MIN_VIEWPORT_HEIGHT, Math.min(VIEWPORT_HEIGHT, byScreen));
    }

    /**
     * Integer magnification for a flat GUI sprite. Integer steps keep pixel art crisp (the GUI item atlas
     * is rendered at 1× and sampled nearest-neighbour). Never below 1.
     */
    public static int spriteScale(int viewportW, int viewportH) {
        int avail = Math.min(viewportW, viewportH) - MARGIN * 2;
        return Math.max(1, Math.min(MAX_SPRITE_SCALE, avail / 16));
    }

    /**
     * GUI units per model unit so a subject whose bounds are {@code boundsW × boundsH} model units fits the
     * viewport (minus margins) without cropping and without distortion. Long or oversized models scale
     * down; small ones may scale up to {@link #MAX_MODEL_SCALE}. Degenerate bounds fall back to the
     * standard inventory scale.
     */
    public static float fitScale(float boundsW, float boundsH, int viewportW, int viewportH) {
        float availW = Math.max(1, viewportW - MARGIN * 2);
        float availH = Math.max(1, viewportH - MARGIN * 2);
        if (!(boundsW > 0) || !(boundsH > 0)) return Math.min(STANDARD_ITEM_SCALE, Math.min(availW, availH));
        float scale = Math.min(availW / boundsW, availH / boundsH);
        return Math.min(scale, MAX_MODEL_SCALE);
    }

    /**
     * Fit for a subject that rotates: uses the model's bounding sphere diameter on both axes, so no angle
     * of the turntable can ever push it past the viewport, and the scale stays constant while it turns.
     */
    public static float rotatingFitScale(float boundingRadius, int viewportW, int viewportH) {
        return fitScale(boundingRadius * 2, boundingRadius * 2, viewportW, viewportH);
    }

    /**
     * On-screen {@code {width, height}} of a {@code sizeX × sizeY × sizeZ} box seen in the standard GUI block
     * view (yaw about the vertical axis, then pitch toward the viewer), in the box's own units. A box is
     * symmetric about its centre, so rotated about that centre its projection is centred too.
     */
    public static float[] blockViewExtents(float sizeX, float sizeY, float sizeZ) {
        double yaw = Math.toRadians(BLOCK_VIEW_YAW_DEGREES), pitch = Math.toRadians(BLOCK_VIEW_PITCH_DEGREES);
        double maxX = 0, maxY = 0;
        for (int i = 0; i < 8; i++) {
            double x = ((i & 1) == 0 ? -0.5 : 0.5) * sizeX;
            double y = ((i & 2) == 0 ? -0.5 : 0.5) * sizeY;
            double z = ((i & 4) == 0 ? -0.5 : 0.5) * sizeZ;
            double yawedX = x * Math.cos(yaw) + z * Math.sin(yaw);
            double yawedZ = -x * Math.sin(yaw) + z * Math.cos(yaw);
            maxX = Math.max(maxX, Math.abs(yawedX));
            maxY = Math.max(maxY, Math.abs(y * Math.cos(pitch) - yawedZ * Math.sin(pitch)));
        }
        return new float[]{(float) (2 * maxX), (float) (2 * maxY)};
    }

    /** Turntable angle in degrees for the given time; 0 at the start of each revolution. */
    public static float turntableDegrees(long timeMs) {
        return (float) (Math.floorMod(timeMs, TURNTABLE_PERIOD_MS) * 360.0 / TURNTABLE_PERIOD_MS);
    }

    private TooltipPreviewLayout() {}
}
