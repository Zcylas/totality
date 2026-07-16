package zcylas.totality.client.quest;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.networking.quest.QuestEntryDisplayData;

/**
 * Always-visible top-right HUD tracker for the player's currently tracked quest — solves
 * the discoverability problem for quests like First Signal, where the objectives themselves
 * require opening a menu (the Quests app) that the player has no other reason to know exists.
 * Quests auto-track on grant ({@code QuestManager.grantIfMissing}) specifically so this has
 * something to show immediately, without the player ever opening the phone.
 */
public final class QuestTrackerHud {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "quest_tracker");

    private static final int COLOR_TITLE = 0xFFD4A030;
    private static final int COLOR_DONE = 0xFF66CC66;
    private static final int COLOR_TODO = 0xFFDDDDDD;
    private static final int PAD = 4;
    private static final int LINE_H = 10;

    private QuestTrackerHud() {}

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.hud.isHidden()) return;

            QuestEntryDisplayData quest = ClientQuestManager.getTrackedQuest().orElse(null);
            if (quest == null) return;

            int right = graphics.guiWidth() - PAD;
            int y = PAD + 14; // clear vanilla's own top-left/right HUD elements

            String title = quest.name().toUpperCase();
            graphics.text(mc.font, Component.literal(title), right - mc.font.width(title), y, COLOR_TITLE, true);
            y += LINE_H + 2;

            for (int i = 0; i < quest.objectives().size(); i++) {
                boolean done = i < quest.objectivesDone().length && quest.objectivesDone()[i];
                String mark = done ? "✓ " : "▢ ";
                String line = mark + quest.objectives().get(i);
                int color = done ? COLOR_DONE : COLOR_TODO;
                graphics.text(mc.font, Component.literal(line), right - mc.font.width(line), y, color, true);
                y += LINE_H;
            }

            if (quest.ready()) {
                String readyLine = "Open Quests to turn in!";
                graphics.text(mc.font, Component.literal(readyLine), right - mc.font.width(readyLine), y, COLOR_DONE, true);
            }
        });
    }
}
