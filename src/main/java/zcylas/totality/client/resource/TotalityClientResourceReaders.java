package zcylas.totality.client.resource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.ClientResourceReader;
import zcylas.totality.api.rpg.resources.client.ClientResourceService;
import zcylas.totality.api.rpg.resources.client.GenericSyncClientResourceReader;
import zcylas.totality.api.rpg.resources.client.NativeClientResourceReader;
import zcylas.totality.networking.resource.ClientResourceSyncBridge;

/**
 * Phase 3B-1 production wiring: registers the client Resource façade's reader strategies once, at
 * client startup, so {@link ClientResourceService#INSTANCE} is fully usable by a future HUD, menu,
 * radial, tooltip, or debug consumer without any further wiring. Registration only — no production
 * consumer queries the façade yet (see
 * {@code TOTALITY_RESOURCE_API_PHASE_3B_CLIENT_VIEW_AND_PARITY_READINESS.md}).
 */
@Environment(EnvType.CLIENT)
public final class TotalityClientResourceReaders {

    public static void register() {
        ClientResourceReader nativeReader = new NativeClientResourceReader(MinecraftNativeResourceAccess.INSTANCE);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.HEALTH, nativeReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.FOOD, nativeReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.BREATH, nativeReader);

        ClientResourceReader genericReader = new GenericSyncClientResourceReader(ClientResourceSyncBridge.INSTANCE);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.MANA, genericReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.STAMINA, genericReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.SPELL_SLOTS, genericReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.RAGE, genericReader);
    }

    private TotalityClientResourceReaders() {}
}
