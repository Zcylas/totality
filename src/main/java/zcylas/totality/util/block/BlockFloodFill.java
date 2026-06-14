package zcylas.totality.util.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * BFS flood-fill utilities that collect sets of spatially connected blocks
 * sharing the same type or tag.
 *
 * <p>Adapted from Collective (Serilum). The original used recursive calls with
 * shared static state (causing thread-safety and re-entrancy bugs). This version
 * uses an explicit {@link ArrayDeque}-based BFS, which is safe and faster.</p>
 *
 * <p>Uses:
 * <ul>
 *   <li>Ritual: find all chalk sigil blocks adjacent to an Altar.</li>
 *   <li>Smeltery: validate that all controller/wall/tank blocks form a contiguous
 *       structure.</li>
 *   <li>Structure Lift: collect the bounding box of connected blocks.</li>
 *   <li>Storage network: topology scan for connected cable/machine nodes.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * List<BlockPos> nodes = BlockFloodFill.getConnected(level, altarPos,
 *         List.of(ModBlocks.RITUAL_DAIS), 12);
 * }</pre>
 */
public final class BlockFloodFill {

    private BlockFloodFill() {}

    // -----------------------------------------------------------------------
    // By Block type
    // -----------------------------------------------------------------------

    /**
     * Find all blocks connected to {@code startPos} (6-directionally) that are
     * one of the types in {@code allowedBlocks}, up to the default 50-block cap.
     *
     * @return the set of matching connected positions (includes startPos if it matches)
     */
    public static List<BlockPos> getConnected(Level level, BlockPos startPos, List<Block> allowedBlocks) {
        return getConnected(level, startPos, allowedBlocks, 50);
    }

    /**
     * Find all blocks connected to {@code startPos} (6-directionally) that are
     * one of the types in {@code allowedBlocks}, within {@code maxDistance} blocks
     * of the start position.
     */
    public static List<BlockPos> getConnected(Level level, BlockPos startPos, List<Block> allowedBlocks, int maxDistance) {
        List<BlockPos>  result  = new ArrayList<>();
        Set<BlockPos>   visited = new HashSet<>();
        Deque<BlockPos> queue   = new ArrayDeque<>();

        BlockPos immStart = startPos.immutable();
        visited.add(immStart);

        if (allowedBlocks.contains(level.getBlockState(immStart).getBlock())) {
            result.add(immStart);
            queue.add(immStart);
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir).immutable();
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);
                if (!withinChebyshev(startPos, neighbor, maxDistance)) continue;
                if (allowedBlocks.contains(level.getBlockState(neighbor).getBlock())) {
                    result.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // By tag
    // -----------------------------------------------------------------------

    /**
     * Variant of {@link #getConnected} that accepts blocks matching any of
     * the given {@link TagKey TagKeys} instead of a specific type list.
     */
    public static List<BlockPos> getConnectedByTag(Level level, BlockPos startPos, List<TagKey<Block>> tags) {
        return getConnectedByTag(level, startPos, tags, 50);
    }

    /**
     * @see #getConnectedByTag(Level, BlockPos, List)
     */
    public static List<BlockPos> getConnectedByTag(Level level, BlockPos startPos, List<TagKey<Block>> tags, int maxDistance) {
        List<BlockPos>  result  = new ArrayList<>();
        Set<BlockPos>   visited = new HashSet<>();
        Deque<BlockPos> queue   = new ArrayDeque<>();

        BlockPos immStart = startPos.immutable();
        visited.add(immStart);

        if (matchesAnyTag(level.getBlockState(immStart), tags)) {
            result.add(immStart);
            queue.add(immStart);
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir).immutable();
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);
                if (!withinChebyshev(startPos, neighbor, maxDistance)) continue;
                if (matchesAnyTag(level.getBlockState(neighbor), tags)) {
                    result.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Get the 6 face-adjacent positions (N/S/E/W/Up/Down).
     */
    public static List<BlockPos> getFaceAdjacent(BlockPos pos) {
        List<BlockPos> adjacent = new ArrayList<>(6);
        for (Direction dir : Direction.values()) adjacent.add(pos.relative(dir));
        return adjacent;
    }

    /**
     * Chebyshev (chess-king) distance — returns {@code true} when the Manhattan
     * component along any single axis is within {@code radius}.
     */
    public static boolean withinChebyshev(BlockPos a, BlockPos b, int radius) {
        return Math.abs(a.getX() - b.getX()) <= radius
                && Math.abs(a.getY() - b.getY()) <= radius
                && Math.abs(a.getZ() - b.getZ()) <= radius;
    }

    private static boolean matchesAnyTag(BlockState state, List<TagKey<Block>> tags) {
        for (TagKey<Block> tag : tags) {
            if (state.is(tag)) return true;
        }
        return false;
    }
}
