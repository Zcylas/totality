package zcylas.totality.client.voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import zcylas.totality.client.renderer.hud.notification.NotificationManager;

/**
 * "Edit & Send": puts the latest transcript into vanilla's chat input. The player reviews it,
 * edits it if they like, and presses Enter — vanilla then sends it as any typed message (signing,
 * length limit and server moderation unchanged). This class never sends anything.
 *
 * <p>Deliberately NOT {@code Gui.openChatAndAddText}: in 26.2 that ignores its method argument and
 * always opens chat in COMMAND mode, so with no draft the input would read {@code /<transcript>} and
 * Enter would run it as a command. Instead chat is opened in MESSAGE mode through the public
 * {@code ChatComponent.openScreen}, with vanilla's own {@code ChatScreen::new}; the constructor
 * callback reveals whether vanilla is restoring an unsent draft, which is then kept untouched.
 */
final class VoiceChatEditor {

    private VoiceChatEditor() {}

    /** Client thread, no screen open. */
    static void openLatest(Minecraft client, VoiceInputController controller) {
        String transcript = controller.latestTranscript();
        if (transcript == null) {
            NotificationManager.add("No voice transcript yet — hold push-to-talk and speak first.", MinecraftVoiceUi.GRAY);
            return;
        }
        ChatInsertionPolicy.Decision[] decision = {ChatInsertionPolicy.Decision.NOTHING_TO_INSERT};
        client.gui.hud.getChat().openScreen(ChatComponent.ChatMethod.MESSAGE, (initial, isDraft) -> {
            decision[0] = ChatInsertionPolicy.decide(transcript, initial, isDraft);
            return new ChatScreen(initial, isDraft);
        });
        switch (decision[0]) {
            case INSERT -> {
                if (client.gui.screen() instanceof ChatScreen chat) {
                    chat.insertText(ChatInsertionPolicy.textFor(transcript), false);
                }
            }
            case KEEP_DRAFT -> NotificationManager.add("Your unsent chat draft was kept; the transcript was not "
                    + "inserted. Send or clear the draft, then try again.", MinecraftVoiceUi.YELLOW);
            case NOTHING_TO_INSERT -> { }
        }
    }
}
