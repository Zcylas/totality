package zcylas.totality.client.quest;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import zcylas.totality.networking.quest.OpenQuestAppPayload;
import zcylas.totality.networking.quest.QuestEntryDisplayData;
import zcylas.totality.networking.quest.ShowQuestStatePayload;
import zcylas.totality.screen.phone.PhoneFrame;
import zcylas.totality.screen.quest.QuestScreen;

import java.util.List;
import java.util.Optional;

/**
 * Client-side entry point for the Quests app, and the persistent cache the HUD tracker
 * reads from. The server pushes {@code ShowQuestStatePayload} both in response to an
 * explicit "open the app" request AND as a silent background update whenever quest state
 * changes elsewhere (join, equipping the phone, finishing setup) — only the former should
 * pop the screen open, so {@link #awaitingOpen} distinguishes the two.
 */
public final class ClientQuestManager {

    private static PhoneFrame pendingFrame = PhoneFrame.COPPER;
    private static boolean awaitingOpen = false;
    private static List<QuestEntryDisplayData> lastKnownQuests = List.of();

    private ClientQuestManager() {}

    public static void openQuestApp(PhoneFrame frame) {
        pendingFrame = frame;
        awaitingOpen = true;
        ClientPlayNetworking.send(new OpenQuestAppPayload());
    }

    public static void handle(ShowQuestStatePayload payload) {
        lastKnownQuests = payload.quests();

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof QuestScreen qs) {
            qs.applyUpdate(payload);
        } else if (awaitingOpen) {
            awaitingOpen = false;
            mc.gui.setScreen(new QuestScreen(payload, pendingFrame));
        }
        // Otherwise: a background push (join/equip/setup) — cache updated above,
        // no screen opened. The HUD tracker picks it up from getTrackedQuest().
    }

    public static Optional<QuestEntryDisplayData> getTrackedQuest() {
        return lastKnownQuests.stream().filter(q -> q.tracked() && !q.completed()).findFirst();
    }
}
