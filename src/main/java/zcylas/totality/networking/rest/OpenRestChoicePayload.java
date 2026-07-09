package zcylas.totality.networking.rest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;

/** S2C — tells the client to open the Short/Long Rest choice popup. Shared by the Rest ability and the bed trigger. */
public record OpenRestChoicePayload(@Nullable BlockPos bedPos, int shortRestRemaining) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenRestChoicePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "open_rest_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRestChoicePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.bedPos() != null);
                        if (p.bedPos() != null) buf.writeBlockPos(p.bedPos());
                        buf.writeVarInt(p.shortRestRemaining());
                    },
                    buf -> new OpenRestChoicePayload(
                            buf.readBoolean() ? buf.readBlockPos() : null,
                            buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
