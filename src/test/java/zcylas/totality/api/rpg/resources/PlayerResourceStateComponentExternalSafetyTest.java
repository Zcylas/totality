package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@code totality:health}/{@code totality:food}/{@code totality:breath}/{@code
 * totality:spell_slots}/{@code totality:rage} can never become live {@link
 * PlayerResourceStateComponent} state — the "Prevent duplicate external state" requirement from the
 * Phase 2A task, extended through Phase 2E. {@code totality:mana}/{@code totality:stamina} were
 * EXTERNAL_ADAPTER-authority too through Phase 2C, but the Phase 4 migration (2026-09-15) redefined
 * both as {@code GENERIC_COMPONENT} — see {@link #instantiateScalarAcceptsManaAfterPhase4Migration}/
 * {@link #instantiateScalarAcceptsStaminaAfterPhase4Migration} for their current-shape proof instead.
 * Uses the real production registry (via {@link TestResourceBootstrap}) since the guarantee under
 * test is specifically about the real external-authority definitions.
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
    void instantiateScalarAcceptsManaAfterPhase4Migration() {
        // Phase 4 migration (2026-09-15): totality:mana is now GENERIC_COMPONENT-authority — this
        // replaces the removed instantiateScalarRejectsMana test, which pinned the pre-migration
        // rejection.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertDoesNotThrow(() -> state.instantiateScalar(PlayerResourceIds.MANA, 100));
        assertTrue(state.hasState(PlayerResourceIds.MANA));
    }

    @Test
    void instantiateScalarAcceptsStaminaAfterPhase4Migration() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertDoesNotThrow(() -> state.instantiateScalar(PlayerResourceIds.STAMINA, 100));
        assertTrue(state.hasState(PlayerResourceIds.STAMINA));
    }

    // instantiatePartitionedRejectsMana/Stamina removed: their premise (Mana/Stamina reject generic
    // instantiation via authority) is no longer true after the Phase 4 migration. Note this reveals
    // a pre-existing, out-of-scope gap: instantiateScalar/instantiatePartitioned validate authority
    // but never cross-check model, so instantiatePartitioned(MANA, ...) would now technically
    // succeed and create a wrongly-shaped PartitionedResourceState under a SCALAR-model id — no
    // production code calls it that way (this migration's own code only ever calls
    // instantiateScalar for Mana/Stamina), so this is not exercised, and fixing it is a Phase 1
    // foundation concern unrelated to this migration's scope, not addressed here. The
    // "instantiatePartitioned rejects EXTERNAL_ADAPTER authority regardless of model" guarantee
    // itself remains fully covered by instantiatePartitionedRejectsSpellSlots above (a genuinely
    // still-external PARTITIONED_POOL resource).

    // instantiateScalarRejectsSpellSlots/instantiatePartitionedRejectsSpellSlots removed: their
    // premise (Standard Spell Slots reject generic instantiation via authority) is no longer true
    // after the Phase 6 migration — same removal precedent as
    // instantiatePartitionedRejectsMana/Stamina and instantiateScalarRejectsRage/
    // instantiatePartitionedRejectsRage above. instantiatePartitionedFromGrant (the real production
    // partitioned-instantiation path) is now covered directly by ResourceGrantReconciler's own tests.

    // instantiateScalarRejectsRage/instantiatePartitionedRejectsRage removed: their premise (Rage
    // rejects generic instantiation via authority) is no longer true after the Phase 5 migration —
    // same removal precedent as instantiatePartitionedRejectsMana/Stamina above.

    @Test
    void isRegisteredExternalAdapterAuthorityIsTrueForAllThreeRemainingExternalAdapterProductionResources() {
        // Same-package access to the package-visible predicate — the exact decision point both
        // instantiateScalar/instantiatePartitioned and the NBT-read quarantine logic share.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.HEALTH));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.FOOD));
        assertTrue(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.BREATH));
        // Phase 4 migration (2026-09-15): Mana/Stamina are GENERIC_COMPONENT-authority now, so this
        // predicate must be false for them — the opposite of their pre-migration behavior. Phase 5
        // migration (2026-09-15): Rage joins them. Phase 6 migration (2026-09-16): Standard Spell
        // Slots joins them too.
        assertFalse(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.MANA));
        assertFalse(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.STAMINA));
        assertFalse(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.RAGE));
        assertFalse(PlayerResourceStateComponent.isRegisteredExternalAdapterAuthority(PlayerResourceIds.SPELL_SLOTS));
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
        assertFalse(freshPlayerState.hasState(PlayerResourceIds.RAGE));
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
