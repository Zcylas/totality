// screen/dice/DiceRollScreen.java
package zcylas.totality.screen.dice;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import zcylas.totality.api.client.gui.TotalityGuiRenderer;
import zcylas.totality.api.dice.*;
import zcylas.totality.networking.dice.DiceRollClickPayload;

import java.util.Random;
import java.util.UUID;

/**
 * BG3-faithful dice roll screen.
 *
 * Layout:
 *   Title (check name + subtype) — above the panel, always visible
 *   Panel — DC section (always visible) + die area + hint OR outcome
 *   Bonus section — BELOW the panel, outside the frame
 *   Continue button — below bonus section in SHOW_FINAL
 *
 * Die: hexagonal silhouette, face-forward d20 projection.
 *   Central downward triangle = front face (brightest, main number)
 *   3 surrounding nooks = adjacent faces (darker, adjacent numbers)
 */
public class DiceRollScreen extends Screen {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG          = 0xCC000000; // semi-opaque — world shows through like BG3
    private static final int C_PANEL       = 0xFF090714;
    private static final int C_DC_BG       = 0xFF060410;
    private static final int C_BORDER      = 0xFF8A6820;
    private static final int C_BORDER_DIM  = 0xFF4A380E;
    private static final int C_DIVIDER     = 0xFF4A380E;
    private static final int C_WHITE       = 0xFFEEEEEE;
    private static final int C_GOLD        = 0xFFE8D5A0;
    private static final int C_GOLD_DIM    = 0xFF7A6040;
    private static final int C_GOLD_BRIGHT = 0xFFFFD700;
    private static final int C_RED         = 0xFFE24B4A;
    private static final int C_CRIMSON     = 0xFFCC2222;
    private static final int C_GREEN       = 0xFF5CC87A;
    private static final int C_BONUS_BG    = 0xFF0C0A10;
    private static final int C_BONUS_BDR   = 0xFF3A2C0A;
    private static final int C_BONUS_ACT   = 0xFF9A7830;
    private static final int C_ADV         = 0xFF4A9060;
    private static final int C_DIS         = 0xFF9A3030;

    // Adjacent number colors per nook (semi-transparent white, varies by shading)
    private static final int C_ADJ_A  = 0xBBDDEEFF; // upper-right (medium shade)
    private static final int C_ADJ_B  = 0x99BBCCEE; // lower (darkest face)
    private static final int C_ADJ_C  = 0xAAC8DDFF; // upper-left (medium-dark)

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PANEL_W   = 150;
    private static final int DC_H      = 34;
    private static final int DIE_H     = 76;
    private static final int HINT_H    = 16;
    private static final int RT_H      = 12;
    private static final int SUCCESS_H = 20;
    private static final int CONT_H    = 16;
    private static final int DIE_R     = 27;
    private static final int CARD_W    = 42;
    private static final int CARD_H    = 36;
    private static final int CARD_GAP  = 3;

    // ── Animation ─────────────────────────────────────────────────────────────
    private static final long ROLL_MS        = 2500L;
    private static final long FAST_MS        = 1400L;
    private static final long FAST_INTERVAL  = 70L;
    private static final long SLOW_MAX       = 380L;
    private static final long NATURAL_MS     = 600L;
    private static final long BONUS_STEP_MS  = 350L;
    private int displayNum2 = 20; // second die for advantage/disadvantage
    // ── Phases ────────────────────────────────────────────────────────────────
    private enum Phase { IDLE, ROLLING, WAITING, RESULT }
    private enum ResultPhase { SHOW_NATURAL, ADDING_BONUSES, SHOW_FINAL }

    private Phase       phase       = Phase.IDLE;
    private ResultPhase resultPhase = ResultPhase.SHOW_NATURAL;

    private long rollStart    = -1L;
    private long lastChange   = 0L;
    private long resultStart  = -1L;

    private int  displayNum     = 20;
    private int  displayTotal   = 0;
    private int  activeBonusIdx = -1;
    private boolean skipRequested = false;

    private DiceRollResult receivedResult = null;

    private final UUID            sessionId;
    private final DiceRollContext context;
    private final DiceSkin        skin;
    private final Random          rng = new Random();

    private int panelX, panelY, panelH;
    private int dieCx, dieCy;
    private int bonusSectionY;
    private int continueY;

