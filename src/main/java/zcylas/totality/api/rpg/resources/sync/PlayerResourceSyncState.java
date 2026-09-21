package zcylas.totality.api.rpg.resources.sync;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure, per-player synchronization bookkeeping for the Phase 3A generic Resource sync contract:
 * the unified-view revision counter, the dirty set, the pending-full-snapshot flag, and the
 * last-known-sent wire state used to diff against. Deliberately free of any {@code ServerPlayer}/
 * networking dependency so the coalescing/diffing/revision behavior can be unit-tested directly —
 * see {@code zcylas.totality.networking.resource.ResourceSyncManager} for the impure orchestration
 * layer (queries, packet sends, lifecycle wiring) built on top of this class.
 */
public final class PlayerResourceSyncState {

    /** One resource's freshly queried result, already reduced to a client-safe wire shape (or
     *  absent). Produced by the impure caller from a {@code PlayerResourceService} query — this
     *  class only ever compares/stores these, it never queries anything itself. */
    public sealed interface ResourceOutcome permits ScalarOutcome, PartitionedOutcome, AbsentOutcome {}

    public record ScalarOutcome(ResourceScalarWireSnapshot snapshot) implements ResourceOutcome {}

    public record PartitionedOutcome(ResourcePartitionedWireSnapshot snapshot) implements ResourceOutcome {}

    /** The resource is currently omitted from the synchronized view — an unregistered/unavailable/
     *  uninitialized/not-yet-granted/failed query, or a resource intentionally excluded from generic
     *  sync (e.g. {@code NATIVE_SYNCHRONIZATION}-mirrored). Never "a valid zero snapshot". */
    public record AbsentOutcome() implements ResourceOutcome {}

    public record DeltaBatch(
            List<ResourceScalarWireSnapshot> upsertScalars,
            List<ResourcePartitionedWireSnapshot> upsertPartitioned,
            List<Identifier> invalidated
    ) {
        public boolean isEmpty() {
            return upsertScalars.isEmpty() && upsertPartitioned.isEmpty() && invalidated.isEmpty();
        }
    }

    public record FullBatch(
            List<ResourceScalarWireSnapshot> scalars,
            List<ResourcePartitionedWireSnapshot> partitioned
    ) {}

    private long revision = 0;
    private boolean fullSnapshotPending = false;
    private final Set<Identifier> dirty = new LinkedHashSet<>();
    private final Map<Identifier, ResourceScalarWireSnapshot> lastScalars = new LinkedHashMap<>();
    private final Map<Identifier, ResourcePartitionedWireSnapshot> lastPartitioned = new LinkedHashMap<>();

    public long revision() {
        return revision;
    }

    public void markDirty(Identifier resourceId) {
        dirty.add(resourceId);
    }

    public void scheduleFullSnapshot() {
        fullSnapshotPending = true;
    }

    public boolean isFullSnapshotPending() {
        return fullSnapshotPending;
    }

    public boolean hasPendingWork() {
        return fullSnapshotPending || !dirty.isEmpty();
    }

    /** The resource ids currently marked dirty — the caller queries exactly these, and only these,
     *  before calling {@link #computeDeltaAndApply}. Never all registered resources every tick. */
    public Set<Identifier> dirtyIds() {
        return Set.copyOf(dirty);
    }

