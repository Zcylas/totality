package zcylas.totality.api.rpg.resources.state;

import zcylas.totality.api.rpg.resources.ResourceModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Preserves the raw persisted state of a resource whose definition is no longer registered (or
 * whose persisted model no longer matches the registered one) — e.g. an optional module/datapack
 * was temporarily removed. Never exposed to ordinary gameplay; written back unchanged on save so
 * no data is lost if the definition returns with a compatible model. See canonical §5.4.
 *
 * Always represented as a generic partition map, regardless of the orphaned resource's declared
 * {@link ResourceModel} — a scalar resource's current/overflow values are stored at partition key
 * {@code 0}. This keeps the current/overflow format uniform instead of needing per-model
 * branches. A scalar resource's regeneration remainder is carried separately via
 * {@link #scalarRegenerationRemainder()}, since it is meaningless for {@code PARTITIONED_POOL}
 * resources and is not itself a partition value.
 */
public final class OrphanedResourceState {

    private final ResourceModel model;
    private final Map<Integer, Long> currentByPartition = new LinkedHashMap<>();
    private final Map<Integer, Long> overflowByPartition = new LinkedHashMap<>();
    private long scalarRegenerationRemainder;

    public OrphanedResourceState(ResourceModel model) {
        this.model = model;
    }

    public ResourceModel model() {
        return model;
    }

    public void putCurrent(int partition, long value) {
        currentByPartition.put(partition, value);
    }

    public long getCurrent(int partition) {
        return currentByPartition.getOrDefault(partition, 0L);
    }

    public void putOverflow(int partition, long value) {
        overflowByPartition.put(partition, value);
    }

    public long getOverflow(int partition) {
        return overflowByPartition.getOrDefault(partition, 0L);
    }

    /** Only meaningful when {@link #model()} is {@link ResourceModel#SCALAR}. */
    public long scalarRegenerationRemainder() {
        return scalarRegenerationRemainder;
    }

    public void setScalarRegenerationRemainder(long value) {
        this.scalarRegenerationRemainder = value;
    }

    /**
     * The union of every partition key present in either the current or overflow map — a
     * partition holding only an overflow value (no current entry) is still a real partition and
     * must not be dropped by callers iterating this set (e.g. persistence/sync write paths).
     */
    public Set<Integer> partitions() {
        if (overflowByPartition.isEmpty()) {
            return Collections.unmodifiableSet(currentByPartition.keySet());
        }
        Set<Integer> union = new LinkedHashSet<>(currentByPartition.keySet());
        union.addAll(overflowByPartition.keySet());
        return Collections.unmodifiableSet(union);
    }

    public Map<Integer, Long> currentByPartition() {
        return Collections.unmodifiableMap(currentByPartition);
    }

    public Map<Integer, Long> overflowByPartition() {
        return Collections.unmodifiableMap(overflowByPartition);
    }

    /** Produces an independent copy — no map or field is shared with the original. */
    public OrphanedResourceState copy() {
        OrphanedResourceState copy = new OrphanedResourceState(model);
        copy.currentByPartition.putAll(this.currentByPartition);
        copy.overflowByPartition.putAll(this.overflowByPartition);
        copy.scalarRegenerationRemainder = this.scalarRegenerationRemainder;
        return copy;
    }
}
