package zcylas.totality.block.alchemy;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import zcylas.totality.api.core.util.MountainFlowerBushBlock;
import zcylas.totality.init.items.SKIngredientItems;

public class PurpleMountainFlowerBlock extends MountainFlowerBushBlock {

    public PurpleMountainFlowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public Item getFlowerItem() {
        return SKIngredientItems.PURPLE_MOUNTAIN_FLOWER;
    }
}