    /**
     * Diffs {@code outcomes} (one entry per currently-dirty id) against the last-known-sent state,
     * applies the changed entries to that last-known state, clears the dirty set, and returns only
     * the entries that actually changed. An outcome identical to what was last sent produces no
     * upsert (no packet spam for a no-op mutation). Does not touch {@link #revision} — the caller
     * bumps it only when the returned batch is non-empty.
     */
    public DeltaBatch computeDeltaAndApply(Map<Identifier, ResourceOutcome> outcomes) {
        List<ResourceScalarWireSnapshot> upsertScalars = new ArrayList<>();
        List<ResourcePartitionedWireSnapshot> upsertPartitioned = new ArrayList<>();
        List<Identifier> invalidated = new ArrayList<>();

        for (Identifier id : dirty) {
            ResourceOutcome outcome = outcomes.get(id);
            if (outcome == null) {
                continue;
            }
            switch (outcome) {
                case ScalarOutcome scalarOutcome -> {
                    boolean wasPartitioned = lastPartitioned.remove(id) != null;
                    ResourceScalarWireSnapshot previous = lastScalars.get(id);
                    if (wasPartitioned || !scalarOutcome.snapshot().equals(previous)) {
                        upsertScalars.add(scalarOutcome.snapshot());
                        lastScalars.put(id, scalarOutcome.snapshot());
                    }
                }
                case PartitionedOutcome partitionedOutcome -> {
                    boolean wasScalar = lastScalars.remove(id) != null;
                    ResourcePartitionedWireSnapshot previous = lastPartitioned.get(id);
                    if (wasScalar || !partitionedOutcome.snapshot().equals(previous)) {
                        upsertPartitioned.add(partitionedOutcome.snapshot());
                        lastPartitioned.put(id, partitionedOutcome.snapshot());
                    }
                }
                case AbsentOutcome ignored -> {
                    boolean wasScalar = lastScalars.remove(id) != null;
                    boolean wasPartitioned = lastPartitioned.remove(id) != null;
                    if (wasScalar || wasPartitioned) {
                        invalidated.add(id);
                    }
                }
            }
        }

        dirty.clear();
        // Deterministic wire ordering: the dirty set iterates in insertion order, which depends on
        // incidental mutation order within the tick, not on resource identity — sort every list by
        // resource id (string form, matching applyFull's TreeMap-based ordering below) so the same
        // set of changes always serializes identically regardless of which mutation happened first.
        upsertScalars.sort(Comparator.comparing(s -> s.resourceId().toString()));
        upsertPartitioned.sort(Comparator.comparing(p -> p.resourceId().toString()));
        invalidated.sort(Comparator.comparing(Identifier::toString));
        return new DeltaBatch(List.copyOf(upsertScalars), List.copyOf(upsertPartitioned), List.copyOf(invalidated));
    }

    /**
     * Replaces the entire last-known-sent view with {@code outcomes} (one entry per resource
     * eligible for generic sync, success or absent). Clears the dirty set and the pending-full flag
     * — the full snapshot this batch feeds establishes a fresh baseline, superseding any pending
     * delta work. Does not touch {@link #revision}; the caller always bumps it when sending a full
     * snapshot.
     */
    public FullBatch applyFull(Map<Identifier, ResourceOutcome> outcomes) {
        lastScalars.clear();
        lastPartitioned.clear();
        // TreeMap keyed by string form for deterministic, reproducible wire ordering.
        Map<String, ResourceScalarWireSnapshot> scalarOrder = new TreeMap<>();
        Map<String, ResourcePartitionedWireSnapshot> partitionedOrder = new TreeMap<>();
        for (Map.Entry<Identifier, ResourceOutcome> entry : outcomes.entrySet()) {
            switch (entry.getValue()) {
                case ScalarOutcome scalarOutcome -> {
                    lastScalars.put(entry.getKey(), scalarOutcome.snapshot());
                    scalarOrder.put(entry.getKey().toString(), scalarOutcome.snapshot());
                }
                case PartitionedOutcome partitionedOutcome -> {
                    lastPartitioned.put(entry.getKey(), partitionedOutcome.snapshot());
                    partitionedOrder.put(entry.getKey().toString(), partitionedOutcome.snapshot());
                }
                case AbsentOutcome ignored -> {
                    // Omitted from the full view entirely.
                }
            }
        }
        dirty.clear();
        fullSnapshotPending = false;
        return new FullBatch(List.copyOf(scalarOrder.values()), List.copyOf(partitionedOrder.values()));
    }

    public void bumpRevision() {
        revision++;
    }
}
