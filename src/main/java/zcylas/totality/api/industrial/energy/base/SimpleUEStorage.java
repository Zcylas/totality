package zcylas.totality.api.industrial.energy.base;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.industrial.energy.UEStorage;

/**
 * A simple energy storage implementation with fixed capacity
 * and per-operation insertion and extraction limits.
 *
 * Override {@link #onCommit} to call markDirty() on your block entity.
 *
 * Example usage:
 * <pre>{@code
 * public class MyBlockEntity extends BlockEntity {
 *     public final SimpleUEStorage energyStorage = new SimpleUEStorage(10000, UETier.BASIC) {
 *         @Override
 *         protected void onCommit() {
 *             setChanged();
 *         }
 *     };
 * }
 * }</pre>
 */
public class SimpleUEStorage extends UEParticipant implements UEStorage {

    private long amount = 0;
    private final long capacity;
    private final long maxInsert;
    private final long maxExtract;


    /**
     * Create a storage with custom I/O rates.
     */
    public SimpleUEStorage(long capacity, long maxInsert, long maxExtract) {
        if (capacity < 0) throw new IllegalArgumentException("Capacity must be non-negative");
        if (maxInsert < 0) throw new IllegalArgumentException("maxInsert must be non-negative");
        if (maxExtract < 0) throw new IllegalArgumentException("maxExtract must be non-negative");
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
    }

    @Override
    public boolean supportsInsertion() {
        return maxInsert > 0;
    }

    @Override
    public long insert(long maxAmount, UETransaction transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
        long inserted = Math.min(maxInsert, Math.min(maxAmount, capacity - amount));
        if (inserted > 0) {
            updateSnapshots(transaction);
            amount += inserted;
        }
        return inserted;
    }

    @Override
    public boolean supportsExtraction() {
        return maxExtract > 0;
    }

    @Override
    public long extract(long maxAmount, UETransaction transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
        long extracted = Math.min(maxExtract, Math.min(maxAmount, amount));
        if (extracted > 0) {
            updateSnapshots(transaction);
            amount -= extracted;
        }
        return extracted;
    }

    @Override
    public long getAmount() { return amount; }

    @Override
    public long getCapacity() { return capacity; }

    @Override
    public long getMaxInsert() { return maxInsert; }

    @Override
    public long getMaxExtract() { return maxExtract; }

    /**
     * Directly set the stored amount, bypassing transactions.
     * Only use this for deserialization (NBT loading).
     */
    public void setAmountUnchecked(long amount) {
        this.amount = Math.min(amount, capacity);
    }

    @Override
    protected long createSnapshot() { return amount; }

    @Override
    protected void readSnapshot(long snapshot) { amount = snapshot; }

    public void saveToOutput(ValueOutput output) {
        output.putLong("Energy", amount);
    }

    public void loadFromInput(ValueInput input) {
        setAmountUnchecked(input.getLongOr("Energy", 0L));
    }

}