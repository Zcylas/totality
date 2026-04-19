package zcylas.totality.networking;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import zcylas.totality.client.config.EnergyFaceConfigClientCache;
import zcylas.totality.client.config.SideModeClientCache;
import zcylas.totality.networking.config.EnergyFaceConfigSyncPayload;
import zcylas.totality.networking.config.SideModeSyncPayload;

public class TotalityClientPacketHandlers {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                SideModeSyncPayload.TYPE,
                (payload, context) -> {
                    SideModeClientCache.set(payload.pos(), payload.sideModes());
                });
    }

    private TotalityClientPacketHandlers() {}
}