    public DiceRollScreen(UUID sessionId, DiceRollContext context) {
        super(Component.empty());
        this.sessionId  = sessionId;
        this.context    = context;
        this.skin       = DiceSkin.DEFAULT;
        this.displayNum = 20;
    }

    @Override protected void init() { super.init(); recalcLayout(); }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void recalcLayout() {
        boolean isFinal = resultPhase == ResultPhase.SHOW_FINAL;

        // Panel always shows DC section.
        // In IDLE/ROLLING/SHOW_NATURAL/ADDING_BONUSES: DC + die + hint
        // In SHOW_FINAL: DC + die + SUCCESS text
        panelH = DC_H + DIE_H
                + (isFinal ? SUCCESS_H : HINT_H)
                + (context.rollType() != RollType.NORMAL && !isFinal ? RT_H : 0);

        int bonusH  = bonusSectionH();
        int titleH  = 22;
        int totalH  = titleH + 4 + panelH
                + (bonusH > 0 ? 4 + bonusH : 0)
                + (isFinal   ? 6 + CONT_H  : 0);

        panelX = (width  - PANEL_W) / 2;
        panelY = Math.max(26, (height - totalH) / 2 + titleH + 4);


        dieCx  = panelX + PANEL_W / 2;
        dieCy  = panelY + DC_H + DIE_H / 2;

        bonusSectionY = panelY + panelH + 6;
        continueY     = bonusSectionY + (bonusH > 0 ? bonusH + 8 : 0);
    }

    private int bonusSectionH() {
        if (context.bonuses().isEmpty()) return 0;
        int maxPerRow = Math.max(1, PANEL_W / (CARD_W + CARD_GAP));
        int rows = (context.bonuses().size() + maxPerRow - 1) / maxPerRow;
        return rows * CARD_H + (rows - 1) * CARD_GAP + 14; // 14 for "Total Bonus" label
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        updateAnimation();
        recalcLayout();

        boolean isFinal = resultPhase == ResultPhase.SHOW_FINAL;

        // Semi-opaque overlay (world shows through slightly like BG3)
        g.fill(0, 0, width, height, C_BG);

        // Title — always visible
        drawLarge( g, context.checkName(),             width / 2, panelY - 22, C_WHITE);
        drawSmallC(g, context.checkSubtype(),           width / 2, panelY - 9,  C_GOLD_DIM);

        // Panel fill
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, C_PANEL);

        // Ornate panel border
        drawOrnateBorder(g, panelX, panelY, PANEL_W, panelH);

        // Critical glow around panel border in SHOW_FINAL
        if (isFinal && receivedResult != null) {
            float p = (float)(0.45f + 0.55f * Math.abs(Math.sin(Util.getMillis() / 280.0)));
            int glowCol = switch (receivedResult.outcome()) {
                case CRITICAL_SUCCESS -> applyAlpha(C_GOLD_BRIGHT, p);
                case SUCCESS          -> applyAlpha(C_GREEN,       p);
                case FAILURE          -> applyAlpha(C_RED,         p);
                case CRITICAL_FAILURE -> applyAlpha(C_CRIMSON,     p);
            };
            for (int i = 1; i <= 2; i++)
                border1px(g, panelX - i, panelY - i, PANEL_W + i*2, panelH + i*2,
                        applyAlpha(glowCol, 1.0f - i * 0.4f));
        }

        // DC section — always visible
        g.fill(panelX + 2, panelY + 2, panelX + PANEL_W - 2, panelY + DC_H, C_DC_BG);
        drawSmallC(g, "DIFFICULTY CLASS", width / 2, panelY + 9,  C_GOLD_DIM);
        drawHuge(  g, String.valueOf(context.dc()), width / 2, panelY + 20, dcColor());

        // Ornamental separator
        int sv = panelY + DC_H + 3;
        g.fill(panelX + 2, sv, panelX + PANEL_W - 2, sv + 1, C_DIVIDER);

        // Plain closed rectangle around die + outcome area — no corner accents
        int dieBoxY = sv + 1;
        int dieBoxH = panelY + panelH - dieBoxY - 2;
        border1px(g, panelX + 2, dieBoxY, PANEL_W - 4, dieBoxH, C_BORDER_DIM);

