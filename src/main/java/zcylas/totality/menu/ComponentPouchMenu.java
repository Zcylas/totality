package zcylas.totality.menu;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import zcylas.totality.api.menu.SortableContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import zcylas.totality.Totality;
import zcylas.totality.api.item.SpellMaterialIngredient;

/**
 * Container menu for the {@link zcylas.totality.item.spell_material.ComponentPouchItem}.
 *
 * Layout:
 *   Rows 0–1 (18 slots) — pouch storage, only accepts SpellMaterialIngredient items.
 *   Rows 2–4 (36 slots) — player inventory.
 *
 * Contents are loaded from the item's {@link DataComponents#CONTAINER} on open
 * and saved back when the menu is closed.
 */
public class ComponentPouchMenu extends AbstractContainerMenu
        implements SortableContainerMenu {

    public static final MenuType<ComponentPouchMenu> TYPE = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "component_pouch"),
            new ExtendedMenuType<>(ComponentPouchMenu::new, ByteBufCodecs.INT));

    private static final int POUCH_ROWS   = 3;
    private static final int POUCH_COLS   = 9;
    public static final int  POUCH_SLOTS  = POUCH_ROWS * POUCH_COLS; // 27
    private static final int INV_SLOTS    = 36;

    /** The SimpleContainer used during this menu session. */
    private final SimpleContainer pouchContainer;
    /** Slot index in the player inventory where the pouch lives, used to save back. */
    private final int pouchSlotIndex;

    // ── Client-side constructor (called by ExtendedMenuType) ──────────────────

    public ComponentPouchMenu(int syncId, Inventory playerInventory, int pouchSlotIndex) {
        this(syncId, playerInventory, pouchSlotIndex, new SimpleContainer(POUCH_SLOTS));
    }

    // ── Shared constructor ────────────────────────────────────────────────────

    public ComponentPouchMenu(int syncId, Inventory playerInventory,
                              int pouchSlotIndex, SimpleContainer container) {
        super(TYPE, syncId);
        this.pouchContainer  = container;
        this.pouchSlotIndex  = pouchSlotIndex;

        checkContainerSize(container, POUCH_SLOTS);

        // ── Pouch slots (filtered) ────────────────────────────────────────────
        for (int row = 0; row < POUCH_ROWS; row++) {
            for (int col = 0; col < POUCH_COLS; col++) {
                int slotIdx = row * POUCH_COLS + col;
                addSlot(new Slot(container, slotIdx, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof SpellMaterialIngredient;
                    }
                });
            }
        }

        // ── Player inventory ──────────────────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    // ── Loading / saving ──────────────────────────────────────────────────────

    /**
     * Load ItemContainerContents from the pouch item into our SimpleContainer.
     * Call server-side when opening the menu.
     */
    public static SimpleContainer loadFrom(ItemStack pouchStack) {
        SimpleContainer container = new SimpleContainer(POUCH_SLOTS);
        ItemContainerContents contents = pouchStack.get(DataComponents.CONTAINER);
        if (contents != null) {
            net.minecraft.core.NonNullList<ItemStack> list =
                    net.minecraft.core.NonNullList.withSize(POUCH_SLOTS, ItemStack.EMPTY);
            contents.copyInto(list);
            for (int i = 0; i < POUCH_SLOTS; i++) {
                container.setItem(i, list.get(i));
            }
        }
        return container;
    }

    /**
     * Save our SimpleContainer contents back into the pouch item stack.
     * Call server-side when the menu is closed.
     */
    public void saveTo(Inventory playerInventory) {
        ItemStack pouchStack = playerInventory.getItem(pouchSlotIndex);
        if (pouchStack.isEmpty()) return;

        var list = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for (int i = 0; i < POUCH_SLOTS; i++) {
            net.minecraft.world.item.ItemStack s = pouchContainer.getItem(i);
            if (!s.isEmpty()) list.add(s.copy());
        }
        pouchStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            saveTo(player.getInventory());
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < POUCH_SLOTS) {
                if (!moveItemStackTo(stack, POUCH_SLOTS, POUCH_SLOTS + INV_SLOTS, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stack, 0, POUCH_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    public SimpleContainer getPouchContainer() { return pouchContainer; }
    public int getPouchSlotIndex()            { return pouchSlotIndex; }

    // ── SortableContainerMenu ─────────────────────────────────────────────────
    @Override public Container getSortableContainer()  { return pouchContainer; }
    @Override public int getContainerSlotCount()       { return POUCH_SLOTS; }
    @Override public void afterAction(net.minecraft.server.level.ServerPlayer player) {
        saveTo(player.getInventory());
    }
}