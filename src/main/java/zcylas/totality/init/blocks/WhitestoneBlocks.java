package zcylas.totality.init.blocks;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;

public class WhitestoneBlocks {

    public static final Block WHITESTONE = TotalityRegistry.registerBlock(
            "whitestone",
            Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.BLOCK))
                    .component(ItemComponents.getLore(), new LoreComponent("A pale stone found in the mountains of Tal'Dorei."))
    );

    public static final Block FLECKED_WHITESTONE = TotalityRegistry.registerBlock(
            "flecked_whitestone",
            Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.BLOCK))
                    .component(ItemComponents.getLore(), new LoreComponent("Whitestone infused with traces of residuum."))
    );
    public static final Block POLISHED_WHITESTONE = TotalityRegistry.registerBlock(
            "polished_whitestone",
            Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.BLOCK))
                    .component(ItemComponents.getLore(), new LoreComponent("A smooth, refined stone from the mountains of Tal'Dorei, favored in Whitestone architecture."))
    );
    public static final Block POLISHED_WHITESTONE_BRICKS = TotalityRegistry.registerBlock(
            "polished_whitestone_bricks",
            Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.BLOCK))
                    .component(ItemComponents.getLore(), new LoreComponent("Polished Whitestone cut into sturdy bricks, fit for the halls and keeps of Tal'Dorei."))
    );

    public static void register() {}
}