        // Die
        boolean hovered = isDieHovered(mx, my) && phase == Phase.IDLE;
        boolean twodice = context.rollType() != RollType.NORMAL;
        if (twodice) {
            int r2 = 20, spacing = r2 + 8;
            int d1cx = dieCx - spacing, d2cx = dieCx + spacing;
            boolean d1dim = phase == Phase.RESULT && resultPhase == ResultPhase.SHOW_FINAL
                    && receivedResult != null && receivedResult.usedRoll() != receivedResult.roll1();
            boolean d2dim = phase == Phase.RESULT && resultPhase == ResultPhase.SHOW_FINAL
                    && receivedResult != null && receivedResult.usedRoll() != receivedResult.roll2();
            drawDie(g, d1cx, dieCy, r2, displayNum,  hovered, d1dim);
            drawDie(g, d2cx, dieCy, r2, displayNum2, hovered, d2dim);
        } else {
            drawDie(g, dieCx, dieCy, DIE_R, displayNum, hovered, false);
        }

        // Below die: hint / outcome / running total
        int actualR = twodice ? 20 : DIE_R;
        int belowY  = dieCy + actualR + 6;

        if (isFinal && receivedResult != null) {
            drawOutcome(g, belowY);
        } else if (phase == Phase.IDLE) {
            // "Click dice to roll" — plain text, no border, no hover
            drawSmallC(g, "Click dice to roll", width / 2, belowY + 3, C_WHITE);
        } else if (resultPhase == ResultPhase.ADDING_BONUSES) {
            drawSmallC(g, String.valueOf(displayTotal), width / 2, belowY + 3, C_WHITE);
        }

        // Advantage/disadvantage
        if (context.rollType() != RollType.NORMAL && !isFinal) {
            boolean adv = context.rollType() == RollType.ADVANTAGE;
            drawSmallC(g, adv ? "▲  Advantage" : "▼  Disadvantage",
                    width / 2, belowY + HINT_H, adv ? C_ADV : C_DIS);
        }

        // Bonus section (outside panel)
        if (!context.bonuses().isEmpty())
            drawBonusSection(g, bonusSectionY);

