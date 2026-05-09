package zcylas.totality.api.rpg.skills.alchemy;

/**
 * A pairing of an AlchemyEffect with the slot index (0–3) it occupies on an ingredient.
 * Slot 0 is always revealed by eating the ingredient.
 * Slots 1–3 are revealed only through successful brewing.
 */
public record AlchemyEffectInstance(AlchemyEffect effect, int slot) {

    public AlchemyEffectInstance {
        if (slot < 0 || slot > 3) {
            throw new IllegalArgumentException("Effect slot must be 0–3, got: " + slot);
        }
    }

    /** Convenience factory. */
    public static AlchemyEffectInstance of(AlchemyEffect effect, int slot) {
        return new AlchemyEffectInstance(effect, slot);
    }
}