package zcylas.totality.screen.character.tabs;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.ClientStatsManager;
import zcylas.totality.networking.stats.SpendAttributePointPayload;
import zcylas.totality.screen.character.BaseCharacterScreen;
import zcylas.totality.screen.character.CharacterScreen;

public class AttributesTab extends CharacterScreenTab {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int COLOR_PENDING       = 0xFF44FF88;
    private static final int COLOR_MODIFIER_POS  = 0xFF44FFAA;
    private static final int COLOR_MODIFIER_NEG  = 0xFFFF4444;
    private static final int COLOR_MODIFIER_NEU  = 0xFF888888;
    private static final int COLOR_BTN_PLUS      = 0xFF001830;
    private static final int COLOR_BTN_PLUS_HOV  = 0xFF0A5070;
    private static final int COLOR_BTN_MINUS     = 0xFF1A0808;
    private static final int COLOR_BTN_MINUS_HOV = 0xFF3A1010;
    private static final int COLOR_CONFIRM_BG    = 0xFF001A08;
    private static final int COLOR_CONFIRM_HOV   = 0xFF002A10;
    private static final int COLOR_RESET_BG      = 0xFF1A0808;
    private static final int COLOR_RESET_HOV     = 0xFF2A1010;
    private static final int COLOR_ROW_SEL       = 0xCC001828;
    private static final int COLOR_PTS_BG        = 0xFF001020;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int ROW_H   = 18;
    private static final int BTN_W   = 14;
    private static final int BTN_H   = 12;
    private static final int BTN_GAP = 3;
    private static final int CONF_H  = 14;
    private static final int PTS_H   = 18;
    private static final int TLH = BaseCharacterScreen.TLH;
    private static final float TINY = BaseCharacterScreen.TINY;
    // ── State ─────────────────────────────────────────────────────────────────
    private final int[] pending = new int[AbilityScore.values().length];
    private AbilityScore selectedScore = AbilityScore.STR;
    private boolean confirmHovered     = false;
    private boolean resetHovered       = false;
    private int detailScrollY          = 0;

    public AttributesTab(CharacterScreen screen) { super(screen); }

    @Override
    public void onOpen() {
        java.util.Arrays.fill(pending, 0);
        detailScrollY = 0;
    }

    private void resetPending() { java.util.Arrays.fill(pending, 0); }

