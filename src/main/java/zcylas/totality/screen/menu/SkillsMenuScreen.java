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
 * Skills screen — two states:
 *   State 1: Skill overview — large icon, name, level, XP, description. A/D cycles skills.
 *   State 2: Mastery panel — left list + right details. W/S navigates, E unlocks.
 */
public class SkillsMenuScreen extends Screen {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int COLOR_BG              = 0xBB000000;
    private static final int COLOR_PANEL_BG        = 0xCC001020;
    private static final int COLOR_BORDER          = 0xFF0A5070;
    private static final int COLOR_BORDER_DIM      = 0xFF062840;
    private static final int COLOR_BORDER_GLOW     = 0x440A8FBF;
    private static final int COLOR_VALUE           = 0xFF00CCFF;
    private static final int COLOR_LABEL           = 0xFF5599BB;
    private static final int COLOR_SEPARATOR       = 0xFF062840;
    private static final int COLOR_UNLOCKED        = 0xFF44BB44;
    private static final int COLOR_XP_BG           = 0xFF111111;
    private static final int COLOR_XP_FILL         = 0xFF0066AA;
    private static final int COLOR_HP_BG           = 0xFF3A0000;
    private static final int COLOR_HP_FILL         = 0xFFDD3333;
    private static final int COLOR_STA_BG          = 0xFF003A00;
    private static final int COLOR_STA_FILL        = 0xFF33BB33;
    private static final int COLOR_MAN_BG          = 0xFF00003A;
    private static final int COLOR_MAN_FILL        = 0xFF3355DD;
    private static final int COLOR_BTN             = 0xFF001830;
    private static final int COLOR_BTN_HOVER       = 0xFF0A5070;
    private static final int COLOR_BTN_LOCKED      = 0xFF111111;
    private static final int COLOR_POPUP_BG        = 0xEE000000;
    private static final int COLOR_YES             = 0xFF44BB44;
    private static final int COLOR_YES_HOV         = 0xFF66DD66;
    private static final int COLOR_NO              = 0xFFBB4444;
    private static final int COLOR_NO_HOV          = 0xFFDD6666;
    private static final int COLOR_LIST_SEL        = 0x220A5070;
    private static final int COLOR_LIST_HOV        = 0x110A3050;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PADDING     = 12;
    private static final int BOTTOM_H    = 80;
    private static final int RES_BAR_H   = 5;
    private static final int RES_BAR_SEC = 22;
    private static final int ITEM_H      = 18;
    private static final int BTN_W       = 90;
    private static final int BTN_H       = 14;
    private static final int POP_BTN_W   = 50;
    private static final int POP_BTN_H   = 14;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Skill[] skills = Skill.values();
    private int skillIndex = 0;
    private boolean masteryPanelOpen = false;
    private int selectedMasteryIndex = 0;
    private int hoveredMasteryIndex  = -1;
    private int masteryScrollOffset  = 0;

    // Confirm popup
    private boolean showConfirm   = false;
    private int confirmMasteryIdx = -1;
    private int popYesX, popYesY, popNoX, popNoY;

    // Button bounds
    private int btnX, btnY;
    private boolean btnCanUnlock = false;

    // Fade
    private float alpha = 0f;
    private boolean fadingOut = false;
    private Runnable onFadeOutDone = null;

    public SkillsMenuScreen() {
        super(Component.literal("Skills"));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        alpha = 0f;
        fadingOut = false;
        showConfirm = false;
        masteryPanelOpen = false;
        selectedMasteryIndex = 0;
        masteryScrollOffset = 0;
    }

    private Skill currentSkill() { return skills[skillIndex]; }

