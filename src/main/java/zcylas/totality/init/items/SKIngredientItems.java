package zcylas.totality.init.items;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.alchemy.*;

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
            new Item.Properties().stacksTo(64)
                    .food(INGREDIENT_FOOD)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.INGREDIENT))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Carefully harvested from river salmon. A prized binding agent in many alchemical preparations."
                    ))
    );

    public static final RockWarblerEggItem ROCK_WARBLER_EGG = TotalityRegistry.registerItem(
            "rock_warbler_egg",
            RockWarblerEggItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.INGREDIENT))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Laid in high mountain crags by a bird few have ever seen. Prized by apothecaries for its binding properties."
                    ))
    );

    public static final TrueWheatItem TRUE_WHEAT = TotalityRegistry.registerItem(
            "true_wheat",
            TrueWheatItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.INGREDIENT))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A simple grain, gathered and dried. Unremarkable on its own, but a staple of many basic recipes."
                    ))
    );

    public static final GarlicItem GARLIC = TotalityRegistry.registerItem(
            "garlic",
            GarlicItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.INGREDIENT))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Humble but essential. Alchemists know that the most ordinary things often hold the deepest power."
                    ))
    );
    public static final RedMountainFlowerItem RED_MOUNTAIN_FLOWER = TotalityRegistry.registerItem(
            "red_mountain_flower",
            RedMountainFlowerItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.INGREDIENT))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A delicate red bloom found on misty hillsides. Sought by healers for its regenerative properties."
                    ))
    );
    public static final BlueMountainFlowerItem BLUE_MOUNTAIN_FLOWER = TotalityRegistry.registerItem(
            "blue_mountain_flower",
            BlueMountainFlowerItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.INGREDIENT))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Grows where the mountain mist lingers longest. Healers seek it out, though few know all its properties."
                    ))
    );

    public static final PurpleMountainFlowerItem PURPLE_MOUNTAIN_FLOWER = TotalityRegistry.registerItem(
            "purple_mountain_flower",
            PurpleMountainFlowerItem::new,
            new Item.Properties().stacksTo(64).food(INGREDIENT_FOOD)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.INGREDIENT))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Found in the cold reaches where taiga meets stone. Its scent is sweet, but apothecaries handle it with care."
                    ))
    );

    private SKIngredientItems() {}

    public static void register() {}
}