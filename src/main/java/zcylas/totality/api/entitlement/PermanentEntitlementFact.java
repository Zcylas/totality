package zcylas.totality.api.entitlement;

/**
 * The two stable semantic axes Entitlement may store permanently (canonical §3.2). Deliberately not
 * an extensible list of gameplay systems: domain systems that own richer knowledge (spells, recipes)
 * keep it themselves and contribute it to queries.
 */
public enum PermanentEntitlementFact {
    KNOWN,
    UNLOCKED
}
