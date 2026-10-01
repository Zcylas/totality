package zcylas.totality.api.entitlement;

/**
 * Whether Entitlement may store a permanent fact for content (canonical §2.4).
 *
 * <ul>
 *   <li>{@link #SOURCE_BOUND} — access exists only while a validated source exists; permanent facts are rejected.</li>
 *   <li>{@link #EXPLICIT_PERMANENT_ACQUISITION} — a validated transaction may write an independent permanent fact;
 *   ordinary grants still never become permanent automatically.</li>
 *   <li>{@link #DOMAIN_OWNED} — a richer owning system (e.g. a future Spells API) owns knowledge and retention;
 *   Entitlement never stores the fact itself.</li>
 * </ul>
 */
public enum EntitlementRetentionPolicy {
    SOURCE_BOUND,
    EXPLICIT_PERMANENT_ACQUISITION,
    DOMAIN_OWNED
}
