package zcylas.totality.api.rpg.combat.stamina.depletion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure classification tests for {@link StaminaDepletionState#fromStamina(int, int)} — the
 * renamed, immediate Stamina-derived exertion/depletion tier. Not the future D&D-style
 * persistent Exhaustion condition; these tiers remain purely derived from current/max Stamina.
 */
class StaminaDepletionStateTest {

    @Test
    void aboveThirtyPercentIsNormal() {
        assertEquals(StaminaDepletionState.NORMAL, StaminaDepletionState.fromStamina(31, 100));
        assertEquals(StaminaDepletionState.NORMAL, StaminaDepletionState.fromStamina(100, 100));
    }

    @Test
    void exactlyThirtyPercentIsWinded() {
        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionState.fromStamina(30, 100));
    }

    @Test
    void positiveValueBelowThirtyPercentIsWinded() {
        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionState.fromStamina(1, 100));
        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionState.fromStamina(15, 100));
    }

    @Test
    void exactlyZeroIsDepleted() {
        assertEquals(StaminaDepletionState.DEPLETED, StaminaDepletionState.fromStamina(0, 100));
    }

    @Test
    void negativeValueIsDepleted() {
        assertEquals(StaminaDepletionState.DEPLETED, StaminaDepletionState.fromStamina(-5, 100));
    }

    @Test
    void nonPositiveMaximumIsSafeNormal() {
        assertEquals(StaminaDepletionState.NORMAL, StaminaDepletionState.fromStamina(0, 0));
        assertEquals(StaminaDepletionState.NORMAL, StaminaDepletionState.fromStamina(5, 0));
        assertEquals(StaminaDepletionState.NORMAL, StaminaDepletionState.fromStamina(5, -10));
    }

    @Test
    void classificationIsProportionalNotAbsolute() {
        // Same percentage (30%) at wildly different scales must classify identically.
        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionState.fromStamina(3, 10));
        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionState.fromStamina(30, 100));
        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionState.fromStamina(300, 1000));

        // The same absolute value (50) must classify differently depending on max — proving the
        // classifier is not comparing against a fixed absolute threshold like "50".
        assertEquals(StaminaDepletionState.NORMAL, StaminaDepletionState.fromStamina(50, 100));
        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionState.fromStamina(50, 1000));
    }
}
