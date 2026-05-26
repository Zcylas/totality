package zcylas.totality.networking.inventory;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import zcylas.totality.api.inventory.HotbarSlotMap;
import zcylas.totality.api.rpg.skills.alchemy.potions.PotionDataComponent;

public final class InventoryActionHandler {

    // Special inventory slot markers
    public static final int SLOT_OFFHAND = -2;
    public static final int SLOT_ARMOR   = -3;

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
            handleUnequip(player, target, invSlot);
            return;
        }

        // invSlot == SLOT_OFFHAND means "take item from offhand and re-equip elsewhere"
        if (invSlot == SLOT_OFFHAND) {
            handleEquipFromOffhand(player, target);
            return;
        }
        // SLOT_ARMOR is only used for unequip; shouldn't reach here as equip
        if (invSlot == SLOT_ARMOR) return;

        // invSlot is a valid inventory index (0–35 covers hotbar and main inv)
        if (invSlot < 0 || invSlot >= 36) return;
        ItemStack toEquip = player.getInventory().getItem(invSlot);
        if (toEquip.isEmpty()) return;

        if (isArmorSlot(target)) {
            equipArmor(player, invSlot, toEquip, target);
        } else if (target == EquipmentSlot.OFFHAND) {
            equipOffhand(player, invSlot, toEquip);
        } else {
            equipMainhand(player, invSlot, toEquip);
        }
    }

    /**
     * Move the item currently in offhand to inventory, then equip it to the target slot.
     */
    private static void handleEquipFromOffhand(ServerPlayer player, EquipmentSlot target) {
        ItemStack offhandItem = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhandItem.isEmpty()) return;

        for (int i = 9; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.getInventory().setItem(i, offhandItem.copyWithCount(1));
                if (isArmorSlot(target)) {
                    equipArmor(player, i, offhandItem, target);
                } else if (target == EquipmentSlot.OFFHAND) {
                    equipOffhand(player, i, offhandItem);
                } else {
                    equipMainhand(player, i, offhandItem);
                }
                return;
            }
        }
        // No free slot — drop
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        player.drop(offhandItem, false);
    }

    private static void equipArmor(ServerPlayer player, int invSlot,
                                   ItemStack toEquip, EquipmentSlot target) {
        ItemStack current = player.getItemBySlot(target);
        if (!current.isEmpty()) sendToMainInventory(player, current.copy());
        player.setItemSlot(target, toEquip.copyWithCount(1));
        removeOne(player, invSlot);
    }

    /**
     * Equip an item to offhand. invSlot can be any slot 0–35, including hotbar.
     * The item is physically moved out of that slot (no copy/dupe).
     */
    private static void equipOffhand(ServerPlayer player, int invSlot, ItemStack toEquip) {
        ItemStack current = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!current.isEmpty()) sendToMainInventory(player, current.copy());
        player.setItemSlot(EquipmentSlot.OFFHAND, toEquip.copyWithCount(1));
        removeOne(player, invSlot);
    }

    /**
     * Equip an item to mainhand (places it in its preferred hotbar slot).
     * invSlot can be any slot 0–35.
     */
    private static void equipMainhand(ServerPlayer player, int invSlot, ItemStack toEquip) {
        int hotbarSlot = HotbarSlotMap.preferredSlot(toEquip);
        if (hotbarSlot == -1) hotbarSlot = player.getInventory().getSelectedSlot();

        if (invSlot == hotbarSlot) {
            // Already in the right slot, just select it
            player.getInventory().setSelectedSlot(hotbarSlot);
            return;
        }

        // Displace whatever is in the preferred hotbar slot
        ItemStack displaced = player.getInventory().getItem(hotbarSlot);
        if (!displaced.isEmpty()) {
            sendToMainInventory(player, displaced.copy());
            player.getInventory().setItem(hotbarSlot, ItemStack.EMPTY);
        }

        player.getInventory().setItem(hotbarSlot, toEquip.copyWithCount(1));
        removeOne(player, invSlot);
        player.getInventory().setSelectedSlot(hotbarSlot);
    }

    // ── Unequip ───────────────────────────────────────────────────────────────

    private static void handleUnequip(ServerPlayer player, EquipmentSlot target, int invSlot) {
        if (target == EquipmentSlot.MAINHAND) {
            unequipMainhand(player, invSlot);
        } else if (target == EquipmentSlot.OFFHAND) {
            ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
            if (offhand.isEmpty()) return;
            sendToMainInventory(player, offhand.copy());
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else {
            // Armor
            ItemStack armor = player.getItemBySlot(target);
            if (armor.isEmpty()) return;
            sendToMainInventory(player, armor.copy());
            player.setItemSlot(target, ItemStack.EMPTY);
        }
    }

    /**
     * Unequip from the mainhand hotbar slot.
     * hotbarSlot is the specific slot index (0–8) sent by the client via findInHotbar().
     * Falls back to selectedSlot only if -1 was sent (shouldn't normally happen).
     */
    private static void unequipMainhand(ServerPlayer player, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) {
            hotbarSlot = player.getInventory().getSelectedSlot();
        }
        ItemStack inSlot = player.getInventory().getItem(hotbarSlot);
        if (inSlot.isEmpty()) return;
        sendToMainInventory(player, inSlot.copy());
        player.getInventory().setItem(hotbarSlot, ItemStack.EMPTY);
    }

    // ── Use ───────────────────────────────────────────────────────────────────

    private static void handleUse(InventoryUsePayload payload,
                                  ServerPlayNetworking.Context ctx) {
        ServerPlayer player  = ctx.player();
        int          invSlot = payload.inventorySlot();

        if (invSlot < 0 || invSlot >= 36) return;
        ItemStack stack = player.getInventory().getItem(invSlot);
        if (stack.isEmpty()) return;

        boolean isAlchemyPotion = stack.has(PotionDataComponent.POTION_DATA);
        boolean isFood   = stack.has(DataComponents.FOOD);
        boolean isPotion = stack.has(DataComponents.POTION_CONTENTS);

        if (!isFood && !isPotion && !isAlchemyPotion) return;

        if (isFood && !isAlchemyPotion) {
            var food = stack.get(DataComponents.FOOD);
            if (food != null && !food.canAlwaysEat()
                    && !player.getFoodData().needsFood()) return;
        }

        if (stack.getItem() instanceof zcylas.totality.item.potion.AlchemyPotionItem potionItem) {
            var data = potionItem.getPotionData(stack);
            for (var entry : data.effects()) {
                if (entry.durationTicks() > 0)
                    entry.effect().applyConsume(player, entry.magnitude(), entry.durationTicks());
            }
            for (var entry : data.effects()) {
                if (entry.durationTicks() == 0)
                    entry.effect().applyConsume(player, entry.magnitude(), entry.durationTicks());
            }
            removeOne(player, invSlot);
            return;
        }

        ItemStack single = stack.copyWithCount(1);
        ItemStack result = single.finishUsingItem(player.level(), player);
        removeOne(player, invSlot);

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

        if (invSlot < 0 || invSlot >= 36) return;
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

    private static void sendToMainInventory(ServerPlayer player, ItemStack stack) {
        // Intentionally excludes hotbar (slots 0-8) — unequipped items go to main inventory only
        if (player.getInventory().add(stack)) return;
        player.drop(stack, false);
    }

    private static void removeOne(ServerPlayer player, int invSlot) {
        ItemStack slot = player.getInventory().getItem(invSlot);
        if (slot.getCount() <= 1) {
            player.getInventory().setItem(invSlot, ItemStack.EMPTY);
        } else {
            player.getInventory().getItem(invSlot).shrink(1);
        }
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD  || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private InventoryActionHandler() {}
}