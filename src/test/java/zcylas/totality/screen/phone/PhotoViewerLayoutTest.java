package zcylas.totality.screen.phone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Viewer header: the rename field never overlaps the date/metadata line, and both stay inside the top bar. */
class PhotoViewerLayoutTest {

    /** {GUI width, GUI height}: 1920x1012 and 1880x1052 at GUI 4/3/2/1, and 1280x720 at GUI 3/2/1. */
    private static final int[][] WINDOWS = {{480, 253}, {640, 337}, {960, 506}, {1920, 1012},
            {470, 263}, {626, 350}, {940, 526}, {1880, 1052}, {426, 240}, {640, 360}, {1280, 720}};

    @Test
    void renameFieldEndsAboveTheDetailsLine() {
        for (int[] win : WINDOWS) {
            int u = Math.max(1, Math.round(win[1] / 253f));
            PhotoViewerScreen.Layout l = new PhotoViewerScreen.Layout(win[0], win[1], u);
            String at = win[0] + "x" + win[1];
            int fieldBottom = l.nameFieldY() + PhotoViewerScreen.NAME_FIELD_H;
            assertTrue(fieldBottom + 2 <= l.detailsY(), at + ": field ends at " + fieldBottom + ", details start at " + l.detailsY());
            assertTrue(l.detailsY() + 8 * u <= l.barH(), at + ": the details line stays inside the top bar");
            assertTrue(l.nameFieldY() >= 0, at);
        }
    }
}
