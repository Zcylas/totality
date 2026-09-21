package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-resource {@link ResourceGrantPolicy}, keyed by resource id.
 *
 * <p><b>Deliberate deviation from canonical's literal wording:</b> §16.4/§16.7 describe aggregation
 * and removal policy as properties the resource <i>definition</i> declares. {@code
 * PlayerResourceDefinition} is a record with ten already-registered production instances and
 * dozens of direct-construction call sites across the test suite (Phase 1 through the dormant
 * Resource Registration pass); adding a mandatory new field to it would force-touch all of them for
 * a concept no production resource uses yet. Declaring the same two policies here instead — as an
 * opt-in sibling registry exactly matching {@link zcylas.totality.api.rpg.resources.ResourceMaximumResolverRegistry}'s
 * already-established pattern for the same reason (a resource may have no resolver at all) — keeps
 * the canonical concept (a resource-level, not grant-level, declared aggregation/removal policy)
 * while touching zero existing code. An unregistered resource id falls back to
 * {@link ResourceGrantPolicy#DEFAULT} (SINGLE_OWNER / REMOVE_STATE, canonical's own stated default
 * removal behavior for class/species/lineage/discipline pools).
 *
 * <p>Instantiable (not a pure static singleton) so tests get isolated state; production code uses
 * {@link #INSTANCE}.
 */
public final class ResourceGrantPolicyRegistry {

    public static final ResourceGrantPolicyRegistry INSTANCE = new ResourceGrantPolicyRegistry();

    private final Map<Identifier, ResourceGrantPolicy> policies = new HashMap<>();

    public void register(Identifier resourceId, ResourceGrantPolicy policy) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(policy, "policy");
        if (policies.containsKey(resourceId)) {
            throw new IllegalStateException("A grant policy is already registered for " + resourceId);
        }
        policies.put(resourceId, policy);
    }

    public ResourceGrantPolicy get(Identifier resourceId) {
        return policies.getOrDefault(resourceId, ResourceGrantPolicy.DEFAULT);
    }
}
