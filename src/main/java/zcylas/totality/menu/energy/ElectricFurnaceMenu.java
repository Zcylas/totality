package zcylas.totality.menu.energy;

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
import zcylas.totality.block.energy.ElectricFurnaceBlock;
import zcylas.totality.blockentity.energy.ElectricFurnaceBlockEntity;

public class ElectricFurnaceMenu extends AbstractContainerMenu {

    public static final MenuType<ElectricFurnaceMenu> TYPE = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "electric_furnace"),
            new ExtendedMenuType<>(ElectricFurnaceMenu::new, BlockPos.STREAM_CODEC));

    private static final int SMELT_TIME_INDEX       = 0;
    private static final int TOTAL_SMELT_TIME_INDEX = 1;
    private static final int ENERGY_INDEX           = 2;
    private static final int MAX_ENERGY_INDEX       = 3;
    private static final int FACING_INDEX           = 4;
    private static final int DATA_COUNT             = 5;

    private static final int ENERGY_SCALE = 10;

    private final Container container;
    private final ContainerData data;
    public final Level level;
    private final BlockPos blockPos;

    // Client-side constructor
    public ElectricFurnaceMenu(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, new SimpleContainer(2), new SimpleContainerData(DATA_COUNT), pos);
    }


    public static ElectricFurnaceMenu create(int syncId, Inventory playerInventory, ElectricFurnaceBlockEntity be) {
        return new ElectricFurnaceMenu(syncId, playerInventory, (Container) be, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case SMELT_TIME_INDEX       -> be.smeltTime;
                    case TOTAL_SMELT_TIME_INDEX -> ElectricFurnaceBlockEntity.SMELT_TIME_TOTAL;
                    case ENERGY_INDEX           -> (int) (be.getStoredEnergy() / ENERGY_SCALE);
                    case MAX_ENERGY_INDEX       -> (int) (be.getMaxEnergy() / ENERGY_SCALE);
                    case FACING_INDEX           -> be.getBlockState()
                            .getValue(ElectricFurnaceBlock.FACING).get3DDataValue();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {
                if (index == SMELT_TIME_INDEX) be.smeltTime = value;
            }
            @Override public int getCount() { return DATA_COUNT; }
        }, be.getBlockPos());
    }


    private ElectricFurnaceMenu(int syncId, Inventory playerInventory,
                                Container container, ContainerData data, BlockPos blockPos) {
        super(TYPE, syncId);
        this.container = container;
        this.data      = data;
        this.level     = playerInventory.player.level();
        this.blockPos  = blockPos;

        // Input slot: sprite at X=60, so register at X=61, Y=32
        addSlot(new Slot(container, ElectricFurnaceBlockEntity.SLOT_INPUT, 61, 32));

        // Output slot: sprite at X=98, so register at X=99, Y=32
        addSlot(new Slot(container, ElectricFurnaceBlockEntity.SLOT_OUTPUT, 99, 32) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));

        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));

        addDataSlots(data);
    }

    public BlockPos getBlockPos()  { return blockPos; }
    public Direction getFacing()   { return Direction.from3DDataValue(data.get(FACING_INDEX)); }
    public int getSmeltTime()      { return data.get(SMELT_TIME_INDEX); }
    public int getSmeltTimeTotal() { return data.get(TOTAL_SMELT_TIME_INDEX); }
    public long getStoredEnergy()  { return data.get(ENERGY_INDEX) * (long) ENERGY_SCALE; }
    public long getMaxEnergy()     { return data.get(MAX_ENERGY_INDEX) * (long) ENERGY_SCALE; }

    public int getScaledEnergy(int scale) {
        long max = getMaxEnergy();
        return max == 0 ? 0 : (int) (getStoredEnergy() * scale / max);
    }

    public int getScaledProgress(int scale) {
        int total = getSmeltTimeTotal();
        return total == 0 ? 0 : getSmeltTime() * scale / total;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == ElectricFurnaceBlockEntity.SLOT_OUTPUT) {
                if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
            } else if (index >= 2) {
                if (!moveItemStackTo(stack, 0, 1, false)) {
                    if (index < 2 + 27) {
                        if (!moveItemStackTo(stack, 29, slots.size(), false)) return ItemStack.EMPTY;
                    } else {
                        if (!moveItemStackTo(stack, 2, 29, false)) return ItemStack.EMPTY;
                    }
                }
            } else {
                if (!moveItemStackTo(stack, 2, slots.size(), false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) { return container.stillValid(player); }
}