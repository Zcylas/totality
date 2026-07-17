package zcylas.totality.api.core.rpgutils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that no Hunger/Food display-scale conversion exists yet (readiness audit §2.5 / Stage 2
 * task scope explicitly forbids implementing one in this patch). Most of the Hunger findings —
 * "vanilla FoodData remains authoritative", "no Totality duplicate storage" — are absence claims
 * about production code that don't have a meaningful positive JUnit assertion; they were verified
 * by exhaustive grep in the readiness audit rather than by a runtime test here. This test instead
 * pins the one thing that IS a concrete, regression-checkable code fact: {@link RpgDisplayUtils}
 * has no Food/Hunger-scaling method counterpart to {@code toDisplayHp}. When Hunger is actually
 * migrated to the x5 scale in a later phase, this test is expected to start failing — that is the
 * intended signal to update it deliberately, not evidence of an accidental regression.
 */
class HungerDisplayCharacterizationTest {

    @Test
    void noFoodOrHungerDisplayConversionMethodExistsYet() {
        boolean hasFoodOrHungerMethod = Arrays.stream(RpgDisplayUtils.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.toLowerCase().contains("food") || name.toLowerCase().contains("hunger"));

        assertFalse(hasFoodOrHungerMethod,
                "RpgDisplayUtils must not gain a Food/Hunger display formatter in this Phase 1 "
                        + "patch — the x5 Hunger adapter is explicitly out of scope for Stage 2");
    }

    @Test
    void hpFormatterRemainsTheOnlyExistingDisplayMultiplierConstant() {
        // Confirms HP_DISPLAY_MULTIPLIER is still the sole scaling constant on this class.
        long constantFields = Arrays.stream(RpgDisplayUtils.class.getFields())
                .filter(f -> f.getName().toUpperCase().contains("DISPLAY_MULTIPLIER"))
                .count();
        assertEquals(1, constantFields);
    }
}
