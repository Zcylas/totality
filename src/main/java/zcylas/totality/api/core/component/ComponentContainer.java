package zcylas.totality.api.core.component;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds all components attached to a single provider.
 * Stored on the provider and queried via ComponentKey.
 */
public final class ComponentContainer {

    private final Map<ComponentKey<?>, TotalityComponent> components = new LinkedHashMap<>();

    public <C extends TotalityComponent> void put(ComponentKey<C> key, C component) {
        components.put(key, component);
    }

    @SuppressWarnings("unchecked")
    public @Nullable <C extends TotalityComponent> C get(ComponentKey<C> key) {
        return (C) components.get(key);
    }

    public Iterable<Map.Entry<ComponentKey<?>, TotalityComponent>> entries() {
        return components.entrySet();
    }
}