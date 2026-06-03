package zcylas.totality.util.math;

import net.minecraft.util.Mth;

/**
 * Math utilities that vanilla {@link Mth} doesn't cover.
 *
 * <ul>
 *   <li>Epsilon-safe floating-point comparisons ({@link #equals}, {@link #within}).</li>
 *   <li>Minecraft tick ↔ millisecond conversion ({@link #tickToMs}, {@link #msToTick}).</li>
 *   <li>Three-argument {@code min}/{@code max} for common primitive types.</li>
 * </ul>
 *
 * Ported from CreativeCore {@code Maths} (team.creative.creativecore).
 */
public final class TotalityMaths {

    private TotalityMaths() {}

    // -------------------------------------------------------------------------
    // Epsilon constants
    // -------------------------------------------------------------------------

    public static final float  EPSILON              = 0.001f;
    public static final float  EPSILON_UP           = 1f / EPSILON;
    public static final double EPSILON_D            = 0.001;
    public static final double EPSILON_D_UP         = 1.0 / EPSILON_D;

    public static final float  EPSILON_PRECISE      = 0.00001f;
    public static final float  EPSILON_PRECISE_UP   = 1f / EPSILON_PRECISE;
    public static final double EPSILON_PRECISE_D    = 0.00001;
    public static final double EPSILON_PRECISE_D_UP = 1.0 / EPSILON_PRECISE_D;

    // -------------------------------------------------------------------------
    // Epsilon-safe comparisons (double)
    // -------------------------------------------------------------------------

    public static boolean equals(double a, double b) {
        return a == b || Math.abs(a - b) < EPSILON_D;
    }

    public static boolean equals(double a, double b, double epsilon) {
        return a == b || Math.abs(a - b) < epsilon;
    }

    public static boolean smallerThanOrEquals(double a, double b) {
        return a < b || equals(a, b);
    }

    public static boolean smallerThanOrEquals(double a, double b, double epsilon) {
        return a < b || equals(a, b, epsilon);
    }

    public static boolean greaterThanOrEquals(double a, double b) {
        return a > b || equals(a, b);
    }

    public static boolean greaterThanOrEquals(double a, double b, double epsilon) {
        return a > b || equals(a, b, epsilon);
    }

    /** Returns true if {@code value} is within [min, max] with epsilon tolerance. */
    public static boolean within(double value, double min, double max) {
        return greaterThanOrEquals(value, min) && smallerThanOrEquals(value, max);
    }

    public static boolean within(double value, double min, double max, double epsilon) {
        return greaterThanOrEquals(value, min, epsilon) && smallerThanOrEquals(value, max, epsilon);
    }

    // -------------------------------------------------------------------------
    // Epsilon-safe comparisons (float)
    // -------------------------------------------------------------------------

    public static boolean equals(float a, float b) {
        return a == b || Math.abs(a - b) < EPSILON;
    }

    public static boolean equals(float a, float b, float epsilon) {
        return a == b || Math.abs(a - b) < epsilon;
    }

    // -------------------------------------------------------------------------
    // Tick / millisecond conversion
    // 1 tick = 50 ms  (20 ticks/second)
    // -------------------------------------------------------------------------

    /** Converts Minecraft ticks to milliseconds (1 tick = 50 ms). */
    public static long tickToMs(int ticks) {
        return ticks * 50L;
    }

    /** Converts milliseconds to the nearest whole Minecraft tick count. */
    public static int msToTick(long ms) {
        return (int) (ms / 50L);
    }

    // -------------------------------------------------------------------------
    // Rounding helpers
    // -------------------------------------------------------------------------

    /** Rounds a double to {@link #EPSILON_PRECISE_D} precision. */
    public static double round(double value) {
        return Mth.floor(EPSILON_PRECISE_D_UP * value + 0.5) * EPSILON_PRECISE_D;
    }

    /** Rounds a float to {@link #EPSILON_PRECISE} precision. */
    public static float round(float value) {
        return Mth.floor(EPSILON_PRECISE_UP * value + 0.5f) * EPSILON_PRECISE;
    }

    // -------------------------------------------------------------------------
    // Safe arithmetic
    // -------------------------------------------------------------------------

    /** Divides v1 by v2, returning 0 if either operand is 0. */
    public static double safeDivide(double v1, double v2) {
        if (v1 == 0 || v2 == 0) return 0;
        return v1 / v2;
    }

    /** Returns {@code Math.round(value * 100) / 100f}, or 0 if value is 0. */
    public static float safeRound(double value) {
        return value != 0 ? Math.round(value * 100f) / 100f : 0;
    }

    // -------------------------------------------------------------------------
    // Three-argument min/max
    // -------------------------------------------------------------------------

    public static int min(int a, int b, int c) {
        return a <= b ? (a <= c ? a : c) : (b <= c ? b : c);
    }

    public static float min(float a, float b, float c) {
        return a <= b ? (a <= c ? a : c) : (b <= c ? b : c);
    }

    public static double min(double a, double b, double c) {
        return a <= b ? (a <= c ? a : c) : (b <= c ? b : c);
    }

    public static long min(long a, long b, long c) {
        return a <= b ? (a <= c ? a : c) : (b <= c ? b : c);
    }

    public static int max(int a, int b, int c) {
        return a >= b ? (a >= c ? a : c) : (b >= c ? b : c);
    }

    public static float max(float a, float b, float c) {
        return a >= b ? (a >= c ? a : c) : (b >= c ? b : c);
    }

    public static double max(double a, double b, double c) {
        return a >= b ? (a >= c ? a : c) : (b >= c ? b : c);
    }

    public static long max(long a, long b, long c) {
        return a >= b ? (a >= c ? a : c) : (b >= c ? b : c);
    }
}
