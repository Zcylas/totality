// api/combat/condition/ConditionRegistry.java
package zcylas.totality.api.combat.condition;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConditionRegistry {

    private static final Map<Identifier, TotalityCondition> REGISTRY = new LinkedHashMap<>();

    private ConditionRegistry() {}

    public static TotalityCondition register(TotalityCondition condition) {
        if (REGISTRY.containsKey(condition.getId())) {
            throw new IllegalStateException("Condition already registered: " + condition.getId());
        }
        REGISTRY.put(condition.getId(), condition);
        return condition;
    }

    public static TotalityCondition get(Identifier id) {
        return REGISTRY.get(id);
    }

    public static Collection<TotalityCondition> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static boolean contains(Identifier id) {
        return REGISTRY.containsKey(id);
    }
}