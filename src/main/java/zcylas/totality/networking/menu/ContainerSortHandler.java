package zcylas.totality.networking.menu;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import zcylas.totality.api.menu.SortableContainerMenu;
import zcylas.totality.util.InventorySortHelper;

/**
 * Server-side handler for {@link ContainerSortPayload}.
 *
 * Sort: the client already computed the merged + sorted stack list; the server
 * just applies it. This gives proper localized alphabetical order.
 *
 * Transfers: use {@link Slot#safeInsert(ItemStack)} which respects the slot's
 * own {@code mayPlace()} filter and max stack size automatically.
 */
public final class ContainerSortHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ContainerSortPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(() ->
                        handle(ctx.player(), payload)));
    }

    public static void handle(ServerPlayer player, ContainerSortPayload payload) {
        if (!(player.containerMenu instanceof SortableContainerMenu sortable)) return;

        Container container = sortable.getSortableContainer();
        int containerSlotCount = sortable.getContainerSlotCount();
        AbstractContainerMenu menu = player.containerMenu;

        switch (payload.action()) {
            case ContainerSortPayload.SORT -> {
                // Client computed the sorted+merged stacks — just apply
                InventorySortHelper.applyMapping(container, payload.sortedStacks());
            }

            case ContainerSortPayload.QUICK_STACK ->
                    quickStack(player, menu, container, containerSlotCount,
                            payload.shiftPressed());

            case ContainerSortPayload.TO_CONTAINER ->
                    transferToContainer(player, menu, container, containerSlotCount,
                            payload.shiftPressed());

            case ContainerSortPayload.TO_INVENTORY ->
                    transferToInventory(player, menu, container, containerSlotCount);
        }

        sortable.afterAction(player);
    }

    // ── Quick-stack ───────────────────────────────────────────────────────────

    private static void quickStack(ServerPlayer player, AbstractContainerMenu menu,
                                   Container container, int containerSlotCount,
                                   boolean includeHotbar) {
        var inv = player.getInventory();
        int start = includeHotbar ? 0 : 9;
        for (int i = start; i < 36; i++) {
            ItemStack invStack = inv.getItem(i);
            if (invStack.isEmpty()) continue;
            // Only move if container already has this item
            boolean exists = false;
            for (int c = 0; c < container.getContainerSize(); c++) {
                if (ItemStack.isSameItemSameComponents(container.getItem(c), invStack)) {
                    exists = true; break;
                }
            }
            if (!exists) continue;
            // Use the menu's slot safeInsert to respect filters
            invStack = safeInsertViaSlots(menu, containerSlotCount, invStack);
            inv.setItem(i, invStack);
        }
    }

    // ── Transfer to container ─────────────────────────────────────────────────

    private static void transferToContainer(ServerPlayer player, AbstractContainerMenu menu,
                                            Container container, int containerSlotCount,
                                            boolean includeHotbar) {
        var inv = player.getInventory();
        int start = includeHotbar ? 0 : 9;
        for (int i = start; i < 36; i++) {
            ItemStack invStack = inv.getItem(i);
            if (invStack.isEmpty()) continue;
            // Use slot.mayPlace() to check eligibility — respects each container's filter
            if (!canInsertIntoContainer(menu, containerSlotCount, invStack)) continue;
            invStack = safeInsertViaSlots(menu, containerSlotCount, invStack);
            inv.setItem(i, invStack);
        }
    }

    // ── Transfer to inventory ─────────────────────────────────────────────────

    private static void transferToInventory(ServerPlayer player, AbstractContainerMenu menu,
                                            Container container, int containerSlotCount) {
        var inv = player.getInventory();
        for (int c = 0; c < containerSlotCount; c++) {
            ItemStack stack = container.getItem(c);
            if (stack.isEmpty()) continue;
            // Use safeInsert into player inventory slots (9–35)
            for (int i = 9; i < 36 && !stack.isEmpty(); i++) {
                Slot slot = menu.getSlot(containerSlotCount + (i - 9));
                stack = slot.safeInsert(stack);
            }
            container.setItem(c, stack);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Insert using the container's own menu slots — safeInsert respects
     * mayPlace(), max stack size, and all slot-specific rules.
     * Pass 1: fill partial stacks. Pass 2: fill empty slots.
     */
    private static ItemStack safeInsertViaSlots(AbstractContainerMenu menu,
                                                int containerSlotCount,
                                                ItemStack stack) {
        // Pass 1: partials
        for (int i = 0; i < containerSlotCount && !stack.isEmpty(); i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.getItem().isEmpty()) {
                stack = slot.safeInsert(stack);
            }
        }
        // Pass 2: empty slots
        for (int i = 0; i < containerSlotCount && !stack.isEmpty(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.getItem().isEmpty()) {
                stack = slot.safeInsert(stack);
            }
        }
        return stack;
    }

    private static boolean canInsertIntoContainer(AbstractContainerMenu menu,
                                                  int containerSlotCount,
                                                  ItemStack stack) {
        for (int i = 0; i < containerSlotCount; i++) {
            if (menu.getSlot(i).mayPlace(stack)) return true;
        }
        return false;
    }

    private ContainerSortHandler() {}
}