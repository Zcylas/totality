package zcylas.totality.block.ritual;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import zcylas.totality.api.ritual.ChalkColor;
import zcylas.totality.api.ritual.ChalkSigil;
import zcylas.totality.init.items.RitualItems;

public class ChalkBlock extends Block {

    public static final EnumProperty<ChalkSigil> SIGIL =
            EnumProperty.create("sigil", ChalkSigil.class);
    public static final EnumProperty<ChalkColor> COLOR =
            EnumProperty.create("color", ChalkColor.class);

    private static final VoxelShape SHAPE =
            Shapes.box(0, 0, 0, 1, 0.25 / 16.0, 1);

    public ChalkBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SIGIL, ChalkSigil.FOCUS));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(ChalkBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIGIL, COLOR);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // no collision — you walk through it
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        // Cycle glyph only if holding the matching chalk item
        if (!level.isClientSide()) {
            ChalkColor blockColor = state.getValue(COLOR);
            if (isMatchingChalk(stack, blockColor)) {
                ChalkSigil next = state.getValue(SIGIL).next();
                level.setBlock(pos, state.setValue(SIGIL, next), 3);
                level.playSound(null, pos,
                        SoundEvents.GRAVEL_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0f,
                        0.8f + level.getRandom().nextFloat() * 0.4f);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    // ── Placement ────────────────────────────────────────────────────────────

    /**
     * Called by ChalkItem to place this block with the correct color and default glyph.
     */
    public static BlockState defaultStateFor(ChalkColor color) {
        return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(); // placeholder — replaced by RitualBlocks
    }

    private static boolean isMatchingChalk(ItemStack stack, ChalkColor color) {
        return switch (color) {
            case WHITE -> stack.is(RitualItems.WHITE_CHALK);
            case GOLD -> stack.is(RitualItems.GOLD_CHALK);
            case BLUE -> stack.is(RitualItems.BLUE_CHALK);
            case PURPLE -> stack.is(RitualItems.PURPLE_CHALK);
            case RED -> stack.is(RitualItems.RED_CHALK);
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level,
                                 BlockPos pos) {
        // Chalk needs a solid block below it
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}