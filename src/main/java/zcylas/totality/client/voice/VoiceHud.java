package zcylas.totality.client.voice;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.voice.capture.AudioLevel;
import zcylas.totality.client.hud.HudSprites;
import zcylas.totality.client.hud.HudSprites.Sprite;
import zcylas.totality.init.ModKeybinds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Voice Input HUD V2, just below the crosshair (the V1 indicator's place): a diamond microphone emblem
 * with a live waveform on both sides, and a small label plate underneath. Colour tells the context —
 * cyan Voice Commands (push-to-talk), violet Dictation (modifier + push-to-talk), amber/gold Operator
 * Mode — and the plate says the phase: listening, processing (an indeterminate ring; no invented
 * percentages) and a brief result. Results come only from {@link VoiceFeedback}, i.e. after the owner
 * (a hologram, the chat line, the SERVER for Operator Mode) actually accepted or refused. Hints name the
 * CONFIGURED keys. Reads state only; never changes it.
 *
 * <p>The waveform follows the real capture level ({@code MicrophoneManager#currentPeak()},
 * one value per captured chunk) through an attack/release smoother and a short history, newest bars
 * nearest the emblem; silence is flat. No audio is kept or read beyond that peak.
 */
final class VoiceHud {

    static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "voice_indicator");

    static final int CYAN = 0x46D9FF, VIOLET = 0xB38AFF, AMBER = 0xFFB43C, GREEN = 0x56F0A0, RED = 0xFF5468, GRAY = 0x9DB2C8;
    private static final int NAVY = 0x061331, DEEP_VIOLET = 0x150A33, DEEP_AMBER = 0x231404, DEEP_GREEN = 0x06241A,
            DEEP_RED = 0x2A0710, DEEP_GRAY = 0x0E1826;

    private static final Sprite EMBLEM_FRAME = Sprite.of("voice/emblem_frame", 104, 104);
    private static final Sprite EMBLEM_FILL = Sprite.of("voice/emblem_fill", 104, 104);
    private static final Sprite EMBLEM_GLOW = Sprite.of("voice/emblem_glow", 168, 168);
    private static final Sprite MIC = Sprite.of("voice/mic", 40, 56);
    private static final Sprite CHECK = Sprite.of("voice/check", 48, 48);
    private static final Sprite CROSS = Sprite.of("voice/cross", 48, 48);
    private static final Sprite RING = Sprite.of("voice/ring", 128, 128);
    private static final Sprite ORNAMENT = Sprite.of("voice/ornament", 64, 32);
    private static final Sprite PLATE_FILL = Sprite.of("voice/plate_fill", 192, 96);
    private static final Sprite PLATE_FRAME = Sprite.of("voice/plate_frame", 192, 96);
    private static final Sprite PLATE_GLOW = Sprite.of("voice/plate_glow", 240, 144);
    private static final Sprite KEY = Sprite.of("voice/key", 48, 44);
    private static final Sprite BAR = Sprite.of("voice/bar", 8, 64);

    /** Below the crosshair: the emblem's top edge sits this many GUI px under the screen centre. */
    static final int TOP_BELOW_CENTRE = 12;
    private static final int BARS = 8;
    private static final long SAMPLE_NANOS = 55_000_000L;
    private static final long RESULT_NANOS = 1_600_000_000L;
    private static final long AWAIT_NANOS = 5_000_000_000L;

    private static @Nullable VoiceFeedback feedback;
    private static long feedbackNanos;
    private static float appear;
    private static float level;
    private static final float[] history = new float[BARS];
    private static long lastSample;
    private static long lastFrame;
    private static int accent = CYAN, deep = NAVY;
    /** Smoothed extra drop below the default place (clearing a first-person System panel). */
    private static float drop;
    /** Smoothed sideways shift (third person: beside the character instead of over it). */
    private static float shift;
    private static @Nullable Look shownLook;

    /** Diagnostics for the capture run: the look drawn last frame (null when hidden). */
    static @Nullable String lastLook;

    private VoiceHud() {}

    static void register(VoiceInputController controller) {
        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.gui.hud.isHidden()) return;
            render(graphics, client, controller);
        });
    }

    /** Diagnostics: the smoothed capture level driving the waveform (0 when silent / not listening). */
    static float levelNow() {
        return level;
    }

    /** Client thread. */
    static void post(VoiceFeedback f) {
        feedback = f;
        feedbackNanos = System.nanoTime();
    }

    // ── What to show ──────────────────────────────────────────────────────────

    private enum Glyph { MIC, CHECK, CROSS }

    /** One visual state: palette, words, glyph, and which live elements run. */
    private record Look(String id, int accent, int deep, String title, List<Seg> hint, Glyph glyph, boolean bars,
                        boolean ring) {}

    /** A run of hint text, or a key cap. */
    private record Seg(String text, boolean key) {
        static Seg t(String s) { return new Seg(s, false); }
        static Seg k(KeyMapping m) { return new Seg(m.getTranslatedKeyMessage().getString(), true); }
    }

    private static @Nullable Look look(VoiceInputController controller, long now) {
        VoiceState state = controller.state();
        boolean dictation = controller.utteranceMode() == VoiceInputController.PushToTalkMode.DICTATION;
        VoiceInputController.OperatorPhase op = controller.operatorPhase();
        KeyMapping ptt = ModKeybinds.VOICE_PUSH_TO_TALK;
        switch (state) {
            case LISTENING -> {
                if (dictation) {
                    return new Look("dictation_listening", VIOLET, DEEP_VIOLET, "Dictation · Listening",
                            List.of(Seg.t("Release "), Seg.k(ptt), Seg.t(" to transcribe")), Glyph.MIC, true, false);
                }
                return switch (op) {
                    case REQUESTING -> new Look("operator_requesting", AMBER, DEEP_AMBER, "Operator Mode",
                            List.of(Seg.t("Requesting authorization…")), Glyph.MIC, true, true);
                    case AUTHORIZED -> new Look("operator_listening", AMBER, DEEP_AMBER, "Operator — Listening",
                            List.of(Seg.t("Clear Rain · Survival · Creative")), Glyph.MIC, true, false);
                    case DENIED -> new Look("operator_denied", RED, DEEP_RED, "Operator Mode Denied",
                            List.of(Seg.t("Operator permission required")), Glyph.CROSS, true, false);
                    case NONE -> new Look("command_listening", CYAN, NAVY, "System Listening",
                            List.of(Seg.t("Hold "), Seg.k(ptt), Seg.t(" to speak")), Glyph.MIC, true, false);
                };
            }
            case RECOGNIZING -> {
                if (dictation) {
                    return new Look("dictation_processing", VIOLET, DEEP_VIOLET, "Transcribing…",
                            List.of(Seg.t("Converting speech to text")), Glyph.MIC, false, true);
                }
                boolean operator = op == VoiceInputController.OperatorPhase.REQUESTING || op == VoiceInputController.OperatorPhase.AUTHORIZED;
                return new Look(operator ? "operator_processing" : "command_processing", operator ? AMBER : CYAN,
                        operator ? DEEP_AMBER : NAVY, "Processing Command",
                        List.of(Seg.t(operator ? "Operator Mode" : "Understanding your input…")), Glyph.MIC, false, true);
            }
            case LOADING -> {
                return new Look("loading", GRAY, DEEP_GRAY, "Voice Loading…",
                        List.of(Seg.t("Preparing speech recognition")), Glyph.MIC, false, true);
            }
            default -> { }
        }
        VoiceFeedback f = feedback;
        if (f == null) return null;
        long age = now - feedbackNanos;
        if (f.kind() == VoiceFeedback.Kind.AWAITING_SERVER) {
            return age < AWAIT_NANOS ? new Look("operator_awaiting", AMBER, DEEP_AMBER, "Awaiting Server",
                    List.of(Seg.t("“" + title(f.detail()) + "” · verifying authority")), Glyph.MIC, false, true) : null;
        }
        if (age > RESULT_NANOS) return null;
        return switch (f.kind()) {
            case RECOGNIZED -> new Look("command_recognized", GREEN, DEEP_GREEN, "Command Recognized",
                    List.of(Seg.t("“" + title(f.detail()) + "”")), Glyph.CHECK, false, false);
            case EXECUTED -> new Look("operator_executed", GREEN, DEEP_GREEN, "Operator · Executed",
                    List.of(Seg.t("“" + title(f.detail()) + "” · authorized by server")), Glyph.CHECK, false, false);
            case DENIED -> new Look("operator_denied_result", RED, DEEP_RED, "Denied",
                    List.of(Seg.t(f.detail().isEmpty() ? "Operator permission required" : f.detail())), Glyph.CROSS, false, false);
            case TRANSCRIBED -> new Look("dictation_done", VIOLET, DEEP_VIOLET, "Transcribed",
                    List.of(Seg.k(ModKeybinds.VOICE_EDIT_TRANSCRIPT), Seg.t(" Edit & Send")), Glyph.CHECK, false, false);
            case NOT_UNDERSTOOD -> new Look("not_understood", RED, DEEP_RED, "Not Understood",
                    List.of(Seg.t("Please try again")), Glyph.CROSS, false, false);
            case NO_COMMAND -> new Look("no_command", GRAY, DEEP_GRAY, "No Command Available",
                    List.of(Seg.t("Hold "), Seg.k(ModKeybinds.RADIAL_MODIFIER), Seg.t("+"), Seg.k(ptt), Seg.t(" to dictate")),
                    Glyph.MIC, false, false);
            case FAILED -> new Look("failed", RED, DEEP_RED, f.operator() ? "Operator Mode" : "Voice Input",
                    List.of(Seg.t(f.detail())), Glyph.CROSS, false, false);
            case CANCELLED -> new Look("cancelled", GRAY, DEEP_GRAY, "Cancelled",
                    List.of(Seg.t(f.detail().isEmpty() ? "Voice input stopped" : title(f.detail()))), Glyph.CROSS, false, false);
            case AWAITING_SERVER -> null;
        };
    }

    /** "clear_rain" / "confirm" → "Clear Rain" / "Confirm". */
    static String title(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.replace('_', ' ').trim().split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return b.toString();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private static void render(GuiGraphicsExtractor g, Minecraft client, VoiceInputController controller) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        Look look = look(controller, now);
        if (look != null) shownLook = look;
        appear = approach(appear, look != null ? 1 : 0, dt, look != null ? 0.09f : 0.14f);
        updateLevel(controller, now, dt, look != null && look.bars());
        if (look != null) {
            float k = 1 - (float) Math.exp(-dt / 0.09f);
            accent = HudSprites.mix(accent, look.accent(), k);
            deep = HudSprites.mix(deep, look.deep(), k);
        }
        lastLook = look == null ? null : look.id();
        drop = approach(drop, clearance(g), dt, 0.08f);
        // Placement evidence: in third person the character stands at the screen centre and the widget
        // covered its torso; there it moves beside the character (right-hand side).
        shift = approach(shift, client.options.getCameraType().isFirstPerson() ? 0 : THIRD_PERSON_SHIFT, dt, 0.1f);
        Look l = shownLook;
        if (l == null || appear < 0.02f) return;
        draw(g, client.font, l, now);
    }

    /**
     * Placement evidence (Voice HUD V2 captures): at its default place the widget covered the lower edge
     * and the buttons of an open first-person System panel. While such a panel is on screen the widget
     * moves just below the panel's lower edge — as far as the hotbar allows — and returns afterwards.
     */
    private static float clearance(GuiGraphicsExtractor g) {
        float bottom = zcylas.totality.client.hologram.HologramRenderer.firstPersonPanelBottom();
        if (Float.isNaN(bottom)) return 0;
        float half = g.guiHeight() / 2f;
        float wanted = half * bottom + 4 - TOP_BELOW_CENTRE;
        float room = g.guiHeight() - 40 - HEIGHT - (half + TOP_BELOW_CENTRE);   // keep above hotbar + status bars
        return Mth.clamp(wanted, 0, Math.max(0, room));
    }

    /** Third person: the widget's centre this many GUI px right of the crosshair (clear of the character). */
    static final float THIRD_PERSON_SHIFT = 72;

    /** Emblem plus plate, GUI px. */
    static final int HEIGHT = 50;

    /** Diagnostics: the widget's top edge in GUI px below the screen centre, as last drawn. */
    static float lastTop() {
        return TOP_BELOW_CENTRE + drop;
    }

    private static void updateLevel(VoiceInputController controller, long now, float dt, boolean live) {
        float target = 0;
        if (live) {
            // Speech sits around -35..-5 dBFS: map that span (the meter's 0.4..0.92) onto the bars.
            float meter = AudioLevel.meter(controller.microphone().currentPeak());
            target = Mth.clamp((meter - 0.4f) / 0.52f, 0, 1);
        }
        level = approach(level, target, dt, target > level ? 0.04f : 0.16f);
        if (now - lastSample >= SAMPLE_NANOS) {
            lastSample = now;
            System.arraycopy(history, 0, history, 1, BARS - 1);
            history[0] = level;
        }
    }

    private static float approach(float value, float target, float dt, float tau) {
        return value + (target - value) * (1 - (float) Math.exp(-dt / Math.max(1e-4f, tau)));
    }

    private static void draw(GuiGraphicsExtractor g, Font font, Look l, long now) {
        float a = appear;
        float t = now / 1e9f;
        float cx = Math.min(g.guiWidth() / 2f + shift, g.guiWidth() - 70);
        float top = g.guiHeight() / 2f + TOP_BELOW_CENTRE + drop + (1 - a) * 3;
        float cy = top + 12;
        float scale = 0.92f + 0.08f * a;
        int light = HudSprites.mix(accent, 0xFFFFFF, 0.55f);
        boolean live = l.bars();

        // Emblem: glow, dark core, luminous frame, glyph.
        float glow = live ? 0.35f + 0.45f * level : 0.32f + 0.06f * Mth.sin(t * 2.4f);
        HudSprites.drawCentred(g, EMBLEM_GLOW, cx, cy, scale, 0, HudSprites.argb(a * glow, accent), true);
        HudSprites.drawCentred(g, EMBLEM_FILL, cx, cy, scale, 0, HudSprites.argb(a * 0.9f, deep), false);
        HudSprites.drawCentred(g, EMBLEM_FRAME, cx, cy, scale, 0, HudSprites.argb(a, accent), false);
        Sprite glyph = switch (l.glyph()) {
            case MIC -> MIC;
            case CHECK -> CHECK;
            case CROSS -> CROSS;
        };
        float glyphAlpha = live ? 0.8f + 0.2f * level : 1;
        HudSprites.drawCentred(g, glyph, cx, cy + (l.glyph() == Glyph.MIC ? 0.3f : 0), scale * 0.9f, 0,
                HudSprites.argb(a * glyphAlpha, light), false);
        if (l.ring()) {
            HudSprites.drawCentred(g, RING, cx, cy, scale, t * 4.2f, HudSprites.argb(a * 0.95f, accent), false);
        }

        if (live) {
            // Live waveform: real capture level history, newest nearest the emblem.
            for (int i = 0; i < BARS; i++) {
                float v = history[i] * (0.78f + 0.22f * Mth.sin(t * 11f + i * 1.9f)) * (1 - i * 0.07f);
                float h = 1.6f + 12.5f * Mth.clamp(v, 0, 1);
                float dx = 15.5f + i * 3.1f;
                int col = HudSprites.argb(a * (0.95f - i * 0.075f), accent);
                HudSprites.draw(g, BAR, cx - dx - 0.75f, cy - h / 2, 1.5f, h, col, false);
                HudSprites.draw(g, BAR, cx + dx - 0.75f, cy - h / 2, 1.5f, h, col, false);
            }
        } else {
            int col = HudSprites.argb(a * 0.85f, accent);
            HudSprites.draw(g, ORNAMENT, cx - 14 - 16, cy - 4, 16, 8, col, false);
            HudSprites.drawMirrored(g, ORNAMENT, cx + 14, cy - 4, 16, 8, col, false);
        }

        // Label plate: title and a hint line with key caps.
        int hintW = hintWidth(font, l.hint());
        float pw = Math.max(font.width(l.title()), hintW) + 18;
        float ph = 23;
        float px = cx - pw / 2, py = top + 27;
        HudSprites.threeSlice(g, PLATE_GLOW, px - 6, py - 6, pw + 12, ph + 12, 14, HudSprites.argb(a * 0.28f, accent), true);
        HudSprites.threeSlice(g, PLATE_FILL, px, py, pw, ph, 8, HudSprites.argb(a * 0.88f, deep), false);
        HudSprites.threeSlice(g, PLATE_FRAME, px, py, pw, ph, 8, HudSprites.argb(a * 0.95f, accent), false);
        if (a < 0.3f) return;   // text only once it is readable (font alpha is coarse)
        int ta = Math.round(a * 255) << 24;
        g.text(font, l.title(), Math.round(cx - font.width(l.title()) / 2f), Math.round(py + 3), ta | light, false);
        drawHint(g, font, l.hint(), Math.round(cx - hintW / 2f), Math.round(py + 13), a);
    }

    private static int hintWidth(Font font, List<Seg> hint) {
        int w = 0;
        for (Seg s : hint) w += s.key() ? font.width(s.text()) + 6 : font.width(s.text());
        return w;
    }

    private static void drawHint(GuiGraphicsExtractor g, Font font, List<Seg> hint, int x, int y, float a) {
        int ta = Math.round(a * 255) << 24;
        int dim = HudSprites.mix(accent, 0xB8C4D4, 0.6f);
        for (Seg s : hint) {
            int w = font.width(s.text());
            if (s.key()) {
                HudSprites.threeSlice(g, KEY, x, y - 2, w + 6, 11, 3, HudSprites.argb(a * 0.95f, accent), false);
                g.text(font, s.text(), x + 3, y, ta | HudSprites.mix(accent, 0xFFFFFF, 0.7f), false);
                x += w + 6;
            } else {
                g.text(font, s.text(), x, y, ta | dim, false);
                x += w;
            }
        }
    }

    /** Test/diagnostic seam: the words of the look for a controller state (no drawing). */
    static List<String> describe(VoiceInputController controller) {
        Look l = look(controller, System.nanoTime());
        List<String> out = new ArrayList<>();
        if (l == null) return out;
        out.add(l.title());
        StringBuilder b = new StringBuilder();
        for (Seg s : l.hint()) b.append(s.key() ? "[" + s.text() + "]" : s.text());
        out.add(b.toString());
        return out;
    }
}
