package zcylas.totality.api.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A block's Max Durability and required Mining Tier, read from its {@link BlockProfiles Block/Material Profile}
 * (exact state override, exact block, material + form, material default, then the vanilla-derived compatibility
 * fallback). This class is the durability view of that one authority, not a second source of truth.
 */
public final class BlockDurability {

    /** Either field may be null to defer to the next resolution layer. */
    public record Definition(@Nullable Float maxDurability, @Nullable Integer requiredTier) {}

    /** Resolved values. {@code max < 0} means unbreakable; {@code max == 0} breaks on any effective impact. */
    public record Resolved(float max, int requiredTier) {
        public boolean unbreakable() { return max < 0f; }
    }

    private BlockDurability() {}

    /** Shorthand for an exact block profile that authors only durability and/or tier. */
    public static void register(Block block, Definition definition) {
        BlockProfile profile = BlockProfile.EMPTY;
        if (definition.maxDurability() != null) profile = profile.withMaxDurability(definition.maxDurability());
        if (definition.requiredTier() != null) profile = profile.withRequiredTier(definition.requiredTier());
        BlockProfiles.block(block, profile);
    }

    public static Resolved resolve(Level level, BlockPos pos, BlockState state) {
        return of(BlockProfiles.resolve(level, pos, state));
    }

    /**
     * Level-free resolution for contexts with no world position (tooltips): the same profile resolution as
     * {@link #resolve}, with the block's own configured hardness ({@link Block#defaultDestroyTime()}) instead of
     * {@code state.getDestroySpeed(level, pos)}.
     */
    public static Resolved resolveStatic(BlockState state) {
        return of(BlockProfiles.resolveStatic(state));
    }

    private static Resolved of(BlockProfile.Resolved profile) {
        if (profile.classification() == BlockProfile.Classification.UNBREAKABLE) return new Resolved(-1f, 0);
        return new Resolved(profile.maxDurability(), profile.requiredTier());
    }

    /** Vanilla crack stage 0-9 from integrity; -1 when undamaged. */
    public static int crackStage(float integrity, float max) {
        if (max <= 0f || integrity >= max) return -1;
        float taken = (max - integrity) / max;
        return Math.max(0, Math.min(9, (int) (taken * 10f)));
    }
}
