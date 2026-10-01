package zcylas.totality.api.entitlement.requirement;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.entitlement.EntitlementQueryContext;
import zcylas.totality.api.entitlement.EntitlementReasons;
import zcylas.totality.api.entitlement.requirement.RequirementEvaluation.Outcome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Evaluates {@link EntitlementRequirement} trees (canonical §5). Every failure mode fails closed:
 * an unregistered condition id, a throwing evaluator, an empty {@code AnyOf} and an over-deep tree all
 * produce {@link Outcome#ERROR}, which no combinator turns into a success.
 */
public final class RequirementEvaluator {

    /** Defence in depth against pathological trees; immutable records cannot form cycles. */
    public static final int MAX_DEPTH = 32;

    private RequirementEvaluator() {}

    public static RequirementEvaluation evaluate(
            EntitlementRequirement requirement,
            Function<Identifier, Optional<EntitlementConditionDefinition<?>>> conditions,
            EntitlementQueryContext context) {
        return evaluate(requirement, conditions, context, 0);
    }

    private static RequirementEvaluation evaluate(
            EntitlementRequirement requirement,
            Function<Identifier, Optional<EntitlementConditionDefinition<?>>> conditions,
            EntitlementQueryContext context,
            int depth) {
        if (depth > MAX_DEPTH) return error(EntitlementReasons.INVALID_REQUIREMENT, Optional.empty());
        return switch (requirement) {
            case EntitlementRequirement.AllOf allOf -> {
                List<RequirementEvaluation> children = new ArrayList<>();
                for (EntitlementRequirement child : allOf.children()) {
                    children.add(evaluate(child, conditions, context, depth + 1));
                }
                Outcome outcome = combineAll(children);
                yield composite(outcome, children);
            }
            case EntitlementRequirement.AnyOf anyOf -> {
                if (anyOf.children().isEmpty()) {
                    yield error(EntitlementReasons.INVALID_REQUIREMENT, Optional.empty());
                }
                List<RequirementEvaluation> children = new ArrayList<>();
                for (EntitlementRequirement child : anyOf.children()) {
                    children.add(evaluate(child, conditions, context, depth + 1));
                }
                Outcome outcome = combineAny(children);
                yield composite(outcome, children);
            }
            case EntitlementRequirement.Not not -> {
                RequirementEvaluation child = evaluate(not.child(), conditions, context, depth + 1);
                Outcome outcome = switch (child.outcome()) {
                    case SATISFIED -> Outcome.UNSATISFIED;
                    case UNSATISFIED -> Outcome.SATISFIED;
                    case ERROR -> Outcome.ERROR;
                };
                Identifier reason = outcome == Outcome.ERROR ? child.reasonCode() : EntitlementReasons.REQUIREMENT_FAILED;
                // The child's secrecy carries over: inverting a secret condition must not reveal it.
                yield new RequirementEvaluation(outcome, reason, Optional.empty(),
                        Optional.of(not.failureTranslationKey()), List.of(child), child.dependenciesRead(),
                        child.disclosurePolicy(), child.cacheable());
            }
            case EntitlementRequirement.Condition condition -> evaluateCondition(condition.conditionId(), conditions, context);
        };
    }

    private static RequirementEvaluation evaluateCondition(
            Identifier conditionId,
            Function<Identifier, Optional<EntitlementConditionDefinition<?>>> conditions,
            EntitlementQueryContext context) {
        Optional<EntitlementConditionDefinition<?>> definition = conditions.apply(conditionId);
        if (definition.isEmpty()) {
            Totality.LOGGER.warn("[Entitlement] Requirement references unregistered condition {} — failing closed", conditionId);
            return error(EntitlementReasons.INVALID_CONDITION, Optional.of(conditionId));
        }
        EntitlementConditionDefinition<?> def = definition.get();
        boolean result;
        try {
            result = def.test(context);
        } catch (RuntimeException e) {
            Totality.LOGGER.error("[Entitlement] Condition {} threw — failing closed", conditionId, e);
            return error(EntitlementReasons.INVALID_CONDITION, Optional.of(conditionId));
        }
        return new RequirementEvaluation(
                result ? Outcome.SATISFIED : Outcome.UNSATISFIED,
                result ? EntitlementReasons.ALLOWED : EntitlementReasons.MISSING_REQUIREMENT,
                Optional.of(conditionId),
                Optional.of(def.failureTranslationKey()),
                List.of(),
                def.dependencies(),
                def.disclosurePolicy(),
                def.type().cacheable());
    }

    private static Outcome combineAll(List<RequirementEvaluation> children) {
        boolean unsatisfied = false;
        for (RequirementEvaluation child : children) {
            if (child.outcome() == Outcome.ERROR) return Outcome.ERROR;
            if (child.outcome() == Outcome.UNSATISFIED) unsatisfied = true;
        }
        return unsatisfied ? Outcome.UNSATISFIED : Outcome.SATISFIED;
    }

    private static Outcome combineAny(List<RequirementEvaluation> children) {
        boolean error = false;
        for (RequirementEvaluation child : children) {
            if (child.outcome() == Outcome.SATISFIED) return Outcome.SATISFIED;
            if (child.outcome() == Outcome.ERROR) error = true;
        }
        return error ? Outcome.ERROR : Outcome.UNSATISFIED;
    }

    private static RequirementEvaluation composite(Outcome outcome, List<RequirementEvaluation> children) {
        Set<EntitlementDependencyKey> deps = new HashSet<>();
        boolean cacheable = true;
        DisclosurePolicy disclosure = DisclosurePolicy.PUBLIC;
        for (RequirementEvaluation child : children) {
            deps.addAll(child.dependenciesRead());
            cacheable &= child.cacheable();
            if (child.disclosurePolicy().ordinal() > disclosure.ordinal()) disclosure = child.disclosurePolicy();
        }
        Identifier reason = switch (outcome) {
            case SATISFIED -> EntitlementReasons.ALLOWED;
            case UNSATISFIED -> EntitlementReasons.MISSING_REQUIREMENT;
            case ERROR -> EntitlementReasons.INVALID_REQUIREMENT;
        };
        return new RequirementEvaluation(outcome, reason, Optional.empty(), Optional.empty(), children, deps,
                disclosure, cacheable);
    }

    private static RequirementEvaluation error(Identifier reason, Optional<Identifier> conditionId) {
        // Errors are never cached and never disclose which security condition was misconfigured.
        return new RequirementEvaluation(Outcome.ERROR, reason, conditionId, Optional.empty(), List.of(), Set.of(),
                DisclosurePolicy.REDACTED, false);
    }
}
