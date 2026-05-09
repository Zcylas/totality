package zcylas.totality.api.rpg.skills.alchemy.potions;

/**
 * Common interface for all potion tier enums.
 * Implemented by MagnitudeTier and DurationTier.
 */
public interface PotionTier {

    /**
     * The word that appears in the potion name.
     * For STANDARD magnitude tier this is empty — "Potion of Healing" not "Potion of Standard Healing".
     * For duration tiers this replaces "Potion" entirely — "Draught of Waterbreathing".
     */
    String getTierWord();

    /**
     * Whether this tier's word replaces "Potion/Poison" entirely (true for DurationTier)
     * or is inserted after "Potion/Poison of" (false for MagnitudeTier).
     */
    boolean replacesPrefix();

    /**
     * Build the full display name given the effect name and whether it's a poison.
     */
    default String buildName(String effectName, boolean isPoison) {
        if (replacesPrefix()) {
            // "Draught of Waterbreathing"
            return getTierWord() + " of " + effectName;
        } else {
            String prefix = isPoison ? "Poison" : "Potion";
            String tierWord = getTierWord();
            if (tierWord.isEmpty()) {
                // STANDARD tier: "Potion of Healing"
                return prefix + " of " + effectName;
            } else {
                // "Potion of Minor Healing"
                return prefix + " of " + tierWord + " " + effectName;
            }
        }
    }
}