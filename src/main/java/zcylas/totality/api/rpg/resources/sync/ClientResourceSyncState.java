package zcylas.totality.api.rpg.resources.sync;

import net.minecraft.resources.Identifier;
import zcylas.totality.networking.resource.ResourceDeltaSyncPayload;
import zcylas.totality.networking.resource.ResourceFullSyncPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Phase 3A internal, receive-side mirror of the generic Resource synchronization wire state.
 * Pure logic — no Minecraft client/network dependency — so full/delta/revision behavior can be
 * unit-tested directly.
 *
 * <p>This is <b>not</b> the trusted client Resource query façade: nothing in Phase 3A reads this
 * class for gameplay, HUD, or tooltip decisions. It exists only to make the packet contract real
 * and testable, per the Phase 3A task's client-boundary constraints. The public façade is Phase 3B
 * scope.
 */
public final class ClientResourceSyncState {

    public enum ApplyResult {
        APPLIED_FULL,
        APPLIED_DELTA,
        STALE_IGNORED,
        REVISION_GAP,
        INCOMPATIBLE_SCHEMA,
        MALFORMED
    }

    private long revision = -1;
    private final Map<Identifier, ResourceScalarWireSnapshot> scalars = new LinkedHashMap<>();
    private final Map<Identifier, ResourcePartitionedWireSnapshot> partitioned = new LinkedHashMap<>();

    public boolean hasSynced() {
        return revision >= 0;
    }

    public long revision() {
        return revision;
    }

    public Optional<ResourceScalarWireSnapshot> scalar(Identifier resourceId) {
        return Optional.ofNullable(scalars.get(resourceId));
    }

    public Optional<ResourcePartitionedWireSnapshot> partitioned(Identifier resourceId) {
        return Optional.ofNullable(partitioned.get(resourceId));
    }

    /**
     * Applies a full snapshot: replaces the entire synchronized view (entries omitted from
     * {@code payload} are absent afterward) and establishes a fresh revision base — but only once
     * the payload has been fully validated, and only if it is not stale.
     *
     * <p>A full snapshot carrying a revision lower than the one already held is a stale/out-of-order
     * packet (e.g. reordered on the wire) and is ignored outright: the existing maps and revision
     * are preserved untouched, exactly like a rejected delta. A full snapshot carrying the same
     * revision as the one already held is treated as an idempotent authoritative replacement — the
     * same view resent (e.g. by an explicit resync) applies cleanly and is reported as
     * {@link ApplyResult#APPLIED_FULL}, not as a no-op, so callers cannot distinguish "resent" from
     * "first applied" and mishandle either. Only a strictly higher revision or the very first full
     * snapshot ever received also replaces the view — both go through the same code path below.
     */
    public ApplyResult applyFull(ResourceFullSyncPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.schemaVersion() != ResourceSyncProtocol.PROTOCOL_VERSION) {
            return ApplyResult.INCOMPATIBLE_SCHEMA;
        }
        if (payload.revision() < 0) {
            return ApplyResult.MALFORMED;
        }
        if (hasSynced() && payload.revision() < revision) {
            return ApplyResult.STALE_IGNORED;
        }
        Map<Identifier, ResourceScalarWireSnapshot> newScalars = new LinkedHashMap<>();
        for (ResourceScalarWireSnapshot s : payload.scalars()) {
            newScalars.put(s.resourceId(), s);
        }
        Map<Identifier, ResourcePartitionedWireSnapshot> newPartitioned = new LinkedHashMap<>();
        for (ResourcePartitionedWireSnapshot p : payload.partitioned()) {
            newPartitioned.put(p.resourceId(), p);
        }
        scalars.clear();
        scalars.putAll(newScalars);
        partitioned.clear();
        partitioned.putAll(newPartitioned);
        revision = payload.revision();
        return ApplyResult.APPLIED_FULL;
    }

    /**
     * Applies a revisioned delta only if {@code payload.baseRevision()} matches the currently held
     * revision. A stale delta (older base) is ignored; a gap (newer base than expected, or no full
     * snapshot received yet) is reported so the caller can request a resync. No partial mutation
     * occurs on any non-{@code APPLIED_DELTA} outcome.
     *
     * <p>Defensively re-verifies the exact-succession contract ({@code ResourceDeltaSyncPayload}'s
     * own constructor already enforces {@code revision == baseRevision + 1} and rejects
     * {@code baseRevision == Long.MAX_VALUE}) independently of however the payload instance reached
     * this method — the same guard against a larger-than-one revision jump and against overflow is
     * applied here too, never trusting the payload's constructor alone.
     */
    public ApplyResult applyDelta(ResourceDeltaSyncPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.schemaVersion() != ResourceSyncProtocol.PROTOCOL_VERSION) {
            return ApplyResult.INCOMPATIBLE_SCHEMA;
        }
        if (payload.baseRevision() == Long.MAX_VALUE || payload.revision() != payload.baseRevision() + 1) {
            return ApplyResult.MALFORMED;
        }
        if (!hasSynced()) {
            return ApplyResult.REVISION_GAP;
        }
        if (payload.baseRevision() < revision) {
            return ApplyResult.STALE_IGNORED;
        }
        if (payload.baseRevision() > revision) {
            return ApplyResult.REVISION_GAP;
        }
        for (ResourceScalarWireSnapshot s : payload.upsertScalars()) {
            scalars.put(s.resourceId(), s);
            partitioned.remove(s.resourceId());
        }
        for (ResourcePartitionedWireSnapshot p : payload.upsertPartitioned()) {
            partitioned.put(p.resourceId(), p);
            scalars.remove(p.resourceId());
        }
        for (Identifier id : payload.invalidated()) {
            scalars.remove(id);
            partitioned.remove(id);
        }
        revision = payload.revision();
        return ApplyResult.APPLIED_DELTA;
    }

    /** Clears all synchronized state — disconnect, world change, or connection replacement. */
    public void clear() {
        scalars.clear();
        partitioned.clear();
        revision = -1;
    }
}
