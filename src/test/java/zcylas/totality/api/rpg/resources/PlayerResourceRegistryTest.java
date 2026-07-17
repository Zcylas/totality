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
                ResourceLifecyclePolicy.DEFAULT, 1
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
                ResourceLifecyclePolicy.DEFAULT, 1
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
    void productionSingletonHasNoDefinitionsInThisPatch() {
        // This Phase 1 patch registers no production resources — INSTANCE must remain empty.
        assertEquals(0, PlayerResourceRegistry.INSTANCE.size());
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
