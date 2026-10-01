package zcylas.totality.api.entitlement.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * The finalized requirement expression (canonical §5.1): {@code AllOf / AnyOf / Not / Condition}.
 * Trees are immutable records, so they are acyclic by construction; leaves reference registered
 * {@link EntitlementConditionDefinition}s by id, never executable data.
 */
public sealed interface EntitlementRequirement
        permits EntitlementRequirement.AllOf, EntitlementRequirement.AnyOf,
                EntitlementRequirement.Not, EntitlementRequirement.Condition {

    /** True when every child is satisfied. An empty {@code AllOf} is true. */
    record AllOf(List<EntitlementRequirement> children) implements EntitlementRequirement {
        public AllOf { children = List.copyOf(children); }
    }

    /** True when at least one child is satisfied. An empty {@code AnyOf} is invalid and fails closed. */
    record AnyOf(List<EntitlementRequirement> children) implements EntitlementRequirement {
        public AnyOf { children = List.copyOf(children); }
    }

    /** Inverts its child. Requires an authored failure message — an inverted explanation is otherwise meaningless. */
    record Not(EntitlementRequirement child, String failureTranslationKey) implements EntitlementRequirement {
        public Not {
            Objects.requireNonNull(child, "child");
            Objects.requireNonNull(failureTranslationKey, "failureTranslationKey");
        }
    }

    /** A leaf referencing a registered condition definition. */
    record Condition(Identifier conditionId) implements EntitlementRequirement {
        public Condition { Objects.requireNonNull(conditionId, "conditionId"); }
    }

    static EntitlementRequirement allOf(EntitlementRequirement... children) { return new AllOf(List.of(children)); }

    static EntitlementRequirement anyOf(EntitlementRequirement... children) { return new AnyOf(List.of(children)); }

    static EntitlementRequirement not(EntitlementRequirement child, String failureTranslationKey) {
        return new Not(child, failureTranslationKey);
    }

    static EntitlementRequirement condition(Identifier conditionId) { return new Condition(conditionId); }

    /** Data form: {@code {"type":"all_of","children":[...]}}, {@code any_of}, {@code {"type":"not","child":..,"failure":".."}},
     *  {@code {"type":"condition","id":"ns:path"}}. */
    Codec<EntitlementRequirement> CODEC = Codec.recursive("EntitlementRequirement", self -> {
        MapCodec<AllOf> allOf = self.listOf().fieldOf("children").xmap(AllOf::new, AllOf::children);
        MapCodec<AnyOf> anyOf = self.listOf().fieldOf("children").xmap(AnyOf::new, AnyOf::children);
        MapCodec<Not> not = RecordCodecBuilder.mapCodec(i -> i.group(
                self.fieldOf("child").forGetter(Not::child),
                Codec.STRING.fieldOf("failure").forGetter(Not::failureTranslationKey)
        ).apply(i, Not::new));
        MapCodec<Condition> condition = Identifier.CODEC.fieldOf("id").xmap(Condition::new, Condition::conditionId);
        Codec<EntitlementRequirement> dispatch = Codec.STRING.partialDispatch("type",
                requirement -> DataResult.success(switch (requirement) {
                    case AllOf ignored -> "all_of";
                    case AnyOf ignored -> "any_of";
                    case Not ignored -> "not";
                    case Condition ignored -> "condition";
                }),
                type -> switch (type) {
                    case "all_of" -> DataResult.success(allOf);
                    case "any_of" -> DataResult.success(anyOf);
                    case "not" -> DataResult.success(not);
                    case "condition" -> DataResult.success(condition);
                    default -> DataResult.error(() -> "Unknown requirement type: " + type);
                });
        // Validated inside the recursion so every nested node is checked, not only the root.
        return dispatch.validate(EntitlementRequirement::validateShape);
    });

    private static DataResult<EntitlementRequirement> validateShape(EntitlementRequirement requirement) {
        return switch (requirement) {
            case AnyOf anyOf when anyOf.children().isEmpty() -> DataResult.error(() -> "Empty any_of is invalid");
            case Not not when not.failureTranslationKey().isBlank() -> DataResult.error(() -> "not requires a failure message");
            default -> DataResult.success(requirement);
        };
    }
}
