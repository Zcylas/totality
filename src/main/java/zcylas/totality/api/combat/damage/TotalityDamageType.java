// api/combat/damage/TotalityDamageType.java
package zcylas.totality.api.combat.damage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class TotalityDamageType {

    private final Identifier id;
    private final String displayName;
    private final ChatFormatting color;
    private final boolean magical;

    public TotalityDamageType(Identifier id, String displayName, ChatFormatting color, boolean magical) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.magical = magical;
    }

    public Identifier getId() { return id; }
    public String getDisplayName() { return displayName; }
    public ChatFormatting getColor() { return color; }
    public boolean isMagical() { return magical; }

    public Component asComponent() {
        return Component.literal(displayName).withStyle(color);
    }

    @Override
    public String toString() { return id.toString(); }
}