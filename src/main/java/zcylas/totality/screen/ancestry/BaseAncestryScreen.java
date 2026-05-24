package zcylas.totality.screen.ancestry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public abstract class BaseAncestryScreen extends Screen {

    // ── Colors ────────────────────────────────────────────────────────────────
    protected static final int COLOR_BG            = 0xFF080A0F;
    protected static final int COLOR_PANEL_BG      = 0xFF0D1117;
    protected static final int COLOR_PANEL_BG_ALT  = 0xFF0A0E14;
    protected static final int COLOR_BORDER        = 0xFF8B6914;
    protected static final int COLOR_BORDER_INNER  = 0xFF5C4510;
    protected static final int COLOR_ACCENT        = 0xFF00CCFF;
    protected static final int COLOR_TITLE         = 0xFF00CCFF;
    protected static final int COLOR_LABEL         = 0xFF7A9AAA;
    protected static final int COLOR_VALUE         = 0xFFDDDDDD;
    protected static final int COLOR_SEPARATOR     = 0xFF1E2D36;
    protected static final int COLOR_ROW_SEL       = 0xCC082030;
    protected static final int COLOR_ROW_HOV       = 0x44082030;
    protected static final int COLOR_HEADER_BG     = 0xFF060A0F;
    protected static final int COLOR_COPPER        = 0xFFB87A1A;
    protected static final int COLOR_COPPER_BRIGHT = 0xFFD4A030;
    protected static final int COLOR_COPPER_DIM    = 0xFF7A5010;
    protected static final int COLOR_GREEN         = 0xFF44FF88;
    protected static final int COLOR_BTN_BACK      = 0xFF120800;
    protected static final int COLOR_BTN_BACK_HOV  = 0xFF2A1200;
    protected static final int COLOR_BTN_NEXT      = 0xFF081408;
    protected static final int COLOR_BTN_NEXT_HOV  = 0xFF102410;
    protected static final int COLOR_LOCKED        = 0xFF555555;
    protected static final int COLOR_LOCKED_BG     = 0xFF111111;

    // ── Text scale ────────────────────────────────────────────────────────────
    protected static final float SMALL  = 0.75f;
    protected static final float TINY   = 0.65f;
    protected static final int   SLH    = 9;   // small line height
    protected static final int   TLH    = 8;   // tiny line height
    protected static final int   NLH    = 10;  // normal line height

    // ── Layout — matches ChatGPT render proportions ───────────────────────────
    protected static final int HEADER_H    = 44;
    protected static final int BOTTOM_H    = 38;
    protected static final int CAT_W       = 100; // was 120
    protected static final int LIST_W      = 100; // was 120 // was 150 // center: species/origin list
    protected static final int CORNER_SIZE = 6;
    protected static final int PAD         = 6;
    protected static final int ROW_H       = 22;
    protected static final int HDR_H       = 16; // panel header height

    // ── Derived ───────────────────────────────────────────────────────────────
    protected int W, H;
    protected int listX, detX, detW;
    protected int top, bot;

    protected BaseAncestryScreen(Component title) { super(title); }

    @Override
    protected void init() {
        super.init();
        W    = width;
        H    = height;
        listX = CAT_W;
        detX  = CAT_W + LIST_W;
        detW  = W - CAT_W - LIST_W;
        top  = HEADER_H;
        bot  = H - BOTTOM_H;
    }

    // ── Background ────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, W, H, COLOR_BG);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    protected void drawHeader(GuiGraphicsExtractor g,
                              String title, String sub, String crumb) {
        g.fill(0, 0, W, HEADER_H, COLOR_HEADER_BG);
        g.fill(0, HEADER_H - 1, W, HEADER_H, COLOR_BORDER);

        // Corner decorations
        drawCorner(g, 4, 4, true,  true);
        drawCorner(g, W - 4, 4, false, true);

        int cy = 5;
        // Title — normal size, centered
        g.text(font, Component.literal(title),
                W / 2 - font.width(title) / 2, cy, COLOR_TITLE, true);
        cy += NLH + 2;

        if (sub != null)   { drawSmallC(g, "◆  " + sub + "  ◆", cy, COLOR_COPPER);  cy += SLH + 1; }
        if (crumb != null) { drawSmallC(g, crumb, cy, COLOR_LABEL); }
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    protected void drawBottomBar(GuiGraphicsExtractor g, int mx, int my,
                                 boolean back, boolean next,
                                 boolean canNext, String nextLbl) {
        g.fill(0, bot, W, H, COLOR_HEADER_BG);
        g.fill(0, bot, W, bot + 1, COLOR_BORDER);

        int bh = BOTTOM_H - 8, by = bot + 4, bw = 80;

        if (back) {
            boolean hov = isBack(mx, my);
            drawBtn(g, 10, by, bw, bh, "◀  BACK",
                    COLOR_BTN_BACK, COLOR_BTN_BACK_HOV, COLOR_COPPER, hov);
        }
        if (next) {
            boolean hov = canNext && isNext(mx, my);
            drawBtn(g, W - bw - 10, by, bw, bh, nextLbl + "  ▶",
                    canNext ? COLOR_BTN_NEXT : COLOR_LOCKED_BG,
                    canNext ? COLOR_BTN_NEXT_HOV : COLOR_LOCKED_BG,
                    canNext ? COLOR_GREEN : COLOR_LOCKED, hov);
        }
        // Center diamond
        g.text(font, Component.literal("◆"),
                W / 2 - font.width("◆") / 2,
                by + (bh - 8) / 2, COLOR_COPPER_BRIGHT, false);
    }

    protected boolean isBack(int mx, int my) {
        return inB(mx, my, 10, bot + 4, 80, BOTTOM_H - 8);
    }

    protected boolean isNext(int mx, int my) {
        return inB(mx, my, W - 90, bot + 4, 80, BOTTOM_H - 8);
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    /**
     * Draws a panel with copper border and corner decorations,
     * matching the ChatGPT render style.
     */
    protected void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        // Background
        g.fill(x, y, x + w, y + h, COLOR_PANEL_BG);
        // Outer copper border
        drawCopperBorder(g, x, y, w, h);
        // Inner subtle border
        g.fill(x + 2, y + 2, x + w - 2, y + 3, COLOR_BORDER_INNER);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, COLOR_BORDER_INNER);
        g.fill(x + 2, y + 2, x + 3, y + h - 2, COLOR_BORDER_INNER);
        g.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, COLOR_BORDER_INNER);
        // Corner accents
        drawCorner(g, x + 1, y + 1, true,  true);
        drawCorner(g, x + w - 1, y + 1, false, true);
        drawCorner(g, x + 1, y + h - 1, true,  false);
        drawCorner(g, x + w - 1, y + h - 1, false, false);
    }

    /**
     * Draws a panel header matching the render style:
     * horizontal line with centered label.
     */
    protected void drawPanelHdr(GuiGraphicsExtractor g,
                                int x, int y, int w, String text) {
        g.fill(x + 3, y + 3, x + w - 3, y + HDR_H, COLOR_HEADER_BG);

        int labelW  = Math.round(font.width(text) * SMALL);
        int panelCx = x + w / 2;
        int halfLbl = labelW / 2 + 4;
        int lineY   = y + 3 + (HDR_H - 3) / 2; // true center between y+3 and y+HDR_H

        // Horizontal lines
        g.fill(x + PAD, lineY, panelCx - halfLbl, lineY + 1, COLOR_COPPER_DIM);
        g.fill(panelCx + halfLbl, lineY, x + w - PAD, lineY + 1, COLOR_COPPER_DIM);

        // Text centered on line
        drawSmallAt(g, text, panelCx - labelW / 2, lineY - SLH / 2, COLOR_COPPER);

        // Bottom border line
        g.fill(x + 3, y + HDR_H, x + w - 3, y + HDR_H + 1, COLOR_BORDER);
    }

    // ── Row ───────────────────────────────────────────────────────────────────

    protected void drawRow(GuiGraphicsExtractor g, int x, int y, int w,
                           String name, String tag,
                           boolean sel, boolean hov, boolean locked) {
        if (sel)  g.fill(x + 3, y, x + w - 3, y + ROW_H, COLOR_ROW_SEL);
        else if (hov) g.fill(x + 3, y, x + w - 3, y + ROW_H, COLOR_ROW_HOV);

        if (sel) g.fill(x + 3, y + 1, x + 5, y + ROW_H - 1, COLOR_ACCENT);

        int tx = x + PAD + (sel ? 8 : 4);
        int nameCol = locked ? COLOR_LOCKED : sel ? COLOR_ACCENT : COLOR_VALUE;
        String nameStr = locked ? "🔒 " + name : name;

        if (tag != null) {
            drawSmallAt(g, nameStr, tx, y + 3, nameCol);
            drawTinyAt(g, tag, tx, y + 3 + SLH + 1, COLOR_LABEL);
        } else {
            drawSmallAt(g, nameStr, tx, y + (ROW_H - SLH) / 2, nameCol);
        }

        g.fill(x + PAD, y + ROW_H - 1, x + w - PAD, y + ROW_H, COLOR_SEPARATOR);
    }

    // ── Section label (render style) ──────────────────────────────────────────

    protected int drawSection(GuiGraphicsExtractor g, int x, int y, int w,
                              String label, int labelCol) {
        int labelW = Math.round(font.width(label) * SMALL);
        int cx     = x + w / 2;
        g.fill(x, y + SLH / 2, cx - labelW / 2 - 3, y + SLH / 2 + 1, COLOR_COPPER_DIM);
        g.fill(cx + labelW / 2 + 3, y + SLH / 2, x + w, y + SLH / 2 + 1, COLOR_COPPER_DIM);
        drawSmallAt(g, label, cx - labelW / 2, y, labelCol);
        return y + SLH + 4;
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    protected void drawSmallAt(GuiGraphicsExtractor g, String t, int x, int y, int c) {
        g.pose().pushMatrix();
        g.pose().scale(SMALL, SMALL);
        g.text(font, Component.literal(t),
                Math.round(x / SMALL), Math.round(y / SMALL), c, false);
        g.pose().popMatrix();
    }

    protected void drawTinyAt(GuiGraphicsExtractor g, String t, int x, int y, int c) {
        g.pose().pushMatrix();
        g.pose().scale(TINY, TINY);
        g.text(font, Component.literal(t),
                Math.round(x / TINY), Math.round(y / TINY), c, false);
        g.pose().popMatrix();
    }

    protected void drawSmallC(GuiGraphicsExtractor g, String t, int y, int c) {
        int w = Math.round(font.width(t) * SMALL);
        drawSmallAt(g, t, W / 2 - w / 2, y, c);
    }

    protected int drawSmallWrap(GuiGraphicsExtractor g, String text,
                                int x, int y, int maxW, int col) {
        int scaledMax = Math.round(maxW / SMALL);
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (font.width(test) > scaledMax) {
                drawSmallAt(g, line.toString(), x, y, col);
                y += SLH + 1;
                line = new StringBuilder(word);
            } else line = new StringBuilder(test);
        }
        if (!line.isEmpty()) { drawSmallAt(g, line.toString(), x, y, col); y += SLH + 1; }
        return y;
    }

    // Replace drawBonuses entirely:
    protected int drawBonuses(GuiGraphicsExtractor g, int x, int y, int w,
                              zcylas.totality.api.rpg.stats.AbilityScoreBonus b) {
        boolean twoCol = w > 180;
        int col0 = x, col1 = x + w / 2, col = 0;
        for (var score : zcylas.totality.api.rpg.stats.AbilityScore.values()) {
            int v = bonusVal(b, score);
            if (v == 0) continue;
            int cx = (twoCol && col % 2 == 1) ? col1 : col0;
            int cy = twoCol
                    ? y + (col / 2) * (SLH + 2)
                    : y + col * (SLH + 2);
            drawSmallAt(g, score.getDisplayName() + "  +" + v, cx, cy, COLOR_GREEN);
            col++;
        }
        int rows = twoCol ? (col + 1) / 2 : col;
        return y + rows * (SLH + 2) + 4;
    }

    private int bonusVal(zcylas.totality.api.rpg.stats.AbilityScoreBonus b,
                         zcylas.totality.api.rpg.stats.AbilityScore s) {
        return switch (s) {
            case STR -> b.str(); case DEX -> b.dex();
            case CON -> b.con(); case END -> b.end();
            case INT -> b.intel(); case WIS -> b.wis();
            case CHA -> b.cha(); case FTH -> b.fth();
        };
    }

    // ── Decorative ────────────────────────────────────────────────────────────

    protected void drawCopperBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x,         y,         x + w,     y + 1,     COLOR_BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     COLOR_BORDER);
        g.fill(x,         y,         x + 1,     y + h,     COLOR_BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     COLOR_BORDER);
    }

    protected void drawCorner(GuiGraphicsExtractor g, int x, int y,
                              boolean left, boolean top) {
        int dx = left ? 1 : -1, dy = top ? 1 : -1;
        g.fill(x, y, x + dx * CORNER_SIZE, y + dy, COLOR_COPPER);
        g.fill(x, y, x + dx, y + dy * CORNER_SIZE, COLOR_COPPER);
    }

    protected void drawBtn(GuiGraphicsExtractor g, int x, int y, int w, int h,
                           String lbl, int bg, int bgh, int tc, boolean hov) {
        g.fill(x, y, x + w, y + h, hov ? bgh : bg);
        drawCopperBorder(g, x, y, w, h);
        g.text(font, Component.literal(lbl),
                x + w / 2 - font.width(lbl) / 2,
                y + (h - 8) / 2, tc, false);
    }

    // ── Scissor ───────────────────────────────────────────────────────────────

    protected void sc(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }
    protected void esc(GuiGraphicsExtractor g) { g.disableScissor(); }

    // ── Scroll ────────────────────────────────────────────────────────────────

    protected int scrollCat  = 0;
    protected int scrollList = 0;
    protected int scrollDet  = 0;

    // ── Input ─────────────────────────────────────────────────────────────────

    protected boolean inB(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(e);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}