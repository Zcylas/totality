package zcylas.totality.networking.rest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — player chose to stop an in-progress Short/Long Rest early. */
public record CancelRestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CancelRestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "cancel_rest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelRestPayload> STREAM_CODEC =
            StreamCodec.unit(new CancelRestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
