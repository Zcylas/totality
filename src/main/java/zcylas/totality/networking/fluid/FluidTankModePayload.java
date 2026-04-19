package zcylas.totality.networking.fluid;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record FluidTankModePayload(boolean insertMode) implements CustomPacketPayload {

    public static final Type<FluidTankModePayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "fluid_tank_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidTankModePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, FluidTankModePayload::insertMode,
                    FluidTankModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
