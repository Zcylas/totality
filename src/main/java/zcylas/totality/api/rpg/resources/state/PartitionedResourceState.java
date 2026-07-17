package zcylas.totality.api.rpg.resources.state;

import zcylas.totality.api.rpg.resources.ResourceModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * State for a {@link ResourceModel#PARTITIONED_POOL} resource: independent current/overflow
 * counts keyed by an integer partition (spell slot tier, Hit Die size, ...). See canonical §6.2.
 *
 * Pure data holder — partition validity/labels/spending policy belong to the owning system's
 * partition descriptor and the transaction service, not this Phase 1 foundation. This correction
 * does not invent any gameplay semantics for overflow; it concerns lossless state representation
 * only.
 */
public final class PartitionedResourceState implements ResourceState {

    private final Map<Integer, Long> currentByPartition = new LinkedHashMap<>();
    private final Map<Integer, Long> overflowByPartition = new LinkedHashMap<>();

    @Override
    public ResourceModel model() {
        return ResourceModel.PARTITIONED_POOL;
    }

    public long getCurrent(int partition) {
        return currentByPartition.getOrDefault(partition, 0L);
    }

    public void setCurrent(int partition, long value) {
        currentByPartition.put(partition, value);
    }

    public long getOverflow(int partition) {
        return overflowByPartition.getOrDefault(partition, 0L);
    }

    public void setOverflow(int partition, long value) {
        overflowByPartition.put(partition, value);
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

    public PartitionedResourceState copy() {
        PartitionedResourceState copy = new PartitionedResourceState();
        copy.currentByPartition.putAll(this.currentByPartition);
        copy.overflowByPartition.putAll(this.overflowByPartition);
        return copy;
    }
}
