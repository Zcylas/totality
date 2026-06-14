// api/combat/damage/DamageFlags.java
package zcylas.totality.api.combat.damage;

public enum DamageFlags {
    BYPASS_RESISTANCE,
    BYPASS_ARMOR,
    IS_AOE,
    NO_KNOCKBACK,
    SILENT,
    MAGICAL,
    /** Prevents applyFromDamageType from applying conditions. Used for spell bolts
     *  (which manage their own conditions via SpellBoltOnHitRegistry) and for
     *  condition tick damage (prevents BURNING re-applying and creating a loop). */
    NO_CONDITIONS
}