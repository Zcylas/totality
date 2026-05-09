package zcylas.totality.blockentity.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.industrial.fluid.TotalityFluidStorage;
import zcylas.totality.init.ModBlockEntities;

public class FluidTankBlockEntity extends BlockEntity {

    private final TotalityFluidStorage fluidStorage;
    private final long capacityMb;

    public FluidTankBlockEntity(BlockPos pos, BlockState state, long capacityMb) {
        super(ModBlockEntities.FLUID_TANK, pos, state);
        this.capacityMb = capacityMb;
        this.fluidStorage = TotalityFluidStorage.fromMb(capacityMb, this);
    }

    public long getCapacityMb() { return capacityMb; }

    public void tick() {
        this.fluidStorage.tick();
    }

    public TotalityFluidStorage getFluidStorage() {
        return fluidStorage;
    }

    public @Nullable TotalityFluidStorage getFluidStorage(@Nullable Direction side) {
        return fluidStorage;
    }

    // World save
    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);
        fluidStorage.write(view);
        view.putLong("CapacityMb", capacityMb);
    }

    // World load AND client sync receive
    // Both paths call loadAdditional — fluid fields are always present
    // in both the full save and the update tag, so this is safe
    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);
        fluidStorage.read(view);
    }

    // Client sync send — returns only fluid data
    // Minecraft will wrap this CompoundTag in a TagValueInput
    // and pass it through loadWithComponents -> loadAdditional
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return fluidStorage.writeToTag(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        // Fluid data is already handled by saveAdditional/loadAdditional
        // but we need BLOCK_ENTITY_DATA component for the dropped item
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
}