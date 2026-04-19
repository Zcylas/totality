package zcylas.totality.block.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class CableShapeUtil {

    private static final Map<BlockState, VoxelShape> SHAPE_CACHE = new IdentityHashMap<>();

    private static VoxelShape buildShape(BlockState state) {
        double s = CableBlock.THICKNESS;
        VoxelShape core = Shapes.box(s, s, s, 1 - s, 1 - s, 1 - s);
        List<VoxelShape> arms = new ArrayList<>();

        for (Direction dir : Direction.values()) {
            if (state.getValue(CableBlock.PROPERTY_MAP.get(dir))) {
                double[] min = {s, s, s};
                double[] max = {1 - s, 1 - s, 1 - s};
                int axis = dir.getAxis().ordinal();
                if (dir.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                    max[axis] = 1.0;
                } else {
                    min[axis] = 0.0;
                }
                arms.add(Shapes.box(min[0], min[1], min[2], max[0], max[1], max[2]));
            }
        }

        return Shapes.or(core, arms.toArray(new VoxelShape[0]));
    }

    public static VoxelShape getShape(BlockState state) {
        return SHAPE_CACHE.computeIfAbsent(state, CableShapeUtil::buildShape);
    }
}