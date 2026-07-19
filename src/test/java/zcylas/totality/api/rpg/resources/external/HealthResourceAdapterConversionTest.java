package zcylas.totality.api.rpg.resources.external;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure fixed-point conversion logic for Health, tested directly rather than through a real
 * {@code Player} (constructing one outside the Minecraft runtime is impractical — see the Phase 2A
 * task's own instruction to isolate and test the pure conversion logic, then cover the actual
 * adapter through a manual client smoke test / future GameTest). {@link HealthResourceAdapter#snapshot}
 * itself is exercised end-to-end only by the manual smoke-test checklist in the Phase 2A report.
 */
class HealthResourceAdapterConversionTest {

    private static final long SCALE = HealthResourceAdapter.UNIT_SCALE;

    private static long displayFor(float mechanicalHealth) {
        long units = HealthResourceAdapter.toUnits(mechanicalHealth, SCALE);
        return ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(units, SCALE);
    }

    @Test
    void unitScaleIsOneThousand() {
        assertEquals(1000L, SCALE);
    }

    @Test
    void healthAdapterSupportsQuery() {
        assertTrue(HealthResourceAdapter.INSTANCE.supportedOperations()
                .contains(ExternalResourceOperationSupport.QUERY));
        assertEquals(ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION,
                HealthResourceAdapter.INSTANCE.clientMirrorMode());
    }

    @Test
    void nativeTwentyDisplaysAsOneHundred() {
        assertEquals(20000L, HealthResourceAdapter.toUnits(20.0f, SCALE));
        assertEquals(100L, displayFor(20.0f));
    }

    @Test
    void nativeTenDisplaysAsFifty() {
        assertEquals(10000L, HealthResourceAdapter.toUnits(10.0f, SCALE));
        assertEquals(50L, displayFor(10.0f));
    }

    @Test
    void nativeZeroPointTwoDisplaysAsOne() {
        assertEquals(200L, HealthResourceAdapter.toUnits(0.2f, SCALE));
        assertEquals(1L, displayFor(0.2f));
    }

    @Test
    void halfPointHealthRoundsDeterministically() {
        // 10.5 -> units 10500 -> display (10500*5+500)/1000 = 53.
        assertEquals(10500L, HealthResourceAdapter.toUnits(10.5f, SCALE));
        assertEquals(53L, displayFor(10.5f));
        // 0.5 -> units 500 -> display (500*5+500)/1000 = 3.
        assertEquals(500L, HealthResourceAdapter.toUnits(0.5f, SCALE));
        assertEquals(3L, displayFor(0.5f));
    }

    @Test
    void ordinaryIntegerHealthRoundTripsExactly() {
        for (int hp = 0; hp <= 40; hp++) {
            int sample = hp;
            long units = HealthResourceAdapter.toUnits((float) sample, SCALE);
            assertEquals(sample, (int) HealthResourceAdapter.toMechanical(units, SCALE),
                    () -> "round-trip failed for hp=" + sample);
        }
    }

    @Test
    void dynamicMaximumValuesAreReflectedByTheSameConversion() {
        // Simulates a CON-bonus-scaled max health (e.g. base 20 + 2 CON bonus = 24) — the
        // conversion itself has no hardcoded "20", it works for any mechanical maximum.
        assertEquals(120L, displayFor(24.0f));
        assertEquals(200L, displayFor(40.0f));
    }

    @Test
    void deterministicFixedPointConversionIsStableAcrossRepeatedCalls() {
        for (int i = 0; i < 5; i++) {
            assertEquals(69L, displayFor(13.7f));
        }
    }

    @Test
    void doesNotTruncateByDirectFloatToLongCast() {
        // A naive `(long) (mechanicalHealth * unitScale)` truncates toward zero instead of
        // rounding to the nearest unit. 0.9999f is deliberately far from any float-precision edge
        // case: (long) 999.9 truncates to 999, while the correct nearest-unit value is 1000.
        long naiveTruncation = (long) (0.9999f * 1000f);
        long correct = HealthResourceAdapter.toUnits(0.9999f, SCALE);
        assertEquals(999L, naiveTruncation, "sanity-check: naive cast really does truncate here");
        assertEquals(1000L, correct, "toUnits must round to the nearest unit, not truncate");
    }

    @Test
    void nonFiniteHealthIsRejectedIncludingNegativeInfinity() {
        assertThrows(IllegalArgumentException.class, () -> HealthResourceAdapter.toUnits(Float.NaN, SCALE));
        assertThrows(IllegalArgumentException.class, () -> HealthResourceAdapter.toUnits(Float.POSITIVE_INFINITY, SCALE));
        assertThrows(IllegalArgumentException.class, () -> HealthResourceAdapter.toUnits(Float.NEGATIVE_INFINITY, SCALE));
    }

    @Test
    void invalidUnitScaleIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> HealthResourceAdapter.toUnits(1.0f, 0));
        assertThrows(IllegalArgumentException.class, () -> HealthResourceAdapter.toUnits(1.0f, -1));
    }

    @Test
    void zeroHealthConvertsToZeroUnits() {
        assertEquals(0L, HealthResourceAdapter.toUnits(0.0f, SCALE));
        // Negative zero must not produce a signed-zero or off-by-one artifact.
        assertEquals(0L, HealthResourceAdapter.toUnits(-0.0f, SCALE));
    }

    @Test
    void positiveHalfBoundaryRoundsAwayFromZero() {
        // 0.0625 (= 1/16) is exactly representable in binary float; ×1000 = 62.5 exactly —
        // a genuine tie, not an approximation. HALF_UP must round it up to 63.
        assertEquals(63L, HealthResourceAdapter.toUnits(0.0625f, SCALE));
    }

    @Test
    void negativeHalfBoundaryRoundsAwayFromZero() {
        // Same exact tie, negated: -62.5 must round to -63 (away from zero), not -62 (toward zero).
        assertEquals(-63L, HealthResourceAdapter.toUnits(-0.0625f, SCALE));
    }

    @Test
    void positiveOverflowIsRejectedNotSaturated() {
        // Float.MAX_VALUE at unitScale=1 vastly exceeds Long.MAX_VALUE. The original
        // Math.round(double)-then-compare implementation could not detect this: Math.round
        // itself already saturates to Long.MAX_VALUE before the (ineffective) comparison ever
        // ran. This must now genuinely throw rather than silently returning a saturated value.
        ArithmeticException ex = assertThrows(ArithmeticException.class,
                () -> HealthResourceAdapter.toUnits(Float.MAX_VALUE, 1));
        assertFalse(ex.getMessage().isBlank());
    }

    @Test
    void negativeOverflowIsRejectedNotSaturated() {
        assertThrows(ArithmeticException.class, () -> HealthResourceAdapter.toUnits(-Float.MAX_VALUE, 1));
    }

    @Test
    void valueImmediatelyInsideValidLongBoundaryIsAccepted() {
        // 2^62 is exactly representable in float (a power of two) and comfortably inside the
        // valid long range at unitScale=1 — proves large-but-valid input is not falsely rejected.
        float twoToThe62 = (float) Math.pow(2, 62);
        assertEquals(1L << 62, HealthResourceAdapter.toUnits(twoToThe62, 1));
    }

    @Test
    void valueImmediatelyOutsideValidLongBoundaryIsRejected() {
        // 2^63 is exactly representable in float and is exactly Long.MAX_VALUE + 1 — genuinely
        // immediately outside the valid long range, not merely "very large".
        float twoToThe63 = (float) Math.pow(2, 63);
        assertThrows(ArithmeticException.class, () -> HealthResourceAdapter.toUnits(twoToThe63, 1));
    }
}
