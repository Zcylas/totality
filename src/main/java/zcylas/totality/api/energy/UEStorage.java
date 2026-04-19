package zcylas.totality.api.energy;

import zcylas.totality.api.energy.base.UETransaction;

public interface UEStorage {

    /**
     * Return false if calling {@link #insert} will always return 0, true otherwise or in doubt.
     */
    default boolean supportsInsertion() {
        return true;
    }

    /**
     * Try to insert up to maxAmount of energy into this storage.
     * @param maxAmount The maximum amount to insert. Must be non-negative.
     * @param transaction The transaction this operation is part of.
     * @return The amount actually inserted, between 0 and maxAmount.
     */
    long insert(long maxAmount, UETransaction transaction);

    /**
     * Return false if calling {@link #extract} will always return 0, true otherwise or in doubt.
     */
    default boolean supportsExtraction() {
        return true;
    }

    /**
     * Try to extract up to maxAmount of energy from this storage.
     * @param maxAmount The maximum amount to extract. Must be non-negative.
     * @param transaction The transaction this operation is part of.
     * @return The amount actually extracted, between 0 and maxAmount.
     */
    long extract(long maxAmount, UETransaction transaction);

    /**
     * Return the current amount of energy stored.
     */
    long getAmount();

    /**
     * Return the maximum amount of energy that can be stored.
     */
    long getCapacity();

    /**
     * Return the maximum amount of energy that can be inserted per operation.
     * Defaults to capacity if not overridden.
     */
    default long getMaxInsert() {
        return getCapacity();
    }

    /**
     * Return the maximum amount of energy that can be extracted per operation.
     * Defaults to capacity if not overridden.
     */
    default long getMaxExtract() {
        return getCapacity();
    }

    /**
     * Return a fraction between 0.0 and 1.0 representing how full this storage is.
     * Useful for rendering energy bars in GUIs.
     */
    default double getFillFraction() {
        if (getCapacity() == 0) return 0.0;
        return (double) getAmount() / getCapacity();
    }

    /**
     * Return true if this storage is completely full.
     */
    default boolean isFull() {
        return getAmount() >= getCapacity();
    }

    /**
     * Return true if this storage is completely empty.
     */
    default boolean isEmpty() {
        return getAmount() == 0;
    }

    /**
     * An always-empty storage that supports neither insertion nor extraction.
     */
    UEStorage EMPTY = new UEStorage() {
        @Override public boolean supportsInsertion() { return false; }
        @Override public long insert(long maxAmount, UETransaction transaction) { return 0; }
        @Override public boolean supportsExtraction() { return false; }
        @Override public long extract(long maxAmount, UETransaction transaction) { return 0; }
        @Override public long getAmount() { return 0; }
        @Override public long getCapacity() { return 0; }
    };
}