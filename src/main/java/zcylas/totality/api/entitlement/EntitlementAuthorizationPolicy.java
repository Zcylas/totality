package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A code-owned authorization strategy (canonical §2.7). It only decides which <em>bases</em> authorize an
 * action — permanent facts, grants, contributor paths. Visibility, suspensions and requirement trees are
 * applied uniformly by the engine around it. Datapacks may select a registered policy, never inject one.
 */
public interface EntitlementAuthorizationPolicy {

    Identifier id();

    Evaluation evaluate(StateView view, Identifier actionId);

    /** Everything the engine gathered for one key and action, read-only. */
    record StateView(
            EntitlementKey key,
            EntitlementTypeDefinition type,
            Optional<EntitlementDefinition> definition,
            Map<PermanentEntitlementFact, PermanentFactRecord> permanentFacts,
            List<EntitlementGrant> grantsForAction,
            EntitlementStateContributor.Collector contributed,
            EntitlementQueryContext context
    ) {}

    /**
     * @param paths              every basis that authorizes the action; empty when unauthorized
     * @param missingBasisReason reason code used when {@code paths} is empty
     */
    record Evaluation(List<AuthorizationPath> paths, Identifier missingBasisReason) {
        public Evaluation {
            paths = List.copyOf(paths);
        }
    }
}
