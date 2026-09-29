package zcylas.totality.client.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure rules behind push-to-talk, Edit & Send and settings. */
class VoiceInputRulesTest {

    // ── push-to-talk edges ─────────────────────────────────────────────────────

    @Test
    void pressIsOnlyTheDownEdgeDuringOrdinaryGameplay() {
        assertTrue(PushToTalkEdge.isPress(true, false, true, false, true));
        assertFalse(PushToTalkEdge.isPress(true, true, true, false, true), "holding is not a new press");
        assertFalse(PushToTalkEdge.isPress(false, true, true, false, true), "release is not a press");
        assertFalse(PushToTalkEdge.isPress(true, false, true, true, false));
        assertFalse(PushToTalkEdge.isPress(true, false, true, true, true), "never while a screen (e.g. chat) is open");
        assertFalse(PushToTalkEdge.isPress(true, false, false, false, true), "never outside a world");
        assertFalse(PushToTalkEdge.isPress(true, false, true, false, false), "never while unfocused");
    }

    @Test
    void closingAScreenWhileHoldingIsNotAPress() {
        // tick 1: chat open, key held (typing) → wasDown becomes true; tick 2: chat closed, still held.
        boolean wasDown = false;
        boolean tick1 = PushToTalkEdge.isPress(true, wasDown, true, true, true);
        wasDown = true;
        boolean tick2 = PushToTalkEdge.isPress(true, wasDown, true, false, true);
        assertFalse(tick1);
        assertFalse(tick2);
    }

    // ── Edit & Send ────────────────────────────────────────────────────────────

    @Test
    void transcriptGoesOnlyIntoAnEmptyMessageChat() {
        assertEquals(ChatInsertionPolicy.Decision.INSERT, ChatInsertionPolicy.decide("Hello there", "", false));
    }

    @Test
    void anExistingDraftIsNeverOverwrittenOrAppendedTo() {
        assertEquals(ChatInsertionPolicy.Decision.KEEP_DRAFT, ChatInsertionPolicy.decide("Hello", "half-typed message", true));
        assertEquals(ChatInsertionPolicy.Decision.KEEP_DRAFT, ChatInsertionPolicy.decide("Hello", "/gamemode creat", true));
        assertEquals(ChatInsertionPolicy.Decision.KEEP_DRAFT, ChatInsertionPolicy.decide("Hello", "", true));
        assertEquals(ChatInsertionPolicy.Decision.KEEP_DRAFT, ChatInsertionPolicy.decide("Hello", "/", false),
                "a pre-filled command prefix is never combined with a transcript");
    }

    @Test
    void nothingToInsertWithoutATranscript() {
        assertEquals(ChatInsertionPolicy.Decision.NOTHING_TO_INSERT, ChatInsertionPolicy.decide(null, "", false));
        assertEquals(ChatInsertionPolicy.Decision.NOTHING_TO_INSERT, ChatInsertionPolicy.decide("  ", "", false));
    }

    @Test
    void insertedTextIsAnOrdinaryMessageNeverACommand() {
        assertEquals("Tp home", ChatInsertionPolicy.textFor("/tp home"));
        assertEquals("Hello this is a test", ChatInsertionPolicy.textFor("hello this is a test"));
        assertTrue(ChatInsertionPolicy.textFor("x".repeat(500)).length() <= 256);
    }

    // ── settings ───────────────────────────────────────────────────────────────

    @Test
    void settingsDefaultsAndRoundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config").resolve("totality-voice.properties");
        VoiceSettings fresh = VoiceSettings.load(file);
        assertTrue(fresh.enabled());
        assertNull(fresh.device());
        assertFalse(fresh.saveDebugWav());
        assertFalse(Files.exists(file), "loading never creates the file");

        fresh.setEnabled(false);
        fresh.setDevice("CORSAIR HS80 RGB Wireless Gaming Receiver Mono");
        fresh.setDebug(true);
        fresh.save();
        VoiceSettings back = VoiceSettings.load(file);
        assertFalse(back.enabled());
        assertEquals("CORSAIR HS80 RGB Wireless Gaming Receiver Mono", back.device());
        assertTrue(back.debug());
        assertFalse(back.saveDebugWav(), "WAV saving stays off unless explicitly set");

        back.setDevice(null);
        back.save();
        assertNull(VoiceSettings.load(file).device());
    }

    @Test
    void corruptSettingsFallBackToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("totality-voice.properties");
        Files.writeString(file, "enabled=\\u12");
        assertTrue(VoiceSettings.load(file).enabled());
    }

    @Test
    void deviceResolutionFallsBackToDefault() {
        List<String> present = List.of("CORSAIR HS80 RGB Wireless Gaming Receiver Mono", "Starship/Matisse HD Audio Controller Analog Stereo");
        assertEquals(new VoiceSettings.DeviceChoice(null, false), VoiceSettings.resolveDevice(null, present));
        assertEquals(new VoiceSettings.DeviceChoice(present.get(1), false), VoiceSettings.resolveDevice(present.get(1), present));
        assertEquals(new VoiceSettings.DeviceChoice(null, true), VoiceSettings.resolveDevice("Unplugged USB Mic", present));
    }
}
