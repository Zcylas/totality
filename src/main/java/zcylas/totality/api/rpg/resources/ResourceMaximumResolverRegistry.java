package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of {@link ResourceMaximumResolver}s, keyed by resource id — one resolver per
 * {@code GENERIC_COMPONENT} resource that needs a dynamic (rather than fixed-authored) maximum.
 * Instantiable (not a pure static singleton) so tests get isolated state, matching
 * {@link PlayerResourceRegistry}/{@link zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry}'s
 * established precedent; production code uses {@link #INSTANCE}.
 *
 * <p>Unlike {@link PlayerResourceRegistry}, this registry is not frozen at startup: a resource is
 * free to have no resolver at all (it then falls back to its definition's
 * {@code authoredBaseMaximum}, or fails {@link ResourceFailureCode#MAXIMUM_ZERO}/query's
 * {@link ResourceQueryFailureReason#MAXIMUM_UNAVAILABLE} if it has neither) — a missing resolver is
 * an ordinary, valid configuration, not a structural error to catch at freeze time.
 */
public final class ResourceMaximumResolverRegistry {

    public static final ResourceMaximumResolverRegistry INSTANCE = new ResourceMaximumResolverRegistry();

    private final Map<Identifier, ResourceMaximumResolver> resolvers = new HashMap<>();

    public void register(Identifier resourceId, ResourceMaximumResolver resolver) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(resolver, "resolver");
        if (resolvers.containsKey(resourceId)) {
            throw new IllegalStateException("A maximum resolver is already registered for " + resourceId);
        }
        resolvers.put(resourceId, resolver);
    }

    public Optional<ResourceMaximumResolver> get(Identifier resourceId) {
        return Optional.ofNullable(resolvers.get(resourceId));
    }

    public boolean has(Identifier resourceId) {
        return resolvers.containsKey(resourceId);
    }
}
