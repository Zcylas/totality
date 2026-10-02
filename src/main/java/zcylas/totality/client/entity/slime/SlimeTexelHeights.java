package zcylas.totality.client.entity.slime;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;

import java.util.Arrays;

/**
 * Which texels of the Small Slime texture the body draws, and at what body height (0 at the base, 1 at the dome top),
 * read from the body cuboids' own per-face UVs and vertex positions. Texels no body face uses (the eye region, the
 * transparent texels, unused space) are {@link Float#NaN}. Where faces overlap in UV space: a side-view texel is one
 * height on every face (planar projection); a top-view texel takes the highest top face (the one that is visible) and a
 * bottom-view texel the lowest bottom face.
 */
public final class SlimeTexelHeights {

    private SlimeTexelHeights() {}

    private static volatile float[] baseModel;

    /** Heights for the approved base model ({@link SlimeBaseGeometry}), computed once. */
    public static float[] forBaseModel() {
        float[] h = baseModel;
        if (h == null) {
            ModelPart body = SlimeBaseGeometry.createRoot().getChild("root").getChild("body");
            baseModel = h = fromPart(body, SlimeBaseGeometry.TEXTURE_WIDTH, SlimeBaseGeometry.TEXTURE_HEIGHT);
        }
        return h;
    }

    /** Heights for every face of every cube of {@code part} (and its children), row-major {@code width * height}. */
    public static float[] fromPart(ModelPart part, int width, int height) {
        float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE}; // model y: the top is the smallest value
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            bounds[0] = Math.min(bounds[0], cube.minY);
            bounds[1] = Math.max(bounds[1], cube.maxY);
        });
        float top = bounds[0], bottom = bounds[1];
        float[] out = new float[width * height];
        Arrays.fill(out, Float.NaN);
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            for (ModelPart.Polygon polygon : cube.polygons) {
                ModelPart.Vertex[] v = polygon.vertices();
                boolean flat = v[0].y() == v[1].y() && v[1].y() == v[2].y() && v[2].y() == v[3].y();
                int kind = !flat ? 0 : v[0].y() == cube.minY ? 1 : -1; // 0 side, 1 top face, -1 bottom face
                rasterise(out, width, height, v, kind, top, bottom);
            }
        });
        return out;
    }

    private static void rasterise(float[] out, int width, int height, ModelPart.Vertex[] v, int kind, float top, float bottom) {
        float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, w0 = Float.MAX_VALUE, w1 = -Float.MAX_VALUE;
        for (ModelPart.Vertex x : v) {
            u0 = Math.min(u0, x.u() * width); u1 = Math.max(u1, x.u() * width);
            w0 = Math.min(w0, x.v() * height); w1 = Math.max(w1, x.v() * height);
        }
        // y as an affine function of (u, v) through three corners (faces are axis-aligned rectangles).
        ModelPart.Vertex a = v[0], b = v[1], c = v[2];
        float au = a.u() * width, av = a.v() * height, bu = b.u() * width, bv = b.v() * height, cu = c.u() * width, cv = c.v() * height;
        float det = (bu - au) * (cv - av) - (cu - au) * (bv - av);
        if (det == 0.0F) return; // degenerate (zero-area) face
        for (int ty = (int) Math.floor(w0); ty < Math.ceil(w1); ty++) {
            for (int tx = (int) Math.floor(u0); tx < Math.ceil(u1); tx++) {
                if (tx < 0 || ty < 0 || tx >= width || ty >= height) continue;
                float pu = tx + 0.5F, pv = ty + 0.5F;
                if (pu < u0 || pu > u1 || pv < w0 || pv > w1) continue;
                float s = ((pu - au) * (cv - av) - (cu - au) * (pv - av)) / det;
                float t = ((bu - au) * (pv - av) - (pu - au) * (bv - av)) / det;
                float y = a.y() + s * (b.y() - a.y()) + t * (c.y() - a.y());
                float h = Math.clamp((bottom - y) / (bottom - top), 0.0F, 1.0F);
                int i = ty * width + tx;
                float prev = out[i];
                if (Float.isNaN(prev)) out[i] = h;
                else if (kind == -1) out[i] = Math.min(prev, h);
                else out[i] = Math.max(prev, h);
            }
        }
    }
}
