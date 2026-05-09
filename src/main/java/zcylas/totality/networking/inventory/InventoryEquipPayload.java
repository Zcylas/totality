package zcylas.totality.networking.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import zcylas.totality.Totality;

/**
 * Sent client → server when the player equips an item from the custom inventory screen.
 *
 * inventorySlot: the player inventory slot index the item is in (-1 means already in hand)
 * targetSlot:    the EquipmentSlot to equip to (MAINHAND, OFFHAND, HEAD, CHEST, LEGS, FEET)
 * unequip:       if true, unequip from targetSlot back to inventory instead
 */
public record InventoryEquipPayload(int inventorySlot, EquipmentSlot targetSlot, boolean unequip)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InventoryEquipPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "inventory_equip"));

    public static final StreamCodec<FriendlyByteBuf, InventoryEquipPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.inventorySlot());
                        buf.writeEnum(p.targetSlot());
                        buf.writeBoolean(p.unequip());
                    },
                    buf -> new InventoryEquipPayload(
                            buf.readInt(),
                            buf.readEnum(EquipmentSlot.class),
                            buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}