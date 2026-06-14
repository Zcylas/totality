package zcylas.totality.client.hud.resource;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public interface ISecondaryResource {
    String getName();
    int getCurrent(Minecraft client);
    int getMax(Minecraft client);
    int getColor();
    boolean shouldShow(Minecraft client);

    /** PIPS = discrete charges (Rage, Ki). BAR = continuous (Solar Charge). */
    default DisplayType getDisplayType() { return DisplayType.PIPS; }

    /** Sprite for a filled/active pip. If null, falls back to colored fill. */
    @Nullable
    default Identifier getActivePipSprite() { return null; }

    /** Sprite for a spent/empty pip. If null, falls back to dimmed outline. */
    @Nullable
    default Identifier getSpentPipSprite() { return null; }

    enum DisplayType { PIPS, BAR }
}