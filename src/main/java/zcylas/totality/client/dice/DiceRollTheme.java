package zcylas.totality.client.dice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.client.hologram.HologramSound;
import zcylas.totality.client.hologram.HologramStyle;

/**
 * Colours and UI cues of the Dice Roll UI V2, taken from Totality's System (the Solo Leveling-inspired holograms):
 * cyan light on a deep navy surface, the brighter System blue for success, the error red for failure; a critical
 * success burns gold (the warning amber's highlight). The System's own open/select/close cues play for the screen.
 */
public final class DiceRollTheme {

    public static final int ACCENT = HologramStyle.SYSTEM.accent;
    public static final int SURFACE = HologramStyle.SYSTEM.surface;
    public static final int TITLE = HologramStyle.SYSTEM.title;
    public static final int BODY = HologramStyle.SYSTEM.body;
    public static final int HIGHLIGHT = HologramStyle.SYSTEM.highlight;
    public static final int SUCCESS = HologramStyle.SUCCESS.highlight;
    public static final int FAILURE = HologramStyle.ERROR.accent;
    public static final int CRITICAL_SUCCESS = HologramStyle.WARNING.highlight;
    public static final int CRITICAL_FAILURE = 0xC8102E;
    /** Die faces: unlit and fully lit steel blue. */
    public static final int FACE_DARK = 0x07142A;
    public static final int FACE_LIGHT = 0x3C78B4;

    private DiceRollTheme() {}

    public static int outcomeColor(RollOutcome outcome) {
        return switch (outcome) {
            case CRITICAL_SUCCESS -> CRITICAL_SUCCESS;
            case SUCCESS -> SUCCESS;
            case FAILURE -> FAILURE;
            case CRITICAL_FAILURE -> CRITICAL_FAILURE;
        };
    }

    public static String outcomeText(RollOutcome outcome) {
        return switch (outcome) {
            case CRITICAL_SUCCESS -> "CRITICAL SUCCESS";
            case SUCCESS -> "SUCCESS";
            case FAILURE -> "FAILURE";
            case CRITICAL_FAILURE -> "CRITICAL FAILURE";
        };
    }

    /** ARGB from an RGB colour and a 0-1 alpha. */
    public static int argb(float alpha, int rgb) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return a << 24 | rgb & 0xFFFFFF;
    }

    public static int mix(int rgbA, int rgbB, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((rgbA >> 16) & 255) * (1 - t) + ((rgbB >> 16) & 255) * t);
        int g = Math.round(((rgbA >> 8) & 255) * (1 - t) + ((rgbB >> 8) & 255) * t);
        int b = Math.round((rgbA & 255) * (1 - t) + (rgbB & 255) * t);
        return r << 16 | g << 8 | b;
    }

    public static void systemCue(HologramSound cue, float pitch) {
        play(SoundEvent.createVariableRangeEvent(cue.id), pitch);
    }

    public static void openCue() {
        systemCue(HologramSound.OPEN, 1.0f);
    }

    public static void selectCue() {
        systemCue(HologramSound.SELECT, 1.0f);
    }

    public static void closeCue() {
        systemCue(HologramSound.CLOSE, 1.0f);
    }

    public static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }
}
