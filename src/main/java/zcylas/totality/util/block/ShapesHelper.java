package zcylas.totality.util.block;

import com.google.common.collect.Maps;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.EnumMap;
import java.util.Map;

/**
 * Utilities for rotating {@link VoxelShape VoxelShapes}.
 *
 * <p>The most common pattern: define a shape facing {@link Direction#SOUTH} (or
 * {@link Direction#UP} for vertical shapes) and call {@link #rotateHorizontally}
 * or {@link #rotate} to get a pre-computed map for all directions.</p>
 *
 * <p>Ported from puzzles-lib (Fuzs).</p>
 *
 * <p>Usage:
 * <pre>{@code
 * // At class init time (static final):
 * private static final Map<Direction, VoxelShape> SHAPES =
 *         ShapesHelper.rotateHorizontally(
 *                 Shapes.box(0.0625, 0, 0.0625, 0.9375, 0.75, 0.9375));
 *
 * // In getShape():
 * Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
 * return SHAPES.get(facing);
 * }</pre>
 */
public final class ShapesHelper {

    private ShapesHelper() {}

    // -----------------------------------------------------------------------
    // Full 6-direction rotation (shape assumed to face UP)
    // -----------------------------------------------------------------------

    /**
     * Build a map of this shape rotated into all six {@link Direction Directions}.
     * The provided shape is assumed to face {@link Direction#UP}; passing
     * {@link Direction#UP} will return the original shape unchanged.
     */
    public static Map<Direction, VoxelShape> rotate(VoxelShape shape) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            shapes.put(dir, rotate(dir.getRotation(), shape));
        }
        return Maps.immutableEnumMap(shapes);
    }

    // -----------------------------------------------------------------------
    // Horizontal 4-direction rotation (shape assumed to face SOUTH)
    // -----------------------------------------------------------------------

    /**
     * Build a map of this shape rotated into all four horizontal
     * {@link Direction Directions}. The provided shape is assumed to face
     * {@link Direction#SOUTH}; passing {@link Direction#SOUTH} returns the
     * original shape unchanged.
     */
    public static Map<Direction, VoxelShape> rotateHorizontally(VoxelShape shape) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            shapes.put(dir, rotate(getHorizontalRotation(dir), shape));
        }
        return Maps.immutableEnumMap(shapes);
    }

    /**
     * Build the {@link Quaternionf} that rotates a shape from {@link Direction#SOUTH}
     * to {@code direction}. Passing {@link Direction#SOUTH} returns an identity quaternion.
     */
    public static Quaternionf getHorizontalRotation(Direction direction) {
        return new Quaternionf().rotationY(
                (float) Math.atan2(direction.getStepX(), direction.getStepZ()));
    }

    // -----------------------------------------------------------------------
    // Core rotation
    // -----------------------------------------------------------------------

    /**
     * Rotate a {@link VoxelShape} by the given {@link Quaternionf}.
     * The shape is assumed to be a block shape centred at (0.5, 0.5, 0.5).
     */
    public static VoxelShape rotate(Quaternionf rotation, VoxelShape shape) {
        return rotate(rotation, shape, new Vector3d(0.5, 0.5, 0.5));
    }

    /**
     * Rotate a {@link VoxelShape} by the given {@link Quaternionf} around
     * {@code originOffset}. Decompose the shape into its AABB boxes, rotate each,
     * and union the results.
     *
     * @param rotation     the rotation quaternion to apply
     * @param shape        the shape to rotate
     * @param originOffset offset from the world origin — use (0.5, 0.5, 0.5) for
     *                     block shapes, (0, 0, 0) for entity-centred shapes
     */
    public static VoxelShape rotate(Quaternionf rotation, VoxelShape shape, Vector3d originOffset) {
        VoxelShape[] result = new VoxelShape[]{ Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            Vector3d start = rotation.transform(
                    new Vector3d(minX, minY, minZ).sub(originOffset)).add(originOffset);
            Vector3d end   = rotation.transform(
                    new Vector3d(maxX, maxY, maxZ).sub(originOffset)).add(originOffset);
            result[0] = Shapes.or(result[0],
                    box(start.x, start.y, start.z, end.x, end.y, end.z));
        });
        return result[0];
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Like {@link Shapes#box} but automatically sorts start and end coordinates,
     * which is necessary after rotation since min/max can be swapped.
     */
    public static VoxelShape box(
            double x1, double y1, double z1,
            double x2, double y2, double z2) {
        return Shapes.box(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }
}
