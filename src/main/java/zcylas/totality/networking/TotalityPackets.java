package zcylas.totality.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import zcylas.totality.api.component.ComponentSync;
import zcylas.totality.networking.alchemy.BrewPayload;
import zcylas.totality.networking.alchemy.BrewResultPayload;
import zcylas.totality.networking.alchemy.OpenApothecaryTablePayload;
import zcylas.totality.networking.config.ItemSideModePayload;
import zcylas.totality.networking.config.ItemSideModeSyncPayload;
import zcylas.totality.networking.config.SideModePayload;
import zcylas.totality.networking.config.SideModeSyncPayload;
import zcylas.totality.networking.fluid.FluidTankModePayload;
import zcylas.totality.networking.magic.grimoire.SwitchGrimoireSlotPayload;
import zcylas.totality.networking.magic.grimoire.UpdateGrimoirePayload;
import zcylas.totality.networking.mana.SyncManaPayload;
import zcylas.totality.networking.notification.SendNotificationPayload;
import zcylas.totality.networking.stamina.SyncStaminaPayload;

public class TotalityPackets {

    public static void register() {
        serverbound(PayloadTypeRegistry.serverboundPlay());
        clientbound(PayloadTypeRegistry.clientboundPlay());
    }

    private static void serverbound(PayloadTypeRegistry<RegistryFriendlyByteBuf> registry) {
        registry.register(FluidTankModePayload.ID, FluidTankModePayload.CODEC);
        registry.register(SideModePayload.TYPE, SideModePayload.CODEC);
        registry.register(UpdateGrimoirePayload.TYPE, UpdateGrimoirePayload.CODEC);
        registry.register(SwitchGrimoireSlotPayload.TYPE, SwitchGrimoireSlotPayload.STREAM_CODEC);
        registry.register(ItemSideModePayload.TYPE, ItemSideModePayload.CODEC);
        registry.register(BrewPayload.TYPE, BrewPayload.STREAM_CODEC);
    }

    private static void clientbound(PayloadTypeRegistry<RegistryFriendlyByteBuf> registry){
        registry.register(SideModeSyncPayload.TYPE, SideModeSyncPayload.CODEC);
        registry.register(SyncStaminaPayload.TYPE, SyncStaminaPayload.CODEC);
        registry.register(SyncManaPayload.TYPE, SyncManaPayload.CODEC);
        registry.register(ItemSideModeSyncPayload.TYPE, ItemSideModeSyncPayload.CODEC);
        registry.register(ComponentSync.PACKET_TYPE, ComponentSync.STREAM_CODEC);
        registry.register(OpenApothecaryTablePayload.TYPE, OpenApothecaryTablePayload.STREAM_CODEC);
        registry.register(BrewResultPayload.TYPE, BrewResultPayload.STREAM_CODEC);
        registry.register(SendNotificationPayload.TYPE, SendNotificationPayload.CODEC);
    }

    private TotalityPackets() {}

}
