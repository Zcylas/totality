package zcylas.totality.api.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.mining.BlockProfile.Classification;
import zcylas.totality.api.mining.BlockProfile.Form;
import zcylas.totality.api.mining.BlockProfile.Material;
import zcylas.totality.api.mining.BlockProfile.Ownership;

import java.util.function.Predicate;

/**
 * The Block/Material Profile registry for real blocks: {@link BlockProfileResolver} bound to {@link BlockState}
 * and {@link Block}, plus the compatibility fallback and the geometry of shared Integrity owners. A resolved
 * profile is the authority for mining resolution (classification, Block Durability, Required Mining Tier,
 * effective tool, Integrity ownership) and for the Tooltip V2 block rows.
 *
 * <p>The compatibility fallback reproduces the pre-profile behaviour exactly: hardness × 100/1.5, the
 * vanilla-derived Required Mining Tier, the vanilla {@code mineable/*} tags, and hardness-derived classification
 * (&lt; 0 UNBREAKABLE, 0 SPECIAL i.e. vanilla instant break).
 */
public final class BlockProfiles {

    private static final BlockProfileResolver<BlockState, Block> RESOLVER =
            new BlockProfileResolver<>(BlockState::getBlock, BlockProfiles::fallback);

    private BlockProfiles() {}

    // ── authoring ────────────────────────────────────────────────────────────────────────────
    public static void stateOverride(Block block, Predicate<BlockState> when, BlockProfile profile) {
        RESOLVER.stateOverride(block, when, profile);
    }

    public static void block(Block block, BlockProfile profile) { RESOLVER.block(block, profile); }

    /** Verification seam: withdraws an exact block profile a self-test registered. */
    static void removeBlockForVerification(Block block) { RESOLVER.removeBlock(block); }

    public static void assign(Block block, Material material, Form form) { RESOLVER.assign(block, material, form); }

    public static void assign(TagKey<Block> tag, Material material, Form form) {
        RESOLVER.assign(state -> state.is(tag), material, form);
    }

    public static void materialForm(Material material, Form form, BlockProfile profile) {
        RESOLVER.materialForm(material, form, profile);
    }

    public static void material(Material material, BlockProfile profile) { RESOLVER.material(material, profile); }

    public static void transformation(Block from, Block to) { RESOLVER.transformation(from, to); }

    /** Verification seam: withdraws a transformation a self-test registered. */
    static void removeTransformationForVerification(Block from, Block to) { RESOLVER.removeTransformation(from, to); }

    public static boolean transformsTo(Block from, Block to) { return RESOLVER.transformsTo(from, to); }

    // ── resolution ───────────────────────────────────────────────────────────────────────────
    /** Profile of the block at a real position (positional hardness, as the strike pipeline has always used). */
    public static BlockProfile.Resolved resolve(BlockGetter level, BlockPos pos, BlockState state) {
        return RESOLVER.resolve(state, state.getDestroySpeed(level, pos));
    }

    /** Level-free profile (tooltips, tool effectiveness): the block's configured hardness instead of the positional one. */
    public static BlockProfile.Resolved resolveStatic(BlockState state) {
        return RESOLVER.resolve(state, state.getBlock().defaultDestroyTime());
    }

    static BlockProfile fallback(BlockState state, float hardness) {
        return new BlockProfile(null, null, fallbackClassification(state, hardness),
                hardness < 0f ? -1f : hardness * MiningTuning.DURABILITY_PER_HARDNESS,
                TargetEffectiveness.fallbackTools(state),
                hardness < 0f ? 0 : MiningTier.fallbackRequiredTier(state),
                fallbackOwnership(state));
    }

