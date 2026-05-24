package zcylas.totality.networking.ancestry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Sent client → server when the player confirms their ancestry selection.
 */
public record SelectAncestryPayload(String speciesName, @Nullable String originName)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectAncestryPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("totality", "select_ancestry")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectAncestryPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.speciesName());
                        buf.writeBoolean(payload.originName() != null);
                        if (payload.originName() != null) buf.writeUtf(payload.originName());
                    },
                    buf -> {
                        String speciesName = buf.readUtf();
                        boolean hasOrigin  = buf.readBoolean();
                        String originName  = hasOrigin ? buf.readUtf() : null;
                        return new SelectAncestryPayload(speciesName, originName);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}