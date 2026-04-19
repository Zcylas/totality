package zcylas.totality.menu.generator;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import zcylas.totality.block.generator.GeneratorBlock;
import zcylas.totality.blockentity.generator.GeneratorBlockEntity;

public class GeneratorMenu extends AbstractContainerMenu {

    public static final MenuType<GeneratorMenu> TYPE = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "generator"),
            new ExtendedMenuType<>(GeneratorMenu::new, BlockPos.STREAM_CODEC));

    private static final int BURN_TIME_INDEX = 0;
    private static final int TOTAL_BURN_TIME_INDEX = 1;
    private static final int ENERGY_INDEX = 2;
    private static final int MAX_ENERGY_INDEX = 3;
    private static final int FACING_INDEX = 4;
    private static final int DATA_COUNT = 5;

    private final Container container;
    private final ContainerData data;
    public final Level level;
    private final BlockPos blockPos;

    // Client-side constructor
    public GeneratorMenu(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, new SimpleContainer(2),
                new SimpleContainerData(DATA_COUNT), pos);
    }

    // Server-side constructor
    public GeneratorMenu(int syncId, Inventory playerInventory, GeneratorBlockEntity blockEntity) {
        this(syncId, playerInventory, new Container() {
            @Override public int getContainerSize() { return 2; }
            @Override public boolean isEmpty() {
                return blockEntity.getItem(0).isEmpty() && blockEntity.getItem(1).isEmpty();
            }
            @Override public ItemStack getItem(int slot) { return blockEntity.getItem(slot); }
            @Override public ItemStack removeItem(int slot, int count) {
                ItemStack stack = blockEntity.getItem(slot).split(count);
                blockEntity.setChanged();
                return stack;
            }
            @Override public ItemStack removeItemNoUpdate(int slot) {
                ItemStack stack = blockEntity.getItem(slot);
                blockEntity.setItem(slot, ItemStack.EMPTY);
                return stack;
            }
            @Override public void setItem(int slot, ItemStack stack) { blockEntity.setItem(slot, stack); }
            @Override public boolean stillValid(Player player) { return true; }
            @Override public void setChanged() { blockEntity.setChanged(); }
            @Override public void clearContent() {
                blockEntity.setItem(0, ItemStack.EMPTY);
                blockEntity.setItem(1, ItemStack.EMPTY);
            }
        }, new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case BURN_TIME_INDEX -> blockEntity.burnTime;
                    case TOTAL_BURN_TIME_INDEX -> blockEntity.totalBurnTime;
                    case ENERGY_INDEX -> (int) (blockEntity.getStoredEnergy() / 10);
                    case MAX_ENERGY_INDEX -> (int) (blockEntity.getMaxEnergy() / 10);
                    case FACING_INDEX -> blockEntity.getBlockState()
                            .getValue(GeneratorBlock.FACING).get3DDataValue();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {
                switch (index) {
                    case BURN_TIME_INDEX -> blockEntity.burnTime = value;
                    case TOTAL_BURN_TIME_INDEX -> blockEntity.totalBurnTime = value;
                }
            }
            @Override public int getCount() { return DATA_COUNT; }
        }, blockEntity.getBlockPos());
    }

    private GeneratorMenu(int syncId, Inventory playerInventory,
                          Container container, ContainerData data,
                          BlockPos blockPos) {
        super(TYPE, syncId);
        this.container = container;
        this.data = data;
        this.level = playerInventory.player.level();
        this.blockPos = blockPos;

        this.addSlot(new Slot(container, GeneratorBlockEntity.FUEL_SLOT, 80, 32) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return GeneratorBlockEntity.getFuelDuration(level, stack) > 0;
            }
        });

        this.addSlot(new Slot(container, GeneratorBlockEntity.CHARGE_SLOT, 8, 65));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18,
                        84 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    public BlockPos getBlockPos() { return blockPos; }
    public Direction getFacing() {
        return Direction.from3DDataValue(data.get(FACING_INDEX));
    }

    public int getBurnTime() { return data.get(BURN_TIME_INDEX); }
    public int getTotalBurnTime() { return data.get(TOTAL_BURN_TIME_INDEX); }
    public long getStoredEnergy() { return data.get(ENERGY_INDEX) * 10L; }
    public long getMaxEnergy() { return data.get(MAX_ENERGY_INDEX) * 10L; }

    public int getScaledBurnTime(int scale) {
        int total = getTotalBurnTime();
        if (total == 0) return 0;
        return getBurnTime() * scale / total;
    }

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
            if (index == 0 || index == 1) {
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