    private static Classification fallbackClassification(BlockState state, float hardness) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BubbleColumnBlock) {
            return Classification.NOT_APPLICABLE;
        }
        if (hardness < 0f) return Classification.UNBREAKABLE;
        if (hardness == 0f) return Classification.SPECIAL;
        return Classification.ORDINARY;
    }

    private static Ownership fallbackOwnership(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock) return Ownership.DOOR_LOWER_HALF;
        if (block instanceof BedBlock) return Ownership.BED_HEAD;
        if (block instanceof PistonBaseBlock || block instanceof PistonHeadBlock) return Ownership.PISTON_BASE;
        return Ownership.POSITION;   // includes each half of a double chest: independent by design
    }

    // ── Integrity ownership ──────────────────────────────────────────────────────────────────
    /** The position whose record holds the Integrity of the block at {@code pos} (itself unless part of a supported assembly).
     *  Each neighbour is read only if its own chunk is loaded (checked per position; nothing is ever loaded). */
    public static BlockPos integrityOwner(BlockGetter level, BlockPos pos, BlockState state) {
        switch (resolveStatic(state).ownership()) {
            case DOOR_LOWER_HALF -> {
                if (state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                    BlockPos below = pos.below();
                    BlockState lower = loaded(level, below) ? level.getBlockState(below) : null;
                    if (lower != null && lower.is(state.getBlock()) && lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) return below;
                }
            }
            case BED_HEAD -> {
                if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                    BlockPos head = pos.relative(state.getValue(BedBlock.FACING));
                    BlockState headState = loaded(level, head) ? level.getBlockState(head) : null;
                    if (headState != null && headState.is(state.getBlock()) && headState.getValue(BedBlock.PART) == BedPart.HEAD) return head;
                }
            }
            case PISTON_BASE -> {
                if (state.getBlock() instanceof PistonHeadBlock) {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    BlockPos base = pos.relative(facing.getOpposite());
                    if (loaded(level, base) && isExtendedBaseOf(level.getBlockState(base), facing)) return base;
                }
            }
            case POSITION -> { }
        }
        return pos;
    }

    /** The other position sharing {@code owner}'s record, or null when the owner currently stands alone. */
    @Nullable
    public static BlockPos integrityPartner(BlockGetter level, BlockPos owner, BlockState ownerState) {
        switch (resolveStatic(ownerState).ownership()) {
            case DOOR_LOWER_HALF -> {
                if (ownerState.hasProperty(DoorBlock.HALF) && ownerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    BlockState upper = loaded(level, owner.above()) ? level.getBlockState(owner.above()) : null;
                    if (upper != null && upper.is(ownerState.getBlock()) && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return owner.above();
                }
            }
            case BED_HEAD -> {
                if (ownerState.hasProperty(BedBlock.PART) && ownerState.getValue(BedBlock.PART) == BedPart.HEAD) {
                    BlockPos foot = owner.relative(ownerState.getValue(BedBlock.FACING).getOpposite());
                    BlockState footState = loaded(level, foot) ? level.getBlockState(foot) : null;
                    if (footState != null && footState.is(ownerState.getBlock()) && footState.getValue(BedBlock.PART) == BedPart.FOOT) return foot;
                }
            }
            case PISTON_BASE -> {
                if (ownerState.getBlock() instanceof PistonBaseBlock) {
                    Direction facing = ownerState.getValue(BlockStateProperties.FACING);
                    BlockPos head = owner.relative(facing);
                    BlockState headState = loaded(level, head) ? level.getBlockState(head) : null;
                    if (headState != null && headState.getBlock() instanceof PistonHeadBlock && headState.getValue(BlockStateProperties.FACING) == facing
                            && isExtendedBaseOf(ownerState, facing)) return head;
                }
            }
            case POSITION -> { }
        }
        return null;
    }

    /** Never loads a chunk: an unloaded neighbour is simply not part of the assembly for this lookup. */
    private static boolean loaded(BlockGetter level, BlockPos pos) {
        return !(level instanceof net.minecraft.world.level.LevelReader reader) || reader.hasChunkAt(pos);
    }

    private static boolean isExtendedBaseOf(BlockState base, Direction headFacing) {
        return base.getBlock() instanceof PistonBaseBlock && base.getValue(PistonBaseBlock.EXTENDED)
                && base.getValue(BlockStateProperties.FACING) == headFacing;
    }
}
