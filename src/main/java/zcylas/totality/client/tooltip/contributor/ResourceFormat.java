package zcylas.totality.client.tooltip.contributor;

import java.math.BigInteger;

/**
 * Figures for the tooltip's resource sections (Energy, Durability). Never rounds in the flattering direction:
 * compact values are floored to one decimal ({@code 47_990 -> "47.9k"}, not "48k"), and a percentage is floored,
 * so a resource only reads "100%" when it is actually full and only "0%" when it is actually empty (a non-empty
 * remainder below 1% reads "<1%"). Pure and unit-tested.
 */
public final class ResourceFormat {

    private static final long[] UNITS = {1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L};
    private static final String[] SUFFIXES = {"T", "B", "M", "k"};

    /** Compact figure: exact below 1000, otherwise one floored decimal with k/M/B/T, trailing ".0" dropped. */
    public static String compact(long amount) {
        if (amount < 0) return "-" + compact(-amount);
        for (int i = 0; i < UNITS.length; i++) {
            if (amount >= UNITS[i]) {
                long tenths = amount / (UNITS[i] / 10);
                long whole = tenths / 10, decimal = tenths % 10;
                return (decimal == 0 ? String.valueOf(whole) : whole + "." + decimal) + SUFFIXES[i];
            }
        }
        return String.valueOf(amount);
    }

    /**
     * Floored whole percentage of {@code current / max}: "0%" only when empty, "100%" only when full. Exact integer
     * arithmetic ({@code current * 100} would overflow a {@code long} for large capacities).
     */
    public static String percent(long current, long max) {
        if (max <= 0 || current <= 0) return "0%";
        if (current >= max) return "100%";
        long pct = BigInteger.valueOf(current).multiply(BigInteger.valueOf(100)).divide(BigInteger.valueOf(max)).longValue();
        return pct == 0 ? "<1%" : pct + "%";
    }

    /** {@code current / max} clamped to 0..1, for color blending only (the bar fill uses the exact values). */
    public static float fraction(long current, long max) {
        if (max <= 0 || current <= 0) return 0f;
        if (current >= max) return 1f;
        return (float) ((double) current / max);
    }

    /** {@code "current / max unit (pct)"}, compact or exact. {@code unit} may be empty. */
    public static String figures(long current, long max, String unit, boolean exact) {
        String c = exact ? String.valueOf(current) : compact(current);
        String m = exact ? String.valueOf(max) : compact(max);
        return c + " / " + m + (unit.isEmpty() ? "" : " " + unit) + " (" + percent(current, max) + ")";
    }

    private ResourceFormat() {}
}
