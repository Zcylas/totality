package zcylas.totality.client.spell;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import zcylas.totality.api.magic.spell.CastType;

/**
 * Tracks the currently active spell cast on the client.
 *
 * When the player presses the spell cast key on a spell with castTimeTicks > 0,
 * this manager starts a countdown. The {@link CastBarHud} reads from it every
 * frame to render the cast bar under the crosshair.
 *
 * When the cast completes, the actual spell payload is sent to the server.
 * If the cast is interrupted (movement while STATIONARY, damage, etc.),
 * {@link #cancel()} is called and no payload is sent.
 *
 * For INSTANT spells (castTimeTicks == 0) this manager is never involved —
 * the payload fires immediately.
 */
public final class ClientCastManager {

    private static String   spellName    = "";
    private static int      color        = 0xFF88BBFF;
    private static CastType castType     = CastType.INSTANT;
    private static int      totalTicks   = 0;
    private static int      ticksElapsed = 0;
    private static boolean  casting      = false;

    /** Callback invoked when the cast bar completes naturally. */
    private static Runnable onComplete   = null;

    private ClientCastManager() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!casting) return;

            ticksElapsed++;

            // STATIONARY: cancel if player moved this tick
            if (castType == CastType.STATIONARY && hasMoved(client)) {
                cancel();
                return;
            }

            if (ticksElapsed >= totalTicks) {
                Runnable cb = onComplete;
                reset();
                if (cb != null) cb.run();
            }
        });
    }

    /**
     * Starts a cast bar.
     * @param spellName    display name shown above the bar
     * @param color        school color (0xAARRGGBB)
     * @param type         MOBILE or STATIONARY
     * @param castTimeTicks total duration
     * @param onComplete   fired when bar fills — send the spell payload here
     */
    public static void startCast(String spellName, int color, CastType type,
                                 int castTimeTicks, Runnable onComplete) {
        ClientCastManager.spellName    = spellName;
        ClientCastManager.color        = color;
        ClientCastManager.castType     = type;
        ClientCastManager.totalTicks   = castTimeTicks;
        ClientCastManager.ticksElapsed = 0;
        ClientCastManager.casting      = true;
        ClientCastManager.onComplete   = onComplete;
    }

    public static void cancel() {
        reset();
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public static boolean isCasting()     { return casting; }
    public static String  getSpellName()  { return spellName; }
    public static int     getColor()      { return color; }
    public static CastType getCastType()  { return castType; }
    /** 0.0 = just started, 1.0 = complete. */
    public static float   getFraction()  {
        return totalTicks == 0 ? 1f : Math.min(1f, (float) ticksElapsed / totalTicks);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void reset() {
        casting      = false;
        ticksElapsed = 0;
        totalTicks   = 0;
        onComplete   = null;
    }

    /** Very simple movement detection — checks if the player moved this tick. */
    private static boolean hasMoved(Minecraft client) {
        if (client.player == null) return false;
        return client.player.getDeltaMovement().horizontalDistanceSqr() > 0.001;
    }
}