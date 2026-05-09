package zcylas.totality.api.industrial.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import zcylas.totality.api.industrial.energy.base.SimpleSidedUEContainer;
import zcylas.totality.api.industrial.energy.base.UETransaction;

public class UETransferTicker {

    /**
     * Call this from a block entity's tick to push energy to adjacent blocks.
     * Only pushes from OUTPUT faces.
     */
    public static void pushToNeighbors(ServerLevel level, BlockPos pos,
                                       BlockEntity be, SimpleSidedUEContainer storage) {
        if (storage.isEmpty()) return;

        for (Direction dir : Direction.values()) {
            if (!storage.getSideMode(dir).allowsExtraction()) continue;

            BlockPos neighborPos = pos.relative(dir);
            UEStorage neighbor = UEComponents.SIDED_STORAGE.find(
                    level, neighborPos, dir.getOpposite());
            if (neighbor == null || !neighbor.supportsInsertion()) continue;

            long toMove = Math.min(storage.getMaxExtract(),
                    neighbor.getCapacity() - neighbor.getAmount());
            if (toMove <= 0) continue;

            try (UETransaction transaction = UETransaction.open()) {
                long extracted = storage.getSideStorage(dir).extract(toMove, transaction);
                if (extracted > 0) {
                    long inserted = neighbor.insert(extracted, transaction);
                    if (inserted > 0) {
                        transaction.commit();
                    }
                }
            }
        }
    }

    private UETransferTicker() {}
}