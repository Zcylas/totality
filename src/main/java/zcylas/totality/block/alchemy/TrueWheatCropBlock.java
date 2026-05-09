package zcylas.totality.block.alchemy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import zcylas.totality.init.items.IngredientItems;

public class TrueWheatCropBlock extends CropBlock {
    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = CropBlock.AGE;

    private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[] {
            Block.box(2, 0, 2, 14, 2, 14),   // age 0
            Block.box(2, 0, 2, 14, 4, 14),   // age 1
            Block.box(2, 0, 2, 14, 6, 14),   // age 2
            Block.box(2, 0, 2, 14, 8, 14),   // age 3
            Block.box(2, 0, 2, 14, 10, 14),  // age 4
            Block.box(2, 0, 2, 14, 12, 14),  // age 5
            Block.box(2, 0, 2, 14, 16, 14),   // age 6
            Block.box(2, 0, 2, 14, 16, 14)   // age 7

    };

    public TrueWheatCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return IngredientItems.TRUE_WHEAT_SEEDS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_AGE[this.getAge(state)];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}