package zcylas.totality.client.vfx.glow;

import net.minecraft.world.phys.Vec3;

/**
 * Something that contributes light to the shared glow layer. Sources draw only their <em>emissive</em> part here; their
 * ordinary appearance is rendered by their own renderer as usual, so turning the glow off leaves them unchanged.
 */
public interface EmissiveSource {

    /**
     * Adds this frame's emissive geometry, in camera-relative coordinates ({@code world - camera}). Called on the render
     * thread, only while the glow layer is active.
     */
    void emit(EmissiveBuffer buffer, Vec3 camera, float partialTick);

    /**
     * How much light this source is about to add this frame, in "full effects" (e.g. one Fireball explosion at its
     * brightest = 1), for the layer's brightness budget ({@link EmissiveBudget}). Called once per frame before
     * {@link #emit}. 0 (the default) = not budgeted.
     */
    default float emissiveDemand() {
        return 0.0f;
    }

    /** The budget group this source's demand counts toward (null = none; only the global limit applies). */
    default String budgetGroup() {
        return null;
    }
}
