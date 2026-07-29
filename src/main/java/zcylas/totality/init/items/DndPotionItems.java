package zcylas.totality.init.items;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumables;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.potion.dnd.HealingAmount;
import zcylas.totality.item.potion.dnd.HealingPotionItem;

/**
 * Standalone D&D-style potions, independent of Totality's Alchemy API (see {@link PotionItems}
 * for the unrelated Alchemy potion ladder, which already occupies the "potion_of_healing" id).
 */
public final class DndPotionItems {

    // Normal D&D Potion of Healing: 2d4 + 2, 32-tick drink duration.
    // Registry id is "dnd_potion_of_healing" — "potion_of_healing" is already used by
    // PotionItems.POTION_OF_HEALING (Alchemy). Visible name remains "Potion of Healing".
    public static final HealingPotionItem POTION_OF_HEALING = TotalityRegistry.registerItem(
            "dnd_potion_of_healing",
            props -> new HealingPotionItem(HealingAmount.dice(2, Dice.D4, 2), 32, props),
            new Item.Properties()
                    .stacksTo(64)
                    .food(new FoodProperties.Builder().alwaysEdible().build(), Consumables.DEFAULT_DRINK)
    );

    private DndPotionItems() {}

    public static void register() {}
}
