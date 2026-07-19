package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.external.ExternalResourceClientMirrorMode;
import zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport;
import zcylas.totality.api.rpg.resources.external.HealthResourceAdapter;
import zcylas.totality.api.rpg.resources.external.FoodResourceAdapter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-registry validation: a {@link PlayerResourceDefinition} declaring {@code EXTERNAL_ADAPTER}
 * authority must resolve to a real, registered {@link ExternalPlayerResourceAdapter} by the time
 * {@link PlayerResourceRegistry#freeze(ExternalPlayerResourceAdapterRegistry)} runs. Uses isolated
 * registries throughout so it never touches production {@code INSTANCE} state.
 */
class PlayerResourceRegistryExternalAdapterFreezeTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static ExternalPlayerResourceAdapter fakeAdapter(Identifier id) {
        return new ExternalPlayerResourceAdapter() {
            @Override public Identifier id() { return id; }
            @Override public ResourceSnapshot snapshot(Player player, PlayerResourceDefinition definition) {
                return new ResourceSnapshot(id, 1, 2, 1);
            }
            @Override public Set<ExternalResourceOperationSupport> supportedOperations() {
                return Set.of(ExternalResourceOperationSupport.QUERY);
            }
            @Override public ExternalResourceClientMirrorMode clientMirrorMode() {
                return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
            }
        };
    }

    @Test
    void definitionResolvesARegisteredAdapterAndFreezeSucceeds() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        ExternalPlayerResourceAdapterRegistry adapters = new ExternalPlayerResourceAdapterRegistry();
        adapters.register(fakeAdapter(id("fake")));

        registry.register(PlayerResourceDefinition.builder(id("fake"), ResourceModel.SCALAR)
                .externalAdapter(id("fake"))
                .authoredBaseMaximum(100)
                .build());

        assertDoesNotThrow(() -> registry.freeze(adapters));
        assertTrue(registry.isFrozen());
    }

    @Test
    void missingAdapterCausesFreezeValidationFailure() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        ExternalPlayerResourceAdapterRegistry adapters = new ExternalPlayerResourceAdapterRegistry();
        // Deliberately never registered into `adapters`.

        registry.register(PlayerResourceDefinition.builder(id("missing_adapter"), ResourceModel.SCALAR)
                .externalAdapter(id("missing_adapter"))
                .authoredBaseMaximum(100)
                .build());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> registry.freeze(adapters));
        assertTrue(ex.getMessage().contains("missing_adapter"));
        // Left unfrozen — the definition registered so far remains intact and inspectable.
        assertFalse(registry.isFrozen());
        assertTrue(registry.isRegistered(id("missing_adapter")));
    }

    @Test
    void genericComponentDefinitionsNeedNoAdapterAndDoNotBlockFreeze() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        ExternalPlayerResourceAdapterRegistry adapters = new ExternalPlayerResourceAdapterRegistry();

        registry.register(PlayerResourceDefinition.builder(id("generic_only"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .build());

        assertDoesNotThrow(() -> registry.freeze(adapters));
    }

    @Test
    void nullAdapterRegistryIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        assertThrows(NullPointerException.class, () -> registry.freeze(null));
    }

    @Test
    void productionHealthAndFoodDefinitionsResolveTheirRegisteredAdapters() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition health = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow();
        PlayerResourceDefinition food = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow();

        assertEquals(HealthResourceAdapter.ID, health.externalAdapterId().orElseThrow());
        assertEquals(FoodResourceAdapter.ID, food.externalAdapterId().orElseThrow());
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(health.externalAdapterId().orElseThrow()));
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(food.externalAdapterId().orElseThrow()));
    }
}
