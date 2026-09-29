package zcylas.totality.client.dice;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zcylas.totality.api.client.gui.TotalityGuiRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a {@link D20Geometry} die on a GUI screen: an orthographic projection (die space x right, y up, +z towards the
 * viewer) of its front faces as flat-shaded triangles, their edges, a brighter silhouette, and each face's number laid
 * on the face with the same affine map as the face (so numbers foreshorten and turn with it). A face can be outlined
 * (the result) and cracked (a critical failure).
 */
public final class D20Renderer {

    /** Look of one draw. {@code highlightNumber} 0 = no outlined face. */
    public record Look(float alpha, int edgeRgb, float numberAlpha, int highlightNumber, int highlightRgb,
                       float highlightAlpha, float dim, boolean cracked) {

        public static Look plain(float alpha, float numberAlpha) {
            return new Look(alpha, DiceRollTheme.HIGHLIGHT, numberAlpha, 0, 0, 0, 0, false);
        }
    }

    private static final Vector3f LIGHT = new Vector3f(-0.35f, 0.55f, 0.76f).normalize();
    /** Die units per font pixel: a number is about a third of a face tall. */
    private static final float TEXT_SCALE = 0.043f;
    private static final int DIM_RGB = 0x0C1018;

    private static final List<Vector3f> VERTICES = new ArrayList<>();
    private static final int[][] FACE_VERTICES = new int[20][3];

    static {
        for (int i = 0; i < 20; i++) {
            D20Geometry.Face f = D20Geometry.FACES.get(i);
            Vector3f[] corners = {f.a(), f.b(), f.c()};
            for (int c = 0; c < 3; c++) {
                int index = -1;
                for (int v = 0; v < VERTICES.size(); v++) if (VERTICES.get(v) == corners[c]) index = v;
                if (index < 0) {
                    VERTICES.add(corners[c]);
                    index = VERTICES.size() - 1;
                }
                FACE_VERTICES[i][c] = index;
            }
        }
    }

    private D20Renderer() {}

