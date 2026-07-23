package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionWireEntry;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Mana/Stamina/spell-slots/Rage reader for the client Resource façade. Reads only through the
 * injected {@link GenericSyncResourceAccess} — the narrow read-only bridge over the Phase 3A
 * {@code ClientResourceSyncState} — never the mutable state object itself, and never
 * {@code PlayerResourceService.query(...)}: that server-shared query service is structurally
 * unavailable client-side for these four {@code LEGACY_BESPOKE_SYNCHRONIZATION} resources (see the
 * Phase 3B readiness audit §2.5). This reader is pure/unit-testable with a synthetic
 * {@link GenericSyncResourceAccess} implementation.
 */
public final class GenericSyncClientResourceReader implements ClientResourceReader {

    private final GenericSyncResourceAccess access;

    public GenericSyncClientResourceReader(GenericSyncResourceAccess access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    @Override
    public ClientResourceQueryResult query(PlayerResourceDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Identifier id = definition.id();
        if (!access.hasSynced()) {
            return ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET);
        }
        ClientResourceTrust trust = access.isResyncPending() ? ClientResourceTrust.PENDING_RESYNC : ClientResourceTrust.FRESH;
        Optional<ResourceScalarWireSnapshot> scalar = access.scalar(id);
        Optional<ResourcePartitionedWireSnapshot> partitioned = access.partitioned(id);

        return switch (definition.model()) {
            case SCALAR -> {
                if (partitioned.isPresent()) {
                    // Stored synchronized shape contradicts the canonical definition — never
                    // silently coerced or flattened.
                    yield ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.MODEL_MISMATCH);
                }
                if (scalar.isEmpty()) {
                    // Registered, synchronized, and genuinely absent from this player's view (e.g. no
                    // Rage pool granted yet) — never fabricated as a 0/0 success.
                    yield ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER);
                }
                ResourceScalarWireSnapshot wire = scalar.get();
                if (wire.unitScale() != definition.unitScale()) {
                    // Defense-in-depth (external review correction, 2026-07-23): the wire snapshot's
                    // unit scale contradicts the canonical definition — never converted, reinterpreted,
                    // or trusted at either scale, and never a mutation of the Phase 3A wire state.
                    yield ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.MODEL_MISMATCH);
                }
                yield toScalar(wire, trust);
            }
            case PARTITIONED_POOL -> {
                if (scalar.isPresent()) {
                    yield ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.MODEL_MISMATCH);
                }
                if (partitioned.isEmpty()) {
                    yield ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER);
                }
                ResourcePartitionedWireSnapshot wire = partitioned.get();
                if (wire.unitScale() != definition.unitScale()) {
                    yield ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.MODEL_MISMATCH);
                }
                yield toPartitioned(wire, trust);
            }
        };
    }

    private static ClientResourceQueryResult.Scalar toScalar(ResourceScalarWireSnapshot wire, ClientResourceTrust trust) {
        return new ClientResourceQueryResult.Scalar(
                wire.resourceId(), wire.currentUnits(), wire.maximumUnits(), wire.overflowUnits(), wire.unitScale(),
                ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, trust);
    }

    private static ClientResourceQueryResult.Partitioned toPartitioned(ResourcePartitionedWireSnapshot wire, ClientResourceTrust trust) {
        List<ClientResourceQueryResult.Partitioned.Partition> partitions = new ArrayList<>(wire.partitions().size());
        for (ResourcePartitionWireEntry entry : wire.partitions()) {
            partitions.add(new ClientResourceQueryResult.Partitioned.Partition(
                    entry.partition(), entry.currentUnits(), entry.maximumUnits(), entry.overflowUnits()));
        }
        return ClientResourceQueryResult.Partitioned.of(
                wire.resourceId(), partitions, wire.unitScale(), ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, trust);
    }
}
