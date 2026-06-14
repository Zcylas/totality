package zcylas.totality.api.combat.damage;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

public final class TotalityDamageType {

    private final Identifier id;
    private final String displayName;
    /** RGB color (no alpha) used in floating combat text and UI labels. */
    private final int combatTextColor;
    private final boolean magical;

    public TotalityDamageType(Identifier id, String displayName, int combatTextColor, boolean magical) {
        this.id               = id;
        this.displayName      = displayName;
        this.combatTextColor  = combatTextColor;
        this.magical          = magical;
    }

    public Identifier getId()           { return id; }
    public String getDisplayName()      { return displayName; }
    /** RGB, no alpha — e.g. 0xFF4400 for fire. Alpha is applied by the renderer. */
    public int getCombatTextColor()     { return combatTextColor; }
    public boolean isMagical()          { return magical; }

    /** Colored component for tooltips and chat, using the exact combat text color. */
    public Component asComponent() {
        return Component.literal(displayName)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(combatTextColor)));
    }

    @Override
    public String toString() { return id.toString(); }
}