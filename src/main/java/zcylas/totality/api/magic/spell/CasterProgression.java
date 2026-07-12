package zcylas.totality.api.magic.spell;

/**
 * How a class contributes to the shared spell slot pool for multiclassing (D&D 5e rules).
 * FULL = 1 level, HALF = ½ level rounded down, THIRD = ⅓ level rounded down — all three pool
 * together and resolve via {@link SpellSlotTable#combinedCasterLevel}.
 *
 * WARLOCK is deliberately excluded from that pool — Pact Magic is its own separate slot bank
 * (see {@link SpellSlotTable#forWarlock}) and isn't modeled by {@link SpellSlotComponent} yet.
 */
public enum CasterProgression {
    FULL,
    HALF,
    THIRD,
    WARLOCK
}
