package zcylas.totality.api.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a block's Max Durability and required Mining Tier.
 *
 * <p>Resolution order: (1) a per-block definition, (2) the first matching material (tag)
 * definition, (3) the vanilla fallback derived from hardness and tool tags. Nothing is registered
 * by default — every vanilla/modded block works through the fallback; data-driven authoring
 * (datapack profiles) can later feed {@link #register}/{@link #registerMaterial}.
 */
public final class BlockDurability {

    /** Either field may be null to defer to the next resolution step. */
    public record Definition(@Nullable Float maxDurability, @Nullable Integer requiredTier) {}

    /** Resolved values. {@code max < 0} means unbreakable; {@code max == 0} breaks on any effective impact. */
    public record Resolved(float max, int requiredTier) {
        public boolean unbreakable() { return max < 0f; }
    }

    private static final Map<Block, Definition> BLOCKS = new HashMap<>();
    private static final List<Map.Entry<TagKey<Block>, Definition>> MATERIALS = new ArrayList<>();

    private BlockDurability() {}

    public static void register(Block block, Definition definition) { BLOCKS.put(block, definition); }

    public static void registerMaterial(TagKey<Block> tag, Definition definition) {
        MATERIALS.add(Map.entry(tag, definition));
    }

    public static Resolved resolve(Level level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0f) return new Resolved(-1f, 0);

        Float max = null;
        Integer tier = null;
        Definition def = BLOCKS.get(state.getBlock());
        if (def != null) { max = def.maxDurability(); tier = def.requiredTier(); }
        if (max == null || tier == null) {
            for (var entry : MATERIALS) {
                if (!state.is(entry.getKey())) continue;
                if (max == null) max = entry.getValue().maxDurability();
                if (tier == null) tier = entry.getValue().requiredTier();
                if (max != null && tier != null) break;
            }
        }
        if (max == null) max = hardness * MiningTuning.DURABILITY_PER_HARDNESS;
        if (tier == null) tier = MiningTier.fallbackRequiredTier(state);
        return new Resolved(max, tier);
    }

    /** Vanilla crack stage 0-9 from integrity; -1 when undamaged. */
    public static int crackStage(float integrity, float max) {
        if (max <= 0f || integrity >= max) return -1;
        float taken = (max - integrity) / max;
        return Math.max(0, Math.min(9, (int) (taken * 10f)));
    }
}
