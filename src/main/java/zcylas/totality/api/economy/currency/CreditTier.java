package zcylas.totality.api.economy.currency;

/**
 * Visual/display tier for a {@code totality:credits} stack, keyed by amount.
 * Mirrors the design spec's five tiers (Credit Chip through Encrypted Credit Card).
 */
public enum CreditTier {
    CHIP(1, 9, "Credit Chip"),
    STACK(10, 99, "Credit Stack"),
    PILE(100, 999, "Credit Pile"),
    CARD(1000, 9999, "Credit Card"),
    ENCRYPTED_CARD(10000, Long.MAX_VALUE, "Encrypted Credit Card");

    public final long min;
    public final long max;
    public final String displayName;

    CreditTier(long min, long max, String displayName) {
        this.min = min;
        this.max = max;
        this.displayName = displayName;
    }

    public static CreditTier forAmount(long amount) {
        if (amount < CHIP.min) return CHIP;
        for (CreditTier tier : values()) {
            if (amount >= tier.min && amount <= tier.max) return tier;
        }
        return ENCRYPTED_CARD;
    }
}
