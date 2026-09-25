package zcylas.totality.api.mining;

/** Small shared "I".."X" formatter for enchantment-level tooltip provenance labels (Impact V, Haste II, ...). */
final class RomanNumeral {

    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private RomanNumeral() {}

    static String of(int level) {
        return level >= 1 && level <= NUMERALS.length ? NUMERALS[level - 1] : String.valueOf(level);
    }
}
