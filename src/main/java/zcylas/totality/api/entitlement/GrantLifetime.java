package zcylas.totality.api.entitlement;

/**
 * How long a grant lives (canonical §3.5).
 *
 * <ul>
 *   <li>{@link #WHILE_SOURCE_ACTIVE} — provider-reconciled, never serialized; rebuilt from source state.</li>
 *   <li>{@link #SESSION} — runtime only; survives respawn, ends at logout.</li>
 *   <li>{@link #UNTIL_DEATH} — persisted; removed when the player dies.</li>
 *   <li>{@link #PERSISTENT_LEASE} — persisted; must carry an expiry.</li>
 *   <li>{@link #UNTIL_EXPLICITLY_REMOVED} — persisted until removed by its exact source.</li>
 * </ul>
 */
public enum GrantLifetime {
    WHILE_SOURCE_ACTIVE,
    SESSION,
    UNTIL_DEATH,
    PERSISTENT_LEASE,
    UNTIL_EXPLICITLY_REMOVED;

    public boolean persisted() {
        return this == UNTIL_DEATH || this == PERSISTENT_LEASE || this == UNTIL_EXPLICITLY_REMOVED;
    }
}
