package zcylas.totality.client.entity.slime;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The exported geometry samples the texture as the Blockbench project specifies (approved top/bottom UV fix of
 * export_java.py, 2026-10-02): every cuboid's visible top face reads the painted top view (40,30) and its bottom face
 * the bottom view (80,30, as Blockbench reads the project's "down" rectangles).
 */
class SlimeUvMappingTest {

    private static final int W = 128;

    @Test
    void topAndBottomFacesSampleTheirOwnViews() {
        int[] c = new int[4];
        SlimeBaseGeometry.createRoot().getChild("root").getChild("body").visit(new PoseStack(), (pose, path, i, cube) -> {
            for (int p = 0; p < 2; p++) {
                for (ModelPart.Vertex v : cube.polygons[p].vertices()) {
                    boolean top = v.y() == cube.minY;
                    float xbb = -v.x(), zbb = v.z();
                    float eu = top ? 40 + (xbb + 10) * 2 : 80 + (10 + xbb) * 2;
                    float ev = top ? 30 + (zbb + 10) * 2 : 30 + (10 - zbb) * 2;
                    c[top ? 1 : 3]++;
                    if (Math.abs(v.u() * W - eu) < 1e-3 && Math.abs(v.v() * W - ev) < 1e-3) c[top ? 0 : 2]++;
                }
            }
        });
        assertArrayEquals(new int[]{164, 164, 164, 164}, c, "visual tops / bottoms on their projections");
    }

    @Test
    void polygonZeroIsTheVisualTop() {
        SlimeBaseGeometry.createRoot().getChild("root").getChild("body").visit(new PoseStack(), (pose, path, i, cube) -> {
            for (ModelPart.Vertex v : cube.polygons[0].vertices()) {
                assertEquals(cube.minY, v.y());
                assertTrue(v.u() * W >= 40 && v.u() * W <= 80, "visual top samples the top view");
            }
        });
    }
}
