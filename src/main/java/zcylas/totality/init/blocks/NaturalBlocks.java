package zcylas.totality.init.blocks;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;

public class NaturalBlocks {

    public static final Block LIMESTONE = TotalityRegistry.registerBlock(
            "limestone",
            Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.BLOCK))
                    .component(ItemComponents.getLore(), new LoreComponent("A sedimentary rock formed from compacted calcium carbonate."))
    );

    public static void register() {}
}