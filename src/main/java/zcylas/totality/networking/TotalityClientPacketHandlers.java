package zcylas.totality.networking;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import zcylas.totality.client.config.ItemSideModeClientCache;
import zcylas.totality.client.config.SideModeClientCache;
import zcylas.totality.networking.config.ItemSideModeSyncPayload;
import zcylas.totality.networking.config.SideModeSyncPayload;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.mana.SyncManaPayload;

public class TotalityClientPacketHandlers {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                SideModeSyncPayload.TYPE,
                (payload, context) -> {
                    SideModeClientCache.set(payload.pos(), payload.sideModes());
                });

        ClientPlayNetworking.registerGlobalReceiver(
                SyncManaPayload.TYPE,
                (payload, context) -> ClientManaManager.sync(payload.mana(), payload.maxMana()
                ));
        ClientPlayNetworking.registerGlobalReceiver(
                ItemSideModeSyncPayload.TYPE,
                (payload, context) -> {
                    ItemSideModeClientCache.setAll(payload.pos(), payload.sideModes());
                });

    }


    private TotalityClientPacketHandlers() {}
}