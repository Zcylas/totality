package zcylas.totality.api.rpg.resources.presentation;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of {@link ResourceValueFormatter}s, one per resource id. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §19.2.
 *
 * Instantiable rather than a pure static singleton, matching {@link zcylas.totality.api.rpg.resources.PlayerResourceRegistry}'s
 * Phase 1 precedent — tests need isolated registries that don't share mutable static state.
 * Production code uses {@link #INSTANCE}.
 */
public final class ResourceValueFormatterRegistry {

    public static final ResourceValueFormatterRegistry INSTANCE = new ResourceValueFormatterRegistry();

    private final Map<Identifier, ResourceValueFormatter> formatters = new LinkedHashMap<>();
    private volatile boolean frozen = false;

    public ResourceValueFormatterRegistry() {}

    public synchronized ResourceValueFormatter register(ResourceValueFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        Identifier id = Objects.requireNonNull(formatter.resourceId(), "formatter.resourceId()");
        if (frozen) {
            throw new IllegalStateException("Cannot register formatter for " + id + " — registry is frozen");
        }
        if (formatters.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate formatter registration for resource id: " + id);
        }
        formatters.put(id, formatter);
        return formatter;
    }

    public Optional<ResourceValueFormatter> get(Identifier resourceId) {
        return Optional.ofNullable(formatters.get(resourceId));
    }

    public boolean isRegistered(Identifier resourceId) {
        return formatters.containsKey(resourceId);
    }

    public int size() {
        return formatters.size();
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
