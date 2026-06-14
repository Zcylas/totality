package zcylas.totality.client.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.networking.dialogue.ShowDialogueStatePayload;
import zcylas.totality.screen.dialogue.DialogueScreen;
import zcylas.totality.screen.dice.DiceRollScreen;

public final class ClientDialogueManager {

    @Nullable
    private static ShowDialogueStatePayload pendingUpdate = null;

    private ClientDialogueManager() {}

    public static void handle(ShowDialogueStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();

        if (payload.ended() && payload.npcText().getString().isEmpty()) {
            if (mc.screen instanceof DialogueScreen) mc.setScreen(null);
            pendingUpdate = null;
            return;
        }

        if (mc.screen instanceof DialogueScreen ds) {
            ds.applyUpdate(payload);
        } else if (mc.screen instanceof DiceRollScreen) {
            pendingUpdate = payload;
        } else {
            mc.setScreen(new DialogueScreen(payload));
        }
    }

    /** Called when the DiceRollScreen closes so any pending dialogue state can be shown. */
    public static void onDiceScreenClosed() {
        if (pendingUpdate != null) {
            ShowDialogueStatePayload update = pendingUpdate;
            pendingUpdate = null;
            Minecraft.getInstance().execute(() -> {
                if (update.ended()) return;
                Minecraft.getInstance().setScreen(new DialogueScreen(update));
            });
        }
    }
}
