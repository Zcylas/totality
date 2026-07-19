package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@code totality:health}/{@code totality:food}/{@code totality:breath} can never become
 * live {@link PlayerResourceStateComponent} state — the "Prevent duplicate external state"
 * requirement from the Phase 2A task, extended to Breath in Phase 2B. Uses the real production
 * registry (via {@link TestResourceBootstrap}) since the guarantee under test is specifically about
 * the real external-authority definitions.
 */
class PlayerResourceStateComponentExternalSafetyTest {

    @Test
    void instantiateScalarRejectsHealth() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiateScalar(PlayerResourceIds.HEALTH, 20_000));
        assertFalse(state.hasState(PlayerResourceIds.HEALTH));
    }

    @Test
    void instantiateScalarRejectsFood() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiateScalar(PlayerResourceIds.FOOD, 20));
        assertFalse(state.hasState(PlayerResourceIds.FOOD));
    }

    @Test
    void instantiatePartitionedRejectsHealth() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiatePartitioned(PlayerResourceIds.HEALTH));
        assertFalse(state.hasState(PlayerResourceIds.HEALTH));
    }

    @Test
    void instantiatePartitionedRejectsFood() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiatePartitioned(PlayerResourceIds.FOOD));
        assertFalse(state.hasState(PlayerResourceIds.FOOD));
    }

    @Test
    void instantiateScalarRejectsBreath() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiateScalar(PlayerResourceIds.BREATH, 300));
        assertFalse(state.hasState(PlayerResourceIds.BREATH));
    }

    @Test
    void instantiatePartitionedRejectsBreath() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiatePartitioned(PlayerResourceIds.BREATH));
        assertFalse(state.hasState(PlayerResourceIds.BREATH));
    }

    @Test
    void isRegisteredExternalAdapterAuthorityIsTrueOnlyForHealthFoodAndBreath() {
        // Same-package access to the package-visible predicate — the exact decision point both
        // instantiateScalar/instantiatePartitioned and the NBT-read quarantine logic share.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.HEALTH));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.FOOD));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.BREATH));
        assertFalse(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(
                Identifier.fromNamespaceAndPath("totality", "definitely_unregistered")));
    }

    @Test
    void registrationAloneCreatesNoPlayerState() {
        // Registering (and freezing) the production definitions is pure metadata registration —
        // it must never, by itself, create state for any player object.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent freshPlayerState = new PlayerResourceStateComponent(null);

        assertTrue(freshPlayerState.instantiatedResourceIds().isEmpty());
        assertTrue(freshPlayerState.orphanedResourceIds().isEmpty());
        assertFalse(freshPlayerState.hasState(PlayerResourceIds.HEALTH));
        assertFalse(freshPlayerState.hasState(PlayerResourceIds.FOOD));
        assertFalse(freshPlayerState.hasState(PlayerResourceIds.BREATH));
    }

    @Test
    void healthAndFoodProduceNoGenericStateEntriesForAnOrdinaryPlayer() {
        // writeData() only ever iterates the live `states`/`orphanedStates` maps (see
        // PlayerResourceStateComponent#writeData) — an empty component therefore always writes
        // ResourceCount=0/OrphanedCount=0 regardless of which resources exist as definitions.
        // This is the direct consequence already proven by registrationAloneCreatesNoPlayerState()
        // above; asserted again here under this test's more task-literal name for traceability.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertEquals(0, state.instantiatedResourceIds().size());
    }
}