    private int pendingTotal() {
        int t = 0; for (int p : pending) t += p; return t;
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    @Override
    public void mouseScrolled(int mx, int my, double delta) {
        // Only scroll if mouse is in detail panel
        int x     = screen.contentX;
        int listW = (int)(screen.contentW * 0.55f);
        int detailX = x + listW;
        if (mx >= detailX) {
            detailScrollY = Math.max(0, detailScrollY - (int)(delta * 10));
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    @Override
    public void draw(GuiGraphicsExtractor g, Font font,
                     int mx, int my, int ba,
                     int x, int y, int w, int h) {
        confirmHovered = false;
        resetHovered   = false;

        int unspent = ClientStatsManager.getUnspentPoints() - pendingTotal();
        int listW   = (int)(w * 0.55f);
        int detailX = x + listW ;
        int detailW = w - listW;

        drawAttributeList(g, font, mx, my, x, y, listW, h, unspent);
        drawDetailPanel(g, font, mx, my, detailX, y, detailW, h, unspent);
    }

    // ── Attribute list ────────────────────────────────────────────────────────

    private void drawAttributeList(GuiGraphicsExtractor g, Font font,
                                   int mx, int my,
                                   int x, int y, int w, int h, int unspent) {
        boolean hasPointsOrPending = unspent > 0 || pendingTotal() > 0;
        int btnAreaH = pendingTotal() > 0 ? CONF_H + PAD * 2 : 0;
        int listH    = h - btnAreaH;

        screen.drawPanel(g, x, y, w, listH);
        screen.drawPanelHdr(g, x, y, w, "ATTRIBUTES");

        // Fixed button positions — minus always left of plus, plus always right edge
        int plusX  = x + w - PAD - BTN_W - 2;
        int minusX = plusX - BTN_W - BTN_GAP;

        // Column positions
        int headerY = y + HDR_H + 2;
        int nameX   = x + PAD + 4;
        int curX    = minusX - 46;
        int aftX    = curX + 26;

        // Column headers — only when points or pending
        screen.drawSmallAt(g, "ATTRIBUTE", nameX, headerY, COLOR_LABEL);
        screen.drawSmallAt(g, "NOW", curX, headerY, COLOR_LABEL);
        if (hasPointsOrPending)
            screen.drawSmallAt(g, "AFTER", aftX, headerY, COLOR_LABEL);
        g.fill(x + PAD, headerY + SLH + 1, x + w - PAD, headerY + SLH + 2, COLOR_SEPARATOR);

        int rowStartY = headerY + SLH + 4;
        AbilityScore[] scores = AbilityScore.values();

        for (int i = 0; i < scores.length; i++) {
            AbilityScore score = scores[i];
            int rowY  = rowStartY + i * ROW_H;
            int textY = rowY + (ROW_H - 8) / 2;
            boolean sel = score == selectedScore;
            boolean hov = screen.inB(mx, my, x + PAD, rowY, minusX - x - PAD - 4, ROW_H);

            // Row background
            if (sel)
                g.fill(x + PAD, rowY, x + w - PAD, rowY + ROW_H - 1, COLOR_ROW_SEL);
            else if (hov)
                g.fill(x + PAD, rowY, x + w - PAD, rowY + ROW_H - 1, COLOR_ROW_HOV);

            // Selected left accent bar
            if (sel)
                g.fill(x + PAD, rowY + 1, x + PAD + 2, rowY + ROW_H - 2, COLOR_ACCENT);

            // Icon (Unicode symbol)
            int abbrevColor = getScoreColor(score);
            String icon = getScoreIcon(score);
            g.text(font, Component.literal(icon), nameX, textY + 1, abbrevColor, false);

            // Abbreviation
            int abbrevX = nameX + font.width(icon) + 3;
            screen.drawSmallAt(g, score.name(), abbrevX, textY + 1, abbrevColor);
            int abbrevW = Math.round(font.width(score.name()) * SMALL);

            // Full name — only truncate if genuinely needed
            int nameStartX = abbrevX + abbrevW + 3;
            int nameMaxW   = hasPointsOrPending
                    ? curX - nameStartX - 4
                    : x + w - PAD - nameStartX - 4;
            String dName = score.getDisplayName();
            // was drawSmallAt, now drawTinyAt for the name only
            if (Math.round(font.width(dName) * TINY) <= nameMaxW) {
                screen.drawTinyAt(g, dName, nameStartX, textY + 1, COLOR_LABEL);
            } else {
                while (Math.round(font.width(dName + "..") * TINY) > nameMaxW && dName.length() > 2)
                    dName = dName.substring(0, dName.length() - 1);
                screen.drawTinyAt(g, dName + "..", nameStartX, textY + 1, COLOR_LABEL);
            }

            // Values (only when points/pending)
            int current = ClientStatsManager.getScore(score);
            int pend    = pending[i];
            int after   = current + pend;

            screen.drawSmallAt(g, String.valueOf(current), curX, textY, COLOR_VALUE);
            if (pend != 0) {
                screen.drawSmallAt(g, "▶", curX + 16, textY, COLOR_SEPARATOR);
                screen.drawSmallAt(g, String.valueOf(after), aftX, textY,
                        pend > 0 ? COLOR_PENDING : COLOR_MODIFIER_NEG);
            }

            // Minus button — fixed left position, only visible when pending > 0
            if (pend > 0) {
                int bY = rowY + (ROW_H - BTN_H) / 2;
                boolean mHov = screen.inB(mx, my, minusX, bY, BTN_W, BTN_H);
                g.fill(minusX, bY, minusX + BTN_W, bY + BTN_H,
                        mHov ? COLOR_BTN_MINUS_HOV : COLOR_BTN_MINUS);
                screen.drawBorder(g, minusX, bY, BTN_W, BTN_H, COLOR_RED);
                g.text(font, Component.literal("−"),
                        minusX + BTN_W / 2 - font.width("−") / 2,
                        bY + (BTN_H - 8) / 2, COLOR_RED, false);
            }

            // Plus button — fixed right position, only visible when unspent > 0
            if (unspent > 0) {
                int bY = rowY + (ROW_H - BTN_H) / 2;
                boolean pHov = screen.inB(mx, my, plusX, bY, BTN_W, BTN_H);
                g.fill(plusX, bY, plusX + BTN_W, bY + BTN_H,
                        pHov ? COLOR_BTN_PLUS_HOV : COLOR_BTN_PLUS);
                screen.drawBorder(g, plusX, bY, BTN_W, BTN_H, COLOR_ACCENT);
                g.text(font, Component.literal("+"),
                        plusX + BTN_W / 2 - font.width("+") / 2,
                        bY + (BTN_H - 8) / 2, COLOR_ACCENT, false);
            }

            // Row separator
            g.fill(x + PAD, rowY + ROW_H - 1, x + w - PAD, rowY + ROW_H, COLOR_SEPARATOR);
        }

        // ── Confirm / Reset ───────────────────────────────────────────────────
        if (pendingTotal() > 0) {
            int btnY  = y + listH + PAD;
            int half  = (w - PAD * 3) / 2;
            int confX = x + PAD;
            int resX  = confX + half + PAD;

            confirmHovered = screen.inB(mx, my, confX, btnY, half, CONF_H);
            g.fill(confX, btnY, confX + half, btnY + CONF_H,
                    confirmHovered ? COLOR_CONFIRM_HOV : COLOR_CONFIRM_BG);
            screen.drawBorder(g, confX, btnY, half, CONF_H, COLOR_GREEN);
            String cl = "Confirm";
            g.text(font, Component.literal(cl),
                    confX + half / 2 - font.width(cl) / 2,
                    btnY + (CONF_H - 8) / 2, COLOR_GREEN, false);

            resetHovered = screen.inB(mx, my, resX, btnY, half, CONF_H);
            g.fill(resX, btnY, resX + half, btnY + CONF_H,
                    resetHovered ? COLOR_RESET_HOV : COLOR_RESET_BG);
            screen.drawBorder(g, resX, btnY, half, CONF_H, COLOR_RED);
            String rl = "Reset";
            g.text(font, Component.literal(rl),
                    resX + half / 2 - font.width(rl) / 2,
                    btnY + (CONF_H - 8) / 2, COLOR_RED, false);
        }
    }

    // ── Detail panel (scrollable) ─────────────────────────────────────────────

    private void drawDetailPanel(GuiGraphicsExtractor g, Font font,
                                 int mx, int my,
                                 int x, int y, int w, int h, int unspent) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "ATTRIBUTE DETAILS");

        AbilityScore show = selectedScore;
        int current  = ClientStatsManager.getScore(show);
        int pend     = pending[show.ordinal()];
        int after    = current + pend;
        int modifier = AbilityScore.getModifier(current);
        int newMod   = AbilityScore.getModifier(after);

        int ix = x + PAD;
        int iw = w - PAD * 2;

        // Reserve space for points box at bottom
        int ptsH        = PTS_H + PAD;
        int scrollAreaY = y + HDR_H + PAD;
        int scrollAreaH = h - HDR_H - PAD - ptsH;

        // Scissor scrollable area
        screen.sc(g, ix, scrollAreaY, iw, scrollAreaH);

        int cy = scrollAreaY - detailScrollY;

        // ── Icon + large name ─────────────────────────────────────────────────
        int iconColor = getScoreColor(show);
        String icon   = getScoreIcon(show);

        g.text(font, Component.literal(icon), ix, cy + 2, iconColor, false);

        g.pose().pushMatrix();
        g.pose().scale(1.5f, 1.5f);
        g.text(font, Component.literal(show.getDisplayName()),
                (int)((ix + font.width(icon) + 4) / 1.5f),
                (int)(cy / 1.5f),
                iconColor, true);
        g.pose().popMatrix();
        cy += 20;

        // ── Description ───────────────────────────────────────────────────────
        cy = screen.drawWrappedSmall(g, show.getDescription(), ix, cy, iw, COLOR_LABEL);
        cy += PAD;

        g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR);
        cy += PAD;

        // ── AFFECTS ───────────────────────────────────────────────────────────
        screen.drawSmallAt(g, "AFFECTS:", ix, cy, COLOR_COPPER);
        cy += SLH + 3;
        for (String affect : getScoreAffects(show)) {
            screen.drawSmallAt(g, "◆  " + affect, ix + 4, cy, COLOR_LABEL);
            cy += SLH + 2;
        }
        cy += PAD;

        g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR);
        cy += PAD;

