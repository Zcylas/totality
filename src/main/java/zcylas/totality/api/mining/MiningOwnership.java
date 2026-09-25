package zcylas.totality.api.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ONE rule for "does Totality mining (rather than vanilla) own this strike?", used verbatim by the
 * client (intercepting vanilla click/hold mining) and the server (refusing vanilla START/STOP destroy).
 * Sharing it means the two sides cannot silently disagree.
 *
 * <p>Vanilla owns: any non-Survival mode (Creative, Adventure, Spectator), items with
 * {@code PIERCING_WEAPON} (spears — vanilla never mines with them: 26.2 {@code startAttack} stabs and
 * {@code continueAttack} skips them, so no destroy packet is ever produced), and instant-break/unbreakable
 * blocks (hardness &lt;= 0), and blocks whose Block/Material Profile classifies them SPECIAL or NOT_APPLICABLE.
 */
public final class MiningOwnership {

    private MiningOwnership() {}

    /** Item/mode part of the rule (no block involved). */
    public static boolean ownsItemAndMode(GameType mode, ItemStack held) {
        return mode == GameType.SURVIVAL && !held.has(DataComponents.PIERCING_WEAPON);
    }

    /**
     * A block profiled SPECIAL (or NOT_APPLICABLE) stays with vanilla even when it has hardness; an UNBREAKABLE
     * profile on a block with hardness is owned so that no vanilla progress can break it.
     */
    public static boolean owns(GameType mode, ItemStack held, BlockGetter level, BlockPos pos, BlockState state) {
        if (!ownsItemAndMode(mode, held) || !(state.getDestroySpeed(level, pos) > 0f)) return false;
        BlockProfile.Classification c = BlockProfiles.resolve(level, pos, state).classification();
        return c == BlockProfile.Classification.ORDINARY || c == BlockProfile.Classification.UNBREAKABLE;
    }
}
