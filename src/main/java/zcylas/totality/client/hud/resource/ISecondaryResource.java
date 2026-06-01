package zcylas.totality.client.hud.resource;

import net.minecraft.client.Minecraft;

public interface ISecondaryResource {
    String getName();
    int getCurrent(Minecraft client);
    int getMax(Minecraft client);
    int getColor();
    boolean shouldShow(Minecraft client);

    /** PIPS = discrete charges (Rage, Ki). BAR = continuous (Solar Charge). */
    default DisplayType getDisplayType() { return DisplayType.PIPS; }

    enum DisplayType { PIPS, BAR }
}