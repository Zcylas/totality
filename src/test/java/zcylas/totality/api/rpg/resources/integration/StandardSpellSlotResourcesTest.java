package zcylas.totality.api.rpg.resources.integration;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.TestResourceBootstrap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the real, production {@code combined multiclass caster progression -> totality:spell_slots}
 * grant provider — registered via {@code ProductionResourceDefinitions.register() ->
 * registerGrants() -> StandardSpellSlotResources.register()} — declares exactly the shape canonical
 * §16.2 requires for a {@code CLASS} grant (Phase 6 Standard Spell Slot migration, 2026-09-16).
 * Mirrors {@link BarbarianRageResourcesTest} exactly, except this provider genuinely dereferences
 * {@code player} to compute the combined caster level, so {@code getResourceGrants(null)} is expected
 * to yield no grant rather than a static one.
 */
class StandardSpellSlotResourcesTest {

    private static Optional<ResourceGrant> grantFor(net.minecraft.resources.Identifier resourceId) {
        List<ResourceGrant> all = ResourceGrantRegistry.INSTANCE.providers().stream()
                .flatMap(provider -> provider.getResourceGrants(null).stream())
                .toList();
        return all.stream().filter(g -> g.resourceId().equals(resourceId)).findFirst();
    }

    @Test
    void theProviderYieldsNoGrantForANullPlayerRatherThanThrowing() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(grantFor(PlayerResourceIds.SPELL_SLOTS).isEmpty(),
                "a null player has no combined caster level, so no grant should be produced — and this must not throw");
    }

    @Test
    void theSourceIdIsTheCanonicalStandardCasterProgressionIdentifier() {
        assertEquals(net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "standard_caster_progression"),
                StandardSpellSlotResources.SOURCE_ID);
    }
}
