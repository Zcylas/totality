package zcylas.totality.screen.ability;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.ability.EquipAbilityPayload;

import java.util.List;

public class AbilityRadialScreen extends Screen {

    private static final float RADIUS_IN  = 42f;
    private static final float RADIUS_OUT = 88f;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int COL_OVERLAY        = 0x88000000; // full-screen dim
    private static final int COL_RING           = 0xCC0D0D20; // dark navy ring
    private static final int COL_RING_SELECTED  = 0xCC1A4060; // cyan-tinted highlight
    private static final int COL_DIVIDER        = 0xFF000000; // black slice borders
    private static final int COL_CENTER         = 0x99000000; // inner circle fill
    private static final int COL_NAME           = 0xFFFFFFFF;
    private static final int COL_TYPE           = 0xFF88DDFF;

    private final List<Identifier> favIds;
    private int selectedSlot = -1;

    public AbilityRadialScreen() {
        super(Component.literal(""));
        this.favIds = List.copyOf(ClientAbilityManager.getFavorites());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // transparent — world renders behind
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        if (favIds.isEmpty()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }

        int cx = width  / 2;
        int cy = height / 2;
        int n  = favIds.size();

        // ── Detect hovered slice ──────────────────────────────────────────────
        double dx   = mx - cx;
        double dy   = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        selectedSlot = -1;
        if (dist >= RADIUS_IN && dist <= RADIUS_OUT) {
            double nx = dx / dist, ny = dy / dist;
            float bestDot = -2f;
            for (int i = 0; i < n; i++) {
                float rad = (float) Math.toRadians(-90f + i * (360f / n));
                float dot = (float)(nx * Mth.cos(rad) + ny * Mth.sin(rad));
                if (dot > bestDot) { bestDot = dot; selectedSlot = i; }
            }
        }

        // ── Full-screen dark overlay ──────────────────────────────────────────
        g.fill(0, 0, width, height, COL_OVERLAY);

        // ── Base ring ─────────────────────────────────────────────────────────
        drawRing(g, cx, cy, COL_RING);

        // ── Selected slice highlight ──────────────────────────────────────────
        if (selectedSlot >= 0) {
            float half      = 360f / n / 2f;
            float centerDeg = -90f + selectedSlot * (360f / n);
            drawRingSlice(g, cx, cy, centerDeg - half + 0.5f, centerDeg + half - 0.5f,
                    COL_RING_SELECTED);
        }

        // ── Slice dividers ────────────────────────────────────────────────────
        for (int i = 0; i < n; i++) {
            float borderAngle = -90f + i * (360f / n) - 360f / n / 2f;
            drawDivider(g, cx, cy, borderAngle, COL_DIVIDER);
        }

        // ── Inner circle (cleans up the center hole) ──────────────────────────
        drawDisk(g, cx, cy, (int)(RADIUS_IN - 1), COL_CENTER);

        // ── Icons ─────────────────────────────────────────────────────────────
        float iconRadius = (RADIUS_IN + RADIUS_OUT) * 0.5f;
        for (int i = 0; i < n; i++) {
            float rad = (float) Math.toRadians(-90f + i * (360f / n));
            int ix = (int)(cx + iconRadius * Mth.cos(rad));
            int iy = (int)(cy + iconRadius * Mth.sin(rad));

            Ability ab = AbilityRegistry.get(favIds.get(i));
            if (ab != null && ab.getIcon() != null) {
                int sz  = (i == selectedSlot) ? 20 : 16;
                int off = sz / 2;
                g.blit(RenderPipelines.GUI_TEXTURED, ab.getIcon(),
                        ix - off, iy - off, 0f, 0f, sz, sz, sz, sz);
            }
        }

        // ── Selected ability name ─────────────────────────────────────────────
        if (selectedSlot >= 0 && selectedSlot < n) {
            Ability ab = AbilityRegistry.get(favIds.get(selectedSlot));
            if (ab != null) {
                String name = ab.getDisplayName();
                g.text(font, Component.literal(name),
                        cx - font.width(name) / 2, cy - 5, COL_NAME, true);

                String type = ab.getType().name();
                g.pose().pushMatrix();
                g.pose().scale(0.85f, 0.85f);
                int typeW = Math.round(font.width(type) * 0.85f);
                g.text(font, Component.literal(type),
                        Math.round((cx - typeW / 2f) / 0.85f),
                        Math.round((cy + 5) / 0.85f),
                        COL_TYPE, false);
                g.pose().popMatrix();
            }
        }
    }

