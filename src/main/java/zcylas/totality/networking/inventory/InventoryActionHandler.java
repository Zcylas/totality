package zcylas.totality.networking.inventory;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Server-side handlers for custom inventory screen actions.
 * Call InventoryActionHandler.register() from your server initializer.
 *
 * Hand slot (MAINHAND/OFFHAND) design:
 *   Items in hand slots are NOT removed from inventory — they stay there and
 *   the equipment slot holds an independent copy. This mirrors vanilla hotbar
 *   behaviour. On unequip, only the equipment slot is cleared.
 *
 * Armor slot design:
 *   Items ARE physically moved from inventory to the armor slot.
 *   On unequip they are returned to inventory.
 */
public final class InventoryActionHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                InventoryEquipPayload.TYPE, InventoryActionHandler::handleEquip);
        ServerPlayNetworking.registerGlobalReceiver(
                InventoryUsePayload.TYPE,   InventoryActionHandler::handleUse);
        ServerPlayNetworking.registerGlobalReceiver(
                InventoryDropPayload.TYPE,  InventoryActionHandler::handleDrop);
    }

    // ── Equip ─────────────────────────────────────────────────────────────────

    private static void handleEquip(InventoryEquipPayload payload,
                                    ServerPlayNetworking.Context ctx) {
        ServerPlayer  player  = ctx.player();
        int           invSlot = payload.inventorySlot();
        EquipmentSlot target  = payload.targetSlot();
        boolean       unequip = payload.unequip();

        if (unequip) {
            ItemStack equipped = player.getItemBySlot(target);
            if (equipped.isEmpty()) return;

            if (isArmorSlot(target)) {
                // Armor was physically moved out of inventory — return it
                player.getInventory().add(equipped.copy());
            }
            // Hand slots: item never left inventory, just clear the slot reference
            player.setItemSlot(target, ItemStack.EMPTY);
            return;
        }

        // Validate inventory slot
        if (invSlot < 0 || invSlot >= player.getInventory().getContainerSize()) return;
        ItemStack inInventory = player.getInventory().getItem(invSlot);
        if (inInventory.isEmpty()) return;

        if (isArmorSlot(target)) {
            // ── Armor: physically moves inventory → equipment slot ────────────
            ItemStack currentArmor = player.getItemBySlot(target);
            if (!currentArmor.isEmpty()) {
                player.getInventory().add(currentArmor.copy());
            }
            // Equip one copy
            player.setItemSlot(target, inInventory.copyWithCount(1));
            // Remove exactly one from inventory
            removeOne(player, invSlot);

        } else {
            // ── Hand slot: item STAYS in inventory, slot gets a copy ──────────
            // Clear whatever was previously in that hand (it stays in its own inv slot)
            player.setItemSlot(target, ItemStack.EMPTY);
            // Set the hand to a fresh copy — count 1 for display purposes
            player.setItemSlot(target, inInventory.copyWithCount(1));
        }
    }

    // ── Use ───────────────────────────────────────────────────────────────────

    private static void handleUse(InventoryUsePayload payload,
                                  ServerPlayNetworking.Context ctx) {
        ServerPlayer player  = ctx.player();
        int          invSlot = payload.inventorySlot();

        if (invSlot < 0 || invSlot >= player.getInventory().getContainerSize()) return;
        ItemStack stack = player.getInventory().getItem(invSlot);
        if (stack.isEmpty()) return;

        boolean isFood   = stack.has(DataComponents.FOOD);
        boolean isPotion = stack.has(DataComponents.POTION_CONTENTS);
        if (!isFood && !isPotion) return;

        // Check hunger constraint for food
        if (isFood) {
            var food = stack.get(DataComponents.FOOD);
            if (food != null && !food.canAlwaysEat()
                    && !player.getFoodData().needsFood()) return;
        }

        // Work on a detached single-item copy — never mutate the slot reference
        ItemStack single = stack.copyWithCount(1);
        ItemStack result = single.finishUsingItem(player.level(), player);

        // Remove exactly one from inventory
        removeOne(player, invSlot);

        // Return container item if any (glass bottle, bowl, etc.)
        if (!result.isEmpty()
                && result.getItem() != single.getItem()
                && result.getItem() != Items.AIR) {
            player.getInventory().add(result);
        }
    }

    // ── Drop ──────────────────────────────────────────────────────────────────

    private static void handleDrop(InventoryDropPayload payload,
                                   ServerPlayNetworking.Context ctx) {
        ServerPlayer player     = ctx.player();
        int          invSlot    = payload.inventorySlot();
        boolean      wholeStack = payload.wholeStack();

        if (invSlot < 0 || invSlot >= player.getInventory().getContainerSize()) return;
        ItemStack stack = player.getInventory().getItem(invSlot);
        if (stack.isEmpty()) return;

        if (wholeStack) {
            player.drop(stack.copy(), false);
            player.getInventory().setItem(invSlot, ItemStack.EMPTY);
        } else {
            player.drop(stack.copyWithCount(1), false);
            removeOne(player, invSlot);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Removes exactly one item from an inventory slot, clearing it if count reaches 0. */
    private static void removeOne(ServerPlayer player, int invSlot) {
        ItemStack slot = player.getInventory().getItem(invSlot);
        if (slot.getCount() <= 1) {
            player.getInventory().setItem(invSlot, ItemStack.EMPTY);
        } else {
            // Get a fresh reference to avoid acting on a stale copy
            player.getInventory().getItem(invSlot).shrink(1);
        }
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private InventoryActionHandler() {}
}