package zcylas.totality.client.renderer.hud;

import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Pure, deterministic "current / max" value formatter for Totality's main HUD bars, used to embed
 * the value text inside each bar's fill lane (the "contained HUD cleanup" pass — see
 * {@code TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md}). Takes {@code long} values
 * throughout — Resource presentation values are never narrowed to {@code int} merely to format
 * them for the HUD.
 *
 * <p>No Minecraft/rendering dependency: the caller supplies a {@code widthFn} (in production,
 * {@code Font::width}) so this class stays a pure, directly-unit-testable helper rather than one
 * that needs a bootstrapped client to test, matching this codebase's established pattern (compare
 * {@code NotificationManager.effectiveWidth}).
 */
final class HudValueFormatter {

    private HudValueFormatter() {}

    /**
     * Chooses the widest of the two candidate forms — {@code "cur / max"} (preferred) or
     * {@code "cur/max"} (compact fallback) — that fits within {@code maxWidth} as measured by
     * {@code widthFn}. Never truncates or drops digits from either number; the compact form only
     * removes the surrounding spaces. If even the compact form does not fit, the compact form is
     * still returned (never truncated into a misleading value).
     */
    static String display(long current, long max, int maxWidth, ToIntFunction<String> widthFn) {
        String c = abbreviate(current);
        String m = abbreviate(max);
        String spaced = c + " / " + m;
        if (widthFn.applyAsInt(spaced) <= maxWidth) {
            return spaced;
        }
        return c + "/" + m;
    }

    /** Ascending suffix tiers: index 0 = thousands, 1 = millions, 2 = billions. */
    private static final String[] SUFFIXES = {"k", "M", "B"};

    /**
     * Abbreviates a single non-negative value for compact HUD display: {@code 999 -> "999"},
     * {@code 1000 -> "1k"}, {@code 1500 -> "1.5k"}, {@code 10000 -> "10k"}, extending the same
     * one-decimal-trimmed-to-integer pattern to millions ({@code "M"}) and billions ({@code "B"})
     * for safety against arbitrarily large future values — never narrows {@code value} itself.
     *
     * <p><b>Rollover correction:</b> rounding to one decimal at a given tier can itself reach the
     * next tier's threshold — e.g. {@code 999_950} naively rounds to {@code "1000.0k"}. Whenever
     * the rounded result would read {@code >= 1000} at a tier that has a next tier available, this
     * method promotes to that next tier instead (so {@code 999_950 -> "1M"}, {@code 999_950_000 ->
     * "1B"}) rather than ever emitting a misleading {@code "1000k"}/{@code "1000M"}-shaped string.
     * The top tier ({@code "B"}) has no further promotion target, matching the suffix support this
     * formatter documents (through billions only).
     *
     * @throws IllegalArgumentException if {@code value} is negative — this formatter is defined
     *         only for non-negative Resource presentation values.
     */
    static String abbreviate(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative, was " + value);
        }
        if (value < 1_000L) {
            return Long.toString(value);
        }

        long divisor = 1_000L;
        int tier = 0;
        while (true) {
            double rounded = roundToOneDecimal(value / (double) divisor);
            boolean canPromote = tier < SUFFIXES.length - 1;
            if (rounded >= 1000.0 && canPromote) {
                divisor *= 1_000L;
                tier++;
                continue;
            }
            return trimTrailingZero(rounded) + SUFFIXES[tier];
        }
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** {@code scaled} is expected to already be rounded to one decimal (see {@link #roundToOneDecimal}) — formatting it again here is a deterministic no-op, just producing the trimmed string form. */
    private static String trimTrailingZero(double scaled) {
        String formatted = String.format(Locale.ROOT, "%.1f", scaled);
        if (formatted.endsWith(".0")) {
            formatted = formatted.substring(0, formatted.length() - 2);
        }
        return formatted;
    }
}
