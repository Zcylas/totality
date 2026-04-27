package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import zcylas.totality.init.ModBlocks;
import zcylas.totality.init.blocks.EnergyBlocks;
import zcylas.totality.init.blocks.OreBlocks;
import zcylas.totality.init.items.IngredientItems;

import java.util.concurrent.CompletableFuture;

public class ModBlockLootTableProvider extends FabricBlockLootSubProvider {
    public ModBlockLootTableProvider(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(packOutput, registriesFuture);
    }

    @Override
    public void generate() {
        add(ModBlocks.COPPER_TANK, noDrop());
        add(EnergyBlocks.COPPER_ENERGY_CELL, noDrop());
        dropSelf(EnergyBlocks.GENERATOR);
        dropSelf(EnergyBlocks.COPPER_CABLE);
        this.add(OreBlocks.TIN_ORE, this.createOreDrop(OreBlocks.TIN_ORE, IngredientItems.RAW_TIN));
        this.add(OreBlocks.GRAPHITE_ORE, this.createOreDrop(OreBlocks.GRAPHITE_ORE, IngredientItems.GRAPHITE));
        this.add(OreBlocks.DEEPSLATE_GRAPHITE_ORE, this.createOreDrop(OreBlocks.DEEPSLATE_GRAPHITE_ORE, IngredientItems.GRAPHITE));
    }
}
