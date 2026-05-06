package zcylas.totality.api.potions;

/**
 * Tiers for regeneration potions (Regenerate Health, Regenerate Mana, etc.)
 * All last 300 seconds (6000 ticks) in Skyrim.
 * The tier word replaces "Potion" entirely like DurationTier.
 *
 *   POTION   → "Potion of Regeneration"    (50% faster)
 *   DRAUGHT  → "Draught of Regeneration"   (60% faster)
 *   SOLUTION → "Solution of Regeneration"  (70% faster)
 *   PHILTER  → "Philter of Regeneration"   (80% faster)
 *   ELIXIR   → "Elixir of Regeneration"    (100% faster)
 */
public enum RegenerateTier implements PotionTier {

    POTION  ("Potion",    0.50f, 6000),
    DRAUGHT ("Draught",   0.60f, 6000),
    SOLUTION("Solution",  0.70f, 6000),
    PHILTER ("Philter",   0.80f, 6000),
    ELIXIR  ("Elixir",    1.00f, 6000);

    private final String tierWord;
    private final float regenBoost;     // fraction to add to regen rate
    private final int durationTicks;

    RegenerateTier(String tierWord, float regenBoost, int durationTicks) {
        this.tierWord      = tierWord;
        this.regenBoost    = regenBoost;
        this.durationTicks = durationTicks;
    }

    @Override
    public String getTierWord() {
        return tierWord;
    }

    @Override
    public boolean replacesPrefix() {
        return true; // "Draught of Regeneration" not "Potion of Draught Regeneration"
    }

    public float getRegenBoost() {
        return regenBoost;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getDurationSeconds() {
        return durationTicks / 20;
    }
}