    // ── Ring rendering helpers ────────────────────────────────────────────────

    /**
     * Draws a clean circular ring using a horizontal scan-line approach.
     * For each row, computes the circle intersection analytically and draws
     * two rectangles (left and right caps). O(diameter) fill calls total.
     */
    private void drawRing(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int outerR = (int) RADIUS_OUT;
        int innerR = (int) RADIUS_IN;
        for (int row = -outerR; row <= outerR; row++) {
            int y      = cy + row;
            int outerX = circleX(outerR, row);
            int innerX = (Math.abs(row) <= innerR) ? circleX(innerR, row) : 0;
            if (outerX > innerX) {
                g.fill(cx - outerX, y, cx - innerX, y + 1, color);
                g.fill(cx + innerX, y, cx + outerX, y + 1, color);
            }
        }
    }

    /**
     * Draws a filled disk (solid circle). Used to clean up the center hole.
     */
    private void drawDisk(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int row = -r; row <= r; row++) {
            int x = circleX(r, row);
            if (x > 0) g.fill(cx - x, cy + row, cx + x, cy + row + 1, color);
        }
    }

    /**
     * Draws the highlighted portion of the ring for the selected slice.
     * Uses run-length encoding per row so the total call count stays O(diameter).
     */
    private void drawRingSlice(GuiGraphicsExtractor g, int cx, int cy,
                               float startDeg, float endDeg, int color) {
        float startRad = (float) Math.toRadians(startDeg);
        float endRad   = (float) Math.toRadians(endDeg);
        int outerR = (int) RADIUS_OUT;
        int innerR = (int) RADIUS_IN;

        for (int row = -outerR; row <= outerR; row++) {
            int y      = cy + row;
            int outerX = circleX(outerR, row);
            int innerX = (Math.abs(row) <= innerR) ? circleX(innerR, row) : 0;
            // left cap: [-outerX, -innerX]
            fillSliceRow(g, cx, y, row, -outerX, -innerX, startRad, endRad, color);
            // right cap: [+innerX, +outerX]
            fillSliceRow(g, cx, y, row,  innerX,  outerX, startRad, endRad, color);
        }
    }

    /** Scans one row's x-range, groups pixels inside the slice into runs, draws one fill per run. */
    private void fillSliceRow(GuiGraphicsExtractor g, int cx, int y, int dy,
                              int xMin, int xMax, float startRad, float endRad, int color) {
        int runStart = Integer.MIN_VALUE;
        for (int dx = xMin; dx <= xMax; dx++) {
            if (inSlice(dx, dy, startRad, endRad)) {
                if (runStart == Integer.MIN_VALUE) runStart = dx;
            } else {
                if (runStart != Integer.MIN_VALUE) {
                    g.fill(cx + runStart, y, cx + dx, y + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
        }
        if (runStart != Integer.MIN_VALUE)
            g.fill(cx + runStart, y, cx + xMax + 1, y + 1, color);
    }

    /** Returns true if (dx, dy) falls within the angular slice [startRad, endRad]. */
    private boolean inSlice(int dx, int dy, float startRad, float endRad) {
        if (dx == 0 && dy == 0) return false;
        float angle  = (float) Math.atan2(dy, dx);
        float span   = endRad - startRad;
        float offset = angle - startRad;
        float TWO_PI = (float)(2 * Math.PI);
        offset = ((offset % TWO_PI) + TWO_PI) % TWO_PI;
        return offset <= span;
    }

    /** Draws a thin radial line from innerRadius to outerRadius at the given angle. */
    private void drawDivider(GuiGraphicsExtractor g, int cx, int cy, float angleDeg, int color) {
        float rad = (float) Math.toRadians(angleDeg);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        for (float r = RADIUS_IN; r <= RADIUS_OUT; r++) {
            int px = cx + Math.round(r * cos);
            int py = cy + Math.round(r * sin);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** Horizontal extent of a circle of radius r at vertical offset dy. */
    private static int circleX(int r, int dy) {
        return (int) Math.sqrt(Math.max(0.0, (double)r*r - (double)dy*dy));
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────

    @Override
    public void tick() {
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        boolean altHeld = InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
        if (!altHeld) {
            if (selectedSlot >= 0 && selectedSlot < favIds.size())
                ClientPlayNetworking.send(new EquipAbilityPayload(favIds.get(selectedSlot)));
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (selectedSlot >= 0 && selectedSlot < favIds.size()) {
            ClientPlayNetworking.send(new EquipAbilityPayload(favIds.get(selectedSlot)));
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return false;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi()    { return true;  }
}