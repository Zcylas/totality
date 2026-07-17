package zcylas.totality.client.renderer.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Post-migration correction pass: the exact same root cause as
 * {@link zcylas.totality.client.renderer.hud.notification.NotificationManager}'s regression —
 * {@link #tick()} used to be called from {@code TotalityHudRenderer}'s HUD render callback, which
 * fires once per rendered FRAME, not once per game TICK. {@code git diff 953a677} confirms
 * {@code TotalityHudRenderer}'s Power Attack flash block is byte-for-byte unchanged from
 * pre-migration (only the unrelated {@code options.hideGui} rename touched that method at all), so
 * this was never a migration-introduced logic change — it was always frame-rate dependent, just
 * far less noticeable under 26.1.2's slower rendering pipeline. At a 10-tick ({@code
 * FLASH_DURATION}) budget, a high post-migration frame rate can burn through the entire flash in a
 * fraction of a real second — visually indistinguishable from "never appears," which is exactly
 * what was reported.
 *
 * <p>Fix: {@link #tick()} now runs from {@link ClientTickEvents#END_CLIENT_TICK} (registered via
 * {@link #register()}), exactly once per game tick — {@code TotalityHudRenderer} only reads {@link
 * #isActive()}/{@link #getAlpha()} to draw, it never calls {@link #tick()} itself. The visual
 * itself (a full-screen translucent orange tint, not a literal corner-only vignette) is otherwise
 * completely unchanged — confirmed via the same diff to be the original, intended effect, not
 * something to redesign.
 */
public class PowerAttackFlash {
    private static float flashTimer = 0f;
    private static final float FLASH_DURATION = 10f; // ticks

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.gui.hud.isHidden()) return;
            tick();
        });
        // No stale flash may survive into the next world/server — mirrors
        // NotificationManager's identical disconnect-reset requirement.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void trigger() {
        flashTimer = FLASH_DURATION;
    }

    /** The sole place {@code flashTimer} is decremented — called only from
     *  {@link ClientTickEvents#END_CLIENT_TICK}, never from a render/extraction callback, so the
     *  effect's duration is frame-rate independent by construction. */
    private static void tick() {
        if (flashTimer > 0) flashTimer--;
    }

    public static float getAlpha() {
        if (flashTimer <= 0) return 0f;
        return (flashTimer / FLASH_DURATION) * 0.4f;
    }

    public static boolean isActive() {
        return flashTimer > 0;
    }

    /** Clears any in-progress flash — used on disconnect and by
     *  {@code PowerAttackFlashVerification} to reset shared static state between checks. */
    static void reset() {
        flashTimer = 0f;
    }

    // ── Test-only hooks (PowerAttackFlashVerification) ─────────────────────────────────────────
    static void tickForTest() {
        tick();
    }

    static float flashTimerForTest() {
        return flashTimer;
    }

    private PowerAttackFlash() {}
}
