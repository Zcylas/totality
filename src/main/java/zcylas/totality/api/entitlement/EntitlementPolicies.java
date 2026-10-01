package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The generic, content-agnostic built-in policies (canonical §2.7). The canonical list's
 * {@code dnd_spell_access} and {@code phone_app_access} are deliberately not part of Core: a magic or
 * phone system registers its own policy when its rules are designed.
 */
public final class EntitlementPolicies {

    public static final Identifier UNLOCK_OR_GRANT = id("unlock_or_grant");
    public static final Identifier KNOWN_OR_GRANT = id("known_or_grant");
    public static final Identifier OWNED_OR_GRANT = id("owned_or_grant");
    public static final Identifier REQUIREMENTS_ONLY = id("requirements_only");

    /** Permanent {@code UNLOCKED}, any grant, or a contributor path. */
    public static EntitlementAuthorizationPolicy unlockOrGrant() {
        return policy(UNLOCK_OR_GRANT, EntitlementReasons.LOCKED, PermanentEntitlementFact.UNLOCKED, false);
    }

    /** Permanent {@code KNOWN}, contributor-reported knowledge, any grant, or a contributor path. */
    public static EntitlementAuthorizationPolicy knownOrGrant() {
        return policy(KNOWN_OR_GRANT, EntitlementReasons.NOT_KNOWN, PermanentEntitlementFact.KNOWN, false);
    }

    /** Contributor-reported ownership, any grant, or a contributor path. */
    public static EntitlementAuthorizationPolicy ownedOrGrant() {
        return policy(OWNED_OR_GRANT, EntitlementReasons.MISSING_OWNERSHIP, null, true);
    }

    /** Always has a basis; the definition's requirement trees alone decide. */
    public static EntitlementAuthorizationPolicy requirementsOnly() {
        return new EntitlementAuthorizationPolicy() {
            @Override public Identifier id() { return REQUIREMENTS_ONLY; }

            @Override
            public Evaluation evaluate(StateView view, Identifier actionId) {
                List<AuthorizationPath> paths = new ArrayList<>(basePaths(view, null));
                paths.add(new AuthorizationPath(AuthorizationPath.REQUIREMENTS, Optional.empty(),
                        false, false, true, false));
                return new Evaluation(paths, EntitlementReasons.MISSING_REQUIREMENT);
            }
        };
    }

    private static EntitlementAuthorizationPolicy policy(Identifier id, Identifier missingReason,
                                                         PermanentEntitlementFact fact, boolean ownership) {
        return new EntitlementAuthorizationPolicy() {
            @Override public Identifier id() { return id; }

            @Override
            public Evaluation evaluate(StateView view, Identifier actionId) {
                List<AuthorizationPath> paths = new ArrayList<>(basePaths(view, fact));
                boolean contributorBasis = fact == PermanentEntitlementFact.KNOWN && view.contributed().isKnown()
                        || ownership && view.contributed().isOwned();
                if (contributorBasis) {
                    paths.add(new AuthorizationPath(AuthorizationPath.CONTRIBUTOR, Optional.empty(),
                            true, false, true, false));
                }
                return new Evaluation(paths, missingReason);
            }
        };
    }

    /** Grants and contributor paths authorize under every policy; a permanent fact only when the policy names it. */
    private static List<AuthorizationPath> basePaths(EntitlementAuthorizationPolicy.StateView view,
                                                     PermanentEntitlementFact fact) {
        List<AuthorizationPath> paths = new ArrayList<>();
        if (fact != null) {
            PermanentFactRecord record = view.permanentFacts().get(fact);
            if (record != null) paths.add(AuthorizationPath.permanent(record));
        }
        for (EntitlementGrant grant : view.grantsForAction()) paths.add(AuthorizationPath.grant(grant));
        paths.addAll(view.contributed().paths());
        return paths;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private EntitlementPolicies() {}
}
