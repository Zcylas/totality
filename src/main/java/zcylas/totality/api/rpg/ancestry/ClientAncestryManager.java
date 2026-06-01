// api/rpg/ancestry/ClientAncestryManager.java
package zcylas.totality.api.rpg.ancestry;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Client-side cache of the local player's ancestry.
 * Updated whenever a sync packet arrives from the server.
 */
public final class ClientAncestryManager {

    private static Identifier speciesId   = null;
    private static Identifier originId    = null;
    private static float      heightScale = 1.0f;

    private ClientAncestryManager() {}

    public static void apply(Identifier incomingSpeciesId, Identifier incomingOriginId, float scale) {
        speciesId   = incomingSpeciesId;
        originId    = incomingOriginId;
        heightScale = scale;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.refreshDimensions();
        }
    }

    public static Identifier   getSpeciesId()    { return speciesId; }
    public static Identifier   getOriginId()     { return originId; }
    public static float        getHeightScale()  { return heightScale; }
    public static boolean      hasAncestry()     { return speciesId != null; }

    public static SpeciesData  getSpeciesData()  { return speciesId != null ? SpeciesRegistry.get(speciesId) : null; }
    public static OriginData   getOriginData()   { return originId  != null ? OriginRegistry.get(originId)   : null; }
}