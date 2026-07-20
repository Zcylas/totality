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
import zcylas.totality.api.rpg.resources.external.BreathResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ManaResourceAdapter;
import zcylas.totality.api.rpg.resources.external.RageResourceAdapter;
import zcylas.totality.api.rpg.resources.external.StaminaResourceAdapter;
import zcylas.totality.api.rpg.resources.external.StandardSpellSlotsResourceAdapter;

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
            @Override public ResourceQueryResult snapshot(Player player, PlayerResourceDefinition definition) {
                return new ResourceQueryResult.Success(new ResourceSnapshot(id, 1, 2, 1));
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

    @Test
    void productionBreathDefinitionResolvesItsRegisteredAdapter() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition breath = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.BREATH).orElseThrow();

        assertEquals(BreathResourceAdapter.ID, breath.externalAdapterId().orElseThrow());
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(breath.externalAdapterId().orElseThrow()));
        assertSame(BreathResourceAdapter.INSTANCE,
                ExternalPlayerResourceAdapterRegistry.INSTANCE.get(breath.externalAdapterId().orElseThrow()).orElseThrow());
    }

    @Test
    void productionManaAndStaminaDefinitionsResolveTheirRegisteredAdapters() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition mana = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow();
        PlayerResourceDefinition stamina = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.STAMINA).orElseThrow();

        assertEquals(ManaResourceAdapter.ID, mana.externalAdapterId().orElseThrow());
        assertEquals(StaminaResourceAdapter.ID, stamina.externalAdapterId().orElseThrow());
        assertSame(ManaResourceAdapter.INSTANCE,
                ExternalPlayerResourceAdapterRegistry.INSTANCE.get(mana.externalAdapterId().orElseThrow()).orElseThrow());
        assertSame(StaminaResourceAdapter.INSTANCE,
                ExternalPlayerResourceAdapterRegistry.INSTANCE.get(stamina.externalAdapterId().orElseThrow()).orElseThrow());
        assertEquals(1, mana.definitionVersion(), "transitional legacy-adapter representation must be explicit at version 1");
        assertEquals(1, stamina.definitionVersion());
    }

    @Test
    void productionAdapterRegistryContainsExactlySevenAdapters() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertEquals(7, ExternalPlayerResourceAdapterRegistry.INSTANCE.size());
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(HealthResourceAdapter.ID));
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(FoodResourceAdapter.ID));
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(BreathResourceAdapter.ID));
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(ManaResourceAdapter.ID));
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(StaminaResourceAdapter.ID));
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(StandardSpellSlotsResourceAdapter.ID));
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(RageResourceAdapter.ID));
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isFrozen());
    }

    @Test
    void productionRageDefinitionResolvesItsRegisteredAdapter() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition rage = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow();

        assertEquals(RageResourceAdapter.ID, rage.externalAdapterId().orElseThrow());
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(rage.externalAdapterId().orElseThrow()));
        assertSame(RageResourceAdapter.INSTANCE,
                ExternalPlayerResourceAdapterRegistry.INSTANCE.get(rage.externalAdapterId().orElseThrow()).orElseThrow());
        assertEquals(1, rage.definitionVersion(), "transitional legacy-adapter representation must be explicit at version 1");
    }

    @Test
    void productionRageQueryOnANonServerPlayerReturnsStateUnavailableOnThisSideNotAnException() {
        // Exercises the FULL production query path end to end — PlayerResourceService.query ->
        // queryExternal -> the real registered RageResourceAdapter.snapshot, using `null` as the
        // player, exactly like the Mana/Stamina/SpellSlots tests above/below.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(null, PlayerResourceIds.RAGE);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE,
                ((ResourceQueryResult.Failure) result).reason());
        assertEquals(PlayerResourceIds.RAGE, ((ResourceQueryResult.Failure) result).resourceId());
    }

    @Test
    void productionSpellSlotsDefinitionResolvesItsRegisteredAdapter() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition spellSlots = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS).orElseThrow();

        assertEquals(StandardSpellSlotsResourceAdapter.ID, spellSlots.externalAdapterId().orElseThrow());
        assertTrue(ExternalPlayerResourceAdapterRegistry.INSTANCE.isRegistered(spellSlots.externalAdapterId().orElseThrow()));
        assertSame(StandardSpellSlotsResourceAdapter.INSTANCE,
                ExternalPlayerResourceAdapterRegistry.INSTANCE.get(spellSlots.externalAdapterId().orElseThrow()).orElseThrow());
        assertEquals(1, spellSlots.definitionVersion(), "transitional legacy-adapter representation must be explicit at version 1");
    }

    @Test
    void productionSpellSlotsQueryOnANonServerPlayerReturnsStateUnavailableOnThisSideNotAnException() {
        // Exercises the FULL production query path end to end, exactly like the Mana/Stamina tests
        // below — PlayerResourceService.query -> queryExternal -> the real registered
        // StandardSpellSlotsResourceAdapter.snapshot, using `null` as the player.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(null, PlayerResourceIds.SPELL_SLOTS);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE,
                ((ResourceQueryResult.Failure) result).reason());
        assertEquals(PlayerResourceIds.SPELL_SLOTS, ((ResourceQueryResult.Failure) result).resourceId());
    }

    @Test
    void productionManaQueryOnANonServerPlayerReturnsStateUnavailableOnThisSideNotAnException() {
        // Exercises the FULL production query path end to end — PlayerResourceService.query ->
        // queryExternal -> the real registered ManaResourceAdapter.snapshot -- using `null` as the
        // player, which fails the `instanceof ServerPlayer` check exactly like a real client-side
        // LocalPlayer would (neither is a ServerPlayer). No fake/mock Player construction needed:
        // this is the real production adapter, actually invoked.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(null, PlayerResourceIds.MANA);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE,
                ((ResourceQueryResult.Failure) result).reason());
        assertEquals(PlayerResourceIds.MANA, ((ResourceQueryResult.Failure) result).resourceId());
    }

    @Test
    void productionStaminaQueryOnANonServerPlayerReturnsStateUnavailableOnThisSideNotAnException() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(null, PlayerResourceIds.STAMINA);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE,
                ((ResourceQueryResult.Failure) result).reason());
        assertEquals(PlayerResourceIds.STAMINA, ((ResourceQueryResult.Failure) result).resourceId());
    }
}
