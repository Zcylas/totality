package zcylas.totality.screen.character.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.classes.covenant.CovenantData;
import zcylas.totality.api.rpg.combat.armor.ArmorProficiency;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.presentation.ClientResourcePresentationResolver;
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
    /** Scroll offset for the right-side owned-class list — mirrors {@code progScroll}'s own
     *  pattern (a plain int offset subtracted from the drawing cursor, clamped on both ends,
     *  reset in {@link #onOpen}), added so a multiclass character with more owned classes than
     *  fit in the visible box can reach every row and its "+" button. */
    private int mcListScroll  = 0;

    // Cached bounds for scroll hit testing
    private int progPanelX, progPanelY, progPanelW, progPanelH;
    private int resPanelX,  resPanelY,  resPanelW,  resPanelH;
    private int identDescX,  identDescY,  identDescW,  identDescH;
    /** Viewport of the scrollable owned-class list only (below the CLASS LEVEL header / SPEND
     *  CLASS POINT button, which never scroll) — used both for {@code mouseScrolled} hit-testing
     *  and to decide whether a given "+" button is fully visible (see {@link #drawQuickLevelButton}). */
    private int mcListPanelX, mcListPanelY, mcListPanelW, mcListPanelH;
    /** Recomputed every frame in {@link #drawProgressionPanel} from the list's actual content
     *  height, so a change in the number of owned classes (or a class gaining a subclass) is
     *  reflected — and {@code mcListScroll} re-clamped against it — on the very next frame. */
    private int mcListMaxScroll = 0;

    // ── Button state ──────────────────────────────────────────────────────────────
    private int lvlUpBtnX, lvlUpBtnY, lvlUpBtnW, lvlUpBtnH;
    private int mcBtnX,    mcBtnY,    mcBtnW,    mcBtnH;

    public ClassTab(CharacterScreen screen) { super(screen); }

    @Override
    public void draw(GuiGraphicsExtractor g, Font font,
                     int mx, int my, int ba,
                     int x, int y, int w, int h) {
        int centerW = w * 56 / 100;
        int rightX  = x + centerW;
        int rightW  = w - centerW;

        drawCenterColumn(g, font, mx, my, x, y, centerW, h);
        drawRightColumn(g, font, rightX, y, rightW, h);
    }

    // ── CENTER: Identity + Progression ────────────────────────────────────────

    private void drawCenterColumn(GuiGraphicsExtractor g, Font font,
                                  int mx, int my,
                                  int x, int y, int w, int h) {
        int identH = h * 45 / 100;
        int progY  = y + identH;
        int progH  = h - identH;
        progPanelX = x; progPanelY = progY; progPanelW = w; progPanelH = progH;
        drawIdentityPanel(g, font, x, y, w, identH);
        drawProgressionPanel(g, font, mx, my, x, progY, w, progH);
    }

    // ── CLASS IDENTITY ────────────────────────────────────────────────────────

    private void drawIdentityPanel(GuiGraphicsExtractor g, Font font,
                                   int x, int y, int w, int h) {
        screen.drawPanel(g, x, y, w, h);
        screen.drawPanelHdr(g, x, y, w, "CLASS IDENTITY");

        ClassData classData     = ClientClassManager.getPrimaryClassData();
        SubclassData subclass   = classData != null ? ClientClassManager.getSubclassData(classData.id()) : null;
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
                                      int mx, int my,
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
        // The CLASS LEVEL header and SPEND CLASS POINT button never scroll — only the owned-class
        // list below them does (see the second, independent sc()/esc() pair further down).
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
        int unspentPoints = available - totalSpent;
        if (unspentPoints > 0) {
            int unspent = unspentPoints;
            String pts = "✦ " + unspent + (unspent > 1 ? " pts" : " pt") + " to spend!";
            int ptW = Math.round(font.width(pts) * TINY);
            screen.drawTinyAt(g, pts,
                    rightX + rightW / 2 - ptW / 2,
                    rcy, 0xFFFFD700);
            rcy += TLH + 3;

            // ── Spend class point button ──────────────────────────────────────
            String spendLabel = "✦ SPEND CLASS POINT";
            int btnW = rightW - PAD * 2;
            int btnH = SLH + 4;
            int btnX = rightX + PAD;
            boolean hovSpend = screen.inB(mx, my, btnX, rcy, btnW, btnH);
            g.fill(btnX, rcy, btnX + btnW, rcy + btnH, hovSpend ? 0x44FFD700 : 0x22FFD700);
            screen.drawBorder(g, btnX, rcy, btnW, btnH, 0xFFFFD700);
            screen.drawTinyAt(g, spendLabel,
                    btnX + btnW / 2 - Math.round(font.width(spendLabel) * TINY) / 2,
                    rcy + 3, 0xFFFFD700);
            lvlUpBtnX = btnX; lvlUpBtnY = rcy; lvlUpBtnW = btnW; lvlUpBtnH = btnH;
            mcBtnW = 0; // single button now
            rcy += btnH + 3;
        } else {
            lvlUpBtnW = 0; mcBtnW = 0; // no buttons this frame
        }
        screen.esc(g); // end header (CLASS LEVEL label + SPEND CLASS POINT button) scissor

        // ── Owned-class list — independently scrollable ────────────────────────────
        // Mirrors the LEFT column's progScroll pattern exactly (plain int offset subtracted from
        // the drawing cursor, its own sc()/esc() pair, clamped in mouseScrolled) — added because a
        // multiclass character can own more classes than fit in the remaining vertical space.
        int listTopY = rcy;
        int listH    = Math.max(0, (y + h) - listTopY);
        mcListPanelX = rightX; mcListPanelY = listTopY; mcListPanelW = rightW; mcListPanelH = listH;

        // Clamp BEFORE drawing (not just in mouseScrolled) so a content-height change — leveling a
        // class, gaining a subclass, or simply reopening the tab with a different character —
        // never leaves mcListScroll pointing past the new content on the very frame it changes.
        mcListMaxScroll = Math.max(0, measureOwnedClassListContentHeight() - listH);
        mcListScroll    = Math.clamp(mcListScroll, 0, mcListMaxScroll);

        screen.sc(g, mcListPanelX, mcListPanelY, mcListPanelW, mcListPanelH);
        int mcy = listTopY - mcListScroll;
        quickLevelButtons.clear();
        boolean canSpendAPoint = unspentPoints > 0;
        if (ClientClassManager.getClassLevels().size() == 1) {
            String lvlStr = String.valueOf(classLevel);
            g.pose().pushMatrix();
            g.pose().scale(3f, 3f);
            g.text(font, Component.literal(lvlStr),
                    (int)((rightX + rightW / 2f) / 3f - font.width(lvlStr) / 2f),
                    (int)(mcy / 3f), classColor, true);
            g.pose().popMatrix();
            mcy += 28 + 3;
            if (primaryId != null) {
                int btnSz = QUICK_LVL_BTN_SZ;
                drawQuickLevelButton(g, font, mx, my,
                        rightX + rightW / 2 - btnSz / 2, mcy, btnSz, primaryId, canSpendAPoint);
                mcy += btnSz;
            }
        } else {
            for (Map.Entry<Identifier, Integer> entry : ClientClassManager.getClassLevels().entrySet()) {
                ClassData cd = ClassRegistry.get(entry.getKey()).orElse(null);
                if (cd == null) continue;
                String line = cd.displayName() + "  Lv. " + entry.getValue();
                int btnSz = QUICK_LVL_BTN_SZ;
                int rowH = Math.max(SLH, btnSz);
                screen.drawSmallAt(g, line, rightX, mcy + (rowH - SLH) / 2, getClassColor(cd.category()));
                drawQuickLevelButton(g, font, mx, my,
                        rightX + rightW - btnSz, mcy + (rowH - btnSz) / 2, btnSz,
                        entry.getKey(), canSpendAPoint);
                mcy += rowH + 2;
                // Each class shows its OWN subclass only — never another class's (per-class
                // subclass migration, 2026-09-16).
                SubclassData sub = ClientClassManager.getSubclassData(entry.getKey());
                if (sub != null) {
                    screen.drawTinyAt(g, sub.displayName(), rightX + 4, mcy, COLOR_LABEL);
                    mcy += TLH + 1;
                }
                mcy += 3;
            }
        }
        screen.esc(g); // end owned-class list scissor
    }

    /**
     * The owned-class list's total content height at {@code mcListScroll == 0} — mirrors, term for
     * term, the vertical advances the draw loop above actually performs, so the clamp computed from
     * it can never drift from what is actually rendered. Deliberately a pure measurement with no
     * drawing side effects, so it is cheap to call once per frame before the real draw pass.
     */
    private int measureOwnedClassListContentHeight() {
        if (ClientClassManager.getClassLevels().size() == 1) {
            return 28 + 3 + QUICK_LVL_BTN_SZ;
        }
        int total = 0;
        int rowH = Math.max(SLH, QUICK_LVL_BTN_SZ);
        for (Identifier classId : ClientClassManager.getClassLevels().keySet()) {
            total += rowH + 2;
            if (ClientClassManager.getSubclassData(classId) != null) {
                total += TLH + 1;
            }
            total += 3;
        }
        return total;
    }

    // ── Quick level-up ("+") buttons ─────────────────────────────────────────────

    private static final int QUICK_LVL_BTN_SZ = 13;

    /**
     * One "+" per owned class, beside its existing level display — generic across every class
     * (no per-class branching), following the class-progression audit's own conclusion that the
     * only gate on continuing to level ANY specific class is the player's shared pool of unspent
     * class points (canonical: no per-class maximum field exists in {@code ClassData}; the total
     * is already capped at 30 by {@code PlayerClassComponent#toClassLevel}). Pressing it sends the
     * exact same {@code AddClassLevelPayload} the existing "SPEND CLASS POINT" → Class Screen flow
     * already sends for an already-owned class — the server-authoritative {@code
     * AddClassLevelHandler} performs all validation and fires the normal {@code
     * ClassLevelUpRegistry} hooks unchanged. If the level-up crosses that class's subclass-unlock
     * milestone, the server pushes {@code OpenSubclassSelectionPayload} exactly as it already does
     * today — this button never bypasses that, and never mutates class-level state itself.
     */
    private void drawQuickLevelButton(GuiGraphicsExtractor g, Font font, int mx, int my,
                                      int x, int y, int size, Identifier classId, boolean enabled) {
        boolean hov = enabled && screen.inB(mx, my, x, y, size, size);
        int fill = !enabled ? 0x22444444 : (hov ? 0x4466DD66 : 0x2266DD66);
        int border = enabled ? 0xFF66DD66 : COLOR_BORDER_INNER;
        int textColor = enabled ? 0xFF66DD66 : COLOR_LABEL;
        g.fill(x, y, x + size, y + size, fill);
        screen.drawBorder(g, x, y, size, size, border);
        String plus = "+";
        screen.drawTinyAt(g, plus,
                x + size / 2 - Math.round(font.width(plus) * TINY) / 2,
                y + (size - TLH) / 2, textColor);
        // A row scrolled outside the owned-class list's viewport is still drawn at its raw,
        // off-screen coordinates (harmless — the GL scissor above already makes it invisible), but
        // its stored hitbox must not remain clickable there: those coordinates can still fall
        // within the CLASS LEVEL header/SPEND CLASS POINT button area directly above this list, or
        // simply past the bottom of the panel — either way, a click there must never be mistaken
        // for pressing a "+" that is not actually visible.
        boolean fullyVisible = y >= mcListPanelY && y + size <= mcListPanelY + mcListPanelH;
        quickLevelButtons.add(new QuickLevelButton(classId, x, y, size, size, enabled && fullyVisible));
    }

    private record QuickLevelButton(Identifier classId, int x, int y, int w, int h, boolean enabled) {}

    private final java.util.List<QuickLevelButton> quickLevelButtons = new java.util.ArrayList<>();

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

        // Read charge data.
        // Phase 3C: presentation source migrated to the trusted Generic client Resource view,
        // falling back to the legacy PlayerChargesComponent mirror only when the Generic query is
        // unavailable — see ClientResourcePresentationResolver.
        String resourceName = "—";
        String rechargeNote = "";

        ClientResourcePresentationResolver.ScalarPresentation rageView =
                ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.RAGE,
                        ClassTab::legacyRageCurrent, ClassTab::legacyRageMax);
        int currentCharges = (int) rageView.current();
        int maxCharges     = (int) rageView.maximum();
        if (maxCharges > 0) {
            resourceName = "BARBARIAN RAGE";
            rechargeNote = "+1 Short Rest  ·  All on Long Rest";
        }

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

    /**
     * External-review correction (Phase 5, 2026-09-15, finding 3) — see {@code
     * TotalityClient#legacyRageCurrent}'s Javadoc for the full reasoning: reading the legacy {@code
     * PlayerChargesComponent} mirror here is no longer safe now that Generic Rage is independently
     * authoritative and an existing migrated Barbarian's legacy pool is frozen at a possibly-stale
     * value. Returns 0 unconditionally so this fallback can never present a wrong nonzero number.
     */
    private static int legacyRageCurrent() { return 0; }

    /** See {@link #legacyRageCurrent} — same correction, same reasoning, applied to the maximum. */
    private static int legacyRageMax() { return 0; }

    @Override
    public void mouseClicked(int mx, int my) {
        // Single "Spend Class Point" button — opens ClassSelectionScreen.
        // Picking your existing class levels it up; picking a new one starts multiclassing.
        // AddClassLevelHandler handles both cases on the server.
        if (lvlUpBtnW > 0 && screen.inB(mx, my, lvlUpBtnX, lvlUpBtnY, lvlUpBtnW, lvlUpBtnH)) {
            zcylas.totality.screen.classes.ClassScreenMode.IS_MULTICLASSING = true;
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                    new zcylas.totality.screen.classes.ClassSelectionScreen());
            return;
        }

        // Per-class "+" quick level-up — sends the exact same server-authoritative
        // AddClassLevelPayload the full Class Screen flow sends; never mutates class state here.
        for (QuickLevelButton btn : quickLevelButtons) {
            if (btn.enabled() && screen.inB(mx, my, btn.x(), btn.y(), btn.w(), btn.h())) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new zcylas.totality.networking.classes.AddClassLevelPayload(btn.classId().toString()));
                return;
            }
        }
    }

    @Override
    public void onOpen() {
        progScroll = resourceScroll = identScroll = mcListScroll = 0;
    }

    @Override
    public void mouseScrolled(int mx, int my, double delta) {
        int amount = (int)(delta * 12);
        // Checked before the (wider) progPanel region below: the owned-class list's viewport is a
        // geometric subset of the whole progression panel, so checking it first is what makes
        // hovering the right/multiclass list scroll only that list rather than also matching the
        // left column's own region.
        if (screen.inB(mx, my, mcListPanelX, mcListPanelY, mcListPanelW, mcListPanelH)) {
            mcListScroll = Math.clamp(mcListScroll - amount, 0, mcListMaxScroll);
        } else if (screen.inB(mx, my, progPanelX, progPanelY, progPanelW, progPanelH)) {
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