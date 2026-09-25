package zcylas.totality.mixin.mining;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Read access to vanilla's stripping table, the source of the accepted stripping transformation pairs. */
@Mixin(AxeItem.class)
public interface AxeItemAccessor {
    @Accessor("STRIPPABLES")
    static Map<Block, Block> totality$getStrippables() { throw new AssertionError(); }
}
