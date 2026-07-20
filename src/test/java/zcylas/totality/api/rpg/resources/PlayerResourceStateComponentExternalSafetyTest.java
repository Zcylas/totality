package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@code totality:health}/{@code totality:food}/{@code totality:breath}/{@code totality:mana}/
 * {@code totality:stamina} can never become live {@link PlayerResourceStateComponent} state — the
 * "Prevent duplicate external state" requirement from the Phase 2A task, extended to Breath in
 * Phase 2B and to Mana/Stamina in Phase 2C (whose transitional {@code EXTERNAL_ADAPTER} authority
 * over the legacy {@code PlayerResourceComponent} store gets exactly the same protection
 * automatically — this class proves that generalization holds, not just documents it). Uses the
 * real production registry (via {@link TestResourceBootstrap}) since the guarantee under test is
 * specifically about the real external-authority definitions.
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
    void instantiateScalarRejectsMana() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiateScalar(PlayerResourceIds.MANA, 100));
        assertFalse(state.hasState(PlayerResourceIds.MANA));
    }

    @Test
    void instantiateScalarRejectsStamina() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiateScalar(PlayerResourceIds.STAMINA, 100));
        assertFalse(state.hasState(PlayerResourceIds.STAMINA));
    }

    @Test
    void instantiatePartitionedRejectsMana() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiatePartitioned(PlayerResourceIds.MANA));
        assertFalse(state.hasState(PlayerResourceIds.MANA));
    }

    @Test
    void instantiatePartitionedRejectsStamina() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiatePartitioned(PlayerResourceIds.STAMINA));
        assertFalse(state.hasState(PlayerResourceIds.STAMINA));
    }

    @Test
    void instantiateScalarRejectsSpellSlots() {
        // totality:spell_slots is EXTERNAL_ADAPTER-authority (Phase 2D) — instantiateScalar must
        // reject it exactly like every other external-authority resource, even though its own model
        // is PARTITIONED_POOL, not SCALAR (rejectExternalAuthority checks authority, not model).
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiateScalar(PlayerResourceIds.SPELL_SLOTS, 5));
        assertFalse(state.hasState(PlayerResourceIds.SPELL_SLOTS));
    }

    @Test
    void instantiatePartitionedRejectsSpellSlots() {
        // The more natural-looking call for a PARTITIONED_POOL resource — still rejected, since
        // authority (not model) is what generic instantiation cares about.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertThrows(IllegalArgumentException.class,
                () -> state.instantiatePartitioned(PlayerResourceIds.SPELL_SLOTS));
        assertFalse(state.hasState(PlayerResourceIds.SPELL_SLOTS));
    }

    @Test
    void isRegisteredExternalAdapterAuthorityIsTrueForAllSixProductionResources() {
        // Same-package access to the package-visible predicate — the exact decision point both
        // instantiateScalar/instantiatePartitioned and the NBT-read quarantine logic share.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.HEALTH));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.FOOD));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.BREATH));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.MANA));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.STAMINA));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.SPELL_SLOTS));
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
        assertFalse(freshPlayerState.hasState(PlayerResourceIds.MANA));
        assertFalse(freshPlayerState.hasState(PlayerResourceIds.STAMINA));
        assertFalse(freshPlayerState.hasState(PlayerResourceIds.SPELL_SLOTS));
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