        // ── BONUS PREVIEW ─────────────────────────────────────────────────────
        screen.drawSmallAt(g, "BONUS PREVIEW", ix, cy, COLOR_COPPER);
        cy += SLH + 3;

        // Column headers — give label column enough width to avoid truncation
        int col1X = ix + (iw * 52 / 100);
        int col2X = ix + (iw * 78 / 100);
        screen.drawTinyAt(g, "CURRENT", col1X - 2, cy, COLOR_LABEL);
        screen.drawTinyAt(g, "AFTER",   col2X,     cy, COLOR_LABEL);
        cy += TLH + 2;
        g.fill(ix, cy, ix + iw, cy + 1, COLOR_SEPARATOR);
        cy += 3;

// Modifier
        screen.drawTinyAt(g, "Modifier", ix, cy, COLOR_LABEL);
        screen.drawTinyAt(g, formatMod(modifier), col1X, cy,
                modifier > 0 ? COLOR_MODIFIER_POS : modifier < 0 ? COLOR_MODIFIER_NEG : COLOR_MODIFIER_NEU);
        screen.drawTinyAt(g, formatMod(newMod), col2X, cy,
                newMod > modifier ? COLOR_PENDING : newMod < modifier ? COLOR_MODIFIER_NEG : COLOR_MODIFIER_NEU);
        cy += TLH + 3;

// Stat rows
        for (BonusRow row : getBonusRows(show, modifier, newMod)) {
            screen.drawTinyAt(g, row.label,   ix,    cy, COLOR_LABEL);
            screen.drawTinyAt(g, row.current, col1X, cy, COLOR_VALUE);
            int aftColor = !row.current.equals(row.after) && pend > 0 ? COLOR_PENDING : COLOR_VALUE;
            screen.drawTinyAt(g, row.after,   col2X, cy, aftColor);
            cy += TLH + 3;
        }

