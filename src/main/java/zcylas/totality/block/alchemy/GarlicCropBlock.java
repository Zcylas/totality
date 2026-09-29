package zcylas.totality.block.alchemy;


import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

import zcylas.totality.init.items.SKIngredientItems;


/**
 * Vanilla-style eight-stage (age 0-7) garlic crop, planted from a Garlic Clove on farmland. Growth, light,
 * farmland moisture and bone meal are {@link CropBlock}'s own. Drops (loot table): a Garlic Clove before
 * maturity; at age 7, Garlic with a Fortune bonus.
 */
public class GarlicCropBlock extends CropBlock {
    public GarlicCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return SKIngredientItems.GARLIC_CLOVE;
    }

}
