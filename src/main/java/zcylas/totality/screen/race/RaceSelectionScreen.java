package zcylas.totality.screen.race;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import zcylas.totality.api.rpg.race.Race;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.networking.race.SelectRacePayload;

import java.util.Map;

/**
 * Race selection screen — shown on first world join.
 * Cannot be closed without selecting a race (ESC does nothing).
 *
 * Layout:
 *   Left panel  — race list
 *   Center      — player model preview (follows mouse)
 *   Right panel — race name, description, skill bonuses
 *   Bottom bar  — confirm button (disabled until a race is selected)
 */
public class RaceSelectionScreen extends Screen {

    // ── Colors (exact match with ApothecaryTableScreen) ───────────────────────
    private static final int COLOR_BG          = 0xFF000005;
    private static final int COLOR_BORDER      = 0xFF0A5070;
    private static final int COLOR_BORDER_GLOW = 0x440A8FBF;
    private static final int COLOR_VALUE       = 0xFF00CCFF;
    private static final int COLOR_LABEL       = 0xFF5599BB;
    private static final int COLOR_SEPARATOR   = 0xFF0A3A5A;
    private static final int COLOR_ROW_SEL     = 0xCC002844;
    private static final int COLOR_ROW_HOV     = 0x88001830;
    private static final int COLOR_HEADER_BG   = 0xDD000005;
    private static final int COLOR_BOTTOM      = 0xDD000005;
    private static final int COLOR_BTN         = 0xFF003355;
    private static final int COLOR_BTN_HOVER   = 0xFF0066AA;
    private static final int COLOR_BTN_CRAFT   = 0xFF004422;
    private static final int COLOR_BTN_CRAFT_H = 0xFF006633;
    private static final int COLOR_GREEN       = 0xFF44FF88;

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int HEADER_H     = 24;
    private static final int ROW_H        = 20;
    private static final int BOTTOM_BAR_H = 32;
    private static final int LEFT_W       = 110;
    private static final int RIGHT_W      = 160;
    private static final int COL_GAP      = 8;

    // ── Derived layout (computed in init) ─────────────────────────────────────
    private int guiTop;
    private int guiH;
    private int centerX;   // center of the player preview area
    private int centerY;
    private int rightX;

    // ── State ─────────────────────────────────────────────────────────────────
    private Race selected  = null;
    private Race hovered   = null;

    public RaceSelectionScreen() {
        super(Component.literal("Choose Your Race"));
    }

    @Override
    protected void init() {
        super.init();
        guiH    = (int)(height * 0.80f);
        guiTop  = (height - guiH) / 2;
        rightX  = width - RIGHT_W - 16;
        int leftEdge  = 16 + LEFT_W + COL_GAP;
        int rightEdge = width - RIGHT_W - 16 - COL_GAP;
        centerX = leftEdge + (rightEdge - leftEdge) / 2;
        centerY = guiTop + guiH / 2 - BOTTOM_BAR_H / 2;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, COLOR_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        drawLeftPanel(g, mx, my);
        drawPlayerPreview(g, mx, my);
        drawRightPanel(g, mx, my);
        drawBottomBar(g, mx, my);
    }

    // ── Left panel — race list ────────────────────────────────────────────────

    private void drawLeftPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = 16;
        int y = guiTop;
        int panelH = guiH - BOTTOM_BAR_H;

        g.fill(x, y, x + LEFT_W, y + panelH, 0x55001020);
        drawBorder(g, x, y, LEFT_W, panelH);

        // Header
        String header = "RACE";
        g.fill(x + 1, y + 1, x + LEFT_W - 1, y + HEADER_H, COLOR_HEADER_BG);
        g.fill(x, y + HEADER_H, x + LEFT_W, y + HEADER_H + 1, COLOR_SEPARATOR);
        g.text(font, Component.literal(header),
                x + LEFT_W / 2 - font.width(header) / 2,
                y + (HEADER_H - 8) / 2,
                COLOR_LABEL, false);

