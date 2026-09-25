package zcylas.totality.client.tooltip.contributor;

/**
 * Shared number formatting for mining tooltip rows (Block Breaking V2 §9): whole numbers display
 * without a trailing ".0", decimals are trimmed to avoid noise like "2.400000".
 */
final class MiningStatFormat {

    private MiningStatFormat() {}

    static String integer(float v) {
        return String.valueOf(Math.round(v));
    }

    /** At most 2 meaningful decimals: 2.25 -> "2.25", 2.40 -> "2.4", 10.00 -> "10.0". */
    static String decimal(float v) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
