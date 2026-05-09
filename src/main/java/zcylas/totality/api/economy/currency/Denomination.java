package zcylas.totality.api.economy.currency;

/**
 * Fixed denomination hierarchy.
 * All values are stored internally as the base unit (Bronze).
 */
public enum Denomination {

    COPPER(1,      0xae5b3c, "Copper"),
    SILVER(100,    0x617174, "Silver"),
    GOLD(10000,    0xbd9838, "Gold");

    public final long baseValue;
    public final int color;
    public final String displayName;

    Denomination(long baseValue, int color, String displayName) {
        this.baseValue = baseValue;
        this.color = color;
        this.displayName = displayName;
    }

    /**
     * Converts a raw value into how many of this denomination it contains (floored).
     */
    public long amountFrom(long rawValue) {
        return rawValue / baseValue;
    }

    /**
     * Converts a count of this denomination into raw value.
     */
    public long toRawValue(long count) {
        return count * baseValue;
    }

    public static final Denomination[] VALUES = values();
}