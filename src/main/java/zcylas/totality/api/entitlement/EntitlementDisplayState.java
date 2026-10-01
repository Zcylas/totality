package zcylas.totality.api.entitlement;

/**
 * The client-presentable distinctions a decision resolves to.
 *
 * <ul>
 *   <li>{@link #HIDDEN} — must not be shown at all.</li>
 *   <li>{@link #LOCKED} — visible, but the player has no basis (not unlocked, known, owned or granted).</li>
 *   <li>{@link #KNOWN_UNAVAILABLE} — the player holds it, but a current requirement fails.</li>
 *   <li>{@link #AVAILABLE_PERMANENT} — available through a durable (permanent) path.</li>
 *   <li>{@link #AVAILABLE_SOURCE_BOUND} — available only through a source-bound, leased, session or debug
 *   source (Class, Origin, equipment, transformation...); losing every such source removes it.</li>
 *   <li>{@link #SUSPENDED} — held, but explicitly suspended.</li>
 *   <li>{@link #MISSING_REQUIREMENT} — denied because a requirement is not met and nothing is held.</li>
 * </ul>
 */
public enum EntitlementDisplayState {
    HIDDEN,
    LOCKED,
    KNOWN_UNAVAILABLE,
    AVAILABLE_PERMANENT,
    AVAILABLE_SOURCE_BOUND,
    SUSPENDED,
    MISSING_REQUIREMENT;

    public boolean available() {
        return this == AVAILABLE_PERMANENT || this == AVAILABLE_SOURCE_BOUND;
    }
}
