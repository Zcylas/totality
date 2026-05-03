package zcylas.totality.init.items;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.alchemy.RockWarblerEggItem;
import zcylas.totality.item.alchemy.SalmonRoeItem;
import zcylas.totality.item.alchemy.TrueWheatItem;

/**
 * All alchemy ingredient items.
 * BlueMountainFlowerItem is registered via AlchemyBlocks (as a BlockItem) — not here.
 */
public final class SKIngredientItems {

    // Public so TotalityRegistry.registerFlowerIngredient() can use it
    public static final FoodProperties INGREDIENT_FOOD = new FoodProperties.Builder()
            .nutrition(0)
            .saturationModifier(0f)
            .alwaysEdible()
            .build();

    public static final SalmonRoeItem SALMON_ROE = TotalityRegistry.registerItem(
            "salmon_roe",
            SalmonRoeItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
    );

    public static final RockWarblerEggItem ROCK_WARBLER_EGG = TotalityRegistry.registerItem(
            "rock_warbler_egg",
            RockWarblerEggItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
    );

    public static final TrueWheatItem TRUE_WHEAT = TotalityRegistry.registerItem(
            "true_wheat",
            TrueWheatItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
    );

    private SKIngredientItems() {}

    public static void register() {}
}