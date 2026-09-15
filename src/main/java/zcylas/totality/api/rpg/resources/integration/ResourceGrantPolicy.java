package zcylas.totality.api.rpg.resources.integration;

import java.util.Objects;

/**
 * A resource's declared aggregation + removal behavior for grant reconciliation — canonical
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §16.4/§16.7 describe both as properties "each
 * resource definition declares," but {@code PlayerResourceDefinition} (an already-established,
 * widely-constructed record predating the grant system) is deliberately not given a new field for
 * this foundation pass — see {@link ResourceGrantPolicyRegistry}'s Javadoc for why. This record
 * bundles the two per-resource policies the {@link ResourceGrantReconciler} actually needs.
 */
public record ResourceGrantPolicy(ResourceGrantAggregationPolicy aggregation, ResourceRemovalPolicy removal) {

    public ResourceGrantPolicy {
        Objects.requireNonNull(aggregation, "aggregation");
        Objects.requireNonNull(removal, "removal");
    }

    /** Canonical §16.7's stated default removal behavior, paired with the simplest aggregation shape. */
    public static final ResourceGrantPolicy DEFAULT =
            new ResourceGrantPolicy(ResourceGrantAggregationPolicy.SINGLE_OWNER, ResourceRemovalPolicy.REMOVE_STATE);
}
