package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

/**
 * Core decision reason codes (canonical §6.4). Namespaced so owning systems can add their own.
 * {@link #INVALID_CONDITION} and {@link #INVALID_REQUIREMENT} are Entitlement-core additions for
 * fail-closed requirement errors.
 */
public final class EntitlementReasons {

    public static final Identifier ALLOWED = id("allowed");
    public static final Identifier HIDDEN = id("hidden");
    public static final Identifier LOCKED = id("locked");
    public static final Identifier NOT_KNOWN = id("not_known");
    public static final Identifier KNOWN_BUT_UNAVAILABLE = id("known_but_unavailable");
    public static final Identifier MISSING_REQUIREMENT = id("missing_requirement");
    public static final Identifier TEMPORARILY_SUSPENDED = id("temporarily_suspended");
    public static final Identifier MISSING_OWNERSHIP = id("missing_ownership");
    public static final Identifier SERVER_DENIED = id("server_denied");
    public static final Identifier DEBUG_ONLY = id("debug_only");
    public static final Identifier UNREGISTERED_CONTENT = id("unregistered_content");
    public static final Identifier STALE_CLIENT_STATE = id("stale_client_state");
    public static final Identifier EXPIRED_GRANT = id("expired_grant");
    public static final Identifier INVALID_CONDITION = id("invalid_condition");
    public static final Identifier INVALID_REQUIREMENT = id("invalid_requirement");
    public static final Identifier REQUIREMENT_FAILED = id("requirement_failed");
    public static final Identifier REDACTED = id("redacted");
    /** A grant provider or state contributor needed for this decision failed; access is withheld until it
     *  can be verified again. Nothing permanent is erased. */
    public static final Identifier SOURCE_UNVERIFIED = id("source_unverified");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private EntitlementReasons() {}
}
