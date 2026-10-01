package zcylas.totality.screen.phone;

/**
 * Pure layout of the home screen for one device layout and text scale: grid cells, icon positions, the hover/focus
 * frame, the INTERACTIVE bounds of each app (its frame plus its label, nothing else), the favourites dock and the
 * page indicator. Drawing and hit-testing both read this one object, so what is visible is exactly what is
 * clickable; it needs no Minecraft state, so the rules are unit-tested.
 *
 * <p>All values are whole GUI pixels in the un-shifted layout (the open slide-in offset is applied by the caller).
 */
public record PhoneHomeGeometry(PhoneFrameRenderer.Layout device, int statusH, int gridX, int gridY, int cellW,
                                int cellH, int icon, int labelLine, int dotsY,
                                int dockX, int dockY, int dockW, int dockH, int dockGap, int dockCount, int dockIcon) {

    public static final int COLUMNS = 3;
    public static final int ROWS = 4;
    /** The dock is laid out for up to this many favourites; only the ones that exist are shown. */
    public static final int DOCK_CAPACITY = 5;
    /** Space between an icon and its label (below the frame). */
    static final int LABEL_GAP = 3;
    /** Clear pixels between the icon (and its padlock) and the 1 px hover/focus frame. */
    static final int FRAME_CLEARANCE = 1;
    /** Smallest dock gap at which neighbouring hover frames (2 px outside each icon) keep 1 px of empty space. */
    static final int MIN_DOCK_GAP = 5;

    /**
     * @param scale      display text scale (status bar)
     * @param labelLine  line height of the label text (GUI px)
     * @param dockCount  favourites shown (1..{@link #DOCK_CAPACITY})
     */
    public static PhoneHomeGeometry compute(PhoneFrameRenderer.Layout l, float scale, int labelLine, int dockCount) {
        int line = Math.round(9 * scale);
        int statusH = Math.max(PhoneUi.GLYPH_H, line) + 5;
        int cellW = cellWidth(l);
        int icon = Math.max(14, Math.min(26, Math.round(l.dw() * 0.19f))) & ~1;

        // Dock: equal gaps outside and between the icons, the panel centred on the display to the pixel.
        int n = Math.max(1, Math.min(DOCK_CAPACITY, dockCount));
        int margin = 3;
        int dockH = icon + 8;
        int dockY = l.dy() + l.dh() - margin - dockH;
        int dotsY = dockY - 7;
        int gridY = l.dy() + statusH + 4;
        int cellH = (dotsY - 3 - gridY) / ROWS;
        icon = Math.min(icon, Math.min(cellW - 8, cellH - labelLine * 2 - LABEL_GAP - 5)) & ~1;
        // Dock icons match the grid's, stepping down 2 px at a time only where the (final) gaps would be too small for
        // separate hover frames. At every captured window size (1080p and 720p, all GUI scales) they match the grid;
        // the step-down is a safeguard, e.g. for a future fifth favourite on the narrowest phone.
        int dockIcon = icon + 2, gap;
        do {
            dockIcon -= 2;
            // The panel may come within 2 px of the display edge (its own margin is the equal outer gap).
            gap = dockGap(l.dw() - 4, l.dw(), n, dockIcon);
        } while (dockIcon > 12 && gap < MIN_DOCK_GAP);
        dockH = dockIcon + 8;
        dockY = l.dy() + l.dh() - margin - dockH;
        int dockW = n * dockIcon + (n + 1) * gap;
        int dockX = l.dx() + (l.dw() - dockW) / 2;
        int gridX = l.dx() + (l.dw() - cellW * COLUMNS) / 2;
        return new PhoneHomeGeometry(l, statusH, gridX, gridY, cellW, cellH, icon, labelLine, dotsY,
                dockX, dockY, dockW, dockH, gap, n, dockIcon);
    }

    /** Width of one grid column (the space a label may use, minus 2 px). */
    public static int cellWidth(PhoneFrameRenderer.Layout l) {
        return (l.dw() - 2) / COLUMNS;
    }

    /**
     * Equal gaps outside and between {@code n} icons in {@code room} pixels, such that the panel centres exactly in a
     * {@code displayW} display ((display - panel) even). With an even number of gaps (1, 3 or 5 favourites) the panel
     * width is always even, so on an odd-width display that panel is 1 px off centre; four favourites always centre.
     */
    static int dockGap(int room, int displayW, int n, int icon) {
        int gap = Math.max(1, (room - n * icon) / (n + 1));
        if ((n + 1) % 2 == 1) {
            while (gap > 1 && (displayW - (n * icon + (n + 1) * gap)) % 2 != 0) gap--;
        }
        return gap;
    }

    public int statusBottom() {
        return device.dy() + statusH;
    }

    public int gridBottom() {
        return gridY + ROWS * cellH;
    }

    public int cellX(int index) {
        return gridX + (index % COLUMNS) * cellW;
    }

    public int cellY(int index) {
        return gridY + (index / COLUMNS) * cellH;
    }

    /** Top-left of app {@code index}'s icon; the icon, frame and two label lines are centred in the cell. */
    public int[] iconPos(int index) {
        int block = icon + LABEL_GAP + labelLine * 2;
        return new int[] {cellX(index) + (cellW - icon) / 2, cellY(index) + (cellH - block) / 2};
    }

    /** Top of the first label line of an icon at {@code iconY}. */
    public int labelY(int iconY) {
        return iconY + icon + LABEL_GAP;
    }

    /** Size of the padlock glyph ({@code phone/os/lock}), GUI pixels. */
    public static final int PADLOCK_W = 5, PADLOCK_H = 7;

    /**
     * The padlock on a locked icon of {@code size} GUI pixels at ({@code x}, {@code y}): {x, y, w, h}, centred in the
     * tile and always inside it. {@link PhoneUi#appIcon} draws it here.
     */
    public static int[] padlock(int x, int y, int size) {
        return new int[] {x + (size - PADLOCK_W) / 2, y + (size - PADLOCK_H) / 2, PADLOCK_W, PADLOCK_H};
    }

    /**
     * The 1 px hover/focus frame around an icon at ({@code x}, {@code y}), {x, y, w, h}: one clear pixel around the
     * icon tile. Locked and unlocked apps have the same tile, so the same frame.
     */
    public int[] frame(int x, int y) {
        return frame(x, y, icon);
    }

    /** {@link #frame(int, int)} for an icon of {@code size} GUI pixels (the dock's may be smaller). */
    public int[] frame(int x, int y, int size) {
        int d = FRAME_CLEARANCE + 1;
        return new int[] {x - d, y - d, size + 2 * d, size + 2 * d};
    }

    /**
     * Whether a point hits app {@code index}: inside its frame, or on its label (the label's own width, its lines,
     * and the gap between label and frame). Empty cell space never does.
     */
    public boolean hitsApp(int index, int labelWidth, int labelLines, double mx, double my) {
        for (int[] r : appBounds(index, labelWidth, labelLines)) if (in(r, mx, my)) return true;
        return false;
    }

    /** App {@code index}'s interactive rectangles {x, y, w, h}: its frame and (if any) its label block. */
    public int[][] appBounds(int index, int labelWidth, int labelLines) {
        int[] p = iconPos(index);
        int[] frame = frame(p[0], p[1]);
        if (labelLines <= 0) return new int[][] {frame};
        int cx = cellX(index) + cellW / 2;
        int[] label = {cx - labelWidth / 2 - 1, p[1] + icon + FRAME_CLEARANCE + 1, labelWidth + 2,
                LABEL_GAP - FRAME_CLEARANCE - 1 + labelLines * labelLine};
        return new int[][] {frame, label};
    }

    /** Left edge of dock icon {@code i}. */
    public int dockIconX(int i) {
        return dockX + dockGap + i * (dockIcon + dockGap);
    }

    public int dockIconY() {
        return dockY + (dockH - dockIcon) / 2;
    }

    /** Whether a point hits dock icon {@code i} (its frame, as on the grid). */
    public boolean hitsDock(int i, double mx, double my) {
        return in(frame(dockIconX(i), dockIconY(), dockIcon), mx, my);
    }

    static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
