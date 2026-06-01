package zcylas.totality.api.rpg.combat.weapon;

/**
 * D&D-style weapon category — used for class proficiency checks.
 * Separate from WeaponType which handles stamina costs.
 */
public enum WeaponCategory {
    SIMPLE_MELEE,
    MARTIAL_MELEE,
    SIMPLE_RANGED,
    MARTIAL_RANGED,
    THROWN;

    public String displayName() {
        return switch (this) {
            case SIMPLE_MELEE   -> "Simple · Melee";
            case MARTIAL_MELEE  -> "Martial · Melee";
            case SIMPLE_RANGED  -> "Simple · Ranged";
            case MARTIAL_RANGED -> "Martial · Ranged";
            case THROWN         -> "Martial · Thrown";
        };
    }
}