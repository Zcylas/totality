package zcylas.totality.api.potions;

/**
 * Tiers for magnitude-based potions (Restore Health, Fortify Mana, etc.)
 * Percentages apply to the target's max value (health, mana, stamina).
 *
 * Tier names follow Skyrim convention:
 *   MINOR     → "Potion of Minor Healing"       (15%)
 *   STANDARD  → "Potion of Healing"             (25%)
 *   PLENTIFUL → "Potion of Plentiful Healing"   (40%)
 *   VIGOROUS  → "Potion of Vigorous Healing"    (55%)
 *   EXTREME   → "Potion of Extreme Healing"     (75%)
 *   ULTIMATE  → "Potion of Ultimate Healing"    (100%)
 */
public enum MagnitudeTier implements PotionTier {

    MINOR    ("Minor",     0.15f),
    STANDARD ("",         0.25f),  // No tier word — "Potion of Healing"
    PLENTIFUL("Plentiful", 0.40f),
    VIGOROUS ("Vigorous",  0.55f),
    EXTREME  ("Extreme",   0.75f),
    ULTIMATE ("Ultimate",  1.00f);

    private final String tierWord;
    private final float percentage;

    MagnitudeTier(String tierWord, float percentage) {
        this.tierWord   = tierWord;
        this.percentage = percentage;
    }

    @Override
    public String getTierWord() {
        return tierWord;
    }

    @Override
    public boolean replacesPrefix() {
        return false;
    }

    /** Percentage of max value to restore/apply (0.0 – 1.0). */
    public float getPercentage() {
        return percentage;
    }
}