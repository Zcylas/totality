package zcylas.totality.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.energy.HasSidedEnergy;
import zcylas.totality.item.fluid.FluidTankItem;
import zcylas.totality.networking.config.SideModePayload;
import zcylas.totality.networking.fluid.FluidTankModePayload;

public class TotalityServerPacketHandlers {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                FluidTankModePayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    ItemStack held = context.player().getMainHandItem();
                    if (held.getItem() instanceof FluidTankItem) {
                        FluidTankItem.setInsertMode(held, payload.insertMode());
                    }
                }));
        ServerPlayNetworking.registerGlobalReceiver(
                SideModePayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var level = context.player().level();
                    var be = level.getBlockEntity(payload.pos());
                    if (be instanceof HasSidedEnergy sided) {
                        sided.getEnergy().cycleSideMode(payload.face());
                        be.setChanged();
                        sided.syncSideModes(context.player(), payload.pos());
                    }
                }));
    }


    private TotalityServerPacketHandlers() {}

}
