package zcylas.totality.networking.resource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.client.GenericSyncResourceAccess;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;

import java.util.Optional;

/**
 * The narrowest possible read-only bridge from the Phase 3B client Resource façade to the Phase 3A
 * client synchronization state. Lives in this package specifically so it can call
 * {@link ClientResourceSyncManager}'s package-private {@code state()}/{@code isResyncPending()}
 * accessors directly — {@link ClientResourceSyncManager#state()} itself stays package-private and is
 * never made public (per the Phase 3B-1 task's explicit instruction). This class exposes only
 * single-resource, side-effect-free lookups: it cannot apply a packet, advance a revision, clear
 * synchronization state, request a resync, or mutate any Resource.
 */
@Environment(EnvType.CLIENT)
public final class ClientResourceSyncBridge implements GenericSyncResourceAccess {

    public static final ClientResourceSyncBridge INSTANCE = new ClientResourceSyncBridge();

    private ClientResourceSyncBridge() {}

    @Override
    public boolean hasSynced() {
        return ClientResourceSyncManager.state().hasSynced();
    }

    @Override
    public boolean isResyncPending() {
        return ClientResourceSyncManager.isResyncPending();
    }

    @Override
    public Optional<ResourceScalarWireSnapshot> scalar(Identifier resourceId) {
        return ClientResourceSyncManager.state().scalar(resourceId);
    }

    @Override
    public Optional<ResourcePartitionedWireSnapshot> partitioned(Identifier resourceId) {
        return ClientResourceSyncManager.state().partitioned(resourceId);
    }
}