        // Race rows
        Race[] races = Race.values();
        for (int i = 0; i < races.length; i++) {
            Race race = races[i];
            int rowY = y + HEADER_H + 1 + i * ROW_H;
            boolean isHov = inBounds(mx, my, x + 1, rowY, LEFT_W - 2, ROW_H);
            boolean isSel = race == selected;

            if (isHov) hovered = race;

            int rowBg = isSel ? COLOR_ROW_SEL : isHov ? COLOR_ROW_HOV : 0;
            if (rowBg != 0)
                g.fill(x + 1, rowY, x + LEFT_W - 1, rowY + ROW_H, rowBg);

            if (isSel) {
                g.text(font, Component.literal("◆"),
                        x + 6, rowY + (ROW_H - 8) / 2, COLOR_VALUE, false);
            }

            g.text(font, Component.literal(race.getDisplayName()),
                    x + (isSel ? 18 : 10), rowY + (ROW_H - 8) / 2,
                    isSel ? COLOR_VALUE : COLOR_LABEL, false);
        }
    }

    // ── Center — player preview ───────────────────────────────────────────────

    private void drawPlayerPreview(GuiGraphicsExtractor g, int mx, int my) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        InventoryScreen.extractEntityInInventoryFollowsMouse(
                g,
                centerX - 40, guiTop + 20,
                centerX + 40, guiTop + guiH - BOTTOM_BAR_H - 20,
                50, 0.0f, mx, my, player
        );
    }

    // ── Right panel — description + bonuses ───────────────────────────────────

    private void drawRightPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = rightX;
        int y = guiTop;
        int panelH = guiH - BOTTOM_BAR_H;

        g.fill(x, y, x + RIGHT_W, y + panelH, 0x55001020);
        drawBorder(g, x, y, RIGHT_W, panelH);

        // Header
        Race display = selected != null ? selected : hovered;
        String header = display != null ? display.getDisplayName().toUpperCase() : "SELECT A RACE";
        g.fill(x + 1, y + 1, x + RIGHT_W - 1, y + HEADER_H, COLOR_HEADER_BG);
        g.fill(x, y + HEADER_H, x + RIGHT_W, y + HEADER_H + 1, COLOR_SEPARATOR);
        g.text(font, Component.literal(header),
                x + RIGHT_W / 2 - font.width(header) / 2,
                y + (HEADER_H - 8) / 2,
                COLOR_VALUE, false);

        if (display == null) {
            String hint = "Hover over a race";
            g.text(font, Component.literal(hint),
                    x + RIGHT_W / 2 - font.width(hint) / 2,
                    y + HEADER_H + 16, COLOR_LABEL, false);
            return;
        }

        int cy = y + HEADER_H + 10;

        // Description — word wrapped
        cy = drawWrappedText(g, display.getDescription(), x + 8, cy, RIGHT_W - 16, COLOR_LABEL);
        cy += 8;

        // Separator
        g.fill(x + 8, cy, x + RIGHT_W - 8, cy + 1, COLOR_SEPARATOR);
        cy += 8;

        // Skill bonuses header
        g.text(font, Component.literal("Starting Bonuses"),
                x + 8, cy, COLOR_LABEL, false);
        cy += 12;

        Map<Skill, Integer> bonuses = display.getSkillBonuses();
        if (bonuses.isEmpty()) {
            g.text(font, Component.literal("  None"), x + 8, cy, COLOR_LABEL, false);
        } else {
            for (Map.Entry<Skill, Integer> entry : bonuses.entrySet()) {
                String line = "  " + entry.getKey().getDisplayName()
                        + "  +" + entry.getValue();
                g.text(font, Component.literal(line), x + 8, cy, COLOR_GREEN, false);
                cy += 11;
            }
        }
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private void drawBottomBar(GuiGraphicsExtractor g, int mx, int my) {
        int barY = guiTop + guiH - BOTTOM_BAR_H;
        g.fill(0, barY, width, barY + BOTTOM_BAR_H, COLOR_BOTTOM);
        g.fill(0, barY, width, barY + 1, COLOR_BORDER);

        int btnH = BOTTOM_BAR_H - 10;
        int btnY = barY + 5;
        int btnW = 80;
        int btnX = width / 2 - btnW / 2;

        boolean canConfirm = selected != null;
        boolean hov = canConfirm && inBounds(mx, my, btnX, btnY, btnW, btnH);

        drawButton(g, btnX, btnY, btnW, btnH, "Confirm",
                canConfirm ? COLOR_BTN_CRAFT : COLOR_BTN,
                canConfirm ? COLOR_BTN_CRAFT_H : COLOR_BTN,
                canConfirm ? COLOR_GREEN : COLOR_LABEL,
                hov);

        // Hint text left side
        String hint = selected != null
                ? "Selected: " + selected.getDisplayName()
                : "Choose your race to begin";
        g.text(font, Component.literal(hint),
                16, btnY + (btnH - 8) / 2, COLOR_LABEL, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x();
        int my = (int) mouse.y();

        // Race list clicks
        int x = 16;
        Race[] races = Race.values();
        for (int i = 0; i < races.length; i++) {
            int rowY = guiTop + HEADER_H + 1 + i * ROW_H;
            if (inBounds(mx, my, x + 1, rowY, LEFT_W - 2, ROW_H)) {
                selected = races[i];
                playClick();
                return true;
            }
        }

        // Confirm button
        if (selected != null) {
            int barY = guiTop + guiH - BOTTOM_BAR_H;
            int btnH = BOTTOM_BAR_H - 10;
            int btnY = barY + 5;
            int btnW = 80;
            int btnX = width / 2 - btnW / 2;
            if (inBounds(mx, my, btnX, btnY, btnW, btnH)) {
                confirmSelection();
                return true;
            }
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        hovered = null;
        int x = 16;
        Race[] races = Race.values();
        for (int i = 0; i < races.length; i++) {
            int rowY = guiTop + HEADER_H + 1 + i * ROW_H;
            if (inBounds((int) mx, (int) my, x + 1, rowY, LEFT_W - 2, ROW_H)) {
                hovered = races[i];
                return;
            }
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Block ESC — player must choose a race
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Confirm ───────────────────────────────────────────────────────────────

    private void confirmSelection() {
        if (selected == null) return;
        ClientPlayNetworking.send(new SelectRacePayload(selected.name()));
        playClick();
        onClose();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Draws word-wrapped text and returns the Y position after the last line.
     */
    private int drawWrappedText(GuiGraphicsExtractor g, String text,
                                int x, int y, int maxWidth, int color) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (font.width(test) > maxWidth) {
                g.text(font, Component.literal(line.toString()), x, y, color, false);
                y += 10;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) {
            g.text(font, Component.literal(line.toString()), x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x,         y,         x + w,     y + 1,     COLOR_BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     COLOR_BORDER);
        g.fill(x,         y,         x + 1,     y + h,     COLOR_BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     COLOR_BORDER);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h,
                            String label, int bgNorm, int bgHov, int textCol,
                            boolean hovered) {
        int bg = hovered ? bgHov : bgNorm;
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x,         y,         x + w, y + 1,     COLOR_BORDER);
        g.fill(x,         y + h - 1, x + w, y + h,     COLOR_BORDER);
        g.fill(x,         y,         x + 1, y + h,     COLOR_BORDER);
        g.fill(x + w - 1, y,         x + w, y + h,     COLOR_BORDER);
        g.text(font, Component.literal(label),
                x + w / 2 - font.width(label) / 2,
                y + (h - 8) / 2, textCol, false);
    }

    private boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean isInGameUi()    { return false; }
    @Override public boolean isPauseScreen() { return false; }
}