package zcylas.totality.blockentity.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.industrial.energy.HasSidedEnergy;
import zcylas.totality.api.industrial.energy.UEStorage;
import zcylas.totality.api.industrial.energy.base.UETransaction;
import zcylas.totality.block.energy.CableBlock;
import zcylas.totality.init.ModBlockEntities;

import java.util.ArrayList;
import java.util.List;

public class CableBlockEntity extends BlockEntity {

    private long storedEnergy = 0;
    private @Nullable List<Direction> targets = null;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE, pos, state);
    }

    public void onNeighborChanged() {
        targets = null;
    }

    private long getTransferPerTick() {
        if (level == null) return 1000;
        if (level.getBlockState(worldPosition).getBlock() instanceof CableBlock cable) {
            return cable.getTransferPerTick();
        }
        return 1000;
    }

    private long getCapacity() {
        return getTransferPerTick() * 4;
    }

    private @Nullable UEStorage getAdjacentStorage(Direction dir) {
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(worldPosition.relative(dir));
        if (be instanceof HasSidedEnergy hasSided) {
            return hasSided.getEnergy().getSideStorage(dir.getOpposite());
        }
        return null;
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        if (targets == null) {
            targets = new ArrayList<>();
            BlockState newState = getBlockState();

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = worldPosition.relative(dir);
                boolean connected = false;

                if (level.getBlockState(neighborPos).getBlock() instanceof CableBlock) {
                    connected = true;
                } else {
                    UEStorage storage = getAdjacentStorage(dir);
                    if (storage != null) {
                        connected = true;
                        targets.add(dir);
                    }
                }

                newState = newState.setValue(CableBlock.PROPERTY_MAP.get(dir), connected);
            }

            serverLevel.setBlockAndUpdate(worldPosition, newState);
        }

        long transferRate = getTransferPerTick();

        // Pull from OUTPUT-facing adjacent machines into buffer
        for (Direction dir : Direction.values()) {
            if (storedEnergy >= getCapacity()) break;
            if (level.getBlockState(worldPosition.relative(dir)).getBlock() instanceof CableBlock) continue;
            UEStorage storage = getAdjacentStorage(dir);
            if (storage != null && storage.supportsExtraction()) {
                long canExtract = Math.min(transferRate, getCapacity() - storedEnergy);
                if (canExtract > 0) {
                    try (UETransaction tx = UETransaction.open()) {
                        long extracted = storage.extract(canExtract, tx);
                        storedEnergy += extracted;
                        tx.commit();
                    }
                }
            }
        }

        if (storedEnergy <= 0 || targets.isEmpty()) return;

        // Push from buffer into INPUT-facing adjacent machines
        long toDistribute = Math.min(storedEnergy, transferRate);
        long perTarget = Math.max(1, toDistribute / targets.size());

        for (Direction dir : targets) {
            if (storedEnergy <= 0) break;
            UEStorage storage = getAdjacentStorage(dir);
            if (storage != null && storage.supportsInsertion()) {
                long toInsert = Math.min(perTarget, storedEnergy);
                try (UETransaction tx = UETransaction.open()) {
                    long inserted = storage.insert(toInsert, tx);
                    storedEnergy -= inserted;
                    tx.commit();
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("energy", storedEnergy);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storedEnergy = input.getLongOr("energy", 0L);
    }
}