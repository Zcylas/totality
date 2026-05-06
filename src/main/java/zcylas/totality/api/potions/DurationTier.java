package zcylas.totality.api.potions;

/**
 * Tiers for duration-based potions (Waterbreathing, Invisibility, etc.)
 * The tier word replaces "Potion" entirely — "Draught of Waterbreathing".
 *
 * Durations follow Skyrim convention:
 *   POTION  → "Potion of Waterbreathing"   (15 seconds)
 *   DRAUGHT → "Draught of Waterbreathing"  (30 seconds)
 *   PHILTER → "Philter of Waterbreathing"  (45 seconds)
 *   ELIXIR  → "Elixir of Waterbreathing"   (60 seconds)
 */
public enum DurationTier implements PotionTier {

    POTION  ("Potion",   15 * 20),   // 300 ticks
    DRAUGHT ("Draught",  30 * 20),   // 600 ticks
    SOLUTION("Solution", 45 * 20),   // 900 ticks
    PHILTER ("Philter",  60 * 20),   // 1200 ticks
    ELIXIR  ("Elixir",   75 * 20);   // 1500 ticks

    private final String tierWord;
    private final int durationTicks;

    DurationTier(String tierWord, int durationTicks) {
        this.tierWord      = tierWord;
        this.durationTicks = durationTicks;
    }

    @Override
    public String getTierWord() {
        return tierWord;
    }

    @Override
    public boolean replacesPrefix() {
        return true;
    }

    /** Duration in ticks. */
    public int getDurationTicks() {
        return durationTicks;
    }

    /** Duration in seconds, for display. */
    public int getDurationSeconds() {
        return durationTicks / 20;
    }
}