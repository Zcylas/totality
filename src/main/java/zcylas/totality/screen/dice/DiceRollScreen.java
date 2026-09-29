// screen/dice/DiceRollScreen.java
package zcylas.totality.screen.dice;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.api.client.gui.TotalityGuiRenderer;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.DiceRollContext;
import zcylas.totality.api.dice.DiceRollResult;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.client.dialogue.ClientDialogueManager;
import zcylas.totality.client.dice.D20Renderer;
import zcylas.totality.client.dice.DiceRollPresentation;
import zcylas.totality.client.dice.DiceRollPresentation.Phase;
import zcylas.totality.client.dice.DiceRollTheme;
import zcylas.totality.client.dice.DiceTumble;
import zcylas.totality.networking.dice.DiceRollClickPayload;
import zcylas.totality.networking.dice.DiceRollResultClientHandler;

import java.util.List;
import java.util.UUID;

/**
 * Dice Roll UI V2: the player-facing dice check, in Totality's System style. A real, numbered d20 tumbles in 3D and
 * lands on the server's natural roll; the kept value, each modifier, the total against the DC and the verdict follow
 * (with advantage/disadvantage both dice are shown and the discarded one dims). The timeline and every shown value
 * come from {@link DiceRollPresentation}; this screen only draws it and forwards input.
 *
 * <p>Controls: click the die / E / Enter / Space rolls; any click or key during the animation skips to the result;
 * Continue (or E / Enter / Space / Esc) closes. Esc before the roll rolls and skips — a check can be hurried, never
 * avoided; if this screen is closed any other way before rolling, the roll is still made (see {@link #removed}).
 */
public class DiceRollScreen extends Screen {

    private static final int PANEL_W = 250;
    private static final int PANEL_H = 216;
    private static final int ARENA_R = 46;
    private static final int BOX_W = 46;
    private static final int BOX_H = 40;

    private final UUID sessionId;
    private final DiceRollPresentation show;

    private int panelX, panelY, panelW;
    private int arenaX, arenaY;

    public DiceRollScreen(UUID sessionId, DiceRollContext context) {
        super(Component.empty());
        this.sessionId = sessionId;
        this.show = new DiceRollPresentation(sessionId, context, Util.getMillis(), sessionId.getLeastSignificantBits());
    }

    public UUID sessionId() {
        return sessionId;
    }

    /** What this screen is showing (read-only use: the capture run compares it with the server's result). */
    public DiceRollPresentation presentation() {
        return show;
    }

    @Override
    protected void init() {
        super.init();
        layout();
    }

    private void layout() {
        panelW = Math.min(PANEL_W, width - 12);
        panelX = (width - panelW) / 2;
        panelY = Math.max(4, (height - PANEL_H) / 2);
        arenaX = width / 2;
        arenaY = panelY + 92;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long now = Util.getMillis();
        layout();
        playCues(now);
        Phase phase = show.phase(now);
        float open = ease(Math.min(1f, (now - show.openedAt()) / (float) DiceRollPresentation.OPEN_MS));

        g.fill(0, 0, width, height, DiceRollTheme.argb(0.74f * open, 0x01040A));

        g.pose().pushMatrix();
        float shake = shake(now);
        g.pose().translate(width / 2f + shake, panelY + PANEL_H / 2f);
        g.pose().scale(0.94f + 0.06f * open, 0.94f + 0.06f * open);
        g.pose().translate(-width / 2f, -(panelY + PANEL_H / 2f));

        panel(g, open);
        if (open < 1) {
            float y = panelY + PANEL_H * open;
            TotalityGuiRenderer.drawLine(g, panelX + 2, y, panelX + panelW - 2, y, 1.5f, DiceRollTheme.argb(1 - open, DiceRollTheme.HIGHLIGHT));
        }
        header(g, open);
        arena(g, now, phase, open);
        dice(g, now, phase, mx, my, open);
        boxes(g, now, phase, open);
        status(g, now, phase, open);
        chips(g, now, phase, open);
        outcome(g, now, phase);
        footer(g, now, phase, mx, my, open);
        g.pose().popMatrix();
    }

