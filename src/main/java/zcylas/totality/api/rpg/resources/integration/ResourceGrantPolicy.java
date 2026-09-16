package zcylas.totality.api.rpg.resources.integration;

import zcylas.totality.api.rpg.resources.MaximumChangePolicy;

import java.util.Objects;

/**
 * A resource's declared aggregation + removal + maximum-change behavior for grant reconciliation —
 * canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §16.4/§16.7 describe aggregation and
 * removal as properties "each resource definition declares," but {@code PlayerResourceDefinition}
 * (an already-established, widely-constructed record predating the grant system) is deliberately
 * not given new fields for this foundation pass — see {@link ResourceGrantPolicyRegistry}'s
 * Javadoc for why. This record bundles the per-resource policies {@link ResourceGrantReconciler}
 * and {@code ClassChangeReconciler} actually need.
 *
 * <p>{@code maximumChangePolicy} (Phase 6 correction pass, 2026-09-16) answers canonical §10.5's
 * "the owning progression/class system must explicitly choose whether to preserve current, add the
 * gained difference, or fill" — it governs how a {@code GENERIC_COMPONENT} resource's already-live
 * state reconciles when a class change (e.g. a multiclass level-up) moves its resolved maximum,
 * for a resource that stays continuously granted across the change (so grant reconciliation itself
 * never re-instantiates it). Defaults to {@link MaximumChangePolicy#CLAMP_CURRENT} — the existing,
 * safest behavior every resource had before this field existed — so no resource's behavior changes
 * merely by this field's addition; a resource must opt into a different policy explicitly (see
 * {@code StandardSpellSlotResources#register()}).
 */
public record ResourceGrantPolicy(
        ResourceGrantAggregationPolicy aggregation, ResourceRemovalPolicy removal, MaximumChangePolicy maximumChangePolicy) {

    public ResourceGrantPolicy {
        Objects.requireNonNull(aggregation, "aggregation");
        Objects.requireNonNull(removal, "removal");
        Objects.requireNonNull(maximumChangePolicy, "maximumChangePolicy");
    }

    /** Convenience overload for the common case of no explicit maximum-change opinion — defaults to
     *  {@link MaximumChangePolicy#CLAMP_CURRENT}, preserving every pre-existing call site's behavior. */
    public ResourceGrantPolicy(ResourceGrantAggregationPolicy aggregation, ResourceRemovalPolicy removal) {
        this(aggregation, removal, MaximumChangePolicy.CLAMP_CURRENT);
    }

    /** Canonical §16.7's stated default removal behavior, paired with the simplest aggregation shape. */
    public static final ResourceGrantPolicy DEFAULT =
            new ResourceGrantPolicy(ResourceGrantAggregationPolicy.SINGLE_OWNER, ResourceRemovalPolicy.REMOVE_STATE, MaximumChangePolicy.CLAMP_CURRENT);
}
