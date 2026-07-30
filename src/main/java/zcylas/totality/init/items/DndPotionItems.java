package zcylas.totality.init.items;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumables;
import zcylas.totality.api.core.rpgutils.rarity.ClassificationsComponent;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.rpgutils.rarity.ItemTypeComponent;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;
import zcylas.totality.api.core.rpgutils.rarity.TooltipProfileComponent;
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
    //
    // Explicitly opted into Totality tooltip presentation with the existing POTION
    // classification, and authored as UNCOMMON rarity (presentation-cleanup pass, Finding 3) —
    // purely a presentation/metadata choice, not a change to Alchemy, the healing formula, use
    // duration, notifications, or this item's independence from the Alchemy potion ladder above.
    public static final HealingPotionItem POTION_OF_HEALING = TotalityRegistry.registerItem(
            "dnd_potion_of_healing",
            props -> new HealingPotionItem(HealingAmount.dice(2, Dice.D4, 2), 32, props),
            new Item.Properties()
                    .stacksTo(64)
                    .food(new FoodProperties.Builder().alwaysEdible().build(), Consumables.DEFAULT_DRINK)
                    .component(ItemComponents.getTooltipProfile(), TooltipProfileComponent.STANDARD)
                    .component(ItemComponents.getRarity(), new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.POTION))
                    .component(ItemComponents.getClassifications(), ClassificationsComponent.of(ItemType.POTION))
    );

    private DndPotionItems() {}

    public static void register() {}
}
