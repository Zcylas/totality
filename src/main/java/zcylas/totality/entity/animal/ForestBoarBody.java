package zcylas.totality.entity.animal;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.entity.animal.ForestBoarEntity.Behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where the Forest Boar's visible body is, for crosshair targeting and charge contact.
 * <p>
 * The entity's physical bounding box stays a vanilla 0.9 x 1.3 square (collision and navigation), but Astra's model
 * is 1.86 blocks long: the snout, head and rump overhang that box, and a square box turned 45 degrees also covers
 * empty air beside the animal. So targeting uses two boxes that turn with the boar instead — the torso with its
 * legs, and the head (snout, tusks, ears), which also follows the head's yaw, its downward pitch and the lowered
 * poses of grazing, the wind-up and the charge. The numbers are the extents of Astra's cuboids at the render scale
 * (measured with the hybrid's Blockbench kinematics). Both sides compute them from synced state, so the client's
 * crosshair and the server agree.
 */
public final class ForestBoarBody {

    private ForestBoarBody() {}

    /** One box in the boar's frame: forward from {@code back} to {@code front}, +/- {@code halfWidth}, {@code bottom}..{@code top}. */
    public record Region(Vec3 origin, float yawDegrees, double back, double front, double halfWidth, double bottom, double top) {

        private double sin() { return Mth.sin(yawDegrees * Mth.DEG_TO_RAD); }
        private double cos() { return Mth.cos(yawDegrees * Mth.DEG_TO_RAD); }

        /** World point -> (lateral, up, forward) in this region's frame. */
        Vec3 toLocal(Vec3 p) {
            double dx = p.x - origin.x, dz = p.z - origin.z;
            return new Vec3(dx * cos() + dz * sin(), p.y - origin.y, -dx * sin() + dz * cos());
        }

        Vec3 toWorld(Vec3 l) {
            return new Vec3(origin.x + l.z * -sin() + l.x * cos(), origin.y + l.y, origin.z + l.z * cos() + l.x * sin());
        }

        AABB local() {
            return new AABB(-halfWidth, bottom, back, halfWidth, top, front);
        }

        /** The eight corners in world space (for the F3+B outline). */
        public Vec3[] corners() {
            Vec3[] c = new Vec3[8];
            int i = 0;
            for (double y : new double[]{bottom, top}) {
                for (double f : new double[]{back, front}) {
                    for (double x : new double[]{-halfWidth, halfWidth}) c[i++] = toWorld(new Vec3(x, y, f));
                }
            }
            return c;
        }
    }

    // Astra's model at the render scale, in blocks (forward = the way the boar faces).
    /** Torso, rump and legs: rump cap -0.76 to the forelegs' front +0.59, 0.455 either side, ground to the back stripes. */
    static final double BODY_BACK = -0.76, BODY_FRONT = 0.59, BODY_HALF_WIDTH = 0.455, BODY_TOP = 1.27;
    /** The head's pivot, 0.37 forward of the entity's origin (Astra's head group origin). */
    static final double HEAD_PIVOT = 0.37;
    /** Head, snout, tusks and ears around the pivot at rest: 0.33 .. 0.92 forward, 0.36 .. 1.30 up. */
    static final double HEAD_BACK = 0.33 - HEAD_PIVOT, HEAD_FRONT = 0.92 - HEAD_PIVOT, HEAD_HALF_WIDTH = 0.45,
            HEAD_BOTTOM = 0.36, HEAD_TOP = 1.30;
    /** Grazing: the head reaches forward and down to the grass (0.18 .. 1.06 forward, down to 0.02). */
    static final double GRAZE_BACK = 0.18 - HEAD_PIVOT, GRAZE_FRONT = 1.06 - HEAD_PIVOT, GRAZE_BOTTOM = 0.02;
    /** Wind-up and charge: the head is lowered, tusks forward (down to 0.19). */
    static final double LOWERED_BOTTOM = 0.19;
    /** The model turns its head at most this far from the body (ForestBoarModel). */
    static final float MAX_HEAD_YAW = 35.0F;
    /** Looking down lowers the snout (0.55 in front of the pivot) by up to 25 degrees (ForestBoarModel). */
    static final float MAX_HEAD_PITCH_DOWN = 25.0F;

    public static List<Region> regions(ForestBoarEntity boar) {
        return regions(boar.position(), boar.yBodyRot, boar.getYHeadRot(), boar.getXRot(), boar.getBehavior());
    }

    public static List<Region> regions(Vec3 pos, float bodyYaw, float headYaw, float pitch, Behavior behavior) {
        List<Region> out = new ArrayList<>(2);
        out.add(new Region(pos, bodyYaw, BODY_BACK, BODY_FRONT, BODY_HALF_WIDTH, 0.0, BODY_TOP));
        float s = Mth.sin(bodyYaw * Mth.DEG_TO_RAD), c = Mth.cos(bodyYaw * Mth.DEG_TO_RAD);
        Vec3 pivot = pos.add(-s * HEAD_PIVOT, 0.0, c * HEAD_PIVOT);
        float yaw = bodyYaw + Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -MAX_HEAD_YAW, MAX_HEAD_YAW);
        double back = HEAD_BACK, front = HEAD_FRONT;
        double bottom = HEAD_BOTTOM - HEAD_FRONT * Mth.sin(Mth.clamp(pitch, 0.0F, MAX_HEAD_PITCH_DOWN) * Mth.DEG_TO_RAD);
        switch (behavior) {
            case GRAZE -> { back = GRAZE_BACK; front = GRAZE_FRONT; bottom = GRAZE_BOTTOM; }
            case CHARGE_WINDUP, CHARGE -> { back = GRAZE_BACK; bottom = Math.min(bottom, LOWERED_BOTTOM); }
            default -> { }
        }
        out.add(new Region(pivot, yaw, back, front, HEAD_HALF_WIDTH, bottom, HEAD_TOP));
        return out;
    }

    /** The nearest point where the segment from -> to enters the body, if it does. */
    public static Optional<Vec3> clip(List<Region> regions, Vec3 from, Vec3 to) {
        Vec3 best = null;
        for (Region r : regions) {
            Optional<Vec3> hit = r.local().clip(r.toLocal(from), r.toLocal(to));
            if (hit.isPresent()) {
                Vec3 w = r.toWorld(hit.get());
                if (best == null || from.distanceToSqr(w) < from.distanceToSqr(best)) best = w;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean contains(List<Region> regions, Vec3 p) {
        for (Region r : regions) if (r.local().contains(r.toLocal(p))) return true;
        return false;
    }

    /** Whether an axis-aligned box (a player) touches the body, grown by {@code margin} on every side. */
    public static boolean touches(List<Region> regions, AABB box, double margin) {
        for (Region r : regions) {
            if (box.maxY < r.origin().y + r.bottom() - margin || box.minY > r.origin().y + r.top() + margin) continue;
            if (separated(r, box, margin)) continue;
            return true;
        }
        return false;
    }

    /** Separating-axis test in the horizontal plane (the region is only turned about the vertical). */
    private static boolean separated(Region r, AABB box, double margin) {
        double s = Mth.sin(r.yawDegrees() * Mth.DEG_TO_RAD), c = Mth.cos(r.yawDegrees() * Mth.DEG_TO_RAD);
        double[][] rect = new double[4][];
        int i = 0;
        for (double f : new double[]{r.back() - margin, r.front() + margin}) {
            for (double x : new double[]{-r.halfWidth() - margin, r.halfWidth() + margin}) {
                rect[i++] = new double[]{r.origin().x + f * -s + x * c, r.origin().z + f * c + x * s};
            }
        }
        double[][] aabb = {{box.minX, box.minZ}, {box.maxX, box.minZ}, {box.minX, box.maxZ}, {box.maxX, box.maxZ}};
        double[][] axes = {{1, 0}, {0, 1}, {-s, c}, {c, s}};
        for (double[] a : axes) {
            double r0 = Double.MAX_VALUE, r1 = -Double.MAX_VALUE, b0 = Double.MAX_VALUE, b1 = -Double.MAX_VALUE;
            for (double[] p : rect) { double d = p[0] * a[0] + p[1] * a[1]; r0 = Math.min(r0, d); r1 = Math.max(r1, d); }
            for (double[] p : aabb) { double d = p[0] * a[0] + p[1] * a[1]; b0 = Math.min(b0, d); b1 = Math.max(b1, d); }
            if (r1 < b0 || b1 < r0) return true;
        }
        return false;
    }
}
