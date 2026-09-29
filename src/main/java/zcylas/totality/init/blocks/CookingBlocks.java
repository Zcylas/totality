package zcylas.totality.init.blocks;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.rpgutils.rarity.ItemTypeComponent;
import zcylas.totality.api.core.rpgutils.rarity.LoreComponent;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;
import zcylas.totality.block.cooking.CuttingBoardBlock;
import zcylas.totality.init.TotalityRegistry;

/** Kitchen / food-preparation blocks — the future Cooking API's workstations. */
public final class CookingBlocks {

    public static final CuttingBoardBlock CUTTING_BOARD = TotalityRegistry.registerBlock(
            "cutting_board",
            CuttingBoardBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.8F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()
                    .noOcclusion(),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.BLOCK))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A plain wooden board for preparing ingredients. Set food on it, then cut it with a blade."
                    ))
    );

    public static void register() {}

    private CookingBlocks() {}
}
