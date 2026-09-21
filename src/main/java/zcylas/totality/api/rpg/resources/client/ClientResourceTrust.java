package zcylas.totality.api.rpg.resources.client;

/**
 * Freshness of a successful {@link ClientResourceQueryResult}. Deliberately has no member for "never
 * synced" — a Resource with no accepted full snapshot yet is not a successful value with weak trust,
 * it is a structured {@link ClientResourceQueryResult.Unavailable} with reason
 * {@link ClientResourceUnavailableReason#NOT_SYNCHRONIZED_YET}. See the Phase 3B readiness audit §9.
 */
public enum ClientResourceTrust {
    /** No resync is currently pending; the value reflects the latest accepted full/delta. */
    FRESH,
    /**
     * A revision gap was detected and a resync request is in flight or awaiting retry. The value
     * returned alongside this trust level is still the last accepted snapshot — never blanked or
     * withheld while a resync is pending.
     */
    PENDING_RESYNC
}
