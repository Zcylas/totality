package zcylas.totality.api.rpg.ancestry;

import net.minecraft.client.Minecraft;

/**
 * Client-side cache of the local player's ancestry.
 * Updated whenever a sync packet arrives from the server.
 */
public final class ClientAncestryManager {

    private static Species species     = null;
    private static Origin origin       = null;
    private static float heightScale   = 1.0f;

    private ClientAncestryManager() {}

    public static void apply(Species incomingSpecies, Origin incomingOrigin, float scale) {
        species     = incomingSpecies;
        origin      = incomingOrigin;
        heightScale = scale;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.refreshDimensions();
        }
    }

    public static Species getSpecies()       { return species; }
    public static Origin getOrigin()         { return origin; }
    public static float getHeightScale()     { return heightScale; }
    public static boolean hasAncestry()      { return species != null; }
}