package zcylas.totality.api.industrial.energy.base;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.industrial.energy.UEComponents;
import zcylas.totality.api.industrial.energy.UEItem;
import zcylas.totality.api.industrial.energy.UEStorage;

/**
 * A UEStorage implementation backed by an ItemStack's data component.
 * Participates in transactions via snapshot/rollback of the stored energy value.
 */
public class UEItemStorage extends UEParticipant implements UEStorage {

    private final ItemStack stack;
    private final UEItem item;

    // Cached current amount to avoid repeated component lookups
    // and to allow transaction rollback without touching the stack
    private long cachedAmount;

    public UEItemStorage(ItemStack stack, UEItem item) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Stack must not be empty");
        this.stack = stack;
        this.item = item;
        this.cachedAmount = stack.getOrDefault(UEComponents.ENERGY, 0L);
    }

    @Override
    public boolean supportsInsertion() {
        return item.getEnergyMaxInput(stack) > 0;
    }

    @Override
    public long insert(long maxAmount, UETransaction transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
        if (!supportsInsertion()) return 0;

        long capacity = item.getEnergyCapacity(stack);
        long inserted = Math.min(
                item.getEnergyMaxInput(stack),
                Math.min(maxAmount, capacity - cachedAmount)
        );

        if (inserted > 0) {
            updateSnapshots(transaction);
            cachedAmount += inserted;
        }

        return inserted;
    }

    @Override
    public boolean supportsExtraction() {
        return item.getEnergyMaxOutput(stack) > 0;
    }

    @Override
    public long extract(long maxAmount, UETransaction transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
        if (!supportsExtraction()) return 0;

        long extracted = Math.min(
                item.getEnergyMaxOutput(stack),
                Math.min(maxAmount, cachedAmount)
        );

        if (extracted > 0) {
            updateSnapshots(transaction);
            cachedAmount -= extracted;
        }

        return extracted;
    }

    @Override
    public long getAmount() { return cachedAmount; }

    @Override
    public long getCapacity() { return item.getEnergyCapacity(stack); }

    @Override
    public long getMaxInsert() { return item.getEnergyMaxInput(stack); }

    @Override
    public long getMaxExtract() { return item.getEnergyMaxOutput(stack); }

    @Override
    protected long createSnapshot() { return cachedAmount; }

    @Override
    protected void readSnapshot(long snapshot) {
        cachedAmount = snapshot;
        // Write rollback value back to the stack component
        writeToStack();
    }

    @Override
    protected void onCommit() {
        // Write committed value to the stack component
        writeToStack();
    }

    private void writeToStack() {
        if (cachedAmount <= 0) {
            stack.remove(UEComponents.ENERGY);
        } else {
            stack.set(UEComponents.ENERGY, cachedAmount);
        }
    }
}