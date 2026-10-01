package zcylas.totality.api.entitlement.requirement;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.EntitlementQueryContext;

import java.util.Optional;
import java.util.Set;

/**
 * A code-owned condition evaluator strategy (canonical §5.2). Its typed configuration is decoded by
 * {@link #codec()}, so a definition never hands untyped data or executable script to runtime evaluation.
 * The owning system implements this; Entitlement Core knows nothing about classes, spells or phones.
 */
public interface EntitlementConditionType<C> {

    Identifier id();

    MapCodec<C> codec();

    /** Evaluates the condition. Throwing is treated as an error and fails closed. */
    boolean test(C configuration, EntitlementQueryContext context);

    /** External state this condition reads, for targeted cache invalidation. */
    default Set<EntitlementDependencyKey> dependencies(C configuration) {
        return Set.of();
    }

    /** False when the condition reads state that has no invalidation signal (e.g. world position);
     *  decisions reading it are then never cached. */
    default boolean cacheable() {
        return true;
    }

    /** Rejects an invalid configuration at registration time; an invalid definition is never registered. */
    default Optional<String> validate(C configuration) {
        return Optional.empty();
    }
}
