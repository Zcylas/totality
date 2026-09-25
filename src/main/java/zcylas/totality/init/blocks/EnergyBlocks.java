package zcylas.totality.init.blocks;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.block.energy.CableBlock;
import zcylas.totality.block.energy.ElectricFurnaceBlock;
import zcylas.totality.block.energy.EnergyCellBlock;
import zcylas.totality.block.generator.GeneratorBlock;
import zcylas.totality.init.TotalityRegistry;
import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;

public class EnergyBlocks {

    public static final GeneratorBlock GENERATOR = TotalityRegistry.registerBlock(
            "generator",
            GeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .strength(3.5f, 3.5f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.CRUDE))
    );
    public static final ElectricFurnaceBlock ELECTRIC_FURNACE = TotalityRegistry.registerBlock(
            "electric_furnace",
            ElectricFurnaceBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .strength(3.5f, 3.5f)
                    .requiresCorrectToolForDrops(),
            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.CRUDE))
    );
    public static final EnergyCellBlock COPPER_ENERGY_CELL = TotalityRegistry.registerEnergyCell(
            "copper_energy_cell", 500_000, 64, 64, ItemRarity.CRUDE);
    public static final CableBlock COPPER_CABLE = TotalityRegistry.registerCable("copper_cable", 32L, ItemRarity.CRUDE);

    public static void register(){}

    private EnergyBlocks(){}
}
