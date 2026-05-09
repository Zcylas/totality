package zcylas.totality.screen.stats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.ClientStatsManager;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.stamina.ClientStaminaManager;
import zcylas.totality.networking.stats.SpendAttributePointPayload;

/**
 * Solo Leveling inspired STATUS screen.
 * TODO: Remove /totality stats command and open via TAB radial menu when main menu is implemented.
 */
public class StatusScreen extends Screen {

    private static final int COLOR_BG           = 0xCC000814;
    private static final int COLOR_BORDER       = 0xFF0A5070;
    private static final int COLOR_BORDER_GLOW  = 0x440A8FBF;
    private static final int COLOR_HEADER_BG    = 0xDD000C1A;
    private static final int COLOR_TITLE        = 0xFFCCEEFF;
    private static final int COLOR_LABEL        = 0xFF5599BB;
    private static final int COLOR_VALUE        = 0xFF00CCFF;
    private static final int COLOR_MODIFIER_POS = 0xFF44FFAA;
    private static final int COLOR_MODIFIER_NEG = 0xFFFF4444;
    private static final int COLOR_MODIFIER_NEU = 0xFF888888;
    private static final int COLOR_XP_BAR_BG   = 0xFF002244;
    private static final int COLOR_XP_BAR_FILL = 0xFF0088CC;
    private static final int COLOR_XP_BORDER   = 0xFF0A4060;
    private static final int COLOR_HP_BG        = 0xFF3A0000;
    private static final int COLOR_HP_FILL      = 0xFFDD3333;
    private static final int COLOR_STA_BG       = 0xFF003A00;
    private static final int COLOR_STA_FILL     = 0xFF33BB33;
    private static final int COLOR_MAN_BG       = 0xFF00003A;
    private static final int COLOR_MAN_FILL     = 0xFF3355DD;
    private static final int COLOR_BTN          = 0xFF003355;
    private static final int COLOR_BTN_HOVER    = 0xFF0066AA;
    private static final int COLOR_BTN_TEXT     = 0xFF00CCFF;
    private static final int COLOR_SEPARATOR    = 0xFF0A3A5A;
    private static final int COLOR_BAR_BORDER   = 0xFF0A4060;

    // SL bar dimensions — icon + bar + number below
    private static final int BAR_H       = 7;
    private static final int SL_BAR_H    = BAR_H + 12; // bar + number below
    private static final int PANEL_W     = 290;
    private static final int PADDING     = 12;
    private static final int STAT_ROW_H  = 17;
    private static final int BTN_SIZE    = 11;

    // Fixed stat column offsets
    private static final int STAT_EMOJI_W = 14;
    private static final int STAT_NAME_W  = 22;
    private static final int STAT_VAL_W   = 22;

    private int panelX, panelY, panelH;
    private AbilityScore hoveredScore = null;

    private static String getStatEmoji(AbilityScore score) {
        return switch (score) {
            case STR -> "⚔";  case DEX -> "🏹";
            case CON -> "❤";  case END -> "⚡";
            case INT -> "✨";  case WIS -> "👁";
            case CHA -> "👑";  case FTH -> "🙏";
        };
    }

    public StatusScreen() {
        super(Component.literal("Status"));
    }

