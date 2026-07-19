package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure normalization logic for Breath, tested directly against {@link BreathResourceAdapter#normalize}
 * rather than through a real {@code Player} (constructing one outside the Minecraft runtime is
 * impractical — matching {@code HealthResourceAdapterConversionTest}'s established precedent).
 * {@link BreathResourceAdapter#snapshot} itself is exercised end-to-end only by the manual
 * smoke-test checklist in the Phase 2B report.
 */
class BreathResourceAdapterTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "breath");

    @Test
    void breathAdapterSupportsOnlyQuery() {
        assertTrue(BreathResourceAdapter.INSTANCE.supportedOperations()
                .contains(ExternalResourceOperationSupport.QUERY));
        assertEquals(1, BreathResourceAdapter.INSTANCE.supportedOperations().size(),
                "Breath is query-only in Phase 2B — no RESTORE/DRAIN/SPEND/SET support");
        assertEquals(ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION,
                BreathResourceAdapter.INSTANCE.clientMirrorMode());
    }

    @Test
    void adapterIdMatchesTheBreathResourceId() {
        assertEquals(ID, BreathResourceAdapter.INSTANCE.id());
        assertEquals(ID, BreathResourceAdapter.ID);
    }

    @Test
    void vanillaBaselineMaximumIsThreeHundred() {
        // Entity.TOTAL_AIR_SUPPLY / Entity.getMaxAirSupply()'s unoverridden default — see the
        // vanilla air audit in BreathResourceAdapter's class Javadoc and the Phase 2B report.
        assertEquals(300, BreathResourceAdapter.VANILLA_BASELINE_MAXIMUM);
    }

    @Test
    void fullAirNormalizesToCurrentEqualsMaximum() {
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 300, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(300L, result.get().currentUnits());
        assertEquals(300L, result.get().maximumUnits());
    }

    @Test
    void partialAirNormalizesUnchanged() {
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 150, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(150L, result.get().currentUnits());
        assertEquals(300L, result.get().maximumUnits());
    }

    @Test
    void zeroAirNormalizesToZero() {
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 0, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().currentUnits());
    }

    @Test
    void negativeAirClampsToZeroNotNegative() {
        // Vanilla air ticks down to -20 as its own drowning-damage timer (see LivingEntity.baseTick
        // / shouldTakeDrowningDamage in the vanilla air audit) — that negative range is
        // owner-specific metadata, never exposed as negative generic Breath.
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, -20, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().currentUnits());
    }

    @Test
    void deeplyNegativeAirStillClampsToZero() {
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, Integer.MIN_VALUE, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().currentUnits());
    }

    @Test
    void currentAboveMaximumClampsDownToMaximum() {
        // No known vanilla path produces current > max, but the adapter must not fabricate an
        // invalid (current > maximum) snapshot if it ever did — clamp rather than pass through.
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 400, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(300L, result.get().currentUnits());
        assertEquals(300L, result.get().maximumUnits());
    }

    @Test
    void dynamicMaximumDifferentFromVanillaBaselineIsRespected() {
        // getMaxAirSupply() is virtual — a future entity type could override it. The adapter must
        // never hardcode 300; it must reflect whatever live maximum it was actually given.
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 500, 600, 1);
        assertTrue(result.isPresent());
        assertEquals(500L, result.get().currentUnits());
        assertEquals(600L, result.get().maximumUnits());
    }

    @Test
    void zeroMaximumProducesMalformedOwnerStateAsEmptyOptional() {
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 100, 0, 1);
        assertTrue(result.isEmpty(), "a non-positive maximum must be a typed decline, not a fabricated snapshot");
    }

    @Test
    void negativeMaximumProducesMalformedOwnerStateAsEmptyOptional() {
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 100, -5, 1);
        assertTrue(result.isEmpty());
    }

    @Test
    void resultCurrentAndMaximumAreExactIntegersNoFloatConversion() {
        // Breath is an int-native vanilla quantity end to end (SynchedEntityData int field) — the
        // normalization core must never round-trip through float/double.
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 137, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(137L, result.get().currentUnits());
        assertEquals(300L, result.get().maximumUnits());
    }

    @Test
    void unitScaleIsAlwaysOne() {
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 100, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().unitScale());
    }

    @Test
    void resourceIdIsPreservedOnTheSnapshot() {
        Optional<ResourceSnapshot> result = BreathResourceAdapter.normalize(ID, 100, 300, 1);
        assertTrue(result.isPresent());
        assertEquals(ID, result.get().resourceId());
    }
}
