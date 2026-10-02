package zcylas.totality.client.renderer.ability;

import net.minecraft.world.phys.Vec3;

/**
 * One Heat Vision beam for one frame, in world coordinates.
 *
 * @param start    where the beam leaves the eye
 * @param end      where it stops (hit point, or full range)
 * @param hit      true when it stopped on a block or an entity (an impact hotspot is drawn there)
 * @param strength 0..1 brightness (ignition and fade-out)
 * @param length   0..1 share of the full length drawn (the beam grows from the eye while igniting)
 */
public record HeatVisionBeam(Vec3 start, Vec3 end, boolean hit, float strength, float length) {

    /** The point where the visible beam currently ends. */
    public Vec3 visibleEnd() {
        return length >= 1.0f ? end : start.lerp(end, length);
    }

    /** True when the drawn beam reaches the impact point. */
    public boolean reachesImpact() {
        return hit && length >= 0.999f;
    }
}
