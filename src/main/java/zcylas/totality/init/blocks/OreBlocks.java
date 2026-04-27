package zcylas.totality.init.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.init.TotalityRegistry;

public class OreBlocks {

    public static final Block TIN_ORE = TotalityRegistry.registerBlock("tin_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
            true
    );
    public static final Block GRAPHITE_ORE = TotalityRegistry.registerBlock("graphite_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
            true
    );
    public static final Block DEEPSLATE_GRAPHITE_ORE = TotalityRegistry.registerBlock("deepslate_graphite_ore", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static void register() {}

    private OreBlocks() {}
}
