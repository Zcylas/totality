package zcylas.totality.client.dice;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A regular icosahedron numbered like a real d20 (opposite faces sum to 21), in die space: circumradius 1, centred on
 * the origin. Each face carries an outward normal and an in-plane "up" (towards one of its corners) that orients its
 * number. {@link #settle(int)} is the rotation that turns a face towards the viewer (+Z) with its number upright
 * (+Y); the screen projects die space orthographically (x right, y up), so a face is visible when its rotated normal
 * has z > 0. Pure math: shared by the renderer and the tests.
 */
public final class D20Geometry {

    /** One triangular face: corners counter-clockwise seen from outside. */
    public record Face(int number, Vector3f a, Vector3f b, Vector3f c, Vector3f normal, Vector3f up, Vector3f centroid) {
        /** In-plane right, so that (right, up, normal) is right-handed. */
        public Vector3f right() {
            return new Vector3f(up).cross(normal).normalize();
        }
    }

    public static final List<Face> FACES = build();

    private D20Geometry() {}

    private static List<Face> build() {
        float phi = (float) ((1 + Math.sqrt(5)) / 2);
        List<Vector3f> v = new ArrayList<>();
        for (int s1 : new int[]{-1, 1}) {
            for (int s2 : new int[]{-1, 1}) {
                v.add(new Vector3f(0, s1, s2 * phi));
                v.add(new Vector3f(s1, s2 * phi, 0));
                v.add(new Vector3f(s2 * phi, 0, s1));
            }
        }
        float radius = v.getFirst().length();
        v.forEach(p -> p.div(radius));
        float edge = minDistance(v);

        List<Vector3f[]> tris = new ArrayList<>();
        for (int i = 0; i < 12; i++)
            for (int j = i + 1; j < 12; j++)
                for (int k = j + 1; k < 12; k++)
                    if (near(v.get(i).distance(v.get(j)), edge) && near(v.get(j).distance(v.get(k)), edge)
                            && near(v.get(i).distance(v.get(k)), edge)) {
                        Vector3f a = v.get(i), b = v.get(j), c = v.get(k);
                        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
                        Vector3f g = new Vector3f(a).add(b).add(c).div(3);
                        tris.add(n.dot(g) > 0 ? new Vector3f[]{a, b, c} : new Vector3f[]{a, c, b});
                    }
        if (tris.size() != 20) throw new IllegalStateException("icosahedron has " + tris.size() + " faces");

        // Number the faces: walk them from the top of the die down, giving each unnumbered face the next low number
        // and its opposite face 21 minus it.
        tris.sort(Comparator.<Vector3f[]>comparingDouble(t -> -centroid(t).y).thenComparingDouble(t -> -centroid(t).z)
                .thenComparingDouble(t -> centroid(t).x));
        int[] numbers = new int[20];
        int next = 1;
        for (int i = 0; i < 20; i++) {
            if (numbers[i] != 0) continue;
            numbers[i] = next;
            Vector3f opposite = centroid(tris.get(i)).negate();
            for (int j = 0; j < 20; j++) {
                if (centroid(tris.get(j)).distance(opposite) < 1e-4f) numbers[j] = 21 - next;
            }
            next++;
        }
        List<Face> faces = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Vector3f[] t = tris.get(i);
            Vector3f g = centroid(t);
            Vector3f n = new Vector3f(t[1]).sub(t[0]).cross(new Vector3f(t[2]).sub(t[0])).normalize();
            Vector3f up = new Vector3f(t[0]).sub(g).normalize();
            faces.add(new Face(numbers[i], t[0], t[1], t[2], n, up, g));
        }
        faces.sort(Comparator.comparingInt(Face::number));
        return List.copyOf(faces);
    }

    private static float minDistance(List<Vector3f> v) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i < v.size(); i++)
            for (int j = i + 1; j < v.size(); j++) min = Math.min(min, v.get(i).distance(v.get(j)));
        return min;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static Vector3f centroid(Vector3f[] t) {
        return new Vector3f(t[0]).add(t[1]).add(t[2]).div(3);
    }

    public static Face face(int number) {
        return FACES.get(number - 1);
    }

    /** The rotation that shows face {@code number} to the viewer, number upright. */
    public static Quaternionf settle(int number) {
        Face f = face(number);
        Vector3f r = f.right(), u = f.up(), n = f.normal();
        // rows r, u, n: maps the face frame onto the screen axes (JOML's constructor takes columns)
        Matrix3f m = new Matrix3f(r.x, u.x, n.x, r.y, u.y, n.y, r.z, u.z, n.z);
        return new Quaternionf().setFromNormalized(m).normalize();
    }

    /** The number on the face most directly facing the viewer under {@code orientation}. */
    public static int frontNumber(Quaternionf orientation) {
        Face best = null;
        float bestZ = -2;
        for (Face f : FACES) {
            float z = orientation.transform(new Vector3f(f.normal())).z;
            if (z > bestZ) {
                bestZ = z;
                best = f;
            }
        }
        return best.number();
    }
}
