package zcylas.totality.api.ability.harvest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Defines custom harvest behavior for a specific block type.
 * Register via HarvestRegistry.register().
 */
public interface HarvestHandler {
    /** Returns true if this handler can harvest the given block state. */
    boolean canHarvest(BlockState state);

    /** Performs the harvest — drop collection, replanting, sounds, etc. */
    void harvest(ServerPlayer player, ServerLevel level,
                 net.minecraft.core.BlockPos pos, BlockState state);
}