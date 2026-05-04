package zcylas.totality.api.potions;

import zcylas.totality.api.alchemy.AlchemyEffect;

/**
 * A single effect entry for a special potion with fully custom values.
 * Used by AlchemyPotionItem.special() for unique/named potions.
 *
 * magnitude  — how strong the effect is (percentage for restore/fortify, ticks for duration)
 * duration   — duration in ticks (0 for instant effects like Restore Health)
 */
public record EffectEntry(
        AlchemyEffect effect,
        float magnitude,
        int durationTicks
) {
    /** Instant effect — magnitude only, no duration. */
    public static EffectEntry instant(AlchemyEffect effect, float magnitude) {
        return new EffectEntry(effect, magnitude, 0);
    }

    /** Duration effect — duration only, no magnitude. */
    public static EffectEntry timed(AlchemyEffect effect, int durationTicks) {
        return new EffectEntry(effect, 0f, durationTicks);
    }

    /** Full control — both magnitude and duration. */
    public static EffectEntry of(AlchemyEffect effect, float magnitude, int durationTicks) {
        return new EffectEntry(effect, magnitude, durationTicks);
    }
}