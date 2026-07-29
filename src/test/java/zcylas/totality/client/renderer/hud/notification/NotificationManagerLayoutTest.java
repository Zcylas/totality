package zcylas.totality.client.renderer.hud.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for {@link NotificationManager}'s Part C pure layout helpers —
 * {@code effectiveWidth} and {@code splitIntoParagraphs}. These execute for real: neither helper
 * touches {@code Minecraft.getInstance()}, a live {@code Font}, or any rendering/registry state, so
 * they run safely under plain JUnit (same reasoning {@code NotificationTimingVerification} already
 * relies on for this class's other package-private test hooks, just via real JUnit assertions
 * instead of the dev-client {@code VerificationReporter} convention).
 *
 * <p>The actual native font-wrapping call ({@code Font.split(...)} inside {@code wrapParagraph})
 * cannot be exercised here — it requires a live {@code Minecraft}/{@code Font} instance. That is
 * covered instead by {@link NotificationWrappingSourceRegressionTest} (a source sentinel, not
 * runtime proof) and by manual validation.
 */
class NotificationManagerLayoutTest {

    // ── effectiveWidth ───────────────────────────────────────────────────────

    @Test
    void wideScreenUsesThePreferredWidth() {
        assertEquals(180, NotificationManager.effectiveWidth(1920));
    }

    @Test
    void narrowScreenShrinksBelowThePreferredWidth() {
        int width = NotificationManager.effectiveWidth(100);
        assertTrue(width < 180, "expected a narrower-than-preferred width on a 100px-wide screen, got " + width);
        assertEquals(100 - 4 - 8, width, "expected guiWidth - PADDING_X(4) - RIGHT_SAFETY_MARGIN(8)");
    }

    @Test
    void extremelyNarrowScreenNeverProducesZeroOrNegativeWidth() {
        assertTrue(NotificationManager.effectiveWidth(0) > 0);
        assertTrue(NotificationManager.effectiveWidth(1) > 0);
        assertTrue(NotificationManager.effectiveWidth(5) > 0);
        assertTrue(NotificationManager.effectiveWidth(-50) > 0, "must stay positive even for a nonsensical negative input");
    }

    @Test
    void widthNeverExceedsWhatTheScreenActuallyHasRoomFor() {
        for (int guiWidth = 0; guiWidth <= 400; guiWidth += 7) {
            int width = NotificationManager.effectiveWidth(guiWidth);
            int available = Math.max(1, guiWidth - 4 - 8);
            assertTrue(width <= available,
                    "guiWidth=" + guiWidth + ": effectiveWidth=" + width + " exceeds available=" + available);
        }
    }

    @Test
    void widthChangesWithGuiWidth() {
        int narrow = NotificationManager.effectiveWidth(80);
        int wide = NotificationManager.effectiveWidth(1920);
        assertTrue(narrow < wide, "expected a narrower effective width for a narrower screen");
    }

    // ── splitIntoParagraphs ──────────────────────────────────────────────────

    @Test
    void singleParagraphWithNoNewlineStaysOneParagraph() {
        assertArrayEquals(new String[] { "hello" }, NotificationManager.splitIntoParagraphs("hello"));
    }

    @Test
    void explicitNewlineProducesTwoParagraphs() {
        assertArrayEquals(new String[] { "line one", "line two" },
                NotificationManager.splitIntoParagraphs("line one\nline two"));
    }

    @Test
    void twoConsecutiveNewlinesPreserveAnEmptyParagraphBetweenThem() {
        assertArrayEquals(new String[] { "above", "", "below" },
                NotificationManager.splitIntoParagraphs("above\n\nbelow"));
    }

    @Test
    void trailingNewlinePreservesATrailingEmptyParagraph() {
        assertArrayEquals(new String[] { "text", "" }, NotificationManager.splitIntoParagraphs("text\n"));
    }
}
