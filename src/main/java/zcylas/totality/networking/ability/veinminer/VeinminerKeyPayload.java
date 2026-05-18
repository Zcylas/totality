package zcylas.totality.networking.ability.veinminer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record VeinminerKeyPayload(boolean held)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VeinminerKeyPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "veinminer_key"));

    public static final StreamCodec<FriendlyByteBuf, VeinminerKeyPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.held()),
                    buf -> new VeinminerKeyPayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}