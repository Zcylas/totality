package zcylas.totality.init.blocks;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.block.alchemy.*;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.alchemy.BlueMountainFlowerItem;
import zcylas.totality.item.alchemy.PurpleMountainFlowerItem;
import zcylas.totality.item.alchemy.RedMountainFlowerItem;

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
    public static final BlueMountainFlowerBlock BLUE_MOUNTAIN_FLOWER = TotalityRegistry.registerFlowerIngredient(
            "blue_mountain_flower",
            props -> new BlueMountainFlowerBlock(MobEffects.REGENERATION, 7, props),
            BlueMountainFlowerItem::new,
            ItemRarity.UNCOMMON,
            "A delicate blue bloom found on misty hillsides. Sought by healers for its regenerative properties."
    );

    public static final PurpleMountainFlowerBlock PURPLE_MOUNTAIN_FLOWER = TotalityRegistry.registerFlowerIngredient(
            "purple_mountain_flower",
            props -> new PurpleMountainFlowerBlock(MobEffects.POISON, 7, props),
            PurpleMountainFlowerItem::new,
            ItemRarity.UNCOMMON,
            "Its vivid hue is a warning. Apothecaries prize it, but handle with care."
    );

    public static final RedMountainFlowerBlock RED_MOUNTAIN_FLOWER = TotalityRegistry.registerFlowerIngredient(
            "red_mountain_flower",
            props -> new RedMountainFlowerBlock(MobEffects.WEAKNESS, 7, props),
            RedMountainFlowerItem::new,
            ItemRarity.UNCOMMON,
            "Blooms in volcanic soil where little else survives. Its petals carry a subtle draining quality."
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