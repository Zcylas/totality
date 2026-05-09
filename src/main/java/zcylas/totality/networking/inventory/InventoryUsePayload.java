package zcylas.totality.networking.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent client → server when the player uses a consumable (food/potion)
 * from the custom inventory screen.
 *
 * inventorySlot: the player inventory slot index of the item to use.
 * The server will move it to the selected hotbar slot, apply the use effect,
 * and consume it if appropriate.
 */
public record InventoryUsePayload(int inventorySlot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InventoryUsePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "inventory_use"));

    public static final StreamCodec<FriendlyByteBuf, InventoryUsePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.inventorySlot()),
                    buf -> new InventoryUsePayload(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}