        // Update scroll max
        int totalContentH = (cy + detailScrollY) - scrollAreaY;
        int maxScroll = Math.max(0, totalContentH - scrollAreaH + PAD);
        if (detailScrollY > maxScroll) detailScrollY = maxScroll;

        screen.esc(g);

        // ── Scroll indicator ──────────────────────────────────────────────────
        if (totalContentH > scrollAreaH) {
            int trackX = x + w - 4;
            int trackH = scrollAreaH;
            int trackY = scrollAreaY;
            g.fill(trackX, trackY, trackX + 2, trackY + trackH, COLOR_SEPARATOR);
            int thumbH = Math.max(10, trackH * scrollAreaH / totalContentH);
            int thumbY = trackY + detailScrollY * (trackH - thumbH) / Math.max(1, totalContentH - scrollAreaH);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, COLOR_ACCENT);
        }

        // ── Available points box — outside scissor, always visible ────────────
        int bx = ix;
        int bw = iw;
        int bh = PTS_H;
        int by = y + h - bh - PAD;
        g.fill(bx, by, bx + bw, by + bh, COLOR_PTS_BG);
        screen.drawBorder(g, bx, by, bw, bh,
                unspent > 0 ? COLOR_ACCENT : COLOR_BORDER);
        String ptsText = unspent > 0 ? "Available Points:  " + unspent : "No Points Available";
        int ptColor = unspent > 0 ? COLOR_ACCENT : COLOR_LABEL;
        int ptw = Math.round(font.width(ptsText) * SMALL);
        screen.drawSmallAt(g, ptsText, bx + bw / 2 - ptw / 2, by + (bh - SLH) / 2, ptColor);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public void mouseClicked(int mx, int my) {
        AbilityScore[] scores = AbilityScore.values();
        int unspent   = ClientStatsManager.getUnspentPoints() - pendingTotal();

        int x         = screen.contentX;
        int y         = screen.contentY;
        int w         = (int)(screen.contentW * 0.55f);
        int plusX     = x + w - PAD - BTN_W - 2;
        int minusX    = plusX - BTN_W - BTN_GAP;
        int headerY   = y + HDR_H + 2;
        int rowStartY = headerY + SLH + 4;

        for (int i = 0; i < scores.length; i++) {
            AbilityScore score = scores[i];
            int rowY = rowStartY + i * ROW_H;
            int bY   = rowY + (ROW_H - BTN_H) / 2;

            // Row click → select
            if (screen.inB(mx, my, x + PAD, rowY, minusX - x - PAD - 4, ROW_H)) {
                selectedScore = score;
                screen.click();
                return;
            }

            // Minus
            if (pending[i] > 0 && screen.inB(mx, my, minusX, bY, BTN_W, BTN_H)) {
                pending[i]--;
                screen.click();
                return;
            }

            // Plus
            if (unspent > 0 && screen.inB(mx, my, plusX, bY, BTN_W, BTN_H)) {
                pending[i]++;
                selectedScore = score;
                screen.click();
                return;
            }
        }

        // Confirm
        if (confirmHovered && pendingTotal() > 0) {
            for (int i = 0; i < scores.length; i++)
                for (int j = 0; j < pending[i]; j++)
                    ClientPlayNetworking.send(new SpendAttributePointPayload(scores[i]));
            resetPending();
            screen.click();
            return;
        }

        // Reset
        if (resetHovered) {
            resetPending();
            screen.click();
        }
    }

    // ── Bonus rows ────────────────────────────────────────────────────────────

    private record BonusRow(String label, String current, String after) {}

    private BonusRow[] getBonusRows(AbilityScore score, int mod, int newMod) {
        return switch (score) {
            case STR -> new BonusRow[]{
                    new BonusRow("Melee Dmg",    "+" + (mod * 2) + "%",  "+" + (newMod * 2) + "%"),
                    new BonusRow("Carry Cap.",   String.valueOf(100 + mod * 10), String.valueOf(100 + newMod * 10)),
            };
            case DEX -> new BonusRow[]{
                    new BonusRow("Ranged Acc.",  "+" + (mod * 2) + "%", "+" + (newMod * 2) + "%"),
                    new BonusRow("Dodge Chance", "+" + mod + "%",        "+" + newMod + "%"),
            };
            case CON -> new BonusRow[]{
                    new BonusRow("Max Health",   "+" + (mod * 10), "+" + (newMod * 10)),
                    new BonusRow("HP Regen",     "+" + mod + "%",  "+" + newMod + "%"),
            };
            case END -> new BonusRow[]{
                    new BonusRow("Max Stamina",  "+" + (mod * 10), "+" + (newMod * 10)),
                    new BonusRow("Sprint Dur.",  "+" + mod + "%",  "+" + newMod + "%"),
            };
            case INT -> new BonusRow[]{
                    new BonusRow("Max Mana",     "+" + (mod * 10),      "+" + (newMod * 10)),
                    new BonusRow("Spell Power",  "+" + (mod * 2) + "%", "+" + (newMod * 2) + "%"),
            };
            case WIS -> new BonusRow[]{
                    new BonusRow("Skill XP",     "+" + (mod * 2) + "%", "+" + (newMod * 2) + "%"),
                    new BonusRow("Perception",   "+" + mod,              "+" + newMod),
            };
            case CHA -> new BonusRow[]{
                    new BonusRow("NPC Disp.",    "+" + (mod * 5) + "%",            "+" + (newMod * 5) + "%"),
                    new BonusRow("Barter",       "-" + Math.max(0, mod * 2) + "%", "-" + Math.max(0, newMod * 2) + "%"),
            };
            case FTH -> new BonusRow[]{
                    new BonusRow("Holy Power",   "+" + (mod * 2) + "%", "+" + (newMod * 2) + "%"),
                    new BonusRow("Dark Resist",  "+" + mod + "%",        "+" + newMod + "%"),
            };
        };
    }

    private String formatMod(int mod) {
        return mod >= 0 ? "+" + mod : String.valueOf(mod);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getScoreIcon(AbilityScore score) {
        return switch (score) {
            case STR -> "⚔";
            case DEX -> "◎";
            case CON -> "♥";
            case END -> "⚡";
            case INT -> "✦";
            case WIS -> "◈";
            case CHA -> "★";
            case FTH -> "✝";
        };
    }

    private int getScoreColor(AbilityScore score) {
        return switch (score) {
            case STR -> 0xFFCC4444;
            case DEX -> 0xFFCC8833;
            case CON -> 0xFF44AACC;
            case END -> 0xFF44CC88;
            case INT -> 0xFFAA44CC;
            case WIS -> 0xFF4488CC;
            case CHA -> 0xFFCCAA33;
            case FTH -> 0xFFCCCCCC;
        };
    }

    private String[] getScoreAffects(AbilityScore score) {
        return switch (score) {
            case STR -> new String[]{"Melee Weapon Damage", "Carry Capacity", "Shove & Grapple"};
            case DEX -> new String[]{"Ranged Accuracy", "Dodge Chance", "Stealth"};
            case CON -> new String[]{"Maximum Health", "Physical Resistance"};
            case END -> new String[]{"Maximum Stamina", "Sprint Duration"};
            case INT -> new String[]{"Maximum Mana", "Spell Power", "Item Identification"};
            case WIS -> new String[]{"Skill XP Rate", "Perception", "Magic Resistance"};
            case CHA -> new String[]{"NPC Disposition", "Persuasion", "Barter Prices"};
            case FTH -> new String[]{"Holy Ability Power", "Dark Resistance", "Deity Favor"};
        };
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int   COLOR_SEPARATOR = BaseCharacterScreen.COLOR_SEPARATOR;
    private static final int   COLOR_LABEL     = BaseCharacterScreen.COLOR_LABEL;
    private static final int   COLOR_VALUE     = BaseCharacterScreen.COLOR_VALUE;
    private static final int   COLOR_ACCENT    = BaseCharacterScreen.COLOR_ACCENT;
    private static final int   COLOR_BORDER    = BaseCharacterScreen.COLOR_BORDER;
    private static final int   COLOR_ROW_HOV   = BaseCharacterScreen.COLOR_ROW_HOV;
    private static final int   COLOR_GREEN     = BaseCharacterScreen.COLOR_GREEN;
    private static final int   COLOR_RED       = BaseCharacterScreen.COLOR_RED;
    private static final int   COLOR_COPPER    = BaseCharacterScreen.COLOR_COPPER;
    private static final int   COLOR_PANEL_BG  = BaseCharacterScreen.COLOR_PANEL_BG;
    private static final int   LEFT_W          = BaseCharacterScreen.LEFT_W;
    private static final int   HDR_H           = BaseCharacterScreen.HDR_H;
    private static final int   PAD             = BaseCharacterScreen.PAD;
    private static final int   NLH             = BaseCharacterScreen.NLH;
    private static final int   SLH             = BaseCharacterScreen.SLH;
    private static final float SMALL           = BaseCharacterScreen.SMALL;
}