    public static void draw(GuiGraphicsExtractor g, Font font, Quaternionf q, float cx, float cy, float radius, Look look) {
        float[] sx = new float[VERTICES.size()], sy = new float[VERTICES.size()];
        for (int v = 0; v < VERTICES.size(); v++) {
            Vector3f p = q.transform(new Vector3f(VERTICES.get(v)));
            sx[v] = cx + radius * p.x;
            sy[v] = cy - radius * p.y;
        }
        float[] facing = new float[20];
        Map<Long, Integer> edgeUse = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            D20Geometry.Face f = D20Geometry.FACES.get(i);
            Vector3f n = q.transform(new Vector3f(f.normal()));
            facing[i] = n.z;
            if (n.z <= 0) continue;
            float lit = 0.12f + 0.88f * Math.max(0f, n.dot(LIGHT));
            int face = DiceRollTheme.mix(DiceRollTheme.mix(DiceRollTheme.FACE_DARK, DiceRollTheme.FACE_LIGHT, lit), DIM_RGB, look.dim());
            int centre = DiceRollTheme.mix(face, DiceRollTheme.HIGHLIGHT, 0.10f);
            int[] fv = FACE_VERTICES[i];
            TotalityGuiRenderer.fillPolygon(g, new float[]{sx[fv[0]], sx[fv[1]], sx[fv[2]]}, new float[]{sy[fv[0]], sy[fv[1]], sy[fv[2]]}, 3,
                    DiceRollTheme.argb(look.alpha(), face), DiceRollTheme.argb(look.alpha(), centre));
            for (int e = 0; e < 3; e++) edgeUse.merge(edgeKey(fv[e], fv[(e + 1) % 3]), 1, Integer::sum);
        }
        int edgeRgb = DiceRollTheme.mix(look.edgeRgb(), DIM_RGB, look.dim() * 0.7f);
        for (int i = 0; i < 20; i++) {
            if (facing[i] <= 0) continue;
            int[] fv = FACE_VERTICES[i];
            for (int e = 0; e < 3; e++) {
                int a = fv[e], b = fv[(e + 1) % 3];
                if (edgeUse.get(edgeKey(a, b)) == 2 && a > b) continue; // an inner edge is drawn once
                boolean silhouette = edgeUse.get(edgeKey(a, b)) == 1;
                float alpha = look.alpha() * (silhouette ? 0.95f : 0.30f + 0.45f * facing[i]);
                TotalityGuiRenderer.drawLine(g, sx[a], sy[a], sx[b], sy[b], silhouette ? 1.4f : 0.8f, DiceRollTheme.argb(alpha, edgeRgb));
            }
        }
        if (look.highlightNumber() > 0) {
            int i = look.highlightNumber() - 1;
            if (facing[i] > 0) {
                int[] fv = FACE_VERTICES[i];
                float[] xs = {sx[fv[0]], sx[fv[1]], sx[fv[2]]}, ys = {sy[fv[0]], sy[fv[1]], sy[fv[2]]};
                TotalityGuiRenderer.fillPolygon(g, xs, ys, 3, DiceRollTheme.argb(0.10f * look.highlightAlpha(), look.highlightRgb()),
                        DiceRollTheme.argb(0.28f * look.highlightAlpha(), look.highlightRgb()));
                for (int e = 0; e < 3; e++) {
                    TotalityGuiRenderer.drawLine(g, xs[e], ys[e], xs[(e + 1) % 3], ys[(e + 1) % 3], 1.8f,
                            DiceRollTheme.argb(look.highlightAlpha(), look.highlightRgb()));
                }
                if (look.cracked()) crack(g, xs, ys, look.highlightAlpha());
            }
        }
        for (int i = 0; i < 20; i++) {
            float fade = smooth(0.18f, 0.55f, facing[i]) * look.numberAlpha() * look.alpha();
            if (fade < 0.05f) continue;
            D20Geometry.Face f = D20Geometry.FACES.get(i);
            number(g, font, q, f, cx, cy, radius, fade, look.dim());
        }
    }

    private static void number(GuiGraphicsExtractor g, Font font, Quaternionf q, D20Geometry.Face f, float cx, float cy,
                               float radius, float alpha, float dim) {
        Vector3f r = q.transform(f.right());
        Vector3f u = q.transform(new Vector3f(f.up()));
        Vector3f c = q.transform(new Vector3f(f.centroid()));
        float s = radius * TEXT_SCALE;
        String text = f.number() == 6 || f.number() == 9 ? f.number() + "." : String.valueOf(f.number());
        g.pose().pushMatrix();
        // face-plane text: font x along the face's right, font y (down) along the face's -up; screen y points down
        g.pose().mul(new Matrix3x2f(s * r.x, -s * r.y, -s * u.x, s * u.y, cx + radius * c.x, cy - radius * c.y));
        g.pose().translate(-font.width(text) / 2f, -3.5f);
        int rgb = DiceRollTheme.mix(DiceRollTheme.TITLE, DIM_RGB, dim * 0.6f);
        g.text(font, Component.literal(text), 0, 0, DiceRollTheme.argb(alpha, rgb), false);
        g.pose().popMatrix();
    }

    private static void crack(GuiGraphicsExtractor g, float[] xs, float[] ys, float alpha) {
        float gx = (xs[0] + xs[1] + xs[2]) / 3, gy = (ys[0] + ys[1] + ys[2]) / 3;
        int col = DiceRollTheme.argb(alpha, DiceRollTheme.CRITICAL_FAILURE);
        for (int e = 0; e < 3; e++) {
            float tx = xs[e] * 0.8f + gx * 0.2f, ty = ys[e] * 0.8f + gy * 0.2f;
            float mx = (gx + tx) / 2 + (ty - gy) * 0.18f, my = (gy + ty) / 2 - (tx - gx) * 0.18f;
            TotalityGuiRenderer.drawLine(g, gx, gy, mx, my, 1.2f, col);
            TotalityGuiRenderer.drawLine(g, mx, my, tx, ty, 1.0f, col);
        }
    }

    /** The front faces only, flat: a fading motion trail. */
    public static void ghost(GuiGraphicsExtractor g, Quaternionf q, float cx, float cy, float radius, int rgb, float alpha) {
        for (int i = 0; i < 20; i++) {
            D20Geometry.Face f = D20Geometry.FACES.get(i);
            if (q.transform(new Vector3f(f.normal())).z <= 0) continue;
            float[] xs = new float[3], ys = new float[3];
            Vector3f[] corners = {f.a(), f.b(), f.c()};
            for (int k = 0; k < 3; k++) {
                Vector3f p = q.transform(new Vector3f(corners[k]));
                xs[k] = cx + radius * p.x;
                ys[k] = cy - radius * p.y;
            }
            TotalityGuiRenderer.fillPolygon(g, xs, ys, 3, DiceRollTheme.argb(alpha, rgb), DiceRollTheme.argb(alpha, rgb));
        }
    }

    /** A soft elliptical shadow. */
    public static void shadow(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, float alpha) {
        int n = 20;
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            xs[i] = cx + rx * (float) Math.cos(a);
            ys[i] = cy + ry * (float) Math.sin(a);
        }
        TotalityGuiRenderer.fillPolygon(g, xs, ys, n, DiceRollTheme.argb(0f, 0), DiceRollTheme.argb(alpha, 0x000000));
    }

    private static long edgeKey(int a, int b) {
        return a < b ? (long) a << 32 | b : (long) b << 32 | a;
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
