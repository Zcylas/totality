package zcylas.totality.api.rpg.resources.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDisplayConversionTest {

    @Test
    void fiveOverOneConversionValidates() {
        assertDoesNotThrow(() -> new ResourceDisplayConversion(5, 1));
        assertEquals(5, ResourceDisplayConversion.HEALTH_FOOD.numerator());
        assertEquals(1, ResourceDisplayConversion.HEALTH_FOOD.denominator());
    }

    @Test
    void identityConversionIsOneOverOne() {
        assertEquals(1, ResourceDisplayConversion.IDENTITY.numerator());
        assertEquals(1, ResourceDisplayConversion.IDENTITY.denominator());
        assertEquals(42L, ResourceDisplayConversion.IDENTITY.convertUnitsToDisplay(42, 1));
    }

    @Test
    void zeroDenominatorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceDisplayConversion(5, 0));
    }

    @Test
    void negativeDenominatorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceDisplayConversion(5, -1));
    }

    @Test
    void negativeNumeratorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceDisplayConversion(-5, 1));
    }

    @Test
    void zeroNumeratorIsAccepted() {
        // A resource that never displays a numeric value is a legitimate (if unusual) declaration.
        assertDoesNotThrow(() -> new ResourceDisplayConversion(0, 1));
    }

    @Test
    void invalidUnitScaleIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(100, 0));
    }

    @Test
    void checkedOverflowThrowsRatherThanWrapping() {
        ResourceDisplayConversion huge = new ResourceDisplayConversion(Long.MAX_VALUE, 1);
        assertThrows(ArithmeticException.class, () -> huge.convertUnitsToDisplay(2, 1));
    }

    @Test
    void roundingIsDeterministicAndRepeatable() {
        for (int i = 0; i < 10; i++) {
            assertEquals(53L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(10500, 1000));
        }
    }

    @Test
    void negativeUnitsRoundHalfAwayFromZeroSymmetrically() {
        // Not exercised by any HIGH_IS_GOOD production resource, but the conversion itself must
        // stay well-defined for a future signed/TARGET_RANGE resource.
        assertEquals(-53L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(-10500, 1000));
    }

    @Test
    void pairFormattingIsDeterministic() {
        long current = ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(13700, 1000);
        long max = ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(20000, 1000);
        assertEquals(69L, current);
        assertEquals(100L, max);
        assertEquals("69 / 100", current + " / " + max);
    }

    // ── Correction pass: Long.MIN_VALUE rounding regression ─────────────────────────────────

    @Test
    void longMinValueUnitsWithEvenDivisorRoundsExactlyWithoutThrowing() {
        // The original implementation negated a negative numerator before dividing
        // (Math.negateExact), which itself overflows for Long.MIN_VALUE even though the true
        // divided result (Long.MIN_VALUE / 2) is perfectly representable. Must not throw.
        ResourceDisplayConversion half = new ResourceDisplayConversion(1, 2);
        long result = assertDoesNotThrow(() -> half.convertUnitsToDisplay(Long.MIN_VALUE, 1));
        assertEquals(Long.MIN_VALUE / 2, result);
    }

    @Test
    void negativeExactHalfRoundsAwayFromZero() {
        // -5 / 2 = -2.5 exactly — a genuine tie. Away from zero means -3, not -2.
        ResourceDisplayConversion half = new ResourceDisplayConversion(1, 2);
        assertEquals(-3L, half.convertUnitsToDisplay(-5, 1));
    }

    @Test
    void negativeValueJustBelowAHalfRoundsTowardZero() {
        // -9 / 4 = -2.25 — nearer to -2 (distance 0.25) than to -3 (distance 0.75).
        ResourceDisplayConversion quarter = new ResourceDisplayConversion(1, 4);
        assertEquals(-2L, quarter.convertUnitsToDisplay(-9, 1));
    }

    @Test
    void negativeValueJustAboveAHalfRoundsAwayFromZero() {
        // -11 / 4 = -2.75 — nearer to -3 (distance 0.25) than to -2 (distance 0.75).
        ResourceDisplayConversion quarter = new ResourceDisplayConversion(1, 4);
        assertEquals(-3L, quarter.convertUnitsToDisplay(-11, 1));
    }

    @Test
    void ordinaryPositiveValuesRemainUnchangedAfterTheRoundingFix() {
        assertEquals(100L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(20000, 1000));
        assertEquals(50L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(10000, 1000));
        assertEquals(1L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(200, 1000));
        assertEquals(69L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(13700, 1000));
        assertEquals(53L, ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(10500, 1000));
    }

    @Test
    void genuineMultiplicationOverflowStillThrowsAfterTheRoundingFix() {
        ResourceDisplayConversion huge = new ResourceDisplayConversion(Long.MAX_VALUE, 1);
        assertThrows(ArithmeticException.class, () -> huge.convertUnitsToDisplay(2, 1));
        // Also exercised at the denominator side (unitScale * denominator overflow).
        ResourceDisplayConversion hugeDenominator = new ResourceDisplayConversion(1, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> hugeDenominator.convertUnitsToDisplay(1, 2));
    }

    // ── Correction pass: invertToMechanical ─────────────────────────────────────────────────

    @Test
    void invertToMechanicalIsTheExactInverseForHealthFood() {
        assertEquals(20.0, ResourceDisplayConversion.HEALTH_FOOD.invertToMechanical(100));
        assertEquals(10.0, ResourceDisplayConversion.HEALTH_FOOD.invertToMechanical(50));
        assertEquals(1.0, ResourceDisplayConversion.HEALTH_FOOD.invertToMechanical(5));
    }

    @Test
    void invertToMechanicalRejectsZeroNumerator() {
        ResourceDisplayConversion zero = new ResourceDisplayConversion(0, 1);
        assertThrows(ArithmeticException.class, () -> zero.invertToMechanical(100));
    }
}
