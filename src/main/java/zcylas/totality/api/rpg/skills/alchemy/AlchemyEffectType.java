package zcylas.totality.api.rpg.skills.alchemy;

import net.minecraft.ChatFormatting;

/**
 * The type of an alchemy effect, determining tooltip color and potion/poison classification.
 */
public enum AlchemyEffectType {
    /** Beneficial effects — shown in green. E.g. Restore Health, Fortify Health. */
    BENEFICIAL(ChatFormatting.GREEN),
    /** Harmful effects — shown in red. E.g. Damage Health, Lingering Damage Magicka. */
    HARMFUL(ChatFormatting.RED),
    /** Neutral effects — shown in white. E.g. Waterbreathing, Invisibility. */
    NEUTRAL(ChatFormatting.WHITE);

    public final ChatFormatting color;

    AlchemyEffectType(ChatFormatting color) {
        this.color = color;
    }
}