package zcylas.totality.screen.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import zcylas.totality.init.ModTags;

/**
 * Static helpers for querying equip state.
 * Shared between TotalityInventoryScreen (actions) and InventoryItemList/InventoryItemDetail (rendering).
 */
public final class InventoryEquipHelper {

    // ── Item classification ───────────────────────────────────────────────────

    public static boolean isTwoHandedWeapon(ItemStack stack) {
        return stack.is(ModTags.TWO_HANDED_WEAPONS)
                || stack.is(ModTags.BOWS)
                || stack.is(ModTags.CROSSBOWS);
    }

    public static boolean isOneHandedWeapon(ItemStack stack) {
        return stack.is(ModTags.ONE_HANDED_WEAPONS);
    }

    public static boolean isThrownWeapon(ItemStack stack) {
        return stack.is(ModTags.THROWN_WEAPONS);
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.is(ModTags.BOWS) || stack.is(ModTags.CROSSBOWS)
                || stack.is(ModTags.ONE_HANDED_WEAPONS) || stack.is(ModTags.TWO_HANDED_WEAPONS)
                || stack.is(ModTags.THROWN_WEAPONS);
    }

    public static boolean isArmor(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return false;
        EquipmentSlot s = eq.slot();
        return s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST
                || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET;
    }

    public static boolean isPotion(ItemStack stack) {
        return stack.has(DataComponents.POTION_CONTENTS)
                || stack.is(ModTags.POTIONS)
                || stack.has(zcylas.totality.api.rpg.skills.alchemy.potions.PotionDataComponent.POTION_DATA);
    }

    public static boolean isSpecial(ItemStack stack) { return stack.is(ModTags.SPECIAL); }

    public static boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD) && !isPotion(stack);
    }

    public static boolean isTool(ItemStack stack) {
        return stack.is(ModTags.TOOLS) && !stack.is(ModTags.SPECIAL);
    }

    public static boolean isHandTool(ItemStack stack) {
        return stack.is(ModTags.TOOLS)
                && !stack.is(ModTags.SPECIAL)
                && !stack.is(ModTags.ONE_HANDED_WEAPONS)
                && !stack.is(ModTags.TWO_HANDED_WEAPONS)
                && !stack.is(ModTags.BOWS)
                && !stack.is(ModTags.CROSSBOWS)
                && !stack.is(ModTags.THROWN_WEAPONS);
    }

    public static boolean isMisc(ItemStack stack) {
        return !isWeapon(stack) && !isArmor(stack) && !isPotion(stack)
                && !isFood(stack) && !isTool(stack) && !isSpecial(stack);
    }

    public static boolean isArmorEquipSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    // ── Equip state ───────────────────────────────────────────────────────────

    /**
     * Scans all 9 hotbar slots for a matching item. Authoritative — does not
     * rely on selectedSlot or getItemBySlot(MAINHAND).
     */
    public static int findInHotbar(Player player, ItemStack stack) {
        boolean mutableComponents = stack.is(ModTags.SPECIAL);
        for (int i = 0; i < 9; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            boolean matches = mutableComponents
                    ? ItemStack.isSameItem(slot, stack)
                    : ItemStack.isSameItemSameComponents(slot, stack);
            if (matches) return i;
        }
        return -1;
    }

    public static boolean isInMainhand(Player player, ItemStack stack) {
        return findInHotbar(player, stack) >= 0;
    }

    public static boolean isInOffhand(Player player, ItemStack stack) {
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return !offhand.isEmpty() && ItemStack.isSameItem(offhand, stack);
    }

    public static boolean isInArmorSlot(Player player, ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return false;
        EquipmentSlot slot = eq.slot();
        if (!isArmorEquipSlot(slot)) return false;
        ItemStack equipped = player.getItemBySlot(slot);
        return !equipped.isEmpty() && ItemStack.isSameItem(equipped, stack);
    }

    /** Returns the badge string ([A], [R], [L], [B]) or null if not equipped. */
    public static String getEquipBadge(Player player, ItemStack stack) {
        if (isArmor(stack))                        return isInArmorSlot(player, stack) ? "[A]" : null;
        if (isSpecial(stack) || isHandTool(stack)) return isInMainhand(player, stack)  ? "[R]" : null;
        if (isWeapon(stack)) {
            boolean main = isInMainhand(player, stack);
            boolean off  = isInOffhand(player, stack);
            if (main && off) return "[B]";
            if (main)        return "[R]";
            if (off)         return "[L]";
        }
        return null;
    }

    private InventoryEquipHelper() {}
}