package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;
import zcylas.totality.init.ModBlocks;
import zcylas.totality.init.ModTags;
import zcylas.totality.init.blocks.*;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends FabricTagsProvider.BlockTagsProvider {
    public ModBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
        super(output, registryLookupFuture);
    }

    @Override
    protected void addTags(HolderLookup.@NonNull Provider provider) {
        builder(BlockTags.MINEABLE_WITH_PICKAXE)
        //Energy Items
                //Generators
                .add(key(EnergyBlocks.GENERATOR))
                //Cells
                .add(key(EnergyBlocks.COPPER_ENERGY_CELL))
                //Tanks
                .add(key(ModBlocks.COPPER_TANK))
                //Cables
                .add(key(EnergyBlocks.COPPER_CABLE))
                //Machines
                .add(key(EnergyBlocks.ELECTRIC_FURNACE))
        //Ores
                .add(key(OreBlocks.TIN_ORE))
                .add(key(OreBlocks.DEEPSLATE_TIN_ORE))
                .add(key(OreBlocks.GRAPHITE_ORE))
                .add(key(OreBlocks.DEEPSLATE_GRAPHITE_ORE))
                .add(key(OreBlocks.LEAD_ORE))
                .add(key(OreBlocks.DEEPSLATE_LEAD_ORE))
                .add(key(OreBlocks.SILVER_ORE))
                .add(key(OreBlocks.DEEPSLATE_SILVER_ORE))
                .add(key(OreBlocks.RUBY_ORE))
                .add(key(OreBlocks.DEEPSLATE_RUBY_ORE))
        //Ritual Blocks
                .add(key(RitualBlocks.RITUAL_ALTAR))
                .add(key(RitualBlocks.RITUAL_DAIS))
        //Whitestone
                .add(key(WhitestoneBlocks.WHITESTONE))
                .add(key(WhitestoneBlocks.FLECKED_WHITESTONE))
                .add(key(WhitestoneBlocks.POLISHED_WHITESTONE))
                .add(key(WhitestoneBlocks.POLISHED_WHITESTONE_BRICKS))
        //Natural Blocks
                .add(key(NaturalBlocks.LIMESTONE))
        ;

        builder(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(key(OreBlocks.RUBY_ORE))
                .add(key(OreBlocks.DEEPSLATE_RUBY_ORE))
        ;

        builder(BlockTags.MINEABLE_WITH_AXE)
                .add(key(AlchemyBlocks.APOTHECARY_TABLE))
        ;

        builder(ModTags.HARVESTABLE)
        ;
    }

    /** MC 26.2's BlockItemTagAppender.add() takes a ResourceKey, not the Block itself. */
    private static ResourceKey<Block> key(Block block) {
        return block.builtInRegistryHolder().key();
    }
}
