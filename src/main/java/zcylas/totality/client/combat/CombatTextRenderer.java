package zcylas.totality.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.util.color.ColorUtils;

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
        float dim = switch (entry.getType()) {
            case RESIST -> 0.6f;
            case IMMUNE -> 0.5f;
            default     -> 1.0f;
        };
        int base   = 0xFF000000 | switch (entry.getType()) {
            case BLOCK_DAMAGE -> blockDamageColor(entry.getStyle());
            case INEFFECTIVE  -> 0xB0B0B0;
            default           -> getTypeColor(entry.getDamageType());
        };
        int dimmed = dim < 1f ? ColorUtils.blend(0xFF000000, base, dim) : base;
        return ColorUtils.setAlpha(dimmed, (int)(alpha * 255));
    }

    /**
     * Structural damage colour by presentation band (playtest-correction pass §13): an ordinary,
     * non-Power hit ({@code MiningTuning.BAND_DEFAULT}, 0) is now WHITE, not the old yellow-ish
     * default — and a genuine Power swing's colour always matches its resolved
     * WHITE/GREEN/ORANGE/RED band exactly, never guessed independently from charge timing on the
     * client. Orange/Red are unchanged from before this pass.
     */
    private static int blockDamageColor(int band) {
        return switch (band) {
            case 1 -> 0xFFFFFF;   // MiningTuning.BAND_WHITE — released too weak for a Power bonus
            case 2 -> 0x6BE05A;   // MiningTuning.BAND_GREEN
            case 3 -> 0xFF8A2A;   // MiningTuning.BAND_ORANGE — unchanged
            case 4 -> 0xFF3030;   // MiningTuning.BAND_RED — unchanged
            default -> 0xFFFFFF;  // MiningTuning.BAND_DEFAULT — an ordinary, non-Power hit is now WHITE
        };
    }

    private static int getTypeColor(TotalityDamageType type) {
        if (type == null) return 0x44DDAA; // fallback: healing green
        return type.getCombatTextColor();
    }
}