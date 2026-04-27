package zcylas.totality.api.magic.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class SpellUtil {

    /**
     * Calculates AOE blocks around a hit position.
     * Expands perpendicular to the hit face, accounting for caster direction.
     * radius=0 → just the one block
     * radius=1 → 3x3 face
     * radius=2 → 5x5 face
     */
    public static List<BlockPos> calcAoeBlocks(LivingEntity caster, BlockPos origin,
                                               BlockHitResult hit, double aoeRadius) {
        return calcAoeBlocks(caster, origin, hit, aoeRadius, 0);
    }

    public static List<BlockPos> calcAoeBlocks(LivingEntity caster, BlockPos origin,
                                               BlockHitResult hit, double aoeRadius,
                                               int pierceCount) {
        int size  = (int)(1 + Math.floor(aoeRadius));
        int depth = 1 + pierceCount;
        Vec3i facingVec = caster.getDirection().getUnitVec3i();
        return calcAoeBlocks(facingVec, origin, hit, size, size, depth, -1);
    }

    public static List<BlockPos> calcAoeBlocks(Vec3i facingVec, BlockPos origin,
                                               BlockHitResult hit,
                                               int width, int height,
                                               int depth, int distance) {
        int x, y, z;
        BlockPos start = origin;

        switch (hit.isInside() ? Direction.DOWN : hit.getDirection()) {
            case DOWN, UP -> {
                x = facingVec.getX() * height + facingVec.getZ() * width;
                y = hit.getDirection().getAxisDirection().getStep() * -depth;
                z = facingVec.getX() * width + facingVec.getZ() * height;
                start = start.offset(-x / 2, 0, -z / 2);
                if (x % 2 == 0) {
                    if (x > 0 && hit.getLocation().x - hit.getBlockPos().getX() > 0.5d)
                        start = start.offset(1, 0, 0);
                    else if (x < 0 && hit.getLocation().x - hit.getBlockPos().getX() < 0.5d)
                        start = start.offset(-1, 0, 0);
                }
                if (z % 2 == 0) {
                    if (z > 0 && hit.getLocation().z - hit.getBlockPos().getZ() > 0.5d)
                        start = start.offset(0, 0, 1);
                    else if (z < 0 && hit.getLocation().z - hit.getBlockPos().getZ() < 0.5d)
                        start = start.offset(0, 0, -1);
                }
            }
            case NORTH, SOUTH -> {
                x = width;
                y = height;
                z = hit.getDirection().getAxisDirection().getStep() * -depth;
                start = start.offset(-x / 2, -y / 2, 0);
                if (x % 2 == 0 && hit.getLocation().x - hit.getBlockPos().getX() > 0.5d)
                    start = start.offset(1, 0, 0);
                if (y % 2 == 0 && hit.getLocation().y - hit.getBlockPos().getY() > 0.5d)
                    start = start.offset(0, 1, 0);
            }
            case WEST, EAST -> {
                x = hit.getDirection().getAxisDirection().getStep() * -depth;
                y = height;
                z = width;
                start = start.offset(0, -y / 2, -z / 2);
                if (y % 2 == 0 && hit.getLocation().y - hit.getBlockPos().getY() > 0.5d)
                    start = start.offset(0, 1, 0);
                if (z % 2 == 0 && hit.getLocation().z - hit.getBlockPos().getZ() > 0.5d)
                    start = start.offset(0, 0, 1);
            }
            default -> x = y = z = 0;
        }

        ArrayList<BlockPos> result = new ArrayList<>();

        if (x == 0 || y == 0 || z == 0) {
            result.add(origin);
            return result;
        }

        for (int xp = start.getX(); xp != start.getX() + x; xp += x / Mth.abs(x)) {
            for (int yp = start.getY(); yp != start.getY() + y; yp += y / Mth.abs(y)) {
                for (int zp = start.getZ(); zp != start.getZ() + z; zp += z / Mth.abs(z)) {
                    if (distance > 0 && Mth.abs(xp - origin.getX())
                            + Mth.abs(yp - origin.getY())
                            + Mth.abs(zp - origin.getZ()) > distance) continue;
                    result.add(new BlockPos(xp, yp, zp));
                }
            }
        }

        if (!result.contains(origin)) result.add(origin);
        return result;
    }

    private SpellUtil() {}
}