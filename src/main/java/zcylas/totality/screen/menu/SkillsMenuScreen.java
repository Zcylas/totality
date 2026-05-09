package zcylas.totality.screen.menu;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.api.rpg.skills.core.*;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.skills.UnlockMasteryPayload;
import zcylas.totality.networking.stamina.ClientStaminaManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Skills screen — vertical mastery tree with centered line.
 * Same-level masteries appear on the same row, nodes centered on a vertical line.
 * Bottom panel: skill info + separator + selected mastery details.
 */
public class SkillsMenuScreen extends Screen {

    private static final int COLOR_BG                 = 0xEE000814;
    private static final int COLOR_BORDER             = 0xFF0A5070;
    private static final int COLOR_BORDER_GLOW        = 0x440A8FBF;
    private static final int COLOR_VALUE              = 0xFF00CCFF;
    private static final int COLOR_LABEL              = 0xFF5599BB;
    private static final int COLOR_SEPARATOR          = 0xFF0A3A5A;
    private static final int COLOR_NODE_LOCKED        = 0xFF1A2A3A;
    private static final int COLOR_NODE_BORDER_LOCKED = 0xFF2A4A5A;
    private static final int COLOR_NODE_BORDER_ACTIVE = 0xFF00CCFF;
    private static final int COLOR_NODE_UNLOCKED      = 0xFF003355;
    private static final int COLOR_NODE_MAX           = 0xFF005577;
    private static final int COLOR_LINE               = 0xFF2A4A5A;
    private static final int COLOR_LINE_UNLOCKED      = 0xFF00CCFF;
    private static final int COLOR_XP_BG              = 0xFF001830;
    private static final int COLOR_XP_FILL            = 0xFF0066AA;
    private static final int COLOR_HP_BG              = 0xFF3A0000;
    private static final int COLOR_HP_FILL            = 0xFFDD3333;
    private static final int COLOR_STA_BG             = 0xFF003A00;
    private static final int COLOR_STA_FILL           = 0xFF33BB33;
    private static final int COLOR_MAN_BG             = 0xFF00003A;
    private static final int COLOR_MAN_FILL           = 0xFF3355DD;
    private static final int COLOR_BTN                = 0xFF003355;
    private static final int COLOR_BTN_HOVER          = 0xFF0066AA;
    private static final int COLOR_BTN_LOCKED         = 0xFF1A2A3A;
    private static final int COLOR_POPUP_BG           = 0xEE000C1A;
    private static final int COLOR_YES                = 0xFF44BB44;
    private static final int COLOR_YES_HOV            = 0xFF66DD66;
    private static final int COLOR_NO                 = 0xFFBB4444;
    private static final int COLOR_NO_HOV             = 0xFFDD6666;
    private static final int COLOR_ROW_SEL            = 0x220A5070;
    private static final int COLOR_ROW_HOV            = 0x110A3050;

    // Layout
    private static final int NODE_R       = 12;  // node radius
    private static final int NODE_R_SEL   = 14; // selected node radius
    private static final int ROW_H        = 26; // height per mastery row
    private static final int ROW_SPACING  = 4;  // extra gap between level groups
    private static final int INFO_H       = 155;
    private static final int RES_BAR_H    = 5;
    private static final int RES_BAR_SEC  = 22;
    private static final int PADDING      = 12;

    // Scroll
    private int scrollOffset = 0; // in pixels
    private static final int SCROLL_SPEED = ROW_H;

    private final Skill[] skills = Skill.values();
    private int skillIndex        = 0;
    private int selectedNodeIndex = -1; // flat index into nodePositions
    private int hoveredNodeIndex  = -1;

    private boolean showConfirm   = false;
    private int confirmMasteryIdx = -1;

    /** One entry per mastery, with screen position. */
    private static class NodePos {
        int cx, cy;      // center of node on screen
        int masteryIndex; // index into MasteryRegistry list
        int row;         // which row group (level group)
        NodePos(int cx, int cy, int mi, int row) {
            this.cx = cx; this.cy = cy; this.masteryIndex = mi; this.row = row;
        }
    }
    private final List<NodePos> nodePositions = new ArrayList<>();
    // Total height of the tree (for scroll bounds)
    private int treeHeight = 0;

