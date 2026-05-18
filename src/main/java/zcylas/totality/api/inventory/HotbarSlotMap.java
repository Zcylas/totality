package zcylas.totality.api.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.init.ModTags;

/**
 * Defines the preferred hotbar slot for each item category.
 *
 *   0 — Special   ModTags.SPECIAL
 *   1 — Pickaxes  ItemTags.PICKAXES
 *   2 — One-handed weapons
 *   3 — Two-handed / Bows / Crossbows
 *   4 — Other tools
 *   5,6 — Free
 *   7 — Food
 *   8 — Potions
 */
public final class HotbarSlotMap {

    public static final int SLOT_SPECIAL    = 0;
    public static final int SLOT_PICKAXE    = 1;
    public static final int SLOT_ONE_HANDED = 2;
    public static final int SLOT_TWO_HANDED = 3;
    public static final int SLOT_TOOL       = 4;
    public static final int SLOT_FOOD       = 7;
    public static final int SLOT_POTION     = 8;

    /**
     * Returns the preferred hotbar slot for this item. -1 = no preference.
     */
    public static int preferredSlot(ItemStack stack) {
        if (stack.is(ModTags.SPECIAL))            return SLOT_SPECIAL;
        if (stack.is(ItemTags.PICKAXES))          return SLOT_PICKAXE;
        if (stack.is(ModTags.ONE_HANDED_WEAPONS)) return SLOT_ONE_HANDED;
        if (stack.is(ModTags.TWO_HANDED_WEAPONS)
                || stack.is(ModTags.BOWS)
                || stack.is(ModTags.CROSSBOWS))   return SLOT_TWO_HANDED;
        if (stack.is(ModTags.TOOLS))              return SLOT_TOOL;
        if (stack.has(DataComponents.FOOD)
                && !stack.has(DataComponents.POTION_CONTENTS)
                && !stack.is(ModTags.POTIONS))    return SLOT_FOOD;
        if (stack.has(DataComponents.POTION_CONTENTS)
                || stack.is(ModTags.POTIONS))     return SLOT_POTION;
        return -1;
    }

    /**
     * Always returns the preferred slot. Handler displaces the occupant.
     */
    public static int findTargetSlot(Player player, ItemStack stack) {
        return preferredSlot(stack);
    }

    /**
     * Finds which hotbar slot (0-8) holds this item type. -1 if not found.
     */
    public static int findInHotbar(Player player, ItemStack stack) {
        for (int i = 0; i < 9; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItem(slot, stack)) return i;
        }
        return -1;
    }

    private HotbarSlotMap() {}
}