package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.sync.ClientResourceSyncState;
import zcylas.totality.api.rpg.resources.sync.ClientResyncRequestGate;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;

import java.util.Optional;

/**
 * Test-only {@link GenericSyncResourceAccess} backed by real, freshly-constructed Phase 3A pure
 * state objects ({@link ClientResourceSyncState}, {@link ClientResyncRequestGate}) rather than the
 * process-wide {@code ClientResourceSyncManager} singleton — lets tests drive full/delta application
 * and resync-gate transitions exactly like {@code ClientResourceSyncStateTest}/
 * {@code ClientResyncRequestGateTest} already do, without touching any shared global state.
 */
final class FakeGenericSyncResourceAccess implements GenericSyncResourceAccess {

    final ClientResourceSyncState state = new ClientResourceSyncState();
    final ClientResyncRequestGate gate = new ClientResyncRequestGate();

    @Override
    public boolean hasSynced() {
        return state.hasSynced();
    }

    @Override
    public boolean isResyncPending() {
        return gate.isPending();
    }

    @Override
    public Optional<ResourceScalarWireSnapshot> scalar(Identifier resourceId) {
        return state.scalar(resourceId);
    }

    @Override
    public Optional<ResourcePartitionedWireSnapshot> partitioned(Identifier resourceId) {
        return state.partitioned(resourceId);
    }
}
