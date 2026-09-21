package zcylas.totality.api.rpg.resources;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural validation for a resource "whose preferred state is a bounded range" (canonical
 * §8.2's {@code TARGET_RANGE} polarity shape) — deliberately no concrete example resource named,
 * per the current decision that Temperature is owned by a future Environment/Physiology system,
 * not the Resource API.
 */
class ResourceTargetRangeTest {

    @Test
    void validRangeConstructsCleanly() {
        var range = new ResourceTargetRange(20, 40, OptionalLong.of(10), OptionalLong.of(50));
        assertEquals(20, range.preferredMinimumUnits());
        assertEquals(40, range.preferredMaximumUnits());
        assertEquals(10, range.warningMinimumUnits().orElseThrow());
        assertEquals(50, range.warningMaximumUnits().orElseThrow());
    }

    @Test
    void validRangeWithoutWarningBoundsConstructsCleanly() {
        assertDoesNotThrow(() ->
                new ResourceTargetRange(20, 40, OptionalLong.empty(), OptionalLong.empty()));
    }

    @Test
    void rejectsPreferredMinimumGreaterThanMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceTargetRange(41, 40, OptionalLong.empty(), OptionalLong.empty()));
    }

    @Test
    void allowsEqualPreferredMinimumAndMaximum() {
        assertDoesNotThrow(() ->
                new ResourceTargetRange(30, 30, OptionalLong.empty(), OptionalLong.empty()));
    }

    @Test
    void rejectsWarningMinimumAbovePreferredMinimum() {
        // A "warning-low" threshold must sit at or below the comfortable band, not inside/above it.
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceTargetRange(20, 40, OptionalLong.of(25), OptionalLong.empty()));
    }

    @Test
    void rejectsWarningMaximumBelowPreferredMaximum() {
        // A "warning-high" threshold must sit at or above the comfortable band, not inside/below it.
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceTargetRange(20, 40, OptionalLong.empty(), OptionalLong.of(35)));
    }

    @Test
    void allowsWarningBoundsExactlyAtPreferredBounds() {
        assertDoesNotThrow(() ->
                new ResourceTargetRange(20, 40, OptionalLong.of(20), OptionalLong.of(40)));
    }
}
