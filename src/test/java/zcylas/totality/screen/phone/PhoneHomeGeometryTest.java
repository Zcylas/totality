package zcylas.totality.screen.phone;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 revision regression: app hit areas are the VISIBLE bounds (icon frame + label), never empty cell space;
 * the dock is symmetric to the pixel; the padlock is centred inside the tile and every app (locked or not) has the
 * same frame. Checked for every window/GUI-scale combination the captures cover, with worst-case (full-width,
 * two-line) labels.
 */
class PhoneHomeGeometryTest {

    /** {GUI width, GUI height, GUI scale}: 1920x1012 at GUI 4/3/2/1 and 1280x720 at GUI 3/2/1, plus 1080p proper. */
    private static final int[][] WINDOWS = {
            {480, 253, 4}, {640, 337, 3}, {960, 506, 2}, {1920, 1012, 1},
            {426, 240, 3}, {640, 360, 2}, {1280, 720, 1}, {480, 270, 4}, {640, 360, 3}};

    private record Case(String name, PhoneHomeGeometry geo) {}

    private static List<Case> cases(int dockCount) {
        List<Case> out = new ArrayList<>();
        for (int[] w : WINDOWS) {
            PhoneFrameRenderer.Layout l = PhoneFrameRenderer.layout(w[0], w[1], PhoneDeviceStyle.CRUDE);
            float scale = PhoneUi.displayScale(l, w[2]);
            // Every crisp label scale up to the display scale (k / guiScale).
            for (int k = 1; k <= w[2]; k++) {
                float labelScale = k / (float) w[2];
                if (labelScale > scale) break;
                int line = Math.round(9 * labelScale);
                out.add(new Case(w[0] + "x" + w[1] + "@" + w[2] + " label " + labelScale,
                        PhoneHomeGeometry.compute(l, scale, line, dockCount)));
            }
        }
        return out;
    }

    /** The worst case: every app with a two-line label as wide as its column allows. */
    private static int hit(PhoneHomeGeometry g, double mx, double my) {
        for (int i = 0; i < 12; i++) if (g.hitsApp(i, g.cellW() - 2, 2, mx, my)) return i;
        return -1;
    }

    @Test
    void iconsAndLabelsHitTheirOwnApp() {
        for (Case c : cases(4)) {
            PhoneHomeGeometry g = c.geo();
            for (int i = 0; i < 12; i++) {
                int[] p = g.iconPos(i);
                assertEquals(i, hit(g, p[0] + g.icon() / 2.0, p[1] + g.icon() / 2.0), c.name() + " icon " + i);
                assertEquals(i, hit(g, p[0] + 0.5, p[1] + 0.5), c.name() + " icon corner " + i);
                assertEquals(i, hit(g, g.cellX(i) + g.cellW() / 2.0, g.labelY(p[1]) + 1.5), c.name() + " label " + i);
            }
        }
    }

    @Test
    void emptySpaceBetweenAppsNeverActivatesOne() {
        for (Case c : cases(4)) {
            PhoneHomeGeometry g = c.geo();
            for (int i = 0; i < 12; i++) {
                int col = i % 3, row = i / 3;
                int[] p = g.iconPos(i);
                double iconMidY = p[1] + g.icon() / 2.0;
                // Beside the icon, in the gap toward the next column (the Codex|Map, Inventory-edge cases).
                int[] frame = g.frame(p[0], p[1]);
                double rightOfFrame = frame[0] + frame[2] + 0.5;
                double nextCell = g.cellX(i) + g.cellW() + 0.5;
                if (col < 2) {
                    int[] next = g.iconPos(i + 1);
                    double between = (rightOfFrame + g.frame(next[0], next[1])[0]) / 2;
                    assertEquals(-1, hit(g, between, iconMidY), c.name() + " between " + i + " and " + (i + 1));
                } else {
                    assertTrue(rightOfFrame < nextCell, c.name());
                    assertEquals(-1, hit(g, rightOfFrame, iconMidY), c.name() + " right of " + i);
                }
                // Left of the icon, in its own cell.
                assertEquals(-1, hit(g, frame[0] - 0.5, iconMidY), c.name() + " left of " + i);
                // Below the label, above the next row's frame.
                if (row < 3) {
                    double labelBottom = g.labelY(p[1]) + 2 * g.labelLine();
                    double nextTop = g.iconPos(i + 3)[1] - 2;
                    assertTrue(labelBottom < nextTop, c.name() + " rows separated at " + i);
                    assertEquals(-1, hit(g, p[0] + g.icon() / 2.0, (labelBottom + nextTop) / 2), c.name() + " below " + i);
                }
                // The four-way corner between this app, the next column and the next row (Codex/Map/Spells/Abilities).
                if (col < 2 && row < 3) {
                    double x = g.cellX(i) + g.cellW(), y = g.cellY(i) + g.cellH();
                    assertEquals(-1, hit(g, x, y + 0.5), c.name() + " corner after " + i);
                }
            }
        }
    }

