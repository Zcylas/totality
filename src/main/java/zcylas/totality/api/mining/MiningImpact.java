package zcylas.totality.api.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * One discrete strike on one block.
 *
 * @param damage           absolute Block Integrity this impact removes (NOT a fraction of the block)
 * @param tier             Mining Tier of the source, compared against the block's required tier
 * @param presentationBand floating-text emphasis only ({@link MiningTuning#presentationBand}); never used in gameplay math
 */
public record MiningImpact(BlockPos pos, Direction face, Vec3 hitLocation, float damage, int tier,
                           MiningSource source, int presentationBand) {

    public MiningImpact(BlockPos pos, Direction face, Vec3 hitLocation, float damage, int tier, MiningSource source) {
        this(pos, face, hitLocation, damage, tier, source, 0);
    }
}
