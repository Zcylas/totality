package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import zcylas.totality.init.ModBlocks;
import zcylas.totality.init.blocks.AlchemyBlocks;
import zcylas.totality.init.blocks.EnergyBlocks;
import zcylas.totality.init.blocks.OreBlocks;
import zcylas.totality.init.blocks.RitualBlocks;
import zcylas.totality.init.items.IngredientItems;

import java.util.concurrent.CompletableFuture;

public class ModBlockLootTableProvider extends FabricBlockLootSubProvider {
    public ModBlockLootTableProvider(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(packOutput, registriesFuture);
    }

    @Override
    public void generate() {
        //Energy Blocks
            //Generators
        dropSelf(EnergyBlocks.GENERATOR);
            //Cables
        dropSelf(EnergyBlocks.COPPER_CABLE);
            //Cells
        add(EnergyBlocks.COPPER_ENERGY_CELL, noDrop());
            //Machines
        dropSelf(EnergyBlocks.ELECTRIC_FURNACE);
        //Tanks
        add(ModBlocks.COPPER_TANK, noDrop());
        //Functional Blocks
            //Skill Blocks
        dropSelf(AlchemyBlocks.APOTHECARY_TABLE);

        //Ores
            //Normal Ores
        this.add(OreBlocks.TIN_ORE, this.createOreDrop(OreBlocks.TIN_ORE, IngredientItems.RAW_TIN));
        this.add(OreBlocks.GRAPHITE_ORE, this.createOreDrop(OreBlocks.GRAPHITE_ORE, IngredientItems.GRAPHITE));
        this.add(OreBlocks.DEEPSLATE_GRAPHITE_ORE, this.createOreDrop(OreBlocks.DEEPSLATE_GRAPHITE_ORE, IngredientItems.GRAPHITE));
            //Gemstones
        this.add(OreBlocks.RUBY_ORE, multipleOreDrops(OreBlocks.RUBY_ORE, IngredientItems.ROUGH_RUBY, 1, 3));
        this.add(OreBlocks.DEEPSLATE_RUBY_ORE, multipleOreDrops(OreBlocks.DEEPSLATE_RUBY_ORE, IngredientItems.ROUGH_RUBY, 1, 3));
        //Alchemy Ingredients
            //Flowers
        dropSelf(AlchemyBlocks.BLUE_MOUNTAIN_FLOWER);
        dropSelf(AlchemyBlocks.PURPLE_MOUNTAIN_FLOWER);
        dropSelf(AlchemyBlocks.RED_MOUNTAIN_FLOWER);
        //Ritual Blocks
        dropSelf(RitualBlocks.RITUAL_ALTAR);
        dropSelf(RitualBlocks.RITUAL_DAIS);
    }


    public LootTable.Builder multipleOreDrops(Block drop, Item item, float minDrops, float maxDrops){
        HolderLookup.RegistryLookup<Enchantment> enchantments = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(drop,
                this.applyExplosionDecay(drop,
                        LootItem.lootTableItem(item)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(minDrops, maxDrops)))
                                .apply(ApplyBonusCount.addOreBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE)))
                )
        );
    }
}
