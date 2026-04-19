package zcylas.totality.api.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

/**
 * A single-fluid storage backed by Fabric's SingleFluidStorage.
 * Handles dirty marking and NBT serialization.
 *
 * Capacity can be set via FluidTier or directly in droplets.
 * Use fromTier() or fromMb() or fromDroplets() to create instances.
 *
 * Override onCommit() to call setChanged() on your block entity.
 */
public class TotalityFluidStorage extends SingleFluidStorage {

    private final long capacityDroplets;
    private final BlockEntity blockEntity;
    private boolean isDirty = false;

    private TotalityFluidStorage(long capacityDroplets, BlockEntity blockEntity) {
        this.capacityDroplets = capacityDroplets;
        this.blockEntity = blockEntity;
    }

    public static TotalityFluidStorage fromTier(FluidTier tier, BlockEntity blockEntity) {
        return new TotalityFluidStorage(tier.getCapacityDroplets(), blockEntity);
    }

    public static TotalityFluidStorage fromMb(long capacityMb, BlockEntity blockEntity) {
        return new TotalityFluidStorage(capacityMb * 81L, blockEntity);
    }

    public static TotalityFluidStorage fromDroplets(long capacityDroplets, BlockEntity blockEntity) {
        return new TotalityFluidStorage(capacityDroplets, blockEntity);
    }

    @Override
    protected long getCapacity(FluidVariant variant) {
        return capacityDroplets;
    }

    @Override
    protected void onFinalCommit() {
        super.onFinalCommit();
        isDirty = true;
    }

    /**
     * Call this every tick on the server side to sync changes.
     */
    public void tick() {
        if (isDirty && blockEntity.hasLevel() && !blockEntity.getLevel().isClientSide()) {
            isDirty = false;
            blockEntity.setChanged();
            syncToClients();
        }
    }

    /**
     * Sends block update to clients so they can re-render the fluid level.
     */
    private void syncToClients() {
        var level = blockEntity.getLevel();
        if (level != null && !level.isClientSide()) {
            var pos = blockEntity.getBlockPos();
            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // Convenience methods
    public long getCapacityDroplets() { return capacityDroplets; }
    public long getCapacityMb() { return capacityDroplets / 81L; }
    public long getAmountMb() { return amount / 81L; }
    public boolean isEmpty() { return amount == 0 || variant.isBlank(); }
    public boolean isFull() { return amount >= capacityDroplets; }

    public double getFillFraction() {
        if (capacityDroplets == 0) return 0.0;
        return (double) amount / capacityDroplets;
    }

    // NBT serialization
    public void write(ValueOutput view) {
        view.putLong("Amount", amount);
        view.store("Fluid", FluidVariant.CODEC, variant);
    }

    public void read(ValueInput view) {
        amount = view.getLongOr("Amount", 0L);
        variant = view.read("Fluid", FluidVariant.CODEC)
                .orElse(FluidVariant.blank());
    }

    /**
     * Marks dirty without a commit, for example after NBT load.
     */
    public void markDirty() {
        isDirty = true;
    }

    // Add these to TotalityFluidStorage

    public CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Amount", amount);

        FluidVariant.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), variant)
                .ifSuccess(nbt -> tag.put("Fluid", nbt));

        return tag;
    }

    public void readFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        amount = tag.getLongOr("Amount", 0L);

        variant = FluidVariant.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("Fluid"))
                .result()
                .orElse(FluidVariant.blank());
    }
}