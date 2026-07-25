package zcylas.totality.api.rpg.resources.client.parity;

import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure mapper from the Phase 3B-1 façade's {@link ClientResourceQueryResult} into the Phase 3B-2
 * shadow-parity engine's own {@link ClientResourceParitySummary}, plus the separately-extracted
 * {@code pendingResync} flag the tracker needs. A dedicated mapping step, not a reuse of either
 * type as the other, since {@code ClientResourceQueryResult} carries façade-specific concepts
 * ({@code source}, {@code trust}) the parity summaries deliberately do not model at all — {@code
 * trust} is tracker input metadata, never part of a summary's own shape.
 *
 * <p>Never fabricates a numeric value for an {@link ClientResourceQueryResult.Unavailable} result:
 * it maps directly to {@link ClientResourceParitySummary.Unavailable} with the exact same {@link
 * zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason}, and {@code
 * pendingResync} is unconditionally {@code false} for it — an unavailable result has no successful
 * trust value to extract at all.
 */
public final class ClientGenericParitySummaryMapper {

    private ClientGenericParitySummaryMapper() {}

    /** The mapped parity summary, plus whether the generic side reported {@code PENDING_RESYNC}
     *  trust (always {@code false} for an {@link ClientResourceParitySummary.Unavailable} summary). */
    public record MappedResult(ClientResourceParitySummary summary, boolean pendingResync) {
        public MappedResult {
            Objects.requireNonNull(summary, "summary");
        }
    }

    public static MappedResult map(ClientResourceQueryResult result) {
        Objects.requireNonNull(result, "result");
        return switch (result) {
            case ClientResourceQueryResult.Scalar scalar -> new MappedResult(
                    new ClientResourceParitySummary.Scalar(
                            scalar.currentUnits(), scalar.maximumUnits(), scalar.overflowUnits(), scalar.unitScale()),
                    scalar.trust() == ClientResourceTrust.PENDING_RESYNC);
            case ClientResourceQueryResult.Partitioned partitioned -> new MappedResult(
                    mapPartitioned(partitioned),
                    partitioned.trust() == ClientResourceTrust.PENDING_RESYNC);
            case ClientResourceQueryResult.Unavailable unavailable -> new MappedResult(
                    new ClientResourceParitySummary.Unavailable(unavailable.reason()),
                    false);
        };
    }

    private static ClientResourceParitySummary.Partitioned mapPartitioned(ClientResourceQueryResult.Partitioned partitioned) {
        List<ClientResourceParitySummary.Partitioned.Partition> partitions = new ArrayList<>(partitioned.partitions().size());
        for (ClientResourceQueryResult.Partitioned.Partition p : partitioned.partitions().values()) {
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(
                    p.partitionId(), p.currentUnits(), p.maximumUnits(), p.overflowUnits()));
        }
        return ClientResourceParitySummary.Partitioned.of(partitions, partitioned.unitScale());
    }
}
