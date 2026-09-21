package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.ClientResourceSource;
import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test-only fake driving {@link ClientResourceParityPoll} without a real Minecraft client — mirrors
 *  the established {@code FakeGenericSyncResourceAccess} precedent from Phase 3B-1. Defaults every
 *  unconfigured resource id to {@code NOT_SYNCHRONIZED_YET}, matching the real façade's own
 *  pre-first-full-snapshot behavior.
 *
 *  <p>External-review correction (2026-07-25): records a bounded, test-only invocation history (one
 *  entry per {@link #queryScalar}/{@link #queryPartitioned} call, in call order) so tests can prove
 *  the exact polling contract — every valid-player poll performs exactly one scalar query each for
 *  Mana/Stamina/Rage and one partitioned query for spell slots, never more, never fewer, never
 *  reusing a stale value across successive polls. Never referenced by production code. */
final class FakeClientResourceParityGenericAccess implements ClientResourceParityGenericAccess {

    enum QueryKind { SCALAR, PARTITIONED }

    record Invocation(QueryKind kind, Identifier resourceId) {}

    private final Map<Identifier, ClientResourceQueryResult> scalarResults = new HashMap<>();
    private final Map<Identifier, ClientResourceQueryResult> partitionedResults = new HashMap<>();
    private final List<Invocation> invocations = new ArrayList<>();

    void setScalar(Identifier resourceId, long current, long maximum, ClientResourceTrust trust) {
        scalarResults.put(resourceId, new ClientResourceQueryResult.Scalar(
                resourceId, current, maximum, 0, 1, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, trust));
    }

    void setScalarUnavailable(Identifier resourceId, ClientResourceUnavailableReason reason) {
        scalarResults.put(resourceId, ClientResourceQueryResult.unavailable(resourceId, reason));
    }

    void setPartitioned(Identifier resourceId, ClientResourceQueryResult.Partitioned result) {
        partitionedResults.put(resourceId, result);
    }

    List<Invocation> invocations() {
        return List.copyOf(invocations);
    }

    long scalarQueryCount(Identifier resourceId) {
        return invocations.stream()
                .filter(invocation -> invocation.kind() == QueryKind.SCALAR && invocation.resourceId().equals(resourceId))
                .count();
    }

    long partitionedQueryCount(Identifier resourceId) {
        return invocations.stream()
                .filter(invocation -> invocation.kind() == QueryKind.PARTITIONED && invocation.resourceId().equals(resourceId))
                .count();
    }

    void clearInvocations() {
        invocations.clear();
    }

    @Override
    public ClientResourceQueryResult queryScalar(Identifier resourceId) {
        invocations.add(new Invocation(QueryKind.SCALAR, resourceId));
        return scalarResults.getOrDefault(resourceId,
                ClientResourceQueryResult.unavailable(resourceId, ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET));
    }

    @Override
    public ClientResourceQueryResult queryPartitioned(Identifier resourceId) {
        invocations.add(new Invocation(QueryKind.PARTITIONED, resourceId));
        return partitionedResults.getOrDefault(resourceId,
                ClientResourceQueryResult.unavailable(resourceId, ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET));
    }
}
