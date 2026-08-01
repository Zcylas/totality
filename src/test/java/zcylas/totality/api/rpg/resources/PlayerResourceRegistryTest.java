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
    void productionSingletonContainsExactlyHealthFoodBreathManaStaminaSpellSlotsAndRage() {
        // Phase 1 registered zero production resources; Phase 2A added Health and Food; Phase 2B
        // added Breath; Phase 2C added Mana and Stamina; Phase 2D added totality:spell_slots; Phase
        // 2E adds totality:rage — all seven EXTERNAL_ADAPTER-authority, query-only (see
        // ProductionResourceDefinitions). Mana/Stamina/SpellSlots/Rage are transitional adapters over
        // pre-existing Totality-owned legacy stores, unlike Health/Food/Breath which wrap vanilla
        // directly. These seven remain semantically unchanged by the later dormant-registration pass
        // below — see productionSingletonAlsoContainsExactlyThreeDormantGenericComponentDefinitions.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier resourceId : new Identifier[] {
                PlayerResourceIds.HEALTH, PlayerResourceIds.FOOD, PlayerResourceIds.BREATH,
                PlayerResourceIds.MANA, PlayerResourceIds.STAMINA, PlayerResourceIds.SPELL_SLOTS,
                PlayerResourceIds.RAGE
        }) {
            assertTrue(PlayerResourceRegistry.INSTANCE.isRegistered(resourceId), () -> resourceId + " must be registered");
            assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                    PlayerResourceRegistry.INSTANCE.get(resourceId).orElseThrow().stateAuthority(),
                    () -> resourceId + " must be EXTERNAL_ADAPTER");
        }
        assertTrue(PlayerResourceRegistry.INSTANCE.isFrozen());
    }

    @Test
    void productionSingletonAlsoContainsExactlyThreeDormantGenericComponentDefinitions() {
        // The dormant Resource Registration pass adds totality:thirst, totality:sanity, and
        // totality:ki on top of the seven EXTERNAL_ADAPTER definitions above — ten total. All three
        // are GENERIC_COMPONENT-authority (no externalAdapterId), the first of their kind in
        // production. totality:fatigue and totality:temperature are deliberately NOT registered —
        // see the dormant-registration implementation report for why.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertEquals(10, PlayerResourceRegistry.INSTANCE.size());
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
    void allSixScalarProductionDefinitionsAreExternalScalarResources() {
        // Deliberately excludes totality:spell_slots (Phase 2D): it is PARTITIONED_POOL-model, not
        // SCALAR — see spellSlotsDefinitionIsPartitionedPoolExternalAdapter below for its own shape
        // assertions. totality:rage (Phase 2E) IS scalar and is included here.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier resourceId : new Identifier[] {
                PlayerResourceIds.HEALTH, PlayerResourceIds.FOOD, PlayerResourceIds.BREATH,
                PlayerResourceIds.MANA, PlayerResourceIds.STAMINA, PlayerResourceIds.RAGE
        }) {
            PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(resourceId).orElseThrow();
            assertEquals(ResourceModel.SCALAR, definition.model(), () -> resourceId + " must be SCALAR");
            assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER, definition.stateAuthority(), () -> resourceId + " must be EXTERNAL_ADAPTER");
            assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity(), () -> resourceId + " must be HIGH_IS_GOOD");
            assertEquals(0L, definition.absoluteMinimum(), () -> resourceId + " must have absoluteMinimum 0");
        }
    }

    @Test
    void rageDefinitionIsScalarExternalAdapter() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow();
        assertEquals(ResourceModel.SCALAR, definition.model());
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER, definition.stateAuthority());
        assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity());
        assertEquals(zcylas.totality.api.rpg.resources.external.RageResourceAdapter.UNIT_SCALE, definition.unitScale(),
                "the definition's unitScale must stay in lockstep with the adapter's own canonical constant");
        assertEquals(1L, zcylas.totality.api.rpg.resources.external.RageResourceAdapter.UNIT_SCALE);
        assertEquals(0L, definition.absoluteMinimum());
        assertEquals(2L, definition.authoredBaseMaximum().orElseThrow(),
                "authored baseline is descriptive only — the live query path never consults it");
        assertEquals(1, definition.definitionVersion());
        assertEquals(zcylas.totality.api.rpg.resources.external.RageResourceAdapter.ID,
                definition.externalAdapterId().orElseThrow());
    }

    @Test
    void rageDeclaresOnlyHudVisibleAndMenuVisibleCapabilities() {
        // Query-only, exactly like every other Phase 2A-2D adapter — no SPENDABLE/RESTORABLE/
        // MAXIMUM_MODIFIERS/PARTITIONED_SPENDING, deferred to a future generic-mutation phase.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var capabilities = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow().capabilities();
        assertEquals(java.util.Set.of(ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE), capabilities);
        assertFalse(capabilities.contains(ResourceCapability.SPENDABLE));
        assertFalse(capabilities.contains(ResourceCapability.RESTORABLE));
        assertFalse(capabilities.contains(ResourceCapability.MAXIMUM_MODIFIERS));
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
    void spellSlotsDefinitionIsPartitionedPoolExternalAdapter() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS).orElseThrow();
        assertEquals(ResourceModel.PARTITIONED_POOL, definition.model());
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER, definition.stateAuthority());
        assertEquals(ResourcePolarity.HIGH_IS_GOOD, definition.polarity());
        assertEquals(zcylas.totality.api.rpg.resources.external.StandardSpellSlotsResourceAdapter.UNIT_SCALE, definition.unitScale(),
                "the definition's unitScale must stay in lockstep with the adapter's own canonical constant");
        assertEquals(0L, definition.absoluteMinimum());
        assertTrue(definition.authoredBaseMaximum().isEmpty(),
                "authoredBaseMaximum is scalar-shaped and must not be declared for a 10-partition resource");
        assertEquals(1, definition.definitionVersion());
        assertEquals(zcylas.totality.api.rpg.resources.external.StandardSpellSlotsResourceAdapter.ID,
                definition.externalAdapterId().orElseThrow());
    }

    @Test
    void spellSlotsDeclaresOnlyMenuVisibleNotHudVisibleOrAnyMutationCapability() {
        // Spell slots belong in the spell radial / a future Spells app / character screens, not the
        // ordinary resource-bar HUD — and this adapter is query-only, exactly like every other
        // Phase 2A/2B/2C adapter, so no SPENDABLE/RESTORABLE/PARTITIONED_SPENDING is declared either.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        var capabilities = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS).orElseThrow().capabilities();
        assertEquals(java.util.Set.of(ResourceCapability.MENU_VISIBLE), capabilities);
        assertFalse(capabilities.contains(ResourceCapability.HUD_VISIBLE));
        assertFalse(capabilities.contains(ResourceCapability.SPENDABLE));
        assertFalse(capabilities.contains(ResourceCapability.RESTORABLE));
        assertFalse(capabilities.contains(ResourceCapability.PARTITIONED_SPENDING));
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
    void productionManaAndStaminaDeclareExactlyHudVisibleAndMenuVisibleCapabilities() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        java.util.Set<ResourceCapability> expected = java.util.Set.of(
                ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE);

        assertEquals(expected, PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow().capabilities());
        assertEquals(expected, PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.STAMINA).orElseThrow().capabilities());

        for (ResourceCapability mutationCapability : new ResourceCapability[] {
                ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE, ResourceCapability.DIRECT_DRAIN
        }) {
            assertFalse(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow()
                    .capabilities().contains(mutationCapability));
            assertFalse(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.STAMINA).orElseThrow()
                    .capabilities().contains(mutationCapability));
        }
    }

    @Test
    void productionManaAndStaminaDeclareAuditedLegacyBaselineMaximumsAndExplicitDefinitionVersion() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        PlayerResourceDefinition mana = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow();
        PlayerResourceDefinition stamina = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.STAMINA).orElseThrow();

        assertEquals(1L, mana.unitScale());
        assertEquals(1L, stamina.unitScale());
        assertEquals(0L, mana.absoluteMinimum());
        assertEquals(0L, stamina.absoluteMinimum());
        assertEquals(100L, mana.authoredBaseMaximum().orElseThrow(),
                "authored baseline is descriptive only — the live query path never consults it");
        assertEquals(100L, stamina.authoredBaseMaximum().orElseThrow());
        assertEquals(1, mana.definitionVersion());
        assertEquals(1, stamina.definitionVersion());
    }

    @Test
    void productionHealthAndFoodDeclareExactlyHudVisibleAndMenuVisibleCapabilities() {
        // Correction pass: player-visible constant HUD resources should declare HUD_VISIBLE/
        // MENU_VISIBLE, and must NOT declare a mutation capability merely because their owning
        // vanilla system (Health/Combat, Food/Hunger) can itself change the value — Phase 2A's
        // generic adapters remain query-only.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        java.util.Set<ResourceCapability> expected = java.util.Set.of(
                ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE);

        assertEquals(expected,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow().capabilities());
        assertEquals(expected,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow().capabilities());

        for (ResourceCapability mutationCapability : new ResourceCapability[] {
                ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE, ResourceCapability.DIRECT_DRAIN
        }) {
            assertFalse(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow()
                    .capabilities().contains(mutationCapability));
            assertFalse(PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow()
                    .capabilities().contains(mutationCapability));
        }
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
    void productionHealthAndFoodDeclareCorePresentationMetadata() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertPresentationIsCoreConstantBar(
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow());
        assertPresentationIsCoreConstantBar(
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow());
    }

    private static void assertPresentationIsCoreConstantBar(PlayerResourceDefinition definition) {
        var presentation = definition.presentation().orElseThrow(
                () -> new AssertionError(definition.id() + " must declare presentation metadata"));
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceDisplayType.BAR, presentation.displayType());
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceHudRole.CORE_CONSTANT, presentation.hudRole());
        assertEquals(5, presentation.displayConversion().numerator());
        assertEquals(1, presentation.displayConversion().denominator());
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
