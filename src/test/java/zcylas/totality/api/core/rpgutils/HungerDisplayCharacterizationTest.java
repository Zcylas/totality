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
    void foodDisplayConversionNowExistsViaTheSharedFormatterRegistry() {
        // The Phase 1 characterization above ("no conversion exists yet") is now superseded: Food
        // does have a registered ×5 formatter — just reached through PlayerResourceIds.FOOD, not
        // through RpgDisplayUtils. Native Food 20/16/6/0 -> displayed 100/80/30/0 (canonical §19.8).
        zcylas.totality.api.rpg.resources.TestResourceBootstrap.ensureProductionResourcesRegistered();
        ResourceValueFormatter formatter = ResourceValueFormatterRegistry.INSTANCE
                .get(PlayerResourceIds.FOOD)
                .orElseThrow(() -> new AssertionError("totality:food formatter must be registered by Phase 2A"));

        assertEquals(100, formatter.toDisplayValue(20, 1));
        assertEquals(80, formatter.toDisplayValue(16, 1));
        assertEquals(30, formatter.toDisplayValue(6, 1));
        assertEquals(0, formatter.toDisplayValue(0, 1));
    }

    @Test
    void foodAndHealthShareTheSameRegisteredIdentifierNamespace() {
        assertEquals(Identifier.fromNamespaceAndPath("totality", "food"), PlayerResourceIds.FOOD);
        assertEquals(Identifier.fromNamespaceAndPath("totality", "health"), PlayerResourceIds.HEALTH);
    }
}
