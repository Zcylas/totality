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
import zcylas.totality.api.magic.spell.ClientSpellSlotManager;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.client.spell.ClientSelectedSpellManager;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.ability.SelectSpellPayload;
import zcylas.totality.util.RadialAnimationHelper;

import java.util.List;

/**
 * Spell selection radial. Hold X to open, release to cast selected spell.
 * Uses {@link RadialAnimationHelper} for a smooth cubic open/close animation.
 */
public class SpellRadialScreen extends Screen {

    private static final float RADIUS_IN  = 42f;
    private static final float RADIUS_OUT = 88f;

    private static final int COL_OVERLAY       = 0x88000000;
    private static final int COL_RING          = 0xCC1A0D30;  // dark purple for spells
    private static final int COL_RING_SELECTED = 0xCC6040A0;  // violet highlight
    private static final int COL_DIVIDER       = 0xFF000000;
    private static final int COL_CENTER        = 0x99000000;
    private static final int COL_NAME          = 0xFFFFFFFF;
    private static final int COL_LEVEL         = 0xFFCCA0FF;  // lavender for spell level
    private static final int COL_PIP_AVAILABLE = 0xFFCCA0FF;  // filled — slot available
    private static final int COL_PIP_USED      = 0xFF6A5A80;  // hollow border only — slot used

    private static final int PIP_SIZE   = 6;
    private static final int PIP_GAP    = 3;
    private static final int PIP_MARGIN = 10; // gap between the ring's top edge and the pip row

    private final List<Identifier> spellIds;
    private int   selectedSlot = -1;
    private float openProgress = 0f;

    public SpellRadialScreen() {
        super(Component.literal(""));
        this.spellIds = List.copyOf(ClientAbilityManager.getSpellFavorites());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        if (spellIds.isEmpty()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }

        // Advance open animation
        openProgress = Math.min(1f, openProgress + 0.15f);
        float scale  = RadialAnimationHelper.smoothstep(openProgress);

        int cx = width  / 2;
        int cy = height / 2;
        int n  = spellIds.size();

        // Detect hovered slice
        double dx   = mx - cx;
        double dy   = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        selectedSlot = -1;
        if (dist >= RADIUS_IN * scale && dist <= RADIUS_OUT * scale) {
            double nx = dx / dist, ny = dy / dist;
            float bestDot = -2f;
            for (int i = 0; i < n; i++) {
                float rad = (float) Math.toRadians(-90f + i * (360f / n));
                float dot = (float)(nx * net.minecraft.util.Mth.cos(rad)
                        + ny * net.minecraft.util.Mth.sin(rad));
                if (dot > bestDot) { bestDot = dot; selectedSlot = i; }
            }
        }

        // Full-screen dim
        g.fill(0, 0, width, height, COL_OVERLAY);

        // Scale everything around center
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);

        drawRing(g, cx, cy, COL_RING);
        if (selectedSlot >= 0) {
            float half      = 360f / n / 2f;
            float centerDeg = -90f + selectedSlot * (360f / n);
            drawRingSlice(g, cx, cy, centerDeg - half + 0.5f, centerDeg + half - 0.5f, COL_RING_SELECTED);
        }
        for (int i = 0; i < n; i++) {
            drawDivider(g, cx, cy, -90f + i * (360f / n) - 360f / n / 2f, COL_DIVIDER);
        }
        drawDisk(g, cx, cy, (int)(RADIUS_IN - 1), COL_CENTER);

        float iconRadius = (RADIUS_IN + RADIUS_OUT) * 0.5f;
        for (int i = 0; i < n; i++) {
            float rad = (float) Math.toRadians(-90f + i * (360f / n));
            int ix = (int)(cx + iconRadius * Mth.cos(rad));
            int iy = (int)(cy + iconRadius * Mth.sin(rad));
            Ability ab = AbilityRegistry.get(spellIds.get(i));
            if (ab != null && ab.getIcon() != null) {
                int sz = (i == selectedSlot) ? 20 : 16;
                g.blit(RenderPipelines.GUI_TEXTURED, ab.getIcon(),
                        ix - sz/2, iy - sz/2, 0f, 0f, sz, sz, sz, sz);
            }
        }

        g.pose().popMatrix();

