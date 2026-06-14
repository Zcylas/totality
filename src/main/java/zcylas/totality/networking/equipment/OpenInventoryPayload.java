package zcylas.totality.networking.equipment;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record OpenInventoryPayload() implements CustomPacketPayload {

    public static final Type<OpenInventoryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "open_inventory"));

    public static final StreamCodec<FriendlyByteBuf, OpenInventoryPayload> CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new OpenInventoryPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
