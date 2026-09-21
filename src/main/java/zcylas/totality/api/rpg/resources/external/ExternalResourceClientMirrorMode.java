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
    GENERIC_SYNCHRONIZATION,

    /**
     * Introduced in Phase 2C for {@code ManaResourceAdapter}/{@code StaminaResourceAdapter}: the
     * owner is a legacy-authoritative store ({@code PlayerResourceComponent}) that already has its
     * own Totality-authored bespoke packet and client-side cache ({@code SyncManaPayload}/
     * {@code ClientManaManager}, {@code SyncStaminaPayload}/{@code ClientStaminaManager}) — neither
     * vanilla-native nor the generic Resource API sync path. Existing HUD readers keep using that
     * bespoke packet/cache directly and are unaffected by this declaration. A generic client-side
     * query for a resource in this mode has no trustworthy value to read yet (the Resource API does
     * not mirror this resource to the client at all during the transitional adapter phase) and must
     * fail with {@link zcylas.totality.api.rpg.resources.ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE}
     * rather than reading the legacy client cache (untrusted for this purpose) or fabricating a
     * value. This mode is expected to become {@link #GENERIC_SYNCHRONIZATION} once a future
     * synchronization/client-presentation phase actually mirrors these resources generically.
     */
    LEGACY_BESPOKE_SYNCHRONIZATION
}
