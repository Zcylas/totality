package zcylas.totality.api.industrial.energy;

import org.jspecify.annotations.Nullable;
import zcylas.totality.api.industrial.energy.base.UETransaction;

/**
 * Utility methods for working with UEStorage instances.
 */
public class UEStorageUtil {

    /**
     * Move up to maxAmount energy from one storage to another.
     * Opens its own transaction internally.
     *
     * @param from      The source storage. May be null.
     * @param to        The target storage. May be null.
     * @param maxAmount The maximum amount to move. Must be non-negative.
     * @return The amount of energy actually moved.
     */
    public static long move(@Nullable UEStorage from, @Nullable UEStorage to, long maxAmount) {
        if (from == null || to == null) return 0;
        if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
        if (!from.supportsExtraction() || !to.supportsInsertion()) return 0;

        try (UETransaction transaction = UETransaction.open()) {
            // Simulate extraction to see what's available
            long extractable;
            try (UETransaction simulation = UETransaction.openNested(transaction)) {
                extractable = from.extract(maxAmount, simulation);
                // Don't commit — this is just a simulation
            }

            if (extractable == 0) return 0;

            // Simulate insertion to see what's accepted
            long insertable;
            try (UETransaction simulation = UETransaction.openNested(transaction)) {
                insertable = to.insert(extractable, simulation);
                // Don't commit — this is just a simulation
            }

            if (insertable == 0) return 0;

            // Execute for real
            try (UETransaction execution = UETransaction.openNested(transaction)) {
                long extracted = from.extract(insertable, execution);
                long inserted = to.insert(extracted, execution);

                if (extracted == inserted && extracted > 0) {
                    execution.commit();
                    transaction.commit();
                    return inserted;
                }
                // Mismatch — abort everything
            }
        }
        return 0;
    }

    /**
     * Move up to maxAmount energy as part of an existing transaction.
     *
     * @param from        The source storage. May be null.
     * @param to          The target storage. May be null.
     * @param maxAmount   The maximum amount to move. Must be non-negative.
     * @param transaction The transaction to operate within.
     * @return The amount of energy actually moved.
     */
    public static long move(
            @Nullable UEStorage from,
            @Nullable UEStorage to,
            long maxAmount,
            UETransaction transaction
    ) {
        if (from == null || to == null) return 0;
        if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
        if (!from.supportsExtraction() || !to.supportsInsertion()) return 0;

        // Simulate extraction
        long extractable;
        try (UETransaction simulation = UETransaction.openNested(transaction)) {
            extractable = from.extract(maxAmount, simulation);
        }

        if (extractable == 0) return 0;

        // Simulate insertion
        long insertable;
        try (UETransaction simulation = UETransaction.openNested(transaction)) {
            insertable = to.insert(extractable, simulation);
        }

        if (insertable == 0) return 0;

        // Execute for real
        try (UETransaction execution = UETransaction.openNested(transaction)) {
            long extracted = from.extract(insertable, execution);
            long inserted = to.insert(extracted, execution);

            if (extracted == inserted && extracted > 0) {
                execution.commit();
                return inserted;
            }
        }

        return 0;
    }

    /**
     * Return true if the given item stack implements UEItem.
     */
    public static boolean isEnergyItem(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof UEItem;
    }

    private UEStorageUtil() {}
}