package zcylas.totality.block.alchemy;


import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

import zcylas.totality.init.items.SKIngredientItems;


public class GarlicCropBlock extends CropBlock {
    public GarlicCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return SKIngredientItems.GARLIC;
    }

}