    private void panel(GuiGraphicsExtractor g, float a) {
        int x = panelX, y = panelY, w = panelW, h = PANEL_H;
        TotalityGuiRenderer.fillRoundShadow(g, x, y, w, h, 3, 8, DiceRollTheme.argb(0.55f * a, 0x000000));
        TotalityGuiRenderer.fillVerticalGradient(g, x, y, w, h,
                DiceRollTheme.argb(0.94f * a, DiceRollTheme.mix(DiceRollTheme.SURFACE, DiceRollTheme.ACCENT, 0.06f)),
                DiceRollTheme.argb(0.94f * a, DiceRollTheme.SURFACE));
        TotalityGuiRenderer.drawRectOutline(g, x - 1, y - 1, w + 1, h + 1, 1f, DiceRollTheme.argb(0.18f * a, DiceRollTheme.ACCENT));
        TotalityGuiRenderer.drawRectOutline(g, x, y, w - 1, h - 1, 1f, DiceRollTheme.argb(0.75f * a, DiceRollTheme.ACCENT));
        int c = 10;
        int bright = DiceRollTheme.argb(a, DiceRollTheme.HIGHLIGHT);
        for (int[] k : new int[][]{{x, y, 1, 1}, {x + w, y, -1, 1}, {x, y + h, 1, -1}, {x + w, y + h, -1, -1}}) {
            TotalityGuiRenderer.drawLine(g, k[0], k[1], k[0] + k[2] * c, k[1], 2f, bright);
            TotalityGuiRenderer.drawLine(g, k[0], k[1], k[0], k[1] + k[3] * c, 2f, bright);
        }
        TotalityGuiRenderer.fillHorizontalGradient(g, x + 8, y + 40, w / 2 - 8, 1,
                DiceRollTheme.argb(0f, DiceRollTheme.ACCENT), DiceRollTheme.argb(0.6f * a, DiceRollTheme.ACCENT));
        TotalityGuiRenderer.fillHorizontalGradient(g, x + w / 2, y + 40, w / 2 - 8, 1,
                DiceRollTheme.argb(0.6f * a, DiceRollTheme.ACCENT), DiceRollTheme.argb(0f, DiceRollTheme.ACCENT));
    }

    private void header(GuiGraphicsExtractor g, float a) {
        DiceRollContext ctx = show.context();
        String subtype = ctx.checkSubtype();
        String tag = subtype.contains("Saving Throw") ? "SAVING THROW" : subtype.endsWith("Check") ? "ABILITY CHECK" : "ROLL";
        text(g, "[ " + tag + " ]", width / 2f, panelY + 5, 0.65f, DiceRollTheme.argb(0.9f * a, DiceRollTheme.ACCENT));
        text(g, ctx.checkName(), width / 2f, panelY + 13, 1.5f, DiceRollTheme.argb(a, DiceRollTheme.TITLE));
        text(g, subtype, width / 2f, panelY + 29, 0.75f, DiceRollTheme.argb(0.85f * a, DiceRollTheme.BODY));
    }

