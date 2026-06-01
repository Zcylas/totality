// api/combat/damage/DamageTypes.java
package zcylas.totality.api.combat.damage;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

public final class DamageTypes {

    // Physical
    public static final TotalityDamageType SLASHING    = register("slashing",    "Slashing",    ChatFormatting.GRAY,         false);
    public static final TotalityDamageType BLUDGEONING = register("bludgeoning", "Bludgeoning", ChatFormatting.WHITE,        false);
    public static final TotalityDamageType PIERCING    = register("piercing",    "Piercing",    ChatFormatting.WHITE,        false);

    // Elemental
    public static final TotalityDamageType FIRE        = register("fire",        "Fire",        ChatFormatting.RED,          true);
    public static final TotalityDamageType FROST       = register("frost",       "Frost",       ChatFormatting.AQUA,         true);
    public static final TotalityDamageType LIGHTNING   = register("lightning",   "Lightning",   ChatFormatting.YELLOW,       true);
    public static final TotalityDamageType ACID        = register("acid",        "Acid",        ChatFormatting.GREEN,        true);
    public static final TotalityDamageType POISON      = register("poison",      "Poison",      ChatFormatting.DARK_GREEN,   true);

    // Magical
    public static final TotalityDamageType FORCE       = register("force",       "Force",       ChatFormatting.BLUE,         true);
    public static final TotalityDamageType RADIANT     = register("radiant",     "Radiant",     ChatFormatting.YELLOW,       true);
    public static final TotalityDamageType NECROTIC    = register("necrotic",    "Necrotic",    ChatFormatting.DARK_PURPLE,  true);
    public static final TotalityDamageType PSYCHIC     = register("psychic",     "Psychic",     ChatFormatting.LIGHT_PURPLE, true);
    public static final TotalityDamageType ARCANE      = register("arcane",      "Arcane",      ChatFormatting.DARK_AQUA,    true);

    // Special
    public static final TotalityDamageType SONIC       = register("sonic",       "Sonic",       ChatFormatting.WHITE,        true);
    public static final TotalityDamageType VOID        = register("void",        "Void",        ChatFormatting.DARK_GRAY,    true);

    private static TotalityDamageType register(String name, String display,
                                               ChatFormatting color, boolean magical) {
        return DamageTypeRegistry.register(new TotalityDamageType(
                Identifier.fromNamespaceAndPath("totality", name),
                display, color, magical
        ));
    }
    public static void init() { /* triggers static field initialization */ }

    private DamageTypes() {}
}