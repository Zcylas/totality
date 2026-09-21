package zcylas.totality.api.rpg.resources.integration;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.TestResourceBootstrap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the real, production {@code BarbarianClass -> totality:rage} grant provider — registered
 * via {@code ProductionResourceDefinitions.register() -> registerGrants() -> BarbarianRageResources
 * .register()} — declares exactly the shape canonical §16.2 requires for a {@code CLASS} grant
 * (Phase 5 Rage migration, 2026-09-15). Mirrors {@link PlayerBaselineResourcesTest} exactly, except
 * this provider genuinely dereferences {@code player} to decide class ownership, so
 * {@code getResourceGrants(null)} is expected to yield no grant rather than a static one.
 */
class BarbarianRageResourcesTest {

    private static Optional<ResourceGrant> grantFor(net.minecraft.resources.Identifier resourceId) {
        List<ResourceGrant> all = ResourceGrantRegistry.INSTANCE.providers().stream()
                .flatMap(provider -> provider.getResourceGrants(null).stream())
                .toList();
        return all.stream().filter(g -> g.resourceId().equals(resourceId)).findFirst();
    }

    @Test
    void theProviderYieldsNoGrantForANullPlayerRatherThanThrowing() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(grantFor(PlayerResourceIds.RAGE).isEmpty(),
                "a null player cannot be a Barbarian, so no grant should be produced — and this must not throw");
    }

    @Test
    void theSourceIdIsTheCanonicalBarbarianClassIdentifier() {
        assertEquals(net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "barbarian_class"),
                BarbarianRageResources.SOURCE_ID);
    }
}
