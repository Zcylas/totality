package zcylas.totality.api.rpg.resources.client;

/**
 * Why {@link ClientResourceService#query}/{@code queryScalar}/{@code queryPartitioned} could not
 * produce a numeric client Resource value. Mirrors the "never fabricate a value" principle of
 * {@code ResourceQueryFailureReason} (see its Javadoc) but is a distinct, client-only vocabulary —
 * see the Phase 3B readiness audit §6.1 for why the server-side enum is not reused verbatim.
 */
public enum ClientResourceUnavailableReason {
    /** The Resource ID is not in the canonical {@code PlayerResourceRegistry}. */
    RESOURCE_UNREGISTERED,

    /** The Resource is registered, but no {@link ClientResourceReader} is registered for it yet. */
    CLIENT_SOURCE_NOT_CONFIGURED,

    /**
     * A generic-synchronized-view Resource was queried before the first accepted full snapshot this
     * session ({@code ClientResourceSyncState.hasSynced() == false}).
     */
    NOT_SYNCHRONIZED_YET,

    /**
     * The Resource is registered and a full snapshot has been accepted, but the Resource is absent
     * from the authoritative synchronized view for this player (e.g. Rage never granted to a
     * non-Barbarian). Never conflated with a genuine {@code 0/0} success — see the readiness audit's
     * Rage absence-versus-zero discussion (§8.3). Deliberately generic: also the eventual home for
     * physiology/origin-based unavailability (e.g. a species without Breath), without hardcoding any
     * particular species now.
     */
    NOT_AVAILABLE_TO_PLAYER,

    /** A native-client-view Resource was queried while no active local player or world exists. */
    NO_LOCAL_PLAYER,

    /**
     * A typed scalar query was made for a partitioned Resource, a typed partitioned query was made
     * for a scalar Resource, or the stored synchronized shape contradicts the Resource's canonical
     * definition. Never silently converted, flattened, or coerced.
     */
    MODEL_MISMATCH,

    /**
     * The Resource is registered and its client source exists (a reader answered), but the source
     * returned state that cannot be represented as a valid client Resource result — e.g. a native
     * owner reporting a non-positive maximum, a non-finite Health value, a fixed-point conversion
     * overflow, or any other value that would violate {@link ClientResourceQueryResult.Scalar}'s or
     * {@link ClientResourceQueryResult.Partitioned}'s own numeric invariants.
     *
     * <p>Deliberately distinct from {@link #NOT_AVAILABLE_TO_PLAYER}: that reason means the Resource
     * genuinely does not apply to or has not been granted to this player (a real, well-formed
     * absence); this reason means the source answered but its answer is structurally corrupt or
     * unrepresentable. Added per the Phase 3B-1 external review correction (2026-07-23) — see
     * {@code TOTALITY_RESOURCE_API_PHASE_3B1_CLIENT_VIEW_IMPLEMENTATION_REPORT.md}'s "Phase 3B-1
     * External Review Correction" section.
     */
    MALFORMED_SOURCE_STATE
}
