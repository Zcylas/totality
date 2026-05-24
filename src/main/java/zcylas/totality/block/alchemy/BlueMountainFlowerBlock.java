package zcylas.totality.block.alchemy;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import zcylas.totality.api.core.util.MountainFlowerBushBlock;
import zcylas.totality.init.items.SKIngredientItems;

public class BlueMountainFlowerBlock extends MountainFlowerBushBlock {

    public BlueMountainFlowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public Item getFlowerItem() {
        return SKIngredientItems.BLUE_MOUNTAIN_FLOWER;
    }
}