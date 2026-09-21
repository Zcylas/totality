package zcylas.totality.api.rpg.resources.sync;

/**
 * Shared constants for the Phase 3A generic Resource synchronization wire contract. Bumping
 * {@link #PROTOCOL_VERSION} is the only sanctioned way to change the wire shape of the full/delta
 * payloads — a client on a different version fails safely (see {@link ClientResourceSyncState})
 * rather than misinterpreting bytes.
 */
public final class ResourceSyncProtocol {

    public static final int PROTOCOL_VERSION = 1;

    /**
     * Upper bound on how many scalar/partitioned/invalidated resource entries a single full or
     * delta packet may declare, checked before any collection is allocated at that size. Production
     * currently registers 7 resources total, so this is comfortably above every supported use —
     * chosen to reject a corrupt or hostile length prefix cheaply rather than to accommodate any
     * anticipated future resource count.
     */
    public static final int MAX_RESOURCE_ENTRIES = 256;

    /**
     * Upper bound on how many partition entries a single {@code ResourcePartitionedWireSnapshot} may
     * declare, checked before its entry list is allocated at that size. Production's only
     * partitioned resource (standard spell slots) always has exactly 10 (see
     * {@code SpellSlotComponent#MAX_SPELL_LEVEL}), so this is comfortably above every supported use.
     */
    public static final int MAX_PARTITION_ENTRIES = 64;

    private ResourceSyncProtocol() {}
}
