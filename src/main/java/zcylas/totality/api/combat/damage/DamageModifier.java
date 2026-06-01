// api/combat/damage/DamageModifier.java
package zcylas.totality.api.combat.damage;

import java.util.Set;

public sealed interface DamageModifier {

    record Immunity(boolean nonMagicalOnly)   implements DamageModifier {}
    record Resistance(boolean nonMagicalOnly) implements DamageModifier {}
    record Vulnerability()                    implements DamageModifier {}
    record Custom(float multiplier)           implements DamageModifier {}

    /**
     * Returns the damage multiplier for this modifier.
     * If nonMagicalOnly is true and the attack is magical, returns 1.0 (no effect).
     */
    static float resolve(DamageModifier modifier, Set<DamageFlags> flags) {
        boolean isMagical = flags.contains(DamageFlags.MAGICAL);
        return switch (modifier) {
            case Immunity i    -> (i.nonMagicalOnly() && isMagical) ? 1.0f : 0.0f;
            case Resistance r  -> (r.nonMagicalOnly() && isMagical) ? 1.0f : 0.5f;
            case Vulnerability v -> 2.0f;
            case Custom c      -> c.multiplier();
        };
    }
}