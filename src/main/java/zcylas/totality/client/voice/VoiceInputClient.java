package zcylas.totality.client.voice;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import zcylas.totality.init.ModKeybinds;

/**
 * Connects {@link VoiceInputController} to the game: push-to-talk edges, Edit & Send, the HUD
 * indicator, {@code /totalityvoice}, and cancellation on disconnect, shutdown, screens and focus loss.
 *
 * <p><b>Controls.</b> Push-to-talk (default B) is a System COMMAND (answering a hologram); the Totality
 * modifier (default Left Alt) + push-to-talk is DICTATION. The mode is decided once when the key goes
 * down. Push-to-talk only starts while in a world with no screen open and the window focused, so it can
 * never type into chat or another text field. Opening any screen or losing focus while listening
 * cancels that phrase. Edit & Send (default N) works the same way. Vanilla's debug shortcuts take precedence: while
 * the debug modifier (F3) is held, push-to-talk never starts and an active phrase is cancelled ({@link PushToTalkGate}).
 */
final class VoiceInputClient {

    private static final PushToTalkGate GATE = new PushToTalkGate();

    private VoiceInputClient() {}

    static void register(VoiceInputController controller, VoiceSettings settings) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, controller));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> controller.onDisconnect());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> controller.shutdown());
        VoiceHud.register(controller);
        VoiceCommands.register(controller, settings);
    }

    private static void tick(Minecraft client, VoiceInputController controller) {
        // Only while an opt-in verification/capture hook drives the controller directly (the live hook
        // runs at the title screen, the capture run holds push-to-talk itself), where the gameplay
        // guards below would cancel or release every utterance.
        if (VoiceLiveVerification.isDriving() || VoiceHologramCapture.isDriving()) return;
        boolean inWorld = client.player != null && client.level != null;
        boolean screenOpen = client.gui.screen() != null;
        boolean focused = client.isWindowActive();
        boolean down = inWorld && ModKeybinds.isPhysicallyDown(ModKeybinds.VOICE_PUSH_TO_TALK);
        // Vanilla's debug shortcuts come first: with the debug modifier (F3, rebindable) held, B is F3+B.
        boolean debugModifier = inWorld && ModKeybinds.isPhysicallyDown(client.options.keyDebugModifier);
        PushToTalkGate.Action action = GATE.update(down, debugModifier, inWorld, screenOpen, focused,
                controller.state() == VoiceState.LISTENING);
        if (action == PushToTalkGate.Action.CANCEL) controller.cancelUtterance(null);

        if (controller.state() == VoiceState.LISTENING) {
            if (!inWorld) controller.cancelUtterance(null);
            else if (screenOpen) controller.cancelUtterance("a screen was opened");
            else if (!focused) controller.cancelUtterance("the game window lost focus");
            else if (!down) controller.onPushToTalkReleased();
        }
        if (action == PushToTalkGate.Action.PRESS) {
            // Decided once, at the edge (Totality's modifier-chord convention): modifier held = dictation.
            // Releasing or pressing the modifier afterwards never changes this utterance.
            controller.onPushToTalkPressed(ModKeybinds.isPhysicallyDown(ModKeybinds.RADIAL_MODIFIER)
                    ? VoiceInputController.PushToTalkMode.DICTATION : VoiceInputController.PushToTalkMode.COMMAND);
        }

        while (ModKeybinds.VOICE_EDIT_TRANSCRIPT.consumeClick()) {
            if (inWorld && !screenOpen) VoiceChatEditor.openLatest(client, controller);
        }
    }
}
