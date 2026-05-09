package zcylas.totality.screen.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import zcylas.totality.screen.stats.StatusScreen;

/**
 * Character sub-menu — uniform 3x3 grid, smaller than main menu.
 */
public class CharacterMenuScreen extends Screen {

    private static final int COLOR_BORDER       = 0xFF0A5070;
    private static final int COLOR_BORDER_GLOW  = 0x440A8FBF;
    private static final int COLOR_VALUE        = 0xFF00CCFF;
    private static final int COLOR_LABEL        = 0xFF5599BB;
    private static final int COLOR_PANEL_NORMAL = 0xBB001020;
    private static final int COLOR_PANEL_HOVER  = 0xCC002844;
    private static final int COLOR_PANEL_BORDER = 0xFF0A5070;
    private static final int COLOR_PANEL_SEL    = 0xFF00CCFF;
    private static final int COLOR_CENTER_BG    = 0xFF003355;
    private static final int COLOR_CENTER_HOV   = 0xFF0066AA;
    private static final int COLOR_TRIANGLE     = 0xFF00CCFF;

    private static final int CELL_W        = 90;
    private static final int CELL_H        = 42;
    private static final int GAP           = 8;
    private static final int CENTER_W      = 28;
    private static final int CENTER_H      = 28;
    private static final int TRI_GAP       = 5;
    private static final int TRI_SIZE      = 4;
    private static final int DIAMOND_OFFSET = 6;

    private static final String[][] LABELS = {
            { "",      "STATS",  ""      },
            { "TEST1", null,     "TEST2" },
            { "",      "SKILLS", ""      }
    };
    private static final String[][] DESCS = {
            { "", "Ability Scores", "" },
            { "", null,             "" },
            { "", "Coming Soon",    "" }
    };

    private int curRow = 1, curCol = 1;
    private float alpha = 0f;
    private boolean fadingOut = false;
    private Runnable onFadeOutDone = null;
    private int lastMx = -1, lastMy = -1;

    public CharacterMenuScreen() {
        super(Component.literal("Character"));
    }

    @Override
    protected void init() {
        super.init();
        alpha = 0f; fadingOut = false;
        curRow = 1; curCol = 1;
        lastMx = -1; lastMy = -1;
    }

    private int[] slotBounds(int row, int col) {
        int totalW = CELL_W * 3 + GAP * 2;
        int totalH = CELL_H * 3 + GAP * 2;
        int gridX  = width  / 2 - totalW / 2;
        int gridY  = height / 2 - totalH / 2;
        return new int[]{ gridX + col * (CELL_W + GAP), gridY + row * (CELL_H + GAP), CELL_W, CELL_H };
    }

    private int[] drawnBounds(int row, int col) {
        int[] s = slotBounds(row, col);
        if (row == 1 && col == 1) {
            return new int[]{
                    s[0] + (CELL_W - CENTER_W) / 2,
                    s[1] + (CELL_H - CENTER_H) / 2,
                    CENTER_W, CENTER_H
            };
        }
        return s;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, (int)(alpha * 0x88) << 24);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        if (fadingOut) {
            alpha = Math.max(0f, alpha - 0.12f);
            if (alpha <= 0f && onFadeOutDone != null) { onFadeOutDone.run(); return; }
        } else {
            alpha = Math.min(1f, alpha + 0.12f);
        }
        int ba = (int)(alpha * 255);

        // Title above grid
        int[] topSlot = slotBounds(0, 1);
        String title = "CHARACTER";
        g.text(font, Component.literal(title),
                width/2 - font.width(title)/2,
                topSlot[1] - 13,
                withAlpha(COLOR_VALUE, ba), true);

        // Only update hover when mouse actually moves
        if (mx != lastMx || my != lastMy) {
            lastMx = mx; lastMy = my;
            updateHover(mx, my);
        }

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                drawCell(g, row, col, ba);

