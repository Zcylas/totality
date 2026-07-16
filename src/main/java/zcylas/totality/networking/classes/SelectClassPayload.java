package zcylas.totality.networking.classes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record SelectClassPayload(
        String classId,
        @Nullable String subclassId,
        @Nullable String covenantId
) implements CustomPacketPayload {

    public static final Type<SelectClassPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totality", "select_class"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectClassPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.classId());
                        buf.writeBoolean(p.subclassId() != null);
                        if (p.subclassId() != null) buf.writeUtf(p.subclassId());
                        buf.writeBoolean(p.covenantId() != null);
                        if (p.covenantId() != null) buf.writeUtf(p.covenantId());
                    },
                    buf -> new SelectClassPayload(
                            buf.readUtf(),
                            buf.readBoolean() ? buf.readUtf() : null,
                            buf.readBoolean() ? buf.readUtf() : null
                    )
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}