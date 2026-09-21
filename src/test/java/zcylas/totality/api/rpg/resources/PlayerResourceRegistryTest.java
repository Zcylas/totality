package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each test constructs its own {@link PlayerResourceRegistry} instance rather than using
 * {@link PlayerResourceRegistry#INSTANCE}, so tests do not share mutable static state.
 */
class PlayerResourceRegistryTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static PlayerResourceDefinition scalarDef(String path) {
        return PlayerResourceDefinition.builder(id(path), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .build();
    }

    @Test
    void successfulRegistrationIsRetrievable() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = scalarDef("test_resource");

        registry.register(def);

        assertTrue(registry.isRegistered(id("test_resource")));
        assertEquals(def, registry.get(id("test_resource")).orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    void duplicateIdIsRejectedAndDoesNotReplace() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition first = scalarDef("test_resource");
        registry.register(first);

        PlayerResourceDefinition second = PlayerResourceDefinition
                .builder(id("test_resource"), ResourceModel.SCALAR)
                .authoredBaseMaximum(999)
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(second));
        // No silent replacement: the original definition must still be the one stored.
        assertEquals(first, registry.get(id("test_resource")).orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    void invalidUnitScaleIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bad_scale"), ResourceModel.SCALAR)
                .unitScale(0)
                .authoredBaseMaximum(100)
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void authoredMaximumNotGreaterThanMinimumIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bad_bounds"), ResourceModel.SCALAR)
                .absoluteMinimum(50)
                .authoredBaseMaximum(50)
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void targetRangePolarityWithoutRangeIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bad_polarity"), ResourceModel.SCALAR)
                .polarity(ResourcePolarity.TARGET_RANGE)
                .authoredBaseMaximum(100)
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void targetRangePolarityWithRangeIsAccepted() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bounded_range_resource"), ResourceModel.SCALAR)
                .polarity(ResourcePolarity.TARGET_RANGE)
                .absoluteMinimum(-100)
                .authoredBaseMaximum(100)
                .targetRange(new ResourceTargetRange(20, 40,
                        java.util.OptionalLong.empty(), java.util.OptionalLong.empty()))
                .build();

        assertDoesNotThrow(() -> registry.register(def));
    }

    @Test
    void targetRangeBelowAbsoluteMinimumIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bad_range_low"), ResourceModel.SCALAR)
                .polarity(ResourcePolarity.TARGET_RANGE)
                .absoluteMinimum(0)
                .authoredBaseMaximum(100)
                // preferredMinimumUnits (-10) is below absoluteMinimum (0).
                .targetRange(new ResourceTargetRange(-10, 40,
                        java.util.OptionalLong.empty(), java.util.OptionalLong.empty()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void targetRangeAboveAuthoredMaximumIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bad_range_high"), ResourceModel.SCALAR)
                .polarity(ResourcePolarity.TARGET_RANGE)
                .absoluteMinimum(0)
                .authoredBaseMaximum(100)
                // preferredMaximumUnits (150) exceeds authoredBaseMaximum (100).
                .targetRange(new ResourceTargetRange(20, 150,
                        java.util.OptionalLong.empty(), java.util.OptionalLong.empty()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void targetRangeExactlyAtAbsoluteBoundsIsAccepted() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("range_at_bounds"), ResourceModel.SCALAR)
                .polarity(ResourcePolarity.TARGET_RANGE)
                .absoluteMinimum(0)
                .authoredBaseMaximum(100)
                .targetRange(new ResourceTargetRange(0, 100,
                        java.util.OptionalLong.empty(), java.util.OptionalLong.empty()))
                .build();

        assertDoesNotThrow(() -> registry.register(def));
    }

    @Test
    void externalAdapterAuthorityWithoutAdapterIdIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        // Bypass the builder's externalAdapter() helper to construct an invalid combination directly.
        PlayerResourceDefinition def = new PlayerResourceDefinition(
                id("bad_authority"),
                ResourceModel.SCALAR,
                ResourcePolarity.HIGH_IS_GOOD,
                ResourceStateAuthority.EXTERNAL_ADAPTER,
                java.util.Optional.empty(),
                1, 0, java.util.OptionalLong.of(100),
                java.util.Set.of(), java.util.Optional.empty(),
                ResourceLifecyclePolicy.DEFAULT, 1,
                java.util.Optional.empty()
        );

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void genericComponentAuthorityWithAdapterIdIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = new PlayerResourceDefinition(
                id("bad_authority_2"),
                ResourceModel.SCALAR,
                ResourcePolarity.HIGH_IS_GOOD,
                ResourceStateAuthority.GENERIC_COMPONENT,
                java.util.Optional.of(id("some_adapter")),
                1, 0, java.util.OptionalLong.of(100),
                java.util.Set.of(), java.util.Optional.empty(),
                ResourceLifecyclePolicy.DEFAULT, 1,
                java.util.Optional.empty()
        );

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void externalAdapterHelperProducesValidDefinition() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("health"), ResourceModel.SCALAR)
                .externalAdapter(id("health"))
                .authoredBaseMaximum(20)
                .build();

        assertDoesNotThrow(() -> registry.register(def));
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER, registry.get(id("health")).orElseThrow().stateAuthority());
    }

    @Test
    void partitionedSpendingCapabilityOnScalarModelIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bad_capability"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .capability(ResourceCapability.PARTITIONED_SPENDING)
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void partitionedSpendingCapabilityOnPartitionedModelIsAccepted() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("spell_slots_like"), ResourceModel.PARTITIONED_POOL)
                .capability(ResourceCapability.PARTITIONED_SPENDING)
                .build();

        assertDoesNotThrow(() -> registry.register(def));
    }

    @Test
    void freezeBlocksFurtherRegistration() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDef("before_freeze"));

        assertFalse(registry.isFrozen());
        registry.freeze();
        assertTrue(registry.isFrozen());

        assertThrows(IllegalStateException.class, () -> registry.register(scalarDef("after_freeze")));
        // The already-registered definition must remain intact.
        assertTrue(registry.isRegistered(id("before_freeze")));
        assertFalse(registry.isRegistered(id("after_freeze")));
    }

    @Test
    void unknownIdLookupReturnsEmptyNotException() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        assertTrue(registry.get(id("nonexistent")).isEmpty());
        assertFalse(registry.isRegistered(id("nonexistent")));
    }

    @Test
    void productionSingletonContainsExactlyHealthAndBreathAsExternalAdapters() {
        // Phase 1 registered zero production resources; Phase 2A added Health and Food; Phase 2B
        // added Breath. Food is no longer in this list — the 2026-09-17 Food migration redefines it
        // as GENERIC_COMPONENT (see productionFoodIsGenericComponentAuthorityAfterTheFoodMigration
        // below), the same shape as Mana/Stamina/Rage/Spell Slots below. Health and Breath remain
        // EXTERNAL_ADAPTER-authority, query-only (see ProductionResourceDefinitions).
        // Phase 2C originally added Mana/Stamina as transitional EXTERNAL_ADAPTER too, but the Phase
        // 4 migration (2026-09-15) redefines both as GENERIC_COMPONENT — see
        // productionManaAndStaminaAreGenericComponentAuthorityAfterPhase4Migration below. Phase 2E
        // originally added totality:rage the same way, but the Phase 5 migration (2026-09-15)
        // redefines it as GENERIC_COMPONENT too — see
        // productionRageIsGenericComponentAuthorityAfterPhase5Migration below. Phase 2D originally
        // added totality:spell_slots the same way, but the Phase 6 migration (2026-09-16) redefines
        // it as GENERIC_COMPONENT too — see
        // productionSpellSlotsIsGenericComponentAuthorityAfterPhase6Migration below.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier resourceId : new Identifier[] {
                PlayerResourceIds.HEALTH, PlayerResourceIds.BREATH
        }) {
            assertTrue(PlayerResourceRegistry.INSTANCE.isRegistered(resourceId), () -> resourceId + " must be registered");
            assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                    PlayerResourceRegistry.INSTANCE.get(resourceId).orElseThrow().stateAuthority(),
                    () -> resourceId + " must be EXTERNAL_ADAPTER");
        }
        assertTrue(PlayerResourceRegistry.INSTANCE.isFrozen());
    }

    @Test
    void productionFoodIsGenericComponentAuthorityAfterTheFoodMigration() {
        // 2026-09-17 Food migration: same EXTERNAL_ADAPTER -> GENERIC_COMPONENT shape as the Phase
        // 4/5/6 migrations, but Food's legacy owner is vanilla's own FoodData engine, not
        // Totality-owned code — see FoodVanillaCompatibilityBridge and the implementation report.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceRegistry.INSTANCE.isRegistered(PlayerResourceIds.FOOD));
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow();
        assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(),
                "totality:food must be GENERIC_COMPONENT after the Food migration");
        assertTrue(definition.externalAdapterId().isEmpty(), "totality:food must declare no external adapter");
    }

    @Test
    void productionManaAndStaminaAreGenericComponentAuthorityAfterPhase4Migration() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier resourceId : new Identifier[] {PlayerResourceIds.MANA, PlayerResourceIds.STAMINA}) {
            assertTrue(PlayerResourceRegistry.INSTANCE.isRegistered(resourceId), () -> resourceId + " must be registered");
            PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(resourceId).orElseThrow();
            assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(),
                    () -> resourceId + " must be GENERIC_COMPONENT after the Phase 4 migration");
            assertTrue(definition.externalAdapterId().isEmpty(), () -> resourceId + " must declare no external adapter");
        }
    }

    @Test
    void productionRageIsGenericComponentAuthorityAfterPhase5Migration() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceRegistry.INSTANCE.isRegistered(PlayerResourceIds.RAGE));
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow();
        assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(),
                "totality:rage must be GENERIC_COMPONENT after the Phase 5 migration");
        assertTrue(definition.externalAdapterId().isEmpty(), "totality:rage must declare no external adapter");
    }

    @Test
    void productionSpellSlotsIsGenericComponentAuthorityAfterPhase6Migration() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceRegistry.INSTANCE.isRegistered(PlayerResourceIds.SPELL_SLOTS));
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS).orElseThrow();
        assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(),
                "totality:spell_slots must be GENERIC_COMPONENT after the Phase 6 migration");
        assertTrue(definition.externalAdapterId().isEmpty(), "totality:spell_slots must declare no external adapter");
        assertEquals(ResourceModel.PARTITIONED_POOL, definition.model(), "PARTITIONED_POOL is unchanged by the authority migration");
    }

    @Test
    void productionHealthRecoveryDiceIsGenericComponentPartitionedPoolAfterPhase7AMigration() {
        // totality:health_recovery_dice (working name during this pass: "Hit Dice"/totality:hit_dice,
        // corrected before commit — NOT the future Hit Die API, a separate Character
        // Creation/Progression system) never had a legacy authority of any kind (confirmed by
        // repo-wide search before Phase 7A) — this is a first-time registration, not a migration
        // from EXTERNAL_ADAPTER.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceRegistry.INSTANCE.isRegistered(PlayerResourceIds.HEALTH_RECOVERY_DICE));
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH_RECOVERY_DICE).orElseThrow();
        assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(),
                "totality:health_recovery_dice must be GENERIC_COMPONENT");
        assertTrue(definition.externalAdapterId().isEmpty(), "totality:health_recovery_dice must declare no external adapter");
        assertEquals(ResourceModel.PARTITIONED_POOL, definition.model(), "totality:health_recovery_dice must be PARTITIONED_POOL");
        assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity());
        assertEquals(0L, definition.absoluteMinimum());
        assertTrue(definition.capabilities().contains(ResourceCapability.SPENDABLE));
        assertTrue(definition.capabilities().contains(ResourceCapability.RESTORABLE));
        assertTrue(definition.capabilities().contains(ResourceCapability.PARTITIONED_SPENDING));
        assertTrue(definition.capabilities().contains(ResourceCapability.MENU_VISIBLE));
    }

    @Test
    void productionRegistryDoesNotContainTheObsoleteHitDiceWorkingName() {
        // Guards the correction itself: totality:hit_dice was this resource's working name during
        // the Phase 7A pass and must never be registered now that the production id is
        // totality:health_recovery_dice.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var oldId = net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "hit_dice");
        assertTrue(PlayerResourceRegistry.INSTANCE.get(oldId).isEmpty(),
                "totality:hit_dice must not be registered");
    }

    @Test
    void productionSingletonAlsoContainsExactlyThreeDormantGenericComponentDefinitions() {
        // The dormant Resource Registration pass adds totality:thirst, totality:sanity, and
        // totality:ki on top of the seven other production definitions above (three EXTERNAL_ADAPTER
        // — Health/Food/Breath — plus four GENERIC_COMPONENT — Mana/Stamina/Rage/Spell Slots) — ten
        // total. Phase 7A adds an eleventh, real (non-dormant) GENERIC_COMPONENT definition,
        // totality:health_recovery_dice — the dormant count itself is unaffected (still exactly
        // three). The 2026-09-17 Food migration moves Food from the EXTERNAL_ADAPTER group to the
        // GENERIC_COMPONENT group (now two EXTERNAL_ADAPTER — Health/Breath — plus five
        // GENERIC_COMPONENT — Mana/Stamina/Rage/Spell Slots/Food) without changing the total count. All three dormant ones are GENERIC_COMPONENT-authority (no externalAdapterId), the first of their kind
        // in production. totality:fatigue and totality:temperature are deliberately NOT registered —
        // see the dormant-registration implementation report for why.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertEquals(11, PlayerResourceRegistry.INSTANCE.size());
        for (Identifier resourceId : new Identifier[] {
                PlayerResourceIds.THIRST, PlayerResourceIds.SANITY, PlayerResourceIds.KI
        }) {
            PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(resourceId)
                    .orElseThrow(() -> new AssertionError(resourceId + " must be registered"));
            assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(),
                    () -> resourceId + " must be GENERIC_COMPONENT");
            assertTrue(definition.externalAdapterId().isEmpty(), () -> resourceId + " must have no external adapter");
            assertEquals(ResourceModel.SCALAR, definition.model(), () -> resourceId + " must be SCALAR");
            assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity(), () -> resourceId + " must be HIGH_IS_GOOD");
            assertEquals(0L, definition.absoluteMinimum(), () -> resourceId + " must have absoluteMinimum 0");
            assertTrue(definition.capabilities().isEmpty(),
                    () -> resourceId + " must declare no capabilities while dormant");
            assertTrue(definition.presentation().isEmpty(),
                    () -> resourceId + " must declare no presentation while dormant");
        }
    }

    @Test
    void thirstAndSanityDeclareAnAuthoredOneHundredMaximum() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertEquals(100L, PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.THIRST)
                .orElseThrow().authoredBaseMaximum().orElseThrow());
        assertEquals(100L, PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SANITY)
                .orElseThrow().authoredBaseMaximum().orElseThrow());
    }

    @Test
    void kiDeclaresNoAuthoredMaximumSinceItIsGenuinelyDynamic() {
        // Canonical §25.7: Ki's maximum is set "by Monk level/features" — genuinely dynamic, not yet
        // resolvable. No ResourceMaximumResolver framework exists (Phase 1 scope), so the only honest
        // representation is leaving authoredBaseMaximum absent rather than inventing e.g. 100.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.KI)
                .orElseThrow().authoredBaseMaximum().isEmpty(),
                "Ki must declare no authored maximum — its ceiling is future class-progression-resolved");
    }

    @Test
    void dormantDefinitionsHaveNoRegisteredExternalAdapter() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier resourceId : new Identifier[] {
                PlayerResourceIds.THIRST, PlayerResourceIds.SANITY, PlayerResourceIds.KI
        }) {
            assertFalse(zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry.INSTANCE
                    .isRegistered(resourceId), () -> resourceId + " must have no registered external adapter");
        }
    }

    @Test
    void temperatureAndFatigueAreNotRegistered() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertFalse(PlayerResourceRegistry.INSTANCE.isRegistered(id("temperature")));
        assertFalse(PlayerResourceRegistry.INSTANCE.isRegistered(id("fatigue")));
        assertFalse(PlayerResourceRegistry.INSTANCE.isRegistered(id("rest_need")));
    }

    @Test
    void bothRemainingScalarExternalAdapterProductionDefinitionsAreExternalScalarResources() {
        // Deliberately excludes totality:spell_slots: it is PARTITIONED_POOL-model, not SCALAR — see
        // spellSlotsDefinitionIsPartitionedPoolGenericComponentAfterPhase6Migration below for its own
        // shape assertions. Mana/Stamina moved to
        // productionManaAndStaminaAreScalarGenericComponentResourcesAfterPhase4Migration below after
        // the Phase 4 migration (2026-09-15) redefined them as GENERIC_COMPONENT; Rage moved to
        // productionRageIsScalarGenericComponentResourceAfterPhase5Migration below after the Phase 5
        // migration (2026-09-15) redefined it the same way; Food moved to
        // foodIsAScalarGenericComponentResourceAfterTheFoodMigration below after the 2026-09-17 Food
        // migration redefined it the same way.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier resourceId : new Identifier[] {
                PlayerResourceIds.HEALTH, PlayerResourceIds.BREATH
        }) {
            PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(resourceId).orElseThrow();
            assertEquals(ResourceModel.SCALAR, definition.model(), () -> resourceId + " must be SCALAR");
            assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER, definition.stateAuthority(), () -> resourceId + " must be EXTERNAL_ADAPTER");
            assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity(), () -> resourceId + " must be HIGH_IS_GOOD");
            assertEquals(0L, definition.absoluteMinimum(), () -> resourceId + " must have absoluteMinimum 0");
        }
    }

    @Test
    void foodIsAScalarGenericComponentResourceAfterTheFoodMigration() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow();
        assertEquals(ResourceModel.SCALAR, definition.model(), "totality:food must be SCALAR");
        assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(), "totality:food must be GENERIC_COMPONENT");
        assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity(), "totality:food must be HIGH_IS_GOOD");
        assertEquals(0L, definition.absoluteMinimum(), "totality:food must have absoluteMinimum 0");
    }

    @Test
    void productionManaAndStaminaAreScalarGenericComponentResourcesAfterPhase4Migration() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier resourceId : new Identifier[] {PlayerResourceIds.MANA, PlayerResourceIds.STAMINA}) {
            PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(resourceId).orElseThrow();
            assertEquals(ResourceModel.SCALAR, definition.model(), () -> resourceId + " must be SCALAR");
            assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(), () -> resourceId + " must be GENERIC_COMPONENT");
            assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity(), () -> resourceId + " must be HIGH_IS_GOOD");
            assertEquals(0L, definition.absoluteMinimum(), () -> resourceId + " must have absoluteMinimum 0");
        }
    }

    @Test
    void productionRageIsScalarGenericComponentResourceAfterPhase5Migration() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow();
        assertEquals(ResourceModel.SCALAR, definition.model(), "totality:rage must be SCALAR");
        assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority(), "totality:rage must be GENERIC_COMPONENT");
        assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity(), "totality:rage must be HIGH_IS_GOOD");
        assertEquals(0L, definition.absoluteMinimum(), "totality:rage must have absoluteMinimum 0");
    }

    @Test
    void rageDeclaresNoAuthoredMaximumAndBumpedDefinitionVersionAfterPhase5Migration() {
        // Mirrors productionManaAndStaminaDeclareNoAuthoredMaximumAndBumpedDefinitionVersionAfterPhase4Migration:
        // an authored base wins outright over a registered resolver, so Rage must declare NO
        // authoredBaseMaximum — otherwise RageMaximumResolver (registered in
        // ProductionResourceDefinitions) would never actually run. definitionVersion bumped 1 -> 2:
        // the Phase 5 migration is a real, explicit redefinition of what totality:rage means, never a
        // silent structural hot-swap.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition rage = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow();

        assertEquals(zcylas.totality.api.rpg.resources.external.RageResourceAdapter.UNIT_SCALE, rage.unitScale(),
                "the definition's unitScale must stay in lockstep with the adapter's own canonical constant");
        assertEquals(1L, zcylas.totality.api.rpg.resources.external.RageResourceAdapter.UNIT_SCALE);
        assertTrue(rage.authoredBaseMaximum().isEmpty(),
                "an authored maximum would silently short-circuit the registered RageMaximumResolver");
        assertEquals(2, rage.definitionVersion());
        assertTrue(rage.externalAdapterId().isEmpty(), "totality:rage must declare no external adapter after Phase 5");
    }

    @Test
    void productionRageDeclaresExactlyItsCanonicalPhase5CapabilitySet() {
        // Canonical §25.6's exact declared capability set for totality:rage — SPENDABLE/RESTORABLE/
        // MAXIMUM_MODIFIERS/HUD_VISIBLE/MENU_VISIBLE, a real authoritative GENERIC_COMPONENT
        // resource, not the query-only transitional shape Phase 2E originally declared (HUD_VISIBLE/
        // MENU_VISIBLE only). Phase 5 Rage migration, 2026-09-15.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        java.util.Set<ResourceCapability> expected = java.util.Set.of(
                ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE,
                ResourceCapability.MAXIMUM_MODIFIERS, ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE);

        var capabilities = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow().capabilities();
        assertEquals(expected, capabilities);
        // Canonical §25.6 does not declare PASSIVE_REGENERATION/DIRECT_DRAIN/CLIENT_PREDICTION/
        // PARTITIONED_SPENDING for Rage — no passive regen, no continuous drain, no client
        // prediction, and Rage is SCALAR not partitioned.
        assertFalse(capabilities.contains(ResourceCapability.PASSIVE_REGENERATION));
        assertFalse(capabilities.contains(ResourceCapability.DIRECT_DRAIN));
        assertFalse(capabilities.contains(ResourceCapability.CLIENT_PREDICTION));
        assertFalse(capabilities.contains(ResourceCapability.PARTITIONED_SPENDING));
    }

    @Test
    void rageDeclaresContextualAccessPipsPresentation() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var presentation = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow()
                .presentation().orElseThrow(() -> new AssertionError("totality:rage must declare presentation metadata"));
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceDisplayType.PIPS, presentation.displayType());
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceHudRole.CONTEXTUAL_ACCESS, presentation.hudRole());
        assertEquals(1, presentation.displayConversion().numerator());
        assertEquals(1, presentation.displayConversion().denominator());
    }

    @Test
    void spellSlotsDefinitionIsPartitionedPoolGenericComponentAfterPhase6Migration() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS).orElseThrow();
        assertEquals(ResourceModel.PARTITIONED_POOL, definition.model());
        assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, definition.stateAuthority());
        assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity());
        assertEquals(zcylas.totality.api.rpg.resources.external.StandardSpellSlotsResourceAdapter.UNIT_SCALE, definition.unitScale(),
                "the definition keeps using the retired adapter's canonical unitScale constant for continuity");
        assertEquals(0L, definition.absoluteMinimum());
        assertTrue(definition.authoredBaseMaximum().isEmpty(),
                "authoredBaseMaximum is scalar-shaped and must not be declared for a 9-partition resource");
        assertEquals(2, definition.definitionVersion(), "the Phase 6 authority migration must bump this, never silently redefine the id");
        assertTrue(definition.externalAdapterId().isEmpty(), "totality:spell_slots must declare no external adapter");
    }

    @Test
    void spellSlotsDeclaresSpendableRestorablePartitionedSpendingAndMenuVisibleAfterPhase6Migration() {
        // Spell slots belong in the spell radial / a future Spells app / character screens, not the
        // ordinary resource-bar HUD — still no HUD_VISIBLE. Phase 6 migration: PlayerResourceService
        // now genuinely mutates this resource (ActivateAbilityHandler's cast-spend, Long Rest
        // restore), so SPENDABLE/RESTORABLE/PARTITIONED_SPENDING are now declared, unlike the
        // pre-migration query-only adapter shape.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var capabilities = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS).orElseThrow().capabilities();
        assertEquals(java.util.Set.of(ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE,
                ResourceCapability.PARTITIONED_SPENDING, ResourceCapability.MENU_VISIBLE), capabilities);
        assertFalse(capabilities.contains(ResourceCapability.HUD_VISIBLE));
    }

    @Test
    void spellSlotsDeclaresMenuOnlySlotsPresentation() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var presentation = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS).orElseThrow()
                .presentation().orElseThrow(() -> new AssertionError("totality:spell_slots must declare presentation metadata"));
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceDisplayType.SLOTS, presentation.displayType());
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceHudRole.MENU_ONLY, presentation.hudRole());
        assertEquals(1, presentation.displayConversion().numerator());
        assertEquals(1, presentation.displayConversion().denominator());
    }

    @Test
    void productionSingletonHasNoOxygenAirOrTemperatureDefinition() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceRegistry.INSTANCE.get(id("temperature")).isEmpty(),
                "Temperature must not be registered as a Resource API resource (current decision, see readiness audit)");
        assertTrue(PlayerResourceRegistry.INSTANCE.get(id("oxygen")).isEmpty(),
                "The canonical name is totality:breath, not totality:oxygen");
        assertTrue(PlayerResourceRegistry.INSTANCE.get(id("air")).isEmpty(),
                "The canonical name is totality:breath, not totality:air");
    }

    @Test
    void productionSingletonHasNoBarbarianRageLegacyKeyAsAResourceId() {
        // totality:rage IS now registered (Phase 2E) — see
        // productionSingletonContainsExactlyHealthFoodBreathManaStaminaSpellSlotsAndRage above. But
        // the legacy PlayerChargesComponent backing key, totality:barbarian_rage, is a different,
        // deliberately distinct identifier that must never itself become a registered
        // PlayerResourceDefinition id — the Resource API resource id and the legacy pool key are not
        // interchangeable (see RageResourceAdapter's class Javadoc).
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(PlayerResourceRegistry.INSTANCE.isRegistered(PlayerResourceIds.RAGE));
        assertTrue(PlayerResourceRegistry.INSTANCE.get(id("barbarian_rage")).isEmpty());
    }

    @Test
    void productionManaDeclaresExactlyItsCanonicalPhase4CapabilitySet() {
        // Final external-review correction pass (2026-09-15): the original Phase 4 migration left
        // Mana/Stamina's capability metadata at its old Phase 2C transitional shape (HUD_VISIBLE/
        // MENU_VISIBLE only) even after redefining both as real, authoritative GENERIC_COMPONENT
        // resources — this pins canonical §25.4's exact declared set instead. Replaces the removed
        // productionManaAndStaminaDeclareExactlyHudVisibleAndMenuVisibleCapabilities, which pinned
        // the now-corrected wrong set.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        java.util.Set<ResourceCapability> expected = java.util.Set.of(
                ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE,
                ResourceCapability.PASSIVE_REGENERATION, ResourceCapability.MAXIMUM_MODIFIERS,
                ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE);

        assertEquals(expected, PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow().capabilities());
        // Canonical §25.4 does not declare DIRECT_DRAIN or CLIENT_PREDICTION for Mana (those are
        // Stamina-specific — see productionStaminaDeclaresExactlyItsCanonicalPhase4CapabilitySet).
        assertFalse(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow()
                .capabilities().contains(ResourceCapability.DIRECT_DRAIN));
        assertFalse(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow()
                .capabilities().contains(ResourceCapability.CLIENT_PREDICTION));
    }

    @Test
    void productionStaminaDeclaresExactlyItsCanonicalPhase4CapabilitySet() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        java.util.Set<ResourceCapability> expected = java.util.Set.of(
                ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE, ResourceCapability.DIRECT_DRAIN,
                ResourceCapability.PASSIVE_REGENERATION, ResourceCapability.MAXIMUM_MODIFIERS,
                ResourceCapability.CLIENT_PREDICTION, ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE);

        assertEquals(expected, PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.STAMINA).orElseThrow().capabilities());
    }

    @Test
    void productionManaAndStaminaDeclareNoAuthoredMaximumAndBumpedDefinitionVersionAfterPhase4Migration() {
        // External-review-hardened precedence (see the pre-Phase-4 foundation correction pass,
        // Issue 7): an authored base wins outright over a registered resolver, so Mana/Stamina must
        // declare NO authoredBaseMaximum — otherwise ManaMaximumResolver/StaminaMaximumResolver
        // (registered in ProductionResourceDefinitions) would never actually run, collapsing the
        // real dynamic formula down to a flat, unmodified base. definitionVersion bumped 1 -> 2:
        // the Phase 4 migration is a real, explicit redefinition of what these two ids mean, never a
        // silent structural hot-swap (matching Ki's own no-authored-maximum precedent for a
        // genuinely dynamic resource).
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition mana = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow();
        PlayerResourceDefinition stamina = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.STAMINA).orElseThrow();

        assertEquals(1L, mana.unitScale());
        assertEquals(1L, stamina.unitScale());
        assertEquals(0L, mana.absoluteMinimum());
        assertEquals(0L, stamina.absoluteMinimum());
        assertTrue(mana.authoredBaseMaximum().isEmpty(),
                "an authored maximum would silently short-circuit the registered ManaMaximumResolver");
        assertTrue(stamina.authoredBaseMaximum().isEmpty());
        assertEquals(2, mana.definitionVersion());
        assertEquals(2, stamina.definitionVersion());
    }

    @Test
    void productionHealthDeclaresExactlyHudVisibleAndMenuVisibleCapabilities() {
        // Correction pass: player-visible constant HUD resources should declare HUD_VISIBLE/
        // MENU_VISIBLE, and must NOT declare a mutation capability merely because their owning
        // vanilla system (Health/Combat) can itself change the value — Phase 2A's generic adapters
        // remain query-only. Food moved to
        // foodDeclaresRestorableDirectDrainHudVisibleAndMenuVisibleAfterTheFoodMigration below — as a
        // real GENERIC_COMPONENT resource it now genuinely mutates through this façade (eating,
        // exhaustion), unlike Health, which is still query-only.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        java.util.Set<ResourceCapability> expected = java.util.Set.of(
                ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE);

        assertEquals(expected,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow().capabilities());

        for (ResourceCapability mutationCapability : new ResourceCapability[] {
                ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE, ResourceCapability.DIRECT_DRAIN
        }) {
            assertFalse(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow()
                    .capabilities().contains(mutationCapability));
        }
    }

    @Test
    void foodDeclaresRestorableDirectDrainHudVisibleAndMenuVisibleAfterTheFoodMigration() {
        // See FoodResourceDefinitionTest for the fuller Food-specific capability assertions; this
        // keeps this file's own Health/Food-parity narrative intact for anyone reading it top to
        // bottom.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var capabilities = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow().capabilities();
        assertEquals(java.util.Set.of(ResourceCapability.RESTORABLE, ResourceCapability.DIRECT_DRAIN,
                ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE), capabilities);
        assertFalse(capabilities.contains(ResourceCapability.SPENDABLE));
    }

    @Test
    void productionBreathDeclaresExactlyHudVisibleAndMenuVisibleCapabilities() {
        // Phase 2B: Breath is query-only, same capability contract as Health/Food — HUD/MENU
        // visibility without any mutation capability.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        java.util.Set<ResourceCapability> expected = java.util.Set.of(
                ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE);

        assertEquals(expected,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.BREATH).orElseThrow().capabilities());

        for (ResourceCapability mutationCapability : new ResourceCapability[] {
                ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE, ResourceCapability.DIRECT_DRAIN
        }) {
            assertFalse(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.BREATH).orElseThrow()
                    .capabilities().contains(mutationCapability));
        }
    }

    @Test
    void productionBreathDeclaresAuditedVanillaBaselineMaximum() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition breath = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.BREATH).orElseThrow();
        assertEquals(1L, breath.unitScale());
        assertEquals(0L, breath.absoluteMinimum());
        assertEquals(300L, breath.authoredBaseMaximum().orElseThrow(),
                "authored baseline is descriptive only — the live query path never consults it");
    }

    @Test
    void productionHealthDeclaresCorePresentationMetadataOnTheFiveOverOneConversion() {
        // Food used to share this exact ×5 presentation; the 2026-09-17 Food migration moves it to
        // foodDeclaresCorePresentationMetadataOnIdentity below — Food is now natively 0-100, so its
        // conversion is IDENTITY (1/1), not 5/1. Health is untouched by the Food migration.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertPresentationIsCoreConstantBar(
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow(), 5, 1);
    }

    @Test
    void foodDeclaresCorePresentationMetadataOnIdentity() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertPresentationIsCoreConstantBar(
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow(), 1, 1);
    }

    private static void assertPresentationIsCoreConstantBar(PlayerResourceDefinition definition, int numerator, int denominator) {
        var presentation = definition.presentation().orElseThrow(
                () -> new AssertionError(definition.id() + " must declare presentation metadata"));
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceDisplayType.BAR, presentation.displayType());
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceHudRole.CORE_CONSTANT, presentation.hudRole());
        assertEquals(numerator, presentation.displayConversion().numerator());
        assertEquals(denominator, presentation.displayConversion().denominator());
    }

    @Test
    void zeroDefinitionVersionIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bad_version"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .definitionVersion(0)
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void negativeDefinitionVersionIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("bad_version_2"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .definitionVersion(-1)
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void defaultDefinitionVersionIsPositiveAndAccepted() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        // scalarDef() never calls .definitionVersion(...) — confirms the builder default (1) passes.
        assertDoesNotThrow(() -> registry.register(scalarDef("default_version")));
        assertEquals(1, registry.get(id("default_version")).orElseThrow().definitionVersion());
    }

    @Test
    void atAbsoluteInitializationReferencingWrongResourceIdIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("wrong_id_init"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .initialization(new ResourceGrantInitialization.AtAbsolute(
                        ResourceAmount.scalar(id("a_completely_different_resource"), 10)))
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void atAbsoluteInitializationOnScalarWithPartitionIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("scalar_with_partition"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .initialization(new ResourceGrantInitialization.AtAbsolute(
                        ResourceAmount.partitioned(id("scalar_with_partition"), 1, 10)))
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void atAbsoluteInitializationOnPartitionedWithoutPartitionIsRejected() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("partitioned_without_partition"), ResourceModel.PARTITIONED_POOL)
                .initialization(new ResourceGrantInitialization.AtAbsolute(
                        ResourceAmount.scalar(id("partitioned_without_partition"), 10)))
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(def));
    }

    @Test
    void atAbsoluteInitializationOnScalarWithoutPartitionIsAccepted() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("valid_scalar_absolute"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .initialization(new ResourceGrantInitialization.AtAbsolute(
                        ResourceAmount.scalar(id("valid_scalar_absolute"), 10)))
                .build();

        assertDoesNotThrow(() -> registry.register(def));
    }

    @Test
    void atAbsoluteInitializationOnPartitionedWithPartitionIsAccepted() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition def = PlayerResourceDefinition
                .builder(id("valid_partitioned_absolute"), ResourceModel.PARTITIONED_POOL)
                .initialization(new ResourceGrantInitialization.AtAbsolute(
                        ResourceAmount.partitioned(id("valid_partitioned_absolute"), 1, 10)))
                .build();

        assertDoesNotThrow(() -> registry.register(def));
    }

    @Test
    void nonAtAbsoluteInitializationVariantsRequireNoCrossCheck() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition atMax = PlayerResourceDefinition
                .builder(id("uses_at_maximum"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .initialization(new ResourceGrantInitialization.AtMaximum())
                .build();
        PlayerResourceDefinition atFraction = PlayerResourceDefinition
                .builder(id("uses_at_fraction"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .initialization(new ResourceGrantInitialization.AtFraction(1, 2))
                .build();

        assertDoesNotThrow(() -> registry.register(atMax));
        assertDoesNotThrow(() -> registry.register(atFraction));
    }
}
