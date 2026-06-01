// api/combat/damage/DamageTypeRegistry.java
package zcylas.totality.api.combat.damage;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DamageTypeRegistry {

    private static final Map<Identifier, TotalityDamageType> REGISTRY = new LinkedHashMap<>();

    private DamageTypeRegistry() {}

    public static TotalityDamageType register(TotalityDamageType type) {
        if (REGISTRY.containsKey(type.getId())) {
            throw new IllegalStateException("Damage type already registered: " + type.getId());
        }
        REGISTRY.put(type.getId(), type);
        return type;
    }

    public static TotalityDamageType get(Identifier id) {
        return REGISTRY.get(id);
    }

    public static Collection<TotalityDamageType> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static boolean contains(Identifier id) {
        return REGISTRY.containsKey(id);
    }
}