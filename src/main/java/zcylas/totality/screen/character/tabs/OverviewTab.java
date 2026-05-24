package zcylas.totality.screen.character.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.api.rpg.ancestry.ClientAncestryManager;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.ClientStatsManager;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.stamina.ClientStaminaManager;
import zcylas.totality.screen.character.BaseCharacterScreen;
import zcylas.totality.screen.character.CharacterScreen;

public class OverviewTab extends CharacterScreenTab {

    public OverviewTab(CharacterScreen screen) { super(screen); }

    @Override
    public void draw(GuiGraphicsExtractor g, Font font,
                     int mx, int my, int ba,
                     int x, int y, int w, int h) {
        Player player = Minecraft.getInstance().player;

        int midW   = w / 2;
        int rightX = x + midW;
        int rightW = w - midW;

        drawMiddleColumn(g, font, player, x, y, midW, h);
        drawRightColumn(g, font, player, rightX, y, rightW, h);
    }

    // ── Middle: Level + Resources + Attributes stacked, no gaps ──────────────

    private void drawMiddleColumn(GuiGraphicsExtractor g, Font font, Player player,
                                  int x, int y, int w, int h) {
        int levelH = (int)(h * 0.35f);
        int resH   = (int)(h * 0.27f);
        int attrH  = h - levelH - resH;
        int resY   = y + levelH;
        int attrY  = resY + resH;

        drawLevelPanel(g, font, x, y, w, levelH);
        drawResourcesPanel(g, font, player, x, resY, w, resH);
        drawAttributesPanel(g, font, x, attrY, w, attrH);
    }

    // ── LEVEL ─────────────────────────────────────────────────────────────────

    private void drawLevelPanel(GuiGraphicsExtractor g, Font font,
                                int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "LEVEL");

        int level  = ClientStatsManager.getLevel();
        long xp    = ClientStatsManager.getCharacterXp();
        long xpReq = ClientStatsManager.getXpRequired();
        int cx = x + w / 2;
        int cy = y + HDR_H + 2;

        // "LEVEL" sub-label
        String lbl = "LEVEL";
        int lw = Math.round(font.width(lbl) * SMALL);
        screen.drawSmallAt(g, lbl, cx - lw / 2, cy, COLOR_LABEL);
        cy += SLH + 1;

        // Big number — 3x
        String lvlStr = String.valueOf(level);
        g.pose().pushMatrix();
        g.pose().scale(3f, 3f);
        g.text(font, Component.literal(lvlStr),
                (int)(cx / 3f - font.width(lvlStr) / 2f),
                (int)(cy / 3f),
                COLOR_ACCENT, true);
        g.pose().popMatrix();
        cy += 26;

        // XP bar
        int barX = x + PAD, barW = w - PAD * 2, barH = 6;
        g.fill(barX, cy, barX + barW, cy + barH, COLOR_XP_BG);
        if (xpReq > 0) {
            int fill = (int)(Math.min((float)xp / xpReq, 1f) * barW);
            if (fill > 0) g.fill(barX, cy, barX + fill, cy + barH, COLOR_XP_FILL);
        }
        screen.drawBorder(g, barX, cy, barW, barH, COLOR_BORDER_INNER);
        cy += barH + 3;

