package zcylas.totality.init.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the 2026-09-17 real-client manual test correction's tooltip fix:
 * both Pizza items were missing Totality tooltip metadata entirely, so they rendered through
 * vanilla's plain tooltip instead of the custom Food-themed renderer. Follows the established
 * convention in {@code TooltipApiFoundationSourceRegressionTest}: {@code FoodItems}' fields are
 * {@code Item} registrations frozen at class-load time behind {@code BuiltInRegistries.ITEM}, and
 * {@code ItemComponents}' static component-type fields are only populated once {@code
 * ItemComponents.register()} runs (via {@code Totality.onInitialize()} -> {@code
 * ModComponents.register()}), which never happens under plain JUnit — so this is a source-text
 * sentinel, not runtime proof of the rendered tooltip.
 *
 * <p>Also pins the 2026-09-17 real-client correction's Pizza Margherita stack-size decision
 * (1 -&gt; 8): a whole meal stays bulkier than an individual slice without being excessively
 * inventory-hostile, and 8 is thematically consistent with one Pizza = 8 Slices. The Slice's own
 * stack size (64) is explicitly pinned as unchanged by that same decision.
 */
class FoodItemsTooltipMetadataSourceRegressionTest {

    private static final Path FOOD_ITEMS = Path.of("src/main/java/zcylas/totality/init/items/FoodItems.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    private static String blockFor(String source, String fieldName) {
        int start = source.indexOf("TotalityFoodItem " + fieldName);
        assertTrue(start >= 0, "expected to find the " + fieldName + " registration");
        int next = source.indexOf("public static final", start + 1);
        return next >= 0 ? source.substring(start, next) : source.substring(start);
    }

    @Test
    void pizzaMargheritaHasCommonRarityFoodTypeAndCorrectLore() throws Exception {
        String block = blockFor(read(FOOD_ITEMS), "PIZZA_MARGHERITA =");
        assertTrue(block.contains("new RarityComponent(ItemRarity.COMMON)"));
        assertTrue(block.contains("new ItemTypeComponent(ItemType.FOOD)"));
        assertTrue(block.contains("A classic pizza topped with tomato, mozzarella, and basil."));
    }

    @Test
    void pizzaMargheritaSliceHasCommonRarityFoodTypeAndCorrectLore() throws Exception {
        String block = blockFor(read(FOOD_ITEMS), "PIZZA_MARGHERITA_SLICE =");
        assertTrue(block.contains("new RarityComponent(ItemRarity.COMMON)"));
        assertTrue(block.contains("new ItemTypeComponent(ItemType.FOOD)"));
        assertTrue(block.contains("A slice of a classic Margherita pizza."));
    }

    @Test
    void bothItemsUseTheEstablishedComponentApiNotAnInventedOne() throws Exception {
        String source = read(FOOD_ITEMS);
        assertTrue(source.contains("import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;"));
        assertTrue(source.contains("import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;"));
        assertTrue(source.contains("import zcylas.totality.api.core.rpgutils.rarity.ItemTypeComponent;"));
        assertTrue(source.contains("import zcylas.totality.api.core.rpgutils.rarity.LoreComponent;"));
        assertTrue(source.contains(".component(ItemComponents.RARITY,"));
        assertTrue(source.contains(".component(ItemComponents.ITEM_TYPE,"));
        assertTrue(source.contains(".component(ItemComponents.getLore(),"));
        // No invented API: neither the newer ClassificationsComponent nor an explicit
        // TooltipProfileComponent opt-in was introduced — both items rely on the same documented
        // "has RARITY" compatibility fallback IngredientItems already uses.
        assertFalse(source.contains("ClassificationsComponent"));
        assertFalse(source.contains("TooltipProfileComponent"));
        assertFalse(source.contains("getTooltipProfile()"));
    }

    @Test
    void pizzaMargheritaStackSizeIsEightNotOne() throws Exception {
        String block = blockFor(read(FOOD_ITEMS), "PIZZA_MARGHERITA =");
        assertTrue(block.contains("new Item.Properties().stacksTo(8)"),
                "Pizza Margherita's stack size must be the user-approved 8, not the original 1");
        assertFalse(block.contains("stacksTo(1)"),
                "the original stack-size-1 decision must not still be present");
    }

    @Test
    void pizzaMargheritaSliceStackSizeRemainsSixtyFour() throws Exception {
        String block = blockFor(read(FOOD_ITEMS), "PIZZA_MARGHERITA_SLICE =");
        assertTrue(block.contains("new Item.Properties().stacksTo(64)"),
                "the Slice's stack size must remain 64 — the Pizza Margherita stack-size change "
                        + "explicitly does not apply to the Slice");
    }
}
