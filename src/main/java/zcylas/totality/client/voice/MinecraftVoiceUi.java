package zcylas.totality.client.voice;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import zcylas.totality.Totality;
import zcylas.totality.client.hologram.HologramManager;
import zcylas.totality.client.renderer.hud.notification.NotificationManager;
import zcylas.totality.init.ModKeybinds;

/**
 * Minecraft side of {@link VoiceUi}. Notices go through the existing {@link NotificationManager}; the
 * one-time model initialization is a System hologram ({@link VoiceHolograms});
 * transcripts become a chat line added with {@code addClientSystemMessage}, which only ever exists
 * on this client — nothing is sent to the server.
 */
final class MinecraftVoiceUi implements VoiceUi {

    static final int WHITE = 0xFFFFFFFF;
    static final int GRAY = 0xFFAAAAAA;
    static final int GREEN = 0xFF55FF55;
    static final int YELLOW = 0xFFFFFF55;
    static final int RED = 0xFFFF5555;
    static final int GOLD = 0xFFFFAA00;

    private final VoiceSettings settings;

    MinecraftVoiceUi(VoiceSettings settings) {
        this.settings = settings;
    }

    @Override
    public void notice(Notice notice, String detail) {
        if (notice == Notice.NO_COMMAND) {
            detail = "No System command to answer right now — hold " + dictationKeys() + " to dictate.";
        }
        NotificationManager.add(detail, switch (notice) {
            case READY, RESET -> GREEN;
            case LOADING, DISABLED, CANCELLED, BUSY, NO_COMMAND -> GRAY;
            case NOT_UNDERSTOOD, TOO_SHORT, TOO_QUIET, DEVICE_FALLBACK, MAX_DURATION -> YELLOW;
            case NO_SIGNAL, DEVICE_UNAVAILABLE, BACKEND_FAILED, CRASH_SUSPECTED -> RED;
        });
    }

    /** The one-time model initialization is presented as a System hologram instead of a feed line. */
    @Override
    public void modelInitialization(Notice notice, String detail) {
        HologramManager.show(VoiceHolograms.modelInitialization(notice, detail));
    }

    @Override
    public void feedback(VoiceFeedback feedback) {
        VoiceHud.post(feedback);
    }

    /** The configured dictation chord, e.g. "Left Alt+B" (rebind-aware). */
    static String dictationKeys() {
        return ModKeybinds.RADIAL_MODIFIER.getTranslatedKeyMessage().getString() + "+"
                + ModKeybinds.VOICE_PUSH_TO_TALK.getTranslatedKeyMessage().getString();
    }

    @Override
    public void transcript(String text) {
        Component line = Component.literal("[Totality Voice] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  [").withStyle(ChatFormatting.DARK_GRAY)
                        .append(ModKeybinds.VOICE_EDIT_TRANSCRIPT.getTranslatedKeyMessage().copy())
                        .append(": edit & send]"));
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(line);
    }

    @Override
    public void log(String line) {
        Totality.LOGGER.info("[Totality Voice] {}", line);
    }

    @Override
    public void debug(String line) {
        if (!settings.debug()) return;
        Totality.LOGGER.info("[Totality Voice][debug] {}", line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                Component.literal("[Voice debug] " + line).withStyle(ChatFormatting.DARK_GRAY));
    }
}
