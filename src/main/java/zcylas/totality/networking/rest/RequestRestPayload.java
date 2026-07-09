package zcylas.totality.networking.rest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.api.rpg.rest.ShortRestActivity;
import zcylas.totality.api.rpg.rest.ShortRestLength;

/** C2S — player confirmed a rest choice in the popup (from either the Rest ability or a bed). */
public record RequestRestPayload(RestType restType, @Nullable ShortRestLength length, @Nullable BlockPos bedPos,
                                  @Nullable ShortRestActivity activity)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestRestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "request_rest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeEnum(p.restType());
                        buf.writeBoolean(p.length() != null);
                        if (p.length() != null) buf.writeEnum(p.length());
                        buf.writeBoolean(p.bedPos() != null);
                        if (p.bedPos() != null) buf.writeBlockPos(p.bedPos());
                        buf.writeBoolean(p.activity() != null);
                        if (p.activity() != null) buf.writeEnum(p.activity());
                    },
                    buf -> new RequestRestPayload(
                            buf.readEnum(RestType.class),
                            buf.readBoolean() ? buf.readEnum(ShortRestLength.class) : null,
                            buf.readBoolean() ? buf.readBlockPos() : null,
                            buf.readBoolean() ? buf.readEnum(ShortRestActivity.class) : null)
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
