package zcylas.totality.init.blocks;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.block.ritual.ChalkBlock;
import zcylas.totality.block.ritual.RitualAltarBlock;
import zcylas.totality.block.ritual.RitualDaisBlock;
import zcylas.totality.init.TotalityRegistry;

public class RitualBlocks {

    public static final RitualAltarBlock RITUAL_ALTAR = (RitualAltarBlock) TotalityRegistry.registerBlock(
            "ritual_altar",
            RitualAltarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.5f, 6.0f)
                    .noOcclusion()
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops(),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An ancient altar carved with runes and copper sigils. Place it at the heart of a ritual circle."
                    ))
    );
    public static final RitualDaisBlock RITUAL_DAIS = (RitualDaisBlock) TotalityRegistry.registerBlock(
            "ritual_dais",
            RitualDaisBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A stone dais carved with copper studs. Place offerings upon it to empower rituals."
                    ))
    );
    public static final ChalkBlock CHALK = (ChalkBlock) TotalityRegistry.registerBlock(
            "chalk",
            ChalkBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(0f)
                    .noCollision()
                    .noOcclusion()
                    .instabreak()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY),
            false
    );

    public static void register() {}

    private RitualBlocks() {}
}