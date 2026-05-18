package zcylas.totality.item.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.Totality;
import zcylas.totality.api.ritual.ChalkColor;
import zcylas.totality.api.ritual.ChalkSigil;
import zcylas.totality.block.ritual.ChalkBlock;
import zcylas.totality.init.blocks.RitualBlocks;

public class ChalkItem extends Item {

    private final ChalkColor color;

    public ChalkItem(Properties properties, ChalkColor color) {
        super(properties);
        this.color = color;
    }

    public ChalkColor getColor() {
        return color;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();

        // Only place on top of blocks
        if (face != Direction.UP) return InteractionResult.PASS;

        BlockPos placePos = pos.above();

        // If there's already a chalk block of this color here, let ChalkBlock handle cycling
        BlockState existing = level.getBlockState(placePos);
        if (existing.getBlock() instanceof zcylas.totality.block.ritual.ChalkBlock) {
            return InteractionResult.PASS;
        }

        // Must have solid block below
        if (!level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
            return InteractionResult.FAIL;
        }

        // Must be air or replaceable
        if (!level.getBlockState(placePos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            BlockState toPlace = RitualBlocks.CHALK
                    .defaultBlockState()
                    .setValue(zcylas.totality.block.ritual.ChalkBlock.COLOR, color)
                    .setValue(ChalkBlock.SIGIL,
                            ChalkSigil.FOCUS);

            level.setBlock(placePos, toPlace, 3);
            Totality.LOGGER.info("Placed chalk state: {}", level.getBlockState(placePos));
            Totality.LOGGER.info("Color serialized: {}", color.getSerializedName());
            Totality.LOGGER.info("Sigil serialized: {}", ChalkSigil.FOCUS.getSerializedName());
            level.playSound(null, placePos,
                    net.minecraft.sounds.SoundEvents.GRAVEL_PLACE,
                    SoundSource.BLOCKS, 1.0f,
                    1.2f + level.getRandom().nextFloat() * 0.2f);

            context.getItemInHand().hurtAndBreak(1, context.getPlayer(),
                    context.getHand());
        }

        return InteractionResult.SUCCESS;
    }
}