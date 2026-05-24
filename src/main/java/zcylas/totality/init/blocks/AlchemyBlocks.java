package zcylas.totality.init.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.block.alchemy.*;
import zcylas.totality.init.TotalityRegistry;


public class AlchemyBlocks {

    public static final ApothecaryTableBlock APOTHECARY_TABLE = TotalityRegistry.registerBlock(
            "apothecary_table",
            ApothecaryTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .strength(2.5f, 2.5f),
            true
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
            true
    );

    public static final PurpleMountainFlowerBlock PURPLE_MOUNTAIN_FLOWER_BUSH = TotalityRegistry.registerBlock(
            "purple_mountain_flower_bush",
            PurpleMountainFlowerBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.GRASS),
            true
    );

    public static final RedMountainFlowerBlock RED_MOUNTAIN_FLOWER_BUSH = TotalityRegistry.registerBlock(
            "red_mountain_flower_bush",
            RedMountainFlowerBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.GRASS),
            true
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

    public static void register() {}

    private AlchemyBlocks() {}
}