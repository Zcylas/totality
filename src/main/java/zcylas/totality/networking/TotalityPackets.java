package zcylas.totality.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import zcylas.totality.networking.config.SideModePayload;
import zcylas.totality.networking.config.SideModeSyncPayload;
import zcylas.totality.networking.fluid.FluidTankModePayload;

public class TotalityPackets {

    public static void register() {
        serverbound(PayloadTypeRegistry.serverboundPlay());
        clientbound(PayloadTypeRegistry.clientboundPlay());
    }

    private static void serverbound(PayloadTypeRegistry<RegistryFriendlyByteBuf> registry) {
        registry.register(FluidTankModePayload.ID, FluidTankModePayload.CODEC);
        registry.register(SideModePayload.TYPE, SideModePayload.CODEC);
    }

    private static void clientbound(PayloadTypeRegistry<RegistryFriendlyByteBuf> registry){
        registry.register(SideModeSyncPayload.TYPE, SideModeSyncPayload.CODEC);
    }

    private TotalityPackets() {}

}
