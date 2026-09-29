package zcylas.totality.init.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import zcylas.totality.block.alchemy.*;
import zcylas.totality.init.TotalityRegistry;
import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;


public class AlchemyBlocks {

    public static final ApothecaryTableBlock APOTHECARY_TABLE = TotalityRegistry.registerBlock(
            "apothecary_table",
            ApothecaryTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .strength(2.5f, 2.5f),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );

    /**
     * Blue Mountain Flower — flower block + alchemy ingredient item.
     * The item is a BlockItem so it can be placed in the world.
     * Adding Red/Purple later: copy this pattern with new block/item classes.
     */
    public static final BlueMountainFlowerBlock BLUE_MOUNTAIN_FLOWER_BUSH = TotalityRegistry.registerBlock(
            "blue_mountain_flower_bush",
            BlueMountainFlowerBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.GRASS),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );

    public static final PurpleMountainFlowerBlock PURPLE_MOUNTAIN_FLOWER_BUSH = TotalityRegistry.registerBlock(
            "purple_mountain_flower_bush",
            PurpleMountainFlowerBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.GRASS),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );

    public static final RedMountainFlowerBlock RED_MOUNTAIN_FLOWER_BUSH = TotalityRegistry.registerBlock(
            "red_mountain_flower_bush",
            RedMountainFlowerBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.GRASS),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );
    public static final Block TRUE_WHEAT_CROP = TotalityRegistry.registerBlock(
            "true_wheat_crop",
            TrueWheatCropBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.CROP),
            false
    );
    /** Planted from {@code SKIngredientItems.GARLIC_CLOVE}; vanilla carrot/potato crop properties. */
    public static final GarlicCropBlock GARLIC_CROP = TotalityRegistry.registerBlock(
            "garlic_crop",
            GarlicCropBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.CROP)
                    .pushReaction(PushReaction.DESTROY),
            false
    );
    /** Wild regrowing plant (Mountain Flower Bush pattern); right-click harvests one {@code SKIngredientItems.JUEYUN_CHILI}. */
    public static final JueyunChiliPlantBlock JUEYUN_CHILI_PLANT = TotalityRegistry.registerBlock(
            "jueyun_chili_plant",
            JueyunChiliPlantBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.GRASS)
                    .pushReaction(PushReaction.DESTROY),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
    );

    public static void register() {}

    private AlchemyBlocks() {}
}