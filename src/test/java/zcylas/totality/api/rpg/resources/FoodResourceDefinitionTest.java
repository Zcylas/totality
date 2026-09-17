package zcylas.totality.api.rpg.resources;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.food.FoodMaximumResolver;
import zcylas.totality.api.rpg.resources.food.FoodVanillaCompatibilityBridge;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatter;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatterRegistry;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the real production {@code totality:food} definition is a true, baseline-100
 * {@code GENERIC_COMPONENT} authority — original pass 2026-09-17, corrected 2026-09-17 (the
 * maximum is now resolver-driven, not a hardcoded {@code authoredBaseMaximum} literal, and the
 * vanilla compatibility mirror is proportional to the resolved maximum, not a fixed {@code /5}). See
 * {@code TOTALITY_FOOD_0_100_AND_TOTALITY_FOOD_ITEM_IMPLEMENTATION_REPORT_2026-09-17.md}'s
 * 2026-09-17 correction section. Mutation behavior is exercised against
 * {@link PlayerResourceService}'s package-visible {@code applyClampedDeltaGenericState}/
 * {@code queryGenericState} cores with a bare {@code new PlayerResourceStateComponent(null)},
 * matching {@code PlayerResourceServiceMutationTest}'s established no-real-Player pattern (same
 * package, for that exact access reason). Real end-to-end player behavior (join, migration, eating,
 * vanilla mutation paths) is proven separately by {@code FoodSystemVerification}, a dev-server
 * verification.
 */
class FoodResourceDefinitionTest {

