package zcylas.totality.api.energy.base;

import zcylas.totality.api.energy.UEStorage;

/**
 * An energy storage with infinite capacity and extraction.
 * Does not support insertion.
 *
 * Use INSTANCE instead of creating new instances.
 * Useful for creative machines, testing, and endgame items.
 *
 * Note: operations on this storage are transaction-safe but
 * snapshots are no-ops since state never changes.
 */
public class InfiniteUEStorage implements UEStorage {

    public static final InfiniteUEStorage INSTANCE = new InfiniteUEStorage();

    private InfiniteUEStorage() {}

    @Override
    public boolean supportsInsertion() { return false; }

    @Override
    public long insert(long maxAmount, UETransaction transaction) { return 0; }

    @Override
    public boolean supportsExtraction() { return true; }

    @Override
    public long extract(long maxAmount, UETransaction transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
        return maxAmount;
    }

    @Override
    public long getAmount() { return Long.MAX_VALUE; }

    @Override
    public long getCapacity() { return Long.MAX_VALUE; }

    @Override
    public long getMaxInsert() { return 0; }

    @Override
    public long getMaxExtract() { return Long.MAX_VALUE; }

    @Override
    public boolean isFull() { return false; }

    @Override
    public boolean isEmpty() { return false; }
}