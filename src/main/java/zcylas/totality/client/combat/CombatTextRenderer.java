package zcylas.totality.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamageType;

public final class CombatTextRenderer {

    private CombatTextRenderer() {}

    public static void onExtractGui(Matrix4f viewMatrix,
                                    Matrix4f projMatrix,
                                    CameraRenderState camera,
                                    GuiGraphicsExtractor graphics) {
        if (CombatTextManager.getEntries().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();

        for (CombatTextEntry entry : CombatTextManager.getEntries()) {
            float life = entry.getLifePercent();
            float alpha = life > 0.7f ? 1.0f - ((life - 0.7f) / 0.3f) : 1.0f;
            if (alpha <= 0f) continue;

            float yOffset = life * 1.2f;
            Vec3 worldPos = entry.getWorldPos().add(0, yOffset, 0);

            // World to screen — Ping Wheel's approach
            Vector4f pos = new Vector4f(
                    worldPos.subtract(camera.pos).toVector3f(), 1f);
            viewMatrix.transform(pos);
            projMatrix.transform(pos);

            if (pos.w <= 0) continue; // behind camera

            pos.div(pos.w);

            float screenX = screenW * (0.5f + pos.x * 0.5f);
            float screenY = screenH * (0.5f - pos.y * 0.5f);

            if (screenX < 0 || screenX > screenW ||
                    screenY < 0 || screenY > screenH) continue;

            String text = entry.getDisplayText();
            int color = getColor(entry, alpha);
            int textX = (int)(screenX - mc.font.width(text) / 2f);
            int textY = (int)screenY;

            graphics.text(mc.font, text, textX, textY, color, true);
        }
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> CombatTextManager.tick());
    }

    private static int getColor(CombatTextEntry entry, float alpha) {
        int a = (int)(alpha * 255) << 24;

        float dimFactor = switch (entry.getType()) {
            case RESIST -> 0.6f;
            case IMMUNE -> 0.5f;
            default -> 1.0f;
        };

        TotalityDamageType type = entry.getDamageType();
        int baseColor = getTypeColor(type);

        int r = (int)(((baseColor >> 16) & 0xFF) * dimFactor);
        int g = (int)(((baseColor >> 8)  & 0xFF) * dimFactor);
        int b = (int)((baseColor & 0xFF)          * dimFactor);

        return a | (r << 16) | (g << 8) | b;
    }

    private static int getTypeColor(TotalityDamageType type) {
        if (type == null)                  return 0x00FF44;
        if (type == DamageTypes.FIRE)      return 0xFF4400;
        if (type == DamageTypes.FROST)     return 0x00CCFF;
        if (type == DamageTypes.LIGHTNING) return 0xFFFF00;
        if (type == DamageTypes.POISON)    return 0x44FF44;
        if (type == DamageTypes.RADIANT)   return 0xFFFF88;
        if (type == DamageTypes.NECROTIC)  return 0xAA00FF;
        if (type == DamageTypes.PSYCHIC)   return 0xFF88FF;
        if (type == DamageTypes.FORCE)     return 0x4488FF;
        if (type == DamageTypes.ARCANE)    return 0x00FFCC;
        if (type == DamageTypes.ACID)      return 0x88FF00;
        if (type == DamageTypes.SONIC)     return 0xFFFFFF;
        if (type == DamageTypes.VOID)      return 0x444444;
        return 0xFFFFFF;
    }
}