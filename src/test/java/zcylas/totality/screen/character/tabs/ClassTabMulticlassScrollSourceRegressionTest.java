package zcylas.totality.screen.character.tabs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the right-side/multiclass owned-class list scrolling fix (see
 * {@code TOTALITY_CLASS_TAB_MULTICLASS_SCROLL_FIX_IMPLEMENTATION_2026-09-16.md}). A source-text
 * sentinel, not a runtime proof — {@code ClassTab} is a client GUI tab that needs a bootstrapped,
 * GL-initialized {@code Minecraft}/{@code Font} to render/click-test, unavailable under plain JUnit
 * (the same constraint {@code Phase3CConsumerMigrationSourceRegressionTest}/{@code
 * ClassTabQuickLevelUpSourceRegressionTest} already document elsewhere in this suite).
 *
 * <p>Root cause this fix addresses: the owned-class list inside the right column of {@code
 * drawProgressionPanel} was drawn with an unconditional cursor advance and no scroll offset at
 * all — a fourth+ owned class (and its "+" button) simply rendered past the bottom of the visible
 * box with no way to reach it, unlike the LEFT column's {@code progScroll}-based list a few lines
 * above it in the same method, which already scrolls correctly.
 */
class ClassTabMulticlassScrollSourceRegressionTest {

    private static final Path SOURCE =
            Path.of("src/main/java/zcylas/totality/screen/character/tabs/ClassTab.java");

    private static String read() throws Exception {
        assertTrue(Files.exists(SOURCE), "expected to find source file at " + SOURCE);
        return Files.readString(SOURCE);
    }

    @Test
    void theOwnedClassListMaintainsItsOwnScrollOffsetField() throws Exception {
        String source = read();
        assertTrue(source.contains("private int mcListScroll"),
                "the owned-class list must maintain its own scroll offset, mirroring progScroll's own pattern");
    }

    @Test
    void theListCursorIsDerivedFromTheScrollOffsetBeforeAnyRowIsDrawn() throws Exception {
        String source = read();
        assertTrue(source.contains("int mcy = listTopY - mcListScroll;"),
                "the drawing cursor must be offset by the scroll amount before the first row is drawn, "
                        + "exactly like the left column's `int lcy = topY + PAD - progScroll;`");
    }

    @Test
    void scrollingIsBoundedOnBothEndsInDrawAndInMouseScrolled() throws Exception {
        String source = read();
        // Requirement 3: clamp correctly at both top and bottom. Checked in two places: once
        // in draw() (so a content-height change is corrected the very next frame) and once in
        // mouseScrolled() (so the wheel itself never pushes the offset out of range).
        int drawClampCount = countOccurrences(source, "mcListScroll    = Math.clamp(mcListScroll, 0, mcListMaxScroll);")
                + countOccurrences(source, "mcListScroll = Math.clamp(mcListScroll, 0, mcListMaxScroll);");
        assertTrue(drawClampCount >= 1, "draw() must clamp mcListScroll against the freshly measured content height");
        assertTrue(source.contains("mcListScroll = Math.clamp(mcListScroll - amount, 0, mcListMaxScroll);"),
                "mouseScrolled must clamp the multiclass list's scroll on both ends, unlike progScroll's "
                        + "own bottom-only Math.max(0, ...) (a pre-existing, unrelated gap this task does not fix)");
    }

    @Test
    void theMaxScrollIsRecomputedFromMeasuredContentHeightEveryFrame() throws Exception {
        String source = read();
        assertTrue(source.contains("mcListMaxScroll = Math.max(0, measureOwnedClassListContentHeight() - listH);"),
                "the scroll ceiling must be derived from the list's actual current content height every "
                        + "frame, so a changing number of owned classes (or a class gaining a subclass) is "
                        + "handled safely without any special-cased event hook");
    }

    @Test
    void renderingAndClickPositioningShareTheSameScrolledCursor() throws Exception {
        String source = read();
        int start = source.indexOf("int mcy = listTopY - mcListScroll;");
        int end = source.indexOf("screen.esc(g); // end owned-class list scissor");
        assertTrue(start >= 0 && end > start, "expected to find the scrollable list's draw block");
        String block = source.substring(start, end);

        // Every drawQuickLevelButton call in this block must be positioned using the same `mcy`
        // cursor the row text itself is drawn at — never a separate, unscrolled coordinate.
        assertTrue(block.contains("rightX + rightW / 2 - btnSz / 2, mcy, btnSz, primaryId, canSpendAPoint"),
                "the single-class quick-level button must be positioned using the scrolled `mcy` cursor");
        assertTrue(block.contains("rightX + rightW - btnSz, mcy + (rowH - btnSz) / 2, btnSz,"),
                "the multiclass row's quick-level button must be positioned using the scrolled `mcy` cursor");
    }

    @Test
    void offViewportButtonsAreExcludedFromClickHandling() throws Exception {
        String source = read();
        assertTrue(source.contains("boolean fullyVisible = y >= mcListPanelY && y + size <= mcListPanelY + mcListPanelH;"),
                "a button must be checked for full containment within the list's own viewport");
        assertTrue(source.contains("quickLevelButtons.add(new QuickLevelButton(classId, x, y, size, size, enabled && fullyVisible));"),
                "a button scrolled outside the viewport must never be stored as clickable, regardless of "
                        + "whether it would otherwise be enabled");
    }

    @Test
    void theRightListViewportIsCheckedBeforeTheWiderLeftColumnRegionInMouseScrolled() throws Exception {
        String source = read();
        int mouseScrolledStart = source.indexOf("public void mouseScrolled(int mx, int my, double delta)");
        assertTrue(mouseScrolledStart >= 0);
        String body = source.substring(mouseScrolledStart);
        int mcCheck = body.indexOf("mcListPanelX, mcListPanelY, mcListPanelW, mcListPanelH");
        int progCheck = body.indexOf("progPanelX, progPanelY, progPanelW, progPanelH");
        assertTrue(mcCheck >= 0 && progCheck >= 0);
        assertTrue(mcCheck < progCheck,
                "the multiclass list's (narrower) viewport must be checked before the (wider) progression "
                        + "panel region, so hovering the right list scrolls only that list rather than also "
                        + "matching the left column's own wider hit-box");
    }

    @Test
    void openingTheTabResetsTheMulticlassScrollAlongsideEveryOtherScrollField() throws Exception {
        String source = read();
        assertTrue(source.contains("progScroll = resourceScroll = identScroll = mcListScroll = 0;"),
                "onOpen must reset the multiclass list's scroll exactly like the three pre-existing scroll fields");
    }

    @Test
    void theAddClassLevelPayloadClickPathIsUnchanged() throws Exception {
        String source = read();
        assertTrue(source.contains("new zcylas.totality.networking.classes.AddClassLevelPayload(btn.classId().toString())"),
                "the quick level-up click handler must still send the same AddClassLevelPayload unchanged");
        assertFalse(source.contains("SelectSubclassPayload") ,
                "this scrolling fix must not touch subclass selection in any way");
    }

    @Test
    void leftSideScrollingFieldsAndHandlingStillExist() throws Exception {
        String source = read();
        assertTrue(source.contains("private int progScroll"), "the left column's own scroll field must remain");
        assertTrue(source.contains("progScroll = Math.max(0, progScroll - amount);"),
                "the left column's own mouseScrolled handling must remain unchanged");
        assertTrue(source.contains("int lcy = topY + PAD - progScroll;"),
                "the left column's own scrolled cursor must remain unchanged");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) { count++; idx += needle.length(); }
        return count;
    }
}
