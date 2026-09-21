package zcylas.totality.client.mining;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.mining.MiningTuning;

/**
 * PLACEHOLDER Power Mining meter: a plain procedural bar under the crosshair, so the mechanic is playable.
 * It only READS {@link ClientMiningController#isMeterActive()} / {@link ClientMiningController#meterValue()}
 * and draws; it holds no gameplay rule. The intended final presentation (a sprite-based circular ring around
 * the crosshair, in the crosshair-context HUD style, not an Ability) can replace {@link #draw} without
 * touching the controller or the server.
 */
public final class PowerMiningMeterHud {

    private PowerMiningMeterHud() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "power_mining_meter"),
                (graphics, delta) -> {
                    if (ClientMiningController.isMeterActive()) draw(graphics, ClientMiningController.meterValue());
                });
    }

    private static void draw(GuiGraphicsExtractor graphics, float value) {
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        int barW = 60, barH = 6, x = (w - barW) / 2, y = h / 2 + 16;
        graphics.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xFF000000);
        graphics.fill(x, y, x + barW, y + barH, 0xFF303030);
        graphics.fill(x + Math.round(barW * MiningTuning.RED_ZONE), y, x + barW, y + barH, 0xFF5A1E1E);
        graphics.fill(x, y, x + Math.round(barW * value), y + barH,
                value >= MiningTuning.RED_ZONE ? 0xFFE03030 : 0xFFE0B040);
    }
}
