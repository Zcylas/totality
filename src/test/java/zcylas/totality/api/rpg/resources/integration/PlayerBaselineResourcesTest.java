package zcylas.totality.api.rpg.resources.integration;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.TestResourceBootstrap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the real, production {@code totality:player_baseline} grant provider — registered via
 * {@code ProductionResourceDefinitions.register() -> registerGrants() -> PlayerBaselineResources
 * .register()} — declares exactly the shape canonical §16.2 requires for a universal
 * {@code GLOBAL_SYSTEM} grant. {@code getResourceGrants} is called with {@code player = null}
 * directly (matching {@link ResourceGrantReconcilerTest}'s own established nullable-player
 * convention): the provider's grants are entirely static/player-independent, so this is a legitimate
 * way to inspect their shape without a real {@code ServerPlayer} — {@code
 * PlayerBaselineResources#reconcile}/{@code #ensureInstantiated} themselves (which do need a real
 * {@code ServerPlayer}) are proven end-to-end instead by
 * {@code BaselineResourceMigrationVerification}, a dev-only self-test.
 */
class PlayerBaselineResourcesTest {

    private static Optional<ResourceGrant> grantFor(net.minecraft.resources.Identifier resourceId) {
        List<ResourceGrant> all = ResourceGrantRegistry.INSTANCE.providers().stream()
                .flatMap(provider -> provider.getResourceGrants(null).stream())
                .toList();
        return all.stream().filter(g -> g.resourceId().equals(resourceId)).findFirst();
    }

    @Test
    void theProductionBaselineProviderGrantsMana() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        ResourceGrant grant = grantFor(PlayerResourceIds.MANA).orElseThrow(() -> new AssertionError("no grant found for totality:mana"));

        assertEquals(PlayerBaselineResources.SOURCE_ID, grant.sourceId());
        assertEquals(ResourceGrantSourceType.GLOBAL_SYSTEM, grant.sourceType());
        assertEquals(ResourceGrantMode.PERSISTENT, grant.mode());
        assertInstanceOf(ResourceGrantInitialization.AtMaximum.class, grant.initialization());
        assertEquals(ResourceRemovalPolicy.REMOVE_STATE, grant.removalPolicy());
        assertEquals(ResourceVisibilityPolicy.ALWAYS_FOR_OWNER, grant.visibilityPolicy());
    }

    @Test
    void theProductionBaselineProviderGrantsStamina() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        ResourceGrant grant = grantFor(PlayerResourceIds.STAMINA).orElseThrow(() -> new AssertionError("no grant found for totality:stamina"));

        assertEquals(PlayerBaselineResources.SOURCE_ID, grant.sourceId());
        assertEquals(ResourceGrantSourceType.GLOBAL_SYSTEM, grant.sourceType());
        assertEquals(ResourceGrantMode.PERSISTENT, grant.mode());
        assertInstanceOf(ResourceGrantInitialization.AtMaximum.class, grant.initialization());
        assertEquals(ResourceRemovalPolicy.REMOVE_STATE, grant.removalPolicy());
        assertEquals(ResourceVisibilityPolicy.ALWAYS_FOR_OWNER, grant.visibilityPolicy());
    }

    @Test
    void theSourceIdIsTheCanonicalPlayerBaselineIdentifier() {
        assertEquals(net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "player_baseline"),
                PlayerBaselineResources.SOURCE_ID);
    }
}
