package zcylas.totality.client.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Renders the attunement ring next to the cursor, to the LEFT of the tooltip.
 *
 * Called directly from {@link zcylas.totality.mixin.client.AbstractContainerScreenMixin}
 * at the tail of AbstractContainerScreen.render(), which guarantees it draws
 * on top of all inventory content including the Totality tooltip.
 *
 * Tooltips in this game always appear to the RIGHT of the cursor, so the ring
 * always renders to the LEFT.
 */
public final class AttunementHud {

    private static final int   RING_RADIUS = 8;
    private static final int   RING_THICK  = 2;
    private static final int   COLOR_RING  = 0xFFD4AA22;
    private static final int   COLOR_BG    = 0x88000000;
    private static final int   SIDE_GAP    = 12;

    private static final Component LABEL = Component.literal("Attuning...");

    private AttunementHud() {}

    /**
     * No-op — rendering is now handled via
     * {@link zcylas.totality.mixin.client.AbstractContainerScreenMixin}.
     * Left here so existing registration calls don't break.
     */
    public static void register() {}

    /** Called from the screen mixin. No HudElementRegistry needed. */
    public static void renderOnScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, int screenW) {
        if (!AttunementClientManager.isActive()) return;

        boolean unattuning = AttunementClientManager.isUnattuning();
        int ringColor  = unattuning ? 0xFFDD4444 : 0xFFD4AA22; // red vs gold
        Component label = unattuning
                ? Component.literal("Unattuning...")
                : LABEL;

        int cx = Math.max(RING_RADIUS + 2, mouseX - SIDE_GAP - RING_RADIUS);
        int cy = mouseY - RING_RADIUS - 4;

        float fraction = AttunementClientManager.getFraction();

        drawArc(g, cx, cy, RING_RADIUS, RING_THICK, 0, 360, COLOR_BG);
        if (fraction > 0) {
            drawArc(g, cx, cy, RING_RADIUS, RING_THICK, 0, (int)(fraction * 360), ringColor);
        }

        Minecraft mc = Minecraft.getInstance();
        g.text(mc.font, label,
                cx - mc.font.width(label) / 2,
                cy + RING_RADIUS + 3, ringColor, true);
    }

    private static void drawArc(GuiGraphicsExtractor g, int cx, int cy,
                                int radius, int thickness,
                                int startDeg, int endDeg, int color) {
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float f0 = (float) i / segments;
            float f1 = (float)(i + 1) / segments;
            float d0 = startDeg + f0 * (endDeg - startDeg);
            float d1 = startDeg + f1 * (endDeg - startDeg);
            if (d0 >= endDeg) break;

            double a0 = Math.toRadians(d0 - 90);
            double a1 = Math.toRadians(d1 - 90);

            for (int r = radius - thickness; r <= radius; r++) {
                int x0 = cx + (int)(r * Math.cos(a0));
                int y0 = cy + (int)(r * Math.sin(a0));
                int x1 = cx + (int)(r * Math.cos(a1));
                int y1 = cy + (int)(r * Math.sin(a1));
                g.fill(Math.min(x0,x1), Math.min(y0,y1),
                        Math.max(x0,x1)+1, Math.max(y0,y1)+1, color);
            }
        }
    }
}