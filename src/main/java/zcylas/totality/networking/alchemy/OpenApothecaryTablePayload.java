package zcylas.totality.networking.alchemy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenApothecaryTablePayload(BlockPos pos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenApothecaryTablePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("totality", "open_apothecary_table")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenApothecaryTablePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBlockPos(payload.pos()),
                    buf -> new OpenApothecaryTablePayload(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}