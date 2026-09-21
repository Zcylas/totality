package zcylas.totality.api.rpg.resources.integration;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.MaximumChangePolicy;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.TestResourceBootstrap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the real, production {@code class progression -> totality:health_recovery_dice} grant
 * provider — registered via {@code ProductionResourceDefinitions.register() -> registerGrants() ->
 * HealthRecoveryDiceResources.register()} — declares exactly the shape canonical §25.10/§16.2
 * requires for a {@code CLASS} grant (Phase 7A, 2026-09-16; renamed from the working name
 * {@code totality:hit_dice}/{@code HitDiceResourcesTest} before commit — this Generic Resource is
 * NOT the future Hit Die API, a separate, unimplemented Character Creation/Progression system).
 * Mirrors {@link StandardSpellSlotResourcesTest} exactly, except this provider's qualification
 * check ({@code hasAnyClass()}) dereferences {@code player}, so {@code getResourceGrants(null)} is
 * expected to yield no grant rather than a static one, matching the same established
 * nullable-player convention.
 */
class HealthRecoveryDiceResourcesTest {

    private static Optional<ResourceGrant> grantFor(net.minecraft.resources.Identifier resourceId) {
        List<ResourceGrant> all = ResourceGrantRegistry.INSTANCE.providers().stream()
                .flatMap(provider -> provider.getResourceGrants(null).stream())
                .toList();
        return all.stream().filter(g -> g.resourceId().equals(resourceId)).findFirst();
    }

    @Test
    void theProviderYieldsNoGrantForANullPlayerRatherThanThrowing() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(grantFor(PlayerResourceIds.HEALTH_RECOVERY_DICE).isEmpty(),
                "a null player has no class ownership to check, so no grant should be produced — and this must not throw");
    }

    @Test
    void theSourceIdIsTheCanonicalClassProgressionHealthRecoveryDiceIdentifier() {
        assertEquals(net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "class_progression_health_recovery_dice"),
                HealthRecoveryDiceResources.SOURCE_ID);
    }

    @Test
    void theRegisteredGrantPolicyIsSharedResourceAggregationWithPreserveDeficitMaximumChangePolicy() {
        // Canonical §25.10's explicit choice for this resource — distinct from Standard Spell
        // Slots' SINGLE_OWNER — and the deficit-preserving level-up behavior this task requires.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var policy = ResourceGrantPolicyRegistry.INSTANCE.get(PlayerResourceIds.HEALTH_RECOVERY_DICE);
        assertEquals(ResourceGrantAggregationPolicy.SHARED_RESOURCE, policy.aggregation());
        assertEquals(ResourceRemovalPolicy.REMOVE_STATE, policy.removal());
        assertEquals(MaximumChangePolicy.PRESERVE_DEFICIT, policy.maximumChangePolicy());
    }

    @Test
    void theObsoleteHitDiceWorkingNameIsNotRegisteredAnywhere() {
        // Guards the correction itself: totality:hit_dice must never resurface as a registered
        // resource id now that the production identifier is totality:health_recovery_dice.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var oldId = net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "hit_dice");
        assertTrue(zcylas.totality.api.rpg.resources.PlayerResourceRegistry.INSTANCE.get(oldId).isEmpty(),
                "totality:hit_dice must not be registered — the corrected id is totality:health_recovery_dice");
    }
}
