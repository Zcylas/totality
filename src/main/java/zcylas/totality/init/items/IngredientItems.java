package zcylas.totality.init.items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.init.blocks.AlchemyBlocks;

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
    //GEMSTONES
        //ROUGH
    public static final Item ROUGH_RUBY = TotalityRegistry.registerItem("rough_ruby", Item::new, new Item.Properties());

    //For Crops
        //Seeds
    public static final Item TRUE_WHEAT_SEEDS = TotalityRegistry.registerItem("true_wheat_seeds",
            properties -> new BlockItem(AlchemyBlocks.TRUE_WHEAT_CROP, properties), new Item.Properties().stacksTo(64));
    // Whitestone
    public static final Item WHITESTONE_CHUNK = TotalityRegistry.registerItem(
            "whitestone_chunk", Item::new,
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MATERIAL))
                    .component(ItemComponents.getLore(), new LoreComponent("A raw chunk of whitestone. Sturdy and unrefined."))
    );

    public static final Item RESIDUUM_FLECKED_CHUNK = TotalityRegistry.registerItem(
            "residuum_flecked_chunk", Item::new,
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.REAGENT))
                    .component(ItemComponents.getLore(), new LoreComponent("A chunk of whitestone with visible residuum inclusions."))
    );
    public static final Item LIMESTONE_CHUNK = TotalityRegistry.registerItem(
            "limestone_chunk", Item::new,
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MATERIAL))
                    .component(ItemComponents.getLore(), new LoreComponent("A raw chunk of limestone, useful for processing into building materials."))
    );
    public static void register() {}

    private IngredientItems() {}

}
