package zcylas.totality.client.vfx.glow;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Collects emissive quads (position + colour) for one frame. Colours are linear light added to the glow layer:
 * brighter colours glow more; alpha scales the contribution.
 */
public final class EmissiveBuffer {

    private static final Matrix4fc IDENTITY = new Matrix4f();

    private final BufferBuilder builder;
    private int quads;

    EmissiveBuffer(BufferBuilder builder) {
        this.builder = builder;
    }

    public void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3,
                     float r, float g, float b, float a) {
        builder.addVertex(IDENTITY, x0, y0, z0).setColor(r, g, b, a);
        builder.addVertex(IDENTITY, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(IDENTITY, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(IDENTITY, x3, y3, z3).setColor(r, g, b, a);
        quads++;
    }

    /** An axis-aligned box centred on ({@code x, y, z}) with half-extents ({@code hx, hy, hz}). */
    public void box(float x, float y, float z, float hx, float hy, float hz, float r, float g, float b, float a) {
        float x0 = x - hx, x1 = x + hx, y0 = y - hy, y1 = y + hy, z0 = z - hz, z1 = z + hz;
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    int quadCount() {
        return quads;
    }
}