        // Continue button
        if (isFinal)
            drawContinue(g, mx, my);
    }

    // ── Ornate panel border ───────────────────────────────────────────────────

    private void drawOrnateBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        border1px(g, x, y, w, h, C_BORDER);

        int ca = 8;
        // Top-left
        g.fill(x,         y,         x + ca, y + 2,  C_BORDER);
        g.fill(x,         y,         x + 2,  y + ca,  C_BORDER);
        // Top-right
        g.fill(x + w - ca, y,        x + w,  y + 2,  C_BORDER);
        g.fill(x + w - 2,  y,        x + w,  y + ca,  C_BORDER);
        // Bottom-left
        g.fill(x,          y + h - 2, x + ca, y + h,  C_BORDER);
        g.fill(x,          y + h - ca, x + 2, y + h,  C_BORDER);
        // Bottom-right
        g.fill(x + w - ca, y + h - 2, x + w, y + h,  C_BORDER);
        g.fill(x + w - 2,  y + h - ca, x + w, y + h, C_BORDER);
    }

    private static void border1px(GuiGraphicsExtractor g, int x, int y, int w, int h, int col) {
        g.fill(x,         y,         x + w, y + 1,  col);
        g.fill(x,         y + h - 1, x + w, y + h,  col);
        g.fill(x,         y,         x + 1, y + h,   col);
        g.fill(x + w - 1, y,         x + w, y + h,   col);
    }

    // ── Die ───────────────────────────────────────────────────────────────────
    /**
     * d20 face-forward hexagonal projection.
     *
     * Point-top hexagon, vertices clockwise from top:
     *   0=top, 1=upper-right, 2=lower-right, 3=bottom, 4=lower-left, 5=upper-left
     *
     * 4 visible triangular faces filling the hexagon:
     *   Central (v0,v2,v4) — downward-pointing FRONT FACE, brightest, main number
     *   Nook A  (v0,v1,v2) — upper-right adjacent face
     *   Nook B  (v2,v3,v4) — lower adjacent face (darkest)
     *   Nook C  (v4,v5,v0) — upper-left adjacent face
     */
    private void drawDie(GuiGraphicsExtractor g, float cx, float cy, float r,
                         int num, boolean hovered, boolean dimmed) {
        int sides = context.dice().getSides();
        float dimFactor = dimmed ? 0.35f : 1.0f;

        float[] vx = new float[6], vy = new float[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60.0 * i - 90.0);
            vx[i] = cx + r * (float)Math.cos(a);
            vy[i] = cy + r * (float)Math.sin(a);
        }

        int outerCol  = applyBrightness(skin.outerColor(),  dimFactor);
        int centerCol = applyBrightness(skin.centerColor(), dimFactor);
        int facetCol  = applyBrightness(skin.facetColor(),  dimFactor);
        int borderCol = applyBrightness(hovered ? skin.hoverBorderColor() : skin.borderColor(), dimFactor);

        TotalityGuiRenderer.fillPolygon(g, vx, vy, 6, outerCol, outerCol);

        float[] cfx = { vx[0], vx[2], vx[4] };
        float[] cfy = { vy[0], vy[2], vy[4] };
        TotalityGuiRenderer.fillPolygon(g, cfx, cfy, 3,
                applyBrightness(centerCol, 0.92f),
                applyBrightness(centerCol, 1.25f));

        float[] nafx = { vx[0], vx[1], vx[2] };
        float[] nafy = { vy[0], vy[1], vy[2] };
        TotalityGuiRenderer.fillPolygon(g, nafx, nafy, 3,
                applyBrightness(outerCol, 0.90f),
                applyBrightness(centerCol, 0.72f));

        float[] ncfx = { vx[4], vx[5], vx[0] };
        float[] ncfy = { vy[4], vy[5], vy[0] };
        TotalityGuiRenderer.fillPolygon(g, ncfx, ncfy, 3,
                applyBrightness(outerCol, 0.75f),
                applyBrightness(centerCol, 0.60f));

        float[] nbfx = { vx[2], vx[3], vx[4] };
        float[] nbfy = { vy[2], vy[3], vy[4] };
        TotalityGuiRenderer.fillPolygon(g, nbfx, nbfy, 3,
                applyBrightness(outerCol, 0.60f),
                applyBrightness(centerCol, 0.48f));

        TotalityGuiRenderer.drawLine(g, vx[0], vy[0], vx[2], vy[2], 1.0f, facetCol);
        TotalityGuiRenderer.drawLine(g, vx[2], vy[2], vx[4], vy[4], 1.0f, facetCol);
        TotalityGuiRenderer.drawLine(g, vx[4], vy[4], vx[0], vy[0], 1.0f, facetCol);

        for (int i = 0; i < 6; i++)
            TotalityGuiRenderer.drawLine(g, vx[i], vy[i], vx[(i+1)%6], vy[(i+1)%6], 1.2f, borderCol);

        boolean showAdj = !dimmed
                && (phase != Phase.RESULT || resultPhase == ResultPhase.SHOW_NATURAL)
                && sides >= 6;
        if (showAdj) {
            float naCx = (vx[0]+vx[1]+vx[2]) / 3f, naCy = (vy[0]+vy[1]+vy[2]) / 3f;
            float nbCx = (vx[2]+vx[3]+vx[4]) / 3f, nbCy = (vy[2]+vy[3]+vy[4]) / 3f;
            float ncCx = (vx[4]+vx[5]+vx[0]) / 3f, ncCy = (vy[4]+vy[5]+vy[0]) / 3f;
            int adjA = ((num - 1 +  4) % sides) + 1;
            int adjB = ((num - 1 +  9) % sides) + 1;
            int adjC = ((num - 1 + 14) % sides) + 1;
            drawTiny(g, String.valueOf(adjA), centerX(adjA, (int)naCx), (int)naCy - 3, C_ADJ_A);
            drawTiny(g, String.valueOf(adjB), centerX(adjB, (int)nbCx), (int)nbCy - 3, C_ADJ_B);
            drawTiny(g, String.valueOf(adjC), centerX(adjC, (int)ncCx), (int)ncCy - 3, C_ADJ_C);
        }

        int numCol = dimmed ? applyBrightness(dieNumColor(), dimFactor) : dieNumColor();
        float mainCx = (vx[0]+vx[2]+vx[4]) / 3f;
        float mainCy = (vy[0]+vy[2]+vy[4]) / 3f;
        g.pose().pushMatrix();
        g.pose().scale(1.5f, 1.5f);
        String numStr = String.valueOf(num);
        g.text(font, Component.literal(numStr),
                Math.round(mainCx / 1.5f - font.width(numStr) / 2.0f),
                Math.round((mainCy - r * 0.18f) / 1.5f),
                numCol, false);
        g.pose().popMatrix();
    }

    private int centerX(int num, int cx) {
        return cx - (int)(font.width(String.valueOf(num)) * 0.65f / 2f);
    }

    // ── Outcome text ──────────────────────────────────────────────────────────

    private void drawOutcome(GuiGraphicsExtractor g, int y) {
        if (receivedResult == null) return;
        String text = switch (receivedResult.outcome()) {
            case CRITICAL_SUCCESS -> "Critical Success!";
            case SUCCESS          -> "Success";
            case FAILURE          -> "Failure";
            case CRITICAL_FAILURE -> "Critical Failure";
        };
        int color = switch (receivedResult.outcome()) {
            case CRITICAL_SUCCESS -> C_GOLD_BRIGHT;
            case SUCCESS          -> C_GREEN;
            case FAILURE          -> C_RED;
            case CRITICAL_FAILURE -> C_CRIMSON;
        };
        if (receivedResult.outcome() == RollOutcome.CRITICAL_SUCCESS
                || receivedResult.outcome() == RollOutcome.CRITICAL_FAILURE) {
            color = applyBrightness(color, (float)(0.80 + 0.20 * Math.sin(Util.getMillis() / 200.0)));
        }
        drawLarge(g, text.toUpperCase(), width / 2, y + 4, color);
    }

    // ── Bonus section ─────────────────────────────────────────────────────────

    private void drawBonusSection(GuiGraphicsExtractor g, int y) {
        var bonuses  = context.bonuses();
        int maxPerRow = Math.max(1, PANEL_W / (CARD_W + CARD_GAP));
        int rows      = (bonuses.size() + maxPerRow - 1) / maxPerRow;

        for (int row = 0; row < rows; row++) {
            int start = row * maxPerRow, end = Math.min(start + maxPerRow, bonuses.size());
            int count = end - start;
            int rowW  = count * CARD_W + (count - 1) * CARD_GAP;
            int rx    = width / 2 - rowW / 2;
            int ry    = y + row * (CARD_H + CARD_GAP);
            for (int i = start; i < end; i++)
                drawBonusCard(g, rx + (i - start) * (CARD_W + CARD_GAP), ry,
                        CARD_W, CARD_H, bonuses.get(i), i == activeBonusIdx);
        }
        // "Total Bonus" label at bottom
        int labelY = y + rows * (CARD_H + CARD_GAP);
        drawSmallC(g, "Total Bonus  " + (context.totalBonus() >= 0 ? "+" : "") + context.totalBonus(),
                width / 2, labelY, C_GOLD_DIM);
    }

    private void drawBonusCard(GuiGraphicsExtractor g, int x, int y, int w, int h,
                               DiceBonus b, boolean active) {
        g.fill(x, y, x + w, y + h, C_BONUS_BG);
        if (active) {
            for (int i = -1; i <= 0; i++)
                border1px(g, x + i, y + i, w - i*2, h - i*2,
                        applyAlpha(C_BONUS_ACT, i == 0 ? 1.0f : 0.5f));
        } else {
            border1px(g, x, y, w, h, C_BONUS_BDR);
        }

        drawSmallC(g, b.valueString(), x + w / 2, y + 3, active ? C_GOLD_BRIGHT : C_GOLD);
        g.fill(x + w/2 - 8, y + 14, x + w/2 + 8, y + 28, 0xFF1A1408);

        String lbl  = b.label();
        int    maxW = (int)((w - 2) / 0.65f);
        if (font.width(lbl) > maxW) {
            int sp = lbl.lastIndexOf(' ');
            if (sp > 0) {
                drawTiny(g, lbl.substring(0, sp),
                        x + w/2 - (int)(font.width(lbl.substring(0, sp)) * 0.65f / 2),
                        y + h - 14, C_GOLD_DIM);
                drawTiny(g, lbl.substring(sp + 1),
                        x + w/2 - (int)(font.width(lbl.substring(sp + 1)) * 0.65f / 2),
                        y + h - 7, C_GOLD_DIM);
                return;
            }
        }
        drawTiny(g, lbl, x + w/2 - (int)(font.width(lbl) * 0.65f / 2), y + h - 8, C_GOLD_DIM);
    }

    // ── Continue button ───────────────────────────────────────────────────────

    private void drawContinue(GuiGraphicsExtractor g, int mx, int my) {
        int bw = PANEL_W - 16, bh = CONT_H;
        int bx = panelX + 8, by = continueY;
        g.fill(bx, by, bx + bw, by + bh, 0xFF0E0820);
        drawOrnateBorder(g, bx, by, bw, bh);
        // White text, properly centered vertically in the button
        drawSmallC(g, "Continue", width / 2, by + (bh - 6) / 2, C_WHITE);
    }

    // ── Colors ────────────────────────────────────────────────────────────────

    private int dieNumColor() {
        if (receivedResult == null) return C_WHITE;
        if (phase != Phase.RESULT || resultPhase != ResultPhase.SHOW_FINAL) return C_WHITE;
        return switch (receivedResult.outcome()) {
            case CRITICAL_SUCCESS -> C_GOLD_BRIGHT;
            case CRITICAL_FAILURE -> C_CRIMSON;
            case SUCCESS          -> C_WHITE;
            case FAILURE          -> C_RED;
        };
    }

    private int dcColor() {
        if (resultPhase != ResultPhase.SHOW_FINAL || receivedResult == null) return C_WHITE;
        return receivedResult.outcome().isSuccess() ? C_GOLD_BRIGHT : C_RED;
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    public void updateAnimation() {
        long now = Util.getMillis();
        switch (phase) {
            case ROLLING -> tickRolling(now);
            case WAITING -> tickWaiting(now);
            case RESULT  -> tickResult(now);
            default -> {}
        }
    }

    private void tickRolling(long now) {
        long elapsed = now - rollStart;
        if (skipRequested && receivedResult != null && elapsed > 500) { enterResult(); return; }
        if (elapsed >= ROLL_MS) {
            if (receivedResult != null) enterResult(); else phase = Phase.WAITING;
            return;
        }
        long iv = elapsed < FAST_MS
                ? FAST_INTERVAL
                : (long)(FAST_INTERVAL + (float)(elapsed - FAST_MS) / (ROLL_MS - FAST_MS) * (SLOW_MAX - FAST_INTERVAL));
        if (now - lastChange > iv) {
            displayNum  = rng.nextInt(context.dice().getSides()) + 1;
            displayNum2 = rng.nextInt(context.dice().getSides()) + 1;
            lastChange  = now;
        }
    }

    private void tickWaiting(long now) {
        if (receivedResult != null) { enterResult(); return; }
        if (now - lastChange > 300) {
            displayNum  = rng.nextInt(context.dice().getSides()) + 1;
            displayNum2 = rng.nextInt(context.dice().getSides()) + 1;
            lastChange  = now;
        }
    }

    private void tickResult(long now) {
        switch (resultPhase) {
            case SHOW_NATURAL -> {
                if (now - resultStart >= NATURAL_MS) {
                    if (context.bonuses().isEmpty()) {
                        transitionToFinal();
                    } else {
                        resultPhase    = ResultPhase.ADDING_BONUSES;
                        resultStart    = now;
                        activeBonusIdx = 0;
                        displayTotal   = receivedResult.usedRoll() + context.bonuses().get(0).value();
                        displayNum     = displayTotal;
                        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f);
                    }
                }
            }
            case ADDING_BONUSES -> {
                int step = (int)((now - resultStart) / BONUS_STEP_MS);
                if (step != activeBonusIdx) {
                    activeBonusIdx = step;
                    if (step < context.bonuses().size()) {
                        displayTotal = receivedResult.usedRoll();
                        for (int i = 0; i <= step && i < context.bonuses().size(); i++)
                            displayTotal += context.bonuses().get(i).value();
                        displayNum = displayTotal;
                        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f + step * 0.1f);
                    } else {
                        transitionToFinal();
                    }
                }
            }
            case SHOW_FINAL -> {}
        }
    }

    private void enterResult() {
        phase          = Phase.RESULT;
        resultPhase    = ResultPhase.SHOW_NATURAL;
        resultStart    = Util.getMillis();
        activeBonusIdx = -1;
        displayNum  = receivedResult.roll1();
        displayNum2 = receivedResult.roll2();
        displayTotal   = receivedResult.usedRoll();
        if (receivedResult.outcome() == RollOutcome.CRITICAL_SUCCESS)
            playSound(SoundEvents.PLAYER_LEVELUP, 1.4f);
        else if (receivedResult.outcome() == RollOutcome.CRITICAL_FAILURE)
            playSound(SoundEvents.GLASS_BREAK, 0.6f);
    }

    private void transitionToFinal() {
        resultPhase    = ResultPhase.SHOW_FINAL;
        activeBonusIdx = -1;
        recalcLayout();
        if (receivedResult.outcome().isSuccess())
            playSound(SoundEvents.PLAYER_LEVELUP, 1.0f);
        else
            playSound(SoundEvents.VILLAGER_NO, 1.0f);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean dc) {
        int mx = (int)mouse.x(), my = (int)mouse.y();

        if (phase == Phase.IDLE && isDieHovered(mx, my)) { startRolling(); return true; }

        if (phase == Phase.ROLLING) {
            if (receivedResult != null) enterResult(); else skipRequested = true;
            return true;
        }

        if (resultPhase == ResultPhase.SHOW_FINAL) {
            int bx = panelX + 8, bw = PANEL_W - 16;
            if (mx >= bx && mx <= bx + bw && my >= continueY && my <= continueY + CONT_H)
            { onClose(); return true; }
        }
        return super.mouseClicked(mouse, dc);
    }

    private boolean isDieHovered(int mx, int my) {
        return mx >= dieCx - DIE_R - 4 && mx <= dieCx + DIE_R + 4
                && my >= dieCy - DIE_R - 4 && my <= dieCy + DIE_R + 4;
    }

    private void startRolling() {
        phase      = Phase.ROLLING;
        rollStart  = Util.getMillis();
        lastChange = rollStart;
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
        ClientPlayNetworking.send(new DiceRollClickPayload(sessionId));
    }

    public void receiveResult(DiceRollResult result) {
        this.receivedResult = result;
        if (phase == Phase.WAITING) enterResult();
        else if (skipRequested && phase == Phase.ROLLING) enterResult();
    }

    // ── Sound ─────────────────────────────────────────────────────────────────

    private static void playSound(net.minecraft.sounds.SoundEvent e, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch));
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    private void drawHuge(GuiGraphicsExtractor g, String t, int cx, int y, int col) {
        g.pose().pushMatrix(); g.pose().scale(2f, 2f);
        g.text(font, Component.literal(t), cx / 2 - font.width(t) / 2, y / 2, col, false);
        g.pose().popMatrix();
    }

    private void drawLarge(GuiGraphicsExtractor g, String t, int cx, int y, int col) {
        g.pose().pushMatrix(); g.pose().scale(1.5f, 1.5f);
        g.text(font, Component.literal(t), Math.round(cx / 1.5f) - font.width(t) / 2,
                Math.round(y / 1.5f), col, false);
        g.pose().popMatrix();
    }

    private void drawSmallC(GuiGraphicsExtractor g, String t, int cx, int y, int col) {
        g.pose().pushMatrix(); g.pose().scale(0.75f, 0.75f);
        g.text(font, Component.literal(t), Math.round(cx / 0.75f) - font.width(t) / 2,
                Math.round(y / 0.75f), col, false);
        g.pose().popMatrix();
    }

    private void drawTiny(GuiGraphicsExtractor g, String t, int x, int y, int col) {
        g.pose().pushMatrix(); g.pose().scale(0.65f, 0.65f);
        g.text(font, Component.literal(t), Math.round(x / 0.65f), Math.round(y / 0.65f), col, false);
        g.pose().popMatrix();
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    private static int applyBrightness(int argb, float f) {
        int a  = (argb >> 24) & 0xFF;
        int r  = Math.min(255, (int)(((argb >> 16) & 0xFF) * f));
        int gr = Math.min(255, (int)(((argb >>  8) & 0xFF) * f));
        int b  = Math.min(255, (int)(( argb        & 0xFF) * f));
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private static int applyAlpha(int rgb, float alpha) {
        int a = Math.min(255, (int)(((rgb >> 24) & 0xFF) * alpha));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return true;  }
}