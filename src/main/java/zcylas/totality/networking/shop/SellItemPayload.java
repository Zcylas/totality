package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — player sells {@code quantity} of the stack in inventory slot {@code slotIndex} to the
 *  currently active trade session's merchant. The server re-reads and revalidates the real
 *  stack at that slot; nothing about sellability or payout is trusted from the client. */
public record SellItemPayload(int slotIndex, int quantity) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SellItemPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "sell_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SellItemPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeInt(p.slotIndex()); buf.writeInt(p.quantity()); },
                    buf -> new SellItemPayload(buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
