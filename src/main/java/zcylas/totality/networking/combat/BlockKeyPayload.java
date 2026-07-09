package zcylas.totality.networking.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record BlockKeyPayload(boolean blocking)
        implements CustomPacketPayload {

    public static final Type<BlockKeyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "block_key"));

    public static final StreamCodec<FriendlyByteBuf, BlockKeyPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.blocking()),
                    buf -> new BlockKeyPayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
