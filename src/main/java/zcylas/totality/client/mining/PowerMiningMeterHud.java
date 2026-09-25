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
 *
 * <p><b>Playtest-correction pass, §12:</b> the track is now visibly split into the same four real
 * bands {@link MiningTuning#presentationBand} resolves gameplay from — WHITE / GREEN / ORANGE /
 * RED — at the exact same thresholds ({@link MiningTuning#WHITE_ZONE_MAX},
 * {@link MiningTuning#GREEN_ZONE_MAX}, {@link MiningTuning#RED_ZONE}), so the HUD can never drift
 * from what a release at that charge actually resolves to. No threshold was invented here; all
 * three are the same tuning constants gameplay already uses.
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

        // Four background zone tracks — WHITE / GREEN / ORANGE / RED, split at the exact same
        // thresholds MiningTuning#presentationBand resolves gameplay from.
        int greenStart = x + Math.round(barW * MiningTuning.WHITE_ZONE_MAX);
        int orangeStart = x + Math.round(barW * MiningTuning.GREEN_ZONE_MAX);
        int redStart = x + Math.round(barW * MiningTuning.RED_ZONE);
        graphics.fill(x, y, greenStart, y + barH, 0xFF4A4A4A);
        graphics.fill(greenStart, y, orangeStart, y + barH, 0xFF2E4A20);
        graphics.fill(orangeStart, y, redStart, y + barH, 0xFF5A3A14);
        graphics.fill(redStart, y, x + barW, y + barH, 0xFF5A1E1E);

        graphics.fill(x, y, x + Math.round(barW * value), y + barH, fillColor(value));
    }

    /** Fill colour for the current charge — matches {@link MiningTuning#presentationBand}'s zone exactly. */
    private static int fillColor(float value) {
        if (value >= MiningTuning.RED_ZONE) return 0xFFE03030;
        if (value >= MiningTuning.GREEN_ZONE_MAX) return 0xFFE0902A;
        if (value >= MiningTuning.WHITE_ZONE_MAX) return 0xFF60D050;
        return 0xFFE0E0E0;
    }
}