    private float alpha = 0f;
    private boolean fadingOut = false;
    private Runnable onFadeOutDone = null;

    private int btnX, btnY, btnW = 90, btnH = 14;
    private boolean btnCanUnlock = false;
    private int popYesX, popYesY, popNoX, popNoY;
    private static final int POP_BTN_W = 50, POP_BTN_H = 14;

    public SkillsMenuScreen() {
        super(Component.literal("Skills"));
    }

    @Override
    protected void init() {
        super.init();
        alpha = 0f; fadingOut = false; showConfirm = false;
        scrollOffset = 0;
        computeNodePositions();
        int treeAreaH = height - INFO_H - PADDING * 2;
        scrollOffset = Math.max(0, treeHeight - treeAreaH);
    }

    private Skill currentSkill() { return skills[skillIndex]; }

    // ── Node positions ────────────────────────────────────────────────────────

    /**
     * Builds the vertical tree layout.
     * Groups masteries by required level — same level = same row.
     * Rows ordered top (level 0) to bottom (level 100).
     * Vertical center line runs through screen center X.
     * Nodes on a row are spaced horizontally around center X.
     */
    private void computeNodePositions() {
        nodePositions.clear();
        List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
        if (masteries.isEmpty()) return;

        // Group by required level
        List<Integer> levels = new ArrayList<>();
        List<List<Integer>> groups = new ArrayList<>();
        for (int i = 0; i < masteries.size(); i++) {
            int req = masteries.get(i).getBaseRequiredLevel();
            int gi = levels.indexOf(req);
            if (gi < 0) {
                levels.add(req);
                groups.add(new ArrayList<>());
                gi = groups.size() - 1;
            }
            groups.get(gi).add(i);
        }

        // Sort ascending by level
        List<int[]> sorted = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++)
            sorted.add(new int[]{ levels.get(g), g });
        sorted.sort((a, b) -> Integer.compare(a[0], b[0]));
        java.util.Collections.reverse(sorted); // level 100 at top, level 0 at bottom

        int cx = width / 2;
        int startY = 10; // top of tree (relative, before scroll)
        int y = startY;

        for (int gi = 0; gi < sorted.size(); gi++) {
            List<Integer> group = groups.get(sorted.get(gi)[1]);
            int nc = group.size();
            // Node spacing — fit nodes around center
            int nodeSpacing = 70;
            int totalW = (nc - 1) * nodeSpacing;
            int rowCenterY = y + ROW_H / 2;

            for (int n = 0; n < nc; n++) {
                int nx = nc == 1 ? cx : cx - totalW / 2 + n * nodeSpacing;
                nodePositions.add(new NodePos(nx, rowCenterY, group.get(n), gi));
            }

            y += ROW_H;
            // Extra gap between level groups
            if (gi < sorted.size() - 1) y += ROW_SPACING;
        }

