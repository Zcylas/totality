package zcylas.totality.networking.classes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenClassSelectionPayload() implements CustomPacketPayload {

    public static final Type<OpenClassSelectionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totality", "open_class_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenClassSelectionPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new OpenClassSelectionPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}