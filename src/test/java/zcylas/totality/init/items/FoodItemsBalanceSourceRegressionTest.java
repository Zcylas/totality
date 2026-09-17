package zcylas.totality.init.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the 2026-09-17 Pizza/Saturation correction pass's authored Food and
 * temporary-Saturation constants. Follows the established convention in
 * {@code FoodItemsTooltipMetadataSourceRegressionTest}: {@code FoodItems}' fields are frozen behind
 * {@code BuiltInRegistries.ITEM} at class-load time, so this pins the exact constructor arguments as
 * source text rather than reading the registered items' private fields at runtime.
 *
 * <p>Pins the locked ratio: Pizza Margherita (Food 48, temporary Saturation 12.0) equals exactly 8x
 * Pizza Margherita Slice (Food 6, temporary Saturation 1.5) for both figures — "8 slices = one whole
 * Pizza" applies identically to Food and to the transitional Saturation compatibility value. These
 * Saturation figures are explicitly transitional compatibility-buffer values, not canonical
 * Diet/Metabolism/Metabolic Reserve figures, and are expected to be replaced wholesale once that
 * future system exists.
 */
class FoodItemsBalanceSourceRegressionTest {

    private static final Path FOOD_ITEMS = Path.of("src/main/java/zcylas/totality/init/items/FoodItems.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    @Test
    void pizzaMargheritaAuthoredFoodAndTemporarySaturationAndDuration() throws Exception {
        String source = read(FOOD_ITEMS);
        assertTrue(source.contains("new TotalityFoodItem(properties, 48, 12.0F, 16.0F)"),
                "Pizza Margherita must author Food 48, temporary Saturation 12.0, and a 16.0s consume duration");
    }

    @Test
    void pizzaMargheritaSliceAuthoredFoodAndTemporarySaturationAndDuration() throws Exception {
        String source = read(FOOD_ITEMS);
        assertTrue(source.contains("new TotalityFoodItem(properties, 6, 1.5F, 2.0F)"),
                "the Slice must author Food 6, temporary Saturation 1.5, and a 2.0s consume duration");
    }

    @Test
    void eightSlicesAreNumericallyEquivalentToOnePizzaForFoodAndTemporarySaturationBeforeClamping() {
        // Direct arithmetic pin of the locked ratio, independent of any runtime clamping behavior
        // (clamping against the resolved Food maximum, or against the vanilla mirror ceiling for
        // Saturation, is proven separately and does not change these authored source values).
        long pizzaFood = 48;
        long sliceFood = 6;
        float pizzaSaturation = 12.0F;
        float sliceSaturation = 1.5F;
        assertEquals(pizzaFood, 8 * sliceFood, "8 slices' Food must equal one Pizza's Food");
        assertEquals(pizzaSaturation, 8 * sliceSaturation, 0.0001F,
                "8 slices' temporary Saturation must equal one Pizza's temporary Saturation");
    }
}
