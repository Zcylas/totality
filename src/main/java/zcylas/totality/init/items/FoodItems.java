package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.rpgutils.rarity.ItemTypeComponent;
import zcylas.totality.api.core.rpgutils.rarity.LoreComponent;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.food.TotalityFoodItem;

/**
 * Totality's first real, authored foods — both {@link TotalityFoodItem}, restoring the true 0-100
 * {@code totality:food} resource directly. See
 * {@code TOTALITY_FOOD_0_100_AND_TOTALITY_FOOD_ITEM_IMPLEMENTATION_REPORT_2026-09-17.md}.
 *
 * <p>Locked authored values (not to be rebalanced): Pizza Margherita restores 48 Food over a
 * 16-second consume; Pizza Margherita Slice restores 6 Food over a 2-second consume — 8 slices = 48
 * = one whole Pizza. Both are directly, independently edible; no cutting recipe exists yet
 * (deliberately deferred to a future Cooking/Processing pass — see the implementation report).
 *
 * <p><b>2026-09-17 (real-client manual test correction):</b> both items now carry the same
 * Totality tooltip metadata every other opted-in item uses ({@link RarityComponent},
 * {@link ItemTypeComponent}, {@link LoreComponent} — see {@code IngredientItems} for the identical
 * convention), so they render through the custom tooltip renderer's Food theme/border/color
 * automatically instead of falling back to vanilla's plain tooltip.
 *
 * <p><b>2026-09-17 (Pizza/Saturation correction pass):</b> both items now also authors a
 * <i>temporary</i> vanilla Saturation contribution (Pizza Margherita {@code +12.0}, Slice
 * {@code +1.5} — 8 slices = 12.0 = one whole Pizza, matching the Food ratio exactly) via {@link
 * TotalityFoodItem}'s new {@code temporarySaturationRestoration} parameter. This is explicitly
 * transitional compatibility-buffer value, not a canonical Diet/Metabolism/Metabolic Reserve figure,
 * and is expected to be replaced wholesale once that future system exists — see {@code
 * TotalityFoodItem}'s own Javadoc for why this is necessary now (vanilla's own zero-nutrition
 * {@code FoodData#add} was silently clamping/destroying real Saturation on every Totality food
 * consumption before this correction).
 */
public final class FoodItems {

    public static final TotalityFoodItem PIZZA_MARGHERITA = TotalityRegistry.registerItem(
            "pizza_margherita",
            properties -> new TotalityFoodItem(properties, 48, 12.0F, 16.0F),
            new Item.Properties().stacksTo(8)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.FOOD))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A classic pizza topped with tomato, mozzarella, and basil."))
    );

    public static final TotalityFoodItem PIZZA_MARGHERITA_SLICE = TotalityRegistry.registerItem(
            "pizza_margherita_slice",
            properties -> new TotalityFoodItem(properties, 6, 1.5F, 2.0F),
            new Item.Properties().stacksTo(64)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.FOOD))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A slice of a classic Margherita pizza."))
    );

    private FoodItems() {}

    public static void register() {}
}
