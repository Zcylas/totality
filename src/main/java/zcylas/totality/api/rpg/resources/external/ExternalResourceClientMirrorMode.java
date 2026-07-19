package zcylas.totality.api.rpg.resources.external;

/**
 * How an {@link ExternalPlayerResourceAdapter}'s state reaches the client, so the query path never
 * double-applies both a generic resource packet and the owner's own synchronization. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §6.5 ("Native client synchronization may be
 * reused where reliable, or the adapter may request generic deltas. Do not double-apply both.").
 */
public enum ExternalResourceClientMirrorMode {
    /**
     * The owning vanilla system already synchronizes this value to the client through its own
     * packet (Health via {@code ClientboundSetHealthPacket}, Food via vanilla {@code FoodData}
     * sync). No generic Resource API packet is sent for this resource. Both Health and Food use
     * this mode in Phase 2A.
     */
    NATIVE_SYNCHRONIZATION,

    /**
     * The Resource API's own generic sync path is responsible for mirroring this value to the
     * client. Not used by any Phase 2A adapter — declared for definition-shape completeness ahead
     * of the future generic synchronization phase.
     */
    GENERIC_SYNCHRONIZATION
}
