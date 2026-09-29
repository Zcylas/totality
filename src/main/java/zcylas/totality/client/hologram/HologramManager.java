package zcylas.totality.client.hologram;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;

/**
 * Client entry point of Notification V2 System holograms: floating, client-private, world-space
 * panels for significant System events. Routine messages stay in the top-left feed
 * ({@code NotificationManager}); holograms are for moments that deserve the player's attention.
 *
 * <p><b>Interaction</b> never captures the cursor or blocks movement/combat:
 * <ul>
 *   <li><b>Touch (left click).</b> Rest the crosshair on a hologram button (it lights up and arms
 *       within ~0.2 s) and press Attack. The click is consumed only when an armed button is aimed at,
 *       and the Attack key is then released for this press, so it never also swings, attacks, charges
 *       a Power Attack or breaks the block behind the panel. A crosshair merely sweeping across the
 *       panel, or resting where the panel appeared, never arms anything; otherwise Attack works exactly
 *       as normal.</li>
 *   <li><b>Voice</b> ({@link HologramVoice}): holograms that opt in accept their own button words
 *       ("Confirm", "Cancel") by push-to-talk. Mouse interaction always remains available.</li>
 * </ul>
 * Neither works while a screen is open.
 *
 * <p><b>Lifecycle</b> ({@link HologramStack}): priority interruption with suspend/resume, in-place
 * updates by key, lifetime counted only while the player can see the hologram, and a full reset on
 * disconnect. Nothing here touches gameplay state; action handlers belong to the owning system.
 */
public final class HologramManager {

    private static final HologramStack STACK = new HologramStack(System::nanoTime, new HologramStack.Listener() {
        @Override
        public void opened(HologramStack.Entry entry, boolean resumed) {
            HologramRenderer.onOpened();
            HologramSound sound = entry.spec().openSound();
            if (sound != null && !resumed) play(sound);
            if (resumed) play(HologramSound.OPEN);
        }

        @Override
        public void updated(HologramStack.Entry entry) {
            HologramSound sound = entry.spec().openSound();
            if (sound != null) play(sound);
        }

        @Override
        public void closing(HologramStack.Entry entry, HologramStack.CloseReason reason) {
            if (reason == HologramStack.CloseReason.DISMISSED) play(HologramSound.CLOSE);
        }
    });

    /** How long the crosshair must rest on a button before a left click activates it. */
    static final long ARM_NANOS = 220_000_000L;

    private static @Nullable ClientLevel lastLevel;

    private HologramManager() {}

    /** Called once from {@code TotalityClient}. */
    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(HologramManager::consumeAimedAttack);
        ClientTickEvents.END_CLIENT_TICK.register(HologramManager::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        HologramVoice.register();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Shows, queues, or updates (same key) a System hologram. Client thread only. */
    public static HologramStack.ShowResult show(HologramSpec spec) {
        return STACK.show(spec);
    }

    /** Closes (if displayed) or drops (if waiting/suspended) the hologram with this key. */
    public static boolean dismiss(String key) {
        return STACK.dismiss(key, HologramStack.CloseReason.CANCELLED);
    }

    /** Drops every hologram immediately, without animation. */
    public static void clear() {
        STACK.clear();
        HologramRenderer.onOpened();
    }

    static HologramStack stack() {
        return STACK;
    }

    // ── Read-only diagnostics (showcase / capture checks) ─────────────────────

    public static @Nullable String activeKey() {
        HologramStack.Entry a = STACK.active();
        return a == null ? null : a.spec().key();
    }

    public static @Nullable HologramStack.Phase activePhase() {
        HologramStack.Entry a = STACK.active();
        return a == null ? null : a.phase();
    }

    public static @Nullable String hoveredAction() {
        HologramStack.Entry a = STACK.active();
        return a == null ? null : a.hoveredAction();
    }

    public static int suspendedCount() {
        return STACK.suspendedCount();
    }

    /** Which pass drew the displayed hologram last frame: first_person or third_person. */
    public static String placement() {
        return HologramRenderer.lastPlacement;
    }

    /** From the crosshair's point on the panel plane to a button's centre, in panel units; null if unknown. */
    public static float @Nullable [] aimOffsetTo(String actionId) {
        HologramStack.Entry a = STACK.active();
        float[] centre = HologramRenderer.buttonCentre(actionId);
        if (a == null || centre == null || Float.isNaN(a.aimX)) return null;
        return new float[] {centre[0] - a.aimX, centre[1] - a.aimY};
    }

    /** 0..1: how far the aimed button has armed (1 = Use will activate it). */
    static float armProgress(HologramStack.Entry entry, long now) {
        if (entry.hoveredAction == null) return 0;
        return Math.min(1, (now - entry.hoverStartNanos) / (float) ARM_NANOS);
    }

    // ── Tick & input ──────────────────────────────────────────────────────────

    private static void tick(Minecraft client) {
        if (client.level != lastLevel) {
            // New world or dimension: re-project in front of the player rather than where the old
            // camera was; the holograms themselves (client-private) survive a dimension change.
            lastLevel = client.level;
            HologramRenderer.onOpened();
        }
        boolean inWorld = client.player != null && client.level != null;
        boolean canSee = inWorld && client.gui.screen() == null && !client.gui.hud.isHidden() && !client.isPaused();
        STACK.tick(canSee);
    }

    /**
     * START of the client tick — before vanilla, Totality combat and Totality mining read Attack — so a
     * click aimed at an armed hologram button is taken here and never reaches an attack or block break.
     */
    private static void consumeAimedAttack(Minecraft client) {
        HologramStack.Entry active = STACK.active();
        if (active == null || client.player == null || client.gui.screen() != null) return;
        String aimed = active.hoveredAction;
        if (aimed == null || active.phase != HologramStack.Phase.SHOWN || armProgress(active, STACK.now()) < 1) return;
        boolean clicked = false;
        while (client.options.keyAttack.consumeClick()) clicked = true;
        if (!clicked) return;
        // Everything else reads keyAttack.isDown() (vanilla continueAttack/mining, Totality mining and
        // Power Attack hold): releasing it here means this press can never swing, charge or mine, even
        // while the button is still held.
        client.options.keyAttack.setDown(false);
        activate(active, aimed);
        // Visual only: the arm reaches out to touch the projection. The two-argument swing on the client
        // only starts the local animation — no attack, no swing packet, nothing another player sees.
        client.player.swing(InteractionHand.MAIN_HAND, false);
    }

    /** The one action path for clicks and voice commands. Client thread. */
    static void activate(HologramStack.Entry entry, String actionId) {
        entry.pressedAction = actionId;
        entry.pressedNanos = STACK.now();
        HologramSpec spec = entry.spec();
        boolean dismiss = HologramAction.DISMISS.equals(actionId);
        if (!dismiss) play(HologramSound.SELECT);
        STACK.dismissActive(dismiss ? HologramStack.CloseReason.DISMISSED : HologramStack.CloseReason.ACTION);
        HologramSpec.ActionHandler handler = spec.actionHandler();
        if (handler == null) return;
        try {
            handler.onAction(spec, actionId);
        } catch (RuntimeException e) {
            Totality.LOGGER.error("[Totality Hologram] action '{}' of '{}' failed", actionId, spec.id(), e);
        }
    }

    static void play(HologramSound sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sound.id), 1.0f, 1.0f));
    }
}