    @Override
    protected void init() {
        super.init();
        // Dynamic height:
        // title(16) + sep(7) + header(44) + gap(4) + sep(6) +
        // 3 SL bars (each SL_BAR_H + 8 gap) + sep(6) +
        // 4 stat rows + sep(5) + close(11) + padding*2
        panelH = 16 + 7 + 44 + 4 + 6
                + 3 * (SL_BAR_H + 8)
                + 6
                + 4 * STAT_ROW_H
                + 5 + 11
                + PADDING * 2
                - 70; // trim unused bottom space
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - panelH) / 2; // perfectly centered
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        hoveredScore = null;
        drawPanel(g, mx, my);
    }

    private void drawPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = panelX, y = panelY, w = PANEL_W, h = panelH;

        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, COLOR_BORDER_GLOW);
        g.fill(x, y, x + w, y + h, COLOR_BG);
        g.fill(x,         y,         x + w,     y + 1,     COLOR_BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     COLOR_BORDER);
        g.fill(x,         y,         x + 1,     y + h,     COLOR_BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     COLOR_BORDER);
        drawCornerAccent(g, x,     y,     6, true,  true);
        drawCornerAccent(g, x + w, y,     6, false, true);
        drawCornerAccent(g, x,     y + h, 6, true,  false);
        drawCornerAccent(g, x + w, y + h, 6, false, false);

        int cy = y + PADDING;

        // ── TITLE ────────────────────────────────────────────────────────────
        g.fill(x, cy - 3, x + w, cy + 11, COLOR_HEADER_BG);
        String title = "STATUS";
        g.text(font, Component.literal(title),
                x + w / 2 - font.width(title) / 2, cy, COLOR_TITLE, true);
        cy += 16;
        g.fill(x + PADDING, cy, x + w - PADDING, cy + 1, COLOR_SEPARATOR);
        cy += 6;

        // ── HEADER ───────────────────────────────────────────────────────────
        int level   = ClientStatsManager.getLevel();
        int charXp  = ClientStatsManager.getCharacterXp();
        int xpReq   = ClientStatsManager.getXpRequired();
        int unspent = ClientStatsManager.getUnspentPoints();

        int thirdW  = w / 3;
        int headerY = cy;

        // LEFT — XP with spacing
        int xpBarW = thirdW - PADDING * 2;
        int xpBarX = x + PADDING;
        // "XP" label
        g.text(font, Component.literal("XP"),
                xpBarX + xpBarW / 2 - font.width("XP") / 2,
                headerY, COLOR_LABEL, false);
        // Gap of 4px between label and bar
        int xpBarY = headerY + 11;
        g.fill(xpBarX - 1, xpBarY - 1, xpBarX + xpBarW + 1, xpBarY + 5, COLOR_XP_BORDER);
        g.fill(xpBarX, xpBarY, xpBarX + xpBarW, xpBarY + 4, COLOR_XP_BAR_BG);
        if (xpReq > 0 && charXp > 0) {
            int fill = (int)((float) charXp / xpReq * xpBarW);
            if (fill > 0) g.fill(xpBarX, xpBarY, xpBarX + fill, xpBarY + 4, COLOR_XP_BAR_FILL);
        }
        // Gap of 4px between bar and numbers
        String xpText = charXp + "/" + xpReq;
        g.text(font, Component.literal(xpText),
                xpBarX + xpBarW / 2 - font.width(xpText) / 2,
                xpBarY + 8, COLOR_LABEL, false);

        // CENTER — Level
        int centerX    = x + thirdW + thirdW / 2;
        String levelStr = String.valueOf(level);
        int scaledW    = font.width(levelStr) * 2;
        g.pose().pushMatrix();
        g.pose().scale(2.0f, 2.0f);
        g.text(font, Component.literal(levelStr),
                (centerX - scaledW / 2) / 2, headerY / 2, COLOR_VALUE, true);
        g.pose().popMatrix();
        String lbl = "LEVEL";
        g.text(font, Component.literal(lbl),
                centerX - font.width(lbl) / 2, headerY + 17, COLOR_LABEL, false);

        // RIGHT — Info
        int infoX = x + thirdW * 2 + PADDING;
        g.text(font, Component.literal("JOB: None"),   infoX, headerY,      COLOR_LABEL, false);
        g.text(font, Component.literal("RACE: None"),  infoX, headerY + 9,  COLOR_LABEL, false);
        g.text(font, Component.literal("TITLE: None"), infoX, headerY + 18, COLOR_LABEL, false);
        g.text(font, Component.literal("CLASS: None"), infoX, headerY + 27, COLOR_LABEL, false);

        cy += 44;
        g.fill(x + PADDING, cy, x + w - PADDING, cy + 1, COLOR_SEPARATOR);
        cy += 5;

        // ── RESOURCE BARS (Solo Leveling style) ──────────────────────────────
        // Split into 3 equal sections side by side
        // Each: icon | [bar] on top, number centered below
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int secW = (w - PADDING * 2) / 3;
            int sec0X = x + PADDING;
            int sec1X = sec0X + secW;
            int sec2X = sec1X + secW;

            float hp = player.getHealth(), maxHp = player.getMaxHealth();
            drawSLBarVertical(g, sec0X, cy, secW, "❤", "HP",
                    hp, maxHp,
                    RpgDisplayUtils.toDisplayHp(hp), RpgDisplayUtils.toDisplayHp(maxHp),
                    COLOR_HP_BG, COLOR_HP_FILL);

            int sta = ClientStaminaManager.getStamina(), maxSta = ClientStaminaManager.getMaxStamina();
            drawSLBarVertical(g, sec1X, cy, secW, "⚡", "STAMINA",
                    sta, maxSta, sta, maxSta, COLOR_STA_BG, COLOR_STA_FILL);

            int man = ClientManaManager.getMana(), maxMan = ClientManaManager.getMaxMana();
            drawSLBarVertical(g, sec2X, cy, secW, "✨", "MANA",
                    man, maxMan, man, maxMan, COLOR_MAN_BG, COLOR_MAN_FILL);

            cy += SL_BAR_H + 8;
        }

        g.fill(x + PADDING, cy, x + w - PADDING, cy + 1, COLOR_SEPARATOR);
        cy += 5;

        // ── UNSPENT POINTS ────────────────────────────────────────────────────
        if (unspent > 0) {
            String pts = "⬆ " + unspent + " Point" + (unspent > 1 ? "s" : "") + " Available";
            g.text(font, Component.literal(pts),
                    x + w / 2 - font.width(pts) / 2, cy, COLOR_VALUE, true);
            cy += 10;
        }

        // ── ABILITY SCORES ────────────────────────────────────────────────────
        AbilityScore[] scores = AbilityScore.values();
        int halfW  = (w - PADDING * 2) / 2;
        int col0X  = x + PADDING;
        int col1X  = x + PADDING + halfW;

        for (int i = 0; i < scores.length; i++) {
            AbilityScore score = scores[i];
            int colX  = (i % 2 == 0) ? col0X : col1X;
            int rowY  = cy + (i / 2) * STAT_ROW_H;
            int textY = rowY + (STAT_ROW_H - 8) / 2;

            if (inBounds(mx, my, colX, rowY, halfW - 4, STAT_ROW_H - 2))
                g.fill(colX, rowY, colX + halfW - 4, rowY + STAT_ROW_H - 2, 0x22FFFFFF);

            int scoreVal = ClientStatsManager.getScore(score);
            int modifier = ClientStatsManager.getModifier(score);

            g.text(font, Component.literal(getStatEmoji(score)),
                    colX, textY, 0xFFFFFFFF, false);
            g.text(font, Component.literal(score.name()),
                    colX + STAT_EMOJI_W, textY, COLOR_LABEL, false);
            g.text(font, Component.literal(String.valueOf(scoreVal)),
                    colX + STAT_EMOJI_W + STAT_NAME_W, textY, COLOR_VALUE, true);

            String modStr = modifier >= 0 ? "(+" + modifier + ")" : "(" + modifier + ")";
            int modColor  = modifier > 0 ? COLOR_MODIFIER_POS
                    : modifier < 0 ? COLOR_MODIFIER_NEG : COLOR_MODIFIER_NEU;
            g.text(font, Component.literal(modStr),
                    colX + STAT_EMOJI_W + STAT_NAME_W + STAT_VAL_W, textY, modColor, false);

            if (unspent > 0) {
                int btnX = colX + halfW - BTN_SIZE - 4;
                int btnY = rowY + (STAT_ROW_H - BTN_SIZE) / 2;
                boolean btnHov = inBounds(mx, my, btnX, btnY, BTN_SIZE, BTN_SIZE);
                if (btnHov) hoveredScore = score;
                g.fill(btnX, btnY, btnX + BTN_SIZE, btnY + BTN_SIZE,
                        btnHov ? COLOR_BTN_HOVER : COLOR_BTN);
                g.fill(btnX,              btnY,                btnX + BTN_SIZE, btnY + 1,          COLOR_BORDER);
                g.fill(btnX,              btnY + BTN_SIZE - 1, btnX + BTN_SIZE, btnY + BTN_SIZE,   COLOR_BORDER);
                g.fill(btnX,              btnY,                btnX + 1,        btnY + BTN_SIZE,   COLOR_BORDER);
                g.fill(btnX + BTN_SIZE-1, btnY,                btnX + BTN_SIZE, btnY + BTN_SIZE,   COLOR_BORDER);
                g.text(font, Component.literal("+"),
                        btnX + BTN_SIZE / 2 - font.width("+") / 2 + 1,
                        btnY + (BTN_SIZE - 8) / 2, COLOR_BTN_TEXT, true);
            }
        }

        cy += (scores.length / 2) * STAT_ROW_H + 2;

        g.fill(x + PADDING, cy, x + w - PADDING, cy + 1, COLOR_SEPARATOR);
        cy += 3;
        String hint = "[Esc] Close";
        g.text(font, Component.literal(hint),
                x + w / 2 - font.width(hint) / 2, cy, COLOR_LABEL, false);
    }

    /**
     * Solo Leveling style bar — icon on left, bar next to it, number centered below.
     * Each section is side by side (HP | STAMINA | MANA).
     */
    private void drawSLBarVertical(GuiGraphicsExtractor g, int x, int y, int secW,
                                   String icon, String label,
                                   float value, float max,
                                   int displayCurrent, int displayMax,
                                   int bgColor, int fillColor) {
        int iconW  = font.width(icon) + 3;
        int barX   = x + iconW;
        int barW   = secW - iconW - 6;

        // Icon — vertically centered to bar
        g.text(font, Component.literal(icon), x, y + (BAR_H - 8) / 2, 0xFFFFFFFF, false);

        // Bar with border
        g.fill(barX - 1, y - 1, barX + barW + 1, y + BAR_H + 1, COLOR_BAR_BORDER);
        g.fill(barX, y, barX + barW, y + BAR_H, bgColor);
        if (max > 0 && value > 0) {
            int fill = (int)(Math.min(value / max, 1f) * barW);
            if (fill > 0) g.fill(barX, y, barX + fill, y + BAR_H, fillColor);
        }

        // Number centered below bar (smaller — no shadow)
        String valText = displayCurrent + "/" + displayMax;
        int valX = barX + barW / 2 - font.width(valText) / 2;
        g.text(font, Component.literal(valText), valX, y + BAR_H + 3, COLOR_LABEL, false);
    }

    private void drawCornerAccent(GuiGraphicsExtractor g, int x, int y,
                                  int size, boolean left, boolean top) {
        int dx = left ? 1 : -1, dy = top ? 1 : -1;
        g.fill(x, y, x + dx * size, y + dy, COLOR_VALUE);
        g.fill(x, y, x + dx, y + dy * size, COLOR_VALUE);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        if (hoveredScore != null) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new SpendAttributePointPayload(hoveredScore));
            playClick();
            return true;
        }
        return super.mouseClicked(mouse, doubleClick);
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