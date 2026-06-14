package zcylas.totality.networking.equipment;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record OpenAccessoryInventoryPayload() implements CustomPacketPayload {

    public static final Type<OpenAccessoryInventoryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "open_accessory_inventory"));

    public static final StreamCodec<FriendlyByteBuf, OpenAccessoryInventoryPayload> CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new OpenAccessoryInventoryPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}