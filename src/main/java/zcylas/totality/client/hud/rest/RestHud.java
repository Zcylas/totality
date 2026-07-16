package zcylas.totality.client.hud.rest;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.client.rest.ClientRestManager;

/** Small countdown overlay while a Short/Long Rest is in progress. */
public final class RestHud {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "rest_countdown");

    private static final int C_TEXT  = 0xFFF0E0C0;
    private static final int C_GRACE = 0xFFFF6060;

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.gui.hud.isHidden()) return;
            if (!ClientRestManager.isActive()) return;

            String text;
            int color;
            if (ClientRestManager.isInGrace()) {
                int seconds = ClientRestManager.getGraceRemainingTicks() / 20;
                text = "Interrupted — resume within " + seconds + "s";
                color = C_GRACE;
            } else {
                RestType type = ClientRestManager.getType();
                String label = type == RestType.SHORT ? "Short Rest" : "Long Rest";
                text = "Resting (" + label + ") — " + formatMcTime(ClientRestManager.getRemainingTicks()) + " remaining";
                color = C_TEXT;
            }

            int screenW = client.getWindow().getGuiScaledWidth();
            int textW = client.font.width(text);
            graphics.text(client.font, Component.literal(text), (screenW - textW) / 2, 10, color, true);
        });
    }

    /** 1 MC hour = 1000 ticks. */
    private static String formatMcTime(int ticks) {
        int hours = ticks / 1000;
        int minutes = (ticks % 1000) * 60 / 1000;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    private RestHud() {}
}
