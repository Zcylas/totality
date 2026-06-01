package zcylas.totality.client.renderer.hud.notification;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "notifications");

    // How long each notification lives in ticks (60 = 3 seconds)
    private static final int LIFETIME_TICKS = 80;
    // How many ticks to fade out over
    private static final int FADE_TICKS = 20;
    // Max notifications visible at once
    private static final int MAX_NOTIFICATIONS = 5;
    // Padding from the top-left corner
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 4;
    // Gap between notifications
    private static final int LINE_HEIGHT = 11;

    private static final List<Notification> active = new ArrayList<>();

    /**
     * Add a notification to the queue.
     * Called from the client packet handler when a SendNotificationPayload is received.
     */
    public static void add(String message, int color) {
        // Drop oldest if at max
        if (active.size() >= MAX_NOTIFICATIONS) {
            active.remove(0);
        }
        active.add(new Notification(message, color, LIFETIME_TICKS));
    }

    /**
     * Register the HUD renderer. Call from client entrypoint.
     */
    public static void register() {
        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) return;

            // Tick down all notifications
            Iterator<Notification> it = active.iterator();
            while (it.hasNext()) {
                Notification n = it.next();
                n.ticksLeft--;
                if (n.ticksLeft <= 0) {
                    it.remove();
                }
            }

            // Render from top-left, stacking downward
            int y = PADDING_Y;
            for (Notification n : active) {
                float alpha = n.ticksLeft < FADE_TICKS
                        ? (float) n.ticksLeft / FADE_TICKS : 1.0f;
                int finalColor = ((int)(alpha * 255) << 24) | (n.color & 0x00FFFFFF);

                String[] lines = n.message.split("\n");
                for (String line : lines) {
                    graphics.text(client.font, line, PADDING_X, y, finalColor, true);
                    y += LINE_HEIGHT;
                }
            }
        });
    }

    private static class Notification {
        final String message;
        final int color;
        int ticksLeft;

        Notification(String message, int color, int ticksLeft) {
            this.message = message;
            this.color   = color;
            this.ticksLeft = ticksLeft;
        }
    }
}