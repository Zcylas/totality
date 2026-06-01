package zcylas.totality.screen.character.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.classes.covenant.CovenantData;
import zcylas.totality.api.rpg.combat.armor.ArmorProficiency;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.ClientStatsManager;
import zcylas.totality.screen.character.BaseCharacterScreen;
import zcylas.totality.screen.character.CharacterScreen;

import java.util.Map;

public class ClassTab extends CharacterScreenTab {

    // ── Scroll state ──────────────────────────────────────────────────────────────
    private int progScroll    = 0;
    private int resourceScroll = 0;
    private int identScroll   = 0;

    // Cached bounds for scroll hit testing
    private int progPanelX, progPanelY, progPanelW, progPanelH;
    private int resPanelX,  resPanelY,  resPanelW,  resPanelH;
    private int identDescX,  identDescY,  identDescW,  identDescH;

    public ClassTab(CharacterScreen screen) { super(screen); }

    @Override
    public void draw(GuiGraphicsExtractor g, Font font,
                     int mx, int my, int ba,
                     int x, int y, int w, int h) {
        int centerW = w * 56 / 100;
        int rightX  = x + centerW;
        int rightW  = w - centerW;

        drawCenterColumn(g, font, x, y, centerW, h);
        drawRightColumn(g, font, rightX, y, rightW, h);
    }

    // ── CENTER: Identity + Progression ────────────────────────────────────────

    private void drawCenterColumn(GuiGraphicsExtractor g, Font font,
                                  int x, int y, int w, int h) {
        int identH = h * 45 / 100;
        int progY  = y + identH;
        int progH  = h - identH;
        progPanelX = x; progPanelY = progY; progPanelW = w; progPanelH = progH;
        drawIdentityPanel(g, font, x, y, w, identH);
        drawProgressionPanel(g, font, x, progY, w, progH);
    }

    // ── CLASS IDENTITY ────────────────────────────────────────────────────────

    private void drawIdentityPanel(GuiGraphicsExtractor g, Font font,
                                   int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "CLASS IDENTITY");

        ClassData classData     = ClientClassManager.getPrimaryClassData();
        SubclassData subclass   = ClientClassManager.getSubclassData();
        CovenantData covenant   = ClientClassManager.getCovenantData();

        int ix = x + PAD;
        int iw = w - PAD * 2;
        int cx = x + w / 2;
        int cy = y + HDR_H + PAD;

        if (classData == null) {
            String msg = "No class selected";
            screen.drawSmallAt(g, msg, cx - Math.round(font.width(msg) * SMALL) / 2,
                    y + h / 2, COLOR_LABEL);
            return;
        }

        int classColor = getClassColor(classData.category());

        // ── Class icon box ────────────────────────────────────────────────────
        int iconSz = Math.min(h - HDR_H - PAD * 4, 56);
        int iconX  = ix;
        int iconY  = cy;

        g.fill(iconX, iconY, iconX + iconSz, iconY + iconSz, 0x22000000);
        screen.drawBorder(g, iconX, iconY, iconSz, iconSz, classColor);
        // Glow corner accents
        g.fill(iconX, iconY, iconX + 6, iconY + 1, classColor);
        g.fill(iconX, iconY, iconX + 1, iconY + 6, classColor);
        g.fill(iconX + iconSz - 6, iconY, iconX + iconSz, iconY + 1, classColor);
        g.fill(iconX + iconSz - 1, iconY, iconX + iconSz, iconY + 6, classColor);
        g.fill(iconX, iconY + iconSz - 1, iconX + 6, iconY + iconSz, classColor);
        g.fill(iconX, iconY + iconSz - 6, iconX + 1, iconY + iconSz, classColor);
        g.fill(iconX + iconSz - 6, iconY + iconSz - 1, iconX + iconSz, iconY + iconSz, classColor);
        g.fill(iconX + iconSz - 1, iconY + iconSz - 6, iconX + iconSz, iconY + iconSz, classColor);

        // Category icon centered
        String catIcon = classData.category().getIcon();
        g.pose().pushMatrix();
        g.pose().scale(2.5f, 2.5f);
        g.text(font, Component.literal(catIcon),
                (int)((iconX + iconSz / 2f) / 2.5f - font.width(catIcon) / 2f),
                (int)((iconY + iconSz / 2f) / 2.5f - 4f),
                classColor, true);
        g.pose().popMatrix();

        // ── Text right of icon ────────────────────────────────────────────────
        // ── Text right of icon — scrollable ──────────────────────────────────────
        int textX    = iconX + iconSz + PAD + 2;
        int textW    = iw - iconSz - PAD - 2;
        int textTopY = cy;
        int textH    = (y + h) - cy - PAD;

// Cache for scroll hit detection
        identDescX = textX; identDescY = textTopY; identDescW = textW; identDescH = textH;

