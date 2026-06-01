package zcylas.totality.screen.character;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.ancestry.ClientAncestryManager;
import zcylas.totality.api.rpg.classes.ClientClassManager;
import zcylas.totality.api.rpg.stats.ClientStatsManager;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCharacterScreen extends Screen {

    // ── Colors ────────────────────────────────────────────────────────────────
    public static final int COLOR_BG            = 0xFF060A10;
    public static final int COLOR_PANEL_BG      = 0xFF0A0F18;
    public static final int COLOR_PANEL_BG_ALT  = 0xFF080C14;
    public static final int COLOR_BORDER        = 0xFF1A4A64;
    public static final int COLOR_BORDER_INNER  = 0xFF102A3A;
    public static final int COLOR_BORDER_GLOW   = 0x220A6080;
    public static final int COLOR_ACCENT        = 0xFF00CCFF;
    public static final int COLOR_ACCENT_DIM    = 0x6600CCFF;
    public static final int COLOR_TITLE         = 0xFF00CCFF;
    public static final int COLOR_LABEL         = 0xFF5A8A9A;
    public static final int COLOR_VALUE         = 0xFFDDDDDD;
    public static final int COLOR_SEPARATOR     = 0xFF0F2030;
    public static final int COLOR_ROW_SEL       = 0xCC001828;
    public static final int COLOR_ROW_HOV       = 0x44001828;
    public static final int COLOR_HEADER_BG     = 0xFF050810;
    public static final int COLOR_COPPER        = 0xFFB87A1A;
    public static final int COLOR_COPPER_BRIGHT = 0xFFD4A030;
    public static final int COLOR_COPPER_DIM    = 0xFF7A5010;
    public static final int COLOR_GREEN         = 0xFF44BB44;
    public static final int COLOR_GREEN_BRIGHT  = 0xFF44FF88;
    public static final int COLOR_RED           = 0xFFBB4444;
    public static final int COLOR_LOCKED        = 0xFF555555;
    public static final int COLOR_LOCKED_BG     = 0xFF111111;
    public static final int COLOR_XP_BG         = 0xFF001020;
    public static final int COLOR_XP_FILL       = 0xFF0088CC;
    public static final int COLOR_HP_FILL       = 0xFFCC3333;
    public static final int COLOR_STA_FILL      = 0xFF33AA33;
    public static final int COLOR_MAN_FILL      = 0xFF3355CC;
    public static final int COLOR_TAB_ACTIVE    = 0xDD001828;
    public static final int COLOR_TAB_INACTIVE  = 0x88040810;

    // ── Skill category colors (matching render) ───────────────────────────────
    public static final int COLOR_CAT_COMBAT   = 0xFFCC6633;
    public static final int COLOR_CAT_MAGIC    = 0xFFAA44CC;
    public static final int COLOR_CAT_STEALTH  = 0xFF44AA66;
    public static final int COLOR_CAT_GATHER   = 0xFFBB8833;

    // ── Text scale ────────────────────────────────────────────────────────────
    public static final float SMALL = 0.85f;
    public static final float TINY  = 0.75f;
    public static final int   SLH   = 9;
    public static final int   TLH   = 8;
    public static final int   NLH   = 10;

    // ── Layout ────────────────────────────────────────────────────────────────
    public static final int HEADER_H    = 36;  // title + tab bar
    public static final int BOTTOM_H    = 22;  // hints bar
    public static final int LEFT_W      = 170; // player panel
    public static final int PAD         = 6;
    public static final int CORNER_SIZE = 5;
    public static final int ROW_H       = 18;
    public static final int HDR_H       = 14;  // panel section header
    public static final int BAR_H       = 5;

    // ── Derived (set in init) ─────────────────────────────────────────────────
    public int W, H;
    public int contentX, contentY, contentW, contentH;

    // ── Fade ─────────────────────────────────────────────────────────────────
    public float alpha         = 0f;
    public boolean fadingOut   = false;
    public Runnable onFadeOutDone = null;

    public BaseCharacterScreen(Component title) {
        super(title);
    }

    @Override
    public void init() {
        super.init();
        W        = width;
        H        = height;
        contentX = LEFT_W;
        contentY = HEADER_H;
        contentW = W - LEFT_W;
        contentH = H - HEADER_H - BOTTOM_H;
    }

    // ── Background ────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, W, H, COLOR_BG);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    public void drawHeader(GuiGraphicsExtractor g, String title) {
        g.fill(0, 0, W, HEADER_H, COLOR_HEADER_BG);
        g.fill(0, HEADER_H - 1, W, HEADER_H, COLOR_BORDER);

        drawCorner(g, 4, 4, true,  true);
        drawCorner(g, W - 4, 4, false, true);

        g.text(font, Component.literal(title),
                W / 2 - font.width(title) / 2, 6,
                COLOR_TITLE, true);
    }

    // ── Tab bar ───────────────────────────────────────────────────────────────

    public void drawTabBar(GuiGraphicsExtractor g, CharacterScreen.CharacterTab[] tabs,
                              CharacterScreen.CharacterTab active, int mx, int my) {
        int tabBarY = 18;
        int tabBarH = HEADER_H - tabBarY - 1;
        int tabW = W / tabs.length;
        int tx   = 0;

        // separator dots between tabs
        for (CharacterScreen.CharacterTab tab : tabs) {
            boolean isActive = tab == active;
            int bg  = isActive ? COLOR_TAB_ACTIVE   : COLOR_TAB_INACTIVE;
            int tc  = isActive ? COLOR_ACCENT        : COLOR_LABEL;
            int brc = isActive ? COLOR_ACCENT        : COLOR_BORDER;

            g.fill(tx, tabBarY, tx + tabW, tabBarY + tabBarH, bg);
            // border
            g.fill(tx,          tabBarY,              tx + tabW, tabBarY + 1,          brc);
            g.fill(tx,          tabBarY + tabBarH - 1, tx + tabW, tabBarY + tabBarH,   brc);
            g.fill(tx,          tabBarY,              tx + 1,    tabBarY + tabBarH,    brc);
            g.fill(tx + tabW - 1, tabBarY,            tx + tabW, tabBarY + tabBarH,    brc);

            // active bottom accent line
            if (isActive)
                g.fill(tx + 2, tabBarY + tabBarH - 2, tx + tabW - 2,
                        tabBarY + tabBarH - 1, COLOR_ACCENT);

            String label = tab.label();
            int labelW = Math.round(font.width(label) * SMALL);
            drawSmallAt(g, label,
                    tx + tabW / 2 - labelW / 2,
                    tabBarY + (tabBarH - 8) / 2, tc);

            tx += tabW;
        }
    }

    // ── Left panel ────────────────────────────────────────────────────────────

    public void drawLeftPanel(GuiGraphicsExtractor g, int mx, int my, int lw) {
        int ly = contentY;
        int lh = contentH;
        Player player = Minecraft.getInstance().player;

        // ── Sizing ────────────────────────────────────────────────────────────
        int slotSz     = 18; // vanilla slot size
        int slotGap    = 3;
        int slotColW   = 28; // 2px padding each side
        int sx       = (slotColW - slotSz) / 2; // = 5 // fixed left padding inside column
        int renderBoxH = (int)(lh * 0.62f) - 1; // matches render's player box height ratio, minus 2px padding
        int summaryY   = ly + renderBoxH;
        int summaryH = lh - renderBoxH;

        EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND
        };

        // ── Box 1: Slot column ────────────────────────────────────────────────

        int totalSlotsH = slots.length * slotSz + (slots.length - 1) * slotGap;
        int slotStartY  = ly + (renderBoxH - totalSlotsH) / 2;
        // ── Box 1: Individual slot boxes (no column panel) ────────────────────
        for (int i = 0; i < slots.length; i++) {
            int sy = slotStartY + i * (slotSz + slotGap);
            g.fill(sx, sy, sx + slotSz, sy + slotSz, COLOR_PANEL_BG);
            drawBorder(g, sx, sy, slotSz, slotSz, COLOR_BORDER);
            if (player != null) {
                ItemStack stack = player.getItemBySlot(slots[i]);
                if (!stack.isEmpty())
                    g.item(stack, sx + 1, sy + 1);
            }
        }

        // ── Box 2: Player render ──────────────────────────────────────────────
        int renderX = slotColW + 2;
        int renderW = lw - slotColW - 2;
        drawPanel(g, renderX, ly, renderW, renderBoxH);

        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, renderX + 4, ly + 4,
                    renderX + renderW - 4, ly + renderBoxH - 4,
                    (int)(renderBoxH * 0.32f), 0.0f, mx, my, player);
        }

        // ── Box 3: Summary ────────────────────────────────────────────────────
        drawPanel(g, 0, summaryY, lw, summaryH);

        int level = ClientStatsManager.getLevel();
        String playerName = player != null ? player.getName().getString() : "Unknown";
        String speciesStr = ClientAncestryManager.hasAncestry()
                ? ClientAncestryManager.getSpeciesData().getDisplayName() : "None";
        String originStr  = ClientAncestryManager.getOriginData() != null
                ? ClientAncestryManager.getOriginData().getDisplayName() : "None";

        int cy = summaryY + PAD;
        cy = drawSummaryRow(g, 0, cy, lw, "Name:",    playerName);
        cy = drawSummaryRow(g, 0, cy, lw, "Level:",   String.valueOf(level));
        String classLine = ClientClassManager.getSubclassData() != null
                ? ClientClassManager.getSubclassData().displayName()
                : ClientClassManager.getPrimaryClassData() != null
                ? ClientClassManager.getPrimaryClassData().displayName()
                : "None";

        cy = drawSummaryRow(g, 0, cy, lw, "Class:", classLine);
        cy = drawSummaryRow(g, 0, cy, lw, "Species:", speciesStr);
        cy = drawSummaryRow(g, 0, cy, lw, "Origin:",  originStr);
        drawSummaryRow(g, 0, cy, lw, "Title:", "None");
    }

    private int drawSummaryRow(GuiGraphicsExtractor g, int lx, int cy,
                               int lw, String label, String value) {
        g.text(font, Component.literal(label),
                lx + PAD, cy, COLOR_LABEL, false);
        int maxW = lw - PAD * 2 - font.width(label) - 4;
        String val = value;
        while (font.width(val) > maxW && val.length() > 3)
            val = val.substring(0, val.length() - 1);
        if (!val.equals(value)) val += "..";
        g.text(font, Component.literal(val),
                lx + lw - PAD - font.width(val), cy,
                COLOR_VALUE, false);
        return cy + NLH + 1;
    }

    // ── Bottom hints ──────────────────────────────────────────────────────────

    public void drawBottomHints(GuiGraphicsExtractor g) {
        int barY = H - BOTTOM_H;
        g.fill(0, barY, W, H, COLOR_HEADER_BG);
        g.fill(0, barY, W, barY + 1, COLOR_BORDER);

        int cy = barY + (BOTTOM_H - 8) / 2;
        g.text(font, Component.literal("[TAB] Next Tab"),
                PAD + 4, cy, COLOR_LABEL, false);
        String center = "[SHIFT+TAB] Previous Tab";
        g.text(font, Component.literal(center),
                W / 2 - font.width(center) / 2, cy, COLOR_LABEL, false);
        String right = "[ESC] Close";
        g.text(font, Component.literal(right),
                W - PAD - 4 - font.width(right), cy, COLOR_LABEL, false);
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    public void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COLOR_PANEL_BG);
        drawBorder(g, x, y, w, h, COLOR_BORDER);
        g.fill(x + 2, y + 2, x + w - 2, y + 3,     COLOR_BORDER_INNER);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, COLOR_BORDER_INNER);
        g.fill(x + 2, y + 2, x + 3, y + h - 2,     COLOR_BORDER_INNER);
        g.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, COLOR_BORDER_INNER);
        drawCorner(g, x + 1,     y + 1,     true,  true);
        drawCorner(g, x + w - 1, y + 1,     false, true);
        drawCorner(g, x + 1,     y + h - 1, true,  false);
        drawCorner(g, x + w - 1, y + h - 1, false, false);
    }

    /**
     * Panel section header — horizontal line with centered label,
     * matching the render's "◈── LEVEL ──◈" style.
     */
    public void drawPanelHdr(GuiGraphicsExtractor g,
                                int x, int y, int w, String text) {
        int lineY  = y + HDR_H / 2;
        int labelW = font.width(text);
        int cx     = x + w / 2;
        int half   = labelW / 2 + 6;

        g.fill(x + PAD, lineY, cx - half, lineY + 1, COLOR_COPPER_DIM);
        g.fill(cx + half, lineY, x + w - PAD, lineY + 1, COLOR_COPPER_DIM);

        g.text(font, Component.literal(text),
                cx - labelW / 2, y + (HDR_H - 8) / 2, COLOR_COPPER, false);
    }

    // ── XP / resource bars ───────────────────────────────────────────────────

    public void drawBar(GuiGraphicsExtractor g, int x, int y, int w,
                           float value, float max, int bgColor, int fillColor) {
        g.fill(x, y, x + w, y + BAR_H, bgColor);
        if (max > 0 && value > 0) {
            int fill = (int)(Math.min(value / max, 1f) * w);
            if (fill > 0) g.fill(x, y, x + fill, y + BAR_H, fillColor);
        }
        drawBorder(g, x, y, w, BAR_H, COLOR_BORDER_INNER);
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    public void drawSmallAt(GuiGraphicsExtractor g, String t, int x, int y, int c) {
        g.pose().pushMatrix();
        g.pose().scale(SMALL, SMALL);
        g.text(font, Component.literal(t),
                Math.round(x / SMALL), Math.round(y / SMALL), c, false);
        g.pose().popMatrix();
    }

    public void drawTinyAt(GuiGraphicsExtractor g, String t, int x, int y, int c) {
        g.pose().pushMatrix();
        g.pose().scale(TINY, TINY);
        g.text(font, Component.literal(t),
                Math.round(x / TINY), Math.round(y / TINY), c, false);
        g.pose().popMatrix();
    }

    public int drawWrapped(GuiGraphicsExtractor g, String text,
                              int x, int y, int maxW, int col) {
        List<String> lines = wrapText(text, maxW);
        for (String line : lines) {
            g.text(font, Component.literal(line), x, y, col, false);
            y += NLH;
        }
        return y;
    }

    public int drawWrappedSmall(GuiGraphicsExtractor g, String text,
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

    public List<String> wrapText(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(test) > maxW) {
                if (!cur.isEmpty()) lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else cur = new StringBuilder(test);
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    public String truncate(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 3 && font.width(s + "..") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    // ── Decorative ────────────────────────────────────────────────────────────

    public void drawBorder(GuiGraphicsExtractor g, int x, int y,
                              int w, int h, int color) {
        g.fill(x,         y,         x + w,     y + 1,     color);
        g.fill(x,         y + h - 1, x + w,     y + h,     color);
        g.fill(x,         y,         x + 1,     y + h,     color);
        g.fill(x + w - 1, y,         x + w,     y + h,     color);
    }

    public void drawCorner(GuiGraphicsExtractor g, int x, int y,
                              boolean left, boolean top) {
        int dx = left ? 1 : -1, dy = top ? 1 : -1;
        g.fill(x, y, x + dx * CORNER_SIZE, y + dy,           COLOR_ACCENT);
        g.fill(x, y, x + dx,               y + dy * CORNER_SIZE, COLOR_ACCENT);
    }

    // ── Scissor ───────────────────────────────────────────────────────────────

    public void sc(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }
    public void esc(GuiGraphicsExtractor g) { g.disableScissor(); }

    // ── Fade ─────────────────────────────────────────────────────────────────

    public int tickFade() {
        if (fadingOut) {
            alpha = Math.max(0f, alpha - 0.12f);
            if (alpha <= 0f && onFadeOutDone != null) onFadeOutDone.run();
        } else {
            alpha = Math.min(1f, alpha + 0.12f);
        }
        return (int)(alpha * 255);
    }

    public void fadeOutTo(Runnable onDone) {
        fadingOut    = true;
        onFadeOutDone = onDone;
    }

    // ── Input helpers ─────────────────────────────────────────────────────────

    public boolean inB(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public boolean keyPressed(int key) {
        return false;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}