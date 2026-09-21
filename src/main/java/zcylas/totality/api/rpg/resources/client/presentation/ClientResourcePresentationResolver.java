package zcylas.totality.api.rpg.resources.client.presentation;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.ClientResourceService;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The one shared, presentation-only resolver for Phase 3C client consumers (HUD, class screen,
 * overview tab, spell radial). Wraps a {@link ClientResourceService} query with the Phase 3C
 * fallback policy: prefer a Generic result (both {@code FRESH} and {@code PENDING_RESYNC} trust
 * are accepted and displayed identically — see the canonical spec's PENDING_RESYNC policy), and
 * fall back to a caller-supplied legacy value only when the Generic result is
 * {@link ClientResourceQueryResult.Unavailable}.
 *
 * <p>Never mutates a Resource, sends a packet, recalculates a maximum, or spends/restores a value
 * — it only reads {@link ClientResourceService#queryScalar}/{@code queryPartitioned} and the
 * legacy suppliers a caller passes in. Never fabricates a value: an unavailable Generic query
 * always defers to the caller's own legacy reader (which itself owns whatever "no data" convention
 * it already had), never to a hardcoded zero invented by this class. Deliberately depends on
 * nothing but the client façade — no legacy manager class, no {@code ClientResourceSyncState}/
 * {@code ClientResourceSyncManager}, no server-only class — so every consumer wiring stays visible
 * at the call site instead of being hidden inside a second Resource authority.
 */
public final class ClientResourcePresentationResolver {

    public static final ClientResourcePresentationResolver INSTANCE =
            new ClientResourcePresentationResolver(ClientResourceService.INSTANCE);

    private final ClientResourceService service;

    public ClientResourcePresentationResolver(ClientResourceService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Resolves a scalar Resource (Mana, Stamina, Rage) for display. Prefers a valid Generic
     * {@link ClientResourceQueryResult.Scalar} (any trust level); falls back to
     * {@code legacyCurrent}/{@code legacyMaximum} only when the Generic query is
     * {@link ClientResourceQueryResult.Unavailable}. The legacy suppliers are never evaluated when
     * a Generic result is used.
     */
    public ScalarPresentation resolveScalar(Identifier resourceId, LongSupplier legacyCurrent, LongSupplier legacyMaximum) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(legacyCurrent, "legacyCurrent");
        Objects.requireNonNull(legacyMaximum, "legacyMaximum");

        ClientResourceQueryResult result = service.queryScalar(resourceId);
        if (result instanceof ClientResourceQueryResult.Scalar scalar) {
            long scale = Math.max(1L, scalar.unitScale());
            return new ScalarPresentation(scalar.currentUnits() / scale, scalar.maximumUnits() / scale, true);
        }
        return new ScalarPresentation(legacyCurrent.getAsLong(), legacyMaximum.getAsLong(), false);
    }

    /**
     * Resolves a single partition of a partitioned Resource (Standard Spell Slots' tier
     * {@code partitionId}, 1-10) for display. {@code currentUnits} is the partition's remaining
     * amount (never used amount — see {@code StandardSpellSlotsResourceAdapter}), {@code
     * maximumUnits} its maximum. Falls back to {@code legacyCurrent}/{@code legacyMaximum} when the
     * Generic query is unavailable, or when the Generic result is a structurally valid Partitioned
     * result that simply has no entry for {@code partitionId} (treated as unusable for this tier,
     * not as a fabricated zero).
     */
    public PartitionPresentation resolvePartition(
            Identifier resourceId, int partitionId, LongSupplier legacyCurrent, LongSupplier legacyMaximum) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(legacyCurrent, "legacyCurrent");
        Objects.requireNonNull(legacyMaximum, "legacyMaximum");

        ClientResourceQueryResult result = service.queryPartitioned(resourceId);
        if (result instanceof ClientResourceQueryResult.Partitioned partitioned) {
            var partition = partitioned.partition(partitionId);
            if (partition.isPresent()) {
                long scale = Math.max(1L, partitioned.unitScale());
                ClientResourceQueryResult.Partitioned.Partition p = partition.get();
                return new PartitionPresentation(p.currentUnits() / scale, p.maximumUnits() / scale, true);
            }
        }
        return new PartitionPresentation(legacyCurrent.getAsLong(), legacyMaximum.getAsLong(), false);
    }

    /** @param generic {@code true} when this value came from the Generic Resource view, {@code false} when it is the legacy fallback. Diagnostic only — never rendered as player-facing text. */
    public record ScalarPresentation(long current, long maximum, boolean generic) {}

    /** @param generic {@code true} when this value came from the Generic Resource view, {@code false} when it is the legacy fallback. Diagnostic only — never rendered as player-facing text. */
    public record PartitionPresentation(long current, long maximum, boolean generic) {}
}
