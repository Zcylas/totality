package zcylas.totality.api.rpg.resources.client;

/**
 * Which kind of client-reachable source answered a {@link ClientResourceQueryResult}. See
 * {@code TOTALITY_RESOURCE_API_PHASE_3B_CLIENT_VIEW_AND_PARITY_READINESS.md} §5/§7/§8.
 */
public enum ClientResourceSource {
    /** Health/Food/Breath: read directly from the active local player's native vanilla state. */
    NATIVE_CLIENT_VIEW,
    /** Mana/Stamina/spell slots/Rage: read from the Phase 3A generic client synchronization state. */
    GENERIC_SYNCHRONIZED_VIEW
}