        // XP text
        String xpStr = xp + " / " + xpReq + " XP";
        int xw = Math.round(font.width(xpStr) * SMALL);
        screen.drawSmallAt(g, xpStr, cx - xw / 2, cy, COLOR_VALUE);
    }

    // ── RESOURCES ─────────────────────────────────────────────────────────────

    private void drawResourcesPanel(GuiGraphicsExtractor g, Font font, Player player,
                                    int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "RESOURCES");

        float hp    = player != null ? RpgDisplayUtils.toDisplayHp(player.getHealth())    : 0;
        float maxHp = player != null ? RpgDisplayUtils.toDisplayHp(player.getMaxHealth()) : 1;
        float sta    = ClientStaminaManager.getStamina();
        float maxSta = ClientStaminaManager.getMaxStamina();
        float mana    = ClientManaManager.getMana();
        float maxMana = ClientManaManager.getMaxMana();

        // Layout — maximize bar width
        int iconW = font.width("⚡") + 3;
        int lblW  = Math.round(font.width("STAMINA") * SMALL) + 4;
        int valW  = Math.round(font.width("170/170") * SMALL) + 4;
        int barX  = x + PAD + iconW + lblW;
        int barW  = w - PAD * 2 - iconW - lblW - valW - 4;
        int valX  = barX + barW + 4;

        int usableH = h - HDR_H - PAD * 2;
        int rowH    = usableH / 3;
        int cy      = y + HDR_H + PAD;

        cy = drawResRow(g, font, x + PAD, barX, barW, valX, cy, rowH, "♥", "HEALTH",  0xFFCC3333, hp,   maxHp);
        cy = drawResRow(g, font, x + PAD, barX, barW, valX, cy, rowH, "⚡", "STAMINA", 0xFF44AA44, sta,  maxSta);
        drawResRow(g, font, x + PAD, barX, barW, valX, cy, rowH, "◇", "MANA",    0xFF4466CC, mana, maxMana);
    }

    private int drawResRow(GuiGraphicsExtractor g, Font font,
                           int iconX, int barX, int barW, int valX,
                           int y, int rowH,
                           String icon, String label, int color,
                           float cur, float max) {
        int textY = y + (rowH - 8) / 2;
        int barH  = 7;
        int barY  = y + (rowH - barH) / 2;

        g.text(font, Component.literal(icon), iconX, textY, color, false);
        screen.drawSmallAt(g, label, iconX + font.width(icon) + 2, textY + 1, color);

        g.fill(barX, barY, barX + barW, barY + barH, COLOR_XP_BG);
        if (max > 0) {
            int fill = (int)(Math.min(cur / max, 1f) * barW);
            if (fill > 0) g.fill(barX, barY, barX + fill, barY + barH, color);
        }
        screen.drawBorder(g, barX, barY, barW, barH, COLOR_BORDER_INNER);

        String val = (int)cur + "/" + (int)max;
        int vw = Math.round(font.width(val) * SMALL);
        screen.drawSmallAt(g, val, valX, textY + 1, COLOR_VALUE);

        return y + rowH;
    }

    // ── ATTRIBUTES ────────────────────────────────────────────────────────────

    private void drawAttributesPanel(GuiGraphicsExtractor g, Font font,
                                     int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "ATTRIBUTES");

        AbilityScore[] scores = AbilityScore.values();
        int half  = scores.length / 2; // 4
        int colW  = (w - PAD * 2) / 2;
        int col1X = x + PAD;
        int col2X = col1X + colW;

        // Tight fixed row height
        int rowH = (h - HDR_H - PAD) / half;
        int cy   = y + HDR_H + PAD / 2;

        for (int i = 0; i < half; i++) {
            int rowY = cy + i * rowH + (rowH - 8) / 2;
            drawAttrRow(g, font, scores[i],        col1X, rowY, colW);
            drawAttrRow(g, font, scores[i + half], col2X, rowY, colW);
        }
    }

    private void drawAttrRow(GuiGraphicsExtractor g, Font font,
                             AbilityScore score, int x, int y, int colW) {
        String icon   = getScoreIcon(score);
        int    color  = getScoreColor(score);
        // Use abbreviation (STR, DEX...) not full name
        String abbrev = score.name();
        String valStr = String.valueOf(ClientStatsManager.getScore(score));

        g.text(font, Component.literal(icon), x, y, color, false);
        int abbrevX = x + font.width(icon) + 2;
        screen.drawSmallAt(g, abbrev, abbrevX, y + 1, color);
        int vw = Math.round(font.width(valStr) * SMALL);
        screen.drawSmallAt(g, valStr, x + colW - vw - 4, y + 1, COLOR_VALUE);
    }

    // ── Right column: Identity + Combat Summary ───────────────────────────────

    private void drawRightColumn(GuiGraphicsExtractor g, Font font, Player player,
                                 int x, int y, int w, int h) {
        int identityH = (int)(h * 0.54f);
        int combatY   = y + identityH;
        int combatH   = h - identityH;

        drawIdentityPanel(g, font, player, x, y, w, identityH);
        drawCombatPanel(g, font, player, x, combatY, w, combatH);
    }

    // ── IDENTITY ──────────────────────────────────────────────────────────────

    private void drawIdentityPanel(GuiGraphicsExtractor g, Font font, Player player,
                                   int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "IDENTITY");

        int cx = x + w / 2;
        int ix = x + PAD;
        int iw = w - PAD * 2;
        int cy = y + HDR_H + PAD;

        // Large name
        String name = player != null ? player.getName().getString() : "Unknown";
        g.pose().pushMatrix();
        g.pose().scale(1.5f, 1.5f);
        g.text(font, Component.literal(name),
                (int)(cx / 1.5f - font.width(name) / 2f),
                (int)(cy / 1.5f),
                COLOR_ACCENT, true);
        g.pose().popMatrix();
        cy += 18;

        // Title
        String titleLine = "◆ — None — ◆";
        int tlw = Math.round(font.width(titleLine) * SMALL);
        screen.drawSmallAt(g, titleLine, cx - tlw / 2, cy, COLOR_COPPER);
        cy += SLH + PAD;

        // Description placeholder
        cy += SLH;

        // Separator with diamond
        g.fill(ix, cy, cx - 6, cy + 1, COLOR_SEPARATOR);
        g.text(font, Component.literal("◆"), cx - 3, cy - 3, COLOR_COPPER, false);
        g.fill(cx + 6, cy, ix + iw, cy + 1, COLOR_SEPARATOR);
        cy += PAD + 2;

        // Species + Origin
        String speciesStr = ClientAncestryManager.hasAncestry()
                ? ClientAncestryManager.getSpecies().getDisplayName() : "None";
        String originStr  = ClientAncestryManager.getOrigin() != null
                ? ClientAncestryManager.getOrigin().getDisplayName() : "None";

        drawIdRow(g, font, ix, cy, iw, "Species:", speciesStr); cy += NLH + 2;
        drawIdRow(g, font, ix, cy, iw, "Origin:",  originStr);
    }

    private void drawIdRow(GuiGraphicsExtractor g, Font font,
                           int ix, int y, int iw, String label, String value) {
        g.text(font, Component.literal(label), ix, y, COLOR_LABEL, false);
        g.text(font, Component.literal(value), ix + iw - font.width(value), y, COLOR_VALUE, false);
    }

    // ── COMBAT SUMMARY ────────────────────────────────────────────────────────

    private void drawCombatPanel(GuiGraphicsExtractor g, Font font, Player player,
                                 int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "COMBAT SUMMARY");

        int ix   = x + PAD;
        int valX = x + w - PAD;
        int rowH = (h - HDR_H - PAD) / 5;
        int cy   = y + HDR_H + PAD / 2;

        int armor = player != null ? player.getArmorValue() : 0;
        int speed = player != null
                ? (int)(player.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1 * 100)
                : 100;

        drawCombatRow(g, font, "⚔", "Attack Power", "—",                   ix, cy + rowH * 0, valX);
        drawCombatRow(g, font, "◈", "Defense",       "—",                   ix, cy + rowH * 1, valX);
        drawCombatRow(g, font, "◆", "Armor",         String.valueOf(armor), ix, cy + rowH * 2, valX);
        drawCombatRow(g, font, "✦", "Crit Chance",   "—",                   ix, cy + rowH * 3, valX);
        drawCombatRow(g, font, "◎", "Move Speed",    speed + "%",           ix, cy + rowH * 4, valX);
    }

    private void drawCombatRow(GuiGraphicsExtractor g, Font font,
                               String icon, String label, String value,
                               int x, int y, int valX) {
        g.text(font, Component.literal(icon), x, y, COLOR_COPPER, false);
        screen.drawSmallAt(g, label, x + font.width(icon) + 3, y + 1, COLOR_LABEL);
        int vw = Math.round(font.width(value) * SMALL);
        screen.drawSmallAt(g, value, valX - vw, y + 1, COLOR_VALUE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getScoreIcon(AbilityScore score) {
        return switch (score) {
            case STR -> "⚔"; case DEX -> "◎"; case CON -> "♥"; case END -> "⚡";
            case INT -> "✦"; case WIS -> "◈"; case CHA -> "★"; case FTH -> "✝";
        };
    }

    private int getScoreColor(AbilityScore score) {
        return switch (score) {
            case STR -> 0xFFCC4444; case DEX -> 0xFFCC8833;
            case CON -> 0xFF44AACC; case END -> 0xFF44CC88;
            case INT -> 0xFFAA44CC; case WIS -> 0xFF4488CC;
            case CHA -> 0xFFCCAA33; case FTH -> 0xFFCCCCCC;
        };
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int   COLOR_SEPARATOR    = BaseCharacterScreen.COLOR_SEPARATOR;
    private static final int   COLOR_LABEL        = BaseCharacterScreen.COLOR_LABEL;
    private static final int   COLOR_VALUE        = BaseCharacterScreen.COLOR_VALUE;
    private static final int   COLOR_ACCENT       = BaseCharacterScreen.COLOR_ACCENT;
    private static final int   COLOR_BORDER_INNER = BaseCharacterScreen.COLOR_BORDER_INNER;
    private static final int   COLOR_COPPER       = BaseCharacterScreen.COLOR_COPPER;
    private static final int   COLOR_XP_BG        = BaseCharacterScreen.COLOR_XP_BG;
    private static final int   COLOR_XP_FILL      = BaseCharacterScreen.COLOR_XP_FILL;
    private static final int   HDR_H              = BaseCharacterScreen.HDR_H;
    private static final int   BAR_H              = BaseCharacterScreen.BAR_H;
    private static final int   PAD                = BaseCharacterScreen.PAD;
    private static final int   NLH                = BaseCharacterScreen.NLH;
    private static final int   SLH                = BaseCharacterScreen.SLH;
    private static final float SMALL              = BaseCharacterScreen.SMALL;
}