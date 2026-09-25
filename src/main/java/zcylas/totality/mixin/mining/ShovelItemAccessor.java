package zcylas.totality.mixin.mining;

import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Read access to vanilla's flattening table, the source of the accepted flattening transformation pairs. */
@Mixin(ShovelItem.class)
public interface ShovelItemAccessor {
    @Accessor("FLATTENABLES")
    static Map<Block, BlockState> totality$getFlattenables() { throw new AssertionError(); }
}
