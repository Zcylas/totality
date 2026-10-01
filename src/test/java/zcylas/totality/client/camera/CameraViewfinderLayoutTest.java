package zcylas.totality.client.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Viewfinder controls stay on screen, never overlap, and hit-test to themselves at every captured window size. */
class CameraViewfinderLayoutTest {

    /** {GUI width, GUI height}: 1920x1012 at GUI 4/3/2/1 and 1280x720 at GUI 3/2/1. */
    private static final int[][] WINDOWS = {{480, 253}, {640, 337}, {960, 506}, {1920, 1012}, {426, 240}, {640, 360}, {1280, 720}};

    @Test
    void everyControlHitsItselfAndStaysInside() {
        for (int[] win : WINDOWS) {
            CameraViewfinder.Layout l = CameraViewfinder.layout(win[0], win[1]);
            String at = win[0] + "x" + win[1];
            assertEquals(CameraViewfinder.Control.SHUTTER, l.at(l.shutterX(), l.shutterY(), CameraMode.NORMAL), at);
            assertEquals(CameraViewfinder.Control.GALLERY, l.at(l.thumbX() + l.thumbS() / 2.0, l.thumbY() + l.thumbS() / 2.0, CameraMode.NORMAL), at);
            assertEquals(CameraViewfinder.Control.BACK, l.at(l.backX() + 2, l.backY() + 2, CameraMode.NORMAL), at);
            int n = CameraZoom.BASIC.presets().size();
            for (int i = 0; i < n; i++) {
                assertEquals(CameraViewfinder.Control.values()[CameraViewfinder.Control.ZOOM_0.ordinal() + i],
                        l.at(l.chipX(i, n) + l.chipW() / 2.0, l.chipY() + l.chipH() / 2.0, CameraMode.NORMAL), at + " chip " + i);
            }
            assertEquals(CameraViewfinder.Control.MODE_NORMAL, l.at(l.modeX(0, 0), l.modeY() + 4 * l.u(), CameraMode.NORMAL), at);
            assertEquals(CameraViewfinder.Control.MODE_SCAN, l.at(l.modeX(1, 0), l.modeY() + 4 * l.u(), CameraMode.NORMAL), at);
            assertEquals(CameraViewfinder.Control.NONE, l.at(win[0] / 2.0, win[1] / 3.0, CameraMode.NORMAL), at + ": the view itself is no control");
            assertTrue(l.shutterY() + l.shutterR() < win[1] && l.thumbX() > 0 && l.chipY() > l.backY() + l.backH(), at);
            assertTrue(l.thumbX() + l.thumbS() < l.shutterX() - l.shutterR(), at + ": shortcut clear of the shutter");
            assertTrue(l.chipX(n - 1, n) + l.chipW() < win[0], at);
        }
    }

    @Test
    void scanPlaceholderAndModeSelectorKeepClearOfTheZoomControls() {
        for (int[] win : WINDOWS) {
            CameraViewfinder.Layout l = CameraViewfinder.layout(win[0], win[1]);
            String at = win[0] + "x" + win[1];
            int u = l.u();
            assertTrue(l.scanBottom() + 4 * u <= l.chipPillTop(),
                    at + ": Scan panel/brackets end " + (l.chipPillTop() - l.scanBottom()) + " px above the zoom pill");
            assertTrue(l.scanTop() >= l.backY() + l.backH() + 2 * u, at + ": and below the top hint");
            assertTrue(l.chipPillTop() + l.chipH() + 4 * u + 2 * u <= l.modeY(),
                    at + ": the mode labels sit clearly below the zoom pill");
            assertTrue(l.modeY() + 12 * u <= l.shutterY() - l.shutterR(), at + ": and above the shutter");
        }
    }

    @Test
    void controlsKeepTheirPhysicalSizeAcrossGuiScales() {
        // 1012-px window: GUI 4 (253) .. GUI 1 (1012). Screen pixels per unit stay at 3-4.
        int[][] scales = {{253, 4}, {337, 3}, {506, 2}, {1012, 1}};
        for (int[] s : scales) {
            int u = CameraViewfinder.layout(s[0] * 16 / 9, s[0]).u();
            int screenPxPerUnit = u * s[1];
            assertTrue(screenPxPerUnit >= 3 && screenPxPerUnit <= 4, "GUI " + s[1] + ": " + screenPxPerUnit + " px per unit");
        }
    }
}
