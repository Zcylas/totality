package zcylas.totality.client.vfx.explosion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import zcylas.totality.client.particle.fireball.FireballV2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Apparent shape of Fireball V2's explosion over time (Fireball V2 phase B2.1): how far the dense, hot fire visibly
 * reaches in each direction of the upper hemisphere of a ground hit, and how much of the outer shell it covers. Each
 * hot element is approximated by its dense visible core (about 0.4 of its quad size). Prints a table (the review
 * report's model metrics); the assertions guard the B2.1 refinement.
 */
class FireExplosionShapeTest {

    private static final double R = 6.0;
    private static final Vec3 C = new Vec3(0, 0.25, 0);
    private static final int SEEDS = 12;
    private static final double[] TIMES = {0.05, 0.10, 0.15, 0.20, 0.30, 0.45, 0.60, 0.90};

    /** Directions of the upper hemisphere (Fibonacci lattice, slightly above the ground plane). */
    private static final List<Vec3> DIRS = new ArrayList<>();

    static {
        int n = 600;
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 0.04 + 0.96 * (i + 0.5) / n, s = Math.sqrt(1 - y * y), a = i * golden;
            DIRS.add(new Vec3(s * Math.cos(a), y, s * Math.sin(a)));
        }
    }

    /** Farthest point along {@code d} (from the centre) inside the dense core of a hot element, or 0. */
    private static double reach(Vec3 d, List<FireExplosion.Element> els) {
        double best = 0;
        for (FireExplosion.Element el : els) {
            if (el.heat < 0.3f || el.opacity < 0.3f) continue;
            if (el.kind == FireExplosion.Kind.BODY || el.kind == FireExplosion.Kind.CORE) {
                best = Math.max(best, far(d, new Vec3(el.x, el.y, el.z).subtract(C), 0.4 * el.width * Math.sqrt(el.stretch)));
            } else {
                for (int k = 0; k <= 6; k++) {
                    double f = (0.1 + 0.8 * k / 6.0) * el.length;
                    best = Math.max(best, far(d, new Vec3(el.x + el.ax * f, el.y + el.ay * f, el.z + el.az * f).subtract(C), 0.3 * el.width));
                }
            }
        }
        return best;
    }

    private static double far(Vec3 d, Vec3 c, double rho) {
        double proj = c.dot(d), perp2 = c.lengthSqr() - proj * proj;
        return perp2 < rho * rho ? proj + Math.sqrt(rho * rho - perp2) : 0;
    }

    /** Per time: {median reach, 90th percentile reach, shell coverage at 4.5, 5.0, 5.5}, averaged over seeds. */
    static double[][] metrics() {
        double[][] m = new double[TIMES.length][5];
        for (long seed = 1; seed <= SEEDS; seed++) {
            FireExplosion e = FireExplosion.create(C, new Vec3(0, 1, 0), R, 0.0, seed, FireballV2.STYLE,
                    (from, dir, max) -> max, (x, y, z, depth) -> 0.0, 0);
            for (int ti = 0; ti < TIMES.length; ti++) {
                List<FireExplosion.Element> els = new ArrayList<>();
                e.evaluate(TIMES[ti], els, new FireExplosion.ElementPool());
                double[] r = DIRS.stream().mapToDouble(d -> reach(d, els)).sorted().toArray();
                m[ti][0] += r[r.length / 2] / SEEDS;
                m[ti][1] += r[r.length * 9 / 10] / SEEDS;
                for (int k = 0; k < 3; k++) {
                    double shell = 4.5 + 0.5 * k;
                    m[ti][2 + k] += Arrays.stream(r).filter(v -> v >= shell).count() / (double) r.length / SEEDS;
                }
            }
        }
        return m;
    }

    @Test
    void printApparentShape() {
        double[][] m = metrics();
        StringBuilder sb = new StringBuilder("apparent shape (ground hit, " + SEEDS + " seeds):\n  t(s)  median  p90   cover>=4.5 >=5.0 >=5.5\n");
        for (int i = 0; i < TIMES.length; i++) {
            sb.append(String.format(Locale.ROOT, "  %.2f  %5.2f  %5.2f   %4.0f%%     %4.0f%% %4.0f%%%n", TIMES[i], m[i][0], m[i][1],
                    100 * m[i][2], 100 * m[i][3], 100 * m[i][4]));
        }
        System.out.println(sb);
        // B2 measured: p90 3.72 at 0.10 s; median 4.43 and 34 % of the 5-block shell at 0.30 s.
        assertTrue(m[1][1] >= 4.8, "the flame front is near the boundary by 0.10 s: " + m[1][1]);
        assertTrue(m[4][0] >= 4.9, "the dense body reads close to the 6-block radius at 0.30 s: " + m[4][0]);
        assertTrue(m[4][3] >= 0.45, "the outer front covers the 5-block shell at 0.30 s: " + m[4][3]);
    }
}
