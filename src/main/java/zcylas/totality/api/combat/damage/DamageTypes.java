// api/combat/damage/DamageTypes.java
package zcylas.totality.api.combat.damage;

import net.minecraft.resources.Identifier;

/**
 * All damage types in Totality.
 * Colors are BG3-inspired — each type has a distinct, recognisable hue
 * used in floating combat text and UI labels.
 */
public final class DamageTypes {

    // ── Physical ──────────────────────────────────────────────────────────────
    public static final TotalityDamageType SLASHING    = register("slashing",    "Slashing",    0xC0C0C0, false);
    public static final TotalityDamageType BLUDGEONING = register("bludgeoning", "Bludgeoning", 0xC0C0C0, false);
    public static final TotalityDamageType PIERCING    = register("piercing",    "Piercing",    0xC0C0C0, false);

    // ── Elemental ─────────────────────────────────────────────────────────────
    public static final TotalityDamageType FIRE        = register("fire",        "Fire",        0xFF4400, true);
    public static final TotalityDamageType FROST       = register("frost",       "Frost",       0x77CCFF, true);  // BG3 "Cold"
    public static final TotalityDamageType LIGHTNING   = register("lightning",   "Lightning",   0x4499FF, true);
    public static final TotalityDamageType ACID        = register("acid",        "Acid",        0x55CC44, true);
    public static final TotalityDamageType POISON      = register("poison",      "Poison",      0xAA44CC, true);
    public static final TotalityDamageType THUNDER     = register("thunder",     "Thunder",     0xAA66DD, true);

    // ── Magical ───────────────────────────────────────────────────────────────
    public static final TotalityDamageType FORCE       = register("force",       "Force",       0xCC3344, true);
    public static final TotalityDamageType RADIANT     = register("radiant",     "Radiant",     0xFFAA33, true);
    public static final TotalityDamageType NECROTIC    = register("necrotic",    "Necrotic",    0x778833, true);
    public static final TotalityDamageType PSYCHIC     = register("psychic",     "Psychic",     0xFF55AA, true);
    public static final TotalityDamageType ARCANE      = register("arcane",      "Arcane",      0x22CCCC, true);

    // ── Special ───────────────────────────────────────────────────────────────
    public static final TotalityDamageType SONIC       = register("sonic",       "Sonic",       0xDDEEFF, true);
    public static final TotalityDamageType VOID        = register("void",        "Void",        0x553377, true);

    private static TotalityDamageType register(String name, String display,
                                               int combatTextColor, boolean magical) {
        return DamageTypeRegistry.register(new TotalityDamageType(
                Identifier.fromNamespaceAndPath("totality", name),
                display, combatTextColor, magical
        ));
    }

    public static void init() { /* triggers static field initialization */ }

    private DamageTypes() {}
}