        treeHeight = y;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, withAlpha(COLOR_BG, (int)(alpha * 0xEE)));
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

        int treeTop    = PADDING;
        int treeBottom = height - INFO_H - PADDING;
        int treeAreaH  = treeBottom - treeTop;
        int offsetY    = treeTop - scrollOffset; // screen Y of tree top

        // Update hover
        if (!showConfirm) {
            hoveredNodeIndex = -1;
            for (int i = 0; i < nodePositions.size(); i++) {
                NodePos np = nodePositions.get(i);
                int screenY = np.cy + offsetY;
                int r = NODE_R_SEL + 4;
                if (Math.abs(mx - np.cx) <= r && Math.abs(my - screenY) <= r
                        && screenY >= treeTop && screenY <= treeBottom) {
                    hoveredNodeIndex = i; break;
                }
            }
        }

        // Clip to tree area — draw background for tree region
        g.fill(0, treeTop, width, treeBottom, withAlpha(0x00000000, ba)); // transparent

        drawTree(g, offsetY, treeTop, treeBottom, ba);
        drawInfoPanel(g, mx, my, ba);
        if (showConfirm) drawConfirmPopup(g, mx, my, ba);
    }

    private void drawTree(GuiGraphicsExtractor g, int offsetY, int treeTop, int treeBottom, int ba) {
        List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
        if (masteries.isEmpty()) return;

        int cx = width / 2;

        // Draw vertical center line through entire tree
        int lineTop    = nodePositions.get(0).cy + offsetY;
        int lineBottom = nodePositions.get(nodePositions.size()-1).cy + offsetY;
        lineTop    = Math.max(lineTop,    treeTop);
        lineBottom = Math.min(lineBottom, treeBottom);
        g.fill(cx-1, lineTop, cx+1, lineBottom, withAlpha(COLOR_LINE, ba));

        // Draw row highlights and horizontal lines
        for (int i = 0; i < nodePositions.size(); i++) {
            NodePos np = nodePositions.get(i);
            int screenY = np.cy + offsetY;
            if (screenY < treeTop || screenY > treeBottom) continue;

            boolean sel = i == selectedNodeIndex;
            boolean hov = i == hoveredNodeIndex;

            // Row highlight
            if (sel || hov) {
                g.fill(0, screenY - ROW_H/2, width, screenY + ROW_H/2,
                        withAlpha(sel ? COLOR_ROW_SEL : COLOR_ROW_HOV, ba));
            }

            // Horizontal line connecting nodes on same row to center line
            if (np.cx != cx) {
                int rank = ClientMasteriesManager.getUnlockedRank(masteries.get(np.masteryIndex).getId());
                int lc = withAlpha(rank > 0 ? COLOR_LINE_UNLOCKED : COLOR_LINE, ba);
                int x1 = Math.min(np.cx, cx), x2 = Math.max(np.cx, cx);
                g.fill(x1, screenY-1, x2, screenY+1, lc);
            }
        }

        // Draw nodes
        for (int i = 0; i < nodePositions.size(); i++) {
            NodePos np = nodePositions.get(i);
            int screenY = np.cy + offsetY;
            if (screenY < treeTop - NODE_R_SEL || screenY > treeBottom + NODE_R_SEL) continue;

            Mastery mastery = masteries.get(np.masteryIndex);
            int rank    = ClientMasteriesManager.getUnlockedRank(mastery.getId());
            boolean sel = i == selectedNodeIndex;
            boolean hov = i == hoveredNodeIndex;
            boolean max = rank >= mastery.getRankCount();
            int r = (sel || hov) ? NODE_R_SEL : NODE_R;
            int dimAlpha = (selectedNodeIndex >= 0 && !sel && !hov) ? ba * 2 / 3 : ba;

            // Glow
            if (sel)
                g.fill(np.cx-r-3, screenY-r-3, np.cx+r+3, screenY+r+3,
                        withAlpha(COLOR_BORDER_GLOW, ba/2));

            // Fill
            g.fill(np.cx-r, screenY-r, np.cx+r, screenY+r, withAlpha(
                    max ? COLOR_NODE_MAX : rank > 0 ? COLOR_NODE_UNLOCKED : COLOR_NODE_LOCKED, dimAlpha));

            // Border
            int bc = withAlpha(rank > 0 ? COLOR_NODE_BORDER_ACTIVE : COLOR_NODE_BORDER_LOCKED, ba);
            g.fill(np.cx-r,   screenY-r,   np.cx+r, screenY-r+1, bc);
            g.fill(np.cx-r,   screenY+r-1, np.cx+r, screenY+r,   bc);
            g.fill(np.cx-r,   screenY-r,   np.cx-r+1, screenY+r, bc);
            g.fill(np.cx+r-1, screenY-r,   np.cx+r,   screenY+r, bc);

            // Rank text inside node
            String rankStr = mastery.getRankCount() > 1
                    ? rank + "/" + mastery.getRankCount()
                    : (rank > 0 ? "✓" : "○");
            g.text(font, Component.literal(rankStr),
                    np.cx - font.width(rankStr)/2, screenY - 4,
                    withAlpha(rank > 0 ? COLOR_VALUE : COLOR_LABEL, dimAlpha), false);

            // Mastery name — to the right of rightmost node on this row,
            // or to the left if it would overflow
            boolean isRightmost = i == nodePositions.size()-1 || nodePositions.get(i+1).row != np.row;
            if (isRightmost) {
                // Collect all names on this row
                int rowStart = i;
                while (rowStart > 0 && nodePositions.get(rowStart-1).row == np.row) rowStart--;
                int nameX = np.cx + r + 6;
                int nameY = screenY - 4;
                for (int j = rowStart; j <= i; j++) {
                    Mastery m = masteries.get(nodePositions.get(j).masteryIndex);
                    int mr = ClientMasteriesManager.getUnlockedRank(m.getId());
                    boolean msel = j == selectedNodeIndex || j == hoveredNodeIndex;
                    int nc = withAlpha(msel ? COLOR_VALUE : mr > 0 ? COLOR_VALUE : COLOR_LABEL,
                            (selectedNodeIndex >= 0 && !msel) ? ba*2/3 : ba);
                    String nm = m.getName();
                    // Truncate if too long
                    int maxW = width - nameX - PADDING;
                    if (font.width(nm) > maxW) nm = truncate(nm, maxW);
                    g.text(font, Component.literal(nm), nameX, nameY + (j - rowStart) * 10, nc, false);
                }
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            String up = "▲";
            g.text(font, Component.literal(up), width/2 - font.width(up)/2, treeTop + 2,
                    withAlpha(COLOR_LABEL, ba), false);
        }
        int maxScroll = Math.max(0, treeHeight - (treeBottom - treeTop));
        if (scrollOffset < maxScroll) {
            String dn = "▼";
            g.text(font, Component.literal(dn), width/2 - font.width(dn)/2, treeBottom - 10,
                    withAlpha(COLOR_LABEL, ba), false);
        }
    }

    // ── Info panel ────────────────────────────────────────────────────────────

    private void drawInfoPanel(GuiGraphicsExtractor g, int mx, int my, int ba) {
        int panelY = height - INFO_H;
        g.fill(0, panelY, width, height, withAlpha(0xDD000C1A, ba));
        g.fill(0, panelY, width, panelY+1, withAlpha(COLOR_BORDER, ba));

        Skill skill = currentSkill();
        int cy = panelY + PADDING;

        // Skill name
        String arrow = "◄  " + skill.getDisplayName().toUpperCase() + "  ►";
        g.text(font, Component.literal(arrow),
                width/2-font.width(arrow)/2, cy,
                withAlpha(skill.getCategoryColor(), ba), true);
        cy += 13;

        // Level + XP bar
        int sl = ClientSkillsManager.getLevel(skill);
        int sx = ClientSkillsManager.getXp(skill);
        int sr = ClientSkillsManager.getXpRequired(skill);
        String lvl = "LEVEL " + sl;
        g.text(font, Component.literal(lvl),
                width/2-font.width(lvl)/2, cy, withAlpha(COLOR_VALUE, ba), true);
        cy += 11;
        int xpW = 180, xpX = width/2-xpW/2;
        g.fill(xpX, cy, xpX+xpW, cy+4, withAlpha(COLOR_XP_BG, ba));
        if (sr > 0 && sx > 0)
            g.fill(xpX, cy, xpX+Math.min((int)((float)sx/sr*xpW),xpW), cy+4, withAlpha(COLOR_XP_FILL, ba));
        String xpStr = sx+" / "+sr+" XP";
        g.text(font, Component.literal(xpStr),
                width/2-font.width(xpStr)/2, cy+5, withAlpha(COLOR_LABEL, ba), false);
        cy += 16;

        // Skill description
        // Skill description — up to 2 lines
        String desc = skill.getDescription();
        int maxDescW = width - PADDING*6;
        if (font.width(desc) <= maxDescW) {
            g.text(font, Component.literal(desc), width/2-font.width(desc)/2, cy, withAlpha(COLOR_LABEL, ba), false);
            cy += 10;
        } else {
            int cut = desc.length();
            while (cut > 0 && font.width(desc.substring(0, cut)) > maxDescW) cut--;
            int sp = desc.lastIndexOf(' ', cut);
            String l1 = sp > 0 ? desc.substring(0, sp) : truncate(desc, maxDescW);
            String l2 = sp > 0 ? truncate(desc.substring(sp+1), maxDescW) : "";
            g.text(font, Component.literal(l1), width/2-font.width(l1)/2, cy, withAlpha(COLOR_LABEL, ba), false);
            cy += 10;
            if (!l2.isEmpty()) {
                g.text(font, Component.literal(l2), width/2-font.width(l2)/2, cy, withAlpha(COLOR_LABEL, ba), false);
                cy += 10;
            }
        }

        // Separator
        g.fill(PADDING*3, cy, width-PADDING*3, cy+1, withAlpha(COLOR_SEPARATOR, ba));
        cy += 7;

        // Mastery details
        int displayIdx = selectedNodeIndex >= 0 ? selectedNodeIndex
                : hoveredNodeIndex >= 0 ? hoveredNodeIndex : -1;
        List<Mastery> masteries = MasteryRegistry.getMasteries(skill);

        if (displayIdx >= 0 && displayIdx < nodePositions.size()) {
            Mastery mastery = masteries.get(nodePositions.get(displayIdx).masteryIndex);
            int rank = ClientMasteriesManager.getUnlockedRank(mastery.getId());
            int nextRank = Math.min(rank+1, mastery.getRankCount());
            int reqLevel = mastery.getRequiredLevelForRank(nextRank);
            boolean fullyUnlocked = rank >= mastery.getRankCount();
            boolean canUnlock = !fullyUnlocked
                    && ClientMasteriesManager.getMasteryPoints() > 0
                    && sl >= reqLevel;

            // Name + rank
            String mn = mastery.getName() + (mastery.getRankCount() > 1
                    ? "  (" + rank + "/" + mastery.getRankCount() + ")" : "");
            g.text(font, Component.literal(mn),
                    width/2-font.width(mn)/2, cy,
                    withAlpha(rank > 0 ? COLOR_VALUE : COLOR_LABEL, ba), true);
            cy += 11;

            // Description — up to 2 lines
            String md = mastery.getDescriptionForRank(nextRank);
            int maxW = width - PADDING*4;
            if (font.width(md) <= maxW) {
                g.text(font, Component.literal(md), width/2-font.width(md)/2, cy, withAlpha(COLOR_LABEL, ba), false);
                cy += 10;
            } else {
                int cut = md.length();
                while (cut > 0 && font.width(md.substring(0, cut)) > maxW) cut--;
                int sp = md.lastIndexOf(' ', cut);
                String l1 = sp > 0 ? md.substring(0, sp) : truncate(md, maxW);
                String l2 = sp > 0 ? truncate(md.substring(sp+1), maxW) : "";
                g.text(font, Component.literal(l1), width/2-font.width(l1)/2, cy, withAlpha(COLOR_LABEL, ba), false);
                cy += 10;
                if (!l2.isEmpty()) {
                    g.text(font, Component.literal(l2), width/2-font.width(l2)/2, cy, withAlpha(COLOR_LABEL, ba), false);
                    cy += 10;
                }
            }

            // Requirement
            if (fullyUnlocked) {
                g.text(font, Component.literal("✓ Mastered"),
                        width/2-font.width("✓ Mastered")/2, cy, withAlpha(COLOR_VALUE, ba), false);
            } else {
                String req = "Skill Level Requirement: " + reqLevel;
                g.text(font, Component.literal(req),
                        width/2-font.width(req)/2, cy, withAlpha(COLOR_LABEL, ba), false);
            }

            // Unlock button
            btnCanUnlock = canUnlock;
            btnX = width - PADDING - btnW;
            btnY = panelY + PADDING;
            boolean btnHov = inBounds(mx, my, btnX, btnY, btnW, btnH);
            int btnBg = withAlpha(fullyUnlocked ? 0xFF001830
                    : canUnlock ? (btnHov ? COLOR_BTN_HOVER : COLOR_BTN) : COLOR_BTN_LOCKED, ba);
            g.fill(btnX, btnY, btnX+btnW, btnY+btnH, btnBg);
            g.fill(btnX,        btnY,        btnX+btnW, btnY+1,       withAlpha(COLOR_BORDER, ba));
            g.fill(btnX,        btnY+btnH-1, btnX+btnW, btnY+btnH,    withAlpha(COLOR_BORDER, ba));
            g.fill(btnX,        btnY,        btnX+1,    btnY+btnH,    withAlpha(COLOR_BORDER, ba));
            g.fill(btnX+btnW-1, btnY,        btnX+btnW, btnY+btnH,    withAlpha(COLOR_BORDER, ba));
            String btnLbl = fullyUnlocked ? "✓ Mastered" : canUnlock ? "[E] Unlock" : "Locked";
            g.text(font, Component.literal(btnLbl),
                    btnX+btnW/2-font.width(btnLbl)/2, btnY+(btnH-8)/2,
                    withAlpha(canUnlock ? COLOR_VALUE : COLOR_LABEL, ba), false);
        } else {
            g.text(font, Component.literal("Select a mastery node to view details"),
                    width/2-font.width("Select a mastery node to view details")/2, cy,
                    withAlpha(COLOR_LABEL, ba), false);
        }

        // Mastery points + hints
        g.text(font, Component.literal("Mastery Points: " + ClientMasteriesManager.getMasteryPoints()),
                PADDING, height-RES_BAR_SEC-10, withAlpha(COLOR_VALUE, ba), false);
        String hint = "[A/D] Cycle   [ESC] Back";
        g.text(font, Component.literal(hint),
                width-PADDING-font.width(hint), height-RES_BAR_SEC-10, withAlpha(COLOR_LABEL, ba), false);

        // Resource bars
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int barW = (width-PADDING*6)/3;
            int barY = height-RES_BAR_SEC+4;
            float hp = player.getHealth(), maxHp = player.getMaxHealth();
            drawResBar(g, PADDING, barY, barW, "❤ HP", hp, maxHp,
                    RpgDisplayUtils.toDisplayHp(hp), RpgDisplayUtils.toDisplayHp(maxHp), COLOR_HP_BG, COLOR_HP_FILL, ba);
            int sta = ClientStaminaManager.getStamina(), maxSta = ClientStaminaManager.getMaxStamina();
            drawResBar(g, PADDING*2+barW, barY, barW, "⚡ STAMINA", sta, maxSta, sta, maxSta, COLOR_STA_BG, COLOR_STA_FILL, ba);
            int man = ClientManaManager.getMana(), maxMan = ClientManaManager.getMaxMana();
            drawResBar(g, PADDING*3+barW*2, barY, barW, "✨ MANA", man, maxMan, man, maxMan, COLOR_MAN_BG, COLOR_MAN_FILL, ba);
        }
    }

    private void drawResBar(GuiGraphicsExtractor g, int x, int y, int w,
                            String label, float value, float max,
                            int dispCur, int dispMax, int bgColor, int fillColor, int ba) {
        g.text(font, Component.literal(label), x, y, withAlpha(COLOR_LABEL, ba), false);
        int lw = font.width(label)+4;
        String val = dispCur+"/"+dispMax;
        g.text(font, Component.literal(val), x+w-font.width(val), y, withAlpha(COLOR_VALUE, ba), false);
        int bx = x+lw, bw = w-lw-font.width(val)-4, by = y+1;
        g.fill(bx, by, bx+bw, by+RES_BAR_H, withAlpha(bgColor, ba));
        if (max > 0 && value > 0) {
            int fill = (int)(Math.min(value/max,1f)*bw);
            if (fill > 0) g.fill(bx, by, bx+fill, by+RES_BAR_H, withAlpha(fillColor, ba));
        }
    }

    private void drawConfirmPopup(GuiGraphicsExtractor g, int mx, int my, int ba) {
        if (confirmMasteryIdx < 0 || confirmMasteryIdx >= nodePositions.size()) return;
        List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
        Mastery mastery = masteries.get(nodePositions.get(confirmMasteryIdx).masteryIndex);

        int popW = 220, popH = 70;
        int px = width/2-popW/2, py = height/2-popH/2;

        g.fill(px-2, py-2, px+popW+2, py+popH+2, withAlpha(COLOR_BORDER_GLOW, ba/2));
        g.fill(px, py, px+popW, py+popH, withAlpha(COLOR_POPUP_BG, ba));
        g.fill(px,        py,        px+popW, py+1,    withAlpha(COLOR_BORDER, ba));
        g.fill(px,        py+popH-1, px+popW, py+popH, withAlpha(COLOR_BORDER, ba));
        g.fill(px,        py,        px+1,    py+popH, withAlpha(COLOR_BORDER, ba));
        g.fill(px+popW-1, py,        px+popW, py+popH, withAlpha(COLOR_BORDER, ba));

        String title = "Unlock " + mastery.getName() + "?";
        g.text(font, Component.literal(title),
                px+popW/2-font.width(title)/2, py+10, withAlpha(COLOR_VALUE, ba), true);
        g.text(font, Component.literal("Cost: 1 Mastery Point"),
                px+popW/2-font.width("Cost: 1 Mastery Point")/2, py+23, withAlpha(COLOR_LABEL, ba), false);

        popYesX = px+popW/2-POP_BTN_W-8; popYesY = py+popH-POP_BTN_H-10;
        boolean yesHov = inBounds(mx, my, popYesX, popYesY, POP_BTN_W, POP_BTN_H);
        drawPopBtn(g, popYesX, popYesY, "[Y] Yes", COLOR_YES, yesHov ? COLOR_YES_HOV : COLOR_YES, ba);

        popNoX = px+popW/2+8; popNoY = popYesY;
        boolean noHov = inBounds(mx, my, popNoX, popNoY, POP_BTN_W, POP_BTN_H);
        drawPopBtn(g, popNoX, popNoY, "[N] No", COLOR_NO, noHov ? COLOR_NO_HOV : COLOR_NO, ba);
    }

    private void drawPopBtn(GuiGraphicsExtractor g, int x, int y, String label,
                            int borderColor, int textColor, int ba) {
        g.fill(x, y, x+POP_BTN_W, y+POP_BTN_H, withAlpha(0xFF111111, ba));
        g.fill(x,             y,              x+POP_BTN_W, y+1,           withAlpha(borderColor, ba));
        g.fill(x,             y+POP_BTN_H-1,  x+POP_BTN_W, y+POP_BTN_H, withAlpha(borderColor, ba));
        g.fill(x,             y,              x+1,          y+POP_BTN_H, withAlpha(borderColor, ba));
        g.fill(x+POP_BTN_W-1, y,             x+POP_BTN_W, y+POP_BTN_H, withAlpha(borderColor, ba));
        g.text(font, Component.literal(label),
                x+POP_BTN_W/2-font.width(label)/2, y+(POP_BTN_H-8)/2,
                withAlpha(textColor, ba), false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int treeBottom = height - INFO_H - PADDING;
        int treeAreaH  = treeBottom - PADDING;
        int maxScroll  = Math.max(0, treeHeight - treeAreaH);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * SCROLL_SPEED));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        if (showConfirm) {
            if (inBounds(mx, my, popYesX, popYesY, POP_BTN_W, POP_BTN_H)) { confirmUnlock(); return true; }
            if (inBounds(mx, my, popNoX,  popNoY,  POP_BTN_W, POP_BTN_H)) { showConfirm = false; playClick(); return true; }
            return true;
        }

        int offsetY = PADDING - scrollOffset;
        for (int i = 0; i < nodePositions.size(); i++) {
            NodePos np = nodePositions.get(i);
            int screenY = np.cy + offsetY;
            int r = NODE_R_SEL + 4;
            if (Math.abs(mx-np.cx) <= r && Math.abs(my-screenY) <= r) {
                if (selectedNodeIndex == i) showUnlockConfirm(i);
                else { selectedNodeIndex = i; playClick(); }
                return true;
            }
        }

        if (btnCanUnlock && inBounds(mx, my, btnX, btnY, btnW, btnH)) {
            showUnlockConfirm(selectedNodeIndex >= 0 ? selectedNodeIndex : hoveredNodeIndex);
            return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();

        if (showConfirm) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y || key == org.lwjgl.glfw.GLFW.GLFW_KEY_E) { confirmUnlock(); return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_N || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { showConfirm = false; playClick(); return true; }
            return true;
        }

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
            skillIndex = (skillIndex-1+skills.length)%skills.length;
            selectedNodeIndex = -1; scrollOffset = 0; computeNodePositions(); playClick(); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_D) {
            skillIndex = (skillIndex+1)%skills.length;
            selectedNodeIndex = -1; scrollOffset = 0; computeNodePositions(); playClick(); return true;
        }
        // W = up = lower index (toward top of list = lower level)
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
            if (!nodePositions.isEmpty()) {
                selectedNodeIndex = Math.max(0, selectedNodeIndex <= 0 ? 0 : selectedNodeIndex-1);
                scrollToSelected();
            }
            playClick(); return true;
        }
        // S = down = higher index (toward bottom = higher level)
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            if (!nodePositions.isEmpty()) {
                selectedNodeIndex = Math.min(nodePositions.size()-1,
                        selectedNodeIndex < 0 ? 0 : selectedNodeIndex+1);
                scrollToSelected();
            }
            playClick(); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_E) {
            int idx = selectedNodeIndex >= 0 ? selectedNodeIndex : hoveredNodeIndex >= 0 ? hoveredNodeIndex : -1;
            if (idx >= 0) showUnlockConfirm(idx);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            fadeOutTo(() -> Minecraft.getInstance().setScreen(new CharacterMenuScreen()));
            return true;
        }

        return super.keyPressed(event);
    }

    /** Scrolls so the selected node is visible. */
    private void scrollToSelected() {
        if (selectedNodeIndex < 0 || selectedNodeIndex >= nodePositions.size()) return;
        int treeBottom = height - INFO_H - PADDING;
        int treeAreaH  = treeBottom - PADDING;
        int maxScroll  = Math.max(0, treeHeight - treeAreaH);
        int nodeY = nodePositions.get(selectedNodeIndex).cy;
        // Keep node in middle of view
        int targetScroll = nodeY - treeAreaH / 2;
        scrollOffset = Math.max(0, Math.min(maxScroll, targetScroll));
    }

    private void showUnlockConfirm(int idx) {
        if (idx < 0 || idx >= nodePositions.size()) return;
        List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
        Mastery mastery = masteries.get(nodePositions.get(idx).masteryIndex);
        int rank = ClientMasteriesManager.getUnlockedRank(mastery.getId());
        if (rank >= mastery.getRankCount()) return;
        int reqLevel = mastery.getRequiredLevelForRank(rank+1);
        if (ClientMasteriesManager.getMasteryPoints() <= 0) return;
        if (ClientSkillsManager.getLevel(currentSkill()) < reqLevel) return;
        confirmMasteryIdx = idx;
        showConfirm = true;
        playClick();
    }

    private void confirmUnlock() {
        if (confirmMasteryIdx < 0 || confirmMasteryIdx >= nodePositions.size()) return;
        List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
        Mastery mastery = masteries.get(nodePositions.get(confirmMasteryIdx).masteryIndex);
        ClientPlayNetworking.send(new UnlockMasteryPayload(currentSkill(), mastery.getId()));
        showConfirm = false;
        playClick();
    }

    private void fadeOutTo(Runnable onDone) { fadingOut = true; onFadeOutDone = onDone; }

    private String truncate(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 3 && font.width(s+"...") > maxPx) s = s.substring(0, s.length()-1);
        return s+"...";
    }

    private boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private int withAlpha(int color, int alpha) {
        return ((((color >> 24) & 0xFF) * alpha / 255) << 24) | (color & 0x00FFFFFF);
    }

    @Override public boolean isInGameUi()    { return false; }
    @Override public boolean isPauseScreen() { return false; }
}