    @Test
    void appBoundsNeverOverlap() {
        for (Case c : cases(4)) {
            PhoneHomeGeometry g = c.geo();
            for (int a = 0; a < 12; a++) {
                for (int b = a + 1; b < 12; b++) {
                    for (int[] ra : g.appBounds(a, g.cellW() - 2, 2)) {
                        for (int[] rb : g.appBounds(b, g.cellW() - 2, 2)) {
                            boolean overlap = ra[0] < rb[0] + rb[2] && rb[0] < ra[0] + ra[2]
                                    && ra[1] < rb[1] + rb[3] && rb[1] < ra[1] + ra[3];
                            assertFalse(overlap, c.name() + " apps " + a + " and " + b);
                        }
                    }
                }
            }
        }
    }

    @Test
    void dockIsSymmetricWithEqualGaps() {
        for (int n = 1; n <= PhoneHomeGeometry.DOCK_CAPACITY; n++) {
            for (Case c : cases(n)) {
                PhoneHomeGeometry g = c.geo();
                PhoneFrameRenderer.Layout d = g.device();
                String at = c.name() + " dock of " + n;
                int left = g.dockIconX(0) - g.dockX();
                int di = g.dockIcon();
                int right = g.dockX() + g.dockW() - (g.dockIconX(n - 1) + di);
                assertEquals(left, right, at + ": outer padding");
                for (int i = 1; i < n; i++) {
                    assertEquals(left, g.dockIconX(i) - (g.dockIconX(i - 1) + di), at + ": gap " + i);
                    int[] a = g.frame(g.dockIconX(i - 1), g.dockIconY(), di), b = g.frame(g.dockIconX(i), g.dockIconY(), di);
                    assertTrue(a[0] + a[2] < b[0], at + ": hover frames " + (i - 1) + "/" + i + " keep empty space between them");
                }
                // The current four favourites match the grid's icons or are at most 4 px smaller; a future fifth may need
                // smaller icons on the narrowest phones, but never below 12 px.
                assertTrue(di <= g.icon() && di >= (n <= 4 ? g.icon() - 4 : 12), at + ": dock icon size " + di + " vs grid " + g.icon());
                int offCentre = Math.abs((g.dockX() - d.dx()) - (d.dx() + d.dw() - (g.dockX() + g.dockW())));
                // Exact for the current four favourites (an odd number of gaps); an even number of gaps can't centre
                // an even-width panel in an odd-width display, so it may be 1 px off.
                assertTrue(offCentre <= ((n + 1) % 2 == 1 ? 0 : 1), at + ": centred (off by " + offCentre + ")");
                assertTrue(g.dockX() >= d.dx() && g.dockX() + g.dockW() <= d.dx() + d.dw(), at + ": inside the display");
                assertTrue(left >= 2, at + ": room for the hover frame");
                for (int i = 0; i < n; i++) {
                    assertEquals(i, dockHit(g, n, g.dockIconX(i) + di / 2.0, g.dockIconY() + di / 2.0), at);
                    if (i + 1 < n) {
                        double between = (g.dockIconX(i) + di + g.dockIconX(i + 1)) / 2.0;
                        assertEquals(-1, dockHit(g, n, between, g.dockIconY() + di / 2.0), at + " between");
                    }
                }
            }
        }
    }

    private static int dockHit(PhoneHomeGeometry g, int n, double mx, double my) {
        for (int i = 0; i < n; i++) if (g.hitsDock(i, mx, my)) return i;
        return -1;
    }

    @Test
    void padlockIsCentredInsideTheTileAndFramesNeverGrow() {
        for (Case c : cases(4)) {
            PhoneHomeGeometry g = c.geo();
            for (int i = 0; i < 12; i++) {
                int[] p = g.iconPos(i);
                int x = p[0], y = p[1], s = g.icon();
                int[] lock = PhoneHomeGeometry.padlock(x, y, s);
                assertTrue(lock[0] >= x + 1 && lock[1] >= y + 1 && lock[0] + lock[2] <= x + s - 1 && lock[1] + lock[3] <= y + s - 1,
                        c.name() + ": padlock inside the tile (clear of its 1 px edge)");
                int left = lock[0] - x, right = x + s - (lock[0] + lock[2]);
                int top = lock[1] - y, bottom = y + s - (lock[1] + lock[3]);
                assertTrue(Math.abs(left - right) <= 1 && Math.abs(top - bottom) <= 1, c.name() + ": padlock centred");
                // One frame for every app: 1 px line, 1 px clearance around the tile — locked or not.
                assertArrayEquals(new int[] {x - 2, y - 2, s + 4, s + 4}, g.frame(x, y), c.name() + ": frame " + i);
                assertEquals(g.frame(x, y)[0], g.appBounds(i, 10, 1)[0][0], c.name() + ": hit frame = drawn frame");
                assertEquals(g.frame(x, y)[2], g.appBounds(i, 10, 1)[0][2], c.name() + ": hit frame = drawn frame");
            }
            // The dock's padlock rule is the same (its tiles may be smaller).
            int[] dl = PhoneHomeGeometry.padlock(g.dockIconX(0), g.dockIconY(), g.dockIcon());
            assertTrue(dl[0] >= g.dockIconX(0) + 1 && dl[0] + dl[2] <= g.dockIconX(0) + g.dockIcon() - 1, c.name() + ": dock padlock inside");
        }
    }
}
