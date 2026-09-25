package zcylas.totality.init.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.init.TotalityRegistry;
import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;

public class OreBlocks {

    public static final Block TIN_ORE = TotalityRegistry.registerBlock("tin_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block DEEPSLATE_TIN_ORE = TotalityRegistry.registerBlock("deepslate_tin_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block GRAPHITE_ORE = TotalityRegistry.registerBlock("graphite_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block DEEPSLATE_GRAPHITE_ORE = TotalityRegistry.registerBlock("deepslate_graphite_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block LEAD_ORE = TotalityRegistry.registerBlock("lead_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block DEEPSLATE_LEAD_ORE = TotalityRegistry.registerBlock("deepslate_lead_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block VIBRANIUM_ORE = TotalityRegistry.registerBlock("vibranium_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block DEEPSLATE_VIBRANIUM_ORE = TotalityRegistry.registerBlock("deepslate_vibranium_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(6.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block SILVER_ORE = TotalityRegistry.registerBlock("silver_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block DEEPSLATE_SILVER_ORE = TotalityRegistry.registerBlock("deepslate_silver_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block RUBY_ORE = TotalityRegistry.registerBlock("ruby_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block DEEPSLATE_RUBY_ORE = TotalityRegistry.registerBlock("deepslate_ruby_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );


    public static void register() {}

    private OreBlocks() {}
}
