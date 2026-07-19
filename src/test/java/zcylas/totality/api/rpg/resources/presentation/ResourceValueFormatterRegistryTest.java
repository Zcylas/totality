package zcylas.totality.api.rpg.resources.presentation;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.TestResourceBootstrap;

import static org.junit.jupiter.api.Assertions.*;

class ResourceValueFormatterRegistryTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    @Test
    void registrationSucceedsAndIsRetrievable() {
        ResourceValueFormatterRegistry registry = new ResourceValueFormatterRegistry();
        ResourceValueFormatter formatter = ResourceValueFormatter.ofConversion(id("fake"), ResourceDisplayConversion.IDENTITY);

        registry.register(formatter);

        assertTrue(registry.isRegistered(id("fake")));
        assertSame(formatter, registry.get(id("fake")).orElseThrow());
    }

    @Test
    void duplicateRegistrationIsRejected() {
        ResourceValueFormatterRegistry registry = new ResourceValueFormatterRegistry();
        registry.register(ResourceValueFormatter.ofConversion(id("fake"), ResourceDisplayConversion.IDENTITY));

        assertThrows(IllegalArgumentException.class, () ->
                registry.register(ResourceValueFormatter.ofConversion(id("fake"), ResourceDisplayConversion.HEALTH_FOOD)));
    }

    @Test
    void freezeBlocksFurtherRegistration() {
        ResourceValueFormatterRegistry registry = new ResourceValueFormatterRegistry();
        registry.freeze();

        assertThrows(IllegalStateException.class, () ->
                registry.register(ResourceValueFormatter.ofConversion(id("late"), ResourceDisplayConversion.IDENTITY)));
    }

    @Test
    void productionRegistryHasHealthAndFoodBothOnFiveOverOne() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        ResourceValueFormatter health = ResourceValueFormatterRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow();
        ResourceValueFormatter food = ResourceValueFormatterRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow();

        assertEquals(100L, health.toDisplayValue(20000, 1000));
        assertEquals(100L, food.toDisplayValue(20, 1));
    }

    @Test
    void healthCompatibilityMethodMatchesTheRegisteredFormatterExactly() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        ResourceValueFormatter health = ResourceValueFormatterRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow();

        for (float hp = 0f; hp <= 40f; hp += 0.5f) {
            float sample = hp;
            long units = zcylas.totality.api.rpg.resources.external.HealthResourceAdapter.toUnits(sample, 1000);
            long viaFormatter = health.toDisplayValue(units, 1000);
            int viaRpgDisplayUtils = zcylas.totality.api.core.rpgutils.RpgDisplayUtils.toDisplayHp(sample);
            assertEquals(viaFormatter, viaRpgDisplayUtils,
                    () -> "RpgDisplayUtils.toDisplayHp diverged from the registered formatter at hp=" + sample);
        }
    }

    @Test
    void foodDeltaOfSixFormatsAsThirty() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        ResourceValueFormatter food = ResourceValueFormatterRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow();

        assertEquals(30L, food.toDisplayDelta(6, 1));
    }
}