    private void arena(GuiGraphicsExtractor g, long now, Phase phase, float a) {
        boolean live = phase == Phase.ROLLING;
        float spin = now / 4000f;
        ring(g, arenaX, arenaY, ARENA_R, 1f, DiceRollTheme.argb((live ? 0.5f : 0.3f) * a, DiceRollTheme.ACCENT), 48);
        ring(g, arenaX, arenaY, ARENA_R - 5, 0.8f, DiceRollTheme.argb(0.14f * a, DiceRollTheme.ACCENT), 48);
        for (int i = 0; i < 24; i++) {
            double ang = spin + i * Math.PI / 12;
            float len = i % 3 == 0 ? 4 : 2;
            float x1 = arenaX + (float) Math.cos(ang) * (ARENA_R - 1), y1 = arenaY + (float) Math.sin(ang) * (ARENA_R - 1);
            float x2 = arenaX + (float) Math.cos(ang) * (ARENA_R - 1 - len), y2 = arenaY + (float) Math.sin(ang) * (ARENA_R - 1 - len);
            TotalityGuiRenderer.drawLine(g, x1, y1, x2, y2, 0.8f, DiceRollTheme.argb(0.45f * a, DiceRollTheme.ACCENT));
        }
        if (show.context().rollType() != RollType.NORMAL) {
            boolean adv = show.context().rollType() == RollType.ADVANTAGE;
            text(g, adv ? "ADVANTAGE" : "DISADVANTAGE", arenaX, arenaY - ARENA_R + 7, 0.6f,
                    DiceRollTheme.argb(0.9f * a, adv ? DiceRollTheme.SUCCESS : DiceRollTheme.FAILURE));
        }
        // landing burst
        long landed = show.landedAt();
        if (landed > 0 && !show.skipped() && now >= landed && now - landed < 520) {
            float p = (now - landed) / 520f;
            ring(g, arenaX, arenaY, ARENA_R * (0.55f + 0.5f * p), 1.4f, DiceRollTheme.argb((1 - p) * 0.9f, DiceRollTheme.HIGHLIGHT), 40);
            for (int i = 0; i < 12; i++) {
                double ang = i * Math.PI / 6 + 0.3;
                float r0 = ARENA_R * (0.45f + 0.35f * p), r1 = r0 + 6 * (1 - p);
                TotalityGuiRenderer.drawLine(g, arenaX + (float) Math.cos(ang) * r0, arenaY + (float) Math.sin(ang) * r0,
                        arenaX + (float) Math.cos(ang) * r1, arenaY + (float) Math.sin(ang) * r1, 1f,
                        DiceRollTheme.argb(1 - p, DiceRollTheme.HIGHLIGHT));
            }
        }
        if (show.outcomeShown(now) && show.result().outcome() == RollOutcome.CRITICAL_SUCCESS) {
            float t = (now - show.outcomeStart()) / 1000f;
            float alpha = Math.min(1f, t * 3) * 0.22f;
            for (int i = 0; i < 10; i++) {
                double ang = t * 0.6 + i * Math.PI / 5;
                float[] xs = {arenaX, arenaX + (float) Math.cos(ang - 0.12) * ARENA_R, arenaX + (float) Math.cos(ang + 0.12) * ARENA_R};
                float[] ys = {arenaY, arenaY + (float) Math.sin(ang - 0.12) * ARENA_R, arenaY + (float) Math.sin(ang + 0.12) * ARENA_R};
                TotalityGuiRenderer.fillPolygon(g, xs, ys, 3, DiceRollTheme.argb(0f, DiceRollTheme.CRITICAL_SUCCESS),
                        DiceRollTheme.argb(alpha, DiceRollTheme.CRITICAL_SUCCESS));
            }
        }
    }

