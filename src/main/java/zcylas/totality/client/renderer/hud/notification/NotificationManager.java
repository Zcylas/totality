package zcylas.totality.client.renderer.hud.notification;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Post-migration correction pass (regression found during Phase 4 manual testing, not by the
 * 26.2 migration task itself): {@code ticksLeft} used to be decremented inside the
 * {@link HudElementRegistry} render callback, which Fabric fires once per RENDERED FRAME, not
 * once per game TICK — confirmed identical (byte-for-byte apart from the unrelated
 * {@code options.hideGui} → {@code gui.hud.isHidden()} rename) between this file's pre-migration
 * version and the pre-fix version of this file via {@code git diff 953a677}, and Fabric's own
 * {@code HudLayer}/{@code HudElementRegistryImpl} invocation bytecode is unchanged between the
 * 26.1.2 and 26.2 {@code fabric-rendering-v1} jars — so this was never a migration-introduced
 * LOGIC change. It was always frame-rate dependent; 26.2's rendering pipeline rewrite (documented
 * in the migration report, §15.3/§15.6b) legitimately achieves a higher sustained frame rate for
 * the same scene, so the same per-frame decrement now burns through the 80-tick (~4 second)
 * budget far faster in real time than it did under 26.1.2's slower pipeline — a genuine
 * post-migration regression in user-visible behavior, even though no notification code changed.
 *
 * <p>Fix: the authoritative {@code ticksLeft} countdown now advances from
 * {@link ClientTickEvents#END_CLIENT_TICK}, which fires exactly once per game tick regardless of
 * frame rate. The HUD render callback ({@link #renderActive}) only READS current state to compute
 * display alpha/position — it never mutates {@code ticksLeft}, so a tick is never advanced more
 * than once, and it can never be advanced from two different places (Part C's shared requirement).
 * The tick and render callbacks share the exact same {@code player == null || hud.isHidden()}
 * guard the original single callback used, so F1 continues to pause the countdown while hidden —
 * unchanged visible behavior, faithfully restored per the original 26.1.2 design intent, not
 * reinvented.
 */
public class NotificationManager {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "notifications");

    // How long each notification lives in ticks (80 ticks = 4 seconds at the fixed 20 tick/sec
    // game tick rate — the model this constant has always assumed, restored to actually being
    // tick-driven rather than frame-driven).
    private static final int LIFETIME_TICKS = 80;
    // How many ticks to fade out over. No fade-IN phase exists — confirmed absent in the
    // pre-migration implementation too (git diff), so none is invented here; only hold then
    // linear fade-out, exactly as originally authored.
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
     * Register the tick and HUD callbacks. Call from client entrypoint.
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.gui.hud.isHidden()) return;
            tickActive();
        });

        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.gui.hud.isHidden()) return;
            renderActive(graphics, client);
        });

        // A leftover notification from a previous world/server (its 80-tick lifetime not yet
        // expired at disconnect) must never survive into the next session — no permanent state
        // may remain after disconnect or client reset (canonical requirement).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    /**
     * Advances every active notification's lifetime by exactly one tick, removing any that have
     * expired. The SOLE place {@code ticksLeft} is mutated — called only from
     * {@link ClientTickEvents#END_CLIENT_TICK}, never from the render/extraction callback, so
     * lifetime is frame-rate independent by construction, not by convention.
     */
    static void tickActive() {
        Iterator<Notification> it = active.iterator();
        while (it.hasNext()) {
            Notification n = it.next();
            n.ticksLeft--;
            if (n.ticksLeft <= 0) {
                it.remove();
            }
        }
    }

    /** Reads current state and draws — never mutates {@code ticksLeft} (Part C: render-state
     *  extraction gathers immutable display data, it does not advance authoritative state). */
    private static void renderActive(GuiGraphicsExtractor graphics, Minecraft client) {
        int y = PADDING_Y;
        for (Notification n : active) {
            float alpha = computeAlpha(n.ticksLeft);
            int finalColor = ((int) (alpha * 255) << 24) | (n.color & 0x00FFFFFF);

            String[] lines = n.message.split("\n");
            for (String line : lines) {
                graphics.text(client.font, line, PADDING_X, y, finalColor, true);
                y += LINE_HEIGHT;
            }
        }
    }

    /** Pure fade computation: full opacity during hold, linear fade-out over the final
     *  {@link #FADE_TICKS} ticks — no fade-in phase (see the class javadoc). */
    static float computeAlpha(int ticksLeft) {
        return ticksLeft < FADE_TICKS ? (float) ticksLeft / FADE_TICKS : 1.0f;
    }

    /** Clears all active notifications — used on disconnect and by
     *  {@code NotificationTimingVerification} to reset shared static state between checks. */
    static void clear() {
        active.clear();
    }

    // ── Test-only hooks (NotificationTimingVerification) ──────────────────────────────────────
    static void addForTest(String message, int color, int ticksLeft) {
        active.add(new Notification(message, color, ticksLeft));
    }

    static int sizeForTest() {
        return active.size();
    }

    static int ticksLeftForTest(int index) {
        return active.get(index).ticksLeft;
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
