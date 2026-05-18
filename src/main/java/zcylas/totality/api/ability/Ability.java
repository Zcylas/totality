// zcylas/totality/api/ability/Ability.java
package zcylas.totality.api.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;

public abstract class Ability {

    public enum Type { PASSIVE, ACTIVE, CHANNELED, TOGGLE }

    private final Identifier id;
    private final String displayName;
    private final String description;
    private final Type type;
    private final int cooldownTicks;
    private final Identifier icon;

    protected Ability(Identifier id, String displayName, String description,
                      Type type, int cooldownTicks, Identifier icon) {
        this.id           = id;
        this.displayName  = displayName;
        this.description  = description;
        this.type         = type;
        this.cooldownTicks = cooldownTicks;
        this.icon = icon;
    }

    public Identifier getId()        { return id; }
    public String getDisplayName()   { return displayName; }
    public String getDescription()   { return description; }
    public Type getType()            { return type; }
    public int getCooldownTicks()    { return cooldownTicks; }
    public boolean isDefault()       { return false; }
    public Identifier getIcon()      {return  icon;}

    /**
     * Called client-side every tick to evaluate whether this ability
     * is applicable given what the crosshair is pointing at.
     *
     * Return a non-null AbilityContext to show the HUD prompt.
     * Return null to hide it.
     *
     * Default: always null (ability has no context prompt).
     */
    public @Nullable AbilityContext getContext(Minecraft mc, LocalPlayer player) {
        return AbilityContext.NONE;
    }

    /**
     * Called server-side when the player activates this ability.
     * @param context the context that was active client-side when Z was pressed.
     *                Null for abilities with no context (e.g. Short Rest).
     */
    public abstract void onActivate(ServerPlayer player, @Nullable AbilityContext context);

    /** Channeled tick — only relevant for CHANNELED type. */
    public void onChannel(ServerPlayer player, @Nullable AbilityContext context) {}

    /** Passive tick — only relevant for PASSIVE type. */
    public void onPassiveTick(ServerPlayer player) {}
}