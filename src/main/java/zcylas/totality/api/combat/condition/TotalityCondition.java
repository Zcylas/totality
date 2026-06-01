// api/combat/condition/TotalityCondition.java
package zcylas.totality.api.combat.condition;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

public final class TotalityCondition {

    private final Identifier id;
    private final String displayName;
    private final ChatFormatting color;
    private final boolean debuff;        // true = bad for the target, false = neutral/buff
    private final boolean saveable;      // true = can be saved against (future CON/WIS saves)

    public TotalityCondition(Identifier id, String displayName,
                             ChatFormatting color, boolean debuff, boolean saveable) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.debuff = debuff;
        this.saveable = saveable;
    }

    public Identifier getId() { return id; }
    public String getDisplayName() { return displayName; }
    public ChatFormatting getColor() { return color; }
    public boolean isDebuff() { return debuff; }
    public boolean isSaveable() { return saveable; }
}