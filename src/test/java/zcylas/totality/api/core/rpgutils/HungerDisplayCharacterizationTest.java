package zcylas.totality.api.core.rpgutils;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatter;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatterRegistry;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 pinned that no Hunger/Food display-scale conversion existed yet (readiness audit §2.5).
 * Phase 2A implements it — but deliberately NOT as a new method on {@link RpgDisplayUtils}, whose
 * own class Javadoc has always scoped it to HP/stat conversions. Food's ×5 conversion instead lives
 * on the same shared, registered {@code totality:food} formatter that the HUD and item tooltips
 * both consume (see {@code ResourceValueFormatterRegistry}), matching the "one authoritative
 * conversion, no consumer hardcodes ×5" requirement without growing an HP-scoped utility class with
 * an unrelated resource's formatting method.
 */
class HungerDisplayCharacterizationTest {

    @Test
    void rpgDisplayUtilsStillHasNoFoodOrHungerMethod() {
        // Still true in Phase 2A — the conversion is intentionally NOT added here, see class Javadoc.
        boolean hasFoodOrHungerMethod = Arrays.stream(RpgDisplayUtils.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.toLowerCase().contains("food") || name.toLowerCase().contains("hunger"));

        assertFalse(hasFoodOrHungerMethod,
                "RpgDisplayUtils intentionally has no Food/Hunger method — that conversion lives on "
                        + "the shared totality:food ResourceValueFormatter instead");
    }

    @Test
    void hpFormatterRemainsTheOnlyExistingDisplayMultiplierConstant() {
        // Confirms HP_DISPLAY_MULTIPLIER is still the sole scaling constant on this class.
        long constantFields = Arrays.stream(RpgDisplayUtils.class.getFields())
                .filter(f -> f.getName().toUpperCase().contains("DISPLAY_MULTIPLIER"))
                .count();
        assertEquals(1, constantFields);
    }

    @Test
    void foodDisplayConversionIsNowIdentityNotTheOldPhase2AFiveToOneConversion() {
        // Superseded twice over: Phase 2A registered a x5 formatter (native 0-20 -> display 0-100)
        // reached through PlayerResourceIds.FOOD, not RpgDisplayUtils. The 2026-09-17 Food 0-100
        // migration supersedes THAT: totality:food is now natively 0-100 (a real GENERIC_COMPONENT
        // resource, not vanilla FoodData 0-20 with a presentation trick), so the registered formatter
        // is IDENTITY — applying any further multiplier here would double-scale a native 73 into a
        // displayed 365. See TOTALITY_FOOD_0_100_AND_TOTALITY_FOOD_ITEM_IMPLEMENTATION_REPORT_2026-09-17.md.
        zcylas.totality.api.rpg.resources.TestResourceBootstrap.ensureProductionResourcesRegistered();
        ResourceValueFormatter formatter = ResourceValueFormatterRegistry.INSTANCE
                .get(PlayerResourceIds.FOOD)
                .orElseThrow(() -> new AssertionError("totality:food formatter must be registered"));

        assertEquals(100, formatter.toDisplayValue(100, 1));
        assertEquals(73, formatter.toDisplayValue(73, 1));
        assertEquals(30, formatter.toDisplayValue(30, 1));
        assertEquals(0, formatter.toDisplayValue(0, 1));
    }

    @Test
    void foodAndHealthShareTheSameRegisteredIdentifierNamespace() {
        assertEquals(Identifier.fromNamespaceAndPath("totality", "food"), PlayerResourceIds.FOOD);
        assertEquals(Identifier.fromNamespaceAndPath("totality", "health"), PlayerResourceIds.HEALTH);
    }
}
