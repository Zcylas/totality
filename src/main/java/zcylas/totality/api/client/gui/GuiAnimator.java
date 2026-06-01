// api/client/gui/GuiAnimator.java
package zcylas.totality.api.client.gui;

import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;

/**
 * Time-based animation utility for GUI elements.
 * Adapted from ImproperUI's Animator/PollingAnimator.
 *
 * Usage — fade in/out:
 *   GuiAnimator anim = new GuiAnimator(200); // 200ms
 *   anim.setReversed(true);                  // start hidden
 *
 *   // on hover:
 *   anim.setReversed(false);
 *   anim.reset();
 *
 *   // in render:
 *   int color = GuiAnimator.applyAlpha(0xFF4400FF, anim.getProgressClamped());
 *
 * Usage — polling (auto-animates based on condition):
 *   GuiAnimator anim = GuiAnimator.polling(200, () -> isHovered);
 *   // progress automatically follows isHovered
 */
public class GuiAnimator {

    private long   start;
    private long   length;
    private boolean reversed;

    public GuiAnimator(long lengthMs) {
        this.start    = System.currentTimeMillis();
        this.length   = lengthMs;
        this.reversed = false;
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    public double getProgress() {
        double ratio = (System.currentTimeMillis() - start) / (double) length;
        return reversed ? 1.0 - ratio : ratio;
    }

    public double getProgressClamped() {
        return Mth.clamp(getProgress(), 0.0, 1.0);
    }

    public float getProgressClampedF() {
        return (float) getProgressClamped();
    }

    public boolean isFinished() {
        double p = getProgress();
        return reversed ? p <= 0.0 : p >= 1.0;
    }

    // ── Control ───────────────────────────────────────────────────────────────

    public void reset() {
        this.start = System.currentTimeMillis();
    }

    public void reset(long lengthMs) {
        this.start  = System.currentTimeMillis();
        this.length = lengthMs;
    }

    public void reverse() {
        this.reversed = !this.reversed;
    }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    public boolean isReversed() {
        return reversed;
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    /** Apply alpha based on animator progress to an RGB color. */
    public static int applyAlpha(int rgb, double progress) {
        int alpha = (int)(0xFF * Mth.clamp(progress, 0.0, 1.0));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    /** Lerp between two ARGB colors using animator progress. */
    public static int lerpColor(int colorA, int colorB, double progress) {
        float t  = (float) Mth.clamp(progress, 0.0, 1.0);
        int  aA  = (colorA >> 24) & 0xFF, rA = (colorA >> 16) & 0xFF,
                gA  = (colorA >> 8)  & 0xFF, bA = colorA & 0xFF;
        int  aB  = (colorB >> 24) & 0xFF, rB = (colorB >> 16) & 0xFF,
                gB  = (colorB >> 8)  & 0xFF, bB = colorB & 0xFF;
        int  a   = (int)(aA + (aB - aA) * t);
        int  r   = (int)(rA + (rB - rA) * t);
        int  g   = (int)(gA + (gB - gA) * t);
        int  b   = (int)(bA + (bB - bA) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates an animator that automatically follows a boolean condition.
     * When condition becomes true, animates forward. When false, animates backward.
     */
    public static Polling polling(long lengthMs, BooleanSupplier condition) {
        return new Polling(lengthMs, condition);
    }

    // ── Polling subclass ──────────────────────────────────────────────────────

    public static final class Polling extends GuiAnimator {

        private final BooleanSupplier condition;
        private boolean lastState;

        public Polling(long lengthMs, BooleanSupplier condition) {
            super(lengthMs);
            this.condition = condition;
            this.lastState = condition.getAsBoolean();
            this.setReversed(!lastState);
        }

        @Override
        public double getProgress() {
            tick();
            return super.getProgress();
        }

        public void tick() {
            boolean current = condition.getAsBoolean();
            if (current != lastState) {
                lastState = current;
                setReversed(!current);
                reset();
            }
        }
    }
}