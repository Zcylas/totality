package zcylas.totality.api.energy.base;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an atomic unit of energy transfer.
 * Use try-with-resources to ensure transactions are always closed.
 *
 * Example usage:
 * <pre>{@code
 * try (UETransaction transaction = UETransaction.open()) {
 *     long moved = UEStorageUtil.move(from, to, amount, transaction);
 *     if (moved > 0) transaction.commit();
 * } // auto-closes here, aborts if not committed
 * }</pre>
 */
public class UETransaction implements AutoCloseable {

    private static final ThreadLocal<UETransaction> CURRENT = new ThreadLocal<>();

    private final @Nullable UETransaction parent;
    private final List<UEParticipant> participants = new ArrayList<>();
    private boolean committed = false;
    private boolean closed = false;

    private UETransaction(@Nullable UETransaction parent) {
        this.parent = parent;
    }

    /**
     * Open a new transaction. If a transaction is already open on this
     * thread, the new transaction will be nested inside it.
     */
    public static UETransaction open() {
        UETransaction transaction = new UETransaction(CURRENT.get());
        CURRENT.set(transaction);
        return transaction;
    }

    /**
     * Open a nested transaction inside the given parent, or a new
     * top-level transaction if parent is null.
     */
    public static UETransaction openNested(@Nullable UETransaction parent) {
        if (parent == null) return open();
        UETransaction transaction = new UETransaction(parent);
        CURRENT.set(transaction);
        return transaction;
    }

    /**
     * Enlist a participant in this transaction.
     * Called automatically by {@link UEParticipant#updateSnapshots}.
     */
    void enlist(UEParticipant participant) {
        if (closed) throw new IllegalStateException("Cannot enlist in a closed transaction");
        participants.add(participant);
    }

    /**
     * Mark this transaction as successful.
     * Changes will be finalized when the transaction is closed.
     */
    public void commit() {
        if (closed) throw new IllegalStateException("Cannot commit a closed transaction");
        committed = true;
    }

    /**
     * Return the currently open transaction on this thread, or null.
     */
    @Nullable
    public static UETransaction getCurrent() {
        return CURRENT.get();
    }

    @Override
    public void close() {
        if (closed) throw new IllegalStateException("Transaction is already closed");
        closed = true;
        CURRENT.set(parent);

        if (committed) {
            if (parent == null) {
                // Outermost transaction — finalize all participants
                for (UEParticipant participant : participants) {
                    participant.onFinalCommit();
                }
            } else {
                // Nested — promote participants to parent
                for (UEParticipant participant : participants) {
                    parent.enlist(participant);
                }
            }
        } else {
            // Aborted — revert all participants
            for (UEParticipant participant : participants) {
                participant.onAbort();
            }
        }
    }
}