    private void dice(GuiGraphicsExtractor g, long now, Phase phase, int mx, int my, float a) {
        DiceTumble[] dice = show.dice();
        boolean two = dice.length == 2;
        float radius = two ? 21 : 28;
        boolean hover = !show.rollRequested() && dieHovered(mx, my);
        DiceRollResult result = show.result();
        boolean revealed = result != null && phase != Phase.ROLLING;
        for (int i = 0; i < dice.length; i++) {
            DiceTumble d = dice[i];
            float baseX = arenaX + (two ? (i == 0 ? -24 : 24) : 0);
            float h = d.height(now);
            float x = baseX + d.offsetX(now) * radius * 0.9f;
            float y = arenaY + d.offsetY(now) * radius * 0.9f - h * radius * 0.35f;
            float r = radius * (1 + 0.28f * h);
            D20Renderer.shadow(g, x, arenaY + radius * 0.92f, radius * 0.85f * (1 - 0.25f * h), radius * 0.22f, 0.5f * a * (1 - 0.5f * h));
            float speed = d.spinSpeed(now);
            if (speed > 9) {
                D20Renderer.ghost(g, d.orientation(now - 34), x, y, r, DiceRollTheme.ACCENT, 0.05f * a);
                D20Renderer.ghost(g, d.orientation(now - 17), x, y, r, DiceRollTheme.ACCENT, 0.09f * a);
            }
            boolean kept = !two || show.keptDie() < 0 || show.keptDie() == i;
            float dim = revealed && !kept ? 0.75f : 0f;
            int highlight = 0, highlightRgb = DiceRollTheme.HIGHLIGHT;
            float highlightAlpha = 0;
            if (revealed && kept) {
                highlight = d.number();
                highlightAlpha = 0.9f;
                if (show.outcomeShown(now)) highlightRgb = DiceRollTheme.outcomeColor(result.outcome());
            }
            boolean cracked = show.outcomeShown(now) && kept && result.outcome() == RollOutcome.CRITICAL_FAILURE;
            float numberAlpha = Math.max(0.3f, Math.min(1f, 1.4f - speed / 9f));
            int edge = hover ? 0xFFFFFF : DiceRollTheme.HIGHLIGHT;
            Quaternionf q = d.orientation(now);
            D20Renderer.draw(g, font, q, x, y, r, new D20Renderer.Look(a * (dim > 0 ? 0.75f : 1f), edge, numberAlpha,
                    highlight, highlightRgb, highlightAlpha * a, dim, cracked));
            if (revealed && two && !kept) {
                TotalityGuiRenderer.drawLine(g, x - r * 0.7f, y + r * 0.7f, x + r * 0.7f, y - r * 0.7f, 1.2f,
                        DiceRollTheme.argb(0.7f * a, DiceRollTheme.FAILURE));
            }
        }
    }

    private void boxes(GuiGraphicsExtractor g, long now, Phase phase, float a) {
        int dcX = arenaX - 88 - BOX_W / 2, totX = arenaX + 88 - BOX_W / 2, y = arenaY - BOX_H / 2;
        DiceRollResult result = show.result();
        box(g, dcX, y, DiceRollTheme.argb(0.7f * a, DiceRollTheme.ACCENT), a);
        text(g, "DIFFICULTY", dcX + BOX_W / 2f, y + 4, 0.55f, DiceRollTheme.argb(0.85f * a, DiceRollTheme.BODY));
        text(g, String.valueOf(show.context().dc()), dcX + BOX_W / 2f, y + 16, 2f, DiceRollTheme.argb(a, DiceRollTheme.TITLE));

        int total = show.totalShown(now);
        int accent = show.outcomeShown(now) ? DiceRollTheme.outcomeColor(result.outcome()) : DiceRollTheme.ACCENT;
        box(g, totX, y, DiceRollTheme.argb(0.7f * a, accent), a);
        text(g, "TOTAL", totX + BOX_W / 2f, y + 4, 0.55f, DiceRollTheme.argb(0.85f * a, DiceRollTheme.BODY));
        float pop = 1f;
        float p = show.modifierProgress(now);
        if (p >= 0.6f && p < 0.85f) pop = 1f + 0.25f * (float) Math.sin((p - 0.6f) / 0.25f * Math.PI);
        if (show.outcomeShown(now)) {
            float t = (now - show.outcomeStart()) / 250f;
            if (t < 1) pop = 1f + 0.3f * (float) Math.sin(t * Math.PI);
        }
        String value = total == Integer.MIN_VALUE ? (show.rollRequested() ? "?" : "-") : String.valueOf(total);
        int color = show.outcomeShown(now) ? DiceRollTheme.outcomeColor(result.outcome()) : DiceRollTheme.TITLE;
        text(g, value, totX + BOX_W / 2f, y + 16 - (pop - 1) * 6, 2f * pop, DiceRollTheme.argb(a, color));
    }

