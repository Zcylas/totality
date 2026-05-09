package zcylas.totality.api.rpg.skills.alchemy.potions;

/**
 * Tiers for fortify potions (Fortify Health, Fortify Mana, etc.)
 * All last 60 seconds (1200 ticks) in Skyrim.
 * Uses flat point values rather than percentages.
 *
 *   POTION   → "Potion of Health"    (+20 points)
 *   DRAUGHT  → "Draught of Health"   (+40 points)
 *   SOLUTION → "Solution of Health"  (+60 points)
 *   PHILTER  → "Philter of Health"   (+80 points)
 *   ELIXIR   → "Elixir of Health"    (+100 points)
 */
public enum FortifyTier implements PotionTier {

    POTION  ("Potion",    20,  1200),
    DRAUGHT ("Draught",   40,  1200),
    SOLUTION("Solution",  60,  1200),
    PHILTER ("Philter",   80,  1200),
    ELIXIR  ("Elixir",    100, 1200);

    private final String tierWord;
    private final int points;           // flat points to add to max stat
    private final int durationTicks;

    FortifyTier(String tierWord, int points, int durationTicks) {
        this.tierWord      = tierWord;
        this.points        = points;
        this.durationTicks = durationTicks;
    }

    @Override
    public String getTierWord() {
        return tierWord;
    }

    @Override
    public boolean replacesPrefix() {
        return true; // "Draught of Health" not "Potion of Draught Health"
    }

    public int getPoints() {
        return points;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getDurationSeconds() {
        return durationTicks / 20;
    }
}