package zcylas.totality.menu.energy;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import zcylas.totality.Totality;
import zcylas.totality.blockentity.energy.EnergyCellBlockEntity;

public class EnergyCellMenu extends AbstractContainerMenu {

    public static final MenuType<EnergyCellMenu> TYPE = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "energy_cell"),
            new ExtendedMenuType<>(EnergyCellMenu::new, BlockPos.STREAM_CODEC));

    private static final int ENERGY_INDEX = 0;
    private static final int MAX_ENERGY_INDEX = 1;
    private static final int DATA_COUNT = 2;

    private final Container container;
    private final ContainerData data;
    public final Level level;
    private final BlockPos blockPos;

    // Client constructor
    public EnergyCellMenu(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, new SimpleContainer(2),
                new SimpleContainerData(DATA_COUNT), pos);
    }

    // Server constructor
    public EnergyCellMenu(int syncId, Inventory playerInventory, EnergyCellBlockEntity be) {
        this(syncId, playerInventory, new Container() {
            @Override public int getContainerSize() { return 2; }
            @Override public boolean isEmpty() {
                return be.getItem(0).isEmpty() && be.getItem(1).isEmpty();
            }
            @Override public ItemStack getItem(int slot) { return be.getItem(slot); }
            @Override public ItemStack removeItem(int slot, int count) {
                ItemStack stack = be.getItem(slot).split(count);
                be.setChanged();
                return stack;
            }
            @Override public ItemStack removeItemNoUpdate(int slot) {
                ItemStack stack = be.getItem(slot);
                be.setItem(slot, ItemStack.EMPTY);
                return stack;
            }
            @Override public void setItem(int slot, ItemStack stack) { be.setItem(slot, stack); }
            @Override public boolean stillValid(Player player) { return true; }
            @Override public void setChanged() { be.setChanged(); }
            @Override public void clearContent() {
                be.setItem(0, ItemStack.EMPTY);
                be.setItem(1, ItemStack.EMPTY);
            }
        }, new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case ENERGY_INDEX -> (int) (be.getStoredEnergy() / 100);
                    case MAX_ENERGY_INDEX -> (int) (be.getCapacity() / 100);
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, be.getBlockPos());
    }

    private EnergyCellMenu(int syncId, Inventory playerInventory,
                           Container container, ContainerData data, BlockPos blockPos) {
        super(TYPE, syncId);
        this.container = container;
        this.data = data;
        this.level = playerInventory.player.level();
        this.blockPos = blockPos;

        // Discharge slot (left) — drains into cell
        this.addSlot(new Slot(container, EnergyCellBlockEntity.DISCHARGE_SLOT, 61, 32));

        // Charge slot (right) — charged by cell
        this.addSlot(new Slot(container, EnergyCellBlockEntity.CHARGE_SLOT, 99, 32));

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18, 85 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));
        }

        this.addDataSlots(data);
    }

    public BlockPos getBlockPos() { return blockPos; }
    public long getStoredEnergy() { return data.get(ENERGY_INDEX) * 100L; }
    public long getMaxEnergy() { return data.get(MAX_ENERGY_INDEX) * 100L; }

    public int getScaledEnergy(int scale) {
        long max = getMaxEnergy();
        if (max == 0) return 0;
        return (int) (getStoredEnergy() * scale / max);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 2) {
                if (!this.moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, 0, 2, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }
}