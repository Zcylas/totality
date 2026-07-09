package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — player buys {@code quantity} of the shop entry at {@code index} in the currently open shop. */
public record BuyItemPayload(int index, int quantity) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyItemPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "buy_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyItemPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeInt(p.index()); buf.writeInt(p.quantity()); },
                    buf -> new BuyItemPayload(buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
