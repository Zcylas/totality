package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;
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
        valueLookupBuilder(BlockTags.MINEABLE_WITH_PICKAXE)
        //Energy Items
                //Generators
                .add(EnergyBlocks.GENERATOR)
                //Cells
                .add(EnergyBlocks.COPPER_ENERGY_CELL)
                //Tanks
                .add(ModBlocks.COPPER_TANK)
                //Cables
                .add(EnergyBlocks.COPPER_CABLE)
                //Machines
                .add(EnergyBlocks.ELECTRIC_FURNACE)
        //Ores
                .add(OreBlocks.TIN_ORE)
                .add(OreBlocks.DEEPSLATE_TIN_ORE)
                .add(OreBlocks.GRAPHITE_ORE)
                .add(OreBlocks.DEEPSLATE_GRAPHITE_ORE)
                .add(OreBlocks.LEAD_ORE)
                .add(OreBlocks.DEEPSLATE_LEAD_ORE)
                .add(OreBlocks.SILVER_ORE)
                .add(OreBlocks.DEEPSLATE_SILVER_ORE)
                .add(OreBlocks.RUBY_ORE)
                .add(OreBlocks.DEEPSLATE_RUBY_ORE)
        //Ritual Blocks
                .add(RitualBlocks.RITUAL_ALTAR)
                .add(RitualBlocks.RITUAL_DAIS)
        //Whitestone
                .add(WhitestoneBlocks.WHITESTONE)
                .add(WhitestoneBlocks.FLECKED_WHITESTONE)
                .add(WhitestoneBlocks.POLISHED_WHITESTONE)
                .add(WhitestoneBlocks.POLISHED_WHITESTONE_BRICKS)
        //Natural Blocks
                .add(NaturalBlocks.LIMESTONE)
        ;

        valueLookupBuilder(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(OreBlocks.RUBY_ORE)
                .add(OreBlocks.DEEPSLATE_RUBY_ORE)
        ;

        valueLookupBuilder(BlockTags.MINEABLE_WITH_AXE)
                .add(AlchemyBlocks.APOTHECARY_TABLE)
        ;

        valueLookupBuilder(ModTags.HARVESTABLE)
                .add(AlchemyBlocks.BLUE_MOUNTAIN_FLOWER_BUSH)
                .add(AlchemyBlocks.PURPLE_MOUNTAIN_FLOWER_BUSH)
                .add(AlchemyBlocks.RED_MOUNTAIN_FLOWER_BUSH)
        ;
    }
}