        screen.sc(g, textX, textTopY, textW, textH);
        int ty = cy - identScroll; // apply scroll

        screen.drawTinyAt(g, "CURRENT CLASS", textX, ty, COLOR_LABEL);
        ty += TLH + 1;

        g.pose().pushMatrix();
        g.pose().scale(1.5f, 1.5f);
        g.text(font, Component.literal(classData.displayName()),
                (int)(textX / 1.5f), (int)(ty / 1.5f), classColor, true);
        g.pose().popMatrix();
        ty += 16;

        screen.drawTinyAt(g, "SUBCLASS", textX, ty, COLOR_LABEL);
        ty += TLH + 1;
        String subName = subclass != null ? subclass.displayName() : "None Selected";
        screen.drawSmallAt(g, subName, textX, ty,
                subclass != null ? COLOR_VALUE : COLOR_LABEL);
        ty += SLH + 3;

        if (covenant != null) {
            screen.drawTinyAt(g, "PATRON", textX, ty, COLOR_LABEL);
            ty += TLH + 1;
            screen.drawSmallAt(g, covenant.displayName(), textX, ty, 0xFFAA44CC);
            ty += SLH + 3;
        }

// Description flows naturally after identity info
        ty += 2;
        g.fill(textX, ty, textX + textW, ty + 1, COLOR_SEPARATOR);
        ty += PAD;
        screen.drawWrappedSmall(g, classData.description(), textX, ty, textW, COLOR_VALUE);

