package zcylas.totality.api.potions;

import java.util.List;

/**
 * Data stored on an AlchemyPotionItem stack.
 * Contains the display name, color, and list of effects to apply on drink.
 */
public record PotionData(
        String displayName,
        int color,
        List<EffectEntry> effects,
        boolean isPoison
) {
    // Standard potion colors
    public static final int COLOR_RED    = 0x8B0000; // Dark red — health
    public static final int COLOR_BLUE   = 0x1B2A8B; // Dark blue — mana
    public static final int COLOR_GREEN  = 0x1A6B2E; // Dark green — stamina
    public static final int COLOR_PURPLE = 0x4B0082; // Dark purple — poison/unknown
    public static final int COLOR_GOLD   = 0xA67C00; // Dark gold — special
    public static final int COLOR_WHITE  = 0xA0B8C0; // Muted white — neutral

    public static PotionData of(String displayName, int color,
                                List<EffectEntry> effects, boolean isPoison) {
        return new PotionData(displayName, color, effects, isPoison);
    }
}