package zcylas.totality.screen.ability;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final float PRECISION  = 3.0f;

    private final List<Identifier> favIds;
    private int selectedSlot = -1;

    public AbilityRadialScreen() {
        super(Component.literal(""));
        this.favIds = List.copyOf(ClientAbilityManager.getFavorites());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // transparent — game world renders behind
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
        double dy   = my - cy;  // standard screen coords (positive = down)
        double dist = Math.sqrt(dx * dx + dy * dy);

        selectedSlot = -1;
        if (dist >= RADIUS_IN && dist <= RADIUS_OUT) {
            double nx = dx / dist;
            double ny = dy / dist;
            float bestDot = -2f;
            for (int i = 0; i < n; i++) {
                float centerDeg = -90f + i * (360f / n);
                float rad = (float) Math.toRadians(centerDeg);
                float slotNx = Mth.cos(rad);
                float slotNy = Mth.sin(rad);
                float dot = (float)(nx * slotNx + ny * slotNy);
                if (dot > bestDot) {
                    bestDot = dot;
                    selectedSlot = i;
                }
            }
        }

        // ── Draw slices ───────────────────────────────────────────────────────
        for (int i = 0; i < n; i++) {
            float halfSlice = 360f / n / 2f;
            float centerDeg = -90f + i * (360f / n);
            float startDeg  = centerDeg - halfSlice + 0.5f; // tiny gap between slices
            float endDeg    = centerDeg + halfSlice - 0.5f;

            boolean sel = (i == selectedSlot);

            // Ars Nouveau style: dark transparent ring, cyan tint on selected
            int r  = sel ? 63  : 0;
            int gr = sel ? 161 : 0;
            int b  = sel ? 191 : 0;
            int al = sel ? 90  : 55;

            drawSlice(g, cx, cy, startDeg, endDeg, r, gr, b, al);
        }

        // ── Draw icons on ring ────────────────────────────────────────────────
        float itemRadius = (RADIUS_IN + RADIUS_OUT) * 0.5f;
        for (int i = 0; i < n; i++) {
            float centerDeg = -90f + i * (360f / n);
            float rad = (float) Math.toRadians(centerDeg);
            int ix = (int)(cx + itemRadius * Mth.cos(rad));
            int iy = (int)(cy + itemRadius * Mth.sin(rad));

            Ability ab = AbilityRegistry.get(favIds.get(i));
            if (ab != null && ab.getIcon() != null) {
                boolean sel = (i == selectedSlot);
                int sz  = sel ? 20 : 16;
                int off = sz / 2;
                g.blit(RenderPipelines.GUI_TEXTURED, ab.getIcon(),
                        ix - off, iy - off, 0f, 0f, sz, sz, sz, sz);
            }
        }

        // ── Selected ability name centered ────────────────────────────────────
        if (selectedSlot >= 0 && selectedSlot < n) {
            Ability ab = AbilityRegistry.get(favIds.get(selectedSlot));
            if (ab != null) {
                String name = ab.getDisplayName();
                int nameW = font.width(name);
                g.text(font, Component.literal(name),
                        cx - nameW / 2, cy - 4, 0xFFFFFFFF, true);

                // Subtext: type
                String type = ab.getType().name();
                int typeW = Math.round(font.width(type) * 0.85f);
                g.pose().pushMatrix();
                g.pose().scale(0.85f, 0.85f);
                g.text(font, Component.literal(type),
                        Math.round((cx - typeW / 2) / 0.85f),
                        Math.round((cy + 6) / 0.85f),
                        0xFF88DDFF, false);
                g.pose().popMatrix();
            }
        }
    }

    private void drawSlice(GuiGraphicsExtractor g, int cx, int cy,
                           float startDeg, float endDeg,
                           int r, int gr, int b, int alpha) {
        float start    = (float) Math.toRadians(startDeg);
        float end      = (float) Math.toRadians(endDeg);
        float range    = end - start;
        int sections   = Math.max(1, (int) Math.ceil(
                Math.abs(Math.toDegrees(range)) / PRECISION));
        int color      = (alpha << 24) | (r << 16) | (gr << 8) | b;

        // Render each section as overlapping fill quads to approximate the arc
        int steps = (int)(RADIUS_OUT - RADIUS_IN);
        for (int i = 0; i < sections; i++) {
            float a1 = start + (i       / (float) sections) * range;
            float a2 = start + ((i + 1) / (float) sections) * range;

            for (int s = 0; s <= steps; s++) {
                float t   = s / (float) steps;
                float rad = RADIUS_IN + t * (RADIUS_OUT - RADIUS_IN);
                int lx1   = cx + (int)(rad * Mth.cos(a1));
                int ly1   = cy + (int)(rad * Mth.sin(a1));
                int lx2   = cx + (int)(rad * Mth.cos(a2));
                int ly2   = cy + (int)(rad * Mth.sin(a2));
                g.fill(Math.min(lx1, lx2), Math.min(ly1, ly2),
                        Math.max(lx1, lx2) + 1, Math.max(ly1, ly2) + 1,
                        color);
            }
        }
    }

    @Override
    public void tick() {
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        boolean altHeld = InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
        if (!altHeld) {
            if (selectedSlot >= 0 && selectedSlot < favIds.size()) {
                ClientPlayNetworking.send(new EquipAbilityPayload(favIds.get(selectedSlot)));
            }
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (selectedSlot >= 0 && selectedSlot < favIds.size()) {
            ClientPlayNetworking.send(new EquipAbilityPayload(favIds.get(selectedSlot)));
            Minecraft.getInstance().setScreen(null);
        }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi()    { return true;  }
}