package zcylas.totality.networking.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent client → server when the player drops an item from the custom inventory screen.
 *
 * inventorySlot: the player inventory slot index of the item to drop.
 * wholeStack:    if true, drop the entire stack; if false, drop one item.
 */
public record InventoryDropPayload(int inventorySlot, boolean wholeStack)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InventoryDropPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "inventory_drop"));

    public static final StreamCodec<FriendlyByteBuf, InventoryDropPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.inventorySlot());
                        buf.writeBoolean(p.wholeStack());
                    },
                    buf -> new InventoryDropPayload(buf.readInt(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}