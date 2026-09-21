package zcylas.totality.api.rpg.resources.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The set of currently-registered {@link ResourceGrantProvider}s. Instantiable (not a pure static
 * singleton) so tests get isolated state, matching
 * {@link zcylas.totality.api.rpg.resources.PlayerResourceRegistry}'s established precedent;
 * production code uses {@link #INSTANCE}. No provider is registered against {@link #INSTANCE} by
 * this foundation pass — see {@link ResourceGrantReconciler}'s class Javadoc for why.
 */
public final class ResourceGrantRegistry {

    public static final ResourceGrantRegistry INSTANCE = new ResourceGrantRegistry();

    private final List<ResourceGrantProvider> providers = new ArrayList<>();

    public void register(ResourceGrantProvider provider) {
        providers.add(Objects.requireNonNull(provider, "provider"));
    }

    public List<ResourceGrantProvider> providers() {
        return Collections.unmodifiableList(providers);
    }
}