    // ── Background ────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, withAlpha(COLOR_BG, (int)(alpha * 0xFF)));
    }

    // ── Main render ───────────────────────────────────────────────────────────

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

        if (!masteryPanelOpen) {
            drawSkillOverview(g, mx, my, ba);
        } else {
            drawMasteryPanel(g, mx, my, ba);
        }

        drawBottomStrip(g, mx, my, ba);

        if (showConfirm) drawConfirmPopup(g, mx, my, ba);
    }

    // ── State 1: Skill Overview ───────────────────────────────────────────────

    private void drawSkillOverview(GuiGraphicsExtractor g, int mx, int my, int ba) {
        Skill skill = currentSkill();
        int centerX = width / 2;
        int contentAreaH = height - BOTTOM_H;
        int cy = contentAreaH / 2 - 50;

        // Large skill icon — rendered at 2x scale (32x32)
        var icon = skill.getIconItem();
        if (icon != null) {
            g.pose().pushMatrix();
            g.pose().translate(centerX - 16f, (float)cy);
            g.pose().scale(2f, 2f);
            g.item(icon, 0, 0);
            g.pose().popMatrix();
            cy += 40;
        } else {
            cy += 10;
        }

        // Skill name with A/D arrows
        String arrow = "◄  " + skill.getDisplayName().toUpperCase() + "  ►";
        g.text(font, Component.literal(arrow),
                centerX - font.width(arrow)/2, cy,
                withAlpha(skill.getCategoryColor(), ba), true);
        cy += 14;

        // Level
        int sl = ClientSkillsManager.getLevel(skill);
        String lvl = "LEVEL " + sl;
        g.text(font, Component.literal(lvl),
                centerX - font.width(lvl)/2, cy,
                withAlpha(COLOR_VALUE, ba), true);
        cy += 12;

        // XP bar
        int sx = ClientSkillsManager.getXp(skill);
        int sr = ClientSkillsManager.getXpRequired(skill);
        int xpW = 200, xpX = centerX - xpW/2;
        g.fill(xpX, cy, xpX+xpW, cy+4, withAlpha(COLOR_XP_BG, ba));
        g.fill(xpX, cy, xpX+xpW, cy+1, withAlpha(COLOR_BORDER_DIM, ba));
        g.fill(xpX, cy+3, xpX+xpW, cy+4, withAlpha(COLOR_BORDER_DIM, ba));
        if (sr > 0 && sx > 0)
            g.fill(xpX, cy, xpX + Math.min((int)((float)sx/sr*xpW), xpW), cy+4,
                    withAlpha(COLOR_XP_FILL, ba));
        cy += 7;
        String xpStr = sx + " / " + sr + " XP";
        g.text(font, Component.literal(xpStr),
                centerX - font.width(xpStr)/2, cy,
                withAlpha(COLOR_LABEL, ba), false);
        cy += 14;

        // Separator
        g.fill(PADDING*4, cy, width-PADDING*4, cy+1, withAlpha(COLOR_SEPARATOR, ba));
        cy += 8;

        // Description — word wrapped, up to 3 lines
        String desc = skill.getDescription();
        int maxW = width - PADDING*8;
        List<String> lines = wrapText(desc, maxW);
        for (int i = 0; i < Math.min(lines.size(), 3); i++) {
            String line = lines.get(i);
            g.text(font, Component.literal(line),
                    centerX - font.width(line)/2, cy,
                    withAlpha(COLOR_LABEL, ba), false);
            cy += 10;
        }
        cy += 10;

        // Masteries hint
        List<Mastery> masteries = MasteryRegistry.getMasteries(skill);
        if (!masteries.isEmpty()) {
            String hint = "▼  " + masteries.size() + " Masteries  [S]";
            g.text(font, Component.literal(hint),
                    centerX - font.width(hint)/2, cy,
                    withAlpha(COLOR_LABEL, ba), false);
        }
    }

    // ── State 2: Mastery Panel ────────────────────────────────────────────────

    private void drawMasteryPanel(GuiGraphicsExtractor g, int mx, int my, int ba) {
        List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
        int contentH = height - BOTTOM_H;

        int panelX  = PADDING;
        int panelY  = PADDING;
        int panelW  = width - PADDING*2;
        int panelH  = contentH - PADDING*2;
        int divX    = panelX + panelW * 2/5;

        // Panel background + border
        g.fill(panelX, panelY, panelX+panelW, panelY+panelH, withAlpha(COLOR_PANEL_BG, ba));
        drawBorder(g, panelX, panelY, panelX+panelW, panelY+panelH, ba);
        // Divider
        g.fill(divX, panelY+PADDING, divX+1, panelY+panelH-PADDING, withAlpha(COLOR_BORDER_DIM, ba));

        // ── Left: list ────────────────────────────────────────────────────────
        int listX = panelX + PADDING;
        int listY = panelY + PADDING;
        int listW = divX - panelX - PADDING*2;
        int listH = panelH - PADDING*2;
        int visibleItems = listH / ITEM_H;

        int maxScroll = Math.max(0, masteries.size() - visibleItems);
        masteryScrollOffset = Math.max(0, Math.min(masteryScrollOffset, maxScroll));

        // Hover detection
        hoveredMasteryIndex = -1;
        for (int i = 0; i < visibleItems; i++) {
            int idx = i + masteryScrollOffset;
            if (idx >= masteries.size()) break;
            int rowY = listY + i * ITEM_H;
            if (mx >= listX && mx < divX - PADDING && my >= rowY && my < rowY + ITEM_H) {
                hoveredMasteryIndex = idx;
                break;
            }
        }

        // Draw list rows
        for (int i = 0; i < visibleItems; i++) {
            int idx = i + masteryScrollOffset;
            if (idx >= masteries.size()) break;
            Mastery m = masteries.get(idx);
            int rank = ClientMasteriesManager.getUnlockedRank(m.getId());
            boolean sel = idx == selectedMasteryIndex;
            boolean hov = idx == hoveredMasteryIndex;
            int rowY = listY + i * ITEM_H;

            if (sel)
                g.fill(listX-2, rowY, divX-PADDING, rowY+ITEM_H, withAlpha(COLOR_LIST_SEL, ba));
            else if (hov)
                g.fill(listX-2, rowY, divX-PADDING, rowY+ITEM_H, withAlpha(COLOR_LIST_HOV, ba));

            // Status indicator
            String rankStr = m.getRankCount() > 1
                    ? rank + "/" + m.getRankCount()
                    : (rank > 0 ? "✓" : "○");
            g.text(font, Component.literal(rankStr),
                    listX, rowY + (ITEM_H-8)/2,
                    withAlpha(rank > 0 ? COLOR_UNLOCKED : COLOR_LABEL, ba), false);

            // Name
            int rankW = font.width(rankStr) + 6;
            int nameX = listX + rankW;
            int nameColor = sel ? COLOR_VALUE : rank > 0 ? COLOR_UNLOCKED : 0xFFFFFFFF;
            String name = truncate(m.getName(), listW - font.width("○○") - 6);
            g.text(font, Component.literal(name),
                    nameX, rowY + (ITEM_H-8)/2,
                    withAlpha(nameColor, ba), false);
        }

        // Scroll indicators
        if (masteryScrollOffset > 0)
            g.text(font, Component.literal("▲"),
                    listX + listW/2 - font.width("▲")/2, listY,
                    withAlpha(COLOR_LABEL, ba), false);
        if (masteryScrollOffset < maxScroll)
            g.text(font, Component.literal("▼"),
                    listX + listW/2 - font.width("▼")/2, listY + listH - 8,
                    withAlpha(COLOR_LABEL, ba), false);

        // ── Right: details ────────────────────────────────────────────────────
        int detX = divX + PADDING;
        int detW = panelX + panelW - divX - PADDING*2;
        int detY = panelY + PADDING;
        int detH = panelH - PADDING*2;

        if (selectedMasteryIndex >= 0 && selectedMasteryIndex < masteries.size()) {
            Mastery mastery = masteries.get(selectedMasteryIndex);
            int rank = ClientMasteriesManager.getUnlockedRank(mastery.getId());
            int nextRank = Math.min(rank+1, mastery.getRankCount());
            int reqLevel = mastery.getRequiredLevelForRank(nextRank);
            boolean fullyUnlocked = rank >= mastery.getRankCount();
            int sl = ClientSkillsManager.getLevel(currentSkill());
            boolean canUnlock = !fullyUnlocked
                    && ClientMasteriesManager.getMasteryPoints() > 0
                    && sl >= reqLevel;

            int cy = detY;

            // Name
            String mn = mastery.getName()
                    + (mastery.getRankCount() > 1 ? "  (" + rank + "/" + mastery.getRankCount() + ")" : "");
            g.text(font, Component.literal(mn),
                    detX + detW/2 - font.width(mn)/2, cy,
                    withAlpha(rank > 0 ? COLOR_VALUE : 0xFFFFFFFF, ba), true);
            cy += 13;

            g.fill(detX, cy, detX+detW, cy+1, withAlpha(COLOR_BORDER_DIM, ba));
            cy += 7;

            // Description
            String desc = mastery.getDescriptionForRank(nextRank);
            for (String line : wrapText(desc, detW)) {
                g.text(font, Component.literal(line), detX, cy,
                        withAlpha(COLOR_LABEL, ba), false);
                cy += 10;
            }
            cy += 6;

            // Requirement
            if (fullyUnlocked) {
                g.text(font, Component.literal("✓ Mastered"),
                        detX, cy, withAlpha(COLOR_UNLOCKED, ba), false);
            } else {
                g.text(font, Component.literal("Required Level: " + reqLevel),
                        detX, cy, withAlpha(COLOR_LABEL, ba), false);
                cy += 10;
                g.text(font, Component.literal("Cost: 1 Mastery Point"),
                        detX, cy, withAlpha(COLOR_LABEL, ba), false);
            }

            // Unlock button
            btnX = panelX + panelW - PADDING - BTN_W;
            btnY = panelY + panelH - PADDING - BTN_H;
            btnCanUnlock = canUnlock;
            boolean btnHov = inBounds(mx, my, btnX, btnY, BTN_W, BTN_H);
            int btnBg = fullyUnlocked ? withAlpha(0xFF001830, ba)
                    : canUnlock ? withAlpha(btnHov ? COLOR_BTN_HOVER : COLOR_BTN, ba)
                    : withAlpha(COLOR_BTN_LOCKED, ba);
            g.fill(btnX, btnY, btnX+BTN_W, btnY+BTN_H, btnBg);
            drawBorder(g, btnX, btnY, btnX+BTN_W, btnY+BTN_H, ba);
            String btnLbl = fullyUnlocked ? "✓ Mastered"
                    : canUnlock ? "[E] Unlock" : "Locked";
            g.text(font, Component.literal(btnLbl),
                    btnX + BTN_W/2 - font.width(btnLbl)/2, btnY + (BTN_H-8)/2,
                    withAlpha(canUnlock ? COLOR_VALUE : COLOR_LABEL, ba), false);

        } else {
            String placeholder = "Select a mastery";
            g.text(font, Component.literal(placeholder),
                    detX + detW/2 - font.width(placeholder)/2, detY + detH/2 - 4,
                    withAlpha(COLOR_LABEL, ba), false);
        }
    }

    // ── Bottom strip ──────────────────────────────────────────────────────────

    private void drawBottomStrip(GuiGraphicsExtractor g, int mx, int my, int ba) {
        int panelY = height - BOTTOM_H;
        g.fill(0, panelY, width, height, withAlpha(COLOR_PANEL_BG, ba));
        g.fill(0, panelY, width, panelY+1, withAlpha(COLOR_BORDER, ba));
        g.fill(0, panelY+1, width, panelY+2, withAlpha(COLOR_BORDER_DIM, ba/2));

        Skill skill = currentSkill();
        int cy = panelY + PADDING;

        // Skill name
        String arrow = "◄  " + skill.getDisplayName().toUpperCase() + "  ►";
        g.text(font, Component.literal(arrow),
                width/2 - font.width(arrow)/2, cy,
                withAlpha(skill.getCategoryColor(), ba), true);
        cy += 12;

        // Level
        int sl = ClientSkillsManager.getLevel(skill);
        int sx = ClientSkillsManager.getXp(skill);
        int sr = ClientSkillsManager.getXpRequired(skill);
        String lvl = "LEVEL " + sl;
        g.text(font, Component.literal(lvl),
                width/2 - font.width(lvl)/2, cy,
                withAlpha(COLOR_VALUE, ba), false);
        cy += 10;

        // Compact XP bar
        int xpW = 160, xpX = width/2 - xpW/2;
        g.fill(xpX, cy, xpX+xpW, cy+3, withAlpha(COLOR_XP_BG, ba));
        if (sr > 0 && sx > 0)
            g.fill(xpX, cy, xpX + Math.min((int)((float)sx/sr*xpW), xpW), cy+3,
                    withAlpha(COLOR_XP_FILL, ba));
        cy += 6;

        // Points + hints
        g.text(font, Component.literal("Points: " + ClientMasteriesManager.getMasteryPoints()),
                PADDING, cy, withAlpha(COLOR_VALUE, ba), false);
        String hint = masteryPanelOpen
                ? "[W/S] Navigate   [ESC] Back"
                : "[A/D] Cycle   [S] Masteries   [ESC] Back";
        g.text(font, Component.literal(hint),
                width - PADDING - font.width(hint), cy,
                withAlpha(COLOR_LABEL, ba), false);
        cy += 12;

        // Resource bars
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int barW = (width - PADDING*6)/3;
            int barY = height - RES_BAR_SEC + 4;
            float hp = player.getHealth(), maxHp = player.getMaxHealth();
            drawResBar(g, PADDING, barY, barW, "❤ HP",
                    hp, maxHp, RpgDisplayUtils.toDisplayHp(hp), RpgDisplayUtils.toDisplayHp(maxHp),
                    COLOR_HP_BG, COLOR_HP_FILL, ba);
            int sta = ClientStaminaManager.getStamina(), maxSta = ClientStaminaManager.getMaxStamina();
            drawResBar(g, PADDING*2+barW, barY, barW, "⚡ STAMINA",
                    sta, maxSta, sta, maxSta, COLOR_STA_BG, COLOR_STA_FILL, ba);
            int man = ClientManaManager.getMana(), maxMan = ClientManaManager.getMaxMana();
            drawResBar(g, PADDING*3+barW*2, barY, barW, "✨ MANA",
                    man, maxMan, man, maxMan, COLOR_MAN_BG, COLOR_MAN_FILL, ba);
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
        g.fill(bx, by, bx+bw, by+1, withAlpha(COLOR_BORDER_DIM, ba));
        g.fill(bx, by+RES_BAR_H-1, bx+bw, by+RES_BAR_H, withAlpha(COLOR_BORDER_DIM, ba));
        if (max > 0 && value > 0) {
            int fill = (int)(Math.min(value/max, 1f)*bw);
            if (fill > 0) g.fill(bx, by, bx+fill, by+RES_BAR_H, withAlpha(fillColor, ba));
        }
    }

    // ── Confirm popup ─────────────────────────────────────────────────────────

    private void drawConfirmPopup(GuiGraphicsExtractor g, int mx, int my, int ba) {
        List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
        if (confirmMasteryIdx < 0 || confirmMasteryIdx >= masteries.size()) return;
        Mastery mastery = masteries.get(confirmMasteryIdx);

        int popW = 220, popH = 70;
        int px = width/2 - popW/2, py = height/2 - popH/2;

        g.fill(px-2, py-2, px+popW+2, py+popH+2, withAlpha(COLOR_BORDER_GLOW, ba/2));
        g.fill(px, py, px+popW, py+popH, withAlpha(COLOR_POPUP_BG, ba));
        drawBorder(g, px, py, px+popW, py+popH, ba);

        String title = "Unlock " + mastery.getName() + "?";
        g.text(font, Component.literal(title),
                px+popW/2 - font.width(title)/2, py+10,
                withAlpha(COLOR_VALUE, ba), true);
        g.text(font, Component.literal("Cost: 1 Mastery Point"),
                px+popW/2 - font.width("Cost: 1 Mastery Point")/2, py+23,
                withAlpha(COLOR_LABEL, ba), false);

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
        g.fill(x,             y,             x+POP_BTN_W, y+1,           withAlpha(borderColor, ba));
        g.fill(x,             y+POP_BTN_H-1, x+POP_BTN_W, y+POP_BTN_H,  withAlpha(borderColor, ba));
        g.fill(x,             y,             x+1,          y+POP_BTN_H,  withAlpha(borderColor, ba));
        g.fill(x+POP_BTN_W-1, y,             x+POP_BTN_W, y+POP_BTN_H,  withAlpha(borderColor, ba));
        g.text(font, Component.literal(label),
                x+POP_BTN_W/2 - font.width(label)/2, y+(POP_BTN_H-8)/2,
                withAlpha(textColor, ba), false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();

        if (showConfirm) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y || key == org.lwjgl.glfw.GLFW.GLFW_KEY_E) {
                confirmUnlock(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_N || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                showConfirm = false; playClick(); return true;
            }
            return true;
        }

        if (!masteryPanelOpen) {
            // ── Overview navigation ──────────────────────────────────────────
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_A || key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
                cycleSkill(-1); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_D || key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
                cycleSkill(1); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                if (!MasteryRegistry.getMasteries(currentSkill()).isEmpty()) {
                    masteryPanelOpen = true; playClick();
                }
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                fadeOutTo(() -> Minecraft.getInstance().setScreen(new CharacterMenuScreen()));
                return true;
            }
        } else {
            // ── Mastery panel navigation ─────────────────────────────────────
            List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_W || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                if (selectedMasteryIndex > 0) {
                    selectedMasteryIndex--;
                    clampScroll(masteries.size());
                } else {
                    masteryPanelOpen = false;
                }
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                if (selectedMasteryIndex < masteries.size()-1) { selectedMasteryIndex++; clampScroll(masteries.size()); }
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_E || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                tryShowConfirm(selectedMasteryIndex, masteries); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                masteryPanelOpen = false; playClick(); return true;
            }
            // A/D cycles skills even from mastery panel
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_A || key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
                cycleSkill(-1); masteryPanelOpen = false; return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_D || key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
                cycleSkill(1); masteryPanelOpen = false; return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!masteryPanelOpen) {
            if (scrollY > 0) cycleSkill(-1);
            else if (scrollY < 0) cycleSkill(1);
        } else {
            List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
            int contentH = height - BOTTOM_H;
            int listH = contentH - PADDING*4;
            int visibleItems = listH / ITEM_H;
            int maxScroll = Math.max(0, masteries.size() - visibleItems);
            if (scrollY > 0 && masteryScrollOffset > 0) masteryScrollOffset--;
            else if (scrollY < 0 && masteryScrollOffset < maxScroll) masteryScrollOffset++;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        if (showConfirm) {
            if (inBounds(mx, my, popYesX, popYesY, POP_BTN_W, POP_BTN_H)) { confirmUnlock(); return true; }
            if (inBounds(mx, my, popNoX, popNoY, POP_BTN_W, POP_BTN_H)) { showConfirm = false; playClick(); return true; }
            return true;
        }

        if (masteryPanelOpen) {
            List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
            int panelX = PADDING, panelY = PADDING;
            int panelW = width - PADDING*2;
            int divX = panelX + panelW * 2/5;
            int listX = panelX + PADDING;
            int listY = panelY + PADDING;

            // Click list item
            if (mx >= listX && mx < divX - PADDING && my >= listY) {
                int clickedIdx = (my - listY) / ITEM_H + masteryScrollOffset;
                if (clickedIdx >= 0 && clickedIdx < masteries.size()) {
                    if (clickedIdx == selectedMasteryIndex) tryShowConfirm(clickedIdx, masteries);
                    else { selectedMasteryIndex = clickedIdx; playClick(); }
                    return true;
                }
            }

            // Click unlock button
            if (btnCanUnlock && inBounds(mx, my, btnX, btnY, BTN_W, BTN_H)) {
                tryShowConfirm(selectedMasteryIndex, masteries);
                return true;
            }
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void cycleSkill(int dir) {
        skillIndex = (skillIndex + dir + skills.length) % skills.length;
        selectedMasteryIndex = 0;
        masteryScrollOffset = 0;
        playClick();
    }

    private void clampScroll(int totalItems) {
        int contentH = height - BOTTOM_H;
        int listH = contentH - PADDING*4;
        int visibleItems = listH / ITEM_H;
        int maxScroll = Math.max(0, totalItems - visibleItems);
        if (selectedMasteryIndex < masteryScrollOffset)
            masteryScrollOffset = selectedMasteryIndex;
        if (selectedMasteryIndex >= masteryScrollOffset + visibleItems)
            masteryScrollOffset = selectedMasteryIndex - visibleItems + 1;
        masteryScrollOffset = Math.max(0, Math.min(masteryScrollOffset, maxScroll));
    }

    private void tryShowConfirm(int idx, List<Mastery> masteries) {
        if (idx < 0 || idx >= masteries.size()) return;
        Mastery mastery = masteries.get(idx);
        int rank = ClientMasteriesManager.getUnlockedRank(mastery.getId());
        if (rank >= mastery.getRankCount()) return;
        if (ClientMasteriesManager.getMasteryPoints() <= 0) return;
        int reqLevel = mastery.getRequiredLevelForRank(rank+1);
        if (ClientSkillsManager.getLevel(currentSkill()) < reqLevel) return;
        confirmMasteryIdx = idx;
        showConfirm = true;
        playClick();
    }

    private void confirmUnlock() {
        List<Mastery> masteries = MasteryRegistry.getMasteries(currentSkill());
        if (confirmMasteryIdx < 0 || confirmMasteryIdx >= masteries.size()) return;
        Mastery mastery = masteries.get(confirmMasteryIdx);
        ClientPlayNetworking.send(new UnlockMasteryPayload(currentSkill(), mastery.getId()));
        showConfirm = false;
        playClick();
    }

    private void fadeOutTo(Runnable onDone) { fadingOut = true; onFadeOutDone = onDone; }

    private void drawBorder(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int ba) {
        g.fill(x1,   y1,   x2,   y1+1, withAlpha(COLOR_BORDER, ba));
        g.fill(x1,   y2-1, x2,   y2,   withAlpha(COLOR_BORDER, ba));
        g.fill(x1,   y1,   x1+1, y2,   withAlpha(COLOR_BORDER, ba));
        g.fill(x2-1, y1,   x2,   y2,   withAlpha(COLOR_BORDER, ba));
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String test = current.isEmpty() ? word : current + " " + word;
            if (font.width(test) > maxWidth) {
                if (!current.isEmpty()) lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(test);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private String truncate(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 3 && font.width(s + "..") > maxPx) s = s.substring(0, s.length()-1);
        return s + "..";
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