package zcylas.totality.api.entitlement.requirement;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.EntitlementReasons;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Structured result of evaluating a requirement tree (canonical §5.6). {@link Outcome#ERROR} is a
 * distinct fail-closed state: a missing condition, a throwing evaluator or an invalid composite is
 * never satisfied, and wrapping it in {@code Not} does not make it satisfied either.
 */
public record RequirementEvaluation(
        Outcome outcome,
        Identifier reasonCode,
        Optional<Identifier> conditionId,
        Optional<String> messageTranslationKey,
        List<RequirementEvaluation> children,
        Set<EntitlementDependencyKey> dependenciesRead,
        DisclosurePolicy disclosurePolicy,
        boolean cacheable
) {

    /** Shown to the client in place of a redacted requirement's real explanation. */
    public static final String REDACTED_MESSAGE = "totality.entitlement.requirement.redacted";

    public enum Outcome {
        SATISFIED,
        UNSATISFIED,
        ERROR
    }

    public RequirementEvaluation {
        children = List.copyOf(children);
        dependenciesRead = Set.copyOf(dependenciesRead);
    }

    public boolean satisfied() {
        return outcome == Outcome.SATISFIED;
    }

    /** Unsatisfied leaves (conditions, and {@code Not} nodes, which carry their own authored message). */
    public List<RequirementEvaluation> failures() {
        List<RequirementEvaluation> out = new ArrayList<>();
        collectFailures(this, out);
        return out;
    }

    private static void collectFailures(RequirementEvaluation node, List<RequirementEvaluation> out) {
        if (node.satisfied()) return;
        if (node.children.isEmpty() || node.messageTranslationKey.isPresent()) {
            out.add(node);
            return;
        }
        for (RequirementEvaluation child : node.children) collectFailures(child, out);
    }

    /** Client-safe failure summaries: SECRET failures are dropped, REDACTED ones lose their real
     *  condition id and message. Server code keeps the full tree. */
    public List<DisplayRequirementSummary> displayableFailures() {
        List<DisplayRequirementSummary> out = new ArrayList<>();
        for (RequirementEvaluation failure : failures()) {
            switch (failure.disclosurePolicy) {
                case PUBLIC -> out.add(new DisplayRequirementSummary(failure.reasonCode, failure.conditionId,
                        failure.messageTranslationKey.orElse(REDACTED_MESSAGE)));
                case REDACTED -> out.add(new DisplayRequirementSummary(EntitlementReasons.REDACTED, Optional.empty(),
                        REDACTED_MESSAGE));
                case SECRET -> { }
            }
        }
        return out;
    }
}
