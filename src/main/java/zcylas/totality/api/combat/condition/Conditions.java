// api/combat/condition/Conditions.java
package zcylas.totality.api.combat.condition;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

public final class Conditions {

    // ── Physical ──────────────────────────────────────────────────────────────
    public static final TotalityCondition BLEEDING    = register("bleeding",    "Bleeding",    ChatFormatting.DARK_RED,    true,  false);
    public static final TotalityCondition BURNING     = register("burning",     "Burning",     ChatFormatting.RED,         true,  false);
    public static final TotalityCondition FROZEN      = register("frozen",      "Frozen",      ChatFormatting.AQUA,        true,  true);
    public static final TotalityCondition POISONED    = register("poisoned",    "Poisoned",    ChatFormatting.DARK_GREEN,  true,  true);
    public static final TotalityCondition STUNNED     = register("stunned",     "Stunned",     ChatFormatting.YELLOW,      true,  true);
    public static final TotalityCondition ROOTED      = register("rooted",      "Rooted",      ChatFormatting.GREEN,       true,  true);
    public static final TotalityCondition SLOWED      = register("slowed",      "Slowed",      ChatFormatting.GRAY,        true,  true);

    // ── Sensory ───────────────────────────────────────────────────────────────
    public static final TotalityCondition BLINDED     = register("blinded",     "Blinded",     ChatFormatting.WHITE,       true,  true);
    public static final TotalityCondition DEAFENED    = register("deafened",    "Deafened",    ChatFormatting.GRAY,        true,  false);

    // ── Mental ────────────────────────────────────────────────────────────────
    public static final TotalityCondition FRIGHTENED  = register("frightened",  "Frightened",  ChatFormatting.GOLD,        true,  true);
    public static final TotalityCondition CHARMED     = register("charmed",     "Charmed",     ChatFormatting.LIGHT_PURPLE,true,  true);
    public static final TotalityCondition CONFUSED    = register("confused",    "Confused",    ChatFormatting.LIGHT_PURPLE,true,  true);

    // ── Magic ─────────────────────────────────────────────────────────────────
    public static final TotalityCondition SILENCED    = register("silenced",    "Silenced",    ChatFormatting.DARK_GRAY,   true,  true);
    public static final TotalityCondition CURSED      = register("cursed",      "Cursed",      ChatFormatting.DARK_PURPLE, true,  false);

    // ── Invisibility ──────────────────────────────────────────────────────────
    public static final TotalityCondition INVISIBLE         = register("invisible",         "Invisible",         ChatFormatting.WHITE,       false, false);
    public static final TotalityCondition GREATER_INVISIBLE = register("greater_invisible", "Greater Invisible", ChatFormatting.WHITE,       false, false);

    // ── Positive ──────────────────────────────────────────────────────────────
    public static final TotalityCondition REGENERATING = register("regenerating", "Regenerating", ChatFormatting.GREEN,  false, false);
    public static final TotalityCondition HASTED       = register("hasted",       "Hasted",       ChatFormatting.YELLOW, false, false);
    public static final TotalityCondition SHIELDED     = register("shielded",     "Shielded",     ChatFormatting.BLUE,   false, false);

    private static TotalityCondition register(String name, String display,
                                              ChatFormatting color, boolean debuff,
                                              boolean saveable) {
        return ConditionRegistry.register(new TotalityCondition(
                Identifier.fromNamespaceAndPath("totality", name),
                display, color, debuff, saveable
        ));
    }

    public static TotalityCondition getByName(String displayName) {
        return ConditionRegistry.getAll().stream()
                .filter(c -> c.getDisplayName().equals(displayName))
                .findFirst()
                .orElse(null);
    }
    public static void init() { /* triggers static field initialization */ }

    private Conditions() {}
}