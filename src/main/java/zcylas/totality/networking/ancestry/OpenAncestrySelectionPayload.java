package zcylas.totality.networking.ancestry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent server → client to open the ancestry selection screen.
 */
public record OpenAncestrySelectionPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenAncestrySelectionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("totality", "open_ancestry_selection")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAncestrySelectionPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {}, buf -> new OpenAncestrySelectionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}