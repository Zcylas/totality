package zcylas.totality.screen.inventory;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Represents one row in the inventory list.
 * Tracks the ItemStack, where it currently lives (slot index or special marker),
 * and — for armor — which EquipmentSlot it occupies.
 */
public final class ItemEntry {
    public final ItemStack     stack;
    public final int           slot;      // inventory slot 0-35, SLOT_ARMOR, or SLOT_OFFHAND
    public final EquipmentSlot armorSlot; // non-null only when slot == SLOT_ARMOR

    public ItemEntry(ItemStack stack, int slot) {
        this.stack     = stack;
        this.slot      = slot;
        this.armorSlot = null;
    }

    public ItemEntry(ItemStack stack, int slot, EquipmentSlot armorSlot) {
        this.stack     = stack;
        this.slot      = slot;
        this.armorSlot = armorSlot;
    }
}