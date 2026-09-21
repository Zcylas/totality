package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;

import java.util.Optional;

/**
 * The narrowest possible read-only bridge from the client Resource façade to the Phase 3A generic
 * client synchronization state. Deliberately exposes only immutable single-resource lookups and
 * synchronization metadata — never the mutable {@code ClientResourceSyncState} object itself, and
 * never a method that could apply a packet, advance a revision, clear synchronization state, request
 * a resync, or mutate any Resource. See the Phase 3B readiness audit §6 ("Narrow Phase 3A read-only
 * bridge").
 *
 * <p>References only common, non-client-only wire-shape types ({@link ResourceScalarWireSnapshot},
 * {@link ResourcePartitionedWireSnapshot}) — this interface itself has no Minecraft client/network
 * dependency and is safe to implement with a plain-Java test double.
 */
public interface GenericSyncResourceAccess {

    /** Whether a full snapshot has ever been accepted this session. */
    boolean hasSynced();

    /** Whether a resync request is currently in flight or awaiting retry. */
    boolean isResyncPending();

    /** The last accepted scalar snapshot for {@code resourceId}, if any. */
    Optional<ResourceScalarWireSnapshot> scalar(Identifier resourceId);

    /** The last accepted partitioned snapshot for {@code resourceId}, if any. */
    Optional<ResourcePartitionedWireSnapshot> partitioned(Identifier resourceId);
}
