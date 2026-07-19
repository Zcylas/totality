package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

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

    private static ResourceSnapshot successSnapshot(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.Success.class, result);
        return ((ResourceQueryResult.Success) result).snapshot();
    }

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
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, 300, 300, 1));
        assertEquals(300L, snapshot.currentUnits());
        assertEquals(300L, snapshot.maximumUnits());
    }

    @Test
    void partialAirNormalizesUnchanged() {
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, 150, 300, 1));
        assertEquals(150L, snapshot.currentUnits());
        assertEquals(300L, snapshot.maximumUnits());
    }

    @Test
    void zeroAirNormalizesToZero() {
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, 0, 300, 1));
        assertEquals(0L, snapshot.currentUnits());
    }

    @Test
    void negativeAirClampsToZeroNotNegative() {
        // Vanilla air ticks down to -20 as its own drowning-damage timer (see LivingEntity.baseTick
        // / shouldTakeDrowningDamage in the vanilla air audit) — that negative range is
        // owner-specific metadata, never exposed as negative generic Breath.
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, -20, 300, 1));
        assertEquals(0L, snapshot.currentUnits());
    }

    @Test
    void deeplyNegativeAirStillClampsToZero() {
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, Integer.MIN_VALUE, 300, 1));
        assertEquals(0L, snapshot.currentUnits());
    }

    @Test
    void currentAboveMaximumClampsDownToMaximum() {
        // No known vanilla path produces current > max, but the adapter must not fabricate an
        // invalid (current > maximum) snapshot if it ever did — clamp rather than pass through.
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, 400, 300, 1));
        assertEquals(300L, snapshot.currentUnits());
        assertEquals(300L, snapshot.maximumUnits());
    }

    @Test
    void dynamicMaximumDifferentFromVanillaBaselineIsRespected() {
        // getMaxAirSupply() is virtual — a future entity type could override it. The adapter must
        // never hardcode 300; it must reflect whatever live maximum it was actually given.
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, 500, 600, 1));
        assertEquals(500L, snapshot.currentUnits());
        assertEquals(600L, snapshot.maximumUnits());
    }

    @Test
    void zeroMaximumProducesMalformedOwnerStateFailure() {
        ResourceQueryResult result = BreathResourceAdapter.normalize(ID, 100, 0, 1);
        assertInstanceOf(ResourceQueryResult.Failure.class, result,
                "a non-positive maximum must be a typed decline, not a fabricated snapshot");
        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, ((ResourceQueryResult.Failure) result).reason());
        assertEquals(ID, ((ResourceQueryResult.Failure) result).resourceId());
    }

    @Test
    void negativeMaximumProducesMalformedOwnerStateFailure() {
        ResourceQueryResult result = BreathResourceAdapter.normalize(ID, 100, -5, 1);
        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, ((ResourceQueryResult.Failure) result).reason());
    }

    @Test
    void resultCurrentAndMaximumAreExactIntegersNoFloatConversion() {
        // Breath is an int-native vanilla quantity end to end (SynchedEntityData int field) — the
        // normalization core must never round-trip through float/double.
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, 137, 300, 1));
        assertEquals(137L, snapshot.currentUnits());
        assertEquals(300L, snapshot.maximumUnits());
    }

    @Test
    void unitScaleIsAlwaysOne() {
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, 100, 300, 1));
        assertEquals(1L, snapshot.unitScale());
    }

    @Test
    void resourceIdIsPreservedOnTheSnapshot() {
        ResourceSnapshot snapshot = successSnapshot(BreathResourceAdapter.normalize(ID, 100, 300, 1));
        assertEquals(ID, snapshot.resourceId());
    }
}