    private void status(GuiGraphicsExtractor g, long now, Phase phase, float a) {
        float y = arenaY + ARENA_R + 5;
        DiceRollResult r = show.result();
        String line;
        int color = DiceRollTheme.BODY;
        if (!show.rollRequested()) {
            line = show.dice().length == 2 ? "Click a die or press [E] to roll both" : "Click the die or press [E] to roll";
            color = DiceRollTheme.TITLE;
        } else if (r == null || phase == Phase.ROLLING) {
            line = show.timedOut(now) ? "No answer from the server  -  [Esc] to close" : "Rolling...";
        } else if (show.outcomeShown(now)) {
            StringBuilder sb = new StringBuilder().append(r.usedRoll());
            for (DiceBonus b : show.bonuses()) sb.append(b.value() >= 0 ? " + " : " - ").append(Math.abs(b.value()));
            sb.append(" = ").append(r.total()).append("   vs   DC ").append(r.context().dc());
            line = sb.toString();
            color = DiceRollTheme.TITLE;
        } else if (show.dice().length == 2) {
            int kept = r.usedRoll(), other = show.keptDie() == 0 ? r.roll2() : r.roll1();
            line = (r.context().rollType() == RollType.ADVANTAGE ? "Advantage" : "Disadvantage") + ": kept " + kept + ", discarded " + other;
        } else {
            line = "Natural " + r.usedRoll();
        }
        text(g, line, width / 2f, y, 0.75f, DiceRollTheme.argb(a, color));
    }

    private void chips(GuiGraphicsExtractor g, long now, Phase phase, float a) {
        List<DiceBonus> bonuses = show.bonuses();
        int y = arenaY + ARENA_R + 16;
        if (bonuses.isEmpty()) {
            text(g, "No modifiers", width / 2f, y + 6, 0.65f, DiceRollTheme.argb(0.6f * a, DiceRollTheme.BODY));
            return;
        }
        int n = bonuses.size(), gap = 4;
        int w = Math.min(62, (panelW - 20 - (n - 1) * gap) / n), h = 22;
        int x0 = width / 2 - (n * w + (n - 1) * gap) / 2;
        int applied = show.modifiersApplied(now);
        float p = show.modifierProgress(now);
        for (int i = 0; i < n; i++) {
            DiceBonus b = bonuses.get(i);
            int x = x0 + i * (w + gap);
            boolean active = p >= 0 && i == applied - 1;
            boolean done = i < applied && !active;
            int border = active ? DiceRollTheme.argb(a, DiceRollTheme.HIGHLIGHT)
                    : DiceRollTheme.argb((done ? 0.7f : 0.35f) * a, DiceRollTheme.ACCENT);
            TotalityGuiRenderer.fillRect(g, x, y, w, h, DiceRollTheme.argb(0.7f * a, DiceRollTheme.mix(DiceRollTheme.SURFACE, DiceRollTheme.ACCENT, active ? 0.18f : 0.05f)));
            TotalityGuiRenderer.drawRectOutline(g, x, y, w, h, 1f, border);
            text(g, b.valueString(), x + w / 2f, y + 3, 1f, DiceRollTheme.argb(a, active || done ? DiceRollTheme.TITLE : DiceRollTheme.BODY));
            String label = b.label();
            float scale = 0.55f;
            while (font.width(label) * scale > w - 4 && label.length() > 3) label = label.substring(0, label.length() - 2) + ".";
            text(g, label, x + w / 2f, y + 14, scale, DiceRollTheme.argb(0.85f * a, DiceRollTheme.BODY));
            if (active && p < 0.6f) {
                float t = ease(p / 0.6f);
                float sx = x + w / 2f, sy = y + 3, tx = arenaX + 88, ty = arenaY;
                text(g, b.valueString(), sx + (tx - sx) * t, sy + (ty - sy) * t - 10 * (float) Math.sin(t * Math.PI), 1.2f,
                        DiceRollTheme.argb(1 - t, DiceRollTheme.HIGHLIGHT));
            }
        }
    }

