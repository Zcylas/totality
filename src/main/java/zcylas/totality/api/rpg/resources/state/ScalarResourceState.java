package zcylas.totality.api.rpg.resources.state;

import zcylas.totality.api.rpg.resources.ResourceModel;

/**
 * State for a {@link ResourceModel#SCALAR} resource: a single bounded value plus optional
 * overflow and fractional-regeneration remainder. See canonical §6.1.
 *
 * Pure data holder — no clamping, spending, or regeneration logic. That belongs to the
 * transaction/strategy service introduced in a later phase, not this Phase 1 foundation.
 */
public final class ScalarResourceState implements ResourceState {

    private long currentUnits;
    private long overflowUnits;
    private long regenerationRemainder;

    public ScalarResourceState(long currentUnits) {
        this(currentUnits, 0L, 0L);
    }

    public ScalarResourceState(long currentUnits, long overflowUnits, long regenerationRemainder) {
        this.currentUnits = currentUnits;
        this.overflowUnits = overflowUnits;
        this.regenerationRemainder = regenerationRemainder;
    }

    @Override
    public ResourceModel model() {
        return ResourceModel.SCALAR;
    }

    public long currentUnits() {
        return currentUnits;
    }

    public void setCurrentUnits(long value) {
        this.currentUnits = value;
    }

    public long overflowUnits() {
        return overflowUnits;
    }

    public void setOverflowUnits(long value) {
        this.overflowUnits = value;
    }

    public long regenerationRemainder() {
        return regenerationRemainder;
    }

    public void setRegenerationRemainder(long value) {
        this.regenerationRemainder = value;
    }

    public ScalarResourceState copy() {
        return new ScalarResourceState(currentUnits, overflowUnits, regenerationRemainder);
    }
}
