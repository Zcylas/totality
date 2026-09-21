package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of {@link ExternalPlayerResourceAdapter}s. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §6.5.
 *
 * Instantiable rather than a pure static singleton, matching
 * {@link zcylas.totality.api.rpg.resources.PlayerResourceRegistry}'s Phase 1 precedent — tests
 * need isolated registries that don't share mutable static registration/freeze state across the
 * whole test JVM. Production code uses {@link #INSTANCE}.
 */
public final class ExternalPlayerResourceAdapterRegistry {

    public static final ExternalPlayerResourceAdapterRegistry INSTANCE = new ExternalPlayerResourceAdapterRegistry();

    private final Map<Identifier, ExternalPlayerResourceAdapter> adapters = new LinkedHashMap<>();
    private volatile boolean frozen = false;

    public ExternalPlayerResourceAdapterRegistry() {}

    /**
     * Rejects a null adapter; a null id; a null, empty, or null-containing operation-support set;
     * an operation-support set that does not declare {@link ExternalResourceOperationSupport#QUERY}
     * (every Phase 2A adapter is query-only, so this is the one operation every registered adapter
     * must actually support — see the correction pass's "Enforce operation-support declarations"
     * requirement); a null client-mirror-mode declaration; a duplicate id; and any registration
     * attempted after {@link #freeze()}. A {@link Set} cannot itself contain a duplicate element by
     * definition, so "duplicate declarations within one adapter's own support set" is not a
     * separate failure mode to detect here.
     */
    public synchronized ExternalPlayerResourceAdapter register(ExternalPlayerResourceAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        Identifier id = Objects.requireNonNull(adapter.id(), "adapter.id()");

        Set<ExternalResourceOperationSupport> support = adapter.supportedOperations();
        Objects.requireNonNull(support, id + ": supportedOperations() must not be null");
        if (support.isEmpty()) {
            throw new IllegalArgumentException(id + ": supportedOperations() must declare at least one operation");
        }
        // Deliberately iterates (never calls support.contains(null)): Set.of(...) — used by every
        // adapter shipped in this codebase — throws NullPointerException from contains(null)
        // itself, which would make this null-entry guard indistinguishable from "adapter is fine,
        // uses Set.of()". Iterating is safe for any Set implementation, malformed or not.
        for (ExternalResourceOperationSupport operation : support) {
            if (operation == null) {
                throw new IllegalArgumentException(id + ": supportedOperations() must not contain a null entry");
            }
        }
        if (!support.contains(ExternalResourceOperationSupport.QUERY)) {
            throw new IllegalArgumentException(
                    id + ": every Phase 2A external adapter must declare QUERY support (this phase is query-only)");
        }

        Objects.requireNonNull(adapter.clientMirrorMode(), id + ": clientMirrorMode() must not be null");

        if (frozen) {
            throw new IllegalStateException(
                    "Cannot register external adapter " + id + " — ExternalPlayerResourceAdapterRegistry is frozen");
        }
        if (adapters.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate external adapter id: " + id);
        }
        adapters.put(id, adapter);
        return adapter;
    }

    public Optional<ExternalPlayerResourceAdapter> get(Identifier id) {
        return Optional.ofNullable(adapters.get(id));
    }

    public boolean isRegistered(Identifier id) {
        return adapters.containsKey(id);
    }

    public Collection<ExternalPlayerResourceAdapter> all() {
        return Collections.unmodifiableCollection(adapters.values());
    }

    public int size() {
        return adapters.size();
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
