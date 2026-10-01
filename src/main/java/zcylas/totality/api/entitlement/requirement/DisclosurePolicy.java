package zcylas.totality.api.entitlement.requirement;

/**
 * How much of a requirement may reach the client (canonical §14.15).
 *
 * <ul>
 *   <li>{@link #PUBLIC} — the condition id and authored failure message may be shown.</li>
 *   <li>{@link #REDACTED} — the client only learns that something is unmet ("Unavailable due to current
 *   circumstances"); the condition id and real message are withheld.</li>
 *   <li>{@link #SECRET} — the failure is removed from client-facing views entirely.</li>
 * </ul>
 */
public enum DisclosurePolicy {
    PUBLIC,
    REDACTED,
    SECRET
}
