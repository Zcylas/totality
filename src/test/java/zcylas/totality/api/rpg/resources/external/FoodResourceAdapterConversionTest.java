package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;
import zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure conversion logic for Food, tested directly rather than through a real {@code Player}
 * (constructing {@code FoodData} outside the Minecraft runtime is impractical — same rationale as
 * {@code HealthResourceAdapterConversionTest}). {@link FoodResourceAdapter#snapshot} itself is
 * exercised end-to-end only by the manual smoke-test checklist in the Phase 2A report.
 */
class FoodResourceAdapterConversionTest {

    private static long displayFor(long nativeFoodLevel) {
        // Food's unitScale is 1 — a raw getFoodLevel() int already is the unit amount.
        return ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(nativeFoodLevel, 1);
    }

    @Test
    void nativeMaximumIsTwenty() {
        assertEquals(20, FoodResourceAdapter.NATIVE_MAXIMUM);
    }

    @Test
    void nativeZeroDisplaysAsZero() {
        assertEquals(0L, displayFor(0));
    }

    @Test
    void nativeSixDisplaysAsThirty() {
        assertEquals(30L, displayFor(6));
    }

    @Test
    void nativeSixteenDisplaysAsEighty() {
        assertEquals(80L, displayFor(16));
    }

    @Test
    void nativeTwentyDisplaysAsOneHundred() {
        assertEquals(100L, displayFor(20));
    }

    @Test
    void conversionIsPresentationOnlyAndDoesNotAlterTheSnapshot() {
        // Constructing a snapshot at the native (un-converted) values and only converting for
        // display proves the conversion never touches the authoritative mechanical amount.
        ResourceSnapshot snapshot = new ResourceSnapshot(
                Identifier.fromNamespaceAndPath("totality", "food"), 6, 20, 1);

        assertEquals(6, snapshot.currentUnits(), "mechanical current must stay 6, not 30");
        assertEquals(20, snapshot.maximumUnits(), "mechanical maximum must stay 20, not 100");
        assertEquals(30L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(snapshot.currentUnits(), snapshot.unitScale()));
    }

    @Test
    void foodAdapterSupportsQuery() {
        assertTrue(FoodResourceAdapter.INSTANCE.supportedOperations()
                .contains(ExternalResourceOperationSupport.QUERY));
        assertEquals(ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION,
                FoodResourceAdapter.INSTANCE.clientMirrorMode());
    }

    // ── Correction pass: TotalityHudRenderer's defensive Hunger fallback ────────────────────
    //
    // TotalityHudRenderer.resourceDisplayCurrentMax's fallback for totality:food cannot itself be
    // unit-tested (it is exercised only inside a private render lambda that needs a real client
    // Player). What CAN be pinned here is the exact conversion pattern that fallback now uses —
    // ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(nativeValue, 1) — applied to the
    // same four representative native Food levels the renderer's fallback must stay correct for.
    // A prior version of the fallback passed the raw `hunger, 20` values straight through
    // (equivalent to unitScale=1 with NO conversion applied); this test's non-identity assertions
    // (0->0 is the only value where "converted" and "unconverted" coincide) prove the fallback
    // must go through the shared conversion, not raw passthrough, to satisfy the 0-100 contract.

    @Test
    void hudHungerFallbackConversionMatchesTheSharedFoodConversionAtAllFourReferenceLevels() {
        assertEquals(0L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(0, 1));
        assertEquals(30L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(6, 1));
        assertEquals(80L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(16, 1));
        assertEquals(100L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(20, 1));
    }

    @Test
    void hudHungerFallbackMaximumConversionMatchesNativeTwentyToDisplayOneHundred() {
        // Specifically pins the *maximum* side of the fallback (TotalityHudRenderer's
        // hungerFallbackMax), which is a fixed native 20 -> display 100, independent of the
        // player's actual current hunger level.
        assertEquals(100L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(FoodResourceAdapter.NATIVE_MAXIMUM, 1));
    }

    @Test
    void saturationAndExhaustionHaveNoFieldOnResourceSnapshot() {
        // Structural proof that the generic scalar snapshot cannot carry saturation/exhaustion —
        // ResourceSnapshot's own record components are the only data an adapter can report.
        String[] componentNames = Arrays.stream(ResourceSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);

        assertEquals(4, componentNames.length);
        assertTrue(Arrays.asList(componentNames).containsAll(
                Arrays.asList("resourceId", "currentUnits", "maximumUnits", "unitScale")));
        assertTrue(Arrays.stream(componentNames).noneMatch(
                name -> name.toLowerCase().contains("saturation") || name.toLowerCase().contains("exhaustion")));
    }
}