    private void outcome(GuiGraphicsExtractor g, long now, Phase phase) {
        if (!show.outcomeShown(now)) return;
        RollOutcome o = show.result().outcome();
        float t = Math.min(1f, (now - show.outcomeStart()) / 260f);
        float scale = 1.75f * (1 + 0.6f * (1 - ease(t)));
        int color = DiceRollTheme.outcomeColor(o);
        float y = panelY + 183;
        float sweep = ease(Math.min(1f, (now - show.outcomeStart()) / 450f));
        TotalityGuiRenderer.fillHorizontalGradient(g, width / 2 - (int) (90 * sweep), (int) y + 15, (int) (90 * sweep), 1,
                DiceRollTheme.argb(0f, color), DiceRollTheme.argb(0.9f, color));
        TotalityGuiRenderer.fillHorizontalGradient(g, width / 2, (int) y + 15, (int) (90 * sweep), 1,
                DiceRollTheme.argb(0.9f, color), DiceRollTheme.argb(0f, color));
        text(g, DiceRollTheme.outcomeText(o), width / 2f, y + 6 - 4.5f * scale, scale, DiceRollTheme.argb(t, color));
    }

    private void footer(GuiGraphicsExtractor g, long now, Phase phase, int mx, int my, float a) {
        if (show.canContinue(now)) {
            int bw = 110, bh = 14, bx = width / 2 - bw / 2, by = panelY + PANEL_H - 19;
            boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            TotalityGuiRenderer.fillRect(g, bx, by, bw, bh, DiceRollTheme.argb(0.8f, DiceRollTheme.mix(DiceRollTheme.SURFACE, DiceRollTheme.ACCENT, hover ? 0.3f : 0.12f)));
            TotalityGuiRenderer.drawRectOutline(g, bx, by, bw, bh, 1f, DiceRollTheme.argb(1f, hover ? DiceRollTheme.HIGHLIGHT : DiceRollTheme.ACCENT));
            text(g, "Continue   [E]", width / 2f, by + 4, 0.75f, DiceRollTheme.argb(1f, DiceRollTheme.TITLE));
        } else if (show.rollRequested() && !show.timedOut(now)) {
            text(g, "Click / [E]: skip", panelX + panelW - 32, panelY + PANEL_H - 10, 0.55f, DiceRollTheme.argb(0.55f * a, DiceRollTheme.BODY));
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void box(GuiGraphicsExtractor g, int x, int y, int border, float a) {
        TotalityGuiRenderer.fillRect(g, x, y, BOX_W, BOX_H, DiceRollTheme.argb(0.55f * a, DiceRollTheme.SURFACE));
        TotalityGuiRenderer.drawRectOutline(g, x, y, BOX_W, BOX_H, 1f, border);
        TotalityGuiRenderer.drawLine(g, x + 3, y + BOX_H + 2, x + BOX_W - 3, y + BOX_H + 2, 1f, DiceRollTheme.argb(0.25f * a, DiceRollTheme.ACCENT));
    }

    private static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float thickness, int color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            TotalityGuiRenderer.drawLine(g, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, thickness, color);
        }
    }

    /** Text centred on {@code cx}, top at {@code y}, at {@code scale}. */
    private void text(GuiGraphicsExtractor g, String s, float cx, float y, float scale, int argb) {
        if ((argb >>> 24) < 5) return;
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);
        g.text(font, Component.literal(s), -font.width(s) / 2, 0, argb, false);
        g.pose().popMatrix();
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    /** A short jolt when a critical failure is revealed. */
    private float shake(long now) {
        if (!show.outcomeShown(now) || show.result().outcome() != RollOutcome.CRITICAL_FAILURE) return 0;
        float t = (now - show.outcomeStart()) / 320f;
        return t >= 1 ? 0 : (float) Math.sin(t * 38) * 2.5f * (1 - t);
    }