        // Name + level label (drawn after pop — not scaled, stays at center)
        if (selectedSlot >= 0 && selectedSlot < n) {
            Ability ab = AbilityRegistry.get(spellIds.get(selectedSlot));
            if (ab != null) {
                String name = ab.getDisplayName();
                g.text(font, Component.literal(name),
                        cx - font.width(name) / 2, cy - 5, COL_NAME, true);
                if (ab instanceof Spell spell) {
                    String levelStr = spell.getLevelDisplay();
                    g.pose().pushMatrix();
                    g.pose().scale(0.85f, 0.85f);
                    int lw = Math.round(font.width(levelStr) * 0.85f);
                    g.text(font, Component.literal(levelStr),
                            Math.round((cx - lw / 2f) / 0.85f),
                            Math.round((cy + 5) / 0.85f),
                            COL_LEVEL, false);
                    g.pose().popMatrix();

                    drawSlotIndicator(g, cx, cy, spell);
                }
            }
        }
    }

    /** Filled square = available slot, hollow square = used slot, drawn above the ring.
     *  Cantrips show an infinity symbol instead — they never consume a slot. */
    private void drawSlotIndicator(GuiGraphicsExtractor g, int cx, int cy, Spell spell) {
        int topY = cy - (int) RADIUS_OUT - PIP_MARGIN - PIP_SIZE;

        if (spell.isCantrip()) {
            String inf = "∞";
            g.text(font, Component.literal(inf), cx - font.width(inf) / 2, topY, COL_PIP_AVAILABLE, true);
            return;
        }

        int max = ClientSpellSlotManager.getMax(spell.getSpellLevel());
        if (max <= 0) return;
        int remaining = ClientSpellSlotManager.getRemaining(spell.getSpellLevel());

        int totalW = max * PIP_SIZE + (max - 1) * PIP_GAP;
        int startX = cx - totalW / 2;
        for (int i = 0; i < max; i++) {
            int x = startX + i * (PIP_SIZE + PIP_GAP);
            if (i < remaining) {
                g.fill(x, topY, x + PIP_SIZE, topY + PIP_SIZE, COL_PIP_AVAILABLE);
            } else {
                g.fill(x, topY, x + PIP_SIZE, topY + 1, COL_PIP_USED);                     // top
                g.fill(x, topY + PIP_SIZE - 1, x + PIP_SIZE, topY + PIP_SIZE, COL_PIP_USED); // bottom
                g.fill(x, topY, x + 1, topY + PIP_SIZE, COL_PIP_USED);                     // left
                g.fill(x + PIP_SIZE - 1, topY, x + PIP_SIZE, topY + PIP_SIZE, COL_PIP_USED); // right
            }
        }
    }

    @Override
    public void tick() {
        com.mojang.blaze3d.platform.Window w = Minecraft.getInstance().getWindow();
        boolean spellHeld = InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_X);
        if (!spellHeld) {
            // Only SELECT the spell — tap X to fire it
            if (selectedSlot >= 0 && selectedSlot < spellIds.size()) {
                selectSpell(spellIds.get(selectedSlot));
            }
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean dc) {
        if (selectedSlot >= 0 && selectedSlot < spellIds.size()) {
            selectSpell(spellIds.get(selectedSlot));
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return false;
    }

    /** Updates the client-local selection immediately and tells the server so it persists
     *  across a disconnect (mirrors EquipAbilityPayload for non-spell abilities). */
    private void selectSpell(Identifier id) {
        ClientSelectedSpellManager.setSelectedSpell(id.toString());
        ClientPlayNetworking.send(new SelectSpellPayload(id));
    }

    // ── Ring helpers (identical to AbilityRadialScreen) ───────────────────────

    private void drawRing(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int outerR = (int) RADIUS_OUT, innerR = (int) RADIUS_IN;
        for (int row = -outerR; row <= outerR; row++) {
            int y = cy + row, outerX = circleX(outerR, row);
            int innerX = (Math.abs(row) <= innerR) ? circleX(innerR, row) : 0;
            if (outerX > innerX) {
                g.fill(cx - outerX, y, cx - innerX, y + 1, color);
                g.fill(cx + innerX, y, cx + outerX, y + 1, color);
            }
        }
    }

    private void drawDisk(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int row = -r; row <= r; row++) {
            int x = circleX(r, row);
            if (x > 0) g.fill(cx - x, cy + row, cx + x, cy + row + 1, color);
        }
    }

    private void drawRingSlice(GuiGraphicsExtractor g, int cx, int cy,
                               float startDeg, float endDeg, int color) {
        float sR = (float)Math.toRadians(startDeg), eR = (float)Math.toRadians(endDeg);
        int outerR = (int) RADIUS_OUT, innerR = (int) RADIUS_IN;
        for (int row = -outerR; row <= outerR; row++) {
            int y = cy + row, oX = circleX(outerR, row);
            int iX = (Math.abs(row) <= innerR) ? circleX(innerR, row) : 0;
            fillSliceRow(g, cx, y, row, -oX, -iX, sR, eR, color);
            fillSliceRow(g, cx, y, row,  iX,  oX, sR, eR, color);
        }
    }

    private void fillSliceRow(GuiGraphicsExtractor g, int cx, int y, int dy,
                              int xMin, int xMax, float s, float e, int color) {
        int run = Integer.MIN_VALUE;
        for (int dx = xMin; dx <= xMax; dx++) {
            if (inSlice(dx, dy, s, e)) { if (run == Integer.MIN_VALUE) run = dx; }
            else { if (run != Integer.MIN_VALUE) { g.fill(cx+run, y, cx+dx, y+1, color); run = Integer.MIN_VALUE; } }
        }
        if (run != Integer.MIN_VALUE) g.fill(cx+run, y, cx+xMax+1, y+1, color);
    }

    private boolean inSlice(int dx, int dy, float s, float e) {
        if (dx == 0 && dy == 0) return false;
        float a = (float)Math.atan2(dy, dx), span = e - s;
        float TWO_PI = (float)(2 * Math.PI);
        float off = (((a - s) % TWO_PI) + TWO_PI) % TWO_PI;
        return off <= span;
    }

    private void drawDivider(GuiGraphicsExtractor g, int cx, int cy, float ang, int color) {
        float r = (float)Math.toRadians(ang), cos = Mth.cos(r), sin = Mth.sin(r);
        for (float d = RADIUS_IN; d <= RADIUS_OUT; d++) {
            int px = cx + Math.round(d * cos), py = cy + Math.round(d * sin);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static int circleX(int r, int dy) {
        return (int) Math.sqrt(Math.max(0.0, (double)r*r - (double)dy*dy));
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi()    { return true;  }
}