    private static PlayerResourceDefinition foodDefinition() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        return PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD)
                .orElseThrow(() -> new AssertionError("totality:food must be registered"));
    }

    @Test
    void foodIsNowGenericComponentAuthorityNotExternalAdapter() {
        assertEquals(ResourceStateAuthority.GENERIC_COMPONENT, foodDefinition().stateAuthority());
        assertTrue(foodDefinition().externalAdapterId().isEmpty());
    }

    @Test
    void foodMinimumIsZero() {
        assertEquals(0, foodDefinition().absoluteMinimum());
    }

    @Test
    void foodDefinitionVersionWasBumpedForTheAuthorityChange() {
        assertEquals(2, foodDefinition().definitionVersion());
    }

    @Test
    void foodDeclaresRestorableAndDirectDrainButNoSpendable() {
        var capabilities = foodDefinition().capabilities();
        assertTrue(capabilities.contains(ResourceCapability.RESTORABLE));
        assertTrue(capabilities.contains(ResourceCapability.DIRECT_DRAIN));
        assertTrue(capabilities.contains(ResourceCapability.HUD_VISIBLE));
        assertTrue(capabilities.contains(ResourceCapability.MENU_VISIBLE));
        assertFalse(capabilities.contains(ResourceCapability.SPENDABLE));
    }

    @Test
    void foodDeathPolicyResetsToMaximumMatchingVanillaRespawnRefillingHungerToFull() {
        assertEquals(ResourceDeathPolicy.RESET_TO_MAXIMUM, foodDefinition().lifecycle().deathPolicy());
    }

    @Test
    void foodFormatterIsIdentityNotTheOldFiveToOneHealthFoodConversion() {
        ResourceValueFormatter formatter = ResourceValueFormatterRegistry.INSTANCE.get(PlayerResourceIds.FOOD)
                .orElseThrow(() -> new AssertionError("totality:food formatter must be registered"));
        assertEquals(73, formatter.toDisplayValue(73, 1), "a native 73 must display as 73, not 365");
        assertEquals(100, formatter.toDisplayValue(100, 1));
        assertEquals(0, formatter.toDisplayValue(0, 1));
    }

    @Test
    void valuesAboveTwentyAreNativelyRepresentable() {
        PlayerResourceDefinition definition = foodDefinition();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(definition.id(), 73);

        ResourceQueryResult result = PlayerResourceService.INSTANCE.queryGenericState(null, definition, state);
        assertInstanceOf(ResourceQueryResult.Success.class, result);
        assertEquals(73, ((ResourceQueryResult.Success) result).snapshot().currentUnits());
        assertEquals(100, ((ResourceQueryResult.Success) result).snapshot().maximumUnits());
    }

    @Test
    void restoreClampsAtOneHundredRatherThanOverflowing() {
        PlayerResourceDefinition definition = foodDefinition();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(definition.id(), 92);

        ResourceOperationResult result = PlayerResourceService.INSTANCE.applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(definition.id(), 48), +1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        ResourceOperationResult.Success success = (ResourceOperationResult.Success) result;
        assertEquals(100, success.after().currentUnits(), "48 restored onto 92 must clamp at 100, not reach 140");
        assertEquals(8, success.appliedUnits(), "only 8 of the 48 could actually be applied");
    }

    @Test
    void restoringAtExactlyFullStillSucceedsWithZeroApplied() {
        // "Eating while full": the operation succeeds (the item is still consumed by the caller)
        // even though zero Food could actually be restored.
        PlayerResourceDefinition definition = foodDefinition();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(definition.id(), 100);

        ResourceOperationResult result = PlayerResourceService.INSTANCE.applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(definition.id(), 6), +1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        ResourceOperationResult.Success success = (ResourceOperationResult.Success) result;
        assertEquals(100, success.after().currentUnits());
        assertEquals(0, success.appliedUnits());
    }

    @Test
    void drainClampsAtZeroRatherThanGoingNegative() {
        PlayerResourceDefinition definition = foodDefinition();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(definition.id(), 3);

        ResourceOperationResult result = PlayerResourceService.INSTANCE.applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(definition.id(), 5), -1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(0, ((ResourceOperationResult.Success) result).after().currentUnits());
    }

    @Test
    void vanillaCompatibilityExchangeRateIsExactlyFive() {
        assertEquals(5, FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE);
    }

    @Test
    void migrationConversionFormulaMatchesEveryLockedExample() {
        // The exact locked examples from the task: old/20 -> new/100 is always an exact x5. This is
        // the one-time save-migration conversion — always fixed, never proportional to a resolved
        // maximum (unlike the ongoing mirror projection — see the VARIABLE MAXIMUM tests below).
        assertEquals(100, 20 * FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE);
        assertEquals(90, 18 * FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE);
        assertEquals(70, 14 * FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE);
        assertEquals(50, 10 * FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE);
        assertEquals(5, 1 * FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE);
        assertEquals(0, 0 * FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE);
    }

    @Test
    void translateAndResyncIsAFastPathNoOpForAZeroDeltaEvenWithoutARealPlayer() {
        assertEquals(0, FoodVanillaCompatibilityBridge.translateAndResync(null, 0, 0),
                "a zero delta never dereferences the player at all");
    }

    // ── VARIABLE MAXIMUM (2026-09-17 correction) ────────────────────────────────────────────────

    @Test
    void foodDeclaresNoAuthoredMaximumSoARegisteredResolverCanActuallyRun() {
        // Canonical §10.2: an authored base wins outright over a registered resolver for SCALAR
        // resources — keeping a hardcoded 100 here would silently short-circuit FoodMaximumResolver
        // the same way it would have collapsed ManaMaximumResolver/StaminaMaximumResolver/
        // RageMaximumResolver (their own established precedent for this exact same reason).
        assertTrue(foodDefinition().authoredBaseMaximum().isEmpty(),
                "an authored maximum would silently short-circuit the registered FoodMaximumResolver");
    }

    @Test
    void productionFoodMaximumResolverResolvesTheOneHundredBaseline() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        Optional<ResourceMaximumResolver> resolver = ResourceMaximumResolverRegistry.INSTANCE.get(PlayerResourceIds.FOOD);
        assertTrue(resolver.isPresent(), "totality:food must have a registered maximum resolver");

        ResourceMaximum resolved = resolver.get().resolve(null, foodDefinition(), ResourceResolutionContext.EMPTY);
        assertInstanceOf(ResourceMaximum.Scalar.class, resolved);
        assertEquals(100L, ((ResourceMaximum.Scalar) resolved).effectiveUnits());
        assertEquals(FoodMaximumResolver.BASELINE_MAXIMUM, ((ResourceMaximum.Scalar) resolved).effectiveUnits());
    }

    @Test
    void productionServiceResolvesFoodMaximumThroughTheRealRegisteredResolverNotAHardcodedLiteral() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        Optional<ResourceMaximum> resolved = PlayerResourceService.INSTANCE.resolveMaximum(
                null, foodDefinition(), ResourceResolutionContext.EMPTY);
        assertTrue(resolved.isPresent());
        assertEquals(100L, ((ResourceMaximum.Scalar) resolved.get()).effectiveUnits());
    }

    /**
     * The conceptual proof the task requires: baseline max = 100, a resolver CAN produce a
     * different (here, higher) maximum without any change to the Food definition's architecture,
     * authority, or shape — and every downstream consumer (clamping, mirror projection) already
     * correctly uses the RESOLVED maximum, never a hardcoded 100. Uses a fully isolated
     * {@link PlayerResourceRegistry}/{@link ResourceMaximumResolverRegistry}/
     * {@link PlayerResourceService} trio — the real production registries are never touched, so this
     * proves the existing pipeline's capability without authoring any real +Food-Max modifier.
     */
    private static PlayerResourceDefinition exceptionalFoodDefinition() {
        return PlayerResourceDefinition.builder(PlayerResourceIds.FOOD, ResourceModel.SCALAR)
                .polarity(ResourcePolarity.HIGH_IS_GOOD)
                .unitScale(1)
                .absoluteMinimum(0)
                // Deliberately no .authoredBaseMaximum(...) — same reason as production.
                .capabilities(ResourceCapability.RESTORABLE, ResourceCapability.DIRECT_DRAIN)
                .build();
    }

    private static PlayerResourceService serviceWithFoodMaximumResolver(ResourceMaximumResolver resolver) {
        ResourceMaximumResolverRegistry resolvers = new ResourceMaximumResolverRegistry();
        resolvers.register(PlayerResourceIds.FOOD, resolver);
        return new PlayerResourceService(new PlayerResourceRegistry(), new ExternalPlayerResourceAdapterRegistry(), resolvers);
    }

    @Test
    void anIsolatedResolverCanLegitimatelyResolveFoodMaximumAboveOneHundred() {
        PlayerResourceDefinition definition = exceptionalFoodDefinition();
        PlayerResourceService service = serviceWithFoodMaximumResolver(
                (player, def, context) -> ResourceMaximum.Scalar.of(150));

        Optional<ResourceMaximum> resolved = service.resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);
        assertTrue(resolved.isPresent());
        assertEquals(150L, ((ResourceMaximum.Scalar) resolved.get()).effectiveUnits(),
                "the existing resolver pipeline must be able to produce >100 without any Food-specific architecture change");
    }

    @Test
    void currentFoodClampsToTheResolvedMaximumNotALiteralOneHundred() {
        // Pizza at 140/150 -> 150, never silently reinterpreted as clamping to a literal 100.
        PlayerResourceDefinition definition = exceptionalFoodDefinition();
        PlayerResourceService service = serviceWithFoodMaximumResolver(
                (player, def, context) -> ResourceMaximum.Scalar.of(150));
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(definition.id(), 140);

        ResourceOperationResult result = service.applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(definition.id(), 48), +1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        ResourceOperationResult.Success success = (ResourceOperationResult.Success) result;
        assertEquals(150, success.after().currentUnits(), "140 + 48 must clamp at the resolved 150, not 100");
        assertEquals(10, success.appliedUnits());
    }

    @Test
    void mirrorOfMapsCurrentAndMaximumProportionallyNotAsAFixedFractionOfOneHundred() {
        // Locked examples: 100/100 -> 20, 50/100 -> 10, 150/150 -> 20, 75/150 -> 10.
        assertEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(100, 100));
        assertEquals(10, FoodVanillaCompatibilityBridge.mirrorOf(50, 100));
        assertEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(150, 150));
        assertEquals(10, FoodVanillaCompatibilityBridge.mirrorOf(75, 150));
    }

    @Test
    void mirrorOfMatchesTheLockedMigrationExamplesAtTheBaselineMaximum() {
        assertEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(100, 100));
        assertEquals(18, FoodVanillaCompatibilityBridge.mirrorOf(90, 100));
        assertEquals(14, FoodVanillaCompatibilityBridge.mirrorOf(70, 100));
        assertEquals(10, FoodVanillaCompatibilityBridge.mirrorOf(50, 100));
        assertEquals(1, FoodVanillaCompatibilityBridge.mirrorOf(5, 100));
        assertEquals(0, FoodVanillaCompatibilityBridge.mirrorOf(0, 100));
    }

    @Test
    void mirrorOfNeverExceedsTwentyOrDropsBelowZero() {
        assertEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(1000, 100), "grossly over-max current must still clamp at 20");
        assertEquals(0, FoodVanillaCompatibilityBridge.mirrorOf(0, 100));
        assertEquals(0, FoodVanillaCompatibilityBridge.mirrorOf(50, 0), "a zero/corrupt maximum must fail safe to 0, never divide by zero");
        assertEquals(0, FoodVanillaCompatibilityBridge.mirrorOf(50, -10), "a negative maximum must also fail safe to 0");
    }

    // ── ENDPOINT-PRESERVING MIRROR (2026-09-17, second correction pass) ────────────────────────

    @Test
    void mirrorIsZeroOnlyWhenAuthoritativeFoodIsActuallyEmpty() {
        assertEquals(0, FoodVanillaCompatibilityBridge.mirrorOf(0, 100), "0/100 must be exactly 0");
        assertEquals(0, FoodVanillaCompatibilityBridge.mirrorOf(0, 150), "0/150 must be exactly 0");
        // Every strictly-positive current must NOT be 0 — see positiveInteriorFoodNeverMirrorsToZero
        // below for the boundary cases a naive round(current/max*20) gets wrong.
    }

    @Test
    void mirrorIsTwentyOnlyWhenAuthoritativeFoodIsActuallyAtTheResolvedMaximum() {
        assertEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(100, 100), "100/100 must be exactly 20");
        assertEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(150, 150), "150/150 must be exactly 20");
        assertEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(1000, 100), "over-max current is treated as at-max, still exactly 20");
        // Every current strictly below max must NOT be 20 — see belowMaxInteriorFoodNeverMirrorsToTwenty
        // below for the boundary cases a naive round(current/max*20) gets wrong.
    }

    @Test
    void positiveInteriorFoodNeverMirrorsToZero() {
        // The exact bug the review flagged: round(1/100*20) = round(0.2) = 0, falsely signaling
        // "truly empty" to vanilla code that treats mirror 0 as needsFood()-false/starving.
        assertNotEquals(0, FoodVanillaCompatibilityBridge.mirrorOf(1, 100), "1/100 must not mirror to 0");
        assertEquals(1, FoodVanillaCompatibilityBridge.mirrorOf(1, 100), "1/100 must mirror to at least 1");
        assertNotEquals(0, FoodVanillaCompatibilityBridge.mirrorOf(2, 100), "2/100 must not mirror to 0");
        assertEquals(1, FoodVanillaCompatibilityBridge.mirrorOf(1, 150), "1/150 must mirror to at least 1");
    }

    @Test
    void belowMaxInteriorFoodNeverMirrorsToTwenty() {
        // The exact bug the review flagged: round(99/100*20) = round(19.8) = 20, falsely signaling
        // "truly full" to vanilla code that gates Peaceful's restore branch on needsFood().
        assertNotEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(99, 100), "99/100 must not mirror to 20");
        assertEquals(19, FoodVanillaCompatibilityBridge.mirrorOf(99, 100), "99/100 must mirror to at most 19");
        assertNotEquals(20, FoodVanillaCompatibilityBridge.mirrorOf(98, 100), "98/100 must not mirror to 20");
        assertEquals(19, FoodVanillaCompatibilityBridge.mirrorOf(149, 150), "149/150 must mirror to at most 19");
    }

    @Test
    void interiorMirrorAtVariableMaximumStaysWithinTheOpenRange() {
        for (long current = 1; current < 150; current++) {
            int mirror = FoodVanillaCompatibilityBridge.mirrorOf(current, 150);
            assertTrue(mirror >= 1 && mirror <= 19,
                    "current=" + current + "/150 must mirror strictly within [1,19], was " + mirror);
        }
    }

    // ── >100 MAXIMUM: VANILLA-ORIGINATING RESTORATION MUST USE THE INTENDED AMOUNT, NEVER A
    //    MEASURED/CLAMPED MIRROR DELTA (2026-09-17, second correction pass) ───────────────────────
    //
    // These reproduce FoodVanillaCompatibilityBridge#interceptFoodDataEat/#translateAndResync's own
    // internal arithmetic (intended old-domain amount x5, applied via the exact same
    // applyClampedDeltaGenericState routing core the real restore()/drain() delegate to) against an
    // isolated resolver/service, at a resolved maximum where the bug is actually observable (at the
    // real production 100 baseline, restoring too little still often clamps to the same 100 the
    // correct amount would — only a >100 maximum makes the two answers actually differ). The mixins'
    // own call sites are separately, trivially correct by inspection: each passes a literal, already-
    // known nutrition value (`self.nutrition()`, or the literal `food` argument) as the intended
    // amount, never a value computed from vanilla's own field.

    @Test
    void vanillaFoodNutritionFourAtOneHundredFortyOfOneHundredFiftyRestoresToExactlyOneHundredFifty() {
        // The exact review example: naively measuring vanilla's OWN clamped mirror delta at 19/20
        // (mirror clamps 19+4 at 20, an observed delta of only +1) would wrongly restore only +5,
        // landing at 145. The intended amount (nutrition 4) must instead restore the full +20.
        PlayerResourceDefinition definition = exceptionalFoodDefinition();
        PlayerResourceService service = serviceWithFoodMaximumResolver(
                (player, def, context) -> ResourceMaximum.Scalar.of(150));
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(definition.id(), 140);

        int intendedNutrition = 4;
        ResourceOperationResult result = service.applyClampedDeltaGenericState(
                null, definition,
                ResourceAmount.scalar(definition.id(), (long) intendedNutrition * FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE),
                +1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(150, ((ResourceOperationResult.Success) result).after().currentUnits(),
                "140 + (4 nutrition x 5) = 160, clamped at the resolved 150 — must NOT be 145");
    }

    @Test
    void cakeNutritionTwoNearMaxRestoresCorrectly() {
        PlayerResourceDefinition definition = exceptionalFoodDefinition();
        PlayerResourceService service = serviceWithFoodMaximumResolver(
                (player, def, context) -> ResourceMaximum.Scalar.of(150));
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(definition.id(), 145);

        int cakeNutrition = 2;
        ResourceOperationResult result = service.applyClampedDeltaGenericState(
                null, definition,
                ResourceAmount.scalar(definition.id(), (long) cakeNutrition * FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE),
                +1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(150, ((ResourceOperationResult.Success) result).after().currentUnits(),
                "145 + (2 nutrition x 5) = 155, clamped at the resolved 150");
    }

    @Test
    void lowPositiveFoodCanReachExactlyZeroViaARealDrainNotStrandedByMirrorQuantization() {
        // The review's exact concern: authoritative Food at a small positive value (whose mirror was
        // already quantized to a coarse interior bucket) must still be able to reach true 0 via a
        // real depletion event, never "stuck" merely because its mirror looked non-empty.
        PlayerResourceDefinition definition = foodDefinition();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(definition.id(), 2);

        ResourceOperationResult result = PlayerResourceService.INSTANCE.applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(definition.id(), FoodVanillaCompatibilityBridge.VANILLA_TO_TRUE_SCALE), -1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(0, ((ResourceOperationResult.Success) result).after().currentUnits());
    }
}
