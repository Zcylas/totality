package zcylas.totality.api.component;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Central registry for all ComponentKeys.
 * Call getOrCreate() once per component type, typically in a static field.
 */
public final class ComponentRegistry {

    private static final Map<Identifier, ComponentKey<?>> KEYS = new HashMap<>();

    private ComponentRegistry() {}

    public static <C extends TotalityComponent> ComponentKey<C> getOrCreate(
            Identifier id,
            Class<C> componentClass
    ) {
        ComponentKey<?> existing = KEYS.get(id);
        if (existing != null) {
            if (existing.getComponentClass() != componentClass) {
                throw new IllegalStateException(
                        "Component key " + id + " already registered with a different class: "
                                + existing.getComponentClass() + " vs " + componentClass
                );
            }
            @SuppressWarnings("unchecked")
            ComponentKey<C> cast = (ComponentKey<C>) existing;
            return cast;
        }
        ComponentKey<C> key = new ComponentKey<>(id, componentClass);
        KEYS.put(id, key);
        return key;
    }

    public static Optional<ComponentKey<?>> get(Identifier id) {
        return Optional.ofNullable(KEYS.get(id));
    }

    public static Stream<ComponentKey<?>> stream() {
        return KEYS.values().stream();
    }
}