        screen.esc(g);
    }

    // ── CLASS PROGRESSION ─────────────────────────────────────────────────────

    private void drawProgressionPanel(GuiGraphicsExtractor g, Font font,
                                      int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "CLASS PROGRESSION");
        // No outer scissor — each column clips itself

        ClassData classData = ClientClassManager.getPrimaryClassData();
        if (classData == null) return;

        int ix    = x + PAD;
        int iw    = w - PAD * 2;
        int leftW  = iw * 58 / 100;
        int rightX = ix + leftW + PAD;
        int rightW = iw - leftW - PAD;
        int topY   = y + HDR_H + 2;
        int clipH  = h - HDR_H - 3;

        // ── LEFT column — scrollable ──────────────────────────────────────────────
        screen.sc(g, ix, topY, leftW, clipH);
        int lcy = topY + PAD - progScroll;
        int bx  = ix;
        int lineH = TLH + 4;

        screen.drawSmallAt(g, "WEAPON PROFICIENCIES", ix, lcy, COLOR_LABEL);
        lcy += SLH + 3;
        for (WeaponCategory cat : classData.weaponProficiencies()) {
            String badge = cat.displayName();
            int bw = Math.round(font.width(badge) * TINY) + 8;
            if (bx + bw > ix + leftW) { bx = ix; lcy += lineH + 2; }
            g.fill(bx, lcy, bx + bw, lcy + lineH, 0x22004488);
            screen.drawBorder(g, bx, lcy, bw, lineH, 0xFF4488CC);
            screen.drawTinyAt(g, badge, bx + 4, lcy + 2, 0xFF88BBDD);
            bx += bw + 3;
        }
        lcy += lineH + PAD + 2; bx = ix;

        screen.drawSmallAt(g, "ARMOR PROFICIENCIES", ix, lcy, COLOR_LABEL);
        lcy += SLH + 3;
        if (classData.armorProficiencies().isEmpty()) {
            screen.drawTinyAt(g, "Unarmored", ix, lcy, COLOR_LABEL);
            lcy += TLH + 4;
        } else {
            for (ArmorProficiency prof : classData.armorProficiencies()) {
                String badge = prof.name();
                int bw = Math.round(font.width(badge) * TINY) + 8;
                if (bx + bw > ix + leftW) { bx = ix; lcy += lineH + 2; }
                g.fill(bx, lcy, bx + bw, lcy + lineH, 0x22443300);
                screen.drawBorder(g, bx, lcy, bw, lineH, COLOR_COPPER);
                screen.drawTinyAt(g, badge, bx + 4, lcy + 2, COLOR_COPPER);
                bx += bw + 3;
            }
            lcy += lineH + PAD + 2; bx = ix;
        }

        screen.drawSmallAt(g, "SAVING THROWS", ix, lcy, COLOR_LABEL);
        lcy += SLH + 3;
        for (AbilityScore score : classData.savingThrowProficiencies()) {
            String badge = "⚔ " + score.name();
            int bw = Math.round(font.width(badge) * TINY) + 8;
            if (bx + bw > ix + leftW) { bx = ix; lcy += lineH + 2; }
            g.fill(bx, lcy, bx + bw, lcy + lineH, 0x2200AA44);
            screen.drawBorder(g, bx, lcy, bw, lineH, COLOR_GREEN);
            screen.drawTinyAt(g, badge, bx + 4, lcy + 2, COLOR_GREEN);
            bx += bw + 3;
        }
        lcy += lineH + PAD + 2;

        if (classData.spellcastingAbility() != null) {
            screen.drawSmallAt(g, "SPELLCASTING", ix, lcy, COLOR_LABEL);
            lcy += SLH + 3;
            screen.drawSmallAt(g, classData.spellcastingAbility().name(), ix, lcy, 0xFFAA44CC);
        }
        screen.esc(g); // end left column scissor

        // ── RIGHT column ──────────────────────────────────────────────────────────
        screen.sc(g, rightX, topY, rightW, clipH);
        int rcy = topY + PAD;

        Identifier primaryId = ClientClassManager.getPrimaryClassId();
        int classLevel = 1;
        if (primaryId != null) {
            Integer stored = ClientClassManager.getClassLevels().get(primaryId);
            classLevel = stored != null ? Math.max(1, stored) : 1;
        }
        int playerLevel = ClientStatsManager.getLevel();
        int classColor  = getClassColor(classData.category());

        String clLbl = "CLASS LEVEL";
        screen.drawTinyAt(g, clLbl,
                rightX + rightW / 2 - Math.round(font.width(clLbl) * TINY) / 2, rcy, COLOR_LABEL);
        rcy += TLH + 2;

        // Unspent class points indicator
        int totalSpent = ClientClassManager.getClassLevels().values()
                .stream().mapToInt(Integer::intValue).sum();
        int available  = PlayerClassComponent.toClassLevel(playerLevel);
        if (available > totalSpent) {
            int unspent = available - totalSpent;
            String pts = "✦ " + unspent + (unspent > 1 ? " pts" : " pt") + " to spend!";
            screen.drawTinyAt(g, pts,
                    rightX + rightW / 2 - Math.round(font.width(pts) * TINY) / 2,
                    rcy, 0xFFFFD700);
            rcy += TLH + 2;
        }

        // Class level — big number or multiclass list
        if (ClientClassManager.getClassLevels().size() == 1) {
            String lvlStr = String.valueOf(classLevel);
            g.pose().pushMatrix();
            g.pose().scale(3f, 3f);
            g.text(font, Component.literal(lvlStr),
                    (int)((rightX + rightW / 2f) / 3f - font.width(lvlStr) / 2f),
                    (int)(rcy / 3f), classColor, true);
            g.pose().popMatrix();
            rcy += 28;
        } else {
            for (Map.Entry<Identifier, Integer> entry : ClientClassManager.getClassLevels().entrySet()) {
                ClassData cd = ClassRegistry.get(entry.getKey()).orElse(null);
                if (cd == null) continue;
                String line = cd.displayName() + "  Lv. " + entry.getValue();
                screen.drawSmallAt(g, line, rightX, rcy, getClassColor(cd.category()));
                rcy += SLH + 3;
            }
        }
        screen.esc(g); // end right column scissor
    }

    // ── RIGHT: Features + Resource ────────────────────────────────────────────

    private void drawRightColumn(GuiGraphicsExtractor g, Font font,
                                 int x, int y, int w, int h) {
        int featH = h * 62 / 100;
        int resY  = y + featH;
        int resH  = h - featH;
        resPanelX = x; resPanelY = resY; resPanelW = w; resPanelH = resH;
        drawFeaturesPanel(g, font, x, y, w, featH);
        drawResourcePanel(g, font, x, resY, w, resH);
    }

    // ── CLASS FEATURES ────────────────────────────────────────────────────────

    private void drawFeaturesPanel(GuiGraphicsExtractor g, Font font,
                                   int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "CLASS FEATURES");

        // Placeholder — fills when ClassFeatureRegistry is built
        String line1 = "Features will appear";
        String line2 = "as you level up.";
        int lh = SLH + 3;
        int startY = y + h / 2 - lh;
        for (String line : new String[]{line1, line2}) {
            screen.drawSmallAt(g, line,
                    x + w / 2 - Math.round(font.width(line) * SMALL) / 2,
                    startY, COLOR_LABEL);
            startY += lh;
        }
    }

    // ── CLASS RESOURCE ────────────────────────────────────────────────────────

    private void drawResourcePanel(GuiGraphicsExtractor g, Font font,
                                   int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "CLASS RESOURCE");

        int cx = x + w / 2;

        // Read charge data
        int currentCharges = 0;
        int maxCharges     = 0;
        String resourceName = "—";
        String rechargeNote = "";

        try {
            var chargeComp = ChargeComponents.PLAYER_CHARGES.get(
                    (ComponentProvider) Minecraft.getInstance().player);
            Identifier rageId = BarbarianRageAbility.CHARGE_ID;
            currentCharges = chargeComp.getCurrent(rageId);
            maxCharges     = chargeComp.getMax(rageId);
            if (maxCharges > 0) {
                resourceName = "BARBARIAN RAGE";
                rechargeNote = "+1 Short Rest  ·  All on Long Rest";
            }
        } catch (Exception ignored) {}

        screen.sc(g, x + 1, y + HDR_H + 1, w - 2, h - HDR_H - 2);
        int cy = y + HDR_H + PAD - resourceScroll;

        if (maxCharges <= 0) {
            String msg = "No resource";
            screen.drawSmallAt(g, msg,
                    cx - Math.round(font.width(msg) * SMALL) / 2,
                    y + h / 2, COLOR_LABEL);
            screen.esc(g); // ← close before early return
            return;
        }

        int rnW = Math.round(font.width(resourceName) * SMALL);
        screen.drawSmallAt(g, resourceName, cx - rnW / 2, cy, 0xFFCC3333);
        cy += SLH + PAD;

        int pipSz  = 11;
        int pipGap = 4;
        int totalW = maxCharges * pipSz + (maxCharges - 1) * pipGap;
        int pipX   = cx - totalW / 2;

        for (int i = 0; i < maxCharges; i++) {
            boolean filled = i < currentCharges;
            g.fill(pipX, cy, pipX + pipSz, cy + pipSz,
                    filled ? 0x44CC0000 : COLOR_PANEL_BG);
            screen.drawBorder(g, pipX, cy, pipSz, pipSz,
                    filled ? 0xFFCC3333 : COLOR_BORDER_INNER);
            if (filled)
                g.fill(pipX + 2, cy + 2, pipX + pipSz - 2, cy + pipSz - 2, 0xFFCC3333);
            pipX += pipSz + pipGap;
        }
        cy += pipSz + PAD;

        String countStr = currentCharges + " / " + maxCharges;
        int cw = Math.round(font.width(countStr) * SMALL);
        screen.drawSmallAt(g, countStr, cx - cw / 2, cy, COLOR_VALUE);
        cy += SLH + 2;

        int rnw2 = Math.round(font.width(rechargeNote) * TINY);
        screen.drawTinyAt(g, rechargeNote, cx - rnw2 / 2, cy, COLOR_LABEL);

        screen.esc(g);
    }

    @Override
    public void onOpen() {
        progScroll = resourceScroll = identScroll = 0;
    }

    @Override
    public void mouseScrolled(int mx, int my, double delta) {
        int amount = (int)(delta * 12);
        if (screen.inB(mx, my, progPanelX, progPanelY, progPanelW, progPanelH)) {
            progScroll = Math.max(0, progScroll - amount);
        } else if (screen.inB(mx, my, resPanelX, resPanelY, resPanelW, resPanelH)) {
            resourceScroll = Math.max(0, resourceScroll - amount);
        } else if (screen.inB(mx, my, identDescX, identDescY, identDescW, identDescH)) {
            identScroll = Math.max(0, identScroll - amount);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getClassColor(ClassCategory cat) {
        return switch (cat) {
            case MARTIAL -> 0xFFCC4444;
            case ARCANE  -> 0xFFAA44CC;
            case DIVINE  -> 0xFFCCBB44;
            case CUNNING -> 0xFF44CCAA;
        };
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int   COLOR_SEPARATOR    = BaseCharacterScreen.COLOR_SEPARATOR;
    private static final int   COLOR_LABEL        = BaseCharacterScreen.COLOR_LABEL;
    private static final int   COLOR_VALUE        = BaseCharacterScreen.COLOR_VALUE;
    private static final int   COLOR_ACCENT       = BaseCharacterScreen.COLOR_ACCENT;
    private static final int   COLOR_BORDER_INNER = BaseCharacterScreen.COLOR_BORDER_INNER;
    private static final int   COLOR_PANEL_BG     = BaseCharacterScreen.COLOR_PANEL_BG;
    private static final int   COLOR_COPPER       = BaseCharacterScreen.COLOR_COPPER;
    private static final int   COLOR_GREEN        = BaseCharacterScreen.COLOR_GREEN;
    private static final int   COLOR_XP_BG        = BaseCharacterScreen.COLOR_XP_BG;
    private static final int   COLOR_XP_FILL      = BaseCharacterScreen.COLOR_XP_FILL;
    private static final int   HDR_H              = BaseCharacterScreen.HDR_H;
    private static final int   BAR_H              = BaseCharacterScreen.BAR_H;
    private static final int   PAD                = BaseCharacterScreen.PAD;
    private static final int   NLH                = BaseCharacterScreen.NLH;
    private static final int   SLH                = BaseCharacterScreen.SLH;
    private static final int   TLH                = BaseCharacterScreen.TLH;
    private static final float SMALL              = BaseCharacterScreen.SMALL;
    private static final float TINY               = BaseCharacterScreen.TINY;
}