package zcylas.totality.api.energy.base;

import zcylas.totality.api.energy.base.UETransaction;

/**
 * Base class for objects that participate in UE transactions.
 * Extend this to get automatic snapshot/rollback behavior.
 */
public abstract class UEParticipant {

    private long snapshot = Long.MIN_VALUE;
    private boolean enlisted = false;

    /**
     * Call this before modifying any state in a transaction.
     * Automatically takes a snapshot if this is the first modification.
     */
    public final void updateSnapshots(UETransaction transaction) {
        if (!enlisted) {
            snapshot = createSnapshot();
            enlisted = true;
            transaction.enlist(this);
        }
    }

    void onFinalCommit() {
        enlisted = false;
        snapshot = Long.MIN_VALUE;
        onCommit();
    }

    void onAbort() {
        if (enlisted) {
            readSnapshot(snapshot);
            enlisted = false;
            snapshot = Long.MIN_VALUE;
        }
    }

    /**
     * Override to perform additional logic on commit,
     * such as marking a block entity dirty.
     */
    protected void onCommit() {}

    /**
     * Return a snapshot of the current state.
     * For most energy storages this is just the current amount.
     */
    protected abstract long createSnapshot();

    /**
     * Restore state from a previously created snapshot.
     */
    protected abstract void readSnapshot(long snapshot);
}