    private boolean dieHovered(int mx, int my) {
        int reach = show.dice().length == 2 ? 50 : 34;
        return Math.abs(mx - arenaX) <= reach && Math.abs(my - arenaY) <= 34;
    }

    // ── Sound ─────────────────────────────────────────────────────────────────

    private void playCues(long now) {
        for (DiceRollPresentation.Cue cue : show.cues(now)) {
            switch (cue) {
                case OPEN -> DiceRollTheme.openCue();
                case ROLL -> DiceRollTheme.selectCue();
                case BOUNCE -> DiceRollTheme.play(SoundEvents.AMETHYST_BLOCK_HIT, 1.2f + (float) Math.random() * 0.5f);
                case LAND -> DiceRollTheme.play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f);
                case MODIFIER -> DiceRollTheme.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f + show.modifiersApplied(now) * 0.1f);
                case OUTCOME -> {
                    switch (show.result().outcome()) {
                        case CRITICAL_SUCCESS -> DiceRollTheme.play(SoundEvents.PLAYER_LEVELUP, 1.4f);
                        case CRITICAL_FAILURE -> DiceRollTheme.play(SoundEvents.GLASS_BREAK, 0.6f);
                        case SUCCESS -> DiceRollTheme.play(SoundEvents.PLAYER_LEVELUP, 1.0f);
                        case FAILURE -> DiceRollTheme.play(SoundEvents.VILLAGER_NO, 1.0f);
                    }
                }
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        long now = Util.getMillis();
        int mx = (int) mouse.x(), my = (int) mouse.y();
        if (show.canContinue(now)) {
            int bw = 110, bh = 14, bx = width / 2 - bw / 2, by = panelY + PANEL_H - 19;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) confirmClose();
            return true;
        }
        if (!show.rollRequested()) {
            if (dieHovered(mx, my) && show.requestRoll(now)) sendClick();
            return true;
        }
        if (show.skip(now)) sendClick();
        return true;
    }

    // Mirrors DialogueScreen's E/Enter convention — so the whole roll+dialogue flow can be driven with a single key.
    @Override
    public boolean keyPressed(KeyEvent input) {
        long now = Util.getMillis();
        boolean escape = input.key() == GLFW.GLFW_KEY_ESCAPE;
        boolean confirm = Minecraft.getInstance().options.keyInventory.matches(input)
                || input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_SPACE;
        if (!escape && !confirm) return super.keyPressed(input);
        if (show.canContinue(now)) {
            confirmClose();
        } else if (escape && show.timedOut(now)) {
            onClose();
        } else if (!show.rollRequested() && !escape) {
            if (show.requestRoll(now)) sendClick();
        } else if (show.skip(now)) {
            sendClick();
        }
        return true;
    }

    private void confirmClose() {
        DiceRollTheme.closeCue();
        onClose();
        // after closing: a dialogue waiting behind the roll then opens last, whether it is shown at once or deferred
        ClientDialogueManager.onDiceScreenClosed();
    }

    /** Closed without rolling (replaced by another screen, or any other way): the roll is still made. */
    @Override
    public void removed() {
        if (show.requestRoll(Util.getMillis())) sendClick();
        super.removed();
        DiceRollResultClientHandler.closed(sessionId, show.rollRequested(), show.result() != null);
    }

    private void sendClick() {
        if (ClientPlayNetworking.canSend(DiceRollClickPayload.TYPE)) {
            ClientPlayNetworking.send(new DiceRollClickPayload(sessionId));
        }
    }

    /** The server's result for a session; ignored unless it is this screen's first result. */
    public boolean receiveResult(UUID session, DiceRollResult result) {
        return show.acceptResult(session, result, Util.getMillis());
    }

    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