        drawTriangle(g, ba);
    }

    private void drawCell(GuiGraphicsExtractor g, int row, int col, int ba) {
        boolean isCenter = row == 1 && col == 1;
        String label = isCenter ? "←" : LABELS[row][col];
        if (!isCenter && label == null) return;

        int[] b = drawnBounds(row, col);
        int x = b[0], y = b[1], w = b[2], h = b[3];
        boolean selected = row == curRow && col == curCol;
        boolean isBlank  = !isCenter && label.isEmpty();

        if (selected) { int e = 3; x -= e; y -= e; w += e*2; h += e*2; }

        int bgColor  = withAlpha(isCenter
                ? (selected ? COLOR_CENTER_HOV : COLOR_CENTER_BG)
                : (selected ? COLOR_PANEL_HOVER : COLOR_PANEL_NORMAL), ba);
        int brdColor = withAlpha(selected ? COLOR_PANEL_SEL : COLOR_PANEL_BORDER, ba);

        if (selected) g.fill(x-2, y-2, x+w+2, y+h+2, withAlpha(COLOR_BORDER_GLOW, ba/2));

        g.fill(x, y, x+w, y+h, bgColor);
        g.fill(x,     y,     x+w, y+1,   brdColor);
        g.fill(x,     y+h-1, x+w, y+h,   brdColor);
        g.fill(x,     y,     x+1, y+h,   brdColor);
        g.fill(x+w-1, y,     x+w, y+h,   brdColor);

        int ac = withAlpha(COLOR_VALUE, ba);
        int cs = isCenter ? 3 : 4;
        drawCornerAccent(g, x,   y,   cs, true,  true,  ac);
        drawCornerAccent(g, x+w, y,   cs, false, true,  ac);
        drawCornerAccent(g, x,   y+h, cs, true,  false, ac);
        drawCornerAccent(g, x+w, y+h, cs, false, false, ac);

        if (isCenter) {
            String sym = "←";
            g.text(font, Component.literal(sym),
                    x + w/2 - font.width(sym)/2, y + h/2 - 4,
                    withAlpha(selected ? COLOR_VALUE : COLOR_LABEL, ba), false);
        } else if (!isBlank) {
            int tc = withAlpha(selected ? COLOR_VALUE : COLOR_LABEL, ba);
            String desc = DESCS[row][col];
            g.text(font, Component.literal(label),
                    x + w/2 - font.width(label)/2, y + h/2 - 8, tc, true);
            if (desc != null && !desc.isEmpty())
                g.text(font, Component.literal(desc),
                        x + w/2 - font.width(desc)/2, y + h/2 + 2,
                        withAlpha(COLOR_LABEL, ba), false);
        }
    }

    private void drawTriangle(GuiGraphicsExtractor g, int ba) {
        if (curRow == 1 && curCol == 1) return;
        String label = LABELS[curRow][curCol];
        if (label == null) return;

        int[] cb = drawnBounds(1, 1);
        int cx = cb[0] + cb[2] / 2;
        int cy = cb[1] + cb[3] / 2;
        int color = withAlpha(COLOR_TRIANGLE, ba);
        boolean isCorner = curRow != 1 && curCol != 1;

        if (isCorner) {
            // Diamond at fixed offset from inner corner of selected panel
            int[] pb = drawnBounds(curRow, curCol);
            int icx = (curCol == 0) ? pb[0] + pb[2] : pb[0];
            int icy = (curRow == 0) ? pb[1] + pb[3] : pb[1];
            int dmx = icx + (curCol == 0 ? DIAMOND_OFFSET : -DIAMOND_OFFSET);
            int dmy = icy + (curRow == 0 ? DIAMOND_OFFSET : -DIAMOND_OFFSET);
            g.fill(dmx - 2, dmy,     dmx + 3, dmy + 1, color);
            g.fill(dmx - 1, dmy - 1, dmx + 2, dmy + 2, color);
            g.fill(dmx,     dmy - 2, dmx + 1, dmy + 3, color);
        } else if (curRow == 0) {
            // TOP — base near center (bottom), tip far from center (top)
            // baseY is just above center top edge
            int baseY = cb[1] - TRI_GAP;
            // i=0: full width at base (near center)
            // i=TRI_SIZE-1: 1 pixel at tip (far from center)
            for (int i = 0; i < TRI_SIZE; i++)
                g.fill(cx - (TRI_SIZE-1-i), baseY - i,
                        cx + (TRI_SIZE-1-i) + 1, baseY - i + 1, color);
        } else if (curRow == 2) {
            int baseY = cb[1] + cb[3] + TRI_GAP;
            for (int i = 0; i < TRI_SIZE; i++)
                g.fill(cx - (TRI_SIZE-1-i), baseY + i,
                        cx + (TRI_SIZE-1-i) + 1, baseY + i + 1, color);
        } else if (curCol == 0) {
            int baseX = cb[0] - TRI_GAP;
            for (int i = 0; i < TRI_SIZE; i++)
                g.fill(baseX - i, cy - (TRI_SIZE-1-i),
                        baseX - i + 1, cy + (TRI_SIZE-1-i) + 1, color);
        } else if (curCol == 2) {
            int baseX = cb[0] + cb[2] + TRI_GAP;
            for (int i = 0; i < TRI_SIZE; i++)
                g.fill(baseX + i, cy - (TRI_SIZE-1-i),
                        baseX + i + 1, cy + (TRI_SIZE-1-i) + 1, color);
        }
    }

    private void updateHover(int mx, int my) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                boolean isCenter = row == 1 && col == 1;
                if (!isCenter && LABELS[row][col] == null) continue;
                int[] s = slotBounds(row, col);
                if (inBounds(mx, my, s[0], s[1], s[2], s[3])) {
                    if (row != curRow || col != curCol) {
                        curRow = row; curCol = col; playClick();
                    }
                    return;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        enterCurrent();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        int nr = curRow, nc = curCol;
        boolean moved = false;

        if      (key == org.lwjgl.glfw.GLFW.GLFW_KEY_W) { nr = Math.max(0, curRow - 1); moved = true; }
        else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) { nr = Math.min(2, curRow + 1); moved = true; }
        else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_A) { nc = Math.max(0, curCol - 1); moved = true; }
        else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_D) { nc = Math.min(2, curCol + 1); moved = true; }
        else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_E) { enterCurrent(); return true; }
        else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            goBack(); return true;
        }

        if (moved) {
            boolean isCenter = nr == 1 && nc == 1;
            boolean valid = isCenter || LABELS[nr][nc] != null;
            if (valid && (nr != curRow || nc != curCol)) {
                curRow = nr; curCol = nc; playClick();
            }
            return true;
        }

        return super.keyPressed(event);
    }

    private void enterCurrent() {
        playClick();
        if (curRow == 1 && curCol == 1) { goBack(); return; }
        String label = LABELS[curRow][curCol];
        if (label == null || label.isEmpty()) return;
        switch (label) {
            case "STATS"  -> fadeOutTo(() -> Minecraft.getInstance().setScreen(new StatusScreen()));
            case "SKILLS" -> fadeOutTo(() -> Minecraft.getInstance().setScreen(new SkillsMenuScreen()));
            case "TEST1", "TEST2" -> { /* TODO */ }
        }
    }

    private void goBack()                  { fadeOutTo(() -> Minecraft.getInstance().setScreen(new MainMenuScreen())); }
    private void fadeOutTo(Runnable onDone){ fadingOut = true; onFadeOutDone = onDone; }

    private boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }

    private void drawCornerAccent(GuiGraphicsExtractor g, int x, int y,
                                  int size, boolean left, boolean top, int color) {
        int dx = left ? 1 : -1, dy = top ? 1 : -1;
        g.fill(x, y, x+dx*size, y+dy, color);
        g.fill(x, y, x+dx, y+dy*size, color);
    }

    private int withAlpha(int color, int alpha) {
        return ((((color >> 24) & 0xFF) * alpha / 255) << 24) | (color & 0x00FFFFFF);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean isInGameUi()    { return false; }
    @Override public boolean isPauseScreen() { return false; }
}