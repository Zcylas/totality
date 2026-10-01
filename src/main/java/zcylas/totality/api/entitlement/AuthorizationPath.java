package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * One successful way a query is authorized (canonical §6.5). A decision exposes every path, which is
 * what lets one source be removed safely while another keeps the content available.
 *
 * @param durable   independent of any source staying active (a permanent fact)
 * @param temporary exists only while a source / lease / session lasts
 * @param debug     supplied by debug tooling; never progression
 */
public record AuthorizationPath(
        Identifier pathTypeId,
        Optional<GrantSourceRef> source,
        boolean durable,
        boolean temporary,
        boolean progressionEligible,
        boolean debug
) {

    public static final Identifier PERMANENT_FACT = Identifier.fromNamespaceAndPath("totality", "permanent_fact");
    public static final Identifier GRANT = Identifier.fromNamespaceAndPath("totality", "grant");
    public static final Identifier CONTRIBUTOR = Identifier.fromNamespaceAndPath("totality", "contributor");
    public static final Identifier REQUIREMENTS = Identifier.fromNamespaceAndPath("totality", "requirements");
    /** Authorizes only the {@code view} action: the content is visible. */
    public static final Identifier VISIBLE = Identifier.fromNamespaceAndPath("totality", "visible");

    public static AuthorizationPath permanent(PermanentFactRecord record) {
        return new AuthorizationPath(PERMANENT_FACT, Optional.of(record.primarySource()), true, false,
                record.progressionEligible(), record.primarySource().isDebug());
    }

    public static AuthorizationPath grant(EntitlementGrant grant) {
        return new AuthorizationPath(GRANT, Optional.of(grant.source()), false, true, grant.progressionEligible(),
                grant.isDebug());
    }
}
