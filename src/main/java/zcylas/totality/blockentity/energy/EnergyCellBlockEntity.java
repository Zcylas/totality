package zcylas.totality.blockentity.energy;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.industrial.energy.HasSidedEnergy;
import zcylas.totality.api.industrial.energy.UEItem;
import zcylas.totality.api.industrial.energy.UETransferTicker;
import zcylas.totality.api.industrial.energy.base.SimpleSidedUEContainer;
import zcylas.totality.init.ModBlockEntities;
import zcylas.totality.menu.energy.EnergyCellMenu;

public class EnergyCellBlockEntity extends BlockEntity
        implements ExtendedMenuProvider<BlockPos>, HasSidedEnergy {

    public static final int DISCHARGE_SLOT = 0;
    public static final int CHARGE_SLOT = 1;

    private final long capacity;
    private final long maxInput;
    private final long maxOutput;

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);

    public final SimpleSidedUEContainer energy;

    public EnergyCellBlockEntity(BlockPos pos, BlockState state,
                                 long capacity, long maxInput, long maxOutput) {
        super(ModBlockEntities.ENERGY_CELL, pos, state);
        this.capacity = capacity;
        this.maxInput = maxInput;
        this.maxOutput = maxOutput;
        this.energy = new SimpleSidedUEContainer(capacity, maxInput, maxOutput) {
            @Override
            protected void onCommit() { setChanged(); }
        };
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;

        // Discharge slot — drain energy from item into cell
        ItemStack dischargeStack = items.get(DISCHARGE_SLOT);
        if (!dischargeStack.isEmpty() && dischargeStack.getItem() instanceof UEItem ueItem) {
            long stored = ueItem.getStoredEnergy(dischargeStack);
            if (stored > 0 && energy.getAmount() < energy.getCapacity()) {
                long toTransfer = Math.min(maxInput,
                        Math.min(stored, energy.getCapacity() - energy.getAmount()));
                if (toTransfer > 0) {
                    ueItem.setStoredEnergy(dischargeStack, stored - toTransfer);
                    energy.setAmountUnchecked(energy.getAmount() + toTransfer);
                    setChanged();
                }
            }
        }

        // Charge slot — charge item from cell
        ItemStack chargeStack = items.get(CHARGE_SLOT);
        if (!chargeStack.isEmpty() && chargeStack.getItem() instanceof UEItem ueItem) {
            if (!ueItem.isFull(chargeStack) && energy.getAmount() > 0) {
                long toTransfer = Math.min(maxOutput,
                        Math.min(ueItem.getEnergyMaxInput(chargeStack),
                                Math.min(energy.getAmount(),
                                        ueItem.getEnergyCapacity(chargeStack)
                                                - ueItem.getStoredEnergy(chargeStack))));
                if (toTransfer > 0) {
                    ueItem.setStoredEnergy(chargeStack,
                            ueItem.getStoredEnergy(chargeStack) + toTransfer);
                    energy.setAmountUnchecked(energy.getAmount() - toTransfer);
                    setChanged();
                }
            }
        }

        UETransferTicker.pushToNeighbors((ServerLevel) level, worldPosition, this, energy);
    }

    public long getCapacity() { return capacity; }
    public long getStoredEnergy() { return energy.getAmount(); }
    public long getMaxInput() { return maxInput; }
    public long getMaxOutput() { return maxOutput; }

    public ItemStack getItem(int slot) { return items.get(slot); }
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public SimpleSidedUEContainer getEnergy() { return energy; }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            syncSideModes(serverPlayer, worldPosition);
        }
        return new EnergyCellMenu(syncId, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        energy.saveToOutput(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        energy.loadFromInput(input);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        try (var reporter = new ProblemReporter.ScopedCollector(
                problemPath(), Totality.LOGGER)) {
            var output = TagValueOutput.createWithContext(
                    reporter, level != null ? level.registryAccess()
                            : net.minecraft.core.RegistryAccess.EMPTY);
            saveAdditional(output);
            components.set(
                    DataComponents.BLOCK_ENTITY_DATA,
                    TypedEntityData.of(getType(), output.buildResult())
            );
        }
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