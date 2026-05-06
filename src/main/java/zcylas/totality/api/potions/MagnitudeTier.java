package zcylas.totality.api.potions;

/**
 * Tiers for magnitude-based potions (Restore Health, Fortify Mana, etc.)
 * Percentages apply to the target's max value (health, mana, stamina).
 *
 * Tier names follow Skyrim convention:
 *   MINOR     → "Potion of Minor Healing"       (25 points)
 *   STANDARD  → "Potion of Healing"             (50 points)
 *   PLENTIFUL → "Potion of Plentiful Healing"   (75 points)
 *   VIGOROUS  → "Potion of Vigorous Healing"    (100 points)
 *   EXTREME   → "Potion of Extreme Healing"     (150 points)
 *   ULTIMATE  → "Potion of Ultimate Healing"    (full restore)
 */
public enum MagnitudeTier implements PotionTier {

    MINOR    ("Minor",     25f),
    STANDARD ("",         50f),
    PLENTIFUL("Plentiful", 75f),
    VIGOROUS ("Vigorous",  100f),
    EXTREME  ("Extreme",   150f),
    ULTIMATE ("Ultimate",  -1f);  // -1 = full restore

    private final String tierWord;
    private final float points;

    MagnitudeTier(String tierWord, float points) {
        this.tierWord = tierWord;
        this.points   = points;
    }

    @Override
    public String getTierWord() { return tierWord; }

    @Override
    public boolean replacesPrefix() { return false; }

    /** Flat points to restore. -1 means full restore (ULTIMATE). */
    public float getPoints() { return points; }
}