package zcylas.totality.blockentity.generator;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.energy.HasSidedEnergy;
import zcylas.totality.api.energy.UEItem;
import zcylas.totality.api.energy.UETransferTicker;
import zcylas.totality.api.energy.base.SimpleSidedUEContainer;
import zcylas.totality.block.generator.GeneratorBlock;
import zcylas.totality.init.ModBlockEntities;
import zcylas.totality.menu.generator.GeneratorMenu;

public class GeneratorBlockEntity extends BlockEntity
        implements ExtendedMenuProvider<BlockPos>, HasSidedEnergy {

    public static final long CAPACITY = 40_000L;
    public static final long INPUT_OUTPUT = 32L;
    public static final long GENERATION_RATE = 10L;
    public static final int FUEL_SLOT = 0;
    public static final int CHARGE_SLOT = 1;

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);

    public int burnTime = 0;
    public int totalBurnTime = 0;

    public final SimpleSidedUEContainer energy = new SimpleSidedUEContainer(
            CAPACITY, INPUT_OUTPUT, INPUT_OUTPUT) {
        @Override
        protected void onCommit() { setChanged(); }
    };

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            GeneratorBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;

        long stored = be.energy.getAmount();
        long capacity = be.energy.getCapacity();

        if (be.burnTime > 0 && stored < capacity) {
            be.burnTime--;
            be.energy.setAmountUnchecked(Math.min(stored + GENERATION_RATE, capacity));
            be.setChanged();
        }

        if (be.burnTime == 0) {
            ItemStack fuel = be.items.get(FUEL_SLOT);
            int duration = getFuelDuration(level, fuel);
            if (duration > 0 && stored < capacity) {
                be.burnTime = duration;
                be.totalBurnTime = duration;
                if (fuel.getCount() == 1) {
                    be.items.set(FUEL_SLOT, ItemStack.EMPTY);
                } else {
                    fuel.shrink(1);
                }
                be.setChanged();
            }
        }

        // Charge item in charge slot
        ItemStack chargeStack = be.items.get(CHARGE_SLOT);
        if (!chargeStack.isEmpty() && chargeStack.getItem() instanceof UEItem ueItem) {
            if (!ueItem.isFull(chargeStack)) {
                long currentEnergy = be.energy.getAmount();
                if (currentEnergy > 0) {
                    long toTransfer = Math.min(INPUT_OUTPUT,
                            Math.min(ueItem.getEnergyMaxInput(chargeStack),
                                    Math.min(currentEnergy,
                                            ueItem.getEnergyCapacity(chargeStack)
                                                    - ueItem.getStoredEnergy(chargeStack))));
                    if (toTransfer > 0) {
                        ueItem.setStoredEnergy(chargeStack,
                                ueItem.getStoredEnergy(chargeStack) + toTransfer);
                        be.energy.setAmountUnchecked(currentEnergy - toTransfer);
                        be.setChanged();
                    }
                }
            }
        }

        boolean isLit = be.burnTime > 0;
        if (state.getValue(GeneratorBlock.LIT) != isLit) {
            level.setBlock(pos, state.setValue(GeneratorBlock.LIT, isLit), 3);
        }

        UETransferTicker.pushToNeighbors((ServerLevel) level, pos, be, be.energy);
    }

    public static int getFuelDuration(Level level, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return level.fuelValues().burnDuration(stack) / 4;
    }

    public long getStoredEnergy() { return energy.getAmount(); }
    public long getMaxEnergy() { return energy.getCapacity(); }

    public int getScaledEnergy(int scale) {
        if (energy.getCapacity() == 0) return 0;
        return (int) ((float) energy.getAmount() / energy.getCapacity() * scale);
    }

    public ItemStack getItem(int slot) { return items.get(slot); }
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public SimpleSidedUEContainer getEnergy() { return energy; }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("BurnTime", burnTime);
        output.putInt("TotalBurnTime", totalBurnTime);
        energy.saveToOutput(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        burnTime = input.getIntOr("BurnTime", 0);
        totalBurnTime = input.getIntOr("TotalBurnTime", 0);
        energy.loadFromInput(input);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.totality.generator");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            syncSideModes(serverPlayer, worldPosition);
        }
        return new GeneratorMenu(syncId, inventory, this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (var reporter = new ProblemReporter.ScopedCollector(problemPath(), Totality.LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, registries);
            energy.saveToOutput(output);
            return output.buildResult();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}