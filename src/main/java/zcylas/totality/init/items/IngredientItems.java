package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.init.TotalityRegistry;

public class IngredientItems {
    //Gears
    public static final Item COPPER_GEAR = TotalityRegistry.registerItem("copper_gear", Item::new, new Item.Properties());
    public static final Item IRON_GEAR = TotalityRegistry.registerItem("iron_gear", Item::new, new Item.Properties());
    public static final Item GOLD_GEAR = TotalityRegistry.registerItem("gold_gear", Item::new, new Item.Properties());
    public static final Item DIAMOND_GEAR = TotalityRegistry.registerItem("diamond_gear", Item::new, new Item.Properties());
    public static final Item NETHERITE_GEAR = TotalityRegistry.registerItem("netherite_gear", Item::new, new Item.Properties());
    //Raw Ores
    public static final Item RAW_TIN = TotalityRegistry.registerItem("raw_tin", Item::new, new Item.Properties());
    public static final Item GRAPHITE = TotalityRegistry.registerItem("graphite", Item::new, new Item.Properties());
    public static void register() {}

    private IngredientItems() {}

}
