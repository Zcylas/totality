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
}
