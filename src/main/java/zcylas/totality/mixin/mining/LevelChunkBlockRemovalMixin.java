package zcylas.totality.mixin.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 2: tells {@link BlockDamageStorage} the moment a server block is REMOVED (replaced by a
 * different {@code Block}), so a damaged block's Integrity can never be re-attached to a fresh block placed later at
 * the same position, even one with the same registry id. Every server block change (setBlock, destroyBlock,
 * removeBlock, explosions, pistons, commands) goes through {@code LevelChunk.setBlockState}.
 *
 * <p>Read at HEAD, before the change: the block currently in the section versus the incoming one. Same-block state
 * changes (door opening, piston retraction of the base, log axis) are not removals and are ignored. Only the
 * already-loaded chunk being written is read; nothing loads chunks, scans the world, or re-enters setBlock.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkBlockRemovalMixin {

    @Final @Shadow private Level level;

    @Shadow public abstract BlockState getBlockState(BlockPos pos);

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void totality$notifyBlockRemoval(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        BlockState old = getBlockState(pos);
        if (old.is(state.getBlock())) return;          // same block: an ordinary state change (or no change at all)
        BlockDamageStorage.onBlockRemoved(serverLevel, pos);
    }
}
