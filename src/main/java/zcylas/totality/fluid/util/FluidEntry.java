package zcylas.totality.fluid.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FlowingFluid;

public class FluidEntry {
    private final FlowingFluid still;
    private final FlowingFluid flowing;
    private final Block block;
    private final Item bucket;

    public FluidEntry(FlowingFluid still, FlowingFluid flowing, Block block, Item bucket) {
        this.still = still;
        this.flowing = flowing;
        this.block = block;
        this.bucket = bucket;
    }

    public FlowingFluid still() {
        return still;
    }

    public FlowingFluid flowing() {
        return flowing;
    }

    public Block block() {
        return block;
    }

    public Item bucket() {
        return bucket;
    }
}
