package zcylas.totality.client.renderer.hud.notification;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for {@link NotificationManager}'s Part C wrapping wiring. Every test
 * here is a source-text sentinel, not runtime proof: the actual native {@code Font.split(...)} call
 * requires a live {@code Minecraft}/{@code Font} instance and cannot be invoked under plain JUnit.
 * The two pure helpers it depends on ({@code effectiveWidth}, {@code splitIntoParagraphs}) ARE
 * covered behaviorally, for real, by {@link NotificationManagerLayoutTest}.
 */
class NotificationWrappingSourceRegressionTest {

    private static final Path NOTIFICATION_MANAGER =
            Path.of("src/main/java/zcylas/totality/client/renderer/hud/notification/NotificationManager.java");

    private static String read() throws Exception {
        assertTrue(Files.exists(NOTIFICATION_MANAGER), "expected to find " + NOTIFICATION_MANAGER);
        return Files.readString(NOTIFICATION_MANAGER);
    }

    @Test
    void usesMinecraftsNativeFontSplittingApi() throws Exception {
        String source = read();
        assertTrue(source.contains("font.split("),
                "expected wrapping to use Minecraft's native Font.split(...), not a hand-rolled wrapper");
    }

    @Test
    void doesNotWrapByCharacterCountOrStringLength() throws Exception {
        String source = read();
        // Only the natural uses of String methods for paragraph splitting are allowed; nothing in
        // this file should measure text width via .length().
        assertFalse(source.contains(".length()"),
                "must not measure rendered text width via String.length() / character counts");
    }

    @Test
    void isNotCoupledToMobHealthBarHud() throws Exception {
        // Import lines only — explanatory comments are expected to mention MobHealthBarHud when
        // documenting why NotificationManager deliberately stays independent of it (Part C scope).
        // The actual required boundary is no code dependency/import.
        String source = read();
        java.util.List<String> importLines = source.lines().map(String::trim).filter(l -> l.startsWith("import ")).toList();
        for (String importLine : importLines) {
            assertFalse(importLine.toLowerCase(java.util.Locale.ROOT).contains("mobhealthbarhud"),
                    "NotificationManager must not import MobHealthBarHud (see class Javadoc / Part C scope): " + importLine);
        }
    }

    @Test
    void preservesTheExistingTopLeftOriginQueueLimitAndTiming() throws Exception {
        String source = read();
        assertTrue(source.contains("PADDING_X = 4"), "expected the top-left X origin to remain unchanged");
        assertTrue(source.contains("PADDING_Y = 4"), "expected the top-left Y origin to remain unchanged");
        assertTrue(source.contains("MAX_NOTIFICATIONS = 5"), "expected the 5-notification queue limit to remain unchanged");
        assertTrue(source.contains("active.remove(0)"), "expected oldest-notification removal on overflow to remain unchanged");
        assertTrue(source.contains("LIFETIME_TICKS = 80"), "expected the 80-tick lifetime to remain unchanged");
        assertTrue(source.contains("FADE_TICKS = 20"), "expected the 20-tick fade window to remain unchanged");
    }

    @Test
    void preservesTheExistingDisconnectClearingAndF1Guard() throws Exception {
        String source = read();
        assertTrue(source.contains("ClientPlayConnectionEvents.DISCONNECT.register"),
                "expected disconnect clearing to remain wired");
        assertTrue(source.contains("client.gui.hud.isHidden()") || source.contains("hud.isHidden()"),
                "expected the F1/HUD-hidden guard to remain in place");
    }

    @Test
    void wrappingHelpersAreNamedConstantsNotMagicNumbers() throws Exception {
        String source = read();
        assertTrue(source.contains("PREFERRED_NOTIFICATION_WIDTH = 180"),
                "expected the preferred width to be a named constant equal to 180");
        assertTrue(source.contains("RIGHT_SAFETY_MARGIN"), "expected a named right safety margin constant");
        assertTrue(source.contains("MIN_EFFECTIVE_WIDTH"), "expected a named positive safety floor constant");
    }
}
